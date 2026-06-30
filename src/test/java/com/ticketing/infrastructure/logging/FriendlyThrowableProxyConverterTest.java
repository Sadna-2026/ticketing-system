package com.ticketing.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.exception.JDBCConnectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;

@DisplayName("FriendlyThrowableProxyConverter")
class FriendlyThrowableProxyConverterTest {

    @Test
    void databaseError_rendersFramedSummaryWithoutStackFrames() {
        Throwable ex = new DataAccessResourceFailureException("db gone",
                new JDBCConnectionException("no connection",
                        new java.sql.SQLException("The connection attempt failed",
                                new java.net.SocketTimeoutException("Connect timed out"))));

        Logger logger = (Logger) LoggerFactory.getLogger(FriendlyThrowableProxyConverterTest.class);
        LoggingEvent event = new LoggingEvent(
                FriendlyThrowableProxyConverter.class.getName(),
                logger,
                Level.ERROR,
                "Application run failed",
                ex,
                null);

        FriendlyThrowableProxyConverter converter = new FriendlyThrowableProxyConverter();
        converter.start();

        String rendered = converter.convert(event);

        assertThat(rendered)
                .contains("DATABASE UNAVAILABLE")
                .contains("authorized networks")
                .doesNotContain("at org.postgresql")
                .doesNotContain("at org.hibernate.tool.schema");
    }

    @Test
    void unclassifiedError_rendersGenericSummaryWithoutStackFrames() {
        Throwable ex = new IllegalStateException("something odd happened");

        Logger logger = (Logger) LoggerFactory.getLogger(FriendlyThrowableProxyConverterTest.class);
        LoggingEvent event = new LoggingEvent(
                FriendlyThrowableProxyConverter.class.getName(),
                logger,
                Level.ERROR,
                "operation failed",
                ex,
                null);

        FriendlyThrowableProxyConverter converter = new FriendlyThrowableProxyConverter();
        converter.start();

        String rendered = converter.convert(event);

        assertThat(rendered)
                .contains("UNEXPECTED ERROR")
                .contains("something odd happened")
                .doesNotContain("at java.");
    }
}
