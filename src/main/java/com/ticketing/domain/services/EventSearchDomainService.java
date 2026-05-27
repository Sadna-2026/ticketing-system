package com.ticketing.domain.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;

@org.springframework.stereotype.Service
public class EventSearchDomainService {

    private static final Logger log = LoggerFactory.getLogger(EventSearchDomainService.class);

    private final IEventRepository eventRepository;
    private final ICompanyRepository companyRepository;

    public EventSearchDomainService(IEventRepository eventRepository, ICompanyRepository companyRepository) {
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
    }

    public List<EventSummaryDTO> searchEvents(SearchEventsRequest req) {
        log.info("Event search requested: text={}, category={}, company={}",
                req == null ? null : req.text(), req == null ? null : req.category(), req == null ? null : req.companyName());
        SearchEventsRequest q = req == null ? SearchEventsRequest.empty() : req;
        Set<String> activeCompanyNames = activeCompanyNames();

        List<EventSummaryDTO> hits = eventRepository.findAll().stream()
                .filter(EventSearchDomainService::isBrowsableStatus)
                .filter(e -> activeCompanyNames.contains(e.getCompanyName()))
                .filter(e -> matchesText(e, q.text()))
                .filter(e -> matchesRegion(e, q.region()))
                .filter(e -> matchesCategory(e, q.category()))
                .filter(e -> matchesCompanyName(e, q.companyName()))
                .filter(e -> matchesPriceRange(e, q.minPrice(), q.maxPrice()))
                .filter(e -> matchesDateRange(e, q.fromDate(), q.toDate()))
                .map(EventSummaryDTO::from)
                .toList();

        log.info("Event search: text={}, category={}, company={}, hits={}",
                q.text(), q.category(), q.companyName(), hits.size());
        return hits;
    }

    private Set<String> activeCompanyNames() {
        Set<String> names = new HashSet<>();
        for (Company c : companyRepository.getAll()) {
            if (c.isActive()) names.add(c.getName());
        }
        return names;
    }

    private static boolean isBrowsableStatus(Event e) {
        return e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SOLD_OUT;
    }

    private static boolean matchesText(Event e, String text) {
        if (text == null || text.isBlank()) return true;
        String needle = text.toLowerCase();
        return contains(e.getName(), needle)
                || contains(e.getArtist(), needle)
                || contains(e.getDescription(), needle);
    }

    private static boolean contains(String haystack, String needleLower) {
        return haystack != null && haystack.toLowerCase().contains(needleLower);
    }

    private static boolean matchesRegion(Event e, String region) {
        if (region == null) return true;
        return region.equalsIgnoreCase(e.getRegion());
    }

    private static boolean matchesCategory(Event e, com.ticketing.domain.event.EventCategory category) {
        if (category == null) return true;
        return category == e.getCategory();
    }

    private static boolean matchesCompanyName(Event e, String companyName) {
        if (companyName == null) return true;
        return companyName.equals(e.getCompanyName());
    }

    private static boolean matchesPriceRange(Event e, BigDecimal min, BigDecimal max) {
        if (min == null && max == null) return true;
        for (InventoryZone z : e.getZones()) {
            BigDecimal p = z.getPricePerTicket();
            if (p == null) continue;
            if (min != null && p.compareTo(min) < 0) continue;
            if (max != null && p.compareTo(max) > 0) continue;
            return true;
        }
        return false;
    }

    private static boolean matchesDateRange(Event e, Instant from, Instant to) {
        if (from == null && to == null) return true;
        Instant start = e.getSchedule().getStartTime();
        if (from != null && start.isBefore(from)) return false;
        if (to != null && start.isAfter(to)) return false;
        return true;
    }
}
