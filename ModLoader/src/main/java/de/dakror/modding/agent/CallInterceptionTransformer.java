package de.dakror.modding.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
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

    static {
        if ("true".equals(System.getProperty("de.dakror.modding.boot.debug"))) {
            Interceptor.DEBUG_INTERCEPTOR = true;
        }
    }

    private final Class<?> interceptorClass;
    private final Map<Method, Map<Class<?>, Method>> interceptions;
    private final Map<String, Map<Method, Method>> methodsToHookByClass = new HashMap<>();
    private final Class<?>[] classesToRetransform;
    private final boolean isStaticInterceptor;
    private Class<?> targetClass;
    @SuppressWarnings("unused")
    private Interceptor interceptorInstance;

    CallInterceptionTransformer(Interceptor interceptor, Class<?> target) {
        this.interceptorClass = Objects.requireNonNull(interceptor).getClass();
        this.isStaticInterceptor = false;
        this.targetClass = target;
        this.interceptorInstance = interceptor;

        this.interceptions = getInterceptedMethods(interceptorClass);
        this.classesToRetransform = findHookTargets(target).toArray(Class[]::new);
    }
    
    CallInterceptionTransformer(Class<?> interceptorClass, Object... targets) {
        this.interceptorClass = interceptorClass;
        this.isStaticInterceptor = true;
        this.targetClass = null;
        this.interceptorInstance = null;

        this.interceptions = getInterceptedMethods(interceptorClass);
        this.classesToRetransform = findHookTargets(targets).toArray(Class[]::new);
    }

    private HashSet<Class<?>> findHookTargets(Object... targets) {
        var methodsToHook = new HashMap<>(interceptions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> new HashMap<>(e.getValue()))));
        var classesToHook = new HashSet<Class<?>>();

        for (var target: targets) {
            if (target == null) {
                continue;
            }
            for (Class<?> checkClass = target instanceof Class ? (Class<?>)target : target.getClass(); checkClass != null && !methodsToHook.isEmpty(); checkClass = checkClass.getSuperclass()) {
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
        return classesToHook;
    }

    public void revert(Instrumentation inst) {
        methodsToHookByClass.clear();
        retransform(inst);
        inst.removeTransformer(this);
        interceptorInstance = null; // let GC remove it
    }

    public void apply(Instrumentation inst) {
        inst.addTransformer(this, true);

        retransform(inst);
    }

    private void retransform(Instrumentation inst) {
        try {
            inst.retransformClasses(classesToRetransform);
        } catch (UnmodifiableClassException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        var methodsToHook = methodsToHookByClass.get(className);
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
                        return new InterceptionAdapter(mv, access, name, descriptor, interceptMethod, cr.getClassName());
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
        final boolean isStatic;
        final String methodKey;
        final String className;
        public InterceptionAdapter(MethodVisitor mv, int access, String name, String descriptor, Method interceptMethod, String className) {
            super(Opcodes.ASM9, mv, access, name, descriptor);
            this.interceptMethod = interceptMethod;
            this.isStatic = Modifier.isStatic(access);
            this.methodKey = name + descriptor;
            this.className = className;
        }
        @Override
        public void visitCode() {
            super.visitCode();
            var start = new Label();
            var end = new Label();
            var handler = new Label();
            visitTryCatchBlock(start, end, handler, Type.getType(NoInterceptionException.class).getInternalName());

            mark(start);
            if (isStatic) {
                push(Type.getObjectType(className));
            } else {
                loadThis();
            }
            if (isStaticInterceptor) {
                // static interceptors have the same arguments as the base method
                loadArgs();
                invokeStatic(Type.getType(interceptorClass), interceptMethod);
            } else {
                // dynamic interceptors need extra argument for which class declared this method...
                push(Type.getType(targetClass));
                // ... which method is getting intercepted...
                push(this.methodKey);
                // ...and to collect their arguments into Object[] form
                loadArgArray();
                invokeStatic(Type.getType(Interceptor.class), interceptMethod);
            }
            returnValue();
            mark(end);
            mark(handler);
            pop();
            // then continue with the usual code
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            maxStack = Math.max(maxStack, (Type.getArgumentsAndReturnSizes(interceptMethod.getDescriptor()) >> 2) + (isStaticInterceptor ? 1 : 6));
            super.visitMaxs(maxStack, maxLocals);
        }
    }

    private Map<Method, Map<Class<?>, Method>> getInterceptedMethods(Class<?> interceptorClass) {
        Map<Method, Map<Class<?>, Method>> methods = new HashMap<>();
        for (var reflectMethod: interceptorClass.getMethods()) {
            int mod = reflectMethod.getModifiers();
            if (Modifier.isStatic(mod) == isStaticInterceptor && reflectMethod.getDeclaringClass() != Object.class) {
                var hookMethod = Method.getMethod(reflectMethod);
                var hookReturnType = hookMethod.getReturnType();
                var hookArgTypes = hookMethod.getArgumentTypes();
                var baseMethod = new Method(hookMethod.getName(), hookMethod.getReturnType(),
                                            Arrays.copyOfRange(hookArgTypes, 1, hookArgTypes.length));
                var targetParameterClass = reflectMethod.getParameterTypes()[0];
                Class<?> targetClass = targetParameterClass;
                if (targetParameterClass == Class.class) {
                    // if we have a generic type for this Class<?>, use it instead
                    var genericType = reflectMethod.getGenericParameterTypes()[0];
                    if (genericType != null && genericType instanceof ParameterizedType) {
                        var typeParam = ((ParameterizedType) genericType).getActualTypeArguments()[0];
                        if (typeParam instanceof ParameterizedType) {
                            typeParam = ((ParameterizedType)typeParam).getRawType();
                        }
                        if (typeParam instanceof Class) {
                            targetClass = (Class<?>)typeParam;
                        }
                    }
                }
                if (!isStaticInterceptor) {
                    // use the generic hook method for this return type
                    switch (hookReturnType.getSort()) {
                        case Type.VOID:
                            hookMethod = Method.getMethod("void callInterceptMethodVoid(Object, Class, String, Object[])");
                            break;
                        case Type.INT:
                            hookMethod = Method.getMethod("int callInterceptMethodInt(Object, Class, String, Object[])");
                            break;
                        case Type.BOOLEAN:
                            hookMethod = Method.getMethod("boolean callInterceptMethodBoolean(Object, Class, String, Object[])");
                            break;
                        case Type.OBJECT:
                        case Type.ARRAY:
                            hookMethod = Method.getMethod("Object callInterceptMethodRef(Object, Class, String, Object[])");
                            break;
                    }
                }
                methods.merge(baseMethod, Map.of(targetClass, hookMethod), (a, b) -> {
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
