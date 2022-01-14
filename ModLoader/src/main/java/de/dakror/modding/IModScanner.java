package de.dakror.modding;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public interface IModScanner extends ModLoader.IBaseMod {
    // required implementations
    String[] getAnnotatedClasses(String annotationClass);
    Function<String, IAnnotation<?>> getClassAnnotations(String className);
    String getDeclaredSuperclass(String declaringClass);

    default void scanForMods(ModLoader modLoader) {
        var modClasses = getAnnotatedClasses(ModLoader.Enabled.class);
        Arrays.sort(modClasses, (a, b) -> (getClassAnnotation(a, ModLoader.Enabled.class).getIntValue() - getClassAnnotation(b, ModLoader.Enabled.class).getIntValue()));
        for (var className: modClasses) {
            try {
                modLoader.registerMod(className);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                debugln("Exception while loading mod %s: %s", className, e.getMessage());
                debugln("Continuing with load.");
            }
        }
    }

    // optional implementations
    default int getClassVersion(String className) { throw new UnsupportedOperationException(); }
    default String[] getReferencingClasses(String referencedClass) { throw new UnsupportedOperationException(); }
    default String[] getDeclaredInterfaces(String declaringClass) { throw new UnsupportedOperationException(); }
    default Map<String, MemberInfo> getDeclaredFields(String className) { throw new UnsupportedOperationException(); }
    default Map<String, List<MemberInfo>> getDeclaredMethods(String className) { throw new UnsupportedOperationException(); }

    // convenience methods
    default IAnnotation<?> getClassAnnotation(String className, String annotationClass) {
        return getClassAnnotations(className).apply(annotationClass);
    }
    default String[] getAnnotatedClasses(Class<?> annotationClass) { return getAnnotatedClasses(annotationClass.getName()); }
    default int getClassVersion(Class<?> className) { return getClassVersion(className.getName()); }
    default String[] getReferencingClasses(Class<?> referencedClass) { return getReferencingClasses(referencedClass.getName()); }
    default String[] getDeclaredInterfaces(Class<?> declaringClass) { return getDeclaredInterfaces(declaringClass.getName()); }
    default Map<String, MemberInfo> getDeclaredFields(Class<?> className) { return getDeclaredFields(className.getName()); }
    default Map<String, List<MemberInfo>> getDeclaredMethods(Class<?> className) { return getDeclaredMethods(className.getName()); }

    default Class<?>[] loadAnnotatedClasses(Class<?> annotationClass) {
        return loadAnnotatedClasses(annotationClass, IModScanner.class.getClassLoader());
    }
    default Class<?>[] loadAnnotatedClasses(Class<?> annotationClass, ClassLoader loader) {
        return Arrays.stream(getAnnotatedClasses(annotationClass))
                    .map(cn -> { try { return loader.loadClass(cn); }
                                 catch (Exception e) { throw new RuntimeException(e); } })
                    .collect(Collectors.toSet())
                    .toArray(Class[]::new);
    }

    @SuppressWarnings("unchecked")
    default <AT> IAnnotation<AT> getClassAnnotation(String className, Class<AT> annotationClass) {
        return (IAnnotation<AT>)getClassAnnotation(className, annotationClass.getName());
    }

    static interface IAnnotation<AT> {
        String getStringValue(String memberName);
        default String getStringValue() { return getStringValue("value"); }
        default int getIntValue(String memberName) { return Integer.parseInt(getStringValue(memberName)); }
        default int getIntValue() { return getIntValue("value"); }
    }
}
