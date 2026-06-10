package com.ticketing.presentation.vaadin.views;

import static com.ticketing.presentation.vaadin.util.RequiredFields.markRequired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.DateRangeCondition;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.IDiscountCondition;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.MaxQuantityCondition;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityCondition;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.SimpleDiscount;
import com.ticketing.domain.event.SumCompositeDiscount;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.CompanyInfoResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.OrgChartResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PolicyViewResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.SalesReportResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "company", layout = MainLayout.class)
@PageTitle("Company")
public class CompanyView extends VerticalLayout {

    private enum CompanyMode {
        LOOKUP("Company lookup", false),
        FOUNDER("Founder setup", true),
        PERSONNEL("Personnel", true),
        LIFECYCLE("Lifecycle", true),
        EVENTS("Events", true),
        INVENTORY("Inventory", true),
        POLICIES("Policies", true),
        REPORTS("Reports", true);

        private final String label;
        private final boolean memberOnly;

        CompanyMode(String label, boolean memberOnly) {
            this.label = label;
            this.memberOnly = memberOnly;
        }
    }

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CompanyPresenter presenter;

    private final Span sessionStatus = new Span();
    private final Paragraph memberOnlyCompanyHint = new Paragraph("Log in as a member to use company owner and manager actions.");
    private final Tabs modeTabs = new Tabs();
    private final VerticalLayout modeContent = new VerticalLayout();
    private final Map<CompanyMode, Tab> tabByMode = new EnumMap<>(CompanyMode.class);
    private final Map<Tab, VerticalLayout> panelByTab = new HashMap<>();
    private VerticalLayout inventoryManagementControls;

    private final TextField openCompanyName = new TextField("New company name");
    private final TextArea openCompanyDescription = new TextArea("New company description");
    private final ComboBox<CompanySummaryDTO> infoCompanyName = new ComboBox<>("Company info name");
    private final Span companyInfoStatus = new Span("Load company information to view public details.");
    private final Grid<EventSummaryDTO> companyEventsGrid = new Grid<>(EventSummaryDTO.class, false);
    private final ComboBox<EventSummaryDTO> lookupEventPicker = new ComboBox<>("Published event");

    private final ComboBox<CompanySummaryDTO> personnelCompanyName = new ComboBox<>("Personnel company name");
    private final ComboBox<PersonnelTarget> targetMember = new ComboBox<>("Target member");
    private final ComboBox<StaffAppointment.StaffRole> role = new ComboBox<>("Role");
    private final CheckboxGroup<ManagerPermission> permissions = new CheckboxGroup<>("Manager permissions");
    private final TextField offerId = new TextField("Role offer ID");
    private final Span personnelStatus = new Span("Manage owner and manager appointments.");
    private final Span personnelAccessHint = new Span("Select a company to show owner-only permission controls.");
    private final VerticalLayout orgChartDisplay = new VerticalLayout();
    private Button revokePersonnelButton;
    private Button changeManagerPermissionsButton;

    private final ComboBox<CompanySummaryDTO> eventCompanyName = new ComboBox<>("Event company name");
    private final TextField eventName = new TextField("Event name");
    private final TextArea eventDescription = new TextArea("Event description");
    private final ComboBox<EventCategory> eventCategory = new ComboBox<>("Event category");
    private final DateTimePicker startTime = new DateTimePicker("Start time");
    private final DateTimePicker endTime = new DateTimePicker("End time");
    private final DateTimePicker doorsOpenTime = new DateTimePicker("Doors open time");
    private final IntegerField lockMinutes = new IntegerField("Lock minutes");
    private final TextField zoneName = new TextField("Zone name");
    private final BigDecimalField zonePrice = new BigDecimalField("Zone price");
    private final IntegerField gaCapacity = new IntegerField("GA capacity");
    private final TextField sectionName = new TextField("Venue section");
    private final ComboBox<EventSummaryDTO> eventId = new ComboBox<>("Event to manage");
    private final TextField newEventName = new TextField("New event name");
    private final TextArea newEventDescription = new TextArea("New event description");
    private final TextField newArtist = new TextField("New artist");
    private final DateTimePicker newStartTime = new DateTimePicker("New start time");
    private final DateTimePicker newEndTime = new DateTimePicker("New end time");
    private final DateTimePicker newDoorsOpenTime = new DateTimePicker("New doors open time");
    private final Span eventStatus = new Span("Create, edit, or cancel company events.");

    private final ComboBox<CompanySummaryDTO> inventoryCompanyName = new ComboBox<>("Inventory company name");
    private final ComboBox<EventSummaryDTO> inventoryEventId = new ComboBox<>("Inventory event");
    private final TextField inventoryZoneId = new TextField("Inventory zone ID");
    private final TextField seatRow = new TextField("Seat row");
    private final TextField seatNumber = new TextField("Seat number");
    private final TextField seatId = new TextField("Seat ID");
    private final IntegerField capacityDelta = new IntegerField("Capacity delta");
    private final BigDecimalField zonePriceUpdate = new BigDecimalField("Zone price update");
    private final Span inventoryStatus = new Span("Load or manage supported inventory actions.");
    private final VerticalLayout eventMapDisplay = new VerticalLayout();

    private final ComboBox<CompanySummaryDTO> lifecycleCompanyName = new ComboBox<>("Lifecycle company name");
    private final Span lifecycleStatus = new Span("Suspend, reopen, or close companies.");
    private final Span lifecycleAccessHint = new Span("Select a company to show founder-only lifecycle controls.");
    private Button suspendCompanyButton;
    private Button reopenCompanyButton;
    private Button closeCompanyButton;

    private final ComboBox<CompanySummaryDTO> reportingCompanyName = new ComboBox<>("Reporting company name");
    private final Span reportingStatus = new Span("Load company purchase history and hierarchical sales reports.");
    private final Grid<PurchaseRecordDTO> purchasesGrid = new Grid<>(PurchaseRecordDTO.class, false);
    private final VerticalLayout salesReportDisplay = new VerticalLayout();

    private final ComboBox<CompanySummaryDTO> policyCompanyName = new ComboBox<>("Policy company name");
    private final ComboBox<EventSummaryDTO> policyEventId = new ComboBox<>("Policy event");
    private final ComboBox<String> purchasePolicyType = new ComboBox<>("Purchase rule type");
    private final IntegerField policyAge = new IntegerField("Min age");
    private final IntegerField policyMaxTickets = new IntegerField("Max tickets");
    private final IntegerField policyMinTickets = new IntegerField("Min tickets");
    private final ComboBox<String> policyComposition = new ComboBox<>("Composition");
    private final ComboBox<String> discountType = new ComboBox<>("Discount type");
    private final BigDecimalField discountPercent = new BigDecimalField("Discount %");
    private final TextField couponCodeField = new TextField("Coupon code");
    private final DateTimePicker couponExpiry = new DateTimePicker("Coupon expiry");
    private final ComboBox<String> discountConditionType = new ComboBox<>("Condition type");
    private final IntegerField conditionMinTickets = new IntegerField("Cond. min tickets");
    private final IntegerField conditionMaxTickets = new IntegerField("Cond. max tickets");
    private final DateTimePicker conditionFrom = new DateTimePicker("Cond. from date");
    private final DateTimePicker conditionTo = new DateTimePicker("Cond. to date");
    private final ComboBox<String> discountComposition = new ComboBox<>("Discount composition");
    private final Span policyStatus = new Span("View and manage purchase and discount policies.");
    private final Span currentPolicyDisplay = new Span();
    public CompanyView(CompanyPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1180px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configurePickers();
        configureCompanyEventsGrid();
        configurePurchasesGrid();
        configureDisplays();
        initModePanels();
        initModeNavigation();
        attachModePanels();

        modeContent.setPadding(false);
        modeContent.setSpacing(true);
        modeContent.setWidthFull();
        modeTabs.setWidthFull();

        add(
                new H2("Company"),
                new Paragraph("Choose a section below. Each mode shows only the controls for that area."),
                new Paragraph("Application services still enforce authorization for every action and their responses are shown in the status area."),
                sessionStatus,
                memberOnlyCompanyHint,
                modeTabs,
                modeContent
        );
        selectMode(CompanyMode.LOOKUP);
        refreshSessionStatus();
    }

    private void initModePanels() {
        VerticalLayout lookupPanel = new VerticalLayout(publicCompanySection(), publicEventMapSection());
        lookupPanel.setPadding(false);
        lookupPanel.setSpacing(true);

        Map<CompanyMode, VerticalLayout> panels = Map.of(
                CompanyMode.LOOKUP, lookupPanel,
                CompanyMode.FOUNDER, openCompanySection(),
                CompanyMode.PERSONNEL, personnelSection(),
                CompanyMode.LIFECYCLE, lifecycleSection(),
                CompanyMode.EVENTS, eventManagementSection(),
                CompanyMode.INVENTORY, inventorySection(),
                CompanyMode.POLICIES, policySection(),
                CompanyMode.REPORTS, reportingSection()
        );

        panelByTab.clear();
        tabByMode.clear();
        for (CompanyMode mode : CompanyMode.values()) {
            Tab tab = new Tab(mode.label);
            tabByMode.put(mode, tab);
            panelByTab.put(tab, panels.get(mode));
        }
    }

    private void initModeNavigation() {
        modeTabs.removeAll();
        for (CompanyMode mode : CompanyMode.values()) {
            modeTabs.add(tabByMode.get(mode));
        }
        modeTabs.addSelectedChangeListener(event -> {
            Tab tab = event.getSelectedTab();
            if (tab != null) {
                showPanelForTab(tab);
            }
        });
    }

    private void attachModePanels() {
        modeContent.removeAll();
        for (CompanyMode mode : CompanyMode.values()) {
            VerticalLayout panel = panelByTab.get(tabByMode.get(mode));
            if (panel != null) {
                panel.setVisible(false);
                modeContent.add(panel);
            }
        }
    }

    private void selectMode(CompanyMode mode) {
        Tab tab = tabByMode.get(mode);
        modeTabs.setSelectedTab(tab);
        showPanelForTab(tab);
    }

    private void showPanelForTab(Tab tab) {
        panelByTab.values().forEach(panel -> panel.setVisible(false));
        VerticalLayout panel = panelByTab.get(tab);
        if (panel != null) {
            panel.setVisible(true);
        }
    }

    private void configureFields() {
        role.setItems(StaffAppointment.StaffRole.values());
        role.setItemLabelGenerator(StaffAppointment.StaffRole::name);
        role.setValue(StaffAppointment.StaffRole.MANAGER);
        permissions.setItems(ManagerPermission.values());
        targetMember.setItemLabelGenerator(PersonnelTarget::label);
        targetMember.setPlaceholder("Select personnel after choosing a company");

        eventCategory.setItems(EventCategory.values());
        eventCategory.setItemLabelGenerator(EventCategory::name);
        eventCategory.setValue(EventCategory.CONCERT);

        lockMinutes.setMin(1);
        lockMinutes.setValue(15);
        gaCapacity.setMin(1);
        gaCapacity.setValue(100);
        capacityDelta.setMin(1);
        capacityDelta.setValue(1);

        inventoryZoneId.setPlaceholder("Zone UUID");
        seatId.setPlaceholder("Assigned seat UUID");

        markRequiredFields();
    }

    private void markRequiredFields() {
        markRequired(openCompanyName, "Company name is required.");

        markRequired(personnelCompanyName, "Select a company.");
        markRequired(targetMember, "Target member is required.");
        markRequired(role, "Select a role.");

        markRequired(eventCompanyName, "Select a company.");
        markRequired(eventName, "Event name is required.");
        markRequired(eventCategory, "Select a category.");
        markRequired(startTime, "Start time is required.");
        markRequired(endTime, "End time is required.");
        markRequired(lockMinutes, "Lock minutes is required.");
        markRequired(zoneName, "Zone name is required.");
        markRequired(zonePrice, "Zone price is required.");
        markRequired(gaCapacity, "GA capacity is required.");
        markRequired(sectionName, "Venue section is required.");
    }

    private void configurePickers() {
        for (ComboBox<CompanySummaryDTO> picker : List.of(
                infoCompanyName, personnelCompanyName, eventCompanyName,
                inventoryCompanyName, lifecycleCompanyName, reportingCompanyName, policyCompanyName)) {
            picker.setItemLabelGenerator(CompanySummaryDTO::name);
            picker.setPlaceholder("Search by company name");
        }

        eventId.setItemLabelGenerator(EventSummaryDTO::name);
        eventId.setPlaceholder("Select a company first");
        inventoryEventId.setItemLabelGenerator(EventSummaryDTO::name);
        inventoryEventId.setPlaceholder("Select a company first");
        lookupEventPicker.setItemLabelGenerator(EventSummaryDTO::name);
        lookupEventPicker.setPlaceholder("Search published events");

        policyEventId.setItemLabelGenerator(EventSummaryDTO::name);
        policyEventId.setPlaceholder("Optional — leave empty for company-level");
        policyEventId.setClearButtonVisible(true);

        // Event pickers cascade from the company selected in their section.
        personnelCompanyName.addValueChangeListener(e -> refreshPersonnelContext());
        lifecycleCompanyName.addValueChangeListener(e -> refreshLifecycleAccess());
        eventCompanyName.addValueChangeListener(e -> reloadCompanyEvents(eventId, e.getValue()));
        inventoryCompanyName.addValueChangeListener(e -> reloadCompanyEvents(inventoryEventId, e.getValue()));
        policyCompanyName.addValueChangeListener(e -> reloadCompanyEvents(policyEventId, e.getValue()));
    }

    private void reloadCompanyEvents(ComboBox<EventSummaryDTO> picker, CompanySummaryDTO company) {
        picker.clear();
        List<EventSummaryDTO> events = company == null ? List.of() : orEmpty(presenter.listCompanyEvents(company.name()));
        picker.setItems(events);
        // The policy picker keeps its "optional / company-level" hint; the others reflect cascade state.
        if (picker != policyEventId) {
            picker.setPlaceholder(company == null
                    ? "Select a company first"
                    : events.isEmpty() ? "No events for this company" : "Select an event");
        }
    }

    private void populatePickerItems() {
        List<CompanySummaryDTO> companies = orEmpty(presenter.searchCompanies(""));
        for (ComboBox<CompanySummaryDTO> picker : List.of(
                infoCompanyName, personnelCompanyName, eventCompanyName,
                inventoryCompanyName, reportingCompanyName, policyCompanyName)) {
            picker.setItems(companies);
        }
        refreshLifecycleCompanies();
        lookupEventPicker.setItems(orEmpty(presenter.searchBrowsableEvents()));
    }

    private void refreshLifecycleCompanies() {
        CompanySummaryDTO selected = lifecycleCompanyName.getValue();
        List<CompanySummaryDTO> companies = orEmpty(presenter.searchLifecycleCompanies(""));
        lifecycleCompanyName.setItems(companies);
        if (selected != null) {
            companies.stream()
                    .filter(company -> company.name().equals(selected.name()))
                    .findFirst()
                    .ifPresentOrElse(lifecycleCompanyName::setValue, lifecycleCompanyName::clear);
        }
    }

    private static <T> List<T> orEmpty(List<T> items) {
        return items == null ? List.of() : items;
    }

    private void configureCompanyEventsGrid() {
        companyEventsGrid.setId("company-events-grid");
        companyEventsGrid.addColumn(EventSummaryDTO::name).setHeader("Event").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> event.category().name()).setHeader("Category").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> formatInstant(event.schedule().getStartTime())).setHeader("Starts").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> event.status().name()).setHeader("Status").setAutoWidth(true);
        companyEventsGrid.setMinHeight("180px");
    }

    private void configurePurchasesGrid() {
        purchasesGrid.setId("company-purchases-grid");
        purchasesGrid.addColumn(purchase -> purchase.purchaseId().toString()).setHeader("Purchase ID").setAutoWidth(true);
        purchasesGrid.addColumn(PurchaseRecordDTO::eventName).setHeader("Event").setAutoWidth(true);
        purchasesGrid.addColumn(PurchaseRecordDTO::companyName).setHeader("Company").setAutoWidth(true);
        purchasesGrid.addColumn(purchase -> formatPrice(purchase.amount())).setHeader("Amount").setAutoWidth(true);
        purchasesGrid.addColumn(purchase -> formatInstant(purchase.purchasedAt())).setHeader("Purchased at").setAutoWidth(true);
        purchasesGrid.setMinHeight("180px");
    }

    private void configureDisplays() {
        orgChartDisplay.setPadding(false);
        orgChartDisplay.add(new Paragraph("Load an organization chart to view company personnel."));
        eventMapDisplay.setPadding(false);
        eventMapDisplay.add(new Paragraph("Load an event map to inspect public map data."));
        salesReportDisplay.setPadding(false);
        salesReportDisplay.add(new Paragraph("Load a sales report to see totals."));
    }

    private VerticalLayout publicCompanySection() {
        Button loadInfo = new Button("Load company info", event -> loadCompanyInfo());

        FormLayout infoForm = new FormLayout(infoCompanyName);

        VerticalLayout section = new VerticalLayout(
                new H3("Public company lookup"),
                new Paragraph("Public company details and published events are visible without company-management permissions."),
                infoForm,
                loadInfo,
                companyInfoStatus,
                companyEventsGrid
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout publicEventMapSection() {
        Button loadMap = new Button("Load event map", event -> loadEventMap());
        FormLayout mapForm = new FormLayout(lookupEventPicker);
        HorizontalLayout mapActions = new HorizontalLayout(loadMap);
        mapActions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H4("Event map (read-only)"),
                new Paragraph("Inspect published event map data. Inventory changes are under the Inventory section."),
                mapForm,
                mapActions,
                eventMapDisplay
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout openCompanySection() {
        Button openCompany = new Button("Open company", event -> openCompany());

        FormLayout form = new FormLayout(openCompanyName, openCompanyDescription);
        VerticalLayout section = new VerticalLayout(
                new H3("Founder company setup"),
                new Paragraph("Register a new production company and become its founder."),
                form,
                openCompany
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout personnelSection() {
        Button offerRole = new Button("Offer role appointment", event -> handlePersonnelResult(presenter.offerRoleAppointment(
                companyNameOf(personnelCompanyName),
                selectedTargetMemberId(),
                role.getValue(),
                permissions.getSelectedItems()
        )));
        Button acceptOffer = new Button("Accept role offer", event -> handlePersonnelResult(
                presenter.respondToRoleOffer(parseUuid(offerId, "role offer"), true)));
        Button rejectOffer = new Button("Reject role offer", event -> handlePersonnelResult(
                presenter.respondToRoleOffer(parseUuid(offerId, "role offer"), false)));
        revokePersonnelButton = new Button("Revoke personnel", event -> {
            ActionResult result = presenter.revokePersonnel(companyNameOf(personnelCompanyName), selectedTargetMemberId());
            handlePersonnelResult(result);
            if (result.success()) {
                refreshPersonnelContext();
            }
        });
        changeManagerPermissionsButton = new Button("Change manager permissions", event -> handlePersonnelResult(
                presenter.changeManagerPermissions(companyNameOf(personnelCompanyName), selectedTargetMemberId(),
                        permissions.getSelectedItems())));
        Button relinquish = new Button("Relinquish ownership", event -> handlePersonnelResult(
                presenter.relinquishOwnership(companyNameOf(personnelCompanyName))));
        Button loadOrgChart = new Button("Load organization chart", event -> loadOrganizationChart());

        FormLayout form = new FormLayout(personnelCompanyName, targetMember, role, permissions, offerId);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        HorizontalLayout actions = new HorizontalLayout(offerRole, acceptOffer, rejectOffer, revokePersonnelButton, changeManagerPermissionsButton, relinquish, loadOrgChart);
        actions.setAlignItems(Alignment.BASELINE);
        actions.getStyle().set("flex-wrap", "wrap");
        refreshPersonnelAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Personnel and roles"),
                new Paragraph("Owners appoint managers and other owners. Members can accept or reject role offers."),
                new H4("Role appointment and personnel"),
                form,
                personnelAccessHint,
                actions,
                personnelStatus,
                orgChartDisplay
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout eventManagementSection() {
        Button createEvent = new Button("Create company event", event -> createEvent());
        Button editEvent = new Button("Edit event details", event -> editEvent());
        Button publishEvent = new Button("Publish event", event -> handleEventAction(presenter.publishEvent(selectedEventId(eventId))));
        Button cancelEvent = new Button("Cancel event", event -> handleEventAction(presenter.cancelEvent(selectedEventId(eventId))));
        Button designHall = new Button("Design hall layout (visual)", event -> openVenueDesigner());

        FormLayout createForm = new FormLayout(
                eventCompanyName,
                eventName,
                eventDescription,
                eventCategory,
                startTime,
                endTime,
                doorsOpenTime,
                lockMinutes,
                zoneName,
                zonePrice,
                gaCapacity,
                sectionName
        );
        createForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        FormLayout editForm = new FormLayout(eventId, newEventName, newEventDescription, newArtist, newStartTime, newEndTime, newDoorsOpenTime);
        editForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        HorizontalLayout actions = new HorizontalLayout(createEvent, editEvent, publishEvent, cancelEvent, designHall);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Event management"),
                new Paragraph("Create, edit, publish, and cancel company events."),
                new H4("Create and edit"),
                createForm,
                editForm,
                actions,
                eventStatus
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout inventorySection() {
        Button addSeat = new Button("Add seat", event -> handleInventoryResult(presenter.addSeat(
                selectedEventId(inventoryEventId),
                parseUuid(inventoryZoneId, "zone"),
                seatRow.getValue(),
                seatNumber.getValue()
        )));
        Button removeSeat = new Button("Remove seat", event -> handleInventoryResult(presenter.removeSeat(
                selectedEventId(inventoryEventId),
                parseUuid(inventoryZoneId, "zone"),
                parseUuid(seatId, "seat")
        )));
        Button increaseCapacity = new Button("Increase GA capacity", event -> handleInventoryResult(presenter.increaseGACapacity(
                selectedEventId(inventoryEventId),
                parseUuid(inventoryZoneId, "zone"),
                capacityDelta.getValue()
        )));
        Button decreaseCapacity = new Button("Decrease GA capacity", event -> handleInventoryResult(presenter.decreaseGACapacity(
                selectedEventId(inventoryEventId),
                parseUuid(inventoryZoneId, "zone"),
                capacityDelta.getValue()
        )));
        Button setPrice = new Button("Set zone price", event -> handleInventoryResult(presenter.setZonePrice(
                selectedEventId(inventoryEventId),
                parseUuid(inventoryZoneId, "zone"),
                zonePriceUpdate.getValue()
        )));

        FormLayout form = new FormLayout(inventoryCompanyName, inventoryEventId, inventoryZoneId, seatRow, seatNumber, seatId, capacityDelta, zonePriceUpdate);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));
        HorizontalLayout actions = new HorizontalLayout(addSeat, removeSeat, increaseCapacity, decreaseCapacity, setPrice);
        actions.setAlignItems(Alignment.BASELINE);
        actions.getStyle().set("flex-wrap", "wrap");
        inventoryManagementControls = new VerticalLayout(actions);
        inventoryManagementControls.setPadding(false);

        VerticalLayout section = new VerticalLayout(
                new H3("Inventory management"),
                new Paragraph("Adjust seats, GA capacity, and zone pricing for an event."),
                form,
                inventoryManagementControls,
                inventoryStatus
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout lifecycleSection() {
        suspendCompanyButton = new Button("Suspend company", event -> handleLifecycleResult(presenter.suspendCompany(companyNameOf(lifecycleCompanyName))));
        reopenCompanyButton = new Button("Reopen company", event -> handleLifecycleResult(presenter.reopenCompany(companyNameOf(lifecycleCompanyName))));
        closeCompanyButton = new Button("Close company", event -> handleLifecycleResult(presenter.closeCompany(companyNameOf(lifecycleCompanyName))));
        HorizontalLayout actions = new HorizontalLayout(suspendCompanyButton, reopenCompanyButton, closeCompanyButton);
        actions.setAlignItems(Alignment.BASELINE);
        refreshLifecycleAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Company lifecycle"),
                new Paragraph("Founders may suspend, reopen, or close their company."),
                lifecycleCompanyName,
                lifecycleAccessHint,
                actions,
                lifecycleStatus
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout reportingSection() {
        Button loadHistory = new Button("Load company purchase history", event -> loadPurchaseHistory());
        Button loadSalesReport = new Button("Load sales report", event -> loadSalesReport());
        HorizontalLayout actions = new HorizontalLayout(loadHistory, loadSalesReport);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("History and reporting"),
                new Paragraph("View purchase history and sales totals for a company."),
                reportingCompanyName,
                actions,
                reportingStatus,
                purchasesGrid,
                salesReportDisplay
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout policySection() {
        purchasePolicyType.setItems("Age restriction", "Max quantity", "Min quantity");
        purchasePolicyType.setValue("Age restriction");
        policyAge.setMin(1);
        policyAge.setValue(18);
        policyMaxTickets.setMin(1);
        policyMaxTickets.setValue(5);
        policyMinTickets.setMin(1);
        policyMinTickets.setValue(2);
        policyComposition.setItems("Single rule", "AND (all must pass)", "OR (any can pass)");
        policyComposition.setValue("Single rule");

        discountType.setItems("Simple (flat %)", "Conditional (% with condition)", "Coupon (% with code)");
        discountType.setValue("Simple (flat %)");
        discountPercent.setValue(BigDecimal.TEN);
        couponCodeField.setPlaceholder("e.g. EARLY20");
        discountConditionType.setItems("Min tickets", "Max tickets", "Date range");
        discountConditionType.setValue("Min tickets");
        conditionMinTickets.setMin(1);
        conditionMinTickets.setValue(2);
        conditionMaxTickets.setMin(1);
        conditionMaxTickets.setValue(5);
        discountComposition.setItems("Single discount", "MAX (best discount wins)", "SUM (stack all)");
        discountComposition.setValue("Single discount");

        Button loadPurchasePolicy = new Button("Load purchase policy", e -> loadPurchasePolicy());
        Button setPurchasePolicy = new Button("Set purchase policy", e -> setPurchasePolicy());
        Button removePurchasePolicy = new Button("Remove purchase policy", e -> removePurchasePolicy());
        Button loadDiscountPolicy = new Button("Load discount policy", e -> loadDiscountPolicy());
        Button setDiscountPolicy = new Button("Set discount policy", e -> setDiscountPolicy());
        Button removeDiscountPolicy = new Button("Remove discount policy", e -> removeDiscountPolicy());

        FormLayout targetForm = new FormLayout(policyCompanyName, policyEventId);
        targetForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));

        FormLayout purchaseForm = new FormLayout(purchasePolicyType, policyAge, policyMaxTickets, policyMinTickets, policyComposition);
        purchaseForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        HorizontalLayout purchaseActions = new HorizontalLayout(loadPurchasePolicy, setPurchasePolicy, removePurchasePolicy);
        purchaseActions.setAlignItems(Alignment.BASELINE);

        FormLayout discountForm = new FormLayout(discountType, discountPercent, couponCodeField, couponExpiry,
                discountConditionType, conditionMinTickets, conditionMaxTickets, conditionFrom, conditionTo, discountComposition);
        discountForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        HorizontalLayout discountActions = new HorizontalLayout(loadDiscountPolicy, setDiscountPolicy, removeDiscountPolicy);
        discountActions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Purchase and discount policies"),
                new Paragraph("Define and manage purchase rules and discount policies at company or event level."),
                targetForm,
                new H4("Purchase policy"),
                purchaseForm,
                purchaseActions,
                new H4("Discount policy"),
                discountForm,
                discountActions,
                policyStatus,
                currentPolicyDisplay
        );
        section.setPadding(false);
        return section;
    }

    private void loadPurchasePolicy() {
        UUID eventId = selectedEventId(policyEventId);
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventPurchasePolicy(eventId);
        } else {
            result = presenter.loadCompanyPurchasePolicy(companyNameOf(policyCompanyName));
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText(result.success() ? "Current: " + result.description() : "");
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void setPurchasePolicy() {
        IPurchasePolicy policy = buildPurchasePolicy();
        if (policy == null) return;

        UUID eventId = selectedEventId(policyEventId);
        ActionResult result;
        if (eventId != null) {
            result = presenter.setEventPurchasePolicy(eventId, policy);
        } else {
            result = presenter.setCompanyPurchasePolicy(companyNameOf(policyCompanyName), policy);
        }
        policyStatus.setText(result.message());
        notify(result);
    }

    private void removePurchasePolicy() {
        UUID eventId = selectedEventId(policyEventId);
        ActionResult result;
        if (eventId != null) {
            result = presenter.removeEventPurchasePolicy(eventId);
        } else {
            result = presenter.removeCompanyPurchasePolicy(companyNameOf(policyCompanyName));
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText("");
        notify(result);
    }

    private void loadDiscountPolicy() {
        UUID eventId = selectedEventId(policyEventId);
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventDiscountPolicy(eventId);
        } else {
            result = presenter.loadCompanyDiscountPolicy(companyNameOf(policyCompanyName));
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText(result.success() ? "Current: " + result.description() : "");
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void setDiscountPolicy() {
        IDiscountPolicy policy = buildDiscountPolicy();
        if (policy == null) return;

        UUID eventId = selectedEventId(policyEventId);
        ActionResult result;
        if (eventId != null) {
            result = presenter.setEventDiscountPolicy(eventId, policy);
        } else {
            result = presenter.setCompanyDiscountPolicy(companyNameOf(policyCompanyName), policy);
        }
        policyStatus.setText(result.message());
        notify(result);
    }

    private void removeDiscountPolicy() {
        UUID eventId = selectedEventId(policyEventId);
        ActionResult result;
        if (eventId != null) {
            result = presenter.removeEventDiscountPolicy(eventId);
        } else {
            result = presenter.removeCompanyDiscountPolicy(companyNameOf(policyCompanyName));
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText("");
        notify(result);
    }

    private IPurchasePolicy buildPurchasePolicy() {
        IPurchasePolicy leaf = buildSinglePurchaseRule();
        if (leaf == null) return null;

        String composition = policyComposition.getValue();
        if (composition == null || "Single rule".equals(composition)) {
            return leaf;
        }

        List<IPurchasePolicy> rules = new ArrayList<>();
        rules.add(leaf);
        if ("Age restriction".equals(purchasePolicyType.getValue())) {
            if (policyMaxTickets.getValue() != null && policyMaxTickets.getValue() > 0) {
                rules.add(new MaxQuantityPolicy(policyMaxTickets.getValue()));
            }
            if (policyMinTickets.getValue() != null && policyMinTickets.getValue() > 0) {
                rules.add(new MinQuantityPolicy(policyMinTickets.getValue()));
            }
        } else if ("Max quantity".equals(purchasePolicyType.getValue())) {
            if (policyAge.getValue() != null && policyAge.getValue() > 0) {
                rules.add(new AgeRestrictionPolicy(policyAge.getValue()));
            }
            if (policyMinTickets.getValue() != null && policyMinTickets.getValue() > 0) {
                rules.add(new MinQuantityPolicy(policyMinTickets.getValue()));
            }
        } else {
            if (policyAge.getValue() != null && policyAge.getValue() > 0) {
                rules.add(new AgeRestrictionPolicy(policyAge.getValue()));
            }
            if (policyMaxTickets.getValue() != null && policyMaxTickets.getValue() > 0) {
                rules.add(new MaxQuantityPolicy(policyMaxTickets.getValue()));
            }
        }

        if (rules.size() == 1) return leaf;

        if ("AND (all must pass)".equals(composition)) {
            return new AndPolicy(rules);
        }
        return new OrPolicy(rules);
    }

    private IPurchasePolicy buildSinglePurchaseRule() {
        String type = purchasePolicyType.getValue();
        try {
            if ("Age restriction".equals(type)) {
                Integer age = policyAge.getValue();
                if (age == null || age <= 0) {
                    policyStatus.setText("Min age must be positive.");
                    UiMessages.error("Min age must be positive.");
                    return null;
                }
                return new AgeRestrictionPolicy(age);
            } else if ("Max quantity".equals(type)) {
                Integer max = policyMaxTickets.getValue();
                if (max == null || max <= 0) {
                    policyStatus.setText("Max tickets must be positive.");
                    UiMessages.error("Max tickets must be positive.");
                    return null;
                }
                return new MaxQuantityPolicy(max);
            } else {
                Integer min = policyMinTickets.getValue();
                if (min == null || min <= 0) {
                    policyStatus.setText("Min tickets must be positive.");
                    UiMessages.error("Min tickets must be positive.");
                    return null;
                }
                return new MinQuantityPolicy(min);
            }
        } catch (IllegalArgumentException ex) {
            policyStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
            return null;
        }
    }

    private IDiscountPolicy buildDiscountPolicy() {
        IDiscountPolicy leaf = buildSingleDiscountPolicy();
        if (leaf == null) return null;

        String composition = discountComposition.getValue();
        if (composition == null || "Single discount".equals(composition)) {
            return leaf;
        }

        List<IDiscountPolicy> policies = new ArrayList<>();
        policies.add(leaf);

        if ("MAX (best discount wins)".equals(composition)) {
            return new MaxCompositeDiscount(policies);
        }
        return new SumCompositeDiscount(policies);
    }

    private IDiscountPolicy buildSingleDiscountPolicy() {
        String type = discountType.getValue();
        BigDecimal percent = discountPercent.getValue();
        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            policyStatus.setText("Discount % must be between 0 and 100.");
            UiMessages.error("Discount % must be between 0 and 100.");
            return null;
        }

        try {
            if ("Simple (flat %)".equals(type)) {
                return new SimpleDiscount(percent);
            } else if ("Coupon (% with code)".equals(type)) {
                String code = couponCodeField.getValue();
                if (code == null || code.isBlank()) {
                    policyStatus.setText("Coupon code is required.");
                    UiMessages.error("Coupon code is required.");
                    return null;
                }
                LocalDateTime expiryLdt = couponExpiry.getValue();
                if (expiryLdt == null) {
                    policyStatus.setText("Coupon expiry date is required.");
                    UiMessages.error("Coupon expiry date is required.");
                    return null;
                }
                Instant expiry = expiryLdt.atZone(ZoneId.systemDefault()).toInstant();
                return new CouponDiscount(percent, code, expiry);
            } else {
                IDiscountCondition condition = buildDiscountCondition();
                if (condition == null) return null;
                return new ConditionalDiscount(percent, condition);
            }
        } catch (IllegalArgumentException ex) {
            policyStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
            return null;
        }
    }

    private IDiscountCondition buildDiscountCondition() {
        String condType = discountConditionType.getValue();
        try {
            if ("Min tickets".equals(condType)) {
                Integer min = conditionMinTickets.getValue();
                if (min == null || min <= 0) {
                    policyStatus.setText("Condition min tickets must be positive.");
                    UiMessages.error("Condition min tickets must be positive.");
                    return null;
                }
                return new MinQuantityCondition(min);
            } else if ("Max tickets".equals(condType)) {
                Integer max = conditionMaxTickets.getValue();
                if (max == null || max <= 0) {
                    policyStatus.setText("Condition max tickets must be positive.");
                    UiMessages.error("Condition max tickets must be positive.");
                    return null;
                }
                return new MaxQuantityCondition(max);
            } else {
                LocalDateTime from = conditionFrom.getValue();
                LocalDateTime to = conditionTo.getValue();
                Instant fromInstant = from == null ? null : from.atZone(ZoneId.systemDefault()).toInstant();
                Instant toInstant = to == null ? null : to.atZone(ZoneId.systemDefault()).toInstant();
                return new DateRangeCondition(fromInstant, toInstant);
            }
        } catch (IllegalArgumentException ex) {
            policyStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
            return null;
        }
    }

    private void openCompany() {
        ActionResult result = presenter.openCompany(openCompanyName.getValue(), openCompanyDescription.getValue());
        handleCompanyAction(result);
        refreshSessionStatus();
    }

    private void loadCompanyInfo() {
        CompanyInfoResult result = presenter.loadCompanyInfo(companyNameOf(infoCompanyName));
        if (!result.success()) {
            companyInfoStatus.setText(result.message());
            companyEventsGrid.setItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        CompanyPublicDTO company = result.company();
        companyInfoStatus.setText(company.name() + " | " + nullToEmpty(company.description()));
        companyEventsGrid.setItems(company.events());
        UiMessages.success(result.message());
    }

    private void loadOrganizationChart() {
        OrgChartResult result = presenter.loadOrganizationChart(companyNameOf(personnelCompanyName));
        orgChartDisplay.removeAll();
        if (!result.success()) {
            personnelStatus.setText(result.message());
            orgChartDisplay.add(new Paragraph(result.message()));
            setPersonnelTargetItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        personnelStatus.setText(result.message());
        setPersonnelTargetItems(result.roots());
        if (result.roots().isEmpty()) {
            orgChartDisplay.add(new Paragraph("No personnel found."));
        } else {
            for (OrgNodeDTO root : result.roots()) {
                orgChartDisplay.add(orgNode(root));
            }
        }
        UiMessages.success(result.message());
    }

    private void refreshPersonnelContext() {
        CompanyPresenter.PersonnelAccessResult access = refreshPersonnelAccess();
        if (access.canManagePersonnel()) {
            targetMember.setEnabled(true);
            reloadPersonnelTargets();
        } else {
            setPersonnelTargetsUnavailable("Owner-only personnel list unavailable");
        }
    }

    private void reloadPersonnelTargets() {
        String companyName = companyNameOf(personnelCompanyName);
        if (companyName == null) {
            setPersonnelTargetItems(List.of());
            return;
        }

        OrgChartResult result = presenter.loadOrganizationChart(companyName);
        setPersonnelTargetItems(result.success() ? result.roots() : List.of());
    }

    private void setPersonnelTargetItems(List<OrgNodeDTO> roots) {
        List<PersonnelTarget> targets = new ArrayList<>();
        collectPersonnelTargets(roots, targets);
        targetMember.clear();
        targetMember.setItems(targets);
        targetMember.setEnabled(true);
        targetMember.setPlaceholder(targets.isEmpty()
                ? "No personnel available"
                : "Select personnel");
    }

    private void setPersonnelTargetsUnavailable(String placeholder) {
        targetMember.clear();
        targetMember.setItems(List.of());
        targetMember.setInvalid(false);
        targetMember.setEnabled(false);
        targetMember.setPlaceholder(placeholder);
    }

    private void collectPersonnelTargets(List<OrgNodeDTO> nodes, List<PersonnelTarget> targets) {
        if (nodes == null) {
            return;
        }
        for (OrgNodeDTO node : nodes) {
            if (!node.revoked()) {
                targets.add(new PersonnelTarget(node.memberId(), node.username(), node.role()));
            }
            collectPersonnelTargets(node.subordinates(), targets);
        }
    }

    private CompanyPresenter.PersonnelAccessResult refreshPersonnelAccess() {
        if (changeManagerPermissionsButton == null || revokePersonnelButton == null) {
            return CompanyPresenter.PersonnelAccessResult.denied("Select a company to show owner-only permission controls.");
        }
        String companyName = companyNameOf(personnelCompanyName);
        if (companyName == null) {
            revokePersonnelButton.setVisible(false);
            changeManagerPermissionsButton.setVisible(false);
            personnelAccessHint.setText("Select a company to show owner-only permission controls.");
            personnelAccessHint.setVisible(true);
            return CompanyPresenter.PersonnelAccessResult.denied("Select a company to show owner-only permission controls.");
        }

        CompanyPresenter.PersonnelAccessResult result = presenter.loadPersonnelAccess(companyName);
        revokePersonnelButton.setVisible(result.canManagePersonnel());
        changeManagerPermissionsButton.setVisible(result.canManagePersonnel());
        personnelAccessHint.setText(result.message());
        personnelAccessHint.setVisible(!result.canManagePersonnel());
        return result;
    }

    private UUID selectedTargetMemberId() {
        PersonnelTarget selected = targetMember.getValue();
        return selected == null ? null : selected.memberId();
    }

    private void refreshLifecycleAccess() {
        if (suspendCompanyButton == null || reopenCompanyButton == null || closeCompanyButton == null) {
            return;
        }
        String companyName = companyNameOf(lifecycleCompanyName);
        if (companyName == null) {
            setLifecycleControlsVisible(false);
            lifecycleAccessHint.setText("Select a company to show founder-only lifecycle controls.");
            lifecycleAccessHint.setVisible(true);
            return;
        }

        CompanyPresenter.LifecycleAccessResult result = presenter.loadLifecycleAccess(companyName);
        setLifecycleControlsVisible(result.canManageLifecycle());
        lifecycleAccessHint.setText(result.message());
        lifecycleAccessHint.setVisible(!result.canManageLifecycle());
    }

    private void setLifecycleControlsVisible(boolean visible) {
        suspendCompanyButton.setVisible(visible);
        reopenCompanyButton.setVisible(visible);
        closeCompanyButton.setVisible(visible);
    }

    private void createEvent() {
        CompanySummaryDTO company = eventCompanyName.getValue();
        EventActionResult result = presenter.createEvent(
                companyNameOf(eventCompanyName),
                eventName.getValue(),
                eventDescription.getValue(),
                eventCategory.getValue(),
                instant(startTime),
                instant(endTime),
                instant(doorsOpenTime),
                lockMinutes.getValue(),
                zoneName.getValue(),
                zonePrice.getValue(),
                gaCapacity.getValue(),
                sectionName.getValue()
        );
        handleEventAction(result);
        if (result.success() && result.eventId() != null && company != null) {
            // Refresh the management pickers so the new event is selectable straight away.
            selectEventInPicker(eventId, company.name(), result.eventId());
            inventoryCompanyName.setValue(company);
            selectEventInPicker(inventoryEventId, company.name(), result.eventId());
        }
    }

    private void openVenueDesigner() {
        String company = companyNameOf(eventCompanyName);
        if (company == null) {
            UiMessages.error("Select a company first.");
            return;
        }
        new VenueDesignerDialog(presenter, company).open();
    }

    private void selectEventInPicker(ComboBox<EventSummaryDTO> picker, String companyName, UUID eventId) {
        List<EventSummaryDTO> events = orEmpty(presenter.listCompanyEvents(companyName));
        picker.setItems(events);
        events.stream()
                .filter(e -> e.id().equals(eventId))
                .findFirst()
                .ifPresent(picker::setValue);
    }

    private void editEvent() {
        EventActionResult result = presenter.editEvent(
                selectedEventId(eventId),
                newEventName.getValue(),
                newEventDescription.getValue(),
                newArtist.getValue(),
                instant(newStartTime),
                instant(newEndTime),
                instant(newDoorsOpenTime)
        );
        handleEventAction(result);
    }

    private void loadEventMap() {
        EventMapResult result = presenter.loadEventMap(selectedEventId(lookupEventPicker));
        eventMapDisplay.removeAll();
        if (!result.success()) {
            inventoryStatus.setText(result.message());
            eventMapDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        inventoryStatus.setText(result.message());
        renderEventMap(result.eventMap());
        UiMessages.success(result.message());
    }

    private void loadPurchaseHistory() {
        PurchaseHistoryResult result = presenter.loadPurchaseHistory(companyNameOf(reportingCompanyName));
        if (!result.success()) {
            reportingStatus.setText(result.message());
            purchasesGrid.setItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        reportingStatus.setText(result.message());
        purchasesGrid.setItems(result.purchases());
        UiMessages.success(result.message());
    }

    private void loadSalesReport() {
        SalesReportResult result = presenter.loadSalesReport(companyNameOf(reportingCompanyName));
        salesReportDisplay.removeAll();
        if (!result.success()) {
            reportingStatus.setText(result.message());
            salesReportDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        SalesReportDTO report = result.report();
        reportingStatus.setText(result.message());
        salesReportDisplay.add(
                new Span("Company: " + report.companyName()),
                new Span("Requested by: " + report.requestedByMemberId()),
                new Span("Total purchases: " + report.totalPurchases()),
                new Span("Total revenue: " + formatPrice(report.totalRevenue()))
        );
        UiMessages.success(result.message());
    }

    private void handleCompanyAction(ActionResult result) {
        companyInfoStatus.setText(result.message());
        notify(result);
    }

    private void handlePersonnelResult(ActionResult result) {
        personnelStatus.setText(result.message());
        notify(result);
    }

    private void handleEventAction(ActionResult result) {
        eventStatus.setText(result.message());
        notify(result);
    }

    private void handleEventAction(EventActionResult result) {
        if (result.eventDetails() != null) {
            EventDetailsDTO details = result.eventDetails();
            eventStatus.setText(result.message() + " " + details.name() + " | " + details.status());
        } else if (result.eventId() != null) {
            eventStatus.setText(result.message() + " Event ID: " + result.eventId());
        } else {
            eventStatus.setText(result.message());
        }
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void handleInventoryResult(ActionResult result) {
        inventoryStatus.setText(result.message());
        notify(result);
    }

    private void handleLifecycleResult(ActionResult result) {
        lifecycleStatus.setText(result.message());
        if (result.success()) {
            refreshLifecycleCompanies();
            refreshLifecycleAccess();
        }
        notify(result);
    }

    private void notify(ActionResult result) {
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void renderEventMap(EventMapDTO eventMap) {
        eventMapDisplay.add(
                new H4(eventMap.eventName()),
                new Span("Company: " + eventMap.companyName()),
                new Span("Status: " + eventMap.status().name())
        );
        if (eventMap.zones().isEmpty()) {
            eventMapDisplay.add(new Paragraph("No inventory zones are available for this event."));
            return;
        }
        for (EventMapDTO.ZoneInfo zone : eventMap.zones()) {
            VerticalLayout content = new VerticalLayout(
                    new Span("Zone ID: " + zone.id()),
                    new Span("Type: " + zone.type()),
                    new Span("Price: " + formatPrice(zone.pricePerTicket()))
            );
            content.setPadding(false);
            if (zone.maxCapacity() != null) {
                content.add(
                        new Span("Capacity: " + zone.maxCapacity()),
                        new Span("Available: " + zone.availableCount()),
                        new Span("Sold: " + zone.soldCount())
                );
            } else {
                content.add(new Span("Seats: " + zone.seats().size()));
            }
            eventMapDisplay.add(new Details(zone.name(), content));
        }
    }

    private Details orgNode(OrgNodeDTO node) {
        VerticalLayout content = new VerticalLayout(
                new Span("Member ID: " + node.memberId()),
                new Span("Role: " + node.role()),
                new Span("Permissions: " + node.permissions()),
                new Span("Revoked: " + node.revoked())
        );
        content.setPadding(false);
        for (OrgNodeDTO child : node.subordinates()) {
            content.add(orgNode(child));
        }
        Details details = new Details(node.username(), content);
        details.setOpened(true);
        return details;
    }

    private UUID parseUuid(TextField field, String label) {
        String value = field.getValue();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String companyNameOf(ComboBox<CompanySummaryDTO> picker) {
        CompanySummaryDTO selected = picker.getValue();
        return selected == null ? null : selected.name();
    }

    private static UUID selectedEventId(ComboBox<EventSummaryDTO> picker) {
        EventSummaryDTO selected = picker.getValue();
        return selected == null ? null : selected.id();
    }

    private Instant instant(DateTimePicker picker) {
        LocalDateTime value = picker.getValue();
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
        populatePickerItems();
        boolean member = presenter.currentSessionState().loggedInMember();
        memberOnlyCompanyHint.setVisible(!member);
        for (CompanyMode mode : CompanyMode.values()) {
            Tab tab = tabByMode.get(mode);
            if (tab != null) {
                tab.setVisible(!mode.memberOnly || member);
            }
        }
        Tab selected = modeTabs.getSelectedTab();
        if (!member || selected == null || !selected.isVisible()) {
            selectMode(CompanyMode.LOOKUP);
        }
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : price.toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PersonnelTarget(UUID memberId, String username, StaffAppointment.StaffRole role) {
        private String label() {
            return username + " | " + memberId + " | " + role;
        }

        @Override
        public String toString() {
            return label();
        }
    }
}
