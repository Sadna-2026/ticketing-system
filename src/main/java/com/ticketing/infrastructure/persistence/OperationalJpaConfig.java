package com.ticketing.infrastructure.persistence;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.ticketing.infrastructure.persistence.config.AdminJpaRepository;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.ticketing.infrastructure.persistence",
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {AdminJpaRepository.class}
        ),
        entityManagerFactoryRef = "operationalEntityManagerFactory",
        transactionManagerRef = "transactionManager" // Spring default name for @Primary
)
public class OperationalJpaConfig {

    @Primary
    @Bean(name = "operationalDataSourceProperties")
    @ConfigurationProperties("spring.datasource.operational")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "operationalDataSource")
    public DataSource dataSource(@Qualifier("operationalDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean(name = "operationalEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("operationalDataSource") DataSource dataSource,
            org.springframework.boot.autoconfigure.orm.jpa.JpaProperties jpaProperties,
            org.springframework.core.env.Environment env) {

        java.util.Map<String, Object> properties = new java.util.HashMap<>(jpaProperties.getProperties());
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto != null) {
            properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        }

        return builder
                .dataSource(dataSource)
                .packages(
                        "com.ticketing.domain.company",
                        "com.ticketing.domain.event",
                        "com.ticketing.domain.lottery",
                        "com.ticketing.domain.member",
                        "com.ticketing.domain.order",
                        "com.ticketing.domain.queue",
                        "com.ticketing.domain.ticket",
                        "com.ticketing.domain.notification"
                )
                .persistenceUnit("operational")
                .properties(properties)
                .build();
    }

    @Primary
    @Bean(name = "transactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("operationalEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
