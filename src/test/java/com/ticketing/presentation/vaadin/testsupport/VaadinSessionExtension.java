package com.ticketing.presentation.vaadin.testsupport;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import com.vaadin.flow.internal.CurrentInstance;
import com.vaadin.flow.server.VaadinSession;

/**
 * Installs a fresh, attribute-backed {@link VaadinSession} as the current session
 * before each test and clears all Vaadin thread-locals afterwards.
 *
 * <p>{@code VaadinSession.getCurrent()} is a process-wide thread-local that
 * {@link com.ticketing.presentation.vaadin.util.SessionContext} reads from, so a
 * test that leaves a session set (or a test that runs without one and inherits a
 * leaked session) makes the suite order-sensitive and intermittently red. Applying
 * this extension uniformly to every test that touches {@code SessionContext} or
 * builds a Vaadin component guarantees each test starts from a known-clean session
 * and tears it down with {@link CurrentInstance#clearAll()} — regardless of the
 * order JUnit happens to run the classes in.
 *
 * <p>The session's {@code getAttribute}/{@code setAttribute} are backed by a real
 * map, so {@code SessionContext} setters/getters behave like a real session.
 */
public final class VaadinSessionExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        VaadinSession session = mock(VaadinSession.class);
        Map<String, Object> attributes = new HashMap<>();

        doAnswer(invocation -> {
            attributes.put(invocation.getArgument(0, String.class), invocation.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), nullable(Object.class));

        when(session.getAttribute(anyString())).thenAnswer(invocation ->
                attributes.get(invocation.getArgument(0, String.class)));

        VaadinSession.setCurrent(session);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        CurrentInstance.clearAll();
    }
}
