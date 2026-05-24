package com.ticketing;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.notification.IPendingNotificationRepository;
import com.ticketing.infrastructure.notification.InMemoryPendingNotificationRepository;
import com.ticketing.infrastructure.notification.WebSocketNotificationService;

@Configuration
public class TicketingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IPendingNotificationRepository pendingNotificationRepository() {
        return new InMemoryPendingNotificationRepository();
    }

    @Bean
    public WebSocketNotificationService webSocketNotificationService(
            IPendingNotificationRepository pendingNotificationRepository) {
        return new WebSocketNotificationService(pendingNotificationRepository);
    }

    @Bean
    public INotificationService notificationService(
            WebSocketNotificationService webSocketNotificationService) {
        return webSocketNotificationService;
    }
}
