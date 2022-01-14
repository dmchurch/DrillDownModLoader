package de.dakror.modding.asm.diff;

public class MethodDiffer extends Differ<MethodDiffer, Node.Method> {
    final MatchGroup<Node.Class, ClassDiffer> classes;

    public MethodDiffer(Node.Method left, Node.Method right) {
        super(left, right);
        classes = new MatchGroup<>("Inner classes", left.innerClasses(), right.innerClasses(), ClassDiffer::new);
    }

    @Override
    public MethodDiffer newDiffer(Node.Method nodeLeft, Node.Method nodeRight) {
        return new MethodDiffer(nodeLeft, nodeRight);
    }

    @Override
    protected void printDetails(IndentingPrinter p) {
        p.print(classes);
    }
}
