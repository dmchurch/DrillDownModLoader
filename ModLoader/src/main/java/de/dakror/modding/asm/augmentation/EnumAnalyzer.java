package de.dakror.modding.asm.augmentation;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import de.dakror.modding.asm.Util;

public class EnumAnalyzer extends ClassVisitor implements Opcodes {
    private static final Method CLINIT = Method.getMethod("void <clinit>()");
    private static final Method ENUM_VALUEOF = Method.getMethod("Enum valueOf(Class, String)");
    private static final Method OBJECT_CLONE = Method.getMethod("Object clone()");
    private Method VALUES;
    private Method VALUEOF;
    private Type enumType;
    private Type enumArrayType;

    public final EnumMemberMap enumFields = new EnumMemberMap();
    public int clinitMaxLocals = 0;
    public int clinitMaxStack = 0;

    public EnumAnalyzer(ClassVisitor classVisitor) {
        super(ASM9, classVisitor);
    }

    public void updateMaxs(int maxStack, int maxLocals) {
        clinitMaxLocals = Math.max(maxLocals, clinitMaxLocals);
        clinitMaxStack = Math.max(maxStack, clinitMaxStack);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        assert (access & ACC_ENUM) != 0;
        enumType = Type.getObjectType(name);
        VALUEOF = Method.getMethod(enumType.getClassName() + " valueOf(String)");
        VALUES = Method.getMethod(enumType.getClassName() + "[] values()");
        enumArrayType = VALUES.getReturnType();
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        if (access == (ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_ENUM)) {
            enumFields.add(name);
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        var method = new Method(name, descriptor);
        if (method.equals(CLINIT)) {
            return new MethodVisitor(ASM9, new ClinitAnalyzer(access, name, descriptor, signature, exceptions, enumType.getInternalName(), enumFields, cv)) {
                public void visitMaxs(int maxStack, int maxLocals) {
                    updateMaxs(maxStack, maxLocals);
                    super.visitMaxs(maxStack, maxLocals);
                }
            };
        } else if (method.equals(VALUEOF) || method.equals(VALUES)) {
            // ignore the values() and valueOf(String) methods entirely, we'll provide our own implementation when requested
            return null;
        }
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    public void emitInitializers(GeneratorAdapter gen) {
        var fields = enumFields.getFields();
        // emit all the field inits
        for (var field: fields) {
            field.getInitInsns().accept(gen);
        }
        // collect the values into an array for use in the values() function
        gen.push(fields.length);
        gen.newArray(enumType);
        for (var field: fields) {
            gen.dup();
            gen.push(field.ordinal);
            gen.getStatic(enumType, field.name, enumType);
            gen.arrayStore(enumType);
        }
        gen.putStatic(enumType, "ENUM$allvalues", enumArrayType);
    }

    public void emitValuesMethod() {
        emitValuesMethod(cv);
    }
    public void emitValuesMethod(ClassVisitor cv) {
        cv.visitField(ACC_PRIVATE|ACC_STATIC|ACC_SYNTHETIC|ACC_FINAL, "ENUM$allvalues", enumArrayType.getDescriptor(), null, null).visitEnd();
        var gen = Util.genMethod(cv, ACC_PUBLIC|ACC_STATIC, VALUES);
        gen.getStatic(enumType, "ENUM$allvalues", enumArrayType);
        gen.invokeVirtual(enumArrayType, OBJECT_CLONE);
        gen.checkCast(enumArrayType);
        gen.returnValue();
        gen.visitMaxs(1, 0);
        gen.visitEnd();
    }

    public void emitValueOfMethod() {
        emitValueOfMethod(cv);
    }
    public void emitValueOfMethod(ClassVisitor cv) {
        var gen = Util.genMethod(cv, ACC_PUBLIC|ACC_STATIC, VALUEOF);
        gen.push(enumType);
        gen.loadArg(0);
        gen.invokeStatic(Type.getType(Enum.class), ENUM_VALUEOF);
        gen.checkCast(enumType);
        gen.returnValue();
        gen.visitMaxs(2, 1);
        gen.visitEnd();
    }
}