package de.dakror.modding.asm;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class Util {

    public static String toIntName(String extName) {
        return extName.replace('.', '/');
    }

    public static String fromIntName(String intName) {
        return intName.replace('/', '.');
    }

    public static String methodDescArgs(String desc) {
        return desc.substring(0, desc.indexOf(')')+1);
    }

    public static String methodDescReturn(String desc) {
        return desc.substring(desc.indexOf(')')+1);
    }

    public static String methodKey(String name, String desc) {
        return name + methodDescArgs(desc);
    }

    public static String appendMethodParam(String methodDesc, String paramDesc) {
        return methodDesc.substring(0, methodDesc.indexOf(')')) + paramDesc + methodDesc.substring(methodDesc.indexOf(')'));
    }
    
    public static String prependMethodParam(String methodDesc, String paramDesc) {
        return "(" + paramDesc + methodDesc.substring(1);
    }
    
    public static GeneratorAdapter genMethod(ClassVisitor cv, int access, String name, String descriptor, String signature, String... exceptions) {
        var gen = new GeneratorAdapter(cv.visitMethod(access, name, descriptor, signature, exceptions), access, name, descriptor);
        gen.visitCode();
        return gen;
    }

    public static GeneratorAdapter genMethod(ClassVisitor cv, int access, Method method, String... exceptions) {
        return genMethod(cv, access, method.getName(), method.getDescriptor(), null, exceptions);
    }
}
