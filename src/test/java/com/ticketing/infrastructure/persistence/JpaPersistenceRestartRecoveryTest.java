package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;

/**
 * V3-22: Persistence restart recovery test.
 *
 * <p>Verifies that data written to a file-backed H2 database survives a complete
 * application context restart (i.e. simulated application restart).
 */
@DisplayName("Persistence restart recovery test")
class JpaPersistenceRestartRecoveryTest {

    @Configuration
    @EntityScan(basePackages = "com.ticketing.domain")
    @Import({ JpaMemberRepository.class })
    @EnableJpaRepositories(basePackageClasses = { MemberJpaRepository.class })
    static class RestartRecoveryTestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    JpaRepositoriesAutoConfiguration.class
            ))
            .withUserConfiguration(RestartRecoveryTestConfig.class)
            .withPropertyValues(
                    "ticketing.persistence=jpa",
                    "spring.datasource.url=jdbc:h2:file:./target/recovery-test-db/db;MODE=PostgreSQL",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.open-in-view=false"
            );

    @Test
    void GivenFileBackedDatabase_WhenContextRestarts_ThenStateSurvives() {
        UUID memberId = UUID.randomUUID();
        
        // 1. Start context, create schema, and save data
        contextRunner
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=create") // drop on startup, keep on shutdown
                .run(context -> {
                    org.springframework.transaction.PlatformTransactionManager ptm = 
                            context.getBean(org.springframework.transaction.PlatformTransactionManager.class);
                    IMemberRepository repository = context.getBean(IMemberRepository.class);

                    new org.springframework.transaction.support.TransactionTemplate(ptm).executeWithoutResult(status -> {
                        Member member = new Member(memberId, "recovery_user", "recovery@test.com", "pass123");
                        repository.save(member);
                        
                        assertThat(repository.findById(memberId)).isPresent();
                    });
                });

        // 2. Start a new context (simulating restart), reusing schema and data
        contextRunner
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=update") // Do not drop
                .run(context -> {
                    org.springframework.transaction.PlatformTransactionManager ptm = 
                            context.getBean(org.springframework.transaction.PlatformTransactionManager.class);
                    IMemberRepository repository = context.getBean(IMemberRepository.class);
                    
                    new org.springframework.transaction.support.TransactionTemplate(ptm).executeWithoutResult(status -> {
                        var found = repository.findById(memberId);
                        assertThat(found).isPresent();
                        assertThat(found.get().getUsername()).isEqualTo("recovery_user");
                        assertThat(found.get().getEmail()).isEqualTo("recovery@test.com");
                    });
                });
    }
}
