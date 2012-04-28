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

package com.android.ide.eclipse.adt.internal.editors.resources.descriptors;

import com.android.ide.common.api.IAttributeInfo.Format;
import com.android.ide.common.resources.platform.AttributeInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.FlagAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.IDescriptorProvider;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ListAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.TextAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.TextValueDescriptor;
import com.android.resources.ResourceType;


/**
 * Complete description of the structure for resources XML files (under res/values/)
 */
public final class ResourcesDescriptors implements IDescriptorProvider {

    // Public attributes names, attributes descriptors and elements descriptors

    public static final String ROOT_ELEMENT = "resources"; //$NON-NLS-1$
    public static final String STRING_ELEMENT = "string";  //$NON-NLS-1$
    public static final String STYLE_ELEMENT = "style";    //$NON-NLS-1$
    public static final String COLOR_ELEMENT = "color";    //$NON-NLS-1$
    public static final String DIMEN_ELEMENT = "dimen";    //$NON-NLS-1$
    public static final String DRAWABLE_ELEMENT = "drawable"; //$NON-NLS-1$
    public static final String INTEGER_ARRAY_ELEMENT = "integer-array"; //$NON-NLS-1$
    public static final String STRING_ARRAY_ELEMENT = "string-array";   //$NON-NLS-1$

    public static final String ITEM_TAG = "item";  //$NON-NLS-1$
    public static final String NAME_ATTR = "name"; //$NON-NLS-1$
    public static final String TYPE_ATTR = "type"; //$NON-NLS-1$
    public static final String PARENT_ATTR = "parent"; //$NON-NLS-1$

    private static final ResourcesDescriptors sThis = new ResourcesDescriptors();

    /** The {@link ElementDescriptor} for the root Resources element. */
    public final ElementDescriptor mResourcesElement;

    public static ResourcesDescriptors getInstance() {
        return sThis;
    }

    /*
     * @see com.android.ide.eclipse.editors.descriptors.IDescriptorProvider#getRootElementDescriptors()
     */
    public ElementDescriptor[] getRootElementDescriptors() {
        return new ElementDescriptor[] { mResourcesElement };
    }

    public ElementDescriptor getDescriptor() {
        return mResourcesElement;
    }

    public ElementDescriptor getElementDescriptor() {
        return mResourcesElement;
    }

    private ResourcesDescriptors() {

        // Common attributes used in many placed

        // Elements

        AttributeInfo nameAttrInfo = new AttributeInfo(NAME_ATTR, new Format[] { Format.STRING } );

        ElementDescriptor color_element = new ElementDescriptor(
                COLOR_ELEMENT,
                "Color",
                "A @color@ value specifies an RGB value with an alpha channel, which can be used in various places such as specifying a solid color for a Drawable or the color to use for text.  It always begins with a # character and then is followed by the alpha-red-green-blue information in one of the following formats: #RGB, #ARGB, #RRGGBB or #AARRGGBB.",
                "http://code.google.com/android/reference/available-resources.html#colorvals",  //$NON-NLS-1$
                new AttributeDescriptor[] {
                        new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this color.",
                                nameAttrInfo),
                        new ColorValueDescriptor(
                                "Value*",
                                "A mandatory color value.")
                },
                null,  // no child nodes
                false /* not mandatory */);

        ElementDescriptor string_element = new ElementDescriptor(
                STRING_ELEMENT,
                "String",
                "@Strings@, with optional simple formatting, can be stored and retrieved as resources. You can add formatting to your string by using three standard HTML tags: b, i, and u. If you use an apostrophe or a quote in your string, you must either escape it or enclose the whole string in the other kind of enclosing quotes.",
                "http://code.google.com/android/reference/available-resources.html#stringresources",  //$NON-NLS-1$
                new AttributeDescriptor[] {
                        new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this string.",
                                nameAttrInfo),
                        new TextValueDescriptor(
                                "Value*",
                                "A mandatory string value.")
                },
                null,  // no child nodes
                false /* not mandatory */);

        ElementDescriptor item_element = new ItemElementDescriptor(
                 ITEM_TAG,
                 "Item",
                 null,  // TODO find javadoc
                 null,  // TODO find link to javadoc
                 new AttributeDescriptor[] {
                         new TextAttributeDescriptor(NAME_ATTR,
                                 "Name*",
                                 null /* nsUri */,
                                 "The mandatory name used in referring to this resource.",
                                 nameAttrInfo),
                         new ListAttributeDescriptor(TYPE_ATTR,
                                 "Type*",
                                 null /* nsUri */,
                                 "The mandatory type of this resource.",
                                 new AttributeInfo(TYPE_ATTR,
                                         new Format[] { Format.STRING, Format.ENUM }
                                 ).setEnumValues(ResourceType.getNames())
                         ),
                         new FlagAttributeDescriptor("format",      //$NON-NLS-1$
                                 "Format",
                                 null /* nsUri */,
                                 "The optional format of this resource.",
                                 new AttributeInfo("format",
                                         new Format[] { Format.STRING, Format.FLAG }
                                 ).setFlagValues(
                                     new String[] {
                                         "boolean",     //$NON-NLS-1$
                                         COLOR_ELEMENT,
                                         "dimension",   //$NON-NLS-1$
                                         "float",       //$NON-NLS-1$
                                         "fraction",    //$NON-NLS-1$
                                         "integer",     //$NON-NLS-1$
                                         "reference",   //$NON-NLS-1$
                                         "string"       //$NON-NLS-1$
                                     } )
                         ),
                         new TextValueDescriptor(
                                 "Value",
                                 "A standard string, hex color value, or reference to any other resource type.")
                 },
                 null,  // no child nodes
                 false /* not mandatory */);

        ElementDescriptor drawable_element = new ElementDescriptor(
                DRAWABLE_ELEMENT,
                "Drawable",
                "A @drawable@ defines a rectangle of color. Android accepts color values written in various web-style formats -- a hexadecimal constant in any of the following forms: #RGB, #ARGB, #RRGGBB, #AARRGGBB. Zero in the alpha channel means transparent. The default value is opaque.",
                "http://code.google.com/android/reference/available-resources.html#colordrawableresources",  //$NON-NLS-1$
                new AttributeDescriptor[] {
                        new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this drawable.",
                                nameAttrInfo),
                        new TextValueDescriptor(
                                "Value*",
                                "A mandatory color value in the form #RGB, #ARGB, #RRGGBB or #AARRGGBB.")
                },
                null,  // no child nodes
                false /* not mandatory */);

        ElementDescriptor dimen_element = new ElementDescriptor(
                DIMEN_ELEMENT,
                "Dimension",
                "You can create common dimensions to use for various screen elements by defining @dimension@ values in XML. A dimension resource is a number followed by a unit of measurement. Supported units are px (pixels), in (inches), mm (millimeters), pt (points at 72 DPI), dp (density-independent pixels) and sp (scale-independent pixels)",
                "http://code.google.com/android/reference/available-resources.html#dimension",  //$NON-NLS-1$
                new AttributeDescriptor[] {
                        new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this dimension.",
                                nameAttrInfo),
                        new TextValueDescriptor(
                                "Value*",
                                "A mandatory dimension value is a number followed by a unit of measurement. For example: 10px, 2in, 5sp.")
                },
                null,  // no child nodes
                false /* not mandatory */);

         ElementDescriptor style_element = new ElementDescriptor(
                STYLE_ELEMENT,
                "Style/Theme",
                "Both @styles and themes@ are defined in a style block containing one or more string or numerical values (typically color values), or references to other resources (drawables and so on).",
                "http://code.google.com/android/reference/available-resources.html#stylesandthemes",  //$NON-NLS-1$
                new AttributeDescriptor[] {
                        new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this theme.",
                                nameAttrInfo),
                        new TextAttributeDescriptor("parent", //$NON-NLS-1$
                                "Parent",
                                null /* nsUri */,
                                "An optional parent theme. All values from the specified theme will be inherited into this theme. Any values with identical names that you specify will override inherited values.",
                                new AttributeInfo("parent",  //$NON-NLS-1$
                                        new Format[] { Format.STRING })),
                },
                new ElementDescriptor[] {
                    new ElementDescriptor(
                        ITEM_TAG,
                        "Item",
                        "A value to use in this @theme@. It can be a standard string, a hex color value, or a reference to any other resource type.",
                        "http://code.google.com/android/reference/available-resources.html#stylesandthemes",  //$NON-NLS-1$
                        new AttributeDescriptor[] {
                            new TextAttributeDescriptor(NAME_ATTR,
                                "Name*",
                                null /* nsUri */,
                                "The mandatory name used in referring to this item.",
                                nameAttrInfo),
                            new TextValueDescriptor(
                                "Value*",
                                "A mandatory standard string, hex color value, or reference to any other resource type.")
                        },
                        null,  // no child nodes
                        false /* not mandatory */)
                },
                false /* not mandatory */);

         ElementDescriptor string_array_element = new ElementDescriptor(
                 STRING_ARRAY_ELEMENT,
                 "String Array",
                 "An array of strings. Strings are added as underlying item elements to the array.",
                 null, // tooltips
                 new AttributeDescriptor[] {
                         new TextAttributeDescriptor(NAME_ATTR,
                                 "Name*",
                                 null /* nsUri */,
                                 "The mandatory name used in referring to this string array.",
                                 nameAttrInfo),
                 },
                 new ElementDescriptor[] {
                     new ElementDescriptor(
                         ITEM_TAG,
                         "Item",
                         "A string value to use in this string array.",
                         null, // tooltip
                         new AttributeDescriptor[] {
                             new TextValueDescriptor(
                                 "Value*",
                                 "A mandatory string.")
                         },
                         null,  // no child nodes
                         false /* not mandatory */)
                 },
                 false /* not mandatory */);

         ElementDescriptor integer_array_element = new ElementDescriptor(
                 INTEGER_ARRAY_ELEMENT,
                 "Integer Array",
                 "An array of integers. Integers are added as underlying item elements to the array.",
                 null, // tooltips
                 new AttributeDescriptor[] {
                         new TextAttributeDescriptor(NAME_ATTR,
                                 "Name*",
                                 null /* nsUri */,
                                 "The mandatory name used in referring to this integer array.",
                                 nameAttrInfo),
                 },
                 new ElementDescriptor[] {
                     new ElementDescriptor(
                         ITEM_TAG,
                         "Item",
                         "An integer value to use in this integer array.",
                         null, // tooltip
                         new AttributeDescriptor[] {
                             new TextValueDescriptor(
                                 "Value*",
                                 "A mandatory integer.")
                         },
                         null,  // no child nodes
                         false /* not mandatory */)
                 },
                 false /* not mandatory */);

         mResourcesElement = new ElementDescriptor(
                        ROOT_ELEMENT,
                        "Resources",
                        null,
                        "http://code.google.com/android/reference/available-resources.html",  //$NON-NLS-1$
                        null,  // no attributes
                        new ElementDescriptor[] {
                                string_element,
                                color_element,
                                dimen_element,
                                drawable_element,
                                style_element,
                                item_element,
                                string_array_element,
                                integer_array_element,
                        },
                        true /* mandatory */);
    }
}
