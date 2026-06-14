import { LitElement, html, svg, css, nothing } from 'lit';

/**
 * Scalable assigned-seating map. Buyers stage seats locally (free -> selected -> free
 * on click) and only commit to the server when the host view triggers
 * {@link #requestAddSelection}. Matches common cinema UX: pick multiple seats first,
 * then add to cart; inventory is locked only on commit.
 *
 * States: free (green), selected / your pick (amber + check), taken (red + ✕).
 */
class SeatMap extends LitElement {
  static get properties() {
    return {
      seats: { type: Array },
      _focus: { state: true },
      disabled: { type: Boolean },
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
        flex-wrap: wrap;
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
      .legend .selected::before {
        color: var(--lumo-warning-color, #f5a623);
      }
      .legend .taken::before {
        color: var(--lumo-error-color, #e5484d);
      }
      g.cell {
        outline: none;
      }
      g.cell.free,
      g.cell.selected {
        cursor: pointer;
      }
      g.cell.taken {
        cursor: not-allowed;
      }
      g.cell.free:hover rect.seat,
      g.cell.selected:hover rect.seat {
        stroke-width: 2;
      }
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
      text.selected-mark {
        font-size: 14px;
        font-weight: 700;
        fill: var(--lumo-warning-text-color, #8a6116);
        pointer-events: none;
        user-select: none;
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
    this.disabled = false;
  }

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

  _selectedCount() {
    return (this.seats || []).filter((s) => s.selected && !s.taken).length;
  }

  _notifySelectionCount() {
    if (this.$server && this.$server.notifySelectionCount) {
      this.$server.notifySelectionCount(this._selectedCount());
    }
  }

  /** Toggle staged selection for a free seat; taken seats are ignored. */
  _toggle(seat) {
    if (this.disabled || seat.taken) {
      return;
    }
    this.seats = (this.seats || []).map((s) =>
      s.id === seat.id ? { ...s, selected: !s.selected } : s
    );
    this._notifySelectionCount();
  }

  _onKeydown(event, cell, layout) {
    const { rows, total } = layout;
    let target = cell.i;
    switch (event.key) {
      case 'Enter':
      case ' ':
      case 'Spacebar':
        event.preventDefault();
        this._toggle(cell.seat);
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

  /** Called from the server when the buyer clicks Add selected seats. */
  requestAddSelection() {
    const ids = (this.seats || [])
      .filter((s) => s.selected && !s.taken)
      .map((s) => s.id);
    if (ids.length === 0 || !this.$server || !this.$server.onCommitSelection) {
      return;
    }
    this.$server.onCommitSelection(ids);
  }

  /** Flip committed seats to taken after a successful server-side add. */
  markTaken(id) {
    this.markTakenMany([id]);
  }

  markTakenMany(ids) {
    const idList = Array.isArray(ids) ? ids : [ids];
    const idSet = new Set(idList);
    this.seats = (this.seats || []).map((seat) =>
      idSet.has(seat.id)
        ? { ...seat, taken: true, selected: false }
        : { ...seat, selected: false }
    );
    this._notifySelectionCount();
  }

  /**
   * Apply server availability; drop staged picks on seats that became taken.
   * Reports lost labels back to Java via onSyncComplete.
   */
  syncSeats(freshSeats) {
    const byId = new Map((freshSeats || []).map((s) => [s.id, s]));
    const lost = [];
    this.seats = (this.seats || []).map((seat) => {
      const fresh = byId.get(seat.id);
      if (!fresh) {
        return seat;
      }
      const wasSelected = seat.selected && !seat.taken;
      const nowTaken = !!fresh.taken;
      if (wasSelected && nowTaken) {
        lost.push(`${fresh.row}-${fresh.num}`);
      }
      return {
        ...seat,
        taken: nowTaken,
        selected: wasSelected && !nowTaken,
      };
    });
    this._notifySelectionCount();
    if (this.$server && this.$server.onSyncComplete) {
      this.$server.onSyncComplete(lost);
    }
  }

  _seatState(seat) {
    if (seat.taken) {
      return 'taken';
    }
    if (seat.selected) {
      return 'selected';
    }
    return 'free';
  }

  _seatColors(state) {
    if (state === 'taken') {
      return {
        fill: 'var(--lumo-error-color-10pct, #fbe9e9)',
        stroke: 'var(--lumo-error-color-50pct, #ef9a9a)',
      };
    }
    if (state === 'selected') {
      return {
        fill: 'var(--lumo-warning-color-10pct, #fff4e0)',
        stroke: 'var(--lumo-warning-color-50pct, #f0c36d)',
      };
    }
    return {
      fill: 'var(--lumo-success-color-10pct, #e3f6ec)',
      stroke: 'var(--lumo-success-color-50pct, #8fd6ab)',
    };
  }

  _ariaLabel(rowLabel, seat) {
    const state = this._seatState(seat);
    if (state === 'taken') {
      return `Row ${rowLabel} seat ${seat.num}, taken`;
    }
    if (state === 'selected') {
      return `Row ${rowLabel} seat ${seat.num}, selected`;
    }
    return `Row ${rowLabel} seat ${seat.num}, free`;
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
        <span class="free">Available</span>
        <span class="selected">Your selection</span>
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
                const state = this._seatState(seat);
                const colors = this._seatColors(state);
                return svg`
                  <g
                    class="cell ${state}"
                    role="gridcell"
                    data-i="${cell.i}"
                    tabindex="${cell.i === focusIdx ? 0 : -1}"
                    aria-label="${this._ariaLabel(row.label, seat)}"
                    aria-disabled="${state === 'taken' ? 'true' : 'false'}"
                    @click="${() => {
                      this._focus = cell.i;
                      this._toggle(seat);
                    }}"
                    @keydown="${(e) => this._onKeydown(e, cell, layout)}"
                  >
                    <rect
                      class="seat"
                      x="${x}"
                      y="${y}"
                      width="${SEAT}"
                      height="${SEAT}"
                      rx="5"
                      fill="${colors.fill}"
                      stroke="${colors.stroke}"
                      stroke-width="1"
                    ></rect>
                    ${state === 'taken'
                      ? svg`
                          <line class="taken-mark" x1="${x + 5}" y1="${y + 5}" x2="${x + SEAT - 5}" y2="${y + SEAT - 5}"></line>
                          <line class="taken-mark" x1="${x + SEAT - 5}" y1="${y + 5}" x2="${x + 5}" y2="${y + SEAT - 5}"></line>
                        `
                      : nothing}
                    ${state === 'selected'
                      ? svg`
                          <text
                            class="selected-mark"
                            x="${x + SEAT / 2}"
                            y="${y + SEAT / 2 + 5}"
                            text-anchor="middle"
                            aria-hidden="true"
                          >✓</text>
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
