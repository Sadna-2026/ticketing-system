package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.h2.tools.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticketing.application.initialization.DataBootstrapRunner;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * Req 5: startup deferred when DB is down at boot completes without restart once the
 * database becomes reachable (same pools as runtime recovery).
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.main.lazy-initialization=true",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.startup.initialize-platform=false",
                "ticketing.bootstrap.dataset=initial-state-file",
                "ticketing.initial-state.file=classpath:initial-state/deferred-boot.txt",
                "ticketing.seed.enabled=false",
                "ticketing.external.base-url=",
                "ticketing.startup.db-recovery-poll-ms=1000000",
                "spring.datasource.operational.hikari.maximum-pool-size=1",
                "spring.datasource.config.hikari.maximum-pool-size=1"
        })
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbDeferredStartupJpaTest {

    private static final int H2_PORT = 9095;
    private static Server h2Server;

    @Autowired
    private DataBootstrapRunner bootstrapRunner;

    @Autowired
    @Lazy
    private IMemberRepository memberRepository;

    @Autowired
    @Qualifier("operationalDataSource")
    private javax.sql.DataSource dataSource;

    @Autowired
    @Qualifier("configDataSource")
    private javax.sql.DataSource configDataSource;

    @DynamicPropertySource
    static void configureDeadDatabase(DynamicPropertyRegistry registry) {
        String deadUrl = "jdbc:h2:tcp://localhost:" + H2_PORT
                + "/./target/deferred-startup-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.operational.url", () -> deadUrl);
        registry.add("spring.datasource.config.url", () -> deadUrl);
    }

    @AfterAll
    static void stopH2Server() {
        if (h2Server != null) {
            h2Server.stop();
        }
    }

    @Test
    @Order(1)
    void step1_givenDbDownAtBoot_whenContextStarts_thenBootstrapIsPending() {
        assertThat(bootstrapRunner.hasPendingWork()).isTrue();
    }

    @Test
    @Order(2)
    void step2_givenDbReturns_whenRetryStartup_thenInitialStateIsAppliedWithoutRestart() throws Exception {
        try (java.sql.Connection ignored = java.sql.DriverManager.getConnection(
                "jdbc:h2:./target/deferred-startup-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "")) {
        }
        h2Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", String.valueOf(H2_PORT)).start();
        Thread.sleep(500);

        refreshPoolAfterDatabaseReturns(dataSource);
        refreshPoolAfterDatabaseReturns(configDataSource);

        bootstrapRunner.retryWhenDatabaseAvailable();

        assertThat(bootstrapRunner.hasPendingWork()).isFalse();
        assertThat(memberRepository.findByUsername("deferred_user")).isPresent();

        Member probe = new Member(UUID.randomUUID(), "post_recovery", "post@test.com", "pass123");
        memberRepository.save(probe);
        assertThat(memberRepository.findByUsername("post_recovery")).isPresent();
    }

    private static void refreshPoolAfterDatabaseReturns(javax.sql.DataSource dataSource) throws java.sql.SQLException {
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDataSource) {
            com.zaxxer.hikari.HikariPoolMXBean poolMx = hikariDataSource.getHikariPoolMXBean();
            if (poolMx != null) {
                poolMx.softEvictConnections();
            } else {
                try (java.sql.Connection ignored = hikariDataSource.getConnection()) {
                }
            }
        }
    }
}
