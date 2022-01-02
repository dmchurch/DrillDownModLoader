package de.dakror.modding.loader;

import java.nio.ByteBuffer;

// StubbedLauncher isn't actually used. It just provides the template for the
// code in StubFactory.
public interface StubbedLauncher {
    public static void main(String[] args) {
        ModClassLoader.fromStub(StubbedLauncher.class, args);
    }
}

class StubFactory extends ClassLoader {
    private static final int TAG_CLASS = 7;
    private static final int TAG_METHODREF = 10;
    private static final int TAG_NAME_AND_TYPE = 12;
    private static final int TAG_UTF8 = 1;

    private static final int ACC_PUBLIC    = 0x0001;
    private static final int ACC_STATIC    = 0x0008;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ABSTRACT  = 0x0400;

    private static final int I_LDC = 18;
    private final static int I_ALOAD_0 = 42;
    private static final int I_RETURN = 177;
    private static final int I_INVOKESTATIC = 184;

    ByteBuffer bb = ByteBuffer.allocate(512); // 512 bytes is more than enough

    StubFactory(ClassLoader parent) {
        super(parent);
    }

    StubFactory u1(int b) {
        bb.put((byte)b);
        return this;
    }
    StubFactory u2(int s) {
        bb.putShort((short)s);
        return this;
    }
    StubFactory u4(int i) {
        bb.putInt(i);
        return this;
    }
    StubFactory cpRef(int tag, int... ss) {
        u1(tag);
        for (var s: ss) u2(s);
        return this;
    }
    StubFactory cpUtf8(String str) {
        byte[] repr = str.getBytes(); // works for simple cases
        u1(TAG_UTF8).u2(repr.length);
        bb.put(repr);
        return this;
    }

    Class<?> buildStub(String name) {
        bb.flip();
        return defineClass(name, bb, null);
    }

    public static Class<?> makeStubFor(String name, ModClassLoader modClassLoader) {
        return new StubFactory(modClassLoader)
            .u4(0xCAFEBABE)     // magic
            .u2(0).u2(55)       // minor, major version (55.0 = Java 11)
            .u2(14)             // const pool size (last index + 1)
                .cpRef(TAG_CLASS, 2)                // #1: Class<name>
                .cpUtf8(name.replace('.','/'))      // #2
                .cpRef(TAG_CLASS, 4)                // #3: Class<Object>
                .cpUtf8("java/lang/Object")         // #4
                .cpUtf8("main")                     // #5
                .cpUtf8("([Ljava/lang/String;)V")   // #6
                .cpUtf8("Code")                     // #7
                .cpRef(TAG_METHODREF, 9, 11)        // #8 // Method<ModClassLoader.fromStub(Clsss, String[])void>
                .cpRef(TAG_CLASS, 10)               // #9 Class<ModClassLoader>
                .cpUtf8(ModClassLoader.class.getName().replace('.', '/')) // #10
                .cpRef(TAG_NAME_AND_TYPE, 12, 13)   // #11 NameType<fromStub, void (Class, String[])>
                .cpUtf8("fromStub")                 // #12
                .cpUtf8("(Ljava/lang/Class;[Ljava/lang/String;)V") // #13
            .u2(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT)
            .u2(1) // thisclass
            .u2(3) // superclass
            .u2(0) // #interfaces
            .u2(0) // #fields
            .u2(1) // #methods
                // public static void main(String[] args)
                .u2(ACC_PUBLIC | ACC_STATIC)
                .u2(5) // "main"
                .u2(6) // "([Ljava/lang/String;)V"
                .u2(1) // #attributes
                    .u2(7)  // "Code"
                    .u4(19) // length
                        .u2(2).u2(1)    // stack 2, locals 1
                        .u4(7)          // code length
                            .u1(I_LDC).u1(1)          // 0 - LDC Class<name>
                            .u1(I_ALOAD_0)            // 2 - ALOAD 0 (args)
                            .u1(I_INVOKESTATIC).u2(8) // 3 - INVOKESTATIC
                            .u1(I_RETURN)             // 6
                        .u2(0)          // #exceptions
                        .u2(0)          // #attributes
            .u2(0) // #attributes
            .buildStub(name);
    }
}
