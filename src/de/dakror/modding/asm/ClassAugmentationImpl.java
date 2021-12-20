package de.dakror.modding.asm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import de.dakror.modding.ClassAugmentationBase;
import de.dakror.modding.ModLoader;

public class ClassAugmentationImpl extends ClassAugmentationBase<ClassVisitor, ClassReader> implements Opcodes {
    protected ASMModLoader modLoader;
    protected ModScanner modScanner = null;

    public ClassAugmentationImpl(ASMModLoader modLoader) {
        this.modLoader = modLoader;
    }

    protected AugmentationChain newAugmentationChain(String className) {
        return new AugmentationChain(className);
    }

    @Override
    protected ClassVisitor redefineAffectedClass(String className, ClassVisitor classDef, ClassReader context, Map<String, String> renameMap) {
        return new ClassRemapper(classDef, new SimpleRemapper(renameMap));
    }

    protected class AugmentationChain extends ClassAugmentationBase<ClassVisitor, ClassReader>.AugmentationChain {
        protected String baseIntName;
        protected Map<String, String> innerClassRemaps = new HashMap<>();
        protected List<String> extraNestMembers = new ArrayList<>();

        public AugmentationChain(String baseClass) {
            super(baseClass);
            baseIntName = baseName.replace('.', '/');
        }

        @Override
        public boolean addAugmentation(String augmentation, boolean force) {
            if (!super.addAugmentation(augmentation, force)) {
                return false;
            }
            try {
                var scanner = modLoader.getScanner();
                var augIntName = augmentation.replace('.','/');
                innerClassRemaps.put(augIntName, baseIntName);
                recordInnerClasses(augIntName, baseIntName+"$"+mappedClassName(augIntName)+">");
                for (var remappedClass: innerClassRemaps.keySet()) {
                    for (var affectedClass: scanner.getIntReferencingClasses(remappedClass)) {
                        affectedClasses.get(Util.fromIntName(affectedClass)).putAll(innerClassRemaps);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        protected String mappedClassName(String name) {
            return name.replace('/', '\\').replace('$', '>');
        }

        protected String mappedMemberName(String className, String memberName) {
            return mappedClassName(className) + "#" + memberName;
        }

        protected void recordInnerClasses(String outerIntName, String prefix) throws IOException {
            var reader = modLoader.newIntClassReader(outerIntName);
            reader.accept(
                new RecordInnerClassVisitor(outerIntName, prefix),
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        protected class RecordInnerClassVisitor extends ClassVisitor {
            private String outerIntName;
            private String prefix;

            public RecordInnerClassVisitor(String outerIntName, String prefix) {
                super(ASM9);
                this.outerIntName = outerIntName;
                this.prefix = prefix;
            }

            private String mapName(String origName) {
                if (outerIntName.equals(origName)) {
                    return baseIntName;
                } else if (!origName.startsWith(outerIntName+"$")) {
                    return null;
                }
                return prefix + origName.substring(outerIntName.length()+1);
            }

            @Override
            public void visitNestMember(String nestMember) {
                nestMember = mapName(nestMember);
                if (nestMember != null)
                    extraNestMembers.add(nestMember);
            }

            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                var mappedName = mapName(name);
                if (mappedName != null) {
                    innerClassRemaps.put(name, mappedName);
                    modLoader.replaceClass(mappedName.replace('/', '.'), name.replace('/','.')); // load the proper source file
                    augmentationChains.put(mappedName.replace('/', '.'), AugmentationChain.this);
                }
            }
        }

        public ClassVisitor redefineClass(String className, ClassVisitor nextVisitor, ClassReader reader) throws ClassNotFoundException {
            var origIntClassName = reader.getClassName();
            var intClassName = Util.toIntName(className);
            if (intClassName.equals(innerClassRemaps.get(origIntClassName))) {
                return new ClassRemapper(nextVisitor, new SimpleRemapper(innerClassRemaps));
            } else if (!className.equals(baseName)) {
                // the original versions of the classes can't be loaded (this is to guard against unexpected and confusing behavior)
                throw new ClassNotFoundException(className);
            }
            compiled = true; // don't allow any more augmentations after redefinition
            try {
                return new AugmentationVisitor(nextVisitor, reader);
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
        }

        protected class AugmentationVisitor extends ClassVisitor {
            protected ClassReader reader;
            protected String[] interfaces;
            protected Augment[] augments;
            protected Map<String, String> visitedMethods = new HashMap<>();
            // protected Map<String, String> visitedMethods = new HashMap<>();
            // protected Map<String, String> baseMembers = new HashMap<>();
            protected Set<String> visitedFields = new HashSet<>();
            protected Map<String, String> augMethods = new HashMap<>();
            protected Map<String, String> augFields = new HashMap<>();
            protected Map<String, String> nameMapping = new HashMap<>(innerClassRemaps);
            protected Remapper remapper = new SimpleRemapper(nameMapping);
            protected ModScanner scanner;

            public AugmentationVisitor(ClassVisitor classVisitor, ClassReader reader) throws IOException {
                super(ASM9, classVisitor);
                this.reader = reader;
                Set<String> interfaces = new HashSet<>(Arrays.asList(reader.getInterfaces()));
                var modLoader = ASMModLoader.forReader(reader);
                scanner = modLoader.getScanner();
                augments = new Augment[augmentations.size()];
                // var augmentBase = new Augment(reader).analyze();
                int i = 0;
                for (var augName: augmentations) {
                    // interfaces.addAll(scanner.getIntDeclaredInterfaces(Util.toIntName(augName)));
                    var augReader = modLoader.newClassReader(augName);
                    interfaces.addAll(Arrays.asList(augReader.getInterfaces()));
                    // augmentBase = 
                    augments[i++] = new Augment(augReader).analyze(); // , augmentBase).analyze();
                }
                this.interfaces = interfaces.toArray(new String[0]);
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                super.visit(version, access, name, signature, superName, this.interfaces);
            }

            private Map<String, String> visitedInnerClasses = new HashMap<>();
            protected void emitInnerClass(String name, String outerName, String innerName, int access) {
                var oldOuterName = visitedInnerClasses.put(name, outerName);
                if (oldOuterName == null) {
                    super.visitInnerClass(name, outerName, innerName, access);
                } else if (!oldOuterName.equals(outerName)) {
                    throw new RuntimeException("discrepancy: trying to define outer class of "+name+" to "+outerName+" when it was previously "+oldOuterName);
                }
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                visitedFields.add(name);
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var args = descriptor.substring(0, descriptor.indexOf(')')+1);
                var key = name + args;
                if (augMethods.containsKey(key)) {
                    // the only changes we make to the base's bytecode is to rename 
                    name = mappedMemberName(baseIntName, name);
                }
                visitedMethods.put(key, name);
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }

            @Override
            public void visitEnd() {
                for (var nestMember: extraNestMembers) {
                    cv.visitNestMember(nestMember);
                }
                for (var augment: augments) {
                    augment.emit();
                }
                super.visitEnd();
            }

            protected class Augment {
                public final String augName;
                public final Map<String, String> fieldMap = new HashMap<>();
                public final Map<String, String> methodMap = new HashMap<>();
                // public final Map<String, String> knownMembers = new HashMap<>();
                public final ClassReader cr;
                // public final Augment baseAnalysis;

                public Augment(ClassReader cr) {
                //     this(cr, null);
                // }
                // public Augment(ClassReader cr, Augment baseAnalysis) {
                    this.cr = cr;
                    this.augName = cr.getClassName();
                    // knownMethods = scanner.getIntDeclaredMethods(augName);
                    // this.baseAnalysis = baseAnalysis;
                    // if (baseAnalysis != null) {
                    //     knownMembers.putAll(baseAnalysis.knownMembers);
                    // }
                }

                public Augment(String augName) {
                    this.augName = augName;
                    this.cr = null;
                }

                public Augment analyze() {
                    for (var info: scanner.getIntDeclaredFields(augName).values()) {
                        if ((info.access & ACC_PRIVATE) != 0) {
                            augFields.put(info.name, augName);
                            // fieldMap.put(info.name, mappedMemberName(augName, info.name));
                        }
                    }
                    for (var overloads: scanner.getIntDeclaredMethods(augName).values()) {
                        for (var info: overloads) {
                            if ((info.access & ACC_PRIVATE) != 0) {
                                var desc = remapper.mapMethodDesc(info.descriptor);
                                var args = desc.substring(0, desc.indexOf(')')+1);
                                augMethods.put(info.name + args, augName);
                            }
                        }
                    }
                    // cr.accept(new Analyzer(), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                    return this;
                }

                public int emit() {
                    for (var info: scanner.getIntDeclaredFields(augName).values()) {
                        if ((info.access & ACC_PRIVATE) != 0 || visitedFields.contains(info.name)) {
                            var mappedName = mappedMemberName(augName, info.name);
                            fieldMap.put(info.name, mappedName);
                            nameMapping.put(augName + '.' + info.name, mappedName);
                        }
                    }
                    for (var overloads: scanner.getIntDeclaredMethods(augName).values()) {
                        for (var info: overloads) {
                            var desc = remapper.mapMethodDesc(info.descriptor);
                            var args = desc.substring(0, desc.indexOf(')')+1);
                            var key = info.name + args;
                            if ((info.access & ACC_PRIVATE) != 0 || augMethods.get(key) != augName) {
                                var mappedName = mappedMemberName(augName, info.name);
                                methodMap.put(desc, mappedMemberName(augName, info.name));
                                nameMapping.put(augName + '.' + info.name + info.descriptor, mappedName);
                            }
                        }
                    }
                    cr.accept(new Emitter(), 0);
                    return 0;
                }

                protected class Emitter extends ClassVisitor {
                    public Emitter() {
                        super(ASM9);
                    }
    
                    @Override
                    public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        name = remapper.mapType(name);
                        outerName = remapper.mapType(outerName);
                        emitInnerClass(name, outerName, innerName, access);
                    }
                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {

                        if ((access & ACC_PRIVATE) != 0) {
                        }
                        return null;
                    }
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return null;
                    }
                }
            }
        }
    }
}