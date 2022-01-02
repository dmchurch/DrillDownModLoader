package de.dakror.modding.asm;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import de.dakror.modding.DefaultingHashMap;
import de.dakror.modding.IModScanner;
import de.dakror.modding.MemberInfo;
import de.dakror.modding.ModLoader;

public class ModScanner implements IModScanner, Opcodes {
    // constant values from org.objectweb.asm.Symbol, which is not public because...?
    /** The tag value of CONSTANT_Class_info JVMS structures. */
    static final int CONSTANT_CLASS_TAG = 7;

    protected Map<String, List<String>> classesByReference = DefaultingHashMap.using(ArrayList::new);
    protected Map<String, List<String>> classesByAnnotation = DefaultingHashMap.using(ArrayList::new);
    protected Map<String, ClassInfo> scannedClasses = new HashMap<>() {
        public ClassInfo get(Object key) { return super.getOrDefault(key, ClassInfo.EMPTY); }
    };

    @Override
    public void registered(ModLoader modLoader) {
        debugln("starting scan");
        var start = Instant.now();
        scanUrl(ModLoader.class.getProtectionDomain().getCodeSource().getLocation());
        for (var url : modLoader.getModUrls()) {
            scanUrl(url);
        }
        var elapsed = ChronoUnit.NANOS.between(start, Instant.now());
        debugln("scan finished, %d ns elapsed (%.3f ms)", elapsed, (double)elapsed/1000000.0);

        // freeze the DefaultingHashMaps
        classesByAnnotation.get(DefaultingHashMap.FREEZE);
        classesByReference.get(DefaultingHashMap.FREEZE);
    }

    private void scanUrl(URL url) {
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

    // IModScanner external-name access functions

    @Override
    public Function<String, IAnnotation<?>> getClassAnnotations(String className) {
        final var intAnnotations = getIntClassAnnotations(Util.toIntName(className));
        return extName -> intAnnotations.apply(Util.toIntName(extName));
    }

    @Override
    public IAnnotation<?> getClassAnnotation(String className, String annotationClass) {
        return getIntClassAnnotation(Util.toIntName(className), Util.toIntName(annotationClass));
    }

    public int getClassVersion(String className) {
        return getIntClassVersion(Util.toIntName(className));
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
    @Override
    public String getDeclaredSuperclass(String declaringClass) {
        return Util.fromIntName(getIntDeclaredSuperclass(Util.toIntName(declaringClass)));
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

    // Internal-name access functions variants

    public int getIntClassVersion(String classIntName) {
        return scannedClasses.get(classIntName).version;
    }

    public List<String> getIntReferencingClasses(String referencedIntClass) {
        return classesByReference.getOrDefault(referencedIntClass, List.of());
    }

    public List<String> getIntAnnotatedClasses(String annotationIntClass) {
        return classesByAnnotation.getOrDefault(annotationIntClass, List.of());
    }

    public String getIntDeclaredSuperclass(String declaringIntClass) {
        return scannedClasses.get(declaringIntClass).superclass;
    }

    public List<String> getIntDeclaredInterfaces(String declaringIntClass) {
        return scannedClasses.get(declaringIntClass).interfaces;
    }

    public Map<String, MemberInfo> getIntDeclaredFields(String classIntName) {
        return scannedClasses.get(classIntName).fields;
    }

    public Map<String, List<MemberInfo>> getIntDeclaredMethods(String classIntName) {
        return scannedClasses.get(classIntName).methods;
    }

    public Collection<MemberInfo> collectDeclaredFields(String classIntName) {
        return getIntDeclaredFields(classIntName).values();
    }

    public Collection<MemberInfo> collectDeclaredMethods(String classIntName) {
        return getIntDeclaredMethods(classIntName).values()
                                                  .stream()
                                                  .flatMap(List::stream)
                                                  .collect(Collectors.toUnmodifiableList());
    }

    private Function<String, Annotation<?>> getIntClassAnnotations(String classIntName) {
        return scannedClasses.get(classIntName).annotations::get;
    }

    public Annotation<?> getIntClassAnnotation(String classIntName, String annotationIntClass) {
        return getIntClassAnnotations(classIntName).apply(annotationIntClass);
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
            int cpOff;
            String cname;
            cpOff = cr.getItem(i);
            try {
                if (cpOff > 0) {
                    switch (cr.readByte(cpOff - 1)) {
                    case CONSTANT_CLASS_TAG:
                        cname = cr.readUTF8(cpOff, cbuf);
                        if (cname.startsWith("java/")) continue;
                        classesByReference.get(cname).add(myname);
                        break;
                    }
                }
            } catch (Exception e) {
                debugln("Exception while scanning class %s: %s", myname, e);
                break;
            }
        }

        var scanner = new ClassInfoScanner(this);
        try {
            cr.accept(scanner, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            debugln("Exception while scanning class %s: %s", myname, e);
        }
        scannedClasses.put(myname, scanner.getClassInfo());
        inputStream.close();
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> deepCopy(Map<K, V> map, Function<V, V> copyValue) {
        return Map.ofEntries(map.entrySet()
                                .stream()
                                .map(e -> Map.entry(e.getKey(), copyValue.apply(e.getValue())))
                                .toArray(Map.Entry[]::new)
                            );
    }

    private class Annotation<AT> implements IAnnotation<AT> {
        private final String annotationClassName;
        private final Map<String, String> fieldMap;

        public Annotation(String annotationClassName) {
            this(annotationClassName, new HashMap<>());
        }

        public Annotation(String annotationClassName, Map<String, String> fieldMap) {
            this.annotationClassName = annotationClassName;
            this.fieldMap = fieldMap;
        }

        public Annotation(Annotation<?> other) {
            this(other.annotationClassName, Map.copyOf(other.fieldMap));
        }

        @Override
        public String getStringValue(String memberName) {
            String value = fieldMap.get(memberName);
            if (value == null) {
                value = getIntClassAnnotation(annotationClassName, "!defaults").fieldMap.get(memberName);
            }
            return value;
        }

        public String putStringValue(String memberName, String value) {
            return fieldMap.put(memberName, value);
        }

        public Object putValue(String memberName, Object value) {
            if (value instanceof Type) {
                return putStringValue(memberName, ((Type)value).getClassName());
            } else {
                return putStringValue(memberName, value == null ? null : value.toString());
            }
        }
    }

    private static class ClassInfo {
        public static final ClassInfo EMPTY = new ClassInfo(-1, 0, null, null, List.of(), Map.of(), Map.of(), Map.of());

        public final int version;
        public final int access;
        public final String name;
        public final String superclass;
        public final List<String> interfaces;
        public final Map<String, Annotation<?>> annotations;
        public final Map<String, MemberInfo> fields;
        public final Map<String, List<MemberInfo>> methods;

        public ClassInfo(int version, int access, String name, String superclass, String[] interfaces, Function<String, Annotation<?>> newAnnotation) {
            this(version, access, name, superclass, List.of(interfaces),
                /* annotations = */ new DefaultingHashMap<>(newAnnotation),
                /* fields = */ new HashMap<>(),
                /* methods = */ DefaultingHashMap.using(ArrayList::new));
        }
        public ClassInfo(ClassInfo info, Function<Annotation<?>, Annotation<?>> copyAnnotation) {
            this(info.version, info.access, info.name, info.superclass, List.copyOf(info.interfaces),
                deepCopy(info.annotations, copyAnnotation),
                Map.copyOf(info.fields),
                deepCopy(info.methods, List::copyOf));
        }
        private ClassInfo(int version, int access, String name, String superclass, List<String> interfaces,
                Map<String, Annotation<?>> annotations,
                Map<String, MemberInfo> fields,
                Map<String, List<MemberInfo>> methods) {
            this.version = version;
            this.access = access;
            this.name = name;
            this.superclass = superclass;
            this.interfaces = interfaces;
            this.annotations = annotations;
            this.fields = fields;
            this.methods = methods;
        }
    }
    private class ClassInfoScanner extends ClassVisitor {
        private ClassInfo classInfo;
        private boolean isAnnotation = false;
        public ClassInfoScanner(ModScanner modScanner) {
            super(ASM9);
        }
        public ClassInfo getClassInfo() {
            return new ClassInfo(classInfo, Annotation::new);
        }
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            isAnnotation = (access & ACC_ANNOTATION) != 0;
            classInfo = new ClassInfo(version, access, name, superName, interfaces, Annotation::new);
        }
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            ModScanner.this.classesByAnnotation.get(Type.getType(descriptor).getInternalName()).add(classInfo.name);
            if (visible) {
                return new AnnotationScanner(classInfo.annotations.get(Type.getType(descriptor).getInternalName())::putValue);
            }
            return null;
        };
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            classInfo.fields.put(name, new MemberInfo(name, descriptor, access));
            return null;
        }
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            classInfo.methods.get(name).add(new MemberInfo(name, descriptor, access));
            if (isAnnotation) {
                return new MethodVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotationDefault() {
                        return new AnnotationScanner((k, v) -> { classInfo.annotations.get("!defaults").putValue(name, v); });
                    }
                };
            }
            return null;
        }
    }
    private static class AnnotationScanner extends AnnotationVisitor {
        private final BiConsumer<String, Object> valueHandler;

        public AnnotationScanner(BiConsumer<String, Object> valueHandler) {
            super(ASM9);
            this.valueHandler = valueHandler;
        }
        public void visit(String name, Object value) {
            valueHandler.accept(name, value);
        }
    }

}
