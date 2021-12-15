package de.dakror.modding;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;

public class Bootstrapper {
    private static final String MAIN_CLASS = "de.dakror.quarry.desktop.DesktopLauncher";
    private static final String LOADER_CLASS = "de.dakror.modding.loader.ModClassLoader";
    private static final String LOADER_IFACE = "de.dakror.modding.loader.IModLoader";

    public static void main(String[] args) throws Exception {
        var bootstrapLoader = makeBootstrapLoader();

        var appLoader = Bootstrapper.class.getClassLoader();
        var modLoaderClass = bootstrapLoader.loadClass(LOADER_CLASS);
        bootstrapLoader.loadClass(LOADER_IFACE);
        // now that anything directly referenced by ModClassLoader has been loaded, we close the bootstrap loader
        // so that it doesn't load anything else. Anything it has already loaded can't be touched by the ModLoader itself,
        // so we try to keep those to a minimum.
        bootstrapLoader.close();
        Object modClassLoader = modLoaderClass.getConstructor(URL[].class, ClassLoader.class).newInstance(findMods(), appLoader);
        var startMethod = modLoaderClass.getMethod("start", String.class, String[].class);
        startMethod.invoke(modClassLoader, MAIN_CLASS, args);
    }

    private static ClassLoader getSystemLoader() {
        var sysLoader = ClassLoader.getSystemClassLoader();
        try {
            while (sysLoader != null && sysLoader.loadClass(MAIN_CLASS) != null) {
                // keep walking up the loader tree until we find one that can't load the app
                sysLoader = sysLoader.getParent();
            }
        } catch (ClassNotFoundException e) {
            // now the sysLoader is actually the platform/bootstrap ClassLoader
        }
        return sysLoader;
    }

    private static URLClassLoader makeBootstrapLoader() {
        var packageClasses = new Class<?>[] {
            Bootstrapper.class,
            javassist.CtClass.class,
            org.scannotation.ClasspathUrlFinder.class,
        };
        List<URL> urls = new ArrayList<>();
        for (var packageClass: packageClasses) {
            var url = packageClass.getProtectionDomain().getCodeSource().getLocation();
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        // the bootstrapLoader should now be able to fetch ModLoader and everything necessary to load it.
        // normally all these classes will be bundled in one single "ModLoader.jar", but for dev purposes
        // it's helpful to check the URLs on all needed packages
        return new URLClassLoader("ModLoader bootstrap", urls.toArray(new URL[0]), getSystemLoader());
    }

    private static URL[] findMods() throws IOException {
        List<URL> mods = new ArrayList<URL>();
        File modDir = new File("./mods");
        if (modDir.isDirectory()) {
            PathMatcher jarMatcher = FileSystems.getDefault().getPathMatcher("glob:**.jar");
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(modDir.toPath())) {
                for (Path entry: dirStream) {
                    if (Files.isDirectory(entry) || (jarMatcher.matches(entry) && Files.isReadable(entry))) {
                        mods.add(entry.normalize().toUri().toURL());
                    }
                }
            }
        }
        mods.add(Path.of("./TestMod/bin/main").normalize().toUri().toURL());
        return mods.toArray(new URL[0]);
    }
}