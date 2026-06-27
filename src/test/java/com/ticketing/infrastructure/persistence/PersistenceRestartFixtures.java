package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.StaffAppointment.StaffRole;
import com.ticketing.domain.notification.IPendingNotificationRepository;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;

/**
 * Builds and asserts a non-trivial operational snapshot for persistence / restart tests.
 */
final class PersistenceRestartFixtures {

    static final String COMPANY_NAME = "Persistence QA Co";
    static final String EVENT_NAME = "Persistence QA Concert";
    static final String NOTIFICATION_MESSAGE = "Persistence QA: cart reminder pending";
    static final int ORDER_TICKET_COUNT = 2;

    private PersistenceRestartFixtures() {
    }

    record Snapshot(
            UUID ownerId,
            UUID managerId,
            UUID eventId,
            UUID gaZoneId,
            UUID seatZoneId,
            UUID seatId,
            UUID orderId,
            UUID sessionId,
            String notificationUserId
    ) {
    }

    static Snapshot seed(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            IEventRepository eventRepository,
            IOrderRepository orderRepository,
            IPendingNotificationRepository notificationRepository
    ) {
        UUID ownerId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID gaZoneId = UUID.randomUUID();
        UUID seatZoneId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Member owner = new Member(ownerId, "persist_owner", "persist-owner@example.com", "pw-hash");
        owner.addStaffAppointment(COMPANY_NAME, new StaffAppointment(
                COMPANY_NAME, ownerId, StaffRole.OWNER, Set.of()));
        memberRepository.save(owner);

        Member manager = new Member(managerId, "persist_manager", "persist-manager@example.com", "pw-hash");
        manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(
                COMPANY_NAME, ownerId, StaffRole.MANAGER,
                Set.of(ManagerPermission.INVENTORY_MGMT, ManagerPermission.VIEW_REPORTS)));
        memberRepository.save(manager);

        companyRepository.save(new Company(COMPANY_NAME, "Restart recovery acceptance fixture", ownerId));

        Event event = new Event(
                eventId,
                COMPANY_NAME,
                EVENT_NAME,
                "Event with GA + assigned seating for restart QA",
                EventCategory.CONCERT,
                new EventSchedule(
                        Instant.parse("2026-12-15T19:00:00Z"),
                        Instant.parse("2026-12-15T22:00:00Z"),
                        Instant.parse("2026-12-15T18:00:00Z")),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(),
                new NoDiscountPolicy());
        InventoryZone seating = InventoryZone.createAssigned(seatZoneId, "Front", new BigDecimal("120.00"));
        seating.addSeat(new Seat(seatId, "A", "1"));
        event.addZone(seating);
        event.addZone(InventoryZone.createGA(gaZoneId, "Floor", new BigDecimal("45.00"), 80));
        event.publish();
        eventRepository.save(event);

        ActiveOrder order = new ActiveOrder(orderId, sessionId, managerId, eventId,
                Instant.parse("2026-06-10T10:00:00Z"));
        order.addItem(OrderItem.forGA(UUID.randomUUID(), gaZoneId, ORDER_TICKET_COUNT, new BigDecimal("45.00")));
        orderRepository.save(order);

        String notificationUserId = managerId.toString();
        notificationRepository.savePendingNotification(notificationUserId, NOTIFICATION_MESSAGE);

        return new Snapshot(ownerId, managerId, eventId, gaZoneId, seatZoneId, seatId,
                orderId, sessionId, notificationUserId);
    }

    static void assertStatePresent(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            IEventRepository eventRepository,
            IOrderRepository orderRepository,
            IPendingNotificationRepository notificationRepository,
            Snapshot snapshot
    ) {
        var company = companyRepository.findByName(COMPANY_NAME);
        assertThat(company).isPresent();
        assertThat(company.get().getFounderId()).isEqualTo(snapshot.ownerId());

        Member owner = memberRepository.findById(snapshot.ownerId()).orElseThrow();
        StaffAppointment ownerRole = owner.getStaffAppointment(COMPANY_NAME);
        assertThat(ownerRole).isNotNull();
        assertThat(ownerRole.getRole()).isEqualTo(StaffRole.OWNER);

        Member manager = memberRepository.findById(snapshot.managerId()).orElseThrow();
        StaffAppointment managerRole = manager.getStaffAppointment(COMPANY_NAME);
        assertThat(managerRole).isNotNull();
        assertThat(managerRole.getRole()).isEqualTo(StaffRole.MANAGER);
        assertThat(managerRole.getPermissions())
                .contains(ManagerPermission.INVENTORY_MGMT, ManagerPermission.VIEW_REPORTS);

        Event event = eventRepository.findById(snapshot.eventId()).orElseThrow();
        assertThat(event.getName()).isEqualTo(EVENT_NAME);
        assertThat(event.getCompanyName()).isEqualTo(COMPANY_NAME);
        assertThat(event.getZones()).hasSize(2);
        assertThat(event.findZone(snapshot.gaZoneId()).getAvailableCount()).isEqualTo(80);
        assertThat(event.findZone(snapshot.seatZoneId()).getSeats()).hasSize(1);
        assertThat(event.findZone(snapshot.seatZoneId()).findSeat(snapshot.seatId()).getRow()).isEqualTo("A");

        ActiveOrder order = orderRepository.findById(snapshot.orderId()).orElseThrow();
        assertThat(order.getMemberId()).isEqualTo(snapshot.managerId());
        assertThat(order.getEventId()).isEqualTo(snapshot.eventId());
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().get(0).getQuantity()).isEqualTo(ORDER_TICKET_COUNT);
        assertThat(orderRepository.findActiveByMemberId(snapshot.managerId())).isPresent();
        assertThat(orderRepository.findActiveBySessionId(snapshot.sessionId())).isPresent();

        assertThat(notificationRepository.getPendingNotifications(snapshot.notificationUserId()))
                .containsExactly(NOTIFICATION_MESSAGE);
    }
}
