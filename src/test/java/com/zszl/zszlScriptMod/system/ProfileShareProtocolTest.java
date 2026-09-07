package com.zszl.zszlScriptMod.system;

import com.google.gson.*;
import net.minecraft.util.ChatAllowedCharacters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;
import static org.junit.Assert.*;

public class ProfileShareProtocolTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String FILE = "keycommand_killaura.json";

    @Test public void textEncodingsRoundTripAndSurviveMinecraftAndNormalization() throws Exception {
        Random random = new Random(81723);
        for (int size = 0; size < 320; size++) {
            byte[] bytes = new byte[size];
            random.nextBytes(bytes);
            String dense = ProfileShareTextEncoding.dense(bytes);
            assertEquals(dense, ChatAllowedCharacters.filterAllowedCharacters(dense));
            assertEquals(dense, Normalizer.normalize(dense, Normalizer.Form.NFKC));
            assertArrayEquals(bytes, ProfileShareTextEncoding.undense(dense));
            assertArrayEquals(bytes, ProfileShareTextEncoding.undense(
                    Normalizer.normalize(dense, Normalizer.Form.NFD)));
            assertArrayEquals(bytes, ProfileShareTextEncoding.unascii(ProfileShareTextEncoding.ascii(bytes)));
        }
        for (int value = 0; value < 32768; value++) {
            byte[] bytes = { (byte) value, (byte) (value >>> 8) };
            String dense = ProfileShareTextEncoding.dense(bytes);
            assertEquals(dense, ChatAllowedCharacters.filterAllowedCharacters(dense));
            assertEquals(dense, Normalizer.normalize(dense, Normalizer.Form.NFKC));
            assertArrayEquals(bytes, ProfileShareTextEncoding.undense(dense));
        }
    }

    @Test public void packingPreservesUnknownFieldsNumbersNullsAndUnicode() throws Exception {
        String json = "{\"legacy/key~\":{\"unknown\":null,\"name\":\"中文\\n\\\"路径😀\"},"
                + "\"values\":[-0,1.000,1e+99,123456789012345678901234567890,true,false,{},[]]}";
        byte[] bytes = payload(json, true);
        assertArrayEquals(bytes, ProfileSharePacking.transform(ProfileSharePacking.transform(bytes, true), false));
        String code = ProfileShareCompression.encode(bytes);
        assertArrayEquals(bytes, ProfileShareCompression.decode(code));
        String ascii = ProfileShareCompression.encode(bytes, true);
        assertArrayEquals(bytes, ProfileShareCompression.decode(ascii));
    }

    @Test public void everyCompressionBackendHasAWorkingDecoder() throws Exception {
        byte[] bytes = payload("{\"rules\":[{\"name\":\"中文\",\"steps\":[1,2,3],\"old\":null}]}", false);
        Method decode = ProfileShareCompression.class.getDeclaredMethod("decompress", byte[].class, int.class, int.class);
        decode.setAccessible(true);
        assertArrayEquals(bytes, (byte[]) decode.invoke(null, bytes, 0, bytes.length));
        Method deflate = ProfileShareCompression.class.getDeclaredMethod("deflate",
                byte[].class, int.class, int.class, boolean.class);
        deflate.setAccessible(true);
        for (boolean dictionary : new boolean[] { false, true }) {
            byte[] compressed = (byte[]) deflate.invoke(null, bytes, 9, Deflater.FILTERED, dictionary);
            assertArrayEquals(bytes, (byte[]) decode.invoke(null, compressed, dictionary ? 5 : 1, bytes.length));
        }
        com.aayushatharva.brotli4j.Brotli4jLoader.ensureAvailability();
        byte[] brotli = com.aayushatharva.brotli4j.encoder.Encoder.compress(bytes,
                new com.aayushatharva.brotli4j.encoder.Encoder.Parameters().setQuality(11));
        assertArrayEquals(bytes, (byte[]) decode.invoke(null, brotli, 2, bytes.length));
        byte[] zstd = com.github.luben.zstd.Zstd.compress(bytes, 22);
        assertArrayEquals(bytes, (byte[]) decode.invoke(null, zstd, 3, bytes.length));
        org.tukaani.xz.LZMA2Options options = new org.tukaani.xz.LZMA2Options(9);
        options.setDictSize(4096);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(93);
        try (org.tukaani.xz.LZMAOutputStream stream = new org.tukaani.xz.LZMAOutputStream(output, options, false)) {
            stream.write(bytes);
        }
        assertArrayEquals(bytes, (byte[]) decode.invoke(null, output.toByteArray(), 4, bytes.length));
    }

    @Test public void fullExportImportPreservesAllFields() throws Exception {
        Path source = temporary.newFolder().toPath(), target = temporary.newFolder().toPath();
        String json = "{\"enabled\":true,\"legacyUnknown\":null,\"large\":12345678901234567890,"
                + "\"decimal\":1.000000000000000000001,\"name\":\"中文😀\"}";
        Files.write(source.resolve(FILE), json.getBytes(StandardCharsets.UTF_8));
        String code = ProfileShareCodeManager.generateShareCode(source.toString(), Collections.singleton(FILE));
        assertTrue(code.startsWith("ZS3!"));
        ProfileShareCodeManager.importShareCode(code, target.toString());
        assertEquals(new JsonParser().parse(json), new JsonParser().parse(
                new String(Files.readAllBytes(target.resolve(FILE)), StandardCharsets.UTF_8)));
    }

    @Test public void pathConflictsDefaultToReplaceAndCanAppendOrderedDuplicateSteps() throws Exception {
        Path source = temporary.newFolder().toPath();
        String file = "custom_paths.json";
        String imported = "{\"sequences\":[{\"name\":\"A\",\"category\":\"G\",\"steps\":[2,2,3]},"
                + "{\"name\":\"A\",\"category\":\"Other\",\"steps\":[4]}]}";
        Files.write(source.resolve(file), imported.getBytes(StandardCharsets.UTF_8));
        for (boolean partial : new boolean[] { false, true }) {
            String newCode = ProfileShareCodeManager.generateShareCode(source.toString(), Collections.singleton(file),
                    partial ? Collections.singletonMap(file, Collections.singleton("/sequences/0")) : null);
            byte[] raw = ProfileShareCompression.decode(newCode);
            String oldCode = (partial ? "ZS3." : "ZS2.") + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(oldDeflate(raw));
            for (String code : new String[] { newCode, oldCode }) {
                for (ProfileShareCodeManager.PathConflictMode mode : ProfileShareCodeManager.PathConflictMode.values()) {
                    Path target = temporary.newFolder().toPath();
                    Files.write(target.resolve(file),
                            "{\"sequences\":[{\"name\":\"A\",\"category\":\"G\",\"steps\":[1,2]}]}"
                                    .getBytes(StandardCharsets.UTF_8));
                    ProfileShareCodeManager.ImportPreview preview =
                            ProfileShareCodeManager.previewImport(code, target.toString());
                    assertEquals(1, preview.getPathConflictCount());
                    ProfileShareCodeManager.applyImportPreview(preview, null, mode);
                    JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(target.resolve(file)),
                            StandardCharsets.UTF_8)).getAsJsonObject();
                    String expected = mode == ProfileShareCodeManager.PathConflictMode.REPLACE
                            ? "[2,2,3]" : "[1,2,2,2,3]";
                    assertEquals(new JsonParser().parse(expected),
                            root.getAsJsonArray("sequences").get(0).getAsJsonObject().get("steps"));
                    assertEquals(partial ? 1 : 2, root.getAsJsonArray("sequences").size());
                }
            }
        }
    }

    @Test public void partialImportPreservesLocalFieldsAndOtherPaths() throws Exception {
        Path source = temporary.newFolder().toPath(), target = temporary.newFolder().toPath();
        String paths = "custom_paths.json";
        Files.write(source.resolve(paths), ("{\"version\":5,\"sequences\":["
                + "{\"name\":\"A\",\"nodes\":[1]},"
                + "{\"name\":\"B\",\"category\":\"new\",\"nodes\":[2]}]}").getBytes(StandardCharsets.UTF_8));
        Files.write(target.resolve(paths), ("{\"localFlag\":true,\"sequences\":["
                + "{\"name\":\"A\",\"nodes\":[9]},"
                + "{\"name\":\"B\",\"oldUnknown\":42,\"nodes\":[8]}]}").getBytes(StandardCharsets.UTF_8));
        String code = ProfileShareCodeManager.generateShareCode(source.toString(), Collections.singleton(paths),
                Collections.singletonMap(paths, Collections.singleton("/sequences/1")));
        ProfileShareCodeManager.ImportPreview preview = ProfileShareCodeManager.previewImport(code, target.toString());
        assertEquals(0, preview.getPathConflictCount());
        JsonObject imported = new JsonParser().parse(preview.getEntries().get(0).getImportedContent()).getAsJsonObject();
        assertEquals(1, imported.getAsJsonArray("sequences").size());
        assertFalse(imported.has("version"));
        ProfileShareCodeManager.applyImportPreview(preview, null);
        JsonObject merged = new JsonParser().parse(
                new String(Files.readAllBytes(target.resolve(paths)), StandardCharsets.UTF_8)).getAsJsonObject();
        assertTrue(merged.get("localFlag").getAsBoolean());
        assertEquals(9, merged.getAsJsonArray("sequences").get(0).getAsJsonObject().getAsJsonArray("nodes").get(0).getAsInt());
        assertEquals(42, merged.getAsJsonArray("sequences").get(1).getAsJsonObject().get("oldUnknown").getAsInt());
        assertEquals(new JsonParser().parse("[8]"),
                merged.getAsJsonArray("sequences").get(1).getAsJsonObject().get("nodes"));
        assertEquals(3, merged.getAsJsonArray("sequences").size());
        assertEquals("new", merged.getAsJsonArray("sequences").get(2).getAsJsonObject().get("category").getAsString());
        assertEquals(new JsonParser().parse("[2]"),
                merged.getAsJsonArray("sequences").get(2).getAsJsonObject().get("nodes"));
        assertTrue(Files.exists(target.resolve("path_categories.json")));
        assertTrue(new String(Files.readAllBytes(target.resolve("path_categories.json")), StandardCharsets.UTF_8)
                .contains("new"));
    }

    @Test public void oldZs1Zsp1Zs2AndEarlierZs3CodesRemainReadable() throws Exception {
        String json = "{\"unknownOldField\":42}";
        Path target = temporary.newFolder().toPath();
        for (String prefix : new String[] { "ZS1.", "ZSP1." }) {
            JsonObject root = new JsonObject(), files = new JsonObject();
            files.addProperty(FILE, json);
            root.add("files", files);
            ByteArrayOutputStream zipped = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(zipped)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            String code = prefix + Base64.getEncoder().encodeToString(zipped.toByteArray());
            assertEquals(new JsonParser().parse(json), new JsonParser().parse(
                    ProfileShareCodeManager.previewImport(code, target.toString()).getEntries().get(0).getImportedContent()));
        }
        for (boolean partial : new boolean[] { false, true }) {
            String code = (partial ? "ZS3." : "ZS2.") + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(oldDeflate(payload(json, partial)));
            assertEquals(new JsonParser().parse(json), new JsonParser().parse(
                    ProfileShareCodeManager.previewImport(code, target.toString()).getEntries().get(0).getImportedContent()));
        }
    }

    @Test public void corruptionAndTruncationDoNotWriteTargetFiles() throws Exception {
        Path target = temporary.newFolder().toPath();
        byte[] payload = payload("{\"unknown\":true}", false);
        String code = ProfileShareCompression.encode(payload);
        byte[] envelope = ProfileShareTextEncoding.undense(code.substring(4));
        envelope[2] ^= 1;
        for (String damaged : new String[] { code.substring(0, code.length() - 1),
                "ZS3!" + ProfileShareTextEncoding.dense(envelope) }) {
            try {
                ProfileShareCodeManager.importShareCode(damaged, target.toString());
                fail("Expected corrupt code rejection");
            } catch (IOException | IllegalArgumentException expected) { }
            assertFalse(Files.exists(target.resolve(FILE)));
        }
    }

    @Test public void invalidExplicitSelectionCannotAccidentallyShareAllFields() {
        try {
            ProfileConfigFieldCodec.select(FILE, "{\"private\":true}", Collections.singleton("/missing"));
            fail("Expected stale selection rejection");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void malformedPartialTargetCannotFallBackToReplacement() throws Exception {
        Path target = temporary.newFolder().toPath();
        Files.write(target.resolve(FILE), "{broken".getBytes(StandardCharsets.UTF_8));
        String code = ProfileShareCompression.encode(payload("{\"enabled\":true}", true));
        try {
            ProfileShareCodeManager.importShareCode(code, target.toString());
            fail("Expected partial merge rejection");
        } catch (IllegalArgumentException expected) { }
        assertEquals("{broken", new String(Files.readAllBytes(target.resolve(FILE)), StandardCharsets.UTF_8));
    }

    @Test public void representativeRulesAreShorterThanPreviousProtocol() throws Exception {
        JsonArray rules = new JsonArray();
        for (int i = 0; i < 100; i++) {
            JsonObject rule = new JsonObject();
            rule.addProperty("name", "route-" + i);
            rule.addProperty("category", "default");
            rule.addProperty("enabled", i % 2 == 0);
            rule.addProperty("legacyField", i);
            rules.add(rule);
        }
        byte[] bytes = payload("{\"rules\":" + rules + "}", false);
        int old = 4 + Base64.getUrlEncoder().withoutPadding().encodeToString(oldDeflate(bytes)).length();
        String code = ProfileShareCompression.encode(bytes);
        assertArrayEquals(bytes, ProfileShareCompression.decode(code));
        assertTrue(code.length() < old);
        System.out.println("rules: old=" + old + " new=" + code.length());
    }

    private static byte[] payload(String json, boolean partial) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(partial ? 3 : 2);
        ProfileShareWire.number(out, 1);
        ProfileShareWire.number(out, 0);
        ProfileShareWire.string(out, FILE);
        if (partial) out.write(1);
        ProfileShareWire.string(out, json);
        return out.toByteArray();
    }

    private static byte[] oldDeflate(byte[] bytes) throws Exception {
        Method method = ProfileShareCodeManager.class.getDeclaredMethod("deflate", byte[].class);
        method.setAccessible(true);
        return (byte[]) method.invoke(null, (Object) bytes);
    }
}
