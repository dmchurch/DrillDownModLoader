package de.dakror.modding.asm.diff;

public class FieldDiffer extends Differ<FieldDiffer, Node.Field> {
    public FieldDiffer(Node.Field left, Node.Field right) {
        super(left, right);
    }
    @Override
    public FieldDiffer newDiffer(Node.Field nodeLeft, Node.Field nodeRight) {
        return new FieldDiffer(nodeLeft, nodeRight);
    }

    @Override
    protected void printDetails(IndentingPrinter p) { }
}
