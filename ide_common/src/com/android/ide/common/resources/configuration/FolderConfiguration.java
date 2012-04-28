/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.ide.common.resources.configuration;

import com.android.AndroidConstants;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;

import java.util.ArrayList;
import java.util.List;


/**
 * Represents the configuration for Resource Folders. All the properties have a default
 * value which means that the property is not set.
 */
public final class FolderConfiguration implements Comparable<FolderConfiguration> {

    private final static ResourceQualifier[] DEFAULT_QUALIFIERS;

    static {
        // get the default qualifiers.
        FolderConfiguration defaultConfig = new FolderConfiguration();
        defaultConfig.createDefault();
        DEFAULT_QUALIFIERS = defaultConfig.getQualifiers();
    }


    private final ResourceQualifier[] mQualifiers = new ResourceQualifier[INDEX_COUNT];

    private final static int INDEX_COUNTRY_CODE          = 0;
    private final static int INDEX_NETWORK_CODE          = 1;
    private final static int INDEX_LANGUAGE              = 2;
    private final static int INDEX_REGION                = 3;
    private final static int INDEX_SMALLEST_SCREEN_WIDTH = 4;
    private final static int INDEX_SCREEN_WIDTH          = 5;
    private final static int INDEX_SCREEN_HEIGHT         = 6;
    private final static int INDEX_SCREEN_LAYOUT_SIZE    = 7;
    private final static int INDEX_SCREEN_RATIO          = 8;
    private final static int INDEX_SCREEN_ORIENTATION    = 9;
    private final static int INDEX_UI_MODE               = 10;
    private final static int INDEX_NIGHT_MODE            = 11;
    private final static int INDEX_PIXEL_DENSITY         = 12;
    private final static int INDEX_TOUCH_TYPE            = 13;
    private final static int INDEX_KEYBOARD_STATE        = 14;
    private final static int INDEX_TEXT_INPUT_METHOD     = 15;
    private final static int INDEX_NAVIGATION_STATE      = 16;
    private final static int INDEX_NAVIGATION_METHOD     = 17;
    private final static int INDEX_SCREEN_DIMENSION      = 18;
    private final static int INDEX_VERSION               = 19;
    private final static int INDEX_COUNT                 = 20;

    /**
     * Creates a {@link FolderConfiguration} matching the folder segments.
     * @param folderSegments The segments of the folder name. The first segments should contain
     * the name of the folder
     * @return a FolderConfiguration object, or null if the folder name isn't valid..
     */
    public static FolderConfiguration getConfig(String[] folderSegments) {
        FolderConfiguration config = new FolderConfiguration();

        // we are going to loop through the segments, and match them with the first
        // available qualifier. If the segment doesn't match we try with the next qualifier.
        // Because the order of the qualifier is fixed, we do not reset the first qualifier
        // after each successful segment.
        // If we run out of qualifier before processing all the segments, we fail.

        int qualifierIndex = 0;
        int qualifierCount = DEFAULT_QUALIFIERS.length;

        for (int i = 1 ; i < folderSegments.length; i++) {
            String seg = folderSegments[i];
            if (seg.length() > 0) {
                while (qualifierIndex < qualifierCount &&
                        DEFAULT_QUALIFIERS[qualifierIndex].checkAndSet(seg, config) == false) {
                    qualifierIndex++;
                }

                // if we reached the end of the qualifier we didn't find a matching qualifier.
                if (qualifierIndex == qualifierCount) {
                    return null;
                }

            } else {
                return null;
            }
        }

        return config;
    }

    /**
     * Returns the number of {@link ResourceQualifier} that make up a Folder configuration.
     */
    public static int getQualifierCount() {
        return INDEX_COUNT;
    }

    /**
     * Sets the config from the qualifiers of a given <var>config</var>.
     * <p/>This is equivalent to <code>set(config, false)</code>
     * @param config the configuration to set
     *
     * @see #set(FolderConfiguration, boolean)
     */
    public void set(FolderConfiguration config) {
        set(config, false /*nonFakeValuesOnly*/);
    }

    /**
     * Sets the config from the qualifiers of a given <var>config</var>.
     * @param config the configuration to set
     * @param nonFakeValuesOnly if set to true this ignore qualifiers for which the
     * current value is a fake value.
     *
     * @see ResourceQualifier#hasFakeValue()
     */
    public void set(FolderConfiguration config, boolean nonFakeValuesOnly) {
        if (config != null) {
            for (int i = 0 ; i < INDEX_COUNT ; i++) {
                ResourceQualifier q = config.mQualifiers[i];
                if (nonFakeValuesOnly == false || q == null || q.hasFakeValue() == false) {
                    mQualifiers[i] = q;
                }
            }
        }
    }

    /**
     * Reset the config.
     * <p/>This makes qualifiers at all indices <code>null</code>.
     */
    public void reset() {
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            mQualifiers[i] = null;
        }
    }

    /**
     * Removes the qualifiers from the receiver if they are present (and valid)
     * in the given configuration.
     */
    public void substract(FolderConfiguration config) {
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (config.mQualifiers[i] != null && config.mQualifiers[i].isValid()) {
                mQualifiers[i] = null;
            }
        }
    }

    /**
     * Adds the non-qualifiers from the given config.
     * Qualifiers that are null in the given config do not change in the receiver.
     */
    public void add(FolderConfiguration config) {
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (config.mQualifiers[i] != null) {
                mQualifiers[i] = config.mQualifiers[i];
            }
        }
    }

    /**
     * Returns the first invalid qualifier, or <code>null<code> if they are all valid (or if none
     * exists).
     */
    public ResourceQualifier getInvalidQualifier() {
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (mQualifiers[i] != null && mQualifiers[i].isValid() == false) {
                return mQualifiers[i];
            }
        }

        // all allocated qualifiers are valid, we return null.
        return null;
    }

    /**
     * Returns whether the Region qualifier is valid. Region qualifier can only be present if a
     * Language qualifier is present as well.
     * @return true if the Region qualifier is valid.
     */
    public boolean checkRegion() {
        if (mQualifiers[INDEX_LANGUAGE] == null && mQualifiers[INDEX_REGION] != null) {
            return false;
        }

        return true;
    }

    /**
     * Adds a qualifier to the {@link FolderConfiguration}
     * @param qualifier the {@link ResourceQualifier} to add.
     */
    public void addQualifier(ResourceQualifier qualifier) {
        if (qualifier instanceof CountryCodeQualifier) {
            mQualifiers[INDEX_COUNTRY_CODE] = qualifier;

        } else if (qualifier instanceof NetworkCodeQualifier) {
            mQualifiers[INDEX_NETWORK_CODE] = qualifier;

        } else if (qualifier instanceof LanguageQualifier) {
            mQualifiers[INDEX_LANGUAGE] = qualifier;

        } else if (qualifier instanceof RegionQualifier) {
            mQualifiers[INDEX_REGION] = qualifier;

        } else if (qualifier instanceof SmallestScreenWidthQualifier) {
            mQualifiers[INDEX_SMALLEST_SCREEN_WIDTH] = qualifier;

        } else if (qualifier instanceof ScreenWidthQualifier) {
            mQualifiers[INDEX_SCREEN_WIDTH] = qualifier;

        } else if (qualifier instanceof ScreenHeightQualifier) {
            mQualifiers[INDEX_SCREEN_HEIGHT] = qualifier;

        } else if (qualifier instanceof ScreenSizeQualifier) {
            mQualifiers[INDEX_SCREEN_LAYOUT_SIZE] = qualifier;

        } else if (qualifier instanceof ScreenRatioQualifier) {
            mQualifiers[INDEX_SCREEN_RATIO] = qualifier;

        } else if (qualifier instanceof ScreenOrientationQualifier) {
            mQualifiers[INDEX_SCREEN_ORIENTATION] = qualifier;

        } else if (qualifier instanceof UiModeQualifier) {
            mQualifiers[INDEX_UI_MODE] = qualifier;

        } else if (qualifier instanceof NightModeQualifier) {
            mQualifiers[INDEX_NIGHT_MODE] = qualifier;

        } else if (qualifier instanceof DensityQualifier) {
            mQualifiers[INDEX_PIXEL_DENSITY] = qualifier;

        } else if (qualifier instanceof TouchScreenQualifier) {
            mQualifiers[INDEX_TOUCH_TYPE] = qualifier;

        } else if (qualifier instanceof KeyboardStateQualifier) {
            mQualifiers[INDEX_KEYBOARD_STATE] = qualifier;

        } else if (qualifier instanceof TextInputMethodQualifier) {
            mQualifiers[INDEX_TEXT_INPUT_METHOD] = qualifier;

        } else if (qualifier instanceof NavigationStateQualifier) {
            mQualifiers[INDEX_NAVIGATION_STATE] = qualifier;

        } else if (qualifier instanceof NavigationMethodQualifier) {
            mQualifiers[INDEX_NAVIGATION_METHOD] = qualifier;

        } else if (qualifier instanceof ScreenDimensionQualifier) {
            mQualifiers[INDEX_SCREEN_DIMENSION] = qualifier;

        } else if (qualifier instanceof VersionQualifier) {
            mQualifiers[INDEX_VERSION] = qualifier;

        }
    }

    /**
     * Removes a given qualifier from the {@link FolderConfiguration}.
     * @param qualifier the {@link ResourceQualifier} to remove.
     */
    public void removeQualifier(ResourceQualifier qualifier) {
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (mQualifiers[i] == qualifier) {
                mQualifiers[i] = null;
                return;
            }
        }
    }

    /**
     * Returns a qualifier by its index. The total number of qualifiers can be accessed by
     * {@link #getQualifierCount()}.
     * @param index the index of the qualifier to return.
     * @return the qualifier or null if there are none at the index.
     */
    public ResourceQualifier getQualifier(int index) {
        return mQualifiers[index];
    }

    public void setCountryCodeQualifier(CountryCodeQualifier qualifier) {
        mQualifiers[INDEX_COUNTRY_CODE] = qualifier;
    }

    public CountryCodeQualifier getCountryCodeQualifier() {
        return (CountryCodeQualifier)mQualifiers[INDEX_COUNTRY_CODE];
    }

    public void setNetworkCodeQualifier(NetworkCodeQualifier qualifier) {
        mQualifiers[INDEX_NETWORK_CODE] = qualifier;
    }

    public NetworkCodeQualifier getNetworkCodeQualifier() {
        return (NetworkCodeQualifier)mQualifiers[INDEX_NETWORK_CODE];
    }

    public void setLanguageQualifier(LanguageQualifier qualifier) {
        mQualifiers[INDEX_LANGUAGE] = qualifier;
    }

    public LanguageQualifier getLanguageQualifier() {
        return (LanguageQualifier)mQualifiers[INDEX_LANGUAGE];
    }

    public void setRegionQualifier(RegionQualifier qualifier) {
        mQualifiers[INDEX_REGION] = qualifier;
    }

    public RegionQualifier getRegionQualifier() {
        return (RegionQualifier)mQualifiers[INDEX_REGION];
    }

    public void setSmallestScreenWidthQualifier(SmallestScreenWidthQualifier qualifier) {
        mQualifiers[INDEX_SMALLEST_SCREEN_WIDTH] = qualifier;
    }

    public SmallestScreenWidthQualifier getSmallestScreenWidthQualifier() {
        return (SmallestScreenWidthQualifier) mQualifiers[INDEX_SMALLEST_SCREEN_WIDTH];
    }

    public void setScreenWidthQualifier(ScreenWidthQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_WIDTH] = qualifier;
    }

    public ScreenWidthQualifier getScreenWidthQualifier() {
        return (ScreenWidthQualifier) mQualifiers[INDEX_SCREEN_WIDTH];
    }

    public void setScreenHeightQualifier(ScreenHeightQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_HEIGHT] = qualifier;
    }

    public ScreenHeightQualifier getScreenHeightQualifier() {
        return (ScreenHeightQualifier) mQualifiers[INDEX_SCREEN_HEIGHT];
    }

    public void setScreenSizeQualifier(ScreenSizeQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_LAYOUT_SIZE] = qualifier;
    }

    public ScreenSizeQualifier getScreenSizeQualifier() {
        return (ScreenSizeQualifier)mQualifiers[INDEX_SCREEN_LAYOUT_SIZE];
    }

    public void setScreenRatioQualifier(ScreenRatioQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_RATIO] = qualifier;
    }

    public ScreenRatioQualifier getScreenRatioQualifier() {
        return (ScreenRatioQualifier)mQualifiers[INDEX_SCREEN_RATIO];
    }

    public void setScreenOrientationQualifier(ScreenOrientationQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_ORIENTATION] = qualifier;
    }

    public ScreenOrientationQualifier getScreenOrientationQualifier() {
        return (ScreenOrientationQualifier)mQualifiers[INDEX_SCREEN_ORIENTATION];
    }

    public void setUiModeQualifier(UiModeQualifier qualifier) {
        mQualifiers[INDEX_UI_MODE] = qualifier;
    }

    public UiModeQualifier getUiModeQualifier() {
        return (UiModeQualifier)mQualifiers[INDEX_UI_MODE];
    }

    public void setNightModeQualifier(NightModeQualifier qualifier) {
        mQualifiers[INDEX_NIGHT_MODE] = qualifier;
    }

    public NightModeQualifier getNightModeQualifier() {
        return (NightModeQualifier)mQualifiers[INDEX_NIGHT_MODE];
    }

    public void setDensityQualifier(DensityQualifier qualifier) {
        mQualifiers[INDEX_PIXEL_DENSITY] = qualifier;
    }

    public DensityQualifier getDensityQualifier() {
        return (DensityQualifier)mQualifiers[INDEX_PIXEL_DENSITY];
    }

    public void setTouchTypeQualifier(TouchScreenQualifier qualifier) {
        mQualifiers[INDEX_TOUCH_TYPE] = qualifier;
    }

    public TouchScreenQualifier getTouchTypeQualifier() {
        return (TouchScreenQualifier)mQualifiers[INDEX_TOUCH_TYPE];
    }

    public void setKeyboardStateQualifier(KeyboardStateQualifier qualifier) {
        mQualifiers[INDEX_KEYBOARD_STATE] = qualifier;
    }

    public KeyboardStateQualifier getKeyboardStateQualifier() {
        return (KeyboardStateQualifier)mQualifiers[INDEX_KEYBOARD_STATE];
    }

    public void setTextInputMethodQualifier(TextInputMethodQualifier qualifier) {
        mQualifiers[INDEX_TEXT_INPUT_METHOD] = qualifier;
    }

    public TextInputMethodQualifier getTextInputMethodQualifier() {
        return (TextInputMethodQualifier)mQualifiers[INDEX_TEXT_INPUT_METHOD];
    }

    public void setNavigationStateQualifier(NavigationStateQualifier qualifier) {
        mQualifiers[INDEX_NAVIGATION_STATE] = qualifier;
    }

    public NavigationStateQualifier getNavigationStateQualifier() {
        return (NavigationStateQualifier)mQualifiers[INDEX_NAVIGATION_STATE];
    }

    public void setNavigationMethodQualifier(NavigationMethodQualifier qualifier) {
        mQualifiers[INDEX_NAVIGATION_METHOD] = qualifier;
    }

    public NavigationMethodQualifier getNavigationMethodQualifier() {
        return (NavigationMethodQualifier)mQualifiers[INDEX_NAVIGATION_METHOD];
    }

    public void setScreenDimensionQualifier(ScreenDimensionQualifier qualifier) {
        mQualifiers[INDEX_SCREEN_DIMENSION] = qualifier;
    }

    public ScreenDimensionQualifier getScreenDimensionQualifier() {
        return (ScreenDimensionQualifier)mQualifiers[INDEX_SCREEN_DIMENSION];
    }

    public void setVersionQualifier(VersionQualifier qualifier) {
        mQualifiers[INDEX_VERSION] = qualifier;
    }

    public VersionQualifier getVersionQualifier() {
        return (VersionQualifier)mQualifiers[INDEX_VERSION];
    }

    /**
     * Updates the {@link SmallestScreenWidthQualifier}, {@link ScreenWidthQualifier}, and
     * {@link ScreenHeightQualifier} based on the (required) values of
     * {@link ScreenDimensionQualifier} {@link DensityQualifier}, and
     * {@link ScreenOrientationQualifier}.
     *
     * Also the density cannot be {@link Density#NODPI} as it's not valid on a device.
     */
    public void updateScreenWidthAndHeight() {

        ResourceQualifier sizeQ = mQualifiers[INDEX_SCREEN_DIMENSION];
        ResourceQualifier densityQ = mQualifiers[INDEX_PIXEL_DENSITY];
        ResourceQualifier orientQ = mQualifiers[INDEX_SCREEN_ORIENTATION];

        if (sizeQ != null && densityQ != null && orientQ != null) {
            Density density = ((DensityQualifier) densityQ).getValue();
            if (density == Density.NODPI) {
                return;
            }

            ScreenOrientation orientation = ((ScreenOrientationQualifier) orientQ).getValue();

            int size1 = ((ScreenDimensionQualifier) sizeQ).getValue1();
            int size2 = ((ScreenDimensionQualifier) sizeQ).getValue2();

            // make sure size1 is the biggest (should be the case, but make sure)
            if (size1 < size2) {
                int a = size1;
                size1 = size2;
                size2 = a;
            }

            // compute the dp. round them up since we want -w480dp to match a 480.5dp screen
            int dp1 = (int) Math.ceil(size1 * Density.DEFAULT_DENSITY / density.getDpiValue());
            int dp2 = (int) Math.ceil(size2 * Density.DEFAULT_DENSITY / density.getDpiValue());

            setSmallestScreenWidthQualifier(new SmallestScreenWidthQualifier(dp2));

            switch (orientation) {
                case PORTRAIT:
                    setScreenWidthQualifier(new ScreenWidthQualifier(dp2));
                    setScreenHeightQualifier(new ScreenHeightQualifier(dp1));
                    break;
                case LANDSCAPE:
                    setScreenWidthQualifier(new ScreenWidthQualifier(dp1));
                    setScreenHeightQualifier(new ScreenHeightQualifier(dp2));
                    break;
                case SQUARE:
                    setScreenWidthQualifier(new ScreenWidthQualifier(dp2));
                    setScreenHeightQualifier(new ScreenHeightQualifier(dp2));
                    break;
            }
        }
    }

    /**
     * Returns whether an object is equals to the receiver.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof FolderConfiguration) {
            FolderConfiguration fc = (FolderConfiguration)obj;
            for (int i = 0 ; i < INDEX_COUNT ; i++) {
                ResourceQualifier qualifier = mQualifiers[i];
                ResourceQualifier fcQualifier = fc.mQualifiers[i];
                if (qualifier != null) {
                    if (qualifier.equals(fcQualifier) == false) {
                        return false;
                    }
                } else if (fcQualifier != null) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * Returns whether the Configuration has only default values.
     */
    public boolean isDefault() {
        for (ResourceQualifier irq : mQualifiers) {
            if (irq != null) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the name of a folder with the configuration.
     */
    public String getFolderName(ResourceFolderType folder) {
        StringBuilder result = new StringBuilder(folder.getName());

        for (ResourceQualifier qualifier : mQualifiers) {
            if (qualifier != null) {
                String segment = qualifier.getFolderSegment();
                if (segment != null && segment.length() > 0) {
                    result.append(AndroidConstants.RES_QUALIFIER_SEP);
                    result.append(segment);
                }
            }
        }

        return result.toString();
    }

    /**
     * Returns {@link #toDisplayString()}.
     */
    @Override
    public String toString() {
        return toDisplayString();
    }

    /**
     * Returns a string valid for display purpose.
     */
    public String toDisplayString() {
        if (isDefault()) {
            return "default";
        }

        StringBuilder result = null;
        int index = 0;
        ResourceQualifier qualifier = null;

        // pre- language/region qualifiers
        while (index < INDEX_LANGUAGE) {
            qualifier = mQualifiers[index++];
            if (qualifier != null) {
                if (result == null) {
                    result = new StringBuilder();
                } else {
                    result.append(", "); //$NON-NLS-1$
                }
                result.append(qualifier.getLongDisplayValue());

            }
        }

        // process the language/region qualifier in a custom way, if there are both non null.
        if (mQualifiers[INDEX_LANGUAGE] != null && mQualifiers[INDEX_REGION] != null) {
            String language = mQualifiers[INDEX_LANGUAGE].getLongDisplayValue();
            String region = mQualifiers[INDEX_REGION].getLongDisplayValue();

            if (result == null) {
                result = new StringBuilder();
            } else {
                result.append(", "); //$NON-NLS-1$
            }
            result.append(String.format("Locale %s_%s", language, region)); //$NON-NLS-1$

            index += 2;
        }

        // post language/region qualifiers.
        while (index < INDEX_COUNT) {
            qualifier = mQualifiers[index++];
            if (qualifier != null) {
                if (result == null) {
                    result = new StringBuilder();
                } else {
                    result.append(", "); //$NON-NLS-1$
                }
                result.append(qualifier.getLongDisplayValue());

            }
        }

        return result == null ? null : result.toString();
    }

    public int compareTo(FolderConfiguration folderConfig) {
        // default are always at the top.
        if (isDefault()) {
            if (folderConfig.isDefault()) {
                return 0;
            }
            return -1;
        }

        // now we compare the qualifiers
        for (int i = 0 ; i < INDEX_COUNT; i++) {
            ResourceQualifier qualifier1 = mQualifiers[i];
            ResourceQualifier qualifier2 = folderConfig.mQualifiers[i];

            if (qualifier1 == null) {
                if (qualifier2 == null) {
                    continue;
                } else {
                    return -1;
                }
            } else {
                if (qualifier2 == null) {
                    return 1;
                } else {
                    int result = qualifier1.compareTo(qualifier2);

                    if (result == 0) {
                        continue;
                    }

                    return result;
                }
            }
        }

        // if we arrive here, all the qualifier matches
        return 0;
    }

    /**
     * Returns the best matching {@link Configurable} for this configuration.
     *
     * @param configurables the list of {@link Configurable} to choose from.
     *
     * @return an item from the given list of {@link Configurable} or null.
     *
     * @see http://d.android.com/guide/topics/resources/resources-i18n.html#best-match
     */
    public Configurable findMatchingConfigurable(List<? extends Configurable> configurables) {
        //
        // 1: eliminate resources that contradict the reference configuration
        // 2: pick next qualifier type
        // 3: check if any resources use this qualifier, if no, back to 2, else move on to 4.
        // 4: eliminate resources that don't use this qualifier.
        // 5: if more than one resource left, go back to 2.
        //
        // The precedence of the qualifiers is more important than the number of qualifiers that
        // exactly match the device.

        // 1: eliminate resources that contradict
        ArrayList<Configurable> matchingConfigurables = new ArrayList<Configurable>();
        for (int i = 0 ; i < configurables.size(); i++) {
            Configurable res = configurables.get(i);

            if (res.getConfiguration().isMatchFor(this)) {
                matchingConfigurables.add(res);
            }
        }

        // if there is only one match, just take it
        if (matchingConfigurables.size() == 1) {
            return matchingConfigurables.get(0);
        } else if (matchingConfigurables.size() == 0) {
            return null;
        }

        // 2. Loop on the qualifiers, and eliminate matches
        final int count = FolderConfiguration.getQualifierCount();
        for (int q = 0 ; q < count ; q++) {
            // look to see if one configurable has this qualifier.
            // At the same time also record the best match value for the qualifier (if applicable).

            // The reference value, to find the best match.
            // Note that this qualifier could be null. In which case any qualifier found in the
            // possible match, will all be considered best match.
            ResourceQualifier referenceQualifier = getQualifier(q);

            boolean found = false;
            ResourceQualifier bestMatch = null; // this is to store the best match.
            for (Configurable configurable : matchingConfigurables) {
                ResourceQualifier qualifier = configurable.getConfiguration().getQualifier(q);
                if (qualifier != null) {
                    // set the flag.
                    found = true;

                    // Now check for a best match. If the reference qualifier is null ,
                    // any qualifier is a "best" match (we don't need to record all of them.
                    // Instead the non compatible ones are removed below)
                    if (referenceQualifier != null) {
                        if (qualifier.isBetterMatchThan(bestMatch, referenceQualifier)) {
                            bestMatch = qualifier;
                        }
                    }
                }
            }

            // 4. If a configurable has a qualifier at the current index, remove all the ones that
            // do not have one, or whose qualifier value does not equal the best match found above
            // unless there's no reference qualifier, in which case they are all considered
            // "best" match.
            if (found) {
                for (int i = 0 ; i < matchingConfigurables.size(); ) {
                    Configurable configurable = matchingConfigurables.get(i);
                    ResourceQualifier qualifier = configurable.getConfiguration().getQualifier(q);

                    if (qualifier == null) {
                        // this resources has no qualifier of this type: rejected.
                        matchingConfigurables.remove(configurable);
                    } else if (referenceQualifier != null && bestMatch != null &&
                            bestMatch.equals(qualifier) == false) {
                        // there's a reference qualifier and there is a better match for it than
                        // this resource, so we reject it.
                        matchingConfigurables.remove(configurable);
                    } else {
                        // looks like we keep this resource, move on to the next one.
                        i++;
                    }
                }

                // at this point we may have run out of matching resources before going
                // through all the qualifiers.
                if (matchingConfigurables.size() < 2) {
                    break;
                }
            }
        }

        // Because we accept resources whose configuration have qualifiers where the reference
        // configuration doesn't, we can end up with more than one match. In this case, we just
        // take the first one.
        if (matchingConfigurables.size() == 0) {
            return null;
        }
        return matchingConfigurables.get(0);
    }


    /**
     * Returns whether the configuration is a match for the given reference config.
     * <p/>A match means that, for each qualifier of this config
     * <ul>
     * <li>The reference config has no value set
     * <li>or, the qualifier of the reference config is a match. Depending on the qualifier type
     * this does not mean the same exact value.</li>
     * </ul>
     * @param referenceConfig The reference configuration to test against.
     * @return true if the configuration matches.
     */
    public boolean isMatchFor(FolderConfiguration referenceConfig) {
        if (referenceConfig == null) {
            return false;
        }

        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            ResourceQualifier testQualifier = mQualifiers[i];
            ResourceQualifier referenceQualifier = referenceConfig.mQualifiers[i];

            // it's only a non match if both qualifiers are non-null, and they don't match.
            if (testQualifier != null && referenceQualifier != null &&
                        testQualifier.isMatchFor(referenceQualifier) == false) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the index of the first non null {@link ResourceQualifier} starting at index
     * <var>startIndex</var>
     * @param startIndex
     * @return -1 if no qualifier was found.
     */
    public int getHighestPriorityQualifier(int startIndex) {
        for (int i = startIndex ; i < INDEX_COUNT ; i++) {
            if (mQualifiers[i] != null) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Create default qualifiers.
     * <p/>This creates qualifiers with no values for all indices.
     */
    public void createDefault() {
        mQualifiers[INDEX_COUNTRY_CODE] = new CountryCodeQualifier();
        mQualifiers[INDEX_NETWORK_CODE] = new NetworkCodeQualifier();
        mQualifiers[INDEX_LANGUAGE] = new LanguageQualifier();
        mQualifiers[INDEX_REGION] = new RegionQualifier();
        mQualifiers[INDEX_SMALLEST_SCREEN_WIDTH] = new SmallestScreenWidthQualifier();
        mQualifiers[INDEX_SCREEN_WIDTH] = new ScreenWidthQualifier();
        mQualifiers[INDEX_SCREEN_HEIGHT] = new ScreenHeightQualifier();
        mQualifiers[INDEX_SCREEN_LAYOUT_SIZE] = new ScreenSizeQualifier();
        mQualifiers[INDEX_SCREEN_RATIO] = new ScreenRatioQualifier();
        mQualifiers[INDEX_SCREEN_ORIENTATION] = new ScreenOrientationQualifier();
        mQualifiers[INDEX_UI_MODE] = new UiModeQualifier();
        mQualifiers[INDEX_NIGHT_MODE] = new NightModeQualifier();
        mQualifiers[INDEX_PIXEL_DENSITY] = new DensityQualifier();
        mQualifiers[INDEX_TOUCH_TYPE] = new TouchScreenQualifier();
        mQualifiers[INDEX_KEYBOARD_STATE] = new KeyboardStateQualifier();
        mQualifiers[INDEX_TEXT_INPUT_METHOD] = new TextInputMethodQualifier();
        mQualifiers[INDEX_NAVIGATION_STATE] = new NavigationStateQualifier();
        mQualifiers[INDEX_NAVIGATION_METHOD] = new NavigationMethodQualifier();
        mQualifiers[INDEX_SCREEN_DIMENSION] = new ScreenDimensionQualifier();
        mQualifiers[INDEX_VERSION] = new VersionQualifier();
    }

    /**
     * Returns an array of all the non null qualifiers.
     */
    public ResourceQualifier[] getQualifiers() {
        int count = 0;
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (mQualifiers[i] != null) {
                count++;
            }
        }

        ResourceQualifier[] array = new ResourceQualifier[count];
        int index = 0;
        for (int i = 0 ; i < INDEX_COUNT ; i++) {
            if (mQualifiers[i] != null) {
                array[index++] = mQualifiers[i];
            }
        }

        return array;
    }
}
