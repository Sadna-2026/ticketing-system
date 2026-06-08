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
    