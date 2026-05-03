package com.ticketing.domain.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IActiveOrderRepository {
    Optional<ActiveOrder> findActiveBySessionId(UUID sessionId);
}