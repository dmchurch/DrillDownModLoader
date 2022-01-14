package de.dakror.modding.asm.diff;

import java.util.*;
import java.util.function.*;

import de.dakror.modding.HashSetMap;

public class MatchGroup<TNode extends Node<TNode>, TDiffer extends Differ<TDiffer, TNode>> implements IndentingPrinter.Printable {
    public final HashSetMap<String, TDiffer> matched = new HashSetMap<>(TDiffer::getKey);
    public final HashSetMap<String, TNode> left;
    public final HashSetMap<String, TNode> right;
    public String title;

    private final BiFunction<TNode, TNode, TDiffer> newDiffer;
    public MatchGroup(TNode[] left, TNode[] right, BiFunction<TNode, TNode, TDiffer> newDiffer) {
        this(null, left, right, newDiffer);
    }
    public MatchGroup(String title, TNode[] left, TNode[] right, BiFunction<TNode, TNode, TDiffer> newDiffer) {
        this.title = title;
        this.newDiffer = newDiffer;
        this.left = new HashSetMap<>(left.length, TNode::getKey);
        this.right = new HashSetMap<>(right.length, TNode::getKey);

        for (TNode l: left) {
            this.left.add(l);
        }
        for (TNode r: right) {
            this.right.add(r);
            TNode l = this.left.find(r);
            if (r.strongMatchWith(l)) {
                match(l, r);
                continue;
            }
            for (TNode ll: this.left.values()) {
                if (r.identicalTo(ll)) {
                    match(ll, r);
                    break;
                }
            }
        }
    }

    public TDiffer match(TNode leftNode, TNode rightNode) {
        leftNode = left.removeElement(leftNode);
        rightNode = right.removeElement(rightNode);
        if (leftNode != null && rightNode != null) {
            var differ = newDiffer.apply(leftNode, rightNode);
            matched.add(differ);
            return differ;
        } else if (leftNode != null) {
            left.add(leftNode);
        } else if (rightNode != null) {
            right.add(rightNode);
        }
        return null;
    }

    public TDiffer match(String lKey, String rKey) {
        return match(left.get(lKey), right.get(rKey));
    }

    public boolean isEmpty() {
        return matched.isEmpty() && left.isEmpty() && right.isEmpty();
    }

    public int size() {
        return matched.size() + Math.max(left.size(), right.size());
    }

    public void printMatched(IndentingPrinter p) {
        p.indented(matched.values(), "%s (matched):", title);
    }

    public void printLeft(IndentingPrinter p) {
        p.indented(left.values(), "%s (left):", title);
    }

    public void printRight(IndentingPrinter p) {
        p.indented(right.values(), "%s (right):", title);
    }

    public void print(IndentingPrinter p) {
        if (isEmpty()) return;
        printMatched(p);
        printLeft(p);
        printRight(p);
    }
    @Override
    public String toString() {
        return "MatchGroup(\"" + title + "\", matched=" + matched + ", left=" + left + ", right=" + right + ")";
    }
}
