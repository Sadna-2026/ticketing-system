package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.lottery.ILotteryRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryLotteryRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

/**
 * V3-7: the repository backend is selected by the {@code ticketing.persistence}
 * property and exactly one bean per interface is active in each mode. Uses an
 * {@link ApplicationContextRunner} (the existing pattern in
 * {@code JpaH2ConfigurationTest}) so the full Vaadin/web app does not boot.
 */
@DisplayName("Persistence backend selection by config")
class PersistenceProfileSelectionTest {

    /**
     * Registers both InMemory beans and the JPA adapters plus the Spring Data /
     * JPA infrastructure they depend on, then lets {@code @ConditionalOnProperty}
     * pick the active pair.
     */
    @Configuration
    @EntityScan(basePackages = "com.ticketing.domain")
    @org.springframework.context.annotation.Import({
            InMemoryMemberRepository.class,
            InMemoryCompanyRepository.class,
            InMemoryEventRepository.class,
            InMemoryOrderRepository.class,
            InMemoryLotteryRepository.class,
            JpaMemberRepository.class,
            JpaCompanyRepository.class,
            JpaEventRepository.class,
            JpaOrderRepository.class,
            JpaLotteryRepository.class
    })
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories(
            basePackageClasses = { MemberJpaRepository.class })
    static class RepositoryWiringConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class,
                    JpaRepositoriesAutoConfiguration.class
            ))
            .withUserConfiguration(RepositoryWiringConfig.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:selection-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=create-drop",
                    "spring.jpa.open-in-view=false"
            );

    @Test
    void GivenDefaultConfig_WhenContextStarts_ThenInMemoryReposAreTheSingleActiveBeans() {
        contextRunner.run(PersistenceProfileSelectionTest::assertInMemoryReposAreSingleActiveBeans);
    }

    @Test
    void GivenMemoryConfig_WhenContextStarts_ThenInMemoryReposAreTheSingleActiveBeans() {
        contextRunner.withPropertyValues("ticketing.persistence=memory")
                .run(PersistenceProfileSelectionTest::assertInMemoryReposAreSingleActiveBeans);
    }

    @Test
    void GivenJpaConfig_WhenContextStarts_ThenJpaReposAreTheSingleActiveBeans() {
        contextRunner.withPropertyValues("ticketing.persistence=jpa").run(context -> {
            assertThat(context).hasSingleBean(IMemberRepository.class);
            assertThat(context).hasSingleBean(ICompanyRepository.class);
            assertThat(context).hasSingleBean(IEventRepository.class);
            assertThat(context).hasSingleBean(IOrderRepository.class);
            assertThat(context).hasSingleBean(ILotteryRepository.class);
            assertThat(context.getBean(IMemberRepository.class))
                    .isInstanceOf(JpaMemberRepository.class);
            assertThat(context.getBean(ICompanyRepository.class))
                    .isInstanceOf(JpaCompanyRepository.class);
            assertThat(context.getBean(IEventRepository.class))
                    .isInstanceOf(JpaEventRepository.class);
            assertThat(context.getBean(IOrderRepository.class))
                    .isInstanceOf(JpaOrderRepository.class);
            assertThat(context.getBean(ILotteryRepository.class))
                    .isInstanceOf(JpaLotteryRepository.class);
        });
    }

    private static void assertInMemoryReposAreSingleActiveBeans(
            org.springframework.boot.test.context.assertj.AssertableApplicationContext context) {
        assertThat(context).hasSingleBean(IMemberRepository.class);
        assertThat(context).hasSingleBean(ICompanyRepository.class);
        assertThat(context).hasSingleBean(IEventRepository.class);
        assertThat(context).hasSingleBean(IOrderRepository.class);
        assertThat(context).hasSingleBean(ILotteryRepository.class);
        assertThat(context.getBean(IMemberRepository.class))
                .isInstanceOf(InMemoryMemberRepository.class);
        assertThat(context.getBean(ICompanyRepository.class))
                .isInstanceOf(InMemoryCompanyRepository.class);
        assertThat(context.getBean(IEventRepository.class))
                .isInstanceOf(InMemoryEventRepository.class);
        assertThat(context.getBean(IOrderRepository.class))
                .isInstanceOf(InMemoryOrderRepository.class);
        assertThat(context.getBean(ILotteryRepository.class))
                .isInstanceOf(InMemoryLotteryRepository.class);
    }
}
