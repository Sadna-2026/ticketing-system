package com.ticketing.domain.event;

import java.util.UUID;

import com.ticketing.domain.order.ActiveOrder;

public interface IPurchasePolicy {
    PolicyResult isAllowed(ActiveOrder order, UUID memberId);
}

