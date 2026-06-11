package com.ticketing.application.initialization;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional startup hook (V3-15) that brings the platform into a known state from an
 * initial-state file. It is <strong>off by default</strong>: it only does anything when the
 * {@code ticketing.initial-state.file} property points at a readable file. When unset (the
 * default), {@link #run(ApplicationArguments)} is a no-op, so normal startup and the existing
 * tests are unaffected and {@link DevSeedDataInitializer} is left to seed as before.
 *
 * <p>When enabled, the file is read, parsed by {@link InitialStateParser}, and the resulting
 * operations are executed by {@link InitialStateExecutor} against the application layer. A
 * parse or execution failure aborts startup with a clear error (the thrown exception
 * propagates out of the runner), keeping the all-or-nothing contract.
 *
 * <p>Ordered after {@link DevSeedDataInitializer} so that, if both are active, platform
 * initialization and any dev seed run first.
 */
@Component
@Order(100)
public class InitialStateRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialStateRunner.class);

    private final String initialStateFile;
    private final InitialStateParser parser;
    private final InitialStateExecutor executor;

    public InitialStateRunner(
            @Value("${ticketing.initial-state.file:}") String initialStateFile,
            InitialStateExecutor executor
    ) {
        this.initialStateFile = initialStateFile;
        this.parser = new InitialStateParser();
        this.executor = executor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (initialStateFile == null || initialStateFile.isBlank()) {
            log.info("Initial-state file: not configured (ticketing.initial-state.file unset) — skipping");
            return;
        }

        Path path = Path.of(initialStateFile.trim());
        if (!Files.isReadable(path)) {
            throw new IllegalStateException(
                    "Initial-state file is not readable: " + path.toAbsolutePath());
        }

        log.info("Initial-state file: loading from {}", path.toAbsolutePath());
        List<InitialStateOperation> ops = parser.parse(path);
        executor.execute(ops);
        log.info("Initial-state file: applied {} operation(s) from {}", ops.size(), path.toAbsolutePath());
    }
}
