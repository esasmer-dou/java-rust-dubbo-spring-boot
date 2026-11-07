package com.reactor.rust.dubbo.runtime;

/** Allocation-free scalar decoder for the bounded direct-JSON input window. */
final class Hessian2JsonScalars {
    private Hessian2JsonScalars() {}

    static boolean readBoolean(Hessian2JsonCursor in) {
        int tag = in.scalarByte();
        if (tag == 'T') return true;
        if (tag == 'F') return false;
        throw unexpected("boolean", tag);
    }

    static int readInt(Hessian2JsonCursor in) { return intTag(in, in.scalarByte()); }

    private static int intTag(Hessian2JsonCursor in, int tag) {
        if (tag >= 0x80 && tag <= 0xbf) return tag - 0x90;
        if (tag >= 0xc0 && tag <= 0xcf) return ((tag - 0xc8) << 8) + in.scalarByte();
        if (tag >= 0xd0 && tag <= 0xd7) return ((tag - 0xd4) << 16) + in.scalarCompactShort();
        if (tag == 'I') return in.scalarInt();
        throw unexpected("int", tag);
    }

    static long readLong(Hessian2JsonCursor in) {
        int tag = in.scalarByte();
        if (tag >= 0x80 && tag <= 0xd7 || tag == 'I') return intTag(in, tag);
        return longTag(in, tag);
    }

    private static long longTag(Hessian2JsonCursor in, int tag) {
        if (tag >= 0xd8 && tag <= 0xef) return tag - 0xe0;
        if (tag >= 0xf0) return ((tag - 0xf8) << 8) + in.scalarByte();
        if (tag >= 0x38 && tag <= 0x3f) return ((tag - 0x3c) << 16) + in.scalarCompactShort();
        if (tag == 0x59) return in.scalarInt();
        if (tag == 'L') return in.scalarLong();
        throw unexpected("long", tag);
    }

    static double readDouble(Hessian2JsonCursor in) {
        int tag = in.scalarByte();
        if (tag >= 0x80 && tag <= 0xd7 || tag == 'I') return intTag(in, tag);
        if (tag >= 0xd8 || tag >= 0x38 && tag <= 0x3f || tag == 0x59 || tag == 'L') return longTag(in, tag);
        return switch (tag) {
            case 0x5b -> 0.0d;
            case 0x5c -> 1.0d;
            case 0x5d -> (byte) in.scalarByte();
            case 0x5e -> (short) in.scalarShort();
            case 0x5f -> 0.001d * in.scalarInt();
            case 'D' -> Double.longBitsToDouble(in.scalarLong());
            default -> throw unexpected("double", tag);
        };
    }

    static char readChar(Hessian2JsonCursor in) {
        if (!in.hasRemaining()) throw new DubboCodecException("Incomplete Hessian payload");
        int tag = in.get(in.position()) & 255;
        if (!(tag <= 0x1f || tag >= 0x30 && tag <= 0x33 || tag == 'R' || tag == 'S')) {
            int value = readInt(in);
            if (value < Character.MIN_VALUE || value > Character.MAX_VALUE) {
                throw new DubboCodecException("Integer outside declared char range");
            }
            return (char) value;
        }
        int value = -1;
        while (true) {
            tag = in.scalarByte();
            int length;
            boolean last;
            if (tag <= 0x1f) { length = tag; last = true; }
            else if (tag >= 0x30 && tag <= 0x33) { length = ((tag - 0x30) << 8) + in.scalarByte(); last = true; }
            else if (tag == 'R' || tag == 'S') { length = in.scalarShort(); last = tag == 'S'; }
            else throw unexpected("char string", tag);
            if (length > 1 || length == 1 && value >= 0) {
                throw new DubboCodecException("Hessian char requires exactly one UTF-16 unit");
            }
            if (length == 1) value = charUnit(in);
            if (last) {
                if (value < 0) throw new DubboCodecException("Hessian char string must not be empty");
                return (char) value;
            }
        }
    }

    private static int charUnit(Hessian2JsonCursor in) {
        int first = in.scalarByte();
        if (first < 0x80) return first;
        if (first >= 0xc2 && first <= 0xdf) return ((first & 0x1f) << 6) | continuation(in);
        if (first >= 0xe0 && first <= 0xef) {
            int value = ((first & 0x0f) << 12) | (continuation(in) << 6) | continuation(in);
            if (value < 0x800) throw new DubboCodecException("Overlong Hessian char encoding");
            return value;
        }
        throw new DubboCodecException("Invalid Hessian char encoding");
    }

    private static int continuation(Hessian2JsonCursor in) {
        int value = in.scalarByte();
        if ((value & 0xc0) != 0x80) throw new DubboCodecException("Invalid Hessian char continuation");
        return value & 0x3f;
    }

    private static DubboCodecException unexpected(String expected, int actual) {
        return new DubboCodecException("Expected Hessian " + expected + " but got tag 0x" + Integer.toHexString(actual));
    }
}
