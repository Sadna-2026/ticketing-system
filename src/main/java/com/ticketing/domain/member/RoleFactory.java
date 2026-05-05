package com.ticketing.domain.member;

import java.util.List;

public class RoleFactory {
    public static ProducerRole createFounder() {
        return new Founder();
    }

    public static ProducerRole createOwner() {
        return new Owner();
    }

    public static ProducerRole createManager(List<ManagerPermission> permissions) {
        return new Manager(permissions);
    }
}
