package com.ticketing.application;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private static final String ADMIN_PERMISSION = "SYSTEM_ADMIN";

    private final IMemberRepository memberRepository;
    private final ICompanyRepository companyRepository;
    private final ISessionTokenService sessionTokenService;

    public AdminService(
            IMemberRepository memberRepository,
            ICompanyRepository companyRepository,
            ISessionTokenService sessionTokenService
    ) {
        if (memberRepository == null || companyRepository == null || sessionTokenService == null) {
            throw new IllegalArgumentException("Dependencies cannot be null");
        }
        this.memberRepository = memberRepository;
        this.companyRepository = companyRepository;
        this.sessionTokenService = sessionTokenService;
    }

    public synchronized void removeMember(String adminToken, UUID targetMemberId) {
        // 1. Validate Admin
        if (!isAdmin(adminToken)) {
            throw new SecurityException("System admin permission required");
        }

        // 2. Validate Target
        Member target = memberRepository.findById(targetMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Target member not found: " + targetMemberId));

        // 3. Sole Admin Check (Stubbed)
        if (isSoleAdmin(targetMemberId)) {
            throw new IllegalStateException("SoleAdminProtection: Cannot remove the last system admin");
        }

        // 4. Evaluate Company Integrity
        List<Company> allCompanies = companyRepository.getAll();
        for (Company company : allCompanies) {
            if (company.isActive() && target.hasStaffAppointment(company.getName(), StaffAppointment.StaffRole.OWNER)) {
                List<Member> companyMembers = memberRepository.findByCompanyAppointment(company.getName());
                long ownerCount = companyMembers.stream()
                        .filter(m -> m.hasStaffAppointment(company.getName(), StaffAppointment.StaffRole.OWNER))
                        .count();
                
                if (ownerCount <= 1) {
                    throw new IllegalStateException("CompanyIntegrityBlock: Cannot remove the only owner of active company: " + company.getName());
                }
            }
        }

        // 5. Revoke Roles and Delete target member
        memberRepository.delete(target);

        // 6. Terminate Sessions
        sessionTokenService.revokeMemberSessions(targetMemberId);
    }

    private boolean isAdmin(String token) {
        if (!sessionTokenService.isValid(token)) {
            return false;
        }
        Set<String> perms = sessionTokenService.extractPermissions(token);
        return perms != null && perms.contains(ADMIN_PERMISSION);
    }

    // TODO: implement sole admin check when Admin aggregate is in scope
    private boolean isSoleAdmin(UUID targetId) {
        return false;
    }
}
