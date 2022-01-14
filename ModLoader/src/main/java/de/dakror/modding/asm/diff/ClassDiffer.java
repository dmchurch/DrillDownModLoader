package de.dakror.modding.asm.diff;

import java.util.*;
import java.io.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

public class ClassDiffer extends Differ<ClassDiffer, Node.Class> {
    final MatchGroup<Node.Method, MethodDiffer> methods;
    final MatchGroup<Node.Field, FieldDiffer> fields;
    final MatchGroup<Node.Class, ClassDiffer> classes;

    public ClassDiffer(ClassRef lRef, ClassRef rRef) throws IOException {
        this(Node.Class.of(lRef), Node.Class.of(rRef));
    }

    public ClassDiffer(Node.Class left, Node.Class right) {
        super(left, right);
        methods = new MatchGroup<>("Methods", left.methods(), right.methods(), MethodDiffer::new);
        fields = new MatchGroup<>("Fields", left.fields(), right.fields(), FieldDiffer::new);
        classes = new MatchGroup<>("Classes", left.memberClasses(), right.memberClasses(), ClassDiffer::new);
    }

    public static void main(String[] args) throws Exception {
        final ClassRef lRef, rRef;
        if (args.length == 2) {
            lRef = ClassRef.of(args[0]);
            rRef = ClassRef.of(args[1]);
        } else if (args.length == 3) {
            lRef = ClassRef.of(args[0], args[2]);
            rRef = ClassRef.of(args[1], args[2]);
        } else {
            System.out.println("Usage: ClassDiffer class1 class2");
            System.exit(1);
            return;
        }
        System.out.format("Diff classes:\n---%s\n+++%s\n", lRef, rRef);
        try {
            var diff = new ClassDiffer(lRef, rRef);
            diff.print();
        } catch (Throwable e) {
            System.err.print("error: ");
            e.printStackTrace();
            System.exit(0);
        }
    }

    @Override
    public ClassDiffer newDiffer(Node.Class nodeLeft, Node.Class nodeRight) {
        return new ClassDiffer(nodeLeft, nodeRight);
    }

    @Override
    protected void printDetails(IndentingPrinter p) {
        p.print(fields);
        p.print(methods);
        p.print(classes);
    }
}
