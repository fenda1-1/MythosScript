package com.mythos.mythosScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.mythos.mythosScriptMod.config.session.SessionConfigSync;
import com.mythos.mythosScriptMod.gui.MainUiLayoutManager;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler.HuntPickupRule;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler.HuntScoreDebugEntry;
import com.mythos.mythosScriptMod.handlers.KillAuraHandler.KillAuraPreset;
import com.mythos.mythosScriptMod.gui.modern.form.ModernFormI18n;
import com.mythos.mythosScriptMod.gui.modern.path.ModernPathSequencePicker;
import com.mythos.mythosScriptMod.gui.modern.path.editor.ExpressionEditorPreview;
import com.mythos.mythosScriptMod.gui.modern.path.editor.ModernExpressionEditorPanel;
import com.mythos.mythosScriptMod.path.PathSequenceManager;
import com.mythos.mythosScriptMod.utils.guiinspect.GuiElementInspector;

import net.minecraft.client.gui.FontRenderer;
import com.mythos.mythosScriptMod.gui.modern.components.ModernTextField;

/**
 * Complete embedded editor for Kill Aura. The feature has enough coupled
 * options that it intentionally keeps a category rail and a two-column
 * editor instead of forcing the legacy page into a single generic form.
 *
 * <p>All changes are maintained as a live draft. The defensive state snapshot
 * restores configuration when this tab is closed or reverted, while save
 * continues to use {@link KillAuraHandler#saveConfig()} as the only normal
 * persistence path.</p>
 */
public final class ModernKillAuraSettingsTab implements ModernSettingsTab {

    private static final int GROUP_RAIL_MIN_WIDTH = 120;
    private static final int GROUP_RAIL_FLOOR_WIDTH = 82;
    private static final int EDITOR_MIN_WIDTH = 280;
    private static final int EDITOR_FLOOR_WIDTH = 180;
    private static final int GROUP_EDITOR_GAP = 8;
    private static final double DEFAULT_GROUP_RAIL_RATIO = 0.18D;
    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 5;
    private static final int SECTION_HEADER_HEIGHT = 21;
    private static final int SPECIAL_TITLE_HEIGHT = 20;
    private static final int CONTENT_TOP_INSET = 3;
    private static final int LIST_CARD_HEIGHT = 25;
    private static final int LIST_CARD_GAP = 4;
    private static final int NAME_GROUP_HEADER_HEIGHT = 26;
    private static final int CARD_HEADER_HEIGHT = 26;
    private static final int CARD_PADDING = 6;

    private static final String[] ATTACK_MODES = {
            KillAuraHandler.ATTACK_MODE_NORMAL,
            KillAuraHandler.ATTACK_MODE_PACKET,
            KillAuraHandler.ATTACK_MODE_TELEPORT,
            KillAuraHandler.ATTACK_MODE_SEQUENCE,
            KillAuraHandler.ATTACK_MODE_MOUSE_CLICK };
    private static final String KEY_RANGE = "attackRange";
    private static final String KEY_INTERVAL = "minAttackIntervalTicks";
    private static final String KEY_STRENGTH = "minAttackStrength";
    private static final String KEY_TARGETS = "targetsPerAttack";
    private static final String KEY_DELAY = "attackSequenceDelayTicksSpec";
    private static final String KEY_TP_ROTATION = "teleportAttackRotationPackets";
    /**
     * 内核在非活动模式下不读取这些字段，所以每种模式只保存自己卡片里出现的键；
     * 若把五套全量参数互相覆盖，切模式时会把别的模式无效的值带过去。
     */
    private static final Map<String, List<String>> MODE_PROFILE_KEYS = new java.util.LinkedHashMap<>();
    /**
     * 非当前模式的档案只活在这里；当前模式的真值始终是 KillAuraHandler 的静态字段，
     * 这样内核、预设与 MCP 改动当前参数时不需要知道档案的存在。
     */
    private static final Map<String, Map<String, String>> MODE_PROFILES = new java.util.LinkedHashMap<>();

    static {
        List<String> base = Arrays.asList(KEY_RANGE, KEY_INTERVAL, KEY_STRENGTH, KEY_TARGETS);
        MODE_PROFILE_KEYS.put(KillAuraHandler.ATTACK_MODE_NORMAL, base);
        MODE_PROFILE_KEYS.put(KillAuraHandler.ATTACK_MODE_PACKET, base);
        List<String> teleport = new ArrayList<>(base);
        teleport.addAll(Arrays.asList("teleportStepDistance", "teleportStationAttackRadius", "teleportActionsPerTick",
                "teleportStepDistancePerAction", KEY_TP_ROTATION, "teleportViewMode"));
        MODE_PROFILE_KEYS.put(KillAuraHandler.ATTACK_MODE_TELEPORT, teleport);
        MODE_PROFILE_KEYS.put(KillAuraHandler.ATTACK_MODE_SEQUENCE, Arrays.asList(KEY_RANGE, KEY_DELAY));
        MODE_PROFILE_KEYS.put(KillAuraHandler.ATTACK_MODE_MOUSE_CLICK, Arrays.asList(KEY_RANGE, KEY_STRENGTH, KEY_DELAY));
    }

    private enum Group {
        PRESET("gui.modern.killaura.u001", "gui.modern.killaura.u002"),
        ATTACK("gui.modern.killaura.u003", "gui.modern.killaura.u004"),
        ROTATION("gui.modern.killaura.u064", "gui.modern.killaura.u065"),
        TARGET("gui.modern.killaura.u005", "gui.modern.killaura.u006"),
        NAME_FILTER("gui.modern.killaura.u007", "gui.modern.killaura.u008"),
        HUNT("gui.modern.killaura.u009", "gui.modern.killaura.u010"),
        PROTECTION("gui.modern.killaura.u011", "gui.modern.killaura.u012" );

        private final String label;
        private final String tooltip;

        Group(String label, String tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }
    }

    private enum FieldType {
        TOGGLE,
        TEXT,
        SEQUENCE,
        DECIMAL,
        INTEGER,
        CHOICE,
        ACTION
    }

    private enum NameListKind {
        NEARBY
    }

    private enum RuleInputKey {
        NAME("gui.modern.killaura.u015", "gui.modern.killaura.u016"),
        CATEGORY("gui.modern.killaura.u017", "gui.modern.killaura.u018"),
        NAME_KEYWORD("gui.modern.killaura.u019", "gui.modern.killaura.u020"),
        ITEM_ID_KEYWORD("gui.modern.killaura.u021", "gui.modern.killaura.u022"),
        NBT_TAGS("gui.modern.killaura.u023", "gui.modern.killaura.u024"),
        EXPRESSIONS("gui.modern.killaura.u242", "gui.modern.killaura.u243"),
        MAX_DISTANCE("gui.modern.killaura.u027", "gui.modern.killaura.u028"),
        PRIORITY("gui.modern.killaura.u029", "gui.modern.killaura.u030" );

        private final String label;
        private final String tooltip;

        RuleInputKey(String label, String tooltip) {
            this.label = label;
            this.tooltip = tooltip;
        }
    }

    private interface Condition {
        boolean matches();
    }

    private interface FieldAction {
        void execute();
    }

    private static final Condition ALWAYS = new Condition() {
        @Override
        public boolean matches() {
            return true;
        }
    };

    private static final class Choice {
        private final String value;
        private final String label;

        private Choice(String value, String label) {
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
        }
    }

    private static final class Field {
        private final FieldType type;
        private final String key;
        private final String title;
        private final String tooltip;
        private final String actionLabel;
        private final String placeholder;
        private final int minimum;
        private final int maximum;
        private final List<Choice> choices;
        private final FieldAction action;
        private boolean fullWidth;
        private Condition visibleWhen = ALWAYS;
        private Condition enabledWhen = ALWAYS;
        private ModernTextField input;
        private ModernMainLayout.Rect rowBounds;
        private ModernMainLayout.Rect controlBounds;
        private ModernMainLayout.Rect infoBounds;

        private Field(FieldType type, String key, String title, String tooltip, String actionLabel, String placeholder,
                int minimum, int maximum, List<Choice> choices, FieldAction action) {
            this.type = type;
            this.key = key == null ? "" : key;
            this.title = title == null ? "" : title;
            this.tooltip = tooltip == null ? "" : tooltip;
            this.actionLabel = actionLabel == null ? "" : actionLabel;
            this.placeholder = placeholder == null ? "" : placeholder;
            this.minimum = minimum;
            this.maximum = Math.max(minimum, maximum);
            this.choices = choices == null ? Collections.<Choice>emptyList() : new ArrayList<>(choices);
            this.action = action;
        }

        private Field fullWidth() {
            this.fullWidth = true;
            return this;
        }

        private Field visibleWhen(Condition condition) {
            this.visibleWhen = condition == null ? ALWAYS : condition;
            return this;
        }

        private Field enabledWhen(Condition condition) {
            this.enabledWhen = condition == null ? ALWAYS : condition;
            return this;
        }

        private boolean visible() {
            return this.visibleWhen.matches();
        }

        private boolean enabled() {
            return this.enabledWhen.matches();
        }

        private boolean usesInput() {
            return type == FieldType.TEXT || type == FieldType.DECIMAL || type == FieldType.INTEGER;
        }

        private void clearBounds() {
            rowBounds = null;
            controlBounds = null;
            infoBounds = null;
            if (input != null) {
                input.setVisible(false);
            }
        }
    }

    private static final class Section {
        private final String title;
        private final String tooltip;
        private final List<Field> fields = new ArrayList<>();
        private String modeCard;
        private boolean collapsible;
        private boolean expanded = true;
        private ModernMainLayout.Rect headerBounds;
        private ModernMainLayout.Rect activateBounds;

        private Section(String title, String tooltip) {
            this.title = title == null ? "" : title;
            this.tooltip = tooltip == null ? "" : tooltip;
        }

        private Section collapsed() {
            this.collapsible = true;
            this.expanded = false;
            return this;
        }

        private Section modeCard(String mode) {
            this.modeCard = mode;
            return collapsed();
        }
    }

    private static final class SectionLayout {
        private final Section section;
        private final ModernMainLayout.Rect bounds;

        private SectionLayout(Section section, ModernMainLayout.Rect bounds) {
            this.section = section;
            this.bounds = bounds;
        }
    }

    private static final class PresetCardHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private PresetCardHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class NameRowHit {
        private final NameListKind kind;
        private final int index;
        private final ModernMainLayout.Rect rowBounds;
        private final ModernMainLayout.Rect moveUpBounds;
        private final ModernMainLayout.Rect moveDownBounds;
        private final ModernMainLayout.Rect removeBounds;

        private NameRowHit(NameListKind kind, int index, ModernMainLayout.Rect rowBounds,
                ModernMainLayout.Rect moveUpBounds, ModernMainLayout.Rect moveDownBounds,
                ModernMainLayout.Rect removeBounds) {
            this.kind = kind;
            this.index = index;
            this.rowBounds = rowBounds;
            this.moveUpBounds = moveUpBounds;
            this.moveDownBounds = moveDownBounds;
            this.removeBounds = removeBounds;
        }
    }

    private static final class NameGroupCard {
        private final int index;
        private final ModernMainLayout.Rect bounds;
        private final ModernMainLayout.Rect typeBounds;
        private final ModernMainLayout.Rect enabledBounds;
        private final ModernMainLayout.Rect moveUpBounds;
        private final ModernMainLayout.Rect moveDownBounds;
        private final ModernMainLayout.Rect removeBounds;
        private final List<NameRowHit> rows = new ArrayList<>();

        private NameGroupCard(int index, ModernMainLayout.Rect bounds, ModernMainLayout.Rect typeBounds,
                ModernMainLayout.Rect enabledBounds, ModernMainLayout.Rect moveUpBounds,
                ModernMainLayout.Rect moveDownBounds, ModernMainLayout.Rect removeBounds) {
            this.index = index;
            this.bounds = bounds;
            this.typeBounds = typeBounds;
            this.enabledBounds = enabledBounds;
            this.moveUpBounds = moveUpBounds;
            this.moveDownBounds = moveDownBounds;
            this.removeBounds = removeBounds;
        }
    }

    private static final class RuleCardHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private RuleCardHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class RuleExpressionHit {
        private final int index;
        private final ModernMainLayout.Rect row;
        private final ModernMainLayout.Rect edit;
        private final ModernMainLayout.Rect delete;

        private RuleExpressionHit(int index, ModernMainLayout.Rect row, ModernMainLayout.Rect edit,
                ModernMainLayout.Rect delete) {
            this.index = index;
            this.row = row;
            this.edit = edit;
            this.delete = delete;
        }
    }

    private final Map<Group, List<Section>> sections = new EnumMap<>(Group.class);
    private String expandedForMode = "";
    private final Map<String, Section> modeCards = new java.util.LinkedHashMap<>();
    private final List<SectionLayout> sectionLayouts = new ArrayList<>();
    private final List<PresetCardHit> presetCardHits = new ArrayList<>();
    private final List<NameRowHit> nameRowHits = new ArrayList<>();
    private final List<RuleCardHit> ruleCardHits = new ArrayList<>();
    private final List<RuleExpressionHit> ruleExpressionHits = new ArrayList<>();
    private final Map<RuleInputKey, ModernTextField> ruleInputs = new EnumMap<>(RuleInputKey.class);
    private final Map<RuleInputKey, String> localizedRuleInputSources = new EnumMap<>(RuleInputKey.class);
    private final Map<RuleInputKey, ModernMainLayout.Rect> ruleInputBounds = new EnumMap<>(RuleInputKey.class);
    private final List<String> nearbyNames = new ArrayList<>();
    private final List<String> nearbyNameTypes = new ArrayList<>();
    private final List<KillAuraPreset> presetCards = new ArrayList<>();
    private final List<HuntScoreDebugEntry> huntScoreEntries = new ArrayList<>();

    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect groupRailBounds;
    private ModernMainLayout.Rect groupRailDividerBounds;
    private ModernMainLayout.Rect contentClipBounds;
    private ModernMainLayout.Rect headerToggleBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect defaultsBounds;
    private ModernMainLayout.Rect revertBounds;

    private ModernMainLayout.Rect presetNameBounds;
    private ModernMainLayout.Rect presetNewBounds;
    private ModernMainLayout.Rect presetApplyBounds;
    private ModernMainLayout.Rect presetOverwriteBounds;
    private ModernMainLayout.Rect presetRenameBounds;
    private ModernMainLayout.Rect presetDeleteBounds;
    private ModernMainLayout.Rect presetListBounds;

    private ModernMainLayout.Rect nameEntryBounds;
    private ModernMainLayout.Rect scanNamesBounds;
    private ModernMainLayout.Rect addToNameGroupBounds;
    private ModernMainLayout.Rect newNameGroupBounds;
    private ModernMainLayout.Rect renameNameGroupBounds;
    private ModernMainLayout.Rect nearbyListBounds;
    private ModernMainLayout.Rect nameGroupsBounds;
    private final List<NameGroupCard> nameGroupCards = new ArrayList<>();

    private ModernMainLayout.Rect addRuleBounds;
    private ModernMainLayout.Rect deleteRuleBounds;
    private ModernMainLayout.Rect ruleListBounds;
    private ModernMainLayout.Rect ruleToggleBounds;
    private ModernMainLayout.Rect ruleModeBounds;
    private ModernMainLayout.Rect ruleExpressionAddBounds;
    private final Map<String, ModernMainLayout.Rect> rarityBounds = new java.util.LinkedHashMap<>();
    private ModernMainLayout.Rect huntScoreBounds;

    private ModernTextField presetNameField;
    private ModernTextField nameEntryField;
    private int selectedPresetIndex = -1;
    private int selectedNameGroupIndex = -1;
    private int selectedRuleIndex = -1;
    private int selectedRuleExpressionIndex = -1;
    private int scrollOffset;
    private int maxScrollOffset;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private Group activeGroup = Group.ATTACK;
    private double groupRailRatio = DEFAULT_GROUP_RAIL_RATIO;
    private boolean draggingGroupRailDivider;
    private boolean layoutPreferencesLoaded;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean drawingContent;
    /**
     * The form temporarily hides every text field while rebuilding its layout.
     * Remember the logical focus so that this render-only reset cannot steal it.
     */
    private ModernTextField focusedInputBeforeLayoutReset;
    private boolean initialized;
    private boolean immediatePersistenceOccurred;
    private boolean draftDirty;
    private State savedState;
    /**
     * Nexus 切换挂载会话时，静态配置字段会整套换成另一个 bot 的，但这个页签实例和它的
     * 草稿（基线、输入框文字、模式档案）还留着上一个 bot 的。所以草稿按会话分开：
     * 切走时停放，切回来原样恢复，保存/取消也只作用于当前会话。
     */
    private final Map<String, ParkedDraft> parkedDrafts = new HashMap<>();
    private String draftOwner = "";
    private int draftGeneration;
    /**
     * 当前会话草稿的最近快照。切换被察觉时静态字段已经是新会话的了，没法再从里面
     * 取旧草稿，只能靠每次交互后记下的这份。
     */
    private ParkedDraft liveDraft;
    private final ModernExpressionEditorPanel ruleExpressionEditor = new ModernExpressionEditorPanel();
    private final ModernPathSequencePicker attackSequencePicker = new ModernPathSequencePicker(new ModernPathSequencePicker.Selection() {
        @Override
        public void accept(String name) {
            KillAuraHandler.cfg().attackSequenceName = safe(name).trim();
            draftDirty = true;
        }
    });

    public static ModernSettingsTab create() {
        return new ModernKillAuraSettingsTab();
    }

    private ModernKillAuraSettingsTab() {
        for (Group group : Group.values()) {
            sections.put(group, new ArrayList<Section>());
        }
        buildFields();
    }

    private void buildFields() {
        Section rotation = section(Group.ROTATION, "gui.modern.killaura.u064", "gui.modern.killaura.u065");
        rotation.fields.add(toggle("rotateToTarget", "gui.modern.killaura.u066", "gui.modern.killaura.u067")
                .enabledWhen(condition("rotationAvailable")));
        rotation.fields.add(toggle("smoothRotation", "gui.modern.killaura.u068", "gui.modern.killaura.u069")
                .enabledWhen(condition("rotationAvailable")));
        rotation.fields.add(text("smoothMaxTurnStepSpec", "gui.modern.killaura.u070", "gui.modern.killaura.u071", "24-60")
                .enabledWhen(condition("smoothRotation")));
        rotation.fields.add(toggle("onlyAttackWhenLookingAtTarget", "gui.modern.killaura.u072", "gui.modern.killaura.u073")
                .enabledWhen(condition("lookAttackAvailable")));
        rotation.fields.add(toggle("relockOnlyWhenNoCrosshairTarget", "gui.modern.killaura.u074", "gui.modern.killaura.u075")
                .enabledWhen(condition("relockAvailable")));
        rotation.fields.add(toggle("rotateOnlyOnAttack", "gui.modern.killaura.u076", "gui.modern.killaura.u077")
                .enabledWhen(condition("rotateOnlyAvailable")));
        rotation.fields.add(text("aimYawOffsetSpec", "gui.modern.killaura.u078", "gui.modern.killaura.u079", "0")
                .enabledWhen(condition("rotationAvailable")));
        rotation.fields.add(text("aimPitchOffsetSpec", "gui.modern.killaura.u080", "gui.modern.killaura.u081", "0")
                .enabledWhen(condition("rotationAvailable")));
        rotation.fields.add(decimal("minTurnSpeed", "gui.modern.killaura.u166", "gui.modern.killaura.u167", 1, 40)
                .enabledWhen(condition("smoothRotation")));
        rotation.fields.add(decimal("maxTurnSpeed", "gui.modern.killaura.u168", "gui.modern.killaura.u169", 1, 60)
                .enabledWhen(condition("smoothRotation")));

        Section normalCard = modeCard(KillAuraHandler.ATTACK_MODE_NORMAL, "gui.modern.killaura.u034");
        addCadenceFields(normalCard, KillAuraHandler.ATTACK_MODE_NORMAL);

        Section packetCard = modeCard(KillAuraHandler.ATTACK_MODE_PACKET, "gui.modern.killaura.u035");
        addCadenceFields(packetCard, KillAuraHandler.ATTACK_MODE_PACKET);

        String tp = KillAuraHandler.ATTACK_MODE_TELEPORT;
        Section teleportCard = modeCard(tp, "gui.modern.killaura.u036");
        addCadenceFields(teleportCard, tp);
        teleportCard.fields.add(decimal(profileKey(tp, "teleportStepDistance"), "gui.modern.killaura.u041",
                "gui.modern.killaura.u042", 0, 200).enabledWhen(condition("notAimOnly")));
        teleportCard.fields.add(decimal(profileKey(tp, "teleportStationAttackRadius"), "gui.modern.killaura.u043",
                "gui.modern.killaura.u044", 0, 200).enabledWhen(condition("notAimOnly")));
        teleportCard.fields.add(integer(profileKey(tp, "teleportActionsPerTick"), "gui.modern.killaura.u261",
                "gui.modern.killaura.u262", 1, 5).enabledWhen(condition("notAimOnly")));
        teleportCard.fields.add(decimal(profileKey(tp, "teleportStepDistancePerAction"), "gui.modern.killaura.u263",
                "gui.modern.killaura.u264", 0, 10).enabledWhen(condition("notAimOnly")));
        teleportCard.fields.add(toggle(profileKey(tp, KEY_TP_ROTATION), "gui.modern.killaura.u265",
                "gui.modern.killaura.u266").enabledWhen(condition("notAimOnly")));
        teleportCard.fields.add(choice(profileKey(tp, "teleportViewMode"), "gui.modern.killaura.u256",
                "gui.modern.killaura.u257",
                choices(new Choice(KillAuraHandler.TELEPORT_VIEW_FOLLOW, "gui.modern.killaura.u258"),
                        new Choice(KillAuraHandler.TELEPORT_VIEW_LOCK, "gui.modern.killaura.u259"),
                        new Choice(KillAuraHandler.TELEPORT_VIEW_AVATAR, "gui.modern.killaura.u260")))
                .fullWidth().enabledWhen(condition("notAimOnly")));

        // 序列模式下内核跳过间隔、蓄力与目标数判定，放进卡里只会让人以为它们生效。
        String sequenceMode = KillAuraHandler.ATTACK_MODE_SEQUENCE;
        Section sequenceCard = modeCard(sequenceMode, "gui.modern.killaura.u037");
        sequenceCard.fields.add(decimal(profileKey(sequenceMode, KEY_RANGE), "gui.modern.killaura.u162",
                "gui.modern.killaura.u163", 1, 100));
        sequenceCard.fields.add(text(profileKey(sequenceMode, KEY_DELAY), "gui.modern.killaura.u280",
                "gui.modern.killaura.u281", "1-6"));
        sequenceCard.fields.add(sequence("attackSequenceName", "gui.modern.killaura.u047", "gui.modern.killaura.u048",
                "gui.modern.killaura.u049").fullWidth());
        sequenceCard.fields.add(action("gui.modern.killaura.u052", "gui.modern.killaura.u053", "gui.modern.killaura.u054",
                new FieldAction() {
                    @Override
                    public void execute() {
                        PathSequenceManager.initializePathSequences();
                        showStatus("gui.modern.killaura.u055");
                    }
                }).fullWidth());

        // 模拟点击一次只点准星下的那个实体，目标数在内核里固定为 1。
        String click = KillAuraHandler.ATTACK_MODE_MOUSE_CLICK;
        Section clickCard = modeCard(click, "gui.modern.killaura.u038");
        clickCard.fields.add(decimal(profileKey(click, KEY_RANGE), "gui.modern.killaura.u162",
                "gui.modern.killaura.u163", 1, 100).enabledWhen(condition("notAimOnly")));
        clickCard.fields.add(decimal(profileKey(click, KEY_STRENGTH), "gui.modern.killaura.u164",
                "gui.modern.killaura.u165", 0, 1).enabledWhen(condition("notAimOnly")));
        clickCard.fields.add(text(profileKey(click, KEY_DELAY), "gui.modern.killaura.u278",
                "gui.modern.killaura.u279", "1-6").enabledWhen(condition("notAimOnly")));

        // 先选攻击方式再看通用开关；攻击方式的切换只由卡片完成，顶部不再保留重复的五选一。
        Section attackCore = section(Group.ATTACK, "gui.modern.killaura.u267", "gui.modern.killaura.u268");
        attackCore.fields.add(toggle("aimOnlyMode", "gui.modern.killaura.u045", "gui.modern.killaura.u046"));
        attackCore.fields.add(toggle("onlyWeapon", "gui.modern.killaura.u056", "gui.modern.killaura.u057")
                .enabledWhen(condition("weaponApplicable")));
        attackCore.fields.add(toggle("requireLineOfSight", "gui.modern.killaura.u058", "gui.modern.killaura.u059"));
        attackCore.fields.add(toggle("throughWallAttack", "gui.modern.killaura.u060", "gui.modern.killaura.u061"));
        attackCore.fields.add(toggle("focusSingleTarget", "gui.modern.killaura.u062", "gui.modern.killaura.u063"));
        attackCore.fields.add(toggle("blockWhileAttacking", "gui.modern.killaura.block_while_attacking",
                "gui.modern.killaura.block_while_attacking_desc").enabledWhen(condition("notAimOnly")));

        Section targets = section(Group.TARGET, "gui.modern.killaura.u082", "gui.modern.killaura.u083");
        targets.fields.add(toggle("targetHostile", "gui.modern.killaura.u084", "gui.modern.killaura.u085"));
        targets.fields.add(toggle("targetPassive", "gui.modern.killaura.u086", "gui.modern.killaura.u087"));
        targets.fields.add(toggle("targetPlayers", "gui.modern.killaura.u088", "gui.modern.killaura.u089"));
        targets.fields.add(toggle("targetNpcs", "gui.modern.killaura.u254", "gui.modern.killaura.u255"));
        targets.fields.add(toggle("targetEnderCrystal", "gui.modern.killaura.u090", "gui.modern.killaura.u091"));
        targets.fields.add(toggle("ignoreInvisible", "gui.modern.killaura.u092", "gui.modern.killaura.u093"));
        targets.fields.add(integer("noDamageAttackLimit", "gui.modern.killaura.u094", "gui.modern.killaura.u095", 0,
                KillAuraHandler.MAX_NO_DAMAGE_ATTACK_LIMIT).fullWidth());

        Section scoring = section(Group.TARGET, "gui.modern.killaura.u096", "gui.modern.killaura.u097");
        scoring.fields.add(decimal("huntScoreRadiusWeight", "gui.modern.killaura.u098", "gui.modern.killaura.u099", 0, 100));
        scoring.fields.add(decimal("huntScorePlayerDistanceWeight", "gui.modern.killaura.u100", "gui.modern.killaura.u101", 0, 100));
        scoring.fields.add(decimal("huntScorePlayerPlaneWeight", "gui.modern.killaura.u102", "gui.modern.killaura.u103", 0, 100));
        scoring.fields.add(decimal("huntScoreTargetHeightWeight", "gui.modern.killaura.u104", "gui.modern.killaura.u105", 0, 100));
        scoring.fields.add(decimal("huntScoreAttackRangeWeight", "gui.modern.killaura.u106", "gui.modern.killaura.u107", 0, 100));
        scoring.fields.add(decimal("huntScoreVisibilityWeight", "gui.modern.killaura.u108", "gui.modern.killaura.u109", 0, 100));
        scoring.fields.add(decimal("huntScoreOpennessWeight", "gui.modern.killaura.u110", "gui.modern.killaura.u111", 0, 100));
        scoring.fields.add(action("gui.modern.killaura.u112", "gui.modern.killaura.u113", "gui.modern.killaura.u114", new FieldAction() {
            @Override
            public void execute() {
                KillAuraHandler.resetHuntScoreWeights();
                syncInputFields();
                showStatus("gui.modern.killaura.u115");
            }
        }));

        Section names = section(Group.NAME_FILTER, "gui.modern.killaura.u116", "gui.modern.killaura.u117");
        names.fields.add(decimal("nearbyEntityScanRange", "gui.modern.killaura.u122", "gui.modern.killaura.u123", 1, 64).fullWidth());

        Section hunt = section(Group.HUNT, "gui.modern.killaura.u124", "gui.modern.killaura.u125");
        hunt.fields.add(choice("huntMode", "gui.modern.killaura.u126", "gui.modern.killaura.u127",
                choices(new Choice(KillAuraHandler.HUNT_MODE_APPROACH, "gui.modern.killaura.u128"),
                        new Choice(KillAuraHandler.HUNT_MODE_FIXED_DISTANCE, "gui.modern.killaura.u129"),
                        new Choice(KillAuraHandler.HUNT_MODE_OFF, "gui.modern.killaura.u130"))).fullWidth());
        hunt.fields.add(decimal("huntRadius", "gui.modern.killaura.u131", "gui.modern.killaura.u132", 1, 100)
                .enabledWhen(condition("huntEnabled")));
        hunt.fields.add(decimal("huntFixedDistance", "gui.modern.killaura.u129", "gui.modern.killaura.u133", 0, 100)
                .visibleWhen(condition("fixedHunt")).enabledWhen(condition("huntEnabled")));
        hunt.fields.add(decimal("huntUpRange", "gui.modern.killaura.u134", "gui.modern.killaura.u135", 0, 100)
                .enabledWhen(condition("huntEnabled")));
        hunt.fields.add(decimal("huntDownRange", "gui.modern.killaura.u136", "gui.modern.killaura.u137", 0, 100)
                .enabledWhen(condition("huntEnabled")));
        hunt.fields.add(toggle("huntOrbitEnabled", "gui.modern.killaura.u138", "gui.modern.killaura.u139")
                .visibleWhen(condition("fixedHunt")).enabledWhen(condition("huntEnabled")));
        hunt.fields.add(toggle("huntJumpOrbitEnabled", "gui.modern.killaura.u140", "gui.modern.killaura.u141")
                .visibleWhen(condition("orbitEnabled")).enabledWhen(condition("huntEnabled")));
        hunt.fields.add(integer("huntOrbitSamplePoints", "gui.modern.killaura.u142", "gui.modern.killaura.u143",
                KillAuraHandler.MIN_HUNT_ORBIT_SAMPLE_POINTS, KillAuraHandler.MAX_HUNT_ORBIT_SAMPLE_POINTS)
                .visibleWhen(condition("orbitEnabled")).enabledWhen(condition("huntEnabled")));
        hunt.fields.add(toggle("huntPickupItemsEnabled", "gui.modern.killaura.u144", "gui.modern.killaura.u145")
                .enabledWhen(condition("huntEnabled")));
        hunt.fields.add(toggle("visualizeHuntRadius", "gui.modern.killaura.u146", "gui.modern.killaura.u147")
                .enabledWhen(condition("huntEnabled")));

        Section protection = section(Group.PROTECTION, "gui.modern.killaura.u148", "gui.modern.killaura.u149");
        protection.fields.add(toggle("enableNoCollision", "gui.modern.killaura.u150", "gui.modern.killaura.u151"));
        protection.fields.add(toggle("enableAntiKnockback", "gui.modern.killaura.u152", "gui.modern.killaura.u153"));
        protection.fields.add(toggle("disableOnDisconnect", "gui.modern.killaura.u154", "gui.modern.killaura.u155"));
        protection.fields.add(toggle("enableFullBrightVision", "gui.modern.killaura.u156", "gui.modern.killaura.u157"));
        protection.fields.add(decimal("fullBrightGamma", "gui.modern.killaura.u158", "gui.modern.killaura.u159", 1, 1000)
                .enabledWhen(condition("fullBright")));
    }

    private Section section(Group group, String title, String tooltip) {
        Section section = new Section(title, tooltip);
        sections.get(group).add(section);
        return section;
    }

    private Section modeCard(String mode, String title) {
        Section card = section(Group.ATTACK, title, "gui.modern.killaura.u269").modeCard(mode);
        modeCards.put(mode, card);
        return card;
    }

    private void addCadenceFields(Section card, String mode) {
        card.fields.add(decimal(profileKey(mode, KEY_RANGE), "gui.modern.killaura.u162", "gui.modern.killaura.u163",
                1, 100).enabledWhen(condition("notAimOnly")));
        card.fields.add(integer(profileKey(mode, KEY_INTERVAL), "gui.modern.killaura.u170", "gui.modern.killaura.u171",
                0, 20).enabledWhen(condition("notAimOnly")));
        card.fields.add(decimal(profileKey(mode, KEY_STRENGTH), "gui.modern.killaura.u164", "gui.modern.killaura.u165",
                0, 1).enabledWhen(condition("notAimOnly")));
        card.fields.add(integer(profileKey(mode, KEY_TARGETS), "gui.modern.killaura.u172", "gui.modern.killaura.u173",
                1, 50).enabledWhen(condition("notAimOnly")));
    }

    private static String profileKey(String mode, String key) {
        return mode + ":" + key;
    }

    private static Field toggle(String key, String title, String tooltip) {
        return new Field(FieldType.TOGGLE, key, title, tooltip, "", "", 0, 0, null, null);
    }

    private static Field text(String key, String title, String tooltip, String placeholder) {
        return new Field(FieldType.TEXT, key, title, tooltip, "", placeholder, 0, 0, null, null);
    }

    private static Field sequence(String key, String title, String tooltip, String placeholder) {
        return new Field(FieldType.SEQUENCE, key, title, tooltip, "", placeholder, 0, 0, null, null);
    }

    private static Field decimal(String key, String title, String tooltip, int minimum, int maximum) {
        return new Field(FieldType.DECIMAL, key, title, tooltip, "", "", minimum, maximum, null, null);
    }

    private static Field integer(String key, String title, String tooltip, int minimum, int maximum) {
        return new Field(FieldType.INTEGER, key, title, tooltip, "", "", minimum, maximum, null, null);
    }

    private static Field choice(String key, String title, String tooltip, List<Choice> choices) {
        return new Field(FieldType.CHOICE, key, title, tooltip, "", "", 0, 0, choices, null);
    }

    private static Field action(String title, String tooltip, String label, FieldAction action) {
        return new Field(FieldType.ACTION, "", title, tooltip, label, "", 0, 0, null, action);
    }

    private static List<Choice> choices(Choice... values) {
        return values == null ? Collections.<Choice>emptyList() : Arrays.asList(values);
    }

    @Override
    public void ensureInitialized(FontRenderer renderer) {
        if (initialized) {
            return;
        }
        this.fontRenderer = renderer;
        attackSequencePicker.ensureInitialized(renderer);
        if (!layoutPreferencesLoaded) {
            groupRailRatio = MainUiLayoutManager.getModernSplitRatio("killaura.groups", groupRailRatio);
            layoutPreferencesLoaded = true;
        }
        KillAuraHandler.loadConfig();
        loadModeProfiles();
        expandActiveModeCard();
        createInputFields(renderer);
        refreshPresetCards("");
        refreshNearbyNames();
        savedState = new State();
        draftDirty = false;
        draftOwner = SessionConfigSync.loadedOwner();
        draftGeneration = SessionConfigSync.generation();
        parkedDrafts.clear();
        liveDraft = null;
        initialized = true;
    }

    private static final class ParkedDraft {
        private final State saved;
        private final State draft;
        private final Map<String, String> inputs;
        private final Map<String, Map<String, String>> profiles;
        private final boolean dirty;
        private final boolean immediatePersistence;

        private ParkedDraft(State saved, State draft, Map<String, String> inputs,
                Map<String, Map<String, String>> profiles, boolean dirty, boolean immediatePersistence) {
            this.saved = saved;
            this.draft = draft;
            this.inputs = inputs;
            this.profiles = profiles;
            this.dirty = dirty;
            this.immediatePersistence = immediatePersistence;
        }
    }

    /** 挂载的会话换了：先停放上一个会话的草稿，再显示新会话的（有停放的草稿就恢复它）。 */
    private void syncDraftOwner() {
        if (!initialized) {
            return;
        }
        int generation = SessionConfigSync.generation();
        if (generation == draftGeneration) {
            return;
        }
        draftGeneration = generation;
        // 同一会话也要走一遍：切走再切回来时静态字段已按配置文件重载，草稿预览得重新套上。
        String owner = SessionConfigSync.loadedOwner();
        if (liveDraft != null) {
            parkedDrafts.put(draftOwner, liveDraft);
        } else {
            parkedDrafts.remove(draftOwner);
        }
        draftOwner = owner;
        // 弹窗里编辑的是上一个会话的值，关掉免得确认后写进新会话。
        if (attackSequencePicker.isOpen()) {
            attackSequencePicker.handleEscape();
        }
        if (ruleExpressionEditor.isOpen()) {
            ruleExpressionEditor.handleEscape();
        }
        clearInputFocus();
        scrollbar.endDrag();
        selectedNameGroupIndex = -1;
        ParkedDraft parked = parkedDrafts.remove(owner);
        liveDraft = parked;
        if (parked == null) {
            // 静态字段刚由 SessionConfigSync 按该会话的配置文件重载过，就是它已保存的值。
            savedState = new State();
            loadModeProfiles();
            draftDirty = false;
            immediatePersistenceOccurred = false;
        } else {
            // 启用状态是会话的运行状态，不属于草稿，保留它现在的值。
            boolean enabled = KillAuraHandler.enabled();
            savedState = parked.saved;
            parked.draft.apply();
            KillAuraHandler.setEnabledSilently(enabled);
            MODE_PROFILES.clear();
            MODE_PROFILES.putAll(copyModeProfiles(parked.profiles));
            draftDirty = parked.dirty;
            immediatePersistenceOccurred = parked.immediatePersistence;
        }
        expandActiveModeCard();
        refreshAfterStateChange();
        if (parked != null) {
            restoreInputTexts(parked.inputs);
        }
    }

    /** 交互结束后记下当前会话的草稿，供切换会话时停放。 */
    private void rememberDraft() {
        if (!initialized) {
            return;
        }
        if (!draftDirty && !immediatePersistenceOccurred) {
            liveDraft = null;
            return;
        }
        liveDraft = new ParkedDraft(savedState, new State(), captureInputTexts(),
                copyModeProfiles(MODE_PROFILES), draftDirty, immediatePersistenceOccurred);
    }

    private Map<String, String> captureInputTexts() {
        Map<String, String> texts = new HashMap<>();
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field.usesInput() && field.input != null) {
                        texts.put(field.key, field.input.getText());
                    }
                }
            }
        }
        return texts;
    }

    private void restoreInputTexts(Map<String, String> texts) {
        if (texts == null) {
            return;
        }
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    String text = texts.get(field.key);
                    if (text != null && field.usesInput() && field.input != null) {
                        field.input.setText(text);
                    }
                }
            }
        }
    }

    private static Map<String, Map<String, String>> copyModeProfiles(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new java.util.LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public void updateScreen() {
        syncDraftOwner();
        attackSequencePicker.updateScreen();
        ruleExpressionEditor.updateScreen();
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                input.updateCursorCounter();
            }
        });
    }

    @Override
    public void draw(FontRenderer renderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(renderer);
        syncDraftOwner();
        this.fontRenderer = renderer;
        syncExpandedModeCard();
        focusedInputBeforeLayoutReset = findFocusedInput();
        hoveredTooltip = "";
        drawingContent = false;
        panelBounds = buildPanelBounds(contentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int headerHeight = 1;
        int footerHeight = statusVisible() ? 53 : 33;
        int bodyY = panelBounds.y + headerHeight + 7;
        int bodyHeight = Math.max(1, panelBounds.height - headerHeight - footerHeight - 14);
        int paneX = panelBounds.x + 9;
        int paneWidth = Math.max(2, panelBounds.width - 18);
        int gap = Math.min(GROUP_EDITOR_GAP, Math.max(0, paneWidth - 2));
        int splitWidth = Math.max(2, paneWidth - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitWidth, groupRailRatio,
                GROUP_RAIL_MIN_WIDTH, EDITOR_MIN_WIDTH, GROUP_RAIL_FLOOR_WIDTH, EDITOR_FLOOR_WIDTH);
        groupRailBounds = new ModernMainLayout.Rect(paneX, bodyY, split.firstWidth, bodyHeight);

        int editorX = groupRailBounds.right() + gap;
        int editorWidth = split.secondWidth;
        int editorHeaderHeight = 24;
        ModernMainLayout.Rect editorBounds = new ModernMainLayout.Rect(editorX, bodyY, editorWidth, bodyHeight);
        groupRailDividerBounds = ModernSplitPane.verticalDividerBounds(groupRailBounds.x, groupRailBounds.width, gap,
                bodyY, bodyHeight);
        contentClipBounds = new ModernMainLayout.Rect(editorBounds.x + 5, editorBounds.y + editorHeaderHeight + 4,
                Math.max(1, editorBounds.width - 10), Math.max(1, editorBounds.height - editorHeaderHeight - 8));

        drawGroupRail(mouseX, mouseY);
        drawEditorHeader(editorBounds, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(groupRailDividerBounds, mouseX, mouseY, draggingGroupRailDivider);

        if (ruleExpressionEditor.isOpen()) {
            clearAllFieldBounds();
            hideAllInputs();
            drawingContent = true;
            ModernUiRenderer.beginClip(contentClipBounds);
            ruleExpressionEditor.draw(renderer, contentClipBounds, mouseX, mouseY);
            ModernUiRenderer.endClip();
            drawingContent = false;
            hoveredTooltip = ruleExpressionEditor.getHoveredTooltip();
            drawFooter(mouseX, mouseY);
            if (attackSequencePicker.isOpen()) {
                hoveredTooltip = "";
                attackSequencePicker.draw(fontRenderer, panelBounds, t("gui.modern.killaura.u047"), mouseX, mouseY);
            }
            focusedInputBeforeLayoutReset = null;
            return;
        }

        if (activeGroup == Group.TARGET) {
            refreshHuntScoreEntries();
        }
        int formX = contentClipBounds.x + 5;
        int formWidth = ModernHoverScrollbar.contentWidth(contentClipBounds.width - 7);
        int columns = formWidth >= 430 ? 2 : 1;
        int formHeight = layoutActiveContent(formX, 0, formWidth, columns, false);
        // The content is drawn three pixels below the clip origin. Include that
        // inset in the scrollable extent so the last panel can reach the bottom
        // of the viewport when the scrollbar is at its maximum.
        maxScrollOffset = Math.max(0, formHeight + CONTENT_TOP_INSET - contentClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);

        clearAllFieldBounds();
        layoutActiveContent(formX, contentClipBounds.y + CONTENT_TOP_INSET - scrollOffset, formWidth, columns, true);
        hideAllInputs();
        drawingContent = true;
        ModernUiRenderer.beginClip(contentClipBounds);
        drawSectionLayouts(mouseX, mouseY);
        drawSpecialContent(formX, formWidth, mouseX, mouseY);
        ModernUiRenderer.endClip();
        drawingContent = false;
        drawScrollbar(mouseX, mouseY);
        drawFooter(mouseX, mouseY);
        if (attackSequencePicker.isOpen()) {
            hoveredTooltip = "";
            attackSequencePicker.draw(fontRenderer, panelBounds, t("gui.modern.killaura.u047"), mouseX, mouseY);
        }
        focusedInputBeforeLayoutReset = null;
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        syncDraftOwner();
        boolean handled = handleMouseClicked(mouseX, mouseY, mouseButton);
        rememberDraft();
        return handled;
    }

    private boolean handleMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (attackSequencePicker.isOpen()) {
            if (mouseButton == 0) {
                attackSequencePicker.mouseClicked(mouseX, mouseY);
            }
            return true;
        }
        if (ruleExpressionEditor.isOpen()) {
            return ruleExpressionEditor.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (mouseButton != 0 || panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (contains(headerToggleBounds, mouseX, mouseY)) {
            KillAuraHandler.INSTANCE.setEnabled(!KillAuraHandler.enabled());
            immediatePersistenceOccurred = true;
            showStatus(KillAuraHandler.enabled() ? "gui.modern.killaura.u174" : "gui.modern.killaura.u175");
            return true;
        }
        if (contains(saveBounds, mouseX, mouseY)) {
            saveDraft();
            return true;
        }
        if (contains(defaultsBounds, mouseX, mouseY)) {
            applyDefaults();
            return true;
        }
        if (contains(revertBounds, mouseX, mouseY)) {
            revertDraft();
            return true;
        }
        if (contains(groupRailDividerBounds, mouseX, mouseY)) {
            draggingGroupRailDivider = true;
            clearInputFocus();
            return true;
        }
        if (handleGroupClick(mouseX, mouseY)) {
            return true;
        }
        if (contentClipBounds == null || !contentClipBounds.contains(mouseX, mouseY)) {
            clearInputFocus();
            return true;
        }
        if (scrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (handleFieldClick(mouseX, mouseY)) {
            return true;
        }
        if (handleSpecialClick(mouseX, mouseY)) {
            return true;
        }
        clearInputFocus();
        return true;
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        syncDraftOwner();
        boolean handled = handleKeyTyped(typedChar, keyCode);
        rememberDraft();
        return handled;
    }

    private boolean handleKeyTyped(char typedChar, int keyCode) {
        if (attackSequencePicker.isOpen()) {
            return attackSequencePicker.keyTyped(typedChar, keyCode);
        }
        if (ruleExpressionEditor.isOpen()) {
            return ruleExpressionEditor.keyTyped(typedChar, keyCode);
        }
        if (keyTypedInInputs(typedChar, keyCode)) {
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN && hasFocusedInput()) {
            applyEditableValues();
            clearInputFocus();
            showStatus("gui.modern.killaura.u176");
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (attackSequencePicker.isOpen()) {
            return attackSequencePicker.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (ruleExpressionEditor.isOpen()) {
            return ruleExpressionEditor.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (clickedMouseButton == 0 && draggingGroupRailDivider && panelBounds != null) {
            resizeGroupRail(mouseX);
            return true;
        }
        if (clickedMouseButton == 0 && scrollbar.isDragging()) {
            scrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        syncDraftOwner();
        boolean handled = handleMouseReleased(mouseX, mouseY, state);
        rememberDraft();
        return handled;
    }

    private boolean handleMouseReleased(int mouseX, int mouseY, int state) {
        if (attackSequencePicker.isOpen()) {
            return attackSequencePicker.mouseReleased(mouseX, mouseY, state);
        }
        if (ruleExpressionEditor.isOpen()) {
            return ruleExpressionEditor.mouseReleased(mouseX, mouseY, state);
        }
        if (state == 0 && scrollbar.isDragging()) {
            scrollbar.endDrag();
            return true;
        }
        if (state == 0 && draggingGroupRailDivider) {
            draggingGroupRailDivider = false;
            MainUiLayoutManager.setModernSplitRatio("killaura.groups", groupRailRatio);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (attackSequencePicker.isOpen()) {
            return true;
        }
        if (ruleExpressionEditor.isOpen()) {
            return true;
        }
        if (wheel == 0 || maxScrollOffset <= 0) {
            return false;
        }
        int before = scrollOffset;
        scrollOffset = clamp(scrollOffset + (wheel > 0 ? -32 : 32), 0, maxScrollOffset);
        return before != scrollOffset;
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (attackSequencePicker.isOpen()) {
            return attackSequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
        }
        if (ruleExpressionEditor.isOpen()) {
            return ruleExpressionEditor.handleMouseWheel(wheel, mouseX, mouseY);
        }
        return handleMouseWheel(wheel);
    }

    @Override
    public boolean handleEscape() {
        if (attackSequencePicker.isOpen()) {
            return attackSequencePicker.handleEscape();
        }
        return ruleExpressionEditor.isOpen() && ruleExpressionEditor.handleEscape();
    }

    @Override
    public boolean isTextInputFocused() {
        return attackSequencePicker.isTextInputFocused() || ruleExpressionEditor.isTextInputFocused()
                || ModernSettingsTab.super.isTextInputFocused();
    }

    @Override
    public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        if (attackSequencePicker.isOpen()) {
            return;
        }
        if (ruleExpressionEditor.isOpen()) {
            ruleExpressionEditor.clearTextInputFocusOutside(mouseX, mouseY);
            return;
        }
        ModernSettingsTab.super.clearTextInputFocusOutside(mouseX, mouseY);
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentClipBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    /**
     * Publishes the same controls that this tab uses for hit testing. MCP can
     * therefore address a field by a stable path or visible label instead of
     * calculating a pixel from the current window size and scroll position.
     */
    @Override
    public List<GuiElementInspector.GuiElementInfo> getMcpGuiElements() {
        syncDraftOwner();
        List<GuiElementInspector.GuiElementInfo> result = new ArrayList<>();
        if (panelBounds == null) {
            return result;
        }

        for (Group group : Group.values()) {
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/group/" + group.name(),
                    t(group.label), groupBounds(group, Math.max(22,
                            Math.min(28, (groupRailBounds.height - 34) / Group.values().length))));
        }

        for (SectionLayout sectionLayout : sectionLayouts) {
            if (sectionLayout == null || sectionLayout.section == null) {
                continue;
            }
            Section mcpSection = sectionLayout.section;
            if (mcpSection.modeCard != null && mcpSection.headerBounds != null) {
                String cardPath = "screen/GuiModernMainScreen/tab/toggle_kill_aura/mode/" + mcpSection.modeCard;
                addMcpElement(result, cardPath + "/header", t(mcpSection.title), mcpSection.headerBounds);
                if (mcpSection.activateBounds != null) {
                    addMcpElement(result, cardPath + "/activate", t("gui.modern.killaura.u274"),
                            mcpSection.activateBounds);
                }
            }
            if (mcpSection.collapsible && !mcpSection.expanded) {
                continue;
            }
            for (Field field : sectionLayout.section.fields) {
                if (field == null || !field.visible() || field.rowBounds == null) {
                    continue;
                }
                String fieldKey = field.key.isEmpty() ? "action_" + mcpSegment(field.actionLabel) : mcpSegment(field.key);
                String basePath = "screen/GuiModernMainScreen/tab/toggle_kill_aura/field/" + fieldKey;
                if (field.type == FieldType.CHOICE) {
                    addChoiceMcpElements(result, field, basePath);
                } else if (field.controlBounds != null) {
                    addFieldMcpElement(result, field, basePath);
                    if (field.usesInput() && field.input != null) {
                        String label = field.key.isEmpty() ? t(field.actionLabel) : t(field.title);
                        addMcpElement(result, basePath + "/input", label, field.controlBounds,
                                "input", field.input.getText(), field.enabled(), true,
                                java.util.Arrays.asList("click", "input", "set"), Collections.<String>emptyList());
                    }
                }
            }
        }

        addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/enabled",
                t(KillAuraHandler.enabled() ? "gui.modern.killaura.u180" : "gui.modern.killaura.u181"),
                headerToggleBounds, "toggle", String.valueOf(KillAuraHandler.enabled()), true, true,
                java.util.Arrays.asList("click", "set"), Collections.<String>emptyList());
        addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/save",
                t("gui.modern.killaura.u221"), saveBounds, "action", "", true, false,
                Collections.singletonList("click"), Collections.<String>emptyList());
        addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/defaults",
                t("gui.modern.killaura.u114"), defaultsBounds, "action", "", true, false,
                Collections.singletonList("click"), Collections.<String>emptyList());
        addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/footer/revert",
                t("gui.modern.killaura.u222"), revertBounds, "action", "", true, false,
                Collections.singletonList("click"), Collections.<String>emptyList());

        if (activeGroup == Group.PRESET) {
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/name",
                    t("gui.modern.killaura.u184"), presetNameBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/new",
                    t("gui.modern.killaura.u187"), presetNewBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/apply",
                    t("gui.modern.killaura.u188"), presetApplyBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/overwrite",
                    t("gui.modern.killaura.u189"), presetOverwriteBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/rename",
                    t("gui.modern.killaura.u190"), presetRenameBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/delete",
                    t("gui.modern.killaura.u191"), presetDeleteBounds);
            for (PresetCardHit hit : presetCardHits) {
                if (hit != null && hit.index >= 0 && hit.index < presetCards.size()) {
                    addMcpElement(result,
                            "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/preset/card/" + hit.index,
                            safe(presetCards.get(hit.index).name), hit.bounds);
                }
            }
        } else if (activeGroup == Group.NAME_FILTER) {
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/entry",
                    t("gui.modern.killaura.u194"), nameEntryBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/scan",
                    t("gui.modern.killaura.u197"), scanNamesBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/add_to_group",
                    t("gui.modern.killaura.u282"), addToNameGroupBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/new_group",
                    t("gui.modern.killaura.u283"), newNameGroupBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/rename_group",
                    t("gui.modern.killaura.u284"), renameNameGroupBounds);
            List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
            for (NameGroupCard card : nameGroupCards) {
                if (card.index < 0 || card.index >= groups.size()) {
                    continue;
                }
                KillAuraHandler.NameFilterGroup group = groups.get(card.index);
                String base = "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/group/" + card.index;
                addMcpElement(result, base, nameGroupTitle(group, card.index), card.bounds);
                addMcpElement(result, base + "/type", t(group.blacklist ? "gui.modern.killaura.u287"
                        : "gui.modern.killaura.u286"), card.typeBounds);
                addMcpElement(result, base + "/enabled", t(group.enabled ? "gui.modern.killaura.u288"
                        : "gui.modern.killaura.u289"), card.enabledBounds);
                addMcpElement(result, base + "/up", "↑", card.moveUpBounds);
                addMcpElement(result, base + "/down", "↓", card.moveDownBounds);
                addMcpElement(result, base + "/remove", "×", card.removeBounds);
                for (NameRowHit row : card.rows) {
                    String value = group.names != null && row.index < group.names.size()
                            ? safe(group.names.get(row.index)) : "";
                    addMcpElement(result, base + "/row/" + row.index, value, row.rowBounds);
                    addMcpElement(result, base + "/row/" + row.index + "/up", "↑", row.moveUpBounds);
                    addMcpElement(result, base + "/row/" + row.index + "/down", "↓", row.moveDownBounds);
                    addMcpElement(result, base + "/row/" + row.index + "/remove", "×", row.removeBounds);
                }
            }
            for (NameRowHit hit : nameRowHits) {
                if (hit == null || hit.rowBounds == null) {
                    continue;
                }
                List<String> values = nearbyNames;
                String value = values != null && hit.index >= 0 && hit.index < values.size()
                        ? safe(values.get(hit.index)) : "";
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/"
                                + hit.kind.name().toLowerCase(Locale.ROOT) + "/" + hit.index,
                        value, hit.rowBounds);
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/"
                                + hit.kind.name().toLowerCase(Locale.ROOT) + "/" + hit.index + "/remove",
                        "×", hit.removeBounds);
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/"
                                + hit.kind.name().toLowerCase(Locale.ROOT) + "/" + hit.index + "/up",
                        "↑", hit.moveUpBounds);
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/name/"
                                + hit.kind.name().toLowerCase(Locale.ROOT) + "/" + hit.index + "/down",
                        "↓", hit.moveDownBounds);
            }
        } else if (activeGroup == Group.HUNT) {
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/add_rule",
                    t("gui.modern.killaura.u206"), addRuleBounds);
            addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/delete_rule",
                    t("gui.modern.killaura.u207"), deleteRuleBounds);
            for (RuleCardHit hit : ruleCardHits) {
                if (hit != null) {
                    HuntPickupRule rule = hit.index >= 0 && KillAuraHandler.cfg().huntPickupRules != null
                            && hit.index < KillAuraHandler.cfg().huntPickupRules.size()
                                    ? KillAuraHandler.cfg().huntPickupRules.get(hit.index) : null;
                    addMcpElement(result,
                            "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/" + hit.index,
                            rule == null ? "" : safe(rule.name), hit.bounds);
                }
            }
            if (selectedRule() != null) {
                addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/enabled",
                        t("gui.modern.killaura.u215"), ruleToggleBounds);
                addMcpElement(result, "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/mode",
                        t(KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(selectedRule().mode)
                                ? "gui.modern.killaura.u216" : "gui.modern.killaura.u217"), ruleModeBounds);
                for (Map.Entry<String, ModernMainLayout.Rect> entry : rarityBounds.entrySet()) {
                    addMcpElement(result,
                            "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/rarity/"
                                    + mcpSegment(entry.getKey()),
                            KillAuraHandler.getHuntPickupRarityDisplayName(entry.getKey()), entry.getValue());
                }
                for (Map.Entry<RuleInputKey, ModernMainLayout.Rect> entry : ruleInputBounds.entrySet()) {
                    if (entry.getKey() == RuleInputKey.EXPRESSIONS) {
                        continue;
                    }
                    addMcpElement(result,
                            "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/input/"
                                    + entry.getKey().name().toLowerCase(Locale.ROOT),
                            t(entry.getKey().label), entry.getValue());
                }
                ModernMainLayout.Rect expressionPanel = ruleInputBounds.get(RuleInputKey.EXPRESSIONS);
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/expressions",
                        t("gui.modern.killaura.u242"), expressionPanel, "expression_list",
                        joinLiteralLines(selectedRule().itemFilterExpressions), true, false,
                        Collections.singletonList("click"), Collections.<String>emptyList());
                addMcpElement(result,
                        "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/expressions/add",
                        t("gui.modern.killaura.u244"), ruleExpressionAddBounds);
                for (RuleExpressionHit hit : ruleExpressionHits) {
                    if (hit == null) continue;
                    List<String> values = selectedRule().itemFilterExpressions;
                    String value = values != null && hit.index >= 0 && hit.index < values.size()
                            ? safe(values.get(hit.index)) : "";
                    String base = "screen/GuiModernMainScreen/tab/toggle_kill_aura/special/hunt/rule/expressions/"
                            + hit.index;
                    addMcpElement(result, base, value, hit.row);
                    addMcpElement(result, base + "/edit", t("gui.modern.killaura.u245"), hit.edit);
                    addMcpElement(result, base + "/delete", t("gui.modern.killaura.u246"), hit.delete);
                }
            }
        }
        return result;
    }

    private void addChoiceMcpElements(List<GuiElementInspector.GuiElementInfo> result, Field field, String basePath) {
        if (field == null || field.rowBounds == null || fontRenderer == null) {
            return;
        }
        List<String> values = new ArrayList<>();
        for (Choice choice : field.choices) values.add(choice.value);
        addMcpElement(result, basePath, t(field.title), field.rowBounds, "choice", State.getText(field.key),
                field.enabled(), true, java.util.Arrays.asList("click", "set"), values);
        int x = field.rowBounds.x + 9;
        int y = field.rowBounds.y + 20;
        int available = Math.max(1, field.rowBounds.width - 18);
        for (Choice choice : field.choices) {
            int optionWidth = Math.min(available, Math.max(48, fontRenderer.getStringWidth(choice.label) + 16));
            if (x > field.rowBounds.x + 9 && x + optionWidth > field.rowBounds.right() - 9) {
                x = field.rowBounds.x + 9;
                y += 21;
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, optionWidth, 18);
            addMcpElement(result, basePath + "/choice/" + mcpSegment(choice.value), t(choice.label), bounds,
                    "choice_option", choice.value, field.enabled(), true, Collections.singletonList("click"),
                    Collections.<String>emptyList());
            x += optionWidth + 4;
        }
    }

    private void addFieldMcpElement(List<GuiElementInspector.GuiElementInfo> result, Field field, String path) {
        if (field == null || field.controlBounds == null) return;
        String controlType;
        boolean editable;
        List<String> actions;
        String value = "";
        switch (field.type) {
        case TOGGLE:
            controlType = "toggle";
            editable = true;
            actions = java.util.Arrays.asList("click", "set");
            value = String.valueOf(State.getBoolean(field.key));
            break;
        case TEXT:
        case DECIMAL:
        case INTEGER:
            controlType = "input";
            editable = true;
            actions = java.util.Arrays.asList("click", "input", "set");
            value = field.input == null ? State.getText(field.key) : field.input.getText();
            break;
        case SEQUENCE:
            controlType = "sequence_picker";
            editable = false;
            actions = Collections.singletonList("click");
            value = State.getText(field.key);
            break;
        case ACTION:
            controlType = "action";
            editable = false;
            actions = Collections.singletonList("click");
            break;
        default:
            controlType = "custom";
            editable = false;
            actions = Collections.singletonList("click");
            break;
        }
        addMcpElement(result, path, field.key.isEmpty() ? t(field.actionLabel) : t(field.title), field.controlBounds,
                controlType, value, field.enabled(), editable, actions, Collections.<String>emptyList());
    }

    private void addMcpElement(List<GuiElementInspector.GuiElementInfo> result, String path, String text,
            ModernMainLayout.Rect bounds) {
        addMcpElement(result, path, text, bounds, "action", "", true, false,
                Collections.singletonList("click"), Collections.<String>emptyList());
    }

    private void addMcpElement(List<GuiElementInspector.GuiElementInfo> result, String path, String text,
            ModernMainLayout.Rect bounds, String controlType, String value, boolean enabled, boolean editable,
            List<String> actions, List<String> choices) {
        if (result == null || path == null || path.trim().isEmpty() || bounds == null
                || bounds.width <= 0 || bounds.height <= 0) {
            return;
        }
        GuiElementInspector.GuiElementInfo element = new GuiElementInspector.GuiElementInfo(
                GuiElementInspector.ElementType.CUSTOM, path, text == null ? "" : text, bounds.x, bounds.y,
                bounds.width, bounds.height, Integer.MIN_VALUE, -1, controlType, value, enabled, editable, actions,
                choices);
        if (contentClipBounds != null && (path.contains("/field/") || path.contains("/special/"))) {
            element = element.withVisibility(intersects(contentClipBounds, bounds));
        }
        result.add(element);
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first == null || second == null
                || first.x < second.right() && second.x < first.right()
                        && first.y < second.bottom() && second.y < first.bottom();
    }

    private String mcpSegment(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "unnamed";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_.-]+", "_");
    }

    @Override
    public boolean setMcpText(String target, String text, boolean append) {
        syncDraftOwner();
        boolean handled = handleSetMcpText(target, text, append);
        rememberDraft();
        return handled;
    }

    private boolean handleSetMcpText(String target, String text, boolean append) {
        String normalizedTarget = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (normalizedTarget.contains("/rule/expressions")
                && !normalizedTarget.endsWith("/add") && !normalizedTarget.endsWith("/edit")
                && !normalizedTarget.endsWith("/delete")) {
            activeGroup = Group.HUNT;
            scrollOffset = 0;
            HuntPickupRule rule = selectedRule();
            if (rule == null) {
                return false;
            }
            List<String> values = parseExpressionList(text);
            if (!append) {
                rule.itemFilterExpressions = values;
            } else {
                if (rule.itemFilterExpressions == null) {
                    rule.itemFilterExpressions = new ArrayList<>();
                }
                rule.itemFilterExpressions.addAll(values);
            }
            selectedRuleExpressionIndex = rule.itemFilterExpressions.isEmpty()
                    ? -1 : rule.itemFilterExpressions.size() - 1;
            draftDirty = true;
            return true;
        }
        ModernTextField input = findMcpInput(target);
        if (input == null) {
            return false;
        }
        clearInputFocus();
        input.setFocused(true);
        input.setText(append ? safe(input.getText()) + (text == null ? "" : text) : (text == null ? "" : text));
        draftDirty = true;
        return true;
    }

    private List<String> parseExpressionList(String text) {
        List<String> values = new ArrayList<>();
        String normalized = safe(text).replace("\\n", "\n").replace('\r', '\n');
        for (String part : normalized.split("[\\n;；]+")) {
            String expression = safe(part).trim();
            if (!expression.isEmpty()) {
                values.add(expression);
            }
        }
        return values;
    }

    @Override
    public boolean setMcpValue(String target, String value) {
        syncDraftOwner();
        boolean handled = handleSetMcpValue(target, value);
        rememberDraft();
        return handled;
    }

    private boolean handleSetMcpValue(String target, String value) {
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        String requested = value == null ? "" : value.trim();
        if (normalized.endsWith("/field/attackmode") || normalized.equals("attackmode")) {
            if (KillAuraHandler.aimOnlyMode()) return false;
            for (String mode : ATTACK_MODES) {
                if (mode.equalsIgnoreCase(requested)) {
                    activeGroup = Group.ATTACK;
                    switchAttackMode(mode);
                    return true;
                }
            }
            return false;
        }
        if (normalized.endsWith("/footer/enabled") || normalized.equals("enabled")) {
            Boolean enabled = parseMcpBoolean(requested);
            if (enabled == null) return false;
            if (KillAuraHandler.enabled() != enabled.booleanValue()) {
                KillAuraHandler.INSTANCE.setEnabled(enabled.booleanValue());
                immediatePersistenceOccurred = true;
            }
            return true;
        }
        if (normalized.contains("/rule/expressions")
                && !normalized.endsWith("/add") && !normalized.endsWith("/edit")
                && !normalized.endsWith("/delete")) {
            return setMcpText(target, value, false);
        }
        ModernTextField input = findMcpInput(target);
        if (input != null) {
            return setMcpText(target, value, false);
        }
        for (Map.Entry<Group, List<Section>> groupEntry : sections.entrySet()) {
            List<Section> groupSections = groupEntry.getValue();
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field == null || field.key.isEmpty() || !matchesMcpFieldTarget(normalized, field.key)
                            || !field.visible() || !field.enabled()) continue;
                    if (activeGroup != groupEntry.getKey()) {
                        activeGroup = groupEntry.getKey();
                        scrollOffset = 0;
                    }
                    if (field.type == FieldType.TOGGLE) {
                        Boolean toggle = parseMcpBoolean(requested);
                        if (toggle == null) return false;
                        State.setBoolean(field.key, toggle.booleanValue());
                        draftDirty = true;
                        return true;
                    }
                    if (field.type == FieldType.CHOICE) {
                        for (Choice choice : field.choices) {
                            if (requested.equalsIgnoreCase(choice.value)
                                    || requested.equalsIgnoreCase(t(choice.label))) {
                                State.setText(field.key, choice.value);
                                draftDirty = true;
                                return true;
                            }
                        }
                    }
                    if (field.type == FieldType.SEQUENCE) {
                        State.setText(field.key, requested);
                        draftDirty = true;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesMcpFieldTarget(String target, String key) {
        String normalizedKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return !normalizedKey.isEmpty() && (target.equals(normalizedKey)
                || target.endsWith("/" + normalizedKey)
                || target.contains("/field/" + normalizedKey + "/")
                || target.contains("/field/" + normalizedKey));
    }

    private static Boolean parseMcpBoolean(String value) {
        if (value == null) return null;
        if ("true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value) || "1".equals(value)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)
                || "no".equalsIgnoreCase(value) || "0".equals(value)) return Boolean.FALSE;
        return null;
    }

    @Override
    public boolean revealMcpTarget(String target) {
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return false;
        for (SectionLayout sectionLayout : sectionLayouts) {
            if (sectionLayout == null || sectionLayout.section == null) continue;
            for (Field field : sectionLayout.section.fields) {
                if (field == null || field.rowBounds == null) continue;
                String key = field.key.toLowerCase(Locale.ROOT);
                if (!key.isEmpty() && (normalized.endsWith("/" + key)
                        || normalized.contains("/field/" + key + "/")
                        || normalized.contains("/field/" + key))) {
                    return revealRect(field.rowBounds);
                }
            }
        }
        for (PresetCardHit hit : presetCardHits) {
            if (hit != null && normalized.contains("/preset/card/" + hit.index)) return revealRect(hit.bounds);
        }
        for (NameGroupCard card : nameGroupCards) {
            String base = "/name/group/" + card.index;
            if (normalized.endsWith(base) || normalized.contains(base + "/")) return revealRect(card.bounds);
        }
        for (NameRowHit hit : nameRowHits) {
            if (hit != null && normalized.contains("/name/")
                    && normalized.endsWith("/" + hit.index)) return revealRect(hit.rowBounds);
        }
        for (RuleCardHit hit : ruleCardHits) {
            if (hit != null && normalized.contains("/hunt/rule/" + hit.index)) return revealRect(hit.bounds);
        }
        if (normalized.contains("/hunt/rule/expressions")) {
            return revealRect(ruleInputBounds.get(RuleInputKey.EXPRESSIONS));
        }
        return false;
    }

    private boolean revealRect(ModernMainLayout.Rect bounds) {
        if (bounds == null || contentClipBounds == null) return false;
        if (bounds.y < contentClipBounds.y) {
            scrollOffset = clamp(scrollOffset + bounds.y - contentClipBounds.y - 4, 0, maxScrollOffset);
        } else if (bounds.bottom() > contentClipBounds.bottom()) {
            scrollOffset = clamp(scrollOffset + bounds.bottom() - contentClipBounds.bottom() + 4, 0, maxScrollOffset);
        }
        return true;
    }

    private ModernTextField findMcpInput(String target) {
        String normalized = target == null ? "" : target.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.equals("preset_name") || normalized.endsWith("/preset/name")
                || normalized.contains("/special/preset/name")) {
            activeGroup = Group.PRESET;
            return presetNameField;
        }
        if (normalized.equals("name_entry") || normalized.endsWith("/name/entry")
                || normalized.contains("/special/name/entry")) {
            activeGroup = Group.NAME_FILTER;
            return nameEntryField;
        }
        for (Map.Entry<Group, List<Section>> groupEntry : sections.entrySet()) {
            List<Section> groupSections = groupEntry.getValue();
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field == null || !field.usesInput() || field.input == null || field.key.isEmpty()) {
                        continue;
                    }
                    String key = field.key.toLowerCase(Locale.ROOT);
                    if (normalized.equals(key) || normalized.endsWith("/" + key)
                            || normalized.contains("/field/" + key + "/")) {
                        if (activeGroup != groupEntry.getKey()) {
                            activeGroup = groupEntry.getKey();
                            scrollOffset = 0;
                        }
                        return field.input;
                    }
                }
            }
        }
        for (RuleInputKey key : RuleInputKey.values()) {
            String name = key.name().toLowerCase(Locale.ROOT);
            if (normalized.equals(name) || normalized.endsWith("/" + name)
                    || normalized.contains("/input/" + name)) {
                return ruleInputs.get(key);
            }
        }
        return null;
    }

    @Override
    public void discardDraft() {
        if (!initialized || savedState == null) {
            return;
        }
        syncDraftOwner();
        // 其它会话停放的草稿一并丢弃；它们下次挂载时会从自己的配置文件重载。
        parkedDrafts.clear();
        liveDraft = null;
        savedState.apply();
        if (immediatePersistenceOccurred) {
            KillAuraHandler.saveConfig();
        }
        loadModeProfiles();
        immediatePersistenceOccurred = false;
        scrollbar.endDrag();
        draftDirty = false;
        refreshAfterStateChange();
        statusMessage = "";
    }

    @Override
    public boolean isDirty() {
        syncDraftOwner();
        return draftDirty || immediatePersistenceOccurred || !parkedDrafts.isEmpty();
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect contentBounds) {
        ModernMainLayout.Rect source = contentBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : contentBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 17));
        int insetY = Math.max(6, Math.min(9, source.height / 15));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
    }

    private void resizeGroupRail(int mouseX) {
        int paneX = panelBounds.x + 9;
        int paneWidth = Math.max(2, panelBounds.width - 18);
        int gap = Math.min(GROUP_EDITOR_GAP, Math.max(0, paneWidth - 2));
        int splitWidth = Math.max(2, paneWidth - gap);
        int pointerOffset = mouseX - paneX - gap / 2;
        ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitWidth, pointerOffset,
                GROUP_RAIL_MIN_WIDTH, EDITOR_MIN_WIDTH, GROUP_RAIL_FLOOR_WIDTH, EDITOR_FLOOR_WIDTH);
        groupRailRatio = split.ratio;
    }


    private void drawGroupRail(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(groupRailBounds.x, groupRailBounds.y, groupRailBounds.width,
                groupRailBounds.height, 6, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u183", groupRailBounds.x + 9, groupRailBounds.y + 8,
                ModernUiRenderer.readableText(ModernUiRenderer.SUBTLE_TEXT, ModernUiRenderer.SHELL_RAISED),
                groupRailBounds.width - 20);
        int y = groupRailBounds.y + 25;
        int rowHeight = Math.max(22, Math.min(28, (groupRailBounds.height - 34) / Group.values().length));
        for (Group group : Group.values()) {
            ModernMainLayout.Rect row = groupBounds(group, rowHeight);
            boolean selected = group == activeGroup;
            boolean hovered = row.contains(mouseX, mouseY);
            int fill = selected ? ModernUiRenderer.SELECTED_SURFACE
                    : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    fill, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            if (selected) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y + 4, 3, Math.max(8, row.height - 8), 2,
                        ModernUiRenderer.ACCENT);
            }
            ModernUiRenderer.drawText(fontRenderer, group.label, row.x + 9,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                    ModernUiRenderer.readableText(selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, fill),
                    row.width - 16);
            y += rowHeight + 4;
        }
    }

    private ModernMainLayout.Rect groupBounds(Group wanted, int rowHeight) {
        int y = groupRailBounds.y + 25;
        for (Group group : Group.values()) {
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(groupRailBounds.x + 5, y,
                    Math.max(1, groupRailBounds.width - 10), rowHeight);
            if (group == wanted) {
                return bounds;
            }
            y += rowHeight + 4;
        }
        return new ModernMainLayout.Rect(-1, -1, 0, 0);
    }

    private void drawEditorHeader(ModernMainLayout.Rect editorBounds, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, activeGroup.label, editorBounds.x + 10, editorBounds.y + 7,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(40, editorBounds.width - 28));
        drawInfoIcon(editorBounds.x + Math.min(Math.max(0, editorBounds.width - 22),
                fontRenderer.getStringWidth(activeGroup.label) + 17), editorBounds.y + 6, activeGroup.tooltip, mouseX,
                mouseY);
        ModernUiRenderer.drawDivider(editorBounds.x + 5, editorBounds.y + 22, Math.max(1, editorBounds.width - 10),
                ModernUiRenderer.BORDER_SUBTLE);
    }

    private void createInputFields(FontRenderer renderer) {
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field.usesInput()) {
                        field.input = createTextField(renderer, 256);
                    }
                }
            }
        }
        presetNameField = createTextField(renderer, 96);
        nameEntryField = createTextField(renderer, 128);
        for (RuleInputKey key : RuleInputKey.values()) {
            if (key != RuleInputKey.EXPRESSIONS) {
                ruleInputs.put(key, createTextField(renderer, 256));
            }
        }
        syncInputFields();
    }

    private ModernTextField createTextField(FontRenderer renderer, int maxLength) {
        ModernTextField field = new ModernTextField(0, renderer, 0, 0, 1, 18);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(Math.max(1, maxLength));
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        return field;
    }

    private int layoutActiveContent(int x, int y, int width, int columns, boolean assign) {
        sectionLayouts.clear();
        int currentY = y;
        List<Section> activeSections = sections.get(activeGroup);
        if (activeSections != null) {
            for (Section section : activeSections) {
                List<Field> visible = visibleFields(section);
                if (visible.isEmpty()) {
                    for (Field field : section.fields) {
                        field.clearBounds();
                    }
                    continue;
                }
                boolean card = section.modeCard != null;
                boolean open = !section.collapsible || section.expanded;
                int sectionTop = currentY;
                int headerHeight = card ? CARD_HEADER_HEIGHT : SECTION_HEADER_HEIGHT;
                currentY += headerHeight;
                if (assign) {
                    section.headerBounds = new ModernMainLayout.Rect(x, sectionTop, width, headerHeight);
                    section.activateBounds = null;
                }
                int sectionX = x;
                int sectionWidth = width;
                // 卡片内缩一圈，让边框把字段框在里面；折叠时仍要清掉字段坐标，否则隐藏输入框还能被点中。
                if (card) {
                    x = sectionX + CARD_PADDING;
                    width = Math.max(1, sectionWidth - CARD_PADDING * 2);
                }
                if (!open) {
                    for (Field field : visible) {
                        field.clearBounds();
                    }
                }
                int rowWidth = Math.max(1, (width - ROW_GAP * Math.max(0, columns - 1)) / columns);
                int index = open ? 0 : visible.size();
                while (index < visible.size()) {
                    Field first = visible.get(index);
                    boolean full = first.fullWidth || columns == 1;
                    if (full) {
                        int height = fieldHeight(first, width);
                        if (assign) {
                            first.rowBounds = new ModernMainLayout.Rect(x, currentY, width, height);
                        }
                        currentY += height + ROW_GAP;
                        index++;
                        continue;
                    }
                    Field second = index + 1 < visible.size() && !visible.get(index + 1).fullWidth
                            ? visible.get(index + 1) : null;
                    int height = fieldHeight(first, rowWidth);
                    if (second != null) {
                        height = Math.max(height, fieldHeight(second, rowWidth));
                    }
                    if (assign) {
                        first.rowBounds = new ModernMainLayout.Rect(x, currentY, rowWidth, height);
                        if (second != null) {
                            second.rowBounds = new ModernMainLayout.Rect(x + rowWidth + ROW_GAP, currentY, rowWidth,
                                    height);
                        }
                    }
                    currentY += height + ROW_GAP;
                    index += second == null ? 1 : 2;
                }
                x = sectionX;
                width = sectionWidth;
                if (card && open) {
                    currentY += CARD_PADDING - ROW_GAP;
                }
                if (assign) {
                    sectionLayouts.add(new SectionLayout(section,
                            new ModernMainLayout.Rect(x, sectionTop, width, Math.max(1, currentY - sectionTop))));
                }
                currentY += card ? ROW_GAP : 3;
            }
        }
        if (assign) {
            currentY = layoutSpecialContent(x, currentY, width, columns);
        } else {
            currentY += specialContentHeight(width, columns);
        }
        return Math.max(1, currentY - y);
    }

    private List<Field> visibleFields(Section section) {
        List<Field> result = new ArrayList<>();
        if (section == null) {
            return result;
        }
        for (Field field : section.fields) {
            if (field.visible()) {
                result.add(field);
            } else {
                field.clearBounds();
            }
        }
        return result;
    }

    private int fieldHeight(Field field, int width) {
        if (field.type == FieldType.CHOICE) {
            int available = Math.max(1, width - 18);
            int used = 0;
            int rows = 1;
            for (Choice choice : field.choices) {
                int optionWidth = Math.min(available, Math.max(48, fontRenderer.getStringWidth(choice.label) + 16));
                if (used > 0 && used + optionWidth > available) {
                    rows++;
                    used = 0;
                }
                used += optionWidth + 4;
            }
            return 23 + rows * 21;
        }
        return field.type == FieldType.TEXT && width < 210 ? 48 : ROW_HEIGHT;
    }

    private int specialContentHeight(int width, int columns) {
        switch (activeGroup) {
        case PRESET:
            return presetContentHeight();
        case NAME_FILTER:
            return nameContentHeight(width);
        case HUNT:
            return pickupContentHeight(width, columns);
        case TARGET:
            return huntScoreContentHeight();
        default:
            return 0;
        }
    }

    private int layoutSpecialContent(int x, int y, int width, int columns) {
        switch (activeGroup) {
        case PRESET:
            return layoutPresetContent(x, y, width);
        case NAME_FILTER:
            return layoutNameContent(x, y, width);
        case HUNT:
            return layoutPickupContent(x, y, width, columns);
        case TARGET:
            huntScoreBounds = new ModernMainLayout.Rect(x, y, width, huntScoreContentHeight());
            return y + huntScoreBounds.height;
        default:
            return y;
        }
    }

    private int presetContentHeight() {
        return SPECIAL_TITLE_HEIGHT + 27 + 24 + 25
                + presetListHeight() + 4;
    }

    private int presetListHeight() {
        return Math.max(64, 20 + presetCards.size() * (LIST_CARD_HEIGHT + LIST_CARD_GAP) + 8);
    }

    private int layoutPresetContent(int x, int y, int width) {
        y += SPECIAL_TITLE_HEIGHT;
        presetNameBounds = new ModernMainLayout.Rect(x, y, width, 22);
        y += 27;
        int gap = 4;
        int third = Math.max(1, (width - gap * 2) / 3);
        presetNewBounds = new ModernMainLayout.Rect(x, y, third, 20);
        presetApplyBounds = new ModernMainLayout.Rect(x + third + gap, y, third, 20);
        presetOverwriteBounds = new ModernMainLayout.Rect(x + (third + gap) * 2, y,
                Math.max(1, width - (third + gap) * 2), 20);
        y += 24;
        int half = Math.max(1, (width - gap) / 2);
        presetRenameBounds = new ModernMainLayout.Rect(x, y, half, 20);
        presetDeleteBounds = new ModernMainLayout.Rect(x + half + gap, y, Math.max(1, width - half - gap), 20);
        y += 25;
        presetListBounds = new ModernMainLayout.Rect(x, y, width,
                presetListHeight());
        return presetListBounds.bottom() + 4;
    }

    private int nameContentHeight(int width) {
        int nearbyHeight = listPanelHeight(nearbyNames.size());
        return SPECIAL_TITLE_HEIGHT + 27 + 31 + nearbyHeight + 8 + nameGroupsPanelHeight() + 4;
    }

    private int nameGroupCardHeight(KillAuraHandler.NameFilterGroup group) {
        int rows = group == null || group.names == null ? 0 : group.names.size();
        return NAME_GROUP_HEADER_HEIGHT + Math.max(1, rows) * (LIST_CARD_HEIGHT + LIST_CARD_GAP) + 3;
    }

    private int nameGroupsPanelHeight() {
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        if (groups.isEmpty()) {
            return 54;
        }
        int height = 24;
        for (KillAuraHandler.NameFilterGroup group : groups) {
            height += nameGroupCardHeight(group) + LIST_CARD_GAP;
        }
        return height + 3;
    }

    private int layoutNameContent(int x, int y, int width) {
        y += SPECIAL_TITLE_HEIGHT;
        nameEntryBounds = new ModernMainLayout.Rect(x, y, width, 22);
        y += 27;
        int gap = 4;
        int quarter = Math.max(1, (width - gap * 3) / 4);
        scanNamesBounds = new ModernMainLayout.Rect(x, y, quarter, 20);
        addToNameGroupBounds = new ModernMainLayout.Rect(x + quarter + gap, y, quarter, 20);
        newNameGroupBounds = new ModernMainLayout.Rect(x + (quarter + gap) * 2, y, quarter, 20);
        renameNameGroupBounds = new ModernMainLayout.Rect(x + (quarter + gap) * 3, y,
                Math.max(1, width - (quarter + gap) * 3), 20);
        y += 31;
        nearbyListBounds = new ModernMainLayout.Rect(x, y, width, listPanelHeight(nearbyNames.size()));
        y = nearbyListBounds.bottom() + 8;
        nameGroupsBounds = new ModernMainLayout.Rect(x, y, width, nameGroupsPanelHeight());
        nameGroupCards.clear();
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        int cardY = nameGroupsBounds.y + 20;
        for (int i = 0; i < groups.size(); i++) {
            KillAuraHandler.NameFilterGroup group = groups.get(i);
            ModernMainLayout.Rect card = new ModernMainLayout.Rect(x + 7, cardY, Math.max(1, width - 14),
                    nameGroupCardHeight(group));
            int buttonY = card.y + 4;
            int right = card.right() - 6;
            ModernMainLayout.Rect remove = new ModernMainLayout.Rect(right - 15, buttonY, 15, 18);
            ModernMainLayout.Rect down = new ModernMainLayout.Rect(right - 33, buttonY, 15, 18);
            ModernMainLayout.Rect up = new ModernMainLayout.Rect(right - 51, buttonY, 15, 18);
            ModernMainLayout.Rect enabledBounds = new ModernMainLayout.Rect(up.x - 4 - 40, buttonY, 40, 18);
            ModernMainLayout.Rect typeBounds = new ModernMainLayout.Rect(enabledBounds.x - 4 - 52, buttonY, 52, 18);
            NameGroupCard groupCard = new NameGroupCard(i, card, typeBounds, enabledBounds, up, down, remove);
            int rowY = card.y + NAME_GROUP_HEADER_HEIGHT;
            int rowCount = group.names == null ? 0 : group.names.size();
            for (int row = 0; row < rowCount; row++) {
                ModernMainLayout.Rect rowBounds = new ModernMainLayout.Rect(card.x + 6, rowY,
                        Math.max(1, card.width - 12), LIST_CARD_HEIGHT);
                ModernMainLayout.Rect rowRemove = new ModernMainLayout.Rect(rowBounds.right() - 18, rowBounds.y + 3, 15, 19);
                // 只有白名单的先后有意义（目标优先级），黑名单不给排序按钮，免得误以为有优先级。
                ModernMainLayout.Rect rowUp = group.blacklist ? null
                        : new ModernMainLayout.Rect(rowBounds.right() - 54, rowBounds.y + 3, 15, 19);
                ModernMainLayout.Rect rowDown = group.blacklist ? null
                        : new ModernMainLayout.Rect(rowBounds.right() - 36, rowBounds.y + 3, 15, 19);
                groupCard.rows.add(new NameRowHit(NameListKind.NEARBY, row, rowBounds, rowUp, rowDown, rowRemove));
                rowY += LIST_CARD_HEIGHT + LIST_CARD_GAP;
            }
            nameGroupCards.add(groupCard);
            cardY += card.height + LIST_CARD_GAP;
        }
        return nameGroupsBounds.bottom() + 4;
    }

    private int pickupContentHeight(int width, int columns) {
        int listHeight = listPanelHeight(ruleCount());
        int total = SPECIAL_TITLE_HEIGHT + 25 + listHeight + 6;
        if (selectedRule() == null) {
            return total;
        }
        int inputColumns = width >= 430 ? 2 : 1;
        return total + SPECIAL_TITLE_HEIGHT + 25 + 24
                + ruleInputRowsHeight(width, inputColumns) + 4;
    }

    private int layoutPickupContent(int x, int y, int width, int columns) {
        y += SPECIAL_TITLE_HEIGHT;
        int gap = 4;
        int half = Math.max(1, (width - gap) / 2);
        addRuleBounds = new ModernMainLayout.Rect(x, y, half, 20);
        deleteRuleBounds = new ModernMainLayout.Rect(x + half + gap, y, Math.max(1, width - half - gap), 20);
        y += 25;
        ruleListBounds = new ModernMainLayout.Rect(x, y, width, listPanelHeight(ruleCount()));
        y = ruleListBounds.bottom() + 6;
        ruleInputBounds.clear();
        rarityBounds.clear();
        if (selectedRule() == null) {
            return y;
        }
        y += SPECIAL_TITLE_HEIGHT;
        ruleToggleBounds = new ModernMainLayout.Rect(x, y, half, 20);
        ruleModeBounds = new ModernMainLayout.Rect(x + half + gap, y, Math.max(1, width - half - gap), 20);
        y += 25;
        int rarityWidth = Math.max(1, (width - gap * 3) / 4);
        String[] rarityTokens = new String[] { KillAuraHandler.HUNT_PICKUP_RARITY_COMMON,
                KillAuraHandler.HUNT_PICKUP_RARITY_UNCOMMON, KillAuraHandler.HUNT_PICKUP_RARITY_RARE,
                KillAuraHandler.HUNT_PICKUP_RARITY_EPIC };
        for (int i = 0; i < rarityTokens.length; i++) {
            int entryX = x + i * (rarityWidth + gap);
            rarityBounds.put(rarityTokens[i], new ModernMainLayout.Rect(entryX, y,
                    i == rarityTokens.length - 1 ? Math.max(1, x + width - entryX) : rarityWidth, 18));
        }
        y += 24;
        int inputColumns = width >= 430 ? 2 : 1;
        int inputWidth = Math.max(1, (width - ROW_GAP * Math.max(0, inputColumns - 1)) / inputColumns);
        RuleInputKey[] keys = RuleInputKey.values();
        int index = 0;
        while (index < keys.length) {
            RuleInputKey key = keys[index];
            if (key == RuleInputKey.EXPRESSIONS) {
                int height = ruleInputHeight(key, width);
                ruleInputBounds.put(key, new ModernMainLayout.Rect(x, y, width, height));
                y += height + ROW_GAP;
                index++;
                continue;
            }
            RuleInputKey second = index + 1 < keys.length && keys[index + 1] != RuleInputKey.EXPRESSIONS
                    ? keys[index + 1] : null;
            int rowWidth = second == null && inputColumns == 1 ? width : inputWidth;
            int height = Math.max(ruleInputHeight(key, rowWidth),
                    second == null ? 0 : ruleInputHeight(second, inputWidth));
            ruleInputBounds.put(key, new ModernMainLayout.Rect(x, y, rowWidth, height));
            if (second != null) {
                ruleInputBounds.put(second, new ModernMainLayout.Rect(x + inputWidth + ROW_GAP, y,
                        inputWidth, height));
            }
            y += height + ROW_GAP;
            index += second == null ? 1 : 2;
        }
        return y + 4;
    }

    private int ruleInputRowsHeight(int width, int inputColumns) {
        int inputWidth = Math.max(1, (width - ROW_GAP * Math.max(0, inputColumns - 1)) / inputColumns);
        RuleInputKey[] keys = RuleInputKey.values();
        int total = 0;
        int index = 0;
        while (index < keys.length) {
            RuleInputKey key = keys[index];
            if (key == RuleInputKey.EXPRESSIONS) {
                total += ruleInputHeight(key, width) + ROW_GAP;
                index++;
                continue;
            }
            RuleInputKey second = index + 1 < keys.length && keys[index + 1] != RuleInputKey.EXPRESSIONS
                    ? keys[index + 1] : null;
            int rowWidth = second == null && inputColumns == 1 ? width : inputWidth;
            int height = Math.max(ruleInputHeight(key, rowWidth),
                    second == null ? 0 : ruleInputHeight(second, inputWidth));
            total += height + ROW_GAP;
            index += second == null ? 1 : 2;
        }
        return total;
    }

    private int ruleInputHeight(RuleInputKey key, int width) {
        if (key == RuleInputKey.EXPRESSIONS) {
            int count = selectedRule() == null || selectedRule().itemFilterExpressions == null
                    ? 0 : selectedRule().itemFilterExpressions.size();
            int visibleRows = Math.max(1, count);
            return Math.max(96, 50 + visibleRows * (LIST_CARD_HEIGHT + LIST_CARD_GAP));
        }
        return width < 210 ? 48 : ROW_HEIGHT;
    }

    private int huntScoreContentHeight() {
        return SPECIAL_TITLE_HEIGHT + Math.min(170, Math.max(54, huntScoreEntries.size() * 22 + 20));
    }

    private int listPanelHeight(int count) {
        return Math.max(54, Math.max(0, count) * (LIST_CARD_HEIGHT + LIST_CARD_GAP) + 24);
    }

    private void drawSpecialContent(int x, int width, int mouseX, int mouseY) {
        int y = specialStartY();
        switch (activeGroup) {
        case PRESET:
            drawPresetContent(x, y, width, mouseX, mouseY);
            break;
        case NAME_FILTER:
            drawNameContent(x, y, width, mouseX, mouseY);
            break;
        case HUNT:
            drawPickupContent(x, y, width, mouseX, mouseY);
            break;
        case TARGET:
            drawHuntScoreContent(x, y, width, mouseX, mouseY);
            break;
        default:
            break;
        }
    }

    private int specialStartY() {
        int y = contentClipBounds.y + CONTENT_TOP_INSET - scrollOffset;
        List<Section> activeSections = sections.get(activeGroup);
        if (activeSections != null) {
            for (Section section : activeSections) {
                List<Field> visible = visibleFields(section);
                if (visible.isEmpty()) {
                    continue;
                }
                int sectionHeight = sectionLayoutsFor(section);
                y += sectionHeight + 3;
            }
        }
        return y;
    }

    private int sectionLayoutsFor(Section section) {
        for (SectionLayout layout : sectionLayouts) {
            if (layout.section == section) {
                return layout.bounds.height;
            }
        }
        return 0;
    }

    private void drawPresetContent(int x, int y, int width, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u184", x, y + 2, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth(t("gui.modern.killaura.u184")) + 5), y + 1,
                "gui.modern.killaura.u185", mouseX, mouseY);
        drawInput(presetNameField, presetNameBounds, "gui.modern.killaura.u186", true, mouseX, mouseY);
        drawActionButton(presetNewBounds, "gui.modern.killaura.u187", true, mouseX, mouseY, false);
        drawActionButton(presetApplyBounds, "gui.modern.killaura.u188", selectedPresetIndex >= 0, mouseX, mouseY, false);
        drawActionButton(presetOverwriteBounds, "gui.modern.killaura.u189", selectedPresetIndex >= 0, mouseX, mouseY, false);
        drawActionButton(presetRenameBounds, "gui.modern.killaura.u190", selectedPresetIndex >= 0, mouseX, mouseY, false);
        drawActionButton(presetDeleteBounds, "gui.modern.killaura.u191", selectedPresetIndex >= 0, mouseX, mouseY, true);
        ModernUiRenderer.drawSubtlePanel(presetListBounds.x, presetListBounds.y, presetListBounds.width,
                presetListBounds.height, 5, 0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u192", presetListBounds.x + 8, presetListBounds.y + 5,
                ModernUiRenderer.MUTED_TEXT, presetListBounds.width - 16);
        presetCardHits.clear();
        int cardY = presetListBounds.y + 20;
        if (presetCards.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u193", presetListBounds.x + 8, cardY + 8,
                    ModernUiRenderer.MUTED_TEXT, presetListBounds.width - 16);
            return;
        }
        for (int i = 0; i < presetCards.size(); i++) {
            KillAuraPreset preset = presetCards.get(i);
            ModernMainLayout.Rect card = new ModernMainLayout.Rect(presetListBounds.x + 7, cardY,
                    Math.max(1, presetListBounds.width - 14), LIST_CARD_HEIGHT);
            boolean selected = i == selectedPresetIndex;
            boolean hovered = card.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 4,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String summary = t("gui.modern.killaura.fmt.preset", safe(preset.name),
                    modeLabel(preset.attackMode), formatFloat(preset.attackRange));
            ModernUiRenderer.drawText(fontRenderer, summary, card.x + 8,
                    card.y + (card.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.SUBTLE_TEXT, card.width - 16);
            presetCardHits.add(new PresetCardHit(i, card));
            cardY += LIST_CARD_HEIGHT + LIST_CARD_GAP;
        }
    }

    private void drawNameContent(int x, int y, int width, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u194", x, y + 2, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth(t("gui.modern.killaura.u194")) + 5), y + 1,
                "gui.modern.killaura.u195", mouseX, mouseY);
        drawInput(nameEntryField, nameEntryBounds, "gui.modern.killaura.u196", true, mouseX, mouseY);
        drawActionButton(scanNamesBounds, "gui.modern.killaura.u197", true, mouseX, mouseY, false);
        drawActionButton(addToNameGroupBounds, "gui.modern.killaura.u282", hasNameEntry(), mouseX, mouseY, false);
        drawActionButton(newNameGroupBounds, "gui.modern.killaura.u283", true, mouseX, mouseY, false);
        drawActionButton(renameNameGroupBounds, "gui.modern.killaura.u284",
                hasNameEntry() && selectedNameGroup() != null, mouseX, mouseY, false);
        drawNameListPanel("gui.modern.killaura.u200", nearbyNames, nearbyListBounds, NameListKind.NEARBY, mouseX, mouseY);
        drawNameGroups(mouseX, mouseY);
    }

    private void drawNameGroups(int mouseX, int mouseY) {
        if (nameGroupsBounds == null) {
            return;
        }
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        ModernUiRenderer.drawSubtlePanel(nameGroupsBounds.x, nameGroupsBounds.y, nameGroupsBounds.width,
                nameGroupsBounds.height, 5, 0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, t("gui.modern.killaura.u285") + "  " + groups.size(),
                nameGroupsBounds.x + 8, nameGroupsBounds.y + 5, ModernUiRenderer.MUTED_TEXT,
                Math.max(30, nameGroupsBounds.width - 16));
        if (groups.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u290", nameGroupsBounds.x + 8,
                    nameGroupsBounds.y + 27, ModernUiRenderer.MUTED_TEXT, nameGroupsBounds.width - 16);
            return;
        }
        for (NameGroupCard card : nameGroupCards) {
            if (card.index < 0 || card.index >= groups.size()) {
                continue;
            }
            KillAuraHandler.NameFilterGroup group = groups.get(card.index);
            boolean selected = card.index == selectedNameGroupIndex;
            ModernMainLayout.Rect bounds = card.bounds;
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : ModernUiRenderer.SHELL_RAISED,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            int titleRight = card.typeBounds.x - 6;
            ModernUiRenderer.drawText(fontRenderer, nameGroupTitle(group, card.index) + "  "
                            + (group.names == null ? 0 : group.names.size()),
                    bounds.x + 8, bounds.y + (NAME_GROUP_HEADER_HEIGHT - fontRenderer.FONT_HEIGHT) / 2,
                    group.enabled ? selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.TEXT
                            : ModernUiRenderer.MUTED_TEXT,
                    Math.max(24, titleRight - bounds.x - 8));
            drawActionButton(card.typeBounds, group.blacklist ? "gui.modern.killaura.u287" : "gui.modern.killaura.u286",
                    true, mouseX, mouseY, group.blacklist);
            drawActionButton(card.enabledBounds, group.enabled ? "gui.modern.killaura.u288" : "gui.modern.killaura.u289",
                    true, mouseX, mouseY, false);
            drawTinyButton(card.moveUpBounds, "↑", card.index > 0, mouseX, mouseY);
            drawTinyButton(card.moveDownBounds, "↓", card.index + 1 < groups.size(), mouseX, mouseY);
            drawTinyButton(card.removeBounds, "×", true, mouseX, mouseY);
            if (card.rows.isEmpty()) {
                ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u203", bounds.x + 12,
                        bounds.y + NAME_GROUP_HEADER_HEIGHT + 8, ModernUiRenderer.MUTED_TEXT, bounds.width - 24);
                continue;
            }
            for (NameRowHit row : card.rows) {
                String value = row.index < group.names.size() && group.names.get(row.index) != null
                        ? group.names.get(row.index) : "";
                ModernMainLayout.Rect rowBounds = row.rowBounds;
                boolean hovered = rowBounds.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(rowBounds.x, rowBounds.y, rowBounds.width, rowBounds.height, 4,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        ModernUiRenderer.BORDER_SUBTLE);
                int actionWidth = group.blacklist ? 21 : 58;
                ModernUiRenderer.drawText(fontRenderer, (row.index + 1) + ". " + value, rowBounds.x + 7,
                        rowBounds.y + (rowBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                        group.enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                        Math.max(24, rowBounds.width - actionWidth - 14));
                drawTinyButton(row.moveUpBounds, "↑", row.index > 0, mouseX, mouseY);
                drawTinyButton(row.moveDownBounds, "↓", row.index + 1 < group.names.size(), mouseX, mouseY);
                drawTinyButton(row.removeBounds, "×", true, mouseX, mouseY);
            }
        }
    }

    private void drawNameListPanel(String title, List<String> values, ModernMainLayout.Rect bounds, NameListKind kind,
            int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5, 0xFF111A22,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, t(title) + "  " + (values == null ? 0 : values.size()), bounds.x + 8,
                bounds.y + 5, ModernUiRenderer.MUTED_TEXT, Math.max(30, bounds.width - 16));
        int cardY = bounds.y + 20;
        nameRowHits.removeIf(hit -> hit.kind == kind);
        if (values == null || values.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u203", bounds.x + 8, cardY + 7, ModernUiRenderer.MUTED_TEXT,
                    bounds.width - 16);
            return;
        }
        int maxRows = Math.max(1, (bounds.height - 24) / (LIST_CARD_HEIGHT + LIST_CARD_GAP));
        for (int i = 0; i < Math.min(values.size(), maxRows); i++) {
            String value = values.get(i) == null ? "" : values.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(bounds.x + 7, cardY,
                    Math.max(1, bounds.width - 14), LIST_CARD_HEIGHT);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    ModernUiRenderer.BORDER_SUBTLE);
            String typeLabel = i < nearbyNameTypes.size() ? nearbyNameTypes.get(i) : "";
            int typeWidth = typeLabel.isEmpty() ? 0 : fontRenderer.getStringWidth(typeLabel) + 10;
            int textRight = row.right() - 4 - typeWidth;
            ModernUiRenderer.drawText(fontRenderer, (i + 1) + ". " + value, row.x + 7,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT,
                    Math.max(24, textRight - row.x - 10));
            if (!typeLabel.isEmpty()) {
                int labelWidth = fontRenderer.getStringWidth(typeLabel);
                ModernUiRenderer.drawText(fontRenderer, typeLabel, row.right() - 4 - labelWidth,
                        row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.MUTED_TEXT,
                        labelWidth + 4);
            }
            nameRowHits.add(new NameRowHit(kind, i, row, null, null, null));
            cardY += LIST_CARD_HEIGHT + LIST_CARD_GAP;
        }
        if (values.size() > maxRows) {
            String extra = t("gui.modern.killaura.fmt.more", String.valueOf(values.size() - maxRows));
            int extraWidth = fontRenderer.getStringWidth(extra);
            ModernUiRenderer.drawText(fontRenderer, extra, bounds.right() - extraWidth - 7, bounds.y + 5,
                    ModernUiRenderer.MUTED_TEXT, extraWidth);
        }
    }

    private void drawTinyButton(ModernMainLayout.Rect bounds, String label, boolean enabled, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 3,
                hovered ? ModernUiRenderer.SURFACE_PRESSED : 0xFF151F28,
                enabled ? hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE : 0xFF202A33);
        com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 4,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT,
                bounds.width - 7);
    }

    private void drawPickupContent(int x, int y, int width, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u204", x, y + 2, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth(t("gui.modern.killaura.u204")) + 5), y + 1,
                "gui.modern.killaura.u205",
                mouseX, mouseY);
        drawActionButton(addRuleBounds, "gui.modern.killaura.u206", true, mouseX, mouseY, false);
        drawActionButton(deleteRuleBounds, "gui.modern.killaura.u207", selectedRule() != null, mouseX, mouseY, true);
        drawRuleList(mouseX, mouseY);
        HuntPickupRule rule = selectedRule();
        if (rule == null) {
            return;
        }
        int editorY = ruleListBounds.bottom() + 6;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u208", x, editorY + 2, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth(t("gui.modern.killaura.u208")) + 5), editorY + 1,
                "gui.modern.killaura.u209", mouseX, mouseY);
        drawRuleToggle(rule, mouseX, mouseY);
        drawRuleRarityOptions(rule, mouseX, mouseY);
        for (RuleInputKey key : RuleInputKey.values()) {
            ModernMainLayout.Rect bounds = ruleInputBounds.get(key);
            if (key == RuleInputKey.EXPRESSIONS) {
                drawRuleExpressionPanel(rule, bounds, mouseX, mouseY);
                continue;
            }
            ModernTextField input = ruleInputs.get(key);
            if (bounds == null || input == null) {
                continue;
            }
            drawRuleInput(key, input, bounds, mouseX, mouseY);
        }
    }

    private void drawRuleList(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(ruleListBounds.x, ruleListBounds.y, ruleListBounds.width, ruleListBounds.height,
                5, 0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, t("gui.modern.killaura.fmt.rule_list", String.valueOf(ruleCount())), ruleListBounds.x + 8, ruleListBounds.y + 5,
                ModernUiRenderer.MUTED_TEXT, ruleListBounds.width - 16);
        ruleCardHits.clear();
        if (KillAuraHandler.cfg().huntPickupRules == null || KillAuraHandler.cfg().huntPickupRules.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u210", ruleListBounds.x + 8,
                    ruleListBounds.y + 29, ModernUiRenderer.MUTED_TEXT, ruleListBounds.width - 16);
            return;
        }
        int cardY = ruleListBounds.y + 20;
        int maxRows = Math.max(1, (ruleListBounds.height - 24) / (LIST_CARD_HEIGHT + LIST_CARD_GAP));
        int displayed = 0;
        for (int index = 0; index < KillAuraHandler.cfg().huntPickupRules.size() && displayed < maxRows; index++) {
            HuntPickupRule rule = KillAuraHandler.cfg().huntPickupRules.get(index);
            if (rule == null) {
                continue;
            }
            ModernMainLayout.Rect card = new ModernMainLayout.Rect(ruleListBounds.x + 7, cardY,
                    Math.max(1, ruleListBounds.width - 14), LIST_CARD_HEIGHT);
            boolean selected = index == selectedRuleIndex;
            boolean hovered = card.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 4,
                    selected ? ModernUiRenderer.SELECTED_SURFACE : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            String mode = KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(rule.mode) ? "gui.modern.killaura.u211" : "gui.modern.killaura.u212";
            String state = rule.enabled ? "gui.modern.killaura.u213" : "gui.modern.killaura.u214";
            String summary = t("gui.modern.killaura.fmt.rule_row", t(safe(rule.name)), t(mode), t(state),
                    String.valueOf(rule.priority));
            ModernUiRenderer.drawText(fontRenderer, summary, card.x + 8,
                    card.y + (card.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, card.width - 16);
            ruleCardHits.add(new RuleCardHit(index, card));
            cardY += LIST_CARD_HEIGHT + LIST_CARD_GAP;
            displayed++;
        }
        if (KillAuraHandler.cfg().huntPickupRules.size() > displayed) {
            String extra = t("gui.modern.killaura.fmt.more",
                    String.valueOf(KillAuraHandler.cfg().huntPickupRules.size() - displayed));
            int extraWidth = fontRenderer.getStringWidth(extra);
            ModernUiRenderer.drawText(fontRenderer, extra, ruleListBounds.right() - extraWidth - 7,
                    ruleListBounds.y + 5, ModernUiRenderer.MUTED_TEXT, extraWidth);
        }
    }

    private void drawRuleToggle(HuntPickupRule rule, int mouseX, int mouseY) {
        boolean hoverEnabled = ruleToggleBounds != null && ruleToggleBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(ruleToggleBounds.x, ruleToggleBounds.y, ruleToggleBounds.width,
                ruleToggleBounds.height, 4, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u215", ruleToggleBounds.x + 7,
                ruleToggleBounds.y + (ruleToggleBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                ModernUiRenderer.SUBTLE_TEXT, ruleToggleBounds.width - 52);
        ModernUiRenderer.drawToggle(ruleToggleBounds.right() - 39, ruleToggleBounds.y + 2, 30, 15, rule.enabled,
                hoverEnabled);

        boolean block = KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(rule.mode);
        boolean hoverMode = ruleModeBounds != null && ruleModeBounds.contains(mouseX, mouseY);
        int modeColor = block ? ModernUiRenderer.DANGER : ModernUiRenderer.SUCCESS;
        int modeFill = ModernUiRenderer.lerpRgb(ModernUiRenderer.SURFACE, modeColor, 0.22F);
        ModernUiRenderer.drawSubtlePanel(ruleModeBounds.x, ruleModeBounds.y, ruleModeBounds.width, ruleModeBounds.height,
                4, modeFill, modeColor);
        ModernUiRenderer.drawText(fontRenderer, block ? "gui.modern.killaura.u216" : "gui.modern.killaura.u217", ruleModeBounds.x + 7,
                ruleModeBounds.y + (ruleModeBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                hoverMode ? ModernUiRenderer.TEXT : ModernUiRenderer.readableText(modeColor, modeFill),
                ruleModeBounds.width - 14);
    }

    private void drawRuleRarityOptions(HuntPickupRule rule, int mouseX, int mouseY) {
        for (Map.Entry<String, ModernMainLayout.Rect> entry : rarityBounds.entrySet()) {
            String token = entry.getKey();
            ModernMainLayout.Rect bounds = entry.getValue();
            boolean selected = rule.rarityFilters != null && rule.rarityFilters.contains(token);
            boolean hovered = bounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, KillAuraHandler.getHuntPickupRarityDisplayName(token), bounds.x + 6,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, bounds.width - 10);
        }
    }

    private void drawRuleInput(RuleInputKey key, ModernTextField input, ModernMainLayout.Rect row, int mouseX, int mouseY) {
        boolean compact = row.width < 210;
        int titleY = row.y + (compact ? 5 : (row.height - fontRenderer.FONT_HEIGHT) / 2);
        int labelWidth = compact ? row.width - 20 : Math.max(28, row.width - 120);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, key.label, row.x + 9, titleY, ModernUiRenderer.SUBTLE_TEXT, labelWidth);
        drawInfoIcon(row.x + 9 + Math.min(Math.max(0, labelWidth - 12), fontRenderer.getStringWidth(key.label) + 5),
                titleY - 1, key.tooltip, mouseX, mouseY);
        ModernMainLayout.Rect inputBounds = compact ? new ModernMainLayout.Rect(row.x + 9, row.bottom() - 25,
                Math.max(1, row.width - 18), 21) : new ModernMainLayout.Rect(row.right() - 110, row.y + 5, 102, 22);
        drawInput(input, inputBounds, "", true, mouseX, mouseY);
    }

    private void drawRuleExpressionPanel(HuntPickupRule rule, ModernMainLayout.Rect bounds,
            int mouseX, int mouseY) {
        if (rule == null || bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5,
                0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u242", bounds.x + 9, bounds.y + 7,
                ModernUiRenderer.TEXT, Math.max(70, bounds.width - 112));
        drawInfoIcon(bounds.x + Math.min(Math.max(0, bounds.width - 112),
                fontRenderer.getStringWidth(t("gui.modern.killaura.u242")) + 5), bounds.y + 6,
                "gui.modern.killaura.u243", mouseX, mouseY);
        ruleExpressionAddBounds = new ModernMainLayout.Rect(bounds.right() - 82, bounds.y + 6, 72, 21);
        drawActionButton(ruleExpressionAddBounds, "gui.modern.killaura.u244", true, mouseX, mouseY, false);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u243", bounds.x + 9, bounds.y + 27,
                ModernUiRenderer.MUTED_TEXT, Math.max(70, bounds.width - 18));

        ruleExpressionHits.clear();
        List<String> expressions = rule.itemFilterExpressions == null
                ? Collections.<String>emptyList() : rule.itemFilterExpressions;
        if (expressions.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u247", bounds.x + 10, bounds.y + 51,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, bounds.width - 20));
            return;
        }
        int y = bounds.y + 45;
        int maxRows = Math.max(1, (bounds.height - 50) / (LIST_CARD_HEIGHT + LIST_CARD_GAP));
        int visible = Math.min(expressions.size(), maxRows);
        for (int index = 0; index < visible; index++) {
            String expression = safe(expressions.get(index)).trim();
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(bounds.x + 8, y,
                    Math.max(1, bounds.width - 16), LIST_CARD_HEIGHT);
            boolean selected = index == selectedRuleExpressionIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SELECTED_SURFACE
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            int buttonWidth = 58;
            int textRight = row.right() - buttonWidth - 4;
            ModernUiRenderer.drawText(fontRenderer, (index + 1) + ". " + expression, row.x + 8,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.SELECTED_TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(28, textRight - row.x - 10));
            ModernMainLayout.Rect edit = new ModernMainLayout.Rect(row.right() - 54, row.y + 3, 25, 19);
            ModernMainLayout.Rect delete = new ModernMainLayout.Rect(row.right() - 27, row.y + 3, 24, 19);
            drawTinyButton(edit, "gui.modern.killaura.u245", true, mouseX, mouseY);
            drawTinyButton(delete, "gui.modern.killaura.u246", true, mouseX, mouseY);
            ruleExpressionHits.add(new RuleExpressionHit(index, row, edit, delete));
            y += LIST_CARD_HEIGHT + LIST_CARD_GAP;
        }
        if (expressions.size() > visible) {
            ModernUiRenderer.drawText(fontRenderer,
                    t("gui.modern.killaura.fmt.more", String.valueOf(expressions.size() - visible)),
                    bounds.right() - 78, bounds.y + 27, ModernUiRenderer.MUTED_TEXT, 68);
        }
    }

    private void drawHuntScoreContent(int x, int y, int width, int mouseX, int mouseY) {
        if (huntScoreBounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(huntScoreBounds.x, huntScoreBounds.y, huntScoreBounds.width,
                huntScoreBounds.height, 5, 0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u218", huntScoreBounds.x + 8, huntScoreBounds.y + 5,
                ModernUiRenderer.MUTED_TEXT, huntScoreBounds.width - 18);
        drawInfoIcon(huntScoreBounds.x + Math.min(Math.max(0, huntScoreBounds.width - 14),
                fontRenderer.getStringWidth(t("gui.modern.killaura.u218")) + 13), huntScoreBounds.y + 4,
                "gui.modern.killaura.u219", mouseX, mouseY);
        if (huntScoreEntries.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u220", huntScoreBounds.x + 8,
                    huntScoreBounds.y + 29, ModernUiRenderer.MUTED_TEXT, huntScoreBounds.width - 16);
            return;
        }
        int rowY = huntScoreBounds.y + 20;
        int limit = Math.min(7, huntScoreEntries.size());
        for (int i = 0; i < limit; i++) {
            HuntScoreDebugEntry entry = huntScoreEntries.get(i);
            String text = t("gui.modern.killaura.fmt.score", safe(entry.name), formatDouble(entry.totalScore),
                    t(entry.visible ? "gui.modern.killaura.visible" : "gui.modern.killaura.blocked"));
            ModernUiRenderer.drawText(fontRenderer, text, huntScoreBounds.x + 8, rowY, ModernUiRenderer.SUBTLE_TEXT,
                    huntScoreBounds.width - 16);
            rowY += 22;
        }
    }

    private void drawFooter(int mouseX, int mouseY) {
        int y = panelBounds.bottom() - 27;
        int gap = 6;
        int width = Math.max(1, (panelBounds.width - 28 - gap * 3) / 4);
        int toggleWidth = Math.min(42, Math.max(1, width - 8));
        headerToggleBounds = new ModernMainLayout.Rect(panelBounds.x + 14 + width - toggleWidth - 4,
                y + 3, toggleWidth, 16);
        ModernUiRenderer.drawToggle(headerToggleBounds.x, headerToggleBounds.y, toggleWidth, 16,
                KillAuraHandler.enabled(), headerToggleBounds.contains(mouseX, mouseY));
        ModernUiRenderer.drawText(fontRenderer, KillAuraHandler.enabled() ? "gui.modern.killaura.u180"
                : "gui.modern.killaura.u181", panelBounds.x + 18, y + 6,
                KillAuraHandler.enabled() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, width - toggleWidth - 24));
        drawInfoIcon(headerToggleBounds.x - 15, y + 5, "gui.modern.killaura.u182", mouseX, mouseY);
        if (statusVisible()) {
            ModernUiRenderer.drawText(fontRenderer, statusMessage, panelBounds.x + 14, y - 16,
                    ModernUiRenderer.SUCCESS, Math.max(1, panelBounds.width - 28));
        }
        saveBounds = new ModernMainLayout.Rect(panelBounds.x + 14 + width + gap, y, width, 21);
        defaultsBounds = new ModernMainLayout.Rect(saveBounds.right() + gap, y, width, 21);
        revertBounds = new ModernMainLayout.Rect(defaultsBounds.right() + gap, y,
                Math.max(1, panelBounds.right() - 14 - defaultsBounds.right() - gap), 21);
        drawActionButton(saveBounds, "gui.modern.killaura.u221", true, mouseX, mouseY, false);
        drawActionButton(defaultsBounds, "gui.modern.killaura.u114", true, mouseX, mouseY, false);
        drawActionButton(revertBounds, "gui.modern.killaura.u222", true, mouseX, mouseY, false);
    }

    private void drawActionButton(ModernMainLayout.Rect bounds, String label, boolean enabled, int mouseX, int mouseY,
            boolean danger) {
        if (bounds == null) {
            return;
        }
        boolean hovered = enabled && bounds.contains(mouseX, mouseY);
        int fill = !enabled ? 0xFF141D25
                : danger ? hovered ? 0xFFE69073 : 0xFFD46B57
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int border = !enabled ? ModernUiRenderer.BORDER_SUBTLE
                : danger ? 0xFFF1A48B : hovered ? 0xFF637886 : ModernUiRenderer.BORDER;
        if (!danger && "gui.modern.killaura.u221".equals(label)) {
            fill = hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT;
            border = 0xFFFFABC0;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, fill, border);
        int labelWidth = fontRenderer.getStringWidth(com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.mythos.mythosScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.mythos.mythosScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? danger || "gui.modern.killaura.u221".equals(label) ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT
                        : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, bounds.width - 8));
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (contentClipBounds == null || maxScrollOffset <= 0) {
            scrollbar.idle();
            return;
        }
        scrollbar.draw(contentClipBounds, scrollOffset, maxScrollOffset, contentClipBounds.height,
                contentClipBounds.height + maxScrollOffset, mouseX, mouseY, value -> scrollOffset = value);
    }

    private boolean handleGroupClick(int mouseX, int mouseY) {
        if (groupRailBounds == null || !groupRailBounds.contains(mouseX, mouseY)) {
            return false;
        }
        int rowHeight = Math.max(22, Math.min(28, (groupRailBounds.height - 34) / Group.values().length));
        for (Group group : Group.values()) {
            ModernMainLayout.Rect row = groupBounds(group, rowHeight);
            if (row.contains(mouseX, mouseY)) {
                if (activeGroup != group) {
                    applyEditableValues();
                    activeGroup = group;
                    scrollOffset = 0;
                    clearInputFocus();
                    refreshAfterStateChange();
                }
                return true;
            }
        }
        return true;
    }

    private boolean handleFieldClick(int mouseX, int mouseY) {
        List<Section> activeSections = sections.get(activeGroup);
        if (activeSections == null) {
            return false;
        }
        for (SectionLayout layout : sectionLayouts) {
            Section section = layout.section;
            if (!section.collapsible || !contains(section.headerBounds, mouseX, mouseY)) {
                continue;
            }
            if (section.modeCard != null && contains(section.activateBounds, mouseX, mouseY)) {
                switchAttackMode(section.modeCard);
                return true;
            }
            clearInputFocus();
            section.expanded = !section.expanded;
            return true;
        }
        for (Section section : activeSections) {
            for (Field field : section.fields) {
                if (!field.visible() || field.rowBounds == null || !field.rowBounds.contains(mouseX, mouseY)) {
                    continue;
                }
                if (contains(field.infoBounds, mouseX, mouseY)) {
                    return true;
                }
                if (!field.enabled()) {
                    return true;
                }
                if (field.type == FieldType.TOGGLE) {
                    boolean value = !State.getBoolean(field.key);
                    // 只瞄准会把模式强制改成序列；先走切换流程，把当前模式的参数存回它自己的档案。
                    if ("aimOnlyMode".equals(field.key) && value) {
                        switchAttackMode(KillAuraHandler.ATTACK_MODE_SEQUENCE);
                    }
                    State.setBoolean(field.key, value);
                    enforceTargetSelection();
                    draftDirty = true;
                    return true;
                }
                if (field.type == FieldType.CHOICE) {
                    String selected = findChoiceAt(field, mouseX, mouseY);
                    if (selected != null && "attackMode".equals(field.key)) {
                        switchAttackMode(selected);
                    } else if (selected != null) {
                        State.setText(field.key, selected);
                        if ("huntMode".equals(field.key)) {
                            KillAuraHandler.setHuntMode(selected);
                        }
                        draftDirty = true;
                        refreshAfterStateChange();
                    }
                    return true;
                }
                if (field.type == FieldType.SEQUENCE && contains(field.controlBounds, mouseX, mouseY)) {
                    openAttackSequencePicker();
                    return true;
                }
                if (field.type == FieldType.ACTION && contains(field.controlBounds, mouseX, mouseY)) {
                    if (field.action != null) {
                        applyEditableValues();
                        draftDirty = true;
                        field.action.execute();
                    }
                    return true;
                }
                if (field.usesInput() && contains(field.controlBounds, mouseX, mouseY)) {
                    clearInputFocus();
                    if (field.input != null) {
                        field.input.setFocused(true);
                        field.input.mouseClicked(mouseX, mouseY, 0);
                    }
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private void openAttackSequencePicker() {
        clearInputFocus();
        PathSequenceManager.initializePathSequences();
        attackSequencePicker.open(State.getText("attackSequenceName"));
    }

    private String findChoiceAt(Field field, int mouseX, int mouseY) {
        int x = field.rowBounds.x + 9;
        int y = field.rowBounds.y + 20;
        int available = Math.max(1, field.rowBounds.width - 18);
        for (Choice choice : field.choices) {
            int optionWidth = Math.min(available, Math.max(48, fontRenderer.getStringWidth(choice.label) + 16));
            if (x > field.rowBounds.x + 9 && x + optionWidth > field.rowBounds.right() - 9) {
                x = field.rowBounds.x + 9;
                y += 21;
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, optionWidth, 18);
            if (bounds.contains(mouseX, mouseY)) {
                return choice.value;
            }
            x += optionWidth + 4;
        }
        return null;
    }

    private boolean handleSpecialClick(int mouseX, int mouseY) {
        switch (activeGroup) {
        case PRESET:
            return handlePresetClick(mouseX, mouseY);
        case NAME_FILTER:
            return handleNameClick(mouseX, mouseY);
        case HUNT:
            return handlePickupClick(mouseX, mouseY);
        default:
            return false;
        }
    }

    private boolean handlePresetClick(int mouseX, int mouseY) {
        if (contains(presetNameBounds, mouseX, mouseY)) {
            clearInputFocus();
            presetNameField.setFocused(true);
            presetNameField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (contains(presetNewBounds, mouseX, mouseY)) {
            applyEditableValues();
            String name = safe(presetNameField.getText()).trim();
            if (KillAuraHandler.saveCurrentAsPreset(name)) {
                immediatePersistenceOccurred = true;
                refreshPresetCards(name);
                showStatus("gui.modern.killaura.u223");
            } else {
                showStatus("gui.modern.killaura.u224");
            }
            return true;
        }
        if (contains(presetApplyBounds, mouseX, mouseY) && selectedPreset() != null) {
            if (KillAuraHandler.applyPresetByName(selectedPreset().name)) {
                immediatePersistenceOccurred = true;
                draftDirty = true;
                refreshAfterStateChange();
                showStatus("gui.modern.killaura.u225");
            }
            return true;
        }
        if (contains(presetOverwriteBounds, mouseX, mouseY) && selectedPreset() != null) {
            applyEditableValues();
            if (KillAuraHandler.overwritePreset(selectedPreset().name)) {
                immediatePersistenceOccurred = true;
                draftDirty = true;
                refreshPresetCards(selectedPreset().name);
                showStatus("gui.modern.killaura.u226");
            }
            return true;
        }
        if (contains(presetRenameBounds, mouseX, mouseY) && selectedPreset() != null) {
            String newName = safe(presetNameField.getText()).trim();
            if (KillAuraHandler.renamePreset(selectedPreset().name, newName)) {
                immediatePersistenceOccurred = true;
                draftDirty = true;
                refreshPresetCards(newName);
                showStatus("gui.modern.killaura.u227");
            } else {
                showStatus("gui.modern.killaura.u228");
            }
            return true;
        }
        if (contains(presetDeleteBounds, mouseX, mouseY) && selectedPreset() != null) {
            if (KillAuraHandler.deletePreset(selectedPreset().name)) {
                immediatePersistenceOccurred = true;
                draftDirty = true;
                refreshPresetCards("");
                showStatus("gui.modern.killaura.u229");
            }
            return true;
        }
        for (PresetCardHit hit : presetCardHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                selectedPresetIndex = hit.index;
                presetNameField.setText(selectedPreset() == null ? "" : safe(selectedPreset().name));
                return true;
            }
        }
        return true;
    }

    private boolean handleNameClick(int mouseX, int mouseY) {
        if (contains(nameEntryBounds, mouseX, mouseY)) {
            clearInputFocus();
            nameEntryField.setFocused(true);
            nameEntryField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (contains(scanNamesBounds, mouseX, mouseY)) {
            applyEditableValues();
            refreshNearbyNames();
            showStatus("gui.modern.killaura.u230");
            return true;
        }
        if (contains(addToNameGroupBounds, mouseX, mouseY) && hasNameEntry()) {
            KillAuraHandler.NameFilterGroup group = selectedNameGroup();
            if (group == null) {
                // 还没有分组时直接建一个，免得用户先得去点「新建分组」才能加名字。
                group = createNameGroup("");
            }
            addName(group.names, nameEntryField.getText());
            nameEntryField.setText("");
            afterNameGroupsChanged();
            return true;
        }
        if (contains(newNameGroupBounds, mouseX, mouseY)) {
            createNameGroup(nameEntryField.getText());
            nameEntryField.setText("");
            afterNameGroupsChanged();
            showStatus("gui.modern.killaura.u292");
            return true;
        }
        if (contains(renameNameGroupBounds, mouseX, mouseY) && hasNameEntry() && selectedNameGroup() != null) {
            selectedNameGroup().name = nameEntryField.getText().trim();
            nameEntryField.setText("");
            afterNameGroupsChanged();
            return true;
        }
        for (NameRowHit hit : new ArrayList<>(nameRowHits)) {
            if (hit.rowBounds == null || !hit.rowBounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit.index >= 0 && hit.index < nearbyNames.size()) {
                nameEntryField.setText(nearbyNames.get(hit.index));
            }
            return true;
        }
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        for (NameGroupCard card : new ArrayList<>(nameGroupCards)) {
            if (card.bounds == null || !card.bounds.contains(mouseX, mouseY)
                    || card.index < 0 || card.index >= groups.size()) {
                continue;
            }
            KillAuraHandler.NameFilterGroup group = groups.get(card.index);
            selectedNameGroupIndex = card.index;
            if (contains(card.typeBounds, mouseX, mouseY)) {
                group.blacklist = !group.blacklist;
                afterNameGroupsChanged();
            } else if (contains(card.enabledBounds, mouseX, mouseY)) {
                group.enabled = !group.enabled;
                afterNameGroupsChanged();
            } else if (contains(card.moveUpBounds, mouseX, mouseY) && card.index > 0) {
                Collections.swap(groups, card.index, card.index - 1);
                selectedNameGroupIndex = card.index - 1;
                afterNameGroupsChanged();
            } else if (contains(card.moveDownBounds, mouseX, mouseY) && card.index + 1 < groups.size()) {
                Collections.swap(groups, card.index, card.index + 1);
                selectedNameGroupIndex = card.index + 1;
                afterNameGroupsChanged();
            } else if (contains(card.removeBounds, mouseX, mouseY)) {
                groups.remove(card.index);
                selectedNameGroupIndex = Math.min(card.index, groups.size() - 1);
                afterNameGroupsChanged();
            } else {
                handleNameGroupRowClick(card, group, mouseX, mouseY);
            }
            return true;
        }
        return true;
    }

    private void handleNameGroupRowClick(NameGroupCard card, KillAuraHandler.NameFilterGroup group,
            int mouseX, int mouseY) {
        List<String> values = group.names;
        for (NameRowHit row : card.rows) {
            if (values == null || !contains(row.rowBounds, mouseX, mouseY) || row.index >= values.size()) {
                continue;
            }
            if (contains(row.removeBounds, mouseX, mouseY)) {
                values.remove(row.index);
                afterNameGroupsChanged();
            } else if (contains(row.moveUpBounds, mouseX, mouseY) && row.index > 0) {
                Collections.swap(values, row.index, row.index - 1);
                afterNameGroupsChanged();
            } else if (contains(row.moveDownBounds, mouseX, mouseY) && row.index + 1 < values.size()) {
                Collections.swap(values, row.index, row.index + 1);
                afterNameGroupsChanged();
            }
            return;
        }
    }

    private List<KillAuraHandler.NameFilterGroup> nameGroups() {
        if (KillAuraHandler.cfg().nameFilterGroups == null) {
            KillAuraHandler.cfg().nameFilterGroups = new ArrayList<>();
        }
        return KillAuraHandler.cfg().nameFilterGroups;
    }

    private KillAuraHandler.NameFilterGroup selectedNameGroup() {
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        return selectedNameGroupIndex >= 0 && selectedNameGroupIndex < groups.size()
                ? groups.get(selectedNameGroupIndex) : null;
    }

    private KillAuraHandler.NameFilterGroup createNameGroup(String rawName) {
        List<KillAuraHandler.NameFilterGroup> groups = nameGroups();
        String name = rawName == null ? "" : rawName.trim();
        KillAuraHandler.NameFilterGroup group = new KillAuraHandler.NameFilterGroup(name, true, false,
                new ArrayList<>());
        groups.add(group);
        selectedNameGroupIndex = groups.size() - 1;
        return group;
    }

    private String nameGroupTitle(KillAuraHandler.NameFilterGroup group, int index) {
        String name = group == null || group.name == null ? "" : group.name.trim();
        return name.isEmpty() ? t("gui.modern.killaura.fmt.name_group", String.valueOf(index + 1)) : name;
    }

    /** 分组改动后立刻重算合并名单，草稿期间目标过滤就按新分组生效，和其它字段的即时预览一致。 */
    private void afterNameGroupsChanged() {
        KillAuraHandler.rebuildNameFiltersFromGroups();
        draftDirty = true;
        refreshAfterStateChange();
    }

    private boolean handlePickupClick(int mouseX, int mouseY) {
        if (contains(addRuleBounds, mouseX, mouseY)) {
            HuntPickupRule rule = new HuntPickupRule();
            rule.name = "gui.modern.killaura.u231";
            rule.category = "gui.modern.killaura.u232";
            rule.enabled = true;
            rule.mode = KillAuraHandler.HUNT_PICKUP_RULE_MODE_ALLOW;
            rule.priority = 0;
            rule.maxDistance = 0.0F;
            rule.itemFilterExpressions = new ArrayList<>(Collections.singletonList("false"));
            if (KillAuraHandler.cfg().huntPickupRules == null) {
                KillAuraHandler.cfg().huntPickupRules = new ArrayList<>();
            }
            KillAuraHandler.cfg().huntPickupRules.add(rule);
            selectedRuleIndex = KillAuraHandler.cfg().huntPickupRules.size() - 1;
            selectedRuleExpressionIndex = 0;
            draftDirty = true;
            syncRuleInputs();
            showStatus("gui.modern.killaura.u233");
            return true;
        }
        if (contains(deleteRuleBounds, mouseX, mouseY) && selectedRule() != null) {
            KillAuraHandler.cfg().huntPickupRules.remove(selectedRuleIndex);
            draftDirty = true;
            selectedRuleIndex = Math.min(selectedRuleIndex, KillAuraHandler.cfg().huntPickupRules.size() - 1);
            selectedRuleExpressionIndex = -1;
            syncRuleInputs();
            showStatus("gui.modern.killaura.u234");
            return true;
        }
        for (RuleCardHit hit : ruleCardHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                applyRuleInputs();
                selectedRuleIndex = hit.index;
                selectedRuleExpressionIndex = -1;
                syncRuleInputs();
                return true;
            }
        }
        if (selectedRule() != null) {
            if (contains(ruleToggleBounds, mouseX, mouseY)) {
                applyRuleInputs();
                selectedRule().enabled = !selectedRule().enabled;
                draftDirty = true;
                return true;
            }
            if (contains(ruleModeBounds, mouseX, mouseY)) {
                applyRuleInputs();
                selectedRule().mode = KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(selectedRule().mode)
                        ? KillAuraHandler.HUNT_PICKUP_RULE_MODE_ALLOW : KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK;
                draftDirty = true;
                syncRuleInputs();
                return true;
            }
            for (Map.Entry<String, ModernMainLayout.Rect> entry : rarityBounds.entrySet()) {
                if (entry.getValue().contains(mouseX, mouseY)) {
                    applyRuleInputs();
                    toggleRarity(selectedRule(), entry.getKey());
                    draftDirty = true;
                    syncRuleInputs();
                    return true;
                }
            }
            if (contains(ruleInputBounds.get(RuleInputKey.EXPRESSIONS), mouseX, mouseY)) {
                return handleRuleExpressionClick(mouseX, mouseY);
            }
            for (Map.Entry<RuleInputKey, ModernMainLayout.Rect> entry : ruleInputBounds.entrySet()) {
                if (entry.getKey() == RuleInputKey.EXPRESSIONS) {
                    continue;
                }
                if (entry.getValue().contains(mouseX, mouseY)) {
                    ModernTextField input = ruleInputs.get(entry.getKey());
                    clearInputFocus();
                    if (input != null) {
                        input.setFocused(true);
                        input.mouseClicked(mouseX, mouseY, 0);
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private boolean handleRuleExpressionClick(int mouseX, int mouseY) {
        HuntPickupRule rule = selectedRule();
        if (rule == null) {
            return true;
        }
        if (contains(ruleExpressionAddBounds, mouseX, mouseY)) {
            applyRuleInputs();
            openRuleExpressionEditor(-1);
            return true;
        }
        for (RuleExpressionHit hit : new ArrayList<>(ruleExpressionHits)) {
            if (hit == null || hit.row == null || !hit.row.contains(mouseX, mouseY)) {
                continue;
            }
            if (contains(hit.delete, mouseX, mouseY)) {
                applyRuleInputs();
                if (rule.itemFilterExpressions != null && hit.index >= 0
                        && hit.index < rule.itemFilterExpressions.size()) {
                    rule.itemFilterExpressions.remove(hit.index);
                    selectedRuleExpressionIndex = Math.min(hit.index,
                            rule.itemFilterExpressions.size() - 1);
                    draftDirty = true;
                    showStatus("gui.modern.killaura.u253");
                }
                return true;
            }
            selectedRuleExpressionIndex = hit.index;
            if (contains(hit.edit, mouseX, mouseY) || hit.row.contains(mouseX, mouseY)) {
                applyRuleInputs();
                openRuleExpressionEditor(hit.index);
            }
            return true;
        }
        return true;
    }

    private void openRuleExpressionEditor(final int index) {
        final HuntPickupRule rule = selectedRule();
        if (rule == null) {
            return;
        }
        List<String> expressions = rule.itemFilterExpressions == null
                ? Collections.<String>emptyList() : rule.itemFilterExpressions;
        String initial = index >= 0 && index < expressions.size() ? safe(expressions.get(index)) : "";
        clearInputFocus();
        ruleExpressionEditor.open(fontRenderer, initial,
                index < 0 ? "gui.modern.killaura.u248" : "gui.modern.killaura.u249",
                ExpressionEditorPreview.Mode.ITEM_FILTER, Collections.emptyList(), null, -1, -1,
                new ModernExpressionEditorPanel.CommitListener() {
                    @Override
                    public void accept(String expression) {
                        if (rule.itemFilterExpressions == null) {
                            rule.itemFilterExpressions = new ArrayList<>();
                        }
                        String value = safe(expression).trim();
                        if (index >= 0 && index < rule.itemFilterExpressions.size()) {
                            rule.itemFilterExpressions.set(index, value);
                            selectedRuleExpressionIndex = index;
                            showStatus("gui.modern.killaura.u251");
                        } else {
                            rule.itemFilterExpressions.add(value);
                            selectedRuleExpressionIndex = rule.itemFilterExpressions.size() - 1;
                            showStatus("gui.modern.killaura.u250");
                        }
                        draftDirty = true;
                    }
                }, new ModernExpressionEditorPanel.CancelListener() {
                    @Override
                    public void cancel() {
                        selectedRuleExpressionIndex = index;
                    }
                });
    }

    @Override
    public void save() {
        syncDraftOwner();
        saveDraft();
        rememberDraft();
    }

    private void saveDraft() {
        applyEditableValues();
        KillAuraHandler.INSTANCE.resetRuntimeState();
        storeModeProfiles();
        KillAuraHandler.saveConfig();
        savedState = new State();
        immediatePersistenceOccurred = false;
        draftDirty = false;
        refreshAfterStateChange();
        showStatus("gui.modern.killaura.u235");
    }

    private void applyDefaults() {
        applyEditableValues();
        draftDirty = true;
        boolean enabled = KillAuraHandler.enabled();
        KillAuraHandler.cfg().rotateToTarget = true;
        KillAuraHandler.cfg().smoothRotation = true;
        KillAuraHandler.setSmoothMaxTurnStepSpec(KillAuraHandler.DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC);
        KillAuraHandler.cfg().rotateOnlyOnAttack = true;
        KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget = true;
        KillAuraHandler.cfg().onlyAttackWhenLookingAtTarget = true;
        KillAuraHandler.cfg().requireLineOfSight = false;
        KillAuraHandler.cfg().throughWallAttack = false;
        KillAuraHandler.cfg().targetHostile = true;
        KillAuraHandler.cfg().targetPassive = false;
        KillAuraHandler.cfg().targetPlayers = false;
        KillAuraHandler.cfg().targetNpcs = false;
        KillAuraHandler.cfg().targetEnderCrystal = false;
        KillAuraHandler.cfg().onlyWeapon = false;
        KillAuraHandler.setAimOnlyMode(false);
        KillAuraHandler.cfg().focusSingleTarget = true;
        KillAuraHandler.cfg().blockWhileAttacking = false;
        KillAuraHandler.cfg().ignoreInvisible = true;
        KillAuraHandler.cfg().enableNoCollision = true;
        KillAuraHandler.cfg().enableAntiKnockback = true;
        // 出厂字段就是 AVATAR；恢复默认同样回到 AVATAR，FOLLOW 只留给用户手动切。
        KillAuraHandler.cfg().teleportViewMode = KillAuraHandler.TELEPORT_VIEW_AVATAR;
        KillAuraHandler.cfg().teleportStepDistance = KillAuraHandler.DEFAULT_TELEPORT_STEP_DISTANCE;
        KillAuraHandler.cfg().teleportStationAttackRadius = KillAuraHandler.DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        KillAuraHandler.cfg().teleportActionsPerTick = KillAuraHandler.DEFAULT_TELEPORT_ACTIONS_PER_TICK;
        KillAuraHandler.cfg().teleportStepDistancePerAction = KillAuraHandler.DEFAULT_TELEPORT_STEP_DISTANCE_PER_ACTION;
        KillAuraHandler.cfg().teleportAttackRotationPackets = false;
        KillAuraHandler.setDisableOnDisconnect(true);
        KillAuraHandler.cfg().enableFullBrightVision = false;
        KillAuraHandler.cfg().fullBrightGamma = 1000.0F;
        KillAuraHandler.cfg().attackMode = KillAuraHandler.DEFAULT_ATTACK_MODE;
        KillAuraHandler.cfg().attackSequenceName = "";
        KillAuraHandler.setAttackSequenceDelayTicksSpec(KillAuraHandler.DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC);
        KillAuraHandler.setAimYawOffsetSpec(KillAuraHandler.DEFAULT_AIM_YAW_OFFSET_SPEC);
        KillAuraHandler.setAimPitchOffsetSpec(KillAuraHandler.DEFAULT_AIM_PITCH_OFFSET_SPEC);
        KillAuraHandler.setHuntMode(KillAuraHandler.HUNT_MODE_APPROACH);
        KillAuraHandler.cfg().huntPickupItemsEnabled = false;
        KillAuraHandler.cfg().huntPickupRules = new ArrayList<>();
        KillAuraHandler.setVisualizeHuntRadius(false);
        KillAuraHandler.cfg().huntRadius = 8.0F;
        KillAuraHandler.cfg().huntFixedDistance = 4.2F;
        KillAuraHandler.cfg().huntUpRange = KillAuraHandler.DEFAULT_HUNT_UP_RANGE;
        KillAuraHandler.cfg().huntDownRange = KillAuraHandler.DEFAULT_HUNT_DOWN_RANGE;
        KillAuraHandler.cfg().huntOrbitEnabled = false;
        KillAuraHandler.cfg().huntJumpOrbitEnabled = true;
        KillAuraHandler.cfg().huntOrbitSamplePoints = KillAuraHandler.DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        KillAuraHandler.cfg().nameFilterGroups = new ArrayList<>();
        KillAuraHandler.rebuildNameFiltersFromGroups();
        KillAuraHandler.cfg().nearbyEntityScanRange = 10.0F;
        KillAuraHandler.cfg().attackRange = 4.2F;
        KillAuraHandler.resetHuntScoreWeights();
        KillAuraHandler.cfg().minAttackStrength = 0.92F;
        KillAuraHandler.cfg().minTurnSpeed = 4.0F;
        KillAuraHandler.cfg().maxTurnSpeed = 18.0F;
        KillAuraHandler.cfg().minAttackIntervalTicks = 2;
        KillAuraHandler.cfg().targetsPerAttack = 1;
        KillAuraHandler.cfg().noDamageAttackLimit = KillAuraHandler.DEFAULT_NO_DAMAGE_ATTACK_LIMIT;
        KillAuraHandler.setEnabledSilently(enabled);
        KillAuraHandler.INSTANCE.resetRuntimeState();
        resetModeProfilesToLive();
        applyTeleportProfileDefaults();
        expandActiveModeCard();
        selectedNameGroupIndex = -1;
        selectedRuleIndex = -1;
        selectedRuleExpressionIndex = -1;
        refreshAfterStateChange();
        showStatus("gui.modern.killaura.u236");
    }

    private void revertDraft() {
        if (savedState == null) {
            return;
        }
        savedState.apply();
        if (immediatePersistenceOccurred) {
            KillAuraHandler.saveConfig();
        }
        loadModeProfiles();
        expandActiveModeCard();
        immediatePersistenceOccurred = false;
        draftDirty = false;
        refreshAfterStateChange();
        showStatus("gui.modern.killaura.u237");
    }

    private void applyEditableValues() {
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field.usesInput() && field.input != null) {
                        State.setText(field.key, field.input.getText());
                    }
                }
            }
        }
        applyRuleInputs();
        enforceTargetSelection();
    }

    private void syncInputFields() {
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field.usesInput() && field.input != null) {
                        field.input.setText(State.getText(field.key));
                    }
                }
            }
        }
        syncRuleInputs();
    }

    private void syncRuleInputs() {
        HuntPickupRule rule = selectedRule();
        if (rule == null || rule.itemFilterExpressions == null || rule.itemFilterExpressions.isEmpty()) {
            selectedRuleExpressionIndex = -1;
        } else {
            selectedRuleExpressionIndex = clamp(selectedRuleExpressionIndex, 0,
                    rule.itemFilterExpressions.size() - 1);
        }
        localizedRuleInputSources.clear();
        for (RuleInputKey key : RuleInputKey.values()) {
            ModernTextField input = ruleInputs.get(key);
            if (input == null) {
                continue;
            }
            if (rule == null) {
                input.setText("");
                continue;
            }
            String modelValue = ruleInputValue(rule, key);
            String displayValue = ruleInputDisplayValue(rule, key);
            input.setText(displayValue);
            if ((key == RuleInputKey.NAME || key == RuleInputKey.CATEGORY)
                    && !modelValue.equals(displayValue)) {
                localizedRuleInputSources.put(key, modelValue);
            }
        }
    }

    private void applyRuleInputs() {
        HuntPickupRule rule = selectedRule();
        if (rule == null) {
            return;
        }
        rule.name = ruleInputModelValue(RuleInputKey.NAME);
        rule.category = ruleInputModelValue(RuleInputKey.CATEGORY);
        rule.nameKeyword = valueOf(RuleInputKey.NAME_KEYWORD).trim();
        rule.itemIdKeyword = valueOf(RuleInputKey.ITEM_ID_KEYWORD).trim();
        rule.requiredNbtTags = parseCommaList(valueOf(RuleInputKey.NBT_TAGS));
        ModernTextField expressionInput = ruleInputs.get(RuleInputKey.EXPRESSIONS);
        if (expressionInput != null) {
            rule.itemFilterExpressions = parseLiteralLines(expressionInput.getText());
        }
        rule.maxDistance = parseFloat(valueOf(RuleInputKey.MAX_DISTANCE), rule.maxDistance, 0.0F, 100.0F);
        rule.priority = parseInt(valueOf(RuleInputKey.PRIORITY), rule.priority, -999, 999);
    }

    private String valueOf(RuleInputKey key) {
        ModernTextField input = ruleInputs.get(key);
        return input == null ? "" : safe(input.getText());
    }

    private String ruleInputModelValue(RuleInputKey key) {
        String visible = valueOf(key).trim();
        String source = localizedRuleInputSources.get(key);
        return source != null && t(source).equals(visible) ? source : visible;
    }

    private String ruleInputValue(HuntPickupRule rule, RuleInputKey key) {
        if (rule == null) {
            return "";
        }
        switch (key) {
        case NAME:
            return safe(rule.name);
        case CATEGORY:
            return safe(rule.category);
        case NAME_KEYWORD:
            return safe(rule.nameKeyword);
        case ITEM_ID_KEYWORD:
            return safe(rule.itemIdKeyword);
        case NBT_TAGS:
            return joinComma(rule.requiredNbtTags);
        case EXPRESSIONS:
            return joinLiteralLines(rule.itemFilterExpressions);
        case MAX_DISTANCE:
            return formatFloat(rule.maxDistance);
        case PRIORITY:
            return String.valueOf(rule.priority);
        default:
            return "";
        }
    }

    private String ruleInputDisplayValue(HuntPickupRule rule, RuleInputKey key) {
        String value = ruleInputValue(rule, key);
        return key == RuleInputKey.NAME || key == RuleInputKey.CATEGORY ? t(value) : value;
    }

    private void refreshAfterStateChange() {
        syncInputFields();
        refreshPresetCards(selectedPreset() == null ? "" : selectedPreset().name);
        refreshNearbyNames();
        if (activeGroup == Group.TARGET) {
            refreshHuntScoreEntries();
        }
    }

    private void refreshPresetCards(String preferredName) {
        String requested = safe(preferredName).trim();
        String currentlySelected = selectedPreset() == null ? "" : safe(selectedPreset().name).trim();
        presetCards.clear();
        presetCards.addAll(KillAuraHandler.getPresetSnapshots());
        selectedPresetIndex = -1;
        String target = !requested.isEmpty() ? requested : currentlySelected;
        for (int index = 0; index < presetCards.size(); index++) {
            if (target.equalsIgnoreCase(safe(presetCards.get(index).name).trim())) {
                selectedPresetIndex = index;
                break;
            }
        }
        if (selectedPresetIndex < 0 && !presetCards.isEmpty() && !target.isEmpty()) {
            selectedPresetIndex = 0;
        }
    }

    private void refreshNearbyNames() {
        nearbyNames.clear();
        nearbyNameTypes.clear();
        java.util.LinkedHashMap<String, String> map = KillAuraHandler
                .getNearbyEntityNameTypeMap(KillAuraHandler.cfg().nearbyEntityScanRange);
        nearbyNames.addAll(map.keySet());
        nearbyNameTypes.addAll(map.values());
    }

    private void refreshHuntScoreEntries() {
        huntScoreEntries.clear();
        huntScoreEntries.addAll(KillAuraHandler.INSTANCE.getHuntScoreDebugEntries());
    }

    private void addName(List<String> values, String rawValue) {
        if (values == null) {
            return;
        }
        String normalized = KillAuraHandler.normalizeFilterName(rawValue).toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        for (String entry : values) {
            if (normalized.equalsIgnoreCase(KillAuraHandler.normalizeFilterName(entry))) {
                return;
            }
        }
        values.add(normalized);
    }

    private boolean hasNameEntry() {
        return nameEntryField != null && !KillAuraHandler.normalizeFilterName(nameEntryField.getText()).isEmpty();
    }

    private void toggleRarity(HuntPickupRule rule, String rarity) {
        if (rule == null || rarity == null) {
            return;
        }
        if (rule.rarityFilters == null) {
            rule.rarityFilters = new ArrayList<>();
        }
        if (rule.rarityFilters.contains(rarity)) {
            rule.rarityFilters.remove(rarity);
        } else {
            rule.rarityFilters.add(rarity);
        }
    }

    private int ruleCount() {
        return KillAuraHandler.cfg().huntPickupRules == null ? 0 : KillAuraHandler.cfg().huntPickupRules.size();
    }

    private HuntPickupRule selectedRule() {
        if (KillAuraHandler.cfg().huntPickupRules == null || selectedRuleIndex < 0
                || selectedRuleIndex >= KillAuraHandler.cfg().huntPickupRules.size()) {
            return null;
        }
        return KillAuraHandler.cfg().huntPickupRules.get(selectedRuleIndex);
    }

    private KillAuraPreset selectedPreset() {
        if (selectedPresetIndex < 0 || selectedPresetIndex >= presetCards.size()) {
            return null;
        }
        return presetCards.get(selectedPresetIndex);
    }

    private interface InputVisitor {
        void visit(ModernTextField input);
    }

    private void forEachInput(InputVisitor visitor) {
        if (visitor == null) {
            return;
        }
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    if (field.input != null) {
                        visitor.visit(field.input);
                    }
                }
            }
        }
        if (presetNameField != null) {
            visitor.visit(presetNameField);
        }
        if (nameEntryField != null) {
            visitor.visit(nameEntryField);
        }
        for (ModernTextField input : ruleInputs.values()) {
            if (input != null) {
                visitor.visit(input);
            }
        }
    }

    private ModernTextField findFocusedInput() {
        final ModernTextField[] focused = new ModernTextField[] { null };
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                if (focused[0] == null && input.isVisible() && input.isFocused()) {
                    focused[0] = input;
                }
            }
        });
        return focused[0];
    }

    private boolean keyTypedInInputs(char typedChar, int keyCode) {
        final boolean[] handled = new boolean[] { false };
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                if (!handled[0] && input.getVisible() && input.isFocused()
                        && input.textboxKeyTyped(typedChar, keyCode)) {
                    handled[0] = true;
                }
            }
        });
        if (handled[0]) {
            draftDirty = true;
        }
        return handled[0];
    }

    private boolean hasFocusedInput() {
        final boolean[] focused = new boolean[] { false };
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                if (input.getVisible() && input.isFocused()) {
                    focused[0] = true;
                }
            }
        });
        return focused[0];
    }

    private void clearInputFocus() {
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                input.setFocused(false);
            }
        });
    }

    private Condition condition(final String name) {
        return new Condition() {
            @Override
            public boolean matches() {
                if ("notAimOnly".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode();
                }
                if ("sequenceMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                if ("delayMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)
                            || KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                if ("teleportMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_TELEPORT.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                if ("weaponApplicable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode()
                            && !KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                if ("rotationAvailable".equals(name)) {
                    return !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)
                            || KillAuraHandler.aimOnlyMode();
                }
                if ("smoothRotation".equals(name)) {
                    return KillAuraHandler.cfg().smoothRotation && (condition("rotationAvailable").matches());
                }
                if ("lookAttackAvailable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode()
                            && !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)
                            && KillAuraHandler.cfg().rotateToTarget
                            && !KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                if ("relockAvailable".equals(name)) {
                    return KillAuraHandler.aimOnlyMode() || KillAuraHandler.cfg().rotateToTarget;
                }
                if ("rotateOnlyAvailable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode()
                            && !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)
                            && KillAuraHandler.cfg().rotateToTarget;
                }
                if ("huntEnabled".equals(name)) {
                    return KillAuraHandler.isHuntEnabled();
                }
                if ("fixedHunt".equals(name)) {
                    return KillAuraHandler.isHuntFixedDistanceMode();
                }
                if ("orbitEnabled".equals(name)) {
                    return KillAuraHandler.isHuntFixedDistanceMode() && KillAuraHandler.cfg().huntOrbitEnabled;
                }
                if ("fullBright".equals(name)) {
                    return KillAuraHandler.cfg().enableFullBrightVision;
                }
                if ("normalAttackStats".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode()
                            && !KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
                }
                return true;
            }
        };
    }

    private String modeLabel(String mode) {
        if (KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(mode)) {
            return t("gui.modern.killaura.u238");
        }
        if (KillAuraHandler.ATTACK_MODE_TELEPORT.equalsIgnoreCase(mode)) {
            return "TP";
        }
        if (KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(mode)) {
            return t("gui.modern.killaura.u239");
        }
        if (KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(mode)) {
            return t("gui.modern.killaura.u240");
        }
        return t("gui.modern.killaura.u241");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static String t(String key) {
        return ModernFormI18n.tr(key);
    }

    private static String t(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

    private String formatFloat(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatDouble(double value) {
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            return "--";
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private int parseInt(String text, int fallback, int minimum, int maximum) {
        int value = fallback;
        try {
            value = Integer.parseInt(safe(text).trim());
        } catch (Exception ignored) {
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private float parseFloat(String text, float fallback, float minimum, float maximum) {
        float value = fallback;
        try {
            value = Float.parseFloat(safe(text).trim());
        } catch (Exception ignored) {
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            value = fallback;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }

    private List<String> parseCommaList(String text) {
        List<String> values = new ArrayList<>();
        String[] parts = safe(text).replace('，', ',').split(",");
        for (String part : parts) {
            String normalized = safe(part).trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private List<String> parseLiteralLines(String text) {
        List<String> values = new ArrayList<>();
        String[] parts = safe(text).split("\\\\n", -1);
        for (String part : parts) {
            String normalized = safe(part).trim();
            if (!normalized.isEmpty()) {
                values.add(normalized);
            }
        }
        return values;
    }

    private String joinComma(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            if (!safe(value).trim().isEmpty()) {
                cleaned.add(safe(value).trim());
            }
        }
        return String.join(", ", cleaned);
    }

    private String joinLiteralLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> cleaned = new ArrayList<>();
        for (String value : values) {
            String normalized = safe(value).trim();
            if (!normalized.isEmpty()) {
                cleaned.add(normalized.replace("\\n", " "));
            }
        }
        return String.join("\\n", cleaned);
    }

    private boolean contains(ModernMainLayout.Rect bounds, int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private boolean statusVisible() {
        return !statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageUntil;
    }

    private void switchAttackMode(String requested) {
        String mode = canonicalMode(requested);
        applyEditableValues();
        String current = canonicalMode(KillAuraHandler.cfg().attackMode);
        if (!mode.equals(current)) {
            captureModeProfile(current);
            KillAuraHandler.cfg().attackMode = mode;
            applyModeProfile(mode);
            if (KillAuraHandler.ATTACK_MODE_PACKET.equals(mode)) {
                KillAuraHandler.cfg().rotateToTarget = false;
                KillAuraHandler.cfg().smoothRotation = false;
                KillAuraHandler.cfg().rotateOnlyOnAttack = false;
                KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget = false;
            }
        }
        Section card = modeCards.get(mode);
        if (card != null) {
            card.expanded = true;
        }
        expandedForMode = mode;
        draftDirty = true;
        refreshAfterStateChange();
        showStatus(t("gui.modern.killaura.u276", t(card == null ? mode : card.title)));
    }

    private void syncExpandedModeCard() {
        if (!canonicalMode(KillAuraHandler.cfg().attackMode).equals(expandedForMode)) {
            expandActiveModeCard();
        }
    }

    private void expandActiveModeCard() {
        String current = canonicalMode(KillAuraHandler.cfg().attackMode);
        expandedForMode = current;
        for (Map.Entry<String, Section> entry : modeCards.entrySet()) {
            entry.getValue().expanded = entry.getKey().equals(current);
        }
    }

    private static String canonicalMode(String mode) {
        for (String candidate : ATTACK_MODES) {
            if (candidate.equalsIgnoreCase(mode == null ? "" : mode.trim())) {
                return candidate;
            }
        }
        return KillAuraHandler.ATTACK_MODE_NORMAL;
    }

    private static String liveProfileValue(String key) {
        return KEY_TP_ROTATION.equals(key) ? String.valueOf(State.getBoolean(key)) : State.getText(key);
    }

    private static void captureModeProfile(String mode) {
        Map<String, String> profile = modeProfile(mode);
        for (String key : MODE_PROFILE_KEYS.get(mode)) {
            profile.put(key, liveProfileValue(key));
        }
    }

    private static void applyModeProfile(String mode) {
        Map<String, String> profile = modeProfile(mode);
        for (String key : MODE_PROFILE_KEYS.get(mode)) {
            String value = profile.get(key);
            if (value == null) {
                continue;
            }
            if (KEY_TP_ROTATION.equals(key)) {
                State.setBoolean(key, Boolean.parseBoolean(value));
            } else {
                State.setText(key, value);
            }
        }
    }

    private static Map<String, String> modeProfile(String mode) {
        Map<String, String> profile = MODE_PROFILES.get(mode);
        if (profile == null) {
            profile = new java.util.LinkedHashMap<>();
            MODE_PROFILES.put(mode, profile);
        }
        return profile;
    }

    /**
     * 旧配置没有档案块，或只缺某几项时，用当前生效值补齐；当前模式最后再用实时值覆盖，
     * 因为预设、MCP 和其它保存入口只会改实时字段，不会同步档案。
     */
    private static void loadModeProfiles() {
        MODE_PROFILES.clear();
        com.google.gson.JsonObject stored = KillAuraHandler.cfg().attackModeProfiles;
        for (String mode : ATTACK_MODES) {
            captureModeProfile(mode);
            if (stored == null || !stored.has(mode) || !stored.get(mode).isJsonObject()) {
                continue;
            }
            com.google.gson.JsonObject block = stored.getAsJsonObject(mode);
            Map<String, String> profile = modeProfile(mode);
            for (String key : MODE_PROFILE_KEYS.get(mode)) {
                if (block.has(key) && block.get(key).isJsonPrimitive()) {
                    profile.put(key, block.get(key).getAsString());
                }
            }
        }
        captureModeProfile(canonicalMode(KillAuraHandler.cfg().attackMode));
    }

    private static void storeModeProfiles() {
        captureModeProfile(canonicalMode(KillAuraHandler.cfg().attackMode));
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        for (String mode : ATTACK_MODES) {
            com.google.gson.JsonObject block = new com.google.gson.JsonObject();
            for (Map.Entry<String, String> entry : modeProfile(mode).entrySet()) {
                block.addProperty(entry.getKey(), entry.getValue());
            }
            root.add(mode, block);
        }
        KillAuraHandler.cfg().attackModeProfiles = root;
    }

    /** TP 攻击会先位移到目标身边，沿用近战的 4.2 格单目标默认值就浪费了它的作用距离，所以单独给一套默认值。 */
    private static void applyTeleportProfileDefaults() {
        String tp = KillAuraHandler.ATTACK_MODE_TELEPORT;
        Map<String, String> profile = modeProfile(tp);
        profile.put(KEY_RANGE, "40");
        profile.put(KEY_INTERVAL, "0");
        profile.put(KEY_STRENGTH, "0");
        profile.put(KEY_TARGETS, "5");
        if (tp.equals(canonicalMode(KillAuraHandler.cfg().attackMode))) {
            applyModeProfile(tp);
        }
    }

    private static void resetModeProfilesToLive() {
        MODE_PROFILES.clear();
        for (String mode : ATTACK_MODES) {
            captureModeProfile(mode);
        }
    }

    private void showStatus(String message) {
        statusMessage = safe(message);
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private void enforceTargetSelection() {
        if (!KillAuraHandler.cfg().targetHostile && !KillAuraHandler.cfg().targetPassive && !KillAuraHandler.cfg().targetPlayers
                && !KillAuraHandler.cfg().targetNpcs && !KillAuraHandler.cfg().targetEnderCrystal) {
            KillAuraHandler.cfg().targetHostile = true;
        }
    }

    /** Defensive snapshot of the full persisted Kill Aura state. */
    private static final class State {
        private final boolean enabled;
        private final KillAuraPreset values;
        private final List<KillAuraHandler.NameFilterGroup> nameGroups;
        private final List<HuntPickupRule> pickupRules;
        private final List<KillAuraPreset> presets;

        private State() {
            this.enabled = KillAuraHandler.enabled();
            this.values = captureValues();
            this.nameGroups = KillAuraHandler.copyNameFilterGroups(KillAuraHandler.cfg().nameFilterGroups);
            this.pickupRules = copyRules(KillAuraHandler.cfg().huntPickupRules);
            this.presets = copyPresets(KillAuraHandler.getPresetSnapshots());
        }

        private State(State source) {
            this.enabled = source.enabled;
            this.values = new KillAuraPreset(source.values);
            this.nameGroups = KillAuraHandler.copyNameFilterGroups(source.nameGroups);
            this.pickupRules = copyRules(source.pickupRules);
            this.presets = copyPresets(source.presets);
        }

        private void apply() {
            applyValues(values);
            KillAuraHandler.setEnabledSilently(enabled);
            KillAuraHandler.cfg().nameFilterGroups = KillAuraHandler.copyNameFilterGroups(nameGroups);
            KillAuraHandler.rebuildNameFiltersFromGroups();
            KillAuraHandler.cfg().huntPickupRules = copyRules(pickupRules);
            KillAuraHandler.cfg().presets.clear();
            KillAuraHandler.cfg().presets.addAll(copyPresets(presets));
            KillAuraHandler.INSTANCE.resetRuntimeState();
        }

        private static KillAuraPreset captureValues() {
            KillAuraPreset value = new KillAuraPreset();
            value.rotateToTarget = KillAuraHandler.cfg().rotateToTarget;
            value.smoothRotation = KillAuraHandler.cfg().smoothRotation;
            value.smoothMaxTurnStep = KillAuraHandler.cfg().smoothMaxTurnStep;
            value.smoothMaxTurnStepSpec = KillAuraHandler.getSmoothMaxTurnStepSpec();
            value.rotateOnlyOnAttack = KillAuraHandler.cfg().rotateOnlyOnAttack;
            value.relockOnlyWhenNoCrosshairTarget = KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget;
            value.onlyAttackWhenLookingAtTarget = KillAuraHandler.cfg().onlyAttackWhenLookingAtTarget;
            value.requireLineOfSight = KillAuraHandler.cfg().requireLineOfSight;
            value.throughWallAttack = KillAuraHandler.cfg().throughWallAttack;
            value.targetHostile = KillAuraHandler.cfg().targetHostile;
            value.targetPassive = KillAuraHandler.cfg().targetPassive;
            value.targetPlayers = KillAuraHandler.cfg().targetPlayers;
            value.targetNpcs = KillAuraHandler.cfg().targetNpcs;
            value.targetEnderCrystal = KillAuraHandler.cfg().targetEnderCrystal;
            value.onlyWeapon = KillAuraHandler.cfg().onlyWeapon;
            value.aimOnlyMode = KillAuraHandler.aimOnlyMode();
            value.focusSingleTarget = KillAuraHandler.cfg().focusSingleTarget;
            value.blockWhileAttacking = KillAuraHandler.cfg().blockWhileAttacking;
            value.ignoreInvisible = KillAuraHandler.cfg().ignoreInvisible;
            value.enableNoCollision = KillAuraHandler.cfg().enableNoCollision;
            value.enableAntiKnockback = KillAuraHandler.cfg().enableAntiKnockback;
            value.teleportStepDistance = KillAuraHandler.cfg().teleportStepDistance;
            value.teleportStationAttackRadius = KillAuraHandler.cfg().teleportStationAttackRadius;
            value.teleportActionsPerTick = KillAuraHandler.cfg().teleportActionsPerTick;
            value.teleportStepDistancePerAction = KillAuraHandler.cfg().teleportStepDistancePerAction;
            value.teleportAttackRotationPackets = KillAuraHandler.cfg().teleportAttackRotationPackets;
            value.disableOnDisconnect = KillAuraHandler.disableOnDisconnect();
            value.enableFullBrightVision = KillAuraHandler.cfg().enableFullBrightVision;
            value.fullBrightGamma = KillAuraHandler.cfg().fullBrightGamma;
            value.attackMode = KillAuraHandler.cfg().attackMode;
            value.attackSequenceName = KillAuraHandler.cfg().attackSequenceName;
            value.attackSequenceDelayTicks = KillAuraHandler.cfg().attackSequenceDelayTicks;
            value.attackSequenceDelayTicksSpec = KillAuraHandler.getAttackSequenceDelayTicksSpec();
            value.aimYawOffset = KillAuraHandler.cfg().aimYawOffset;
            value.aimYawOffsetSpec = KillAuraHandler.getAimYawOffsetSpec();
            value.aimPitchOffset = KillAuraHandler.cfg().aimPitchOffset;
            value.aimPitchOffsetSpec = KillAuraHandler.getAimPitchOffsetSpec();
            value.huntMode = KillAuraHandler.cfg().huntMode;
            value.huntPickupItemsEnabled = KillAuraHandler.cfg().huntPickupItemsEnabled;
            value.huntPickupRules = copyRules(KillAuraHandler.cfg().huntPickupRules);
            value.visualizeHuntRadius = KillAuraHandler.visualizeHuntRadius();
            value.huntRadius = KillAuraHandler.cfg().huntRadius;
            value.huntFixedDistance = KillAuraHandler.cfg().huntFixedDistance;
            value.huntUpRange = KillAuraHandler.cfg().huntUpRange;
            value.huntDownRange = KillAuraHandler.cfg().huntDownRange;
            value.huntOrbitEnabled = KillAuraHandler.cfg().huntOrbitEnabled;
            value.huntJumpOrbitEnabled = KillAuraHandler.cfg().huntJumpOrbitEnabled;
            value.huntOrbitSamplePoints = KillAuraHandler.cfg().huntOrbitSamplePoints;
            value.enableNameWhitelist = KillAuraHandler.cfg().enableNameWhitelist;
            value.enableNameBlacklist = KillAuraHandler.cfg().enableNameBlacklist;
            value.nameWhitelist = copyStrings(KillAuraHandler.cfg().nameWhitelist);
            value.nameBlacklist = copyStrings(KillAuraHandler.cfg().nameBlacklist);
            value.nearbyEntityScanRange = KillAuraHandler.cfg().nearbyEntityScanRange;
            value.attackRange = KillAuraHandler.cfg().attackRange;
            value.huntScoreRadiusWeight = KillAuraHandler.cfg().huntScoreRadiusWeight;
            value.huntScorePlayerDistanceWeight = KillAuraHandler.cfg().huntScorePlayerDistanceWeight;
            value.huntScorePlayerPlaneWeight = KillAuraHandler.cfg().huntScorePlayerPlaneWeight;
            value.huntScoreTargetHeightWeight = KillAuraHandler.cfg().huntScoreTargetHeightWeight;
            value.huntScoreAttackRangeWeight = KillAuraHandler.cfg().huntScoreAttackRangeWeight;
            value.huntScoreVisibilityWeight = KillAuraHandler.cfg().huntScoreVisibilityWeight;
            value.huntScoreOpennessWeight = KillAuraHandler.cfg().huntScoreOpennessWeight;
            value.minAttackStrength = KillAuraHandler.cfg().minAttackStrength;
            value.minTurnSpeed = KillAuraHandler.cfg().minTurnSpeed;
            value.maxTurnSpeed = KillAuraHandler.cfg().maxTurnSpeed;
            value.minAttackIntervalTicks = KillAuraHandler.cfg().minAttackIntervalTicks;
            value.targetsPerAttack = KillAuraHandler.cfg().targetsPerAttack;
            value.noDamageAttackLimit = KillAuraHandler.cfg().noDamageAttackLimit;
            return value;
        }

        private static void applyValues(KillAuraPreset value) {
            if (value == null) {
                return;
            }
            KillAuraHandler.cfg().rotateToTarget = value.rotateToTarget;
            KillAuraHandler.cfg().smoothRotation = value.smoothRotation;
            KillAuraHandler.setSmoothMaxTurnStepSpec(value.smoothMaxTurnStepSpec);
            KillAuraHandler.cfg().rotateOnlyOnAttack = value.rotateOnlyOnAttack;
            KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget = value.relockOnlyWhenNoCrosshairTarget;
            KillAuraHandler.cfg().onlyAttackWhenLookingAtTarget = value.onlyAttackWhenLookingAtTarget;
            KillAuraHandler.cfg().requireLineOfSight = value.requireLineOfSight;
            KillAuraHandler.cfg().throughWallAttack = value.throughWallAttack;
            KillAuraHandler.cfg().targetHostile = value.targetHostile;
            KillAuraHandler.cfg().targetPassive = value.targetPassive;
            KillAuraHandler.cfg().targetPlayers = value.targetPlayers;
            KillAuraHandler.cfg().targetNpcs = value.targetNpcs;
            KillAuraHandler.cfg().targetEnderCrystal = value.targetEnderCrystal;
            KillAuraHandler.cfg().onlyWeapon = value.onlyWeapon;
            KillAuraHandler.setAimOnlyMode(value.aimOnlyMode);
            KillAuraHandler.cfg().focusSingleTarget = value.focusSingleTarget;
            KillAuraHandler.cfg().blockWhileAttacking = value.blockWhileAttacking;
            KillAuraHandler.cfg().ignoreInvisible = value.ignoreInvisible;
            KillAuraHandler.cfg().enableNoCollision = value.enableNoCollision;
            KillAuraHandler.cfg().enableAntiKnockback = value.enableAntiKnockback;
            KillAuraHandler.cfg().teleportStepDistance = value.teleportStepDistance;
            KillAuraHandler.cfg().teleportStationAttackRadius = value.teleportStationAttackRadius;
            KillAuraHandler.cfg().teleportActionsPerTick = value.teleportActionsPerTick;
            KillAuraHandler.cfg().teleportStepDistancePerAction = value.teleportStepDistancePerAction;
            KillAuraHandler.cfg().teleportAttackRotationPackets = value.teleportAttackRotationPackets;
            KillAuraHandler.setDisableOnDisconnect(value.disableOnDisconnect);
            KillAuraHandler.cfg().enableFullBrightVision = value.enableFullBrightVision;
            KillAuraHandler.cfg().fullBrightGamma = value.fullBrightGamma;
            KillAuraHandler.cfg().attackMode = value.attackMode;
            KillAuraHandler.cfg().attackSequenceName = value.attackSequenceName;
            KillAuraHandler.setAttackSequenceDelayTicksSpec(value.attackSequenceDelayTicksSpec);
            KillAuraHandler.setAimYawOffsetSpec(value.aimYawOffsetSpec);
            KillAuraHandler.setAimPitchOffsetSpec(value.aimPitchOffsetSpec);
            KillAuraHandler.setHuntMode(value.huntMode);
            KillAuraHandler.cfg().huntPickupItemsEnabled = value.huntPickupItemsEnabled;
            KillAuraHandler.cfg().huntPickupRules = copyRules(value.huntPickupRules);
            KillAuraHandler.setVisualizeHuntRadius(value.visualizeHuntRadius);
            KillAuraHandler.cfg().huntRadius = value.huntRadius;
            KillAuraHandler.cfg().huntFixedDistance = value.huntFixedDistance;
            KillAuraHandler.cfg().huntUpRange = value.huntUpRange;
            KillAuraHandler.cfg().huntDownRange = value.huntDownRange;
            KillAuraHandler.cfg().huntOrbitEnabled = value.huntOrbitEnabled;
            KillAuraHandler.cfg().huntJumpOrbitEnabled = value.huntJumpOrbitEnabled;
            KillAuraHandler.cfg().huntOrbitSamplePoints = value.huntOrbitSamplePoints;
            KillAuraHandler.cfg().enableNameWhitelist = value.enableNameWhitelist;
            KillAuraHandler.cfg().enableNameBlacklist = value.enableNameBlacklist;
            KillAuraHandler.cfg().nearbyEntityScanRange = value.nearbyEntityScanRange;
            KillAuraHandler.cfg().attackRange = value.attackRange;
            KillAuraHandler.cfg().huntScoreRadiusWeight = value.huntScoreRadiusWeight;
            KillAuraHandler.cfg().huntScorePlayerDistanceWeight = value.huntScorePlayerDistanceWeight;
            KillAuraHandler.cfg().huntScorePlayerPlaneWeight = value.huntScorePlayerPlaneWeight;
            KillAuraHandler.cfg().huntScoreTargetHeightWeight = value.huntScoreTargetHeightWeight;
            KillAuraHandler.cfg().huntScoreAttackRangeWeight = value.huntScoreAttackRangeWeight;
            KillAuraHandler.cfg().huntScoreVisibilityWeight = value.huntScoreVisibilityWeight;
            KillAuraHandler.cfg().huntScoreOpennessWeight = value.huntScoreOpennessWeight;
            KillAuraHandler.cfg().minAttackStrength = value.minAttackStrength;
            KillAuraHandler.cfg().minTurnSpeed = value.minTurnSpeed;
            KillAuraHandler.cfg().maxTurnSpeed = value.maxTurnSpeed;
            KillAuraHandler.cfg().minAttackIntervalTicks = value.minAttackIntervalTicks;
            KillAuraHandler.cfg().targetsPerAttack = value.targetsPerAttack;
            KillAuraHandler.cfg().noDamageAttackLimit = value.noDamageAttackLimit;
        }

        /** 返回带模式前缀的档案键对应的模式；当前模式的档案键直接读写实时字段。 */
        private static String profileMode(String key) {
            int split = key == null ? -1 : key.indexOf(':');
            return split > 0 ? key.substring(0, split) : null;
        }

        private static boolean getBoolean(String key) {
            String mode = profileMode(key);
            if (mode != null) {
                String base = key.substring(mode.length() + 1);
                if (mode.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)) return getBoolean(base);
                return Boolean.parseBoolean(modeProfile(mode).get(base));
            }
            if ("enabled".equals(key)) return KillAuraHandler.enabled();
            if ("rotateToTarget".equals(key)) return KillAuraHandler.cfg().rotateToTarget;
            if ("smoothRotation".equals(key)) return KillAuraHandler.cfg().smoothRotation;
            if ("rotateOnlyOnAttack".equals(key)) return KillAuraHandler.cfg().rotateOnlyOnAttack;
            if ("relockOnlyWhenNoCrosshairTarget".equals(key)) return KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget;
            if ("onlyAttackWhenLookingAtTarget".equals(key)) return KillAuraHandler.cfg().onlyAttackWhenLookingAtTarget;
            if ("requireLineOfSight".equals(key)) return KillAuraHandler.cfg().requireLineOfSight;
            if ("throughWallAttack".equals(key)) return KillAuraHandler.cfg().throughWallAttack;
            if ("targetHostile".equals(key)) return KillAuraHandler.cfg().targetHostile;
            if ("targetPassive".equals(key)) return KillAuraHandler.cfg().targetPassive;
            if ("targetPlayers".equals(key)) return KillAuraHandler.cfg().targetPlayers;
            if ("targetNpcs".equals(key)) return KillAuraHandler.cfg().targetNpcs;
            if ("targetEnderCrystal".equals(key)) return KillAuraHandler.cfg().targetEnderCrystal;
            if ("onlyWeapon".equals(key)) return KillAuraHandler.cfg().onlyWeapon;
            if ("aimOnlyMode".equals(key)) return KillAuraHandler.aimOnlyMode();
            if ("focusSingleTarget".equals(key)) return KillAuraHandler.cfg().focusSingleTarget;
            if ("blockWhileAttacking".equals(key)) return KillAuraHandler.cfg().blockWhileAttacking;
            if ("ignoreInvisible".equals(key)) return KillAuraHandler.cfg().ignoreInvisible;
            if ("enableNoCollision".equals(key)) return KillAuraHandler.cfg().enableNoCollision;
            if ("enableAntiKnockback".equals(key)) return KillAuraHandler.cfg().enableAntiKnockback;
            if ("disableOnDisconnect".equals(key)) return KillAuraHandler.disableOnDisconnect();
            if ("enableFullBrightVision".equals(key)) return KillAuraHandler.cfg().enableFullBrightVision;
            if ("huntPickupItemsEnabled".equals(key)) return KillAuraHandler.cfg().huntPickupItemsEnabled;
            if ("visualizeHuntRadius".equals(key)) return KillAuraHandler.visualizeHuntRadius();
            if ("huntOrbitEnabled".equals(key)) return KillAuraHandler.cfg().huntOrbitEnabled;
            if ("huntJumpOrbitEnabled".equals(key)) return KillAuraHandler.cfg().huntJumpOrbitEnabled;
            if ("enableNameWhitelist".equals(key)) return KillAuraHandler.cfg().enableNameWhitelist;
            if ("enableNameBlacklist".equals(key)) return KillAuraHandler.cfg().enableNameBlacklist;
            if ("teleportAttackRotationPackets".equals(key)) return KillAuraHandler.cfg().teleportAttackRotationPackets;
            return false;
        }

        private static void setBoolean(String key, boolean value) {
            String mode = profileMode(key);
            if (mode != null) {
                String base = key.substring(mode.length() + 1);
                if (mode.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)) setBoolean(base, value);
                else modeProfile(mode).put(base, String.valueOf(value));
                return;
            }
            if ("enabled".equals(key)) {
                KillAuraHandler.setEnabledSilently(value);
            } else if ("rotateToTarget".equals(key)) {
                KillAuraHandler.cfg().rotateToTarget = value;
                if (!value) {
                    KillAuraHandler.cfg().rotateOnlyOnAttack = false;
                    KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget = false;
                }
            } else if ("smoothRotation".equals(key)) KillAuraHandler.cfg().smoothRotation = value;
            else if ("rotateOnlyOnAttack".equals(key)) KillAuraHandler.cfg().rotateOnlyOnAttack = value;
            else if ("relockOnlyWhenNoCrosshairTarget".equals(key)) KillAuraHandler.cfg().relockOnlyWhenNoCrosshairTarget = value;
            else if ("onlyAttackWhenLookingAtTarget".equals(key)) KillAuraHandler.cfg().onlyAttackWhenLookingAtTarget = value;
            else if ("requireLineOfSight".equals(key)) KillAuraHandler.cfg().requireLineOfSight = value;
            else if ("throughWallAttack".equals(key)) KillAuraHandler.cfg().throughWallAttack = value;
            else if ("targetHostile".equals(key)) KillAuraHandler.cfg().targetHostile = value;
            else if ("targetPassive".equals(key)) KillAuraHandler.cfg().targetPassive = value;
            else if ("targetPlayers".equals(key)) KillAuraHandler.cfg().targetPlayers = value;
            else if ("targetNpcs".equals(key)) KillAuraHandler.cfg().targetNpcs = value;
            else if ("targetEnderCrystal".equals(key)) KillAuraHandler.cfg().targetEnderCrystal = value;
            else if ("onlyWeapon".equals(key)) KillAuraHandler.cfg().onlyWeapon = value;
            else if ("aimOnlyMode".equals(key)) {
                KillAuraHandler.setAimOnlyMode(value);
                if (value) {
                    KillAuraHandler.cfg().attackMode = KillAuraHandler.ATTACK_MODE_SEQUENCE;
                    KillAuraHandler.cfg().rotateOnlyOnAttack = false;
                }
            } else if ("focusSingleTarget".equals(key)) KillAuraHandler.cfg().focusSingleTarget = value;
            else if ("blockWhileAttacking".equals(key)) {
                KillAuraHandler.cfg().blockWhileAttacking = value;
                if (!value) KillAuraHandler.INSTANCE.stopAttackBlocking();
            }
            else if ("ignoreInvisible".equals(key)) KillAuraHandler.cfg().ignoreInvisible = value;
            else if ("enableNoCollision".equals(key)) KillAuraHandler.cfg().enableNoCollision = value;
            else if ("enableAntiKnockback".equals(key)) KillAuraHandler.cfg().enableAntiKnockback = value;
            else if ("disableOnDisconnect".equals(key)) KillAuraHandler.setDisableOnDisconnect(value);
            else if ("enableFullBrightVision".equals(key)) KillAuraHandler.cfg().enableFullBrightVision = value;
            else if ("huntPickupItemsEnabled".equals(key)) KillAuraHandler.cfg().huntPickupItemsEnabled = value;
            else if ("visualizeHuntRadius".equals(key)) KillAuraHandler.setVisualizeHuntRadius(value);
            else if ("huntOrbitEnabled".equals(key)) KillAuraHandler.cfg().huntOrbitEnabled = value;
            else if ("huntJumpOrbitEnabled".equals(key)) KillAuraHandler.cfg().huntJumpOrbitEnabled = value;
            else if ("enableNameWhitelist".equals(key)) KillAuraHandler.cfg().enableNameWhitelist = value;
            else if ("enableNameBlacklist".equals(key)) KillAuraHandler.cfg().enableNameBlacklist = value;
            else if ("teleportAttackRotationPackets".equals(key)) KillAuraHandler.cfg().teleportAttackRotationPackets = value;
        }

        private static String getText(String key) {
            String mode = profileMode(key);
            if (mode != null) {
                String base = key.substring(mode.length() + 1);
                if (mode.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)) return getText(base);
                return safeStatic(modeProfile(mode).get(base));
            }
            if ("attackMode".equals(key)) return safeStatic(KillAuraHandler.cfg().attackMode);
            if ("huntMode".equals(key)) return safeStatic(KillAuraHandler.cfg().huntMode);
            if ("attackSequenceName".equals(key)) return safeStatic(KillAuraHandler.cfg().attackSequenceName);
            if ("smoothMaxTurnStepSpec".equals(key)) return KillAuraHandler.getSmoothMaxTurnStepSpec();
            if ("attackSequenceDelayTicksSpec".equals(key)) return KillAuraHandler.getAttackSequenceDelayTicksSpec();
            if ("aimYawOffsetSpec".equals(key)) return KillAuraHandler.getAimYawOffsetSpec();
            if ("aimPitchOffsetSpec".equals(key)) return KillAuraHandler.getAimPitchOffsetSpec();
            if ("fullBrightGamma".equals(key)) return formatStatic(KillAuraHandler.cfg().fullBrightGamma);
            if ("teleportViewMode".equals(key)) return safeStatic(KillAuraHandler.cfg().teleportViewMode);
            if ("teleportStepDistance".equals(key)) return formatStatic(KillAuraHandler.cfg().teleportStepDistance);
            if ("teleportStationAttackRadius".equals(key)) return formatStatic(KillAuraHandler.cfg().teleportStationAttackRadius);
            if ("teleportActionsPerTick".equals(key)) return String.valueOf(KillAuraHandler.cfg().teleportActionsPerTick);
            if ("teleportStepDistancePerAction".equals(key)) return formatStatic(KillAuraHandler.cfg().teleportStepDistancePerAction);
            if ("nearbyEntityScanRange".equals(key)) return formatStatic(KillAuraHandler.cfg().nearbyEntityScanRange);
            if ("huntRadius".equals(key)) return formatStatic(KillAuraHandler.cfg().huntRadius);
            if ("huntFixedDistance".equals(key)) return formatStatic(KillAuraHandler.cfg().huntFixedDistance);
            if ("huntUpRange".equals(key)) return formatStatic(KillAuraHandler.cfg().huntUpRange);
            if ("huntDownRange".equals(key)) return formatStatic(KillAuraHandler.cfg().huntDownRange);
            if ("attackRange".equals(key)) return formatStatic(KillAuraHandler.cfg().attackRange);
            if ("minAttackStrength".equals(key)) return formatStatic(KillAuraHandler.cfg().minAttackStrength);
            if ("minTurnSpeed".equals(key)) return formatStatic(KillAuraHandler.cfg().minTurnSpeed);
            if ("maxTurnSpeed".equals(key)) return formatStatic(KillAuraHandler.cfg().maxTurnSpeed);
            if ("huntScoreRadiusWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScoreRadiusWeight);
            if ("huntScorePlayerDistanceWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScorePlayerDistanceWeight);
            if ("huntScorePlayerPlaneWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScorePlayerPlaneWeight);
            if ("huntScoreTargetHeightWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScoreTargetHeightWeight);
            if ("huntScoreAttackRangeWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScoreAttackRangeWeight);
            if ("huntScoreVisibilityWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScoreVisibilityWeight);
            if ("huntScoreOpennessWeight".equals(key)) return formatStatic(KillAuraHandler.cfg().huntScoreOpennessWeight);
            if ("huntOrbitSamplePoints".equals(key)) return String.valueOf(KillAuraHandler.cfg().huntOrbitSamplePoints);
            if ("noDamageAttackLimit".equals(key)) return String.valueOf(KillAuraHandler.cfg().noDamageAttackLimit);
            if ("minAttackIntervalTicks".equals(key)) return String.valueOf(KillAuraHandler.cfg().minAttackIntervalTicks);
            if ("targetsPerAttack".equals(key)) return String.valueOf(KillAuraHandler.cfg().targetsPerAttack);
            return "";
        }

        private static void setText(String key, String text) {
            String mode = profileMode(key);
            if (mode != null) {
                String base = key.substring(mode.length() + 1);
                if (mode.equalsIgnoreCase(KillAuraHandler.cfg().attackMode)) setText(base, text);
                else modeProfile(mode).put(base, safeStatic(text).trim());
                return;
            }
            if ("attackMode".equals(key)) {
                KillAuraHandler.cfg().attackMode = safeStatic(text).trim();
            } else if ("huntMode".equals(key)) {
                KillAuraHandler.setHuntMode(text);
            } else if ("attackSequenceName".equals(key)) {
                KillAuraHandler.cfg().attackSequenceName = safeStatic(text).trim();
            } else if ("smoothMaxTurnStepSpec".equals(key)) {
                KillAuraHandler.setSmoothMaxTurnStepSpec(text);
            } else if ("attackSequenceDelayTicksSpec".equals(key)) {
                KillAuraHandler.setAttackSequenceDelayTicksSpec(text);
            } else if ("aimYawOffsetSpec".equals(key)) {
                KillAuraHandler.setAimYawOffsetSpec(text);
            } else if ("aimPitchOffsetSpec".equals(key)) {
                KillAuraHandler.setAimPitchOffsetSpec(text);
            } else if ("fullBrightGamma".equals(key)) KillAuraHandler.cfg().fullBrightGamma = floatStatic(text, KillAuraHandler.cfg().fullBrightGamma, 1, 1000);
            else if ("teleportViewMode".equals(key)) KillAuraHandler.cfg().teleportViewMode = text;
            else if ("teleportStepDistance".equals(key)) KillAuraHandler.cfg().teleportStepDistance = floatStatic(text,
                    KillAuraHandler.cfg().teleportStepDistance, KillAuraHandler.MIN_TELEPORT_STEP_DISTANCE,
                    KillAuraHandler.MAX_TELEPORT_STEP_DISTANCE);
            else if ("teleportStationAttackRadius".equals(key)) KillAuraHandler.cfg().teleportStationAttackRadius = floatStatic(
                    text, KillAuraHandler.cfg().teleportStationAttackRadius,
                    KillAuraHandler.MIN_TELEPORT_STATION_ATTACK_RADIUS,
                    KillAuraHandler.MAX_TELEPORT_STATION_ATTACK_RADIUS);
            else if ("teleportActionsPerTick".equals(key)) KillAuraHandler.cfg().teleportActionsPerTick = intStatic(
                    text, KillAuraHandler.cfg().teleportActionsPerTick,
                    KillAuraHandler.MIN_TELEPORT_ACTIONS_PER_TICK,
                    KillAuraHandler.MAX_TELEPORT_ACTIONS_PER_TICK);
            else if ("teleportStepDistancePerAction".equals(key)) KillAuraHandler.cfg().teleportStepDistancePerAction = floatStatic(
                    text, KillAuraHandler.cfg().teleportStepDistancePerAction,
                    KillAuraHandler.MIN_TELEPORT_STEP_DISTANCE_PER_ACTION,
                    KillAuraHandler.MAX_TELEPORT_STEP_DISTANCE_PER_ACTION);
            else if ("nearbyEntityScanRange".equals(key)) KillAuraHandler.cfg().nearbyEntityScanRange = floatStatic(text, KillAuraHandler.cfg().nearbyEntityScanRange, 1, 64);
            else if ("huntRadius".equals(key)) KillAuraHandler.cfg().huntRadius = floatStatic(text, KillAuraHandler.cfg().huntRadius, KillAuraHandler.cfg().attackRange, 100);
            else if ("huntFixedDistance".equals(key)) KillAuraHandler.cfg().huntFixedDistance = floatStatic(text, KillAuraHandler.cfg().huntFixedDistance, 0.5F, 100);
            else if ("huntUpRange".equals(key)) KillAuraHandler.cfg().huntUpRange = floatStatic(text, KillAuraHandler.cfg().huntUpRange, 0, 100);
            else if ("huntDownRange".equals(key)) KillAuraHandler.cfg().huntDownRange = floatStatic(text, KillAuraHandler.cfg().huntDownRange, 0, 100);
            else if ("attackRange".equals(key)) {
                KillAuraHandler.cfg().attackRange = floatStatic(text, KillAuraHandler.cfg().attackRange, 1, 100);
                KillAuraHandler.cfg().huntRadius = Math.max(KillAuraHandler.cfg().huntRadius, KillAuraHandler.cfg().attackRange);
            } else if ("minAttackStrength".equals(key)) KillAuraHandler.cfg().minAttackStrength = floatStatic(text, KillAuraHandler.cfg().minAttackStrength, 0, 1);
            else if ("minTurnSpeed".equals(key)) {
                KillAuraHandler.cfg().minTurnSpeed = floatStatic(text, KillAuraHandler.cfg().minTurnSpeed, 1, 40);
                KillAuraHandler.cfg().maxTurnSpeed = Math.max(KillAuraHandler.cfg().maxTurnSpeed, KillAuraHandler.cfg().minTurnSpeed);
            } else if ("maxTurnSpeed".equals(key)) KillAuraHandler.cfg().maxTurnSpeed = floatStatic(text, KillAuraHandler.cfg().maxTurnSpeed, KillAuraHandler.cfg().minTurnSpeed, 60);
            else if ("huntScoreRadiusWeight".equals(key)) KillAuraHandler.cfg().huntScoreRadiusWeight = floatStatic(text, KillAuraHandler.cfg().huntScoreRadiusWeight, 0, 100);
            else if ("huntScorePlayerDistanceWeight".equals(key)) KillAuraHandler.cfg().huntScorePlayerDistanceWeight = floatStatic(text, KillAuraHandler.cfg().huntScorePlayerDistanceWeight, 0, 100);
            else if ("huntScorePlayerPlaneWeight".equals(key)) KillAuraHandler.cfg().huntScorePlayerPlaneWeight = floatStatic(text, KillAuraHandler.cfg().huntScorePlayerPlaneWeight, 0, 100);
            else if ("huntScoreTargetHeightWeight".equals(key)) KillAuraHandler.cfg().huntScoreTargetHeightWeight = floatStatic(text, KillAuraHandler.cfg().huntScoreTargetHeightWeight, 0, 100);
            else if ("huntScoreAttackRangeWeight".equals(key)) KillAuraHandler.cfg().huntScoreAttackRangeWeight = floatStatic(text, KillAuraHandler.cfg().huntScoreAttackRangeWeight, 0, 100);
            else if ("huntScoreVisibilityWeight".equals(key)) KillAuraHandler.cfg().huntScoreVisibilityWeight = floatStatic(text, KillAuraHandler.cfg().huntScoreVisibilityWeight, 0, 100);
            else if ("huntScoreOpennessWeight".equals(key)) KillAuraHandler.cfg().huntScoreOpennessWeight = floatStatic(text, KillAuraHandler.cfg().huntScoreOpennessWeight, 0, 100);
            else if ("huntOrbitSamplePoints".equals(key)) KillAuraHandler.cfg().huntOrbitSamplePoints = intStatic(text, KillAuraHandler.cfg().huntOrbitSamplePoints, KillAuraHandler.MIN_HUNT_ORBIT_SAMPLE_POINTS, KillAuraHandler.MAX_HUNT_ORBIT_SAMPLE_POINTS);
            else if ("noDamageAttackLimit".equals(key)) KillAuraHandler.cfg().noDamageAttackLimit = intStatic(text, KillAuraHandler.cfg().noDamageAttackLimit, 0, KillAuraHandler.MAX_NO_DAMAGE_ATTACK_LIMIT);
            else if ("minAttackIntervalTicks".equals(key)) KillAuraHandler.cfg().minAttackIntervalTicks = intStatic(text, KillAuraHandler.cfg().minAttackIntervalTicks, 0, 20);
            else if ("targetsPerAttack".equals(key)) KillAuraHandler.cfg().targetsPerAttack = intStatic(text, KillAuraHandler.cfg().targetsPerAttack, 1, 50);
        }

        private static String safeStatic(String value) {
            return value == null ? "" : value;
        }

        private static String formatStatic(float value) {
            return String.format(Locale.ROOT, "%.2f", value);
        }

        private static float floatStatic(String text, float fallback, float minimum, float maximum) {
            float value = fallback;
            try {
                value = Float.parseFloat(safeStatic(text).trim());
            } catch (Exception ignored) {
            }
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                value = fallback;
            }
            return Math.max(minimum, Math.min(maximum, value));
        }

        private static int intStatic(String text, int fallback, int minimum, int maximum) {
            int value = fallback;
            try {
                value = Integer.parseInt(safeStatic(text).trim());
            } catch (Exception ignored) {
            }
            return Math.max(minimum, Math.min(maximum, value));
        }

        private static List<String> copyStrings(List<String> source) {
            return source == null ? new ArrayList<String>() : new ArrayList<String>(source);
        }

        private static List<HuntPickupRule> copyRules(List<HuntPickupRule> source) {
            List<HuntPickupRule> result = new ArrayList<>();
            if (source != null) {
                for (HuntPickupRule rule : source) {
                    if (rule != null) {
                        result.add(new HuntPickupRule(rule));
                    }
                }
            }
            return result;
        }

        private static List<KillAuraPreset> copyPresets(List<KillAuraPreset> source) {
            List<KillAuraPreset> result = new ArrayList<>();
            if (source != null) {
                for (KillAuraPreset preset : source) {
                    if (preset != null) {
                        result.add(new KillAuraPreset(preset));
                    }
                }
            }
            return result;
        }
    }

    private void clearAllFieldBounds() {
        for (List<Section> groupSections : sections.values()) {
            for (Section section : groupSections) {
                for (Field field : section.fields) {
                    field.clearBounds();
                }
            }
        }
        sectionLayouts.clear();
        presetNameBounds = null;
        presetNewBounds = null;
        presetApplyBounds = null;
        presetOverwriteBounds = null;
        presetRenameBounds = null;
        presetDeleteBounds = null;
        presetListBounds = null;
        nameEntryBounds = null;
        scanNamesBounds = null;
        addToNameGroupBounds = null;
        newNameGroupBounds = null;
        renameNameGroupBounds = null;
        nearbyListBounds = null;
        nameGroupsBounds = null;
        nameGroupCards.clear();
        addRuleBounds = null;
        deleteRuleBounds = null;
        ruleListBounds = null;
        ruleToggleBounds = null;
        ruleModeBounds = null;
        ruleExpressionAddBounds = null;
        ruleExpressionHits.clear();
        rarityBounds.clear();
        huntScoreBounds = null;
    }

    private void hideAllInputs() {
        forEachInput(new InputVisitor() {
            @Override
            public void visit(ModernTextField input) {
                input.setVisible(false);
            }
        });
    }

    private void drawSectionLayouts(int mouseX, int mouseY) {
        for (SectionLayout layout : sectionLayouts) {
            Section section = layout.section;
            if (section.modeCard != null) {
                drawModeCardFrame(layout, mouseX, mouseY);
            } else {
                ModernUiRenderer.drawText(fontRenderer, section.title, layout.bounds.x, layout.bounds.y + 2,
                        ModernUiRenderer.SUBTLE_TEXT, Math.max(32, layout.bounds.width - 16));
                drawInfoIcon(layout.bounds.x + Math.min(Math.max(0, layout.bounds.width - 12),
                        fontRenderer.getStringWidth(t(section.title)) + 5), layout.bounds.y + 1, section.tooltip,
                        mouseX, mouseY);
            }
            if (section.collapsible && !section.expanded) {
                continue;
            }
            for (Field field : section.fields) {
                if (field.visible() && field.rowBounds != null) {
                    drawField(field, mouseX, mouseY);
                }
            }
        }
    }

    private void drawModeCardFrame(SectionLayout layout, int mouseX, int mouseY) {
        Section section = layout.section;
        ModernMainLayout.Rect bounds = layout.bounds;
        ModernMainLayout.Rect header = section.headerBounds;
        boolean active = section.modeCard.equalsIgnoreCase(KillAuraHandler.cfg().attackMode);
        boolean headerHovered = contains(header, mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 6,
                headerHovered && !section.expanded ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int textY = header.y + (header.height - fontRenderer.FONT_HEIGHT) / 2;
        int chevronY = header.y + header.height / 2;
        if (section.expanded) {
            ModernUiRenderer.drawDropdownChevron(header.x + 8, chevronY - 2, ModernUiRenderer.SUBTLE_TEXT);
        } else {
            ModernUiRenderer.drawChevron(header.x + 10, chevronY - 4, true, ModernUiRenderer.SUBTLE_TEXT);
        }
        String name = t(section.title);
        int nameX = header.x + 20;
        ModernUiRenderer.drawText(fontRenderer, name, nameX, textY,
                active ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(24, header.width / 3));
        int nameWidth = Math.min(fontRenderer.getStringWidth(name), Math.max(24, header.width / 3));

        String label = active ? t("gui.modern.killaura.u275") : t("gui.modern.killaura.u274");
        int buttonWidth = Math.max(44, fontRenderer.getStringWidth(label) + 16);
        ModernMainLayout.Rect button = new ModernMainLayout.Rect(header.right() - buttonWidth - 6, header.y + 4,
                buttonWidth, header.height - 8);
        if (active) {
            ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 4,
                    ModernUiRenderer.ACCENT_DIM, ModernUiRenderer.ACCENT);
            ModernUiRenderer.drawText(fontRenderer, label,
                    button.x + (button.width - fontRenderer.getStringWidth(label)) / 2,
                    button.y + (button.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT, button.width - 4);
        } else {
            boolean switchable = !KillAuraHandler.aimOnlyMode();
            section.activateBounds = switchable ? button : null;
            drawActionButton(button, label, switchable, mouseX, mouseY, false);
        }
        int summaryX = nameX + nameWidth + 8;
        int summaryWidth = button.x - 6 - summaryX;
        if (summaryWidth > 24) {
            ModernUiRenderer.drawText(fontRenderer, modeSummary(section.modeCard), summaryX, textY,
                    ModernUiRenderer.MUTED_TEXT, summaryWidth);
        }
        if (headerHovered && !contains(button, mouseX, mouseY)) {
            hoveredTooltip = section.tooltip;
        }
    }

    private String modeSummary(String mode) {
        String range = State.getText(profileKey(mode, KEY_RANGE));
        if (KillAuraHandler.ATTACK_MODE_TELEPORT.equals(mode)) {
            return t("gui.modern.killaura.u271", range, State.getText(profileKey(mode, "teleportStepDistance")),
                    State.getText(profileKey(mode, "teleportActionsPerTick")),
                    State.getText(profileKey(mode, KEY_TARGETS)));
        }
        if (KillAuraHandler.ATTACK_MODE_SEQUENCE.equals(mode)) {
            String sequenceName = safe(KillAuraHandler.cfg().attackSequenceName).trim();
            return t("gui.modern.killaura.u272", range,
                    sequenceName.isEmpty() ? t("gui.modern.killaura.u277") : sequenceName,
                    State.getText(profileKey(mode, KEY_DELAY)));
        }
        if (KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equals(mode)) {
            return t("gui.modern.killaura.u273", range, State.getText(profileKey(mode, KEY_STRENGTH)),
                    State.getText(profileKey(mode, KEY_DELAY)));
        }
        return t("gui.modern.killaura.u270", range, State.getText(profileKey(mode, KEY_INTERVAL)),
                State.getText(profileKey(mode, KEY_STRENGTH)), State.getText(profileKey(mode, KEY_TARGETS)));
    }

    private void drawField(Field field, int mouseX, int mouseY) {
        ModernMainLayout.Rect row = field.rowBounds;
        boolean enabled = field.enabled();
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                !enabled ? 0xFF141D25 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        if (field.type == FieldType.CHOICE) {
            ModernUiRenderer.drawText(fontRenderer, field.title, row.x + 9, row.y + 5,
                    enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(26, row.width - 20));
            field.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, row.width - 32),
                    fontRenderer.getStringWidth(field.title) + 5), row.y + 4, field.tooltip, mouseX, mouseY);
            drawChoiceField(field, enabled, mouseX, mouseY);
            return;
        }
        boolean compact = field.type != FieldType.TOGGLE && row.width < 220;
        int titleY = row.y + (compact ? 5 : (row.height - fontRenderer.FONT_HEIGHT) / 2);
        int titleWidth = compact ? row.width - 20 : Math.max(28, row.width - 120);
        ModernUiRenderer.drawText(fontRenderer, field.title, row.x + 9, titleY,
                enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, titleWidth);
        field.infoBounds = drawInfoIcon(row.x + 9 + Math.min(Math.max(0, titleWidth - 12),
                fontRenderer.getStringWidth(field.title) + 5), titleY - 1, field.tooltip, mouseX, mouseY);
        if (field.type == FieldType.TOGGLE) {
            field.controlBounds = new ModernMainLayout.Rect(row.right() - 46, row.y + (row.height - 16) / 2, 36, 16);
            ModernUiRenderer.drawToggle(field.controlBounds.x, field.controlBounds.y, field.controlBounds.width,
                    field.controlBounds.height, State.getBoolean(field.key), hovered && enabled);
        } else if (field.type == FieldType.ACTION) {
            field.controlBounds = new ModernMainLayout.Rect(row.right() - Math.min(126, Math.max(76, row.width / 3)) - 8,
                    row.y + 5, Math.min(126, Math.max(76, row.width / 3)), row.height - 10);
            drawActionButton(field.controlBounds, field.actionLabel, enabled, mouseX, mouseY, false);
        } else if (field.type == FieldType.SEQUENCE) {
            int controlWidth = compact ? row.width - 18 : Math.min(178, Math.max(74, row.width / 2));
            field.controlBounds = compact ? new ModernMainLayout.Rect(row.x + 9, row.bottom() - 25, controlWidth, 21)
                    : new ModernMainLayout.Rect(row.right() - controlWidth - 8, row.y + 5, controlWidth, 22);
            drawSequencePickerField(field, enabled, mouseX, mouseY);
        } else {
            int controlWidth = compact ? row.width - 18 : Math.min(178, Math.max(74, row.width / 2));
            field.controlBounds = compact ? new ModernMainLayout.Rect(row.x + 9, row.bottom() - 25, controlWidth, 21)
                    : new ModernMainLayout.Rect(row.right() - controlWidth - 8, row.y + 5, controlWidth, 22);
            drawInput(field.input, field.controlBounds, field.placeholder, enabled, mouseX, mouseY);
        }
    }

    private void drawChoiceField(Field field, boolean enabled, int mouseX, int mouseY) {
        int x = field.rowBounds.x + 9;
        int y = field.rowBounds.y + 20;
        int available = Math.max(1, field.rowBounds.width - 18);
        String selected = State.getText(field.key);
        for (Choice choice : field.choices) {
            int optionWidth = Math.min(available, Math.max(48, fontRenderer.getStringWidth(choice.label) + 16));
            if (x > field.rowBounds.x + 9 && x + optionWidth > field.rowBounds.right() - 9) {
                x = field.rowBounds.x + 9;
                y += 21;
            }
            ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, optionWidth, 18);
            boolean chosen = choice.value.equalsIgnoreCase(selected);
            boolean hovered = bounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                    chosen ? ModernUiRenderer.ACCENT_DIM : hovered && enabled ? ModernUiRenderer.SURFACE_PRESSED
                            : 0xFF111A22,
                    chosen ? ModernUiRenderer.ACCENT : hovered && enabled ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, choice.label, bounds.x + 7,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                    chosen ? ModernUiRenderer.TEXT : enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT,
                    bounds.width - 12);
            x += optionWidth + 4;
        }
    }

    private void drawSequencePickerField(Field field, boolean enabled, int mouseX, int mouseY) {
        ModernMainLayout.Rect bounds = field.controlBounds;
        String value = State.getText(field.key).trim();
        boolean hovered = bounds != null && bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                hovered && enabled ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered && enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, value.isEmpty() ? field.placeholder : value,
                bounds.x + 7, bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                value.isEmpty() ? ModernUiRenderer.MUTED_TEXT
                        : enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, bounds.width - 14));
    }

    private void drawInput(ModernTextField input, ModernMainLayout.Rect bounds, String placeholder, boolean enabled,
            int mouseX, int mouseY) {
        if (input == null || bounds == null) {
            return;
        }
        input.setVisible(true);
        input.setEnabled(enabled);
        if (input == focusedInputBeforeLayoutReset) {
            input.setFocused(enabled);
        }
        input.x = bounds.x + 7;
        input.y = bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2;
        input.width = Math.max(1, bounds.width - 14);
        input.height = fontRenderer.FONT_HEIGHT + 2;
        boolean focused = input.isFocused();
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered && enabled ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        input.setTextColor(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        input.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.reflowTextField(input);
        ModernUiRenderer.drawTextField(input);
        if (!focused && input.getText().trim().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, input.x, input.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, input.width));
        }
    }

    private ModernMainLayout.Rect drawInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty()) {
            return null;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x, y, 11, 11);
        boolean hovered = bounds.contains(mouseX, mouseY)
                && (!drawingContent || contentClipBounds == null || contentClipBounds.contains(mouseX, mouseY));
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        return bounds;
    }
}
