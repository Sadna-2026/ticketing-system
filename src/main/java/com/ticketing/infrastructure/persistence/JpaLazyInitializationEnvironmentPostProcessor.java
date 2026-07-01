package com.ticketing.infrastructure.persistence;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Req 5: enable Spring lazy bean initialization in JPA mode so EMF/repository bootstrap
 * does not run during context refresh when the database is temporarily unreachable at boot.
 */
public class JpaLazyInitializationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!"jpa".equals(environment.getProperty("ticketing.persistence"))) {
            return;
        }
        if (Boolean.parseBoolean(environment.getProperty("spring.main.lazy-initialization", "false"))) {
            return;
        }
        environment.getPropertySources().addFirst(new MapPropertySource(
                "ticketingJpaLazyInitialization",
                Map.of("spring.main.lazy-initialization", true)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
