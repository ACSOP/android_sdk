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
package com.android.ide.common.layout.grid;

import static com.android.ide.common.layout.GridLayoutRule.GRID_SIZE;
import static com.android.ide.common.layout.GridLayoutRule.MARGIN_SIZE;
import static com.android.ide.common.layout.GridLayoutRule.MAX_CELL_DIFFERENCE;
import static com.android.ide.common.layout.GridLayoutRule.SHORT_GAP_DP;
import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_COLUMN_COUNT;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_COLUMN;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_COLUMN_SPAN;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_GRAVITY;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ROW;
import static com.android.ide.common.layout.LayoutConstants.ATTR_LAYOUT_ROW_SPAN;
import static com.android.ide.common.layout.LayoutConstants.VALUE_1;
import static com.android.ide.common.layout.LayoutConstants.VALUE_BOTTOM;
import static com.android.ide.common.layout.LayoutConstants.VALUE_CENTER_HORIZONTAL;
import static com.android.ide.common.layout.LayoutConstants.VALUE_RIGHT;
import static com.android.ide.common.layout.grid.GridModel.UNDEFINED;
import static java.lang.Math.abs;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.IViewMetadata;
import com.android.ide.common.api.Margins;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.common.api.SegmentType;
import com.android.ide.common.layout.BaseLayoutRule;
import com.android.ide.common.layout.GridLayoutRule;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The {@link GridDropHandler} handles drag and drop operations into and within a
 * GridLayout, computing guidelines, handling drops to edit the grid model, and so on.
 */
public class GridDropHandler {
    private final GridModel mGrid;
    private final GridLayoutRule mRule;
    private GridMatch mColumnMatch;
    private GridMatch mRowMatch;

    /**
     * Creates a new {@link GridDropHandler} for
     * @param gridLayoutRule the corresponding {@link GridLayoutRule}
     * @param layout the GridLayout node
     * @param view the view instance of the grid layout receiving the drop
     */
    public GridDropHandler(GridLayoutRule gridLayoutRule, INode layout, Object view) {
        mRule = gridLayoutRule;
        mGrid = new GridModel(mRule.getRulesEngine(), layout, view);
    }

    /**
     * Computes the best horizontal and vertical matches for a drag to the given position.
     *
     * @param feedback a {@link DropFeedback} object containing drag state like the drag
     *            bounds and the drag baseline
     * @param p the mouse position
     */
    public void computeMatches(DropFeedback feedback, Point p) {
        mRowMatch = mColumnMatch = null;

        Rect bounds = mGrid.layout.getBounds();
        int x1 = p.x;
        int y1 = p.y;

        if (!GridLayoutRule.sGridMode) {
            Rect dragBounds = feedback.dragBounds;
            if (dragBounds != null) {
                // Sometimes the items are centered under the mouse so
                // offset by the top left corner distance
                x1 += dragBounds.x;
                y1 += dragBounds.y;
            }

            int w = dragBounds != null ? dragBounds.w : 0;
            int h = dragBounds != null ? dragBounds.h : 0;
            int x2 = x1 + w;
            int y2 = y1 + h;

            if (x2 < bounds.x || y2 < bounds.y || x1 > bounds.x2() || y1 > bounds.y2()) {
                return;
            }

            List<GridMatch> columnMatches = new ArrayList<GridMatch>();
            List<GridMatch> rowMatches = new ArrayList<GridMatch>();
            int max = BaseLayoutRule.getMaxMatchDistance();

            // Column matches:
            addLeftSideMatch(x1, columnMatches, max);
            addRightSideMatch(x2, columnMatches, max);
            addCenterColumnMatch(bounds, x1, y1, x2, y2, columnMatches, max);

            // Row matches:
            int row = mGrid.getClosestRow(y1);
            int rowY = mGrid.getRowY(row);
            addTopMatch(y1, rowMatches, max, row, rowY);
            addBaselineMatch(feedback.dragBaseline, y1, rowMatches, max, row, rowY);
            addBottomMatch(y2, rowMatches, max);

            // Look for gap-matches: Predefined spacing between widgets.
            // TODO: Make this use metadata for predefined spacing between
            // pairs of types of components. For example, buttons have certain
            // inserts in their 9-patch files (depending on the theme) that should
            // be considered and subtracted from the overall proposed distance!
            addColumnGapMatch(bounds, x1, x2, columnMatches, max);
            addRowGapMatch(bounds, y1, y2, rowMatches, max);

            // Fallback: Split existing cell. Also do snap-to-grid.
            if (GridLayoutRule.sSnapToGrid) {
                x1 = ((x1 - MARGIN_SIZE - bounds.x) / GRID_SIZE) * GRID_SIZE
                        + MARGIN_SIZE + bounds.x;
                y1 = ((y1 - MARGIN_SIZE - bounds.y) / GRID_SIZE) * GRID_SIZE
                        + MARGIN_SIZE + bounds.y;
                x2 = x1 + w;
                y2 = y1 + h;
            }


            if (columnMatches.size() == 0) {
                // Split the current cell since we have no matches
                // TODO: Decide whether it should be gravity left or right...
                columnMatches.add(new GridMatch(SegmentType.LEFT, 0, x1, mGrid.getColumn(x1),
                        true /* createCell */, UNDEFINED));
            }
            if (rowMatches.size() == 0) {
                rowMatches.add(new GridMatch(SegmentType.TOP, 0, y1, mGrid.getRow(y1),
                        true /* createCell */, UNDEFINED));
            }

            // Pick best matches
            Collections.sort(rowMatches);
            Collections.sort(columnMatches);

            mColumnMatch = null;
            mRowMatch = null;
            String columnDescription = null;
            String rowDescription = null;
            if (columnMatches.size() > 0) {
                mColumnMatch = columnMatches.get(0);
                columnDescription = mColumnMatch.getDisplayName(mGrid.layout);
            }
            if (rowMatches.size() > 0) {
                mRowMatch = rowMatches.get(0);
                rowDescription = mRowMatch.getDisplayName(mGrid.layout);
            }
            if (columnDescription != null) {
                if (rowDescription != null) {
                    feedback.tooltip = columnDescription + '\n' + rowDescription;
                } else {
                    feedback.tooltip = columnDescription;
                }
            } else if (rowDescription != null) {
                feedback.tooltip = rowDescription;
            } else {
                feedback.tooltip = null;
            }

            feedback.invalidTarget = mColumnMatch == null || mRowMatch == null;
        } else {
            // Find which cell we're inside.

            // TODO: Find out where within the cell we are, and offer to tweak the gravity
            // based on the position.
            int column = mGrid.getColumn(x1);
            int row = mGrid.getRow(y1);

            int leftDistance = mGrid.getColumnDistance(column, x1);
            int rightDistance = mGrid.getColumnDistance(column + 1, x1);
            int topDistance = mGrid.getRowDistance(row, y1);
            int bottomDistance = mGrid.getRowDistance(row + 1, y1);

            int SLOP = 2;
            int radius = mRule.getNewCellSize();
            if (rightDistance < radius + SLOP) {
                column++;
                leftDistance = rightDistance;
            }
            if (bottomDistance < radius + SLOP) {
                row++;
                topDistance = bottomDistance;
            }

            boolean matchLeft = leftDistance < radius + SLOP;
            boolean matchTop = topDistance < radius + SLOP;

            mColumnMatch = new GridMatch(SegmentType.LEFT, 0, x1, column, matchLeft, 0);
            mRowMatch = new GridMatch(SegmentType.TOP, 0, y1, row, matchTop, 0);
        }
    }

    /**
     * Adds a match to align the left edge with some other edge.
     */
    private void addLeftSideMatch(int x1, List<GridMatch> columnMatches, int max) {
        int column = mGrid.getClosestColumn(x1);
        int columnX = mGrid.getColumnX(column);
        int distance = abs(columnX - x1);
        if (distance <= max) {
            columnMatches.add(new GridMatch(SegmentType.LEFT, distance, columnX, column,
                    false, UNDEFINED));
        }
    }

    /**
     * Adds a match to align the right edge with some other edge.
     */
    private void addRightSideMatch(int x2, List<GridMatch> columnMatches, int max) {
        // TODO: Only match the right hand side if the drag bounds fit fully within the
        // cell! Ditto for match below.
        int columnRight = mGrid.getClosestColumn(x2);
        int rightDistance = mGrid.getColumnDistance(columnRight, x2);
        if (rightDistance < max) {
            columnMatches.add(new GridMatch(SegmentType.RIGHT, rightDistance,
                    mGrid.getColumnX(columnRight), columnRight, false, UNDEFINED));
        }
    }

    /**
     * Adds a horizontal match with the center axis of the GridLayout
     */
    private void addCenterColumnMatch(Rect bounds, int x1, int y1, int x2, int y2,
            List<GridMatch> columnMatches, int max) {
        Collection<INode> intersectsRow = mGrid.getIntersectsRow(y1, y2);
        if (intersectsRow.size() == 0) {
            // Offer centering on this row since there isn't anything there
            int matchedLine = bounds.centerX();
            int distance = abs((x1 + x2) / 2 - matchedLine);
            if (distance <= 2 * max) {
                boolean createCell = false; // always just put in column 0
                columnMatches.add(new GridMatch(SegmentType.CENTER_HORIZONTAL, distance,
                        matchedLine, 0 /* column */, createCell, UNDEFINED));
            }
        }
    }

    /**
     * Adds a match to align the top edge with some other edge.
     */
    private void addTopMatch(int y1, List<GridMatch> rowMatches, int max, int row, int rowY) {
        int distance = mGrid.getRowDistance(row, y1);
        if (distance <= max) {
            rowMatches.add(new GridMatch(SegmentType.TOP, distance, rowY, row, false,
                    UNDEFINED));
        }
    }

    /**
     * Adds a match to align the bottom edge with some other edge.
     */
    private void addBottomMatch(int y2, List<GridMatch> rowMatches, int max) {
        int rowBottom = mGrid.getClosestRow(y2);
        int distance = mGrid.getRowDistance(rowBottom, y2);
        if (distance < max) {
            int rowY = mGrid.getRowY(rowBottom);
            rowMatches.add(new GridMatch(SegmentType.BOTTOM, distance, rowY,
                    rowBottom, false, UNDEFINED));
        }
    }

    /**
     * Adds a baseline match, if applicable.
     */
    private void addBaselineMatch(int dragBaseline, int y1, List<GridMatch> rowMatches, int max,
            int row, int rowY) {
        int dragBaselineY = y1 + dragBaseline;
        int rowBaseline = mGrid.getBaseline(row);
        if (rowBaseline != -1) {
            int rowBaselineY = rowY + rowBaseline;
            int distance = abs(dragBaselineY - rowBaselineY);
            if (distance < max) {
                rowMatches.add(new GridMatch(SegmentType.BASELINE, distance, rowBaselineY, row,
                        false, UNDEFINED));
            }
        }
    }

    /**
     * Computes a horizontal "gap" match - a preferred distance from the nearest edge,
     * including margin edges
     */
    private void addColumnGapMatch(Rect bounds, int x1, int x2, List<GridMatch> columnMatches,
            int max) {
        if (x1 < bounds.x + MARGIN_SIZE + max) {
            int matchedLine = bounds.x + MARGIN_SIZE;
            int distance = abs(matchedLine - x1);
            if (distance <= max) {
                boolean createCell = mGrid.getColumnX(mGrid.getColumn(matchedLine)) != matchedLine;
                columnMatches.add(new GridMatch(SegmentType.LEFT, distance, matchedLine,
                        0, createCell, MARGIN_SIZE));
            }
        } else if (x2 > bounds.x2() - MARGIN_SIZE - max) {
            int matchedLine = bounds.x2() - MARGIN_SIZE;
            int distance = abs(matchedLine - x2);
            if (distance <= max) {
                // This does not yet work properly; we need to use columnWeights to achieve this
                //boolean createCell = mGrid.getColumnX(mGrid.getColumn(matchedLine)) != matchedLine;
                //columnMatches.add(new GridMatch(SegmentType.RIGHT, distance, matchedLine,
                //        mGrid.actualColumnCount - 1, createCell, MARGIN_SIZE));
            }
        } else {
            int columnRight = mGrid.getColumn(x1 - SHORT_GAP_DP);
            int columnX = mGrid.getColumnMaxX(columnRight);
            int matchedLine = columnX + SHORT_GAP_DP;
            int distance = abs(matchedLine - x1);
            if (distance <= max) {
                boolean createCell = mGrid.getColumnX(mGrid.getColumn(matchedLine)) != matchedLine;
                columnMatches.add(new GridMatch(SegmentType.LEFT, distance, matchedLine,
                        columnRight, createCell, SHORT_GAP_DP));
            }

            // Add a column directly adjacent (no gap)
            columnRight = mGrid.getColumn(x1);
            columnX = mGrid.getColumnMaxX(columnRight);
            matchedLine = columnX;
            distance = abs(matchedLine - x1);

            // Let's say you have this arrangement:
            //     [button1][button2]
            // This is two columns, where the right hand side edge of column 1 is
            // flush with the left side edge of column 2, because in fact the width of
            // button1 is what defines the width of column 1, and that in turn is what
            // defines the left side position of column 2.
            //
            // In this case we don't want to consider inserting a new column at the
            // right hand side of button1 a better match than matching left on column 2.
            // Therefore, to ensure that this doesn't happen, we "penalize" right column
            // matches such that they don't get preferential treatment when the matching
            // line is on the left side of the column.
            distance += 2;

            if (distance <= max) {
                boolean createCell = mGrid.getColumnX(mGrid.getColumn(matchedLine)) != matchedLine;
                columnMatches.add(new GridMatch(SegmentType.LEFT, distance, matchedLine,
                        columnRight, createCell, 0));
            }
        }
    }

    /**
     * Computes a vertical "gap" match - a preferred distance from the nearest edge,
     * including margin edges
     */
    private void addRowGapMatch(Rect bounds, int y1, int y2, List<GridMatch> rowMatches, int max) {
        if (y1 < bounds.y + MARGIN_SIZE + max) {
            int matchedLine = bounds.y + MARGIN_SIZE;
            int distance = abs(matchedLine - y1);
            if (distance <= max) {
                boolean createCell = mGrid.getRowY(mGrid.getRow(matchedLine)) != matchedLine;
                rowMatches.add(new GridMatch(SegmentType.TOP, distance, matchedLine,
                        0, createCell, MARGIN_SIZE));
            }
        } else if (y2 > bounds.y2() - MARGIN_SIZE - max) {
            int matchedLine = bounds.y2() - MARGIN_SIZE;
            int distance = abs(matchedLine - y2);
            if (distance <= max) {
                // This does not yet work properly; we need to use columnWeights to achieve this
                //boolean createCell = mGrid.getRowY(mGrid.getRow(matchedLine)) != matchedLine;
                //rowMatches.add(new GridMatch(SegmentType.BOTTOM, distance, matchedLine,
                //        mGrid.actualRowCount - 1, createCell, MARGIN_SIZE));
            }
        } else {
            int rowBottom = mGrid.getRow(y1 - SHORT_GAP_DP);
            int rowY = mGrid.getRowMaxY(rowBottom);
            int matchedLine = rowY + SHORT_GAP_DP;
            int distance = abs(matchedLine - y1);
            if (distance <= max) {
                boolean createCell = mGrid.getRowY(mGrid.getRow(matchedLine)) != matchedLine;
                rowMatches.add(new GridMatch(SegmentType.TOP, distance, matchedLine,
                        rowBottom, createCell, SHORT_GAP_DP));
            }

            // Add a row directly adjacent (no gap)
            rowBottom = mGrid.getRow(y1);
            rowY = mGrid.getRowMaxY(rowBottom);
            matchedLine = rowY;
            distance = abs(matchedLine - y1);
            distance += 2; // See explanation in addColumnGapMatch
            if (distance <= max) {
                boolean createCell = mGrid.getRowY(mGrid.getRow(matchedLine)) != matchedLine;
                rowMatches.add(new GridMatch(SegmentType.TOP, distance, matchedLine,
                        rowBottom, createCell, 0));
            }

        }
    }

    /**
     * Called when a node is dropped in free-form mode. This will insert the dragged
     * element into the grid and returns the newly created node.
     *
     * @param targetNode the GridLayout node
     * @param element the dragged element
     * @return the newly created {@link INode}
     */
    public INode handleFreeFormDrop(INode targetNode, IDragElement element) {
        assert mRowMatch != null;
        assert mColumnMatch != null;

        String fqcn = element.getFqcn();

        INode newChild = null;

        Rect bounds = element.getBounds();
        int row = mRowMatch.cellIndex;
        int column = mColumnMatch.cellIndex;

        if (targetNode.getChildren().length == 0) {
            //
            // Set up the initial structure:
            //
            //
            //    Fixed                                 Fixed
            //     Size                                  Size
            //    Column       Expanding Column         Column
            //   +-----+-------------------------------+-----+
            //   |     |                               |     |
            //   | 0,0 |              0,1              | 0,2 | Fixed Size Row
            //   |     |                               |     |
            //   +-----+-------------------------------+-----+
            //   |     |                               |     |
            //   |     |                               |     |
            //   |     |                               |     |
            //   | 1,0 |              1,1              | 1,2 | Expanding Row
            //   |     |                               |     |
            //   |     |                               |     |
            //   |     |                               |     |
            //   +-----+-------------------------------+-----+
            //   |     |                               |     |
            //   | 2,0 |              2,1              | 2,2 | Fixed Size Row
            //   |     |                               |     |
            //   +-----+-------------------------------+-----+
            //
            // This is implemented in GridLayout by the following grid, where
            // SC1 has columnWeight=1 and SR1 has rowWeight=1.
            // (SC=Space for Column, SR=Space for Row)
            //
            //   +------+-------------------------------+------+
            //   |      |                               |      |
            //   | SCR0 |             SC1               | SC2  |
            //   |      |                               |      |
            //   +------+-------------------------------+------+
            //   |      |                               |      |
            //   |      |                               |      |
            //   |      |                               |      |
            //   | SR1  |                               |      |
            //   |      |                               |      |
            //   |      |                               |      |
            //   |      |                               |      |
            //   +------+-------------------------------+------+
            //   |      |                               |      |
            //   | SR2  |                               |      |
            //   |      |                               |      |
            //   +------+-------------------------------+------+
            //
            // Note that when we split columns and rows here, if splitting the expanding
            // row or column then the row or column weight should be moved to the right or
            // bottom half!


            int columnX = mGrid.getColumnX(column);
            int rowY = mGrid.getRowY(row);

            targetNode.setAttribute(ANDROID_URI, ATTR_COLUMN_COUNT, VALUE_1);
            //targetNode.setAttribute(ANDROID_URI, ATTR_COLUMN_COUNT, "3");
            //INode scr0 = addSpacer(targetNode, -1, 0, 0, 1, 1);
            //INode sc1 = addSpacer(targetNode, -1, 0, 1, 0, 0);
            //INode sc2 = addSpacer(targetNode, -1, 0, 2, 1, 0);
            //INode sr1 = addSpacer(targetNode, -1, 1, 0, 0, 0);
            //INode sr2 = addSpacer(targetNode, -1, 2, 0, 0, 1);
            //sc1.setAttribute(ANDROID_URI, ATTR_LAYOUT_COLUMN_WEIGHT, VALUE_1);
            //sr1.setAttribute(ANDROID_URI, ATTR_LAYOUT_ROW_WEIGHT, VALUE_1);
            //sc1.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, VALUE_FILL_HORIZONTAL);
            //sr1.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, VALUE_FILL_VERTICAL);

            mGrid.loadFromXml();
            column = mGrid.getColumn(columnX);
            row = mGrid.getRow(rowY);
        }

        int startX, endX;
        if (mColumnMatch.type == SegmentType.RIGHT) {
            endX = mColumnMatch.matchedLine - 1;
            startX = endX - bounds.w;
            column = mGrid.getColumn(startX);
        } else {
            startX = mColumnMatch.matchedLine; // TODO: What happens on type=RIGHT?
            endX = startX + bounds.w;
        }
        int startY, endY;
        if (mRowMatch.type == SegmentType.BOTTOM) {
            endY = mRowMatch.matchedLine - 1;
            startY = endY - bounds.h;
            row = mGrid.getRow(startY);
        } else if (mRowMatch.type == SegmentType.BASELINE) {
            // TODO: The rowSpan should always be 1 for baseline alignments, since
            // otherwise the alignment won't work!
            startY = endY = mRowMatch.matchedLine;
        } else {
            startY = mRowMatch.matchedLine;
            endY = startY + bounds.h;
        }
        int endColumn = mGrid.getColumn(endX);
        int endRow = mGrid.getRow(endY);
        int columnSpan = endColumn - column + 1;
        int rowSpan = endRow - row + 1;

        // Make sure my math was right:
        if (mRowMatch.type == SegmentType.BASELINE) {
            assert rowSpan == 1 : rowSpan;
        }

        // If the item almost fits into the row (at most N % bigger) then just enlarge
        // the row; don't add a rowspan since that will defeat baseline alignment etc
        if (!mRowMatch.createCell && bounds.h <= MAX_CELL_DIFFERENCE * mGrid.getRowHeight(
                mRowMatch.type == SegmentType.BOTTOM ? endRow : row, 1)) {
            if (mRowMatch.type == SegmentType.BOTTOM) {
                row += rowSpan - 1;
            }
            rowSpan = 1;
        }
        if (!mColumnMatch.createCell && bounds.w <= MAX_CELL_DIFFERENCE * mGrid.getColumnWidth(
                mColumnMatch.type == SegmentType.RIGHT ? endColumn : column, 1)) {
            if (mColumnMatch.type == SegmentType.RIGHT) {
                column += columnSpan - 1;
            }
            columnSpan = 1;
        }

        if (mColumnMatch.type == SegmentType.CENTER_HORIZONTAL) {
            column = 0;
            columnSpan = mGrid.actualColumnCount;
        }

        // Temporary: Ensure we don't get in trouble with implicit positions
        mGrid.applyPositionAttributes();

        // Split cells to make a new column
        if (mColumnMatch.createCell) {
            int columnWidthPx = mGrid.getColumnDistance(column, mColumnMatch.matchedLine);
            //assert columnWidthPx == columnMatch.distance; // TBD? IF so simplify
            int columnWidthDp = mRule.getRulesEngine().pxToDp(columnWidthPx);

            int maxX = mGrid.getColumnMaxX(column);
            boolean insertMarginColumn = false;
            if (mColumnMatch.margin == 0) {
                columnWidthDp = 0;
            } else if (mColumnMatch.margin != UNDEFINED) {
                int distance = abs(mColumnMatch.matchedLine - (maxX + mColumnMatch.margin));
                insertMarginColumn = column > 0 && distance < 2;
                if (insertMarginColumn) {
                    int margin = mColumnMatch.margin;
                    if (ViewMetadataRepository.INSETS_SUPPORTED) {
                        IViewMetadata metadata = mRule.getRulesEngine().getMetadata(fqcn);
                        if (metadata != null) {
                            Margins insets = metadata.getInsets();
                            if (insets != null) {
                                // TODO:
                                // Consider left or right side attachment
                                // TODO: Also consider inset of element on cell to the left
                                margin -= insets.left;
                            }
                        }
                    }

                    columnWidthDp = mRule.getRulesEngine().pxToDp(margin);
                }
            }

            column++;
            mGrid.splitColumn(column, insertMarginColumn, columnWidthDp, mColumnMatch.matchedLine);
            if (insertMarginColumn) {
                column++;
            }
            // TODO: This grid refresh is a little risky because we may have added a new
            // child (spacer) which has no bounds yet!
            mGrid.loadFromXml();
        }

        // Split cells to make a new  row
        if (mRowMatch.createCell) {
            int rowHeightPx = mGrid.getRowDistance(row, mRowMatch.matchedLine);
            //assert rowHeightPx == rowMatch.distance; // TBD? If so simplify
            int rowHeightDp = mRule.getRulesEngine().pxToDp(rowHeightPx);

            int maxY = mGrid.getRowMaxY(row);
            boolean insertMarginRow = false;
            if (mRowMatch.margin == 0) {
                rowHeightDp = 0;
            } else if (mRowMatch.margin != UNDEFINED) {
                int distance = abs(mRowMatch.matchedLine - (maxY + mRowMatch.margin));
                insertMarginRow = row > 0 && distance < 2;
                if (insertMarginRow) {
                    int margin = mRowMatch.margin;
                    IViewMetadata metadata = mRule.getRulesEngine().getMetadata(element.getFqcn());
                    if (metadata != null) {
                        Margins insets = metadata.getInsets();
                        if (insets != null) {
                            // TODO:
                            // Consider left or right side attachment
                            // TODO: Also consider inset of element on cell to the left
                            margin -= insets.top;
                        }
                    }

                    rowHeightDp = mRule.getRulesEngine().pxToDp(margin);
                }
            }

            row++;
            mGrid.splitRow(row, insertMarginRow, rowHeightDp, mRowMatch.matchedLine);
            if (insertMarginRow) {
                row++;
            }
            mGrid.loadFromXml();
        }

        // Figure out where to insert the new child

        int index = mGrid.getInsertIndex(row, column);
        if (index == -1) {
            // Couldn't find a later place to insert
            newChild = targetNode.appendChild(fqcn);
        } else {
            GridModel.ViewData next = mGrid.getView(index);

            newChild = targetNode.insertChildAt(fqcn, index);

            // Must also apply positions to the following child to ensure
            // that the new child doesn't affect the implicit numbering!
            // TODO: We can later check whether the implied number is equal to
            // what it already is such that we don't need this
            next.applyPositionAttributes();
        }

        // Set the cell position of the new widget
        if (mColumnMatch.type == SegmentType.RIGHT) {
            newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, VALUE_RIGHT);
        } else if (mColumnMatch.type == SegmentType.CENTER_HORIZONTAL) {
            newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, VALUE_CENTER_HORIZONTAL);
        }
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_COLUMN, Integer.toString(column));
        if (mRowMatch.type == SegmentType.BOTTOM) {
            String value = VALUE_BOTTOM;
            if (mColumnMatch.type == SegmentType.RIGHT) {
                value = value + '|' + VALUE_RIGHT;
            }
            newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_GRAVITY, value);
        }
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_ROW, Integer.toString(row));

        // Apply spans to ensure that the widget can fit without pushing columns
        if (columnSpan > 1) {
            newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_COLUMN_SPAN,
                    Integer.toString(columnSpan));
        }
        if (rowSpan > 1) {
            newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_ROW_SPAN, Integer.toString(rowSpan));
        }

        // Ensure that we don't store columnCount=0
        if (mGrid.actualColumnCount == 0) {
            mGrid.layout.setAttribute(ANDROID_URI, ATTR_COLUMN_COUNT, VALUE_1);
        }

        return newChild;
    }

    /**
     * Called when a drop is completed and we're in grid-editing mode. This will insert
     * the dragged element into the target cell.
     *
     * @param targetNode the GridLayout node
     * @param element the dragged element
     * @return the newly created node
     */
    public INode handleGridModeDrop(INode targetNode, IDragElement element) {
        String fqcn = element.getFqcn();
        INode newChild = targetNode.appendChild(fqcn);

        if (mColumnMatch.createCell) {
            mGrid.addColumn(mColumnMatch.cellIndex,
                    newChild, UNDEFINED, false, UNDEFINED, UNDEFINED);
        }
        if (mRowMatch.createCell) {
            mGrid.loadFromXml();
            mGrid.addRow(mRowMatch.cellIndex, newChild, UNDEFINED, false, UNDEFINED, UNDEFINED);
        }

        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_COLUMN,
                Integer.toString(mColumnMatch.cellIndex));
        newChild.setAttribute(ANDROID_URI, ATTR_LAYOUT_ROW,
                Integer.toString(mRowMatch.cellIndex));

        return newChild;
    }

    /**
     * Returns the best horizontal match
     *
     * @return the best horizontal match, or null if there is no match
     */
    public GridMatch getColumnMatch() {
        return mColumnMatch;
    }

    /**
     * Returns the best vertical match
     *
     * @return the best vertical match, or null if there is no match
     */
    public GridMatch getRowMatch() {
        return mRowMatch;
    }

    /**
     * Returns the grid used by the drop handler
     *
     * @return the grid used by the drop handler, never null
     */
    public GridModel getGrid() {
        return mGrid;
    }
}