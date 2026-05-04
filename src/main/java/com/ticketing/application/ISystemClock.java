package com.ticketing.application;

import java.time.Instant;

public interface ISystemClock {

    /**
     * Returns the current time.
     */
    Instant now();
}