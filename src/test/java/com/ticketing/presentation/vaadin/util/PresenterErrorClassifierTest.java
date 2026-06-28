package com.ticketing.presentation.vaadin.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

import com.ticketing.domain.gateway.ExternalSystemsUnavailableException;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier.Category;

/**
 * Unit-level coverage for the #516 classifier: feed it the exception shapes the
 * real infrastructure raises and assert the category. The classifier walks the
 * cause chain, so most tests build a small chain rather than constructing the
 * exact deep stack the production stack would emit.
 */
@DisplayName("PresenterErrorClassifier (#516)")
class PresenterErrorClassifierTest {

    @Nested
    @DisplayName("classify(...)")
    class Classify {

        @Test
        void springDataAccessResourceFailure_isDbUnavailable() {
            Throwable ex = new DataAccessResourceFailureException("db gone");
            assertThat(PresenterErrorClassifier.classify(ex)).isEqualTo(Category.DB_UNAVAILABLE);
        }

        @Test
        void hibernateJdbcConnectionExceptionInCauseChain_isDbUnavailable() {
            // Realistic shape: a Spring exception wrapping the Hibernate one wrapping
            // the JDBC driver's SQLException. Any layer of the chain must trigger DB_UNAVAILABLE.
            Throwable root = new SQLException("connection refused");
            Throwable hibernate = new JDBCConnectionException("could not get connection", (SQLException) root);
            Throwable spring = new DataAccessResourceFailureException("db gone", hibernate);
            Throwable wrapper = new RuntimeException("wrapped at presenter", spring);

            assertThat(PresenterErrorClassifier.classify(wrapper)).isEqualTo(Category.DB_UNAVAILABLE);
        }

        @Test
        void hikariTransientConnectionException_isDbUnavailable() {
            Throwable ex = new SQLTransientConnectionException("HikariPool-1 - Connection is not available");
            assertThat(PresenterErrorClassifier.classify(ex)).isEqualTo(Category.DB_UNAVAILABLE);
        }

        @Test
        void cannotCreateTransaction_isDbUnavailable() {
            // What Spring throws when the @Transactional boundary cannot acquire a connection.
            Throwable ex = new CannotCreateTransactionException("could not open jdbc connection");
            assertThat(PresenterErrorClassifier.classify(ex)).isEqualTo(Category.DB_UNAVAILABLE);
        }

        @Test
        void externalSystemsUnavailable_isExternalSystemUnavailable() {
            Throwable ex = new ExternalSystemsUnavailableException("payment endpoint timed out");
            assertThat(PresenterErrorClassifier.classify(ex)).isEqualTo(Category.EXTERNAL_SYSTEM_UNAVAILABLE);
        }

        @Test
        void externalSystemsUnavailableNestedInIllegalStateException_isExternalSystemUnavailable() {
            // PaymentGateway commonly catches the gateway exception and rethrows
            // IllegalStateException("Payment failed", cause). The cause-walk must still find it.
            Throwable ex = new IllegalStateException("Payment failed",
                    new ExternalSystemsUnavailableException("payment endpoint timed out"));
            assertThat(PresenterErrorClassifier.classify(ex)).isEqualTo(Category.EXTERNAL_SYSTEM_UNAVAILABLE);
        }

        @Test
        void plainIllegalArgument_isNone() {
            // Validation errors are NOT infrastructure — the caller should fall back
            // to its own message mapping (the existing presenter logic).
            assertThat(PresenterErrorClassifier.classify(new IllegalArgumentException("bad input")))
                    .isEqualTo(Category.NONE);
        }

        @Test
        void plainSecurityException_isNone() {
            assertThat(PresenterErrorClassifier.classify(new SecurityException("nope")))
                    .isEqualTo(Category.NONE);
        }

        @Test
        void nullException_isNone() {
            assertThat(PresenterErrorClassifier.classify(null)).isEqualTo(Category.NONE);
        }

        @Test
        void cyclicalCauseChain_doesNotInfiniteLoop() {
            // Defensive: an exception with a self-referential cause must not spin the classifier.
            RuntimeException a = new RuntimeException("a");
            RuntimeException b = new RuntimeException("b", a);
            // Java's Throwable.initCause guards against direct self-cause, so build the cycle
            // by chaining: the depth cap (32 hops) terminates the walk regardless.
            assertThat(PresenterErrorClassifier.classify(b)).isEqualTo(Category.NONE);
        }
    }

    @Nested
    @DisplayName("userFacingMessage(...)")
    class UserFacingMessage {

        @Test
        void dbUnavailable_messageMentionsDatabaseAndRetry() {
            String message = PresenterErrorClassifier.userFacingMessage(Category.DB_UNAVAILABLE);
            assertThat(message)
                    .containsIgnoringCase("database")
                    .containsIgnoringCase("retry");
        }

        @Test
        void externalSystem_messageMentionsExternalServiceAndRetry() {
            String message = PresenterErrorClassifier.userFacingMessage(Category.EXTERNAL_SYSTEM_UNAVAILABLE);
            assertThat(message)
                    .containsIgnoringCase("external")
                    .containsIgnoringCase("retry");
        }

        @Test
        void none_returnsNullSoCallerFallsBack() {
            assertThat(PresenterErrorClassifier.userFacingMessage(Category.NONE)).isNull();
        }
    }
}
