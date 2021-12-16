package de.dakror.modding.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import de.dakror.modding.ClassAugmentationBase;

public class ClassAugmentationImpl extends ClassAugmentationBase<ClassVisitor, ClassReader> {
    protected AugmentationChain newAugmentationChain(String className) {
        return new AugmentationChain(className);
    }

    protected class AugmentationChain extends ClassAugmentationBase<ClassVisitor, ClassReader>.AugmentationChain {
        public AugmentationChain(String baseClass) {
            super(baseClass);
        }

        public boolean compile(String className, ClassReader reader) {
            if (compiled) return true;

            compiled = true;
            return true;
        }

        public ClassVisitor redefineClass(String className, ClassVisitor nextVisitor, ClassReader reader) {
            compile(className, reader);
            return new AugmentationVisitor(nextVisitor, reader);
        }

        protected class AugmentationVisitor extends ClassVisitor {
            protected ClassReader reader;
            protected String[] interfaces;

            public AugmentationVisitor(ClassVisitor classVisitor, ClassReader reader) {
                super(Opcodes.ASM9, classVisitor);
                this.reader = reader;
                interfaces = reader.getInterfaces();

            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                this.interfaces = interfaces;

                super.visit(version, access, name, signature, superName, this.interfaces);
            }
        }
    }
}
