package com.ticketing.domain.event;


import com.ticketing.domain.member.User;
import com.ticketing.domain.order.ActiveOrder;

public interface IPurchasePolicy {
    PolicyResult isAllowed(ActiveOrder order, User user);
}