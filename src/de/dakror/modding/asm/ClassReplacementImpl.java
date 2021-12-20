package de.dakror.modding.asm;

import java.io.IOException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import de.dakror.modding.ClassReplacementBase;
import de.dakror.modding.ModLoader;

public class ClassReplacementImpl extends ClassReplacementBase<ClassVisitor, ClassReader> {
    public ClassVisitor redefineClass(String className, ClassVisitor visitor, ClassReader reader) throws ClassNotFoundException {
        return new ClassRemapper(
            visitor,
            new SimpleRemapper(
                replacedClasses.get(className).replace('.','/'),
                className.replace('.', '/')
            )
        );
    }

    public class ReaderReplacement implements ModLoader.IClassMod.And<ClassReader, ASMModLoader> {
        public ClassReader redefineClass(String className, ClassReader reader, ASMModLoader modLoader) throws ClassNotFoundException {
            try {
                return modLoader.newClassReader(replacedClasses.get(className));
            } catch (IOException e) {
                throw new ClassNotFoundException(e.getMessage());
            }
        }
    }
}