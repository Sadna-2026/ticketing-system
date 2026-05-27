package com.ticketing.presentation.vaadin.presenters;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.MemberDto;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
import com.ticketing.presentation.vaadin.util.SessionContext;

@Component
public class MemberPresenter {

    private static final Logger logger = LoggerFactory.getLogger(MemberPresenter.class);

    private final MemberService memberService;

    public MemberPresenter(MemberService memberService) {
        this.memberService = memberService;
    }

    public MemberDto getCurrentMember() {
        String sessionToken = SessionContext.getSessionToken();
        if (sessionToken == null || sessionToken.isBlank()) {
            return null;
        }
        return memberService.getMemberDetails(sessionToken);
    }

    public UpdateResult updateIdentifyingDetails(String username, String email, String phoneNumber, LocalDate dateOfBirth) {
        String sessionToken = SessionContext.getSessionToken();
        if (sessionToken == null || sessionToken.isBlank() || !SessionContext.isLoggedInMember()) {
            return UpdateResult.failure("No authenticated member session exists.");
        }

        try {
            UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(username, email, phoneNumber, dateOfBirth);
            UpdateMemberDetailsResponse response = memberService.updateIdentifyingDetails(sessionToken, SessionContext.getMemberId(), request);
            if (!response.success()) {
                return UpdateResult.failure(response.message());
            }

            // Update session context if username changed
            if (username != null && !username.isBlank()) {
                SessionContext.setUsername(username);
            }
            return UpdateResult.success("Profile updated successfully.");
        } catch (RuntimeException ex) {
            logger.error("Profile update failed.", ex);
            return UpdateResult.failure("Profile update failed. Please try again.");
        }
    }

    public record UpdateResult(boolean success, String message) {
        public static UpdateResult success(String message) {
            return new UpdateResult(true, message);
        }

        public static UpdateResult failure(String message) {
            return new UpdateResult(false, message);
        }
    }
}