package com.ticketing.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.domain.member.Member;

/**
 * Spring Data JPA repository for the {@link Member} aggregate root.
 * Provides the derived/JPQL queries the {@link JpaMemberRepository} adapter
 * needs to satisfy the {@code IMemberRepository} contract. Not a Spring bean
 * by itself outside the JPA persistence profile — it is only instantiated when
 * the JPA adapter is active (see {@link JpaMemberRepository}).
 */
public interface MemberJpaRepository extends JpaRepository<Member, UUID> {

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    /**
     * Members that hold a staff appointment for the given company key.
     * StaffAppointments are mapped as an element/entity collection keyed by the
     * company key, so a join over the collection is required.
     */
    @Query("select distinct m from Member m join m.staffAppointments sa where key(sa) = :companyKey")
    List<Member> findByCompanyAppointmentKey(@Param("companyKey") String companyKey);
}
