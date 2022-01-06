package de.dakror.modding.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.util.CheckClassAdapter;

import de.dakror.modding.agent.boot.Interceptor;
import de.dakror.modding.agent.boot.Interceptor.IClassInterceptor;
import de.dakror.modding.agent.boot.Interceptor.NoInterceptionException;

public class ModClassTransformer implements ClassFileTransformer {
    public final ClassLoader classLoader;

    private static final Map<Method, Method> INTERCEPTIONS = getInterceptedMethods();
    private final Map<String, Map<Method, Method>> methodsToHookByClass;
    private final Class<?>[] classesToRetransform;

    ModClassTransformer(ClassLoader classLoader) {
        this.classLoader = classLoader;
        this.methodsToHookByClass = new HashMap<>();

        var methodsToHook = new HashMap<>(INTERCEPTIONS);
        var classesToHook = new HashSet<Class<?>>();

        for (Class<?> checkClass = classLoader.getClass(); checkClass != null && !methodsToHook.isEmpty(); checkClass = checkClass.getSuperclass()) {
            for (var reflectMethod: checkClass.getDeclaredMethods()) {
                var baseMethod = Method.getMethod(reflectMethod);
                var hookMethod = methodsToHook.remove(baseMethod);
                if (hookMethod != null) {
                    methodsToHookByClass.computeIfAbsent(checkClass.getName().replace('.', '/'), k -> new HashMap<>()).put(baseMethod, hookMethod);
                    classesToHook.add(checkClass);
                }
            }
        }

        this.classesToRetransform = classesToHook.toArray(Class[]::new);
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
    public static Object loadAndHookModClassLoader(ClassLoader appLoader, Instrumentation inst, String mclClassName) {
        final String mclIntName = mclClassName.replace('.','/');
        var transformer = new StubReplacementTransformer(cn -> cn.startsWith(mclIntName), Interceptor.class);

        Class<? extends IClassInterceptor> mclClass;
        inst.addTransformer(transformer);
        try {
            var cls = appLoader.loadClass(mclClassName);
            // ensure any member classes have also been transformed before removing this transformer
            walkDeclaredClasses(cls);
            mclClass = cls.asSubclass(IClassInterceptor.class);
        } catch (ClassNotFoundException cnfe) {
            return null;
        } catch (Throwable e) {
            throw new RuntimeException(e);
        } finally {
            inst.removeTransformer(transformer);
        }

        try {
            var mcl = mclClass.getConstructor(ClassLoader.class, Instrumentation.class).newInstance(appLoader, inst);
            hookInterceptor(appLoader, mcl);
            return mcl;
        } catch (InvocationTargetException ite) {
            throw new RuntimeException(ite.getTargetException());
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException(roe);
        }
    }

    private Class<?>[] getClassesToRetransform() {
        return classesToRetransform;
    }

    static void hookClassLoader(ClassLoader classLoader, Instrumentation inst) {
        var transformer = new ModClassTransformer(classLoader);
        inst.addTransformer(transformer, true);
        try {
            inst.retransformClasses(transformer.getClassesToRetransform());
        } catch (UnmodifiableClassException uce) {
            throw new RuntimeException(uce); // unlikely
        }
        inst.removeTransformer(transformer);
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        var methodsToHook = methodsToHookByClass.remove(className);
        if (methodsToHook == null) {
            return null;
        }
        var cr = new ClassReader(classfileBuffer);
        var cw = new ClassWriter(cr, 0);
        try {
            cr.accept(new ClassVisitor(Opcodes.ASM9, new CheckClassAdapter(cw, true)) {
                Map<Method, Method> methodsToHook = new HashMap<>(INTERCEPTIONS);
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    final var mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                    final var baseMethod = new Method(name, descriptor);
                    final var interceptMethod = methodsToHook.remove(baseMethod);
                    if (interceptMethod != null) {
                        return new InterceptionAdapter(mv, access, name, descriptor, interceptMethod);
                    }
                    return mv;
                }
            }, 0);
        } catch (Throwable e) {
            System.err.print("Exception in transform: ");
            e.printStackTrace();
            System.exit(1);
        }
        return cw.toByteArray();
    }

    static Map<Method, Method> getInterceptedMethods() {
        Map<Method, Method> methods = new HashMap<>();
        for (var reflectMethod: Interceptor.class.getDeclaredMethods()) {
            int mod = reflectMethod.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
                var hookMethod = Method.getMethod(reflectMethod);
                var hookArgTypes = hookMethod.getArgumentTypes();
                var baseMethod = new Method(hookMethod.getName(), hookMethod.getReturnType(),
                                            Arrays.copyOfRange(hookArgTypes, 1, hookArgTypes.length));
                methods.put(baseMethod, hookMethod);
            }
        }
        return Map.copyOf(methods);
    }

    private class InterceptionAdapter extends GeneratorAdapter {
        final Method interceptMethod;
        public InterceptionAdapter(MethodVisitor mv, int access, String name, String descriptor, Method interceptMethod) {
            super(Opcodes.ASM9, mv, access, name, descriptor);
            this.interceptMethod = interceptMethod;
        }
        @Override
        public void visitCode() {
            super.visitCode();
            var start = new Label();
            var end = new Label();
            var handler = new Label();
            visitTryCatchBlock(start, end, handler, Type.getType(NoInterceptionException.class).getInternalName());

            mark(start);
            loadThis();
            loadArgs();
            invokeStatic(Type.getType(Interceptor.class), interceptMethod);
            returnValue();
            mark(end);
            mark(handler);
            pop();
            // then continue with the usual code
        }
    }
}
