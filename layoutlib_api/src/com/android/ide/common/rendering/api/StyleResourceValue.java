/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.common.rendering.api;

import com.android.layoutlib.api.IResourceValue;
import com.android.layoutlib.api.IStyleResourceValue;
import com.android.resources.ResourceType;

import java.util.HashMap;

/**
 * Represents an android style resources with a name and a list of children {@link ResourceValue}.
 */
@SuppressWarnings("deprecation")
public final class StyleResourceValue extends ResourceValue implements IStyleResourceValue {

    private String mParentStyle = null;
    private HashMap<String, ResourceValue> mItems = new HashMap<String, ResourceValue>();

    public StyleResourceValue(ResourceType type, String name, boolean isFramework) {
        super(type, name, isFramework);
    }

    public StyleResourceValue(ResourceType type, String name, String parentStyle,
            boolean isFramework) {
        super(type, name, isFramework);
        mParentStyle = parentStyle;
    }

    /**
     * Returns the parent style name or <code>null</code> if unknown.
     */
    public String getParentStyle() {
        return mParentStyle;
    }

    /**
     * Finds a value in the list by name
     * @param name the name of the resource
     */
    public ResourceValue findValue(String name) {
        return mItems.get(name);
    }

    public void addValue(ResourceValue value) {
        mItems.put(value.getName(), value);
    }

    @Override
    public void replaceWith(ResourceValue value) {
        assert value instanceof StyleResourceValue;
        super.replaceWith(value);

        if (value instanceof StyleResourceValue) {
            mItems.clear();
            mItems.putAll(((StyleResourceValue)value).mItems);
        }
    }

    /**
     * Legacy method.
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    public IResourceValue findItem(String name) {
        return mItems.get(name);
    }
}
