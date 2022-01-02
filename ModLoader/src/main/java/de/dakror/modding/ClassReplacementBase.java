package de.dakror.modding;

import java.util.HashMap;
import java.util.Map;

abstract public class ClassReplacementBase<T, C> implements ModLoader.IClassReplacement, ModLoader.IClassMod<T, C> {
    protected Map<String, String> replacedClasses = new HashMap<>();
    
    @Override
    public boolean hooksClass(String className) {
        return replacedClasses.containsKey(className);
    }

    @Override
    public void replaceClass(String replacedClass, String replacementClass) {
        replacedClasses.put(replacedClass, replacementClass);
    }
}