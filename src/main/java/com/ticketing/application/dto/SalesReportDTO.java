package com.ticketing.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Sales report for a company's hierarchical subtree.
 * Shows all completed purchases made by the requester and their subordinates.
 */
public record SalesReportDTO(
        String companyName,
        UUID requestedByMemberId,
        List<PurchaseRecordDTO> purchases,
        BigDecimal totalRevenue,
        int totalPurchases
) {
}
