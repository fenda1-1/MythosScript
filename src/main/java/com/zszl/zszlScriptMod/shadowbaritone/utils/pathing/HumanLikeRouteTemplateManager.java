package com.zszl.zszlScriptMod.shadowbaritone.utils.pathing;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zszl.zszlScriptMod.shadowbaritone.api.utils.BetterBlockPos;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;
import net.minecraft.util.math.BlockPos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟真人路线模板管理器。
 *
 * 负责：
 * 1. 按 Profile 读取/保存模板
 * 2. 维护内存中的模板列表
 * 3. 提供基础查询能力
 * 4. 在运行时根据起终点挑选匹配模板，并输出一次“拟真路线计划”
 */
public final class HumanLikeRouteTemplateManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TEMPLATE_LIST_TYPE = new TypeToken<List<HumanLikeRouteTemplate>>() {
    }.getType();
    private static final String FILE_NAME = "human_like_route_templates.json";

    private static final List<HumanLikeRouteTemplate> templates = new ArrayList<>();
    private static PlannedRoute lastPlannedRoute;

    private HumanLikeRouteTemplateManager() {
    }

    private static Path getConfigFile() {
        return ProfileManager.getCurrentProfileDir().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        templates.clear();
        lastPlannedRoute = null;

        Path file = getConfigFile();
        if (!Files.exists(file)) {
            save();
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<HumanLikeRouteTemplate> loaded = GSON.fromJson(reader, TEMPLATE_LIST_TYPE);
            if (loaded != null) {
                for (HumanLikeRouteTemplate template : loaded) {
                    if (template == null) {
                        continue;
                    }
                    template.normalize();
                    templates.add(template);
                }
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法加载模拟真人路线模板", e);
            templates.clear();
        }
    }

    public static synchronized void save() {
        Path file = getConfigFile();
        try {
            Files.createDirectories(file.getParent());
            normalizeTemplates();
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(templates, TEMPLATE_LIST_TYPE, writer);
            }
        } catch (Exception e) {
            zszlScriptMod.LOGGER.error("无法保存模拟真人路线模板", e);
        }
    }

    public static synchronized List<HumanLikeRouteTemplate> getTemplates() {
        normalizeTemplates();
        return new ArrayList<>(templates);
    }

    public static synchronized void setTemplates(List<HumanLikeRouteTemplate> newTemplates) {
        templates.clear();
        if (newTemplates != null) {
            for (HumanLikeRouteTemplate template : newTemplates) {
                if (template == null) {
                    continue;
                }
                template.normalize();
                templates.add(template);
            }
        }
    }

    public static synchronized void addTemplate(HumanLikeRouteTemplate template) {
        if (template == null) {
            return;
        }
        template.normalize();
        templates.add(template);
    }

    public static synchronized boolean removeTemplateById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return templates.removeIf(template -> id.equals(template.id));
    }

    public static synchronized Optional<HumanLikeRouteTemplate> findTemplateById(String id) {
        if (id == null || id.trim().isEmpty()) {
            return Optional.empty();
        }
        for (HumanLikeRouteTemplate template : templates) {
            if (template != null && id.equals(template.id)) {
                template.normalize();
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    public static synchronized boolean isEmpty() {
        return templates.isEmpty();
    }

    public static synchronized Optional<PlannedRoute> buildRoute(BetterBlockPos start, BlockPos goal) {
        if (start == null || goal == null) {
            lastPlannedRoute = null;
            return Optional.empty();
        }

        normalizeTemplates();
        if (templates.isEmpty()) {
            lastPlannedRoute = null;
            return Optional.empty();
        }

        List<HumanLikeRouteTemplate> matched = new ArrayList<>();
        double startX = start.x + 0.5D;
        double startY = start.y;
        double startZ = start.z + 0.5D;
        double goalX = goal.getX() + 0.5D;
        double goalY = goal.getY();
        double goalZ = goal.getZ() + 0.5D;

        for (HumanLikeRouteTemplate template : templates) {
            if (template == null) {
                continue;
            }
            if (matchesRegion(template.startRegion, startX, startY, startZ)
                    && matchesRegion(template.endRegion, goalX, goalY, goalZ)) {
                matched.add(template);
            }
        }

        if (matched.isEmpty()) {
            lastPlannedRoute = null;
            return Optional.empty();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        HumanLikeRouteTemplate selected = matched.get(random.nextInt(matched.size()));
        PlannedRoute route = buildPlannedRoute(selected, random);
        lastPlannedRoute = route;
        return Optional.of(route);
    }

    public static synchronized Optional<PlannedRoute> getLastPlannedRoute() {
        return Optional.ofNullable(lastPlannedRoute);
    }

    public static synchronized void clearLastPlannedRoute() {
        lastPlannedRoute = null;
    }

    private static PlannedRoute buildPlannedRoute(HumanLikeRouteTemplate template, ThreadLocalRandom random) {
        List<PlannedAnchor> plannedAnchors = new ArrayList<>();

        if (template.keyPoints != null) {
            for (HumanLikeRouteTemplate.KeyPoint keyPoint : template.keyPoints) {
                if (keyPoint == null) {
                    continue;
                }
                keyPoint.normalize();
                plannedAnchors.add(new PlannedAnchor(
                        keyPoint.x,
                        keyPoint.y,
                        keyPoint.z,
                        Math.max(0.75D, keyPoint.tolerance),
                        Math.max(0.6D, keyPoint.priority),
                        true,
                        false,
                        false
                ));
            }
        }

        if (template.anchorGroups != null) {
            for (HumanLikeRouteTemplate.AnchorGroup group : template.anchorGroups) {
                if (group == null) {
                    continue;
                }
                group.normalize();
                if (group.anchors.isEmpty()) {
                    continue;
                }

                int available = group.anchors.size();
                int minSelect = Math.max(0, Math.min(group.minSelect, available));
                int maxSelect = Math.max(minSelect, Math.min(group.maxSelect, available));
                int selectCount = minSelect;
                if (maxSelect > minSelect) {
                    selectCount += random.nextInt(maxSelect - minSelect + 1);
                }

                List<HumanLikeRouteTemplate.AnchorPoint> pool = new ArrayList<>(group.anchors);
                for (int i = 0; i < selectCount && !pool.isEmpty(); i++) {
                    HumanLikeRouteTemplate.AnchorPoint selectedAnchor = takeWeightedRandom(pool, random);
                    if (selectedAnchor == null) {
                        break;
                    }
                    selectedAnchor.normalize();

                    double offsetX = 0.0D;
                    double offsetZ = 0.0D;
                    if (selectedAnchor.randomRadius > 0.001D) {
                        double angle = random.nextDouble() * Math.PI * 2.0D;
                        double distance = Math.sqrt(random.nextDouble()) * selectedAnchor.randomRadius;
                        offsetX = Math.cos(angle) * distance;
                        offsetZ = Math.sin(angle) * distance;
                    }

                    plannedAnchors.add(new PlannedAnchor(
                            selectedAnchor.x + offsetX,
                            selectedAnchor.y,
                            selectedAnchor.z + offsetZ,
                            Math.max(0.75D, selectedAnchor.randomRadius),
                            Math.max(0.5D, selectedAnchor.weight),
                            false,
                            selectedAnchor.preferEarly,
                            selectedAnchor.preferLate
                    ));
                }
            }
        }

        return new PlannedRoute(
                safeString(template.id),
                safeString(template.name),
                safeString(template.note),
                plannedAnchors
        );
    }

    private static HumanLikeRouteTemplate.AnchorPoint takeWeightedRandom(
            List<HumanLikeRouteTemplate.AnchorPoint> pool,
            ThreadLocalRandom random) {
        if (pool.isEmpty()) {
            return null;
        }

        double totalWeight = 0.0D;
        for (HumanLikeRouteTemplate.AnchorPoint anchor : pool) {
            if (anchor == null) {
                continue;
            }
            anchor.normalize();
            totalWeight += Math.max(0.0D, anchor.weight);
        }

        int selectedIndex = 0;
        if (totalWeight > 0.0001D) {
            double cursor = random.nextDouble() * totalWeight;
            for (int i = 0; i < pool.size(); i++) {
                HumanLikeRouteTemplate.AnchorPoint anchor = pool.get(i);
                if (anchor == null) {
                    continue;
                }
                cursor -= Math.max(0.0D, anchor.weight);
                if (cursor <= 0.0D) {
                    selectedIndex = i;
                    break;
                }
            }
        } else {
            selectedIndex = random.nextInt(pool.size());
        }

        return pool.remove(selectedIndex);
    }

    private static boolean matchesRegion(HumanLikeRouteTemplate.Region region, double x, double y, double z) {
        if (region == null) {
            return false;
        }
        region.normalize();

        double dx = x - region.centerX;
        double dz = z - region.centerZ;
        double horizontalDistanceSq = dx * dx + dz * dz;
        double horizontalRadius = region.horizontalRadius;
        if (horizontalDistanceSq > horizontalRadius * horizontalRadius) {
            return false;
        }

        return Math.abs(y - region.centerY) <= region.verticalRadius;
    }

    private static String safeString(String value) {
        return value == null ? "" : value;
    }

    private static void normalizeTemplates() {
        templates.removeIf(template -> template == null);
        for (HumanLikeRouteTemplate template : templates) {
            template.normalize();
        }
    }

    public static final class PlannedRoute {
        public final String templateId;
        public final String templateName;
        public final String note;
        public final List<PlannedAnchor> anchors;

        private PlannedRoute(String templateId, String templateName, String note, List<PlannedAnchor> anchors) {
            this.templateId = templateId;
            this.templateName = templateName;
            this.note = note;
            this.anchors = new ArrayList<>(anchors);
        }
    }

    public static final class PlannedAnchor {
        public final double x;
        public final double y;
        public final double z;
        public final double radius;
        public final double weight;
        public final boolean required;
        public final boolean preferEarly;
        public final boolean preferLate;

        private PlannedAnchor(double x, double y, double z, double radius, double weight, boolean required,
                boolean preferEarly, boolean preferLate) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.weight = weight;
            this.required = required;
            this.preferEarly = preferEarly;
            this.preferLate = preferLate;
        }
    }
}