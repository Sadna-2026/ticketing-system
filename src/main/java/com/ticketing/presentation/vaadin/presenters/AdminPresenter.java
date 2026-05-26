package com.ticketing.presentation.vaadin.presenters;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.services.AdminService;
import com.ticketing.domain.member.Suspension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class AdminPresenter {

    private static final Logger logger = LoggerFactory.getLogger(AdminPresenter.class);

    private static final String ADMIN_SESSION_REQUIRED =
            "Start a session with system admin permissions before using admin actions.";
    private static final String ADMIN_ACTION_FAILURE_MESSAGE =
            "Could not complete admin action. Please try again.";
    private static final String ADMIN_HISTORY_FAILURE_MESSAGE =
            "Could not load global purchase history. Please try again.";
    private static final String ADMIN_SUSPENSION_FAILURE_MESSAGE =
            "Could not complete suspension action. Please try again.";
    private static final String POLICY_SUPPORT_MESSAGE =
            "Waiting for backend/application support: #149.";

    private final AdminService adminService;

    public AdminPresenter(AdminService adminService) {
        this.adminService = adminService;
    }

    public ActionResult removeMember(UUID targetMemberId) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }

        try {
            adminService.removeMember(token, targetMemberId);
            return ActionResult.success("Member removed.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_ACTION_FAILURE_MESSAGE));
        }
    }

    public ActionResult suspendUser(UUID targetMemberId, Integer durationDays, boolean permanent, String reason) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }
        if (!permanent && (durationDays == null || durationDays <= 0)) {
            return ActionResult.failure("Suspension duration days must be positive, or choose permanent.");
        }

        Duration duration = permanent ? null : Duration.ofDays(durationDays);
        try {
            Suspension suspension = adminService.suspendUser(token, targetMemberId, duration, blankToNull(reason));
            String message = suspension.isPermanent()
                    ? "Member suspended permanently. Suspension ID: " + suspension.getSuspensionId()
                    : "Member suspended for " + durationDays + " day(s). Suspension ID: " + suspension.getSuspensionId();
            return ActionResult.success(message);
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_SUSPENSION_FAILURE_MESSAGE));
        }
    }

    public ActionResult cancelSuspension(UUID targetMemberId, UUID suspensionId) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }
        if (suspensionId == null) {
            return ActionResult.failure("Suspension ID is required.");
        }

        try {
            adminService.cancelSuspension(token, targetMemberId, suspensionId);
            return ActionResult.success("Suspension cancelled.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_SUSPENSION_FAILURE_MESSAGE));
        }
    }

    public SuspensionListResult listSuspensions(boolean activeOnly) {
        String token = adminToken();
        if (token == null) {
            return SuspensionListResult.failure(ADMIN_SESSION_REQUIRED);
        }

        try {
            List<SuspensionDTO> suspensions = adminService.listSuspensions(token, activeOnly);
            String message = suspensions.isEmpty()
                    ? "No suspensions found."
                    : "Loaded " + suspensions.size() + " suspension(s).";
            return SuspensionListResult.success(message, suspensions);
        } catch (RuntimeException ex) {
            return SuspensionListResult.failure(userMessage(ex, ADMIN_SUSPENSION_FAILURE_MESSAGE));
        }
    }

    public PurchaseHistoryResult loadGlobalPurchaseHistory(UUID buyerId, String companyName) {
        String token = adminToken();
        if (token == null) {
            return PurchaseHistoryResult.failure(ADMIN_SESSION_REQUIRED);
        }

        try {
            List<PurchaseRecordDTO> purchases = adminService.getGlobalPurchaseHistory(token, buyerId, blankToNull(companyName));
            String message = purchases.isEmpty()
                    ? "No global purchases found."
                    : "Loaded " + purchases.size() + " purchase(s).";
            return PurchaseHistoryResult.success(message, purchases);
        } catch (RuntimeException ex) {
            return PurchaseHistoryResult.failure(userMessage(ex, ADMIN_HISTORY_FAILURE_MESSAGE));
        }
    }

    public ActionResult policySupportStatus() {
        return ActionResult.info(POLICY_SUPPORT_MESSAGE);
    }

    public String currentSessionLabel() {
        if (SessionContext.isLoggedInMember()) {
            String username = SessionContext.getUsername();
            if (username != null && !username.isBlank()) {
                return "Current session: Member (" + username + ")";
            }
            return "Current session: Member";
        }

        if (SessionContext.hasSessionToken()) {
            return "Current session: Guest";
        }

        return "Current session: none";
    }

    private static String adminToken() {
        String token = SessionContext.getSessionToken();
        return token == null || token.isBlank() ? null : token;
    }

    private String userMessage(RuntimeException ex, String fallback) {
        if (ex instanceof IllegalArgumentException
                || ex instanceof IllegalStateException
                || ex instanceof SecurityException) {
            String message = ex.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }

        logger.warn(fallback, ex);
        return fallback;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum Feedback {
        SUCCESS,
        ERROR,
        INFO
    }

    public record ActionResult(boolean success, String message, Feedback feedback) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message, Feedback.SUCCESS);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, Feedback.ERROR);
        }

        public static ActionResult info(String message) {
            return new ActionResult(true, message, Feedback.INFO);
        }
    }

    public record PurchaseHistoryResult(boolean success, String message, List<PurchaseRecordDTO> purchases) {
        public PurchaseHistoryResult {
            purchases = purchases == null ? List.of() : List.copyOf(purchases);
        }

        public static PurchaseHistoryResult success(String message, List<PurchaseRecordDTO> purchases) {
            return new PurchaseHistoryResult(true, message, purchases);
        }

        public static PurchaseHistoryResult failure(String message) {
            return new PurchaseHistoryResult(false, message, List.of());
        }
    }

    public record SuspensionListResult(boolean success, String message, List<SuspensionDTO> suspensions) {
        public SuspensionListResult {
            suspensions = suspensions == null ? List.of() : List.copyOf(suspensions);
        }

        public static SuspensionListResult success(String message, List<SuspensionDTO> suspensions) {
            return new SuspensionListResult(true, message, suspensions);
        }

        public static SuspensionListResult failure(String message) {
            return new SuspensionListResult(false, message, List.of());
        }
    }
}
