package de.dakror.modding.agent;

import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class ModAgent {
    private final String agentArgs;
    public static final boolean IS_DEBUG = "true".equals(System.getProperty("de.dakror.modding.agent.debug"));
    private final ClassLoader appLoader;
    final Instrumentation inst;
    static TaskLog task = null;
    private static volatile ModAgent agent;
    private static boolean bootJarLoaded = false;

    private AgentTrampoline trampoline;

    public ModAgent(String agentArgs, Instrumentation inst) {
        this.agentArgs = agentArgs;
        this.inst = inst;
        this.appLoader = ClassLoader.getSystemClassLoader();
    }

    private void start() {
        if (!inst.isRetransformClassesSupported()) {
            throw new UnsupportedOperationException("Bad configuration, expecting retransform capability");
        }
        debugln("Initialized, appLoader = " + appLoader + " (modifiable: "+inst.isModifiableClass(appLoader.getClass()) +")");
        ClassLoader myLoader = ModAgent.class.getClassLoader();
        if (task(myLoader != appLoader, "Adding modloader to classpath")) {
            try {
                var myLocation = ModAgent.class.getProtectionDomain().getCodeSource().getLocation();
                inst.appendToSystemClassLoaderSearch(new JarFile(new File(myLocation.toURI()), false));
            } catch (IOException|URISyntaxException e) {
                task.report(e);
            }
            task.report("migrating");
            MethodHandle agentmainHandle;
            try {
                var agentClass = appLoader.loadClass(ModAgent.class.getName());
                agentmainHandle = publicLookup().findStatic(agentClass, "agentmain", methodType(void.class, String.class, Instrumentation.class));
            } catch (ReflectiveOperationException cnfe) {
                throw task.fail(cnfe);
            }
            task.finish();
            MethodHandleProxies.asInterfaceInstance(AgentMainMethod.class, agentmainHandle).agentmain(agentArgs, inst);
            return;
        }
        if (task(!bootJarLoaded, "Loading boot jar")) {
            JarFile bootJarFile = null;
            try (InputStream jarStream = ModAgent.class.getResourceAsStream("/boot-jar.bin")) {
                if (jarStream == null) {
                    throw new RuntimeException("Could not find boot jar");
                }
                File jarPath = File.createTempFile("dd-modloader-boot", ".jar");
                jarPath.deleteOnExit();
                Files.copy(jarStream, jarPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                bootJarFile = new JarFile(jarPath, false);
                task.report("loaded");
            } catch (IOException e) {
                task.report(e);
            }
            if (bootJarFile != null) {
                task.report("registering");
                inst.appendToBootstrapClassLoaderSearch(bootJarFile);
            }
            task.finish();
        }
        trampoline = new AgentTrampoline(appLoader, inst);
        task("Hooking builtin class loader", trampoline::hookClassLoader);
        task("Hooking main class execution", trampoline::hookMainClass);
        agent = this;
    }

    // Gets called after trampoline.hookMainClass
    public static void main(String[] args) throws Throwable {
        var task = agent.task("Loading modloader");
        var mainMethod = agent.trampoline.loadAndStartModPlatform(task, args);
        task.finish();
        agent = null; // don't need to keep this around
        mainMethod.main(args);
    }

    private static void debugln(String message) {
        if (IS_DEBUG) {
            System.err.println(message);
        }
    }

    private static void debug(String message) {
        if (IS_DEBUG) {
            System.err.print(message);
        }
    }

    private boolean task(boolean condition, String message) {
        task = condition ? task(message) : null;
        return condition;
    }

    private TaskLog task(String message) {
        return task = new TaskLog().report(message);
    }
    private void task(String message, Consumer<TaskLog> action) {
        task(message);
        try {
            action.accept(task);
            task.finish();
        } catch (Throwable e) {
            task.fail(e);
        }
    }

    @FunctionalInterface
    public static interface MainMethod {
        void main(String[] args) throws Throwable;
    }

    @FunctionalInterface
    public static interface AgentMainMethod {
        void agentmain(String agentargs, Instrumentation inst);
    }

    public static class TaskLog {
        public boolean thrown = false;
        public TaskLog report(String message) {
            debug(message + "...");
            return this;
        }
        public void report(Throwable e) {
            debug("ERROR: ");
            e.printStackTrace();
            thrown = true;
        }
        public RuntimeException fail(Throwable e) {
            debug("ERROR: ");
            thrown = true;
            throw new RuntimeException(e);
        }
        public void finish() {
            if (!thrown) {
                debugln("done");
            }
        }
    }
    // Java agent static interface
    public static void premain(String agentArgs, Instrumentation inst) throws Throwable {
        new ModAgent(agentArgs, inst).start();
    }
    public static void agentmain(String agentArgs, Instrumentation inst) throws Throwable {
        new ModAgent(agentArgs, inst).start();
    }
}
