package com.ticketing.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.SocketTimeoutException;
import java.sql.SQLException;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.dao.DataAccessResourceFailureException;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;

@DisplayName("InfrastructureErrorMessages")
class InfrastructureErrorMessagesTest {

    @Test
    void dbConnectionTimeout_producesFramedDatabaseMessage() {
        Throwable root = new SocketTimeoutException("Connect timed out");
        SQLException driver = new SQLException("The connection attempt failed", root);
        Throwable hibernate = new JDBCConnectionException("Unable to open JDBC Connection for DDL execution", driver);
        Throwable spring = new BeanCreationException("configEntityManagerFactory", hibernate);

        String summary = InfrastructureErrorMessages.summarize(spring);

        assertThat(summary)
                .contains("DATABASE UNAVAILABLE")
                .contains("DB_URL_OPERATIONAL")
                .contains("authorized networks")
                .doesNotContain("at org.hibernate");
    }

    @Test
    void externalSystemsFailure_producesFramedExternalMessage() {
        Throwable ex = new ExternalSystemsUnavailableException("payment endpoint timed out");

        String summary = InfrastructureErrorMessages.summarize(ex);

        assertThat(summary)
                .contains("EXTERNAL SERVICE UNAVAILABLE")
                .contains("TICKETING_EXTERNAL_BASE_URL");
    }

    @Test
    void validationError_formatLogThrowable_usesGenericFramedMessage() {
        String formatted = InfrastructureErrorMessages.formatLogThrowable(new IllegalArgumentException("bad input"));

        assertThat(formatted)
                .contains("UNEXPECTED ERROR")
                .contains("IllegalArgumentException: bad input")
                .doesNotContain("at java.");
    }

    @Test
    void clientDisconnect_isBenignAndFormatsShortMessage() {
        Throwable ex = new org.apache.catalina.connector.ClientAbortException(
                "java.io.IOException: An established connection was aborted by the software in your host machine",
                new java.io.IOException("An established connection was aborted by the software in your host machine"));

        assertThat(InfrastructureErrorMessages.isBenignTransportFailure(ex)).isTrue();
        assertThat(InfrastructureErrorMessages.summarize(ex)).isNull();
        assertThat(InfrastructureErrorMessages.formatLogThrowable(ex))
                .contains("CLIENT DISCONNECTED")
                .doesNotContain("at org.apache.catalina");
    }

    @Test
    void responseAlreadyCommitted_isBenignTransportFailure() {
        Throwable ex = new IllegalStateException("Cannot call sendError() after the response has been committed");

        assertThat(InfrastructureErrorMessages.isBenignTransportFailure(ex)).isTrue();
    }

    @Test
    void vaadinCopilotDnsFailure_producesFramedCopilotMessage() {
        Throwable ex = new java.net.UnknownHostException("Failed to resolve 'copilot.vaadin.com'");

        String summary = InfrastructureErrorMessages.summarize(ex);

        assertThat(summary)
                .contains("VAADIN COPILOT OFFLINE")
                .contains("vaadin.copilot.enable=false")
                .doesNotContain("at io.netty");
    }

    @Test
    void databaseUnavailable_producesFramedUnavailableMessage() {
        String summary = InfrastructureErrorMessages.databaseUnavailable();

        assertThat(summary)
                .startsWith(System.lineSeparator())
                .contains("DATABASE UNAVAILABLE")
                .contains("DB_URL_OPERATIONAL");
    }

    @Test
    void databaseRecovered_producesFramedRestoredMessage() {
        String summary = InfrastructureErrorMessages.databaseRecovered();

        assertThat(summary)
                .contains("DATABASE CONNECTION RESTORED")
                .contains("reachable again");
    }

    @Test
    void failureAnalyzerDescribesDatabaseProblem() {
        Throwable ex = new DataAccessResourceFailureException("db gone",
                new JDBCConnectionException("no connection",
                        new SQLException("Connection refused")));

        var analysis = new InfrastructureConnectivityFailureAnalyzer().analyze(ex);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("could not connect to the database");
        assertThat(analysis.getAction()).contains("DB_URL_OPERATIONAL");
    }
}
