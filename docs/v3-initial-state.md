# V3 initial-state file bootstrap

This document describes how to initialize the ticketing system from an external **initial-state file** for V3 checking meetings (issue **V3-INIT-STATE** / #415).

## Overview

At startup the system can load a plain-text script that replays **legal application use cases** in order. The script is **not** hardcoded in Java — staff can copy, edit, and run it before or during a meeting.

| Component | Role |
| --- | --- |
| `InitialStateParser` | Reads the file into ordered `InitialStateOperation` values |
| `InitialStateExecutor` | Executes operations via `MemberService`, `CompanyService`, `EventService` |
| `DataBootstrapRunner` | Platform init, then dev seed or initial-state file replay |

Execution is **all-or-nothing**: the first illegal step aborts with `InitialStateExecutionException` naming the operation index and name. In **JPA** mode the executor transaction rolls back so the database stays clean.

## Starting from an empty database

1. Use a fresh database (or H2 in-memory with no prior data).
2. Disable the dev seed dataset.
3. Point bootstrap at your script.

```bash
mvn spring-boot:run \
  "-Dspring-boot.run.arguments=--ticketing.bootstrap.dataset=initial-state-file --ticketing.seed.enabled=false --ticketing.initial-state.file=classpath:initial-state/staff-demo-v3.txt"
```

For a file on disk (editable during the meeting):

```bash
--ticketing.initial-state.file=/path/to/my-scenario.txt
```

Classpath resources use the `classpath:` prefix, e.g. `classpath:initial-state/staff-demo-v3.txt`.

### Configuration reference

| Property | Values | Meaning |
| --- | --- | --- |
| `ticketing.bootstrap.dataset` | `dev-seed`, `initial-state-file`, `none` | Explicit data source (overrides legacy rules) |
| `ticketing.seed.enabled` | `true` / `false` | Legacy: `true` selects dev seed when dataset unset |
| `ticketing.initial-state.file` | path or `classpath:...` | Script to replay when using file bootstrap |

Legacy resolution when `bootstrap.dataset` is unset:

- `seed.enabled=true` → dev seed
- else if `initial-state.file` set → file bootstrap
- else → none (empty application data)

## File format

**Format name:** initial-state DSL (plain text).

One operation per statement, terminated by `;`:

```
operation-name(arg1, arg2, ...);
```

- **Comments:** lines starting with `#` or `//`
- **Arguments:** bare tokens or double-quoted strings (commas allowed inside quotes)
- **Token threading:** after `login(u1, secret1)` or `guest-registration(u1, ...)`, the symbol `u1_token` is bound to that member's session token for later operations

See [README.md](../README.md#initial-state-file) for the full grammar and operation table.

## Staff demo scenario (V3-INIT-STATE)

Repository file: [`src/main/resources/initial-state/staff-demo-v3.txt`](../src/main/resources/initial-state/staff-demo-v3.txt)

The script performs:

1. Register `u1`–`u4`
2. `u1` logs in and opens production company `p1`
3. `u1` appoints `u2` as owner; `u2` logs in and confirms
4. `u2` appoints `u3` as manager with **MAP_DEFINITION** only (venue layout, no policy management); `u3` logs in and confirms
5. `u2` creates event `e1`: Standing GA 30 @ 50, Seating 10×10 (100 seats) @ 100, with visual 10×10 layout
6. `u2` sets company coupon **20%**, code `sale123`
7. All users logout (`u1`–`u4`, including registration sessions)

Default passwords in the file: `secret1` … `secret4` (minimum 6 characters).

## Final V3 checking scenario (V3-INIT-STATE-FINAL / #561)

Repository file: [`src/main/resources/initial-state/final-v3-scenario.txt`](../src/main/resources/initial-state/final-v3-scenario.txt)

This is the scenario the **final** V3 checking meeting uses. It is separate from the staff demo
above (which remains as an older/demo scenario). It is also the **default** value of
`ticketing.initial-state.file`, so selecting `bootstrap.dataset=initial-state-file` without an
explicit file replays it.

Precondition: the registered system administrator (**Admin**) already exists from platform
initialization; the operational database is otherwise empty.

The script performs:

1. Register `u1`–`u4` (User3 has date of birth `2000-01-01`, so it is **over 18**)
2. `u1` logs in and opens company `C1`
3. `u1` creates event `E1`: **10** standing tickets + **10** assigned seats (2×5 grid), then
   **publishes** it
4. `u1` creates event `E2`: **10** standing tickets + **10** assigned seats (2×5 grid), where
   **every ticket costs 10 dollars** (standing price = seating price = 10), then **publishes** it
5. `u1` logs out; the `u2`–`u4` registration sessions are also logged out so no regular user
   remains logged in

Both events are published because a company's event listing (and the buyer-facing browse page)
only shows `PUBLISHED`/`SOLD_OUT` events — a `DRAFT` event would not appear during UI verification.

Run it:

```bash
mvn spring-boot:run \
  "-Dspring-boot.run.arguments=--ticketing.bootstrap.dataset=initial-state-file --ticketing.seed.enabled=false --ticketing.initial-state.file=classpath:initial-state/final-v3-scenario.txt"
```

**E1 price (open clarification):** the staff message gives an explicit price only for E2. The domain
requires a price per zone, so E1's tickets default to **10 dollars** in the file. Edit the two `10`
price columns on the E1 `create-event` line if staff decides E1 should use a different price.

Tests: `FinalV3ScenarioTest` covers registration, User3 over 18, login, `C1`, `E1`/`E2` inventory,
every-E2-ticket-is-10-dollars, logout, invalid-file failure, and the configured-file replay. The
Admin precondition is covered by `PlatformInitializationServiceTest`.

## Switching between dev seed and initial-state file

| Meeting need | Configuration |
| --- | --- |
| Local QA / UI testing | `ticketing.bootstrap.dataset=dev-seed` or `ticketing.seed.enabled=true` (default in `application.yml`) |
| V3 staff demo | `ticketing.bootstrap.dataset=initial-state-file` + `ticketing.seed.enabled=false` + path to script |
| Empty system | `ticketing.bootstrap.dataset=none` + `ticketing.seed.enabled=false` |

## Failure behavior

When a step fails, startup aborts and the error message includes:

- Operation index (0-based), e.g. `#7`
- Operation name, e.g. `'accept-role-offer'`
- Underlying cause (validation, permissions, missing token, etc.)

Example:

```
Initial-state operation #3 'open-production-company' failed: unbound token reference 'u1_token' ...
```

In JPA mode, no partial data from that run should remain committed.

## Tests

| Test class | What it verifies |
| --- | --- |
| `InitialStateExecutorTest` | Staff scenario end-to-end, token threading, failure messages |
| `StaffInitialStateScenarioTest` | Acceptance criteria (permissions, logout, ordering) |
| `InitialStateRollbackJpaTest` | Failed run leaves JPA database unchanged |
| `DataBootstrapRunnerTest` | Dataset resolution |
| `InitialStateParserTest` | File format parsing |
