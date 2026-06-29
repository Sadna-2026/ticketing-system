# V2 UI Manual QA

This branch seeds a small in-memory dataset so V2-INF-4.5 can be tested from the Vaadin UI.

## Run

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

The app uses in-memory repositories. Restarting the server resets the data and loads the seed again.

To disable seed data:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--ticketing.seed.enabled=false"
```

## Seed Accounts

All users are created on startup when `ticketing.seed.enabled=true`.

| Role | Username | Password | Notes |
|---|---|---|---|
| System admin seed | `admin` | `admin123` | Seeded as a member and in the admin repository. Log in via the **Admin username/password** form on `/auth`. |
| Member | `member` | `member123` | Adult buyer for successful purchase flow. |
| Owner | `owner` | `owner123` | Owner/founder of `Demo Productions`. |
| Manager | `manager` | `manager123` | Manager with only `VIEW_REPORTS`. Useful for permission-denied checks. |
| Under-age member | `teen` | `teen123` | Date of birth is 2012-01-01. Useful for age-policy failure checks. |
| Inventory manager | `inventory` | `inventory123` | Manager for `Demo Productions` with map, inventory, event lifecycle, policy modification, and reports permissions. |
| Second owner | `owner2` | `owner2123` | Owner/founder of `Northwind Events`. |

## Seed Data

| Item | Value |
|---|---|
| Company | `Demo Productions` |
| Second company | `Northwind Events` |
| Normal event ID | `11111111-1111-1111-1111-111111111111` |
| Normal GA zone ID | `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa` |
| Age-policy event ID | `22222222-2222-2222-2222-222222222222` |
| Age-policy GA zone ID | `bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb` |
| Assigned-seat event ID | `33333333-3333-3333-3333-333333333333` |
| Assigned-seat zone ID | `cccccccc-cccc-cccc-cccc-cccccccccccc` |
| Assigned seat IDs | `eeeeeeee-0000-0000-0000-000000000001`, `eeeeeeee-0000-0000-0000-000000000002`, `eeeeeeee-0000-0000-0000-000000000003` |
| Second-company conference event ID | `44444444-4444-4444-4444-444444444444` |
| Conference GA zone ID | `dddddddd-dddd-dddd-dddd-dddddddddddd` |
| Mixed limited event ID | `88888888-8888-8888-8888-888888888888` |
| Mixed limited seat zone ID | `88888888-0000-0000-0000-0000000000a1` |
| Mixed limited GA zone ID | `88888888-0000-0000-0000-0000000000a2` |
| No-orphan seat event ID | `aabbccdd-aabb-aabb-aabb-aabbccddeeff` |
| No-orphan seat zone ID | `aabbccdd-0000-0000-0000-0000000000a1` |

## Test Checklist

### Guest

1. Open `/auth`.
2. Click `Enter as guest`.
3. Verify navigation hides `Company` and `Admin`.
4. Open `/events` and search with empty filters.
5. Verify seeded published events appear.
6. Open `/orders`, use the normal event ID, create an order, load inventory, add GA tickets.
7. Checkout as guest should fail if a member session is required for the selected flow; the UI should show the application/domain message.

### Member

1. Start a guest session, then log in as `member` / `member123`.
2. Verify `Company` appears and `Admin` stays hidden.
3. In `/orders`, create an order for event `11111111-1111-1111-1111-111111111111`.
4. Load inventory, add GA ticket(s), checkout.
5. Verify success message and purchase history.
6. Repeat with assigned-seat event `33333333-3333-3333-3333-333333333333` and one of the assigned seat IDs to verify assigned-seat selection.
7. Open `/orders`, create an order for Mixed Limited Event `88888888-8888-8888-8888-888888888888`. Add 1 assigned seat and 3 GA tickets. The system should block the addition of the 4th ticket due to the `MaxQuantityPolicy(3)`.

### Policy Failure

1. Log in as `teen` / `teen123`.
2. On `/events`, search for `18+ Policy Test Event`, load its map, and try to add GA ticket(s).
3. Verify the age-policy message is shown from the domain/application result when adding tickets:
   `You must be at least 18 years old to purchase tickets for this event`.
4. Checkout stays disabled because no tickets were added to the cart.

### No-Orphan Seat Policy

1. Log in as `member` / `member123` (or use a guest session).
2. On `/orders`, create an order for the No-Orphan Seat Demo event `aabbccdd-aabb-aabb-aabb-aabbccddeeff`.
3. Load inventory and try selecting a single middle seat (e.g. A-2). The system should **reject** the reservation because it leaves seat A-1 isolated at the edge.
4. Select seat A-1, then A-2. Both should succeed because the remaining seats (A-3, A-4, A-5) are adjacent.
5. Verify the error message mentions the isolated seat (e.g. `Your selection would leave seat A-1 isolated`).

### Owner

1. Log in as `owner` / `owner123`.
2. Verify `Company` is visible.
3. In `/company`, load company info for `Demo Productions`.
4. Verify owner/company actions are visible.
5. Try event/company actions and verify success or backend error messages are displayed clearly.

### Manager

1. Log in as `manager` / `manager123`.
2. Verify `Company` is visible.
3. In `/company`, load reports/history for `Demo Productions`.
4. Try actions outside `VIEW_REPORTS`, such as event creation or policy-related actions.
5. Verify denial messages are displayed clearly and actions do not silently succeed.

### Inventory Manager

1. Log in as `inventory` / `inventory123`.
2. Verify `Company` is visible and `Admin` is hidden.
3. In `/company`, use `Demo Productions`.
4. Try event, inventory, lifecycle, policy, and report-related actions.
5. Verify permitted manager actions succeed or reach backend validation, while system-admin actions remain hidden.

### Second Company

1. Log in as `owner2` / `owner2123`.
2. In `/company`, load company info for `Northwind Events`.
3. In `/events`, search by company `Northwind Events`.
4. Verify `Northwind Tech Summit` appears and `Demo Productions` events are not mixed into the company-filtered result.

### Admin

1. Verify admin-only actions are hidden for guest/member/owner/manager accounts.
2. On `/auth`, log in with the **Admin username/password** form as `admin` / `admin123`.
3. Verify `Admin` navigation is visible.
4. Open `/admin`.
5. Load global purchase history.
6. Try suspension/removal inputs using member UUIDs from the seed if needed.

### Coupon Edge Cases

These scenarios use the specialized demo events created in `DevSeedDataInitializer`. Go to the `/events` page and add tickets to the respective event's cart to test:

1. **Valid, Unknown, and Removed Coupon:** Use the `Coupon Checkout Demo` event. 
   - Apply `SAVE20` to verify a 20% discount. 
   - Apply `BOGUS` or `###` to verify the "Invalid coupon code" error message.
   - Click "Remove coupon" to verify the price restores to the original amount.
2. **Expired Coupon:** Use the `Coupon Expired Demo` event. 
   - Apply `EXPIRED` to verify the specific "Coupon code has expired" message.
3. **100% Free Checkout:** Use the `Coupon Free Demo (100%)` event.
   - Apply `FREE` to drop the total to $0.00 and successfully checkout without a gateway charge.
4. **Stacked / Composite Discounts:** Use the `Coupon Stacked Demo (SumComposite)` event.
   - The event has a base 10% discount. Apply `EXTRA` to stack an additional 20% coupon, verifying a total 30% reduction.


## V2-INF-4.5 QA Findings

Evidence gathered for issue #193 by reading the Vaadin presentation layer
(`src/main/java/com/ticketing/presentation/vaadin/`) and exercising the flows above. Each
acceptance criterion was verified against the current code.

### Role visibility (AC 1)

Navigation links and route access are gated by session role — `MainLayout` shows/hides nav
links, and views guard access:

| Role | Adds to navigation | Guarding |
|---|---|---|
| No session | — (redirected to `/auth`) | `AuthNavigationGuard` forwards any route without a session to `/auth`. |
| Guest session | Home, Events, Orders, Notifications | Member-only pages still guard on entry (below). |
| Member | + Profile, Company | — |
| System admin | + Admin | Admin controls hidden + guarded for non-admins. |

Full-page member-only views guard on entry with a forward-to-Home + toast (consistent pattern):

- `/notifications` → `NotificationsView.beforeEnter`
- `/profile` → `MemberView.beforeEnter` (**added in this issue** so it matches `/notifications`;
  previously it only rendered an inline "you must be logged in" message)

Mixed-role views gate per section rather than per route:

- `/company` — `CompanyView` shows/hides each tab and its controls by manager permission
  (events, inventory, reports, policies, lifecycle, personnel); a denied section shows a
  permission message instead of the controls.
- `/admin` — `AdminView` hides the admin tabs/controls and shows an admin-only hint for
  non-admin sessions.

### Action feedback (AC 2)

Every user action reports its outcome through `UiMessages` (success **and** failure toasts),
and most also update an on-page status `Span`. Verified across Auth, Events, Orders, Company,
Admin, Notifications, and Profile actions.

### Error message sourcing (AC 3)

Presenters surface the application/domain message **verbatim** rather than inventing a business
reason. The shared pattern is a `userMessage(ex, fallback)` helper (e.g. `OrdersPresenter`):
known validation exceptions (`IllegalArgumentException` / `IllegalStateException` /
`SecurityException`) pass their message through to the UI; unexpected exceptions fall back to a
safe generic message and are logged.

Age-policy failure renders the domain message verbatim (matches the spec example):

```text
You must be at least 18 years old to purchase tickets for this event
```

Permission denials in `CompanyView` are shown with a UI-composed, context-rich message, e.g.:

```text
User "manager" doesn't have MANAGE_EVENTS permission for Demo Productions.
```

This summarises the backend `CompanyAccessResult` for the user — it states *which* permission is
missing rather than inventing a business explanation, so it is consistent with req 3.5.

### Generic backend/application messages (recorded)

These appear only when an unexpected exception carries no user-facing message; specific domain
validation messages are preserved on the normal paths. Recorded here so they can be made more
specific if the application layer starts wrapping more failures in typed messages:

- `AuthPresenter`: "Could not start guest session.", "Login failed. Please try again.",
  "Registration failed. Please try again.", "Logout failed. Please try again.",
  "Could not exit guest session."
- `OrdersPresenter`: "Could not load active order. Please try again.",
  "Could not add GA tickets. Please try again.", "Could not checkout. Please try again."

### Password fields

All password inputs use Vaadin `PasswordField` (not `TextField`): member login, member
registration, and admin login (all in `AuthView`).

### Result

Acceptance criteria 1–3 are met. The single role-visibility inconsistency found (the profile
page guarded with an inline message instead of a redirect) was fixed in this issue. No UI
inventions of business explanations were found. Purchase/discount **policy management controls
now exist** in the `CompanyView` Policies tab (load/set/remove for purchase and discount
policies), so the earlier "no policy UI" gap is closed.
