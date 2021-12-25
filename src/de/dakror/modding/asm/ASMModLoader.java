package de.dakror.modding.asm;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import de.dakror.modding.ModLoader;

public class ASMModLoader extends ModLoader {
    public static boolean checkClasses = false;
    public static boolean traceClasses = false;
    public static boolean dumpClasses = false;
    protected List<IClassMod<ClassReader, ASMModLoader>> readerMods = new ArrayList<>();
    protected List<IClassMod<ClassVisitor, ClassReader>> visitorMods = new LinkedList<>();

    protected static Map<ClassReader, ASMModLoader> readerToLoader = new WeakHashMap<>();

    protected Map<String, byte[]> definedClasses = new HashMap<>();

    @SuppressWarnings("unchecked")
    <T>Class<T> defineClass(String className, byte[] code, Class<T> existingClass) {
        return (Class<T>)defineClass(className, code);
    }
    Class<?> defineClass(String className, byte[] code) {
        definedClasses.put(className, code);
        try {
            return classLoader.loadClass(className);            
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } finally {
            definedClasses.remove(className);
        }
    }

    @Override
    protected void implInit() {
        debugln("init ASMModLoader");
        try {
            // exercise this code path to ensure that all necessary classes are loaded prior to activation
            redefineClass(ASMModLoader.class.getName());
        } catch (Exception e) {}
        // reset the counting so we only track classes that ASMModLoader can touch
        classLoader.time = classLoader.count = 0;
        registerMod(new ModScanner());
        registerMod(new ClassReplacementImpl());
        registerMod(new ClassAugmentationImpl(this));
    }

    public ModScanner getScanner() {
        return getMod(ModScanner.class);
    }

    @Override
    protected void registerClassMod(IClassMod<?,?> mod) {
        var registered = false;
        try {
            registerClassReaderMod(mod.asType(ClassReader.class, ASMModLoader.class));
            debugln("Registered ClassReader mod "+mod);
            registered = true;
        } catch (ClassCastException e) {}
        try {
            registerClassVisitorMod(mod.asType(ClassVisitor.class, ClassReader.class));
            debugln("Registered ClassVisitor mod "+mod);
        } catch (ClassCastException e) {
            if (!registered) {
                throw e;
            }
        }
        super.registerClassMod(mod);
    }

    protected void registerClassReaderMod(IClassMod<ClassReader, ASMModLoader> mod) {
        readerMods.add(mod);
    }

    protected void registerClassVisitorMod(IClassMod<ClassVisitor, ClassReader> mod) {
        visitorMods.add(0, mod);
    }
    
    @Override
    public boolean classHooked(String className) {
        return true;
    }

    @Override
    public byte[] redefineClass(String name) throws ClassNotFoundException {
        byte[] code = definedClasses.remove(name);
        if (code != null) {
            return code;
        }
        ClassReader cr = null;
        IOException ioExc = null;
        try {
            cr = newClassReader(name);
        } catch (IOException e) {
            ioExc = e;
        }
        try {
            cr = applyMods(readerMods, name, cr, this);
        } catch (RuntimeException e) {
            if (ioExc != null) {
                throw new ClassNotFoundException(name, ioExc);
            }
            throw e;
        }
        ClassWriter cw = new ClassWriter(cr, 0);
        ClassVisitor cv = cw;
        final var preWriteWrapper = new ClassVisitor(Opcodes.ASM9, cv) {
            public void addVisitor(Function<ClassVisitor, ClassVisitor> newCv) {
                cv = newCv.apply(cv);
            }
        };
        cv = applyMods(visitorMods, name, preWriteWrapper, cr);
        if (cv == preWriteWrapper) {
            cv = cw; // don't check or trace class
        } else {
            if (checkClasses) {
                preWriteWrapper.addVisitor(v -> new CheckClassAdapter(v, true));
            }
            if (traceClasses) {
                // trace modded classes post-transformation
                preWriteWrapper.addVisitor(v -> new TraceClassVisitor(v, new PrintWriter(tryGetOutputStream(name+"-post.dump"))));
                // trace modded classes pre-transformation
                cv = new TraceClassVisitor(cv, new PrintWriter(tryGetOutputStream(name+"-pre.dump")));
            }
        }
        cr.accept(cv, 0);
        closeOutputStreams(name+"-pre.dump", name+"-post.dump");
        if (cv != cw && dumpClasses) {
            try (var os = new FileOutputStream(name+"-post.class")) {
                os.write(cw.toByteArray());
            } catch (Exception e) {}
        }
        return cw.toByteArray();
    }

    private Map<String, OutputStream> outputStreams = new HashMap<>();
    private OutputStream tryGetOutputStream(String name) {
        try {
            var os = new FileOutputStream(name);
            outputStreams.put(name, os);
            return os;
        } catch (FileNotFoundException e) {
            return System.out;
        }
    }

    private void closeOutputStreams(String... names) {
        for (var name: names) {
            var os = outputStreams.get(name);
            try {
                if (os != null) os.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        outputStreams.clear();
    }

    public ClassReader newClassReader(String name) throws IOException {
        return newIntClassReader(Util.toIntName(name));
    }

    public ClassReader newIntClassReader(String intName) throws IOException {
        try (var inputStream = classLoader.getResourceAsStream(intName+".class")) {
            var reader = new ClassReader(inputStream);
            readerToLoader.put(reader, this);
            return reader;
        }
    }

    public static ASMModLoader forReader(ClassReader reader) {
        return readerToLoader.get(reader);
    }
}
