package com.ticketing.application.initialization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.application.scheduling.LotteryDrawScheduler;
import com.ticketing.infrastructure.logging.InfrastructureErrorMessages;
import com.ticketing.infrastructure.persistence.DatabaseConnectivityProbe;

/**
 * Completes deferred platform initialization / bootstrap when the database becomes
 * reachable after a failed-at-boot connection (req 5). Uses the same JDBC pools as
 * runtime activity — no application restart required.
 */
@Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
@Lazy(false)
public class DeferredDatabaseStartupPoller {

    private static final Logger log = LoggerFactory.getLogger(DeferredDatabaseStartupPoller.class);

    private final DatabaseConnectivityProbe connectivityProbe;
    private final DataBootstrapRunner bootstrapRunner;
    private final ObjectProvider<LotteryDrawScheduler> lotteryDrawScheduler;

    private Boolean lastReachable;

    public DeferredDatabaseStartupPoller(
            DatabaseConnectivityProbe connectivityProbe,
            DataBootstrapRunner bootstrapRunner,
            ObjectProvider<LotteryDrawScheduler> lotteryDrawScheduler) {
        this.connectivityProbe = connectivityProbe;
        this.bootstrapRunner = bootstrapRunner;
        this.lotteryDrawScheduler = lotteryDrawScheduler;
    }

    @Scheduled(fixedDelayString = "${ticketing.startup.db-recovery-poll-ms:5000}")
    public void pollForDatabaseRecovery() {
        boolean reachable = connectivityProbe.isReachable();
        if (lastReachable != null && lastReachable && !reachable) {
            log.warn(InfrastructureErrorMessages.databaseUnavailable());
        }
        if (lastReachable != null && !lastReachable && reachable) {
            log.warn(InfrastructureErrorMessages.databaseRecovered());
        }
        lastReachable = reachable;
        if (!reachable) {
            return;
        }
        if (bootstrapRunner.hasPendingWork()) {
            log.info("Database reachable — completing deferred startup steps");
            bootstrapRunner.retryWhenDatabaseAvailable();
        }
        lotteryDrawScheduler.getObject().tryReschedulePendingStartup();
    }
}
