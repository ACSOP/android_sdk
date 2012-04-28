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

package com.android.ant;

import com.android.sdklib.SdkConstants;
import com.android.sdklib.internal.project.ProjectProperties;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

final class TaskHelper {

    static File getSdkLocation(Project antProject) {
        // get the SDK location
        String sdkOsPath = antProject.getProperty(ProjectProperties.PROPERTY_SDK);

        // check if it's valid and exists
        if (sdkOsPath == null || sdkOsPath.length() == 0) {
            throw new BuildException("SDK Location is not set.");
        }

        File sdk = new File(sdkOsPath);
        if (sdk.isDirectory() == false) {
            throw new BuildException(String.format("SDK Location '%s' is not valid.", sdkOsPath));
        }

        return sdk;
    }

    /**
     * Returns the revision of the tools for a given SDK.
     * @param sdkFile the {@link File} for the root folder of the SDK
     * @return the tools revision or -1 if not found.
     */
    static int getToolsRevision(File sdkFile) {
        Properties p = new Properties();
        try{
            // tools folder must exist, or this custom task wouldn't run!
            File toolsFolder= new File(sdkFile, SdkConstants.FD_TOOLS);
            File sourceProp = new File(toolsFolder, SdkConstants.FN_SOURCE_PROP);

            FileInputStream fis = null;
            try {
                fis = new FileInputStream(sourceProp);
                p.load(fis);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException ignore) {
                    }
                }
            }

            String value = p.getProperty("Pkg.Revision"); //$NON-NLS-1$
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (FileNotFoundException e) {
            // couldn't find the file? return -1 below.
        } catch (IOException e) {
            // couldn't find the file? return -1 below.
        }

        return -1;
    }

    static String checkSinglePath(String attribute, Path path) {
        String[] paths = path.list();
        if (paths.length != 1) {
            throw new BuildException(String.format(
                    "Value for '%1$s' is not valid. It must resolve to a single path", attribute));
        }

        return paths[0];
    }
}
