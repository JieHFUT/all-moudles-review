/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class VersionProps {

    private static final String launcher_name =
        "java";

    // This field is read by HotSpot
    private static final String java_version =
        "17.0.13";

    private static final String java_version_date =
        "2024-10-15";

    // This field is read by HotSpot
    private static final String java_runtime_name =
        "Java(TM) SE Runtime Environment";

    // This field is read by HotSpot
    private static final String java_runtime_version =
        "17.0.13+10-LTS-268";

    private static final String VERSION_NUMBER =
        "17.0.13";

    private static final String VERSION_SPECIFICATION =
        "17";

    private static final String VERSION_BUILD =
        "10";

    private static final String VERSION_PRE =
        "";

    private static final String VERSION_OPT =
        "LTS-268";

    private static final boolean isLTS =
        "LTS-268".startsWith("LTS");

    private static final String CLASSFILE_MAJOR_MINOR =
        "61.0";

    private static final String VENDOR =
        "Oracle Corporation";

    private static final String VENDOR_URL =
        "https://java.oracle.com/";

    // The remaining VENDOR_* fields must not be final,
    // so that they can be redefined by jlink plugins

    // This field is read by HotSpot
    private static String VENDOR_VERSION =
        "";

    private static String VENDOR_URL_BUG =
        "https://bugreport.java.com/bugreport/";

    // This field is read by HotSpot
    @SuppressWarnings("unused")
    private static String VENDOR_URL_VM_BUG =
        "https://bugreport.java.com/bugreport/crash.jsp";

    /**
     * Initialize system properties using build provided values.
     *
     * @param props Map instance in which to insert the properties
     */
    public static void init(Map<String, String> props) {
        props.put("java.version", java_version);
        props.put("java.version.date", java_version_date);
        props.put("java.runtime.version", java_runtime_version);
        props.put("java.runtime.name", java_runtime_name);
        if (!VENDOR_VERSION.isEmpty())
            props.put("java.vendor.version", VENDOR_VERSION);

        props.put("java.class.version", CLASSFILE_MAJOR_MINOR);

        props.put("java.specification.version", VERSION_SPECIFICATION);
        props.put("java.specification.maintenance.version", "1");
        props.put("java.specification.name", "Java Platform API Specification");
        props.put("java.specification.vendor", "Oracle Corporation");

        props.put("java.vendor", VENDOR);
        props.put("java.vendor.url", VENDOR_URL);
        props.put("java.vendor.url.bug", VENDOR_URL_BUG);
    }

    private static int parseVersionNumber(String version, int prevIndex, int index) {
        if (index - prevIndex > 1 &&
            Character.digit(version.charAt(prevIndex), 10) <= 0)
            throw new IllegalArgumentException("Leading zeros not supported (" +
                    version.substring(prevIndex, index) + ")");
        return Integer.parseInt(version, prevIndex, index, 10);
    }

    // This method is reflectively used by regression tests.
    static List<Integer> parseVersionNumbers(String version) {
        // Let's find the size of an array required to hold $VNUM components
        int size = 0;
        int prevIndex = 0;
        do {
            prevIndex = version.indexOf('.', prevIndex) + 1;
            size++;
        } while (prevIndex > 0);
        Integer[] verNumbers = new Integer[size];

        // Fill in the array with $VNUM components
        int n = 0;
        prevIndex = 0;
        int index = version.indexOf('.');
        while (index > -1) {
            verNumbers[n] = parseVersionNumber(version, prevIndex, index);
            prevIndex = index + 1; // Skip the period
            index = version.indexOf('.', prevIndex);
            n++;
        }
        verNumbers[n] = parseVersionNumber(version, prevIndex, version.length());

        if (verNumbers[0] == 0 || verNumbers[n] == 0)
            throw new IllegalArgumentException("Leading/trailing zeros not allowed (" +
                    Arrays.toString(verNumbers) + ")");

        return List.of(verNumbers);
    }

    static List<Integer> versionNumbers() {
        return parseVersionNumbers(VERSION_NUMBER);
    }

    static Optional<String> pre() {
        return optionalOf(VERSION_PRE);
    }

    static Optional<Integer> build() {
        return VERSION_BUILD.isEmpty() ?
                Optional.empty() :
                Optional.of(Integer.parseInt(VERSION_BUILD));
    }

    static Optional<String> optional() {
        return optionalOf(VERSION_OPT);
    }

    // Treat empty strings as value not being present
    private static Optional<String> optionalOf(String value) {
        if (!value.isEmpty()) {
            return Optional.of(value);
        } else {
            return Optional.empty();
        }
    }

    /**
     * In case you were wondering this method is called by java -version.
     */
    public static void print(boolean err) {
        print(err, false);
    }

    /**
     * This is the same as print except that it adds an extra line-feed
     * at the end, typically used by the -showversion in the launcher
     */
    public static void println(boolean err) {
        print(err, true);
    }

    /**
     * Print version info.
     */
    private static void print(boolean err, boolean newln) {
        PrintStream ps = err ? System.err : System.out;

        /* First line: platform version. */
        if (err) {
            ps.println(launcher_name + " version \"" + java_version + "\""
                       + " " + java_version_date
                       + (isLTS ? " LTS" : ""));
        } else {
            /* Use a format more in line with GNU conventions */
            ps.println(launcher_name + " " + java_version
                       + " " + java_version_date
                       + (isLTS ? " LTS" : ""));
        }

        /* Second line: runtime version (ie, libraries). */
        String jdk_debug_level = System.getProperty("jdk.debug", "release");
        if ("release".equals(jdk_debug_level)) {
           /* Do not show debug level "release" builds */
            jdk_debug_level = "";
        } else {
            jdk_debug_level = jdk_debug_level + " ";
        }

        String vendor_version = (VENDOR_VERSION.isEmpty()
                                 ? "" : " " + VENDOR_VERSION);

        ps.println(java_runtime_name + vendor_version
                   + " (" + jdk_debug_level + "build " + java_runtime_version + ")");

        /* Third line: JVM information. */
        String java_vm_name    = System.getProperty("java.vm.name");
        String java_vm_version = System.getProperty("java.vm.version");
        String java_vm_info    = System.getProperty("java.vm.info");
        ps.println(java_vm_name + vendor_version
                   + " (" + jdk_debug_level + "build " + java_vm_version + ", "
                            + java_vm_info + ")");

    }

}
