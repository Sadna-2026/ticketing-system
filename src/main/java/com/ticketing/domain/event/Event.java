package com.ticketing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.ticketing.domain.order.ActiveOrder;
import com.ticketing.domain.order.OrderItem;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

@Entity
@Table(name = "events")
public class Event{

    /**
     * V1 default currency for the no-discount fallback. Lives here as a constant
     * so it's discoverable and overridable in one place. When multi-currency
     * support lands (V2+), the default policy should be injected via the
     * constructor instead of read from this constant.
     */
    @Transient
    public static final java.util.Currency DEFAULT_CURRENCY = java.util.Currency.getInstance("USD");

    @Id
    @Column(name = "id")
    private UUID id;
    @Column(name = "company_name")
    private String companyName;
    @Column(name = "name")
    private String name;
    @Column(name = "description")
    private String description;
    @Column(name = "artist")
    private String artist;
    @Enumerated(EnumType.STRING)
    @Column(name = "category")
    private EventCategory category;
    @Column(name = "region")
    private String region;
    @Embedded
    private EventSchedule schedule;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private EventStatus status;
    @Embedded
    private LockTimerDuration lockTimerDuration;
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id")
    private List<InventoryZone> zones;
    @Embedded
    private VenueMap venueMap;
    @Embedded
    private VenueLayout venueLayout;

    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "purchase_policy_id")
    private AbstractPurchasePolicy purchasePolicy;

    @ManyToOne(cascade = CascadeType.PERSIST, fetch = FetchType.EAGER)
    @JoinColumn(name = "discount_policy_id")
    private AbstractDiscountPolicy discountPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "sale_method")
    private SaleMethod saleMethod;
    // non-null only when saleMethod == LOTTERY. Embedded nullable; its instant columns
    // are prefixed (lottery_*) so they don't clash with EventSchedule's instants.
    @Embedded
    private LotteryWindow lotteryWindow;

    @Version
    @Column(name = "version")
    private int version;

    // Required by JPA; do not use directly. Defaults policies so a new Event is never null.
    protected Event() {
        this.zones = new ArrayList<>();
        this.purchasePolicy = new AlwaysAllowPolicy();
        this.discountPolicy = new NoDiscountPolicy();
    }

    /**
     * Creates a new Event with required policies and sale method.
     * Policies must be provided at creation time and cannot be changed afterward in V1.
     */
    public Event(UUID id, String companyName, String name, String description,
                 EventCategory category, EventSchedule schedule, LockTimerDuration lockTimerDuration,
                 IPurchasePolicy eventPurchasePolicy, IDiscountPolicy eventDiscountPolicy,
                 SaleMethod saleMethod, LotteryWindow lotteryWindow) {
        if (id == null) throw new IllegalArgumentException("Event ID is required");
        if (companyName == null || companyName.isBlank()) throw new IllegalArgumentException("Company name is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Event name is required");
        if (schedule == null) throw new IllegalArgumentException("Event schedule is required");
        if (lockTimerDuration == null) throw new IllegalArgumentException("Lock timer duration is required");
        if (eventPurchasePolicy == null) throw new IllegalArgumentException("EventPurchasePolicy is required");
        if (eventDiscountPolicy == null) throw new IllegalArgumentException("EventDiscountPolicy is required");
        if (saleMethod == null) throw new IllegalArgumentException("SaleMethod is required");
        if (saleMethod == SaleMethod.LOTTERY && lotteryWindow == null) {
            throw new IllegalArgumentException("LotteryWindow is required for LOTTERY sale method");
        }

        this.id = id;
        this.companyName = companyName;
        this.name = name;
        this.description = description;
        this.category = category;
        this.schedule = schedule;
        this.status = EventStatus.DRAFT;
        this.lockTimerDuration = lockTimerDuration;
        this.zones = new ArrayList<>();

        this.purchasePolicy = requirePersistentPurchasePolicy(eventPurchasePolicy);
        this.discountPolicy = requirePersistentDiscountPolicy(eventDiscountPolicy);
        this.saleMethod = saleMethod;
        this.lotteryWindow = lotteryWindow;

        this.version = 0;
    }

    public Event(UUID id, String companyName, String name, String description,
                 EventCategory category, EventSchedule schedule, LockTimerDuration lockTimerDuration,
                 IPurchasePolicy eventPurchasePolicy, IDiscountPolicy eventDiscountPolicy) {
        this(id, companyName, name, description, category, schedule, lockTimerDuration,
                eventPurchasePolicy, eventDiscountPolicy, SaleMethod.REGULAR, null);
    }

    public Event(UUID id, String companyName, String name, String description,
                 EventCategory category, EventSchedule schedule, LockTimerDuration lockTimerDuration) {
        this(id, companyName, name, description, category, schedule, lockTimerDuration,
                new AlwaysAllowPolicy(), new NoDiscountPolicy(), SaleMethod.REGULAR, null);
    }
    public LockTimerDuration getLockTimerDuration() { return lockTimerDuration; }

    public SaleMethod getSaleMethod() { return saleMethod; }
    public LotteryWindow getLotteryWindow() { return lotteryWindow; }
    public void setLotteryWindow(LotteryWindow lotteryWindow) { this.lotteryWindow = lotteryWindow; }

    public boolean isLottery() { return saleMethod == SaleMethod.LOTTERY; }

    public boolean isLotteryRegistrationOpen(Instant now) {
        return isLottery() && lotteryWindow.isOpen(now);
    }

    public InventoryZone findZone(UUID zoneId) {
        return zones.stream()
                .filter(z -> z.getId().equals(zoneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + zoneId));
    }

    public boolean hasAvailableTickets() {
        return getTotalAvailableTickets() > 0;
    }

    public int getTotalAvailableTickets() {
        return zones.stream().mapToInt(InventoryZone::getAvailableCount).sum();
    }

    public int getTotalLockedTickets() {
        return zones.stream().mapToInt(InventoryZone::getLockedCount).sum();
    }

    /**
     * True only when every ticket has actually been SOLD — none available and none merely
     * locked (reserved in a cart). Reserved tickets can still be released (cart expiry, item
     * removal, order cancellation), so an event with locked-but-unsold tickets is NOT sold
     * out. This is the condition that drives the "event sold out" notification and status
     * transition, so producers are only told an event is sold out once it genuinely is.
     */
    public boolean isFullySold() {
        return totalCapacity() > 0
                && getTotalAvailableTickets() == 0
                && getTotalLockedTickets() == 0;
    }

    /**
     * The event's full ticket capacity across all zones — GA max capacity plus the seat
     * count of assigned-seating zones. Used to bound the number of winners in an automatic
     * lottery draw (requirement §II.3.6): at most one winning slot per available ticket.
     */
    public int totalCapacity() {
        return zones.stream()
                .mapToInt(z -> z.isGA() ? z.getMaxCapacity() : z.getSeats().size())
                .sum();
    }

    public boolean isPublished() { return status == EventStatus.PUBLISHED; }

    public void markSoldOut() {
        if (status != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Can only mark a PUBLISHED event as sold out");
        }
        this.status = EventStatus.SOLD_OUT;
    }

    public void markAvailable() {
        if (status != EventStatus.SOLD_OUT) {
            throw new IllegalStateException("Can only mark a SOLD_OUT event as available");
        }
        this.status = EventStatus.PUBLISHED;
    }

    /** Releases a single reserved line item back to inventory. */
    public void releaseReservationFor(OrderItem item) {
        InventoryZone zone = findZone(item.getZoneId());
        if (item.isAssignedSeat()) {
            zone.releaseSeat(item.getSeatId());
        } else {
            zone.releaseGA(item.getQuantity());
        }
    }

    /** Releases every reserved line item on the order back to inventory. */
    public void releaseReservationsFor(ActiveOrder order) {
        for (OrderItem item : order.getItems()) {
            releaseReservationFor(item);
        }
    }

    /**
     * After tickets are released, reopen a sold-out event when stock is available again.
     *
     * @return {@code true} if the event transitioned back to {@link EventStatus#PUBLISHED}
     */
    public boolean reopenAvailabilityIfTicketsFreed() {
        if (hasAvailableTickets() && status == EventStatus.SOLD_OUT) {
            markAvailable();
            return true;
        }
        return false;
    }

    public UUID getId() {
        return id;
    }
    public String getCompanyName() { return companyName; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getArtist() { return artist; }
    public EventCategory getCategory() { return category; }
    public String getRegion() { return region; }
    public EventSchedule getSchedule() { return schedule; }
    public EventStatus getStatus() { return status; }
    public List<InventoryZone> getZones() { return java.util.Collections.unmodifiableList(zones); }
    public IPurchasePolicy getPurchasePolicy() { return purchasePolicy; }
    public IDiscountPolicy getDiscountPolicy() { return discountPolicy; }
    /** Repository-internal: called by InMemoryEventRepository.save() as part of the
     *  optimistic-lock flow. Service code should not call this directly. */
    public void incrementVersion() { this.version++; }
    public int getVersion() { return this.version; }

    /** Repository-internal (V3-11 #269): after a successful merge+flush, the JPA repo
     *  copies the post-flush optimistic-lock versions of the whole aggregate (root,
     *  zones, seats — matched by id) from the managed copy back into this (still
     *  detached) instance. This keeps a second save of the SAME aggregate within the
     *  SAME transaction (e.g. reserve then mark-sold-out) from being misread as a
     *  concurrent edit. A genuinely concurrent transaction holds a different detached
     *  snapshot that never receives this sync, so it still conflicts. Service code must
     *  not call this directly. */
    public void syncVersionsFrom(Event managed) {
        if (managed == null) {
            return;
        }
        this.version = managed.version;
        for (InventoryZone zone : this.zones) {
            managed.zones.stream()
                    .filter(m -> m.getId().equals(zone.getId()))
                    .findFirst()
                    .ifPresent(zone::syncVersionFrom);
        }
    }

    public void addZone(InventoryZone zone) {
        validateModifiable();
        if (zone == null) throw new IllegalArgumentException("Zone cannot be null");
        zones.add(zone);
    }

    /**
     * Clears all zones, the venue map, and the visual layout so a DRAFT event's hall can be
     * redefined from scratch. DRAFT-only (orphanRemoval deletes the old zones/seats on save).
     */
    public void resetVenue() {
        validateModifiable();
        zones.clear();
        venueMap = null;
        venueLayout = null;
    }

    // Two distinct guards by design:
    //   validateModifiable() — DRAFT-only — gates STRUCTURAL changes (zones, venue map)
    //   rejectIfCancelled()  — not-CANCELLED — gates EDITORIAL changes (name, schedule, etc.)
    // After publish, the venue layout is locked but core details remain editable
    // (subject to the active-reservations check in EventService.editEvent).
    private void validateModifiable() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Cannot modify event in status: " + status);
        }
    }
    
    /** True once the event is cancelled (whether or not refunds are fully settled). */
    public boolean isCancelled() {
        return status == EventStatus.CANCELLED || status == EventStatus.CANCELLED_WITH_PENDING_REFUNDS;
    }

    /** True from the moment cancellation begins — used to block new purchases immediately. */
    public boolean isCancellationStarted() {
        return status == EventStatus.CANCELLATION_IN_PROGRESS || isCancelled();
    }

    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        this.status = EventStatus.CANCELLED;
    }

    /**
     * Moves the event into {@link EventStatus#CANCELLATION_IN_PROGRESS} so purchases are blocked
     * while orders are released and refunds run. Idempotent for retries (any non-final state is
     * re-entered); a fully {@code CANCELLED} event cannot be re-cancelled.
     */
    public void beginCancellation() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        this.status = EventStatus.CANCELLATION_IN_PROGRESS;
    }

    public void completeCancellation() {
        this.status = EventStatus.CANCELLED;
    }

    public void markCancelledWithPendingRefunds() {
        this.status = EventStatus.CANCELLED_WITH_PENDING_REFUNDS;
    }

    // --- core-detail setters (used by EventService.editEvent) ---

    public void setName(String name) {
        rejectIfCancelled();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Event name cannot be blank");
        }
        this.name = name;
    }

    public void setDescription(String description) {
        rejectIfCancelled();
        this.description = description;
    }

    public void setArtist(String artist) {
        rejectIfCancelled();
        this.artist = artist;
    }

    public void setRegion(String region) {
        rejectIfCancelled();
        this.region = region;
    }

    public void setSchedule(EventSchedule schedule) {
        rejectIfCancelled();
        if (schedule == null) {
            throw new IllegalArgumentException("Schedule is required");
        }
        this.schedule = schedule;
    }

    private void rejectIfCancelled() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Cannot edit a cancelled event");
        }
    }

    public void publish() {
        if (status != EventStatus.DRAFT) {
            throw new IllegalStateException("Can only publish a DRAFT event");
        }
        if (zones.isEmpty()) {
            throw new IllegalStateException("Event must have at least one inventory zone to publish");
        }
        this.status = EventStatus.PUBLISHED;
    }

    public IPurchasePolicy getEventPurchasePolicy() {
        return purchasePolicy;
    }

    public IDiscountPolicy getEventDiscountPolicy() {
        return discountPolicy;
    }

    public void setPurchasePolicy(IPurchasePolicy policy) {
        rejectIfCancelled();
        if (policy == null) {
            throw new IllegalArgumentException("Purchase policy cannot be null");
        }
        this.purchasePolicy = requirePersistentPurchasePolicy(policy);
    }

    public void setDiscountPolicy(IDiscountPolicy policy) {
        rejectIfCancelled();
        if (policy == null) {
            throw new IllegalArgumentException("Discount policy cannot be null");
        }
        this.discountPolicy = requirePersistentDiscountPolicy(policy);
    }

    public BigDecimal calculateOrderTotal(ActiveOrder order, Instant systemClock) {
        BigDecimal originalAmount = order.getTotalPrice();
        String couponCode = order.getCouponCode();
        BigDecimal finalAmount = discountPolicy.priceAfterDiscount(order, couponCode, systemClock);

        if (couponCode != null && !couponCode.isBlank() && finalAmount.compareTo(originalAmount) >= 0) {
            // Produce a specific message when the policy is a CouponDiscount we can interrogate.
            String specificReason = resolveRejectionReason(discountPolicy, couponCode, systemClock);
            throw new IllegalArgumentException(specificReason);
        }

        return finalAmount;
    }

    /**
     * Walks the discount policy tree to find the first {@link CouponDiscount} leaf and asks it
     * why the code was rejected.  Falls back to the generic message for non-coupon policies.
     */
    private static String resolveRejectionReason(IDiscountPolicy policy, String couponCode, Instant clock) {
        if (policy instanceof CouponDiscount cd) {
            String reason = cd.getRejectionReason(couponCode, clock);
            if ("expired".equals(reason)) return "Coupon code has expired";
            if ("invalid".equals(reason)) return "Invalid coupon code";
        }
        return "Invalid or expired coupon code";
    }


    private static AbstractPurchasePolicy requirePersistentPurchasePolicy(IPurchasePolicy policy) {
        if (policy instanceof AbstractPurchasePolicy persistent) {
            return persistent;
        }
        throw new IllegalArgumentException(
                "Purchase policy must be a persistent entity type, got: " + policy.getClass().getName());
    }

    private static AbstractDiscountPolicy requirePersistentDiscountPolicy(IDiscountPolicy policy) {
        if (policy instanceof AbstractDiscountPolicy persistent) {
            return persistent;
        }
        throw new IllegalArgumentException(
                "Discount policy must be a persistent entity type, got: " + policy.getClass().getName());
    }

    public void setVenueMap(VenueMap venueMap) {
        validateModifiable();
        if (venueMap == null) {
            throw new IllegalArgumentException("VenueMap cannot be null");
        }

        Set<UUID> mappedIds = venueMap.mappedZoneIds();
        Set<UUID> zoneIds = zones.stream()
                .map(InventoryZone::getId)
                .collect(Collectors.toSet());

        Set<UUID> unknown = new HashSet<>(mappedIds);
        unknown.removeAll(zoneIds);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException(
                    "VenueMap references unknown zone ids: " + unknown);
        }

        Set<UUID> unmapped = new HashSet<>(zoneIds);
        unmapped.removeAll(mappedIds);
        if (!unmapped.isEmpty()) {
            throw new IllegalArgumentException(
                    "Zones not referenced by any venue map section: " + unmapped);
        }

        this.venueMap = venueMap;
    }

    public VenueMap getVenueMap() {
        return venueMap;
    }

    /**
     * Attaches the visual grid layout. DRAFT-only (a structural change), so managers
     * can iterate on the hall design and the layout is locked once published.
     * Referential integrity (cells point at real zones/seats) is enforced by the
     * application layer before this is called.
     */
    public void setVenueLayout(VenueLayout layout) {
        validateModifiable();
        if (layout == null) {
            throw new IllegalArgumentException("VenueLayout cannot be null");
        }
        this.venueLayout = layout;
    }

    public VenueLayout getVenueLayout() {
        return venueLayout;
    }

}
