package com.ticketing.domain.member;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.MemberExitResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;

class MemberValueObjectsTest {

    @Test
    void GivenContactInfo_WhenCreated_ThenGettersAgeEqualsAndToStringWork() {
        ContactInfo info = new ContactInfo(
                "tamar@example.com",
                "Tamar",
                "Iluz",
                "0500000000",
                LocalDate.of(2000, 1, 1));
        ContactInfo same = new ContactInfo(
                "tamar@example.com",
                "Tamar",
                "Iluz",
                "0500000000",
                LocalDate.of(2000, 1, 1));
        ContactInfo withoutBirthDate = new ContactInfo(
                "other@example.com", "Other", "User", null, null);

        assertAll(
                () -> assertEquals("tamar@example.com", info.getEmail()),
                () -> assertEquals("Tamar", info.getFirstName()),
                () -> assertEquals("Iluz", info.getLastName()),
                () -> assertEquals("0500000000", info.getPhoneNumber()),
                () -> assertEquals(LocalDate.of(2000, 1, 1), info.getDateOfBirth()),
                () -> assertEquals(26, info.getAgeAsOf(LocalDate.of(2026, 1, 2))),
                () -> assertEquals(-1, withoutBirthDate.getAgeAsOf(LocalDate.of(2026, 1, 2))),
                () -> assertEquals(info, same),
                () -> assertNotEquals(info, null),
                () -> assertNotEquals(info, "not contact info"),
                () -> assertTrue(info.toString().contains("Tamar Iluz"))
        );
    }

    @Test
    void GivenInvalidContactInfo_WhenCreated_ThenThrows() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo(null, "Tamar", "Iluz", "050", LocalDate.now())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo(" ", "Tamar", "Iluz", "050", LocalDate.now())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", null, "Iluz", "050", LocalDate.now())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", " ", "Iluz", "050", LocalDate.now())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", "Tamar", null, "050", LocalDate.now())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ContactInfo("t@example.com", "Tamar", " ", "050", LocalDate.now()))
        );
    }

    @Test
    void GivenRoleFactory_WhenCreatingRoles_ThenPermissionsMatchRoleType() {
        ProducerRole founder = RoleFactory.createFounder();
        ProducerRole owner = RoleFactory.createOwner();
        ProducerRole manager = RoleFactory.createManager(List.of(
                ManagerPermission.MAP_DEFINITION,
                ManagerPermission.VIEW_REPORTS));

        assertAll(
                () -> assertEquals("Founder", founder.getRoleName()),
                () -> assertTrue(founder.canAppoint()),
                () -> assertTrue(owner.canManageInventory()),
                () -> assertEquals("Manager", manager.getRoleName()),
                () -> assertFalse(manager.canAppoint()),
                () -> assertTrue(manager.canDefineMap()),
                () -> assertFalse(manager.canManageInventory()),
                () -> assertFalse(manager.canModifyPolicy()),
                () -> assertFalse(manager.canManagePersonnel()),
                () -> assertTrue(manager.canViewReports()),
                () -> assertFalse(manager.canHandleInquiries()),
                () -> assertFalse(manager.canHandleEventLifecycle())
        );
    }

    @Test
    void GivenProducer_WhenCreatedWithValidRoles_ThenDelegatesPermissionsToRole() {
        UUID founderMemberId = UUID.randomUUID();
        Producer founder = new Producer("Acme", founderMemberId, null, RoleFactory.createFounder());

        UUID managerMemberId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();
        Producer manager = new Producer("Acme", managerMemberId, appointerId,
                RoleFactory.createManager(List.of(ManagerPermission.POLICY_MODIFICATION)));

        manager.setAppointerId(founderMemberId);
        manager.setRole(RoleFactory.createManager(List.of(ManagerPermission.EVENT_LIFECYCLE)));

        assertAll(
                () -> assertEquals("Acme", founder.getCompanyName()),
                () -> assertEquals(founderMemberId, founder.getMemberId()),
                () -> assertNull(founder.getAppointerId()),
                () -> assertEquals("Founder", founder.getRoleName()),
                () -> assertTrue(founder.canManagePersonnel()),
                () -> assertEquals(founderMemberId, manager.getAppointerId()),
                () -> assertEquals("Manager", manager.getRoleName()),
                () -> assertFalse(manager.canModifyPolicy()),
                () -> assertTrue(manager.canHandleEventLifecycle())
        );
    }

    @Test
    void GivenInvalidProducerArguments_WhenCreated_ThenThrows() {
        UUID memberId = UUID.randomUUID();
        UUID appointerId = UUID.randomUUID();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer(null, memberId, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("", memberId, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", null, null, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", memberId, null, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", memberId, memberId, RoleFactory.createOwner())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", memberId, appointerId, RoleFactory.createFounder())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Producer("Acme", memberId, null, RoleFactory.createOwner()))
        );
    }

    @Test
    void GivenMemberMapper_WhenMappingMember_ThenDtoContainsPublicMemberFields() {
        UUID memberId = UUID.randomUUID();
        LocalDate dateOfBirth = LocalDate.of(2000, 1, 1);
        Member member = new Member(memberId, "tamar", "tamar@example.com", "hashed", "050", dateOfBirth);

        MemberDto dto = MemberMapper.toDto(member);

        assertAll(
                () -> assertEquals(memberId, dto.memberId()),
                () -> assertEquals("tamar", dto.username()),
                () -> assertEquals("tamar@example.com", dto.email()),
                () -> assertEquals("050", dto.phoneNumber()),
                () -> assertEquals(dateOfBirth, dto.dateOfBirth()),
                () -> assertThrows(IllegalArgumentException.class, () -> MemberMapper.toDto(null))
        );
    }

    @Test
    void GivenResponseFactories_WhenCalled_ThenResponsesContainExpectedPayloads() {
        MemberDto dto = new MemberDto(UUID.randomUUID(), "tamar", "tamar@example.com", "050", LocalDate.of(2000, 1, 1));

        LoginResponse loginSuccess = LoginResponse.success(dto, "member-token");
        LoginResponse loginFailure = LoginResponse.failure("bad login");
        RegisterResponse registerSuccess = RegisterResponse.success(dto, "register-token");
        RegisterResponse registerFailure = RegisterResponse.failure("bad register");
        LogoutResponse logoutSuccess = LogoutResponse.success("guest-token");
        LogoutResponse logoutFailure = LogoutResponse.failure("bad logout");
        MemberExitResponse exitSuccess = MemberExitResponse.successResponse("tamar");
        MemberExitResponse exitFailure = MemberExitResponse.failure("bad exit");
        UpdateMemberDetailsResponse updateSuccess = UpdateMemberDetailsResponse.success(dto);
        UpdateMemberDetailsResponse updateFailure = UpdateMemberDetailsResponse.failure("bad update");

        assertAll(
                () -> assertTrue(loginSuccess.success()),
                () -> assertEquals(dto, loginSuccess.member()),
                () -> assertEquals("member-token", loginSuccess.sessionToken()),
                () -> assertFalse(loginFailure.success()),
                () -> assertNull(loginFailure.member()),
                () -> assertNull(loginFailure.sessionToken()),
                () -> assertTrue(registerSuccess.success()),
                () -> assertEquals("register-token", registerSuccess.sessionToken()),
                () -> assertFalse(registerFailure.success()),
                () -> assertTrue(logoutSuccess.success()),
                () -> assertEquals("guest-token", logoutSuccess.sessionToken()),
                () -> assertFalse(logoutFailure.success()),
                () -> assertNull(logoutFailure.sessionToken()),
                () -> assertTrue(exitSuccess.success()),
                () -> assertTrue(exitSuccess.message().contains("tamar")),
                () -> assertFalse(exitFailure.success()),
                () -> assertTrue(updateSuccess.success()),
                () -> assertEquals(dto, updateSuccess.member()),
                () -> assertFalse(updateFailure.success()),
                () -> assertNull(updateFailure.member())
        );
    }
}
