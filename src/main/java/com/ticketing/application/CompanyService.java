package com.ticketing.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.application.auth.ISessionTokenService;

import java.util.Collections;
import java.util.UUID;

public class CompanyService {
    private static final Logger log = LoggerFactory.getLogger(CompanyService.class);

    private final ICompanyRepository companyRepository;
    private final IMemberRepository memberRepository;
    //private final IDomainEventPublisher eventPublisher;
    private final ISessionTokenService sessionTokenService;

    public CompanyService(ICompanyRepository companyRepository, IMemberRepository memberRepository, ISessionTokenService sessionTokenService) {
        this.companyRepository = companyRepository;
        this.memberRepository = memberRepository;
        this.sessionTokenService = sessionTokenService;
    }

    /**
     * Creates a new production company. The creating member becomes the Founder
     * and initial Owner (via a Founder StaffAppointment in the Member aggregate).
     *
     * @param token token of the member creating the company
     * @param name the company name
     * @param description optional company description
     * @return the new company's name (unique identifier)
     */
    public String openProductionCompany(String token, String name, String description) {
        UUID founderId = validateToken(token);
        log.info("Creating company: founderId={}, name={}", founderId, name);
        
        if (founderId == null) {
            throw new IllegalArgumentException("A guest user cannot create a production company. Please log in.");
        }

        if (companyRepository.existsByName(name)) {
            throw new IllegalArgumentException("A production company with this name already exists.");
        }

        Member founder = memberRepository.findById(founderId)
            .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        Company company = new Company(name, description, founderId);
        companyRepository.save(company);

        // Assign Owner appointment to the member
        StaffAppointment ownerAppointment = new StaffAppointment(
            company.getName(),
            founderId,
            StaffAppointment.StaffRole.OWNER,
            Collections.emptySet()
        );
        founder.addStaffAppointment(company.getName(), ownerAppointment);
        memberRepository.save(founder);

        log.info("Company created: companyName={}, founderId={}", company.getName(), founderId);
        return company.getName();
    }

    private UUID validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Authentication token is required");
        }
        if (!sessionTokenService.isValid(token)) {
            throw new IllegalArgumentException("Invalid or expired authentication token");
        }
        return sessionTokenService.extractMemberId(token);
    }
}
