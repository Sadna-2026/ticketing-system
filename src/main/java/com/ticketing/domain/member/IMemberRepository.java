package com.ticketing.domain.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IMemberRepository {
    Optional<Member> findById(UUID memberId);

    void save(Member member);

    Optional<Member> findByUsername(String username);

    Optional<Member> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean saveIfUsernameAndEmailAvailable(Member member);

    long count();

    List<Member> findByCompanyAppointment(String companyName);

    void delete(Member member);
}

