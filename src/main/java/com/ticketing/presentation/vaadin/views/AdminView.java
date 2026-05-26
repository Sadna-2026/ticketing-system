package com.ticketing.presentation.vaadin.views;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.presentation.vaadin.MainLayout;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.Feedback;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SuspensionListResult;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

@Route(value = "admin", layout = MainLayout.class)
@PageTitle("Admin")
public class AdminView extends VerticalLayout {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final AdminPresenter presenter;

    private final Span sessionStatus = new Span();
    private final Paragraph adminOnlyHint = new Paragraph("Log in with system admin permissions to use admin actions.");
    private VerticalLayout memberControls;
    private VerticalLayout purchaseHistoryControls;
    private VerticalLayout suspensionControls;
    private VerticalLayout policyControls;

    private final Span memberStatus = new Span("Remove members using system admin authorization.");
    private final TextField removeTargetMemberId = new TextField("Target member ID");

    private final Span historyStatus = new Span("Load global purchase history by buyer, company, or all purchases.");
    private final TextField historyBuyerId = new TextField("Buyer member ID");
    private final TextField historyCompanyName = new TextField("Company name");
    private final Grid<PurchaseRecordDTO> purchaseHistoryGrid = new Grid<>(PurchaseRecordDTO.class, false);

    private final Span suspensionStatus = new Span("Suspend members and view active or historical suspensions.");
    private final TextField suspensionTargetMemberId = new TextField("Suspension target member ID");
    private final IntegerField suspensionDurationDays = new IntegerField("Duration days");
    private final Checkbox permanentSuspension = new Checkbox("Permanent suspension");
    private final TextArea suspensionReason = new TextArea("Suspension reason");
    private final TextField suspensionId = new TextField("Suspension ID");
    private final Checkbox activeSuspensionsOnly = new Checkbox("Active suspensions only");
    private final Grid<SuspensionDTO> suspensionsGrid = new Grid<>(SuspensionDTO.class, false);

    private final Span policyStatus = new Span("Policy UI placeholders are waiting for backend/application support: #149.");
    private final TextField policyCompanyName = new TextField("Policy company name");
    private final TextField policyEventId = new TextField("Policy event ID");
    private final ComboBox<String> purchasePolicyType = new ComboBox<>("Purchase policy placeholder");
    private final ComboBox<String> discountPolicyType = new ComboBox<>("Discount policy placeholder");

    public AdminView(AdminPresenter presenter) {
        this.presenter = presenter;

        setPadding(true);
        setSpacing(true);
        setMaxWidth("1180px");
        getStyle().set("margin", "0 auto");

        configureFields();
        configurePurchaseHistoryGrid();
        configureSuspensionsGrid();
        add(
                new H2("Admin"),
                new Paragraph("Use system admin actions backed directly by application services."),
                sessionStatus,
                adminOnlyHint,
                memberSection(),
                purchaseHistorySection(),
                suspensionSection(),
                policySection()
        );
        refreshSessionStatus();
    }

    private void configureFields() {
        removeTargetMemberId.setPlaceholder("Member UUID");
        historyBuyerId.setPlaceholder("Optional member UUID");
        historyCompanyName.setPlaceholder("Optional company filter");
        suspensionTargetMemberId.setPlaceholder("Member UUID");
        suspensionDurationDays.setMin(1);
        suspensionDurationDays.setValue(7);
        suspensionReason.setPlaceholder("Reason shown in application error/status flows");
        suspensionId.setPlaceholder("Suspension UUID");
        activeSuspensionsOnly.setValue(true);

        purchasePolicyType.setItems("Age restriction", "Minimum quantity", "Maximum quantity", "AND composition", "OR composition");
        purchasePolicyType.setValue("Maximum quantity");
        discountPolicyType.setItems("Simple discount", "Coupon discount", "Conditional discount", "Maximum composite", "Sum composite");
        discountPolicyType.setValue("Simple discount");
    }

    private void configurePurchaseHistoryGrid() {
        purchaseHistoryGrid.setId("admin-global-purchases-grid");
        purchaseHistoryGrid.addColumn(purchase -> purchase.purchaseId().toString()).setHeader("Purchase ID").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(PurchaseRecordDTO::eventName).setHeader("Event").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(PurchaseRecordDTO::companyName).setHeader("Company").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> valueOrEmpty(purchase.memberId())).setHeader("Buyer").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> formatPrice(purchase.amount())).setHeader("Amount").setAutoWidth(true);
        purchaseHistoryGrid.addColumn(purchase -> formatInstant(purchase.purchasedAt())).setHeader("Purchased at").setAutoWidth(true);
        purchaseHistoryGrid.setMinHeight("180px");
    }

    private void configureSuspensionsGrid() {
        suspensionsGrid.setId("admin-suspensions-grid");
        suspensionsGrid.addColumn(suspension -> suspension.suspensionId().toString()).setHeader("Suspension ID").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::memberUsername).setHeader("Member").setAutoWidth(true);
        suspensionsGrid.addColumn(suspension -> suspension.memberId().toString()).setHeader("Member ID").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::active).setHeader("Active").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::permanent).setHeader("Permanent").setAutoWidth(true);
        suspensionsGrid.addColumn(suspension -> formatDuration(suspension.duration())).setHeader("Duration").setAutoWidth(true);
        suspensionsGrid.addColumn(suspension -> formatInstant(suspension.startTime())).setHeader("Started").setAutoWidth(true);
        suspensionsGrid.addColumn(SuspensionDTO::reason).setHeader("Reason").setAutoWidth(true);
        suspensionsGrid.setMinHeight("180px");
    }

    private VerticalLayout memberSection() {
        Button removeMember = new Button("Remove member", event -> removeMember());

        FormLayout form = new FormLayout(removeTargetMemberId);
        VerticalLayout section = new VerticalLayout(
                new H3("Member administration"),
                form,
                removeMember,
                memberStatus
        );
        section.setPadding(false);
        memberControls = section;
        return section;
    }

    private VerticalLayout purchaseHistorySection() {
        Button loadHistory = new Button("Load global purchase history", event -> loadPurchaseHistory());

        FormLayout form = new FormLayout(historyBuyerId, historyCompanyName);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        VerticalLayout section = new VerticalLayout(
                new H3("Global purchase history"),
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
        Button cancel = new Button("Cancel suspension", event -> cancelSuspension());
        Button load = new Button("Load suspensions", event -> loadSuspensions());

        FormLayout form = new FormLayout(
                suspensionTargetMemberId,
                suspensionDurationDays,
                permanentSuspension,
                suspensionReason,
                suspensionId,
                activeSuspensionsOnly
        );
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 3));
        HorizontalLayout actions = new HorizontalLayout(suspend, cancel, load);
        actions.setAlignItems(Alignment.BASELINE);

        VerticalLayout section = new VerticalLayout(
                new H3("Suspensions"),
                form,
                actions,
                suspensionStatus,
                suspensionsGrid
        );
        section.setPadding(false);
        suspensionControls = section;
        return section;
    }

    private VerticalLayout policySection() {
        Button showSupportStatus = new Button("Check policy backend support", event -> handlePolicyResult(
                presenter.policySupportStatus()));

        FormLayout form = new FormLayout(policyCompanyName, policyEventId, purchasePolicyType, discountPolicyType);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("760px", 2));
        VerticalLayout section = new VerticalLayout(
                new H3("Purchase and discount policies"),
                new Paragraph("Supported domain concepts are visible here, but policy CRUD and attach/edit actions require application support from #149."),
                form,
                showSupportStatus,
                policyStatus
        );
        section.setPadding(false);
        policyControls = section;
        return section;
    }

    private void removeMember() {
        try {
            handleMemberResult(presenter.removeMember(requiredUuid(removeTargetMemberId, "target member")));
        } catch (IllegalArgumentException ex) {
            memberStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
        }
    }

    private void suspendMember() {
        try {
            handleSuspensionAction(presenter.suspendUser(
                    requiredUuid(suspensionTargetMemberId, "target member"),
                    suspensionDurationDays.getValue(),
                    permanentSuspension.getValue(),
                    suspensionReason.getValue()
            ));
        } catch (IllegalArgumentException ex) {
            suspensionStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
        }
    }

    private void cancelSuspension() {
        try {
            handleSuspensionAction(presenter.cancelSuspension(
                    requiredUuid(suspensionTargetMemberId, "target member"),
                    requiredUuid(suspensionId, "suspension")
            ));
        } catch (IllegalArgumentException ex) {
            suspensionStatus.setText(ex.getMessage());
            UiMessages.error(ex.getMessage());
        }
    }

    private void loadPurchaseHistory() {
        PurchaseHistoryResult result;
        try {
            result = presenter.loadGlobalPurchaseHistory(optionalUuid(historyBuyerId, "buyer member"), historyCompanyName.getValue());
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

    private void handlePolicyResult(ActionResult result) {
        policyStatus.setText(result.message());
        notify(result);
    }

    private void notify(ActionResult result) {
        if (result.feedback() == Feedback.INFO) {
            UiMessages.info(result.message());
        } else if (result.success()) {
            UiMessages.success(result.message());
        } else {
            UiMessages.error(result.message());
        }
    }

    private UUID requiredUuid(TextField field, String label) {
        return parseUuid(field, label, false);
    }

    private UUID optionalUuid(TextField field, String label) {
        return parseUuid(field, label, true);
    }

    private UUID parseUuid(TextField field, String label, boolean optional) {
        String value = field.getValue();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Enter a valid " + label + " ID.");
        }
    }

    private void refreshSessionStatus() {
        sessionStatus.setText(presenter.currentSessionLabel());
        boolean admin = presenter.currentSessionState().systemAdmin();
        adminOnlyHint.setVisible(!admin);
        memberControls.setVisible(admin);
        purchaseHistoryControls.setVisible(admin);
        suspensionControls.setVisible(admin);
        policyControls.setVisible(admin);
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
}
