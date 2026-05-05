package com.ticketing.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.ticketing.domain.event.EventCategory;

/**
 * Search criteria for {@link EventSearchService#searchEvents}.
 * Every field is optional — null means "do not filter on this field".
 * Filters combine with AND semantics.
 */
public record SearchEventsRequest(
        String text,           // case-insensitive substring against name / artist / description
        String region,         // exact match on Event.region
        EventCategory category,
        String companyName,    // scope to a single company's catalog
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Instant fromDate,      // schedule.startTime >= fromDate
        Instant toDate         // schedule.startTime <= toDate
) {
    public static SearchEventsRequest empty() {
        return new SearchEventsRequest(null, null, null, null, null, null, null, null);
    }
}
