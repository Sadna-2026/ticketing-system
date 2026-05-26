package com.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;

@SpringBootApplication
@Push
public class TicketingApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }

}
