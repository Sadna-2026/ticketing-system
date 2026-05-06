package com.ticketing.application;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenData;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.MemberMapper;
import com.ticketing.domain.member.StaffAppointment;
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

    /**
     * Retrieves the organizational hierarchy for a company.
     * Only accessible to members with the OWNER role in that company.
     */
    public List<OrgNodeDTO> getOrganizationChart(String token, String companyName) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name is required.");
        }

        UUID requestorId = validateTokenForChart(token);
        Member requestor = memberRepository.findById(requestorId)
                .orElseThrow(() -> new SecurityException("Authenticated member not found."));

        StaffAppointment appt = requestor.getStaffAppointment(companyName);
        if (appt == null || appt.getRole() != StaffAppointment.StaffRole.OWNER) {
            throw new SecurityException("Access denied. Only company owners can view the organization chart.");
        }

        List<Member> companyMembers = memberRepository.findByCompanyAppointment(companyName);
        if (companyMembers.isEmpty()) {
            return List.of();
        }

        // Precompute for performance O(n)
        Set<UUID> memberIdsInCompany = companyMembers.stream()
                .map(Member::getId)
                .collect(Collectors.toSet());

        Map<UUID, List<Member>> subordinatesByAppointer = companyMembers.stream()
                .filter(m -> {
                    StaffAppointment sa = m.getStaffAppointment(companyName);
                    return sa != null && sa.getAppointedByMemberId() != null;
                })
                .collect(Collectors.groupingBy(m -> m.getStaffAppointment(companyName).getAppointedByMemberId()));

        // Identify roots: no appointer, or appointer is not in the company
        List<Member> roots = companyMembers.stream()
                .filter(m -> {
                    StaffAppointment sa = m.getStaffAppointment(companyName);
                    UUID appointerId = sa.getAppointedByMemberId();
                    return appointerId == null || !memberIdsInCompany.contains(appointerId);
                })
                .sorted(Comparator.comparing(Member::getUsername))
                .toList();

        if (roots.isEmpty()) {
            logger.log(System.Logger.Level.ERROR, "Data inconsistency: Company " + companyName + " has members but no hierarchy roots.");
            throw new IllegalStateException("Organization hierarchy is corrupted: no roots found.");
        }

        return roots.stream()
                .map(root -> buildSubtree(root, subordinatesByAppointer, companyName))
                .collect(Collectors.toList());
    }

    private OrgNodeDTO buildSubtree(Member member, Map<UUID, List<Member>> subordinatesByAppointer, String companyName) {
        StaffAppointment appt = member.getStaffAppointment(companyName);
        
        List<OrgNodeDTO> subordinates = subordinatesByAppointer.getOrDefault(member.getId(), List.of()).stream()
                .sorted(Comparator.comparing(Member::getUsername))
                .map(m -> buildSubtree(m, subordinatesByAppointer, companyName))
                .collect(Collectors.toList());

        return new OrgNodeDTO(
                member.getId(),
                member.getUsername(),
                appt.getRole(),
                appt.getPermissions(),
                subordinates
        );
    }

    private UUID validateTokenForChart(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required.");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token.");
        }
        UUID memberId = sessionTokenService.extractMemberId(token);
        if (memberId == null) {
            throw new SecurityException("Guests cannot view the organization chart. Please log in.");
        }
        return memberId;
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