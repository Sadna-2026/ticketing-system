package com.ticketing.presentation.vaadin.util;

import java.sql.SQLTransientConnectionException;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;

/**
 * Classifies infrastructure failures that bubble up to the Vaadin presenters into a
 * user-actionable category (#516). The point is to turn a noisy stack trace into a
 * short, true sentence the user can act on — "the database is briefly unavailable,
 * retrying" reads differently than "could not complete company action" backed by a
 * 30-line trace.
 *
 * <p>The classifier walks the {@link Throwable#getCause() cause chain} because the
 * concrete connection failure is almost always wrapped: a Spring service throws
 * {@link DataAccessResourceFailureException}, which wraps a Hibernate
 * {@link JDBCConnectionException}, which wraps a HikariCP
 * {@link SQLTransientConnectionException}, which wraps the driver's connection
 * error. Any link in that chain is enough to identify the category.
 *
 * <p>The companion banner work (#517) reuses {@link Category#DB_UNAVAILABLE} to
 * decide when to show the persistent red banner versus a transient toast.
 */
public final class PresenterErrorClassifier {

    /** Coarse categories presenters care about. {@link #NONE} means the exception is
     *  not an infrastructure failure — a validation/authorization message etc. */
    public enum Category {
        DB_UNAVAILABLE,
        EXTERNAL_SYSTEM_UNAVAILABLE,
        NONE
    }

    private static final String DB_UNAVAILABLE_MESSAGE =
            "The database is temporarily unavailable. Your work hasn't been lost — please retry in a moment.";
    private static final String EXTERNAL_SYSTEM_UNAVAILABLE_MESSAGE =
            "An external service (payment or ticket issuance) is temporarily unreachable. Please retry in a moment.";

    private PresenterErrorClassifier() {
    }

    /**
     * Classifies {@code ex} by walking its cause chain. Returns the first matching
     * category; if nothing in the chain is recognised, returns {@link Category#NONE}.
     *
     * @param ex the exception to classify; may be {@code null}
     * @return the matching category, never {@code null}
     */
    public static Category classify(Throwable ex) {
        Throwable cursor = ex;
        int hops = 0;
        // Cap chain depth as a defensive guard against pathological/looped causes;
        // real exception chains are 3–6 deep, never anywhere near 32.
        while (cursor != null && hops++ < 32) {
            if (cursor instanceof ExternalSystemsUnavailableException) {
                return Category.EXTERNAL_SYSTEM_UNAVAILABLE;
            }
            if (cursor instanceof DataAccessResourceFailureException
                    || cursor instanceof CannotCreateTransactionException
                    || cursor instanceof JDBCConnectionException
                    || cursor instanceof SQLTransientConnectionException) {
                return Category.DB_UNAVAILABLE;
            }
            // Match the H2-specific connection error by class-name to avoid pulling
            // h2's JDBC types into compile-time deps of this presentation utility.
            String className = cursor.getClass().getName();
            if (className.equals("org.h2.jdbc.JdbcSQLNonTransientConnectionException")
                    || className.equals("org.h2.jdbc.JdbcSQLTransientConnectionException")) {
                return Category.DB_UNAVAILABLE;
            }
            cursor = cursor.getCause();
        }
        return Category.NONE;
    }

    /**
     * Returns the user-facing message for the given category, or {@code null} for
     * {@link Category#NONE} (the caller should fall back to its own error mapping).
     */
    public static String userFacingMessage(Category category) {
        return switch (category) {
            case DB_UNAVAILABLE -> DB_UNAVAILABLE_MESSAGE;
            case EXTERNAL_SYSTEM_UNAVAILABLE -> EXTERNAL_SYSTEM_UNAVAILABLE_MESSAGE;
            case NONE -> null;
        };
    }
}
