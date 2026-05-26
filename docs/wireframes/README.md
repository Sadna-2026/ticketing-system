# UI Wireframes (V1-B / INF-11)

Each screen lives here as a `.png` or `.pdf` file alongside this README.

## Files (12 screens — exceeds the 10+ minimum)

| # | File | Screen | Primary actor |
|---|---|---|---|
| 1 | `01-login-register.png` | Login / Register | Guest |
| 2 | `02-browse-events.png` | Browse events (list view) | Guest / Member |
| 3 | `03-event-detail.png` | Event detail page | Guest / Member |
| 4 | `04-venue-map-selection.png` | Venue map + ticket selection | Guest / Member |
| 5 | `05-active-cart.png` | Active order / cart | Guest / Member |
| 6 | `06-checkout-payment.png` | Checkout / payment | Guest / Member |
| 7 | `07-purchase-confirmation.png` | Purchase confirmation | Guest / Member |
| 8 | `08-owner-dashboard.png` | Owner dashboard | Owner / Founder |
| 9 | `09-manager-dashboard.png` | Manager dashboard | Manager |
| 10 | `10-admin-dashboard.png` | Admin dashboard | System Admin |
| 11 | `11-queue-waiting.png` | Queue waiting screen | Guest / Member |
| 12 | `12-lottery-registration.png` | Lottery registration | Member |

## V2 implementation note

The Vaadin `/notifications` route added in V2 is intentionally outside the V1 wireframe set above. It presents a member notification panel with refresh/clear actions and real-time toast delivery for connected users.

## What each screen needs to show

Detailed layout briefs — use these as the spec when drawing.

### 1. Login / Register
- Logo top-left, page title "Sign in to Ticketing"
- Two-tab toggle: **Sign In** (default) and **Register**
- Sign In: username + password, "Forgot password?" link, primary CTA "Sign In"
- Register: username, email, password, confirm password, primary CTA "Create account"
- Footer link: "Continue as guest" → routes to Browse events

### 2. Browse events (list view)
- Top bar: logo, search box (placeholder: "Search by name, artist, category"), filters dropdown (region, date range, price range, category), user avatar / Sign in
- Left column: filter chips (cleared individually)
- Main grid: event cards (image placeholder, name, date, region, "From $X" price, button "View")
- Footer: pagination

### 3. Event detail page
- Hero image placeholder + event name, artist, date/time, region
- "Get tickets" primary CTA → routes to venue map
- Description block, company info card (clickable → company page)
- Bottom: "Other events by this company" carousel

### 4. Venue map + ticket selection
- Top: event name + date breadcrumb
- Center-left: visual venue layout (sections labelled, color-coded by zone)
- Center-right: zone legend, price per zone
- Selected seats / GA quantity panel on the right with running total
- Bottom: "Continue to checkout" CTA (disabled until at least one selection)

### 5. Active order / cart
- Heading: "Your selection (lock expires in MM:SS)" — countdown timer prominent
- List: each item shows event, zone, seat or qty, unit price, subtotal
- Remove button per row
- Right side: order summary (total, taxes if any), CTAs: "Continue shopping", "Proceed to checkout"

### 6. Checkout / payment
- Step indicator (1 Cart · 2 Details · 3 Payment · 4 Confirmation) with step 3 highlighted
- Buyer details form: name, email, phone (pre-filled if member)
- Payment: card number, expiry, CVV, cardholder name (note: V1 uses stub gateway)
- Order summary on the right (items + total)
- Primary CTA "Pay $X.XX"
- Lock-timer banner at top (warning if <2 min left)

### 7. Purchase confirmation
- Big check icon, "Order confirmed!" headline
- Order number prominent, list of purchased tickets, total paid
- "Tickets will be sent to <email>" text
- CTAs: "View order details", "Browse more events"

### 8. Owner dashboard
- Sidebar: Events, Inventory, Staff, Reports, Company settings
- Top metrics row: tickets sold (count), revenue ($), upcoming events (count)
- Center: events table (name, date, status, sold/total, actions: edit / cancel)
- Right rail: recent activity feed

### 9. Manager dashboard
- Same layout as Owner but limited menu — visible items reflect the manager's permissions (e.g. only Inventory + Reports if those are granted)
- Top row: notification "Permissions: MAP_DEFINITION, INVENTORY_MGMT" pill chips

### 10. Admin dashboard
- Sidebar: Companies, Members, System metrics, Audit log
- Center: companies table (name, founder, status, founded date, actions: suspend / force-close)
- Right rail: system health (active sessions, payment gateway status)

### 11. Queue waiting screen
- Centered hero: "You're #234 in line — estimated wait 4 minutes"
- Animated spinner (placeholder rectangle in static)
- Note: "Please don't refresh — you'll lose your place"
- Position updates every few seconds (annotation: "auto-refreshes")

### 12. Lottery registration
- Event banner at top
- "This event uses a lottery for ticket allocation" explanation
- Registration form: which zone, how many tickets desired
- Date the lottery draws
- CTA "Enter the lottery"
- Below: "Already entered" status if applicable


