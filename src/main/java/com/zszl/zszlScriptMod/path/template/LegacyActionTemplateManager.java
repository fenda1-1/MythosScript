package com.zszl.zszlScriptMod.path.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LegacyActionTemplateManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<TemplateDefinition> TEMPLATES = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized = false;

    public static final class TemplateEditModel {
        public String name = "";
        public String sequenceName = "";
        public String defaultsText = "";
        public String note = "";
    }

    public static final class ResolvedTemplateCall {
        private final String templateName;
        private final String sequenceName;
        private final Map<String, Object> variables;

        public ResolvedTemplateCall(String templateName, String sequenceName, Map<String, Object> variables) {
            this.templateName = templateName == null ? "" : templateName;
            this.sequenceName = sequenceName == null ? "" : sequenceName;
            this.variables = variables == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<>(variables);
        }

        public String getTemplateName() {
            return templateName;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public Map<String, Object> getVariables() {
            return new LinkedHashMap<>(variables);
        }
    }

    private static final class TemplateRoot {
        private List<TemplateDefinition> templates = new ArrayList<>();
    }

    private static final class TemplateSharePayload {
        private int version = 1;
        private TemplateDefinition template;
    }

    private static final class TemplateDefinition {
        private String name = "";
        private String sequenceName = "";
        private String defaultsText = "";
        private String note = "";
    }

    private LegacyActionTemplateManager() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        reloadTemplates();
        initialized = true;
    }

    public static synchronized void reloadTemplates() {
        TEMPLATES.clear();
        Path path = getConfigPath();
        ensureConfigExists(path);
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            TemplateRoot root = GSON.fromJson(reader, TemplateRoot.class);
            if (root == null || root.templates == null) {
                return;
            }
            for (TemplateDefinition definition : root.templates) {
                if (definition == null || isBlank(definition.name) || isBlank(definition.sequenceName)) {
                    continue;
                }
                TEMPLATES.add(definition);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTemplate] 加载模板失败", e);
        }
    }

    public static synchronized List<TemplateEditModel> getTemplateModels() {
        initialize();
        List<TemplateEditModel> models = new ArrayList<>();
        for (TemplateDefinition definition : TEMPLATES) {
            if (definition == null) {
                continue;
            }
            TemplateEditModel model = new TemplateEditModel();
            model.name = safe(definition.name);
            model.sequenceName = safe(definition.sequenceName);
            model.defaultsText = safe(definition.defaultsText);
            model.note = safe(definition.note);
            models.add(model);
        }
        return models;
    }

    public static synchronized void saveTemplateModels(List<TemplateEditModel> models) {
        Path path = getConfigPath();
        ensureConfigExists(path);
        TemplateRoot root = new TemplateRoot();
        root.templates = new ArrayList<>();
        if (models != null) {
            for (TemplateEditModel model : models) {
                if (model == null || isBlank(model.name) || isBlank(model.sequenceName)) {
                    continue;
                }
                TemplateDefinition definition = new TemplateDefinition();
                definition.name = safe(model.name).trim();
                definition.sequenceName = safe(model.sequenceName).trim();
                definition.defaultsText = safe(model.defaultsText);
                definition.note = safe(model.note);
                root.templates.add(definition);
            }
        }
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTemplate] 保存模板失败", e);
        }
        reloadTemplates();
    }

    public static synchronized List<String> getTemplateNames() {
        initialize();
        List<String> names = new ArrayList<>();
        for (TemplateDefinition definition : TEMPLATES) {
            if (definition != null && !isBlank(definition.name)) {
                names.add(definition.name.trim());
            }
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public static synchronized TemplateEditModel getTemplate(String templateName) {
        initialize();
        String normalized = normalize(templateName);
        for (TemplateDefinition definition : TEMPLATES) {
            if (definition != null && normalized.equals(normalize(definition.name))) {
                TemplateEditModel model = new TemplateEditModel();
                model.name = safe(definition.name);
                model.sequenceName = safe(definition.sequenceName);
                model.defaultsText = safe(definition.defaultsText);
                model.note = safe(definition.note);
                return model;
            }
        }
        return null;
    }

    public static synchronized String resolveTemplateTargetSequence(String templateName) {
        TemplateEditModel model = getTemplate(templateName);
        return model == null ? "" : safe(model.sequenceName).trim();
    }

    public static synchronized ResolvedTemplateCall resolveCall(String templateName, String overridesText) {
        initialize();
        TemplateEditModel template = getTemplate(templateName);
        if (template == null || isBlank(template.sequenceName)) {
            return null;
        }
        if (PathSequenceManager.getSequence(template.sequenceName) == null) {
            return null;
        }

        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.putAll(parseKeyValueText(template.defaultsText));
        values.putAll(parseKeyValueText(overridesText));
        values.put("template_name", template.name);
        values.put("template_sequence", template.sequenceName);
        return new ResolvedTemplateCall(template.name, template.sequenceName, values);
    }

    public static synchronized String exportTemplate(String templateName) {
        TemplateEditModel model = getTemplate(templateName);
        if (model == null) {
            return "";
        }
        TemplateSharePayload payload = new TemplateSharePayload();
        payload.template = new TemplateDefinition();
        payload.template.name = safe(model.name);
        payload.template.sequenceName = safe(model.sequenceName);
        payload.template.defaultsText = safe(model.defaultsText);
        payload.template.note = safe(model.note);
        return GSON.toJson(payload);
    }

    public static synchronized String importTemplate(String rawText) {
        if (isBlank(rawText)) {
            return "";
        }
        try {
            TemplateSharePayload payload = GSON.fromJson(rawText, TemplateSharePayload.class);
            if (payload == null || payload.template == null || isBlank(payload.template.name)
                    || isBlank(payload.template.sequenceName)) {
                return "";
            }
            TemplateDefinition imported = payload.template;
            boolean replaced = false;
            for (int i = 0; i < TEMPLATES.size(); i++) {
                TemplateDefinition existing = TEMPLATES.get(i);
                if (existing != null && normalize(existing.name).equals(normalize(imported.name))) {
                    TEMPLATES.set(i, imported);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                TEMPLATES.add(imported);
            }
            saveTemplateModels(getTemplateModels());
            return imported.name;
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTemplate] 导入模板失败", e);
            return "";
        }
    }

    public static Map<String, Object> parseKeyValueText(String rawText) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        if (rawText == null || rawText.trim().isEmpty()) {
            return values;
        }
        for (String line : rawText.split("\\r?\\n|;|；")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                separator = trimmed.indexOf(':');
            }
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (key.isEmpty()) {
                continue;
            }
            values.put(key, parseLiteralValue(value));
        }
        return values;
    }

    private static Object parseLiteralValue(String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        if (value.matches("[-+]?\\d+")) {
            try {
                long parsed = Long.parseLong(value);
                if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE) {
                    return (int) parsed;
                }
                return parsed;
            } catch (Exception ignored) {
            }
        }
        if (value.matches("[-+]?\\d+\\.\\d+")) {
            try {
                return Double.parseDouble(value);
            } catch (Exception ignored) {
            }
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            List<Object> list = new ArrayList<>();
            String inner = value.substring(1, value.length() - 1).trim();
            if (!inner.isEmpty()) {
                for (String part : inner.split(",")) {
                    list.add(parseLiteralValue(part));
                }
            }
            return list;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static Path getConfigPath() {
        return ProfileManager.getCurrentProfileDir().resolve("legacy_action_templates.json");
    }

    private static void ensureConfigExists(Path path) {
        if (path == null || Files.exists(path)) {
            return;
        }
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                writer.write("{\"templates\":[]}");
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("[LegacyTemplate] 创建模板文件失败", e);
        }
    }

    private static String normalize(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
