package com.ticketing.presentation.rest;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.services.CompanyService;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.StaffAppointment;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<String>> openCompany(@RequestHeader("X-Session-Token") String token, @RequestParam String name, @RequestParam String description) {
        String companyName = companyService.openProductionCompany(token, name, description);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(companyName));
    }

    @GetMapping("/{companyName}")
    public ResponseEntity<ApiResponse<CompanyPublicDTO>> getCompanyInfo(@PathVariable String companyName) {
        Optional<CompanyPublicDTO> dto = companyService.getCompanyInfo(companyName);
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
        companyService.suspendCompany(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{companyName}/lifecycle/reopen")
    public ResponseEntity<ApiResponse<Void>> reopenCompany(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName) {
        companyService.reopenCompany(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{companyName}")
    public ResponseEntity<ApiResponse<Void>> closeCompany(@RequestHeader("X-Session-Token") String token, @PathVariable String companyName) {
        companyService.permanentCloseByFounder(token, companyName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
