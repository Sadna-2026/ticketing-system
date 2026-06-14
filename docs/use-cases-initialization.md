# System-initialization use cases (V3)

This document updates the **system-initialization use cases** (requirement **I.1**, spec **§2.5**) to match
the V3 implementation: **config-driven boot**, an optional **initial-state-file replay**, and
**all-or-nothing** semantics. It is the use-case companion to the [architecture diagram](architecture.md);
operational/config details live in the [README](../README.md).

## Actors
- **Operator** — whoever launches the application (provides configuration + environment).
- **System** — the Spring Boot application performing startup.
- **External systems (WSEP)** — the remote payment + ticket endpoint (req I.3/I.4).

## Initialization phases

Startup is driven by three Spring `ApplicationRunner`s. The **logical** order of initialization is:

```mermaid
sequenceDiagram
    actor Op as Operator
    participant Sys as System (Spring Boot)
    participant Ext as ExternalSystemsHandshakeRunner
    participant Plat as PlatformInitializationService
    participant Seed as DevSeedDataInitializer
    participant ISR as InitialStateRunner
    participant X as External systems (WSEP)

    Op->>Sys: launch with configuration (env / application.yml / --args)
    Sys->>Ext: run()
    alt ticketing.external.base-url set
        Ext->>X: handshake (action_type=handshake)
        X-->>Ext: "OK"  (else → throw, startup fails)
    else base-url blank (default)
        Ext-->>Sys: skip (no-op)
    end
    Sys->>Seed: run()
    alt ticketing.startup.initialize-platform = true
        Seed->>Plat: initialize()  (one transaction)
        Plat-->>Seed: success / failure(reason) — logged; platform left inactive on failure
    end
    opt ticketing.seed.enabled = true
        Seed->>Seed: seed demo data (runs regardless of the init result)
    end
    Sys->>ISR: run()
    alt ticketing.initial-state.file set
        ISR->>ISR: parse file → execute ops (all-or-nothing; failure → throw, startup fails)
    else unset (default)
        ISR-->>Sys: skip (no-op)
    end
```

### Runner wiring & a known ordering caveat
The runners carry these orders: `ExternalSystemsHandshakeRunner` `@Order(0)`, `InitialStateRunner`
`@Order(100)`, and `DevSeedDataInitializer` **with no `@Order`** (Spring's default *lowest* precedence).
Because lowest precedence sorts *after* `@Order(100)`, the **actual** execution order is
`ExternalSystemsHandshakeRunner → InitialStateRunner → DevSeedDataInitializer`. In the default
configuration this is harmless: with no initial-state file, `InitialStateRunner` is a no-op, so
`DevSeedDataInitializer` still performs platform initialization (UC-INIT-1) and seeding.

> **Caveat (known, tracked separately):** because platform initialization currently lives inside
> `DevSeedDataInitializer` (lowest precedence), a *configured* initial-state file would be replayed
> **before** platform initialization — the "initialize, then replay" intent stated in
> `InitialStateRunner`'s javadoc is not yet enforced by `@Order`. The diagram above shows the intended
> logical order, not the current literal runner order.

---

## UC-INIT-1 — Configure and boot the platform (config-driven)

**Requirement:** I.1, §2.5 · **Primary actor:** Operator · **Implementation:**
`PlatformInitializationService.initialize()` (invoked by `DevSeedDataInitializer`), `StartupConfiguration`.

**Goal:** bring the platform up in a valid, *active* state from configuration alone — no code change to
switch database, queue parameters, external endpoint, or whether a state file is replayed.

**Configuration (all externalized; see README for env-var names):**

| `application.yml` key | Purpose |
|---|---|
| `ticketing.persistence` (`memory`\|`jpa`) | select in-memory or DB-backed repositories (V3-7) |
| `spring.datasource.*` / `spring.jpa.*` | datasource + ORM, env-overridable; H2 default, PostgreSQL config-only (V3-12) |
| `ticketing.queue.threshold` / `flow-rate` | virtual-queue admission defaults (V3-13) |
| `ticketing.external.base-url` (+ `payment.*`) | external systems endpoint; enables the real gateways + startup handshake (V3-16/17/18) |
| `ticketing.startup.initialize-platform` | run platform initialization at boot |
| `ticketing.seed.enabled` | seed demo data at boot |
| `ticketing.initial-state.file` | optional initial-state file to replay (V3-15) |

**Preconditions:** valid `StartupConfiguration` (system-admin username, an e-mail address containing `@`,
a password of length ≥ 6); the clearing (payment) and supply (ticket) services configured.

**Main flow** (`initialize()` runs as a **single `@Transactional`** unit):
1. Validate the startup configuration (admin credentials / e-mail).
2. Verify the **payment gateway** is configured and reachable.
3. Verify the **ticket-supply gateway** is configured and reachable.
4. Register the **system administrator** (idempotent — skipped if already present).
5. Mark the platform **active**.

**Postcondition:** the platform is active with a registered system admin; the selected persistence
backend is in use.

**Alternative / exception flows:** on any failure, `initialize()` returns an
`InitializationResult.failure(reason)` — the reason is recorded and logged and the platform is left
**inactive**. It does **not** throw or stop the JVM; `DevSeedDataInitializer` currently logs the result
and proceeds (it neither aborts startup nor skips seeding on failure).
- *A1 — invalid configuration:* step 1 fails → failure result with the reason; platform not activated.
- *A2 — gateway unreachable:* step 2 or 3 fails → failure "Unable to connect to clearing/supply service".
- *A3 — concurrent admin change:* an `OptimisticLockException` registering the admin → failure asking to retry.
- *A4 — initialization disabled:* `ticketing.startup.initialize-platform=false` → the platform is not
  initialized (used by tests / specialized boots).

---

## UC-INIT-2 — Verify external-systems connectivity at startup

**Requirement:** I.3/I.4 (integrity rule) · **Implementation:** `ExternalSystemsHandshakeRunner` (order 0),
`HttpExternalSystemsClient`.

**Trigger:** application startup, **only when** `ticketing.external.base-url` is set.

**Main flow:** the runner sends a `handshake` (`action_type=handshake`) to the configured endpoint; a body
of `OK` confirms availability and startup proceeds.

**Alternative / exception flows:**
- *A1 — base URL unset (default):* the handshake is **skipped**; the stub gateways stay active (local dev /
  tests unaffected).
- *A2 — handshake fails:* the runner **halts startup** with a clear message (no live connection → do not
  serve).

---

## UC-INIT-3 — Seed demonstration data (optional)

**Implementation:** `DevSeedDataInitializer` · **Trigger:** startup, when `ticketing.seed.enabled=true`.

**Main flow:** in the same runner, after `initialize()` has run (regardless of its result), a fixed set of
demo members, an admin, companies and events is created through the repositories so a freshly-booted
instance is browsable.

**Alternative flow:** *A1 — seeding disabled (default in production-like config):* no demo data is created.

> Seeding is a developer convenience and is independent of the initial-state file (UC-INIT-4); a real
> staff/demo scenario should be expressed as an initial-state file, which runs through legal use cases.

---

## UC-INIT-4 — Replay an initial-state file (optional, all-or-nothing)

**Requirement:** I.1(ii), §2.5 · **Primary actor:** Operator · **Implementation:** `InitialStateRunner`
(order 100) → `InitialStateParser` → `InitialStateExecutor`.

**Goal:** bring the platform into a known state by replaying an **ordered sequence of use-case calls** from
an external, editable file — *not* by inserting objects into the database directly.

**Trigger:** startup, **only when** `ticketing.initial-state.file` points at a readable file (off by default).

**Preconditions:** the platform has been initialized (UC-INIT-1); the file parses (see the
[README "Initial-state file"](../README.md) for the format).

**Main flow:**
1. The runner reads the file and parses it into an ordered `List<InitialStateOperation(name, args)>`.
2. `InitialStateExecutor.execute(...)` runs the operations **in order**, each invoking the matching
   **application-service use case**:

   | Operation (aliases) | Use case |
   |---|---|
   | `guest-registration` (`register`) | register a member (mints a guest token first) |
   | `login` | log a member in (mints a guest token first) |
   | `open-production-company` | open a company owned by the authenticated member |
   | `appoint-manager` (`offer-manager-role`) | offer a MANAGER role appointment (optional permissions) |

3. **Token threading:** a successful `login`/`guest-registration` for username `X` binds the returned
   session token under the symbol `X_token`; later arguments equal to a bound symbol are substituted with
   the real token before the use case is called.

**Postcondition:** every operation has been applied through legal use cases; the platform reflects the
scenario.

**Alternative / exception flows (all-or-nothing):** `execute(...)` is `@Transactional`, so the per-use-case
service transactions join one transaction. The run **aborts at the first failure**, throwing
`InitialStateExecutionException` — the message names the failing operation (index + name); parse errors
throw `InitialStateParseException`, and an unreadable file throws `IllegalStateException`. Any of these
propagates out of the runner and **fails startup**:
- *A1 — file unreadable:* `InitialStateRunner` throws `IllegalStateException` before any operation runs.
- *A2 — malformed file:* parse fails → `InitialStateParseException` (line + snippet).
- *A3 — unknown operation / wrong argument count:* the executor rejects it → halt.
- *A4 — unbound token reference* (a `*_token` argument with no prior successful auth): halt.
- *A5 — a use case is rejected* (e.g. duplicate username, unknown target member, illegal sequence): halt.

> In `jpa` mode a failure rolls back everything applied so far; in the default in-memory mode the
> repositories are not transactional, so earlier writes may remain, but the run still aborts at the first
> failure and startup does not complete.

---

## Traceability
| Use case | Spec | Code |
|---|---|---|
| UC-INIT-1 | I.1, §2.5 | `PlatformInitializationService`, `DevSeedDataInitializer`, `StartupConfiguration`, `application.yml` |
| UC-INIT-2 | I.3/I.4 | `ExternalSystemsHandshakeRunner`, `HttpExternalSystemsClient` |
| UC-INIT-3 | — (dev aid) | `DevSeedDataInitializer` |
| UC-INIT-4 | I.1(ii), §2.5 | `InitialStateRunner`, `InitialStateParser`, `InitialStateExecutor` |
