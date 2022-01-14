package de.dakror.modding.asm.diff;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Locale;

public class IndentingPrinter extends PrintWriter {
    public static interface Printable {
        void print(IndentingPrinter p);
        default void print() {
            print(new IndentingPrinter(System.out, true));
        }
        default void println(IndentingPrinter p) {
            print(p);
            if (!p.atNewline) {
                p.println();
            }
        }
    }
    public IndentingPrinter(OutputStream out) {
        super(out);
    }
    public IndentingPrinter(Writer out) {
        super(out);
    }
    public IndentingPrinter(String fileName) throws FileNotFoundException {
        super(fileName);
    }
    public IndentingPrinter(File file) throws FileNotFoundException {
        super(file);
    }
    public IndentingPrinter(Writer out, boolean autoFlush) {
        super(out, autoFlush);
    }
    public IndentingPrinter(OutputStream out, boolean autoFlush) {
        super(out, autoFlush);
    }

    public boolean atNewline = true;
    public int indentLevel = 0;
    public int prefixLength = 0;
    private static interface SafeCloseable extends AutoCloseable {
        void close();
    }
    public SafeCloseable indent() {
        indentLevel += 2;
        return this::dedent;
    }
    public SafeCloseable indent(String title) {
        if (title != null) {
            maybeNewline();
            println(title);
        }
        return indent();
    }
    public SafeCloseable indent(String format, Object... args) {
        maybeNewline();
        printf(format, args);
        return indent();
    }

    public void dedent() {
        indentLevel -= 2;
    }

    public void print(Printable obj) {
        obj.print(this);
    }

    public void print(Printable[] objs) {
        for (var o: objs) {
            print(o);
            maybeSeparator(", ");
        }
    }

    public void print(Object[] objs)  {
        for (var o: objs) {
            print(o);
            maybeSeparator(", ");
        }
    }

    public void print(Collection<?> objs) {
        for (var o: objs) {
            if (o instanceof Printable) {
                print((Printable)o);
            } else {
                print(o);
            }
            maybeSeparator(", ");
        }
    }

    public void println(Printable obj) {
        obj.println(this);
        maybeNewline();
    }

    public void println(Printable[] objs) {
        for (var o: objs) {
            println(o);
            maybeNewline();
        }
    }

    public void println(Object[] objs)  {
        for (var o: objs) {
            println(o);
        }
    }

    public void println(Collection<?> objs) {
        for (var o: objs) {
            if (o instanceof Printable) {
                println((Printable)o);
            } else {
                println(o);
            }
        }
    }

    public void indented(Printable[] objs) { indented(objs, null); }
    public void indented(Printable[] objs, String title) {
        if (objs.length == 0) return;
        try (var x = indent(title)) {
            for (var o: objs) {
                println(o);
            }
        }
    }
    public void indented(Printable[] objs, String format, Object... args) {
        indented(objs, String.format(format, args));
    }

    public void indented(Object[] objs) { indented(objs, null); }
    public void indented(Object[] objs, String title)  {
        if (objs.length == 0) return;
        try (var x = indent(title)) {
            for (var o: objs) {
                println(o);
            }
        }
    }
    public void indented(Object[] objs, String format, Object... args) {
        indented(objs, String.format(format, args));
    }

    public void indented(Collection<?> objs) { indented(objs, null); }
    public void indented(Collection<?> objs, String title) {
        if (objs.isEmpty()) return;
        try (var x = indent(title)) {
            for (var o: objs) {
                if (o instanceof Printable) {
                    println((Printable)o);
                } else {
                    println(o);
                }
            }
        }
    }
    public void indented(Collection<?> objs, String format, Object... args) {
        indented(objs, String.format(format, args));
    }

    private void writeIndent() {
        var indent = indentLevel - prefixLength;
        if (atNewline && indent > 0) {
            super.write(String.format("%"+indent+"s", ""));
        }
        prefixLength = 0;
        atNewline = false;
    }
    private void maybeNewline() {
        if (!atNewline) {
            println();
        }
    }
    private void maybeSeparator(String sep) {
        if (!atNewline) {
            print(sep);
        }
    }

    public void writePrefix(String s) {
        super.write(s);
        atNewline = true;
        prefixLength = s.length();
    }

    @Override
    public void write(String s) {
        writeIndent();
        super.write(s);
        atNewline = s.endsWith("\n");
    }

    @Override
    public void println() {
        super.println();
        atNewline = true;
    }

    @Override
    public IndentingPrinter format(String format, Object... args) {
        writeIndent();
        super.format(format, args);
        atNewline = format.endsWith("\n");
        return this;
    }
    @Override
    public IndentingPrinter format(Locale l, String format, Object... args) {
        writeIndent();
        super.format(l, format, args);
        atNewline = format.endsWith("\n");
        return this;
    }
}
