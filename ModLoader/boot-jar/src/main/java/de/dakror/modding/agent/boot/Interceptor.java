package de.dakror.modding.agent.boot;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.WeakHashMap;

import jdk.internal.loader.Resource;
import jdk.internal.loader.URLClassPath;

// public static methods in this class all intercept the instance method of the same name in ClassLoader, with
// the same arguments except for an additional ClassLoader at the start. It must be public so the rewritten
// classes can access it, and it must get added to the boot ClassLoader for platform classes to link to it.
public final class Interceptor {
    private static final Map<ClassLoader, IClassInterceptor> loaderInterceptions = new WeakHashMap<>();
    private static final Map<ClassLoader, CallAdapter> loaderAdapters = new WeakHashMap<>();
    static final Map<URLClassPath, ClassLoader> ucpLoaders = new WeakHashMap<>();
    public static final NoInterceptionException NO_INTERCEPTION = new NoInterceptionException(null, null, false, false);
    public static boolean DEBUG_INTERCEPTOR = false;
    public static final Class<?> ucpClass = URLClassPath.class;
    static final ThreadLocal<Boolean> inRecall = ThreadLocal.withInitial(() -> false);
    
    public static final IClassInterceptor NULL_INTERCEPTOR = new NullInterceptor();

    static boolean inRecall() {
        if (inRecall.get()) {
            inRecall.remove();
            return true;
        }
        return false;
    }

    public static CallAdapter interceptTarget(ClassLoader target) {
        var adapter = loaderAdapters.computeIfAbsent(target, CallAdapter::new);
        if (adapter.ucp != null) {
            ucpLoaders.put(adapter.ucp, target);
        }
        return adapter;
    }

    public static IClassInterceptor interceptClasses(ClassLoader target, IClassInterceptor newInterceptor) {
        interceptTarget(target);
        Integer i = 5;
        i = 7;
        switch(i) {
            case 5:
                throw new RuntimeException();
        }
        if (newInterceptor == null) {
            return loaderInterceptions.remove(target);
        } else {
            return loaderInterceptions.put(target, newInterceptor);
        }
    }

    public static class NullInterceptor implements IClassInterceptor {
        @Override public Class<?> interceptedFindClass(CallAdapter source, String name) throws NoInterceptionException, ClassNotFoundException { throw NO_INTERCEPTION; }
        @Override public URL interceptedFindResource(CallAdapter source, String name) throws NoInterceptionException { throw NO_INTERCEPTION; }
        @Override public Enumeration<URL> interceptedFindResources(CallAdapter source, String name) throws NoInterceptionException, IOException { throw NO_INTERCEPTION; }
    };

    public static Class<?> findClass(ClassLoader loader, String name) throws ClassNotFoundException, NoInterceptionException {
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findClass(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindClass(loaderAdapters.get(loader), name);
        } catch (ClassNotFoundException|UnsupportedOperationException cnfe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Class<?> findClassOnClassPathOrNull(ClassLoader loader, String name) throws NoInterceptionException {
        if (inRecall()) throw NO_INTERCEPTION;
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findClassOnClassPathOrNull(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindClass(loaderAdapters.get(loader), name);
        } catch (ClassNotFoundException|UnsupportedOperationException cnfe) {
            throw NO_INTERCEPTION;
        }
    }

    public static URL findResource(ClassLoader loader, String name) throws NoInterceptionException {
        if (inRecall()) throw NO_INTERCEPTION;
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findResource(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindResource(loaderAdapters.get(loader), name);
        } catch (UnsupportedOperationException ioe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Enumeration<URL> findResources(ClassLoader loader, String name) throws IOException, NoInterceptionException {
        if (inRecall()) throw NO_INTERCEPTION;
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findResources(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindResources(loaderAdapters.get(loader), name);
        } catch (UnsupportedOperationException ioe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Resource getResource(URLClassPath ucp, String name, boolean check) throws NoInterceptionException {
        if (inRecall()) throw NO_INTERCEPTION;
        ClassLoader loader = ucpLoaders.getOrDefault(ucp, ClassLoader.getSystemClassLoader());
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        try {
            return UcpResource.ReverseProxy.of(target.interceptedUcpGetResource(loaderAdapters.get(loader), name, check));
        } catch (UnsupportedOperationException ioe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Enumeration<Resource> getResources(URLClassPath ucp, String name, boolean check) throws NoInterceptionException {
        if (inRecall()) throw NO_INTERCEPTION;
        ClassLoader loader = ucpLoaders.getOrDefault(ucp, ClassLoader.getSystemClassLoader());
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        try {
            return UcpResource.asResourceEnumeration(target.interceptedUcpGetResources(loaderAdapters.get(loader), name, check));
        } catch (UnsupportedOperationException|IOException ioe) {
            throw NO_INTERCEPTION;
        }
    }

    private static String mainClassName = null;
    public static String reportMainClass(String cn) {
        var oldCn = mainClassName;
        mainClassName = cn.replace('/', '.');
        return oldCn;
    }

    public static class NoInterceptionException extends Exception {
        public NoInterceptionException() { }
        public NoInterceptionException(String message) { super(message); }
        public NoInterceptionException(Throwable cause) { super(cause); }
        public NoInterceptionException(String message, Throwable cause) { super(message, cause); }
        public NoInterceptionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    public static interface IClassInterceptor {
        Class<?> interceptedFindClass(CallAdapter source, String name) throws ClassNotFoundException, NoInterceptionException, UnsupportedOperationException;
        URL interceptedFindResource(CallAdapter source, String name) throws NoInterceptionException, UnsupportedOperationException;
        Enumeration<URL> interceptedFindResources(CallAdapter source, String name) throws IOException, NoInterceptionException, UnsupportedOperationException;
        default UcpResource interceptedUcpGetResource(CallAdapter source, String name, boolean check) throws NoInterceptionException, UnsupportedOperationException {
            var url = interceptedFindResource(source, name);
            if (url == null) throw NO_INTERCEPTION;
            if (source.ucp != null) {
                return UcpResource.ofResource(name, url, source.ucp.getResource(name, check));
            }
            return UcpResource.of(name, url);
        }
        default Enumeration<UcpResource> interceptedUcpGetResources(CallAdapter source, String name, boolean check) throws IOException, NoInterceptionException, UnsupportedOperationException {
            var urls = interceptedFindResources(source, name);
            if (urls == null) throw NO_INTERCEPTION;
            if (source.ucp != null) {
                return UcpResource.enumerationOfResources(name, urls, source.ucp.getResources(name, check));
            }
            return UcpResource.enumerationOf(name, urls);
        }
    }

    // No instantiation please
    private Interceptor() { }
}
