package com.ticketing.application.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

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

/**
 * Seeds a closed-window lottery on the configured remote DB so a subsequent server start
 * can prove {@link DeferredDatabaseStartupPoller} rearms and runs the draw.
 *
 * <p>Run with the same DB env vars as {@code cloud-split} production, server stopped:
 * {@code mvn test -Dtest=LotteryDrawRemoteProbeTest}
 */
@Tag("slow")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "ticketing.persistence=jpa",
                "spring.main.lazy-initialization=true",
                "spring.jpa.hibernate.ddl-auto=validate",
                "ticketing.startup.initialize-platform=false",
                "ticketing.bootstrap.dataset=none",
                "ticketing.seed.enabled=false",
                "ticketing.external.base-url=",
                "ticketing.startup.db-recovery-poll-ms=600000"
        })
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "DB_URL_OPERATIONAL", matches = ".+")
@DisplayName("Remote DB lottery draw probe seed")
class LotteryDrawRemoteProbeTest {

    static final UUID PROBE_EVENT_ID = UUID.fromString("aaaaaaaa-bbbb-bbbb-bbbb-000000000001");
    static final String PROBE_EVENT_NAME = "QA lottery startup probe";

    @DynamicPropertySource
    static void remoteDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.operational.url", () -> System.getenv("DB_URL_OPERATIONAL"));
        registry.add("spring.datasource.config.url",
                () -> System.getenv().getOrDefault("DB_URL_CONFIG", System.getenv("DB_URL_OPERATIONAL")));
        registry.add("spring.datasource.operational.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.config.username", () -> System.getenv("DB_USERNAME"));
        registry.add("spring.datasource.operational.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.config.password", () -> System.getenv("DB_PASSWORD"));
        registry.add("spring.datasource.operational.driver-class-name",
                () -> System.getenv().getOrDefault("DB_DRIVER", "org.postgresql.Driver"));
        registry.add("spring.datasource.config.driver-class-name",
                () -> System.getenv().getOrDefault("DB_DRIVER", "org.postgresql.Driver"));
        registry.add("spring.jpa.database-platform",
                () -> System.getenv().getOrDefault("DB_DIALECT", "org.hibernate.dialect.PostgreSQLDialect"));
    }

    @Autowired
    private IEventRepository eventRepository;

    @Autowired
    private ILotteryRepository lotteryRepository;

    @Autowired
    private IMemberRepository memberRepository;

    @Test
    @Commit
    @DisplayName("Seeds a closed-window lottery with two registrants (no draw in this JVM)")
    void seedClosedWindowLotteryProbeOnRemoteDb() {
        Instant now = Instant.now();
        String company = "Staff demo production company";

        lotteryRepository.findByEventId(PROBE_EVENT_ID)
                .forEach(entry -> lotteryRepository.delete(entry.id()));

        UUID zoneId = UUID.randomUUID();
        Event event = new Event(PROBE_EVENT_ID, company, PROBE_EVENT_NAME,
                "Automated probe for lottery startup rearm",
                EventCategory.CONCERT,
                new EventSchedule(now.plus(Duration.ofDays(30)), now.plus(Duration.ofDays(30).plusSeconds(7200)),
                        now.plus(Duration.ofDays(29))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy(),
                SaleMethod.LOTTERY,
                new LotteryWindow(now.minus(Duration.ofDays(2)), now.minus(Duration.ofHours(1)), 2, 48));
        event.addZone(InventoryZone.createGA(zoneId, "Probe floor", new BigDecimal("25.00"), 10));
        event.setVenueMap(new VenueMap(Map.of("Probe floor", zoneId)));
        event.publish();
        eventRepository.save(event);

        UUID memberOne = memberRepository.findByUsername("u1")
                .orElseThrow(() -> new IllegalStateException("u1 missing on remote DB"))
                .getId();
        UUID memberTwo = memberRepository.findByUsername("u2")
                .orElseThrow(() -> new IllegalStateException("u2 missing on remote DB"))
                .getId();

        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), PROBE_EVENT_ID, memberOne, now.minus(Duration.ofHours(3))));
        lotteryRepository.save(new LotteryEntry(UUID.randomUUID(), PROBE_EVENT_ID, memberTwo, now.minus(Duration.ofHours(2))));

        assertThat(eventRepository.findById(PROBE_EVENT_ID)).isPresent();
        assertThat(lotteryRepository.findByEventId(PROBE_EVENT_ID)).hasSize(2);
    }
}
