package de.dakror.modding.loader;

import java.io.InputStream;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URL;
import java.util.Collection;

public interface IModLoader {
    IModLoader init(IModPlatform modPlatform, ClassLoader appLoader, String[] launchArgs);
    void start(String mainClass, String[] args) throws Exception;
    boolean resourceHooked(String name);
    boolean classHooked(String className);
    
    Collection<URL> getModUrls();

    byte[] redefineClass(String name) throws ClassNotFoundException;
    default byte[] redefineClass(String name, byte[] code) throws ClassNotFoundException, IllegalClassFormatException {
        return redefineClass(name);
    }
    default byte[] redefineClass(String name, byte[] code, Class<?> existingClass) throws ClassNotFoundException, IllegalClassFormatException {
        if (existingClass == null) {
            return redefineClass(name, code);
        }
        throw new UnsupportedOperationException("Cannot redefine existing classes");
    }
    InputStream redefineResourceStream(String resourceName, InputStream origStream);

    default void reportLoad(String name, Class<?> loadedClass, long nsElapsed) {}
}