package dev.drawethree.essdash.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * A minimal, read-only NBT parser for the (gzipped) {@code <uuid>.dat} files Minecraft
 * writes under a world's {@code playerdata/} folder. Self-contained, no dependencies.
 *
 * <p>Tags are decoded to plain Java values: TAG_Compound → {@link Map}, TAG_List →
 * {@link List}, numbers → boxed primitives, TAG_String → {@link String}, the array
 * tags → {@code byte[]}/{@code int[]}/{@code long[]}. NBT strings use the same
 * modified-UTF-8 encoding as {@link DataInputStream#readUTF()}, which we reuse.
 */
public final class NbtReader {

    private NbtReader() {}

    // Tag type ids (see the NBT specification).
    private static final int TAG_END = 0, TAG_BYTE = 1, TAG_SHORT = 2, TAG_INT = 3,
            TAG_LONG = 4, TAG_FLOAT = 5, TAG_DOUBLE = 6, TAG_BYTE_ARRAY = 7,
            TAG_STRING = 8, TAG_LIST = 9, TAG_COMPOUND = 10, TAG_INT_ARRAY = 11,
            TAG_LONG_ARRAY = 12;

    /** Reads a gzipped NBT file and returns its root compound, or null if it cannot be parsed. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readGzipFile(Path file) throws IOException {
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(raw)))) {
            int rootType = in.readUnsignedByte();
            if (rootType != TAG_COMPOUND) return null;
            in.readUTF(); // root name (usually empty)
            Object root = readPayload(in, rootType);
            return root instanceof Map ? (Map<String, Object>) root : null;
        }
    }

    private static Object readPayload(DataInputStream in, int type) throws IOException {
        switch (type) {
            case TAG_BYTE: return in.readByte();
            case TAG_SHORT: return in.readShort();
            case TAG_INT: return in.readInt();
            case TAG_LONG: return in.readLong();
            case TAG_FLOAT: return in.readFloat();
            case TAG_DOUBLE: return in.readDouble();
            case TAG_BYTE_ARRAY: {
                byte[] arr = new byte[in.readInt()];
                in.readFully(arr);
                return arr;
            }
            case TAG_STRING: return in.readUTF();
            case TAG_LIST: {
                int elemType = in.readUnsignedByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) list.add(readPayload(in, elemType));
                return list;
            }
            case TAG_COMPOUND: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == TAG_END) break;
                    String name = in.readUTF();
                    map.put(name, readPayload(in, childType));
                }
                return map;
            }
            case TAG_INT_ARRAY: {
                int[] arr = new int[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readInt();
                return arr;
            }
            case TAG_LONG_ARRAY: {
                long[] arr = new long[in.readInt()];
                for (int i = 0; i < arr.length; i++) arr[i] = in.readLong();
                return arr;
            }
            default:
                throw new IOException("Unknown NBT tag type: " + type);
        }
    }
}
