package com.ticketing.infrastructure;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.member.Member;
import com.ticketing.infrastructure.Interface.IMemberRepository;

@Repository
public class InMemoryMemberRepository implements IMemberRepository {

    private final ConcurrentHashMap<UUID, Member> membersById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> idsByUsername = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> idsByEmail = new ConcurrentHashMap<>();

    @Override
    public synchronized boolean saveIfUsernameAndEmailAvailable(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }

        if (idsByUsername.containsKey(member.getUsername())) {
            return false;
        }

        if (idsByEmail.containsKey(member.getEmail())) {
            return false;
        }

        membersById.put(member.getId(), member);
        idsByUsername.put(member.getUsername(), member.getId());
        idsByEmail.put(member.getEmail(), member.getId());

        return true;
    }

    @Override
    public Optional<Member> findById(UUID memberId) {
        if (memberId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(membersById.get(memberId));
    }

    @Override
    public Optional<Member> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }

        UUID memberId = idsByUsername.get(username);

        if (memberId == null) {
            return Optional.empty();
        }

        return findById(memberId);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }

        UUID memberId = idsByEmail.get(email);

        if (memberId == null) {
            return Optional.empty();
        }

        return findById(memberId);
    }

    @Override
    public boolean existsByUsername(String username) {
        return username != null && idsByUsername.containsKey(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && idsByEmail.containsKey(email);
    }

    @Override
    public long count() {
        return membersById.size();
    }
}