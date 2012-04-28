/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.layout;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_COLUMN;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ROW;
import static com.android.ide.common.layout.LayoutConstants.ATTR_ORIENTATION;
import static com.android.ide.common.layout.LayoutConstants.FQCN_SPACE;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_FILL_VERTICAL;
import static com.android.ide.common.layout.LayoutConstants.GRAVITY_VALUE_LEFT;
import static com.android.ide.common.layout.LayoutConstants.VALUE_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_TRUE;

import com.android.ide.common.api.DrawingStyle;
import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.IFeedbackPainter;
import com.android.ide.common.api.IGraphics;
import com.android.ide.common.api.IMenuCallback;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.INodeHandler;
import com.android.ide.common.api.IViewMetadata;
import com.android.ide.common.api.IViewMetadata.FillPreference;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.InsertType;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.RuleAction;
import com.android.ide.common.api.RuleAction.Choices;
import com.android.ide.common.api.SegmentType;
import com.android.ide.common.layout.grid.GridDropHandler;
import com.android.ide.common.layout.grid.GridLayoutPainter;
import com.android.ide.common.layout.grid.GridModel;
import com.android.util.Pair;

import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An {@link IViewRule} for android.widget.GridLayout which provides designtime
 * interaction with GridLayouts.
 * <p>
 * TODO:
 * <ul>
 * <li>Handle multi-drag: preserving relative positions and alignments among dragged
 * views.
 * <li>Handle GridLayouts that have been configured in a vertical orientation.
 * <li>Handle free-form editing GridLayouts that have been manually edited rather than
 * built up using free-form editing (e.g. they might not follow the same spacing
 * convention, might use weights etc)
 * <li>Avoid setting row and column numbers on the actual elements if they can be skipped
 * to make the XML leaner.
 * </ul>
 */
public class GridLayoutRule extends BaseLayoutRule {
    /**
     * The size of the visual regular grid that we snap to (if {@link #sSnapToGrid} is set
     */
    public static final int GRID_SIZE = 16;

    /** Standard gap between views */
    public static final int SHORT_GAP_DP = 16;

    /**
     * The preferred margin size, in pixels
     */
    public static final int MARGIN_SIZE = 32;

    /**
     * Size in screen pixels in the IDE of the gutter shown for new rows and columns (in
     * grid mode)
     */
    private static final int NEW_CELL_WIDTH = 10;

    /**
     * Maximum size of a widget relative to a cell which is allowed to fit into a cell
     * (and thereby enlarge it) before it is spread with row or column spans.
     */
    public static final double MAX_CELL_DIFFERENCE = 1.2;

    /** Whether debugging diagnostics is available in the toolbar */
    private static final boolean CAN_DEBUG =
            VALUE_TRUE.equals(System.getenv("ADT_DEBUG_GRIDLAYOUT")); //$NON-NLS-1$

    private static final String ACTION_ADD_ROW = "_addrow"; //$NON-NLS-1$
    private static final String ACTION_REMOVE_ROW = "_removerow"; //$NON-NLS-1$
    private static final String ACTION_ADD_COL = "_addcol"; //$NON-NLS-1$
    private static final String ACTION_REMOVE_COL = "_removecol"; //$NON-NLS-1$
    private static final String ACTION_ORIENTATION = "_orientation"; //$NON-NLS-1$
    private static final String ACTION_SHOW_STRUCTURE = "_structure"; //$NON-NLS-1$
    private static final String ACTION_GRID_MODE = "_gridmode"; //$NON-NLS-1$
    private static final String ACTION_SNAP = "_snap"; //$NON-NLS-1$
    private static final String ACTION_DEBUG = "_debug"; //$NON-NLS-1$

    private static final URL ICON_HORIZONTAL = GridLayoutRule.class.getResource("hlinear.png"); //$NON-NLS-1$
    private static final URL ICON_VERTICAL = GridLayoutRule.class.getResource("vlinear.png"); //$NON-NLS-1$
    private static final URL ICON_ADD_ROW = GridLayoutRule.class.getResource("addrow.png"); //$NON-NLS-1$
    private static final URL ICON_REMOVE_ROW = GridLayoutRule.class.getResource("removerow.png"); //$NON-NLS-1$
    private static final URL ICON_ADD_COL = GridLayoutRule.class.getResource("addcol.png"); //$NON-NLS-1$
    private static final URL ICON_REMOVE_COL = GridLayoutRule.class.getResource("removecol.png"); //$NON-NLS-1$
    private static final URL ICON_SHOW_STRUCT = GridLayoutRule.class.getResource("showgrid.png"); //$NON-NLS-1$
    private static final URL ICON_GRID_MODE = GridLayoutRule.class.getResource("gridmode.png"); //$NON-NLS-1$
    private static final URL ICON_SNAP = GridLayoutRule.class.getResource("snap.png"); //$NON-NLS-1$

    /**
     * Whether the IDE should show diagnostics for debugging the grid layout - including
     * spacers visibly in the outline, showing row and column numbers, and so on
     */
    public static boolean sDebugGridLayout = CAN_DEBUG;

    /** Whether the structure (grid model) should be displayed persistently to the user */
    public static boolean sShowStructure = false;

    /** Whether the drop positions should snap to a regular grid */
    public static boolean sSnapToGrid = false;

    /**
     * Whether the grid is edited in "grid mode" where the operations are row/column based
     * rather than free-form
     */
    public static boolean sGridMode = false;

    /** Constructs a new {@link GridLayoutRule} */
    public GridLayoutRule() {
    }

    @Override
    public void addLayoutActions(List<RuleAction> actions, final INode parentNode,
            final List<? extends INode> children) {
        super.addLayoutActions(actions, parentNode, children);

        Choices orientationAction = RuleAction.createChoices(
                ACTION_ORIENTATION,
                "Orientation", //$NON-NLS-1$
                new PropertyCallback(Collections.singletonList(parentNode),
                        "Change LinearLayout Orientation", ANDROID_URI, ATTR_ORIENTATION), Arrays
                        .<String> asList("Set Horizontal Orientation", "Set Vertical Orientation"),
                Arrays.<URL> asList(ICON_HORIZONTAL, ICON_VERTICAL), Arrays.<String> asList(
                        "horizontal", "vertical"), getCurrentOrientation(parentNode),
                null /* icon */, -10, false);
        orientationAction.setRadio(true);
        actions.add(orientationAction);

        // Gravity and margins
        if (children != null && children.size() > 0) {
            actions.add(RuleAction.createSeparator(35));
            actions.add(createMarginAction(parentNode, children));
            actions.add(createGravityAction(children, ATTR_LAYOUT_GRAVITY));
        }

        IMenuCallback actionCallback = new IMenuCallback() {
            public void action(final RuleAction action, List<? extends INode> selectedNodes,
                    final String valueId, final Boolean newValue) {
                parentNode.editXml("Add/Remove Row/Column", new INodeHandler() {
                    public void handle(INode n) {
                        String id = action.getId();
                        if (id.equals(ACTION_SHOW_STRUCTURE)) {
                            sShowStructure = !sShowStructure;
                            mRulesEngine.redraw();
                            return;
                        } else if (id.equals(ACTION_GRID_MODE)) {
                            sGridMode = !sGridMode;
                            mRulesEngine.redraw();
                            return;
                        } else if (id.equals(ACTION_SNAP)) {
                            sSnapToGrid = !sSnapToGrid;
                            mRulesEngine.redraw();
                            return;
                        } else if (id.equals(ACTION_DEBUG)) {
                            sDebugGridLayout = !sDebugGridLayout;
                            mRulesEngine.layout();
                            return;
                        }

                        GridModel grid = new GridModel(mRulesEngine, parentNode, null);
                        if (id.equals(ACTION_ADD_ROW)) {
                            grid.addRow(children);
                        } else if (id.equals(ACTION_REMOVE_ROW)) {
                            grid.removeRows(children);
                        } else if (id.equals(ACTION_ADD_COL)) {
                            grid.addColumn(children);
                        } else if (id.equals(ACTION_REMOVE_COL)) {
                            grid.removeColumns(children);
                        }
                    }

                });
            }
        };

        actions.add(RuleAction.createSeparator(142));

        actions.add(RuleAction.createToggle(ACTION_GRID_MODE, "Grid Model Mode",
                sGridMode, actionCallback, ICON_GRID_MODE, 145, false));

        // Add and Remove Column actions only apply in Grid Mode
        if (sGridMode) {
            // Add Row and Add Column
            actions.add(RuleAction.createSeparator(150));
            actions.add(RuleAction.createAction(ACTION_ADD_COL, "Add Column", actionCallback,
                    ICON_ADD_COL, 160, false /* supportsMultipleNodes */));
            actions.add(RuleAction.createAction(ACTION_ADD_ROW, "Add Row", actionCallback,
                    ICON_ADD_ROW, 165, false));

            // Remove Row and Remove Column (if something is selected)
            if (children != null && children.size() > 0) {
                // TODO: Add "Merge Columns" and "Merge Rows" ?

                actions.add(RuleAction.createAction(ACTION_REMOVE_COL, "Remove Column",
                        actionCallback, ICON_REMOVE_COL, 170, false));
                actions.add(RuleAction.createAction(ACTION_REMOVE_ROW, "Remove Row",
                        actionCallback, ICON_REMOVE_ROW, 175, false));
            }

            actions.add(RuleAction.createSeparator(185));
        } else {
            actions.add(RuleAction.createToggle(ACTION_SHOW_STRUCTURE, "Show Structure",
                    sShowStructure, actionCallback, ICON_SHOW_STRUCT, 190, false));

            // Snap to Grid and Show Structure are only relevant in free form mode
            actions.add(RuleAction.createToggle(ACTION_SNAP, "Snap to Grid",
                    sSnapToGrid, actionCallback, ICON_SNAP, 200, false));
        }

        // Temporary: Diagnostics for GridLayout
        if (CAN_DEBUG) {
            actions.add(RuleAction.createToggle(ACTION_DEBUG, "Debug",
                    sDebugGridLayout, actionCallback, null, 210, false));
        }
    }

    /**
     * Returns the orientation attribute value currently used by the node (even if not
     * defined, in which case the default horizontal value is returned)
     */
    private static String getCurrentOrientation(final INode node) {
        String orientation = node.getStringAttr(ANDROID_URI, ATTR_ORIENTATION);
        if (orientation == null || orientation.length() == 0) {
            orientation = VALUE_HORIZONTAL;
        }
        return orientation;
    }

    @Override
    public DropFeedback onDropEnter(INode targetNode, Object targetView, IDragElement[] elements) {
        GridDropHandler userData = new GridDropHandler(this, targetNode, targetView);
        IFeedbackPainter painter = GridLayoutPainter.createDropFeedbackPainter(this, elements);
        return new DropFeedback(userData, painter);
    }

    @Override
    public DropFeedback onDropMove(INode targetNode, IDragElement[] elements,
            DropFeedback feedback, Point p) {
        feedback.requestPaint = true;

        GridDropHandler handler = (GridDropHandler) feedback.userData;
        handler.computeMatches(feedback, p);

        return feedback;
    }

    @Override
    public void onDropped(final INode targetNode, final IDragElement[] elements,
            DropFeedback feedback, Point p) {
        Rect b = targetNode.getBounds();
        if (!b.isValid()) {
            return;
        }

        GridDropHandler dropHandler = (GridDropHandler) feedback.userData;
        if (dropHandler.getRowMatch() == null || dropHandler.getColumnMatch() == null) {
            return;
        }

        // Collect IDs from dropped elements and remap them to new IDs
        // if this is a copy or from a different canvas.
        Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                feedback.isCopy || !feedback.sameCanvas);

        for (IDragElement element : elements) {
            INode newChild;
            if (!sGridMode) {
                newChild = dropHandler.handleFreeFormDrop(targetNode, element);
            } else {
                newChild = dropHandler.handleGridModeDrop(targetNode, element);
            }

            // Copy all the attributes, modifying them as needed.
            addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

            addInnerElements(newChild, element, idMap);
        }
    }

    @Override
    public void onChildInserted(INode node, INode parent, InsertType insertType) {
        if (insertType == InsertType.MOVE_WITHIN) {
            // Don't adjust widths/heights/weights when just moving within a single layout
            return;
        }

        // Attempt to set "fill" properties on newly added views such that for example
        // a text field will stretch horizontally.
        String fqcn = node.getFqcn();
        IViewMetadata metadata = mRulesEngine.getMetadata(fqcn);
        if (metadata == null) {
            return;
        }
        FillPreference fill = metadata.getFillPreference();
        String gravity = computeDefaultGravity(fill);
        if (gravity != null) {
            node.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, gravity);
        }
    }

    /**
     * Computes the default gravity to be used for a widget of the given fill
     * preference when added to a grid layout
     *
     * @param fill the fill preference for the widget
     * @return the gravity value, or null, to be set on the widget
     */
    public static String computeDefaultGravity(FillPreference fill) {
        String horizontal = GRAVITY_VALUE_LEFT;
        String vertical = null;
        if (fill.fillHorizontally(true /*verticalContext*/)) {
            horizontal = GRAVITY_VALUE_FILL_HORIZONTAL;
        }
        if (fill.fillVertically(true /*verticalContext*/)) {
            vertical = GRAVITY_VALUE_FILL_VERTICAL;
        }
        String gravity;
        if (horizontal == GRAVITY_VALUE_FILL_HORIZONTAL
                && vertical == GRAVITY_VALUE_FILL_VERTICAL) {
            gravity = GRAVITY_VALUE_FILL;
        } else if (vertical != null) {
            gravity = horizontal + '|' + vertical;
        } else {
            gravity = horizontal;
        }

        return gravity;
    }

    @Override
    public void onRemovingChildren(List<INode> deleted, INode parent) {
        super.onRemovingChildren(deleted, parent);

        // Attempt to clean up spacer objects for any newly-empty rows or columns
        // as the result of this deletion
        GridModel grid = new GridModel(mRulesEngine, parent, null);
        for (INode child : deleted) {
            // We don't care about deletion of spacers
            if (child.getFqcn().equals(FQCN_SPACE)) {
                continue;
            }
            grid.markDeleted(child);
        }

        grid.cleanup();
    }

    @Override
    protected void paintResizeFeedback(IGraphics gc, INode node, ResizeState state) {
        if (!sGridMode) {
            GridModel grid = getGrid(state);
            GridLayoutPainter.paintResizeFeedback(gc, state.layout, grid);
        }

        if (resizingWidget(state)) {
            super.paintResizeFeedback(gc, node, state);
        } else {
            GridModel grid = getGrid(state);
            int startColumn = grid.getColumn(state.bounds.x);
            int endColumn = grid.getColumn(state.bounds.x2());
            int columnSpan = endColumn - startColumn + 1;

            int startRow = grid.getRow(state.bounds.y);
            int endRow = grid.getRow(state.bounds.y2());
            int rowSpan = endRow - startRow + 1;

            Rect cellBounds = grid.getCellBounds(startRow, startColumn, rowSpan, columnSpan);
            gc.useStyle(DrawingStyle.RESIZE_PREVIEW);
            gc.drawRect(cellBounds);
        }
    }

    /** Returns the grid size cached on the given {@link ResizeState} object */
    private GridModel getGrid(ResizeState resizeState) {
        GridModel grid = (GridModel) resizeState.clientData;
        if (grid == null) {
            grid = new GridModel(mRulesEngine, resizeState.layout, resizeState.layoutView);
            resizeState.clientData = grid;
        }

        return grid;
    }

    @Override
    protected void setNewSizeBounds(ResizeState state, INode node, INode layout,
            Rect oldBounds, Rect newBounds, SegmentType horizontalEdge, SegmentType verticalEdge) {

        if (resizingWidget(state)) {
            super.setNewSizeBounds(state, node, layout, oldBounds, newBounds, horizontalEdge,
                    verticalEdge);
        } else {
            Pair<Integer, Integer> spans = computeResizeSpans(state);
            int rowSpan = spans.getFirst();
            int columnSpan = spans.getSecond();
            GridModel.setColumnSpanAttribute(node, columnSpan);
            GridModel.setRowSpanAttribute(node, rowSpan);
        }
    }

    @Override
    protected String getResizeUpdateMessage(ResizeState state, INode child, INode parent,
            Rect newBounds, SegmentType horizontalEdge, SegmentType verticalEdge) {
        Pair<Integer, Integer> spans = computeResizeSpans(state);
        if (resizingWidget(state)) {
            String width = state.getWidthAttribute();
            String height = state.getHeightAttribute();

            String message;
            if (horizontalEdge == null) {
                message = width;
            } else if (verticalEdge == null) {
                message = height;
            } else {
                // U+00D7: Unicode for multiplication sign
                message = String.format("%s \u00D7 %s", width, height);
            }

            // Tack on a tip about using the Shift modifier key
            return String.format("%s\n(Press Shift to resize row/column spans)", message);
        } else {
            int rowSpan = spans.getFirst();
            int columnSpan = spans.getSecond();
            return String.format("ColumnSpan=%d, RowSpan=%d\n(Release Shift to resize widget itself)",
                    columnSpan, rowSpan);
        }
    }

    /**
     * Returns true if we're resizing the widget, and false if we're resizing the cell
     * spans
     */
    private static boolean resizingWidget(ResizeState state) {
        return (state.modifierMask & DropFeedback.MODIFIER2) == 0;
    }

    /**
     * Computes the new column and row spans as the result of the current resizing
     * operation
     */
    private Pair<Integer, Integer> computeResizeSpans(ResizeState state) {
        GridModel grid = getGrid(state);

        int startColumn = grid.getColumn(state.bounds.x);
        int endColumn = grid.getColumn(state.bounds.x2());
        int columnSpan = endColumn - startColumn + 1;

        int startRow = grid.getRow(state.bounds.y);
        int endRow = grid.getRow(state.bounds.y2());
        int rowSpan = endRow - startRow + 1;

        return Pair.of(rowSpan, columnSpan);
    }

    /**
     * Returns the size of the new cell gutter in layout coordinates
     *
     * @return the size of the new cell gutter in layout coordinates
     */
    public int getNewCellSize() {
        return mRulesEngine.screenToLayout(NEW_CELL_WIDTH / 2);
    }

    @Override
    public void paintSelectionFeedback(IGraphics graphics, INode parentNode,
            List<? extends INode> childNodes, Object view) {
        super.paintSelectionFeedback(graphics, parentNode, childNodes, view);

        if (sShowStructure) {
            // TODO: Cache the grid
            if (view != null) {
                if (GridLayoutPainter.paintStructure(view, DrawingStyle.GUIDELINE_DASHED,
                        parentNode, graphics)) {
                    return;
                }
            }
            GridLayoutPainter.paintStructure(DrawingStyle.GUIDELINE_DASHED,
                        parentNode, graphics, new GridModel(mRulesEngine, parentNode, view));
        } else if (sDebugGridLayout) {
            GridLayoutPainter.paintStructure(DrawingStyle.GRID,
                    parentNode, graphics, new GridModel(mRulesEngine, parentNode, view));
        }

        // TBD: Highlight the cells around the selection, and display easy controls
        // for for example tweaking the rowspan/colspan of a cell? (but only in grid mode)
    }

    /**
     * Paste into a GridLayout. We have several possible behaviors (and many
     * more than are listed here):
     * <ol>
     * <li> Preserve the current positions of the elements (if pasted from another
     *      canvas, not just XML markup copied from say a web site) and apply those
     *      into the current grid. This might mean "overwriting" (sitting on top of)
     *      existing elements.
     * <li> Fill available "holes" in the grid.
     * <li> Lay them out consecutively, row by row, like text.
     * <li> Some hybrid approach, where I attempt to preserve the <b>relative</b>
     *      relationships (columns/wrapping, spacing between the pasted views etc)
     *      but I append them to the bottom of the layout on one or more new rows.
     * <li> Try to paste at the current mouse position, if known, preserving the
     *      relative distances between the existing elements there.
     * </ol>
     * Attempting to preserve the current position isn't possible right now,
     * because the clipboard data contains only the textual representation of
     * the markup. (We'd need to stash position information from a previous
     * layout render along with the clipboard data).
     * <p>
     * Currently, this implementation simply lays out the elements row by row,
     * approach #3 above.
     */
    @Override
    public void onPaste(INode targetNode, Object targetView, IDragElement[] elements) {
        DropFeedback feedback = onDropEnter(targetNode, targetView, elements);
        if (feedback != null) {
            Rect b = targetNode.getBounds();
            if (!b.isValid()) {
                return;
            }

            Map<String, Pair<String, String>> idMap = getDropIdMap(targetNode, elements,
                    true /* remap id's */);

            for (IDragElement element : elements) {
                // Skip <Space> elements and only insert the real elements being
                // copied
                if (elements.length > 1 && FQCN_SPACE.equals(element.getFqcn())) {
                    continue;
                }

                String fqcn = element.getFqcn();
                INode newChild = targetNode.appendChild(fqcn);
                addAttributes(newChild, element, idMap, DEFAULT_ATTR_FILTER);

                // Ensure that we reset any potential row/column attributes from a different
                // grid layout being copied from
                newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_COLUMN, null);
                newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_ROW, null);

                // TODO: Set columnSpans to avoid making these widgets completely
                // break the layout
                // Alternatively, I could just lay them all out on subsequent lines
                // with a column span of columnSpan5

                addInnerElements(newChild, element, idMap);
            }
        }
    }
}
