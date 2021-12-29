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
        public static final class SUPERCLASS {}
        public static final String SUPERCLASS = AugmentationClass.SUPERCLASS.class.getName();

        Class<?> augments() default SUPERCLASS.class;

        @Repeatable(MultiPreInit.class)
        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.CONSTRUCTOR, ElementType.METHOD})
        public static @interface PreInit {
            /** The name of the method to execute prior to <init> */
            String value() default "";
            Class<?> inClass() default SUPERCLASS.class;
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target({ElementType.CONSTRUCTOR})
        public static @interface MultiPreInit {
            PreInit[] value();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    public static @interface EnumExtensionClass {
        Class<?> extendsEnum();
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
        for (var name: scanner.getAnnotatedClasses(ReplacementClass.class)) {
            var anno = scanner.getClassAnnotation(name, ReplacementClass.class);
            var replClassName = anno.getStringValue("replaces");
            loader.replaceClass(replClassName, name);
        }

        for (var name: scanner.getAnnotatedClasses(AugmentationClass.class)) {
            var anno = scanner.getClassAnnotation(name, AugmentationClass.class);
            var baseClass = anno.getStringValue("augments");
            if (baseClass == null || baseClass.equals(AugmentationClass.SUPERCLASS)) {
                baseClass = scanner.getDeclaredSuperclass(name);
            }
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