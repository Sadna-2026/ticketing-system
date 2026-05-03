package com.ticketing.application;

import java.util.UUID;

public final class CompanyDTO {
    private final String name;
    private final String description;
    private final UUID founderId;
    private final String status;
    //private final String jsonDiscountPolicy;
    //private final String jsonPurchasePolicy;

    public CompanyDTO(String name, String description, UUID founderId, String status, String jsonDiscountPolicy, String jsonPurchasePolicy) {
        this.name = name;
        this.description = description;
        this.founderId = founderId;
        this.status = status;
        //this.jsonDiscountPolicy = jsonDiscountPolicy;
        //this.jsonPurchasePolicy = jsonPurchasePolicy;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getFounderId() { return founderId; }
    public String getStatus() { return status; }
    //public String getJsonDiscountPolicy() { return jsonDiscountPolicy; }
    //public String getJsonPurchasePolicy() { return jsonPurchasePolicy; }
}
