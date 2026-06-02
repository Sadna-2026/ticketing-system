package com.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;

@SpringBootApplication
@EnableScheduling
@Push
public class TicketingApplication implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addLink("stylesheet", "styles/required-fields.css");
    }

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }

}
