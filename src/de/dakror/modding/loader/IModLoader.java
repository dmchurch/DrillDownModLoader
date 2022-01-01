package de.dakror.modding.loader;

import java.io.InputStream;
import java.net.URL;
import java.util.Collection;

public interface IModLoader {
    IModLoader init(ModClassLoader modClassLoader, ClassLoader appLoader, String[] launchArgs);
    void start(String mainClass, String[] args) throws Exception;
    boolean resourceHooked(String name);
    boolean classHooked(String className);
    
    Collection<URL> getModUrls();

    byte[] redefineClass(String name) throws ClassNotFoundException;
    InputStream redefineResourceStream(String resourceName, InputStream origStream);

    default void reportLoad(String name, Class<?> loadedClass, long nsElapsed) {}
}