package com.ticketing.domain.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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

    // Policy hierarchies are mapped separately in V3-6 (#264); kept @Transient here and
    // defaulted in the constructors so a reloaded Event is never null.
    @Transient
    private IPurchasePolicy purchasePolicy;
    @Transient
    private IDiscountPolicy discountPolicy;

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

    // Required by JPA; do not use directly. Defaults the @Transient policies so a
    // reloaded Event never returns null from getPurchasePolicy()/getDiscountPolicy().
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

        this.purchasePolicy = eventPurchasePolicy;
        this.discountPolicy = eventDiscountPolicy;
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

    public boolean isPublished() { return status == EventStatus.PUBLISHED; }

    public void markSoldOut() {
        if (status != EventStatus.PUBLISHED) {
            throw new IllegalStateException("Can only mark a PUBLISHED event as sold out");
        }
        this.status = EventStatus.SOLD_OUT;
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
    
    public boolean isCancelled() { return status == EventStatus.CANCELLED; }

    public void cancel() {
        if (status == EventStatus.CANCELLED) {
            throw new IllegalStateException("Event is already cancelled");
        }
        this.status = EventStatus.CANCELLED;
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
        this.purchasePolicy = policy;
    }

    public void setDiscountPolicy(IDiscountPolicy policy) {
        rejectIfCancelled();
        if (policy == null) {
            throw new IllegalArgumentException("Discount policy cannot be null");
        }
        this.discountPolicy = policy;
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
