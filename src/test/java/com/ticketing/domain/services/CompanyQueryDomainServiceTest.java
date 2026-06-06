package com.ticketing.domain.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.services.CompanyService;
import com.ticketing.domain.company.Company;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventRepository;

@DisplayName("CompanyService.searchCompanies")
class CompanyQueryDomainServiceTest {

    private InMemoryCompanyRepository companyRepository;
    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyRepository = new InMemoryCompanyRepository();
        companyService = new CompanyService(companyRepository, null, null, null, new InMemoryEventRepository(), null, null);
    }

    @Test
    void GivenActiveCompanies_WhenSearchingWithBlankQuery_ThenAllActiveCompaniesReturnedSortedByName() {
        companyRepository.save(new Company("Acme", "desc", UUID.randomUUID()));
        companyRepository.save(new Company("Beta", "desc", UUID.randomUUID()));

        List<CompanySummaryDTO> results = companyService.searchCompanies("");

        assertEquals(List.of("Acme", "Beta"), results.stream().map(CompanySummaryDTO::name).toList());
    }

    @Test
    void GivenPartialQuery_WhenSearchingCompanies_ThenOnlyMatchingNamesReturned() {
        companyRepository.save(new Company("Acme", "desc", UUID.randomUUID()));
        companyRepository.save(new Company("Beta", "desc", UUID.randomUUID()));

        List<CompanySummaryDTO> results = companyService.searchCompanies("be");

        assertEquals(List.of("Beta"), results.stream().map(CompanySummaryDTO::name).toList());
    }

    @Test
    void GivenMixedCaseQuery_WhenSearchingCompanies_ThenMatchIsCaseInsensitive() {
        companyRepository.save(new Company("Acme", "desc", UUID.randomUUID()));

        List<CompanySummaryDTO> results = companyService.searchCompanies("ACME");

        assertEquals(List.of("Acme"), results.stream().map(CompanySummaryDTO::name).toList());
    }

    @Test
    void GivenSuspendedCompany_WhenSearchingCompanies_ThenInactiveCompaniesAreExcluded() {
        companyRepository.save(new Company("Acme", "desc", UUID.randomUUID()));
        Company suspended = new Company("Beta", "desc", UUID.randomUUID());
        suspended.suspend();
        companyRepository.save(suspended);

        List<CompanySummaryDTO> results = companyService.searchCompanies("");

        assertEquals(List.of("Acme"), results.stream().map(CompanySummaryDTO::name).toList());
    }

    @Test
    void GivenNoMatch_WhenSearchingCompanies_ThenEmptyListReturned() {
        companyRepository.save(new Company("Acme", "desc", UUID.randomUUID()));

        assertTrue(companyService.searchCompanies("zzz").isEmpty());
    }
}
