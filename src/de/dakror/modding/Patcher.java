package de.dakror.modding;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.scannotation.AnnotationDB;

import javassist.ClassPool;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.ClassMemberValue;

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
    }

    private AnnotationDB db;
    private ClassPool pool;

    public Patcher(URL[] urls) throws IOException {
        db = new AnnotationDB();
        db.scanArchives(urls);

        pool = new ClassPool();
        var cl = new URLClassLoader(urls);
        pool.insertClassPath(new LoaderClassPath(cl));
    }

    public void patchClasses(ModLoader loader) {
        var replacementClasses = db.getAnnotationIndex().get(ReplacementClass.class.getName());
        if (replacementClasses != null)
        for (var name: replacementClasses) {
            try {
                var ct = pool.get(name);
                // can't use ct.getAnnotation because that would trigger a class lookup, so instead:
                var cfile = ct.getClassFile();
                var annoAttr = (AnnotationsAttribute) cfile.getAttribute(AnnotationsAttribute.visibleTag);
                var replacementAnno = annoAttr.getAnnotation(ReplacementClass.class.getName());
                var replClass = (ClassMemberValue) replacementAnno.getMemberValue("replaces");
                var replClassName = replClass.getValue();
                loader.replaceClass(replClassName, name);
            } catch (NotFoundException e) {
                System.err.println("could not find class "+name+" for replace: "+e.getMessage());
                continue;
            }
        }

        var augmentationClasses = db.getAnnotationIndex().get(AugmentationClass.class.getName());
        if (augmentationClasses != null)
        for (var name: augmentationClasses) {
            try {
                var ct = pool.get(name);
                var cfile = ct.getClassFile();
                var baseClass = cfile.getSuperclass();
                loader.augmentClass(baseClass, name);
            } catch (NotFoundException e) {
                System.err.println("could not find class "+name+" for augment: "+e.getMessage());
                continue;
            }
        }
    }

    public static void patchEnums(URL[] urls) throws IOException, NotFoundException {

        // Set<String> annotations = db.getAnnotationIndex().get(ModEnum.class.getName());

        // ClassPool pool = new ClassPool();

        // if (annotations == null)
        //     return;

        // for (String s : annotations) {
        //     CtClass cls = pool.get(s);

        // }
    }
}