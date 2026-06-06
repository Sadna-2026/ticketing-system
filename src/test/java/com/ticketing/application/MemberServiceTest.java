package com.ticketing.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.ticketing.application.auth.ISessionTokenRepository;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.auth.SessionTokenService;
import com.ticketing.application.dto.ActiveOrderDto;
import com.ticketing.application.dto.OrgNodeDTO;
import com.ticketing.application.services.MemberService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.request.UpdateMemberDetailsRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.LogoutResponse;
import com.ticketing.domain.member.response.MemberExitResponse;
import com.ticketing.domain.member.response.RegisterResponse;
import com.ticketing.domain.member.response.UpdateMemberDetailsResponse;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderCheckoutDomainService;
import com.ticketing.domain.order.TicketReservationDomainService;
import com.ticketing.infrastructure.InMemoryEventRepository;
import com.ticketing.infrastructure.InMemoryMemberRepository;
import com.ticketing.infrastructure.InMemoryOrderRepository;
import com.ticketing.infrastructure.InMemorySessionTokenRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

@DisplayName("MemberService")
class MemberServiceTest {

    @Nested
    @DisplayName("Login")
    class Login {

        private InMemoryEventRepository eventRepository;
        private IMemberRepository memberRepository;
        private InMemoryOrderRepository orderRepository;
        private SessionTokenService sessionTokenService;
        private MemberService memberService;
        private OrderService orderService;

        @BeforeEach
        void setUp() {
            String secret = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );

            ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

            memberRepository = new InMemoryMemberRepository();
            orderRepository = new InMemoryOrderRepository();
            sessionTokenService = new SessionTokenService(secret, 120, tokenRepository);
            PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();

            memberService = new MemberService(
                    memberRepository,
                    passwordEncryptionUtils,
                    sessionTokenService
            );

            eventRepository = new InMemoryEventRepository();
            TestClock clock = new TestClock(Instant.parse("2026-06-01T10:00:00Z"));
            TicketReservationDomainService ticketReservationDomainService = new TicketReservationDomainService(orderRepository, eventRepository, clock, memberRepository);
            OrderCheckoutDomainService orderCheckoutDomainService = new OrderCheckoutDomainService(orderRepository, eventRepository, null, List.of(), List.of(), clock);
            orderService = new OrderService(sessionTokenService, ticketReservationDomainService, orderCheckoutDomainService, null, null, null, null, null);
        }

        @Test
        @DisplayName("SuccessfulLogin - returns valid member token")
        void GivenGuestAndValidCredentials_WhenLogin_ThenGuestTokenUpgradedToMemberToken() {
            RegisterResponse registered = registerMember("tamar", "tamar@example.com", "123456");
            memberService.logout(registered.sessionToken());

            String guestToken = sessionTokenService.generateGuestToken();
            UUID originalSessionId = sessionTokenService.extractSessionId(guestToken);

            LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), guestToken);

            assertTrue(response.success());
            assertEquals("Member logged in successfully.", response.message());
            assertNotNull(response.member());
            assertEquals(registered.member().memberId(), response.member().memberId());
            assertEquals("tamar", response.member().username());
            assertEquals("tamar@example.com", response.member().email());
            assertNotNull(response.sessionToken());

            assertFalse(sessionTokenService.isValid(guestToken));
            assertTrue(sessionTokenService.isValid(response.sessionToken()));
            assertEquals(originalSessionId, sessionTokenService.extractSessionId(response.sessionToken()));
            assertEquals(registered.member().memberId(), sessionTokenService.extractMemberId(response.sessionToken()));
        }

        @Test
        @DisplayName("WrongPassword rejected")
        void GivenGuestAndWrongPassword_WhenLogin_ThenRejectedAndGuestStillConnected() {
            registerMember("tamar", "tamar@example.com", "123456");
            String guestToken = sessionTokenService.generateGuestToken();

            LoginResponse response = memberService.login(new LoginRequest("tamar", "wrong-password"), guestToken);

            assertFalse(response.success());
            assertEquals("Invalid username or password.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("NonexistentUser rejected")
        void GivenGuestAndNonexistentUsername_WhenLogin_ThenRejectedAndGuestStillConnected() {
            String guestToken = sessionTokenService.generateGuestToken();

            LoginResponse response = memberService.login(new LoginRequest("missing", "123456"), guestToken);

            assertFalse(response.success());
            assertEquals("Invalid username or password.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("Recoverable active order behavior follows member-exit rules")
        void GivenGuestWithActiveOrder_WhenLogin_ThenOrderRemainsRecoverableBySession() {
            registerMember("tamar", "tamar@example.com", "123456");
            String guestToken = sessionTokenService.generateGuestToken();
            UUID sessionId = sessionTokenService.extractSessionId(guestToken);
            UUID orderId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            com.ticketing.domain.event.Event event = new com.ticketing.domain.event.Event(
                    eventId,
                    "Test Company",
                    "Test Event",
                    "desc",
                    com.ticketing.domain.event.EventCategory.CONCERT,
                    new com.ticketing.domain.event.EventSchedule(
                            Instant.parse("2026-07-01T20:00:00Z"),
                            Instant.parse("2026-07-01T23:00:00Z"),
                            Instant.parse("2026-07-01T19:00:00Z")
                    ),
                    new com.ticketing.domain.event.LockTimerDuration(java.time.Duration.ofMinutes(15))
            );
            event.addZone(com.ticketing.domain.event.InventoryZone.createGA(UUID.randomUUID(), "Zone", new java.math.BigDecimal("10.00"), 100));
            event.publish();
            ((InMemoryEventRepository) eventRepository).save(event);
            orderRepository.save(new ActiveOrder(orderId, sessionId, eventId, Instant.parse("2026-06-01T10:00:00Z")));

            LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), guestToken);

            assertTrue(response.success());
            assertEquals(sessionId, sessionTokenService.extractSessionId(response.sessionToken()));
            assertTrue(orderRepository.findActiveBySessionId(sessionId).isPresent());

            ActiveOrderDto activeOrder = orderService.getActiveOrder(response.sessionToken());
            assertEquals(orderId, activeOrder.getId());
            assertEquals(sessionId, activeOrder.getSessionId());
            assertEquals(eventId, activeOrder.getEventId());
        }

        @Test
        @DisplayName("InvalidLoginToken rejected")
        void GivenInvalidGuestToken_WhenLogin_ThenRejected() {
            registerMember("tamar", "tamar@example.com", "123456");

            LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), "not.a.real.jwt");

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("MemberToken rejected")
        void GivenMemberToken_WhenLogin_ThenRejected() {
            RegisterResponse registered = registerMember("tamar", "tamar@example.com", "123456");

            LoginResponse response = memberService.login(
                    new LoginRequest("tamar", "123456"),
                    registered.sessionToken()
            );

            assertFalse(response.success());
            assertEquals("Only guests can log in.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
            assertTrue(sessionTokenService.isValid(registered.sessionToken()));
        }

        @Test
        @DisplayName("InvalidCredentialsFormat rejected")
        void GivenBlankCredentials_WhenLogin_ThenRejectedAndGuestStillConnected() {
            String guestToken = sessionTokenService.generateGuestToken();

            LoginResponse response = memberService.login(new LoginRequest(" ", " "), guestToken);

            assertFalse(response.success());
            assertEquals("Invalid credentials.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("ConcurrentLogin - one guest token can only be upgraded once")
        void GivenSameGuestToken_WhenLoginConcurrently_ThenOnlyOneLoginSucceeds() throws Exception {
            registerMember("tamar", "tamar@example.com", "123456");
            String guestToken = sessionTokenService.generateGuestToken();
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Runnable loginAttempt = () -> {
                try {
                    startLatch.await();
                    LoginResponse response = memberService.login(new LoginRequest("tamar", "123456"), guestToken);
                    if (response.success()) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                }
            };

            executor.submit(loginAttempt);
            executor.submit(loginAttempt);

            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            assertEquals(1, successes.get());
            assertEquals(1, failures.get());
            assertFalse(sessionTokenService.isValid(guestToken));
        }

        @Test
        @DisplayName("ConcurrentLogin - different users can log in independently")
        void GivenDifferentGuestTokensAndUsers_WhenLoginConcurrently_ThenBothLoginsSucceed() throws Exception {
            registerMember("tamar", "tamar@example.com", "123456");
            registerMember("daniel", "daniel@example.com", "123456");
            String tamarGuestToken = sessionTokenService.generateGuestToken();
            String danielGuestToken = sessionTokenService.generateGuestToken();
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger(0);
            AtomicInteger failures = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Runnable tamarLogin = concurrentLoginAttempt(
                    startLatch,
                    successes,
                    failures,
                    new LoginRequest("tamar", "123456"),
                    tamarGuestToken
            );
            Runnable danielLogin = concurrentLoginAttempt(
                    startLatch,
                    successes,
                    failures,
                    new LoginRequest("daniel", "123456"),
                    danielGuestToken
            );

            executor.submit(tamarLogin);
            executor.submit(danielLogin);

            startLatch.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

            assertEquals(2, successes.get());
            assertEquals(0, failures.get());
            assertFalse(sessionTokenService.isValid(tamarGuestToken));
            assertFalse(sessionTokenService.isValid(danielGuestToken));
        }

        private RegisterResponse registerMember(String username, String email, String password) {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterResponse response = memberService.register(
                    new RegisterRequest(username, email, password, "0501234567", "2000-01-01"),
                    guestToken
            );

            assertTrue(response.success());
            assertNotNull(response.sessionToken());

            return response;
        }

        private Runnable concurrentLoginAttempt(
                CountDownLatch startLatch,
                AtomicInteger successes,
                AtomicInteger failures,
                LoginRequest request,
                String guestToken
        ) {
            return () -> {
                try {
                    startLatch.await();
                    LoginResponse response = memberService.login(request, guestToken);
                    if (response.success()) {
                        successes.incrementAndGet();
                    } else {
                        failures.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failures.incrementAndGet();
                }
            };
        }
    }

    @Nested
    @DisplayName("Register")
    class Register {

        private IMemberRepository memberRepository;
        private PasswordEncryptionUtils passwordEncryptionUtils;
        private SessionTokenService sessionTokenService;
        private MemberService memberService;

        @BeforeEach
        void setUp() {
            String secret = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );

            ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

            memberRepository = new InMemoryMemberRepository();
            passwordEncryptionUtils = new PasswordEncryptionUtils();

            sessionTokenService = new SessionTokenService(
                    secret,
                    120,
                    tokenRepository
            );

            memberService = new MemberService(
                    memberRepository,
                    passwordEncryptionUtils,
                    sessionTokenService
            );
        }

        @Test
        @DisplayName("SuccessfulRegistration — guest with unique details is registered and auto-logged-in")
        void GivenGuestWithUniqueRegistrationDetails_WhenRegister_ThenMemberCreatedAndMemberTokenReturned() {
            String guestToken = sessionTokenService.generateGuestToken();
            UUID originalSessionId = sessionTokenService.extractSessionId(guestToken);

            RegisterRequest request = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse response = memberService.register(request, guestToken);

            assertTrue(response.success());
            assertEquals("Member registered and logged in successfully.", response.message());
            assertNotNull(response.member());
            assertEquals("tamar", response.member().username());
            assertEquals("tamar@example.com", response.member().email());
            assertNotNull(response.sessionToken());

            assertFalse(sessionTokenService.isValid(guestToken));
            assertTrue(sessionTokenService.isValid(response.sessionToken()));

            assertEquals(
                    originalSessionId,
                    sessionTokenService.extractSessionId(response.sessionToken())
            );

            assertEquals(
                    response.member().memberId(),
                    sessionTokenService.extractMemberId(response.sessionToken())
            );
        }

        @Test
        @DisplayName("DuplicateRegistrationDetails — duplicate username is denied")
        void GivenExistingMemberWithSameUsername_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
            String firstGuestToken = sessionTokenService.generateGuestToken();

            RegisterRequest firstRequest = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse firstResponse = memberService.register(firstRequest, firstGuestToken);
            assertTrue(firstResponse.success());

            String secondGuestToken = sessionTokenService.generateGuestToken();

            RegisterRequest duplicateUsernameRequest = registerRequest(
                    "tamar",
                    "other@example.com",
                    "123456"
            );

            RegisterResponse secondResponse =
                    memberService.register(duplicateUsernameRequest, secondGuestToken);

            assertFalse(secondResponse.success());
            assertEquals("Username already in use.", secondResponse.message());
            assertNull(secondResponse.member());
            assertNull(secondResponse.sessionToken());

            assertTrue(sessionTokenService.isValid(secondGuestToken));
            assertNull(sessionTokenService.extractMemberId(secondGuestToken));
        }

        @Test
        @DisplayName("DuplicateRegistrationDetails — duplicate email is denied")
        void GivenExistingMemberWithSameEmail_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
            String firstGuestToken = sessionTokenService.generateGuestToken();

            RegisterRequest firstRequest = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse firstResponse = memberService.register(firstRequest, firstGuestToken);
            assertTrue(firstResponse.success());

            String secondGuestToken = sessionTokenService.generateGuestToken();

            RegisterRequest duplicateEmailRequest = registerRequest(
                    "other",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse secondResponse =
                    memberService.register(duplicateEmailRequest, secondGuestToken);

            assertFalse(secondResponse.success());
            assertEquals("Email already in use.", secondResponse.message());
            assertNull(secondResponse.member());
            assertNull(secondResponse.sessionToken());

            assertTrue(sessionTokenService.isValid(secondGuestToken));
            assertNull(sessionTokenService.extractMemberId(secondGuestToken));
        }

        @Test
        @DisplayName("InvalidFields — blank username, invalid email, and short password are denied")
        void GivenInvalidRegistrationFields_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterRequest invalidRequest = registerRequest(
                    "",
                    "bad-email",
                    "123"
            );

            RegisterResponse response = memberService.register(invalidRequest, guestToken);

            assertFalse(response.success());
            assertEquals("Invalid registration details.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("InvalidFields — null request is denied")
        void GivenNullRegisterRequest_WhenRegister_ThenRegistrationDeniedAndGuestStillConnected() {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterResponse response = memberService.register(null, guestToken);

            assertFalse(response.success());
            assertEquals("Invalid registration details.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("InvalidToken — invalid token is denied")
        void GivenInvalidSessionToken_WhenRegister_ThenRegistrationDenied() {
            String invalidToken = "not.a.real.jwt";

            RegisterRequest request = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse response = memberService.register(request, invalidToken);

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("InvalidToken — null token is denied")
        void GivenNullSessionToken_WhenRegister_ThenRegistrationDenied() {
            RegisterRequest request = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse response = memberService.register(request, null);

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("InvalidToken — blank token is denied")
        void GivenBlankSessionToken_WhenRegister_ThenRegistrationDenied() {
            RegisterRequest request = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse response = memberService.register(request, "   ");

            assertFalse(response.success());
            assertEquals("Invalid session token.", response.message());
            assertNull(response.member());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("Only guests can register — member token is denied")
        void GivenMemberSessionToken_WhenRegister_ThenRegistrationDenied() {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterRequest firstRequest = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    "123456"
            );

            RegisterResponse firstResponse = memberService.register(firstRequest, guestToken);
            assertTrue(firstResponse.success());
            assertNotNull(firstResponse.sessionToken());

            RegisterRequest secondRequest = registerRequest(
                    "other",
                    "other@example.com",
                    "123456"
            );

            RegisterResponse secondResponse =
                    memberService.register(secondRequest, firstResponse.sessionToken());

            assertFalse(secondResponse.success());
            assertEquals("Only guests can register.", secondResponse.message());
            assertNull(secondResponse.member());
            assertNull(secondResponse.sessionToken());
        }

        @Test
        @DisplayName("PasswordSecurity — password is stored as BCrypt hash, not plaintext")
        void GivenSuccessfulRegistration_WhenInspectStoredMember_ThenPasswordIsHashedNotPlaintext() {
            String guestToken = sessionTokenService.generateGuestToken();
            String rawPassword = "123456";

            RegisterRequest request = registerRequest(
                    "tamar",
                    "tamar@example.com",
                    rawPassword
            );

            RegisterResponse response = memberService.register(request, guestToken);

            assertTrue(response.success());

            Member savedMember = memberRepository.findByUsername("tamar")
                    .orElseThrow();

            assertNotEquals(rawPassword, savedMember.getEncryptedPassword());
            assertTrue(passwordEncryptionUtils.matches(
                    rawPassword,
                    savedMember.getEncryptedPassword()
            ));
        }

        private RegisterRequest registerRequest(String username, String email, String password) {
            return new RegisterRequest(
                    username,
                    email,
                    password,
                    "0501234567",
                    "2000-01-01"
            );
        }
    }

    @Nested
    @DisplayName("Logout")
    class Logout {

        private IMemberRepository memberRepository;
        private PasswordEncryptionUtils passwordEncryptionUtils;
        private SessionTokenService sessionTokenService;
        private MemberService memberService;

        @BeforeEach
        void setUp() {
            String secret = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );

            ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

            memberRepository = new InMemoryMemberRepository();
            passwordEncryptionUtils = new PasswordEncryptionUtils();

            sessionTokenService = new SessionTokenService(
                    secret,
                    120,
                    tokenRepository
            );

            memberService = new MemberService(
                    memberRepository,
                    passwordEncryptionUtils,
                    sessionTokenService
            );
        }

        @Test
        @DisplayName("SuccessfulMemberLogout — member session is invalidated and visitor continues as guest")
        void GivenLoggedInMember_WhenLogout_ThenMemberTokenInvalidatedAndGuestTokenReturned() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            String memberToken = registerResponse.sessionToken();

            assertTrue(sessionTokenService.isValid(memberToken));
            assertNotNull(sessionTokenService.extractMemberId(memberToken));

            LogoutResponse logoutResponse = memberService.logout(memberToken);

            assertTrue(logoutResponse.success());
            assertEquals("Member logged out successfully.", logoutResponse.message());
            assertNotNull(logoutResponse.sessionToken());

            assertFalse(sessionTokenService.isValid(memberToken));

            assertTrue(sessionTokenService.isValid(logoutResponse.sessionToken()));
            assertNull(sessionTokenService.extractMemberId(logoutResponse.sessionToken()));
        }

        @Test
        @DisplayName("LogoutWithoutLogin — guest token is denied")
        void GivenGuestToken_WhenLogout_ThenSystemDeniesRequest() {
            String guestToken = sessionTokenService.generateGuestToken();

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));

            LogoutResponse response = memberService.logout(guestToken);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
            assertNull(response.sessionToken());

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("LogoutWithoutLogin — already logged out member token is denied")
        void GivenAlreadyLoggedOutMemberToken_WhenLogoutAgain_ThenSystemDeniesRequest() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            String memberToken = registerResponse.sessionToken();

            LogoutResponse firstLogout = memberService.logout(memberToken);
            assertTrue(firstLogout.success());

            LogoutResponse secondLogout = memberService.logout(memberToken);

            assertFalse(secondLogout.success());
            assertEquals("No authenticated member session exists.", secondLogout.message());
            assertNull(secondLogout.sessionToken());
        }

        @Test
        @DisplayName("LogoutWithoutLogin — invalid token is denied")
        void GivenInvalidToken_WhenLogout_ThenSystemDeniesRequest() {
            String invalidToken = "not.a.real.jwt";

            LogoutResponse response = memberService.logout(invalidToken);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("LogoutWithoutLogin — null token is denied")
        void GivenNullToken_WhenLogout_ThenSystemDeniesRequest() {

            LogoutResponse response = memberService.logout(null);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
            assertNull(response.sessionToken());
        }

        @Test
        @DisplayName("LogoutWithoutLogin — blank token is denied")
        void GivenBlankToken_WhenLogout_ThenSystemDeniesRequest() {

            LogoutResponse response = memberService.logout("   ");

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
            assertNull(response.sessionToken());
        }

        private RegisterResponse registerMember(String username, String email) {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterRequest request = new RegisterRequest(
                    username,
                    email,
                    "123456",
                    "0501234567",
                    "2000-01-01"
            );

            RegisterResponse response = memberService.register(request, guestToken);

            assertTrue(response.success());
            assertNotNull(response.sessionToken());

            return response;
        }
    }

    @Nested
    @DisplayName("Update identifying details")
    class UpdateIdentifyingDetails {

        private IMemberRepository memberRepository;
        private SessionTokenService sessionTokenService;
        private MemberService memberService;

        @BeforeEach
        void setUp() {
            String secret = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );

            ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

            memberRepository = new InMemoryMemberRepository();
            PasswordEncryptionUtils passwordEncryptionUtils = new PasswordEncryptionUtils();

            sessionTokenService = new SessionTokenService(
                    secret,
                    120,
                    tokenRepository
            );

            memberService = new MemberService(
                    memberRepository,
                    passwordEncryptionUtils,
                    sessionTokenService
            );
        }

        @Test
        @DisplayName("Member updates own details successfully")
        void GivenLoggedInMember_WhenUpdateOwnDetails_ThenDetailsUpdatedAndDtoReturned() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            UUID memberId = registerResponse.member().memberId();

            UpdateMemberDetailsRequest request = updateRequest(
                    "tamar-new",
                    "new@example.com",
                    "0527654321",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

            assertTrue(response.success());
            assertEquals("Member details updated successfully.", response.message());
            assertNotNull(response.member());
            assertEquals(memberId, response.member().memberId());
            assertEquals("tamar-new", response.member().username());
            assertEquals("new@example.com", response.member().email());
            assertEquals("0527654321", response.member().phoneNumber());
            assertEquals(LocalDate.parse("1999-05-06"), response.member().dateOfBirth());

            Member savedMember = memberRepository.findById(memberId).orElseThrow();
            assertEquals("tamar-new", savedMember.getUsername());
            assertEquals("new@example.com", savedMember.getEmail());
            assertEquals("0527654321", savedMember.getPhoneNumber());
            assertEquals(LocalDate.parse("1999-05-06"), savedMember.getDateOfBirth());
        }

        @Test
        @DisplayName("Member updates only username successfully")
        void GivenLoggedInMember_WhenUpdateOnlyUsername_ThenOtherDetailsRemainUnchanged() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            UUID memberId = registerResponse.member().memberId();

            UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(
                    "tamar-new",
                    null,
                    null,
                    (LocalDate) null
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

            assertTrue(response.success());
            assertEquals("tamar-new", response.member().username());
            assertEquals("tamar@example.com", response.member().email());
            assertEquals("0501234567", response.member().phoneNumber());
            assertEquals(LocalDate.parse("2000-01-01"), response.member().dateOfBirth());
        }

        @Test
        @DisplayName("Member updates only email successfully")
        void GivenLoggedInMember_WhenUpdateOnlyEmail_ThenOtherDetailsRemainUnchanged() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            UUID memberId = registerResponse.member().memberId();

            UpdateMemberDetailsRequest request = new UpdateMemberDetailsRequest(
                    null,
                    "new@example.com",
                    null,
                    (LocalDate) null
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(registerResponse.sessionToken(), memberId, request);

            assertTrue(response.success());
            assertEquals("tamar", response.member().username());
            assertEquals("new@example.com", response.member().email());
            assertEquals("0501234567", response.member().phoneNumber());
            assertEquals(LocalDate.parse("2000-01-01"), response.member().dateOfBirth());
        }

        @Test
        @DisplayName("Cannot update another member's details")
        void GivenLoggedInMember_WhenUpdateAnotherMemberDetails_ThenDenied() {
            RegisterResponse tamar = registerMember("tamar", "tamar@example.com");
            RegisterResponse other = registerMember("other", "other@example.com");

            UpdateMemberDetailsRequest request = updateRequest(
                    "hijacked",
                    "hijacked@example.com",
                    "0527654321",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(tamar.sessionToken(), other.member().memberId(), request);

            assertFalse(response.success());
            assertEquals("Members can only update their own details.", response.message());
            assertNull(response.member());

            Member otherMember = memberRepository.findById(other.member().memberId()).orElseThrow();
            assertEquals("other", otherMember.getUsername());
            assertEquals("other@example.com", otherMember.getEmail());
        }

        @Test
        @DisplayName("Invalid/empty fields rejected")
        void GivenInvalidFields_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");

            UpdateMemberDetailsRequest request = updateRequest(
                    "",
                    "bad-email",
                    " ",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(
                            registerResponse.sessionToken(),
                            registerResponse.member().memberId(),
                            request
                    );

            assertFalse(response.success());
            assertEquals("Invalid member details.", response.message());
            assertNull(response.member());

            Member savedMember = memberRepository.findById(registerResponse.member().memberId()).orElseThrow();
            assertEquals("tamar", savedMember.getUsername());
            assertEquals("tamar@example.com", savedMember.getEmail());
        }

        @Test
        @DisplayName("Duplicate username rejected")
        void GivenDuplicateUsername_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
            registerMember("existing", "existing@example.com");
            RegisterResponse tamar = registerMember("tamar", "tamar@example.com");

            UpdateMemberDetailsRequest request = updateRequest(
                    "existing",
                    "new@example.com",
                    "0527654321",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(tamar.sessionToken(), tamar.member().memberId(), request);

            assertFalse(response.success());
            assertEquals("Username or email already in use.", response.message());
            assertNull(response.member());

            Member savedMember = memberRepository.findById(tamar.member().memberId()).orElseThrow();
            assertEquals("tamar", savedMember.getUsername());
            assertEquals("tamar@example.com", savedMember.getEmail());
        }

        @Test
        @DisplayName("Duplicate email rejected")
        void GivenDuplicateEmail_WhenUpdateOwnDetails_ThenDeniedAndDetailsUnchanged() {
            registerMember("existing", "existing@example.com");
            RegisterResponse tamar = registerMember("tamar", "tamar@example.com");

            UpdateMemberDetailsRequest request = updateRequest(
                    "tamar-new",
                    "existing@example.com",
                    "0527654321",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(tamar.sessionToken(), tamar.member().memberId(), request);

            assertFalse(response.success());
            assertEquals("Username or email already in use.", response.message());
            assertNull(response.member());

            Member savedMember = memberRepository.findById(tamar.member().memberId()).orElseThrow();
            assertEquals("tamar", savedMember.getUsername());
            assertEquals("tamar@example.com", savedMember.getEmail());
        }

        @Test
        @DisplayName("Returns DTO, not domain object")
        void GivenSuccessfulUpdate_WhenInspectResponse_ThenResponseContainsMemberDtoOnly() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");

            UpdateMemberDetailsRequest request = updateRequest(
                    "tamar-new",
                    "new@example.com",
                    "0527654321",
                    "1999-05-06"
            );

            UpdateMemberDetailsResponse response =
                    memberService.updateIdentifyingDetails(
                            registerResponse.sessionToken(),
                            registerResponse.member().memberId(),
                            request
                    );

            assertTrue(response.success());
            assertNotNull(response.member());
            assertEquals("MemberDto", response.member().getClass().getSimpleName());
        }

        private RegisterResponse registerMember(String username, String email) {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterRequest request = new RegisterRequest(
                    username,
                    email,
                    "123456",
                    "0501234567",
                    "2000-01-01"
            );

            RegisterResponse response = memberService.register(request, guestToken);

            assertTrue(response.success());
            assertNotNull(response.sessionToken());

            return response;
        }

        private UpdateMemberDetailsRequest updateRequest(
                String username,
                String email,
                String phoneNumber,
                String dateOfBirth
        ) {
            return new UpdateMemberDetailsRequest(username, email, phoneNumber, dateOfBirth);
        }
    }

    @Nested
    @DisplayName("Exit platform")
    class ExitPlatform {

        private IMemberRepository memberRepository;
        private PasswordEncryptionUtils passwordEncryptionUtils;
        private SessionTokenService sessionTokenService;
        private MemberService memberService;

        @BeforeEach
        void setUp() {
            String secret = Base64.getEncoder().encodeToString(
                    "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)
            );

            ISessionTokenRepository tokenRepository = new InMemorySessionTokenRepository();

            memberRepository = new InMemoryMemberRepository();
            passwordEncryptionUtils = new PasswordEncryptionUtils();

            sessionTokenService = new SessionTokenService(
                    secret,
                    120,
                    tokenRepository
            );

            memberService = new MemberService(
                    memberRepository,
                    passwordEncryptionUtils,
                    sessionTokenService
            );
        }

        @Test
        @DisplayName("SuccessfulMemberExit — member session token is invalidated")
        void GivenLoggedInMember_WhenExitPlatform_ThenMemberSessionTokenInvalidated() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            String memberToken = registerResponse.sessionToken();

            assertTrue(sessionTokenService.isValid(memberToken));
            assertNotNull(sessionTokenService.extractMemberId(memberToken));

            MemberExitResponse response = memberService.exitPlatform(memberToken);

            assertTrue(response.success());
            assertEquals("Member null exited platform successfully.", response.message());

            assertFalse(sessionTokenService.isValid(memberToken));
        }

        @Test
        @DisplayName("ExitWithoutLogin — guest token is denied and remains valid")
        void GivenGuestToken_WhenExitPlatform_ThenSystemDeniesRequestAndGuestTokenStaysValid() {
            String guestToken = sessionTokenService.generateGuestToken();

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));

            MemberExitResponse response = memberService.exitPlatform(guestToken);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());

            assertTrue(sessionTokenService.isValid(guestToken));
            assertNull(sessionTokenService.extractMemberId(guestToken));
        }

        @Test
        @DisplayName("ExitWithoutLogin — invalid token is denied")
        void GivenInvalidToken_WhenExitPlatform_ThenSystemDeniesRequest() {
            String invalidToken = "not.a.real.jwt";

            MemberExitResponse response = memberService.exitPlatform(invalidToken);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
        }

        @Test
        @DisplayName("ExitWithoutLogin — null token is denied")
        void GivenNullToken_WhenExitPlatform_ThenSystemDeniesRequest() {

            MemberExitResponse response = memberService.exitPlatform(null);

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
        }

        @Test
        @DisplayName("ExitWithoutLogin — blank token is denied")
        void GivenBlankToken_WhenExitPlatform_ThenSystemDeniesRequest() {

            MemberExitResponse response = memberService.exitPlatform("   ");

            assertFalse(response.success());
            assertEquals("No authenticated member session exists.", response.message());
        }

        @Test
        @DisplayName("ExitWithoutLogin — already exited member token is denied")
        void GivenAlreadyExitedMemberToken_WhenExitPlatformAgain_ThenSystemDeniesRequest() {
            RegisterResponse registerResponse = registerMember("tamar", "tamar@example.com");
            String memberToken = registerResponse.sessionToken();

            MemberExitResponse firstExit = memberService.exitPlatform(memberToken);

            assertTrue(firstExit.success());
            assertFalse(sessionTokenService.isValid(memberToken));

            MemberExitResponse secondExit = memberService.exitPlatform(memberToken);

            assertFalse(secondExit.success());
            assertEquals("No authenticated member session exists.", secondExit.message());
        }

        private RegisterResponse registerMember(String username, String email) {
            String guestToken = sessionTokenService.generateGuestToken();

            RegisterRequest request = new RegisterRequest(
                    username,
                    email,
                    "123456",
                    "0501234567",
                    "2000-01-01"
            );

            RegisterResponse response = memberService.register(request, guestToken);

            assertTrue(response.success());
            assertNotNull(response.sessionToken());

            return response;
        }
    }

    @Nested
    @DisplayName("Organization chart")
    class OrganizationChart {

        private IMemberRepository memberRepository;
        private PasswordEncryptionUtils passwordEncryptionUtils;
        private ISessionTokenService sessionTokenService;
        private MemberService memberService;

        private final String COMPANY_NAME = "TestComp";
        private final String AUTH_TOKEN = "valid-token";
        private UUID ownerId;

        @BeforeEach
        void setUp() {
            memberRepository = mock(IMemberRepository.class);
            passwordEncryptionUtils = new PasswordEncryptionUtils();
            sessionTokenService = mock(ISessionTokenService.class);
            memberService = new MemberService(memberRepository, passwordEncryptionUtils, sessionTokenService);

            ownerId = UUID.randomUUID();
            when(sessionTokenService.isValid(AUTH_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(AUTH_TOKEN)).thenReturn(ownerId);
        }

        @Test
        void GivenManagerCaller_WhenGetOrganizationChart_ThenSecurityException() {
            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, ownerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            when(sessionTokenService.extractMemberId(AUTH_TOKEN)).thenReturn(managerId);
            when(memberRepository.findById(managerId)).thenReturn(Optional.of(manager));

            assertThrows(SecurityException.class, () -> {
                memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);
            });
        }

        @Test
        void GivenInvalidToken_WhenGetOrganizationChart_ThenIllegalArgumentException() {
            when(sessionTokenService.isValid(AUTH_TOKEN)).thenReturn(false);
            assertThrows(IllegalArgumentException.class, () -> memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenGuestToken_WhenGetOrganizationChart_ThenSecurityException() {
            when(sessionTokenService.isValid(AUTH_TOKEN)).thenReturn(true);
            when(sessionTokenService.extractMemberId(AUTH_TOKEN)).thenReturn(null);
            assertThrows(SecurityException.class, () -> memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME));
        }

        @Test
        void GivenOwnerAndStaffHierarchy_WhenGetOrganizationChart_ThenTreeBuiltWithPermissions() {
            Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
            owner.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            manager.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, ownerId, StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.VIEW_REPORTS)));

            UUID juniorId = UUID.randomUUID();
            Member junior = new Member(juniorId, "junior", "j@test.com", "pass");
            junior.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, managerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            when(memberRepository.findByCompanyAppointment(COMPANY_NAME)).thenReturn(List.of(owner, manager, junior));

            List<OrgNodeDTO> chart = memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);

            assertEquals(1, chart.size());
            OrgNodeDTO root = chart.get(0);
            assertEquals(ownerId, root.memberId());
            assertEquals(1, root.subordinates().size());

            OrgNodeDTO managerNode = root.subordinates().get(0);
            assertEquals(managerId, managerNode.memberId());
            assertTrue(managerNode.permissions().contains(ManagerPermission.VIEW_REPORTS));
            assertEquals(1, managerNode.subordinates().size());

            OrgNodeDTO juniorNode = managerNode.subordinates().get(0);
            assertEquals(juniorId, juniorNode.memberId());
            assertTrue(juniorNode.subordinates().isEmpty());
            assertFalse(managerNode.revoked());
        }

        @Test
        void GivenRevokedManagerWithSubordinate_WhenGetOrganizationChart_ThenRevokedNodeMarkedWithSubtree() {
            Member owner = new Member(ownerId, "owner", "o@test.com", "pass");
            owner.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID managerId = UUID.randomUUID();
            Member manager = new Member(managerId, "manager", "m@test.com", "pass");
            StaffAppointment managerAppt = new StaffAppointment(
                    COMPANY_NAME, ownerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet());
            managerAppt.revoke();
            manager.addStaffAppointment(COMPANY_NAME, managerAppt);

            UUID juniorId = UUID.randomUUID();
            Member junior = new Member(juniorId, "junior", "j@test.com", "pass");
            junior.addStaffAppointment(COMPANY_NAME, new StaffAppointment(
                    COMPANY_NAME, managerId, StaffAppointment.StaffRole.MANAGER, Collections.emptySet()));

            when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner));
            when(memberRepository.findByCompanyAppointment(COMPANY_NAME)).thenReturn(List.of(owner, manager, junior));

            List<OrgNodeDTO> chart = memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);

            OrgNodeDTO managerNode = chart.get(0).subordinates().get(0);
            assertTrue(managerNode.revoked());
            assertEquals(1, managerNode.subordinates().size());
            assertEquals(juniorId, managerNode.subordinates().get(0).memberId());
            assertFalse(managerNode.subordinates().get(0).revoked());
        }

        @Test
        void GivenMultipleTopLevelOwners_WhenGetOrganizationChart_ThenMultipleRootsReturned() {
            Member owner1 = new Member(ownerId, "owner1", "o1@test.com", "pass");
            owner1.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            UUID owner2Id = UUID.randomUUID();
            Member owner2 = new Member(owner2Id, "owner2", "o2@test.com", "pass");
            owner2.addStaffAppointment(COMPANY_NAME, new StaffAppointment(COMPANY_NAME, UUID.randomUUID(), StaffAppointment.StaffRole.OWNER, Collections.emptySet()));

            when(memberRepository.findById(ownerId)).thenReturn(Optional.of(owner1));
            when(memberRepository.findByCompanyAppointment(COMPANY_NAME)).thenReturn(List.of(owner1, owner2));

            List<OrgNodeDTO> chart = memberService.getOrganizationChart(AUTH_TOKEN, COMPANY_NAME);

            assertEquals(2, chart.size());
        }
    }
}
