package com.ticketing.presentation.vaadin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ticketing.infrastructure.logging.InfrastructureErrorMessages;
import com.ticketing.presentation.vaadin.util.UiMessages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

/**
 * Prevents uncaught Vaadin UI errors from emitting raw stack traces or triggering a second
 * {@code sendError()} after the response is already committed (common when Wi-Fi drops).
 */
@Component
public class InfrastructureVaadinErrorHandler implements VaadinServiceInitListener, ErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureVaadinErrorHandler.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(
                sessionInit -> sessionInit.getSession().setErrorHandler(this));
    }

    @Override
    public void error(ErrorEvent event) {
        Throwable ex = event.getThrowable();
        if (InfrastructureErrorMessages.isBenignTransportFailure(ex)) {
            log.debug("UI request ended early ({})", ex.toString());
            return;
        }
        log.error(InfrastructureErrorMessages.formatLogThrowable(ex));
        UI ui = UI.getCurrent();
        if (ui != null && ui.isAttached()) {
            ui.access(() -> UiMessages.error(
                    "Something went wrong. Please retry — your work may not have been saved."));
        }
    }
}
