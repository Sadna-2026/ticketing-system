package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.ticketing.application.initialization.DataBootstrapRunner;

@DisplayName("DatabaseConnectivityProbe")
class DatabaseConnectivityProbeTest {

    @Test
    void isReachable_whenBothPoolsConnect_returnsTrue() throws SQLException {
        DatabaseConnectivityProbe probe = probeWith(reachableDataSource(), reachableDataSource(), unusedBootstrap());

        assertThat(probe.isReachable()).isTrue();
        assertThat(probe.isReady()).isTrue();
    }

    @Test
    void isReachable_whenOperationalPoolFails_returnsFalse() throws SQLException {
        DatabaseConnectivityProbe probe = probeWith(unreachableDataSource(), reachableDataSource(), unusedBootstrap());

        assertThat(probe.isReachable()).isFalse();
        assertThat(probe.isReady()).isFalse();
    }

    @Test
    void isReady_whenBootstrapPending_returnsFalseEvenIfReachable() throws SQLException {
        DataBootstrapRunner bootstrap = mock(DataBootstrapRunner.class);
        when(bootstrap.hasPendingWork()).thenReturn(true);
        ObjectProvider<DataBootstrapRunner> bootstrapProvider = mock(ObjectProvider.class);
        when(bootstrapProvider.getIfAvailable()).thenReturn(bootstrap);

        DatabaseConnectivityProbe probe = probeWith(
                reachableDataSource(), reachableDataSource(), bootstrapProvider);

        assertThat(probe.isReachable()).isTrue();
        assertThat(probe.isReady()).isFalse();
    }

    @Test
    void isReady_whenBootstrapDone_returnsTrue() throws SQLException {
        DataBootstrapRunner bootstrap = mock(DataBootstrapRunner.class);
        when(bootstrap.hasPendingWork()).thenReturn(false);
        ObjectProvider<DataBootstrapRunner> bootstrapProvider = mock(ObjectProvider.class);
        when(bootstrapProvider.getIfAvailable()).thenReturn(bootstrap);

        DatabaseConnectivityProbe probe = probeWith(
                reachableDataSource(), reachableDataSource(), bootstrapProvider);

        assertThat(probe.isReady()).isTrue();
    }

    private static DatabaseConnectivityProbe probeWith(
            DataSource operational,
            DataSource config,
            ObjectProvider<DataBootstrapRunner> bootstrapProvider) {
        return new DatabaseConnectivityProbe(operational, config, bootstrapProvider);
    }

    private static DataSource reachableDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        return dataSource;
    }

    private static DataSource unreachableDataSource() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));
        return dataSource;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<DataBootstrapRunner> unusedBootstrap() {
        return mock(ObjectProvider.class);
    }
}
