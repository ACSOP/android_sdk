/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.uimodel;

import static com.android.ide.common.layout.LayoutConstants.ATTR_ID;
import static com.android.ide.common.layout.LayoutConstants.ATTR_STYLE;
import static com.android.ide.common.resources.ResourceResolver.PREFIX_ANDROID_RESOURCE_REF;
import static com.android.ide.eclipse.adt.AdtConstants.ANDROID_PKG;

import com.android.ide.common.api.IAttributeInfo;
import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.TextAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.ui.SectionHelper;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.ui.ReferenceChooserDialog;
import com.android.ide.eclipse.adt.internal.ui.ResourceChooser;
import com.android.resources.ResourceType;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.TableWrapData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents an XML attribute for a resource that can be modified using a simple text field or
 * a dialog to choose an existing resource.
 * <p/>
 * It can be configured to represent any kind of resource, by providing the desired
 * {@link ResourceType} in the constructor.
 * <p/>
 * See {@link UiTextAttributeNode} for more information.
 */
public class UiResourceAttributeNode extends UiTextAttributeNode {
    private ResourceType mType;

    public UiResourceAttributeNode(ResourceType type,
            AttributeDescriptor attributeDescriptor, UiElementNode uiParent) {
        super(attributeDescriptor, uiParent);

        mType = type;
    }

    /* (non-java doc)
     * Creates a label widget and an associated text field.
     * <p/>
     * As most other parts of the android manifest editor, this assumes the
     * parent uses a table layout with 2 columns.
     */
    @Override
    public void createUiControl(final Composite parent, IManagedForm managedForm) {
        setManagedForm(managedForm);
        FormToolkit toolkit = managedForm.getToolkit();
        TextAttributeDescriptor desc = (TextAttributeDescriptor) getDescriptor();

        Label label = toolkit.createLabel(parent, desc.getUiName());
        label.setLayoutData(new TableWrapData(TableWrapData.LEFT, TableWrapData.MIDDLE));
        SectionHelper.addControlTooltip(label, DescriptorsUtils.formatTooltip(desc.getTooltip()));

        Composite composite = toolkit.createComposite(parent);
        composite.setLayoutData(new TableWrapData(TableWrapData.FILL_GRAB, TableWrapData.MIDDLE));
        GridLayout gl = new GridLayout(2, false);
        gl.marginHeight = gl.marginWidth = 0;
        composite.setLayout(gl);
        // Fixes missing text borders under GTK... also requires adding a 1-pixel margin
        // for the text field below
        toolkit.paintBordersFor(composite);

        final Text text = toolkit.createText(composite, getCurrentValue());
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalIndent = 1;  // Needed by the fixed composite borders under GTK
        text.setLayoutData(gd);
        Button browseButton = toolkit.createButton(composite, "Browse...", SWT.PUSH);

        setTextWidget(text);

        // TODO Add a validator using onAddModifyListener

        browseButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String result = showDialog(parent.getShell(), text.getText().trim());
                if (result != null) {
                    text.setText(result);
                }
            }
        });
    }

    /**
     * Shows a dialog letting the user choose a set of enum, and returns a string
     * containing the result.
     */
    public String showDialog(Shell shell, String currentValue) {
        // we need to get the project of the file being edited.
        UiElementNode uiNode = getUiParent();
        AndroidXmlEditor editor = uiNode.getEditor();
        IProject project = editor.getProject();
        if (project != null) {
            // get the resource repository for this project and the system resources.
            ResourceRepository projectRepository =
                ResourceManager.getInstance().getProjectResources(project);

            if (mType != null) {
                // get the Target Data to get the system resources
                AndroidTargetData data = editor.getTargetData();
                ResourceRepository frameworkRepository = data.getFrameworkResources();

                // open a resource chooser dialog for specified resource type.
                ResourceChooser dlg = new ResourceChooser(project,
                        mType,
                        projectRepository,
                        frameworkRepository,
                        shell);

                dlg.setCurrentResource(currentValue);

                if (dlg.open() == Window.OK) {
                    return dlg.getCurrentResource();
                }
            } else {
                ReferenceChooserDialog dlg = new ReferenceChooserDialog(
                        project,
                        projectRepository,
                        shell);

                dlg.setCurrentResource(currentValue);

                if (dlg.open() == Window.OK) {
                    return dlg.getCurrentResource();
                }
            }
        }

        return null;
    }

    /**
     * Gets all the values one could use to auto-complete a "resource" value in an XML
     * content assist.
     * <p/>
     * Typically the user is editing the value of an attribute in a resource XML, e.g.
     *   <pre> "&lt;Button android:test="@string/my_[caret]_string..." </pre>
     * <p/>
     *
     * "prefix" is the value that the user has typed so far (or more exactly whatever is on the
     * left side of the insertion point). In the example above it would be "@style/my_".
     * <p/>
     *
     * To avoid a huge long list of values, the completion works on two levels:
     * <ul>
     * <li> If a resource type as been typed so far (e.g. "@style/"), then limit the values to
     *      the possible completions that match this type.
     * <li> If no resource type as been typed so far, then return the various types that could be
     *      completed. So if the project has only strings and layouts resources, for example,
     *      the returned list will only include "@string/" and "@layout/".
     * </ul>
     *
     * Finally if anywhere in the string we find the special token "android:", we use the
     * current framework system resources rather than the project resources.
     * This works for both "@android:style/foo" and "@style/android:foo" conventions even though
     * the reconstructed name will always be of the former form.
     *
     * Note that "android:" here is a keyword specific to Android resources and should not be
     * mixed with an XML namespace for an XML attribute name.
     */
    @Override
    public String[] getPossibleValues(String prefix) {
        return computeResourceStringMatches(getUiParent().getEditor(), getDescriptor(), prefix);
    }

    public static String[] computeResourceStringMatches(AndroidXmlEditor editor,
            AttributeDescriptor attributeDescriptor, String prefix) {
        ResourceRepository repository = null;
        boolean isSystem = false;

        if (prefix == null || !prefix.regionMatches(1, ANDROID_PKG, 0, ANDROID_PKG.length())) {
            IProject project = editor.getProject();
            if (project != null) {
                // get the resource repository for this project and the system resources.
                repository = ResourceManager.getInstance().getProjectResources(project);
            }
        } else {
            // If there's a prefix with "android:" in it, use the system resources
            // Non-public framework resources are filtered out later.
            AndroidTargetData data = editor.getTargetData();
            repository = data.getFrameworkResources();
            isSystem = true;
        }

        // Get list of potential resource types, either specific to this project
        // or the generic list.
        Collection<ResourceType> resTypes = (repository != null) ?
                    repository.getAvailableResourceTypes() :
                    EnumSet.allOf(ResourceType.class);

        // Get the type name from the prefix, if any. It's any word before the / if there's one
        String typeName = null;
        if (prefix != null) {
            Matcher m = Pattern.compile(".*?([a-z]+)/.*").matcher(prefix);      //$NON-NLS-1$
            if (m.matches()) {
                typeName = m.group(1);
            }
        }

        // Now collect results
        List<String> results = new ArrayList<String>();

        if (typeName == null) {
            // This prefix does not have a / in it, so the resource string is either empty
            // or does not have the resource type in it. Simply offer the list of potential
            // resource types.

            for (ResourceType resType : resTypes) {
                if (isSystem) {
                    results.add(PREFIX_ANDROID_RESOURCE_REF + resType.getName() + '/');
                } else {
                    results.add('@' + resType.getName() + '/');
                }
                if (resType == ResourceType.ID) {
                    // Also offer the + version to create an id from scratch
                    results.add("@+" + resType.getName() + '/');    //$NON-NLS-1$
                }
            }

            // Also add in @android: prefix to completion such that if user has typed
            // "@an" we offer to complete it.
            if (prefix == null ||
                    ANDROID_PKG.regionMatches(0, prefix, 1, prefix.length() - 1)) {
                results.add(PREFIX_ANDROID_RESOURCE_REF);
            }
        } else if (repository != null) {
            // We have a style name and a repository. Find all resources that match this
            // type and recreate suggestions out of them.

            ResourceType resType = ResourceType.getEnum(typeName);
            if (resType != null) {
                StringBuilder sb = new StringBuilder();
                sb.append('@');
                if (prefix != null && prefix.indexOf('+') >= 0) {
                    sb.append('+');
                }

                if (isSystem) {
                    sb.append(ANDROID_PKG).append(':');
                }

                sb.append(typeName).append('/');
                String base = sb.toString();

                for (ResourceItem item : repository.getResourceItemsOfType(resType)) {
                    results.add(base + item.getName());
                }
            }
        }

        if (attributeDescriptor != null) {
            sortAttributeChoices(attributeDescriptor, results);
        } else {
            Collections.sort(results);
        }

        return results.toArray(new String[results.size()]);
    }

    /**
     * Attempts to sort the attribute values to bubble up the most likely choices to
     * the top.
     * <p>
     * For example, if you are editing a style attribute, it's likely that among the
     * resource values you would rather see @style or @android than @string.
     */
    private static void sortAttributeChoices(AttributeDescriptor descriptor,
            List<String> choices) {
        final IAttributeInfo attributeInfo = descriptor.getAttributeInfo();
        Collections.sort(choices, new Comparator<String>() {
            public int compare(String s1, String s2) {
                int compare = score(attributeInfo, s1) - score(attributeInfo, s2);
                if (compare == 0) {
                    // Sort alphabetically as a fallback
                    compare = s1.compareTo(s2);
                }
                return compare;
            }
        });
    }

    /** Compute a suitable sorting score for the given  */
    private static final int score(IAttributeInfo attributeInfo, String value) {
        if (value.equals(PREFIX_ANDROID_RESOURCE_REF)) {
            return -1;
        }

        for (Format format : attributeInfo.getFormats()) {
            String type = null;
            switch (format) {
                case BOOLEAN:
                    type = "bool"; //$NON-NLS-1$
                    break;
                case COLOR:
                    type = "color"; //$NON-NLS-1$
                    break;
                case DIMENSION:
                    type = "dimen"; //$NON-NLS-1$
                    break;
                case INTEGER:
                    type = "integer"; //$NON-NLS-1$
                    break;
                case STRING:
                    type = "string"; //$NON-NLS-1$
                    break;
                // default: REFERENCE, FLAG, ENUM, etc - don't have type info about individual
                // elements to help make a decision
            }

            if (type != null) {
                if (value.startsWith('@' + type + '/')) {
                    return -2;
                }

                if (value.startsWith(PREFIX_ANDROID_RESOURCE_REF + type + '/')) {
                    return -2;
                }
            }
        }

        // Handle a few more cases not covered by the Format metadata check
        String type = null;

        String attribute = attributeInfo.getName();
        if (attribute.equals(ATTR_ID)) {
            type = "id"; //$NON-NLS-1$
        } else if (attribute.equals(ATTR_STYLE)) {
            type = "style"; //$NON-NLS-1$
        } else if (attribute.equals(LayoutDescriptors.ATTR_LAYOUT)) {
            type = "layout"; //$NON-NLS-1$
        } else if (attribute.equals("drawable")) { //$NON-NLS-1$
            type = "drawable"; //$NON-NLS-1$
        }

        if (type != null) {
            if (value.startsWith('@' + type + '/')) {
                return -2;
            }

            if (value.startsWith(PREFIX_ANDROID_RESOURCE_REF + type + '/')) {
                return -2;
            }
        }

        return 0;
    }
}
