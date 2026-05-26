package com.ticketing.application.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.CompletedPurchase;
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

    /**
     * UC-II.6.7 — System admin suspends a user.
     */
    public Suspension suspendUser(String adminToken, UUID targetMemberId,
                                   Duration duration, String reason) {
        return domainService.suspendUser(adminToken, targetMemberId, duration, reason);
    }

    /**
     * UC-II.6.8 — System admin cancels (lifts) an active suspension.
     */
    public void cancelSuspension(String adminToken, UUID targetMemberId,
                                  UUID suspensionId) {
        domainService.cancelSuspension(adminToken, targetMemberId, suspensionId);
    }

    /**
     * UC-II.6.9 — System admin views user suspensions.
     */
    public List<SuspensionDTO> listSuspensions(String adminToken, boolean activeOnly) {
        return domainService.listSuspensions(adminToken, activeOnly);
    }

    public List<PurchaseRecordDTO> getGlobalPurchaseHistory(String adminToken, UUID buyerId, String companyName) {
        return domainService.getGlobalPurchaseHistory(adminToken, buyerId, companyName);
    }
}
