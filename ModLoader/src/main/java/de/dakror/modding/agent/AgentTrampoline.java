package de.dakror.modding.agent;

import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;

import de.dakror.modding.agent.ModAgent.TaskLog;
import de.dakror.modding.agent.boot.Interceptor;
import de.dakror.modding.agent.boot.Interceptor.IClassInterceptor;

public class AgentTrampoline {
    public final ClassLoader classLoader;
    private final Instrumentation inst;

    AgentTrampoline(ClassLoader classLoader, Instrumentation inst) {
        this.classLoader = classLoader;
        this.inst = inst;
    }

    public static IClassInterceptor hookInterceptor(ClassLoader toHook, IClassInterceptor hookTarget) {
        return Interceptor.loaderInterceptions.put(toHook, hookTarget);
    }

    public static void walkDeclaredClasses(Class<?> outerClass) {
        for (var sub: outerClass.getDeclaredClasses()) {
            walkDeclaredClasses(sub);
        }
    }

    // load the ModClassLoader class definition, transform any direct implementation of an interface named IClassInterceptor
    // contained within a class/interface named Interceptor into an implementation of the actual Interceptor$IClassInterceptor.
    public Object loadAndHookModClassLoader(TaskLog task, String mclClassName) {
        final String mclIntName = mclClassName.replace('.','/');
        var transformer = new StubReplacementTransformer(cn -> cn.startsWith(mclIntName), Interceptor.class);

        Class<? extends IClassInterceptor> mclClass;
        inst.addTransformer(transformer);
        try {
            var cls = classLoader.loadClass(mclClassName);
            // ensure any member classes have also been transformed before removing this transformer
            walkDeclaredClasses(cls);
            mclClass = cls.asSubclass(IClassInterceptor.class);
            task.report("loaded");
        } catch (ClassNotFoundException cnfe) {
            task.report(cnfe);
            return null;
        } catch (Throwable e) {
            throw task.fail(e);
        } finally {
            inst.removeTransformer(transformer);
        }

        try {
            var mcl = mclClass.getConstructor(ClassLoader.class, Instrumentation.class).newInstance(classLoader, inst);
            task.report("instantiated");
            hookInterceptor(classLoader, mcl);
            return mcl;
        } catch (InvocationTargetException ite) {
            throw task.fail(ite.getTargetException());
        } catch (ReflectiveOperationException roe) {
            throw task.fail(roe);
        }
    }

    void hookClassLoader(TaskLog task) {
        var transformer = new CallInterceptionTransformer(classLoader);
        inst.addTransformer(transformer, true);
        try {
            inst.retransformClasses(transformer.classesToRetransform);
        } catch (UnmodifiableClassException uce) {
            task.fail(uce); // unlikely
        }
        inst.removeTransformer(transformer);
    }

}
