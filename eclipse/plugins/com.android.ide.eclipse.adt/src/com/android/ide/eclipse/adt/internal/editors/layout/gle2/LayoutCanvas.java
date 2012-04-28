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

import com.android.ide.common.api.INode;
import com.android.ide.common.api.Margins;
import com.android.ide.common.api.Point;
import com.android.ide.common.layout.LayoutConstants;
import com.android.ide.common.rendering.api.Capability;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.XmlnsAttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.LayoutEditor;
import com.android.ide.eclipse.adt.internal.editors.layout.configuration.ConfigurationComposite;
import com.android.ide.eclipse.adt.internal.editors.layout.descriptors.ViewElementDescriptor;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.IncludeFinder.Reference;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.NodeFactory;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.RulesEngine;
import com.android.ide.eclipse.adt.internal.editors.layout.gre.ViewMetadataRepository;
import com.android.ide.eclipse.adt.internal.editors.layout.uimodel.UiViewElementNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiDocumentNode;
import com.android.ide.eclipse.adt.internal.editors.uimodel.UiElementNode;
import com.android.resources.Density;
import com.android.sdklib.SdkConstants;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSource;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Displays the image rendered by the {@link GraphicalEditorPart} and handles
 * the interaction with the widgets.
 * <p/>
 * {@link LayoutCanvas} implements the "Canvas" control. The editor part
 * actually uses the {@link LayoutCanvasViewer}, which is a JFace viewer wrapper
 * around this control.
 * <p/>
 * The LayoutCanvas contains the painting logic for the canvas. Selection,
 * clipboard, view management etc. is handled in separate helper classes.
 *
 * @since GLE2
 */
@SuppressWarnings("restriction") // For WorkBench "Show In" support
public class LayoutCanvas extends Canvas {
    private final static QualifiedName NAME_ZOOM =
        new QualifiedName(AdtPlugin.PLUGIN_ID, "zoom");//$NON-NLS-1$

    private static final boolean DEBUG = false;

    /* package */ static final String PREFIX_CANVAS_ACTION = "canvas_action_";

    /** The layout editor that uses this layout canvas. */
    private final LayoutEditor mLayoutEditor;

    /** The Rules Engine, associated with the current project. */
    private RulesEngine mRulesEngine;

    /** GC wrapper given to the IViewRule methods. The GC itself is only defined in the
     *  context of {@link #onPaint(PaintEvent)}; otherwise it is null. */
    private GCWrapper mGCWrapper;

    /** Default font used on the canvas. Do not dispose, it's a system font. */
    private Font mFont;

    /** Current hover view info. Null when no mouse hover. */
    private CanvasViewInfo mHoverViewInfo;

    /** When true, always display the outline of all views. */
    private boolean mShowOutline;

    /** When true, display the outline of all empty parent views. */
    private boolean mShowInvisible;

    /** Drop target associated with this composite. */
    private DropTarget mDropTarget;

    /** Factory that can create {@link INode} proxies. */
    private final NodeFactory mNodeFactory = new NodeFactory(this);

    /** Vertical scaling & scrollbar information. */
    private CanvasTransform mVScale;

    /** Horizontal scaling & scrollbar information. */
    private CanvasTransform mHScale;

    /** Drag source associated with this canvas. */
    private DragSource mDragSource;

    /**
     * The current Outline Page, to set its model.
     * It isn't possible to call OutlinePage2.dispose() in this.dispose().
     * this.dispose() is called from GraphicalEditorPart.dispose(),
     * when page's widget is already disposed.
     * Added the DisposeListener to OutlinePage2 in order to correctly dispose this page.
     **/
    private OutlinePage mOutlinePage;

    /** Delete action for the Edit or context menu. */
    private Action mDeleteAction;

    /** Select-All action for the Edit or context menu. */
    private Action mSelectAllAction;

    /** Paste action for the Edit or context menu. */
    private Action mPasteAction;

    /** Cut action for the Edit or context menu. */
    private Action mCutAction;

    /** Copy action for the Edit or context menu. */
    private Action mCopyAction;

    /** Root of the context menu. */
    private MenuManager mMenuManager;

    /** The view hierarchy associated with this canvas. */
    private final ViewHierarchy mViewHierarchy = new ViewHierarchy(this);

    /** The selection in the canvas. */
    private final SelectionManager mSelectionManager = new SelectionManager(this);

    /** The overlay which paints the optional outline. */
    private OutlineOverlay mOutlineOverlay;

    /** The overlay which paints outlines around empty children */
    private EmptyViewsOverlay mEmptyOverlay;

    /** The overlay which paints the mouse hover. */
    private HoverOverlay mHoverOverlay;

    /** The overlay which paints the selection. */
    private SelectionOverlay mSelectionOverlay;

    /** The overlay which paints the rendered layout image. */
    private ImageOverlay mImageOverlay;

    /** The overlay which paints masks hiding everything but included content. */
    private IncludeOverlay mIncludeOverlay;

    /**
     * Gesture Manager responsible for identifying mouse, keyboard and drag and
     * drop events.
     */
    private final GestureManager mGestureManager = new GestureManager(this);

    /**
     * When set, performs a zoom-to-fit when the next rendering image arrives.
     */
    private boolean mZoomFitNextImage;

    /**
     * Native clipboard support.
     */
    private ClipboardSupport mClipboardSupport;

    public LayoutCanvas(LayoutEditor layoutEditor,
            RulesEngine rulesEngine,
            Composite parent,
            int style) {
        super(parent, style | SWT.DOUBLE_BUFFERED | SWT.V_SCROLL | SWT.H_SCROLL);
        mLayoutEditor = layoutEditor;
        mRulesEngine = rulesEngine;

        mClipboardSupport = new ClipboardSupport(this, parent);
        mHScale = new CanvasTransform(this, getHorizontalBar());
        mVScale = new CanvasTransform(this, getVerticalBar());

        // Unit test suite passes a null here; TODO: Replace with mocking
        IFile file = layoutEditor != null ? layoutEditor.getInputFile() : null;
        if (file != null) {
            String zoom = AdtPlugin.getFileProperty(file, NAME_ZOOM);
            if (zoom != null) {
                try {
                    double initialScale = Double.parseDouble(zoom);
                    if (initialScale > 0.1) {
                        mHScale.setScale(initialScale);
                        mVScale.setScale(initialScale);
                    }
                } catch (NumberFormatException nfe) {
                    // Ignore - use zoom=100%
                }
            } else {
                mZoomFitNextImage = true;
            }
        }

        mGCWrapper = new GCWrapper(mHScale, mVScale);

        Display display = getDisplay();
        mFont = display.getSystemFont();

        // --- Set up graphic overlays
        // mOutlineOverlay and mEmptyOverlay are initialized lazily
        mHoverOverlay = new HoverOverlay(this, mHScale, mVScale);
        mHoverOverlay.create(display);
        mSelectionOverlay = new SelectionOverlay(this);
        mSelectionOverlay.create(display);
        mImageOverlay = new ImageOverlay(this, mHScale, mVScale);
        mIncludeOverlay = new IncludeOverlay(this);
        mImageOverlay.create(display);

        // --- Set up listeners
        addPaintListener(new PaintListener() {
            public void paintControl(PaintEvent e) {
                onPaint(e);
            }
        });

        addControlListener(new ControlAdapter() {
            @Override
            public void controlResized(ControlEvent e) {
                super.controlResized(e);
                mHScale.setClientSize(getClientArea().width);
                mVScale.setClientSize(getClientArea().height);
            }
        });

        // --- setup drag'n'drop ---
        // DND Reference: http://www.eclipse.org/articles/Article-SWT-DND/DND-in-SWT.html

        mDropTarget = createDropTarget(this);
        mDragSource = createDragSource(this);
        mGestureManager.registerListeners(mDragSource, mDropTarget);

        if (mLayoutEditor == null) {
            // TODO: In another CL we should use EasyMock/objgen to provide an editor.
            return; // Unit test
        }

        // --- setup context menu ---
        setupGlobalActionHandlers();
        createContextMenu();

        // --- setup outline ---
        // Get the outline associated with this editor, if any and of the right type.
        Object outline = layoutEditor.getAdapter(IContentOutlinePage.class);
        if (outline instanceof OutlinePage) {
            mOutlinePage = (OutlinePage) outline;
        }
    }

    public void handleKeyPressed(KeyEvent e) {
        // Set up backspace as an alias for the delete action within the canvas.
        // On most Macs there is no delete key - though there IS a key labeled
        // "Delete" and it sends a backspace key code! In short, for Macs we should
        // treat backspace as delete, and it's harmless (and probably useful) to
        // handle backspace for other platforms as well.
        if (e.keyCode == SWT.BS) {
            mDeleteAction.run();
        } else if (e.keyCode == SWT.ESC) {
            mSelectionManager.selectParent();
        } else {
            // Zooming actions
            char c = e.character;
            LayoutActionBar actionBar = mLayoutEditor.getGraphicalEditor()
                    .getLayoutActionBar();
            if (c == '1' && actionBar.isZoomingAllowed()) {
                setScale(1, true);
            } else if (c == '0' && actionBar.isZoomingAllowed()) {
                setFitScale(true);
            } else if (e.keyCode == '0' && (e.stateMask & SWT.MOD2) != 0
                    && actionBar.isZoomingAllowed()) {
                setFitScale(false);
            } else if (c == '+' && actionBar.isZoomingAllowed()) {
                actionBar.rescale(1);
            } else if (c == '-' && actionBar.isZoomingAllowed()) {
                actionBar.rescale(-1);
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();

        mGestureManager.unregisterListeners(mDragSource, mDropTarget);

        if (mDropTarget != null) {
            mDropTarget.dispose();
            mDropTarget = null;
        }

        if (mRulesEngine != null) {
            mRulesEngine.dispose();
            mRulesEngine = null;
        }

        if (mDragSource != null) {
            mDragSource.dispose();
            mDragSource = null;
        }

        if (mClipboardSupport != null) {
            mClipboardSupport.dispose();
            mClipboardSupport = null;
        }

        if (mGCWrapper != null) {
            mGCWrapper.dispose();
            mGCWrapper = null;
        }

        if (mOutlineOverlay != null) {
            mOutlineOverlay.dispose();
            mOutlineOverlay = null;
        }

        if (mEmptyOverlay != null) {
            mEmptyOverlay.dispose();
            mEmptyOverlay = null;
        }

        if (mHoverOverlay != null) {
            mHoverOverlay.dispose();
            mHoverOverlay = null;
        }

        if (mSelectionOverlay != null) {
            mSelectionOverlay.dispose();
            mSelectionOverlay = null;
        }

        if (mImageOverlay != null) {
            mImageOverlay.dispose();
            mImageOverlay = null;
        }

        if (mIncludeOverlay != null) {
            mIncludeOverlay.dispose();
            mIncludeOverlay = null;
        }

        mViewHierarchy.dispose();
    }

    /** Returns the Rules Engine, associated with the current project. */
    /* package */ RulesEngine getRulesEngine() {
        return mRulesEngine;
    }

    /** Sets the Rules Engine, associated with the current project. */
    /* package */ void setRulesEngine(RulesEngine rulesEngine) {
        mRulesEngine = rulesEngine;
    }

    /**
     * Returns the factory to use to convert from {@link CanvasViewInfo} or from
     * {@link UiViewElementNode} to {@link INode} proxies.
     */
    /* package */ NodeFactory getNodeFactory() {
        return mNodeFactory;
    }

    /**
     * Returns the GCWrapper used to paint view rules.
     *
     * @return The GCWrapper used to paint view rules
     */
    /* package */ GCWrapper getGcWrapper() {
        return mGCWrapper;
    }

    /**
     * Returns the {@link LayoutEditor} associated with this canvas.
     */
    public LayoutEditor getLayoutEditor() {
        return mLayoutEditor;
    }

    /**
     * Returns the current {@link ImageOverlay} painting the rendered result
     *
     * @return the image overlay responsible for painting the rendered result, never null
     */
    ImageOverlay getImageOverlay() {
        return mImageOverlay;
    }

    /**
     * Returns the current {@link SelectionOverlay} painting the selection highlights
     *
     * @return the selection overlay responsible for painting the selection highlights,
     *         never null
     */
    SelectionOverlay getSelectionOverlay() {
        return mSelectionOverlay;
    }

    /**
     * Returns the {@link GestureManager} associated with this canvas.
     *
     * @return the {@link GestureManager} associated with this canvas, never null.
     */
    GestureManager getGestureManager() {
        return mGestureManager;
    }

    /**
     * Returns the current {@link HoverOverlay} painting the mouse hover.
     *
     * @return the hover overlay responsible for painting the mouse hover,
     *         never null
     */
    HoverOverlay getHoverOverlay() {
        return mHoverOverlay;
    }

    /**
     * Returns the horizontal {@link CanvasTransform} transform object, which can map
     * a layout point into a control point.
     *
     * @return A {@link CanvasTransform} for mapping between layout and control
     *         coordinates in the horizontal dimension.
     */
    /* package */ CanvasTransform getHorizontalTransform() {
        return mHScale;
    }

    /**
     * Returns the vertical {@link CanvasTransform} transform object, which can map a
     * layout point into a control point.
     *
     * @return A {@link CanvasTransform} for mapping between layout and control
     *         coordinates in the vertical dimension.
     */
    /* package */ CanvasTransform getVerticalTransform() {
        return mVScale;
    }

    /**
     * Returns the {@link OutlinePage} associated with this canvas
     *
     * @return the {@link OutlinePage} associated with this canvas
     */
    public OutlinePage getOutlinePage() {
        return mOutlinePage;
    }

    /**
     * Returns the {@link SelectionManager} associated with this canvas.
     *
     * @return The {@link SelectionManager} holding the selection for this
     *         canvas. Never null.
     */
    public SelectionManager getSelectionManager() {
        return mSelectionManager;
    }

    /**
     * Returns the {@link ViewHierarchy} object associated with this canvas,
     * holding the most recent rendered view of the scene, if valid.
     *
     * @return The {@link ViewHierarchy} object associated with this canvas.
     *         Never null.
     */
    public ViewHierarchy getViewHierarchy() {
        return mViewHierarchy;
    }

    /**
     * Returns the {@link ClipboardSupport} object associated with this canvas.
     *
     * @return The {@link ClipboardSupport} object for this canvas. Null only after dispose.
     */
    public ClipboardSupport getClipboardSupport() {
        return mClipboardSupport;
    }

    /** Returns the Select All action bound to this canvas */
    Action getSelectAllAction() {
        return mSelectAllAction;
    }

    /**
     * Sets the result of the layout rendering. The result object indicates if the layout
     * rendering succeeded. If it did, it contains a bitmap and the objects rectangles.
     *
     * Implementation detail: the bridge's computeLayout() method already returns a newly
     * allocated ILayourResult. That means we can keep this result and hold on to it
     * when it is valid.
     *
     * @param session The new scene, either valid or not.
     * @param explodedNodes The set of individual nodes the layout computer was asked to
     *            explode. Note that these are independent of the explode-all mode where
     *            all views are exploded; this is used only for the mode (
     *            {@link #showInvisibleViews(boolean)}) where individual invisible nodes
     *            are padded during certain interactions.
     */
    /* package */ void setSession(RenderSession session, Set<UiElementNode> explodedNodes,
            boolean layoutlib5) {
        // disable any hover
        clearHover();

        mViewHierarchy.setSession(session, explodedNodes, layoutlib5);
        if (mViewHierarchy.isValid() && session != null) {
            Image image = mImageOverlay.setImage(session.getImage(), session.isAlphaChannelImage());

            mOutlinePage.setModel(mViewHierarchy.getRoot());

            if (image != null) {
                mHScale.setSize(image.getImageData().width, getClientArea().width);
                mVScale.setSize(image.getImageData().height, getClientArea().height);
                if (mZoomFitNextImage) {
                    mZoomFitNextImage = false;
                    // Must be run asynchronously because getClientArea() returns 0 bounds
                    // when the editor is being initialized
                    getDisplay().asyncExec(new Runnable() {
                        public void run() {
                            setFitScale(true);
                        }
                    });
                }
            }
        }

        redraw();
    }

    /* package */ void setShowOutline(boolean newState) {
        mShowOutline = newState;
        redraw();
    }

    public double getScale() {
        return mHScale.getScale();
    }

    /* package */ void setScale(double scale, boolean redraw) {
        if (scale <= 0.0) {
            scale = 1.0;
        }

        if (scale == getScale()) {
            return;
        }

        mHScale.setScale(scale);
        mVScale.setScale(scale);
        if (redraw) {
            redraw();
        }

        // Clear the zoom setting if it is almost identical to 1.0
        String zoomValue = (Math.abs(scale - 1.0) < 0.0001) ? null : Double.toString(scale);
        AdtPlugin.setFileProperty(mLayoutEditor.getInputFile(), NAME_ZOOM, zoomValue);
    }

    /**
     * Scales the canvas to best fit
     *
     * @param onlyZoomOut if true, then the zooming factor will never be larger than 1,
     *            which means that this function will zoom out if necessary to show the
     *            rendered image, but it will never zoom in.
     */
    void setFitScale(boolean onlyZoomOut) {
        Image image = getImageOverlay().getImage();
        if (image != null) {
            Rectangle canvasSize = getClientArea();
            int canvasWidth = canvasSize.width;
            int canvasHeight = canvasSize.height;

            ImageData imageData = image.getImageData();
            int sceneWidth = imageData.width;
            int sceneHeight = imageData.height;
            if (sceneWidth == 0.0 || sceneHeight == 0.0) {
                return;
            }

            // Reduce the margins if necessary
            int hDelta = canvasWidth - sceneWidth;
            int hMargin = 0;
            if (hDelta > 2 * CanvasTransform.DEFAULT_MARGIN) {
                hMargin = CanvasTransform.DEFAULT_MARGIN;
            } else if (hDelta > 0) {
                hMargin = hDelta / 2;
            }

            int vDelta = canvasHeight - sceneHeight;
            int vMargin = 0;
            if (vDelta > 2 * CanvasTransform.DEFAULT_MARGIN) {
                vMargin = CanvasTransform.DEFAULT_MARGIN;
            } else if (vDelta > 0) {
                vMargin = vDelta / 2;
            }

            double hScale = (canvasWidth - 2 * hMargin) / (double) sceneWidth;
            double vScale = (canvasHeight - 2 * vMargin) / (double) sceneHeight;

            double scale = Math.min(hScale, vScale);

            if (onlyZoomOut) {
                scale = Math.min(1.0, scale);
            }

            setScale(scale, true);
        }
    }

    /**
     * Transforms a point, expressed in layout coordinates, into "client" coordinates
     * relative to the control (and not relative to the display).
     *
     * @param canvasX X in the canvas coordinates
     * @param canvasY Y in the canvas coordinates
     * @return A new {@link Point} in control client coordinates (not display coordinates)
     */
    /* package */ Point layoutToControlPoint(int canvasX, int canvasY) {
        int x = mHScale.translate(canvasX);
        int y = mVScale.translate(canvasY);
        return new Point(x, y);
    }

    /**
     * Returns the action for the context menu corresponding to the given action id.
     * <p/>
     * For global actions such as copy or paste, the action id must be composed of
     * the {@link #PREFIX_CANVAS_ACTION} followed by one of {@link ActionFactory}'s
     * action ids.
     * <p/>
     * Returns null if there's no action for the given id.
     */
    /* package */ IAction getAction(String actionId) {
        String prefix = PREFIX_CANVAS_ACTION;
        if (mMenuManager == null ||
                actionId == null ||
                !actionId.startsWith(prefix)) {
            return null;
        }

        actionId = actionId.substring(prefix.length());

        for (IContributionItem contrib : mMenuManager.getItems()) {
            if (contrib instanceof ActionContributionItem &&
                    actionId.equals(contrib.getId())) {
                return ((ActionContributionItem) contrib).getAction();
            }
        }

        return null;
    }

    //---------------

    /**
     * Paints the canvas in response to paint events.
     */
    private void onPaint(PaintEvent e) {
        GC gc = e.gc;
        gc.setFont(mFont);
        mGCWrapper.setGC(gc);
        try {
            if (!mImageOverlay.isHiding()) {
                mImageOverlay.paint(gc);
            }

            if (mShowOutline) {
                if (mOutlineOverlay == null) {
                    mOutlineOverlay = new OutlineOverlay(mViewHierarchy, mHScale, mVScale);
                    mOutlineOverlay.create(getDisplay());
                }
                if (!mOutlineOverlay.isHiding()) {
                    mOutlineOverlay.paint(gc);
                }
            }

            if (mShowInvisible) {
                if (mEmptyOverlay == null) {
                    mEmptyOverlay = new EmptyViewsOverlay(mViewHierarchy, mHScale, mVScale);
                    mEmptyOverlay.create(getDisplay());
                }
                if (!mEmptyOverlay.isHiding()) {
                    mEmptyOverlay.paint(gc);
                }
            }

            if (!mHoverOverlay.isHiding()) {
                mHoverOverlay.paint(gc);
            }
            if (!mIncludeOverlay.isHiding()) {
                mIncludeOverlay.paint(gc);
            }

            if (!mSelectionOverlay.isHiding()) {
                mSelectionOverlay.paint(mSelectionManager, mGCWrapper, gc, mRulesEngine);
            }
            mGestureManager.paint(gc);

        } finally {
            mGCWrapper.setGC(null);
        }
    }

    /**
     * Shows or hides invisible parent views, which are views which have empty bounds and
     * no children. The nodes which will be shown are provided by
     * {@link #getNodesToExplode()}.
     *
     * @param show When true, any invisible parent nodes are padded and highlighted
     *            ("exploded"), and when false any formerly exploded nodes are hidden.
     */
    /* package */ void showInvisibleViews(boolean show) {
        if (mShowInvisible == show) {
            return;
        }
        mShowInvisible = show;

        // Optimization: Avoid doing work when we don't have invisible parents (on show)
        // or formerly exploded nodes (on hide).
        if (show && !mViewHierarchy.hasInvisibleParents()) {
            return;
        } else if (!show && !mViewHierarchy.hasExplodedParents()) {
            return;
        }

        mLayoutEditor.recomputeLayout();
    }

    /**
     * Returns a set of nodes that should be exploded (forced non-zero padding during render),
     * or null if no nodes should be exploded. (Note that this is independent of the
     * explode-all mode, where all nodes are padded -- that facility does not use this
     * mechanism, which is only intended to be used to expose invisible parent nodes.
     *
     * @return The set of invisible parents, or null if no views should be expanded.
     */
    public Set<UiElementNode> getNodesToExplode() {
        if (mShowInvisible) {
            return mViewHierarchy.getInvisibleNodes();
        }

        // IF we have selection, and IF we have invisible nodes in the view,
        // see if any of the selected items are among the invisible nodes, and if so
        // add them to a lazily constructed set which we pass back for rendering.
        Set<UiElementNode> result = null;
        List<SelectionItem> selections = mSelectionManager.getSelections();
        if (selections.size() > 0) {
            List<CanvasViewInfo> invisibleParents = mViewHierarchy.getInvisibleViews();
            if (invisibleParents.size() > 0) {
                for (SelectionItem item : selections) {
                    CanvasViewInfo viewInfo = item.getViewInfo();
                    // O(n^2) here, but both the selection size and especially the
                    // invisibleParents size are expected to be small
                    if (invisibleParents.contains(viewInfo)) {
                        UiViewElementNode node = viewInfo.getUiViewNode();
                        if (node != null) {
                            if (result == null) {
                                result = new HashSet<UiElementNode>();
                            }
                            result.add(node);
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Clears the hover.
     */
    /* package */ void clearHover() {
        mHoverOverlay.clearHover();
    }

    /**
     * Hover on top of a known child.
     */
    /* package */ void hover(MouseEvent e) {
        // Check if a button is pressed; no hovers during drags
        if ((e.stateMask & SWT.BUTTON_MASK) != 0) {
            clearHover();
            return;
        }

        LayoutPoint p = ControlPoint.create(this, e).toLayout();
        CanvasViewInfo vi = mViewHierarchy.findViewInfoAt(p);

        // We don't hover on the root since it's not a widget per see and it is always there.
        // We also skip spacers...
        if (vi != null && (vi.isRoot() || vi.isHidden())) {
            vi = null;
        }

        boolean needsUpdate = vi != mHoverViewInfo;
        mHoverViewInfo = vi;

        if (vi == null) {
            clearHover();
        } else {
            Rectangle r = vi.getSelectionRect();
            mHoverOverlay.setHover(r.x, r.y, r.width, r.height);
        }

        if (needsUpdate) {
            redraw();
        }
    }

    /**
     * Shows the given {@link CanvasViewInfo}, which can mean exposing its XML or if it's
     * an included element, its corresponding file.
     *
     * @param vi the {@link CanvasViewInfo} to be shown
     */
    public void show(CanvasViewInfo vi) {
        String url = vi.getIncludeUrl();
        if (url != null) {
            showInclude(url);
        } else {
            showXml(vi);
        }
    }

    /**
     * Shows the layout file referenced by the given url in the same project.
     *
     * @param url The layout attribute url of the form @layout/foo
     */
    private void showInclude(String url) {
        GraphicalEditorPart graphicalEditor = mLayoutEditor.getGraphicalEditor();
        IPath filePath = graphicalEditor.findResourceFile(url);
        if (filePath == null) {
            // Should not be possible - if the URL had been bad, then we wouldn't
            // have been able to render the scene and you wouldn't have been able
            // to click on it
            return;
        }

        // Save the including file, if necessary: without it, the "Show Included In"
        // facility which is invoked automatically will not work properly if the <include>
        // tag is not in the saved version of the file, since the outer file is read from
        // disk rather than from memory.
        IEditorSite editorSite = graphicalEditor.getEditorSite();
        IWorkbenchPage page = editorSite.getPage();
        page.saveEditor(mLayoutEditor, false);

        IWorkspaceRoot workspace = ResourcesPlugin.getWorkspace().getRoot();
        IFile xmlFile = null;
        IPath workspacePath = workspace.getLocation();
        if (workspacePath.isPrefixOf(filePath)) {
            IPath relativePath = filePath.makeRelativeTo(workspacePath);
            xmlFile = (IFile) workspace.findMember(relativePath);
        } else if (filePath.isAbsolute()) {
            xmlFile = workspace.getFileForLocation(filePath);
        }
        if (xmlFile != null) {
            IFile leavingFile = graphicalEditor.getEditedFile();
            Reference next = Reference.create(graphicalEditor.getEditedFile());

            try {
                IEditorPart openAlready = EditorUtility.isOpenInEditor(xmlFile);

                // Show the included file as included within this click source?
                if (openAlready != null) {
                    if (openAlready instanceof LayoutEditor) {
                        LayoutEditor editor = (LayoutEditor)openAlready;
                        GraphicalEditorPart gEditor = editor.getGraphicalEditor();
                        if (gEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
                            gEditor.showIn(next);
                        }
                    }
                } else {
                    try {
                        // Set initial state of a new file
                        // TODO: Only set rendering target portion of the state
                        QualifiedName qname = ConfigurationComposite.NAME_CONFIG_STATE;
                        String state = AdtPlugin.getFileProperty(leavingFile, qname);
                        xmlFile.setSessionProperty(GraphicalEditorPart.NAME_INITIAL_STATE,
                                state);
                    } catch (CoreException e) {
                        // pass
                    }

                    if (graphicalEditor.renderingSupports(Capability.EMBEDDED_LAYOUT)) {
                        try {
                            xmlFile.setSessionProperty(GraphicalEditorPart.NAME_INCLUDE, next);
                        } catch (CoreException e) {
                            // pass - worst that can happen is that we don't
                            //start with inclusion
                        }
                    }
                }

                EditorUtility.openInEditor(xmlFile, true);
                return;
            } catch (PartInitException ex) {
                AdtPlugin.log(ex, "Can't open %$1s", url); //$NON-NLS-1$
            }
        } else {
            // It's not a path in the workspace; look externally
            // (this is probably an @android: path)
            if (filePath.isAbsolute()) {
                IFileStore fileStore = EFS.getLocalFileSystem().getStore(filePath);
                // fileStore = fileStore.getChild(names[i]);
                if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists()) {
                    try {
                        IDE.openEditorOnFileStore(page, fileStore);
                        return;
                    } catch (PartInitException ex) {
                        AdtPlugin.log(ex, "Can't open %$1s", url); //$NON-NLS-1$
                    }
                }
            }
        }

        // Failed: display message to the user
        String message = String.format("Could not find resource %1$s", url);
        IStatusLineManager status = editorSite.getActionBars().getStatusLineManager();
        status.setErrorMessage(message);
        getDisplay().beep();
    }

    /**
     * Returns the layout resource name of this layout
     *
     * @return the layout resource name of this layout
     */
    public String getLayoutResourceName() {
        GraphicalEditorPart graphicalEditor = mLayoutEditor.getGraphicalEditor();
        return graphicalEditor.getLayoutResourceName();
    }

    /**
     * Returns the layout resource url of the current layout
     *
     * @return
     */
    /*
    public String getMe() {
        GraphicalEditorPart graphicalEditor = mLayoutEditor.getGraphicalEditor();
        IFile editedFile = graphicalEditor.getEditedFile();
        return editedFile.getProjectRelativePath().toOSString();
    }
     */

    /**
     * Show the XML element corresponding to the given {@link CanvasViewInfo} (unless it's
     * a root).
     *
     * @param vi The clicked {@link CanvasViewInfo} whose underlying XML element we want
     *            to view
     */
    private void showXml(CanvasViewInfo vi) {
        // Warp to the text editor and show the corresponding XML for the
        // double-clicked widget
        if (vi.isRoot()) {
            return;
        }

        Node xmlNode = vi.getXmlNode();
        if (xmlNode != null) {
            boolean found = mLayoutEditor.show(xmlNode);
            if (!found) {
                getDisplay().beep();
            }
        }
    }

    //---------------

    /**
     * Helper to create the drag source for the given control.
     * <p/>
     * This is static with package-access so that {@link OutlinePage} can also
     * create an exact copy of the source with the same attributes.
     */
    /* package */static DragSource createDragSource(Control control) {
        DragSource source = new DragSource(control, DND.DROP_COPY | DND.DROP_MOVE);
        source.setTransfer(new Transfer[] {
                TextTransfer.getInstance(),
                SimpleXmlTransfer.getInstance()
        });
        return source;
    }

    /**
     * Helper to create the drop target for the given control.
     */
    private static DropTarget createDropTarget(Control control) {
        DropTarget dropTarget = new DropTarget(
                control, DND.DROP_COPY | DND.DROP_MOVE | DND.DROP_DEFAULT);
        dropTarget.setTransfer(new Transfer[] {
            SimpleXmlTransfer.getInstance()
        });
        return dropTarget;
    }

    //---------------

    /**
     * Invoked by the constructor to add our cut/copy/paste/delete/select-all
     * handlers in the global action handlers of this editor's site.
     * <p/>
     * This will enable the menu items under the global Edit menu and make them
     * invoke our actions as needed. As a benefit, the corresponding shortcut
     * accelerators will do what one would expect.
     */
    private void setupGlobalActionHandlers() {
        mCutAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.cutSelectionToClipboard(mSelectionManager.getSnapshot());
                updateMenuActionState();
            }
        };

        copyActionAttributes(mCutAction, ActionFactory.CUT);

        mCopyAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.copySelectionToClipboard(mSelectionManager.getSnapshot());
                updateMenuActionState();
            }
        };

        copyActionAttributes(mCopyAction, ActionFactory.COPY);

        mPasteAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.pasteSelection(mSelectionManager.getSnapshot());
                updateMenuActionState();
            }
        };

        copyActionAttributes(mPasteAction, ActionFactory.PASTE);

        mDeleteAction = new Action() {
            @Override
            public void run() {
                mClipboardSupport.deleteSelection(
                        getDeleteLabel(),
                        mSelectionManager.getSnapshot());
            }
        };

        copyActionAttributes(mDeleteAction, ActionFactory.DELETE);

        mSelectAllAction = new Action() {
            @Override
            public void run() {
                GraphicalEditorPart graphicalEditor = getLayoutEditor().getGraphicalEditor();
                StyledText errorLabel = graphicalEditor.getErrorLabel();
                if (errorLabel.isFocusControl()) {
                    errorLabel.selectAll();
                    return;
                }

                mSelectionManager.selectAll();
            }
        };

        copyActionAttributes(mSelectAllAction, ActionFactory.SELECT_ALL);
    }

    /* package */ String getCutLabel() {
        return mCutAction.getText();
    }

    /* package */ String getDeleteLabel() {
        // verb "Delete" from the DELETE action's title
        return mDeleteAction.getText();
    }

    /**
     * Updates menu actions that depends on the selection.
     */
    void updateMenuActionState() {
        List<SelectionItem> selections = getSelectionManager().getSelections();
        boolean hasSelection = !selections.isEmpty();
        if (hasSelection && selections.size() == 1 && selections.get(0).isRoot()) {
            hasSelection = false;
        }

        StyledText errorLabel = mLayoutEditor.getGraphicalEditor().getErrorLabel();
        mCutAction.setEnabled(hasSelection);
        mCopyAction.setEnabled(hasSelection || errorLabel.getSelectionCount() > 0);
        mDeleteAction.setEnabled(hasSelection);
        // Select All should *always* be selectable, regardless of whether anything
        // is currently selected.
        mSelectAllAction.setEnabled(true);

        // The paste operation is only available if we can paste our custom type.
        // We do not currently support pasting random text (e.g. XML). Maybe later.
        boolean hasSxt = mClipboardSupport.hasSxtOnClipboard();
        mPasteAction.setEnabled(hasSxt);
    }

    /**
     * Update the actions when this editor is activated
     *
     * @param bars the action bar for this canvas
     */
    public void updateGlobalActions(IActionBars bars) {
        updateMenuActionState();

        bars.setGlobalActionHandler(ActionFactory.CUT.getId(), mCutAction);
        bars.setGlobalActionHandler(ActionFactory.COPY.getId(), mCopyAction);
        bars.setGlobalActionHandler(ActionFactory.PASTE.getId(), mPasteAction);
        bars.setGlobalActionHandler(ActionFactory.DELETE.getId(), mDeleteAction);
        bars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(), mSelectAllAction);

        ITextEditor editor = mLayoutEditor.getStructuredTextEditor();
        IAction undoAction = editor.getAction(ActionFactory.UNDO.getId());
        bars.setGlobalActionHandler(ActionFactory.UNDO.getId(), undoAction);
        IAction redoAction = editor.getAction(ActionFactory.REDO.getId());
        bars.setGlobalActionHandler(ActionFactory.REDO.getId(), redoAction);

        bars.updateActionBars();
    }

    /**
     * Helper for {@link #setupGlobalActionHandlers()}.
     * Copies the action attributes form the given {@link ActionFactory}'s action to
     * our action.
     * <p/>
     * {@link ActionFactory} provides access to the standard global actions in Eclipse.
     * <p/>
     * This allows us to grab the standard labels and icons for the
     * global actions such as copy, cut, paste, delete and select-all.
     */
    private void copyActionAttributes(Action action, ActionFactory factory) {
        IWorkbenchAction wa = factory.create(mLayoutEditor.getEditorSite().getWorkbenchWindow());
        action.setId(wa.getId());
        action.setText(wa.getText());
        action.setEnabled(wa.isEnabled());
        action.setDescription(wa.getDescription());
        action.setToolTipText(wa.getToolTipText());
        action.setAccelerator(wa.getAccelerator());
        action.setActionDefinitionId(wa.getActionDefinitionId());
        action.setImageDescriptor(wa.getImageDescriptor());
        action.setHoverImageDescriptor(wa.getHoverImageDescriptor());
        action.setDisabledImageDescriptor(wa.getDisabledImageDescriptor());
        action.setHelpListener(wa.getHelpListener());
    }

    /**
     * Creates the context menu for the canvas. This is called once from the canvas' constructor.
     * <p/>
     * The menu has a static part with actions that are always available such as
     * copy, cut, paste and show in > explorer. This is created by
     * {@link #setupStaticMenuActions(IMenuManager)}.
     * <p/>
     * There's also a dynamic part that is populated by the rules of the
     * selected elements, created by {@link DynamicContextMenu}.
     */
    private void createContextMenu() {

        // This manager is the root of the context menu.
        mMenuManager = new MenuManager() {
            @Override
            public boolean isDynamic() {
                return true;
            }
        };

        // Fill the menu manager with the static & dynamic actions
        setupStaticMenuActions(mMenuManager);
        new DynamicContextMenu(mLayoutEditor, this, mMenuManager);
        Menu menu = mMenuManager.createContextMenu(this);
        setMenu(menu);

        // Add listener to detect when the menu is about to be posted, such that
        // we can sync the selection. Without this, you can right click on something
        // in the canvas which is NOT selected, and the context menu will show items related
        // to the selection, NOT the item you clicked on!!
        addMenuDetectListener(new MenuDetectListener() {
            public void menuDetected(MenuDetectEvent e) {
                mSelectionManager.menuClick(e);
            }
        });
    }

    /**
     * Invoked by {@link #createContextMenu()} to create our *static* context menu once.
     * <p/>
     * The content of the menu itself does not change. However the state of the
     * various items is controlled by their associated actions.
     * <p/>
     * For cut/copy/paste/delete/select-all, we explicitly reuse the actions
     * created by {@link #setupGlobalActionHandlers()}, so this method must be
     * invoked after that one.
     */
    private void setupStaticMenuActions(IMenuManager manager) {
        manager.removeAll();

        manager.add(new SelectionManager.SelectionMenu(mLayoutEditor.getGraphicalEditor()));
        manager.add(new Separator());
        manager.add(mCutAction);
        manager.add(mCopyAction);
        manager.add(mPasteAction);
        manager.add(new Separator());
        manager.add(mDeleteAction);
        manager.add(new Separator());
        manager.add(new PlayAnimationMenu(this));
        manager.add(new Separator());

        // Group "Show Included In" and "Show In" together
        manager.add(new ShowWithinMenu(mLayoutEditor));

        // Create a "Show In" sub-menu and automatically populate it using standard
        // actions contributed by the workbench.
        String showInLabel = IDEWorkbenchMessages.Workbench_showIn;
        MenuManager showInSubMenu = new MenuManager(showInLabel);
        showInSubMenu.add(
                ContributionItemFactory.VIEWS_SHOW_IN.create(
                        mLayoutEditor.getSite().getWorkbenchWindow()));
        manager.add(showInSubMenu);
    }

    /**
     * Deletes the selection. Equivalent to pressing the Delete key.
     */
    /* package */ void delete() {
        mDeleteAction.run();
    }

    /**
     * Add new root in an existing empty XML layout.
     * <p/>
     * In case of error (unknown FQCN, document not empty), silently do nothing.
     * In case of success, the new element will have some default attributes set
     * (xmlns:android, layout_width and height). The edit is wrapped in a proper
     * undo.
     * <p/>
     * This is invoked by
     * {@link MoveGesture#drop(org.eclipse.swt.dnd.DropTargetEvent)}.
     *
     * @param rootFqcn A non-null non-empty FQCN that must match an existing
     *            {@link ViewElementDescriptor} to add as root to the current
     *            empty XML document.
     */
    /* package */ void createDocumentRoot(String rootFqcn) {

        // Need a valid empty document to create the new root
        final UiDocumentNode uiDoc = mLayoutEditor.getUiRootNode();
        if (uiDoc == null || uiDoc.getUiChildren().size() > 0) {
            debugPrintf("Failed to create document root for %1$s: document is not empty", rootFqcn);
            return;
        }

        // Find the view descriptor matching our FQCN
        final ViewElementDescriptor viewDesc = mLayoutEditor.getFqcnViewDescriptor(rootFqcn);
        if (viewDesc == null) {
            // TODO this could happen if dropping a custom view not known in this project
            debugPrintf("Failed to add document root, unknown FQCN %1$s", rootFqcn);
            return;
        }

        // Get the last segment of the FQCN for the undo title
        String title = rootFqcn;
        int pos = title.lastIndexOf('.');
        if (pos > 0 && pos < title.length() - 1) {
            title = title.substring(pos + 1);
        }
        title = String.format("Create root %1$s in document", title);

        mLayoutEditor.wrapUndoEditXmlModel(title, new Runnable() {
            public void run() {
                UiElementNode uiNew = uiDoc.appendNewUiChild(viewDesc);

                // A root node requires the Android XMLNS
                uiNew.setAttributeValue(
                        LayoutConstants.ANDROID_NS_NAME,
                        XmlnsAttributeDescriptor.XMLNS_URI,
                        SdkConstants.NS_RESOURCES,
                        true /*override*/);

                // Adjust the attributes
                DescriptorsUtils.setDefaultLayoutAttributes(uiNew, false /*updateLayout*/);

                uiNew.createXmlNode();
            }
        });
    }

    /**
     * Returns the insets associated with views of the given fully qualified name, for the
     * current theme and screen type.
     *
     * @param fqcn the fully qualified name to the widget type
     * @return the insets, or null if unknown
     */
    public Margins getInsets(String fqcn) {
        if (ViewMetadataRepository.INSETS_SUPPORTED) {
            ConfigurationComposite configComposite =
                    mLayoutEditor.getGraphicalEditor().getConfigurationComposite();
            String theme = configComposite.getTheme();
            Density density = configComposite.getDensity();
            return ViewMetadataRepository.getInsets(fqcn, density, theme);
        } else {
            return null;
        }
    }

    private void debugPrintf(String message, Object... params) {
        if (DEBUG) {
            AdtPlugin.printToConsole("Canvas", String.format(message, params));
        }
    }
}
