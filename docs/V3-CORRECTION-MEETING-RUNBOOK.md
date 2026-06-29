# V3 Correction Meeting Runbook

This file is the short version to use during the V3 correction meeting.  
Use it to know **which command to run**, **which users/data should appear**, **what to check**, and **where to look if something fails**.

---

## 0. Before the meeting: clean checklist

Run these first from the repo root.

```powershell
git branch --show-current
git status --short
git log --oneline -5
```

Expected:

- You are on the V3 DB fix branch or on the final branch you will demo.
- `git status --short` should be empty, except for intentionally ignored local files.
- Latest commit should include the DB fix / README / run script.

Then run the full test suite:

```powershell
mvn clean verify
```

Expected:

- Build succeeds.
- Tests pass.
- JaCoCo coverage gate passes.

If `mvn clean verify` fails, do not debug the whole suite first. Run the smaller test listed under the failing feature below.

---

## 1. Which command loads which users?

This is the most important thing to understand.

There are two separate settings:

| Setting | Meaning |
|---|---|
| `TICKETING_BOOTSTRAP_DATASET` | Which demo data/users are loaded |
| `TICKETING_PERSISTENCE` | Where the data is stored: memory/H2/JPA/Cloud SQL |

`TICKETING_PERSISTENCE` does **not** decide whether you get `manager` or `u1`.  
The dataset decides that.

---

## 2. Local run commands

### 2.1 Default local demo

```powershell
mvn spring-boot:run
```

Expected data:

| User | Password | Meaning |
|---|---|---|
| `admin` | `admin123` | system admin |
| `manager` | `manager123` | demo manager |
| `owner` | `owner123` | demo company owner |
| `buyer1` | `buyer1123` | demo buyer |
| `buyer2` | `buyer2123` | demo buyer |
| `teen` | `teen123` | under-age user test case |

Use this when you want the regular demo data.

---

### 2.2 Initial-state demo: `u1`, `u2`, `u3`, `u4`

PowerShell:

```powershell
$env:TICKETING_BOOTSTRAP_DATASET="initial-state-file"
$env:TICKETING_SEED_ENABLED="false"
$env:TICKETING_INITIAL_STATE_FILE="classpath:initial-state/staff-demo-v3.txt"
mvn spring-boot:run
```

Expected data:

| User | Password | Meaning |
|---|---|---|
| `admin` | `admin123` | system admin |
| `u1` | `secret1` | creates company `p1` |
| `u2` | `secret2` | becomes owner |
| `u3` | `secret3` | manager |
| `u4` | `secret4` | regular user |

Use this when the reviewer asks about the init-file scenario.

---

### 2.3 Empty local app: only admin

```powershell
$env:TICKETING_BOOTSTRAP_DATASET="none"
$env:TICKETING_SEED_ENABLED="false"
mvn spring-boot:run
```

Expected data:

| User | Password |
|---|---|
| `admin` | `admin123` |

Use this when you want to prove that the app can start without demo data.

---

## 3. How to run with a clean DB

### 3.1 Clean local memory run

For the default local memory mode, restarting the app is enough because the data is in memory.

```powershell
mvn spring-boot:run
```

Stop and run again = fresh local data.

---

### 3.2 Clean operational DB before startup

Use this flag:

```powershell
$env:TICKETING_BOOTSTRAP_CLEAR_DB_ON_START="true"
```

Clean DB + dev-seed:

```powershell
$env:TICKETING_BOOTSTRAP_CLEAR_DB_ON_START="true"
$env:TICKETING_BOOTSTRAP_DATASET="dev-seed"
mvn spring-boot:run
```

Clean DB + initial-state `u1`–`u4`:

```powershell
$env:TICKETING_BOOTSTRAP_CLEAR_DB_ON_START="true"
$env:TICKETING_BOOTSTRAP_DATASET="initial-state-file"
$env:TICKETING_SEED_ENABLED="false"
$env:TICKETING_INITIAL_STATE_FILE="classpath:initial-state/staff-demo-v3.txt"
mvn spring-boot:run
```

Expected:

- Operational data is wiped.
- Config/admin data is preserved or recreated from config.
- `admin / admin123` still works.

Tests:

```powershell
mvn test -Dtest=DataBootstrapRunnerTest
mvn test -Dtest='InitialStateRollback*Test'
```

If this fails, check:

```text
src/main/java/com/ticketing/application/initialization/DataBootstrapRunner.java
src/main/java/com/ticketing/application/initialization/OperationalDataWiper.java
src/main/resources/application.yml
```

---

## 4. Cloud SQL / PostgreSQL run commands

Use the new run chooser script.

### 4.1 Interactive menu

```powershell
.\scripts\run.ps1
```

It lets you choose:

| Target | Meaning |
|---|---|
| `local` | local in-memory mode |
| `cloud` | one Cloud SQL database |
| `cloud-split` | operational DB + config DB separately |

And:

| Mode | Meaning |
|---|---|
| `first` | create/update schema |
| `normal` | validate schema, keep data |
| `initial` | wipe/recreate schema and load `staff-demo-v3.txt` |

---

### 4.2 Cloud SQL first run

```powershell
.\scripts\run.ps1 -Target cloud-split -Mode first
```

Use only the first time or after schema changes.

Expected:

- Connects to Cloud SQL.
- Creates/updates schema.
- App starts.

---

### 4.3 Cloud SQL normal run

```powershell
.\scripts\run.ps1 -Target cloud-split -Mode normal
```

Use this for the normal demo after schema exists.

Expected:

- Validates schema.
- Keeps existing DB data.
- App starts.

---

### 4.4 Cloud SQL clean initial-state demo

```powershell
.\scripts\run.ps1 -Target cloud-split -Mode initial
```

Expected:

- Wipes/recreates schema.
- Loads `staff-demo-v3.txt`.
- Users are `admin`, `u1`, `u2`, `u3`, `u4`.

Use this if you need a clean DB for the meeting.

---

### 4.5 If Cloud SQL fails

First open:

```text
logs/error.log
```

The new DB preflight should print a clear `DATABASE CONNECTION ERROR`.

Common causes:

| Error type | What to fix |
|---|---|
| wrong password | rotate/check `DB_PASSWORD` |
| DB does not exist | create `ticketing_cfg` or fix URL |
| timeout/refused | Cloud SQL not running or your public IP is not authorized |
| no suitable driver | `DB_DRIVER` does not match the JDBC URL |
| using H2 accidentally | check `DB_URL`, `DB_URL_OPERATIONAL`, `DB_URL_CONFIG` |

Check environment variables:

```powershell
Get-ChildItem Env:DB*
Get-ChildItem Env:TICKETING*
```

For split DB mode, you need:

```powershell
$env:DB_URL_OPERATIONAL="jdbc:postgresql://<IP>:5432/ticketing"
$env:DB_URL_CONFIG="jdbc:postgresql://<IP>:5432/ticketing_cfg"
$env:DB_DRIVER="org.postgresql.Driver"
$env:DB_USERNAME="ticketing"
$env:DB_PASSWORD="<password>"
$env:DB_DIALECT="org.hibernate.dialect.PostgreSQLDialect"
```

If `ticketing_cfg` does not exist:

```powershell
gcloud sql databases create ticketing_cfg --instance=ticketing-db
```

---

## 5. What to show for each V3 correction

Use this table during the meeting.

| Review point | What to show | Command/test | If it fails, check |
|---|---|---|---|
| Admin user/pass in config | `admin / admin123` works; can override env vars | `mvn test -Dtest=PlatformInitializationServiceTest` | `application.yml`, `PlatformInitializationService` |
| Bad config logs error and stops | invalid env var stops app with `error.log` | `mvn test -Dtest=ConfigurationValidatorTest` | `TicketingConfigurationRules`, `StartupHaltException`, `logback.xml` |
| Config DB separate | config/admin separate from operational data | `mvn test -Dtest=ConfigIsolationTest` | `ConfigJpaConfig`, `OperationalJpaConfig`, `OperationalDataWiper` |
| Clear DB before start | run with `TICKETING_BOOTSTRAP_CLEAR_DB_ON_START=true` | `mvn test -Dtest=DataBootstrapRunnerTest` | `DataBootstrapRunner`, `OperationalDataWiper` |
| Bad init file wipes partial data | failed init leaves no half-loaded data | `mvn test -Dtest='InitialStateRollback*Test'` | `DataBootstrapRunner`, `InitialStateExecutor`, `InitialStateParser` |
| DB URL Cloud SQL fix | `DB_URL` fallback and split URLs work | run `.\scripts\run.ps1 -Target cloud-split -Mode first` | `application.yml`, `scripts/run.ps1` |
| Graceful DB connection error | wrong DB/IP shows clear error in `logs/error.log` | run with bad `DB_URL` | `DatabaseConnectivityPreflight` |
| DB down at runtime | app recovers when DB returns | `mvn test -Dtest=DbConnectionRecoveryJpaTest` | `PresenterErrorClassifier`, repositories |
| Company page DB error banner | no Vaadin crash screen, red banner shown | `mvn test -Dtest=CompanyPresenterDbErrorMessageTest` | `CompanyPresenter`, `ErrorBanner`, `PresenterErrorClassifier` |
| External systems crash handling | payment/supply failures do not crash app | `mvn test -Dtest='*RobustnessTest,ExternalSystemsHandshakeRunnerTest,HttpExternalSystemsClientTest'` | `HttpExternalSystemsClient`, gateway classes |
| Lottery loser notification | losers get notification after draw | `mvn test -Dtest=LotteryNotificationTest` | `EventService.performDraw` |
| Notifications everywhere | toast works outside notification tab | `mvn test -Dtest=RealtimeNotificationBinderTest` | `MainLayout`, `RealtimeNotificationBinder` |
| CVV 100/986/988 | `100` success, `986` and `988` fail cleanly | `mvn test -Dtest='HttpPaymentGatewayTest,StubGatewaysTest'` | `StubPaymentGateway`, `HttpPaymentGateway` |
| Coupon reduces price | total changes immediately after coupon | `mvn test -Dtest=OrderServiceTest` | `OrderService.applyCoupon`, `quoteCheckout`, `EventsView` |
| Save Draft validation feedback | invalid doors-open time shows exact reason | `mvn test -Dtest='EventServiceTest,EventLayoutServiceTest'` | `EventSchedule`, `CompanyPresenter` |
| Locking / no double sell | concurrent seat purchase does not double-sell | `mvn test -Dtest='SeatReservation*JpaTest,*LockingJpaTest,*ConcurrencyTest,GlobalRaceConditionTest'` | `@Version`, seat reservation path, JPA repos |

---

## 6. Manual UI smoke test

Do this after `mvn clean verify`.

### 6.1 Default dev-seed smoke

Run:

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

Check:

1. Login as `admin / admin123`.
2. Login as `manager / manager123`.
3. Open Company tab.
4. Confirm no crash.
5. Open Events.
6. Try buying a ticket as `buyer1 / buyer1123`.

Expected:

- App loads.
- Demo users work.
- Company/events pages open.

---

### 6.2 Initial-state smoke

Run:

```powershell
$env:TICKETING_BOOTSTRAP_DATASET="initial-state-file"
$env:TICKETING_SEED_ENABLED="false"
$env:TICKETING_INITIAL_STATE_FILE="classpath:initial-state/staff-demo-v3.txt"
mvn spring-boot:run
```

Check:

1. Login as `u1 / secret1`.
2. Login as `u2 / secret2`.
3. Login as `u3 / secret3`.
4. Check company/event data from `staff-demo-v3.txt`.

Expected:

- `u1`–`u4` exist.
- `manager` may not exist because this is not dev-seed.

---

### 6.3 Coupon + CVV smoke

Use an event/order that accepts coupon `sale123`.

Check:

| Input | Expected |
|---|---|
| coupon `sale123` | total price drops |
| CVV `100` | payment succeeds |
| CVV `988` | payment failed / declined |
| CVV `986` | unexpected/declined response handled cleanly |

If coupon does not reduce price, check:

```text
OrderService.applyCoupon
OrderService.quoteCheckout
EventsView coupon display
```

Run:

```powershell
mvn test -Dtest=OrderServiceTest
```

---

### 6.4 DB-down banner smoke

This is optional if there is no time.

1. Run against Cloud SQL.
2. Open Company page.
3. Stop/pause the DB or break the connection.
4. Click Company again or refresh data.

Expected:

- You stay on the page.
- Red error banner appears.
- No generic Vaadin error screen.
- After DB returns, actions work again.

If it fails, check:

```text
CompanyPresenter
ErrorBanner
PresenterErrorClassifier
```

Run:

```powershell
mvn test -Dtest='CompanyPresenterDbErrorMessageTest,ErrorBannerTest,PresenterErrorClassifierTest'
```

---

## 7. Known open items for V4

Be honest about these if asked.

| Item | Status |
|---|---|
| Store session tokens in DB | Open for V4 |
| Drag-to-select zone painting | Open for V4 |
| Company tab performance optimization | Open for V4 |

Everything else in the V3 correction list is implemented or covered by tests.

---

## 8. Fast meeting script

Use this if you need to explain quickly:

```text
For V3 corrections, we added/verified config validation, DB separation, clean initialization,
DB rollback on bad init files, Cloud SQL run support, graceful DB connection errors, runtime
DB-down handling, notification fixes, payment/CVV handling, coupon recalculation, save-draft
validation feedback, and locking/race-condition coverage.

The main command is mvn clean verify.

For data:
- default run loads dev-seed: manager/owner/buyer users.
- initial-state run loads u1/u2/u3/u4.
- admin/admin123 exists in every mode.

For clean DB:
- local memory restarts cleanly.
- TICKETING_BOOTSTRAP_CLEAR_DB_ON_START=true wipes operational data.
- scripts/run.ps1 -Target cloud-split -Mode initial gives a clean Cloud SQL initial-state demo.

For failures:
- config/startup failures go to logs/error.log.
- Cloud SQL failures are explained by DatabaseConnectivityPreflight.
- UI DB failures should show an ErrorBanner instead of crashing.
```

---

## 9. Most useful commands summary

```powershell
# Full verification
mvn clean verify

# Normal local run
mvn spring-boot:run

# Initial-state local run
$env:TICKETING_BOOTSTRAP_DATASET="initial-state-file"
$env:TICKETING_SEED_ENABLED="false"
$env:TICKETING_INITIAL_STATE_FILE="classpath:initial-state/staff-demo-v3.txt"
mvn spring-boot:run

# Empty local run
$env:TICKETING_BOOTSTRAP_DATASET="none"
$env:TICKETING_SEED_ENABLED="false"
mvn spring-boot:run

# Clean DB before run
$env:TICKETING_BOOTSTRAP_CLEAR_DB_ON_START="true"

# Cloud SQL menu
.\scripts\run.ps1

# Cloud SQL first setup
.\scripts\run.ps1 -Target cloud-split -Mode first

# Cloud SQL normal run
.\scripts\run.ps1 -Target cloud-split -Mode normal

# Cloud SQL clean initial-state run
.\scripts\run.ps1 -Target cloud-split -Mode initial

# Check DB startup errors
notepad logs\error.log

# Check what is staged/changed
git status --short
git diff --cached --stat
```
