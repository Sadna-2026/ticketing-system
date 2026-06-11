package com.ticketing.application.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.OrderService;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.exception.OptimisticLockException;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.domain.order.OrderItem;
import com.ticketing.infrastructure.PasswordEncryptionUtils;
import com.ticketing.infrastructure.gateway.StubTicketSupplyGateway;

/**
 * V3-10 (#268): verifies that each use case is one atomic, isolated transaction
 * against the JPA-backed repositories on H2. This is the first {@code @SpringBootTest}
 * in the repo, so the real Spring-wired, transaction-managed services run here (unlike
 * the unit tests that build services with {@code new} and never see a tx proxy).
 *
 * <p>A mock servlet environment is used (the default) rather than {@code NONE}: the
 * app's {@code SecurityConfig} declares a {@code SecurityFilterChain} that needs the
 * servlet-only {@code HttpSecurity} bean, which is absent under {@code NONE}. MOCK
 * boots the full Spring-wired context without binding a real port.
 * {@code ticketing.persistence=jpa} activates the DB-backed adapters,
 * {@code ddl-auto=create-drop} builds the schema for the test, and
 * {@code ticketing.seed.enabled=false} keeps the DB clean of dev-seed rows.
 */
@SpringBootTest(
        properties = {
                "ticketing.persistence=jpa",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "ticketing.seed.enabled=false",
                "ticketing.startup.initialize-platform=false"
        })
@DisplayName("Checkout transaction & double-sell on JPA/H2 (V3-10 #268)")
class CheckoutTransactionJpaTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private ICompanyRepository companyRepository;
    @Autowired
    private IEventRepository eventRepository;
    @Autowired
    private IOrderRepository orderRepository;
    @Autowired
    private IMemberRepository memberRepository;
    @Autowired
    private ISessionTokenService sessionTokenService;
    @Autowired
    private StubTicketSupplyGateway supplyGateway;
    @Autowired
    private TransactionTemplate transactionTemplate;

    private final PasswordEncryptionUtils passwords = new PasswordEncryptionUtils();

    @AfterEach
    void resetGateway() {
        // The stub is a shared singleton bean; never leave it in the failing state.
        supplyGateway.setShouldFail(false);
    }

    // ── Rollback: a mid-checkout failure rolls back ALL DB changes ────────────────

    @Test
    @DisplayName("Given a checkout that fails at ticket supply, When it throws, Then no DB changes are committed")
    void GivenCheckoutFailsAtSupply_WhenItThrows_ThenAllDbChangesRollBack() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, /* capacity */ 1));

        UUID memberId = UUID.randomUUID();
        memberRepository.save(member(memberId, "buyer"));

        String token = memberToken(memberId);

        // Reservation (its own committed transaction): locks the single GA ticket.
        orderService.createOrder(token, eventId);
        orderService.addGATicketsToOrder(token, eventId, zoneId, 1);
        UUID orderId = orderRepository.findActiveByMemberId(memberId).orElseThrow().getId();

        // Force the external ticket-supply gateway to fail mid-checkout, AFTER the
        // payment has been charged. processCheckout refunds the payment (external
        // compensation) and throws; the exception propagates out of the @Transactional
        // checkout method, so Spring rolls back every DB write made in that method.
        supplyGateway.setShouldFail(true);

        assertThatThrownBy(() -> orderService.checkout(token, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Ticket generation failed");

        // DB shows NO partial changes from the failed checkout:
        ActiveOrder reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.isActive())
                .as("order must remain ACTIVE — the CHECKOUT_IN_PROGRESS/CANCELLED writes were rolled back")
                .isTrue();

        InventoryZone zone = eventRepository.findById(eventId).orElseThrow().findZone(zoneId);
        assertThat(zone.getSoldCount()).as("nothing was sold").isZero();
        assertThat(zone.getLockedCount()).as("the pre-checkout reservation lock is intact").isEqualTo(1);

        assertThat(orderRepository.findCompletedByMemberId(memberId))
                .as("no CompletedPurchase row was inserted")
                .isEmpty();
    }

    @Test
    @DisplayName("Given a healthy checkout, When it completes, Then the sale is committed exactly once")
    void GivenHealthyCheckout_WhenItCompletes_ThenSaleIsCommitted() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, 1));

        UUID memberId = UUID.randomUUID();
        memberRepository.save(member(memberId, "happy"));
        String token = memberToken(memberId);

        orderService.createOrder(token, eventId);
        orderService.addGATicketsToOrder(token, eventId, zoneId, 1);

        UUID purchaseId = orderService.checkout(token, null);

        assertThat(orderRepository.findCompletedById(purchaseId)).isPresent();
        InventoryZone zone = eventRepository.findById(eventId).orElseThrow().findZone(zoneId);
        assertThat(zone.getSoldCount()).isEqualTo(1);
        assertThat(zone.getLockedCount()).isZero();
        assertThat(zone.getAvailableCount()).isZero();
    }

    // ── Double-sell: @Version optimistic lock across two transactions ─────────────

    /**
     * Two transactions race to claim the SAME reserved order that holds the last GA
     * ticket (e.g. a buyer's two browser tabs both finishing the cart). The
     * {@code ActiveOrder} aggregate carries a JPA {@code @Version}; both transactions
     * read the same version as independent detached snapshots, the first commits and
     * bumps the version, and the second's stale write is rejected with the domain
     * {@link OptimisticLockException}. The order therefore mutates exactly once — no
     * double claim.
     *
     * <p>NOTE for #269: the optimistic-lock guard verified here lives on the
     * {@code ActiveOrder} (and on the {@code Event} ROOT). Inventory counts mutate the
     * child {@code InventoryZone} entity, whose changes do NOT bump the parent
     * {@code Event}'s {@code @Version} and which has no {@code @Version} of its own, so
     * a pure zone-level concurrent sell is not yet version-guarded. Closing that gap
     * (e.g. {@code OPTIMISTIC_FORCE_INCREMENT} on the event, or a version on the zone)
     * is the JPA-locking work tracked by #269.
     */
    @Test
    @DisplayName("Given two transactions claiming the same last-ticket order, When both commit, Then exactly one succeeds")
    void GivenTwoTransactionsClaimingSameOrder_WhenBothCommit_ThenExactlyOneSucceeds() {
        UUID eventId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        eventRepository.save(publishedGaEvent(eventId, zoneId, 1));

        // The order that holds the last GA ticket (already reserved/committed).
        UUID orderId = UUID.randomUUID();
        ActiveOrder seed = new ActiveOrder(orderId, UUID.randomUUID(), UUID.randomUUID(),
                eventId, Instant.now());
        seed.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("45.00")));
        orderRepository.save(seed);

        // Both transactions read the SAME order version as independent detached snapshots.
        ActiveOrder snapshotA = orderRepository.findById(orderId).orElseThrow();
        ActiveOrder snapshotB = orderRepository.findById(orderId).orElseThrow();
        assertThat(snapshotA.getVersion()).isEqualTo(snapshotB.getVersion());

        // Transaction A claims first: mutate the order root and save → bumps @Version.
        transactionTemplate.executeWithoutResult(status -> {
            snapshotA.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("45.00")));
            orderRepository.save(snapshotA);
        });

        // Transaction B, built on the now-stale version, is rejected by the @Version guard.
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            snapshotB.addItem(OrderItem.forGA(UUID.randomUUID(), zoneId, 1, new BigDecimal("45.00")));
            orderRepository.save(snapshotB);
        })).isInstanceOf(OptimisticLockException.class);

        // DB ends consistent: only transaction A's single mutation was applied.
        ActiveOrder finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getItems())
                .as("exactly one writer mutated the order despite two concurrent claims")
                .hasSize(2);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private String memberToken(UUID memberId) {
        // Real JWT flow: a guest session is created (and stored as active in the token
        // repo), then upgraded to a member token bound to the same session id.
        String guestToken = sessionTokenService.generateGuestToken();
        UUID sessionId = sessionTokenService.extractSessionId(guestToken);
        return sessionTokenService.generateMemberToken(sessionId, memberId, Set.of());
    }

    private Member member(UUID id, String username) {
        return new Member(id, username, username + "@test.local",
                passwords.hashPassword("password123"));
    }

    private Event publishedGaEvent(UUID eventId, UUID zoneId, int capacity) {
        if (!companyRepository.existsByName("Acme Productions")) {
            companyRepository.save(new Company("Acme Productions", "desc", UUID.randomUUID()));
        }
        Instant start = Instant.now().plus(Duration.ofDays(30));
        Event event = new Event(
                eventId, "Acme Productions", "Race Fest", "desc",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(30)),
                new AlwaysAllowPolicy(), new NoDiscountPolicy());
        event.addZone(InventoryZone.createGA(zoneId, "Floor", new BigDecimal("45.00"), capacity));
        event.publish();
        return event;
    }
}
