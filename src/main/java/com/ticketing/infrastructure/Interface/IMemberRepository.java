package com.ticketing.infrastructure.Interface;

import java.util.Optional;
import java.util.UUID;

import com.ticketing.domain.member.Member;


public interface IMemberRepository {

    boolean saveIfUsernameAndEmailAvailable(Member member);

    Optional<Member> findById(UUID memberId);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    long count();
}
