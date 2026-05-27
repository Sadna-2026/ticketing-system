
## Concurrency / locking (in-memory repositories)

The project uses **optimistic locking** for concurrent updates to shared aggregates stored in in-memory repositories (`Event`, `ActiveOrder`, `Company`, `VirtualQueue`, `LotteryEntry`, `SessionToken`, `Admin`, `Member`):

- Each aggregate carries a `version` field; the repository increments it on every successful `save`.
- `save` uses `ConcurrentHashMap.compute` with compare-and-set semantics. If the entity's version does not match the stored version, `OptimisticLockException` is thrown.
- Repositories for mutable aggregates return **detached copies** from lookup methods and persist **repository-owned copies** on `save`, so callers cannot mutate stored state without a version check.
- Creating a company with a name that already exists fails atomically in `InMemoryCompanyRepository` (no global service lock required).

Application services must **not** use a single `synchronized` lock or `Object lock` to serialize **all** company operations. Simple state changes (create, suspend, reopen) rely on repository CAS. Workflows with **non-idempotent side effects** before save (e.g. company close with refunds) use a **per-company critical section** in `CompanyLifecycleService` — do not blindly retry those flows after `OptimisticLockException`.

## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.

## Policy Editing Architecture (V1 vs V2)
Per V1 spec (UC-II.4.3 / UC-C.2), the **full purchase/discount policy edit API is deferred to V2**. V1 ships only:
*   `IPurchasePolicy` + `IDiscountPolicy` abstractions (delivered by INF-9).
*   `AlwaysAllowPolicy` (purchase) and `NoDiscountPolicy` (discount) defaults wired into every `Event` at construction time.
*   In V2, policy management services now exist in the application/domain layers, while Vaadin policy CRUD screens remain intentionally scoped and incrementally exposed.
The V0 acceptance tests for policy editing (`SuccessfulDefaultPolicyEdit`, etc.) are present in the test suite under `@Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")`.

## Vaadin permission and error-message behavior (V2)

- Route-level Spring Security remains permissive for local UI development (`SecurityConfig`), but sensitive Vaadin views should enforce access semantics in the presentation layer.
- `MainLayout` navigation is role-aware:
  - guests: no `Company` / no `Admin`,
  - members: `Company` visible,
  - system admins: `Company` and `Admin` visible.
- `AdminView` now reroutes non-admin sessions away from `/admin` and shows a user-facing permission message.
- `CompanyView` keeps public lookup/map features visible while member-only actions stay hidden for guest sessions.
- For UUID-based actions, Vaadin views should surface explicit validation feedback (`Enter a valid ... ID.`) before service calls whenever possible.

## Service Architecture (Application vs Domain)
Following strict Domain-Driven Design (DDD) principles, the architecture enforces a strict separation between Application Services and Domain Services:

1.  **Application Services** (e.g. `OrderService`, `EventService`, `CompanyLifecycleService`) act purely as orchestrators. 
    *   They handle session validation, extract IDs from tokens, and manage transaction boundaries.
    *   They delegate all complex state modifications and cross-aggregate coordination to Domain Services.
    *   They should **not** hold an excessive number of repositories (an audit reduced their dependency footprint significantly).
    *   **Acceptance Tests:** All existing acceptance tests still go through the Application layer, treating it as the stable API entry point for the system.

2.  **Domain Services** (e.g. `OrderDomainService`, `QueueDomainService`, `EventDomainService`, `OrderTimeDomainService`, `LotteryDrawDomainService`) hold the core domain logic.
    *   They perform cross-aggregate logic (e.g., coordinating between events, queues, and orders).
    *   They interact directly with multiple repositories and infrastructure components to fulfill complex domain operations without leaking that complexity up to the orchestrators.

## Test Coverage

Per V2 specifications, we measure code coverage automatically for the Domain and Application layers using JaCoCo. The UI (Presentation) and Infrastructure layers are excluded.

To generate the code coverage report locally:
1. Run `mvn clean verify` or `mvn test` in the terminal.
2. After the tests finish, open `target/site/jacoco/index.html` in your web browser.

In the CI pipeline, the coverage report is generated automatically on every push to `main`/`develop` and PRs. It is uploaded as a build artifact named `jacoco-report`, which you can download directly from the GitHub Actions run summary.
