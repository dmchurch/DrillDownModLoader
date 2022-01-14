package de.dakror.modding.asm.diff;

import java.io.*;
import java.util.Objects;
import java.util.function.*;

abstract public class Differ<This extends Differ<This, TNode>, TNode extends Node<TNode>> implements IndentingPrinter.Printable {
    abstract public This newDiffer(TNode nodeLeft, TNode nodeRight);
    abstract protected void printDetails(IndentingPrinter p);

    public final TNode left;
    public final TNode right;

    protected String delimiter = "/";

    protected This newDiffer(TNode nodeLeft, TNode nodeRight, BiFunction<TNode, TNode, This> newDifferFunc) {
        return newDifferFunc.apply(nodeLeft, nodeRight);
    }

    protected Differ(Supplier<TNode> nodeSupplier) {
        this(nodeSupplier.get(), nodeSupplier.get());
    }

    protected Differ(TNode left, TNode right) {
        this.left = left;
        this.right = right;
    }

    public String getKey() {
        return mergeStrings(left.getKey(), right.getKey(), delimiter);
    }

    public String getName() {
        return mergeStrings(left.getName(), right.getName(), " vs ");
    }

    public String getLabel() {
        var label = mergeStrings(left.getLabel(), right.getLabel(), " vs ");
        if (label != null) {
            return label;
        }
        return getName();
    }

    private static String mergeStrings(String leftStr, String rightStr, String delimiter) {
        if (Objects.equals(leftStr, rightStr) || rightStr == null) {
            return leftStr;
        } else if (leftStr == null) {
            return rightStr;
        } else {
            return leftStr + delimiter + rightStr;
        }
    }

    public void print(IndentingPrinter p) {
        try (var x = p.indent("%s%s: %s\n", getClass().getSimpleName(), left.identicalTo(right) ? " (identical)" : "", getLabel())) {
            printDetails(p);
        }
    }

}
