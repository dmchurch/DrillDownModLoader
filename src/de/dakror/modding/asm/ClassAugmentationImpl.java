package de.dakror.modding.asm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import de.dakror.modding.ClassAugmentationBase;

public class ClassAugmentationImpl extends ClassAugmentationBase<ClassVisitor, ClassReader> {
    protected ASMModLoader modLoader;

    public ClassAugmentationImpl(ASMModLoader modLoader) {
        this.modLoader = modLoader;
    }

    protected AugmentationChain newAugmentationChain(String className) {
        return new AugmentationChain(className);
    }

    @Override
    protected ClassVisitor redefineAffectedClass(String className, ClassVisitor classDef, ClassReader context, Map<String, String> renameMap) {
        return new ClassRemapper(classDef, new SimpleRemapper(renameMap));
    }

    protected class AugmentationChain extends ClassAugmentationBase<ClassVisitor, ClassReader>.AugmentationChain {
        protected String baseIntName;
        protected Map<String, String> innerClassRemaps = new HashMap<>();
        protected List<String> extraNestMembers = new ArrayList<>();

        public AugmentationChain(String baseClass) {
            super(baseClass);
            baseIntName = baseName.replace('.', '/');
        }

        @Override
        public boolean addAugmentation(String augmentation, boolean force) {
            if (!super.addAugmentation(augmentation, force)) {
                return false;
            }
            try {
                var scanner = modLoader.getScanner();
                var augIntName = augmentation.replace('.','/');
                innerClassRemaps.put(augIntName, baseIntName);
                recordInnerClasses(augIntName, baseIntName+"$"+mappedClassName(augIntName)+">");
                for (var remappedClass: innerClassRemaps.keySet()) {
                    for (var affectedClass: scanner.getIntReferencingClasses(remappedClass)) {
                        affectedClasses.get(Util.fromIntName(affectedClass)).putAll(innerClassRemaps);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        /** Creates a valid JVM identifier from an internal class name */
        protected String mappedClassName(String name) {
            return name.replace('/', '-').replace('$', '+');
        }

        /** Creates a valid JVM identifier from a class and member name */
        protected String mappedMemberName(String className, String memberName) {
            return mappedClassName(className) + "#" + memberName.replace('<', '{').replace('>', '}');
        }

        protected void recordInnerClasses(String outerIntName, String prefix) throws IOException {
            var reader = modLoader.newIntClassReader(outerIntName);
            reader.accept(
                new RecordInnerClassVisitor(outerIntName, prefix),
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        protected class RecordInnerClassVisitor extends ClassVisitor {
            private String outerIntName;
            private String prefix;

            public RecordInnerClassVisitor(String outerIntName, String prefix) {
                super(Opcodes.ASM9);
                this.outerIntName = outerIntName;
                this.prefix = prefix;
            }

            private String mapName(String origName) {
                if (outerIntName.equals(origName)) {
                    return baseIntName;
                } else if (!origName.startsWith(outerIntName+"$")) {
                    return null;
                }
                return prefix + origName.substring(outerIntName.length()+1);
            }

            @Override
            public void visitNestMember(String nestMember) {
                nestMember = mapName(nestMember);
                if (nestMember != null)
                    extraNestMembers.add(nestMember);
            }

            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                var mappedName = mapName(name);
                if (mappedName != null) {
                    innerClassRemaps.put(name, mappedName);
                    modLoader.replaceClass(mappedName.replace('/', '.'), name.replace('/','.')); // load the proper source file
                    augmentationChains.put(mappedName.replace('/', '.'), AugmentationChain.this);
                }
            }
        }

        public ClassVisitor redefineClass(String className, ClassVisitor nextVisitor, ClassReader reader) throws ClassNotFoundException {
            var origIntClassName = reader.getClassName();
            var intClassName = Util.toIntName(className);
            Map<String, String> remaps = new HashMap<>(affectedClasses.get(className));
            remaps.putAll(innerClassRemaps);
            SimpleRemapper remapper = new SimpleRemapper(remaps);
            if (intClassName.equals(innerClassRemaps.get(origIntClassName))) {
                return new ClassRemapper(nextVisitor, remapper);
            } else if (!className.equals(baseName)) {
                // the original versions of the classes can't be loaded (this is to guard against unexpected and confusing behavior)
                throw new ClassNotFoundException(className);
            }
            compiled = true; // don't allow any more augmentations after redefinition
            try {
                return new AugmentationVisitor(this, nextVisitor, reader, remaps, remapper);
            } catch (IOException e) {
                throw new ClassNotFoundException(className, e);
            }
        }
    }
}