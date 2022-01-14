package de.dakror.modding.asm.diff;

import static org.objectweb.asm.Opcodes.ASM9;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Modifier;
import java.util.*;

import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import de.dakror.modding.asm.Util;

public interface Node<This extends Node<This>> extends IndentingPrinter.Printable {
    public String getName();
    default public String getKey() { return getName(); }
    default boolean hasPublicName() {
        return getName() != null && getName().matches("^[a-zA-Z_]\\w*$");
    }
    default boolean strongMatchWith(This other) {
        return other != null && hasPublicName() && other.hasPublicName() && getKey().equals(other.getKey());
    }
    default boolean identicalTo(This other) {
        return false;
    }
    public String getLabel();

    @Override
    default void print(IndentingPrinter p) {
        p.printf("Node.%s: %s", getClass().getSimpleName(), getLabel());
        printDetails(p);
    }

    default public void printDetails(IndentingPrinter p) { }

    private static String accLabel(int access) {
        var label = Modifier.toString(access);
        return label.isBlank() ? "" : (label + " ");
    }
    
    public class Class extends ClassNode implements Node<Class> {
        static int ict = 0;
        public final WeakReference<Class> declaringClass;
        public WeakReference<Method> declaringMethod;
        public final ClassRef ref;
        public Class(ClassRef ref) {
            this(null, ref);
        }
        public Class(Class declaringClass, ClassRef ref) {
            super(ASM9);
            this.declaringClass = new WeakReference<>(declaringClass);
            this.ref = ref;
        }

        public static Node.Class of(ClassRef ref) throws IOException {
            return new Node.Class(ref).read();
        }

        private Class read() {
            if (name == null && ref != null) {
                try {
                    ref.getReader().accept(this, 0);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                for (var ac: anonClasses) {
                    ac.read();
                    if (ac.outerMethod != null) {
                        for (var m: methods()) {
                            if (m.name.equals(ac.outerMethod) && m.desc.equals(ac.outerMethodDesc)) {
                                m.innerClasses.add(ac);
                                ac.declaringMethod = new WeakReference<>(m);
                            }
                        }
                    }
                }
            }
            return this;
        }

        public static String simpleName(Type type) {
            return simpleName(type == null ? null : type.getClassName());
        }
        public static String simpleName(String className) {
            return className == null ? null : className.replaceAll(".*[/.]", "").replace('$','.');
        }

        @Override
        public String getName() {
            return ref.className != null ? simpleName(ref.className) : null;
        }
        @Override
        public String getLabel() {
            return accLabel(access & Modifier.classModifiers()) + Util.fromIntName(ref.className);
        }
        @Override
        public void printDetails(IndentingPrinter p) {
            try (var x = p.indent()) {
                p.indented(methods(), "Methods:");
                p.indented(fields(), "Fields:");
                p.indented(memberClasses(), "Classes:");
            }
        }
        public Method[] methods() {
            read();
            return methods.stream().map(this::methodOf).toArray(Method[]::new);
        }
        public Field[] fields() {
            read();
            return fields.stream().map(this::fieldOf).toArray(Field[]::new);
        }
        public List<Class> memberClasses = new ArrayList<>();
        public List<Class> anonClasses = new ArrayList<>();
        public Class[] memberClasses() {
            read();
            return memberClasses.toArray(Class[]::new);
        }
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            var method = new Method(this, access, name, descriptor, signature, exceptions);
            methods.add(method);
            return method;
        }
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            var field = new Field(this, access, name, descriptor, signature, value);
            fields.add(field);
            return field;
        }
        @Override
        public void visitInnerClass(String name, String outerName, String innerName, int access) {
            super.visitInnerClass(name, outerName, innerName, access);
            // System.out.println("ic: "+name+" "+outerName+" "+innerName);
            if (name.startsWith(this.name) && !name.equals(this.name)) {
                final List<Class> clist;
                if (innerName == null) {
                    clist = anonClasses;
                } else if (Objects.equals(outerName, this.name)) {
                    clist = memberClasses;
                } else {
                    return;
                }
                try {
                    clist.add(new Class(ref.resolve(name)));
                } catch (IOException|ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public boolean identicalTo(Class other) {
            try {
                return ref.identicalTo(other.ref);
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        public boolean hasPublicName() {
            return !ref.className.matches(".*\\$[0-9].*");
        }
        private Method methodOf(MethodNode m) {
            return Method.of(this, m);
        }
        private Field fieldOf(FieldNode f) {
            return Field.of(this, f);
        }
    }
    public class Method extends MethodNode implements Node<Method> {
        public final WeakReference<Node.Class> declarer;
        public final Type methodType;
        public final List<Node.Class> innerClasses = new ArrayList<>();
        public Method(Class declarer, MethodNode src) {
            this(declarer, src.access, src.name, src.desc, src.signature, src.exceptions.toArray(String[]::new));
            src.accept(this);
        }
        public Method(Class declarer, int access, String name, String descriptor, String signature, String[] exceptions) {
            super(ASM9, access, name, descriptor, signature, exceptions);
            this.declarer = new WeakReference<>(declarer);
            methodType = Type.getMethodType(descriptor);
        }
        public static Method of(Class declarer, MethodNode node) {
            if (node instanceof Method) {
                return (Method)node;
            }
            return new Method(declarer, node);
        }
        public Node.Class[] innerClasses() {
            return innerClasses.toArray(Node.Class[]::new);
        }
        @Override
        public boolean hasPublicName() {
            return Node.super.hasPublicName() || name.equals("<init>") || name.equals("<clinit>");
        }
        @Override
        public String getName() {
            return name;
        }
        @Override
        public String getLabel() {
            var sb = new StringBuilder(name.length() + desc.length());
            sb.append(accLabel(access & Modifier.methodModifiers()))
              .append(Class.simpleName(methodType.getReturnType())).append(' ')
              .append(name).append('(');
            var argTypes = methodType.getArgumentTypes();
            var lvNames = new String[maxLocals];
            LabelNode firstLabel = null;
            for (var i: instructions) {
                if (i instanceof LabelNode) {
                    firstLabel = (LabelNode)i;
                    break;
                } else if (i.getOpcode() != -1) {
                    break;
                }
            }
            for (var lv: localVariables) {
                if (lv.start == firstLabel) {
                    lvNames[lv.index] = lv.name;
                }
            }
            int lvidx = Modifier.isStatic(access) ? 0 : 1;
            for (int i = 0; i < argTypes.length; i++) {
                var t = argTypes[i];
                var p = parameters != null && parameters.size() > i ? parameters.get(i) : null;
                var lv = lvNames[lvidx];
                lvidx += t.getSize();
                
                if (i > 0) {
                    sb.append(", ");
                }
                if (p != null) {
                    sb.append(accLabel(p.access));
                }
                sb.append(Class.simpleName(t));
                if (lv != null) {
                    sb.append(' ').append(lv);
                } else if (p != null) {
                    sb.append(' ').append(p.name);
                }
            }
            sb.append(')');
            return sb.toString();
        }
        @Override
        public String getKey() {
            return name + Util.methodDescArgs(desc);
        }
        @Override
        public void printDetails(IndentingPrinter p) {
            try (var x = p.indent()) {
                p.indented(innerClasses, "Inner classes:");
            }
        }
    }
    public class Field extends FieldNode implements Node<Field> {
        public final WeakReference<Node.Class> declarer;
        public Field(Class declarer, FieldNode src) {
            this(declarer, src.access, src.name, src.desc, src.signature, src.value);
        }
        public Field(Class declarer, int access, String name, String descriptor, String signature, Object value) {
            super(ASM9, access, name, descriptor, signature, value);
            this.declarer = new WeakReference<>(declarer);
        }
        @Override
        public String getName() {
            return name;
        }
        @Override
        public String getLabel() {
            return accLabel(access & Modifier.fieldModifiers()) + Class.simpleName(Type.getType(desc)) + " " + name;
        }
        @Override
        public boolean identicalTo(Field other) {
            return access == other.access && name.equals(other.name) && desc.equals(other.desc);
        }
        public static Field of(Class declarer, FieldNode node) {
            if (node instanceof Field) {
                return (Field)node;
            }
            return new Field(declarer, node);
        }
    }
}
