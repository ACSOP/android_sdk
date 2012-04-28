/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.preferences;


import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.formatting.XmlFormatStyle;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;
import com.android.sdkstats.DdmsPreferenceStore;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.PropertyChangeEvent;

import java.io.File;

public final class AdtPrefs extends AbstractPreferenceInitializer {
    public final static String PREFS_SDK_DIR = AdtPlugin.PLUGIN_ID + ".sdk"; //$NON-NLS-1$

    public final static String PREFS_BUILD_RES_AUTO_REFRESH = AdtPlugin.PLUGIN_ID + ".resAutoRefresh"; //$NON-NLS-1$

    public final static String PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR = AdtPlugin.PLUGIN_ID + ".forceErrorNativeLibInJar"; //$NON-NLS-1$

    public final static String PREFS_BUILD_SKIP_POST_COMPILE_ON_FILE_SAVE = AdtPlugin.PLUGIN_ID + ".skipPostCompileOnFileSave"; //$NON-NLS-1$

    public final static String PREFS_BUILD_VERBOSITY = AdtPlugin.PLUGIN_ID + ".buildVerbosity"; //$NON-NLS-1$

    public final static String PREFS_DEFAULT_DEBUG_KEYSTORE = AdtPlugin.PLUGIN_ID + ".defaultDebugKeyStore"; //$NON-NLS-1$

    public final static String PREFS_CUSTOM_DEBUG_KEYSTORE = AdtPlugin.PLUGIN_ID + ".customDebugKeyStore"; //$NON-NLS-1$

    public final static String PREFS_HOME_PACKAGE = AdtPlugin.PLUGIN_ID + ".homePackage"; //$NON-NLS-1$

    public final static String PREFS_EMU_OPTIONS = AdtPlugin.PLUGIN_ID + ".emuOptions"; //$NON-NLS-1$

    public final static String PREFS_MONITOR_DENSITY = AdtPlugin.PLUGIN_ID + ".monitorDensity"; //$NON-NLS-1$

    public final static String PREFS_FORMAT_GUI_XML = AdtPlugin.PLUGIN_ID + ".formatXml"; //$NON-NLS-1$
    public final static String PREFS_USE_CUSTOM_XML_FORMATTER = AdtPlugin.PLUGIN_ID + ".androidForm"; //$NON-NLS-1$

    public final static String PREFS_PALETTE_MODE = AdtPlugin.PLUGIN_ID + ".palette"; //$NON-NLS-1$

    public final static String PREFS_USE_ECLIPSE_INDENT = AdtPlugin.PLUGIN_ID + ".eclipseIndent"; //$NON-NLS-1$
    public final static String PREVS_REMOVE_EMPTY_LINES = AdtPlugin.PLUGIN_ID + ".removeEmpty"; //$NON-NLS-1$
    public final static String PREFS_ONE_ATTR_PER_LINE = AdtPlugin.PLUGIN_ID + ".oneAttrPerLine"; //$NON-NLS-1$
    public final static String PREFS_SPACE_BEFORE_CLOSE = AdtPlugin.PLUGIN_ID + ".spaceBeforeClose"; //$NON-NLS-1$
    public final static String PREFS_FORMAT_ON_SAVE = AdtPlugin.PLUGIN_ID + ".formatOnSave"; //$NON-NLS-1$
    public final static String PREFS_LINT_ON_SAVE = AdtPlugin.PLUGIN_ID + ".lintOnSave"; //$NON-NLS-1$
    public final static String PREFS_LINT_ON_EXPORT = AdtPlugin.PLUGIN_ID + ".lintOnExport"; //$NON-NLS-1$
    public final static String PREFS_ATTRIBUTE_SORT = AdtPlugin.PLUGIN_ID + ".attrSort"; //$NON-NLS-1$
    public final static String PREFS_DISABLED_ISSUES = AdtPlugin.PLUGIN_ID + ".disabedIssues"; //$NON-NLS-1$
    public final static String PREFS_LINT_SEVERITIES = AdtPlugin.PLUGIN_ID + ".lintSeverities"; //$NON-NLS-1$

    /** singleton instance */
    private final static AdtPrefs sThis = new AdtPrefs();

    /** default store, provided by eclipse */
    private IPreferenceStore mStore;

    /** cached location for the sdk folder */
    private String mOsSdkLocation;

    /** Verbosity of the build */
    private BuildVerbosity mBuildVerbosity = BuildVerbosity.NORMAL;

    private boolean mBuildForceResResfresh = false;
    private boolean mBuildForceErrorOnNativeLibInJar = true;
    private boolean mBuildSkipPostCompileOnFileSave = true;
    private float mMonitorDensity = 0.f;
    private String mPalette;

    private boolean mFormatGuiXml;
    private boolean mCustomXmlFormatter;
    private boolean mUseEclipseIndent;
    private boolean mRemoveEmptyLines;
    private boolean mOneAttributeOnFirstLine;
    private boolean mSpaceBeforeClose;
    private boolean mFormatOnSave;
    private boolean mLintOnSave;
    private boolean mLintOnExport;
    private AttributeSortOrder mAttributeSort;

    public static enum BuildVerbosity {
        /** Build verbosity "Always". Those messages are always displayed, even in silent mode */
        ALWAYS(0),
        /** Build verbosity level "Normal" */
        NORMAL(1),
        /** Build verbosity level "Verbose". Those messages are only displayed in verbose mode */
        VERBOSE(2);

        private int mLevel;

        BuildVerbosity(int level) {
            mLevel = level;
        }

        public int getLevel() {
            return mLevel;
        }

        /**
         * Finds and returns a {@link BuildVerbosity} whose {@link #name()} matches a given name.
         * <p/>This is different from {@link Enum#valueOf(Class, String)} in that it returns null
         * if no matches are found.
         *
         * @param name the name to look up.
         * @return returns the matching enum or null of no match where found.
         */
        public static BuildVerbosity find(String name) {
            for (BuildVerbosity v : values()) {
                if (v.name().equals(name)) {
                    return v;
                }
            }

            return null;
        }
    }

    public static void init(IPreferenceStore preferenceStore) {
        sThis.mStore = preferenceStore;
    }

    public static AdtPrefs getPrefs() {
        return sThis;
    }

    public synchronized void loadValues(PropertyChangeEvent event) {
        // get the name of the property that changed, if any
        String property = event != null ? event.getProperty() : null;

        if (property == null || PREFS_SDK_DIR.equals(property)) {
            mOsSdkLocation = mStore.getString(PREFS_SDK_DIR);

            // Make it possible to override the SDK path using an environment variable.
            // The value will only be used if it matches an existing directory.
            // Useful for testing from Eclipse.
            // Note: this is a hack that does not change the preferences, so if the user
            // looks at Window > Preferences > Android, the path will be the preferences
            // one and not the overridden one.
            String override = System.getenv("ADT_TEST_SDK_PATH");   //$NON-NLS-1$
            if (override != null && override.length() > 0 && new File(override).isDirectory()) {
                mOsSdkLocation = override;
            }

            // make sure it ends with a separator. Normally this is done when the preference
            // is set. But to make sure older version still work, we fix it here as well.
            if (mOsSdkLocation.length() > 0 && mOsSdkLocation.endsWith(File.separator) == false) {
                mOsSdkLocation = mOsSdkLocation + File.separator;
            }
        }

        if (property == null || PREFS_BUILD_VERBOSITY.equals(property)) {
            mBuildVerbosity = BuildVerbosity.find(mStore.getString(PREFS_BUILD_VERBOSITY));
            if (mBuildVerbosity == null) {
                mBuildVerbosity = BuildVerbosity.NORMAL;
            }
        }

        if (property == null || PREFS_BUILD_RES_AUTO_REFRESH.equals(property)) {
            mBuildForceResResfresh = mStore.getBoolean(PREFS_BUILD_RES_AUTO_REFRESH);
        }

        if (property == null || PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR.equals(property)) {
            mBuildForceErrorOnNativeLibInJar = mStore.getBoolean(PREFS_BUILD_RES_AUTO_REFRESH);
        }

        if (property == null || PREFS_BUILD_SKIP_POST_COMPILE_ON_FILE_SAVE.equals(property)) {
            mBuildSkipPostCompileOnFileSave =
                mStore.getBoolean(PREFS_BUILD_SKIP_POST_COMPILE_ON_FILE_SAVE);
        }

        if (property == null || PREFS_MONITOR_DENSITY.equals(property)) {
            mMonitorDensity = mStore.getFloat(PREFS_MONITOR_DENSITY);
        }

        if (property == null || PREFS_FORMAT_GUI_XML.equals(property)) {
            mFormatGuiXml = mStore.getBoolean(PREFS_FORMAT_GUI_XML);
        }

        if (property == null || PREFS_USE_CUSTOM_XML_FORMATTER.equals(property)) {
            mCustomXmlFormatter = mStore.getBoolean(PREFS_USE_CUSTOM_XML_FORMATTER);
        }

        if (property == null || PREFS_PALETTE_MODE.equals(property)) {
            mPalette = mStore.getString(PREFS_PALETTE_MODE);
        }

        if (property == null || PREFS_USE_ECLIPSE_INDENT.equals(property)) {
            mUseEclipseIndent = mStore.getBoolean(PREFS_USE_ECLIPSE_INDENT);
        }

        if (property == null || PREVS_REMOVE_EMPTY_LINES.equals(property)) {
            mRemoveEmptyLines = mStore.getBoolean(PREVS_REMOVE_EMPTY_LINES);
        }

        if (property == null || PREFS_ONE_ATTR_PER_LINE.equals(property)) {
            mOneAttributeOnFirstLine = mStore.getBoolean(PREFS_ONE_ATTR_PER_LINE);
        }

        if (property == null || PREFS_ATTRIBUTE_SORT.equals(property)) {
            String order = mStore.getString(PREFS_ATTRIBUTE_SORT);
            mAttributeSort = AttributeSortOrder.LOGICAL;
            if (AttributeSortOrder.ALPHABETICAL.key.equals(order)) {
                mAttributeSort = AttributeSortOrder.ALPHABETICAL;
            } else if (AttributeSortOrder.NO_SORTING.key.equals(order)) {
                mAttributeSort = AttributeSortOrder.NO_SORTING;
            }
        }

        if (property == null || PREFS_SPACE_BEFORE_CLOSE.equals(property)) {
            mSpaceBeforeClose = mStore.getBoolean(PREFS_SPACE_BEFORE_CLOSE);
        }

        if (property == null || PREFS_FORMAT_ON_SAVE.equals(property)) {
            mFormatOnSave = mStore.getBoolean(PREFS_FORMAT_ON_SAVE);
        }

        if (property == null || PREFS_LINT_ON_SAVE.equals(property)) {
            mLintOnSave = mStore.getBoolean(PREFS_LINT_ON_SAVE);
        }

        if (property == null || PREFS_LINT_ON_EXPORT.equals(property)) {
            mLintOnExport = mStore.getBoolean(PREFS_LINT_ON_EXPORT);
        }
    }

    /**
     * Returns the SDK folder.
     * Guaranteed to be terminated by a platform-specific path separator.
     */
    public synchronized String getOsSdkFolder() {
        return mOsSdkLocation;
    }

    public synchronized BuildVerbosity getBuildVerbosity() {
        return mBuildVerbosity;
    }

    public boolean getBuildForceResResfresh() {
        return mBuildForceResResfresh;
    }

    /**
     * Should changes made by GUI editors automatically format the corresponding XML nodes
     * affected by the edit?
     *
     * @return true if the GUI editors should format affected XML regions
     */
    public boolean getFormatGuiXml() {
        // The format-GUI-editors flag only applies when the custom formatter is used,
        // since the built-in formatter has problems editing partial documents
        return mFormatGuiXml && mCustomXmlFormatter;
    }

    /**
     * Should the XML formatter use a custom Android XML formatter (following
     * Android code style) or use the builtin Eclipse XML formatter?
     *
     * @return true if the Android formatter should be used instead of the
     *         default Eclipse one
     */
    public boolean getUseCustomXmlFormatter() {
        return mCustomXmlFormatter;
    }

    /**
     * Should the Android XML formatter use the Eclipse XML indentation settings
     * (usually one tab character) instead of the default 4 space character
     * indent?
     *
     * @return true if the Eclipse XML indentation settings should be use
     */
    public boolean isUseEclipseIndent() {
        return mUseEclipseIndent;
    }

    /**
     * Should the Android XML formatter try to avoid inserting blank lines to
     * make the format as compact as possible (no blank lines between elements,
     * no blank lines surrounding comments, etc).
     *
     * @return true to remove blank lines
     */
    public boolean isRemoveEmptyLines() {
        return mRemoveEmptyLines;
    }

    /**
     * Should the Android XML formatter attempt to place a single attribute on
     * the same line as the element open tag?
     *
     * @return true if single-attribute elements should place the attribute on
     *         the same line as the element open tag
     */
    public boolean isOneAttributeOnFirstLine() {
        return mOneAttributeOnFirstLine;
    }

    /**
     * Returns the sort order to be applied to the attributes (one of which can
     * be {@link AttributeSortOrder#NO_SORTING}).
     *
     * @return the sort order to apply to the attributes
     */
    public AttributeSortOrder getAttributeSort() {
        if (mAttributeSort == null) {
            return AttributeSortOrder.LOGICAL;
        }
        return mAttributeSort;
    }

    /**
     * Returns whether a space should be inserted before the closing {@code >}
     * character in open tags and before the closing {@code />} characters in
     * empty tag. Note that the {@link XmlFormatStyle#RESOURCE} style overrides
     * this setting to make it more compact for the {@code <item>} elements.
     *
     * @return true if an empty space should be inserted before {@code >} or
     *         {@code />}.
     */
    public boolean isSpaceBeforeClose() {
        return mSpaceBeforeClose;
    }

    /**
     * Returns whether the file should be automatically formatted on save.
     *
     * @return true if the XML files should be formatted on save.
     */
    public boolean isFormatOnSave() {
        return mFormatOnSave;
    }

    public boolean isLintOnSave() {
        return mLintOnSave;
    }

    public void setLintOnSave(boolean on) {
        mLintOnSave = on;
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        store.setValue(PREFS_LINT_ON_SAVE, on);
    }

    public boolean isLintOnExport() {
        return mLintOnExport;
    }

    public void setLintOnExport(boolean on) {
        mLintOnExport = on;
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        store.setValue(PREFS_LINT_ON_EXPORT, on);
    }

    public boolean getBuildForceErrorOnNativeLibInJar() {
        return mBuildForceErrorOnNativeLibInJar;
    }

    public boolean getBuildSkipPostCompileOnFileSave() {
        return mBuildSkipPostCompileOnFileSave;
    }

    public String getPaletteModes() {
        return mPalette;
    }

    public void setPaletteModes(String palette) {
        mPalette = palette;

        // need to save this new value to the store
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        store.setValue(PREFS_PALETTE_MODE, palette);
    }

    public float getMonitorDensity() {
        return mMonitorDensity;
    }

    public void setMonitorDensity(float density) {
        mMonitorDensity = density;

        // need to save this new value to the store
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        store.setValue(PREFS_MONITOR_DENSITY, density);
    }

    /**
     * Sets the new location of the SDK
     *
     * @param location the location of the SDK
     */
    public void setSdkLocation(File location) {
        mOsSdkLocation = location != null ? location.getPath() : null;

        // TODO: Also store this location in the .android settings directory
        // such that we can support using multiple workspaces without asking
        // over and over.
        if (mOsSdkLocation != null && mOsSdkLocation.length() > 0) {
            DdmsPreferenceStore ddmsStore = new DdmsPreferenceStore();
            ddmsStore.setLastSdkPath(mOsSdkLocation);
        }

        // need to save this new value to the store
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        store.setValue(PREFS_SDK_DIR, mOsSdkLocation);
    }

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
        initializeStoreWithDefaults(store);
    }

    public void initializeStoreWithDefaults(IPreferenceStore store) {
        store.setDefault(PREFS_BUILD_RES_AUTO_REFRESH, true);
        store.setDefault(PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR, true);
        store.setDefault(PREFS_BUILD_SKIP_POST_COMPILE_ON_FILE_SAVE, true);

        store.setDefault(PREFS_BUILD_VERBOSITY, BuildVerbosity.ALWAYS.name());

        store.setDefault(PREFS_HOME_PACKAGE, "android.process.acore"); //$NON-NLS-1$

        store.setDefault(PREFS_MONITOR_DENSITY, 0.f);
        store.setDefault(PREFS_FORMAT_GUI_XML, true);
        store.setDefault(PREFS_USE_CUSTOM_XML_FORMATTER, true);
        store.setDefault(PREFS_ONE_ATTR_PER_LINE, true);
        store.setDefault(PREFS_SPACE_BEFORE_CLOSE, true);
        store.setDefault(PREFS_LINT_ON_SAVE, true);
        store.setDefault(PREFS_LINT_ON_EXPORT, true);

        // Defaults already handled; no need to write into map:
        //store.setDefault(PREFS_ATTRIBUTE_SORT, AttributeSortOrder.LOGICAL.key);
        //store.setDefault(PREFS_USE_ECLIPSE_INDENT, false);
        //store.setDefault(PREVS_REMOVE_EMPTY_LINES, false);
        //store.setDefault(PREFS_FORMAT_ON_SAVE, false);

        try {
            store.setDefault(PREFS_DEFAULT_DEBUG_KEYSTORE,
                    DebugKeyProvider.getDefaultKeyStoreOsPath());
        } catch (KeytoolException e) {
            AdtPlugin.log(e, "Get default debug keystore path failed"); //$NON-NLS-1$
        } catch (AndroidLocationException e) {
            AdtPlugin.log(e, "Get default debug keystore path failed"); //$NON-NLS-1$
        }
    }
}
