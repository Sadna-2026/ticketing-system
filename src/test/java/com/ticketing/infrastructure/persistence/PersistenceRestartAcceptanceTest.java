package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.notification.IPendingNotificationRepository;
import com.ticketing.domain.order.IOrderRepository;

/**
 * Req 7 / V3 persistency: operational state (companies, events, roles, orders, notifications)
 * survives a full application context restart against a file-backed database.
 */
@DisplayName("Persistence restart acceptance (JPA)")
class PersistenceRestartAcceptanceTest {

    @Configuration
    @Import({
            OperationalJpaConfig.class,
            JpaMemberRepository.class,
            JpaCompanyRepository.class,
            JpaEventRepository.class,
            JpaOrderRepository.class,
            JpaPendingNotificationRepository.class
    })
    static class OperationalRestartConfig {
    }

    @TempDir
    Path tempDir;

    private ApplicationContextRunner contextRunner;
    private String operationalDbUrl;

    @BeforeEach
    void setUp() {
        operationalDbUrl = "jdbc:h2:file:" + tempDir.resolve("operational").toAbsolutePath()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=false";
        contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class,
                        JpaRepositoriesAutoConfiguration.class))
                .withUserConfiguration(OperationalRestartConfig.class)
                .withPropertyValues(
                        "ticketing.persistence=jpa",
                        "spring.datasource.operational.url=" + operationalDbUrl,
                        "spring.datasource.operational.driver-class-name=org.h2.Driver",
                        "spring.datasource.operational.username=sa",
                        "spring.datasource.operational.password=",
                        "spring.datasource.config.url=jdbc:h2:mem:unused_cfg;DB_CLOSE_DELAY=-1",
                        "spring.datasource.config.driver-class-name=org.h2.Driver",
                        "spring.datasource.config.username=sa",
                        "spring.datasource.config.password=",
                        "spring.jpa.open-in-view=false");
    }

    @Test
    void GivenRichOperationalState_WhenContextRestarts_ThenAllAggregatesRehydrate() {
        final PersistenceRestartFixtures.Snapshot[] snapshotHolder = new PersistenceRestartFixtures.Snapshot[1];

        contextRunner.withPropertyValues("spring.jpa.hibernate.ddl-auto=create").run(context -> {
            var tx = new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            snapshotHolder[0] = tx.execute(status -> {
                var seeded = PersistenceRestartFixtures.seed(
                        context.getBean(IMemberRepository.class),
                        context.getBean(ICompanyRepository.class),
                        context.getBean(IEventRepository.class),
                        context.getBean(IOrderRepository.class),
                        context.getBean(IPendingNotificationRepository.class));
                PersistenceRestartFixtures.assertStatePresent(
                        context.getBean(IMemberRepository.class),
                        context.getBean(ICompanyRepository.class),
                        context.getBean(IEventRepository.class),
                        context.getBean(IOrderRepository.class),
                        context.getBean(IPendingNotificationRepository.class),
                        seeded);
                return seeded;
            });
            assertThat(snapshotHolder[0]).isNotNull();
        });

        PersistenceRestartFixtures.Snapshot expected = snapshotHolder[0];
        contextRunner.withPropertyValues("spring.jpa.hibernate.ddl-auto=update").run(context -> {
            var tx = new TransactionTemplate(context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> PersistenceRestartFixtures.assertStatePresent(
                    context.getBean(IMemberRepository.class),
                    context.getBean(ICompanyRepository.class),
                    context.getBean(IEventRepository.class),
                    context.getBean(IOrderRepository.class),
                    context.getBean(IPendingNotificationRepository.class),
                    expected));
        });
    }
}
