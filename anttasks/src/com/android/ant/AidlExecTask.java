/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.ant;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.ExecTask;
import org.apache.tools.ant.types.Path;

import java.util.ArrayList;
import java.util.List;

/**
 * Task to execute aidl.
 * <p>
 * It expects 3 attributes:<br>
 * 'executable' ({@link Path} with a single path) for the location of the aidl executable<br>
 * 'framework' ({@link Path} with a single path) for the "preprocessed" file containing all the
 *     parcelables exported by the framework<br>
 * 'genFolder' ({@link Path} with a single path) for the location of the gen folder.
 *
 * It also expects one or more inner elements called "source" which are identical to {@link Path}
 * elements.
 */
public class AidlExecTask extends MultiFilesTask {

    private String mExecutable;
    private String mFramework;
    private String mGenFolder;
    private final ArrayList<Path> mPaths = new ArrayList<Path>();

    private class AidlProcessor implements SourceProcessor {

        public String getSourceFileExtension() {
            return "aidl";
        }

        public void process(String filePath, String sourceFolder,
                List<String> sourceFolders, Project taskProject) {
            ExecTask task = new ExecTask();
            task.setProject(taskProject);
            task.setOwningTarget(getOwningTarget());
            task.setExecutable(mExecutable);
            task.setTaskName("aidl");
            task.setFailonerror(true);

            task.createArg().setValue("-p" + mFramework);
            task.createArg().setValue("-o" + mGenFolder);
            // add all the source folders as import in case an aidl file in a source folder
            // imports a parcelable from another source folder.
            for (String importFolder : sourceFolders) {
                task.createArg().setValue("-I" + importFolder);
            }

            // set auto dependency file creation
            task.createArg().setValue("-a");

            task.createArg().setValue(filePath);

            // execute it.
            task.execute();
        }

        public void displayMessage(DisplayType type, int count) {
            switch (type) {
                case FOUND:
                    System.out.println(String.format("Found %1$d AIDL files.", count));
                    break;
                case COMPILING:
                    if (count > 0) {
                        System.out.println(String.format("Compiling %1$d AIDL files.",
                                count));
                    } else {
                        System.out.println("No AIDL files to compile.");
                    }
                    break;
                case REMOVE_OUTPUT:
                    System.out.println(String.format("Found %1$d obsolete output files to remove.",
                            count));
                    break;
                case REMOVE_DEP:
                    System.out.println(
                            String.format("Found %1$d obsolete dependency files to remove.",
                                    count));
                    break;
            }
        }

    }

    /**
     * Sets the value of the "executable" attribute.
     * @param executable the value.
     */
    public void setExecutable(Path executable) {
        mExecutable = TaskHelper.checkSinglePath("executable", executable);
    }

    public void setFramework(Path value) {
        mFramework = TaskHelper.checkSinglePath("framework", value);
    }

    public void setGenFolder(Path value) {
        mGenFolder = TaskHelper.checkSinglePath("genFolder", value);
    }

    public Path createSource() {
        Path p = new Path(getProject());
        mPaths.add(p);
        return p;
    }

    @Override
    public void execute() throws BuildException {
        if (mExecutable == null) {
            throw new BuildException("AidlExecTask's 'executable' is required.");
        }
        if (mFramework == null) {
            throw new BuildException("AidlExecTask's 'framework' is required.");
        }
        if (mGenFolder == null) {
            throw new BuildException("AidlExecTask's 'genFolder' is required.");
        }

        processFiles(new AidlProcessor(), mPaths, mGenFolder);
    }

}
