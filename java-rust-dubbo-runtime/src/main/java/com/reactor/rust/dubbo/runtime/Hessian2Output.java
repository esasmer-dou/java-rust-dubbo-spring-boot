package com.reactor.rust.dubbo.runtime;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

public final class Hessian2Output {
    private static final String BIG_DECIMAL = "java.math.BigDecimal";
    private static final String LOCAL_DATE_HANDLE =
            "com.alibaba.com.caucho.hessian.io.java8.LocalDateHandle";
    private static final String LOCAL_TIME_HANDLE =
            "com.alibaba.com.caucho.hessian.io.java8.LocalTimeHandle";
    private static final String LOCAL_DATE_TIME_HANDLE =
            "com.alibaba.com.caucho.hessian.io.java8.LocalDateTimeHandle";
    private static final String[] VALUE_FIELD = {"value"};
    private static final String[] DATE_FIELDS = {"year", "month", "day"};
    private static final String[] TIME_FIELDS = {"hour", "minute", "second", "nano"};
    private static final String[] DATE_TIME_FIELDS = {"date", "time"};

    private ByteBuffer buffer;
    private long nativeHandle;
    private String[] classTypes = new String[8];
    private String[][] classFields = new String[8][];
    private int classCount;

    public Hessian2Output() {
    }

    public void attach(ByteBuffer target, long responseHandle) {
        if (target == null || !target.isDirect()) {
            throw new IllegalArgumentException("Hessian output requires a direct ByteBuffer");
        }
        buffer = target.order(ByteOrder.BIG_ENDIAN);
        buffer.clear();
        nativeHandle = responseHandle;
        classCount = 0;
    }

    public int position() {
        requireAttached();
        return buffer.position();
    }

    public ByteBuffer buffer() {
        requireAttached();
        return buffer;
    }

    public void writeNull() {
        put((byte) 'N');
    }

    public void writeBoolean(boolean value) {
        put((byte) (value ? 'T' : 'F'));
    }

    public void writeInt(int value) {
        ensure(5);
        if (value >= -16 && value <= 47) {
            buffer.put((byte) (value + 0x90));
        } else if (value >= -2048 && value <= 2047) {
            buffer.put((byte) ((value >> 8) + 0xc8));
            buffer.put((byte) value);
        } else if (value >= -262_144 && value <= 262_143) {
            buffer.put((byte) ((value >> 16) + 0xd4));
            buffer.put((byte) (value >> 8));
            buffer.put((byte) value);
        } else {
            buffer.put((byte) 'I').putInt(value);
        }
    }

    public void writeLong(long value) {
        ensure(9);
        if (value >= -8 && value <= 15) {
            buffer.put((byte) (value + 0xe0));
        } else if (value >= -2048 && value <= 2047) {
            buffer.put((byte) ((value >> 8) + 0xf8));
            buffer.put((byte) value);
        } else if (value >= -262_144 && value <= 262_143) {
            buffer.put((byte) ((value >> 16) + 0x3c));
            buffer.put((byte) (value >> 8));
            buffer.put((byte) value);
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            buffer.put((byte) 0x59).putInt((int) value);
        } else {
            buffer.put((byte) 'L').putLong(value);
        }
    }

    public void writeDouble(double value) {
        ensure(9);
        if (value == 0.0d) {
            buffer.put((byte) 0x5b);
        } else if (value == 1.0d) {
            buffer.put((byte) 0x5c);
        } else if (value == Math.rint(value) && value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            buffer.put((byte) 0x5d).put((byte) value);
        } else if (value == Math.rint(value) && value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            buffer.put((byte) 0x5e).putShort((short) value);
        } else if ((double) (float) value == value) {
            buffer.put((byte) 0x5f).putFloat((float) value);
        } else {
            buffer.put((byte) 'D').putDouble(value);
        }
    }

    public void writeString(String value) {
        if (value == null) {
            writeNull();
            return;
        }
        int start = 0;
        int remaining = value.length();
        while (remaining > 65_535) {
            writeStringChunk(value, start, 65_535, false);
            start += 65_535;
            remaining -= 65_535;
        }
        writeStringChunk(value, start, remaining, true);
    }

    public void writeBinary(byte[] value) {
        if (value == null) {
            writeNull();
            return;
        }
        int offset = 0;
        while (value.length - offset > 65_535) {
            ensure(65_538);
            buffer.put((byte) 'A').putShort((short) 65_535);
            buffer.put(value, offset, 65_535);
            offset += 65_535;
        }
        int remaining = value.length - offset;
        ensure(remaining + 3);
        if (remaining <= 15) {
            buffer.put((byte) (0x20 + remaining));
        } else if (remaining <= 1023) {
            buffer.put((byte) (0x34 + (remaining >> 8))).put((byte) remaining);
        } else {
            buffer.put((byte) 'B').putShort((short) remaining);
        }
        buffer.put(value, offset, remaining);
    }

    public void writeListStart(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("list size must not be negative");
        }
        if (size <= 7) {
            put((byte) (0x78 + size));
        } else {
            put((byte) 'X');
            writeInt(size);
        }
    }

    public void writeMapStart() {
        put((byte) 'H');
    }

    public void writeMapEnd() {
        put((byte) 'Z');
    }

    public void writeObjectStart(String typeName, String[] fields) {
        int classRef = findClass(typeName, fields);
        if (classRef < 0) {
            put((byte) 'C');
            writeString(typeName);
            writeInt(fields.length);
            for (String field : fields) {
                writeString(field);
            }
            classRef = addClass(typeName, fields);
        }
        if (classRef <= 15) {
            put((byte) (0x60 + classRef));
        } else {
            put((byte) 'O');
            writeInt(classRef);
        }
    }

    public void writeBigDecimal(BigDecimal value) {
        if (value == null) {
            writeNull();
            return;
        }
        writeObjectStart(BIG_DECIMAL, VALUE_FIELD);
        writeString(value.toPlainString());
    }

    public void writeDate(Date value) {
        if (value == null) {
            writeNull();
            return;
        }
        ensure(9);
        buffer.put((byte) 'J').putLong(value.getTime());
    }

    public void writeLocalDate(LocalDate value) {
        if (value == null) {
            writeNull();
            return;
        }
        writeObjectStart(LOCAL_DATE_HANDLE, DATE_FIELDS);
        writeInt(value.getYear());
        writeInt(value.getMonthValue());
        writeInt(value.getDayOfMonth());
    }

    public void writeLocalTime(LocalTime value) {
        if (value == null) {
            writeNull();
            return;
        }
        writeObjectStart(LOCAL_TIME_HANDLE, TIME_FIELDS);
        writeInt(value.getHour());
        writeInt(value.getMinute());
        writeInt(value.getSecond());
        writeInt(value.getNano());
    }

    public void writeLocalDateTime(LocalDateTime value) {
        if (value == null) {
            writeNull();
            return;
        }
        writeObjectStart(LOCAL_DATE_TIME_HANDLE, DATE_TIME_FIELDS);
        writeLocalDate(value.toLocalDate());
        writeLocalTime(value.toLocalTime());
    }

    public void writeDynamic(Object value) {
        if (value == null) {
            writeNull();
        } else if (value instanceof Boolean current) {
            writeBoolean(current);
        } else if (value instanceof Byte current) {
            writeInt(current);
        } else if (value instanceof Short current) {
            writeInt(current);
        } else if (value instanceof Integer current) {
            writeInt(current);
        } else if (value instanceof Long current) {
            writeLong(current);
        } else if (value instanceof Float current) {
            writeDouble(current);
        } else if (value instanceof Double current) {
            writeDouble(current);
        } else if (value instanceof Character current) {
            writeInt(current);
        } else if (value instanceof String current) {
            writeString(current);
        } else if (value instanceof byte[] current) {
            writeBinary(current);
        } else if (value instanceof BigDecimal current) {
            writeBigDecimal(current);
        } else if (value instanceof Date current) {
            writeDate(current);
        } else if (value instanceof LocalDate current) {
            writeLocalDate(current);
        } else if (value instanceof LocalTime current) {
            writeLocalTime(current);
        } else if (value instanceof LocalDateTime current) {
            writeLocalDateTime(current);
        } else if (value instanceof Object[] current) {
            writeListStart(current.length);
            for (Object item : current) {
                writeDynamic(item);
            }
        } else if (value instanceof List<?> current) {
            writeListStart(current.size());
            for (Object item : current) {
                writeDynamic(item);
            }
        } else if (value instanceof Map<?, ?> current) {
            writeMapStart();
            for (Map.Entry<?, ?> entry : current.entrySet()) {
                writeDynamic(entry.getKey());
                writeDynamic(entry.getValue());
            }
            writeMapEnd();
        } else if (value instanceof DynamicDubboObject current) {
            String[] fields = current.fields().keySet().toArray(String[]::new);
            writeObjectStart(current.typeName(), fields);
            for (String field : fields) {
                writeDynamic(current.fields().get(field));
            }
        } else {
            throw new DubboCodecException("Unsupported dynamic Dubbo value type: "
                    + value.getClass().getName());
        }
    }

    private void writeStringChunk(String value, int start, int chars, boolean last) {
        ensure(3 + chars * 3);
        if (last && chars <= 31) {
            buffer.put((byte) chars);
        } else if (last && chars <= 1023) {
            buffer.put((byte) (0x30 + (chars >> 8))).put((byte) chars);
        } else {
            buffer.put((byte) (last ? 'S' : 'R')).putShort((short) chars);
        }
        int end = start + chars;
        for (int index = start; index < end; index++) {
            char current = value.charAt(index);
            if (current < 0x80) {
                buffer.put((byte) current);
            } else if (current < 0x800) {
                buffer.put((byte) (0xc0 | current >> 6));
                buffer.put((byte) (0x80 | current & 0x3f));
            } else {
                buffer.put((byte) (0xe0 | current >> 12));
                buffer.put((byte) (0x80 | current >> 6 & 0x3f));
                buffer.put((byte) (0x80 | current & 0x3f));
            }
        }
    }

    private int findClass(String typeName, String[] fields) {
        for (int index = 0; index < classCount; index++) {
            if (classTypes[index].equals(typeName) && Arrays.equals(classFields[index], fields)) {
                return index;
            }
        }
        return -1;
    }

    private int addClass(String typeName, String[] fields) {
        if (classCount == classTypes.length) {
            classTypes = Arrays.copyOf(classTypes, classCount * 2);
            classFields = Arrays.copyOf(classFields, classCount * 2);
        }
        classTypes[classCount] = typeName;
        classFields[classCount] = fields;
        return classCount++;
    }

    private void put(byte value) {
        ensure(1);
        buffer.put(value);
    }

    private void ensure(int additionalBytes) {
        requireAttached();
        if (additionalBytes <= buffer.remaining()) {
            return;
        }
        int required = Math.addExact(buffer.position(), additionalBytes);
        int previousPosition = buffer.position();
        if (nativeHandle > 0) {
            buffer = NativeDubboBridge.growProviderResponse(nativeHandle, required)
                    .order(ByteOrder.BIG_ENDIAN);
            buffer.position(previousPosition);
            return;
        }
        int capacity = Math.max(required, Math.max(1024, buffer.capacity() * 2));
        ByteBuffer replacement = ByteBuffer.allocateDirect(capacity).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer source = buffer.duplicate();
        source.flip();
        replacement.put(source);
        buffer = replacement;
    }

    private void requireAttached() {
        if (buffer == null) {
            throw new IllegalStateException("Hessian output is not attached");
        }
    }
}
