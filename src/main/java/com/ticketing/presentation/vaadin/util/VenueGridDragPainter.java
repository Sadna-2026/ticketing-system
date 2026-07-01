package com.ticketing.presentation.vaadin.util;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.dom.DomEvent;

/**
 * Pointer-drag painting for venue layout grids: hold the primary button and drag across cells
 * to paint a rectangular block (same interaction as seat selection on the events map).
 */
public final class VenueGridDragPainter {

    @FunctionalInterface
    public interface CellAction {
        void apply(int row, int col);
    }

    private final CellAction cellAction;
    private int anchorRow = -1;
    private int anchorCol = -1;
    private boolean dragActive;
    private boolean dragMoved;
    private boolean suppressNextClick;

    public VenueGridDragPainter(CellAction cellAction) {
        this.cellAction = cellAction;
    }

    public void wireCell(Button cell, int row, int col) {
        cell.getStyle()
                .set("user-select", "none")
                .set("-webkit-user-select", "none")
                .set("touch-action", "none");
        cell.getElement().setAttribute("draggable", "false");
        cell.addClickListener(e -> handleClick(row, col, e));
        cell.getElement().addEventListener("pointerdown", e -> beginDrag(row, col, e))
                .addEventData("event.button")
                .preventDefault();
        cell.getElement().addEventListener("pointerenter", e -> extendDrag(row, col, e))
                .addEventData("event.buttons");
        cell.getElement().addEventListener("pointerup", e -> finishDrag(row, col));
        cell.getElement().addEventListener("dragstart", e -> {
        }).preventDefault();
    }

    public void reset() {
        anchorRow = -1;
        anchorCol = -1;
        dragActive = false;
        dragMoved = false;
        suppressNextClick = false;
    }

    /** Paints every cell in the inclusive rectangle between the two grid coordinates. */
    public static void paintRange(CellAction action, int fromRow, int fromCol, int toRow, int toCol) {
        int minRow = Math.min(fromRow, toRow);
        int maxRow = Math.max(fromRow, toRow);
        int minCol = Math.min(fromCol, toCol);
        int maxCol = Math.max(fromCol, toCol);
        for (int r = minRow; r <= maxRow; r++) {
            for (int c = minCol; c <= maxCol; c++) {
                action.apply(r, c);
            }
        }
    }

    private void handleClick(int row, int col, ClickEvent<Button> event) {
        if (suppressNextClick) {
            suppressNextClick = false;
            return;
        }
        cellAction.apply(row, col);
    }

    private void beginDrag(int row, int col, DomEvent event) {
        if (!isPrimaryPointerButton(event)) {
            return;
        }
        anchorRow = row;
        anchorCol = col;
        dragActive = true;
        dragMoved = false;
        suppressNextClick = false;
    }

    private void extendDrag(int row, int col, DomEvent event) {
        if (!dragActive || anchorRow < 0 || !isPrimaryPointerDown(event)) {
            return;
        }
        if (row == anchorRow && col == anchorCol) {
            return;
        }
        dragMoved = true;
        paintRange(cellAction, anchorRow, anchorCol, row, col);
    }

    private void finishDrag(int row, int col) {
        if (dragActive && dragMoved && anchorRow >= 0) {
            paintRange(cellAction, anchorRow, anchorCol, row, col);
            suppressNextClick = true;
        }
        anchorRow = -1;
        anchorCol = -1;
        dragActive = false;
        dragMoved = false;
    }

    private static boolean isPrimaryPointerButton(DomEvent event) {
        return event.getEventData().getNumber("event.button") == 0;
    }

    private static boolean isPrimaryPointerDown(DomEvent event) {
        return ((int) event.getEventData().getNumber("event.buttons") & 1) == 1;
    }
}
