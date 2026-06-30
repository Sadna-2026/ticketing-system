package com.ticketing.infrastructure.logging;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.infrastructure.persistence.DbConnectivityFailures;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier;

import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Turns infrastructure failures (database, external systems, Vaadin Copilot DNS) into short,
 * actionable text for {@code logs/error.log} and Spring Boot startup diagnostics — without
 * multi-page stack traces.
 */
public final class InfrastructureErrorMessages {

    private static final String BORDER = "*************************************************************";

    private static final String DB_ERROR_LOG_DETAIL = """
            Cannot reach the database. Verify PostgreSQL is running, DB_URL_OPERATIONAL and \
            DB_URL_CONFIG are correct, DB_USERNAME and DB_PASSWORD are set, and your current IP \
            is listed in the Cloud SQL authorized networks (firewall).""";

    private static final String EXTERNAL_ERROR_LOG_DETAIL = """
            An external service (payment or ticket issuance) is unreachable. Check \
            TICKETING_EXTERNAL_BASE_URL and network connectivity, then retry.""";

    private static final String VAADIN_COPILOT_OFFLINE_DETAIL = """
            Vaadin Copilot could not reach copilot.vaadin.com (no network). Offline dev is \
            unaffected — set vaadin.copilot.enable=false to silence this, or reconnect Wi-Fi.""";

    private static final String GENERIC_ERROR_LOG_DETAIL = """
            An unexpected error occurred. Retry the action; if it keeps failing, check \
            configuration and network connectivity.""";

    private static final String CLIENT_DISCONNECTED_DETAIL = """
            The browser closed the connection (for example during a Wi-Fi change). The page \
            should recover after reconnect — no action needed unless it does not.""";

    private enum LogCategory {
        DB_UNAVAILABLE,
        EXTERNAL_SYSTEM_UNAVAILABLE,
        VAADIN_COPILOT_OFFLINE,
        CLIENT_DISCONNECTED,
        GENERIC_UNEXPECTED,
        NONE
    }

    private InfrastructureErrorMessages() {
    }

    public static String summarize(Throwable failure) {
        LogCategory category = classify(failure);
        if (category == LogCategory.GENERIC_UNEXPECTED || category == LogCategory.CLIENT_DISCONNECTED) {
            return null;
        }
        return framedForThrowable(category, failure);
    }

    /** Prominent log line when the database cannot be reached (startup or runtime). */
    public static String databaseUnavailable() {
        return framed("DATABASE UNAVAILABLE", DB_ERROR_LOG_DETAIL);
    }

    /** Prominent log line when connectivity returns after an outage (req 5). */
    public static String databaseRecovered() {
        return framed(
                "DATABASE CONNECTION RESTORED",
                """
                The database is reachable again. Deferred startup will complete automatically; \
                retry any action that failed while offline.""");
    }

    public static String summarize(IThrowableProxy failure) {
        LogCategory category = classifyProxy(failure);
        if (category == LogCategory.NONE || category == LogCategory.GENERIC_UNEXPECTED) {
            return null;
        }
        return framedForProxy(category, failure);
    }

    /** Short, stack-free text for {@code logs/error.log} — never returns {@code null}. */
    public static String formatLogThrowable(Throwable failure) {
        return formatCategory(classify(failure), failure);
    }

    /** Short, stack-free text for logback {@code IThrowableProxy} chains. */
    public static String formatLogThrowable(IThrowableProxy failure) {
        return formatCategory(classifyProxy(failure), failure);
    }

    /** Benign transport failures that should not fill {@code logs/error.log}. */
    public static boolean isBenignTransportFailure(Throwable failure) {
        return classify(failure) == LogCategory.CLIENT_DISCONNECTED;
    }

    public static boolean isBenignTransportFailure(IThrowableProxy failure) {
        return classifyProxy(failure) == LogCategory.CLIENT_DISCONNECTED;
    }

    public static String shortDescription(Throwable failure) {
        return switch (classify(failure)) {
            case DB_UNAVAILABLE -> "The application could not connect to the database.";
            case EXTERNAL_SYSTEM_UNAVAILABLE -> "An external service is unreachable.";
            case VAADIN_COPILOT_OFFLINE, CLIENT_DISCONNECTED, GENERIC_UNEXPECTED, NONE -> null;
        };
    }

    public static String recommendedAction(Throwable failure) {
        return switch (classify(failure)) {
            case DB_UNAVAILABLE -> """
                    Check DB_URL_OPERATIONAL, DB_URL_CONFIG, credentials, that Cloud SQL is running, \
                    and that your IP is authorized in the firewall. Then restart or wait for \
                    automatic recovery.""";
            case EXTERNAL_SYSTEM_UNAVAILABLE -> """
                    Check TICKETING_EXTERNAL_BASE_URL and that the external endpoint is reachable.""";
            case VAADIN_COPILOT_OFFLINE, CLIENT_DISCONNECTED, GENERIC_UNEXPECTED, NONE -> null;
        };
    }

    private static LogCategory classify(Throwable failure) {
        Throwable cursor = failure;
        int hops = 0;
        while (cursor != null && hops++ < 32) {
            if (isVaadinCopilotOffline(cursor.getClass().getName(), cursor.getMessage())) {
                return LogCategory.VAADIN_COPILOT_OFFLINE;
            }
            if (isClientDisconnected(cursor.getClass().getName(), cursor.getMessage())) {
                return LogCategory.CLIENT_DISCONNECTED;
            }
            cursor = cursor.getCause();
        }
        return switch (PresenterErrorClassifier.classify(failure)) {
            case DB_UNAVAILABLE -> LogCategory.DB_UNAVAILABLE;
            case EXTERNAL_SYSTEM_UNAVAILABLE -> LogCategory.EXTERNAL_SYSTEM_UNAVAILABLE;
            case NONE -> LogCategory.GENERIC_UNEXPECTED;
        };
    }

    private static String framedForThrowable(LogCategory category, Throwable failure) {
        return switch (category) {
            case DB_UNAVAILABLE -> databaseUnavailable();
            case EXTERNAL_SYSTEM_UNAVAILABLE -> framed("EXTERNAL SERVICE UNAVAILABLE", EXTERNAL_ERROR_LOG_DETAIL);
            case VAADIN_COPILOT_OFFLINE -> framed("VAADIN COPILOT OFFLINE", VAADIN_COPILOT_OFFLINE_DETAIL);
            case CLIENT_DISCONNECTED -> framed("CLIENT DISCONNECTED", CLIENT_DISCONNECTED_DETAIL);
            case GENERIC_UNEXPECTED -> framed("UNEXPECTED ERROR", genericDetail(failure));
            case NONE -> null;
        };
    }

    private static String formatCategory(LogCategory category, Throwable failure) {
        String framed = framedForThrowable(category, failure);
        return framed != null ? framed : framedForThrowable(LogCategory.GENERIC_UNEXPECTED, failure);
    }

    private static String formatCategory(LogCategory category, IThrowableProxy failure) {
        String framed = framedForProxy(category, failure);
        return framed != null ? framed : framedForProxy(LogCategory.GENERIC_UNEXPECTED, failure);
    }

    private static String genericDetail(Throwable failure) {
        if (failure == null) {
            return GENERIC_ERROR_LOG_DETAIL;
        }
        String message = failure.getMessage();
        if (message != null && !message.isBlank()) {
            return GENERIC_ERROR_LOG_DETAIL + " (" + failure.getClass().getSimpleName() + ": "
                    + message.strip() + ")";
        }
        return GENERIC_ERROR_LOG_DETAIL + " (" + failure.getClass().getSimpleName() + ")";
    }

    private static String genericDetail(IThrowableProxy failure) {
        if (failure == null) {
            return GENERIC_ERROR_LOG_DETAIL;
        }
        String message = failure.getMessage();
        String simpleName = simpleClassName(failure.getClassName());
        if (message != null && !message.isBlank()) {
            return GENERIC_ERROR_LOG_DETAIL + " (" + simpleName + ": " + message.strip() + ")";
        }
        return GENERIC_ERROR_LOG_DETAIL + " (" + simpleName + ")";
    }

    private static String simpleClassName(String className) {
        if (className == null) {
            return "Exception";
        }
        int dot = className.lastIndexOf('.');
        return dot >= 0 ? className.substring(dot + 1) : className;
    }

    private static String framed(String kind, String detail) {
        return "%n%s%n  %s%n  %s%n%s%n".formatted(BORDER, kind, detail.strip(), BORDER);
    }

    private static LogCategory classifyProxy(IThrowableProxy failure) {
        IThrowableProxy cursor = failure;
        int hops = 0;
        while (cursor != null && hops++ < 32) {
            if (isVaadinCopilotOffline(cursor.getClassName(), cursor.getMessage())) {
                return LogCategory.VAADIN_COPILOT_OFFLINE;
            }
            if (isClientDisconnected(cursor.getClassName(), cursor.getMessage())) {
                return LogCategory.CLIENT_DISCONNECTED;
            }
            if (ExternalSystemsUnavailableException.class.getName().equals(cursor.getClassName())) {
                return LogCategory.EXTERNAL_SYSTEM_UNAVAILABLE;
            }
            if (DbConnectivityFailures.isUnavailable(cursor.getClassName(), cursor.getMessage())
                    || isDataAccessExceptionClassName(cursor.getClassName())) {
                return LogCategory.DB_UNAVAILABLE;
            }
            cursor = cursor.getCause();
        }
        return LogCategory.GENERIC_UNEXPECTED;
    }

    private static boolean isDataAccessExceptionClassName(String className) {
        return className != null && (
                className.startsWith("org.springframework.dao.")
                        || className.startsWith("org.hibernate.exception.")
                        || className.equals("org.springframework.orm.jpa.JpaSystemException"));
    }

    private static boolean isClientDisconnected(String className, String message) {
        if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
            return true;
        }
        if ("java.io.IOException".equals(className) && message != null) {
            return message.contains("connection was aborted")
                    || message.contains("Connection reset by peer")
                    || message.contains("Broken pipe");
        }
        if ("java.lang.IllegalStateException".equals(className) && message != null) {
            return message.contains("Cannot call sendError() after the response has been committed")
                    || message.contains("getOutputStream() has already been called");
        }
        if ("org.springframework.beans.factory.BeanCreationNotAllowedException".equals(className)) {
            return true;
        }
        if ("com.vaadin.flow.server.ServiceException".equals(className) && message != null) {
            return message.contains("Cannot call sendError() after the response has been committed");
        }
        return false;
    }

    private static String framedForProxy(LogCategory category, IThrowableProxy failure) {
        return switch (category) {
            case DB_UNAVAILABLE -> databaseUnavailable();
            case EXTERNAL_SYSTEM_UNAVAILABLE -> framed("EXTERNAL SERVICE UNAVAILABLE", EXTERNAL_ERROR_LOG_DETAIL);
            case VAADIN_COPILOT_OFFLINE -> framed("VAADIN COPILOT OFFLINE", VAADIN_COPILOT_OFFLINE_DETAIL);
            case CLIENT_DISCONNECTED -> framed("CLIENT DISCONNECTED", CLIENT_DISCONNECTED_DETAIL);
            case GENERIC_UNEXPECTED -> framed("UNEXPECTED ERROR", genericDetail(failure));
            case NONE -> null;
        };
    }

    private static boolean isVaadinCopilotOffline(String className, String message) {
        return message != null && message.contains("copilot.vaadin.com");
    }
}
