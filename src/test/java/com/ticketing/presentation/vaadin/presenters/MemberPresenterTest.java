package com.ticketing.presentation.vaadin.presenters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;

import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.MemberDto;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
import com.ticketing.presentation.vaadin.presenters.MemberPresenter.UpdateResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

@DisplayName("MemberPresenter")
@ExtendWith(VaadinSessionExtension.class)
class MemberPresenterTest {

    private MemberService memberService;
    private MemberPresenter presenter;

    @BeforeEach
    void setUp() {
        memberService = mock(MemberService.class);
        presenter = new MemberPresenter(memberService);
    }

    @Test
    void GivenMemberSession_WhenGettingCurrentMember_ThenServiceIsCalledWithSessionToken() {
        UUID memberId = memberSession();
        MemberDto member = memberDto(memberId);
        when(memberService.getMemberDetails("member-token")).thenReturn(member);

        assertSame(member, presenter.getCurrentMember());
        verify(memberService).getMemberDetails("member-token");
    }

    @Test
    void GivenNoSession_WhenGettingCurrentMember_ThenNullIsReturnedWithoutCallingService() {
        assertNull(presenter.getCurrentMember());
        verifyNoInteractions(memberService);
    }

    @Test
    void GivenMemberSession_WhenUpdatingIdentifyingDetails_ThenServiceIsCalledAndSuccessIsReturned() {
        UUID memberId = memberSession();
        LocalDate dateOfBirth = LocalDate.of(1995, 5, 15);
        MemberDto updated = memberDto(memberId, "member-updated", "updated@ticketing.local");
        when(memberService.updateIdentifyingDetails(eq("member-token"), eq(memberId), any()))
                .thenReturn(UpdateMemberDetailsResponse.success(updated));

        UpdateResult result = presenter.updateIdentifyingDetails(
                "member-updated",
                "updated@ticketing.local",
                "050-111-2222",
                dateOfBirth
        );

        ArgumentCaptor<UpdateMemberDetailsRequest> request = ArgumentCaptor.forClass(UpdateMemberDetailsRequest.class);
        verify(memberService).updateIdentifyingDetails(eq("member-token"), eq(memberId), request.capture());
        assertEquals("member-updated", request.getValue().username());
        assertEquals("updated@ticketing.local", request.getValue().email());
        assertEquals("050-111-2222", request.getValue().phoneNumber());
        assertEquals(dateOfBirth, request.getValue().dateOfBirth());
        assertTrue(result.success());
        assertEquals("Profile updated successfully.", result.message());
        assertEquals("member-updated", SessionContext.getUsername());
    }

    @Test
    void GivenGuestSession_WhenUpdatingIdentifyingDetails_ThenFailureIsReturnedWithoutCallingService() {
        SessionContext.setSessionToken("guest-token");

        UpdateResult result = presenter.updateIdentifyingDetails(
                "guest",
                "guest@ticketing.local",
                "050-000-0000",
                LocalDate.of(1990, 1, 1)
        );

        assertFalse(result.success());
        assertEquals("No authenticated member session exists.", result.message());
        verifyNoInteractions(memberService);
    }

    @Test
    void GivenServiceRejectsUpdate_WhenUpdatingIdentifyingDetails_ThenSpecificFailureReasonIsReturned() {
        memberSession();
        when(memberService.updateIdentifyingDetails(eq("member-token"), any(), any()))
                .thenReturn(UpdateMemberDetailsResponse.failure("Username or email already in use."));

        UpdateResult result = presenter.updateIdentifyingDetails(
                "owner",
                "owner@ticketing.local",
                "050-000-0003",
                LocalDate.of(1988, 8, 20)
        );

        assertFalse(result.success());
        assertEquals("Username or email already in use.", result.message());
    }

    @Test
    void GivenServiceThrows_WhenUpdatingIdentifyingDetails_ThenGenericFailureMessageIsReturned() {
        memberSession();
        doThrow(new IllegalStateException("boom"))
                .when(memberService).updateIdentifyingDetails(eq("member-token"), any(), any());

        UpdateResult result = presenter.updateIdentifyingDetails(
                "member",
                "member@ticketing.local",
                "050-000-0002",
                LocalDate.of(1995, 5, 15)
        );

        assertFalse(result.success());
        assertEquals("Profile update failed. Please try again.", result.message());
    }

    private static UUID memberSession() {
        UUID memberId = UUID.randomUUID();
        SessionContext.setSessionToken("member-token");
        SessionContext.setMemberId(memberId);
        SessionContext.setUsername("alice");
        return memberId;
    }

    private static MemberDto memberDto(UUID memberId) {
        return memberDto(memberId, "alice", "alice@example.com");
    }

    private static MemberDto memberDto(UUID memberId, String username, String email) {
        return new MemberDto(memberId, username, email, "050-1234567", LocalDate.of(1990, 1, 1));
    }
}
