package com.ticketing.application;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;

public class CompanyQueryServiceTest {

    private static final String COMPANY = "Acme Productions";

    private InMemoryCompanyRepository companyRepo;
    private InMemoryEventRepository eventRepo;
    private CompanyQueryService service;

    @BeforeEach
    public void setUp() {
        companyRepo = new InMemoryCompanyRepository();
        eventRepo = new InMemoryEventRepository();
        service = new CompanyQueryService(companyRepo, eventRepo);
    }

    @Test
    public void GivenActiveCompanyWithPublishedEvents_WhenGetCompanyInfo_ThenReturnsPublicDetails() {
        companyRepo.save(new Company(COMPANY, "We host concerts", UUID.randomUUID()));
        Event published = eventOf(COMPANY, "Spring Concert");
        publish(published);
        eventRepo.save(published);

        Optional<CompanyPublicDTO> dto = service.getCompanyInfo(COMPANY);

        assertTrue(dto.isPresent());
        assertEquals(COMPANY, dto.get().name());
        assertEquals("We host concerts", dto.get().description());
        assertEquals(1, dto.get().events().size());
        assertEquals("Spring Concert", dto.get().events().get(0).name());
        assertEquals(EventStatus.PUBLISHED, dto.get().events().get(0).status());
    }

    @Test
    public void GivenSuspendedCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
        Company c = new Company(COMPANY, "x", UUID.randomUUID());
        c.suspend();
        companyRepo.save(c);

        assertTrue(service.getCompanyInfo(COMPANY).isEmpty());
    }

    @Test
    public void GivenClosedCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
        Company c = new Company(COMPANY, "x", UUID.randomUUID());
        c.close();
        companyRepo.save(c);

        assertTrue(service.getCompanyInfo(COMPANY).isEmpty());
    }

    @Test
    public void GivenUnknownCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
        assertTrue(service.getCompanyInfo("Nonexistent Co").isEmpty());
    }

    @Test
    public void GivenNullOrBlankName_WhenGetCompanyInfo_ThenReturnsEmpty() {
        assertTrue(service.getCompanyInfo(null).isEmpty());
        assertTrue(service.getCompanyInfo("  ").isEmpty());
    }

    @Test
    public void GivenDraftAndCancelledEvents_WhenGetCompanyInfo_ThenOnlyVisibleStatusesReturned() {
        companyRepo.save(new Company(COMPANY, "desc", UUID.randomUUID()));

        Event draft = eventOf(COMPANY, "Draft Event"); // stays DRAFT
        Event published = eventOf(COMPANY, "Live Event");
        publish(published);
        Event cancelled = eventOf(COMPANY, "Cancelled Event");
        cancelled.cancel();

        eventRepo.save(draft);
        eventRepo.save(published);
        eventRepo.save(cancelled);

        CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

        assertEquals(1, dto.events().size());
        assertEquals("Live Event", dto.events().get(0).name());
    }

    @Test
    public void GivenActiveCompany_WhenGetCompanyInfo_ThenDtoFieldsHoldNoDomainTypes() {
        UUID founderId = UUID.randomUUID();
        companyRepo.save(new Company(COMPANY, "desc", founderId));

        service.getCompanyInfo(COMPANY).orElseThrow();

        // Type-based check — would catch a future regression where someone adds
        // e.g. `Company company` or `List<StaffAppointment>` to the DTO.
        for (Field f : CompanyPublicDTO.class.getDeclaredFields()) {
            String typeName = f.getType().getName();
            assertFalse(typeName.startsWith("com.ticketing.domain.company"),
                    "DTO field of domain type leaks Company internals: " + f);
            assertFalse(typeName.startsWith("com.ticketing.domain.member"),
                    "DTO field of domain type leaks Member internals: " + f);
        }
    }

    @Test
    public void GivenDtoReturned_WhenSourceCompanyMutated_ThenDtoUnchanged() {
        Company c = new Company(COMPANY, "original description", UUID.randomUUID());
        companyRepo.save(c);

        CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

        // mutate source after the DTO is in the caller's hands
        c.setDescription("LEAKED");

        assertEquals("original description", dto.description(),
                "DTO must be a snapshot — not a live view of the Company");
    }

    @Test
    public void GivenDtoReturned_WhenCallerMutatesEventsList_ThenThrows() {
        companyRepo.save(new Company(COMPANY, "x", UUID.randomUUID()));
        Event e = eventOf(COMPANY, "Live");
        publish(e);
        eventRepo.save(e);

        CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> dto.events().clear());
    }

    // helpers

    private static Event eventOf(String companyName, String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new Event(UUID.randomUUID(), companyName, name, "desc", EventCategory.CONCERT,
                new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                new LockTimerDuration(Duration.ofMinutes(15)));
    }

    /** publish() needs at least one zone, so add a tiny GA zone first. */
    private static void publish(Event e) {
        e.addZone(InventoryZone.createGA(UUID.randomUUID(), "Floor",
                new java.math.BigDecimal("10.00"), 10));
        e.publish();
    }
}
