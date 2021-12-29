package de.dakror.modding.asm.augmentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

class EnumMemberMap extends HashMap<String, EnumMember> {
    private final ArrayList<String> byOrdList = new ArrayList<>();
    public int nextEnumOrd = 0;

    public boolean add(String name) {
        return add(new EnumMember(name, nextEnumOrd++));
    }

    public boolean add(EnumMember field) {
        var setField = this.merge(field.name, field, EnumMember::merge);
        if (setField == field) {
            // this is a new field
            setOrdName(field.ordinal, field.name);
            return true;
        } else {
            return false;
        }
    }

    public void addAll(EnumMemberMap submap) {
        for (var field: submap.getFields()) {
            if (add(field.renumber(nextEnumOrd))) {
                nextEnumOrd++;
            }
        }
    }

    @Override
    public EnumMember get(Object key) {
        if (key instanceof EnumMember) {
            key = ((EnumMember)key).name;
        }
        return super.get(key);
    }

    public EnumMember put(String name, EnumMember field) {
        assert(name.equals(field.name));
        var oldField = super.put(name, field);
        if (oldField != null && oldField.ordinal != field.ordinal) {
            byOrdList.set(oldField.ordinal, null);
        }
        setOrdName(field.ordinal, name);
        return oldField;
    }

    public EnumMember[] getFields() {
        return byOrdList.stream()
                .map(Objects::requireNonNull)
                .map(this::get)
                .filter(f -> (Objects.requireNonNull(f.initInsns) != null))
                .toArray(EnumMember[]::new);
    }

    private void setOrdName(int ord, String name) {
        byOrdList.ensureCapacity(ord+1);
        while (ord >= byOrdList.size()) {
            byOrdList.add(null);
        }
        var prevName = byOrdList.set(ord, name);
        if (prevName != null && !prevName.equals(name)) {
            throw new RuntimeException("Attempted to rename ord position "+ord+" from "+prevName+" to "+name);
        }
    }
}