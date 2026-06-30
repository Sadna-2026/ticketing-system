package com.ticketing.domain.event;

import java.util.ArrayList;
import java.util.List;

/**
 * User-facing wording for purchase-policy trees.
 */
public final class PurchasePolicyText {

    private PurchasePolicyText() {
    }

    public static String describeRequirement(IPurchasePolicy policy) {
        if (policy == null || policy instanceof AlwaysAllowPolicy) {
            return "no purchase restrictions";
        }
        if (policy instanceof AgeRestrictionPolicy age) {
            return "minimum age " + age.getMinimumAge();
        }
        if (policy instanceof MaxQuantityPolicy max) {
            return "at most " + max.getMaxTickets() + " tickets";
        }
        if (policy instanceof MinQuantityPolicy min) {
            return "at least " + min.getMinTickets() + " tickets";
        }
        if (policy instanceof NoOrphanSeatPolicy) {
            return "no isolated single seats";
        }
        if (policy instanceof AndPolicy and) {
            return joinComposite(and.getPolicies(), "AND");
        }
        if (policy instanceof OrPolicy or) {
            return joinComposite(or.getPolicies(), "OR");
        }
        return policy.getClass().getSimpleName();
    }

    public static String describeOrFailure(List<IPurchasePolicy> branches, PurchaseContext context) {
        if (branches == null || branches.isEmpty()) {
            return "No policies in the OR condition passed.";
        }

        List<String> branchMessages = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) {
            IPurchasePolicy branch = branches.get(i);
            String branchPolicy = describeRequirement(branch);
            List<String> reasons = reasonsForFailedBranch(branch, context);
            String suffix = reasons.isEmpty() ? "" : " failed because " + String.join(" AND ", reasons);
            branchMessages.add("Option " + (i + 1) + " (" + branchPolicy + ")" + suffix);
        }
        return "Purchase policy requires one OR option to pass: " + String.join("; OR ", branchMessages);
    }

    private static List<String> reasonsForFailedBranch(IPurchasePolicy policy, PurchaseContext context) {
        if (policy instanceof AndPolicy and) {
            List<String> reasons = new ArrayList<>();
            for (IPurchasePolicy child : and.getPolicies()) {
                if (!child.isAllowed(context).allowed()) {
                    reasons.addAll(reasonsForFailedBranch(child, context));
                }
            }
            return reasons;
        }
        if (policy instanceof OrPolicy or) {
            if (or.isAllowed(context).allowed()) {
                return List.of();
            }
            return List.of(describeOrFailure(or.getPolicies(), context));
        }

        PolicyResult result = policy.isAllowed(context);
        if (result.allowed()) {
            return List.of();
        }
        String reason = result.reason();
        return reason == null || reason.isBlank() ? List.of(describeRequirement(policy)) : List.of(reason);
    }

    private static String joinComposite(List<IPurchasePolicy> policies, String operator) {
        List<String> parts = new ArrayList<>();
        for (IPurchasePolicy child : policies) {
            if (child == null || child instanceof AlwaysAllowPolicy) {
                continue;
            }
            parts.add(describeRequirement(child));
        }
        if (parts.isEmpty()) {
            return "no purchase restrictions";
        }
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        return "(" + String.join(" " + operator + " ", parts) + ")";
    }
}
