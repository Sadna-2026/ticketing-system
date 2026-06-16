package com.ticketing.application.initialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.initialization.DataBootstrapRunner.Dataset;

@DisplayName("DataBootstrapRunner dataset resolution")
class DataBootstrapRunnerTest {

    @Test
    @DisplayName("Explicit dataset overrides legacy seed flag")
    void givenExplicitDataset_whenResolve_thenUsesExplicit() {
        assertEquals(Dataset.INITIAL_STATE_FILE,
                DataBootstrapRunner.resolveDataset("initial-state-file", true, ""));
        assertEquals(Dataset.NONE,
                DataBootstrapRunner.resolveDataset("none", true, "file.txt"));
    }

    @Test
    @DisplayName("Legacy seed.enabled selects dev seed when dataset unset")
    void givenSeedEnabled_whenResolve_thenDevSeed() {
        assertEquals(Dataset.DEV_SEED,
                DataBootstrapRunner.resolveDataset("", true, ""));
    }

    @Test
    @DisplayName("Initial-state file without seed selects file bootstrap")
    void givenInitialStateFile_whenResolve_thenInitialStateFile() {
        assertEquals(Dataset.INITIAL_STATE_FILE,
                DataBootstrapRunner.resolveDataset("", false, "classpath:initial-state/staff-demo-v3.txt"));
    }

    @Test
    @DisplayName("No seed and no file selects none")
    void givenNoSeedAndNoFile_whenResolve_thenNone() {
        assertEquals(Dataset.NONE,
                DataBootstrapRunner.resolveDataset("", false, ""));
    }
}
