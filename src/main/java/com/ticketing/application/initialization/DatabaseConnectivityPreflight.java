package com.ticketing.application.initialization;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

/**
 * Verifies the configured databases are actually reachable BEFORE the JPA layer
 * starts, so an unreachable or misconfigured remote DB halts with a short, plain
 * explanation in {@code error.log} instead of a wall of Hibernate / HikariCP
 * stack traces. Runs at environment-prepare time (invoked from
 * {@link TicketingConfigurationRules.EarlyValidationInitializer}), before any bean
 * is created.
 *
 * <p>Only acts in JPA mode and only for non-H2 URLs: an in-memory / file H2 is
 * always reachable, so there is nothing useful to pre-check and the whole existing
 * test suite (memory mode, or JPA-on-H2) skips this entirely.
 */
final class DatabaseConnectivityPreflight {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectivityPreflight.class);

    private static final String BORDER =
            "*************************************************************";

    private static final Pattern HOST_PORT =
            Pattern.compile("jdbc:[^:]+://([^/?]+)");

    private DatabaseConnectivityPreflight() {
    }

    static void verify(Environment env) {
        if (!"jpa".equals(env.getProperty("ticketing.persistence"))) {
            return; // memory mode: no external DB to reach
        }
        int timeoutSeconds = Math.max(
                1, env.getProperty("ticketing.external.connect-timeout-ms", Integer.class, 5000) / 1000);
        verifyOne(env, "spring.datasource.operational", "operational", timeoutSeconds);
        verifyOne(env, "spring.datasource.config", "config", timeoutSeconds);
    }

    private static void verifyOne(Environment env, String prefix, String label, int timeoutSeconds) {
        String url = env.getProperty(prefix + ".url");
        if (url == null || url.startsWith("jdbc:h2:")) {
            return; // skip in-memory / local H2 - always reachable
        }
        String username = env.getProperty(prefix + ".username", "");
        String password = env.getProperty(prefix + ".password", "");

        DriverManager.setLoginTimeout(timeoutSeconds);
        Properties props = new Properties();
        if (username != null) {
            props.setProperty("user", username);
        }
        if (password != null) {
            props.setProperty("password", password);
        }

        try (Connection ignored = DriverManager.getConnection(url, props)) {
            // reachable - nothing to do
        } catch (SQLException ex) {
            if (isDeferrableConnectionFailure(ex.getSQLState(), ex.getMessage())) {
                log.warn(framedDeferred(buildDetail(label, url, ex)));
                return;
            }
            throw new StartupHaltException(framedHalt(buildDetail(label, url, ex)));
        }
    }

    /**
     * Req 5: transient network outages defer startup; permanent misconfiguration still halts
     * with the partner's framed DATABASE CONNECTION ERROR message.
     */
    static boolean isDeferrableConnectionFailure(String sqlState, String message) {
        String m = message == null ? "" : message.toLowerCase();

        if ("28P01".equalsIgnoreCase(sqlState) || m.contains("password authentication failed")) {
            return false;
        }
        if ("3D000".equalsIgnoreCase(sqlState) || m.contains("does not exist")) {
            return false;
        }
        if (m.contains("no suitable driver")) {
            return false;
        }
        return true;
    }

    private static String buildDetail(String label, String url, SQLException ex) {
        String hostPort = extractHostPort(url);
        String sqlState = ex.getSQLState() == null ? "n/a" : ex.getSQLState();
        return """
                Could not connect to the %s database at %s.
                  Driver reported: %s (SQLState %s)

                %s

                Once the cause is fixed, just re-run - no code change needed.""".formatted(
                label, hostPort, oneLine(ex.getMessage()), sqlState, diagnose(sqlState, ex.getMessage()));
    }

    /** Maps the common Postgres / Cloud SQL failure modes to a plain next step. */
    static String diagnose(String sqlState, String message) {
        String m = message == null ? "" : message.toLowerCase();

        if ("28P01".equalsIgnoreCase(sqlState) || m.contains("password authentication failed")) {
            return "Likely cause: wrong credentials. Check DB_USERNAME / DB_PASSWORD match the Cloud SQL user.";
        }
        if ("3D000".equalsIgnoreCase(sqlState) || m.contains("does not exist")) {
            return "Likely cause: that database name does not exist on the instance. "
                    + "Create it (e.g. `gcloud sql databases create <name> --instance=<instance>`) "
                    + "or fix the database name in DB_URL / DB_URL_OPERATIONAL / DB_URL_CONFIG.";
        }
        if (m.contains("no suitable driver")) {
            return "Likely cause: DB_DRIVER does not match the URL scheme "
                    + "(use org.postgresql.Driver for a jdbc:postgresql:// URL).";
        }
        if (m.contains("connection refused") || m.contains("timed out") || m.contains("timeout")
                || "08001".equals(sqlState) || "08006".equals(sqlState)) {
            return "Likely cause: the host/port is wrong, the instance is stopped, or your current "
                    + "public IP is not in the instance's Authorized networks. "
                    + "Check the instance is RUNNABLE and add your IP under Connections > Networking.";
        }
        return "Check: instance is running, host/port and DB name are correct, credentials are valid, "
                + "and your public IP is in the instance's Authorized networks.";
    }

    static String extractHostPort(String url) {
        Matcher matcher = HOST_PORT.matcher(url);
        return matcher.find() ? matcher.group(1) : url;
    }

    private static String oneLine(String message) {
        return message == null ? "(no message)" : message.replaceAll("\\s+", " ").trim();
    }

    private static String framedHalt(String detail) {
        return """

                %s
                  APPLICATION STARTUP HALTED — DATABASE CONNECTION ERROR
                  %s
                %s
                """.formatted(BORDER, detail, BORDER);
    }

    private static String framedDeferred(String detail) {
        return """

                %s
                  DATABASE UNAVAILABLE AT STARTUP — DEFERRING INITIALIZATION
                  %s
                %s
                """.formatted(BORDER, detail, BORDER);
    }
}
