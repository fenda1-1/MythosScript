package com.zszl.zszlScriptMod.gui.path.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.system.ProfileManager;
import com.zszl.zszlScriptMod.zszlScriptMod;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ActionTemplateCatalog {
        private static final String CATEGORY_COMBAT = "刷怪/击杀";
        private static final String CATEGORY_TELEPORT = "传送/副本";
        private static final String CATEGORY_INVENTORY = "背包/容器";
        private static final String CATEGORY_NPC = "NPC/传送";
        private static final String CATEGORY_PACKET = "抓包/等待";
        private static final String CATEGORY_FLOW = "条件/闭环";
        private static final String CATEGORY_CAPTURE = "采集/变量";
        private static final String CATEGORY_SUPPORT = "补给/状态";
        private static final String CATEGORY_SEQUENCE = "子序列/调度";

        private static final String CUSTOM_TEMPLATE_FILE_NAME = "action_templates.json";
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final List<ActionTemplate> BUILTIN_TEMPLATES = buildTemplates();
        private static final List<ActionTemplate> CUSTOM_TEMPLATES = new ArrayList<ActionTemplate>();
        private static final List<String> CUSTOM_CATEGORIES = new ArrayList<String>();
        private static boolean customLoaded = false;

        private ActionTemplateCatalog() {
        }

        public static List<ActionTemplate> getBuiltinTemplates() {
                return Collections.unmodifiableList(BUILTIN_TEMPLATES);
        }

        public static synchronized List<ActionTemplate> getTemplates() {
                ensureCustomLoaded();
                List<ActionTemplate> templates = new ArrayList<ActionTemplate>(BUILTIN_TEMPLATES);
                templates.addAll(CUSTOM_TEMPLATES);
                return Collections.unmodifiableList(templates);
        }

        public static synchronized ActionTemplate getTemplateById(String id) {
                String needle = safe(id).trim();
                if (needle.isEmpty()) {
                        return null;
                }
                for (ActionTemplate template : BUILTIN_TEMPLATES) {
                        if (needle.equals(template.getId())) {
                                return template;
                        }
                }
                ensureCustomLoaded();
                for (ActionTemplate template : CUSTOM_TEMPLATES) {
                        if (needle.equals(template.getId())) {
                                return template;
                        }
                }
                return null;
        }

        public static JsonObject describeForMcp(ActionTemplate template, boolean includeActions) {
                if (template == null) {
                        return new JsonObject();
                }
                JsonObject object = new JsonObject();
                object.addProperty("id", template.getId());
                object.addProperty("category", template.getCategory());
                object.addProperty("name", template.getName());
                object.addProperty("summary", template.getSummary());
                object.addProperty("useCase", template.getUseCase());
                object.addProperty("note", template.getNote());
                object.addProperty("custom", template.isCustom());
                object.addProperty("actionCount", template.getActions().size());
                if (includeActions) {
                        JsonArray actions = new JsonArray();
                        for (ActionData action : template.getActions()) {
                                if (action == null || action.type == null) {
                                        continue;
                                }
                                JsonObject actionObject = new JsonObject();
                                actionObject.addProperty("type", action.type);
                                actionObject.add("params", action.params == null ? new JsonObject()
                                                : new JsonParser().parse(action.params.toString()).getAsJsonObject());
                                actions.add(actionObject);
                        }
                        object.add("actions", actions);
                }
                return object;
        }

        public static synchronized List<String> getCategories() {
                ensureCustomLoaded();
                Set<String> categories = new LinkedHashSet<String>();
                for (ActionTemplate template : BUILTIN_TEMPLATES) {
                        categories.add(template.getCategory());
                }
                categories.addAll(CUSTOM_CATEGORIES);
                for (ActionTemplate template : CUSTOM_TEMPLATES) {
                        categories.add(template.getCategory());
                }
                return new ArrayList<String>(categories);
        }

        public static synchronized boolean isBuiltinCategory(String category) {
                String normalized = normalizeCategory(category);
                for (ActionTemplate template : BUILTIN_TEMPLATES) {
                        if (safe(template.getCategory()).equals(normalized)) {
                                return true;
                        }
                }
                return false;
        }

        public static synchronized boolean isCustomCategory(String category) {
                ensureCustomLoaded();
                String normalized = normalizeCategory(category);
                return !normalized.isEmpty() && CUSTOM_CATEGORIES.contains(normalized);
        }

        public static synchronized boolean addCustomCategory(String category) {
                ensureCustomLoaded();
                String normalized = normalizeCategory(category);
                if (normalized.isEmpty() || isBuiltinCategory(normalized) || CUSTOM_CATEGORIES.contains(normalized)) {
                        return false;
                }
                CUSTOM_CATEGORIES.add(normalized);
                saveCustomTemplates();
                return true;
        }

        public static synchronized boolean deleteCustomCategory(String category) {
                ensureCustomLoaded();
                String normalized = normalizeCategory(category);
                if (normalized.isEmpty() || isBuiltinCategory(normalized)) {
                        return false;
                }
                boolean changed = CUSTOM_CATEGORIES.remove(normalized);
                Iterator<ActionTemplate> iterator = CUSTOM_TEMPLATES.iterator();
                while (iterator.hasNext()) {
                        ActionTemplate template = iterator.next();
                        if (safe(template.getCategory()).equals(normalized)) {
                                iterator.remove();
                                changed = true;
                        }
                }
                if (changed) {
                        saveCustomTemplates();
                }
                return changed;
        }

        public static synchronized boolean renameCustomCategory(String oldCategory, String newCategory) {
                ensureCustomLoaded();
                String oldName = normalizeCategory(oldCategory);
                String newName = normalizeCategory(newCategory);
                if (oldName.isEmpty() || newName.isEmpty() || oldName.equals(newName)
                                || isBuiltinCategory(oldName) || isBuiltinCategory(newName)
                                || !CUSTOM_CATEGORIES.contains(oldName) || CUSTOM_CATEGORIES.contains(newName)) {
                        return false;
                }
                int categoryIndex = CUSTOM_CATEGORIES.indexOf(oldName);
                if (categoryIndex < 0) {
                        return false;
                }
                CUSTOM_CATEGORIES.set(categoryIndex, newName);
                for (int i = 0; i < CUSTOM_TEMPLATES.size(); i++) {
                        ActionTemplate template = CUSTOM_TEMPLATES.get(i);
                        if (safe(template.getCategory()).equals(oldName)) {
                                CUSTOM_TEMPLATES.set(i, new ActionTemplate(template.getId(), newName,
                                                template.getName(), template.getSummary(), template.getUseCase(),
                                                template.getNote(), template.getActions(), true));
                        }
                }
                saveCustomTemplates();
                return true;
        }

        public static synchronized ActionTemplate addCustomTemplate(String category, String name, String summary,
                        String useCase, String note, List<ActionData> actions) {
                ensureCustomLoaded();
                String normalizedCategory = normalizeCategory(category);
                if (normalizedCategory.isEmpty()) {
                        normalizedCategory = "自定义";
                }
                if (!isBuiltinCategory(normalizedCategory) && !CUSTOM_CATEGORIES.contains(normalizedCategory)) {
                        CUSTOM_CATEGORIES.add(normalizedCategory);
                }
                String normalizedName = safe(name).trim();
                if (normalizedName.isEmpty() || actions == null || actions.isEmpty()) {
                        return null;
                }
                String id = "custom_" + System.currentTimeMillis();
                ActionTemplate template = new ActionTemplate(id, normalizedCategory, normalizedName,
                                safe(summary).trim(), safe(useCase).trim(), safe(note).trim(), actions, true);
                CUSTOM_TEMPLATES.add(template);
                saveCustomTemplates();
                return template;
        }

        public static synchronized boolean deleteCustomTemplate(String id) {
                ensureCustomLoaded();
                String normalizedId = safe(id);
                Iterator<ActionTemplate> iterator = CUSTOM_TEMPLATES.iterator();
                while (iterator.hasNext()) {
                        ActionTemplate template = iterator.next();
                        if (safe(template.getId()).equals(normalizedId)) {
                                iterator.remove();
                                saveCustomTemplates();
                                return true;
                        }
                }
                return false;
        }

        public static final class ActionTemplate {
                private final String id;
                private final String category;
                private final String name;
                private final String summary;
                private final String useCase;
                private final String note;
                private final List<ActionData> actions;
                private final String searchText;
                private final boolean custom;

                private ActionTemplate(String id, String category, String name, String summary, String useCase,
                                String note,
                                List<ActionData> actions) {
                        this(id, category, name, summary, useCase, note, actions, false);
                }

                private ActionTemplate(String id, String category, String name, String summary, String useCase,
                                String note,
                                List<ActionData> actions, boolean custom) {
                        this.id = safe(id);
                        this.category = safe(category);
                        this.name = safe(name);
                        this.summary = safe(summary);
                        this.useCase = safe(useCase);
                        this.note = safe(note);
                        this.actions = Collections.unmodifiableList(copyActions(actions));
                        this.custom = custom;
                        this.searchText = buildSearchText();
                }

                public String getId() {
                        return id;
                }

                public String getCategory() {
                        return category;
                }

                public String getName() {
                        return name;
                }

                public String getSummary() {
                        return summary;
                }

                public String getUseCase() {
                        return useCase;
                }

                public String getNote() {
                        return note;
                }

                public List<ActionData> getActions() {
                        return actions;
                }

                public String getSearchText() {
                        return searchText;
                }

                public boolean isCustom() {
                        return custom;
                }

                private String buildSearchText() {
                        StringBuilder builder = new StringBuilder();
                        appendSearch(builder, id);
                        appendSearch(builder, category);
                        appendSearch(builder, name);
                        appendSearch(builder, summary);
                        appendSearch(builder, useCase);
                        appendSearch(builder, note);
                        for (ActionData action : actions) {
                                if (action != null) {
                                        appendSearch(builder, action.type);
                                        try {
                                                appendSearch(builder, action.getDescription());
                                        } catch (Exception ignored) {
                                        }
                                }
                        }
                        return builder.toString();
                }
        }

        private static void ensureCustomLoaded() {
                if (customLoaded) {
                        return;
                }
                customLoaded = true;
                CUSTOM_TEMPLATES.clear();
                CUSTOM_CATEGORIES.clear();
                Path path = getCustomTemplateFile();
                if (!Files.exists(path)) {
                        return;
                }
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonElement rootElement = new JsonParser().parse(reader);
                        if (rootElement == null || !rootElement.isJsonObject()) {
                                return;
                        }
                        JsonObject root = rootElement.getAsJsonObject();
                        if (root.has("categories") && root.get("categories").isJsonArray()) {
                                JsonArray categories = root.getAsJsonArray("categories");
                                for (JsonElement element : categories) {
                                        String category = normalizeCategory(element == null || element.isJsonNull()
                                                        ? ""
                                                        : element.getAsString());
                                        if (!category.isEmpty() && !isBuiltinCategory(category)
                                                        && !CUSTOM_CATEGORIES.contains(category)) {
                                                CUSTOM_CATEGORIES.add(category);
                                        }
                                }
                        }
                        if (root.has("templates") && root.get("templates").isJsonArray()) {
                                JsonArray templates = root.getAsJsonArray("templates");
                                for (JsonElement element : templates) {
                                        ActionTemplate template = parseCustomTemplate(element);
                                        if (template != null) {
                                                CUSTOM_TEMPLATES.add(template);
                                        }
                                }
                        }
                } catch (Exception e) {
                        zszlScriptMod.LOGGER.warn("[action_templates] 读取自定义模板失败: {}", path, e);
                }
        }

        private static ActionTemplate parseCustomTemplate(JsonElement element) {
                if (element == null || !element.isJsonObject()) {
                        return null;
                }
                JsonObject object = element.getAsJsonObject();
                List<ActionData> actions = new ArrayList<ActionData>();
                if (object.has("actions") && object.get("actions").isJsonArray()) {
                        for (JsonElement actionElement : object.getAsJsonArray("actions")) {
                                if (actionElement == null || !actionElement.isJsonObject()) {
                                        continue;
                                }
                                JsonObject actionObject = actionElement.getAsJsonObject();
                                String type = readString(actionObject, "type");
                                if (type.trim().isEmpty()) {
                                        continue;
                                }
                                JsonObject params = actionObject.has("params") && actionObject.get("params").isJsonObject()
                                                ? actionObject.getAsJsonObject("params")
                                                : new JsonObject();
                                actions.add(new ActionData(type, params));
                        }
                }
                if (actions.isEmpty()) {
                        return null;
                }
                String id = readString(object, "id");
                if (id.trim().isEmpty()) {
                        id = "custom_" + System.currentTimeMillis() + "_" + CUSTOM_TEMPLATES.size();
                }
                String category = normalizeCategory(readString(object, "category"));
                if (category.isEmpty()) {
                        category = "自定义";
                }
                if (!isBuiltinCategory(category) && !CUSTOM_CATEGORIES.contains(category)) {
                        CUSTOM_CATEGORIES.add(category);
                }
                return new ActionTemplate(id, category, readString(object, "name"),
                                readString(object, "summary"), readString(object, "useCase"),
                                readString(object, "note"), actions, true);
        }

        private static void saveCustomTemplates() {
                try {
                        Path path = getCustomTemplateFile();
                        Files.createDirectories(path.getParent());
                        JsonObject root = new JsonObject();
                        JsonArray categories = new JsonArray();
                        for (String category : CUSTOM_CATEGORIES) {
                                if (!safe(category).trim().isEmpty() && !isBuiltinCategory(category)) {
                                        categories.add(category);
                                }
                        }
                        root.add("categories", categories);

                        JsonArray templates = new JsonArray();
                        for (ActionTemplate template : CUSTOM_TEMPLATES) {
                                templates.add(toJson(template));
                        }
                        root.add("templates", templates);
                        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                                GSON.toJson(root, writer);
                        }
                } catch (Exception e) {
                        zszlScriptMod.LOGGER.warn("[action_templates] 保存自定义模板失败", e);
                }
        }

        private static JsonObject toJson(ActionTemplate template) {
                JsonObject object = new JsonObject();
                object.addProperty("id", template.getId());
                object.addProperty("category", template.getCategory());
                object.addProperty("name", template.getName());
                object.addProperty("summary", template.getSummary());
                object.addProperty("useCase", template.getUseCase());
                object.addProperty("note", template.getNote());
                JsonArray actions = new JsonArray();
                for (ActionData action : template.getActions()) {
                        if (action == null || action.type == null) {
                                continue;
                        }
                        JsonObject actionObject = new JsonObject();
                        actionObject.addProperty("type", action.type);
                        actionObject.add("params", action.params == null ? new JsonObject()
                                        : new JsonParser().parse(action.params.toString()).getAsJsonObject());
                        actions.add(actionObject);
                }
                object.add("actions", actions);
                return object;
        }

        private static Path getCustomTemplateFile() {
                return ProfileManager.getCurrentProfileDir().resolve(CUSTOM_TEMPLATE_FILE_NAME);
        }

        private static String readString(JsonObject object, String key) {
                if (object == null || key == null || !object.has(key) || object.get(key).isJsonNull()) {
                        return "";
                }
                try {
                        return object.get(key).getAsString();
                } catch (Exception e) {
                        return "";
                }
        }

        private static List<ActionTemplate> buildTemplates() {
                List<ActionTemplate> templates = new ArrayList<ActionTemplate>();
                templates.add(waitBossRespawnAndKillOnce());
                templates.add(killBossCountLoop());
                templates.add(waitBossHudAndKill());
                templates.add(huntWhitelistBlacklist());
                templates.add(followNamedPlayerOrEntity());
                templates.add(directCommandTeleport());
                templates.add(guiTitleSettleThenClick());
                templates.add(openWorldChestWaitThenLoot());
                templates.add(dungeonTeleportCooldownAreaRetry());
                templates.add(commandMenuContainerTeleportFirstDungeon());
                templates.add(silentUseMenuContainerTeleportFirstDungeon());
                templates.add(hotbarMenuItemContainerTeleport());
                templates.add(hotbarUseMenuContainerTeleport());
                templates.add(heldMenuItemContainerTeleport());
                templates.add(fixedSlotContainerTeleport());
                templates.add(multiLevelDungeonContainerTeleport());
                templates.add(pagedContainerTeleport());
                templates.add(npcDialogContainerTeleport());
                templates.add(containerTeleportRetryUntilArea());
                templates.add(rightClickBlockTeleportRetry());
                templates.add(lineSwitchContainerTeleport());
                templates.add(crossServerPacketTeleport());
                templates.add(portalStandStillWaitTeleport());
                templates.add(guiTeleportFailureTextRetry());
                templates.add(packetCapturedTeleportWait());
                templates.add(packetTextTeleportWait());
                templates.add(npcTeleportRetry());
                templates.add(waitNpcAndInteract());
                templates.add(npcMultiStepDialogTeleport());
                templates.add(batchDisassembleLowStatsByCommand());
                templates.add(batchDisassembleLowStatsByMenu());
                templates.add(batchExchangeEquipment());
                templates.add(batchSubmitMaterials());
                templates.add(batchExchangeByStackSize());
                templates.add(depositLootToWarehouse());
                templates.add(withdrawSuppliesFromWarehouse());
                templates.add(warehouseAutoDepositHighlights());
                templates.add(takeAllContainerAndClose());
                templates.add(rewardPagesTakeAll());
                templates.add(conditionalRewardClaimSlot());
                templates.add(dropJunkByConfiguredFilter());
                templates.add(sellJunkEquipment());
                templates.add(repairEquipmentByMenu());
                templates.add(ensurePotionOrBuy());
                templates.add(npcShopBuySupply());
                templates.add(enableAutoEatBeforeFight());
                templates.add(enableAutoEquipSet());
                templates.add(enablePickupDuringFarm());
                templates.add(toggleKillAuraForMobFarm());
                templates.add(packetCaptureAndReadField());
                templates.add(waitPacketText());
                templates.add(waitPacketFailureRetry());
                templates.add(sendPacketAndWaitAck());
                templates.add(guiConfirmClosedLoop());
                templates.add(guiElementRetryUntilAppears());
                templates.add(restartSequenceOnBadState());
                templates.add(repeatActionsBlock());
                templates.add(skipDailyIfScoreboardDone());
                templates.add(expressionBranchLoop());
                templates.add(waitCombinedAnyReady());
                templates.add(waitCombinedAllReady());
                templates.add(captureNearbyEntityBranch());
                templates.add(captureHotbarMenuAndUse());
                templates.add(captureGuiElementThenClick());
                templates.add(captureScoreboardDailyDone());
                templates.add(captureScreenRegionColorWait());
                templates.add(captureBlockChangeWait());
                templates.add(runSubSequenceForeground());
                templates.add(runSubSequenceBackground());
                templates.add(runSubSequenceInterval());
                templates.add(stopBackgroundSequence());
                templates.add(pointOccupiedSkipNextPoint());
                templates.add(bossPatrolAcrossPoints());
                templates.add(playerDetectedRetreat());
                templates.add(targetMissingRetryNextPoint());
                templates.add(waitRespawnKillContinue());
                templates.add(safeMenuReopenRecovery());
                templates.add(retryTargetScanThenNextPoint());
                templates.add(switchRouteByStateVar());
                templates.add(branchByPacketFieldStatus());
                templates.add(ifElsePlayerDetectedSwitchLine());
                templates.add(forEachPointPatrolCheck());
                templates.add(forEachListBatchNotify());
                templates.add(whileConditionUntilAreaClear());
                return Collections.unmodifiableList(templates);
        }

        private static ActionTemplate waitBossRespawnAndKillOnce() {
                return template("boss_respawn_kill_once", CATEGORY_COMBAT,
                                "等待 Boss 复活并击杀 1 次",
                                "附近没有 Boss 时等待，出现后执行一次中心搜怪并结束当前动作块。",
                                "适合定点刷单个 Boss、精英怪、刷新点怪物。可作为单独步骤插入到刷怪路线中。",
                                "把“Boss 名称关键字”改成实际怪物名；如果一次 hunt 不能稳定完成击杀，可以把 hunt 后面的跳过动作改成等待击杀提示或数据包。",
                                actions(
                                                action("condition_entity_nearby",
                                                                params("entityName", "Boss 名称关键字", "radius", 24,
                                                                                "skipCount", 2)),
                                                action("hunt", params("radius", 24, "huntUpRange", 8, "huntDownRange",
                                                                8,
                                                                "noTargetSkipCount", 0, "enableNameWhitelist", true,
                                                                "nameWhitelistText",
                                                                "Boss 名称关键字", "enableNameBlacklist", false,
                                                                "nameBlacklistText", "",
                                                                "showHuntRange", true)),
                                                action("skip_actions", params("count", 2)),
                                                action("delay", params("ticks", "200", "normalizeDelayTo20Tps", true)),
                                                action("goto_action", params("targetActionIndex", 0))));
        }

        private static ActionTemplate killBossCountLoop() {
                return template("boss_kill_count_loop", CATEGORY_COMBAT,
                                "累计击杀 N 次 Boss",
                                "用变量记录目标数量与当前击杀次数，未达成时继续等待刷新并循环。",
                                "适合日常任务、成就任务、需要固定击杀次数后退出的 Boss 场景。",
                                "默认通过“击杀”数据包文本确认击杀。不同服务器提示文本不同，请把 packetText 和 Boss 名称改成实际值。",
                                actions(
                                                action("set_var",
                                                                params("name", "sequence.boss_kill_target", "value", 5,
                                                                                "valueType",
                                                                                "number")),
                                                action("set_var",
                                                                params("name", "sequence.boss_kill_count", "value", 0,
                                                                                "valueType",
                                                                                "number")),
                                                action("condition_expression", params("expressions",
                                                                strings("sequence.boss_kill_count < sequence.boss_kill_target"),
                                                                "skipCount", 7)),
                                                action("condition_entity_nearby",
                                                                params("entityName", "Boss 名称关键字", "radius", 24,
                                                                                "skipCount", 4)),
                                                action("hunt", params("radius", 24, "huntUpRange", 8, "huntDownRange",
                                                                8,
                                                                "noTargetSkipCount", 0, "enableNameWhitelist", true,
                                                                "nameWhitelistText",
                                                                "Boss 名称关键字", "showHuntRange", true)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "击杀", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("set_var",
                                                                params("name", "sequence.boss_kill_count", "expression",
                                                                                "sequence.boss_kill_count + 1",
                                                                                "valueType", "number")),
                                                action("goto_action", params("targetActionIndex", 2)),
                                                action("delay", params("ticks", "200", "normalizeDelayTo20Tps", true)),
                                                action("goto_action", params("targetActionIndex", 2)),
                                                action("system_message", params("message", "Boss 击杀数量已达成"))));
        }

        private static ActionTemplate waitBossHudAndKill() {
                return template("boss_hud_respawn_kill", CATEGORY_COMBAT,
                                "等待刷新提示后击杀 Boss",
                                "等待 HUD 或聊天识别到刷新文本，再启动白名单搜怪并等待击杀提示。",
                                "适合服务器有“Boss 已刷新/出现”提示，但实体刷新位置或加载时间不稳定的 Boss。",
                                "把刷新提示、Boss 名称、击杀提示改成实际文本。HUD 捕获不到时可改成 wait_until_packet_text。",
                                actions(
                                                action("wait_until_hud_text",
                                                                params("contains", "Boss已刷新", "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("hunt", params("radius", 32, "huntUpRange", 10, "huntDownRange",
                                                                10,
                                                                "noTargetSkipCount", 0, "enableNameWhitelist", true,
                                                                "nameWhitelistText",
                                                                "Boss 名称关键字", "showHuntRange", true)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "击杀", "preExecuteCount", 0,
                                                                                "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate huntWhitelistBlacklist() {
                return template("hunt_whitelist_blacklist", CATEGORY_COMBAT,
                                "白名单目标搜怪并排除干扰",
                                "只追击白名单目标，同时排除宠物、NPC、召唤物等干扰实体。",
                                "适合怪物堆、活动场景、Boss 旁边有不可攻击 NPC 或小怪的区域。",
                                "白名单和黑名单支持多行文本。按服务器实体名调整关键字，必要时开启显示范围辅助调试。",
                                actions(
                                                action("hunt", params("radius", 18, "huntUpRange", 6, "huntDownRange",
                                                                6,
                                                                "noTargetSkipCount", 1, "enableNameWhitelist", true,
                                                                "nameWhitelistText",
                                                                "目标怪物\nBoss", "enableNameBlacklist", true,
                                                                "nameBlacklistText",
                                                                "NPC\n宠物\n召唤物", "showHuntRange", true)),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true))));
        }

        private static ActionTemplate followNamedPlayerOrEntity() {
                return template("follow_named_player_or_entity", CATEGORY_COMBAT,
                                "跟随队友或指定实体",
                                "扫描附近实体并跟随指定玩家/实体，保持距离直到超时或丢失目标。",
                                "适合组队副本、跟随带路玩家、跟随移动 NPC 或活动目标。",
                                "默认跟随玩家。若要跟随怪物或 NPC，把 entityType 改为 hostile/passive/all，并填 targetName。",
                                actions(
                                                action("follow_entity",
                                                                params("entityType", "player", "targetName", "队友名称",
                                                                                "searchRadius", 24, "followDistance", 3,
                                                                                "timeout", 60, "stopOnLost", true))));
        }

        private static ActionTemplate directCommandTeleport() {
                return template("direct_command_teleport", CATEGORY_TELEPORT,
                                "指令传送并等待落点",
                                "直接执行传送/warp 指令，然后等待传送提示和玩家进入目标区域。",
                                "适合 /warp、/home、/spawn、/tpaccept 等指令型传送。",
                                "如果服务器没有数据包文本提示，可删除 wait_until_packet_text，只保留区域等待。",
                                actions(
                                                action("command", params("command", "/warp 副本一层")),
                                                action("wait_until_packet_text",
                                                                params("packetText", "传送", "preExecuteCount", 0,
                                                                                "timeoutTicks", 100,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate commandMenuContainerTeleportFirstDungeon() {
                return template("command_menu_container_teleport_first_dungeon", CATEGORY_TELEPORT,
                                "命令打开菜单并点第一层副本",
                                "先用 /菜单 打开容器菜单，再用容器内槽位点击按物品文本匹配“第一层副本”。",
                                "适合副本/传送菜单是箱子界面，第一层副本是容器槽位物品的服务器。",
                                "这是最常用的容器传送模板。把 /菜单、GUI 标题、槽位文本和目标坐标替换成实际值。",
                                actions(
                                                action("command", params("command", "/菜单")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "传送", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate silentUseMenuContainerTeleportFirstDungeon() {
                return template("silentuse_menu_container_teleport_first_dungeon", CATEGORY_TELEPORT,
                                "静默使用菜单物品并点第一层副本",
                                "从背包中按文本找到“菜单”物品，临时切到快捷栏静默使用，再点击容器传送槽位。",
                                "适合没有 /菜单 指令，但背包里有菜单/指南针/传送书物品的服务器。",
                                "静默使用不会要求物品当前就在快捷栏。tempslot 是临时快捷栏索引 0-8，默认用 0。",
                                actions(
                                                action("silentuse", params("item", "菜单", "tempslot", 0,
                                                                "switchDelayTicks", 0,
                                                                "useDelayTicks", 1, "switchBackDelayTicks", 0)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate hotbarMenuItemContainerTeleport() {
                return template("hotbar_menu_item_container_teleport", CATEGORY_TELEPORT,
                                "菜单物品放入快捷栏后传送",
                                "先把背包内“菜单”移动到指定快捷栏，再切换并使用，打开菜单后点击副本槽位。",
                                "适合静默使用受限、但允许正常切换快捷栏使用菜单物品的服务器。",
                                "targetHotbarSlot 为 1-9。changeLocalSlot/useAfterSwitch 会改变本地快捷栏选择。",
                                actions(
                                                action("move_inventory_item_to_hotbar",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "targetHotbarSlot", 9)),
                                                action("switch_hotbar_slot",
                                                                params("targetHotbarSlot", 9, "useAfterSwitch", true,
                                                                                "useAfterSwitchDelayTicks", 1)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate hotbarUseMenuContainerTeleport() {
                return template("hotbar_use_menu_container_teleport", CATEGORY_TELEPORT,
                                "快捷栏菜单物品直接传送",
                                "在快捷栏中按文本匹配“菜单”物品并右键使用，打开容器后点击“第一层副本”。",
                                "适合菜单物品长期固定在快捷栏，或者服务器要求正常快捷栏发包使用的传送。",
                                "changeLocalSlot=true 会同步切换本地快捷栏显示；如果不想改变本地显示可改为 false。",
                                actions(
                                                action("use_hotbar_item",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "useMode", "RIGHT_CLICK",
                                                                                "changeLocalSlot", true, "count", 1,
                                                                                "switchItemDelayTicks", 0,
                                                                                "switchDelayTicks", 1,
                                                                                "switchBackDelayTicks", 0,
                                                                                "intervalTicks", 0)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "传送", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate heldMenuItemContainerTeleport() {
                return template("held_menu_item_container_teleport", CATEGORY_TELEPORT,
                                "使用当前手持菜单物品传送",
                                "直接右键当前主手物品打开菜单，再按容器物品文本点击目标传送项。",
                                "适合已经手持指南针/菜单/传送书的路线步骤。",
                                "这个模板最短，但依赖执行前主手已经是正确菜单物品。",
                                actions(
                                                action("use_held_item", params("delayTicks", 0)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate fixedSlotContainerTeleport() {
                return template("fixed_slot_container_teleport", CATEGORY_TELEPORT,
                                "固定槽位容器传送",
                                "打开传送菜单后直接点击固定槽位，例如槽位 10 的“第一层副本”。",
                                "适合菜单物品文本频繁变色、动态描述变化，但槽位位置稳定的服务器。",
                                "slot 从 0 开始。若槽位物品名稳定，contains 可填“第一层副本”作为二次保险。",
                                actions(
                                                action("command", params("command", "/菜单")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickBySlot(10, "第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate multiLevelDungeonContainerTeleport() {
                return template("multi_level_dungeon_container_teleport", CATEGORY_TELEPORT,
                                "多层容器菜单进入第一层副本",
                                "打开主菜单后依次点击“副本”“普通副本”“第一层副本”，每层都等待 GUI 标题。",
                                "适合传送入口有多级箱子菜单或分页菜单的服务器。",
                                "每一级槽位都用文本匹配，不依赖固定槽位。按实际菜单层级增删 window_click。",
                                actions(
                                                action("command", params("command", "/菜单")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("副本"),
                                                action("wait_until_gui_title",
                                                                params("title", "副本", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("普通副本"),
                                                action("wait_until_gui_title",
                                                                params("title", "普通副本", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate pagedContainerTeleport() {
                return template("paged_container_teleport", CATEGORY_TELEPORT,
                                "分页容器查找副本并传送",
                                "打开菜单后先采集当前页是否存在“第一层副本”，存在就点击，不存在就点下一页并循环查找。",
                                "适合副本很多、目标入口不在第一页、服务器会分页展示传送项的菜单。",
                                "如果下一页按钮文本不同，把“下一页”改成实际文本。若可能无限翻页，建议额外加计数变量限制。",
                                actions(
                                                action("command", params("command", "/菜单")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("capture_gui_element",
                                                                params("varName", "sequence.target_slot", "elementType",
                                                                                "SLOT", "guiElementLocatorMode", "TEXT",
                                                                                "locatorText", "第一层副本",
                                                                                "locatorMatchMode", "CONTAINS")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.target_slot_found == true"),
                                                                                "skipCount", 2)),
                                                windowClickByText("第一层副本"),
                                                action("skip_actions", params("count", 2)),
                                                windowClickByText("下一页"),
                                                action("goto_action", params("targetActionIndex", 3)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate npcDialogContainerTeleport() {
                return template("npc_dialog_container_teleport", CATEGORY_TELEPORT,
                                "NPC 对话容器传送",
                                "等待传送 NPC，右键打开对话/箱子菜单，再点击“第一层副本”槽位。",
                                "适合传送 NPC 右键后弹出容器菜单，而不是立即传送的场景。",
                                "如果 NPC 右键偶发失败，可使用“NPC 传送失败自动重试”模板作为外层保护。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "传送NPC", "radius", 8,
                                                                                "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("rightclickentity",
                                                                params("locatorMode", "NAME", "locatorText", "传送NPC",
                                                                                "locatorMatchMode", "CONTAINS", "pos",
                                                                                vec(0, 64, 0), "range", 4,
                                                                                "preserveView", true)),
                                                action("wait_until_gui_title",
                                                                params("title", "传送", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate containerTeleportRetryUntilArea() {
                return template("container_teleport_retry_until_area", CATEGORY_TELEPORT,
                                "容器传送直到到达区域",
                                "打开菜单、等标题、稳定 8 tick 后点击。3 秒内到达目标区域则继续，否则整段重试。",
                                "适合容器传送因网络抖动、菜单未加载、点击丢失导致偶发失败的场景。",
                                "timeoutTicks=60 约 3 秒。到达则 skip_actions 跳过 goto；超时才回到打开菜单。需要冷却检测时改用 dungeon_teleport_cooldown_area_retry。",
                                actions(
                                                label("teleport_start"),
                                                closeGui(),
                                                action("command", params("command", "/菜单")),
                                                waitGuiTitle("菜单", 200),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                waitInArea(0, 64, 0, 8, 60, 1),
                                                skipActions(1),
                                                gotoLabel("teleport_start"),
                                                action("system_message", params("message", "容器传送已确认到达"))));
        }

        private static ActionTemplate rightClickBlockTeleportRetry() {
                return template("rightclick_block_teleport_retry", CATEGORY_TELEPORT,
                                "右键传送牌/传送方块重试",
                                "右键指定方块后，3 秒内进入目标区域则继续；未到达就重新右键。",
                                "适合传送牌、传送门方块、NPC 旁按钮、传送石碑等方块交互传送。",
                                "不要用右键后固定 delay 再假设已经传送。把方块坐标和目标区域坐标换成实际值。",
                                actions(
                                                label("click_block"),
                                                action("rightclickblock",
                                                                params("locatorMode", "POSITION", "pos", vec(0, 64, 0),
                                                                                "range", 5, "preserveView", true)),
                                                waitInArea(100, 64, 100, 6, 60, 1),
                                                skipActions(1),
                                                gotoLabel("click_block"),
                                                action("system_message", params("message", "方块传送已确认到达"))));
        }

        private static ActionTemplate lineSwitchContainerTeleport() {
                return template("line_switch_container_teleport", CATEGORY_TELEPORT,
                                "换线/分线菜单传送",
                                "打开线路菜单，点击指定线路槽位，等待换线成功数据包，再等待目标 NPC 加载。",
                                "适合 /line、/server、频道选择器、分线大厅等容器式换线。",
                                "如果换线后没有 NPC，可把最后的 wait_until_entity_nearby 改成 wait_until_player_in_area。",
                                actions(
                                                action("command", params("command", "/line")),
                                                action("wait_until_gui_title",
                                                                params("title", "线路", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("1线"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "切换成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "传送NPC", "radius", 16,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate crossServerPacketTeleport() {
                return template("cross_server_packet_teleport", CATEGORY_TELEPORT,
                                "跨服/大厅传送等待回包",
                                "执行跨服指令后等待服务端回包或文本，再等待目标大厅关键 NPC 出现。",
                                "适合 /server、/lobby、小游戏大厅、跨服副本入口等切服传送。",
                                "capturedId 需要在抓包规则里提前配置；没有抓包规则时可删除 wait_until_captured_id。",
                                actions(
                                                action("command", params("command", "/server dungeon1")),
                                                action("wait_until_captured_id",
                                                                params("capturedId", "server_switch", "waitMode",
                                                                                "update", "preExecuteCount", 0,
                                                                                "timeoutTicks", 240, "timeoutSkipCount",
                                                                                1)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "进入服务器", "preExecuteCount", 0,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "传送NPC", "radius", 24,
                                                                                "timeoutTicks", 300,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate portalStandStillWaitTeleport() {
                return template("portal_stand_still_wait_teleport", CATEGORY_TELEPORT,
                                "站上传送门/压力板等待传送",
                                "到达路线点后原地等待触发传送，再等待传送数据包和目标区域。",
                                "适合压力板、区域触发、传送门、水流入口等不需要点击的传送点。",
                                "这个模板通常作为路径点动作：步骤坐标放在触发点上，动作只负责等待确认。",
                                actions(
                                                action("delay", params("ticks", "80", "normalizeDelayTo20Tps", true)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "传送", "preExecuteCount", 0,
                                                                                "timeoutTicks", 180,
                                                                                "timeoutSkipCount", 1)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate guiTeleportFailureTextRetry() {
                return template("gui_teleport_failure_text_retry", CATEGORY_TELEPORT,
                                "容器传送失败文本重试",
                                "等标题并稳定后再点击。若短时间内出现冷却/失败/繁忙文本则等待后重试；否则 3 秒内确认落点，未到达也重试。",
                                "适合服务器返回“传送失败、目标繁忙、冷却中、请稍后”的容器传送。",
                                "packetText 默认匹配“冷却”。可改成服务器原文片段。到点确认仍是最终成功条件，不要只凭成功聊天。",
                                actions(
                                                label("teleport_start"),
                                                closeGui(),
                                                action("command", params("command", "/菜单")),
                                                waitGuiTitle("菜单", 200),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                waitInArea(0, 64, 0, 8, 60, 1),
                                                skipActions(3),
                                                action("wait_until_packet_text",
                                                                params("packetText", "冷却", "preExecuteCount", 0,
                                                                                "timeoutTicks", 10,
                                                                                "timeoutSkipCount", 1)),
                                                action("delay", params("ticks", "40", "normalizeDelayTo20Tps", true)),
                                                gotoLabel("teleport_start"),
                                                action("system_message", params("message", "传送已确认到达"))));
        }

        private static ActionTemplate packetCapturedTeleportWait() {
                return template("packet_captured_teleport_wait", CATEGORY_TELEPORT,
                                "抓包确认传送结果",
                                "等待传送相关捕获 ID 更新，读取字段后再等待玩家进入目标区域。",
                                "适合服务器传送成功/失败只体现在数据包字段中，HUD/聊天没有可靠文本的场景。",
                                "需要先配置抓包规则 capturedId。字段读取成功后仍用区域等待做最终确认。",
                                actions(
                                                action("wait_until_captured_id",
                                                                params("capturedId", "teleport_result", "waitMode",
                                                                                "update", "preExecuteCount", 0,
                                                                                "timeoutTicks", 200, "timeoutSkipCount",
                                                                                0)),
                                                action("capture_packet_field",
                                                                params("varName", "sequence.teleport_result",
                                                                                "lookupMode",
                                                                                "LATEST_CAPTURE", "fieldKey", "status",
                                                                                "fallbackValue", "")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.teleport_result_found != true"),
                                                                                "skipCount", 1)),
                                                action("goto_action", params("targetActionIndex", 0)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate packetTextTeleportWait() {
                return template("packet_text_teleport_wait", CATEGORY_TELEPORT,
                                "数据包文本传送等待",
                                "点击或指令触发传送后，等待最近数据包文本包含“传送成功”，再等待区域。",
                                "适合聊天/HUD 不稳定，但抓包文本能看到传送提示的服务器。",
                                "如果有失败提示，可配合“数据包失败提示重试”模板。",
                                actions(
                                                action("wait_until_packet_text",
                                                                params("packetText", "传送成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 160,
                                                                                "timeoutSkipCount", 0)),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate npcTeleportRetry() {
                return template("npc_teleport_retry", CATEGORY_NPC,
                                "NPC 传送失败自动重试",
                                "等待 NPC 出现并右键后，3 秒内到达目标区域则继续；未到达则重新等待并点击。",
                                "适合 NPC 加载慢、网络抖动、右键传送偶发失败的地图传送点。",
                                "把 NPC 名称、传送后坐标改成实际值。不要用“NPC 还在附近”代替落点确认。",
                                actions(
                                                label("click_npc"),
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "传送NPC名称关键字", "radius", 8,
                                                                                "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("rightclickentity",
                                                                params("locatorMode", "NAME", "locatorText",
                                                                                "传送NPC名称关键字",
                                                                                "locatorMatchMode", "CONTAINS", "pos",
                                                                                vec(0, 64, 0), "range", 4,
                                                                                "preserveView", true)),
                                                waitInArea(0, 64, 0, 5, 60, 1),
                                                skipActions(1),
                                                gotoLabel("click_npc"),
                                                action("system_message", params("message", "NPC 传送已确认到达"))));
        }

        private static ActionTemplate waitNpcAndInteract() {
                return template("wait_npc_interact", CATEGORY_NPC,
                                "等待 NPC 出现并交互",
                                "等待指定 NPC 加载到附近，然后按名称右键交互。",
                                "适合换线、进图、传送后 NPC 延迟刷新的对话或商店入口。",
                                "rightclickentity 运行时仍需要 pos 字段，按名称定位时 pos 只作为占位；实际匹配依赖 locatorText 与 range。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "NPC 名称关键字", "radius", 8,
                                                                                "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("rightclickentity",
                                                                params("locatorMode", "NAME", "locatorText",
                                                                                "NPC 名称关键字",
                                                                                "locatorMatchMode", "CONTAINS", "pos",
                                                                                vec(0, 64, 0), "range", 4,
                                                                                "preserveView", true)),
                                                action("wait_until_gui_title",
                                                                params("title", "NPC", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle()));
        }

        private static ActionTemplate npcMultiStepDialogTeleport() {
                return template("npc_multi_step_dialog_teleport", CATEGORY_NPC,
                                "NPC 多级对话进入传送菜单",
                                "右键 NPC 后先点“传送”，再在二级容器菜单里点“第一层副本”。",
                                "适合 NPC 不是直接传送，而是先弹对话/功能菜单，再进入副本菜单的场景。",
                                "每层都使用 GUI 标题等待，能减少 NPC 菜单加载慢导致的误点。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "传送NPC", "radius", 8,
                                                                                "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("rightclickentity",
                                                                params("locatorMode", "NAME", "locatorText", "传送NPC",
                                                                                "locatorMatchMode", "CONTAINS", "pos",
                                                                                vec(0, 64, 0), "range", 4,
                                                                                "preserveView", true)),
                                                action("wait_until_gui_title",
                                                                params("title", "对话", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("传送"),
                                                action("wait_until_gui_title",
                                                                params("title", "传送", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_player_in_area",
                                                                params("center", vec(0, 64, 0), "radius", 8,
                                                                                "timeoutTicks", 240,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate batchDisassembleLowStatsByCommand() {
                return template("batch_disassemble_low_stats_command", CATEGORY_INVENTORY,
                                "批量分解低词条装备-指令打开",
                                "用指令打开分解界面，按表达式筛选低属性装备并批量移动到容器。",
                                "适合服务器分解炉、装备分解 NPC、带容器槽位的批量分解界面。",
                                "默认表达式示例为武器伤害 < 1 且暴击 < 2。不同服务器词条名可能不同，请按物品 tooltip/NBT 文本改表达式。",
                                actions(
                                                action("command", params("command", "/分解")),
                                                action("wait_until_gui_title",
                                                                params("title", "分解", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "(nameContains(\"剑\") || nameContains(\"刀\") || nameContains(\"弓\") || tooltip(\"武器\")) && tagNum(\"武器伤害\") < 1 && tagNum(\"暴击\") < 2")),
                                                action("click", params("locatorMode", "BUTTON_TEXT", "locatorText",
                                                                "分解",
                                                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left",
                                                                true)),
                                                action("wait_until_hud_text",
                                                                params("contains", "分解", "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate batchDisassembleLowStatsByMenu() {
                return template("batch_disassemble_low_stats_menu", CATEGORY_INVENTORY,
                                "批量分解低词条装备-菜单打开",
                                "先用菜单或主界面按钮进入分解界面，再执行低词条装备批量移动。",
                                "适合没有直接分解指令，需要从菜单按钮进入分解页的服务器。",
                                "把 /menu、按钮文本、GUI 标题和确认按钮文本替换成实际值。确认不是普通按钮时，可把 click 改成 window_click。",
                                actions(
                                                action("command", params("command", "/menu")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("click", params("locatorMode", "BUTTON_TEXT", "locatorText",
                                                                "分解",
                                                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left",
                                                                true)),
                                                action("wait_until_gui_title",
                                                                params("title", "分解", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "(tooltip(\"武器伤害\") || tooltip(\"暴击\")) && tagNum(\"武器伤害\") < 1 && tagNum(\"暴击\") < 2")),
                                                action("click", params("locatorMode", "BUTTON_TEXT", "locatorText",
                                                                "确认分解",
                                                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left",
                                                                true)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "分解成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate batchExchangeEquipment() {
                return template("batch_exchange_equipment", CATEGORY_INVENTORY,
                                "批量容器兑换/上交装备",
                                "打开兑换界面后，筛选可兑换装备并批量放入容器槽位，再点击兑换。",
                                "适合活动兑换、装备回收、批量上交材料或用装备换货币的界面。",
                                "默认过滤“可兑换/装备”且排除“绑定”。如果目标界面是箱子式按钮，请把确认 click 改为 window_click 槽位点击。",
                                actions(
                                                action("command", params("command", "/兑换")),
                                                action("wait_until_gui_title",
                                                                params("title", "兑换", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "(tooltip(\"可兑换\") || nameContains(\"装备\")) && !tooltip(\"绑定\")")),
                                                action("click", params("locatorMode", "BUTTON_TEXT", "locatorText",
                                                                "兑换",
                                                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left",
                                                                true)),
                                                action("wait_until_packet_text",
                                                                params("packetText", "兑换成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate batchSubmitMaterials() {
                return template("batch_submit_materials", CATEGORY_INVENTORY,
                                "批量上交材料/钥匙/碎片",
                                "打开上交界面，把碎片、钥匙、证明或标记为可提交的物品批量放入容器并确认。",
                                "适合活动任务、日常上交、副本门票兑换、材料提交型 NPC。",
                                "默认表达式偏保守，只匹配常见上交物。实际使用时把关键字改成服务器物品文本。",
                                actions(
                                                action("command", params("command", "/提交")),
                                                action("wait_until_gui_title",
                                                                params("title", "提交", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "nameContains(\"碎片\") || nameContains(\"钥匙\") || nameContains(\"证明\") || tooltip(\"可提交\")")),
                                                clickButtonByText("提交"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "提交成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate batchExchangeByStackSize() {
                return template("batch_exchange_by_stack_size", CATEGORY_INVENTORY,
                                "按堆叠数量批量兑换材料",
                                "只把数量达到阈值的材料堆放入兑换容器，避免把零散保留材料也交掉。",
                                "适合“满 32/64 个碎片换奖励”的活动兑换或材料合成界面。",
                                "表达式里的 count 是物品堆叠数量；阈值和材料名都需要按实际服务器调整。",
                                actions(
                                                action("command", params("command", "/兑换")),
                                                action("wait_until_gui_title",
                                                                params("title", "兑换", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "count >= 32 && (nameContains(\"碎片\") || nameContains(\"材料\") || tooltip(\"可兑换\"))")),
                                                clickButtonByText("兑换"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "兑换成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate depositLootToWarehouse() {
                return template("deposit_loot_to_warehouse", CATEGORY_INVENTORY,
                                "批量存入仓库/箱子",
                                "打开仓库后，把非绑定、非菜单、非消耗品的战利品批量移动到容器。",
                                "适合刷怪后清包、矿物/材料入库、自动回城整理背包。",
                                "表达式默认排除绑定、菜单、药水、传送卷。按自己的保留物品继续补充排除条件。",
                                actions(
                                                action("command", params("command", "/仓库")),
                                                action("wait_until_gui_title",
                                                                params("title", "仓库", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "!tooltip(\"绑定\") && !nameContains(\"菜单\") && !nameContains(\"药\") && !nameContains(\"传送\")")),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate withdrawSuppliesFromWarehouse() {
                return template("withdraw_supplies_from_warehouse", CATEGORY_INVENTORY,
                                "从仓库批量取补给",
                                "打开仓库后，从容器侧筛选药水、卷轴、食物等补给移动到背包。",
                                "适合进副本前自动补药、补传送卷、补食物。",
                                "moveDirection 是 CHEST_TO_INVENTORY，表达式匹配容器物品。目标背包槽位默认全背包。",
                                actions(
                                                action("command", params("command", "/仓库")),
                                                action("wait_until_gui_title",
                                                                params("title", "仓库", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveContainerToInventoryParams(
                                                                                "nameContains(\"药\") || nameContains(\"传送卷\") || nameContains(\"食物\")")),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate warehouseAutoDepositHighlights() {
                return template("warehouse_auto_deposit_highlights", CATEGORY_INVENTORY,
                                "仓库高亮槽位自动存入",
                                "打开仓库后使用仓库高亮/扫描结果执行自动存入，再关闭容器。",
                                "适合已经配置过仓库管理规则，需要复用现有高亮存入逻辑的清包流程。",
                                "warehouse_auto_deposit 依赖仓库功能已有扫描或高亮规则（通用-仓库管理中的仓库自动存入）；如果没有规则，改用批量移动表达式模板。",
                                actions(
                                                action("command", params("command", "/仓库")),
                                                action("wait_until_gui_title",
                                                                params("title", "仓库", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("warehouse_auto_deposit", params()),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate takeAllContainerAndClose() {
                return template("take_all_container_and_close", CATEGORY_INVENTORY,
                                "容器取完并关闭",
                                "等待箱子/奖励/邮件界面打开，安全快速取出全部物品，然后关闭容器。",
                                "适合领取副本奖励箱、邮件附件、活动箱子、临时仓库输出箱。",
                                "如果只想取部分物品，改用“从仓库批量取补给”或批量移动表达式模板。",
                                actions(
                                                action("wait_until_gui_title",
                                                                params("title", "奖励", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("take_all_items_safe", params("shiftQuickMove", true)),
                                                action("delay", params("ticks", "10", "normalizeDelayTo20Tps", true)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate rewardPagesTakeAll() {
                return template("reward_pages_take_all", CATEGORY_INVENTORY,
                                "多页奖励全部领取",
                                "在奖励容器中先取当前页，再点下一页继续取，最后关闭容器。",
                                "适合邮件附件、赛季奖励、活动分页奖励箱等多页容器。",
                                "默认示例取两页；更多页可复制“下一页 + take_all_items_safe”动作。",
                                actions(
                                                action("wait_until_gui_title",
                                                                params("title", "奖励", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("take_all_items_safe", params("shiftQuickMove", true)),
                                                windowClickByText("下一页"),
                                                action("delay", params("ticks", "15", "normalizeDelayTo20Tps", true)),
                                                action("take_all_items_safe", params("shiftQuickMove", true)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate conditionalRewardClaimSlot() {
                return template("conditional_reward_claim_slot", CATEGORY_INVENTORY,
                                "条件点击可领取奖励槽位",
                                "等待福利/奖励界面，只有指定槽位物品名包含“领取”时才点击。",
                                "适合每日签到、在线奖励、通行证奖励等固定槽位按钮，避免未达成时误点。",
                                "slot 默认 13，从 0 开始。按钮不是固定槽位时可改为 windowClickByText。",
                                actions(
                                                action("wait_until_gui_title",
                                                                params("title", "福利", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                conditionalWindowClickBySlot(13, "领取"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "领取成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 1)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate dropJunkByConfiguredFilter() {
                return template("drop_junk_by_configured_filter", CATEGORY_INVENTORY,
                                "按已配置过滤规则丢弃垃圾",
                                "调用已有丢弃过滤配置，把命中的垃圾物品丢出背包。",
                                "适合已有成熟丢弃规则，刷怪途中快速清理无价值掉落。",
                                "dropfiltereditems 使用当前全局过滤配置，不在模板里写表达式。高价值物品请优先用仓库/分解模板处理。",
                                actions(
                                                action("dropfiltereditems", params()),
                                                action("system_message", params("message", "已执行配置过滤丢弃"))));
        }

        private static ActionTemplate sellJunkEquipment() {
                return template("sell_junk_equipment", CATEGORY_INVENTORY,
                                "批量出售垃圾装备",
                                "打开商店/出售界面，按表达式筛选低价值装备放入出售容器并确认。",
                                "适合刷怪背包溢出、低品质装备自动卖店、临时清包。",
                                "默认示例排除绑定，匹配“普通/劣质/破损”。不同服务器品质文本不同，需要按 tooltip 修改。",
                                actions(
                                                action("command", params("command", "/商店")),
                                                action("wait_until_gui_title",
                                                                params("title", "出售", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("move_inventory_items_to_chest_slots",
                                                                moveInventoryToContainerParams(
                                                                                "(tooltip(\"普通\") || tooltip(\"劣质\") || tooltip(\"破损\")) && !tooltip(\"绑定\")")),
                                                clickButtonByText("出售"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "出售成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate repairEquipmentByMenu() {
                return template("repair_equipment_by_menu", CATEGORY_INVENTORY,
                                "菜单修理装备",
                                "打开修理/铁匠界面，点击修理按钮或槽位，等待成功提示。",
                                "适合进副本前补耐久、循环刷图中定期修理。",
                                "如果“修理”是容器物品按钮，把 clickButtonByText 改为 windowClickByText 或直接改模板动作。",
                                actions(
                                                action("command", params("command", "/repair")),
                                                action("wait_until_gui_title",
                                                                params("title", "修理", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                clickButtonByText("修理"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "修理成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate ensurePotionOrBuy() {
                return template("ensure_potion_or_buy", CATEGORY_SUPPORT,
                                "药水不足时自动购买",
                                "先判断背包药水数量；不足时打开商店购买，足够时跳过购买动作。",
                                "适合刷图前补给检查、挂机循环中自动买药。",
                                "药水足够时执行 skip_actions 跳过购买链；不足时 condition false 会跳过 skip_actions，进入购买链。",
                                actions(
                                                action("condition_inventory_item",
                                                                params("itemFilterExpressions",
                                                                                strings("nameContains(\"药水\")"),
                                                                                "count", 16, "skipCount", 1)),
                                                action("skip_actions", params("count", 4)),
                                                action("command", params("command", "/shop")),
                                                action("wait_until_gui_title",
                                                                params("title", "商店", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("药水"),
                                                action("close_container_window", params()),
                                                action("system_message", params("message", "药水数量检查完成"))));
        }

        private static ActionTemplate npcShopBuySupply() {
                return template("npc_shop_buy_supply", CATEGORY_SUPPORT,
                                "NPC 商店购买补给",
                                "等待商店 NPC，右键打开商店容器，点击药水/补给槽位并关闭。",
                                "适合补给 NPC 没有指令入口，必须通过实体交互购买的地图。",
                                "购买数量通常由服务器菜单槽位决定；需要多次购买时复制 window_click 或改用固定槽位点击。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "商店NPC", "radius", 8,
                                                                                "timeoutTicks", 0,
                                                                                "timeoutSkipCount", 0)),
                                                action("rightclickentity",
                                                                params("locatorMode", "NAME", "locatorText", "商店NPC",
                                                                                "locatorMatchMode", "CONTAINS", "pos",
                                                                                vec(0, 64, 0), "range", 4,
                                                                                "preserveView", true)),
                                                action("wait_until_gui_title",
                                                                params("title", "商店", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                windowClickByText("药水"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "购买", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 1)),
                                                action("close_container_window", params())));
        }

        private static ActionTemplate enableAutoEatBeforeFight() {
                return template("enable_auto_eat_before_fight", CATEGORY_SUPPORT,
                                "战斗前启用自动进食",
                                "打开自动进食，配置饥饿阈值和目标快捷栏，避免刷怪中断粮。",
                                "适合长时间刷怪、跑图、Boss 战前的状态准备。",
                                "食物关键字可按服务器食物名称补充。若已有全局配置，只需保留 autoeat 启用动作。",
                                actions(
                                                action("autoeat", params("enabled", true, "foodLevelThreshold", 12,
                                                                "autoMoveFoodEnabled", true, "eatWithLookDown", false,
                                                                "targetHotbarSlot", 9,
                                                                "foodKeywordsText", "面包\n牛排\n苹果"))));
        }

        private static ActionTemplate enableAutoEquipSet() {
                return template("enable_auto_equip_set", CATEGORY_SUPPORT,
                                "进入副本前启用套装自动穿戴",
                                "启用自动穿戴并指定套装名，让装备变更后自动补齐目标套装。",
                                "适合副本、Boss、PVP 前切换固定装备套装。",
                                "setName 需要和自动穿戴功能里配置的套装名称一致。",
                                actions(
                                                action("autoequip", params("enabled", true, "setName", "副本套装",
                                                                "smartActivation", true))));
        }

        private static ActionTemplate enablePickupDuringFarm() {
                return template("enable_pickup_during_farm", CATEGORY_SUPPORT,
                                "刷怪期间临时开启自动拾取",
                                "开启自动拾取，执行一段刷怪动作，再关闭自动拾取。",
                                "适合只希望在刷怪窗口内拾取掉落，传送和跑图时不拾取杂物的循环。",
                                "hunt 只是示例动作；也可以把中间替换成子序列或多段路线动作。",
                                actions(
                                                action("autopickup", params("enabled", true)),
                                                action("hunt", params("radius", 18, "huntUpRange", 6, "huntDownRange",
                                                                6,
                                                                "noTargetSkipCount", 0, "enableNameWhitelist", false,
                                                                "enableNameBlacklist", false, "showHuntRange", false)),
                                                action("delay", params("ticks", "40", "normalizeDelayTo20Tps", true)),
                                                action("autopickup", params("enabled", false))));
        }

        private static ActionTemplate toggleKillAuraForMobFarm() {
                return template("toggle_kill_aura_for_mob_farm", CATEGORY_SUPPORT,
                                "刷怪窗口临时开启杀戮光环",
                                "进入刷怪点后开启杀戮光环，等待一段时间后关闭。",
                                "适合纯定点刷怪、活动小怪清理、需要短时间自动攻击的步骤。",
                                "杀戮光环目标规则仍来自全局配置。高风险服务器建议优先用 hunt 白名单模板。",
                                actions(
                                                action("toggle_kill_aura", params("enabled", true)),
                                                action("delay", params("ticks", "200", "normalizeDelayTo20Tps", true)),
                                                action("toggle_kill_aura", params("enabled", false))));
        }

        private static ActionTemplate packetCaptureAndReadField() {
                return template("packet_capture_read_field", CATEGORY_PACKET,
                                "抓包等待并读取字段",
                                "等待指定捕获 ID 更新，再把最近包字段写入运行时变量并做成功分支判断。",
                                "适合需要从服务器数据包里读取结果、数值、状态字段后再继续动作链的场景。",
                                "先在抓包工具里配置捕获 ID。fieldKey 为空时读取最近字段；需要固定字段时填字段路径或变量键。",
                                actions(
                                                action("wait_until_captured_id",
                                                                params("capturedId", "捕获ID名称", "waitMode", "update",
                                                                                "preExecuteCount", 0, "timeoutTicks",
                                                                                200, "timeoutSkipCount", 0)),
                                                action("capture_packet_field", params("varName",
                                                                "sequence.packet_result", "lookupMode",
                                                                "LATEST_CAPTURE", "fieldKey", "", "fallbackValue", "")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.packet_result_found == true"),
                                                                                "skipCount", 1)),
                                                action("system_message", params("message", "抓包字段已捕获")),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true))));
        }

        private static ActionTemplate waitPacketText() {
                return template("wait_packet_text", CATEGORY_PACKET,
                                "等待数据包文本再继续",
                                "等待最近数据包文本包含指定片段，成功后继续执行后续动作。",
                                "适合服务器不发 HUD 文本、但抓包文本能看到成功/失败提示的等待点。",
                                "packetText 默认是“成功”。如果超时也要继续，可以设置 timeoutSkipCount 跳过后续失败处理动作。",
                                actions(
                                                action("wait_until_packet_text",
                                                                params("packetText", "成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0)),
                                                action("system_message", params("message", "检测到目标数据包文本"))));
        }

        private static ActionTemplate waitPacketFailureRetry() {
                return template("wait_packet_failure_retry", CATEGORY_PACKET,
                                "数据包失败提示重试",
                                "等待失败提示数据包；如果出现失败文本，就跳回前面动作重新执行。",
                                "适合点击传送、兑换、购买、交互后服务器偶发返回“失败/繁忙/请稍后”的场景。",
                                "把 targetActionIndex 改成需要重试的动作序号。若失败提示不存在，等待超时后继续后续动作。",
                                actions(
                                                action("wait_until_packet_text",
                                                                params("packetText", "失败", "preExecuteCount", 0,
                                                                                "timeoutTicks", 80,
                                                                                "timeoutSkipCount", 1)),
                                                action("goto_action", params("targetActionIndex", 0)),
                                                action("system_message", params("message", "未检测到失败提示，继续执行"))));
        }

        private static ActionTemplate sendPacketAndWaitAck() {
                return template("send_packet_and_wait_ack", CATEGORY_PACKET,
                                "发送自定义数据包并等待回包",
                                "发送 C2S 自定义包后，等待指定捕获 ID 更新作为 ACK。",
                                "适合服务器插件通道交互、需要手动发包触发功能并等待确认的高级场景。",
                                "channel、packetId、hex 必须按实际抓包结果填写。默认内容只是占位。",
                                actions(
                                                action("send_packet",
                                                                params("direction", "C2S", "channel", "minecraft:brand",
                                                                                "packetId", 0, "hex", "00")),
                                                action("wait_until_captured_id",
                                                                params("capturedId", "ack_packet", "waitMode",
                                                                                "update", "preExecuteCount", 0,
                                                                                "timeoutTicks", 200, "timeoutSkipCount",
                                                                                0)),
                                                action("capture_packet_field",
                                                                params("varName", "sequence.packet_ack", "lookupMode",
                                                                                "LATEST_CAPTURE", "fieldKey", "",
                                                                                "fallbackValue", ""))));
        }

        private static ActionTemplate guiConfirmClosedLoop() {
                return template("gui_confirm_closed_loop", CATEGORY_FLOW,
                                "等待 GUI 标题并点击确认",
                                "等待指定界面打开，稳定 8 tick 后再点确认，界面还在就重试点击。",
                                "适合确认购买、确认传送、确认兑换、二次确认弹窗。",
                                "如果确认按钮不是 GuiButton，请把 click 改为 window_click，并填槽位或物品文本定位。不要用打开后盲等再点。",
                                actions(
                                                action("wait_until_gui_title",
                                                                params("title", "确认", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("click", params("locatorMode", "BUTTON_TEXT", "locatorText",
                                                                "确认",
                                                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left",
                                                                true)),
                                                action("delay", params("ticks", "10", "normalizeDelayTo20Tps", true)),
                                                action("condition_gui_title", params("title", "确认", "skipCount", 1)),
                                                action("goto_action", params("targetActionIndex", 2)),
                                                action("wait_until_hud_text",
                                                                params("contains", "成功", "timeoutTicks", 120,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate guiElementRetryUntilAppears() {
                return template("gui_element_retry_until_appears", CATEGORY_FLOW,
                                "GUI 元素未出现就循环等待",
                                "采集按钮/槽位是否存在；存在就点击，不存在就延迟后回到采集动作。",
                                "适合菜单加载慢、按钮延迟出现、分页刷新、网络抖动导致元素短暂缺失的 GUI。",
                                "默认查找按钮“确认”。如果目标是容器槽位，把 elementType 改为 SLOT，点击动作改为 window_click。",
                                actions(
                                                action("command", params("command", "/menu")),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle(),
                                                action("capture_gui_element",
                                                                params("varName", "sequence.confirm_button",
                                                                                "elementType",
                                                                                "BUTTON", "guiElementLocatorMode",
                                                                                "TEXT", "locatorText", "确认",
                                                                                "locatorMatchMode", "CONTAINS")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.confirm_button_found == true"),
                                                                                "skipCount", 2)),
                                                clickButtonByText("确认"),
                                                action("skip_actions", params("count", 2)),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true)),
                                                action("goto_action", params("targetActionIndex", 3)),
                                                action("system_message", params("message", "GUI 元素已点击"))));
        }

        private static ActionTemplate restartSequenceOnBadState() {
                return template("restart_sequence_on_bad_state", CATEGORY_FLOW,
                                "检测异常状态后重头开始",
                                "采集记分板或状态文本，发现卡住/异常提示时执行 restart_sequence。",
                                "适合副本进入失败、路线卡住、任务状态异常，需要从序列开头重新跑的场景。",
                                "表达式只是示例。也可以改成抓包字段、HUD 文本或玩家区域条件。",
                                actions(
                                                action("capture_scoreboard",
                                                                params("varName", "sequence.scoreboard", "lineIndex",
                                                                                -1)),
                                                action("condition_expression",
                                                                params("expressions",
                                                                                strings("contains(sequence.scoreboard_joined, \"异常\") || contains(sequence.scoreboard_joined, \"卡住\")"),
                                                                                "skipCount", 1)),
                                                action("restart_sequence", params()),
                                                action("system_message", params("message", "未检测到异常状态"))));
        }

        private static ActionTemplate repeatActionsBlock() {
                return template("repeat_actions_block", CATEGORY_FLOW,
                                "重复执行后续动作块",
                                "用 repeat_actions 把后面两个动作重复执行 3 次，并写入循环变量。",
                                "适合固定次数点击、固定次数等待/搜怪、固定次数尝试入口等短循环。",
                                "repeat_actions 会重复当前动作后面的 bodyCount 个动作；插入后可把示例动作替换成真实动作。",
                                actions(
                                                action("repeat_actions", params("count", 3, "bodyCount", 2, "loopVar",
                                                                "sequence.repeat_i")),
                                                action("system_message", params("message", "重复动作执行中")),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true)),
                                                action("system_message", params("message", "重复动作结束"))));
        }

        private static ActionTemplate skipDailyIfScoreboardDone() {
                return template("skip_daily_if_scoreboard_done", CATEGORY_FLOW,
                                "记分板显示已完成则跳过当前步骤",
                                "采集记分板文本，若包含“今日已完成”就结束当前步骤，避免重复执行日常。",
                                "适合日常任务、活动次数、Boss 次数等通过记分板显示进度的服务器。",
                                "skip_steps count=0 表示结束当前步骤进入下一步骤；需要跳过更多步骤时改 count。",
                                actions(
                                                action("capture_scoreboard",
                                                                params("varName", "sequence.scoreboard", "lineIndex",
                                                                                -1)),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "contains(sequence.scoreboard_joined, \"今日已完成\")"),
                                                                                "skipCount", 1)),
                                                action("skip_steps", params("count", 0)),
                                                action("system_message", params("message", "日常未完成，继续执行"))));
        }

        private static ActionTemplate expressionBranchLoop() {
                return template("expression_branch_loop", CATEGORY_FLOW,
                                "表达式条件循环骨架",
                                "用条件表达式决定是否继续循环，失败时跳过 goto 并退出。",
                                "适合变量计数、背包数量、抓包结果、冷却时间等纯变量控制的动作链。",
                                "把表达式换成自己的退出条件。插入已有步骤时，goto 目标会自动偏移到模板插入位置。",
                                actions(
                                                action("set_var",
                                                                params("name", "sequence.loop_count", "value", 0,
                                                                                "valueType",
                                                                                "number")),
                                                action("condition_expression",
                                                                params("expressions",
                                                                                strings("sequence.loop_count < 3"),
                                                                                "skipCount", 3)),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true)),
                                                action("set_var", params("name", "sequence.loop_count", "expression",
                                                                "sequence.loop_count + 1", "valueType", "number")),
                                                action("goto_action", params("targetActionIndex", 1)),
                                                action("system_message", params("message", "表达式循环结束"))));
        }

        private static ActionTemplate waitCombinedAnyReady() {
                return template("wait_combined_any_ready", CATEGORY_FLOW,
                                "组合等待-任一条件满足",
                                "同时等待多个表达式条件，任意一个满足就继续。",
                                "适合等待 GUI、变量、数据包、区域等多个信号中的任意一个先到达。",
                                "conditionsText 用分号或换行分隔表达式。根据实际变量名替换示例条件。",
                                actions(
                                                action("wait_combined", params("combinedMode", "ANY", "conditionsText",
                                                                "sequence.packet_result_found == true; sequence.gui_ready == true",
                                                                "cancelExpression", "", "preExecuteCount", 0,
                                                                "timeoutTicks", 200,
                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate waitCombinedAllReady() {
                return template("wait_combined_all_ready", CATEGORY_FLOW,
                                "组合等待-全部条件满足",
                                "同时等待多个表达式条件，全部满足后再继续执行后续动作。",
                                "适合传送后既要确认落点，又要确认 GUI/数据包/变量都准备好的严格场景。",
                                "conditionsText 用分号或换行分隔表达式。建议只放能稳定变为 true 的条件，避免永久等待。",
                                actions(
                                                action("wait_combined", params("combinedMode", "ALL", "conditionsText",
                                                                "sequence.packet_ready == true; sequence.area_ready == true",
                                                                "cancelExpression", "", "preExecuteCount", 0,
                                                                "timeoutTicks", 240,
                                                                "timeoutSkipCount", 0)),
                                                action("system_message", params("message", "组合条件已全部满足"))));
        }

        private static ActionTemplate captureNearbyEntityBranch() {
                return template("capture_nearby_entity_branch", CATEGORY_CAPTURE,
                                "采集附近实体并分支",
                                "采集附近实体到变量，再用表达式判断是否找到目标实体。",
                                "适合 NPC/怪物是否加载、是否需要等待、是否需要换线的判断。",
                                "capture_nearby_entity 会产出 sequence.target_entity_found 等变量；找不到时跳过提示动作。",
                                actions(
                                                action("capture_nearby_entity",
                                                                params("varName", "sequence.target_entity",
                                                                                "entityName", "目标名称", "radius", 8)),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.target_entity_found == true"),
                                                                                "skipCount", 1)),
                                                action("system_message", params("message", "目标实体已找到")),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true))));
        }

        private static ActionTemplate captureHotbarMenuAndUse() {
                return template("capture_hotbar_menu_and_use", CATEGORY_CAPTURE,
                                "采集快捷栏并使用菜单",
                                "先采集快捷栏文本；如果快捷栏已有“菜单”就直接使用，否则从背包移动到快捷栏后再使用。",
                                "适合菜单物品有时在快捷栏、有时在背包里，想用同一模板兼容两种状态。",
                                "变量 sequence.hotbar_names 是快捷栏物品名列表。目标快捷栏默认 9，可按习惯调整。",
                                actions(
                                                action("capture_hotbar", params("varName", "sequence.hotbar")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "contains(sequence.hotbar_names, \"菜单\")"),
                                                                                "skipCount", 2)),
                                                action("use_hotbar_item",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "useMode", "RIGHT_CLICK",
                                                                                "changeLocalSlot", true, "count", 1,
                                                                                "switchItemDelayTicks", 0,
                                                                                "switchDelayTicks", 1,
                                                                                "switchBackDelayTicks", 0,
                                                                                "intervalTicks", 0)),
                                                action("skip_actions", params("count", 2)),
                                                action("move_inventory_item_to_hotbar",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "targetHotbarSlot", 9)),
                                                action("use_hotbar_item",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "useMode", "RIGHT_CLICK",
                                                                                "changeLocalSlot", true, "count", 1,
                                                                                "switchItemDelayTicks", 0,
                                                                                "switchDelayTicks", 1,
                                                                                "switchBackDelayTicks", 0,
                                                                                "intervalTicks", 0)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                guiSettle()));
        }

        private static ActionTemplate captureGuiElementThenClick() {
                return template("capture_gui_element_then_click", CATEGORY_CAPTURE,
                                "采集 GUI 元素后点击",
                                "先采集按钮/槽位元素信息，再判断是否存在，存在后按按钮文本点击。",
                                "适合 GUI 加载慢、按钮偶发不出现、需要确认按钮存在再点击的场景。",
                                "locatorText 要和按钮或槽位文本一致。按钮不是 GuiButton 时，把 click 改成 window_click。",
                                actions(
                                                action("capture_gui_element",
                                                                params("varName", "sequence.confirm_button",
                                                                                "elementType",
                                                                                "BUTTON", "guiElementLocatorMode",
                                                                                "TEXT", "locatorText", "确认",
                                                                                "locatorMatchMode", "CONTAINS")),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.confirm_button_found == true"),
                                                                                "skipCount", 1)),
                                                clickButtonByText("确认"),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true))));
        }

        private static ActionTemplate captureScoreboardDailyDone() {
                return template("capture_scoreboard_daily_done", CATEGORY_CAPTURE,
                                "采集记分板判断日常完成",
                                "读取记分板指定行或全部文本，判断是否包含完成标记。",
                                "适合任务进度、活动次数、Boss 剩余次数显示在计分板的服务器。",
                                "默认采集全部行。表达式中的文本按实际记分板内容调整。",
                                actions(
                                                action("capture_scoreboard",
                                                                params("varName", "sequence.scoreboard", "lineIndex",
                                                                                -1)),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "contains(sequence.scoreboard_joined, \"已完成\")"),
                                                                                "skipCount", 1)),
                                                action("system_message", params("message", "记分板显示任务已完成"))));
        }

        private static ActionTemplate captureScreenRegionColorWait() {
                return template("capture_screen_region_color_wait", CATEGORY_CAPTURE,
                                "屏幕区域颜色等待",
                                "等待指定屏幕区域平均颜色接近目标颜色，用于无文本图标状态判断。",
                                "适合按钮高亮、冷却完成图标、加载完成图标、图像状态变化。",
                                "regionRect 是 [x,y,w,h]。targetColor 和 colorTolerance 需要按实际截图调试。",
                                actions(
                                                action("wait_until_screen_region",
                                                                params("regionRect", rect(0, 0, 50, 50),
                                                                                "visionCompareMode", "AVERAGE_COLOR",
                                                                                "targetColor", "#FFFFFF",
                                                                                "colorTolerance", 48, "imagePath", "",
                                                                                "similarityThreshold", 0.92,
                                                                                "edgeThreshold", 0.12, "timeoutTicks",
                                                                                200, "timeoutSkipCount", 0)),
                                                action("capture_screen_region",
                                                                params("varName", "sequence.region_state", "regionRect",
                                                                                rect(0, 0, 50, 50)))));
        }

        private static ActionTemplate captureBlockChangeWait() {
                return template("capture_block_change_wait", CATEGORY_CAPTURE,
                                "采集方块状态直到变化",
                                "持续采集指定方块；只要它还不是空气就等待并重试，变为空气后继续。",
                                "适合等待门打开、方块消失、屏障解除、红石机关完成等地图机制。",
                                "默认判断 registry 是否为 minecraft:air。若要等待变成传送门或其他方块，修改表达式即可。",
                                actions(
                                                action("capture_block_at",
                                                                params("varName", "sequence.target_block", "pos",
                                                                                vec(0, 64, 0))),
                                                action("condition_expression",
                                                                params("expressions", strings(
                                                                                "sequence.target_block_registry != \"minecraft:air\""),
                                                                                "skipCount", 2)),
                                                action("delay", params("ticks", "20", "normalizeDelayTo20Tps", true)),
                                                action("goto_action", params("targetActionIndex", 0)),
                                                action("system_message", params("message", "目标方块状态已变化"))));
        }

        private static ActionTemplate runSubSequenceForeground() {
                return template("run_sub_sequence_foreground", CATEGORY_SEQUENCE,
                                "执行子序列并返回",
                                "在当前动作链中运行另一个路径序列，完成后回到当前序列继续。",
                                "适合把补给、清包、修理、传送等共用流程拆成独立子序列复用。",
                                "sequenceName 需要改成实际存在的序列名。默认每次执行一次。",
                                actions(
                                                action("run_sequence",
                                                                params("sequenceName", "子序列名称", "executeMode", "always",
                                                                                "executeEveryCount", 1,
                                                                                "backgroundExecution", false))));
        }

        private static ActionTemplate runSubSequenceBackground() {
                return template("run_sub_sequence_background", CATEGORY_SEQUENCE,
                                "后台启动辅助子序列",
                                "后台运行一个辅助序列，不阻塞当前主序列继续执行。",
                                "适合后台监控、自动补给、周期性状态检查、辅助采集。",
                                "后台序列会与资源锁交互，必要时在主序列里设置不打断其他序列或锁冲突策略。",
                                actions(
                                                action("run_sequence", params("sequenceName", "后台辅助序列", "executeMode",
                                                                "always",
                                                                "executeEveryCount", 1, "backgroundExecution", true)),
                                                action("system_message", params("message", "后台辅助序列已启动"))));
        }

        private static ActionTemplate runSubSequenceInterval() {
                return template("run_sub_sequence_interval", CATEGORY_SEQUENCE,
                                "每 N 次执行一次子序列",
                                "按间隔模式调用子序列，例如主循环每执行 5 次才补给/清包一次。",
                                "适合定期修理、定期清包、定期回城补给、定期状态检测。",
                                "executeEveryCount 默认 5。sequenceName 需要替换为实际存在的序列名。",
                                actions(
                                                action("run_sequence", params("sequenceName", "定期补给清包", "executeMode",
                                                                "interval",
                                                                "executeEveryCount", 5, "backgroundExecution", false)),
                                                action("system_message", params("message", "已检查是否需要执行定期子序列"))));
        }

        private static ActionTemplate stopBackgroundSequence() {
                return template("stop_background_sequence", CATEGORY_SEQUENCE,
                                "停止后台序列",
                                "停止当前后台/前台范围内正在运行的序列，用于清理辅助任务。",
                                "适合副本结束、回城、切换任务前关闭后台监控或自动辅助序列。",
                                "targetScope 默认 background，只停止后台序列；需要停止前台时改为 foreground。",
                                actions(
                                                action("stop_current_sequence", params("targetScope", "background")),
                                                action("system_message", params("message", "已请求停止后台序列"))));
        }

        private static ActionTemplate pointOccupiedSkipNextPoint() {
                return template("point_occupied_skip_next", CATEGORY_FLOW,
                                "点位被占用就跳下一个",
                                "检测当前点位附近是否有玩家；如果有人占点，就直接跳到当前步骤里的下一个标签。",
                                "适合 Boss 点、挂机点、采集点轮巡。",
                                "把 targetLabel 改成你下一点的标签名；半径按实际点位大小调整。",
                                actions(
                                                action("condition_entity_nearby",
                                                                params("entityType", "player", "radius", 8, "minCount", 1,
                                                                                "skipCount", 1)),
                                                action("goto_label", params("targetLabel", "next_point"))));
        }

        private static ActionTemplate bossPatrolAcrossPoints() {
                return template("boss_patrol_across_points", CATEGORY_COMBAT,
                                "Boss 多点轮巡",
                                "按标签在多个 Boss 点之间轮巡；有玩家就跳点，没玩家再等 Boss 或搜怪。",
                                "适合固定刷新点多、需要避开挂机玩家的地图。",
                                "把每个 label、坐标、Boss 名称关键字改成实际值。后续新增点时继续照着复制 label 和 goto_label 即可。",
                                actions(
                                                action("label", params("labelName", "point_a")),
                                                action("condition_entity_nearby",
                                                                params("entityType", "player", "radius", 8, "minCount", 1,
                                                                                "skipCount", 1)),
                                                action("goto_label", params("targetLabel", "point_b")),
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "Boss 名称关键字", "radius", 24,
                                                                                "minCount", 1, "timeoutTicks", 60,
                                                                                "timeoutSkipCount", 0)),
                                                action("hunt", params("radius", 24, "entityType", "hostile",
                                                                "huntUpRange", 8, "huntDownRange", 8,
                                                                "enableNameWhitelist", true,
                                                                "nameWhitelistText", "Boss 名称关键字",
                                                                "showHuntRange", true)),
                                                action("goto_label", params("targetLabel", "point_b")),
                                                action("label", params("labelName", "point_b")),
                                                action("condition_entity_nearby",
                                                                params("entityType", "player", "radius", 8, "minCount", 1,
                                                                                "skipCount", 1)),
                                                action("goto_label", params("targetLabel", "point_a"))));
        }

        private static ActionTemplate playerDetectedRetreat() {
                return template("player_detected_retreat", CATEGORY_FLOW,
                                "发现玩家就撤离/换线",
                                "当附近检测到玩家时，立即执行撤离动作或切到后续换线标签。",
                                "适合挂机避人、稀有怪抢点、敏感区域脚本。",
                                "把 targetLabel 改成你的撤离、换线或停止逻辑标签。",
                                actions(
                                                action("condition_entity_nearby",
                                                                params("entityType", "player", "radius", 10, "minCount", 1,
                                                                                "skipCount", 1)),
                                                action("goto_label", params("targetLabel", "retreat_or_switch"))));
        }

        private static ActionTemplate targetMissingRetryNextPoint() {
                return template("target_missing_retry_next_point", CATEGORY_FLOW,
                                "目标不存在则重试后换点",
                                "等待目标一小段时间；若仍未出现，则跳去当前步骤内的下一个点位标签。",
                                "适合传送后 NPC 未加载、Boss 未刷新、交互实体偶发缺失。",
                                "把 entityName 和 targetLabel 改成实际值；timeoutTicks 控制本点等待时长。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "目标名称关键字", "radius", 16,
                                                                                "minCount", 1, "timeoutTicks", 80,
                                                                                "timeoutSkipCount", 1)),
                                                action("goto_label", params("targetLabel", "next_point"))));
        }

        private static ActionTemplate waitRespawnKillContinue() {
                return template("wait_respawn_kill_continue", CATEGORY_COMBAT,
                                "等待复活 -> 击杀 -> 继续",
                                "在当前点等待目标刷新，击杀后立刻继续后续动作。",
                                "适合刷单个精英、守定点 Boss、等刷新后打一轮就走。",
                                "把实体名称、半径和后续逻辑改成实际值；若击杀确认要更稳，可改成等待数据包文本或计分板文本。",
                                actions(
                                                action("wait_until_entity_nearby",
                                                                params("entityName", "Boss 名称关键字", "radius", 24,
                                                                                "minCount", 1, "timeoutTicks", 200,
                                                                                "timeoutSkipCount", 0)),
                                                action("hunt", params("radius", 24, "entityType", "hostile",
                                                                "huntUpRange", 8, "huntDownRange", 8,
                                                                "enableNameWhitelist", true,
                                                                "nameWhitelistText", "Boss 名称关键字",
                                                                "showHuntRange", true))));
        }

        private static ActionTemplate safeMenuReopenRecovery() {
                return template("safe_menu_reopen_recovery", CATEGORY_FLOW,
                                "安全菜单重开恢复",
                                "如果菜单没开出来或意外关闭，就重试打开并等待 GUI 标题恢复。",
                                "适合传送菜单、商店菜单、分页奖励菜单等偶发失效场景。",
                                "把 use_hotbar_item 的菜单名和 wait_until_gui_title 的标题改成实际值。",
                                actions(
                                                action("condition_gui_title",
                                                                params("title", "菜单", "skipCount", 2)),
                                                action("use_hotbar_item",
                                                                params("itemName", "菜单", "matchMode", "CONTAINS",
                                                                                "useMode", "RIGHT_CLICK",
                                                                                "changeLocalSlot", true, "count", 1,
                                                                                "switchDelayTicks", 1)),
                                                action("wait_until_gui_title",
                                                                params("title", "菜单", "timeoutTicks", 80,
                                                                                "timeoutSkipCount", 0))));
        }

        private static ActionTemplate retryTargetScanThenNextPoint() {
                return template("retry_target_scan_then_next_point", CATEGORY_FLOW,
                                "重试扫描目标，失败后换点",
                                "重复扫描附近目标；命中就继续当前点，重试耗尽后跳到下一个标签。",
                                "适合传送后 NPC/Boss 偶尔没刷出来，需要本地重试几轮再换点。",
                                "把目标名称、半径和 next_point 标签改成你的实际脚本。",
                                actions(
                                                action("retry_block",
                                                                params("conditionsText",
                                                                                "sequence.target_scan_found == true",
                                                                                "bodyCount", 2, "retryCount", 3,
                                                                                "retryDelayTicks", 10, "attemptVar",
                                                                                "sequence.target_retry")),
                                                action("capture_nearby_entity",
                                                                params("varName", "sequence.target_scan", "entityName",
                                                                                "目标名称关键字", "radius", 16)),
                                                action("delay", params("ticks", 5)),
                                                action("condition_expression",
                                                                params("expressions",
                                                                                strings("sequence.target_scan_found == true"),
                                                                                "skipCount", 1)),
                                                action("goto_label", params("targetLabel", "next_point"))));
        }

        private static ActionTemplate switchRouteByStateVar() {
                return template("switch_route_by_state_var", CATEGORY_FLOW,
                                "按状态变量切换路线",
                                "根据一个状态变量值，跳入不同动作块。",
                                "适合根据前置采集结果、模式变量、地图状态在一条步骤里走不同分支。",
                                "sourceVar 填变量名；casesText 按 值=动作数 配置。当前模板示例为 farm / sell / default 三路。",
                                actions(
                                                action("switch_var",
                                                                params("sourceVar", "sequence.route_state",
                                                                                "casesText", "farm=2\nsell=2",
                                                                                "defaultCount", 1)),
                                                action("system_message", params("message", "进入刷怪路线")),
                                                action("goto_label", params("targetLabel", "farm_route")),
                                                action("system_message", params("message", "进入出售路线")),
                                                action("goto_label", params("targetLabel", "sell_route")),
                                                action("system_message", params("message", "进入默认路线"))));
        }

        private static ActionTemplate branchByPacketFieldStatus() {
                return template("branch_by_packet_field_status", CATEGORY_PACKET,
                                "按包字段状态分支",
                                "先读取最近包字段，再根据状态值进入不同动作块。",
                                "适合副本确认包、商店购买回执、活动状态 ACK 等场景。",
                                "把 fieldKey 和各分支值改成你的抓包规则名或变量键。",
                                actions(
                                                action("capture_packet_field",
                                                                params("varName", "sequence.packet_status",
                                                                                "lookupMode", "LATEST_CAPTURE",
                                                                                "fieldKey", "status", "fallbackValue",
                                                                                "")),
                                                action("switch_var",
                                                                params("sourceVar", "sequence.packet_status_value_text",
                                                                                "casesText", "ok=1\nretry=1",
                                                                                "defaultCount", 1)),
                                                action("system_message", params("message", "包字段状态: 成功")),
                                                action("system_message", params("message", "包字段状态: 需要重试")),
                                                action("system_message", params("message", "包字段状态: 未知"))));
        }

        private static ActionTemplate ifElsePlayerDetectedSwitchLine() {
                return template("if_else_player_detected_switch_line", CATEGORY_FLOW,
                                "If/Else 检测玩家并切线",
                                "通过 if_else 块判断附近是否有玩家，有就执行换线块，没有就执行继续刷点块。",
                                "适合避人挂机、Boss 点轮巡、资源点保护。",
                                "把表达式里的变量名、动作块内容改成你的实际逻辑。",
                                actions(
                                                action("capture_entity_list",
                                                                params("varName", "sequence.nearby_players",
                                                                                "entityType", "player", "radius", 10,
                                                                                "maxCount", 8)),
                                                action("if_else",
                                                                params("conditionsText",
                                                                                "sequence.nearby_players_player_count > 0",
                                                                                "thenCount", 2, "elseCount", 1)),
                                                action("system_message", params("message", "附近有玩家，准备换线")),
                                                action("goto_label", params("targetLabel", "switch_line")),
                                                action("system_message", params("message", "附近无人，继续当前点"))));
        }

        private static ActionTemplate forEachPointPatrolCheck() {
                return template("for_each_point_patrol_check", CATEGORY_FLOW,
                                "遍历点列表轮巡检测",
                                "按给定点列表逐个执行动作块，可配合区域检测、占点检测、调试输出使用。",
                                "适合 Boss 多点轮巡、挂机点巡逻、资源点轮扫。",
                                "把 pointsText 改成实际点位，动作块改成你的检测/传送/交互逻辑。",
                                actions(
                                                action("for_each_point",
                                                                params("pointsText",
                                                                                "[0,64,0]\n[10,64,10]\n[20,64,20]",
                                                                                "bodyCount", 2, "pointVar",
                                                                                "sequence.scan_point", "indexVar",
                                                                                "sequence.scan_point_index")),
                                                action("condition_player_in_area",
                                                                params("center", "sequence.scan_point", "radius", 4,
                                                                                "skipCount", 1)),
                                                action("system_message", params("message", "命中巡逻点位"))));
        }

        private static ActionTemplate forEachListBatchNotify() {
                return template("for_each_list_batch_notify", CATEGORY_FLOW,
                                "遍历列表批量处理",
                                "把一个列表变量逐项展开执行动作块。",
                                "适合先 capture_entity_list / capture_scoreboard，再逐项处理或逐项打印。",
                                "先把 sourceVar 改成你的列表变量，例如 sequence.targets_list。",
                                actions(
                                                action("for_each_list",
                                                                params("sourceVar", "sequence.targets_list",
                                                                                "bodyCount", 1, "itemVar",
                                                                                "sequence.current_item", "indexVar",
                                                                                "sequence.current_item_index")),
                                                action("system_message", params("message", "处理列表当前项"))));
        }

        private static ActionTemplate whileConditionUntilAreaClear() {
                return template("while_condition_until_area_clear", CATEGORY_FLOW,
                                "条件循环直到区域清空",
                                "只要条件还成立就重复执行动作块，直到条件失败或达到最大次数。",
                                "适合守点清怪、持续检测区域内玩家、持续等待某状态消失。",
                                "把循环条件和动作块改成你的实际逻辑；maxLoops=0 表示不限次数。",
                                actions(
                                                action("capture_entity_list",
                                                                params("varName", "sequence.area_entities",
                                                                                "entityType", "hostile", "radius", 12,
                                                                                "maxCount", 16)),
                                                action("while_condition",
                                                                params("conditionsText",
                                                                                "sequence.area_entities_hostile_count > 0",
                                                                                "bodyCount", 2, "maxLoops", 5,
                                                                                "loopVar", "sequence.clear_loop")),
                                                action("hunt", params("radius", 12, "entityType", "hostile",
                                                                "huntUpRange", 6, "huntDownRange", 6,
                                                                "showHuntRange", true)),
                                                action("capture_entity_list",
                                                                params("varName", "sequence.area_entities",
                                                                                "entityType", "hostile", "radius", 12,
                                                                                "maxCount", 16))));
        }

        private static ActionTemplate guiTitleSettleThenClick() {
                return template("gui_title_settle_then_click", CATEGORY_FLOW,
                                "等待 GUI 标题、稳定后再点击",
                                "打开界面后先等标题，再等 8 tick 让槽位加载，确认目标槽位出现后点击。不要用“右键后延迟 1 秒再点”。",
                                "任何箱子、菜单、确认窗、商店点击都应使用这个骨架，再换成实际标题和槽位文本。",
                                "8 tick 是 20 TPS 下约 0.4 秒。槽位加载更慢时，把 wait_until_gui_element 的 timeoutTicks 加大，不要改成数秒盲等。",
                                actions(
                                                action("command", params("command", "/菜单")),
                                                waitGuiTitle("菜单", 200),
                                                guiSettle(),
                                                waitGuiSlotText("确认", 80),
                                                windowClickByText("确认"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "成功", "preExecuteCount", 0,
                                                                                "timeoutTicks", 80,
                                                                                "timeoutSkipCount", 1)),
                                                action("system_message", params("message", "GUI 点击已发出，请用结果等待确认"))));
        }

        private static ActionTemplate openWorldChestWaitThenLoot() {
                return template("open_world_chest_wait_then_loot", CATEGORY_INVENTORY,
                                "右键箱子：等标题稳定后再取物",
                                "右键方块后等待箱子标题，稳定 8 tick，确认界面还在再全部取出并关闭。界面没开出来就整段重试。",
                                "适合战利品箱、奖励箱、世界箱子。不要写成右键后固定延迟再点击槽位。",
                                "把 pos 和标题换成实际箱子。原版标题可能是 Chest 或箱子；服务器自定义标题用 mythos_snapshot 的 gui 组确认。",
                                actions(
                                                label("open_chest"),
                                                action("rightclickblock",
                                                                params("locatorMode", "POSITION", "pos", vec(0, 64, 0),
                                                                                "range", 5, "preserveView", true)),
                                                action("wait_until_gui_title",
                                                                params("title", "箱子", "timeoutTicks", 60,
                                                                                "timeoutSkipCount", 1)),
                                                skipActions(1),
                                                gotoLabel("open_chest"),
                                                guiSettle(),
                                                action("take_all_items_safe", params("shiftQuickMove", true)),
                                                guiSettle(),
                                                closeGui()));
        }

        private static ActionTemplate dungeonTeleportCooldownAreaRetry() {
                return template("dungeon_teleport_cooldown_area_retry", CATEGORY_TELEPORT,
                                "副本传送：冷却检测 + 3 秒到点确认",
                                "关闭旧界面、打开菜单、等标题、稳定 8 tick、等槽位出现再点击。3 秒内到达目标区域则成功；否则检查冷却/失败文本，整段重试，最多 8 次。",
                                "菜单点传送、副本入口、有“冷却中/请稍后”提示的服务器都应优先用这个骨架，而不是点击后盲等。",
                                "先用 mythos_chat 和 mythos_snapshot 确认真实标题、槽位文本、冷却原文和落点。timeoutTicks=60 在 20 TPS 下约 3 秒。把“冷却”换成服务器完整提示中的稳定片段。",
                                actions(
                                                action("retry_block",
                                                                params("conditionsText",
                                                                                "abs(player_x - 0) <= 8 && abs(player_y - 64) <= 8 && abs(player_z - 0) <= 8",
                                                                                "bodyCount", 10, "retryCount", 8,
                                                                                "retryDelayTicks", 10, "attemptVar",
                                                                                "sequence.teleport_retry")),
                                                closeGui(),
                                                action("command", params("command", "/菜单")),
                                                waitGuiTitle("菜单", 200),
                                                action("condition_gui_title",
                                                                params("title", "菜单", "skipCount", 6)),
                                                guiSettle(),
                                                waitGuiSlotText("第一层副本", 80),
                                                windowClickByText("第一层副本"),
                                                action("wait_until_packet_text",
                                                                params("packetText", "冷却", "preExecuteCount", 0,
                                                                                "timeoutTicks", 10,
                                                                                "timeoutSkipCount", 1)),
                                                action("delay", params("ticks", "40", "normalizeDelayTo20Tps", true)),
                                                waitInArea(0, 64, 0, 8, 50, 0)));
        }

        private static JsonObject moveInventoryToContainerParams(String expression) {
                return params("delayTicks", 2, "normalizeDelayTo20Tps", true,
                                "chestRows", 6, "chestCols", 9, "inventoryRows", 4, "inventoryCols", 9,
                                "moveDirection", "INVENTORY_TO_CHEST", "button", 0, "clickType", "PICKUP",
                                "chestSlots", intRange(0, 26), "inventorySlots", intRange(0, 35),
                                "itemFilterExpressions", strings(expression));
        }

        private static JsonObject moveContainerToInventoryParams(String expression) {
                return params("delayTicks", 2, "normalizeDelayTo20Tps", true,
                                "chestRows", 6, "chestCols", 9, "inventoryRows", 4, "inventoryCols", 9,
                                "moveDirection", "CHEST_TO_INVENTORY", "button", 0, "clickType", "PICKUP",
                                "chestSlots", intRange(0, 53), "inventorySlots", intRange(0, 35),
                                "itemFilterExpressions", strings(expression));
        }

        private static ActionData guiSettle() {
                return action("delay", params("ticks", "8", "normalizeDelayTo20Tps", true));
        }

        private static ActionData waitGuiTitle(String title, int timeoutTicks) {
                return action("wait_until_gui_title", params("title", title, "timeoutTicks", timeoutTicks,
                                "timeoutSkipCount", 0));
        }

        private static ActionData waitGuiSlotText(String text, int timeoutTicks) {
                return action("wait_until_gui_element",
                                params("elementType", "SLOT", "guiElementLocatorMode", "TEXT", "locatorText", text,
                                                "locatorMatchMode", "CONTAINS", "timeoutTicks", timeoutTicks,
                                                "timeoutSkipCount", 0));
        }

        private static ActionData waitInArea(double x, double y, double z, Number radius, int timeoutTicks,
                        int timeoutSkipCount) {
                return action("wait_until_player_in_area", params("center", vec(x, y, z), "radius", radius,
                                "timeoutTicks", timeoutTicks, "timeoutSkipCount", timeoutSkipCount));
        }

        private static ActionData label(String name) {
                return action("label", params("labelName", name));
        }

        private static ActionData gotoLabel(String name) {
                return action("goto_label", params("targetLabel", name));
        }

        private static ActionData skipActions(int count) {
                return action("skip_actions", params("count", Integer.valueOf(count)));
        }

        private static ActionData closeGui() {
                return action("close_container_window", params());
        }

        private static ActionData windowClickByText(String text) {
                return action("window_click", params("locatorMode", "ITEM_TEXT", "locatorText", text,
                                "locatorMatchMode", "CONTAINS", "windowId", "-1", "contains", "", "onlyOnSlotChange",
                                false,
                                "button", 0, "clickType", "PICKUP"));
        }

        private static ActionData windowClickBySlot(int slot, String contains) {
                return action("window_click", params("windowId", "-1", "slot", String.valueOf(slot), "slotBase", "DEC",
                                "contains", contains == null ? "" : contains, "onlyOnSlotChange", false, "button", 0,
                                "clickType", "PICKUP"));
        }

        private static ActionData conditionalWindowClickBySlot(int slot, String contains) {
                return action("conditional_window_click", params("windowId", "-1", "slot", String.valueOf(slot),
                                "slotBase", "DEC", "contains", contains == null ? "" : contains, "button", 0,
                                "clickType", "PICKUP"));
        }

        private static ActionData clickButtonByText(String text) {
                return action("click", params("locatorMode", "BUTTON_TEXT", "locatorText", text,
                                "locatorMatchMode", "CONTAINS", "x", 0, "y", 0, "left", true));
        }

        private static ActionTemplate template(String id, String category, String name, String summary, String useCase,
                        String note, List<ActionData> actions) {
                return new ActionTemplate(id, category, name, summary, useCase, note, actions);
        }

        private static List<ActionData> actions(ActionData... actions) {
                List<ActionData> list = new ArrayList<ActionData>();
                if (actions == null) {
                        return list;
                }
                for (ActionData action : actions) {
                        if (action != null) {
                                list.add(action);
                        }
                }
                return list;
        }

        private static ActionData action(String type, JsonObject params) {
                return new ActionData(type, params);
        }

        private static JsonObject params(Object... values) {
                JsonObject json = new JsonObject();
                if (values == null) {
                        return json;
                }
                for (int i = 0; i + 1 < values.length; i += 2) {
                        String key = values[i] == null ? "" : values[i].toString();
                        Object value = values[i + 1];
                        if (key.trim().isEmpty() || value == null) {
                                continue;
                        }
                        if (value instanceof JsonArray) {
                                json.add(key, (JsonArray) value);
                        } else if (value instanceof Boolean) {
                                json.addProperty(key, (Boolean) value);
                        } else if (value instanceof Number) {
                                json.addProperty(key, (Number) value);
                        } else {
                                json.addProperty(key, value.toString());
                        }
                }
                return json;
        }

        private static JsonArray strings(String... values) {
                JsonArray array = new JsonArray();
                if (values == null) {
                        return array;
                }
                for (String value : values) {
                        if (value != null) {
                                array.add(value);
                        }
                }
                return array;
        }

        private static JsonArray intRange(int startInclusive, int endInclusive) {
                JsonArray array = new JsonArray();
                int step = startInclusive <= endInclusive ? 1 : -1;
                for (int value = startInclusive; value != endInclusive + step; value += step) {
                        array.add(value);
                }
                return array;
        }

        private static JsonArray vec(double x, double y, double z) {
                JsonArray array = new JsonArray();
                array.add(x);
                array.add(y);
                array.add(z);
                return array;
        }

        private static JsonArray rect(int x, int y, int width, int height) {
                JsonArray array = new JsonArray();
                array.add(x);
                array.add(y);
                array.add(width);
                array.add(height);
                return array;
        }

        private static List<ActionData> copyActions(List<ActionData> source) {
                List<ActionData> copied = new ArrayList<ActionData>();
                if (source == null) {
                        return copied;
                }
                for (ActionData action : source) {
                        if (action != null) {
                                copied.add(new ActionData(action));
                        }
                }
                return copied;
        }

        private static void appendSearch(StringBuilder builder, String value) {
                if (builder == null || value == null || value.trim().isEmpty()) {
                        return;
                }
                if (builder.length() > 0) {
                        builder.append(' ');
                }
                builder.append(value.trim());
        }

        private static String normalizeCategory(String value) {
                return safe(value).trim();
        }

        private static String safe(String value) {
                return value == null ? "" : value;
        }
}
