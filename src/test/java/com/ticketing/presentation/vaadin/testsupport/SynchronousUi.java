package com.ticketing.presentation.vaadin.testsupport;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.Command;

/**
 * Test helper for views that marshal work onto the UI thread via {@link UI#access(Command)}.
 *
 * <p>A bare {@code new UI()} in a unit test has no {@link com.vaadin.flow.server.VaadinSession}, so
 * the real {@code ui.access(...)} a view uses (e.g. a {@code beforeEnter} guard's info popup, or a
 * push callback) throws {@link com.vaadin.flow.component.UIDetachedException}. This UI runs the
 * command inline instead, letting a test exercise that path without a live WebSocket/session.
 */
public final class SynchronousUi {

    private SynchronousUi() {
    }

    /** Builds a {@link UI} whose {@link UI#access(Command)} executes the command inline. */
    public static UI create() {
        return new UI() {
            @Override
            public Future<Void> access(Command command) {
                command.execute();
                return CompletableFuture.completedFuture(null);
            }
        };
    }
}
