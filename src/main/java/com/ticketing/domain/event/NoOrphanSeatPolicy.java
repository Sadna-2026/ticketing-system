package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Rejects seat selections that leave a single isolated (orphan) free seat
 * in an assigned-seating row. Only applies to assigned-seating zones;
 * general-admission zones are ignored.
 */
public class NoOrphanSeatPolicy implements IPurchasePolicy {

    public NoOrphanSeatPolicy() {
    }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        Event event = context.event();
        if (event == null) {
            return PolicyResult.success();
        }

        Set<UUID> incomingSeatIds = context.incomingSeatIds();

        for (InventoryZone zone : event.getZones()) {
            if (!zone.isAssigned()) {
                continue;
            }

            List<Seat> seats = zone.getSeats();
            if (seats.isEmpty()) {
                continue;
            }

            Map<String, List<Seat>> seatsByRow = seats.stream()
                    .collect(Collectors.groupingBy(Seat::getRow));

            for (List<Seat> rowSeats : seatsByRow.values()) {
                List<Seat> sorted = new ArrayList<>(rowSeats);
                sorted.sort(Comparator.comparingInt(s -> parseSeatNumber(s.getSeatNumber())));

                for (int i = 0; i < sorted.size(); i++) {
                    Seat seat = sorted.get(i);
                    if (!isFreeAfterSelection(seat, incomingSeatIds)) {
                        continue;
                    }

                    boolean leftTaken = (i == 0)
                            || !isFreeAfterSelection(sorted.get(i - 1), incomingSeatIds);
                    boolean rightTaken = (i == sorted.size() - 1)
                            || !isFreeAfterSelection(sorted.get(i + 1), incomingSeatIds);

                    if (leftTaken && rightTaken) {
                        return PolicyResult.failure("ORPHAN_SEAT",
                                "Your selection would leave seat "
                                        + seat.getRow() + "-" + seat.getSeatNumber()
                                        + " isolated. Please choose adjacent seats or adjust your selection.");
                    }
                }
            }
        }

        return PolicyResult.success();
    }

    private boolean isFreeAfterSelection(Seat seat, Set<UUID> incomingSeatIds) {
        if (!seat.isAvailable()) {
            return false;
        }
        return !incomingSeatIds.contains(seat.getId());
    }

    private int parseSeatNumber(String seatNumber) {
        try {
            return Integer.parseInt(seatNumber);
        } catch (NumberFormatException e) {
            return seatNumber.hashCode();
        }
    }
}
