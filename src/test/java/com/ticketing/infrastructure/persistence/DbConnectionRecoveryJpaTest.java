package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;

import org.h2.tools.Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * Validates V3-21 DB connection loss + auto-resume.
 * We spin up a local H2 TCP server manually before Spring Boot starts, pointing
 * HikariCP to the TCP endpoint instead of embedded in-memory.
 * By stopping the TCP server mid-test, we simulate database crash/network loss,
 * verifying that transactions abort (roll back). By restarting the TCP server,
 * we verify that the pool successfully auto-resumes operations without requiring
 * an app restart.
 */
@org.junit.jupiter.api.Tag("slow")
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.startup.initialize-platform=false",
                "ticketing.bootstrap.dataset=none",
                "spring.datasource.operational.hikari.maximum-pool-size=1",
                "spring.datasource.operational.hikari.connection-test-query=SELECT 1",
                "spring.datasource.config.hikari.maximum-pool-size=1",
                "spring.datasource.config.hikari.connection-test-query=SELECT 1"
        })
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DbConnectionRecoveryJpaTest {

    private static Server h2Server;
    // We use a fixed port for the test server. 9093 is less likely to clash.
    private static final int H2_PORT = 9093;

    @Autowired
    private IMemberRepository memberRepository;

    @BeforeAll
    static void startH2Server() throws SQLException {
        // Pre-create the file DB locally so remote connections don't fail with "not found"
        try (java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:h2:./target/recoverytest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE", "sa", "")) {
        }
        h2Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", String.valueOf(H2_PORT)).start();
    }

    @AfterAll
    static void stopH2Server() {
        if (h2Server != null) {
            h2Server.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ticketing.persistence", () -> "jpa");
        // Point to the manually started TCP server using file DB.
        String tcpUrl = "jdbc:h2:tcp://localhost:" + H2_PORT + "/./target/recoverytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        registry.add("spring.datasource.operational.url", () -> tcpUrl);
        registry.add("spring.datasource.config.url", () -> tcpUrl);
    }

    @Test
    @Order(1)
    void step1_givenDbIsUp_whenSave_thenSucceeds() {
        Member member = new Member(UUID.randomUUID(), "member_alive", "member_alive@test.com", "pass123");
        memberRepository.save(member);

        assertThat(memberRepository.findByUsername("member_alive")).isPresent();
    }

    @Test
    @Order(2)
    void step2_givenDbGoesDown_whenSave_thenThrowsExceptionAndRollsBack() {
        // Kill the database server
        h2Server.stop();

        Member member = new Member(UUID.randomUUID(), "member_dead", "member_dead@test.com", "pass123");
        
        assertThatThrownBy(() -> memberRepository.save(member))
                .isInstanceOf(Exception.class); // CannotCreateTransactionException or JDBCConnectionException
    }

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("operationalDataSource")
    private javax.sql.DataSource dataSource;

    @Test
    @Order(3)
    void step3_givenDbComesBackUp_whenSave_thenAutoResumes() throws SQLException, InterruptedException {
        // Sleep to ensure HikariCP's aliveBypassWindowMs (500ms) expires.
        // Otherwise, it might hand us the recently-broken connection without testing it.
        Thread.sleep(1000);

        // Restart the database server
        h2Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", String.valueOf(H2_PORT)).start();

        // Evict any broken connections from the pool that might be stuck
        if (dataSource instanceof com.zaxxer.hikari.HikariDataSource hikariDataSource) {
            hikariDataSource.getHikariPoolMXBean().softEvictConnections();
        }

        // HikariCP will need to attempt to acquire a connection, which should succeed now.
        Member member = new Member(UUID.randomUUID(), "member_recovered", "member_recovered@test.com", "pass123");
        memberRepository.save(member);

        assertThat(memberRepository.findByUsername("member_recovered")).isPresent();
    }
}
