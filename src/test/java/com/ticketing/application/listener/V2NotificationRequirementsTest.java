package com.ticketing.application.listener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.RoleAppointmentOfferResponseEvent;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.notification.InMemoryPendingNotificationRepository;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;

/**
 * V2-INF-6: end-to-end notification coverage for the V2 spec, driving a real
 * application flow (a role-appointment response) through the *real*
 * {@link WebSocketNotificationService} — not a mock notifier.
 *
 * <p>Complements the existing coverage, which exercises these layers separately:
 * <ul>
 *   <li>broadcaster real-time/delayed mechanics — {@code WebSocketNotificationServiceTest}</li>
 *   <li>application-boundary notify() calls via a <em>mock</em> notifier — {@code ApplicationListenerTest}</li>
 * </ul>
 * Here the two meet: an application handler notifies a member, and we assert the
 * connected member gets it in real time, while an offline member's message is
 * stored and delivered exactly once on reconnect.
 */
@DisplayName("V2 notification requirements (application flow over real WebSocket)")
class V2NotificationRequirementsTest {

    private static final String COMPANY_NAME = "TestCo";

    private InMemoryMemberRepository memberRepository;
    private InMemoryCompanyRepository companyRepository;
    private InMemoryPendingNotificationRepository pendingRepository;
    private WebSocketNotificationService notificationService;
    private RoleAppointmentOfferResponseHandler handler;

    private UUID appointerId;
    private UUID targetId;
    private UUID offerId;

    @BeforeEach
    void setUp() {
        memberRepository = new InMemoryMemberRepository();
        companyRepository = new InMemoryCompanyRepository();
        pendingRepository = new InMemoryPendingNotificationRepository();
        notificationService = new WebSocketNotificationService(pendingRepository,
                Executors.newSingleThreadExecutor());
        handler = new RoleAppointmentOfferResponseHandler(memberRepository, companyRepository, notificationService);

        appointerId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        // Appointer owns the company and made a manager offer to the target.
        Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "password");
        appointer.addStaffAppointment(COMPANY_NAME,
                new StaffAppointment(COMPANY_NAME, appointerId, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));
        memberRepository.save(appointer);

        PendingRoleOffer offer = new PendingRoleOffer(appointerId, COMPANY_NAME,
                StaffAppointment.StaffRole.MANAGER, Collections.emptySet(), LocalDateTime.now().plusDays(1));
        offerId = offer.getOfferId();
        Member target = new Member(targetId, "target", "target@test.com", "password");
        target.addPendingOffer(offer);
        memberRepository.save(target);

        companyRepository.save(new Company(COMPANY_NAME, "desc", appointerId));
    }

    @AfterEach
    void tearDown() {
        notificationService.shutdown();
    }

    @Test
    @DisplayName("RealTime — connected appointer receives the offer-response notification live")
    void GivenConnectedAppointer_WhenRoleOfferAccepted_ThenAppointerGetsRealTimeNotification() throws Exception {
        List<String> inbox = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch delivered = new CountDownLatch(1);
        notificationService.registerListener(appointerId.toString(), msg -> { inbox.add(msg); delivered.countDown(); });

        handler.handle(new RoleAppointmentOfferResponseEvent(offerId, targetId, true));

        assertTrue(delivered.await(2, TimeUnit.SECONDS), "connected appointer should be notified in real time");
        assertEquals(1, inbox.size());
        assertTrue(inbox.get(0).toLowerCase().contains("accepted"),
                "message should report the acceptance, was: " + inbox.get(0));
        assertTrue(pendingRepository.getPendingNotifications(appointerId.toString()).isEmpty(),
                "nothing should be queued for a connected user");
    }

    @Test
    @DisplayName("Delayed — offline appointer's notification is stored and delivered once on reconnect")
    void GivenOfflineAppointer_WhenRoleOfferAccepted_ThenNotificationStoredAndDeliveredOnceOnReconnect() throws Exception {
        // Appointer is offline: no listener registered when the offer is answered.
        handler.handle(new RoleAppointmentOfferResponseEvent(offerId, targetId, true));

        List<String> pending = pendingRepository.getPendingNotifications(appointerId.toString());
        assertEquals(1, pending.size(), "offline appointer's notification must be stored, not dropped");
        String stored = pending.get(0);
        assertTrue(stored.toLowerCase().contains("accepted"), "stored message should report acceptance, was: " + stored);

        // Appointer reconnects → pending flushes exactly once.
        List<String> inbox = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch delivered = new CountDownLatch(1);
        notificationService.registerListener(appointerId.toString(), msg -> { inbox.add(msg); delivered.countDown(); });

        assertTrue(delivered.await(2, TimeUnit.SECONDS), "stored notification should flush on reconnect");
        assertEquals(1, inbox.size(), "delivered exactly once");
        assertEquals(stored, inbox.get(0));
        assertTrue(pendingRepository.getPendingNotifications(appointerId.toString()).isEmpty(),
                "pending cleared after delivery — not delivered twice");
    }
}
