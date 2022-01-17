package de.dakror.modding;

public class Debug implements AutoCloseable {
    public static void println(String msg, String source) {
        if (source.length() > maxSourceLen) {
            maxSourceLen = Math.min(source.length(), 40);
        }
        System.out.println(String.format("%"+maxSourceLen+"s:%"+indent+"s%s", source,"",msg));
    }

    public static void println(String msg) {
        println(msg, 1);
    }

    protected static void println(String msg, int stackOffset) {
        println(msg, getCaller(stackOffset).getClassName().replaceAll(".*\\.", "").replaceAll(".*-(\\w+)[+>]","$1.").replace('$','.'));
    }

    public static void formatln(String format, Object... args) {
        println(String.format(format, args));
    }

    public static void enter() {
        enter(1);
    }
    public static void enter(int stackOffset) {
        enter(getCaller(stackOffset).getMethodName(), stackOffset + 1);
    }

    public static void enter(String msg) {
        enter(msg, 1);
    }
    public static void enter(String msg, int stackOffset) {
        println("entering "+msg, stackOffset + 1);
        Debug.indent += 2;
    }

    public static void exit(String msg) {
        // println("exiting "+msg);
        Debug.indent -= 2;
    }
    protected static int indent = 1;
    protected static int maxSourceLen = 1;

    protected static StackTraceElement getCaller() {
        return getCaller(1);
    }
    protected static StackTraceElement getCaller(int offset) {
        return Thread.currentThread().getStackTrace()[offset+3];
    }

    // AutoCloseable implementation, for try (new Debug(msg)) { code... }    
    private String msg;
    public Debug() {
        this(1);
    }
    public Debug(int stackOffset) {
        Debug.enter(getCaller(stackOffset).getMethodName());
    }
    public Debug(String msg) {
        this(msg, 1);
    }
    public Debug(String msg, int stackOffset) {
        Debug.enter(msg, stackOffset + 1);
        this.msg = msg;
    }
    @Override
    public void close() {
        Debug.exit(msg);
    }
}
