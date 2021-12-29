package de.dakror.modding.asm.augmentation;

import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

class EnumMember {
    public final String name;
    public final int ordinal;
    InsnList initInsns = null;
    private AbstractInsnNode ordInsn = null;
    private boolean needsOrdInsn = true;

    public EnumMember(String name, int ordinal) {
        this.name = name;
        this.ordinal = ordinal;
    }
    public EnumMember(String name, int ordinal, InsnList initInsns, AbstractInsnNode ordInsn) {
        this(name, ordinal, initInsns, ordInsn, false);
    }
    private EnumMember(String name, int ordinal, InsnList initInsns, AbstractInsnNode ordInsn, boolean needsOrdInsn) {
        this(name, ordinal);
        this.initInsns = initInsns;
        this.ordInsn = ordInsn;
        this.needsOrdInsn = needsOrdInsn;
    }

    @Override
    public String toString() {
        return String.format("EnumField %s[%d](%s, %s)", name, ordinal, initInsns, ordInsn);
    }

    public EnumMember renumber(int ordinal) {
        return new EnumMember(this.name, ordinal, this.initInsns, this.ordInsn, true);
    }

    public EnumMember merge(EnumMember other) {
        return merge(other, false);
    }
    public EnumMember merge(EnumMember other, boolean allowRenumber) {
        if (!name.equals(other.name) || (ordinal != other.ordinal && !allowRenumber)) {
            throw new RuntimeException("Tried to merge field "+this+" with "+other+", which are not compatible");
        }
        if (initInsns == null) {
            assert ordInsn == null;
            initInsns = other.initInsns;
            ordInsn = other.ordInsn;
            needsOrdInsn = (ordinal != other.ordinal) || other.needsOrdInsn;
        } else if (other.initInsns != null) {
            assert other.ordInsn != null;
            throw new RuntimeException("Tried to merge field "+this+" with "+other+", won't combine non-null InsnLists");
        }
        return this;
    }

    private void computeOrdInsn() {
        assert initInsns != null;
        var m = new MethodNode();
        new InstructionAdapter(m).iconst(ordinal);
        final AbstractInsnNode insn = m.instructions.getFirst();
        // switch(ordinal) {
        // case 0: insn = new InsnNode(EnumAugmentationVisitor.ICONST_0); break;
        // case 1: insn = new InsnNode(EnumAugmentationVisitor.ICONST_1); break;
        // case 2: insn = new InsnNode(EnumAugmentationVisitor.ICONST_2); break;
        // case 3: insn = new InsnNode(EnumAugmentationVisitor.ICONST_3); break;
        // case 4: insn = new InsnNode(EnumAugmentationVisitor.ICONST_4); break;
        // case 5: insn = new InsnNode(EnumAugmentationVisitor.ICONST_5); break;
        // default:
        //     if (ordinal < 128) {
        //         insn = new IntInsnNode(EnumAugmentationVisitor.BIPUSH, ordinal);
        //     } else if (ordinal < 65536) {
        //         insn = new IntInsnNode(EnumAugmentationVisitor.SIPUSH, ordinal);
        //     } else {
        //         insn = new LdcInsnNode(ordinal);
        //     }
        // }
        initInsns.set(ordInsn, insn);
        ordInsn = insn;
        needsOrdInsn = false;
    }

    public InsnList getInitInsns() {
        if (needsOrdInsn) {
            computeOrdInsn();
        }
        return initInsns;
    }
}