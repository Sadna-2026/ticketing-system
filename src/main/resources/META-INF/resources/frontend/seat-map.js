import { LitElement, html, svg, css, nothing } from 'lit';

/**
 * Scalable assigned-seating map rendered as a single SVG (one element, not one
 * Vaadin component per seat — see issue #255). The server pushes a compact,
 * already row/seat-ordered payload via the `seats` property; selection is
 * hit-tested here on the client and only the chosen seat id is sent back to the
 * server through `$server.selectSeat(id)`. Taken seats are non-interactive.
 */
class SeatMap extends LitElement {
  static get properties() {
    return { seats: { type: Array } };
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
      rect.seat.free {
        cursor: pointer;
      }
      rect.seat.taken {
        cursor: not-allowed;
      }
      rect.seat.free:hover {
        stroke-width: 2;
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
  }

  // Groups the (pre-ordered) seat payload into rows, preserving encounter order.
  _rows() {
    const rows = [];
    const byLabel = new Map();
    for (const seat of this.seats || []) {
      let row = byLabel.get(seat.row);
      if (!row) {
        row = { label: seat.row, seats: [] };
        byLabel.set(seat.row, row);
        rows.push(row);
      }
      row.seats.push(seat);
    }
    return rows;
  }

  _select(seat) {
    if (seat.taken) {
      return;
    }
    this.$server.selectSeat(seat.id);
  }

  /** Flip one seat free->taken in place after a successful server-side add. */
  markTaken(id) {
    this.seats = (this.seats || []).map((seat) =>
      seat.id === id ? { ...seat, taken: true } : seat
    );
  }

  render() {
    const rows = this._rows();
    if (rows.length === 0) {
      return html`<div>No seats to display.</div>`;
    }

    const SEAT = 24;
    const GAP = 6;
    const ROW_H = SEAT + GAP;
    const LABEL_W = 56;
    const PAD = 8;
    const maxSeats = rows.reduce((m, r) => Math.max(m, r.seats.length), 0);
    const width = LABEL_W + maxSeats * (SEAT + GAP) + PAD;
    const height = rows.length * ROW_H + PAD * 2;

    return html`
      <div class="legend">
        <span class="free">Free</span>
        <span class="taken">Taken</span>
      </div>
      <svg
        width="${width}"
        height="${height}"
        viewBox="0 0 ${width} ${height}"
        role="group"
        aria-label="Seat map"
      >
        ${rows.map((row, r) => {
          const y = PAD + r * ROW_H;
          return svg`
            <text class="row-label" x="4" y="${y + SEAT / 2 + 4}">Row ${row.label}</text>
            ${row.seats.map((seat, i) => {
              const x = LABEL_W + i * (SEAT + GAP);
              const fill = seat.taken
                ? 'var(--lumo-error-color-10pct, #fbe9e9)'
                : 'var(--lumo-success-color-10pct, #e3f6ec)';
              const stroke = seat.taken
                ? 'var(--lumo-error-color-50pct, #ef9a9a)'
                : 'var(--lumo-success-color-50pct, #8fd6ab)';
              return svg`
                <g @click="${() => this._select(seat)}">
                  <rect
                    class="seat ${seat.taken ? 'taken' : 'free'}"
                    x="${x}"
                    y="${y}"
                    width="${SEAT}"
                    height="${SEAT}"
                    rx="5"
                    fill="${fill}"
                    stroke="${stroke}"
                    stroke-width="1"
                  >
                    <title>${row.label}-${seat.num}${seat.taken ? ' (taken)' : ''}</title>
                  </rect>
                  <text
                    class="seat-label"
                    x="${x + SEAT / 2}"
                    y="${y + SEAT / 2 + 3}"
                    text-anchor="middle"
                  >${seat.num}</text>
                </g>
              `;
            })}
          `;
        })}
        ${nothing}
      </svg>
    `;
  }
}

customElements.define('seat-map', SeatMap);
