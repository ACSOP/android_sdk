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

package com.android.ide.eclipse.adt;

import com.android.ddmuilib.console.DdmConsole;
import com.android.ddmuilib.console.IDdmConsole;
import com.android.ide.common.log.ILogger;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceFolder;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.internal.VersionCheck;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.animator.AnimationEditor;
import com.android.ide.eclipse.adt.internal.editors.color.ColorEditor;
import com.android.ide.eclipse.adt.internal.editors.drawable.DrawableEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder;
import com.android.ide.eclipse.adt.internal.editors.menu.MenuEditor;
import com.android.ide.eclipse.adt.internal.editors.resources.ResourcesEditor;
import com.android.ide.eclipse.adt.internal.editors.xml.XmlEditor;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.AndroidClasspathContainerInitializer;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.Sdk.ITargetChangeListener;
import com.android.ide.eclipse.adt.internal.ui.EclipseUiHelper;
import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.io.StreamException;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.browser.IWebBrowser;
import org.eclipse.ui.browser.IWorkbenchBrowserSupport;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The activator class controls the plug-in life cycle
 */
public class AdtPlugin extends AbstractUIPlugin implements ILogger {
    /**
     * Temporary logging code to help track down
     * http://code.google.com/p/android/issues/detail?id=15003
     *
     * Deactivated right now.
     * TODO remove this and associated logging code once we're done with issue 15003.
     */
    public static final boolean DEBUG_XML_FILE_INIT = false;

    /** The plug-in ID */
    public static final String PLUGIN_ID = "com.android.ide.eclipse.adt"; //$NON-NLS-1$

    /** singleton instance */
    private static AdtPlugin sPlugin;

    private static Image sAndroidLogo;
    private static ImageDescriptor sAndroidLogoDesc;

    /** The global android console */
    private MessageConsole mAndroidConsole;

    /** Stream to write in the android console */
    private MessageConsoleStream mAndroidConsoleStream;

    /** Stream to write error messages to the android console */
    private MessageConsoleStream mAndroidConsoleErrorStream;

    /** Color used in the error console */
    private Color mRed;

    /** Load status of the SDK. Any access MUST be in a synchronized(mPostLoadProjects) block */
    private LoadStatus mSdkIsLoaded = LoadStatus.LOADING;
    /** Project to update once the SDK is loaded.
     * Any access MUST be in a synchronized(mPostLoadProjectsToResolve) block */
    private final ArrayList<IJavaProject> mPostLoadProjectsToResolve =
            new ArrayList<IJavaProject>();
    /** Project to check validity of cache vs actual once the SDK is loaded.
     * Any access MUST be in a synchronized(mPostLoadProjectsToResolve) block */
    private final ArrayList<IJavaProject> mPostLoadProjectsToCheck = new ArrayList<IJavaProject>();

    private GlobalProjectMonitor mResourceMonitor;
    private ArrayList<ITargetChangeListener> mTargetChangeListeners =
            new ArrayList<ITargetChangeListener>();

    protected boolean mSdkIsLoading;

    /**
     * An error handler for checkSdkLocationAndId() that will handle the generated error
     * or warning message. Each method must return a boolean that will in turn be returned by
     * checkSdkLocationAndId.
     */
    public static abstract class CheckSdkErrorHandler {
        /** Handle an error message during sdk location check. Returns whatever
         * checkSdkLocationAndId() should returns.
         */
        public abstract boolean handleError(String message);

        /** Handle a warning message during sdk location check. Returns whatever
         * checkSdkLocationAndId() should returns.
         */
        public abstract boolean handleWarning(String message);
    }

    /**
     * The constructor
     */
    public AdtPlugin() {
        sPlugin = this;
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
     */
    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);

        Display display = getDisplay();

        // set the default android console.
        mAndroidConsole = new MessageConsole("Android", null); //$NON-NLS-1$
        ConsolePlugin.getDefault().getConsoleManager().addConsoles(
                new IConsole[] { mAndroidConsole });

        // get the stream to write in the android console.
        mAndroidConsoleStream = mAndroidConsole.newMessageStream();
        mAndroidConsoleErrorStream = mAndroidConsole.newMessageStream();
        mRed = new Color(display, 0xFF, 0x00, 0x00);

        // because this can be run, in some cases, by a non ui thread, and because
        // changing the console properties update the ui, we need to make this change
        // in the ui thread.
        display.asyncExec(new Runnable() {
            public void run() {
                mAndroidConsoleErrorStream.setColor(mRed);
            }
        });

        // set up the ddms console to use this objects
        DdmConsole.setConsole(new IDdmConsole() {
            public void printErrorToConsole(String message) {
                AdtPlugin.printErrorToConsole((String)null, message);
            }
            public void printErrorToConsole(String[] messages) {
                AdtPlugin.printErrorToConsole((String)null, (Object[])messages);
            }
            public void printToConsole(String message) {
                AdtPlugin.printToConsole((String)null, message);
            }
            public void printToConsole(String[] messages) {
                AdtPlugin.printToConsole((String)null, (Object[])messages);
            }
        });

        // get the eclipse store
        IPreferenceStore eclipseStore = getPreferenceStore();
        AdtPrefs.init(eclipseStore);

        // set the listener for the preference change
        eclipseStore.addPropertyChangeListener(new IPropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent event) {
                // load the new preferences
                AdtPrefs.getPrefs().loadValues(event);

                // if the SDK changed, we have to do some extra work
                if (AdtPrefs.PREFS_SDK_DIR.equals(event.getProperty())) {

                    // finally restart adb, in case it's a different version
                    DdmsPlugin.setToolsLocation(getOsAbsoluteAdb(), true /* startAdb */,
                            getOsAbsoluteHprofConv(), getOsAbsoluteTraceview());

                    // get the SDK location and build id.
                    if (checkSdkLocationAndId()) {
                        // if sdk if valid, reparse it

                        reparseSdk();
                    }
                }
            }
        });

        // load preferences.
        AdtPrefs.getPrefs().loadValues(null /*event*/);

        // initialize editors
        startEditors();

        // Listen on resource file edits for updates to file inclusion
        IncludeFinder.start();

        // Parse the SDK content.
        // This is deferred in separate jobs to avoid blocking the bundle start.
        final boolean isSdkLocationValid = checkSdkLocationAndId();
        if (isSdkLocationValid) {
            // parse the SDK resources.
            // Wait 2 seconds before starting the job. This leaves some time to the
            // other bundles to initialize.
            parseSdkContent(2000 /*milliseconds*/);
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    @Override
    public void stop(BundleContext context) throws Exception {
        super.stop(context);

        stopEditors();
        IncludeFinder.stop();

        mRed.dispose();
        synchronized (AdtPlugin.class) {
            sPlugin = null;
        }
    }

    /**
     * Returns the shared instance
     *
     * @return the shared instance
     */
    public static synchronized AdtPlugin getDefault() {
        return sPlugin;
    }

    public static Display getDisplay() {
        IWorkbench bench = null;
        synchronized (AdtPlugin.class) {
            bench = sPlugin.getWorkbench();
        }

        if (bench != null) {
            return bench.getDisplay();
        }
        return null;
    }

    /** Returns the adb path relative to the sdk folder */
    public static String getOsRelativeAdb() {
        return SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER + SdkConstants.FN_ADB;
    }

    /** Returns the zipalign path relative to the sdk folder */
    public static String getOsRelativeZipAlign() {
        return SdkConstants.OS_SDK_TOOLS_FOLDER + SdkConstants.FN_ZIPALIGN;
    }

    /** Returns the emulator path relative to the sdk folder */
    public static String getOsRelativeEmulator() {
        return SdkConstants.OS_SDK_TOOLS_FOLDER + SdkConstants.FN_EMULATOR;
    }

    /** Returns the adb path relative to the sdk folder */
    public static String getOsRelativeProguard() {
        return SdkConstants.OS_SDK_TOOLS_PROGUARD_BIN_FOLDER + SdkConstants.FN_PROGUARD;
    }

    /** Returns the absolute adb path */
    public static String getOsAbsoluteAdb() {
        return getOsSdkFolder() + getOsRelativeAdb();
    }

    /** Returns the absolute zipalign path */
    public static String getOsAbsoluteZipAlign() {
        return getOsSdkFolder() + getOsRelativeZipAlign();
    }

    /** Returns the absolute traceview path */
    public static String getOsAbsoluteTraceview() {
        return getOsSdkFolder() + SdkConstants.OS_SDK_TOOLS_FOLDER +
                AdtConstants.FN_TRACEVIEW;
    }

    /** Returns the absolute emulator path */
    public static String getOsAbsoluteEmulator() {
        return getOsSdkFolder() + getOsRelativeEmulator();
    }

    public static String getOsAbsoluteHprofConv() {
        return getOsSdkFolder() + SdkConstants.OS_SDK_TOOLS_FOLDER +
                AdtConstants.FN_HPROF_CONV;
    }

    /** Returns the absolute proguard path */
    public static String getOsAbsoluteProguard() {
        return getOsSdkFolder() + getOsRelativeProguard();
    }

    /**
     * Returns a Url file path to the javaDoc folder.
     */
    public static String getUrlDoc() {
        return ProjectHelper.getJavaDocPath(
                getOsSdkFolder() + AdtConstants.WS_JAVADOC_FOLDER_LEAF);
    }

    /**
     * Returns the SDK folder.
     * Guaranteed to be terminated by a platform-specific path separator.
     */
    public static synchronized String getOsSdkFolder() {
        if (sPlugin == null) {
            return null;
        }

        return AdtPrefs.getPrefs().getOsSdkFolder();
    }

    public static String getOsSdkToolsFolder() {
        return getOsSdkFolder() + SdkConstants.OS_SDK_TOOLS_FOLDER;
    }

    /**
     * Returns an image descriptor for the image file at the given
     * plug-in relative path
     *
     * @param path the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        return imageDescriptorFromPlugin(PLUGIN_ID, path);
    }

    /**
     * Reads the contents of an {@link IFile} and return it as a String
     *
     * @param file the file to be read
     * @return the String read from the file, or null if there was an error
     */
    public static String readFile(IFile file) {
        InputStream contents = null;
        try {
            contents = file.getContents();
            String charset = file.getCharset();
            return readFile(new InputStreamReader(contents, charset));
        } catch (CoreException e) {
            // pass -- ignore files we can't read
        } catch (IOException e) {
            // pass -- ignore files we can't read.

            // Note that IFile.getContents() indicates it throws a CoreException but
            // experience shows that if the file does not exists it really throws
            // IOException.
            // New InputStreamReader() throws UnsupportedEncodingException
            // which is handled by this IOException catch.

        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }

        return null;
    }

    /**
     * Reads the contents of an {@link File} and return it as a String
     *
     * @param file the file to be read
     * @return the String read from the file, or null if there was an error
     */
    public static String readFile(File file) {
        try {
            return readFile(new FileReader(file));
        } catch (FileNotFoundException e) {
            AdtPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Writes the given content out to the given {@link File}. The file will be deleted if
     * it already exists.
     *
     * @param file the target file
     * @param content the content to be written into the file
     */
    public static void writeFile(File file, String content) {
        if (file.exists()) {
            file.delete();
        }
        FileWriter fw = null;
        try {
            fw = new FileWriter(file);
            fw.write(content);
        } catch (IOException e) {
            AdtPlugin.log(e, null);
        } finally {
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException e) {
                    AdtPlugin.log(e, null);
                }
            }
        }
    }

    /**
     * Returns true iff the given file contains the given String.
     *
     * @param file the file to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    public static boolean fileContains(IFile file, String string) {
        InputStream contents = null;
        try {
            contents = file.getContents();
            String charset = file.getCharset();
            return streamContains(new InputStreamReader(contents, charset), string);
        } catch (Exception e) {
            AdtPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * Returns true iff the given file contains the given String.
     *
     * @param file the file to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    public static boolean fileContains(File file, String string) {
        try {
            return streamContains(new FileReader(file), string);
        } catch (Exception e) {
            AdtPlugin.log(e, "Can't read file %1$s", file); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * Returns true iff the given input stream contains the given String.
     *
     * @param r the stream to look for the string in
     * @param string the string to be searched for
     * @return true if the file is found and contains the given string anywhere within it
     */
    public static boolean streamContains(Reader r, String string) {
        if (string.length() == 0) {
            return true;
        }

        PushbackReader reader = null;
        try {
            reader = new PushbackReader(r, string.length());
            char first = string.charAt(0);
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    return false;
                } else if (c == first) {
                    boolean matches = true;
                    for (int i = 1; i < string.length(); i++) {
                        c = reader.read();
                        if (c == -1) {
                            return false;
                        } else if (string.charAt(i) != (char)c) {
                            matches = false;
                            // Back up the characters that did not match
                            reader.backup(i-1);
                            break;
                        }
                    }
                    if (matches) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            AdtPlugin.log(e, "Can't read stream"); //$NON-NLS-1$
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                AdtPlugin.log(e, "Can't read stream"); //$NON-NLS-1$
            }
        }

        return false;

    }

    /**
     * A special reader that allows backing up in the input (up to a predefined maximum
     * number of characters)
     * <p>
     * NOTE: This class ONLY works with the {@link #read()} method!!
     */
    private static class PushbackReader extends BufferedReader {
        /**
         * Rolling/circular buffer. Can be a char rather than int since we never store EOF
         * in it.
         */
        private char[] mStorage;

        /** Points to the head of the queue. When equal to the tail, the queue is empty. */
        private int mHead;

        /**
         * Points to the tail of the queue. This will move with each read of the actual
         * wrapped reader, and the characters previous to it in the circular buffer are
         * the most recently read characters.
         */
        private int mTail;

        /**
         * Creates a new reader with a given maximum number of backup characters
         *
         * @param reader the reader to wrap
         * @param max the maximum number of characters to allow rollback for
         */
        public PushbackReader(Reader reader, int max) {
            super(reader);
            mStorage = new char[max + 1];
        }

        @Override
        public int read() throws IOException {
            // Have we backed up? If so we should serve characters
            // from the storage
            if (mHead != mTail) {
                char c = mStorage[mHead];
                mHead = (mHead + 1) % mStorage.length;
                return c;
            }
            assert mHead == mTail;

            // No backup -- read the next character, but stash it into storage
            // as well such that we can retrieve it if we must.
            int c = super.read();
            mStorage[mHead] = (char) c;
            mHead = mTail = (mHead + 1) % mStorage.length;
            return c;
        }

        /**
         * Backs up the reader a given number of characters. The next N reads will yield
         * the N most recently read characters prior to this backup.
         *
         * @param n the number of characters to be backed up
         */
        public void backup(int n) {
            if (n >= mStorage.length) {
                throw new IllegalArgumentException("Exceeded backup limit");
            }
            assert n < mStorage.length;
            mHead -= n;
            if (mHead < 0) {
                mHead += mStorage.length;
            }
        }
    }

    /**
     * Reads the contents of a {@link ResourceFile} and returns it as a String
     *
     * @param file the file to be read
     * @return the contents as a String, or null if reading failed
     */
    public static String readFile(ResourceFile file) {
        InputStream contents = null;
        try {
            contents = file.getFile().getContents();
            return readFile(new InputStreamReader(contents));
        } catch (StreamException e) {
            // pass -- ignore files we can't read
        } finally {
            try {
                if (contents != null) {
                    contents.close();
                }
            } catch (IOException e) {
                AdtPlugin.log(e, "Can't read layout file"); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Reads the contents of an {@link InputStreamReader} and return it as a String
     *
     * @param inputStream the input stream to be read from
     * @return the String read from the stream, or null if there was an error
     */
    public static String readFile(Reader inputStream) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(inputStream);
            StringBuilder sb = new StringBuilder(2000);
            while (true) {
                int c = reader.read();
                if (c == -1) {
                    return sb.toString();
                } else {
                    sb.append((char)c);
                }
            }
        } catch (IOException e) {
            // pass -- ignore files we can't read
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                AdtPlugin.log(e, "Can't read input stream"); //$NON-NLS-1$
            }
        }

        return null;
    }

    /**
     * Reads and returns the content of a text file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static String readEmbeddedTextFile(String filepath) {
        try {
            InputStream is = readEmbeddedFileAsStream(filepath);
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                String line;
                StringBuilder total = new StringBuilder(reader.readLine());
                while ((line = reader.readLine()) != null) {
                    total.append('\n');
                    total.append(line);
                }

                return total.toString();
            }
        } catch (IOException e) {
            // we'll just return null
            AdtPlugin.log(e, "Failed to read text file '%s'", filepath);  //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Reads and returns the content of a binary file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static byte[] readEmbeddedFile(String filepath) {
        try {
            InputStream is = readEmbeddedFileAsStream(filepath);
            if (is != null) {
                // create a buffered reader to facilitate reading.
                BufferedInputStream stream = new BufferedInputStream(is);

                // get the size to read.
                int avail = stream.available();

                // create the buffer and reads it.
                byte[] buffer = new byte[avail];
                stream.read(buffer);

                // and return.
                return buffer;
            }
        } catch (IOException e) {
            // we'll just return null;.
            AdtPlugin.log(e, "Failed to read binary file '%s'", filepath);  //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Reads and returns the content of a binary file embedded in the plugin jar
     * file.
     * @param filepath the file path to the text file
     * @return null if the file could not be read
     */
    public static InputStream readEmbeddedFileAsStream(String filepath) {
        // attempt to read an embedded file
        try {
            URL url = getEmbeddedFileUrl(AdtConstants.WS_SEP + filepath);
            if (url != null) {
                return url.openStream();
            }
        } catch (MalformedURLException e) {
            // we'll just return null.
            AdtPlugin.log(e, "Failed to read stream '%s'", filepath);  //$NON-NLS-1$
        } catch (IOException e) {
            // we'll just return null;.
            AdtPlugin.log(e, "Failed to read stream '%s'", filepath);  //$NON-NLS-1$
        }

        return null;
    }

    /**
     * Returns the URL of a binary file embedded in the plugin jar file.
     * @param filepath the file path to the text file
     * @return null if the file was not found.
     */
    public static URL getEmbeddedFileUrl(String filepath) {
        Bundle bundle = null;
        synchronized (AdtPlugin.class) {
            if (sPlugin != null) {
                bundle = sPlugin.getBundle();
            } else {
                AdtPlugin.log(IStatus.WARNING, "ADT Plugin is missing");    //$NON-NLS-1$
                return null;
            }
        }

        // attempt to get a file to one of the template.
        String path = filepath;
        if (!path.startsWith(AdtConstants.WS_SEP)) {
            path = AdtConstants.WS_SEP + path;
        }

        URL url = bundle.getEntry(path);

        if (url == null) {
            AdtPlugin.log(IStatus.INFO, "Bundle file URL not found at path '%s'", path); //$NON-NLS-1$
        }

        return url;
    }

    /**
     * Displays an error dialog box. This dialog box is ran asynchronously in the ui thread,
     * therefore this method can be called from any thread.
     * @param title The title of the dialog box
     * @param message The error message
     */
    public final static void displayError(final String title, final String message) {
        // get the current Display
        final Display display = getDisplay();

        // dialog box only run in ui thread..
        display.asyncExec(new Runnable() {
            public void run() {
                Shell shell = display.getActiveShell();
                MessageDialog.openError(shell, title, message);
            }
        });
    }

    /**
     * Displays a warning dialog box. This dialog box is ran asynchronously in the ui thread,
     * therefore this method can be called from any thread.
     * @param title The title of the dialog box
     * @param message The warning message
     */
    public final static void displayWarning(final String title, final String message) {
        // get the current Display
        final Display display = getDisplay();

        // dialog box only run in ui thread..
        display.asyncExec(new Runnable() {
            public void run() {
                Shell shell = display.getActiveShell();
                MessageDialog.openWarning(shell, title, message);
            }
        });
    }

    /**
     * Display a yes/no question dialog box. This dialog is opened synchronously in the ui thread,
     * therefore this message can be called from any thread.
     * @param title The title of the dialog box
     * @param message The error message
     * @return true if OK was clicked.
     */
    public final static boolean displayPrompt(final String title, final String message) {
        // get the current Display and Shell
        final Display display = getDisplay();

        // we need to ask the user what he wants to do.
        final boolean[] result = new boolean[1];
        display.syncExec(new Runnable() {
            public void run() {
                Shell shell = display.getActiveShell();
                result[0] = MessageDialog.openQuestion(shell, title, message);
            }
        });
        return result[0];
    }

    /**
     * Logs a message to the default Eclipse log.
     *
     * @param severity The severity code. Valid values are: {@link IStatus#OK},
     * {@link IStatus#ERROR}, {@link IStatus#INFO}, {@link IStatus#WARNING} or
     * {@link IStatus#CANCEL}.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static void log(int severity, String format, Object ... args) {
        if (format == null) {
            return;
        }

        String message = String.format(format, args);
        Status status = new Status(severity, PLUGIN_ID, message);

        if (getDefault() != null) {
            getDefault().getLog().log(status);
        } else {
            // During UnitTests, we generally don't have a plugin object. It's ok
            // to log to stdout or stderr in this case.
            (severity < IStatus.ERROR ? System.out : System.err).println(status.toString());
        }
    }

    /**
     * Logs an exception to the default Eclipse log.
     * <p/>
     * The status severity is always set to ERROR.
     *
     * @param exception the exception to log.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static void log(Throwable exception, String format, Object ... args) {
        String message = null;
        if (format != null) {
            message = String.format(format, args);
        } else {
            message = "";
        }
        Status status = new Status(IStatus.ERROR, PLUGIN_ID, message, exception);

        if (getDefault() != null) {
            getDefault().getLog().log(status);
        } else {
            // During UnitTests, we generally don't have a plugin object. It's ok
            // to log to stderr in this case.
            System.err.println(status.toString());
        }
    }

    /**
     * This is a mix between log(Throwable) and printErrorToConsole.
     * <p/>
     * This logs the exception with an ERROR severity and the given printf-like format message.
     * The same message is then printed on the Android error console with the associated tag.
     *
     * @param exception the exception to log.
     * @param format The format string, like for {@link String#format(String, Object...)}.
     * @param args The arguments for the format string, like for
     * {@link String#format(String, Object...)}.
     */
    public static synchronized void logAndPrintError(Throwable exception, String tag,
            String format, Object ... args) {
        if (sPlugin != null) {
            String message = String.format(format, args);
            Status status = new Status(IStatus.ERROR, PLUGIN_ID, message, exception);
            getDefault().getLog().log(status);
            printToStream(sPlugin.mAndroidConsoleErrorStream, tag, message);
            showAndroidConsole();
        }
    }

    /**
     * Prints one or more error message to the android console.
     * @param tag A tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static synchronized void printErrorToConsole(String tag, Object... objects) {
        if (sPlugin != null) {
            printToStream(sPlugin.mAndroidConsoleErrorStream, tag, objects);

            showAndroidConsole();
        }
    }

    /**
     * Prints one or more error message to the android console.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(Object... objects) {
        printErrorToConsole((String)null, objects);
    }

    /**
     * Prints one or more error message to the android console.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printErrorToConsole(IProject project, Object... objects) {
        String tag = project != null ? project.getName() : null;
        printErrorToConsole(tag, objects);
    }

    /**
     * Prints one or more build messages to the android console, filtered by Build output verbosity.
     * @param level {@link BuildVerbosity} level of the message.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     * @see BuildVerbosity#ALWAYS
     * @see BuildVerbosity#NORMAL
     * @see BuildVerbosity#VERBOSE
     */
    public static synchronized void printBuildToConsole(BuildVerbosity level, IProject project,
            Object... objects) {
        if (sPlugin != null) {
            if (level.getLevel() <= AdtPrefs.getPrefs().getBuildVerbosity().getLevel()) {
                String tag = project != null ? project.getName() : null;
                printToStream(sPlugin.mAndroidConsoleStream, tag, objects);
            }
        }
    }

    /**
     * Prints one or more message to the android console.
     * @param tag The tag to be associated with the message. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static synchronized void printToConsole(String tag, Object... objects) {
        if (sPlugin != null) {
            printToStream(sPlugin.mAndroidConsoleStream, tag, objects);
        }
    }

    /**
     * Prints one or more message to the android console.
     * @param project The project to which the message is associated. Can be null.
     * @param objects the objects to print through their <code>toString</code> method.
     */
    public static void printToConsole(IProject project, Object... objects) {
        String tag = project != null ? project.getName() : null;
        printToConsole(tag, objects);
    }

    /** Force the display of the android console */
    public static void showAndroidConsole() {
        // first make sure the console is in the workbench
        EclipseUiHelper.showView(IConsoleConstants.ID_CONSOLE_VIEW, true);

        // now make sure it's not docked.
        ConsolePlugin.getDefault().getConsoleManager().showConsoleView(
                AdtPlugin.getDefault().getAndroidConsole());
    }

    /**
     * Returns whether the {@link IAndroidTarget}s have been loaded from the SDK.
     */
    public final LoadStatus getSdkLoadStatus() {
        synchronized (Sdk.getLock()) {
            return mSdkIsLoaded;
        }
    }

    /**
     * Sets the given {@link IJavaProject} to have its target resolved again once the SDK finishes
     * to load.
     */
    public final void setProjectToResolve(IJavaProject javaProject) {
        synchronized (Sdk.getLock()) {
            mPostLoadProjectsToResolve.add(javaProject);
        }
    }

    /**
     * Sets the given {@link IJavaProject} to have its target checked for consistency
     * once the SDK finishes to load. This is used if the target is resolved using cached
     * information while the SDK is loading.
     */
    public final void setProjectToCheck(IJavaProject javaProject) {
        // only lock on
        synchronized (Sdk.getLock()) {
            mPostLoadProjectsToCheck.add(javaProject);
        }
    }

    /**
     * Checks the location of the SDK is valid and if it is, grab the SDK API version
     * from the SDK.
     * @return false if the location is not correct.
     */
    private boolean checkSdkLocationAndId() {
        String sdkLocation = AdtPrefs.getPrefs().getOsSdkFolder();
        if (sdkLocation == null || sdkLocation.length() == 0) {
            return false;
        }

        return checkSdkLocationAndId(sdkLocation, new CheckSdkErrorHandler() {
            @Override
            public boolean handleError(String message) {
                AdtPlugin.displayError("Android SDK Verification", message);
                return false;
            }

            @Override
            public boolean handleWarning(String message) {
                AdtPlugin.displayWarning("Android SDK Verification", message);
                return true;
            }
        });
    }

    /**
     * Internal helper to perform the actual sdk location and id check.
     *
     * @param osSdkLocation The sdk directory, an OS path.
     * @param errorHandler An checkSdkErrorHandler that can display a warning or an error.
     * @return False if there was an error or the result from the errorHandler invocation.
     */
    public boolean checkSdkLocationAndId(String osSdkLocation, CheckSdkErrorHandler errorHandler) {
        if (osSdkLocation.endsWith(File.separator) == false) {
            osSdkLocation = osSdkLocation + File.separator;
        }

        File osSdkFolder = new File(osSdkLocation);
        if (osSdkFolder.isDirectory() == false) {
            return errorHandler.handleError(
                    String.format(Messages.Could_Not_Find_Folder, osSdkLocation));
        }

        String osTools = osSdkLocation + SdkConstants.OS_SDK_TOOLS_FOLDER;
        File toolsFolder = new File(osTools);
        if (toolsFolder.isDirectory() == false) {
            return errorHandler.handleError(
                    String.format(Messages.Could_Not_Find_Folder_In_SDK,
                            SdkConstants.FD_TOOLS, osSdkLocation));
        }

        // first check the min plug-in requirement as its error message is easier to figure
        // out for the user
        if (VersionCheck.checkVersion(osSdkLocation, errorHandler) == false) {
            return false;
        }

        // check that we have both the tools component and the platform-tools component.
        String platformTools = osSdkLocation + SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER;
        if (checkFolder(platformTools) == false) {
            return errorHandler.handleWarning("SDK Platform Tools component is missing!\n" +
                    "Please use the SDK Manager to install it.");
        }

        String tools = osSdkLocation + SdkConstants.OS_SDK_TOOLS_FOLDER;
        if (checkFolder(tools) == false) {
            return errorHandler.handleError("SDK Tools component is missing!\n" +
                    "Please use the SDK Manager to install it.");
        }

        // check the path to various tools we use to make sure nothing is missing. This is
        // not meant to be exhaustive.
        String[] filesToCheck = new String[] {
                osSdkLocation + getOsRelativeAdb(),
                osSdkLocation + getOsRelativeEmulator()
        };
        for (String file : filesToCheck) {
            if (checkFile(file) == false) {
                return errorHandler.handleError(String.format(Messages.Could_Not_Find, file));
            }
        }

        return true;
    }

    /**
     * Checks if a path reference a valid existing file.
     * @param osPath the os path to check.
     * @return true if the file exists and is, in fact, a file.
     */
    private boolean checkFile(String osPath) {
        File file = new File(osPath);
        if (file.isFile() == false) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a path reference a valid existing folder.
     * @param osPath the os path to check.
     * @return true if the folder exists and is, in fact, a folder.
     */
    private boolean checkFolder(String osPath) {
        File file = new File(osPath);
        if (file.isDirectory() == false) {
            return false;
        }

        return true;
    }

    /**
     * Parses the SDK resources.
     */
    private void parseSdkContent(long delay) {
        // Perform the update in a thread (here an Eclipse runtime job)
        // since this should never block the caller (especially the start method)
        Job job = new Job(Messages.AdtPlugin_Android_SDK_Content_Loader) {
            @SuppressWarnings("unchecked")
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {

                    if (mSdkIsLoading) {
                        return new Status(IStatus.WARNING, PLUGIN_ID,
                                "An Android SDK is already being loaded. Please try again later.");
                    }

                    mSdkIsLoading = true;

                    SubMonitor progress = SubMonitor.convert(monitor,
                            "Initialize SDK Manager", 100);

                    Sdk sdk = Sdk.loadSdk(AdtPrefs.getPrefs().getOsSdkFolder());

                    if (sdk != null) {

                        ArrayList<IJavaProject> list = new ArrayList<IJavaProject>();
                        synchronized (Sdk.getLock()) {
                            mSdkIsLoaded = LoadStatus.LOADED;

                            progress.setTaskName("Check Projects");

                            for (IJavaProject javaProject : mPostLoadProjectsToResolve) {
                                IProject iProject = javaProject.getProject();
                                if (iProject.isOpen()) {
                                    // project that have been resolved before the sdk was loaded
                                    // will have a ProjectState where the IAndroidTarget is null
                                    // so we load the target now that the SDK is loaded.
                                    sdk.loadTarget(Sdk.getProjectState(iProject));
                                    list.add(javaProject);
                                }
                            }

                            // done with this list.
                            mPostLoadProjectsToResolve.clear();
                        }

                        // check the projects that need checking.
                        // The method modifies the list (it removes the project that
                        // do not need to be resolved again).
                        AndroidClasspathContainerInitializer.checkProjectsCache(
                                mPostLoadProjectsToCheck);

                        list.addAll(mPostLoadProjectsToCheck);

                        // update the project that needs recompiling.
                        if (list.size() > 0) {
                            IJavaProject[] array = list.toArray(
                                    new IJavaProject[list.size()]);
                            AndroidClasspathContainerInitializer.updateProjects(array);
                        }

                        progress.worked(10);
                    } else {
                        // SDK failed to Load!
                        // Sdk#loadSdk() has already displayed an error.
                        synchronized (Sdk.getLock()) {
                            mSdkIsLoaded = LoadStatus.FAILED;
                        }
                    }

                    // Notify resource changed listeners
                    progress.setTaskName("Refresh UI");
                    progress.setWorkRemaining(mTargetChangeListeners.size());

                    // Clone the list before iterating, to avoid ConcurrentModification
                    // exceptions
                    final List<ITargetChangeListener> listeners =
                        (List<ITargetChangeListener>)mTargetChangeListeners.clone();
                    final SubMonitor progress2 = progress;
                    AdtPlugin.getDisplay().asyncExec(new Runnable() {
                        public void run() {
                            for (ITargetChangeListener listener : listeners) {
                                try {
                                    listener.onSdkLoaded();
                                } catch (Exception e) {
                                    AdtPlugin.log(e, "Failed to update a TargetChangeListener."); //$NON-NLS-1$
                                } finally {
                                    progress2.worked(1);
                                }
                            }
                        }
                    });
                } catch (Throwable t) {
                    log(t, "Unknown exception in parseSdkContent.");    //$NON-NLS-1$
                    return new Status(IStatus.ERROR, PLUGIN_ID,
                            "parseSdkContent failed", t);               //$NON-NLS-1$

                } finally {
                    mSdkIsLoading = false;
                    if (monitor != null) {
                        monitor.done();
                    }
                }

                return Status.OK_STATUS;
            }
        };
        job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
        if (delay > 0) {
            job.schedule(delay);
        } else {
            job.schedule();
        }
    }

    /** Returns the global android console */
    public MessageConsole getAndroidConsole() {
        return mAndroidConsole;
    }

    // ----- Methods for Editors -------

    public void startEditors() {
        sAndroidLogoDesc = imageDescriptorFromPlugin(AdtPlugin.PLUGIN_ID,
                "/icons/android.png"); //$NON-NLS-1$
        sAndroidLogo = sAndroidLogoDesc.createImage();

        // Add a resource listener to handle compiled resources.
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        mResourceMonitor = GlobalProjectMonitor.startMonitoring(ws);

        if (mResourceMonitor != null) {
            try {
                setupDefaultEditor(mResourceMonitor);
                ResourceManager.setup(mResourceMonitor);
            } catch (Throwable t) {
                log(t, "ResourceManager.setup failed"); //$NON-NLS-1$
            }
        }
    }

    /**
     * The <code>AbstractUIPlugin</code> implementation of this <code>Plugin</code>
     * method saves this plug-in's preference and dialog stores and shuts down
     * its image registry (if they are in use). Subclasses may extend this
     * method, but must send super <b>last</b>. A try-finally statement should
     * be used where necessary to ensure that <code>super.shutdown()</code> is
     * always done.
     *
     * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
     */
    public void stopEditors() {
        sAndroidLogo.dispose();

        IconFactory.getInstance().dispose();

        // Remove the resource listener that handles compiled resources.
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        GlobalProjectMonitor.stopMonitoring(ws);

        mRed.dispose();
    }

    /**
     * Returns an Image for the small Android logo.
     *
     * Callers should not dispose it.
     */
    public static Image getAndroidLogo() {
        return sAndroidLogo;
    }

    /**
     * Returns an {@link ImageDescriptor} for the small Android logo.
     *
     * Callers should not dispose it.
     */
    public static ImageDescriptor getAndroidLogoDesc() {
        return sAndroidLogoDesc;
    }

    /**
     * Returns the ResourceMonitor object.
     */
    public GlobalProjectMonitor getResourceMonitor() {
        return mResourceMonitor;
    }

    /**
     * Sets up the editor to register default editors for resource files when needed.
     *
     * This is called by the {@link AdtPlugin} during initialization.
     *
     * @param monitor The main Resource Monitor object.
     */
    public void setupDefaultEditor(GlobalProjectMonitor monitor) {
        monitor.addFileListener(new IFileListener() {

            private static final String UNKNOWN_EDITOR = "unknown-editor"; //$NON-NLS-1$

            /* (non-Javadoc)
             * Sent when a file changed.
             * @param file The file that changed.
             * @param markerDeltas The marker deltas for the file.
             * @param kind The change kind. This is equivalent to
             * {@link IResourceDelta#accept(IResourceDeltaVisitor)}
             *
             * @see IFileListener#fileChanged
             */
            public void fileChanged(IFile file, IMarkerDelta[] markerDeltas, int kind) {
                if (AdtConstants.EXT_XML.equals(file.getFileExtension())) {
                    // The resources files must have a file path similar to
                    //    project/res/.../*.xml
                    // There is no support for sub folders, so the segment count must be 4
                    if (DEBUG_XML_FILE_INIT) {
                        AdtPlugin.log(IStatus.INFO, "fileChanged %1$s",
                            file.getFullPath().toOSString());
                    }

                    if (file.getFullPath().segmentCount() == 4) {
                        // check if we are inside the res folder.
                        String segment = file.getFullPath().segment(1);
                        if (segment.equalsIgnoreCase(SdkConstants.FD_RESOURCES)) {
                            // we are inside a res/ folder, get the actual ResourceFolder
                            ProjectResources resources = ResourceManager.getInstance().
                                getProjectResources(file.getProject());

                            // This happens when importing old Android projects in Eclipse
                            // that lack the container (probably because resources fail to build
                            // properly.)
                            if (resources == null) {
                                log(IStatus.INFO,
                                        "getProjectResources failed for path %1$s in project %2$s", //$NON-NLS-1$
                                        file.getFullPath().toOSString(),
                                        file.getProject().getName());
                                return;
                            }

                            ResourceFolder resFolder = resources.getResourceFolder(
                                (IFolder)file.getParent());

                            if (resFolder != null) {
                                if (kind == IResourceDelta.ADDED) {
                                    resourceAdded(file, resFolder.getType());
                                } else if (kind == IResourceDelta.CHANGED) {
                                    resourceChanged(file, resFolder.getType());
                                }
                            } else {
                                if (DEBUG_XML_FILE_INIT) {
                                    AdtPlugin.log(IStatus.INFO, "  The resource folder was null");
                                }

                                // if the res folder is null, this means the name is invalid,
                                // in this case we remove whatever android editors that was set
                                // as the default editor.
                                IEditorDescriptor desc = IDE.getDefaultEditor(file);
                                String editorId = desc.getId();
                                if (DEBUG_XML_FILE_INIT) {
                                    AdtPlugin.log(IStatus.INFO, "Old editor id=%1$s", editorId);
                                }
                                if (editorId.startsWith(AdtConstants.EDITORS_NAMESPACE)) {
                                    // reset the default editor.
                                    IDE.setDefaultEditor(file, null);
                                    if (DEBUG_XML_FILE_INIT) {
                                        AdtPlugin.log(IStatus.INFO, "  Resetting editor id to default");
                                    }
                                }
                            }
                        } else {
                            if (DEBUG_XML_FILE_INIT) {
                                AdtPlugin.log(IStatus.INFO, "    Not in resources/, ignoring");
                            }
                        }
                    }
                }
            }

            private void resourceAdded(IFile file, ResourceFolderType type) {
                if (DEBUG_XML_FILE_INIT) {
                    AdtPlugin.log(IStatus.INFO, "resourceAdded %1$s - type=%1$s",
                        file.getFullPath().toOSString(), type);
                }
                // set the default editor based on the type.
                if (type == ResourceFolderType.LAYOUT) {
                    if (DEBUG_XML_FILE_INIT) {
                        AdtPlugin.log(IStatus.INFO, "   set default editor id to layout");
                    }
                    IDE.setDefaultEditor(file, LayoutEditor.ID);
                } else if (type == ResourceFolderType.VALUES) {
                    IDE.setDefaultEditor(file, ResourcesEditor.ID);
                } else if (type == ResourceFolderType.MENU) {
                    IDE.setDefaultEditor(file, MenuEditor.ID);
                } else if (type == ResourceFolderType.COLOR) {
                    IDE.setDefaultEditor(file, ColorEditor.ID);
                } else if (type == ResourceFolderType.DRAWABLE) {
                    IDE.setDefaultEditor(file, DrawableEditor.ID);
                } else if (type == ResourceFolderType.ANIMATOR
                        || type == ResourceFolderType.ANIM) {
                    IDE.setDefaultEditor(file, AnimationEditor.ID);
                } else if (type == ResourceFolderType.XML) {
                    if (XmlEditor.canHandleFile(file)) {
                        if (DEBUG_XML_FILE_INIT) {
                            AdtPlugin.log(IStatus.INFO, "   set default editor id to XmlEditor.id");
                        }
                        IDE.setDefaultEditor(file, XmlEditor.ID);
                    } else {
                        if (DEBUG_XML_FILE_INIT) {
                            AdtPlugin.log(IStatus.INFO, "   set default editor id unknown");
                        }
                        // set a property to determine later if the XML can be handled
                        QualifiedName qname = new QualifiedName(
                                AdtPlugin.PLUGIN_ID,
                                UNKNOWN_EDITOR);
                        setFileProperty(file, qname, "1"); //$NON-NLS-1$
                    }
                }
            }

            private void resourceChanged(IFile file, ResourceFolderType type) {
                if (DEBUG_XML_FILE_INIT) {
                    AdtPlugin.log(IStatus.INFO, "resourceChanged %1$s - type=%1$s",
                        file.getFullPath().toOSString(), type);
                }
                if (type == ResourceFolderType.XML) {
                    IEditorDescriptor ed = IDE.getDefaultEditor(file);
                    if (ed == null || !ed.getId().equals(XmlEditor.ID)) {
                        QualifiedName qname = new QualifiedName(
                                AdtPlugin.PLUGIN_ID,
                                UNKNOWN_EDITOR);
                        String prop = getFileProperty(file, qname);
                        if (prop != null && XmlEditor.canHandleFile(file)) {
                            try {
                                // remove the property & set editor
                                setFileProperty(file, qname, null);

                                // the window can be null sometimes
                                IWorkbench wb = PlatformUI.getWorkbench();
                                IWorkbenchWindow win = wb == null ? null :
                                                       wb.getActiveWorkbenchWindow();
                                IWorkbenchPage page = win == null ? null :
                                                      win.getActivePage();

                                IEditorPart oldEditor = page == null ? null :
                                                        page.findEditor(new FileEditorInput(file));
                                if (page != null &&
                                        oldEditor != null &&
                                        AdtPlugin.displayPrompt("Android XML Editor",
                                            String.format("The file you just saved as been recognized as a file that could be better handled using the Android XML Editor. Do you want to edit '%1$s' using the Android XML editor instead?",
                                                    file.getFullPath()))) {
                                    IDE.setDefaultEditor(file, XmlEditor.ID);
                                    IEditorPart newEditor = page.openEditor(
                                            new FileEditorInput(file),
                                            XmlEditor.ID,
                                            true, /* activate */
                                            IWorkbenchPage.MATCH_NONE);

                                    if (newEditor != null) {
                                        page.closeEditor(oldEditor, true /* save */);
                                    }
                                }
                            } catch (CoreException e) {
                                // page.openEditor may have failed
                            }
                        }
                    }
                }
            }

        }, IResourceDelta.ADDED | IResourceDelta.CHANGED);
    }

    /**
     * Adds a new {@link ITargetChangeListener} to be notified when a new SDK is loaded, or when
     * a project has its target changed.
     */
    public void addTargetListener(ITargetChangeListener listener) {
        mTargetChangeListeners.add(listener);
    }

    /**
     * Removes an existing {@link ITargetChangeListener}.
     * @see #addTargetListener(ITargetChangeListener)
     */
    public void removeTargetListener(ITargetChangeListener listener) {
        mTargetChangeListeners.remove(listener);
    }

    /**
     * Updates all the {@link ITargetChangeListener}s that a target has changed for a given project.
     * <p/>Only editors related to that project should reload.
     */
    @SuppressWarnings("unchecked")
    public void updateTargetListeners(final IProject project) {
        final List<ITargetChangeListener> listeners =
            (List<ITargetChangeListener>)mTargetChangeListeners.clone();

        AdtPlugin.getDisplay().asyncExec(new Runnable() {
            public void run() {
                for (ITargetChangeListener listener : listeners) {
                    try {
                        listener.onProjectTargetChange(project);
                    } catch (Exception e) {
                        AdtPlugin.log(e, "Failed to update a TargetChangeListener.");  //$NON-NLS-1$
                    }
                }
            }
        });
    }

    /**
     * Updates all the {@link ITargetChangeListener}s that a target data was loaded.
     * <p/>Only editors related to a project using this target should reload.
     */
    @SuppressWarnings("unchecked")
    public void updateTargetListeners(final IAndroidTarget target) {
        final List<ITargetChangeListener> listeners =
            (List<ITargetChangeListener>)mTargetChangeListeners.clone();

        AdtPlugin.getDisplay().asyncExec(new Runnable() {
            public void run() {
                for (ITargetChangeListener listener : listeners) {
                    try {
                        listener.onTargetLoaded(target);
                    } catch (Exception e) {
                        AdtPlugin.log(e, "Failed to update a TargetChangeListener.");  //$NON-NLS-1$
                    }
                }
            }
        });
    }

    public static synchronized OutputStream getOutStream() {
        return sPlugin.mAndroidConsoleStream;
    }

    public static synchronized OutputStream getErrorStream() {
        return sPlugin.mAndroidConsoleErrorStream;
    }

    /**
     * Sets the named persistent property for the given file to the given value
     *
     * @param file the file to associate the property with
     * @param qname the name of the property
     * @param value the new value, or null to clear the property
     */
    public static void setFileProperty(IFile file, QualifiedName qname, String value) {
        try {
            file.setPersistentProperty(qname, value);
        } catch (CoreException e) {
            log(e, "Cannot set property %1$s to %2$s", qname, value);
        }
    }

    /**
     * Gets the named persistent file property from the given file
     *
     * @param file the file to look up properties for
     * @param qname the name of the property to look up
     * @return the property value, or null
     */
    public static String getFileProperty(IFile file, QualifiedName qname) {
        try {
            return file.getPersistentProperty(qname);
        } catch (CoreException e) {
            log(e, "Cannot get property %1$s", qname);
        }

        return null;
    }

    /**
     * Reparses the content of the SDK and updates opened projects.
     */
    public void reparseSdk() {
        // add all the opened Android projects to the list of projects to be updated
        // after the SDK is reloaded
        synchronized (Sdk.getLock()) {
            // get the project to refresh.
            IJavaProject[] androidProjects = BaseProjectHelper.getAndroidProjects(null /*filter*/);
            mPostLoadProjectsToResolve.addAll(Arrays.asList(androidProjects));
        }

        // parse the SDK resources at the new location
        parseSdkContent(0 /*immediately*/);
    }

    /**
     * Prints messages, associated with a project to the specified stream
     * @param stream The stream to write to
     * @param tag The tag associated to the message. Can be null
     * @param objects The objects to print through their toString() method (or directly for
     * {@link String} objects.
     */
    public static synchronized void printToStream(MessageConsoleStream stream, String tag,
            Object... objects) {
        String dateTag = AndroidPrintStream.getMessageTag(tag);

        for (Object obj : objects) {
            stream.print(dateTag);
            stream.print(" "); //$NON-NLS-1$
            if (obj instanceof String) {
                stream.println((String)obj);
            } else if (obj == null) {
                stream.println("(null)");  //$NON-NLS-1$
            } else {
                stream.println(obj.toString());
            }
        }
    }

    // --------- ILogger methods -----------

    public void error(Throwable t, String format, Object... args) {
        if (t != null) {
            log(t, format, args);
        } else {
            log(IStatus.ERROR, format, args);
        }
    }

    public void printf(String format, Object... args) {
        log(IStatus.INFO, format, args);
    }

    public void warning(String format, Object... args) {
        log(IStatus.WARNING, format, args);
    }

    /**
     * Opens the given URL in a browser tab
     *
     * @param url the URL to open in a browser
     */
    public static void openUrl(URL url) {
        IWorkbenchBrowserSupport support = PlatformUI.getWorkbench().getBrowserSupport();
        IWebBrowser browser;
        try {
            browser = support.createBrowser(PLUGIN_ID);
            browser.openURL(url);
        } catch (PartInitException e) {
            log(e, null);
        }
    }

    /**
     * Opens a Java class for the given fully qualified class name
     *
     * @param project the project containing the class
     * @param fqcn the fully qualified class name of the class to be opened
     * @return true if the class was opened, false otherwise
     */
    public static boolean openJavaClass(IProject project, String fqcn) {
        if (fqcn == null) {
            return false;
        }

        // Handle inner classes
        if (fqcn.indexOf('$') != -1) {
            fqcn = fqcn.replaceAll("\\$", "."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        try {
            if (project.hasNature(JavaCore.NATURE_ID)) {
                IJavaProject javaProject = JavaCore.create(project);
                IJavaElement result = javaProject.findType(fqcn);
                if (result != null) {
                    return JavaUI.openInEditor(result) != null;
                }
            }
        } catch (Throwable e) {
            log(e, "Can't open class %1$s", fqcn); //$NON-NLS-1$
        }

        return false;
    }

    /**
     * Opens the given file and shows the given (optional) region in the editor (or
     * if no region is specified, opens the editor tab.)
     *
     * @param file the file to be opened
     * @param region an optional region which if set will be selected and shown to the
     *            user
     * @throws PartInitException if something goes wrong
     */
    public static void openFile(IFile file, IRegion region) throws PartInitException {
        openFile(file, region, true);
    }

    /**
     * Opens the given file and shows the given (optional) region
     *
     * @param file the file to be opened
     * @param region an optional region which if set will be selected and shown to the
     *            user
     * @param showEditorTab if true, front the editor tab after opening the file
     * @return the editor that was opened, or null if no editor was opened
     * @throws PartInitException if something goes wrong
     */
    public static IEditorPart openFile(IFile file, IRegion region, boolean showEditorTab)
            throws PartInitException {
        IWorkbench workbench = PlatformUI.getWorkbench();
        if (workbench == null) {
            return null;
        }
        IWorkbenchWindow activeWorkbenchWindow = workbench.getActiveWorkbenchWindow();
        if (activeWorkbenchWindow == null) {
            return null;
        }
        IWorkbenchPage page = activeWorkbenchWindow.getActivePage();
        if (page == null) {
            return null;
        }
        IEditorPart targetEditor = IDE.openEditor(page, file, true);
        if (targetEditor instanceof AndroidXmlEditor) {
            AndroidXmlEditor editor = (AndroidXmlEditor) targetEditor;
            if (region != null) {
                editor.show(region.getOffset(), region.getLength(), showEditorTab);
            } else if (showEditorTab) {
                editor.setActivePage(AndroidXmlEditor.TEXT_EDITOR_ID);
            }
        }

        return targetEditor;
    }
}
