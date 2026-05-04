package com.ticketing.infrastructure.Interface;

import com.ticketing.domain.event.PolicyResult;


import com.ticketing.domain.member.User;
import com.ticketing.domain.order.ActiveOrder;

public interface IPurchasePolicy {
    PolicyResult isAllowed(ActiveOrder order, User user);
}

