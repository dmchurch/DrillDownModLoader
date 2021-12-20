package de.dakror.modding.asm;

public class Util {

    public static String toIntName(String extName) {
        return extName.replace('.', '/');
    }

    public static String fromIntName(String intName) {
        return intName.replace('/', '.');
    }
    
}
