package com.ticketing.domain.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Value object: maps visual sections (named regions of a venue layout)
 * to {@link InventoryZone} ids. Multiple sections may reference the same zone.
 * Bijection with the Event's zones is enforced by the Event aggregate.
 */
public final class VenueMap {

    private final Map<String, UUID> sectionToZone;

    public VenueMap(Map<String, UUID> sectionToZone) {
        if (sectionToZone == null || sectionToZone.isEmpty()) {
            throw new IllegalArgumentException("VenueMap must have at least one section");
        }
        Map<String, UUID> copy = new HashMap<>(sectionToZone.size());
        for (Map.Entry<String, UUID> e : sectionToZone.entrySet()) {
            String section = e.getKey();
            UUID zoneId = e.getValue();
            if (section == null || section.isBlank()) {
                throw new IllegalArgumentException("Section name cannot be null or blank");
            }
            if (zoneId == null) {
                throw new IllegalArgumentException("Section '" + section + "' must map to a non-null zone id");
            }
            copy.put(section, zoneId);
        }
        this.sectionToZone = Collections.unmodifiableMap(copy);
    }

    public Map<String, UUID> getSectionToZone() {
        return sectionToZone;
    }

    public Set<UUID> mappedZoneIds() {
        return new HashSet<>(sectionToZone.values());
    }

    public UUID zoneIdFor(String sectionName) {
        if (sectionName == null) return null;
        return sectionToZone.get(sectionName);
    }
}
