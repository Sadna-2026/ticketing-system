package com.ticketing;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketingConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
