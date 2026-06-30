package com.ticketing.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.application.initialization.DeferredDatabaseStartupPoller;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SaleMethod;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.lottery.LotteryEntry;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.IOrderRepository;

/**
 * Verifies {@link DeferredDatabaseStartupPoller} and {@link LotteryDrawScheduler} work when
 * {@code spring.main.lazy-initialization=true} (the production default).
 */
@SpringBootTest(properties = {
        "ticketing.persistence=jpa",
        "spring.main.lazy-initialization=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ticketing.startup.initialize-platform=false",
        "ticketing.bootstrap.dataset=none",
        "ticketing.seed.enabled=false",
        "ticketing.external.base-url=",
        "ticketing.startup.db-recovery-poll-ms=600000"
})
@ActiveProfiles("test")
@DisplayName("DeferredDatabaseStartupPoller under lazy init")
class DeferredDatabaseStartupPollerLazyInitTest {

    private static final String COMPANY = "Poller QA Co";

    @Autowired
    private DeferredDatabaseStartupPoller poller;

    @Autowired
    private LotteryDrawScheduler scheduler;

    @Autowired
    private IEventRepository eventRepository;

    @Autowired
    private ILotteryRepository lotteryRepository;

    @Autowired
    private IOrderRepository orderRepository;

    @Autowired
    private IMemberRepository memberRepository;

    @Test
    @DisplayName("Closed-window lottery draw runs when scheduler is armed under lazy init")
    void givenClosedWindowLottery_whenSchedulerArmsDrawUnderLazyInit_thenWinnersCreated() throws InterruptedException {
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID memberOne = UUID.randomUUID();
        UUID memberTwo = UUID.randomUUID();
        memberRepository.save(new Member(memberOne, "poll_m1", "poll_m1@test.com", "pass"));
        memberRepository.save(new Member(memberTwo, "poll_m2", "poll_m2@test.com", "pass"));

        Event event = new Event(eventId, COMPANY, "Poller QA lottery", "draw probe",
                EventCategory.CONCERT,
                new EventSchedule(now.plusSeconds(86400), now.plusSeconds(90000), now.plusSeconds(82000)),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(now.minusSeconds(7200), now.minusSeconds(60), 2, 48));
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("40.00"), 10));
        event.setVenueMap(new VenueMap(Map.of("Floor", zoneId)));
        event.publish();
        eventRepository.save(event);

        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, memberOne, now.minusSeconds(3000)));
        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), eventId, memberTwo, now.minusSeconds(2900)));

        assertThat(scheduler.scheduleDrawFor(event)).isTrue();
        assertDrawCompletesWithin(eventId, 5_000);
    }

    private void assertDrawCompletesWithin(UUID eventId, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (orderRepository.findActiveByEventId(eventId).size() == 2) {
                return;
            }
            Thread.sleep(200);
        }
        assertThat(orderRepository.findActiveByEventId(eventId)).hasSize(2);
    }

    @Test
    @DisplayName("Poller is eagerly created and runs startup rearm under global lazy init")
    void givenLazyApplicationInit_whenStartupCompletes_thenPollerBeanIsAvailable() {
        assertThat(poller).isNotNull();
        assertThat(scheduler).isNotNull();
        poller.pollForDatabaseRecovery();
    }
}
