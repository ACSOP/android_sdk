/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.sdklib.internal.repository;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.repository.Archive.Arch;
import com.android.sdklib.internal.repository.Archive.Os;

import org.w3c.dom.Node;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Represents a platform-tool XML node in an SDK repository.
 */
public class PlatformToolPackage extends Package {

    /** The value returned by {@link PlatformToolPackage#installId()}. */
    public static final String INSTALL_ID = "platform-tools";                       //$NON-NLS-1$

    /**
     * Creates a new platform-tool package from the attributes and elements of the given XML node.
     * This constructor should throw an exception if the package cannot be created.
     *
     * @param source The {@link SdkSource} where this is loaded from.
     * @param packageNode The XML element being parsed.
     * @param nsUri The namespace URI of the originating XML document, to be able to deal with
     *          parameters that vary according to the originating XML schema.
     * @param licenses The licenses loaded from the XML originating document.
     */
    PlatformToolPackage(SdkSource source, Node packageNode,
            String nsUri, Map<String,String> licenses) {
        super(source, packageNode, nsUri, licenses);
    }

    /**
     * Manually create a new package with one archive and the given attributes or properties.
     * This is used to create packages from local directories in which case there must be
     * one archive which URL is the actual target location.
     * <p/>
     * By design, this creates a package with one and only one archive.
     */
    static Package create(
            SdkSource source,
            Properties props,
            int revision,
            String license,
            String description,
            String descUrl,
            Os archiveOs,
            Arch archiveArch,
            String archiveOsPath) {

        PlatformToolPackage ptp = new PlatformToolPackage(source, props, revision, license,
                description, descUrl, archiveOs, archiveArch, archiveOsPath);

        File platformToolsFolder = new File(archiveOsPath);
        String error = null;
        if (!platformToolsFolder.isDirectory()) {
            error = "platform-tools folder is missing";
        } else {
            File[] files = platformToolsFolder.listFiles();
            if (files == null || files.length == 0) {
                error = "platform-tools folder is empty";
            } else {
                Set<String> names = new HashSet<String>();
                for (File file : files) {
                    names.add(file.getName());
                }
                for (String name : new String[] { SdkConstants.FN_ADB,
                                                  SdkConstants.FN_AAPT,
                                                  SdkConstants.FN_AIDL,
                                                  SdkConstants.FN_DX } ) {
                    if (!names.contains(name)) {
                        if (error == null) {
                            error = "platform-tools folder is missing ";
                        } else {
                            error += ", ";
                        }
                        error += name;
                    }
                }
            }
        }

        if (error != null) {
            String shortDesc = ptp.getShortDescription() + " [*]";  //$NON-NLS-1$

            String longDesc = String.format(
                    "Broken Platform-Tools Package: %1$s\n" +
                    "[*] Package cannot be used due to error: %2$s",
                    description,
                    error);

            BrokenPackage ba = new BrokenPackage(props, shortDesc, longDesc,
                    IMinApiLevelDependency.MIN_API_LEVEL_NOT_SPECIFIED,
                    IExactApiLevelDependency.API_LEVEL_INVALID,
                    archiveOsPath);
            return ba;
        }


        return ptp;
    }

    @VisibleForTesting(visibility=Visibility.PRIVATE)
    protected PlatformToolPackage(
                SdkSource source,
                Properties props,
                int revision,
                String license,
                String description,
                String descUrl,
                Os archiveOs,
                Arch archiveArch,
                String archiveOsPath) {
        super(source,
                props,
                revision,
                license,
                description,
                descUrl,
                archiveOs,
                archiveArch,
                archiveOsPath);
    }

    /**
     * Returns a string identifier to install this package from the command line.
     * For platform-tools, we use "platform-tools" since this package type is unique.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String installId() {
        return INSTALL_ID;
    }

    /**
     * Returns a description of this package that is suitable for a list display.
     * <p/>
     * {@inheritDoc}
     */
    @Override
    public String getListDescription() {
        return String.format("Android SDK Platform-tools%1$s",
                isObsolete() ? " (Obsolete)" : "");
    }

    /**
     * Returns a short description for an {@link IDescription}.
     */
    @Override
    public String getShortDescription() {
        return String.format("Android SDK Platform-tools, revision %1$d%2$s",
                getRevision(),
                isObsolete() ? " (Obsolete)" : "");
    }

    /** Returns a long description for an {@link IDescription}. */
    @Override
    public String getLongDescription() {
        String s = getDescription();
        if (s == null || s.length() == 0) {
            s = getShortDescription();
        }

        if (s.indexOf("revision") == -1) {
            s += String.format("\nRevision %1$d%2$s",
                    getRevision(),
                    isObsolete() ? " (Obsolete)" : "");
        }

        return s;
    }

    /**
     * Computes a potential installation folder if an archive of this package were
     * to be installed right away in the given SDK root.
     * <p/>
     * A "tool" package should always be located in SDK/tools.
     *
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param sdkManager An existing SDK manager to list current platforms and addons.
     * @return A new {@link File} corresponding to the directory to use to install this package.
     */
    @Override
    public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {
        return new File(osSdkRoot, SdkConstants.FD_PLATFORM_TOOLS);
    }

    @Override
    public boolean sameItemAs(Package pkg) {
        // only one platform-tool package so any platform-tool package is the same item.
        return pkg instanceof PlatformToolPackage;
    }

    /**
     * Hook called right before an archive is installed.
     * This is used here to stop ADB before trying to replace the platform-tool package.
     *
     * @param archive The archive that will be installed
     * @param monitor The {@link ITaskMonitor} to display errors.
     * @param osSdkRoot The OS path of the SDK root folder.
     * @param installFolder The folder where the archive will be installed. Note that this
     *                      is <em>not</em> the folder where the archive was temporary
     *                      unzipped. The installFolder, if it exists, contains the old
     *                      archive that will soon be replaced by the new one.
     * @return True if installing this archive shall continue, false if it should be skipped.
     */
    @Override
    public boolean preInstallHook(Archive archive, ITaskMonitor monitor,
            String osSdkRoot, File installFolder) {
        AdbWrapper aw = new AdbWrapper(osSdkRoot, monitor);
        aw.stopAdb();
        return super.preInstallHook(archive, monitor, osSdkRoot, installFolder);
    }

}
