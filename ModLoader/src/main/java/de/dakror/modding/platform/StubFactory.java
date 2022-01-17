package de.dakror.modding.platform;

import java.nio.ByteBuffer;

// StubClass isn't actually used. It just provides the template for the
// code in StubFactory. It's an interface to avoid producing constructors.
interface StubClass { }

public class StubFactory {
    private static final int TAG_CLASS = 7;
    private static final int TAG_UTF8 = 1;

    private static final int ACC_PUBLIC    = 0x0001;
    private static final int ACC_INTERFACE = 0x0200;
    private static final int ACC_ABSTRACT  = 0x0400;

    ByteBuffer bb = ByteBuffer.allocate(512); // 512 bytes is more than enough

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

    byte[] buildStub(String name) {
        bb.flip();
        var code = new byte[bb.limit()];
        bb.get(code);
        return code;
    }

    public static byte[] makeStubFor(String name) {
        return new StubFactory()
            .u4(0xCAFEBABE)     // magic
            .u2(0).u2(55)       // minor, major version (55.0 = Java 11)
            .u2(5)             // const pool size (last index + 1)
                .cpRef(TAG_CLASS, 2)                // #1: Class<name>
                .cpUtf8(name.replace('.','/'))      // #2
                .cpRef(TAG_CLASS, 4)                // #3: Class<Object>
                .cpUtf8("java/lang/Object")         // #4
            .u2(ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT)
            .u2(1) // thisclass
            .u2(3) // superclass
            .u2(0) // #interfaces
            .u2(0) // #fields
            .u2(0) // #methods
            .u2(0) // #attributes
            .buildStub(name);
    }
}
