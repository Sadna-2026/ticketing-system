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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.DateRangeCondition;
import com.ticketing.domain.event.DiscountPolicyText;
import com.ticketing.domain.event.IDiscountCondition;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.MaxCompositeDiscount;
import com.ticketing.domain.event.MaxQuantityCondition;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityCondition;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.NoOrphanSeatPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.SimpleDiscount;
import com.ticketing.domain.event.SumCompositeDiscount;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.CompanyAccessResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.CompanyInfoResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.EventMapResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.OrgChartResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PolicyViewResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.SalesReportResult;
import com.ticketing.presentation.vaadin.util.DestructiveActionDialogs;
import com.ticketing.presentation.vaadin.util.ErrorBanner;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "company", layout = MainLayout.class)
@PageTitle("Company")
@SpringComponent
@UIScope
public class CompanyView extends VerticalLayout {

    private enum CompanyMode {
        LOOKUP("Company lookup", false),
        FOUNDER("Founder", true),
        PERSONNEL("Personnel", true),
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
    private static final String PURCHASE_RULE_AGE = "Age restriction";
    private static final String PURCHASE_RULE_MAX = "Max quantity";
    private static final String PURCHASE_RULE_MIN = "Min quantity";
    private static final String PURCHASE_RULE_NO_ORPHAN = "No orphan seat";

    private final CompanyPresenter presenter;

    // #517: persistent red banner shown when a DB-connection failure prevents the tab
    // from loading. Stays mounted; cleared automatically when the periodic recovery
    // probe finds the backend reachable again.
    private static final int RECOVERY_POLL_MILLIS = 5_000;
    private final ErrorBanner dbErrorBanner = new ErrorBanner();
    private Registration recoveryPollListener;

    private final Span sessionStatus = new Span();
    private final Paragraph memberOnlyCompanyHint = new Paragraph("Log in as a member to use company owner and manager actions.");
    private final ComboBox<CompanySummaryDTO> selectedCompanyName = new ComboBox<>("Selected company");
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
    private final ComboBox<com.ticketing.application.dto.MemberSummaryDTO> offerTargetMember = new ComboBox<>("Member to appoint");
    private final ComboBox<PersonnelTarget> targetMember = new ComboBox<>("Existing personnel");
    private final ComboBox<StaffAppointment.StaffRole> role = new ComboBox<>("Role");
    private final CheckboxGroup<ManagerPermission> permissions = new CheckboxGroup<>("Manager permissions");
    private final Span personnelStatus = new Span("Manage owner and manager appointments.");
    private final Span personnelAccessHint = new Span("Select a company to show owner-only permission controls.");
    private final VerticalLayout orgChartDisplay = new VerticalLayout();
    private VerticalLayout ownerPersonnelControls;
    private FormLayout ownerPersonnelForm;
    private HorizontalLayout ownerPersonnelActions;
    private Button offerRoleButton;
    private Button loadOrganizationChartButton;
    private Button revokePersonnelButton;
    private Button changeManagerPermissionsButton;
    private Button relinquishOwnershipButton;

    private final ComboBox<CompanySummaryDTO> eventCompanyName = new ComboBox<>("Event company name");

    private final Span eventStatus = new Span();
    private Button editEventButton;
    private Button designHallButton;
    private Button editVenueLayoutButton;
    private FormLayout eventSelectionForm;
    private HorizontalLayout eventActions;
    private VerticalLayout eventControls;

    private final ComboBox<CompanySummaryDTO> inventoryCompanyName = new ComboBox<>("Inventory company name");
    private final ComboBox<EventSummaryDTO> inventoryEventId = new ComboBox<>("Inventory event");
    private final ComboBox<EventMapDTO.ZoneInfo> inventoryZonePicker = new ComboBox<>("Inventory zone");
    private final TextField seatRow = new TextField("Seat row");
    private final TextField seatNumber = new TextField("Seat number");
    private final ComboBox<EventMapDTO.SeatInfo> seatPicker = new ComboBox<>("Seat");
    private final IntegerField capacityDelta = new IntegerField("Capacity delta");
    private final BigDecimalField zonePriceUpdate = new BigDecimalField("Zone price update");
    private final Span inventoryStatus = new Span("Load or manage supported inventory actions.");
    private final VerticalLayout eventMapDisplay = new VerticalLayout();
    private Button addSeatButton;
    private Button removeSeatButton;
    private Button increaseCapacityButton;
    private Button decreaseCapacityButton;
    private Button setZonePriceButton;
    private FormLayout inventoryForm;

    private final ComboBox<CompanySummaryDTO> lifecycleCompanyName = new ComboBox<>("Lifecycle company name");
    private final Span lifecycleStatus = new Span("Suspend or reopen companies.");
    private final Span lifecycleAccessHint = new Span("Select a company to show founder-only lifecycle controls.");
    private Button suspendCompanyButton;
    private Button reopenCompanyButton;
    private HorizontalLayout lifecycleActions;

    private final ComboBox<CompanySummaryDTO> reportingCompanyName = new ComboBox<>("Reporting company name");
    private final Span reportingStatus = new Span("Load company purchase history and hierarchical sales reports.");
    private final Grid<PurchaseRecordDTO> purchasesGrid = new Grid<>(PurchaseRecordDTO.class, false);
    private final VerticalLayout salesReportDisplay = new VerticalLayout();
    private Button loadHistoryButton;
    private Button loadSalesReportButton;
    private VerticalLayout reportingControls;

    private final ComboBox<CompanySummaryDTO> policyCompanyName = new ComboBox<>("Policy company name");
    private final ComboBox<EventSummaryDTO> policyEventId = new ComboBox<>("Policy event");
    private final CheckboxGroup<String> purchaseRuleParts = new CheckboxGroup<>("Included purchase rules");
    private final IntegerField policyAge = new IntegerField("Min age");
    private final IntegerField policyMaxTickets = new IntegerField("Max tickets");
    private final IntegerField policyMinTickets = new IntegerField("Min tickets");
    private final ComboBox<String> policyComposition = new ComboBox<>("Primary group match");
    private final CheckboxGroup<String> secondaryPurchaseRuleParts = new CheckboxGroup<>("Optional second purchase-rule group");
    private final IntegerField secondaryPolicyAge = new IntegerField("Second group min age");
    private final IntegerField secondaryPolicyMaxTickets = new IntegerField("Second group max tickets");
    private final IntegerField secondaryPolicyMinTickets = new IntegerField("Second group min tickets");
    private final ComboBox<String> secondaryPolicyComposition = new ComboBox<>("Second group match");
    private final ComboBox<String> policyGroupComposition = new ComboBox<>("Between groups");
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
    private final List<IDiscountPolicy> discountDrafts = new ArrayList<>();
    private final VerticalLayout discountDraftList = new VerticalLayout();
    private final Span policyStatus = new Span("View and manage purchase and discount policies.");
    private final Span currentPolicyDisplay = new Span();
    private Button setPurchasePolicyButton;
    private Button removePurchasePolicyButton;
    private Button addDiscountDraftButton;
    private Button clearDiscountDraftsButton;
    private Button setDiscountPolicyButton;
    private Button removeDiscountPolicyButton;
    private VerticalLayout policyControls;
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
        modeTabs.getElement().setAttribute("aria-label", "Company sections");
        selectedCompanyName.setWidth("min(100%, 28rem)");

        add(
                new H2("Company"),
                dbErrorBanner,
                new Paragraph("Choose a section below. Each mode shows only the controls for that area."),
                new Paragraph("Application services still enforce authorization for every action and their responses are shown in the status area."),
                sessionStatus,
                memberOnlyCompanyHint,
                selectedCompanyName,
                modeTabs,
                modeContent
        );
        selectMode(CompanyMode.LOOKUP);
        // #517: every attempt to refresh the session/pickers may hit the DB. Route the
        // initial call AND every subsequent re-attach through the safe wrapper so a DB
        // outage degrades to a banner instead of bubbling to Vaadin's error page.
        safeRefreshSessionStatus();
        addAttachListener(event -> safeRefreshSessionStatus());
        addDetachListener(event -> stopRecoveryPolling());
    }

    /**
     * #517: wraps {@link #refreshSessionStatus()} so a DB-connection failure shows the
     * persistent red banner and starts a recovery poll instead of letting the exception
     * propagate to Vaadin's error page. On success — including a probe-triggered
     * recovery — the banner is hidden and polling stops.
     */
    private void safeRefreshSessionStatus() {
        try {
            refreshSessionStatus();
            if (dbErrorBanner.isShown()) {
                dbErrorBanner.hide();
            }
            stopRecoveryPolling();
        } catch (RuntimeException ex) {
            PresenterErrorClassifier.Category category = PresenterErrorClassifier.classify(ex);
            if (category == PresenterErrorClassifier.Category.DB_UNAVAILABLE) {
                dbErrorBanner.showError(PresenterErrorClassifier.userFacingMessage(category));
                startRecoveryPolling();
                return;
            }
            throw ex; // not an infrastructure outage — let the global handler see it
        }
    }

    private void startRecoveryPolling() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (ui == null || recoveryPollListener != null) {
            return;
        }
        ui.setPollInterval(RECOVERY_POLL_MILLIS);
        recoveryPollListener = ui.addPollListener(event -> {
            if (presenter.isBackendReachable()) {
                safeRefreshSessionStatus();
            }
        });
    }

    private void stopRecoveryPolling() {
        com.vaadin.flow.component.UI ui = com.vaadin.flow.component.UI.getCurrent();
        if (recoveryPollListener != null) {
            recoveryPollListener.remove();
            recoveryPollListener = null;
        }
        if (ui != null) {
            ui.setPollInterval(-1);
        }
    }

    private void initModePanels() {
        VerticalLayout lookupPanel = new VerticalLayout(publicCompanySection(), publicEventMapSection());
        lookupPanel.setPadding(false);
        lookupPanel.setSpacing(true);

        VerticalLayout founderPanel = new VerticalLayout(openCompanySection(), lifecycleSection());
        founderPanel.setPadding(false);
        founderPanel.setSpacing(true);

        Map<CompanyMode, VerticalLayout> panels = Map.of(
                CompanyMode.LOOKUP, lookupPanel,
                CompanyMode.FOUNDER, founderPanel,
                CompanyMode.PERSONNEL, personnelSection(),
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
                if (tab.equals(tabByMode.get(CompanyMode.POLICIES))) {
                    ensurePolicyContextSynced();
                    refreshPolicyAccess();
                    autoLoadPolicies();
                }
            }
        });
    }

    private void attachModePanels() {
        modeContent.removeAll();
        for (CompanyMode mode : CompanyMode.values()) {
            VerticalLayout panel = panelByTab.get(tabByMode.get(mode));
            if (panel != null) {
                panel.setVisible(false);
                panel.setWidthFull();
                panel.addClassName("app-card");
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
        offerTargetMember.setItemLabelGenerator(com.ticketing.application.dto.MemberSummaryDTO::username);
        targetMember.setItemLabelGenerator(PersonnelTarget::label);
        targetMember.setPlaceholder("Select personnel after choosing a company");

        capacityDelta.setMin(1);
        capacityDelta.setValue(1);

        markRequiredFields();
    }

    private void markRequiredFields() {
        markRequired(openCompanyName, "Company name is required.");

        markRequired(personnelCompanyName, "Select a company.");

        markRequired(eventCompanyName, "Select a company.");
    }

    private void configurePickers() {
        for (ComboBox<CompanySummaryDTO> picker : List.of(
                selectedCompanyName, infoCompanyName, personnelCompanyName, eventCompanyName,
                inventoryCompanyName, lifecycleCompanyName, reportingCompanyName, policyCompanyName)) {
            picker.setItemLabelGenerator(CompanySummaryDTO::name);
            picker.setPlaceholder("Search by company name");
        }
        selectedCompanyName.setHelperText("Used by all company sections except founder setup.");
        for (ComboBox<CompanySummaryDTO> picker : sectionCompanyPickers()) {
            picker.setVisible(false);
        }

        inventoryEventId.setItemLabelGenerator(EventSummaryDTO::name);
        inventoryEventId.setPlaceholder("Select a company first");
        lookupEventPicker.setItemLabelGenerator(EventSummaryDTO::name);
        lookupEventPicker.setPlaceholder("Search published events");

        policyEventId.setItemLabelGenerator(EventSummaryDTO::name);
        policyEventId.setPlaceholder("Optional — leave empty for company-level");
        policyEventId.setClearButtonVisible(true);
        policyEventId.addValueChangeListener(e -> autoLoadPolicies());

        selectedCompanyName.addValueChangeListener(e -> {
            applySelectedCompany(e.getValue());
            if (isPoliciesTabActive()) {
                refreshPolicyAccess();
                autoLoadPolicies();
            }
        });

        inventoryZonePicker.setItemLabelGenerator(zone -> zone.name() + " — " + zone.type());
        inventoryZonePicker.setPlaceholder("Select an event to list zones");
        seatPicker.setItemLabelGenerator(seat -> "Row " + seat.row() + " · Seat " + seat.seatNumber());
        seatPicker.setPlaceholder("Select a zone to list seats");

        personnelCompanyName.addValueChangeListener(e -> refreshPersonnelContext());
        eventCompanyName.addValueChangeListener(e -> refreshEventAccess());
        inventoryCompanyName.addValueChangeListener(e -> {
            reloadCompanyEvents(inventoryEventId, e.getValue());
            refreshInventoryAccess();
        });
        lifecycleCompanyName.addValueChangeListener(e -> refreshLifecycleAccess());
        reportingCompanyName.addValueChangeListener(e -> refreshReportingAccess());
        policyCompanyName.addValueChangeListener(e -> {
            reloadCompanyEvents(policyEventId, e.getValue());
            refreshPolicyAccess();
            autoLoadPolicies();
        });
        inventoryEventId.addValueChangeListener(e -> loadInventoryZones(e.getValue()));
        inventoryZonePicker.addValueChangeListener(e -> {
            EventMapDTO.ZoneInfo zone = e.getValue();
            seatPicker.clear();
            seatPicker.setItems(zone == null ? List.of() : zone.seats());
            refreshInventoryActionState();
        });
        seatPicker.addValueChangeListener(e -> refreshInventoryActionState());
    }

    private void loadInventoryZones(EventSummaryDTO event) {
        inventoryZonePicker.clear();
        seatPicker.clear();
        seatPicker.setItems(List.of());
        if (event == null) {
            inventoryZonePicker.setItems(List.of());
            refreshInventoryActionState();
            return;
        }
        EventMapResult result = presenter.loadEventMapForManagement(event.id());
        if (!result.success()) {
            inventoryZonePicker.setItems(List.of());
            inventoryStatus.setText(result.message());
            UiMessages.error(result.message());
            refreshInventoryActionState();
            return;
        }
        inventoryZonePicker.setItems(result.eventMap().zones());
        refreshInventoryActionState();
    }

    private void refreshInventoryActionState() {
        boolean hasZone = inventoryZonePicker.getValue() != null;
        boolean hasSeat = seatPicker.getValue() != null;
        if (addSeatButton != null) {
            addSeatButton.setEnabled(hasZone);
        }
        if (increaseCapacityButton != null) {
            increaseCapacityButton.setEnabled(hasZone);
        }
        if (decreaseCapacityButton != null) {
            decreaseCapacityButton.setEnabled(hasZone);
        }
        if (setZonePriceButton != null) {
            setZonePriceButton.setEnabled(hasZone);
        }
        if (removeSeatButton != null) {
            removeSeatButton.setEnabled(hasSeat);
        }
    }

    private UUID selectedZoneId() {
        EventMapDTO.ZoneInfo zone = inventoryZonePicker.getValue();
        return zone == null ? null : zone.id();
    }

    private UUID selectedSeatId() {
        EventMapDTO.SeatInfo seat = seatPicker.getValue();
        return seat == null ? null : seat.id();
    }

    private String selectedSeatLabel() {
        EventMapDTO.SeatInfo seat = seatPicker.getValue();
        if (seat == null) {
            return null;
        }
        return "row " + seat.row() + ", seat " + seat.seatNumber();
    }

    private String selectedZoneLabel() {
        EventMapDTO.ZoneInfo zone = inventoryZonePicker.getValue();
        return zone == null ? null : zone.name();
    }

    private String policyTargetLabel() {
        EventSummaryDTO event = policyEventId.getValue();
        if (event != null) {
            return event.name();
        }
        CompanySummaryDTO company = policyCompanyName.getValue();
        return company == null ? null : company.name();
    }

    private void reloadCompanyEvents(ComboBox<EventSummaryDTO> picker, CompanySummaryDTO company) {
        picker.clear();
        List<EventSummaryDTO> events = company == null ? List.of() : orEmpty(presenter.listCompanyEvents(company.name()));
        picker.setItems(events);
        if (picker != policyEventId) {
            picker.setPlaceholder(company == null
                    ? "Select a company first"
                    : events.isEmpty() ? "No events for this company" : "Select an event");
        }
    }

    private void populatePickerItems() {
        List<CompanySummaryDTO> lookupCompanies = orEmpty(presenter.searchLookupCompanies(""));
        List<CompanySummaryDTO> activeCompanies = orEmpty(presenter.searchCompanies(""));
        List<CompanySummaryDTO> lifecycleCompanies = orEmpty(presenter.searchLifecycleCompanies(""));
        List<CompanySummaryDTO> selectableCompanies = mergeCompanies(lookupCompanies, activeCompanies, lifecycleCompanies);

        setCompanyItemsPreservingSelection(selectedCompanyName, selectableCompanies);
        for (ComboBox<CompanySummaryDTO> picker : sectionCompanyPickers()) {
            setCompanyItemsPreservingSelection(picker, selectableCompanies);
        }
        lookupEventPicker.setItems(orEmpty(presenter.searchBrowsableEvents()));
    }

    @SafeVarargs
    private static List<CompanySummaryDTO> mergeCompanies(List<CompanySummaryDTO>... groups) {
        Map<String, CompanySummaryDTO> byName = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (List<CompanySummaryDTO> group : groups) {
            for (CompanySummaryDTO company : group) {
                byName.putIfAbsent(company.name(), company);
            }
        }
        return List.copyOf(byName.values());
    }

    private static void setCompanyItemsPreservingSelection(
            ComboBox<CompanySummaryDTO> picker,
            List<CompanySummaryDTO> companies
    ) {
        CompanySummaryDTO selected = picker.getValue();
        picker.setItems(companies);
        if (selected != null) {
            companies.stream()
                    .filter(company -> company.name().equals(selected.name()))
                    .findFirst()
                    .ifPresentOrElse(picker::setValue, picker::clear);
        }
    }

    private void applySelectedCompany(CompanySummaryDTO company) {
        for (ComboBox<CompanySummaryDTO> picker : sectionCompanyPickers()) {
            picker.setValue(company);
        }
        if (company == null) {
            companyInfoStatus.setText("Select a company to view public details.");
            companyEventsGrid.setItems(List.of());
            reloadCompanyEvents(inventoryEventId, null);
            reloadCompanyEvents(policyEventId, null);
        } else {
            reloadCompanyEvents(inventoryEventId, company);
            reloadCompanyEvents(policyEventId, company);
        }
    }

    private List<ComboBox<CompanySummaryDTO>> sectionCompanyPickers() {
        return List.of(infoCompanyName, personnelCompanyName, eventCompanyName,
                inventoryCompanyName, lifecycleCompanyName, reportingCompanyName, policyCompanyName);
    }

    private static <T> List<T> orEmpty(List<T> items) {
        return items == null ? List.of() : items;
    }

    private void configureCompanyEventsGrid() {
        companyEventsGrid.setId("company-events-grid");
        companyEventsGrid.setEmptyStateText("No events yet — create one to start selling tickets.");
        companyEventsGrid.addColumn(EventSummaryDTO::name).setHeader("Event").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> event.category().name()).setHeader("Category").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> formatInstant(event.schedule().getStartTime())).setHeader("Starts").setAutoWidth(true);
        companyEventsGrid.addColumn(event -> event.status().name()).setHeader("Status").setAutoWidth(true);
        companyEventsGrid.setMinHeight("180px");
    }

    private void configurePurchasesGrid() {
        purchasesGrid.setId("company-purchases-grid");
        purchasesGrid.setEmptyStateText("No purchases recorded for this company yet.");
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
        openCompany.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

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
        offerRoleButton = new Button("Offer role appointment", event -> {
            if (offerTargetMember.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show("Please select a member to appoint.");
                return;
            }
            if (role.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show("Please select a role.");
                return;
            }
            handlePersonnelResult(presenter.offerRoleAppointment(
                companyNameOf(personnelCompanyName),
                selectedOfferTargetMemberId(),
                role.getValue(),
                permissions.getSelectedItems()
            ));
        });
        revokePersonnelButton = new Button("Revoke personnel", event -> {
            if (targetMember.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show("Please select an existing personnel to revoke.");
                return;
            }
            PersonnelTarget target = targetMember.getValue();
            DestructiveActionDialogs.confirmRevokePersonnel(target.username(), () -> {
                CompanyPresenter.ActionResult result = presenter.revokePersonnel(
                        companyNameOf(personnelCompanyName), target.memberId());
                handlePersonnelResult(result);
            });
        });
        changeManagerPermissionsButton = new Button("Change manager permissions", event -> {
            if (targetMember.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show("Please select an existing personnel to modify.");
                return;
            }
            handlePersonnelResult(presenter.changeManagerPermissions(companyNameOf(personnelCompanyName), selectedTargetMemberId(),
                        permissions.getSelectedItems()));
        });
        relinquishOwnershipButton = new Button("Relinquish ownership", event -> {
            String companyName = companyNameOf(personnelCompanyName);
            DestructiveActionDialogs.confirmRelinquishOwnership(companyName, () ->
                    handlePersonnelResult(presenter.relinquishOwnership(companyName)));
        });
        loadOrganizationChartButton = new Button("Load organization chart", event -> loadOrganizationChart());

        ownerPersonnelForm = new FormLayout(offerTargetMember, targetMember, role, permissions);
        ownerPersonnelForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        ownerPersonnelActions = new HorizontalLayout(offerRoleButton, revokePersonnelButton,
                changeManagerPermissionsButton, relinquishOwnershipButton, loadOrganizationChartButton);
        ownerPersonnelActions.setAlignItems(Alignment.BASELINE);
        ownerPersonnelActions.getStyle().set("flex-wrap", "wrap");
        ownerPersonnelControls = new VerticalLayout(
                new H4("Role appointment and personnel"),
                ownerPersonnelForm,
                ownerPersonnelActions
        );
        ownerPersonnelControls.setPadding(false);
        ownerPersonnelControls.setSpacing(true);

        personnelCompanyName.setVisible(false);
        refreshPersonnelAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Personnel and roles"),
                new Paragraph("Owners appoint managers and other owners."),
                personnelCompanyName,
                ownerPersonnelControls,
                personnelAccessHint,
                personnelStatus,
                orgChartDisplay
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout eventManagementSection() {
        editEventButton = new Button("Edit event", event -> openEditEventDialog());
        designHallButton = new Button("Create event", event -> openVenueDesigner());
        editVenueLayoutButton = new Button("Edit venue layout", event -> openEditVenueLayout());
        designHallButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        eventSelectionForm = new FormLayout(eventCompanyName);
        eventSelectionForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));

        eventActions = new HorizontalLayout(designHallButton, editEventButton, editVenueLayoutButton);
        eventActions.setAlignItems(Alignment.BASELINE);
        eventControls = new VerticalLayout(eventSelectionForm, eventActions);
        eventControls.setPadding(false);
        refreshEventAccess();

        VerticalLayout section = new VerticalLayout(eventControls, eventStatus);
        section.setPadding(false);
        return section;
    }

    private VerticalLayout inventorySection() {
        addSeatButton = new Button("Add seat", event -> handleInventoryResult(presenter.addSeat(
                selectedEventId(inventoryEventId),
                selectedZoneId(),
                seatRow.getValue(),
                seatNumber.getValue()
        )));
        removeSeatButton = new Button("Remove seat", event ->
                DestructiveActionDialogs.confirmRemoveSeat(selectedSeatLabel(), () -> handleInventoryResult(presenter.removeSeat(
                        selectedEventId(inventoryEventId),
                        selectedZoneId(),
                        selectedSeatId()
                ))));
        increaseCapacityButton = new Button("Increase GA capacity", event -> handleInventoryResult(presenter.increaseGACapacity(
                selectedEventId(inventoryEventId),
                selectedZoneId(),
                capacityDelta.getValue()
        )));
        decreaseCapacityButton = new Button("Decrease GA capacity", event ->
                DestructiveActionDialogs.confirmDecreaseGaCapacity(selectedZoneLabel(), () ->
                        handleInventoryResult(presenter.decreaseGACapacity(
                                selectedEventId(inventoryEventId),
                                selectedZoneId(),
                                capacityDelta.getValue()
                        ))));
        setZonePriceButton = new Button("Set zone price", event -> handleInventoryResult(presenter.setZonePrice(
                selectedEventId(inventoryEventId),
                selectedZoneId(),
                zonePriceUpdate.getValue()
        )));
        refreshInventoryActionState();

        inventoryForm = new FormLayout(
                inventoryCompanyName,
                inventoryEventId,
                inventoryZonePicker,
                seatRow,
                seatNumber,
                seatPicker,
                capacityDelta,
                zonePriceUpdate
        );
        inventoryForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));
        HorizontalLayout actions = new HorizontalLayout(addSeatButton, removeSeatButton, increaseCapacityButton, decreaseCapacityButton, setZonePriceButton);
        actions.setAlignItems(Alignment.BASELINE);
        actions.getStyle().set("flex-wrap", "wrap");
        inventoryManagementControls = new VerticalLayout(actions);
        inventoryManagementControls.setPadding(false);
        refreshInventoryAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Inventory management"),
                new Paragraph("Adjust seats, GA capacity, and zone pricing for an event."),
                inventoryForm,
                inventoryManagementControls,
                inventoryStatus
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout lifecycleSection() {
        suspendCompanyButton = new Button("Suspend company", event -> {
            String companyName = companyNameOf(lifecycleCompanyName);
            DestructiveActionDialogs.confirmSuspendCompany(companyName, () ->
                    handleLifecycleResult(presenter.suspendCompany(companyName)));
        });
        reopenCompanyButton = new Button("Reopen company", event -> handleLifecycleResult(presenter.reopenCompany(companyNameOf(lifecycleCompanyName))));
        lifecycleActions = new HorizontalLayout(suspendCompanyButton, reopenCompanyButton);
        lifecycleActions.setAlignItems(Alignment.BASELINE);
        refreshLifecycleAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Company lifecycle"),
                new Paragraph("Founders may suspend or reopen their company."),
                lifecycleCompanyName,
                lifecycleAccessHint,
                lifecycleActions,
                lifecycleStatus
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout reportingSection() {
        loadHistoryButton = new Button("Load company purchase history", event -> loadPurchaseHistory());
        loadSalesReportButton = new Button("Load sales report", event -> loadSalesReport());
        HorizontalLayout actions = new HorizontalLayout(loadHistoryButton, loadSalesReportButton);
        actions.setAlignItems(Alignment.BASELINE);
        reportingControls = new VerticalLayout(actions, purchasesGrid, salesReportDisplay);
        reportingControls.setPadding(false);
        refreshReportingAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("History and reporting"),
                new Paragraph("View purchase history and sales totals for a company."),
                reportingCompanyName,
                reportingStatus,
                reportingControls
        );
        section.setPadding(false);
        return section;
    }

    private VerticalLayout policySection() {
        purchaseRuleParts.setItems(PURCHASE_RULE_AGE, PURCHASE_RULE_MAX, PURCHASE_RULE_MIN, PURCHASE_RULE_NO_ORPHAN);
        purchaseRuleParts.setHelperText("Leave empty to allow all purchases.");
        policyAge.setMin(1);
        policyAge.setValue(18);
        policyMaxTickets.setMin(1);
        policyMaxTickets.setValue(5);
        policyMinTickets.setMin(1);
        policyMinTickets.setValue(2);
        policyComposition.setItems("AND (all must pass)", "OR (any can pass)");
        policyComposition.setValue("AND (all must pass)");
        secondaryPurchaseRuleParts.setItems(PURCHASE_RULE_AGE, PURCHASE_RULE_MAX, PURCHASE_RULE_MIN, PURCHASE_RULE_NO_ORPHAN);
        secondaryPurchaseRuleParts.setHelperText("Use for nested policies, for example: Age 18 OR (Age 40 AND min tickets 10).");
        secondaryPolicyAge.setMin(1);
        secondaryPolicyAge.setValue(40);
        secondaryPolicyMaxTickets.setMin(1);
        secondaryPolicyMaxTickets.setValue(5);
        secondaryPolicyMinTickets.setMin(1);
        secondaryPolicyMinTickets.setValue(10);
        secondaryPolicyComposition.setItems("AND (all must pass)", "OR (any can pass)");
        secondaryPolicyComposition.setValue("AND (all must pass)");
        policyGroupComposition.setItems("OR between groups", "AND between groups");
        policyGroupComposition.setValue("OR between groups");
        purchaseRuleParts.addValueChangeListener(e -> updatePurchaseRuleVisibility());
        secondaryPurchaseRuleParts.addValueChangeListener(e -> updatePurchaseRuleVisibility());

        discountType.setItems("No discount", "Simple (flat %)", "Conditional (% with condition)", "Coupon (% with code)");
        discountType.setValue("No discount");
        discountType.addValueChangeListener(e -> updateDiscountFieldVisibility());
        discountPercent.setValue(BigDecimal.TEN);
        couponCodeField.setPlaceholder("e.g. EARLY20");
        discountConditionType.setItems("Min tickets", "Max tickets", "Date range");
        discountConditionType.setValue("Min tickets");
        discountConditionType.addValueChangeListener(e -> updateDiscountFieldVisibility());
        conditionMinTickets.setMin(1);
        conditionMinTickets.setValue(2);
        conditionMaxTickets.setMin(1);
        conditionMaxTickets.setValue(5);
        discountComposition.setItems("Single discount", "MAX (best discount wins)", "SUM (stack all)");
        discountComposition.setValue("Single discount");

        setPurchasePolicyButton = new Button("Set purchase policy", e -> setPurchasePolicy());
        removePurchasePolicyButton = new Button("Remove purchase policy", e -> removePurchasePolicy());
        addDiscountDraftButton = new Button("+ Add discount", e -> addDiscountDraft());
        clearDiscountDraftsButton = new Button("Clear discounts", e -> clearDiscountDrafts());
        setDiscountPolicyButton = new Button("Set discount policy", e -> setDiscountPolicy());
        removeDiscountPolicyButton = new Button("Remove discount policy", e -> removeDiscountPolicy());

        FormLayout targetForm = new FormLayout(policyCompanyName, policyEventId);
        targetForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));

        FormLayout primaryPurchaseForm = new FormLayout(
                purchaseRuleParts, policyAge, policyMaxTickets, policyMinTickets, policyComposition);
        primaryPurchaseForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        FormLayout secondaryPurchaseForm = new FormLayout(
                secondaryPurchaseRuleParts, secondaryPolicyAge, secondaryPolicyMaxTickets,
                secondaryPolicyMinTickets, secondaryPolicyComposition, policyGroupComposition);
        secondaryPurchaseForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        VerticalLayout purchaseForm = new VerticalLayout(primaryPurchaseForm, secondaryPurchaseForm);
        purchaseForm.setPadding(false);
        purchaseForm.setSpacing(true);
        updatePurchaseRuleVisibility();

        HorizontalLayout purchaseActions = new HorizontalLayout(setPurchasePolicyButton, removePurchasePolicyButton);
        purchaseActions.setAlignItems(Alignment.BASELINE);

        FormLayout discountForm = new FormLayout(discountType, discountPercent, couponCodeField, couponExpiry,
                discountConditionType, conditionMinTickets, conditionMaxTickets, conditionFrom, conditionTo);
        discountForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));

        discountDraftList.setPadding(false);
        discountDraftList.setSpacing(true);
        discountDraftList.setWidthFull();
        HorizontalLayout discountDraftActions = new HorizontalLayout(addDiscountDraftButton, clearDiscountDraftsButton);
        discountDraftActions.setAlignItems(Alignment.BASELINE);
        HorizontalLayout discountActions = new HorizontalLayout(setDiscountPolicyButton, removeDiscountPolicyButton);
        discountActions.setAlignItems(Alignment.BASELINE);
        updateDiscountFieldVisibility();
        renderDiscountDrafts();
        policyControls = new VerticalLayout(
                targetForm,
                new H4("Purchase policy"),
                purchaseForm,
                purchaseActions,
                new H4("Discount policy"),
                discountForm,
                discountDraftActions,
                discountDraftList,
                discountComposition,
                discountActions,
                currentPolicyDisplay
        );
        policyControls.setPadding(false);
        refreshPolicyAccess();

        VerticalLayout section = new VerticalLayout(
                new H3("Purchase and discount policies"),
                new Paragraph("Define and manage purchase rules and discount policies at company or event level."),
                policyStatus,
                policyControls
        );
        section.setPadding(false);
        return section;
    }

    private void autoLoadPolicies() {
        if (!isPoliciesTabActive() || policyControls == null) {
            return;
        }
        String companyName = resolvePolicyCompanyName();
        if (companyName == null) {
            resetPolicyFormToDefaults();
            policyStatus.setText("Select a company to load policies.");
            currentPolicyDisplay.setText("");
            return;
        }
        CompanyAccessResult access = presenter.loadCompanyAccess(companyName);
        if (!access.canManagePolicies()) {
            resetPolicyFormToDefaults();
            return;
        }
        UUID eventId = selectedEventId(policyEventId);
        PolicyViewResult purchase = eventId != null
                ? presenter.loadEventPurchasePolicy(eventId, companyName)
                : presenter.loadCompanyPurchasePolicy(companyName);
        PolicyViewResult discount = eventId != null
                ? presenter.loadEventDiscountPolicy(eventId, companyName)
                : presenter.loadCompanyDiscountPolicy(companyName);

        if (purchase == null || discount == null) {
            return;
        }

        if (purchase.success() && purchase.purchasePolicy() != null) {
            applyPurchasePolicyToForm(purchase.purchasePolicy());
        }
        if (discount.success() && discount.discountPolicy() != null) {
            applyDiscountPolicyToForm(discount.discountPolicy());
        }

        if (!purchase.success()) {
            policyStatus.setText(purchase.message());
            currentPolicyDisplay.setText("");
            UiMessages.error(purchase.message());
            return;
        }
        if (!discount.success()) {
            policyStatus.setText(discount.message());
            currentPolicyDisplay.setText(formatPurchasePolicyLine(purchase, eventId != null));
            UiMessages.error(discount.message());
            return;
        }

        policyStatus.setText(eventId != null ? "Event policies loaded." : "Company policies loaded.");
        currentPolicyDisplay.setText(formatPurchasePolicyLine(purchase, eventId != null)
                + " | " + formatDiscountPolicyLine(discount, eventId != null));
    }

    private void resetPolicyFormToDefaults() {
        purchaseRuleParts.clear();
        policyComposition.setValue("AND (all must pass)");
        clearPurchaseThresholdFields();
        clearSecondaryPurchaseGroup();
        discountType.setValue("No discount");
        discountComposition.setValue("Single discount");
        discountDrafts.clear();
        renderDiscountDrafts();
        clearDiscountFormAuxiliaryFields();
    }

    private void clearPurchaseThresholdFields() {
        policyAge.clear();
        policyMaxTickets.clear();
        policyMinTickets.clear();
        updatePurchaseRuleVisibility();
    }

    private void clearSecondaryPurchaseGroup() {
        secondaryPurchaseRuleParts.clear();
        secondaryPolicyComposition.setValue("AND (all must pass)");
        policyGroupComposition.setValue("OR between groups");
        secondaryPolicyAge.clear();
        secondaryPolicyMaxTickets.clear();
        secondaryPolicyMinTickets.clear();
        updatePurchaseRuleVisibility();
    }

    private void updatePurchaseRuleVisibility() {
        updatePurchaseGroupVisibility(purchaseRuleParts.getValue(), policyAge, policyMaxTickets, policyMinTickets, policyComposition);
        updatePurchaseGroupVisibility(secondaryPurchaseRuleParts.getValue(), secondaryPolicyAge,
                secondaryPolicyMaxTickets, secondaryPolicyMinTickets, secondaryPolicyComposition);
        policyGroupComposition.setVisible(hasSelectedPurchaseRules(purchaseRuleParts.getValue())
                && hasSelectedPurchaseRules(secondaryPurchaseRuleParts.getValue()));
    }

    private static void updatePurchaseGroupVisibility(
            Set<String> selectedRules,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField,
            ComboBox<String> compositionField
    ) {
        Set<String> safeRules = selectedRules == null ? Set.of() : selectedRules;
        ageField.setVisible(safeRules.contains(PURCHASE_RULE_AGE));
        maxTicketsField.setVisible(safeRules.contains(PURCHASE_RULE_MAX));
        minTicketsField.setVisible(safeRules.contains(PURCHASE_RULE_MIN));
        compositionField.setVisible(safeRules.size() >= 2);
    }

    private static boolean hasSelectedPurchaseRules(Set<String> selectedRules) {
        return selectedRules != null && !selectedRules.isEmpty();
    }

    private void clearDiscountFormAuxiliaryFields() {
        discountPercent.clear();
        couponCodeField.clear();
        couponExpiry.clear();
        discountConditionType.setValue("Min tickets");
        conditionMinTickets.clear();
        conditionMaxTickets.clear();
        conditionFrom.clear();
        conditionTo.clear();
        updateDiscountFieldVisibility();
    }

    private void updateDiscountFieldVisibility() {
        String type = discountType.getValue();
        boolean noDiscount = type == null || "No discount".equals(type);
        boolean coupon = "Coupon (% with code)".equals(type);
        boolean conditional = "Conditional (% with condition)".equals(type);
        boolean percentBased = !noDiscount;

        discountPercent.setVisible(percentBased);
        couponCodeField.setVisible(coupon);
        couponExpiry.setVisible(coupon);
        discountConditionType.setVisible(conditional);

        String conditionType = discountConditionType.getValue();
        conditionMinTickets.setVisible(conditional && "Min tickets".equals(conditionType));
        conditionMaxTickets.setVisible(conditional && "Max tickets".equals(conditionType));
        conditionFrom.setVisible(conditional && "Date range".equals(conditionType));
        conditionTo.setVisible(conditional && "Date range".equals(conditionType));
    }

    private static String formatPurchasePolicyLine(PolicyViewResult purchase, boolean eventScope) {
        String line = "Current purchase: " + purchase.description();
        if (!eventScope) {
            return line;
        }
        return purchase.inheritedFromCompany()
                ? line + " (inherited from company)"
                : line + " (event-specific)";
    }

    private static String formatDiscountPolicyLine(PolicyViewResult discount, boolean eventScope) {
        String line = "Current discount: " + discount.description();
        if (!eventScope) {
            return line;
        }
        return discount.inheritedFromCompany()
                ? line + " (inherited from company)"
                : line + " (event-specific)";
    }

    private boolean isPoliciesTabActive() {
        VerticalLayout panel = panelByTab.get(tabByMode.get(CompanyMode.POLICIES));
        return panel != null && panel.isVisible();
    }

    private void ensurePolicyContextSynced() {
        CompanySummaryDTO selected = selectedCompanyName.getValue();
        if (selected == null) {
            return;
        }
        CompanySummaryDTO policyCompany = policyCompanyName.getValue();
        if (policyCompany == null || !policyCompany.name().equalsIgnoreCase(selected.name())) {
            policyCompanyName.setValue(selected);
        } else {
            reloadCompanyEvents(policyEventId, selected);
        }
    }

    private String resolvePolicyCompanyName() {
        String policyCompany = companyNameOf(policyCompanyName);
        if (policyCompany != null) {
            return policyCompany;
        }
        return companyNameOf(selectedCompanyName);
    }

    private void loadPurchasePolicy(boolean notifySuccess) {
        UUID eventId = selectedEventId(policyEventId);
        String companyName = resolvePolicyCompanyName();
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventPurchasePolicy(eventId, companyName);
        } else {
            result = presenter.loadCompanyPurchasePolicy(companyName);
        }
        applyPurchasePolicyResult(result, notifySuccess);
    }

    private void applyPurchasePolicyResult(PolicyViewResult result, boolean notifySuccess) {
        if (result.success() && result.purchasePolicy() != null) {
            applyPurchasePolicyToForm(result.purchasePolicy());
        }
        if (!notifySuccess) {
            return;
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText(result.success() ? "Current purchase: " + result.description() : "");
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void applyPurchasePolicyToForm(IPurchasePolicy policy) {
        if (policy == null || policy instanceof AlwaysAllowPolicy) {
            purchaseRuleParts.clear();
            policyComposition.setValue("AND (all must pass)");
            clearPurchaseThresholdFields();
            clearSecondaryPurchaseGroup();
            return;
        }
        if (policy instanceof AndPolicy and) {
            applyPurchaseComposite(and.getPolicies(), "AND (all must pass)");
            return;
        }
        if (policy instanceof OrPolicy or) {
            applyPurchaseComposite(or.getPolicies(), "OR (any can pass)");
            return;
        }
        applyPurchaseLeaves(List.of(policy), "Single rule");
    }

    private void applyPurchaseComposite(List<IPurchasePolicy> policies, String composition) {
        clearSecondaryPurchaseGroup();
        if (policies.size() == 2 && (isCompositePurchasePolicy(policies.get(0)) || isCompositePurchasePolicy(policies.get(1)))) {
            policyGroupComposition.setValue("AND (all must pass)".equals(composition)
                    ? "AND between groups"
                    : "OR between groups");
            applyPurchaseGroupToFields(policies.get(0), purchaseRuleParts, policyAge, policyMaxTickets, policyMinTickets,
                    policyComposition, compositionOf(policies.get(0)));
            applyPurchaseGroupToFields(policies.get(1), secondaryPurchaseRuleParts, secondaryPolicyAge,
                    secondaryPolicyMaxTickets, secondaryPolicyMinTickets, secondaryPolicyComposition,
                    compositionOf(policies.get(1)));
            return;
        }
        applyPurchaseLeaves(policies, composition);
    }

    private static boolean isCompositePurchasePolicy(IPurchasePolicy policy) {
        return policy instanceof AndPolicy || policy instanceof OrPolicy;
    }

    private static String compositionOf(IPurchasePolicy policy) {
        if (policy instanceof OrPolicy) {
            return "OR (any can pass)";
        }
        return "AND (all must pass)";
    }

    private void applyPurchaseLeaves(List<IPurchasePolicy> policies, String composition) {
        applyPurchaseLeavesToFields(policies, purchaseRuleParts, policyAge, policyMaxTickets, policyMinTickets,
                policyComposition, "Single rule".equals(composition) ? "AND (all must pass)" : composition);
        clearSecondaryPurchaseGroup();
    }

    private void applyPurchaseGroupToFields(
            IPurchasePolicy policy,
            CheckboxGroup<String> ruleParts,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField,
            ComboBox<String> compositionField,
            String composition
    ) {
        if (policy instanceof AndPolicy and) {
            applyPurchaseLeavesToFields(and.getPolicies(), ruleParts, ageField, maxTicketsField, minTicketsField,
                    compositionField, "AND (all must pass)");
            return;
        }
        if (policy instanceof OrPolicy or) {
            applyPurchaseLeavesToFields(or.getPolicies(), ruleParts, ageField, maxTicketsField, minTicketsField,
                    compositionField, "OR (any can pass)");
            return;
        }
        applyPurchaseLeavesToFields(List.of(policy), ruleParts, ageField, maxTicketsField, minTicketsField,
                compositionField, composition);
    }

    private void applyPurchaseLeavesToFields(
            List<IPurchasePolicy> policies,
            CheckboxGroup<String> ruleParts,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField,
            ComboBox<String> compositionField,
            String composition
    ) {
        compositionField.setValue(composition);
        ageField.clear();
        maxTicketsField.clear();
        minTicketsField.clear();
        LinkedHashSet<String> selectedRules = new LinkedHashSet<>();

        List<IPurchasePolicy> leaves = new ArrayList<>();
        for (IPurchasePolicy child : policies) {
            leaves.addAll(flattenPurchaseLeaves(child));
        }
        if (leaves.isEmpty()) {
            ruleParts.clear();
            return;
        }

        for (IPurchasePolicy leaf : leaves) {
            applyPurchaseLeaf(leaf, selectedRules, ageField, maxTicketsField, minTicketsField);
        }
        ruleParts.setValue(selectedRules);
    }

    private static List<IPurchasePolicy> flattenPurchaseLeaves(IPurchasePolicy policy) {
        if (policy instanceof AndPolicy and) {
            List<IPurchasePolicy> flattened = new ArrayList<>();
            for (IPurchasePolicy child : and.getPolicies()) {
                flattened.addAll(flattenPurchaseLeaves(child));
            }
            return flattened;
        }
        if (policy instanceof OrPolicy or) {
            List<IPurchasePolicy> flattened = new ArrayList<>();
            for (IPurchasePolicy child : or.getPolicies()) {
                flattened.addAll(flattenPurchaseLeaves(child));
            }
            return flattened;
        }
        if (policy instanceof AlwaysAllowPolicy) {
            return List.of();
        }
        return List.of(policy);
    }

    private void applyPurchaseLeaf(
            IPurchasePolicy leaf,
            Set<String> selectedRules,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField
    ) {
        if (leaf instanceof AgeRestrictionPolicy age) {
            selectedRules.add(PURCHASE_RULE_AGE);
            ageField.setValue(age.getMinimumAge());
        } else if (leaf instanceof MaxQuantityPolicy max) {
            selectedRules.add(PURCHASE_RULE_MAX);
            maxTicketsField.setValue(max.getMaxTickets());
        } else if (leaf instanceof MinQuantityPolicy min) {
            selectedRules.add(PURCHASE_RULE_MIN);
            minTicketsField.setValue(min.getMinTickets());
        } else if (leaf instanceof NoOrphanSeatPolicy) {
            selectedRules.add(PURCHASE_RULE_NO_ORPHAN);
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
        if (result.success()) {
            autoLoadPolicies();
        }
    }

    private void removePurchasePolicy() {
        DestructiveActionDialogs.confirmRemovePurchasePolicy(policyTargetLabel(), () -> {
            UUID eventId = selectedEventId(policyEventId);
            ActionResult result;
            if (eventId != null) {
                result = presenter.removeEventPurchasePolicy(eventId);
            } else {
                result = presenter.removeCompanyPurchasePolicy(companyNameOf(policyCompanyName));
            }
            policyStatus.setText(result.message());
            notify(result);
            if (result.success()) {
                autoLoadPolicies();
            }
        });
    }

    private void loadDiscountPolicy(boolean notifySuccess) {
        UUID eventId = selectedEventId(policyEventId);
        String companyName = resolvePolicyCompanyName();
        PolicyViewResult result;
        if (eventId != null) {
            result = presenter.loadEventDiscountPolicy(eventId, companyName);
        } else {
            result = presenter.loadCompanyDiscountPolicy(companyName);
        }
        applyDiscountPolicyResult(result, notifySuccess);
    }

    private void applyDiscountPolicyResult(PolicyViewResult result, boolean notifySuccess) {
        if (result.success() && result.discountPolicy() != null) {
            applyDiscountPolicyToForm(result.discountPolicy());
        }
        if (!notifySuccess) {
            return;
        }
        policyStatus.setText(result.message());
        currentPolicyDisplay.setText(result.success() ? "Current discount: " + result.description() : "");
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private void applyDiscountPolicyToForm(IDiscountPolicy policy) {
        if (policy == null || policy instanceof NoDiscountPolicy) {
            discountType.setValue("No discount");
            discountComposition.setValue("Single discount");
            discountDrafts.clear();
            renderDiscountDrafts();
            clearDiscountFormAuxiliaryFields();
            return;
        }
        if (policy instanceof MaxCompositeDiscount max) {
            applyDiscountLeaves(max.getPolicies(), "MAX (best discount wins)");
            return;
        }
        if (policy instanceof SumCompositeDiscount sum) {
            applyDiscountLeaves(sum.getPolicies(), "SUM (stack all)");
            return;
        }
        applyDiscountLeaves(List.of(policy), "Single discount");
    }

    private void applyDiscountLeaves(List<IDiscountPolicy> policies, String composition) {
        discountComposition.setValue(composition);
        clearDiscountFormAuxiliaryFields();
        discountDrafts.clear();
        if (policies.isEmpty()) {
            discountType.setValue("No discount");
            renderDiscountDrafts();
            return;
        }
        discountDrafts.addAll(policies);
        applyDiscountLeafPrimary(policies.getFirst());
        renderDiscountDrafts();
    }

    private void applyDiscountLeafPrimary(IDiscountPolicy leaf) {
        if (leaf instanceof SimpleDiscount simple) {
            discountType.setValue("Simple (flat %)");
            discountPercent.setValue(simple.getPercentOff());
        } else if (leaf instanceof CouponDiscount coupon) {
            discountType.setValue("Coupon (% with code)");
            discountPercent.setValue(coupon.getPercentOff());
            couponCodeField.setValue(coupon.getCouponCode());
            couponExpiry.setValue(LocalDateTime.ofInstant(coupon.getExpiresAt(), ZoneId.systemDefault()));
        } else if (leaf instanceof ConditionalDiscount conditional) {
            discountType.setValue("Conditional (% with condition)");
            discountPercent.setValue(conditional.getPercentOff());
            IDiscountCondition condition = conditional.getCondition();
            if (condition instanceof MinQuantityCondition min) {
                discountConditionType.setValue("Min tickets");
                conditionMinTickets.setValue(min.getMinTickets());
            } else if (condition instanceof MaxQuantityCondition max) {
                discountConditionType.setValue("Max tickets");
                conditionMaxTickets.setValue(max.getMaxTickets());
            } else if (condition instanceof DateRangeCondition range) {
                discountConditionType.setValue("Date range");
                if (range.getFrom() != null) {
                    conditionFrom.setValue(LocalDateTime.ofInstant(range.getFrom(), ZoneId.systemDefault()));
                }
                if (range.getTo() != null) {
                    conditionTo.setValue(LocalDateTime.ofInstant(range.getTo(), ZoneId.systemDefault()));
                }
            }
        }
    }

    private void addDiscountDraft() {
        IDiscountPolicy policy = buildSingleDiscountPolicy();
        if (policy == null) return;
        if (policy instanceof NoDiscountPolicy) {
            policyStatus.setText("Choose a discount type before adding a discount.");
            UiMessages.error("Choose a discount type before adding a discount.");
            return;
        }
        discountDrafts.add(policy);
        if (discountDrafts.size() == 2 && "Single discount".equals(discountComposition.getValue())) {
            discountComposition.setValue("SUM (stack all)");
        }
        renderDiscountDrafts();
        policyStatus.setText("Discount added to the draft list.");
    }

    private void clearDiscountDrafts() {
        discountDrafts.clear();
        discountComposition.setValue("Single discount");
        renderDiscountDrafts();
    }

    private void editDiscountDraft(int index) {
        if (index < 0 || index >= discountDrafts.size()) {
            return;
        }
        IDiscountPolicy policy = discountDrafts.remove(index);
        applyDiscountLeafPrimary(policy);
        if (discountDrafts.size() <= 1) {
            discountComposition.setValue("Single discount");
        }
        renderDiscountDrafts();
        policyStatus.setText("Discount loaded into the editor. Update it, then add it again.");
    }

    private void removeDiscountDraft(int index) {
        if (index < 0 || index >= discountDrafts.size()) {
            return;
        }
        discountDrafts.remove(index);
        if (discountDrafts.size() <= 1) {
            discountComposition.setValue("Single discount");
        }
        renderDiscountDrafts();
    }

    private void renderDiscountDrafts() {
        discountDraftList.removeAll();
        discountComposition.setVisible(discountDrafts.size() > 1);
        clearDiscountDraftsButton.setEnabled(!discountDrafts.isEmpty());
        if (discountDrafts.isEmpty()) {
            Span empty = new Span("No discounts added. Add one discount, or leave the policy as no discount.");
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
            discountDraftList.add(empty);
            return;
        }
        for (int i = 0; i < discountDrafts.size(); i++) {
            int index = i;
            Span description = new Span((i + 1) + ". " + DiscountPolicyText.describeManagementDiscount(discountDrafts.get(i)));
            description.getStyle().set("font-weight", "600");
            Button edit = new Button("Edit", e -> editDiscountDraft(index));
            Button remove = new Button("Remove", e -> removeDiscountDraft(index));
            HorizontalLayout row = new HorizontalLayout(description, edit, remove);
            row.setAlignItems(Alignment.BASELINE);
            row.setWidthFull();
            row.expand(description);
            discountDraftList.add(row);
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
        if (result.success()) {
            autoLoadPolicies();
        }
    }

    private void removeDiscountPolicy() {
        DestructiveActionDialogs.confirmRemoveDiscountPolicy(policyTargetLabel(), () -> {
            UUID eventId = selectedEventId(policyEventId);
            ActionResult result;
            if (eventId != null) {
                result = presenter.removeEventDiscountPolicy(eventId);
            } else {
                result = presenter.removeCompanyDiscountPolicy(companyNameOf(policyCompanyName));
            }
            policyStatus.setText(result.message());
            notify(result);
            if (result.success()) {
                autoLoadPolicies();
            }
        });
    }

    private IPurchasePolicy buildPurchasePolicy() {
        try {
            return buildPurchasePolicyInternal();
        } catch (IllegalArgumentException ex) {
            policyStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
            return null;
        }
    }

    private IPurchasePolicy buildPurchasePolicyInternal() {
        IPurchasePolicy primary = buildPurchaseGroup(
                purchaseRuleParts.getValue(), policyAge, policyMaxTickets, policyMinTickets, policyComposition);
        IPurchasePolicy secondary = buildPurchaseGroup(
                secondaryPurchaseRuleParts.getValue(), secondaryPolicyAge, secondaryPolicyMaxTickets,
                secondaryPolicyMinTickets, secondaryPolicyComposition);
        if (primary == null && secondary == null) {
            return new AlwaysAllowPolicy();
        }
        if (primary == null) {
            return secondary;
        }
        if (secondary == null) {
            return primary;
        }
        if ("AND between groups".equals(policyGroupComposition.getValue())) {
            return new AndPolicy(List.of(primary, secondary));
        }
        return new OrPolicy(List.of(primary, secondary));
    }

    private IPurchasePolicy buildPurchaseGroup(
            Set<String> selectedRules,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField,
            ComboBox<String> compositionField
    ) {
        if (selectedRules == null || selectedRules.isEmpty()) {
            return null;
        }
        List<IPurchasePolicy> rules = new ArrayList<>();
        for (String rule : List.of(PURCHASE_RULE_AGE, PURCHASE_RULE_MAX, PURCHASE_RULE_MIN, PURCHASE_RULE_NO_ORPHAN)) {
            if (selectedRules.contains(rule)) {
                IPurchasePolicy builtRule = buildSelectedPurchaseRule(rule, ageField, maxTicketsField, minTicketsField);
                if (builtRule == null) {
                    throw new IllegalArgumentException(policyStatus.getText());
                }
                rules.add(builtRule);
            }
        }

        if (rules.size() == 1) return rules.getFirst();

        if ("AND (all must pass)".equals(compositionField.getValue())) {
            return new AndPolicy(rules);
        }
        return new OrPolicy(rules);
    }

    private IPurchasePolicy buildSelectedPurchaseRule(
            String type,
            IntegerField ageField,
            IntegerField maxTicketsField,
            IntegerField minTicketsField
    ) {
        try {
            if (PURCHASE_RULE_AGE.equals(type)) {
                Integer age = ageField.getValue();
                if (age == null || age <= 0) {
                    policyStatus.setText("Min age must be positive.");
                    UiMessages.error("Min age must be positive.");
                    return null;
                }
                return new AgeRestrictionPolicy(age);
            } else if (PURCHASE_RULE_MAX.equals(type)) {
                Integer max = maxTicketsField.getValue();
                if (max == null || max <= 0) {
                    policyStatus.setText("Max tickets must be positive.");
                    UiMessages.error("Max tickets must be positive.");
                    return null;
                }
                return new MaxQuantityPolicy(max);
            } else if (PURCHASE_RULE_NO_ORPHAN.equals(type)) {
                return new NoOrphanSeatPolicy();
            } else {
                Integer min = minTicketsField.getValue();
                if (min == null || min < 2) {
                    policyStatus.setText("Min tickets must be at least 2.");
                    UiMessages.error("Min tickets must be at least 2.");
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
        if (discountDrafts.isEmpty()) {
            return buildSingleDiscountPolicy();
        }

        List<IDiscountPolicy> policies = new ArrayList<>(discountDrafts);
        if (policies.size() == 1) {
            return policies.getFirst();
        }

        String composition = discountComposition.getValue();
        if (composition == null || "Single discount".equals(composition)) {
            policyStatus.setText("Choose SUM or MAX when multiple discounts are added.");
            UiMessages.error("Choose SUM or MAX when multiple discounts are added.");
            return null;
        }

        if ("MAX (best discount wins)".equals(composition)) {
            return new MaxCompositeDiscount(policies);
        }
        return new SumCompositeDiscount(policies);
    }

    private IDiscountPolicy buildSingleDiscountPolicy() {
        String type = discountType.getValue();
        if ("No discount".equals(type)) {
            return new NoDiscountPolicy();
        }
        BigDecimal percent = discountPercent.getValue();
        if (percent == null || percent.compareTo(BigDecimal.ZERO) <= 0 || percent.compareTo(new BigDecimal("100")) > 0) {
            policyStatus.setText("Discount % must be greater than 0 and up to 100.");
            UiMessages.error("Discount % must be greater than 0 and up to 100.");
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
        if (result.success()) {
            resetOpenCompanyForm();
            selectCompanyByName(openedCompanyName(result));
        }
    }

    private void resetOpenCompanyForm() {
        openCompanyName.clear();
        openCompanyDescription.clear();
        openCompanyName.setInvalid(false);
    }

    private void selectCompanyByName(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return;
        }
        selectedCompanyName.getDataProvider().fetch(new Query<>())
                .filter(company -> company.name().equalsIgnoreCase(companyName))
                .findFirst()
                .ifPresent(selectedCompanyName::setValue);
    }

    private static String openedCompanyName(ActionResult result) {
        String prefix = "Company opened: ";
        String message = result.message();
        if (message != null && message.startsWith(prefix)) {
            return message.substring(prefix.length());
        }
        return null;
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
            offerTargetMember.setEnabled(true);
            offerTargetMember.setItems(presenter.listAppointableMembers());
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
        String currentUsername = presenter.currentSessionState().username();
        collectPersonnelTargets(roots, targets, currentUsername);
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

    private void collectPersonnelTargets(List<OrgNodeDTO> nodes, List<PersonnelTarget> targets, String currentUsername) {
        if (nodes == null) {
            return;
        }
        for (OrgNodeDTO node : nodes) {
            if (!node.revoked() && (currentUsername == null || !currentUsername.equals(node.username()))) {
                targets.add(new PersonnelTarget(node.memberId(), node.username(), node.role()));
            }
            collectPersonnelTargets(node.subordinates(), targets, currentUsername);
        }
    }

    private CompanyPresenter.PersonnelAccessResult refreshPersonnelAccess() {
        if (offerRoleButton == null || changeManagerPermissionsButton == null
                || revokePersonnelButton == null || loadOrganizationChartButton == null
                || relinquishOwnershipButton == null || ownerPersonnelControls == null) {
            return CompanyPresenter.PersonnelAccessResult.denied("Select a company to show owner-only permission controls.");
        }
        String companyName = companyNameOf(personnelCompanyName);
        if (companyName == null) {
            ownerPersonnelControls.setVisible(false);
            orgChartDisplay.setVisible(false);
            orgChartDisplay.removeAll();
            offerRoleButton.setVisible(false);
            revokePersonnelButton.setVisible(false);
            changeManagerPermissionsButton.setVisible(false);
            relinquishOwnershipButton.setVisible(false);
            loadOrganizationChartButton.setVisible(false);
            personnelAccessHint.setText("Select a company to show owner-only permission controls.");
            personnelAccessHint.setVisible(true);
            return CompanyPresenter.PersonnelAccessResult.denied("Select a company to show owner-only permission controls.");
        }

        CompanyPresenter.PersonnelAccessResult result = presenter.loadPersonnelAccess(companyName);
        boolean canManagePersonnel = result.canManagePersonnel();
        ownerPersonnelControls.setVisible(canManagePersonnel);
        orgChartDisplay.setVisible(canManagePersonnel);
        if (!canManagePersonnel) {
            orgChartDisplay.removeAll();
        }
        offerRoleButton.setVisible(canManagePersonnel);
        revokePersonnelButton.setVisible(canManagePersonnel);
        changeManagerPermissionsButton.setVisible(canManagePersonnel);
        relinquishOwnershipButton.setVisible(canManagePersonnel);
        loadOrganizationChartButton.setVisible(canManagePersonnel);
        personnelAccessHint.setText(result.message());
        personnelAccessHint.setVisible(!canManagePersonnel);
        return result;
    }

    private void refreshEventAccess() {
        if (editEventButton == null) {
            return;
        }
        CompanyAccessResult access = companyAccessFor(eventCompanyName, "Select a company to show event controls.");
        boolean eventLifecycle = access.canManageEvents();
        boolean mapDefinition = access.canDefineMaps();
        eventControls.setVisible(true);
        eventSelectionForm.setVisible(true);
        eventActions.setVisible(true);
        designHallButton.setVisible(true);
        designHallButton.setEnabled(eventLifecycle);
        editEventButton.setVisible(true);
        editEventButton.setEnabled(eventLifecycle);
        editVenueLayoutButton.setVisible(true);
        editVenueLayoutButton.setEnabled(mapDefinition);
        boolean denied = access.companyName() != null && !eventLifecycle && !mapDefinition;
        eventStatus.setText(denied
                ? missingPermissionsMessage(access, ManagerPermission.EVENT_LIFECYCLE, ManagerPermission.MAP_DEFINITION)
                : "");
        eventStatus.setVisible(denied);
    }

    private void refreshInventoryAccess() {
        if (addSeatButton == null) {
            return;
        }
        CompanyAccessResult access = companyAccessFor(inventoryCompanyName, "Select a company to show inventory controls.");
        boolean inventory = access.canManageInventory();
        inventoryForm.setVisible(inventory);
        inventoryManagementControls.setVisible(inventory);
        addSeatButton.setVisible(inventory);
        removeSeatButton.setVisible(inventory);
        increaseCapacityButton.setVisible(inventory);
        decreaseCapacityButton.setVisible(inventory);
        setZonePriceButton.setVisible(inventory);
        inventoryStatus.setText(access.companyName() == null || inventory
                ? access.message()
                : missingPermissionsMessage(access, ManagerPermission.INVENTORY_MGMT, ManagerPermission.MAP_DEFINITION));
    }

    private void refreshReportingAccess() {
        if (loadHistoryButton == null) {
            return;
        }
        CompanyAccessResult access = companyAccessFor(reportingCompanyName, "Select a company to show reporting controls.");
        boolean reporting = access.canViewReports();
        reportingControls.setVisible(reporting);
        loadHistoryButton.setVisible(reporting);
        loadSalesReportButton.setVisible(reporting);
        reportingStatus.setText(access.companyName() == null || reporting
                ? access.message()
                : missingPermissionsMessage(access, ManagerPermission.VIEW_REPORTS));
    }

    private void refreshPolicyAccess() {
        if (policyControls == null) {
            return;
        }
        CompanyAccessResult access = companyAccessFor(policyCompanyName, "Select a company to show policy controls.");
        boolean policy = access.canManagePolicies();
        policyControls.setVisible(policy);
        setPurchasePolicyButton.setVisible(policy);
        removePurchasePolicyButton.setVisible(policy);
        setDiscountPolicyButton.setVisible(policy);
        removeDiscountPolicyButton.setVisible(policy);
        policyStatus.setText(access.companyName() == null || policy
                ? access.message()
                : missingPermissionsMessage(access, ManagerPermission.POLICY_MODIFICATION));
    }

    private String missingPermissionsMessage(CompanyAccessResult access, ManagerPermission... permissions) {
        String username = presenter.currentSessionState().username();
        String displayName = username == null || username.isBlank() ? "current user" : username;
        String permissionText = permissions.length == 1
                ? permissions[0].name() + " permission"
                : String.join(", ", java.util.Arrays.stream(permissions)
                        .map(ManagerPermission::name)
                        .toList()) + " permissions";
        String company = access.companyName() == null ? "" : " for " + access.companyName();
        return "User \"" + displayName + "\" doesn't have " + permissionText + company + ".";
    }

    private CompanyAccessResult companyAccessFor(ComboBox<CompanySummaryDTO> picker, String emptyMessage) {
        String companyName = companyNameOf(picker);
        if (companyName == null) {
            return CompanyAccessResult.denied(null, emptyMessage);
        }
        CompanyAccessResult result = presenter.loadCompanyAccess(companyName);
        return result == null ? CompanyAccessResult.denied(companyName, emptyMessage) : result;
    }

    private UUID selectedTargetMemberId() {
        PersonnelTarget selected = targetMember.getValue();
        return selected == null ? null : selected.memberId();
    }

    private UUID selectedOfferTargetMemberId() {
        com.ticketing.application.dto.MemberSummaryDTO selected = offerTargetMember.getValue();
        return selected == null ? null : selected.id();
    }

    private void refreshLifecycleAccess() {
        if (suspendCompanyButton == null || reopenCompanyButton == null) {
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
        lifecycleActions.setVisible(visible);
        suspendCompanyButton.setVisible(visible);
        reopenCompanyButton.setVisible(visible);
    }

    private void openVenueDesigner() {
        String company = companyNameOf(eventCompanyName);
        if (company == null) {
            UiMessages.error("Select a company first.");
            return;
        }
        new VenueDesignerDialog(presenter, company).open();
    }

    private void openEditVenueLayout() {
        String company = companyNameOf(eventCompanyName);
        if (company == null) {
            UiMessages.error("Select a company first.");
            return;
        }
        new EditVenueLayoutDialog(presenter, company).open();
    }

    private void openEditEventDialog() {
        String company = companyNameOf(eventCompanyName);
        if (company == null) {
            UiMessages.error("Select a company first.");
            return;
        }
        new EditEventDialog(presenter, company).open();
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
        if (result.success()) {
            refreshAfterPersonnelChange();
        }
        notify(result);
    }

    private void refreshAfterPersonnelChange() {
        refreshSessionStatus();
        refreshPersonnelContext();
        refreshEventAccess();
        refreshInventoryAccess();
        refreshReportingAccess();
        refreshPolicyAccess();
        refreshLifecycleAccess();
    }

    private void handleInventoryResult(ActionResult result) {
        inventoryStatus.setText(result.message());
        notify(result);
    }

    private void handleLifecycleResult(ActionResult result) {
        lifecycleStatus.setText(result.message());
        if (result.success()) {
            populatePickerItems();
            lookupEventPicker.setItems(orEmpty(presenter.searchBrowsableEvents()));
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
        Span username = new Span(node.username());
        username.getStyle()
                .set("font-weight", "600")
                .set("font-size", "1rem");
        Span roleBadge = badge(node.role().name(), "var(--lumo-primary-color-10pct)", "var(--lumo-primary-text-color)");
        Span status = node.revoked()
                ? badge("Revoked", "var(--lumo-error-color-10pct)", "var(--lumo-error-text-color)")
                : badge("Active", "var(--lumo-success-color-10pct)", "var(--lumo-success-text-color)");

        HorizontalLayout summary = new HorizontalLayout(username, roleBadge, status);
        summary.setAlignItems(Alignment.CENTER);
        summary.setSpacing(true);
        if (node.revoked()) {
            username.getStyle().set("color", "var(--lumo-error-text-color)");
        }

        Span memberId = new Span("ID: " + node.memberId());
        memberId.getStyle()
                .set("font-family", "monospace")
                .set("font-size", "0.85rem")
                .set("color", "var(--lumo-secondary-text-color)");

        VerticalLayout permissionsBlock = new VerticalLayout(new Span("Permissions"));
        permissionsBlock.setPadding(false);
        permissionsBlock.setSpacing(false);
        permissionsBlock.getStyle().set("gap", "0.35rem");
        permissionsBlock.add(permissionChips(node));

        VerticalLayout content = new VerticalLayout(memberId, permissionsBlock);
        content.setPadding(false);
        content.setSpacing(false);
        content.getStyle()
                .set("gap", "0.65rem")
                .set("padding", "0.35rem 0 0.2rem 1.15rem");

        VerticalLayout children = new VerticalLayout();
        children.setPadding(false);
        children.setSpacing(false);
        children.getStyle()
                .set("gap", "0.45rem")
                .set("margin-top", "0.5rem")
                .set("padding-left", "1rem")
                .set("border-left", "1px solid var(--lumo-contrast-20pct)");
        for (OrgNodeDTO child : node.subordinates()) {
            children.add(orgNode(child));
        }
        if (!node.subordinates().isEmpty()) {
            content.add(children);
        }
        Details details = new Details(summary, content);
        details.setOpened(true);
        details.getStyle()
                .set("border", "1px solid var(--lumo-contrast-20pct)")
                .set("border-left", node.revoked()
                        ? "4px solid var(--lumo-error-text-color)"
                        : "4px solid var(--lumo-primary-color)")
                .set("border-radius", "6px")
                .set("padding", "0.45rem 0.65rem")
                .set("background", node.revoked()
                        ? "var(--lumo-error-color-10pct)"
                        : "var(--lumo-base-color)")
                .set("box-shadow", "0 1px 2px var(--lumo-contrast-10pct)");
        return details;
    }

    private static HorizontalLayout permissionChips(OrgNodeDTO node) {
        HorizontalLayout chips = new HorizontalLayout();
        chips.setPadding(false);
        chips.setSpacing(true);
        chips.getStyle().set("flex-wrap", "wrap");
        if (node.permissions() == null || node.permissions().isEmpty()) {
            chips.add(badge("No manager permissions", "var(--lumo-contrast-10pct)", "var(--lumo-secondary-text-color)"));
            return chips;
        }
        node.permissions().stream()
                .map(ManagerPermission::name)
                .sorted()
                .map(permission -> badge(permission, "var(--lumo-contrast-10pct)", "var(--lumo-body-text-color)"))
                .forEach(chips::add);
        return chips;
    }

    private static Span badge(String text, String background, String color) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("min-height", "1.45rem")
                .set("padding", "0 0.45rem")
                .set("border-radius", "999px")
                .set("background", background)
                .set("color", color)
                .set("font-size", "0.78rem")
                .set("font-weight", "600");
        return badge;
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
        return price == null ? "N/A" : "$" + price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
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
