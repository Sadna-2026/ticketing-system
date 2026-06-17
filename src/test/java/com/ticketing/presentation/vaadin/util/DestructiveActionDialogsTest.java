package com.ticketing.presentation.vaadin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.testsupport.ConfirmDialogTestSupport;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;

@DisplayName("DestructiveActionDialogs")
@ExtendWith(VaadinSessionExtension.class)
class DestructiveActionDialogsTest {

    @BeforeEach
    void setUp() {
        ConfirmDialogTestSupport.install();
    }

    @AfterEach
    void tearDown() {
        ConfirmDialogTestSupport.reset();
    }

    @Test
    void GivenOpenDialog_WhenCancelClicked_ThenActionDoesNotRun() {
        AtomicBoolean ran = new AtomicBoolean(false);

        DestructiveActionDialogs.confirmRemoveMember("alice", () -> ran.set(true));

        assertTrue(ConfirmDialogTestSupport.isOpen());
        assertEquals("Remove member?", ConfirmDialogTestSupport.openDialogHeader());
        assertTrue(ConfirmDialogTestSupport.openDialogText().contains("alice"));
        assertTrue(ConfirmDialogTestSupport.openDialogText().contains("permanently deletes the account"));
        ConfirmDialogTestSupport.cancel();

        assertFalse(ran.get());
        assertFalse(ConfirmDialogTestSupport.isOpen());
    }

    @Test
    void GivenOpenDialog_WhenConfirmClicked_ThenActionRuns() {
        AtomicBoolean ran = new AtomicBoolean(false);

        DestructiveActionDialogs.confirmCloseCompany("Acme", () -> ran.set(true));

        ConfirmDialogTestSupport.confirm();

        assertTrue(ran.get());
        assertFalse(ConfirmDialogTestSupport.isOpen());
    }

    @Test
    void GivenBlankTarget_WhenDialogOpens_ThenFallbackTargetLabelIsUsed() {
        DestructiveActionDialogs.confirmSuspendMember("  ", () -> {
        });

        assertEquals(
                "Are you sure you want to suspend \"the selected item\"? You can cancel the suspension later.",
                ConfirmDialogTestSupport.openDialogText());
    }

    @Test
    void GivenCancelSuspension_WhenDialogOpens_ThenUsesRestorativeCopy() {
        DestructiveActionDialogs.confirmCancelSuspension("suspended", () -> {
        });

        assertEquals("Restore member access?", ConfirmDialogTestSupport.openDialogHeader());
        assertEquals(
                "Restore access for \"suspended\"? This will end the active suspension.",
                ConfirmDialogTestSupport.openDialogText());
    }

    @Test
    void GivenClearCart_WhenDialogOpens_ThenMentionsReservedTickets() {
        DestructiveActionDialogs.confirmClearCart("Demo Concert", () -> {
        });

        assertEquals("Clear cart?", ConfirmDialogTestSupport.openDialogHeader());
        assertTrue(ConfirmDialogTestSupport.openDialogText().contains("clear the cart for \"Demo Concert\""));
        assertTrue(ConfirmDialogTestSupport.openDialogText().contains("releases all reserved tickets back"));
    }

    @Test
    void GivenRejectRoleOffer_WhenDialogOpens_ThenUsesPlainConfirmationOnly() {
        DestructiveActionDialogs.confirmRejectRoleOffer("Acme — MANAGER", () -> {
        });

        assertEquals(
                "Are you sure you want to reject the role offer \"Acme — MANAGER\"?",
                ConfirmDialogTestSupport.openDialogText());
    }
}
