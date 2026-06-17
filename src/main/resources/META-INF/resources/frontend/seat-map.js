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
      _zoom: { state: true },
      _maxw: { state: true },
      disabled: { type: Boolean },
    };
  }

  static get styles() {
    return css`
      :host {
        display: block;
        position: relative;
        width: 100%;
        max-width: 100%;
        min-width: 0;
      }
      /* The wide seat grid scrolls inside this viewport (both axes), never
         the whole page. */
      .viewport {
        overflow: auto;
        max-width: 100%;
        max-height: 70vh;
      }
      /* Compact, non-disruptive zoom control floating on the side; stays put
         while the map scrolls. */
      .zoombar {
        position: absolute;
        top: 6px;
        right: 6px;
        z-index: 3;
        display: flex;
        flex-direction: column;
        gap: 1px;
        width: fit-content;
        padding: 2px;
        border-radius: 8px;
        background: color-mix(in srgb, var(--app-surface, #181b24) 86%, transparent);
        border: 1px solid var(--app-border, rgba(255, 255, 255, 0.08));
        backdrop-filter: blur(4px);
      }
      .zoombar button {
        height: 24px;
        min-width: 24px;
        padding: 0 4px;
        border: 0;
        border-radius: 5px;
        background: transparent;
        color: var(--app-text, #eaeef5);
        cursor: pointer;
        font-size: 15px;
        line-height: 1;
      }
      .zoombar button.zlabel {
        font-size: 12px;
        font-variant-numeric: tabular-nums;
        color: var(--app-muted, #99a0ae);
        min-width: 3.4em;
      }
      .zoombar button:hover {
        background: var(--app-surface-2, #20242f);
        color: var(--app-cyan, #34e1d6);
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
      .legend {
        color: var(--app-text, #eaeef5);
      }
      .legend .free::before {
        color: var(--app-muted, #99a0ae);
      }
      .legend .selected::before {
        color: var(--app-cyan, #34e1d6);
      }
      .legend .taken::before {
        color: var(--app-muted, #99a0ae);
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
      /* Taken seats sink toward the background. */
      g.cell.taken rect.seat {
        opacity: 0.45;
      }
      g.cell.free:hover rect.seat {
        stroke: var(--app-cyan, #34e1d6);
        stroke-width: 2;
      }
      /* Selected seats are lit — the EQ signature. */
      g.cell.selected rect.seat {
        filter: drop-shadow(0 0 5px color-mix(in srgb, var(--app-cyan, #34e1d6) 70%, transparent));
      }
      g.cell:focus-visible rect.seat {
        stroke: var(--app-cyan, #34e1d6);
        stroke-width: 3;
      }
      line.taken-mark {
        stroke: var(--app-muted, #99a0ae);
        stroke-width: 2;
        stroke-linecap: round;
        pointer-events: none;
      }
      text.selected-mark {
        font-size: 16px;
        font-weight: 700;
        fill: #08110f;
        pointer-events: none;
        user-select: none;
      }
      g.cell.selected text.seat-label {
        fill: #08110f;
      }
      text.row-label {
        font-size: 12px;
        font-weight: 600;
        fill: var(--app-text, #eaeef5);
      }
      text.seat-label {
        font-size: 11px;
        fill: var(--app-muted, #99a0ae);
        pointer-events: none;
        user-select: none;
      }
    `;
  }

  constructor() {
    super();
    this.seats = [];
    this._focus = 0;
    this._zoom = 1;
    this._maxw = 0;
    this.disabled = false;
  }

  /** Fit the map to the available width once it's first laid out, and wire
   *  pinch / Ctrl+wheel / keyboard zoom shortcuts. */
  firstUpdated() {
    this._zoomFit();
    this.addEventListener('wheel', (e) => this._onWheel(e), { passive: false });
    this.addEventListener('keydown', (e) => this._onZoomKey(e));
  }

  _onWheel(e) {
    // Trackpad pinch and Ctrl/Cmd + wheel zoom the map itself, not the page.
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      const factor = e.deltaY < 0 ? 1.1 : 1 / 1.1;
      this._zoom = Math.max(0.25, Math.min(2.5, (this._zoom || 1) * factor));
    }
  }

  _onZoomKey(e) {
    if (e.key === '+' || e.key === '=') {
      e.preventDefault();
      this._zoomIn();
    } else if (e.key === '-' || e.key === '_') {
      e.preventDefault();
      this._zoomOut();
    } else if (e.key === '0') {
      e.preventDefault();
      this._zoomFit();
    }
  }

  _zoomFit() {
    // Bound the scroll viewport to the actually-visible width (the Vaadin flex
    // ancestors don't constrain it), then fit the map into that width.
    const rect = this.getBoundingClientRect();
    const avail = Math.max(240, (window.innerWidth || 1000) - rect.left - 24);
    this._maxw = avail;
    this._zoom = this._w ? Math.max(0.25, Math.min(1, avail / this._w)) : 1;
  }

  _zoomIn() {
    this._zoom = Math.min(2.5, (this._zoom || 1) * 1.25);
  }

  _zoomOut() {
    this._zoom = Math.max(0.25, (this._zoom || 1) / 1.25);
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
    const ids = (this.seats || [])
      .filter((s) => s.selected && !s.taken)
      .map((s) => s.id);
    if (this.$server) {
      if (this.$server.notifySelectionCount) {
        this.$server.notifySelectionCount(ids.length);
      }
      if (this.$server.onStagedSelectionChanged) {
        this.$server.onStagedSelectionChanged(ids);
      }
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

  /** Restore buyer's staged picks after the map re-attaches (e.g. tab navigation). */
  applyStagedSelection(ids) {
    const idSet = new Set(Array.isArray(ids) ? ids : [ids]);
    this.seats = (this.seats || []).map((seat) => ({
      ...seat,
      selected: idSet.has(seat.id) && !seat.taken,
    }));
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
      const keepSelected =
        fresh.selected != null ? !!fresh.selected && !nowTaken : wasSelected && !nowTaken;
      if ((wasSelected || fresh.selected) && nowTaken) {
        lost.push(`${fresh.row}-${fresh.num}`);
      }
      return {
        ...seat,
        taken: nowTaken,
        selected: keepSelected,
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
        fill: 'var(--app-surface, #181b24)',
        stroke: 'var(--app-border, rgba(255,255,255,0.08))',
      };
    }
    if (state === 'selected') {
      return {
        fill: 'url(#sm-sel)',
        stroke: 'transparent',
      };
    }
    return {
      fill: 'var(--app-surface-2, #20242f)',
      stroke: 'var(--app-border, rgba(255,255,255,0.08))',
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

    const SEAT = 36;
    const GAP = 10;
    const ROW_H = SEAT + GAP;
    const LABEL_W = 64;
    const PAD = 10;
    const maxSeats = rows.reduce((m, r) => Math.max(m, r.cells.length), 0);
    const width = LABEL_W + maxSeats * (SEAT + GAP) + PAD;
    const height = rows.length * ROW_H + PAD * 2;
    const focusIdx = Math.min(this._focus, total - 1);
    this._w = width;
    this._h = height;
    const zoom = this._zoom || 1;

    return html`
      <div class="zoombar" role="group" aria-label="Zoom controls">
        <button @click="${() => this._zoomOut()}" title="Zoom out (−)" aria-label="Zoom out">&minus;</button>
        <button class="zlabel" @click="${() => this._zoomFit()}" title="Fit to width (press 0)">${Math.round(zoom * 100)}%</button>
        <button @click="${() => this._zoomIn()}" title="Zoom in (+)" aria-label="Zoom in">+</button>
      </div>
      <div class="legend">
        <span class="free">Available</span>
        <span class="selected">Your selection</span>
        <span class="taken">Taken</span>
      </div>
      <div class="viewport" style="max-width:${this._maxw ? this._maxw + 'px' : '100%'}">
      <svg
        width="${width * zoom}"
        height="${height * zoom}"
        viewBox="0 0 ${width} ${height}"
        role="grid"
        aria-label="Seat map"
      >
        <defs>
          <linearGradient id="sm-sel" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="var(--app-cyan, #34e1d6)"></stop>
            <stop offset="1" stop-color="var(--app-magenta, #ff5ca8)"></stop>
          </linearGradient>
        </defs>
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
                      rx="8"
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
      </div>
    `;
  }
}

customElements.define('seat-map', SeatMap);
