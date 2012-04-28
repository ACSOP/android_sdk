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

package com.android.ide.eclipse.adt.internal.refactoring.core;

import com.android.AndroidConstants;
import com.android.ide.common.layout.LayoutConstants;
import com.android.ide.eclipse.adt.AdtConstants;
import com.android.ide.eclipse.adt.internal.project.AndroidManifestHelper;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutChange;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutChangeDescription;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidLayoutFileChanges;
import com.android.ide.eclipse.adt.internal.refactoring.changes.AndroidTypeMoveChange;
import com.android.sdklib.SdkConstants;
import com.android.sdklib.xml.AndroidManifest;
import com.android.sdklib.xml.ManifestData;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveParticipant;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.internal.provisional.text.IStructuredDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMModel;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A participant to participate in refactorings that move a type in an Android project.
 * The class updates android manifest and the layout file
 * The user can suppress refactoring by disabling the "Update references" checkbox
 * <p>
 * Rename participants are registered via the extension point <code>
 * org.eclipse.ltk.core.refactoring.moveParticipants</code>.
 * Extensions to this extension point must therefore extend <code>org.eclipse.ltk.core.refactoring.participants.MoveParticipant</code>.
 * </p>
 */
@SuppressWarnings("restriction")
public class AndroidTypeMoveParticipant extends MoveParticipant {

    protected IFile mAndroidManifest;

    protected ITextFileBufferManager mManager;

    protected String mOldName;

    protected String mNewName;

    protected IDocument mDocument;

    protected String mJavaPackage;

    protected Map<String, String> mAndroidElements;

    private Set<AndroidLayoutFileChanges> mFileChanges = new HashSet<AndroidLayoutFileChanges>();

    @Override
    public RefactoringStatus checkConditions(IProgressMonitor pm, CheckConditionsContext context)
            throws OperationCanceledException {
        return new RefactoringStatus();
    }

    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        if (pm.isCanceled()) {
            return null;
        }
        if (!getArguments().getUpdateReferences())
            return null;
        CompositeChange result = new CompositeChange(getName());
        if (mAndroidManifest.exists()) {
            if (mAndroidElements.size() > 0) {
                getDocument();
                Change change = new AndroidTypeMoveChange(mAndroidManifest, mManager, mDocument,
                        mAndroidElements, mNewName, mOldName);
                if (change != null) {
                    result.add(change);
                }
            }

            for (AndroidLayoutFileChanges fileChange : mFileChanges) {
                IFile file = fileChange.getFile();
                ITextFileBufferManager lManager = FileBuffers.getTextFileBufferManager();
                lManager.connect(file.getFullPath(), LocationKind.NORMALIZE,
                        new NullProgressMonitor());
                ITextFileBuffer buffer = lManager.getTextFileBuffer(file.getFullPath(),
                        LocationKind.NORMALIZE);
                IDocument lDocument = buffer.getDocument();
                Change layoutChange = new AndroidLayoutChange(file, lDocument, lManager,
                        fileChange.getChanges());
                if (layoutChange != null) {
                    result.add(layoutChange);
                }
            }
        }
        return (result.getChildren().length == 0) ? null : result;

    }

    /**
     * @return the document
     * @throws CoreException
     */
    public IDocument getDocument() throws CoreException {
        if (mDocument == null) {
            mManager = FileBuffers.getTextFileBufferManager();
            mManager.connect(mAndroidManifest.getFullPath(), LocationKind.NORMALIZE,
                    new NullProgressMonitor());
            ITextFileBuffer buffer = mManager.getTextFileBuffer(mAndroidManifest.getFullPath(),
                    LocationKind.NORMALIZE);
            mDocument = buffer.getDocument();
        }
        return mDocument;
    }

    /**
     * @return the android manifest file
     */
    public IFile getAndroidManifest() {
        return mAndroidManifest;
    }

    @Override
    public String getName() {
        return "Android Type Move";
    }

    @Override
    protected boolean initialize(Object element) {

        if (element instanceof IType) {
            IType type = (IType) element;
            IJavaProject javaProject = (IJavaProject) type.getAncestor(IJavaElement.JAVA_PROJECT);
            IProject project = javaProject.getProject();
            IResource manifestResource = project.findMember(AdtConstants.WS_SEP
                    + SdkConstants.FN_ANDROID_MANIFEST_XML);

            if (manifestResource == null || !manifestResource.exists()
                    || !(manifestResource instanceof IFile)) {
                RefactoringUtil.logInfo("Invalid or missing the "
                        + SdkConstants.FN_ANDROID_MANIFEST_XML + " in the " + project.getName()
                        + " project.");
                return false;
            }
            mAndroidManifest = (IFile) manifestResource;
            ManifestData manifestData;
            manifestData = AndroidManifestHelper.parseForData(mAndroidManifest);
            if (manifestData == null) {
                return false;
            }
            mJavaPackage = manifestData.getPackage();
            mOldName = type.getFullyQualifiedName();
            Object destination = getArguments().getDestination();
            if (destination instanceof IPackageFragment) {
                IPackageFragment packageFragment = (IPackageFragment) destination;
                mNewName = packageFragment.getElementName() + "." + type.getElementName();
            }
            if (mOldName == null || mNewName == null) {
                return false;
            }
            mAndroidElements = addAndroidElements();
            try {
                ITypeHierarchy typeHierarchy = type.newSupertypeHierarchy(null);
                if (typeHierarchy == null) {
                    return false;
                }
                IType[] superTypes = typeHierarchy.getAllSuperclasses(type);
                for (int i = 0; i < superTypes.length; i++) {
                    IType superType = superTypes[i];
                    String className = superType.getFullyQualifiedName();
                    if (className.equals(SdkConstants.CLASS_VIEW)) {
                        addLayoutChanges(project, type.getFullyQualifiedName());
                        break;
                    }
                }
            } catch (JavaModelException ignore) {
            }
            return mAndroidElements.size() > 0 || mFileChanges.size() > 0;
        }
        return false;
    }

    /**
     * Adds layout changes for project
     *
     * @param project the Android project
     * @param className the layout classes
     *
     */
    private void addLayoutChanges(IProject project, String className) {
        try {
            IFolder resFolder = project.getFolder(SdkConstants.FD_RESOURCES);
            IFolder layoutFolder = resFolder.getFolder(AndroidConstants.FD_RES_LAYOUT);
            IResource[] members = layoutFolder.members();
            for (int i = 0; i < members.length; i++) {
                IResource member = members[i];
                if ((member instanceof IFile) && member.exists()) {
                    IFile file = (IFile) member;
                    Set<AndroidLayoutChangeDescription> changes = parse(file, className);
                    if (changes.size() > 0) {
                        AndroidLayoutFileChanges fileChange = new AndroidLayoutFileChanges(file);
                        fileChange.getChanges().addAll(changes);
                        mFileChanges.add(fileChange);
                    }
                }
            }
        } catch (CoreException e) {
            RefactoringUtil.log(e);
        }
    }

    /**
     * Searches the layout file for classes
     *
     * @param file the Android layout file
     * @param className the layout classes
     *
     */
    private Set<AndroidLayoutChangeDescription> parse(IFile file, String className) {
        Set<AndroidLayoutChangeDescription> changes = new HashSet<AndroidLayoutChangeDescription>();
        ITextFileBufferManager lManager = null;
        try {
            lManager = FileBuffers.getTextFileBufferManager();
            lManager.connect(file.getFullPath(), LocationKind.NORMALIZE, new NullProgressMonitor());
            ITextFileBuffer buffer = lManager.getTextFileBuffer(file.getFullPath(),
                    LocationKind.NORMALIZE);
            IDocument lDocument = buffer.getDocument();
            IStructuredModel model = null;
            try {
                model = StructuredModelManager.getModelManager().getExistingModelForRead(lDocument);
                if (model == null) {
                    if (lDocument instanceof IStructuredDocument) {
                        IStructuredDocument structuredDocument = (IStructuredDocument) lDocument;
                        model = StructuredModelManager.getModelManager().getModelForRead(
                                structuredDocument);
                    }
                }
                if (model != null) {
                    IDOMModel xmlModel = (IDOMModel) model;
                    IDOMDocument xmlDoc = xmlModel.getDocument();
                    NodeList nodes = xmlDoc.getElementsByTagName(LayoutConstants.VIEW);
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node node = nodes.item(i);
                        NamedNodeMap attributes = node.getAttributes();
                        if (attributes != null) {
                            Node attributeNode =
                                attributes.getNamedItem(LayoutConstants.ATTR_CLASS);
                            if (attributeNode instanceof Attr) {
                                Attr attribute = (Attr) attributeNode;
                                String value = attribute.getValue();
                                if (value != null && value.equals(className)) {
                                    AndroidLayoutChangeDescription layoutChange =
                                        new AndroidLayoutChangeDescription(className, mNewName,
                                            AndroidLayoutChangeDescription.VIEW_TYPE);
                                    changes.add(layoutChange);
                                }
                            }
                        }
                    }
                    nodes = xmlDoc.getElementsByTagName(className);
                    for (int i = 0; i < nodes.getLength(); i++) {
                        AndroidLayoutChangeDescription layoutChange =
                            new AndroidLayoutChangeDescription(className, mNewName,
                                    AndroidLayoutChangeDescription.STANDALONE_TYPE);
                        changes.add(layoutChange);
                    }
                }
            } finally {
                if (model != null) {
                    model.releaseFromRead();
                }
            }

        } catch (CoreException ignore) {
        } finally {
            if (lManager != null) {
                try {
                    lManager.disconnect(file.getFullPath(), LocationKind.NORMALIZE,
                            new NullProgressMonitor());
                } catch (CoreException ignore) {
                }
            }
        }
        return changes;
    }

    /**
     * Returns the elements (activity, receiver, service ...)
     * which have to be renamed
     *
     * @return the android elements
     */
    private Map<String, String> addAndroidElements() {
        Map<String, String> androidElements = new HashMap<String, String>();

        IDocument document;
        try {
            document = getDocument();
        } catch (CoreException e) {
            RefactoringUtil.log(e);
            if (mManager != null) {
                try {
                    mManager.disconnect(mAndroidManifest.getFullPath(), LocationKind.NORMALIZE,
                            new NullProgressMonitor());
                } catch (CoreException e1) {
                    RefactoringUtil.log(e1);
                }
            }
            document = null;
            return androidElements;
        }

        IStructuredModel model = null;
        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(document);
            if (model == null) {
                if (document instanceof IStructuredDocument) {
                    IStructuredDocument structuredDocument = (IStructuredDocument) document;
                    model = StructuredModelManager.getModelManager().getModelForRead(
                            structuredDocument);
                }
            }
            if (model != null) {
                IDOMModel xmlModel = (IDOMModel) model;
                IDOMDocument xmlDoc = xmlModel.getDocument();
                add(xmlDoc, androidElements, AndroidManifest.NODE_ACTIVITY,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_APPLICATION,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_PROVIDER,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_RECEIVER,
                        AndroidManifest.ATTRIBUTE_NAME);
                add(xmlDoc, androidElements, AndroidManifest.NODE_SERVICE,
                        AndroidManifest.ATTRIBUTE_NAME);
            }
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        return androidElements;
    }

    /**
     * Adds the element  (activity, receiver, service ...) to the map
     *
     * @param xmlDoc the document
     * @param androidElements the map
     * @param element the element
     */
    private void add(IDOMDocument xmlDoc, Map<String, String> androidElements, String element,
            String argument) {
        NodeList nodes = xmlDoc.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            NamedNodeMap attributes = node.getAttributes();
            if (attributes != null) {
                Attr attribute = RefactoringUtil.findAndroidAttributes(attributes, argument);
                if (attribute != null) {
                    String value = attribute.getValue();
                    if (value != null) {
                        String fullName = AndroidManifest.combinePackageAndClassName(mJavaPackage,
                                value);
                        if (fullName != null && fullName.equals(mOldName)) {
                            androidElements.put(element, value);
                        }
                    }
                }
            }
        }
    }

}
