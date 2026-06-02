package com.ticketing.domain.services;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.CompanySummaryDTO;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventRepository;

@org.springframework.stereotype.Service
public class CompanyQueryDomainService {

    private static final Logger log = LoggerFactory.getLogger(CompanyQueryDomainService.class);

    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;

    public CompanyQueryDomainService(ICompanyRepository companyRepository, IEventRepository eventRepository) {
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
    }

    public Optional<CompanyPublicDTO> getCompanyInfo(String companyName) {
        log.info("Company info requested: name={}", companyName);
        if (companyName == null || companyName.isBlank()) {
            return Optional.empty();
        }
        Optional<Company> maybe = companyRepository.findByName(companyName);
        if (maybe.isEmpty() || !maybe.get().isActive()) {
            log.info("Company info request denied: name={}, reason={}",
                    companyName, maybe.isEmpty() ? "unknown" : "not-active");
            return Optional.empty();
        }
        Company company = maybe.get();
        List<EventSummaryDTO> active = eventRepository.findByCompanyName(company.getName()).stream()
                .filter(CompanyQueryDomainService::isPubliclyVisible)
                .map(EventSummaryDTO::from)
                .toList();
        log.info("Company info provided: name={}", company.getName());
        return Optional.of(new CompanyPublicDTO(company.getName(), company.getDescription(), active));
    }

    /**
     * Lists active companies whose name contains the (case-insensitive) query, for use by
     * UI company pickers. A blank/null query returns all active companies.
     */
    public List<CompanySummaryDTO> searchCompanies(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return companyRepository.getAll().stream()
                .filter(Company::isActive)
                .filter(c -> needle.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(needle))
                .map(c -> new CompanySummaryDTO(c.getName()))
                .sorted(Comparator.comparing(CompanySummaryDTO::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static boolean isPubliclyVisible(Event e) {
        return e.getStatus() == EventStatus.PUBLISHED || e.getStatus() == EventStatus.SOLD_OUT;
    }
}
