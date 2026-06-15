package com.ticketing.presentation.vaadin.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.presentation.vaadin.testsupport.ConfirmDialogTestSupport;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;

@DisplayName("DestructiveActionDialogs")
@ExtendWith(VaadinSessionExtension.class)
class DestructiveActionDialogsTest {

    @Test
    void GivenOpenDialog_WhenCancelClicked_ThenActionDoesNotRun() {
        AtomicBoolean ran = new AtomicBoolean(false);

        DestructiveActionDialogs.confirm("remove member", "alice", () -> ran.set(true));

        assertTrue(ConfirmDialogTestSupport.isOpen());
        assertTrue(ConfirmDialogTestSupport.openDialogText().contains("alice"));
        ConfirmDialogTestSupport.cancel();

        assertFalse(ran.get());
        assertFalse(ConfirmDialogTestSupport.isOpen());
    }

    @Test
    void GivenOpenDialog_WhenConfirmClicked_ThenActionRuns() {
        AtomicBoolean ran = new AtomicBoolean(false);

        DestructiveActionDialogs.confirm("close company", "Acme", () -> ran.set(true));

        ConfirmDialogTestSupport.confirm();

        assertTrue(ran.get());
        assertFalse(ConfirmDialogTestSupport.isOpen());
    }

    @Test
    void GivenBlankTarget_WhenDialogOpens_ThenFallbackTargetLabelIsUsed() {
        DestructiveActionDialogs.confirm("suspend member", "  ", () -> {
        });

        assertEquals(
                "Are you sure you want to suspend member \"the selected item\"? This cannot be undone.",
                ConfirmDialogTestSupport.openDialogText());
    }
}
