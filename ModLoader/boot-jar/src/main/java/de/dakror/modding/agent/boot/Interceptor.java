package de.dakror.modding.agent.boot;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Map;
import java.util.WeakHashMap;

// public static methods in this class all intercept the instance method of the same name in ClassLoader, with
// the same arguments except for an additional ClassLoader at the start. It must be public so the rewritten
// classes can access it, and it must get added to the boot ClassLoader for platform classes to link to it.
public final class Interceptor {
    public static final Map<ClassLoader, IClassInterceptor> loaderInterceptions = new WeakHashMap<>();
    public static final NoInterceptionException NO_INTERCEPTION = new NoInterceptionException(null, null, false, false);
    public static boolean DEBUG_INTERCEPTOR = false;

    public static final IClassInterceptor NULL_INTERCEPTOR = new NullInterceptor();
    public static class NullInterceptor implements IClassInterceptor {
        @Override public Class<?> findClass(String name) throws ClassNotFoundException, NoInterceptionException { throw NO_INTERCEPTION; }
        @Override public URL findResource(String name) throws NoInterceptionException { throw NO_INTERCEPTION; }
        @Override public Enumeration<URL> findResources(String name) throws IOException, NoInterceptionException { throw NO_INTERCEPTION; }
    };

    public static Class<?> findClass(ClassLoader loader, String name) throws ClassNotFoundException, NoInterceptionException {
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findClass(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindClass(loader, name);
        } catch (ClassNotFoundException|UnsupportedOperationException cnfe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Class<?> findClassOnClassPathOrNull(ClassLoader loader, String name) throws NoInterceptionException {
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findClassOnClassPathOrNull(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindClass(loader, name);
        } catch (ClassNotFoundException|UnsupportedOperationException cnfe) {
            throw NO_INTERCEPTION;
        }
    }

    public static URL findResource(ClassLoader loader, String name) throws NoInterceptionException {
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findResource(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindResource(loader, name);
        } catch (UnsupportedOperationException ioe) {
            throw NO_INTERCEPTION;
        }
    }

    public static Enumeration<URL> findResources(ClassLoader loader, String name) throws IOException, NoInterceptionException {
        IClassInterceptor target = loaderInterceptions.getOrDefault(loader, NULL_INTERCEPTOR);
        if (DEBUG_INTERCEPTOR) System.err.println(String.format("%s.findResources(%s)", target != NULL_INTERCEPTOR ? target : loader, name));
        try {
            return target.interceptedFindResources(loader, name);
        } catch (UnsupportedOperationException ioe) {
            throw NO_INTERCEPTION;
        }
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
        Class<?> findClass(String name) throws ClassNotFoundException, NoInterceptionException;
        URL findResource(String name) throws NoInterceptionException;
        Enumeration<URL> findResources(String name) throws IOException, NoInterceptionException;

        default Class<?> interceptedFindClass(ClassLoader loader, String name) throws ClassNotFoundException, NoInterceptionException, UnsupportedOperationException {
            return findClass(name);
        }
        default URL interceptedFindResource(ClassLoader loader, String name) throws NoInterceptionException, UnsupportedOperationException {
            return findResource(name);
        }
        default Enumeration<URL> interceptedFindResources(ClassLoader loader, String name) throws IOException, NoInterceptionException, UnsupportedOperationException {
            return findResources(name);
        }
    }

    private Interceptor() { }
}
