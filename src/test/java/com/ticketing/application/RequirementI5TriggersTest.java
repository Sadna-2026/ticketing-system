package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.listener.RelinquishOwnershipHandler;
import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.application.services.OrderService;
import com.ticketing.application.services.SystemAnalyticsCollector;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RelinquishOwnershipEvent;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.OrderTimeDomainService;

@DisplayName("Requirement I.5: Triggers to verify")
class RequirementI5TriggersTest {

    private INotificationService notificationService;
    private ISessionTokenService sessionTokenService;
    private IMemberRepository memberRepository;
    private ICompanyRepository companyRepository;
    private IEventRepository eventRepository;
    private IOrderRepository orderRepository;

    private UUID memberId = UUID.randomUUID();
    private String token = "test-token";
    private String adminToken = "admin-token";
    private String companyName = "TestCompany";

    @BeforeEach
    void setup() {
        notificationService = mock(INotificationService.class);
        sessionTokenService = mock(ISessionTokenService.class);
        when(sessionTokenService.isValid(anyString())).thenReturn(true);
        when(sessionTokenService.extractMemberId(token)).thenReturn(memberId);
        when(sessionTokenService.extractMemberId(adminToken)).thenReturn(memberId);
        when(sessionTokenService.extractPermissions(adminToken)).thenReturn(Set.of("SYSTEM_ADMIN"));

        memberRepository = mock(IMemberRepository.class);
        companyRepository = mock(ICompanyRepository.class);
        eventRepository = mock(IEventRepository.class);
        orderRepository = mock(IOrderRepository.class);
    }

    @Test
    @DisplayName("1. Event Sold Out (to producers/managers)")
    void triggerEventSoldOut() {
        OrderService orderService = new OrderService(sessionTokenService, orderRepository, eventRepository, companyRepository, memberRepository, List.of(), List.of(), Instant::now, mock(com.ticketing.domain.queue.IQueueRepository.class), mock(com.ticketing.domain.services.OrderTimeDomainService.class), notificationService, null);

        Event event = mock(Event.class);
        when(event.isFullySold()).thenReturn(true);
        when(event.isPublished()).thenReturn(true);
        when(event.getCompanyName()).thenReturn(companyName);
        when(event.getName()).thenReturn("Concert");

        Member manager = mock(Member.class);
        when(manager.getId()).thenReturn(UUID.randomUUID());
        when(manager.hasStaffAppointment(companyName, StaffAppointment.StaffRole.OWNER)).thenReturn(true);
        when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(manager));

        try {
            java.lang.reflect.Method method = OrderService.class.getDeclaredMethod("checkAndPublishSoldOut", Event.class);
            method.setAccessible(true);
            method.invoke(orderService, event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(manager.getId().toString()), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("Event sold out"));
    }

    @Test
    @DisplayName("2. Company closed/frozen")
    void triggerCompanyClosed() {
        CompanyService companyService = new CompanyService(companyRepository, mock(IEventPublisher.class), sessionTokenService, memberRepository, eventRepository, orderRepository, null, notificationService);

        Member admin = mock(Member.class);
        when(admin.getId()).thenReturn(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(admin));

        Company company = mock(Company.class);
        when(company.getName()).thenReturn(companyName);
        when(company.getFounderId()).thenReturn(memberId);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(company));
        
        StaffAppointment appt = mock(StaffAppointment.class);
        when(appt.isOwner()).thenReturn(true);
        when(admin.getStaffAppointment(companyName)).thenReturn(appt);

        Member staff = mock(Member.class);
        UUID staffId = UUID.randomUUID();
        when(staff.getId()).thenReturn(staffId);
        StaffAppointment staffAppt = mock(StaffAppointment.class);
        when(staffAppt.isRevoked()).thenReturn(false);
        when(staff.getStaffAppointment(companyName)).thenReturn(staffAppt);
        when(memberRepository.findByCompanyAppointment(companyName)).thenReturn(List.of(staff));

        companyService.suspendCompany(adminToken, companyName);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(staffId.toString()), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("suspended"));
    }

    @Test
    @DisplayName("3. Role changes")
    void triggerRoleChanges() {
        RelinquishOwnershipHandler handler = new RelinquishOwnershipHandler(memberRepository, notificationService);

        Company company = mock(Company.class);
        when(company.getName()).thenReturn(companyName);
        when(company.getFounderId()).thenReturn(memberId);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(company));

        RelinquishOwnershipEvent event = new RelinquishOwnershipEvent(company, memberId);

        Member owner = mock(Member.class);
        when(owner.getId()).thenReturn(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(owner));
        
        StaffAppointment appt = mock(StaffAppointment.class);
        when(appt.getRole()).thenReturn(StaffAppointment.StaffRole.OWNER);
        when(owner.getStaffAppointment(companyName)).thenReturn(appt);
        
        UUID targetId = UUID.randomUUID();
        com.ticketing.application.listener.RevokePersonnelHandler revokeHandler = new com.ticketing.application.listener.RevokePersonnelHandler(memberRepository, notificationService);
        com.ticketing.domain.member.communication.RevokePersonnelEvent revokeEvent = new com.ticketing.domain.member.communication.RevokePersonnelEvent(company, memberId, targetId);
        
        StaffAppointment targetAppt = mock(StaffAppointment.class);
        when(targetAppt.getAppointedByMemberId()).thenReturn(memberId);
        Member target = mock(Member.class);
        when(target.getId()).thenReturn(targetId);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(target.getStaffAppointment(companyName)).thenReturn(targetAppt);
        
        revokeHandler.handle(revokeEvent);
        verify(notificationService).notify(eq(targetId.toString()), anyString());
    }

    @Test
    @DisplayName("4. Purchase completed")
    void triggerPurchaseCompleted() {
        UUID eventId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();

        when(sessionTokenService.extractSessionId(token)).thenReturn(sessionId);

        com.ticketing.domain.gateway.IPaymentGateway paymentGateway = mock(com.ticketing.domain.gateway.IPaymentGateway.class);
        com.ticketing.domain.gateway.PaymentResult paymentResult = mock(com.ticketing.domain.gateway.PaymentResult.class);
        when(paymentResult.success()).thenReturn(true);
        when(paymentResult.transactionId()).thenReturn("txn-1");
        when(paymentGateway.charge(any(), any())).thenReturn(paymentResult);

        com.ticketing.domain.gateway.ITicketSupplyGateway supplyGateway = mock(com.ticketing.domain.gateway.ITicketSupplyGateway.class);
        com.ticketing.domain.gateway.SupplyResult supplyResult = mock(com.ticketing.domain.gateway.SupplyResult.class);
        when(supplyResult.success()).thenReturn(true);
        when(supplyGateway.issueTickets(any(), any())).thenReturn(supplyResult);

        OrderService orderService = new OrderService(sessionTokenService, orderRepository, eventRepository,
                companyRepository, memberRepository, List.of(paymentGateway), List.of(supplyGateway),
                Instant::now, mock(com.ticketing.domain.queue.IQueueRepository.class),
                mock(com.ticketing.domain.services.OrderTimeDomainService.class), notificationService, null);

        // Real persistent policy types (the Event constructor rejects non-persistent/mocked
        // policies). AlwaysAllowPolicy lets checkout proceed; NoDiscountPolicy charges the order total.
        com.ticketing.domain.event.IPurchasePolicy purchasePolicy = new com.ticketing.domain.event.AlwaysAllowPolicy();
        com.ticketing.domain.event.IDiscountPolicy discountPolicy = new com.ticketing.domain.event.NoDiscountPolicy();

        com.ticketing.domain.event.InventoryZone zone = com.ticketing.domain.event.InventoryZone.createGA(
                zoneId, "GA", new java.math.BigDecimal("10.00"), 10);
        Event event = new Event(eventId, companyName, "Concert", "Desc", EventCategory.CONCERT,
                new EventSchedule(Instant.now(), Instant.now().plus(Duration.ofHours(2)), null),
                new LockTimerDuration(Duration.ofMinutes(10)), purchasePolicy, discountPolicy,
                SaleMethod.REGULAR, null);
        event.addZone(zone);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        zone.lockGA(1);
        com.ticketing.domain.order.OrderItem item = com.ticketing.domain.order.OrderItem.forGA(
                UUID.randomUUID(), zoneId, 1, new java.math.BigDecimal("10.00"));
        ActiveOrder order = new ActiveOrder(UUID.randomUUID(), sessionId, memberId, eventId, Instant.now());
        order.addItem(item);
        when(orderRepository.findActiveByMemberId(memberId)).thenReturn(Optional.of(order));

        Member buyer = mock(Member.class);
        when(buyer.getEmail()).thenReturn("buyer@test.com");
        when(buyer.getUsername()).thenReturn("buyer");
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(buyer));

        Company company = mock(Company.class);
        when(company.isActive()).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(company));

        orderService.checkout(token, null);

        verify(notificationService).notify(eq(memberId.toString()), contains("checkout was completed successfully"));
    }

    @Test
    @DisplayName("5. Event cancellation/reschedule (to buyers)")
    void triggerEventReschedule() {
        UUID eventId = UUID.randomUUID();
        EventService eventService = new EventService(eventRepository, companyRepository, memberRepository, orderRepository, sessionTokenService, mock(com.ticketing.domain.lottery.ILotteryRepository.class), Instant::now, mock(OrderService.class), notificationService);

        Event event = new Event(eventId, companyName, "Old Event", "Desc", EventCategory.CONCERT, new EventSchedule(Instant.now(), Instant.now().plus(Duration.ofHours(2)), null), new LockTimerDuration(Duration.ofMinutes(10)), new com.ticketing.domain.event.AlwaysAllowPolicy(), new com.ticketing.domain.event.NoDiscountPolicy(), SaleMethod.REGULAR, null);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        Member admin = mock(Member.class);
        when(admin.getId()).thenReturn(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(admin));
        
        Company company = mock(Company.class);
        when(company.getName()).thenReturn(companyName);
        when(company.isActive()).thenReturn(true);
        when(companyRepository.findByName(companyName)).thenReturn(Optional.of(company));
        
        StaffAppointment appt = mock(StaffAppointment.class);
        when(appt.isOwner()).thenReturn(true);
        when(admin.getStaffAppointment(companyName)).thenReturn(appt);

        CompletedPurchase purchase = mock(CompletedPurchase.class);
        UUID buyerId = UUID.randomUUID();
        when(purchase.memberId()).thenReturn(buyerId);
        when(orderRepository.findCompletedByEventId(eventId)).thenReturn(List.of(purchase));

        EditEventRequest request = new EditEventRequest(eventId, null, null, null, new EventSchedule(Instant.now().plus(Duration.ofDays(1)), Instant.now().plus(Duration.ofDays(1).plusHours(2)), null), null);
        eventService.editEvent(token, request);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(buyerId.toString()), messageCaptor.capture());
        assertTrue(messageCaptor.getValue().contains("rescheduled"));
    }

    @Test
    @DisplayName("6. Active-order expiry warning")
    void triggerExpiryWarning() {
        UUID eventId = UUID.randomUUID();
        UUID expiredMemberId = UUID.randomUUID();

        IOrderRepository localOrderRepo = mock(IOrderRepository.class);
        IEventRepository localEventRepo = mock(IEventRepository.class);

        com.ticketing.domain.services.OrderTimeDomainService timeDomainService =
                new com.ticketing.domain.services.OrderTimeDomainService(
                        localOrderRepo, localEventRepo, Instant::now, notificationService);

        // Order created 2 hours ago with a 10-minute lock timer — definitely expired
        Instant longAgo = Instant.now().minus(Duration.ofHours(2));
        ActiveOrder expiredOrder = new ActiveOrder(
                UUID.randomUUID(), UUID.randomUUID(), expiredMemberId, eventId, longAgo);
        when(localOrderRepo.findAllActive()).thenReturn(List.of(expiredOrder));

        com.ticketing.domain.event.IPurchasePolicy pp = new com.ticketing.domain.event.AlwaysAllowPolicy();
        com.ticketing.domain.event.IDiscountPolicy dp = new com.ticketing.domain.event.NoDiscountPolicy();
        Event event = new Event(eventId, companyName, "Expiry Event", "Desc", EventCategory.CONCERT,
                new EventSchedule(Instant.now(), Instant.now().plus(Duration.ofHours(2)), null),
                new LockTimerDuration(Duration.ofMinutes(10)), pp, dp, SaleMethod.REGULAR, null);
        when(localEventRepo.findById(eventId)).thenReturn(Optional.of(event));

        timeDomainService.expireOrders();

        verify(notificationService).notify(eq(expiredMemberId.toString()), contains("expired"));
    }

    @Test
    @DisplayName("7. New admin/producer message to members")
    void triggerAdminMessage() {
        AdminService adminService = new AdminService(memberRepository, companyRepository, sessionTokenService, mock(IAdminRepository.class), orderRepository, notificationService, mock(SystemAnalyticsCollector.class), mock(OrderTimeDomainService.class));

        Member admin = mock(Member.class);
        when(admin.getId()).thenReturn(memberId);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(admin));

        UUID targetId = UUID.randomUUID();
        Member target = mock(Member.class);
        when(target.getId()).thenReturn(targetId);
        when(memberRepository.findById(targetId)).thenReturn(Optional.of(target));
        when(target.suspend(any(), any(), any())).thenReturn(mock(com.ticketing.domain.member.Suspension.class));

        adminService.suspendUser(adminToken, targetId, Duration.ofDays(1), "Spamming");

        verify(notificationService).notify(eq(targetId.toString()), contains("suspended"));
    }

    @Test
    @DisplayName("8. Lottery result")
    void triggerLotteryResult() {
        UUID eventId = UUID.randomUUID();
        EventService eventService = new EventService(eventRepository, companyRepository, memberRepository, orderRepository, sessionTokenService, mock(com.ticketing.domain.lottery.ILotteryRepository.class), mock(com.ticketing.application.ISystemClock.class), mock(OrderService.class), notificationService);
        
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.isLottery()).thenReturn(true);
        when(event.getLotteryWindow()).thenReturn(null);
        when(event.totalCapacity()).thenReturn(10);
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        
        com.ticketing.domain.lottery.ILotteryRepository lotteryRepo = mock(com.ticketing.domain.lottery.ILotteryRepository.class);
        
        eventService = new EventService(eventRepository, companyRepository, memberRepository, orderRepository, sessionTokenService, lotteryRepo, Instant::now, mock(OrderService.class), notificationService);
        
        UUID winnerId = UUID.randomUUID();
        com.ticketing.domain.lottery.LotteryEntry entry = new com.ticketing.domain.lottery.LotteryEntry(UUID.randomUUID(), eventId, winnerId, Instant.now());
        when(lotteryRepo.findByEventId(eventId)).thenReturn(List.of(entry));
        
        eventService.drawLotteryAutomatically(eventId);
        
        verify(notificationService).notify(eq(winnerId.toString()), contains("won the lottery"));
    }
}
