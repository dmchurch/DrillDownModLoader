package de.dakror.modding;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import de.dakror.modding.loader.IModLoader;
import de.dakror.modding.loader.ModClassLoader;

/**
 * The following description is out-of-date. First, ModLoader isn't itself a ClassLoader, because of
 * chicken-and-egg problems; the actual ClassLoader responsible is an instance of {@link ModClassLoader},
 * which by necessity lives in its own separate package (Java doesn't like it when classes of a given
 * package are loaded by two separate ClassLoaders) and delegates to an instance of ModLoader for the
 * actual "modding" part of the process. Currently, there are two implementations of ModLoader: the
 * {@link ClassPoolModLoader} and the {@link StackedClassPoolModLoader}. I'm probably going to
 * standardize on the former, since it's not quite so heavy on the memory usage, but there's no reason
 * other implementations couldn't be explored, for example to experiment with other bytecode-manipulation
 * libraries than Javassist.
 * <p>
 * The other thing that makes the below explanation out-of-date is that the split-horizon ClassLoader
 * implementation described simply doesn't work. The Java docs claim that the VM can cope with multiple
 * class definitions for the same class as long as each definition was loaded by a different ClassLoader,
 * which is true in a broader sense, but what isn't mentioned is that two different definitions of
 * the same classname can't interact with each other, and they certainly can't inherit from each other.
 * This means that performing class augmentation requires bytecode modification and not just loader
 * tricks; this functionality is currently extracted to the {@link ClassAugmentation} class, which
 * itself is under construction and has a couple different implementations as well; see the comments
 * for that class, whenever I write them.
 * <p>
 * With that caveat, the original class description follows until such time as I write a better explanation:
 * <hr>
 * ModLoader is a {@link ClassLoader} that allows for patching the definitions of classes and resources
 * on-the-fly. On the surface, it's a {@link URLClassLoader} that adds all discovered mod JARs (or
 * class hierarchies) to the classpath; all code thus has access to every package defined in every
 * mod, so that mod JARs can reference code from other JARs. The ModLoader is effectively designed to
 * replace the standard application ClassLoader (the one which implements the classpath specified from
 * the command line/launcher); all application-specific classes will be defined and resolved by the
 * ModLoader. The ModLoader inherits from the application loader's parent, the <i>system</i> classloader
 * (responsible for locating and loading all framework-provided classes).
 * <p>
 * The ModLoader keeps a reference around to the original application ClassLoader for the purposes
 * of fetching resources and class-file bytestreams, but it does not allow the application loader to
 * instantiate Class objects; if it did, code from those classes would use the app loader to resolve
 * class name references, rather than the ModLoader itself.
 * <p>
 * The patching functionality works by selectively replacing class definitions (or resources) with
 * alternative implementations <i>when they are first requested</i>. This limit is forced by the JVM,
 * which (quite reasonably) caches class definitions after first use; the only way around this is to use
 * the Java Agent protocol, which is a significantly heavier-weight undertaking.
 * <p>
 * The simplest form of class-patching involves replacing a stock class with a complete replacement,
 * perhaps after extracting and tweaking the original code. For example, if the
 * <code>de.dakror.quarry.Quarry</code> class were to be replaced by a mod-provided class
 * <code>com.example.quarrymod.AltQuarry</code> that includes all the same functionality, then the
 * ModLoader will, on being asked for the definition of a class called <code>de.dakror.quarry.Quarry</code>,
 * simply return the AltQuarry definition instead. The class-loading hierarchy thus looks as follows:
 * <p>
 * <pre>
 * Loader            Request                  Class definition
 *              |
 * [ModLoader]  | "d.d.q.d.DesktopLauncher" → [d.d.q.d.DesktopLauncher]
 *              |                             (references d.d.q.Quarry)
 *              | "d.d.q.Quarry"            → [c.e.q.AltQuarry]
 *      ↓       |                             (references j.l.String)
 *              | "j.l.String" ↓
 * [sys loader] |     "j.l.String"          → [j.l.String]
 * </pre>
 * <p>
 * This is not the most convenient way to implement modding, however, since it requires that any
 * class to be patched must include all the original code of that class in the mod (and thus also
 * precludes combining different mods that want to patch the same class). More convenient, then,
 * is if the replacement class can inherit from the original class; polymorphism dictates that the
 * replacement will be a proper functional replacement for the original. This is more difficult,
 * however, since now we have to implement a "split-horizon" name resolution scheme; since the
 * replacement must reference the original class in its <code>extends</code> clause, its own
 * resolution of the original name must remain untampered, while all <i>other</i> code must
 * reference the replacement class.
 * <p>
 * This is implemented using one-off {@link ModLoader.SubLoader} instances, where each SubLoader
 * is responsible for loading and defining one and only one class file. Within the context of the
 * SubLoader, references to the original class name are resolved to the original class definition;
 * everywhere else, they resolve to the replacement. Using the same example as above, this time
 * with the <code>AltQuarry</code> class inheriting from <code>Quarry</code>:
 * <p>
 * <pre>
 * Loader               Request                      Class definition
 *                 |
 * [ModLoader]     | "d.d.q.d.DesktopLauncher"    → [d.d.q.d.DesktopLauncher]
 * |               |                                (references d.d.q.Quarry)
 * |               | "d.d.q.Quarry" ↓
 * | [SubLoader 1] |     "c.e.q.AltQuarry"        → [c.e.q.AltQuarry]
 * |               |                                (references d.d.q.Quarry)
 * |      ↓        | "d.d.q.Quarry"               → [d.d.q.Quarry]
 * |               |                                (references j.l.String)
 * |               | "j.l.String" ↓
 * [ModLoader]     |     "j.l.String" ↓
 * [sys loader]    |         "j.l.String"         → [j.l.String]
 * </pre>
 */

abstract public class ModLoader implements IModLoader {
    protected ModClassLoader classLoader;
    // private ClassLoader appLoader;
    // private URL[] cpUrls;
    protected URL[] modUrls;
    // private Map<String, byte[]> classes = new HashMap<>();
    // private Map<String, Class<?>> definedClasses = new HashMap<>();
    // private Map<String, byte[]> resources = new HashMap<>();
    // private Map<String, ProtectionDomain> packageDomains = new HashMap<>();
    protected List<IBaseMod> mods = new ArrayList<>();
    protected List<IClassMod<?,?>> classMods = new ArrayList<>();
    protected List<IResourceMod> resourceMods = new ArrayList<>();

    public static IModLoader newInstance(ModClassLoader modClassLoader, String[] args) {
        var modLoader = new ClassPoolModLoader();
        return modLoader;
    }

    public ModLoader init(ModClassLoader modClassLoader, URL[] modUrls, ClassLoader appLoader, String[] args) {
        this.classLoader = modClassLoader;
        // this.cpUrls = getURLs();
        // this.appLoader = appLoader;
        this.modUrls = modUrls;

        implInit();
        registerBaseMods();
        return this;
    }

    abstract protected void implInit();

    protected void registerBaseMods() {
        registerMod(new ClassReplacement());
        registerMod(new ClassAugmentation());
    }

    public void replaceClass(String replacedClass, String replacementClass) {
        getMod(IClassReplacement.class).replaceClass(replacedClass, replacementClass);
    }

    public void augmentClass(String augmentedClass, String augmentationClass) {
        getMod(IClassAugmentation.class).augmentClass(augmentedClass, augmentationClass);
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public void registerMod(String modClassname) throws ClassNotFoundException, ClassCastException {
        Class<?> rawClass = classLoader.loadClass(modClassname);
        Class<? extends IBaseMod> modClass = null;
        try {
            modClass = rawClass.asSubclass(IBaseMod.class);
        } catch (ClassCastException e) {}

        var constructorArgs = new Object[][] {
            {this},
            {this},
            {},
        };
        var constructorArgTypes = new Class<?>[][] {
            {getClass()},
            {ModLoader.class},
            {}
        };

        IBaseMod mod = null;
        // by "constructor" we either mean a constructor or a public static newInstance(...) method
        for (int i = 0; i < constructorArgs.length*2; i++) {
            var args = constructorArgs[i/2];
            var argTypes = constructorArgTypes[i/2];
            try {
                if (i % 2 == 0) {
                    // try as a method
                    mod = (IBaseMod)rawClass.getMethod("newInstance", argTypes).invoke(null, args);
                } else if (modClass != null) {
                    // try as a constructor, but only if this is an IBaseMod
                    mod = modClass.getConstructor(argTypes).newInstance(args);
                } else {
                    // not an IBaseMod class, try only with static methods
                    continue;
                }
            } catch (NoSuchMethodException|IllegalAccessException|InstantiationException|NullPointerException e) {
                // this signature doesn't work, on to the next
                continue;
            } catch (InvocationTargetException e) {
                debugln("Constructor signature "+modClassname+(i % 2 == 0 ? ".newInstance" : "")+"("+argTypes+") threw exception: "+e.getTargetException().getMessage());
                continue;
            } catch (ClassCastException e) {
                debugln("Constructor");
            }
            // if we got here it worked
            break;
        }
        if (mod == null) {
            throw new ClassNotFoundException("No valid constructor signatures for "+modClassname);
        }
        registerMod(mod);
    }

    public void registerMod(IBaseMod mod) {
        mods.add(mod);
        if (mod instanceof IResourceMod) {
            resourceMods.add((IResourceMod)mod);
        }
        if (mod instanceof IClassMod<?,?>) {
            registerClassMod((IClassMod<?,?>)mod);
        }
    }

    protected void registerClassMod(IClassMod<?,?> mod) {
        classMods.add(mod);
    }

    public IBaseMod getMod(String className) {
        for (var mod: mods) {
            if (mod.getClass().getName().equals(className)) {
                return mod;
            }
        }
        return null;
    }

    public <T> T getMod(Class<T> modClass) {
        return getMod(modClass, false);
    }

    public <T> T getMod(Class<T> modClass, boolean exact) {
        for (var mod: mods) {
            if (modClass.isInstance(mod) && (mod.getClass() == modClass || !exact)) {
                return modClass.cast(mod);
            }
        }
        return null;
    }

    public boolean resourceHooked(String name) {
        for (var mod: resourceMods) {
            if (mod.hooksResource(name)) {
                return true;
            }
        }
        return false;
    }

    public boolean classHooked(String className) {
        for (var mod: classMods) {
            if (mod.hooksClass(className)) {
                return true;
            }
        }
        return false;
    }

    public InputStream redefineResourceStream(String name, InputStream stream) {
        for (var mod: resourceMods) {
            if (!mod.hooksResource(name)) {
                continue;
            }
            var newStream = mod.redefineResourceStream(name, stream, classLoader);
            if (newStream != null) {
                stream = newStream;
            }
        }
        return stream;
    }

    abstract public byte[] redefineClass(String name, Class<?> origClass) throws ClassNotFoundException;

    public void start(String mainClass, String[] args) throws Exception {
        var patcher = new Patcher(modUrls);
        debugln("patching classes");
        patcher.patchClasses(this);
        // classMods.add(new Patcher(modUrls));
        // classMods.add(new LauncherMod());
        debugln("patching enums");
        Patcher.patchEnums(modUrls);
        debugln("loading main class");
        var cls = classLoader.loadClass(mainClass);
        debugln("calling main()");
        cls.getMethod("main", String[].class).invoke(null, (Object) args);
    }

    ///////////// INTERFACES ////////////

    private static interface IBaseMod {}
    public static interface IClassMod<T, C> extends IBaseMod {
        boolean hooksClass(String className);
        T redefineClass(String className, T classDef, C context) throws ClassNotFoundException;
        default boolean accepts(Class<?> classDefType, Class<?> contextType) {
            for (var method: this.getClass().getMethods()) {
                if (method.getName() != "redefineClass") continue;
                if (classDefType != null) {
                    if (!method.getParameterTypes()[1].isAssignableFrom(classDefType) || !classDefType.isAssignableFrom(method.getReturnType())) {
                        continue;
                    }
                }
                if (contextType != null) {
                    if (!method.getParameterTypes()[2].isAssignableFrom(contextType)) {
                        continue;
                    }
                }
                return true;
            }
            return false;
        }

        @SuppressWarnings("unchecked")
        default <U, D> IClassMod<U, D> asType(Class<U> classDefType, Class<D> contextType) throws ClassCastException {
            if (accepts(classDefType, contextType)) {
                return (IClassMod<U, D>)this;
            }
            throw new ClassCastException("Class mod "+getClass().getName()+" does not accept ("+classDefType.getName()+", "+contextType.getName()+")");
        }
    }
    public static interface IResourceMod extends IBaseMod {
        boolean hooksResource(String resourceName);
        InputStream redefineResourceStream(String resourceName, InputStream stream, ClassLoader loader);
    }

    static interface IClassReplacement {
        void replaceClass(String replacedClass, String replacementClass);
    }

    static interface IClassAugmentation {
        void augmentClass(String augmentedClass, String augmentationClass);
    }

    /////////////// DEBUG ///////////////

    private static int indent = 0;

    static void debugln(String msg) {
        System.out.println(String.format("%s:%"+(indent+1)+"s%s", "ModLoader","",msg));
    }

    private static void enter(String msg) {
        debugln("entering "+msg);
        indent += 2;
    }

    private static void exit(String msg) {
        // debugln("exiting "+msg);
        indent -= 2;
    }


    static class DebugContext implements AutoCloseable {
        private String msg;
        public DebugContext(String msg) {
            enter(msg);
            this.msg = msg;
        }
        @Override
        public void close() {
            exit(msg);
        }
    }

    /*
    private class SubLoader extends ClassLoader {
        private String baseName;
        private String subName;
        private Class<?> baseClass;
        private Class<?> subClass = null;
        private ProtectionDomain pd = null;
        public SubLoader(ClassLoader parent, String baseName, String subName, Class<?> baseClass) {
            super(parent);
            this.baseName = baseName;
            this.subName = subName;
            this.baseClass = baseClass;
        }

        public void setOriginalSubClass(Class<?> subClass) {
            pd = subClass.getProtectionDomain();
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.equals(baseName)) {
                return baseClass;
            } else if (name.equals(subName)) {
                if (subClass == null) {
                    subClass = findClass(name);
                }
                return subClass;
            }
            return super.loadClass(name);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (name.equals(baseName)) {
                return baseClass;
            } else if (name.equals(subName)) {                
                try {
                    byte[] code = classPool.get(name).toBytecode();
                    subClass = defineClass(name, code, 0, code.length, pd);
                    return subClass;
                } catch (IOException|NotFoundException e) {
                    debugln("Could not find class "+name);
                    throw new ClassNotFoundException(e.getMessage());
                } catch (CannotCompileException e) {
                    System.err.println("Could not compile class "+name+": "+e.getMessage());
                }
            }
            return super.findClass(name);
        }
    }

    private SynthLoader synthLoader = new SynthLoader(this);
    private static class SynthLoader extends ClassLoader {
        private Map<String, Class<?>> synthClasses = new HashMap<>();
        public SynthLoader(ClassLoader parent) {
            super(parent);
        }
        public Class<?> defineSynthClass(String name, byte[] code, ProtectionDomain pd) {
            var ret = defineClass(name, code, 0, code.length, pd);
            synthClasses.put(name, ret);
            return ret;
        }
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            var ret = synthClasses.get(name);
            if (ret == null) {
                throw new ClassNotFoundException();
            }
            return ret;
        }
    }
    */

}