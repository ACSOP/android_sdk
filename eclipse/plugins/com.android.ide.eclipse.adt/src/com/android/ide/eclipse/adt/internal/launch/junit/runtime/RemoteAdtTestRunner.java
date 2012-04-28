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

package com.android.ide.eclipse.adt.internal.launch.junit.runtime;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.testrunner.ITestRunListener;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.internal.launch.LaunchMessages;

import org.eclipse.jdt.internal.junit.runner.MessageIds;
import org.eclipse.jdt.internal.junit.runner.RemoteTestRunner;
import org.eclipse.jdt.internal.junit.runner.TestExecution;
import org.eclipse.jdt.internal.junit.runner.TestReferenceFailure;

import java.io.IOException;
import java.util.Map;

/**
 * Supports Eclipse JUnit execution of Android tests.
 * <p/>
 * Communicates back to a Eclipse JDT JUnit client via a socket connection.
 *
 * @see org.eclipse.jdt.internal.junit.runner.RemoteTestRunner for more details on the protocol
 */
@SuppressWarnings("restriction")
public class RemoteAdtTestRunner extends RemoteTestRunner {

    private static final String DELAY_MSEC_KEY = "delay_msec";
    /** the delay between each test execution when in collecting test info */
    private static final String COLLECT_TEST_DELAY_MS = "15";

    private AndroidJUnitLaunchInfo mLaunchInfo;
    private TestExecution mExecution;

    /**
     * Initialize the JDT JUnit test runner parameters from the {@code args}.
     *
     * @param args name-value pair of arguments to pass to parent JUnit runner.
     * @param launchInfo the Android specific test launch info
     */
    protected void init(String[] args, AndroidJUnitLaunchInfo launchInfo) {
        defaultInit(args);
        mLaunchInfo = launchInfo;
    }

    /**
     * Runs a set of tests, and reports back results using parent class.
     * <p/>
     * JDT Unit expects to be sent data in the following sequence:
     * <ol>
     *   <li>The total number of tests to be executed.</li>
     *   <li>The test 'tree' data about the tests to be executed, which is composed of the set of
     *   test class names, the number of tests in each class, and the names of each test in the
     *   class.</li>
     *   <li>The test execution result for each test method. Expects individual notifications of
     *   the test execution start, any failures, and the end of the test execution.</li>
     *   <li>The end of the test run, with its elapsed time.</li>
     * </ol>
     * <p/>
     * In order to satisfy this, this method performs two actual Android instrumentation runs.
     * The first is a 'log only' run that will collect the test tree data, without actually
     * executing the tests,  and send it back to JDT JUnit. The second is the actual test execution,
     * whose results will be communicated back in real-time to JDT JUnit.
     *
     * @param testClassNames ignored - the AndroidJUnitLaunchInfo will be used to determine which
     *     tests to run.
     * @param testName ignored
     * @param execution used to report test progress
     */
    @Override
    public void runTests(String[] testClassNames, String testName, TestExecution execution) {
        // hold onto this execution reference so it can be used to report test progress
        mExecution = execution;

        RemoteAndroidTestRunner runner = new RemoteAndroidTestRunner(mLaunchInfo.getAppPackage(),
                mLaunchInfo.getRunner(), mLaunchInfo.getDevice());

        if (mLaunchInfo.getTestClass() != null) {
            if (mLaunchInfo.getTestMethod() != null) {
                runner.setMethodName(mLaunchInfo.getTestClass(), mLaunchInfo.getTestMethod());
            } else {
                runner.setClassName(mLaunchInfo.getTestClass());
            }
        }

        if (mLaunchInfo.getTestPackage() != null) {
            runner.setTestPackageName(mLaunchInfo.getTestPackage());
        }

        // set log only to first collect test case info, so Eclipse has correct test case count/
        // tree info
        runner.setLogOnly(true);
        // add a small delay between each test. Otherwise for large test suites framework may
        // report Binder transaction failures
        runner.addInstrumentationArg(DELAY_MSEC_KEY, COLLECT_TEST_DELAY_MS);
        TestCollector collector = new TestCollector();
        try {
            AdtPlugin.printToConsole(mLaunchInfo.getProject(), "Collecting test information");

            runner.run(collector);
            if (collector.getErrorMessage() != null) {
                // error occurred during test collection.
                reportError(collector.getErrorMessage());
                // abort here
                notifyTestRunEnded(0);
                return;
            }
            notifyTestRunStarted(collector.getTestCaseCount());
            AdtPlugin.printToConsole(mLaunchInfo.getProject(),
                    "Sending test information to Eclipse");

            collector.sendTrees(this);

            // now do real execution
            runner.setLogOnly(false);
            runner.removeInstrumentationArg(DELAY_MSEC_KEY);
            if (mLaunchInfo.isDebugMode()) {
                runner.setDebug(true);
            }
            AdtPlugin.printToConsole(mLaunchInfo.getProject(), "Running tests...");
            runner.run(new TestRunListener());
        } catch (TimeoutException e) {
            reportError(LaunchMessages.RemoteAdtTestRunner_RunTimeoutException);
        } catch (IOException e) {
            reportError(String.format(LaunchMessages.RemoteAdtTestRunner_RunIOException_s,
                    e.getMessage()));
        } catch (AdbCommandRejectedException e) {
            reportError(String.format(
                    LaunchMessages.RemoteAdtTestRunner_RunAdbCommandRejectedException_s,
                    e.getMessage()));
        } catch (ShellCommandUnresponsiveException e) {
            reportError(LaunchMessages.RemoteAdtTestRunner_RunTimeoutException);
        }
    }

    /**
     * Main entry method to run tests
     *
     * @param programArgs JDT JUnit program arguments to be processed by parent
     * @param junitInfo the {@link AndroidJUnitLaunchInfo} containing info about this test ru
     */
    public void runTests(String[] programArgs, AndroidJUnitLaunchInfo junitInfo) {
        init(programArgs, junitInfo);
        run();
    }

    /**
     * Stop the current test run.
     */
    public void terminate() {
        stop();
    }

    @Override
    protected void stop() {
        if (mExecution != null) {
            mExecution.stop();
        }
    }

    private void notifyTestRunEnded(long elapsedTime) {
        // copy from parent - not ideal, but method is private
        sendMessage(MessageIds.TEST_RUN_END + elapsedTime);
        flush();
        //shutDown();
    }

    /**
     * @param errorMessage
     */
    private void reportError(String errorMessage) {
        AdtPlugin.printErrorToConsole(mLaunchInfo.getProject(),
                String.format(LaunchMessages.RemoteAdtTestRunner_RunFailedMsg_s, errorMessage));
        // is this needed?
        //notifyTestRunStopped(-1);
    }

    /**
     * TestRunListener that communicates results in real-time back to JDT JUnit
     */
    private class TestRunListener implements ITestRunListener {

        public void testEnded(TestIdentifier test, Map<String, String> ignoredTestMetrics) {
            mExecution.getListener().notifyTestEnded(new TestCaseReference(test));
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testFailed(com.android.ddmlib.testrunner.ITestRunListener.TestFailure, com.android.ddmlib.testrunner.TestIdentifier, java.lang.String)
         */
        public void testFailed(TestFailure status, TestIdentifier test, String trace) {
            String statusString;
            if (status == TestFailure.ERROR) {
                statusString = MessageIds.TEST_ERROR;
            } else {
                statusString = MessageIds.TEST_FAILED;
            }
            TestReferenceFailure failure =
                new TestReferenceFailure(new TestCaseReference(test),
                        statusString, trace, null);
            mExecution.getListener().notifyTestFailed(failure);
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testRunEnded(long, Map<String, String>)
         */
        public void testRunEnded(long elapsedTime, Map<String, String> runMetrics) {
            notifyTestRunEnded(elapsedTime);
            AdtPlugin.printToConsole(mLaunchInfo.getProject(),
                    LaunchMessages.RemoteAdtTestRunner_RunCompleteMsg);
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testRunFailed(java.lang.String)
         */
        public void testRunFailed(String errorMessage) {
            reportError(errorMessage);
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testRunStarted(int)
         */
        public void testRunStarted(String runName, int testCount) {
            // ignore
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testRunStopped(long)
         */
        public void testRunStopped(long elapsedTime) {
            notifyTestRunStopped(elapsedTime);
            AdtPlugin.printToConsole(mLaunchInfo.getProject(),
                    LaunchMessages.RemoteAdtTestRunner_RunStoppedMsg);
        }

        /* (non-Javadoc)
         * @see com.android.ddmlib.testrunner.ITestRunListener#testStarted(com.android.ddmlib.testrunner.TestIdentifier)
         */
        public void testStarted(TestIdentifier test) {
            TestCaseReference testId = new TestCaseReference(test);
            mExecution.getListener().notifyTestStarted(testId);
        }

    }

    /**
     * Override parent to get extra logs.
     */
    @Override
    protected boolean connect() {
        boolean result = super.connect();
        if (!result) {
            AdtPlugin.printErrorToConsole(mLaunchInfo.getProject(),
                    "Connect to Eclipse test result listener failed");
        }
        return result;
    }

    /**
     * Override parent to dump error message to console.
     */
    @Override
    public void runFailed(String message, Exception exception) {
        if (exception != null) {
            AdtPlugin.logAndPrintError(exception, mLaunchInfo.getProject().getName(),
                    "Test launch failed: %s", message);
        } else {
            AdtPlugin.printErrorToConsole(mLaunchInfo.getProject(), "Test launch failed: %s",
                    message);
        }
    }
}
