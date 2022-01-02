package de.dakror.modding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class ClassAugmentationBase<T, C> implements ModLoader.IClassAugmentation, ModLoader.IClassMod<T, C> {
    // maps all classes in an aug chain to the AugmentationChain object
    protected Map<String, AugmentationChain> augmentationChains = new HashMap<>();
    protected Map<String, Map<String, String>> affectedClasses = DefaultingHashMap.using(HashMap::new);

    public void augmentClass(String baseClass, String augmentationClass) {
        assert baseClass != augmentationClass;
        var augChain = augmentationChains.getOrDefault(baseClass, newAugmentationChain(baseClass));
        var subChain = augmentationChains.get(augmentationClass);

        // augChain.addAugmentation() also adds the item to this.augmentationChains, so we need to fetch subChain beforehand
        if (augChain.addAugmentation(augmentationClass) && subChain != null && subChain != augChain) {
            // this aug class had a chain of its own, add all its augments to the base, forcing them to the end of the list
            subChain.augmentations.forEach(subAug -> augChain.addAugmentation(subAug, true));
        }
    }

    abstract protected AugmentationChain newAugmentationChain(String className);

    @Override
    public boolean hooksClass(String className) {
        return augmentationChains.containsKey(className) || affectedClasses.containsKey(className);
    }

    @Override
    public T redefineClass(String className, T classDef, C context) throws ClassNotFoundException {
        var augChain = augmentationChains.get(className);
        if (augChain != null) {
            return augChain.redefineClass(className, classDef, context);
        } else {
            return redefineAffectedClass(className, classDef, context, affectedClasses.get(className));
        }
    }

    abstract protected T redefineAffectedClass(String className, T classDef, C context, Map<String, String> renameMap);

    abstract protected class AugmentationChain {
        public String baseName;
        public String augmentedName;
        public List<String> augmentations = new ArrayList<>();
        protected boolean compiled = false;

        protected AugmentationChain(String baseClass) {
            baseName = baseClass;
            augmentedName = baseName + "__Augmented";
            augmentationChains.put(baseName, this);
            augmentationChains.put(augmentedName, this);
        }
        public boolean addAugmentation(String augmentation) {
            return addAugmentation(augmentation, false);
        }
        public boolean addAugmentation(String augmentation, boolean force) {
            if (compiled) {
                throw new RuntimeException("Tried to add augmentation "+augmentation+" to chain for "+baseName+", which is already compiled");
            }
            if (augmentations.contains(augmentation)) {
                if (force) {
                    augmentations.remove(augmentation);
                } else {
                    return false;
                }
            }
            augmentations.add(augmentation);
            augmentationChains.put(augmentation, this);
            return true;
        }

        abstract public T redefineClass(String className, T classDef, C context) throws ClassNotFoundException;
    }
}