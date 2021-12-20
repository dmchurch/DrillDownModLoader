package de.dakror.modding.asm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import de.dakror.modding.Debug;
import de.dakror.modding.ModLoader;

public class ASMModLoader extends ModLoader {
    protected List<IClassMod<ClassReader, ASMModLoader>> readerMods = new ArrayList<>();
    protected List<IClassMod<ClassVisitor, ClassReader>> visitorMods = new LinkedList<>();

    protected static Map<ClassReader, ASMModLoader> readerToLoader = new WeakHashMap<>();

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
        cv = applyMods(visitorMods, name, cv, cr);
        cr.accept(cv, 0);
        return cw.toByteArray();
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
