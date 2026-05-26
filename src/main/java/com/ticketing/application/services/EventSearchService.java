package com.ticketing.application.services;

import java.util.List;

import com.ticketing.application.SearchEventsRequest;
import com.ticketing.application.dto.EventSummaryDTO;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.services.EventSearchDomainService;

@org.springframework.stereotype.Service
public class EventSearchService {

    private final EventSearchDomainService domainService;

    // Backward compatibility constructor
    public EventSearchService(IEventRepository eventRepository, ICompanyRepository companyRepository) {
        this(new EventSearchDomainService(eventRepository, companyRepository));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EventSearchService(EventSearchDomainService domainService) {
        this.domainService = domainService;
    }

    public List<EventSummaryDTO> searchEvents(SearchEventsRequest req) {
        return domainService.searchEvents(req);
    }
}

