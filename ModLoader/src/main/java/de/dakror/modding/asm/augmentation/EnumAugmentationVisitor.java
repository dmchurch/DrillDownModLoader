package de.dakror.modding.asm.augmentation;

import java.lang.ref.WeakReference;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;
import org.objectweb.asm.commons.Remapper;

import de.dakror.modding.asm.augmentation.ClassAugmentationImpl.AugmentationChain;

public class EnumAugmentationVisitor extends AugmentationVisitor {
    protected final Method VALUES;
    protected final Method VALUEOF;
    protected final Type enumType;
    protected final Type enumArrayType;

    protected final EnumMemberMap enumFields = new EnumMemberMap();
    protected final WeakReference<EnumAnalyzer> enumAnalyzer;

    public static ClassVisitor create(AugmentationChain chain, ClassVisitor nextClassVisitor, ClassReader reader, Map<String, String> remaps, Remapper remapper) throws ClassNotFoundException {
        return new EnumAugmentationVisitor(chain, nextClassVisitor, reader, remaps, remapper).enumAnalyzer.get();
    }

    private EnumAugmentationVisitor(AugmentationChain chain, ClassVisitor nextClassVisitor, ClassReader reader, Map<String, String> remaps, Remapper remapper) throws ClassNotFoundException {
        super(chain, nextClassVisitor, reader, remaps, remapper);
        // the only time we get called will be FROM the EnumAnalyzer, so we don't need a strong ref back to it
        this.enumAnalyzer = new WeakReference<>(new EnumAnalyzer(this));
        VALUES = Method.getMethod(chain.baseName + "[] values()");
        VALUEOF = Method.getMethod(chain.baseName  + " valueOf(String)");
        enumType = VALUEOF.getReturnType();
        enumArrayType = VALUES.getReturnType();
    }

    @Override
    protected void emitAugmentation(ClassReader augReader, Augment augment) {
        var subAnalyzer = new EnumAnalyzer(augment.emitter(emitAugmentationTarget(), visitedMembers));
        augReader.accept(subAnalyzer, 0);
        enumAnalyzer.get().enumFields.addAll(subAnalyzer.enumFields);
        enumAnalyzer.get().updateMaxs(subAnalyzer.clinitMaxStack, subAnalyzer.clinitMaxLocals);
    }

    @Override
    protected void emitClinit(GeneratorAdapter gen, int maxStack, int maxLocals) {
        enumAnalyzer.get().emitInitializers(gen);
        super.emitClinit(gen, Math.max(maxStack, enumAnalyzer.get().clinitMaxStack), Math.max(maxLocals, enumAnalyzer.get().clinitMaxLocals));
    }

    @Override
    protected void emitSynthetics() {
        super.emitSynthetics();
        enumAnalyzer.get().emitValueOfMethod(cv);
        enumAnalyzer.get().emitValuesMethod(cv);
    }
}
