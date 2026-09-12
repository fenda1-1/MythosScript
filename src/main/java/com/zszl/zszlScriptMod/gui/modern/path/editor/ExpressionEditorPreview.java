package com.zszl.zszlScriptMod.gui.modern.path.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ExpressionTemplateCard;
import com.zszl.zszlScriptMod.path.ActionVariableRegistry;
import com.zszl.zszlScriptMod.path.DroppedPickupTarget;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.path.LegacyActionRuntime;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;
import com.zszl.zszlScriptMod.path.runtime.ScopedRuntimeVariables;
import com.zszl.zszlScriptMod.utils.CapturedIdPreviewValues;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.ItemStack;

/**
 * Side-effect-free expression preview, dependency tracing and completion data.
 */
public final class ExpressionEditorPreview {
    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_\\.\\[\\]-]+)\\s*\\}");
    private static final Set<String> KEYWORDS = keywordSet();
    private static final int MAX_DEPENDENCY_DEPTH = 48;

    private ExpressionEditorPreview() {
    }

    public enum Mode {
        VALUE, BOOLEAN, ITEM_FILTER
    }

    public static final class Line {
        public final String text;
        public final int color;
        public final int indent;

        public Line(String text, int color, int indent) {
            this.text = text == null ? "" : text;
            this.color = color;
            this.indent = Math.max(0, indent);
        }
    }

    public static List<Line> build(String expression, Mode mode,
            PathSequence sequence, int stepIndex, int actionIndex, ExpressionTemplateCard selectedTemplate) {
        List<PathSequence> sequences = sequence == null
                ? Collections.<PathSequence>emptyList() : Collections.singletonList(sequence);
        return build(expression, mode, sequences, sequence, stepIndex, actionIndex, selectedTemplate);
    }

    public static List<Line> build(String expression, Mode mode, Collection<PathSequence> sequences,
            PathSequence sequence, int stepIndex, int actionIndex, ExpressionTemplateCard selectedTemplate) {
        CapturedIdPreviewValues.Lookup captured = CapturedIdPreviewValues.current();
        List<Line> report = LegacyActionRuntime.withPreviewCapturedIds(captured,
                () -> buildWithCapturedIds(expression, mode, sequences, sequence, stepIndex, actionIndex, selectedTemplate));
        for (Map.Entry<String, String> source : captured.sources().entrySet()) {
            report.add(new Line("捕获ID " + source.getKey() + "：" + source.getValue(), ModernUiRenderer.SUBTLE_TEXT, 0));
        }
        return report;
    }

    private static List<Line> buildWithCapturedIds(String expression, Mode mode, Collection<PathSequence> sequences,
            PathSequence sequence, int stepIndex, int actionIndex, ExpressionTemplateCard selectedTemplate) {
        List<Line> lines = new ArrayList<Line>();
        String text = expression == null ? "" : expression.trim();
        if (text.isEmpty()) {
            lines.add(new Line("gui.modern.path.expr.u001",
                    ModernUiRenderer.MUTED_TEXT, 0));
            lines.add(new Line("gui.modern.path.expr.u002",
                    ModernUiRenderer.MUTED_TEXT, 0));
            appendTemplate(lines, selectedTemplate);
            return lines;
        }

        EvaluationContext context = buildContext(sequences, sequence, stepIndex, actionIndex);
        Set<String> references = mode == Mode.ITEM_FILTER
                ? Collections.<String>emptySet() : extractReferences(text);
        for (String reference : references) {
            context.resolve(normalizeReference(reference), 0);
        }
        Map<String, Object> runtime = nestedRuntime(context.values);

        appendSyntaxAndResult(lines, text, mode, runtime, sequence, stepIndex, actionIndex,
                currentActionParams(sequence, stepIndex, actionIndex));
        appendReferences(lines, references, context, runtime, sequence, stepIndex, actionIndex);
        appendTemplate(lines, selectedTemplate);
        return lines;
    }

    public static List<ExpressionLanguageSupport.Completion> buildCompletions(Collection<PathSequence> sequences,
            PathSequence sequence, int stepIndex, int actionIndex) {
        EvaluationContext context = buildContext(sequences, sequence, stepIndex, actionIndex);
        LinkedHashSet<String> variableNames = new LinkedHashSet<String>();
        for (ActionVariableRegistry.VariableEntry entry : ActionVariableRegistry.collectVariables(
                sequences == null ? Collections.<PathSequence>emptyList() : new ArrayList<PathSequence>(sequences))) {
            if (entry == null) {
                continue;
            }
            variableNames.add(normalizeReference(entry.getVariableName()));
            for (ActionVariableRegistry.VariableSource source : entry.getSources()) {
                for (String produced : ActionVariableRegistry.collectProducedVariableNames(source)) {
                    variableNames.add(normalizeReference(produced));
                }
            }
        }
        variableNames.addAll(context.assignments.keySet());

        List<ExpressionLanguageSupport.Completion> result = new ArrayList<ExpressionLanguageSupport.Completion>();
        for (String name : variableNames) {
            if (name.isEmpty()) {
                continue;
            }
            Resolution resolution = context.resolve(name, 0);
            result.add(new ExpressionLanguageSupport.Completion(name, name,
                    variableDetail(name), completionDescription(resolution),
                    ExpressionLanguageSupport.CompletionKind.VARIABLE));
        }

        Map<String, Object> builtins = builtinValues(sequence, stepIndex, actionIndex);
        for (ExpressionLanguageSupport.Completion builtin : ExpressionLanguageSupport.BUILTIN_VARIABLE_COMPLETIONS) {
            String name = builtin.insertText;
            Object value = LegacyActionRuntime.getRuntimeValue(name, Collections.<String, Object>emptyMap(),
                    currentPlayer(), sequence, stepIndex, actionIndex);
            String description = builtins.containsKey(name) || value != null
                    ? tr("gui.modern.path.expr.fmt.current_source", tr(renderValue(value)))
                    : "gui.modern.path.expr.u003";
            result.add(new ExpressionLanguageSupport.Completion(name, builtin.label, "gui.modern.path.expr.u004", description,
                    ExpressionLanguageSupport.CompletionKind.VARIABLE));
        }
        for (String function : LegacyActionRuntime.getRegisteredExpressionFunctionNames()) {
            result.add(new ExpressionLanguageSupport.Completion(function, function + "(...)", "gui.modern.path.expr.u005",
                    "gui.modern.path.expr.u006", ExpressionLanguageSupport.CompletionKind.FUNCTION));
        }
        return result;
    }

    private static EvaluationContext buildContext(Collection<PathSequence> sequences, PathSequence currentSequence,
            int stepIndex, int actionIndex) {
        List<Assignment> assignments = collectAssignments(sequences, currentSequence, stepIndex, actionIndex);
        Map<String, Object> live = liveValues(currentSequence);
        return new EvaluationContext(assignments, live, currentSequence, stepIndex, actionIndex);
    }

    private static void appendSyntaxAndResult(List<Line> lines, String text, Mode mode,
            Map<String, Object> runtime, PathSequence sequence, int stepIndex, int actionIndex, JsonObject data) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc == null ? null : mc.player;
        try {
            if (mode == Mode.ITEM_FILTER) {
                InventoryItemFilterExpressionEngine.validate(text);
            } else if (mode == Mode.BOOLEAN) {
                LegacyActionRuntime.validateBooleanExpressionSyntax(text, data);
            } else {
                LegacyActionRuntime.validateValueExpressionSyntax(text, data);
            }
            lines.add(new Line("gui.modern.path.expr.u007", ModernUiRenderer.SUCCESS, 0));
        } catch (Exception ex) {
            lines.add(new Line("gui.modern.path.expr.u008", ModernUiRenderer.WARNING, 0));
            lines.add(new Line(safeMessage(ex), ModernUiRenderer.WARNING, 1));
            return;
        }

        try {
            if (mode == Mode.ITEM_FILTER) {
                appendItemFilterResult(lines, text, mc);
            } else if (mode == Mode.BOOLEAN) {
                boolean result = LegacyActionRuntime.evaluateExpression(text, data, runtime, player, sequence,
                        stepIndex, actionIndex);
                lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_bool", String.valueOf(result)),
                        result ? ModernUiRenderer.SUCCESS : ModernUiRenderer.ACCENT, 0));
            } else {
                Object result = LegacyActionRuntime.evaluateValueExpression(text, data, runtime, player, sequence,
                        stepIndex, actionIndex);
                lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_typed", tr(renderValue(result)), tr(typeName(result))),
                        ModernUiRenderer.ACCENT, 0));
            }
        } catch (Exception ex) {
            lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_fail", safeMessage(ex)), ModernUiRenderer.WARNING, 0));
        }
    }

    private static void appendItemFilterResult(List<Line> lines, String text, Minecraft mc) {
        if (mc == null || mc.player == null) {
            lines.add(new Line("gui.modern.path.expr.u009", ModernUiRenderer.MUTED_TEXT, 0));
            return;
        }
        ItemStack held = mc.player.getHeldItemMainhand();
        EntityXPOrb experienceOrb = findNearestExperienceOrb(mc);
        if ((held == null || held.isEmpty()) && experienceOrb == null) {
            lines.add(new Line("gui.modern.path.expr.u010", ModernUiRenderer.MUTED_TEXT, 0));
            return;
        }
        if (held != null && !held.isEmpty()) {
            boolean matched = InventoryItemFilterExpressionEngine.matches(held, mc.player.inventory.currentItem, text);
            lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_item", held.getDisplayName(), String.valueOf(held.getCount())),
                    ModernUiRenderer.SUBTLE_TEXT, 0));
            lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_result", tr(matched ? "gui.modern.path.expr.u011" : "gui.modern.path.expr.u012")),
                    matched ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING, 0));
        }
        if (experienceOrb != null) {
            double distance = Math.sqrt(mc.player.getDistanceSq(experienceOrb));
            boolean matched = InventoryItemFilterExpressionEngine.matches(experienceOrb, text, "common", distance);
            lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_drop", DroppedPickupTarget.getDisplayName(experienceOrb)),
                    ModernUiRenderer.SUBTLE_TEXT, 0));
            lines.add(new Line(tr("gui.modern.path.expr.fmt.preview_result", tr(matched ? "gui.modern.path.expr.u011" : "gui.modern.path.expr.u012")),
                    matched ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING, 0));
        }
    }

    private static EntityXPOrb findNearestExperienceOrb(Minecraft mc) {
        if (mc == null || mc.player == null || mc.world == null || mc.world.loadedEntityList == null) {
            return null;
        }
        EntityXPOrb nearest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityXPOrb) || entity.isDead) {
                continue;
            }
            double distanceSq = mc.player.getDistanceSq(entity);
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq;
                nearest = (EntityXPOrb) entity;
            }
        }
        return nearest;
    }

    private static void appendReferences(List<Line> lines, Set<String> references, EvaluationContext context,
            Map<String, Object> runtime, PathSequence sequence, int stepIndex, int actionIndex) {
        lines.add(new Line("", ModernUiRenderer.MUTED_TEXT, 0));
        if (references.isEmpty()) {
            lines.add(new Line("gui.modern.path.expr.u013", ModernUiRenderer.MUTED_TEXT, 0));
            return;
        }
        lines.add(new Line(tr("gui.modern.path.expr.fmt.refs", String.valueOf(references.size())), ModernUiRenderer.TEXT, 0));
        for (String raw : references) {
            String reference = normalizeReference(raw);
            lines.add(new Line(reference, ModernUiRenderer.TEXT, 0));
            Resolution resolution = context.resolve(reference, 0);
            if (resolution.state == ResolutionState.AVAILABLE) {
                lines.add(new Line(tr(resolution.live ? "gui.modern.path.expr.fmt.live_value" : "gui.modern.path.expr.fmt.eval_value", tr(renderValue(resolution.value))),
                        resolution.live ? ModernUiRenderer.SUCCESS : ModernUiRenderer.ACCENT, 1));
            } else if (resolution.state == ResolutionState.DYNAMIC) {
                lines.add(new Line(tr("gui.modern.path.expr.fmt.runtime_value", tr(resolution.message)), ModernUiRenderer.MUTED_TEXT, 1));
            } else if (resolution.state == ResolutionState.CYCLE) {
                lines.add(new Line(tr("gui.modern.path.expr.fmt.cycle", tr(resolution.message)), ModernUiRenderer.WARNING, 1));
            } else {
                Object builtin = LegacyActionRuntime.getRuntimeValue(reference, runtime, currentPlayer(), sequence,
                        stepIndex, actionIndex);
                if (builtin != null || isBuiltin(reference)) {
                    lines.add(new Line(tr("gui.modern.path.expr.fmt.builtin_value", tr(renderValue(builtin))), ModernUiRenderer.SUBTLE_TEXT, 1));
                } else {
                    lines.add(new Line("gui.modern.path.expr.u014", ModernUiRenderer.WARNING, 1));
                }
            }
            for (String trace : resolution.trace) {
                lines.add(new Line(trace, resolution.state == ResolutionState.CYCLE
                        ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT, 2));
            }
        }
    }

    private static void appendTemplate(List<Line> lines, ExpressionTemplateCard card) {
        if (card == null) {
            return;
        }
        lines.add(new Line("", ModernUiRenderer.MUTED_TEXT, 0));
        lines.add(new Line(tr("gui.modern.path.expr.fmt.template", card.name), ModernUiRenderer.TEXT, 0));
        if (card.description != null && !card.description.trim().isEmpty()) {
            lines.add(new Line(card.description, ModernUiRenderer.MUTED_TEXT, 1));
        }
        if (card.format != null && !card.format.trim().isEmpty()) {
            lines.add(new Line(tr("gui.modern.path.expr.fmt.format", card.format), ModernUiRenderer.SUBTLE_TEXT, 1));
        }
        if (card.outputExample != null && !card.outputExample.trim().isEmpty()) {
            lines.add(new Line(tr("gui.modern.path.expr.fmt.example", card.outputExample), ModernUiRenderer.SUBTLE_TEXT, 1));
        }
    }

    private static List<Assignment> collectAssignments(Collection<PathSequence> sequences, PathSequence currentSequence,
            int untilStep, int untilAction) {
        List<Assignment> result = new ArrayList<Assignment>();
        if (sequences == null) {
            return result;
        }
        for (PathSequence sequence : sequences) {
            if (sequence == null || sequence.getSteps() == null) {
                continue;
            }
            boolean current = sameSequence(sequence, currentSequence);
            for (int stepIndex = 0; stepIndex < sequence.getSteps().size(); stepIndex++) {
                PathStep step = sequence.getSteps().get(stepIndex);
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (int actionIndex = 0; actionIndex < step.getActions().size(); actionIndex++) {
                    ActionData action = step.getActions().get(actionIndex);
                    if (action == null || action.params == null) {
                        continue;
                    }
                    String paramKey = ActionVariableRegistry.resolveVariableParamKey(action);
                    if (paramKey == null || !action.params.has(paramKey) || !action.params.get(paramKey).isJsonPrimitive()) {
                        continue;
                    }
                    String variableName = action.params.get(paramKey).getAsString().trim();
                    if (variableName.isEmpty()) {
                        continue;
                    }
                    boolean beforeCursor = !current || untilStep < 0 || stepIndex < untilStep
                            || stepIndex == untilStep && (untilAction < 0 || actionIndex < untilAction);
                    String type = safe(action.type).trim().toLowerCase(Locale.ROOT);
                    for (String produced : ActionVariableRegistry.collectProducedVariableNames(variableName, type)) {
                        String canonical = normalizeReference(produced);
                        if (!current && !canonical.startsWith("global.")) {
                            continue;
                        }
                        result.add(new Assignment(sequence, stepIndex, actionIndex, type, canonical,
                                action.params, beforeCursor));
                    }
                }
            }
        }
        return result;
    }

    private static Map<String, Object> liveValues(PathSequence sequence) {
        Map<String, Object> live = new LinkedHashMap<String, Object>();
        Map<String, Object> snapshot = PathSequenceEventListener.instance.getLiveVariableSnapshot();
        boolean tracking = PathSequenceEventListener.instance.isTrackingSequence(sequence == null ? "" : sequence.getName());
        if (snapshot != null) {
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                if (entry == null || entry.getKey() == null) {
                    continue;
                }
                String key = normalizeReference(entry.getKey());
                if (tracking || key.startsWith("global.")) {
                    live.put(key, entry.getValue());
                }
            }
        }
        for (Map.Entry<String, Object> entry : ScopedRuntimeVariables.getGlobalScopeSnapshot().entrySet()) {
            if (entry != null && entry.getKey() != null) {
                live.put("global." + entry.getKey(), entry.getValue());
            }
        }
        return live;
    }

    private static Map<String, Object> nestedRuntime(Map<String, Object> flat) {
        Map<String, Object> runtime = new LinkedHashMap<String, Object>();
        Map<String, Object> global = new LinkedHashMap<String, Object>();
        Map<String, Object> sequence = new LinkedHashMap<String, Object>();
        Map<String, Object> local = new LinkedHashMap<String, Object>();
        Map<String, Object> temp = new LinkedHashMap<String, Object>();
        runtime.put("global", global);
        runtime.put("sequence", sequence);
        runtime.put("seq", sequence);
        runtime.put("local", local);
        runtime.put("temp", temp);
        runtime.put("tmp", temp);
        if (flat == null) {
            return runtime;
        }
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry == null ? "" : normalizeReference(entry.getKey());
            if (key.isEmpty()) {
                continue;
            }
            Object value = entry.getValue();
            runtime.put(key, value);
            String scope = ActionVariableRegistry.extractScopeKey(key);
            String base = ActionVariableRegistry.extractBaseName(key);
            if ("global".equals(scope)) {
                global.put(base, value);
            } else if ("local".equals(scope)) {
                local.put(base, value);
            } else if ("temp".equals(scope)) {
                temp.put(base, value);
            } else {
                sequence.put(base, value);
                runtime.put(base, value);
            }
        }
        return runtime;
    }

    static Set<String> extractReferences(String expression) {
        LinkedHashSet<String> refs = new LinkedHashSet<String>();
        if (expression == null || expression.isEmpty()) {
            return refs;
        }
        Matcher templates = TEMPLATE_PATTERN.matcher(expression);
        while (templates.find()) {
            refs.add(templates.group(1));
        }
        int i = 0;
        while (i < expression.length()) {
            char c = expression.charAt(i);
            if (c == '\'' || c == '"') {
                char quote = c;
                i++;
                while (i < expression.length()) {
                    char current = expression.charAt(i++);
                    if (current == '\\' && i < expression.length()) {
                        i++;
                    } else if (current == quote) {
                        break;
                    }
                }
                continue;
            }
            if (c == '#' || c == '/' && i + 1 < expression.length() && expression.charAt(i + 1) == '/') {
                while (i < expression.length() && expression.charAt(i) != '\n' && expression.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            boolean dollar = c == '$';
            if (dollar || Character.isLetter(c) || c == '_') {
                int start = dollar ? i + 1 : i;
                int end = scanReferencePath(expression, start);
                if (end > start) {
                    String token = expression.substring(start, end);
                    int next = end;
                    while (next < expression.length() && Character.isWhitespace(expression.charAt(next))) {
                        next++;
                    }
                    boolean function = !dollar && next < expression.length() && expression.charAt(next) == '(';
                    if (function && isCapturedIdFunction(token)) {
                        int close = findClosingParenthesis(expression, next);
                        if (close > next) {
                            String argument = expression.substring(next + 1, close).trim();
                            if (argument.startsWith("$")) {
                                int argumentEnd = scanReferencePath(argument, 1);
                                if (argumentEnd > 1) {
                                    refs.add(argument.substring(1, argumentEnd));
                                }
                            }
                            i = close + 1;
                            continue;
                        }
                    }
                    if (!function && (dollar || isReferenceToken(token))) {
                        refs.add(token);
                    }
                    i = end;
                    continue;
                }
            }
            i++;
        }
        return refs;
    }

    private static int scanReferencePath(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '.') {
                i++;
                continue;
            }
            if (c == '[') {
                int end = text.indexOf(']', i + 1);
                if (end > i + 1 && text.substring(i + 1, end).trim().matches("[-+]?\\d+")) {
                    i = end + 1;
                    continue;
                }
            }
            break;
        }
        return i;
    }

    private static boolean isCapturedIdFunction(String name) {
        String normalized = safe(name).trim().toLowerCase(Locale.ROOT);
        return "cap".equals(normalized) || "captured".equals(normalized) || "capturedid".equals(normalized)
                || "capdec".equals(normalized) || "captureddec".equals(normalized)
                || "capturediddec".equals(normalized);
    }

    private static int findClosingParenthesis(String text, int open) {
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isReferenceToken(String token) {
        String lower = safe(token).toLowerCase(Locale.ROOT);
        if (lower.isEmpty() || KEYWORDS.contains(lower)) {
            return false;
        }
        char first = token.charAt(0);
        return Character.isLetter(first) || first == '_';
    }

    private static String normalizeReference(String raw) {
        String text = safe(raw).trim();
        if (text.startsWith("$")) {
            text = text.substring(1).trim();
        }
        int colon = text.indexOf(':');
        if (colon > 0 && isScope(text.substring(0, colon))) {
            text = text.substring(0, colon) + "." + text.substring(colon + 1);
        }
        if (isBuiltin(text)) {
            return text;
        }
        return ActionVariableRegistry.buildCanonicalVariableName(ActionVariableRegistry.extractScopeKey(text),
                ActionVariableRegistry.extractBaseName(text));
    }

    private static boolean isScope(String value) {
        String scope = safe(value).trim().toLowerCase(Locale.ROOT);
        return "global".equals(scope) || "sequence".equals(scope) || "seq".equals(scope)
                || "local".equals(scope) || "temp".equals(scope) || "tmp".equals(scope);
    }

    private static boolean isBuiltin(String value) {
        String key = safe(value).toLowerCase(Locale.ROOT);
        return key.startsWith("player.") || key.startsWith("gui.") || key.startsWith("player_")
                || "sequence_name".equals(key) || "step_index".equals(key)
                || "action_index".equals(key) || "gui_title".equals(key) || "current_screen".equals(key);
    }

    private static JsonObject currentActionParams(PathSequence sequence, int stepIndex, int actionIndex) {
        if (sequence == null || stepIndex < 0 || actionIndex < 0 || stepIndex >= sequence.getSteps().size()) {
            return new JsonObject();
        }
        PathStep step = sequence.getSteps().get(stepIndex);
        if (step == null || actionIndex >= step.getActions().size()) {
            return new JsonObject();
        }
        ActionData action = step.getActions().get(actionIndex);
        return action == null || action.params == null ? new JsonObject() : action.params;
    }

    private static Map<String, Object> builtinValues(PathSequence sequence, int stepIndex, int actionIndex) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        Map<String, Object> builtins = LegacyActionRuntime.getBuiltinValues(currentPlayer(), sequence, stepIndex, actionIndex);
        result.putAll(builtins);
        flattenBuiltinMap(result, "player", builtins.get("player"));
        flattenBuiltinMap(result, "gui", builtins.get("gui"));
        return result;
    }

    private static void flattenBuiltinMap(Map<String, Object> target, String prefix, Object value) {
        if (!(value instanceof Map)) {
            return;
        }
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (entry != null && entry.getKey() != null) {
                target.put(prefix + "." + entry.getKey(), entry.getValue());
            }
        }
    }

    private static EntityPlayerSP currentPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc == null ? null : mc.player;
    }

    private static boolean sameSequence(PathSequence left, PathSequence right) {
        return left == right || left != null && right != null
                && safe(left.getName()).equalsIgnoreCase(safe(right.getName()));
    }

    private static String variableDetail(String name) {
        if (isBuiltin(name)) {
            return "gui.modern.path.expr.u004";
        }
        return ActionVariableRegistry.scopeKeyToDisplay(ActionVariableRegistry.extractScopeKey(name));
    }

    private static String completionDescription(Resolution resolution) {
        if (resolution == null) {
            return "gui.modern.path.expr.u015";
        }
        StringBuilder text = new StringBuilder();
        if (resolution.state == ResolutionState.AVAILABLE) {
            text.append(tr("gui.modern.path.expr.fmt.current_value", tr(renderValue(resolution.value))));
        } else if (resolution.state == ResolutionState.CYCLE) {
            text.append(tr("gui.modern.path.expr.fmt.cycle_colon", tr(resolution.message)));
        } else if (resolution.state == ResolutionState.DYNAMIC) {
            text.append(tr("gui.modern.path.expr.fmt.runtime_get", tr(resolution.message)));
        } else {
            text.append("gui.modern.path.expr.u016");
        }
        for (String trace : resolution.trace) {
            text.append('\n').append(trace);
        }
        return text.toString();
    }

    private static String renderValue(Object value) {
        if (value == null) {
            return "gui.modern.path.expr.u017";
        }
        String rendered = LegacyActionRuntime.stringifyValue(value);
        return rendered.isEmpty() ? "gui.modern.path.expr.u018" : rendered;
    }

    private static String typeName(Object value) {
        if (value == null) return "gui.modern.path.expr.u019";
        if (value instanceof Boolean) return "gui.modern.path.expr.u020";
        if (value instanceof Number) return "gui.modern.path.expr.u021";
        if (value instanceof List) return "gui.modern.path.expr.u022";
        if (value instanceof Map) return "gui.modern.path.expr.u023";
        return "gui.modern.path.expr.u024";
    }

    private static String safeMessage(Exception ex) {
        String message = ex == null ? "gui.modern.path.expr.u025" : safe(ex.getMessage()).trim();
        if (message.isEmpty() && ex != null) {
            message = ex.getClass().getSimpleName();
        }
        return message.length() > 180 ? message.substring(0, 177) + "..." : message;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Set<String> keywordSet() {
        Set<String> keywords = new LinkedHashSet<String>();
        Collections.addAll(keywords, "true", "false", "null", "nil");
        return keywords;
    }

    private enum ResolutionState {
        AVAILABLE, DYNAMIC, MISSING, CYCLE
    }

    private static final class Resolution {
        final ResolutionState state;
        final Object value;
        final boolean live;
        final String message;
        final List<String> trace;

        Resolution(ResolutionState state, Object value, boolean live, String message, List<String> trace) {
            this.state = state;
            this.value = value;
            this.live = live;
            this.message = safe(message);
            this.trace = trace == null ? Collections.<String>emptyList() : trace;
        }
    }

    private static final class Assignment {
        final PathSequence sequence;
        final int stepIndex;
        final int actionIndex;
        final String type;
        final String canonical;
        final JsonObject params;
        final boolean beforeCursor;

        Assignment(PathSequence sequence, int stepIndex, int actionIndex, String type, String canonical,
                JsonObject params, boolean beforeCursor) {
            this.sequence = sequence;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.type = type;
            this.canonical = canonical;
            this.params = params;
            this.beforeCursor = beforeCursor;
        }

        String location() {
            return tr("gui.modern.path.expr.fmt.location", safe(sequence == null ? "" : sequence.getName()),
                    String.valueOf(stepIndex + 1), String.valueOf(actionIndex + 1));
        }
    }

    private static final class EvaluationContext {
        final Map<String, List<Assignment>> assignments = new LinkedHashMap<String, List<Assignment>>();
        final Map<String, Object> values = new LinkedHashMap<String, Object>();
        final Map<String, Resolution> cache = new LinkedHashMap<String, Resolution>();
        final LinkedHashSet<String> resolving = new LinkedHashSet<String>();
        final PathSequence currentSequence;
        final int stepIndex;
        final int actionIndex;

        EvaluationContext(List<Assignment> source, Map<String, Object> live, PathSequence currentSequence,
                int stepIndex, int actionIndex) {
            this.currentSequence = currentSequence;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            if (live != null) {
                values.putAll(live);
                for (Map.Entry<String, Object> entry : live.entrySet()) {
                    cache.put(entry.getKey(), new Resolution(ResolutionState.AVAILABLE, entry.getValue(), true,
                            "gui.modern.path.expr.u026", Collections.singletonList("gui.modern.path.expr.u027")));
                }
            }
            for (Assignment assignment : source) {
                List<Assignment> list = assignments.get(assignment.canonical);
                if (list == null) {
                    list = new ArrayList<Assignment>();
                    assignments.put(assignment.canonical, list);
                }
                list.add(assignment);
            }
        }

        Resolution resolve(String rawName, int depth) {
            String name = normalizeReference(rawName);
            if (name.isEmpty()) {
                return missing("gui.modern.path.expr.u028");
            }
            Resolution known = cache.get(name);
            if (known != null) {
                return known;
            }
            if (isBuiltin(name)) {
                Object value = LegacyActionRuntime.getRuntimeValue(name, nestedRuntime(values), currentPlayer(),
                        currentSequence, stepIndex, actionIndex);
                Resolution result = value != null
                        ? available(value, true, "gui.modern.path.expr.u029")
                        : dynamic("gui.modern.path.expr.u003", "gui.modern.path.expr.u030");
                cache.put(name, result);
                return result;
            }
            String parentName = findParentVariable(name);
            if (!parentName.isEmpty()) {
                Resolution parent = resolve(parentName, depth + 1);
                if (parent.state == ResolutionState.AVAILABLE) {
                    Object value = LegacyActionRuntime.getRuntimeValue(name, nestedRuntime(values), currentPlayer(),
                            currentSequence, stepIndex, actionIndex);
                    Resolution result = new Resolution(ResolutionState.AVAILABLE, value, parent.live,
                            "gui.modern.path.expr.u031", Collections.singletonList(
                                    tr("gui.modern.path.expr.fmt.parent_field", parentName)));
                    cache.put(name, result);
                    return result;
                }
                if (parent.state == ResolutionState.CYCLE) {
                    cache.put(name, parent);
                    return parent;
                }
            }
            if (depth > MAX_DEPENDENCY_DEPTH) {
                Resolution result = cycle(tr("gui.modern.path.expr.fmt.depth", String.valueOf(MAX_DEPENDENCY_DEPTH)));
                cache.put(name, result);
                return result;
            }
            if (!resolving.add(name)) {
                StringBuilder chain = new StringBuilder();
                for (String item : resolving) {
                    if (chain.length() > 0) chain.append(" -> ");
                    chain.append(item);
                }
                chain.append(" -> ").append(name);
                return cycle(chain.toString());
            }
            try {
                Assignment assignment = chooseAssignment(assignments.get(name));
                if (assignment == null) {
                    Resolution result = missing("gui.modern.path.expr.u032");
                    cache.put(name, result);
                    return result;
                }
                String sourceLine = tr("gui.modern.path.expr.fmt.source_loc", assignment.location(), assignment.type);
                if (!"set_var".equals(assignment.type)) {
                    Resolution result = dynamic("gui.modern.path.expr.u033", sourceLine);
                    cache.put(name, result);
                    return result;
                }
                List<String> trace = new ArrayList<String>();
                trace.add(assignment.beforeCursor ? sourceLine : tr("gui.modern.path.expr.fmt.after_cursor", sourceLine));
                String expression = assignment.params.has("expression") && assignment.params.get("expression").isJsonPrimitive()
                        ? assignment.params.get("expression").getAsString() : "";
                String fromVar = assignment.params.has("fromVar") && assignment.params.get("fromVar").isJsonPrimitive()
                        ? assignment.params.get("fromVar").getAsString() : "";
                Set<String> dependencies = extractReferences(expression.isEmpty() ? "$" + fromVar : expression);
                boolean unresolved = false;
                for (String dependency : dependencies) {
                    String normalizedDependency = normalizeReference(dependency);
                    if (normalizedDependency.equals(name)) {
                        Resolution cycle = cycle(name + " -> " + name);
                        cache.put(name, cycle);
                        return cycle;
                    }
                    Resolution dependencyValue = resolve(normalizedDependency, depth + 1);
                    trace.add(tr("gui.modern.path.expr.fmt.dep", normalizedDependency,
                            dependencyValue.state == ResolutionState.AVAILABLE
                                    ? tr(renderValue(dependencyValue.value)) : tr(dependencyValue.message)));
                    if (dependencyValue.state == ResolutionState.CYCLE) {
                        Resolution cycle = new Resolution(ResolutionState.CYCLE, null, false,
                                dependencyValue.message, trace);
                        cache.put(name, cycle);
                        return cycle;
                    }
                    unresolved |= dependencyValue.state != ResolutionState.AVAILABLE;
                }
                if (unresolved) {
                    Resolution result = new Resolution(ResolutionState.DYNAMIC, null, false,
                            "gui.modern.path.expr.u034", trace);
                    cache.put(name, result);
                    return result;
                }
                try {
                    Object value = LegacyActionRuntime.resolveAssignedValue(assignment.params, nestedRuntime(values),
                            currentPlayer(), assignment.sequence, assignment.stepIndex, assignment.actionIndex);
                    values.put(name, value);
                    Resolution result = new Resolution(ResolutionState.AVAILABLE, value, false,
                            "gui.modern.path.expr.u035", trace);
                    cache.put(name, result);
                    return result;
                } catch (Exception ex) {
                    trace.add(tr("gui.modern.path.expr.fmt.eval_fail", safeMessage(ex)));
                    Resolution result = new Resolution(ResolutionState.DYNAMIC, null, false,
                            safeMessage(ex), trace);
                    cache.put(name, result);
                    return result;
                }
            } finally {
                resolving.remove(name);
            }
        }

        private Assignment chooseAssignment(List<Assignment> options) {
            if (options == null || options.isEmpty()) {
                return null;
            }
            Assignment fallback = null;
            for (Assignment assignment : options) {
                fallback = assignment;
                if (assignment.beforeCursor) {
                    fallback = assignment;
                }
            }
            for (int i = options.size() - 1; i >= 0; i--) {
                if (options.get(i).beforeCursor) {
                    return options.get(i);
                }
            }
            return fallback;
        }

        private String findParentVariable(String name) {
            String best = "";
            for (String candidate : assignments.keySet()) {
                if (isPathParent(candidate, name) && candidate.length() > best.length()) {
                    best = candidate;
                }
            }
            for (String candidate : cache.keySet()) {
                if (isPathParent(candidate, name) && candidate.length() > best.length()) {
                    best = candidate;
                }
            }
            return best;
        }

        private boolean isPathParent(String candidate, String name) {
            if (candidate == null || candidate.isEmpty() || name == null || name.length() <= candidate.length()
                    || !name.regionMatches(true, 0, candidate, 0, candidate.length())) {
                return false;
            }
            char separator = name.charAt(candidate.length());
            return separator == '.' || separator == '[';
        }
    }

    private static Resolution available(Object value, boolean live, String trace) {
        return new Resolution(ResolutionState.AVAILABLE, value, live, "",
                Collections.singletonList(trace));
    }

    private static Resolution dynamic(String message, String trace) {
        return new Resolution(ResolutionState.DYNAMIC, null, false, message,
                Collections.singletonList(trace));
    }

    private static Resolution missing(String message) {
        return new Resolution(ResolutionState.MISSING, null, false, message, Collections.<String>emptyList());
    }

    private static Resolution cycle(String message) {
        return new Resolution(ResolutionState.CYCLE, null, false, message,
                Collections.singletonList(tr("gui.modern.path.expr.fmt.chain", message)));
    }
    private static String tr(String key) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key);
    }

    private static String tr(String key, Object... args) {
        return com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(key, args);
    }

}
