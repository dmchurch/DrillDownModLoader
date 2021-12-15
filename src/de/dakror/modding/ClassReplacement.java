package de.dakror.modding;

import java.util.HashMap;
import java.util.Map;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public class ClassReplacement implements ModLoader.IClassReplacement, ModLoader.IClassMod<CtClass, ClassPool>  {
    private Map<String, String> replacedClasses = new HashMap<>();
    
    @Override
    public boolean hooksClass(String className) {
        return replacedClasses.containsKey(className);
    }

    @Override
    public CtClass redefineClass(String className, CtClass ctClass, ClassPool classPool)
            throws ClassNotFoundException {
        var replacement = replacedClasses.get(className);
        try {
            return classPool.getAndRename(replacement, className);
        } catch (NotFoundException e) {
            throw new ClassNotFoundException(e.getMessage());
        }
    }

    @Override
    public void replaceClass(String replacedClass, String replacementClass) {
        replacedClasses.put(replacedClass, replacementClass);
    }
}