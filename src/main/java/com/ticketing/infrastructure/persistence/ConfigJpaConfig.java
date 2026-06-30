package com.ticketing.infrastructure.persistence;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.repository.config.BootstrapMode;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;

@Configuration
@ConditionalOnProperty(name = "ticketing.persistence", havingValue = "jpa")
@EnableTransactionManagement
@EnableJpaRepositories(
        bootstrapMode = BootstrapMode.LAZY,
        basePackages = "com.ticketing.infrastructure.persistence.config",
        entityManagerFactoryRef = "configEntityManagerFactory",
        transactionManagerRef = "configTransactionManager"
)
public class ConfigJpaConfig {

    @Bean(name = "configDataSourceProperties")
    @ConfigurationProperties("spring.datasource.config")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "configDataSource")
    public DataSource dataSource(@Qualifier("configDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Lazy
    @Bean(name = "configEntityManagerFactory")
    public DeferredLocalContainerEntityManagerFactoryBean entityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("configDataSource") DataSource dataSource,
            org.springframework.boot.autoconfigure.orm.jpa.JpaProperties jpaProperties,
            org.springframework.core.env.Environment env) {

        java.util.Map<String, Object> properties = new java.util.HashMap<>(jpaProperties.getProperties());
        String ddlAuto = env.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto != null) {
            properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        }

        return JpaDeferredBootstrapSupport.deferredEntityManagerFactory(
                builder,
                dataSource,
                new String[] {
                        "com.ticketing.domain.admin",
                        "com.ticketing.domain.system"
                },
                "config",
                properties);
    }

    @Lazy
    @Bean(name = "configTransactionManager")
    public PlatformTransactionManager transactionManager(
            @Qualifier("configEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
