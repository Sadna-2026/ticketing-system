package com.ticketing.application;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.Interface.IActiveOrderRepository;
import com.ticketing.infrastructure.Interface.IEventRepository;
import com.ticketing.infrastructure.Interface.IPaymentGateway;
import com.ticketing.infrastructure.Interface.ITicketSupplyGateway;

/**
 * @deprecated Use {@link OrderService}. Kept as a compatibility adapter while
 * callers migrate from the old active/completed split.
 */
@Deprecated
public class ActiveOrderService extends OrderService {

    public ActiveOrderService(IActiveOrderRepository orderRepository,
                              ISessionTokenService sessionTokenService,
                              IEventRepository eventRepository,
                              ISystemClock systemClock) {
        super(orderRepository, sessionTokenService, eventRepository, systemClock);
    }

    public ActiveOrderService(IActiveOrderRepository orderRepository,
                              ISessionTokenService sessionTokenService,
                              IEventRepository eventRepository,
                              ISystemClock systemClock,
                              IMemberRepository memberRepository,
                              IPaymentGateway paymentGateway,
                              ITicketSupplyGateway ticketSupplyGateway) {
        super(orderRepository, sessionTokenService, eventRepository, systemClock,
                memberRepository, paymentGateway, ticketSupplyGateway);
    }
}
