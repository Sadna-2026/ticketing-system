package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.system.StartupConfiguration;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier;
import com.ticketing.presentation.vaadin.util.PresenterErrorClassifier.Category;

/**
 * Req 5 / advisor "boot with dead DB": the Spring context must start when the database
 * is unreachable at boot; DB-backed actions fail with a classified connectivity error;
 * once the database returns, operations resume without an application restart.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.main.lazy-initialization=true",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.startup.initialize-platform=false",
                "ticketing.bootstrap.dataset=none",
                "ticketing.seed.enabled=false",
                "ticketing.external.base-url=",
                "spring.datasource.operational.hikari.maximum-pool-size=1",
                "spring.datasource.operational.hikari.connection-test-query=SELECT 1",
                "spring.datasource.config.hikari.maximum-pool-size=1",
                "spring.datasource.config.hikari.connection-test-query=SELECT 1"
        })
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbUnavailableAtBootJpaTest {

    private static final int H2_PORT = 9094;
    private static Server h2Server;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Lazy
    private IMemberRepository memberRepository;

    @Autowired
    private StartupConfiguration startupConfiguration;

    @Autowired
    @Qualifier("operationalDataSource")
    private javax.sql.DataSource dataSource;

    @Autowired
    @Qualifier("configDataSource")
    private javax.sql.DataSource configDataSource;

    @DynamicPropertySource
    static void configureDeadDatabase(DynamicPropertyRegistry registry) {
        // No H2 TCP server is started before the context loads — port 9094 is closed.
        String deadUrl = "jdbc:h2:tcp://localhost:" + H2_PORT
                + "/./target/boot-dead-db-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
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
    void step1_givenDbDownAtBoot_whenContextStarts_thenNonDbBeansAreAvailable() {
        assertThat(applicationContext).isNotNull();
        assertThat(startupConfiguration).isNotNull();
    }

    @Test
    @Order(2)
    void step2_givenDbStillDown_whenRepositorySave_thenFailsWithClassifiedDbUnavailableError() {
        Member member = new Member(UUID.randomUUID(), "member_boot_dead", "boot_dead@test.com", "pass123");

        assertThatThrownBy(() -> memberRepository.save(member))
                .satisfies(ex -> assertThat(PresenterErrorClassifier.classify(ex))
                        .isEqualTo(Category.DB_UNAVAILABLE));
    }

    @Test
    @Order(3)
    void step3_givenDbComesUp_whenRepositorySave_thenSucceedsWithoutRestart() throws Exception {
        try (java.sql.Connection ignored = java.sql.DriverManager.getConnection(
                "jdbc:h2:./target/boot-dead-db-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "")) {
        }
        h2Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", String.valueOf(H2_PORT)).start();
        Thread.sleep(500);

        refreshPoolAfterDatabaseReturns(dataSource);
        refreshPoolAfterDatabaseReturns(configDataSource);

        Member member = new Member(UUID.randomUUID(), "member_boot_recovered", "boot_recovered@test.com", "pass123");
        memberRepository.save(member);

        assertThat(memberRepository.findByUsername("member_boot_recovered")).isPresent();
        assertThat(PresenterErrorClassifier.userFacingMessage(Category.DB_UNAVAILABLE))
                .containsIgnoringCase("database");
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
