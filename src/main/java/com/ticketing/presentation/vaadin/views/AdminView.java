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
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.QueueListResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.QueueSummary;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SuspensionListResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.shared.Registration;
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

    // ── Queue tab ────────────────────────────────────────────────────
    private final Span queueStatus = new Span("Load active queues to manage virtual queue settings.");
    private final Grid<QueueSummary> queuesGrid = new Grid<>(QueueSummary.class, false);
    private final IntegerField newFlowRateField = new IntegerField("New flow rate");
    private final TextField createQueueEventIdField = new TextField("Event ID");
    private final IntegerField createQueueThresholdField = new IntegerField("Threshold");
    private final IntegerField createQueueFlowRateField = new IntegerField("Flow rate");
    private QueueSummary selectedQueue;
    private Button updateFlowRateButton;
    private Button flushQueueButton;
    private Registration queuePollRegistration;

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
        configureQueuesGrid();
        add(
                new H2("Admin"),
                new Paragraph("Use system admin actions backed directly by application services."),
                new Paragraph("Application services still enforce system-admin authorization for every action and their responses are shown here."),
                sessionStatus,
                adminOnlyHint,
                adminActionsSection()
        );
        refreshSessionStatus();
        addDetachListener(e -> stopQueuePolling());
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

    private void refreshCancelSuspensionState() {
        if (cancelSuspensionButton != null) {
            cancelSuspensionButton.setEnabled(selectedSuspension != null);
        }
    }

    private void configureQueuesGrid() {
        queuesGrid.setId("admin-queues-grid");
        queuesGrid.setEmptyStateText("No active queues found.");
        queuesGrid.addColumn(QueueSummary::eventName).setHeader("Event").setAutoWidth(true).setFlexGrow(1);
        queuesGrid.addColumn(QueueSummary::waitingCount).setHeader("Waiting").setAutoWidth(true);
        queuesGrid.addColumn(QueueSummary::flowRate).setHeader("Flow rate").setAutoWidth(true);
        queuesGrid.addColumn(QueueSummary::threshold).setHeader("Threshold").setAutoWidth(true);
        queuesGrid.addColumn(QueueSummary::activeUsers).setHeader("Active users").setAutoWidth(true);
        queuesGrid.setMinHeight("160px");
        queuesGrid.asSingleSelect().addValueChangeListener(event -> {
            selectedQueue = event.getValue();
            refreshQueueControlsState();
        });
    }

    private VerticalLayout queueSection() {
        newFlowRateField.setMin(1);
        newFlowRateField.setValue(10);
        createQueueThresholdField.setMin(1);
        createQueueThresholdField.setValue(100);
        createQueueFlowRateField.setMin(1);
        createQueueFlowRateField.setValue(10);
        createQueueEventIdField.setPlaceholder("e.g. 11111111-1111-1111-1111-111111111111");

        updateFlowRateButton = new Button("Update flow rate", e -> doUpdateFlowRate());
        flushQueueButton = new Button("Clear queue", e -> doFlushQueue());
        flushQueueButton.getStyle().set("color", "var(--lumo-error-color)");
        updateFlowRateButton.setEnabled(false);
        flushQueueButton.setEnabled(false);

        Button refreshButton = new Button("Refresh", e -> refreshQueues());
        HorizontalLayout gridActions = new HorizontalLayout(refreshButton, queueStatus);
        gridActions.setAlignItems(Alignment.CENTER);

        FormLayout selectedQueueForm = new FormLayout(newFlowRateField);
        HorizontalLayout selectedQueueActions = new HorizontalLayout(updateFlowRateButton, flushQueueButton);
        selectedQueueActions.setAlignItems(Alignment.BASELINE);

        Button createQueueButton = new Button("Create queue", e -> doCreateQueue());
        FormLayout createForm = new FormLayout(createQueueEventIdField, createQueueThresholdField, createQueueFlowRateField);
        createForm.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3));

        VerticalLayout section = new VerticalLayout(
                new H4("Active queues"),
                new Paragraph("Active virtual queues update every 5 seconds while this tab is open."),
                queuesGrid,
                gridActions,
                new H4("Selected queue controls"),
                new Paragraph("Select a queue above to adjust its flow rate or clear all waiting entries."),
                selectedQueueForm,
                selectedQueueActions,
                new H4("Create queue"),
                new Paragraph("Create a virtual queue for a high-demand event."),
                createForm,
                createQueueButton
        );
        section.setPadding(false);
        return section;
    }

    private void refreshQueues() {
        QueueListResult result = presenter.loadActiveQueues();
        queueStatus.setText(result.message());
        if (!result.success()) {
            UiMessages.error(result.message());
            queuesGrid.setItems(List.of());
            return;
        }
        queuesGrid.setItems(result.queues());
    }

    private void doUpdateFlowRate() {
        if (selectedQueue == null) {
            UiMessages.error("Select a queue from the grid.");
            return;
        }
        Integer flowRate = newFlowRateField.getValue();
        if (flowRate == null || flowRate < 1) {
            UiMessages.error("Enter a positive flow rate.");
            return;
        }
        ActionResult result = presenter.updateQueueFlowRate(selectedQueue.eventId(), selectedQueue.threshold(), flowRate);
        queueStatus.setText(result.message());
        notify(result);
        if (result.success()) {
            refreshQueues();
        }
    }

    private void doFlushQueue() {
        if (selectedQueue == null) {
            UiMessages.error("Select a queue from the grid.");
            return;
        }
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Clear queue?");
        Paragraph text = new Paragraph("All WAITING entries for \"" + selectedQueue.eventName()
                + "\" will be removed. This cannot be undone.");
        Button cancel = new Button("Cancel", e -> confirm.close());
        Button clear = new Button("Yes, clear queue", e -> {
            confirm.close();
            ActionResult result = presenter.flushEventQueue(selectedQueue.eventId());
            queueStatus.setText(result.message());
            notify(result);
            if (result.success()) {
                queuesGrid.asSingleSelect().clear();
                refreshQueues();
            }
        });
        clear.getStyle().set("color", "var(--lumo-error-color)");
        confirm.add(text, new HorizontalLayout(cancel, clear));
        confirm.open();
    }

    private void doCreateQueue() {
        String rawId = createQueueEventIdField.getValue();
        UUID eventId;
        try {
            eventId = UUID.fromString(rawId == null ? "" : rawId.trim());
        } catch (IllegalArgumentException ex) {
            UiMessages.error("Enter a valid event UUID.");
            return;
        }
        Integer threshold = createQueueThresholdField.getValue();
        Integer flowRate = createQueueFlowRateField.getValue();
        if (threshold == null || threshold < 1 || flowRate == null || flowRate < 1) {
            UiMessages.error("Threshold and flow rate must be positive integers.");
            return;
        }
        ActionResult result = presenter.createEventQueue(eventId, threshold, flowRate);
        queueStatus.setText(result.message());
        notify(result);
        if (result.success()) {
            createQueueEventIdField.clear();
            refreshQueues();
        }
    }

    private void refreshQueueControlsState() {
        boolean hasSelection = selectedQueue != null;
        if (updateFlowRateButton != null) {
            updateFlowRateButton.setEnabled(hasSelection);
        }
        if (flushQueueButton != null) {
            flushQueueButton.setEnabled(hasSelection);
        }
    }

    private void startQueuePolling() {
        getUI().ifPresent(ui -> {
            ui.setPollInterval(5_000);
            if (queuePollRegistration == null) {
                queuePollRegistration = ui.addPollListener(e -> refreshQueues());
            }
        });
    }

    private void stopQueuePolling() {
        if (queuePollRegistration != null) {
            queuePollRegistration.remove();
            queuePollRegistration = null;
        }
        getUI().ifPresent(ui -> ui.setPollInterval(-1));
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
        panelByMode.put(AdminMode.QUEUES, queueSection());
        panelByMode.values().forEach(adminModeContent::add);
    }

    private void configureAdminTabs() {
        adminTabs.removeAll();
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
        if (mode == AdminMode.QUEUES) {
            panelByMode.get(mode).setVisible(true);
            refreshQueues();
            startQueuePolling();
        } else {
            stopQueuePolling();
            if (mode != null) {
                panelByMode.get(mode).setVisible(true);
            }
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

    private void removeMember() {
        MemberSummaryDTO target = requireSelected(removeMemberPicker, memberStatus, "Select a target member.");
        if (target == null) {
            return;
        }
        ActionResult result = presenter.removeMember(target.id());
        handleMemberResult(result);
        if (result.success()) {
            removeMemberPicker.clear();
            loadMemberPickerItems();
        }
    }

    private void closeCompany() {
        CompanySummaryDTO company = requireSelected(closeCompanyPicker, companyStatus, "Select a company to close.");
        if (company == null) {
            return;
        }
        ActionResult result = presenter.closeCompany(company.name());
        companyStatus.setText(result.message());
        notify(result);
        if (result.success()) {
            closeCompanyPicker.clear();
            loadCompanyPickerItems();
        }
    }

    private void suspendMember() {
        MemberSummaryDTO target = requireSelected(suspensionTargetPicker, suspensionStatus, "Select a suspension target member.");
        if (target == null) {
            return;
        }
        handleSuspensionAction(presenter.suspendUser(
                target.id(),
                suspensionDurationDays.getValue(),
                permanentSuspension.getValue(),
                suspensionReason.getValue()
        ));
    }

    private void cancelSuspension() {
        if (selectedSuspension == null) {
            suspensionStatus.setText("Select a suspension to cancel.");
            UiMessages.error("Select a suspension to cancel.");
            return;
        }
        ActionResult result = presenter.cancelSuspension(selectedSuspension.memberId(), selectedSuspension.suspensionId());
        handleSuspensionAction(result);
        if (result.success()) {
            suspensionsGrid.asSingleSelect().clear();
        }
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
        QUEUES("Queues");

        private final String label;

        AdminMode(String label) {
            this.label = label;
        }
    }
}
