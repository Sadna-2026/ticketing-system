package com.ticketing.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.Interface.ISessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

class MemberServiceUpdateIdentifyingDetailsTest {

    private IMemberRepository memberRepository;
    private SessionTokenService sessionTokenService;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        String secret = Base64.getEncoder().encodeToString(
                "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
        );

        ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

        memberRepository = new InMemoryMemberRepository();
        PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();

        sessionTokenService = new SessionTokenService(
                secret,
                120,
                tokenRepository
        );

        memberService = new MemberService(
                memberRepository,
                passwordEncryptionUtils,
                sessionTokenService
        );
    }

    @Test
    @DisplayName("Member updates own details successfully")
    void GivenLoggedInMember_WhenUpdateOwnDetails_ThenDetailsUpdatedAndDtoReturned() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        UUID memberId = registerResponse.memberId();

        UpdateMemberDetailsRequest request = updateRequest(
                "tamar-new",
                "new@example.com",
                "0527654321",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

        // Assert
        assertTrue(response.success());
        assertEquals("Member details updated successfully.", response.message());
        assertNotNull(response.member());
        assertEquals(memberId, response.member().memberId());
        assertEquals("tamar-new", response.member().username());
        assertEquals("new@example.com", response.member().email());
        assertEquals("0527654321", response.member().phoneNumber());
        assertEquals(LocalDate.parse("1999-05-06"), response.member().dateOfBirth());

        Member savedMember = memberRepository.findById(memberId).orElseThrow();
        assertEquals("tamar-new", savedMember.getUsername());
        assertEquals("new@example.com", savedMember.getEmail());
        assertEquals("0527654321", savedMember.getPhoneNumber());
        assertEquals(LocalDate.parse("1999-05-06"), savedMember.getDateOfBirth());
    }

    @Test
    @DisplayName("Member updates only username successfully")
    void GivenLoggedInMember_WhenUpdateOnlyUsername_ThenOtherDetailsRemainUnchanged() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        UUID memberId = registerResponse.memberId();

        UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(
                "tamar-new",
                null,
                null,
                (LocalDate) null
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

        // Assert
        assertTrue(response.success());
        assertEquals("tamar-new", response.member().username());
        assertEquals("tamar@example.com", response.member().email());
        assertEquals("0501234567", response.member().phoneNumber());
        assertEquals(LocalDate.parse("2000-01-01"), response.member().dateOfBirth());
    }

    @Test
    @DisplayName("Member updates only email successfully")
    void GivenLoggedInMember_WhenUpdateOnlyEmail_ThenOtherDetailsRemainUnchanged() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
        UUID memberId = registerResponse.memberId();

        UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(
                null,
                "new@example.com",
                null,
                (LocalDate) null
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

        // Assert
        assertTrue(response.success());
        assertEquals("tamar", response.member().username());
        assertEquals("new@example.com", response.member().email());
        assertEquals("0501234567", response.member().phoneNumber());
        assertEquals(LocalDate.parse("2000-01-01"), response.member().dateOfBirth());
    }

    @Test
    @DisplayName("Cannot update another member's details")
    void GivenLoggedInMember_WhenUpdateAnotherMemberDetails_ThenDenied() {
        // Arrange
        RegisterResponse tamar = registerMember("tamar", "tamar@example.com");
        RegisterResponse other = registerMember("other", "other@example.com");

        UpdateMemberDetailsRequest request = updateRequest(
                "hijacked",
                "hijacked@example.com",
                "0527654321",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(tamar.sessionToken(), other.memberId(), request);

        // Assert
        assertFalse(response.success());
        assertEquals("Members can only update their own details.", response.message());
        assertNull(response.member());

        Member otherMember = memberRepository.findById(other.memberId()).orElseThrow();
        assertEquals("other", otherMember.getUsername());
        assertEquals("other@example.com", otherMember.getEmail());
    }

    @Test
    @DisplayName("Invalid/empty fields rejected")
    void GivenInvalidFields_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");

        UpdateMemberDetailsRequest request = updateRequest(
                "",
                "bad-email",
                " ",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(
                        registerResponse.sessionToken(),
                        registerResponse.memberId(),
                        request
                );

        // Assert
        assertFalse(response.success());
        assertEquals("Invalid member details.", response.message());
        assertNull(response.member());

        Member savedMember = memberRepository.findById(registerResponse.memberId()).orElseThrow();
        assertEquals("tamar", savedMember.getUsername());
        assertEquals("tamar@example.com", savedMember.getEmail());
    }

    @Test
    @DisplayName("Duplicate username rejected")
    void GivenDuplicateUsername_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
        // Arrange
        registerMember("existing", "existing@example.com");
        RegisterResponse tamar = registerMember("tamar", "tamar@example.com");

        UpdateMemberDetailsRequest request = updateRequest(
                "existing",
                "new@example.com",
                "0527654321",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(tamar.sessionToken(), tamar.memberId(), request);

        // Assert
        assertFalse(response.success());
        assertEquals("Username or email already in use.", response.message());
        assertNull(response.member());

        Member savedMember = memberRepository.findById(tamar.memberId()).orElseThrow();
        assertEquals("tamar", savedMember.getUsername());
        assertEquals("tamar@example.com", savedMember.getEmail());
    }

    @Test
    @DisplayName("Duplicate email rejected")
    void GivenDuplicateEmail_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
        // Arrange
        registerMember("existing", "existing@example.com");
        RegisterResponse tamar = registerMember("tamar", "tamar@example.com");

        UpdateMemberDetailsRequest request = updateRequest(
                "tamar-new",
                "existing@example.com",
                "0527654321",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(tamar.sessionToken(), tamar.memberId(), request);

        // Assert
        assertFalse(response.success());
        assertEquals("Username or email already in use.", response.message());
        assertNull(response.member());

        Member savedMember = memberRepository.findById(tamar.memberId()).orElseThrow();
        assertEquals("tamar", savedMember.getUsername());
        assertEquals("tamar@example.com", savedMember.getEmail());
    }

    @Test
    @DisplayName("Returns DTO, not domain object")
    void GivenSuccessfulUpdate_WhenInspectResponse_ThenResponseContainsMemberDtoOnly() {
        // Arrange
        RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");

        UpdateMemberDetailsRequest request = updateRequest(
                "tamar-new",
                "new@example.com",
                "0527654321",
                "1999-05-06"
        );

        // Act
        UpdateMemberDetailsResponse response =
                memberService.updateIdentifyingDetails(
                        registerResponse.sessionToken(),
                        registerResponse.memberId(),
                        request
                );

        // Assert
        assertTrue(response.success());
        assertNotNull(response.member());
        assertEquals("MemberDto", response.member().getClass().getSimpleName());
    }

    private RegisterResponse registerMember(String username, String email) {
        String guestToken = sessionTokenService.generateGuestToken();

        RegisterRequest request = new RegisterRequest(
                username,
                email,
                "123456",
                "0501234567",
                "2000-01-01"
        );

        RegisterResponse response = memberService.register(request, guestToken);

        assertTrue(response.success());
        assertNotNull(response.sessionToken());

        return response;
    }

    private UpdateMemberDetailsRequest updateRequest(
            String username,
            String email,
            String phoneNumber,
            String dateOfBirth
    ) {
        return new UpdateMemberDetailsRequest(username, email, phoneNumber, dateOfBirth);
    }
}
