package de.dakror.modding;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

public class StackedClassPoolModLoader extends ClassPoolModLoader {
    protected ClassPool baseClassPool;

    @Override
    protected void implInit() {
        super.implInit();
        baseClassPool = classPool;
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    @Override
    protected void registerCtClassMod(IClassMod<CtClass, ClassPool> mod) {
        super.registerCtClassMod(mod);
        classPool = new StackedClassPoolModLoader.ModClassPool(classPool, mod);
    }

    @Override
    protected CtClass redefineCtClass(String name, CtClass cc) {
        // all the work is done in the ModClassPool
        return cc;
    }

    protected static class ModClassPool extends ClassPool {
        private ClassPool parent;
        private ModLoader.IClassMod<CtClass, ClassPool> parentMod;
    
        public ModClassPool(ClassPool parent, ModLoader.IClassMod<CtClass, ClassPool> parentMod) {
            super(parent);
            childFirstLookup = true;
            this.parentMod = parentMod;
        }
    
        @Override
        protected synchronized CtClass get0(String classname, boolean useCache) throws NotFoundException {
            if (!parentMod.hooksClass(classname)) {
                // not hooked, just pass through to default functionality, return parent if available
                var ctClass = super.get0(classname, useCache);
                ctClass.getClassFile2(); // work around a bug in javassist
                return ctClass;
            }
    
            CtClass ctClass = null;
            CtClass parentClass = null;
            try {
                ctClass = super.get0(classname, useCache);
            } catch (NotFoundException e) {}
    
            if (ctClass != null && ctClass.getClassPool() != null && ctClass.getClassPool() != this) {
                // if base get0 returns a class from another ClassPool, then it's the parent class
                parentClass = ctClass;
                ctClass = null;
            }
    
            // if base get0 returns a valid class NOT from another ClassPool, then it's a primitive/array class, return it
            if (ctClass != null) return ctClass;
    
            // Otherwise, this is a proper class and must be processed.
            try {
                ctClass = parentMod.redefineClass(classname, parentClass, parent);
            } catch (ClassNotFoundException e) {
                if (e.getCause() instanceof NotFoundException) {
                    throw (NotFoundException) e.getCause();
                } else {
                    throw (NotFoundException) new NotFoundException("Class mod threw CNFE for "+classname, e).initCause(e);
                }
            } catch (NullPointerException e) {
                if (parentClass == null) {
                    // this is reasonable, ignore it
                    return null;
                }
                throw e;
            }
    
            if (ctClass == null) {
                return parentClass;
            }
    
            if (!ctClass.getName().equals(classname)) {
                throw new UnsupportedOperationException("Expecting CtClass with name "+classname+", got "+ctClass.getName());
            }
    
            // now we need to copy the CtClass to make it part of this ClassPool, and to prevent modifications further down the line from affecting us
            try {
                var bytecode = ctClass.toBytecode();
                var cachedClass = getCached(classname);
                ctClass = makeClass(new ByteArrayInputStream(bytecode));
                if (!useCache) {
                    if (cachedClass == null) {
                        removeCached(classname);
                    } else {
                        cacheCtClass(classname, cachedClass, false);
                    }
                }
            } catch (IOException|CannotCompileException e) {
                throw new RuntimeException("Exception on creating new class for "+classname, e);
            }
    
            return ctClass;
        }
    }
}
