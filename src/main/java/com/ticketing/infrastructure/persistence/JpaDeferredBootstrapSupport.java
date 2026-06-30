package com.ticketing.infrastructure.persistence;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * Req 5: build a {@link LocalContainerEntityManagerFactoryBean} without eager async bootstrap so
 * Hibernate does not connect during context refresh when the database is temporarily down.
 */
public final class JpaDeferredBootstrapSupport {

    private JpaDeferredBootstrapSupport() {
    }

    public static DeferredLocalContainerEntityManagerFactoryBean deferredEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            DataSource dataSource,
            String[] entityPackages,
            String persistenceUnit,
            Map<String, Object> jpaProperties) {
        java.util.Map<String, Object> properties = new java.util.HashMap<>(jpaProperties);
        // Req 5: dialect is configured explicitly — skip JDBC metadata probe during EMF bootstrap
        // so a temporarily unreachable DB does not emit huge Hibernate stack traces.
        properties.putIfAbsent("hibernate.boot.allow_jdbc_metadata_access", false);
        LocalContainerEntityManagerFactoryBean template = builder
                .dataSource(dataSource)
                .packages(entityPackages)
                .persistenceUnit(persistenceUnit)
                .properties(properties)
                .build();
        DeferredLocalContainerEntityManagerFactoryBean factory = new DeferredLocalContainerEntityManagerFactoryBean();
        factory.setDataSource(template.getDataSource());
        factory.setJpaVendorAdapter(template.getJpaVendorAdapter());
        factory.setPersistenceUnitName(template.getPersistenceUnitName());
        factory.setPackagesToScan(entityPackages);
        factory.setJpaPropertyMap(template.getJpaPropertyMap());
        factory.setPersistenceProvider(template.getPersistenceProvider());
        factory.setJpaDialect(template.getJpaDialect());
        return factory;
    }
}
