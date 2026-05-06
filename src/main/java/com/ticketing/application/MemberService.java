package com.ticketing.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.MemberMapper;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.MemberExitResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
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
                hashedPassword,
                request.phoneNumber()
                ,request.dateOfBirth()
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

    public UpdateMemberDetailsResponse updateIdentifyingDetails(
            String sessionToken,
            UUID memberId,
            UpdateMemberDetailsRequest request
    ) {
        if (!sessionTokenService.isValid(sessionToken)) {
            logger.log(System.Logger.Level.WARNING, "Failed to update member details: invalid session token");
            return UpdateMemberDetailsResponse.failure("No authenticated member session exists.");
        }

        UUID authenticatedMemberId = sessionTokenService.extractMemberId(sessionToken);
        if (authenticatedMemberId == null) {
            logger.log(System.Logger.Level.WARNING, "Failed to update member details: guest session cannot update member details");
            return UpdateMemberDetailsResponse.failure("No authenticated member session exists.");
        }

        if (memberId == null || !authenticatedMemberId.equals(memberId)) {
            logger.log(
                    System.Logger.Level.WARNING,
                    "Failed to update member details: member " + authenticatedMemberId
                            + " attempted to update member " + memberId
            );
            return UpdateMemberDetailsResponse.failure("Members can only update their own details.");
        }

        if (!isValidUpdateMemberDetailsRequest(request)) {
            logger.log(System.Logger.Level.WARNING, "Failed to update member details: invalid details for member " + memberId);
            return UpdateMemberDetailsResponse.failure("Invalid member details.");
        }

        Member member = memberRepository.findById(memberId).orElse(null);

        if (member == null) {
            logger.log(System.Logger.Level.WARNING, "Failed to update member details: member not found " + memberId);
            return UpdateMemberDetailsResponse.failure("Member not found.");
        }

        String username = request.username() == null ? member.getUsername() : request.username();
        String email = request.email() == null ? member.getEmail() : request.email();

        boolean updated = memberRepository.updateIfUsernameAndEmailAvailable(member, username, email);
        if (!updated) {
            logger.log(System.Logger.Level.WARNING, "Failed to update member details: duplicate username or email for member " + memberId);
            return UpdateMemberDetailsResponse.failure("Username or email already in use.");
        }

        if (request.phoneNumber() != null) {
            member.updatePhoneNumber(request.phoneNumber().trim());
        }
        if (request.dateOfBirth() != null) {
            member.updateDateOfBirth(request.dateOfBirth());
        }
        memberRepository.save(member);

        logger.log(System.Logger.Level.INFO, "Member details updated: " + memberId);
        return UpdateMemberDetailsResponse.success(MemberMapper.toDto(member));
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

    private boolean isValidUpdateMemberDetailsRequest(UpdateMemberDetailsRequest request) {
        return request != null
                && hasAnyUpdateMemberDetailsChange(request)
                && (request.username() == null || !request.username().isBlank())
                && (request.email() == null || (!request.email().isBlank() && request.email().contains("@")))
                && (request.phoneNumber() == null || !request.phoneNumber().isBlank());
    }

    private boolean hasAnyUpdateMemberDetailsChange(UpdateMemberDetailsRequest request) {
        return request.username() != null
                || request.email() != null
                || request.phoneNumber() != null
                || request.dateOfBirth() != null;
    }

    private String normalizeUsername(String username) {
        return username.trim();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}