/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.tools.lint.detector.api;

/** Enum which describes the different computation speeds of various detectors */
public enum Speed {
    /** The detector can run very quickly */
    FAST("Fast"),

    /** The detector runs reasonably fast */
    NORMAL("Normal"),

    /** The detector might take a long time to run */
    SLOW("Slow");

    private String mDisplayName;

    Speed(String displayName) {
        mDisplayName = displayName;
    }

    /**
     * Returns the user-visible description of the speed of the given
     * detector
     *
     * @return the description of the speed to display to the user
     */
    public String getDisplayName() {
        return mDisplayName;
    }
}