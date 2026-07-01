package com.ticketing.application.initialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.notification.IPendingNotificationRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.queue.IQueueRepository;

@Component
public class OperationalDataWiper {

    private static final Logger log = LoggerFactory.getLogger(OperationalDataWiper.class);

    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;
    private final ILotteryRepository lotteryRepository;
    private final IMemberRepository memberRepository;
    private final IPendingNotificationRepository pendingNotificationRepository;
    private final IOrderRepository orderRepository;
    private final IQueueRepository queueRepository;

    public OperationalDataWiper(
            ICompanyRepository companyRepository,
            IEventRepository eventRepository,
            ILotteryRepository lotteryRepository,
            IMemberRepository memberRepository,
            IPendingNotificationRepository pendingNotificationRepository,
            IOrderRepository orderRepository,
            IQueueRepository queueRepository
    ) {
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
        this.lotteryRepository = lotteryRepository;
        this.memberRepository = memberRepository;
        this.pendingNotificationRepository = pendingNotificationRepository;
        this.orderRepository = orderRepository;
        this.queueRepository = queueRepository;
    }

    @Transactional
    public void wipeAll() {
        log.warn("Wiping all operational data...");
        try {
            orderRepository.deleteAll();
            lotteryRepository.deleteAll();
            pendingNotificationRepository.deleteAll();
            queueRepository.deleteAll();
            eventRepository.deleteAll();
            companyRepository.deleteAll();
            memberRepository.deleteAll();
            log.info("Operational data wiped successfully.");
        } catch (Exception ex) {
            log.error("Failed to wipe operational data: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
