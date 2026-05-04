package com.ticketing.infrastructure.Interface;

import java.util.UUID;

import com.ticketing.domain.event.PolicyResult;
import com.ticketing.domain.order.ActiveOrder;

public interface IPurchasePolicy {
    PolicyResult isAllowed(ActiveOrder order, UUID memberId);
}

