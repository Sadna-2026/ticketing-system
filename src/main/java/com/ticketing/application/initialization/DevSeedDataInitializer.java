package com.ticketing.application.initialization;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.AdminService;
import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.AndPolicy;
import com.ticketing.domain.event.ConditionalDiscount;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityCondition;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.NoOrphanSeatPolicy;
import com.ticketing.domain.event.OrPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.SimpleDiscount;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.Suspension;
import com.ticketing.domain.order.CompletedPurchase;
import com.ticketing.domain.order.IOrderRepository;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

@Component
public class DevSeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedDataInitializer.class);

    public static final String COMPANY_NAME = "Demo Productions";
    public static final String SECOND_COMPANY_NAME = "Northwind Events";
    public static final String SUSPENDED_COMPANY_NAME = "Dormant Productions";
    public static final String ADMIN_CLOSE_COMPANY_NAME = "Admin Closure Company";
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    public static final UUID TEEN_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    public static final UUID INVENTORY_MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    public static final UUID SECOND_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    public static final UUID SUSPENDED_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000008");
    public static final UUID REVOKED_MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");
    public static final String SUSPENDED_MEMBER_REASON = "Manual QA — policy violation";
    /** Keeps the seeded member suspended through the next calendar year. */
    public static final Duration SUSPENDED_MEMBER_DURATION = Duration.ofDays(365);
    public static final UUID CONCERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID ADULT_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ASSIGNED_EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID CONFERENCE_EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID LARGE_ASSIGNED_EVENT_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    public static final UUID LARGE_ASSIGNED_ZONE_ID = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    public static final UUID CONCERT_GA_ZONE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID ADULT_GA_ZONE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID ASSIGNED_SEAT_ZONE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID CONFERENCE_GA_ZONE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    public static final UUID SEAT_A1_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    public static final UUID SEAT_A2_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000002");
    public static final UUID SEAT_B1_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000003");
    public static final UUID DESIGNER_DEMO_EVENT_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");
    public static final UUID DESIGNER_SEAT_ZONE_ID = UUID.fromString("66666666-0000-0000-0000-0000000000a1");
    public static final UUID DESIGNER_GA_ZONE_ID = UUID.fromString("66666666-0000-0000-0000-0000000000a2");
    public static final UUID ADMIN_CLOSE_EVENT_ID = UUID.fromString("77777777-7777-7777-7777-777777777777");
    public static final UUID ADMIN_CLOSE_GA_ZONE_ID = UUID.fromString("77777777-0000-0000-0000-0000000000a1");
    public static final UUID ADMIN_CLOSE_PURCHASE_ID = UUID.fromString("77777777-0000-0000-0000-0000000000b1");
    public static final UUID MIXED_LIMITED_EVENT_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    public static final UUID MIXED_LIMITED_SEAT_ZONE_ID = UUID.fromString("88888888-0000-0000-0000-0000000000a1");
    public static final UUID MIXED_LIMITED_GA_ZONE_ID = UUID.fromString("88888888-0000-0000-0000-0000000000a2");
    public static final UUID COUPON_CHECKOUT_EVENT_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");
    public static final UUID COUPON_CHECKOUT_GA_ZONE_ID = UUID.fromString("99999999-0000-0000-0000-000000000001");
    public static final UUID NO_ORPHAN_EVENT_ID = UUID.fromString("aabbccdd-aabb-aabb-aabb-aabbccddeeff");
    public static final UUID NO_ORPHAN_SEAT_ZONE_ID = UUID.fromString("aabbccdd-0000-0000-0000-0000000000a1");
    public static final String CHECKOUT_COUPON_CODE = "SAVE20";
    public static final UUID MIN_QTY_EVENT_ID = UUID.fromString("bbbb1111-1111-1111-1111-111111111111");
    public static final UUID MIN_QTY_SEAT_ZONE_ID = UUID.fromString("bbbb1111-0000-0000-0000-0000000000a1");
    public static final UUID MIN_QTY_GA_ZONE_ID = UUID.fromString("bbbb1111-0000-0000-0000-0000000000a2");
    public static final UUID AND_POLICY_EVENT_ID = UUID.fromString("bbbb2222-2222-2222-2222-222222222222");
    public static final UUID AND_POLICY_GA_ZONE_ID = UUID.fromString("bbbb2222-0000-0000-0000-000000000001");
    public static final UUID OR_POLICY_EVENT_ID = UUID.fromString("bbbb3333-3333-3333-3333-333333333333");
    public static final UUID OR_POLICY_GA_ZONE_ID = UUID.fromString("bbbb3333-0000-0000-0000-000000000001");
    public static final UUID SIMPLE_DISCOUNT_EVENT_ID = UUID.fromString("bbbb4444-4444-4444-4444-444444444444");
    public static final UUID SIMPLE_DISCOUNT_GA_ZONE_ID = UUID.fromString("bbbb4444-0000-0000-0000-000000000001");
    public static final UUID CONDITIONAL_DISCOUNT_EVENT_ID = UUID.fromString("bbbb5555-5555-5555-5555-555555555555");
    public static final UUID CONDITIONAL_DISCOUNT_GA_ZONE_ID = UUID.fromString("bbbb5555-0000-0000-0000-000000000001");

    private final boolean initializePlatform;
    private final boolean seedEnabled;
    private final PlatformInitializationService platformInitializationService;
    private final IMemberRepository memberRepository;
    private final IAdminRepository adminRepository;
    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;
    private final IOrderRepository orderRepository;
    private final PasswordEncryptionUtils passwordEncryptionUtils;
        private final AdminService adminService;

    public DevSeedDataInitializer(
            @Value("${ticketing.startup.initialize-platform:true}") boolean initializePlatform,
            @Value("${ticketing.seed.enabled:false}") boolean seedEnabled,
            PlatformInitializationService platformInitializationService,
            IMemberRepository memberRepository,
            IAdminRepository adminRepository,
            ICompanyRepository companyRepository,
            IEventRepository eventRepository,
            IOrderRepository orderRepository,
            PasswordEncryptionUtils passwordEncryptionUtils,    
            AdminService adminService
    ) {
        this.initializePlatform = initializePlatform;
        this.seedEnabled = seedEnabled;
        this.platformInitializationService = platformInitializationService;
        this.memberRepository = memberRepository;
        this.adminRepository = adminRepository;
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.passwordEncryptionUtils = passwordEncryptionUtils;
        this.adminService = adminService;

    }

    @Override
    public void run(ApplicationArguments args) {
        if (initializePlatform) {
            PlatformInitializationService.InitializationResult result = platformInitializationService.initialize();
            log.info("Platform initialization: {}", result.message());
        }
        if (!seedEnabled) {
            log.info("Dev seed data disabled");
            return;
        }
        seedMembersAndAdmin();
        seedCompanies();
        seedEvents();
        seedCompletedPurchases();
        log.info("Dev seed data ready: users admin/member/owner/manager/teen/inventory-manager/owner2/suspended/revoked-manager, companies '{}', '{}', '{}' and '{}'",
                COMPANY_NAME, SECOND_COMPANY_NAME, SUSPENDED_COMPANY_NAME, ADMIN_CLOSE_COMPANY_NAME);
    }

    private void seedMembersAndAdmin() {
        saveMemberIfMissing(ADMIN_ID, "admin", "admin@ticketing.local", "admin123",
                "050-000-0001", LocalDate.of(1985, 1, 1));
        saveMemberIfMissing(MEMBER_ID, "member", "member@ticketing.local", "member123",
                "050-000-0002", LocalDate.of(1995, 5, 15));
        saveMemberIfMissing(OWNER_ID, "owner", "owner@ticketing.local", "owner123",
                "050-000-0003", LocalDate.of(1988, 8, 20));
        saveMemberIfMissing(MANAGER_ID, "manager", "manager@ticketing.local", "manager123",
                "050-000-0004", LocalDate.of(1990, 2, 10));
        saveMemberIfMissing(TEEN_ID, "teen", "teen@ticketing.local", "teen123",
                "050-000-0005", LocalDate.of(2012, 1, 1));
        saveMemberIfMissing(INVENTORY_MANAGER_ID, "inventory", "inventory@ticketing.local", "inventory123",
                "050-000-0006", LocalDate.of(1992, 7, 7));
        saveMemberIfMissing(SECOND_OWNER_ID, "owner2", "owner2@ticketing.local", "owner2123",
                "050-000-0007", LocalDate.of(1984, 4, 12));
        saveMemberIfMissing(SUSPENDED_MEMBER_ID, "suspended", "suspended@ticketing.local", "suspended123",
                "050-000-0008", LocalDate.of(1993, 3, 15));
        saveMemberIfMissing(REVOKED_MANAGER_ID, "revoked-manager", "revoked-manager@ticketing.local", "revoked123",
                "050-000-0009", LocalDate.of(1991, 9, 9));
        ensureSuspendedMemberSeed();
        ensureManualQaAppointments();

        adminService.registerAdmin(ADMIN_ID, "admin", "admin@ticketing.local", "admin123");
    }

    private void ensureSuspendedMemberSeed() {
        Member member = memberRepository.findById(SUSPENDED_MEMBER_ID).orElse(null);
        if (member == null) {
            return;
        }
        if (member.isSuspended(Instant.now())) {
            return;
        }
        member.addSuspension(new Suspension(ADMIN_ID, Instant.now(), SUSPENDED_MEMBER_DURATION, SUSPENDED_MEMBER_REASON));
        memberRepository.save(member);
        log.info("Seeded suspended member '{}' until {}", member.getUsername(), Instant.now().plus(SUSPENDED_MEMBER_DURATION));
    }

    private void ensureManualQaAppointments() {
        ensureStaffAppointment(OWNER_ID, SUSPENDED_COMPANY_NAME, null,
                StaffAppointment.StaffRole.OWNER, Set.of());
        ensureStaffAppointment(MANAGER_ID, SECOND_COMPANY_NAME, SECOND_OWNER_ID,
                StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.VIEW_REPORTS));
        ensureStaffAppointment(MANAGER_ID, SUSPENDED_COMPANY_NAME, OWNER_ID,
                StaffAppointment.StaffRole.MANAGER, Set.of(ManagerPermission.VIEW_REPORTS));
        ensureStaffAppointment(SECOND_OWNER_ID, ADMIN_CLOSE_COMPANY_NAME, null,
                StaffAppointment.StaffRole.OWNER, Set.of());
        ensureStaffAppointment(MANAGER_ID, ADMIN_CLOSE_COMPANY_NAME, SECOND_OWNER_ID,
                StaffAppointment.StaffRole.MANAGER,
                Set.of(ManagerPermission.EVENT_LIFECYCLE, ManagerPermission.VIEW_REPORTS));
    }

    private void ensureStaffAppointment(
            UUID memberId,
            String companyName,
            UUID appointedByMemberId,
            StaffAppointment.StaffRole role,
            Set<ManagerPermission> permissions
    ) {
        Member member = memberRepository.findById(memberId).orElse(null);
        if (member == null || member.getStaffAppointment(companyName) != null) {
            return;
        }
        member.addStaffAppointment(companyName,
                new StaffAppointment(companyName, appointedByMemberId, role, permissions));
        memberRepository.save(member);
        log.info("Seeded {} appointment for '{}' in '{}'", role, member.getUsername(), companyName);
    }

    private void saveMemberIfMissing(
            UUID id,
            String username,
            String email,
            String password,
            String phoneNumber,
            LocalDate dateOfBirth
    ) {
        if (memberRepository.existsByUsername(username)) {
            return;
        }
        Member member = new Member(id, username, email, passwordEncryptionUtils.hashPassword(password),
                phoneNumber, dateOfBirth);
        if (OWNER_ID.equals(id)) {
            member.addStaffAppointment(COMPANY_NAME,
                    new StaffAppointment(COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Set.of()));
            member.addStaffAppointment(SUSPENDED_COMPANY_NAME,
                    new StaffAppointment(SUSPENDED_COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Set.of()));
        }
        if (MANAGER_ID.equals(id)) {
            member.addStaffAppointment(COMPANY_NAME,
                    new StaffAppointment(COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.VIEW_REPORTS)));
            member.addStaffAppointment(SECOND_COMPANY_NAME,
                    new StaffAppointment(SECOND_COMPANY_NAME, SECOND_OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.VIEW_REPORTS)));
            member.addStaffAppointment(SUSPENDED_COMPANY_NAME,
                    new StaffAppointment(SUSPENDED_COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.VIEW_REPORTS)));
        }
        if (INVENTORY_MANAGER_ID.equals(id)) {
            member.addStaffAppointment(COMPANY_NAME,
                    new StaffAppointment(COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT,
                                    ManagerPermission.EVENT_LIFECYCLE, ManagerPermission.POLICY_MODIFICATION,
                                    ManagerPermission.VIEW_REPORTS)));
        }
        if (REVOKED_MANAGER_ID.equals(id)) {
            StaffAppointment revokedAppointment = new StaffAppointment(COMPANY_NAME, OWNER_ID,
                    StaffAppointment.StaffRole.MANAGER,
                    Set.of(ManagerPermission.HANDLE_INQUIRIES, ManagerPermission.VIEW_REPORTS));
            revokedAppointment.revoke();
            member.addStaffAppointment(COMPANY_NAME, revokedAppointment);
        }
        if (SECOND_OWNER_ID.equals(id)) {
            member.addStaffAppointment(SECOND_COMPANY_NAME,
                    new StaffAppointment(SECOND_COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Set.of()));
            member.addStaffAppointment(ADMIN_CLOSE_COMPANY_NAME,
                    new StaffAppointment(ADMIN_CLOSE_COMPANY_NAME, null, StaffAppointment.StaffRole.OWNER, Set.of()));
        }
        memberRepository.save(member);
    }

    private void seedCompanies() {
        if (!companyRepository.existsByName(COMPANY_NAME)) {
            companyRepository.save(new Company(COMPANY_NAME,
                    "Seeded company for Vaadin V2 manual QA.", OWNER_ID));
        }
        if (!companyRepository.existsByName(SECOND_COMPANY_NAME)) {
            companyRepository.save(new Company(SECOND_COMPANY_NAME,
                    "Second seeded company for search and cross-company checks.", SECOND_OWNER_ID));
        }
        if (!companyRepository.existsByName(SUSPENDED_COMPANY_NAME)) {
            Company suspendedCompany = new Company(SUSPENDED_COMPANY_NAME,
                    "Suspended seeded company for reopen lifecycle QA.", OWNER_ID);
            suspendedCompany.suspend();
            companyRepository.save(suspendedCompany);
        }
        if (!companyRepository.existsByName(ADMIN_CLOSE_COMPANY_NAME)) {
            companyRepository.save(new Company(ADMIN_CLOSE_COMPANY_NAME,
                    "Disposable company for admin-close manual QA.", SECOND_OWNER_ID));
        }
    }

    private void seedEvents() {
        saveGaEventIfMissing(CONCERT_ID, COMPANY_NAME, "Demo Concert",
                "General seeded event for browsing and checkout.", EventCategory.CONCERT,
                new AlwaysAllowPolicy(), CONCERT_GA_ZONE_ID, "Main floor", new BigDecimal("45.00"), 100);
        saveGaEventIfMissing(ADULT_EVENT_ID, COMPANY_NAME, "18+ Policy Test Event",
                "Seeded event that should reject under-age buyers at checkout.",
                EventCategory.CONCERT, new AgeRestrictionPolicy(18), ADULT_GA_ZONE_ID, "18+ floor",
                new BigDecimal("30.00"), 80);
        saveAssignedEventIfMissing();
        saveLargeAssignedEventIfMissing();
        saveDesignerDemoEventIfMissing();
        saveGaEventIfMissing(CONFERENCE_EVENT_ID, SECOND_COMPANY_NAME, "Northwind Tech Summit",
                "Second-company event with a max-quantity policy for permission and search QA.",
                EventCategory.CONFERENCE, new MaxQuantityPolicy(4), CONFERENCE_GA_ZONE_ID, "Auditorium",
                new BigDecimal("75.00"), 60);
        saveGaEventIfMissing(ADMIN_CLOSE_EVENT_ID, ADMIN_CLOSE_COMPANY_NAME, "Admin Close Company Event",
                "Seeded event cancelled when an admin closes the demo company.",
                EventCategory.CONFERENCE, new AlwaysAllowPolicy(), ADMIN_CLOSE_GA_ZONE_ID, "Demo hall",
                new BigDecimal("25.00"), 40);
        saveCouponCheckoutEventIfMissing();
        saveMixedLimitedEventIfMissing();
        saveNoOrphanSeatEventIfMissing();
        saveMinQuantityEventIfMissing();
        saveAndPolicyEventIfMissing();
        saveOrPolicyEventIfMissing();
        saveSimpleDiscountEventIfMissing();
        saveConditionalDiscountEventIfMissing();
    }

    private void saveCouponCheckoutEventIfMissing() {
        if (eventRepository.findById(COUPON_CHECKOUT_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(30));
        Instant couponExpiry = Instant.now().plus(Duration.ofDays(365));
        Event event = new Event(COUPON_CHECKOUT_EVENT_ID, COMPANY_NAME, "Coupon Checkout Demo",
                "UI-13 manual QA: add 2 GA tickets at 50.00 each; coupon "
                        + CHECKOUT_COUPON_CODE + " gives 20% off (pay 80.00).",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(),
                new CouponDiscount(new BigDecimal("20"), CHECKOUT_COUPON_CODE, couponExpiry));
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(COUPON_CHECKOUT_GA_ZONE_ID, "Discount floor",
                new BigDecimal("50.00"), 100));
        event.setVenueMap(new VenueMap(Map.of("Discount floor", COUPON_CHECKOUT_GA_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded coupon checkout demo event '{}' with coupon {}", event.getName(), CHECKOUT_COUPON_CODE);
    }

    private void seedCompletedPurchases() {
        if (orderRepository.findCompletedById(ADMIN_CLOSE_PURCHASE_ID).isPresent()) {
        } else {
            orderRepository.save(new CompletedPurchase(
                    ADMIN_CLOSE_PURCHASE_ID,
                    ADMIN_CLOSE_EVENT_ID,
                    "Admin Close Company Event",
                    ADMIN_CLOSE_COMPANY_NAME,
                    MEMBER_ID,
                    "member",
                    "seed-admin-close-txn",
                    new BigDecimal("25.00"),
                    Instant.now().minus(Duration.ofDays(1))));
        }

        Instant base = Instant.now().minus(Duration.ofDays(5));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000001", CONCERT_ID, "Demo Concert",
                MANAGER_ID, "manager", "SEED-DEMO-001", "45.00", base.plus(Duration.ofHours(1)));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000002", CONCERT_ID, "Demo Concert",
                MANAGER_ID, "manager", "SEED-DEMO-002", "45.00", base.plus(Duration.ofHours(3)));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000003", CONCERT_ID, "Demo Concert",
                MANAGER_ID, "manager", "SEED-DEMO-003", "90.00", base.plus(Duration.ofHours(8)));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000004", CONCERT_ID, "Demo Concert",
                MANAGER_ID, "manager", "SEED-DEMO-004", "135.00", base.plus(Duration.ofHours(12)));

        savePurchaseIfMissing("90000000-0000-0000-0000-000000000005", DESIGNER_DEMO_EVENT_ID, "Designer Demo",
                MANAGER_ID, "manager", "SEED-DESIGN-001", "50.00", base.plus(Duration.ofDays(1)));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000006", DESIGNER_DEMO_EVENT_ID, "Designer Demo",
                MANAGER_ID, "manager", "SEED-DESIGN-002", "120.00", base.plus(Duration.ofDays(1).plus(Duration.ofHours(2))));
        savePurchaseIfMissing("90000000-0000-0000-0000-000000000007", DESIGNER_DEMO_EVENT_ID, "Designer Demo",
                MANAGER_ID, "manager", "SEED-DESIGN-003", "170.00", base.plus(Duration.ofDays(1).plus(Duration.ofHours(6))));
    }

    private void savePurchaseIfMissing(
            String purchaseId,
            UUID eventId,
            String eventName,
            UUID memberId,
            String buyerUsername,
            String transactionId,
            String amount,
            Instant purchasedAt
    ) {
        UUID id = UUID.fromString(purchaseId);
        if (orderRepository.findCompletedById(id).isPresent()) {
            return;
        }
        orderRepository.save(new CompletedPurchase(id, eventId, eventName, COMPANY_NAME, memberId, buyerUsername,
                transactionId, new BigDecimal(amount), purchasedAt));
    }

    private void saveGaEventIfMissing(
            UUID eventId,
            String companyName,
            String name,
            String description,
            EventCategory category,
            com.ticketing.domain.event.IPurchasePolicy purchasePolicy,
            UUID zoneId,
            String zoneName,
            BigDecimal price,
            int capacity
    ) {
        if (eventRepository.findById(eventId).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(30));
        Event event = new Event(eventId, companyName, name, description,
                category,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                purchasePolicy,
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(zoneId, zoneName, price, capacity));
        event.setVenueMap(new VenueMap(Map.of(zoneName, zoneId)));
        event.publish();
        eventRepository.save(event);
    }

    private void saveAssignedEventIfMissing() {
        if (eventRepository.findById(ASSIGNED_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(45));
        Event event = new Event(ASSIGNED_EVENT_ID, COMPANY_NAME, "Assigned Seating Demo",
                "Seeded event with deterministic seat IDs for assigned-seat order QA.",
                EventCategory.PLAY,
                new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofMinutes(45))),
                new LockTimerDuration(Duration.ofMinutes(10)),
                new AlwaysAllowPolicy(),
                new NoDiscountPolicy());
        event.setArtist("QA Theater");
        event.setRegion("Tel Aviv");
        InventoryZone zone = InventoryZone.createAssigned(ASSIGNED_SEAT_ZONE_ID, "Orchestra", new BigDecimal("120.00"));
        zone.addSeat(new Seat(SEAT_A1_ID, "A", "1"));
        zone.addSeat(new Seat(SEAT_A2_ID, "A", "2"));
        zone.addSeat(new Seat(SEAT_B1_ID, "B", "1"));
        event.addZone(zone);
        event.setVenueMap(new VenueMap(Map.of("Orchestra", ASSIGNED_SEAT_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
    }

    // Large assigned-seating event (26 rows A-Z x 25 seats = 650) for seat-map
    // scalability QA (#255). Local QA fixture — not part of the #255 PR.
    private void saveLargeAssignedEventIfMissing() {
        if (eventRepository.findById(LARGE_ASSIGNED_EVENT_ID).isPresent()) {
            return;
        }
        int rows = 26;
        int seatsPerRow = 25;
        Instant start = Instant.now().plus(Duration.ofDays(60));
        Event event = new Event(LARGE_ASSIGNED_EVENT_ID, COMPANY_NAME, "Grand Theatre (650 seats)",
                "Seeded large assigned-seating event for seat-map scalability QA.",
                EventCategory.PLAY,
                new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofMinutes(45))),
                new LockTimerDuration(Duration.ofMinutes(10)),
                new AlwaysAllowPolicy(),
                new NoDiscountPolicy());
        event.setArtist("QA Theater");
        event.setRegion("Tel Aviv");
        InventoryZone zone = InventoryZone.createAssigned(LARGE_ASSIGNED_ZONE_ID, "Auditorium", new BigDecimal("95.00"));
        for (int r = 0; r < rows; r++) {
            String row = Character.toString((char) ('A' + r));
            for (int s = 1; s <= seatsPerRow; s++) {
                zone.addSeat(new Seat(UUID.randomUUID(), row, String.valueOf(s)));
            }
        }
        event.addZone(zone);
        event.setVenueMap(new VenueMap(Map.of("Auditorium", LARGE_ASSIGNED_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded large assigned event '{}' with {} seats", event.getName(), rows * seatsPerRow);
    }

    // Event built the way the visual hall designer (FIX-V2-25) produces them: a mix of
    // exact seats, a GA area, a stage, blocked aisle cells and an entrance object, with a
    // VenueLayout so the buyer-facing event map renders the hall grid out of the box.
    private void saveDesignerDemoEventIfMissing() {
        if (eventRepository.findById(DESIGNER_DEMO_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(20));
        Event event = new Event(DESIGNER_DEMO_EVENT_ID, COMPANY_NAME, "Designer Demo",
                "Seeded hall built with the visual designer: seats + GA + stage + blocked + entrance.",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(),
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");

        // Reserved seating zone with 6 seats (A1-A3, B1-B3).
        InventoryZone seating = InventoryZone.createAssigned(DESIGNER_SEAT_ZONE_ID, "Reserved Seating", new BigDecimal("120.00"));
        Seat a1 = new Seat(UUID.randomUUID(), "A", "1");
        Seat a2 = new Seat(UUID.randomUUID(), "A", "2");
        Seat a3 = new Seat(UUID.randomUUID(), "A", "3");
        Seat b1 = new Seat(UUID.randomUUID(), "B", "1");
        Seat b2 = new Seat(UUID.randomUUID(), "B", "2");
        Seat b3 = new Seat(UUID.randomUUID(), "B", "3");
        seating.addSeat(a1);
        seating.addSeat(a2);
        seating.addSeat(a3);
        seating.addSeat(b1);
        seating.addSeat(b2);
        seating.addSeat(b3);
        event.addZone(seating);

        // General-admission zone.
        event.addZone(InventoryZone.createGA(DESIGNER_GA_ZONE_ID, "General Admission", new BigDecimal("50.00"), 200));

        event.setVenueMap(new VenueMap(Map.of(
                "Reserved Seating", DESIGNER_SEAT_ZONE_ID,
                "General Admission", DESIGNER_GA_ZONE_ID)));

        // 5x8 visual grid: stage (row 0), blocked aisle (row 1), seats (rows 2-3),
        // entrance + GA (row 4).
        List<LayoutCell> cells = List.of(
                LayoutCell.stage(0, 3, "Main Stage"),
                LayoutCell.stage(0, 4, "Main Stage"),
                LayoutCell.blocked(1, 0),
                LayoutCell.blocked(1, 1),
                LayoutCell.blocked(1, 2),
                LayoutCell.blocked(1, 3),
                LayoutCell.seat(2, 1, DESIGNER_SEAT_ZONE_ID, a1.getId()),
                LayoutCell.seat(2, 2, DESIGNER_SEAT_ZONE_ID, a2.getId()),
                LayoutCell.seat(2, 3, DESIGNER_SEAT_ZONE_ID, a3.getId()),
                LayoutCell.seat(3, 1, DESIGNER_SEAT_ZONE_ID, b1.getId()),
                LayoutCell.seat(3, 2, DESIGNER_SEAT_ZONE_ID, b2.getId()),
                LayoutCell.seat(3, 3, DESIGNER_SEAT_ZONE_ID, b3.getId()),
                LayoutCell.object(4, 0, "Entrance"),
                LayoutCell.ga(4, 5, DESIGNER_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(4, 6, DESIGNER_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(4, 7, DESIGNER_GA_ZONE_ID, "Floor"));
        event.setVenueLayout(new VenueLayout(5, 8, cells));

        event.publish();
        eventRepository.save(event);
        log.info("Seeded designer-built event '{}' with a {}-cell hall layout", event.getName(), cells.size());
    }

    private void saveMixedLimitedEventIfMissing() {
        if (eventRepository.findById(MIXED_LIMITED_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(25));
        Event event = new Event(MIXED_LIMITED_EVENT_ID, COMPANY_NAME, "Mixed Limited Event",
                "Seeded hall built to test MaxQuantityPolicy(3) across GA and assigned seats.",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new MaxQuantityPolicy(3),
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");

        // Reserved seating zone with 3 seats
        InventoryZone seating = InventoryZone.createAssigned(MIXED_LIMITED_SEAT_ZONE_ID, "Reserved Seating", new BigDecimal("120.00"));
        Seat a1 = new Seat(UUID.randomUUID(), "A", "1");
        Seat a2 = new Seat(UUID.randomUUID(), "A", "2");
        Seat a3 = new Seat(UUID.randomUUID(), "A", "3");
        seating.addSeat(a1);
        seating.addSeat(a2);
        seating.addSeat(a3);
        event.addZone(seating);

        // General-admission zone
        event.addZone(InventoryZone.createGA(MIXED_LIMITED_GA_ZONE_ID, "General Admission", new BigDecimal("50.00"), 200));

        event.setVenueMap(new VenueMap(Map.of(
                "Reserved Seating", MIXED_LIMITED_SEAT_ZONE_ID,
                "General Admission", MIXED_LIMITED_GA_ZONE_ID)));

        List<LayoutCell> cells = List.of(
                LayoutCell.stage(0, 1, "Main Stage"),
                LayoutCell.stage(0, 2, "Main Stage"),
                LayoutCell.seat(1, 1, MIXED_LIMITED_SEAT_ZONE_ID, a1.getId()),
                LayoutCell.seat(1, 2, MIXED_LIMITED_SEAT_ZONE_ID, a2.getId()),
                LayoutCell.seat(1, 3, MIXED_LIMITED_SEAT_ZONE_ID, a3.getId()),
                LayoutCell.ga(2, 1, MIXED_LIMITED_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(2, 2, MIXED_LIMITED_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(2, 3, MIXED_LIMITED_GA_ZONE_ID, "Floor"));
        event.setVenueLayout(new VenueLayout(3, 4, cells));

        event.publish();
        eventRepository.save(event);
        log.info("Seeded mixed limited event '{}' with max quantity 3", event.getName());
    }

    private void saveNoOrphanSeatEventIfMissing() {
        if (eventRepository.findById(NO_ORPHAN_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(35));
        Event event = new Event(NO_ORPHAN_EVENT_ID, COMPANY_NAME, "No-Orphan Seat Demo",
                "Seeded event with NoOrphanSeatPolicy: selecting seats that leave a single isolated seat is rejected.",
                EventCategory.PLAY,
                new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofMinutes(45))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new NoOrphanSeatPolicy(),
                new NoDiscountPolicy());
        event.setArtist("QA Theater");
        event.setRegion("Tel Aviv");

        InventoryZone zone = InventoryZone.createAssigned(NO_ORPHAN_SEAT_ZONE_ID, "Main Hall", new BigDecimal("100.00"));
        Seat a1 = new Seat(UUID.randomUUID(), "A", "1");
        Seat a2 = new Seat(UUID.randomUUID(), "A", "2");
        Seat a3 = new Seat(UUID.randomUUID(), "A", "3");
        Seat a4 = new Seat(UUID.randomUUID(), "A", "4");
        Seat a5 = new Seat(UUID.randomUUID(), "A", "5");
        Seat b1 = new Seat(UUID.randomUUID(), "B", "1");
        Seat b2 = new Seat(UUID.randomUUID(), "B", "2");
        Seat b3 = new Seat(UUID.randomUUID(), "B", "3");
        Seat b4 = new Seat(UUID.randomUUID(), "B", "4");
        Seat b5 = new Seat(UUID.randomUUID(), "B", "5");
        for (Seat s : List.of(a1, a2, a3, a4, a5, b1, b2, b3, b4, b5)) {
            zone.addSeat(s);
        }
        event.addZone(zone);
        event.setVenueMap(new VenueMap(Map.of("Main Hall", NO_ORPHAN_SEAT_ZONE_ID)));

        List<LayoutCell> cells = List.of(
                LayoutCell.stage(0, 1, "Stage"),
                LayoutCell.stage(0, 2, "Stage"),
                LayoutCell.stage(0, 3, "Stage"),
                LayoutCell.seat(1, 0, NO_ORPHAN_SEAT_ZONE_ID, a1.getId()),
                LayoutCell.seat(1, 1, NO_ORPHAN_SEAT_ZONE_ID, a2.getId()),
                LayoutCell.seat(1, 2, NO_ORPHAN_SEAT_ZONE_ID, a3.getId()),
                LayoutCell.seat(1, 3, NO_ORPHAN_SEAT_ZONE_ID, a4.getId()),
                LayoutCell.seat(1, 4, NO_ORPHAN_SEAT_ZONE_ID, a5.getId()),
                LayoutCell.seat(2, 0, NO_ORPHAN_SEAT_ZONE_ID, b1.getId()),
                LayoutCell.seat(2, 1, NO_ORPHAN_SEAT_ZONE_ID, b2.getId()),
                LayoutCell.seat(2, 2, NO_ORPHAN_SEAT_ZONE_ID, b3.getId()),
                LayoutCell.seat(2, 3, NO_ORPHAN_SEAT_ZONE_ID, b4.getId()),
                LayoutCell.seat(2, 4, NO_ORPHAN_SEAT_ZONE_ID, b5.getId()));
        event.setVenueLayout(new VenueLayout(3, 5, cells));

        event.publish();
        eventRepository.save(event);
        log.info("Seeded no-orphan-seat event '{}' with 2 rows x 5 seats", event.getName());
    }

    private void saveMinQuantityEventIfMissing() {
        if (eventRepository.findById(MIN_QTY_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(26));
        Event event = new Event(MIN_QTY_EVENT_ID, COMPANY_NAME, "Min Quantity Demo",
                "UI-45 QA: requires at least 2 tickets per order. Same hall layout as Mixed Limited Event.",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new MinQuantityPolicy(2),
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");

        InventoryZone seating = InventoryZone.createAssigned(MIN_QTY_SEAT_ZONE_ID, "Reserved Seating", new BigDecimal("120.00"));
        Seat a1 = new Seat(UUID.randomUUID(), "A", "1");
        Seat a2 = new Seat(UUID.randomUUID(), "A", "2");
        Seat a3 = new Seat(UUID.randomUUID(), "A", "3");
        seating.addSeat(a1);
        seating.addSeat(a2);
        seating.addSeat(a3);
        event.addZone(seating);

        event.addZone(InventoryZone.createGA(MIN_QTY_GA_ZONE_ID, "General Admission", new BigDecimal("50.00"), 200));

        event.setVenueMap(new VenueMap(Map.of(
                "Reserved Seating", MIN_QTY_SEAT_ZONE_ID,
                "General Admission", MIN_QTY_GA_ZONE_ID)));

        List<LayoutCell> cells = List.of(
                LayoutCell.stage(0, 1, "Main Stage"),
                LayoutCell.stage(0, 2, "Main Stage"),
                LayoutCell.seat(1, 1, MIN_QTY_SEAT_ZONE_ID, a1.getId()),
                LayoutCell.seat(1, 2, MIN_QTY_SEAT_ZONE_ID, a2.getId()),
                LayoutCell.seat(1, 3, MIN_QTY_SEAT_ZONE_ID, a3.getId()),
                LayoutCell.ga(2, 1, MIN_QTY_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(2, 2, MIN_QTY_GA_ZONE_ID, "Floor"),
                LayoutCell.ga(2, 3, MIN_QTY_GA_ZONE_ID, "Floor"));
        event.setVenueLayout(new VenueLayout(3, 4, cells));

        event.publish();
        eventRepository.save(event);
        log.info("Seeded min quantity demo event '{}' with mixed hall layout", event.getName());
    }

    private void saveAndPolicyEventIfMissing() {
        if (eventRepository.findById(AND_POLICY_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(28));
        Event event = new Event(AND_POLICY_EVENT_ID, COMPANY_NAME, "AND Policy Demo",
                "UI-45 QA: buyers must be 18+ and may buy at most 2 tickets (both rules apply).",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AndPolicy(List.of(new AgeRestrictionPolicy(18), new MaxQuantityPolicy(2))),
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(AND_POLICY_GA_ZONE_ID, "Strict floor",
                new BigDecimal("55.00"), 60));
        event.setVenueMap(new VenueMap(Map.of("Strict floor", AND_POLICY_GA_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded AND policy demo event '{}'", event.getName());
    }

    private void saveOrPolicyEventIfMissing() {
        if (eventRepository.findById(OR_POLICY_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(32));
        Event event = new Event(OR_POLICY_EVENT_ID, COMPANY_NAME, "OR Policy Demo",
                "UI-45 QA: purchase is allowed if you buy 3+ tickets OR you are 21+ (either path works).",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new OrPolicy(List.of(new MinQuantityPolicy(3), new AgeRestrictionPolicy(21))),
                new NoDiscountPolicy());
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(OR_POLICY_GA_ZONE_ID, "Flexible floor",
                new BigDecimal("35.00"), 80));
        event.setVenueMap(new VenueMap(Map.of("Flexible floor", OR_POLICY_GA_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded OR policy demo event '{}'", event.getName());
    }

    private void saveSimpleDiscountEventIfMissing() {
        if (eventRepository.findById(SIMPLE_DISCOUNT_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(22));
        Event event = new Event(SIMPLE_DISCOUNT_EVENT_ID, COMPANY_NAME, "Simple Discount Demo",
                "UI-45 QA: 10% off all tickets is shown on the event map; no coupon required.",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(),
                new SimpleDiscount(new BigDecimal("10")));
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(SIMPLE_DISCOUNT_GA_ZONE_ID, "Promo floor",
                new BigDecimal("50.00"), 100));
        event.setVenueMap(new VenueMap(Map.of("Promo floor", SIMPLE_DISCOUNT_GA_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded simple discount demo event '{}'", event.getName());
    }

    private void saveConditionalDiscountEventIfMissing() {
        if (eventRepository.findById(CONDITIONAL_DISCOUNT_EVENT_ID).isPresent()) {
            return;
        }
        Instant start = Instant.now().plus(Duration.ofDays(24));
        Event event = new Event(CONDITIONAL_DISCOUNT_EVENT_ID, COMPANY_NAME, "Conditional Discount Demo",
                "UI-45 QA: 15% off appears on the map when buying 3+ tickets; checkout quote reflects it.",
                EventCategory.CONCERT,
                new EventSchedule(start, start.plus(Duration.ofHours(3)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(15)),
                new AlwaysAllowPolicy(),
                new ConditionalDiscount(new BigDecimal("15"), new MinQuantityCondition(3)));
        event.setArtist("QA Band");
        event.setRegion("Beer Sheva");
        event.addZone(InventoryZone.createGA(CONDITIONAL_DISCOUNT_GA_ZONE_ID, "Bundle floor",
                new BigDecimal("40.00"), 100));
        event.setVenueMap(new VenueMap(Map.of("Bundle floor", CONDITIONAL_DISCOUNT_GA_ZONE_ID)));
        event.publish();
        eventRepository.save(event);
        log.info("Seeded conditional discount demo event '{}'", event.getName());
    }
}
