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

### Enabling it

Execution is **off by default**. Set `ticketing.initial-state.file` to a readable path and the
`InitialStateRunner` (an `ApplicationRunner`) will load, parse and execute it on startup:

```
TICKETING_INITIAL_STATE_FILE=/path/to/initial-state.txt
```

or in `application.yml` / on the command line:

```
--ticketing.initial-state.file=/path/to/initial-state.txt
```

When the property is unset (the default) the runner is a no-op: normal startup, the existing
tests and `DevSeedDataInitializer` are unaffected.
    