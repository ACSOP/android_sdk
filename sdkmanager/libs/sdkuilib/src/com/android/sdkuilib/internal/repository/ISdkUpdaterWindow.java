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

package com.android.sdkuilib.internal.repository;

import com.android.sdkuilib.repository.ISdkChangeListener;

import org.eclipse.swt.widgets.Composite;

/**
 * Interface for the actual implementation of the Update Window.
 */
public interface ISdkUpdaterWindow {

    /**
     * Registers an extra page for the updater window.
     * <p/>
     * Pages must derive from {@link Composite} and implement a constructor that takes
     * a single parent {@link Composite} argument.
     * <p/>
     * All pages must be registered before the call to {@link #open()}.
     *
     * @param pageClass The {@link Composite}-derived class that will implement the page.
     * @param purpose The purpose of this page, e.g. an about box, settings page or generic.
     */
    public abstract void registerPage(Class<? extends UpdaterPage> pageClass,
            UpdaterPage.Purpose purpose);

    /**
     * Indicate the initial page that should be selected when the window opens.
     * <p/>
     * This must be called before the call to {@link #open()}.
     * If null or if the page class is not found, the first page will be selected.
     */
    public abstract void setInitialPage(Class<? extends Composite> pageClass);

    /**
     * Sets whether the auto-update wizard will be shown when opening the window.
     * <p/>
     * This must be called before the call to {@link #open()}.
     */
    public abstract void setRequestAutoUpdate(boolean requestAutoUpdate);

    /**
     * Adds a new listener to be notified when a change is made to the content of the SDK.
     */
    public abstract void addListener(ISdkChangeListener listener);

    /**
     * Removes a new listener to be notified anymore when a change is made to the content of
     * the SDK.
     */
    public abstract void removeListener(ISdkChangeListener listener);

    /**
     * Opens the window.
     */
    public abstract void open();

}
