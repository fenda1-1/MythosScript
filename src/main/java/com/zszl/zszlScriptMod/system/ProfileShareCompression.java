package com.zszl.zszlScriptMod.system;

import com.aayushatharva.brotli4j.Brotli4jLoader;
import com.aayushatharva.brotli4j.encoder.Encoder;
import com.github.luben.zstd.Zstd;
import io.airlift.compress.zstd.ZstdDecompressor;
import org.brotli.dec.BrotliInputStream;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** ZS3 envelope: codec/transform byte, decoded size, CRC32 of original payload, compressed bytes. */
public final class ProfileShareCompression {
    public static final String PREFIX = "ZS3!";
    public static final String ASCII_PREFIX = "ZS3~";
    private static final int RAW = 0, DEFLATE = 1, BROTLI = 2, ZSTD = 3, LZMA = 4, DICTIONARY = 5;
    private static final int PACKED = 8;
    // This protocol dictionary is immutable. Changing it requires a new codec ID.
    private static final byte[] DICT = ("name enabled category categories hiddenCategories sequences rules profiles "
            + "nodes actions settings version id type command description delay timeout radius distance speed mode "
            + "target targets filters key keyCode x y z yaw pitch true false null "
            + "\"name\":\"\" \"enabled\":true \"enabled\":false \"category\":\"默认\" "
            + "\"actions\":[] \"nodes\":[] \"rules\":[] \"sequences\":[] \"version\":1 "
            + "autoFollow killAura autoPickup blockReplacement pathName sequenceName returnPoints "
            + "timeoutReloadEnabled timeoutReloadSeconds legacyUnknownField")
            .getBytes(StandardCharsets.UTF_8);

    private ProfileShareCompression() { }

    public static boolean isNewCode(String code) {
        return code.startsWith(PREFIX) || code.startsWith(ASCII_PREFIX);
    }

    public static String encode(byte[] payload) throws IOException {
        return encode(payload, false);
    }

    public static String encode(byte[] payload, boolean ascii) throws IOException {
        if (payload.length == 0 || payload.length > ProfileShareWire.LIMIT)
            throw new IOException("Share payload exceeds 4 MiB");
        Candidate best = candidates(payload, payload, false, ascii, null);
        try {
            byte[] packed = ProfileSharePacking.transform(payload, true);
            if (Arrays.equals(payload, ProfileSharePacking.transform(packed, false)))
                best = candidates(payload, packed, true, ascii, best);
        } catch (IOException ignored) {
            // Non-JSON and deeply nested documents retain the untransformed candidate.
        }
        if (best == null || !Arrays.equals(payload, decode(best.code)))
            throw new IOException("Share code round-trip verification failed");
        return best.code;
    }

    private static Candidate candidates(byte[] original, byte[] input, boolean packed, boolean ascii,
            Candidate best) throws IOException {
        best = choose(original, input, input, RAW, packed, ascii, best);
        for (int level = 1; level <= 9; level++) {
            for (int strategy : new int[] { Deflater.DEFAULT_STRATEGY, Deflater.FILTERED, Deflater.HUFFMAN_ONLY }) {
                best = choose(original, input, deflate(input, level, strategy, false),
                        DEFLATE, packed, ascii, best);
                if (strategy != Deflater.HUFFMAN_ONLY)
                    best = choose(original, input, deflate(input, level, strategy, true),
                            DICTIONARY, packed, ascii, best);
            }
        }
        if (Brotli4jLoader.isAvailable()) {
            for (Encoder.Mode mode : new Encoder.Mode[] { Encoder.Mode.GENERIC, Encoder.Mode.TEXT }) {
                byte[] bytes = Encoder.compress(input, new Encoder.Parameters().setQuality(11).setMode(mode));
                best = choose(original, input, bytes, BROTLI, packed, ascii, best);
            }
        }
        try {
            for (int level : new int[] { 9, 15, 19, 22 })
                best = choose(original, input, Zstd.compress(input, level), ZSTD, packed, ascii, best);
        } catch (LinkageError unavailableNative) {
            // All other encoders and the pure-Java Brotli decoder remain usable.
        }
        LZMA2Options options = new LZMA2Options(9);
        options.setDictSize(Math.min(1 << 22, Math.max(4096, Integer.highestOneBit(input.length - 1) << 1)));
        for (int pb : new int[] { 0, 2 }) {
            options.setPb(pb);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(options.getLc() + 9 * (options.getLp() + 5 * options.getPb()));
            try (LZMAOutputStream stream = new LZMAOutputStream(bytes, options, false)) {
                stream.write(input);
            }
            best = choose(original, input, bytes.toByteArray(), LZMA, packed, ascii, best);
        }
        return best;
    }

    private static Candidate choose(byte[] original, byte[] input, byte[] compressed, int codec,
            boolean packed, boolean ascii, Candidate current) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(codec | (packed ? PACKED : 0));
        ProfileShareWire.number(out, input.length);
        CRC32 crc = new CRC32();
        crc.update(original);
        new DataOutputStream(out).writeInt((int) crc.getValue());
        out.write(compressed);
        String code = ascii ? ASCII_PREFIX + ProfileShareTextEncoding.ascii(out.toByteArray())
                : PREFIX + ProfileShareTextEncoding.dense(out.toByteArray());
        if (current == null || code.length() < current.code.length()
                || (code.length() == current.code.length() && out.size() < current.bytes))
            return new Candidate(code, out.size());
        return current;
    }

    public static byte[] decode(String code) throws IOException {
        if (code.length() > ProfileShareWire.LIMIT * 2) throw new IOException("Share code too long");
        byte[] envelope;
        if (code.startsWith(PREFIX))
            envelope = ProfileShareTextEncoding.undense(code.substring(PREFIX.length()));
        else if (code.startsWith(ASCII_PREFIX))
            envelope = ProfileShareTextEncoding.unascii(code.substring(ASCII_PREFIX.length()));
        else throw new IOException("Unknown share code prefix");
        ByteArrayInputStream in = new ByteArrayInputStream(envelope);
        int flags = in.read();
        int codec = flags & 7;
        if (flags < 0 || (flags & ~15) != 0 || codec > DICTIONARY) throw new IOException("Unknown codec");
        int size = ProfileShareWire.number(in);
        if (size < 1 || size > ProfileShareWire.LIMIT) throw new IOException("Invalid payload size");
        int expectedCrc = new DataInputStream(in).readInt();
        byte[] compressed = ProfileShareWire.bounded(in);
        byte[] expanded = decompress(compressed, codec, size);
        if (expanded.length != size) throw new IOException("Share code size mismatch");
        byte[] original = (flags & PACKED) == 0 ? expanded : ProfileSharePacking.transform(expanded, false);
        CRC32 crc = new CRC32();
        crc.update(original);
        if ((int) crc.getValue() != expectedCrc) throw new IOException("Share code checksum mismatch");
        return original;
    }

    private static byte[] decompress(byte[] bytes, int codec, int size) throws IOException {
        if (codec == RAW) return bytes;
        if (codec == DEFLATE || codec == DICTIONARY) {
            Inflater inflater = new Inflater(true);
            try {
                if (codec == DICTIONARY) inflater.setDictionary(DICT);
                inflater.setInput(bytes);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                while (!inflater.finished()) {
                    int n = inflater.inflate(buffer);
                    if (n == 0 && !inflater.finished()) throw new IOException("Invalid Deflate stream");
                    if (out.size() + n > size) throw new IOException("Expanded data too large");
                    out.write(buffer, 0, n);
                }
                if (inflater.getRemaining() != 0) throw new IOException("Unexpected Deflate suffix");
                return out.toByteArray();
            } catch (DataFormatException e) {
                throw new IOException("Invalid Deflate data", e);
            } finally {
                inflater.end();
            }
        }
        if (codec == ZSTD) {
            try {
                byte[] out = new byte[size];
                int result = new ZstdDecompressor().decompress(bytes, 0, bytes.length, out, 0, out.length);
                if (result != size) throw new IOException("Invalid Zstandard stream");
                return out;
            } catch (RuntimeException e) {
                throw new IOException("Invalid Zstandard stream", e);
            }
        }
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try (InputStream decoder = codec == BROTLI ? new BrotliInputStream(in)
                : new LZMAInputStream(in, size, (byte) in.read(), 1 << 22)) {
            return ProfileShareWire.bounded(decoder);
        } catch (RuntimeException e) {
            throw new IOException("Invalid compressed share code", e);
        }
    }

    private static byte[] deflate(byte[] bytes, int level, int strategy, boolean dictionary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deflater compressor = new Deflater(level, true);
        compressor.setStrategy(strategy);
        if (dictionary) compressor.setDictionary(DICT);
        try (DeflaterOutputStream stream = new DeflaterOutputStream(out, compressor)) {
            stream.write(bytes);
        } finally {
            compressor.end();
        }
        return out.toByteArray();
    }

    private static final class Candidate {
        final String code;
        final int bytes;
        Candidate(String code, int bytes) { this.code = code; this.bytes = bytes; }
    }
}
