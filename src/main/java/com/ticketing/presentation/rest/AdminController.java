package com.ticketing.presentation.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.services.AdminService;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @DeleteMapping("/members/{targetMemberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@RequestHeader("X-Session-Token") String adminToken, @PathVariable UUID targetMemberId) {
        adminService.removeMember(adminToken, targetMemberId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/purchases")
    public ResponseEntity<ApiResponse<List<PurchaseRecordDTO>>> getGlobalPurchaseHistory(
            @RequestHeader("X-Session-Token") String adminToken,
            @RequestParam(required = false) UUID buyerId,
            @RequestParam(required = false) String companyName) {
        List<PurchaseRecordDTO> history = adminService.getGlobalPurchaseHistory(adminToken, buyerId, companyName);
        return ResponseEntity.ok(ApiResponse.success(history));
    }
}