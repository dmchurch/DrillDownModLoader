package de.dakror.modding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dakror.modding.ModLoader.DebugContext;
import javassist.CannotCompileException;
import javassist.ClassMap;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMember;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.Bytecode;
import javassist.bytecode.Descriptor;
import javassist.bytecode.MethodInfo;

// package-local, not public, so as not to offer confusion with the @AugmentationClass attribute
class ClassAugmentation implements ModLoader.IClassAugmentation, ModLoader.IClassMod<CtClass, ClassPool> {
    // maps all classes in an aug chain to the AugmentationChain object
    private Map<String, AugmentationChain> augmentationChains = new HashMap<>();

    public void augmentClass(String baseClass, String augmentationClass) {
        assert baseClass != augmentationClass;
        var augChain = augmentationChains.getOrDefault(baseClass, new AugmentationChain(baseClass));
        var subChain = augmentationChains.get(augmentationClass);

        // augChain.addAugmentation() also adds the item to this.augmentationChains, so we need to fetch subChain beforehand
        if (augChain.addAugmentation(augmentationClass) && subChain != null && subChain != augChain) {
            // this aug class had a chain of its own, add all its augments to the base, forcing them to the end of the list
            subChain.augmentations.forEach(subAug -> augChain.addAugmentation(subAug, true));
        }
    }

    @Override
    public boolean hooksClass(String className) {
        return augmentationChains.containsKey(className);
    }

    @Override
    public CtClass redefineClass(String className, CtClass ctClass, ClassPool classPool)
            throws ClassNotFoundException {
        try (var ctx = new DebugContext("ClassAugmentation#redefineClass "+className)) {
            return augmentationChains.get(className).redefineClass(className, ctClass, classPool);
            /*
        var baseClass = augmentedClasses.containsKey(className) ? className : augmentationClasses.get(className);
        var augList = augmentedClasses.get(baseClass);
        var staticsMap = new HashMap<String, String>(); // mapping static field/method name to latest class that declares it
        if (className == baseClass) {
            // the base class gets inherited by the first augmentation
            ModLoader.debugln("base class inherit by "+augList.get(0)+" (as "+className+"__Augmented)");
            recordStatics(ctClass, staticsMap);
            // augmentationLoaders.putIfAbsent(augList.get(0), new SubLoader(loader, className, augList.get(0), origClass));
            // the base class redirects to the latest augmentation
            var lastAug = augList.get(augList.size()-1);
            ModLoader.debugln("base class redirect to "+lastAug);
            try {
                return classPool.makeClass(className, classPool.get(lastAug));
                // return synthLoader.defineSynthClass(className, stubClass.toBytecode(), origClass.getProtectionDomain());
            } catch (NotFoundException e) {
                System.err.println("Error creating or defining stub class for "+className+": "+e.getMessage());
                throw new ClassNotFoundException(e.getMessage());
            }
        }
        var augIndex = augList.indexOf(className);
        assert augIndex >= 0;
        // each augmentation references the previous; the first references the base class under its __Augmented name
        var augBase = augIndex == 0 ? baseClass + "__Augmented" : augList.get(augIndex - 1);
        ModLoader.debugln("rewriting augmentation class "+className+" superclass from "+baseClass+" to "+augBase);
        ctClass.getClassFile().renameClass(baseClass, augBase);
        return ctClass;
        // // each augmentation gets inherited by the next
        // // if (augIndex < augList.size() - 1) {
        // //     augmentationLoaders.putIfAbsent(augList.get(augIndex+1), new SubLoader(loader, baseClass, augList.get(augIndex+1), origClass));
        // // }
        // // if this class's base hasn't been loaded yet, do that
        // if (!augmentationLoaders.containsKey(className)) {
        //     loader.loadClass(augIndex > 0 ? augList.get(augIndex - 1) : baseClass);
        // }
        // var augLoader = augmentationLoaders.get(className);
        // if (augLoader == null) {
        //     System.err.println("No augmentation loader for "+className);
        //     throw new ClassNotFoundException();
        // }
        // augLoader.setOriginalSubClass(origClass);
        // return augLoader.loadClass(className);
        */
        }
    }

    protected static interface IMemberVisitor {
        void visitMember(CtMember member, CtMember baseMember) throws CannotCompileException, NotFoundException, IOException;
    }

    protected class AugmentationChain {
        public String baseName;
        public String augmentedName;
        public List<String> augmentations = new ArrayList<>();
        public boolean compiled = false;

        private Map<String, CtClass> compiledClasses = null;

        public AugmentationChain(String baseClass) {
            baseName = baseClass;
            augmentedName = baseName + "__Augmented";
            augmentationChains.put(baseName, this);
            augmentationChains.put(augmentedName, this);
        }
        public boolean addAugmentation(String augmentation) {
            return addAugmentation(augmentation, false);
        }
        public boolean addAugmentation(String augmentation, boolean force) {
            if (compiled) {
                throw new RuntimeException("Tried to add augmentation "+augmentation+" to chain for "+baseName+", which is already compiled");
            }
            if (augmentations.contains(augmentation)) {
                if (force) {
                    augmentations.remove(augmentation);
                } else {
                    return false;
                }
            }
            augmentations.add(augmentation);
            augmentationChains.put(augmentation, this);
            return true;
        }

        public boolean compile(CtClass ctClass, ClassPool classPool) throws NotFoundException, CannotCompileException, IOException {
            if (compiled) return true;

            List<CtClass> classes = compilerFor(ctClass, classPool).compile();
            if (classes == null) {
                return false;
            }

            compiledClasses = new HashMap<>();
            classes.forEach(c -> compiledClasses.put(c.getName(), c));

            compiled = true;
            return true;
        }

        public CtClass redefineClass(String className, CtClass ctClass, ClassPool classPool) throws ClassNotFoundException {
            try {
                compile(ctClass, classPool);
                return compiledClasses.get(className);
            } catch (NotFoundException|CannotCompileException|IOException e) {
                throw new ClassNotFoundException(e.getMessage(), e);
            }
        }

        protected Compiler compilerFor(CtClass targetClass, ClassPool classPool) {
            Compiler compiler = new RenamingCompiler();
            compiler.init(targetClass, classPool);
            return compiler;
        }

        abstract protected class Compiler implements IMemberVisitor {
            protected CtClass targetClass;
            protected CtClass visitingClass;
            protected ClassPool classPool;

            // map constructor signature to latest inheritable iteration of that sig
            protected Map<String, CtConstructor> constructors = new HashMap<>();
            // map name.signature to latest inheritable version
            protected Map<String, CtMethod> staticMethods = new HashMap<>();
            protected Map<String, CtMethod> instanceMethods = new HashMap<>();
            // map inheritable field name to field definition
            protected Map<String, CtField> staticFields = new HashMap<>();
            protected Map<String, CtField> instanceFields = new HashMap<>();

            public void init(CtClass targetClass, ClassPool classPool) {
                this.targetClass = targetClass;
                this.classPool = classPool;
            }

            abstract public List<CtClass> compile() throws NotFoundException, CannotCompileException, IOException;

            public <M extends CtMember> M compileMemberChecks(String key, M member, Map<String, M> instanceMap, Map<String, M> staticMap) throws CannotCompileException {
                var mod = member.getModifiers();
                var isStatic = Modifier.isStatic(mod);
                var map = isStatic ? staticMap : instanceMap;
                var baseMember = map.get(key);
                if (Modifier.isPrivate(mod) && baseMember == null) {
                    return null;
                }
                if (baseMember != null) {
                    var baseMod = baseMember.getModifiers();
                    if (Modifier.isPrivate(mod)
                            || Modifier.isProtected(mod) && !Modifier.isProtected(baseMod)
                            || Modifier.isPackage(mod) && Modifier.isPublic(baseMod)) {
                        var msg = "Augmentation member "+member+" attempts to narrow visibility from original "+baseMember;
                        throw new CannotCompileException(msg);
                    }
                }
                map.put(key, member);
                return baseMember;
            }

            @Override
            public void visitMember(CtMember member, CtMember baseMember) throws CannotCompileException, NotFoundException, IOException {
                if (member instanceof CtField) {
                    visitField((CtField)member, (CtField)baseMember);
                } else if (member instanceof CtConstructor) {
                    visitCtor((CtConstructor)member, (CtConstructor)baseMember);
                } else if (member instanceof CtMethod) {
                    visitMethod((CtMethod)member, (CtMethod)baseMember);
                }
            }

            protected void visitField(CtField field, CtField baseField) throws CannotCompileException, NotFoundException, IOException {
                if (baseField != null && baseField.getType() != field.getType()) {
                    throw new CannotCompileException("Augmentation field "+visitingClass.getName()+"#"+field.getName()+" type "+field.getType()+" differs from base type "+baseField.getType());
                }
            }

            protected void visitCtor(CtConstructor ctor, CtConstructor baseCtor) throws CannotCompileException, NotFoundException, IOException {
            }

            protected void visitMethod(CtMethod method, CtMethod baseMethod) throws CannotCompileException, NotFoundException, IOException {
                if (baseMethod != null && baseMethod.getReturnType() != method.getReturnType()) {
                    throw new CannotCompileException("Augmentation method "+method.getLongName()+" return type "+method.getReturnType()+" differs from base return type "+baseMethod.getReturnType());
                }
            }

            protected List<CtMember> visitClassMembers(CtClass ctClass) throws CannotCompileException, NotFoundException, IOException {
                return visitClassMembers(ctClass, this);
            }

            protected List<CtMember> visitClassMembers(CtClass ctClass, IMemberVisitor visitor) throws CannotCompileException, NotFoundException, IOException {
                visitingClass = ctClass;
                List<CtMember> members = new ArrayList<>();
                ClassMap sigmap = new ClassMap();
                var baseClass = classPool.get(baseName);
                sigmap.put(ctClass, baseClass);
                sigmap.put(ctClass.getSuperclass(), baseClass);
                for (var field: ctClass.getDeclaredFields()) {
                    var baseField = compileMemberChecks(field.getName(), field, instanceFields, staticFields);
                    members.add(field);
                    visitor.visitMember(field, baseField);
                }
                for (var ctor: ctClass.getDeclaredConstructors()) {
                    var key = ctor.getName().equals(MethodInfo.nameClinit) ? "" : Descriptor.rename(ctor.getSignature(), sigmap);
                    var baseCtor = compileMemberChecks(key, ctor, constructors, constructors);
                    members.add(ctor);
                    visitor.visitMember(ctor, baseCtor);
                }
                for (var method: ctClass.getDeclaredMethods()) {
                    var key = method.getName() + "." + Descriptor.rename(method.getSignature(), sigmap);
                    var baseMethod = compileMemberChecks(key, method, instanceMethods, staticMethods);
                    members.add(method);
                    visitor.visitMember(method, baseMethod);
                }
                return members;
            }
        }

        // The RenamingCompiler compiles an AugmentationChain by mutating the base class in-place, renaming methods and fields that have
        // been overridden in augmentations.
        protected class RenamingCompiler extends Compiler {
            protected CodeConverter conv = new CodeConverter();
            protected CodeConverter methodConv = new CodeConverter();
            public List<CtClass> compile() throws NotFoundException, CannotCompileException, IOException {
                if (targetClass.getName() != baseName) return null; // don't compile until we're looking at the base class
                visitClassMembers(targetClass, (m,b)->{}); // register all base members in the maps, don't call visitors

                for (var augName: augmentations) {
                    var augClass = classPool.get(augName);
                    visitClassMembers(augClass);
                }
                targetClass.instrument(conv);
                return List.of(targetClass);
            }

            protected String renamed(CtMember member) {
                var declClass = member.getDeclaringClass();
                return declClass.getName().replace(".", ":")+"#"+member.getName();
            }

            @Override
            protected void visitField(CtField field, CtField baseField) throws CannotCompileException, NotFoundException, IOException {
                super.visitField(field, baseField);
                var newName = renamed(field);
                var newField = new CtField(field, targetClass);
                methodConv.redirectFieldAccess(newField, targetClass, newName);
                newField.setName(newName);
                targetClass.addField(newField);
                conv.redirectFieldAccess(field, targetClass, newName);
            }

            @Override
            protected void visitCtor(CtConstructor ctor, CtConstructor baseCtor) throws CannotCompileException, NotFoundException, IOException {
                super.visitCtor(ctor, baseCtor);
            }

            @Override
            protected void visitMethod(CtMethod method, CtMethod baseMethod) throws CannotCompileException, NotFoundException, IOException {
                super.visitMethod(method, baseMethod);
                var newMethod = new CtMethod(method, targetClass, null);
                newMethod.instrument(methodConv);
                if (baseMethod == null) {
                    var newName = renamed(method);
                }
            }
        }

        // The InheritingCompiler compiles an AugmentationChain by transforming it into an inheritance of classes Base->Aug1->Aug2->Stub,
        // where Stub is a synthetic class that (a) holds all static fields, and (b) redirects static method calls appropriately.
        protected class InheritingCompiler extends Compiler {
            protected CtClass stubClass;
            protected CtClass oldSuperclass;
            protected CodeConverter conv = new CodeConverter();

            private void normalizeParamTypes(CtBehavior behavior, CtClass stubClass) throws NotFoundException, CannotCompileException {
                var methodInfo = behavior.getMethodInfo();
                var descriptor = methodInfo.getDescriptor();
                descriptor = Descriptor.rename(descriptor, behavior.getDeclaringClass().getName(), stubClass.getName());
                descriptor = Descriptor.rename(descriptor, behavior.getDeclaringClass().getClassFile2().getSuperclass(), stubClass.getName());
                methodInfo.setDescriptor(descriptor);
            }

            @Override
            protected void visitField(CtField field, CtField baseField) throws CannotCompileException, NotFoundException, IOException {
                var fieldType = field.getType();
                if (fieldType == visitingClass || fieldType == visitingClass.getSuperclass()) {
                    // update the type itself to the stub-class type
                    field.setType(stubClass);
                    // now update any fieldref in the const pool to fix code
                    // var cp = ctClass.getClassFile().getConstPool();
                }
                super.visitField(field, baseField);
                if (Modifier.isStatic(field.getModifiers()) && !Modifier.isPrivate(field.getModifiers())) {
                    // all static fields get moved to the stub
                    visitingClass.removeField(field);
                    conv.redirectFieldAccess(field, stubClass, field.getName());
                }
            }

            @Override
            protected void visitCtor(CtConstructor ctor, CtConstructor baseCtor) throws CannotCompileException, NotFoundException, IOException {
                super.visitCtor(ctor, baseCtor);
                normalizeParamTypes(ctor, stubClass);
            }

            @Override
            protected void visitMethod(CtMethod method, CtMethod baseMethod) throws CannotCompileException, NotFoundException, IOException {
                super.visitMethod(method, baseMethod);
                if (visitingClass.getName() != augmentedName) {
                    // augmented classes don't need method redirection, but augmentation classes do

                    // redirect any prior super.method() calls to the new super
                    var oldSuperMethod = copyMethodInfo(method, oldSuperclass);
                    var newSuperMethod = copyMethodInfo(method, visitingClass.getSuperclass());
                    conv.redirectMethodCall(oldSuperMethod, newSuperMethod);
                }
            }

            protected CtClass compileClass(CtClass ctClass, CtClass superclass) throws CannotCompileException, NotFoundException, IOException {
                oldSuperclass = ctClass.getSuperclass();
                // setSuperclass will alter all super() calls in constructors but not super.methodName() calls anywhere
                ctClass.setSuperclass(superclass);

                visitClassMembers(ctClass);

                ctClass.instrument(conv);
                return ctClass;
            }

            private CtMethod copyMethodInfo(CtMethod method, CtClass declaringClass) throws NotFoundException {
                return new CtMethod(method.getReturnType(), method.getName(), method.getParameterTypes(), declaringClass);
            }
    
            private CtClass compileStubClass(CtClass superclass) throws CannotCompileException, NotFoundException, IOException {
                for (var field: staticFields.values()) {
                    stubClass.addField(new CtField(field, stubClass));
                }
                for (var method: staticMethods.values()) {
                    var stubMethod = copyMethodInfo(method, stubClass);
                    var methodInfo = stubMethod.getMethodInfo();
                    var paramTypes = method.getParameterTypes();
                    var bc = new Bytecode(methodInfo.getConstPool(), paramTypes.length, 0);
                    bc.addLoadParameters(method.getParameterTypes(), 0);
                    bc.addInvokestatic(method.getDeclaringClass(), method.getName(), method.getSignature());
                    bc.addReturn(method.getReturnType());
                    methodInfo.setCodeAttribute(bc.toCodeAttribute());
                }
                for (var ctor: constructors.values()) {
                    var paramTypes = ctor.getParameterTypes();
                    var stubCtor = new CtConstructor(paramTypes, stubClass);
                    var methodInfo = stubCtor.getMethodInfo();
                    var bc = new Bytecode(methodInfo.getConstPool(), paramTypes.length+1, 0);
                    bc.addAload(0); // this
                    bc.addLoadParameters(paramTypes, 1);
                    bc.addInvokespecial(superclass, MethodInfo.nameInit, ctor.getSignature());
                    bc.addReturn(CtClass.voidType);
                    methodInfo.setCodeAttribute(bc.toCodeAttribute());
                }
                stubClass.setSuperclass(superclass);
                return stubClass;
            }
    
            public List<CtClass> compile() throws NotFoundException, CannotCompileException, IOException {
                var superclass = classPool.getAndRename(baseName, augmentedName);
                stubClass = classPool.makeClass(baseName, superclass.getSuperclass());

                List<CtClass> compiledClasses = new ArrayList<>();
                compiledClasses.add(compileClass(superclass, superclass.getSuperclass()));
    
                for (var augName: augmentations) {
                    var augClass = classPool.get(augName);
                    superclass = compileClass(augClass, superclass);
                    compiledClasses.add(superclass);
                }
    
                compiledClasses.add(compileStubClass(superclass));

                for (var ctClass: compiledClasses) {
                    // now, actually compile them
                    ctClass.toBytecode();
                    ctClass.freeze();
                }
                return compiledClasses;
            }
        }
    }
}