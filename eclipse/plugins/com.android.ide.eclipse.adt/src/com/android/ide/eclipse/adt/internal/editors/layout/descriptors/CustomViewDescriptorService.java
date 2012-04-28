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

package com.android.ide.eclipse.adt.internal.editors.layout.descriptors;

import static com.android.sdklib.SdkConstants.CLASS_VIEWGROUP;

import com.android.ide.common.resources.platform.ViewClassInfo;
import com.android.ide.eclipse.adt.internal.editors.descriptors.AttributeDescriptor;
import com.android.ide.eclipse.adt.internal.editors.descriptors.DescriptorsUtils;
import com.android.ide.eclipse.adt.internal.editors.descriptors.ElementDescriptor;
import com.android.ide.eclipse.adt.internal.sdk.AndroidTargetData;
import com.android.ide.eclipse.adt.internal.sdk.Sdk;
import com.android.sdklib.IAndroidTarget;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.ISharedImages;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

import java.util.HashMap;
import java.util.List;

/**
 * Service responsible for creating/managing {@link ViewElementDescriptor} objects for custom
 * View classes per project.
 * <p/>
 * The service provides an on-demand monitoring of custom classes to check for changes. Monitoring
 * starts once a request for an {@link ViewElementDescriptor} object has been done for a specific
 * class.
 * <p/>
 * The monitoring will notify a listener of any changes in the class triggering a change in its
 * associated {@link ViewElementDescriptor} object.
 * <p/>
 * If the custom class does not exist, no monitoring is put in place to avoid having to listen
 * to all class changes in the projects.
 */
public final class CustomViewDescriptorService {

    private static CustomViewDescriptorService sThis = new CustomViewDescriptorService();

    /**
     * Map where keys are the project, and values are another map containing all the known
     * custom View class for this project. The custom View class are stored in a map
     * where the keys are the fully qualified class name, and the values are their associated
     * {@link ViewElementDescriptor}.
     */
    private HashMap<IProject, HashMap<String, ViewElementDescriptor>> mCustomDescriptorMap =
        new HashMap<IProject, HashMap<String, ViewElementDescriptor>>();

    /**
     * TODO will be used to update the ViewElementDescriptor of the custom view when it
     * is modified (either the class itself or its attributes.xml)
     */
    @SuppressWarnings("unused")
    private ICustomViewDescriptorListener mListener;

    /**
     * Classes which implements this interface provide a method that deal with modifications
     * in custom View class triggering a change in its associated {@link ViewClassInfo} object.
     */
    public interface ICustomViewDescriptorListener {
        /**
         * Sent when a custom View class has changed and
         * its {@link ViewElementDescriptor} was modified.
         *
         * @param project the project containing the class.
         * @param className the fully qualified class name.
         * @param descriptor the updated ElementDescriptor.
         */
        public void updatedClassInfo(IProject project,
                                     String className,
                                     ViewElementDescriptor descriptor);
    }

    /**
     * Returns the singleton instance of {@link CustomViewDescriptorService}.
     */
    public static CustomViewDescriptorService getInstance() {
        return sThis;
    }

    /**
     * Sets the listener receiving custom View class modification notifications.
     * @param listener the listener to receive the notifications.
     *
     * TODO will be used to update the ViewElementDescriptor of the custom view when it
     * is modified (either the class itself or its attributes.xml)
     */
    public void setListener(ICustomViewDescriptorListener listener) {
        mListener = listener;
    }

    /**
     * Returns the {@link ViewElementDescriptor} for a particular project/class when the
     * fully qualified class name actually matches a class from the given project.
     * <p/>
     * Custom descriptors are created as needed.
     * <p/>
     * If it is the first time the {@link ViewElementDescriptor} is requested, the method
     * will check that the specified class is in fact a custom View class. Once this is
     * established, a monitoring for that particular class is initiated. Any change will
     * trigger a notification to the {@link ICustomViewDescriptorListener}.
     *
     * @param project the project containing the class.
     * @param fqcn the fully qualified name of the class.
     * @return a {@link ViewElementDescriptor} or <code>null</code> if the class was not
     *         a custom View class.
     */
    public ViewElementDescriptor getDescriptor(IProject project, String fqcn) {
        // look in the map first
        synchronized (mCustomDescriptorMap) {
            HashMap<String, ViewElementDescriptor> map = mCustomDescriptorMap.get(project);

            if (map != null) {
                ViewElementDescriptor descriptor = map.get(fqcn);
                if (descriptor != null) {
                    return descriptor;
                }
            }

            // if we step here, it looks like we haven't created it yet.
            // First lets check this is in fact a valid type in the project

            try {
                // We expect the project to be both opened and of java type (since it's an android
                // project), so we can create a IJavaProject object from our IProject.
                IJavaProject javaProject = JavaCore.create(project);

                // replace $ by . in the class name
                String javaClassName = fqcn.replaceAll("\\$", "\\."); //$NON-NLS-1$ //$NON-NLS-2$

                // look for the IType object for this class
                IType type = javaProject.findType(javaClassName);
                if (type != null && type.exists()) {
                    // the type exists. Let's get the parent class and its ViewClassInfo.

                    // get the type hierarchy
                    ITypeHierarchy hierarchy = type.newSupertypeHierarchy(
                            new NullProgressMonitor());

                    ViewElementDescriptor parentDescriptor = createViewDescriptor(
                            hierarchy.getSuperclass(type), project, hierarchy);

                    if (parentDescriptor != null) {
                        // we have a valid parent, lets create a new ViewElementDescriptor.

                        String name = DescriptorsUtils.getBasename(fqcn);
                        ViewElementDescriptor descriptor = new CustomViewDescriptor(name, fqcn,
                                getAttributeDescriptor(type, parentDescriptor),
                                getLayoutAttributeDescriptors(type, parentDescriptor),
                                parentDescriptor.getChildren());
                        descriptor.setSuperClass(parentDescriptor);

                        synchronized (mCustomDescriptorMap) {
                            map = mCustomDescriptorMap.get(project);
                            if (map == null) {
                                map = new HashMap<String, ViewElementDescriptor>();
                                mCustomDescriptorMap.put(project, map);
                            }

                            map.put(fqcn, descriptor);
                        }

                        //TODO setup listener on this resource change.

                        return descriptor;
                    }
                }
            } catch (JavaModelException e) {
                // there was an error accessing any of the IType, we'll just return null;
            }
        }

        return null;
    }

    /**
     * Computes (if needed) and returns the {@link ViewElementDescriptor} for the specified type.
     *
     * @return A {@link ViewElementDescriptor} or null if type or typeHierarchy is null.
     */
    private ViewElementDescriptor createViewDescriptor(IType type, IProject project,
            ITypeHierarchy typeHierarchy) {
        // check if the type is a built-in View class.
        List<ViewElementDescriptor> builtInList = null;

        // give up if there's no type
        if (type == null) {
            return null;
        }

        String fqcn = type.getFullyQualifiedName();

        Sdk currentSdk = Sdk.getCurrent();
        if (currentSdk != null) {
            IAndroidTarget target = currentSdk.getTarget(project);
            if (target != null) {
                AndroidTargetData data = currentSdk.getTargetData(target);
                if (data != null) {
                    LayoutDescriptors descriptors = data.getLayoutDescriptors();
                    ViewElementDescriptor d = descriptors.findDescriptorByClass(fqcn);
                    if (d != null) {
                        return d;
                    }
                    builtInList = descriptors.getViewDescriptors();
                }
            }
        }

        // it's not a built-in class? Lets look if the superclass is built-in
        // give up if there's no type
        if (typeHierarchy == null) {
            return null;
        }

        IType parentType = typeHierarchy.getSuperclass(type);
        if (parentType != null) {
            ViewElementDescriptor parentDescriptor = createViewDescriptor(parentType, project,
                    typeHierarchy);

            if (parentDescriptor != null) {
                // parent class is a valid View class with a descriptor, so we create one
                // for this class.
                String name = DescriptorsUtils.getBasename(fqcn);
                // A custom view accepts children if its parent descriptor also does.
                // The only exception to this is ViewGroup, which accepts children even though
                // its parent does not.
                boolean isViewGroup = fqcn.equals(CLASS_VIEWGROUP);
                boolean hasChildren = isViewGroup || parentDescriptor.hasChildren();
                ViewElementDescriptor[] children = null;
                if (hasChildren && builtInList != null) {
                    // We can't figure out what the allowable children are by just
                    // looking at the class, so assume any View is valid
                    children = builtInList.toArray(new ViewElementDescriptor[builtInList.size()]);
                }
                ViewElementDescriptor descriptor = new CustomViewDescriptor(name, fqcn,
                        getAttributeDescriptor(type, parentDescriptor),
                        getLayoutAttributeDescriptors(type, parentDescriptor),
                        children);
                descriptor.setSuperClass(parentDescriptor);

                // add it to the map
                synchronized (mCustomDescriptorMap) {
                    HashMap<String, ViewElementDescriptor> map = mCustomDescriptorMap.get(project);

                    if (map == null) {
                        map = new HashMap<String, ViewElementDescriptor>();
                        mCustomDescriptorMap.put(project, map);
                    }

                    map.put(fqcn, descriptor);

                }

                //TODO setup listener on this resource change.

                return descriptor;
            }
        }

        // class is neither a built-in view class, nor extend one. return null.
        return null;
    }

    /**
     * Returns the array of {@link AttributeDescriptor} for the specified {@link IType}.
     * <p/>
     * The array should contain the descriptor for this type and all its supertypes.
     *
     * @param type the type for which the {@link AttributeDescriptor} are returned.
     * @param parentDescriptor the {@link ViewElementDescriptor} of the direct superclass.
     */
    private static AttributeDescriptor[] getAttributeDescriptor(IType type,
            ViewElementDescriptor parentDescriptor) {
        // TODO add the class attribute descriptors to the parent descriptors.
        return parentDescriptor.getAttributes();
    }

    private static AttributeDescriptor[] getLayoutAttributeDescriptors(IType type,
            ViewElementDescriptor parentDescriptor) {
        return parentDescriptor.getLayoutAttributes();
    }

    private static class CustomViewDescriptor extends ViewElementDescriptor {
        public CustomViewDescriptor(String name, String fqcn, AttributeDescriptor[] attributes,
                AttributeDescriptor[] layoutAttributes,
                ElementDescriptor[] children) {
            super(
                    fqcn, // xml name
                    name, // ui name
                    fqcn, // full class name
                    fqcn, // tooltip
                    null, // sdk_url
                    attributes,
                    layoutAttributes,
                    children,
                    false // mandatory
            );
        }

        @Override
        public Image getGenericIcon() {
            // Java source file icon. We could use the Java class icon here
            // (IMG_OBJS_CLASS), but it does not work well on anything but
            // white backgrounds
            ISharedImages sharedImages = JavaUI.getSharedImages();
            String key = ISharedImages.IMG_OBJS_CUNIT;
            ImageDescriptor descriptor = sharedImages.getImageDescriptor(key);
            return descriptor.createImage();
        }
    }
}
