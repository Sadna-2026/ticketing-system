package com.ticketing.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.MemberMapper;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.MemberExitResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;
@Service
public class MemberService {

    
    private final IMemberRepository memberRepository;
    private final PasswordEncryptionUtils passwordEncryptionUtils;
    private final ISessionTokenService sessionTokenService;
    private static final System.Logger logger = System.getLogger(MemberService.class.getName());

    public MemberService(
            IMemberRepository memberRepository,
            PasswordEncryptionUtils passwordEncryptionUtils,
            ISessionTokenService sessionTokenService
    ) {
        if (memberRepository == null) {
            throw new IllegalArgumentException("memberRepository cannot be null");
        }

        if (passwordEncryptionUtils == null) {
            throw new IllegalArgumentException("passwordEncryptionUtils cannot be null");
        }

        if (sessionTokenService == null) {
            throw new IllegalArgumentException("sessionTokenService cannot be null");
        }

        this.memberRepository = memberRepository;
        this.passwordEncryptionUtils = passwordEncryptionUtils;
        this.sessionTokenService = sessionTokenService;

    }

    public RegisterResponse register(RegisterRequest request, String guestToken) {
        if (guestToken == null || guestToken.isBlank()) {
            return RegisterResponse.failure("Invalid session token.");
        }

        if (!sessionTokenService.isValid(guestToken)) {
            return RegisterResponse.failure("Invalid session token.");
        }

        UUID tokenMemberId = sessionTokenService.extractMemberId(guestToken);

        if (tokenMemberId != null) {
            return RegisterResponse.failure("Only guests can register.");
        }

        if (!isValidRegisterRequest(request)) {
            return RegisterResponse.failure("Invalid registration details.");
        }

        String username = normalizeUsername(request.username());
        String email = normalizeEmail(request.email());

        if (memberRepository.existsByUsername(username)) {
            logger.log(System.Logger.Level.WARNING, "Failed to save new member: " + username);
            return RegisterResponse.failure("Username already in use.");
        }

        if (memberRepository.existsByEmail(email)) {
            logger.log(System.Logger.Level.WARNING, "Failed to save new member: " + username);
            return RegisterResponse.failure("Email already in use.");
        }

        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        UUID newMemberId = UUID.randomUUID();

        String hashedPassword = passwordEncryptionUtils.hashPassword(request.password());

        Member member = new Member(
                newMemberId,
                username,
                email,
                hashedPassword
        );

        boolean saved = memberRepository.saveIfUsernameAndEmailAvailable(member);

        if (!saved) {
            logger.log(System.Logger.Level.WARNING, "Failed to save new member: " + username);
            return RegisterResponse.failure("Registration details already in use.");
        }

        String memberToken = sessionTokenService.generateMemberToken(
                sessionId,
                newMemberId,
                Set.of()
        );
       

        logger.log(System.Logger.Level.INFO, "New member registered: " + username );
        return RegisterResponse.success(MemberMapper.toDto(member), memberToken);
    }


    public LogoutResponse logout(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return LogoutResponse.failure("No authenticated member session exists.");
        }

        if (!sessionTokenService.isValid(sessionToken)) {
            return LogoutResponse.failure("No authenticated member session exists.");
        }

        UUID memberId = sessionTokenService.extractMemberId(sessionToken);

        if (memberId == null) {
            return LogoutResponse.failure("No authenticated member session exists.");
        }

        String guestToken = sessionTokenService.logout(sessionToken);

        logger.log(System.Logger.Level.INFO, "Member logged out: " + memberId);

        return LogoutResponse.success(guestToken);
    }

    public MemberExitResponse exitPlatform(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return MemberExitResponse.failure("No authenticated member session exists.");
        }

        if (!sessionTokenService.isValid(sessionToken)) {
            return MemberExitResponse.failure("No authenticated member session exists.");
        }

        UUID memberId = sessionTokenService.extractMemberId(sessionToken);
        SessionTokenData tokenData = sessionTokenService.extractTokenData(sessionToken);

        if (memberId == null) {
            return MemberExitResponse.failure("No authenticated member session exists.");
        }

        sessionTokenService.revokeToken(sessionToken);

        logger.log(System.Logger.Level.INFO, "exited platform: " + tokenData.getUsername());

        return MemberExitResponse.successResponse(tokenData.getUsername());
    }

    private boolean isValidRegisterRequest(RegisterRequest request) {
        return request != null
                && request.username() != null
                && !request.username().isBlank()
                && request.email() != null
                && !request.email().isBlank()
                && request.email().contains("@")
                && request.password() != null
                && request.password().length() >= 6;
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}