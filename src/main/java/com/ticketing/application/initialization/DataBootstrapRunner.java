package com.ticketing.application.initialization;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.infrastructure.logging.InfrastructureErrorMessages;
import com.ticketing.infrastructure.persistence.DatabaseConnectivityProbe;
import com.ticketing.infrastructure.persistence.DbConnectivityFailures;

/**
 * Startup hook: platform init, optional operational wipe, and data bootstrap.
 * When the database is unreachable at boot, steps are deferred and completed later by
 * {@link DeferredDatabaseStartupPoller} once connectivity returns (req 5).
 */
@Component
@Order(50)
public class DataBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrapRunner.class);

    /**
     * Selects which data bootstrap runs after platform initialization.
     */
    enum Dataset {
        DEV_SEED,
        INITIAL_STATE_FILE,
        NONE;

        static Dataset fromConfig(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String normalized = raw.trim().toUpperCase().replace('-', '_');
            return Dataset.valueOf(normalized);
        }
    }

    private final boolean initializePlatform;
    private final boolean clearDbOnStart;
    private final Dataset dataset;
    private final String initialStateFile;
    private final PlatformInitializationService platformInitializationService;
    private final DevSeedDataInitializer devSeedDataInitializer;
    private final InitialStateParser parser;
    private final InitialStateExecutor executor;
    private final OperationalDataWiper wiper;
    private final ObjectProvider<DatabaseConnectivityProbe> connectivityProbe;

    private final Object lock = new Object();
    private boolean needsPlatformInit;
    private boolean needsWipe;
    private boolean needsBootstrap;
    private boolean wipeOperationalBeforeBootstrap;

    public DataBootstrapRunner(
            @Value("${ticketing.startup.initialize-platform:true}") boolean initializePlatform,
            @Value("${ticketing.bootstrap.clear-db-on-start:false}") boolean clearDbOnStart,
            @Value("${ticketing.bootstrap.dataset:}") String configuredDataset,
            @Value("${ticketing.seed.enabled:false}") boolean seedEnabled,
            @Value("${ticketing.initial-state.file:}") String initialStateFile,
            @Lazy PlatformInitializationService platformInitializationService,
            @Lazy DevSeedDataInitializer devSeedDataInitializer,
            @Lazy InitialStateExecutor executor,
            @Lazy OperationalDataWiper wiper,
            ObjectProvider<DatabaseConnectivityProbe> connectivityProbe
    ) {
        this.initializePlatform = initializePlatform;
        this.clearDbOnStart = clearDbOnStart;
        this.dataset = resolveDataset(configuredDataset, seedEnabled, initialStateFile);
        this.initialStateFile = initialStateFile;
        this.platformInitializationService = platformInitializationService;
        this.devSeedDataInitializer = devSeedDataInitializer;
        this.parser = new InitialStateParser();
        this.executor = executor;
        this.wiper = wiper;
        this.connectivityProbe = connectivityProbe;
        this.needsPlatformInit = initializePlatform;
        this.needsWipe = clearDbOnStart;
        this.needsBootstrap = dataset != Dataset.NONE;
    }

    static Dataset resolveDataset(String configuredDataset, boolean seedEnabled, String initialStateFile) {
        Dataset explicit = Dataset.fromConfig(configuredDataset);
        if (explicit != null) {
            return explicit;
        }
        if (seedEnabled) {
            return Dataset.DEV_SEED;
        }
        if (initialStateFile != null && !initialStateFile.isBlank()) {
            return Dataset.INITIAL_STATE_FILE;
        }
        return Dataset.NONE;
    }

    @Override
    public void run(ApplicationArguments args) {
        runAtStartup();
    }

    void runAtStartup() {
        synchronized (lock) {
            if (!initializePlatform) {
                log.info("Platform initialization disabled (ticketing.startup.initialize-platform=false)");
                needsPlatformInit = false;
            }
            if (!clearDbOnStart) {
                log.info("Data bootstrap: clear-db-on-start=false (preserving existing operational data)");
                needsWipe = false;
            }
            if (!isDatabaseReachable()) {
                log.warn(InfrastructureErrorMessages.databaseUnavailable());
                return;
            }
            runPendingSteps();
        }
    }

    public void retryWhenDatabaseAvailable() {
        synchronized (lock) {
            if (!isDatabaseReachable()) {
                return;
            }
            log.info("Database reachable — completing deferred startup steps");
            runPendingSteps();
        }
    }

    public boolean hasPendingWork() {
        synchronized (lock) {
            return needsPlatformInit || needsWipe || needsBootstrap;
        }
    }

    private boolean isDatabaseReachable() {
        DatabaseConnectivityProbe probe = connectivityProbe.getIfAvailable();
        return probe == null || probe.isReachable();
    }

    private void runPendingSteps() {
        if (needsPlatformInit) {
            if (!tryInitializePlatform()) {
                return;
            }
            needsPlatformInit = false;
        }
        if (needsWipe) {
            if (!tryClearOperationalData()) {
                return;
            }
            needsWipe = false;
        }
        if (needsBootstrap) {
            tryBootstrapData();
        }
    }

    private boolean tryInitializePlatform() {
        try {
            PlatformInitializationService.InitializationResult result = platformInitializationService.initialize();
            if (!result.success()) {
                StartupHaltException.failInitialization(result.message());
            }
            log.info("Platform initialization: {}", result.message());
            return true;
        } catch (RuntimeException ex) {
            if (DbConnectivityFailures.isDeferrableAtStartup(ex)) {
                log.warn("Platform initialization deferred: {} ({})",
                        deferralReason(ex), ex.toString());
                return false;
            }
            throw ex;
        }
    }

    private boolean tryClearOperationalData() {
        log.warn("Data bootstrap: clear-db-on-start=true — wiping operational data before bootstrap");
        try {
            wiper.wipeAll();
            return true;
        } catch (RuntimeException ex) {
            if (DbConnectivityFailures.isDeferrableAtStartup(ex)) {
                log.warn("Operational wipe deferred: {} ({})",
                        deferralReason(ex), ex.toString());
                return false;
            }
            throw ex;
        }
    }

    private void tryBootstrapData() {
        if (wipeOperationalBeforeBootstrap) {
            log.warn("Bootstrap retry: wiping operational data after interrupted bootstrap");
            if (!tryWipeAfterInterruptedBootstrap()) {
                return;
            }
            wipeOperationalBeforeBootstrap = false;
        }
        try {
            switch (dataset) {
                case DEV_SEED -> {
                    log.info("Data bootstrap: dev seed dataset");
                    devSeedDataInitializer.runSeed();
                }
                case INITIAL_STATE_FILE -> runInitialStateFile();
                case NONE -> log.info("Data bootstrap: none (empty application data)");
            }
            needsBootstrap = false;
        } catch (RuntimeException ex) {
            if (DbConnectivityFailures.isDeferrableAtStartup(ex)) {
                markBootstrapInterrupted(ex);
                return;
            }
            throw ex;
        }
    }

    private boolean tryWipeAfterInterruptedBootstrap() {
        try {
            wiper.wipeAll();
            return true;
        } catch (RuntimeException ex) {
            if (DbConnectivityFailures.isDeferrableAtStartup(ex)) {
                log.warn("Operational wipe deferred during bootstrap retry: {} ({})",
                        deferralReason(ex), ex.toString());
                return false;
            }
            throw ex;
        }
    }

    private void markBootstrapInterrupted(RuntimeException ex) {
        wipeOperationalBeforeBootstrap = true;
        try {
            wiper.wipeAll();
        } catch (RuntimeException wipeEx) {
            if (!DbConnectivityFailures.isDeferrableAtStartup(wipeEx)) {
                throw wipeEx;
            }
            log.warn("Could not wipe after interrupted bootstrap ({})", deferralReason(wipeEx));
        }
        log.warn("Data bootstrap deferred: {} ({})", deferralReason(ex), ex.toString());
    }

    private static String deferralReason(RuntimeException ex) {
        return DbConnectivityFailures.isUnavailable(ex) ? "database unavailable" : "database error";
    }

    private void runInitialStateFile() {
        if (initialStateFile == null || initialStateFile.isBlank()) {
            StartupHaltException.failInitialization(
                    "ticketing.initial-state.file is blank (required when ticketing.bootstrap.dataset=initial-state-file)");
        }
        String fileLocation = initialStateFile.trim();
        log.info("Data bootstrap: initial-state file from {}", fileLocation);
        try {
            String content = InitialStateFileLoader.load(initialStateFile);
            List<InitialStateOperation> ops = parser.parse(content, initialStateFile);
            executor.execute(ops);
            log.info("Data bootstrap: applied {} operation(s) from {}", ops.size(), fileLocation);
        } catch (InitialStateParseException | InitialStateExecutionException ex) {
            wiper.wipeAll();
            throw ex;
        }
    }
}
