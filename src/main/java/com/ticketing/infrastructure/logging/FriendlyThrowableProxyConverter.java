package com.ticketing.infrastructure.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Replaces long infrastructure stack traces in {@code logs/error.log} with a short framed
 * summary ({@link InfrastructureErrorMessages}). Other errors keep the default trace.
 */
public class FriendlyThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy throwableProxy) {
        String friendly = InfrastructureErrorMessages.formatLogThrowable(throwableProxy);
        return friendly;
    }
}
