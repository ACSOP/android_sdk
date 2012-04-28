/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.sdkmanager.internal.repository;


import com.android.sdklib.SdkConstants;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.sdklib.repository.SdkRepoConstants;
import com.android.sdkmanager.Main;
import com.android.sdkuilib.internal.repository.UpdaterPage;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AboutPage extends UpdaterPage {

    private Label mLabel;

    /**
     * Create the composite.
     * @param parent The parent of the composite.
     */
    public AboutPage(Composite parent, int swtStyle) {
        super(parent, swtStyle);

        createContents(this);

        postCreate();  //$hide$
    }

    @Override
    public String getPageTitle() {
        return "About";
    }

    private void createContents(Composite parent) {
        parent.setLayout(new GridLayout(2, false));

        Label logo = new Label(parent, SWT.NONE);
        InputStream imageStream = this.getClass().getResourceAsStream("logo.png"); //$NON-NLS-1$

        if (imageStream != null) {
            Image img = new Image(parent.getShell().getDisplay(), imageStream);
            logo.setImage(img);
        }

        mLabel = new Label(parent, SWT.NONE);
        mLabel.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        mLabel.setText(String.format(
                "Android SDK Updater.\n" +
                "Revision %1$s\n" +
                "Add-on XML Schema #%2$d\n" +
                "Repository XML Schema #%3$d\n" +
                "Copyright (C) 2009-2011 The Android Open Source Project.",
                getRevision(),
                SdkAddonConstants.NS_LATEST_VERSION,
                SdkRepoConstants.NS_LATEST_VERSION));
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    // -- Start of internal part ----------
    // Hide everything down-below from SWT designer
    //$hide>>$

    /**
     * Called by the constructor right after {@link #createContents(Composite)}.
     */
    private void postCreate() {
    }

    // End of hiding from SWT Designer
    //$hide<<$

    private String getRevision() {
        Properties p = new Properties();
        try{
            String toolsdir = System.getProperty(Main.TOOLSDIR);
            File sourceProp;
            if (toolsdir == null || toolsdir.length() == 0) {
                sourceProp = new File(SdkConstants.FN_SOURCE_PROP);
            } else {
                sourceProp = new File(toolsdir, SdkConstants.FN_SOURCE_PROP);
            }
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

            String revision = p.getProperty(PkgProps.PKG_REVISION);
            if (revision != null) {
                return revision;
            }
        } catch (FileNotFoundException e) {
            // couldn't find the file? don't ping.
        } catch (IOException e) {
            // couldn't find the file? don't ping.
        }

        return "?";
    }
}
