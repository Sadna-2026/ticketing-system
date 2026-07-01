package com.ticketing.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Keeps {@code logs/error.log} free of benign transport noise (client disconnect, response
 * already committed) that appears when Wi-Fi drops mid-request.
 */
public class SuppressBenignInfrastructureErrorsFilter extends Filter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!event.getLevel().isGreaterOrEqual(Level.ERROR)) {
            return FilterReply.NEUTRAL;
        }
        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null && InfrastructureErrorMessages.isBenignTransportFailure(throwable)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
