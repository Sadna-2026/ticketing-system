package com.ticketing.domain.member;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for the Member aggregate root.
 */
public interface IMemberRepository {

    Optional<Member> findById(UUID id);

}