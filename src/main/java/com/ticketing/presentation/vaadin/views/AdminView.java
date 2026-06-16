package com.ticketing.presentation.vaadin.views;

import static com.ticketing.presentation.vaadin.util.RequiredFields.markRequired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.MemberSummaryDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.dto.SystemAnalyticsDTO;
import com.ticketing.application.dto.SystemAnalyticsDTO.AnalyticsMetricsDTO;
import com.ticketing.application.dto.SystemAnalyticsDTO.AnalyticsRateDTO;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SuspensionListResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SystemAnalyticsResult;
import com.ticketing.presentation.vaadin.util.DestructiveActionDialogs;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin")
@SpringComponent
@UIScope
public class AdminView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AdminPresenter presenter;

    private final Span sessionStatus = new Span();
    private final Paragraph adminOnlyHint = new Paragraph("Log in with system admin permissions to use admin actions.");
    private final Tabs adminTabs = new Tabs();
    private final VerticalLayout adminModeContent = new VerticalLayout();
    private final Map<AdminMode, VerticalLayout> panelByMode = new EnumMap<>(AdminMode.class);
    private final Map<Tab, AdminMode> modeByTab = new HashMap<>();
    private VerticalLayout memberControls;
    private VerticalLayout companyControls;
    private VerticalLayout purchaseHistoryControls;
    private VerticalLayout suspensionControls;
    private VerticalLayout analyticsControls;

    private final Span analyticsStatus = new Span("Load live and historical platform analytics.");
    private final Span activeVisitorsDisplay = new Span();
    private final Grid<AnalyticsRow> analyticsGrid = new Grid<>(AnalyticsRow.class, false);

    private final Span memberStatus = new Span("Remove members using system admin authorization.");
    private final ComboBox<MemberSummaryDTO> removeMemberPicker = new ComboBox<>("Target member");

    private final Span companyStatus = new Span("Close production companies using system admin authorization.");
    private final ComboBox<CompanySummaryDTO> closeCompanyPicker = new ComboBox<>("Company to close");

    private final Span historyStatus = new Span("Load global purchase history by buyer, company, or all purchases.");
    private final ComboBox<MemberSummaryDTO> historyBuyerPicker = new ComboBox<>("Buyer member");
    private final TextField historyCompanyName = new TextField("Company name");
    private final Grid<PurchaseRecordDTO> purchaseHistoryGrid = new Grid<>(PurchaseRecordDTO.class, false);

    private final Span suspensionStatus = new Span("Suspend members and view active or historical suspensions.");
    private final ComboBox<MemberSummaryDTO> suspensionTargetPicker = new ComboBox<>("Suspension target member");
    private final IntegerField suspensionDurationDays = new IntegerField("Duration days");
    private final Checkbox permanentSuspension = new Checkbox("Permanent suspension");
    private final TextArea suspensionReason = new TextArea("Suspension reason");
    private final Checkbox activeSuspensionsOnly = new Checkbox("Active suspensions only");
    private final Grid<SuspensionDTO> suspensionsGrid = new Grid<>(SuspensionDTO.class, false);
    private Button cancelSuspensionButton;
    private SuspensionDTO selectedSuspension;

    public AdminView(AdminPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1180px");
        getStyle().set("margin", "0 auto");

        configurePickers();
        configureFields();
        configurePurchaseHistoryGrid();
        configureSuspensionsGrid();
        configureAnalyticsGrid();
        add(
                new H2("Admin"),
                new Paragraph("Use system admin actions backed directly by application services."),
                new Paragraph("Application services still enforce system-admin authorization for every action and their responses are shown here."),
                sessionStatus,
                adminOnlyHint,
                adminActionsSection()
        );
        refreshSessionStatus();
    }

    private void configurePickers() {
        removeMemberPicker.setItemLabelGenerator(MemberSummaryDTO::username);
        removeMemberPicker.setPlaceholder("Search by username");

        closeCompanyPicker.setItemLabelGenerator(CompanySummaryDTO::name);
        closeCompanyPicker.setPlaceholder("Search by company name");

        historyBuyerPicker.setItemLabelGenerator(MemberSummaryDTO::username);
        historyBuyerPicker.setPlaceholder("Optional — search by username");
        historyBuyerPicker.setClearButtonVisible(true);

        suspensionTargetPicker.setItemLabelGenerator(MemberSummaryDTO::username);
        suspensionTargetPicker.setPlaceholder("Search by username");
    }

    private void configureFields() {
        historyCompanyName.setPlaceholder("Optional company filter");
        suspensionDurationDays.setMin(1);
        suspensionDurationDays.setValue(7);
        suspensionReason.setPlaceholder("Reason shown in application error/status flows");
        activeSuspensionsOnly.setValue(true);

        markRequired(suspensionTargetPicker, "Select a suspension target member.");
        markRequired(closeCompanyPicker, "Select a company to close.");
    }

    private void configurePurchaseHistoryGrid() {
        purchaseHistoryGrid.setId("admin-global-purchases-grid");
        purchaseHistoryGrid.setEmptyStateText("No purchases recorded yet.");
        purchaseHistoryGrid.addColumn(PurchaseRecordDTO::eventName).setHeader("Event").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(PurchaseRecordDTO::companyName).setHeader("Company").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> valueOrEmpty(purchase.buyerUsername())).setHeader("Buyer").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> formatPrice(purchase.amount())).setHeader("Amount").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> formatInstant(purchase.purchasedAt())).setHeader("Purchased at").setAutoWidth(true);
        purchaseHistoryGrid.setMinHeight("180px");
    }

    private void configureSuspensionsGrid() {
        suspensionsGrid.setId("admin-suspensions-grid");
        suspensionsGrid.setEmptyStateText("No suspensions — all members are in good standing.");
        suspensionsGrid.addColumn(suspension -> suspension.suspensionId().toString()).setHeader("Suspension ID").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::memberUsername).setHeader("Member").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::active).setHeader("Active").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::permanent).setHeader("Permanent").setAutoWidth(true);
        suspensionsGrid.addColumn(suspension -> formatDuration(suspension.duration())).setHeader("Duration").setAutoWidth(true);
        suspensionsGrid.addColumn(suspension -> formatInstant(suspension.startTime())).setHeader("Started").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::reason).setHeader("Reason").setAutoWidth(true);
        suspensionsGrid.setMinHeight("180px");
        suspensionsGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedSuspension = event.getValue();
            refreshCancelSuspensionState();
        });
    }

    private void configureAnalyticsGrid() {
        analyticsGrid.setId("admin-analytics-grid");
        analyticsGrid.setEmptyStateText("Load analytics to see visitor, registration and reservation rates.");
        analyticsGrid.addColumn(AnalyticsRow::metric).setHeader("Metric").setAutoWidth(true);
        analyticsGrid.addColumn(AnalyticsRow::liveCount).setHeader("Live count").setAutoWidth(true);
        analyticsGrid.addColumn(AnalyticsRow::liveRate).setHeader("Live / min").setAutoWidth(true);
        analyticsGrid.addColumn(AnalyticsRow::historicalCount).setHeader("Historical count").setAutoWidth(true);
        analyticsGrid.addColumn(AnalyticsRow::historicalRate).setHeader("Historical / min").setAutoWidth(true);
        analyticsGrid.setAllRowsVisible(true);
        analyticsGrid.setWidthFull();
    }

    private void refreshCancelSuspensionState() {
        if (cancelSuspensionButton != null) {
            cancelSuspensionButton.setEnabled(selectedSuspension != null);
        }
    }

    private VerticalLayout adminActionsSection() {
        configureAdminPanels();
        configureAdminTabs();

        VerticalLayout section = new VerticalLayout(
                new H3("System admin actions"),
                new Paragraph("Admin-only controls are kept separate from company owner and manager workflows."),
                adminTabs,
                adminModeContent
        );
        section.setPadding(false);
        section.setSpacing(true);
        return section;
    }

    private void configureAdminPanels() {
        adminModeContent.setPadding(false);
        adminModeContent.setSpacing(true);
        panelByMode.put(AdminMode.MEMBERS, memberSection());
        panelByMode.put(AdminMode.COMPANIES, companySection());
        panelByMode.put(AdminMode.PURCHASES, purchaseHistorySection());
        panelByMode.put(AdminMode.SUSPENSIONS, suspensionSection());
        panelByMode.put(AdminMode.ANALYTICS, analyticsSection());
        panelByMode.values().forEach(adminModeContent::add);
    }

    private void configureAdminTabs() {
        adminTabs.removeAll();
        adminTabs.getElement().setAttribute("aria-label", "Admin sections");
        modeByTab.clear();
        for (AdminMode mode : AdminMode.values()) {
            Tab tab = new Tab(mode.label);
            modeByTab.put(tab, mode);
            adminTabs.add(tab);
        }
        adminTabs.addSelectedChangeListener(event -> showAdminPanel(event.getSelectedTab()));
        adminTabs.setSelectedIndex(0);
        showAdminPanel(adminTabs.getSelectedTab());
    }

    private void showAdminPanel(Tab selectedTab) {
        panelByMode.values().forEach(panel -> panel.setVisible(false));
        AdminMode mode = modeByTab.get(selectedTab);
        if (mode != null) {
            panelByMode.get(mode).setVisible(true);
        }
    }

    private VerticalLayout memberSection() {
        Button removeMember = new Button("Remove member", event -> removeMember());

        FormLayout form = new FormLayout(removeMemberPicker);
        VerticalLayout section = new VerticalLayout(
                new H4("Member administration"),
                form,
                removeMember,
                memberStatus
        );
        section.setPadding(false);
        memberControls = section;
        return section;
    }

    private VerticalLayout companySection() {
        Button closeCompany = new Button("Close company", event -> closeCompany());

        FormLayout form = new FormLayout(closeCompanyPicker);
        VerticalLayout section = new VerticalLayout(
                new H4("Company administration"),
                new Paragraph("Admin close permanently closes the company, revokes staff appointments, and notifies company staff."),
                form,
                closeCompany,
                companyStatus
        );
        section.setPadding(false);
        companyControls = section;
        return section;
    }

    private VerticalLayout purchaseHistorySection() {
        Button loadHistory = new Button("Load global purchase history", event -> loadPurchaseHistory());

        FormLayout form = new FormLayout(historyBuyerPicker, historyCompanyName);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        VerticalLayout section = new VerticalLayout(
                new H4("Global purchase history"),
                form,
                loadHistory,
                historyStatus,
                purchaseHistoryGrid
        );
        section.setPadding(false);
        purchaseHistoryControls = section;
        return section;
    }

    private VerticalLayout suspensionSection() {
        Button suspend = new Button("Suspend member", event -> suspendMember());
        cancelSuspensionButton = new Button("Cancel suspension", event -> cancelSuspension());
        cancelSuspensionButton.setEnabled(false);
        Button load = new Button("Load suspensions", event -> loadSuspensions());

        FormLayout form = new FormLayout(
                suspensionTargetPicker,
                suspensionDurationDays,
                permanentSuspension,
                suspensionReason,
                activeSuspensionsOnly
        );
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));
        HorizontalLayout actions = new HorizontalLayout(suspend, cancelSuspensionButton, load);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H4("Suspensions"),
                form,
                actions,
                suspensionStatus,
                suspensionsGrid
        );
        section.setPadding(false);
        suspensionControls = section;
        return section;
    }

    private VerticalLayout analyticsSection() {
        Button loadAnalytics = new Button("Load system analytics", event -> loadSystemAnalytics());

        VerticalLayout section = new VerticalLayout(
                new H4("System analytics"),
                new Paragraph("Live and historical rates for visitor traffic, registrations, reservations and purchases."),
                loadAnalytics,
                activeVisitorsDisplay,
                analyticsStatus,
                analyticsGrid
        );
        section.setPadding(false);
        analyticsControls = section;
        return section;
    }

    private void loadSystemAnalytics() {
        SystemAnalyticsResult result = presenter.loadSystemAnalytics();
        if (!result.success() || result.analytics() == null) {
            analyticsStatus.setText(result.message());
            activeVisitorsDisplay.setText("");
            analyticsGrid.setItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        SystemAnalyticsDTO analytics = result.analytics();
        analyticsStatus.setText(result.message() + " Live window: " + analytics.liveWindowLabel()
                + ". Historical window: " + analytics.historicalWindowLabel() + ".");
        activeVisitorsDisplay.setText("Active visitors: " + analytics.activeVisitors());
        analyticsGrid.setItems(analyticsRows(analytics.live(), analytics.historical()));
        UiMessages.success(result.message());
    }

    private static List<AnalyticsRow> analyticsRows(AnalyticsMetricsDTO live, AnalyticsMetricsDTO historical) {
        return List.of(
                row("Visitor enter", live.visitorEnter(), historical.visitorEnter()),
                row("Visitor exit", live.visitorExit(), historical.visitorExit()),
                row("Member registration", live.registration(), historical.registration()),
                row("Ticket reservation", live.reservation(), historical.reservation()),
                row("Ticket purchase", live.purchase(), historical.purchase()));
    }

    private static AnalyticsRow row(String metric, AnalyticsRateDTO live, AnalyticsRateDTO historical) {
        return new AnalyticsRow(
                metric,
                Long.toString(live.count()),
                formatRate(live.perMinute()),
                Long.toString(historical.count()),
                formatRate(historical.perMinute()));
    }

    private static String formatRate(double perMinute) {
        return String.format("%.2f", perMinute);
    }

    private record AnalyticsRow(String metric, String liveCount, String liveRate, String historicalCount, String historicalRate) {
    }

    private void removeMember() {
        MemberSummaryDTO target = requireSelected(removeMemberPicker, memberStatus, "Select a target member.");
        if (target == null) {
            return;
        }
        DestructiveActionDialogs.confirmRemoveMember(target.username(), () -> {
            ActionResult result = presenter.removeMember(target.id());
            handleMemberResult(result);
            if (result.success()) {
                removeMemberPicker.clear();
                loadMemberPickerItems();
            }
        });
    }

    private void closeCompany() {
        CompanySummaryDTO company = requireSelected(closeCompanyPicker, companyStatus, "Select a company to close.");
        if (company == null) {
            return;
        }
        DestructiveActionDialogs.confirmCloseCompany(company.name(), () -> {
            ActionResult result = presenter.closeCompany(company.name());
            companyStatus.setText(result.message());
            notify(result);
            if (result.success()) {
                closeCompanyPicker.clear();
                loadCompanyPickerItems();
            }
        });
    }

    private void suspendMember() {
        MemberSummaryDTO target = requireSelected(suspensionTargetPicker, suspensionStatus, "Select a suspension target member.");
        if (target == null) {
            return;
        }
        DestructiveActionDialogs.confirmSuspendMember(target.username(), () -> handleSuspensionAction(presenter.suspendUser(
                target.id(),
                suspensionDurationDays.getValue(),
                permanentSuspension.getValue(),
                suspensionReason.getValue()
        )));
    }

    private void cancelSuspension() {
        if (selectedSuspension == null) {
            suspensionStatus.setText("Select a suspension to cancel.");
            UiMessages.error("Select a suspension to cancel.");
            return;
        }
        DestructiveActionDialogs.confirmCancelSuspension(selectedSuspension.memberUsername(), () -> {
            ActionResult result = presenter.cancelSuspension(selectedSuspension.memberId(), selectedSuspension.suspensionId());
            handleSuspensionAction(result);
            if (result.success()) {
                suspensionsGrid.asSingleSelect().clear();
            }
        });
    }

    private void loadPurchaseHistory() {
        MemberSummaryDTO buyer = historyBuyerPicker.getValue();
        UUID buyerId = buyer == null ? null : buyer.id();
        PurchaseHistoryResult result;
        try {
            result = presenter.loadGlobalPurchaseHistory(buyerId, historyCompanyName.getValue());
        } catch (IllegalArgumentException ex) {
            historyStatus.setText(ex.getMessage());
            purchaseHistoryGrid.setItems(List.of());
            UiMessages.error(ex.getMessage());
            return;
        }

        if (!result.success()) {
            historyStatus.setText(result.message());
            purchaseHistoryGrid.setItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        historyStatus.setText(result.message());
        purchaseHistoryGrid.setItems(result.purchases());
        UiMessages.success(result.message());
    }

    private void loadSuspensions() {
        SuspensionListResult result = presenter.listSuspensions(activeSuspensionsOnly.getValue());
        if (!result.success()) {
            suspensionStatus.setText(result.message());
            suspensionsGrid.setItems(List.of());
            UiMessages.error(result.message());
            return;
        }

        suspensionStatus.setText(result.message());
        suspensionsGrid.setItems(result.suspensions());
        UiMessages.success(result.message());
    }

    private void handleMemberResult(ActionResult result) {
        memberStatus.setText(result.message());
        notify(result);
    }

    private void handleSuspensionAction(ActionResult result) {
        suspensionStatus.setText(result.message());
        notify(result);
    }

    private void notify(ActionResult result) {
        if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private static <T> T requireSelected(ComboBox<T> picker, Span status, String message) {
        T selected = picker.getValue();
        if (selected == null) {
            status.setText(message);
            UiMessages.error(message);
        }
        return selected;
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
        boolean admin = presenter.currentSessionState().systemAdmin();
        adminOnlyHint.setVisible(!admin);
        adminTabs.setVisible(admin);
        adminModeContent.setVisible(admin);
        panelByMode.values().forEach(panel -> panel.setVisible(false));
        if (admin) {
            showAdminPanel(adminTabs.getSelectedTab());
            loadMemberPickerItems();
            loadCompanyPickerItems();
        }
    }

    private void loadMemberPickerItems() {
        List<MemberSummaryDTO> members = presenter.searchMembers("");
        removeMemberPicker.setItems(members);
        historyBuyerPicker.setItems(members);
        suspensionTargetPicker.setItems(members);
    }

    private void loadCompanyPickerItems() {
        closeCompanyPicker.setItems(presenter.searchCompanies(""));
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "" : DATE_TIME_FORMATTER.format(instant);
    }

    private String formatPrice(BigDecimal price) {
        return price == null ? "N/A" : price.toPlainString();
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return "Permanent";
        }
        long days = duration.toDays();
        return days > 0 ? days + " day(s)" : duration.toString();
    }

    private String valueOrEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private enum AdminMode {
        MEMBERS("Members"),
        COMPANIES("Companies"),
        PURCHASES("Purchase history"),
        SUSPENSIONS("Suspensions"),
        ANALYTICS("Analytics");

        private final String label;

        AdminMode(String label) {
            this.label = label;
        }
    }
}
