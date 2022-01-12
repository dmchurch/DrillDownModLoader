package de.dakror.modding.platform;

import static de.dakror.modding.agent.boot.Interceptor.NO_INTERCEPTION;
import static de.dakror.modding.agent.boot.Interceptor.interceptClasses;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.ProtectionDomain;
import java.util.Enumeration;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.jar.JarFile;

import de.dakror.modding.ModLoader;
import de.dakror.modding.agent.boot.CallAdapter;
import de.dakror.modding.agent.boot.Interceptor.NoInterceptionException;
import de.dakror.modding.agent.boot.Interceptor.NullInterceptor;

public class ModClassInterceptor extends NullInterceptor implements ModPlatformBase, ClassFileTransformer {
    private final ClassLoader appLoader;
    private final Instrumentation inst;
    private final IModLoader modLoader;

    public ModClassInterceptor(ClassLoader appLoader, Instrumentation inst, String mainClass, String[] args) {
        this.appLoader = appLoader;
        this.inst = inst;
        IModLoader modLoader;
        try {
            modLoader = ModLoader.newInstance(this, args).init(this, appLoader, args);
        } catch (ClassNotFoundException cnfe) {
            System.err.println("Exception initializing modloader, mods disabled: "+cnfe);
            modLoader = null;
        }
        this.modLoader = modLoader;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!modLoader.classHooked(className.replace('/','.'))) {
            return null;
        }
        try {
            return modLoader.redefineClass(className.replace('/','.'), classfileBuffer, classBeingRedefined);
        } catch (ClassNotFoundException cnfe) {
            return null;
        }
    }

    @Override
    public Class<?> interceptedFindClass(CallAdapter source, String name) throws ClassNotFoundException, NoInterceptionException {
        if (!modLoader.classHooked(name)) {
            throw NO_INTERCEPTION;
        }
        try {
            // as long as there's any sort of classfile there already we can just use the transform() path
            return source.findClass(name);
        } catch (ClassNotFoundException e) { }
        byte[] code = modLoader.redefineClass(name);
        return source.defineClass(name, code, null);
    }

    @Override
    public URL interceptedFindResource(CallAdapter source, String name) throws NoInterceptionException {
        if (!modLoader.resourceHooked(name)) {
            throw NO_INTERCEPTION;
        }
        final URL origResource = source.findResource(name);
        return new ModStreamHandler(name).of(origResource);
    }

    @Override
    public Enumeration<URL> interceptedFindResources(CallAdapter source, String name) throws IOException, NoInterceptionException {
        if (!modLoader.resourceHooked(name)) {
            throw NO_INTERCEPTION;
        }
        Enumeration<URL> resources = source.findResources(name);
        var handler = new ModStreamHandler(name);
        return new Enumeration<>() {
            @Override
            public boolean hasMoreElements() {
                return resources.hasMoreElements();
            }

            @Override
            public URL nextElement() {
                return handler.of(resources.nextElement());
            }
        };
    }

    private final class ModStreamHandler extends URLStreamHandler {
        private final String name;
        private final Map<URL, URL> origUrls = new WeakHashMap<>();

        private ModStreamHandler(String name) {
            this.name = name;
        }

        public URL of(URL origUrl) {
            try {
                var newUrl = new URL(
                    origUrl.getProtocol(),
                    origUrl.getHost(),
                    origUrl.getPort(),
                    origUrl.getFile(),
                    this);
                origUrls.put(newUrl, origUrl);
                return newUrl;
            } catch (MalformedURLException mue) {
                mue.printStackTrace();
                return origUrl;
            }
        }

        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            
            final URLConnection uc = origUrls.get(u).openConnection();
            return new URLConnection(u) {
                @Override
                public void connect() throws IOException {
                    uc.connect();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    var origIstream = uc.getInputStream();
                    return modLoader.redefineResourceStream(name, origIstream);
                }
            };
        }
    }

    @Override
    public ClassLoader getClassLoader() {
        return appLoader;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return appLoader.loadClass(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) throws IOException {
        return appLoader.getResourceAsStream(name);
    }

    @Override
    public boolean addModURL(URL modUrl) {
        try {
            var jarfile = new JarFile(new File(modUrl.toURI()));
            inst.appendToSystemClassLoaderSearch(jarfile);
            return true;
        } catch (IOException|URISyntaxException e) {
            System.err.print("While loading mod from "+modUrl+": ");
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void resetStats() { }

    @Override
    public IModLoader createModLoader(String[] args) throws Exception {
        interceptClasses(appLoader, this);
        inst.addTransformer(this, true);
        return modLoader;
    }
}
