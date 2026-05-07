package com.ticketing.application;

import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.LotteryWindow;
import com.ticketing.domain.event.SaleMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record CreateEventRequest(
        String companyName,
        String name,
        String description,
        EventCategory category,
        EventSchedule schedule,
        LockTimerDuration lockTimerDuration,
        List<ZoneSpec> zones,
        Map<String, String> sectionToZoneName,
        SaleMethod saleMethod,
        LotteryWindow lotteryWindow
) {

    public CreateEventRequest(String companyName, String name, String description,
                              EventCategory category, EventSchedule schedule,
                              LockTimerDuration lockTimerDuration,
                              List<ZoneSpec> zones, Map<String, String> sectionToZoneName) {
        this(companyName, name, description, category, schedule, lockTimerDuration,
                zones, sectionToZoneName, SaleMethod.REGULAR, null);
    }

    public CreateEventRequest {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("event name is required");
        }
        if (schedule == null) {
            throw new IllegalArgumentException("schedule is required");
        }
        if (lockTimerDuration == null) {
            throw new IllegalArgumentException("lockTimerDuration is required");
        }
        if (zones == null || zones.isEmpty()) {
            throw new IllegalArgumentException("at least one zone (ticket type) is required");
        }
        if (sectionToZoneName == null || sectionToZoneName.isEmpty()) {
            throw new IllegalArgumentException("sectionToZoneName must have at least one entry");
        }
        if (saleMethod == null) {
            saleMethod = SaleMethod.REGULAR;
        }
        if (saleMethod == SaleMethod.LOTTERY && lotteryWindow == null) {
            throw new IllegalArgumentException("lotteryWindow is required for LOTTERY sale method");
        }
        zones = List.copyOf(zones);
        sectionToZoneName = Map.copyOf(sectionToZoneName);
    }

    public sealed interface ZoneSpec permits GAZoneSpec, AssignedZoneSpec {
        String name();
        BigDecimal pricePerTicket();
    }

    public record GAZoneSpec(
            String name,
            BigDecimal pricePerTicket,
            int maxCapacity
    ) implements ZoneSpec {
        public GAZoneSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("zone name is required");
            }
            if (pricePerTicket == null || pricePerTicket.signum() < 0) {
                throw new IllegalArgumentException("pricePerTicket must be non-negative");
            }
            if (maxCapacity <= 0) {
                throw new IllegalArgumentException("GA maxCapacity must be positive");
            }
        }
    }

    public record AssignedZoneSpec(
            String name,
            BigDecimal pricePerTicket,
            List<SeatSpec> seats
    ) implements ZoneSpec {
        public AssignedZoneSpec {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("zone name is required");
            }
            if (pricePerTicket == null || pricePerTicket.signum() < 0) {
                throw new IllegalArgumentException("pricePerTicket must be non-negative");
            }
            if (seats == null || seats.isEmpty()) {
                throw new IllegalArgumentException("assigned zone requires at least one seat");
            }
            seats = List.copyOf(seats);
        }
    }

    public record SeatSpec(String row, String seatNumber) {
        public SeatSpec {
            if (row == null || row.isBlank()) {
                throw new IllegalArgumentException("row is required");
            }
            if (seatNumber == null || seatNumber.isBlank()) {
                throw new IllegalArgumentException("seatNumber is required");
            }
        }
    }
}
