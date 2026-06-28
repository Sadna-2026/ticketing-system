package com.ticketing.domain.gateway;

/**
 * One ticket to be issued by the external ticket-supply system (V3-18). {@code zoneId} is the
 * purchased zone (always present); {@code seatId} is the assigned seat for reserved seating and
 * {@code null} for general admission, where each GA ticket is expanded into its own request.
 */
public record TicketRequest(String eventId, String zoneId, String ticketId, String seatId, String seatRow, String seatNumber) {}
