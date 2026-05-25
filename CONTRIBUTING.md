
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

## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.

## Notifications Architecture (V1 vs V2)
As per V1 specifications (#UC-I.5 and #UC-I.6), full real-time and delayed notification delivery is **deferred to V2**.
*   **Real-Time:** Services should use the INotificationService interface in the application layer. In V1, this is bound to a no-op StubNotificationService that simply logs the intent.
*   **Delayed/Pending:** Services should reference IPendingNotificationRepository if they need to queue a message for an offline user. In V1, the StubPendingNotificationRepository sinks these calls. In V2, this will be replaced with a database-backed repository to deliver messages upon the user's next login.

## Policy Editing Architecture (V1 vs V2)
Per V1 spec (UC-II.4.3 / UC-C.2), the **full purchase/discount policy edit API is deferred to V2**. V1 ships only:
*   `IPurchasePolicy` + `IDiscountPolicy` abstractions (delivered by INF-9).
*   `AlwaysAllowPolicy` (purchase) and `NoDiscountPolicy` (discount) defaults wired into every `Event` at construction time.
*   No setter on `Event` for the policy fields — callers cannot edit them in V1.
The V0 acceptance tests for policy editing (`SuccessfulDefaultPolicyEdit`, etc.) are present in the test suite under `@Disabled("V1 spec defers UC-II.4.3 — full policy edit lands in V2")`.
