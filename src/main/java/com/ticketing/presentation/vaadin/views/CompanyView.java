package com.ticketing.presentation.vaadin.views;

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
    private final TextField infoCompanyName = new TextField("Company info name");
    private final Span companyInfoStatus = new Span("Load company information to view public details.");
    private final Grid<EventSummaryDTO> companyEventsGrid = new Grid<>(EventSummaryDTO.class, false);

    private final TextField personnelCompanyName = new TextField("Personnel company name");
    private final TextField targetMemberId = new TextField("Target member ID");
    private final ComboBox<StaffAppointment.StaffRole> role = new ComboBox<>("Role");
    private final CheckboxGroup<ManagerPermission> permissions = new CheckboxGroup<>("Manager permissions");
    private final TextField offerId = new TextField("Role offer ID");
    private final Span personnelStatus = new Span("Manage owner and manager appointments.");
    private final VerticalLayout orgChartDisplay = new VerticalLayout();

    private final TextField eventCompanyName = new TextField("Event company name");
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
    private final TextField eventId = new TextField("Event ID");
    private final TextField newEventName = new TextField("New event name");
    private final TextArea newEventDescription = new TextArea("New event description");
    private final TextField newArtist = new TextField("New artist");
    private final DateTimePicker newStartTime = new DateTimePicker("New start time");
    private final DateTimePicker newEndTime = new DateTimePicker("New end time");
    private final DateTimePicker newDoorsOpenTime = new DateTimePicker("New doors open time");
    private final Span eventStatus = new Span("Create, edit, or cancel company events.");

    private final TextField inventoryEventId = new TextField("Inventory event ID");
    private final TextField inventoryZoneId = new TextField("Inventory zone ID");
    private final TextField seatRow = new TextField("Seat row");
    private final TextField seatNumber = new TextField("Seat number");
    private final TextField seatId = new TextField("Seat ID");
    private final IntegerField capacityDelta = new IntegerField("Capacity delta");
    private final BigDecimalField zonePriceUpdate = new BigDecimalField("Zone price update");
    private final Span inventoryStatus = new Span("Load or manage supported inventory actions.");
    private final VerticalLayout eventMapDisplay = new VerticalLayout();

    private final TextField lifecycleCompanyName = new TextField("Lifecycle company name");
    private final Span lifecycleStatus = new Span("Suspend, reopen, or close companies.");

    private final TextField reportingCompanyName = new TextField("Reporting company name");
    private final Span reportingStatus = new Span("Load company purchase history and hierarchical sales reports.");
    private final Grid<PurchaseRecordDTO> purchasesGrid = new Grid<>(PurchaseRecordDTO.class, false);
    private final VerticalLayout salesReportDisplay = new VerticalLayout();

    private final TextField policyCompanyName = new TextField("Policy company name");
    private final TextField policyEventId = new TextField("Policy event ID");
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

        eventCategory.setItems(EventCategory.values());
        eventCategory.setItemLabelGenerator(EventCategory::name);
        eventCategory.setValue(EventCategory.CONCERT);

        lockMinutes.setMin(1);
        lockMinutes.setValue(15);
        gaCapacity.setMin(1);
        gaCapacity.setValue(100);
        capacityDelta.setMin(1);
        capacityDelta.setValue(1);

        eventId.setPlaceholder("Event UUID for edit/cancel");
        inventoryEventId.setPlaceholder("Event UUID");
        inventoryZoneId.setPlaceholder("Zone UUID");
        seatId.setPlaceholder("Assigned seat UUID");
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
        FormLayout mapForm = new FormLayout(inventoryEventId);
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
                personnelCompanyName.getValue(),
                parseUuid(targetMemberId, "target member"),
                role.getValue(),
                permissions.getSelectedItems()
        )));
        Button acceptOffer = new Button("Accept role offer", event -> handlePersonnelResult(
                presenter.respondToRoleOffer(parseUuid(offerId, "role offer"), true)));
        Button rejectOffer = new Button("Reject role offer", event -> handlePersonnelResult(
                presenter.respondToRoleOffer(parseUuid(offerId, "role offer"), false)));
        Button revoke = new Button("Revoke personnel", event -> handlePersonnelResult(presenter.revokePersonnel(
                personnelCompanyName.getValue(),
                parseUuid(targetMemberId, "target member")
        )));
        Button changePermissions = new Button("Change manager permissions", event -> handlePersonnelResult(
                presenter.changeManagerPermissions(personnelCompanyName.getValue(), parseUuid(targetMemberId, "target member"),
                        permissions.getSelectedItems())));
        Button relinquish = new Button("Relinquish ownership", event -> handlePersonnelResult(
                presenter.relinquishOwnership(personnelCompanyName.getValue())));
        Button loadOrgChart = new Button("Load organization chart", event -> loadOrganizationChart());

        FormLayout form = new FormLayout(personnelCompanyName, targetMemberId, role, permissions, offerId);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        HorizontalLayout actions = new HorizontalLayout(offerRole, acceptOffer, rejectOffer, revoke, changePermissions, relinquish, loadOrgChart);
        actions.setAlignItems(Alignment.BASELINE);
        actions.getStyle().set("flex-wrap", "wrap");

        VerticalLayout section = new VerticalLayout(
                new H3("Personnel and roles"),
                new Paragraph("Owners appoint managers and other owners. Managers accept offers and manage permissions."),
                new H4("Role appointment and personnel"),
                form,
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
        Button publishEvent = new Button("Publish event", event -> handleEventAction(presenter.publishEvent(parseUuid(eventId, "event"))));
        Button cancelEvent = new Button("Cancel event", event -> handleEventAction(presenter.cancelEvent(parseUuid(eventId, "event"))));

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

        HorizontalLayout actions = new HorizontalLayout(createEvent, editEvent, publishEvent, cancelEvent);
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
                parseUuid(inventoryEventId, "event"),
                parseUuid(inventoryZoneId, "zone"),
                seatRow.getValue(),
                seatNumber.getValue()
        )));
        Button removeSeat = new Button("Remove seat", event -> handleInventoryResult(presenter.removeSeat(
                parseUuid(inventoryEventId, "event"),
                parseUuid(inventoryZoneId, "zone"),
                parseUuid(seatId, "seat")
        )));
        Button increaseCapacity = new Button("Increase GA capacity", event -> handleInventoryResult(presenter.increaseGACapacity(
                parseUuid(inventoryEventId, "event"),
                parseUuid(inventoryZoneId, "zone"),
                capacityDelta.getValue()
        )));
        Button decreaseCapacity = new Button("Decrease GA capacity", event -> handleInventoryResult(presenter.decreaseGACapacity(
                parseUuid(inventoryEventId, "event"),
                parseUuid(inventoryZoneId, "zone"),
                capacityDelta.getValue()
        )));
        Button setPrice = new Button("Set zone price", event -> handleInventoryResult(presenter.setZonePrice(
                parseUuid(inventoryEventId, "event"),
                parseUuid(inventoryZoneId, "zone"),
                zonePriceUpdate.getValue()
        )));

        FormLayout form = new FormLayout(inventoryEventId, inventoryZoneId, seatRow, seatNumber, seatId, capacityDelta, zonePriceUpdate);
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
        Button suspend = new Button("Suspend company", event -> handleLifecycleResult(presenter.suspendCompany(lifecycleCompanyName.getValue())));
        Button reopen = new Button("Reopen company", event -> handleLifecycleResult(presenter.reopenCompany(lifecycleCompanyName.getValue())));
        Button close = new Button("Close company", event -> handleLifecycleResult(presenter.closeCompany(lifecycleCompanyName.getValue())));
        HorizontalLayout actions = new HorizontalLayout(suspend, reopen, close);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Company lifecycle"),
                new Paragraph("Founders may suspend, reopen, or close their company."),
                lifecycleCompanyName,
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
        policyEventId.setPlaceholder("Event UUID (leave empty for company-level)");

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
        UUID eventId = parseUuid(policyEventId, "event");
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventPurchasePolicy(eventId);
        } else {
            result = presenter.loadCompanyPurchasePolicy(policyCompanyName.getValue());
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

        UUID eventId = parseUuid(policyEventId, "event");
        ActionResult result;
        if (eventId != null) {
            result = presenter.setEventPurchasePolicy(eventId, policy);
        } else {
            result = presenter.setCompanyPurchasePolicy(policyCompanyName.getValue(), policy);
        }
        policyStatus.setText(result.message());
        notify(result);
    }

    private void removePurchasePolicy() {
        UUID eventId = parseUuid(policyEventId, "event");
        ActionResult result;
        if (eventId != null) {
            result = presenter.removeEventPurchasePolicy(eventId);
        } else {
            result = presenter.removeCompanyPurchasePolicy(policyCompanyName.getValue());
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText("");
        notify(result);
    }

    private void loadDiscountPolicy() {
        UUID eventId = parseUuid(policyEventId, "event");
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventDiscountPolicy(eventId);
        } else {
            result = presenter.loadCompanyDiscountPolicy(policyCompanyName.getValue());
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

        UUID eventId = parseUuid(policyEventId, "event");
        ActionResult result;
        if (eventId != null) {
            result = presenter.setEventDiscountPolicy(eventId, policy);
        } else {
            result = presenter.setCompanyDiscountPolicy(policyCompanyName.getValue(), policy);
        }
        policyStatus.setText(result.message());
        notify(result);
    }

    private void removeDiscountPolicy() {
        UUID eventId = parseUuid(policyEventId, "event");
        ActionResult result;
        if (eventId != null) {
            result = presenter.removeEventDiscountPolicy(eventId);
        } else {
            result = presenter.removeCompanyDiscountPolicy(policyCompanyName.getValue());
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
        CompanyInfoResult result = presenter.loadCompanyInfo(infoCompanyName.getValue());
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
        OrgChartResult result = presenter.loadOrganizationChart(personnelCompanyName.getValue());
        orgChartDisplay.removeAll();
        if (!result.success()) {
            personnelStatus.setText(result.message());
            orgChartDisplay.add(new Paragraph(result.message()));
            UiMessages.error(result.message());
            return;
        }

        personnelStatus.setText(result.message());
        if (result.roots().isEmpty()) {
            orgChartDisplay.add(new Paragraph("No personnel found."));
        } else {
            for (OrgNodeDTO root : result.roots()) {
                orgChartDisplay.add(orgNode(root));
            }
        }
        UiMessages.success(result.message());
    }

    private void createEvent() {
        EventActionResult result = presenter.createEvent(
                eventCompanyName.getValue(),
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
        if (result.success() && result.eventId() != null) {
            eventId.setValue(result.eventId().toString());
            inventoryEventId.setValue(result.eventId().toString());
        }
    }

    private void editEvent() {
        EventActionResult result = presenter.editEvent(
                parseUuid(eventId, "event"),
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
        EventMapResult result = presenter.loadEventMap(parseUuid(inventoryEventId, "event"));
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
        PurchaseHistoryResult result = presenter.loadPurchaseHistory(reportingCompanyName.getValue());
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
        SalesReportResult result = presenter.loadSalesReport(reportingCompanyName.getValue());
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

    private Instant instant(DateTimePicker picker) {
        LocalDateTime value = picker.getValue();
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
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
}
