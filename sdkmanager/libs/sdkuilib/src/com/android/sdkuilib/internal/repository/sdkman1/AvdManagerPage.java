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

package com.android.sdkuilib.internal.repository.sdkman1;

import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdkuilib.internal.repository.UpdaterData;
import com.android.sdkuilib.internal.repository.UpdaterPage;
import com.android.sdkuilib.internal.widgets.AvdSelector;
import com.android.sdkuilib.internal.widgets.AvdSelector.DisplayMode;
import com.android.sdkuilib.repository.ISdkChangeListener;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class AvdManagerPage extends UpdaterPage implements ISdkChangeListener {

    private AvdSelector mAvdSelector;

    private final UpdaterData mUpdaterData;

    /**
     * Create the composite.
     * @param parent The parent of the composite.
     * @param updaterData An instance of {@link UpdaterData}.
     */
    public AvdManagerPage(Composite parent, int swtStyle, UpdaterData updaterData) {
        super(parent, swtStyle);

        mUpdaterData = updaterData;
        mUpdaterData.addListeners(this);

        createContents(this);
        postCreate();  //$hide$
    }

    private void createContents(Composite parent) {
        parent.setLayout(new GridLayout(1, false));

        Label label = new Label(parent, SWT.NONE);
        label.setLayoutData(new GridData());

        try {
            if (mUpdaterData != null && mUpdaterData.getAvdManager() != null) {
                label.setText(String.format(
                        "List of existing Android Virtual Devices located at %s",
                        mUpdaterData.getAvdManager().getBaseAvdFolder()));
            } else {
                label.setText("Error: cannot find the AVD folder location.\r\n Please set the 'ANDROID_SDK_HOME' env variable.");
            }
        } catch (AndroidLocationException e) {
            label.setText(e.getMessage());
        }

        mAvdSelector = new AvdSelector(parent,
                mUpdaterData.getOsSdkRoot(),
                mUpdaterData.getAvdManager(),
                DisplayMode.MANAGER,
                mUpdaterData.getSdkLog());
        mAvdSelector.setSettingsController(mUpdaterData.getSettingsController());
    }

    @Override
    public void dispose() {
        mUpdaterData.removeListener(this);
        super.dispose();
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
        // nothing to be done for now.
    }

    // --- Implementation of ISdkChangeListener ---

    public void onSdkLoaded() {
        onSdkReload();
    }

    public void onSdkReload() {
        mAvdSelector.refresh(false /*reload*/);
    }

    public void preInstallHook() {
        // nothing to be done for now.
    }

    public void postInstallHook() {
        // nothing to be done for now.
    }

    // End of hiding from SWT Designer
    //$hide<<$
}
