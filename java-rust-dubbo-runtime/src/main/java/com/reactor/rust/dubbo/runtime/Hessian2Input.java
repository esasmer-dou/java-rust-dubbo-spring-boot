package com.reactor.rust.dubbo.runtime;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Hessian2Input {
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
    private String[] classTypes = new String[8];
    private String[][] classFields = new String[8][];
    private int classCount;
    private final List<String> types = new ArrayList<>(4);
    private final List<Object> references = new ArrayList<>(16);
    private int maxCollectionItems = 100_000;

    public void attach(ByteBuffer source, int length, int collectionLimit) {
        if (source == null || !source.isDirect()) {
            throw new IllegalArgumentException("Hessian input requires a direct ByteBuffer");
        }
        if (length < 0 || length > source.capacity()) {
            throw new IllegalArgumentException("Invalid Hessian input length " + length);
        }
        buffer = source.duplicate().order(ByteOrder.BIG_ENDIAN);
        buffer.clear().limit(length);
        classCount = 0;
        types.clear();
        references.clear();
        maxCollectionItems = collectionLimit;
    }

    public boolean readNull() {
        require(1);
        if (peek() == 'N') {
            buffer.get();
            return true;
        }
        return false;
    }

    public boolean readBoolean() {
        byte tag = get();
        if (tag == 'T') {
            return true;
        }
        if (tag == 'F') {
            return false;
        }
        throw unexpected("boolean", tag);
    }

    public int readInt() {
        return readIntTag(get());
    }

    public long readLong() {
        byte tag = get();
        if ((tag & 0xff) >= 0x80 && (tag & 0xff) <= 0xd7 || tag == 'I') {
            return readIntTag(tag);
        }
        int unsigned = tag & 0xff;
        if (unsigned >= 0xd8 && unsigned <= 0xef) {
            return unsigned - 0xe0;
        }
        if (unsigned >= 0xf0 && unsigned <= 0xff) {
            return ((unsigned - 0xf8) << 8) + unsigned(get());
        }
        if (unsigned >= 0x38 && unsigned <= 0x3f) {
            return ((unsigned - 0x3c) << 16) + (unsigned(get()) << 8) + unsigned(get());
        }
        if (tag == 0x59) {
            require(4);
            return buffer.getInt();
        }
        if (tag == 'L') {
            require(8);
            return buffer.getLong();
        }
        throw unexpected("long", tag);
    }

    public double readDouble() {
        byte tag = get();
        int unsigned = tag & 0xff;
        if (unsigned >= 0x80 && unsigned <= 0xd7 || tag == 'I') {
            return readIntTag(tag);
        }
        if (unsigned >= 0xd8 && unsigned <= 0xff || unsigned >= 0x38 && unsigned <= 0x3f
                || tag == 0x59 || tag == 'L') {
            buffer.position(buffer.position() - 1);
            return readLong();
        }
        return switch (tag) {
            case 0x5b -> 0.0d;
            case 0x5c -> 1.0d;
            case 0x5d -> get();
            case 0x5e -> { require(2); yield buffer.getShort(); }
            case 0x5f -> { require(4); yield buffer.getFloat(); }
            case 'D' -> { require(8); yield buffer.getDouble(); }
            default -> throw unexpected("double", tag);
        };
    }

    public String readString() {
        if (readNull()) {
            return null;
        }
        return readStringTag(get());
    }

    public byte[] readBinary() {
        if (readNull()) {
            return null;
        }
        ByteBuffer scan = buffer.duplicate();
        int total = scanBinaryLength(scan);
        byte[] output = new byte[total];
        int offset = 0;
        while (true) {
            byte tag = get();
            int length = readBinaryChunkLength(buffer, tag);
            require(length);
            buffer.get(output, offset, length);
            offset += length;
            if (isLastBinaryChunk(tag)) {
                return output;
            }
        }
    }

    private int scanBinaryLength(ByteBuffer source) {
        int total = 0;
        while (true) {
            require(source, 1);
            byte tag = source.get();
            int length = readBinaryChunkLength(source, tag);
            require(source, length);
            source.position(source.position() + length);
            total = Math.addExact(total, length);
            if (isLastBinaryChunk(tag)) {
                return total;
            }
        }
    }

    private static int readBinaryChunkLength(ByteBuffer source, byte tag) {
        int unsigned = tag & 0xff;
        if (unsigned >= 0x20 && unsigned <= 0x2f) {
            return unsigned - 0x20;
        }
        if (unsigned >= 0x34 && unsigned <= 0x37) {
            require(source, 1);
            return ((unsigned - 0x34) << 8) + unsigned(source.get());
        }
        if (tag == 'A' || tag == 'B') {
            require(source, 2);
            return Short.toUnsignedInt(source.getShort());
        }
        throw unexpected("binary", tag);
    }

    private static boolean isLastBinaryChunk(byte tag) {
        int unsigned = tag & 0xff;
        return unsigned >= 0x20 && unsigned <= 0x2f
                || unsigned >= 0x34 && unsigned <= 0x37
                || tag == 'B';
    }

    public int readListStart() {
        byte tag = get();
        int unsigned = tag & 0xff;
        int length;
        if (unsigned >= 0x78 && unsigned <= 0x7f) {
            length = unsigned - 0x78;
        } else if (unsigned >= 0x70 && unsigned <= 0x77) {
            readType();
            length = unsigned - 0x70;
        } else if (tag == 'X') {
            length = readInt();
        } else if (tag == 'V') {
            readType();
            length = readInt();
        } else if (tag == 'U') {
            readType();
            length = -1;
        } else if (tag == 'W') {
            length = -1;
        } else {
            throw unexpected("list", tag);
        }
        if (length < -1 || length > maxCollectionItems) {
            throw new DubboCodecException("Hessian list length exceeds configured limit: " + length);
        }
        return length;
    }

    public boolean hasMoreListEntries(int declaredSize, int decodedItems) {
        if (decodedItems < 0 || decodedItems > maxCollectionItems) {
            throw new DubboCodecException("Hessian list item count exceeds configured limit");
        }
        return declaredSize >= 0 ? decodedItems < declaredSize : peek() != 'Z';
    }

    public void readListEnd(int declaredSize) {
        if (declaredSize < 0) {
            byte tag = get();
            if (tag != 'Z') {
                throw unexpected("list end", tag);
            }
        }
    }

    public void readMapStart() {
        byte tag = get();
        if (tag == 'H') {
            return;
        }
        if (tag == 'M') {
            readType();
            return;
        }
        throw unexpected("map", tag);
    }

    public boolean hasMoreMapEntries() {
        return peek() != 'Z';
    }

    public void readMapEnd() {
        byte tag = get();
        if (tag != 'Z') {
            throw unexpected("map end", tag);
        }
    }

    public void expectObject(String expectedType, String[] expectedFields) {
        String[] actualFields = readObjectFields(expectedType);
        if (!Arrays.equals(actualFields, expectedFields)) {
            throw new DubboCodecException("Hessian object schema mismatch. Expected " + expectedType
                    + Arrays.toString(expectedFields) + " but received "
                    + Arrays.toString(actualFields));
        }
    }

    public String[] readObjectFields(String expectedType) {
        ObjectDescriptor descriptor = readObjectDescriptor();
        if (!descriptor.typeName().equals(expectedType)) {
            throw new DubboCodecException("Hessian object type mismatch. Expected " + expectedType
                    + " but received " + descriptor.typeName());
        }
        return descriptor.fields();
    }

    private ObjectDescriptor readObjectDescriptor() {
        byte tag = get();
        if (tag == 'C') {
            readClassDefinition();
            tag = get();
        }
        int classRef;
        int unsigned = tag & 0xff;
        if (unsigned >= 0x60 && unsigned <= 0x6f) {
            classRef = unsigned - 0x60;
        } else if (tag == 'O') {
            classRef = readInt();
        } else {
            throw unexpected("object", tag);
        }
        if (classRef < 0 || classRef >= classCount) {
            throw new DubboCodecException("Unknown Hessian class reference " + classRef);
        }
        return new ObjectDescriptor(classTypes[classRef], classFields[classRef]);
    }

    public BigDecimal readBigDecimal() {
        if (readNull()) {
            return null;
        }
        expectObject(BIG_DECIMAL, VALUE_FIELD);
        return new BigDecimal(readString());
    }

    public Date readDate() {
        if (readNull()) {
            return null;
        }
        byte tag = get();
        if (tag == 'J') {
            require(8);
            return new Date(buffer.getLong());
        }
        if (tag == 'K') {
            require(4);
            return new Date(buffer.getInt() * 60_000L);
        }
        throw unexpected("date", tag);
    }

    public LocalDate readLocalDate() {
        if (readNull()) {
            return null;
        }
        String[] fields = readObjectFields(LOCAL_DATE_HANDLE);
        int year = 0;
        int month = 0;
        int day = 0;
        for (String field : fields) {
            switch (field) {
                case "year" -> year = readInt();
                case "month" -> month = readInt();
                case "day" -> day = readInt();
                default -> skipValue();
            }
        }
        return LocalDate.of(year, month, day);
    }

    public LocalTime readLocalTime() {
        if (readNull()) {
            return null;
        }
        String[] fields = readObjectFields(LOCAL_TIME_HANDLE);
        int hour = 0;
        int minute = 0;
        int second = 0;
        int nano = 0;
        for (String field : fields) {
            switch (field) {
                case "hour" -> hour = readInt();
                case "minute" -> minute = readInt();
                case "second" -> second = readInt();
                case "nano" -> nano = readInt();
                default -> skipValue();
            }
        }
        return LocalTime.of(hour, minute, second, nano);
    }

    public LocalDateTime readLocalDateTime() {
        if (readNull()) {
            return null;
        }
        String[] fields = readObjectFields(LOCAL_DATE_TIME_HANDLE);
        LocalDate date = null;
        LocalTime time = null;
        for (String field : fields) {
            switch (field) {
                case "date" -> date = readLocalDate();
                case "time" -> time = readLocalTime();
                default -> skipValue();
            }
        }
        if (date == null || time == null) {
            throw new DubboCodecException("Incomplete Hessian LocalDateTime handle");
        }
        return LocalDateTime.of(date, time);
    }

    public Object readDynamic() {
        byte tag = peek();
        int unsigned = tag & 0xff;
        if (tag == 'N') {
            buffer.get();
            return null;
        }
        if (tag == 'Q') {
            buffer.get();
            int index = readInt();
            if (index < 0 || index >= references.size()) {
                throw new DubboCodecException("Invalid Hessian object reference " + index);
            }
            return references.get(index);
        }
        if (tag == 'T' || tag == 'F') {
            return readBoolean();
        }
        if (unsigned >= 0x80 && unsigned <= 0xd7 || tag == 'I') {
            return readInt();
        }
        if (unsigned >= 0xd8 && unsigned <= 0xff || unsigned >= 0x38 && unsigned <= 0x3f
                || tag == 0x59 || tag == 'L') {
            return readLong();
        }
        if (tag == 0x5b || tag == 0x5c || tag == 0x5d || tag == 0x5e || tag == 0x5f
                || tag == 'D') {
            return readDouble();
        }
        if (unsigned <= 0x1f || unsigned >= 0x30 && unsigned <= 0x33
                || tag == 'R' || tag == 'S') {
            return readString();
        }
        if (unsigned >= 0x20 && unsigned <= 0x2f || unsigned >= 0x34 && unsigned <= 0x37
                || tag == 'A' || tag == 'B') {
            return readBinary();
        }
        if (unsigned >= 0x70 && unsigned <= 0x7f || tag == 'U' || tag == 'V'
                || tag == 'W' || tag == 'X') {
            int size = readListStart();
            List<Object> values = new ArrayList<>(Math.max(size, 0));
            references.add(values);
            int index = 0;
            while (hasMoreListEntries(size, index)) {
                values.add(readDynamic());
                index++;
            }
            readListEnd(size);
            return values;
        }
        if (tag == 'H' || tag == 'M') {
            readMapStart();
            Map<Object, Object> values = new LinkedHashMap<>();
            references.add(values);
            while (hasMoreMapEntries()) {
                values.put(readDynamic(), readDynamic());
            }
            readMapEnd();
            return values;
        }
        if (tag == 'J' || tag == 'K') {
            return readDate();
        }
        if (tag == 'C' || unsigned >= 0x60 && unsigned <= 0x6f || tag == 'O') {
            ObjectDescriptor descriptor = readObjectDescriptor();
            int reference = reserveReference();
            if (descriptor.typeName().equals(BIG_DECIMAL)
                    && Arrays.equals(descriptor.fields(), VALUE_FIELD)) {
                return completeReference(reference, new BigDecimal(readString()));
            }
            if (descriptor.typeName().equals(LOCAL_DATE_HANDLE)
                    && sameFields(descriptor.fields(), DATE_FIELDS)) {
                int year = 0;
                int month = 0;
                int day = 0;
                for (String field : descriptor.fields()) {
                    switch (field) {
                        case "year" -> year = readInt();
                        case "month" -> month = readInt();
                        case "day" -> day = readInt();
                        default -> skipValue();
                    }
                }
                return completeReference(reference, LocalDate.of(year, month, day));
            }
            if (descriptor.typeName().equals(LOCAL_TIME_HANDLE)
                    && sameFields(descriptor.fields(), TIME_FIELDS)) {
                int hour = 0;
                int minute = 0;
                int second = 0;
                int nano = 0;
                for (String field : descriptor.fields()) {
                    switch (field) {
                        case "hour" -> hour = readInt();
                        case "minute" -> minute = readInt();
                        case "second" -> second = readInt();
                        case "nano" -> nano = readInt();
                        default -> skipValue();
                    }
                }
                return completeReference(reference, LocalTime.of(hour, minute, second, nano));
            }
            if (descriptor.typeName().equals(LOCAL_DATE_TIME_HANDLE)
                    && sameFields(descriptor.fields(), DATE_TIME_FIELDS)) {
                LocalDate date = null;
                LocalTime time = null;
                for (String field : descriptor.fields()) {
                    switch (field) {
                        case "date" -> date = (LocalDate) readDynamic();
                        case "time" -> time = (LocalTime) readDynamic();
                        default -> skipValue();
                    }
                }
                if (date == null || time == null) {
                    throw new DubboCodecException("Incomplete Hessian LocalDateTime handle");
                }
                return completeReference(reference, LocalDateTime.of(date, time));
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String field : descriptor.fields()) {
                fields.put(field, readDynamic());
            }
            return completeReference(reference,
                    new DynamicDubboObject(descriptor.typeName(), fields));
        }
        throw unexpected("dynamic value", tag);
    }

    public void skipValue() {
        readDynamic();
    }

    private int reserveReference() {
        int index = references.size();
        references.add(null);
        return index;
    }

    private <T> T completeReference(int index, T value) {
        references.set(index, value);
        return value;
    }

    private static boolean sameFields(String[] actual, String[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (String field : expected) {
            boolean found = false;
            for (String current : actual) {
                if (field.equals(current)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    public int remaining() {
        return buffer.remaining();
    }

    private int readIntTag(byte tag) {
        int unsigned = tag & 0xff;
        if (unsigned >= 0x80 && unsigned <= 0xbf) {
            return unsigned - 0x90;
        }
        if (unsigned >= 0xc0 && unsigned <= 0xcf) {
            return ((unsigned - 0xc8) << 8) + unsigned(get());
        }
        if (unsigned >= 0xd0 && unsigned <= 0xd7) {
            return ((unsigned - 0xd4) << 16) + (unsigned(get()) << 8) + unsigned(get());
        }
        if (tag == 'I') {
            require(4);
            return buffer.getInt();
        }
        throw unexpected("int", tag);
    }

    private String readStringTag(byte firstTag) {
        StringBuilder output = new StringBuilder();
        byte tag = firstTag;
        while (true) {
            int unsigned = tag & 0xff;
            int chars;
            boolean last;
            if (unsigned <= 0x1f) {
                chars = unsigned;
                last = true;
            } else if (unsigned >= 0x30 && unsigned <= 0x33) {
                chars = ((unsigned - 0x30) << 8) + unsigned(get());
                last = true;
            } else if (tag == 'R' || tag == 'S') {
                require(2);
                chars = Short.toUnsignedInt(buffer.getShort());
                last = tag == 'S';
            } else {
                throw unexpected("string", tag);
            }
            for (int index = 0; index < chars; index++) {
                int current = unsigned(get());
                if (current < 0x80) {
                    output.append((char) current);
                } else if ((current & 0xe0) == 0xc0) {
                    output.append((char) ((current & 0x1f) << 6 | unsigned(get()) & 0x3f));
                } else if ((current & 0xf0) == 0xe0) {
                    output.append((char) ((current & 0x0f) << 12
                            | (unsigned(get()) & 0x3f) << 6 | unsigned(get()) & 0x3f));
                } else {
                    throw new DubboCodecException("Unsupported Hessian UTF-8 sequence");
                }
            }
            if (last) {
                return output.toString();
            }
            tag = get();
        }
    }

    private void readClassDefinition() {
        String type = readString();
        int fieldCount = readInt();
        if (fieldCount < 0 || fieldCount > maxCollectionItems) {
            throw new DubboCodecException("Invalid Hessian class field count " + fieldCount);
        }
        String[] fields = new String[fieldCount];
        for (int index = 0; index < fieldCount; index++) {
            fields[index] = readString();
        }
        if (classCount == classTypes.length) {
            classTypes = Arrays.copyOf(classTypes, classCount * 2);
            classFields = Arrays.copyOf(classFields, classCount * 2);
        }
        classTypes[classCount] = type;
        classFields[classCount] = fields;
        classCount++;
    }

    private void readType() {
        byte tag = peek();
        int unsigned = tag & 0xff;
        if (unsigned <= 0x1f || unsigned >= 0x30 && unsigned <= 0x33
                || tag == 'R' || tag == 'S') {
            types.add(readString());
            return;
        }
        int index = readInt();
        if (index < 0 || index >= types.size()) {
            throw new DubboCodecException("Invalid Hessian type reference " + index);
        }
    }

    private byte peek() {
        require(1);
        return buffer.get(buffer.position());
    }

    private byte get() {
        require(1);
        return buffer.get();
    }

    private void require(int bytes) {
        if (buffer == null || bytes < 0 || buffer.remaining() < bytes) {
            throw new DubboCodecException("Incomplete Hessian payload");
        }
    }

    private static void require(ByteBuffer source, int bytes) {
        if (bytes < 0 || source.remaining() < bytes) {
            throw new DubboCodecException("Incomplete Hessian payload");
        }
    }

    private static int unsigned(byte value) {
        return value & 0xff;
    }

    private static DubboCodecException unexpected(String expected, byte actual) {
        return new DubboCodecException(
                "Expected Hessian " + expected + " but got tag 0x" + Integer.toHexString(actual & 0xff));
    }

    private record ObjectDescriptor(String typeName, String[] fields) {
    }
}
