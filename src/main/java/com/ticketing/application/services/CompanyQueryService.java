package com.ticketing.application.services;

import java.util.Optional;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.services.CompanyQueryDomainService;

@org.springframework.stereotype.Service
public class CompanyQueryService {

    private final CompanyQueryDomainService domainService;

    // Backward compatibility constructor
    public CompanyQueryService(ICompanyRepository companyRepository, IEventRepository eventRepository) {
        this(new CompanyQueryDomainService(companyRepository, eventRepository));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyQueryService(CompanyQueryDomainService domainService) {
        this.domainService = domainService;
    }

    public Optional<CompanyPublicDTO> getCompanyInfo(String companyName) {
        return domainService.getCompanyInfo(companyName);
    }
}

