/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.loader;

import jdk.internal.misc.VM;
import jdk.internal.ref.CleanerFactory;
import jdk.internal.util.StaticProperty;

import java.io.File;
import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Native libraries are loaded via {@link System#loadLibrary(String)},
 * {@link System#load(String)}, {@link Runtime#loadLibrary(String)} and
 * {@link Runtime#load(String)}.  They are caller-sensitive.
 *
 * Each class loader has a NativeLibraries instance to register all of its
 * loaded native libraries.  System::loadLibrary (and other APIs) only
 * allows a native library to be loaded by one class loader, i.e. one
 * NativeLibraries instance.  Any attempt to load a native library that
 * has already been loaded by a class loader with another class loader
 * will fail.
 */
public final class NativeLibraries {
    private static final boolean loadLibraryOnlyIfPresent = ClassLoaderHelper.loadLibraryOnlyIfPresent();
    private final Map<String, NativeLibraryImpl> libraries = new ConcurrentHashMap<>();
    private final ClassLoader loader;
    // caller, if non-null, is the fromClass parameter for NativeLibraries::loadLibrary
    // unless specified
    private final Class<?> caller;      // may be null
    private final boolean searchJavaLibraryPath;
    // loading JNI native libraries
    private final boolean isJNI;

    /**
     * Creates a NativeLibraries instance for loading JNI native libraries
     * via for System::loadLibrary use.
     *
     * 1. Support of auto-unloading.  The loaded native libraries are unloaded
     *    when the class loader is reclaimed.
     * 2. Support of linking of native method.  See JNI spec.
     * 3. Restriction on a native library that can only be loaded by one class loader.
     *    Each class loader manages its own set of native libraries.
     *    The same JNI native library cannot be loaded into more than one class loader.
     *
     * This static factory method is intended only for System::loadLibrary use.
     *
     * @see <a href="${docroot}/specs/jni/invocation.html##library-and-version-management">
     *     JNI Specification: Library and Version Management</a>
     */
    public static NativeLibraries jniNativeLibraries(ClassLoader loader) {
        return new NativeLibraries(loader);
    }

    /**
     * Creates a raw NativeLibraries instance that has the following properties:
     * 1. Native libraries loaded in this raw NativeLibraries instance are
     *    not JNI native libraries.  Hence JNI_OnLoad and JNI_OnUnload will
     *    be ignored.  No support for linking of native method.
     * 2. Native libraries not auto-unloaded.  They may be explicitly unloaded
     *    via NativeLibraries::unload.
     * 3. No relationship with class loaders.
     *
     * This static factory method is restricted for JDK trusted class use.
     */
    public static NativeLibraries rawNativeLibraries(Class<?> trustedCaller,
                                                     boolean searchJavaLibraryPath) {
        return new NativeLibraries(trustedCaller, searchJavaLibraryPath);
    }

    private NativeLibraries(ClassLoader loader) {
        // for null loader, default the caller to this class and
        // do not search java.library.path
        this.loader = loader;
        this.caller = loader != null ? null : NativeLibraries.class;
        this.searchJavaLibraryPath = loader != null ? true : false;
        this.isJNI = true;
    }

    /*
     * Constructs a NativeLibraries instance of no relationship with class loaders
     * and disabled auto unloading.
     */
    private NativeLibraries(Class<?> caller, boolean searchJavaLibraryPath) {
        Objects.requireNonNull(caller);
        if (!VM.isSystemDomainLoader(caller.getClassLoader())) {
            throw new IllegalArgumentException("must be JDK trusted class");
        }
        this.loader = caller.getClassLoader();
        this.caller = caller;
        this.searchJavaLibraryPath = searchJavaLibraryPath;
        this.isJNI = false;
    }

    /*
     * Find the address of the given symbol name from the native libraries
     * loaded in this NativeLibraries instance.
     */
    public long find(String name) {
        if (libraries.isEmpty())
            return 0;

        // the native libraries map may be updated in another thread
        // when a native library is being loaded.  No symbol will be
        // searched from it yet.
        for (NativeLibrary lib : libraries.values()) {
            long entry = lib.find(name);
            if (entry != 0) return entry;
        }
        return 0;
    }

    /*
     * Load a native library from the given file.  Returns null if the given
     * library is determined to be non-loadable, which is system-dependent.
     *
     * @param fromClass the caller class calling System::loadLibrary
     * @param file the path of the native library
     * @throws UnsatisfiedLinkError if any error in loading the native library
     */
    @SuppressWarnings("removal")
    public NativeLibrary loadLibrary(Class<?> fromClass, File file) {
        // Check to see if we're attempting to access a static library
        String name = findBuiltinLib(file.getName());
        boolean isBuiltin = (name != null);
        if (!isBuiltin) {
            name = AccessController.doPrivileged(new PrivilegedAction<>() {
                    public String run() {
                        try {
                            if (loadLibraryOnlyIfPresent && !file.exists()) {
                                return null;
                            }
                            return file.getCanonicalPath();
                        } catch (IOException e) {
                            return null;
                        }
                    }
                });
            if (name == null) {
                return null;
            }
        }
        return loadLibrary(fromClass, name, isBuiltin);
    }

    /**
     * Returns a NativeLibrary of the given name.
     *
     * @param fromClass the caller class calling System::loadLibrary
     * @param name      library name
     * @param isBuiltin built-in library
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    private NativeLibrary loadLibrary(Class<?> fromClass, String name, boolean isBuiltin) {
        ClassLoader loader = (fromClass == null) ? null : fromClass.getClassLoader();
        if (this.loader != loader) {
            throw new InternalError(fromClass.getName() + " not allowed to load library");
        }

        synchronized (loadedLibraryNames) {
            // find if this library has already been loaded and registered in this NativeLibraries
            NativeLibrary cached = libraries.get(name);
            if (cached != null) {
                return cached;
            }

            // cannot be loaded by other class loaders
            if (loadedLibraryNames.contains(name)) {
                throw new UnsatisfiedLinkError("Native Library " + name +
                        " already loaded in another classloader");
            }

            /*
             * When a library is being loaded, JNI_OnLoad function can cause
             * another loadLibrary invocation that should succeed.
             *
             * We use a static stack to hold the list of libraries we are
             * loading because this can happen only when called by the
             * same thread because this block is synchronous.
             *
             * If there is a pending load operation for the library, we
             * immediately return success; otherwise, we raise
             * UnsatisfiedLinkError.
             */
            for (NativeLibraryImpl lib : nativeLibraryContext) {
                if (name.equals(lib.name())) {
                    if (loader == lib.fromClass.getClassLoader()) {
                        return lib;
                    } else {
                        throw new UnsatisfiedLinkError("Native Library " +
                                name + " is being loaded in another classloader");
                    }
                }
            }

            NativeLibraryImpl lib = new NativeLibraryImpl(fromClass, name, isBuiltin, isJNI);
            // load the native library
            nativeLibraryContext.push(lib);
            try {
                if (!lib.open()) {
                    return null;    // fail to open the native library
                }
                // auto unloading is only supported for JNI native libraries
                // loaded by custom class loaders that can be unloaded.
                // built-in class loaders are never unloaded.
                boolean autoUnload = isJNI && !VM.isSystemDomainLoader(loader)
                        && loader != ClassLoaders.appClassLoader();
                if (autoUnload) {
                    // register the loaded native library for auto unloading
                    // when the class loader is reclaimed, all native libraries
                    // loaded that class loader will be unloaded.
                    // The entries in the libraries map are not removed since
                    // the entire map will be reclaimed altogether.
                    CleanerFactory.cleaner().register(loader, lib.unloader());
                }
            } finally {
                nativeLibraryContext.pop();
            }
            // register the loaded native library
            loadedLibraryNames.add(name);
            libraries.put(name, lib);
            return lib;
        }
    }

    /**
     * Loads a native library from the system library path and java library path.
     *
     * @param name library name
     *
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    public NativeLibrary loadLibrary(String name) {
        assert name.indexOf(File.separatorChar) < 0;
        assert caller != null;

        return loadLibrary(caller, name);
    }

    /**
     * Loads a native library from the system library path and java library path.
     *
     * @param name library name
     * @param fromClass the caller class calling System::loadLibrary
     *
     * @throws UnsatisfiedLinkError if the native library has already been loaded
     *      and registered in another NativeLibraries
     */
    public NativeLibrary loadLibrary(Class<?> fromClass, String name) {
        assert name.indexOf(File.separatorChar) < 0;

        NativeLibrary lib = findFromPaths(LibraryPaths.SYS_PATHS, fromClass, name);
        if (lib == null && searchJavaLibraryPath) {
            lib = findFromPaths(LibraryPaths.USER_PATHS, fromClass, name);
        }
        return lib;
    }

    /**
     * Unloads the given native library
     *
     * @param lib native library
     */
    public void unload(NativeLibrary lib) {
        if (isJNI) {
            throw new UnsupportedOperationException("explicit unloading cannot be used with auto unloading");
        }
        Objects.requireNonNull(lib);
        synchronized (loadedLibraryNames) {
            NativeLibraryImpl nl = libraries.remove(lib.name());
            if (nl != lib) {
                throw new IllegalArgumentException(lib.name() + " not loaded by this NativeLibraries instance");
            }
            // unload the native library and also remove from the global name registry
            nl.unloader().run();
        }
    }

    private NativeLibrary findFromPaths(String[] paths, Class<?> fromClass, String name) {
        for (String path : paths) {
            File libfile = new File(path, System.mapLibraryName(name));
            NativeLibrary nl = loadLibrary(fromClass, libfile);
            if (nl != null) {
                return nl;
            }
            libfile = ClassLoaderHelper.mapAlternativeName(libfile);
            if (libfile != null) {
                nl = loadLibrary(fromClass, libfile);
                if (nl != null) {
                    return nl;
                }
            }
        }
        return null;
    }

    /**
     * NativeLibraryImpl denotes a loaded native library instance.
     * Each NativeLibraries contains a map of loaded native libraries in the
     * private field {@code libraries}.
     *
     * Every native library requires a particular version of JNI. This is
     * denoted by the private {@code jniVersion} field.  This field is set by
     * the VM when it loads the library, and used by the VM to pass the correct
     * version of JNI to the native methods.
     */
    static class NativeLibraryImpl implements NativeLibrary {
        // the class from which the library is loaded, also indicates
        // the loader this native library belongs.
        final Class<?> fromClass;
        // the canonicalized name of the native library.
        // or static library name
        final String name;
        // Indicates if the native library is linked into the VM
        final boolean isBuiltin;
        // Indicate if this is JNI native library
        final boolean isJNI;

        // opaque handle to native library, used in native code.
        long handle;
        // the version of JNI environment the native library requires.
        int jniVersion;

        NativeLibraryImpl(Class<?> fromClass, String name, boolean isBuiltin, boolean isJNI) {
            assert !isBuiltin || isJNI : "a builtin native library must be JNI library";

            this.fromClass = fromClass;
            this.name = name;
            this.isBuiltin = isBuiltin;
            this.isJNI = isJNI;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public long find(String name) {
            return findEntry0(this, name);
        }

        Runnable unloader() {
            return new Unloader(name, handle, isBuiltin, isJNI);
        }

        /*
         * Loads the named native library
         */
        boolean open() {
            if (handle != 0) {
                throw new InternalError("Native library " + name + " has been loaded");
            }

            return load(this, name, isBuiltin, isJNI, throwExceptionIfFail());
        }

        @SuppressWarnings("removal")
        private boolean throwExceptionIfFail() {
            if (loadLibraryOnlyIfPresent) return true;

            // If the file exists but fails to load, UnsatisfiedLinkException thrown by the VM
            // will include the error message from dlopen to provide diagnostic information
            return AccessController.doPrivileged(new PrivilegedAction<>() {
                public Boolean run() {
                    File file = new File(name);
                    return file.exists();
                }
            });
        }
    }

    /*
     * The run() method will be invoked when this class loader becomes
     * phantom reachable to unload the native library.
     */
    static class Unloader implements Runnable {
        // This represents the context when a native library is unloaded
        // and getFromClass() will return null,
        static final NativeLibraryImpl UNLOADER =
                new NativeLibraryImpl(null, "dummy", false, false);

        final String name;
        final long handle;
        final boolean isBuiltin;
        final boolean isJNI;

        Unloader(String name, long handle, boolean isBuiltin, boolean isJNI) {
            assert !isBuiltin || isJNI : "a builtin native library must be JNI library";
            if (handle == 0) {
                throw new IllegalArgumentException(
                        "Invalid handle for native library " + name);
            }

            this.name = name;
            this.handle = handle;
            this.isBuiltin = isBuiltin;
            this.isJNI = isJNI;
        }

        @Override
        public void run() {
            synchronized (loadedLibraryNames) {
                /* remove the native library name */
                if (!loadedLibraryNames.remove(name)) {
                    throw new IllegalStateException(name + " has already been unloaded");
                }
                nativeLibraryContext.push(UNLOADER);
                try {
                    unload(name, isBuiltin, isJNI, handle);
                } finally {
                    nativeLibraryContext.pop();
                }
            }
        }
    }

    /*
     * Holds system and user library paths derived from the
     * {@code java.library.path} and {@code sun.boot.library.path} system
     * properties. The system properties are eagerly read at bootstrap, then
     * lazily parsed on first use to avoid initialization ordering issues.
     */
    static class LibraryPaths {
        // The paths searched for libraries
        static final String[] SYS_PATHS = ClassLoaderHelper.parsePath(StaticProperty.sunBootLibraryPath());
        static final String[] USER_PATHS = ClassLoaderHelper.parsePath(StaticProperty.javaLibraryPath());
    }

    // All native libraries we've loaded.
    // This also serves as the lock to obtain nativeLibraries
    // and write to nativeLibraryContext.
    private static final Set<String> loadedLibraryNames = new HashSet<>();

    // native libraries being loaded
    private static Deque<NativeLibraryImpl> nativeLibraryContext = new ArrayDeque<>(8);

    // Invoked in the VM to determine the context class in JNI_OnLoad
    // and JNI_OnUnload
    private static Class<?> getFromClass() {
        if (nativeLibraryContext.isEmpty()) { // only default library
            return Object.class;
        }
        return nativeLibraryContext.peek().fromClass;
    }

    // JNI FindClass expects the caller class if invoked from JNI_OnLoad
    // and JNI_OnUnload is NativeLibrary class
    private static native boolean load(NativeLibraryImpl impl, String name,
                                       boolean isBuiltin, boolean isJNI,
                                       boolean throwExceptionIfFail);
    private static native void unload(String name, boolean isBuiltin, boolean isJNI, long handle);
    private static native String findBuiltinLib(String name);
    private static native long findEntry0(NativeLibraryImpl lib, String name);
}
