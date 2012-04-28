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
package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ID_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.NEW_ID_PREFIX;

import com.android.ide.common.layout.BaseLayoutRule;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.AdtProjectTest;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.wst.sse.core.internal.provisional.IndexedRegion;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.w3c.dom.Node;

@SuppressWarnings("restriction") // XML DOM model
public class LayoutMetadataTest extends AdtProjectTest {
    public void testMetadata1() throws Exception {
        Pair<IDocument, UiElementNode> pair = getNode("metadata.xml", "listView1");
        IDocument document = pair.getFirst();
        UiElementNode uiNode = pair.getSecond();
        Node node = uiNode.getXmlNode();

        LayoutMetadata metadata = LayoutMetadata.get();
        assertNull(metadata.getProperty(document, node, "foo"));
        String before =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "    </ListView>";
        assertEquals(before, getText(document, node));

        // Set the property
        metadata.setProperty(document, node,
                "listitem", "@android:layout/simple_list_item_checked");
        String after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "        <!-- Preview: listitem=@android:layout/simple_list_item_checked -->\n" +
            "    </ListView>";
        assertEquals(after, getText(document, node));

        // Set a second property
        metadata.setProperty(document, node,
                "listheader", "@android:layout/browser_link_context_header");
        after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "        <!-- Preview: \n" +
            "            listheader=@android:layout/browser_link_context_header\n" +
            "            listitem=@android:layout/simple_list_item_checked\n" +
            "         -->\n" +
            "    </ListView>";
        assertEquals(after, getText(document, node));

        // Set list item to a different layout
        metadata.setProperty(document, node,
                "listitem", "@android:layout/simple_list_item_single_choice");
        after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "        <!-- Preview: \n" +
            "            listheader=@android:layout/browser_link_context_header\n" +
            "            listitem=@android:layout/simple_list_item_single_choice\n" +
            "         -->\n" +
            "    </ListView>";
        assertEquals(after, getText(document, node));

        // Set header to a different layout
        metadata.setProperty(document, node,
                "listheader", "@layout/foo");
        after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "        <!-- Preview: \n" +
            "            listheader=@layout/foo\n" +
            "            listitem=@android:layout/simple_list_item_single_choice\n" +
            "         -->\n" +
            "    </ListView>";
        assertEquals(after, getText(document, node));

        // Clear out list item
        metadata.setProperty(document, node,
                "listitem", null);
        after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\">\n" +
            "        <!-- Preview: listheader=@layout/foo -->\n" +
            "    </ListView>";
        assertEquals(after, getText(document, node));

        // Clear out list header
        metadata.setProperty(document, node,
                "listheader", null);
        after =
            "<ListView android:layout_width=\"match_parent\" android:id=\"@+id/listView1\"\n" +
            "        android:layout_height=\"wrap_content\"></ListView>";
        assertEquals(after, getText(document, node));

        // Check node expansion on the button which doesn't have an end tag:
        before = "<Button android:text=\"Button\" android:id=\"@+id/button1\"/>";
    }

    public void testMetadata2() throws Exception {
        Pair<IDocument, UiElementNode> pair = getNode("metadata.xml", "button1");
        IDocument document = pair.getFirst();
        UiElementNode uiNode = pair.getSecond();
        Node node = uiNode.getXmlNode();

        LayoutMetadata metadata = LayoutMetadata.get();
        assertNull(metadata.getProperty(document, node, "foo"));
        String before =
            "<Button android:text=\"Button\" android:id=\"@+id/button1\"/>";
        assertEquals(before, getText(document, node));

        // Set the property
        metadata.setProperty(document, node,
                "listitem", "@android:layout/simple_list_item_checked");
        String after =
            "<Button android:text=\"Button\" android:id=\"@+id/button1\">\n" +
            "        <!-- Preview: listitem=@android:layout/simple_list_item_checked -->\n" +
            "    </Button>";
        assertEquals(after, getText(document, node));
    }

    // ==== Test utilities ====

    private static String getText(IDocument document, Node node) throws Exception {
        IndexedRegion region = (IndexedRegion) node;
        // This often returns the wrong value:
        //int length = region.getLength();
        int length = region.getEndOffset() - region.getStartOffset();
        return document.get(region.getStartOffset(), length);
    }

    private Pair<IDocument, UiElementNode> getNode(String filename, String targetId)
            throws Exception, PartInitException {
        IFile file = getLayoutFile(getProject(), filename);
        AdtPlugin.openFile(file, null);
        IEditorPart newEditor = AdtUtils.getActiveEditor();
        assertTrue(newEditor instanceof AndroidXmlEditor);
        AndroidXmlEditor xmlEditor = (AndroidXmlEditor) newEditor;
        IStructuredDocument document = xmlEditor.getStructuredDocument();
        UiElementNode root = xmlEditor.getUiRootNode();
        assertNotNull(root);
        UiElementNode node = findById(root, targetId);
        assertNotNull(node);
        Pair<IDocument, UiElementNode> pair = Pair.<IDocument, UiElementNode>of(document, node);
        return pair;
    }

    private static UiElementNode findById(UiElementNode node, String targetId) {
        assertFalse(targetId.startsWith(NEW_ID_PREFIX));
        assertFalse(targetId.startsWith(ID_PREFIX));

        String id = node.getAttributeValue(ATTR_ID);
        if (id != null && targetId.equals(BaseLayoutRule.stripIdPrefix(id))) {
            return node;
        }

        for (UiElementNode child : node.getUiChildren()) {
            UiElementNode result = findById(child, targetId);
            if (result != null) {
                return result;
            }
        }

        return null;
    }
}
