package com.ticketing.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import jakarta.persistence.EntityManagerFactory;

class JpaH2ConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    HibernateJpaAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.datasource.url=jdbc:h2:mem:ticketing-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
                    "spring.datasource.driver-class-name=org.h2.Driver",
                    "spring.datasource.username=sa",
                    "spring.datasource.password=",
                    "spring.jpa.hibernate.ddl-auto=none",
                    "spring.jpa.open-in-view=false"
            );

    @Test
    void GivenJpaAndH2Dependencies_WhenAutoConfigurationRuns_ThenH2DataSourceAndEntityManagerAreAvailable() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(DataSource.class);
            assertThat(context).hasSingleBean(EntityManagerFactory.class);
            assertThat(context.getBean(DataSource.class).getConnection().getMetaData().getURL())
                    .startsWith("jdbc:h2:mem:ticketing-test");
        });
    }

    @Test
    void GivenPersistenceDependencies_WhenClasspathIsChecked_ThenSpringDataJpaHibernateAndH2ArePresent()
            throws ClassNotFoundException {
        Class.forName("org.springframework.data.jpa.repository.JpaRepository");
        Class.forName("org.hibernate.Session");
        Class.forName("org.h2.Driver");
    }
}
