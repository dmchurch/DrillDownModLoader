package de.dakror.modding.agent;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

public class ModAgent {
    // private final String agentArgs;
    private final String MODCLASSLOADER_CLASS = "de.dakror.modding.loader.ModClassLoader";
    private final ClassLoader appLoader;
    final Instrumentation inst;

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
        System.err.println("Initialized, appLoader = " + appLoader + " (modifiable: "+inst.isModifiableClass(appLoader.getClass()) +")");
        if (bootJarFile == null) {
            System.err.println("Loading boot jar...");
            try (InputStream jarStream = ModAgent.class.getResourceAsStream("/boot-jar.jar")) {
                if (jarStream == null) {
                    throw new RuntimeException("Could not find boot jar");
                }
                File jarPath = File.createTempFile("dd-modloader-boot", ".jar");
                jarPath.deleteOnExit();
                Files.copy(jarStream, jarPath.toPath(), StandardCopyOption.REPLACE_EXISTING);
                bootJarFile = new JarFile(jarPath, false);
            } catch (IOException e) {}
            try {
                inst.appendToBootstrapClassLoaderSearch(bootJarFile);
            } catch (Exception e) {
                System.err.println("Loading boot jar failed, aborting");
                throw e;
            }
        }
        ModClassTransformer.hookClassLoader(appLoader, inst);
        ModClassTransformer.loadAndHookModClassLoader(appLoader, inst, MODCLASSLOADER_CLASS);
    }

    // Java agent static interface
    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        new ModAgent(agentArgs, inst).start();
    }
    public static void agentmain(String agentArgs, Instrumentation inst) throws Exception {
        new ModAgent(agentArgs, inst).start();
    }
}
