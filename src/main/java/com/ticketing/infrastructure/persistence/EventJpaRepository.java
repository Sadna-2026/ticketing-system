package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.domain.event.Event;

/**
 * Spring Data JPA repository for the {@link Event} aggregate root.
 * Provides the derived queries the {@link JpaEventRepository} adapter needs to
 * satisfy the {@code IEventRepository} contract. Only instantiated when the JPA
 * persistence profile is active (see {@link JpaEventRepository}).
 */
public interface EventJpaRepository extends JpaRepository<Event, UUID> {

    List<Event> findByCompanyName(String companyName);
}
