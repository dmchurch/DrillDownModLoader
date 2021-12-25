package de.dakror.modding.asm;

import java.util.Objects;

public final class MemberInfo {
    public final String name;
    public final String descriptor;
    public final int access;
    public final String owner;

    public MemberInfo(String name, String descriptor, int access) {
        this(name, descriptor, access, null);
    }
    public MemberInfo(String name, String descriptor, int access, String owner) {
        this.name = name;
        this.descriptor = descriptor;
        this.access = access;
        this.owner = owner;
    }

    public MemberInfo withName(String newName) {
        return new MemberInfo(newName, descriptor, access, owner);
    }

    public MemberInfo withOwner(String newOwner) {
        return new MemberInfo(name, descriptor, access, newOwner);
    }

    @Override
    public String toString() {
        return super.toString() + " ["+(owner!=null?owner+".":"")+name+descriptor+"/"+access+"]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, descriptor, access, owner);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MemberInfo) {
            var other = (MemberInfo) obj;
            return (name.equals(other.name) && descriptor.equals(other.descriptor) && access == other.access && Objects.equals(owner, other.owner));
        }
        return super.equals(obj);
    }
}