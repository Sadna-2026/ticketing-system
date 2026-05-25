package com.ticketing.presentation.rest;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.CompanyQueryService;
import com.ticketing.application.services.CompanyLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.Collections;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;
    private final CompanyQueryService companyQueryService;
    private final CompanyLifecycleService companyLifecycleService;

    public CompanyController(CompanyService companyService, CompanyQueryService companyQueryService, CompanyLifecycleService companyLifecycleService) {
        this.companyService = companyService;
        this.companyQueryService = companyQueryService;
        this.companyLifecycleService = companyLifecycleService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> openCompany(@RequestHeader("X-Session-Token") String token, @RequestParam String name, @RequestParam String description) {
        String companyName = companyService.openProductionCompany(token, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(companyName));
    }

    @GetMapping("/{companyName}")
    public ResponseEntity<ApiResponse<CompanyPublicDTO>> getCompanyInfo(@PathVariable String companyName) {
        Optional<CompanyPublicDTO> dto = companyQueryService.getCompanyInfo(companyName);
        return dto.map(val -> ResponseEntity.ok(ApiResponse.success(val))).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Company not found")));
    }

    @PostMapping("/{companyName}/roles/offer")
    public ResponseEntity<ApiResponse<Void>> offerRoleAppointment(
            @RequestHeader("X-Session-Token") String token, 
            @PathVariable String companyName, 
            @RequestParam UUID appointeeId, 
            @RequestParam String roleType,
            @RequestParam(required = false) Set<ManagerPermission> permissions) {
        Set<ManagerPermission> perms = permissions != null ? permissions : Collections.emptySet();
        companyService.offerRoleAppointment(token, companyName, appointeeId, StaffAppointment.StaffRole.valueOf(roleType), perms);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @PatchMapping("/{companyName}/roles/respond")
    public ResponseEntity<ApiResponse<Void>> respondToRoleAppointment(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName, @RequestParam UUID offerId, @RequestParam boolean accept) {
        companyService.respondToRoleAppointment(token, offerId, accept);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{companyName}/personnel/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> revokePersonnel(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName, @PathVariable UUID targetMemberId) {
        companyService.revokePersonnel(token, companyName, targetMemberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{companyName}/lifecycle/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendCompany(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName) {
        companyLifecycleService.suspendCompany(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{companyName}/lifecycle/reopen")
    public ResponseEntity<ApiResponse<Void>> reopenCompany(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName) {
        companyLifecycleService.reopenCompany(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{companyName}")
    public ResponseEntity<ApiResponse<Void>> closeCompany(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName) {
        companyLifecycleService.permanentCloseByFounder(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}