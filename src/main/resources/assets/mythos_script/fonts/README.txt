Bundled UI font: Noto Sans CJK SC Regular
Upstream: https://github.com/notofonts/noto-cjk
Source path: Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf
Download: https://cdn.jsdelivr.net/gh/notofonts/noto-cjk@main/Sans/OTF/SimplifiedChinese/NotoSansCJKsc-Regular.otf
Retrieved: 2026-09-19
SHA-256: 2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b
License: SIL Open Font License 1.1 (see OFL.txt).
The bundled TTF is converted from the upstream CFF OTF to TrueType outlines
using fontTools cu2qu (1 font-unit tolerance), retaining all 65,535 glyphs.
Copyright notices and OFL license are retained. See tools/convert_ui_font.py.

Font initialization and text rasterization run on a bounded background worker.
Only completed images are uploaded on the game render thread.

Loaded directly from the mod JAR using Java AWT Font.createFont. No system
font installation or runtime download is required. The modern in-game UI
and detached window share this font. Unsupported text and font loading
failures fall back to Minecraft's font renderer.
