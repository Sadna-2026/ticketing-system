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
# Bring the platform up with one company and a member.
login(rina, pw);
open-production-company(
    rina_token,
    "Demo Co",
    "A demo company, with a comma in its description"
);
refresh();   // zero-arg call
```
    