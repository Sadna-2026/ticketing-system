package com.ticketing.application.initialization;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ticketing.application.CreateEventRequest;
import com.ticketing.application.ISystemClock;
import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.EventService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.event.CouponDiscount;
import com.ticketing.domain.event.Event;
import com.ticketing.domain.event.EventCategory;
import com.ticketing.domain.event.EventSchedule;
import com.ticketing.domain.event.IEventRepository;
import com.ticketing.domain.event.InventoryZone;
import com.ticketing.domain.event.LayoutCell;
import com.ticketing.domain.event.LockTimerDuration;
import com.ticketing.domain.event.Seat;
import com.ticketing.domain.event.VenueLayout;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.PendingRoleOffer;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.RegisterResponse;

/**
 * Executes the ordered operations parsed from an initial-state file (V3-15) by invoking
 * the matching application-service use cases <strong>in order</strong>, against the real
 * application layer.
 */
@Service
@org.springframework.context.annotation.Lazy
public class InitialStateExecutor {

    private static final Logger log = LoggerFactory.getLogger(InitialStateExecutor.class);

    /** Suffix appended to a username to form its token symbol (e.g. {@code rina_token}). */
    private static final String TOKEN_SUFFIX = "_token";

    private final MemberService memberService;
    private final CompanyService companyService;
    private final EventService eventService;
    private final ISessionTokenService sessionTokenService;
    private final IMemberRepository memberRepository;
    private final IEventRepository eventRepository;
    private final ISystemClock systemClock;

    public InitialStateExecutor(
            MemberService memberService,
            CompanyService companyService,
            EventService eventService,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository,
            IEventRepository eventRepository,
            ISystemClock systemClock
    ) {
        this.memberService = memberService;
        this.companyService = companyService;
        this.eventService = eventService;
        this.sessionTokenService = sessionTokenService;
        this.memberRepository = memberRepository;
        this.eventRepository = eventRepository;
        this.systemClock = systemClock;
    }

    @Transactional
    public void execute(List<InitialStateOperation> ops) {
        if (ops == null || ops.isEmpty()) {
            log.info("Initial-state execution: no operations to run");
            return;
        }

        Map<String, String> boundTokens = new HashMap<>();

        for (int index = 0; index < ops.size(); index++) {
            InitialStateOperation op = ops.get(index);
            try {
                executeOne(op, boundTokens);
            } catch (RuntimeException ex) {
                String file = op.sourceFile() != null ? op.sourceFile() : "unknown";
                String detail = "[EXECUTION ERROR] " + file + ":" + op.line() + ": " + op.name() + ": "
                        + ex.getMessage();
                throw new InitialStateExecutionException(StartupHaltException.framedInitializationMessage(detail), ex);
            }
        }

        log.info("Initial-state execution: {} operation(s) applied successfully", ops.size());
    }

    private void executeOne(InitialStateOperation op, Map<String, String> boundTokens) {
        String name = op.name().toLowerCase(Locale.ROOT);
        List<String> args = op.args();
        switch (name) {
            case "guest-registration", "register" -> register(args, boundTokens);
            case "login" -> login(args, boundTokens);
            case "open-production-company" -> openProductionCompany(args, boundTokens);
            case "appoint-manager", "offer-manager-role" -> appointManager(args, boundTokens);
            case "appoint-owner", "offer-owner-role" -> appointOwner(args, boundTokens);
            case "accept-role-offer", "respond-role-offer" -> acceptRoleOffer(args, boundTokens);
            case "create-event" -> createEvent(args, boundTokens);
            case "publish-event" -> publishEvent(args, boundTokens);
            case "set-event-seating-layout" -> setEventSeatingLayout(args, boundTokens);
            case "set-company-coupon-discount" -> setCompanyCouponDiscount(args, boundTokens);
            case "logout" -> logout(args, boundTokens);
            default -> throw new InitialStateExecutionException(
                    "Unknown initial-state operation '" + op.name() + "'");
        }
    }

    private void register(List<String> args, Map<String, String> boundTokens) {
        requireArity("guest-registration", args, 3, 5);
        String username = args.get(0);
        String email = args.get(1);
        String password = args.get(2);
        String phone = args.size() > 3 ? args.get(3) : null;
        String dateOfBirth = args.size() > 4 ? args.get(4) : null;

        RegisterRequest request = dateOfBirth == null
                ? new RegisterRequest(username, email, password, phone, (java.time.LocalDate) null)
                : new RegisterRequest(username, email, password, phone, dateOfBirth);

        String guestToken = sessionTokenService.generateGuestToken();
        RegisterResponse response = memberService.register(request, guestToken);
        if (!response.success()) {
            throw new InitialStateExecutionException(
                    "guest-registration for '" + username + "' was rejected: " + response.message());
        }
        bindToken(boundTokens, username, response.sessionToken());
    }

    private void login(List<String> args, Map<String, String> boundTokens) {
        requireArity("login", args, 2, 2);
        String username = args.get(0);
        String password = args.get(1);

        String guestToken = sessionTokenService.generateGuestToken();
        LoginResponse response = memberService.login(new LoginRequest(username, password), guestToken);
        if (!response.success()) {
            throw new InitialStateExecutionException(
                    "login for '" + username + "' was rejected: " + response.message());
        }
        bindToken(boundTokens, username, response.sessionToken());
    }

    private void openProductionCompany(List<String> args, Map<String, String> boundTokens) {
        requireArity("open-production-company", args, 2, 3);
        String token = resolveToken(args.get(0), boundTokens);
        String name = args.get(1);
        String description = args.size() > 2 ? args.get(2) : null;
        companyService.openProductionCompany(token, name, description);
    }

    private void appointManager(List<String> args, Map<String, String> boundTokens) {
        requireArity("appoint-manager", args, 3, Integer.MAX_VALUE);
        offerRole(args, boundTokens, StaffAppointment.StaffRole.MANAGER);
    }

    private void appointOwner(List<String> args, Map<String, String> boundTokens) {
        requireArity("appoint-owner", args, 3, 3);
        offerRole(args, boundTokens, StaffAppointment.StaffRole.OWNER);
    }

    private void offerRole(List<String> args, Map<String, String> boundTokens, StaffAppointment.StaffRole role) {
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        String targetUsername = args.get(2);

        Member target = memberRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new InitialStateExecutionException(
                        "role appointment target member '" + targetUsername + "' does not exist"));

        Set<ManagerPermission> permissions = role == StaffAppointment.StaffRole.MANAGER
                ? parsePermissions(args.subList(3, args.size()))
                : Set.of();

        companyService.offerRoleAppointment(token, companyName, target.getId(), role, permissions);
    }

    private void acceptRoleOffer(List<String> args, Map<String, String> boundTokens) {
        requireArity("accept-role-offer", args, 3, 3);
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        StaffAppointment.StaffRole role = parseRole(args.get(2));

        PendingRoleOffer offer = memberService.listPendingRoleOffers(token).stream()
                .filter(o -> companyName.equals(o.getCompanyName()) && role == o.getRole())
                .findFirst()
                .orElseThrow(() -> new InitialStateExecutionException(
                        "no pending " + role + " offer for company '" + companyName + "'"));

        companyService.respondToRoleAppointment(token, offer.getOfferId(), true);
    }

    private void createEvent(List<String> args, Map<String, String> boundTokens) {
        requireArity("create-event", args, 12, 12);
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        String eventName = args.get(2);
        String description = args.get(3);
        EventCategory category = parseCategory(args.get(4));
        String standingZoneName = args.get(5);
        int standingCapacity = parsePositiveInt(args.get(6), "standing capacity");
        BigDecimal standingPrice = parsePrice(args.get(7));
        String seatingZoneName = args.get(8);
        int seatingRows = parsePositiveInt(args.get(9), "seating rows");
        int seatingCols = parsePositiveInt(args.get(10), "seating cols");
        BigDecimal seatingPrice = parsePrice(args.get(11));

        if (seatingRows > 26) {
            throw new InitialStateExecutionException(
                    "seating rows must be at most 26 (A-Z), got " + seatingRows);
        }

        List<CreateEventRequest.SeatSpec> seats = new ArrayList<>(seatingRows * seatingCols);
        for (int r = 0; r < seatingRows; r++) {
            String row = String.valueOf((char) ('A' + r));
            for (int c = 1; c <= seatingCols; c++) {
                seats.add(new CreateEventRequest.SeatSpec(row, String.valueOf(c)));
            }
        }

        Instant start = systemClock.now().plus(Duration.ofDays(30));
        CreateEventRequest request = new CreateEventRequest(
                companyName,
                eventName,
                description,
                category,
                new EventSchedule(start, start.plus(Duration.ofHours(2)), start.minus(Duration.ofHours(1))),
                new LockTimerDuration(Duration.ofMinutes(10)),
                List.of(
                        new CreateEventRequest.GAZoneSpec(standingZoneName, standingPrice, standingCapacity),
                        new CreateEventRequest.AssignedZoneSpec(seatingZoneName, seatingPrice, seats)),
                Map.of(standingZoneName, standingZoneName, seatingZoneName, seatingZoneName));

        eventService.createEvent(token, request);
    }

    private void publishEvent(List<String> args, Map<String, String> boundTokens) {
        requireArity("publish-event", args, 3, 3);
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        String eventName = args.get(2);

        Event event = findEvent(companyName, eventName);
        eventService.publishEvent(token, event.getId());
    }

    private void setEventSeatingLayout(List<String> args, Map<String, String> boundTokens) {
        requireArity("set-event-seating-layout", args, 6, 6);
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        String eventName = args.get(2);
        String seatingZoneName = args.get(3);
        int gridRows = parsePositiveInt(args.get(4), "grid rows");
        int gridCols = parsePositiveInt(args.get(5), "grid cols");

        Event event = findEvent(companyName, eventName);
        InventoryZone zone = event.getZones().stream()
                .filter(z -> seatingZoneName.equals(z.getName()))
                .findFirst()
                .orElseThrow(() -> new InitialStateExecutionException(
                        "seating zone '" + seatingZoneName + "' not found on event '" + eventName + "'"));

        List<Seat> seats = new ArrayList<>(zone.getSeats());
        seats.sort(Comparator
                .comparing(Seat::getRow)
                .thenComparing(s -> Integer.parseInt(s.getSeatNumber())));

        if (seats.size() != gridRows * gridCols) {
            throw new InitialStateExecutionException(
                    "zone '" + seatingZoneName + "' has " + seats.size()
                            + " seats but layout is " + gridRows + "x" + gridCols);
        }

        List<LayoutCell> cells = new ArrayList<>(seats.size());
        int seatIndex = 0;
        for (int r = 0; r < gridRows; r++) {
            for (int c = 0; c < gridCols; c++) {
                Seat seat = seats.get(seatIndex++);
                cells.add(LayoutCell.seat(r, c, zone.getId(), seat.getId()));
            }
        }

        eventService.setEventLayout(token, event.getId(), new VenueLayout(gridRows, gridCols, cells));
    }

    private void setCompanyCouponDiscount(List<String> args, Map<String, String> boundTokens) {
        requireArity("set-company-coupon-discount", args, 4, 5);
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        BigDecimal percent = parsePrice(args.get(2));
        String couponCode = args.get(3);
        int expiryDays = args.size() > 4 ? parsePositiveInt(args.get(4), "expiry days") : 365;

        Instant expiresAt = systemClock.now().plus(Duration.ofDays(expiryDays));
        companyService.setCompanyDiscountPolicy(
                token, companyName, new CouponDiscount(percent, couponCode, expiresAt));
    }

    private void logout(List<String> args, Map<String, String> boundTokens) {
        requireArity("logout", args, 1, 1);
        String token = resolveToken(args.get(0), boundTokens);
        memberService.logout(token);
        boundTokens.entrySet().removeIf(e -> token.equals(e.getValue()));
    }

    private Event findEvent(String companyName, String eventName) {
        return eventRepository.findByCompanyName(companyName).stream()
                .filter(e -> eventName.equals(e.getName()))
                .findFirst()
                .orElseThrow(() -> new InitialStateExecutionException(
                        "event '" + eventName + "' not found for company '" + companyName + "'"));
    }

    private static void bindToken(Map<String, String> boundTokens, String username, String token) {
        boundTokens.put(username + TOKEN_SUFFIX, token);
    }

    private static String resolveToken(String arg, Map<String, String> boundTokens) {
        if (boundTokens.containsKey(arg)) {
            return boundTokens.get(arg);
        }
        if (arg.endsWith(TOKEN_SUFFIX)) {
            throw new InitialStateExecutionException(
                    "unbound token reference '" + arg + "' (no prior successful login/guest-registration "
                            + "bound this symbol)");
        }
        return arg;
    }

    private static Set<ManagerPermission> parsePermissions(List<String> raw) {
        Set<ManagerPermission> permissions = new HashSet<>();
        for (String value : raw) {
            try {
                permissions.add(ManagerPermission.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                throw new InitialStateExecutionException("unknown manager permission '" + value + "'");
            }
        }
        return permissions;
    }

    private static StaffAppointment.StaffRole parseRole(String raw) {
        try {
            return StaffAppointment.StaffRole.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InitialStateExecutionException("unknown staff role '" + raw + "'");
        }
    }

    private static EventCategory parseCategory(String raw) {
        try {
            return EventCategory.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InitialStateExecutionException("unknown event category '" + raw + "'");
        }
    }

    private static BigDecimal parsePrice(String raw) {
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            throw new InitialStateExecutionException("invalid decimal value '" + raw + "'");
        }
    }

    private static int parsePositiveInt(String raw, String label) {
        try {
            int value = Integer.parseInt(raw.trim());
            if (value <= 0) {
                throw new InitialStateExecutionException(label + " must be positive, got " + value);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new InitialStateExecutionException("invalid integer for " + label + ": '" + raw + "'");
        }
    }

    private static void requireArity(String op, List<String> args, int min, int max) {
        int n = args.size();
        if (n < min || n > max) {
            String expected = max == Integer.MAX_VALUE
                    ? "at least " + min
                    : (min == max ? String.valueOf(min) : min + ".." + max);
            throw new InitialStateExecutionException(
                    op + " expects " + expected + " argument(s) but got " + n);
        }
    }
}
