package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ticketing.domain.order.OrderItem;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Rejects seat selections that leave a single isolated (orphan) free seat
 * in an assigned-seating row. Only applies to assigned-seating zones;
 * general-admission zones are ignored.
 */
@Entity
@DiscriminatorValue("NO_ORPHAN_SEAT")
public class NoOrphanSeatPolicy extends AbstractPurchasePolicy {

    public NoOrphanSeatPolicy() {
    }

    @Override
    public PolicyResult isAllowed(PurchaseContext context) {
        List<String> orphans = findOrphanSeatLabels(context);
        if (orphans.isEmpty()) {
            return PolicyResult.success();
        }
        return PolicyResult.failure("ORPHAN_SEAT", formatOrphanMessage(orphans));
    }

    private List<String> findOrphanSeatLabels(PurchaseContext context) {
        Event event = context.event();
        if (event == null) {
            return List.of();
        }

        Set<UUID> takenOrSelected = occupiedSeatIds(context);
        Set<UUID> becomingAvailable = context.seatsBecomingAvailable();
        List<String> orphans = new ArrayList<>();

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
                    if (!isFreeAfterSelection(seat, takenOrSelected, becomingAvailable)) {
                        continue;
                    }

                    boolean leftTaken = (i == 0)
                            || !isFreeAfterSelection(sorted.get(i - 1), takenOrSelected, becomingAvailable);
                    boolean rightTaken = (i == sorted.size() - 1)
                            || !isFreeAfterSelection(sorted.get(i + 1), takenOrSelected, becomingAvailable);

                    if (leftTaken && rightTaken) {
                        orphans.add(seat.getRow() + "-" + seat.getSeatNumber());
                    }
                }
            }
        }

        return orphans;
    }

    private static String formatOrphanMessage(List<String> orphanLabels) {
        String seatPhrase = orphanLabels.size() == 1
                ? "seat " + orphanLabels.get(0)
                : "seats " + String.join(", ", orphanLabels);
        return "Your selection would leave " + seatPhrase
                + " isolated. Please choose adjacent seats or adjust your selection.";
    }

    /**
     * Seat ids treated as occupied when checking for orphans:
     * <ul>
     *   <li><b>In cart</b> — assigned seats already on the active order</li>
     *   <li><b>Staged batch</b> — seats in {@code incomingSeatIds} (the current
     *       "Add selected seats" request, before inventory is locked)</li>
     * </ul>
     * Seats locked or sold by other buyers are excluded via {@link Seat#isAvailable()}.
     */
    private static Set<UUID> occupiedSeatIds(PurchaseContext context) {
        Set<UUID> occupied = new java.util.HashSet<>(context.incomingSeatIds());
        if (context.order() != null) {
            for (OrderItem item : context.order().getItems()) {
                if (item.isAssignedSeat() && item.getSeatId() != null) {
                    occupied.add(item.getSeatId());
                }
            }
        }
        return occupied;
    }

    private boolean isFreeAfterSelection(Seat seat, Set<UUID> occupied, Set<UUID> becomingAvailable) {
        if (becomingAvailable.contains(seat.getId())) {
            return true;
        }
        if (occupied.contains(seat.getId())) {
            return false;
        }
        return seat.isAvailable();
    }

    private int parseSeatNumber(String seatNumber) {
        try {
            return Integer.parseInt(seatNumber);
        } catch (NumberFormatException e) {
            return seatNumber.hashCode();
        }
    }
}
