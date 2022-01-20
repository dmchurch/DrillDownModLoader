package de.dakror.modding.agent;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Set;

import de.dakror.modding.agent.ModAgent.TaskLog;
import de.dakror.modding.agent.boot.CallAdapter;
import de.dakror.modding.agent.boot.Interceptor;
import de.dakror.modding.platform.ModClassInterceptor;

public class AgentTrampoline {
    public final ClassLoader classLoader;
    private final Instrumentation inst;
    private String mainClassName = null;
    private CallInterceptionTransformer forNameTransformer = null;

    AgentTrampoline(ClassLoader classLoader, Instrumentation inst) {
        this.classLoader = classLoader;
        this.inst = inst;
    }

    public ModAgent.MainMethod loadAndStartModPlatform(TaskLog task, String[] args) throws Throwable {
        var appLoader = ClassLoader.getSystemClassLoader();
        assert mainClassName != null;
        var mci = new ModClassInterceptor(appLoader, inst, mainClassName, args);
        task.report("instantiated");
        mci.start(null, args);
        task.report("started");
        Class<?> mainClass = null;
        try {
            mainClass = mci.loadClass(mainClassName);
            var lhClass = Class.forName("sun.launcher.LauncherHelper");
            // MainHookTransformer will have made this public if possible, try to set appClass if so
            lhClass.getField("appClass").set(null, mainClass);
        } catch (NoSuchFieldException nsfe) {
            // but don't worry if not
        } catch (ReflectiveOperationException roe) {
            if (mainClass == null) {
                throw task.fail(roe);
            }
            task.report(roe);
        }
        try {
            var mh = MethodHandles.publicLookup().findStatic(mainClass, "main", MethodType.methodType(void.class, String[].class));
            return MethodHandleProxies.asInterfaceInstance(ModAgent.MainMethod.class, mh);
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
                            /* extraExports = */ Map.of("jdk.internal.loader", Set.of(bootModule),
                                                        "sun.launcher", Set.of(myModule)),
                            /* extraOpens = */ Map.of("jdk.internal.loader", Set.of(bootModule)),
                            /* extraUses = */ Set.of(),
                            /* extraProvides = */ Map.of());
        task.report("opened java.base to "+myModule);

        var callTransformer = new CallInterceptionTransformer(Interceptor.class, classLoader, CallAdapter.getUCP(classLoader));
        callTransformer.apply(inst);
    }

    void hookMainClass(TaskLog task) {
        var forNameInterceptor = Interceptor.of(Class.class, new Interceptor () {
            boolean finished = false;
            @SuppressWarnings("unused")
            public Class<?> forName(Class<Class<?>> classClass, String name, boolean initialize, ClassLoader loader) throws ClassNotFoundException, NoInterceptionException {
                if (!finished && initialize == false && loader == ClassLoader.getSystemClassLoader()) {
                    finished = true;
                    forNameTransformer.revert(inst);
                    forNameTransformer = null;
                    mainClassName = name;
                    return ModAgent.class;
                }
                throw NO_INTERCEPTION;
            }
        }, MethodHandles.lookup());

        forNameTransformer = new CallInterceptionTransformer(forNameInterceptor, Class.class);
        forNameTransformer.apply(inst);
    }
}
