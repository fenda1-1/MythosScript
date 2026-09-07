package com.zszl.zszlScriptMod.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public final class PacketPayloadDecoder {
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Charset GBK = Charset.forName("GBK");
    private static final int MAX_INPUT_BYTES = 64 * 1024;
    private static final int MAX_OUTPUT_CHARS = 1024 * 1024;
    private static final int MAX_INFLATED_BYTES = 128 * 1024;
    private static final int MAX_SEGMENTS = 256;
    private static final int MAX_EMBEDDED_STRING_BYTES = 64 * 1024;
    private static final int MAX_SCAN_BYTES = 64 * 1024;
    private static final int MAX_RECURSION = 2;

    private PacketPayloadDecoder() {
    }

    public static String decode(byte[] data) {
        DecodeReport report = inspect(data);
        return report == null ? "" : report.summaryText;
    }

    public static String decodeDetailed(byte[] data) {
        DecodeReport report = inspect(data);
        return report == null ? "" : report.detailText;
    }

    public static String decodeFull(byte[] data) {
        AnnotatedDecodeReport report = inspectAnnotated(data);
        StringBuilder builder = new StringBuilder();
        if (report.summaryText != null && !report.summaryText.trim().isEmpty()) {
            builder.append(report.summaryText.trim());
        }
        if (report.chunks != null && !report.chunks.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("通用解码块");
            for (int i = 0; i < report.chunks.size(); i++) {
                DecodedChunk chunk = report.chunks.get(i);
                if (chunk == null || chunk.text == null || chunk.text.trim().isEmpty()) {
                    continue;
                }
                builder.append("\n\n#").append(i + 1).append(' ');
                builder.append(String.format(Locale.ROOT, "[%04X-%04X]", chunk.startOffset,
                        Math.max(chunk.startOffset, chunk.endOffset - 1)));
                if (chunk.label != null && !chunk.label.trim().isEmpty()) {
                    builder.append(' ').append(chunk.label.trim());
                }
                builder.append('\n').append(chunk.text.trim());
            }
        }
        if (builder.length() == 0 && report.detailText != null) {
            builder.append(report.detailText.trim());
        }
        return capOutput(builder.toString());
    }

    public static AnnotatedDecodeReport inspectAnnotated(byte[] data) {
        DecodeReport report = inspect(data);
        List<DecodedChunk> chunks = buildAnnotatedChunks(data);
        if (report == null) {
            return new AnnotatedDecodeReport("", "", chunks);
        }
        return new AnnotatedDecodeReport(report.summaryText, report.detailText, chunks);
    }

    private static DecodeReport inspect(byte[] data) {
        if (data == null || data.length == 0) {
            return new DecodeReport("", "");
        }

        boolean truncatedInput = data.length > MAX_INPUT_BYTES;
        byte[] working = data.length > MAX_INPUT_BYTES ? Arrays.copyOf(data, MAX_INPUT_BYTES) : data;
        CandidateCollector collector = new CandidateCollector();
        collectFromBytes(collector, working, "", 0, new LinkedHashSet<String>());
        DecodeCandidate best = collector.getBest();
        List<DecodeCandidate> ranked = collector.getRankedCandidates();
        if (best == null) {
            String emptyDetail = truncatedInput
                    ? "未识别到稳定文本解码结果。\n注意: 原始负载超过 " + MAX_INPUT_BYTES + " 字节，本次仅分析前 "
                            + MAX_INPUT_BYTES + " 字节。"
                    : "未识别到稳定文本解码结果。";
            return new DecodeReport("", emptyDetail);
        }
        return new DecodeReport(best.text, buildDetailText(best, ranked, truncatedInput));
    }

    private static String buildDetailText(DecodeCandidate best, List<DecodeCandidate> ranked, boolean truncatedInput) {
        StringBuilder builder = new StringBuilder();
        builder.append("主结果");
        if (!best.label.isEmpty()) {
            builder.append(" [").append(best.label).append("]");
        }
        builder.append('\n').append(best.text);

        if (ranked != null && ranked.size() > 1) {
            builder.append("\n\n候选结果");
            int limit = Math.min(10, ranked.size());
            for (int i = 0; i < limit; i++) {
                DecodeCandidate candidate = ranked.get(i);
                builder.append("\n\n#").append(i + 1);
                if (!candidate.label.isEmpty()) {
                    builder.append(" [").append(candidate.label).append("]");
                }
                builder.append('\n').append(candidate.text);
            }
        }

        if (truncatedInput) {
            builder.append("\n\n注意: 原始负载超过 ").append(MAX_INPUT_BYTES)
                    .append(" 字节，本次详细解码仅分析前 ").append(MAX_INPUT_BYTES).append(" 字节。");
        }

        return capOutput(builder.toString());
    }

    private static boolean isPreferredStructuredCandidate(DecodeCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String label = candidate.label == null ? "" : candidate.label;
        String text = candidate.text == null ? "" : candidate.text.trim();
        return label.contains("JSON") || looksJsonObjectOrArray(text);
    }

    private static boolean looksJsonObjectOrArray(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return false;
        }
        return trimmed.contains(":") || trimmed.contains("\"");
    }

    private static boolean looksFragmentedEmbeddedText(DecodeCandidate candidate) {
        if (candidate == null) {
            return false;
        }
        String label = candidate.label == null ? "" : candidate.label;
        String text = candidate.text == null ? "" : candidate.text.trim();
        if (!(label.contains("嵌入字符串") || label.contains("片段") || label.contains("Minecraft字符串"))) {
            return false;
        }
        if (text.contains(" | ")) {
            return true;
        }
        if (text.contains("\":\"") || text.contains("\": ")) {
            return !looksJsonObjectOrArray(text);
        }
        return false;
    }

    private static void collectFromBytes(CandidateCollector collector, byte[] data, String sourceLabel, int depth,
            Set<String> visitedBinary) {
        if (collector == null || data == null || data.length == 0 || depth > MAX_RECURSION) {
            return;
        }

        String binaryKey = data.length + ":" + Arrays.hashCode(data);
        if (!visitedBinary.add(binaryKey)) {
            return;
        }

        collectDirectTextDecodes(collector, data, sourceLabel, depth, visitedBinary);
        collectLeadingMinecraftStrings(collector, data, sourceLabel, depth, visitedBinary);
        collectEmbeddedVarIntStrings(collector, data, sourceLabel, depth, visitedBinary);
        collectEmbeddedFixedLengthStrings(collector, data, sourceLabel, depth, visitedBinary, "U16字符串", 2, 150);
        collectEmbeddedFixedLengthStrings(collector, data, sourceLabel, depth, visitedBinary, "I32字符串", 4, 124);
        collectUtf8Segments(collector, data, sourceLabel, depth, visitedBinary);
        collectAsciiSegments(collector, data, sourceLabel, depth, visitedBinary);

        if (depth >= MAX_RECURSION) {
            return;
        }

        byte[] gzipBytes = tryGunzip(data);
        if (gzipBytes != null) {
            collectFromBytes(collector, gzipBytes, appendLabel(sourceLabel, "GZIP"), depth + 1, visitedBinary);
        }

        byte[] zlibBytes = tryInflate(data, false);
        if (zlibBytes != null) {
            collectFromBytes(collector, zlibBytes, appendLabel(sourceLabel, "ZLIB"), depth + 1, visitedBinary);
        }

        byte[] deflateBytes = tryInflate(data, true);
        if (deflateBytes != null) {
            collectFromBytes(collector, deflateBytes, appendLabel(sourceLabel, "Deflate"), depth + 1, visitedBinary);
        }
    }

    private static void collectDirectTextDecodes(CandidateCollector collector, byte[] data, String sourceLabel,
            int depth, Set<String> visitedBinary) {
        addTextCandidate(collector, tryDecodeStrict(data, StandardCharsets.UTF_8), appendLabel(sourceLabel, "UTF-8"),
                120, depth, visitedBinary);

        if (hasUtf16Bom(data, true)) {
            addTextCandidate(collector, tryDecodeStrict(Arrays.copyOfRange(data, 2, data.length), StandardCharsets.UTF_16LE),
                    appendLabel(sourceLabel, "UTF-16LE"), 112, depth, visitedBinary);
        } else if (looksLikeUtf16(data, true)) {
            addTextCandidate(collector, tryDecodeStrict(data, StandardCharsets.UTF_16LE),
                    appendLabel(sourceLabel, "UTF-16LE"), 104, depth, visitedBinary);
        }

        if (hasUtf16Bom(data, false)) {
            addTextCandidate(collector, tryDecodeStrict(Arrays.copyOfRange(data, 2, data.length), StandardCharsets.UTF_16BE),
                    appendLabel(sourceLabel, "UTF-16BE"), 112, depth, visitedBinary);
        } else if (looksLikeUtf16(data, false)) {
            addTextCandidate(collector, tryDecodeStrict(data, StandardCharsets.UTF_16BE),
                    appendLabel(sourceLabel, "UTF-16BE"), 104, depth, visitedBinary);
        }

        if (containsHighBytes(data)) {
            addTextCandidate(collector, tryDecodeStrict(data, GBK), appendLabel(sourceLabel, "GBK"), 102, depth,
                    visitedBinary);
        }
    }

    private static void collectLeadingMinecraftStrings(CandidateCollector collector, byte[] data, String sourceLabel,
            int depth, Set<String> visitedBinary) {
        PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
        List<String> parts = new ArrayList<>();
        while (buffer.readableBytes() > 0 && parts.size() < MAX_SEGMENTS) {
            try {
                buffer.markReaderIndex();
                String part = buffer.readString(MAX_EMBEDDED_STRING_BYTES);
                String normalized = normalizeDisplayText(part);
                if (!isLikelyHumanText(normalized, 2)) {
                    buffer.resetReaderIndex();
                    break;
                }
                parts.add(normalized);
            } catch (Exception e) {
                buffer.resetReaderIndex();
                break;
            }
        }

        addPartsCandidate(collector, parts, appendLabel(sourceLabel, "Minecraft字符串"), 145, depth, visitedBinary);
    }

    private static void collectEmbeddedVarIntStrings(CandidateCollector collector, byte[] data, String sourceLabel,
            int depth, Set<String> visitedBinary) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        int scanLimit = Math.min(data.length, MAX_SCAN_BYTES);

        for (int offset = 0; offset < scanLimit - 2 && parts.size() < MAX_SEGMENTS; offset++) {
            VarIntRead varInt = readVarInt(data, offset);
            if (varInt == null || varInt.value < 2 || varInt.value > MAX_EMBEDDED_STRING_BYTES) {
                continue;
            }

            int start = offset + varInt.bytesUsed;
            if (start < 0 || start + varInt.value > data.length) {
                continue;
            }

            String text = tryDecodeStrict(Arrays.copyOfRange(data, start, start + varInt.value), StandardCharsets.UTF_8);
            String normalized = normalizeDisplayText(text);
            if (!isLikelyHumanText(normalized, 2)) {
                continue;
            }

            parts.add(normalized);
            offset = start + varInt.value - 1;
        }

        addPartsCandidate(collector, new ArrayList<>(parts), appendLabel(sourceLabel, "嵌入字符串"), 132, depth,
                visitedBinary);
    }

    private static void collectEmbeddedFixedLengthStrings(CandidateCollector collector, byte[] data, String sourceLabel,
            int depth, Set<String> visitedBinary, String label, int prefixBytes, int baseScore) {
        if (collector == null || data == null || data.length == 0 || prefixBytes <= 0) {
            return;
        }
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        int scanLimit = Math.min(data.length, MAX_SCAN_BYTES);

        for (int offset = 0; offset + prefixBytes < scanLimit && parts.size() < MAX_SEGMENTS; offset++) {
            int textLength = 0;
            for (int i = 0; i < prefixBytes; i++) {
                textLength = (textLength << 8) | (data[offset + i] & 0xFF);
            }
            if (textLength < 2 || textLength > MAX_EMBEDDED_STRING_BYTES) {
                continue;
            }

            int textStart = offset + prefixBytes;
            int textEnd = textStart + textLength;
            if (textStart < 0 || textEnd > data.length) {
                continue;
            }

            byte[] textBytes = Arrays.copyOfRange(data, textStart, textEnd);
            String decoded = tryDecodeStrict(textBytes, StandardCharsets.UTF_8);
            if (decoded == null && containsHighBytes(textBytes)) {
                decoded = tryDecodeStrict(textBytes, GBK);
            }

            String normalized = normalizeDisplayText(decoded);
            if (!isLikelyHumanText(normalized, 2) && !looksStructuredText(normalized)
                    && splitEmbeddedEncodedTokens(normalized).isEmpty()) {
                continue;
            }

            parts.add(normalized);
            offset = textEnd - 1;
        }

        addPartsCandidate(collector, new ArrayList<>(parts), appendLabel(sourceLabel, label), baseScore, depth,
                visitedBinary);
    }

    private static void collectUtf8Segments(CandidateCollector collector, byte[] data, String sourceLabel, int depth,
            Set<String> visitedBinary) {
        String decoded = new String(data, StandardCharsets.UTF_8);
        List<String> segments = splitReadableSegments(decoded, false);
        addPartsCandidate(collector, segments, appendLabel(sourceLabel, "UTF-8片段"), 108, depth, visitedBinary);
    }

    private static void collectAsciiSegments(CandidateCollector collector, byte[] data, String sourceLabel, int depth,
            Set<String> visitedBinary) {
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (byte b : data) {
            int value = b & 0xFF;
            if (isAsciiTextByte(value)) {
                current.append((char) value);
            } else {
                flushSegment(segments, current);
                if (segments.size() >= MAX_SEGMENTS) {
                    break;
                }
            }
        }
        flushSegment(segments, current);
        addPartsCandidate(collector, segments, appendLabel(sourceLabel, "ASCII片段"), 96, depth, visitedBinary);
    }

    private static void addPartsCandidate(CandidateCollector collector, List<String> parts, String label, int baseScore,
            int depth, Set<String> visitedBinary) {
        if (parts == null || parts.isEmpty()) {
            return;
        }

        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String part : parts) {
            String normalized = normalizeDisplayText(part);
            if (isLikelyHumanText(normalized, 2)) {
                deduped.add(normalized);
            }
            if (deduped.size() >= MAX_SEGMENTS) {
                break;
            }
        }

        if (deduped.isEmpty()) {
            return;
        }

        if (deduped.size() == 1) {
            addTextCandidate(collector, deduped.iterator().next(), label, baseScore, depth, visitedBinary);
            return;
        }

        String joined = joinParts(deduped);
        int readabilityScore = scoreReadableText(joined);
        if (readabilityScore < 0) {
            return;
        }
        collector.add(label, joined, baseScore + readabilityScore + Math.min(18, deduped.size() * 3));
    }

    private static void addTextCandidate(CandidateCollector collector, String text, String label, int baseScore,
            int depth, Set<String> visitedBinary) {
        String normalized = normalizeDisplayText(text);
        if (normalized.isEmpty()) {
            return;
        }

        String currentLabel = label == null ? "" : label.trim();

        String prettyJson = tryPrettyJson(normalized);
        if (prettyJson != null) {
            collector.add(appendLabel(currentLabel, "JSON"), prettyJson, baseScore + 92);
            return;
        }

        int readabilityScore = scoreReadableText(normalized);
        if (readabilityScore >= 0) {
            collector.add(currentLabel, normalized, baseScore + readabilityScore);
        } else if (depth >= MAX_RECURSION) {
            return;
        }

        if (depth >= MAX_RECURSION) {
            return;
        }

        String urlDecoded = tryUrlDecode(normalized);
        if (!urlDecoded.isEmpty() && !urlDecoded.equals(normalized)) {
            addTextCandidate(collector, urlDecoded, appendLabel(currentLabel, "URL解码"), baseScore + 8, depth + 1,
                    visitedBinary);
        }

        byte[] hexBytes = tryDecodeHexText(normalized);
        if (hexBytes != null) {
            collectFromBytes(collector, hexBytes, appendLabel(currentLabel, "HEX"), depth + 1, visitedBinary);
        }

        byte[] base64Bytes = tryDecodeBase64Text(normalized);
        if (base64Bytes != null) {
            collectFromBytes(collector, base64Bytes, appendLabel(currentLabel, "Base64"), depth + 1, visitedBinary);
        }

        collectEmbeddedEncodedTokens(collector, normalized, currentLabel, baseScore, depth, visitedBinary);
    }

    private static void collectEmbeddedEncodedTokens(CandidateCollector collector, String text, String currentLabel,
            int baseScore, int depth, Set<String> visitedBinary) {
        if (collector == null || text == null || text.trim().isEmpty() || depth >= MAX_RECURSION) {
            return;
        }

        List<String> tokens = splitEmbeddedEncodedTokens(text);
        for (String token : tokens) {
            if (token == null || token.isEmpty()) {
                continue;
            }

            byte[] base64Bytes = tryDecodeBase64Text(token);
            if (base64Bytes != null) {
                collectFromBytes(collector, base64Bytes, appendLabel(currentLabel, "片段Base64"), depth + 1,
                        visitedBinary);
                continue;
            }

            byte[] hexBytes = tryDecodeHexText(token);
            if (hexBytes != null) {
                collectFromBytes(collector, hexBytes, appendLabel(currentLabel, "片段HEX"), depth + 1, visitedBinary);
            }
        }
    }

    private static String tryDecodeStrict(byte[] data, Charset charset) {
        if (data == null || data.length == 0 || charset == null) {
            return null;
        }
        CharsetDecoder decoder = charset.newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPORT);
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static boolean hasUtf16Bom(byte[] data, boolean littleEndian) {
        if (data == null || data.length < 2) {
            return false;
        }
        if (littleEndian) {
            return (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE;
        }
        return (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF;
    }

    private static boolean looksLikeUtf16(byte[] data, boolean littleEndian) {
        if (data == null || data.length < 6 || (data.length & 1) != 0) {
            return false;
        }
        int sampleLength = Math.min(data.length, 128);
        if ((sampleLength & 1) != 0) {
            sampleLength--;
        }
        if (sampleLength < 6) {
            return false;
        }

        int zeroCount = 0;
        int printableCount = 0;
        int pairs = sampleLength / 2;
        for (int i = 0; i < sampleLength; i += 2) {
            int first = data[i] & 0xFF;
            int second = data[i + 1] & 0xFF;
            int charByte = littleEndian ? first : second;
            int zeroByte = littleEndian ? second : first;
            if (zeroByte == 0) {
                zeroCount++;
            }
            if (charByte >= 32 && charByte <= 126) {
                printableCount++;
            }
        }

        return zeroCount >= Math.max(2, pairs / 3) && printableCount >= Math.max(2, pairs / 3);
    }

    private static boolean containsHighBytes(byte[] data) {
        if (data == null) {
            return false;
        }
        for (byte b : data) {
            if ((b & 0x80) != 0) {
                return true;
            }
        }
        return false;
    }

    private static byte[] tryGunzip(byte[] data) {
        if (data == null || data.length < 4) {
            return null;
        }
        if ((data[0] & 0xFF) != 0x1F || (data[1] & 0xFF) != 0x8B) {
            return null;
        }
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return readAllLimited(input, MAX_INFLATED_BYTES);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] tryInflate(byte[] data, boolean nowrap) {
        if (data == null || data.length < 4) {
            return null;
        }
        if (!nowrap && !looksLikeZlibHeader(data)) {
            return null;
        }

        Inflater inflater = new Inflater(nowrap);
        try (InflaterInputStream input = new InflaterInputStream(new ByteArrayInputStream(data), inflater)) {
            byte[] inflated = readAllLimited(input, MAX_INFLATED_BYTES);
            if (inflated == null || inflated.length == 0 || Arrays.equals(inflated, data)) {
                return null;
            }
            return inflated;
        } catch (Exception e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static boolean looksLikeZlibHeader(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        int cmf = data[0] & 0xFF;
        int flg = data[1] & 0xFF;
        return (cmf & 0x0F) == 8 && ((cmf << 8) + flg) % 31 == 0;
    }

    private static byte[] readAllLimited(java.io.InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String tryPrettyJson(String text) {
        String trimmed = normalizeDisplayText(text);
        if (trimmed.isEmpty()) {
            return null;
        }
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(trimmed);
            if (element == null || !(element.isJsonObject() || element.isJsonArray())) {
                return null;
            }
            return capOutput(PRETTY_GSON.toJson(element).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String tryUrlDecode(String text) {
        if (text == null || !text.matches(".*%[0-9A-Fa-f]{2}.*")) {
            return "";
        }
        try {
            return URLDecoder.decode(text, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] tryDecodeHexText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("0x", "").replace("0X", "")
                .replaceAll("[\\s,:;\\-_|]+", "")
                .trim();
        if (normalized.length() < 8 || (normalized.length() & 1) != 0) {
            return null;
        }
        if (!normalized.matches("(?i)^[0-9a-f]+$")) {
            return null;
        }

        byte[] bytes = new byte[normalized.length() / 2];
        for (int i = 0; i < normalized.length(); i += 2) {
            try {
                bytes[i / 2] = (byte) Integer.parseInt(normalized.substring(i, i + 2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return bytes;
    }

    private static byte[] tryDecodeBase64Text(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", "");
        if (normalized.length() < 12) {
            return null;
        }
        if (!normalized.matches("^[A-Za-z0-9+/=_-]+$")) {
            return null;
        }
        if (!(normalized.contains("=") || normalized.contains("+") || normalized.contains("/")
                || normalized.contains("-") || normalized.contains("_") || normalized.length() % 4 == 0)) {
            return null;
        }
        try {
            return java.util.Base64.getUrlDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
        }
        try {
            return java.util.Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static VarIntRead readVarInt(byte[] data, int offset) {
        if (data == null || offset < 0 || offset >= data.length) {
            return null;
        }
        int value = 0;
        int bytesUsed = 0;
        int position = 0;

        while (offset + bytesUsed < data.length && bytesUsed < 5) {
            int current = data[offset + bytesUsed] & 0xFF;
            value |= (current & 0x7F) << position;
            bytesUsed++;
            if ((current & 0x80) == 0) {
                if (value < 0) {
                    return null;
                }
                return new VarIntRead(value, bytesUsed);
            }
            position += 7;
        }

        return null;
    }

    private static List<String> splitReadableSegments(String text, boolean asciiOnly) {
        List<String> segments = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return segments;
        }

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isReadableSegmentChar(c, asciiOnly)) {
                current.append(c);
            } else {
                flushSegment(segments, current);
                if (segments.size() >= MAX_SEGMENTS) {
                    break;
                }
            }
        }
        flushSegment(segments, current);
        return segments;
    }

    private static void flushSegment(List<String> segments, StringBuilder current) {
        if (segments == null || current == null || current.length() == 0 || segments.size() >= MAX_SEGMENTS) {
            if (current != null) {
                current.setLength(0);
            }
            return;
        }

        String normalized = normalizeDisplayText(current.toString());
        current.setLength(0);
        if (isLikelyHumanText(normalized, 4)) {
            segments.add(normalized);
        }
    }

    private static boolean isReadableSegmentChar(char c, boolean asciiOnly) {
        if (c == '\uFFFD') {
            return false;
        }
        if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
            return false;
        }
        if (!asciiOnly) {
            return true;
        }
        return c == '\t' || c == '\n' || c == '\r' || (c >= 32 && c <= 126);
    }

    private static boolean isAsciiTextByte(int value) {
        return value == 9 || value == 10 || value == 13 || (value >= 32 && value <= 126);
    }

    private static boolean isLikelyHumanText(String text, int minLength) {
        if (text == null) {
            return false;
        }
        String normalized = normalizeDisplayText(text);
        if (normalized.length() < minLength) {
            return false;
        }
        if (containsDisallowedControlChars(normalized)) {
            return false;
        }
        return scoreReadableText(normalized) >= 0;
    }

    private static int scoreReadableText(String text) {
        if (text == null) {
            return -1;
        }
        String normalized = normalizeDisplayText(text);
        if (normalized.isEmpty()) {
            return -1;
        }
        if (containsDisallowedControlChars(normalized)) {
            return -1;
        }
        if (looksEncodedBlob(normalized) && !normalized.contains(" ")) {
            return -1;
        }

        int printable = 0;
        int suspicious = 0;
        int wordLike = 0;
        int whitespace = 0;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '\uFFFD') {
                suspicious += 4;
                continue;
            }
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                suspicious += 3;
                continue;
            }

            printable++;
            if (Character.isWhitespace(c)) {
                whitespace++;
            }
            if (Character.isLetterOrDigit(c) || isCjk(c)) {
                wordLike++;
            }
        }

        if (normalized.length() == 0 || printable == 0) {
            return -1;
        }

        double printableRatio = (double) printable / (double) normalized.length();
        double suspiciousRatio = (double) suspicious / (double) Math.max(1, normalized.length());
        if (printableRatio < 0.72 || suspiciousRatio > 0.18) {
            return -1;
        }
        if (wordLike == 0 && !looksStructuredText(normalized)) {
            return -1;
        }

        int score = (int) Math.round(printableRatio * 70.0);
        score -= suspicious * 2;
        score += Math.min(34, normalized.length() / 8);
        if (wordLike > 0) {
            score += 12;
        }
        if (whitespace > 0) {
            score += 4;
        }
        if (looksStructuredText(normalized)) {
            score += 10;
        }
        return score;
    }

    private static boolean looksStructuredText(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("<")) {
            return true;
        }
        return trimmed.contains(":") || trimmed.contains("=") || trimmed.contains("&") || trimmed.contains("|");
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    private static String normalizeDisplayText(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\uFEFF", "")
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        return capOutput(normalized);
    }

    private static boolean containsDisallowedControlChars(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    private static String capOutput(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_OUTPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_OUTPUT_CHARS) + "\n...(截断)";
    }

    private static String joinParts(Iterable<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(" | ");
            }
            builder.append(part.trim());
            if (builder.length() >= MAX_OUTPUT_CHARS) {
                break;
            }
        }
        return capOutput(builder.toString());
    }

    private static String appendLabel(String prefix, String next) {
        String left = prefix == null ? "" : prefix.trim();
        String right = next == null ? "" : next.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " -> " + right;
    }

    private static List<String> splitEmbeddedEncodedTokens(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        for (String token : text.split("[\\s\"'`,;|]+")) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.length() < 12) {
                continue;
            }
            if (looksPotentialBase64Token(trimmed) || looksPotentialHexToken(trimmed)) {
                tokens.add(trimmed);
            }
        }
        return tokens;
    }

    private static boolean looksPotentialBase64Token(String token) {
        if (token == null || token.length() < 12) {
            return false;
        }
        if (!token.matches("^[A-Za-z0-9+/=_-]+$")) {
            return false;
        }
        int upper = 0;
        int lower = 0;
        int digits = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                upper++;
            } else if (c >= 'a' && c <= 'z') {
                lower++;
            } else if (c >= '0' && c <= '9') {
                digits++;
            }
        }
        return (upper > 0 && lower > 0) || digits >= 4 || token.contains("=") || token.contains("+")
                || token.contains("/") || token.contains("-") || token.contains("_");
    }

    private static boolean looksPotentialHexToken(String token) {
        if (token == null) {
            return false;
        }
        String normalized = token.replace("0x", "").replace("0X", "").replaceAll("[^0-9A-Fa-f]", "");
        return normalized.length() >= 12 && (normalized.length() & 1) == 0;
    }

    private static byte[] tryDecodeUnpaddedBase64(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", "");
        int mod = normalized.length() % 4;
        if (mod != 0) {
            normalized += "====".substring(mod);
        }
        try {
            return java.util.Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ignored) {
            try {
                return java.util.Base64.getUrlDecoder().decode(normalized);
            } catch (IllegalArgumentException ignoredAgain) {
                return null;
            }
        }
    }

    private static int encodedLengthWithoutPadding(int byteLength) {
        if (byteLength <= 0) {
            return 0;
        }
        int paddedLength = ((byteLength + 2) / 3) * 4;
        int padding = (3 - (byteLength % 3)) % 3;
        return paddedLength - padding;
    }

    private static boolean looksEncodedBlob(String text) {
        if (text == null) {
            return false;
        }
        String compact = text.replaceAll("\\s+", "");
        if (compact.length() < 24) {
            return false;
        }
        return looksPotentialBase64Token(compact) || looksPotentialHexToken(compact);
    }

    private static List<DecodedChunk> buildAnnotatedChunks(byte[] data) {
        if (data == null || data.length == 0) {
            return Collections.emptyList();
        }

        byte[] working = data.length > MAX_INPUT_BYTES ? Arrays.copyOf(data, MAX_INPUT_BYTES) : data;
        List<DecodedChunk> chunks = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        collectSequentialStructuredChunks(chunks, seen, working, 0, 0);
        collectAnnotatedChunks(chunks, seen, working, 0, true, "", 0);
        chunks = postProcessAnnotatedChunks(chunks);
        Collections.sort(chunks, (left, right) -> {
            if (left.startOffset != right.startOffset) {
                return Integer.compare(left.startOffset, right.startOffset);
            }
            if (left.endOffset != right.endOffset) {
                return Integer.compare(left.endOffset, right.endOffset);
            }
            return left.label.compareTo(right.label);
        });
        return chunks;
    }

    private static void collectSequentialStructuredChunks(List<DecodedChunk> chunks, Set<String> seen, byte[] data,
            int rawStartOffset, int depth) {
        if (chunks == null || seen == null || data == null || data.length == 0 || depth > MAX_RECURSION) {
            return;
        }

        int offset = 0;
        while (offset < data.length) {
            int zeroRun = countZeroRun(data, offset);
            if (zeroRun >= 4) {
                offset += zeroRun;
                continue;
            }

            StructuredField field = tryReadStructuredStringField(data, rawStartOffset, offset);
            if (field == null) {
                field = tryReadStructuredBooleanField(data, rawStartOffset, offset);
            }
            if (field == null) {
                field = tryReadStructuredLongField(data, rawStartOffset, offset);
            }
            if (field == null) {
                field = tryReadStructuredIntField(data, rawStartOffset, offset);
            }
            if (field == null) {
                field = tryReadStructuredAsciiBlobField(data, rawStartOffset, offset);
            }

            if (field != null) {
                addAnnotatedChunk(chunks, seen, field.startOffset, field.endOffset, field.label, field.text);
                if (field.nestedBytes != null && field.nestedBytes.length > 0) {
                    collectAnnotatedChunks(chunks, seen, field.nestedBytes, field.startOffset, false, field.label,
                            depth + 1);
                }
                offset = Math.max(offset + 1, field.nextOffset);
                continue;
            }
            offset++;
        }
    }

    private static StructuredField tryReadStructuredStringField(byte[] data, int rawStartOffset, int offset) {
        StructuredField u16Field = tryReadLengthPrefixedStructuredString(data, rawStartOffset, offset, "U16", 2);
        if (u16Field != null) {
            return u16Field;
        }
        return tryReadLengthPrefixedStructuredString(data, rawStartOffset, offset, "VarInt", 0);
    }

    private static StructuredField tryReadLengthPrefixedStructuredString(byte[] data, int rawStartOffset, int offset,
            String mode, int fixedPrefixBytes) {
        if (data == null || offset < 0 || offset >= data.length) {
            return null;
        }

        int bytesUsed;
        int textLength;
        if ("VarInt".equals(mode)) {
            VarIntRead varInt = readVarInt(data, offset);
            if (varInt == null) {
                return null;
            }
            bytesUsed = varInt.bytesUsed;
            textLength = varInt.value;
        } else {
            if (offset + fixedPrefixBytes > data.length) {
                return null;
            }
            textLength = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            bytesUsed = fixedPrefixBytes;
        }

        if (textLength <= 0 || textLength > MAX_EMBEDDED_STRING_BYTES) {
            return null;
        }

        int textStart = offset + bytesUsed;
        int textEnd = textStart + textLength;
        if (textStart < 0 || textEnd > data.length) {
            return null;
        }

        byte[] textBytes = Arrays.copyOfRange(data, textStart, textEnd);
        String decoded = tryDecodeStrict(textBytes, StandardCharsets.UTF_8);
        if (decoded == null && containsHighBytes(textBytes)) {
            decoded = tryDecodeStrict(textBytes, GBK);
        }
        String normalized = normalizeDisplayText(decoded);
        String compact = normalized.replaceAll("\\s+", "");

        boolean encodedBlob = looksPotentialBase64Token(compact) || looksPotentialHexToken(compact);
        if (!encodedBlob && !isLikelyHumanText(normalized, 1) && !looksMinecraftStyledText(normalized)
                && !looksIdentifierText(normalized) && !looksNumericText(normalized)) {
            return null;
        }
        if (!encodedBlob && normalized.length() <= 1 && !looksNumericText(normalized)) {
            return null;
        }

        String label = mode + "字符串";
        if (encodedBlob) {
            label += looksPotentialBase64Token(compact) ? " / Base64数据" : " / HEX数据";
        } else if (looksIdentifierText(normalized)) {
            label += " / 键名";
        } else if (looksNumericText(normalized)) {
            label += " / 数值文本";
        } else if (looksMinecraftStyledText(normalized) || containsCjkCharacters(normalized)) {
            label += " / 文本";
        }

        byte[] nestedBytes = null;
        if (looksPotentialBase64Token(compact)) {
            nestedBytes = tryDecodeBase64Text(compact);
        } else if (looksPotentialHexToken(compact)) {
            nestedBytes = tryDecodeHexText(compact);
        }

        return new StructuredField(rawStartOffset + offset, rawStartOffset + textEnd, label, normalized, nestedBytes,
                textEnd);
    }

    private static StructuredField tryReadStructuredBooleanField(byte[] data, int rawStartOffset, int offset) {
        if (data == null || offset < 0 || offset >= data.length) {
            return null;
        }
        int value = data[offset] & 0xFF;
        if (value != 0 && value != 1) {
            return null;
        }
        if (offset + 1 < data.length) {
            boolean nextLooksStructured = tryReadStructuredStringField(data, rawStartOffset, offset + 1) != null
                    || tryReadStructuredIntField(data, rawStartOffset, offset + 1) != null
                    || tryReadStructuredLongField(data, rawStartOffset, offset + 1) != null;
            if (!nextLooksStructured && countZeroRun(data, offset + 1) < 2) {
                return null;
            }
        }
        String text = String.format("%02X (%s)", value, value == 1 ? "True" : "False");
        return new StructuredField(rawStartOffset + offset, rawStartOffset + offset + 1, "Boolean / 标志位", text,
                null, offset + 1);
    }

    private static StructuredField tryReadStructuredIntField(byte[] data, int rawStartOffset, int offset) {
        if (data == null || offset < 0 || offset + 4 > data.length) {
            return null;
        }
        long value = ((data[offset] & 0xFFL) << 24)
                | ((data[offset + 1] & 0xFFL) << 16)
                | ((data[offset + 2] & 0xFFL) << 8)
                | (data[offset + 3] & 0xFFL);
        if (value <= 0 || value > 100_000_000L) {
            return null;
        }
        if (offset + 4 < data.length) {
            boolean nextLooksStructured = tryReadStructuredStringField(data, rawStartOffset, offset + 4) != null
                    || tryReadStructuredBooleanField(data, rawStartOffset, offset + 4) != null;
            if (!nextLooksStructured) {
                return null;
            }
        }
        String text = toHexSlice(data, offset, 4) + " (" + value + ")";
        return new StructuredField(rawStartOffset + offset, rawStartOffset + offset + 4, "I32整型 / 整数值", text, null,
                offset + 4);
    }

    private static StructuredField tryReadStructuredLongField(byte[] data, int rawStartOffset, int offset) {
        if (data == null || offset < 0 || offset + 8 > data.length) {
            return null;
        }
        long value = ByteBuffer.wrap(data, offset, 8).getLong();
        if (value <= 0L) {
            return null;
        }
        if (value > 9_999_999_999_999_999L) {
            return null;
        }
        if (offset + 8 < data.length) {
            boolean nextLooksStructured = tryReadStructuredIntField(data, rawStartOffset, offset + 8) != null
                    || tryReadStructuredStringField(data, rawStartOffset, offset + 8) != null
                    || tryReadStructuredBooleanField(data, rawStartOffset, offset + 8) != null;
            if (!nextLooksStructured) {
                return null;
            }
        }
        String text = toHexSlice(data, offset, 8) + " (" + value + ")";
        return new StructuredField(rawStartOffset + offset, rawStartOffset + offset + 8, "64位整数 / 可能时间或ID", text,
                null, offset + 8);
    }

    private static StructuredField tryReadStructuredAsciiBlobField(byte[] data, int rawStartOffset, int offset) {
        if (data == null || offset < 0 || offset >= data.length || !isAsciiTextByte(data[offset] & 0xFF)) {
            return null;
        }
        int end = offset;
        while (end < data.length && isAsciiTextByte(data[end] & 0xFF)) {
            end++;
        }
        if (end - offset < 24) {
            return null;
        }

        String ascii = new String(data, offset, end - offset, StandardCharsets.US_ASCII).trim();
        String compact = ascii.replaceAll("\\s+", "");
        if (!looksPotentialBase64Token(compact) && !looksPotentialHexToken(compact)) {
            return null;
        }

        byte[] nestedBytes = looksPotentialBase64Token(compact)
                ? tryDecodeBase64Text(compact)
                : tryDecodeHexText(compact);
        String label = "ASCII片段 / " + (looksPotentialBase64Token(compact) ? "Base64数据" : "HEX数据");
        return new StructuredField(rawStartOffset + offset, rawStartOffset + end, label, compact, nestedBytes, end);
    }

    private static int countZeroRun(byte[] data, int offset) {
        if (data == null || offset < 0 || offset >= data.length) {
            return 0;
        }
        int count = 0;
        while (offset + count < data.length && data[offset + count] == 0x00) {
            count++;
        }
        return count;
    }

    private static boolean looksIdentifierText(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.length() >= 3
                && trimmed.length() <= 48
                && trimmed.matches("^[A-Za-z_][A-Za-z0-9_]*$");
    }

    private static boolean looksNumericText(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        return trimmed.matches("^[+-]?\\d+(\\.\\d+)?$");
    }

    private static boolean containsCjkCharacters(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String toHexSlice(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length <= 0 || offset + length > data.length) {
            return "";
        }
        StringBuilder builder = new StringBuilder(length * 3);
        for (int i = 0; i < length; i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(String.format("%02X", data[offset + i] & 0xFF));
        }
        return builder.toString();
    }

    private static ItemStack tryReadItemStackPayload(byte[] data) {
        if (data == null || data.length <= 0) {
            return ItemStack.EMPTY;
        }
        try {
            PacketBuffer buffer = new PacketBuffer(Unpooled.wrappedBuffer(data));
            ItemStack stack = buffer.readItemStack();
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static NBTTagCompound tryReadCompressedNbt(byte[] data) {
        if (data == null || data.length <= 2) {
            return null;
        }
        try {
            return CompressedStreamTools.readCompressed(new ByteArrayInputStream(data));
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addItemStackChunks(List<DecodedChunk> chunks, Set<String> seen, int startOffset, int endOffset,
            String labelPrefix, ItemStack stack) {
        if (chunks == null || seen == null || stack == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        ResourceLocation registryName = item == null ? null : item.getRegistryName();
        String registryText = registryName == null ? "" : registryName.toString();
        if (!registryText.isEmpty()) {
            addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "物品ID"), registryText);
        }

        addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "物品数量"),
                String.valueOf(stack.getCount()));

        String displayName = normalizeDisplayText(sanitizeMinecraftText(stack.getDisplayName()));
        if (!displayName.isEmpty()) {
            addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "显示名称"), displayName);
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && !tag.hasNoTags()) {
            addNbtChunks(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "NBT"), tag);
        }
    }

    private static void addNbtChunks(List<DecodedChunk> chunks, Set<String> seen, int startOffset, int endOffset,
            String labelPrefix, NBTTagCompound tag) {
        if (chunks == null || seen == null || tag == null || tag.hasNoTags()) {
            return;
        }

        NBTTagCompound display = tag.hasKey("display", 10) ? tag.getCompoundTag("display") : null;
        if (display != null && !display.hasNoTags()) {
            String name = extractDisplayName(display);
            if (!name.isEmpty()) {
                addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "显示名称"), name);
            }

            List<String> lore = extractLore(display);
            if (!lore.isEmpty()) {
                addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "Lore"),
                        String.join("\n", lore));
            }
        }

        String compact = normalizeDisplayText(tag.toString());
        if (!compact.isEmpty() && !looksEncodedBlob(compact)) {
            addAnnotatedChunk(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "摘要"), compact);
        }
    }

    private static String extractDisplayName(NBTTagCompound display) {
        if (display == null || display.hasNoTags()) {
            return "";
        }
        String raw = display.getString("Name");
        return sanitizeMinecraftText(raw);
    }

    private static List<String> extractLore(NBTTagCompound display) {
        List<String> lore = new ArrayList<>();
        if (display == null || !display.hasKey("Lore", 9)) {
            return lore;
        }
        NBTTagList list = display.getTagList("Lore", 8);
        for (int i = 0; i < list.tagCount(); i++) {
            String line = sanitizeMinecraftText(list.getStringTagAt(i));
            if (!line.isEmpty()) {
                lore.add(line);
            }
        }
        return lore;
    }

    private static String sanitizeMinecraftText(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "";
        }
        String trimmed = raw.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                ITextComponent component = ITextComponent.Serializer.jsonToComponent(trimmed);
                if (component != null) {
                    String formatted = normalizeDisplayText(component.getFormattedText());
                    if (!formatted.isEmpty()) {
                        return formatted;
                    }
                    return normalizeDisplayText(component.getUnformattedText());
                }
            } catch (Exception ignored) {
            }
        }
        return normalizeDisplayText(trimmed);
    }

    private static List<DecodedChunk> postProcessAnnotatedChunks(List<DecodedChunk> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<DecodedChunk> filtered = new ArrayList<>();
        for (DecodedChunk chunk : source) {
            if (isDisplayableAnnotatedChunk(chunk)) {
                filtered.add(new DecodedChunk(chunk.startOffset, chunk.endOffset,
                        normalizeAnnotatedChunkLabel(chunk.label, chunk.text), chunk.text));
            }
        }

        List<DecodedChunk> withoutRedundant = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            DecodedChunk current = filtered.get(i);
            if (!isRedundantAnnotatedChunk(current, filtered, i)) {
                withoutRedundant.add(current);
            }
        }

        return mergeAnnotatedChunkGroups(withoutRedundant);
    }

    private static boolean isDisplayableAnnotatedChunk(DecodedChunk chunk) {
        if (chunk == null) {
            return false;
        }
        String text = normalizeDisplayText(chunk.text);
        if (text.isEmpty()) {
            return false;
        }
        if (containsDisallowedControlChars(text)) {
            return false;
        }
        if (text.length() <= 2 && !text.matches("\\d+")) {
            return false;
        }
        if (looksEncodedBlob(text)) {
            return text.length() >= 48;
        }
        if (chunk.label != null && (chunk.label.contains("Base64解码") || chunk.label.contains("HEX解码"))) {
            return isLikelyHumanText(text, 4) || looksMinecraftStyledText(text);
        }
        return isLikelyHumanText(text, 2) || looksMinecraftStyledText(text);
    }

    private static boolean looksMinecraftStyledText(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (!text.contains("§")) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i)) || isCjk(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeAnnotatedChunkLabel(String label, String text) {
        String normalizedLabel = label == null ? "" : label.trim();
        if (text == null) {
            return normalizedLabel;
        }
        String compact = text.replaceAll("\\s+", "");
        if (looksPotentialBase64Token(compact)) {
            return normalizedLabel.isEmpty() ? "Base64数据" : normalizedLabel + " / Base64数据";
        }
        if (looksPotentialHexToken(compact)) {
            return normalizedLabel.isEmpty() ? "HEX数据" : normalizedLabel + " / HEX数据";
        }
        return normalizedLabel;
    }

    private static boolean isRedundantAnnotatedChunk(DecodedChunk current, List<DecodedChunk> all, int currentIndex) {
        if (current == null || all == null) {
            return false;
        }
        String currentText = normalizeDisplayText(current.text);
        boolean currentEncoded = looksEncodedBlob(currentText);
        int nestedChildren = 0;
        for (int i = 0; i < all.size(); i++) {
            if (i == currentIndex) {
                continue;
            }
            DecodedChunk other = all.get(i);
            if (other == null) {
                continue;
            }
            String otherText = normalizeDisplayText(other.text);
            if (otherText.isEmpty()) {
                continue;
            }

            boolean containsRange = other.startOffset <= current.startOffset && other.endOffset >= current.endOffset;
            if (!containsRange) {
                boolean currentContainsOther = current.startOffset <= other.startOffset && current.endOffset >= other.endOffset
                        && (current.endOffset - current.startOffset) > (other.endOffset - other.startOffset);
                if (currentContainsOther) {
                    nestedChildren++;
                }
                continue;
            }

            if (currentEncoded && looksEncodedBlob(otherText)
                    && otherText.length() > currentText.length()
                    && otherText.contains(currentText)) {
                return true;
            }
        }
        if (nestedChildren >= 3
                && current.label != null
                && current.label.contains("字符串")
                && !current.label.contains("Base64")
                && !current.label.contains("HEX")) {
            return true;
        }
        return false;
    }

    private static List<DecodedChunk> mergeAnnotatedChunkGroups(List<DecodedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChunkGroup> groups = new ArrayList<>();
        for (DecodedChunk chunk : chunks) {
            String normalizedText = normalizeDisplayText(chunk.text);
            if (normalizedText.isEmpty()) {
                continue;
            }

            ChunkGroup matched = null;
            for (ChunkGroup group : groups) {
                if (group.canMerge(chunk, normalizedText)) {
                    matched = group;
                    break;
                }
            }
            if (matched == null) {
                groups.add(new ChunkGroup(chunk, normalizedText));
            } else {
                matched.merge(chunk);
            }
        }

        List<DecodedChunk> merged = new ArrayList<>();
        for (ChunkGroup group : groups) {
            merged.add(group.toChunk());
        }
        return merged;
    }

    private static void collectAnnotatedChunks(List<DecodedChunk> chunks, Set<String> seen, byte[] data, int rawStartOffset,
            boolean exactMapping, String labelPrefix, int depth) {
        if (chunks == null || seen == null || data == null || data.length == 0 || depth > MAX_RECURSION) {
            return;
        }

        collectLengthPrefixedChunks(chunks, seen, data, rawStartOffset, exactMapping, labelPrefix, depth, "VarInt",
                0);
        collectLengthPrefixedChunks(chunks, seen, data, rawStartOffset, exactMapping, labelPrefix, depth, "U16", 2);
        collectLengthPrefixedChunks(chunks, seen, data, rawStartOffset, exactMapping, labelPrefix, depth, "I32", 4);
        collectAsciiChunks(chunks, seen, data, rawStartOffset, exactMapping, labelPrefix, depth);
    }

    private static void collectLengthPrefixedChunks(List<DecodedChunk> chunks, Set<String> seen, byte[] data,
            int rawStartOffset, boolean exactMapping, String labelPrefix, int depth, String mode, int fixedPrefixBytes) {
        int scanLimit = Math.min(data.length, MAX_SCAN_BYTES);
        for (int offset = 0; offset < scanLimit - 2; offset++) {
            int bytesUsed;
            int textLength;
            if ("VarInt".equals(mode)) {
                VarIntRead varInt = readVarInt(data, offset);
                if (varInt == null) {
                    continue;
                }
                bytesUsed = varInt.bytesUsed;
                textLength = varInt.value;
            } else {
                if (offset + fixedPrefixBytes > scanLimit) {
                    continue;
                }
                if ("U16".equals(mode)) {
                    textLength = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                } else {
                    if (offset + 4 > scanLimit) {
                        continue;
                    }
                    textLength = ((data[offset] & 0xFF) << 24)
                            | ((data[offset + 1] & 0xFF) << 16)
                            | ((data[offset + 2] & 0xFF) << 8)
                            | (data[offset + 3] & 0xFF);
                }
                bytesUsed = fixedPrefixBytes;
            }

            if (textLength < 2 || textLength > MAX_EMBEDDED_STRING_BYTES) {
                continue;
            }

            int textStart = offset + bytesUsed;
            int textEnd = textStart + textLength;
            if (textStart < 0 || textEnd > data.length) {
                continue;
            }

            byte[] textBytes = Arrays.copyOfRange(data, textStart, textEnd);
            String decoded = tryDecodeStrict(textBytes, StandardCharsets.UTF_8);
            if (decoded == null && containsHighBytes(textBytes)) {
                decoded = tryDecodeStrict(textBytes, GBK);
            }

            String normalized = normalizeDisplayText(decoded);
            if (!isLikelyHumanText(normalized, 2) && splitEmbeddedEncodedTokens(normalized).isEmpty()) {
                continue;
            }

            int start = exactMapping ? rawStartOffset + offset : rawStartOffset;
            int end = exactMapping ? rawStartOffset + textEnd : rawStartOffset + data.length;
            String label = appendLabel(labelPrefix, mode + "字符串");
            if (isLikelyHumanText(normalized, 2)) {
                addAnnotatedChunk(chunks, seen, start, end, label, normalized);
            }
            collectEmbeddedTokenChunks(chunks, seen, normalized, start, end, label, depth + 1);
            offset = textEnd - 1;
        }
    }

    private static void collectAsciiChunks(List<DecodedChunk> chunks, Set<String> seen, byte[] data, int rawStartOffset,
            boolean exactMapping, String labelPrefix, int depth) {
        int start = -1;
        for (int i = 0; i <= data.length; i++) {
            boolean readable = i < data.length && isAsciiTextByte(data[i] & 0xFF);
            if (readable) {
                if (start < 0) {
                    start = i;
                }
                continue;
            }
            if (start >= 0) {
                int end = i;
                if (end - start >= 4) {
                    String segment = new String(data, start, end - start, StandardCharsets.US_ASCII);
                    String normalized = normalizeDisplayText(segment);
                    int mappedStart = exactMapping ? rawStartOffset + start : rawStartOffset;
                    int mappedEnd = exactMapping ? rawStartOffset + end : rawStartOffset + data.length;
                    if (isLikelyHumanText(normalized, 4)) {
                        addAnnotatedChunk(chunks, seen, mappedStart, mappedEnd, appendLabel(labelPrefix, "ASCII片段"),
                                normalized);
                    }
                    collectEmbeddedTokenChunks(chunks, seen, normalized, mappedStart, mappedEnd,
                            appendLabel(labelPrefix, "ASCII片段"), depth + 1);
                }
                start = -1;
            }
        }
    }

    private static void collectEmbeddedTokenChunks(List<DecodedChunk> chunks, Set<String> seen, String sourceText,
            int rawStartOffset, int rawEndOffset, String labelPrefix, int depth) {
        if (chunks == null || seen == null || sourceText == null || sourceText.isEmpty() || depth > MAX_RECURSION) {
            return;
        }

        int searchFrom = 0;
        for (String token : splitEmbeddedEncodedTokens(sourceText)) {
            if (token == null || token.isEmpty()) {
                continue;
            }
            int tokenPos = sourceText.indexOf(token, searchFrom);
            if (tokenPos >= 0) {
                searchFrom = tokenPos + token.length();
            }
            int tokenStart = tokenPos >= 0 ? rawStartOffset + tokenPos : rawStartOffset;
            int tokenEnd = tokenPos >= 0 ? Math.min(rawEndOffset, tokenStart + token.length()) : rawEndOffset;

            collectConcatenatedBase64NbtLikeChunks(chunks, seen, token, tokenStart, tokenEnd, labelPrefix);

            byte[] decodedBytes = tryDecodeBase64Text(token);
            String nestedLabel = appendLabel(labelPrefix, "Base64解码");
            if (decodedBytes == null) {
                decodedBytes = tryDecodeHexText(token);
                nestedLabel = appendLabel(labelPrefix, "HEX解码");
            }
            if (decodedBytes == null || decodedBytes.length == 0) {
                continue;
            }

            collectGameSpecificNestedChunks(chunks, seen, decodedBytes, tokenStart, tokenEnd, nestedLabel);
            String summary = decode(decodedBytes);
            if (!normalizeDisplayText(summary).isEmpty()) {
                addAnnotatedChunk(chunks, seen, tokenStart, tokenEnd, nestedLabel, normalizeDisplayText(summary));
            }
            collectAnnotatedChunks(chunks, seen, decodedBytes, tokenStart, false, nestedLabel, depth + 1);
        }
    }

    private static void collectConcatenatedBase64NbtLikeChunks(List<DecodedChunk> chunks, Set<String> seen, String token,
            int tokenStartOffset, int tokenEndOffset, String labelPrefix) {
        if (chunks == null || seen == null || token == null || token.length() < 24) {
            return;
        }

        extractConcatenatedItemKeyValue(chunks, seen, token, tokenStartOffset, labelPrefix);
        extractConcatenatedNameValue(chunks, seen, token, tokenStartOffset, labelPrefix);
        extractConcatenatedLoreValues(chunks, seen, token, tokenStartOffset, labelPrefix);
    }

    private static void extractConcatenatedItemKeyValue(List<DecodedChunk> chunks, Set<String> seen, String token,
            int tokenStartOffset, String labelPrefix) {
        int markerIndex = token.indexOf("AARpdGVt");
        if (markerIndex < 0) {
            return;
        }
        int valueLengthIndex = markerIndex + "AARpdGVt".length();
        ExtractedBase64Text value = extractConcatenatedLengthPrefixedText(token, valueLengthIndex);
        if (value == null || value.text.isEmpty()) {
            return;
        }
        addAnnotatedChunk(chunks, seen, tokenStartOffset + markerIndex, tokenStartOffset + value.endIndex,
                appendLabel(labelPrefix, "拼接Base64流 / 物品ID"), value.text);
    }

    private static void extractConcatenatedNameValue(List<DecodedChunk> chunks, Set<String> seen, String token,
            int tokenStartOffset, String labelPrefix) {
        int markerIndex = token.indexOf("CAAETmFtZQ");
        if (markerIndex < 0 || markerIndex + 12 > token.length()) {
            return;
        }
        byte[] markerBytes = tryDecodeUnpaddedBase64(token.substring(markerIndex, markerIndex + 12));
        if (markerBytes == null || markerBytes.length < 8) {
            return;
        }
        int byteLength = ((markerBytes[6] & 0xFF) << 8) | (markerBytes[7] & 0xFF);
        if (byteLength <= 0 || byteLength > 512) {
            return;
        }
        int valueStart = markerIndex + 12;
        int encodedCharLength = encodedLengthWithoutPadding(byteLength);
        if (valueStart + encodedCharLength > token.length()) {
            return;
        }
        String encodedValue = token.substring(valueStart, valueStart + encodedCharLength);
        byte[] valueBytes = tryDecodeUnpaddedBase64(encodedValue);
        if (valueBytes == null || valueBytes.length != byteLength) {
            return;
        }
        String text = sanitizeMinecraftText(new String(valueBytes, StandardCharsets.UTF_8));
        if (text.isEmpty()) {
            return;
        }
        addAnnotatedChunk(chunks, seen, tokenStartOffset + markerIndex, tokenStartOffset + valueStart + encodedCharLength,
                appendLabel(labelPrefix, "拼接Base64流 / NBT名称"), text);
    }

    private static void extractConcatenatedLoreValues(List<DecodedChunk> chunks, Set<String> seen, String token,
            int tokenStartOffset, String labelPrefix) {
        int markerIndex = token.indexOf("CQAETG9yZQ");
        if (markerIndex < 0 || markerIndex + 16 > token.length()) {
            return;
        }
        byte[] markerBytes = tryDecodeUnpaddedBase64(token.substring(markerIndex, markerIndex + 16));
        if (markerBytes == null || markerBytes.length < 12) {
            return;
        }
        int count = ((markerBytes[8] & 0xFF) << 24)
                | ((markerBytes[9] & 0xFF) << 16)
                | ((markerBytes[10] & 0xFF) << 8)
                | (markerBytes[11] & 0xFF);
        if (count <= 0 || count > 16) {
            return;
        }

        int cursor = markerIndex + 16;
        List<String> loreLines = new ArrayList<>();
        int finalEnd = cursor;
        for (int i = 0; i < count; i++) {
            ExtractedBase64Text value = extractConcatenatedLengthPrefixedText(token, cursor);
            if (value == null || value.text.isEmpty()) {
                break;
            }
            loreLines.add(sanitizeMinecraftText(value.text));
            cursor = value.endIndex;
            finalEnd = value.endIndex;
        }
        loreLines.removeIf(line -> line == null || line.trim().isEmpty());
        if (loreLines.isEmpty()) {
            return;
        }
        addAnnotatedChunk(chunks, seen, tokenStartOffset + markerIndex, tokenStartOffset + finalEnd,
                appendLabel(labelPrefix, "拼接Base64流 / NBT说明(Lore)"), String.join("\n", loreLines));
    }

    private static ExtractedBase64Text extractConcatenatedLengthPrefixedText(String token, int prefixIndex) {
        if (token == null || prefixIndex < 0 || prefixIndex + 3 > token.length()) {
            return null;
        }
        byte[] lengthBytes = tryDecodeUnpaddedBase64(token.substring(prefixIndex, prefixIndex + 3));
        if (lengthBytes == null || lengthBytes.length < 2) {
            return null;
        }
        int byteLength = ((lengthBytes[0] & 0xFF) << 8) | (lengthBytes[1] & 0xFF);
        if (byteLength <= 0 || byteLength > 2048) {
            return null;
        }
        int valueStart = prefixIndex + 3;
        int encodedCharLength = encodedLengthWithoutPadding(byteLength);
        if (valueStart + encodedCharLength > token.length()) {
            return null;
        }
        String encoded = token.substring(valueStart, valueStart + encodedCharLength);
        byte[] valueBytes = tryDecodeUnpaddedBase64(encoded);
        if (valueBytes == null || valueBytes.length != byteLength) {
            return null;
        }
        String text = new String(valueBytes, StandardCharsets.UTF_8);
        return new ExtractedBase64Text(prefixIndex, valueStart + encodedCharLength, text);
    }

    private static void collectGameSpecificNestedChunks(List<DecodedChunk> chunks, Set<String> seen, byte[] nestedBytes,
            int startOffset, int endOffset, String labelPrefix) {
        if (chunks == null || seen == null || nestedBytes == null || nestedBytes.length == 0) {
            return;
        }

        ItemStack stack = tryReadItemStackPayload(nestedBytes);
        if (!stack.isEmpty()) {
            addItemStackChunks(chunks, seen, startOffset, endOffset, labelPrefix, stack);
            return;
        }

        NBTTagCompound compressedNbt = tryReadCompressedNbt(nestedBytes);
        if (compressedNbt != null && !compressedNbt.hasNoTags()) {
            addNbtChunks(chunks, seen, startOffset, endOffset, appendLabel(labelPrefix, "NBT"), compressedNbt);
        }
    }

    private static void addAnnotatedChunk(List<DecodedChunk> chunks, Set<String> seen, int startOffset, int endOffset,
            String label, String text) {
        String normalizedText = normalizeDisplayText(text);
        if (normalizedText.isEmpty()) {
            return;
        }
        int safeStart = Math.max(0, startOffset);
        int safeEnd = Math.max(safeStart, endOffset);
        String safeLabel = label == null ? "" : label.trim();
        String key = safeStart + ":" + safeEnd + ":" + safeLabel + ":" + normalizedText;
        if (!seen.add(key)) {
            return;
        }
        chunks.add(new DecodedChunk(safeStart, safeEnd, safeLabel, normalizedText));
    }

    public static final class DecodedChunk {
        public final int startOffset;
        public final int endOffset;
        public final String label;
        public final String text;

        public DecodedChunk(int startOffset, int endOffset, String label, String text) {
            this.startOffset = Math.max(0, startOffset);
            this.endOffset = Math.max(this.startOffset, endOffset);
            this.label = label == null ? "" : label.trim();
            this.text = text == null ? "" : text.trim();
        }
    }

    public static final class AnnotatedDecodeReport {
        public final String summaryText;
        public final String detailText;
        public final List<DecodedChunk> chunks;

        public AnnotatedDecodeReport(String summaryText, String detailText, List<DecodedChunk> chunks) {
            this.summaryText = summaryText == null ? "" : summaryText;
            this.detailText = detailText == null ? "" : detailText;
            this.chunks = chunks == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(chunks));
        }
    }

    private static final class StructuredField {
        private final int startOffset;
        private final int endOffset;
        private final String label;
        private final String text;
        private final byte[] nestedBytes;
        private final int nextOffset;

        private StructuredField(int startOffset, int endOffset, String label, String text, byte[] nestedBytes,
                int nextOffset) {
            this.startOffset = Math.max(0, startOffset);
            this.endOffset = Math.max(this.startOffset, endOffset);
            this.label = label == null ? "" : label.trim();
            this.text = text == null ? "" : text.trim();
            this.nestedBytes = nestedBytes;
            this.nextOffset = Math.max(0, nextOffset);
        }
    }

    private static final class ExtractedBase64Text {
        private final int startIndex;
        private final int endIndex;
        private final String text;

        private ExtractedBase64Text(int startIndex, int endIndex, String text) {
            this.startIndex = Math.max(0, startIndex);
            this.endIndex = Math.max(this.startIndex, endIndex);
            this.text = text == null ? "" : text;
        }
    }

    private static final class ChunkGroup {
        private int startOffset;
        private int endOffset;
        private final String normalizedText;
        private final String displayText;
        private final LinkedHashSet<String> labels = new LinkedHashSet<>();

        private ChunkGroup(DecodedChunk chunk, String normalizedText) {
            this.startOffset = chunk.startOffset;
            this.endOffset = chunk.endOffset;
            this.normalizedText = normalizedText == null ? "" : normalizedText;
            this.displayText = chunk.text == null ? "" : chunk.text;
            if (chunk.label != null && !chunk.label.trim().isEmpty()) {
                this.labels.add(chunk.label.trim());
            }
        }

        private boolean canMerge(DecodedChunk chunk, String otherNormalizedText) {
            if (chunk == null) {
                return false;
            }
            if (!this.normalizedText.equals(otherNormalizedText == null ? "" : otherNormalizedText)) {
                return false;
            }
            return rangesOverlap(this.startOffset, this.endOffset, chunk.startOffset, chunk.endOffset);
        }

        private void merge(DecodedChunk chunk) {
            if (chunk == null) {
                return;
            }
            this.startOffset = Math.min(this.startOffset, chunk.startOffset);
            this.endOffset = Math.max(this.endOffset, chunk.endOffset);
            if (chunk.label != null && !chunk.label.trim().isEmpty()) {
                this.labels.add(chunk.label.trim());
            }
        }

        private DecodedChunk toChunk() {
            String label;
            if (labels.isEmpty()) {
                label = "";
            } else if (labels.size() == 1) {
                label = labels.iterator().next();
            } else {
                label = "▼ " + String.join(" / ", labels);
            }
            return new DecodedChunk(startOffset, endOffset, label, displayText);
        }
    }

    private static boolean rangesOverlap(int startA, int endA, int startB, int endB) {
        return Math.max(startA, startB) < Math.min(endA, endB);
    }

    private static final class VarIntRead {
        private final int value;
        private final int bytesUsed;

        private VarIntRead(int value, int bytesUsed) {
            this.value = value;
            this.bytesUsed = bytesUsed;
        }
    }

    private static final class DecodeCandidate {
        private final String label;
        private final String text;
        private final int score;

        private DecodeCandidate(String label, String text, int score) {
            this.label = label == null ? "" : label.trim();
            this.text = text == null ? "" : text.trim();
            this.score = score;
        }

        private String format() {
            if (label.isEmpty()) {
                return text;
            }
            return "[" + label + "]\n" + text;
        }
    }

    private static final class DecodeReport {
        private final String summaryText;
        private final String detailText;

        private DecodeReport(String summaryText, String detailText) {
            this.summaryText = summaryText == null ? "" : summaryText;
            this.detailText = detailText == null ? "" : detailText;
        }
    }

    private static final class CandidateCollector {
        private final Map<String, DecodeCandidate> bestCandidates = new LinkedHashMap<>();

        private void add(String label, String text, int score) {
            String normalizedText = normalizeDisplayText(text);
            if (normalizedText.isEmpty()) {
                return;
            }

            String normalizedLabel = label == null ? "" : label.trim();
            String key = normalizedText.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
            if (key.length() > 4096) {
                key = key.substring(0, 4096);
            }

            DecodeCandidate existing = bestCandidates.get(key);
            DecodeCandidate incoming = new DecodeCandidate(normalizedLabel, normalizedText, score);
            if (existing == null || incoming.score > existing.score
                    || (incoming.score == existing.score && incoming.text.length() > existing.text.length())) {
                bestCandidates.put(key, incoming);
            }
        }

        private DecodeCandidate getBest() {
            DecodeCandidate best = null;
            for (DecodeCandidate candidate : bestCandidates.values()) {
                if (candidate == null) {
                    continue;
                }
                int candidateRank = rankOf(candidate);
                int bestRank = best == null ? Integer.MIN_VALUE : rankOf(best);
                if (best == null || candidateRank > bestRank
                        || (candidateRank == bestRank && candidate.text.length() > best.text.length())) {
                    best = candidate;
                }
            }
            return best;
        }

        private List<DecodeCandidate> getRankedCandidates() {
            List<DecodeCandidate> ranked = new ArrayList<>(bestCandidates.values());
            Collections.sort(ranked, (left, right) -> {
                int leftRank = rankOf(left);
                int rightRank = rankOf(right);
                if (leftRank != rightRank) {
                    return Integer.compare(rightRank, leftRank);
                }
                if (left.text.length() != right.text.length()) {
                    return Integer.compare(right.text.length(), left.text.length());
                }
                return left.label.compareTo(right.label);
            });
            return ranked;
        }

        private int rankOf(DecodeCandidate candidate) {
            if (candidate == null) {
                return Integer.MIN_VALUE;
            }

            int rank = candidate.score + Math.min(40, candidate.text.length() / 12);
            String label = candidate.label == null ? "" : candidate.label;
            String text = candidate.text == null ? "" : candidate.text;

            if (isPreferredStructuredCandidate(candidate)) {
                rank += 220;
            } else if (PacketPayloadDecoder.looksStructuredText(text)) {
                rank += 40;
            }

            if (label.contains("UTF-8")) {
                rank += 12;
            }

            if (looksFragmentedEmbeddedText(candidate)) {
                rank -= 180;
            } else {
                if (label.contains("嵌入字符串")) {
                    rank -= 120;
                }
                if (label.contains("片段")) {
                    rank -= 90;
                }
                if (label.contains("Minecraft字符串")) {
                    rank += 120;
                    if (containsStrongNaturalText(text)) {
                        rank += 40;
                    }
                }
            }

            if (text.contains(" | ")) {
                rank -= 35;
            }

            if (text.endsWith("\":\"") || text.endsWith("\":")) {
                rank -= 60;
            }

            return rank;
        }
    }

    private static boolean containsStrongNaturalText(String text) {
        String normalized = normalizeDisplayText(text);
        if (normalized.isEmpty()) {
            return false;
        }
        int cjk = 0;
        int asciiWord = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isCjk(c)) {
                cjk++;
            } else if (Character.isLetterOrDigit(c)) {
                asciiWord++;
            }
        }
        return cjk >= 2 || (cjk >= 1 && asciiWord >= 1);
    }
}

