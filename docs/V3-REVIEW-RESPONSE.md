# V3 Review — Response & Verification Guide

This document answers every V3 review comment with three things each:

- **Where it lives** — the production classes that implement it.
- **How it's tested** — the test classes that prove it.
- **How to run / verify** — the exact command or UI step to reproduce it.

Stack: Java 21, Spring Boot 3.3.0, Vaadin 24.6.1, JPA/Hibernate (H2 dev / PostgreSQL prod), JaCoCo. Build with Maven.

Status legend: ✅ done & tested · ⚠️ partial / open for V4 · 🛠️ process item (not code).

---

## 0. Build, run & test — the commands you'll reuse

```bash
# Full build + all tests + JaCoCo coverage gate (75% line/instruction/branch)
mvn clean verify

# Tests only
mvn test

# Run one test class
mvn test -Dtest=ConfigurationValidatorTest

# Run a group by package
mvn test -Dtest='com.ticketing.infrastructure.persistence.*'

# Run the app (in-memory repos, dev seed, H2) — default profile
mvn spring-boot:run
# → http://localhost:8080   (admin / admin123 by default)

# Run against the JPA backend
TICKETING_PERSISTENCE=jpa mvn spring-boot:run

# Coverage report after a run
# target/site/jacoco/index.html
```

The whole test suite runs under an isolated `test` profile (`src/test/resources/application-test.yml`): throwaway H2, blank external base-url (stub gateways, no startup handshake). Surefire pins these as JVM system properties so isolation holds even if `DB_URL` / `TICKETING_EXTERNAL_BASE_URL` are exported. See `pom.xml` Surefire config.

---

## 0a. Which run command loads which users/data (read this if "u1" vs "manager" confuses you)

Two independent knobs decide what you see at login. They do **different** things — mixing them up is exactly why you sometimes get `u1` and sometimes `manager`:

- **`ticketing.bootstrap.dataset`** (env `TICKETING_BOOTSTRAP_DATASET`) decides **which demo data is loaded**: `dev-seed`, `initial-state-file`, or `none`.
- **`ticketing.persistence`** (env `TICKETING_PERSISTENCE`) decides only **where data is stored** (`memory` vs `jpa`). It does **not** change which users exist.

Resolution rule (`DataBootstrapRunner.resolveDataset`): if `TICKETING_BOOTSTRAP_DATASET` is set it wins; otherwise, because `seed.enabled: true` is the default in `application.yml`, an unset dataset falls back to **dev-seed**. So a bare `mvn spring-boot:run` = dev-seed.

**The system admin (`admin` / `admin123`) is created in every mode** by `PlatformInitializationService` from `ticketing.admin.*`, independent of the dataset. The dataset only adds the *rest* of the cast on top.

### Command → data table

| Command | Dataset loaded | Who you can log in as |
|---------|----------------|-----------------------|
| `mvn spring-boot:run` *(default)* | **dev-seed** | `admin`, `manager`, `owner`, `buyer1`–`buyer3`, `member`, `teen`, … (full cast below) |
| `TICKETING_PERSISTENCE=jpa mvn spring-boot:run` | **dev-seed** (same cast, stored in JPA/DB) | same as above |
| `TICKETING_BOOTSTRAP_DATASET=initial-state-file \`<br>`TICKETING_SEED_ENABLED=false \`<br>`TICKETING_INITIAL_STATE_FILE=classpath:initial-state/staff-demo-v3.txt \`<br>`mvn spring-boot:run` | **initial-state file** | `admin` + `u1`, `u2`, `u3`, `u4` |
| `TICKETING_BOOTSTRAP_DATASET=none TICKETING_SEED_ENABLED=false mvn spring-boot:run` | **none** | just `admin` (empty operational DB) |

> **So:** `manager`/`owner`/`buyer1` come from **dev-seed** (the default). `u1`–`u4` come **only** from the **initial-state file**. They never appear together.

### Dev-seed cast (`DevSeedDataInitializer`) — the default run

Members (username / password → role):

- `admin` / `admin123` — system admin · `admin2` / `admin2123` — second admin
- `owner` / `owner123` — owner of **Demo Productions** · `owner2` / `owner2123` — owner of **Northwind Events**
- `manager` / `manager123` — manager (Northwind, `VIEW_REPORTS`) · `inventory` / `inventory123` — inventory manager
- `revoked-manager` / `revoked123` — a manager whose role was revoked (QA case)
- `member` / `member123`, `buyer1` / `buyer1123`, `buyer2` / `buyer2123`, `buyer3` / `buyer3123` — plain buyers
- `teen` / `teen123` — under-age buyer (for age-restriction tests) · `suspended` / `suspended123` — a suspended member

Companies: **Demo Productions**, **Northwind Events**, **Dormant Productions** (suspended), **Admin Closure Company** — plus pre-seeded events, zones and purchase history for QA.

### Initial-state cast (`src/main/resources/initial-state/staff-demo-v3.txt`)

Replays a clean scripted scenario: registers `u1`–`u4` (passwords `secret1`–`secret4`); `u1` opens company **p1** and offers ownership to `u2`; `u2` accepts, sets a 20% company coupon `sale123`, appoints `u3` as manager (`MAP_DEFINITION`) and creates event **e1** with a 10×10 seating layout. Net result you can log into: `u1`, `u2` (owner), `u3` (manager), `u4` — and `admin`.

### Loading your own scripted data

Point the file env var at any path (filesystem or `classpath:`):

```bash
TICKETING_BOOTSTRAP_DATASET=initial-state-file \
TICKETING_SEED_ENABLED=false \
TICKETING_INITIAL_STATE_FILE=/path/to/my-state.txt \
mvn spring-boot:run
```

Combine with `TICKETING_BOOTSTRAP_CLEAR_DB_ON_START=true` to wipe operational data first (the config DB / system admin is preserved). Format and command vocabulary are in the existing `README.md` → "Initial-state file".

---

## 1. Configuration & startup validation

### 1.1 ✅ Admin user + password in the config file (`application.yml`)

- **Where:** `src/main/resources/application.yml` →
  ```yaml
  ticketing:
    admin:
      username: ${TICKETING_ADMIN_USERNAME:admin}
      password: ${TICKETING_ADMIN_PASSWORD:admin123}
  ```
  Consumed by `PlatformInitializationService` (creates the system admin at startup) via `StartupConfiguration` (`domain/system/StartupConfiguration.java`).
- **Tests:** `infrastructure/init/PlatformInitializationServiceTest`, `application/initialization/PlatformInitializationAdminTest`.
- **Verify:**
  ```bash
  TICKETING_ADMIN_USERNAME=root TICKETING_ADMIN_PASSWORD=supersecret mvn spring-boot:run
  # log in as root / supersecret
  ```

### 1.2 ✅ Config DB is a separate database from the operational DB

- **Where:** two datasources are declared in `application.yml` (`spring.datasource.operational` and `spring.datasource.config`) and wired by `infrastructure/persistence/OperationalJpaConfig.java` and `infrastructure/persistence/ConfigJpaConfig.java`. The system-admin / config data lives in `...config` (separate JDBC URL: `jdbc:h2:mem:ticketing_cfg`), operational aggregates in `...operational`. The config DB is never touched by `OperationalDataWiper`.
- **Tests:** `infrastructure/persistence/ConfigIsolationTest`, `infrastructure/config/TestConfigurationIsolationTest`.
- **Verify:**
  ```bash
  mvn test -Dtest=ConfigIsolationTest
  ```

### 1.3 ✅ Error log when the config file is wrong + pre-init "all params correct" check that crashes the system

- **Where:** `application/initialization/TicketingConfigurationRules.java`. Its `EarlyValidationInitializer` runs at **environment-prepare time** (registered in `src/main/resources/META-INF/spring/org.springframework.context.ApplicationContextInitializer`) — i.e. **before** JPA or any heavy bean starts. It validates every parameter and value type:
  - datasource URLs/drivers (operational + config), JWT secret + positive expiry
  - `ticketing.persistence` ∈ {`memory`,`jpa`}; when `jpa`, validates DB username/password/dialect and `ddl-auto` against an allow-list
  - `queue.threshold` / `queue.flow-rate` positive ints
  - external base-url present; when set, validates currency/card-number/holder/cvv/id, month (1–12), year (≥2000), positive timeouts
  - `bootstrap.dataset` ∈ {`dev-seed`,`initial-state-file`,`none`} and `clear-db-on-start` boolean
  - admin username non-blank, admin password ≥ 6 chars
  
  On any violation it throws `StartupHaltException` (`application/initialization/StartupHaltException.java`) with a framed message and a suppressed stack trace, so it lands cleanly in `logs/error.log` (ERROR appender in `src/main/resources/logback.xml`).
- **Tests:** `application/initialization/ConfigurationValidatorTest` (parameter-by-parameter, valid + invalid + wrong-type values), `application/initialization/StartupHaltExceptionTest`, `infrastructure/init/InitializationStartupFailureTest`, `infrastructure/init/InitializationStartupValidTest`, `application/initialization/StartupRunnerOrderingTest`.
- **Verify (watch it refuse to boot + write error.log):**
  ```bash
  TICKETING_QUEUE_THRESHOLD=-5 mvn spring-boot:run        # invalid → halt
  TICKETING_ADMIN_PASSWORD=123 mvn spring-boot:run         # < 6 chars → halt
  TICKETING_PERSISTENCE=mysql mvn spring-boot:run          # not memory/jpa → halt
  cat logs/error.log
  # Unit-level, all param types:
  mvn test -Dtest=ConfigurationValidatorTest
  ```

### 1.4 ✅ Init file: error logs when misconfigured + wipe DB before sending the error

- **Where:** init-file pipeline in `application/initialization/`:
  `InitialStateFileLoader` → `InitialStateParser` (`InitialStateParseException`) → `InitialStateExecutor` (`InitialStateExecutionException`), orchestrated by `DataBootstrapRunner.runInitialStateFile()`. On **any** parse/exec failure it calls `OperationalDataWiper.wipeAll()` **before** rethrowing — "better no data than half the init file's data". The error is framed by `StartupHaltException` and logged to `error.log`.
- **Tests:** `application/initialization/InitialStateRollbackMemoryTest` ("failed initialization leaves database empty in memory mode"), `application/initialization/InitialStateRollbackJpaTest`, `InitialStateParserTest`, `InitialStateExecutorTest`, `InitialStateFileLoaderTest`, `StaffInitialStateScenarioTest`.
- **Verify:**
  ```bash
  mvn test -Dtest='com.ticketing.application.initialization.InitialStateRollback*Test'
  ```

### 1.5 ✅ Flag to clear the DB before starting

- **Where:** `ticketing.bootstrap.clear-db-on-start` (`${TICKETING_BOOTSTRAP_CLEAR_DB_ON_START:false}`) in `application.yml`. `DataBootstrapRunner.clearOperationalDataIfRequested()` calls `OperationalDataWiper.wipeAll()` when true (after platform init, before data bootstrap; config DB untouched).
- **Tests:** `application/initialization/DataBootstrapRunnerTest` (dataset/flag resolution), wipe behavior in the rollback tests above.
- **Verify:**
  ```bash
  TICKETING_BOOTSTRAP_CLEAR_DB_ON_START=true mvn spring-boot:run
  # log shows: "clear-db-on-start=true — wiping operational data before bootstrap"
  ```

---

## 2. Database robustness & locking

### 2.1 ✅ DB not responding → system still boots, but errors on every DB operation

- **Where:** HikariCP timeouts in `application.yml` (`connection-timeout: 5000`, `validation-timeout`, `keepalive-time`). The app starts even with no DB; DB-backed calls then fail fast with a connection exception that the presentation layer classifies (see 3.1). Recovery is automatic once the DB returns.
- **Tests:** `infrastructure/persistence/DbConnectionRecoveryJpaTest` (DB down → operation fails → DB back → operation succeeds, no restart), `infrastructure/persistence/PersistenceRestartAcceptanceTest`.
- **Verify:**
  ```bash
  mvn test -Dtest=DbConnectionRecoveryJpaTest
  ```

### 2.2 ✅ Locking in DB implementations — optimistic + pessimistic, no race on concurrent edits

This covers "locking in db implementations", "no race condition when 2 people edit the same thing", "optimistic logic for all writable data (events etc.)", and "consider pessimistic locking".

- **Where:**
  - **Optimistic locking** via JPA `@Version` on every writable aggregate: `Event`, `InventoryZone`, `Seat`, `Member`, `StaffAppointment`, `Admin`, `LotteryEntry`, `PendingNotification`, `ActiveOrder`, `OrderItem` (`domain/.../*.java`). The in-memory repos enforce the same version invariant — see `infrastructure/InMemory*Repository` and `domain/exception/OptimisticLockException`.
  - **Pessimistic locking** for the hot seat-reservation path (the place a true race would lose money): seat reservation acquires a write lock so two buyers can't sell the same seat. Implemented in `JpaEventRepository` / `JpaOrderRepository` reserve path and `application/services/TicketSelectionService` + `OrderService`.
- **Tests:**
  - `application/transactions/SeatReservationLockingJpaTest`, `application/transactions/SeatReservationStressJpaTest` (concurrent buyers, no double-sell)
  - `application/concurrency/NonInventoryAggregateLockingJpaTest`, `application/concurrency/CompanyServiceConcurrencyTest`
  - `infrastructure/InMemoryOptimisticLockingTest`, `infrastructure/persistence/WritableAggregateVersionInvariantTest`
  - `concurrency/GlobalRaceConditionTest`, `application/QueueConcurrencyTest`
- **Verify:**
  ```bash
  mvn test -Dtest='SeatReservation*JpaTest,*LockingJpaTest,*ConcurrencyTest,GlobalRaceConditionTest'
  ```

### 2.3 ✅ More indicative / informative DB connection errors

- **Where:** `presentation/vaadin/util/PresenterErrorClassifier.java` walks the exception cause chain (`DataAccessResourceFailureException` → Hibernate `JDBCConnectionException` → Hikari `SQLTransientConnectionException` → driver error) and maps it to a `DB_UNAVAILABLE` category with a plain-language message instead of a raw stack trace.
- **Tests:** `presentation/vaadin/util/PresenterErrorClassifierTest`.
- **Verify:** `mvn test -Dtest=PresenterErrorClassifierTest`

### 2.4 ⚠️ Store session token in the DB — **OPEN for V4**

- **Current state:** session-token revocation state is held by `infrastructure/InMemorySessionTokenRepository` (the only `ISessionTokenRepository` implementation). `SessionToken` already carries a `version` field and revoke metadata, so it's *designed* for persistence, but there is **no JPA-backed `ISessionTokenRepository` yet** — tokens don't survive a restart.
- **What's needed for V4:** add `JpaSessionTokenRepository` + a `session_token` entity table in the operational DB and register it under the `jpa` persistence profile (mirror how `JpaMemberRepository` is wired).
- **Existing tests to extend:** `application/SessionTokenServiceTest` (behavior is covered; the persistence backend is the gap).

---

## 3. DB-down / no-internet UX (presentation layer)

### 3.1 ✅ Pressing Company after DB down must NOT route to an error screen — stay on the page, show a red banner, auto-restore when the DB is back

- **Where:**
  - `presentation/vaadin/util/ErrorBanner.java` — a persistent red banner (Lumo error tokens, `role="alert"`), distinct from the auto-dismiss toast in `UiMessages`. Stays until the view hides it.
  - `presentation/vaadin/presenters/CompanyPresenter.userMessage(...)` — catches the infra exception, classifies it (3.1 above), returns the actionable message instead of throwing → the view shows the banner and keeps the user in place rather than navigating away. When the next action succeeds (DB back), the banner is hidden and data reloads.
- **Tests:** `presentation/vaadin/presenters/CompanyPresenterDbErrorMessageTest`, `presentation/vaadin/util/ErrorBannerTest`, `PresenterErrorClassifierTest`.
- **Verify:** `mvn test -Dtest='CompanyPresenterDbErrorMessageTest,ErrorBannerTest'`

### 3.2 ✅ No-internet → profile/external systems crash and don't recover after reconnecting

- **Where:** external integration is wrapped so a dropped connection raises `ExternalSystemsUnavailableException` (`domain/gateway/`) rather than killing the flow; `HttpExternalSystemsClient` / `HttpPaymentGateway` / `HttpTicketSupplyGateway` retry on the next call once connectivity returns. The startup handshake (`infrastructure/gateway/ExternalSystemsHandshakeRunner`) only runs when `ticketing.external.base-url` is set. The same `EXTERNAL_SYSTEM_UNAVAILABLE` category drives a banner/toast, and recovery is per-call (no restart).
- **Tests:** `infrastructure/gateway/ExternalSystemsHandshakeRunnerTest`, `infrastructure/gateway/HttpExternalSystemsClientTest`, `application/TicketSupplyRobustnessTest`, `application/CheckoutRobustnessTest`.
- **Verify:** `mvn test -Dtest='*RobustnessTest,ExternalSystemsHandshakeRunnerTest,HttpExternalSystemsClientTest'`

---

## 4. Notifications

### 4.1 ✅ Lottery loser gets a notification

- **Where:** `application/services/EventService.performDraw(...)`. After selecting winners it notifies **both** groups: winners get "You have won the lottery! You have 48 hours…"; every non-winning entry (`!winnerMemberIds.contains(entry.memberId())`) gets "The lottery draw has concluded. Unfortunately, you were not selected this time." Triggered automatically by `application/scheduling/LotteryDrawScheduler` when the registration window closes.
- **Tests:** `application/LotteryNotificationTest`, plus `LotteryDrawEdgeCasesTest`, `LotteryEnforcementTest`, `application/scheduling/LotteryDrawSchedulerTest`.
- **Verify:** `mvn test -Dtest='LotteryNotificationTest,LotteryDrawSchedulerTest'`

### 4.2 ✅ Notifications received everywhere (any route), only *persisted* in the Notifications tab

- **Where:**
  - `presentation/vaadin/MainLayout.java` is the app shell that holds the live registration, so a logged-in member gets toasts on **every** route — not just while the Notifications view is open (#490).
  - `presentation/vaadin/RealtimeNotificationBinder.java` keeps exactly one listener bound to the current member (rebinds on login/switch, unbinds on logout) — unit-testable without a router.
  - Delivery transport: `infrastructure/notification/WebSocketNotificationService` (+ `NotificationListener`). Durable history lives in `infrastructure/notification/InMemoryPendingNotificationRepository` (and the JPA variant), surfaced by `application/services/NotificationQueryService` in the Notifications tab.
- **Tests:** `presentation/vaadin/RealtimeNotificationBinderTest`, `presentation/vaadin/presenters/NotificationsPresenterTest`, `presentation/vaadin/views/NotificationsViewTest`, `infrastructure/notification/WebSocketNotificationServiceTest`, `infrastructure/notification/InMemoryPendingNotificationRepositoryTest`, `application/NotificationQueryServiceTest`.
- **Verify:** `mvn test -Dtest='RealtimeNotificationBinderTest,NotificationsPresenterTest,NotificationsViewTest,NotificationQueryServiceTest'`

---

## 5. Payment & coupons

### 5.1 ✅ CVV behavior: 986 = unexpected response, 988 = payment failed (−1), 100 = success

- **Where:** `infrastructure/gateway/StubPaymentGateway.java` (active when `ticketing.external.base-url` is blank — i.e. local dev/tests):
  - `SIMULATED_CVV_UNEXPECTED = "986"` → `PaymentResult.failed("…declined by the external payment system.")`
  - `SIMULATED_CVV_DECLINE = "988"` → `PaymentResult.failed("…declined by issuer.")`
  - any other CVV incl. `100` → `PaymentResult.successful(...)`
  
  Against the real WSEP endpoint, `infrastructure/gateway/HttpPaymentGateway.java` maps a `-1` body to a failed result and any out-of-range/garbled body to "declined / unexpected response", so the purchase flow never sees a raw exception. Either way, the outcome is visible in purchase history.
- **Tests:** `infrastructure/gateway/HttpPaymentGatewayTest`, `infrastructure/gateway/StubGatewaysTest`, `infrastructure/gateway/PaymentGatewaySelectionTest`, `infrastructure/gateway/MultiProviderGatewayWiringTest`, `infrastructure/gateway/WsepLivePaymentIntegrationTest` (live, opt-in).
- **Verify:**
  ```bash
  mvn test -Dtest='HttpPaymentGatewayTest,StubGatewaysTest,PaymentGatewaySelectionTest'
  # UI: checkout with CVV 100 → success; 988 → "declined by issuer"; 986 → "declined by external system"
  ```

### 5.2 ✅ Coupon: optional coupon reduces the price, new price visible after entry, discount shown at all steps

- **Where:**
  - Apply/remove: `application/services/OrderService.applyCoupon(token, couponCode)` sets the coupon and **recomputes the order total** (`order.getTotalPrice()` reflects the discount; rollback in memory if the coupon is rejected). `quoteCheckout()` returns `CheckoutQuote(subtotal, total)`.
  - Discount model: `domain/event/CouponDiscount`, `SimpleDiscount`, `ConditionalDiscount`, `SumCompositeDiscount`, `MaxCompositeDiscount`, applied through the event's `IDiscountPolicy`.
  - UI shows it at every step, not just checkout: `presentation/vaadin/views/EventsView` ticket dialog renders `"subtotal X | total Y (Coupon applied)"` and re-renders immediately after Apply/Remove; `presentation/vaadin/presenters/OrdersPresenter.applyCoupon/quoteCheckout` feed the live numbers; `components/PolicyBadgesPanel` surfaces visible discounts on the event.
- **Tests:** `application/OrderServiceTest` (discount/coupon → completed purchase uses discounted amount), `domain/event/EventPolicyTest`, `application/services/BuyerPolicyCatalogTest`.
- **Verify:**
  ```bash
  mvn test -Dtest=OrderServiceTest
  # UI: add tickets → enter coupon → Apply → total drops + "(Coupon applied)"; Remove → reverts
  ```

---

## 6. Event creation & remaining UX

### 6.1 ✅ Save Draft must show *why* it failed (e.g. doors-open after start time)

- **Where:** validation lives in the domain (`domain/event/EventSchedule` constructor: `"Doors open time must be before or at start time"`). `presentation/vaadin/presenters/CompanyPresenter.editEvent/saveLayout` catch the `IllegalArgumentException` and, via `userMessage(...)`, return the **specific** domain message to the view instead of a silent failure or a generic error. Infra failures are separated out so a real DB/external problem reads differently from a validation problem.
- **Tests:** `application/EventServiceTest`, `application/EventServicePolicyTest`, `application/EventLayoutServiceTest`, `application/DefineVenueServiceTest`; schedule mapping in `domain/event/EventJpaMappingTest`.
- **Verify:**
  ```bash
  mvn test -Dtest='EventServiceTest,EventLayoutServiceTest'
  # UI: create draft with doors-open after start → Save Draft shows the exact reason
  ```

### 6.2 ⚠️ Drag-to-select in zone/seat painting — **OPEN for V4**

- **Current state:** `presentation/vaadin/components/VenueLayoutEditorComponent.java` paints cells **one click at a time** (`cell.addClickListener(e -> paint(r,c))`). There is no click-drag marquee yet, so painting a large zone is tedious — exactly the comment.
- **What's needed for V4:** add pointer `mousedown`/`mouseenter`-while-pressed handling (most cleanly in the `seat-map.js` client component, or via a small JS hook on the grid) to paint/erase across a dragged rectangle, reusing the existing `paint(r,c)` / `repaintCell(r,c)` logic.

### 6.3 ⚠️ Company tab takes a long time to load — **OPEN for V4 (needs measurement)**

- **Current state:** no dedicated performance fix is present in this snapshot. `CompanyPresenter` exposes many separate `load*` calls (org chart, personnel access, policies, purchase history, sales report) and the JPA event/venue mappings are lazy — a likely N+1 / many-round-trips cause when the tab opens.
- **What's needed for V4:** profile the Company tab open path, then either batch the `load*` calls, add `@EntityGraph`/fetch joins on the company→events→zones load, or lazy-load the heavier panels (reports) on demand. Add a timing assertion or a simple before/after measurement to the PR.

---

## 7. Process items (not code)

- 🛠️ **Add milestone tags to all issues, including ones added mid-work** — GitHub Issues/Milestones hygiene. Set `--milestone "Version 4 - Final"` at creation time in the issue scripts so nothing slips through; sweep open issues before the eval.
- 🛠️ **Always cover happy / sad / unexpected paths** — the suite already follows this (e.g. payment success/decline/unexpected in 5.1; lottery winner/loser/empty-pool; init valid/invalid/wrong-type in 1.3; DB up/down/recover in 2.1). Keep the pattern for every new V4 issue.

---

## 8. Comment → artifact quick index

| # | Comment | Status | Key class(es) | Key test(s) |
|---|---------|--------|---------------|-------------|
| 1 | Save Draft failure feedback (doors-open) | ✅ | `EventSchedule`, `CompanyPresenter` | `EventServiceTest`, `EventLayoutServiceTest` |
| 2 | Lottery loser notification | ✅ | `EventService.performDraw` | `LotteryNotificationTest` |
| 3 | Notifications everywhere, persisted in tab | ✅ | `MainLayout`, `RealtimeNotificationBinder`, `NotificationQueryService` | `RealtimeNotificationBinderTest`, `NotificationsViewTest` |
| 4 | Milestone tags on all issues | 🛠️ | — | — |
| 5 | Admin user/password in config | ✅ | `application.yml`, `PlatformInitializationService` | `PlatformInitializationServiceTest` |
| 6 | Error log on bad config | ✅ | `TicketingConfigurationRules`, `StartupHaltException`, `logback.xml` | `ConfigurationValidatorTest` |
| 7 | Test param checking in config | ✅ | `TicketingConfigurationRules` | `ConfigurationValidatorTest` |
| 8 | No crash when external systems crash | ✅ | gateways + `ExternalSystemsUnavailableException` | `*RobustnessTest`, `HttpExternalSystemsClientTest` |
| 9 | Config file in its own DB | ✅ | `ConfigJpaConfig`, `OperationalJpaConfig` | `ConfigIsolationTest` |
| 10 | Init file error logs on misconfig | ✅ | `DataBootstrapRunner`, `InitialState*` | `InitialStateParserTest`, `InitialStateExecutorTest` |
| 11 | Pre-init param check, crash if bad (all types) | ✅ | `TicketingConfigurationRules.EarlyValidationInitializer` | `ConfigurationValidatorTest`, `InitializationStartupFailureTest` |
| 12 | DB down → boot but error on DB ops | ✅ | Hikari config, classifier | `DbConnectionRecoveryJpaTest` |
| 13 | Company tab slow load | ⚠️ | `CompanyPresenter` | — (add for V4) |
| 14 | Drag-to-select in zone painting | ⚠️ | `VenueLayoutEditorComponent`, `seat-map.js` | — (add for V4) |
| 15 | Locking in DB implementations | ✅ | `@Version` aggregates, Jpa reserve path | `SeatReservationLockingJpaTest`, `WritableAggregateVersionInvariantTest` |
| 16 | Store session token in DB | ⚠️ | `InMemorySessionTokenRepository` (no JPA impl yet) | `SessionTokenServiceTest` |
| 17 | Flag to clear DB before start | ✅ | `DataBootstrapRunner`, `OperationalDataWiper` | `DataBootstrapRunnerTest`, rollback tests |
| 18 | Init bug → wipe DB before erroring | ✅ | `DataBootstrapRunner.runInitialStateFile` | `InitialStateRollbackMemoryTest`/`JpaTest` |
| 19 | Optimistic logic for all writable data, no race | ✅ | `@Version` on `Event`/`Member`/… | `NonInventoryAggregateLockingJpaTest`, `GlobalRaceConditionTest` |
| 20 | Consider pessimistic locking | ✅ | seat reservation path | `SeatReservationStressJpaTest` |
| 21 | DB errors more informative | ✅ | `PresenterErrorClassifier` | `PresenterErrorClassifierTest` |
| 22 | Company after DB down → banner, not error screen, auto-recover | ✅ | `ErrorBanner`, `CompanyPresenter` | `CompanyPresenterDbErrorMessageTest`, `ErrorBannerTest` |
| 23 | No-internet profile crash & no recovery | ✅ | Http gateways, handshake runner | `ExternalSystemsHandshakeRunnerTest`, `CheckoutRobustnessTest` |
| 24 | Coupon discount shown at all steps | ✅ | `EventsView`, `OrdersPresenter`, `OrderService` | `OrderServiceTest` |
| 25 | CVV 986/988 fail, 100 works | ✅ | `StubPaymentGateway`, `HttpPaymentGateway` | `HttpPaymentGatewayTest`, `StubGatewaysTest` |
| 26 | Cover happy/sad/unexpected everywhere | 🛠️/✅ | — | whole suite |
| 27 | Optional coupon reduces price + new price visible | ✅ | `OrderService.applyCoupon/quoteCheckout` | `OrderServiceTest` |

---

## 9. Open items to schedule for V4

1. **Session tokens in the DB** (#16) — add `JpaSessionTokenRepository` + entity, wire under the `jpa` profile.
2. **Drag-to-select painting** (#14) — pointer-drag rectangle paint/erase in the venue editor.
3. **Company tab load time** (#13) — profile, batch/eager-fetch or lazy-load panels, add a timing check.

Everything else above is implemented and covered; `mvn clean verify` is the single command that proves it (and enforces the 75% JaCoCo gate).

---

## 10. Google Cloud / remote database (PostgreSQL)

### 10.1 Where Google Cloud is (and isn't) used

The running application does **not** depend on Google Cloud. The only `google.com` references in the code are **Google Fonts** (cosmetic, in `TicketingApplication`), and the external payment endpoint is hosted on **Koyeb**, not GCP. By default — and in every test — the app runs on **H2 in-memory** with no cloud at all.

Google Cloud appears only as an **optional place to host the database** for V3 Requirement 7 ("remote database, in config, config-only switch"). The app is DB-agnostic: every datasource setting is an env var with H2 defaults, so pointing it at **Cloud SQL for PostgreSQL** is a config-only change. Provisioning/budget steps live in `docs/deploy-cloud-sql.md`.

### 10.2 ✅ FIX — `DB_URL` was ignored (silent fallback to H2)

After the config DB was split into its own database, `application.yml` reads **two** URL vars — `DB_URL_OPERATIONAL` (operational DB: members/companies/events/orders/queues/tickets/notifications) and `DB_URL_CONFIG` (config DB: admin/system). The README, deploy doc, and the team run-script all set `DB_URL`, which the app **didn't read** — so a "Cloud SQL" run silently fell back to H2 (or failed when the Postgres driver met an H2 URL).

**Fix:** both URLs now fall back through `DB_URL` before the H2 default, so all spellings work and `DB_URL` can never silently land on H2 again:

```yaml
spring:
  datasource:
    operational:
      url: ${DB_URL_OPERATIONAL:${DB_URL:jdbc:h2:mem:ticketing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false}}
    config:
      url: ${DB_URL_CONFIG:${DB_URL:jdbc:h2:mem:ticketing_cfg;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false}}
```

Resolution per datasource: explicit `DB_URL_OPERATIONAL`/`DB_URL_CONFIG` → else `DB_URL` (both share one DB; tables don't collide since operational and config map disjoint entity packages) → else H2 default. Default dev behavior is unchanged.

- **Where:** `src/main/resources/application.yml`
- **Choose your topology:** set `DB_URL` for a single shared Cloud SQL database, or set both `DB_URL_OPERATIONAL` + `DB_URL_CONFIG` for genuinely separate databases (matches V3-9 "config in its own DB"; create the second with `gcloud sql databases create ticketing_cfg --instance=ticketing-db`).

### 10.3 ✅ FEATURE — graceful, explained startup error when the DB is unreachable

Previously a wrong host/password/network surfaced as a Hibernate/Hikari stack trace. Now a **preflight** opens a real connection to each configured database **before** JPA starts and, on failure, halts with a short framed block in `logs/error.log` that names the host, the driver's reason, and the **likely cause** (wrong credentials, DB doesn't exist, instance stopped / IP not authorized, driver-URL mismatch).

- **Where:** `application/initialization/DatabaseConnectivityPreflight.java`, invoked from `TicketingConfigurationRules.EarlyValidationInitializer`; framed via the existing `StartupHaltException` → `logback.xml` ERROR appender.
- **Scope:** only runs in `jpa` mode against non-H2 URLs, so the whole existing suite (memory / JPA-on-H2) is a no-op and stays green. The **runtime** "DB goes down while running → keep serving, error on DB ops" path is unchanged and still covered by `DbConnectionRecoveryJpaTest`.
- **Tests:** add a unit test for the `diagnose()` cause-mapping (see issue FIX-V3-DB-2).
- **Verify the message locally (no Cloud SQL needed):**
  ```powershell
  $env:TICKETING_PERSISTENCE="jpa"
  $env:DB_URL="jdbc:postgresql://10.255.255.1:5432/ticketing"   # unroutable
  $env:DB_DRIVER="org.postgresql.Driver"
  $env:DB_DIALECT="org.hibernate.dialect.PostgreSQLDialect"
  mvn spring-boot:run            # → framed DATABASE CONNECTION ERROR, then check logs\error.log
  ```

### 10.4 ✅ TOOLING — run chooser script (`scripts/run.ps1`)

Lets you **choose** target and mode instead of hand-setting env vars:

- **Target:** `local` (in-memory) · `cloud` (single Cloud SQL DB via `DB_URL`) · `cloud-split` (separate `ticketing` + `ticketing_cfg`)
- **Mode:** `first` (create/update schema) · `normal` (validate) · `initial` (wipe + load `staff-demo-v3.txt`)
- No flags → interactive menu. On a failed run it prints a hint pointing at `logs\error.log` and the common Cloud SQL fixes.

```powershell
.\scripts\run.ps1                              # menu
.\scripts\run.ps1 -Target cloud-split -Mode first
.\scripts\run.ps1 -Target local
```

### 10.5 Verifying the V3 features on real Postgres

The JPA tests run on H2-in-PostgreSQL-mode; to prove them on the real engine, point the datasource at Cloud SQL and run the DB-dependent suites, then smoke-test the UI:

```powershell
.\scripts\run.ps1 -Target cloud-split -Mode first      # one-time: create schema
mvn test -Dtest='*JpaTest,*ConcurrencyTest,SeatReservation*,DbConnectionRecoveryJpaTest,InitialStateRollbackJpaTest,ConfigIsolationTest'
```

Manual smoke pass (normal mode after first run): (1) restart-persistence — create event, restart, still there; (2) no double-sell on the last seat across two browsers; (3) `-Mode initial` wipes operational data but `admin` still logs in (config DB); (4) pause the instance → Company shows the red banner, stays on page, recovers on resume; (5) coupon `sale123` drops the total, CVV 100/988/986 behave; (6) lottery winners + losers both notified. Caveat: running against real Postgres (vs H2) may surface dialect/DDL differences — treat any failure there as a real Postgres-gap issue.

### 10.6 Updated V4 open items

1. **Session tokens in the DB** (#16) — `JpaSessionTokenRepository` + entity under the `jpa` profile.
2. **Drag-to-select painting** (#14) — pointer-drag paint/erase in the venue editor.
3. **Company tab load time** (#13) — profile + batch/eager-fetch or lazy-load panels.
4. **Doc sync** — update `README.md` + `docs/deploy-cloud-sql.md` to the `DB_URL_OPERATIONAL`/`DB_URL_CONFIG` names and the `DB_URL` fallback (FIX-V3-DB-4).
5. **Boot-when-DB-down toggle** (optional) — if you want the app to boot even when a remote DB is unreachable at startup (instead of the preflight halt), add a config switch.
