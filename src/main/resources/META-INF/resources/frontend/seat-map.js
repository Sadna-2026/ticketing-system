import { LitElement, html, svg, css, nothing } from 'lit';

/**
 * Scalable assigned-seating map rendered as a single SVG (one element, not one
 * Vaadin component per seat — see issue #255). The server pushes a compact,
 * already row/seat-ordered payload via the `seats` property; selection is
 * hit-tested here on the client and only the chosen seat id is sent back to the
 * server through `$server.selectSeat(id)`.
 *
 * Accessibility: the SVG is an ARIA grid (`role="grid"` > `role="row"` >
 * `role="gridcell"`). Seats are keyboard-navigable with the arrow keys / Home /
 * End via a roving tabindex (exactly one cell is in the tab order at a time, so
 * the pattern stays usable even for venues with thousands of seats), activated
 * with Enter / Space, and each cell carries an `aria-label` announcing its seat
 * id and free/taken status. Taken seats are `aria-disabled`, marked with a
 * non-colour "✕" overlay (so status does not rely on colour alone — WCAG 1.4.1),
 * and stay focusable-but-not-activatable so assistive tech can still read them.
 */
class SeatMap extends LitElement {
  static get properties() {
    return {
      seats: { type: Array },
      _focus: { state: true },
    };
  }

  static get styles() {
    return css`
      :host {
        display: block;
        overflow: auto;
        max-width: 100%;
      }
      .legend {
        display: flex;
        gap: var(--lumo-space-m, 1rem);
        margin-bottom: var(--lumo-space-s, 0.5rem);
        font-size: var(--lumo-font-size-s, 0.875rem);
      }
      .legend span::before {
        content: '\\25CF';
        margin-right: 0.35em;
      }
      .legend .free::before {
        color: var(--lumo-success-color, #2dc26b);
      }
      .legend .taken::before {
        color: var(--lumo-error-color, #e5484d);
      }
      g.cell {
        outline: none;
      }
      g.cell.free {
        cursor: pointer;
      }
      g.cell.taken {
        cursor: not-allowed;
      }
      g.cell.free:hover rect.seat {
        stroke-width: 2;
      }
      /* Visible keyboard focus indicator (does not show on mouse click). */
      g.cell:focus-visible rect.seat {
        stroke: var(--lumo-primary-color, #1676f3);
        stroke-width: 3;
      }
      line.taken-mark {
        stroke: var(--lumo-error-color, #b3271e);
        stroke-width: 2;
        stroke-linecap: round;
        pointer-events: none;
      }
      text.row-label {
        font-size: 12px;
        font-weight: 600;
        fill: var(--lumo-body-text-color, #1a1a1a);
      }
      text.seat-label {
        font-size: 9px;
        fill: var(--lumo-secondary-text-color, #555);
        pointer-events: none;
        user-select: none;
      }
    `;
  }

  constructor() {
    super();
    this.seats = [];
    this._focus = 0;
  }

  // Groups the (pre-ordered) seat payload into rows (preserving encounter
  // order) and assigns each seat a flat index + (row, col) position so arrow-key
  // navigation and the roving tabindex can address cells consistently.
  _layout() {
    const rows = [];
    const byLabel = new Map();
    for (const seat of this.seats || []) {
      let row = byLabel.get(seat.row);
      if (!row) {
        row = { label: seat.row, cells: [] };
        byLabel.set(seat.row, row);
        rows.push(row);
      }
      row.cells.push({ seat });
    }
    let i = 0;
    rows.forEach((row, r) => {
      row.cells.forEach((cell, c) => {
        cell.i = i++;
        cell.r = r;
        cell.c = c;
      });
    });
    return { rows, total: i };
  }

  _select(seat) {
    if (seat.taken) {
      return;
    }
    this.$server.selectSeat(seat.id);
  }

  _onKeydown(event, cell, layout) {
    const { rows, total } = layout;
    let target = cell.i;
    switch (event.key) {
      case 'Enter':
      case ' ':
      case 'Spacebar':
        event.preventDefault();
        this._select(cell.seat);
        return;
      case 'ArrowRight':
        target = Math.min(cell.i + 1, total - 1);
        break;
      case 'ArrowLeft':
        target = Math.max(cell.i - 1, 0);
        break;
      case 'ArrowDown':
      case 'ArrowUp': {
        const dir = event.key === 'ArrowDown' ? 1 : -1;
        const nextRow = rows[cell.r + dir];
        if (!nextRow) {
          return;
        }
        const col = Math.min(cell.c, nextRow.cells.length - 1);
        target = nextRow.cells[col].i;
        break;
      }
      case 'Home':
        target = rows[cell.r].cells[0].i;
        break;
      case 'End': {
        const cells = rows[cell.r].cells;
        target = cells[cells.length - 1].i;
        break;
      }
      default:
        return;
    }
    event.preventDefault();
    this._moveFocus(target);
  }

  _moveFocus(index) {
    this._focus = index;
    this.updateComplete.then(() => {
      const el = this.shadowRoot.querySelector(`g.cell[data-i="${index}"]`);
      if (el) {
        el.focus();
      }
    });
  }

  /** Flip one seat free->taken in place after a successful server-side add. */
  markTaken(id) {
    this.seats = (this.seats || []).map((seat) =>
      seat.id === id ? { ...seat, taken: true } : seat
    );
  }

  render() {
    const layout = this._layout();
    const { rows, total } = layout;
    if (total === 0) {
      return html`<div>No seats to display.</div>`;
    }

    const SEAT = 24;
    const GAP = 6;
    const ROW_H = SEAT + GAP;
    const LABEL_W = 56;
    const PAD = 8;
    const maxSeats = rows.reduce((m, r) => Math.max(m, r.cells.length), 0);
    const width = LABEL_W + maxSeats * (SEAT + GAP) + PAD;
    const height = rows.length * ROW_H + PAD * 2;
    const focusIdx = Math.min(this._focus, total - 1);

    return html`
      <div class="legend">
        <span class="free">Free</span>
        <span class="taken">Taken</span>
      </div>
      <svg
        width="${width}"
        height="${height}"
        viewBox="0 0 ${width} ${height}"
        role="grid"
        aria-label="Seat map"
      >
        ${rows.map((row, r) => {
          const y = PAD + r * ROW_H;
          return svg`
            <g role="row">
              <text class="row-label" x="4" y="${y + SEAT / 2 + 4}" aria-hidden="true">Row ${row.label}</text>
              ${row.cells.map((cell) => {
                const seat = cell.seat;
                const x = LABEL_W + cell.c * (SEAT + GAP);
                const fill = seat.taken
                  ? 'var(--lumo-error-color-10pct, #fbe9e9)'
                  : 'var(--lumo-success-color-10pct, #e3f6ec)';
                const stroke = seat.taken
                  ? 'var(--lumo-error-color-50pct, #ef9a9a)'
                  : 'var(--lumo-success-color-50pct, #8fd6ab)';
                const label = `Row ${row.label} seat ${seat.num}, ${seat.taken ? 'taken' : 'free'}`;
                return svg`
                  <g
                    class="cell ${seat.taken ? 'taken' : 'free'}"
                    role="gridcell"
                    data-i="${cell.i}"
                    tabindex="${cell.i === focusIdx ? 0 : -1}"
                    aria-label="${label}"
                    aria-disabled="${seat.taken ? 'true' : 'false'}"
                    @click="${() => { this._focus = cell.i; this._select(seat); }}"
                    @keydown="${(e) => this._onKeydown(e, cell, layout)}"
                  >
                    <rect
                      class="seat"
                      x="${x}"
                      y="${y}"
                      width="${SEAT}"
                      height="${SEAT}"
                      rx="5"
                      fill="${fill}"
                      stroke="${stroke}"
                      stroke-width="1"
                    ></rect>
                    ${seat.taken
                      ? svg`
                          <line class="taken-mark" x1="${x + 5}" y1="${y + 5}" x2="${x + SEAT - 5}" y2="${y + SEAT - 5}"></line>
                          <line class="taken-mark" x1="${x + SEAT - 5}" y1="${y + 5}" x2="${x + 5}" y2="${y + SEAT - 5}"></line>
                        `
                      : nothing}
                    <text
                      class="seat-label"
                      x="${x + SEAT / 2}"
                      y="${y + SEAT / 2 + 3}"
                      text-anchor="middle"
                      aria-hidden="true"
                    >${seat.num}</text>
                  </g>
                `;
              })}
            </g>
          `;
        })}
      </svg>
    `;
  }
}

customElements.define('seat-map', SeatMap);
