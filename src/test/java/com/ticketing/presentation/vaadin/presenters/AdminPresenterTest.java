package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.member.Suspension;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.ActionResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.PurchaseHistoryResult;
import com.ticketing.presentation.vaadin.presenters.AdminPresenter.SuspensionListResult;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.server.VaadinSession;

@DisplayName("AdminPresenter")
class AdminPresenterTest {

    private AdminService adminService;
    private AdminPresenter presenter;

    @BeforeEach
    void setUp() {
        adminService = mock(AdminService.class);
        presenter = new AdminPresenter(adminService);
        installVaadinSession();
    }

    @AfterEach
    void tearDown() {
        VaadinSession.setCurrent(null);
    }

    @Test
    void GivenNoSession_WhenRemovingMember_ThenNoServiceIsCalledAndSessionMessageIsReturned() {
        UUID targetId = UUID.randomUUID();

        ActionResult result = presenter.removeMember(targetId);

        assertFalse(result.success());
        assertEquals("Start a session with system admin permissions before using admin actions.", result.message());
        verifyNoInteractions(adminService);
    }

    @Test
    void GivenGuestSession_WhenRemovingMember_ThenNoServiceIsCalledAndAdminSessionMessageIsReturned() {
        SessionContext.setSessionToken("guest-token");

        ActionResult result = presenter.removeMember(UUID.randomUUID());

        assertFalse(result.success());
        assertEquals("Start a session with system admin permissions before using admin actions.", result.message());
        verifyNoInteractions(adminService);
    }

    @Test
    void GivenRegularMemberSession_WhenRemovingMember_ThenNoServiceIsCalledAndAdminSessionMessageIsReturned() {
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("alice");

        ActionResult result = presenter.removeMember(UUID.randomUUID());

        assertFalse(result.success());
        assertEquals("Start a session with system admin permissions before using admin actions.", result.message());
        verifyNoInteractions(adminService);
    }

    @Test
    void GivenAdminSessionAndTargetMember_WhenRemovingMember_ThenAdminServiceIsCalledDirectly() {
        adminSession();
        UUID targetId = UUID.randomUUID();

        ActionResult result = presenter.removeMember(targetId);

        assertTrue(result.success());
        assertEquals("Member removed.", result.message());
        verify(adminService).removeMember("admin-token", targetId);
    }

    @Test
    void GivenApplicationFailure_WhenLoadingGlobalHistory_ThenApplicationMessageIsReturned() {
        adminSession();
        when(adminService.getGlobalPurchaseHistory("admin-token", null, null))
                .thenThrow(new SecurityException("System admin permission required"));

        PurchaseHistoryResult result = presenter.loadGlobalPurchaseHistory(null, null);

        assertFalse(result.success());
        assertEquals("System admin permission required", result.message());
    }

    @Test
    void GivenTechnicalStateFailure_WhenRemovingMember_ThenInternalPrefixIsNotShown() {
        adminSession();
        UUID targetId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalStateException(
                "SoleAdminProtection: Cannot remove the last system admin"))
                .when(adminService).removeMember("admin-token", targetId);

        ActionResult result = presenter.removeMember(targetId);

        assertFalse(result.success());
        assertEquals("Cannot remove the last system admin", result.message());
    }

    @Test
    void GivenHistoryFilters_WhenLoadingGlobalHistory_ThenAdminServiceReceivesNullableFilters() {
        adminSession();
        UUID buyerId = UUID.randomUUID();
        PurchaseRecordDTO purchase = purchase(buyerId);
        when(adminService.getGlobalPurchaseHistory("admin-token", buyerId, "Acme"))
                .thenReturn(List.of(purchase));

        PurchaseHistoryResult result = presenter.loadGlobalPurchaseHistory(buyerId, " Acme ");

        assertTrue(result.success());
        assertEquals(List.of(purchase), result.purchases());
        assertEquals("Loaded 1 purchase(s).", result.message());
        verify(adminService).getGlobalPurchaseHistory("admin-token", buyerId, "Acme");
    }

    @Test
    void GivenSuspensionInputs_WhenSuspendingCancellingAndListing_ThenAdminServiceIsCalled() {
        adminSession();
        UUID targetId = UUID.randomUUID();
        UUID suspensionId = UUID.randomUUID();
        Suspension suspension = new Suspension(UUID.randomUUID(), Instant.parse("2026-05-26T12:00:00Z"),
                Duration.ofDays(7), "Spam");
        SuspensionDTO dto = suspensionDto(targetId, suspensionId);
        when(adminService.suspendUser("admin-token", targetId, Duration.ofDays(7), "Spam"))
                .thenReturn(suspension);
        when(adminService.listSuspensions("admin-token", true)).thenReturn(List.of(dto));

        ActionResult suspendResult = presenter.suspendUser(targetId, 7, false, " Spam ");
        ActionResult cancelResult = presenter.cancelSuspension(targetId, suspensionId);
        SuspensionListResult listResult = presenter.listSuspensions(true);

        assertTrue(suspendResult.success());
        assertTrue(suspendResult.message().startsWith("Member suspended for 7 day(s)."));
        assertTrue(cancelResult.success());
        assertTrue(listResult.success());
        assertEquals(List.of(dto), listResult.suspensions());
        verify(adminService).suspendUser("admin-token", targetId, Duration.ofDays(7), "Spam");
        verify(adminService).cancelSuspension("admin-token", targetId, suspensionId);
        verify(adminService).listSuspensions("admin-token", true);
    }

    private void adminSession() {
        SessionContext.setSessionToken("admin-token");
        SessionContext.setMemberId(UUID.randomUUID());
        SessionContext.setUsername("root");
        SessionContext.setPermissions(Set.of("SYSTEM_ADMIN"));
    }

    private void installVaadinSession() {
        Map<String, Object> attributes = new HashMap<>();
        VaadinSession session = mock(VaadinSession.class);
        doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(session).setAttribute(anyString(), nullable(Object.class));
        when(session.getAttribute(anyString())).thenAnswer(invocation -> attributes.get(invocation.getArgument(0)));
        VaadinSession.setCurrent(session);
    }

    private static PurchaseRecordDTO purchase(UUID memberId) {
        return new PurchaseRecordDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Spring Concert",
                "Acme",
                memberId,
                "TXN-1",
                new BigDecimal("80.00"),
                Instant.parse("2026-05-26T12:00:00Z")
        );
    }

    private static SuspensionDTO suspensionDto(UUID memberId, UUID suspensionId) {
        return new SuspensionDTO(
                suspensionId,
                memberId,
                "alice",
                UUID.randomUUID(),
                Instant.parse("2026-05-26T12:00:00Z"),
                Duration.ofDays(7),
                Instant.parse("2026-06-02T12:00:00Z"),
                false,
                true,
                false,
                "Spam"
        );
    }
}
