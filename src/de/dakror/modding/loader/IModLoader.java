package de.dakror.modding.loader;

import java.io.InputStream;
import java.net.URL;

public interface IModLoader {
    IModLoader init(ModClassLoader modClassLoader, URL[] modUrls, ClassLoader appLoader, String[] launchArgs);
    void start(String mainClass, String[] args) throws Exception;
    boolean resourceHooked(String name);
    boolean classHooked(String className);

    byte[] redefineClass(String name) throws ClassNotFoundException;
    InputStream redefineResourceStream(String resourceName, InputStream origStream);

    default void reportLoad(String name, Class<?> loadedClass, long nsElapsed) {}
}