package de.dakror.modding.loader;

// there must be NO imports in here outside the java.* namespace

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

public class ModClassLoader extends URLClassLoader {
    private static final String MODLOADER_CLASS = "de.dakror.modding.ModLoader";

    private ClassLoader appLoader;
    private URL[] modUrls;
    private IModLoader modLoader = null;
    private Map<String, ProtectionDomain> packageDomains = new HashMap<>();

    public ModClassLoader(URL[] modUrls, ClassLoader appLoader) {
        super(findClassPaths(appLoader), ModClassLoader.class.getClassLoader());

        // this.cpUrls = getURLs();
        this.appLoader = appLoader;
        this.modUrls = modUrls;
        for (var url: modUrls) {
            addURL(url);
        }
    }

    public URL[] getModUrls() {
        return modUrls;
    }

    public ClassLoader getAppLoader() {
        return appLoader;
    }

    public IModLoader getModLoader() {
        return modLoader;
    }

    private IModLoader initModLoader(String[] args) throws Exception {
        if (modLoader == null) {
            var modLoaderClass = loadClass(MODLOADER_CLASS);
            var loader = (IModLoader) modLoaderClass.getMethod("newInstance", ModClassLoader.class, String[].class).invoke(null, this, args);
            modLoader = loader.init(this, modUrls, appLoader, args);
            // on return from init, loader must be ready to answer our queries
        }
        return modLoader;
    }

    private Class<?> recordPD(Class<?> ret) {
        // var ret = super.findClass(name);
        var pkg = ret.getPackageName();
        if (!packageDomains.containsKey(pkg)) {
            packageDomains.put(pkg, ret.getProtectionDomain());
        }
        return ret;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        var stream = super.getResourceAsStream(name);
        if (stream == null) {
            stream = appLoader.getResourceAsStream(name);
            if (stream != null) {
                System.err.println("super.getResourceAsStream failed for resource "+name+" but appLoader succeeded!");
            }
        }
        if (modLoader != null && modLoader.resourceHooked(name)) {
            stream = modLoader.redefineResourceStream(name, stream);
        }
        return stream;
    }

    private String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        return (i == -1) ? "" : className.substring(0, i);
    }

    public long time=0;
    public int count=0;

    // findClass is only called the first time we look for a class, and ONLY if the system
    // classloader couldn't find it (so it won't be called for java.* classes, etc).
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Instant start = Instant.now();
        Class<?> loadedClass = null;
        try {
            if (modLoader == null || !modLoader.classHooked(name)) {
                return recordPD(super.findClass(name));
            }

            byte[] code = modLoader.redefineClass(name);
            if (code == null) {
                return recordPD(super.findClass(name));
            }
            ProtectionDomain pd = packageDomains.get(getPackageName(name));
            if (pd == null) {
                pd = recordPD(appLoader.loadClass(name)).getProtectionDomain();
            }
            return loadedClass = defineClass(name, code, 0, code.length, pd);
        } catch (Exception e) {
            start = null;
            throw e;
        } finally {
            if (start != null) {
                var elapsed = ChronoUnit.NANOS.between(start, Instant.now());
                count++;
                time += elapsed;
                if (modLoader != null) {
                    modLoader.reportLoad(name, loadedClass, elapsed);
                }
            }
            if (name.equals("com.badlogic.gdx.math.Interpolation$BounceIn")) {
                System.out.println(String.format("Loaded %d classes in %d ns (%.3f ms), %.3f ms/class", count, time, (double)time/1000000.0, (double)time/1000000.0/count));
            }
        }
    }

    public void start(String mainClass, String[] args) throws Exception {
        initModLoader(args).start(mainClass, args);
    }
    
    // copied from org.scannotation.ClasspathUrlFinder#findClassPaths. It's important that this
    // class be self-contained.
    public static URL[] findClassPaths(ClassLoader appLoader)
    {
        if (appLoader != null && appLoader instanceof URLClassLoader) {
            // if the app loader is a URLClassLoader, we can get the effective classpath from here
            var ucl = (URLClassLoader)appLoader;
            return ucl.getURLs();
        }
        List<URL> list = new ArrayList<URL>();
        String classpath = System.getProperty("java.class.path");
        StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
    
        while (tokenizer.hasMoreTokens())
        {
            String path = tokenizer.nextToken();
            File fp = new File(path);
            if (!fp.exists()) throw new RuntimeException("File in java.class.path does not exist: " + fp);
            try
            {
                list.add(fp.toURI().toURL());
            }
            catch (MalformedURLException e)
            {
                throw new RuntimeException(e);
            }
        }
        return list.toArray(new URL[list.size()]);
    }
}