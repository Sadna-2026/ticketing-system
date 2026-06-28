# ADR 0001 — Locking strategy: optimistic via JPA `@Version`

- **Status**: Accepted
- **Date**: 2026-06-28
- **Issue**: [#511](https://github.com/Sadna-2026/ticketing-system/issues/511)
- **Related issues**: [#508](https://github.com/Sadna-2026/ticketing-system/issues/508), [#509](https://github.com/Sadna-2026/ticketing-system/issues/509), [#510](https://github.com/Sadna-2026/ticketing-system/issues/510)

## Context

An advisor raised the question of whether the system should move from optimistic to pessimistic locking. The General Requirements (§1, *Consistency & Concurrency*) demand hermetic prevention of double-sell on the seat checkout path, while V1 explicitly says concurrency tests must pass *regardless of the implementation's locking choice*. Graders ask "why this strategy" — this note records the answer.

The platform is built on Spring Boot 3.3 + Spring Data JPA, runs on H2 in dev/test and is provisioned for PostgreSQL on Google Cloud SQL `db-f1-micro` in production (see [deploy-cloud-sql.md](../deploy-cloud-sql.md)). The DB is *remote* in production, so the round-trip cost of every `SELECT ... FOR UPDATE` would compound on every read.

## Decision

**Optimistic concurrency via Hibernate `@Version` on every writable aggregate (and its mutable child entities).** No `@Lock(LockModeType.PESSIMISTIC_*)` and no `SELECT ... FOR UPDATE`. Conflicts surface as `OptimisticLockException`, are translated by the application services into a user-actionable "please retry" `IllegalStateException`, and the caller (UI presenter or REST controller) decides whether to re-issue the use-case.

## Per-aggregate justification

| Aggregate | `@Version` location | Why optimistic is right here |
|---|---|---|
| **Event** (root) | [Event.java](../../src/main/java/com/ticketing/domain/event/Event.java) | Event metadata edits (name, description, lottery window) are rare and owner-driven. Hot mutation is on children below. |
| **InventoryZone** (child) | [InventoryZone.java](../../src/main/java/com/ticketing/domain/event/InventoryZone.java) | Own version so GA-zone decrement conflicts (last-ticket race) abort the loser without dirtying the Event root. |
| **Seat** (child) | [Seat.java:56](../../src/main/java/com/ticketing/domain/event/Seat.java) | Own version + `AtomicReference` CAS. Two orders claiming the same seat produce one winner; the loser's flush is rejected by the versioned `UPDATE` (no double-sell). Pessimistic on the parent Event would serialize *unrelated* zones in the same Event, killing throughput at gate-open. |
| **ActiveOrder** (root) | [ActiveOrder.java](../../src/main/java/com/ticketing/domain/order/ActiveOrder.java) | A single user owns their order; concurrent edits only happen if the same user has two tabs open. Retry on conflict is acceptable UX. |
| **Member** (root) | [Member.java](../../src/main/java/com/ticketing/domain/member/Member.java) | Profile edits are infrequent and self-driven. Staff-appointment changes are admin-driven and low-volume. |
| **Company** (root) | [Company.java](../../src/main/java/com/ticketing/domain/company/Company.java) | Settings/policy edits are owner-driven; multi-owner concurrent edits are rare. |
| **LotteryEntry** (root) | [LotteryEntry.java](../../src/main/java/com/ticketing/domain/lottery/LotteryEntry.java) | Effectively immutable post-creation; `@Version` is defensive. |
| **Admin** (root) | [Admin.java](../../src/main/java/com/ticketing/domain/admin/Admin.java) | Rare writes, no contention. |

Immutable snapshot entities (`CompletedPurchase`, `OrderItem` when read-through-root) do not need `@Version`; they are append-once and never mutated.

`VirtualQueue` is **transient by design** (V3 spec: "do not persist ephemeral values such as waiting queues"). It lives in `InMemoryQueueRepository` only and uses `ConcurrentHashMap` + per-event locks. No DB-level locking applies.

## Why not pessimistic

1. **Remote-DB round-trip cost.** The graded production target is a remote `db-f1-micro`. `SELECT ... FOR UPDATE` on every aggregate read adds a network round-trip *and* holds row locks for the lifetime of the transaction. The seat-claim path already touches Event + InventoryZone + Seat + ActiveOrder + OrderItem; pessimistic locking would multiply latency across all of them.
2. **Hot-row contention amplification.** Pessimistic locking serializes whole branches of the aggregate tree. Two users picking *different* seats in the same Event would block each other on the Event-row lock instead of running in parallel.
3. **Lock leaks on crash.** A pessimistic lock held by a dead app instance only releases when the DB times the connection out. Optimistic conflicts cost nothing on app crash — the stale snapshot just gets garbage-collected.
4. **V3 *Persistency-Robustness* spec** mandates auto-recovery after DB disconnects. Optimistic locking has no recovery debt: the next attempt simply re-reads the current version. Pessimistic locks held across a connection drop would orphan rows until DB-side timeout.
5. **The existing test suite already proves the invariant.** [SeatReservationLockingJpaTest](../../src/test/java/com/ticketing/application/transactions/SeatReservationLockingJpaTest.java) demonstrates that even under N-thread contention on the same seat, exactly one writer wins. V1 spec point 6a explicitly accepts either locking strategy; current evidence confirms optimistic is sufficient.

## Retry policy

The retry decision is **explicit, not automatic**:

- Application services translate `OptimisticLockException` into a domain-level `IllegalStateException` with a "please retry" message ([OrderService.java:1351](../../src/main/java/com/ticketing/application/services/OrderService.java#L1351), and likewise for orders and queues).
- The presenter / REST controller surfaces this to the user and lets them retry. Auto-retry was rejected because (a) it can hide programmer errors that look like conflicts and (b) it can cascade into retry storms during a real hot-row contention spike.

## Consequences

- **Positive**: Maximum read parallelism, zero pessimistic-lock overhead in the steady state, no lock-leak risk on crash, clean recovery story for V3 robustness, low cognitive overhead (the only knob is `@Version`).
- **Negative**: A user who hits a genuine conflict sees an error and must retry. For the seat-claim path under extreme spike this is acceptable — the queue mechanism (V1 §I.7) throttles arrivals into batches so contention stays bounded.

## Verification

The choice is exercised end-to-end by:
- [SeatReservationLockingJpaTest](../../src/test/java/com/ticketing/application/transactions/SeatReservationLockingJpaTest.java) — same-seat and last-GA races (V3-11 #269).
- [CompanyServiceConcurrencyTest](../../src/test/java/com/ticketing/application/concurrency/CompanyServiceConcurrencyTest.java) — concurrent company create.
- [GlobalRaceConditionTest](../../src/test/java/com/ticketing/concurrency/GlobalRaceConditionTest.java) — concurrent member register.
- [QueueConcurrencyTest](../../src/test/java/com/ticketing/application/QueueConcurrencyTest.java) — admit/leave races on the transient queue.

New tests added under issues [#508](https://github.com/Sadna-2026/ticketing-system/issues/508) and [#509](https://github.com/Sadna-2026/ticketing-system/issues/509) further widen this coverage.

## Revisit triggers

Reopen this decision if any of the following happens:
- Production load testing shows >5% of seat-claim requests failing with `OptimisticLockException` after a single retry.
- DB latency becomes negligible (e.g. co-located DB) AND a future feature needs serialized cross-aggregate invariants that optimistic locking cannot express.
- A future requirement asks for read-your-writes guarantees on a hot mutable row that the version mechanism cannot satisfy.
