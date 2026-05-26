package com.ticketing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Development security configuration for the browser UI.
 *
 * The current project performs authorization in the application layer using the
 * session token services. This configuration prevents Spring Security's default
 * login page from blocking the initial Vaadin UI foundation.
 *
 * Later V2 issues can replace this with stricter route-level protection.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
