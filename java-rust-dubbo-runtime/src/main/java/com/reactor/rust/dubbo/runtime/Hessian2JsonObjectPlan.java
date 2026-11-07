package com.reactor.rust.dubbo.runtime;

import java.io.IOException;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Immutable AOT field plan; the shared loop bounds nested codec JIT expansion. */
public final class Hessian2JsonObjectPlan implements Hessian2JsonWriter {
    public static final int CUSTOM = 0, BOOLEAN = 1, BYTE = 2, SHORT = 3, INT = 4,
            LONG = 5, FLOAT = 6, DOUBLE = 7, CHAR = 8, STRING = 9, DECIMAL = 10,
            DATE = 11, NULLABLE = 16;
    // Resolve once, not in the row loop: keep unrelated scalar codec graphs apart.
    private static final Hessian2JsonWriter[] SCALARS = {
        null,
        (in, json) -> json.writeBoolean(json.input(in).readBoolean()),
        (in, json) -> json.writeLongAscii((byte) json.input(in).readInt()),
        (in, json) -> json.writeLongAscii((short) json.input(in).readInt()),
        (in, json) -> json.writeLongAscii(json.input(in).readInt()),
        (in, json) -> json.writeLongAscii(json.input(in).readLong()),
        (in, json) -> json.writeFloatAscii((float) json.input(in).readDouble()),
        (in, json) -> json.writeDoubleAscii(json.input(in).readDouble()),
        (in, json) -> json.writeChar(json.input(in).readChar()),
        (in, json) -> json.writeString(in),
        (in, json) -> json.writeBigDecimal(in),
        (in, json) -> json.writeLocalDate(in)
    };
    private static final Hessian2JsonWriter NULLABLE_STRING = (in, json) -> json.writeNullableString(in);
    private final String type;
    private final ToIntFunction<String> fieldIndex;
    private final String[] wireFields;
    private final byte[][] fields;
    private final int[] kinds;
    private final Hessian2JsonWriter[] writers;
    private final long completeMask;
    private final long nullableProbeMask;

    public Hessian2JsonObjectPlan(String type, ToIntFunction<String> fieldIndex,
            byte[][] fields, int[] kinds, Hessian2JsonWriter[] writers) {
        this(type, fieldIndex, null, fields, kinds, writers);
    }

    /** Wire names must follow the stable generated field-index mapping. */
    public Hessian2JsonObjectPlan(String type, ToIntFunction<String> fieldIndex, String[] wireFields,
            byte[][] fields, int[] kinds, Hessian2JsonWriter[] writers) {
        this.type = Objects.requireNonNull(type, "type");
        this.fieldIndex = Objects.requireNonNull(fieldIndex, "fieldIndex");
        if (type.length() > 256 || fields.length > 64 || fields.length != kinds.length
                || fields.length != writers.length || wireFields != null && wireFields.length != fields.length)
            throw new IllegalArgumentException("Invalid direct JSON object plan");
        this.wireFields = wireFields == null ? null : wireFields.clone();
        if (this.wireFields != null) {
            for (int index = 0; index < this.wireFields.length; index++) {
                String field = this.wireFields[index];
                if (field == null || field.length() > 256) throw new IllegalArgumentException("Invalid direct JSON wire field");
                for (int earlier = 0; earlier < index; earlier++) {
                    if (field.equals(this.wireFields[earlier]))
                        throw new IllegalArgumentException("Duplicate direct JSON wire field");
                }
                if (fieldIndex.applyAsInt(field) != index)
                    throw new IllegalArgumentException("Direct JSON wire names must follow the generated field-index mapping");
            }
        }
        this.fields = new byte[fields.length][];
        this.kinds = kinds.clone();
        this.writers = writers.clone();
        this.completeMask = fields.length == 64 ? -1L : (1L << fields.length) - 1;
        long nullableProbes = 0;
        for (int index = 0; index < fields.length; index++) {
            int kind = kinds[index] & ~NULLABLE;
            if (kind < CUSTOM || kind > DATE || (kinds[index] & ~(NULLABLE | 15)) != 0
                    || kind == CUSTOM && writers[index] == null) {
                throw new IllegalArgumentException("Invalid direct JSON field codec");
            }
            this.fields[index] = Objects.requireNonNull(fields[index], "field literal").clone();
            if (kind != CUSTOM) this.writers[index] = SCALARS[kind];
            if ((kinds[index] & NULLABLE) != 0) {
                // Reference scalar writers consume their own null tag; primitives and custom writers do not.
                if (kind == STRING) this.writers[index] = NULLABLE_STRING;
                else if (kind != DECIMAL && kind != DATE) nullableProbes |= 1L << index;
            }
        }
        this.nullableProbeMask = nullableProbes;
    }

    @Override
    public void write(Hessian2Input in, Hessian2JsonTranscoder json) throws IOException {
        if (json.writeNull(in)) return;
        if (json.isReference(in) && json.replayReference(in, this)) return;
        writeResolved(in, json);
    }

    @Override
    public void writeResolved(Hessian2Input in, Hessian2JsonTranscoder json) throws IOException {
        int reference = json.beginObject(in, type, wireFields);
        String[] incoming = json.objectFields(in);
        long seen = 0;
        json.writeByte('{');
        for (int ordinal = 0; ordinal < incoming.length; ordinal++) {
            String field = incoming[ordinal];
            // Canonical names were validated against the stable AOT mapping at construction.
            int index = wireFields != null && ordinal < wireFields.length && field == wireFields[ordinal]
                    ? ordinal : fieldIndex.applyAsInt(field);
            if (index < 0) { json.skipValue(in); continue; }
            if (index >= fields.length) throw new DubboCodecException("Invalid generated JSON field index");
            json.field(fields[index], seen == 0);
            seen |= 1L << index;
            if ((nullableProbeMask & (1L << index)) == 0 || !json.writeNull(in))
                writers[index].write(in, json);
        }
        if (seen != completeMask) {
            for (int index = 0; index < fields.length; index++) {
                if ((seen & (1L << index)) != 0) continue;
                json.field(fields[index], seen == 0);
                seen |= 1L << index;
                writeDefault(json, kinds[index]);
            }
        }
        json.writeByte('}');
        json.endObject(in, reference);
    }

    private static void writeDefault(Hessian2JsonTranscoder json, int kind) throws IOException {
        if ((kind & NULLABLE) != 0) { json.writeNull(); return; }
        switch (kind) {
            case BOOLEAN -> json.writeBoolean(false);
            case CHAR -> json.writeChar((char) 0);
            case FLOAT -> json.writeFloatAscii(0.0f);
            case DOUBLE -> json.writeDoubleAscii(0.0d);
            default -> json.writeLongAscii(0L);
        }
    }
}
