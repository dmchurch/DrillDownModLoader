package de.dakror.modding.asm.augmentation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import de.dakror.modding.asm.Util;

class AugmentationResultChecker extends ClassVisitor {
    private final Set<String> visitedMembers = new HashSet<>();
    private String myName;
    public AugmentationResultChecker(ClassVisitor cv) {
        super(AugmentationVisitor.ASM9, cv);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.myName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    private Map<String, String> visitedInnerClasses = new HashMap<>();
    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        var oldOuterName = visitedInnerClasses.put(name, outerName);
        if (oldOuterName == null) {
            super.visitInnerClass(name, outerName, innerName, access);
        } else if (!oldOuterName.equals(outerName)) {
            throw new RuntimeException("discrepancy: trying to define outer class of "+name+" to "+outerName+" when it was previously "+oldOuterName);
        }
    }

    private void visitMember(String name) {
        if (!visitedMembers.add(name)) {
            throw new RuntimeException("Class "+myName+" tried to duplicate member "+name);
        }
    }

    @Override
    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
        visitMember(name);
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        visitMember(name + Util.methodDescArgs(descriptor));
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }
}