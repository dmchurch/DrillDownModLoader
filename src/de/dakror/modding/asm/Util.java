package de.dakror.modding.asm;

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

    public static String appendMethodParam(String methodDesc, String paramDesc) {
        return methodDesc.substring(0, methodDesc.indexOf(')')) + paramDesc + methodDesc.substring(methodDesc.indexOf(')'));
    }
    
    public static String prependMethodParam(String methodDesc, String paramDesc) {
        return "(" + paramDesc + methodDesc.substring(1);
    }
    
}
