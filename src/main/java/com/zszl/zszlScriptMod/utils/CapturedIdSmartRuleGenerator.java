package com.zszl.zszlScriptMod.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class CapturedIdSmartRuleGenerator {

    private CapturedIdSmartRuleGenerator() {
    }

    public static final class Proposal {
        private final int index;
        private final String pattern;
        private final int minBytes;
        private final int maxBytes;
        private final List<String> sampleValues;
        private final String summary;

        private Proposal(int index, String pattern, int minBytes, int maxBytes, List<String> sampleValues,
                String summary) {
            this.index = index;
            this.pattern = pattern == null ? "" : pattern;
            this.minBytes = Math.max(0, minBytes);
            this.maxBytes = Math.max(this.minBytes, maxBytes);
            this.sampleValues = sampleValues == null ? Collections.<String>emptyList() : sampleValues;
            this.summary = summary == null ? "" : summary;
        }

        public int getIndex() {
            return index;
        }

        public String getPattern() {
            return pattern;
        }

        public int getMinBytes() {
            return minBytes;
        }

        public int getMaxBytes() {
            return maxBytes;
        }

        public List<String> getSampleValues() {
            return sampleValues;
        }

        public String getSummary() {
            return summary;
        }
    }

    public static final class AnalysisResult {
        private final List<String> normalizedSamples;
        private final List<Proposal> proposals;
        private final String message;

        private AnalysisResult(List<String> normalizedSamples, List<Proposal> proposals, String message) {
            this.normalizedSamples = normalizedSamples == null ? Collections.<String>emptyList() : normalizedSamples;
            this.proposals = proposals == null ? Collections.<Proposal>emptyList() : proposals;
            this.message = message == null ? "" : message;
        }

        public List<String> getNormalizedSamples() {
            return normalizedSamples;
        }

        public List<Proposal> getProposals() {
            return proposals;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class Segment {
        private final boolean stable;
        private final List<String> stableTokens;
        private final List<List<String>> sampleTokens;

        private Segment(boolean stable, List<String> stableTokens, List<List<String>> sampleTokens) {
            this.stable = stable;
            this.stableTokens = stableTokens == null ? Collections.<String>emptyList() : stableTokens;
            this.sampleTokens = sampleTokens == null ? Collections.<List<String>>emptyList() : sampleTokens;
        }
    }

    private static final class Anchor {
        private final List<String> tokens;
        private final int[] positions;

        private Anchor(List<String> tokens, int[] positions) {
            this.tokens = tokens;
            this.positions = positions;
        }
    }

    public static AnalysisResult analyze(List<String> rawSamples) {
        List<List<String>> samples = new ArrayList<>();
        List<String> normalizedSamples = new ArrayList<>();
        if (rawSamples != null) {
            for (String raw : rawSamples) {
                List<String> tokens = tokenize(raw);
                if (tokens.isEmpty()) {
                    continue;
                }
                samples.add(tokens);
                normalizedSamples.add(joinTokens(tokens));
            }
        }

        if (samples.size() < 2) {
            return new AnalysisResult(normalizedSamples, Collections.<Proposal>emptyList(), "至少输入两组有效 HEX 样本");
        }

        int[] starts = new int[samples.size()];
        int[] ends = new int[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            ends[i] = samples.get(i).size();
        }

        List<Segment> segments = new ArrayList<>();
        buildSegments(samples, starts, ends, segments);
        segments = compactSegments(segments);

        List<Proposal> proposals = buildProposals(segments, samples.size());
        if (proposals.isEmpty()) {
            return new AnalysisResult(normalizedSamples, proposals, "未检测到稳定可提取的变化区段");
        }
        return new AnalysisResult(normalizedSamples, proposals, "已识别 " + proposals.size() + " 组变化区段");
    }

    public static CapturedIdRuleManager.RuleEditModel buildRuleModel(Proposal proposal, String name,
            String displayName, String category, String channel, String direction) {
        CapturedIdRuleManager.RuleEditModel model = new CapturedIdRuleManager.RuleEditModel();
        model.name = safe(name).trim();
        model.displayName = safe(displayName).trim();
        model.note = safe(proposal == null ? "" : proposal.getSummary());
        model.aliasesCsv = "";
        model.enabled = true;
        model.channel = safe(channel).trim();
        model.direction = safe(direction).trim().isEmpty() ? "both" : safe(direction).trim();
        model.target = "hex";
        model.pattern = proposal == null ? "" : proposal.getPattern();
        model.offset = "";
        model.group = 1;
        model.category = safe(category).trim();
        model.valueType = "hex";
        model.byteLength = proposal == null ? 1 : Math.max(1, proposal.getMaxBytes());
        model.updateSequenceName = "";
        model.updateSequenceMode = "always";
        model.updateSequenceCooldownMs = 1000;
        return model;
    }

    private static void buildSegments(List<List<String>> samples, int[] starts, int[] ends, List<Segment> out) {
        if (samples == null || samples.isEmpty() || out == null) {
            return;
        }
        boolean allEmpty = true;
        for (int i = 0; i < samples.size(); i++) {
            if (starts[i] < ends[i]) {
                allEmpty = false;
                break;
            }
        }
        if (allEmpty) {
            return;
        }

        if (allSlicesEqual(samples, starts, ends)) {
            out.add(new Segment(true, slice(samples.get(0), starts[0], ends[0]), null));
            return;
        }

        Anchor anchor = findLongestCommonAnchor(samples, starts, ends);
        if (anchor == null || anchor.tokens.isEmpty()) {
            List<List<String>> variableSamples = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                variableSamples.add(slice(samples.get(i), starts[i], ends[i]));
            }
            out.add(new Segment(false, null, variableSamples));
            return;
        }

        int[] leftEnds = new int[ends.length];
        int[] rightStarts = new int[starts.length];
        for (int i = 0; i < samples.size(); i++) {
            leftEnds[i] = anchor.positions[i];
            rightStarts[i] = anchor.positions[i] + anchor.tokens.size();
        }

        buildSegments(samples, starts, leftEnds, out);
        out.add(new Segment(true, anchor.tokens, null));
        buildSegments(samples, rightStarts, ends, out);
    }

    private static List<Segment> compactSegments(List<Segment> segments) {
        List<Segment> result = new ArrayList<>();
        if (segments == null) {
            return result;
        }
        for (Segment segment : segments) {
            if (segment == null) {
                continue;
            }
            if (segment.stable && segment.stableTokens.isEmpty()) {
                continue;
            }
            if (!segment.stable && isAllVariableSamplesEmpty(segment.sampleTokens)) {
                continue;
            }

            if (!result.isEmpty() && result.get(result.size() - 1).stable == segment.stable) {
                Segment previous = result.remove(result.size() - 1);
                if (segment.stable) {
                    List<String> mergedTokens = new ArrayList<>(previous.stableTokens);
                    mergedTokens.addAll(segment.stableTokens);
                    result.add(new Segment(true, mergedTokens, null));
                } else {
                    List<List<String>> mergedSamples = new ArrayList<>();
                    int size = Math.max(previous.sampleTokens.size(), segment.sampleTokens.size());
                    for (int i = 0; i < size; i++) {
                        List<String> merged = new ArrayList<>();
                        if (i < previous.sampleTokens.size()) {
                            merged.addAll(previous.sampleTokens.get(i));
                        }
                        if (i < segment.sampleTokens.size()) {
                            merged.addAll(segment.sampleTokens.get(i));
                        }
                        mergedSamples.add(merged);
                    }
                    result.add(new Segment(false, null, mergedSamples));
                }
            } else {
                result.add(segment);
            }
        }
        return result;
    }

    private static List<Proposal> buildProposals(List<Segment> segments, int sampleCount) {
        List<Proposal> proposals = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return proposals;
        }

        List<Integer> variableSegmentIndexes = new ArrayList<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment != null && !segment.stable && hasMeaningfulVariation(segment.sampleTokens)) {
                variableSegmentIndexes.add(i);
            }
        }

        for (int proposalIndex = 0; proposalIndex < variableSegmentIndexes.size(); proposalIndex++) {
            int targetSegmentIndex = variableSegmentIndexes.get(proposalIndex);
            Segment targetSegment = segments.get(targetSegmentIndex);
            int minBytes = Integer.MAX_VALUE;
            int maxBytes = 0;
            List<String> sampleValues = new ArrayList<>();
            for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
                List<String> tokens = sampleIndex < targetSegment.sampleTokens.size()
                        ? targetSegment.sampleTokens.get(sampleIndex)
                        : Collections.<String>emptyList();
                int size = tokens == null ? 0 : tokens.size();
                minBytes = Math.min(minBytes, size);
                maxBytes = Math.max(maxBytes, size);
                sampleValues.add(size <= 0 ? "(空)" : joinTokens(tokens));
            }
            if (minBytes == Integer.MAX_VALUE) {
                minBytes = 0;
            }

            StringBuilder patternBuilder = new StringBuilder();
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                Segment segment = segments.get(segmentIndex);
                if (segment == null) {
                    continue;
                }
                if (segment.stable) {
                    patternBuilder.append(buildExactTokenRegex(segment.stableTokens));
                } else {
                    int segMin = Integer.MAX_VALUE;
                    int segMax = 0;
                    for (List<String> tokens : segment.sampleTokens) {
                        int size = tokens == null ? 0 : tokens.size();
                        segMin = Math.min(segMin, size);
                        segMax = Math.max(segMax, size);
                    }
                    if (segMin == Integer.MAX_VALUE) {
                        segMin = 0;
                    }
                    patternBuilder.append(buildVariableRegex(segMin, segMax, segmentIndex == targetSegmentIndex));
                }
            }

            String summary = "变化区段 " + (proposalIndex + 1)
                    + " | 长度 " + minBytes + "-" + maxBytes + " 字节"
                    + " | 样本值 " + sampleValues;
            proposals.add(new Proposal(proposalIndex + 1, patternBuilder.toString(), minBytes, maxBytes, sampleValues,
                    summary));
        }
        return proposals;
    }

    private static Anchor findLongestCommonAnchor(List<List<String>> samples, int[] starts, int[] ends) {
        List<String> first = samples.get(0);
        int available = Math.max(0, ends[0] - starts[0]);
        for (int length = available; length >= 1; length--) {
            for (int start = starts[0]; start + length <= ends[0]; start++) {
                List<String> candidate = slice(first, start, start + length);
                int[] positions = new int[samples.size()];
                positions[0] = start;
                boolean matched = true;
                for (int sampleIndex = 1; sampleIndex < samples.size(); sampleIndex++) {
                    positions[sampleIndex] = findSubsequence(samples.get(sampleIndex), candidate, starts[sampleIndex],
                            ends[sampleIndex]);
                    if (positions[sampleIndex] < 0) {
                        matched = false;
                        break;
                    }
                }
                if (matched) {
                    return new Anchor(candidate, positions);
                }
            }
        }
        return null;
    }

    private static int findSubsequence(List<String> source, List<String> candidate, int start, int end) {
        if (source == null || candidate == null || candidate.isEmpty() || start < 0 || end > source.size()) {
            return -1;
        }
        int max = end - candidate.size();
        for (int i = start; i <= max; i++) {
            boolean matched = true;
            for (int j = 0; j < candidate.size(); j++) {
                if (!source.get(i + j).equals(candidate.get(j))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i;
            }
        }
        return -1;
    }

    private static boolean allSlicesEqual(List<List<String>> samples, int[] starts, int[] ends) {
        List<String> first = slice(samples.get(0), starts[0], ends[0]);
        for (int i = 1; i < samples.size(); i++) {
            if (!first.equals(slice(samples.get(i), starts[i], ends[i]))) {
                return false;
            }
        }
        return !first.isEmpty();
    }

    private static boolean isAllVariableSamplesEmpty(List<List<String>> sampleTokens) {
        if (sampleTokens == null) {
            return true;
        }
        for (List<String> tokens : sampleTokens) {
            if (tokens != null && !tokens.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasMeaningfulVariation(List<List<String>> sampleTokens) {
        if (sampleTokens == null || sampleTokens.isEmpty()) {
            return false;
        }
        String first = null;
        for (List<String> tokens : sampleTokens) {
            String value = tokens == null || tokens.isEmpty() ? "" : joinTokens(tokens);
            if (first == null) {
                first = value;
            } else if (!first.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private static String buildExactTokenRegex(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            builder.append(Pattern.quote(token)).append("\\s*");
        }
        return builder.toString();
    }

    private static String buildVariableRegex(int minBytes, int maxBytes, boolean capturing) {
        int safeMin = Math.max(0, minBytes);
        int safeMax = Math.max(safeMin, maxBytes);
        String body = "(?:[0-9A-Fa-f]{2}\\s*)";
        String quantifier = "{" + safeMin + "," + safeMax + "}";
        if (safeMin == safeMax) {
            quantifier = "{" + safeMin + "}";
        }
        String pattern = body + quantifier;
        return capturing ? "(" + pattern + ")" : pattern;
    }

    private static List<String> tokenize(String raw) {
        String text = safe(raw).replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        if ((text.length() & 1) != 0) {
            text = "0" + text;
        }
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i + 1 < text.length(); i += 2) {
            tokens.add(text.substring(i, i + 2));
        }
        return tokens;
    }

    private static List<String> slice(List<String> tokens, int start, int end) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }
        int safeStart = Math.max(0, Math.min(start, tokens.size()));
        int safeEnd = Math.max(safeStart, Math.min(end, tokens.size()));
        return new ArrayList<>(tokens.subList(safeStart, safeEnd));
    }

    private static String joinTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(tokens.get(i));
        }
        return builder.toString();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }
}
