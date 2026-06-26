package com.ticketing.infrastructure.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.notification.InMemoryPendingNotificationRepository;

/**
 * Same operational snapshot assertions as {@link PersistenceRestartAcceptanceTest}, exercised
 * against in-memory repositories (no restart — memory is not durable across JVM restarts).
 */
@DisplayName("Persistence state consistency (memory)")
class PersistenceStateMemoryConsistencyTest {

    @Test
    void GivenRichOperationalState_WhenSavedInMemory_ThenRepositoriesStayConsistent() {
        var members = new InMemoryMemberRepository();
        var companies = new InMemoryCompanyRepository();
        var events = new InMemoryEventRepository();
        var orders = new InMemoryOrderRepository();
        var notifications = new InMemoryPendingNotificationRepository();

        var snapshot = PersistenceRestartFixtures.seed(members, companies, events, orders, notifications);
        PersistenceRestartFixtures.assertStatePresent(members, companies, events, orders, notifications, snapshot);
    }
}
