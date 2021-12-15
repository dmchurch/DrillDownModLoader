package de.dakror.modding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;

public class ClassPoolModLoader extends ModLoader {
    protected ClassPool classPool;
    protected List<IClassMod<CtClass, ClassPool>> classMods = new ArrayList<>();

    @Override
    protected void implInit() {
        classPool = new ClassPool();
        classPool.insertClassPath(new LoaderClassPath(classLoader));
    }

    public ClassPool getClassPool() {
        return classPool;
    }

    @Override
    protected void registerClassMod(IClassMod<?,?> mod) {
        registerCtClassMod(mod.asType(CtClass.class, ClassPool.class));
        super.registerClassMod(mod);
    }

    protected void registerCtClassMod(IClassMod<CtClass, ClassPool> mod) {
        classMods.add(mod);
    }

    protected CtClass redefineCtClass(String name, CtClass cc) {
        for (var mod: classMods) {
            if (mod.hooksClass(name)) {
                try {
                    cc = mod.redefineClass(name, cc, classPool);
                } catch (ClassNotFoundException e) {
                    debugln(mod.getClass().getName()+".redefineClass("+name+" threw CNFE, skipping");
                } catch (NullPointerException e) {
                    if (cc == null) {
                        // this is reasonable, just skip
                    } else {
                        throw e;
                    }
                }
            }
        }

        return cc;
    }

    @Override
    public byte[] redefineClass(String name, Class<?> origClass) throws ClassNotFoundException {
        CtClass cc = classPool.getOrNull(name);

        cc = redefineCtClass(name, cc);

        if (cc == null) {
            throw new RuntimeException("did not get a CtClass after mods, despite being hooked");
        }
        try {
            return cc.toBytecode();
        } catch (IOException|CannotCompileException e) {
            throw new RuntimeException(e);
        }
    }
    
}
