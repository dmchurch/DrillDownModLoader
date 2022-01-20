package de.dakror.modding.agent.boot;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import jdk.internal.loader.Resource;
import jdk.internal.loader.URLClassPath;

// public static methods in this class all intercept the instance method of the same name in ClassLoader, with
// the same arguments except for an additional ClassLoader at the start. It must be public so the rewritten
// classes can access it, and it must get added to the boot ClassLoader for platform classes to link to it.
public abstract class Interceptor {
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

    //////////////////////
    // STATIC INTERFACE //
    //////////////////////
    // The primary job of this class, intercepting ClassLoader/URLClassPath methods on a per-instance basis.
    // As noted above, all public static methods are potential intercept targets here. (And we want these
    // to be as efficient as possible, so this interface will remain static and avoid map-lookups where possible.)

    public static CallAdapter interceptTarget(ClassLoader target) {
        var adapter = loaderAdapters.computeIfAbsent(target, CallAdapter::new);
        if (adapter.ucp != null) {
            ucpLoaders.put(adapter.ucp, target);
        }
        return adapter;
    }

    public static IClassInterceptor interceptClasses(ClassLoader target, IClassInterceptor newInterceptor) {
        interceptTarget(target);
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
        if (inRecall()) throw NO_INTERCEPTION;
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

    ///////////////////////
    // DYNAMIC INTERFACE //
    ///////////////////////
    // Other classes can use these generic static callouts for intercepting calls in core/platform classes;
    // calls will be delegated on a per-CLASS basis to other Interceptor implementations. If multiple
    // Interceptors hook the same class, the order they are called in is undefined and the first to return
    // a value will be used. Interceptors are registered using the of() method, all public instance methods
    // with one or more arguments (of which this class defines none) are potential interceptors, and the
    // caller must retain a reference to the Interceptor for it to remain valid.

    private static final Map<Class<?>, Map<Interceptor, Void>> registeredInterceptors = new ConcurrentHashMap<>();

    protected Map<String, InterceptMethod> interceptMethods = new HashMap<>();

    protected InterceptMethod getMethod(String descriptor) {
        return interceptMethods.get(descriptor);
    }

    @FunctionalInterface
    public static interface InterceptMethod {
        <T> T call(Object target, Object... args) throws NoInterceptionException;
    }

    public static <T extends Interceptor> T of(Class<?> targetClass, T interceptor) {
        return of(targetClass, interceptor, MethodHandles.publicLookup());
    }
    public static <T extends Interceptor> T of(Class<?> targetClass, T interceptor, MethodHandles.Lookup lookup) {
        var interceptorClass = interceptor.getClass();
        for (var method: interceptorClass.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getDeclaringClass() == Object.class || method.getParameterTypes().length < 1) {
                continue;
            }
            try {
                MethodHandle handle = lookup.unreflect(method).bindTo(interceptor);
                MethodType type = handle.type();
                type = type.dropParameterTypes(0, 1); // remove target arg
                handle = handle.asSpreader(Object[].class, method.getParameterTypes().length - 1);
                interceptor.interceptMethods.put(method.getName() + type.toMethodDescriptorString(), MethodHandleProxies.asInterfaceInstance(InterceptMethod.class, handle));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        var interceptors = registeredInterceptors.computeIfAbsent(targetClass, x -> new WeakHashMap<>());
        synchronized (interceptors) {
            interceptors.put(interceptor, null);
            var oldInterceptors = registeredInterceptors.put(targetClass, interceptors); // in case it got removed in a race
            if (oldInterceptors != null && oldInterceptors != interceptors) {
                interceptors.putAll(oldInterceptors);
            }
        }
        return interceptor;
    }

    public static <T> T callInterceptMethodRef(Object target, Class<?> targetClass, String descriptor, Object... args) throws NoInterceptionException {
        return callInterceptMethod(target, targetClass, descriptor, args);
    }
    public static int callInterceptMethodInt(Object target, Class<?> targetClass, String descriptor, Object... args) throws NoInterceptionException {
        return callInterceptMethod(target, targetClass, descriptor, args);
    }
    public static boolean callInterceptMethodBoolean(Object target, Class<?> targetClass, String descriptor, Object... args) throws NoInterceptionException {
        return callInterceptMethod(target, targetClass, descriptor, args);
    }
    public static void callInterceptMethodVoid(Object target, Class<?> targetClass, String descriptor, Object... args) throws NoInterceptionException {
        callInterceptMethod(target, targetClass, descriptor, args);
    }
    protected static <T> T callInterceptMethod(Object target, Class<?> targetClass, String descriptor, Object... args) throws NoInterceptionException {
        var interceptors = registeredInterceptors.get(targetClass);
        if (interceptors == null) {
            throw NO_INTERCEPTION;
        }
        for (Interceptor intercept: interceptors.keySet()) {
            InterceptMethod method = intercept.getMethod(descriptor);
            if (method == null) continue;
            try {
                return method.call(target, args);
            } catch (NoInterceptionException|UnsupportedOperationException e) {
                // let the next Interceptor try, if there is one
            }
        }
        synchronized (interceptors) {
            if (interceptors.isEmpty()) {
                registeredInterceptors.remove(targetClass, interceptors);
            }
        }
        throw NO_INTERCEPTION;
    }
}
