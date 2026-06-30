package com.ticketing.presentation.vaadin.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.IPurchasePolicy;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.presentation.vaadin.presenters.CompanyPresenter;
import com.ticketing.presentation.vaadin.testsupport.VaadinSessionExtension;
import com.ticketing.presentation.vaadin.util.SessionContext;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.IntegerField;

@DisplayName("CompanyView complex purchase policy editor")
@ExtendWith(VaadinSessionExtension.class)
class CompanyViewComplexPurchasePolicyTest {

    @Test
    void GivenTwoRuleGroups_WhenBuildPurchasePolicy_ThenNestedCompositePolicyIsCreated() throws Exception {
        CompanyView view = newView();

        checkbox(view, "purchaseRuleParts").setValue(Set.of("Age restriction"));
        integer(view, "policyAge").setValue(18);
        checkbox(view, "secondaryPurchaseRuleParts").setValue(Set.of("Age restriction", "Min quantity"));
        integer(view, "secondaryPolicyAge").setValue(40);
        integer(view, "secondaryPolicyMinTickets").setValue(10);
        combo(view, "secondaryPolicyComposition").setValue("AND (all must pass)");
        combo(view, "policyGroupComposition").setValue("OR between groups");

        IPurchasePolicy policy = invokeBuild(view);

        OrPolicy root = assertInstanceOf(OrPolicy.class, policy);
        assertEquals(2, root.getPolicies().size());
        AgeRestrictionPolicy left = assertInstanceOf(AgeRestrictionPolicy.class, root.getPolicies().get(0));
        assertEquals(18, left.getMinimumAge());

        AndPolicy right = assertInstanceOf(AndPolicy.class, root.getPolicies().get(1));
        assertEquals(2, right.getPolicies().size());
        AgeRestrictionPolicy olderBuyer = assertInstanceOf(AgeRestrictionPolicy.class, right.getPolicies().get(0));
        MinQuantityPolicy minTickets = assertInstanceOf(MinQuantityPolicy.class, right.getPolicies().get(1));
        assertEquals(40, olderBuyer.getMinimumAge());
        assertEquals(10, minTickets.getMinTickets());
    }

    @Test
    void GivenNestedCompositePolicy_WhenAppliedToForm_ThenBothGroupsCanBeEdited() throws Exception {
        CompanyView view = newView();
        IPurchasePolicy policy = new OrPolicy(List.of(
                new AgeRestrictionPolicy(18),
                new AndPolicy(List.of(new AgeRestrictionPolicy(40), new MinQuantityPolicy(10)))));

        invokeApply(view, policy);

        assertTrue(checkbox(view, "purchaseRuleParts").getValue().contains("Age restriction"));
        assertEquals(18, integer(view, "policyAge").getValue());
        assertEquals("OR between groups", combo(view, "policyGroupComposition").getValue());
        assertEquals("AND (all must pass)", combo(view, "secondaryPolicyComposition").getValue());
        assertTrue(checkbox(view, "secondaryPurchaseRuleParts").getValue().contains("Age restriction"));
        assertTrue(checkbox(view, "secondaryPurchaseRuleParts").getValue().contains("Min quantity"));
        assertEquals(40, integer(view, "secondaryPolicyAge").getValue());
        assertEquals(10, integer(view, "secondaryPolicyMinTickets").getValue());
    }

    @Test
    void GivenSelectedRules_WhenFormChanges_ThenOnlyRelevantInputsAreVisible() throws Exception {
        CompanyView view = newView();

        assertTrue(!integer(view, "policyAge").isVisible());
        assertTrue(!combo(view, "policyComposition").isVisible());
        assertTrue(!combo(view, "policyGroupComposition").isVisible());

        checkbox(view, "purchaseRuleParts").setValue(Set.of("Age restriction"));

        assertTrue(integer(view, "policyAge").isVisible());
        assertTrue(!integer(view, "policyMaxTickets").isVisible());
        assertTrue(!integer(view, "policyMinTickets").isVisible());
        assertTrue(!combo(view, "policyComposition").isVisible());

        checkbox(view, "purchaseRuleParts").setValue(Set.of("Age restriction", "Min quantity"));
        checkbox(view, "secondaryPurchaseRuleParts").setValue(Set.of("Max quantity"));

        assertTrue(integer(view, "policyMinTickets").isVisible());
        assertTrue(combo(view, "policyComposition").isVisible());
        assertTrue(integer(view, "secondaryPolicyMaxTickets").isVisible());
        assertTrue(!integer(view, "secondaryPolicyAge").isVisible());
        assertTrue(!combo(view, "secondaryPolicyComposition").isVisible());
        assertTrue(combo(view, "policyGroupComposition").isVisible());
    }

    private static CompanyView newView() {
        CompanyPresenter presenter = mock(CompanyPresenter.class);
        when(presenter.currentSessionState())
                .thenReturn(new SessionContext.UiState(true, false, true, false, "owner", "MEMBER"));
        return new CompanyView(presenter);
    }

    private static IPurchasePolicy invokeBuild(CompanyView view) throws Exception {
        Method method = CompanyView.class.getDeclaredMethod("buildPurchasePolicyInternal");
        method.setAccessible(true);
        return (IPurchasePolicy) method.invoke(view);
    }

    private static void invokeApply(CompanyView view, IPurchasePolicy policy) throws Exception {
        Method method = CompanyView.class.getDeclaredMethod("applyPurchasePolicyToForm", IPurchasePolicy.class);
        method.setAccessible(true);
        method.invoke(view, policy);
    }

    @SuppressWarnings("unchecked")
    private static CheckboxGroup<String> checkbox(CompanyView view, String name) throws Exception {
        return (CheckboxGroup<String>) field(view, name);
    }

    @SuppressWarnings("unchecked")
    private static ComboBox<String> combo(CompanyView view, String name) throws Exception {
        return (ComboBox<String>) field(view, name);
    }

    private static IntegerField integer(CompanyView view, String name) throws Exception {
        return (IntegerField) field(view, name);
    }

    private static Object field(CompanyView view, String name) throws Exception {
        Field field = CompanyView.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(view);
    }
}
