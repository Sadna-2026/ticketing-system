package com.ticketing.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ticketing.application.initialization.DataBootstrapRunner;

/**
 * JDBC connectivity for JPA mode (req 5): probes both datasources and reports whether deferred
 * startup (platform init / bootstrap) has finished.
 */
@Component
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
public class DatabaseConnectivityProbe {

    private final DataSource operationalDataSource;
    private final DataSource configDataSource;
    private final ObjectProvider<DataBootstrapRunner> bootstrapRunner;

    public DatabaseConnectivityProbe(
            @Qualifier("operationalDataSource") DataSource operationalDataSource,
            @Qualifier("configDataSource") DataSource configDataSource,
            ObjectProvider<DataBootstrapRunner> bootstrapRunner) {
        this.operationalDataSource = operationalDataSource;
        this.configDataSource = configDataSource;
        this.bootstrapRunner = bootstrapRunner;
    }

    /** Whether both operational and config pools can open a valid connection right now. */
    public boolean isReachable() {
        return canConnect(operationalDataSource) && canConnect(configDataSource);
    }

    /**
     * Whether DB-backed work is safe: pools reachable and no deferred bootstrap steps pending.
     * In memory mode this bean is absent — callers treat that as ready.
     */
    public boolean isReady() {
        if (!isReachable()) {
            return false;
        }
        DataBootstrapRunner runner = bootstrapRunner.getIfAvailable();
        return runner == null || !runner.hasPendingWork();
    }

    private static boolean canConnect(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }
}
