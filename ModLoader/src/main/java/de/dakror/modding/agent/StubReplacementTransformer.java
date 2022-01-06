package de.dakror.modding.agent;

import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM9;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.util.CheckClassAdapter;

class StubReplacementTransformer implements ClassFileTransformer {
    private final Predicate<String> classesToTransform;
    private final ClassNameMapper classNameMapper;
    private final Map<String, Class<?>> targetClassesByName = new HashMap<>();

    public StubReplacementTransformer(String classToTransform, Class<?> stubTarget) {
        this(classToTransform::equals, stubTarget);
    }
    public StubReplacementTransformer(Predicate<String> classesToTransform, Class<?> stubTarget) {
        this(classesToTransform, stubTarget, StubReplacementTransformer::simpleNameMatcher);
    }
    public StubReplacementTransformer(Predicate<String> classesToTransform, Class<?> stubTarget, Function<Class<?>, ClassNameMapper> classNameMapperFactory) {
        this(classesToTransform, stubTarget, classNameMapperFactory.apply(stubTarget));
    }
    public StubReplacementTransformer(Predicate<String> classesToTransform, Class<?> stubTarget, ClassNameMapper classNameMapper) {
        this.classesToTransform = classesToTransform;
        this.classNameMapper = classNameMapper;
        collectTargetClasses(stubTarget);
    }

    private void collectTargetClasses(Class<?> targetClass) {
        targetClassesByName.put(Type.getInternalName(targetClass), targetClass);
        for (var sub: targetClass.getDeclaredClasses()) {
            collectTargetClasses(sub);
        }
    }

    @FunctionalInterface
    public static interface ClassNameMapper {
        String mapClassName(String name);
    }

    public static ClassNameMapper simpleNameMatcher(Class<?> stubTarget) {
        var stubTargetName = Type.getInternalName(stubTarget);
        var stubName = stubTarget.getSimpleName();
        return className -> {
            var localName = className.substring(className.lastIndexOf('/')+1);
            if (localName.equals(stubName)) {
                return stubTargetName;
            } else if (localName.startsWith(stubName + "$")) {
                return stubTargetName + localName.substring(stubName.length());
            }
            return null;
        };
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (!classesToTransform.test(className)) {
            return null;
        }
        var cr = new ClassReader(classfileBuffer);
        var cw = new ClassWriter(cr, 0);
        var remapper = new Remapper() {
            @Override public String map(String internalName) {
                var mappedName = classNameMapper.mapClassName(internalName);
                return mappedName != null ? mappedName : internalName;
            }
        };
        cr.accept(new ClassRemapper(new ClassVisitor(ASM9, new CheckClassAdapter(cw, true)) {
            private final Set<Method> intfMethods = new HashSet<>();
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                if (interfaces != null) {
                    for (var ifName: interfaces) {
                        var targetClass = targetClassesByName.get(ifName);
                        if (targetClass != null) {
                            intfMethods.addAll(getInterfaceMethods(targetClass));
                        }
                    }
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                var method = new Method(name, descriptor);
                if (intfMethods.contains(method)) {
                    if ((access & ACC_STATIC) != 0) {
                        throw new IncompatibleClassChangeError("Interface method "+method+" in "+className+" must not be static");
                    }
                    access = (access & ~(ACC_PROTECTED|ACC_PRIVATE)) | ACC_PUBLIC;
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, remapper), 0);

        return cw.toByteArray();
    }

    private static Set<Method> getInterfaceMethods(Class<?> intf) {
        return Arrays.stream(intf.getMethods())
                    .filter(m -> (m.getModifiers() & (ACC_STATIC | ACC_PUBLIC)) == ACC_PUBLIC)
                    .map(Method::getMethod)
                    .collect(Collectors.toSet());
    }
}