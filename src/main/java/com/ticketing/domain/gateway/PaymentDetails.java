package com.ticketing.domain.gateway;

import java.util.UUID;

public record PaymentDetails(UUID orderId, UUID eventId, UUID memberId, String email) {}
