package com.ticketing.application.services;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.AdminDomainService;

@Service
public class AdminService {

    private final AdminDomainService domainService;

    // Backward compatibility
    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository
    ) {
        this(new AdminDomainService(memberRepository, companyRepository, sessionTokenService, adminRepository, orderRepository));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(AdminDomainService domainService) {
        this.domainService = domainService;
    }

    public synchronized void removeMember(String adminToken, UUID targetMemberId) {
        domainService.removeMember(adminToken, targetMemberId);
    }

    public List<PurchaseRecordDTO> getGlobalPurchaseHistory(String adminToken, UUID buyerId, String companyName) {
        return domainService.getGlobalPurchaseHistory(adminToken, buyerId, companyName);
    }
}
