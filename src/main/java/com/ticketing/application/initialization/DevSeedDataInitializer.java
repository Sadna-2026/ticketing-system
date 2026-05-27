package com.ticketing.application.initialization;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.ticketing.application.services.PlatformInitializationService;
import com.ticketing.domain.admin.Admin;
import com.ticketing.domain.admin.IAdminRepository;
import com.ticketing.domain.company.Company;
import com.ticketing.domain.company.ICompanyRepository;
import com.ticketing.domain.event.AgeRestrictionPolicy;
import com.ticketing.domain.event.AlwaysAllowPolicy;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.MaxQuantityPolicy;
import com.ticketing.domain.event.MinQuantityPolicy;
import com.ticketing.domain.event.NoDiscountPolicy;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueMap;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.infrastructure.PasswordEncryptionUtils;

@Component
public class DevSeedDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedDataInitializer.class);

    public static final String COMPANY_NAME = "Demo Productions";
    public static final String SECOND_COMPANY_NAME = "Northwind Events";
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    public static final UUID TEEN_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    public static final UUID INVENTORY_MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");
    public static final UUID SECOND_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    public static final UUID CONCERT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID ADULT_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ASSIGNED_EVENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    public static final UUID CONFERENCE_EVENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    public static final UUID CONCERT_GA_ZONE_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    public static final UUID ADULT_GA_ZONE_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    public static final UUID ASSIGNED_SEAT_ZONE_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    public static final UUID CONFERENCE_GA_ZONE_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    public static final UUID SEAT_A1_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000001");
    public static final UUID SEAT_A2_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000002");
    public static final UUID SEAT_B1_ID = UUID.fromString("eeeeeeee-0000-0000-0000-000000000003");

    private final boolean initializePlatform;
    private final boolean seedEnabled;
    private final PlatformInitializationService platformInitializationService;
    private final IMemberRepository memberRepository;
    private final IAdminRepository adminRepository;
    private final ICompanyRepository companyRepository;
    private final IEventRepository eventRepository;
    private final PasswordEncryptionUtils passwordEncryptionUtils;

    public DevSeedDataInitializer(
            @Value("${ticketing.startup.initialize-platform:true}") boolean initializePlatform,
            @Value("${ticketing.seed.enabled:false}") boolean seedEnabled,
            PlatformInitializationService platformInitializationService,
            IMemberRepository memberRepository,
            IAdminRepository adminRepository,
            ICompanyRepository companyRepository,
            IEventRepository eventRepository,
            PasswordEncryptionUtils passwordEncryptionUtils
    ) {
        this.initializePlatform = initializePlatform;
        this.seedEnabled = seedEnabled;
        this.platformInitializationService = platformInitializationService;
        this.memberRepository = memberRepository;
        this.adminRepository = adminRepository;
        this.companyRepository = companyRepository;
        this.eventRepository = eventRepository;
        this.passwordEncryptionUtils = passwordEncryptionUtils;
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
        log.info("Dev seed data ready: users admin/member/owner/manager/teen/inventory-manager/owner2, companies '{}' and '{}'",
                COMPANY_NAME, SECOND_COMPANY_NAME);
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

        if (adminRepository.findByUsername("admin").isEmpty()) {
            adminRepository.save(new Admin(ADMIN_ID, "admin", "admin@ticketing.local"));
        }
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
                    new StaffAppointment(COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.OWNER, Set.of()));
        }
        if (MANAGER_ID.equals(id)) {
            member.addStaffAppointment(COMPANY_NAME,
                    new StaffAppointment(COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.VIEW_REPORTS)));
        }
        if (INVENTORY_MANAGER_ID.equals(id)) {
            member.addStaffAppointment(COMPANY_NAME,
                    new StaffAppointment(COMPANY_NAME, OWNER_ID, StaffAppointment.StaffRole.MANAGER,
                            Set.of(ManagerPermission.MAP_DEFINITION, ManagerPermission.INVENTORY_MGMT,
                                    ManagerPermission.EVENT_LIFECYCLE, ManagerPermission.POLICY_MODIFICATION,
                                    ManagerPermission.VIEW_REPORTS)));
        }
        if (SECOND_OWNER_ID.equals(id)) {
            member.addStaffAppointment(SECOND_COMPANY_NAME,
                    new StaffAppointment(SECOND_COMPANY_NAME, SECOND_OWNER_ID, StaffAppointment.StaffRole.OWNER, Set.of()));
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
        saveGaEventIfMissing(CONFERENCE_EVENT_ID, SECOND_COMPANY_NAME, "Northwind Tech Summit",
                "Second-company event with a max-quantity policy for permission and search QA.",
                EventCategory.CONFERENCE, new MaxQuantityPolicy(4), CONFERENCE_GA_ZONE_ID, "Auditorium",
                new BigDecimal("75.00"), 60);
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
                new MinQuantityPolicy(1),
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
}
