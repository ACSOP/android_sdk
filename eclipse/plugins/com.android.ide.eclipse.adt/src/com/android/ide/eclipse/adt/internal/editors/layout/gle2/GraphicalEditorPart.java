/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.ide.eclipse.adt.internal.editors.layout.gle2;

import static com.android.ide.common.layout.LayoutConstants.ANDROID_STRING_PREFIX;
import static com.android.ide.common.layout.LayoutConstants.SCROLL_VIEW;
import static com.android.ide.common.layout.LayoutConstants.STRING_PREFIX;
import static com.android.ide.eclipse.adt.AdtConstants.ANDROID_PKG;
import static com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor.viewNeedsPackage;
import static com.android.sdklib.SdkConstants.FD_GEN_SOURCES;

import com.android.ide.common.api.Rect;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.StaticRenderSession;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceFile;
import com.android.ide.common.resources.ResourceRepository;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.sdk.LoadStatus;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.AndroidXmlEditor;
import com.android.ide.eclipse.adt.internal.editors.IPageImageProvider;
import com.android.ide.eclipse.adt.internal.editors.IconFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ChangeFlags;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutReloadMonitor.ILayoutReloadListener;
import com.android.ide.eclipse.adt.internal.editors.layout.ProjectCallback;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite.IConfigListener;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.LayoutCreatorDialog;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.LayoutDescriptors;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.manifest.ManifestInfo;
import com.android.ide.eclipse.adt.internal.editors.ui.DecorComposite;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.ide.eclipse.adt.internal.lint.LintEclipseContext;
import com.android.ide.eclipse.adt.internal.resources.manager.ProjectResources;
import com.android.ide.eclipse.adt.internal.resources.manager.ResourceManager;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.ide.eclipse.adt.internal.sdk.Sdk.ITargetChangeListener;
import com.android.ide.eclipse.adt.io.IFileWrapper;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkConstants;
import com.android.util.Pair;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.preferences.BuildPathsPropertyPage;
import org.eclipse.jdt.ui.actions.OpenNewClassWizardAction;
import org.eclipse.jdt.ui.wizards.NewClassWizardPage;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.INullSelectionListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.EditorPart;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.IPage;
import org.eclipse.ui.part.PageBookView;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Graphical layout editor part, version 2.
 * <p/>
 * The main component of the editor part is the {@link LayoutCanvasViewer}, which
 * actually delegates its work to the {@link LayoutCanvas} control.
 * <p/>
 * The {@link LayoutCanvasViewer} is set as the site's {@link ISelectionProvider}:
 * when the selection changes in the canvas, it is thus broadcasted to anyone listening
 * on the site's selection service.
 * <p/>
 * This part is also an {@link ISelectionListener}. It listens to the site's selection
 * service and thus receives selection changes from itself as well as the associated
 * outline and property sheet (these are registered by {@link LayoutEditor#getAdapter(Class)}).
 *
 * @since GLE2
 */
public class GraphicalEditorPart extends EditorPart
    implements IPageImageProvider, ISelectionListener, INullSelectionListener {

    /*
     * Useful notes:
     * To understand Drag'n'drop:
     *   http://www.eclipse.org/articles/Article-Workbench-DND/drag_drop.html
     *
     * To understand the site's selection listener, selection provider, and the
     * confusion of different-yet-similarly-named interfaces, consult this:
     *   http://www.eclipse.org/articles/Article-WorkbenchSelections/article.html
     *
     * To summarize the selection mechanism:
     * - The workbench site selection service can be seen as "centralized"
     *   service that registers selection providers and selection listeners.
     * - The editor part and the outline are selection providers.
     * - The editor part, the outline and the property sheet are listeners
     *   which all listen to each others indirectly.
     */

    /**
     * Session-property on files which specifies the initial config state to be used on
     * this file
     */
    public final static QualifiedName NAME_INITIAL_STATE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "initialstate");//$NON-NLS-1$

    /**
     * Session-property on files which specifies the inclusion-context (reference to another layout
     * which should be "including" this layout) when the file is opened
     */
    public final static QualifiedName NAME_INCLUDE =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "includer");//$NON-NLS-1$

    /** Reference to the layout editor */
    private final LayoutEditor mLayoutEditor;

    /** Reference to the file being edited. Can also be used to access the {@link IProject}. */
    private IFile mEditedFile;

    /** The configuration composite at the top of the layout editor. */
    private ConfigurationComposite mConfigComposite;

    /** The sash that splits the palette from the canvas. */
    private SashForm mSashPalette;

    /** The sash that splits the palette from the error view.
     * The error view is shown only when needed. */
    private SashForm mSashError;

    /** The palette displayed on the left of the sash. */
    private PaletteControl mPalette;

    /** The layout canvas displayed to the right of the sash. */
    private LayoutCanvasViewer mCanvasViewer;

    /** The Rules Engine associated with this editor. It is project-specific. */
    private RulesEngine mRulesEngine;

    /** Styled text displaying the most recent error in the error view. */
    private StyledText mErrorLabel;

    /**
     * The resource reference to a file that should surround this file (e.g. include this file
     * visually), or null if not applicable
     */
    private Reference mIncludedWithin;

    private Map<ResourceType, Map<String, ResourceValue>> mConfiguredFrameworkRes;
    private Map<ResourceType, Map<String, ResourceValue>> mConfiguredProjectRes;
    private ProjectCallback mProjectCallback;
    private boolean mNeedsRecompute = false;
    private TargetListener mTargetListener;
    private ConfigListener mConfigListener;
    private ResourceResolver mResourceResolver;
    private ReloadListener mReloadListener;
    private int mMinSdkVersion;
    private int mTargetSdkVersion;
    private LayoutActionBar mActionBar;

    /**
     * Flags which tracks whether this editor is currently active which is set whenever
     * {@link #activated()} is called and clear whenever {@link #deactivated()} is called.
     * This is used to suppress repeated calls to {@link #activate()} to avoid doing
     * unnecessary work.
     */
    private boolean mActive;

    public GraphicalEditorPart(LayoutEditor layoutEditor) {
        mLayoutEditor = layoutEditor;
        setPartName("Graphical Layout");
    }

    // ------------------------------------
    // Methods overridden from base classes
    //------------------------------------

    /**
     * Initializes the editor part with a site and input.
     * {@inheritDoc}
     */
    @Override
    public void init(IEditorSite site, IEditorInput input) throws PartInitException {
        setSite(site);
        useNewEditorInput(input);

        if (mTargetListener == null) {
            mTargetListener = new TargetListener();
            AdtPlugin.getDefault().addTargetListener(mTargetListener);
        }
    }

    private void useNewEditorInput(IEditorInput input) throws PartInitException {
        // The contract of init() mentions we need to fail if we can't understand the input.
        if (!(input instanceof FileEditorInput)) {
            throw new PartInitException("Input is not of type FileEditorInput: " +  //$NON-NLS-1$
                    input == null ? "null" : input.toString());                     //$NON-NLS-1$
        }
    }

    public Image getPageImage() {
        return IconFactory.getInstance().getIcon("editor_page_design");  //$NON-NLS-1$
    }

    @Override
    public void createPartControl(Composite parent) {

        Display d = parent.getDisplay();

        GridLayout gl = new GridLayout(1, false);
        parent.setLayout(gl);
        gl.marginHeight = gl.marginWidth = 0;

        mConfigListener = new ConfigListener();

        // Check whether somebody has requested an initial state for the newly opened file.
        // The initial state is a serialized version of the state compatible with
        // {@link ConfigurationComposite#CONFIG_STATE}.
        String initialState = null;
        IFile file = mEditedFile;
        if (file == null) {
            IEditorInput input = mLayoutEditor.getEditorInput();
            if (input instanceof FileEditorInput) {
                file = ((FileEditorInput) input).getFile();
            }
        }

        if (file != null) {
            try {
                initialState = (String) file.getSessionProperty(NAME_INITIAL_STATE);
                if (initialState != null) {
                    // Only use once
                    file.setSessionProperty(NAME_INITIAL_STATE, null);
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, "Can't read session property %1$s", NAME_INITIAL_STATE);
            }
        }

        mConfigComposite = new ConfigurationComposite(mConfigListener, parent,
                SWT.BORDER, initialState);

        mSashPalette = new SashForm(parent, SWT.HORIZONTAL);
        mSashPalette.setLayoutData(new GridData(GridData.FILL_BOTH));

        DecorComposite paletteDecor = new DecorComposite(mSashPalette, SWT.BORDER);
        paletteDecor.setContent(new PaletteControl.PaletteDecor(this));
        mPalette = (PaletteControl) paletteDecor.getContentControl();

        Composite layoutBarAndCanvas = new Composite(mSashPalette, SWT.NONE);
        GridLayout gridLayout = new GridLayout(1, false);
        gridLayout.horizontalSpacing = 0;
        gridLayout.verticalSpacing = 0;
        gridLayout.marginWidth = 0;
        gridLayout.marginHeight = 0;
        layoutBarAndCanvas.setLayout(gridLayout);
        mActionBar = new LayoutActionBar(layoutBarAndCanvas, SWT.NONE, this);
        GridData detailsData = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1);
        mActionBar.setLayoutData(detailsData);
        if (file != null) {
            mActionBar.updateErrorIndicator(LintEclipseContext.hasMarkers(file));
        }

        mSashError = new SashForm(layoutBarAndCanvas, SWT.VERTICAL | SWT.BORDER);
        mSashError.setLayoutData(new GridData(GridData.FILL_BOTH));

        mCanvasViewer = new LayoutCanvasViewer(mLayoutEditor, mRulesEngine, mSashError, SWT.NONE);
        mSashError.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

        mErrorLabel = new StyledText(mSashError, SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
        mErrorLabel.setEditable(false);
        mErrorLabel.setBackground(d.getSystemColor(SWT.COLOR_INFO_BACKGROUND));
        mErrorLabel.setForeground(d.getSystemColor(SWT.COLOR_INFO_FOREGROUND));
        mErrorLabel.addMouseListener(new ErrorLabelListener());

        mSashPalette.setWeights(new int[] { 20, 80 });
        mSashError.setWeights(new int[] { 80, 20 });
        mSashError.setMaximizedControl(mCanvasViewer.getControl());

        // Initialize the state
        reloadPalette();

        getSite().setSelectionProvider(mCanvasViewer);
        getSite().getPage().addSelectionListener(this);
    }

    /**
     * Listens to workbench selections that does NOT come from {@link LayoutEditor}
     * (those are generated by ourselves).
     * <p/>
     * Selection can be null, as indicated by this class implementing
     * {@link INullSelectionListener}.
     */
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (!(part instanceof LayoutEditor)) {
            if (part instanceof PageBookView) {
                PageBookView pbv = (PageBookView) part;
                IPage currentPage = pbv.getCurrentPage();
                if (currentPage instanceof OutlinePage) {
                    LayoutCanvas canvas = getCanvasControl();
                    if (canvas != null && canvas.getOutlinePage() != currentPage) {
                        // The notification is not for this view; ignore
                        // (can happen when there are multiple pages simultaneously
                        // visible)
                        return;
                    }
                }
            }
            mCanvasViewer.setSelection(selection);
        }
    }

    @Override
    public void dispose() {
        getSite().getPage().removeSelectionListener(this);
        getSite().setSelectionProvider(null);

        if (mTargetListener != null) {
            AdtPlugin.getDefault().removeTargetListener(mTargetListener);
            mTargetListener = null;
        }

        if (mReloadListener != null) {
            LayoutReloadMonitor.getMonitor().removeListener(mReloadListener);
            mReloadListener = null;
        }

        if (mCanvasViewer != null) {
            mCanvasViewer.dispose();
            mCanvasViewer = null;
        }
        super.dispose();
    }

    /**
     * Select the visual element corresponding to the given XML node
     * @param xmlNode The Node whose element we want to select
     */
    public void select(Node xmlNode) {
        mCanvasViewer.getCanvas().getSelectionManager().select(xmlNode);
    }

    /**
     * Listens to changes from the Configuration UI banner and triggers layout rendering when
     * changed. Also provide the Configuration UI with the list of resources/layout to display.
     */
    private class ConfigListener implements IConfigListener {

        /**
         * Looks for a file matching the new {@link FolderConfiguration} and attempts to open it.
         * <p/>If there is no match, notify the user.
         */
        public void onConfigurationChange() {
            mConfiguredFrameworkRes = mConfiguredProjectRes = null;
            mResourceResolver = null;

            if (mEditedFile == null || mConfigComposite.getEditedConfig() == null) {
                return;
            }

            // Before doing the normal process, test for the following case.
            // - the editor is being opened (or reset for a new input)
            // - the file being opened is not the best match for any possible configuration
            // - another random compatible config was chosen in the config composite.
            // The result is that 'match' will not be the file being edited, but because this is not
            // due to a config change, we should not trigger opening the actual best match (also,
            // because the editor is still opening the MatchingStrategy woudln't answer true
            // and the best match file would open in a different editor).
            // So the solution is that if the editor is being created, we just call recomputeLayout
            // without looking for a better matching layout file.
            if (mLayoutEditor.isCreatingPages()) {
                recomputeLayout();
            } else {
                // get the resources of the file's project.
                ProjectResources resources = ResourceManager.getInstance().getProjectResources(
                        mEditedFile.getProject());

                // from the resources, look for a matching file
                ResourceFile match = null;
                if (resources != null) {
                    match = resources.getMatchingFile(mEditedFile.getName(),
                                                      ResourceFolderType.LAYOUT,
                                                      mConfigComposite.getCurrentConfig());
                }

                if (match != null) {
                    // since this is coming from Eclipse, this is always an instance of IFileWrapper
                    IFileWrapper iFileWrapper = (IFileWrapper) match.getFile();
                    IFile iFile = iFileWrapper.getIFile();
                    if (iFile.equals(mEditedFile) == false) {
                        try {
                            // tell the editor that the next replacement file is due to a config
                            // change.
                            mLayoutEditor.setNewFileOnConfigChange(true);

                            // ask the IDE to open the replacement file.
                            IDE.openEditor(getSite().getWorkbenchWindow().getActivePage(), iFile);

                            // we're done!
                            return;
                        } catch (PartInitException e) {
                            // FIXME: do something!
                        }
                    }

                    // at this point, we have not opened a new file.

                    // Store the state in the current file
                    mConfigComposite.storeState();

                    // Even though the layout doesn't change, the config changed, and referenced
                    // resources need to be updated.
                    recomputeLayout();
                } else {
                    // display the error.
                    FolderConfiguration currentConfig = mConfigComposite.getCurrentConfig();
                    displayError(
                            "No resources match the configuration\n \n\t%1$s\n \nChange the configuration or create:\n \n\tres/%2$s/%3$s\n \nYou can also click the 'Create' button above.",
                            currentConfig.toDisplayString(),
                            currentConfig.getFolderName(ResourceFolderType.LAYOUT),
                            mEditedFile.getName());
                }
            }

            reloadPalette();
        }

        public void onThemeChange() {
            // Store the state in the current file
            mConfigComposite.storeState();
            mResourceResolver = null;

            recomputeLayout();

            reloadPalette();
        }

        public void onCreate() {
            LayoutCreatorDialog dialog = new LayoutCreatorDialog(mConfigComposite.getShell(),
                    mEditedFile.getName(), mConfigComposite.getCurrentConfig());
            if (dialog.open() == Window.OK) {
                final FolderConfiguration config = new FolderConfiguration();
                dialog.getConfiguration(config);

                createAlternateLayout(config);
            }
        }

        public void onRenderingTargetPreChange(IAndroidTarget oldTarget) {
            preRenderingTargetChangeCleanUp(oldTarget);
        }

        public void onRenderingTargetPostChange(IAndroidTarget target) {
            AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);
            updateCapabilities(targetData);

            mPalette.reloadPalette(target);
        }

        public Map<ResourceType, Map<String, ResourceValue>> getConfiguredFrameworkResources() {
            if (mConfiguredFrameworkRes == null && mConfigComposite != null) {
                ResourceRepository frameworkRes = getFrameworkResources();

                if (frameworkRes == null) {
                    AdtPlugin.log(IStatus.ERROR, "Failed to get ProjectResource for the framework");
                } else {
                    // get the framework resource values based on the current config
                    mConfiguredFrameworkRes = frameworkRes.getConfiguredResources(
                            mConfigComposite.getCurrentConfig());
                }
            }

            return mConfiguredFrameworkRes;
        }

        public Map<ResourceType, Map<String, ResourceValue>> getConfiguredProjectResources() {
            if (mConfiguredProjectRes == null && mConfigComposite != null) {
                ProjectResources project = getProjectResources();

                // get the project resource values based on the current config
                mConfiguredProjectRes = project.getConfiguredResources(
                        mConfigComposite.getCurrentConfig());
            }

            return mConfiguredProjectRes;
        }

        /**
         * Returns a {@link ProjectResources} for the framework resources based on the current
         * configuration selection.
         * @return the framework resources or null if not found.
         */
        public ResourceRepository getFrameworkResources() {
            return getFrameworkResources(getRenderingTarget());
        }

        /**
         * Returns a {@link ProjectResources} for the framework resources of a given
         * target.
         * @param target the target for which to return the framework resources.
         * @return the framework resources or null if not found.
         */
        public ResourceRepository getFrameworkResources(IAndroidTarget target) {
            if (target != null) {
                AndroidTargetData data = Sdk.getCurrent().getTargetData(target);

                if (data != null) {
                    return data.getFrameworkResources();
                }
            }

            return null;
        }

        public ProjectResources getProjectResources() {
            if (mEditedFile != null) {
                ResourceManager manager = ResourceManager.getInstance();
                return manager.getProjectResources(mEditedFile.getProject());
            }

            return null;
        }

        /**
         * Creates a new layout file from the specified {@link FolderConfiguration}.
         */
        private void createAlternateLayout(final FolderConfiguration config) {
            new Job("Create Alternate Resource") {
                @Override
                protected IStatus run(IProgressMonitor monitor) {
                    // get the folder name
                    String folderName = config.getFolderName(ResourceFolderType.LAYOUT);
                    try {

                        // look to see if it exists.
                        // get the res folder
                        IFolder res = (IFolder)mEditedFile.getParent().getParent();
                        String path = res.getLocation().toOSString();

                        File newLayoutFolder = new File(path + File.separator + folderName);
                        if (newLayoutFolder.isFile()) {
                            // this should not happen since aapt would have complained
                            // before, but if one disable the automatic build, this could
                            // happen.
                            String message = String.format("File 'res/%1$s' is in the way!",
                                    folderName);

                            AdtPlugin.displayError("Layout Creation", message);

                            return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID, message);
                        } else if (newLayoutFolder.exists() == false) {
                            // create it.
                            newLayoutFolder.mkdir();
                        }

                        // now create the file
                        File newLayoutFile = new File(newLayoutFolder.getAbsolutePath() +
                                    File.separator + mEditedFile.getName());

                        newLayoutFile.createNewFile();

                        InputStream input = mEditedFile.getContents();

                        FileOutputStream fos = new FileOutputStream(newLayoutFile);

                        byte[] data = new byte[512];
                        int count;
                        while ((count = input.read(data)) != -1) {
                            fos.write(data, 0, count);
                        }

                        input.close();
                        fos.close();

                        // refreshes the res folder to show up the new
                        // layout folder (if needed) and the file.
                        // We use a progress monitor to catch the end of the refresh
                        // to trigger the edit of the new file.
                        res.refreshLocal(IResource.DEPTH_INFINITE, new IProgressMonitor() {
                            public void done() {
                                mConfigComposite.getDisplay().asyncExec(new Runnable() {
                                    public void run() {
                                        onConfigurationChange();
                                    }
                                });
                            }

                            public void beginTask(String name, int totalWork) {
                                // pass
                            }

                            public void internalWorked(double work) {
                                // pass
                            }

                            public boolean isCanceled() {
                                // pass
                                return false;
                            }

                            public void setCanceled(boolean value) {
                                // pass
                            }

                            public void setTaskName(String name) {
                                // pass
                            }

                            public void subTask(String name) {
                                // pass
                            }

                            public void worked(int work) {
                                // pass
                            }
                        });
                    } catch (IOException e2) {
                        String message = String.format(
                                "Failed to create File 'res/%1$s/%2$s' : %3$s",
                                folderName, mEditedFile.getName(), e2.getMessage());

                        AdtPlugin.displayError("Layout Creation", message);

                        return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                                message, e2);
                    } catch (CoreException e2) {
                        String message = String.format(
                                "Failed to create File 'res/%1$s/%2$s' : %3$s",
                                folderName, mEditedFile.getName(), e2.getMessage());

                        AdtPlugin.displayError("Layout Creation", message);

                        return e2.getStatus();
                    }

                    return Status.OK_STATUS;

                }
            }.schedule();
        }

        /**
         * When the device changes, zoom the view to fit, but only up to 100% (e.g. zoom
         * out to fit the content, or zoom back in if we were zoomed out more from the
         * previous view, but only up to 100% such that we never blow up pixels
         */
        public void onDevicePostChange() {
            if (mActionBar.isZoomingAllowed()) {
                getCanvasControl().setFitScale(true);
            }
        }

        public String getIncludedWithin() {
            return mIncludedWithin != null ? mIncludedWithin.getName() : null;
        }
    }

    /**
     * Listens to target changed in the current project, to trigger a new layout rendering.
     */
    private class TargetListener implements ITargetChangeListener {

        public void onProjectTargetChange(IProject changedProject) {
            if (changedProject != null && changedProject.equals(getProject())) {
                updateEditor();
            }
        }

        public void onTargetLoaded(IAndroidTarget loadedTarget) {
            IAndroidTarget target = getRenderingTarget();
            if (target != null && target.equals(loadedTarget)) {
                updateEditor();
            }
        }

        public void onSdkLoaded() {
            // get the current rendering target to unload it
            IAndroidTarget oldTarget = getRenderingTarget();
            preRenderingTargetChangeCleanUp(oldTarget);

            computeSdkVersion();

            // get the project target
            Sdk currentSdk = Sdk.getCurrent();
            if (currentSdk != null) {
                IAndroidTarget target = currentSdk.getTarget(mEditedFile.getProject());
                if (target != null) {
                    mConfigComposite.onSdkLoaded(target);
                    mConfigListener.onConfigurationChange();
                }
            }
        }

        private void updateEditor() {
            mLayoutEditor.commitPages(false /* onSave */);

            // because the target changed we must reset the configured resources.
            mConfiguredFrameworkRes = mConfiguredProjectRes = null;
            mResourceResolver = null;

            // make sure we remove the custom view loader, since its parent class loader is the
            // bridge class loader.
            mProjectCallback = null;

            // recreate the ui root node always, this will also call onTargetChange
            // on the config composite
            mLayoutEditor.initUiRootNode(true /*force*/);
        }

        private IProject getProject() {
            return getLayoutEditor().getProject();
        }
    }

    /** Refresh the configured project resources associated with this editor */
    public void refreshProjectResources() {
        mConfiguredProjectRes = null;
        mResourceResolver = null;
    }

    /**
     * Returns the currently edited file
     *
     * @return the currently edited file, or null
     */
    public IFile getEditedFile() {
        return mEditedFile;
    }

    /**
     * Returns the project for the currently edited file, or null
     *
     * @return the project containing the edited file, or null
     */
    public IProject getProject() {
        if (mEditedFile != null) {
            return mEditedFile.getProject();
        } else {
            return null;
        }
    }

    // ----------------

    /**
     * Save operation in the Graphical Editor Part.
     * <p/>
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     * <p/>
     * This must NOT call the parent editor part. At the contrary, the parent editor
     * part will call this *after* having done the actual save operation.
     * <p/>
     * The only action this editor must do is mark the undo command stack as
     * being no longer dirty.
     */
    @Override
    public void doSave(IProgressMonitor monitor) {
        // TODO implement a command stack
//        getCommandStack().markSaveLocation();
//        firePropertyChange(PROP_DIRTY);
    }

    /**
     * Save operation in the Graphical Editor Part.
     * <p/>
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public void doSaveAs() {
        // pass
    }

    /**
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public boolean isDirty() {
        return false;
    }

    /**
     * In our workflow, the model is owned by the Structured XML Editor.
     * The graphical layout editor just displays it -- thus we don't really
     * save anything here.
     */
    @Override
    public boolean isSaveAsAllowed() {
        return false;
    }

    @Override
    public void setFocus() {
        // TODO Auto-generated method stub

    }

    /**
     * Responds to a page change that made the Graphical editor page the activated page.
     */
    public void activated() {
        if (!mActive) {
            mActive = true;

            mActionBar.updateErrorIndicator();

            boolean changed = mConfigComposite.syncRenderState();
            if (changed) {
                // Will also force recomputeLayout()
                return;
            }

            if (mNeedsRecompute) {
                recomputeLayout();
            }
        }
    }

    /**
     * Responds to a page change that made the Graphical editor page the deactivated page
     */
    public void deactivated() {
        mActive = false;
    }

    /**
     * Opens and initialize the editor with a new file.
     * @param file the file being edited.
     */
    public void openFile(IFile file) {
        mEditedFile = file;
        mConfigComposite.setFile(mEditedFile);

        if (mReloadListener == null) {
            mReloadListener = new ReloadListener();
            LayoutReloadMonitor.getMonitor().addListener(mEditedFile.getProject(), mReloadListener);
        }

        if (mRulesEngine == null) {
            mRulesEngine = new RulesEngine(this, mEditedFile.getProject());
            if (mCanvasViewer != null) {
                mCanvasViewer.getCanvas().setRulesEngine(mRulesEngine);
            }
        }

        // Pick up hand-off data: somebody requesting this file to be opened may have
        // requested that it should be opened as included within another file
        if (mEditedFile != null) {
            try {
                mIncludedWithin = (Reference) mEditedFile.getSessionProperty(NAME_INCLUDE);
                if (mIncludedWithin != null) {
                    // Only use once
                    mEditedFile.setSessionProperty(NAME_INCLUDE, null);
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, "Can't access session property %1$s", NAME_INCLUDE);
            }
        }

        computeSdkVersion();
    }

    /**
     * Resets the editor with a replacement file.
     * @param file the replacement file.
     */
    public void replaceFile(IFile file) {
        mEditedFile = file;
        mConfigComposite.replaceFile(mEditedFile);
        computeSdkVersion();
    }

    /**
     * Resets the editor with a replacement file coming from a config change in the config
     * selector.
     * @param file the replacement file.
     */
    public void changeFileOnNewConfig(IFile file) {
        mEditedFile = file;
        mConfigComposite.changeFileOnNewConfig(mEditedFile);
    }

    /**
     * Responds to a target change for the project of the edited file
     */
    public void onTargetChange() {
        AndroidTargetData targetData = mConfigComposite.onXmlModelLoaded();
        updateCapabilities(targetData);

        mConfigListener.onConfigurationChange();
    }

    /** Updates the capabilities for the given target data (which may be null) */
    private void updateCapabilities(AndroidTargetData targetData) {
        if (targetData != null) {
            LayoutLibrary layoutLib = targetData.getLayoutLibrary();
            if (mIncludedWithin != null &&  !layoutLib.supports(Capability.EMBEDDED_LAYOUT)) {
                showIn(null);
            }
        }
    }

    public LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    /**
     * Returns the {@link RulesEngine} associated with this editor
     *
     * @return the {@link RulesEngine} associated with this editor, never null
     */
    public RulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    /**
     * Return the {@link LayoutCanvas} associated with this editor
     *
     * @return the associated {@link LayoutCanvas}
     */
    public LayoutCanvas getCanvasControl() {
        if (mCanvasViewer != null) {
            return mCanvasViewer.getCanvas();
        }
        return null;
    }

    public UiDocumentNode getModel() {
        return mLayoutEditor.getUiRootNode();
    }

    /**
     * Callback for XML model changed. Only update/recompute the layout if the editor is visible
     */
    public void onXmlModelChanged() {
        // To optimize the rendering when the user is editing in the XML pane, we don't
        // refresh the editor if it's not the active part.
        //
        // This behavior is acceptable when the editor is the single "full screen" part
        // (as in this case active means visible.)
        // Unfortunately this breaks in 2 cases:
        // - when performing a drag'n'drop from one editor to another, the target is not
        //   properly refreshed before it becomes active.
        // - when duplicating the editor window and placing both editors side by side (xml in one
        //   and canvas in the other one), the canvas may not be refreshed when the XML is edited.
        //
        // TODO find a way to really query whether the pane is visible, not just active.

        if (mLayoutEditor.isGraphicalEditorActive()) {
            recomputeLayout();
        } else {
            // Remember we want to recompute as soon as the editor becomes active.
            mNeedsRecompute = true;
        }
    }

    public void recomputeLayout() {
        try {
            if (!ensureFileValid()) {
                return;
            }

            UiDocumentNode model = getModel();
            if (!ensureModelValid(model)) {
                // Although we display an error, we still treat an empty document as a
                // successful layout result so that we can drop new elements in it.
                //
                // For that purpose, create a special LayoutScene that has no image,
                // no root view yet indicates success and then update the canvas with it.

                mCanvasViewer.getCanvas().setSession(
                        new StaticRenderSession(
                                Result.Status.SUCCESS.createResult(),
                                null /*rootViewInfo*/, null /*image*/),
                        null /*explodeNodes*/, true /* layoutlib5 */);
                return;
            }

            LayoutLibrary layoutLib = getReadyLayoutLib(true /*displayError*/);

            if (layoutLib != null) {
                // if drawing in real size, (re)set the scaling factor.
                if (mActionBar.isZoomingRealSize()) {
                    mActionBar.computeAndSetRealScale(false /* redraw */);
                }

                IProject project = mEditedFile.getProject();
                renderWithBridge(project, model, layoutLib);
            }
        } finally {
            // no matter the result, we are done doing the recompute based on the latest
            // resource/code change.
            mNeedsRecompute = false;
        }
    }

    public void reloadPalette() {
        if (mPalette != null) {
            IAndroidTarget renderingTarget = getRenderingTarget();
            if (renderingTarget != null) {
                mPalette.reloadPalette(renderingTarget);
            }
        }
    }

    /**
     * Returns the {@link LayoutLibrary} associated with this editor, if it has
     * been initialized already. May return null if it has not been initialized (or has
     * not finished initializing).
     *
     * @return The {@link LayoutLibrary}, or null
     */
    public LayoutLibrary getLayoutLibrary() {
        return getReadyLayoutLib(false /*displayError*/);
    }

    /**
     * Returns the current bounds of the Android device screen, in canvas control pixels.
     *
     * @return the bounds of the screen, never null
     */
    public Rect getScreenBounds() {
        return mConfigComposite.getScreenBounds();
    }

    /**
     * Returns the scale to multiply pixels in the layout coordinate space with to obtain
     * the corresponding dip (device independent pixel)
     *
     * @return the scale to multiple layout coordinates with to obtain the dip position
     */
    public float getDipScale() {
        return Density.DEFAULT_DENSITY / (float) mConfigComposite.getDensity().getDpiValue();
    }

    // --- private methods ---

    /**
     * Ensure that the file associated with this editor is valid (exists and is
     * synchronized). Any reasons why it is not are displayed in the editor's error area.
     *
     * @return True if the editor is valid, false otherwise.
     */
    private boolean ensureFileValid() {
        // check that the resource exists. If the file is opened but the project is closed
        // or deleted for some reason (changed from outside of eclipse), then this will
        // return false;
        if (mEditedFile.exists() == false) {
            displayError("Resource '%1$s' does not exist.",
                         mEditedFile.getFullPath().toString());
            return false;
        }

        if (mEditedFile.isSynchronized(IResource.DEPTH_ZERO) == false) {
            String message = String.format("%1$s is out of sync. Please refresh.",
                    mEditedFile.getName());

            displayError(message);

            // also print it in the error console.
            IProject iProject = mEditedFile.getProject();
            AdtPlugin.printErrorToConsole(iProject.getName(), message);
            return false;
        }

        return true;
    }

    /**
     * Returns a {@link LayoutLibrary} that is ready for rendering, or null if the bridge
     * is not available or not ready yet (due to SDK loading still being in progress etc).
     * If enabled, any reasons preventing the bridge from being returned are displayed to the
     * editor's error area.
     *
     * @param displayError whether to display the loading error or not.
     *
     * @return LayoutBridge the layout bridge for rendering this editor's scene
     */
    LayoutLibrary getReadyLayoutLib(boolean displayError) {
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget target = getRenderingTarget();

            if (target != null) {
                AndroidTargetData data = currentSdk.getTargetData(target);
                if (data != null) {
                    LayoutLibrary layoutLib = data.getLayoutLibrary();

                    if (layoutLib.getStatus() == LoadStatus.LOADED) {
                        return layoutLib;
                    } else if (displayError) { // getBridge() == null
                        // SDK is loaded but not the layout library!

                        // check whether the bridge managed to load, or not
                        if (layoutLib.getStatus() == LoadStatus.LOADING) {
                            displayError("Eclipse is loading framework information and the layout library from the SDK folder.\n%1$s will refresh automatically once the process is finished.",
                                         mEditedFile.getName());
                        } else {
                            String message = layoutLib.getLoadMessage();
                            displayError("Eclipse failed to load the framework information and the layout library!" +
                                    message != null ? "\n" + message : "");
                        }
                    }
                } else { // data == null
                    // It can happen that the workspace refreshes while the SDK is loading its
                    // data, which could trigger a redraw of the opened layout if some resources
                    // changed while Eclipse is closed.
                    // In this case data could be null, but this is not an error.
                    // We can just silently return, as all the opened editors are automatically
                    // refreshed once the SDK finishes loading.
                    LoadStatus targetLoadStatus = currentSdk.checkAndLoadTargetData(target, null);

                    // display error is asked.
                    if (displayError) {
                        String targetName = target.getName();
                        switch (targetLoadStatus) {
                            case LOADING:
                                String s;
                                if (currentSdk.getTarget(getProject()) == target) {
                                    s = String.format(
                                            "The project target (%1$s) is still loading.",
                                            targetName);
                                } else {
                                    s = String.format(
                                            "The rendering target (%1$s) is still loading.",
                                            targetName);
                                }
                                s += "\nThe layout will refresh automatically once the process is finished.";
                                displayError(s);

                                break;
                            case FAILED: // known failure
                            case LOADED: // success but data isn't loaded?!?!
                                displayError("The project target (%s) was not properly loaded.",
                                        targetName);
                                break;
                        }
                    }
                }

            } else if (displayError) { // target == null
                displayError("The project target is not set.");
            }
        } else if (displayError) { // currentSdk == null
            displayError("Eclipse is loading the SDK.\n%1$s will refresh automatically once the process is finished.",
                         mEditedFile.getName());
        }

        return null;
    }

    /**
     * Returns the {@link IAndroidTarget} used for the rendering.
     * <p/>
     * This first looks for the rendering target setup in the config UI, and if nothing has
     * been setup yet, returns the target of the project.
     *
     * @return an IAndroidTarget object or null if no target is setup and the project has no
     * target set.
     *
     */
    public IAndroidTarget getRenderingTarget() {
        // if the SDK is null no targets are loaded.
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk == null) {
            return null;
        }

        assert mConfigComposite.getDisplay().getThread() == Thread.currentThread();

        // attempt to get a target from the configuration selector.
        IAndroidTarget renderingTarget = mConfigComposite.getRenderingTarget();
        if (renderingTarget != null) {
            return renderingTarget;
        }

        // fall back to the project target
        if (mEditedFile != null) {
            return currentSdk.getTarget(mEditedFile.getProject());
        }

        return null;
    }

    /**
     * Returns whether the current rendering target supports the given capability
     *
     * @param capability the capability to be looked up
     * @return true if the current rendering target supports the given capability
     */
    public boolean renderingSupports(Capability capability) {
        IAndroidTarget target = getRenderingTarget();
        if (target != null) {
            AndroidTargetData targetData = Sdk.getCurrent().getTargetData(target);
            LayoutLibrary layoutLib = targetData.getLayoutLibrary();
            return layoutLib.supports(capability);
        }

        return false;
    }

    private boolean ensureModelValid(UiDocumentNode model) {
        // check there is actually a model (maybe the file is empty).
        if (model.getUiChildren().size() == 0) {
            displayError(
                    "No XML content. Please add a root view or layout to your document.");
            return false;
        }

        return true;
    }

    private void renderWithBridge(IProject iProject, UiDocumentNode model,
            LayoutLibrary layoutLib) {
        LayoutCanvas canvas = getCanvasControl();
        Set<UiElementNode> explodeNodes = canvas.getNodesToExplode();
        Rect rect = getScreenBounds();
        RenderLogger logger = new RenderLogger(mEditedFile.getName());
        RenderingMode renderingMode = RenderingMode.NORMAL;
        // FIXME set the rendering mode using ViewRule or something.
        List<UiElementNode> children = model.getUiChildren();
        if (children.size() > 0 &&
                children.get(0).getDescriptor().getXmlLocalName().equals(SCROLL_VIEW)) {
            renderingMode = RenderingMode.V_SCROLL;
        }

        RenderSession session = RenderService.create(this)
            .setModel(model)
            .setSize(rect.w, rect.h)
            .setLog(logger)
            .setRenderingMode(renderingMode)
            .setIncludedWithin(mIncludedWithin)
            .setNodesToExpand(explodeNodes)
            .createRenderSession();

        boolean layoutlib5 = layoutLib.supports(Capability.EMBEDDED_LAYOUT);
        canvas.setSession(session, explodeNodes, layoutlib5);

        // update the UiElementNode with the layout info.
        if (session != null && session.getResult().isSuccess() == false) {
            // An error was generated. Print it (and any other accumulated warnings)
            String errorMessage = session.getResult().getErrorMessage();
            Throwable exception = session.getResult().getException();
            if (exception != null && errorMessage == null) {
                errorMessage = exception.toString();
            }
            if (exception != null || (errorMessage != null && errorMessage.length() > 0)) {
                logger.error(null, errorMessage, exception, null /*data*/);
            } else if (!logger.hasProblems()) {
                logger.error(null, "Unexpected error in rendering, no details given",
                        null /*data*/);
            }
            // These errors will be included in the log warnings which are
            // displayed regardless of render success status below
        }

        // We might have detected some missing classes and swapped them by a mock view,
        // or run into fidelity warnings or missing resources, so emit all these
        // warnings
        Set<String> missingClasses = mProjectCallback.getMissingClasses();
        Set<String> brokenClasses = mProjectCallback.getUninstantiatableClasses();
        if (logger.hasProblems()) {
            displayLoggerProblems(iProject, logger);
            displayFailingClasses(missingClasses, brokenClasses, true);
        } else if (missingClasses.size() > 0 || brokenClasses.size() > 0) {
            displayFailingClasses(missingClasses, brokenClasses, false);
        } else {
            // Nope, no missing or broken classes. Clear success, congrats!
            hideError();
        }

        model.refreshUi();
    }

    /**
     * Returns the {@link ResourceResolver} for this editor
     *
     * @return the resolver used to resolve resources for the current configuration of
     *         this editor, or null
     */
    public ResourceResolver getResourceResolver() {
        if (mResourceResolver == null) {
            String theme = mConfigComposite.getTheme();
            if (theme == null) {
                displayError("Missing theme.");
                return null;
            }
            boolean isProjectTheme = mConfigComposite.isProjectTheme();

            Map<ResourceType, Map<String, ResourceValue>> configuredProjectRes =
                mConfigListener.getConfiguredProjectResources();

            // Get the framework resources
            Map<ResourceType, Map<String, ResourceValue>> frameworkResources =
                mConfigListener.getConfiguredFrameworkResources();

            if (configuredProjectRes == null) {
                displayError("Missing project resources for current configuration.");
                return null;
            }

            if (frameworkResources == null) {
                displayError("Missing framework resources.");
                return null;
            }

            mResourceResolver = ResourceResolver.create(
                    configuredProjectRes, frameworkResources,
                    theme, isProjectTheme);
        }

        return mResourceResolver;
    }

    /** Returns a project callback, and optionally resets it */
    ProjectCallback getProjectCallback(boolean reset, LayoutLibrary layoutLibrary) {
        // Lazily create the project callback the first time we need it
        if (mProjectCallback == null) {
            ResourceManager resManager = ResourceManager.getInstance();
            IProject project = getProject();
            ProjectResources projectRes = resManager.getProjectResources(project);
            mProjectCallback = new ProjectCallback(layoutLibrary, projectRes, project);
        } else if (reset) {
            // Also clears the set of missing/broken classes prior to rendering
            mProjectCallback.getMissingClasses().clear();
            mProjectCallback.getUninstantiatableClasses().clear();
        }

        return mProjectCallback;
    }

    /**
     * Returns the resource name of this layout, NOT including the @layout/ prefix
     *
     * @return the resource name of this layout, NOT including the @layout/ prefix
     */
    public String getLayoutResourceName() {
        String name = mEditedFile.getName();
        int dotIndex = name.indexOf('.');
        if (dotIndex != -1) {
            name = name.substring(0, dotIndex);
        }
        return name;
    }

    /**
     * Cleans up when the rendering target is about to change
     * @param oldTarget the old rendering target.
     */
    private void preRenderingTargetChangeCleanUp(IAndroidTarget oldTarget) {
        // first clear the caches related to this file in the old target
        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            AndroidTargetData data = currentSdk.getTargetData(oldTarget);
            if (data != null) {
                LayoutLibrary layoutLib = data.getLayoutLibrary();

                // layoutLib can never be null.
                layoutLib.clearCaches(mEditedFile.getProject());
            }
        }

        // Also remove the ProjectCallback as it caches custom views which must be reloaded
        // with the classloader of the new LayoutLib. We also have to clear it out
        // because it stores a reference to the layout library which could have changed.
        mProjectCallback = null;

        // FIXME: get rid of the current LayoutScene if any.
    }

    private class ReloadListener implements ILayoutReloadListener {
        /**
         * Called when the file changes triggered a redraw of the layout
         */
        public void reloadLayout(final ChangeFlags flags, final boolean libraryChanged) {
            if (mConfigComposite.isDisposed()) {
                return;
            }
            Display display = mConfigComposite.getDisplay();
            display.asyncExec(new Runnable() {
                public void run() {
                    reloadLayoutSwt(flags, libraryChanged);
                }
            });
        }

        /** Reload layout. <b>Must be called on the SWT thread</b> */
        private void reloadLayoutSwt(ChangeFlags flags, boolean libraryChanged) {
            if (mConfigComposite.isDisposed()) {
                return;
            }
            assert mConfigComposite.getDisplay().getThread() == Thread.currentThread();

            boolean recompute = false;
            // we only care about the r class of the main project.
            if (flags.rClass && libraryChanged == false) {
                recompute = true;
                if (mEditedFile != null) {
                    ResourceManager manager = ResourceManager.getInstance();
                    ProjectResources projectRes = manager.getProjectResources(
                            mEditedFile.getProject());

                    if (projectRes != null) {
                        projectRes.resetDynamicIds();
                    }
                }
            }

            if (flags.localeList) {
                // the locale list *potentially* changed so we update the locale in the
                // config composite.
                // However there's no recompute, as it could not be needed
                // (for instance a new layout)
                // If a resource that's not a layout changed this will trigger a recompute anyway.
                mConfigComposite.updateLocales();
            }

            // if a resources was modified.
            if (flags.resources) {
                recompute = true;

                // TODO: differentiate between single and multi resource file changed, and whether
                // the resource change affects the cache.

                // force a reparse in case a value XML file changed.
                mConfiguredProjectRes = null;
                mResourceResolver = null;

                // clear the cache in the bridge in case a bitmap/9-patch changed.
                LayoutLibrary layoutLib = getReadyLayoutLib(true /*displayError*/);
                if (layoutLib != null) {
                    layoutLib.clearCaches(mEditedFile.getProject());
                }
            }

            if (flags.code) {
                // only recompute if the custom view loader was used to load some code.
                if (mProjectCallback != null && mProjectCallback.isUsed()) {
                    mProjectCallback = null;
                    recompute = true;
                }
            }

            if (flags.manifest) {
                recompute |= computeSdkVersion();
            }

            if (recompute) {
                if (mLayoutEditor.isGraphicalEditorActive()) {
                    recomputeLayout();
                } else {
                    mNeedsRecompute = true;
                }
            }
        }
    }

    // ---- Error handling ----

    /**
     * Switches the sash to display the error label.
     *
     * @param errorFormat The new error to display if not null.
     * @param parameters String.format parameters for the error format.
     */
    private void displayError(String errorFormat, Object...parameters) {
        if (errorFormat != null) {
            mErrorLabel.setText(String.format(errorFormat, parameters));
        } else {
            mErrorLabel.setText("");
        }
        mSashError.setMaximizedControl(null);
    }

    /** Displays the canvas and hides the error label. */
    private void hideError() {
        mErrorLabel.setText("");
        mSashError.setMaximizedControl(mCanvasViewer.getControl());
    }

    /**
     * Switches the sash to display the error label to show a list of
     * missing classes and give options to create them.
     */
    private void displayFailingClasses(Set<String> missingClasses, Set<String> brokenClasses,
            boolean append) {
        if (missingClasses.size() == 0 && brokenClasses.size() == 0) {
            return;
        }

        if (!append) {
            mErrorLabel.setText("");    //$NON-NLS-1$
        } else {
            addText(mErrorLabel, "\n"); //$NON-NLS-1$
        }

        if (missingClasses.size() > 0) {
            addText(mErrorLabel, "The following classes could not be found:\n");
            for (String clazz : missingClasses) {
                addText(mErrorLabel, "- ");
                addText(mErrorLabel, clazz);
                addText(mErrorLabel, " (");

                IProject project = getProject();
                Collection<String> customViews = getCustomViewClassNames(project);
                addTypoSuggestions(clazz, customViews, false);
                addTypoSuggestions(clazz, customViews, true);
                addTypoSuggestions(clazz, getAndroidViewClassNames(project), false);

                addActionLink(mErrorLabel,
                        ActionLinkStyleRange.LINK_FIX_BUILD_PATH, "Fix Build Path", clazz, null);
                addText(mErrorLabel, ", ");
                addActionLink(mErrorLabel,
                        ActionLinkStyleRange.LINK_EDIT_XML, "Edit XML", clazz, null);
                if (clazz.indexOf('.') != -1) {
                    // Add "Create Class" link, but only for custom views
                    addText(mErrorLabel, ", ");
                    addActionLink(mErrorLabel,
                            ActionLinkStyleRange.LINK_CREATE_CLASS, "Create Class", clazz, null);
                }
                addText(mErrorLabel, ")\n");
            }
        }
        if (brokenClasses.size() > 0) {
            addText(mErrorLabel, "The following classes could not be instantiated:\n");

            // Do we have a custom class (not an Android or add-ons class)
            boolean haveCustomClass = false;

            for (String clazz : brokenClasses) {
                addText(mErrorLabel, "- ");
                addText(mErrorLabel, " (");
                addActionLink(mErrorLabel,
                        ActionLinkStyleRange.LINK_OPEN_CLASS, "Open Class", clazz, null);
                addText(mErrorLabel, ", ");
                addActionLink(mErrorLabel,
                        ActionLinkStyleRange.LINK_SHOW_LOG, "Show Error Log", clazz, null);
                addText(mErrorLabel, ")\n");

                if (!(clazz.startsWith("android.") || //$NON-NLS-1$
                        clazz.startsWith("com.google."))) { //$NON-NLS-1$
                    haveCustomClass = true;
                }
            }

            addText(mErrorLabel, "See the Error Log (Window > Show View) for more details.\n");

            if (haveCustomClass) {
                addText(mErrorLabel, "Tip: Use View.isInEditMode() in your custom views "
                        + "to skip code when shown in Eclipse");
            }
        }

        mSashError.setMaximizedControl(null);
    }

    private void addTypoSuggestions(String actual, Collection<String> views,
            boolean compareWithPackage) {
        if (views.size() == 0) {
            return;
        }

        // Look for typos and try to match with custom views and android views
        String actualBase = actual.substring(actual.lastIndexOf('.') + 1);
        if (views.size() > 0) {
            for (String suggested : views) {
                String suggestedBase = suggested.substring(suggested.lastIndexOf('.') + 1);

                String matchWith = compareWithPackage ? suggested : suggestedBase;
                int maxDistance = actualBase.length() >= 4 ? 2 : 1;
                if (Math.abs(actualBase.length() - matchWith.length()) > maxDistance) {
                    // The string lengths differ more than the allowed edit distance;
                    // no point in even attempting to compute the edit distance (requires
                    // O(n*m) storage and O(n*m) speed, where n and m are the string lengths)
                    continue;
                }
                if (AdtUtils.editDistance(actualBase, matchWith) <= maxDistance) {
                    // Suggest this class as a typo for the given class
                    String labelClass = (suggestedBase.equals(actual) || actual.indexOf('.') != -1)
                        ? suggested : suggestedBase;
                    addActionLink(mErrorLabel,
                            ActionLinkStyleRange.LINK_CHANGE_CLASS_TO,
                            String.format("Change to %1$s",
                                    // Only show full package name if class name
                                    // is the same
                                    labelClass),
                                    actual,
                                    viewNeedsPackage(suggested) ? suggested : suggestedBase
                    );
                    addText(mErrorLabel, ", ");
                }
            }
        }
    }

    private static Collection<String> getCustomViewClassNames(IProject project) {
        CustomViewFinder finder = CustomViewFinder.get(project);
        Collection<String> views = finder.getAllViews();
        if (views == null) {
            finder.refresh();
            views = finder.getAllViews();
        }

        return views;
    }

    private static Collection<String> getAndroidViewClassNames(IProject project) {
        Sdk currentSdk = Sdk.getCurrent();
        IAndroidTarget target = currentSdk.getTarget(project);
        if (target != null) {
            AndroidTargetData targetData = currentSdk.getTargetData(target);
            LayoutDescriptors layoutDescriptors = targetData.getLayoutDescriptors();
            return layoutDescriptors.getAllViewClassNames();
        }

        return Collections.emptyList();
    }

    /** Add a normal line of text to the styled text widget. */
    private void addText(StyledText styledText, String...string) {
        for (String s : string) {
            styledText.append(s);
        }
    }

    /** Display the problem list encountered during a render */
    private void displayLoggerProblems(IProject project, RenderLogger logger) {
        if (logger.hasProblems()) {
            mErrorLabel.setText("");
            // A common source of problems is attempting to open a layout when there are
            // compilation errors. In this case, may not have run (or may not be up to date)
            // so resources cannot be looked up etc. Explain this situation to the user.

            boolean hasAaptErrors = false;
            boolean hasJavaErrors = false;
            try {
                IMarker[] markers;
                markers = project.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
                if (markers.length > 0) {
                    for (IMarker marker : markers) {
                        String markerType = marker.getType();
                        if (markerType.equals(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER)) {
                            int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                            if (severity == IMarker.SEVERITY_ERROR) {
                                hasJavaErrors = true;
                            }
                        } else if (markerType.equals(AdtConstants.MARKER_AAPT_COMPILE)) {
                            int severity = marker.getAttribute(IMarker.SEVERITY, -1);
                            if (severity == IMarker.SEVERITY_ERROR) {
                                hasAaptErrors = true;
                            }
                        }
                    }
                }
            } catch (CoreException e) {
                AdtPlugin.log(e, null);
            }

            if (logger.seenTagPrefix(LayoutLog.TAG_RESOURCES_RESOLVE_THEME_ATTR)) {
                addBoldText(mErrorLabel,
                        "Missing styles. Is the correct theme chosen for this layout?\n");
                addText(mErrorLabel,
                        "Use the Theme combo box above the layout to choose a different layout, " +
                        "or fix the theme style references.\n\n");
            }

            if (hasAaptErrors && logger.seenTagPrefix(LayoutLog.TAG_RESOURCES_PREFIX)) {
                // Text will automatically be wrapped by the error widget so no reason
                // to insert linebreaks in this error message:
                String message =
                    "NOTE: This project contains resource errors, so aapt did not succeed, "
                     + "which can cause rendering failures. "
                     + "Fix resource problems first.\n\n";
                 addBoldText(mErrorLabel, message);
            } else if (hasJavaErrors && mProjectCallback != null && mProjectCallback.isUsed()) {
                // Text will automatically be wrapped by the error widget so no reason
                // to insert linebreaks in this error message:
                String message =
                   "NOTE: This project contains Java compilation errors, "
                    + "which can cause rendering failures for custom views. "
                    + "Fix compilation problems first.\n\n";
                addBoldText(mErrorLabel, message);
            }

            String problems = logger.getProblems(false /*includeFidelityWarnings*/);
            addText(mErrorLabel, problems);

            List<String> fidelityWarnings = logger.getFidelityWarnings();
            if (fidelityWarnings != null && fidelityWarnings.size() > 0) {
                addText(mErrorLabel,
                        "The graphics preview in the layout editor may not be accurate:\n");
                for (String warning : fidelityWarnings) {
                    addText(mErrorLabel, warning + ' ');
                    addActionLink(mErrorLabel,
                            ActionLinkStyleRange.IGNORE_FIDELITY_WARNING,
                            "(Ignore for this session)\n", warning, null);
                }
            }

            mSashError.setMaximizedControl(null);
        } else {
            mSashError.setMaximizedControl(mCanvasViewer.getControl());
        }
    }

    /** Appends the given text as a bold string in the given text widget */
    private void addBoldText(StyledText styledText, String text) {
        String s = styledText.getText();
        int start = (s == null ? 0 : s.length());

        styledText.append(text);
        StyleRange sr = new StyleRange();
        sr.start = start;
        sr.length = text.length();
        sr.fontStyle = SWT.BOLD;
        styledText.setStyleRange(sr);
    }

    /**
     * Add a URL-looking link to the styled text widget.
     * <p/>
     * A mouse-click listener is setup and it interprets the link based on the
     * action, corresponding to the value fields in {@link ActionLinkStyleRange}.
     */
    private void addActionLink(StyledText styledText, int action, String label,
            String data1, String data2) {
        String s = styledText.getText();
        int start = (s == null ? 0 : s.length());
        styledText.append(label);

        StyleRange sr = new ActionLinkStyleRange(action, data1, data2);
        sr.start = start;
        sr.length = label.length();
        sr.fontStyle = SWT.NORMAL;
        sr.underlineStyle = SWT.UNDERLINE_LINK;
        sr.underline = true;
        styledText.setStyleRange(sr);
    }

    /**
     * Looks up the resource file corresponding to the given type
     *
     * @param type The type of resource to look up, such as {@link ResourceType#LAYOUT}
     * @param name The name of the resource (not including ".xml")
     * @param isFrameworkResource if true, the resource is a framework resource, otherwise
     *            it's a project resource
     * @return the resource file defining the named resource, or null if not found
     */
    public IPath findResourceFile(ResourceType type, String name, boolean isFrameworkResource) {
        // FIXME: This code does not handle theme value resolution.
        // There is code to handle this, but it's in layoutlib; we should
        // expose that and use it here.

        Map<ResourceType, Map<String, ResourceValue>> map;
        map = isFrameworkResource ? mConfiguredFrameworkRes : mConfiguredProjectRes;
        if (map == null) {
            // Not yet configured
            return null;
        }

        Map<String, ResourceValue> layoutMap = map.get(type);
        if (layoutMap != null) {
            ResourceValue value = layoutMap.get(name);
            if (value != null) {
                String valueStr = value.getValue();
                if (valueStr.startsWith("?")) { //$NON-NLS-1$
                    // FIXME: It's a reference. We should resolve this properly.
                    return null;
                }
                return new Path(valueStr);
            }
        }

        return null;
    }

    /**
     * Looks up the path to the file corresponding to the given attribute value, such as
     * @layout/foo, which will return the foo.xml file in res/layout/. (The general format
     * of the resource url is {@literal @[<package_name>:]<resource_type>/<resource_name>}.
     *
     * @param url the attribute url
     * @return the path to the file defining this attribute, or null if not found
     */
    public IPath findResourceFile(String url) {
        if (!url.startsWith("@")) { //$NON-NLS-1$
            return null;
        }
        int typeEnd = url.indexOf('/', 1);
        if (typeEnd == -1) {
            return null;
        }
        int nameBegin = typeEnd + 1;
        int typeBegin = 1;
        int colon = url.lastIndexOf(':', typeEnd);
        boolean isFrameworkResource = false;
        if (colon != -1) {
            // The URL contains a package name.
            // While the url format technically allows other package names,
            // the platform apparently only supports @android for now (or if it does,
            // there are no usages in the current code base so this is not common).
            String packageName = url.substring(typeBegin, colon);
            if (ANDROID_PKG.equals(packageName)) {
                isFrameworkResource = true;
            }

            typeBegin = colon + 1;
        }

        String typeName = url.substring(typeBegin, typeEnd);
        ResourceType type = ResourceType.getEnum(typeName);
        if (type == null) {
            return null;
        }

        String name = url.substring(nameBegin);
        return findResourceFile(type, name, isFrameworkResource);
    }

    /**
     * Resolve the given @string reference into a literal String using the current project
     * configuration
     *
     * @param text the text resource reference to resolve
     * @return the resolved string, or null
     */
    public String findString(String text) {
        if (text.startsWith(STRING_PREFIX)) {
            return findString(text.substring(STRING_PREFIX.length()), false);
        } else if (text.startsWith(ANDROID_STRING_PREFIX)) {
            return findString(text.substring(ANDROID_STRING_PREFIX.length()), true);
        } else {
            return text;
        }
    }

    private String findString(String name, boolean isFrameworkResource) {
        Map<ResourceType, Map<String, ResourceValue>> map;
        map = isFrameworkResource ? mConfiguredFrameworkRes : mConfiguredProjectRes;
        if (map == null) {
            // Not yet configured
            return null;
        }

        Map<String, ResourceValue> layoutMap = map.get(ResourceType.STRING);
        if (layoutMap != null) {
            ResourceValue value = layoutMap.get(name);
            if (value != null) {
                // FIXME: This code does not handle theme value resolution.
                // There is code to handle this, but it's in layoutlib; we should
                // expose that and use it here.
                return value.getValue();
            }
        }

        return null;
    }

    /**
     * This StyleRange represents a clickable link in the render output, where various
     * actions can be taken such as creating a class, opening the project chooser to
     * adjust the build path, etc.
     */
    private class ActionLinkStyleRange extends StyleRange {
        /** Create a view class */
        private static final int LINK_CREATE_CLASS = 1;
        /** Edit the build path for the current project */
        private static final int LINK_FIX_BUILD_PATH = 2;
        /** Show the XML tab */
        private static final int LINK_EDIT_XML = 3;
        /** Open the given class */
        private static final int LINK_OPEN_CLASS = 4;
        /** Show the error log */
        private static final int LINK_SHOW_LOG = 5;
        /** Change the class reference to the given fully qualified name */
        private static final int LINK_CHANGE_CLASS_TO = 6;
        /** Ignore the given fidelity warning */
        private static final int IGNORE_FIDELITY_WARNING = 7;

        /** Client data 1 - usually the class name */
        private final String mData1;
        /** Client data 2 - such as the suggested new name */
        private final String mData2;
        /** The action to be taken when the link is clicked */
        private final int mAction;

        private ActionLinkStyleRange(int action, String data1, String data2) {
            super();
            mAction = action;
            mData1 = data1;
            mData2 = data2;
        }

        /** Performs the click action */
        public void onClick() {
            switch (mAction) {
                case LINK_CREATE_CLASS:
                    createNewClass(mData1);
                    break;
                case LINK_EDIT_XML:
                    mLayoutEditor.setActivePage(AndroidXmlEditor.TEXT_EDITOR_ID);
                    break;
                case LINK_FIX_BUILD_PATH:
                    @SuppressWarnings("restriction")
                    String id = BuildPathsPropertyPage.PROP_ID;
                    PreferencesUtil.createPropertyDialogOn(
                            AdtPlugin.getDisplay().getActiveShell(),
                            getProject(), id, null, null).open();
                    break;
                case LINK_OPEN_CLASS:
                    AdtPlugin.openJavaClass(getProject(), mData1);
                    break;
                case LINK_SHOW_LOG:
                    IWorkbench workbench = PlatformUI.getWorkbench();
                    IWorkbenchWindow workbenchWindow = workbench.getActiveWorkbenchWindow();
                    try {
                        IWorkbenchPage page = workbenchWindow.getActivePage();
                        page.showView("org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$
                    } catch (PartInitException e) {
                        AdtPlugin.log(e, null);
                    }
                    break;
                case LINK_CHANGE_CLASS_TO:
                    // Change class reference of mData1 to mData2
                    // TODO: run under undo lock
                    MultiTextEdit edits = new MultiTextEdit();
                    ISourceViewer textViewer = mLayoutEditor.getStructuredSourceViewer();
                    IDocument document = textViewer.getDocument();
                    String xml = document.get();
                    int index = 0;
                    // Replace <old with <new and </old with </new
                    String prefix = "<"; //$NON-NLS-1$
                    String find = prefix + mData1;
                    String replaceWith = prefix + mData2;
                    while (true) {
                        index = xml.indexOf(find, index);
                        if (index == -1) {
                            break;
                        }
                        edits.addChild(new ReplaceEdit(index, find.length(), replaceWith));
                        index += find.length();
                    }
                    index = 0;
                    prefix = "</"; //$NON-NLS-1$
                    find = prefix + mData1;
                    replaceWith = prefix + mData2;
                    while (true) {
                        index = xml.indexOf(find, index);
                        if (index == -1) {
                            break;
                        }
                        edits.addChild(new ReplaceEdit(index, find.length(), replaceWith));
                        index += find.length();
                    }
                    // Handle <view class="old">
                    index = 0;
                    prefix = "\""; //$NON-NLS-1$
                    String suffix = "\""; //$NON-NLS-1$
                    find = prefix + mData1 + suffix;
                    replaceWith = prefix + mData2 + suffix;
                    while (true) {
                        index = xml.indexOf(find, index);
                        if (index == -1) {
                            break;
                        }
                        edits.addChild(new ReplaceEdit(index, find.length(), replaceWith));
                        index += find.length();
                    }
                    try {
                        edits.apply(document);
                    } catch (MalformedTreeException e) {
                        AdtPlugin.log(e, null);
                    } catch (BadLocationException e) {
                        AdtPlugin.log(e, null);
                    }
                    break;
                case IGNORE_FIDELITY_WARNING:
                    RenderLogger.ignoreFidelityWarning(mData1);
                    recomputeLayout();
                    break;
                default:
                    break;
            }
        }

        @Override
        public boolean similarTo(StyleRange style) {
            // Prevent adjacent link ranges from getting merged
            return false;
        }
    }

    /**
     * Returns the error label for the graphical editor (which may not be visible
     * or showing errors)
     *
     * @return the error label, never null
     */
    StyledText getErrorLabel() {
        return mErrorLabel;
    }

    /**
     * Monitor clicks on the error label.
     * If the click happens on a style range created by
     * {@link GraphicalEditorPart#addClassLink(StyledText, String)}, we assume it's about
     * a missing class and we then proceed to display the standard Eclipse class creator wizard.
     */
    private class ErrorLabelListener extends MouseAdapter {

        @Override
        public void mouseUp(MouseEvent event) {
            super.mouseUp(event);

            if (event.widget != mErrorLabel) {
                return;
            }

            int offset = mErrorLabel.getCaretOffset();

            StyleRange r = null;
            StyleRange[] ranges = mErrorLabel.getStyleRanges();
            if (ranges != null && ranges.length > 0) {
                for (StyleRange sr : ranges) {
                    if (sr.start <= offset && sr.start + sr.length > offset) {
                        r = sr;
                        break;
                    }
                }
            }

            if (r instanceof ActionLinkStyleRange) {
                ActionLinkStyleRange range = (ActionLinkStyleRange) r;
                range.onClick();
            }

            LayoutCanvas canvas = getCanvasControl();
            canvas.updateMenuActionState();
        }
    }

    private void createNewClass(String fqcn) {

        int pos = fqcn.lastIndexOf('.');
        String packageName = pos < 0 ? "" : fqcn.substring(0, pos);  //$NON-NLS-1$
        String className = pos <= 0 || pos >= fqcn.length() ? "" : fqcn.substring(pos + 1); //$NON-NLS-1$

        // create the wizard page for the class creation, and configure it
        NewClassWizardPage page = new NewClassWizardPage();

        // set the parent class
        page.setSuperClass(SdkConstants.CLASS_VIEW, true /* canBeModified */);

        // get the source folders as java elements.
        IPackageFragmentRoot[] roots = getPackageFragmentRoots(mLayoutEditor.getProject(),
                false /*includeContainers*/, true /*skipGenFolder*/);

        IPackageFragmentRoot currentRoot = null;
        IPackageFragment currentFragment = null;
        int packageMatchCount = -1;

        for (IPackageFragmentRoot root : roots) {
            // Get the java element for the package.
            // This method is said to always return a IPackageFragment even if the
            // underlying folder doesn't exist...
            IPackageFragment fragment = root.getPackageFragment(packageName);
            if (fragment != null && fragment.exists()) {
                // we have a perfect match! we use it.
                currentRoot = root;
                currentFragment = fragment;
                packageMatchCount = -1;
                break;
            } else {
                // we don't have a match. we look for the fragment with the best match
                // (ie the closest parent package we can find)
                try {
                    IJavaElement[] children;
                    children = root.getChildren();
                    for (IJavaElement child : children) {
                        if (child instanceof IPackageFragment) {
                            fragment = (IPackageFragment)child;
                            if (packageName.startsWith(fragment.getElementName())) {
                                // its a match. get the number of segments
                                String[] segments = fragment.getElementName().split("\\."); //$NON-NLS-1$
                                if (segments.length > packageMatchCount) {
                                    packageMatchCount = segments.length;
                                    currentFragment = fragment;
                                    currentRoot = root;
                                }
                            }
                        }
                    }
                } catch (JavaModelException e) {
                    // Couldn't get the children: we just ignore this package root.
                }
            }
        }

        ArrayList<IPackageFragment> createdFragments = null;

        if (currentRoot != null) {
            // if we have a perfect match, we set it and we're done.
            if (packageMatchCount == -1) {
                page.setPackageFragmentRoot(currentRoot, true /* canBeModified*/);
                page.setPackageFragment(currentFragment, true /* canBeModified */);
            } else {
                // we have a partial match.
                // create the package. We have to start with the first segment so that we
                // know what to delete in case of a cancel.
                try {
                    createdFragments = new ArrayList<IPackageFragment>();

                    int totalCount = packageName.split("\\.").length; //$NON-NLS-1$
                    int count = 0;
                    int index = -1;
                    // skip the matching packages
                    while (count < packageMatchCount) {
                        index = packageName.indexOf('.', index+1);
                        count++;
                    }

                    // create the rest of the segments, except for the last one as indexOf will
                    // return -1;
                    while (count < totalCount - 1) {
                        index = packageName.indexOf('.', index+1);
                        count++;
                        createdFragments.add(currentRoot.createPackageFragment(
                                packageName.substring(0, index),
                                true /* force*/, new NullProgressMonitor()));
                    }

                    // create the last package
                    createdFragments.add(currentRoot.createPackageFragment(
                            packageName, true /* force*/, new NullProgressMonitor()));

                    // set the root and fragment in the Wizard page
                    page.setPackageFragmentRoot(currentRoot, true /* canBeModified*/);
                    page.setPackageFragment(createdFragments.get(createdFragments.size()-1),
                            true /* canBeModified */);
                } catch (JavaModelException e) {
                    // If we can't create the packages, there's a problem.
                    // We revert to the default package
                    for (IPackageFragmentRoot root : roots) {
                        // Get the java element for the package.
                        // This method is said to always return a IPackageFragment even if the
                        // underlying folder doesn't exist...
                        IPackageFragment fragment = root.getPackageFragment(packageName);
                        if (fragment != null && fragment.exists()) {
                            page.setPackageFragmentRoot(root, true /* canBeModified*/);
                            page.setPackageFragment(fragment, true /* canBeModified */);
                            break;
                        }
                    }
                }
            }
        } else if (roots.length > 0) {
            // if we haven't found a valid fragment, we set the root to the first source folder.
            page.setPackageFragmentRoot(roots[0], true /* canBeModified*/);
        }

        // if we have a starting class name we use it
        if (className != null) {
            page.setTypeName(className, true /* canBeModified*/);
        }

        // create the action that will open it the wizard.
        OpenNewClassWizardAction action = new OpenNewClassWizardAction();
        action.setConfiguredWizardPage(page);
        action.run();
        IJavaElement element = action.getCreatedElement();

        if (element == null) {
            // lets delete the packages we created just for this.
            // we need to start with the leaf and go up
            if (createdFragments != null) {
                try {
                    for (int i = createdFragments.size() - 1 ; i >= 0 ; i--) {
                        createdFragments.get(i).delete(true /* force*/,
                                                       new NullProgressMonitor());
                    }
                } catch (JavaModelException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Computes and return the {@link IPackageFragmentRoot}s corresponding to the source
     * folders of the specified project.
     *
     * @param project the project
     * @param includeContainers True to include containers
     * @param skipGenFolder True to skip the "gen" folder
     * @return an array of IPackageFragmentRoot.
     */
    private IPackageFragmentRoot[] getPackageFragmentRoots(IProject project,
            boolean includeContainers, boolean skipGenFolder) {
        ArrayList<IPackageFragmentRoot> result = new ArrayList<IPackageFragmentRoot>();
        try {
            IJavaProject javaProject = JavaCore.create(project);
            IPackageFragmentRoot[] roots = javaProject.getPackageFragmentRoots();
            for (int i = 0; i < roots.length; i++) {
                if (skipGenFolder) {
                    IResource resource = roots[i].getResource();
                    if (resource != null && resource.getName().equals(FD_GEN_SOURCES)) {
                        continue;
                    }
                }
                IClasspathEntry entry = roots[i].getRawClasspathEntry();
                if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE ||
                        (includeContainers &&
                                entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER)) {
                    result.add(roots[i]);
                }
            }
        } catch (JavaModelException e) {
        }

        return result.toArray(new IPackageFragmentRoot[result.size()]);
    }

    /**
     * Reopens this file as included within the given file (this assumes that the given
     * file has an include tag referencing this view, and the set of views that have this
     * property can be found using the {@link IncludeFinder}.
     *
     * @param includeWithin reference to a file to include as a surrounding context,
     *   or null to show the file standalone
     */
    public void showIn(Reference includeWithin) {
        mIncludedWithin = includeWithin;

        if (includeWithin != null) {
            IFile file = includeWithin.getFile();

            // Update configuration
            if (file != null) {
                mConfigComposite.resetConfigFor(file);
            }
        }
        recomputeLayout();
    }

    /**
     * Returns the resource name of the file that is including this current layout, if any
     * (may be null)
     *
     * @return the resource name of an including layout, or null
     */
    public Reference getIncludedWithin() {
        return mIncludedWithin;
    }

    /**
     * Return all resource names of a given type, either in the project or in the
     * framework.
     *
     * @param framework if true, return all the framework resource names, otherwise return
     *            all the project resource names
     * @param type the type of resource to look up
     * @return a collection of resource names, never null but possibly empty
     */
    public Collection<String> getResourceNames(boolean framework, ResourceType type) {
        Map<ResourceType, Map<String, ResourceValue>> map =
            framework ? mConfiguredFrameworkRes : mConfiguredProjectRes;
        Map<String, ResourceValue> animations = map.get(type);
        if (animations != null) {
            return animations.keySet();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Return this editor's current configuration
     *
     * @return the current configuration
     */
    public FolderConfiguration getConfiguration() {
        return mConfigComposite.getCurrentConfig();
    }

    /**
     * Figures out the project's minSdkVersion and targetSdkVersion and return whether the values
     * have changed.
     */
    private boolean computeSdkVersion() {
        int oldMinSdkVersion = mMinSdkVersion;
        int oldTargetSdkVersion = mTargetSdkVersion;

        Pair<Integer, Integer> v = ManifestInfo.computeSdkVersions(mEditedFile.getProject());
        mMinSdkVersion = v.getFirst();
        mTargetSdkVersion = v.getSecond();

        return oldMinSdkVersion != mMinSdkVersion || oldTargetSdkVersion != mTargetSdkVersion;
    }

    public ConfigurationComposite getConfigurationComposite() {
        return mConfigComposite;
    }

    public LayoutActionBar getLayoutActionBar() {
        return mActionBar;
    }

    /**
     * Returns the target SDK version
     *
     * @return the target SDK version
     */
    public int getTargetSdkVersion() {
        return mTargetSdkVersion;
    }

    /**
     * Returns the minimum SDK version
     *
     * @return the minimum SDK version
     */
    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }
}
