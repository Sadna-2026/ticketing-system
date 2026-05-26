package com.ticketing.application.services;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.SalesReportDTO;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.CompletedPurchaseDomainService;

@org.springframework.stereotype.Service
public class CompletedPurchaseService {

    private final CompletedPurchaseDomainService domainService;

    // Backward compatibility constructor
    public CompletedPurchaseService(
            IOrderRepository orderRepository,
            ICompanyRepository companyRepository,
            IMemberRepository memberRepository,
            ISessionTokenService sessionTokenService
    ) {
        this(new CompletedPurchaseDomainService(orderRepository, companyRepository, memberRepository, sessionTokenService));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CompletedPurchaseService(CompletedPurchaseDomainService domainService) {
        this.domainService = domainService;
    }

    public SalesReportDTO getHierarchicalSalesReport(String token, String companyName) {
        return domainService.getHierarchicalSalesReport(token, companyName);
    }
}

