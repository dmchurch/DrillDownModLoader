package de.dakror.modding.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import de.dakror.modding.DefaultingHashMap;
import de.dakror.modding.ModLoader;

public class ModScanner implements ModLoader.IBaseMod, Opcodes {
    // constant values from org.objectweb.asm.Symbol, which is not public because...?
    /** The tag value of CONSTANT_Class_info JVMS structures. */
    static final int CONSTANT_CLASS_TAG = 7;
    // /** The tag value of CONSTANT_Fieldref_info JVMS structures. */
    // static final int CONSTANT_FIELDREF_TAG = 9;
    // /** The tag value of CONSTANT_Methodref_info JVMS structures. */
    // static final int CONSTANT_METHODREF_TAG = 10;
    // /** The tag value of CONSTANT_InterfaceMethodref_info JVMS structures. */
    // static final int CONSTANT_INTERFACE_METHODREF_TAG = 11;

    protected Map<String, List<String>> interfacesByClass = new HashMap<>();
    protected Map<String, List<String>> classesByReference = DefaultingHashMap.using(ArrayList::new);
    protected Map<String, List<String>> classesByAnnotation = DefaultingHashMap.using(ArrayList::new);
    protected Map<String, Map<String,MemberInfo>> declaredFields = DefaultingHashMap.using(HashMap::new);
    protected Map<String, Map<String,List<MemberInfo>>> declaredMethods = DefaultingHashMap.using(() -> DefaultingHashMap.using(ArrayList::new));

    @Override
    public void registered(ModLoader modLoader) {
        debugln("starting scan");
        var start = Instant.now();
        for (var url : modLoader.getModUrls()) {
            // debugln("mod url: "+url.toString());
            try {
                var file = new File(url.toURI());
                if (file.isDirectory()) {
                    scanDirectory(file);
                } else if (file.isFile()) {
                    scanJarFile(new JarFile(file));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        var elapsed = ChronoUnit.NANOS.between(start, Instant.now());
        debugln("scan finished, %d ns elapsed (%.3f ms)", elapsed, (double)elapsed/1000000.0);
        // freeze the DefaultingHashMaps

        classesByReference.get(DefaultingHashMap.FREEZE);
        classesByAnnotation.get(DefaultingHashMap.FREEZE);
        declaredFields.get(DefaultingHashMap.FREEZE);
        declaredMethods.get(DefaultingHashMap.FREEZE);
    }

    public String[] getReferencingClasses(String referencedClass) {
        return getIntReferencingClasses(Util.toIntName(referencedClass))
            .stream()
            .map(Util::fromIntName)
            .toArray(String[]::new);
    }
    public String[] getAnnotatedClasses(String annotationClass) {
        return getIntAnnotatedClasses(Util.toIntName(annotationClass))
            .stream()
            .map(Util::fromIntName)
            .toArray(String[]::new);
    }
    public String[] getDeclaredInterfaces(String declaringClass) {
        return getIntDeclaredInterfaces(Util.toIntName(declaringClass))
            .stream()
            .map(Util::fromIntName)
            .toArray(String[]::new);
    }
    public Map<String, MemberInfo> getDeclaredFields(String className) {
        return getIntDeclaredFields(Util.toIntName(className));
    }
    public Map<String, List<MemberInfo>> getDeclaredMethods(String className) {
        return getIntDeclaredMethods(Util.toIntName(className));
    }

    public List<String> getIntReferencingClasses(String referencedIntClass) {
        return classesByReference.getOrDefault(referencedIntClass, List.of());
    }

    public List<String> getIntAnnotatedClasses(String annotationIntClass) {
        return classesByAnnotation.getOrDefault(annotationIntClass, List.of());
    }

    public List<String> getIntDeclaredInterfaces(String declaringIntClass) {
        return interfacesByClass.getOrDefault(declaringIntClass, List.of());
    }

    public Map<String, MemberInfo> getIntDeclaredFields(String classIntName) {
        return declaredFields.get(classIntName);
    }

    public Map<String, List<MemberInfo>> getIntDeclaredMethods(String classIntName) {
        return declaredMethods.get(classIntName);
    }

    protected void scanDirectory(File dirFile) throws Exception {
        File[] files = dirFile.listFiles();
        for (var file: files) {
            if (file.isDirectory()) {
                scanDirectory(file);
            } else if (file.getName().endsWith(".class")) {
                scanInputStream(new FileInputStream(file));
            }
        }
    }

    protected void scanJarFile(JarFile jarFile) throws Exception {
        var entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                scanInputStream(jarFile.getInputStream(entry));
            }
        }
        jarFile.close();
    }

    protected void scanInputStream(InputStream inputStream) throws Exception {
        var cr = new ClassReader(inputStream);
        var myname = cr.getClassName();
        var count = cr. getItemCount();
        var cbuf = new char[cr.getMaxStringLength()];
        for (var i = 2; i < count; i++) {
            int cpOff, ntOff, tag;
            String cname, name;
            cpOff = cr.getItem(i);
            switch (tag = cr.readByte(cpOff - 1)) {
            case CONSTANT_CLASS_TAG:
                cname = cr.readUTF8(cpOff, cbuf);
                if (cname.startsWith("java/")) continue;
                classesByReference.get(cname).add(myname);
                break;
            // case CONSTANT_FIELDREF_TAG:
            // case CONSTANT_METHODREF_TAG:
            // case CONSTANT_INTERFACE_METHODREF_TAG:
            //     cname = cr.readClass(cpOff, cbuf);
            //     if (cname.startsWith("java/")) continue;
            //     ntOff = cr.getItem(cr.readShort(cpOff + 2));
            //     name = cr.readUTF8(ntOff, cbuf);
            //     if (tag != CONSTANT_FIELDREF_TAG) {
            //         name += "()";
            //     }
            //     knownMembers.get(cname).add(name);
            //     break;
            }
        }
        var fields = declaredFields.get(myname);
        var methods = declaredMethods.get(myname);
        cr.accept(new ClassVisitor(ASM9) {
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                interfacesByClass.put(myname, Arrays.asList(interfaces));
            }
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                classesByAnnotation.get(descriptor).add(myname);
                return null;
            };
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                fields.put(name, new MemberInfo(name, descriptor, access));
                return null;
            }
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                methods.get(name).add(new MemberInfo(name, descriptor, access));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        methods.get(DefaultingHashMap.FREEZE);
        inputStream.close();
    }

    public static class MemberInfo {
        public final String name;
        public final String descriptor;
        public final int access;
        public MemberInfo(String name, String descriptor, int access) {
            this.name = name;
            this.descriptor = descriptor;
            this.access = access;
        }

        @Override
        public String toString() {
            return super.toString() + " ["+name+descriptor+"/"+access+"]";
        }
    }
}
