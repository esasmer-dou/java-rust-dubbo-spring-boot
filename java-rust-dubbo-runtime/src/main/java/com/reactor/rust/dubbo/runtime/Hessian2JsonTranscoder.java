package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.OutputStream;

/** Globally pooled 64 KiB output and direct parser context, detached after each use. */
public final class Hessian2JsonTranscoder {
    private static final byte[] NULL_LITERAL = {'n', 'u', 'l', 'l'};
    private static final byte[] TRUE_LITERAL = {'t', 'r', 'u', 'e'};
    private static final byte[] FALSE_LITERAL = {'f', 'a', 'l', 's', 'e'};
    private static final String[] DECIMAL_FIELDS = {"value"};
    private static final String[] DATE_FIELDS = {"year", "month", "day"};
    private final DirectJsonWriterPool pool;
    private final int slot;
    volatile Thread owner;
    private final byte[] bytes = new byte[64 * 1024];
    private final Hessian2JsonInput context = new Hessian2JsonInput();
    private OutputStream target;
    private int position;
    private int bufferLimit;
    private long flushed;
    private long remainingOutput;
    Hessian2JsonTranscoder(DirectJsonWriterPool pool, int slot) { this.pool = pool; this.slot = slot; }

    static Hessian2JsonTranscoder acquire(Hessian2Input input, OutputStream target) {
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(input, "input");
        return DirectJsonWriterPool.global().acquire(input, target);
    }

    void attach(Hessian2Input input, OutputStream target) {
        context.attach(input, pool.scanLimit);
        this.target = target;
        position = 0;
        flushed = 0L;
        remainingOutput = pool.outputLimit;
        bufferLimit = (int) Math.min(bytes.length, remainingOutput);
    }

    void detach() {
        if (owner != Thread.currentThread()) throw new IllegalStateException("Direct JSON writer is not owned by the caller");
        try { context.detach(); }
        finally {
            target = null;
            position = 0;
            flushed = 0;
            remainingOutput = bufferLimit = 0;
            pool.release(slot);
        }
    }

    boolean detached() { return target == null && context.detached(); }

    static int transcodeString(Hessian2Input input, byte[] out, int position, OutputStream target) throws IOException {
        if (out == null || out.length < 8 || position < 0 || position > out.length || target == null) {
            throw new IllegalArgumentException("Invalid direct JSON output buffer/position/target");
        }
        Hessian2JsonTranscoder writer = acquire(input, target);
        try { return writer.stringValue(input, out, position, target); }
        finally { writer.detach(); }
    }

    private int stringValue(Hessian2Input in, byte[] output, int offset, OutputStream flushTarget) throws IOException {
        Hessian2JsonInput current = input(in);
        if (current.readNull()) {
            if (offset > stringLimit(output.length) - 4) {
                flushString(output, offset, flushTarget); offset = 0;
                if (stringLimit(output.length) < 4) throw outputBudget();
            }
            output[offset++] = 'n'; output[offset++] = 'u'; output[offset++] = 'l'; output[offset++] = 'l';
            return offset;
        }
        Hessian2Utf8Strings cursor = current.strings();
        cursor.begin(current.cursor());
        return cursor.writeString(output, offset, flushTarget, this);
    }

    public void writeByte(int value) throws IOException {
        if (position == bufferLimit) moreSpace();
        bytes[position++] = (byte) value;
    }

    public void writeRaw(byte[] value) throws IOException {
        int offset = 0;
        while (offset < value.length) {
            if (position == bufferLimit) moreSpace();
            int count = Math.min(bufferLimit - position, value.length - offset);
            System.arraycopy(value, offset, bytes, position, count);
            offset += count;
            position += count;
        }
    }

    public void field(byte[] name, boolean first) throws IOException {
        int separator = first ? 0 : 1;
        if (name.length <= bufferLimit - position - separator) {
            if (!first) bytes[position++] = ',';
            System.arraycopy(name, 0, bytes, position, name.length);
            position += name.length;
            return;
        }
        fieldAcrossBoundary(name, first);
    }

    // Keep partial output and the exact budget behavior on the existing bounded path.
    private void fieldAcrossBoundary(byte[] name, boolean first) throws IOException {
        if (!first) writeByte(',');
        writeRaw(name);
    }

    public void writeNull() throws IOException {
        writeRaw(NULL_LITERAL);
    }
    public boolean writeNull(Hessian2Input in) throws IOException {
        if (!input(in).readNull()) return false;
        writeNull(); return true;
    }

    public void writeBoolean(boolean value) throws IOException {
        writeRaw(value ? TRUE_LITERAL : FALSE_LITERAL);
    }

    public void writeLongAscii(long value) throws IOException {
        boolean negative = value < 0;
        if (!negative) value = -value;
        long count = value;
        int length = negative ? 1 : 0;
        do { length++; count /= 10; } while (count != 0);
        if (position > bufferLimit - length) {
            flush();
            if (length > bufferLimit) throw outputBudget();
        }
        int end = position + length, cursor = end;
        do { bytes[--cursor] = (byte) ('0' - value % 10); value /= 10; } while (value != 0);
        if (negative) bytes[position] = '-';
        position = end;
    }

    /** Exact common decimals use primitive scaling; harder values retain JDK round-trip formatting. */
    public void writeDoubleAscii(double value) throws IOException {
        if (!Double.isFinite(value)) throw new DubboCodecException("Non-finite direct JSON number");
        if (value == 0.0) { writeZero(Double.doubleToRawLongBits(value) < 0); return; }
        for (int digits = 0, scale = 1; digits <= 6; digits++, scale *= 10) {
            double integer = Math.rint(value * scale);
            if (integer <= -0x1.0p53 || integer >= 0x1.0p53) break;
            if (integer / scale == value) {
                writeScaledDecimal((long) integer, scale, digits);
                return;
            }
        }
        ascii(Double.toString(value));
    }
    public void writeFloatAscii(float value) throws IOException {
        if (!Float.isFinite(value)) throw new DubboCodecException("Non-finite direct JSON number");
        if (value == 0.0f) { writeZero(Float.floatToRawIntBits(value) < 0); return; }
        // Above 2^24, a float's shortest JDK decimal can differ from its exact
        // integer value. Keep that JSON numeric-node contract on the fallback.
        if (value >= -0x1.0p24f && value <= 0x1.0p24f) {
            for (int digits = 0, scale = 1; digits <= 6; digits++, scale *= 10) {
                double integer = Math.rint((double) value * scale);
                if (integer <= -0x1.0p53 || integer >= 0x1.0p53) break;
                double candidate = integer / scale;
                if (candidate == (double) value) {
                    writeScaledDecimal((long) integer, scale, digits);
                    return;
                }
                // Do not continue to a longer exact spelling if the JDK may
                // choose a shorter decimal that only round-trips to this float.
                if ((float) candidate == value) break;
            }
        }
        ascii(Float.toString(value));
    }
    private void writeZero(boolean negative) throws IOException {
        if (negative) writeByte('-');
        writeByte('0'); writeByte('.'); writeByte('0');
    }
    private void writeScaledDecimal(long integer, int scale, int digits) throws IOException {
        if (integer < 0) { writeByte('-'); integer = -integer; }
        writeLongAscii(integer / scale);
        writeByte('.');
        if (digits == 0) { writeByte('0'); return; }
        int fraction = (int) (integer % scale);
        for (int divisor = scale / 10; divisor != 0; divisor /= 10) {
            writeByte('0' + fraction / divisor);
            fraction %= divisor;
        }
    }
    private void ascii(String value) throws IOException {
        for (int index = 0; index < value.length(); index++) writeByte(value.charAt(index));
    }

    public void writeString(Hessian2Input in) throws IOException {
        position = stringValue(in, bytes, position, target);
    }

    /** AOT nullable-string lane: validate ownership and consume the null tag once. */
    public void writeNullableString(Hessian2Input in) throws IOException {
        Hessian2JsonInput current = input(in);
        if (current.readNull()) {
            writeNull();
            return;
        }
        Hessian2Utf8Strings cursor = current.strings();
        cursor.begin(current.cursor());
        position = cursor.writeString(bytes, position, target, this);
    }

    /** A Java char is one UTF-16 unit; escape isolated surrogates instead of emitting invalid UTF-8. */
    public void writeChar(char value) throws IOException {
        writeByte('"');
        if (value == '"' || value == '\\') {
            writeByte('\\');
            writeByte(value);
        } else if (value < 0x20 || Character.isSurrogate(value)) {
            writeByte('\\');
            writeByte('u');
            for (int shift = 12; shift >= 0; shift -= 4) {
                int digit = value >>> shift & 15;
                writeByte(digit < 10 ? '0' + digit : 'a' + digit - 10);
            }
        } else if (value < 0x80) {
            writeByte(value);
        } else if (value < 0x800) {
            writeByte(0xc0 | value >>> 6);
            writeByte(0x80 | value & 0x3f);
        } else {
            writeByte(0xe0 | value >>> 12);
            writeByte(0x80 | value >>> 6 & 0x3f);
            writeByte(0x80 | value & 0x3f);
        }
        writeByte('"');
    }

    public int beginObject(Hessian2Input in, String expectedType) {
        return input(in).object(expectedType);
    }
    int beginObject(Hessian2Input in, String expectedType, String[] wireFields) {
        return input(in).plannedObject(expectedType, wireFields);
    }
    public String[] objectFields(Hessian2Input in) { return input(in).fields(); }
    public void endObject(Hessian2Input in, int slot) { input(in).complete(slot); }
    public void skipValue(Hessian2Input in) { input(in).skipValue(); }
    public int beginList(Hessian2Input in) { return input(in).readListStart(); }
    public boolean hasMoreListEntries(Hessian2Input in, int size, int count) {
        return input(in).hasMoreListEntries(size, count);
    }
    public void endList(Hessian2Input in, int size) { input(in).readListEnd(size); }
    public void restoreObjectFields(Hessian2Input in, String[] enclosingFields) {
        input(in).restoreFields(enclosingFields);
    }

    /** Non-consuming dispatch probe; it retains the input and thread ownership checks. */
    public boolean isReference(Hessian2Input in) { return input(in).peek() == 'Q'; }

    public boolean replayReference(Hessian2Input in, Hessian2JsonWriter writer) throws IOException {
        Hessian2JsonInput current = input(in);
        // Legacy writers may still probe here without testing the tag first.
        if (current.peek() != 'Q') return false;
        String[] enclosingFields = current.fields();
        int restore = current.beginReplay();
        if (restore < 0) return false;
        try { writer.writeResolved(in, this); }
        finally { current.endReplay(restore); current.restoreFields(enclosingFields); }
        return true;
    }

    public void writeBigDecimal(Hessian2Input in) throws IOException {
        if (writeNull(in) || isReference(in) && replayReference(in, Hessian2JsonTranscoder::decimalValue)) return;
        decimalValue(in, this);
    }
    private static void decimalValue(Hessian2Input in, Hessian2JsonTranscoder out) throws IOException {
        int slot = out.beginObject(in, "java.math.BigDecimal", DECIMAL_FIELDS);
        String[] fields = out.objectFields(in);
        boolean seen = false;
        for (String field : fields) {
            if (field.equals("value")) {
                if (seen) throw new DubboCodecException("Duplicate BigDecimal value");
                seen = true;
                Hessian2JsonInput current = out.input(in);
                Hessian2Utf8Strings cursor = current.strings();
                cursor.begin(current.cursor());
                out.position = cursor.writeNumber(out.bytes, out.position, out.target, out);
            } else out.skipValue(in);
        }
        if (!seen) throw new DubboCodecException("Missing BigDecimal value");
        out.endObject(in, slot);
    }

    public void writeLocalDate(Hessian2Input in) throws IOException {
        if (writeNull(in) || isReference(in) && replayReference(in, Hessian2JsonTranscoder::dateValue)) return;
        dateValue(in, this);
    }
    private static void dateValue(Hessian2Input in, Hessian2JsonTranscoder out) throws IOException {
        Hessian2JsonInput current = out.input(in);
        int slot = current.plannedObject("com.alibaba.com.caucho.hessian.io.java8.LocalDateHandle", DATE_FIELDS);
        String[] fields = current.fields();
        int year = 0, month = 0, day = 0;
        for (String field : fields) {
            switch (field) {
                case "year" -> year = current.readInt();
                case "month" -> month = current.readInt();
                case "day" -> day = current.readInt();
                default -> current.skipValue();
            }
        }
        if (year < -999999999 || year > 999999999 || month < 1 || month > 12 || day < 1) throw new DubboCodecException("Invalid LocalDate");
        boolean leap = (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
        int days = month == 2 ? (leap ? 29 : 28) : month == 4 || month == 6 || month == 9 || month == 11 ? 30 : 31;
        if (day > days) throw new DubboCodecException("Invalid LocalDate");
        out.writeDate(year, month, day);
        current.complete(slot);
    }

    private void writeDate(int year, int month, int day) throws IOException {
        if (year < 0 || year > 9999) { writeExtendedDate(year, month, day); return; }
        // The common ISO date is exactly twelve bytes, including quotes.
        if (position > bufferLimit - 12) {
            flush();
            if (bufferLimit < 12) throw outputBudget();
        }
        int start = position;
        bytes[start] = '"';
        bytes[start + 1] = (byte) ('0' + year / 1000);
        bytes[start + 2] = (byte) ('0' + year / 100 % 10);
        bytes[start + 3] = (byte) ('0' + year / 10 % 10);
        bytes[start + 4] = (byte) ('0' + year % 10);
        bytes[start + 5] = '-';
        bytes[start + 6] = (byte) ('0' + month / 10);
        bytes[start + 7] = (byte) ('0' + month % 10);
        bytes[start + 8] = '-';
        bytes[start + 9] = (byte) ('0' + day / 10);
        bytes[start + 10] = (byte) ('0' + day % 10);
        bytes[start + 11] = '"';
        position = start + 12;
    }

    private void writeExtendedDate(int year, int month, int day) throws IOException {
        writeByte('"');
        if (year < 0) writeByte('-'); else writeByte('+');
        int absoluteYear = Math.abs(year);
        if (absoluteYear < 1000) writeByte('0');
        if (absoluteYear < 100) writeByte('0');
        if (absoluteYear < 10) writeByte('0');
        writeLongAscii(absoluteYear);
        writeByte('-'); if (month < 10) writeByte('0'); writeLongAscii(month);
        writeByte('-'); if (day < 10) writeByte('0'); writeLongAscii(day);
        writeByte('"');
    }

    /** Compares bounded generated enum names without constructing the wire value as a String. */
    public void writeEnumName(Hessian2Input in, String[] names, byte[][] literals) throws IOException {
        Hessian2JsonInput current = input(in);
        int start = current.position();
        for (int candidate = 0; candidate < names.length; candidate++) {
            current.position(start);
            Hessian2Utf8Strings cursor = current.strings();
            cursor.begin(current.cursor());
            String expected = names[candidate];
            int index = 0, scalar;
            boolean matches = true;
            while ((scalar = cursor.scalar()) >= 0) {
                if (index >= 256) throw new DubboCodecException("Direct JSON enum name exceeds 256 characters");
                if (index >= expected.length() || expected.codePointAt(index) != scalar) matches = false;
                index += scalar >= 0x10000 ? 2 : 1;
            }
            if (matches && index == expected.length()) { writeRaw(literals[candidate]); return; }
        }
        throw new DubboCodecException("Unknown direct JSON enum name");
    }

    void flush() throws IOException {
        if (position > 0) { flushString(bytes, position, target); position = 0; }
    }
    private void moreSpace() throws IOException { flush(); if (bufferLimit == 0) throw outputBudget(); }
    int stringLimit(int capacity) { return (int) Math.min(capacity, remainingOutput); }
    void flushString(byte[] buffer, int count, OutputStream output) throws IOException {
        if (count > remainingOutput) throw outputBudget();
        if (count != 0) {
            output.write(buffer, 0, count);
            flushed += count;
            remainingOutput -= count;
            bufferLimit = (int) Math.min(bytes.length, remainingOutput);
        }
    }
    static DubboCodecException outputBudget() { return new DubboCodecException("Direct JSON response output byte budget exceeded"); }
    long bytesWritten() { return flushed; }
    public Hessian2JsonInput input(Hessian2Input in) {
        if (owner != Thread.currentThread() || !context.attachedTo(in)) throw new IllegalStateException("Direct JSON transcoder/input/owner mismatch");
        return context;
    }
}
