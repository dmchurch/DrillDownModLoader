package de.dakror.modding.agent;

import java.util.*;
import java.util.function.*;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

import de.dakror.modding.agent.boot.Interceptor;

import static org.objectweb.asm.Opcodes.*;

import java.lang.instrument.*;
import java.security.ProtectionDomain;

public class MainHookTransformer implements ClassFileTransformer {
    private static final Method METHOD_loadMainClass = Method.getMethod("Class loadMainClass(int, String)");

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!className.equals("sun/launcher/LauncherHelper")) {
            return null;
        }
        var cr = new ClassReader(classfileBuffer);
        var cw = new ClassWriter(cr, 0);
        try {
            cr.accept(new ClassVisitor(ASM9, cw) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (name.equals("appClass")) {
                        access = access & ~(ACC_PRIVATE) | ACC_PUBLIC;
                    }
                    return super.visitField(access, name, descriptor, signature, value);
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (name.equals("loadMainClass")) {
                        var method = new Method(name, descriptor);
                        assert method.equals(METHOD_loadMainClass);
                        return new LoadMainClassAdapter(access, name, descriptor, signature, exceptions, cv);
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, 0);
            return cw.toByteArray();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static class LoadMainClassAdapter extends MethodNode {
        private final ClassVisitor cv;
        private ListIterator<AbstractInsnNode> it;

        public LoadMainClassAdapter(int access, String name, String descriptor, String signature, String[] exceptions, ClassVisitor cv) {
            super(ASM9, access, name, descriptor, signature, exceptions);
            this.cv = cv;
        }

        private AbstractInsnNode search(Predicate<? super AbstractInsnNode> criteria) {
            while (it.hasNext()) {
                var insn = it.next();
                if (criteria.test(insn)) {
                    return insn;
                }
            }
            return null;
        }

        @SafeVarargs
        private <T extends AbstractInsnNode> T searchFor(Class<T> insnClass, int opcode, Predicate<? super T>... criteria) {
            var typeCheck = (Predicate<AbstractInsnNode>)insnClass::isInstance;
            if (opcode >= 0) {
                typeCheck = typeCheck.and(i -> i.getOpcode() == opcode);
            }
            @SuppressWarnings("unchecked")
            var allCriteria = Arrays.stream((Predicate<AbstractInsnNode>[])criteria).reduce(typeCheck, Predicate::and);
            return insnClass.cast(search(allCriteria));
        }

        private AbstractInsnNode nextInsn() {
            return search(i -> i.getOpcode() >= 0);
        }

        @SafeVarargs
        private <T extends AbstractInsnNode> T nextInsn(Class<T> insnClass, int opcode, Predicate<? super T>... criteria) {
            T next = insnClass.cast(Objects.requireNonNull(nextInsn()));

            if (opcode >= 0) {
                assert next.getOpcode() == opcode;
            }
            for (var c: criteria) {
                if (!c.test(next)) {
                    throw new RuntimeException("Bytecode not as expected");
                }
            }
            return next;
        }
        @SafeVarargs
        private <T extends AbstractInsnNode> T nextInsn(Class<T> insnClass, Predicate<? super T>... criteria) {
            return nextInsn(insnClass, -1, criteria);
        }

        @Override
        public void visitEnd() {
            it = instructions.iterator();
            Objects.requireNonNull(searchFor(VarInsnNode.class, ILOAD, vi -> vi.var == 0)); /* var 0: mode */
            var switchNode = nextInsn(LookupSwitchInsnNode.class);
            assert switchNode.keys.get(0) == 1 /* LM_CLASS */;
            Objects.requireNonNull(search(switchNode.labels.get(0)::equals));
            nextInsn(VarInsnNode.class, ALOAD, vi -> vi.var == 1); /* var 1: what */
            var cnVar = nextInsn(VarInsnNode.class, ASTORE).var;
            var switchEndLabel = nextInsn(JumpInsnNode.class, GOTO).label;
            Objects.requireNonNull(search(switchEndLabel::equals));

            // at the end of the switch, report the actual cn and replace it with Folger's crystals
            nextInsn(VarInsnNode.class, ALOAD, vi -> vi.var == cnVar);
            it.add(new MethodInsnNode(
                        INVOKESTATIC,
                        Type.getInternalName(Interceptor.class),
                        "reportMainClass",
                        Type.getMethodDescriptor(Type.getType(String.class), Type.getType(String.class))));
            // let's see if they notice

            accept(cv);
        }
    }
}
