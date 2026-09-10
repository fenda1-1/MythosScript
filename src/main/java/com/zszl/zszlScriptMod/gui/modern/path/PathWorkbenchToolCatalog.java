package com.zszl.zszlScriptMod.gui.modern.path;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.zszl.zszlScriptMod.config.ModConfig;
import com.zszl.zszlScriptMod.zszlScriptMod;

/**
 * Pinned/overflow configuration for the modern path workbench function strips.
 */
public final class PathWorkbenchToolCatalog {
    public enum Zone {
        NAVIGATION("gui.modern.path.tool.u001"),
        STEP("gui.modern.path.tool.u002"),
        ACTION("gui.modern.path.tool.u003"),
        HEADER("gui.modern.path.tool.u004"),
        RECORDING_STEP("gui.modern.path.tool.u002"),
        RECORDING_ACTION("gui.modern.path.tool.u003"),
        RECORDING_CONTROL("gui.modern.path.record.controls");

        public final String title;

        Zone(String title) {
            this.title = title;
        }
    }

    public enum ToolId {
        NAV_ADD_CATEGORY(Zone.NAVIGATION, "add_category", "gui.modern.path.tool.u005", "", true, false, false),
        NAV_ADD_SUBCATEGORY(Zone.NAVIGATION, "add_subcategory", "gui.modern.path.tool.u006", "", true, false, false),
        NAV_CATEGORY_MANAGE(Zone.NAVIGATION, "category_manage", "gui.modern.path.tool.u007", "", true, false, false),
        NAV_ADD_SEQUENCE(Zone.NAVIGATION, "add_sequence", "gui.modern.path.tool.u008", "", true, false, true),
        NAV_COPY_SEQUENCE(Zone.NAVIGATION, "copy_sequence", "gui.modern.path.tool.u009", "Ctrl+C", true, false, false),
        NAV_RENAME_SEQUENCE(Zone.NAVIGATION, "rename_sequence", "gui.modern.path.tool.u010", "", true, false, false),
        NAV_MOVE_SEQUENCE(Zone.NAVIGATION, "move_sequence", "gui.modern.path.tool.u011", "", true, false, false),
        NAV_DELETE_SEQUENCE(Zone.NAVIGATION, "delete_sequence", "gui.modern.path.tool.u012", "Delete", true, true, false),
        NAV_SEQUENCE_UP(Zone.NAVIGATION, "sequence_up", "gui.modern.path.tool.u013", "Ctrl+↑", true, false, false),
        NAV_SEQUENCE_DOWN(Zone.NAVIGATION, "sequence_down", "gui.modern.path.tool.u014", "Ctrl+↓", true, false, false),
        NAV_RUN_FROM_STEP(Zone.NAVIGATION, "run_from_step", "gui.modern.path.tool.u015", "", true, false, false),
        NAV_RELOAD(Zone.NAVIGATION, "reload", "gui.modern.path.tool.u016", "", true, false, false),

        STEP_ADD(Zone.STEP, "add", "gui.modern.path.tool.u008", "", true, false, true),
        STEP_COPY(Zone.STEP, "copy", "gui.modern.path.tool.u009", "Ctrl+C", true, false, false),
        STEP_PASTE(Zone.STEP, "paste", "gui.modern.path.tool.u017", "Ctrl+V", true, false, false),
        STEP_DELETE(Zone.STEP, "delete", "gui.modern.path.tool.u018", "Delete", true, true, false),
        STEP_UP(Zone.STEP, "up", "gui.modern.path.tool.u019", "Ctrl+↑", true, false, false),
        STEP_DOWN(Zone.STEP, "down", "gui.modern.path.tool.u020", "Ctrl+↓", true, false, false),
        STEP_GET_COORDS(Zone.STEP, "get_coords", "gui.modern.path.tool.u021", "", true, false, false),
        STEP_CLEAR_COORDS(Zone.STEP, "clear_coords", "gui.modern.path.tool.u022", "", true, false, false),
        STEP_SETTINGS(Zone.STEP, "settings", "gui.modern.path.tool.u023", "", true, false, false),

        ACTION_ADD(Zone.ACTION, "add", "gui.modern.path.tool.u008", "", true, false, true),
        ACTION_EDIT(Zone.ACTION, "edit", "gui.modern.path.tool.u024", "Enter", true, false, false),
        ACTION_COPY(Zone.ACTION, "copy", "gui.modern.path.tool.u009", "Ctrl+C", true, false, false),
        ACTION_PASTE(Zone.ACTION, "paste", "gui.modern.path.tool.u017", "Ctrl+V", true, false, false),
        ACTION_DELETE(Zone.ACTION, "delete", "gui.modern.path.tool.u018", "Delete", true, true, false),
        ACTION_UP(Zone.ACTION, "up", "gui.modern.path.tool.u019", "Ctrl+↑", true, false, false),
        ACTION_DOWN(Zone.ACTION, "down", "gui.modern.path.tool.u020", "Ctrl+↓", true, false, false),
        ACTION_INSERT_TEMPLATE(Zone.ACTION, "insert_template", "gui.modern.path.tool.u025", "", true, false, false),
        ACTION_BUILTIN_DELAY(Zone.ACTION, "builtin_delay", "gui.modern.path.tool.u047",
                "gui.modern.path.tool.u047", "gui.modern.path.tool.u048", "", true, false, false),

        RECORDING_STEP_ADD(Zone.RECORDING_STEP, "add", "gui.modern.path.record.insert_step", "", true, false, true),
        RECORDING_STEP_DELETE(Zone.RECORDING_STEP, "delete", "gui.modern.path.record.delete_step", "Delete", true, true, false),
        RECORDING_STEP_GET_COORDS(Zone.RECORDING_STEP, "get_coords", "gui.modern.path.tool.u021", "", true, false, false),
        RECORDING_STEP_CLEAR_COORDS(Zone.RECORDING_STEP, "clear_coords", "gui.modern.path.tool.u022", "", true, false, false),
        RECORDING_STEP_UP(Zone.RECORDING_STEP, "up", "gui.modern.path.tool.u019", "Ctrl+↑", true, false, false),
        RECORDING_STEP_DOWN(Zone.RECORDING_STEP, "down", "gui.modern.path.tool.u020", "Ctrl+↓", true, false, false),
        RECORDING_STEP_RUN(Zone.RECORDING_STEP, "run", "gui.modern.path.record.run_step", "", true, false, false),

        RECORDING_ACTION_ADD(Zone.RECORDING_ACTION, "add", "gui.modern.path.record.insert_action", "", true, false, true),
        RECORDING_ACTION_REPLACE(Zone.RECORDING_ACTION, "replace", "gui.modern.path.record.replace_action", "", true, false, false),
        RECORDING_ACTION_DELETE(Zone.RECORDING_ACTION, "delete", "gui.modern.path.record.delete_action", "Delete", true, true, false),
        RECORDING_ACTION_UP(Zone.RECORDING_ACTION, "up", "gui.modern.path.tool.u019", "Ctrl+↑", true, false, false),
        RECORDING_ACTION_DOWN(Zone.RECORDING_ACTION, "down", "gui.modern.path.tool.u020", "Ctrl+↓", true, false, false),

        RECORDING_START(Zone.RECORDING_CONTROL, "start", "gui.modern.path.wb.u239", "N", true, false, false),
        RECORDING_PAUSE(Zone.RECORDING_CONTROL, "pause", "gui.modern.path.wb.u241", "M", true, false, false),
        RECORDING_FINISH(Zone.RECORDING_CONTROL, "finish", "gui.modern.path.wb.u242", "", true, false, false),
        RECORDING_SAVE(Zone.RECORDING_CONTROL, "save", "gui.modern.path.wb.u091", "Ctrl+S", true, false, true),
        RECORDING_RUN(Zone.RECORDING_CONTROL, "run", "gui.modern.path.record.run", "", true, false, true),
        RECORDING_RUN_PAUSE(Zone.RECORDING_CONTROL, "run_pause", "gui.modern.path.record.pause_run", "", true, false, false),
        RECORDING_RUN_STOP(Zone.RECORDING_CONTROL, "run_stop", "gui.modern.path.record.stop_run", "", true, false, false),
        RECORDING_UNDO(Zone.RECORDING_CONTROL, "undo", "gui.modern.path.record.undo", "Ctrl+Z", true, false, false),
        RECORDING_REDO(Zone.RECORDING_CONTROL, "redo", "gui.modern.path.record.redo", "Ctrl+Y", true, false, false),
        RECORDING_CLEAR(Zone.RECORDING_CONTROL, "clear", "gui.modern.path.record.clear", "", true, true, false),

        HEADER_VALIDATE(Zone.HEADER, "validate", "gui.modern.path.tool.u026", "gui.modern.path.tool.u027",
                "gui.modern.path.tool.u028", "", true, false, false),
        HEADER_RECORD(Zone.HEADER, "record", "gui.modern.path.tool.u029", "gui.modern.path.tool.u030",
                "gui.modern.path.tool.u031", "", true, false, false),
        HEADER_LOG(Zone.HEADER, "execution_log", "gui.modern.path.tool.u032", "gui.modern.path.tool.u032",
                "gui.modern.path.tool.u033", "", true, false, false),
        HEADER_TEMPLATES(Zone.HEADER, "action_templates", "gui.modern.path.tool.u034", "gui.modern.path.tool.u034",
                "gui.modern.path.tool.u035", "", true, false, false),
        HEADER_VARIABLES(Zone.HEADER, "action_variables", "gui.modern.path.tool.u036", "gui.modern.path.tool.u037",
                "gui.modern.path.tool.u038", "", true, false, false),
        HEADER_NODE(Zone.HEADER, "node_editor", "gui.modern.path.tool.u039", "gui.modern.path.tool.u039",
                "gui.modern.path.tool.u040", "", true, false, false),
        HEADER_TRIGGERS(Zone.HEADER, "trigger_rules", "gui.modern.path.tool.u041", "gui.modern.path.tool.u042",
                "gui.modern.path.tool.u043", "", true, false, false),
        HEADER_RELOAD(Zone.HEADER, "reload_draft", "gui.modern.path.tool.u044", "gui.modern.path.tool.u045",
                "gui.modern.path.tool.u046", "", true, false, false);

        public final Zone zone;
        public final String id;
        public final String label;
        public final String headerLabel;
        public final String description;
        public final String shortcut;
        public final boolean defaultPinned;
        public final boolean danger;
        public final boolean primary;

        ToolId(Zone zone, String id, String label, String shortcut, boolean defaultPinned, boolean danger,
                boolean primary) {
            this(zone, id, label, label, "", shortcut, defaultPinned, danger, primary);
        }

        ToolId(Zone zone, String id, String label, String headerLabel, String description, String shortcut,
                boolean defaultPinned, boolean danger, boolean primary) {
            this.zone = zone;
            this.id = id;
            this.label = label;
            this.headerLabel = headerLabel == null || headerLabel.isEmpty() ? label : headerLabel;
            this.description = description == null ? "" : description;
            this.shortcut = shortcut == null ? "" : shortcut;
            this.defaultPinned = defaultPinned;
            this.danger = danger;
            this.primary = primary;
        }

        public static ToolId byId(Zone zone, String id) {
            if (zone == null || id == null) {
                return null;
            }
            for (ToolId tool : values()) {
                if (tool.zone == zone && tool.id.equalsIgnoreCase(id)) {
                    return tool;
                }
            }
            return null;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final PathWorkbenchToolCatalog INSTANCE = new PathWorkbenchToolCatalog();

    private final Map<Zone, LinkedHashSet<String>> pinned = new LinkedHashMap<Zone, LinkedHashSet<String>>();

    private PathWorkbenchToolCatalog() {
        resetToDefaults();
    }

    public static PathWorkbenchToolCatalog get() {
        return INSTANCE;
    }

    public static void ensureLoaded() {
        INSTANCE.load();
    }

    public List<ToolId> all(Zone zone) {
        List<ToolId> tools = new ArrayList<ToolId>();
        for (ToolId tool : ToolId.values()) {
            if (tool.zone == zone) {
                tools.add(tool);
            }
        }
        return tools;
    }

    public List<ToolId> pinned(Zone zone) {
        Set<String> ids = pinnedIds(zone);
        List<ToolId> tools = new ArrayList<ToolId>();
        for (ToolId tool : all(zone)) {
            if (ids.contains(tool.id)) {
                tools.add(tool);
            }
        }
        return tools;
    }

    public List<ToolId> overflow(Zone zone) {
        Set<String> ids = pinnedIds(zone);
        List<ToolId> tools = new ArrayList<ToolId>();
        for (ToolId tool : all(zone)) {
            if (!ids.contains(tool.id)) {
                tools.add(tool);
            }
        }
        return tools;
    }

    public boolean isPinned(Zone zone, ToolId tool) {
        return tool != null && tool.zone == zone && pinnedIds(zone).contains(tool.id);
    }

    public void setPinned(Zone zone, ToolId tool, boolean pin) {
        if (zone == null || tool == null || tool.zone != zone) {
            return;
        }
        LinkedHashSet<String> ids = pinnedIds(zone);
        if (pin) {
            ids.add(tool.id);
        } else {
            ids.remove(tool.id);
        }
        save();
    }

    public void togglePinned(Zone zone, ToolId tool) {
        setPinned(zone, tool, !isPinned(zone, tool));
    }

    public int stripHeight(Zone zone, int innerWidth, boolean extraCoordRow) {
        int pinnedCount = 0;
        for (ToolId tool : pinned(zone)) {
            if (zone == Zone.STEP && (tool == ToolId.STEP_GET_COORDS || tool == ToolId.STEP_CLEAR_COORDS
                    || tool == ToolId.STEP_SETTINGS)) {
                continue;
            }
            pinnedCount++;
        }
        int rows = wrapRows(pinnedCount, innerWidth);
        int height = 20 + Math.max(0, rows) * 22;
        if (extraCoordRow) {
            height += 22;
        }
        return Math.max(26, height);
    }

    public static int wrapColumns(int innerWidth) {
        int gap = 3;
        // Leave enough room for a short Chinese label and a right aligned
        // shortcut. The previous 46px minimum forced commands such as
        // “删除步骤 / Delete” into an unreadable sliver in narrow panes.
        int minButton = 68;
        return Math.max(1, (Math.max(1, innerWidth) + gap) / (minButton + gap));
    }

    public static int wrapRows(int count, int innerWidth) {
        if (count <= 0) {
            return 0;
        }
        int columns = wrapColumns(innerWidth);
        return (count + columns - 1) / columns;
    }

    private LinkedHashSet<String> pinnedIds(Zone zone) {
        LinkedHashSet<String> ids = pinned.get(zone);
        if (ids == null) {
            ids = defaultPinned(zone);
            pinned.put(zone, ids);
        }
        return ids;
    }

    private void resetToDefaults() {
        pinned.clear();
        for (Zone zone : Zone.values()) {
            pinned.put(zone, defaultPinned(zone));
        }
    }

    private static LinkedHashSet<String> defaultPinned(Zone zone) {
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        for (ToolId tool : ToolId.values()) {
            if (tool.zone == zone && tool.defaultPinned) {
                ids.add(tool.id);
            }
        }
        return ids;
    }

    private Path configFile() {
        return Paths.get(ModConfig.CONFIG_DIR, "path_workbench_toolbar.json");
    }

    private void load() {
        Path file = configFile();
        if (!Files.exists(file)) {
            resetToDefaults();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) {
                resetToDefaults();
                return;
            }
            resetToDefaults();
            for (Zone zone : Zone.values()) {
                JsonElement element = root.get(zone.name().toLowerCase(Locale.ROOT));
                if (element == null || !element.isJsonArray()) {
                    continue;
                }
                LinkedHashSet<String> ids = new LinkedHashSet<String>();
                JsonArray array = element.getAsJsonArray();
                for (JsonElement item : array) {
                    if (item == null || !item.isJsonPrimitive()) {
                        continue;
                    }
                    ToolId tool = ToolId.byId(zone, item.getAsString());
                    if (tool != null) {
                        ids.add(tool.id);
                    }
                }
                pinned.put(zone, ids);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("Failed to load path workbench toolbar config", e);
            resetToDefaults();
        }
    }

    private void save() {
        Path file = configFile();
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            JsonObject root = new JsonObject();
            for (Zone zone : Zone.values()) {
                JsonArray array = new JsonArray();
                for (ToolId tool : pinned(zone)) {
                    array.add(tool.id);
                }
                root.add(zone.name().toLowerCase(Locale.ROOT), array);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.warn("Failed to save path workbench toolbar config", e);
        }
    }

}
