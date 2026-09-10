package com.zszl.zszlScriptMod.otherfeatures;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OtherFeatureGroupManager {

    private static final String RESOURCE_PATH = "other_features/groups.json";
    private static volatile List<GroupDef> groups = new ArrayList<>();

    private OtherFeatureGroupManager() {
    }

    public static final class GroupDef {
        public final String id;
        public final String name;
        public final List<FeatureDef> features;

        public GroupDef(String id, String name, List<FeatureDef> features) {
            this.id = safe(id);
            this.name = safe(name);
            this.features = features == null ? new ArrayList<>() : new ArrayList<>(features);
        }
    }

    public static final class FeatureDef {
        public final String id;
        public final String name;
        public final String description;

        public FeatureDef(String id, String name, String description) {
            this.id = safe(id);
            this.name = safe(name);
            this.description = safe(description);
        }
    }

    public static synchronized void reload() {
        List<GroupDef> loaded = loadGroupsFromResource();
        if (loaded.isEmpty()) {
            loaded = buildFallbackGroups();
        }
        groups = loaded;
    }

    public static List<GroupDef> getGroups() {
        return groups == null ? Collections.emptyList() : new ArrayList<>(groups);
    }

    private static List<GroupDef> loadGroupsFromResource() {
        try (InputStream in = OtherFeatureGroupManager.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                return new ArrayList<>();
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return parseGroups(reader);
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<GroupDef> parseGroups(Reader reader) {
        List<GroupDef> parsed = new ArrayList<>();
        JsonElement rootElement = new JsonParser().parse(reader);
        if (!rootElement.isJsonObject()) {
            return parsed;
        }

        JsonObject root = rootElement.getAsJsonObject();
        JsonArray groupArray = root.has("groups") && root.get("groups").isJsonArray()
                ? root.getAsJsonArray("groups")
                : new JsonArray();

        for (JsonElement element : groupArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject groupObject = element.getAsJsonObject();
            String id = groupObject.has("id") ? safe(groupObject.get("id").getAsString()) : "";
            String name = groupObject.has("name") ? safe(groupObject.get("name").getAsString()) : "";
            String groupId = id;
            String localizedName = localized("gui.other_feature.group." + groupId + ".name", name);
            if (localizedName.isEmpty()) {
                continue;
            }

            List<FeatureDef> features = new ArrayList<>();
            JsonArray featureArray = groupObject.has("features") && groupObject.get("features").isJsonArray()
                    ? groupObject.getAsJsonArray("features")
                    : new JsonArray();
            for (JsonElement featureElement : featureArray) {
                if (!featureElement.isJsonObject()) {
                    continue;
                }
                JsonObject featureObject = featureElement.getAsJsonObject();
                String featureId = featureObject.has("id") ? safe(featureObject.get("id").getAsString()) : "";
                String featureName = featureObject.has("name") ? safe(featureObject.get("name").getAsString()) : "";
                String description = featureObject.has("description")
                        ? safe(featureObject.get("description").getAsString())
                        : "";
                featureName = localized("gui.other_feature.feature." + featureId + ".name", featureName);
                description = localized("gui.other_feature.feature." + featureId + ".description", description);
                if (!featureName.isEmpty()) {
                    features.add(new FeatureDef(featureId, featureName, description));
                }
            }
            parsed.add(new GroupDef(id, localizedName, features));
        }

        return parsed;
    }

    private static List<GroupDef> buildFallbackGroups() {
        List<GroupDef> defaults = new ArrayList<>();
        defaults.add(new GroupDef("movement", localized("gui.other_feature.group.movement.name", "移动"), buildMovementFeatures()));
        defaults.add(new GroupDef("render", localized("gui.other_feature.group.render.name", "渲染"), mapRenderFeatures()));
        defaults.add(new GroupDef("block", localized("gui.other_feature.group.block.name", "方块"), mapBlockFeatures()));
        defaults.add(new GroupDef("world", localized("gui.other_feature.group.world.name", "世界"), mapWorldFeatures()));
        defaults.add(new GroupDef("item", localized("gui.other_feature.group.item.name", "物品"), mapItemFeatures()));
        defaults.add(new GroupDef("misc", localized("gui.other_feature.group.misc.name", "杂项"), mapMiscFeatures()));
        return defaults;
    }

    private static List<FeatureDef> buildMovementFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        features.add(new FeatureDef("speed", "加速", "左键切换加速总开关，右键打开参数配置。"));
        for (MovementFeatureManager.FeatureState state : MovementFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static List<FeatureDef> mapRenderFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        for (RenderFeatureManager.FeatureState state : RenderFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static List<FeatureDef> mapBlockFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        for (BlockFeatureManager.FeatureState state : BlockFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static List<FeatureDef> mapWorldFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        for (WorldFeatureManager.FeatureState state : WorldFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static List<FeatureDef> mapItemFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        for (ItemFeatureManager.FeatureState state : ItemFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static List<FeatureDef> mapMiscFeatures() {
        List<FeatureDef> features = new ArrayList<>();
        for (MiscFeatureManager.FeatureState state : MiscFeatureManager.getFeatures()) {
            addFeature(features, state.id, state.name, state.description);
        }
        return features;
    }

    private static void addFeature(List<FeatureDef> features, String id, String name, String description) {
        if (features == null) {
            return;
        }
        String safeName = localized("gui.other_feature.feature." + safe(id) + ".name", name);
        if (safeName.isEmpty()) {
            return;
        }
        features.add(new FeatureDef(id, safeName,
                localized("gui.other_feature.feature." + safe(id) + ".description", description)));
    }

    private static String localized(String key, String fallback) {
        String translated = I18n.format(key);
        if (translated != null && !translated.equals(key)) {
            return safe(translated);
        }
        if (key.endsWith(".description") && Minecraft.getMinecraft() != null
                && Minecraft.getMinecraft().gameSettings != null
                && "en_us".equalsIgnoreCase(Minecraft.getMinecraft().gameSettings.language)) {
            String generic = I18n.format("gui.other_feature.default_description");
            if (generic != null && !generic.equals("gui.other_feature.default_description")) {
                return safe(generic);
            }
        }
        return safe(fallback);
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }
}
