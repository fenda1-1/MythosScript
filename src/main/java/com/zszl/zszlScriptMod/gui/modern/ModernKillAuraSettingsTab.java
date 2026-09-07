package com.zszl.zszlScriptMod.gui.modern;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler.HuntPickupRule;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler.HuntScoreDebugEntry;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler.KillAuraPreset;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.path.PathSequenceManager;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

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

    private static final int ROW_HEIGHT = 34;
    private static final int ROW_GAP = 5;
    private static final int SECTION_HEADER_HEIGHT = 21;
    private static final int SPECIAL_TITLE_HEIGHT = 20;
    private static final int LIST_CARD_HEIGHT = 25;
    private static final int LIST_CARD_GAP = 4;

    private enum Group {
        PRESET("gui.modern.killaura.u001", "gui.modern.killaura.u002"),
        ATTACK("gui.modern.killaura.u003", "gui.modern.killaura.u004"),
        TARGET("gui.modern.killaura.u005", "gui.modern.killaura.u006"),
        NAME_FILTER("gui.modern.killaura.u007", "gui.modern.killaura.u008"),
        HUNT("gui.modern.killaura.u009", "gui.modern.killaura.u010"),
        PROTECTION("gui.modern.killaura.u011", "gui.modern.killaura.u012"),
        STATS("gui.modern.killaura.u013", "gui.modern.killaura.u014" );

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
        DECIMAL,
        INTEGER,
        CHOICE,
        ACTION
    }

    private enum NameListKind {
        NEARBY,
        WHITELIST,
        BLACKLIST
    }

    private enum RuleInputKey {
        NAME("gui.modern.killaura.u015", "gui.modern.killaura.u016"),
        CATEGORY("gui.modern.killaura.u017", "gui.modern.killaura.u018"),
        NAME_KEYWORD("gui.modern.killaura.u019", "gui.modern.killaura.u020"),
        ITEM_ID_KEYWORD("gui.modern.killaura.u021", "gui.modern.killaura.u022"),
        NBT_TAGS("gui.modern.killaura.u023", "gui.modern.killaura.u024"),
        EXPRESSIONS("gui.modern.killaura.u025", "gui.modern.killaura.u026"),
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
        private GuiTextField input;
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

        private Section(String title, String tooltip) {
            this.title = title == null ? "" : title;
            this.tooltip = tooltip == null ? "" : tooltip;
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

    private static final class RuleCardHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private RuleCardHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private final Map<Group, List<Section>> sections = new EnumMap<>(Group.class);
    private final List<SectionLayout> sectionLayouts = new ArrayList<>();
    private final List<PresetCardHit> presetCardHits = new ArrayList<>();
    private final List<NameRowHit> nameRowHits = new ArrayList<>();
    private final List<RuleCardHit> ruleCardHits = new ArrayList<>();
    private final Map<RuleInputKey, GuiTextField> ruleInputs = new EnumMap<>(RuleInputKey.class);
    private final Map<RuleInputKey, ModernMainLayout.Rect> ruleInputBounds = new EnumMap<>(RuleInputKey.class);
    private final List<String> nearbyNames = new ArrayList<>();
    private final List<KillAuraPreset> presetCards = new ArrayList<>();
    private final List<HuntScoreDebugEntry> huntScoreEntries = new ArrayList<>();

    private FontRenderer fontRenderer;
    private ModernMainLayout.Rect panelBounds;
    private ModernMainLayout.Rect groupRailBounds;
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
    private ModernMainLayout.Rect addWhitelistBounds;
    private ModernMainLayout.Rect addBlacklistBounds;
    private ModernMainLayout.Rect nearbyListBounds;
    private ModernMainLayout.Rect whitelistListBounds;
    private ModernMainLayout.Rect blacklistListBounds;

    private ModernMainLayout.Rect addRuleBounds;
    private ModernMainLayout.Rect deleteRuleBounds;
    private ModernMainLayout.Rect ruleListBounds;
    private ModernMainLayout.Rect ruleToggleBounds;
    private ModernMainLayout.Rect ruleModeBounds;
    private final Map<String, ModernMainLayout.Rect> rarityBounds = new java.util.LinkedHashMap<>();
    private ModernMainLayout.Rect huntScoreBounds;

    private GuiTextField presetNameField;
    private GuiTextField nameEntryField;
    private int selectedPresetIndex = -1;
    private int selectedWhitelistIndex = -1;
    private int selectedRuleIndex = -1;
    private int scrollOffset;
    private int maxScrollOffset;
    private final ModernHoverScrollbar scrollbar = new ModernHoverScrollbar();
    private Group activeGroup = Group.ATTACK;
    private String hoveredTooltip = "";
    private String statusMessage = "";
    private long statusMessageUntil;
    private boolean drawingContent;
    private boolean initialized;
    private boolean immediatePersistenceOccurred;
    private boolean draftDirty;
    private State savedState;

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
        Section attackCore = section(Group.ATTACK, "gui.modern.killaura.u031", "gui.modern.killaura.u032");
        attackCore.fields.add(choice("attackMode", "gui.modern.killaura.u031", "gui.modern.killaura.u033",
                choices(new Choice(KillAuraHandler.ATTACK_MODE_NORMAL, "gui.modern.killaura.u034"),
                        new Choice(KillAuraHandler.ATTACK_MODE_PACKET, "gui.modern.killaura.u035"),
                        new Choice(KillAuraHandler.ATTACK_MODE_TELEPORT, "gui.modern.killaura.u036"),
                        new Choice(KillAuraHandler.ATTACK_MODE_SEQUENCE, "gui.modern.killaura.u037"),
                        new Choice(KillAuraHandler.ATTACK_MODE_MOUSE_CLICK, "gui.modern.killaura.u038"))).fullWidth()
                .enabledWhen(condition("notAimOnly")));
        attackCore.fields.add(integer("teleportAttackPacketLimitPerTick", "gui.modern.killaura.u039",
                "gui.modern.killaura.u040",
                KillAuraHandler.MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK,
                KillAuraHandler.MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK)
                .visibleWhen(condition("teleportMode")).enabledWhen(condition("notAimOnly")));
        attackCore.fields.add(decimal("teleportStepDistance", "gui.modern.killaura.u041",
                "gui.modern.killaura.u042", 0, 200)
                .visibleWhen(condition("teleportMode")).enabledWhen(condition("notAimOnly")));
        attackCore.fields.add(decimal("teleportStationAttackRadius", "gui.modern.killaura.u043",
                "gui.modern.killaura.u044", 0, 200)
                .visibleWhen(condition("teleportMode")).enabledWhen(condition("notAimOnly")));
        attackCore.fields.add(toggle("aimOnlyMode", "gui.modern.killaura.u045", "gui.modern.killaura.u046"));
        attackCore.fields.add(text("attackSequenceName", "gui.modern.killaura.u047", "gui.modern.killaura.u048", "gui.modern.killaura.u049")
                .fullWidth().visibleWhen(condition("sequenceMode")));
        attackCore.fields.add(text("attackSequenceDelayTicksSpec", "gui.modern.killaura.u050", "gui.modern.killaura.u051", "1-6")
                .visibleWhen(condition("delayMode")));
        attackCore.fields.add(action("gui.modern.killaura.u052", "gui.modern.killaura.u053", "gui.modern.killaura.u054", new FieldAction() {
            @Override
            public void execute() {
                PathSequenceManager.initializePathSequences();
                showStatus("gui.modern.killaura.u055");
            }
        }).visibleWhen(condition("sequenceMode")));
        attackCore.fields.add(toggle("onlyWeapon", "gui.modern.killaura.u056", "gui.modern.killaura.u057")
                .enabledWhen(condition("weaponApplicable")));
        attackCore.fields.add(toggle("requireLineOfSight", "gui.modern.killaura.u058", "gui.modern.killaura.u059"));
        attackCore.fields.add(toggle("throughWallAttack", "gui.modern.killaura.u060", "gui.modern.killaura.u061"));
        attackCore.fields.add(toggle("focusSingleTarget", "gui.modern.killaura.u062", "gui.modern.killaura.u063"));

        Section rotation = section(Group.ATTACK, "gui.modern.killaura.u064", "gui.modern.killaura.u065");
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
        rotation.fields.add(text("aimYawOffsetSpec", "gui.modern.killaura.u078", "gui.modern.killaura.u079", "-3-3")
                .enabledWhen(condition("rotationAvailable")));
        rotation.fields.add(text("aimPitchOffsetSpec", "gui.modern.killaura.u080", "gui.modern.killaura.u081", "-11-11")
                .enabledWhen(condition("rotationAvailable")));

        Section targets = section(Group.TARGET, "gui.modern.killaura.u082", "gui.modern.killaura.u083");
        targets.fields.add(toggle("targetHostile", "gui.modern.killaura.u084", "gui.modern.killaura.u085"));
        targets.fields.add(toggle("targetPassive", "gui.modern.killaura.u086", "gui.modern.killaura.u087"));
        targets.fields.add(toggle("targetPlayers", "gui.modern.killaura.u088", "gui.modern.killaura.u089"));
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
        names.fields.add(toggle("enableNameWhitelist", "gui.modern.killaura.u118", "gui.modern.killaura.u119"));
        names.fields.add(toggle("enableNameBlacklist", "gui.modern.killaura.u120", "gui.modern.killaura.u121"));
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

        Section stats = section(Group.STATS, "gui.modern.killaura.u160", "gui.modern.killaura.u161");
        stats.fields.add(decimal("attackRange", "gui.modern.killaura.u162", "gui.modern.killaura.u163", 1, 100));
        stats.fields.add(decimal("minAttackStrength", "gui.modern.killaura.u164", "gui.modern.killaura.u165", 0, 1)
                .enabledWhen(condition("normalAttackStats")));
        stats.fields.add(decimal("minTurnSpeed", "gui.modern.killaura.u166", "gui.modern.killaura.u167", 1, 40));
        stats.fields.add(decimal("maxTurnSpeed", "gui.modern.killaura.u168", "gui.modern.killaura.u169", 1, 60));
        stats.fields.add(integer("minAttackIntervalTicks", "gui.modern.killaura.u170", "gui.modern.killaura.u171", 0, 20)
                .enabledWhen(condition("normalAttackStats")));
        stats.fields.add(integer("targetsPerAttack", "gui.modern.killaura.u172", "gui.modern.killaura.u173", 1, 50)
                .enabledWhen(condition("normalAttackStats")));
    }

    private Section section(Group group, String title, String tooltip) {
        Section section = new Section(title, tooltip);
        sections.get(group).add(section);
        return section;
    }

    private static Field toggle(String key, String title, String tooltip) {
        return new Field(FieldType.TOGGLE, key, title, tooltip, "", "", 0, 0, null, null);
    }

    private static Field text(String key, String title, String tooltip, String placeholder) {
        return new Field(FieldType.TEXT, key, title, tooltip, "", placeholder, 0, 0, null, null);
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
        KillAuraHandler.loadConfig();
        createInputFields(renderer);
        refreshPresetCards("");
        refreshNearbyNames();
        savedState = new State();
        draftDirty = false;
        initialized = true;
    }

    @Override
    public void updateScreen() {
        forEachInput(new InputVisitor() {
            @Override
            public void visit(GuiTextField input) {
                input.updateCursorCounter();
            }
        });
    }

    @Override
    public void draw(FontRenderer renderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        ensureInitialized(renderer);
        this.fontRenderer = renderer;
        hoveredTooltip = "";
        drawingContent = false;
        panelBounds = buildPanelBounds(contentBounds);
        ModernUiRenderer.drawPanel(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);

        int headerHeight = 1;
        int footerHeight = statusVisible() ? 53 : 33;
        int bodyY = panelBounds.y + headerHeight + 7;
        int bodyHeight = Math.max(1, panelBounds.height - headerHeight - footerHeight - 14);
        int railWidth = Math.min(120, Math.max(82, panelBounds.width / 4));
        groupRailBounds = new ModernMainLayout.Rect(panelBounds.x + 9, bodyY, railWidth, bodyHeight);

        int editorX = groupRailBounds.right() + 8;
        int editorWidth = Math.max(1, panelBounds.right() - 9 - editorX);
        int editorHeaderHeight = 24;
        ModernMainLayout.Rect editorBounds = new ModernMainLayout.Rect(editorX, bodyY, editorWidth, bodyHeight);
        contentClipBounds = new ModernMainLayout.Rect(editorBounds.x + 5, editorBounds.y + editorHeaderHeight + 4,
                Math.max(1, editorBounds.width - 10), Math.max(1, editorBounds.height - editorHeaderHeight - 8));

        drawGroupRail(mouseX, mouseY);
        drawEditorHeader(editorBounds, mouseX, mouseY);

        if (activeGroup == Group.TARGET) {
            refreshHuntScoreEntries();
        }
        int formX = contentClipBounds.x + 5;
        int formWidth = ModernHoverScrollbar.contentWidth(contentClipBounds.width - 7);
        int columns = formWidth >= 430 ? 2 : 1;
        int formHeight = layoutActiveContent(formX, 0, formWidth, columns, false);
        maxScrollOffset = Math.max(0, formHeight - contentClipBounds.height);
        scrollOffset = clamp(scrollOffset, 0, maxScrollOffset);

        clearAllFieldBounds();
        layoutActiveContent(formX, contentClipBounds.y + 3 - scrollOffset, formWidth, columns, true);
        hideAllInputs();
        drawingContent = true;
        ModernUiRenderer.beginClip(contentClipBounds);
        drawSectionLayouts(mouseX, mouseY);
        drawSpecialContent(formX, formWidth, mouseX, mouseY);
        ModernUiRenderer.endClip();
        drawingContent = false;
        drawScrollbar(mouseX, mouseY);
        drawFooter(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0 || panelBounds == null || !panelBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (contains(headerToggleBounds, mouseX, mouseY)) {
            KillAuraHandler.INSTANCE.setEnabled(!KillAuraHandler.enabled);
            immediatePersistenceOccurred = true;
            showStatus(KillAuraHandler.enabled ? "gui.modern.killaura.u174" : "gui.modern.killaura.u175");
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
        if (clickedMouseButton == 0 && scrollbar.isDragging()) {
            scrollbar.applyDrag(mouseX, mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0 && scrollbar.isDragging()) {
            scrollbar.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        if (wheel == 0 || maxScrollOffset <= 0) {
            return false;
        }
        int before = scrollOffset;
        scrollOffset = clamp(scrollOffset + (wheel > 0 ? -32 : 32), 0, maxScrollOffset);
        return before != scrollOffset;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return contains(contentClipBounds, mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        if (!initialized || savedState == null) {
            return;
        }
        savedState.apply();
        if (immediatePersistenceOccurred) {
            KillAuraHandler.saveConfig();
        }
        immediatePersistenceOccurred = false;
        scrollbar.endDrag();
        draftDirty = false;
        refreshAfterStateChange();
        statusMessage = "";
    }

    @Override
    public boolean isDirty() {
        return draftDirty || immediatePersistenceOccurred;
    }

    private ModernMainLayout.Rect buildPanelBounds(ModernMainLayout.Rect contentBounds) {
        ModernMainLayout.Rect source = contentBounds == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : contentBounds;
        int insetX = Math.max(7, Math.min(14, source.width / 17));
        int insetY = Math.max(6, Math.min(9, source.height / 15));
        return new ModernMainLayout.Rect(source.x + insetX, source.y + insetY,
                Math.max(1, source.width - insetX * 2), Math.max(1, source.height - insetY * 2));
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
            ruleInputs.put(key, createTextField(renderer, key == RuleInputKey.EXPRESSIONS ? 2048 : 256));
        }
        syncInputFields();
    }

    private GuiTextField createTextField(FontRenderer renderer, int maxLength) {
        GuiTextField field = new GuiTextField(0, renderer, 0, 0, 1, 18);
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
                int sectionTop = currentY;
                currentY += SECTION_HEADER_HEIGHT;
                int rowWidth = Math.max(1, (width - ROW_GAP * Math.max(0, columns - 1)) / columns);
                int index = 0;
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
                if (assign) {
                    sectionLayouts.add(new SectionLayout(section,
                            new ModernMainLayout.Rect(x, sectionTop, width, Math.max(1, currentY - sectionTop))));
                }
                currentY += 3;
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
        int whitelistHeight = listPanelHeight(nameListSize(NameListKind.WHITELIST));
        int blacklistHeight = listPanelHeight(nameListSize(NameListKind.BLACKLIST));
        int listsHeight = width >= 390 ? Math.max(whitelistHeight, blacklistHeight)
                : whitelistHeight + blacklistHeight + 6;
        return SPECIAL_TITLE_HEIGHT + 27 + 31 + nearbyHeight + 8 + listsHeight + 4;
    }

    private int layoutNameContent(int x, int y, int width) {
        y += SPECIAL_TITLE_HEIGHT;
        nameEntryBounds = new ModernMainLayout.Rect(x, y, width, 22);
        y += 27;
        int gap = 4;
        int third = Math.max(1, (width - gap * 2) / 3);
        scanNamesBounds = new ModernMainLayout.Rect(x, y, third, 20);
        addWhitelistBounds = new ModernMainLayout.Rect(x + third + gap, y, third, 20);
        addBlacklistBounds = new ModernMainLayout.Rect(x + (third + gap) * 2, y,
                Math.max(1, width - (third + gap) * 2), 20);
        y += 31;
        nearbyListBounds = new ModernMainLayout.Rect(x, y, width, listPanelHeight(nearbyNames.size()));
        y = nearbyListBounds.bottom() + 8;
        if (width >= 390) {
            int half = Math.max(1, (width - gap) / 2);
            int listHeight = Math.max(listPanelHeight(nameListSize(NameListKind.WHITELIST)),
                    listPanelHeight(nameListSize(NameListKind.BLACKLIST)));
            whitelistListBounds = new ModernMainLayout.Rect(x, y, half, listHeight);
            blacklistListBounds = new ModernMainLayout.Rect(x + half + gap, y, Math.max(1, width - half - gap),
                    listHeight);
            return y + listHeight + 4;
        }
        whitelistListBounds = new ModernMainLayout.Rect(x, y, width, listPanelHeight(nameListSize(NameListKind.WHITELIST)));
        y = whitelistListBounds.bottom() + 6;
        blacklistListBounds = new ModernMainLayout.Rect(x, y, width, listPanelHeight(nameListSize(NameListKind.BLACKLIST)));
        return blacklistListBounds.bottom() + 4;
    }

    private int pickupContentHeight(int width, int columns) {
        int listHeight = listPanelHeight(ruleCount());
        int total = SPECIAL_TITLE_HEIGHT + 25 + listHeight + 6;
        if (selectedRule() == null) {
            return total;
        }
        int inputColumns = width >= 430 ? 2 : 1;
        int inputWidth = Math.max(1, (width - ROW_GAP * Math.max(0, inputColumns - 1)) / inputColumns);
        int inputHeight = inputWidth < 210 ? 48 : ROW_HEIGHT;
        int inputRows = (RuleInputKey.values().length + inputColumns - 1) / inputColumns;
        return total + SPECIAL_TITLE_HEIGHT + 25 + 24 + inputRows * (inputHeight + ROW_GAP) + 4;
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
        int inputHeight = inputWidth < 210 ? 48 : ROW_HEIGHT;
        RuleInputKey[] keys = RuleInputKey.values();
        for (int index = 0; index < keys.length; index++) {
            int row = index / inputColumns;
            int column = index % inputColumns;
            ruleInputBounds.put(keys[index], new ModernMainLayout.Rect(x + column * (inputWidth + ROW_GAP),
                    y + row * (inputHeight + ROW_GAP), inputWidth, inputHeight));
        }
        int rows = (keys.length + inputColumns - 1) / inputColumns;
        return y + rows * (inputHeight + ROW_GAP) + 4;
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
        int y = contentClipBounds.y + 3 - scrollOffset;
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
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth("gui.modern.killaura.u184") + 5), y + 1,
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
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth("gui.modern.killaura.u194") + 5), y + 1,
                "gui.modern.killaura.u195", mouseX, mouseY);
        drawInput(nameEntryField, nameEntryBounds, "gui.modern.killaura.u196", true, mouseX, mouseY);
        drawActionButton(scanNamesBounds, "gui.modern.killaura.u197", true, mouseX, mouseY, false);
        drawActionButton(addWhitelistBounds, "gui.modern.killaura.u198", hasNameEntry(), mouseX, mouseY, false);
        drawActionButton(addBlacklistBounds, "gui.modern.killaura.u199", hasNameEntry(), mouseX, mouseY, true);
        drawNameListPanel("gui.modern.killaura.u200", nearbyNames, nearbyListBounds, NameListKind.NEARBY, mouseX, mouseY);
        if (whitelistListBounds != null) {
            drawNameListPanel("gui.modern.killaura.u201", KillAuraHandler.nameWhitelist, whitelistListBounds, NameListKind.WHITELIST, mouseX,
                    mouseY);
        }
        if (blacklistListBounds != null) {
            drawNameListPanel("gui.modern.killaura.u202", KillAuraHandler.nameBlacklist, blacklistListBounds, NameListKind.BLACKLIST,
                    mouseX, mouseY);
        }
    }

    private void drawNameListPanel(String title, List<String> values, ModernMainLayout.Rect bounds, NameListKind kind,
            int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 5, 0xFF111A22,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, title + "  " + (values == null ? 0 : values.size()), bounds.x + 8,
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
            boolean selected = kind == NameListKind.WHITELIST && i == selectedWhitelistIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? 0xFF2B3B47 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            int actionWidth = kind == NameListKind.WHITELIST ? 58 : kind == NameListKind.NEARBY ? 0 : 21;
            int textRight = row.right() - actionWidth - 4;
            ModernUiRenderer.drawText(fontRenderer, (i + 1) + ". " + value, row.x + 7,
                    row.y + (row.height - fontRenderer.FONT_HEIGHT) / 2, ModernUiRenderer.TEXT,
                    Math.max(24, textRight - row.x - 10));
            ModernMainLayout.Rect remove = kind == NameListKind.NEARBY ? null
                    : new ModernMainLayout.Rect(row.right() - 18, row.y + 3, 15, 19);
            ModernMainLayout.Rect moveUp = null;
            ModernMainLayout.Rect moveDown = null;
            if (kind == NameListKind.WHITELIST) {
                moveUp = new ModernMainLayout.Rect(row.right() - 54, row.y + 3, 15, 19);
                moveDown = new ModernMainLayout.Rect(row.right() - 36, row.y + 3, 15, 19);
                drawTinyButton(moveUp, "↑", i > 0, mouseX, mouseY);
                drawTinyButton(moveDown, "↓", i + 1 < values.size(), mouseX, mouseY);
            }
            if (remove != null) {
                drawTinyButton(remove, "×", true, mouseX, mouseY);
            }
            nameRowHits.add(new NameRowHit(kind, i, row, moveUp, moveDown, remove));
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
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 4,
                bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                enabled ? hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT,
                bounds.width - 7);
    }

    private void drawPickupContent(int x, int y, int width, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u204", x, y + 2, ModernUiRenderer.SUBTLE_TEXT, width - 16);
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth("gui.modern.killaura.u204") + 5), y + 1,
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
        drawInfoIcon(x + Math.min(Math.max(0, width - 12), fontRenderer.getStringWidth("gui.modern.killaura.u208") + 5), editorY + 1,
                "gui.modern.killaura.u209", mouseX, mouseY);
        drawRuleToggle(rule, mouseX, mouseY);
        drawRuleRarityOptions(rule, mouseX, mouseY);
        for (RuleInputKey key : RuleInputKey.values()) {
            ModernMainLayout.Rect bounds = ruleInputBounds.get(key);
            GuiTextField input = ruleInputs.get(key);
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
        if (KillAuraHandler.huntPickupRules == null || KillAuraHandler.huntPickupRules.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u210", ruleListBounds.x + 8,
                    ruleListBounds.y + 29, ModernUiRenderer.MUTED_TEXT, ruleListBounds.width - 16);
            return;
        }
        int cardY = ruleListBounds.y + 20;
        int maxRows = Math.max(1, (ruleListBounds.height - 24) / (LIST_CARD_HEIGHT + LIST_CARD_GAP));
        int displayed = 0;
        for (int index = 0; index < KillAuraHandler.huntPickupRules.size() && displayed < maxRows; index++) {
            HuntPickupRule rule = KillAuraHandler.huntPickupRules.get(index);
            if (rule == null) {
                continue;
            }
            ModernMainLayout.Rect card = new ModernMainLayout.Rect(ruleListBounds.x + 7, cardY,
                    Math.max(1, ruleListBounds.width - 14), LIST_CARD_HEIGHT);
            boolean selected = index == selectedRuleIndex;
            boolean hovered = card.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(card.x, card.y, card.width, card.height, 4,
                    selected ? 0xFF2B3B47 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.BORDER_SUBTLE);
            String mode = KillAuraHandler.HUNT_PICKUP_RULE_MODE_BLOCK.equalsIgnoreCase(rule.mode) ? "gui.modern.killaura.u211" : "gui.modern.killaura.u212";
            String state = rule.enabled ? "gui.modern.killaura.u213" : "gui.modern.killaura.u214";
            String summary = t("gui.modern.killaura.fmt.rule_row", safe(rule.name), t(mode), t(state),
                    String.valueOf(rule.priority));
            ModernUiRenderer.drawText(fontRenderer, summary, card.x + 8,
                    card.y + (card.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, card.width - 16);
            ruleCardHits.add(new RuleCardHit(index, card));
            cardY += LIST_CARD_HEIGHT + LIST_CARD_GAP;
            displayed++;
        }
        if (KillAuraHandler.huntPickupRules.size() > displayed) {
            String extra = t("gui.modern.killaura.fmt.more",
                    String.valueOf(KillAuraHandler.huntPickupRules.size() - displayed));
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
        ModernUiRenderer.drawSubtlePanel(ruleModeBounds.x, ruleModeBounds.y, ruleModeBounds.width, ruleModeBounds.height,
                4, block ? 0xFF48322F : 0xFF203C34, block ? 0xFFD98168 : 0xFF62CBA0);
        ModernUiRenderer.drawText(fontRenderer, block ? "gui.modern.killaura.u216" : "gui.modern.killaura.u217", ruleModeBounds.x + 7,
                ruleModeBounds.y + (ruleModeBounds.height - fontRenderer.FONT_HEIGHT) / 2,
                hoverMode ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, ruleModeBounds.width - 14);
    }

    private void drawRuleRarityOptions(HuntPickupRule rule, int mouseX, int mouseY) {
        for (Map.Entry<String, ModernMainLayout.Rect> entry : rarityBounds.entrySet()) {
            String token = entry.getKey();
            ModernMainLayout.Rect bounds = entry.getValue();
            boolean selected = rule.rarityFilters != null && rule.rarityFilters.contains(token);
            boolean hovered = bounds.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                    selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : 0xFF111A22,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(fontRenderer, KillAuraHandler.getHuntPickupRarityDisplayName(token), bounds.x + 6,
                    bounds.y + (bounds.height - fontRenderer.FONT_HEIGHT) / 2,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, bounds.width - 10);
        }
    }

    private void drawRuleInput(RuleInputKey key, GuiTextField input, ModernMainLayout.Rect row, int mouseX, int mouseY) {
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

    private void drawHuntScoreContent(int x, int y, int width, int mouseX, int mouseY) {
        if (huntScoreBounds == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(huntScoreBounds.x, huntScoreBounds.y, huntScoreBounds.width,
                huntScoreBounds.height, 5, 0xFF111A22, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.killaura.u218", huntScoreBounds.x + 8, huntScoreBounds.y + 5,
                ModernUiRenderer.MUTED_TEXT, huntScoreBounds.width - 18);
        drawInfoIcon(huntScoreBounds.x + Math.min(Math.max(0, huntScoreBounds.width - 14),
                fontRenderer.getStringWidth("gui.modern.killaura.u218") + 13), huntScoreBounds.y + 4,
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
                KillAuraHandler.enabled, headerToggleBounds.contains(mouseX, mouseY));
        ModernUiRenderer.drawText(fontRenderer, KillAuraHandler.enabled ? "gui.modern.killaura.u180"
                : "gui.modern.killaura.u181", panelBounds.x + 18, y + 6,
                KillAuraHandler.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
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
        int labelWidth = fontRenderer.getStringWidth(com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label) ? com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n.tr(label) + "  " + com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.bindingText() : label);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + Math.max(4, (bounds.width - labelWidth) / 2),
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
                    State.setBoolean(field.key, !State.getBoolean(field.key));
                    enforceTargetSelection();
                    draftDirty = true;
                    return true;
                }
                if (field.type == FieldType.CHOICE) {
                    String selected = findChoiceAt(field, mouseX, mouseY);
                    if (selected != null) {
                        State.setText(field.key, selected);
                        if ("attackMode".equals(field.key) && KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(selected)) {
                            KillAuraHandler.rotateToTarget = false;
                            KillAuraHandler.smoothRotation = false;
                            KillAuraHandler.rotateOnlyOnAttack = false;
                            KillAuraHandler.relockOnlyWhenNoCrosshairTarget = false;
                        }
                        if ("huntMode".equals(field.key)) {
                            KillAuraHandler.setHuntMode(selected);
                        }
                        draftDirty = true;
                        refreshAfterStateChange();
                    }
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
        if (contains(addWhitelistBounds, mouseX, mouseY) && hasNameEntry()) {
            List<String> whitelist = listFor(NameListKind.WHITELIST);
            addName(whitelist, nameEntryField.getText());
            draftDirty = true;
            selectedWhitelistIndex = Math.max(0, whitelist.size() - 1);
            nameEntryField.setText("");
            refreshAfterStateChange();
            return true;
        }
        if (contains(addBlacklistBounds, mouseX, mouseY) && hasNameEntry()) {
            addName(listFor(NameListKind.BLACKLIST), nameEntryField.getText());
            draftDirty = true;
            nameEntryField.setText("");
            refreshAfterStateChange();
            return true;
        }
        for (NameRowHit hit : new ArrayList<>(nameRowHits)) {
            if (hit.rowBounds == null || !hit.rowBounds.contains(mouseX, mouseY)) {
                continue;
            }
            List<String> values = listFor(hit.kind);
            if (values == null || hit.index < 0 || hit.index >= values.size()) {
                return true;
            }
            if (hit.kind == NameListKind.NEARBY) {
                nameEntryField.setText(values.get(hit.index));
                return true;
            }
            if (contains(hit.removeBounds, mouseX, mouseY)) {
                values.remove(hit.index);
                draftDirty = true;
                if (hit.kind == NameListKind.WHITELIST) {
                    selectedWhitelistIndex = Math.min(selectedWhitelistIndex, values.size() - 1);
                }
                refreshAfterStateChange();
                return true;
            }
            if (hit.kind == NameListKind.WHITELIST && contains(hit.moveUpBounds, mouseX, mouseY) && hit.index > 0) {
                Collections.swap(values, hit.index, hit.index - 1);
                draftDirty = true;
                selectedWhitelistIndex = hit.index - 1;
                refreshAfterStateChange();
                return true;
            }
            if (hit.kind == NameListKind.WHITELIST && contains(hit.moveDownBounds, mouseX, mouseY)
                    && hit.index + 1 < values.size()) {
                Collections.swap(values, hit.index, hit.index + 1);
                draftDirty = true;
                selectedWhitelistIndex = hit.index + 1;
                refreshAfterStateChange();
                return true;
            }
            if (hit.kind == NameListKind.WHITELIST) {
                selectedWhitelistIndex = hit.index;
            }
            return true;
        }
        return true;
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
            if (KillAuraHandler.huntPickupRules == null) {
                KillAuraHandler.huntPickupRules = new ArrayList<>();
            }
            KillAuraHandler.huntPickupRules.add(rule);
            selectedRuleIndex = KillAuraHandler.huntPickupRules.size() - 1;
            draftDirty = true;
            syncRuleInputs();
            showStatus("gui.modern.killaura.u233");
            return true;
        }
        if (contains(deleteRuleBounds, mouseX, mouseY) && selectedRule() != null) {
            KillAuraHandler.huntPickupRules.remove(selectedRuleIndex);
            draftDirty = true;
            selectedRuleIndex = Math.min(selectedRuleIndex, KillAuraHandler.huntPickupRules.size() - 1);
            syncRuleInputs();
            showStatus("gui.modern.killaura.u234");
            return true;
        }
        for (RuleCardHit hit : ruleCardHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                applyRuleInputs();
                selectedRuleIndex = hit.index;
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
            for (Map.Entry<RuleInputKey, ModernMainLayout.Rect> entry : ruleInputBounds.entrySet()) {
                if (entry.getValue().contains(mouseX, mouseY)) {
                    GuiTextField input = ruleInputs.get(entry.getKey());
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

    @Override
    public void save() {
        saveDraft();
    }

    private void saveDraft() {
        applyEditableValues();
        KillAuraHandler.INSTANCE.resetRuntimeState();
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
        boolean enabled = KillAuraHandler.enabled;
        KillAuraHandler.rotateToTarget = true;
        KillAuraHandler.smoothRotation = true;
        KillAuraHandler.setSmoothMaxTurnStepSpec(KillAuraHandler.DEFAULT_SMOOTH_MAX_TURN_STEP_SPEC);
        KillAuraHandler.rotateOnlyOnAttack = true;
        KillAuraHandler.relockOnlyWhenNoCrosshairTarget = true;
        KillAuraHandler.onlyAttackWhenLookingAtTarget = true;
        KillAuraHandler.requireLineOfSight = true;
        KillAuraHandler.throughWallAttack = false;
        KillAuraHandler.targetHostile = true;
        KillAuraHandler.targetPassive = false;
        KillAuraHandler.targetPlayers = false;
        KillAuraHandler.targetEnderCrystal = false;
        KillAuraHandler.onlyWeapon = false;
        KillAuraHandler.aimOnlyMode = false;
        KillAuraHandler.focusSingleTarget = true;
        KillAuraHandler.ignoreInvisible = true;
        KillAuraHandler.enableNoCollision = true;
        KillAuraHandler.enableAntiKnockback = true;
        KillAuraHandler.teleportAttackPacketLimitPerTick = KillAuraHandler.DEFAULT_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK;
        KillAuraHandler.teleportStepDistance = KillAuraHandler.DEFAULT_TELEPORT_STEP_DISTANCE;
        KillAuraHandler.teleportStationAttackRadius = KillAuraHandler.DEFAULT_TELEPORT_STATION_ATTACK_RADIUS;
        KillAuraHandler.disableOnDisconnect = true;
        KillAuraHandler.enableFullBrightVision = false;
        KillAuraHandler.fullBrightGamma = 1000.0F;
        KillAuraHandler.attackMode = KillAuraHandler.DEFAULT_ATTACK_MODE;
        KillAuraHandler.attackSequenceName = "";
        KillAuraHandler.setAttackSequenceDelayTicksSpec(KillAuraHandler.DEFAULT_ATTACK_SEQUENCE_DELAY_TICKS_SPEC);
        KillAuraHandler.setAimYawOffsetSpec(KillAuraHandler.DEFAULT_AIM_YAW_OFFSET_SPEC);
        KillAuraHandler.setAimPitchOffsetSpec(KillAuraHandler.DEFAULT_AIM_PITCH_OFFSET_SPEC);
        KillAuraHandler.setHuntMode(KillAuraHandler.HUNT_MODE_APPROACH);
        KillAuraHandler.huntPickupItemsEnabled = false;
        KillAuraHandler.huntPickupRules = new ArrayList<>();
        KillAuraHandler.visualizeHuntRadius = false;
        KillAuraHandler.huntRadius = 8.0F;
        KillAuraHandler.huntFixedDistance = 4.2F;
        KillAuraHandler.huntUpRange = KillAuraHandler.DEFAULT_HUNT_UP_RANGE;
        KillAuraHandler.huntDownRange = KillAuraHandler.DEFAULT_HUNT_DOWN_RANGE;
        KillAuraHandler.huntOrbitEnabled = false;
        KillAuraHandler.huntJumpOrbitEnabled = true;
        KillAuraHandler.huntOrbitSamplePoints = KillAuraHandler.DEFAULT_HUNT_ORBIT_SAMPLE_POINTS;
        KillAuraHandler.enableNameWhitelist = false;
        KillAuraHandler.enableNameBlacklist = false;
        KillAuraHandler.nameWhitelist = new ArrayList<>();
        KillAuraHandler.nameBlacklist = new ArrayList<>();
        KillAuraHandler.nearbyEntityScanRange = 10.0F;
        KillAuraHandler.attackRange = 4.2F;
        KillAuraHandler.resetHuntScoreWeights();
        KillAuraHandler.minAttackStrength = 0.92F;
        KillAuraHandler.minTurnSpeed = 4.0F;
        KillAuraHandler.maxTurnSpeed = 18.0F;
        KillAuraHandler.minAttackIntervalTicks = 2;
        KillAuraHandler.targetsPerAttack = 1;
        KillAuraHandler.noDamageAttackLimit = KillAuraHandler.DEFAULT_NO_DAMAGE_ATTACK_LIMIT;
        KillAuraHandler.enabled = enabled;
        KillAuraHandler.INSTANCE.resetRuntimeState();
        selectedWhitelistIndex = -1;
        selectedRuleIndex = -1;
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
        for (RuleInputKey key : RuleInputKey.values()) {
            GuiTextField input = ruleInputs.get(key);
            if (input == null) {
                continue;
            }
            input.setText(rule == null ? "" : ruleInputValue(rule, key));
        }
    }

    private void applyRuleInputs() {
        HuntPickupRule rule = selectedRule();
        if (rule == null) {
            return;
        }
        rule.name = valueOf(RuleInputKey.NAME).trim();
        rule.category = valueOf(RuleInputKey.CATEGORY).trim();
        rule.nameKeyword = valueOf(RuleInputKey.NAME_KEYWORD).trim();
        rule.itemIdKeyword = valueOf(RuleInputKey.ITEM_ID_KEYWORD).trim();
        rule.requiredNbtTags = parseCommaList(valueOf(RuleInputKey.NBT_TAGS));
        rule.itemFilterExpressions = parseLiteralLines(valueOf(RuleInputKey.EXPRESSIONS));
        rule.maxDistance = parseFloat(valueOf(RuleInputKey.MAX_DISTANCE), rule.maxDistance, 0.0F, 100.0F);
        rule.priority = parseInt(valueOf(RuleInputKey.PRIORITY), rule.priority, -999, 999);
    }

    private String valueOf(RuleInputKey key) {
        GuiTextField input = ruleInputs.get(key);
        return input == null ? "" : safe(input.getText());
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
        nearbyNames.addAll(KillAuraHandler.getNearbyEntityNames(KillAuraHandler.nearbyEntityScanRange));
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

    private List<String> listFor(NameListKind kind) {
        if (kind == NameListKind.NEARBY) {
            return nearbyNames;
        }
        if (kind == NameListKind.WHITELIST) {
            if (KillAuraHandler.nameWhitelist == null) {
                KillAuraHandler.nameWhitelist = new ArrayList<>();
            }
            return KillAuraHandler.nameWhitelist;
        }
        if (KillAuraHandler.nameBlacklist == null) {
            KillAuraHandler.nameBlacklist = new ArrayList<>();
        }
        return KillAuraHandler.nameBlacklist;
    }

    private int nameListSize(NameListKind kind) {
        List<String> values = listFor(kind);
        return values == null ? 0 : values.size();
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
        return KillAuraHandler.huntPickupRules == null ? 0 : KillAuraHandler.huntPickupRules.size();
    }

    private HuntPickupRule selectedRule() {
        if (KillAuraHandler.huntPickupRules == null || selectedRuleIndex < 0
                || selectedRuleIndex >= KillAuraHandler.huntPickupRules.size()) {
            return null;
        }
        return KillAuraHandler.huntPickupRules.get(selectedRuleIndex);
    }

    private KillAuraPreset selectedPreset() {
        if (selectedPresetIndex < 0 || selectedPresetIndex >= presetCards.size()) {
            return null;
        }
        return presetCards.get(selectedPresetIndex);
    }

    private interface InputVisitor {
        void visit(GuiTextField input);
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
        for (GuiTextField input : ruleInputs.values()) {
            if (input != null) {
                visitor.visit(input);
            }
        }
    }

    private boolean keyTypedInInputs(char typedChar, int keyCode) {
        final boolean[] handled = new boolean[] { false };
        forEachInput(new InputVisitor() {
            @Override
            public void visit(GuiTextField input) {
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
            public void visit(GuiTextField input) {
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
            public void visit(GuiTextField input) {
                input.setFocused(false);
            }
        });
    }

    private Condition condition(final String name) {
        return new Condition() {
            @Override
            public boolean matches() {
                if ("notAimOnly".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode;
                }
                if ("sequenceMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.attackMode);
                }
                if ("delayMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.attackMode)
                            || KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(KillAuraHandler.attackMode);
                }
                if ("teleportMode".equals(name)) {
                    return KillAuraHandler.ATTACK_MODE_TELEPORT.equalsIgnoreCase(KillAuraHandler.attackMode);
                }
                if ("weaponApplicable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode
                            && !KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.attackMode);
                }
                if ("rotationAvailable".equals(name)) {
                    return !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.attackMode)
                            || KillAuraHandler.aimOnlyMode;
                }
                if ("smoothRotation".equals(name)) {
                    return KillAuraHandler.smoothRotation && (condition("rotationAvailable").matches());
                }
                if ("lookAttackAvailable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode
                            && !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.attackMode)
                            && KillAuraHandler.rotateToTarget
                            && !KillAuraHandler.ATTACK_MODE_MOUSE_CLICK.equalsIgnoreCase(KillAuraHandler.attackMode);
                }
                if ("relockAvailable".equals(name)) {
                    return KillAuraHandler.aimOnlyMode || KillAuraHandler.rotateToTarget;
                }
                if ("rotateOnlyAvailable".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode
                            && !KillAuraHandler.ATTACK_MODE_PACKET.equalsIgnoreCase(KillAuraHandler.attackMode)
                            && KillAuraHandler.rotateToTarget;
                }
                if ("huntEnabled".equals(name)) {
                    return KillAuraHandler.isHuntEnabled();
                }
                if ("fixedHunt".equals(name)) {
                    return KillAuraHandler.isHuntFixedDistanceMode();
                }
                if ("orbitEnabled".equals(name)) {
                    return KillAuraHandler.isHuntFixedDistanceMode() && KillAuraHandler.huntOrbitEnabled;
                }
                if ("fullBright".equals(name)) {
                    return KillAuraHandler.enableFullBrightVision;
                }
                if ("normalAttackStats".equals(name)) {
                    return !KillAuraHandler.aimOnlyMode
                            && !KillAuraHandler.ATTACK_MODE_SEQUENCE.equalsIgnoreCase(KillAuraHandler.attackMode);
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

    private void showStatus(String message) {
        statusMessage = safe(message);
        statusMessageUntil = System.currentTimeMillis() + 2600L;
    }

    private void enforceTargetSelection() {
        if (!KillAuraHandler.targetHostile && !KillAuraHandler.targetPassive && !KillAuraHandler.targetPlayers
                && !KillAuraHandler.targetEnderCrystal) {
            KillAuraHandler.targetHostile = true;
        }
    }

    /** Defensive snapshot of the full persisted Kill Aura state. */
    private static final class State {
        private final boolean enabled;
        private final KillAuraPreset values;
        private final List<String> whitelist;
        private final List<String> blacklist;
        private final List<HuntPickupRule> pickupRules;
        private final List<KillAuraPreset> presets;

        private State() {
            this.enabled = KillAuraHandler.enabled;
            this.values = captureValues();
            this.whitelist = copyStrings(KillAuraHandler.nameWhitelist);
            this.blacklist = copyStrings(KillAuraHandler.nameBlacklist);
            this.pickupRules = copyRules(KillAuraHandler.huntPickupRules);
            this.presets = copyPresets(KillAuraHandler.getPresetSnapshots());
        }

        private State(State source) {
            this.enabled = source.enabled;
            this.values = new KillAuraPreset(source.values);
            this.whitelist = copyStrings(source.whitelist);
            this.blacklist = copyStrings(source.blacklist);
            this.pickupRules = copyRules(source.pickupRules);
            this.presets = copyPresets(source.presets);
        }

        private void apply() {
            applyValues(values);
            KillAuraHandler.enabled = enabled;
            KillAuraHandler.nameWhitelist = copyStrings(whitelist);
            KillAuraHandler.nameBlacklist = copyStrings(blacklist);
            KillAuraHandler.huntPickupRules = copyRules(pickupRules);
            KillAuraHandler.presets.clear();
            KillAuraHandler.presets.addAll(copyPresets(presets));
            KillAuraHandler.INSTANCE.resetRuntimeState();
        }

        private static KillAuraPreset captureValues() {
            KillAuraPreset value = new KillAuraPreset();
            value.rotateToTarget = KillAuraHandler.rotateToTarget;
            value.smoothRotation = KillAuraHandler.smoothRotation;
            value.smoothMaxTurnStep = KillAuraHandler.smoothMaxTurnStep;
            value.smoothMaxTurnStepSpec = KillAuraHandler.getSmoothMaxTurnStepSpec();
            value.rotateOnlyOnAttack = KillAuraHandler.rotateOnlyOnAttack;
            value.relockOnlyWhenNoCrosshairTarget = KillAuraHandler.relockOnlyWhenNoCrosshairTarget;
            value.onlyAttackWhenLookingAtTarget = KillAuraHandler.onlyAttackWhenLookingAtTarget;
            value.requireLineOfSight = KillAuraHandler.requireLineOfSight;
            value.throughWallAttack = KillAuraHandler.throughWallAttack;
            value.targetHostile = KillAuraHandler.targetHostile;
            value.targetPassive = KillAuraHandler.targetPassive;
            value.targetPlayers = KillAuraHandler.targetPlayers;
            value.targetEnderCrystal = KillAuraHandler.targetEnderCrystal;
            value.onlyWeapon = KillAuraHandler.onlyWeapon;
            value.aimOnlyMode = KillAuraHandler.aimOnlyMode;
            value.focusSingleTarget = KillAuraHandler.focusSingleTarget;
            value.ignoreInvisible = KillAuraHandler.ignoreInvisible;
            value.enableNoCollision = KillAuraHandler.enableNoCollision;
            value.enableAntiKnockback = KillAuraHandler.enableAntiKnockback;
            value.teleportAttackPacketLimitPerTick = KillAuraHandler.teleportAttackPacketLimitPerTick;
            value.teleportStepDistance = KillAuraHandler.teleportStepDistance;
            value.teleportStationAttackRadius = KillAuraHandler.teleportStationAttackRadius;
            value.disableOnDisconnect = KillAuraHandler.disableOnDisconnect;
            value.enableFullBrightVision = KillAuraHandler.enableFullBrightVision;
            value.fullBrightGamma = KillAuraHandler.fullBrightGamma;
            value.attackMode = KillAuraHandler.attackMode;
            value.attackSequenceName = KillAuraHandler.attackSequenceName;
            value.attackSequenceDelayTicks = KillAuraHandler.attackSequenceDelayTicks;
            value.attackSequenceDelayTicksSpec = KillAuraHandler.getAttackSequenceDelayTicksSpec();
            value.aimYawOffset = KillAuraHandler.aimYawOffset;
            value.aimYawOffsetSpec = KillAuraHandler.getAimYawOffsetSpec();
            value.aimPitchOffset = KillAuraHandler.aimPitchOffset;
            value.aimPitchOffsetSpec = KillAuraHandler.getAimPitchOffsetSpec();
            value.huntMode = KillAuraHandler.huntMode;
            value.huntPickupItemsEnabled = KillAuraHandler.huntPickupItemsEnabled;
            value.huntPickupRules = copyRules(KillAuraHandler.huntPickupRules);
            value.visualizeHuntRadius = KillAuraHandler.visualizeHuntRadius;
            value.huntRadius = KillAuraHandler.huntRadius;
            value.huntFixedDistance = KillAuraHandler.huntFixedDistance;
            value.huntUpRange = KillAuraHandler.huntUpRange;
            value.huntDownRange = KillAuraHandler.huntDownRange;
            value.huntOrbitEnabled = KillAuraHandler.huntOrbitEnabled;
            value.huntJumpOrbitEnabled = KillAuraHandler.huntJumpOrbitEnabled;
            value.huntOrbitSamplePoints = KillAuraHandler.huntOrbitSamplePoints;
            value.enableNameWhitelist = KillAuraHandler.enableNameWhitelist;
            value.enableNameBlacklist = KillAuraHandler.enableNameBlacklist;
            value.nameWhitelist = copyStrings(KillAuraHandler.nameWhitelist);
            value.nameBlacklist = copyStrings(KillAuraHandler.nameBlacklist);
            value.nearbyEntityScanRange = KillAuraHandler.nearbyEntityScanRange;
            value.attackRange = KillAuraHandler.attackRange;
            value.huntScoreRadiusWeight = KillAuraHandler.huntScoreRadiusWeight;
            value.huntScorePlayerDistanceWeight = KillAuraHandler.huntScorePlayerDistanceWeight;
            value.huntScorePlayerPlaneWeight = KillAuraHandler.huntScorePlayerPlaneWeight;
            value.huntScoreTargetHeightWeight = KillAuraHandler.huntScoreTargetHeightWeight;
            value.huntScoreAttackRangeWeight = KillAuraHandler.huntScoreAttackRangeWeight;
            value.huntScoreVisibilityWeight = KillAuraHandler.huntScoreVisibilityWeight;
            value.huntScoreOpennessWeight = KillAuraHandler.huntScoreOpennessWeight;
            value.minAttackStrength = KillAuraHandler.minAttackStrength;
            value.minTurnSpeed = KillAuraHandler.minTurnSpeed;
            value.maxTurnSpeed = KillAuraHandler.maxTurnSpeed;
            value.minAttackIntervalTicks = KillAuraHandler.minAttackIntervalTicks;
            value.targetsPerAttack = KillAuraHandler.targetsPerAttack;
            value.noDamageAttackLimit = KillAuraHandler.noDamageAttackLimit;
            return value;
        }

        private static void applyValues(KillAuraPreset value) {
            if (value == null) {
                return;
            }
            KillAuraHandler.rotateToTarget = value.rotateToTarget;
            KillAuraHandler.smoothRotation = value.smoothRotation;
            KillAuraHandler.setSmoothMaxTurnStepSpec(value.smoothMaxTurnStepSpec);
            KillAuraHandler.rotateOnlyOnAttack = value.rotateOnlyOnAttack;
            KillAuraHandler.relockOnlyWhenNoCrosshairTarget = value.relockOnlyWhenNoCrosshairTarget;
            KillAuraHandler.onlyAttackWhenLookingAtTarget = value.onlyAttackWhenLookingAtTarget;
            KillAuraHandler.requireLineOfSight = value.requireLineOfSight;
            KillAuraHandler.throughWallAttack = value.throughWallAttack;
            KillAuraHandler.targetHostile = value.targetHostile;
            KillAuraHandler.targetPassive = value.targetPassive;
            KillAuraHandler.targetPlayers = value.targetPlayers;
            KillAuraHandler.targetEnderCrystal = value.targetEnderCrystal;
            KillAuraHandler.onlyWeapon = value.onlyWeapon;
            KillAuraHandler.aimOnlyMode = value.aimOnlyMode;
            KillAuraHandler.focusSingleTarget = value.focusSingleTarget;
            KillAuraHandler.ignoreInvisible = value.ignoreInvisible;
            KillAuraHandler.enableNoCollision = value.enableNoCollision;
            KillAuraHandler.enableAntiKnockback = value.enableAntiKnockback;
            KillAuraHandler.teleportAttackPacketLimitPerTick = value.teleportAttackPacketLimitPerTick;
            KillAuraHandler.teleportStepDistance = value.teleportStepDistance;
            KillAuraHandler.teleportStationAttackRadius = value.teleportStationAttackRadius;
            KillAuraHandler.disableOnDisconnect = value.disableOnDisconnect;
            KillAuraHandler.enableFullBrightVision = value.enableFullBrightVision;
            KillAuraHandler.fullBrightGamma = value.fullBrightGamma;
            KillAuraHandler.attackMode = value.attackMode;
            KillAuraHandler.attackSequenceName = value.attackSequenceName;
            KillAuraHandler.setAttackSequenceDelayTicksSpec(value.attackSequenceDelayTicksSpec);
            KillAuraHandler.setAimYawOffsetSpec(value.aimYawOffsetSpec);
            KillAuraHandler.setAimPitchOffsetSpec(value.aimPitchOffsetSpec);
            KillAuraHandler.setHuntMode(value.huntMode);
            KillAuraHandler.huntPickupItemsEnabled = value.huntPickupItemsEnabled;
            KillAuraHandler.huntPickupRules = copyRules(value.huntPickupRules);
            KillAuraHandler.visualizeHuntRadius = value.visualizeHuntRadius;
            KillAuraHandler.huntRadius = value.huntRadius;
            KillAuraHandler.huntFixedDistance = value.huntFixedDistance;
            KillAuraHandler.huntUpRange = value.huntUpRange;
            KillAuraHandler.huntDownRange = value.huntDownRange;
            KillAuraHandler.huntOrbitEnabled = value.huntOrbitEnabled;
            KillAuraHandler.huntJumpOrbitEnabled = value.huntJumpOrbitEnabled;
            KillAuraHandler.huntOrbitSamplePoints = value.huntOrbitSamplePoints;
            KillAuraHandler.enableNameWhitelist = value.enableNameWhitelist;
            KillAuraHandler.enableNameBlacklist = value.enableNameBlacklist;
            KillAuraHandler.nearbyEntityScanRange = value.nearbyEntityScanRange;
            KillAuraHandler.attackRange = value.attackRange;
            KillAuraHandler.huntScoreRadiusWeight = value.huntScoreRadiusWeight;
            KillAuraHandler.huntScorePlayerDistanceWeight = value.huntScorePlayerDistanceWeight;
            KillAuraHandler.huntScorePlayerPlaneWeight = value.huntScorePlayerPlaneWeight;
            KillAuraHandler.huntScoreTargetHeightWeight = value.huntScoreTargetHeightWeight;
            KillAuraHandler.huntScoreAttackRangeWeight = value.huntScoreAttackRangeWeight;
            KillAuraHandler.huntScoreVisibilityWeight = value.huntScoreVisibilityWeight;
            KillAuraHandler.huntScoreOpennessWeight = value.huntScoreOpennessWeight;
            KillAuraHandler.minAttackStrength = value.minAttackStrength;
            KillAuraHandler.minTurnSpeed = value.minTurnSpeed;
            KillAuraHandler.maxTurnSpeed = value.maxTurnSpeed;
            KillAuraHandler.minAttackIntervalTicks = value.minAttackIntervalTicks;
            KillAuraHandler.targetsPerAttack = value.targetsPerAttack;
            KillAuraHandler.noDamageAttackLimit = value.noDamageAttackLimit;
        }

        private static boolean getBoolean(String key) {
            if ("enabled".equals(key)) return KillAuraHandler.enabled;
            if ("rotateToTarget".equals(key)) return KillAuraHandler.rotateToTarget;
            if ("smoothRotation".equals(key)) return KillAuraHandler.smoothRotation;
            if ("rotateOnlyOnAttack".equals(key)) return KillAuraHandler.rotateOnlyOnAttack;
            if ("relockOnlyWhenNoCrosshairTarget".equals(key)) return KillAuraHandler.relockOnlyWhenNoCrosshairTarget;
            if ("onlyAttackWhenLookingAtTarget".equals(key)) return KillAuraHandler.onlyAttackWhenLookingAtTarget;
            if ("requireLineOfSight".equals(key)) return KillAuraHandler.requireLineOfSight;
            if ("throughWallAttack".equals(key)) return KillAuraHandler.throughWallAttack;
            if ("targetHostile".equals(key)) return KillAuraHandler.targetHostile;
            if ("targetPassive".equals(key)) return KillAuraHandler.targetPassive;
            if ("targetPlayers".equals(key)) return KillAuraHandler.targetPlayers;
            if ("targetEnderCrystal".equals(key)) return KillAuraHandler.targetEnderCrystal;
            if ("onlyWeapon".equals(key)) return KillAuraHandler.onlyWeapon;
            if ("aimOnlyMode".equals(key)) return KillAuraHandler.aimOnlyMode;
            if ("focusSingleTarget".equals(key)) return KillAuraHandler.focusSingleTarget;
            if ("ignoreInvisible".equals(key)) return KillAuraHandler.ignoreInvisible;
            if ("enableNoCollision".equals(key)) return KillAuraHandler.enableNoCollision;
            if ("enableAntiKnockback".equals(key)) return KillAuraHandler.enableAntiKnockback;
            if ("disableOnDisconnect".equals(key)) return KillAuraHandler.disableOnDisconnect;
            if ("enableFullBrightVision".equals(key)) return KillAuraHandler.enableFullBrightVision;
            if ("huntPickupItemsEnabled".equals(key)) return KillAuraHandler.huntPickupItemsEnabled;
            if ("visualizeHuntRadius".equals(key)) return KillAuraHandler.visualizeHuntRadius;
            if ("huntOrbitEnabled".equals(key)) return KillAuraHandler.huntOrbitEnabled;
            if ("huntJumpOrbitEnabled".equals(key)) return KillAuraHandler.huntJumpOrbitEnabled;
            if ("enableNameWhitelist".equals(key)) return KillAuraHandler.enableNameWhitelist;
            if ("enableNameBlacklist".equals(key)) return KillAuraHandler.enableNameBlacklist;
            return false;
        }

        private static void setBoolean(String key, boolean value) {
            if ("enabled".equals(key)) {
                KillAuraHandler.enabled = value;
            } else if ("rotateToTarget".equals(key)) {
                KillAuraHandler.rotateToTarget = value;
                if (!value) {
                    KillAuraHandler.rotateOnlyOnAttack = false;
                    KillAuraHandler.relockOnlyWhenNoCrosshairTarget = false;
                }
            } else if ("smoothRotation".equals(key)) KillAuraHandler.smoothRotation = value;
            else if ("rotateOnlyOnAttack".equals(key)) KillAuraHandler.rotateOnlyOnAttack = value;
            else if ("relockOnlyWhenNoCrosshairTarget".equals(key)) KillAuraHandler.relockOnlyWhenNoCrosshairTarget = value;
            else if ("onlyAttackWhenLookingAtTarget".equals(key)) KillAuraHandler.onlyAttackWhenLookingAtTarget = value;
            else if ("requireLineOfSight".equals(key)) KillAuraHandler.requireLineOfSight = value;
            else if ("throughWallAttack".equals(key)) KillAuraHandler.throughWallAttack = value;
            else if ("targetHostile".equals(key)) KillAuraHandler.targetHostile = value;
            else if ("targetPassive".equals(key)) KillAuraHandler.targetPassive = value;
            else if ("targetPlayers".equals(key)) KillAuraHandler.targetPlayers = value;
            else if ("targetEnderCrystal".equals(key)) KillAuraHandler.targetEnderCrystal = value;
            else if ("onlyWeapon".equals(key)) KillAuraHandler.onlyWeapon = value;
            else if ("aimOnlyMode".equals(key)) {
                KillAuraHandler.aimOnlyMode = value;
                if (value) {
                    KillAuraHandler.attackMode = KillAuraHandler.ATTACK_MODE_SEQUENCE;
                    KillAuraHandler.rotateOnlyOnAttack = false;
                }
            } else if ("focusSingleTarget".equals(key)) KillAuraHandler.focusSingleTarget = value;
            else if ("ignoreInvisible".equals(key)) KillAuraHandler.ignoreInvisible = value;
            else if ("enableNoCollision".equals(key)) KillAuraHandler.enableNoCollision = value;
            else if ("enableAntiKnockback".equals(key)) KillAuraHandler.enableAntiKnockback = value;
            else if ("disableOnDisconnect".equals(key)) KillAuraHandler.disableOnDisconnect = value;
            else if ("enableFullBrightVision".equals(key)) KillAuraHandler.enableFullBrightVision = value;
            else if ("huntPickupItemsEnabled".equals(key)) KillAuraHandler.huntPickupItemsEnabled = value;
            else if ("visualizeHuntRadius".equals(key)) KillAuraHandler.visualizeHuntRadius = value;
            else if ("huntOrbitEnabled".equals(key)) KillAuraHandler.huntOrbitEnabled = value;
            else if ("huntJumpOrbitEnabled".equals(key)) KillAuraHandler.huntJumpOrbitEnabled = value;
            else if ("enableNameWhitelist".equals(key)) KillAuraHandler.enableNameWhitelist = value;
            else if ("enableNameBlacklist".equals(key)) KillAuraHandler.enableNameBlacklist = value;
        }

        private static String getText(String key) {
            if ("attackMode".equals(key)) return safeStatic(KillAuraHandler.attackMode);
            if ("huntMode".equals(key)) return safeStatic(KillAuraHandler.huntMode);
            if ("attackSequenceName".equals(key)) return safeStatic(KillAuraHandler.attackSequenceName);
            if ("smoothMaxTurnStepSpec".equals(key)) return KillAuraHandler.getSmoothMaxTurnStepSpec();
            if ("attackSequenceDelayTicksSpec".equals(key)) return KillAuraHandler.getAttackSequenceDelayTicksSpec();
            if ("aimYawOffsetSpec".equals(key)) return KillAuraHandler.getAimYawOffsetSpec();
            if ("aimPitchOffsetSpec".equals(key)) return KillAuraHandler.getAimPitchOffsetSpec();
            if ("fullBrightGamma".equals(key)) return formatStatic(KillAuraHandler.fullBrightGamma);
            if ("teleportStepDistance".equals(key)) return formatStatic(KillAuraHandler.teleportStepDistance);
            if ("teleportStationAttackRadius".equals(key)) return formatStatic(KillAuraHandler.teleportStationAttackRadius);
            if ("nearbyEntityScanRange".equals(key)) return formatStatic(KillAuraHandler.nearbyEntityScanRange);
            if ("huntRadius".equals(key)) return formatStatic(KillAuraHandler.huntRadius);
            if ("huntFixedDistance".equals(key)) return formatStatic(KillAuraHandler.huntFixedDistance);
            if ("huntUpRange".equals(key)) return formatStatic(KillAuraHandler.huntUpRange);
            if ("huntDownRange".equals(key)) return formatStatic(KillAuraHandler.huntDownRange);
            if ("attackRange".equals(key)) return formatStatic(KillAuraHandler.attackRange);
            if ("minAttackStrength".equals(key)) return formatStatic(KillAuraHandler.minAttackStrength);
            if ("minTurnSpeed".equals(key)) return formatStatic(KillAuraHandler.minTurnSpeed);
            if ("maxTurnSpeed".equals(key)) return formatStatic(KillAuraHandler.maxTurnSpeed);
            if ("huntScoreRadiusWeight".equals(key)) return formatStatic(KillAuraHandler.huntScoreRadiusWeight);
            if ("huntScorePlayerDistanceWeight".equals(key)) return formatStatic(KillAuraHandler.huntScorePlayerDistanceWeight);
            if ("huntScorePlayerPlaneWeight".equals(key)) return formatStatic(KillAuraHandler.huntScorePlayerPlaneWeight);
            if ("huntScoreTargetHeightWeight".equals(key)) return formatStatic(KillAuraHandler.huntScoreTargetHeightWeight);
            if ("huntScoreAttackRangeWeight".equals(key)) return formatStatic(KillAuraHandler.huntScoreAttackRangeWeight);
            if ("huntScoreVisibilityWeight".equals(key)) return formatStatic(KillAuraHandler.huntScoreVisibilityWeight);
            if ("huntScoreOpennessWeight".equals(key)) return formatStatic(KillAuraHandler.huntScoreOpennessWeight);
            if ("huntOrbitSamplePoints".equals(key)) return String.valueOf(KillAuraHandler.huntOrbitSamplePoints);
            if ("teleportAttackPacketLimitPerTick".equals(key)) return String.valueOf(KillAuraHandler.teleportAttackPacketLimitPerTick);
            if ("noDamageAttackLimit".equals(key)) return String.valueOf(KillAuraHandler.noDamageAttackLimit);
            if ("minAttackIntervalTicks".equals(key)) return String.valueOf(KillAuraHandler.minAttackIntervalTicks);
            if ("targetsPerAttack".equals(key)) return String.valueOf(KillAuraHandler.targetsPerAttack);
            return "";
        }

        private static void setText(String key, String text) {
            if ("attackMode".equals(key)) {
                KillAuraHandler.attackMode = safeStatic(text).trim();
            } else if ("huntMode".equals(key)) {
                KillAuraHandler.setHuntMode(text);
            } else if ("attackSequenceName".equals(key)) {
                KillAuraHandler.attackSequenceName = safeStatic(text).trim();
            } else if ("smoothMaxTurnStepSpec".equals(key)) {
                KillAuraHandler.setSmoothMaxTurnStepSpec(text);
            } else if ("attackSequenceDelayTicksSpec".equals(key)) {
                KillAuraHandler.setAttackSequenceDelayTicksSpec(text);
            } else if ("aimYawOffsetSpec".equals(key)) {
                KillAuraHandler.setAimYawOffsetSpec(text);
            } else if ("aimPitchOffsetSpec".equals(key)) {
                KillAuraHandler.setAimPitchOffsetSpec(text);
            } else if ("fullBrightGamma".equals(key)) KillAuraHandler.fullBrightGamma = floatStatic(text, KillAuraHandler.fullBrightGamma, 1, 1000);
            else if ("teleportStepDistance".equals(key)) KillAuraHandler.teleportStepDistance = floatStatic(text,
                    KillAuraHandler.teleportStepDistance, KillAuraHandler.MIN_TELEPORT_STEP_DISTANCE,
                    KillAuraHandler.MAX_TELEPORT_STEP_DISTANCE);
            else if ("teleportStationAttackRadius".equals(key)) KillAuraHandler.teleportStationAttackRadius = floatStatic(
                    text, KillAuraHandler.teleportStationAttackRadius,
                    KillAuraHandler.MIN_TELEPORT_STATION_ATTACK_RADIUS,
                    KillAuraHandler.MAX_TELEPORT_STATION_ATTACK_RADIUS);
            else if ("nearbyEntityScanRange".equals(key)) KillAuraHandler.nearbyEntityScanRange = floatStatic(text, KillAuraHandler.nearbyEntityScanRange, 1, 64);
            else if ("huntRadius".equals(key)) KillAuraHandler.huntRadius = floatStatic(text, KillAuraHandler.huntRadius, KillAuraHandler.attackRange, 100);
            else if ("huntFixedDistance".equals(key)) KillAuraHandler.huntFixedDistance = floatStatic(text, KillAuraHandler.huntFixedDistance, 0.5F, 100);
            else if ("huntUpRange".equals(key)) KillAuraHandler.huntUpRange = floatStatic(text, KillAuraHandler.huntUpRange, 0, 100);
            else if ("huntDownRange".equals(key)) KillAuraHandler.huntDownRange = floatStatic(text, KillAuraHandler.huntDownRange, 0, 100);
            else if ("attackRange".equals(key)) {
                KillAuraHandler.attackRange = floatStatic(text, KillAuraHandler.attackRange, 1, 100);
                KillAuraHandler.huntRadius = Math.max(KillAuraHandler.huntRadius, KillAuraHandler.attackRange);
            } else if ("minAttackStrength".equals(key)) KillAuraHandler.minAttackStrength = floatStatic(text, KillAuraHandler.minAttackStrength, 0, 1);
            else if ("minTurnSpeed".equals(key)) {
                KillAuraHandler.minTurnSpeed = floatStatic(text, KillAuraHandler.minTurnSpeed, 1, 40);
                KillAuraHandler.maxTurnSpeed = Math.max(KillAuraHandler.maxTurnSpeed, KillAuraHandler.minTurnSpeed);
            } else if ("maxTurnSpeed".equals(key)) KillAuraHandler.maxTurnSpeed = floatStatic(text, KillAuraHandler.maxTurnSpeed, KillAuraHandler.minTurnSpeed, 60);
            else if ("huntScoreRadiusWeight".equals(key)) KillAuraHandler.huntScoreRadiusWeight = floatStatic(text, KillAuraHandler.huntScoreRadiusWeight, 0, 100);
            else if ("huntScorePlayerDistanceWeight".equals(key)) KillAuraHandler.huntScorePlayerDistanceWeight = floatStatic(text, KillAuraHandler.huntScorePlayerDistanceWeight, 0, 100);
            else if ("huntScorePlayerPlaneWeight".equals(key)) KillAuraHandler.huntScorePlayerPlaneWeight = floatStatic(text, KillAuraHandler.huntScorePlayerPlaneWeight, 0, 100);
            else if ("huntScoreTargetHeightWeight".equals(key)) KillAuraHandler.huntScoreTargetHeightWeight = floatStatic(text, KillAuraHandler.huntScoreTargetHeightWeight, 0, 100);
            else if ("huntScoreAttackRangeWeight".equals(key)) KillAuraHandler.huntScoreAttackRangeWeight = floatStatic(text, KillAuraHandler.huntScoreAttackRangeWeight, 0, 100);
            else if ("huntScoreVisibilityWeight".equals(key)) KillAuraHandler.huntScoreVisibilityWeight = floatStatic(text, KillAuraHandler.huntScoreVisibilityWeight, 0, 100);
            else if ("huntScoreOpennessWeight".equals(key)) KillAuraHandler.huntScoreOpennessWeight = floatStatic(text, KillAuraHandler.huntScoreOpennessWeight, 0, 100);
            else if ("huntOrbitSamplePoints".equals(key)) KillAuraHandler.huntOrbitSamplePoints = intStatic(text, KillAuraHandler.huntOrbitSamplePoints, KillAuraHandler.MIN_HUNT_ORBIT_SAMPLE_POINTS, KillAuraHandler.MAX_HUNT_ORBIT_SAMPLE_POINTS);
            else if ("teleportAttackPacketLimitPerTick".equals(key)) KillAuraHandler.teleportAttackPacketLimitPerTick = intStatic(
                    text, KillAuraHandler.teleportAttackPacketLimitPerTick,
                    KillAuraHandler.MIN_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK,
                    KillAuraHandler.MAX_TELEPORT_ATTACK_PACKET_LIMIT_PER_TICK);
            else if ("noDamageAttackLimit".equals(key)) KillAuraHandler.noDamageAttackLimit = intStatic(text, KillAuraHandler.noDamageAttackLimit, 0, KillAuraHandler.MAX_NO_DAMAGE_ATTACK_LIMIT);
            else if ("minAttackIntervalTicks".equals(key)) KillAuraHandler.minAttackIntervalTicks = intStatic(text, KillAuraHandler.minAttackIntervalTicks, 0, 20);
            else if ("targetsPerAttack".equals(key)) KillAuraHandler.targetsPerAttack = intStatic(text, KillAuraHandler.targetsPerAttack, 1, 50);
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
        addWhitelistBounds = null;
        addBlacklistBounds = null;
        nearbyListBounds = null;
        whitelistListBounds = null;
        blacklistListBounds = null;
        addRuleBounds = null;
        deleteRuleBounds = null;
        ruleListBounds = null;
        ruleToggleBounds = null;
        ruleModeBounds = null;
        rarityBounds.clear();
        huntScoreBounds = null;
    }

    private void hideAllInputs() {
        forEachInput(new InputVisitor() {
            @Override
            public void visit(GuiTextField input) {
                input.setVisible(false);
            }
        });
    }

    private void drawSectionLayouts(int mouseX, int mouseY) {
        for (SectionLayout layout : sectionLayouts) {
            Section section = layout.section;
            ModernUiRenderer.drawText(fontRenderer, section.title, layout.bounds.x, layout.bounds.y + 2,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(32, layout.bounds.width - 16));
            drawInfoIcon(layout.bounds.x + Math.min(Math.max(0, layout.bounds.width - 12),
                    fontRenderer.getStringWidth(section.title) + 5), layout.bounds.y + 1, section.tooltip, mouseX, mouseY);
            for (Field field : section.fields) {
                if (field.visible() && field.rowBounds != null) {
                    drawField(field, mouseX, mouseY);
                }
            }
        }
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

    private void drawInput(GuiTextField input, ModernMainLayout.Rect bounds, String placeholder, boolean enabled,
            int mouseX, int mouseY) {
        if (input == null || bounds == null) {
            return;
        }
        input.setVisible(true);
        input.setEnabled(enabled);
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
