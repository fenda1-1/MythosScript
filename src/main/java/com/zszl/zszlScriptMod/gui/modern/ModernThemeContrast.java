package com.zszl.zszlScriptMod.gui.modern;

public final class ModernThemeContrast {
    private ModernThemeContrast() {
    }

    public static int readable(int foreground, int background) {
        if (ratio(foreground, background) >= 4.5D) {
            return foreground;
        }
        int rgb = ratio(0xFF000000, background) >= ratio(0xFFFFFFFF, background) ? 0 : 0x00FFFFFF;
        return (foreground & 0xFF000000) | rgb;
    }

    public static double ratio(int first, int second) {
        double firstLuminance = luminance(first);
        double secondLuminance = luminance(second);
        return (Math.max(firstLuminance, secondLuminance) + 0.05D)
                / (Math.min(firstLuminance, secondLuminance) + 0.05D);
    }

    public static int composite(int foreground, int opaqueBackground) {
        int alpha = foreground >>> 24;
        int result = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int foregroundChannel = (foreground >>> shift) & 255;
            int backgroundChannel = (opaqueBackground >>> shift) & 255;
            result |= ((foregroundChannel * alpha + backgroundChannel * (255 - alpha) + 127) / 255) << shift;
        }
        return result;
    }

    private static double luminance(int color) {
        return linear((color >>> 16) & 255) * 0.2126D
                + linear((color >>> 8) & 255) * 0.7152D + linear(color & 255) * 0.0722D;
    }

    private static double linear(int channel) {
        double value = channel / 255.0D;
        return value <= 0.04045D ? value / 12.92D : Math.pow((value + 0.055D) / 1.055D, 2.4D);
    }
}
