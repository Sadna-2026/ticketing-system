package com.ticketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@SpringBootApplication
@EnableScheduling
@Push
@Theme(value = "showpass", variant = Lumo.DARK)
public class TicketingApplication implements AppShellConfigurator {

    @Override
    public void configurePage(AppShellSettings settings) {
        // Brand fonts: Sora (display) + Inter (body/data). Loaded at the
        // document level so they're available to component shadow roots too.
        settings.addLink("preconnect", "https://fonts.googleapis.com");
        settings.addLink("preconnect", "https://fonts.gstatic.com");
        settings.addLink("stylesheet",
                "https://fonts.googleapis.com/css2?family=Sora:wght@600;700&family=Inter:wght@400;500;600&display=swap");
    }

    /**
     * Scheduler backing the automatic lottery draw (and any other @Scheduled work). A small
     * pool is enough — draws are brief and infrequent. Each lottery event gets a one-shot task
     * fired at its registration-close instant by {@code LotteryDrawScheduler}.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("lottery-draw-");
        scheduler.initialize();
        return scheduler;
    }

    public static void main(String[] args) {
        SpringApplication.run(TicketingApplication.class, args);
    }

}
