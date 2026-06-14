package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.BlurNotifier;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.HasValueAndElement;

/**
 * Marks mandatory form fields so they show a required-indicator asterisk and an
 * inline error when left blank. Optional fields are intentionally left untouched.
 */
public final class RequiredFields {

    private RequiredFields() {
    }

    /**
     * Shows the required-indicator asterisk and surfaces {@code errorMessage} inline when the
     * field is left blank. Vaadin Flow drives the invalid state from the server, so we validate
     * emptiness on blur and on value change rather than relying on client-side constraints.
     *
     * <p>This manages only the <em>required</em> (non-empty) constraint, which is all FIX-V2-15
     * asks for. It is not meant to compose with other per-field validators: any field that also
     * needs format/range validation (e.g. e-mail format) should be wired through a Vaadin
     * {@code Binder} instead, since the emptiness check here would otherwise drive the shared
     * invalid flag on its own.
     */
    public static <F extends Component & HasValidation & HasValueAndElement<?, ?>> void markRequired(
            F field, String errorMessage) {
        field.setRequiredIndicatorVisible(true);
        field.setErrorMessage(errorMessage);
        field.addValueChangeListener(event -> field.setInvalid(field.isEmpty()));
        if (field instanceof BlurNotifier<?> blurNotifier) {
            blurNotifier.addBlurListener(event -> field.setInvalid(field.isEmpty()));
        }
    }
}
