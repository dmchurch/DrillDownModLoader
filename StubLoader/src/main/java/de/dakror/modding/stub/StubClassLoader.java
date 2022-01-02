package de.dakror.modding.stub;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.PrivilegedAction;
import java.security.SecureClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

public final class StubClassLoader extends SecureClassLoader {
    static {
        registerAsParallelCapable();
    }
    private static final String CLASS_LOADER_NAME = System.getProperty("de.dakror.modding.loader.class", "de.dakror.modding.loader.ModClassLoader");
    private static final String CLASS_LOADER_URL = System.getProperty("de.dakror.modding.loader.url", "ModLoader.jar");

    private final AccessControlContext acc;
    private final ClassLoader appLoader;

    private BootstrapClassLoader bootstrapLoader = null;
    private ClassLoader delegate = null;

    public StubClassLoader(ClassLoader appLoader) {
        super("stub", appLoader.getParent());
        this.appLoader = appLoader;
        acc = AccessController.getContext();
        setDelegate(findDefaultDelegate(appLoader));
    }

    public ClassLoader getDelegate() {
        return delegate;
    }

    synchronized public ClassLoader setDelegate(ClassLoader newDelegate) {
        ClassLoader oldDelegate = delegate;
        delegate = newDelegate == null ? appLoader : newDelegate;
        delegateMethods = new Method[NUM_PROTECTED_METHODS];
        if (bootstrapLoader != null && oldDelegate != null) {
            try {
                bootstrapLoader.close();
            } catch (IOException e) { }
            bootstrapLoader = null;
        }
        return oldDelegate;
    }

    public <T extends ClassLoader> T getDelegate(Class<T> delegateClass) {
        return delegateClass.cast(delegate);
    }

    public ClassLoader getEffectiveClassLoader() {
        return delegate == null ? getParent() : delegate;
    }

    // HOOKABLE METHODS
    // all non-final public/protected (i.e. overridable) ClassLoader methods
    // we use try/catch instead of an if check because delegate should always be
    // set and it's cheaper if no exception is thrown

    @Override public String getName() {
        try {
            return delegate.getName();
        } catch (NullPointerException npe) { }
        return super.getName();
    }
    @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return delegate.loadClass(name);
        } catch (NullPointerException npe) { }
        return super.loadClass(name);
    }
    @Override public URL getResource(String name) {
        try {
            return delegate.getResource(name);
        } catch (NullPointerException npe) { }
        return super.getResource(name);
    }
    @Override public Enumeration<URL> getResources(String name) throws IOException {
        try {
            return delegate.getResources(name);
        } catch (NullPointerException npe) { }
        return super.getResources(name);
    }
    @Override public Stream<URL> resources(String name) {
        try {
            return delegate.resources(name);
        } catch (NullPointerException npe) { }
        return super.resources(name);
    }
    @Override public InputStream getResourceAsStream(String name) {
        try {
            return delegate.getResourceAsStream(name);
        } catch (NullPointerException npe) { }
        return super.getResourceAsStream(name);
    }
    @Override public void setDefaultAssertionStatus(boolean enabled) {
        try {
            delegate.setDefaultAssertionStatus(enabled);
            return;
        } catch (NullPointerException npe) { }
        super.setDefaultAssertionStatus(enabled);
    }
    @Override public void setPackageAssertionStatus(String packageName, boolean enabled) {
        try {
            delegate.setPackageAssertionStatus(packageName, enabled);
            return;
        } catch (NullPointerException npe) { }
        super.setPackageAssertionStatus(packageName, enabled);
    }
    @Override public void setClassAssertionStatus(String className, boolean enabled) {
        try {
            delegate.setClassAssertionStatus(className, enabled);
            return;
        } catch (NullPointerException npe) { }
        super.setClassAssertionStatus(className, enabled);
    }
    @Override public void clearAssertionStatus() {
        try {
            delegate.clearAssertionStatus();
            return;
        } catch (NullPointerException npe) { }
        super.clearAssertionStatus();
    }

    private static final int NUM_PROTECTED_METHODS = 13;
    private static final Method METHOD_INVALID = makeInvalidMethod();

    private Method[] delegateMethods;

    // Protected methods. Ideally we shouldn't have to hook these, but they may be called
    // by reflection, so we do the same.
    private static final int METHOD_loadClass = 0;
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return reflectCall(METHOD_loadClass, Class.class, name, resolve);
        } catch (UnsupportedOperationException e) { }
        return super.loadClass(name, resolve);
    }
    private static final int METHOD_getClassLoadingLock = 1;
    @Override protected Object getClassLoadingLock(String className) {
        try {
            return reflectCall(METHOD_getClassLoadingLock, Object.class, className);
        } catch (UnsupportedOperationException e) { }
        return super.getClassLoadingLock(className);
    }

    private static final int METHOD_findClass1 = 2;
    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            return reflectCall(METHOD_findClass1, Class.class, name);
        } catch (UnsupportedOperationException e) { }
        return super.findClass(name);
    }
    private static final int METHOD_findClass2 = 3;
    @Override protected Class<?> findClass(String moduleName, String name) {
        try {
            return reflectCall(METHOD_findClass2, Class.class, moduleName, name);
        } catch (UnsupportedOperationException e) { }
        return super.findClass(moduleName, name);
    }
    private static final int METHOD_findResource2 = 4;
    @Override protected URL findResource(String moduleName, String name) throws IOException {
        try {
            return reflectCall(METHOD_findResource2, URL.class, moduleName, name);
        } catch (UnsupportedOperationException e) { }
        return super.findResource(moduleName, name);
    }
    private static final int METHOD_findResource1 = 5;
    @Override protected URL findResource(String name) {
        try {
            return reflectCall(METHOD_findResource1, URL.class, name);
        } catch (UnsupportedOperationException e) { }
        return super.findResource(name);
    }
    private static final int METHOD_findResources = 6;
    @SuppressWarnings("unchecked")
    @Override protected Enumeration<URL> findResources(String name) throws IOException {
        try {
            return reflectCall(METHOD_findResources, Enumeration.class, name);
        } catch (UnsupportedOperationException e) { }
        return super.findResources(name);
    }
    private static final int METHOD_definePackage = 7;
    @Override protected Package definePackage(String name, String specTitle, String specVersion, String specVendor, String implTitle, String implVersion, String implVendor, URL sealBase) {
        try {
            return reflectCall(METHOD_definePackage, Package.class, name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
        } catch (UnsupportedOperationException e) { }
        return super.definePackage(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }
    private static final int METHOD_getPackage = 8;
    @SuppressWarnings("deprecation")
    @Override protected Package getPackage(String name) {
        try {
            return reflectCall(METHOD_getPackage, Package.class, name);
        } catch (UnsupportedOperationException e) { }
        return super.getPackage(name);
    }
    private static final int METHOD_findLibrary = 9;
    @Override protected String findLibrary(String libname) {
        try {
            return reflectCall(METHOD_findLibrary, String.class, libname);
        } catch (UnsupportedOperationException e) { }
        return super.findLibrary(libname);
    }

    // from SecureClassLoader
    private static final int METHOD_getPermissions = 10;
    @Override protected PermissionCollection getPermissions(CodeSource codesource) {
        try {
            return reflectCall(METHOD_getPermissions, PermissionCollection.class, codesource);
        } catch (UnsupportedOperationException e) { }
        return super.getPermissions(codesource);
    }

    // from ClassLoaders$AppClassLoader. this is package-private, but apparently debuggers use it anyway. WYGD.
    private static final int METHOD_appendToClassPathForInstrumentation = 12;
    void appendToClassPathForInstrumentation(String path) {
        reflectCall(METHOD_appendToClassPathForInstrumentation, Void.class);
    }

    private static final String[] protectedMethodNames = new String[] {
        "loadClass",
        "getClassLoadingLock",
        "findClass",
        "findClass",
        "findResource",
        "findResource",
        "findResources",
        "definePackage",
        "getPackage",
        "findLibrary",
        "getPermissions",
        "appendToClassPathForInstrumentation",
    };
    private static final Class<?>[][] protectedMethodArgs = new Class[][] {
        /* loadClass */ {String.class, boolean.class},
        /* getClassLoadingLock */ {String.class},
        /* findClass */ {String.class},
        /* findClass */ {String.class, String.class},
        /* findResource */ {String.class, String.class},
        /* findResource */ {String.class},
        /* findResources */ {String.class},
        /* definePackage */ {String.class, String.class, String.class, String.class, String.class, String.class, String.class, URL.class},
        /* getPackage */ {String.class},
        /* findLibrary */ {String.class},
        /* getPermissions */ {CodeSource.class},
        /* appendToClassPathForInstrumentation */ {String.class},
    };

    private <T> T reflectCall(int methodNumber, Class<T> returnClass, Object... args) {
        try {
            return returnClass.cast(delegateMethods[methodNumber].invoke(delegate, args));
        } catch (NullPointerException npe) {
            assert delegateMethods[methodNumber] == null;
            loadDelegateMethod(methodNumber, protectedMethodNames[methodNumber], protectedMethodArgs[methodNumber]);
            return reflectCall(methodNumber, returnClass, args);
        } catch (InvocationTargetException ite) {
            throw (RuntimeException)ite.getTargetException();
        } catch (IllegalAccessException|IllegalArgumentException iae) {
            throw new UnsupportedOperationException(protectedMethodNames[methodNumber]);
        }
    }

    private Method loadDelegateMethod(int methodNumber, String name, Class<?>... parameterTypes) {
        // set this at the top so that it will already be there if any exceptions are thrown
        delegateMethods[methodNumber] = METHOD_INVALID;

        return AccessController.doPrivileged(new PrivilegedAction<>() {
            @Override
            public Method run() {
                Class<?> checkClass = getEffectiveClassLoader().getClass();
                Method method = null;
        
                while (checkClass != null) {
                    try {
                        method = checkClass.getDeclaredMethod(name, parameterTypes);
                        break;
                    } catch (NoSuchMethodException e) {
                        checkClass = checkClass.getSuperclass();
                    }
                }
        
                if (method != null) {
                    method.setAccessible(true);
                    delegateMethods[methodNumber] = method;
                }

                return METHOD_INVALID;
            }
        }, acc);
    }

    private ClassLoader findDefaultDelegate(ClassLoader appLoader) {
        try {
            final URL stubLocation = StubClassLoader.class.getProtectionDomain().getCodeSource().getLocation();
            final ClassLoader platformLoader = getPlatformClassLoader();
            bootstrapLoader = new BootstrapClassLoader(platformLoader);
            Class<? extends ClassLoader> sclClass = null;
            
            // First, try to find a loader in an adjacent file called (by default) ModLoader.jar
            sclClass = bootstrapLoader.tryLoading(stubLocation.toURI().resolve(CLASS_LOADER_URL).toURL());

            // If that doesn't work, try using the builtin app loader to find the class in case a modloader has been bundled inside
            // the main jar or is otherwise on the main classpath (like in development)
            if (sclClass == null) {
                final URL sclUrl;
                try {
                    sclUrl = appLoader.loadClass(CLASS_LOADER_NAME).getProtectionDomain().getCodeSource().getLocation();
                    sclClass = bootstrapLoader.tryLoading(sclUrl);
                } catch (ClassNotFoundException e) {}
            }

            // Lastly, just try loading from the same location as the stub.
            if (sclClass == null) {
                sclClass = bootstrapLoader.tryLoading(stubLocation);
            }

            // if we got a class, instantiate it with the ModClassLoader(ClassLoader appLoader) signature and return it
            if (sclClass != null) {
                return sclClass.getConstructor(ClassLoader.class).newInstance(appLoader);
            }
        } catch (Throwable e) {
            try {
                System.err.print("While finding system class loader: ");
                e.printStackTrace();
                System.err.println("Falling back on default class loader, mod support disabled.");
            } catch (Throwable e2) { }
        }
        return null;
    }

    private static Method makeInvalidMethod() {
        try {
            // when called on an ClassLoader, this will *always* throw an IllegalArgumentException
            return String.class.getMethod("length");
        } catch (NoSuchMethodException e) {
            // not going to happen, but we have to satisfy the compiler that METHOD_INVALID has been set
            throw new RuntimeException(e);
        }
    }

    public static class BootstrapClassLoader extends URLClassLoader {
        static {
            registerAsParallelCapable();
        }
        private final List<String> loadPackages = new ArrayList<>();
        private BootstrapClassLoader(ClassLoader parent) {
            super(new URL[0], parent);
            loadPackages.add(CLASS_LOADER_NAME.substring(0, CLASS_LOADER_NAME.lastIndexOf('.')+1));
        }

        // This allows adding to the list of packages this ClassLoader is willing to load.
        // This should be used with care!
        public void allowPackage(String packageName) {
            loadPackages.add(packageName + ".");
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            for (var loadPackage: loadPackages) {
                if (name.startsWith(loadPackage)) {
                    return super.findClass(name);
                }
            }
            throw new ClassNotFoundException(name);
        }
    
        public Class<? extends ClassLoader> tryLoading(URL tryUrl) {
            try {
                addURL(tryUrl);
                return loadClass(CLASS_LOADER_NAME).asSubclass(ClassLoader.class);
            } catch (Exception e) {
                return null;
            }
        }
    }
}