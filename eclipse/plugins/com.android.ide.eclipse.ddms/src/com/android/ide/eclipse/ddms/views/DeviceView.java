/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.ide.eclipse.ddms.views;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.DdmPreferences;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncException;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.ClientData.IHprofDumpHandler;
import com.android.ddmlib.ClientData.MethodProfilingStatus;
import com.android.ddmlib.SyncService.ISyncProgressMonitor;
import com.android.ddmuilib.DevicePanel;
import com.android.ddmuilib.ImageLoader;
import com.android.ddmuilib.ScreenShotDialog;
import com.android.ddmuilib.SyncProgressHelper;
import com.android.ddmuilib.DevicePanel.IUiSelectionListener;
import com.android.ddmuilib.SyncProgressHelper.SyncRunnable;
import com.android.ddmuilib.handler.BaseFileHandler;
import com.android.ddmuilib.handler.MethodProfilingHandler;
import com.android.ide.eclipse.ddms.DdmsPlugin;
import com.android.ide.eclipse.ddms.IDebuggerConnector;
import com.android.ide.eclipse.ddms.i18n.Messages;
import com.android.ide.eclipse.ddms.preferences.PreferenceInitializer;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;

import java.io.File;
import java.io.IOException;

public class DeviceView extends ViewPart implements IUiSelectionListener, IClientChangeListener {

    private final static boolean USE_SELECTED_DEBUG_PORT = true;

    public static final String ID = "com.android.ide.eclipse.ddms.views.DeviceView"; //$NON-NLS-1$

    private static DeviceView sThis;

    private Shell mParentShell;
    private DevicePanel mDeviceList;

    private Action mResetAdbAction;
    private Action mCaptureAction;
    private Action mUpdateThreadAction;
    private Action mUpdateHeapAction;
    private Action mGcAction;
    private Action mKillAppAction;
    private Action mDebugAction;
    private Action mHprofAction;
    private Action mTracingAction;

    private ImageDescriptor mTracingStartImage;
    private ImageDescriptor mTracingStopImage;

    public class HProfHandler extends BaseFileHandler implements IHprofDumpHandler {
        public final static String ACTION_SAVE = "hprof.save"; //$NON-NLS-1$
        public final static String ACTION_OPEN = "hprof.open"; //$NON-NLS-1$

        public final static String DOT_HPROF = ".hprof"; //$NON-NLS-1$

        HProfHandler(Shell parentShell) {
            super(parentShell);
        }

        @Override
        protected String getDialogTitle() {
            return Messages.DeviceView_HPROF_Error;
        }

        public void onEndFailure(final Client client, final String message) {
            mParentShell.getDisplay().asyncExec(new Runnable() {
                public void run() {
                    try {
                        displayErrorFromUiThread(
                                Messages.DeviceView_Unable_Create_HPROF_For_Application,
                                client.getClientData().getClientDescription(),
                                message != null ? message + "\n\n" : ""); //$NON-NLS-1$ //$NON-NLS-2$
                    } finally {
                        // this will make sure the dump hprof button is
                        // re-enabled for the
                        // current selection. as the client is finished dumping
                        // an hprof file
                        doSelectionChanged(mDeviceList.getSelectedClient());
                    }
                }
            });
        }

        public void onSuccess(final String remoteFilePath, final Client client) {
            mParentShell.getDisplay().asyncExec(new Runnable() {
                public void run() {
                    final IDevice device = client.getDevice();
                    try {
                        // get the sync service to pull the HPROF file
                        final SyncService sync = client.getDevice().getSyncService();
                        if (sync != null) {
                            // get from the preference what action to take
                            IPreferenceStore store = DdmsPlugin.getDefault().getPreferenceStore();
                            String value = store.getString(PreferenceInitializer.ATTR_HPROF_ACTION);

                            if (ACTION_OPEN.equals(value)) {
                                File temp = File.createTempFile("android", DOT_HPROF); //$NON-NLS-1$
                                final String tempPath = temp.getAbsolutePath();
                                SyncProgressHelper.run(new SyncRunnable() {

                                    public void run(ISyncProgressMonitor monitor)
                                                throws SyncException, IOException,
                                                TimeoutException {
                                        sync.pullFile(remoteFilePath, tempPath, monitor);
                                    }

                                    public void close() {
                                        sync.close();
                                    }
                                },
                                        String.format(Messages.DeviceView_Pulling_From_Device,
                                                remoteFilePath),
                                        mParentShell);

                                open(tempPath);
                            } else {
                                // default action is ACTION_SAVE
                                promptAndPull(sync,
                                        client.getClientData().getClientDescription() + DOT_HPROF,
                                        remoteFilePath, Messages.DeviceView_Save_HPROF_File);

                            }
                        } else {
                            displayErrorFromUiThread(
                                    Messages.DeviceView_Unable_Download_HPROF_From_Device_One_Param_First_Message,
                                    device.getSerialNumber());
                        }
                    } catch (SyncException e) {
                        if (e.wasCanceled() == false) {
                            displayErrorFromUiThread(
                                    Messages.DeviceView_Unable_Download_HPROF_From_Device_Two_Param,
                                    device.getSerialNumber(), e.getMessage());
                        }
                    } catch (Exception e) {
                        displayErrorFromUiThread(
                                Messages.DeviceView_Unable_Download_HPROF_From_Device_One_Param_Second_Message,
                                device.getSerialNumber());

                    } finally {
                        // this will make sure the dump hprof button is
                        // re-enabled for the
                        // current selection. as the client is finished dumping
                        // an hprof file
                        doSelectionChanged(mDeviceList.getSelectedClient());
                    }
                }
            });
        }

        public void onSuccess(final byte[] data, final Client client) {
            mParentShell.getDisplay().asyncExec(new Runnable() {
                public void run() {
                    // get from the preference what action to take
                    IPreferenceStore store = DdmsPlugin.getDefault().getPreferenceStore();
                    String value = store.getString(PreferenceInitializer.ATTR_HPROF_ACTION);

                    if (ACTION_OPEN.equals(value)) {
                        try {
                            // no need to give an extension since we're going to
                            // convert the
                            // file anyway after.
                            File tempFile = saveTempFile(data, null /* extension */);
                            open(tempFile.getAbsolutePath());
                        } catch (Exception e) {
                            String errorMsg = e.getMessage();
                            displayErrorFromUiThread(
                                    Messages.DeviceView_Failed_To_Save_HPROF_Data,
                                    errorMsg != null ? ":\n" + errorMsg : "."); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    } else {
                        // default action is ACTION_SAVE
                        promptAndSave(client.getClientData().getClientDescription() + DOT_HPROF,
                                data, Messages.DeviceView_Save_HPROF_File);
                    }
                }
            });
        }

        private void open(String path) throws IOException, InterruptedException, PartInitException {
            // make a temp file to convert the hprof into something
            // readable by normal tools
            File temp = File.createTempFile("android", DOT_HPROF); //$NON-NLS-1$
            String tempPath = temp.getAbsolutePath();

            String[] command = new String[3];
            command[0] = DdmsPlugin.getHprofConverter();
            command[1] = path;
            command[2] = tempPath;

            Process p = Runtime.getRuntime().exec(command);
            p.waitFor();

            IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path(tempPath));
            if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists()) {
                // before we open the file in an editor window, we make sure the
                // current
                // workbench page has an editor area (typically the ddms
                // perspective doesn't).
                IWorkbench workbench = PlatformUI.getWorkbench();
                IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
                IWorkbenchPage page = window.getActivePage();
                if (page.isEditorAreaVisible() == false) {
                    IAdaptable input;
                    if (page != null)
                        input = page.getInput();
                    else
                        input = ResourcesPlugin.getWorkspace().getRoot();
                    try {
                        workbench.showPerspective("org.eclipse.debug.ui.DebugPerspective", //$NON-NLS-1$
                                window, input);
                    } catch (WorkbenchException e) {
                    }
                }

                IDE.openEditorOnFileStore(page, fileStore);
            }
        }
    }

    public DeviceView() {
        // the view is declared with allowMultiple="false" so we
        // can safely do this.
        sThis = this;
    }

    public static DeviceView getInstance() {
        return sThis;
    }

    @Override
    public void createPartControl(Composite parent) {
        mParentShell = parent.getShell();

        ImageLoader loader = ImageLoader.getDdmUiLibLoader();

        mDeviceList = new DevicePanel(USE_SELECTED_DEBUG_PORT);
        mDeviceList.createPanel(parent);
        mDeviceList.addSelectionListener(this);

        DdmsPlugin plugin = DdmsPlugin.getDefault();
        mDeviceList.addSelectionListener(plugin);
        plugin.setListeningState(true);

        mCaptureAction = new Action(Messages.DeviceView_Screen_Capture) {
            @Override
            public void run() {
                ScreenShotDialog dlg = new ScreenShotDialog(
                        DdmsPlugin.getDisplay().getActiveShell());
                dlg.open(mDeviceList.getSelectedDevice());
            }
        };
        mCaptureAction.setToolTipText(Messages.DeviceView_Screen_Capture_Tooltip);
        mCaptureAction.setImageDescriptor(loader.loadDescriptor("capture.png")); //$NON-NLS-1$

        mResetAdbAction = new Action(Messages.DeviceView_Reset_ADB) {
            @Override
            public void run() {
                AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
                if (bridge != null) {
                    if (bridge.restart() == false) {
                        // get the current Display
                        final Display display = DdmsPlugin.getDisplay();

                        // dialog box only run in ui thread..
                        display.asyncExec(new Runnable() {
                            public void run() {
                                Shell shell = display.getActiveShell();
                                MessageDialog.openError(shell, Messages.DeviceView_ADB_Error,
                                        Messages.DeviceView_ADB_Failed_Restart);
                            }
                        });
                    }
                }
            }
        };
        mResetAdbAction.setToolTipText(Messages.DeviceView_Reset_ADB_Host_Deamon);
        mResetAdbAction.setImageDescriptor(PlatformUI.getWorkbench()
                .getSharedImages().getImageDescriptor(
                        ISharedImages.IMG_OBJS_WARN_TSK));

        mKillAppAction = new Action() {
            @Override
            public void run() {
                mDeviceList.killSelectedClient();
            }
        };

        mKillAppAction.setText(Messages.DeviceView_Stop_Process);
        mKillAppAction.setToolTipText(Messages.DeviceView_Stop_Process_Tooltip);
        mKillAppAction.setImageDescriptor(loader.loadDescriptor(DevicePanel.ICON_HALT));

        mGcAction = new Action() {
            @Override
            public void run() {
                mDeviceList.forceGcOnSelectedClient();
            }
        };

        mGcAction.setText(Messages.DeviceView_Cause_GC);
        mGcAction.setToolTipText(Messages.DeviceView_Cause_GC_Tooltip);
        mGcAction.setImageDescriptor(loader.loadDescriptor(DevicePanel.ICON_GC));

        mHprofAction = new Action() {
            @Override
            public void run() {
                mDeviceList.dumpHprof();
                doSelectionChanged(mDeviceList.getSelectedClient());
            }
        };
        mHprofAction.setText(Messages.DeviceView_Dump_HPROF_File);
        mHprofAction.setToolTipText(Messages.DeviceView_Dump_HPROF_File_Tooltip);
        mHprofAction.setImageDescriptor(loader.loadDescriptor(DevicePanel.ICON_HPROF));

        mUpdateHeapAction = new Action(Messages.DeviceView_Update_Heap, IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                boolean enable = mUpdateHeapAction.isChecked();
                mDeviceList.setEnabledHeapOnSelectedClient(enable);
            }
        };
        mUpdateHeapAction.setToolTipText(Messages.DeviceView_Update_Heap_Tooltip);
        mUpdateHeapAction.setImageDescriptor(loader.loadDescriptor(DevicePanel.ICON_HEAP));

        mUpdateThreadAction = new Action(Messages.DeviceView_Threads, IAction.AS_CHECK_BOX) {
            @Override
            public void run() {
                boolean enable = mUpdateThreadAction.isChecked();
                mDeviceList.setEnabledThreadOnSelectedClient(enable);
            }
        };
        mUpdateThreadAction.setToolTipText(Messages.DeviceView_Threads_Tooltip);
        mUpdateThreadAction.setImageDescriptor(loader.loadDescriptor(DevicePanel.ICON_THREAD));

        mTracingAction = new Action() {
            @Override
            public void run() {
                mDeviceList.toggleMethodProfiling();
            }
        };
        mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
        mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
        mTracingStartImage = loader.loadDescriptor(DevicePanel.ICON_TRACING_START);
        mTracingStopImage = loader.loadDescriptor(DevicePanel.ICON_TRACING_STOP);
        mTracingAction.setImageDescriptor(mTracingStartImage);

        mDebugAction = new Action(Messages.DeviceView_Debug_Process) {
            @Override
            public void run() {
                if (DdmsPlugin.getDefault().hasDebuggerConnectors()) {
                    Client currentClient = mDeviceList.getSelectedClient();
                    if (currentClient != null) {
                        ClientData clientData = currentClient.getClientData();

                        // make sure the client can be debugged
                        switch (clientData.getDebuggerConnectionStatus()) {
                            case ERROR: {
                                Display display = DdmsPlugin.getDisplay();
                                Shell shell = display.getActiveShell();
                                MessageDialog.openError(shell,
                                        Messages.DeviceView_Debug_Process_Title,
                                        Messages.DeviceView_Process_Debug_Already_In_Use);
                                return;
                            }
                            case ATTACHED: {
                                Display display = DdmsPlugin.getDisplay();
                                Shell shell = display.getActiveShell();
                                MessageDialog.openError(shell,
                                        Messages.DeviceView_Debug_Process_Title,
                                        Messages.DeviceView_Process_Already_Being_Debugged);
                                return;
                            }
                        }

                        // get the name of the client
                        String packageName = clientData.getClientDescription();
                        if (packageName != null) {

                            // try all connectors till one returns true.
                            IDebuggerConnector[] connectors =
                                    DdmsPlugin.getDefault().getDebuggerConnectors();

                            if (connectors != null) {
                                for (IDebuggerConnector connector : connectors) {
                                    try {
                                        if (connector.connectDebugger(packageName,
                                                currentClient.getDebuggerListenPort(),
                                                DdmPreferences.getSelectedDebugPort())) {
                                            return;
                                        }
                                    } catch (Throwable t) {
                                        // ignore, we'll just not use this
                                        // implementation
                                    }
                                }
                            }

                            // if we get to this point, then we failed to find a
                            // project
                            // that matched the application to debug
                            Display display = DdmsPlugin.getDisplay();
                            Shell shell = display.getActiveShell();
                            MessageDialog.openError(shell, Messages.DeviceView_Debug_Process_Title,
                                    String.format(
                                            Messages.DeviceView_Debug_Session_Failed,
                                            packageName));
                        }
                    }
                }
            }
        };
        mDebugAction.setToolTipText(Messages.DeviceView_Debug_Process_Tooltip);
        mDebugAction.setImageDescriptor(loader.loadDescriptor("debug-attach.png")); //$NON-NLS-1$
        mDebugAction.setEnabled(DdmsPlugin.getDefault().hasDebuggerConnectors());

        placeActions();

        ClientData.setHprofDumpHandler(new HProfHandler(mParentShell));
        AndroidDebugBridge.addClientChangeListener(this);
        ClientData.setMethodProfilingHandler(new MethodProfilingHandler(mParentShell) {
            @Override
            protected void open(String tempPath) {
                if (DdmsPlugin.getDefault().launchTraceview(tempPath) == false) {
                    super.open(tempPath);
                }
            }
        });
    }

    @Override
    public void setFocus() {
        mDeviceList.setFocus();
    }

    /**
     * Sent when a new {@link IDevice} and {@link Client} are selected.
     * 
     * @param selectedDevice the selected device. If null, no devices are
     *            selected.
     * @param selectedClient The selected client. If null, no clients are
     *            selected.
     */
    public void selectionChanged(IDevice selectedDevice, Client selectedClient) {
        // update the buttons
        doSelectionChanged(selectedClient);
        doSelectionChanged(selectedDevice);
    }

    private void doSelectionChanged(Client selectedClient) {
        // update the buttons
        if (selectedClient != null) {
            if (USE_SELECTED_DEBUG_PORT) {
                // set the client as the debug client
                selectedClient.setAsSelectedClient();
            }

            mDebugAction.setEnabled(DdmsPlugin.getDefault().hasDebuggerConnectors());
            mKillAppAction.setEnabled(true);
            mGcAction.setEnabled(true);

            mUpdateHeapAction.setEnabled(true);
            mUpdateHeapAction.setChecked(selectedClient.isHeapUpdateEnabled());

            mUpdateThreadAction.setEnabled(true);
            mUpdateThreadAction.setChecked(selectedClient.isThreadUpdateEnabled());

            ClientData data = selectedClient.getClientData();

            if (data.hasFeature(ClientData.FEATURE_HPROF)) {
                mHprofAction.setEnabled(data.hasPendingHprofDump() == false);
                mHprofAction.setToolTipText(Messages.DeviceView_Dump_HPROF_File);
            } else {
                mHprofAction.setEnabled(false);
                mHprofAction
                        .setToolTipText(Messages.DeviceView_Dump_HPROF_File_Not_Supported_By_VM);
            }

            if (data.hasFeature(ClientData.FEATURE_PROFILING)) {
                mTracingAction.setEnabled(true);
                if (data.getMethodProfilingStatus() == MethodProfilingStatus.ON) {
                    mTracingAction
                            .setToolTipText(Messages.DeviceView_Stop_Method_Profiling_Tooltip);
                    mTracingAction.setText(Messages.DeviceView_Stop_Method_Profiling);
                    mTracingAction.setImageDescriptor(mTracingStopImage);
                } else {
                    mTracingAction
                            .setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
                    mTracingAction.setImageDescriptor(mTracingStartImage);
                    mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
                }
            } else {
                mTracingAction.setEnabled(false);
                mTracingAction.setImageDescriptor(mTracingStartImage);
                mTracingAction
                        .setToolTipText(Messages.DeviceView_Start_Method_Profiling_Not_Suported_By_Vm);
                mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
            }
        } else {
            if (USE_SELECTED_DEBUG_PORT) {
                // set the client as the debug client
                AndroidDebugBridge bridge = AndroidDebugBridge.getBridge();
                if (bridge != null) {
                    bridge.setSelectedClient(null);
                }
            }

            mDebugAction.setEnabled(false);
            mKillAppAction.setEnabled(false);
            mGcAction.setEnabled(false);
            mUpdateHeapAction.setChecked(false);
            mUpdateHeapAction.setEnabled(false);
            mUpdateThreadAction.setEnabled(false);
            mUpdateThreadAction.setChecked(false);
            mHprofAction.setEnabled(false);

            mHprofAction.setEnabled(false);
            mHprofAction.setToolTipText(Messages.DeviceView_Dump_HPROF_File);

            mTracingAction.setEnabled(false);
            mTracingAction.setImageDescriptor(mTracingStartImage);
            mTracingAction.setToolTipText(Messages.DeviceView_Start_Method_Profiling_Tooltip);
            mTracingAction.setText(Messages.DeviceView_Start_Method_Profiling);
        }
    }

    private void doSelectionChanged(IDevice selectedDevice) {
        mCaptureAction.setEnabled(selectedDevice != null);
    }

    /**
     * Place the actions in the ui.
     */
    private final void placeActions() {
        IActionBars actionBars = getViewSite().getActionBars();

        // first in the menu
        IMenuManager menuManager = actionBars.getMenuManager();
        menuManager.removeAll();
        menuManager.add(mDebugAction);
        menuManager.add(new Separator());
        menuManager.add(mUpdateHeapAction);
        menuManager.add(mHprofAction);
        menuManager.add(mGcAction);
        menuManager.add(new Separator());
        menuManager.add(mUpdateThreadAction);
        menuManager.add(mTracingAction);
        menuManager.add(new Separator());
        menuManager.add(mKillAppAction);
        menuManager.add(new Separator());
        menuManager.add(mCaptureAction);
        menuManager.add(new Separator());
        menuManager.add(mResetAdbAction);

        // and then in the toolbar
        IToolBarManager toolBarManager = actionBars.getToolBarManager();
        toolBarManager.removeAll();
        toolBarManager.add(mDebugAction);
        toolBarManager.add(new Separator());
        toolBarManager.add(mUpdateHeapAction);
        toolBarManager.add(mHprofAction);
        toolBarManager.add(mGcAction);
        toolBarManager.add(new Separator());
        toolBarManager.add(mUpdateThreadAction);
        toolBarManager.add(mTracingAction);
        toolBarManager.add(new Separator());
        toolBarManager.add(mKillAppAction);
        toolBarManager.add(new Separator());
        toolBarManager.add(mCaptureAction);
    }

    public void clientChanged(final Client client, int changeMask) {
        if ((changeMask & Client.CHANGE_METHOD_PROFILING_STATUS) == Client.CHANGE_METHOD_PROFILING_STATUS) {
            if (mDeviceList.getSelectedClient() == client) {
                mParentShell.getDisplay().asyncExec(new Runnable() {
                    public void run() {
                        // force refresh of the button enabled state.
                        doSelectionChanged(client);
                    }
                });
            }
        }
    }
}
