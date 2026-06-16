package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.VenueLayout;

/**
 * Regression for the venue-designer "Validate" action throwing a
 * {@code LazyInitializationException} against a real database (V3 jpa mode).
 *
 * <p>{@link JpaEventRepository#findById} touches the zone/seat graph before detaching the
 * aggregate, but originally left the venue layout's {@code cells}
 * {@code @ElementCollection} as an uninitialised LAZY proxy. Reading it after detach —
 * e.g. {@link VenueLayout#hasSellableCell()} from {@code EventService.validateEventLayout}
 * — then failed with "could not initialize proxy - no Session". Only reproduces in
 * {@code jpa} mode; the in-memory repository returns live collections.
 *
 * <p>The persistence context is {@code clear()}ed between save and re-read so the second
 * read genuinely loads the aggregate from H2 (creating the LAZY proxy), mirroring the
 * app, where the read happens in a separate transaction from the write.
 */
@DataJpaTest(properties = {
        "ticketing.persistence=jpa",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaEventRepository.class)
@DisplayName("JPA event repository — venue layout survives detach")
class JpaEventVenueLayoutLazyInitTest {

    @Autowired
    private IEventRepository eventRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("reloaded event's venue-layout cells stay traversable after the aggregate is detached")
    void GivenEventWithVenueLayout_WhenReloadedFromDb_ThenCellsAreTraversable() {
        UUID eventId = UUID.randomUUID();
        Event event = newEvent(eventId);
        event.setVenueLayout(new VenueLayout(1, 2, List.of(
                LayoutCell.ga(0, 0, UUID.randomUUID(), "Floor"),
                LayoutCell.blocked(0, 1))));
        eventRepository.save(event);

        // Force a real reload from the DB so the layout's cells come back as a LAZY proxy
        // (without this the first-level cache returns the live instance and hides the bug).
        entityManager.flush();
        entityManager.clear();

        Event found = eventRepository.findById(eventId).orElseThrow();

        // Before the fix these accesses threw org.hibernate.LazyInitializationException
        // because the cells @ElementCollection was a detached, uninitialised proxy.
        assertThat(found.getVenueLayout().hasSellableCell()).isTrue();
        assertThat(found.getVenueLayout().getCells()).hasSize(2);
    }

    private static Event newEvent(UUID eventId) {
        EventSchedule schedule = new EventSchedule(
                Instant.parse("2026-09-01T20:00:00Z"),
                Instant.parse("2026-09-01T23:00:00Z"),
                Instant.parse("2026-09-01T19:00:00Z"));
        return new Event(eventId, "Acme Productions", "Summer Fest", "desc",
                EventCategory.CONCERT, schedule, new LockTimerDuration(Duration.ofMinutes(10)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
    }
}
