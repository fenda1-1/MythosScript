package com.zszl.zszlScriptMod.system;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.util.Arrays;

final class ProfileShareTextEncoding {
    // basE91 alphabet; no whitespace, backslash, apostrophe or Minecraft format marker.
    private static final String ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            + "!#$%&()*+,./:;<=>?@[]^_`{|}~\"";
    private ProfileShareTextEncoding() { }

    static String dense(byte[] data) {
        int bits = 0, buffer = 0;
        StringBuilder out = new StringBuilder();
        out.append((char) ('A' + (15 - data.length * 8 % 15) % 15));
        for (byte b : data) {
            buffer |= (b & 255) << bits;
            bits += 8;
            if (bits >= 15) {
                out.append(symbol(buffer & 32767));
                buffer >>>= 15;
                bits -= 15;
            }
        }
        if (bits > 0) out.append(symbol(buffer));
        return out.toString();
    }

    static byte[] undense(String text) throws IOException {
        text = Normalizer.normalize(text, Normalizer.Form.NFC);
        if (text.isEmpty()) throw new IOException("Empty share code");
        int padding = text.charAt(0) - 'A';
        int total = (text.length() - 1) * 15 - padding;
        if (padding < 0 || padding >= 15 || total < 0 || total % 8 != 0)
            throw new IOException("Truncated share code");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int bits = 0, buffer = 0;
        for (int i = 1; i < text.length(); i++) {
            int value = index(text.charAt(i));
            if (value < 0) throw new IOException("Invalid share code character");
            int valid = i == text.length() - 1 ? 15 - padding : 15;
            if (value >>> valid != 0) throw new IOException("Invalid share code padding");
            buffer |= value << bits;
            bits += valid;
            while (bits >= 8) {
                out.write(buffer & 255);
                buffer >>>= 8;
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    // 6,592 CJK Extension A + 20,992 CJK unified + 5,184 precomposed Hangul.
    private static char symbol(int n) {
        return (char) (n < 6592 ? 0x3400 + n : n < 27584 ? 0x4e00 + n - 6592 : 0xac00 + n - 27584);
    }

    private static int index(char c) {
        if (c >= 0x3400 && c <= 0x4dbf) return c - 0x3400;
        if (c >= 0x4e00 && c <= 0x9fff) return c - 0x4e00 + 6592;
        if (c >= 0xac00 && c < 0xac00 + 5184) return c - 0xac00 + 27584;
        return -1;
    }

    static String ascii(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer |= (b & 255) << bits;
            bits += 8;
            if (bits > 13) {
                int value = buffer & 8191;
                int consumed = value > 88 ? 13 : 14;
                if (consumed == 14) value = buffer & 16383;
                buffer >>>= consumed;
                bits -= consumed;
                out.append(ASCII.charAt(value % 91)).append(ASCII.charAt(value / 91));
            }
        }
        if (bits > 0) {
            out.append(ASCII.charAt(buffer % 91));
            if (bits > 7 || buffer > 90) out.append(ASCII.charAt(buffer / 91));
        }
        return out.toString();
    }

    static byte[] unascii(String text) throws IOException {
        int[] table = new int[128];
        Arrays.fill(table, -1);
        for (int i = 0; i < ASCII.length(); i++) table[ASCII.charAt(i)] = i;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0, bits = 0, first = -1;
        for (char c : text.toCharArray()) {
            if (c >= table.length || table[c] < 0) throw new IOException("Invalid basE91 character");
            if (first < 0) first = table[c];
            else {
                int value = first + table[c] * 91;
                buffer |= value << bits;
                bits += (value & 8191) > 88 ? 13 : 14;
                while (bits >= 8) {
                    out.write(buffer & 255);
                    buffer >>>= 8;
                    bits -= 8;
                }
                first = -1;
            }
        }
        if (first >= 0) out.write((buffer | first << bits) & 255);
        byte[] result = out.toByteArray();
        if (!ascii(result).equals(text)) throw new IOException("Non-canonical basE91 data");
        return result;
    }
}
