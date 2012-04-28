/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;

import com.android.ide.common.api.DropFeedback;
import com.android.ide.common.api.IClientRulesEngine;
import com.android.ide.common.api.IDragElement;
import com.android.ide.common.api.INode;
import com.android.ide.common.api.IValidator;
import com.android.ide.common.api.IViewMetadata;
import com.android.ide.common.api.IViewRule;
import com.android.ide.common.api.Margins;
import com.android.ide.common.api.Point;
import com.android.ide.common.api.Rect;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * Common layout helpers from LayoutRule tests
 */
public class LayoutTestBase extends TestCase {
    /**
     * Helper function used by tests to drag a button into a canvas containing
     * the given children.
     *
     * @param rule The rule to test on
     * @param targetNode The target layout node to drag into
     * @param dragBounds The (original) bounds of the dragged item
     * @param dropPoint The drag point we should drag to and drop
     * @param secondDropPoint An optional second drag point to drag to before
     *            drawing graphics and dropping (or null if not applicable)
     * @param insertIndex The expected insert position we end up with after
     *            dropping at the dropPoint
     * @param currentIndex If the dragged widget is already in the canvas this
     *            should be its child index; if not, pass in -1
     * @param graphicsFragments This is a varargs array of String fragments
     *            we expect to see in the graphics output on the drag over
     *            event.
     * @return The inserted node
     */
    protected INode dragInto(IViewRule rule, INode targetNode, Rect dragBounds, Point dropPoint,
            Point secondDropPoint, int insertIndex, int currentIndex,
            String... graphicsFragments) {

        String draggedButtonId = (currentIndex == -1) ? "@+id/DraggedButton" : targetNode
                .getChildren()[currentIndex].getStringAttr(ANDROID_URI, ATTR_ID);

        IDragElement[] elements = TestDragElement.create(TestDragElement.create(
                "android.widget.Button", dragBounds).id(draggedButtonId));

        // Enter target
        DropFeedback feedback = rule.onDropEnter(targetNode, null/*targetView*/, elements);
        assertNotNull(feedback);
        assertFalse(feedback.invalidTarget);
        assertNotNull(feedback.painter);

        if (currentIndex != -1) {
            feedback.sameCanvas = true;
        }

        // Move near top left corner of the target
        feedback = rule.onDropMove(targetNode, elements, feedback, dropPoint);
        assertNotNull(feedback);

        if (secondDropPoint != null) {
            feedback = rule.onDropMove(targetNode, elements, feedback, secondDropPoint);
            assertNotNull(feedback);
        }

        if (insertIndex == -1) {
            assertTrue(feedback.invalidTarget);
        } else {
            assertFalse(feedback.invalidTarget);
        }

        // Paint feedback and make sure it's what we expect
        TestGraphics graphics = new TestGraphics();
        assertNotNull(feedback.painter);
        feedback.painter.paint(graphics, targetNode, feedback);
        String drawn = graphics.getDrawn().toString();

        // Check that each graphics fragment is drawn
        for (String fragment : graphicsFragments) {
            if (!drawn.contains(fragment)) {
                // Get drawn-output since unit test truncates message in below
                // contains-assertion
                System.out.println("Could not find: " + fragment);
                System.out.println("Full graphics output: " + drawn);
            }
            assertTrue(fragment + " not found; full=" + drawn, drawn.contains(fragment));
        }

        // Attempt a drop?
        if (insertIndex == -1) {
            // No, not expected to succeed (for example, when drop point is over an
            // invalid region in RelativeLayout) - just return.
            return null;
        }
        int childrenCountBefore = targetNode.getChildren().length;
        rule.onDropped(targetNode, elements, feedback, dropPoint);

        if (currentIndex == -1) {
            // Inserting new from outside
            assertEquals(childrenCountBefore+1, targetNode.getChildren().length);
        } else {
            // Moving from existing; must remove in old position first
            ((TestNode) targetNode).removeChild(currentIndex);

            assertEquals(childrenCountBefore, targetNode.getChildren().length);
        }
        // Ensure that it's inserted in the right place
        String actualId = targetNode.getChildren()[insertIndex].getStringAttr(
                ANDROID_URI, ATTR_ID);
        if (!draggedButtonId.equals(actualId)) {
            // Using assertEquals instead of fail to get nice diff view on test
            // failure
            List<String> childrenIds = new ArrayList<String>();
            for (INode child : targetNode.getChildren()) {
                childrenIds.add(child.getStringAttr(ANDROID_URI, ATTR_ID));
            }
            int index = childrenIds.indexOf(draggedButtonId);
            String message = "Button found at index " + index + " instead of " + insertIndex
                    + " among " + childrenIds;
            System.out.println(message);
            assertEquals(message, draggedButtonId, actualId);
        }


        return targetNode.getChildren()[insertIndex];
    }

    /**
     * Utility method for asserting that two collections contain exactly the
     * same elements (regardless of order)
     * @param expected expected collection
     * @param actual  actual collection
     */
    public static void assertContainsSame(Collection<String> expected, Collection<String> actual) {
        if (expected.size() != actual.size()) {
            fail("Collection sizes differ; expected " + expected.size() + " but was "
                    + actual.size());
        }

        // Sort prior to comparison to ensure we have the same elements
        // regardless of order
        List<String> expectedList = new ArrayList<String>(expected);
        Collections.sort(expectedList);
        List<String> actualList = new ArrayList<String>(actual);
        Collections.sort(actualList);
        // Instead of just assertEquals(expectedList, actualList);
        // we iterate one element at a time so we can show the first
        // -difference-.
        for (int i = 0; i < expectedList.size(); i++) {
            String expectedElement = expectedList.get(i);
            String actualElement = actualList.get(i);
            if (!expectedElement.equals(actualElement)) {
                System.out.println("Expected items: " + expectedList);
                System.out.println("Actual items  : " + actualList);
            }
            assertEquals("Collections differ; first difference:", expectedElement, actualElement);
        }
    }

    protected void initialize(IViewRule rule, String fqn) {
        rule.onInitialize(fqn, new TestRulesEngine(fqn));
    }

    private static class TestRulesEngine implements IClientRulesEngine {
        private final String mFqn;

        protected TestRulesEngine(String fqn) {
            mFqn = fqn;
        }

        public void debugPrintf(String msg, Object... params) {
            fail("Not supported in tests yet");
        }

        public void displayAlert(String message) {
            fail("Not supported in tests yet");
        }

        public String displayInput(String message, String value, IValidator filter) {
            fail("Not supported in tests yet");
            return null;
        }

        public String getFqcn() {
            return mFqn;
        }

        public IViewMetadata getMetadata(final String fqcn) {
            return new IViewMetadata() {
                public String getDisplayName() {
                    // This also works when there is no "."
                    return fqcn.substring(fqcn.lastIndexOf('.') + 1);
                }

                public FillPreference getFillPreference() {
                    return ViewMetadataRepository.get().getFillPreference(fqcn);
                }

                public Margins getInsets() {
                    return null;
                }

                public List<String> getTopAttributes() {
                    return ViewMetadataRepository.get().getTopAttributes(fqcn);
                }
            };
        }

        public int getMinApiLevel() {
            return 8;
        }

        public IViewRule loadRule(String fqcn) {
            fail("Not supported in tests yet");
            return null;
        }

        public String displayReferenceInput(String currentValue) {
            fail("Not supported in tests yet");
            return null;
        }

        public IValidator getResourceValidator() {
            fail("Not supported in tests yet");
            return null;
        }

        public String displayResourceInput(String resourceTypeName, String currentValue) {
            fail("Not supported in tests yet");
            return null;
        }

        public String[] displayMarginInput(String all, String left, String right, String top,
                String bottom) {
            fail("Not supported in tests yet");
            return null;
        }

        public String displayIncludeSourceInput() {
            fail("Not supported in tests yet");
            return null;
        }

        public void select(Collection<INode> nodes) {
            fail("Not supported in tests yet");
        }

        public String displayFragmentSourceInput() {
            fail("Not supported in tests yet");
            return null;
        }

        public void layout() {
            fail("Not supported in tests yet");
        }

        public void redraw() {
            fail("Not supported in tests yet");
        }

        public Map<INode, Rect> measureChildren(INode parent, AttributeFilter filter) {
            return null;
        }

        public int pxToDp(int px) {
            fail("Not supported in tests yet");
            return px;
        }

        public String getUniqueId(String prefix) {
            fail("Not supported in tests yet");
            return null;
        }

        public int screenToLayout(int pixels) {
            fail("Not supported in tests yet");
            return 0;
        }

        public int dpToPx(int dp) {
            fail("Not supported in tests yet");
            return 0;
        }
    }

    public void testDummy() {
        // To avoid JUnit warning that this class contains no tests, even though
        // this is an abstract class and JUnit shouldn't try
    }
}
