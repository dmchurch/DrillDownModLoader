package de.dakror.modding.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;

import de.dakror.modding.agent.ModAgent.TaskLog;
import de.dakror.modding.agent.boot.Interceptor;
import de.dakror.modding.agent.boot.Interceptor.IClassInterceptor;
import de.dakror.modding.loader.ModClassInterceptor;

public class AgentTrampoline {
    public final ClassLoader classLoader;
    private final Instrumentation inst;

    AgentTrampoline(ClassLoader classLoader, Instrumentation inst) {
        this.classLoader = classLoader;
        this.inst = inst;
    }

    public static IClassInterceptor hookInterceptor(ClassLoader toHook, IClassInterceptor hookTarget) {
        return Interceptor.interceptClasses(toHook, hookTarget);
    }

    public static void walkDeclaredClasses(Class<?> outerClass) {
        for (var sub: outerClass.getDeclaredClasses()) {
            walkDeclaredClasses(sub);
        }
    }

    public ModAgent.MainMethod loadAndStartModPlatform(TaskLog task, String[] args) throws Throwable {
        var appLoader = ClassLoader.getSystemClassLoader();
        var mainClassName = Interceptor.reportMainClass("");
        assert !mainClassName.equals(ModAgent.class.getName());
        var mci = new ModClassInterceptor(appLoader, inst, mainClassName, args);
        task.report("instantiated");
        mci.start(null, args);
        task.report("started");
        Class<?> mainClass = null;
        try {
            mainClass = mci.loadClass(mainClassName);
            var lhClass = Class.forName("sun.launcher.LauncherHelper");
            // MainHookTransformer will have made this public
            lhClass.getField("appClass").set(null, mainClass);
        } catch (ReflectiveOperationException roe) {
            if (mainClass == null) {
                throw task.fail(roe);
            }
            task.report(roe);
        }
        try {
            var mh = MethodHandles.publicLookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class));
            return mh::invoke;
        } catch (ReflectiveOperationException roe) {
            throw task.fail(roe);
        }
    }

    void hookClassLoader(TaskLog task) {
        var javaBase = Object.class.getModule();
        var bootModule = Interceptor.class.getModule();
        var myModule = AgentTrampoline.class.getModule();
        inst.redefineModule(/* module = */ javaBase,
                            /* extraReads = */ Set.of(),
                            /* extraExports = */ Map.of("jdk.internal.access", Set.of(bootModule),
                                                        "jdk.internal.loader", Set.of(bootModule),
                                                        "sun.launcher", Set.of(myModule)),
                            /* extraOpens = */ Map.of("jdk.internal.loader", Set.of(bootModule)),
                            /* extraUses = */ Set.of(),
                            /* extraProvides = */ Map.of());
        task.report("opened java.base to "+myModule);

        try (var callTransformer = ctx(new CallInterceptionTransformer(classLoader), true)) {
            inst.retransformClasses(callTransformer.transformer.classesToRetransform);
        } catch (UnmodifiableClassException uce) {
            task.fail(uce); // unlikely
        }
    }

    void hookMainClass(TaskLog task) {
        try (var ignore = ctx(new MainHookTransformer(), true)) {
            inst.retransformClasses(Class.forName("sun.launcher.LauncherHelper"));
        } catch (ClassNotFoundException|UnmodifiableClassException e) {
            task.fail(e);
        }
        Interceptor.reportMainClass(ModAgent.class.getName());
    }

    private <T extends ClassFileTransformer> TransformerContext<T> ctx(T transformer, boolean canRetransform) {
        return new TransformerContext<>(transformer, canRetransform);
    }

    private class TransformerContext<T extends ClassFileTransformer> implements AutoCloseable {
        private final T transformer;

        private TransformerContext(T transformer, boolean canRetransform) {
            this.transformer = transformer;
            inst.addTransformer(transformer, canRetransform);
        }

        @Override
        public void close() {
            inst.removeTransformer(transformer);
        }
    }

}
