/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.descriptors;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_URI;
import static com.android.ide.common.layout.LayoutConstants.ATTR_CLASS;
import static com.android.ide.common.layout.LayoutConstants.ATTR_NAME;
import static com.android.ide.common.layout.LayoutConstants.ATTR_TAG;
import static com.android.ide.common.layout.LayoutConstants.FQCN_GESTURE_OVERLAY_VIEW;

import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.resources.platform.AttributeInfo;
import com.android.ide.common.resources.platform.DeclareStyleableInfo;
import com.android.ide.common.resources.platform.ViewClassInfo;
import com.android.ide.common.resources.platform.ViewClassInfo.LayoutParamsInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DocumentDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.IDescriptorProvider;
import com.android.ide.eclipse.adt.internal.editors.descriptors.SeparatorAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.manifest.descriptors.ClassAttributeDescriptor;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Complete description of the layout structure.
 */
public final class LayoutDescriptors implements IDescriptorProvider {

    /**
     * The XML name of the special {@code <include>} layout tag.
     * A synthetic element with that name is created as part of the view descriptors list
     * returned by {@link #getViewDescriptors()}.
     */
    public static final String VIEW_INCLUDE = "include";      //$NON-NLS-1$

    /**
     * The XML name of the special {@code <merge>} layout tag.
     * A synthetic element with that name is created as part of the view descriptors list
     * returned by {@link #getViewDescriptors()}.
     */
    public static final String VIEW_MERGE = "merge";          //$NON-NLS-1$

    /**
     * The XML name of the special {@code <fragment>} layout tag.
     * A synthetic element with that name is created as part of the view descriptors list
     * returned by {@link #getViewDescriptors()}.
     */
    public static final String VIEW_FRAGMENT = "fragment";    //$NON-NLS-1$

    /**
     * The XML name of the special {@code <view>} layout tag. This is used to add generic
     * views with a class attribute to specify the view.
     * <p>
     * TODO: We should add a synthetic descriptor for this, similar to our descriptors for
     * include, merge and requestFocus.
     */
    public static final String VIEW_VIEWTAG = "view";           //$NON-NLS-1$

    /**
     * The XML name of the special {@code <requestFocus>} layout tag.
     * A synthetic element with that name is created as part of the view descriptors list
     * returned by {@link #getViewDescriptors()}.
     */
    public static final String REQUEST_FOCUS = "requestFocus";//$NON-NLS-1$

    /**
     * The attribute name of the include tag's url naming the resource to be inserted
     * <p>
     * <b>NOTE</b>: The layout attribute is NOT in the Android namespace!
     */
    public static final String ATTR_LAYOUT = "layout"; //$NON-NLS-1$

    // Public attributes names, attributes descriptors and elements descriptors
    public static final String ID_ATTR = "id"; //$NON-NLS-1$

    /** The document descriptor. Contains all layouts and views linked together. */
    private DocumentDescriptor mRootDescriptor =
        new DocumentDescriptor("layout_doc", null); //$NON-NLS-1$

    /** The list of all known ViewLayout descriptors. */
    private List<ViewElementDescriptor> mLayoutDescriptors =
        new ArrayList<ViewElementDescriptor>();

    /** Read-Only list of View Descriptors. */
    private List<ViewElementDescriptor> mROLayoutDescriptors;

    /** The list of all known View (not ViewLayout) descriptors. */
    private List<ViewElementDescriptor> mViewDescriptors = new ArrayList<ViewElementDescriptor>();

    /** Read-Only list of View Descriptors. */
    private List<ViewElementDescriptor> mROViewDescriptors;

    /** The descriptor matching android.view.View. */
    private ViewElementDescriptor mBaseViewDescriptor;

    /** Map from view full class name to view descriptor */
    private Map<String, ViewElementDescriptor> mFqcnToDescriptor =
        // As of 3.1 there are 58 items in this map
        new HashMap<String, ViewElementDescriptor>(80);

    /** Returns the document descriptor. Contains all layouts and views linked together. */
    public DocumentDescriptor getDescriptor() {
        return mRootDescriptor;
    }

    /** Returns the read-only list of all known ViewLayout descriptors. */
    public List<ViewElementDescriptor> getLayoutDescriptors() {
        return mROLayoutDescriptors;
    }

    /** Returns the read-only list of all known View (not ViewLayout) descriptors. */
    public List<ViewElementDescriptor> getViewDescriptors() {
        return mROViewDescriptors;
    }

    public ElementDescriptor[] getRootElementDescriptors() {
        return mRootDescriptor.getChildren();
    }

    /**
     * Returns the descriptor matching android.view.View, which is guaranteed
     * to be a {@link ViewElementDescriptor}.
     */
    public ViewElementDescriptor getBaseViewDescriptor() {
        if (mBaseViewDescriptor == null) {
            mBaseViewDescriptor = findDescriptorByClass(SdkConstants.CLASS_VIEW);
        }
        return mBaseViewDescriptor;
    }

    /**
     * Updates the document descriptor.
     * <p/>
     * It first computes the new children of the descriptor and then update them
     * all at once.
     * <p/>
     *  TODO: differentiate groups from views in the tree UI? => rely on icons
     * <p/>
     *
     * @param views The list of views in the framework.
     * @param layouts The list of layouts in the framework.
     * @param styleMap A map from style names to style information provided by the SDK
     * @param target The android target being initialized
     */
    public synchronized void updateDescriptors(ViewClassInfo[] views, ViewClassInfo[] layouts,
            Map<String, DeclareStyleableInfo> styleMap, IAndroidTarget target) {

        // This map links every ViewClassInfo to the ElementDescriptor we created.
        // It is filled by convertView() and used later to fix the super-class hierarchy.
        HashMap<ViewClassInfo, ViewElementDescriptor> infoDescMap =
            new HashMap<ViewClassInfo, ViewElementDescriptor>();

        ArrayList<ViewElementDescriptor> newViews = new ArrayList<ViewElementDescriptor>(40);
        if (views != null) {
            for (ViewClassInfo info : views) {
                ViewElementDescriptor desc = convertView(info, infoDescMap);
                newViews.add(desc);
                mFqcnToDescriptor.put(desc.getFullClassName(), desc);
            }
        }

        // Create <include> as a synthetic regular view.
        // Note: ViewStub is already described by attrs.xml
        insertInclude(newViews);

        List<ViewElementDescriptor> newLayouts = new ArrayList<ViewElementDescriptor>(30);
        if (layouts != null) {
            for (ViewClassInfo info : layouts) {
                ViewElementDescriptor desc = convertView(info, infoDescMap);
                newLayouts.add(desc);
                mFqcnToDescriptor.put(desc.getFullClassName(), desc);
            }
        }

        // Find View and inherit all its layout attributes
        AttributeDescriptor[] frameLayoutAttrs = findViewLayoutAttributes(
                SdkConstants.CLASS_FRAMELAYOUT);

        if (target.getVersion().getApiLevel() >= 4) {
            ViewElementDescriptor fragmentTag = createFragment(frameLayoutAttrs, styleMap);
            newViews.add(fragmentTag);
        }

        List<ElementDescriptor> newDescriptors = new ArrayList<ElementDescriptor>(80);
        newDescriptors.addAll(newLayouts);
        newDescriptors.addAll(newViews);

        // Link all layouts to everything else here.. recursively
        for (ViewElementDescriptor layoutDesc : newLayouts) {
            layoutDesc.setChildren(newDescriptors);
        }

        // The gesture overlay descriptor is really a layout but not included in the layouts list
        // so handle it specially
        ViewElementDescriptor gestureView = findDescriptorByClass(FQCN_GESTURE_OVERLAY_VIEW);
        if (gestureView != null) {
            gestureView.setChildren(newDescriptors);
            // Inherit layout attributes from FrameLayout
            gestureView.setLayoutAttributes(frameLayoutAttrs);
        }

        fixSuperClasses(infoDescMap);

        ViewElementDescriptor requestFocus = createRequestFocus();
        newViews.add(requestFocus);
        newDescriptors.add(requestFocus);

        // The <merge> tag can only be a root tag, so it is added at the end.
        // It gets everything else as children but it is not made a child itself.
        ViewElementDescriptor mergeTag = createMerge(frameLayoutAttrs);
        mergeTag.setChildren(newDescriptors);  // mergeTag makes a copy of the list
        newDescriptors.add(mergeTag);
        newLayouts.add(mergeTag);

        // Sort palette contents
        Collections.sort(newViews);
        Collections.sort(newLayouts);

        mViewDescriptors = newViews;
        mLayoutDescriptors  = newLayouts;
        mRootDescriptor.setChildren(newDescriptors);

        mBaseViewDescriptor = null;
        mROLayoutDescriptors = Collections.unmodifiableList(mLayoutDescriptors);
        mROViewDescriptors = Collections.unmodifiableList(mViewDescriptors);
    }

    /**
     * Creates an element descriptor from a given {@link ViewClassInfo}.
     *
     * @param info The {@link ViewClassInfo} to convert into a new {@link ViewElementDescriptor}.
     * @param infoDescMap This map links every ViewClassInfo to the ElementDescriptor it created.
     *                    It is filled by here and used later to fix the super-class hierarchy.
     */
    private ViewElementDescriptor convertView(
            ViewClassInfo info,
            HashMap<ViewClassInfo, ViewElementDescriptor> infoDescMap) {
        String xmlName = info.getShortClassName();
        String uiName = xmlName;
        String fqcn = info.getFullClassName();
        if (ViewElementDescriptor.viewNeedsPackage(fqcn)) {
            xmlName = fqcn;
        }
        String tooltip = info.getJavaDoc();

        // Average is around 90, max (in 3.2) is 145
        ArrayList<AttributeDescriptor> attributes = new ArrayList<AttributeDescriptor>(120);

        // All views and groups have an implicit "style" attribute which is a reference.
        AttributeInfo styleInfo = new AttributeInfo(
                "style",    //$NON-NLS-1$ xmlLocalName
                new Format[] { Format.REFERENCE });
        styleInfo.setJavaDoc("A reference to a custom style"); //tooltip
        DescriptorsUtils.appendAttribute(attributes,
                "style",    //$NON-NLS-1$
                null,       //nsUri
                styleInfo,
                false,      //required
                null);      // overrides
        styleInfo.setDefinedBy(SdkConstants.CLASS_VIEW);

        // Process all View attributes
        DescriptorsUtils.appendAttributes(attributes,
                null, // elementName
                SdkConstants.NS_RESOURCES,
                info.getAttributes(),
                null, // requiredAttributes
                null /* overrides */);

        List<String> attributeSources = new ArrayList<String>();
        if (info.getAttributes() != null && info.getAttributes().length > 0) {
            attributeSources.add(fqcn);
        }

        for (ViewClassInfo link = info.getSuperClass();
                link != null;
                link = link.getSuperClass()) {
            AttributeInfo[] attrList = link.getAttributes();
            if (attrList.length > 0) {
                attributeSources.add(link.getFullClassName());
                attributes.add(new SeparatorAttributeDescriptor(
                        String.format("Attributes from %1$s", link.getShortClassName())));
                DescriptorsUtils.appendAttributes(attributes,
                        null, // elementName
                        SdkConstants.NS_RESOURCES,
                        attrList,
                        null, // requiredAttributes
                        null /* overrides */);
            }
        }

        // Process all LayoutParams attributes
        ArrayList<AttributeDescriptor> layoutAttributes = new ArrayList<AttributeDescriptor>();
        LayoutParamsInfo layoutParams = info.getLayoutData();

        for(; layoutParams != null; layoutParams = layoutParams.getSuperClass()) {
            boolean needSeparator = true;
            for (AttributeInfo attrInfo : layoutParams.getAttributes()) {
                if (DescriptorsUtils.containsAttribute(layoutAttributes,
                        SdkConstants.NS_RESOURCES, attrInfo)) {
                    continue;
                }
                if (needSeparator) {
                    ViewClassInfo viewLayoutClass = layoutParams.getViewLayoutClass();
                    String title;
                    String shortClassName = viewLayoutClass.getShortClassName();
                    if (layoutParams.getShortClassName().equals(
                            SdkConstants.CLASS_NAME_LAYOUTPARAMS)) {
                        title = String.format("Layout Attributes from %1$s",
                                    shortClassName);
                    } else {
                        title = String.format("Layout Attributes from %1$s (%2$s)",
                                shortClassName,
                                layoutParams.getShortClassName());
                    }
                    layoutAttributes.add(new SeparatorAttributeDescriptor(title));
                    needSeparator = false;
                }
                DescriptorsUtils.appendAttribute(layoutAttributes,
                        null, // elementName
                        SdkConstants.NS_RESOURCES,
                        attrInfo,
                        false, // required
                        null /* overrides */);
            }
        }

        ViewElementDescriptor desc = new ViewElementDescriptor(
                xmlName,
                uiName,
                fqcn,
                tooltip,
                null, // sdk_url
                attributes.toArray(new AttributeDescriptor[attributes.size()]),
                layoutAttributes.toArray(new AttributeDescriptor[layoutAttributes.size()]),
                null, // children
                false /* mandatory */);
        desc.setAttributeSources(Collections.unmodifiableList(attributeSources));
        infoDescMap.put(info, desc);
        return desc;
    }

    /**
     * Creates a new <include> descriptor and adds it to the list of view descriptors.
     *
     * @param knownViews A list of view descriptors being populated. Also used to find the
     *   View descriptor and extract its layout attributes.
     */
    private void insertInclude(List<ViewElementDescriptor> knownViews) {
        String xmlName = VIEW_INCLUDE;

        // Create the include custom attributes
        ArrayList<AttributeDescriptor> attributes = new ArrayList<AttributeDescriptor>();

        // Note that the "layout" attribute does NOT have the Android namespace
        DescriptorsUtils.appendAttribute(attributes,
                null, //elementXmlName
                null, //nsUri
                new AttributeInfo(
                        ATTR_LAYOUT,
                        new Format[] { Format.REFERENCE } ),
                true,  //required
                null); //overrides

        DescriptorsUtils.appendAttribute(attributes,
                null, //elementXmlName
                SdkConstants.NS_RESOURCES, //nsUri
                new AttributeInfo(
                        "id",           //$NON-NLS-1$
                        new Format[] { Format.REFERENCE } ),
                true,  //required
                null); //overrides

        // Find View and inherit all its layout attributes
        AttributeDescriptor[] viewLayoutAttribs = findViewLayoutAttributes(
                SdkConstants.CLASS_VIEW);

        // Create the include descriptor
        ViewElementDescriptor desc = new ViewElementDescriptor(xmlName,
                xmlName, // ui_name
                VIEW_INCLUDE, // "class name"; the GLE only treats this as an element tag
                "Lets you statically include XML layouts inside other XML layouts.",  // tooltip
                null, // sdk_url
                attributes.toArray(new AttributeDescriptor[attributes.size()]),
                viewLayoutAttribs,  // layout attributes
                null, // children
                false /* mandatory */);

        knownViews.add(desc);
    }

    /**
     * Creates and returns a new {@code <merge>} descriptor.
     * @param viewLayoutAttribs The layout attributes to use for the new descriptor
     */
    private ViewElementDescriptor createMerge(AttributeDescriptor[] viewLayoutAttribs) {
        String xmlName = VIEW_MERGE;

        // Create the include descriptor
        ViewElementDescriptor desc = new ViewElementDescriptor(xmlName,
                xmlName, // ui_name
                VIEW_MERGE, // "class name"; the GLE only treats this as an element tag
                "A root tag useful for XML layouts inflated using a ViewStub.",  // tooltip
                null,  // sdk_url
                null,  // attributes
                viewLayoutAttribs,  // layout attributes
                null,  // children
                false  /* mandatory */);

        return desc;
    }

    /**
     * Creates and returns a new {@code <fragment>} descriptor.
     * @param viewLayoutAttribs The layout attributes to use for the new descriptor
     * @param styleMap The style map provided by the SDK
     */
    private ViewElementDescriptor createFragment(AttributeDescriptor[] viewLayoutAttribs,
            Map<String, DeclareStyleableInfo> styleMap) {
        String xmlName = VIEW_FRAGMENT;
        final ViewElementDescriptor descriptor;

        // First try to create the descriptor from metadata in attrs.xml:
        DeclareStyleableInfo style = styleMap.get("Fragment"); //$NON-NLS-1$
        String fragmentTooltip =
            "A Fragment is a piece of an application's user interface or behavior that "
            + "can be placed in an Activity";
        String sdkUrl = "http://developer.android.com/guide/topics/fundamentals/fragments.html";
        ClassAttributeDescriptor classAttribute = new ClassAttributeDescriptor(
                // Should accept both CLASS_V4_FRAGMENT and CLASS_FRAGMENT
                null /*superClassName*/,
                ATTR_CLASS, ATTR_CLASS, null /* namespace */,
                "Supply the name of the fragment class to instantiate",
                new AttributeInfo(ATTR_CLASS, new Format[] { Format.STRING}),
                true /*mandatory*/);

        if (style != null) {
            descriptor = new ViewElementDescriptor(
                    VIEW_FRAGMENT, VIEW_FRAGMENT, VIEW_FRAGMENT,
                    fragmentTooltip,  // tooltip
                    sdkUrl, //,
                    null /* attributes */,
                    viewLayoutAttribs, // layout attributes
                    null /*childrenElements*/,
                    false /*mandatory*/);
            ArrayList<AttributeDescriptor> descs = new ArrayList<AttributeDescriptor>();
            // The class attribute is not included in the attrs.xml
            descs.add(classAttribute);
            DescriptorsUtils.appendAttributes(descs,
                    null,   // elementName
                    SdkConstants.NS_RESOURCES,
                    style.getAttributes(),
                    null,   // requiredAttributes
                    null);  // overrides
            //descriptor.setTooltip(style.getJavaDoc());
            descriptor.setAttributes(descs.toArray(new AttributeDescriptor[descs.size()]));
        } else {
            // The above will only work on API 11 and up. However, fragments are *also* available
            // on older platforms, via the fragment support library, so add in a manual
            // entry if necessary.
            descriptor = new ViewElementDescriptor(xmlName,
                xmlName, // ui_name
                xmlName, // "class name"; the GLE only treats this as an element tag
                fragmentTooltip,
                sdkUrl,
                new AttributeDescriptor[] {
                    new ClassAttributeDescriptor(
                            null /*superClassName*/,
                            ATTR_NAME, ATTR_NAME, ANDROID_URI,
                            "Supply the name of the fragment class to instantiate",
                            new AttributeInfo(ATTR_NAME, new Format[] { Format.STRING}),
                            true /*mandatory*/),
                    classAttribute,
                    new ClassAttributeDescriptor(
                            null /*superClassName*/,
                            ATTR_TAG, ATTR_TAG, ANDROID_URI,
                            "Supply a tag for the top-level view containing a String",
                            new AttributeInfo(ATTR_TAG, new Format[] { Format.STRING}),
                            true /*mandatory*/),
                }, // attributes
                viewLayoutAttribs,  // layout attributes
                null,  // children
                false  /* mandatory */);
        }

        return descriptor;
    }

    /**
     * Creates and returns a new {@code <requestFocus>} descriptor.
     */
    private ViewElementDescriptor createRequestFocus() {
        String xmlName = REQUEST_FOCUS;

        // Create the include descriptor
        return new ViewElementDescriptor(
                xmlName,  // xml_name
                xmlName, // ui_name
                xmlName, // "class name"; the GLE only treats this as an element tag
                "Requests focus for the parent element or one of its descendants", // tooltip
                null,  // sdk_url
                null,  // attributes
                null,  // layout attributes
                null,  // children
                false  /* mandatory */);
    }

    /**
     * Finds the descriptor and retrieves all its layout attributes.
     */
    private AttributeDescriptor[] findViewLayoutAttributes(
            String viewFqcn) {
        ViewElementDescriptor viewDesc = findDescriptorByClass(viewFqcn);
        if (viewDesc != null) {
            return viewDesc.getLayoutAttributes();
        }

        return null;
    }

    /**
     * Set the super-class of each {@link ViewElementDescriptor} by using the super-class
     * information available in the {@link ViewClassInfo}.
     */
    private void fixSuperClasses(Map<ViewClassInfo, ViewElementDescriptor> infoDescMap) {

        for (Entry<ViewClassInfo, ViewElementDescriptor> entry : infoDescMap.entrySet()) {
            ViewClassInfo info = entry.getKey();
            ViewElementDescriptor desc = entry.getValue();

            ViewClassInfo sup = info.getSuperClass();
            if (sup != null) {
                ViewElementDescriptor supDesc = infoDescMap.get(sup);
                while (supDesc == null && sup != null) {
                    // We don't have a descriptor for the super-class. That means the class is
                    // probably abstract, so we just need to walk up the super-class chain till
                    // we find one we have. All views derive from android.view.View so we should
                    // surely find that eventually.
                    sup = sup.getSuperClass();
                    if (sup != null) {
                        supDesc = infoDescMap.get(sup);
                    }
                }
                if (supDesc != null) {
                    desc.setSuperClass(supDesc);
                }
            }
        }
    }

    /**
     * Returns the {@link ViewElementDescriptor} with the given fully qualified class
     * name, or null if not found. This is a quick map lookup.
     *
     * @param fqcn the fully qualified class name
     * @return the corresponding {@link ViewElementDescriptor} or null
     */
    public ViewElementDescriptor findDescriptorByClass(String fqcn) {
        return mFqcnToDescriptor.get(fqcn);
    }

    /**
     * Returns the {@link ViewElementDescriptor} with the given XML tag name,
     * which usually does not include the package (depending on the
     * value of {@link ViewElementDescriptor#viewNeedsPackage(String)}).
     *
     * @param tag the XML tag name
     * @return the corresponding {@link ViewElementDescriptor} or null
     */
    public ViewElementDescriptor findDescriptorByTag(String tag) {
        // TODO: Consider whether we need to add a direct map lookup for this as well.
        // Currently not done since this is not frequently needed (only needed for
        // exploded rendering which was already performing list iteration.)
        for (ViewElementDescriptor descriptor : mLayoutDescriptors) {
            if (tag.equals(descriptor.getXmlLocalName())) {
                return descriptor;
            }
        }

        return null;
    }

    /**
     * Returns a collection of all the view class names, including layouts
     *
     * @return a collection of all the view class names, never null
     */
    public Collection<String> getAllViewClassNames() {
        return mFqcnToDescriptor.keySet();
    }
}
