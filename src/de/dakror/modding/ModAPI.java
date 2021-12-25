package de.dakror.modding;

public interface ModAPI {
    default void debugln(String msg) {
        Debug.println(msg, 1);
    }
    default void debugln(String format, Object... args) {
        Debug.println(String.format(format, args), 1);
    }
    default Debug dcontext() {
        return new Debug(1);
    }
    default Debug dcontext(String msg) {
        return new Debug(msg, 1);
    }
    default Debug dcontext(String format, Object... args) {
        return new Debug(String.format(format, args), 1);
    }

    // static versions of the above, for ease of use in static contexts
    static void DEBUGLN(String msg) {
        Debug.println(msg, 1);
    }
    static void DEBUGLN(String format, Object... args) {
        Debug.println(String.format(format, args), 1);
    }
    static Debug DCONTEXT() {
        return new Debug(1);
    }
    static Debug DCONTEXT(String msg) {
        return new Debug(msg, 1);
    }
    static Debug DCONTEXT(String format, Object... args) {
        return new Debug(String.format(format, args), 1);
    }
    public static class Debug extends de.dakror.modding.Debug {

        public Debug() {
            super(1);
        }

        public Debug(int stackOffset) {
            super(stackOffset+1);
        }

        public Debug(String msg) {
            super(msg, 1);
        }

        public Debug(String msg, int stackOffset) {
            super(msg, stackOffset+1);
        }
    }
}
