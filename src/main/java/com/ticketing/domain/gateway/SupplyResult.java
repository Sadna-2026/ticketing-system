package com.ticketing.domain.gateway;

import java.util.List;

public record SupplyResult(boolean success, List<String> issuedTicketCodes, String errorMessage) {
    public static SupplyResult successful(List<String> codes) {
        return new SupplyResult(true, codes, null);
    }
    public static SupplyResult failed(String errorMessage) {
        return new SupplyResult(false, List.of(), errorMessage);
    }
}
