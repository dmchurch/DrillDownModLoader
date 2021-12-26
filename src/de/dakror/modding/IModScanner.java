package de.dakror.modding;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public interface IModScanner extends ModLoader.IBaseMod {
    // required implementations
    String[] getAnnotatedClasses(String annotationClass);
    IAnnotation<?> getClassAnnotation(String className, String annotationClass);
    String getDeclaredSuperclass(String declaringClass);

    // optional implementations
    default int getClassVersion(String className) { throw new UnsupportedOperationException(); }
    default String[] getReferencingClasses(String referencedClass) { throw new UnsupportedOperationException(); }
    default String[] getDeclaredInterfaces(String declaringClass) { throw new UnsupportedOperationException(); }
    default Map<String, MemberInfo> getDeclaredFields(String className) { throw new UnsupportedOperationException(); }
    default Map<String, List<MemberInfo>> getDeclaredMethods(String className) { throw new UnsupportedOperationException(); }

    // convenience methods
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
                    .toArray(Class[]::new);
    }

    @SuppressWarnings("unchecked")
    default <AT> IAnnotation<AT> getClassAnnotation(String className, Class<AT> annotationClass) {
        return (IAnnotation<AT>)getClassAnnotation(className, annotationClass.getName());
    }

    static interface IAnnotation<AT> {
        String getStringValue(String memberName);
        default String getStringValue() { return getStringValue("value"); }
    }
}
