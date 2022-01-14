package de.dakror.modding.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.reflect.Modifier;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;

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
import de.dakror.modding.agent.boot.Interceptor.NoInterceptionException;

public class CallInterceptionTransformer implements ClassFileTransformer {
    private static final Map<Method, Map<Class<?>, Method>> INTERCEPTIONS = getInterceptedMethods();

    static {
        if ("true".equals(System.getProperty("de.dakror.modding.boot.debug"))) {
            Interceptor.DEBUG_INTERCEPTOR = true;
        }
    }

    private final Map<String, Map<Method, Method>> methodsToHookByClass;
    final Class<?>[] classesToRetransform;
    
    CallInterceptionTransformer(Object... targets) {
        this.methodsToHookByClass = new HashMap<>();

        var methodsToHook = new HashMap<>(INTERCEPTIONS.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new HashMap<>(e.getValue()))));
        var classesToHook = new HashSet<Class<?>>();

        for (var target: targets) {
            if (target == null) {
                continue;
            }
            for (Class<?> checkClass = target.getClass(); checkClass != null && !methodsToHook.isEmpty(); checkClass = checkClass.getSuperclass()) {
                for (var reflectMethod: checkClass.getDeclaredMethods()) {
                    var baseMethod = Method.getMethod(reflectMethod);
                    var hookMethods = methodsToHook.get(baseMethod);
                    if (hookMethods == null) continue;
                    for (var hookClass: hookMethods.keySet()) {
                        if (!hookClass.isAssignableFrom(checkClass)) continue;
                        var hookMethod = hookMethods.remove(hookClass);
                        if (hookMethods.isEmpty()) {
                            methodsToHook.remove(baseMethod);
                        }
                        methodsToHookByClass.computeIfAbsent(checkClass.getName().replace('.', '/'), k -> new HashMap<>()).put(baseMethod, hookMethod);
                        classesToHook.add(checkClass);
                        break;
                    }
                }
            }
        }

        this.classesToRetransform = classesToHook.toArray(Class[]::new);
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

    static Map<Method, Map<Class<?>, Method>> getInterceptedMethods() {
        Map<Method, Map<Class<?>, Method>> methods = new HashMap<>();
        for (var reflectMethod: Interceptor.class.getDeclaredMethods()) {
            int mod = reflectMethod.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod)) {
                var hookMethod = Method.getMethod(reflectMethod);
                var hookArgTypes = hookMethod.getArgumentTypes();
                var baseMethod = new Method(hookMethod.getName(), hookMethod.getReturnType(),
                                            Arrays.copyOfRange(hookArgTypes, 1, hookArgTypes.length));
                methods.merge(baseMethod, Map.of(reflectMethod.getParameterTypes()[0], hookMethod), (a, b) -> {
                    @SuppressWarnings("unchecked")
                    Map.Entry<Class<?>, Method>[] entries = a.entrySet().toArray(new Map.Entry[a.size() + 1]);
                    entries[a.size()] = b.entrySet().iterator().next();
                    return Map.ofEntries(entries);
                });
            }
        }
        return Map.copyOf(methods);
    }
}
