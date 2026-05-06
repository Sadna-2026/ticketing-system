package com.ticketing.domain.event;

/**
 * Determines how tickets for an event are sold.
 * REGULAR — first-come-first-served open sale.
 * LOTTERY — members pre-register; winners receive a purchase-right code.
 */
public enum SaleMethod {
    REGULAR,
    LOTTERY
}
