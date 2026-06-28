package com.ticketing.application.initialization;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.PlatformInitializationService;

/**
 * Startup hook that (1) initializes the platform, then (2) loads exactly one optional
 * data source: dev seed, an initial-state file, or none.
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
    private final Dataset dataset;
    private final String initialStateFile;
    private final PlatformInitializationService platformInitializationService;
    private final DevSeedDataInitializer devSeedDataInitializer;
    private final InitialStateParser parser;
    private final InitialStateExecutor executor;
    private final OperationalDataWiper wiper;

    public DataBootstrapRunner(
            @Value("${ticketing.startup.initialize-platform:true}") boolean initializePlatform,
            @Value("${ticketing.bootstrap.dataset:}") String configuredDataset,
            @Value("${ticketing.seed.enabled:false}") boolean seedEnabled,
            @Value("${ticketing.initial-state.file:}") String initialStateFile,
            PlatformInitializationService platformInitializationService,
            DevSeedDataInitializer devSeedDataInitializer,
            InitialStateExecutor executor,
            OperationalDataWiper wiper
    ) {
        this.initializePlatform = initializePlatform;
        this.dataset = resolveDataset(configuredDataset, seedEnabled, initialStateFile);
        this.initialStateFile = initialStateFile;
        this.platformInitializationService = platformInitializationService;
        this.devSeedDataInitializer = devSeedDataInitializer;
        this.parser = new InitialStateParser();
        this.executor = executor;
        this.wiper = wiper;
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
        initializePlatform();
        bootstrapData();
    }

    private void initializePlatform() {
        if (!initializePlatform) {
            log.info("Platform initialization disabled (ticketing.startup.initialize-platform=false)");
            return;
        }
        PlatformInitializationService.InitializationResult result = platformInitializationService.initialize();
        if (!result.success()) {
            StartupHaltException.failInitialization(result.message());
        }
        log.info("Platform initialization: {}", result.message());
    }

    private void bootstrapData() {
        switch (dataset) {
            case DEV_SEED -> {
                log.info("Data bootstrap: dev seed dataset");
                devSeedDataInitializer.runSeed();
            }
            case INITIAL_STATE_FILE -> runInitialStateFile();
            case NONE -> log.info("Data bootstrap: none (empty application data)");
        }
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
