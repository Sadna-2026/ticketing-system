package com.ticketing.application.services;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.dto.SuspensionDTO;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.member.request.*;
import com.ticketing.domain.member.response.*;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.services.AdminDomainService;

@Service
public class AdminService {

    private final AdminDomainService domainService;
    private final INotificationService notificationService;

    // Backward compatibility
    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService,
            IAdminRepository adminRepository,
            IOrderRepository orderRepository
    ) {
        this(new AdminDomainService(memberRepository, companyRepository, sessionTokenService, adminRepository, orderRepository), null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(
            AdminDomainService domainService,
            @org.springframework.beans.factory.annotation.Autowired(required = false) INotificationService notificationService) {
        this.domainService = domainService;
        this.notificationService = notificationService;
    }

    public synchronized void removeMember(String adminToken, UUID targetMemberId) {
        domainService.removeMember(adminToken, targetMemberId);
    }

    /**
     * UC-II.6.7 — System admin suspends a user.
     */
    public Suspension suspendUser(String adminToken, UUID targetMemberId,
                                   Duration duration, String reason) {
        Suspension suspension = domainService.suspendUser(adminToken, targetMemberId, duration, reason);
        if (notificationService != null) {
            notificationService.notify(targetMemberId.toString(), "Your account has been suspended" + (reason != null ? ": " + reason : "."));
        }
        return suspension;
    }

    /**
     * UC-II.6.8 — System admin cancels (lifts) an active suspension.
     */
    public void cancelSuspension(String adminToken, UUID targetMemberId,
                                  UUID suspensionId) {
        domainService.cancelSuspension(adminToken, targetMemberId, suspensionId);
        if (notificationService != null) {
            notificationService.notify(targetMemberId.toString(), "Your account suspension has been lifted.");
        }
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

    public LoginResponse adminLogin(LoginRequest request, String guestToken) {
        return domainService.adminLogin(request, guestToken);
    }

    public boolean registerAdmin(UUID adminId, String username, String email, String password) {
        return domainService.registerAdmin(adminId, username, email, password);
    }
}
