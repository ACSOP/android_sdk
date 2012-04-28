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

package com.android.sdklib.internal.avd;

import com.android.io.FileWrapper;
import com.android.prefs.AndroidLocation;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.ISystemImage;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdInfo.AvdStatus;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.util.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Android Virtual Device Manager to manage AVDs.
 */
public class AvdManager {

    /**
     * Exception thrown when something is wrong with a target path.
     */
    private final static class InvalidTargetPathException extends Exception {
        private static final long serialVersionUID = 1L;

        InvalidTargetPathException(String message) {
            super(message);
        }
    }

    public static final String AVD_FOLDER_EXTENSION = ".avd";  //$NON-NLS-1$

    public final static String AVD_INFO_PATH = "path";         //$NON-NLS-1$
    public final static String AVD_INFO_TARGET = "target";     //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the abi type of the specific avd
     *
     */
    public final static String AVD_INI_ABI_TYPE = "abi.type"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the CPU architecture of the specific avd
     *
     */
    public final static String AVD_INI_CPU_ARCH = "hw.cpu.arch"; //$NON-NLS-1$

    /**
     * AVD/config.ini key name representing the CPU architecture of the specific avd
     *
     */
    public final static String AVD_INI_CPU_MODEL = "hw.cpu.model"; //$NON-NLS-1$


    /**
     * AVD/config.ini key name representing the SDK-relative path of the skin folder, if any,
     * or a 320x480 like constant for a numeric skin size.
     *
     * @see #NUMERIC_SKIN_SIZE
     */
    public final static String AVD_INI_SKIN_PATH = "skin.path"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing an UI name for the skin.
     * This config key is ignored by the emulator. It is only used by the SDK manager or
     * tools to give a friendlier name to the skin.
     * If missing, use the {@link #AVD_INI_SKIN_PATH} key instead.
     */
    public final static String AVD_INI_SKIN_NAME = "skin.name"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the path to the sdcard file.
     * If missing, the default name "sdcard.img" will be used for the sdcard, if there's such
     * a file.
     *
     * @see #SDCARD_IMG
     */
    public final static String AVD_INI_SDCARD_PATH = "sdcard.path"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the size of the SD card.
     * This property is for UI purposes only. It is not used by the emulator.
     *
     * @see #SDCARD_SIZE_PATTERN
     * @see #parseSdcardSize(String, String[])
     */
    public final static String AVD_INI_SDCARD_SIZE = "sdcard.size"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the first path where the emulator looks
     * for system images. Typically this is the path to the add-on system image or
     * the path to the platform system image if there's no add-on.
     * <p/>
     * The emulator looks at {@link #AVD_INI_IMAGES_1} before {@link #AVD_INI_IMAGES_2}.
     */
    public final static String AVD_INI_IMAGES_1 = "image.sysdir.1"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the second path where the emulator looks
     * for system images. Typically this is the path to the platform system image.
     *
     * @see #AVD_INI_IMAGES_1
     */
    public final static String AVD_INI_IMAGES_2 = "image.sysdir.2"; //$NON-NLS-1$
    /**
     * AVD/config.ini key name representing the presence of the snapshots file.
     * This property is for UI purposes only. It is not used by the emulator.
     *
     * @see #SNAPSHOTS_IMG
     */
    public final static String AVD_INI_SNAPSHOT_PRESENT = "snapshot.present"; //$NON-NLS-1$

    /**
     * Pattern to match pixel-sized skin "names", e.g. "320x480".
     */
    public final static Pattern NUMERIC_SKIN_SIZE = Pattern.compile("([0-9]{2,})x([0-9]{2,})"); //$NON-NLS-1$

    private final static String USERDATA_IMG = "userdata.img"; //$NON-NLS-1$
    final static String CONFIG_INI = "config.ini"; //$NON-NLS-1$
    private final static String SDCARD_IMG = "sdcard.img"; //$NON-NLS-1$
    private final static String SNAPSHOTS_IMG = "snapshots.img"; //$NON-NLS-1$

    final static String INI_EXTENSION = ".ini"; //$NON-NLS-1$
    private final static Pattern INI_NAME_PATTERN = Pattern.compile("(.+)\\" + //$NON-NLS-1$
            INI_EXTENSION + "$",                                               //$NON-NLS-1$
            Pattern.CASE_INSENSITIVE);

    private final static Pattern IMAGE_NAME_PATTERN = Pattern.compile("(.+)\\.img$", //$NON-NLS-1$
            Pattern.CASE_INSENSITIVE);

    /**
     * Pattern for matching SD Card sizes, e.g. "4K" or "16M".
     * Callers should use {@link #parseSdcardSize(String, String[])} instead of using this directly.
     */
    private final static Pattern SDCARD_SIZE_PATTERN = Pattern.compile("(\\d+)([KMG])"); //$NON-NLS-1$

    /**
     * Minimal size of an SDCard image file in bytes. Currently 9 MiB.
     */

    public static final long SDCARD_MIN_BYTE_SIZE = 9<<20;
    /**
     * Maximal size of an SDCard image file in bytes. Currently 1023 GiB.
     */
    public static final long SDCARD_MAX_BYTE_SIZE = 1023L<<30;

    /** The sdcard string represents a valid number but the size is outside of the allowed range. */
    public final static int SDCARD_SIZE_NOT_IN_RANGE = 0;
    /** The sdcard string looks like a size number+suffix but the number failed to decode. */
    public final static int SDCARD_SIZE_INVALID = -1;
    /** The sdcard string doesn't look like a size, it might be a path instead. */
    public final static int SDCARD_NOT_SIZE_PATTERN = -2;

    /** Regex used to validate characters that compose an AVD name. */
    public final static Pattern RE_AVD_NAME = Pattern.compile("[a-zA-Z0-9._-]+"); //$NON-NLS-1$

    /** List of valid characters for an AVD name. Used for display purposes. */
    public final static String CHARS_AVD_NAME = "a-z A-Z 0-9 . _ -"; //$NON-NLS-1$

    public final static String HARDWARE_INI = "hardware.ini"; //$NON-NLS-1$

    /**
     * Status returned by {@link AvdManager#isAvdNameConflicting(String)}.
     */
    public static enum AvdConflict {
        /** There is no known conflict for the given AVD name. */
        NO_CONFLICT,
        /** The AVD name conflicts with an existing valid AVD. */
        CONFLICT_EXISTING_AVD,
        /** The AVD name conflicts with an existing invalid AVD. */
        CONFLICT_INVALID_AVD,
        /**
         * The AVD name does not conflict with any known AVD however there are
         * files or directory that would cause a conflict if this were to be created.
         */
        CONFLICT_EXISTING_PATH,
    }

    private final ArrayList<AvdInfo> mAllAvdList = new ArrayList<AvdInfo>();
    private AvdInfo[] mValidAvdList;
    private AvdInfo[] mBrokenAvdList;
    private final SdkManager mSdkManager;

    /**
     * Creates an AVD Manager for a given SDK represented by a {@link SdkManager}.
     * @param sdkManager The SDK.
     * @param log The log object to receive the log of the initial loading of the AVDs.
     *            This log object is not kept by this instance of AvdManager and each
     *            method takes its own logger. The rationale is that the AvdManager
     *            might be called from a variety of context, each with different
     *            logging needs. Cannot be null.
     * @throws AndroidLocationException
     */
    public AvdManager(SdkManager sdkManager, ISdkLog log) throws AndroidLocationException {
        mSdkManager = sdkManager;
        buildAvdList(mAllAvdList, log);
    }

    /**
     * Returns the base folder where AVDs are created.
     *
     * @throws AndroidLocationException
     */
    public String getBaseAvdFolder() throws AndroidLocationException {
        assert AndroidLocation.getFolder().endsWith(File.separator);
        return AndroidLocation.getFolder() + AndroidLocation.FOLDER_AVD;
    }

    /**
     * Returns the {@link SdkManager} associated with the {@link AvdManager}.
     */
    public SdkManager getSdkManager() {
        return mSdkManager;
    }

    /**
     * Parse the sdcard string to decode the size.
     * Returns:
     * <ul>
     * <li> The size in bytes > 0 if the sdcard string is a valid size in the allowed range.
     * <li> {@link #SDCARD_SIZE_NOT_IN_RANGE} (0)
     *          if the sdcard string is a valid size NOT in the allowed range.
     * <li> {@link #SDCARD_SIZE_INVALID} (-1)
     *          if the sdcard string is number that fails to parse correctly.
     * <li> {@link #SDCARD_NOT_SIZE_PATTERN} (-2)
     *          if the sdcard string is not a number, in which case it's probably a file path.
     * </ul>
     *
     * @param sdcard The sdcard string, which can be a file path, a size string or something else.
     * @param parsedStrings If non-null, an array of 2 strings. The first string will be
     *  filled with the parsed numeric size and the second one will be filled with the
     *  parsed suffix. This is filled even if the returned size is deemed out of range or
     *  failed to parse. The values are null if the sdcard is not a size pattern.
     * @return A size in byte if > 0, or {@link #SDCARD_SIZE_NOT_IN_RANGE},
     *  {@link #SDCARD_SIZE_INVALID} or {@link #SDCARD_NOT_SIZE_PATTERN} as error codes.
     */
    public static long parseSdcardSize(String sdcard, String[] parsedStrings) {

        if (parsedStrings != null) {
            assert parsedStrings.length == 2;
            parsedStrings[0] = null;
            parsedStrings[1] = null;
        }

        Matcher m = SDCARD_SIZE_PATTERN.matcher(sdcard);
        if (m.matches()) {
            if (parsedStrings != null) {
                assert parsedStrings.length == 2;
                parsedStrings[0] = m.group(1);
                parsedStrings[1] = m.group(2);
            }

            // get the sdcard values for checks
            try {
                long sdcardSize = Long.parseLong(m.group(1));

                String sdcardSizeModifier = m.group(2);
                if ("K".equals(sdcardSizeModifier)) {           //$NON-NLS-1$
                    sdcardSize <<= 10;
                } else if ("M".equals(sdcardSizeModifier)) {    //$NON-NLS-1$
                    sdcardSize <<= 20;
                } else if ("G".equals(sdcardSizeModifier)) {    //$NON-NLS-1$
                    sdcardSize <<= 30;
                }

                if (sdcardSize < SDCARD_MIN_BYTE_SIZE ||
                        sdcardSize > SDCARD_MAX_BYTE_SIZE) {
                    return SDCARD_SIZE_NOT_IN_RANGE;
                }

                return sdcardSize;
            } catch (NumberFormatException e) {
                // This could happen if the number is too large to fit in a long.
                return SDCARD_SIZE_INVALID;
            }
        }

        return SDCARD_NOT_SIZE_PATTERN;
    }

    /**
     * Returns all the existing AVDs.
     * @return a newly allocated array containing all the AVDs.
     */
    public AvdInfo[] getAllAvds() {
        synchronized (mAllAvdList) {
            return mAllAvdList.toArray(new AvdInfo[mAllAvdList.size()]);
        }
    }

    /**
     * Returns all the valid AVDs.
     * @return a newly allocated array containing all valid the AVDs.
     */
    public AvdInfo[] getValidAvds() {
        synchronized (mAllAvdList) {
            if (mValidAvdList == null) {
                ArrayList<AvdInfo> list = new ArrayList<AvdInfo>();
                for (AvdInfo avd : mAllAvdList) {
                    if (avd.getStatus() == AvdStatus.OK) {
                        list.add(avd);
                    }
                }

                mValidAvdList = list.toArray(new AvdInfo[list.size()]);
            }
            return mValidAvdList;
        }
    }

    /**
     * Returns all the broken AVDs.
     * @return a newly allocated array containing all the broken AVDs.
     */
    public AvdInfo[] getBrokenAvds() {
        synchronized (mAllAvdList) {
            if (mBrokenAvdList == null) {
                ArrayList<AvdInfo> list = new ArrayList<AvdInfo>();
                for (AvdInfo avd : mAllAvdList) {
                    if (avd.getStatus() != AvdStatus.OK) {
                        list.add(avd);
                    }
                }
                mBrokenAvdList = list.toArray(new AvdInfo[list.size()]);
            }
            return mBrokenAvdList;
        }
    }

    /**
     * Returns the {@link AvdInfo} matching the given <var>name</var>.
     * <p/>
     * The search is case-insensitive.
     *
     * @param name the name of the AVD to return
     * @param validAvdOnly if <code>true</code>, only look through the list of valid AVDs.
     * @return the matching AvdInfo or <code>null</code> if none were found.
     */
    public AvdInfo getAvd(String name, boolean validAvdOnly) {

        boolean ignoreCase = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

        if (validAvdOnly) {
            for (AvdInfo info : getValidAvds()) {
                String name2 = info.getName();
                if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                    return info;
                }
            }
        } else {
            synchronized (mAllAvdList) {
                for (AvdInfo info : mAllAvdList) {
                    String name2 = info.getName();
                    if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                        return info;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns whether this AVD name would generate a conflict.
     *
     * @param name the name of the AVD to return
     * @return A pair of {@link AvdConflict} and the path or AVD name that conflicts.
     */
    public Pair<AvdConflict, String> isAvdNameConflicting(String name) {

        boolean ignoreCase = SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS;

        // Check whether we have a conflict with an existing or invalid AVD
        // known to the manager.
        synchronized (mAllAvdList) {
            for (AvdInfo info : mAllAvdList) {
                String name2 = info.getName();
                if (name2.equals(name) || (ignoreCase && name2.equalsIgnoreCase(name))) {
                    if (info.getStatus() == AvdStatus.OK) {
                        return Pair.of(AvdConflict.CONFLICT_EXISTING_AVD, name2);
                    } else {
                        return Pair.of(AvdConflict.CONFLICT_INVALID_AVD, name2);
                    }
                }
            }
        }

        // No conflict with known AVDs.
        // Are some existing files/folders in the way of creating this AVD?

        try {
            File file = AvdInfo.getDefaultIniFile(this, name);
            if (file.exists()) {
                return Pair.of(AvdConflict.CONFLICT_EXISTING_PATH, file.getPath());
            }

            file = AvdInfo.getDefaultAvdFolder(this, name);
            if (file.exists()) {
                return Pair.of(AvdConflict.CONFLICT_EXISTING_PATH, file.getPath());
            }

        } catch (AndroidLocationException e) {
            // ignore
        }


        return Pair.of(AvdConflict.NO_CONFLICT, null);
    }

    /**
     * Reloads the AVD list.
     * @param log the log object to receive action logs. Cannot be null.
     * @throws AndroidLocationException if there was an error finding the location of the
     * AVD folder.
     */
    public void reloadAvds(ISdkLog log) throws AndroidLocationException {
        // build the list in a temp list first, in case the method throws an exception.
        // It's better than deleting the whole list before reading the new one.
        ArrayList<AvdInfo> allList = new ArrayList<AvdInfo>();
        buildAvdList(allList, log);

        synchronized (mAllAvdList) {
            mAllAvdList.clear();
            mAllAvdList.addAll(allList);
            mValidAvdList = mBrokenAvdList = null;
        }
    }

    /**
     * Creates a new AVD. It is expected that there is no existing AVD with this name already.
     *
     * @param avdFolder the data folder for the AVD. It will be created as needed.
     *   Unless you want to locate it in a specific directory, the ideal default is
     *   {@code AvdManager.AvdInfo.getAvdFolder}.
     * @param avdName the name of the AVD
     * @param target the target of the AVD
     * @param abiType the abi type of the AVD
     * @param skinName the name of the skin. Can be null. Must have been verified by caller.
     * @param sdcard the parameter value for the sdCard. Can be null. This is either a path to
     *        an existing sdcard image or a sdcard size (\d+, \d+K, \dM).
     * @param hardwareConfig the hardware setup for the AVD. Can be null to use defaults.
     * @param createSnapshot If true copy a blank snapshot image into the AVD.
     * @param removePrevious If true remove any previous files.
     * @param editExisting If true, edit an existing AVD, changing only the minimum required.
     *          This won't remove files unless required or unless {@code removePrevious} is set.
     * @param log the log object to receive action logs. Cannot be null.
     * @return The new {@link AvdInfo} in case of success (which has just been added to the
     *         internal list) or null in case of failure.
     */
    public AvdInfo createAvd(
            File avdFolder,
            String avdName,
            IAndroidTarget target,
            String abiType,
            String skinName,
            String sdcard,
            Map<String,String> hardwareConfig,
            boolean createSnapshot,
            boolean removePrevious,
            boolean editExisting,
            ISdkLog log) {
        if (log == null) {
            throw new IllegalArgumentException("log cannot be null");
        }

        File iniFile = null;
        boolean needCleanup = false;
        try {
            if (avdFolder.exists()) {
                if (removePrevious) {
                    // AVD already exists and removePrevious is set, try to remove the
                    // directory's content first (but not the directory itself).
                    try {
                        deleteContentOf(avdFolder);
                    } catch (SecurityException e) {
                        log.error(e, "Failed to delete %1$s", avdFolder.getAbsolutePath());
                    }
                } else if (!editExisting) {
                    // AVD shouldn't already exist if removePrevious is false and
                    // we're not editing an existing AVD.
                    log.error(null,
                            "Folder %1$s is in the way. Use --force if you want to overwrite.",
                            avdFolder.getAbsolutePath());
                    return null;
                }
            } else {
                // create the AVD folder.
                avdFolder.mkdir();
                // We're not editing an existing AVD.
                editExisting = false;
            }

            // actually write the ini file
            iniFile = createAvdIniFile(avdName, avdFolder, target, removePrevious);

            // writes the userdata.img in it.

            File userdataSrc = null;

            // Look for a system image in the add-on.
            // If we don't find one there, look in the base platform.
            ISystemImage systemImage = target.getSystemImage(abiType);

            if (systemImage != null) {
                File imageFolder = systemImage.getLocation();
                userdataSrc = new File(imageFolder, USERDATA_IMG);
            }

            if ((userdataSrc == null || !userdataSrc.exists()) && !target.isPlatform()) {
                // If we don't find a system-image in the add-on, look into the platform.

                systemImage = target.getParent().getSystemImage(abiType);
                if (systemImage != null) {
                    File imageFolder = systemImage.getLocation();
                    userdataSrc = new File(imageFolder, USERDATA_IMG);
                }
            }

            if (userdataSrc == null || !userdataSrc.exists()) {
                log.error(null,
                        "Unable to find a '%1$s' file for ABI %2$s to copy into the AVD folder.",
                        USERDATA_IMG,
                        abiType);
                needCleanup = true;
                return null;
            }

            File userdataDest = new File(avdFolder, USERDATA_IMG);

            copyImageFile(userdataSrc, userdataDest);

            if (userdataDest.exists() == false) {
                log.error(null, "Unable to create '%1$s' file in the AVD folder.",
                        userdataDest);
                needCleanup = true;
                return null;
            }

            // Config file.
            HashMap<String, String> values = new HashMap<String, String>();

           if (setImagePathProperties(target, abiType, values, log) == false) {
               log.error(null, "Failed to set image path properties in the AVD folder.");
               needCleanup = true;
               return null;
            }

            // Create the snapshot file
            if (createSnapshot) {
                File snapshotDest = new File(avdFolder, SNAPSHOTS_IMG);
                if (snapshotDest.isFile() && editExisting) {
                    log.printf("Snapshot image already present, was not changed.\n");

                } else {
                    String toolsLib = mSdkManager.getLocation() + File.separator
                                      + SdkConstants.OS_SDK_TOOLS_LIB_EMULATOR_FOLDER;
                    File snapshotBlank = new File(toolsLib, SNAPSHOTS_IMG);
                    if (snapshotBlank.exists() == false) {
                        log.error(null,
                                "Unable to find a '%2$s%1$s' file to copy into the AVD folder.",
                                SNAPSHOTS_IMG, toolsLib);
                        needCleanup = true;
                        return null;
                    }

                    copyImageFile(snapshotBlank, snapshotDest);
                }
                values.put(AVD_INI_SNAPSHOT_PRESENT, "true");
            }

            // Now the abi type
            values.put(AVD_INI_ABI_TYPE, abiType);

            // and the cpu arch.
            if (SdkConstants.ABI_ARMEABI.equals(abiType)) {
                values.put(AVD_INI_CPU_ARCH, SdkConstants.CPU_ARCH_ARM);
            } else if (SdkConstants.ABI_ARMEABI_V7A.equals(abiType)) {
                values.put(AVD_INI_CPU_ARCH, SdkConstants.CPU_ARCH_ARM);
                values.put(AVD_INI_CPU_MODEL, SdkConstants.CPU_MODEL_CORTEX_A8);
            } else if (SdkConstants.ABI_INTEL_ATOM.equals(abiType)) {
                values.put(AVD_INI_CPU_ARCH, SdkConstants.CPU_ARCH_INTEL_ATOM);
            } else {
                log.error(null,
                        "ABI %1$s is not supported by this version of the SDK Tools", abiType);
                needCleanup = true;
                return null;
            }

            // Now the skin.
            if (skinName == null || skinName.length() == 0) {
                skinName = target.getDefaultSkin();
            }

            if (NUMERIC_SKIN_SIZE.matcher(skinName).matches()) {
                // Skin name is an actual screen resolution.
                // Set skin.name for display purposes in the AVD manager and
                // set skin.path for use by the emulator.
                values.put(AVD_INI_SKIN_NAME, skinName);
                values.put(AVD_INI_SKIN_PATH, skinName);
            } else {
                // get the path of the skin (relative to the SDK)
                // assume skin name is valid
                String skinPath = getSkinRelativePath(skinName, target, log);
                if (skinPath == null) {
                    log.error(null, "Missing skinpath in the AVD folder.");
                    needCleanup = true;
                    return null;
                }

                values.put(AVD_INI_SKIN_PATH, skinPath);
                values.put(AVD_INI_SKIN_NAME, skinName);
            }

            if (sdcard != null && sdcard.length() > 0) {
                // Sdcard is possibly a size. In that case we create a file called 'sdcard.img'
                // in the AVD folder, and do not put any value in config.ini.

                long sdcardSize = parseSdcardSize(sdcard, null/*parsedStrings*/);

                if (sdcardSize == SDCARD_SIZE_NOT_IN_RANGE) {
                    log.error(null, "SD Card size must be in the range 9 MiB..1023 GiB.");
                    needCleanup = true;
                    return null;

                } else if (sdcardSize == SDCARD_SIZE_INVALID) {
                    log.error(null, "Unable to parse SD Card size");
                    needCleanup = true;
                    return null;

                } else if (sdcardSize == SDCARD_NOT_SIZE_PATTERN) {
                    File sdcardFile = new File(sdcard);
                    if (sdcardFile.isFile()) {
                        // sdcard value is an external sdcard, so we put its path into the config.ini
                        values.put(AVD_INI_SDCARD_PATH, sdcard);
                    } else {
                        log.error(null, "'%1$s' is not recognized as a valid sdcard value.\n"
                                + "Value should be:\n" + "1. path to an sdcard.\n"
                                + "2. size of the sdcard to create: <size>[K|M]", sdcard);
                        needCleanup = true;
                        return null;
                    }
                } else {
                    // create the sdcard.
                    File sdcardFile = new File(avdFolder, SDCARD_IMG);

                    boolean runMkSdcard = true;
                    if (sdcardFile.exists()) {
                        if (sdcardFile.length() == sdcardSize && editExisting) {
                            // There's already an sdcard file with the right size and we're
                            // not overriding it... so don't remove it.
                            runMkSdcard = false;
                            log.printf("SD Card already present with same size, was not changed.\n");
                        }
                    }

                    if (runMkSdcard) {
                        String path = sdcardFile.getAbsolutePath();

                        // execute mksdcard with the proper parameters.
                        File toolsFolder = new File(mSdkManager.getLocation(),
                                SdkConstants.FD_TOOLS);
                        File mkSdCard = new File(toolsFolder, SdkConstants.mkSdCardCmdName());

                        if (mkSdCard.isFile() == false) {
                            log.error(null, "'%1$s' is missing from the SDK tools folder.",
                                    mkSdCard.getName());
                            needCleanup = true;
                            return null;
                        }

                        if (createSdCard(mkSdCard.getAbsolutePath(), sdcard, path, log) == false) {
                            log.error(null, "Failed to create sdcard in the AVD folder.");
                            needCleanup = true;
                            return null; // mksdcard output has already been displayed, no need to
                                         // output anything else.
                        }
                    }

                    // add a property containing the size of the sdcard for display purpose
                    // only when the dev does 'android list avd'
                    values.put(AVD_INI_SDCARD_SIZE, sdcard);
                }
            }

            // add the hardware config to the config file.
            // priority order is:
            // - values provided by the user
            // - values provided by the skin
            // - values provided by the target (add-on only).
            // In order to follow this priority, we'll add the lowest priority values first and then
            // override by higher priority values.
            // In the case of a platform with override values from the user, the skin value might
            // already be there, but it's ok.

            HashMap<String, String> finalHardwareValues = new HashMap<String, String>();

            FileWrapper targetHardwareFile = new FileWrapper(target.getLocation(),
                    AvdManager.HARDWARE_INI);
            if (targetHardwareFile.isFile()) {
                Map<String, String> targetHardwareConfig = ProjectProperties.parsePropertyFile(
                        targetHardwareFile, log);

                if (targetHardwareConfig != null) {
                    finalHardwareValues.putAll(targetHardwareConfig);
                    values.putAll(targetHardwareConfig);
                }
            }

            // get the hardware properties for this skin
            File skinFolder = getSkinPath(skinName, target);
            FileWrapper skinHardwareFile = new FileWrapper(skinFolder, AvdManager.HARDWARE_INI);
            if (skinHardwareFile.isFile()) {
                Map<String, String> skinHardwareConfig = ProjectProperties.parsePropertyFile(
                        skinHardwareFile, log);

                if (skinHardwareConfig != null) {
                    finalHardwareValues.putAll(skinHardwareConfig);
                    values.putAll(skinHardwareConfig);
                }
            }

            // finally put the hardware provided by the user.
            if (hardwareConfig != null) {
                finalHardwareValues.putAll(hardwareConfig);
                values.putAll(hardwareConfig);
            }

            File configIniFile = new File(avdFolder, CONFIG_INI);
            writeIniFile(configIniFile, values);

            // Generate the log report first because we want to control where line breaks
            // are located when generating the hardware config list.
            StringBuilder report = new StringBuilder();

            if (target.isPlatform()) {
                if (editExisting) {
                    report.append(String.format("Updated AVD '%1$s' based on %2$s",
                            avdName, target.getName()));
                } else {
                    report.append(String.format("Created AVD '%1$s' based on %2$s",
                            avdName, target.getName()));
                }
            } else {
                if (editExisting) {
                    report.append(String.format("Updated AVD '%1$s' based on %2$s (%3$s)", avdName,
                            target.getName(), target.getVendor()));
                } else {
                    report.append(String.format("Created AVD '%1$s' based on %2$s (%3$s)", avdName,
                            target.getName(), target.getVendor()));
                }
            }
            report.append(String.format(", %s processor", AvdInfo.getPrettyAbiType(abiType)));

            // display the chosen hardware config
            if (finalHardwareValues.size() > 0) {
                report.append(",\nwith the following hardware config:\n");
                for (Entry<String, String> entry : finalHardwareValues.entrySet()) {
                    report.append(String.format("%s=%s\n",entry.getKey(), entry.getValue()));
                }
            } else {
                report.append("\n");
            }

            log.printf(report.toString());

            // create the AvdInfo object, and add it to the list
            AvdInfo newAvdInfo = new AvdInfo(
                    avdName,
                    iniFile,
                    avdFolder.getAbsolutePath(),
                    target.hashString(),
                    target, abiType, values);

            AvdInfo oldAvdInfo = getAvd(avdName, false /*validAvdOnly*/);

            synchronized (mAllAvdList) {
                if (oldAvdInfo != null && (removePrevious || editExisting)) {
                    mAllAvdList.remove(oldAvdInfo);
                }
                mAllAvdList.add(newAvdInfo);
                mValidAvdList = mBrokenAvdList = null;
            }

            if ((removePrevious || editExisting) &&
                    newAvdInfo != null &&
                    oldAvdInfo != null &&
                    !oldAvdInfo.getDataFolderPath().equals(newAvdInfo.getDataFolderPath())) {
                log.warning("Removing previous AVD directory at %s",
                        oldAvdInfo.getDataFolderPath());
                // Remove the old data directory
                File dir = new File(oldAvdInfo.getDataFolderPath());
                try {
                    deleteContentOf(dir);
                    dir.delete();
                } catch (SecurityException e) {
                    log.error(e, "Failed to delete %1$s", dir.getAbsolutePath());
                }
            }

            return newAvdInfo;
        } catch (AndroidLocationException e) {
            log.error(e, null);
        } catch (IOException e) {
            log.error(e, null);
        } catch (SecurityException e) {
            log.error(e, null);
        } finally {
            if (needCleanup) {
                if (iniFile != null && iniFile.exists()) {
                    iniFile.delete();
                }

                try {
                    deleteContentOf(avdFolder);
                    avdFolder.delete();
                } catch (SecurityException e) {
                    log.error(e, "Failed to delete %1$s", avdFolder.getAbsolutePath());
                }
            }
        }

        return null;
    }

    /**
     * Copy the nominated file to the given destination.
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void copyImageFile(File source, File destination)
            throws FileNotFoundException, IOException {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(destination);

        byte[] buffer = new byte[4096];
        int count;
        while ((count = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, count);
        }

        fos.close();
        fis.close();
    }

    /**
     * Returns the path to the target images folder as a relative path to the SDK, if the folder
     * is not empty. If the image folder is empty or does not exist, <code>null</code> is returned.
     * @throws InvalidTargetPathException if the target image folder is not in the current SDK.
     */
    private String getImageRelativePath(IAndroidTarget target, String abiType)
            throws InvalidTargetPathException {

        ISystemImage systemImage = target.getSystemImage(abiType);
        if (systemImage == null) {
            // ABI Type is unknown for target
            return null;
        }

        File folder = systemImage.getLocation();
        String imageFullPath = folder.getAbsolutePath();

        // make this path relative to the SDK location
        String sdkLocation = mSdkManager.getLocation();
        if (!imageFullPath.startsWith(sdkLocation)) {
            // this really really should not happen.
            assert false;
            throw new InvalidTargetPathException("Target location is not inside the SDK.");
        }

        if (folder.isDirectory()) {
            String[] list = folder.list(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return IMAGE_NAME_PATTERN.matcher(name).matches();
                }
            });

            if (list.length > 0) {
                // Remove the SDK root path, e.g. /sdk/dir1/dir2 => /dir1/dir2
                imageFullPath = imageFullPath.substring(sdkLocation.length());
                // The path is relative, so it must not start with a file separator
                if (imageFullPath.charAt(0) == File.separatorChar) {
                    imageFullPath = imageFullPath.substring(1);
                }
                // For compatibility with previous versions, we denote folders
                // by ending the path with file separator
                if (!imageFullPath.endsWith(File.separator)) {
                    imageFullPath += File.separator;
                }

                return imageFullPath;
            }
        }

        return null;
    }

    /**
     * Returns the path to the skin, as a relative path to the SDK.
     * @param skinName The name of the skin to find. Case-sensitive.
     * @param target The target where to find the skin.
     * @param log the log object to receive action logs. Cannot be null.
     */
    public String getSkinRelativePath(String skinName, IAndroidTarget target, ISdkLog log) {
        if (log == null) {
            throw new IllegalArgumentException("log cannot be null");
        }

        // first look to see if the skin is in the target
        File skin = getSkinPath(skinName, target);

        // skin really does not exist!
        if (skin.exists() == false) {
            log.error(null, "Skin '%1$s' does not exist.", skinName);
            return null;
        }

        // get the skin path
        String path = skin.getAbsolutePath();

        // make this path relative to the SDK location
        String sdkLocation = mSdkManager.getLocation();
        if (path.startsWith(sdkLocation) == false) {
            // this really really should not happen.
            log.error(null, "Target location is not inside the SDK.");
            assert false;
            return null;
        }

        path = path.substring(sdkLocation.length());
        if (path.charAt(0) == File.separatorChar) {
            path = path.substring(1);
        }
        return path;
    }

    /**
     * Returns the full absolute OS path to a skin specified by name for a given target.
     * @param skinName The name of the skin to find. Case-sensitive.
     * @param target The target where to find the skin.
     * @return a {@link File} that may or may not actually exist.
     */
    public File getSkinPath(String skinName, IAndroidTarget target) {
        String path = target.getPath(IAndroidTarget.SKINS);
        File skin = new File(path, skinName);

        if (skin.exists() == false && target.isPlatform() == false) {
            target = target.getParent();

            path = target.getPath(IAndroidTarget.SKINS);
            skin = new File(path, skinName);
        }

        return skin;
    }

    /**
     * Creates the ini file for an AVD.
     *
     * @param name of the AVD.
     * @param avdFolder path for the data folder of the AVD.
     * @param target of the AVD.
     * @param removePrevious True if an existing ini file should be removed.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     * @throws IOException if {@link File#getAbsolutePath()} fails.
     */
    private File createAvdIniFile(String name,
            File avdFolder,
            IAndroidTarget target,
            boolean removePrevious)
            throws AndroidLocationException, IOException {
        File iniFile = AvdInfo.getDefaultIniFile(this, name);

        if (removePrevious) {
            if (iniFile.isFile()) {
                iniFile.delete();
            } else if (iniFile.isDirectory()) {
                deleteContentOf(iniFile);
                iniFile.delete();
            }
        }

        HashMap<String, String> values = new HashMap<String, String>();
        values.put(AVD_INFO_PATH, avdFolder.getAbsolutePath());
        values.put(AVD_INFO_TARGET, target.hashString());
        writeIniFile(iniFile, values);

        return iniFile;
    }

    /**
     * Creates the ini file for an AVD.
     *
     * @param info of the AVD.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     * @throws IOException if {@link File#getAbsolutePath()} fails.
     */
    private File createAvdIniFile(AvdInfo info) throws AndroidLocationException, IOException {
        return createAvdIniFile(info.getName(),
                new File(info.getDataFolderPath()),
                info.getTarget(),
                false /*removePrevious*/);
    }

    /**
     * Actually deletes the files of an existing AVD.
     * <p/>
     * This also remove it from the manager's list, The caller does not need to
     * call {@link #removeAvd(AvdInfo)} afterwards.
     * <p/>
     * This method is designed to somehow work with an unavailable AVD, that is an AVD that
     * could not be loaded due to some error. That means this method still tries to remove
     * the AVD ini file or its folder if it can be found. An error will be output if any of
     * these operations fail.
     *
     * @param avdInfo the information on the AVD to delete
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the AVD was deleted with no error.
     */
    public boolean deleteAvd(AvdInfo avdInfo, ISdkLog log) {
        try {
            boolean error = false;

            File f = avdInfo.getIniFile();
            if (f != null && f.exists()) {
                log.printf("Deleting file %1$s\n", f.getCanonicalPath());
                if (!f.delete()) {
                    log.error(null, "Failed to delete %1$s\n", f.getCanonicalPath());
                    error = true;
                }
            }

            String path = avdInfo.getDataFolderPath();
            if (path != null) {
                f = new File(path);
                if (f.exists()) {
                    log.printf("Deleting folder %1$s\n", f.getCanonicalPath());
                    if (deleteContentOf(f) == false || f.delete() == false) {
                        log.error(null, "Failed to delete %1$s\n", f.getCanonicalPath());
                        error = true;
                    }
                }
            }

            removeAvd(avdInfo);

            if (error) {
                log.printf("\nAVD '%1$s' deleted with errors. See errors above.\n",
                        avdInfo.getName());
            } else {
                log.printf("\nAVD '%1$s' deleted.\n", avdInfo.getName());
                return true;
            }

        } catch (IOException e) {
            log.error(e, null);
        } catch (SecurityException e) {
            log.error(e, null);
        }
        return false;
    }

    /**
     * Moves and/or rename an existing AVD and its files.
     * This also change it in the manager's list.
     * <p/>
     * The caller should make sure the name or path given are valid, do not exist and are
     * actually different than current values.
     *
     * @param avdInfo the information on the AVD to move.
     * @param newName the new name of the AVD if non null.
     * @param paramFolderPath the new data folder if non null.
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the move succeeded or there was nothing to do.
     *         If false, this method will have had already output error in the log.
     */
    public boolean moveAvd(AvdInfo avdInfo, String newName, String paramFolderPath, ISdkLog log) {

        try {
            if (paramFolderPath != null) {
                File f = new File(avdInfo.getDataFolderPath());
                log.warning("Moving '%1$s' to '%2$s'.",
                        avdInfo.getDataFolderPath(),
                        paramFolderPath);
                if (!f.renameTo(new File(paramFolderPath))) {
                    log.error(null, "Failed to move '%1$s' to '%2$s'.",
                            avdInfo.getDataFolderPath(), paramFolderPath);
                    return false;
                }

                // update AVD info
                AvdInfo info = new AvdInfo(
                        avdInfo.getName(),
                        avdInfo.getIniFile(),
                        paramFolderPath,
                        avdInfo.getTargetHash(),
                        avdInfo.getTarget(),
                        avdInfo.getAbiType(),
                        avdInfo.getProperties());
                replaceAvd(avdInfo, info);

                // update the ini file
                createAvdIniFile(info);
            }

            if (newName != null) {
                File oldIniFile = avdInfo.getIniFile();
                File newIniFile = AvdInfo.getDefaultIniFile(this, newName);

                log.warning("Moving '%1$s' to '%2$s'.", oldIniFile.getPath(), newIniFile.getPath());
                if (!oldIniFile.renameTo(newIniFile)) {
                    log.error(null, "Failed to move '%1$s' to '%2$s'.",
                            oldIniFile.getPath(), newIniFile.getPath());
                    return false;
                }

                // update AVD info
                AvdInfo info = new AvdInfo(
                        newName,
                        avdInfo.getIniFile(),
                        avdInfo.getDataFolderPath(),
                        avdInfo.getTargetHash(),
                        avdInfo.getTarget(),
                        avdInfo.getAbiType(),
                        avdInfo.getProperties());
                replaceAvd(avdInfo, info);
            }

            log.printf("AVD '%1$s' moved.\n", avdInfo.getName());

        } catch (AndroidLocationException e) {
            log.error(e, null);
        } catch (IOException e) {
            log.error(e, null);
        }

        // nothing to do or succeeded
        return true;
    }

    /**
     * Helper method to recursively delete a folder's content (but not the folder itself).
     *
     * @throws SecurityException like {@link File#delete()} does if file/folder is not writable.
     */
    private boolean deleteContentOf(File folder) throws SecurityException {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    if (deleteContentOf(f) == false) {
                        return false;
                    }
                }
                if (f.delete() == false) {
                    return false;
                }

            }
        }

        return true;
    }

    /**
     * Returns a list of files that are potential AVD ini files.
     * <p/>
     * This lists the $HOME/.android/avd/<name>.ini files.
     * Such files are properties file than then indicate where the AVD folder is located.
     * <p/>
     * Note: the method is to be considered private. It is made protected so that
     * unit tests can easily override the AVD root.
     *
     * @return A new {@link File} array or null. The array might be empty.
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    private File[] buildAvdFilesList() throws AndroidLocationException {
        File folder = new File(getBaseAvdFolder());

        // ensure folder validity.
        if (folder.isFile()) {
            throw new AndroidLocationException(
                    String.format("%1$s is not a valid folder.", folder.getAbsolutePath()));
        } else if (folder.exists() == false) {
            // folder is not there, we create it and return
            folder.mkdirs();
            return null;
        }

        File[] avds = folder.listFiles(new FilenameFilter() {
            public boolean accept(File parent, String name) {
                if (INI_NAME_PATTERN.matcher(name).matches()) {
                    // check it's a file and not a folder
                    boolean isFile = new File(parent, name).isFile();
                    return isFile;
                }

                return false;
            }
        });

        return avds;
    }

    /**
     * Computes the internal list of available AVDs
     * @param allList the list to contain all the AVDs
     * @param log the log object to receive action logs. Cannot be null.
     *
     * @throws AndroidLocationException if there's a problem getting android root directory.
     */
    private void buildAvdList(ArrayList<AvdInfo> allList, ISdkLog log)
            throws AndroidLocationException {
        File[] avds = buildAvdFilesList();
        if (avds != null) {
            for (File avd : avds) {
                AvdInfo info = parseAvdInfo(avd, log);
                if (info != null) {
                    allList.add(info);
                }
            }
        }
    }

    /**
     * Parses an AVD .ini file to create an {@link AvdInfo}.
     *
     * @param iniPath The path to the AVD .ini file
     * @param log the log object to receive action logs. Cannot be null.
     * @return A new {@link AvdInfo} with an {@link AvdStatus} indicating whether this AVD is
     *         valid or not.
     */
    private AvdInfo parseAvdInfo(File iniPath, ISdkLog log) {
        Map<String, String> map = ProjectProperties.parsePropertyFile(
                new FileWrapper(iniPath),
                log);

        String avdPath = map.get(AVD_INFO_PATH);
        String targetHash = map.get(AVD_INFO_TARGET);

        IAndroidTarget target = null;
        FileWrapper configIniFile = null;
        Map<String, String> properties = null;

        if (targetHash != null) {
            target = mSdkManager.getTargetFromHashString(targetHash);
        }

        // load the AVD properties.
        if (avdPath != null) {
            configIniFile = new FileWrapper(avdPath, CONFIG_INI);
        }

        if (configIniFile != null) {
            if (!configIniFile.isFile()) {
                log.warning("Missing file '%1$s'.",  configIniFile.getPath());
            } else {
                properties = ProjectProperties.parsePropertyFile(configIniFile, log);
            }
        }

        // get name
        String name = iniPath.getName();
        Matcher matcher = INI_NAME_PATTERN.matcher(iniPath.getName());
        if (matcher.matches()) {
            name = matcher.group(1);
        }

        // get abi type
        String abiType = properties == null ? null : properties.get(AVD_INI_ABI_TYPE);
        // for the avds created previously without enhancement, i.e. They are created based
        // on previous API Levels. They are supposed to have ARM processor type
        if (abiType == null) {
            abiType = SdkConstants.ABI_ARMEABI;
        }

        // check the image.sysdir are valid
        boolean validImageSysdir = true;
        if (properties != null) {
            String imageSysDir = properties.get(AVD_INI_IMAGES_1);
            if (imageSysDir != null) {
                File f = new File(mSdkManager.getLocation() + File.separator + imageSysDir);
                if (f.isDirectory() == false) {
                    validImageSysdir = false;
                } else {
                    imageSysDir = properties.get(AVD_INI_IMAGES_2);
                    if (imageSysDir != null) {
                        f = new File(mSdkManager.getLocation() + File.separator + imageSysDir);
                        if (f.isDirectory() == false) {
                            validImageSysdir = false;
                        }
                    }
                }
            }
        }

        // TODO: What about missing sdcard, skins, etc?

        AvdStatus status;

        if (avdPath == null) {
            status = AvdStatus.ERROR_PATH;
        } else if (configIniFile == null) {
            status = AvdStatus.ERROR_CONFIG;
        } else if (targetHash == null) {
            status = AvdStatus.ERROR_TARGET_HASH;
        } else if (target == null) {
            status = AvdStatus.ERROR_TARGET;
        } else if (properties == null) {
            status = AvdStatus.ERROR_PROPERTIES;
        } else if (validImageSysdir == false) {
            status = AvdStatus.ERROR_IMAGE_DIR;
        } else {
            status = AvdStatus.OK;
        }

        AvdInfo info = new AvdInfo(
                name,
                iniPath,
                avdPath,
                targetHash,
                target,
                abiType,
                properties,
                status);

        return info;
    }

    /**
     * Writes a .ini file from a set of properties, using UTF-8 encoding.
     *
     * @param iniFile The file to generate.
     * @param values THe properties to place in the ini file.
     * @throws IOException if {@link FileWriter} fails to open, write or close the file.
     */
    private static void writeIniFile(File iniFile, Map<String, String> values)
            throws IOException {
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(iniFile),
                SdkConstants.INI_CHARSET);

        for (Entry<String, String> entry : values.entrySet()) {
            writer.write(String.format("%1$s=%2$s\n", entry.getKey(), entry.getValue()));
        }
        writer.close();
    }

    /**
     * Invokes the tool to create a new SD card image file.
     *
     * @param toolLocation The path to the mksdcard tool.
     * @param size The size of the new SD Card, compatible with {@link #SDCARD_SIZE_PATTERN}.
     * @param location The path of the new sdcard image file to generate.
     * @param log the log object to receive action logs. Cannot be null.
     * @return True if the sdcard could be created.
     */
    private boolean createSdCard(String toolLocation, String size, String location, ISdkLog log) {
        try {
            String[] command = new String[3];
            command[0] = toolLocation;
            command[1] = size;
            command[2] = location;
            Process process = Runtime.getRuntime().exec(command);

            ArrayList<String> errorOutput = new ArrayList<String>();
            ArrayList<String> stdOutput = new ArrayList<String>();
            int status = grabProcessOutput(process, errorOutput, stdOutput,
                    true /* waitForReaders */);

            if (status == 0) {
                return true;
            } else {
                for (String error : errorOutput) {
                    log.error(null, error);
                }
            }

        } catch (InterruptedException e) {
            // pass, print error below
        } catch (IOException e) {
            // pass, print error below
        }

        log.error(null, "Failed to create the SD card.");
        return false;
    }

    /**
     * Gets the stderr/stdout outputs of a process and returns when the process is done.
     * Both <b>must</b> be read or the process will block on windows.
     * @param process The process to get the ouput from
     * @param errorOutput The array to store the stderr output. cannot be null.
     * @param stdOutput The array to store the stdout output. cannot be null.
     * @param waitforReaders if true, this will wait for the reader threads.
     * @return the process return code.
     * @throws InterruptedException
     */
    private int grabProcessOutput(final Process process, final ArrayList<String> errorOutput,
            final ArrayList<String> stdOutput, boolean waitforReaders)
            throws InterruptedException {
        assert errorOutput != null;
        assert stdOutput != null;
        // read the lines as they come. if null is returned, it's
        // because the process finished
        Thread t1 = new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                // create a buffer to read the stderr output
                InputStreamReader is = new InputStreamReader(process.getErrorStream());
                BufferedReader errReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = errReader.readLine();
                        if (line != null) {
                            errorOutput.add(line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        };

        Thread t2 = new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                InputStreamReader is = new InputStreamReader(process.getInputStream());
                BufferedReader outReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = outReader.readLine();
                        if (line != null) {
                            stdOutput.add(line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        };

        t1.start();
        t2.start();

        // it looks like on windows process#waitFor() can return
        // before the thread have filled the arrays, so we wait for both threads and the
        // process itself.
        if (waitforReaders) {
            try {
                t1.join();
            } catch (InterruptedException e) {
                // nothing to do here
            }
            try {
                t2.join();
            } catch (InterruptedException e) {
                // nothing to do here
            }
        }

        // get the return code from the process
        return process.waitFor();
    }

    /**
     * Removes an {@link AvdInfo} from the internal list.
     *
     * @param avdInfo The {@link AvdInfo} to remove.
     * @return true if this {@link AvdInfo} was present and has been removed.
     */
    public boolean removeAvd(AvdInfo avdInfo) {
        synchronized (mAllAvdList) {
            if (mAllAvdList.remove(avdInfo)) {
                mValidAvdList = mBrokenAvdList = null;
                return true;
            }
        }

        return false;
    }

    /**
     * Updates an AVD with new path to the system image folders.
     * @param name the name of the AVD to update.
     * @param log the log object to receive action logs. Cannot be null.
     * @throws IOException
     */
    public void updateAvd(String name, ISdkLog log) throws IOException {
        // find the AVD to update. It should be be in the broken list.
        AvdInfo avd = null;
        synchronized (mAllAvdList) {
            for (AvdInfo info : mAllAvdList) {
                if (info.getName().equals(name)) {
                    avd = info;
                    break;
                }
            }
        }

        if (avd == null) {
            // not in the broken list, just return.
            log.error(null, "There is no Android Virtual Device named '%s'.", name);
            return;
        }

        updateAvd(avd, log);
    }


    /**
     * Updates an AVD with new path to the system image folders.
     * @param avd the AVD to update.
     * @param log the log object to receive action logs. Cannot be null.
     * @throws IOException
     */
    public void updateAvd(AvdInfo avd, ISdkLog log) throws IOException {
        // get the properties. This is a unmodifiable Map.
        Map<String, String> oldProperties = avd.getProperties();

        // create a new map
        Map<String, String> properties = new HashMap<String, String>();
        if (oldProperties != null) {
            properties.putAll(oldProperties);
        }

        AvdStatus status;

        // create the path to the new system images.
        if (setImagePathProperties(avd.getTarget(), avd.getAbiType(), properties, log)) {
            if (properties.containsKey(AVD_INI_IMAGES_1)) {
                log.printf("Updated '%1$s' with value '%2$s'\n", AVD_INI_IMAGES_1,
                        properties.get(AVD_INI_IMAGES_1));
            }

            if (properties.containsKey(AVD_INI_IMAGES_2)) {
                log.printf("Updated '%1$s' with value '%2$s'\n", AVD_INI_IMAGES_2,
                        properties.get(AVD_INI_IMAGES_2));
            }

            status = AvdStatus.OK;
        } else {
            log.error(null, "Unable to find non empty system images folders for %1$s",
                    avd.getName());
            //FIXME: display paths to empty image folders?
            status = AvdStatus.ERROR_IMAGE_DIR;
        }

        // now write the config file
        File configIniFile = new File(avd.getDataFolderPath(), CONFIG_INI);
        writeIniFile(configIniFile, properties);

        // finally create a new AvdInfo for this unbroken avd and add it to the list.
        // instead of creating the AvdInfo object directly we reparse it, to detect other possible
        // errors
        // FIXME: We may want to create this AvdInfo by reparsing the AVD instead. This could detect other errors.
        AvdInfo newAvd = new AvdInfo(
                avd.getName(),
                avd.getIniFile(),
                avd.getDataFolderPath(),
                avd.getTargetHash(),
                avd.getTarget(),
                avd.getAbiType(),
                properties,
                status);

        replaceAvd(avd, newAvd);
    }

    /**
     * Sets the paths to the system images in a properties map.
     *
     * @param target the target in which to find the system images.
     * @param abiType the abi type of the avd to find
     *        the architecture-dependent system images.
     * @param properties the properties in which to set the paths.
     * @param log the log object to receive action logs. Cannot be null.
     * @return true if success, false if some path are missing.
     */
    private boolean setImagePathProperties(IAndroidTarget target,
            String abiType,
            Map<String, String> properties,
            ISdkLog log) {
        properties.remove(AVD_INI_IMAGES_1);
        properties.remove(AVD_INI_IMAGES_2);

        try {
            String property = AVD_INI_IMAGES_1;

            // First the image folders of the target itself
            String imagePath = getImageRelativePath(target, abiType);
            if (imagePath != null) {
                properties.put(property, imagePath);
                property = AVD_INI_IMAGES_2;
            }

            // If the target is an add-on we need to add the Platform image as a backup.
            IAndroidTarget parent = target.getParent();
            if (parent != null) {
                imagePath = getImageRelativePath(parent, abiType);
                if (imagePath != null) {
                    properties.put(property, imagePath);
                }
            }

            // we need at least one path!
            return properties.containsKey(AVD_INI_IMAGES_1);
        } catch (InvalidTargetPathException e) {
            log.error(e, e.getMessage());
        }

        return false;
    }

    /**
     * Replaces an old {@link AvdInfo} with a new one in the lists storing them.
     * @param oldAvd the {@link AvdInfo} to remove.
     * @param newAvd the {@link AvdInfo} to add.
     */
    private void replaceAvd(AvdInfo oldAvd, AvdInfo newAvd) {
        synchronized (mAllAvdList) {
            mAllAvdList.remove(oldAvd);
            mAllAvdList.add(newAvd);
            mValidAvdList = mBrokenAvdList = null;
        }
    }
}
