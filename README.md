# Ticketing System - Version 1

Domain-Driven Design (DDD) implementation of the Event Management and Ticketing Platform.

## Architecture
- **Domain:** Core business logic and interfaces ("White Model").
- **Application:** Use cases and orchestration (Acceptance test entry points).
- **Infrastructure:** Persistence and security.
- **External:** Mock gateways for payments and supply.

## Documentation
- [UI Wireframes](docs/wireframes/README.md) — mid-fidelity B&W layouts for V1 screens.

## Database configuration

The database connection is **fully externalized** (V3-12) — nothing is hard-coded. Every
setting is read from an environment variable, with **H2 in-memory defaults** for local dev,
so switching between H2 and PostgreSQL is a **config-only** change (the PostgreSQL driver is
already on the classpath — no rebuild needed).

| Env var | Default (H2, local dev) | Purpose |
|---------|-------------------------|---------|
| `DB_URL` | `jdbc:h2:mem:ticketing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false` | JDBC URL |
| `DB_DRIVER` | `org.h2.Driver` | JDBC driver class |
| `DB_USERNAME` | `sa` | DB user |
| `DB_PASSWORD` | *(empty)* | DB password |
| `DB_DIALECT` | `org.hibernate.dialect.H2Dialect` | Hibernate dialect |
| `DB_DDL_AUTO` | `none` | Hibernate schema management |
| `DB_SHOW_SQL` | `false` | log SQL statements |
| `H2_CONSOLE_ENABLED` | `true` | H2 web console at `/h2-console` |

### Switch to PostgreSQL (config only)
Set these in the environment before starting — no code change, no rebuild:

```
DB_URL=jdbc:postgresql://<host>:5432/ticketing
DB_DRIVER=org.postgresql.Driver
DB_USERNAME=<user>
DB_PASSWORD=<secret>
DB_DIALECT=org.hibernate.dialect.PostgreSQLDialect
DB_DDL_AUTO=validate
H2_CONSOLE_ENABLED=false
```

> **Never commit real credentials.** Supply remote-DB credentials via the environment /
> deployment secrets, not via `application.yml`.

## Startup parameters

Runtime startup parameters are externalized to config (V3-13) — not hard-coded. The
virtual-queue admission settings (used as the default when a queue is created) are:

| Env var | Default | `application.yml` key | Purpose |
|---------|---------|-----------------------|---------|
| `TICKETING_QUEUE_THRESHOLD` | `100` | `ticketing.queue.threshold` | how many users may hold reservations concurrently before the virtual queue kicks in |
| `TICKETING_QUEUE_FLOW_RATE` | `10` | `ticketing.queue.flow-rate` | how many waiting users are admitted per batch |

`OrderService.createQueue(token, eventId)` uses these defaults; the explicit overload
`createQueue(token, eventId, threshold, flowRate)` still allows a per-event override.

## External systems

The platform connects to the real **external payment + ticket systems** (WSEP, reqs I.3/I.4) via
a single HTTP `POST` endpoint whose behaviour is selected by an `action_type` form parameter; the
`handshake` action returns the literal `OK`. The HTTP client is
`com.ticketing.infrastructure.gateway.HttpExternalSystemsClient` (implements `IExternalSystemsClient`).

The **base URL is config-driven** and, when set, the `ExternalSystemsHandshakeRunner` performs a
**startup handshake** to verify availability (integrity rule: a live connection must exist after
init) — a failed handshake halts startup. When the base URL is **unset (default)** the handshake is
skipped, so local dev, the existing stub gateways and the test suite are unaffected.

| Env var | Default | `application.yml` key | Purpose |
|---------|---------|-----------------------|---------|
| `TICKETING_EXTERNAL_BASE_URL` | *(empty → handshake skipped)* | `ticketing.external.base-url` | base URL of the WSEP external endpoint |
| `TICKETING_EXTERNAL_CONNECT_TIMEOUT_MS` | `5000` | `ticketing.external.connect-timeout-ms` | TCP connect timeout (ms) |
| `TICKETING_EXTERNAL_READ_TIMEOUT_MS` | `5000` | `ticketing.external.read-timeout-ms` | response read timeout (ms) |

```
TICKETING_EXTERNAL_BASE_URL=https://<wsep-host>/
```

### Payment gateway (V3-17)

When `base-url` is set, `HttpPaymentGateway` becomes the active `IPaymentGateway` and **replaces**
`StubPaymentGateway` — the two carry mutually exclusive conditions on `ticketing.external.base-url`,
so the always-approving stub never runs alongside the real gateway (it can't mask a real decline).
With `base-url` blank (the default) the stub stays active for local dev and tests.

`charge` POSTs `action_type=pay`; a transaction id in `[10000, 100000]` is an approval, `-1` (or any
other body, or an unreachable endpoint) is a decline. `refund` POSTs `action_type=refund` and treats
`1` as success, `-1`/anything else as failure.

The WSEP `pay` action also needs card details + a currency, but the purchase flow does not capture
cardholder input (the `IPaymentGateway` contract is amount + order metadata only). Those fields are
therefore **config-driven** with sandbox-friendly defaults; capturing real cardholder input is a
future ticket.

| Env var | Default | `application.yml` key |
|---------|---------|-----------------------|
| `TICKETING_EXTERNAL_PAYMENT_CURRENCY` | `USD` | `ticketing.external.payment.currency` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_NUMBER` | *(sandbox placeholder)* | `ticketing.external.payment.card-number` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_MONTH` | `12` | `ticketing.external.payment.card-month` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_YEAR` | `2030` | `ticketing.external.payment.card-year` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_HOLDER` | `Ticketing System` | `ticketing.external.payment.card-holder` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_CVV` | `123` | `ticketing.external.payment.card-cvv` |
| `TICKETING_EXTERNAL_PAYMENT_CARD_ID` | `000000000` | `ticketing.external.payment.card-id` |

### Ticket-supply gateway (V3-18)

When `base-url` is set, `HttpTicketSupplyGateway` becomes the active `ITicketSupplyGateway` and
**replaces** `StubTicketSupplyGateway` — the two carry mutually exclusive conditions on
`ticketing.external.base-url`, so the always-succeeding stub never sits alongside the real gateway in
`OrderService`'s supply-gateway failover (where it could mask a real supply failure). With `base-url`
blank (the default) the stub stays active for local dev and tests.

`issueTickets` POSTs one `action_type=issue_ticket` per ticket (the purchase flow already expands a GA
quantity into one request per ticket), with `customer_id`, `event_id` and `zone`: general admission
adds `quantity=1`, assigned seating adds `is_seating=true` + the seat as a JSON `seats` array. The
endpoint returns the ticket code on success, or `-1` (or an empty/unexpected body, or an unreachable
endpoint) on failure; on a mid-batch failure the already-issued codes are returned with the failed
result so `OrderService` can cancel them. `cancelTickets` POSTs `action_type=cancel_ticket` per code
and treats `1` as success, `-1`/anything else as failure.

The `zone` and seat ids come from the order (`OrderItem.zoneId`/`seatId`), so the ticket gateway needs
no extra configuration.

> V3-16 delivers the client and the startup handshake. Tests exercise the gateways against a stubbed
> endpoint (`MockRestServiceServer`), never the live system.

## Initial-state file

The platform can optionally be started from an **initial-state file** (V3-14): a plain-text
sequence of use-case calls that bring the system into a known state. The file format and its
parser (`com.ticketing.application.initialization.InitialStateParser`) are independent of
execution — parsing turns the text into an **ordered** list of
`InitialStateOperation(name, args)` values; running them against the application layer is a
separate concern.

### Format

One operation per statement, terminated by `;`:

```
operation-name(arg1, arg2, ...);
```

Grammar (one line):
`file := (operation | comment | blank-line)*` where
`operation := name '(' [ arg (',' arg)* ] ')' ';'` and `arg := "quoted-string" | bare-token`.

Rules:
- **Order is preserved** — operations are returned in the order they appear.
- **Whitespace and newlines** between tokens are insignificant; a single operation may span
  multiple lines.
- **Comments** — a line starting with `#` or `//` (after optional leading whitespace) is
  skipped, as are blank lines.
- **Quoted arguments** — wrap an argument in double quotes so it may contain commas, spaces,
  semicolons or `//`; the surrounding quotes are stripped. Inside a quoted string, `\"` and
  `\\` are escapes. **Unquoted (bare)** arguments are trimmed of surrounding whitespace.
- **Zero-arg calls** are written `op()` and yield an empty argument list.
- **Malformed input** (unterminated call, missing parenthesis, unbalanced quote) throws
  `InitialStateParseException` with the offending line number and a snippet. Empty,
  whitespace-only or comment-only content yields an empty list.

### Example

```
# Bring the platform up with two members, a company and a manager offer.
guest-registration(rina, rina@example.com, secret1, 050-000-0000, 1990-01-01);
guest-registration(dana, dana@example.com, secret2);
login(rina, secret1);
open-production-company(
    rina_token,
    "Demo Co",
    "A demo company, with a comma in its description"
);
# offer dana a manager role at Demo Co (rina is the owner)
appoint-manager(rina_token, "Demo Co", dana);
```

### Execution (V3-15)

When enabled, the parsed operations are executed **in order** against the application layer
by `com.ticketing.application.initialization.InitialStateExecutor`, threading session tokens
between operations. Execution is **all-or-nothing**: the first failure (unknown operation,
wrong argument count, an unbound token reference, or an underlying use case throwing) aborts
the whole run with an `InitialStateExecutionException` that names the failing operation, and —
in `jpa` mode — rolls back everything applied so far (the executor's `execute` is
`@Transactional`, so the per-use-case service transactions join one transaction).

**Token threading.** A successful `login(X, ...)` or `guest-registration(X, ...)` binds the
member's session token under the symbol `X_token`. When a later operation's argument equals a
bound symbol, the real token is substituted; any other argument is passed through literally.
So `login(rina, pw)` makes `rina_token` usable by `open-production-company(rina_token, ...)`.

**Supported operations.** The executor maps each operation name to a real use case:

| Operation (aliases) | Arguments | Use case |
| --- | --- | --- |
| `guest-registration` (`register`) | `username, email, password[, phone, dateOfBirth]` | `MemberService.register` (mints a guest token first) → binds `username_token` |
| `login` | `username, password` | `MemberService.login` (mints a guest token first) → binds `username_token` |
| `open-production-company` | `token, name[, description]` | `CompanyService.openProductionCompany` |
| `appoint-manager` (`offer-manager-role`) | `token, companyName, targetUsername[, permission...]` | `CompanyService.offerRoleAppointment` (MANAGER role; target resolved by username; optional `ManagerPermission` names) |
| `appoint-owner` (`offer-owner-role`) | `token, companyName, targetUsername` | `CompanyService.offerRoleAppointment` (OWNER role) |
| `accept-role-offer` (`respond-role-offer`) | `token, companyName, ROLE` | `CompanyService.respondToRoleAppointment` (accepts the pending offer matching company + role) |
| `create-event` | `token, company, eventName, description, CATEGORY, standingZone, standingCap, standingPrice, seatingZone, seatRows, seatCols, seatingPrice` | `EventService.createEvent` (GA standing zone + assigned seating grid) |
| `set-event-seating-layout` | `token, company, eventName, seatingZone, gridRows, gridCols` | `EventService.setEventLayout` (10×10 seat grid for the assigned zone) |
| `set-company-coupon-discount` | `token, company, percent, code[, expiryDays]` | `CompanyService.setCompanyDiscountPolicy` (`CouponDiscount`) |
| `logout` | `token` | `MemberService.logout` |

### Enabling it

Choose **one** data bootstrap via `ticketing.bootstrap.dataset`:

| Value | Effect |
| --- | --- |
| `dev-seed` | In-code QA dataset (`DevSeedDataInitializer`) — default when `ticketing.seed.enabled=true` |
| `initial-state-file` | Replay an external script via `InitialStateExecutor` |
| `none` | Empty application data (platform init only) |

**Staff demo scenario (#415)** — empty DB + file bootstrap:

```
--ticketing.bootstrap.dataset=initial-state-file
--ticketing.seed.enabled=false
--ticketing.initial-state.file=classpath:initial-state/staff-demo-v3.txt
```

For a filesystem path, set `ticketing.initial-state.file` to a readable path. Classpath resources
use the `classpath:` prefix. When `bootstrap.dataset` is unset, legacy rules apply:
`seed.enabled=true` selects dev seed; otherwise a configured `initial-state.file` selects file bootstrap.

`DataBootstrapRunner` initializes the platform, then loads, parses and executes the file
(all-or-nothing).

See [docs/v3-initial-state.md](docs/v3-initial-state.md) for the staff demo scenario, format
details, and meeting setup instructions.

## Testing

The test suite runs under a **dedicated, isolated configuration** (V3-25) so it never touches the
real external systems or a real / working database. The `test` Spring profile
(`src/test/resources/application-test.yml`) pins the datasource to a throwaway in-memory H2
(`ticketing-test`) and leaves the external base-url blank (so the stub gateways are used and the
startup handshake is skipped).

The profile is activated for **every** test run by the Surefire plugin (`pom.xml`), so
`mvn test` / `mvn verify` — and therefore CI — use it automatically; no per-test annotation is
needed. As defence-in-depth, Surefire also pins `spring.datasource.url` and
`ticketing.external.base-url` as JVM **system properties**, which outrank OS environment variables
(`DB_URL` / `TICKETING_EXTERNAL_BASE_URL`), so isolation holds even when those are exported locally.
A test that legitimately needs a different datasource (e.g. the DB connection-recovery test) overrides
`spring.datasource.url` via `@DynamicPropertySource`, which outranks both.
