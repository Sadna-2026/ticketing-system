# Lottery Flow — Demo & Testing Guide

## Overview

The lottery flow lets an event organizer run a ticket lottery instead of first-come-first-served sales.
Winners get a 48-hour exclusive window to pick their tickets before the event opens to the general public.

---

## Roles needed for the demo

| Role | What they do |
|------|-------------|
| **Owner / Manager** | Creates the lottery event, draws winners |
| **Member A** | Registers for the lottery, wins |
| **Member B** | Registers for the lottery, does not win (or is blocked) |
| **Guest** | Should be blocked from the lottery event until the winner window expires |

---

## Step-by-step demo

### Step 1 — Create a lottery event (as Owner)

1. Go to **Company** page → **Events** tab → click **"Design new venue / event"**
2. Fill in event details
3. In the **Sale method** dropdown, select **Lottery**
4. Set **Lottery registration open** to a time in the past (so registration is already open)
5. Set **Lottery registration close** to a time in the future (so members can still register)
6. Click **Save**
7. Publish the event

> **Result:** Event appears in the Events list with sale method = LOTTERY.

---

### Step 2 — Members register for the lottery

1. Log in as **Member A**
2. Go to **Events** page → find the lottery event
3. At the bottom of the event card, the lottery panel shows: zone picker + quantity + **"Enter lottery"** button
4. Select a zone, set quantity, click **Enter lottery**
5. You should see: *"You have been registered for the lottery."*

Repeat as **Member B** (different session/account).

> **Verification:**
> - The lottery panel shows **"You are registered for the lottery"** after registration
> - Trying to register again shows an error (no duplicates)
> - A guest sees *"Members only"* on the lottery panel

---

### Step 3 — Close the registration window (as Owner)

1. Go to **Company** page → **Events** tab → click the event → **Edit event**
2. Change **Lottery registration close** to a time in the past (e.g. one minute ago)
3. Click **Save lottery window**

> **Verification:** The lottery panel now shows **Registration: CLOSED**

---

### Step 4 — Draw lottery winners (as Owner)

1. Go to **Company** page → **Events** tab → scroll down to **"Lottery management"** section
2. Select **Lottery company name** → your company appears
3. Select **Lottery event** → only lottery events appear
4. Set **Maximum ticket capacity** (total tickets to allocate, e.g. `2`)
   - This is a ticket count, not a winner count. If Member A registered for 2 tickets and capacity is 2, they win and take the full capacity.
5. Click **Draw lottery**

> **Result:** Winner member IDs are shown.
> Each winner receives a notification: *"You have won the lottery! You have 48 hours to choose and purchase your tickets. Go to the Events page to select your tickets."*

---

### Step 5 — Winner claims tickets

1. Log in as the **winning member**
2. Go to **Orders** page

You should see:

```
Active order
──────────────────────────────────────────────────────
You won the lottery! Deadline: YYYY-MM-DD HH:MM.
Go to the Events page to choose your tickets.

[Go to Events to select tickets]   ← button
──────────────────────────────────────────────────────
(empty grid — no items yet)

Remove selected item  |  New GA qty  |  Update selected GA qty  |  Clear cart ❌
```

3. Click **"Go to Events to select tickets"** (or navigate manually to Events)
4. Find the same lottery event → select zone + quantity → click **Add GA tickets**
5. Return to **Orders** page

Banner now reads:
```
Lottery win order. Deadline: YYYY-MM-DD HH:MM.
You can modify your ticket selection but cannot cancel this order.
```

- **Remove selected item** → enabled (can change selection)
- **Update selected GA quantity** → enabled (can change quantity)
- **Clear cart** → disabled (cannot forfeit the entitlement)

6. Click **Checkout** → purchase completes normally

---

### Step 6 — Non-winner is blocked during the window

1. Log in as **Member B** (non-winner)
2. Go to **Events** → try to add tickets to the same lottery event
3. You should see:
   > *"Tickets for this event are allocated by lottery. Register for the lottery instead."*

> **This is correct.** The winner window is still active, so non-winners cannot buy.

---

### Step 7 — Window expires → event opens to all

After the 48-hour window passes (or you set `purchaseWindowDeadline` to the past in the DB for testing):

1. Any user (even guest) goes to Events → can add tickets normally
2. `findOrCreateLotteryOrder()` sees all LOTTERY_WIN windows expired → allows regular orders

---

## What to check at each stage

| Stage | What to verify |
|-------|---------------|
| Event created | Event shows LOTTERY in Company view |
| Registration open | Member can click "Enter lottery" on Events page |
| Registration closed | Lottery panel shows "Registration: CLOSED"; new registrations rejected |
| Before draw | Any attempt to buy tickets → blocked with "allocated by lottery" message |
| After draw (winner) | Orders page shows banner + "Go to Events" button; order is empty initially |
| Winner picks tickets | Tickets added from Events page appear in the lottery win order |
| Winner modifies | Remove/update quantity enabled; Clear cart disabled |
| Winner checks out | Purchase completes; order gone from Orders page |
| Non-winner during window | Blocked when trying to buy |
| Window expired | Everyone can buy; LOTTERY_WIN check passes |

---

## Key constraints enforced by the domain

- **No double-draw:** Calling draw a second time throws *"Lottery has already been drawn for this event."*
- **No pre-allocated tickets:** Draw creates an empty order — inventory is only locked when the winner picks tickets from Events.
- **No cancel:** Winner cannot clear their cart (it would lose their entitlement). They must either checkout or let the 48h expire.
- **Idempotent winner state:** If the winner's 48h window expires before checkout, the domain releases any locked inventory and blocks further access.
- **One lottery order per winner:** After checkout, the order becomes a purchase record. The winner cannot create a second lottery order during the window.

---

## How to run locally (in-memory mode)

```powershell
mvn spring-boot:run
```

No environment variables needed. The app starts with:
- In-memory repositories (`TICKETING_PERSISTENCE=memory` by default) — no database required
- Demo seed data pre-loaded (`seed.enabled=true`)
- H2 console available at `http://localhost:8080/h2-console` (not needed in memory mode)
- UI at `http://localhost:8080`

The full lottery flow works in memory mode — `LotteryEntry`, draw, and `LOTTERY_WIN` orders all operate on the same in-memory domain objects.

---

## Shortcut for testing expiry (in-memory mode)

You cannot directly edit the in-memory state via SQL. To test the window expiry without waiting 48 hours:

- When drawing winners (Step 4), note the `purchaseWindowDeadline` returned is `now + 48h`
- Instead, set a very short deadline by temporarily changing the constant in `EventService.drawLottery()`:

```java
// Temporary test-only change — revert after testing
Instant purchaseWindowDeadline = systemClock.now().plus(Duration.ofMinutes(1));
```

Restart, run the full flow, then wait 1 minute. Refresh the Orders page as winner → *"Your lottery purchase window has expired."* Then as a regular buyer → can buy normally.

Revert the constant to `Duration.ofHours(48)` after testing.
