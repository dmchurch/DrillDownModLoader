package de.dakror.modding.javassist;

import de.dakror.modding.ClassReplacementBase;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;


public class ClassReplacementImpl extends ClassReplacementBase<CtClass, ClassPool> {
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

}
