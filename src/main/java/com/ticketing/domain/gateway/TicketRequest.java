package com.ticketing.domain.gateway;

public record TicketRequest(String eventId, String ticketId, String seatId) {}
