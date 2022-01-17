package de.dakror.modding.agent.boot;

import static java.lang.invoke.MethodHandles.lookup;

import java.io.IOException;
import java.lang.invoke.*;
import java.net.URL;
// import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.function.*;

import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.URLClassPath;
public class CallAdapter {
    private static final Function<BuiltinClassLoader, URLClassPath> getUCP = privateGetter(BuiltinClassLoader.class, "ucp", URLClassPath.class);

    public static final Object getUCP(ClassLoader loader) {
        return loader instanceof BuiltinClassLoader ? getUCP.apply((BuiltinClassLoader)loader) : null;
    }

    private static <O, M> Function<O, M> privateGetter(Class<O> ownerClass, String memberName, Class<M> memberClass) {
        try {
            var handle = MethodHandles.privateLookupIn(ownerClass, lookup());
            @SuppressWarnings("unchecked")
            Function<O, M> getter = MethodHandleProxies.asInterfaceInstance(Function.class, handle.findGetter(ownerClass, memberName, memberClass));
            return getter;
        } catch (IllegalAccessException | NoSuchFieldException e) {
            e.printStackTrace();
        }
        return x -> null;
    }

    @FunctionalInterface
    static interface LoaderMethod<R, E extends Throwable> {
        R call(String name) throws E;
    }

    @FunctionalInterface
    static interface DefineClassMethod {
        Class<?> defineClass(ClassLoader cl, String name, byte[] b, ProtectionDomain pd, String source);
    }

    public final ClassLoader loader;
    public final URLClassPath ucp;
    private final LoaderMethod<Class<?>, ClassNotFoundException> findClassMethod;
    private final LoaderMethod<URL, RuntimeException> findResourceMethod;
    private final LoaderMethod<Enumeration<URL>, IOException> findResourcesMethod;

    CallAdapter(ClassLoader loader) {
        this.loader = loader;
        if (loader instanceof BuiltinClassLoader) {
            BuiltinClassLoader bcl = (BuiltinClassLoader)loader;
            ucp = getUCP.apply(bcl);
            findResourceMethod = bcl::findResource;
            findResourcesMethod = bcl::findResources;
            findClassMethod = bcl::loadClass;
        } else {
            ucp = null;
            findResourceMethod = loader::getResource;
            findResourcesMethod = loader::getResources;
            findClassMethod = loader::loadClass;
        }
    }

    public Interceptor.IClassInterceptor replaceInterceptor(Interceptor.IClassInterceptor newInterceptor) {
        return Interceptor.interceptClasses(loader, newInterceptor);
    }

    public Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            Interceptor.inRecall.set(true);
            return findClassMethod.call(name);
        } finally {
            Interceptor.inRecall.set(false);
        }
    }

    // public Class<?> defineClass(String name, byte[] b, ProtectionDomain pd) {
    //     String source = defineClassSourceLocation(pd);
    //     return JLA.defineClass(loader, name, b, pd, source);
    // }

    public URL findResource(String name) {
        try {
            Interceptor.inRecall.set(true);
            return findResourceMethod.call(name);
        } finally {
            Interceptor.inRecall.set(false);
        }
    }

    public Enumeration<URL> findResources(String name) throws IOException {
        try {
            Interceptor.inRecall.set(true);
            return findResourcesMethod.call(name);
        } finally {
            Interceptor.inRecall.set(false);
        }
    }

    public UcpResource ucpGetResource(String name, boolean check) throws UnsupportedOperationException {
        if (ucp != null) {
            try {
                Interceptor.inRecall.set(true);
                var resource = ucp.getResource(name, check);
                return resource == null ? null : new UcpResource.Proxy(resource);
            } finally {
                Interceptor.inRecall.set(false);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public Enumeration<UcpResource> ucpGetResources(String name, boolean check) throws UnsupportedOperationException {
        if (ucp != null) {
            try {
                Interceptor.inRecall.set(true);
                var resources = ucp.getResources(name, check);
                return UcpResource.enumerationOfResources(resources);
            } finally {
                Interceptor.inRecall.set(false);
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    // // from ClassLoader.java
    // private static String defineClassSourceLocation(ProtectionDomain pd) {
    //     CodeSource cs = pd.getCodeSource();
    //     String source = null;
    //     if (cs != null && cs.getLocation() != null) {
    //         source = cs.getLocation().toString();
    //     }
    //     return source;
    // }    
}