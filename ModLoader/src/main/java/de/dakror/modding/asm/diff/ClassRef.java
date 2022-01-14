package de.dakror.modding.asm.diff;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;

import de.dakror.modding.asm.Util;

public class ClassRef {
    public final TreeRef root;
    public final String className;

    ClassRef(TreeRef tree, String className) {
        this.root = tree;
        this.className = className;
    }

    public ClassRef resolve(String className) throws ClassNotFoundException, IOException {
        return root.getClassRef(className);
    }

    public static ClassRef of(String ref) throws ClassNotFoundException, IOException {
        Objects.requireNonNull(ref);
        if (ref.endsWith(".class")) {
            // this is a classfile, either on the filesystem or as a resource
            var cfile = new File(ref);
            if (cfile.exists()) {
                return of(cfile.toPath());
            }
            ref = ref.substring(0, ref.length() - 6 /* .class */);
        }
        if (ref.contains("!")) {
            var jarPath = ref.substring(0, ref.lastIndexOf("!"));
            var className = ref.substring(ref.lastIndexOf("!") + 1);
            return of(jarPath, className);
        }
        // otherwise this is a classname, look for it in the classpath
        var loader = ClassLoader.getSystemClassLoader();
        return of(loader, ref);
    }

    public static ClassRef of(String source, String ref) throws ClassNotFoundException, IOException {
        if (source.equals("-")) {
            return of(ClassLoader.getSystemClassLoader(), ref);
        }
        var jfile = new File(source);
        if (!jfile.exists()) {
            throw new ClassNotFoundException(ref);
        }
        var jar = new JarFile(jfile, false);
        return of(jar, ref);
}

    public static ClassRef of(ClassLoader loader, String className) throws ClassNotFoundException {
        className = Util.toIntName(className);
        var istream = loader.getResourceAsStream(className + ".class");
        if (istream == null) {
            throw new ClassNotFoundException(className);
        }
        return new ClassRef(TreeRef.of(loader), className);
    }

    public static ClassRef of(JarFile jar, String className) throws ClassNotFoundException {
        className = Util.toIntName(className);
        var entry = jar.getEntry(Util.toIntName(className) + ".class");
        if (entry == null) {
            throw new ClassNotFoundException(className);
        }
        return new ClassRef(TreeRef.of(jar), className);
    }

    public static ClassRef of(Path path) throws IOException {
        var code = Files.readAllBytes(path);
        var cr = new ClassReader(code);
        var cname = cr.getClassName();
        var cncomps = cname.split("/");
        if (cncomps.length < path.getNameCount()) {
            var root = path.subpath(0, path.getNameCount() - cncomps.length);
            if (root.resolve(cname + ".class").equals(path)) {
                return new ClassRef(TreeRef.of(root), cname);
            }
        }
        return new ClassRef(TreeRef.of(path, cname), cname);
    }

    public boolean hasHierarchy() {
        return !(root instanceof TreeRef.SinglePath);
    }

    public InputStream getStream() throws IOException {
        try {
            return root.getClassStream(className);
        } catch (ClassNotFoundException e) {
            System.err.print("exception while getting stream for "+this+": ");
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes() throws IOException {
        var code = getStream().readAllBytes();
        if (checksum == null) {
            checksum = checksum(code);
        }
        return code;
    }

    public ClassReader getReader() throws IOException {
        return new ClassReader(getBytes());
    }

    private byte[] checksum = null;
    public byte[] checksum() {
        if (checksum == null) {
            try {
                getReader();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return checksum;
    }
    private static byte[] checksum(byte[] code) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(code);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean identicalTo(ClassRef other) throws IOException {
        if (!MessageDigest.isEqual(checksum(), other.checksum())) {
            return false;
        }
        return Arrays.equals(getBytes(), other.getBytes());
    }

    @Override
    public int hashCode() {
        return Objects.hash(className, root);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ClassRef other = (ClassRef) obj;
        return Objects.equals(className, other.className) && Objects.equals(root, other.root);
    }

    @Override
    public String toString() {
        return "ClassRef [className=" + className + ", root=" + root + "]";
    }
}
