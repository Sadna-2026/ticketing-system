package com.ticketing.domain.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;

/**
 * Value object: maps visual sections (named regions of a venue layout)
 * to {@link InventoryZone} ids. Multiple sections may reference the same zone.
 * Bijection with the Event's zones is enforced by the Event aggregate.
 *
 * <p>Mapped as an {@code @Embeddable} embedded singularly in {@link Event}; the
 * {@code Map<String,UUID>} is an {@code @ElementCollection} (legal inside a
 * singly-embedded embeddable). {@code final} was removed from the field and class
 * so JPA can set the map via field access; the public API is unchanged.
 */
@Embeddable
public class VenueMap {

    @ElementCollection
    @CollectionTable(
            name = "venue_map_sections",
            joinColumns = @JoinColumn(name = "event_id"))
    @MapKeyColumn(name = "section_name")
    @Column(name = "zone_id")
    private Map<String, UUID> sectionToZone;

    // Required by JPA; do not use directly.
    protected VenueMap() {
    }

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
