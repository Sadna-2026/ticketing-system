package com.ticketing.infrastructure.persistence;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import org.hibernate.exception.JDBCConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Detects database connectivity failures in exception cause chains (req 5 / V3 robustness).
 * Used at startup (defer bootstrap) and in the UI layer ({@code PresenterErrorClassifier}).
 */
public final class DbConnectivityFailures {

    private DbConnectivityFailures() {
    }

    /**
     * Returns {@code true} when {@code ex} (or any cause) indicates the database is
     * unreachable or refusing connections — not validation or business-rule failures.
     */
    public static boolean isUnavailable(Throwable ex) {
        Throwable cursor = ex;
        int hops = 0;
        while (cursor != null && hops++ < 32) {
            if (isUnavailableDescriptor(cursor.getClass().getName(), cursor.getMessage(), cursor)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Same rules as {@link #isUnavailable(Throwable)} for logback {@code IThrowableProxy} chains.
     */
    public static boolean isUnavailable(String className, String message) {
        return isUnavailableDescriptor(className, message, null);
    }

    /**
     * Returns {@code true} when a startup step (platform init, wipe, bootstrap) should be
     * deferred and retried later — including transient connectivity loss and other
     * {@link org.springframework.dao.DataAccessException}s that can occur when the DB
     * drops mid-init (e.g. missing tables while schema is not yet ready).
     */
    public static boolean isDeferrableAtStartup(Throwable ex) {
        if (isUnavailable(ex)) {
            return true;
        }
        Throwable cursor = ex;
        int hops = 0;
        while (cursor != null && hops++ < 32) {
            if (cursor instanceof org.springframework.dao.DataAccessException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static boolean isUnavailableDescriptor(String className, String message, Throwable instance) {
        if (instance instanceof DataAccessResourceFailureException
                || instance instanceof CannotCreateTransactionException
                || instance instanceof JDBCConnectionException
                || instance instanceof SQLTransientConnectionException
                || instance instanceof ConnectException
                || instance instanceof SocketTimeoutException
                || instance instanceof SQLException sql && isConnectionRelatedMessage(sql.getMessage())
                || instance instanceof org.springframework.orm.jpa.JpaSystemException jpa
                        && isConnectionRelatedMessage(jpa.getMessage())) {
            return true;
        }
        if (className != null && (
                className.equals(DataAccessResourceFailureException.class.getName())
                        || className.equals(CannotCreateTransactionException.class.getName())
                        || className.equals(JDBCConnectionException.class.getName())
                        || className.equals(SQLTransientConnectionException.class.getName())
                        || className.equals(ConnectException.class.getName())
                        || className.equals(SocketTimeoutException.class.getName())
                        || className.equals(SQLException.class.getName())
                        || className.equals("org.springframework.orm.jpa.JpaSystemException"))) {
            if (className.equals(SQLException.class.getName())
                    || className.equals("org.springframework.orm.jpa.JpaSystemException")) {
                return isConnectionRelatedMessage(message);
            }
            return true;
        }
        if (className != null && (
                className.equals("org.h2.jdbc.JdbcSQLNonTransientConnectionException")
                        || className.equals("org.h2.jdbc.JdbcSQLTransientConnectionException")
                        || className.equals("org.postgresql.util.PSQLException"))) {
            if (message != null && isConnectionRelatedMessage(message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isConnectionRelatedMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.contains("Connection refused")
                || message.contains("Connect timed out")
                || message.contains("Connection is broken")
                || message.contains("Unable to obtain connection")
                || message.contains("The connection attempt failed")
                || message.contains("Connection reset")
                || message.contains("Broken pipe")
                || message.contains("terminating connection")
                || message.contains("This connection has been closed")
                || message.contains("Connection is closed")
                || message.contains("connection disabled")
                || message.contains("Connection is not available")
                || message.contains("Unable to rollback against JDBC Connection");
    }
}
