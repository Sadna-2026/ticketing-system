package com.ticketing.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

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

        String username = normalizeUsername(member.getUsername());
        String email = normalizeEmail(member.getEmail());

        if (idsByUsername.containsKey(username)) {
            return false;
        }

        if (idsByEmail.containsKey(email)) {
            return false;
        }

        membersById.put(member.getId(), member);
        idsByUsername.put(username, member.getId());
        idsByEmail.put(email, member.getId());

        return true;
    }

    @Override
    public synchronized boolean updateIfUsernameAndEmailAvailable(Member member, String username, String email) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email cannot be null or blank");
        }

        String newUsername = normalizeUsername(username);
        String newEmail = normalizeEmail(email);
        Optional<Member> usernameOwner = findByUsername(newUsername);
        Optional<Member> emailOwner = findByEmail(newEmail);

        if (usernameOwner.isPresent() && !usernameOwner.get().getId().equals(member.getId())) {
            return false;
        }
        if (emailOwner.isPresent() && !emailOwner.get().getId().equals(member.getId())) {
            return false;
        }

        idsByUsername.remove(normalizeUsername(member.getUsername()), member.getId());
        idsByEmail.remove(normalizeEmail(member.getEmail()), member.getId());

        member.updateUsername(newUsername);
        member.updateEmail(newEmail);

        save(member);

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

        UUID memberId = idsByUsername.get(normalizeUsername(username));
        return memberId == null ? Optional.empty() : findById(memberId);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }

        UUID memberId = idsByEmail.get(normalizeEmail(email));
        return memberId == null ? Optional.empty() : findById(memberId);
    }

    @Override
    public boolean existsByUsername(String username) {
        return username != null && idsByUsername.containsKey(normalizeUsername(username));
    }

    @Override
    public boolean existsByEmail(String email) {
        return email != null && idsByEmail.containsKey(normalizeEmail(email));
    }

    @Override
    public long count() {
        return membersById.size();
    }

    @Override
    public List<Member> findByCompanyAppointment(String companyName) {
        if (companyName == null) return List.of();
        List<Member> hits = new ArrayList<>();
        for (Member m : membersById.values()) {
            if (m.getStaffAppointment(companyName) != null) {
                hits.add(m);
            }
        }
        return hits;
    }

    @Override
    public void save(Member member) {
        if (member == null) {
            throw new IllegalArgumentException("member cannot be null");
        }
        membersById.put(member.getId(), member);
        idsByUsername.put(normalizeUsername(member.getUsername()), member.getId());
        idsByEmail.put(normalizeEmail(member.getEmail()), member.getId());
    }

    @Override
    public synchronized void delete(Member member) {
        if (member == null) return;
        membersById.remove(member.getId());
        idsByUsername.remove(normalizeUsername(member.getUsername()));
        idsByEmail.remove(normalizeEmail(member.getEmail()));
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
