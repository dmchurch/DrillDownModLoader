package de.dakror.modding.asm;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;

import de.dakror.modding.MemberInfo;
import de.dakror.modding.Patcher.AugmentationClass;
import de.dakror.modding.asm.ClassAugmentationImpl.AugmentationChain;

class AugmentationVisitor extends ClassRemapper {
    public static final String AUGMENTATION_AT = Type.getType(AugmentationClass.class).getDescriptor();
    public static final String PREINIT_AT = Type.getType(AugmentationClass.PreInit.class).getDescriptor();
    public static final String MULTIPREINIT_AT = Type.getType(AugmentationClass.MultiPreInit.class).getDescriptor();

    protected final AugmentationChain chain;
    protected final ClassReader reader;
    protected final String[] interfaces;
    protected final Augment[] augments;
    protected final Map<String, MemberInfo> visitedMembers = new HashMap<>();
    protected final Map<String, String> augMembers = new HashMap<>();
    protected final List<String> clinitMethods = new ArrayList<>();
    protected final Map<String, String> nameMapping;
    protected final Remapper remapper;
    protected final ModScanner scanner;

    public AugmentationVisitor(AugmentationChain chain, ClassVisitor classVisitor, ClassReader reader, Map<String, String> remaps, Remapper remapper) throws IOException {
        super(ASM9, classVisitor, remapper);
        this.chain = chain;
        this.reader = reader;
        this.nameMapping = remaps;
        this.remapper = remapper;
        Set<String> interfaces = new HashSet<>(Arrays.asList(reader.getInterfaces()));
        var modLoader = ASMModLoader.forReader(reader);
        scanner = modLoader.getScanner();
        augments = new Augment[chain.augmentations.size()];
        int i = 0;
        for (var augName: chain.augmentations) {
            var augReader = modLoader.newClassReader(augName);
            interfaces.addAll(Arrays.asList(augReader.getInterfaces()));
            augments[i++] = new Augment(augReader).analyze();
        }
        this.interfaces = interfaces.toArray(new String[0]);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        for (var augName: chain.augmentations) {
            version = Math.max(version, scanner.getClassVersion(augName));
        }
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

    protected FieldVisitor emitField(int access, String name, String descriptor, String signature, Object value) {
        return super.visitField(access, name, descriptor, signature, value);
    }

    protected MethodVisitor emitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        visitedMembers.put(name, new MemberInfo(name, descriptor, access, chain.baseIntName));
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var args = Util.methodDescArgs(descriptor);
        var key = name + args;
        if (augMembers.containsKey(key)) {
            access = (access & ~(ACC_PUBLIC | ACC_PROTECTED)) | ACC_PRIVATE;
            // the only changes we make to the base's method def are to rename/add discriminator and make private, bytecode doesn't change
            if (name.equals("<init>")) {
                descriptor = Util.appendMethodParam(descriptor, "L"+chain.baseIntName+"-;");
                final int minLocals = Type.getMethodType(descriptor).getArgumentsAndReturnSizes()>>2;
                visitedMembers.put(key, new MemberInfo(name, descriptor, access, chain.baseIntName));
                return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMaxs(int maxStack, int maxLocals) {
                        maxLocals = Math.max(maxLocals, minLocals);
                        super.visitMaxs(maxStack, maxLocals);
                    }
                };
            } else {
                var isClinit = name.equals("<clinit>");
                name = chain.mappedMemberName(chain.baseIntName, name);
                if (isClinit) {
                    clinitMethods.add(name);
                }
            }
        }
        visitedMembers.put(key, new MemberInfo(name, descriptor, access, chain.baseIntName));
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public void visitEnd() {
        for (var nestMember: chain.extraNestMembers) {
            cv.visitNestMember(nestMember);
        }
        for (var augment: augments) {
            augment.emit();
        }
        super.visitEnd();
    }

    protected class Augment {
        public final String augName;
        public final String superName;
        public final Map<String, String> fieldMap = new HashMap<>();
        public final Map<String, String> methodMap = new HashMap<>();
        // public final Map<String, String> knownMembers = new HashMap<>();
        public final ClassReader cr;
        // public final Augment baseAnalysis;

        public Augment(ClassReader cr) {
            this.cr = cr;
            this.augName = cr.getClassName();
            this.superName = cr.getSuperName();
        }

        public Augment(String augName) {
            this.augName = augName;
            this.cr = null;
            this.superName = null;
        }

        public Augment analyze() {
            for (var info: scanner.getIntDeclaredFields(augName).values()) {
                if ((info.access & ACC_PRIVATE) == 0) {
                    augMembers.put(info.name, augName);
                    // fieldMap.put(info.name, mappedMemberName(augName, info.name));
                }
            }
            for (var overloads: scanner.getIntDeclaredMethods(augName).values()) {
                for (var info: overloads) {
                    if ((info.access & ACC_PRIVATE) == 0) {
                        var desc = remapper.mapMethodDesc(info.descriptor);
                        var args = Util.methodDescArgs(desc);
                        augMembers.put(info.name + args, augName);
                    }
                }
            }

            return this;
        }

        public String methodKey(String name, String descriptor) {
            var desc = remapper.mapMethodDesc(descriptor);
            var args = Util.methodDescArgs(desc);
            return name + args;
        }

        public String methodKey(MemberInfo info) {
            return methodKey(info.name, info.descriptor);
        }

        public int emit() {
            for (var info: scanner.getIntDeclaredFields(augName).values()) {
                if ((info.access & ACC_PRIVATE) != 0 || visitedMembers.containsKey(info.name)) {
                    var mappedName = chain.mappedMemberName(augName, info.name);
                    fieldMap.put(info.name, mappedName);
                    nameMapping.put(augName + '.' + info.name, mappedName);
                }
            }
            for (var overloads: scanner.getIntDeclaredMethods(augName).values()) {
                for (var info: overloads) {
                    if (info.name.equals("<init>")) {
                        continue;
                    }
                    var key = methodKey(info);
                    if ((info.access & ACC_PRIVATE) != 0 || !augName.equals(augMembers.get(key))) {
                        var mappedName = chain.mappedMemberName(augName, info.name);
                        // methodMap.put(desc, chain.mappedMemberName(augName, info.name));
                        nameMapping.put(augName + '.' + info.name + info.descriptor, mappedName);
                        if (info.name.equals("<clinit>")) {
                            clinitMethods.add(mappedName);
                        }
                    }
                }
            }
            Map<String, MemberInfo> newVisitedMembers = new HashMap<>();

            cr.accept(new Emitter(newVisitedMembers), 0);
            visitedMembers.putAll(newVisitedMembers);
            return 0;
        }

        protected class Emitter extends ClassVisitor {
            private final Map<String, MemberInfo> myVisitedMembers;
            public Emitter(Map<String, MemberInfo> myVisitedMembers) {
                super(ASM9);
                this.myVisitedMembers = myVisitedMembers;
            }

            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                name = remapper.mapType(name);
                outerName = remapper.mapType(outerName);
                emitInnerClass(name, outerName, innerName, access);
            }
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                MemberInfo visitedField;
                if ((access & ACC_PRIVATE) != 0) {
                    var mappedName = chain.mappedMemberName(augName, name);
                    nameMapping.put(augName + '.' + name, mappedName);
                    // we don't have to change name itself, we can just emit the field as-is
                } else if ((visitedField = visitedMembers.get(name)) != null) {
                    if (remapper.mapType(descriptor) != remapper.mapType(visitedField.descriptor)) {
                        throw new RuntimeException("Tried to redefine type of field "+augName+"."+name+" from "+remapper.mapType(visitedField.descriptor)+" to "+remapper.mapType(descriptor));
                    }
                    if ((access & ACC_PROTECTED) != 0 && ((access ^ visitedField.access) & ~(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE)) == 0) {
                        // we're exposing an internal field definition; just drop the new definition and let the class remapper do the rest of the work
                        return null;
                    }
                    throw new RuntimeException("Augment overload fields must be protected and otherwise have all the same modifiers, augment field "+augName+"."+name+" changes access from "+visitedField.access+" to "+access);
                }
                return emitField(access, name, descriptor, signature, value);
            }
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                final var key = methodKey(name, descriptor);
                if (/* (access & ACC_PRIVATE) != 0 ||  */!augName.equals(augMembers.get(key))) {
                    if (name.equals("<init>")) {
                        // give this aug initializer a discriminator argument
                        descriptor = Util.appendMethodParam(descriptor, "L"+augName+"-;");
                    } else {
                        // give this aug method a private name
                        name = chain.mappedMemberName(augName, name);
                    }
                    access = (access & ~(ACC_PUBLIC | ACC_PROTECTED)) | ACC_PRIVATE;
                }
                myVisitedMembers.put(key, new MemberInfo(name, descriptor, access, augName));
                return new MethodRewriter(emitMethod(access, name, descriptor, signature, exceptions), name, descriptor);
            }
        }

        protected class MethodRewriter extends InstructionAdapter {
            // things this class needs to do:
            //  - rewrite super.method() calls to base class methods into this.method() calls to the next in the chain
            //  - fix super() calls in constructors
            // things this class does NOT need to do:
            //  - rewrite aug class names into base class names
            //  - rewrite private field/method names into their mangled versions (nameMapping will do that)

            private boolean hasDiscriminatedInitCall = false;
            private List<String> preInitCalls = new ArrayList<>();
            private int minStack = 0;
            private final Method method;

            public MethodRewriter(MethodVisitor methodVisitor, String name, String descriptor) {
                super(ASM9, methodVisitor);
                this.method = new Method(name, descriptor);
            }

            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals(PREINIT_AT) || descriptor.equals(MULTIPREINIT_AT)) {
                    return new AnnotationVisitor(ASM9) {
                        @Override
                        public void visit(String name, Object value) {
                            if (!value.equals("")) {
                                preInitCalls.add((String)value);
                            }
                        }
                        @Override
                        public AnnotationVisitor visitArray(String name) {
                            return this;
                        }
                    };
                }
                return super.visitAnnotation(descriptor, visible);
            }

            @Override
            public void visitCode() {
                super.visitCode();
                if (method.getName().equals("<init>") && preInitCalls.size() > 0) {
                    minStack = Math.max(minStack, Type.getArgumentsAndReturnSizes(method.getDescriptor()) >> 2);
                    for (var preInitMethod: preInitCalls) {
                        int arg = 1;
                        for (var argType: method.getArgumentTypes()) {
                            load(arg++, argType);
                        }
                        invokestatic(augName, preInitMethod, method.getDescriptor(), false);
                    }
                }
            }

            // field accesses SHOULD be able to emit without changes, letting the class/name remapper do the heavy lifting
            // @Override
            // public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            //     super.visitFieldInsn(opcode, owner, name, descriptor);
            // }

            private String remapMethodName(String name, String descriptor) {
                var key = methodKey(name, descriptor);
                var chainMethod = visitedMembers.get(key);
                if (chainMethod != null) {
                    return chainMethod.name;
                }
                return name;
            }

            @Override
            public void invokespecial(String owner, String name, String descriptor, boolean isInterface) {
                if (owner.equals(superName)) {
                    if (name.equals("<init>")) {
                        var chainMethod = visitedMembers.get(methodKey(name, descriptor));
                        if (chainMethod != null) {
                            descriptor = Util.appendMethodParam(descriptor, "L"+chainMethod.owner+"-;");
                            super.aconst(null);
                            hasDiscriminatedInitCall = true;
                        }
                    } else {
                        name = remapMethodName(name, descriptor);
                    }
                }
                super.invokespecial(owner, name, descriptor, isInterface);
            }

            @Override
            public void invokestatic(String owner, String name, String descriptor, boolean isInterface) {
                if (owner.equals(superName)) {
                    name = remapMethodName(name, descriptor);
                }
                super.invokestatic(owner, name, descriptor, isInterface);
            }

            @Override
            public void invokedynamic(String name, String descriptor, Handle bootstrapMethodHandle, Object[] bootstrapMethodArguments) {
                if ((bootstrapMethodHandle.getTag() == H_INVOKESPECIAL) && bootstrapMethodHandle.getOwner().equals(superName)) {
                    name = remapMethodName(name, descriptor);
                }
                super.invokedynamic(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                if (hasDiscriminatedInitCall) {
                    maxStack++;
                }
                maxStack = Math.max(maxStack, minStack);
                super.visitMaxs(maxStack, maxLocals);
            }
        }
    }
}