package de.dakror.modding.asm.augmentation;

import static org.objectweb.asm.Opcodes.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.AnnotationVisitor;
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
import de.dakror.modding.MemberInfo.Access;
import de.dakror.modding.Patcher.AugmentationClass;
import de.dakror.modding.asm.ModScanner;
import de.dakror.modding.asm.Util;

class Augment {
    // private static final String AUGMENTATION_AT = Type.getType(AugmentationClass.class).getDescriptor();
    private static final String PREINIT_AT = Type.getType(AugmentationClass.PreInit.class).getDescriptor();
    private static final String MULTIPREINIT_AT = Type.getType(AugmentationClass.MultiPreInit.class).getDescriptor();

    public final String augName;
    public final String superName;
    // private final ClassReader cr;
    // private final ModScanner scanner;
    private final ClassAugmentationImpl.AugmentationChain chain;
    private final Remapper remapper;
    private final Collection<MemberInfo> fields;
    private final Collection<MemberInfo> methods;
    private final Set<String> overlayFields = new HashSet<>();
    private final Map<String, MemberInfo> shadowedMethods = new HashMap<>();
    private final boolean isEnum;

    public Augment(String augName, ModScanner scanner, ClassAugmentationImpl.AugmentationChain chain, Remapper remapper) {
        // this.cr = cr;
        this.remapper = remapper;
        this.augName = augName;
        this.chain = chain;
        this.superName = scanner.getIntDeclaredSuperclass(augName);
        this.fields = scanner.collectDeclaredFields(augName);
        this.methods = scanner.collectDeclaredMethods(augName);
        this.isEnum = this.superName.equals("java/lang/Enum");
    }

    public Augment analyze(Map<String, String> augMethods) {
        analyzeMethods(augMethods);
        return this;
    }
    // first pass: record all nonprivate augmentation methods (and enum constructors) to figure out which is the top of the chain, and so the base knows what needs renaming
    private void analyzeMethods(Map<String, String> augMethods) {
        for (var info: methods) {
            if (info.isClassInitializer()) {
                continue;
            }
            var key = methodKey(info);
            if (!info.isPrivate() || (isEnum && info.isConstructor())) {
                augMethods.put(key, augName);
            }
        }
    }

    // second pass: rename the following:
    //    - all private methods (unless this is the constructor of an enum class, which we're pretending is "protected", basically)
    //    - all nonprivate methods that aren't at the top of the aug chain
    // do NOT rename any constructors or class initializers.
    public void renameMethods(Map<String, String> augMethods, Map<String, String> nameMapping) {
        for (var info: methods) {
            if (info.isClassInitializer()) {
                continue;
            }
            var key = methodKey(info);
            if ((info.isPrivate() && !(isEnum && info.isConstructor())) || !augName.equals(augMethods.get(key))) {
                if (info.isConstructor()) {
                    // give this shadowed initializer a discriminator argument
                    info = info.appendParameter(Type.getObjectType(augName+"-discriminator").getDescriptor());
                } else {
                    // give this shadowed method a new name
                    var mappedName = chain.mappedMemberName(augName, info.name);
                    if (info.isPrivate()) {
                        // private methods get added to nameMapping so that callsites target the right one
                        nameMapping.put(augName + '.' + info.name + info.descriptor, mappedName);
                    }
                    info = info.withName(mappedName);
                }
                shadowedMethods.put(key, info.asPrivate());
            }
        }
    }

    // rename all fields that have appeared already AND any private fields, UNLESS:
    //    - the existing field was private/package
    //    - the new field is protected
    //    - the (post-remap) field type is the same
    //    - all other access flags except ACC_FINAL are identical
    // if all of those hold, the new definition is simply dropped; this allows exposing private fields in the base
    public void renameFields(Map<String, MemberInfo> visitedMembers, Map<String, String> nameMapping) {
        for (var info: fields) {
            var baseField = visitedMembers.get(info.name);
            if (baseField != null && isOverlayField(baseField, info)) {
                overlayFields.add(info.name);
            } else if (info.isPrivate() || baseField != null) {
                var mappedName = chain.mappedMemberName(augName, info.name);
                nameMapping.put(augName + '.' + info.name, mappedName);
            }
        }
    }
    private boolean isOverlayField(MemberInfo baseField, MemberInfo augField) {
        return ((baseField.isPrivate() || baseField.isPackage())
                && augField.isProtected()
                && baseField.descriptor.equals(null))
            || (isEnum && baseField.is(Access.Enum) && augField.is(Access.Enum));
    }

    private String methodKey(String name, String descriptor) {
        return Util.methodKey(name, remapper.mapMethodDesc(descriptor));
    }

    private String methodKey(MemberInfo info) {
        return methodKey(info.name, info.descriptor);
    }

    public Emitter emitter(ClassVisitor classVisitor, Map<String, MemberInfo> visitedMembers) {
        return new Emitter(classVisitor, visitedMembers);
    }

    protected class Emitter extends ClassVisitor {
        private final Map<String, MemberInfo> myVisitedMembers = new HashMap<>();
        private final Map<String, MemberInfo> visitedMembers;

        public Emitter(ClassVisitor classVisitor, Map<String, MemberInfo> visitedMembers) {
            super(ASM9, new ClassRemapper(new MemberFilter(classVisitor), remapper));
            this.visitedMembers = visitedMembers;
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (overlayFields.contains(name)) {
                // skip this definition
                return null;
            }
            return super.visitField(access, name, descriptor, signature, value);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            final var key = methodKey(name, descriptor);
            var info = shadowedMethods.get(key);
            if (info == null) {
                info = new MemberInfo(name, descriptor, access, augName);
            } else {
                // take recorded values
                access = info.access;
                name = info.name;
                descriptor = info.descriptor;
            }
            myVisitedMembers.put(key, info);
            return new MethodRewriter(super.visitMethod(access, name, descriptor, signature, exceptions), name, descriptor, key, visitedMembers);
        }

        @Override
        public void visitEnd() {
            visitedMembers.putAll(myVisitedMembers);
        }
    }

    protected static class MemberFilter extends ClassVisitor {
        final ClassVisitor nextClassVisitor;
        public MemberFilter(ClassVisitor classVisitor) {
            super(ASM9);
            nextClassVisitor = classVisitor;
        }
        // we're only passing along inner-class, field, and method definitions
        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            nextClassVisitor.visitInnerClass(name, outerName, innerName, access);
        }
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return nextClassVisitor.visitField(access, name, descriptor, signature, value);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            return nextClassVisitor.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    protected class MethodRewriter extends InstructionAdapter {
        // things this class needs to do:
        //  - rewrite super.method() calls to base class methods into this.method() calls to the next in the chain
        //  - rewrite BaseClass.method() calls to base static methods into static calls to the next in the chain
        //  - fix super() calls in constructors
        // things this class does NOT need to do:
        //  - rewrite aug class names into base class names
        //  - rewrite private field/method names into their mangled versions (nameMapping will do that)

        private final Map<String, MemberInfo> visitedMembers;
        private boolean hasDiscriminatedInitCall = false;
        private List<MemberInfo> preInitCalls = new ArrayList<>();
        private int minStack = 0;
        private int minLocals = 0;
        private final Method method;
        private final String myMethodKey;

        public MethodRewriter(MethodVisitor methodVisitor, String name, String descriptor, String methodKey, Map<String, MemberInfo> visitedMembers) {
            super(ASM9, methodVisitor);
            this.visitedMembers = visitedMembers;
            this.method = new Method(name, descriptor);
            this.myMethodKey = methodKey;
            this.minLocals = Type.getArgumentsAndReturnSizes(descriptor) >> 2;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor.equals(PREINIT_AT) || descriptor.equals(MULTIPREINIT_AT)) {
                return new AnnotationVisitor(ASM9) {
                    String myName = null;
                    Type myClass = null;

                    @Override
                    public void visit(String name, Object value) {
                        if (value instanceof Type && !((Type)value).getClassName().endsWith("SUPERCLASS")) {
                            myClass = (Type) value;
                        } else if (value instanceof String && !value.equals("")) {
                            myName = (String) value;
                        }
                    }
                    @Override
                    public AnnotationVisitor visitArray(String name) {
                        return this;
                    }

                    @Override
                    public void visitEnd() {
                        super.visitEnd();
                        if (myName != null || myClass != null) {
                            var name = myName == null ? "pre" + augName.replaceAll(".*[$/]", "") : myName;
                            var owner = myClass == null ? augName : myClass.getInternalName();
                            preInitCalls.add(new MemberInfo(name, null, -1, owner));
                        }
                        myName = null;
                        myClass = null;
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
                    invokestatic(preInitMethod.owner, preInitMethod.name, method.getDescriptor(), false);
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
            if (name.equals("<init>")) {
                final var enumCall = isEnum && owner.equals("java/lang/Enum");
                var key = enumCall ? this.myMethodKey : methodKey(name, descriptor);
                var chainMethod = owner.equals(superName) ? visitedMembers.get(key)
                                : owner.equals(augName) ? shadowedMethods.get(key)
                                : null;
                if (chainMethod != null && !chainMethod.descriptor.equals(descriptor)) {
                    if (enumCall) {
                        var argTypes = Type.getArgumentTypes(chainMethod.descriptor);
                        var localIndex = 3;
                        // the first two args are name and ord, the last one is (must be) the discriminator
                        for (var i = 2; i < argTypes.length - 1; i++) {
                            var argType = argTypes[i];
                            super.load(localIndex, argType);
                            localIndex += argType.getSize();
                        }
                        // we're pulling all the arguments into the stack, therefore the stack must be at least that large
                        minStack = Math.max(minStack, Type.getArgumentsAndReturnSizes(chainMethod.descriptor) >> 2);
                    }
                    descriptor = chainMethod.descriptor;
                    owner = chainMethod.owner;
                    super.aconst(null);
                    hasDiscriminatedInitCall = true;
                }
            } else if (owner.equals(superName)) {
                name = remapMethodName(name, descriptor);
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
            maxLocals = Math.max(maxLocals, minLocals);
            super.visitMaxs(maxStack, maxLocals);
        }
    }
}