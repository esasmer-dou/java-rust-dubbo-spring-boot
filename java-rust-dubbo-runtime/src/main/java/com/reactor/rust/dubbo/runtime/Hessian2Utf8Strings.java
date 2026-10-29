package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

/** Reusable strict Hessian UTF-16-length / CESU-8 cursor, producing Unicode scalars. */
final class Hessian2Utf8Strings {
    private static final byte[] HEX = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private final Hessian2JsonInput budget;
    private ByteBuffer source;
    private int chunkStart = -1;
    private int remaining;
    private int pendingLow = -1;
    private boolean last;

    Hessian2Utf8Strings(Hessian2JsonInput budget) { this.budget = budget; }

    void begin(ByteBuffer source) {
        this.source = source;
        remaining = 0;
        pendingLow = -1;
        last = false;
        chunkStart = -1;
        chunk();
    }

    void detach() { source = null; chunkStart = -1; }
    boolean detached() { return source == null; }

    int scalar() {
        int value = unit();
        if (value >= 0xd800 && value <= 0xdbff) {
            int low = unit();
            if (low < 0xdc00 || low > 0xdfff) throw malformed();
            return 0x10000 + ((value - 0xd800) << 10) + low - 0xdc00;
        }
        if (value >= 0xdc00 && value <= 0xdfff) throw malformed();
        return value;
    }

    private int unit() {
        if (pendingLow >= 0) {
            int value = pendingLow;
            pendingLow = -1;
            return value;
        }
        while (remaining == 0) {
            if (last) { finishChunk(); return -1; }
            chunk();
        }
        int first = get();
        int value;
        if (first < 0x80) value = first;
        else if (first >= 0xc2 && first <= 0xdf) value = ((first & 31) << 6) | continuation();
        else if (first >= 0xe0 && first <= 0xef) {
            int second = continuation();
            if (first == 0xe0 && second < 0x20) throw malformed();
            value = ((first & 15) << 12) | (second << 6) | continuation();
        } else if (first >= 0xf0 && first <= 0xf4) {
            int second = continuation();
            if (remaining < 2 || first == 0xf0 && second < 0x10 || first == 0xf4 && second >= 0x10) throw malformed();
            value = ((first & 7) << 18) | (second << 12) | (continuation() << 6) | continuation();
            remaining -= 2;
            pendingLow = 0xdc00 | ((value - 0x10000) & 1023);
            return 0xd800 | ((value - 0x10000) >>> 10);
        } else throw malformed();
        remaining--;
        return value;
    }

    private void chunk() {
        finishChunk();
        chunkStart = source.position();
        int tag = get();
        if (tag <= 31) { remaining = tag; last = true; }
        else if (tag >= 0x30 && tag <= 0x33) { remaining = ((tag - 0x30) << 8) | get(); last = true; }
        else if (tag == 'R' || tag == 'S') { remaining = (get() << 8) | get(); last = tag == 'S'; }
        else throw new DubboCodecException("Expected Hessian string chunk");
    }

    private void finishChunk() {
        if (chunkStart >= 0) {
            int bytes = source.position() - chunkStart;
            chunkStart = -1;
            budget.charge(bytes);
        }
    }

    private int continuation() {
        int value = get();
        if ((value & 0xc0) != 0x80) throw malformed();
        return value & 63;
    }

    private int get() {
        if (source == null || !source.hasRemaining()) throw new DubboCodecException("Incomplete Hessian string");
        return source.get() & 255;
    }

    int writeJson(byte[] out, int position, OutputStream target, boolean number, Hessian2JsonTranscoder writer) throws IOException {
        if (out == null || out.length < 8 || position < 0 || position > out.length || target == null) {
            throw new IllegalArgumentException("JSON string output requires a buffer of at least 8 bytes and a valid position/target");
        }
        int limit = writer.stringLimit(out.length);
        if (position >= limit) { writer.flushString(out, position, target); position = 0; limit = writer.stringLimit(out.length); }
        if (limit == 0) throw Hessian2JsonTranscoder.outputBudget();
        if (!number) out[position++] = '"';
        int numberState = 0;
        int count = 0;
        int fractionDigits = 0;
        long exponent = 0;
        boolean negativeExponent = false;
        int value;
        while ((value = scalar()) >= 0) {
            int width = number ? 1 : value == '"' || value == '\\' ? 2 : value < 32 ? 6
                    : value < 0x80 ? 1 : value < 0x800 ? 2 : value < 0x10000 ? 3 : 4;
            if (position > limit - width) {
                writer.flushString(out, position, target); position = 0; limit = writer.stringLimit(out.length);
                if (width > limit) throw Hessian2JsonTranscoder.outputBudget();
            }
            if (number) {
                if ((numberState == 4 || numberState == 5) && value >= '0' && value <= '9') fractionDigits++;
                if (numberState == 6 && value == '-') negativeExponent = true;
                if ((numberState == 6 || numberState == 7 || numberState == 8) && value >= '0' && value <= '9') {
                    exponent = exponent * 10 + value - '0';
                    if (exponent > Integer.MAX_VALUE) throw new DubboCodecException("BigDecimal exponent exceeds its integer range");
                }
                numberState = numberState(numberState, value);
                out[position++] = (byte) value;
                count++;
            } else if (value == '"' || value == '\\') {
                out[position++] = '\\'; out[position++] = (byte) value;
            } else if (value < 32) {
                out[position++] = '\\'; out[position++] = 'u'; out[position++] = '0'; out[position++] = '0';
                out[position++] = HEX[value >>> 4]; out[position++] = HEX[value & 15];
            } else if (value < 0x80) out[position++] = (byte) value;
            else if (value < 0x800) {
                out[position++] = (byte) (0xc0 | (value >>> 6)); out[position++] = (byte) (0x80 | (value & 63));
            } else if (value < 0x10000) {
                out[position++] = (byte) (0xe0 | (value >>> 12)); out[position++] = (byte) (0x80 | ((value >>> 6) & 63));
                out[position++] = (byte) (0x80 | (value & 63));
            } else {
                out[position++] = (byte) (0xf0 | (value >>> 18)); out[position++] = (byte) (0x80 | ((value >>> 12) & 63));
                out[position++] = (byte) (0x80 | ((value >>> 6) & 63)); out[position++] = (byte) (0x80 | (value & 63));
            }
        }
        if (number) {
            if (count == 0 || numberState != 2 && numberState != 3 && numberState != 5 && numberState != 8) {
                throw new DubboCodecException("Invalid BigDecimal JSON number");
            }
            long scale = fractionDigits - (negativeExponent ? -exponent : exponent);
            if (scale < Integer.MIN_VALUE || scale > Integer.MAX_VALUE) {
                throw new DubboCodecException("BigDecimal scale exceeds its integer range");
            }
        } else {
            if (position == limit) {
                writer.flushString(out, position, target); position = 0; limit = writer.stringLimit(out.length);
                if (limit == 0) throw Hessian2JsonTranscoder.outputBudget();
            }
            out[position++] = '"';
        }
        return position;
    }

    private static int numberState(int state, int value) {
        boolean digit = value >= '0' && value <= '9';
        int next = switch (state) {
            case 0 -> value == '-' ? 1 : value == '0' ? 2 : value >= '1' && value <= '9' ? 3 : -1;
            case 1 -> value == '0' ? 2 : value >= '1' && value <= '9' ? 3 : -1;
            case 2, 3 -> digit && state == 3 ? 3 : value == '.' ? 4 : value == 'e' || value == 'E' ? 6 : -1;
            case 4 -> digit ? 5 : -1;
            case 5 -> digit ? 5 : value == 'e' || value == 'E' ? 6 : -1;
            case 6 -> value == '+' || value == '-' ? 7 : digit ? 8 : -1;
            case 7, 8 -> digit ? 8 : -1;
            default -> -1;
        };
        if (next < 0) throw new DubboCodecException("Invalid BigDecimal JSON number");
        return next;
    }

    private static DubboCodecException malformed() { return new DubboCodecException("Malformed Hessian UTF-8 or surrogate sequence"); }
}
