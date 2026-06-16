package com.ticketing.presentation.vaadin.presenters;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.MemberSummaryDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.application.dto.VirtualQueueDto;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.member.Suspension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class AdminPresenter {

    private static final Logger logger = LoggerFactory.getLogger(AdminPresenter.class);

    private static final String ADMIN_SESSION_REQUIRED =
            "Start a session with system admin permissions before using admin actions.";
    private static final String ADMIN_ACTION_FAILURE_MESSAGE =
            "Could not complete admin action. Please try again.";
    private static final String ADMIN_COMPANY_FAILURE_MESSAGE =
            "Could not complete company administration action. Please try again.";
    private static final String ADMIN_HISTORY_FAILURE_MESSAGE =
            "Could not load global purchase history. Please try again.";
    private static final String ADMIN_SUSPENSION_FAILURE_MESSAGE =
            "Could not complete suspension action. Please try again.";

    private static final String ADMIN_QUEUE_FAILURE_MESSAGE =
            "Could not complete queue action. Please try again.";

    private final AdminService adminService;
    private final CompanyService companyService;
    private final OrderService orderService;
    private final EventService eventService;

    public AdminPresenter(AdminService adminService, CompanyService companyService,
            OrderService orderService, EventService eventService) {
        this.adminService = adminService;
        this.companyService = companyService;
        this.orderService = orderService;
        this.eventService = eventService;
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

    public ActionResult closeCompany(String companyName) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        String normalizedName = blankToNull(companyName);
        if (normalizedName == null) {
            return ActionResult.failure("Company name is required.");
        }

        try {
            companyService.permanentCloseByAdmin(token, normalizedName);
            return ActionResult.success("Company closed.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_COMPANY_FAILURE_MESSAGE));
        }
    }

    public List<MemberSummaryDTO> searchMembers(String usernameQuery) {
        String token = adminToken();
        if (token == null) {
            return List.of();
        }
        try {
            return adminService.searchMembers(token, usernameQuery);
        } catch (RuntimeException ex) {
            logger.warn("Member search failed", ex);
            return List.of();
        }
    }

    public List<CompanySummaryDTO> searchCompanies(String query) {
        String token = adminToken();
        if (token == null) {
            return List.of();
        }
        try {
            return companyService.searchCompanies(query);
        } catch (RuntimeException ex) {
            logger.warn("Company search failed", ex);
            return List.of();
        }
    }

    // ── Queue management ─────────────────────────────────────────────

    private static final int DORMANT_THRESHOLD = 10_000;

    public QueueListResult loadAllQueues() {
        String token = adminToken();
        if (token == null) {
            return QueueListResult.failure(ADMIN_SESSION_REQUIRED);
        }
        try {
            List<VirtualQueueDto> queues = orderService.getAllQueues(token);
            List<QueueSummary> summaries = queues.stream()
                    .map(q -> {
                        String name = resolveEventName(q.getEventId());
                        String status = q.getThreshold() >= DORMANT_THRESHOLD ? "Dormant" : "Configured";
                        return new QueueSummary(q.getEventId(), name,
                                q.getWaitingCount(), q.getFlowRate(),
                                q.getThreshold(), q.getCurrentActiveUsers(), status);
                    })
                    .toList();
            String message = summaries.isEmpty()
                    ? "No event queues." : "Loaded " + summaries.size() + " event queue(s).";
            return QueueListResult.success(message, summaries);
        } catch (RuntimeException ex) {
            return QueueListResult.failure(userMessage(ex, ADMIN_QUEUE_FAILURE_MESSAGE));
        }
    }

    public ActionResult updateQueueConfig(UUID eventId, int newThreshold, int newFlowRate) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        try {
            orderService.updateQueueConfig(token, eventId, newThreshold, newFlowRate);
            return ActionResult.success("Queue updated — threshold: " + newThreshold + ", flow rate: " + newFlowRate + ".");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_QUEUE_FAILURE_MESSAGE));
        }
    }

    public ActionResult flushEventQueue(UUID eventId) {
        String token = adminToken();
        if (token == null) {
            return ActionResult.failure(ADMIN_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        try {
            orderService.flushQueue(token, eventId);
            return ActionResult.success("Queue cleared.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, ADMIN_QUEUE_FAILURE_MESSAGE));
        }
    }

    private String resolveEventName(UUID eventId) {
        try {
            return eventService.getEventMap(eventId)
                    .map(m -> m.eventName())
                    .orElse(eventId.toString());
        } catch (RuntimeException ex) {
            return eventId.toString();
        }
    }

    // ── Session ──────────────────────────────────────────────────────

    public String currentSessionLabel() {
        return SessionContext.currentSessionLabel();
    }

    public SessionContext.UiState currentSessionState() {
        return SessionContext.currentUiState();
    }

    private static String adminToken() {
        String token = SessionContext.getSessionToken();
        return token == null || token.isBlank() || !SessionContext.isSystemAdmin() ? null : token;
    }

    private String userMessage(RuntimeException ex, String fallback) {
        if (ex instanceof IllegalStateException) {
            String message = cleanStateMessage(ex.getMessage());
            if (message != null) {
                logger.warn("Admin action failed: {}", ex.getMessage());
                return message;
            }
        }

        if (ex instanceof IllegalArgumentException || ex instanceof SecurityException) {
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

    private static String cleanStateMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        int separator = message.indexOf(':');
        if (separator < 0) {
            return message;
        }
        String candidate = message.substring(separator + 1).trim();
        return candidate.isBlank() ? message : candidate;
    }

    public enum Feedback {
        SUCCESS,
        ERROR
    }

    public record ActionResult(boolean success, String message, Feedback feedback) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message, Feedback.SUCCESS);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message, Feedback.ERROR);
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

    public record QueueSummary(UUID eventId, String eventName, int waitingCount,
            int flowRate, int threshold, int activeUsers, String status) {}

    public record QueueListResult(boolean success, String message, List<QueueSummary> queues) {
        public QueueListResult {
            queues = queues == null ? List.of() : List.copyOf(queues);
        }

        public static QueueListResult success(String message, List<QueueSummary> queues) {
            return new QueueListResult(true, message, queues);
        }

        public static QueueListResult failure(String message) {
            return new QueueListResult(false, message, List.of());
        }
    }
}
