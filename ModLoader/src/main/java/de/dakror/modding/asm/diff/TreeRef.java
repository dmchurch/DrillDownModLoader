package de.dakror.modding.asm.diff;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

import de.dakror.modding.CacheSet;

abstract class TreeRef {
    private static final CacheSet<TreeRef> trees = new CacheSet<>();

    public ClassRef getClassRef(String className) throws ClassNotFoundException, IOException {
        getClassStream(className);
        return new ClassRef(this, className);
    }
    abstract public InputStream getClassStream(String className) throws ClassNotFoundException, IOException;
    @Override abstract public boolean equals(Object obj);
    @Override abstract public int hashCode();

    public static TreeRef of(Path path) {
        if (Files.isDirectory(path)) {
            return trees.cacheGet(new PathTree(path));
        }
        return null;
    }
    public static TreeRef of(ClassLoader loader) {
        return trees.cacheGet(new LoaderTree(loader));
    }
    public static TreeRef of(JarFile jar) {
        return trees.cacheGet(new JarTree(jar));
    }
    static TreeRef of(Path path, String className) {
        return trees.cacheGet(new SinglePath(className, path));
    }

    public static class PathTree extends TreeRef {
        public final Path root;

        private PathTree(Path root) {
            this.root = root;
        }

        @Override
        public InputStream getClassStream(String className) throws ClassNotFoundException, IOException {
            var cpath = root.resolve(className + ".class");
            if (!Files.exists(cpath)) {
                throw new ClassNotFoundException(className);
            }
            return Files.newInputStream(cpath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            PathTree other = (PathTree) obj;
            return Objects.equals(root, other.root);
        }

        @Override
        public String toString() {
            return "PathTree [root=" + root + "]";
        }
    }
    
    public static class LoaderTree extends TreeRef {
        public final ClassLoader loader;

        private LoaderTree(ClassLoader loader) {
            this.loader = loader;
        }

        @Override
        public InputStream getClassStream(String className) throws ClassNotFoundException, IOException {
            var stream = loader.getResourceAsStream(className + ".class");
            if (stream == null) {
                throw new ClassNotFoundException(className);
            }
            return stream;
        }

        @Override
        public int hashCode() {
            return Objects.hash(loader);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LoaderTree other = (LoaderTree) obj;
            return Objects.equals(loader, other.loader);
        }

        @Override
        public String toString() {
            return "LoaderTree [loader=" + loader + "]";
        }
    }

    public static class JarTree extends TreeRef {
        private final JarFile jar;

        private JarTree(JarFile jar) {
            this.jar = jar;
        }

        @Override
        public InputStream getClassStream(String className) throws ClassNotFoundException, IOException {
            var entry = jar.getJarEntry(className + ".class");
            if (entry == null) {
                throw new ClassNotFoundException(className);
            }
            return jar.getInputStream(entry);
        }

        @Override
        public int hashCode() {
            return Objects.hash(jar);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            JarTree other = (JarTree) obj;
            return Objects.equals(jar, other.jar);
        }

        @Override
        public String toString() {
            return "JarTree [jar=" + jar + " (" + jar.getName() + ")]";
        }
    }

    public static class SinglePath extends TreeRef {
        public final String className;
        public final Path path;
        private SinglePath(String className, Path path) {
            this.className = className;
            this.path = path;
        }

        @Override
        public InputStream getClassStream(String className) throws ClassNotFoundException, IOException {
            if (!className.equals(this.className)) {
                throw new ClassNotFoundException(className);
            }
            return Files.newInputStream(path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(className, path);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SinglePath other = (SinglePath) obj;
            return Objects.equals(className, other.className) && Objects.equals(path, other.path);
        }

        @Override
        public String toString() {
            return "SinglePath [className=" + className + ", path=" + path + "]";
        }
    }
}
