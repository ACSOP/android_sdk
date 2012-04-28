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

package com.android.ide.eclipse.adt.internal.preferences;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Preference page for build options.
 *
 */
public class BuildPreferencePage extends FieldEditorPreferencePage implements
        IWorkbenchPreferencePage {

    public BuildPreferencePage() {
        super(GRID);
        setPreferenceStore(AdtPlugin.getDefault().getPreferenceStore());
        setDescription(Messages.BuildPreferencePage_Title);
    }

    @Override
    protected void createFieldEditors() {
        addField(new BooleanFieldEditor(AdtPrefs.PREFS_BUILD_RES_AUTO_REFRESH,
                Messages.BuildPreferencePage_Auto_Refresh_Resources_on_Build,
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(AdtPrefs.PREFS_BUILD_FORCE_ERROR_ON_NATIVELIB_IN_JAR,
                "Force error when external jars contain native libraries",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(AdtPrefs.PREFS_BUILD_SKIP_POST_COMPILE_ON_FILE_SAVE,
                "Skip packaging and dexing until export or launch. (Speeds up automatic builds on file save)",
                getFieldEditorParent()));

        RadioGroupFieldEditor rgfe = new RadioGroupFieldEditor(
                AdtPrefs.PREFS_BUILD_VERBOSITY,
                Messages.BuildPreferencePage_Build_Output, 1, new String[][] {
                    { Messages.BuildPreferencePage_Silent, BuildVerbosity.ALWAYS.name() },
                    { Messages.BuildPreferencePage_Normal, BuildVerbosity.NORMAL.name() },
                    { Messages.BuildPreferencePage_Verbose, BuildVerbosity.VERBOSE.name() }
                    },
                getFieldEditorParent(), true);
        addField(rgfe);

        addField(new ReadOnlyFieldEditor(AdtPrefs.PREFS_DEFAULT_DEBUG_KEYSTORE,
                Messages.BuildPreferencePage_Default_KeyStore, getFieldEditorParent()));

        addField(new KeystoreFieldEditor(AdtPrefs.PREFS_CUSTOM_DEBUG_KEYSTORE,
                Messages.BuildPreferencePage_Custom_Keystore, getFieldEditorParent()));

    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench) {
    }

    /**
     * A read-only string field editor.
     */
    private static class ReadOnlyFieldEditor extends StringFieldEditor {

        public ReadOnlyFieldEditor(String name, String labelText, Composite parent) {
            super(name, labelText, parent);
        }

        @Override
        protected void createControl(Composite parent) {
            super.createControl(parent);

            Text control = getTextControl();
            control.setEditable(false);
        }
    }

    /**
     * Custom {@link FileFieldEditor} that checks that the keystore is valid.
     */
    private static class KeystoreFieldEditor extends FileFieldEditor {
        public KeystoreFieldEditor(String name, String label, Composite parent) {
            super(name, label, parent);
            setValidateStrategy(VALIDATE_ON_KEY_STROKE);
        }

        @Override
        protected boolean checkState() {
            String fileName = getTextControl().getText();
            fileName = fileName.trim();

            // empty values are considered ok.
            if (fileName.length() > 0) {
                File file = new File(fileName);
                if (file.isFile()) {
                    // attempt to load the debug key.
                    try {
                        DebugKeyProvider provider = new DebugKeyProvider(fileName,
                                null /* storeType */, null /* key gen output */);
                        PrivateKey key = provider.getDebugKey();
                        X509Certificate certificate = (X509Certificate)provider.getCertificate();

                        if (key == null || certificate == null) {
                            showErrorMessage("Unable to find debug key in keystore!");
                            return false;
                        }

                        Date today = new Date();
                        if (certificate.getNotAfter().compareTo(today) < 0) {
                            showErrorMessage("Certificate is expired!");
                            return false;
                        }

                        if (certificate.getNotBefore().compareTo(today) > 0) {
                            showErrorMessage("Certificate validity is in the future!");
                            return false;
                        }

                        // we're good!
                        clearErrorMessage();
                        return true;
                    } catch (GeneralSecurityException e) {
                        handleException(e);
                        return false;
                    } catch (IOException e) {
                        handleException(e);
                        return false;
                    } catch (KeytoolException e) {
                        handleException(e);
                        return false;
                    } catch (AndroidLocationException e) {
                        handleException(e);
                        return false;
                    }


                } else {
                    // file does not exist.
                    showErrorMessage("Not a valid keystore path.");
                    return false;  // Apply/OK must be disabled
                }
            }

            clearErrorMessage();
            return true;
        }

        @Override
        public Text getTextControl(Composite parent) {
            setValidateStrategy(VALIDATE_ON_KEY_STROKE);
            return super.getTextControl(parent);
        }

        /**
         * Set the error message from a {@link Throwable}. If the exception has no message, try
         * to get the message from the cause.
         * @param t the Throwable.
         */
        private void handleException(Throwable t) {
            String msg = t.getMessage();
            if (msg == null) {
                Throwable cause = t.getCause();
                if (cause != null) {
                    handleException(cause);
                } else {
                    setErrorMessage("Uknown error when getting the debug key!");
                }

                return;
            }

            // valid text, display it.
            showErrorMessage(msg);
        }
    }
}
