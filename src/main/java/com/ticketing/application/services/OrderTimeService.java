package com.ticketing.application.services;

import org.springframework.stereotype.Service;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.OrderTimeDomainService;

@Service
public class OrderTimeService {

    private final OrderTimeDomainService domainService;

    // Backward compatibility constructor
    public OrderTimeService(IOrderRepository orderRepository,
                            IEventRepository eventRepository,
                            ISystemClock systemClock) {
        this(new OrderTimeDomainService(orderRepository, eventRepository, systemClock));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OrderTimeService(OrderTimeDomainService domainService) {
        this.domainService = domainService;
    }

    public void expireOrders() {
        domainService.expireOrders();
    }
}
