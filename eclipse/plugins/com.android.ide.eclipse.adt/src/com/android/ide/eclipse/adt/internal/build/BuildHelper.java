/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.build;

import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AndroidPrintStream;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs.BuildVerbosity;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.project.ProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.IAndroidTarget.IOptionalLibrary;
import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.IArchiveBuilder;
import com.android.sdklib.build.SealedApkException;
import com.android.sdklib.build.ApkBuilder.JarStatus;
import com.android.sdklib.build.ApkBuilder.SigningInfo;
import com.android.sdklib.internal.build.DebugKeyProvider;
import com.android.sdklib.internal.build.SignedJarBuilder;
import com.android.sdklib.internal.build.DebugKeyProvider.KeytoolException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceProxy;
import org.eclipse.core.resources.IResourceProxyVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.preference.IPreferenceStore;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Helper with methods for the last 3 steps of the generation of an APK.
 *
 * {@link #packageResources(IFile, IProject[], String, int, String, String)} packages the
 * application resources using aapt into a zip file that is ready to be integrated into the apk.
 *
 * {@link #executeDx(IJavaProject, String, String, IJavaProject[])} will convert the Java byte
 * code into the Dalvik bytecode.
 *
 * {@link #finalPackage(String, String, String, boolean, IJavaProject, IProject[], IJavaProject[], String, boolean)}
 * will make the apk from all the previous components.
 *
 * This class only executes the 3 above actions. It does not handle the errors, and simply sends
 * them back as custom exceptions.
 *
 * Warnings are handled by the {@link ResourceMarker} interface.
 *
 * Console output (verbose and non verbose) is handled through the {@link AndroidPrintStream} passed
 * to the constructor.
 *
 */
public class BuildHelper {

    private static final String CONSOLE_PREFIX_DX = "Dx";   //$NON-NLS-1$
    private final static String TEMP_PREFIX = "android_";   //$NON-NLS-1$

    private static final String COMMAND_CRUNCH = "crunch";  //$NON-NLS-1$
    private static final String COMMAND_PACKAGE = "package"; //$NON-NLS-1$

    private final IProject mProject;
    private final AndroidPrintStream mOutStream;
    private final AndroidPrintStream mErrStream;
    private final boolean mVerbose;
    private final boolean mDebugMode;

    public static final boolean BENCHMARK_FLAG = false;
    public static long sStartOverallTime = 0;
    public static long sStartJavaCTime = 0;

    private final static int MILLION = 1000000;

    /**
     * An object able to put a marker on a resource.
     */
    public interface ResourceMarker {
        void setWarning(IResource resource, String message);
    }

    /**
     * Creates a new post-compiler helper
     * @param project
     * @param outStream
     * @param errStream
     * @param debugMode whether this is a debug build
     * @param verbose
     */
    public BuildHelper(IProject project, AndroidPrintStream outStream,
            AndroidPrintStream errStream, boolean debugMode, boolean verbose) {
        mProject = project;
        mOutStream = outStream;
        mErrStream = errStream;
        mDebugMode = debugMode;
        mVerbose = verbose;
    }

    public void updateCrunchCache() throws AaptExecException, AaptResultException {
        // Benchmarking start
        long startCrunchTime = 0;
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Starting Initial Packaging (.ap_)"; //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
            startCrunchTime = System.nanoTime();
        }

        // Get the resources folder to crunch from
        IFolder resFolder = mProject.getFolder(AdtConstants.WS_RESOURCES);
        List<String> resPaths = new ArrayList<String>();
        resPaths.add(resFolder.getLocation().toOSString());

        // Get the output folder where the cache is stored.
        IFolder cacheFolder = mProject.getFolder(AdtConstants.WS_CRUNCHCACHE);
        String cachePath = cacheFolder.getLocation().toOSString();

        /* For crunching, we don't need the osManifestPath, osAssetsPath, or the configFilter
         * parameters for executeAapt
         */
        executeAapt(COMMAND_CRUNCH, "", resPaths, "", cachePath, "", 0);

        // Benchmarking end
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Ending Initial Package (.ap_). \nTime Elapsed: " //$NON-NLS-1$
                            + ((System.nanoTime() - startCrunchTime)/MILLION) + "ms";     //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
        }
    }

    public static void writeResources(IArchiveBuilder builder, IJavaProject javaProject)
            throws DuplicateFileException, ApkCreationException, SealedApkException, CoreException {
        writeStandardResources(builder, javaProject, null);
    }

    /**
     * Packages the resources of the projet into a .ap_ file.
     * @param manifestFile the manifest of the project.
     * @param libProjects the list of library projects that this project depends on.
     * @param resFilter an optional resource filter to be used with the -c option of aapt. If null
     * no filters are used.
     * @param versionCode an optional versionCode to be inserted in the manifest during packaging.
     * If the value is <=0, no values are inserted.
     * @param outputFolder where to write the resource ap_ file.
     * @param outputFilename the name of the resource ap_ file.
     * @throws AaptExecException
     * @throws AaptResultException
     */
    public void packageResources(IFile manifestFile, List<IProject> libProjects, String resFilter,
            int versionCode, String outputFolder, String outputFilename)
            throws AaptExecException, AaptResultException {

        // Benchmarking start
        long startPackageTime = 0;
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Starting Initial Packaging (.ap_)";    //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
            startPackageTime = System.nanoTime();
        }

        // need to figure out some path before we can execute aapt;

        // get the cache folder
        IFolder cacheFolder = mProject.getFolder(AdtConstants.WS_CRUNCHCACHE);

        // get the resource folder
        IFolder resFolder = mProject.getFolder(AdtConstants.WS_RESOURCES);

        // and the assets folder
        IFolder assetsFolder = mProject.getFolder(AdtConstants.WS_ASSETS);

        // we need to make sure this one exists.
        if (assetsFolder.exists() == false) {
            assetsFolder = null;
        }

        IPath cacheLocation = cacheFolder.getLocation();
        IPath resLocation = resFolder.getLocation();
        IPath manifestLocation = manifestFile.getLocation();

        if (resLocation != null && manifestLocation != null) {
            // list of res folder (main project + maybe libraries)
            ArrayList<String> osResPaths = new ArrayList<String>();
            osResPaths.add(cacheLocation.toOSString()); // PNG crunch cache
            osResPaths.add(resLocation.toOSString()); //main project

            // libraries?
            if (libProjects != null) {
                for (IProject lib : libProjects) {
                    IFolder libCacheFolder = lib.getFolder(AdtConstants.WS_CRUNCHCACHE);
                    if (libCacheFolder.exists()) {
                        osResPaths.add(libCacheFolder.getLocation().toOSString());
                    }
                    IFolder libResFolder = lib.getFolder(AdtConstants.WS_RESOURCES);
                    if (libResFolder.exists()) {
                        osResPaths.add(libResFolder.getLocation().toOSString());
                    }
                }
            }

            String osManifestPath = manifestLocation.toOSString();

            String osAssetsPath = null;
            if (assetsFolder != null) {
                osAssetsPath = assetsFolder.getLocation().toOSString();
            }

            // build the default resource package
            executeAapt(COMMAND_PACKAGE, osManifestPath, osResPaths, osAssetsPath,
                    outputFolder + File.separator + outputFilename, resFilter,
                    versionCode);
        }

        // Benchmarking end
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Ending Initial Package (.ap_). \nTime Elapsed: " //$NON-NLS-1$
                            + ((System.nanoTime() - startPackageTime)/MILLION) + "ms";    //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
        }
    }

    /**
     * Makes a final package signed with the debug key.
     *
     * Packages the dex files, the temporary resource file into the final package file.
     *
     * Whether the package is a debug package is controlled with the <var>debugMode</var> parameter
     * in {@link #PostCompilerHelper(IProject, PrintStream, PrintStream, boolean, boolean)}
     *
     * @param intermediateApk The path to the temporary resource file.
     * @param dex The path to the dex file.
     * @param output The path to the final package file to create.
     * @param javaProject the java project being compiled
     * @param libProjects an optional list of library projects (can be null)
     * @param referencedJavaProjects referenced projects.
     * @return true if success, false otherwise.
     * @throws ApkCreationException
     * @throws AndroidLocationException
     * @throws KeytoolException
     * @throws NativeLibInJarException
     * @throws CoreException
     * @throws DuplicateFileException
     */
    public void finalDebugPackage(String intermediateApk, String dex, String output,
            final IJavaProject javaProject, List<IProject> libProjects,
            List<IJavaProject> referencedJavaProjects, ResourceMarker resMarker)
            throws ApkCreationException, KeytoolException, AndroidLocationException,
            NativeLibInJarException, DuplicateFileException, CoreException {

        AdtPlugin adt = AdtPlugin.getDefault();
        if (adt == null) {
            return;
        }

        // get the debug keystore to use.
        IPreferenceStore store = adt.getPreferenceStore();
        String keystoreOsPath = store.getString(AdtPrefs.PREFS_CUSTOM_DEBUG_KEYSTORE);
        if (keystoreOsPath == null || new File(keystoreOsPath).isFile() == false) {
            keystoreOsPath = DebugKeyProvider.getDefaultKeyStoreOsPath();
            AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, mProject,
                    Messages.ApkBuilder_Using_Default_Key);
        } else {
            AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE, mProject,
                    String.format(Messages.ApkBuilder_Using_s_To_Sign, keystoreOsPath));
        }

        // from the keystore, get the signing info
        SigningInfo info = ApkBuilder.getDebugKey(keystoreOsPath, mVerbose ? mOutStream : null);

        finalPackage(intermediateApk, dex, output, javaProject, libProjects,
                referencedJavaProjects,
                info != null ? info.key : null, info != null ? info.certificate : null, resMarker);
    }

    /**
     * Makes the final package.
     *
     * Packages the dex files, the temporary resource file into the final package file.
     *
     * Whether the package is a debug package is controlled with the <var>debugMode</var> parameter
     * in {@link #PostCompilerHelper(IProject, PrintStream, PrintStream, boolean, boolean)}
     *
     * @param intermediateApk The path to the temporary resource file.
     * @param dex The path to the dex file.
     * @param output The path to the final package file to create.
     * @param debugSign whether the apk must be signed with the debug key.
     * @param javaProject the java project being compiled
     * @param libProjects an optional list of library projects (can be null)
     * @param referencedJavaProjects referenced projects.
     * @param abiFilter an optional filter. If not null, then only the matching ABI is included in
     * the final archive
     * @return true if success, false otherwise.
     * @throws NativeLibInJarException
     * @throws ApkCreationException
     * @throws CoreException
     * @throws DuplicateFileException
     */
    public void finalPackage(String intermediateApk, String dex, String output,
            final IJavaProject javaProject, List<IProject> libProjects,
            List<IJavaProject> referencedJavaProjects, PrivateKey key,
            X509Certificate certificate, ResourceMarker resMarker)
            throws NativeLibInJarException, ApkCreationException, DuplicateFileException,
            CoreException {

        try {
            ApkBuilder apkBuilder = new ApkBuilder(output, intermediateApk, dex,
                    key, certificate,
                    mVerbose ? mOutStream: null);
            apkBuilder.setDebugMode(mDebugMode);

            // Now we write the standard resources from the project and the referenced projects.
            writeStandardResources(apkBuilder, javaProject, referencedJavaProjects);

            // Now we write the standard resources from the external jars
            for (String libraryOsPath : getExternalDependencies(resMarker)) {
                File libFile = new File(libraryOsPath);
                if (libFile.isFile()) {
                    JarStatus jarStatus = apkBuilder.addResourcesFromJar(new File(libraryOsPath));

                    // check if we found native libraries in the external library. This
                    // constitutes an error or warning depending on if they are in lib/
                    if (jarStatus.getNativeLibs().size() > 0) {
                        String libName = new File(libraryOsPath).getName();

                        String msg = String.format(
                                "Native libraries detected in '%1$s'. See console for more information.",
                                libName);

                        ArrayList<String> consoleMsgs = new ArrayList<String>();

                        consoleMsgs.add(String.format(
                                "The library '%1$s' contains native libraries that will not run on the device.",
                                libName));

                        if (jarStatus.hasNativeLibsConflicts()) {
                            consoleMsgs.add("Additionally some of those libraries will interfer with the installation of the application because of their location in lib/");
                            consoleMsgs.add("lib/ is reserved for NDK libraries.");
                        }

                        consoleMsgs.add("The following libraries were found:");

                        for (String lib : jarStatus.getNativeLibs()) {
                            consoleMsgs.add(" - " + lib);
                        }

                        String[] consoleStrings = consoleMsgs.toArray(new String[consoleMsgs.size()]);

                        // if there's a conflict or if the prefs force error on any native code in jar
                        // files, throw an exception
                        if (jarStatus.hasNativeLibsConflicts() ||
                                AdtPrefs.getPrefs().getBuildForceErrorOnNativeLibInJar()) {
                            throw new NativeLibInJarException(jarStatus, msg, libName, consoleStrings);
                        } else {
                            // otherwise, put a warning, and output to the console also.
                            if (resMarker != null) {
                                resMarker.setWarning(mProject, msg);
                            }

                            for (String string : consoleStrings) {
                                mOutStream.println(string);
                            }
                        }
                    }
                } else if (libFile.isDirectory()) {
                    // this is technically not a source folder (class folder instead) but since we
                    // only care about Java resources (ie non class/java files) this will do the
                    // same
                    apkBuilder.addSourceFolder(libFile);
                }
            }

            // now write the native libraries.
            // First look if the lib folder is there.
            IResource libFolder = mProject.findMember(SdkConstants.FD_NATIVE_LIBS);
            if (libFolder != null && libFolder.exists() &&
                    libFolder.getType() == IResource.FOLDER) {
                // get a File for the folder.
                apkBuilder.addNativeLibraries(libFolder.getLocation().toFile());
            }

            // write the native libraries for the library projects.
            if (libProjects != null) {
                for (IProject lib : libProjects) {
                    libFolder = lib.findMember(SdkConstants.FD_NATIVE_LIBS);
                    if (libFolder != null && libFolder.exists() &&
                            libFolder.getType() == IResource.FOLDER) {
                        apkBuilder.addNativeLibraries(libFolder.getLocation().toFile());
                    }
                }
            }

            // seal the APK.
            apkBuilder.sealApk();
        } catch (SealedApkException e) {
            // this won't happen as we control when the apk is sealed.
        }
    }

    /**
     * Return a list of the project output for compiled Java code.
     * @return
     * @throws CoreException
     */
    public String[] getProjectJavaOutputs() throws CoreException {
        IFolder outputFolder = BaseProjectHelper.getJavaOutputFolder(mProject);

        // get the list of referenced projects output to add
        List<IProject> javaProjects = ProjectHelper.getReferencedProjects(mProject);
        List<IJavaProject> referencedJavaProjects = BuildHelper.getJavaProjects(javaProjects);

        // get the project output, and since it's a new list object, just add the outputFolder
        // of the project directly to it.
        List<String> projectOutputs = getProjectJavaOutputs(referencedJavaProjects);

        projectOutputs.add(0, outputFolder.getLocation().toOSString());

        return projectOutputs.toArray(new String[projectOutputs.size()]);
    }

    /**
     * Returns an array for all the compiled code for the project. This can include the
     * code compiled by Eclipse for the main project and dependencies (Java only projects), as well
     * as external jars used by the project or its library.
     *
     * This array of paths is compatible with the input for dx and can be passed as is to
     * {@link #executeDx(IJavaProject, String[], String)}.
     *
     * @param resMarker
     * @return a array (never empty) containing paths to compiled code.
     * @throws CoreException
     */
    public String[] getCompiledCodePaths(boolean includeProjectOutputs, ResourceMarker resMarker)
            throws CoreException {

        // get the list of libraries to include with the source code
        String[] libraries = getExternalDependencies(resMarker);

        int startIndex = 0;

        String[] compiledPaths;

        if (includeProjectOutputs) {
            String[] projectOutputs = getProjectJavaOutputs();

            compiledPaths = new String[libraries.length + projectOutputs.length];

            System.arraycopy(projectOutputs, 0, compiledPaths, 0, projectOutputs.length);
            startIndex = projectOutputs.length;
        } else {
            compiledPaths = new String[libraries.length];
        }

        System.arraycopy(libraries, 0, compiledPaths, startIndex, libraries.length);

        return compiledPaths;
    }

    public void runProguard(File proguardConfig, File inputJar, String[] jarFiles,
                            File obfuscatedJar, File logOutput)
            throws ProguardResultException, ProguardExecException, IOException {
        IAndroidTarget target = Sdk.getCurrent().getTarget(mProject);

        // prepare the command line for proguard
        List<String> command = new ArrayList<String>();
        command.add(AdtPlugin.getOsAbsoluteProguard());

        command.add("@" + quotePath(proguardConfig.getAbsolutePath())); //$NON-NLS-1$

        command.add("-injars"); //$NON-NLS-1$
        StringBuilder sb = new StringBuilder(quotePath(inputJar.getAbsolutePath()));
        for (String jarFile : jarFiles) {
            sb.append(File.pathSeparatorChar);
            sb.append(quotePath(jarFile));
        }
        command.add(quoteWinArg(sb.toString()));

        command.add("-outjars"); //$NON-NLS-1$
        command.add(quotePath(obfuscatedJar.getAbsolutePath()));

        command.add("-libraryjars"); //$NON-NLS-1$
        sb = new StringBuilder(quotePath(target.getPath(IAndroidTarget.ANDROID_JAR)));
        IOptionalLibrary[] libraries = target.getOptionalLibraries();
        if (libraries != null) {
            for (IOptionalLibrary lib : libraries) {
                sb.append(File.pathSeparatorChar);
                sb.append(quotePath(lib.getJarPath()));
            }
        }
        command.add(quoteWinArg(sb.toString()));

        if (logOutput != null) {
            if (logOutput.isDirectory() == false) {
                logOutput.mkdirs();
            }

            command.add("-dump");                                              //$NON-NLS-1$
            command.add(new File(logOutput, "dump.txt").getAbsolutePath());    //$NON-NLS-1$

            command.add("-printseeds");                                        //$NON-NLS-1$
            command.add(new File(logOutput, "seeds.txt").getAbsolutePath());   //$NON-NLS-1$

            command.add("-printusage");                                        //$NON-NLS-1$
            command.add(new File(logOutput, "usage.txt").getAbsolutePath());   //$NON-NLS-1$

            command.add("-printmapping");                                      //$NON-NLS-1$
            command.add(new File(logOutput, "mapping.txt").getAbsolutePath()); //$NON-NLS-1$
        }

        String commandArray[] = null;

        if (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            commandArray = createWindowsProguardConfig(command);
        }

        if (commandArray == null) {
            // For Mac & Linux, use a regular command string array.
            commandArray = command.toArray(new String[command.size()]);
        }

        // Define PROGUARD_HOME to point to $SDK/tools/proguard if it's not yet defined.
        // The Mac/Linux proguard.sh can infer it correctly but not the proguard.bat one.
        String[] envp = null;
        Map<String, String> envMap = new TreeMap<String, String>(System.getenv());
        if (!envMap.containsKey("PROGUARD_HOME")) {                                    //$NON-NLS-1$
            envMap.put("PROGUARD_HOME",    Sdk.getCurrent().getSdkLocation() +         //$NON-NLS-1$
                                            SdkConstants.FD_TOOLS + File.separator +
                                            SdkConstants.FD_PROGUARD);
            envp = new String[envMap.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : envMap.entrySet()) {
                envp[i++] = String.format("%1$s=%2$s",                                 //$NON-NLS-1$
                                          entry.getKey(),
                                          entry.getValue());
            }
        }

        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            sb = new StringBuilder();
            for (String c : commandArray) {
                sb.append(c).append(' ');
            }
            AdtPlugin.printToConsole(mProject, sb.toString());
        }

        // launch
        int execError = 1;
        try {
            // launch the command line process
            Process process = Runtime.getRuntime().exec(commandArray, envp);

            // list to store each line of stderr
            ArrayList<String> results = new ArrayList<String>();

            // get the output and return code from the process
            execError = grabProcessOutput(mProject, process, results);

            if (mVerbose) {
                for (String resultString : results) {
                    mOutStream.println(resultString);
                }
            }

            if (execError != 0) {
                throw new ProguardResultException(execError,
                        results.toArray(new String[results.size()]));
            }

        } catch (IOException e) {
            String msg = String.format(Messages.Proguard_Exec_Error, commandArray[0]);
            throw new ProguardExecException(msg, e);
        } catch (InterruptedException e) {
            String msg = String.format(Messages.Proguard_Exec_Error, commandArray[0]);
            throw new ProguardExecException(msg, e);
        }
    }

    /**
     * For tools R8 up to R11, the proguard.bat launcher on Windows only accepts
     * arguments %1..%9. Since we generally have about 15 arguments, we were working
     * around this by generating a temporary config file for proguard and then using
     * that.
     * Starting with tools R12, the proguard.bat launcher has been fixed to take
     * all arguments using %* so we no longer need this hack.
     *
     * @param command
     * @return
     * @throws IOException
     */
    private String[] createWindowsProguardConfig(List<String> command) throws IOException {

        // Arg 0 is the proguard.bat path and arg 1 is the user config file
        String launcher = AdtPlugin.readFile(new File(command.get(0)));
        if (launcher.contains("%*")) {                                      //$NON-NLS-1$
            // This is the launcher from Tools R12. Don't work around it.
            return null;
        }

        // On Windows, proguard.bat can only pass %1...%9 to the java -jar proguard.jar
        // call, but we have at least 15 arguments here so some get dropped silently
        // and quoting is a big issue. So instead we'll work around that by writing
        // all the arguments to a temporary config file.

        String[] commandArray = new String[3];

        commandArray[0] = command.get(0);
        commandArray[1] = command.get(1);

        // Write all the other arguments to a config file
        File argsFile = File.createTempFile(TEMP_PREFIX, ".pro");           //$NON-NLS-1$
        // TODO FIXME this may leave a lot of temp files around on a long session.
        // Should have a better way to clean up e.g. before each build.
        argsFile.deleteOnExit();

        FileWriter fw = new FileWriter(argsFile);

        for (int i = 2; i < command.size(); i++) {
            String s = command.get(i);
            fw.write(s);
            fw.write(s.startsWith("-") ? ' ' : '\n');                       //$NON-NLS-1$
        }

        fw.close();

        commandArray[2] = "@" + argsFile.getAbsolutePath();                 //$NON-NLS-1$
        return commandArray;
    }

    /**
     * Quotes a single path for proguard to deal with spaces.
     *
     * @param path The path to quote.
     * @return The original path if it doesn't contain a space.
     *   Or the original path surrounded by single quotes if it contains spaces.
     */
    private String quotePath(String path) {
        if (path.indexOf(' ') != -1) {
            path = '\'' + path + '\'';
        }
        return path;
    }

    /**
     * Quotes a compound proguard argument to deal with spaces.
     * <p/>
     * Proguard takes multi-path arguments such as "path1;path2" for some options.
     * When the {@link #quotePath} methods adds quotes for such a path if it contains spaces,
     * the proguard shell wrapper will absorb the quotes, so we need to quote around the
     * quotes.
     *
     * @param path The path to quote.
     * @return The original path if it doesn't contain a single quote.
     *   Or on Windows the original path surrounded by double quotes if it contains a quote.
     */
    private String quoteWinArg(String path) {
        if (path.indexOf('\'') != -1 &&
                SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS) {
            path = '"' + path + '"';
        }
        return path;
    }


    /**
     * Execute the Dx tool for dalvik code conversion.
     * @param javaProject The java project
     * @param inputPath the path to the main input of dex
     * @param osOutFilePath the path of the dex file to create.
     *
     * @throws CoreException
     * @throws DexException
     */
    public void executeDx(IJavaProject javaProject, String[] inputPaths, String osOutFilePath)
            throws CoreException, DexException {

        // get the dex wrapper
        Sdk sdk = Sdk.getCurrent();
        DexWrapper wrapper = sdk.getDexWrapper();

        if (wrapper == null) {
            throw new CoreException(new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                    Messages.ApkBuilder_UnableBuild_Dex_Not_loaded));
        }

        try {
            // set a temporary prefix on the print streams.
            mOutStream.setPrefix(CONSOLE_PREFIX_DX);
            mErrStream.setPrefix(CONSOLE_PREFIX_DX);

            int res = wrapper.run(osOutFilePath,
                    inputPaths,
                    mVerbose,
                    mOutStream, mErrStream);

            mOutStream.setPrefix(null);
            mErrStream.setPrefix(null);

            if (res != 0) {
                // output error message and marker the project.
                String message = String.format(Messages.Dalvik_Error_d, res);
                throw new DexException(message);
            }
        } catch (DexException e) {
            throw e;
        } catch (Throwable t) {
            String message = t.getMessage();
            if (message == null) {
                message = t.getClass().getCanonicalName();
            }
            message = String.format(Messages.Dalvik_Error_s, message);

            throw new DexException(message, t);
        }
    }

    /**
     * Executes aapt. If any error happen, files or the project will be marked.
     * @param command The command for aapt to execute. Currently supported: package and crunch
     * @param osManifestPath The path to the manifest file
     * @param osResPath The path to the res folder
     * @param osAssetsPath The path to the assets folder. This can be null.
     * @param osOutFilePath The path to the temporary resource file to create,
     *   or in the case of crunching the path to the cache to create/update.
     * @param configFilter The configuration filter for the resources to include
     * (used with -c option, for example "port,en,fr" to include portrait, English and French
     * resources.)
     * @param versionCode optional version code to insert in the manifest during packaging. If <=0
     * then no value is inserted
     * @throws AaptExecException
     * @throws AaptResultException
     */
    private void executeAapt(String aaptCommand, String osManifestPath,
            List<String> osResPaths, String osAssetsPath, String osOutFilePath,
            String configFilter, int versionCode) throws AaptExecException, AaptResultException {
        IAndroidTarget target = Sdk.getCurrent().getTarget(mProject);

        // Create the command line.
        ArrayList<String> commandArray = new ArrayList<String>();
        commandArray.add(target.getPath(IAndroidTarget.AAPT));
        commandArray.add(aaptCommand);
        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            commandArray.add("-v"); //$NON-NLS-1$
        }

        // Common to all commands
        for (String path : osResPaths) {
            commandArray.add("-S"); //$NON-NLS-1$
            commandArray.add(path);
        }

        if (aaptCommand.equals(COMMAND_PACKAGE)) {
            commandArray.add("-f");          //$NON-NLS-1$
            commandArray.add("--no-crunch"); //$NON-NLS-1$

            // if more than one res, this means there's a library (or more) and we need
            // to activate the auto-add-overlay
            if (osResPaths.size() > 1) {
                commandArray.add("--auto-add-overlay"); //$NON-NLS-1$
            }

            if (mDebugMode) {
                commandArray.add("--debug-mode"); //$NON-NLS-1$
            }

            if (versionCode > 0) {
                commandArray.add("--version-code"); //$NON-NLS-1$
                commandArray.add(Integer.toString(versionCode));
            }

            if (configFilter != null) {
                commandArray.add("-c"); //$NON-NLS-1$
                commandArray.add(configFilter);
            }

            commandArray.add("-M"); //$NON-NLS-1$
            commandArray.add(osManifestPath);

            if (osAssetsPath != null) {
                commandArray.add("-A"); //$NON-NLS-1$
                commandArray.add(osAssetsPath);
            }

            commandArray.add("-I"); //$NON-NLS-1$
            commandArray.add(target.getPath(IAndroidTarget.ANDROID_JAR));

            commandArray.add("-F"); //$NON-NLS-1$
            commandArray.add(osOutFilePath);
        } else if (aaptCommand.equals(COMMAND_CRUNCH)) {
            commandArray.add("-C"); //$NON-NLS-1$
            commandArray.add(osOutFilePath);
        }

        String command[] = commandArray.toArray(
                new String[commandArray.size()]);

        if (AdtPrefs.getPrefs().getBuildVerbosity() == BuildVerbosity.VERBOSE) {
            StringBuilder sb = new StringBuilder();
            for (String c : command) {
                sb.append(c);
                sb.append(' ');
            }
            AdtPlugin.printToConsole(mProject, sb.toString());
        }

        // Benchmarking start
        long startAaptTime = 0;
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Starting " + aaptCommand  //$NON-NLS-1$
                         + " call to Aapt";                        //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
            startAaptTime = System.nanoTime();
        }

        // launch
        int execError = 1;
        try {
            // launch the command line process
            Process process = Runtime.getRuntime().exec(command);

            // list to store each line of stderr
            ArrayList<String> results = new ArrayList<String>();

            // get the output and return code from the process
            execError = grabProcessOutput(mProject, process, results);

            if (mVerbose) {
                for (String resultString : results) {
                    mOutStream.println(resultString);
                }
            }
            if (execError != 0) {
                throw new AaptResultException(execError,
                        results.toArray(new String[results.size()]));
            }
        } catch (IOException e) {
            String msg = String.format(Messages.AAPT_Exec_Error, command[0]);
            throw new AaptExecException(msg, e);
        } catch (InterruptedException e) {
            String msg = String.format(Messages.AAPT_Exec_Error, command[0]);
            throw new AaptExecException(msg, e);
        }

        // Benchmarking end
        if (BENCHMARK_FLAG) {
            String msg = "BENCHMARK ADT: Ending " + aaptCommand                  //$NON-NLS-1$
                         + " call to Aapt.\nBENCHMARK ADT: Time Elapsed: "       //$NON-NLS-1$
                         + ((System.nanoTime() - startAaptTime)/MILLION) + "ms"; //$NON-NLS-1$
            AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS, mProject, msg);
        }
    }

    /**
     * Writes the standard resources of a project and its referenced projects
     * into a {@link SignedJarBuilder}.
     * Standard resources are non java/aidl files placed in the java package folders.
     * @param builder the archive builder.
     * @param javaProject the javaProject object.
     * @param referencedJavaProjects the java projects that this project references.
     * @throws ApkCreationException if an error occurred
     * @throws SealedApkException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     * @throws CoreException
     */
    private static void writeStandardResources(IArchiveBuilder builder, IJavaProject javaProject,
            List<IJavaProject> referencedJavaProjects)
            throws DuplicateFileException, ApkCreationException, SealedApkException,
            CoreException  {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot wsRoot = ws.getRoot();

        writeStandardProjectResources(builder, javaProject, wsRoot);

        if (referencedJavaProjects != null) {
            for (IJavaProject referencedJavaProject : referencedJavaProjects) {
                // only include output from non android referenced project
                // (This is to handle the case of reference Android projects in the context of
                // instrumentation projects that need to reference the projects to be tested).
                if (referencedJavaProject.getProject().hasNature(
                        AdtConstants.NATURE_DEFAULT) == false) {
                    writeStandardProjectResources(builder, referencedJavaProject, wsRoot);
                }
            }
        }
    }

    /**
     * Writes the standard resources of a {@link IJavaProject} into a {@link SignedJarBuilder}.
     * Standard resources are non java/aidl files placed in the java package folders.
     * @param jarBuilder the {@link ApkBuilder}.
     * @param javaProject the javaProject object.
     * @param wsRoot the {@link IWorkspaceRoot}.
     * @throws ApkCreationException if an error occurred
     * @throws SealedApkException if the APK is already sealed.
     * @throws DuplicateFileException if a file conflicts with another already added to the APK
     *                                   at the same location inside the APK archive.
     * @throws CoreException
     */
    private static void writeStandardProjectResources(IArchiveBuilder builder,
            IJavaProject javaProject, IWorkspaceRoot wsRoot)
            throws DuplicateFileException, ApkCreationException, SealedApkException, CoreException {
        // get the source pathes
        List<IPath> sourceFolders = BaseProjectHelper.getSourceClasspaths(javaProject);

        // loop on them and then recursively go through the content looking for matching files.
        for (IPath sourcePath : sourceFolders) {
            IResource sourceResource = wsRoot.findMember(sourcePath);
            if (sourceResource != null && sourceResource.getType() == IResource.FOLDER) {
                writeFolderResources(builder, javaProject, (IFolder) sourceResource);
            }
        }
    }

    private static void writeFolderResources(IArchiveBuilder builder,
            final IJavaProject javaProject, IFolder root) throws CoreException,
            ApkCreationException, SealedApkException, DuplicateFileException {
        final List<IPath> pathsToPackage = new ArrayList<IPath>();
        root.accept(new IResourceProxyVisitor() {
            public boolean visit(IResourceProxy proxy) throws CoreException {
                if (proxy.getType() == IResource.FOLDER) {
                    // If this folder isn't wanted, don't traverse into it.
                    return ApkBuilder.checkFolderForPackaging(proxy.getName());
                }
                // If it's not a folder, it must be a file.  We won't see any other resource type.
                if (!ApkBuilder.checkFileForPackaging(proxy.getName())) {
                    return true;
                }
                IResource res = proxy.requestResource();
                if (!javaProject.isOnClasspath(res)) {
                    return true;
                }
                // Just record that we need to package this.  Packaging here throws
                // inappropriate checked exceptions.
                IPath location = res.getLocation();
                pathsToPackage.add(location);
                return true;
            }
        }, 0);
        IPath rootLocation = root.getLocation();
        for (IPath path : pathsToPackage) {
            IPath archivePath = path.makeRelativeTo(rootLocation);
            builder.addFile(path.toFile(), archivePath.toString());
        }
    }

    /**
     * Returns an array of external dependencies used the project. This can be paths to jar files
     * or to source folders.
     *
     * @param resMarker if non null, used to put Resource marker on problem files.
     * @return an array of OS-specific absolute file paths
     */
    private final String[] getExternalDependencies(ResourceMarker resMarker) {
        // get a java project from it
        IJavaProject javaProject = JavaCore.create(mProject);

        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();

        ArrayList<String> oslibraryList = new ArrayList<String>();

        // we could use IJavaProject.getResolvedClasspath directly, but we actually
        // want to see the containers themselves.
        IClasspathEntry[] classpaths = javaProject.readRawClasspath();
        if (classpaths != null) {
            for (IClasspathEntry e : classpaths) {
                // if this is a classpath variable reference, we resolve it.
                if (e.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
                    e = JavaCore.getResolvedClasspathEntry(e);
                }

                if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                    handleClasspathEntry(e, wsRoot, oslibraryList, resMarker);
                } else if (e.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                    // get the container
                    try {
                        IClasspathContainer container = JavaCore.getClasspathContainer(
                                e.getPath(), javaProject);
                        // ignore the system and default_system types as they represent
                        // libraries that are part of the runtime.
                        if (container.getKind() == IClasspathContainer.K_APPLICATION) {
                            IClasspathEntry[] entries = container.getClasspathEntries();
                            for (IClasspathEntry entry : entries) {
                                handleClasspathEntry(entry, wsRoot, oslibraryList, resMarker);
                            }
                        }
                    } catch (JavaModelException jme) {
                        // can't resolve the container? ignore it.
                        AdtPlugin.log(jme, "Failed to resolve ClasspathContainer: %s", e.getPath());
                    }
                }
            }
        }

        return oslibraryList.toArray(new String[oslibraryList.size()]);
    }

    private void handleClasspathEntry(IClasspathEntry e, IWorkspaceRoot wsRoot,
            ArrayList<String> oslibraryList, ResourceMarker resMarker) {
        // get the IPath
        IPath path = e.getPath();

        IResource resource = wsRoot.findMember(path);
        // case of a jar file (which could be relative to the workspace or a full path)
        if (AdtConstants.EXT_JAR.equalsIgnoreCase(path.getFileExtension())) {
            if (resource != null && resource.exists() &&
                    resource.getType() == IResource.FILE) {
                oslibraryList.add(resource.getLocation().toOSString());
            } else {
                // if the jar path doesn't match a workspace resource,
                // then we get an OSString and check if this links to a valid file.
                String osFullPath = path.toOSString();

                File f = new File(osFullPath);
                if (f.isFile()) {
                    oslibraryList.add(osFullPath);
                } else {
                    String message = String.format( Messages.Couldnt_Locate_s_Error,
                            path);
                    // always output to the console
                    mOutStream.println(message);

                    // put a marker
                    if (resMarker != null) {
                        resMarker.setWarning(mProject, message);
                    }
                }
            }
        } else {
            // this can be the case for a class folder.
            if (resource != null && resource.exists() &&
                    resource.getType() == IResource.FOLDER) {
                oslibraryList.add(resource.getLocation().toOSString());
            } else {
                // if the path doesn't match a workspace resource,
                // then we get an OSString and check if this links to a valid folder.
                String osFullPath = path.toOSString();

                File f = new File(osFullPath);
                if (f.isDirectory()) {
                    oslibraryList.add(osFullPath);
                }
            }
        }
    }

    /**
     * Returns the list of the output folders for the specified {@link IJavaProject} objects, if
     * they are Android projects.
     *
     * @param referencedJavaProjects the java projects.
     * @return a new list object containing the output folder paths.
     * @throws CoreException
     */
    private List<String> getProjectJavaOutputs(List<IJavaProject> referencedJavaProjects)
            throws CoreException {
        ArrayList<String> list = new ArrayList<String>();

        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceRoot wsRoot = ws.getRoot();

        for (IJavaProject javaProject : referencedJavaProjects) {
            // only include output from non android referenced project
            // (This is to handle the case of reference Android projects in the context of
            // instrumentation projects that need to reference the projects to be tested).
            if (javaProject.getProject().hasNature(AdtConstants.NATURE_DEFAULT) == false) {
                // get the output folder
                IPath path = null;
                try {
                    path = javaProject.getOutputLocation();
                } catch (JavaModelException e) {
                    continue;
                }

                IResource outputResource = wsRoot.findMember(path);
                if (outputResource != null && outputResource.getType() == IResource.FOLDER) {
                    String outputOsPath = outputResource.getLocation().toOSString();

                    list.add(outputOsPath);
                }
            }
        }

        return list;
    }

    /**
     * Checks a {@link IFile} to make sure it should be packaged as standard resources.
     * @param file the IFile representing the file.
     * @return true if the file should be packaged as standard java resources.
     */
    public static boolean checkFileForPackaging(IFile file) {
        String name = file.getName();

        String ext = file.getFileExtension();
        return ApkBuilder.checkFileForPackaging(name, ext);
    }

    /**
     * Checks whether an {@link IFolder} and its content is valid for packaging into the .apk as
     * standard Java resource.
     * @param folder the {@link IFolder} to check.
     */
    public static boolean checkFolderForPackaging(IFolder folder) {
        String name = folder.getName();
        return ApkBuilder.checkFolderForPackaging(name);
    }

    /**
     * Returns a list of {@link IJavaProject} matching the provided {@link IProject} objects.
     * @param projects the IProject objects.
     * @return a new list object containing the IJavaProject object for the given IProject objects.
     * @throws CoreException
     */
    public static List<IJavaProject> getJavaProjects(List<IProject> projects) throws CoreException {
        ArrayList<IJavaProject> list = new ArrayList<IJavaProject>();

        for (IProject p : projects) {
            if (p.isOpen() && p.hasNature(JavaCore.NATURE_ID)) {

                list.add(JavaCore.create(p));
            }
        }

        return list;
    }

    /**
     * Get the stderr output of a process and return when the process is done.
     * @param process The process to get the ouput from
     * @param results The array to store the stderr output
     * @return the process return code.
     * @throws InterruptedException
     */
    public final static int grabProcessOutput(final IProject project, final Process process,
            final ArrayList<String> results)
            throws InterruptedException {
        // Due to the limited buffer size on windows for the standard io (stderr, stdout), we
        // *need* to read both stdout and stderr all the time. If we don't and a process output
        // a large amount, this could deadlock the process.

        // read the lines as they come. if null is returned, it's
        // because the process finished
        new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                // create a buffer to read the stderr output
                InputStreamReader is = new InputStreamReader(process.getErrorStream());
                BufferedReader errReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = errReader.readLine();
                        if (line != null) {
                            results.add(line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }.start();

        new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                InputStreamReader is = new InputStreamReader(process.getInputStream());
                BufferedReader outReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = outReader.readLine();
                        if (line != null) {
                            // If benchmarking always print the lines that
                            // correspond to benchmarking info returned by ADT
                            if (BENCHMARK_FLAG && line.startsWith("BENCHMARK:")) {    //$NON-NLS-1$
                                AdtPlugin.printBuildToConsole(BuildVerbosity.ALWAYS,
                                        project, line);
                            } else {
                                AdtPlugin.printBuildToConsole(BuildVerbosity.VERBOSE,
                                        project, line);
                            }
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }

        }.start();

        // get the return code from the process
        return process.waitFor();
    }
}
