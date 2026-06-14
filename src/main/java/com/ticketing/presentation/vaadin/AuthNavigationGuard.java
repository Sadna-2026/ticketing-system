package com.ticketing.presentation.vaadin;

import org.springframework.stereotype.Component;

import com.ticketing.presentation.vaadin.util.SessionContext;
import com.ticketing.presentation.vaadin.views.AuthView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

@Component
public class AuthNavigationGuard implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent ->
                uiEvent.getUI().addBeforeEnterListener(this::beforeEnter));
    }

    void beforeEnter(BeforeEnterEvent event) {
        if (SessionContext.currentUiState().noSession()
                && !AuthView.class.equals(event.getNavigationTarget())) {
            event.rerouteTo(AuthView.class);
        }
    }
}
