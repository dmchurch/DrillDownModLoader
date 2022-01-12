package de.dakror.modding.platform;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public interface IModPlatform {
    ClassLoader getClassLoader();
    Class<?> loadClass(String name) throws ClassNotFoundException;
    InputStream getResourceAsStream(String name) throws IOException;
    void resetStats();
    default ClassLoader getAppLoader() {
        return ClassLoader.getSystemClassLoader();
    }
    void callMain(String mainClass, String[] args) throws Throwable;
    boolean addModURL(URL modUrl);
    default void addModURLs(URL[] modUrls) {
        for (var url: modUrls) {
            addModURL(url);
        }
    }
    void start(String mainClass, String[] args) throws Throwable;
}
