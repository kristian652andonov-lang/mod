package com.aigirlfriend.ai.local;

/** ggml tensor types as stored in GGUF files (only the ones the local engine can read are marked supported). */
public enum GGMLType {
    F32(0, 1, 4, true),
    F16(1, 1, 2, true),
    Q4_0(2, 32, 18, true),
    Q4_1(3, 32, 20, true),
    Q5_0(6, 32, 22, true),
    Q5_1(7, 32, 24, true),
    Q8_0(8, 32, 34, true),
    Q8_1(9, 32, 36, false),
    Q2_K(10, 256, 84, false),
    Q3_K(11, 256, 110, false),
    Q4_K(12, 256, 144, true),
    Q5_K(13, 256, 176, true),
    Q6_K(14, 256, 210, true),
    Q8_K(15, 256, 292, false),
    BF16(30, 1, 2, true);

    public final int id;
    public final int blockSize;
    public final int typeSize;
    public final boolean supported;

    GGMLType(int id, int blockSize, int typeSize, boolean supported) {
        this.id = id;
        this.blockSize = blockSize;
        this.typeSize = typeSize;
        this.supported = supported;
    }

    public long byteSize(long elements) {
        return elements / blockSize * typeSize;
    }

    public static GGMLType byId(int id) {
        for (GGMLType t : values()) if (t.id == id) return t;
        throw new IllegalArgumentException("Unsupported ggml tensor type " + id);
    }
}
