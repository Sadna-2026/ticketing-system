# INF-8 Workplan: ManagerPermission + Role-Based Domain Checks

## Scope
Implement and wire role-based permission checks for company staff actions, building on existing ManagerPermission and StaffAppointment models.

## Current State Snapshot
- Issue #9 is open.
- ManagerPermission enum exists.
- StaffAppointment.hasPermission(...) exists.
- Permission checks are not yet consistently enforced across domain/application flows.

## Execution Plan
1. Define permission-action matrix
- Map each company-admin action to required permission(s).
- Freeze names and semantics before wiring checks.

2. Identify enforcement points
- List service/domain methods that perform company-admin mutations.
- Mark where caller role and company context are available.

3. Add guard checks at domain boundary
- Enforce required permissions at the first trusted domain entry point.
- Return/throw consistent authorization failure signals.

4. Normalize owner behavior
- Ensure OWNER bypass policy is explicit and tested.
- Ensure MANAGER path requires explicit permission grants.

5. Add focused unit tests (easy wins first)
- Allowed case: manager with permission.
- Denied case: manager without permission.
- Allowed case: owner bypass.
- Null/missing permission input behavior.

6. Add integration-level checks
- Cover at least one end-to-end company-admin flow with denied/allowed outcomes.
- Verify no accidental privilege escalation.

7. Documentation + traceability
- Update issue checklist and link tests.
- Add llm_usage.md entry describing planning/assistance usage.

## Suggested Task Breakdown (PR-sized)
- PR 1: Permission-action matrix + test scaffolding.
- PR 2: Domain guard wiring for 1-2 high-impact actions.
- PR 3: Remaining guard wiring + integration tests + docs.

## Definition of Done
- All mapped admin actions enforce permissions consistently.
- Owner/manager behavior is explicit and validated by tests.
- New tests pass in CI.
- Issue #9 checklist is fully checked and references merged PRs.

## Risks and Mitigations
- Risk: scattered checks in application layer only.
  - Mitigation: enforce at domain boundary, not only controller/service edges.
- Risk: inconsistent failure behavior.
  - Mitigation: standardize authorization error contract.
- Risk: hidden dependency on authentication claims format.
  - Mitigation: keep domain checks independent of token parsing.
