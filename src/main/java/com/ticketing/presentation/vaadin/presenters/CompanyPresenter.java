package com.ticketing.presentation.vaadin.presenters;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.EditEventRequest;
import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventDetailsDTO;
import com.ticketing.application.dto.EventMapDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.CompletedPurchaseService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IDiscountPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class CompanyPresenter {

    private static final Logger logger = LoggerFactory.getLogger(CompanyPresenter.class);

    private static final String MEMBER_SESSION_REQUIRED = "Log in as a member before using company owner or manager actions.";
    private static final String COMPANY_FAILURE_MESSAGE = "Could not complete company action. Please try again.";
    private static final String COMPANY_INFO_FAILURE_MESSAGE = "Could not load company information. Please try again.";
    private static final String PERSONNEL_FAILURE_MESSAGE = "Could not complete personnel action. Please try again.";
    private static final String EVENT_FAILURE_MESSAGE = "Could not complete event action. Please try again.";
    private static final String INVENTORY_FAILURE_MESSAGE = "Could not complete inventory action. Please try again.";
    private static final String LIFECYCLE_FAILURE_MESSAGE = "Could not complete lifecycle action. Please try again.";
    private static final String REPORTING_FAILURE_MESSAGE = "Could not load company reporting data. Please try again.";
    private static final String POLICY_FAILURE_MESSAGE = "Could not complete policy action. Please try again.";

    private final CompanyService companyService;
    private final MemberService memberService;
    private final EventService eventService;
    private final CompletedPurchaseService completedPurchaseService;

    public CompanyPresenter(
            CompanyService companyService,
            MemberService memberService,
            EventService eventService,
            CompletedPurchaseService completedPurchaseService
    ) {
        this.companyService = companyService;
        this.memberService = memberService;
        this.eventService = eventService;
        this.completedPurchaseService = completedPurchaseService;
    }

    public ActionResult openCompany(String name, String description) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String normalizedName = blankToNull(name);
        if (normalizedName == null) {
            return ActionResult.failure("Company name is required.");
        }

        try {
            String companyName = companyService.openProductionCompany(token, normalizedName, blankToNull(description));
            return ActionResult.success("Company opened: " + companyName);
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, COMPANY_FAILURE_MESSAGE));
        }
    }

    public CompanyInfoResult loadCompanyInfo(String companyName) {
        try {
            return companyService.getCompanyInfo(companyName)
                    .map(company -> CompanyInfoResult.success("Company information loaded.", company))
                    .orElseGet(() -> CompanyInfoResult.failure("Company not found."));
        } catch (RuntimeException ex) {
            logger.warn(COMPANY_INFO_FAILURE_MESSAGE, ex);
            return CompanyInfoResult.failure(COMPANY_INFO_FAILURE_MESSAGE);
        }
    }

    /** Active companies for company pickers; empty on failure so the view never crashes. */
    public List<CompanySummaryDTO> searchCompanies(String query) {
        try {
            return companyService.searchCompanies(query);
        } catch (RuntimeException ex) {
            logger.warn("Company search failed", ex);
            return List.of();
        }
    }

    /** All events of a company (any status) for management pickers; empty if no member session. */
    public List<EventSummaryDTO> listCompanyEvents(String companyName) {
        String token = memberToken();
        if (token == null || companyName == null || companyName.isBlank()) {
            return List.of();
        }
        try {
            return eventService.listCompanyEvents(token, companyName);
        } catch (RuntimeException ex) {
            logger.warn("Company event listing failed", ex);
            return List.of();
        }
    }

    /** Browsable (published/sold-out) events for the public event-map lookup picker. */
    public List<EventSummaryDTO> searchBrowsableEvents() {
        try {
            return eventService.searchEvents(SearchEventsRequest.empty());
        } catch (RuntimeException ex) {
            logger.warn("Browsable event search failed", ex);
            return List.of();
        }
    }

    public OrgChartResult loadOrganizationChart(String companyName) {
        String token = memberToken();
        if (token == null) {
            return OrgChartResult.failure(MEMBER_SESSION_REQUIRED);
        }

        try {
            List<OrgNodeDTO> nodes = memberService.getOrganizationChart(token, companyName);
            String message = nodes.isEmpty() ? "No personnel found." : "Organization chart loaded.";
            return OrgChartResult.success(message, nodes);
        } catch (RuntimeException ex) {
            return OrgChartResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public ActionResult offerRoleAppointment(
            String companyName,
            UUID targetMemberId,
            StaffAppointment.StaffRole role,
            Set<ManagerPermission> permissions
    ) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }
        if (role == null) {
            return ActionResult.failure("Role is required.");
        }

        try {
            companyService.offerRoleAppointment(token, companyName, targetMemberId, role, safePermissions(role, permissions));
            return ActionResult.success("Role appointment offer sent.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public ActionResult respondToRoleOffer(UUID offerId, boolean accepted) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (offerId == null) {
            return ActionResult.failure("Offer ID is required.");
        }

        try {
            companyService.respondToRoleAppointment(token, offerId, accepted);
            return ActionResult.success(accepted ? "Role offer accepted." : "Role offer rejected.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public ActionResult revokePersonnel(String companyName, UUID targetMemberId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }

        try {
            companyService.revokePersonnel(token, companyName, targetMemberId);
            return ActionResult.success("Personnel revoked.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public ActionResult changeManagerPermissions(
            String companyName,
            UUID targetMemberId,
            Set<ManagerPermission> permissions
    ) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (targetMemberId == null) {
            return ActionResult.failure("Target member ID is required.");
        }

        try {
            companyService.changeManagerPermissions(token, companyName, targetMemberId, permissions == null ? Set.of() : permissions);
            return ActionResult.success("Manager permissions updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public PersonnelAccessResult loadPersonnelAccess(String companyName) {
        String token = memberToken();
        if (token == null) {
            return PersonnelAccessResult.denied(MEMBER_SESSION_REQUIRED);
        }
        String normalizedName = blankToNull(companyName);
        if (normalizedName == null) {
            return PersonnelAccessResult.denied("Select a company to manage personnel.");
        }
        UUID memberId = SessionContext.getMemberId();
        if (memberId == null) {
            return PersonnelAccessResult.denied(MEMBER_SESSION_REQUIRED);
        }

        try {
            List<OrgNodeDTO> nodes = memberService.getOrganizationChart(token, normalizedName);
            OrgNodeDTO currentMember = findNode(nodes, memberId);
            if (currentMember != null && currentMember.role() == StaffAppointment.StaffRole.OWNER && !currentMember.revoked()) {
                return PersonnelAccessResult.allowed("Owner permissions available for " + normalizedName + ".");
            }
            return PersonnelAccessResult.denied("Only a company owner can change manager permissions for " + normalizedName + ".");
        } catch (RuntimeException ex) {
            return PersonnelAccessResult.denied(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    public ActionResult relinquishOwnership(String companyName) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String normalizedName = blankToNull(companyName);
        if (normalizedName == null) {
            return ActionResult.failure("Company name is required.");
        }

        try {
            companyService.relinquishOwnership(token, normalizedName);
            return ActionResult.success("Ownership relinquished for " + normalizedName + ".");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, PERSONNEL_FAILURE_MESSAGE));
        }
    }

    // ── Purchase Policy ─────────────────────────────────────────────

    public PolicyViewResult loadCompanyPurchasePolicy(String companyName) {
        String token = memberToken();
        if (token == null) {
            return PolicyViewResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return PolicyViewResult.failure("Company name is required.");
        }
        try {
            IPurchasePolicy policy = companyService.getCompanyPurchasePolicy(token, name);
            String desc = describePurchasePolicy(policy);
            return PolicyViewResult.success("Company purchase policy loaded.", desc);
        } catch (RuntimeException ex) {
            return PolicyViewResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult setCompanyPurchasePolicy(String companyName, IPurchasePolicy policy) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return ActionResult.failure("Company name is required.");
        }
        if (policy == null) {
            return ActionResult.failure("Policy definition is required.");
        }
        try {
            companyService.setCompanyPurchasePolicy(token, name, policy);
            return ActionResult.success("Company purchase policy updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult removeCompanyPurchasePolicy(String companyName) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return ActionResult.failure("Company name is required.");
        }
        try {
            companyService.removeCompanyPurchasePolicy(token, name);
            return ActionResult.success("Company purchase policy reset to default (allow all).");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public PolicyViewResult loadEventPurchasePolicy(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return PolicyViewResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return PolicyViewResult.failure("Event ID is required.");
        }
        try {
            IPurchasePolicy policy = eventService.getEventPurchasePolicy(token, eventId);
            String desc = describePurchasePolicy(policy);
            return PolicyViewResult.success("Event purchase policy loaded.", desc);
        } catch (RuntimeException ex) {
            return PolicyViewResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult setEventPurchasePolicy(UUID eventId, IPurchasePolicy policy) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        if (policy == null) {
            return ActionResult.failure("Policy definition is required.");
        }
        try {
            eventService.setEventPurchasePolicy(token, eventId, policy);
            return ActionResult.success("Event purchase policy updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult removeEventPurchasePolicy(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        try {
            eventService.removeEventPurchasePolicy(token, eventId);
            return ActionResult.success("Event purchase policy reset to default (allow all).");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    // ── Discount Policy ─────────────────────────────────────────────

    public PolicyViewResult loadCompanyDiscountPolicy(String companyName) {
        String token = memberToken();
        if (token == null) {
            return PolicyViewResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return PolicyViewResult.failure("Company name is required.");
        }
        try {
            IDiscountPolicy policy = companyService.getCompanyDiscountPolicy(token, name);
            String desc = describeDiscountPolicy(policy);
            return PolicyViewResult.success("Company discount policy loaded.", desc);
        } catch (RuntimeException ex) {
            return PolicyViewResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult setCompanyDiscountPolicy(String companyName, IDiscountPolicy policy) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return ActionResult.failure("Company name is required.");
        }
        if (policy == null) {
            return ActionResult.failure("Discount policy definition is required.");
        }
        try {
            companyService.setCompanyDiscountPolicy(token, name, policy);
            return ActionResult.success("Company discount policy updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult removeCompanyDiscountPolicy(String companyName) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        String name = blankToNull(companyName);
        if (name == null) {
            return ActionResult.failure("Company name is required.");
        }
        try {
            companyService.removeCompanyDiscountPolicy(token, name);
            return ActionResult.success("Company discount policy reset to default (no discount).");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public PolicyViewResult loadEventDiscountPolicy(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return PolicyViewResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return PolicyViewResult.failure("Event ID is required.");
        }
        try {
            IDiscountPolicy policy = eventService.getEventDiscountPolicy(token, eventId);
            String desc = describeDiscountPolicy(policy);
            return PolicyViewResult.success("Event discount policy loaded.", desc);
        } catch (RuntimeException ex) {
            return PolicyViewResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult setEventDiscountPolicy(UUID eventId, IDiscountPolicy policy) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        if (policy == null) {
            return ActionResult.failure("Discount policy definition is required.");
        }
        try {
            eventService.setEventDiscountPolicy(token, eventId, policy);
            return ActionResult.success("Event discount policy updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public ActionResult removeEventDiscountPolicy(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        try {
            eventService.removeEventDiscountPolicy(token, eventId);
            return ActionResult.success("Event discount policy reset to default (no discount).");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, POLICY_FAILURE_MESSAGE));
        }
    }

    public EventActionResult createEvent(
            String companyName,
            String name,
            String description,
            EventCategory category,
            Instant startTime,
            Instant endTime,
            Instant doorsOpenTime,
            Integer lockMinutes,
            String zoneName,
            BigDecimal zonePrice,
            Integer gaCapacity,
            String sectionName
    ) {
        String token = memberToken();
        if (token == null) {
            return EventActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (category == null) {
            return EventActionResult.failure("Event category is required.");
        }
        if (lockMinutes == null || lockMinutes <= 0) {
            return EventActionResult.failure("Lock minutes must be positive.");
        }
        if (gaCapacity == null || gaCapacity <= 0) {
            return EventActionResult.failure("GA capacity must be positive.");
        }
        String normalizedCompanyName = blankToNull(companyName);
        if (normalizedCompanyName == null) {
            return EventActionResult.failure("Company name is required.");
        }
        String normalizedEventName = blankToNull(name);
        if (normalizedEventName == null) {
            return EventActionResult.failure("Event name is required.");
        }
        String normalizedZoneName = blankToNull(zoneName);
        if (normalizedZoneName == null) {
            return EventActionResult.failure("Zone name is required.");
        }
        String normalizedSectionName = blankToNull(sectionName);
        if (normalizedSectionName == null) {
            return EventActionResult.failure("Venue section is required.");
        }

        try {
            CreateEventRequest request = new CreateEventRequest(
                    normalizedCompanyName,
                    normalizedEventName,
                    blankToNull(description),
                    category,
                    new EventSchedule(startTime, endTime, doorsOpenTime),
                    new LockTimerDuration(Duration.ofMinutes(lockMinutes)),
                    List.of(new CreateEventRequest.GAZoneSpec(normalizedZoneName, zonePrice, gaCapacity)),
                    Map.of(normalizedSectionName, normalizedZoneName)
            );
            UUID eventId = eventService.createEvent(token, request);
            return EventActionResult.created("Event created.", eventId);
        } catch (RuntimeException ex) {
            return EventActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    // ── Visual hall designer (FIX-V2-25) ────────────────────────────

    /** Creates a DRAFT event from zones the visual designer derived from painted cells. */
    public EventActionResult createDesignedEvent(
            String companyName, String name, String description, EventCategory category,
            Instant startTime, Instant endTime, Instant doorsOpenTime, Integer lockMinutes,
            List<CreateEventRequest.ZoneSpec> zones, Map<String, String> sectionToZoneName) {
        String token = memberToken();
        if (token == null) {
            return EventActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (category == null) {
            return EventActionResult.failure("Event category is required.");
        }
        if (lockMinutes == null || lockMinutes <= 0) {
            return EventActionResult.failure("Lock minutes must be positive.");
        }
        if (zones == null || zones.isEmpty()) {
            return EventActionResult.failure("Design at least one seat or general-admission area.");
        }
        String normalizedCompanyName = blankToNull(companyName);
        if (normalizedCompanyName == null) {
            return EventActionResult.failure("Company name is required.");
        }
        String normalizedEventName = blankToNull(name);
        if (normalizedEventName == null) {
            return EventActionResult.failure("Event name is required.");
        }
        try {
            CreateEventRequest request = new CreateEventRequest(
                    normalizedCompanyName, normalizedEventName, blankToNull(description), category,
                    new EventSchedule(startTime, endTime, doorsOpenTime),
                    new LockTimerDuration(Duration.ofMinutes(lockMinutes)),
                    zones, sectionToZoneName);
            UUID eventId = eventService.createEvent(token, request);
            return EventActionResult.created("Event created as draft.", eventId);
        } catch (RuntimeException ex) {
            return EventActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    /** Saves the visual grid layout on a DRAFT event ("save draft"). */
    public ActionResult setEventLayout(UUID eventId, VenueLayout layout) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        if (layout == null) {
            return ActionResult.failure("Layout is required.");
        }
        try {
            eventService.setEventLayout(token, eventId, layout);
            return ActionResult.success("Hall layout saved.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    /** Validates the event's saved layout before publishing. */
    public ActionResult validateEventLayout(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }
        try {
            eventService.validateEventLayout(token, eventId);
            return ActionResult.success("Layout is valid.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    public EventActionResult editEvent(
            UUID eventId,
            String name,
            String description,
            String artist,
            Instant startTime,
            Instant endTime,
            Instant doorsOpenTime
    ) {
        String token = memberToken();
        if (token == null) {
            return EventActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return EventActionResult.failure("Event ID is required.");
        }

        try {
            EventSchedule schedule = startTime == null && endTime == null && doorsOpenTime == null
                    ? null
                    : new EventSchedule(startTime, endTime, doorsOpenTime);
            EventDetailsDTO details = eventService.editEvent(token, new EditEventRequest(
                    eventId,
                    blankToNull(name),
                    blankToNull(description),
                    blankToNull(artist),
                    schedule
            ));
            return EventActionResult.edited("Event details updated.", details);
        } catch (RuntimeException ex) {
            return EventActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    public ActionResult cancelEvent(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }

        try {
            eventService.cancelEvent(token, eventId);
            return ActionResult.success("Event cancelled.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    public ActionResult publishEvent(UUID eventId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null) {
            return ActionResult.failure("Event ID is required.");
        }

        try {
            eventService.publishEvent(token, eventId);
            return ActionResult.success("Event published.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, EVENT_FAILURE_MESSAGE));
        }
    }

    public EventMapResult loadEventMap(UUID eventId) {
        if (eventId == null) {
            return EventMapResult.failure("Event ID is required.");
        }

        try {
            return eventService.getEventMap(eventId)
                    .map(eventMap -> EventMapResult.success("Event map loaded.", eventMap))
                    .orElseGet(() -> EventMapResult.failure("Event map not found."));
        } catch (RuntimeException ex) {
            logger.warn(INVENTORY_FAILURE_MESSAGE, ex);
            return EventMapResult.failure(INVENTORY_FAILURE_MESSAGE);
        }
    }

    public ActionResult addSeat(UUID eventId, UUID zoneId, String row, String seatNumber) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null || zoneId == null) {
            return ActionResult.failure("Event ID and zone ID are required.");
        }
        String normalizedRow = blankToNull(row);
        if (normalizedRow == null) {
            return ActionResult.failure("Seat row is required.");
        }
        String normalizedSeatNumber = blankToNull(seatNumber);
        if (normalizedSeatNumber == null) {
            return ActionResult.failure("Seat number is required.");
        }

        try {
            eventService.addSeatsToZone(token, eventId, zoneId, List.of(new CreateEventRequest.SeatSpec(normalizedRow, normalizedSeatNumber)));
            return ActionResult.success("Seat added.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, INVENTORY_FAILURE_MESSAGE));
        }
    }

    public ActionResult removeSeat(UUID eventId, UUID zoneId, UUID seatId) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null || zoneId == null || seatId == null) {
            return ActionResult.failure("Event ID, zone ID, and seat ID are required.");
        }

        try {
            eventService.removeSeats(token, eventId, zoneId, List.of(seatId));
            return ActionResult.success("Seat removed.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, INVENTORY_FAILURE_MESSAGE));
        }
    }

    public ActionResult increaseGACapacity(UUID eventId, UUID zoneId, Integer delta) {
        return changeGACapacity(eventId, zoneId, delta, true);
    }

    public ActionResult decreaseGACapacity(UUID eventId, UUID zoneId, Integer delta) {
        return changeGACapacity(eventId, zoneId, delta, false);
    }

    public ActionResult setZonePrice(UUID eventId, UUID zoneId, BigDecimal price) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null || zoneId == null) {
            return ActionResult.failure("Event ID and zone ID are required.");
        }
        if (price == null) {
            return ActionResult.failure("Zone price is required.");
        }

        try {
            eventService.setZonePrice(token, eventId, zoneId, price);
            return ActionResult.success("Zone price updated.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, INVENTORY_FAILURE_MESSAGE));
        }
    }

    public ActionResult suspendCompany(String companyName) {
        return lifecycle(companyName, "Company suspended.", (token, name) -> companyService.suspendCompany(token, name));
    }

    public ActionResult reopenCompany(String companyName) {
        return lifecycle(companyName, "Company reopened.", (token, name) -> companyService.reopenCompany(token, name));
    }

    public ActionResult closeCompany(String companyName) {
        return lifecycle(companyName, "Company closed.", (token, name) -> companyService.permanentCloseByFounder(token, name));
    }

    public PurchaseHistoryResult loadPurchaseHistory(String companyName) {
        String token = memberToken();
        if (token == null) {
            return PurchaseHistoryResult.failure(MEMBER_SESSION_REQUIRED);
        }

        try {
            List<PurchaseRecordDTO> purchases = companyService.getPurchaseHistory(token, companyName);
            String message = purchases.isEmpty() ? "No company purchases found." : "Loaded " + purchases.size() + " purchase(s).";
            return PurchaseHistoryResult.success(message, purchases);
        } catch (RuntimeException ex) {
            return PurchaseHistoryResult.failure(userMessage(ex, REPORTING_FAILURE_MESSAGE));
        }
    }

    public SalesReportResult loadSalesReport(String companyName) {
        String token = memberToken();
        if (token == null) {
            return SalesReportResult.failure(MEMBER_SESSION_REQUIRED);
        }

        try {
            SalesReportDTO report = completedPurchaseService.getHierarchicalSalesReport(token, companyName);
            return SalesReportResult.success("Sales report loaded.", report);
        } catch (RuntimeException ex) {
            return SalesReportResult.failure(userMessage(ex, REPORTING_FAILURE_MESSAGE));
        }
    }

    public String currentSessionLabel() {
        return SessionContext.currentSessionLabel();
    }

    public SessionContext.UiState currentSessionState() {
        return SessionContext.currentUiState();
    }

    private ActionResult changeGACapacity(UUID eventId, UUID zoneId, Integer delta, boolean increase) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }
        if (eventId == null || zoneId == null) {
            return ActionResult.failure("Event ID and zone ID are required.");
        }
        if (delta == null || delta <= 0) {
            return ActionResult.failure("Capacity delta must be positive.");
        }

        try {
            if (increase) {
                eventService.increaseGACapacity(token, eventId, zoneId, delta);
                return ActionResult.success("GA capacity increased.");
            }
            eventService.decreaseGACapacity(token, eventId, zoneId, delta);
            return ActionResult.success("GA capacity decreased.");
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, INVENTORY_FAILURE_MESSAGE));
        }
    }

    private ActionResult lifecycle(String companyName, String successMessage, LifecycleCall call) {
        String token = memberToken();
        if (token == null) {
            return ActionResult.failure(MEMBER_SESSION_REQUIRED);
        }

        try {
            call.run(token, companyName);
            return ActionResult.success(successMessage);
        } catch (RuntimeException ex) {
            return ActionResult.failure(userMessage(ex, LIFECYCLE_FAILURE_MESSAGE));
        }
    }

    private static Set<ManagerPermission> safePermissions(
            StaffAppointment.StaffRole role,
            Set<ManagerPermission> permissions
    ) {
        if (role == StaffAppointment.StaffRole.OWNER || permissions == null) {
            return Set.of();
        }
        return permissions;
    }

    private static String memberToken() {
        String token = SessionContext.getSessionToken();
        if (token == null || token.isBlank() || !SessionContext.isLoggedInMember()) {
            return null;
        }
        return token;
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

    private static OrgNodeDTO findNode(List<OrgNodeDTO> nodes, UUID memberId) {
        if (nodes == null || memberId == null) {
            return null;
        }
        for (OrgNodeDTO node : nodes) {
            if (memberId.equals(node.memberId())) {
                return node;
            }
            OrgNodeDTO child = findNode(node.subordinates(), memberId);
            if (child != null) {
                return child;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface LifecycleCall {
        void run(String token, String companyName);
    }

    public record ActionResult(boolean success, String message) {
        public static ActionResult success(String message) {
            return new ActionResult(true, message);
        }

        public static ActionResult failure(String message) {
            return new ActionResult(false, message);
        }
    }

    public record PersonnelAccessResult(boolean canChangeManagerPermissions, String message) {
        public static PersonnelAccessResult allowed(String message) {
            return new PersonnelAccessResult(true, message);
        }

        public static PersonnelAccessResult denied(String message) {
            return new PersonnelAccessResult(false, message);
        }
    }

    public record CompanyInfoResult(boolean success, String message, CompanyPublicDTO company) {
        public static CompanyInfoResult success(String message, CompanyPublicDTO company) {
            return new CompanyInfoResult(true, message, company);
        }

        public static CompanyInfoResult failure(String message) {
            return new CompanyInfoResult(false, message, null);
        }
    }

    public record OrgChartResult(boolean success, String message, List<OrgNodeDTO> roots) {
        public OrgChartResult {
            roots = roots == null ? List.of() : List.copyOf(roots);
        }

        public static OrgChartResult success(String message, List<OrgNodeDTO> roots) {
            return new OrgChartResult(true, message, roots);
        }

        public static OrgChartResult failure(String message) {
            return new OrgChartResult(false, message, List.of());
        }
    }

    public record EventActionResult(boolean success, String message, UUID eventId, EventDetailsDTO eventDetails) {
        public static EventActionResult created(String message, UUID eventId) {
            return new EventActionResult(true, message, eventId, null);
        }

        public static EventActionResult edited(String message, EventDetailsDTO details) {
            return new EventActionResult(true, message, details == null ? null : details.id(), details);
        }

        public static EventActionResult failure(String message) {
            return new EventActionResult(false, message, null, null);
        }
    }

    public record EventMapResult(boolean success, String message, EventMapDTO eventMap) {
        public static EventMapResult success(String message, EventMapDTO eventMap) {
            return new EventMapResult(true, message, eventMap);
        }

        public static EventMapResult failure(String message) {
            return new EventMapResult(false, message, null);
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

    public record SalesReportResult(boolean success, String message, SalesReportDTO report) {
        public static SalesReportResult success(String message, SalesReportDTO report) {
            return new SalesReportResult(true, message, report);
        }

        public static SalesReportResult failure(String message) {
            return new SalesReportResult(false, message, null);
        }
    }

    public record PolicyViewResult(boolean success, String message, String description) {
        public static PolicyViewResult success(String message, String description) {
            return new PolicyViewResult(true, message, description);
        }

        public static PolicyViewResult failure(String message) {
            return new PolicyViewResult(false, message, "");
        }
    }

    static String describePurchasePolicy(IPurchasePolicy policy) {
        if (policy == null) return "None (allow all)";
        if (policy instanceof com.ticketing.domain.event.AlwaysAllowPolicy) return "Allow all (no restrictions)";
        if (policy instanceof com.ticketing.domain.event.AgeRestrictionPolicy p)
            return "Age restriction: minimum " + p.getMinimumAge() + " years";
        if (policy instanceof com.ticketing.domain.event.MaxQuantityPolicy p)
            return "Max quantity: at most " + p.getMaxTickets() + " tickets";
        if (policy instanceof com.ticketing.domain.event.MinQuantityPolicy p)
            return "Min quantity: at least " + p.getMinTickets() + " tickets";
        if (policy instanceof com.ticketing.domain.event.AndPolicy p) {
            StringBuilder sb = new StringBuilder("AND(");
            for (int i = 0; i < p.getPolicies().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describePurchasePolicy(p.getPolicies().get(i)));
            }
            return sb.append(")").toString();
        }
        if (policy instanceof com.ticketing.domain.event.OrPolicy p) {
            StringBuilder sb = new StringBuilder("OR(");
            for (int i = 0; i < p.getPolicies().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describePurchasePolicy(p.getPolicies().get(i)));
            }
            return sb.append(")").toString();
        }
        return policy.getClass().getSimpleName();
    }

    static String describeDiscountPolicy(IDiscountPolicy policy) {
        if (policy == null) return "None (no discount)";
        if (policy instanceof com.ticketing.domain.event.NoDiscountPolicy) return "No discount";
        if (policy instanceof com.ticketing.domain.event.SimpleDiscount p)
            return "Simple discount: " + p.getPercentOff() + "% off";
        if (policy instanceof com.ticketing.domain.event.CouponDiscount p)
            return "Coupon discount: " + p.getPercentOff() + "% off with code '" + p.getCouponCode() + "' (expires " + p.getExpiresAt() + ")";
        if (policy instanceof com.ticketing.domain.event.ConditionalDiscount p)
            return "Conditional discount: " + p.getPercentOff() + "% off when " + describeCondition(p.getCondition());
        if (policy instanceof com.ticketing.domain.event.MaxCompositeDiscount p) {
            StringBuilder sb = new StringBuilder("MAX(");
            for (int i = 0; i < p.getPolicies().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describeDiscountPolicy(p.getPolicies().get(i)));
            }
            return sb.append(")").toString();
        }
        if (policy instanceof com.ticketing.domain.event.SumCompositeDiscount p) {
            StringBuilder sb = new StringBuilder("SUM(");
            for (int i = 0; i < p.getPolicies().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(describeDiscountPolicy(p.getPolicies().get(i)));
            }
            return sb.append(")").toString();
        }
        return policy.getClass().getSimpleName();
    }

    private static String describeCondition(com.ticketing.domain.event.IDiscountCondition condition) {
        if (condition instanceof com.ticketing.domain.event.MinQuantityCondition c)
            return "min " + c.getMinTickets() + " tickets";
        if (condition instanceof com.ticketing.domain.event.MaxQuantityCondition c)
            return "max " + c.getMaxTickets() + " tickets";
        if (condition instanceof com.ticketing.domain.event.DateRangeCondition c) {
            String from = c.getFrom() == null ? "unbounded" : c.getFrom().toString();
            String to = c.getTo() == null ? "unbounded" : c.getTo().toString();
            return "date in [" + from + " .. " + to + "]";
        }
        return condition.getClass().getSimpleName();
    }
}
