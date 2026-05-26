package com.ticketing.application.services;
import java.util.List;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.CompanyHistoryDomainService;

@org.springframework.stereotype.Service
public class CompanyHistoryService {

    private final CompanyHistoryDomainService domainService;

    // Backward compatibility constructor
    public CompanyHistoryService(ICompanyRepository companyRepository,
                                 IMemberRepository memberRepository,
                                 IOrderRepository orderRepository,
                                 ISessionTokenService sessionTokenService) {
        this(new CompanyHistoryDomainService(companyRepository, memberRepository, orderRepository, sessionTokenService));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompanyHistoryService(CompanyHistoryDomainService domainService) {
        this.domainService = domainService;
    }

    public List<PurchaseRecordDTO> getPurchaseHistory(String token, String companyName) {
        return domainService.getPurchaseHistory(token, companyName);
    }
}

