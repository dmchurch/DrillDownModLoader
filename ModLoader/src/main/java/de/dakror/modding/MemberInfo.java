package de.dakror.modding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

import de.dakror.modding.asm.Util;

public final class MemberInfo {
    public final String name;
    public final String descriptor;
    public final int access;
    public final String owner;

    public enum Access implements AccessMask {
        None(0) {
            public Set<Access> flags() {
                return Set.of();
            }
            public String toString() {
                return "";
            }
        },
        Public(0x0001),         // class, field, method
        Private(0x0002),        // class, field, method
        Protected(0x0004),      // class, field, method
        Visibility(Public.bitMask | Private.bitMask | Protected.bitMask) {
            public Set<Access> flags() {
                return Set.of(Public, Private, Protected);
            }
            public int set(int access) {
                throw new UnsupportedOperationException();
            }
            public boolean isSet(int access) {
                throw new UnsupportedOperationException();
            }
        },
        Static(0x0008),         // field, method
        Final(0x0010),          // class, field, method, parameter
        Super(0x0020),          // class
        Synchronized(0x0020),   // method
        Open(0x0020),           // module
        Transitive(0x0020),     // module requires
        Volatile(0x0040),       // field
        Bridge(0x0040),         // method
        Static_Phase(0x0040),   // module requires
        Varargs(0x0080),        // method
        Transient(0x0080),      // field
        Native(0x0100),         // method
        Interface(0x0200),      // class
        Abstract(0x0400),       // class, method
        Strict(0x0800),         // method
        Synthetic(0x1000),      // class, field, method, parameter, module *
        Annotation(0x2000),     // class
        Enum(0x4000),           // class(?) field inner
        Mandated(0x8000),       // field, method, parameter, module, module *
        Module(0x8000);         // class
        final int bitMask;
        private Access(int bit) {
            bitMask = bit;
        }
        public int mask() {
            return bitMask;
        }
        private static class Combo implements AccessMask {
            private final int mask;
            private final Access[] flags;
            public static AccessMask of(Iterable<? extends AccessMask> masks) {
                return new Combo(masks).canonicalize();
            }
            public static AccessMask of(AccessMask... masks) {
                return new Combo(masks).canonicalize();
            }
            private Combo(AccessMask... masks) {
                Set<Access> flags = new TreeSet<>();
                int maskValue = 0;
                for (var flag: masks) {
                    flags.addAll(flag.flags());
                    maskValue |= flag.mask();
                }
                flags.remove(Access.None);
                this.mask = maskValue;
                this.flags = flags.toArray(Access[]::new);
            }
            private Combo(Iterable<? extends AccessMask> masks) {
                Set<Access> flags = new TreeSet<>();
                int maskValue = 0;
                for (var flag: masks) {
                    flags.addAll(flag.flags());
                    maskValue |= flag.mask();
                }
                flags.remove(Access.None);
                this.mask = maskValue;
                this.flags = flags.toArray(Access[]::new);
            }

            private AccessMask canonicalize() {
                if (flags.length > 1) {
                    return this;
                } else if (flags.length == 1) {
                    return flags[0];
                } else {
                    return Access.None;
                }
            }

            @Override
            public int mask() {
                return mask;
            }

            @Override
            public boolean equals(Object obj) {
                if (obj instanceof Combo) {
                    return equals((Combo)obj);
                }
                return super.equals(obj);
            }
            public boolean equals(Combo other) {
                return Arrays.equals(flags, other.flags);
            }
            @Override
            public int hashCode() {
                return Arrays.hashCode(flags);
            }

            @Override
            public String toString() {
                return String.join(" ", flags().stream().map(AccessMask::toString).toArray(String[]::new));
            }
        }
    }

    public AccessMask getAccessMask() {
        int access = this.access;
        List<Access> flags = new ArrayList<>();
        for (var testBit: isMethod() ? methodAccessBits : fieldAccessBits) {
            if (testBit.isSet(access)) {
                flags.add(testBit);
                access = testBit.clear(access);
            }
        }
        if (access != 0) {
            throw new RuntimeException("Could not translate access "+this.access+" into flags for "+this);
        }
        return AccessMask.of(flags);
    }

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

    public MemberInfo withAccess(int newAccess) {
        return new MemberInfo(name, descriptor, newAccess, owner);
    }

    public MemberInfo asPrivate() {
        return withAccess(Access.Private.set(Access.Visibility.clear(access)));
    }

    public MemberInfo withDescriptor(String newDescriptor) {
        return new MemberInfo(name, newDescriptor, access, owner);
    }

    public MemberInfo appendParameter(String paramDescriptor) {
        return new MemberInfo(name, Util.appendMethodParam(descriptor, paramDescriptor), access, owner);
    }

    public MemberInfo prependParameter(String paramDescriptor) {
        return new MemberInfo(name, Util.prependMethodParam(descriptor, paramDescriptor), access, owner);
    }

    public boolean isMethod() {
        return descriptor.startsWith("(");
    }
    public boolean isPublic() {
        return Access.Public.isSet(access);
    }
    public boolean isProtected() {
        return Access.Protected.isSet(access);
    }
    public boolean isPrivate() {
        return Access.Private.isSet(access);
    }
    public boolean isPackage() {
        return Access.Private.also(Access.Public).also(Access.Protected).isClear(access);
    }
    public boolean isStatic() {
        return Access.Static.isSet(access);
    }
    public boolean isEnum() {
        return Access.Enum.isSet(access);
    }
    public boolean isConstructor() {
        return name.equals("<init>") && descriptor.endsWith(")V");
    }
    public boolean isClassInitializer() {
        return name.equals("<clinit>") && descriptor.equals("()V");
    }

    public boolean is(AccessMask mask) {
        return mask.isSet(access);
    }

    public boolean isNot(AccessMask mask) {
        return mask.isClear(access);
    }

    @Override
    public String toString() {
        return super.toString() + " ["+(owner!=null?owner+".":"")+name+descriptor+"/"+safeAccessMask()+"]";
    }

    private String safeAccessMask() {
        try {
            return getAccessMask().toString();
        } catch (Exception e) { }
        return String.format("0x%x", access);
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

    private static final Access[] fieldAccessBits = new Access[] {
        Access.Public,      // (0x0001) class, field, method
        Access.Private,     // (0x0002) class, field, method
        Access.Protected,   // (0x0004) class, field, method
        Access.Static,      // (0x0008) field, method
        Access.Final,       // (0x0010) class, field, method, parameter
        Access.Volatile,    // (0x0040) field
        Access.Transient,   // (0x0080) field
        Access.Synthetic,   // (0x1000) class, field, method, parameter, module *
        Access.Enum,        // (0x4000) class(?) field inner
        Access.Mandated,    // (0x8000) field, method, parameter, module, module *
    };

    private static final Access[] methodAccessBits = new Access[]{
        Access.Public,      // (0x0001) class, field, method
        Access.Private,     // (0x0002) class, field, method
        Access.Protected,   // (0x0004) class, field, method
        Access.Static,      // (0x0008) field, method
        Access.Final,       // (0x0010) class, field, method, parameter
        Access.Synchronized,// (0x0020) method
        Access.Bridge,      // (0x0040) method
        Access.Varargs,     // (0x0080) method
        Access.Native,      // (0x0100) method
        Access.Abstract,    // (0x0400) class, method
        Access.Strict,      // (0x0800) method
        Access.Synthetic,   // (0x1000) class, field, method, parameter, module *
        Access.Mandated,    // (0x8000) field, method, parameter, module, module *
    };

    private final static Map<AccessMask, AccessMask> flags = new HashMap<>();
    public static interface AccessMask {
        public static AccessMask of(AccessMask mask) {
            return flags.computeIfAbsent(mask, Function.identity());
        }
        public static AccessMask of(AccessMask... masks) {
            return of(Access.Combo.of(masks));
        }
        public static AccessMask of(Iterable<? extends AccessMask> masks) {
            return of(Access.Combo.of(masks));
        }
        default boolean isSet(int access) {
            var mask = mask();
            return (access & mask) == mask;
        }
        default boolean isClear(int access) {
            var mask = mask();
            return (access & mask) == 0;
        }
        default int set(int access) {
            return access | mask();
        }
        default int clear(int access) {
            return access & ~mask();
        }
        default boolean sameMask(Object obj) {
            return false;
        }
        int mask();
        /**
         * Return an AccessMask that is the combination of this mask and another. Using "also"
         * instead of the more natural "and" to avoid the "bitwise and" connection; this is actually
         * a bitwise OR operation. Thus,
         * <p>
         * {@code member.is(Public) && member.is(Static)}
         * <p>
         * is logically equivalent to
         * <p>
         * {@code member.is(Public.also(Static))}
         */ 
        default AccessMask also(AccessMask flag) {
            return of(this, flag);
        }
        default Set<Access> flags() {
            if (this instanceof Access) {
                return Set.of((Access)this);
            } else if (this instanceof Access.Combo) {
                return Set.of(((Access.Combo)this).flags);
            }
            throw new UnsupportedOperationException();
        }
    }
}