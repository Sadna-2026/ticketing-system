package com.ticketing;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ticketing.application.ISystemClock;
import com.ticketing.domain.system.StartupConfiguration;

@Configuration
public class TicketingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ISystemClock systemClock(Clock clock) {
        return clock::instant;
    }

    @Bean
    public StartupConfiguration startupConfiguration(
            @org.springframework.beans.factory.annotation.Value("${ticketing.admin.username}") String adminUsername,
            @org.springframework.beans.factory.annotation.Value("${ticketing.admin.password}") String adminPassword
    ) {
        return new StartupConfiguration(
                adminUsername, 
                "admin@ticketing.local", 
                adminPassword, 
                true, 
                true
        );
    }
}
