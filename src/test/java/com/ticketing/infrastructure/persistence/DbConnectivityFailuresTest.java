package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.sql.SQLException;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.transaction.CannotCreateTransactionException;

@DisplayName("DbConnectivityFailures (req 5)")
class DbConnectivityFailuresTest {

    @Test
    void springDataAccessResourceFailure_isUnavailable() {
        assertThat(DbConnectivityFailures.isUnavailable(
                new DataAccessResourceFailureException("db gone"))).isTrue();
    }

    @Test
    void cannotCreateTransaction_isUnavailable() {
        assertThat(DbConnectivityFailures.isUnavailable(
                new CannotCreateTransactionException("could not open jdbc connection"))).isTrue();
    }

    @Test
    void nestedJdbcConnectionException_isUnavailable() {
        Throwable root = new SQLException("Connection refused");
        Throwable hibernate = new JDBCConnectionException("could not get connection", (SQLException) root);
        Throwable spring = new DataAccessResourceFailureException("db gone", hibernate);

        assertThat(DbConnectivityFailures.isUnavailable(spring)).isTrue();
    }

    @Test
    void connectException_isUnavailable() {
        assertThat(DbConnectivityFailures.isUnavailable(new ConnectException("Connection refused"))).isTrue();
    }

    @Test
    void validationError_isNotUnavailable() {
        assertThat(DbConnectivityFailures.isUnavailable(new IllegalArgumentException("bad input"))).isFalse();
    }

    @Test
    void missingTable_isDeferrableAtStartup() {
        Throwable ex = new InvalidDataAccessResourceUsageException(
                "relation admin does not exist",
                new org.hibernate.exception.SQLGrammarException(
                        "relation admin does not exist",
                        new java.sql.SQLException("ERROR: relation \"admin\" does not exist")));

        assertThat(DbConnectivityFailures.isUnavailable(ex)).isFalse();
        assertThat(DbConnectivityFailures.isDeferrableAtStartup(ex)).isTrue();
    }

    @Test
    void connectionReset_isUnavailable() {
        assertThat(DbConnectivityFailures.isUnavailable(
                new java.sql.SQLException("Connection reset"))).isTrue();
    }

    @Test
    void rollbackOnClosedConnection_isUnavailable() {
        RuntimeException cause = new RuntimeException(
                "Unable to rollback against JDBC Connection",
                new java.sql.SQLException("Connection is closed"));
        assertThat(DbConnectivityFailures.isUnavailable(
                new org.springframework.orm.jpa.JpaSystemException(cause))).isTrue();
    }
}
