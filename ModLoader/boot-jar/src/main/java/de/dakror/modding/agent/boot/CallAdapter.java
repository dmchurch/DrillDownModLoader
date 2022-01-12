package de.dakror.modding.agent.boot;

import java.io.IOException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.Enumeration;

import jdk.internal.loader.BuiltinClassLoader;
import jdk.internal.loader.URLClassPath;

public class CallAdapter {
    @FunctionalInterface
    static interface LoaderMethod<R, E extends Throwable> {
        R call(String name) throws E;
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
            // ucp = bcl.ucp;
            ucp = null;
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

    public Class<?> defineClass(String name, byte[] b, ProtectionDomain pd) {
        String source = defineClassSourceLocation(pd);
        return Interceptor.JLA.defineClass(loader, name, b, pd, source);
    }

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

    // from ClassLoader.java
    private static String defineClassSourceLocation(ProtectionDomain pd) {
        CodeSource cs = pd.getCodeSource();
        String source = null;
        if (cs != null && cs.getLocation() != null) {
            source = cs.getLocation().toString();
        }
        return source;
    }    
}