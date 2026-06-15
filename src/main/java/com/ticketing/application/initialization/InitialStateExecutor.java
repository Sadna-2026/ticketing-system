package com.ticketing.application.initialization;

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

import com.ticketing.application.auth.ISessionTokenService;
import com.ticketing.application.services.CompanyService;
import com.ticketing.application.services.MemberService;
import com.ticketing.domain.member.IMemberRepository;
import com.ticketing.domain.member.ManagerPermission;
import com.ticketing.domain.member.Member;
import com.ticketing.domain.member.StaffAppointment;
import com.ticketing.domain.member.request.LoginRequest;
import com.ticketing.domain.member.request.RegisterRequest;
import com.ticketing.domain.member.response.LoginResponse;
import com.ticketing.domain.member.response.RegisterResponse;

/**
 * Executes the ordered operations parsed from an initial-state file (V3-15) by invoking
 * the matching application-service use cases <strong>in order</strong>, against the real
 * application layer. Only legal operations run, and the whole run is all-or-nothing: any
 * failure (unknown operation, wrong argument count, an unbound token reference, or an
 * underlying use case throwing) aborts initialization with an
 * {@link InitialStateExecutionException} that names the failing operation.
 *
 * <h2>Supported operations</h2>
 * <ul>
 *   <li>{@code guest-registration(username, email, password[, phone, dateOfBirth])}
 *       (alias {@code register}) — mints a fresh guest token and registers a member.</li>
 *   <li>{@code login(username, password)} — mints a fresh guest token and logs the member
 *       in.</li>
 *   <li>{@code open-production-company(token, name[, description])} — opens a company owned
 *       by the authenticated member.</li>
 *   <li>{@code appoint-manager(token, companyName, targetUsername[, permission...])} —
 *       offers a manager role appointment to another member (alias
 *       {@code offer-manager-role}).</li>
 * </ul>
 *
 * <h2>Token threading</h2>
 * When a {@code login} or {@code guest-registration} for username {@code X} succeeds, the
 * returned member session token is bound under the symbol {@code X_token}. Whenever a later
 * operation's argument equals a bound symbol, the real token value is substituted before the
 * use case is called; otherwise the literal argument is passed through. This matches the V3
 * spec example: {@code login(rina, pw)} then {@code open-production-company(rina_token, ...)}.
 *
 * <h2>All-or-nothing</h2>
 * {@link #execute(List)} is {@code @Transactional} (default {@code REQUIRED} propagation), so
 * the per-use-case {@code @Transactional} service calls join the single surrounding
 * transaction. In {@code jpa} mode any failure rolls the whole initialization back; in the
 * default {@code memory} mode the in-memory repositories are not transactional, so partial
 * writes can remain, but the run still aborts at the first failure.
 */
@Service
public class InitialStateExecutor {

    private static final Logger log = LoggerFactory.getLogger(InitialStateExecutor.class);

    /** Suffix appended to a username to form its token symbol (e.g. {@code rina_token}). */
    private static final String TOKEN_SUFFIX = "_token";

    private final MemberService memberService;
    private final CompanyService companyService;
    private final ISessionTokenService sessionTokenService;
    private final IMemberRepository memberRepository;

    public InitialStateExecutor(
            MemberService memberService,
            CompanyService companyService,
            ISessionTokenService sessionTokenService,
            IMemberRepository memberRepository
    ) {
        this.memberService = memberService;
        this.companyService = companyService;
        this.sessionTokenService = sessionTokenService;
        this.memberRepository = memberRepository;
    }

    /**
     * Executes the parsed operations in order. Bound token symbols accumulate across
     * operations within a single call.
     *
     * @param ops the ordered operations from {@link InitialStateParser}; {@code null} or empty
     *            is a no-op
     * @throws InitialStateExecutionException if any operation cannot be executed; the message
     *                                        names the failing operation (index + name)
     */
    @Transactional
    public void execute(List<InitialStateOperation> ops) {
        if (ops == null || ops.isEmpty()) {
            log.info("Initial-state execution: no operations to run");
            return;
        }

        // username + "_token" -> real member session token, bound on a successful auth op.
        Map<String, String> boundTokens = new HashMap<>();

        for (int index = 0; index < ops.size(); index++) {
            InitialStateOperation op = ops.get(index);
            try {
                executeOne(op, boundTokens);
            } catch (RuntimeException ex) {
                // All-or-nothing: enrich every failure with the failing op (index + name) and
                // its cause, then abort the whole run. A nested InitialStateExecutionException
                // (raised by a handler/validation) keeps its detail message as the cause text.
                throw new InitialStateExecutionException(
                        "Initial-state operation #" + index + " '" + op.name() + "' failed: "
                                + ex.getMessage(), ex);
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
            default -> throw new InitialStateExecutionException(
                    "Unknown initial-state operation '" + op.name() + "'");
        }
    }

    // ── Operation handlers ──────────────────────────────────────────

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
        String token = resolveToken(args.get(0), boundTokens);
        String companyName = args.get(1);
        String targetUsername = args.get(2);

        Member target = memberRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new InitialStateExecutionException(
                        "appoint-manager target member '" + targetUsername + "' does not exist"));

        Set<ManagerPermission> permissions = parsePermissions(args.subList(3, args.size()));

        companyService.offerRoleAppointment(
                token,
                companyName,
                target.getId(),
                StaffAppointment.StaffRole.MANAGER,
                permissions);
    }

    // ── Token threading ─────────────────────────────────────────────

    private static void bindToken(Map<String, String> boundTokens, String username, String token) {
        boundTokens.put(username + TOKEN_SUFFIX, token);
    }

    /**
     * Resolves a token argument: if it matches a bound symbol the real token value is
     * returned; an argument that looks like a token symbol ({@code *_token}) but is not bound
     * is an error. Any other literal is passed through unchanged.
     */
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
