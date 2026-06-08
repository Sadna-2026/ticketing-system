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
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;

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
            JpaMemberRepository.class,
            JpaCompanyRepository.class
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
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(IMemberRepository.class);
            assertThat(context).hasSingleBean(ICompanyRepository.class);
            assertThat(context.getBean(IMemberRepository.class))
                    .isInstanceOf(InMemoryMemberRepository.class);
            assertThat(context.getBean(ICompanyRepository.class))
                    .isInstanceOf(InMemoryCompanyRepository.class);
        });
    }

    @Test
    void GivenMemoryConfig_WhenContextStarts_ThenInMemoryReposAreTheSingleActiveBeans() {
        contextRunner.withPropertyValues("ticketing.persistence=memory").run(context -> {
            assertThat(context).hasSingleBean(IMemberRepository.class);
            assertThat(context).hasSingleBean(ICompanyRepository.class);
            assertThat(context.getBean(IMemberRepository.class))
                    .isInstanceOf(InMemoryMemberRepository.class);
            assertThat(context.getBean(ICompanyRepository.class))
                    .isInstanceOf(InMemoryCompanyRepository.class);
        });
    }

    @Test
    void GivenJpaConfig_WhenContextStarts_ThenJpaReposAreTheSingleActiveBeans() {
        contextRunner.withPropertyValues("ticketing.persistence=jpa").run(context -> {
            assertThat(context).hasSingleBean(IMemberRepository.class);
            assertThat(context).hasSingleBean(ICompanyRepository.class);
            assertThat(context.getBean(IMemberRepository.class))
                    .isInstanceOf(JpaMemberRepository.class);
            assertThat(context.getBean(ICompanyRepository.class))
                    .isInstanceOf(JpaCompanyRepository.class);
        });
    }
}
