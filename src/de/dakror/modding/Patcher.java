package de.dakror.modding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class Patcher {
    public static @interface ModEnum {
        Class<?> value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public static @interface ReplacementClass {
        Class<?> replaces();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public static @interface AugmentationClass {
        @Repeatable(MultiPreInit.class)
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
        public static @interface PreInit {
            /** The name of the method to execute prior to <init> */
            String value() default "";
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.CONSTRUCTOR})
        public static @interface MultiPreInit {
            PreInit[] value();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public static @interface ExtraProperties {
        String file();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public static @interface XMLEditor {
        String file();
    }

    private final ModLoader loader;

    public Patcher(ModLoader loader) {
        this.loader = loader;
    }

    public void patchClasses() {
        var scanner = loader.getScanner();
        var replacementClasses = scanner.getAnnotatedClasses(ReplacementClass.class);
        if (replacementClasses != null)
        for (var name: replacementClasses) {
            var anno = scanner.getClassAnnotation(name, ReplacementClass.class);
            var replClassName = anno.getStringValue("replaces");
            loader.replaceClass(replClassName, name);
        }

        var augmentationClasses = scanner.getAnnotatedClasses(AugmentationClass.class);
        if (augmentationClasses != null)
        for (var name: augmentationClasses) {
            var baseClass = scanner.getDeclaredSuperclass(name);
            loader.augmentClass(baseClass, name);
        }
    }

    public void patchResources() {
        var scanner = loader.getScanner();
        for (var propsClass: scanner.loadAnnotatedClasses(ExtraProperties.class)) {
            var rname = propsClass.getAnnotation(ExtraProperties.class).file();
            loader.getMod(PropertyListEditor.class).setPropertiesFromClass(rname, propsClass);
        }

        for (var xmlClass: scanner.loadAnnotatedClasses(XMLEditor.class)) {
            try {
                var rname = xmlClass.getAnnotation(XMLEditor.class).file();
                loader.getMod(XMLResourceEditor.class).addEditor(rname, (XMLResourceEditor.Editor)xmlClass.getConstructor().newInstance());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void patchEnums() {

        // Set<String> annotations = scanner.getAnnotatedClasses(ModEnum.class);

        // ClassPool pool = new ClassPool();

        // if (annotations == null)
        //     return;

        // for (String s : annotations) {
        //     CtClass cls = pool.get(s);

        // }
    }
}