package com.ticketing.infrastructure.persistence;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import com.ticketing.infrastructure.logging.InfrastructureErrorMessages;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;

/**
 * Req 5: does not open a JDBC connection during Spring context refresh. Hibernate builds the
 * {@link EntityManagerFactory} on first use instead, so the application can start when the
 * database is temporarily unreachable at boot.
 *
 * <p>If initialization fails due to a connectivity issue, the factory remembers the failure and
 * retries on the next access — so the app can recover without restart once the DB returns.
 */
public class DeferredLocalContainerEntityManagerFactoryBean extends LocalContainerEntityManagerFactoryBean {

    private static final Logger log = LoggerFactory.getLogger(DeferredLocalContainerEntityManagerFactoryBean.class);

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicReference<EntityManagerFactory> cachedEmf = new AtomicReference<>();

    @Override
    public void afterPropertiesSet() throws PersistenceException {
        // Defer Hibernate bootstrap until something requests the EntityManagerFactory.
    }

    @Override
    public EntityManagerFactory getObject() {
        EntityManagerFactory emf = cachedEmf.get();
        if (emf != null) {
            return emf;
        }
        return ensureInitialized();
    }

    @Override
    public Class<? extends EntityManagerFactory> getObjectType() {
        return EntityManagerFactory.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public void destroy() {
        if (initialized.get()) {
            super.destroy();
        }
    }

    private EntityManagerFactory ensureInitialized() {
        synchronized (this) {
            EntityManagerFactory emf = cachedEmf.get();
            if (emf != null) {
                return emf;
            }
            log.debug("Initializing deferred EntityManagerFactory for persistence unit '{}'", getPersistenceUnitName());
            try {
                super.afterPropertiesSet();
                initialized.set(true);
                emf = super.getObject();
                cachedEmf.set(emf);
                log.info("Initialized JPA EntityManagerFactory for persistence unit '{}'", getPersistenceUnitName());
                return emf;
            } catch (RuntimeException ex) {
                if (DbConnectivityFailures.isDeferrableAtStartup(ex)) {
                    String msg = InfrastructureErrorMessages.summarize(ex);
                    if (msg != null) {
                        log.warn(msg);
                    } else {
                        log.warn("Database unreachable — EntityManagerFactory for '{}' not initialized ({})",
                                getPersistenceUnitName(), ex.toString());
                    }
                } else {
                    log.error("Failed to initialize JPA EntityManagerFactory for '{}': {}",
                            getPersistenceUnitName(), ex.getMessage());
                }
                throw ex;
            }
        }
    }
}
