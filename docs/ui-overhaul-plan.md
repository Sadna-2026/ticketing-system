# UI Overhaul — Plan & Design System

Living document for the full visual overhaul of the ticketing system. Goal: replace the
stock Vaadin **Lumo** look with a distinctive branded identity — **without changing any
functionality**. New features should adopt this design system as they land.

- **Branch:** `ui/ux-tabs-sections` (stacked PRs off it; clean diffs vs `develop`)
- **Direction:** Waveform / Soundstage — "sound made visible", dark-first with an **optional light mode**
- **Scope:** Theme everywhere **+ targeted UX fixes** to the worst structural offenders (no flow/behavior changes)
- **Approved mockup:** `/tmp/ui-mockups/waveform.html` (refined v2 — waveform restrained to hero + seat map)

---

## 1. Where we're starting (audit summary)

100% stock Lumo, confirmed across all surfaces:

- **No custom theme** (no `theme.json`, no `@Theme`). Only a 20-line `required-fields.css`.
- **Ad-hoc styling:** ~38 inline `getStyle().set(...)`, **0** CSS classes, 8 scattered theme-variant
  calls, 14 hardcoded hex colors (seat/venue), 56 Lumo CSS vars referenced.
- **Structural UX problems:** auth stacks 3 forms vertically; Company crams 8 tabs in one row;
  Admin has no dashboard; plain grids; no cards/elevation; paragraph-heavy copy.
- **Scope is tractable:** ~24 files / ~8.4K LOC in `presentation/vaadin`, loosely coupled to Lumo.
  The one custom component (`seat-map.js`, Lit) is already token-driven.

Surfaces: `HomeView` `/`, `AuthView` `/auth`, `EventsView` `/events`, `OrdersView` `/orders`,
`MemberView` `/profile`, `CompanyView` `/company`, `AdminView` `/admin`, `NotificationsView`
`/notifications`, `VenueDesignerDialog` (modal), `MainLayout` (shell), components
`SeatMapComponent` + `PolicyBadgesPanel`.

---

## 2. Design tokens

Implemented as a custom Vaadin theme `showpass` that overrides Lumo CSS custom properties.
**Dark is the default**; light mode is a `theme="light"` variant. Never use raw hex in views —
consume these tokens (via theme classes / Lumo vars).

### Brand
| Token | Dark | Light | Use |
|---|---|---|---|
| `--app-base` | `#0E0E11` | `#FBFAF7` | app background |
| `--app-surface` | `#17171C` | `#FFFFFF` | cards / panels |
| `--app-surface-2` | `#1F1F26` | `#F3F1EC` | raised / hover |
| `--app-gold` (primary) | `#C8A24B` | `#A1670E`* | primary action / signature |
| `--app-violet` (secondary) | `#8B5CF6` | `#6D5BD0` | secondary accent / spotlight |
| `--app-text` | `#ECEAE6` | `#1A1714` | primary text |
| `--app-text-muted` | `#A09C95` | `#6B6675` | secondary text |
| `--app-border` | `rgba(255,255,255,.08)` | `#ECE9E3` | borders / dividers |
| `--app-success` | `#46C277` | `#2E9E5B` | confirmed / available |
| `--app-danger` | `#F0616D` | `#DC3545` | destructive / errors |
| `--app-pending` | `#C8A24B` | `#B7791F` | pending / warning |

\* Gold must darken on light backgrounds to keep ≥4.5:1 text contrast. Verify both themes.

These map onto Lumo vars (`--lumo-primary-color`, `--lumo-base-color`, `--lumo-contrast-*`,
`--lumo-error-color`, `--lumo-success-color`, etc.) so stock components inherit the brand.

### Typography
- **Display / headings / wordmark:** Outfit (600/700)
- **Body / data / forms:** Inter (400/500)
- **Numerals** (prices, dates, seat numbers, totals): `font-variant-numeric: tabular-nums`
- Load with `font-display: swap`; preload only the two critical weights.

### Spacing / radius / elevation
- Reuse Lumo's 4/8px spacing scale; standardize section rhythm 16 / 24 / 32 / 48.
- **Crisp, engineered geometry** (console / rack-gear feel — NOT soft SaaS blobs). Tight radius scale:
  panels/cards/inputs/buttons `--lumo-border-radius-m` ≈ **4–5px**, badges/seat pads **3px**. Round
  (`50%`/pill) is reserved for things that are *semantically* round: avatars, status LEDs, seat-row dots.
- **Flat elevation:** rely on crisp 1px borders/seams, not big blurry drop shadows. Shadows are tight
  and low-spread; the one soft, glowing element is the waveform / EQ stage bar.

### Signature
The **seat map emerges from darkness**: taken seats sink toward `--app-base`, available seats are
quiet raised chips, **selected seats are lit with a layered gold glow**. Spotlight radial-gradient
(gold→violet) behind page headers. Boldness lives here — everything else stays calm.

---

## 2b. Anti-"AI-slop" guardrails (non-negotiable)

The `frontend-design` skill flags three generic AI-default looks. **Ours (near-black + accent) is
literally one of them**, so we differentiate deliberately. Apply on every screen:

- **Two-accent brand, not one bright dot.** Gold *and* violet working together (warm metal + cool
  light), never a single neon accent floating on black. No acid-green/vermilion clichés.
- **Spend boldness in ONE place** — the lit seat map / spotlight. Everything else stays quiet
  (hairline borders, restrained type, calm surfaces). If a second thing is shouting, mute it.
- **Earn the gradient.** The cyan→magenta wash is a motivated signal behind the hero waveform / EQ
  seat map — not decorative blobs scattered around. No random gradient cards, no glassmorphism everywhere.
- **No uniform fat radii / soft-blob look.** Big `rounded-2xl`-on-everything + soft drop shadows is a
  templated AI tell and fights the engineered concept. Use the crisp radius scale above; round only
  what is semantically round.
- **Don't repeat the signature.** The waveform belongs to the hero + the seat map ONLY — never on card
  footers, section dividers, or the logo (that reads as "too much").
- **Typography does work.** Outfit display with intentional weight/size/tracking and a real type
  scale — not 16px Inter everywhere. Tabular numerals on all data. The wordmark is a designed mark.
- **No template hero.** Avoid the big-number-+-label-+-gradient-blob hero. The hero is the product
  (events / the spotlight on the seats), grounded in the live-events world.
- **Structure encodes meaning.** No decorative 01/02/03 numbering, fake "trusted by" logos, or
  filler copy. Labels label; copy helps the user act (see writing notes below).
- **Real, specific copy.** Plain active-voice microcopy ("Pick your seats", "Checkout · $378.50"),
  sentence case, no marketing filler. Empty/error states give direction, not mood.
- **Restraint check (Chanel rule).** Before shipping a screen, remove one decoration. Screenshot in
  both themes and ask: does this look *chosen for a ticketing app*, or like a generic dark dashboard?

## 3. Component patterns (apply consistently; new features must follow)

- **Buttons** — semantic variants: Primary (gold), Secondary (neutral surface), Tertiary/Ghost,
  Danger (red). One primary CTA per screen; destructive visually separated.
- **Cards / sections** — group related controls in surface cards with a header; replaces bare
  stacked `VerticalLayout`s.
- **Grids** — hairline rows, distinct sticky-feeling header, hover state, right-aligned tabular
  numbers, **status badges** (Confirmed/Pending/Refunded, Active/Revoked) instead of raw booleans.
- **Forms** — visible labels, helper text, inline validation on blur, error below field, required
  asterisk (keep), grouped fieldsets. Show/hide on password fields.
- **Dialogs** — consistent header/footer + button order `[Cancel] … [Primary]`; destructive =
  danger color. (`DestructiveActionDialogs` already centralizes this.)
- **Toasts** (`UiMessages`) — branded success/error/info, `aria-live`, auto-dismiss 3–5s.
- **Empty / loading states** — helpful copy + action; skeletons for >300ms loads.
- **Icons** — one stroke icon set (Lucide-style), never emoji.

---

## 4. Phased plan

Each phase is its own stacked PR (small, reviewable). Functionality unchanged throughout.

**Phase 1 — Theme foundation** *(no visual redesign yet, just the system)* — ✅ core done
- ✅ Create `src/main/frontend/themes/showpass/` → `theme.json`, `styles.css`. (NOTE: theme MUST live
  under `src/main/frontend/themes/`, not `META-INF/resources/themes/` — the latter isn't scanned for
  app themes in dev mode. `.gitignore` updated to track `frontend/themes/` while still ignoring
  `frontend/generated/` + `index.html`.)
- ✅ `@Theme(value="showpass", variant=Lumo.DARK)` on `TicketingApplication`; folded in + deleted
  `required-fields.css`; added Sora+Inter font links.
- ✅ Tokens (dark default + light variant) + fonts + crisp radii. **Specificity gotcha:** Lumo→brand
  var mappings must be under `html, html[theme~="dark"]` to beat Lumo's own dark-variant rules.
- ✅ Verified both themes render in the running app (dark = cyan accent, light = teal accent).
- ⏳ Light/dark **toggle** control → moved to Phase 2 (built into the redesigned navbar, so it's done
  once). Theme switches today by toggling the `theme="dark"` attribute on `<html>`.
- ⏳ Migrate the 38 inline styles + 14 hardcoded hex + `seat-map.js` colors → tokens (per-surface, in
  Phase 3 as each view is touched).

**Phase 2 — Global shell** — ✅ core done
- ✅ `MainLayout`: branded wordmark (cyan→magenta EQ mark + name in Sora), restyled nav, working
  **light/dark toggle** (persisted to localStorage). Styling via theme classes, no inline styles.
- ✅ Component patterns: `components/vaadin-button.css` (primary=cyan / secondary / ghost / danger
  hierarchy), `components/vaadin-grid.css` (uppercase Sora header, hairline rows, cyan hover/selected).
- ✅ Reusable helper classes in `styles.css`: `.app-card`, `.app-section-title`, `.app-badge--*`.
- ⏳ Applying primary-variant to the right CTAs, and `.app-card`/`.app-badge` usage, happens
  per-surface in Phase 3. (Mobile drawer/hamburger + user-menu chip also Phase 3/later.)

**Phase 3 — Per-surface (one PR each)**
- **3a Auth + Home** — auth 3 stacked forms → **centered card with tab switcher** (login/register/
  guest/admin), prominent guest path; Home → spotlight hero + quick-action cards.
- **3b Events + seat selection** — event cards grid; **lit seat map** (restyle `SeatMapComponent` +
  `seat-map.js` for both themes); filter bar; `PolicyBadgesPanel` restyle; cart summary panel.
- **3c Orders** — active order/cart panel, checkout polish, history table w/ status badges,
  currency + tabular price formatting.
- **3d Company** — 8-tab row → **side-nav**; group controls into cards; restyle `VenueDesignerDialog`
  + org chart.
- **3e Admin** — **KPI summary cards** dashboard; status badges (suspended/revoked); table polish.
- **3f Notifications + Profile** — card layouts, empty/loading states.

**Phase 4 — Motion & polish** *(tasteful, non-disruptive only)*
- Route/page-load transition, card hover lift, seat-selection glow, staggered grid reveal,
  skeleton loaders. 150–300ms, transform/opacity only, **respect `prefers-reduced-motion`**.

---

## 5. Definition of done (every PR)
- [ ] Light **and** dark parity verified
- [ ] Contrast ≥4.5:1 (text) both themes; visible focus rings; keyboard nav; aria labels
- [ ] `/ui-test` run on the changed surface
- [ ] `/code-review` self-review (no Copilot)
- [ ] Verified in the running app; no functionality changed
- [ ] Only theme tokens / semantic variants used (no raw hex, no new inline styles)
- [ ] Clean diff vs `develop`; issue linked via `gh issue develop`

## 6. Folding in new features
Any new view/component must: consume the tokens in §2, use the §3 component patterns, support both
themes, and pass §5. Treat §5 as the design checklist on every UI PR.
