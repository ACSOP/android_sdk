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

package com.android.sdklib.internal.repository;

import com.android.sdklib.SdkManager;
import com.android.sdklib.repository.SdkAddonConstants;
import com.android.util.Pair;

import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.TestCase;

/**
 * Tests for {@link SdkAddonSource}
 */
public class SdkAddonSourceTest extends TestCase {

    /**
     * An internal helper class to give us visibility to the protected members we want
     * to test.
     */
    private static class MockSdkAddonSource extends SdkAddonSource {
        public MockSdkAddonSource() {
            super("fake-url", null /*uiName*/);
        }

        public Document _findAlternateToolsXml(InputStream xml) {
            return super.findAlternateToolsXml(xml);
        }

        public boolean _parsePackages(Document doc, String nsUri, ITaskMonitor monitor) {
            return super.parsePackages(doc, nsUri, monitor);
        }

        public int _getXmlSchemaVersion(InputStream xml) {
            return super.getXmlSchemaVersion(xml);
        }

        public String _validateXml(InputStream xml, String url, int version,
                                   String[] outError, Boolean[] validatorFound) {
            return super.validateXml(xml, url, version, outError, validatorFound);
        }

        public Document _getDocument(InputStream xml, ITaskMonitor monitor) {
            return super.getDocument(xml, monitor);
        }

    }

    private MockSdkAddonSource mSource;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mSource = new MockSdkAddonSource();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        mSource = null;
    }

    public void testFindAlternateToolsXml_Errors() throws Exception {
        // Support null as input
        Document result = mSource._findAlternateToolsXml(null);
        assertNull(result);

        // Support an empty input
        String str = "";
        ByteArrayInputStream input = new ByteArrayInputStream(str.getBytes());
        result = mSource._findAlternateToolsXml(input);
        assertNull(result);

        // Support a random string as input
        str = "Some random string, not even HTML nor XML";
        input = new ByteArrayInputStream(str.getBytes());
        result = mSource._findAlternateToolsXml(input);
        assertNull(result);

        // Support an HTML input, e.g. a typical 404 document as returned by DL
        str = "<html><head> " +
        "<meta http-equiv=\"content-type\" content=\"text/html;charset=utf-8\"> " +
        "<title>404 Not Found</title> " + "<style><!--" + "body {font-family: arial,sans-serif}" +
        "div.nav { ... blah blah more css here ... color: green}" +
        "//--></style> " + "<script><!--" + "var rc=404;" + "//-->" + "</script> " + "</head> " +
        "<body text=#000000 bgcolor=#ffffff> " +
        "<table border=0 cellpadding=2 cellspacing=0 width=100%><tr><td rowspan=3 width=1% nowrap> " +
        "<b><font face=times color=#0039b6 size=10>G</font><font face=times color=#c41200 size=10>o</font><font face=times color=#f3c518 size=10>o</font><font face=times color=#0039b6 size=10>g</font><font face=times color=#30a72f size=10>l</font><font face=times color=#c41200 size=10>e</font>&nbsp;&nbsp;</b> " +
        "<td>&nbsp;</td></tr> " +
        "<tr><td bgcolor=\"#3366cc\"><font face=arial,sans-serif color=\"#ffffff\"><b>Error</b></td></tr> " +
        "<tr><td>&nbsp;</td></tr></table> " + "<blockquote> " + "<H1>Not Found</H1> " +
        "The requested URL <code>/404</code> was not found on this server." + " " + "<p> " +
        "</blockquote> " +
        "<table width=100% cellpadding=0 cellspacing=0><tr><td bgcolor=\"#3366cc\"><img alt=\"\" width=1 height=4></td></tr></table> " +
        "</body></html> ";
        input = new ByteArrayInputStream(str.getBytes());
        result = mSource._findAlternateToolsXml(input);
        assertNull(result);

        // Support some random XML document, totally unrelated to our sdk-repository schema
        str = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
        "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
        "    package=\"some.cool.app\" android:versionName=\"1.6.04\" android:versionCode=\"1604\">" +
        "    <application android:label=\"@string/app_name\" android:icon=\"@drawable/icon\"/>" +
        "</manifest>";
        input = new ByteArrayInputStream(str.getBytes());
        result = mSource._findAlternateToolsXml(input);
        assertNull(result);
    }

    /**
     * Validate that findAlternateToolsXml doesn't work for addon even
     * when trying to load a valid addon xml.
     */
    public void testFindAlternateToolsXml_1() throws Exception {
        InputStream xmlStream = getTestResource("/com/android/sdklib/testdata/addon_sample_1.xml");

        Document result = mSource._findAlternateToolsXml(xmlStream);
        assertNull(result);
    }

    /**
     * Validate we can still load a valid add-on schema version 1
     */
    public void testLoadOldXml_1() throws Exception {
        InputStream xmlStream = getTestResource("/com/android/sdklib/testdata/addon_sample_1.xml");

        // guess the version from the XML document
        int version = mSource._getXmlSchemaVersion(xmlStream);
        assertEquals(1, version);

        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        String url = "not-a-valid-url://" + SdkAddonConstants.URL_DEFAULT_FILENAME;

        String uri = mSource._validateXml(xmlStream, url, version, validationError, validatorFound);
        assertEquals(Boolean.TRUE, validatorFound[0]);
        assertEquals(null, validationError[0]);
        assertEquals(SdkAddonConstants.getSchemaUri(1), uri);

        // Validation was successful, load the document
        MockMonitor monitor = new MockMonitor();
        Document doc = mSource._getDocument(xmlStream, monitor);
        assertNotNull(doc);

        // Get the packages
        assertTrue(mSource._parsePackages(doc, uri, monitor));

        assertEquals("Found My First add-on by John Doe, Android API 1, revision 1\n" +
                     "Found My Second add-on by John Deer, Android API 2, revision 42\n" +
                     "Found This add-on has no libraries by Joe Bar, Android API 4, revision 3\n" +
                     "Found G USB Driver package, revision 43 (Obsolete)\n" +
                     "Found Android Vendor Extra API Dep package, revision 2 (Obsolete)\n" +
                     "Found Unkown Extra package, revision 2 (Obsolete)\n",
                monitor.getCapturedVerboseLog());
        assertEquals("", monitor.getCapturedLog());
        assertEquals("", monitor.getCapturedErrorLog());

        // check the packages we found... we expected to find 11 packages with each at least
        // one archive.
        Package[] pkgs = mSource.getPackages();
        assertEquals(6, pkgs.length);
        for (Package p : pkgs) {
            assertTrue(p.getArchives().length >= 1);
        }

        // Check the extra packages path, vendor, install folder

        final String osSdkPath = "SDK";
        final SdkManager sdkManager = new MockEmptySdkManager(osSdkPath);

        ArrayList<String> extraPaths   = new ArrayList<String>();
        ArrayList<String> extraVendors = new ArrayList<String>();
        ArrayList<File>   extraInstall = new ArrayList<File>();
        for (Package p : pkgs) {
            if (p instanceof ExtraPackage) {
                extraPaths.add(((ExtraPackage) p).getPath());
                extraVendors.add(((ExtraPackage) p).getVendor());
                extraInstall.add(((ExtraPackage) p).getInstallFolder(osSdkPath, sdkManager));
            }
        }
        assertEquals(
                "[extra_api_dep, usb_driver, extra0000005f]",
                Arrays.toString(extraPaths.toArray()));
        assertEquals(
                "[android_vendor, g, vendor0000005f]",
                Arrays.toString(extraVendors.toArray()));
        assertEquals(
                ("[SDK/extras/android_vendor/extra_api_dep, " +
                  "SDK/extras/g/usb_driver, " +
                  "SDK/extras/vendor0000005f/extra0000005f]").replace('/', File.separatorChar),
                Arrays.toString(extraInstall.toArray()));
    }

    /**
     * Validate we can still load a valid add-on schema version 2
     */
    public void testLoadOldXml_2() throws Exception {
        InputStream xmlStream = getTestResource("/com/android/sdklib/testdata/addon_sample_2.xml");

        // guess the version from the XML document
        int version = mSource._getXmlSchemaVersion(xmlStream);
        assertEquals(2, version);

        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        String url = "not-a-valid-url://" + SdkAddonConstants.URL_DEFAULT_FILENAME;

        String uri = mSource._validateXml(xmlStream, url, version, validationError, validatorFound);
        assertEquals(Boolean.TRUE, validatorFound[0]);
        assertEquals(null, validationError[0]);
        assertEquals(SdkAddonConstants.getSchemaUri(2), uri);

        // Validation was successful, load the document
        MockMonitor monitor = new MockMonitor();
        Document doc = mSource._getDocument(xmlStream, monitor);
        assertNotNull(doc);

        // Get the packages
        assertTrue(mSource._parsePackages(doc, uri, monitor));

        assertEquals("Found My First add-on by John Doe, Android API 1, revision 1\n" +
                     "Found My Second add-on by John Deer, Android API 2, revision 42\n" +
                     "Found This add-on has no libraries by Joe Bar, Android API 4, revision 3\n" +
                     "Found G USB Driver package, revision 43 (Obsolete)\n" +
                     "Found Android Vendor Extra API Dep package, revision 2 (Obsolete)\n" +
                     "Found Unkown Extra package, revision 2 (Obsolete)\n",
                monitor.getCapturedVerboseLog());
        assertEquals("", monitor.getCapturedLog());
        assertEquals("", monitor.getCapturedErrorLog());

        // check the packages we found... we expected to find 11 packages with each at least
        // one archive.
        // Note the order doesn't necessary match the one from the
        // assertEquald(getCapturedVerboseLog) because packages are sorted using the
        // Packages' sorting order, e.g. all platforms are sorted by descending API level, etc.
        Package[] pkgs = mSource.getPackages();

        assertEquals(6, pkgs.length);
        for (Package p : pkgs) {
            assertTrue(p.getArchives().length >= 1);
        }

        // Check the layoutlib of the platform packages.
        ArrayList<Pair<Integer, Integer>> layoutlibVers = new ArrayList<Pair<Integer,Integer>>();
        for (Package p : pkgs) {
            if (p instanceof AddonPackage) {
                layoutlibVers.add(((AddonPackage) p).getLayoutlibVersion());
            }
        }
        assertEquals(
                 "[Pair [first=3, second=42], " +         // for #3 "This add-on has no libraries"
                 "Pair [first=0, second=0], " +           // for #2 "My Second add-on"
                 "Pair [first=5, second=0]]",            // for #1 "My First add-on"
                Arrays.toString(layoutlibVers.toArray()));


        // Check the extra packages path, vendor, install folder

        final String osSdkPath = "SDK";
        final SdkManager sdkManager = new MockEmptySdkManager(osSdkPath);

        ArrayList<String> extraPaths   = new ArrayList<String>();
        ArrayList<String> extraVendors = new ArrayList<String>();
        ArrayList<File>   extraInstall = new ArrayList<File>();
        for (Package p : pkgs) {
            if (p instanceof ExtraPackage) {
                extraPaths.add(((ExtraPackage) p).getPath());
                extraVendors.add(((ExtraPackage) p).getVendor());
                extraInstall.add(((ExtraPackage) p).getInstallFolder(osSdkPath, sdkManager));
            }
        }
        assertEquals(
                "[extra_api_dep, usb_driver, extra0000005f]",
                Arrays.toString(extraPaths.toArray()));
        assertEquals(
                "[android_vendor, g, vendor0000005f]",
                Arrays.toString(extraVendors.toArray()));
        assertEquals(
                ("[SDK/extras/android_vendor/extra_api_dep, " +
                  "SDK/extras/g/usb_driver, " +
                  "SDK/extras/vendor0000005f/extra0000005f]").replace('/', File.separatorChar),
                Arrays.toString(extraInstall.toArray()));
    }

    /**
     * Validate we can still load a valid add-on schema version 3
     */
    public void testLoadOldXml_3() throws Exception {
        InputStream xmlStream = getTestResource("/com/android/sdklib/testdata/addon_sample_3.xml");

        // guess the version from the XML document
        int version = mSource._getXmlSchemaVersion(xmlStream);
        assertEquals(3, version);

        Boolean[] validatorFound = new Boolean[] { Boolean.FALSE };
        String[] validationError = new String[] { null };
        String url = "not-a-valid-url://" + SdkAddonConstants.URL_DEFAULT_FILENAME;

        String uri = mSource._validateXml(xmlStream, url, version, validationError, validatorFound);
        assertEquals(Boolean.TRUE, validatorFound[0]);
        assertEquals(null, validationError[0]);
        assertEquals(SdkAddonConstants.getSchemaUri(3), uri);

        // Validation was successful, load the document
        MockMonitor monitor = new MockMonitor();
        Document doc = mSource._getDocument(xmlStream, monitor);
        assertNotNull(doc);

        // Get the packages
        assertTrue(mSource._parsePackages(doc, uri, monitor));

        assertEquals("Found My First add-on by John Doe, Android API 1, revision 1\n" +
                     "Found My Second add-on by John Deer, Android API 2, revision 42\n" +
                     "Found This add-on has no libraries by Joe Bar, Android API 4, revision 3\n" +
                     "Found G USB Driver package, revision 43 (Obsolete)\n" +
                     "Found Android Vendor Extra API Dep package, revision 2 (Obsolete)\n" +
                     "Found Unkown Extra package, revision 2 (Obsolete)\n",
                monitor.getCapturedVerboseLog());
        assertEquals("", monitor.getCapturedLog());
        assertEquals("", monitor.getCapturedErrorLog());

        // check the packages we found... we expected to find 6 packages with each at least
        // one archive.
        // Note the order doesn't necessary match the one from the
        // assertEquald(getCapturedVerboseLog) because packages are sorted using the
        // Packages' sorting order, e.g. all platforms are sorted by descending API level, etc.
        Package[] pkgs = mSource.getPackages();

        assertEquals(6, pkgs.length);
        for (Package p : pkgs) {
            assertTrue(p.getArchives().length >= 1);
        }

        // Check the layoutlib of the platform packages.
        ArrayList<Pair<Integer, Integer>> layoutlibVers = new ArrayList<Pair<Integer,Integer>>();
        for (Package p : pkgs) {
            if (p instanceof AddonPackage) {
                layoutlibVers.add(((AddonPackage) p).getLayoutlibVersion());
            }
        }
        assertEquals(
                 "[Pair [first=3, second=42], " +         // for #3 "This add-on has no libraries"
                 "Pair [first=0, second=0], " +           // for #2 "My Second add-on"
                 "Pair [first=5, second=0]]",            // for #1 "My First add-on"
                Arrays.toString(layoutlibVers.toArray()));


        // Check the extra packages: path, vendor, install folder, old-paths

        final String osSdkPath = "SDK";
        final SdkManager sdkManager = new MockEmptySdkManager(osSdkPath);

        ArrayList<String> extraPaths   = new ArrayList<String>();
        ArrayList<String> extraVendors = new ArrayList<String>();
        ArrayList<File>   extraInstall = new ArrayList<File>();
        ArrayList<ArrayList<String>> extraFilePaths = new ArrayList<ArrayList<String>>();
        for (Package p : pkgs) {
            if (p instanceof ExtraPackage) {
                ExtraPackage ep = (ExtraPackage) p;
                // combine path and old-paths in the form "path [old_path1, old_path2]"
                extraPaths.add(ep.getPath() + " " + Arrays.toString(ep.getOldPaths()));
                extraVendors.add(ep.getVendor());
                extraInstall.add(ep.getInstallFolder(osSdkPath, sdkManager));

                ArrayList<String> filePaths = new ArrayList<String>();
                for (String filePath : ep.getProjectFiles()) {
                    filePaths.add(filePath);
                }
                extraFilePaths.add(filePaths);
            }
        }
        assertEquals(
                "[extra_api_dep [path1, old_path2, oldPath3], " +
                 "usb_driver [], " +
                 "extra0000005f []]",
                Arrays.toString(extraPaths.toArray()));
        assertEquals(
                "[android_vendor, " +
                 "g, " +
                 "vendor0000005f]",
                Arrays.toString(extraVendors.toArray()));
        assertEquals(
                ("[SDK/extras/android_vendor/extra_api_dep, " +
                  "SDK/extras/g/usb_driver, " +
                  "SDK/extras/vendor0000005f/extra0000005f]").replace('/', File.separatorChar),
                Arrays.toString(extraInstall.toArray()));
        assertEquals(
                "[[v8/veggies_8.jar, root.jar, dir1/dir 2 with space/mylib.jar], " +
                 "[], " +
                 "[]]",
                Arrays.toString(extraFilePaths.toArray()));
    }

    /**
     * Returns an SdkLib file resource as a {@link ByteArrayInputStream},
     * which has the advantage that we can use {@link InputStream#reset()} on it
     * at any time to read it multiple times.
     * <p/>
     * The default for getResourceAsStream() is to return a {@link FileInputStream} that
     * does not support reset(), yet we need it in the tested code.
     *
     * @throws IOException if some I/O read fails
     */
    private ByteArrayInputStream getTestResource(String filename) throws IOException {
        InputStream xmlStream = this.getClass().getResourceAsStream(filename);

        try {
            byte[] data = new byte[8192];
            int offset = 0;
            int n;

            while ((n = xmlStream.read(data, offset, data.length - offset)) != -1) {
                offset += n;

                if (offset == data.length) {
                    byte[] newData = new byte[offset + 8192];
                    System.arraycopy(data, 0, newData, 0, offset);
                    data = newData;
                }
            }

            return new ByteArrayInputStream(data, 0, offset);
        } finally {
            if (xmlStream != null) {
                xmlStream.close();
            }
        }
    }
}
