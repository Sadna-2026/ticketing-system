package com.ticketing.presentation.vaadin.presenters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataAccessResourceFailureException;

import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.CompletedPurchaseService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter.ActionResult;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;

/**
 * End-to-end check for #516: when the application service blows up with a
 * database-connection failure (or an external-systems failure), the presenter
 * should surface the user-actionable category message rather than the generic
 * "Could not complete company action." fallback. Validates that the
 * {@link com.ticketing.presentation.vaadin.util.PresenterErrorClassifier} is
 * actually wired into the presenter's user-message path, not just available as
 * a utility.
 */
@DisplayName("CompanyPresenter surfaces informative infra-failure messages (#516)")
@ExtendWith(VaadinSessionExtension.class)
class CompanyPresenterDbErrorMessageTest {

    private CompanyService companyService;
    private CompanyPresenter presenter;

    @BeforeEach
    void setUp() {
        companyService = mock(CompanyService.class);
        MemberService memberService = mock(MemberService.class);
        EventService eventService = mock(EventService.class);
        CompletedPurchaseService completedPurchaseService = mock(CompletedPurchaseService.class);
        presenter = new CompanyPresenter(companyService, memberService, eventService, completedPurchaseService);

        // Make memberToken() return non-null so openCompany reaches the service call.
        SessionContext.setSessionToken("test-token");
        SessionContext.setMemberId(UUID.randomUUID());
    }

    @Test
    void GivenServiceRaisesDbConnectionFailure_WhenOpenCompanyCalled_ThenReturnsDbUnavailableMessage() {
        when(companyService.openProductionCompany(anyString(), anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        ActionResult result = presenter.openCompany("AcmeCo", "desc");

        assertThat(result.success()).isFalse();
        assertThat(result.message())
                .as("user should see the DB-specific friendly message, not the generic fallback")
                .containsIgnoringCase("database")
                .containsIgnoringCase("retry")
                .doesNotContain("Could not complete");
    }

    @Test
    void GivenServiceRaisesNestedDbConnectionFailure_WhenOpenCompanyCalled_ThenStillReturnsDbUnavailableMessage() {
        // Real production stack: the spring-data exception is usually wrapped by the
        // @Transactional aspect inside another RuntimeException by the time it reaches
        // the presenter. The classifier walks the cause chain to find it.
        RuntimeException wrapped = new RuntimeException("transaction failed",
                new DataAccessResourceFailureException("HikariPool: connection is not available"));
        when(companyService.openProductionCompany(anyString(), anyString(), anyString()))
                .thenThrow(wrapped);

        ActionResult result = presenter.openCompany("AcmeCo", "desc");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).containsIgnoringCase("database").containsIgnoringCase("retry");
    }

    @Test
    void GivenServiceRaisesExternalSystemsFailure_WhenOpenCompanyCalled_ThenReturnsExternalSystemMessage() {
        when(companyService.openProductionCompany(anyString(), anyString(), anyString()))
                .thenThrow(new ExternalSystemsUnavailableException("payment endpoint unreachable"));

        ActionResult result = presenter.openCompany("AcmeCo", "desc");

        assertThat(result.success()).isFalse();
        assertThat(result.message())
                .as("user should see the external-service-specific message")
                .containsIgnoringCase("external")
                .containsIgnoringCase("retry");
    }

    @Test
    void GivenServiceRaisesPlainIllegalArgument_WhenOpenCompanyCalled_ThenExistingValidationMessagePathStillWorks() {
        // Regression guard: the classifier kicks in BEFORE the validation/auth branch,
        // so we need to confirm that branch still works for non-infrastructure errors
        // (otherwise existing presenter UX silently regresses).
        when(companyService.openProductionCompany(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Company name already taken"));

        ActionResult result = presenter.openCompany("AcmeCo", "desc");

        assertThat(result.success()).isFalse();
        assertThat(result.message())
                .as("existing validation-message path should be unchanged")
                .isEqualTo("Company name already taken");
    }
}
