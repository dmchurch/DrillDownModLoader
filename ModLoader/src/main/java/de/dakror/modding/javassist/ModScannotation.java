package de.dakror.modding.javassist;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.function.Function;

import org.scannotation.AnnotationDB;

import de.dakror.modding.IModScanner;
import de.dakror.modding.ModLoader;
import javassist.ClassPool;
import javassist.LoaderClassPath;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ClassMemberValue;

public class ModScannotation implements IModScanner {
    private URL[] urls;
    private AnnotationDB db;
    private ClassPool pool;
    private ClassLoader cl;

    @Override
    public void registered(ModLoader modLoader) throws Exception {
        urls = modLoader.getModUrls().toArray(URL[]::new);
        Debug.println("starting scannotation");
        var start = Instant.now();
        db = new AnnotationDB();
        db.scanArchives(urls);
        var elapsed = ChronoUnit.NANOS.between(start, Instant.now());
        Debug.println(String.format("scan finished, %d ns elapsed (%.3f ms)", elapsed, (double)elapsed/1000000.0));

        pool = new ClassPool();
        cl = new URLClassLoader(urls);
        pool.insertClassPath(new LoaderClassPath(cl));
    }

    @Override
    public String[] getAnnotatedClasses(String annotationClass) {
        return db.getAnnotationIndex().get(annotationClass).toArray(String[]::new);
    }

    @Override
    public Function<String, IAnnotation<?>> getClassAnnotations(String className) {
        try {
            var ct = pool.get(className);
            // can't use ct.getAnnotation because that would trigger a class lookup, so instead:
            var cfile = ct.getClassFile();
            var annoAttr = (AnnotationsAttribute) cfile.getAttribute(AnnotationsAttribute.visibleTag);
            return annotationClass -> new AnnotationWrapper<>(annoAttr.getAnnotation(annotationClass));
        } catch (NotFoundException e) {
            return null;
        }
    }
    
    @Override
    public String getDeclaredSuperclass(String declaringClass) {
        try {
            var ct = pool.get(declaringClass);
            var cfile = ct.getClassFile();
            return cfile.getSuperclass();
        } catch (NotFoundException e) {
            throw new RuntimeException("could not find class "+declaringClass+" for augmentation", e);
        }
}

    private class AnnotationWrapper<AT> implements IAnnotation<AT> {
        private Annotation anno;
        public AnnotationWrapper(Annotation anno) {
            this.anno = anno;
        }
        @Override
        public String getStringValue(String memberName) {
            var value = anno.getMemberValue(memberName);
            if (value instanceof ClassMemberValue) {
                return ((ClassMemberValue)value).getValue();
            }
            return value.toString();
        }
    }
}
