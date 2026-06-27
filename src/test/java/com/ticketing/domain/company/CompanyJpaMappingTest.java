package com.ticketing.domain.company;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.SimpleDiscount;

/**
 * V3-3: the Company aggregate persists and round-trips via H2 (JPA).
 * Uses @DataJpaTest (embedded H2) with ddl-auto=create-drop so the schema is
 * built for the test even though the app config sets ddl-auto=none.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DisplayName("Company JPA mapping")
class CompanyJpaMappingTest {

    @Autowired
    private TestEntityManager em;

    @Test
    void GivenCompany_WhenPersistedAndReloaded_ThenScalarsAndStatusSurvive() {
        UUID founderId = UUID.randomUUID();
        Company company = new Company("Acme Productions", "A demo company", founderId);
        company.setAllowDiscountStacking(true);
        company.suspend();

        em.persistAndFlush(company);
        em.clear();

        Company found = em.find(Company.class, "Acme Productions");
        assertNotNull(found);
        assertEquals("Acme Productions", found.getName());
        assertEquals("A demo company", found.getDescription());
        assertEquals(founderId, found.getFounderId());
        assertEquals(CompanyStatus.SUSPENDED, found.getStatus());
        assertTrue(found.isAllowDiscountStacking());
    }

    @Test
    void GivenCompanyWithPolicies_WhenPersistedAndReloaded_ThenPoliciesRoundTrip() {
        Company company = new Company("Policy Co", "desc", UUID.randomUUID());
        company.setPurchasePolicy(new MaxQuantityPolicy(5));
        company.setDiscountPolicy(new SimpleDiscount(new BigDecimal("10")));

        em.persistAndFlush(company);
        em.clear();

        Company found = em.find(Company.class, "Policy Co");
        assertInstanceOf(MaxQuantityPolicy.class, found.getPurchasePolicy());
        assertEquals(5, ((MaxQuantityPolicy) found.getPurchasePolicy()).getMaxTickets());
        assertInstanceOf(SimpleDiscount.class, found.getDiscountPolicy());
        assertEquals(0, new BigDecimal("10").compareTo(((SimpleDiscount) found.getDiscountPolicy()).getPercentOff()));
    }

    @Test
    void GivenCompany_WhenReloaded_ThenVersionPresentAndDefaultPoliciesRoundTrip() {
        Company company = new Company("Versioned Co", "desc", UUID.randomUUID());

        em.persistAndFlush(company);
        em.clear();

        Company found = em.find(Company.class, "Versioned Co");
        assertEquals(0, found.getVersion()); // @Version initialised on insert
        assertInstanceOf(AlwaysAllowPolicy.class, found.getPurchasePolicy());
        assertInstanceOf(NoDiscountPolicy.class, found.getDiscountPolicy());
    }
}
