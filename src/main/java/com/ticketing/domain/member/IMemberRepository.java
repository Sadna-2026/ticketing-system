package com.ticketing.domain.member;

import java.util.Optional;
import java.util.UUID;

public interface IMemberRepository {
    Optional<Member> findById(UUID memberId);
    
    void save(Member member);
    
    Optional<Member> findByUsername(String username);
    
    Optional<Member> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
}

