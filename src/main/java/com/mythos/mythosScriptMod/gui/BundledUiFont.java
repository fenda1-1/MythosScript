package com.mythos.mythosScriptMod.gui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/** Loads the shipped font directly, without relying on OS font discovery. */
final class BundledUiFont {
    private static final String RESOURCE = "/assets/mythos_script/fonts/NotoSansCJKsc-Regular.ttf";

    private BundledUiFont() {
    }

    static Font load() throws IOException, FontFormatException {
        try (InputStream stream = BundledUiFont.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IOException("Missing bundled UI font: " + RESOURCE);
            }
            // TrueType outlines avoid the CFF/T2K path that stalled Java 8 menu rendering.
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            if (font.canDisplayUpTo("控制中心路径动作参数搜索默认分类") >= 0) {
                throw new IOException("Bundled UI font lacks required Chinese glyphs");
            }
            return font;
        }
    }
}
