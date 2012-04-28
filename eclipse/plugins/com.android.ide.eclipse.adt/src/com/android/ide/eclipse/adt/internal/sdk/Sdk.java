/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.sdk;

import com.android.ddmlib.IDevice;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.build.DexWrapper;
import com.android.ide.eclipse.adt.internal.project.AndroidClasspathContainerInitializer;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.LibraryClasspathContainerInitializer;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IFileListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IProjectListener;
import com.android.ide.eclipse.adt.internal.resources.manager.GlobalProjectMonitor.IResourceEventListener;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState.LibraryDifference;
import com.android.ide.eclipse.adt.internal.sdk.ProjectState.LibraryState;
import com.android.io.StreamException;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.ISdkLog;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.SdkManager;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.internal.project.ProjectPropertiesWorkingCopy;
import com.android.sdklib.internal.project.ProjectProperties.PropertyType;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * Central point to load, manipulate and deal with the Android SDK. Only one SDK can be used
 * at the same time.
 *
 * To start using an SDK, call {@link #loadSdk(String)} which returns the instance of
 * the Sdk object.
 *
 * To get the list of platforms or add-ons present in the SDK, call {@link #getTargets()}.
 */
public final class Sdk  {
    private final static boolean DEBUG = false;

    private final static Object LOCK = new Object();

    private static Sdk sCurrentSdk = null;

    /**
     * Map associating {@link IProject} and their state {@link ProjectState}.
     * <p/>This <b>MUST NOT</b> be accessed directly. Instead use {@link #getProjectState(IProject)}.
     */
    private final static HashMap<IProject, ProjectState> sProjectStateMap =
            new HashMap<IProject, ProjectState>();

    /**
     * Data bundled using during the load of Target data.
     * <p/>This contains the {@link LoadStatus} and a list of projects that attempted
     * to compile before the loading was finished. Those projects will be recompiled
     * at the end of the loading.
     */
    private final static class TargetLoadBundle {
        LoadStatus status;
        final HashSet<IJavaProject> projecsToReload = new HashSet<IJavaProject>();
    }

    private final SdkManager mManager;
    private final DexWrapper mDexWrapper;
    private final AvdManager mAvdManager;

    /** Map associating an {@link IAndroidTarget} to an {@link AndroidTargetData} */
    private final HashMap<IAndroidTarget, AndroidTargetData> mTargetDataMap =
        new HashMap<IAndroidTarget, AndroidTargetData>();
    /** Map associating an {@link IAndroidTarget} and its {@link TargetLoadBundle}. */
    private final HashMap<IAndroidTarget, TargetLoadBundle> mTargetDataStatusMap =
        new HashMap<IAndroidTarget, TargetLoadBundle>();

    /**
     * If true the target data will never load anymore. The only way to reload them is to
     * completely reload the SDK with {@link #loadSdk(String)}
     */
    private boolean mDontLoadTargetData = false;

    private final String mDocBaseUrl;

    private final LayoutDeviceManager mLayoutDeviceManager = new LayoutDeviceManager();

    /**
     * Classes implementing this interface will receive notification when targets are changed.
     */
    public interface ITargetChangeListener {
        /**
         * Sent when project has its target changed.
         */
        void onProjectTargetChange(IProject changedProject);

        /**
         * Called when the targets are loaded (either the SDK finished loading when Eclipse starts,
         * or the SDK is changed).
         */
        void onTargetLoaded(IAndroidTarget target);

        /**
         * Called when the base content of the SDK is parsed.
         */
        void onSdkLoaded();
    }

    /**
     * Basic abstract implementation of the ITargetChangeListener for the case where both
     * {@link #onProjectTargetChange(IProject)} and {@link #onTargetLoaded(IAndroidTarget)}
     * use the same code based on a simple test requiring to know the current IProject.
     */
    public static abstract class TargetChangeListener implements ITargetChangeListener {
        /**
         * Returns the {@link IProject} associated with the listener.
         */
        public abstract IProject getProject();

        /**
         * Called when the listener needs to take action on the event. This is only called
         * if {@link #getProject()} and the {@link IAndroidTarget} associated with the project
         * match the values received in {@link #onProjectTargetChange(IProject)} and
         * {@link #onTargetLoaded(IAndroidTarget)}.
         */
        public abstract void reload();

        public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(getProject())) {
                reload();
            }
        }

        public void onTargetLoaded(IAndroidTarget target) {
            IProject project = getProject();
            if (target != null && target.equals(Sdk.getCurrent().getTarget(project))) {
                reload();
            }
        }

        public void onSdkLoaded() {
            // do nothing;
        }
    }

    /**
     * Returns the lock object used to synchronize all operations dealing with SDK, targets and
     * projects.
     */
    public static final Object getLock() {
        return LOCK;
    }

    /**
     * Loads an SDK and returns an {@link Sdk} object if success.
     * <p/>If the SDK failed to load, it displays an error to the user.
     * @param sdkLocation the OS path to the SDK.
     */
    public static Sdk loadSdk(String sdkLocation) {
        synchronized (LOCK) {
            if (sCurrentSdk != null) {
                sCurrentSdk.dispose();
                sCurrentSdk = null;
            }

            final ArrayList<String> logMessages = new ArrayList<String>();
            ISdkLog log = new ISdkLog() {
                public void error(Throwable throwable, String errorFormat, Object... arg) {
                    if (errorFormat != null) {
                        logMessages.add(String.format("Error: " + errorFormat, arg));
                    }

                    if (throwable != null) {
                        logMessages.add(throwable.getMessage());
                    }
                }

                public void warning(String warningFormat, Object... arg) {
                    logMessages.add(String.format("Warning: " + warningFormat, arg));
                }

                public void printf(String msgFormat, Object... arg) {
                    logMessages.add(String.format(msgFormat, arg));
                }
            };

            // get an SdkManager object for the location
            SdkManager manager = SdkManager.createManager(sdkLocation, log);
            if (manager != null) {
                // load DX.
                DexWrapper dexWrapper = new DexWrapper();
                String dexLocation =
                        sdkLocation + File.separator +
                        SdkConstants.OS_SDK_PLATFORM_TOOLS_LIB_FOLDER + SdkConstants.FN_DX_JAR;
                IStatus res = dexWrapper.loadDex(dexLocation);
                if (res != Status.OK_STATUS) {
                    log.error(null, res.getMessage());
                    dexWrapper = null;
                }

                // create the AVD Manager
                AvdManager avdManager = null;
                try {
                    avdManager = new AvdManager(manager, log);
                } catch (AndroidLocationException e) {
                    log.error(e, "Error parsing the AVDs");
                }
                sCurrentSdk = new Sdk(manager, dexWrapper, avdManager);
                return sCurrentSdk;
            } else {
                StringBuilder sb = new StringBuilder("Error Loading the SDK:\n");
                for (String msg : logMessages) {
                    sb.append('\n');
                    sb.append(msg);
                }
                AdtPlugin.displayError("Android SDK", sb.toString());
            }
            return null;
        }
    }

    /**
     * Returns the current {@link Sdk} object.
     */
    public static Sdk getCurrent() {
        synchronized (LOCK) {
            return sCurrentSdk;
        }
    }

    /**
     * Returns the location (OS path) of the current SDK.
     */
    public String getSdkLocation() {
        return mManager.getLocation();
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    public String getDocumentationBaseUrl() {
        return mDocBaseUrl;
    }

    /**
     * Returns the list of targets that are available in the SDK.
     */
    public IAndroidTarget[] getTargets() {
        return mManager.getTargets();
    }

    /**
     * Returns a target from a hash that was generated by {@link IAndroidTarget#hashString()}.
     *
     * @param hash the {@link IAndroidTarget} hash string.
     * @return The matching {@link IAndroidTarget} or null.
     */
    public IAndroidTarget getTargetFromHashString(String hash) {
        return mManager.getTargetFromHashString(hash);
    }

    /**
     * Initializes a new project with a target. This creates the <code>project.properties</code>
     * file.
     * @param project the project to intialize
     * @param target the project's target.
     * @throws IOException if creating the file failed in any way.
     * @throws StreamException
     */
    public void initProject(IProject project, IAndroidTarget target)
            throws IOException, StreamException {
        if (project == null || target == null) {
            return;
        }

        synchronized (LOCK) {
            // check if there's already a state?
            ProjectState state = getProjectState(project);

            ProjectPropertiesWorkingCopy properties = null;

            if (state != null) {
                properties = state.getProperties().makeWorkingCopy();
            }

            if (properties == null) {
                IPath location = project.getLocation();
                if (location == null) {  // can return null when the project is being deleted.
                    // do nothing and return null;
                    return;
                }

                properties = ProjectProperties.create(location.toOSString(), PropertyType.PROJECT);
            }

            // save the target hash string in the project persistent property
            properties.setProperty(ProjectProperties.PROPERTY_TARGET, target.hashString());
            properties.save();
        }
    }

    /**
     * Returns the {@link ProjectState} object associated with a given project.
     * <p/>
     * This method is the only way to properly get the project's {@link ProjectState}
     * If the project has not yet been loaded, then it is loaded.
     * <p/>Because this methods deals with projects, it's not linked to an actual {@link Sdk}
     * objects, and therefore is static.
     * <p/>The value returned by {@link ProjectState#getTarget()} will change as {@link Sdk} objects
     * are replaced.
     * @param project the request project
     * @return the ProjectState for the project.
     */
    @SuppressWarnings("deprecation")
    public static ProjectState getProjectState(IProject project) {
        if (project == null) {
            return null;
        }

        synchronized (LOCK) {
            ProjectState state = sProjectStateMap.get(project);
            if (state == null) {
                // load the project.properties from the project folder.
                IPath location = project.getLocation();
                if (location == null) {  // can return null when the project is being deleted.
                    // do nothing and return null;
                    return null;
                }

                String projectLocation = location.toOSString();

                ProjectProperties properties = ProjectProperties.load(projectLocation,
                        PropertyType.PROJECT);
                if (properties == null) {
                    // legacy support: look for default.properties and rename it if needed.
                    properties = ProjectProperties.load(projectLocation,
                            PropertyType.LEGACY_DEFAULT);

                    if (properties == null) {
                        AdtPlugin.log(IStatus.ERROR,
                                "Failed to load properties file for project '%s'",
                                project.getName());
                        return null;
                    } else {
                        //legacy mode.
                        // get a working copy with the new type "project"
                        ProjectPropertiesWorkingCopy wc = properties.makeWorkingCopy(
                                PropertyType.PROJECT);
                        // and save it
                        try {
                            wc.save();

                            // delete the old file.
                            ProjectProperties.delete(projectLocation, PropertyType.LEGACY_DEFAULT);
                        } catch (Exception e) {
                            AdtPlugin.log(IStatus.ERROR,
                                    "Failed to rename properties file to %1$s for project '%s2$'",
                                    PropertyType.PROJECT.getFilename(), project.getName());
                        }
                    }
                }

                state = new ProjectState(project, properties);
                sProjectStateMap.put(project, state);

                // try to resolve the target
                if (AdtPlugin.getDefault().getSdkLoadStatus() == LoadStatus.LOADED) {
                    sCurrentSdk.loadTarget(state);
                }
            }

            return state;
        }
    }

    /**
     * Returns the {@link IAndroidTarget} object associated with the given {@link IProject}.
     */
    public IAndroidTarget getTarget(IProject project) {
        if (project == null) {
            return null;
        }

        ProjectState state = getProjectState(project);
        if (state != null) {
            return state.getTarget();
        }

        return null;
    }

    /**
     * Loads the {@link IAndroidTarget} for a given project.
     * <p/>This method will get the target hash string from the project properties, and resolve
     * it to an {@link IAndroidTarget} object and store it inside the {@link ProjectState}.
     * @param state the state representing the project to load.
     * @return the target that was loaded.
     */
    public IAndroidTarget loadTarget(ProjectState state) {
        IAndroidTarget target = null;
        if (state != null) {
            String hash = state.getTargetHashString();
            if (hash != null) {
                state.setTarget(target = getTargetFromHashString(hash));
            }
        }

        return target;
    }

    /**
     * Checks and loads (if needed) the data for a given target.
     * <p/> The data is loaded in a separate {@link Job}, and opened editors will be notified
     * through their implementation of {@link ITargetChangeListener#onTargetLoaded(IAndroidTarget)}.
     * <p/>An optional project as second parameter can be given to be recompiled once the target
     * data is finished loading.
     * <p/>The return value is non-null only if the target data has already been loaded (and in this
     * case is the status of the load operation)
     * @param target the target to load.
     * @param project an optional project to be recompiled when the target data is loaded.
     * If the target is already loaded, nothing happens.
     * @return The load status if the target data is already loaded.
     */
    public LoadStatus checkAndLoadTargetData(final IAndroidTarget target, IJavaProject project) {
        boolean loadData = false;

        synchronized (LOCK) {
            if (mDontLoadTargetData) {
                return LoadStatus.FAILED;
            }

            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);
            if (bundle == null) {
                bundle = new TargetLoadBundle();
                mTargetDataStatusMap.put(target,bundle);

                // set status to loading
                bundle.status = LoadStatus.LOADING;

                // add project to bundle
                if (project != null) {
                    bundle.projecsToReload.add(project);
                }

                // and set the flag to start the loading below
                loadData = true;
            } else if (bundle.status == LoadStatus.LOADING) {
                // add project to bundle
                if (project != null) {
                    bundle.projecsToReload.add(project);
                }

                return bundle.status;
            } else if (bundle.status == LoadStatus.LOADED || bundle.status == LoadStatus.FAILED) {
                return bundle.status;
            }
        }

        if (loadData) {
            Job job = new Job(String.format("Loading data for %1$s", target.getFullName())) {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    AdtPlugin plugin = AdtPlugin.getDefault();
                    try {
                        IStatus status = new AndroidTargetParser(target).run(monitor);

                        IJavaProject[] javaProjectArray = null;

                        synchronized (LOCK) {
                            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);

                            if (status.getCode() != IStatus.OK) {
                                bundle.status = LoadStatus.FAILED;
                                bundle.projecsToReload.clear();
                            } else {
                                bundle.status = LoadStatus.LOADED;

                                // Prepare the array of project to recompile.
                                // The call is done outside of the synchronized block.
                                javaProjectArray = bundle.projecsToReload.toArray(
                                        new IJavaProject[bundle.projecsToReload.size()]);

                                // and update the UI of the editors that depend on the target data.
                                plugin.updateTargetListeners(target);
                            }
                        }

                        if (javaProjectArray != null) {
                            AndroidClasspathContainerInitializer.updateProjects(javaProjectArray);
                        }

                        return status;
                    } catch (Throwable t) {
                        synchronized (LOCK) {
                            TargetLoadBundle bundle = mTargetDataStatusMap.get(target);
                            bundle.status = LoadStatus.FAILED;
                        }

                        AdtPlugin.log(t, "Exception in checkAndLoadTargetData.");    //$NON-NLS-1$
                        return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                                String.format(
                                        "Parsing Data for %1$s failed", //$NON-NLS-1$
                                        target.hashString()),
                                t);
                    }
                }
            };
            job.setPriority(Job.BUILD); // build jobs are run after other interactive jobs
            job.schedule();
        }

        // The only way to go through here is when the loading starts through the Job.
        // Therefore the current status of the target is LOADING.
        return LoadStatus.LOADING;
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IAndroidTarget}.
     */
    public AndroidTargetData getTargetData(IAndroidTarget target) {
        synchronized (LOCK) {
            return mTargetDataMap.get(target);
        }
    }

    /**
     * Return the {@link AndroidTargetData} for a given {@link IProject}.
     */
    public AndroidTargetData getTargetData(IProject project) {
        synchronized (LOCK) {
            IAndroidTarget target = getTarget(project);
            if (target != null) {
                return getTargetData(target);
            }
        }

        return null;
    }

    /**
     * Returns a {@link DexWrapper} object to be used to execute dx commands. If dx.jar was not
     * loaded properly, then this will return <code>null</code>.
     */
    public DexWrapper getDexWrapper() {
        return mDexWrapper;
    }

    /**
     * Returns the {@link AvdManager}. If the AvdManager failed to parse the AVD folder, this could
     * be <code>null</code>.
     */
    public AvdManager getAvdManager() {
        return mAvdManager;
    }

    public static AndroidVersion getDeviceVersion(IDevice device) {
        try {
            Map<String, String> props = device.getProperties();
            String apiLevel = props.get(IDevice.PROP_BUILD_API_LEVEL);
            if (apiLevel == null) {
                return null;
            }

            return new AndroidVersion(Integer.parseInt(apiLevel),
                    props.get((IDevice.PROP_BUILD_CODENAME)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public LayoutDeviceManager getLayoutDeviceManager() {
        return mLayoutDeviceManager;
    }

    /**
     * Returns a list of {@link ProjectState} representing projects depending, directly or
     * indirectly on a given library project.
     * @param project the library project.
     * @return a possibly empty list of ProjectState.
     */
    public static Set<ProjectState> getMainProjectsFor(IProject project) {
        synchronized (LOCK) {
            // first get the project directly depending on this.
            HashSet<ProjectState> list = new HashSet<ProjectState>();

            // loop on all project and see if ProjectState.getLibrary returns a non null
            // project.
            for (Entry<IProject, ProjectState> entry : sProjectStateMap.entrySet()) {
                if (project != entry.getKey()) {
                    LibraryState library = entry.getValue().getLibrary(project);
                    if (library != null) {
                        list.add(entry.getValue());
                    }
                }
            }

            // now look for projects depending on the projects directly depending on the library.
            HashSet<ProjectState> result = new HashSet<ProjectState>(list);
            for (ProjectState p : list) {
                if (p.isLibrary()) {
                    Set<ProjectState> set = getMainProjectsFor(p.getProject());
                    result.addAll(set);
                }
            }

            return result;
        }
    }

    /**
     * Unload the SDK's target data.
     *
     * If <var>preventReload</var>, this effect is final until the SDK instance is changed
     * through {@link #loadSdk(String)}.
     *
     * The goal is to unload the targets to be able to replace existing targets with new ones,
     * before calling {@link #loadSdk(String)} to fully reload the SDK.
     *
     * @param preventReload prevent the data from being loaded again for the remaining live of
     *   this {@link Sdk} instance.
     */
    public void unloadTargetData(boolean preventReload) {
        synchronized (LOCK) {
            mDontLoadTargetData = preventReload;

            // dispose of the target data.
            for (AndroidTargetData data : mTargetDataMap.values()) {
                data.dispose();
            }

            mTargetDataMap.clear();
        }
    }

    private Sdk(SdkManager manager, DexWrapper dexWrapper, AvdManager avdManager) {
        mManager = manager;
        mDexWrapper = dexWrapper;
        mAvdManager = avdManager;

        // listen to projects closing
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        // need to register the resource event listener first because the project listener
        // is called back during registration with project opened in the workspace.
        monitor.addResourceEventListener(mResourceEventListener);
        monitor.addProjectListener(mProjectListener);
        monitor.addFileListener(mFileListener, IResourceDelta.CHANGED | IResourceDelta.ADDED);

        // pre-compute some paths
        mDocBaseUrl = getDocumentationBaseUrl(mManager.getLocation() +
                SdkConstants.OS_SDK_DOCS_FOLDER);

        // load the built-in and user layout devices
        mLayoutDeviceManager.loadDefaultAndUserDevices(mManager.getLocation());
        // and the ones from the add-on
        loadLayoutDevices();

        // update whatever ProjectState is already present with new IAndroidTarget objects.
        synchronized (LOCK) {
            for (Entry<IProject, ProjectState> entry: sProjectStateMap.entrySet()) {
                entry.getValue().setTarget(
                        getTargetFromHashString(entry.getValue().getTargetHashString()));
            }
        }
    }

    /**
     *  Cleans and unloads the SDK.
     */
    private void dispose() {
        GlobalProjectMonitor monitor = GlobalProjectMonitor.getMonitor();
        monitor.removeProjectListener(mProjectListener);
        monitor.removeFileListener(mFileListener);
        monitor.removeResourceEventListener(mResourceEventListener);

        // the IAndroidTarget objects are now obsolete so update the project states.
        synchronized (LOCK) {
            for (Entry<IProject, ProjectState> entry: sProjectStateMap.entrySet()) {
                entry.getValue().setTarget(null);
            }

            // dispose of the target data.
            for (AndroidTargetData data : mTargetDataMap.values()) {
                data.dispose();
            }

            mTargetDataMap.clear();
        }
    }

    void setTargetData(IAndroidTarget target, AndroidTargetData data) {
        synchronized (LOCK) {
            mTargetDataMap.put(target, data);
        }
    }

    /**
     * Returns the URL to the local documentation.
     * Can return null if no documentation is found in the current SDK.
     *
     * @param osDocsPath Path to the documentation folder in the current SDK.
     *  The folder may not actually exist.
     * @return A file:// URL on the local documentation folder if it exists or null.
     */
    private String getDocumentationBaseUrl(String osDocsPath) {
        File f = new File(osDocsPath);

        if (f.isDirectory()) {
            try {
                // Note: to create a file:// URL, one would typically use something like
                // f.toURI().toURL().toString(). However this generates a broken path on
                // Windows, namely "C:\\foo" is converted to "file:/C:/foo" instead of
                // "file:///C:/foo" (i.e. there should be 3 / after "file:"). So we'll
                // do the correct thing manually.

                String path = f.getAbsolutePath();
                if (File.separatorChar != '/') {
                    path = path.replace(File.separatorChar, '/');
                }

                // For some reason the URL class doesn't add the mandatory "//" after
                // the "file:" protocol name, so it has to be hacked into the path.
                URL url = new URL("file", null, "//" + path);  //$NON-NLS-1$ //$NON-NLS-2$
                String result = url.toString();
                return result;
            } catch (MalformedURLException e) {
                // ignore malformed URLs
            }
        }

        return null;
    }

    /**
     * Parses the SDK add-ons to look for files called {@link SdkConstants#FN_DEVICES_XML} to
     * load {@link LayoutDevice} from them.
     */
    private void loadLayoutDevices() {
        IAndroidTarget[] targets = mManager.getTargets();
        for (IAndroidTarget target : targets) {
            if (target.isPlatform() == false) {
                File deviceXml = new File(target.getLocation(), SdkConstants.FN_DEVICES_XML);
                if (deviceXml.isFile()) {
                    mLayoutDeviceManager.parseAddOnLayoutDevice(deviceXml);
                }
            }
        }

        mLayoutDeviceManager.sealAddonLayoutDevices();
    }

    /**
     * Delegate listener for project changes.
     */
    private IProjectListener mProjectListener = new IProjectListener() {
        public void projectClosed(IProject project) {
            onProjectRemoved(project, false /*deleted*/);
        }

        public void projectDeleted(IProject project) {
            onProjectRemoved(project, true /*deleted*/);
        }

        private void onProjectRemoved(IProject removedProject, boolean deleted) {
            try {
                if (removedProject.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                    return;
                }
            } catch (CoreException e) {
                // this can only happen if the project does not exist or is not open, neither
                // of which can happen here since we're processing a Project removed/deleted event
                // which is processed before the project is actually removed/closed.
            }

            if (DEBUG) {
                System.out.println(">>> CLOSED: " + removedProject.getName());
            }

            // get the target project
            synchronized (LOCK) {
                // Don't use getProject() as it could create the ProjectState if it's not
                // there yet and this is not what we want. We want the current object.
                // Therefore, direct access to the map.
                ProjectState removedState = sProjectStateMap.get(removedProject);
                if (removedState != null) {
                    // 1. clear the layout lib cache associated with this project
                    IAndroidTarget target = removedState.getTarget();
                    if (target != null) {
                        // get the bridge for the target, and clear the cache for this project.
                        AndroidTargetData data = mTargetDataMap.get(target);
                        if (data != null) {
                            LayoutLibrary layoutLib = data.getLayoutLibrary();
                            if (layoutLib != null && layoutLib.getStatus() == LoadStatus.LOADED) {
                                layoutLib.clearCaches(removedProject);
                            }
                        }
                    }

                    // 2. if the project is a library, make sure to update the
                    // LibraryState for any project referencing it.
                    // Also, record the updated projects that are libraries, to update
                    // projects that depend on them.
                    for (ProjectState projectState : sProjectStateMap.values()) {
                        LibraryState libState = projectState.getLibrary(removedProject);
                        if (libState != null) {
                            // Close the library right away.
                            // This remove links between the LibraryState and the projectState.
                            // This is because in case of a rename of a project, projectClosed and
                            // projectOpened will be called before any other job is run, so we
                            // need to make sure projectOpened is closed with the main project
                            // state up to date.
                            libState.close();

                            // record that this project changed, and in case it's a library
                            // that its parents need to be updated as well.
                            markProject(projectState, projectState.isLibrary());
                        }
                    }

                    // now remove the project for the project map.
                    sProjectStateMap.remove(removedProject);
                }
            }

            if (DEBUG) {
                System.out.println("<<<");
            }
        }

        public void projectOpened(IProject project) {
            onProjectOpened(project);
        }

        public void projectOpenedWithWorkspace(IProject project) {
            // no need to force recompilation when projects are opened with the workspace.
            onProjectOpened(project);
        }

        private void onProjectOpened(final IProject openedProject) {
            try {
                if (openedProject.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                    return;
                }
            } catch (CoreException e) {
                // this can only happen if the project does not exist or is not open, neither
                // of which can happen here since we're processing a Project opened event.
            }


            ProjectState openedState = getProjectState(openedProject);
            if (openedState != null) {
                if (DEBUG) {
                    System.out.println(">>> OPENED: " + openedProject.getName());
                }

                synchronized (LOCK) {
                    final boolean isLibrary = openedState.isLibrary();
                    final boolean hasLibraries = openedState.hasLibraries();

                    if (isLibrary || hasLibraries) {
                        boolean foundLibraries = false;
                        // loop on all the existing project and update them based on this new
                        // project
                        for (ProjectState projectState : sProjectStateMap.values()) {
                            if (projectState != openedState) {
                                // If the project has libraries, check if this project
                                // is a reference.
                                if (hasLibraries) {
                                    // ProjectState#needs() both checks if this is a missing library
                                    // and updates LibraryState to contains the new values.
                                    // This must always be called.
                                    LibraryState libState = openedState.needs(projectState);

                                    if (libState != null) {
                                        // found a library! Add the main project to the list of
                                        // modified project
                                        foundLibraries = true;
                                    }
                                }

                                // if the project is a library check if the other project depend
                                // on it.
                                if (isLibrary) {
                                    // ProjectState#needs() both checks if this is a missing library
                                    // and updates LibraryState to contains the new values.
                                    // This must always be called.
                                    LibraryState libState = projectState.needs(openedState);

                                    if (libState != null) {
                                        // There's a dependency! Add the project to the list of
                                        // modified project, but also to a list of projects
                                        // that saw one of its dependencies resolved.
                                        markProject(projectState, projectState.isLibrary());
                                    }
                                }
                            }
                        }

                        // if the project has a libraries and we found at least one, we add
                        // the project to the list of modified project.
                        // Since we already went through the parent, no need to update them.
                        if (foundLibraries) {
                            markProject(openedState, false /*updateParents*/);
                        }
                    }
                }

                if (DEBUG) {
                    System.out.println("<<<");
                }
            }
        }

        public void projectRenamed(IProject project, IPath from) {
            // we don't actually care about this anymore.
        }
    };

    /**
     * Delegate listener for file changes.
     */
    private IFileListener mFileListener = new IFileListener() {
        public void fileChanged(final IFile file, IMarkerDelta[] markerDeltas, int kind) {
            if (SdkConstants.FN_PROJECT_PROPERTIES.equals(file.getName()) &&
                    file.getParent() == file.getProject()) {
                try {
                    // reload the content of the project.properties file and update
                    // the target.
                    IProject iProject = file.getProject();

                    if (iProject.hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                        return;
                    }

                    ProjectState state = Sdk.getProjectState(iProject);

                    // get the current target
                    IAndroidTarget oldTarget = state.getTarget();

                    // get the current library flag
                    boolean wasLibrary = state.isLibrary();

                    LibraryDifference diff = state.reloadProperties();

                    // load the (possibly new) target.
                    IAndroidTarget newTarget = loadTarget(state);

                    // reload the libraries if needed
                    if (diff.hasDiff()) {
                        if (diff.added) {
                            synchronized (LOCK) {
                                for (ProjectState projectState : sProjectStateMap.values()) {
                                    if (projectState != state) {
                                        // need to call needs to do the libraryState link,
                                        // but no need to look at the result, as we'll compare
                                        // the result of getFullLibraryProjects()
                                        // this is easier to due to indirect dependencies.
                                        state.needs(projectState);
                                    }
                                }
                            }
                        }

                        markProject(state, wasLibrary || state.isLibrary());
                    }

                    // apply the new target if needed.
                    if (newTarget != oldTarget) {
                        IJavaProject javaProject = BaseProjectHelper.getJavaProject(
                                file.getProject());
                        if (javaProject != null) {
                            AndroidClasspathContainerInitializer.updateProjects(
                                    new IJavaProject[] { javaProject });
                        }

                        // update the editors to reload with the new target
                        AdtPlugin.getDefault().updateTargetListeners(iProject);
                    }
                } catch (CoreException e) {
                    // This can't happen as it's only for closed project (or non existing)
                    // but in that case we can't get a fileChanged on this file.
                }
            }
        }
    };

    /** List of modified projects. This is filled in
     * {@link IProjectListener#projectOpened(IProject)},
     * {@link IProjectListener#projectOpenedWithWorkspace(IProject)},
     * {@link IProjectListener#projectClosed(IProject)}, and
     * {@link IProjectListener#projectDeleted(IProject)} and processed in
     * {@link IResourceEventListener#resourceChangeEventEnd()}.
     */
    private final List<ProjectState> mModifiedProjects = new ArrayList<ProjectState>();
    private final List<ProjectState> mModifiedChildProjects = new ArrayList<ProjectState>();

    private void markProject(ProjectState projectState, boolean updateParents) {
        if (mModifiedProjects.contains(projectState) == false) {
            if (DEBUG) {
                System.out.println("\tMARKED: " + projectState.getProject().getName());
            }
            mModifiedProjects.add(projectState);
        }

        // if the project is resolved also add it to this list.
        if (updateParents) {
            if (mModifiedChildProjects.contains(projectState) == false) {
                if (DEBUG) {
                    System.out.println("\tMARKED(child): " + projectState.getProject().getName());
                }
                mModifiedChildProjects.add(projectState);
            }
        }
    }

    /**
     * Delegate listener for resource changes. This is called before and after any calls to the
     * project and file listeners (for a given resource change event).
     */
    private IResourceEventListener mResourceEventListener = new IResourceEventListener() {
        public void resourceChangeEventStart() {
            mModifiedProjects.clear();
            mModifiedChildProjects.clear();
        }

        public void resourceChangeEventEnd() {
            if (mModifiedProjects.size() == 0) {
                return;
            }

            // first make sure all the parents are updated
            updateParentProjects();

            // for all modified projects, update their library list
            // and gather their IProject
            final List<IJavaProject> projectList = new ArrayList<IJavaProject>();
            for (ProjectState state : mModifiedProjects) {
                state.updateFullLibraryList();
                projectList.add(JavaCore.create(state.getProject()));
            }

            Job job = new Job("Android Library Update") { //$NON-NLS-1$
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    LibraryClasspathContainerInitializer.updateProjects(
                            projectList.toArray(new IJavaProject[projectList.size()]));

                    for (IJavaProject javaProject : projectList) {
                        try {
                            javaProject.getProject().build(IncrementalProjectBuilder.FULL_BUILD,
                                    monitor);
                        } catch (CoreException e) {
                            // pass
                        }
                    }
                    return Status.OK_STATUS;
                }
            };
            job.setPriority(Job.BUILD);
            job.schedule();
        }
    };

    /**
     * Updates all existing projects with a given list of new/updated libraries.
     * This loops through all opened projects and check if they depend on any of the given
     * library project, and if they do, they are linked together.
     * @param libraries the list of new/updated library projects.
     */
    private void updateParentProjects() {
        if (mModifiedChildProjects.size() == 0) {
            return;
        }

        ArrayList<ProjectState> childProjects = new ArrayList<ProjectState>(mModifiedChildProjects);
        mModifiedChildProjects.clear();
        synchronized (LOCK) {
            // for each project for which we must update its parent, we loop on the parent
            // projects and adds them to the list of modified projects. If they are themselves
            // libraries, we add them too.
            for (ProjectState state : childProjects) {
                if (DEBUG) {
                    System.out.println(">>> Updating parents of " + state.getProject().getName());
                }
                List<ProjectState> parents = state.getParentProjects();
                for (ProjectState parent : parents) {
                    markProject(parent, parent.isLibrary());
                }
                if (DEBUG) {
                    System.out.println("<<<");
                }
            }
        }

        // done, but there may be parents that are also libraries. Need to update their parents.
        updateParentProjects();
    }
}

