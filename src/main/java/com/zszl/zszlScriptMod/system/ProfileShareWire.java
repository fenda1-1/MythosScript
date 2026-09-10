package com.zszl.zszlScriptMod.system;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

final class ProfileShareWire {
    static final int LIMIT = 4 * 1024 * 1024;
    private ProfileShareWire() { }

    static void number(OutputStream out, int value) throws IOException {
        if (value < 0) throw new IOException("Negative length");
        while (value > 127) {
            out.write((value & 127) | 128);
            value >>>= 7;
        }
        out.write(value);
    }

    static int number(InputStream in) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.read();
            if (b < 0) throw new EOFException("Truncated share code");
            value |= (long) (b & 127) << shift;
            if ((b & 128) == 0) {
                if (value > Integer.MAX_VALUE) throw new IOException("Invalid length");
                return (int) value;
            }
        }
        throw new IOException("Invalid VarInt");
    }

    static byte[] bytes(InputStream in, int size) throws IOException {
        if (size < 0 || size > LIMIT) throw new IOException("Share code exceeds size limit");
        byte[] result = new byte[size];
        new DataInputStream(in).readFully(result);
        return result;
    }

    static String string(InputStream in) throws IOException {
        byte[] value = bytes(in, number(in));
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value)).toString();
    }

    static void string(OutputStream out, String text) throws IOException {
        byte[] value = text.getBytes(StandardCharsets.UTF_8);
        number(out, value.length);
        out.write(value);
    }

    static byte[] bounded(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > LIMIT) throw new IOException("Share code exceeds size limit");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
