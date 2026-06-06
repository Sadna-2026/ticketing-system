package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.dto.CompanyPublicDTO;
import com.ticketing.application.dto.PurchaseRecordDTO;
import com.ticketing.application.initialization.InitializationService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.INotificationService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.CompanyStatus;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.EventStatus;
import com.ticketing.domain.event.IEventPublisher;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.gateway.IPaymentGateway;
import com.ticketing.domain.gateway.RefundResult;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.communication.ManagerPermissionsChangedEvent;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.services.CompanyHistoryDomainService;
import com.ticketing.domain.services.CompanyLifecycleDomainService;
import com.ticketing.domain.services.CompanyQueryDomainService;
import com.ticketing.infrastructure.InMemoryCompanyRepository;
import com.ticketing.infrastructure.InMemoryEventPublisher;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;

@DisplayName("CompanyService")
class CompanyServiceTest {

    @Nested
    @DisplayName("CompanyService")
    class Core {

        private ICompanyRepository companyRepository;
        private IMemberRepository memberRepository;
        private InMemoryEventPublisher eventPublisher;
        private ISessionTokenService sessionTokenServiceMock;
        private INotificationService notificationService;
        private InitializationService initializationService;
        private CompanyService companyService;

        @BeforeEach
        public void setUp() {
            companyRepository = new InMemoryCompanyRepository();
            memberRepository = new InMemoryMemberRepository();
            eventPublisher = new InMemoryEventPublisher();
            sessionTokenServiceMock = mock(ISessionTokenService.class);
            notificationService = mock(INotificationService.class);

            initializationService = new InitializationService(
                companyRepository,
                memberRepository,
                eventPublisher,
                sessionTokenServiceMock,
                notificationService
            );

            companyService = initializationService.initialize();
        }

        @Test
        public void GivenValidToken_WhenOpenProductionCompany_ThenCompanyCreated() {
            UUID memberId = UUID.randomUUID();
            String token = "valid-token";
            String companyName = "NewCompany";

            when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

            memberRepository.saveIfUsernameAndEmailAvailable(new Member(memberId, "founder", "founder@test.com", "pass"));

            String result = companyService.openProductionCompany(token, companyName, "Description");

            assertEquals(companyName, result);
            assertTrue(companyRepository.existsByName(companyName));
        }

        @Test
        public void GivenDuplicateCompanyName_WhenOpenProductionCompany_ThenThrowsIllegalArgumentException() {
            UUID memberId = UUID.randomUUID();
            String token = "valid-token";
            String companyName = "ExistingCompany";

            when(sessionTokenServiceMock.isValid(token)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(token)).thenReturn(memberId);

            memberRepository.saveIfUsernameAndEmailAvailable(new Member(memberId, "founder", "founder@test.com", "pass"));

            companyService.openProductionCompany(token, companyName, "First");

            assertThrows(IllegalArgumentException.class, () -> {
                companyService.openProductionCompany(token, companyName, "Second");
            });
        }

        @Test
        public void GivenAcceptedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String appointerToken = "valid-" + appointerId.toString();
            String targetToken = "valid-" + targetId.toString();

            when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
            when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(appointer);
            companyService.openProductionCompany(appointerToken, companyName, "Desc");

            Member target = new Member(targetId, "target", "target@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(target);

            companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.singleton(ManagerPermission.PERSONNEL_MGMT));

            PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
            assertFalse(offer.isExpired());
            companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

            assertNotNull(memberRepository.findById(targetId).get().getStaffAppointment(companyName));
            verify(notificationService).notify(eq(appointerId.toString()), contains("accepted the manager role offer"));
        }

        @Test
        public void GivenRejectedResponse_WhenRespondToRoleAppointment_ThenNotifiesAppointeeWithoutAppointment() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String appointerToken = "valid-" + appointerId.toString();
            String targetToken = "valid-" + targetId.toString();

            when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
            when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(appointer);
            companyService.openProductionCompany(appointerToken, companyName, "Desc");

            Member target = new Member(targetId, "target", "target@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(target);

            companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.MANAGER, Collections.singleton(ManagerPermission.PERSONNEL_MGMT));

            PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
            assertFalse(offer.isExpired());
            companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

            assertNull(memberRepository.findById(targetId).get().getStaffAppointment(companyName));
            verify(notificationService).notify(eq(appointerId.toString()), contains("declined the manager role offer"));
        }

        @Test
        public void GivenManagerPromotionAccepted_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String appointerToken = "valid-" + appointerId.toString();
            String targetToken = "valid-" + targetId.toString();

            when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
            when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(appointer);
            companyService.openProductionCompany(appointerToken, companyName, "Desc");

            Member target = new Member(targetId, "target", "target@test.com", "pass");
            StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            target.addStaffAppointment(companyName, managerAppointment);
            memberRepository.saveIfUsernameAndEmailAvailable(target);

            companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

            PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
            assertFalse(offer.isExpired());
            companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), true);

            assertEquals(StaffAppointment.StaffRole.OWNER, memberRepository.findById(targetId).get().getStaffAppointment(companyName).getRole());
            verify(notificationService).notify(eq(appointerId.toString()), contains("promoted to owner"));
        }

        @Test
        public void GivenManagerPromotionRejected_WhenRespondToRoleAppointment_ThenNotifiesAppointee() {
            UUID appointerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String appointerToken = "valid-" + appointerId.toString();
            String targetToken = "valid-" + targetId.toString();

            when(sessionTokenServiceMock.isValid(appointerToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(appointerToken)).thenReturn(appointerId);
            when(sessionTokenServiceMock.isValid(targetToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(targetToken)).thenReturn(targetId);

            Member appointer = new Member(appointerId, "appointer", "appointer@test.com", "pass");
            memberRepository.saveIfUsernameAndEmailAvailable(appointer);
            companyService.openProductionCompany(appointerToken, companyName, "Desc");

            Member target = new Member(targetId, "target", "target@test.com", "pass");
            StaffAppointment managerAppointment = new StaffAppointment(companyName, appointerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            target.addStaffAppointment(companyName, managerAppointment);
            memberRepository.saveIfUsernameAndEmailAvailable(target);

            companyService.offerRoleAppointment(appointerToken, companyName, targetId, StaffAppointment.StaffRole.OWNER, Collections.emptySet());

            PendingRoleOffer offer = memberRepository.findById(targetId).get().getPendingOffers().get(0);
            assertFalse(offer.isExpired());
            companyService.respondToRoleAppointment(targetToken, offer.getOfferId(), false);

            assertEquals(StaffAppointment.StaffRole.MANAGER, memberRepository.findById(targetId).get().getStaffAppointment(companyName).getRole());
            verify(notificationService).notify(eq(appointerId.toString()), contains("rejected the promotion to owner"));
        }

        /**
         * Fulfills Acceptance Test: "Valid Revocation Request Initiated" (happy path)
         * Tests the core success path of the service layer before delegating to the Handler.
         */
        @Test
        public void GivenValidRequest_WhenRevokePersonnel_ThenPublishesEventSuccessfully() {
            UUID revokerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String validToken = "valid-" + revokerId.toString();

            // Create and save the Members
            Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");
            Member target = new Member(targetId, "target", "target@test.com", "pass");
            memberRepository.save(revoker);
            memberRepository.save(target);

            // Mock token validation for a logged-in user
            when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(revokerId);

            // Setup an ACTIVE company and save it to the repository
            Company activeCompany = new Company(companyName, "desc", revokerId);
            companyRepository.save(activeCompany);

            // Act & Assert: The method should complete without throwing any exceptions
            assertDoesNotThrow(() -> {
                companyService.revokePersonnel(validToken, companyName, targetId);
            });
        }

        /**
         * Fulfills Acceptance Test: "Inactive company blocks role changes"
         * V0 Requirement: Cannot revoke personnel from a suspended or closed company.
         */
        @Test
        public void GivenInactiveCompany_WhenRevokePersonnel_ThenThrowsIllegalArgumentException() {
            UUID revokerId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            String companyName = "TestCo";
            String validToken = "valid-" + revokerId.toString();

            // Create and save the Members to the real in-memory repository
            Member revoker = new Member(revokerId, "revoker", "revoker@test.com", "pass");
            Member target = new Member(targetId, "target", "target@test.com", "pass");
            memberRepository.save(revoker);
            memberRepository.save(target);

            // Mock token validation
            when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(revokerId);

            // Setup an INACTIVE company and save it to the real in-memory repository
            Company inactiveCompany = new Company(companyName, "desc", UUID.randomUUID());
            inactiveCompany.suspend(); // Makes isActive() return false
            companyRepository.save(inactiveCompany);

            // Act & Assert: Service should block the request
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                companyService.revokePersonnel(validToken, companyName, targetId);
            });

            assertEquals("Cannot revoke personnel from a suspended or closed company", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Valid Relinquishment Request Initiated" (happy path)
         * Tests the core success path of the service layer before delegating to the Handler.
         */
        @Test
        public void GivenValidRequest_WhenRelinquishOwnership_ThenPublishesEventSuccessfully() {
            UUID ownerId = UUID.randomUUID();
            String companyName = "TestCo";
            String validToken = "valid-" + ownerId.toString();

            Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
            memberRepository.save(owner);

            when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(ownerId);

            Company activeCompany = new Company(companyName, "desc", UUID.randomUUID());
            companyRepository.save(activeCompany);

            assertDoesNotThrow(() -> {
                companyService.relinquishOwnership(validToken, companyName);
            });
        }

        /**
         * Fulfills Acceptance Test: "Inactive company blocks role changes"
         * V0 Requirement: Cannot relinquish ownership from a suspended or closed company.
         */
        @Test
        public void GivenInactiveCompany_WhenRelinquishOwnership_ThenThrowsIllegalArgumentException() {
            UUID ownerId = UUID.randomUUID();
            String companyName = "TestCo";
            String validToken = "valid-" + ownerId.toString();

            Member owner = new Member(ownerId, "owner", "owner@test.com", "pass");
            memberRepository.save(owner);

            when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(ownerId);

            Company inactiveCompany = new Company(companyName, "desc", UUID.randomUUID());
            inactiveCompany.suspend();
            companyRepository.save(inactiveCompany);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                companyService.relinquishOwnership(validToken, companyName);
            });

            assertEquals("Cannot relinquish ownership from a suspended or closed company", exception.getMessage());
        }

        /**
         * Fulfills Acceptance Test: "Company not found"
         */
        @Test
        public void GivenNonExistentCompany_WhenRelinquishOwnership_ThenThrowsIllegalArgumentException() {
            UUID ownerId = UUID.randomUUID();
            String companyName = "NonExistentCo";
            String validToken = "valid-" + ownerId.toString();

            when(sessionTokenServiceMock.isValid(validToken)).thenReturn(true);
            when(sessionTokenServiceMock.extractMemberId(validToken)).thenReturn(ownerId);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                companyService.relinquishOwnership(validToken, companyName);
            });

            assertEquals("Company not found", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("Permissions")
    class Permissions {

        private ICompanyRepository companyRepository;
        private IEventPublisher eventPublisher;
        private ISessionTokenService sessionTokenService;
        private IMemberRepository memberRepository;
        private CompanyService companyService;

        private final String COMPANY_NAME = "TestCompany";
        private final String VALID_TOKEN = "valid-token";
        private final UUID CALLER_ID = UUID.randomUUID();
        private final UUID TARGET_ID = UUID.randomUUID();

        @BeforeEach
        public void setUp() {
            companyRepository = mock(ICompanyRepository.class);
            eventPublisher = mock(IEventPublisher.class);
            sessionTokenService = mock(ISessionTokenService.class);
            memberRepository = mock(IMemberRepository.class);
            companyService = new CompanyService(companyRepository, eventPublisher, sessionTokenService, memberRepository);
        }

        @Test
        public void GivenValidTokenAndCompany_WhenChangeManagerPermissions_ThenEventPublished() {
            when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);
            when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(true);

            Set<ManagerPermission> newPerms = Set.of(ManagerPermission.VIEW_REPORTS);

            companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, newPerms);

            verify(eventPublisher).publish(any(ManagerPermissionsChangedEvent.class));
        }

        @Test
        public void GivenInvalidToken_WhenChangeManagerPermissions_ThenThrowsIllegalArgumentException() {
            when(sessionTokenService.isValid("invalid")).thenReturn(false);

            assertThrows(IllegalArgumentException.class, () -> 
                companyService.changeManagerPermissions("invalid", COMPANY_NAME, TARGET_ID, Collections.emptySet())
            );

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        public void GivenUnknownCompany_WhenChangeManagerPermissions_ThenThrowsIllegalArgumentException() {
            when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);
            when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(false);

            assertThrows(IllegalArgumentException.class, () -> 
                companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, Collections.emptySet())
            );

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        public void GivenGuestCaller_WhenChangeManagerPermissions_ThenThrowsIllegalArgumentException() {
            when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(null); // Guest

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
                companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, TARGET_ID, Collections.emptySet())
            );
            assertTrue(ex.getMessage().contains("guest"));

            verify(eventPublisher, never()).publish(any());
        }

        @Test
        public void GivenNullTarget_WhenChangeManagerPermissions_ThenThrowsIllegalArgumentException() {
            when(sessionTokenService.isValid(VALID_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(VALID_TOKEN)).thenReturn(CALLER_ID);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
                companyService.changeManagerPermissions(VALID_TOKEN, COMPANY_NAME, null, Collections.emptySet())
            );
            assertTrue(ex.getMessage().contains("Target member ID is required"));

            verify(eventPublisher, never()).publish(any());
        }
    }

    //

    @Nested
    @DisplayName("History")
    class History {
        private static final String COMPANY = "Acme Productions";
        private static final String OTHER_COMPANY = "Other Co";
        private static final String OWNER_TOKEN = "owner-token";
        private static final String OUTSIDER_TOKEN = "outsider-token";

        private InMemoryCompanyRepository companyRepo;
        private InMemoryMemberRepository memberRepo;
        private InMemoryOrderRepository orderRepo;
        private ISessionTokenService tokens;
        private CompanyService service;

        private UUID ownerId;
        private Member owner;

        @BeforeEach
        public void setUp() {
            companyRepo = new InMemoryCompanyRepository();
            memberRepo = new InMemoryMemberRepository();
            orderRepo = new InMemoryOrderRepository();
            tokens = mock(ISessionTokenService.class);
            CompanyHistoryDomainService companyHistoryDomainService = new CompanyHistoryDomainService(companyRepo, memberRepo, orderRepo, tokens);
            service = new CompanyService(companyRepo, null, tokens, memberRepo, companyHistoryDomainService, null, null);

            ownerId = UUID.randomUUID();
            owner = new Member(ownerId, "owner", "owner@x.com", "pw");
            owner.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, ownerId, StaffAppointment.StaffRole.OWNER, Set.of()));
            memberRepo.save(owner);

            companyRepo.save(new Company(COMPANY, "desc", ownerId));

            when(tokens.isValid(OWNER_TOKEN)).thenReturn(true);
            when(tokens.extractMemberId(OWNER_TOKEN)).thenReturn(ownerId);
            when(tokens.isValid(OUTSIDER_TOKEN)).thenReturn(true);
            when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(UUID.randomUUID());
        }

        @Test
        public void GivenOwner_WhenGetPurchaseHistory_ThenReturnsAllCompanyPurchases() {
            orderRepo.save(purchase("Concert A", new BigDecimal("50.00")));
            orderRepo.save(purchase("Concert B", new BigDecimal("75.00")));

            List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

            assertEquals(2, history.size());
        }

        @Test
        public void GivenManagerWithViewReports_WhenGetPurchaseHistory_ThenReturnsHistory() {
            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "mgr", "m@x.com", "pw");
            manager.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.VIEW_REPORTS)));
            memberRepo.save(manager);
            when(tokens.isValid("mgr-token")).thenReturn(true);
            when(tokens.extractMemberId("mgr-token")).thenReturn(managerId);
            orderRepo.save(purchase("Concert", new BigDecimal("20.00")));

            List<PurchaseRecordDTO> history = service.getPurchaseHistory("mgr-token", COMPANY);

            assertEquals(1, history.size());
        }

        @Test
        public void GivenManagerWithoutViewReports_WhenGetPurchaseHistory_ThenThrowSecurityException() {
            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "mgr", "m@x.com", "pw");
            manager.addStaffAppointment(COMPANY,
                    new StaffAppointment(COMPANY, managerId, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.INVENTORY_MGMT)));
            memberRepo.save(manager);
            when(tokens.isValid("mgr-token")).thenReturn(true);
            when(tokens.extractMemberId("mgr-token")).thenReturn(managerId);

            assertThrows(SecurityException.class,
                    () -> service.getPurchaseHistory("mgr-token", COMPANY));
        }

        @Test
        public void GivenOutsider_WhenGetPurchaseHistory_ThenThrowSecurityException() {
            // outsider has a member account but no appointment for this company
            UUID outsiderId = UUID.randomUUID();
            memberRepo.save(new Member(outsiderId, "outsider", "o@x.com", "pw"));
            when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(outsiderId);

            assertThrows(SecurityException.class,
                    () -> service.getPurchaseHistory(OUTSIDER_TOKEN, COMPANY));
        }

        @Test
        public void GivenGuestToken_WhenGetPurchaseHistory_ThenThrowSecurityException() {
            when(tokens.extractMemberId(OWNER_TOKEN)).thenReturn(null);

            assertThrows(SecurityException.class,
                    () -> service.getPurchaseHistory(OWNER_TOKEN, COMPANY));
        }

        @Test
        public void GivenUnknownCompany_WhenGetPurchaseHistory_ThenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.getPurchaseHistory(OWNER_TOKEN, "Nonexistent Co"));
        }

        @Test
        public void GivenCompanyWithNoPurchases_WhenGetPurchaseHistory_ThenReturnsEmptyList() {
            List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

            assertTrue(history.isEmpty());
        }

        @Test
        public void GivenPurchasesAcrossCompanies_WhenGetPurchaseHistory_ThenReturnsOnlyOwn() {
            // seed another company with its own purchase
            companyRepo.save(new Company(OTHER_COMPANY, "x", UUID.randomUUID()));
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(),
                    "Other Concert", OTHER_COMPANY, UUID.randomUUID(),
                    "txn-other", new BigDecimal("30.00"), Instant.now()));
            // and one purchase under our company
            orderRepo.save(purchase("Mine", new BigDecimal("99.00")));

            List<PurchaseRecordDTO> history = service.getPurchaseHistory(OWNER_TOKEN, COMPANY);

            assertEquals(1, history.size());
            assertEquals("Mine", history.get(0).eventName());
        }

        @Test
        public void GivenSnapshotPurchase_WhenSourceFieldsCannotMutate_ThenDtoStaysFrozen() {
            // The CompletedPurchase record is immutable by design — no mutator exists for
            // eventName, amount, etc. This test pins that invariant: even after time passes,
            // the DTO returned at query time matches the originally captured snapshot.
            Instant when = Instant.parse("2026-01-15T10:00:00Z");
            CompletedPurchase original = new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(),
                    "Original Event Name", COMPANY, UUID.randomUUID(),
                    "txn-snap", new BigDecimal("123.45"), when);
            orderRepo.save(original);

            PurchaseRecordDTO dto = service.getPurchaseHistory(OWNER_TOKEN, COMPANY).get(0);

            assertEquals("Original Event Name", dto.eventName());
            assertEquals(when, dto.purchasedAt());
            assertEquals(0, dto.amount().compareTo(new BigDecimal("123.45")));
        }

        private CompletedPurchase purchase(String eventName, BigDecimal amount) {
            return new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), eventName, COMPANY,
                    UUID.randomUUID(), "txn-" + UUID.randomUUID().toString().substring(0, 6),
                    amount, Instant.now());
        }
    }


        @Nested
        @DisplayName("Lifecycle")
        class Lifecycle {
        private static final String COMPANY = "Acme Productions";
        private static final String FOUNDER_TOKEN = "founder-token";
        private static final String ADMIN_TOKEN = "admin-token";
        private static final String OUTSIDER_TOKEN = "outsider-token";

        private InMemoryCompanyRepository companyRepo;
        private InMemoryEventRepository eventRepo;
        private InMemoryMemberRepository memberRepo;
        private InMemoryOrderRepository orderRepo;
        private IPaymentGateway paymentGateway;
        private ISessionTokenService tokens;
        private CompanyService service;

        private UUID founderId;
        private Member founder;
        private Company company;

        @BeforeEach
        public void setUp() {
            companyRepo = new InMemoryCompanyRepository();
            eventRepo = new InMemoryEventRepository();
            memberRepo = new InMemoryMemberRepository();
            orderRepo = new InMemoryOrderRepository();
            paymentGateway = mock(IPaymentGateway.class);
            tokens = mock(ISessionTokenService.class);
            CompanyLifecycleDomainService companyLifecycleDomainService = new CompanyLifecycleDomainService(companyRepo, eventRepo, memberRepo, orderRepo, paymentGateway);
            service = new CompanyService(companyRepo, null, tokens, memberRepo, null, companyLifecycleDomainService, null);

            founderId = UUID.randomUUID();
            founder = new Member(founderId, "founder", "founder@x.com", "pw");
            StaffAppointment ownerAppt = new StaffAppointment(
                COMPANY, founderId, StaffAppointment.StaffRole.OWNER, Set.of());
            founder.addStaffAppointment(COMPANY, ownerAppt);
            memberRepo.save(founder);

            company = new Company(COMPANY, "desc", founderId);
            companyRepo.save(company);

            when(tokens.isValid(anyString())).thenReturn(true);
            when(tokens.extractMemberId(FOUNDER_TOKEN)).thenReturn(founderId);
            when(tokens.extractPermissions(FOUNDER_TOKEN)).thenReturn(Set.of());
            when(tokens.extractMemberId(ADMIN_TOKEN)).thenReturn(UUID.randomUUID());
            when(tokens.extractPermissions(ADMIN_TOKEN)).thenReturn(Set.of("SYSTEM_ADMIN"));
            when(tokens.extractMemberId(OUTSIDER_TOKEN)).thenReturn(UUID.randomUUID());
            when(tokens.extractPermissions(OUTSIDER_TOKEN)).thenReturn(Set.of());
        }

        @Test
        public void GivenFounder_WhenSuspendCompany_ThenStatusBecomesSuspended() {
            service.suspendCompany(FOUNDER_TOKEN, COMPANY);

            Company saved = companyRepo.findByName(COMPANY).orElseThrow();
            assertEquals(CompanyStatus.SUSPENDED, saved.getStatus());
        }

        @Test
        public void GivenSuspendedCompany_WhenReopen_ThenStatusBecomesActive() {
            service.suspendCompany(FOUNDER_TOKEN, COMPANY);

            service.reopenCompany(FOUNDER_TOKEN, COMPANY);

            assertEquals(CompanyStatus.ACTIVE, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
        }

        @Test
        public void GivenFounder_WhenPermanentClose_ThenEventsCancelledPurchasesRefundedAppointmentsKept() {
            // seed two events for this company
            Event e1 = seedEvent(UUID.randomUUID());
            Event e2 = seedEvent(UUID.randomUUID());

            // seed two completed purchases tied to those events
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), e1.getId(), "Concert 1",
                COMPANY, UUID.randomUUID(), "txn-1", new BigDecimal("50.00"), java.time.Instant.now()));
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), e2.getId(), "Concert 2",
                COMPANY, UUID.randomUUID(), "txn-2", new BigDecimal("75.00"), java.time.Instant.now()));

            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-x"));

            service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);

            assertEquals(CompanyStatus.CLOSED, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
            assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());
            assertEquals(EventStatus.CANCELLED, eventRepo.findById(e2.getId()).orElseThrow().getStatus());
            verify(paymentGateway, times(2)).refund(anyString(), anyDouble());

            // Founder's appointment must be preserved per the V0 spec
            Member f = memberRepo.findById(founderId).orElseThrow();
            assertNotNull(f.getStaffAppointment(COMPANY));
        }

        @Test
        public void GivenAdmin_WhenPermanentClose_ThenAllAppointmentsRevoked() {
            Event e1 = seedEvent(UUID.randomUUID());

            // seed a second staff member (manager) appointed to the same company
            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "managerUser", "manager@x.com", "pw");
            manager.addStaffAppointment(COMPANY, new StaffAppointment(
                COMPANY, founderId, StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.EVENT_LIFECYCLE)));
            memberRepo.save(manager);

            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref"));

            service.permanentCloseByAdmin(ADMIN_TOKEN, COMPANY);

            assertEquals(CompanyStatus.CLOSED, companyRepo.findByName(COMPANY).orElseThrow().getStatus());
            assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());

            // BOTH founder and manager appointments must be gone
            assertNull(memberRepo.findById(founderId).orElseThrow().getStaffAppointment(COMPANY),
                "Admin close revokes founder appointment");
            assertNull(memberRepo.findById(managerId).orElseThrow().getStaffAppointment(COMPANY),
                "Admin close revokes manager appointment");
        }

        @Test
        public void GivenPaymentGatewayFails_WhenPermanentClose_ThenStatusBecomesPendingClosure() {
            Event e1 = seedEvent(UUID.randomUUID());
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), e1.getId(), "Concert",
                COMPANY, UUID.randomUUID(), "txn-fail", new BigDecimal("99.00"), java.time.Instant.now()));

            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("gateway down"));

            service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);

            Company c = companyRepo.findByName(COMPANY).orElseThrow();
            assertEquals(CompanyStatus.PENDING_CLOSURE, c.getStatus());
            // events still get cancelled even if refunds fail
            assertEquals(EventStatus.CANCELLED, eventRepo.findById(e1.getId()).orElseThrow().getStatus());
        }

        @Test
        public void GivenPendingClosureWithSuccessfulRetry_WhenRetry_ThenStatusBecomesClosed() {
            seedEvent(UUID.randomUUID());
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), UUID.randomUUID(), "Retry Concert",
                COMPANY, UUID.randomUUID(), "txn-retry", new BigDecimal("12.00"), java.time.Instant.now()));
            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("first try fails"));
            service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);
            assertEquals(CompanyStatus.PENDING_CLOSURE,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());

            // gateway recovers
            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-ok"));

            service.retryPendingRefunds(COMPANY);

            assertEquals(CompanyStatus.CLOSED,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());
            verify(paymentGateway, atLeastOnce()).refund(anyString(), anyDouble());
        }

        @Test
        public void GivenPendingClosureAfterServiceRestart_WhenRetry_ThenRehydratesFromRepoAndCloses() {
            // 1) seed an event + a completed purchase so there's data to refund
            Event e = seedEvent(UUID.randomUUID());
            orderRepo.save(new CompletedPurchase(UUID.randomUUID(), e.getId(), "Rehydrate Concert",
                COMPANY, UUID.randomUUID(), "txn-rehydrate", new BigDecimal("42.00"), java.time.Instant.now()));

            // 2) drive into PENDING_CLOSURE via a failing gateway
            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.failed("down"));
            service.permanentCloseByFounder(FOUNDER_TOKEN, COMPANY);
            assertEquals(CompanyStatus.PENDING_CLOSURE,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());

            // 3) simulate a service restart: build a fresh service whose in-memory queue is empty
            CompanyLifecycleDomainService freshCompanyLifecycleDomainService = new CompanyLifecycleDomainService(companyRepo, eventRepo, memberRepo, orderRepo, paymentGateway);
            CompanyService freshService = new CompanyService(companyRepo, null, tokens, memberRepo, null, freshCompanyLifecycleDomainService, null);

            // 4) gateway recovers; retry must rehydrate the queue from completedPurchaseRepo
            when(paymentGateway.refund(anyString(), anyDouble()))
                .thenReturn(RefundResult.successful("ref-ok"));

            freshService.retryPendingRefunds(COMPANY);

            assertEquals(CompanyStatus.CLOSED,
                companyRepo.findByName(COMPANY).orElseThrow().getStatus());
        }

        @Test
        public void GivenNonFounder_WhenSuspendCompany_ThenThrowSecurityException() {
            assertThrows(SecurityException.class,
                () -> service.suspendCompany(OUTSIDER_TOKEN, COMPANY));
        }

        @Test
        public void GivenNonAdmin_WhenAdminClose_ThenThrowSecurityException() {
            // founder-token is a member but does NOT have SYSTEM_ADMIN
            assertThrows(SecurityException.class,
                () -> service.permanentCloseByAdmin(FOUNDER_TOKEN, COMPANY));
        }

        @Test
        public void GivenAlreadySuspendedCompany_WhenSuspend_ThenThrowIllegalStateException() {
            service.suspendCompany(FOUNDER_TOKEN, COMPANY);

            assertThrows(IllegalStateException.class,
                () -> service.suspendCompany(FOUNDER_TOKEN, COMPANY));
        }

        @Test
        public void GivenActiveCompany_WhenReopen_ThenThrowIllegalStateException() {
            assertThrows(IllegalStateException.class,
                () -> service.reopenCompany(FOUNDER_TOKEN, COMPANY));
        }

        @Test
        public void GivenInvalidToken_WhenSuspend_ThenThrowIllegalArgumentException() {
            when(tokens.isValid("bad")).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                () -> service.suspendCompany("bad", COMPANY));
        }

        @Test
        public void GivenUnknownCompany_WhenSuspend_ThenThrowIllegalArgumentException() {
            assertThrows(IllegalArgumentException.class,
                () -> service.suspendCompany(FOUNDER_TOKEN, "Nonexistent Co"));
        }

        private Event seedEvent(UUID id) {
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            EventSchedule schedule = new EventSchedule(
                start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS));
            Event e = new Event(id, COMPANY, "Concert " + id, null, EventCategory.CONCERT,
                schedule, new LockTimerDuration(Duration.ofMinutes(15)));
            eventRepo.save(e);
            return e;
        }
        }

    @Nested
    @DisplayName("Query")
    class Query {
        private static final String COMPANY = "Acme Productions";

        private InMemoryCompanyRepository companyRepo;
        private InMemoryEventRepository eventRepo;
        private CompanyService service;

        @BeforeEach
        public void setUp() {
            companyRepo = new InMemoryCompanyRepository();
            eventRepo = new InMemoryEventRepository();
            CompanyQueryDomainService companyQueryDomainService = new CompanyQueryDomainService(companyRepo, eventRepo);
            service = new CompanyService(companyRepo, null, null, null, null, null, companyQueryDomainService);
        }

        @Test
        public void GivenActiveCompanyWithPublishedEvents_WhenGetCompanyInfo_ThenReturnsPublicDetails() {
            companyRepo.save(new Company(COMPANY, "We host concerts", UUID.randomUUID()));
            Event published = eventOf(COMPANY, "Spring Concert");
            publish(published);
            eventRepo.save(published);

            Optional<CompanyPublicDTO> dto = service.getCompanyInfo(COMPANY);

            assertTrue(dto.isPresent());
            assertEquals(COMPANY, dto.get().name());
            assertEquals("We host concerts", dto.get().description());
            assertEquals(1, dto.get().events().size());
            assertEquals("Spring Concert", dto.get().events().get(0).name());
            assertEquals(EventStatus.PUBLISHED, dto.get().events().get(0).status());
        }

        @Test
        public void GivenSuspendedCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
            Company c = new Company(COMPANY, "x", UUID.randomUUID());
            c.suspend();
            companyRepo.save(c);

            assertTrue(service.getCompanyInfo(COMPANY).isEmpty());
        }

        @Test
        public void GivenClosedCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
            Company c = new Company(COMPANY, "x", UUID.randomUUID());
            c.close();
            companyRepo.save(c);

            assertTrue(service.getCompanyInfo(COMPANY).isEmpty());
        }

        @Test
        public void GivenUnknownCompany_WhenGetCompanyInfo_ThenReturnsEmpty() {
            assertTrue(service.getCompanyInfo("Nonexistent Co").isEmpty());
        }

        @Test
        public void GivenNullOrBlankName_WhenGetCompanyInfo_ThenReturnsEmpty() {
            assertTrue(service.getCompanyInfo(null).isEmpty());
            assertTrue(service.getCompanyInfo("  ").isEmpty());
        }

        @Test
        public void GivenDraftAndCancelledEvents_WhenGetCompanyInfo_ThenOnlyVisibleStatusesReturned() {
            companyRepo.save(new Company(COMPANY, "desc", UUID.randomUUID()));

            Event draft = eventOf(COMPANY, "Draft Event"); // stays DRAFT
            Event published = eventOf(COMPANY, "Live Event");
            publish(published);
            Event cancelled = eventOf(COMPANY, "Cancelled Event");
            cancelled.cancel();

            eventRepo.save(draft);
            eventRepo.save(published);
            eventRepo.save(cancelled);

            CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

            assertEquals(1, dto.events().size());
            assertEquals("Live Event", dto.events().get(0).name());
        }

        @Test
        public void GivenActiveCompany_WhenGetCompanyInfo_ThenDtoFieldsHoldNoDomainTypes() {
            UUID founderId = UUID.randomUUID();
            companyRepo.save(new Company(COMPANY, "desc", founderId));

            service.getCompanyInfo(COMPANY).orElseThrow();

            // Type-based check — would catch a future regression where someone adds
            // e.g. `Company company` or `List<StaffAppointment>` to the DTO.
            for (Field f : CompanyPublicDTO.class.getDeclaredFields()) {
                String typeName = f.getType().getName();
                assertFalse(typeName.startsWith("com.ticketing.domain.company"),
                        "DTO field of domain type leaks Company internals: " + f);
                assertFalse(typeName.startsWith("com.ticketing.domain.member"),
                        "DTO field of domain type leaks Member internals: " + f);
            }
        }

        @Test
        public void GivenDtoReturned_WhenSourceCompanyMutated_ThenDtoUnchanged() {
            Company c = new Company(COMPANY, "original description", UUID.randomUUID());
            companyRepo.save(c);

            CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

            // mutate source after the DTO is in the caller's hands
            c.setDescription("LEAKED");

            assertEquals("original description", dto.description(),
                    "DTO must be a snapshot — not a live view of the Company");
        }

        @Test
        public void GivenDtoReturned_WhenCallerMutatesEventsList_ThenThrows() {
            companyRepo.save(new Company(COMPANY, "x", UUID.randomUUID()));
            Event e = eventOf(COMPANY, "Live");
            publish(e);
            eventRepo.save(e);

            CompanyPublicDTO dto = service.getCompanyInfo(COMPANY).orElseThrow();

            assertThrows(UnsupportedOperationException.class, () -> dto.events().clear());
        }

        // helpers

        private static Event eventOf(String companyName, String name) {
            Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
            return new Event(UUID.randomUUID(), companyName, name, "desc", EventCategory.CONCERT,
                    new EventSchedule(start, start.plus(2, ChronoUnit.HOURS), start.minus(1, ChronoUnit.HOURS)),
                    new LockTimerDuration(Duration.ofMinutes(15)));
        }

        /** publish() needs at least one zone, so add a tiny GA zone first. */
        private static void publish(Event e) {
            e.addZone(InventoryZone.createGA(UUID.randomUUID(), "Floor",
                    new java.math.BigDecimal("10.00"), 10));
            e.publish();
        }
    }






}
