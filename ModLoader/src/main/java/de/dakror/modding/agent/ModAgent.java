package de.dakror.modding.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.jar.JarFile;

public class ModAgent {
    // private final String agentArgs;
    public static final boolean IS_DEBUG = "true".equals(System.getProperty("de.dakror.modding.agent.debug"));
    private final String MODCLASSLOADER_CLASS = "de.dakror.modding.loader.ModClassLoader";
    private final ClassLoader appLoader;
    final Instrumentation inst;
    static TaskLog task = null;

    private JarFile bootJarFile = null;

    public ModAgent(String agentArgs, Instrumentation inst) {
        // this.agentArgs = agentArgs;
        this.inst = inst;
        this.appLoader = ClassLoader.getSystemClassLoader();
    }

    private void start() /* throws Exception */ {
        if (!inst.isRetransformClassesSupported()) {
            throw new UnsupportedOperationException("Bad configuration, expecting retransform capability");
        }
        debugln("Initialized, appLoader = " + appLoader + " (modifiable: "+inst.isModifiableClass(appLoader.getClass()) +")");
        ClassLoader myLoader = ModAgent.class.getClassLoader();
        if (task(myLoader != appLoader, "Adding modloader to classpath")) {
            try {
                if (myLoader instanceof URLClassLoader) {
                    for (var url: ((URLClassLoader)myLoader).getURLs()) {
                        inst.appendToSystemClassLoaderSearch(new JarFile(new File(url.toURI()), false));
                    }
                }
            } catch (IOException|URISyntaxException e) {
                task.report(e);
            }
            task.finish();
        }
        if (task(bootJarFile == null, "Loading boot jar")) {
            try (InputStream jarStream = ModAgent.class.getResourceAsStream("/boot-jar.bin")) {
                if (jarStream == null) {
                    throw new RuntimeException("Could not find boot jar");
                }
                File jarPath = File.createTempFile("dd-modloader-boot", ".jar");
                jarPath.deleteOnExit();
                Files.copy(jarStream, jarPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                bootJarFile = new JarFile(jarPath, false);
            } catch (IOException e) {
                task.report(e);
            }
            if (!task.thrown) {
                inst.appendToBootstrapClassLoaderSearch(bootJarFile);
            }
            task.finish();
        }
        var at = new AgentTrampoline(appLoader, inst);
        task("Hooking builtin class loader", at::hookClassLoader);
        task("Loading modloader", t -> at.loadAndHookModClassLoader(t, MODCLASSLOADER_CLASS));
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
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        new ModAgent(agentArgs, inst).start();
    }
    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        new ModAgent(agentArgs, inst).start();
    }
}
