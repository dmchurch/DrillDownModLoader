package de.dakror.modding.asm.augmentation;

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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

import de.dakror.modding.MemberInfo;
import de.dakror.modding.asm.ASMModLoader;
import de.dakror.modding.asm.ModScanner;
import de.dakror.modding.asm.Util;

public class AugmentationVisitor extends ClassVisitor implements Opcodes {

    public static final Method CLINIT = Method.getMethod("void <clinit>()");

    protected final ClassAugmentationImpl.AugmentationChain chain;
    protected final ClassReader reader;
    protected final String[] interfaces;
    protected final Augment[] augments;
    protected final Map<String, MemberInfo> visitedMembers = new HashMap<>();
    protected final Map<String, String> augMethods = new HashMap<>();
    protected final List<MethodNode> clinitMethods = new ArrayList<>();
    protected final Map<String, String> nameMapping;
    protected final Remapper remapper;
    protected final ModScanner scanner;
    protected final ASMModLoader modLoader;
    protected final ClinitCollector clinitCollector;

    public static ClassVisitor create(ClassAugmentationImpl.AugmentationChain chain, ClassVisitor nextClassVisitor, ClassReader reader, Map<String, String> remaps, Remapper remapper) throws ClassNotFoundException {
        if ((reader.getAccess() & Opcodes.ACC_ENUM) != 0) {
            return EnumAugmentationVisitor.create(chain, nextClassVisitor, reader, remaps, remapper);
        } else {
            return new AugmentationVisitor(chain, nextClassVisitor, reader, remaps, remapper);
        }
    }

    protected AugmentationVisitor(ClassAugmentationImpl.AugmentationChain chain, ClassVisitor classVisitor, ClassReader reader, Map<String, String> remaps, Remapper remapper) throws ClassNotFoundException {
        super(ASM9);
        cv = new ClassRemapper(clinitCollector = new ClinitCollector(new AugmentationResultChecker(classVisitor)), remapper);
        this.chain = chain;
        this.reader = reader;
        this.nameMapping = remaps;
        this.remapper = remapper;
        Set<String> interfaces = new HashSet<>(Arrays.asList(reader.getInterfaces()));
        modLoader = ASMModLoader.forReader(reader);
        scanner = modLoader.getScanner();
        augments = new Augment[chain.augmentations.size()];
        int i = 0;
        for (var augName: chain.augmentations) {
            var augIntName = Util.toIntName(augName);
            interfaces.addAll(scanner.getIntDeclaredInterfaces(augIntName));
            augments[i++] = new Augment(augIntName, scanner, chain, remapper).analyze(augMethods);
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

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        visitedMembers.put(name, new MemberInfo(name, descriptor, access, chain.baseIntName));
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var key = Util.methodKey(name, descriptor);
        if (augMethods.containsKey(key)) {
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
                name = chain.mappedMemberName(chain.baseIntName, name);
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
        for (final var augment: augments) {
            augment.renameFields(visitedMembers, nameMapping);
            augment.renameMethods(augMethods, nameMapping);
            emitAugmentation(getAugReader(augment), augment);
        }
        emitSynthetics();
        super.visitEnd();
    }

    protected ClassReader getAugReader(Augment augment) {
        try {
            return modLoader.newIntClassReader(augment.augName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected ClassVisitor emitAugmentationTarget() {
        // skip the base-level remapper, since the aug will do its own remapping
        return clinitCollector;
    }

    protected void emitAugmentation(ClassReader augReader, Augment augment) {
        augReader.accept(augment.emitter(emitAugmentationTarget(), visitedMembers), 0);
    }

    protected void emitSynthetics() {
        emitClinit(genMethod(-1, CLINIT, null), 0, 0);
    }

    protected void emitClinit(GeneratorAdapter gen, int maxStack, int maxLocals) {
        for (var node: clinitMethods) {
            if (node.instructions.size() == 0) {
                continue;
            }
            var endLabel = new LabelNode();
            maxStack = Math.max(maxStack, node.maxStack);
            maxLocals = Math.max(maxLocals, node.maxLocals);
            var it = node.instructions.iterator();
            int jumpCount = 0;
            AbstractInsnNode lastJump = null;
            while (it.hasNext()) {
                if (it.next().getOpcode() == RETURN) {
                    it.set(lastJump = new JumpInsnNode(GOTO, endLabel));
                    jumpCount++;
                }
            }
            assert lastJump != null;
            if (node.instructions.getLast() == lastJump) {
                node.instructions.remove(lastJump);
                jumpCount--;
            }
            if (jumpCount > 0) {
                it.add(endLabel);
            }
            node.instructions.accept(gen);
        }
        gen.returnValue();
        gen.visitMaxs(maxStack, maxLocals);
        gen.visitEnd();
    }

    protected class ClinitCollector extends ClassVisitor {
        public ClinitCollector(ClassVisitor classVisitor) {
            super(ASM9, classVisitor);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (name.equals("<clinit>")) {
                    if (access > 0) {
                        var node = new MethodNode(access, name, descriptor, signature, exceptions);
                        clinitMethods.add(node);
                        return node;
                    } else {
                        access = ACC_PUBLIC | ACC_STATIC;
                    }
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
    }

    protected GeneratorAdapter genMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var gen = new GeneratorAdapter(super.visitMethod(access, name, descriptor, signature, exceptions), access, name, descriptor);
        gen.visitCode();
        return gen;
    }

    protected GeneratorAdapter genMethod(int access, Method method, String[] exceptions) {
        return genMethod(access, method.getName(), method.getDescriptor(), null, exceptions);
    }
}