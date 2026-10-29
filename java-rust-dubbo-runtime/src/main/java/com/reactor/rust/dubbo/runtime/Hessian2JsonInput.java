package com.reactor.rust.dubbo.runtime;

import java.nio.ByteBuffer;

/** Direct-path-only row re-reader. No per-object reference index or materialized values. */
public final class Hessian2JsonInput {
    private static final int MAX_CLASSES = 64, MAX_FIELDS = 64, MAX_REFS = 1024, MAX_DEPTH = 32;
    private Hessian2Input input;
    private ByteBuffer source;
    private int collectionLimit;
    private final Hessian2Utf8Strings strings = new Hessian2Utf8Strings(this);
    private final String[] types = new String[MAX_CLASSES];
    private final String[][] fields = new String[MAX_CLASSES][];
    // The only retained wire-reference lookup anchors: root list and current row.
    private final int[] anchors = {-1, -1};
    private int classCount, typeCount, metadataChars, nextReference, baseReference, wireValues, replayDepth, work;
    private int searchReference;
    private boolean searching;
    private String[] currentFields;
    private String lastTypeName;
    private long scanned, scanLimit;

    Hessian2JsonInput() {}

    void attach(Hessian2Input input, long scanLimit) {
        this.input = input;
        source = input.jsonBuffer();
        collectionLimit = input.jsonCollectionLimit();
        this.scanLimit = scanLimit;
    }

    void detach() {
        input = null;
        source = null;
        currentFields = null;
        lastTypeName = null;
        scanned = scanLimit = 0;
        strings.detach();
        java.util.Arrays.fill(types, null);
        java.util.Arrays.fill(fields, null);
        java.util.Arrays.fill(anchors, -1);
        searching = false;
        classCount = typeCount = metadataChars = nextReference = baseReference = wireValues = replayDepth = work = collectionLimit = searchReference = 0;
    }

    boolean attachedTo(Hessian2Input input) { return this.input == input && source != null; }
    boolean detached() {
        if (input != null || source != null || currentFields != null || lastTypeName != null || !strings.detached()
                || anchors[0] != -1 || anchors[1] != -1 || searching) return false;
        for (int index = 0; index < MAX_CLASSES; index++) if (types[index] != null || fields[index] != null) return false;
        return true;
    }
    Hessian2Utf8Strings strings() { return strings; }
    public boolean readBoolean() { int start = position(); try { return input.readBoolean(); } finally { charge(position() - start); } }
    public int readInt() { int start = position(); try { return input.readInt(); } finally { charge(position() - start); } }
    public long readLong() { int start = position(); try { return input.readLong(); } finally { charge(position() - start); } }
    public double readDouble() { int start = position(); try { return input.readDouble(); } finally { charge(position() - start); } }

    record Root(int size, int offset, String typeName, int metadataChars, long scanned, int start) {}
    Root readRoot() {
        anchors[0] = position();
        int size = listStart();
        return new Root(size, position(), typeCount == 0 ? null : lastTypeName, metadataChars, scanned, anchors[0]);
    }
    void restoreRoot(Root root) {
        position(root.offset());
        typeCount = root.typeName() == null ? 0 : 1;
        metadataChars = root.metadataChars();
        charge(root.scanned());
        nextReference = 1;
        anchors[0] = root.start();
        beginRows();
    }
    void charge(long bytes) {
        scanned += bytes;
        if (scanned > scanLimit) throw new DubboCodecException("Direct JSON response wire-scan byte budget exceeded");
    }
    long scannedBytes() { return scanned; }

    int listStart() {
        int tag = get();
        int size;
        if (tag >= 0x78 && tag <= 0x7f) size = tag - 0x78;
        else if (tag >= 0x70 && tag <= 0x77) { readType(); size = tag - 0x70; }
        else if (tag == 'X') size = readInt();
        else if (tag == 'V') { readType(); size = readInt(); }
        else if (tag == 'U') { readType(); size = -1; }
        else if (tag == 'W') size = -1;
        else throw new DubboCodecException("Expected direct JSON Hessian list");
        if (size < -1 || size < 0 && tag != 'U' && tag != 'W' || size > collectionLimit) {
            throw new DubboCodecException("Invalid direct JSON list length or configured limit");
        }
        reserve();
        return size;
    }

    public int readListStart() { budget(); return listStart(); }
    public boolean hasMoreListEntries(int size, int count) {
        if (size < -1 || size > collectionLimit || count < 0 || count > collectionLimit) {
            throw new DubboCodecException("Invalid direct JSON list iteration or configured limit");
        }
        boolean more = hasMore(size, count);
        if (more) budget();
        return more;
    }
    public void readListEnd(int size) {
        if (size < -1 || size > collectionLimit) throw new DubboCodecException("Invalid direct JSON list length");
        listEnd(size);
    }

    void beginRows() { baseReference = nextReference; wireValues = 0; anchors[1] = -1; }
    void beginRow() { baseReference = nextReference; wireValues = 0; work = 0; anchors[1] = position(); }
    /** Retained wire-ID lookup anchors, including the root; no per-value spans are cached. */
    public int retainedReferences() { return (anchors[0] < 0 ? 0 : 1) + (anchors[1] < 0 ? 0 : 1); }
    int referenceSlotCapacity() { return anchors.length; }
    int wireValuesInRow() { return wireValues; }
    int peek() { require(1); return source.get(source.position()) & 255; }
    int get() { require(1); charge(1); return source.get() & 255; }
    boolean readNull() { if (peek() != 'N') return false; get(); return true; }
    boolean hasMore(int size, int count) {
        if (size >= 0) return count < size;
        if (peek() == 'Z') return false;
        if (count >= collectionLimit) throw new DubboCodecException("Direct JSON list exceeds configured limit");
        return true;
    }
    void listEnd(int size) { if (size < 0 && get() != 'Z') throw new DubboCodecException("Expected Hessian list end"); }

    int object(String expected) {
        budget();
        while (peek() == 'C') definition();
        int tag = get();
        int id = tag >= 0x60 && tag <= 0x6f ? tag - 0x60 : tag == 'O' ? readInt() : -1;
        if (id < 0 || id >= classCount) throw new DubboCodecException("Invalid direct JSON Hessian class reference");
        if (expected != null && !expected.equals(types[id])) throw new DubboCodecException("Direct JSON Hessian object type mismatch");
        currentFields = fields[id];
        return reserve();
    }
    String[] fields() { return currentFields; }
    void restoreFields(String[] enclosingFields) { currentFields = enclosingFields; }
    // Completion is verified by re-reading the target at lookup time, never cached.
    void complete(int reference) { }

    int beginReplay() {
        if (peek() != 'Q') return -1;
        int alias = position();
        get();
        int index = readInt();
        referenceSlot(index);
        if (index == baseReference || replayDepth == MAX_DEPTH) throw new DubboCodecException("Cyclic/unfinished or too-deep direct JSON reference");
        int restore = position(), limit = source.limit(), target;
        searching = true;
        searchReference = baseReference;
        try {
            // Do not inspect future bytes or register their definitions. A target must
            // finish strictly before this Q, including when a replay contains another Q.
            source.limit(alias);
            position(anchors[1]);
            target = scan(0, index);
            if (target < 0) throw new DubboCodecException("Unknown/forward direct JSON reference");
        } finally {
            source.limit(limit);
            position(restore);
            searching = false;
        }
        replayDepth++;
        position(target);
        return restore;
    }
    void endReplay(int restore) { if (restore >= 0) { source.position(restore); replayDepth--; } }

    private int reserve() {
        if (searching) {
            if (searchReference - baseReference >= MAX_REFS) throw new DubboCodecException("Direct JSON row exceeds 1024 wire values");
            return searchReference++;
        }
        if (replayDepth > 0) return -1;
        if (wireValues == MAX_REFS || nextReference == Integer.MAX_VALUE) throw new DubboCodecException("Direct JSON row exceeds 1024 wire values");
        wireValues++;
        return nextReference++;
    }
    private int referenceSlot(int index) {
        if (index < baseReference || index >= (searching ? searchReference : nextReference)) {
            throw new DubboCodecException("Direct JSON references cannot target the root list, a previous row or a forward value");
        }
        return index;
    }

    private void definition() {
        if (replayDepth > 0 || searching) {
            get(); skipMetadata();
            int count = readInt();
            if (count < 0 || count > MAX_FIELDS) throw new DubboCodecException("Direct JSON object exceeds 64 fields");
            for (int index = 0; index < count; index++) skipMetadata();
            return;
        }
        if (classCount == MAX_CLASSES) throw new DubboCodecException("Direct JSON class definition limit");
        get();
        String type = metadata();
        int count = readInt();
        if (count < 0 || count > MAX_FIELDS) throw new DubboCodecException("Direct JSON object exceeds 64 fields");
        String[] names = new String[count];
        for (int index = 0; index < count; index++) {
            names[index] = metadata();
            for (int earlier = 0; earlier < index; earlier++) {
                if (names[index].equals(names[earlier])) throw new DubboCodecException("Duplicate Hessian object field");
            }
        }
        types[classCount] = type;
        fields[classCount++] = names;
    }

    private String metadata() {
        Hessian2Utf8Strings cursor = strings;
        cursor.begin(source);
        StringBuilder text = new StringBuilder();
        int scalar;
        while ((scalar = cursor.scalar()) >= 0) {
            int chars = scalar >= 0x10000 ? 2 : 1;
            if (text.length() + chars > 256 || (metadataChars += chars) > 32768) {
                throw new DubboCodecException("Direct JSON metadata exceeds its character budget");
            }
            text.appendCodePoint(scalar);
        }
        return text.toString();
    }

    private void readType() {
        int tag = peek();
        if (tag <= 31 || tag >= 0x30 && tag <= 0x33 || tag == 'R' || tag == 'S') {
            if (replayDepth > 0 || searching) { skipMetadata(); return; }
            if (typeCount == MAX_CLASSES) throw new DubboCodecException("Direct JSON type table limit");
            lastTypeName = metadata();
            typeCount++;
        } else {
            int index = readInt();
            if (index < 0 || index >= typeCount) throw new DubboCodecException("Invalid Hessian type reference");
        }
    }

    private void skipMetadata() {
        Hessian2Utf8Strings cursor = strings; cursor.begin(source);
        int scalar, chars = 0;
        while ((scalar = cursor.scalar()) >= 0) {
            chars += scalar >= 0x10000 ? 2 : 1;
            if (chars > 256) throw new DubboCodecException("Direct JSON metadata exceeds its character budget");
        }
    }

    void skipValue() { scan(0, -1); }
    private int scan(int depth, int wanted) {
        budget();
        if (depth >= MAX_DEPTH) throw new DubboCodecException("Direct JSON nesting exceeds 32");
        int tag = peek();
        if (tag == 'N' || tag == 'T' || tag == 'F') { get(); return -1; }
        if (tag == 'Q') {
            get(); int index = readInt();
            if (index != 0) referenceSlot(index);
            return -1;
        }
        if (tag <= 31 || tag >= 0x30 && tag <= 0x33 || tag == 'R' || tag == 'S') {
            Hessian2Utf8Strings cursor = strings; cursor.begin(source);
            while (cursor.scalar() >= 0) { }
            return -1;
        }
        if (tag >= 0x20 && tag <= 0x2f || tag >= 0x34 && tag <= 0x37 || tag == 'A' || tag == 'B') {
            do {
                tag = get();
                int length = tag >= 0x20 && tag <= 0x2f ? tag - 0x20
                        : tag >= 0x34 && tag <= 0x37 ? ((tag - 0x34) << 8) | get() : (get() << 8) | get();
                if (!(tag >= 0x20 && tag <= 0x2f || tag >= 0x34 && tag <= 0x37 || tag == 'A' || tag == 'B')) {
                    throw new DubboCodecException("Invalid binary chunk");
                }
                require(length); charge(length); source.position(source.position() + length);
            } while (tag == 'A');
            return -1;
        }
        if (tag == 'J' || tag == 'K') { get(); int length = tag == 'J' ? 8 : 4; require(length); charge(length); source.position(source.position() + length); return -1; }
        if (tag >= 0x70 && tag <= 0x7f || tag == 'U' || tag == 'V' || tag == 'W' || tag == 'X') {
            int start = position(), reference = searching ? searchReference : nextReference;
            int size = listStart();
            boolean found = wanted >= 0 && reference == wanted;
            int count = 0;
            while (hasMore(size, count)) {
                int nested = scan(depth + 1, found ? -1 : wanted);
                if (nested >= 0) return nested;
                count++;
            }
            listEnd(size); return found ? start : -1;
        }
        if (tag == 'H' || tag == 'M') {
            int start = source.position(); get(); if (tag == 'M') readType();
            int reference = reserve(), count = 0;
            boolean found = wanted >= 0 && reference == wanted;
            while (peek() != 'Z') {
                if (++count > collectionLimit) throw new DubboCodecException("Direct JSON map exceeds configured limit");
                int key = scan(depth + 1, found ? -1 : wanted);
                if (key >= 0) return key;
                int value = scan(depth + 1, found ? -1 : wanted);
                if (value >= 0) return value;
            }
            get(); return found ? start : -1;
        }
        if (tag == 'C' || tag == 'O' || tag >= 0x60 && tag <= 0x6f) {
            int start = position(), reference = object(null);
            boolean found = wanted >= 0 && reference == wanted;
            String[] names = fields();
            for (int index = 0; index < names.length; index++) {
                int nested = scan(depth + 1, found ? -1 : wanted);
                if (nested >= 0) return nested;
            }
            return found ? start : -1;
        }
        readDouble();
        return -1;
    }

    int position() { return source.position(); }
    void position(int position) { source.position(position); }
    private void require(int bytes) {
        if (bytes < 0 || source.remaining() < bytes) throw new DubboCodecException("Incomplete direct JSON Hessian payload");
    }

    private void budget() {
        if (++work > 65536) throw new DubboCodecException("Direct JSON row expansion exceeds its work budget");
    }
}
