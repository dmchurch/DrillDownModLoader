package de.dakror.modding.asm.augmentation;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/** 
 * Strips enum initializations from the front of a <clinit> method, populating EnumField members.
 * <p>
 * All enum initializations SHOULD be at the beginning of {@code <clinit>} and conform to the following schema:
 * <ol>
 *  <li> Optional sequence of {@link AbstractInsnNode#LABEL LABEL}/{@link AbstractInsnNode#LINE LINE}/{@link AbstractInsnNode#FRAME FRAME} nodes
 *  <li> {@link #NEW} {@code ThisType}
 *  <li> {@link #DUP}
 *  <li> {@link #LDC} {@code "ENUM_NAME"}
 *  <li> exactly one of the following:
 *    <ul>
 *      <li> {@code ICONST_x}
 *      <li> {@link #BIPUSH}/{@link #SIPUSH} x
 *      <li> {@link #LDC} x
 *    </ul>
 *     where x is the in-sequence zero-based ordinal
 *  <li> any number of instructions initializing other arguments; NONE of these will be {@code INVOKESPECIAL ThisType.<init>(...)}
 *  <li> {@link #INVOKESPECIAL} {@code ThisType.<Init>(String name, int ord, ...)}
 *  <li> {@link #PUTSTATIC} {@code ThisType.ENUM_NAME}
 * </ol>
 * As long as this invariant holds (and there's no reason it shouldn't, really) this should get everything. And if it
 * doesn't, it should be able to fail loudly.
 * <p>
 * If any instructions remain after stripping enums, this will emit the remainder of the method to the supplied ClassVisitor.
 */
class ClinitAnalyzer extends MethodVisitor implements Opcodes {
    private static enum Phase {
        START,
        NEW,
        DUP,
        NAME,
        ORD,
        INIT,
        PUT,
        DONE;
        public final Phase prev;
        private Phase() {
            prev = Data.lastPhase;
            Data.lastPhase = this;
        }
        private static class Data {
            private static Phase lastPhase = null;
        }
    }

    private final String ownerName;
    private final Type ownerType;
    private final EnumMemberMap enumFields;
    private final ClassVisitor cv;

    private MethodNode mnode;
    private InsnCapture capture;

    public ClinitAnalyzer(int access, String name, String descriptor, String signature, String[] exceptions, String owner, EnumMemberMap enumFields, ClassVisitor cv) {
        super(ASM9, null);
        this.ownerName = owner;
        this.ownerType = Type.getObjectType(owner);
        this.enumFields = enumFields;
        this.cv = cv;
        mnode = new MethodNode(access, name, descriptor, signature, exceptions);
        mv = mnode;
    }

    @Override
    public void visitCode() {
        super.visitCode();
        mv = capture = new InsnCapture();
    }

    private void abandonCapture() {
        if (mv == capture) {
            try {
                mnode.accept(new ClassVisitor(ASM9, cv) {
                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(ASM9, mv = super.visitMethod(access, name, descriptor, signature, exceptions)) {
                            public void visitCode() {
                                super.visitCode();
                                throw new VisitCodeException();
                            }
                            public void visitEnd() {
                                // we'll reach End if mnode didn't have any instructions. pretend we hit Code and then bail.
                                super.visitCode();
                                throw new VisitCodeException();
                            }
                        };
                    }
                });
            } catch (VisitCodeException e) { }
            if (mv == capture) {
                throw new RuntimeException("mnode.accept did not throw exception?");
            }
            capture.node.instructions.accept(mv);
        }
    }

    private static class VisitCodeException extends RuntimeException { }

    private class InsnCapture extends InstructionAdapter {
        private Phase phase = Phase.START;
        private final MethodNode node;
        private String name;
        private int ord;
        private AbstractInsnNode ordInsn;
        private AbstractInsnNode lastInsn = null;

        public InsnCapture() {
            this(new MethodNode());
        }
        public InsnCapture(MethodNode node) {
            super(ASM9, node);
            this.node = node;
        }

        private boolean updateLastInsn() {
            return updateLastInsn(false);
        }
        private boolean updateLastInsn(boolean force) {
            var last = node.instructions.getLast();
            if (force || lastInsn == last.getPrevious()) {
                lastInsn = last;
                return true;
            }
            return lastInsn == last;
        }

        // Labels, LineNumbers, and Frames can be ignored. Update lastInsn to track.
        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
            updateLastInsn();
        }
        @Override
        public void visitLineNumber(int line, Label start) {
            super.visitLineNumber(line, start);
            updateLastInsn();
        }
        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            super.visitFrame(type, numLocal, local, numStack, stack);
            updateLastInsn();
        }

        // phased instructions
        private boolean enterPhase(Phase newPhase) {
            if (!updateLastInsn() && phase != Phase.ORD) {
                // anything can happen in ORD phase, ignore it
                throw new RuntimeException("Missed an instruction in phase "+phase);
            }
            if (phase == newPhase.prev) {
                phase = newPhase;
                return true;
            } else if (phase == Phase.ORD) {
                return false;
            } else if (phase == Phase.START) {
                abandonCapture();
                return false;
            } else {
                // shouldn't happen, means our analysis is wrong
                throw new RuntimeException("Tried to enter phase "+newPhase+" from phase "+phase);
            }
        }
        private boolean enterPhase(Phase newPhase, boolean condition) {
            var entering = enterPhase(newPhase);
            if (entering && !condition) {
                throw new RuntimeException("Failed postcondition on transition to phase "+newPhase);
            }
            return entering;
        }

        @Override
        public void anew(Type type) {
            super.anew(type);
            enterPhase(Phase.NEW, type.equals(ownerType));
        }

        @Override
        public void dup() {
            super.dup();
            enterPhase(Phase.DUP);
        }

        @Override
        public void aconst(Object value) {
            super.aconst(value);
            if (enterPhase(Phase.NAME, value instanceof String)) {
                name = (String)value;
            }
        }

        @Override
        public void iconst(int intValue) {
            super.iconst(intValue);
            if (enterPhase(Phase.ORD)) {
                ord = intValue;
                ordInsn = node.instructions.getLast();
            }
        }

        @Override
        public void invokespecial(String owner, String name, String descriptor, boolean isInterface) {
            super.invokespecial(owner, name, descriptor, isInterface);
            if (owner.equals(ownerName) && name.equals("<init>")) {
                updateLastInsn(true);
                enterPhase(Phase.INIT);
            }
        }

        @Override
        public void putstatic(String owner, String name, String descriptor) {
            super.putstatic(owner, name, descriptor);
            if (enterPhase(Phase.PUT, owner.equals(ownerName) && name.equals(this.name))) {
                phase = Phase.START;
                var enumInsns = new InsnList();
                enumInsns.add(node.instructions);
                lastInsn = null;
                enumFields.add(new EnumMember(name, ord, enumInsns, ordInsn));
            }
        }

    }



    // private static class ConsumingInsnList extends InsnList {
    //     public final InsnList source;
    //     private final ListIterator<AbstractInsnNode> it;
    //     public ConsumingInsnList(InsnList source) {
    //         this.source = source;
    //         it = source.iterator();
    //     }
    //     public AbstractInsnNode consume() {
    //         var insn = it.next();
    //         it.remove();
    //         this.add(insn);
    //         return insn;
    //     }

    //     public InsnList commit() {
    //         var list = new InsnList();
    //         list.add(this);
    //         return list;
    //     }

    //     public void undo() {
    //         source.insert(this);
    //     }

    //     public AbstractInsnNode consumeUntil(Predicate<AbstractInsnNode> matcher) {
    //         return consumeUntil(AbstractInsnNode.class, matcher);
    //     }
    //     public <T> T consumeUntil(Class<T> expectedClass, Predicate<T> matcher) {
    //         while (true) {
    //             try {
    //                 var insn = expectedClass.cast(consume());
    //                 if (matcher.test(insn)) {
    //                     return insn;
    //                 }
    //             }
    //             catch (ClassCastException e) {
    //                 // repeat
    //             }
    //         }
    //     }

    //     private AbstractInsnNode castInsn(AbstractInsnNode insn, int expectedOpcode) {
    //         if (insn.getOpcode() != expectedOpcode) {
    //             throw new ClassCastException();
    //         }
    //         return insn;
    //     }
    //     private <T> T castInsn(AbstractInsnNode insn, Class<T> expectedClass) {
    //         return expectedClass.cast(insn);
    //     }
    //     private <T> T castInsn(T insn, Predicate<T> matcher) {
    //         if (!matcher.test(insn)) {
    //             throw new ClassCastException();
    //         }
    //         return insn;
    //     }

    //     public AbstractInsnNode consume(int expectedOpcode) {
    //         return castInsn(consume(), expectedOpcode);
    //     }
    //     public <T> T consume(int expectedOpcode, Class<T> expectedClass) {
    //         return castInsn(castInsn(consume(), expectedOpcode), expectedClass);
    //     }
    //     public <T> T expect(int expectedOpcode, Class<T> expectedClass, Predicate<T> matcher) {
    //         return castInsn(castInsn(castInsn(consume(), expectedOpcode), expectedClass), matcher);
    //     }

    //     public AbstractInsnNode expected(int expectedOpcode) {
    //         return castInsn(getLast(), expectedOpcode);
    //     }
    //     public <T> T expected(int expectedOpcode, Class<T> expectedClass) {
    //         return castInsn(castInsn(getLast(), expectedOpcode), expectedClass);
    //     }
    //     public <T> T expected(int expectedOpcode, Class<T> expectedClass, Predicate<T> matcher) {
    //         return castInsn(castInsn(castInsn(getLast(), expectedOpcode), expectedClass), matcher);
    //     }
    // }

    // private static int insnPushValue(AbstractInsnNode insn) throws ClassCastException {
    //     switch(insn.getOpcode()) {
    //         case ICONST_0: return 0;
    //         case ICONST_1: return 1;
    //         case ICONST_2: return 2;
    //         case ICONST_3: return 3;
    //         case ICONST_4: return 4;
    //         case ICONST_5: return 5;
    //         case SIPUSH:
    //         case BIPUSH:
    //             return ((IntInsnNode)insn).operand;
    //         case LDC:
    //             return (int)((LdcInsnNode)insn).cst;
    //         default:
    //             throw new ClassCastException("Node "+insn+" is not a value-push instruction");
    //     }
    // }

    // @Override
    // public void visitEnd() {
    //     // final var it = instructions.iterator();
    //     final var insns = new ConsumingInsnList(instructions);

    //     while (true) {
    //         boolean inInitializer = false;
    //         String expect = null;
    //         try {
    //             // skip all pseudo-instructions with "opcode" -1
    //             insns.consumeUntil(i -> i.getOpcode() >= 0);

    //             insns.expected(NEW, TypeInsnNode.class, i -> i.desc.equals(ownerName));
    //             inInitializer = true;

    //             expect = "DUP";
    //             insns.consume(DUP);

    //             expect = "LDC <name>";
    //             final String name = (String)insns.consume(LDC, LdcInsnNode.class).cst;

    //             expect = "ordinal-load";
    //             final var ordInsn = insns.consume();
    //             final int ord = insnPushValue(ordInsn);

    //             expect = "INVOKESPECIAL "+ownerName+".<init>";
    //             insns.consumeUntil(MethodInsnNode.class, i -> i.getOpcode() == INVOKESPECIAL && i.owner.equals(ownerName) && i.name.equals("<init>"));

    //             expect = "PUTSTATIC "+ownerName+"."+name;
    //             insns.expect(PUTSTATIC, FieldInsnNode.class, i -> i.owner.equals(ownerName) && i.name.equals(name));

    //             this.enumFields.add(new EnumMember(name, ord, insns.commit(), ordInsn));
    //             inInitializer = false;
    //         } catch (NoSuchElementException|ClassCastException e) {
    //             if (inInitializer) {
    //                 throw new RuntimeException("Failure in analysis, expecting " + expect, e);
    //             } else {
    //                 break;
    //             }
    //         }
    //     }
    //     insns.undo();
    //     // replace any instructions that got mistakenly added to the newInsns list
    //     instructions.insert(insns);
    //     // if we have more than just the return insn, pass it forward
    //     if (instructions.size() > 1) {
    //         accept(cv);
    //     }
    // }
}