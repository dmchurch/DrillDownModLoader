package de.dakror.modding.loader;

// there must be NO imports in here outside the java.* namespace

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import java.util.WeakHashMap;

// We can't afford to actually import the agent's boot Interceptor class, in case ModClassLoader was
// loaded in some way other than through ModAgent. So we create our own Interceptor$IClassInterceptor
// and trust the agent to rewrite it into the appropriate inheritance.

// import de.dakror.modding.agent.boot.Interceptor;
interface Interceptor { interface IClassInterceptor {
    Class<?> interceptedFindClass(ClassLoader loader, String name) throws ClassNotFoundException;
} }
public class ModClassLoader extends URLClassLoader implements Interceptor.IClassInterceptor {
    private static final String MODLOADER_CLASS = "de.dakror.modding.ModLoader";
    private static final Map<ClassLoader, ModClassLoader> modClassLoaderHints = new WeakHashMap<>();
    private static final String MY_PACKAGE_PREFIX = ModClassLoader.class.getPackageName() + ".";

    static {
        registerAsParallelCapable();
    }

    private final ClassLoader appLoader;
    private IModLoader modLoader = null;
    private Map<String, ProtectionDomain> packageDomains = new HashMap<>();
    private boolean initialized = false;
    private boolean stubLauncher;
    private String noInterception = null;

    public static ModClassLoader getModClassLoader(ClassLoader hint) {
        for (ClassLoader loader = hint; loader != null; loader = loader.getParent()) {
            if (loader instanceof ModClassLoader) {
                modClassLoaderHints.put(hint, (ModClassLoader) loader);
                return (ModClassLoader)loader;
            } else if (modClassLoaderHints.containsKey(loader)) {
                return modClassLoaderHints.get(loader);
            } else if (loader.getClass().getName().endsWith("StubClassLoader")) {
                try {
                    ClassLoader stubDelegate = (ClassLoader)loader.getClass().getMethod("getDelegate").invoke(loader);
                    if (stubDelegate != null && stubDelegate != hint) {
                        var mcl = getModClassLoader(stubDelegate);
                        if (mcl != null) {
                            modClassLoaderHints.put(stubDelegate, mcl);
                        }
                    }
                } catch (ReflectiveOperationException e) { }
            }
        }
        ClassLoader scl = getSystemClassLoader();
        if (hint != scl) {
            return getModClassLoader(scl);
        }
        return null;
    }
    // convenience methods
    public static ModClassLoader getMyModClassLoader(Class<?> caller) {
        return getModClassLoader(caller.getClassLoader());
    }
    public static ModClassLoader getMyModClassLoader(Object caller) {
        return getModClassLoader(caller.getClass().getClassLoader());
    }

    public ModClassLoader(ClassLoader appLoader) {
        this(appLoader, null, null);
    }
    public ModClassLoader(ClassLoader appLoader, ClassLoader parent) {
        this(appLoader, parent, null);
    }
    public ModClassLoader(ClassLoader appLoader, Instrumentation inst) {
        this(appLoader, appLoader.getParent(), inst);
    }
    public ModClassLoader(ClassLoader appLoader, ClassLoader parent, Instrumentation inst) {
        super(new URL[0], parent != null ? parent : ModClassLoader.class.getClassLoader());

        boolean isSystemClassLoader = false;
        try {
            getSystemClassLoader();
        } catch (IllegalStateException e) {
            isSystemClassLoader = true;
        }
        this.appLoader = appLoader;

        if (isSystemClassLoader || inst != null) {
            stubLauncher = true;
        } else {
            stubLauncher = false;
            init();
        }
    }

    private void init() {
        if (initialized) return;
        initialized = true;
        for (var url: findClassPaths(appLoader)) {
            addURL(url);
        }
        var loaderUrl = ModClassLoader.class.getProtectionDomain().getCodeSource().getLocation();
        if (!List.of(getURLs()).contains(loaderUrl)) {
            // if we had to add ModLoader to the classpath, make sure we're using it here!
            addURL(loaderUrl);
        }
        registerLoaderHintFor(appLoader);
    }

    public void registerLoaderHintFor(ClassLoader loader) {
        modClassLoaderHints.put(loader, this);
    }

    public boolean addModURL(URL modUrl) {
        if (!List.of(getURLs()).contains(modUrl)) {
            addURL(modUrl);
            return true;
        }
        return false;
    }

    public void addModURLs(URL[] modUrls) {
        for (var url: modUrls) {
            addModURL(url);
        }
    }

    public URL[] getModUrls() {
        return modLoader.getModUrls().toArray(URL[]::new);
    }

    public ClassLoader getAppLoader() {
        return appLoader;
    }

    public IModLoader getModLoader() {
        return modLoader;
    }

    // We don't use this, but some debuggers do.
    void appendToClassPathForInstrumentation(String path) {
        try {
            addURL(new File(path).toURI().toURL());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private IModLoader initModLoader(String[] args) throws Exception {
        if (modLoader == null) {
            var modLoaderClass = loadClass(MODLOADER_CLASS);
            var loader = (IModLoader) modLoaderClass.getMethod("newInstance", ModClassLoader.class, String[].class).invoke(null, this, args);
            modLoader = loader.init(this, appLoader, args);
            // on return from init, loader must be ready to answer our queries
            stubLauncher = false;
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

    private ProtectionDomain getProtectionDomain(String name) {
        ProtectionDomain pd = packageDomains.get(getPackageName(name));
        if (pd == null) {
            noInterception = name;
            try {
                pd = recordPD(appLoader.loadClass(name)).getProtectionDomain();
            } catch (ClassNotFoundException e) {}
            noInterception = null;
        }
        return pd;
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

    public Class<?> getClassIfLoaded(String name) {
        var ret = this.findLoadedClass(name);
        if (ret != null) {
            return ret;
        }
        // we can try getting anything from our parent loaders
        try {
            return ModClassLoader.class.getClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {}
        return null;
    }

    public Class<?> interceptedFindClass(ClassLoader loader, String name) throws ClassNotFoundException {
        if (name.startsWith(MY_PACKAGE_PREFIX)) {
            // don't intercept load of our sibling classes
            throw new UnsupportedOperationException("Sibling load");
        } else if (noInterception != null && name.equals(noInterception)) {
            noInterception = null;
            throw new UnsupportedOperationException("no interception for "+name);
        }
        var ret = this.findLoadedClass(name);
        if (ret != null) return ret;
        return findClass(name);
    }

    // findClass is only called the first time we look for a class, and ONLY if the system
    // classloader couldn't find it (so it won't be called for java.* classes, etc).
    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        if (name.startsWith(MY_PACKAGE_PREFIX)) {
            // if we get asked for one of our siblings, delegate to our loader
            return ModClassLoader.class.getClassLoader().loadClass(name);
        }
        if (!initialized) {
            init();
            if (stubLauncher) {
                stubLauncher = false;
                try {
                    return StubFactory.makeStubFor(name, this);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
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
            ProtectionDomain pd = getProtectionDomain(name);
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

    public static void fromStub(Class<?> mainClass, String[] args) {
        try {
            ModClassLoader.getModClassLoader(mainClass.getClassLoader()).start(mainClass.getName(), args);
        } catch (Exception e) { }
    }
    
    // partially copied from org.scannotation.ClasspathUrlFinder#findClassPaths. It's important that this
    // package be self-contained.
    public static URL[] findClassPaths(ClassLoader appLoader)
    {
        if (appLoader != null) {
            if (appLoader instanceof URLClassLoader) {
                // if the app loader is a URLClassLoader, we can get the effective classpath from here
                return ((URLClassLoader)appLoader).getURLs();
            } else {
                try {
                    URL[] urls = null;
                    urls = getUcpURLs(appLoader);
                    if (urls != null) {
                        return urls;
                    }
                } catch (IllegalAccessException|InaccessibleObjectException e) {
                    // This is somewhat expected, it just means our attempts to bypass visibility checks failed. Move on.
                } catch (ReflectiveOperationException e) {
                    // Other exceptions are weirder, and we might want to know about them, but still fall back on parsing classpath.
                    System.err.print("Unexpected while trying to extract classpath from AppClassLoader: ");
                    e.printStackTrace();
                    System.err.println("Using fallback method to obtain classpath.");
                }
            }
        }

        List<URL> list = new ArrayList<URL>();
        String classpath = System.getProperty("java.class.path");
        StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator);
    
        while (tokenizer.hasMoreTokens())
        {
            String path = tokenizer.nextToken();
            File fp = new File(path);
            if (!fp.exists()) {
                System.err.println("Can't find file in java.class.path, skipping: " + fp);
            }
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

    private static URL[] getUcpURLs(ClassLoader appLoader) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<? extends ClassLoader> appLoaderClass = appLoader.getClass();
        Field ucpField = findField(appLoaderClass, "ucp");
        Class<?> ucpClass = ucpField.getType();
        Method getURLsMethod = findMethod(ucpClass, "getURLs");
        try {
            // will probably throw InaccessibleObjectException
            ucpField.setAccessible(true);
            getURLsMethod.setAccessible(true);
        } catch (InaccessibleObjectException ioe) {
            try {
                SetAccessible.setAccessible(ucpField);
                SetAccessible.setAccessible(getURLsMethod);
            } catch (Exception|NoClassDefFoundError ncdfe) {
                throw ncdfe;
            }
        }
        Object ucp = ucpField.get(appLoader);
        return (URL[])getURLsMethod.invoke(ucp);
    }
    private static Field findField(Class<?> klass, String name) throws NoSuchFieldException {
        for (; klass != null; klass = klass.getSuperclass()) {
            try {
                return klass.getDeclaredField(name);
            } catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(name);
    }
    private static Method findMethod(Class<?> klass, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        for (; klass != null; klass = klass.getSuperclass()) {
            try {
                return klass.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) { }
        }
        throw new NoSuchMethodException(name);
    }

    // This is a separate class because we don't want linking errors (of sun.misc.Unsafe)
    // to cause problems for the rest of the code here. That's also why there's no import
    // of sun.misc.Unsafe at the top of the file, just as a programming safeguard.
    private static class SetAccessible {
        private static Object theUnsafe = null;
        private static long overrideOffset = -1;
        private static void setAccessible(AccessibleObject ao) {
            if (theUnsafe == null) {
                System.err.println("fetching theUnsafe");
                try {
                    Field theUnsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafeField.setAccessible(true);
                    theUnsafe = theUnsafeField.get(null);
                    System.err.println("theUnsafe: "+theUnsafe);
                } catch (Exception e) {
                    System.err.print("Exception fetching theUnsafe: ");
                    e.printStackTrace();
                }
            }
            final sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe;
            if (overrideOffset < 0) {
                System.err.print("getting field offset of AccessibleObject#override: ");
                try {
                    Field overrideField = AccessibleObject.class.getDeclaredField("override");
                    overrideOffset = unsafe.objectFieldOffset(overrideField);
                } catch (NoSuchFieldException nsfe) {
                    // Recent Java doesn't give out Fields for reflection classes anymore. Brute-force it with a field we're allowed to tweak.
                    Field testField;
                    try {
                        testField = SetAccessible.class.getDeclaredField("overrideOffset");
                    } catch (NoSuchFieldException e) {
                        throw new RuntimeException(e);
                    }
                    // Testing shows Hotspot JDKs have this at offset 12, but let's be sure.
                    for (long testOffset = 0; testOffset < 32; testOffset += 4) {
                        testField.setAccessible(false);
                        if (unsafe.getBoolean(testField, testOffset)) {
                            continue;
                        }
                        testField.setAccessible(true);
                        if (!unsafe.getBoolean(testField, testOffset) || unsafe.getInt(testField, testOffset) != 1) {
                            continue;
                        }
                        testField.setAccessible(false);
                        overrideOffset = testOffset;
                        break;
                    }
                }
                System.err.println(overrideOffset);
            }

            if (overrideOffset >= 0) {
                unsafe.putBoolean(ao, overrideOffset, true);
            }
        }
    }
}