/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.manifest;

import static com.android.ide.common.resources.ResourceResolver.PREFIX_ANDROID_STYLE;
import static com.android.sdklib.SdkConstants.NS_RESOURCES;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_ICON;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_LABEL;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_MIN_SDK_VERSION;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_NAME;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_PACKAGE;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_TARGET_SDK_VERSION;
import static com.android.sdklib.xml.AndroidManifest.ATTRIBUTE_THEME;
import static com.android.sdklib.xml.AndroidManifest.NODE_ACTIVITY;
import static com.android.sdklib.xml.AndroidManifest.NODE_USES_SDK;
import static org.eclipse.jdt.core.search.IJavaSearchConstants.REFERENCES;

import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.io.IFolderWrapper;
import com.android.io.IAbstractFile;
import com.android.io.StreamException;
import com.android.resources.ScreenSize;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.editors.text.TextFileDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathExpressionException;

/**
 * Retrieves and caches manifest information such as the themes to be used for
 * a given activity.
 *
 * @see AndroidManifest
 */
public class ManifestInfo {
    /**
     * The maximum number of milliseconds to search for an activity in the codebase when
     * attempting to associate layouts with activities in
     * {@link #guessActivity(IFile, String)}
     */
    private static final int SEARCH_TIMEOUT_MS = 3000;

    private final IProject mProject;
    private String mPackage;
    private String mManifestTheme;
    private Map<String, String> mActivityThemes;
    private IAbstractFile mManifestFile;
    private long mLastModified;
    private int mTargetSdk;
    private String mApplicationIcon;
    private String mApplicationLabel;

    /**
     * Qualified name for the per-project non-persistent property storing the
     * {@link ManifestInfo} for this project
     */
    final static QualifiedName MANIFEST_FINDER = new QualifiedName(AdtPlugin.PLUGIN_ID,
            "manifest"); //$NON-NLS-1$

    /**
     * Constructs an {@link ManifestInfo} for the given project. Don't use this method;
     * use the {@link #get} factory method instead.
     *
     * @param project project to create an {@link ManifestInfo} for
     */
    private ManifestInfo(IProject project) {
        mProject = project;
    }

    /**
     * Returns the {@link ManifestInfo} for the given project
     *
     * @param project the project the finder is associated with
     * @return a {@ManifestInfo} for the given project, never null
     */
    public static ManifestInfo get(IProject project) {
        ManifestInfo finder = null;
        try {
            finder = (ManifestInfo) project.getSessionProperty(MANIFEST_FINDER);
        } catch (CoreException e) {
            // Not a problem; we will just create a new one
        }

        if (finder == null) {
            finder = new ManifestInfo(project);
            try {
                project.setSessionProperty(MANIFEST_FINDER, finder);
            } catch (CoreException e) {
                AdtPlugin.log(e, "Can't store ManifestInfo");
            }
        }

        return finder;
    }

    /**
     * Ensure that the package, theme and activity maps are initialized and up to date
     * with respect to the manifest file
     */
    private void sync() {
        if (mManifestFile == null) {
            IFolderWrapper projectFolder = new IFolderWrapper(mProject);
            mManifestFile = AndroidManifest.getManifest(projectFolder);
            if (mManifestFile == null) {
                return;
            }
        }

        // Check to see if our data is up to date
        long fileModified = mManifestFile.getModificationStamp();
        if (fileModified == mLastModified) {
            // Already have up to date data
            return;
        }
        mLastModified = fileModified;

        mActivityThemes = new HashMap<String, String>();
        mManifestTheme = null;
        mTargetSdk = 1; // Default when not specified
        mPackage = ""; //$NON-NLS-1$
        mApplicationIcon = null;
        mApplicationLabel = null;

        Document document = null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            InputSource is = new InputSource(mManifestFile.getContents());

            factory.setNamespaceAware(true);
            factory.setValidating(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(is);

            Element root = document.getDocumentElement();
            mPackage = root.getAttribute(ATTRIBUTE_PACKAGE);
            NodeList activities = document.getElementsByTagName(NODE_ACTIVITY);
            for (int i = 0, n = activities.getLength(); i < n; i++) {
                Element activity = (Element) activities.item(i);
                String theme = activity.getAttributeNS(NS_RESOURCES, ATTRIBUTE_THEME);
                if (theme != null && theme.length() > 0) {
                    String name = activity.getAttributeNS(NS_RESOURCES, ATTRIBUTE_NAME);
                    if (name.startsWith(".")  //$NON-NLS-1$
                            && mPackage != null && mPackage.length() > 0) {
                        name = mPackage + name;
                    }
                    mActivityThemes.put(name, theme);
                }
            }

            NodeList applications = root.getElementsByTagName(AndroidManifest.NODE_APPLICATION);
            if (applications.getLength() > 0) {
                assert applications.getLength() == 1;
                Element application = (Element) applications.item(0);
                if (application.hasAttributeNS(NS_RESOURCES, ATTRIBUTE_ICON)) {
                    mApplicationIcon = application.getAttributeNS(NS_RESOURCES, ATTRIBUTE_ICON);
                }
                if (application.hasAttributeNS(NS_RESOURCES, ATTRIBUTE_LABEL)) {
                    mApplicationLabel = application.getAttributeNS(NS_RESOURCES, ATTRIBUTE_LABEL);
                }
            }

            // Look up target SDK
            String defaultTheme = root.getAttributeNS(NS_RESOURCES, ATTRIBUTE_THEME);
            if (defaultTheme == null || defaultTheme.length() == 0) {
                // From manifest theme documentation:
                // "If that attribute is also not set, the default system theme is used."

                NodeList usesSdks = root.getElementsByTagName(NODE_USES_SDK);
                if (usesSdks.getLength() > 0) {
                    Element usesSdk = (Element) usesSdks.item(0);
                    String targetSdk = null;
                    if (usesSdk.hasAttributeNS(NS_RESOURCES, ATTRIBUTE_TARGET_SDK_VERSION)) {
                        targetSdk = usesSdk.getAttributeNS(NS_RESOURCES,
                                ATTRIBUTE_TARGET_SDK_VERSION);
                    } else if (usesSdk.hasAttributeNS(NS_RESOURCES, ATTRIBUTE_MIN_SDK_VERSION)) {
                        targetSdk = usesSdk.getAttributeNS(NS_RESOURCES,
                                ATTRIBUTE_MIN_SDK_VERSION);
                    }
                    if (targetSdk != null) {
                        int apiLevel = -1;
                        try {
                            apiLevel = Integer.valueOf(targetSdk);
                        } catch (NumberFormatException e) {
                            // Handle codename
                            if (Sdk.getCurrent() != null) {
                                IAndroidTarget target = Sdk.getCurrent().getTargetFromHashString(
                                        "android-" + targetSdk); //$NON-NLS-1$
                                if (target != null) {
                                    // codename future API level is current api + 1
                                    apiLevel = target.getVersion().getApiLevel() + 1;
                                }
                            }
                        }

                        mTargetSdk = apiLevel;
                    }
                }
            } else {
                mManifestTheme = defaultTheme;
            }
        } catch (SAXException e) {
            AdtPlugin.log(e, "Malformed manifest");
        } catch (Exception e) {
            AdtPlugin.log(e, "Could not read Manifest data");
        }
    }

    /**
     * Returns the default package registered in the Android manifest
     *
     * @return the default package registered in the manifest
     */
    public String getPackage() {
        sync();
        return mPackage;
    }

    /**
     * Returns a map from activity full class names to the corresponding theme style to be
     * used
     *
     * @return a map from activity fqcn to theme style
     */
    public Map<String, String> getActivityThemes() {
        sync();
        return mActivityThemes;
    }

    /**
     * Returns the default theme for this project, by looking at the manifest default
     * theme registration, target SDK, rendering target, etc.
     *
     * @param renderingTarget the rendering target use to render the theme, or null
     * @param screenSize the screen size to obtain a default theme for, or null if unknown
     * @return the theme to use for this project, never null
     */
    public String getDefaultTheme(IAndroidTarget renderingTarget, ScreenSize screenSize) {
        sync();

        if (mManifestTheme != null) {
            return mManifestTheme;
        }

        int renderingTargetSdk = mTargetSdk;
        if (renderingTarget != null) {
            renderingTargetSdk = renderingTarget.getVersion().getApiLevel();
        }

        int apiLevel = Math.min(mTargetSdk, renderingTargetSdk);
        // For now this theme works only on XLARGE screens. When it works for all sizes,
        // add that new apiLevel to this check.
        if (apiLevel >= 11 && screenSize == ScreenSize.XLARGE) {
            return PREFIX_ANDROID_STYLE + "Theme.Holo"; //$NON-NLS-1$
        } else {
            return PREFIX_ANDROID_STYLE + "Theme"; //$NON-NLS-1$
        }
    }

    /**
     * Returns the application icon, or null
     *
     * @return the application icon, or null
     */
    public String getApplicationIcon() {
        sync();
        return mApplicationIcon;
    }

    /**
     * Returns the application label, or null
     *
     * @return the application label, or null
     */
    public String getApplicationLabel() {
        sync();
        return mApplicationLabel;
    }

    /**
     * Returns the {@link IPackageFragment} for the package registered in the manifest
     *
     * @return the {@link IPackageFragment} for the package registered in the manifest
     */
    public IPackageFragment getPackageFragment() {
        sync();
        try {
            IJavaProject javaProject = BaseProjectHelper.getJavaProject(mProject);
            if (javaProject != null) {
                IPackageFragmentRoot root = ManifestInfo.getSourcePackageRoot(javaProject);
                if (root != null) {
                    return root.getPackageFragment(mPackage);
                }
            }
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return null;
    }

    /**
     * Returns the activity associated with the given layout file. Makes an educated guess
     * by peeking at the usages of the R.layout.name field corresponding to the layout and
     * if it finds a usage.
     *
     * @param project the project containing the layout
     * @param layoutName the layout whose activity we want to look up
     * @param pkg the package containing activities
     * @return the activity name
     */
    public static String guessActivity(IProject project, String layoutName, String pkg) {
        final AtomicReference<String> activity = new AtomicReference<String>();
        SearchRequestor requestor = new SearchRequestor() {
            @Override
            public void acceptSearchMatch(SearchMatch match) throws CoreException {
                Object element = match.getElement();
                if (element instanceof IMethod) {
                    IMethod method = (IMethod) element;
                    IType declaringType = method.getDeclaringType();
                    String fqcn = declaringType.getFullyQualifiedName();
                    if (activity.get() == null
                            || (declaringType.getSuperclassName() != null &&
                                    declaringType.getSuperclassName().endsWith("Activity")) //$NON-NLS-1$
                            || method.getElementName().equals("onCreate")) { //$NON-NLS-1$
                        activity.set(fqcn);
                    }
                }
            }
        };
        try {
            IJavaProject javaProject = BaseProjectHelper.getJavaProject(project);
            if (javaProject == null) {
                return null;
            }
            // TODO - look around a bit more and see if we can figure out whether the
            // call if from within a setContentView call!

            // Search for which java classes call setContentView(R.layout.layoutname);
            String typeFqcn = "R.layout"; //$NON-NLS-1$
            if (pkg != null) {
                typeFqcn = pkg + '.' + typeFqcn;
            }

            IType type = javaProject.findType(typeFqcn);
            if (type != null) {
                IField field = type.getField(layoutName);
                if (field.exists()) {
                    SearchPattern pattern = SearchPattern.createPattern(field, REFERENCES);
                    search(requestor, javaProject, pattern);
                }
            }
        } catch (CoreException e) {
            AdtPlugin.log(e, null);
        }

        return activity.get();
    }

    /**
     * Returns the activity associated with the given layout file.
     * <p>
     * This is an alternative to {@link #guessActivity(IFile, String)}. Whereas
     * guessActivity simply looks for references to "R.layout.foo", this method searches
     * for all usages of Activity#setContentView(int), and for each match it looks up the
     * corresponding call text (such as "setContentView(R.layout.foo)"). From this it uses
     * a regexp to pull out "foo" from this, and stores the association that layout "foo"
     * is associated with the activity class that contained the setContentView call.
     * <p>
     * This has two potential advantages:
     * <ol>
     * <li>It can be faster. We do the reference search -once-, and we've built a map of
     * all the layout-to-activity mappings which we can then immediately look up other
     * layouts for, which is particularly useful at startup when we have to compute the
     * layout activity associations to populate the theme choosers.
     * <li>It can be more accurate. Just because an activity references an "R.layout.foo"
     * field doesn't mean it's setting it as a content view.
     * </ol>
     * However, this second advantage is also its chief problem. There are some common
     * code constructs which means that the associated layout is not explicitly referenced
     * in a direct setContentView call; on a couple of sample projects I tested I found
     * patterns like for example "setContentView(v)" where "v" had been computed earlier.
     * Therefore, for now we're going to stick with the more general approach of just
     * looking up each field when needed. We're keeping the code around, though statically
     * compiled out with the "if (false)" construct below in case we revisit this.
     *
     * @param layoutFile the layout whose activity we want to look up
     * @return the activity name
     */
    @SuppressWarnings("all")
    public String guessActivityBySetContentView(String layoutName) {
        if (false) {
            // These should be fields
            final Pattern LAYOUT_FIELD_PATTERN =
                Pattern.compile("R\\.layout\\.([a-z0-9_]+)"); //$NON-NLS-1$
            Map<String, String> mUsages = null;

            sync();
            if (mUsages == null) {
                final Map<String, String> usages = new HashMap<String, String>();
                mUsages = usages;
                SearchRequestor requestor = new SearchRequestor() {
                    @Override
                    public void acceptSearchMatch(SearchMatch match) throws CoreException {
                        Object element = match.getElement();
                        if (element instanceof IMethod) {
                            IMethod method = (IMethod) element;
                            IType declaringType = method.getDeclaringType();
                            String fqcn = declaringType.getFullyQualifiedName();
                            IDocumentProvider provider = new TextFileDocumentProvider();
                            IResource resource = match.getResource();
                            try {
                                provider.connect(resource);
                                IDocument document = provider.getDocument(resource);
                                if (document != null) {
                                    String matchText = document.get(match.getOffset(),
                                            match.getLength());
                                    Matcher matcher = LAYOUT_FIELD_PATTERN.matcher(matchText);
                                    if (matcher.find()) {
                                        usages.put(matcher.group(1), fqcn);
                                    }
                                }
                            } catch (Exception e) {
                                AdtPlugin.log(e, "Can't find range information for %1$s",
                                        resource.getName());
                            } finally {
                                provider.disconnect(resource);
                            }
                        }
                    }
                };
                try {
                    IJavaProject javaProject = BaseProjectHelper.getJavaProject(mProject);
                    if (javaProject == null) {
                        return null;
                    }

                    // Search for which java classes call setContentView(R.layout.layoutname);
                    String typeFqcn = "R.layout"; //$NON-NLS-1$
                    if (mPackage != null) {
                        typeFqcn = mPackage + '.' + typeFqcn;
                    }

                    IType activityType = javaProject.findType(SdkConstants.CLASS_ACTIVITY);
                    if (activityType != null) {
                        IMethod method = activityType.getMethod(
                                "setContentView", new String[] {"I"}); //$NON-NLS-1$ //$NON-NLS-2$
                        if (method.exists()) {
                            SearchPattern pattern = SearchPattern.createPattern(method,
                                    REFERENCES);
                            search(requestor, javaProject, pattern);
                        }
                    }
                } catch (CoreException e) {
                    AdtPlugin.log(e, null);
                }
            }

            return mUsages.get(layoutName);
        }

        return null;
    }

    /**
     * Performs a search using the given pattern, scope and handler. The search will abort
     * if it takes longer than {@link #SEARCH_TIMEOUT_MS} milliseconds.
     */
    private static void search(SearchRequestor requestor, IJavaProject javaProject,
            SearchPattern pattern) throws CoreException {
        // Find the package fragment specified in the manifest; the activities should
        // live there.
        IJavaSearchScope scope = createPackageScope(javaProject);

        SearchParticipant[] participants = new SearchParticipant[] {
            SearchEngine.getDefaultSearchParticipant()
        };
        SearchEngine engine = new SearchEngine();

        final long searchStart = System.currentTimeMillis();
        NullProgressMonitor monitor = new NullProgressMonitor() {
            private boolean mCancelled;
            @Override
            public void internalWorked(double work) {
                long searchEnd = System.currentTimeMillis();
                if (searchEnd - searchStart > SEARCH_TIMEOUT_MS) {
                    mCancelled = true;
                }
            }

            @Override
            public boolean isCanceled() {
                return mCancelled;
            }
        };
        engine.search(pattern, participants, scope, requestor, monitor);
    }

    /** Creates a package search scope for the first package root in the given java project */
    private static IJavaSearchScope createPackageScope(IJavaProject javaProject) {
        IPackageFragmentRoot packageRoot = getSourcePackageRoot(javaProject);

        IJavaSearchScope scope;
        if (packageRoot != null) {
            IJavaElement[] scopeElements = new IJavaElement[] { packageRoot };
            scope = SearchEngine.createJavaSearchScope(scopeElements);
        } else {
            scope = SearchEngine.createWorkspaceScope();
        }
        return scope;
    }

    /** Returns the first package root for the given java project */
    public static IPackageFragmentRoot getSourcePackageRoot(IJavaProject javaProject) {
        IPackageFragmentRoot packageRoot = null;
        List<IPath> sources = BaseProjectHelper.getSourceClasspaths(javaProject);

        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        for (IPath path : sources) {
            IResource firstSource = workspace.getRoot().findMember(path);
            if (firstSource != null) {
                packageRoot = javaProject.getPackageFragmentRoot(firstSource);
                if (packageRoot != null) {
                    break;
                }
            }
        }
        return packageRoot;
    }

    /**
     * Computes the minimum SDK and target SDK versions for the project
     *
     * @return a pair of (minimum SDK, target SDK) versions, never null
     */
    public static Pair<Integer, Integer> computeSdkVersions(IProject project) {
        int mMinSdkVersion = 1;
        int mTargetSdkVersion = 1;

        IAbstractFile manifestFile = AndroidManifest.getManifest(new IFolderWrapper(project));
        if (manifestFile != null) {
            try {
                Object value = AndroidManifest.getMinSdkVersion(manifestFile);
                mMinSdkVersion = 1; // Default case if missing
                if (value instanceof Integer) {
                    mMinSdkVersion = ((Integer) value).intValue();
                } else if (value instanceof String) {
                    // handle codename, only if we can resolve it.
                    if (Sdk.getCurrent() != null) {
                        IAndroidTarget target = Sdk.getCurrent().getTargetFromHashString(
                                "android-" + value); //$NON-NLS-1$
                        if (target != null) {
                            // codename future API level is current api + 1
                            mMinSdkVersion = target.getVersion().getApiLevel() + 1;
                        }
                    }
                }

                Integer i = AndroidManifest.getTargetSdkVersion(manifestFile);
                if (i == null) {
                    mTargetSdkVersion = mMinSdkVersion;
                } else {
                    mTargetSdkVersion = i.intValue();
                }
            } catch (XPathExpressionException e) {
                // do nothing we'll use 1 below.
            } catch (StreamException e) {
                // do nothing we'll use 1 below.
            }
        }

        return Pair.of(mMinSdkVersion, mTargetSdkVersion);
    }
}
