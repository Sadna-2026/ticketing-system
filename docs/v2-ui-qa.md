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
| System admin seed | `admin` | `admin123` | Seeded as a member and in the admin repository. Admin-session login wiring is handled separately. |
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

### Policy Failure

1. Log in as `teen` / `teen123`.
2. In `/orders`, create an order for event `22222222-2222-2222-2222-222222222222`.
3. Load inventory, add GA ticket(s), checkout.
4. Verify the age-policy message is shown from the domain/application result:
   `You must be at least 18 years old to purchase tickets for this event`.

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
2. After the admin-login branch is merged, log in through that flow.
3. Verify `Admin` navigation is visible.
4. Open `/admin`.
5. Load global purchase history.
6. Try suspension/removal inputs using member UUIDs from the seed if needed.

## Known UI Gap To Record

Vaadin still does not provide real purchase/discount policy management controls. The domain/application layer has policy APIs, but the UI only exposes a placeholder/gap note. If V2-INF-4.5 is QA-only, create a follow-up UI task for policy management controls instead of implementing it in this branch.
