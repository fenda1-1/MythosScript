package com.zszl.zszlScriptMod.gui.modern.path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.lwjgl.input.Keyboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.gui.DetachedSwingWindowManager;
import com.zszl.zszlScriptMod.gui.GuiModernMainScreen;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.path.ActionVariableRegistry;
import com.zszl.zszlScriptMod.path.ActionParameterVariableResolver;
import com.zszl.zszlScriptMod.path.InventoryItemFilterExpressionEngine;
import com.zszl.zszlScriptMod.path.PathRecordingManager;
import com.zszl.zszlScriptMod.path.PathRecordingPlayback;
import com.zszl.zszlScriptMod.path.RecordingPacketSupport;
import com.zszl.zszlScriptMod.path.PathSequenceManager;
import com.zszl.zszlScriptMod.path.PathSequenceEventListener;
import com.zszl.zszlScriptMod.path.PathSequenceManager.ActionData;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathSequence;
import com.zszl.zszlScriptMod.path.PathSequenceManager.PathStep;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.ExecutionEvent;
import com.zszl.zszlScriptMod.gui.modern.path.ModernExecutionLogView;
import com.zszl.zszlScriptMod.path.runtime.log.ExecutionLogManager.SessionSnapshot;
import com.zszl.zszlScriptMod.path.template.LegacyActionTemplateManager;
import com.zszl.zszlScriptMod.path.trigger.PlayerListTriggerSupport;
import com.zszl.zszlScriptMod.path.validation.PathConfigValidator;
import com.zszl.zszlScriptMod.path.validation.PathConfigValidator.Issue;
import com.zszl.zszlScriptMod.handlers.KillAuraHandler;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.FeatureDef;
import com.zszl.zszlScriptMod.otherfeatures.OtherFeatureGroupManager.GroupDef;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.ActionDisplayCatalog;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.ActionLibraryTreeFactory;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.library.ActionLibraryViewSupport;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryNode;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ActionLibraryVisibleRow;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.model.ExpressionTemplateCard;
import com.zszl.zszlScriptMod.gui.path.GuiActionEditor.template.ExpressionTemplateCatalog;
import com.zszl.zszlScriptMod.gui.path.template.ActionTemplateCatalog;
import com.zszl.zszlScriptMod.gui.path.template.ActionTemplateCatalog.ActionTemplate;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.modern.core.ModernMouseCapture;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.modern.packet.PacketWorkbenchTab;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ActionEditorFieldHelp;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ActionEditorJson;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ActionEditorUxSupport;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ClickSemantics;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ExpressionEditorPreview;
import com.zszl.zszlScriptMod.gui.modern.path.editor.ModernExpressionCodeEditor;
import com.zszl.zszlScriptMod.gui.modern.path.editor.MoveChestEditor;
import com.zszl.zszlScriptMod.gui.modern.path.editor.SlotCanvasWidget;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;
import com.zszl.zszlScriptMod.utils.TickRangeSpec;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.GlStateManager;

import static com.zszl.zszlScriptMod.gui.path.GuiActionEditor.util.ActionEditorDisplayConverters.*;

/**
 * Native list/detail workbench for path sequences, steps and actions.
 *
 * <p>The tab owns an isolated sequence draft. It intentionally uses only the
 * path model and runtime service APIs; all editing surfaces, menus and reports
 * are drawn in the modern shell coordinate space.</p>
 */
public final class ModernPathWorkbenchTab implements ModernSettingsTab {
    private static final int MIN_LOGICAL_WIDTH = 760;
    private static final int MIN_LOGICAL_HEIGHT = 460;
    private static final int HEADER_HEIGHT = 40;
    private static final int FOOTER_HEIGHT = 32;
    private static final int NAV_CATEGORY_HEIGHT = 22;
    private static final int NAV_SUB_HEIGHT = 22;
    private static final int NAV_SEQUENCE_HEIGHT = 26;
    private static final int STEP_ROW_HEIGHT = 32;
    private static final int ACTION_ROW_HEIGHT = 29;
    private static final int TOOL_BUTTON_HEIGHT = 18;
    private static final int TOOL_STRIP_HEADER_HEIGHT = 26;
    private static final long DOUBLE_CLICK_MS = 320L;
    private static final int PARAM_ROW_HEIGHT = 42;
    private static final int MAX_HISTORY = 80;
    private static final String[] SYSTEM_MESSAGE_COLOR_CODES = {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    };
    private static final int[] SYSTEM_MESSAGE_COLORS = {
            0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA,
            0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
            0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF,
            0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
    };
    private static final String[] SYSTEM_MESSAGE_FORMAT_CODES = { "§l", "§n", "§o", "§m", "§k", "§r" };
    private static final String[] SYSTEM_MESSAGE_FORMAT_LABELS = {
            "gui.modern.path.wb.u001", "gui.modern.path.wb.u002", "gui.modern.path.wb.u003", "gui.modern.path.wb.u004", "gui.modern.path.wb.u005", "gui.modern.path.wb.u006"
    };

    private static final List<String> ACTION_TYPES = Collections.unmodifiableList(Arrays.asList(
            "command", "system_message", "paste_text", "disconnect", "delay", "key", "jump", "click",
            "window_click", "conditional_window_click", "setview", "rightclickblock", "rightclickentity",
            "take_all_items_safe", "dropfiltereditems", "move_inventory_items_to_chest_slots",
             "warehouse_auto_deposit", "autochestclick", "blocknextgui",
            "hidecurrentgui", "showhiddengui", "close_container_window", "hud_text_check",
            "condition_inventory_item", "condition_gui_title", "condition_player_in_area", "condition_player_list",
            "condition_scoreboard", "condition_packet_field", "condition_packet_text", "condition_bossbar",
            "condition_gui_element", "condition_screen_region", "condition_entity_nearby", "condition_expression",
            "wait_until_inventory_item", "wait_until_gui_title", "wait_until_player_in_area", "wait_until_player_list",
            "wait_until_scoreboard", "wait_until_packet_field", "wait_until_gui_element", "wait_until_entity_nearby",
            "wait_until_hud_text", "wait_until_expression", "wait_combined", "wait_until_captured_id",
            "wait_until_packet_text", "wait_until_screen_region", "set_var", "capture_nearby_entity",
            "capture_gui_title", "capture_inventory_slot", "capture_hotbar", "capture_entity_list",
            "capture_packet_field", "capture_gui_element", "capture_scoreboard", "capture_screen_region",
            "capture_block_at", "label", "goto_action", "goto_label", "if_else", "switch_var", "branch_table",
            "while_condition", "for_each_point", "for_each_list", "retry_block", "debug_print_var",
            "debug_print_nearby_entities", "debug_print_gui_summary", "skip_actions", "skip_steps",
            "repeat_actions", "restart_sequence", "no_stop_navigation", "autoeat", "autoequip", "autopickup",
            "toggle_autoeat", "toggle_autofishing", "toggle_kill_aura", "toggle_fly",
            "toggle_conditional_execution", "toggle_auto_escape", "toggle_baritone_free_look",
            "toggle_baritone_human_like", "toggle_baritone_flight", "toggle_other_feature", "hunt",
            "follow_entity", "use_hotbar_item", "use_held_item", "move_inventory_item_to_hotbar",
            "spread_inventory_item", "stack_inventory_item", "pickup_nearby_items", "switch_hotbar_slot",
            "silentuse", "runlastsequence", "send_packet", "run_sequence", "run_template",
            "stop_current_sequence", "sequence_control"));

    private enum AuxiliaryView {
        NONE, VALIDATION, LOGS, TEMPLATES, VARIABLES, RECORDING, ACTION_EDITOR,
        EXPRESSION_EDITOR
    }

    private enum ModalMode {
        NONE, ADD_SEQUENCE, RENAME_SEQUENCE, MOVE_SEQUENCE, DELETE_SEQUENCE,
        ADD_CATEGORY, RENAME_CATEGORY, DELETE_CATEGORY,
        ADD_SUBCATEGORY, RENAME_SUBCATEGORY, DELETE_SUBCATEGORY, STEP_SETTINGS, BUILTIN_DELAY_SETTINGS, STEP_NOTE, SEQUENCE_NOTE,
        RUN_FROM_STEP
    }

    private enum NavKind {
        CATEGORY, SUBCATEGORY, SEQUENCE
    }

    private enum ActionPickerMode {
        NONE, SEQUENCE, CAPTURED_ID, OTHER_FEATURE, KEYBOARD, LABEL, ACTION_INDEX, VARIABLE, TEMPLATE
    }

    private enum ListFocus {
        NONE, NAVIGATION, STEP, ACTION
    }

    private enum ClipboardPayloadType {
        NONE, SEQUENCE, STEPS, ACTIONS
    }

    private final Minecraft minecraft;
    private final String command;
    private final String title;
    private final String subtitle;
    private final Consumer<String> routeRequest;

    private boolean initialized;
    private boolean restoringHistory;
    private boolean dirty;
    private boolean templateDirty;
    private boolean sortCategoriesOnSave;
    private boolean returnRequested;
    private String pendingFocusCommand;

    private List<PathSequence> sequences = new ArrayList<>();
    private List<PathSequence> originalSequences = new ArrayList<>();
    private List<String> categories = new ArrayList<>();
    private List<String> originalCategories = new ArrayList<>();
    private Map<String, List<String>> subCategories = new LinkedHashMap<>();
    private Map<String, List<String>> originalSubCategories = new LinkedHashMap<>();
    private Map<String, Boolean> hiddenCategories = new LinkedHashMap<>();
    private Map<String, Boolean> originalHiddenCategories = new LinkedHashMap<>();

    private String selectedCategory = "";
    private String selectedSubCategory = "";
    private String selectedSequenceName = "";
    private PathSequence selectedSequence;
    private PathStep selectedStep;
    private ActionData selectedAction;
    private int selectedStepIndex = -1;
    private int selectedActionIndex = -1;
    private final LinkedHashSet<Integer> selectedStepIndices = new LinkedHashSet<Integer>();
    private final LinkedHashSet<Integer> selectedActionIndices = new LinkedHashSet<Integer>();
    private int selectionAnchorStepIndex = -1;
    private int selectionAnchorActionIndex = -1;
    private ListFocus listFocus = ListFocus.NONE;
    private ClipboardPayloadType clipboardPayloadType = ClipboardPayloadType.NONE;
    private final List<PathStep> stepClipboard = new ArrayList<PathStep>();
    private final List<ActionData> actionClipboard = new ArrayList<ActionData>();
    private PathSequence sequenceClipboard;
    private PathWorkbenchToolCatalog.Zone customizeZone;
    private ModernMainLayout.Rect customizeBounds;
    private final List<CustomizeHit> customizeHits = new ArrayList<CustomizeHit>();
    private final Map<String, ModernMainLayout.Rect> toolHits = new LinkedHashMap<String, ModernMainLayout.Rect>();
    private long lastActionClickTime;
    private int lastActionClickIndex = -1;
    private int draggingStepCount = 1;
    private int draggingActionCount = 1;
    private int navFooterHeight = 78;
    private int selectedTemplateIndex = -1;
    private int selectedActionTemplateIndex = -1;

    private final Set<String> collapsedCategories = new HashSet<>();
    private final PathNavigationSelection navigationSelection = new PathNavigationSelection();
    private final Set<String> collapsedSubGroups = new HashSet<>();
    private final List<HistoryState> undoHistory = new ArrayList<>();
    private final List<HistoryState> redoHistory = new ArrayList<>();
    private final List<PathSequence> recordingUndoHistory = new ArrayList<PathSequence>();
    private final List<PathSequence> recordingRedoHistory = new ArrayList<PathSequence>();
    private final List<ActionData> removedPersistentActions = new ArrayList<>();

    private final Map<String, ModernTextField> fields = new LinkedHashMap<>();
    private final Map<String, ModernMainLayout.Rect> fieldBounds = new HashMap<>();
    private String focusedFieldKey;
    private FontRenderer fontRenderer;

    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private ModernMainLayout.Rect hostBounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private float uiScale = 1.0F;
    private int uiOriginX;
    private int uiOriginY;
    private ModernMainLayout.Rect headerBounds;
    private ModernMainLayout.Rect bodyBounds;
    private ModernMainLayout.Rect footerBounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect navigationContentBounds;
    private ModernMainLayout.Rect navigationDividerBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect editorBodyBounds;
    private ModernMainLayout.Rect stepBounds;
    private ModernMainLayout.Rect actionBounds;
    private ModernMainLayout.Rect editorDividerBounds;
    private ModernMainLayout.Rect stepListBounds;
    private ModernMainLayout.Rect stepToolsBounds;
    private ModernMainLayout.Rect actionListBounds;
    private ModernMainLayout.Rect actionToolsBounds;
    private ModernMainLayout.Rect navToolsBounds;
    private ModernMainLayout.Rect navMoreBounds;
    private ModernMainLayout.Rect stepMoreBounds;
    private ModernMainLayout.Rect navToolsSettingsBounds;
    private ModernMainLayout.Rect stepToolsSettingsBounds;
    private ModernMainLayout.Rect actionToolsSettingsBounds;
    private ModernMainLayout.Rect navToolsToggleBounds;
    private ModernMainLayout.Rect stepToolsToggleBounds;
    private ModernMainLayout.Rect actionToolsToggleBounds;
    private ModernMainLayout.Rect recordingStepToolsToggleBounds;
    private ModernMainLayout.Rect recordingActionToolsToggleBounds;
    private ModernMainLayout.Rect recordingControlToolsToggleBounds;
    private boolean navToolsExpanded = true;
    private boolean stepToolsExpanded = true;
    private boolean actionToolsExpanded = true;
    private boolean recordingStepToolsExpanded = true;
    private boolean recordingActionToolsExpanded = true;
    private boolean recordingControlToolsExpanded = true;
    private ModernMainLayout.Rect navSearchBounds;
    private ModernMainLayout.Rect navCategoryAddBounds;
    private ModernMainLayout.Rect navSubCategoryAddBounds;
    private ModernMainLayout.Rect navCategoryMoreBounds;
    private ModernMainLayout.Rect navSequenceAddBounds;
    private ModernMainLayout.Rect navSequenceCopyBounds;
    private ModernMainLayout.Rect navSequenceMoreBounds;
    private ModernMainLayout.Rect headerBackBounds;
    private ModernMainLayout.Rect headerRunBounds;
    private ModernMainLayout.Rect headerMoreBounds;
    private final List<HeaderToolHit> headerToolHits = new ArrayList<HeaderToolHit>();
    private ModernMainLayout.Rect footerUndoBounds;
    private ModernMainLayout.Rect footerRedoBounds;
    private ModernMainLayout.Rect footerSaveBounds;
    private ModernMainLayout.Rect footerDiscardBounds;
    private ModernMainLayout.Rect stepSettingsButtonBounds;
    private ModernMainLayout.Rect stepOptionsToggleBounds;
    private ModernMainLayout.Rect stepOptionsPanelBounds;
    private ModernMainLayout.Rect stepSettingsModalBounds;
    private ModernMainLayout.Rect stepSettingsPolicyBounds;
    private ModernMainLayout.Rect stepSettingsTargetBounds;
    private ModernMainLayout.Rect stepSettingsConfirmBounds;
    private ModernMainLayout.Rect stepSettingsCancelBounds;
    private final List<RectAction> stepSettingsPolicyOptions = new ArrayList<>();
    private ModernMainLayout.Rect stepTargetPickerBounds;
    private ModernMainLayout.Rect stepTargetNavigationBounds;
    private ModernMainLayout.Rect stepTargetDetailsBounds;
    private ModernMainLayout.Rect stepTargetCancelBounds;
    private final List<StepTargetSequenceHit> stepTargetSequenceHits = new ArrayList<>();
    private final List<StepTargetActionHit> stepTargetActionHits = new ArrayList<>();
    private boolean stepSettingsPolicyOpen;
    private boolean stepTargetPickerOpen;
    private String stepSettingsPolicy = "END_SEQUENCE";
    private String stepSettingsTargetSequence = "";
    private int stepSettingsTargetStepIndex;
    private int stepSettingsTargetActionIndex;
    private int stepTargetNavigationScroll;
    private int stepTargetNavigationMaxScroll;
    private int stepTargetDetailsScroll;
    private int stepTargetDetailsMaxScroll;
    private ModernMainLayout.Rect actionPageLibraryBounds;
    private ModernMainLayout.Rect actionPageParamsBounds;
    private ModernMainLayout.Rect actionPageDividerBounds;
    private ModernMainLayout.Rect actionPageApplyTypeBounds;
    private ModernMainLayout.Rect actionPageBackBounds;
    private ModernMainLayout.Rect actionPageLibrarySearchBounds;
    private ModernMainLayout.Rect actionPageLocateActionBounds;
    private ModernMainLayout.Rect actionPageWritingHelpBounds;
    private ModernMainLayout.Rect actionPageRecentLimitBounds;
    private ModernMainLayout.Rect actionPageParameterViewportBounds;
    private ModernMainLayout.Rect expressionEditorConfirmBounds;
    private ModernMainLayout.Rect expressionEditorCancelBounds;
    private ModernMainLayout.Rect expressionTemplateListBounds;
    private ModernMainLayout.Rect expressionEditorDividerBounds;
    private ModernMainLayout.Rect expressionTemplateScrollTrack;
    private ModernMainLayout.Rect expressionTemplateScrollThumb;
    private ModernMainLayout.Rect expressionPreviewBounds;
    private ModernMainLayout.Rect expressionPreviewScrollTrack;
    private ModernMainLayout.Rect expressionPreviewScrollThumb;
    private ModernMainLayout.Rect expressionCodeEditorBounds;
    private ModernMainLayout.Rect expressionEditorExpandBounds;
    private final List<String> actionPageVisibleTypes = new ArrayList<>();
    private final List<String> recentActionTypes = new ArrayList<>();
    private int actionPageRenderedRecentLimit = -1;
    private final List<ActionLibraryNode> actionPageRoots = new ArrayList<>();
    private final Set<String> actionPageExpandedGroups = new HashSet<>();
    private final List<ActionLibraryVisibleRow> actionPageRows = new ArrayList<>();
    private int actionPageScroll;
    private ModernMainLayout.Rect actionLibraryScrollTrack;
    private ModernMainLayout.Rect actionLibraryScrollThumb;
    private boolean draggingActionLibraryScroll;
    private int actionLibraryScrollDragOffset;
    private float actionLibraryScrollHover;
    private int expressionTemplateScroll;
    private int expressionTemplateMaxScroll;
    private ModernActionEditorSchema.Kind expressionEditorKind;
    private String expressionEditorParamKey = "";
    private int expressionEditorIndex = -1;
    private String expressionEditorTitle = "gui.modern.path.wb.u007";
    private final ModernExpressionCodeEditor expressionCodeEditor = new ModernExpressionCodeEditor();
    private boolean expressionEditorExpanded;
    private long expressionCompletionRefreshAt;
    private final List<ExpressionRowHit> expressionRowHits = new ArrayList<>();
    private final List<ExpressionTemplateHit> expressionTemplateHits = new ArrayList<>();
    private final List<ModernMainLayout.Rect> systemMessageColorBounds = new ArrayList<>();
    private final List<ModernMainLayout.Rect> systemMessageFormatBounds = new ArrayList<>();
    private final List<SlotGridHit> slotGridHits = new ArrayList<>();
    private final SlotCanvasWidget.Machine slotGridMachine = new SlotCanvasWidget.Machine();
    private SlotCanvasWidget.Layout slotGridDragLayout;
    private String slotGridDragKey = "";
    private int slotGridPressIndex = -1;
    private boolean slotGridPressMoved;
    private boolean slotGridPressRemove;
    private boolean slotGridPressExclusive;
    private final Map<String, String> variableScopeOverrides = new HashMap<>();
    private ActionData variableScopeAction;
    private final List<StructuredListHit> structuredListHits = new ArrayList<>();
    private final List<StructuredListEditorHit> structuredListEditorHits = new ArrayList<>();
    private ModernMainLayout.Rect structuredListAddBounds;
    private ModernMainLayout.Rect structuredListModeBounds;
    private ModernMainLayout.Rect huntEntitySuggestionBounds;
    private final List<ModernMainLayout.Rect> huntEntitySuggestionHits = new ArrayList<>();
    private final List<String> huntEntitySuggestionValues = new ArrayList<>();
    private int huntEntitySuggestionScroll;
    private int huntEntitySuggestionMaxScroll;
    private String structuredPlayerMode = PlayerListTriggerSupport.MODE_EXACT;
    private String structuredEditingKey = "";
    private int structuredEditingIndex = -1;
    private ActionData structuredEditingAction;
    private final MoveChestEditor moveChestEditor = new MoveChestEditor();
    private boolean moveChestHostBound;
    private final ModernActionVariablePanel variablePanel = new ModernActionVariablePanel();
    private boolean variablePanelHostBound;
    private ActionPickerMode actionPickerMode = ActionPickerMode.NONE;
    private String actionPickerParamKey = "";
    private final List<ActionPickerOption> actionPickerOptions = new ArrayList<>();
    private final List<KeyboardKeyHit> keyboardKeyHits = new ArrayList<>();
    private ModernMainLayout.Rect actionPickerBounds;
    private ModernMainLayout.Rect actionPickerListBounds;
    private ModernMainLayout.Rect actionPickerSearchBounds;
    private ModernMainLayout.Rect actionPickerClearBounds;
    private ModernMainLayout.Rect actionPickerCancelBounds;
    private int actionPickerScroll;
    private int actionPickerMaxScroll;
    private ModernMainLayout.Rect actionPickerScrollTrack;
    private ModernMainLayout.Rect actionPickerScrollThumb;
    private boolean draggingActionPickerScroll;
    private int actionPickerScrollDragOffset;
    private float actionPickerScrollHover;

    private double navigationRatio = 0.28D;
    private double editorSplitRatio = 0.48D;
    private double actionPageLibraryRatio = 0.29D;
    private double expressionEditorSplitRatio = 0.38D;
    private boolean draggingNavigationDivider;
    private boolean draggingEditorDivider;
    private boolean draggingActionPageDivider;
    private boolean draggingExpressionEditorDivider;
    private boolean draggingTemplateCategoryDivider;
    private boolean draggingTemplateListDivider;
    private boolean draggingRecordingStepDivider;
    private boolean draggingRecordingActionDivider;
    private boolean draggingTemplateCategoryScroll;
    private boolean draggingTemplateScroll;
    private double actionPageLibraryDragStartRatio;
    private double expressionEditorDragStartRatio;
    private double templateCategoryRatio = 0.20D;
    private double templateListRatio = 0.44D;
    private double templateCategoryDragStartRatio;
    private double templateListDragStartRatio;
    private double recordingStepRatio = 0.25D;
    private double recordingActionRatio = 0.46D;
    private long lastTemplateCategoryDividerClickAt;
    private long lastTemplateListDividerClickAt;
    private int lastTemplateCategoryDividerClickX;
    private int lastTemplateListDividerClickX;
    private long lastActionPageDividerClickAt;
    private int lastActionPageDividerClickX;
    private long lastExpressionDividerClickAt;
    private int lastExpressionDividerClickX;
    private boolean showActionWritingHelp;
    private boolean draggingActionWritingHelp;
    private boolean resizingActionWritingHelp;
    private int actionWritingHelpDragOffsetX;
    private int actionWritingHelpDragOffsetY;
    private int actionWritingHelpResizeStartX;
    private int actionWritingHelpResizeStartY;
    private int actionWritingHelpResizeStartWidth;
    private int actionWritingHelpResizeStartHeight;
    private ModernMainLayout.Rect actionWritingHelpBounds;
    private ModernMainLayout.Rect actionWritingHelpHeaderBounds;
    private ModernMainLayout.Rect actionWritingHelpCloseBounds;
    private boolean draggingExpressionTemplateScroll;
    private int expressionTemplateScrollDragOffset;
    private float expressionTemplateScrollHover;
    private ExpressionTemplateCard selectedExpressionTemplate;
    private int expressionPreviewScroll;
    private int expressionPreviewMaxScroll;
    private boolean draggingExpressionPreviewScroll;
    private int expressionPreviewScrollDragOffset;
    private float expressionPreviewScrollHover;
    private int expressionPreviewLineCount;
    private boolean stepOptionsExpanded;
    private final ModernHoverScrollbar navigationScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar stepScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar actionScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar validationScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar logListScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar logDetailScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar recordingScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar parameterScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar stepTargetNavScrollBar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar stepTargetDetailsScrollBar = new ModernHoverScrollbar();
    private int navigationScroll;
    private int navigationMaxScroll;
    private int stepScroll;
    private int stepMaxScroll;
    private int actionScroll;
    private int actionMaxScroll;
    private int parameterScroll;
    private int parameterMaxScroll;
    private final List<NavHit> navHits = new ArrayList<>();
    private final List<RowHit> stepHits = new ArrayList<>();
    private final List<RowHit> actionHits = new ArrayList<>();
    private final List<RowHit> actionPacketToggleHits = new ArrayList<>();
    private final List<ParameterHit> parameterHits = new ArrayList<>();
    private int draggingStepIndex = -1;
    private int stepDropIndex = -1;
    private int draggingActionIndex = -1;
    private int actionDropIndex = -1;
    private int dragStartY;
    private int modalStepIndex = -1;

    private AuxiliaryView auxiliaryView = AuxiliaryView.NONE;
    private ModalMode modalMode = ModalMode.NONE;
    private String modalTitle = "";
    private String modalMessage = "";
    private String moveTargetCategory = "";
    private String moveTargetSubCategory = "";
    private String movePickerQuery = "";
    private final List<NavHit> movePickerHits = new ArrayList<>();
    private ModernMainLayout.Rect moveCategoriesBounds;
    private ModernMainLayout.Rect moveSubCategoriesBounds;
    private int moveCategoriesScroll;
    private int moveSubCategoriesScroll;
    private int moveCategoriesMaxScroll;
    private int moveSubCategoriesMaxScroll;
    private int modalAnchorX;
    private int modalAnchorY;
    private ModernMainLayout.Rect modalBounds;
    private ModernMainLayout.Rect modalMainBounds;
    private ModernMainLayout.Rect modalSecondaryBounds;
    private ModernMainLayout.Rect modalConfirmBounds;
    private ModernMainLayout.Rect modalCancelBounds;
    private ModernMainLayout.Rect paletteBounds;
    private ModernMainLayout.Rect paletteSearchBounds;
    private int paletteScroll;
    private int paletteMaxScroll;
    private ModernMainLayout.Rect paletteScrollTrack;
    private ModernMainLayout.Rect paletteScrollThumb;
    private boolean draggingPaletteScroll;
    private int paletteScrollDragOffset;
    private float paletteScrollHover;
    private final List<RectAction> menuItems = new ArrayList<>();
    private ModernMainLayout.Rect menuBounds;
    private String menuTitle = "";
    private List<Issue> validationIssues = new ArrayList<>();
    private int validationScroll;
    private int validationMaxScroll;
    private List<SessionSnapshot> sessions = new ArrayList<>();
    private int selectedSessionIndex = -1;
    private int sessionScroll;
    private int logDetailScroll;
    private int sessionMaxScroll;
    private int logDetailMaxScroll;
    private final ModernExecutionLogView executionLogView = new ModernExecutionLogView(this::status);
    // Legacy models remain the target-sequence registry used by run_template.
    private List<LegacyActionTemplateManager.TemplateEditModel> templateModels = new ArrayList<>();
    // The modern library presents reusable action chains from the catalog.
    private List<ActionTemplate> actionTemplates = new ArrayList<>();
    private int templateScroll;
    private int templateMaxScroll;
    private int templateCategoryScroll;
    private int templateCategoryMaxScroll;
    private float templateCategoryScrollHover;
    private float templateScrollHover;
    private int templateCategoryScrollDragOffset;
    private int templateScrollDragOffset;
    private ModernMainLayout.Rect templateCategoryScrollTrack;
    private ModernMainLayout.Rect templateCategoryScrollThumb;
    private ModernMainLayout.Rect templateScrollTrack;
    private ModernMainLayout.Rect templateScrollThumb;
    private ModernMainLayout.Rect templateCategoryDividerBounds;
    private ModernMainLayout.Rect templateListDividerBounds;
    private ModernMainLayout.Rect templateCategoryBounds;
    private ModernMainLayout.Rect templateListViewportBounds;
    private String selectedActionTemplateCategory = "gui.modern.path.wb.u008";
    private final List<String> actionTemplateCategories = new ArrayList<>();
    private final List<RowHit> templateCategoryHits = new ArrayList<>();
    private String status = "";
    private long statusUntil;

    private static final class NavHit {
        final NavKind kind;
        final String category;
        final String subCategory;
        final PathSequence sequence;
        final ModernMainLayout.Rect bounds;
        final ModernMainLayout.Rect toggle;

        NavHit(NavKind kind, String category, String subCategory, PathSequence sequence,
                ModernMainLayout.Rect bounds, ModernMainLayout.Rect toggle) {
            this.kind = kind;
            this.category = category;
            this.subCategory = subCategory;
            this.sequence = sequence;
            this.bounds = bounds;
            this.toggle = toggle;
        }
    }

    private static final class RowHit {
        final int index;
        final ModernMainLayout.Rect bounds;

        RowHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class ParameterHit {
        final String key;
        final ModernMainLayout.Rect bounds;
        final ModernActionEditorSchema.Field field;

        ParameterHit(String key, ModernMainLayout.Rect bounds, ModernActionEditorSchema.Field field) {
            this.key = key;
            this.bounds = bounds;
            this.field = field;
        }
    }

    private static final class ExpressionRowHit {
        final int index;
        final ModernMainLayout.Rect row;
        final ModernMainLayout.Rect edit;
        final ModernMainLayout.Rect delete;
        final ModernActionEditorSchema.Field field;

        ExpressionRowHit(int index, ModernMainLayout.Rect row, ModernMainLayout.Rect edit,
                ModernMainLayout.Rect delete, ModernActionEditorSchema.Field field) {
            this.index = index;
            this.row = row;
            this.edit = edit;
            this.delete = delete;
            this.field = field;
        }
    }

    private static final class ExpressionTemplateHit {
        final ExpressionTemplateCard card;
        final ModernMainLayout.Rect bounds;

        ExpressionTemplateHit(ExpressionTemplateCard card, ModernMainLayout.Rect bounds) {
            this.card = card;
            this.bounds = bounds;
        }
    }

    private static final class ActionPickerOption {
        final String value;
        final String label;
        final String detail;

        ActionPickerOption(String value, String label, String detail) {
            this.value = value == null ? "" : value;
            this.label = label == null ? "" : label;
            this.detail = detail == null ? "" : detail;
        }
    }

    private static final class StepTargetSequenceHit {
        final PathSequence sequence;
        final ModernMainLayout.Rect bounds;

        StepTargetSequenceHit(PathSequence sequence, ModernMainLayout.Rect bounds) {
            this.sequence = sequence;
            this.bounds = bounds;
        }
    }

    private static final class StepTargetActionHit {
        final int stepIndex;
        final int actionIndex;
        final ModernMainLayout.Rect bounds;

        StepTargetActionHit(int stepIndex, int actionIndex, ModernMainLayout.Rect bounds) {
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.bounds = bounds;
        }
    }

    private static final class KeyboardKey {
        final String label;
        final String value;
        final int units;

        KeyboardKey(String label, String value, int units) {
            this.label = label;
            this.value = value;
            this.units = Math.max(1, units);
        }
    }

    private static final class KeyboardKeyHit {
        final KeyboardKey key;
        final ModernMainLayout.Rect bounds;

        KeyboardKeyHit(KeyboardKey key, ModernMainLayout.Rect bounds) {
            this.key = key;
            this.bounds = bounds;
        }
    }

    private static final class SlotGridHit {
        final String key;
        final int index;
        final int rows;
        final int cols;
        final ModernMainLayout.Rect bounds;

        SlotGridHit(String key, int index, int rows, int cols, ModernMainLayout.Rect bounds) {
            this.key = key;
            this.index = index;
            this.rows = rows;
            this.cols = cols;
            this.bounds = bounds;
        }
    }

    private static final class StructuredListHit {
        final String key;
        final int index;
        final ModernMainLayout.Rect row;
        final ModernMainLayout.Rect delete;

        StructuredListHit(String key, int index, ModernMainLayout.Rect row, ModernMainLayout.Rect delete) {
            this.key = key;
            this.index = index;
            this.row = row;
            this.delete = delete;
        }
    }

    private static final class StructuredListEditorHit {
        final String key;
        final ModernMainLayout.Rect extra;
        final ModernMainLayout.Rect add;

        StructuredListEditorHit(String key, ModernMainLayout.Rect extra, ModernMainLayout.Rect add) {
            this.key = key;
            this.extra = extra;
            this.add = add;
        }
    }

    private static final class StructuredEntry {
        final String name;
        final String value;

        StructuredEntry(String name, String value) {
            this.name = name == null ? "" : name;
            this.value = value == null ? "" : value;
        }
    }

    private static final class RectAction {
        final String label;
        final String shortcut;
        final boolean enabled;
        final boolean danger;
        final Runnable action;
        final PathWorkbenchToolCatalog.ToolId tool;
        final String description;
        ModernMainLayout.Rect bounds;
        ModernMainLayout.Rect infoBounds;
        ModernMainLayout.Rect pinBounds;
        boolean selected;

        RectAction(String label, boolean enabled, boolean danger, Runnable action) {
            this(label, "", enabled, danger, action, null, "");
        }

        RectAction(String label, String shortcut, boolean enabled, boolean danger, Runnable action) {
            this(label, shortcut, enabled, danger, action, null, "");
        }

        RectAction(String label, String shortcut, boolean enabled, boolean danger, Runnable action,
                PathWorkbenchToolCatalog.ToolId tool, String description) {
            this.label = label == null ? "" : label;
            this.shortcut = shortcut == null ? "" : shortcut;
            this.enabled = enabled;
            this.danger = danger;
            this.action = action;
            this.tool = tool;
            this.description = description == null ? "" : description;
        }
    }

    private static final class HeaderToolHit {
        final PathWorkbenchToolCatalog.ToolId tool;
        final ModernMainLayout.Rect bounds;

        HeaderToolHit(PathWorkbenchToolCatalog.ToolId tool, ModernMainLayout.Rect bounds) {
            this.tool = tool;
            this.bounds = bounds;
        }
    }

    private static final class CustomizeHit {
        final PathWorkbenchToolCatalog.ToolId tool;
        final ModernMainLayout.Rect bounds;
        final ModernMainLayout.Rect toggleBounds;

        CustomizeHit(PathWorkbenchToolCatalog.ToolId tool, ModernMainLayout.Rect bounds,
                ModernMainLayout.Rect toggleBounds) {
            this.tool = tool;
            this.bounds = bounds;
            this.toggleBounds = toggleBounds;
        }
    }

    private static final class HistoryState {
        final List<PathSequence> sequences;
        final List<String> categories;
        final Map<String, List<String>> subCategories;
        final Map<String, Boolean> hiddenCategories;
        final String category;
        final String subCategory;
        final String sequenceName;
        final int stepIndex;
        final int actionIndex;
        final boolean sortCategoriesOnSave;

        HistoryState(List<PathSequence> sequences, List<String> categories, Map<String, List<String>> subCategories,
                Map<String, Boolean> hiddenCategories,
                String category, String subCategory, String sequenceName, int stepIndex, int actionIndex,
                boolean sortCategoriesOnSave) {
            this.sequences = sequences;
            this.categories = categories;
            this.subCategories = subCategories;
            this.hiddenCategories = hiddenCategories;
            this.category = category;
            this.subCategory = subCategory;
            this.sequenceName = sequenceName;
            this.stepIndex = stepIndex;
            this.actionIndex = actionIndex;
            this.sortCategoriesOnSave = sortCategoriesOnSave;
        }
    }

    public ModernPathWorkbenchTab() {
        this(null, "gui.modern.path.wb.u009", "gui.modern.path.wb.u010", Collections.<String>emptyList(), null, null);
    }

    public ModernPathWorkbenchTab(Minecraft minecraft, ModernScreenContext context) {
        this(minecraft,
                "gui.modern.path.wb.u009",
                "gui.modern.path.wb.u010",
                Collections.<String>emptyList(), context == null ? null : context.getRouteRequest(),
                context == null ? "reload_paths" : context.getCommand());
    }

    public ModernPathWorkbenchTab(ModernScreenContext context) {
        this(context == null ? null : context.getMinecraft(), context);
    }

    public static ModernPathWorkbenchTab create(Minecraft minecraft, ModernScreenContext context) {
        return new ModernPathWorkbenchTab(minecraft, context);
    }

    public ModernPathWorkbenchTab(Minecraft minecraft, Consumer<String> routeRequest) {
        this(minecraft, "gui.modern.path.wb.u009", "gui.modern.path.wb.u010", Collections.<String>emptyList(), routeRequest,
                "reload_paths");
    }

    public ModernPathWorkbenchTab(String command, String title, String subtitle, List<String> children) {
        this(null, title, subtitle, children, null, command);
    }

    public ModernPathWorkbenchTab(String command, String title, String subtitle, List<String> children,
            Minecraft minecraft) {
        this(minecraft, title, subtitle, children, null, command);
    }

    public ModernPathWorkbenchTab(Minecraft minecraft, String title, String subtitle, List<String> children) {
        this(minecraft, title, subtitle, children, null, "reload_paths");
    }

    public ModernPathWorkbenchTab(Minecraft minecraft, String title, String subtitle, List<String> children,
            Consumer<String> routeRequest, String command) {
        this.minecraft = minecraft == null ? Minecraft.getMinecraft() : minecraft;
        this.title = safe(title).isEmpty() ? "gui.modern.path.wb.u009" : title;
        this.subtitle = safe(subtitle).isEmpty() ? "gui.modern.path.wb.u010" : subtitle;
        this.routeRequest = routeRequest;
        this.command = safe(command).isEmpty() ? "reload_paths" : command;
        this.pendingFocusCommand = this.command;
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        this.fontRenderer = fontRenderer;
        if (!initialized) {
            navigationRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.navigation", navigationRatio);
            editorSplitRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.editor", editorSplitRatio);
            actionPageLibraryRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.action_library", actionPageLibraryRatio);
            expressionEditorSplitRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.expression_editor", expressionEditorSplitRatio);
            templateCategoryRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.template_category", templateCategoryRatio);
            templateListRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.template_list", templateListRatio);
            recordingStepRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.recording_step", recordingStepRatio);
            recordingActionRatio = MainUiLayoutManager.getModernSplitRatio("path.workbench.recording_action", recordingActionRatio);
            PathWorkbenchToolCatalog.ensureLoaded();
            loadDraft();
            initialized = true;
        }
        ensureField("search", 128);
        ensureField("sequence.loop", 12);
        ensureField("sequence.note", 4096);
        ensureField("step.x", 40);
        ensureField("step.y", 40);
        ensureField("step.z", 40);
        ensureField("step.note", 4096);
        ensureField("step.retry", 12);
        ensureField("step.timeout", 12);
        ensureField("step.tolerance", 12);
        ensureField("step.failureTarget", 128);
        ensureField("action.type", 96);
        ensureField("action.editor.search", 128);
        ensureField("action.picker.search", 128);
        ensureField("structured.list.name", 256);
        ensureField("structured.list.value", 16);
        ensureField("step.settings.retry", 12);
        ensureField("step.settings.timeout", 12);
        ensureField("step.settings.tolerance", 12);
        ensureField("step.settings.failureTarget", 128);
        ensureField("expression.editor.search", 96);
        ensureField("modal.main", 4096);
        ensureField("modal.secondary", 256);
        ensureField("palette.search", 96);
        ensureField("template.search", 128);
        ensureField("template.name", 128);
        ensureField("template.sequence", 128);
        ensureField("template.defaults", 1024);
        ensureField("template.note", 2048);
        ensureField("variable.rename", 128);
        ensureField("recording.name", 128);
        ensureField("recording.radius", 12);
        ensureField("recording.packetWindowSeconds", 12);
        ensureField("move.chest.maxTake", 8);
        ensureField("move.chest.maxPut", 8);
        bindMoveChestHost();
        bindEditorFields();
    }

    @Override
    public void updateScreen() {
        if (!initialized) {
            return;
        }
        for (ModernTextField field : fields.values()) {
            field.updateCursorCounter();
        }
        if (recordingPacketWorkbench != null) {
            recordingPacketWorkbench.updateScreen();
        }
        if (modalMode == ModalMode.NONE) {
            syncEditorFields();
            syncAuxiliaryFields();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requested, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        hostBounds = requested == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requested;
        uiOriginX = hostBounds.x;
        uiOriginY = hostBounds.y;
        uiScale = calculateUiScale(hostBounds);
        int logicalWidth = Math.max(1, Math.round(hostBounds.width / uiScale));
        int logicalHeight = Math.max(1, Math.round(hostBounds.height / uiScale));
        ModernMainLayout.Rect logicalBounds = new ModernMainLayout.Rect(0, 0, logicalWidth, logicalHeight);
        int logicalMouseX = hostBounds.contains(mouseX, mouseY) ? toLogicalX(mouseX) : -1;
        int logicalMouseY = hostBounds.contains(mouseX, mouseY) ? toLogicalY(mouseY) : -1;

        ModernUiRenderer.beginClip(hostBounds);
        ModernUiRenderer.pushClipTransform(uiOriginX, uiOriginY, uiScale);
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(uiOriginX, uiOriginY, 0.0F);
            GlStateManager.scale(uiScale, uiScale, 1.0F);
            lastMouseX = logicalMouseX;
            lastMouseY = logicalMouseY;
            idleHoverScrollBars();
            layout(logicalBounds);
            if (pendingFocusCommand != null) {
                String target = pendingFocusCommand;
                pendingFocusCommand = null;
                focusCommand(target);
            }
            clearFieldBounds();
            hoveredTooltip = "";
            hideInactiveFields();
            if (auxiliaryView != AuxiliaryView.NONE) {
                drawAuxiliary(fontRenderer, logicalMouseX, logicalMouseY);
            } else {
                drawMain(fontRenderer, logicalMouseX, logicalMouseY);
            }
        if (modalMode != ModalMode.NONE) {
                if (modalMode == ModalMode.STEP_SETTINGS) {
                    drawStepSettingsModal(fontRenderer, logicalMouseX, logicalMouseY);
                } else {
                    drawModal(fontRenderer, logicalMouseX, logicalMouseY);
                }
            } else if (paletteBounds != null) {
                drawActionPalette(fontRenderer, logicalMouseX, logicalMouseY);
            } else if (menuBounds == null && !menuItems.isEmpty()) {
                drawMenu(fontRenderer, logicalMouseX, logicalMouseY);
            } else if (menuBounds != null) {
                drawMenu(fontRenderer, logicalMouseX, logicalMouseY);
            }
            if (customizeZone != null) {
                drawCustomizePanel(fontRenderer, logicalMouseX, logicalMouseY);
            }
            if (recordingPacketWorkbench != null) {
                drawRecordingPacketOverlay(fontRenderer, logicalMouseX, logicalMouseY);
            }
        } finally {
            GlStateManager.popMatrix();
            ModernUiRenderer.popClipTransform();
            ModernUiRenderer.endClip();
        }
    }

    private String hoveredTooltip = "";

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (hostBounds == null || !hostBounds.contains(mouseX, mouseY)) {
            return false;
        }
        mouseX = toLogicalX(mouseX);
        mouseY = toLogicalY(mouseY);
        if (bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (recordingPacketWorkbench != null) {
            return handleRecordingPacketOverlayClick(mouseX, mouseY, mouseButton);
        }
        if (modalMode != ModalMode.NONE) {
            return handleModalClick(mouseX, mouseY, mouseButton);
        }
        if (customizeZone != null) {
            return handleCustomizeClick(mouseX, mouseY, mouseButton);
        }
        if (paletteBounds != null) {
            return handlePaletteClick(mouseX, mouseY, mouseButton);
        }
        if (!menuItems.isEmpty()) {
            return handleMenuClick(mouseX, mouseY, mouseButton);
        }
        if (mouseButton == 0 && beginHoverScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (auxiliaryView != AuxiliaryView.NONE) {
            return handleAuxiliaryClick(mouseX, mouseY, mouseButton);
        }

        syncEditorFields();
        if (mouseButton == 1) {
            if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
                if (openNavigationContextMenu(mouseX, mouseY)) return true;
            } else if (stepListBounds != null && stepListBounds.contains(mouseX, mouseY)) {
                if (openStepContextMenu(mouseX, mouseY)) return true;
            } else if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)) {
                if (openActionContextMenu(mouseX, mouseY)) return true;
            }
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (headerBackBounds != null && headerBackBounds.contains(mouseX, mouseY)) {
            GuiModernMainScreen menuScreen = getOwningMenuScreen();
            if (menuScreen != null && !DetachedSwingWindowManager.isDetachedScreen(menuScreen)) {
                menuScreen.toggleActiveTabFullscreen();
            } else {
                if (isDirty()) {
                    status("gui.modern.path.wb.u011");
                } else {
                    returnRequested = true;
                }
            }
            return true;
        }
        for (HeaderToolHit hit : headerToolHits) {
            if (hit.bounds != null && hit.bounds.contains(mouseX, mouseY)) {
                runTool(hit.tool);
                return true;
            }
        }
        if (headerRunBounds != null && headerRunBounds.contains(mouseX, mouseY)) {
            runSelected(false);
            return true;
        }
        if (headerMoreBounds != null && headerMoreBounds.contains(mouseX, mouseY)) {
            openMainMenu(mouseX, mouseY);
            return true;
        }
        if (footerUndoBounds != null && footerUndoBounds.contains(mouseX, mouseY)) {
            undo();
            return true;
        }
        if (footerRedoBounds != null && footerRedoBounds.contains(mouseX, mouseY)) {
            redo();
            return true;
        }
        if (footerSaveBounds != null && footerSaveBounds.contains(mouseX, mouseY)) {
            save();
            return true;
        }
        if (footerDiscardBounds != null && footerDiscardBounds.contains(mouseX, mouseY)) {
            discardDraft();
            status("gui.modern.path.wb.u012");
            return true;
        }

        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null) {
            focusField(fieldKey, mouseX, mouseY, mouseButton);
            return true;
        }
        clearFocus();
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }
        if (handleToolStripClick(mouseX, mouseY)) {
            return true;
        }
        if (navMoreBounds != null && navMoreBounds.contains(mouseX, mouseY)) {
            openOverflowMenu(PathWorkbenchToolCatalog.Zone.NAVIGATION, mouseX, mouseY);
            return true;
        }
        if (stepMoreBounds != null && stepMoreBounds.contains(mouseX, mouseY)) {
            openOverflowMenu(PathWorkbenchToolCatalog.Zone.STEP, mouseX, mouseY);
            return true;
        }
        if (sequenceToggleCloseBounds != null && sequenceToggleCloseBounds.contains(mouseX, mouseY)) {
            toggleSequenceSetting(sequenceToggleCloseBounds);
            return true;
        }
        if (sequenceToggleSingleBounds != null && sequenceToggleSingleBounds.contains(mouseX, mouseY)) {
            toggleSequenceSetting(sequenceToggleSingleBounds);
            return true;
        }
        if (sequenceToggleBackgroundBounds != null && sequenceToggleBackgroundBounds.contains(mouseX, mouseY)) {
            toggleSequenceSetting(sequenceToggleBackgroundBounds);
            return true;
        }
        if (sequenceToggleLockBounds != null && sequenceToggleLockBounds.contains(mouseX, mouseY)) {
            toggleSequenceSetting(sequenceToggleLockBounds);
            return true;
        }
        if (sequenceNoteBounds != null && sequenceNoteBounds.contains(mouseX, mouseY)) {
            openSequenceNoteModal();
            return true;
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            return handleNavigationClick(mouseX, mouseY);
        }
        if (editorDividerBounds != null && editorDividerBounds.contains(mouseX, mouseY)) {
            draggingEditorDivider = true;
            return true;
        }
        if (stepBounds != null && stepBounds.contains(mouseX, mouseY)) {
            return handleStepClick(mouseX, mouseY);
        }
        if (actionBounds != null && actionBounds.contains(mouseX, mouseY)) {
            return handleActionClick(mouseX, mouseY);
        }
        return true;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        mouseX = toLogicalX(mouseX);
        mouseY = toLogicalY(mouseY);
        if (recordingPacketWorkbench != null) {
            return recordingPacketWorkbench.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (applyHoverScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (auxiliaryView == AuxiliaryView.LOGS && executionLogView.drag(mouseX, mouseY)) {
            return true;
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR
                && expressionCodeEditor.mouseClickMove(mouseX, mouseY, clickedMouseButton)) {
            return true;
        }
        if (draggingActionWritingHelp && actionWritingHelpBounds != null) {
            int x = clamp(mouseX - actionWritingHelpDragOffsetX, auxContentBounds.x + 4,
                    auxContentBounds.right() - actionWritingHelpBounds.width - 4);
            int y = clamp(mouseY - actionWritingHelpDragOffsetY, auxContentBounds.y + 4,
                    auxContentBounds.bottom() - actionWritingHelpBounds.height - 4);
            actionWritingHelpBounds = new ModernMainLayout.Rect(x, y, actionWritingHelpBounds.width,
                    actionWritingHelpBounds.height);
            return true;
        }
        if (resizingActionWritingHelp && actionWritingHelpBounds != null) {
            int width = clamp(actionWritingHelpResizeStartWidth + mouseX - actionWritingHelpResizeStartX, 420,
                    Math.max(420, auxContentBounds.right() - actionWritingHelpBounds.x - 4));
            int height = clamp(actionWritingHelpResizeStartHeight + mouseY - actionWritingHelpResizeStartY, 220,
                    Math.max(220, auxContentBounds.bottom() - actionWritingHelpBounds.y - 4));
            actionWritingHelpBounds = new ModernMainLayout.Rect(actionWritingHelpBounds.x,
                    actionWritingHelpBounds.y, width, height);
            return true;
        }
        if (draggingNavigationDivider && navigationBounds != null) {
            int total = Math.max(2, bodyBounds.width - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - bodyBounds.x, 150, 240, 96, 96);
            navigationRatio = split.ratio;
            return true;
        }
        if (draggingEditorDivider && editorBodyBounds != null) {
            int total = Math.max(2, editorBodyBounds.width - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - editorBodyBounds.x, 170, 190, 110, 110);
            editorSplitRatio = split.ratio;
            return true;
        }
        if (draggingActionPageDivider && auxContentBounds != null) {
            int total = Math.max(2, auxContentBounds.width - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - auxContentBounds.x, 205, 240, 160, 160);
            actionPageLibraryRatio = split.ratio;
            return true;
        }
        if (draggingRecordingStepDivider && auxContentBounds != null) {
            int total = Math.max(3, auxContentBounds.width - 14);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - (auxContentBounds.x + 10), 170, 250, 160, 160);
            recordingStepRatio = split.ratio;
            return true;
        }
        if (draggingRecordingActionDivider && auxContentBounds != null) {
            int firstTotal = Math.max(3, auxContentBounds.width - 14);
            ModernSplitPane.Split first = ModernSplitPane.calculate(firstTotal, recordingStepRatio,
                    170, 250, 160, 160);
            int secondX = auxContentBounds.x + 10 + first.firstWidth + 7;
            int secondTotal = Math.max(2, first.secondWidth - 7);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(secondTotal,
                    mouseX - secondX, 220, 340, 160, 160);
            recordingActionRatio = split.ratio;
            return true;
        }
        if (draggingPaletteScroll) {
            applyPaletteScrollFromMouse(mouseY);
            return true;
        }
        if (draggingTemplateCategoryDivider && auxContentBounds != null) {
            updateTemplatePaneRatios(mouseX);
            return true;
        }
        if (draggingTemplateListDivider && auxContentBounds != null) {
            updateTemplatePaneRatios(mouseX);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.VARIABLES && variablePanel.mouseClickMove(mouseX, mouseY)) {
            return true;
        }
        if (draggingExpressionEditorDivider && auxContentBounds != null) {
            int total = Math.max(2, auxContentBounds.width - 8);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - auxContentBounds.x, 210, 240, 160, 160);
            expressionEditorSplitRatio = split.ratio;
            return true;
        }
        if (draggingStepIndex >= 0 && selectedSequence != null && isEditableSequence()) {
            stepDropIndex = dropIndex(stepListBounds, STEP_ROW_HEIGHT, selectedSequence.getSteps().size(), mouseY,
                    stepScroll);
            return true;
        }
        if (draggingActionIndex >= 0 && selectedStep != null && canEditStep()) {
            actionDropIndex = dropIndex(actionListBounds, ACTION_ROW_HEIGHT, selectedStep.getActions().size(), mouseY,
                    actionScroll);
            return true;
        }
        if (draggingActionLibraryScroll) {
            applyActionLibraryScrollFromMouse(mouseY);
            return true;
        }
        if (draggingActionPickerScroll) {
            applyActionPickerScrollFromMouse(mouseY);
            return true;
        }
        if (draggingTemplateCategoryScroll) {
            applyTemplateCategoryScrollFromMouse(mouseY);
            return true;
        }
        if (draggingTemplateScroll) {
            applyTemplateScrollFromMouse(mouseY);
            return true;
        }
        if (draggingExpressionTemplateScroll) {
            applyExpressionTemplateScrollFromMouse(mouseY);
            return true;
        }
        if (draggingExpressionPreviewScroll) {
            applyExpressionPreviewScrollFromMouse(mouseY);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && selectedAction != null
                && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)
                && moveChestEditor.mouseClickMove(mouseX, mouseY, clickedMouseButton)) {
            return true;
        }
        if (slotGridMachine.dragging && clickedMouseButton == 0) {
            int index = slotGridIndexAt(mouseX, mouseY);
            if (index >= 0 && index != slotGridMachine.current) {
                slotGridPressMoved = true;
                slotGridMachine.current = index;
                slotGridMachine.focusIndex = index;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        mouseX = toLogicalX(mouseX);
        mouseY = toLogicalY(mouseY);
        if (recordingPacketWorkbench != null) {
            return recordingPacketWorkbench.mouseReleased(mouseX, mouseY, state);
        }
        if (state == 0 && endHoverScrollDrag()) {
            return true;
        }
        if (state == 0 && auxiliaryView == AuxiliaryView.LOGS && executionLogView.release()) {
            return true;
        }
        if (state == 0 && auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR
                && expressionCodeEditor.mouseReleased(mouseX, mouseY)) {
            return true;
        }
        if (state == 0 && (draggingNavigationDivider || draggingEditorDivider || draggingActionPageDivider
                || draggingRecordingStepDivider || draggingRecordingActionDivider
                || draggingPaletteScroll
                || draggingExpressionEditorDivider || draggingTemplateCategoryDivider || draggingTemplateListDivider)) {
            persistLayoutRatios();
            draggingNavigationDivider = false;
            draggingEditorDivider = false;
            draggingActionPageDivider = false;
            draggingRecordingStepDivider = false;
            draggingRecordingActionDivider = false;
            draggingPaletteScroll = false;
            draggingExpressionEditorDivider = false;
            draggingTemplateCategoryDivider = false;
            draggingTemplateListDivider = false;
            return true;
        }
        if (state == 0 && (draggingActionWritingHelp || resizingActionWritingHelp)) {
            draggingActionWritingHelp = false;
            resizingActionWritingHelp = false;
            return true;
        }
        if (state == 0 && auxiliaryView == AuxiliaryView.VARIABLES && variablePanel.mouseReleased()) {
            return true;
        }
        if (state == 0 && draggingStepIndex >= 0) {
            finishStepDrag();
            return true;
        }
        if (state == 0 && draggingActionIndex >= 0) {
            finishActionDrag();
            return true;
        }
        if (state == 0 && draggingActionLibraryScroll) {
            draggingActionLibraryScroll = false;
            return true;
        }
        if (state == 0 && draggingActionPickerScroll) {
            draggingActionPickerScroll = false;
            return true;
        }
        if (state == 0 && draggingTemplateCategoryScroll) {
            draggingTemplateCategoryScroll = false;
            return true;
        }
        if (state == 0 && draggingTemplateScroll) {
            draggingTemplateScroll = false;
            return true;
        }
        if (state == 0 && draggingExpressionTemplateScroll) {
            draggingExpressionTemplateScroll = false;
            return true;
        }
        if (state == 0 && draggingExpressionPreviewScroll) {
            draggingExpressionPreviewScroll = false;
            return true;
        }
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && selectedAction != null
                && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)
                && moveChestEditor.mouseReleased(mouseX, mouseY, state)) {
            return true;
        }
        if (state == 0 && slotGridMachine.dragging) {
            commitSlotGridPress();
            return true;
        }
        return false;
    }

    private void persistLayoutRatios() {
        MainUiLayoutManager.setModernSplitRatio("path.workbench.navigation", navigationRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.editor", editorSplitRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.action_library", actionPageLibraryRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.expression_editor", expressionEditorSplitRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.template_category", templateCategoryRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.template_list", templateListRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.recording_step", recordingStepRatio);
        MainUiLayoutManager.setModernSplitRatio("path.workbench.recording_action", recordingActionRatio);
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (recordingPacketWorkbench != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeRecordingPacketOverlay();
                return true;
            }
            return recordingPacketWorkbench.keyTyped(typedChar, keyCode);
        }
        if (modalMode != ModalMode.NONE) {
            return handleModalKey(typedChar, keyCode);
        }
        if (paletteBounds != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closePalette();
                return true;
            }
            ModernTextField paletteSearch = fields.get("palette.search");
            if (paletteSearch != null && paletteSearch.isFocused()) {
                paletteSearch.textboxKeyTyped(typedChar, keyCode);
                paletteScroll = 0;
                return true;
            }
        }
        if (!menuItems.isEmpty()) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeMenu();
                return true;
            }
            return true;
        }
        if (customizeZone != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeCustomizePanel();
                return true;
            }
            return true;
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR && expressionCodeEditor.isFocused()
                && expressionCodeEditor.keyTyped(typedChar, keyCode)) {
            return true;
        }
        if (isControlDown() && keyCode == Keyboard.KEY_Z) {
            if (isShiftDown()) redo(); else undo();
            return true;
        }
        if (isControlDown() && keyCode == Keyboard.KEY_Y) {
            redo();
            return true;
        }
        if (isControlDown() && keyCode == Keyboard.KEY_S) {
            save();
            return true;
        }
        if (auxiliaryView != AuxiliaryView.NONE) {
            if (auxiliaryView == AuxiliaryView.RECORDING && !isTextInputFocused()) {
                if (keyCode == Keyboard.KEY_N && !PathRecordingManager.isRecording()) {
                    runRecordingTool(PathWorkbenchToolCatalog.ToolId.RECORDING_START);
                    return true;
                }
                if (keyCode == Keyboard.KEY_M && PathRecordingManager.isRecording()) {
                    runRecordingTool(PathWorkbenchToolCatalog.ToolId.RECORDING_PAUSE);
                    return true;
                }
                if (keyCode == Keyboard.KEY_DELETE) {
                    if (listFocus == ListFocus.ACTION) deleteAction();
                    else if (listFocus == ListFocus.STEP) deleteStep();
                    return true;
                }
                if (isControlDown() && (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN)) {
                    int direction = keyCode == Keyboard.KEY_UP ? -1 : 1;
                    if (listFocus == ListFocus.ACTION) moveAction(direction); else moveStep(direction);
                    return true;
                }
            }
            if (actionPickerMode != ActionPickerMode.NONE) {
                if (keyCode == Keyboard.KEY_ESCAPE) {
                    closeActionPicker();
                    return true;
                }
                ModernTextField search = fields.get("action.picker.search");
                if (search != null && search.isFocused()) {
                    boolean handled = search.textboxKeyTyped(typedChar, keyCode);
                    if (handled) actionPickerScroll = 0;
                    return handled;
                }
                return true;
            }
            if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && selectedAction != null
                    && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)
                    && !isTextInputFocused()
                    && moveChestEditor.keyTyped(keyCode)) {
                return true;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                if (recordingCategoryPickerOpen) {
                    closeRecordingCategoryPicker();
                    return true;
                }
                if (draggingRecordingStepDivider) {
                    draggingRecordingStepDivider = false;
                    return true;
                }
                if (draggingRecordingActionDivider) {
                    draggingRecordingActionDivider = false;
                    return true;
                }
                if (draggingActionPageDivider) {
                    actionPageLibraryRatio = actionPageLibraryDragStartRatio;
                    draggingActionPageDivider = false;
                    return true;
                }
                if (draggingExpressionEditorDivider) {
                    expressionEditorSplitRatio = expressionEditorDragStartRatio;
                    draggingExpressionEditorDivider = false;
                    return true;
                }
                if (draggingTemplateCategoryDivider) {
                    templateCategoryRatio = templateCategoryDragStartRatio;
                    draggingTemplateCategoryDivider = false;
                    return true;
                }
                if (draggingTemplateListDivider) {
                    templateListRatio = templateListDragStartRatio;
                    draggingTemplateListDivider = false;
                    return true;
                }
                if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && selectedAction != null
                        && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)
                        && moveChestEditor.keyTyped(keyCode)) {
                    return true;
                }
                if (auxiliaryView == AuxiliaryView.LOGS && executionLogView.key(typedChar, keyCode)) {
                    return true;
                }
                if (auxiliaryView == AuxiliaryView.VARIABLES && variablePanel.handleEscape()) {
                    return true;
                }
                if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR) {
                    returnToActionEditor();
                } else if (auxiliaryView == AuxiliaryView.ACTION_EDITOR) {
                    returnToPathManager();
                } else if (auxiliaryView == AuxiliaryView.RECORDING) {
                    closeAuxiliary();
                } else {
                    auxiliaryView = AuxiliaryView.NONE;
                }
                clearFocus();
                return true;
            }
            if (auxiliaryView == AuxiliaryView.VARIABLES
                    && (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER)
                    && "variable.rename".equals(focusedFieldKey)) {
                return variablePanel.handleEnter();
            }
            return typeFocusedField(typedChar, keyCode);
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (draggingNavigationDivider || draggingEditorDivider) {
                draggingNavigationDivider = false;
                draggingEditorDivider = false;
                return true;
            }
            return false;
        }
        if (keyCode == Keyboard.KEY_F && isControlDown()) {
            focusField("search", navSearchBounds == null ? 0 : navSearchBounds.x,
                    navSearchBounds == null ? 0 : navSearchBounds.y, 0);
            return true;
        }
        if (!isTextInputFocused() && handleWorkbenchShortcut(keyCode)) {
            return true;
        }
        return typeFocusedField(typedChar, keyCode);
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        int physicalMouseX = uiOriginX + Math.round(lastMouseX * uiScale);
        int physicalMouseY = uiOriginY + Math.round(lastMouseY * uiScale);
        return handleMouseWheel(wheel, physicalMouseX, physicalMouseY);
    }

    private int lastMouseX;
    private int lastMouseY;

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        mouseX = toLogicalX(mouseX);
        mouseY = toLogicalY(mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        if (recordingPacketWorkbench != null) {
            return recordingPacketWorkbench.handleMouseWheel(wheel, mouseX, mouseY);
        }
        if (wheel == 0) {
            return false;
        }
        if (modalMode != ModalMode.NONE) {
            if (modalMode == ModalMode.STEP_SETTINGS && stepTargetPickerOpen) {
                if (stepTargetNavigationBounds != null && stepTargetNavigationBounds.contains(mouseX, mouseY)) {
                    stepTargetNavigationScroll = clamp(stepTargetNavigationScroll + (wheel > 0 ? -24 : 24), 0,
                            stepTargetNavigationMaxScroll);
                } else if (stepTargetDetailsBounds != null && stepTargetDetailsBounds.contains(mouseX, mouseY)) {
                    stepTargetDetailsScroll = clamp(stepTargetDetailsScroll + (wheel > 0 ? -30 : 30), 0,
                            stepTargetDetailsMaxScroll);
                }
            }
            if (modalMode == ModalMode.MOVE_SEQUENCE) {
                if (moveCategoriesBounds != null && moveCategoriesBounds.contains(mouseX, mouseY)) {
                    moveCategoriesScroll = clamp(moveCategoriesScroll + (wheel > 0 ? -32 : 32), 0, moveCategoriesMaxScroll);
                } else if (moveSubCategoriesBounds != null && moveSubCategoriesBounds.contains(mouseX, mouseY)) {
                    moveSubCategoriesScroll = clamp(moveSubCategoriesScroll + (wheel > 0 ? -32 : 32), 0, moveSubCategoriesMaxScroll);
                }
            }
            return true;
        }
        if (customizeZone != null) {
            return true;
        }
        if (paletteBounds != null) {
            if (paletteBounds.contains(mouseX, mouseY)) {
                paletteScroll = clamp(paletteScroll + (wheel > 0 ? -24 : 24), 0, paletteMaxScroll);
                return true;
            }
            return true;
        }
        if (!menuItems.isEmpty()) {
            return true;
        }
        if (auxiliaryView != AuxiliaryView.NONE) {
            if (actionPickerMode != ActionPickerMode.NONE) {
                if (actionPickerListBounds != null && actionPickerListBounds.contains(mouseX, mouseY)) {
                    actionPickerScroll = clamp(actionPickerScroll + (wheel > 0 ? -1 : 1), 0,
                            actionPickerMaxScroll);
                }
                return true;
            }
            return handleAuxiliaryWheel(wheel, mouseX, mouseY);
        }
        if (navigationBounds != null && navigationBounds.contains(mouseX, mouseY)) {
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -(NAV_SEQUENCE_HEIGHT + ModernTreeGuide.GAP)
                    : NAV_SEQUENCE_HEIGHT + ModernTreeGuide.GAP), 0, navigationMaxScroll);
            return true;
        }
        if (stepListBounds != null && stepListBounds.contains(mouseX, mouseY)) {
            stepScroll = clamp(stepScroll + (wheel > 0 ? -STEP_ROW_HEIGHT : STEP_ROW_HEIGHT), 0, stepMaxScroll);
            return true;
        }
        if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)) {
            actionScroll = clamp(actionScroll + (wheel > 0 ? -ACTION_ROW_HEIGHT : ACTION_ROW_HEIGHT), 0, actionMaxScroll);
            return true;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (modalMode != ModalMode.NONE) {
            modalMode = ModalMode.NONE;
            clearFocus();
            return true;
        }
        if (customizeZone != null) {
            closeCustomizePanel();
            return true;
        }
        if (paletteBounds != null) {
            closePalette();
            return true;
        }
        if (!menuItems.isEmpty()) {
            closeMenu();
            return true;
        }
        if (auxiliaryView != AuxiliaryView.NONE) {
            if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR
                    && expressionCodeEditor.keyTyped('\0', Keyboard.KEY_ESCAPE)) {
                return true;
            }
            if (recordingCategoryPickerOpen) {
                closeRecordingCategoryPicker();
                return true;
            }
            if (actionPickerMode != ActionPickerMode.NONE) {
                closeActionPicker();
                return true;
            }
            if (auxiliaryView == AuxiliaryView.VARIABLES && variablePanel.handleEscape()) {
                return true;
            }
            if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR) {
                returnToActionEditor();
            } else if (auxiliaryView == AuxiliaryView.ACTION_EDITOR) {
                returnToPathManager();
            } else if (auxiliaryView == AuxiliaryView.RECORDING) {
                closeAuxiliary();
            } else {
                auxiliaryView = AuxiliaryView.NONE;
            }
            clearFocus();
            return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR && expressionCodeEditor.isFocused()) {
            return true;
        }
        ModernTextField field = focusedFieldKey == null ? null : fields.get(focusedFieldKey);
        return field != null && field.isFocused() && field.getVisible();
    }

    @Override
    public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        int logicalX = toLogicalX(mouseX);
        int logicalY = toLogicalY(mouseY);
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR && expressionCodeEditorBounds != null
                && expressionCodeEditorBounds.contains(logicalX, logicalY)) {
            return;
        }
        if (fieldAt(logicalX, logicalY) == null) {
            clearFocus();
        }
    }

    @Override
    public void focusCommand(String requestedCommand) {
        String normalized = safe(requestedCommand).trim();
        if (normalized.startsWith("path:") || normalized.startsWith("custom_path:")) {
            String name = normalized.substring(normalized.indexOf(':') + 1).trim();
            if (!name.isEmpty()) {
                adoptPersistedSequence(name);
                selectSequenceByName(name);
            }
            auxiliaryView = AuxiliaryView.NONE;
        } else if ("path_validation".equals(normalized)) {
            openValidation();
        } else if ("recording".equals(normalized)) {
            openRecording();
        } else if ("execution_log".equals(normalized)) {
            openLogs();
        } else if ("action_templates".equals(normalized) || "template_library".equals(normalized)) {
            openTemplates();
        } else if ("action_variables".equals(normalized)) {
            openVariables();
        } else if ("action_editor".equals(normalized)) {
            auxiliaryView = AuxiliaryView.ACTION_EDITOR;
            clearFocus();
        } else if ("expression_editor".equals(normalized)) {
            auxiliaryView = AuxiliaryView.EXPRESSION_EDITOR;
            clearFocus();
        } else if ("category_manager".equals(normalized)) {
            auxiliaryView = AuxiliaryView.NONE;
            if (navigationBounds == null) {
                pendingFocusCommand = normalized;
            } else {
                openCategoryMenu(navigationBounds.right() - 12, navigationBounds.y + 28);
            }
        } else if ("path_manager".equals(normalized) || "sequence_selector".equals(normalized)
                || "reload_paths".equals(normalized)) {
            auxiliaryView = AuxiliaryView.NONE;
            closeMenu();
            clearFocus();
        } else if ("sequence_trigger_rules".equals(normalized)) {
            openTriggerRules();
        }
    }

    @Override
    public boolean consumeReturnRequest() {
        boolean result = returnRequested;
        returnRequested = false;
        return result;
    }

    @Override
    public void save() {
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            syncEditorFields();
            saveRecordedSequence();
            return;
        }
        if (templateDirty) {
            syncAuxiliaryFields();
            LegacyActionTemplateManager.saveTemplateModels(templateModels);
            templateDirty = false;
            status("gui.modern.path.wb.u013");
        }
        if (!dirty && auxiliaryView != AuxiliaryView.NONE) {
            return;
        }
        syncEditorFields();
        List<Issue> issues = PathConfigValidator.validateSequences(sequences);
        int errors = countErrors(issues);
        if (errors > 0) {
            validationIssues = new ArrayList<>(issues);
            validationScroll = 0;
            auxiliaryView = AuxiliaryView.VALIDATION;
            clearFocus();
            status(tr("gui.modern.path.wb.fmt.save_errors", String.valueOf(errors)));
            return;
        }
        commitDraft();
    }

    @Override
    public boolean isDirty() {
        return dirty || templateDirty;
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return hostBounds != null && hostBounds.contains(mouseX, mouseY)
                && bounds != null && bounds.contains(toLogicalX(mouseX), toLogicalY(mouseY));
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        return hoveredTooltip == null ? "" : hoveredTooltip;
    }

    @Override
    public void discardDraft() {
        if (PathRecordingManager.isRecording() || !PathRecordingManager.getRecordedSteps().isEmpty()) {
            PathRecordingManager.stopAndClearRecording();
            PathRecordingManager.getRecordedSteps().clear();
        }
        if (!originalSequences.isEmpty() || initialized) {
            sequences = copySequences(originalSequences);
            categories = new ArrayList<>(originalCategories);
            subCategories = copySubCategories(originalSubCategories);
            hiddenCategories = new LinkedHashMap<>(originalHiddenCategories);
            dirty = false;
            sortCategoriesOnSave = false;
            undoHistory.clear();
            redoHistory.clear();
            removedPersistentActions.clear();
            selectSequenceByName(selectedSequenceName);
        }
        if (templateDirty) {
            LegacyActionTemplateManager.reloadTemplates();
            templateModels = LegacyActionTemplateManager.getTemplateModels();
            templateDirty = false;
        }
        modalMode = ModalMode.NONE;
        closeMenu();
        closePalette();
        auxiliaryView = AuxiliaryView.NONE;
        clearFocus();
    }

    private void loadDraft() {
        MainUiLayoutManager.ensureLoaded();
        originalHiddenCategories.clear();
        sortCategoriesOnSave = false;
        sequences = copySequences(PathSequenceManager.getAllSequences());
        originalSequences = copySequences(sequences);
        categories = new ArrayList<>(PathSequenceManager.getAllCategories());
        for (PathSequence sequence : sequences) {
            if (sequence != null) {
                addCategoryLocal(sequence.getCategory());
            }
        }
        if (categories.isEmpty()) {
            categories.add(defaultCategory());
        }
        subCategories = collectSubCategories(categories, sequences);
        originalCategories = new ArrayList<>(categories);
        originalSubCategories = copySubCategories(subCategories);
        for (String category : categories) {
            originalHiddenCategories.put(category, PathSequenceManager.isCategoryHidden(category));
        }
        hiddenCategories = new LinkedHashMap<>(originalHiddenCategories);
        selectedCategory = preferredCategory();
        selectedSubCategory = "";
        selectedSequenceName = "";
        selectInitialSequence();
        dirty = false;
        templateModels = LegacyActionTemplateManager.getTemplateModels();
    }

    private String preferredCategory() {
        String requested = command.startsWith("path:") || command.startsWith("custom_path:") ? "" : command;
        if (categories.contains(requested) && !isBuiltinCategory(requested)) {
            return requested;
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null && !safe(sequence.getCategory()).trim().isEmpty()) {
                return sequence.getCategory().trim();
            }
        }
        return categories.isEmpty() ? defaultCategory() : categories.get(0);
    }

    private void selectInitialSequence() {
        String requested = "";
        if (command.startsWith("path:") || command.startsWith("custom_path:")) {
            requested = command.substring(command.indexOf(':') + 1).trim();
        }
        if (!requested.isEmpty() && findSequence(requested) != null) {
            selectSequenceByName(requested);
            return;
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null) {
                if (safe(sequence.getCategory()).equals(selectedCategory)) {
                    selectSequenceByName(sequence.getName());
                    return;
                }
            }
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null) {
                selectSequenceByName(sequence.getName());
                return;
            }
        }
        refreshSelection();
    }

    private float calculateUiScale(ModernMainLayout.Rect host) {
        if (host == null || host.width <= 0 || host.height <= 0) {
            return 1.0F;
        }
        // The parent screen lays out in logical coordinates after applying its
        // global zoom.  Using those reduced logical dimensions directly here
        // makes the path manager add a second, inverse zoom: Ctrl + causes the
        // host to look smaller, so this fit scale shrinks and cancels the
        // parent's enlargement.  Calculate the fit against the host's
        // rendered size instead, while keeping this tab's local scale capped at
        // 100%.  The same compensation is needed for the detached Swing view,
        // whose projection also converts a zoomed native viewport to logical
        // coordinates before this method is called.
        float parentScale = DetachedSwingWindowManager.isDetached()
                ? MainUiLayoutManager.getDetachedUiScalePercent() / 100.0F
                : MainUiLayoutManager.getModernUiScalePercent() / 100.0F;
        parentScale = Math.max(1.0F, parentScale);
        float renderedWidth = host.width * parentScale;
        float renderedHeight = host.height * parentScale;
        return Math.min(1.0F, Math.min(renderedWidth / MIN_LOGICAL_WIDTH,
                renderedHeight / MIN_LOGICAL_HEIGHT));
    }

    private int toLogicalX(int mouseX) {
        return Math.round((mouseX - uiOriginX) / Math.max(0.0001F, uiScale));
    }

    private int toLogicalY(int mouseY) {
        return Math.round((mouseY - uiOriginY) / Math.max(0.0001F, uiScale));
    }

    private void layout(ModernMainLayout.Rect requested) {
        ModernMainLayout.Rect source = requested == null ? new ModernMainLayout.Rect(0, 0, 1, 1) : requested;
        bounds = inset(source, 6);
        headerBounds = new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width,
                Math.min(HEADER_HEIGHT, Math.max(1, bounds.height)));
        footerBounds = new ModernMainLayout.Rect(bounds.x, Math.max(bounds.y, bounds.bottom() - FOOTER_HEIGHT),
                bounds.width, Math.min(FOOTER_HEIGHT, Math.max(1, bounds.height)));
        bodyBounds = new ModernMainLayout.Rect(bounds.x, headerBounds.bottom(), bounds.width,
                Math.max(1, footerBounds.y - headerBounds.bottom() - 6));

        int total = Math.max(2, bodyBounds.width - 8);
        ModernSplitPane.Split navSplit = ModernSplitPane.calculate(total, navigationRatio, 150, 240, 96, 96);
        navigationRatio = navSplit.ratio;
        navigationBounds = new ModernMainLayout.Rect(bodyBounds.x, bodyBounds.y, navSplit.firstWidth,
                bodyBounds.height);
        navigationDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 8,
                bodyBounds.y, bodyBounds.height);
        editorBounds = new ModernMainLayout.Rect(navigationBounds.right() + 8, bodyBounds.y,
                Math.max(1, navSplit.secondWidth), bodyBounds.height);
        navFooterHeight = PathWorkbenchToolCatalog.get().stripHeight(PathWorkbenchToolCatalog.Zone.NAVIGATION,
                Math.max(1, navigationBounds.width - 28), false);
        if (!navToolsExpanded) {
            navFooterHeight = TOOL_STRIP_HEADER_HEIGHT;
        }
        navFooterHeight = Math.min(Math.max(TOOL_STRIP_HEADER_HEIGHT, navFooterHeight),
                Math.max(TOOL_STRIP_HEADER_HEIGHT, navigationBounds.height / 2));
        // Anchor tools inside the panel first; the scrolling tree uses the remainder.
        navToolsBounds = new ModernMainLayout.Rect(navigationBounds.x + 6,
                navigationBounds.bottom() - navFooterHeight - 6,
                Math.max(1, navigationBounds.width - 12), navFooterHeight);
        navigationContentBounds = new ModernMainLayout.Rect(navigationBounds.x + 6, navigationBounds.y + 52,
                // Leave exactly the scrollbar track width at the right edge.  The
                // previous extra inset made nested sequence rows look truncated.
                Math.max(1, navigationBounds.width - 20),
                Math.max(1, navToolsBounds.y - 4 - (navigationBounds.y + 52)));
        navSearchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8, navigationBounds.y + 26,
                Math.max(1, navigationBounds.width - 16), 20);

        editorBodyBounds = new ModernMainLayout.Rect(editorBounds.x + 6, editorBounds.y + 6,
                Math.max(1, editorBounds.width - 12), Math.max(1, editorBounds.height - 12));
        int editorTotal = Math.max(2, editorBodyBounds.width - 8);
        ModernSplitPane.Split editorSplit = ModernSplitPane.calculate(editorTotal, editorSplitRatio, 170, 190, 110,
                110);
        editorSplitRatio = editorSplit.ratio;
        stepBounds = new ModernMainLayout.Rect(editorBodyBounds.x, editorBodyBounds.y, editorSplit.firstWidth,
                editorBodyBounds.height);
        editorDividerBounds = ModernSplitPane.verticalDividerBounds(stepBounds.x, stepBounds.width, 8,
                editorBodyBounds.y, editorBodyBounds.height);
        actionBounds = new ModernMainLayout.Rect(stepBounds.right() + 8, editorBodyBounds.y,
                Math.max(1, editorSplit.secondWidth), editorBodyBounds.height);

        // Keep the timeline dominant. Infrequent retry and failure options live in a modal.
        int stepToolHeight = PathWorkbenchToolCatalog.get().stripHeight(PathWorkbenchToolCatalog.Zone.STEP,
                Math.max(1, stepBounds.width - 24), true);
        stepToolHeight = stepToolsExpanded
                ? Math.min(Math.max(48, stepToolHeight), Math.max(48, stepBounds.height / 2))
                : TOOL_STRIP_HEADER_HEIGHT;
        stepToolsBounds = new ModernMainLayout.Rect(stepBounds.x + 4,
                Math.max(stepBounds.y + 30, stepBounds.bottom() - stepToolHeight - 4),
                Math.max(1, stepBounds.width - 8), stepToolHeight);
        int stepOptionsHeight = stepOptionsExpanded ? 74 : 25;
        stepListBounds = new ModernMainLayout.Rect(stepBounds.x + 5, stepBounds.y + 28 + stepOptionsHeight,
                Math.max(1, stepBounds.width - 10),
                Math.max(1, stepToolsBounds.y - stepBounds.y - 34 - stepOptionsHeight));
        int actionToolHeight = PathWorkbenchToolCatalog.get().stripHeight(PathWorkbenchToolCatalog.Zone.ACTION,
                Math.max(1, actionBounds.width - 16), false);
        actionToolHeight = actionToolsExpanded
                ? Math.min(Math.max(TOOL_STRIP_HEADER_HEIGHT, actionToolHeight),
                        Math.max(TOOL_STRIP_HEADER_HEIGHT, actionBounds.height / 3))
                : TOOL_STRIP_HEADER_HEIGHT;
        actionToolsBounds = new ModernMainLayout.Rect(actionBounds.x + 4,
                Math.max(actionBounds.y + 30, actionBounds.bottom() - actionToolHeight - 4),
                Math.max(1, actionBounds.width - 8), actionToolHeight);
        actionListBounds = new ModernMainLayout.Rect(actionBounds.x + 5, actionBounds.y + 28,
                Math.max(1, actionBounds.width - 10),
                Math.max(1, actionToolsBounds.y - actionBounds.y - 34));

        headerBackBounds = new ModernMainLayout.Rect(headerBounds.x + 8, headerBounds.y + 7, 28, 26);
        layoutHeaderActions();
        footerUndoBounds = new ModernMainLayout.Rect(footerBounds.x + 8, footerBounds.y + 6, 42, 20);
        footerRedoBounds = new ModernMainLayout.Rect(footerUndoBounds.right() + 4, footerBounds.y + 6, 42, 20);
        int saveWidth = Math.max(46, com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.preferredWidth(
                fontRenderer, "gui.modern.path.wb.u091", 16));
        footerSaveBounds = new ModernMainLayout.Rect(footerBounds.right() - saveWidth - 8,
                footerBounds.y + 6, saveWidth, 20);
        footerDiscardBounds = new ModernMainLayout.Rect(footerSaveBounds.x - 50,
                footerBounds.y + 6, 46, 20);

    }

    private void layoutHeaderActions() {
        headerToolHits.clear();
        headerMoreBounds = new ModernMainLayout.Rect(Math.max(headerBounds.x + 1, headerBounds.right() - 31),
                headerBounds.y + 9, 22, 22);
        headerRunBounds = new ModernMainLayout.Rect(Math.max(headerBounds.x + 1, headerMoreBounds.x - 62),
                headerBounds.y + 9, 56, 20);
        List<PathWorkbenchToolCatalog.ToolId> pinned = PathWorkbenchToolCatalog.get()
                .pinned(PathWorkbenchToolCatalog.Zone.HEADER);
        if (pinned.isEmpty()) {
            return;
        }
        int gap = 6;
        int minX = headerBackBounds.right() + 8;
        int available = Math.max(40, headerRunBounds.x - minX - gap);
        int[] widths = new int[pinned.size()];
        int total = 0;
        for (int i = 0; i < pinned.size(); i++) {
            widths[i] = headerButtonWidth(tr(pinned.get(i).headerLabel));
            total += widths[i] + (i > 0 ? gap : 0);
        }
        if (total > available && total > 0) {
            float scale = (float) available / (float) total;
            for (int i = 0; i < widths.length; i++) {
                widths[i] = Math.max(28, Math.round(widths[i] * scale));
            }
        }
        int x = headerRunBounds.x;
        for (int i = pinned.size() - 1; i >= 0; i--) {
            x -= widths[i] + gap;
            ModernMainLayout.Rect rect = new ModernMainLayout.Rect(Math.max(minX, x), headerBounds.y + 9, widths[i], 20);
            headerToolHits.add(0, new HeaderToolHit(pinned.get(i), rect));
        }
    }

    private int headerButtonWidth(String label) {
        String text = label == null ? "" : label;
        int textWidth = fontRenderer != null ? fontRenderer.getStringWidth(text) : Math.max(18, text.length() * 9);
        return Math.max(40, Math.min(78, textWidth + 14));
    }

    private int headerToolsLeft() {
        int left = headerMoreBounds == null ? headerBounds.right() : headerMoreBounds.x;
        if (headerRunBounds != null) {
            left = Math.min(left, headerRunBounds.x);
        }
        for (HeaderToolHit hit : headerToolHits) {
            if (hit.bounds != null) {
                left = Math.min(left, hit.bounds.x);
            }
        }
        return left;
    }

    private String headerToolTooltip(PathWorkbenchToolCatalog.ToolId tool) {
        if (tool == null) {
            return "";
        }
        String label = tr(tool.label);
        String description = tr(tool.description);
        if (description.isEmpty()) {
            return label;
        }
        if (label.isEmpty() || label.equals(description)) {
            return description;
        }
        return label + "\n" + description;
    }

    private void drawMain(FontRenderer font, int mouseX, int mouseY) {
        toolHits.clear();
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        drawHeader(font, mouseX, mouseY);
        drawNavigation(font, mouseX, mouseY);
        drawStepPane(font, mouseX, mouseY);
        drawActionPane(font, mouseX, mouseY);
        drawFooter(font, mouseX, mouseY);
    }

    private void drawHeader(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawRoundedRect(headerBounds.x + 1, headerBounds.y + 1,
                Math.max(1, headerBounds.width - 2), Math.max(1, headerBounds.height - 2), 6,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawDivider(headerBounds.x + 10, headerBounds.bottom(), Math.max(1, headerBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        drawBackGlyph(headerBackBounds, mouseX, mouseY);
        String current = selectedSequence == null ? tr("gui.modern.path.wb.u014")
                : tr("gui.modern.path.wb.fmt.editing", safe(selectedSequence.getName()));
        int titleWidth = Math.max(80, headerToolsLeft() - headerBounds.x - 50);
        ModernUiRenderer.drawText(font, title, headerBounds.x + 46, headerBounds.y + 7, ModernUiRenderer.TEXT,
                titleWidth);
        ModernUiRenderer.drawText(font, tr(dirty ? "gui.modern.path.wb.fmt.title_draft" : "gui.modern.path.wb.fmt.title_synced", current), headerBounds.x + 46,
                headerBounds.y + 22, dirty ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT,
                titleWidth);
        for (HeaderToolHit hit : headerToolHits) {
            if (hit.tool == null) {
                continue;
            }
            drawButton(font, hit.bounds, hit.tool.headerLabel, false, false, true, mouseX, mouseY);
            if (hit.bounds != null && hit.bounds.contains(mouseX, mouseY)) {
                hoveredTooltip = headerToolTooltip(hit.tool);
            }
        }
        drawButton(font, headerRunBounds, "gui.modern.path.wb.u015", true, false, selectedSequence != null, mouseX, mouseY);
        drawIconButton(headerMoreBounds, "...", mouseX, mouseY, "gui.modern.path.wb.u016");
        if (headerBounds.width > 180 && headerMoreBounds != null && headerMoreBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.wb.u017";
        }
    }

    private void drawNavigation(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        List<PathWorkbenchToolCatalog.ToolId> navOverflow = PathWorkbenchToolCatalog.get()
                .overflow(PathWorkbenchToolCatalog.Zone.NAVIGATION);
        navMoreBounds = navOverflow.isEmpty() ? null
                : new ModernMainLayout.Rect(Math.max(navigationBounds.x + 1, navigationBounds.right() - 26),
                        navigationBounds.y + 6, 18, 18);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u018", navigationBounds.x + 9, navigationBounds.y + 8,
                ModernUiRenderer.TEXT, Math.max(40, (navMoreBounds == null ? navigationBounds.right()
                        : navMoreBounds.x) - navigationBounds.x - 12));
        if (navMoreBounds != null) {
            drawIconButton(navMoreBounds, "...", mouseX, mouseY, "gui.modern.path.wb.u019");
        }
        drawField(font, "search", navSearchBounds, true, "gui.modern.path.wb.u020", mouseX, mouseY);
        navHits.clear();
        String query = query("search");
        ModernUiRenderer.beginClip(navigationContentBounds);
        int y = navigationContentBounds.y - navigationScroll;
        int originX = navigationContentBounds.x;
        int contentWidth = navigationContentBounds.width;
        for (String category : categories) {
            if (category == null || category.trim().isEmpty()) {
                continue;
            }
            List<PathSequence> categorySequences = allSequencesForCategory(category);
            boolean categoryMatches = query.isEmpty() || contains(category, query) || hasMatchingSequence(categorySequences, query);
            if (!categoryMatches) {
                continue;
            }
            ModernMainLayout.Rect categoryRect = ModernTreeGuide.row(originX, contentWidth, y, NAV_CATEGORY_HEIGHT, 0);
            boolean collapsed = collapsedCategories.contains(category) && query.isEmpty();
            drawCategoryRow(font, categoryRect, category, collapsed, mouseX, mouseY);
            ModernMainLayout.Rect toggle = new ModernMainLayout.Rect(categoryRect.x + 4, categoryRect.y + 4, 13, 13);
            navHits.add(new NavHit(NavKind.CATEGORY, category, "", null, categoryRect, toggle));
            y = ModernTreeGuide.nextY(y, NAV_CATEGORY_HEIGHT);
            if (collapsed) {
                continue;
            }
            List<String> subs = subCategoriesFor(category);
            if (subs != null) {
                for (String sub : subs) {
                    if (sub == null || sub.trim().isEmpty()) {
                        continue;
                    }
                    List<PathSequence> group = sequencesForCategory(category, sub);
                    if (!query.isEmpty() && !contains(sub, query) && !hasMatchingSequence(group, query)) {
                        continue;
                    }
                    String key = groupKey(category, sub);
                    boolean groupCollapsed = collapsedSubGroups.contains(key) && query.isEmpty();
                    ModernMainLayout.Rect subRect = ModernTreeGuide.row(originX, contentWidth, y, NAV_SUB_HEIGHT, 1);
                    drawSubCategoryRow(font, subRect, sub, group.size(), groupCollapsed, mouseX, mouseY);
                    ModernTreeGuide.drawChild(originX, 0, categoryRect, subRect);
                    ModernMainLayout.Rect subToggle = new ModernMainLayout.Rect(subRect.x + 4, subRect.y + 4, 12, 12);
                    navHits.add(new NavHit(NavKind.SUBCATEGORY, category, sub, null, subRect, subToggle));
                    y = ModernTreeGuide.nextY(y, NAV_SUB_HEIGHT);
                    if (!groupCollapsed) {
                        for (PathSequence sequence : group) {
                            if (query.isEmpty() || matches(sequence, query)) {
                                ModernMainLayout.Rect sequenceRect = ModernTreeGuide.row(originX, contentWidth, y,
                                        NAV_SEQUENCE_HEIGHT, 2);
                                drawSequenceNavRow(font, sequenceRect, sequence, mouseX, mouseY);
                                ModernTreeGuide.drawChild(originX, 1, subRect, sequenceRect);
                                navHits.add(new NavHit(NavKind.SEQUENCE, category, sub, sequence, sequenceRect, null));
                                y = ModernTreeGuide.nextY(y, NAV_SEQUENCE_HEIGHT);
                            }
                        }
                    }
                }
            }
            List<PathSequence> ungrouped = sequencesForCategory(category, "");
            if (!ungrouped.isEmpty()) {
                String key = groupKey(category, "");
                boolean groupCollapsed = collapsedSubGroups.contains(key) && query.isEmpty();
                ModernMainLayout.Rect subRect = ModernTreeGuide.row(originX, contentWidth, y, NAV_SUB_HEIGHT, 1);
                drawSubCategoryRow(font, subRect, "gui.modern.path.wb.u021", ungrouped.size(), groupCollapsed, mouseX, mouseY);
                ModernTreeGuide.drawChild(originX, 0, categoryRect, subRect);
                navHits.add(new NavHit(NavKind.SUBCATEGORY, category, "", null, subRect,
                        new ModernMainLayout.Rect(subRect.x + 4, subRect.y + 4, 12, 12)));
                y = ModernTreeGuide.nextY(y, NAV_SUB_HEIGHT);
                if (!groupCollapsed) {
                    for (PathSequence sequence : ungrouped) {
                        if (query.isEmpty() || matches(sequence, query)) {
                            ModernMainLayout.Rect sequenceRect = ModernTreeGuide.row(originX, contentWidth, y,
                                    NAV_SEQUENCE_HEIGHT, 2);
                            drawSequenceNavRow(font, sequenceRect, sequence, mouseX, mouseY);
                            ModernTreeGuide.drawChild(originX, 1, subRect, sequenceRect);
                            navHits.add(new NavHit(NavKind.SEQUENCE, category, "", sequence, sequenceRect, null));
                            y = ModernTreeGuide.nextY(y, NAV_SEQUENCE_HEIGHT);
                        }
                    }
                }
            }
        }
        ModernUiRenderer.endClip();
        navigationMaxScroll = Math.max(0, y + navigationScroll - navigationContentBounds.bottom());
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        navigationScrollBar.drawAt(navigationBounds.right(), navigationContentBounds.y, navigationContentBounds.height,
                navigationScroll, navigationMaxScroll, navigationContentBounds.height,
                navigationContentBounds.height + navigationMaxScroll, mouseX, mouseY, value -> navigationScroll = value);
        ModernSplitPane.drawVerticalDivider(navigationDividerBounds, mouseX, mouseY, draggingNavigationDivider);
        drawNavTools(font, mouseX, mouseY);
    }

    private ModernMainLayout.Rect sequenceToggleCloseBounds;
    private ModernMainLayout.Rect sequenceToggleSingleBounds;
    private ModernMainLayout.Rect sequenceToggleBackgroundBounds;
    private ModernMainLayout.Rect sequenceToggleLockBounds;
    private ModernMainLayout.Rect sequenceLoopBounds;
    private ModernMainLayout.Rect sequenceNoteBounds;

    private void drawStepPane(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(stepBounds.x, stepBounds.y, stepBounds.width, stepBounds.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        stepRunBounds = new ModernMainLayout.Rect(Math.max(stepBounds.x + 1, stepBounds.right() - 62),
                stepBounds.y + 5, 54, 18);
        List<PathWorkbenchToolCatalog.ToolId> stepOverflow = PathWorkbenchToolCatalog.get()
                .overflow(PathWorkbenchToolCatalog.Zone.STEP);
        stepMoreBounds = stepOverflow.isEmpty() ? null
                : new ModernMainLayout.Rect(Math.max(stepBounds.x + 1, stepRunBounds.x - 22), stepBounds.y + 5, 18, 18);
        int stepTitleRight = stepMoreBounds == null ? stepRunBounds.x : stepMoreBounds.x;
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u022", stepBounds.x + 9, stepBounds.y + 7, ModernUiRenderer.TEXT,
                Math.max(48, stepTitleRight - stepBounds.x - 58));
        ModernUiRenderer.drawText(font, selectedSequence == null ? "" : tr("gui.modern.path.wb.fmt.steps", String.valueOf(selectedSequence.getSteps().size())),
                stepTitleRight - 46, stepBounds.y + 7, ModernUiRenderer.MUTED_TEXT, 40);
        if (stepMoreBounds != null) {
            drawIconButton(stepMoreBounds, "...", mouseX, mouseY, "gui.modern.path.wb.u023");
        }
        drawButton(font, stepRunBounds, "gui.modern.path.wb.u024", false, false, selectedSequence != null && selectedStepIndex >= 0,
                mouseX, mouseY);
        ModernUiRenderer.drawDivider(stepBounds.x + 7, stepBounds.y + 23, Math.max(1, stepBounds.width - 14),
                ModernUiRenderer.BORDER_SUBTLE);
        drawStepOptions(font, mouseX, mouseY);
        stepHits.clear();
        if (selectedSequence != null) {
            // Rendering, wheel input and drag/drop all use pixel offsets.
            int contentHeight = selectedSequence.getSteps().size() * STEP_ROW_HEIGHT;
            stepMaxScroll = Math.max(0, contentHeight - stepListBounds.height);
            stepScroll = clamp(stepScroll, 0, stepMaxScroll);
            ModernUiRenderer.beginClip(stepListBounds);
            int y = stepListBounds.y - stepScroll;
            for (int i = 0; i < selectedSequence.getSteps().size(); i++) {
                PathStep step = selectedSequence.getSteps().get(i);
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(stepListBounds.x + 2, y,
                        ModernHoverScrollbar.contentWidth(stepListBounds.width - 4), STEP_ROW_HEIGHT - 3);
                drawStepRow(font, row, step, i, mouseX, mouseY);
                if (row.bottom() > stepListBounds.y && row.y < stepListBounds.bottom()) {
                    stepHits.add(new RowHit(i, row));
                }
                y += STEP_ROW_HEIGHT;
            }
            ModernUiRenderer.endClip();
            if (draggingStepIndex >= 0 && stepDropIndex >= 0) {
                drawDropMarker(stepListBounds, STEP_ROW_HEIGHT, stepDropIndex, stepScroll);
            }
            drawScrollBarFor(stepScrollBar, stepListBounds, stepScroll, stepMaxScroll,
                    contentHeight, stepListBounds.height, mouseX, mouseY, value -> stepScroll = value);
        } else {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u025", stepListBounds.x + 10, stepListBounds.y + 20,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, stepListBounds.width - 20));
        }
        drawStepTools(font, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(editorDividerBounds, mouseX, mouseY, draggingEditorDivider);
    }

    private static final String[] NOTE_COLOR_CODES = new String[] {
            "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
            "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
    };
    private static final String[] NOTE_FORMAT_CODES = new String[] {
            "§l", "§n", "§o", "§m", "§k", "§r"
    };
    private static final String[] NOTE_FORMAT_CHIP_TEXTS = new String[] {
            "gui.modern.path.wb.u001", "gui.modern.path.wb.u002", "gui.modern.path.wb.u003", "gui.modern.path.wb.u004", "gui.modern.path.wb.u005", "gui.modern.path.wb.u006"
    };
    private static final int NOTE_CHIP_ROW_HEIGHT = 15;

    private ModernMainLayout.Rect[] sequenceNoteColorChips;
    private ModernMainLayout.Rect[] sequenceNoteFormatChips;
    private ModernMainLayout.Rect[] modalNoteColorChips;
    private ModernMainLayout.Rect[] modalNoteFormatChips;

    private ModernMainLayout.Rect[] drawNoteChipRow(FontRenderer font, int x, int y, int count, int chipWidth,
            boolean colorChips, boolean enabled, int mouseX, int mouseY) {
        if (count <= 0 || chipWidth <= 0) {
            return new ModernMainLayout.Rect[0];
        }
        ModernMainLayout.Rect[] chips = new ModernMainLayout.Rect[count];
        for (int i = 0; i < count; i++) {
            ModernMainLayout.Rect rect = new ModernMainLayout.Rect(x + i * (chipWidth + 2), y, chipWidth,
                    NOTE_CHIP_ROW_HEIGHT);
            chips[i] = rect;
            boolean hovered = enabled && rect.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 3,
                    !enabled ? 0xFF151E26 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String code = colorChips ? NOTE_COLOR_CODES[i] : NOTE_FORMAT_CODES[i];
            if (colorChips) {
                int swatchSize = Math.max(6, Math.min(10, rect.height - 5));
                int swatchX = rect.x + (rect.width - swatchSize) / 2;
                int swatchY = rect.y + (rect.height - swatchSize) / 2;
                int swatchColor = enabled ? SYSTEM_MESSAGE_COLORS[i] : 0xFF4A555F;
                // Keep the black and dark swatches visible against the button surface.
                int swatchBorder = i == 0 || i == 1 || i == 2 || i == 4 || i == 5 || i == 8
                        ? enabled ? 0xFF9AA8B4 : 0xFF68747E : swatchColor;
                ModernUiRenderer.drawSubtlePanel(swatchX, swatchY, swatchSize, swatchSize, 2,
                        swatchColor, swatchBorder);
            } else {
                drawCenteredButtonText(font, NOTE_FORMAT_CHIP_TEXTS[i], rect,
                        enabled ? (hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT)
                                : ModernUiRenderer.MUTED_TEXT);
            }
            if (hovered) {
                hoveredTooltip = tr(colorChips ? "gui.modern.path.wb.fmt.insert_color" : "gui.modern.path.wb.fmt.insert_format",
                        code, tr("gui.modern.path.wb.u026"));
            }
        }
        return chips;
    }

    /** 在指定输入框光标（或选区）处插入格式代码，并让输入框保持聚焦。 */
    private void insertFormattingToken(String fieldKey, String token) {
        ModernTextField field = fields.get(fieldKey);
        if (field == null || token == null || token.isEmpty()) {
            return;
        }
        String currentText = safe(field.getText());
        int cursorPos = clamp(field.getCursorPosition(), 0, currentText.length());
        int selectionPos = clamp(field.getSelectionEnd(), 0, currentText.length());
        int start = Math.min(cursorPos, selectionPos);
        int end = Math.max(cursorPos, selectionPos);
        field.setText(currentText.substring(0, start) + token + currentText.substring(end));
        field.setCursorPosition(start + token.length());
        field.setSelectionPos(start + token.length());
        for (ModernTextField other : fields.values()) {
            other.setFocused(false);
        }
        field.setCanLoseFocus(false);
        field.setFocused(true);
        focusedFieldKey = fieldKey;
    }

    private boolean handleNoteChipClick(ModernMainLayout.Rect[] colorChips, ModernMainLayout.Rect[] formatChips,
            String fieldKey, boolean commitToModel, int mouseX, int mouseY) {
        if (colorChips != null) {
            for (int i = 0; i < colorChips.length && i < NOTE_COLOR_CODES.length; i++) {
                if (colorChips[i] != null && colorChips[i].contains(mouseX, mouseY)) {
                    insertFormattingToken(fieldKey, NOTE_COLOR_CODES[i]);
                    if (commitToModel) {
                        syncEditorFields();
                    }
                    return true;
                }
            }
        }
        if (formatChips != null) {
            for (int i = 0; i < formatChips.length && i < NOTE_FORMAT_CODES.length; i++) {
                if (formatChips[i] != null && formatChips[i].contains(mouseX, mouseY)) {
                    insertFormattingToken(fieldKey, NOTE_FORMAT_CODES[i]);
                    if (commitToModel) {
                        syncEditorFields();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private void drawStepOptions(FontRenderer font, int mouseX, int mouseY) {
        stepOptionsToggleBounds = new ModernMainLayout.Rect(stepBounds.x + 6, stepBounds.y + 28,
                Math.max(1, stepBounds.width - 12), 20);
        drawButton(font, stepOptionsToggleBounds, stepOptionsExpanded ? "gui.modern.path.wb.u027" : "gui.modern.path.wb.u028",
                false, false, selectedSequence != null, mouseX, mouseY);
        if (!stepOptionsExpanded) {
            sequenceToggleCloseBounds = null;
            sequenceToggleSingleBounds = null;
            sequenceToggleBackgroundBounds = null;
            sequenceToggleLockBounds = null;
            sequenceLoopBounds = null;
            sequenceNoteBounds = null;
            sequenceNoteColorChips = null;
            sequenceNoteFormatChips = null;
            return;
        }
        int panelY = stepOptionsToggleBounds.bottom() + 4;
        int panelHeight = 50;
        stepOptionsPanelBounds = new ModernMainLayout.Rect(stepBounds.x + 6, panelY,
                Math.max(1, stepBounds.width - 12), panelHeight);
        ModernUiRenderer.drawSubtlePanel(stepOptionsPanelBounds.x, stepOptionsPanelBounds.y,
                stepOptionsPanelBounds.width, stepOptionsPanelBounds.height, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        int gap = 4;
        int toggleY = panelY + 4;
        int toggleWidth = Math.max(32, (stepOptionsPanelBounds.width - 10 - gap * 3) / 4);
        ModernMainLayout.Rect close = new ModernMainLayout.Rect(stepOptionsPanelBounds.x + 5, toggleY, toggleWidth, 18);
        ModernMainLayout.Rect single = new ModernMainLayout.Rect(close.right() + gap, toggleY, toggleWidth, 18);
        ModernMainLayout.Rect background = new ModernMainLayout.Rect(single.right() + gap, toggleY, toggleWidth, 18);
        ModernMainLayout.Rect lock = new ModernMainLayout.Rect(background.right() + gap, toggleY,
                Math.max(1, stepOptionsPanelBounds.right() - background.right() - 5), 18);
        drawOptionToggle(font, close, "gui.modern.path.wb.u029", selectedSequence != null && selectedSequence.shouldCloseGuiAfterStart(),
                isEditableSequence(), "gui.modern.path.wb.u030", mouseX, mouseY);
        drawOptionToggle(font, single, "gui.modern.path.wb.u031", selectedSequence != null && selectedSequence.isSingleExecution(),
                isEditableSequence(), "gui.modern.path.wb.u032", mouseX, mouseY);
        drawOptionToggle(font, background, "gui.modern.path.wb.u033", selectedSequence != null && selectedSequence.isNonInterruptingExecution(),
                isEditableSequence(), "gui.modern.path.wb.u034", mouseX, mouseY);
        drawOptionToggle(font, lock, lockPolicyLabel(), selectedSequence != null
                && !"WAIT".equals(selectedSequence.getLockConflictPolicy()), isEditableSequence(),
                lockPolicyTooltip(), mouseX, mouseY);
        sequenceToggleCloseBounds = close;
        sequenceToggleSingleBounds = single;
        sequenceToggleBackgroundBounds = background;
        sequenceToggleLockBounds = lock;
        int inputY = panelY + 28;
        int labelX = stepOptionsPanelBounds.x + 5;
        String loopLabel = "gui.modern.path.wb.u035";
        int loopLabelWidth = font.getStringWidth(tr(loopLabel));
        ModernUiRenderer.drawText(font, loopLabel, labelX, inputY + 5, ModernUiRenderer.SUBTLE_TEXT,
                loopLabelWidth + 2);
        drawInlineInfoIcon(labelX + loopLabelWidth + 3, inputY + 4,
                "gui.modern.path.wb.u036",
                mouseX, mouseY);
        int loopFieldX = labelX + loopLabelWidth + 18;
        int loopWidth = Math.min(64, Math.max(36, stepOptionsPanelBounds.width / 6));
        sequenceLoopBounds = new ModernMainLayout.Rect(loopFieldX, inputY, loopWidth, 18);
        sequenceNoteBounds = new ModernMainLayout.Rect(sequenceLoopBounds.right() + 8, inputY,
                Math.max(1, stepOptionsPanelBounds.right() - sequenceLoopBounds.right() - 10), 18);
        drawField(font, "sequence.loop", sequenceLoopBounds, isEditableSequence(), "20", mouseX, mouseY);
        drawSequenceNoteButton(font, sequenceNoteBounds, mouseX, mouseY);
        sequenceNoteColorChips = null;
        sequenceNoteFormatChips = null;
    }

    private ModernMainLayout.Rect stepAddBounds;
    private ModernMainLayout.Rect stepRunBounds;
    private ModernMainLayout.Rect stepCopyBounds;
    private ModernMainLayout.Rect stepDeleteBounds;
    private ModernMainLayout.Rect stepUpBounds;
    private ModernMainLayout.Rect stepDownBounds;
    private ModernMainLayout.Rect stepGetCoordsBounds;
    private ModernMainLayout.Rect stepClearCoordsBounds;
    private ModernMainLayout.Rect actionAddBounds;
    private ModernMainLayout.Rect actionMoreBounds;

    private void drawStepTools(FontRenderer font, int mouseX, int mouseY) {
        List<PathWorkbenchToolCatalog.ToolId> pinned = PathWorkbenchToolCatalog.get()
                .pinned(PathWorkbenchToolCatalog.Zone.STEP);
        List<PathWorkbenchToolCatalog.ToolId> commands = new ArrayList<PathWorkbenchToolCatalog.ToolId>();
        for (PathWorkbenchToolCatalog.ToolId tool : pinned) {
            if (tool != PathWorkbenchToolCatalog.ToolId.STEP_GET_COORDS
                    && tool != PathWorkbenchToolCatalog.ToolId.STEP_CLEAR_COORDS
                    && tool != PathWorkbenchToolCatalog.ToolId.STEP_SETTINGS) {
                commands.add(tool);
            }
        }
        stepToolsSettingsBounds = drawToolStrip(font, stepToolsBounds, "gui.modern.path.wb.u037",
                PathWorkbenchToolCatalog.Zone.STEP, commands, mouseX, mouseY);
        if (!stepToolsExpanded) {
            stepXBounds = null;
            stepYBounds = null;
            stepZBounds = null;
            stepGetCoordsBounds = null;
            stepClearCoordsBounds = null;
            stepSettingsButtonBounds = null;
            return;
        }
        int gap = 3;
        int innerWidth = Math.max(1, stepToolsBounds.width - 16);
        int commandRows = Math.max(0, PathWorkbenchToolCatalog.wrapRows(commands.size(), innerWidth));
        int inputY = stepToolsBounds.y + 20 + commandRows * (TOOL_BUTTON_HEIGHT + 4);
        if (inputY + 18 > stepToolsBounds.bottom() - 4) {
            inputY = Math.max(stepToolsBounds.y + 20, stepToolsBounds.bottom() - 22);
        }
        int coordX = stepToolsBounds.x + 8;
        int coordButtonWidth = Math.max(46, Math.min(88, (innerWidth - gap * 5 - 3 * 28) / 3));
        int coordinateWidth = Math.max(28, (innerWidth - gap * 5 - coordButtonWidth * 3) / 3);
        stepXBounds = new ModernMainLayout.Rect(coordX, inputY, coordinateWidth, 18);
        stepYBounds = new ModernMainLayout.Rect(stepXBounds.right() + gap, inputY, coordinateWidth, 18);
        stepZBounds = new ModernMainLayout.Rect(stepYBounds.right() + gap, inputY, coordinateWidth, 18);
        drawLabeledField(font, "step.x", stepXBounds, "X", canEditStep(), mouseX, mouseY);
        drawLabeledField(font, "step.y", stepYBounds, "Y", canEditStep(), mouseX, mouseY);
        drawLabeledField(font, "step.z", stepZBounds, "Z", canEditStep(), mouseX, mouseY);
        List<PathWorkbenchToolCatalog.ToolId> coordTools = new ArrayList<PathWorkbenchToolCatalog.ToolId>();
        for (PathWorkbenchToolCatalog.ToolId tool : pinned) {
            if (tool == PathWorkbenchToolCatalog.ToolId.STEP_GET_COORDS
                    || tool == PathWorkbenchToolCatalog.ToolId.STEP_CLEAR_COORDS
                    || tool == PathWorkbenchToolCatalog.ToolId.STEP_SETTINGS) {
                coordTools.add(tool);
            }
        }
        int coordButtonX = stepZBounds.right() + gap;
        int remaining = Math.max(1, stepToolsBounds.right() - coordButtonX - 8);
        int coordCols = Math.max(1, coordTools.size());
        int each = Math.max(1, (remaining - gap * (coordCols - 1)) / coordCols);
        for (int i = 0; i < coordTools.size(); i++) {
            PathWorkbenchToolCatalog.ToolId tool = coordTools.get(i);
            int width = i == coordTools.size() - 1
                    ? Math.max(1, stepToolsBounds.right() - coordButtonX - 8)
                    : each;
            ModernMainLayout.Rect rect = new ModernMainLayout.Rect(coordButtonX, inputY, width, TOOL_BUTTON_HEIGHT);
            toolHits.put(toolKey(tool), rect);
            drawButton(font, rect, tool.label, tool.primary, tool.danger, isToolEnabled(tool), mouseX, mouseY);
            coordButtonX += width + gap;
        }
        stepGetCoordsBounds = toolHits.get(toolKey(PathWorkbenchToolCatalog.ToolId.STEP_GET_COORDS));
        stepClearCoordsBounds = toolHits.get(toolKey(PathWorkbenchToolCatalog.ToolId.STEP_CLEAR_COORDS));
        stepSettingsButtonBounds = toolHits.get(toolKey(PathWorkbenchToolCatalog.ToolId.STEP_SETTINGS));
    }

    private ModernMainLayout.Rect stepXBounds;
    private ModernMainLayout.Rect stepYBounds;
    private ModernMainLayout.Rect stepZBounds;

    private void drawActionPane(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(actionBounds.x, actionBounds.y, actionBounds.width, actionBounds.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        List<PathWorkbenchToolCatalog.ToolId> actionOverflow = PathWorkbenchToolCatalog.get()
                .overflow(PathWorkbenchToolCatalog.Zone.ACTION);
        actionMoreBounds = actionOverflow.isEmpty() ? null
                : new ModernMainLayout.Rect(Math.max(actionBounds.x + 1, actionBounds.right() - 26),
                        actionBounds.y + 5, 18, 18);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u038", actionBounds.x + 9, actionBounds.y + 7, ModernUiRenderer.TEXT,
                Math.max(60, (actionMoreBounds == null ? actionBounds.right() : actionMoreBounds.x)
                        - actionBounds.x - 14));
        if (actionMoreBounds != null) {
            drawIconButton(actionMoreBounds, "...", mouseX, mouseY, "gui.modern.path.wb.u039");
        }
        ModernUiRenderer.drawDivider(actionBounds.x + 7, actionBounds.y + 23, Math.max(1, actionBounds.width - 14),
                ModernUiRenderer.BORDER_SUBTLE);
        actionHits.clear();
        actionPacketToggleHits.clear();
        if (selectedStep != null) {
            int contentHeight = selectedStep.getActions().size() * ACTION_ROW_HEIGHT;
            actionMaxScroll = Math.max(0, contentHeight - actionListBounds.height);
            actionScroll = clamp(actionScroll, 0, actionMaxScroll);
            ModernUiRenderer.beginClip(actionListBounds);
            int y = actionListBounds.y - actionScroll;
            for (int i = 0; i < selectedStep.getActions().size(); i++) {
                ActionData action = selectedStep.getActions().get(i);
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(actionListBounds.x + 2, y,
                        ModernHoverScrollbar.contentWidth(actionListBounds.width - 4), ACTION_ROW_HEIGHT - 3);
                drawActionRow(font, row, action, i, mouseX, mouseY);
                if (row.bottom() > actionListBounds.y && row.y < actionListBounds.bottom()) {
                    actionHits.add(new RowHit(i, row));
                }
                y += ACTION_ROW_HEIGHT;
            }
            ModernUiRenderer.endClip();
            if (draggingActionIndex >= 0 && actionDropIndex >= 0) {
                drawDropMarker(actionListBounds, ACTION_ROW_HEIGHT, actionDropIndex, actionScroll);
            }
            drawScrollBarFor(actionScrollBar, actionListBounds, actionScroll, actionMaxScroll,
                    contentHeight, actionListBounds.height, mouseX, mouseY, value -> actionScroll = value);
        } else {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u040", actionListBounds.x + 10, actionListBounds.y + 20,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, actionListBounds.width - 20));
        }
        drawActionTools(font, mouseX, mouseY);
    }

    private void drawActionTools(FontRenderer font, int mouseX, int mouseY) {
        if (actionToolsBounds == null) {
            return;
        }
        actionToolsSettingsBounds = drawToolStrip(font, actionToolsBounds, "gui.modern.path.wb.u041",
                PathWorkbenchToolCatalog.Zone.ACTION,
                PathWorkbenchToolCatalog.get().pinned(PathWorkbenchToolCatalog.Zone.ACTION), mouseX, mouseY);
    }

    private ModernMainLayout.Rect actionTypeBounds;

    private void bindMoveChestHost() {
        if (moveChestHostBound) {
            return;
        }
        moveChestEditor.bind(new MoveChestEditor.Host() {
            @Override
            public boolean canEdit() {
                return canEditStep();
            }

            @Override
            public void pushHistory(String reason) {
                ModernPathWorkbenchTab.this.pushHistory(reason);
            }

            @Override
            public void markDirty() {
                dirty = true;
            }

            @Override
            public void status(String message) {
                ModernPathWorkbenchTab.this.status(message);
            }

            @Override
            public void setTooltip(String text) {
                if (text != null && !text.isEmpty()) {
                    hoveredTooltip = text;
                }
            }

            @Override
            public void openChoiceMenu(String title, List<ClickSemantics.Option> options, int mouseX, int mouseY) {
                menuTitle = title;
                menuItems.clear();
                for (final ClickSemantics.Option option : options) {
                    String label = option.disabled ? tr("gui.modern.path.wb.fmt.unavailable", tr(option.label())) : tr(option.label());
                    menuItems.add(new RectAction(label, !option.disabled, false, new Runnable() {
                        @Override
                        public void run() {
                            moveChestEditor.applyClickOption(option);
                        }
                    }));
                }
                setMenuAnchor(mouseX, mouseY);
            }

            @Override
            public ModernTextField field(String key) {
                return fields.get(key);
            }

            @Override
            public void focusField(String key) {
                ModernTextField field = fields.get(key);
                if (field != null) {
                    ModernPathWorkbenchTab.this.focusField(key, field.x, field.y, 0);
                }
            }

            @Override
            public void showField(String key, ModernMainLayout.Rect rect, boolean enabled) {
                ModernPathWorkbenchTab.this.showField(key, rect, enabled);
            }
        });
        moveChestHostBound = true;
    }

    private void bindVariablePanelHost() {
        if (variablePanelHostBound) {
            return;
        }
        variablePanel.bind(new ModernActionVariablePanel.Host() {
            @Override
            public List<PathSequence> sequences() {
                return sequences;
            }

            @Override
            public void drawField(FontRenderer font, String key, ModernMainLayout.Rect rect, boolean enabled,
                    String hint, int mouseX, int mouseY) {
                ModernPathWorkbenchTab.this.drawField(font, key, rect, enabled, hint, mouseX, mouseY);
            }

            @Override
            public void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary,
                    boolean danger, boolean enabled, int mouseX, int mouseY) {
                ModernPathWorkbenchTab.this.drawButton(font, rect, label, primary, danger, enabled, mouseX, mouseY);
            }

            @Override
            public String fieldText(String key) {
                ModernTextField field = fields.get(key);
                return field == null ? "" : safe(field.getText());
            }

            @Override
            public void setFieldTextIfUnchanged(String key, String value) {
                ModernPathWorkbenchTab.this.setFieldTextIfUnchanged(key, value);
            }

            @Override
            public void clearFocus() {
                ModernPathWorkbenchTab.this.clearFocus();
            }

            @Override
            public void status(String message) {
                ModernPathWorkbenchTab.this.status(message);
            }

            @Override
            public void pushHistory(String reason) {
                ModernPathWorkbenchTab.this.pushHistory(reason);
            }

            @Override
            public void markDirty() {
                dirty = true;
            }

            @Override
            public void refreshEditor() {
                bindEditorFields();
            }

            @Override
            public void setTooltip(String text) {
                if (text != null && !text.isEmpty()) {
                    hoveredTooltip = text;
                }
            }

            @Override
            public boolean isControlDown() {
                return ModernPathWorkbenchTab.this.isControlDown();
            }
        });
        variablePanelHostBound = true;
    }

    private void drawSchemaParameterRow(FontRenderer font, ModernMainLayout.Rect row,
            ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        if (field.kind == ModernActionEditorSchema.Kind.MOVE_CANVAS) {
            if (selectedAction != null && selectedAction.params == null) {
                selectedAction.params = new JsonObject();
            }
            moveChestEditor.draw(font, row, selectedAction == null ? null : selectedAction.params,
                    canEditStep(), mouseX, mouseY);
            return;
        }
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION_LIST
                || field.kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST
                || field.kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            drawExpressionListRow(font, row, field, mouseX, mouseY);
            return;
        }
        if (field.kind == ModernActionEditorSchema.Kind.SLOT_GRID) {
            drawSlotGridRow(font, row, field, mouseX, mouseY);
            return;
        }
        if (field.kind == ModernActionEditorSchema.Kind.STRUCTURED_LIST) {
            drawStructuredListRow(font, row, field, mouseX, mouseY);
            return;
        }
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION) {
            drawStandaloneExpressionRow(font, row, field, mouseX, mouseY);
            return;
        }
        JsonElement value = selectedAction.params == null ? null : selectedAction.params.get(field.key);
        if (value == null) {
            value = parseLiteral(field.defaultValue);
        }
        if ("pickupFilterMode".equals(field.key) && selectedAction.params != null
                && !selectedAction.params.has(field.key)
                && !InventoryItemFilterExpressionEngine.readExpressions(selectedAction.params).isEmpty()) {
            value = parseLiteral("CUSTOM");
        }
        boolean parameterEnabled = canEditStep() && isActionParameterEnabled(field);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                parameterEnabled ? ModernUiRenderer.SHELL : 0xFF151E26, ModernUiRenderer.BORDER_SUBTLE);
        int labelWidth = Math.min(196, Math.max(112, row.width * 36 / 100));
        ModernUiRenderer.drawText(font, field.label, row.x + 10, row.y + 8,
                parameterEnabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                Math.max(40, labelWidth - 30));
        if (!safe(field.hint).isEmpty()) {
            ModernUiRenderer.drawText(font, field.hint, row.x + 10, row.y + 23,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, labelWidth - 30));
        }
        drawParameterInfoIcon(field, row.x + labelWidth - 18, row.y + 15, mouseX, mouseY);
        ModernMainLayout.Rect control = new ModernMainLayout.Rect(row.x + labelWidth, row.y + 8,
                Math.max(1, row.width - labelWidth - 10), 24);
        if (field.kind == ModernActionEditorSchema.Kind.TOGGLE) {
            drawToggleButton(font, control, actionChoiceDisplay(field.key, jsonValue(value)),
                    value != null && value.isJsonPrimitive() && value.getAsBoolean(), parameterEnabled, mouseX, mouseY);
        } else if (shouldDrawSpinner(field)) {
            drawSpinnerControl(font, control, field, jsonValue(value), parameterEnabled, mouseX, mouseY);
        } else if (ActionEditorUxSupport.isHotbarKey(field.key)) {
            drawHotbarControl(font, row, control, field, jsonValue(value), parameterEnabled, mouseX, mouseY);
        } else if (ActionEditorUxSupport.isClickButtonKey(selectedAction.type, field.key)) {
            String clickType = jsonValue(selectedAction.params.get("clickType"));
            int button = 0;
            try {
                button = value == null ? 0 : value.getAsInt();
            } catch (Exception ignored) {
            }
            drawButton(font, control, ClickSemantics.display(clickType, button), false, false, parameterEnabled,
                    mouseX, mouseY);
        } else if (field.kind == ModernActionEditorSchema.Kind.KEYBOARD_PICKER) {
            String keyName = jsonValue(value).trim();
            drawButton(font, control, keyName.isEmpty() ? "gui.modern.path.wb.u042" : keyboardKeyDisplay(keyName), false, false,
                    parameterEnabled, mouseX, mouseY);
        } else if (isSourceVariablePickerField(field)) {
            String selected = jsonValue(value).trim();
            drawButton(font, control, selected.isEmpty() ? "gui.modern.path.wb.u043" : selected, false, false,
                    parameterEnabled, mouseX, mouseY);
        } else if (ActionEditorUxSupport.isVariableKey(selectedAction.type, field.key)) {
            drawVariableNameControl(font, control, field, jsonValue(value), parameterEnabled, mouseX, mouseY);
        } else if (field.kind == ModernActionEditorSchema.Kind.CHOICE) {
            boolean hovered = control.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(control.x, control.y, control.width, control.height, 4,
                    !parameterEnabled ? 0xFF151E26 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered && parameterEnabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernMainLayout.Rect labelBounds = new ModernMainLayout.Rect(control.x, control.y,
                    Math.max(1, control.width - 16), control.height);
            drawCenteredButtonText(font, actionChoiceDisplay(field.key, jsonValue(value)), labelBounds,
                    !parameterEnabled ? ModernUiRenderer.MUTED_TEXT
                            : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT);
            ModernUiRenderer.drawChevron(control.right() - 15, control.y + 8, false,
                    parameterEnabled && hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        } else if (isActionPickerField(field)) {
            drawButton(font, control, actionPickerFieldDisplay(field, jsonValue(value)), false, false,
                    parameterEnabled, mouseX, mouseY);
        } else {
            String fieldKey = "param." + field.key;
            ensureField(fieldKey, ActionEditorUxSupport.isHexKey(selectedAction.type, field.key) ? 4096 : 32767);
            setFieldTextIfUnchanged(fieldKey, jsonValue(value));
            if (ActionEditorUxSupport.isCoordKey(field.key) || ActionEditorUxSupport.isViewCaptureKey(field.key)) {
                ModernMainLayout.Rect capture = new ModernMainLayout.Rect(control.right() - 58, control.y, 58, 24);
                control = new ModernMainLayout.Rect(control.x, control.y, Math.max(1, control.width - 64), 24);
                drawField(font, fieldKey, control, parameterEnabled, field.hint, mouseX, mouseY);
                drawButton(font, capture, "gui.modern.path.wb.u044", false, false, parameterEnabled, mouseX, mouseY);
                parameterHits.add(new ParameterHit("capture:" + field.key, capture, field));
                if (isScreenClickCoordinateField(field) && "x".equals(field.key)) {
                    ModernUiRenderer.drawText(font, screenClickCoordinateInfo(), row.x + 10, row.y + 40,
                            ModernUiRenderer.MUTED_TEXT, Math.max(40, row.width - 20));
                }
            } else {
                drawField(font, fieldKey, control, parameterEnabled, field.hint, mouseX, mouseY);
            }
            if (isSystemMessageField(field)) {
                drawSystemMessageShortcuts(font, row, mouseX, mouseY);
            }
            if (ActionEditorUxSupport.isHexKey(selectedAction.type, field.key)) {
                String hexError = ActionEditorJson.hexError(jsonValue(value));
                if (!hexError.isEmpty()) {
                    ModernUiRenderer.drawText(font, hexError, row.x + 10, row.y + 34, ModernUiRenderer.WARNING,
                            Math.max(40, row.width - 20));
                }
            }
        }
        parameterHits.add(new ParameterHit(field.key, control, field));
        if (field.kind != ModernActionEditorSchema.Kind.SECTION && field.defaultValue != null
                && (selectedAction.params == null || !selectedAction.params.has(field.key))) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u045", control.right() - 38, row.y + 1,
                    ModernUiRenderer.MUTED_TEXT, 30);
        }
    }

    private void drawExpressionListRow(FontRenderer font, ModernMainLayout.Rect row,
            ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        List<String> expressions = getExpressionValues(field);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, field.label, row.x + 10, row.y + 8, ModernUiRenderer.TEXT,
                Math.max(70, row.width - 112));
        ModernUiRenderer.drawText(font, safe(field.hint), row.x + 10, row.y + 23, ModernUiRenderer.MUTED_TEXT,
                Math.max(70, row.width - 112));
        ModernMainLayout.Rect add = new ModernMainLayout.Rect(row.right() - 82, row.y + 8, 72, 22);
        drawButton(font, add, "gui.modern.path.wb.u046", true, false, canEditStep(), mouseX, mouseY);
        parameterHits.add(new ParameterHit(field.key, add, field));
        if (expressions.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u047", row.x + 12, row.y + 49,
                    ModernUiRenderer.MUTED_TEXT, Math.max(50, row.width - 24));
            return;
        }
        int y = row.y + 42;
        for (int i = 0; i < expressions.size(); i++) {
            ModernMainLayout.Rect item = new ModernMainLayout.Rect(row.x + 8, y, row.width - 16, 24);
            boolean hovered = item.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(item.x, item.y, item.width, item.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, (i + 1) + ". " + expressions.get(i), item.x + 8, item.y + 8,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(30, item.width - 82));
            ModernMainLayout.Rect edit = new ModernMainLayout.Rect(item.right() - 62, item.y + 3, 26, 18);
            ModernMainLayout.Rect delete = new ModernMainLayout.Rect(item.right() - 32, item.y + 3, 26, 18);
            drawButton(font, edit, "gui.modern.path.wb.u048", false, false, canEditStep(), mouseX, mouseY);
            drawButton(font, delete, "gui.modern.path.wb.u049", false, true, canEditStep(), mouseX, mouseY);
            expressionRowHits.add(new ExpressionRowHit(i, item, edit, delete, field));
            y += 28;
        }
    }

    private void drawStandaloneExpressionRow(FontRenderer font, ModernMainLayout.Rect row,
            ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        String expression = selectedAction == null || selectedAction.params == null ? ""
                : jsonValue(selectedAction.params.get(field.key)).trim();
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, field.label, row.x + 10, row.y + 8, ModernUiRenderer.TEXT,
                Math.max(70, row.width - 112));
        ModernUiRenderer.drawText(font, safe(field.hint), row.x + 10, row.y + 23, ModernUiRenderer.MUTED_TEXT,
                Math.max(70, row.width - 112));
        ModernMainLayout.Rect edit = new ModernMainLayout.Rect(row.right() - 82, row.y + 8, 72, 22);
        drawButton(font, edit, expression.isEmpty() ? "gui.modern.path.wb.u050" : "gui.modern.path.wb.u051", true, false, canEditStep(), mouseX, mouseY);
        ModernMainLayout.Rect display = new ModernMainLayout.Rect(row.x + 8, row.y + 42, row.width - 16, 24);
        boolean hovered = display.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(display.x, display.y, display.width, display.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        String text = expression.isEmpty() ? "gui.modern.path.wb.u052" : expression;
        ModernUiRenderer.drawText(font, text, display.x + 8, display.y + 8,
                expression.isEmpty() ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(30, display.width - 16));
        parameterHits.add(new ParameterHit(field.key, edit, field));
        parameterHits.add(new ParameterHit(field.key, display, field));
    }

    private int schemaFieldHeight(ModernActionEditorSchema.Field field) {
        if (field == null) {
            return 0;
        }
        if ("itemFilterExpressions".equals(field.key)
                && "pickup_nearby_items".equalsIgnoreCase(safe(selectedAction == null ? "" : selectedAction.type))
                && !isCustomNearbyPickupFilter()) {
            return 0;
        }
        if (ActionEditorUxSupport.isMoveChestAction(selectedAction == null ? "" : selectedAction.type)
                && ActionEditorJson.isMoveChestOwnedKey(field.key)
                && field.kind != ModernActionEditorSchema.Kind.MOVE_CANVAS) {
            return 0;
        }
        if (field.kind == ModernActionEditorSchema.Kind.MOVE_CANVAS) {
            return moveChestEditor.preferredHeight(actionPageParamsBounds == null ? 320
                    : Math.max(160, actionPageParamsBounds.width - 32),
                    selectedAction == null ? null : selectedAction.params);
        }
        if (isScreenClickCoordinateField(field) && "x".equals(field.key)) {
            return 62;
        }
        if (ActionEditorUxSupport.isHotbarKey(field.key)) {
            return 68;
        }
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION_LIST
                || field.kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST
                || field.kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            return 72 + getExpressionValues(field).size() * 28;
        }
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION) {
            return 74;
        }
        if (field.kind == ModernActionEditorSchema.Kind.SLOT_GRID) {
            if (!shouldShowSlotGrid(field)) return 0;
            return 52 + slotGridRows(field) * 24;
        }
        if (field.kind == ModernActionEditorSchema.Kind.STRUCTURED_LIST) {
            return 82 + readStructuredEntries(field.key).size() * 30;
        }
        if (isSystemMessageField(field)) {
            return 142;
        }
        return PARAM_ROW_HEIGHT;
    }

    private boolean isCustomNearbyPickupFilter() {
        if (selectedAction == null || !"pickup_nearby_items".equalsIgnoreCase(safe(selectedAction.type))) {
            return false;
        }
        JsonObject params = selectedAction.params;
        if (params == null) {
            return false;
        }
        if (params.has("pickupFilterMode")) {
            return "CUSTOM".equalsIgnoreCase(jsonValue(params.get("pickupFilterMode")));
        }
        return !InventoryItemFilterExpressionEngine.readExpressions(params).isEmpty();
    }

    private void drawStructuredListRow(FontRenderer font, ModernMainLayout.Rect row,
            ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        if (selectedAction != structuredEditingAction) {
            structuredEditingAction = selectedAction;
            structuredEditingKey = field.key;
            structuredEditingIndex = -1;
            structuredPlayerMode = PlayerListTriggerSupport.MODE_EXACT;
        }
        List<StructuredEntry> entries = readStructuredEntries(field.key);
        boolean nameOnly = isHuntBlacklistList(field.key) || isSingleValueCardList(field.key);
        boolean branchCaseList = isBranchCaseList(field.key);
        boolean editingThis = field.key.equals(structuredEditingKey);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, field.label, row.x + 10, row.y + 8, ModernUiRenderer.TEXT,
                Math.max(80, row.width - 140));
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.items", String.valueOf(entries.size())), row.right() - 58, row.y + 8,
                ModernUiRenderer.ACCENT, 48);
        ModernUiRenderer.drawText(font, safe(field.hint), row.x + 10, row.y + 23,
                ModernUiRenderer.MUTED_TEXT, Math.max(80, row.width - 20));
        int inputY = row.y + 40;
        int extraWidth = nameOnly ? 0 : ("entries".equals(field.key) ? 76 : 58);
        int addWidth = 72;
        int nameWidth = Math.max(80, row.width - extraWidth - addWidth - (nameOnly ? 32 : 38));
        ModernMainLayout.Rect name = new ModernMainLayout.Rect(row.x + 10, inputY, nameWidth, 22);
        ModernMainLayout.Rect extra = extraWidth <= 0 ? null
                : new ModernMainLayout.Rect(name.right() + 6, inputY, extraWidth, 22);
        ModernMainLayout.Rect add = new ModernMainLayout.Rect((extra == null ? name.right() : extra.right()) + 6,
                inputY, addWidth, 22);
        if ("entries".equals(field.key)) {
            structuredListModeBounds = extra;
        }
        if (editingThis) {
            structuredListAddBounds = add;
        }
        ensureField(structuredNameKey(field.key), 256);
        drawField(font, structuredNameKey(field.key), name, canEditStep(),
                branchCaseList ? "分支键，例如 boss" : isHuntFilterList(field.key) ? "gui.modern.path.wb.u053" : "gui.modern.path.wb.u054", mouseX, mouseY);
        if ("entries".equals(field.key) && extra != null) {
            drawButton(font, extra,
                    PlayerListTriggerSupport.MODE_CONTAINS.equals(structuredPlayerMode) ? "gui.modern.path.wb.u055" : "gui.modern.path.wb.u056",
                    false, false, canEditStep(), mouseX, mouseY);
        } else if (extra != null) {
            ensureField(structuredValueKey(field.key), 16);
            drawField(font, structuredValueKey(field.key), extra, canEditStep(),
                    branchCaseList ? "动作数" : "gui.modern.path.wb.u057", mouseX, mouseY);
        }
        drawButton(font, add, editingThis && structuredEditingIndex >= 0 ? "gui.modern.path.wb.u058" : "gui.modern.path.wb.u059",
                true, false, canEditStep(), mouseX, mouseY);
        structuredListEditorHits.add(new StructuredListEditorHit(field.key, extra, add));
        int y = inputY + 30;
        for (int i = 0; i < entries.size(); i++) {
            StructuredEntry entry = entries.get(i);
            ModernMainLayout.Rect item = new ModernMainLayout.Rect(row.x + 8, y, row.width - 16, 26);
            ModernMainLayout.Rect delete = new ModernMainLayout.Rect(item.right() - 30, item.y + 3, 24, 20);
            boolean hovered = item.contains(mouseX, mouseY);
            boolean selected = editingThis && structuredEditingIndex == i;
            ModernUiRenderer.drawSubtlePanel(item.x, item.y, item.width, item.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String suffix;
            if ("entries".equals(field.key)) {
                suffix = PlayerListTriggerSupport.MODE_CONTAINS.equals(entry.value)
                        ? tr("gui.modern.path.wb.fmt.contains") : tr("gui.modern.path.wb.fmt.exact");
            } else if (nameOnly) {
                suffix = "";
            } else if (branchCaseList) {
                suffix = "  → 动作数 " + (entry.value.isEmpty() ? "0" : entry.value);
            } else {
                suffix = tr("gui.modern.path.wb.fmt.kills", entry.value.isEmpty() || "0".equals(entry.value)
                        ? tr("gui.modern.path.wb.u060") : entry.value);
            }
            ModernUiRenderer.drawText(font, entry.name + suffix, item.x + 8, item.y + 8,
                    ModernUiRenderer.SUBTLE_TEXT, item.width - 46);
            drawButton(font, delete, "gui.modern.path.wb.u049", false, true, canEditStep(), mouseX, mouseY);
            structuredListHits.add(new StructuredListHit(field.key, i, item, delete));
            y += 30;
        }
    }

    private List<StructuredEntry> readStructuredEntries(String key) {
        List<StructuredEntry> entries = new ArrayList<>();
        if (selectedAction == null || selectedAction.params == null) return entries;
        if ("entries".equals(key)) {
            for (PlayerListTriggerSupport.RuleEntry entry : PlayerListTriggerSupport.readEntries(selectedAction.params)) {
                entries.add(new StructuredEntry(entry.name, entry.mode));
            }
            return entries;
        }
        if (isBranchCaseList(key)) {
            JsonElement raw = selectedAction.params.get(key);
            String text = raw == null || raw.isJsonNull() ? "" : jsonValue(raw);
            for (String line : text.split("\\r?\\n|[,;]")) {
                String value = safe(line).trim();
                if (value.isEmpty()) continue;
                int separator = value.indexOf('=');
                if (separator < 0) separator = value.indexOf(':');
                if (separator < 0) {
                    entries.add(new StructuredEntry(value, "0"));
                } else {
                    String name = value.substring(0, separator).trim();
                    String count = value.substring(separator + 1).trim();
                    if (!name.isEmpty()) entries.add(new StructuredEntry(name, count));
                }
            }
            return entries;
        }
        if (isSingleValueCardList(key)) {
            JsonElement raw = selectedAction.params.get(key);
            String text = raw == null || raw.isJsonNull() ? "" : jsonValue(raw);
            for (String line : text.split("\\r?\\n")) {
                String value = safe(line).trim();
                if (!value.isEmpty()) entries.add(new StructuredEntry(value, ""));
            }
            return entries;
        }
        if (isHuntBlacklistList(key)) {
            return readHuntNameListEntries("nameBlacklistEntries", "nameBlacklist", "nameBlacklistText", false);
        }
        return readHuntNameListEntries("nameWhitelistEntries", "nameWhitelist", "nameWhitelistText", true);
    }

    private List<StructuredEntry> readHuntNameListEntries(String entriesKey, String arrayKey, String textKey,
            boolean withKillCount) {
        List<StructuredEntry> entries = new ArrayList<>();
        if (selectedAction == null || selectedAction.params == null) return entries;
        JsonElement raw = selectedAction.params.get(entriesKey);
        if (raw != null && raw.isJsonArray()) {
            for (JsonElement element : raw.getAsJsonArray()) {
                if (element == null || element.isJsonNull()) continue;
                if (element.isJsonObject()) {
                    JsonObject object = element.getAsJsonObject();
                    String name = firstString(object, "name", "keyword", "target", "value");
                    int count = withKillCount ? firstInt(object, "killCount", "count", "kills", "targetCount") : 0;
                    if (!name.trim().isEmpty()) {
                        entries.add(new StructuredEntry(name.trim(), withKillCount ? String.valueOf(count) : ""));
                    }
                } else if (element.isJsonPrimitive() && !element.getAsString().trim().isEmpty()) {
                    entries.add(new StructuredEntry(element.getAsString().trim(), withKillCount ? "0" : ""));
                }
            }
        }
        if (!entries.isEmpty()) return entries;
        JsonElement legacy = selectedAction.params.get(arrayKey);
        if (legacy == null) legacy = selectedAction.params.get(textKey);
        if (legacy != null && legacy.isJsonArray()) {
            for (JsonElement element : legacy.getAsJsonArray()) {
                if (element.isJsonPrimitive() && !element.getAsString().trim().isEmpty()) {
                    entries.add(new StructuredEntry(element.getAsString().trim(), withKillCount ? "0" : ""));
                }
            }
        } else if (legacy != null && legacy.isJsonPrimitive()) {
            for (String token : legacy.getAsString().split("[,;\\r\\n]+")) {
                if (!token.trim().isEmpty()) {
                    entries.add(new StructuredEntry(token.trim(), withKillCount ? "0" : ""));
                }
            }
        }
        return entries;
    }

    private void applyStructuredListEntry() {
        if (!canEditStep() || selectedAction == null || structuredEditingKey.isEmpty()) return;
        ModernTextField nameField = fields.get(structuredNameKey(structuredEditingKey));
        ModernTextField valueField = fields.get(structuredValueKey(structuredEditingKey));
        String name = safe(nameField == null ? "" : nameField.getText()).trim();
        if (isHuntFilterList(structuredEditingKey)) {
            name = KillAuraHandler.normalizeFilterName(name);
        }
        if (name.isEmpty()) {
            status("gui.modern.path.wb.u061");
            return;
        }
        List<StructuredEntry> entries = readStructuredEntries(structuredEditingKey);
        String value;
        if ("entries".equals(structuredEditingKey)) {
            value = structuredPlayerMode;
        } else if (isHuntBlacklistList(structuredEditingKey) || isSingleValueCardList(structuredEditingKey)) {
            value = "";
        } else {
            int count = 0;
            try {
                String raw = safe(valueField == null ? "" : valueField.getText()).trim();
                if (!raw.isEmpty()) count = Math.max(0, Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
                status("gui.modern.path.wb.u062");
                return;
            }
            value = String.valueOf(count);
        }
        StructuredEntry next = new StructuredEntry(name, value);
        int target = structuredEditingIndex;
        if (target < 0 || target >= entries.size()) {
            target = -1;
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).name.equalsIgnoreCase(name)) {
                    target = i;
                    break;
                }
            }
        }
        if (target >= 0) entries.set(target, next);
        else entries.add(next);
        pushHistory("edit-structured-action-list");
        writeStructuredEntries(structuredEditingKey, entries);
        clearStructuredListEditor();
        dirty = true;
    }

    private void selectStructuredListEntry(StructuredListHit hit) {
        if (hit == null) return;
        List<StructuredEntry> entries = readStructuredEntries(hit.key);
        if (hit.index < 0 || hit.index >= entries.size()) return;
        StructuredEntry entry = entries.get(hit.index);
        structuredEditingKey = hit.key;
        structuredEditingIndex = hit.index;
        ModernTextField nameField = fields.get(structuredNameKey(hit.key));
        ModernTextField valueField = fields.get(structuredValueKey(hit.key));
        if (nameField != null) {
            nameField.setText(entry.name);
            focusField(structuredNameKey(hit.key), nameField.x, nameField.y, 0);
        }
        if ("entries".equals(hit.key)) {
            structuredPlayerMode = PlayerListTriggerSupport.MODE_CONTAINS.equals(entry.value)
                    ? PlayerListTriggerSupport.MODE_CONTAINS : PlayerListTriggerSupport.MODE_EXACT;
        } else if (valueField != null && !isHuntBlacklistList(hit.key) && !isSingleValueCardList(hit.key)) {
            valueField.setText("0".equals(entry.value) ? "" : entry.value);
        }
    }

    private void deleteStructuredListEntry(StructuredListHit hit) {
        if (hit == null || !canEditStep()) return;
        List<StructuredEntry> entries = readStructuredEntries(hit.key);
        if (hit.index < 0 || hit.index >= entries.size()) return;
        pushHistory("delete-structured-action-list-entry");
        entries.remove(hit.index);
        writeStructuredEntries(hit.key, entries);
        clearStructuredListEditor();
        dirty = true;
    }

    private void writeStructuredEntries(String key, List<StructuredEntry> entries) {
        if (selectedAction == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        if ("entries".equals(key)) {
            List<PlayerListTriggerSupport.RuleEntry> rules = new ArrayList<>();
            for (StructuredEntry entry : entries) {
                rules.add(new PlayerListTriggerSupport.RuleEntry(entry.name, entry.value));
            }
            PlayerListTriggerSupport.writeEntries(selectedAction.params, rules);
            return;
        }
        if (isBranchCaseList(key)) {
            StringBuilder builder = new StringBuilder();
            Set<String> unique = new LinkedHashSet<>();
            for (StructuredEntry entry : entries) {
                String name = safe(entry.name).trim();
                if (name.isEmpty() || !unique.add(name.toLowerCase(Locale.ROOT))) continue;
                String count = safe(entry.value).trim();
                if (count.isEmpty()) count = "0";
                if (builder.length() > 0) builder.append('\n');
                builder.append(name).append('=').append(count);
            }
            selectedAction.params.addProperty(key, builder.toString());
            return;
        }
        if (isSingleValueCardList(key)) {
            StringBuilder builder = new StringBuilder();
            Set<String> unique = new LinkedHashSet<>();
            for (StructuredEntry entry : entries) {
                String value = safe(entry.name).trim();
                if (value.isEmpty() || !unique.add(value.toLowerCase(Locale.ROOT))) continue;
                if (builder.length() > 0) builder.append('\n');
                builder.append(value);
            }
            selectedAction.params.addProperty(key, builder.toString());
            return;
        }
        boolean blacklist = isHuntBlacklistList(key);
        JsonArray objects = new JsonArray();
        JsonArray names = new JsonArray();
        Set<String> unique = new LinkedHashSet<>();
        for (StructuredEntry entry : entries) {
            String name = KillAuraHandler.normalizeFilterName(entry.name);
            if (name.isEmpty() || !unique.add(name.toLowerCase(Locale.ROOT))) continue;
            names.add(name);
            if (blacklist) {
                objects.add(name);
                continue;
            }
            JsonObject object = new JsonObject();
            object.addProperty("name", name);
            int count = 0;
            try {
                count = Math.max(0, Integer.parseInt(entry.value));
            } catch (NumberFormatException ignored) {
            }
            if (count > 0) object.addProperty("killCount", count);
            objects.add(object);
        }
        String entriesKey = blacklist ? "nameBlacklistEntries" : "nameWhitelistEntries";
        String arrayKey = blacklist ? "nameBlacklist" : "nameWhitelist";
        String textKey = blacklist ? "nameBlacklistText" : "nameWhitelistText";
        selectedAction.params.remove(textKey);
        if (objects.size() == 0) {
            selectedAction.params.remove(entriesKey);
            selectedAction.params.remove(arrayKey);
        } else {
            selectedAction.params.add(entriesKey, objects);
            selectedAction.params.add(arrayKey, names);
        }
    }

    private void clearStructuredListEditor() {
        structuredEditingIndex = -1;
        ModernTextField nameField = fields.get(structuredNameKey(structuredEditingKey));
        ModernTextField valueField = fields.get(structuredValueKey(structuredEditingKey));
        if (nameField != null) nameField.setText("");
        if (valueField != null) valueField.setText("");
        structuredPlayerMode = PlayerListTriggerSupport.MODE_EXACT;
        clearFocus();
    }

    private String structuredNameKey(String listKey) {
        return "structured.list.name." + safe(listKey);
    }

    private String structuredValueKey(String listKey) {
        return "structured.list.value." + safe(listKey);
    }

    private boolean isHuntFilterList(String key) {
        return isHuntWhitelistList(key) || isHuntBlacklistList(key);
    }

    private boolean isHuntWhitelistList(String key) {
        return "nameWhitelistEntries".equals(key);
    }

    private boolean isHuntBlacklistList(String key) {
        return "nameBlacklistEntries".equals(key) || "nameBlacklist".equals(key);
    }

    private boolean isBranchCaseList(String key) {
        return "casesText".equals(key);
    }

    private boolean isSingleValueCardList(String key) {
        return "pointsText".equals(key);
    }

    private void drawHuntEntityNameSuggestions(FontRenderer font, int mouseX, int mouseY) {
        huntEntitySuggestionBounds = null;
        huntEntitySuggestionHits.clear();
        huntEntitySuggestionValues.clear();
        if (selectedAction == null || !"hunt".equalsIgnoreCase(safe(selectedAction.type))) return;
        if (focusedFieldKey == null || !focusedFieldKey.startsWith("structured.list.name.")) return;
        String listKey = focusedFieldKey.substring("structured.list.name.".length());
        if (!isHuntFilterList(listKey)) return;
        ModernMainLayout.Rect nameBounds = fieldBounds.get(focusedFieldKey);
        if (nameBounds == null) return;
        if (actionPageParameterViewportBounds != null
                && (nameBounds.bottom() < actionPageParameterViewportBounds.y
                        || nameBounds.y > actionPageParameterViewportBounds.bottom())) {
            return;
        }
        ModernTextField nameField = fields.get(focusedFieldKey);
        String query = PinyinSearchHelper.normalizeQuery(nameField == null ? "" : nameField.getText());
        List<String> names = scanNearbyEntityNames(query);
        int rowHeight = 20;
        int visible = Math.min(8, Math.max(1, names.isEmpty() ? 1 : names.size()));
        huntEntitySuggestionMaxScroll = Math.max(0, names.size() - visible);
        huntEntitySuggestionScroll = clamp(huntEntitySuggestionScroll, 0, huntEntitySuggestionMaxScroll);
        int height = 8 + visible * rowHeight;
        int y = nameBounds.bottom() + 4;
        if (actionPageParamsBounds != null && y + height > actionPageParamsBounds.bottom() - 32) {
            y = Math.max(actionPageParamsBounds.y + 8, nameBounds.y - 4 - height);
        }
        huntEntitySuggestionBounds = new ModernMainLayout.Rect(nameBounds.x, y,
                Math.max(160, nameBounds.width + 80), height);
        ModernUiRenderer.drawPanel(huntEntitySuggestionBounds.x, huntEntitySuggestionBounds.y,
                huntEntitySuggestionBounds.width, huntEntitySuggestionBounds.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        if (names.isEmpty()) {
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.no_entity", formatHuntScanRadius()),
                    huntEntitySuggestionBounds.x + 8, huntEntitySuggestionBounds.y + 6,
                    ModernUiRenderer.MUTED_TEXT, huntEntitySuggestionBounds.width - 16);
            return;
        }
        int rowY = huntEntitySuggestionBounds.y + 4;
        for (int i = huntEntitySuggestionScroll; i < names.size()
                && i < huntEntitySuggestionScroll + visible; i++) {
            String name = names.get(i);
            ModernMainLayout.Rect item = new ModernMainLayout.Rect(huntEntitySuggestionBounds.x + 4, rowY,
                    huntEntitySuggestionBounds.width - 8, rowHeight - 2);
            boolean hovered = item.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(item.x, item.y, item.width, item.height, 3,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, name, item.x + 6, item.y + 5, ModernUiRenderer.TEXT, item.width - 12);
            huntEntitySuggestionHits.add(item);
            huntEntitySuggestionValues.add(name);
            rowY += rowHeight;
        }
    }

    private boolean handleHuntEntitySuggestionClick(int mouseX, int mouseY) {
        if (huntEntitySuggestionBounds == null) return false;
        if (!huntEntitySuggestionBounds.contains(mouseX, mouseY)) return false;
        for (int i = 0; i < huntEntitySuggestionHits.size() && i < huntEntitySuggestionValues.size(); i++) {
            if (huntEntitySuggestionHits.get(i).contains(mouseX, mouseY)) {
                applyHuntEntitySuggestion(huntEntitySuggestionValues.get(i));
                return true;
            }
        }
        return true;
    }

    private void applyHuntEntitySuggestion(String name) {
        if (focusedFieldKey == null || !focusedFieldKey.startsWith("structured.list.name.")) return;
        structuredEditingKey = focusedFieldKey.substring("structured.list.name.".length());
        ModernTextField nameField = fields.get(focusedFieldKey);
        if (nameField != null) nameField.setText(safe(name).trim());
        applyStructuredListEntry();
    }

    private List<String> scanNearbyEntityNames(String query) {
        List<String> result = new ArrayList<>();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.player == null || mc.world == null || mc.world.loadedEntityList == null) {
            return result;
        }
        float radius = huntScanRadius();
        double maxDistSq = radius * radius;
        LinkedHashMap<String, String> unique = new LinkedHashMap<>();
        for (Object raw : mc.world.loadedEntityList) {
            if (!(raw instanceof Entity)) continue;
            Entity entity = (Entity) raw;
            if (entity == mc.player) continue;
            if (mc.player.getDistanceSq(entity) > maxDistSq) continue;
            String display = entity.getDisplayName() == null ? "" : entity.getDisplayName().getUnformattedText();
            String name = KillAuraHandler.normalizeFilterName(display);
            if (name.isEmpty()) name = KillAuraHandler.normalizeFilterName(entity.getName());
            if (name.isEmpty()) continue;
            String key = name.toLowerCase(Locale.ROOT);
            if (!unique.containsKey(key)) unique.put(key, name);
        }
        for (String name : unique.values()) {
            if (query == null || query.isEmpty() || PinyinSearchHelper.matchesNormalized(name, query)) {
                result.add(name);
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    private float huntScanRadius() {
        ModernTextField field = fields.get("param.scanRadius");
        if (field != null) {
            try {
                String raw = safe(field.getText()).trim();
                if (!raw.isEmpty()) return (float) Math.max(1.0D, Double.parseDouble(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        if (selectedAction != null && selectedAction.params != null && selectedAction.params.has("scanRadius")) {
            try {
                return (float) Math.max(1.0D, selectedAction.params.get("scanRadius").getAsDouble());
            } catch (Exception ignored) {
            }
        }
        return 10.0F;
    }

    private String formatHuntScanRadius() {
        float radius = huntScanRadius();
        if (Math.abs(radius - Math.rint(radius)) < 1.0E-4F) {
            return String.valueOf((long) Math.rint(radius));
        }
        return String.format(Locale.ROOT, "%.1f", radius);
    }

    private String firstString(JsonObject object, String... keys) {
        if (object == null) return "";
        for (String key : keys) {
            if (object.has(key) && object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
        }
        return "";
    }

    private int firstInt(JsonObject object, String... keys) {
        if (object == null) return 0;
        for (String key : keys) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive()) continue;
            try {
                return Math.max(0, object.get(key).getAsInt());
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private boolean shouldShowSlotGrid(ModernActionEditorSchema.Field field) {
        if (field == null || !"slotIndices".equals(field.key)) return true;
        if (selectedAction == null || selectedAction.params == null || !selectedAction.params.has("slotArea")) return true;
        return "MAIN".equalsIgnoreCase(safe(selectedAction.params.get("slotArea").getAsString()));
    }

    private void drawSlotGridRow(FontRenderer font, ModernMainLayout.Rect row,
            ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        int rows = slotGridRows(field);
        int cols = slotGridCols(field);
        SlotCanvasWidget.Layout layout = slotGridLayoutFor(rows, cols);
        if (field.key.equals(slotGridDragKey)) {
            slotGridDragLayout = layout;
        }
        Set<Integer> selected = readSlotGridSelection(field.key);
        Set<Integer> preview = slotGridMachine.hasActiveRect() && field.key.equals(slotGridDragKey)
                ? slotGridMachine.preview(layout) : selected;
        Set<Integer> rect = slotGridMachine.hasActiveRect() && field.key.equals(slotGridDragKey)
                ? SlotCanvasWidget.rectangle(layout, slotGridMachine.anchor, slotGridMachine.current) : null;
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, field.label, row.x + 10, row.y + 8, ModernUiRenderer.TEXT,
                Math.max(80, row.width - 150));
        String count = preview.isEmpty() ? "gui.modern.path.wb.u063" : tr("gui.modern.path.wb.fmt.selected_count", String.valueOf(preview.size()));
        ModernUiRenderer.drawText(font, count, row.right() - 112, row.y + 8, ModernUiRenderer.ACCENT, 102);
        ModernUiRenderer.drawText(font, safe(field.hint), row.x + 10, row.y + 23,
                ModernUiRenderer.MUTED_TEXT, Math.max(80, row.width - 20));
        int gap = 2;
        int cell = Math.max(14, Math.min(22,
                (row.width - 20 - Math.max(0, cols - 1) * gap) / Math.max(1, cols)));
        int gridWidth = cols * cell + Math.max(0, cols - 1) * gap;
        int gridX = row.x + Math.max(10, (row.width - gridWidth) / 2);
        int gridY = row.y + 42;
        for (int line = 0; line < rows; line++) {
            for (int col = 0; col < cols; col++) {
                int index = line * cols + col;
                ModernMainLayout.Rect cellBounds = new ModernMainLayout.Rect(gridX + col * (cell + gap),
                        gridY + line * (cell + gap), cell, cell);
                slotGridHits.add(new SlotGridHit(field.key, index, rows, cols, cellBounds));
                boolean active = preview.contains(Integer.valueOf(index));
                boolean hovered = cellBounds.contains(mouseX, mouseY);
                boolean inRect = rect != null && rect.contains(Integer.valueOf(index));
                ModernUiRenderer.drawSubtlePanel(cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height, 3,
                        active ? hovered ? 0xFF4B8BB2 : 0xFF2F6F95
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                if (inRect) {
                    ModernUiRenderer.drawSubtlePanel(cellBounds.x, cellBounds.y, cellBounds.width, cellBounds.height, 3,
                            SlotCanvasWidget.RECT_FILL, SlotCanvasWidget.RECT_BORDER);
                }
                drawCenteredButtonText(font, String.valueOf(index), cellBounds,
                        active ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
            }
        }
    }

    private int slotGridRows(ModernActionEditorSchema.Field field) {
        if (field == null) return 1;
        if ("chestSlots".equals(field.key)) return parameterInt("chestRows", 6, 1, 12);
        if ("inventorySlots".equals(field.key)) return parameterInt("inventoryRows", 4, 1, 8);
        return 4;
    }

    private int slotGridCols(ModernActionEditorSchema.Field field) {
        if (field == null) return 1;
        if ("chestSlots".equals(field.key)) return parameterInt("chestCols", 9, 1, 12);
        if ("inventorySlots".equals(field.key)) return parameterInt("inventoryCols", 9, 1, 12);
        return 9;
    }

    private int parameterInt(String key, int fallback, int min, int max) {
        if (selectedAction == null || selectedAction.params == null || !selectedAction.params.has(key)) return fallback;
        try {
            return clamp(selectedAction.params.get(key).getAsInt(), min, max);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Set<Integer> readSlotGridSelection(String key) {
        Set<Integer> selected = new LinkedHashSet<>();
        if (selectedAction == null || selectedAction.params == null || !selectedAction.params.has(key)) return selected;
        JsonElement raw = selectedAction.params.get(key);
        if (raw == null || !raw.isJsonArray()) return selected;
        for (JsonElement element : raw.getAsJsonArray()) {
            try {
                selected.add(element.getAsInt());
            } catch (Exception ignored) {
            }
        }
        return selected;
    }

    private SlotCanvasWidget.Layout slotGridLayoutFor(int rows, int cols) {
        int safeRows = Math.max(1, rows);
        int safeCols = Math.max(1, cols);
        return SlotCanvasWidget.layout(new ModernMainLayout.Rect(0, 0, safeCols * 22, safeRows * 22),
                safeRows, safeCols, safeRows * safeCols);
    }

    private int slotGridIndexAt(int mouseX, int mouseY) {
        for (SlotGridHit hit : slotGridHits) {
            if (hit.key.equals(slotGridDragKey) && hit.bounds.contains(mouseX, mouseY)) {
                return hit.index;
            }
        }
        return -1;
    }

    private boolean beginSlotGridPress(SlotGridHit hit) {
        if (hit == null || !canEditStep() || selectedAction == null) return false;
        boolean ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        slotGridDragKey = hit.key;
        slotGridDragLayout = slotGridLayoutFor(hit.rows, hit.cols);
        slotGridMachine.reset();
        slotGridMachine.dragging = true;
        slotGridMachine.removeMode = shift && !ctrl;
        slotGridMachine.anchor = hit.index;
        slotGridMachine.current = hit.index;
        slotGridMachine.focusIndex = hit.index;
        slotGridMachine.snapshot.addAll(readSlotGridSelection(hit.key));
        if (ctrl) {
            slotGridMachine.snapshot.clear();
        }
        slotGridPressIndex = hit.index;
        slotGridPressMoved = false;
        slotGridPressExclusive = ctrl;
        slotGridPressRemove = shift && !ctrl;
        return true;
    }

    private void commitSlotGridPress() {
        String key = slotGridDragKey;
        SlotCanvasWidget.Layout layout = slotGridDragLayout;
        int index = slotGridPressIndex;
        boolean moved = slotGridPressMoved;
        boolean exclusive = slotGridPressExclusive;
        boolean remove = slotGridPressRemove;
        int capacity = layout == null ? 0 : layout.capacity();
        if (moved && layout != null) {
            Set<Integer> rect = SlotCanvasWidget.rectangle(layout, slotGridMachine.anchor, slotGridMachine.current);
            Set<Integer> next = new LinkedHashSet<Integer>(slotGridMachine.snapshot);
            if (exclusive) {
                next.clear();
                next.addAll(rect);
            } else if (slotGridMachine.removeMode) {
                next.removeAll(rect);
            } else if (!rect.isEmpty() && next.containsAll(rect)) {
                next.removeAll(rect);
            } else {
                next.addAll(rect);
            }
            writeSlotGridSelection(key, next, capacity);
        } else if (remove) {
            Set<Integer> selected = readSlotGridSelection(key);
            if (selected.remove(Integer.valueOf(index))) {
                writeSlotGridSelection(key, selected, capacity);
            }
        } else {
            toggleSlotGridIndex(key, index, exclusive, capacity);
        }
        resetSlotGridPress();
    }

    private void resetSlotGridPress() {
        slotGridMachine.reset();
        slotGridDragLayout = null;
        slotGridDragKey = "";
        slotGridPressIndex = -1;
        slotGridPressMoved = false;
        slotGridPressRemove = false;
        slotGridPressExclusive = false;
    }

    private void toggleSlotGridIndex(String key, int index, boolean exclusive, int capacity) {
        if (key == null || !canEditStep() || selectedAction == null || index < 0) return;
        Set<Integer> selected = readSlotGridSelection(key);
        Integer boxed = Integer.valueOf(index);
        if (exclusive) {
            selected.clear();
            selected.add(boxed);
        } else if (!selected.add(boxed)) {
            selected.remove(boxed);
        }
        writeSlotGridSelection(key, selected, capacity);
    }

    private void writeSlotGridSelection(String key, Set<Integer> selected, int capacity) {
        if (key == null || !canEditStep() || selectedAction == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        int max = Math.max(1, capacity);
        pushHistory("toggle-slot-selection");
        ActionEditorJson.writeSortedSlots(selectedAction.params, key, selected, max);
        if ("slotIndices".equals(key) && selected != null && !selected.isEmpty()) {
            int first = Integer.MAX_VALUE;
            for (Integer slot : selected) {
                if (slot != null && slot.intValue() < first) first = slot.intValue();
            }
            if (first != Integer.MAX_VALUE) {
                selectedAction.params.addProperty("slotIndex", first);
            }
        }
        dirty = true;
    }

    private void drawVariableNameControl(FontRenderer font, ModernMainLayout.Rect control,
            ModernActionEditorSchema.Field field, String value, boolean enabled, int mouseX, int mouseY) {
        ensureVariableScopeContext();
        String current = safe(value).trim();
        String scope = variableScopeFor(field.key, current);
        int scopeWidth = Math.min(132, Math.max(102, control.width / 3));
        ModernMainLayout.Rect scopeBounds = new ModernMainLayout.Rect(control.x, control.y, scopeWidth, control.height);
        ModernMainLayout.Rect nameBounds = new ModernMainLayout.Rect(scopeBounds.right() + 6, control.y,
                Math.max(1, control.width - scopeWidth - 6), control.height);
        boolean hovered = enabled && scopeBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(scopeBounds.x, scopeBounds.y, scopeBounds.width, scopeBounds.height, 4,
                !enabled ? 0xFF151E26 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered && enabled ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernMainLayout.Rect scopeLabel = new ModernMainLayout.Rect(scopeBounds.x, scopeBounds.y,
                Math.max(1, scopeBounds.width - 16), scopeBounds.height);
        drawCenteredButtonText(font, ActionVariableRegistry.scopeKeyToDisplay(scope), scopeLabel,
                !enabled ? ModernUiRenderer.MUTED_TEXT
                        : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawChevron(scopeBounds.right() - 15, scopeBounds.y + 8, false,
                enabled && hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        parameterHits.add(new ParameterHit("varscope:" + field.key, scopeBounds, field));
        String fieldKey = "param." + field.key;
        ensureField(fieldKey, 32767);
        setFieldTextIfUnchanged(fieldKey, ActionVariableRegistry.extractBaseName(current));
        drawField(font, fieldKey, nameBounds, enabled, field.hint, mouseX, mouseY);
    }

    private boolean isSourceVariablePickerField(ModernActionEditorSchema.Field field) {
        return field != null && selectedAction != null && "set_var".equalsIgnoreCase(safe(selectedAction.type))
                && "fromVar".equals(field.key);
    }

    private void ensureVariableScopeContext() {
        if (variableScopeAction != selectedAction) {
            variableScopeAction = selectedAction;
            variableScopeOverrides.clear();
        }
    }

    private String variableScopeFor(String key, String currentValue) {
        String override = key == null ? null : variableScopeOverrides.get(key);
        if (override != null && !override.isEmpty()) {
            return ActionVariableRegistry.normalizeScopeKey(override);
        }
        return ActionVariableRegistry.extractScopeKey(currentValue);
    }

    private void openVariableScopeMenu(ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        if (field == null || selectedAction == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        ensureVariableScopeContext();
        String current = jsonValue(selectedAction.params.get(field.key));
        String currentScope = variableScopeFor(field.key, current);
        menuTitle = "gui.modern.path.wb.u064";
        menuItems.clear();
        String[] scopes = new String[] { "sequence", "global", "local", "temp" };
        for (final String scope : scopes) {
            String label = ActionVariableRegistry.scopeKeyToDisplay(scope);
            RectAction item = new RectAction(label, true, false, () -> applyVariableScope(field, scope));
            item.selected = scope.equalsIgnoreCase(currentScope);
            menuItems.add(item);
        }
        setMenuAnchor(mouseX, mouseY);
    }

    private void applyVariableScope(ModernActionEditorSchema.Field field, String scope) {
        if (field == null || selectedAction == null || !canEditStep()) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        ensureVariableScopeContext();
        String normalized = ActionVariableRegistry.normalizeScopeKey(scope);
        ModernTextField nameField = fields.get("param." + field.key);
        String base = nameField == null
                ? ActionVariableRegistry.extractBaseName(jsonValue(selectedAction.params.get(field.key)))
                : safe(nameField.getText()).trim();
        String composed = ActionVariableRegistry.buildScopedVariableName(normalized, base);
        String current = jsonValue(selectedAction.params.get(field.key)).trim();
        if (normalized.equals(variableScopeOverrides.get(field.key)) && composed.equals(current)) {
            return;
        }
        pushHistory("choose-variable-scope");
        variableScopeOverrides.put(field.key, normalized);
        selectedAction.params.addProperty(field.key, composed);
        dirty = true;
        bindEditorFields();
    }

    private void syncVariableNameField(ModernActionEditorSchema.Field fieldSpec, ModernTextField field) {
        if (fieldSpec == null || field == null || selectedAction == null || selectedAction.params == null) {
            return;
        }
        ensureVariableScopeContext();
        String base = safe(field.getText()).trim();
        String current = jsonValue(selectedAction.params.get(fieldSpec.key)).trim();
        String scope = variableScopeFor(fieldSpec.key, current);
        String composed = ActionVariableRegistry.buildScopedVariableName(scope, base);
        if (composed.equals(current)) {
            return;
        }
        selectedAction.params.addProperty(fieldSpec.key, composed);
        dirty = true;
    }

    private boolean shouldDrawSpinner(ModernActionEditorSchema.Field field) {
        return field != null && (field.kind == ModernActionEditorSchema.Kind.SPINNER
                || field.kind == ModernActionEditorSchema.Kind.TEXT
                        && ActionEditorJson.isSpinnerKey(selectedAction == null ? "" : selectedAction.type, field.key));
    }

    private void drawSpinnerControl(FontRenderer font, ModernMainLayout.Rect control,
            ModernActionEditorSchema.Field field, String value, boolean enabled, int mouseX, int mouseY) {
        int buttonWidth = Math.min(34, Math.max(28, control.width / 8));
        ModernMainLayout.Rect minus = new ModernMainLayout.Rect(control.x, control.y, buttonWidth, 24);
        ModernMainLayout.Rect plus = new ModernMainLayout.Rect(control.right() - buttonWidth, control.y, buttonWidth, 24);
        ModernMainLayout.Rect valueRect = new ModernMainLayout.Rect(minus.right() + 4, control.y,
                Math.max(20, plus.x - minus.right() - 8), 24);
        drawSpinnerButton(font, minus, "-", enabled, mouseX, mouseY);
        drawSpinnerButton(font, plus, "+", enabled, mouseX, mouseY);
        String display = value == null || value.trim().isEmpty() ? field.defaultValue : value;
        String fieldKey = "param." + field.key;
        ensureField(fieldKey, 32);
        setFieldTextIfUnchanged(fieldKey, display);
        drawField(font, fieldKey, valueRect, enabled, "gui.modern.path.wb.u065", mouseX, mouseY);
        parameterHits.add(new ParameterHit("spinner-:" + field.key, minus, field));
        parameterHits.add(new ParameterHit("spinner+:" + field.key, plus, field));
    }

    private void drawSpinnerButton(FontRenderer font, ModernMainLayout.Rect rect, String glyph,
            boolean enabled, int mouseX, int mouseY) {
        drawButton(font, rect, "", false, false, enabled, mouseX, mouseY);
        if (font == null || rect == null) return;
        String text = tr(glyph);
        int width = font.getStringWidth(text);
        if (width <= 0) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(rect.x + rect.width / 2.0F, rect.y + rect.height / 2.0F, 0.0F);
        GlStateManager.scale(1.45F, 1.45F, 1.0F);
        font.drawStringWithShadow(text, -width / 2.0F, -font.FONT_HEIGHT / 2.0F,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        GlStateManager.popMatrix();
    }

    private void drawHotbarControl(FontRenderer font, ModernMainLayout.Rect row, ModernMainLayout.Rect control,
            ModernActionEditorSchema.Field field, String value, boolean enabled, int mouseX, int mouseY) {
        int current = 1;
        try {
            current = Integer.parseInt(safe(value).trim());
        } catch (Exception ignored) {
            current = ActionEditorUxSupport.spinnerMin(field.key);
        }
        int slotWidth = Math.max(18, Math.min(28, (control.width - 16) / 9));
        for (int i = 1; i <= 9; i++) {
            ModernMainLayout.Rect slot = new ModernMainLayout.Rect(control.x + (i - 1) * (slotWidth + 2), control.y,
                    slotWidth, 24);
            boolean active = i == current || ("tempslot".equals(field.key) && i - 1 == current);
            ModernUiRenderer.drawSubtlePanel(slot.x, slot.y, slot.width, slot.height, 3,
                    active ? ModernUiRenderer.SURFACE_PRESSED : slot.contains(mouseX, mouseY)
                            ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            drawCenteredButtonText(font, String.valueOf(i), slot,
                    active ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
            parameterHits.add(new ParameterHit("hotbar:" + field.key + ":" + i, slot, field));
        }
    }

    private void nudgeSpinner(ModernActionEditorSchema.Field field, int delta) {
        if (field == null || selectedAction == null || !canEditStep()) {
            return;
        }
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        clearFocus();
        int min = ActionEditorUxSupport.spinnerMin(field.key);
        int max = ActionEditorUxSupport.spinnerMax(field.key);
        int current = ActionEditorJson.readInt(selectedAction.params, field.key,
                parseDefaultInt(field.defaultValue, min), min, max);
        pushHistory("nudge-action-spinner");
        selectedAction.params.addProperty(field.key, ActionEditorJson.clamp(current + delta, min, max));
        bindEditorFields();
        dirty = true;
    }

    private int parseDefaultInt(String value, int fallback) {
        try {
            return Integer.parseInt(safe(value).trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void captureCurrentValue(ModernActionEditorSchema.Field field) {
        if (field == null || selectedAction == null || !canEditStep()) {
            return;
        }
        clearFocus();
        if ("x".equals(field.key) || "y".equals(field.key)) {
            ModernMouseCapture.Capture capture = ModernMouseCapture.getLastBeforeMenu();
            if (capture == null) {
                status("gui.modern.path.wb.u066");
                return;
            }
            if (selectedAction.params == null) {
                selectedAction.params = new JsonObject();
            }
            pushHistory("capture-screen-click-coordinates");
            selectedAction.params.addProperty("x", capture.getX());
            selectedAction.params.addProperty("y", capture.getY());
            selectedAction.params.addProperty("originalWidth", capture.getWidth());
            selectedAction.params.addProperty("originalHeight", capture.getHeight());
            bindEditorFields();
            dirty = true;
            return;
        }
        EntityPlayerSP player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            status("gui.modern.path.wb.u067");
            return;
        }
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        pushHistory("capture-action-value");
        if ("yaw".equals(field.key)) {
            selectedAction.params.addProperty("yaw", Math.round(player.rotationYaw * 10f) / 10f);
        } else if ("pitch".equals(field.key)) {
            selectedAction.params.addProperty("pitch", Math.round(player.rotationPitch * 10f) / 10f);
        } else if ("pos".equals(field.key) || "center".equals(field.key)) {
            selectedAction.params.addProperty(field.key, "[" + Math.round(player.posX) + "," + Math.round(player.posY)
                    + "," + Math.round(player.posZ) + "]");
        } else if ("regionRect".equals(field.key)) {
            selectedAction.params.addProperty("regionRect", "[0,0,50,50]");
        }
        bindEditorFields();
        dirty = true;
    }

    private boolean isScreenClickCoordinateField(ModernActionEditorSchema.Field field) {
        return field != null && selectedAction != null && ("x".equals(field.key) || "y".equals(field.key))
                && "click".equalsIgnoreCase(safe(selectedAction.type));
    }

    private String screenClickCoordinateInfo() {
        JsonObject params = selectedAction == null ? null : selectedAction.params;
        int currentWidth = minecraft == null ? 1 : Math.max(1, minecraft.displayWidth);
        int currentHeight = minecraft == null ? 1 : Math.max(1, minecraft.displayHeight);
        int storedWidth = parameterInt(params, "originalWidth", 2560);
        int storedHeight = parameterInt(params, "originalHeight", 1334);
        int sourceWidth = storedWidth > 0 ? storedWidth : 2560;
        int sourceHeight = storedHeight > 0 ? storedHeight : 1334;
        int sourceX = parameterInt(params, "x", 0);
        int sourceY = parameterInt(params, "y", 0);
        int correctedX = scaleScreenCoordinate(sourceX, sourceWidth, currentWidth);
        int correctedY = scaleScreenCoordinate(sourceY, sourceHeight, currentHeight);
        return tr("gui.modern.path.wb.fmt.xy_record", String.valueOf(sourceX), String.valueOf(sourceY),
                String.valueOf(sourceWidth), String.valueOf(sourceHeight), String.valueOf(currentWidth),
                String.valueOf(currentHeight), String.valueOf(correctedX), String.valueOf(correctedY));
    }

    private int parameterInt(JsonObject params, String key, int fallback) {
        if (params == null || key == null || !params.has(key)) {
            return fallback;
        }
        try {
            return params.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private int scaleScreenCoordinate(int coordinate, int sourceSize, int currentSize) {
        int source = Math.max(1, sourceSize);
        int current = Math.max(1, currentSize);
        return Math.max(0, Math.min(current - 1,
                (int) Math.round(coordinate * (double) current / source)));
    }

    private boolean isSystemMessageField(ModernActionEditorSchema.Field field) {
        return field != null && "message".equals(field.key) && selectedAction != null
                && "system_message".equalsIgnoreCase(safe(selectedAction.type));
    }

    private boolean isActionIntegerField(ModernActionEditorSchema.Field field) {
        return field != null && field.key != null && selectedAction != null
                && (field.kind == ModernActionEditorSchema.Kind.SPINNER
                        || ActionEditorJson.isSpinnerKey(selectedAction.type, field.key));
    }

    private boolean isActionSectionEnabled(String section) {
        if (section == null || !"gui.modern.path.wb.u068".equals(section)) {
            return true;
        }
        if (selectedAction == null || selectedAction.params == null
                || !"hunt".equalsIgnoreCase(safe(selectedAction.type))) {
            return true;
        }
        JsonElement whitelist = selectedAction.params.get("enableNameWhitelist");
        return whitelist != null && whitelist.isJsonPrimitive() && whitelist.getAsBoolean();
    }

    private boolean isActionParameterEnabled(ModernActionEditorSchema.Field field) {
        if (field == null || selectedAction == null || selectedAction.params == null) {
            return true;
        }
        // Keep the respawn options interactive even while whitelist mode is
        // currently disabled.  The values are still consumed only when the
        // runtime whitelist condition is active, and users can configure them
        // before enabling that mode.
        if ("hunt".equalsIgnoreCase(safe(selectedAction.type)) && "forceEndHuntTimeoutSeconds".equals(field.key)) {
            JsonElement forceEnd = selectedAction.params.get("forceEndHunt");
            return forceEnd != null && forceEnd.isJsonPrimitive() && forceEnd.getAsBoolean();
        }
        if (!"key".equalsIgnoreCase(safe(selectedAction.type))) {
            return true;
        }
        if (!"customPressDuration".equals(field.key) && !"pressDurationTicks".equals(field.key)) {
            return true;
        }
        String state = jsonValue(selectedAction.params.get("state")).trim();
        boolean press = state.isEmpty() || "Press".equalsIgnoreCase(state);
        if ("customPressDuration".equals(field.key)) {
            return press;
        }
        JsonElement custom = selectedAction.params.get("customPressDuration");
        return press && custom != null && custom.isJsonPrimitive() && custom.getAsBoolean();
    }

    private boolean isActionPickerField(ModernActionEditorSchema.Field field) {
        if (field == null || selectedAction == null) return false;
        String type = safe(selectedAction.type).toLowerCase(Locale.ROOT);
        return "run_sequence".equals(type) && "sequenceName".equals(field.key)
                || "wait_until_captured_id".equals(type) && "capturedId".equals(field.key)
                || "toggle_other_feature".equals(type) && "featureId".equals(field.key)
                || ActionEditorUxSupport.isLabelKey(type, field.key)
                || ActionEditorUxSupport.isActionIndexKey(type, field.key)
                || ActionEditorUxSupport.isTemplateKey(type, field.key);
    }

    private String actionPickerFieldDisplay(ModernActionEditorSchema.Field field, String value) {
        String normalized = safe(value).trim();
        if (normalized.isEmpty()) {
            if ("sequenceName".equals(field.key)) return "gui.modern.path.wb.u069";
            if ("capturedId".equals(field.key)) return "gui.modern.path.wb.u070";
            return "gui.modern.path.wb.u071";
        }
        if ("capturedId".equals(field.key)) {
            for (CapturedIdRuleManager.RuleCard card : CapturedIdRuleManager.getRuleCards()) {
                if (card != null && card.model != null && normalized.equalsIgnoreCase(safe(card.model.name))) {
                    String display = safe(card.model.displayName).trim();
                    return display.isEmpty() ? normalized : display + " (" + normalized + ")";
                }
            }
        } else if ("featureId".equals(field.key)) {
            OtherFeatureGroupManager.reload();
            for (GroupDef group : OtherFeatureGroupManager.getGroups()) {
                if (group == null || group.features == null) continue;
                for (FeatureDef feature : group.features) {
                    if (feature != null && normalized.equalsIgnoreCase(safe(feature.id))) {
                        return safe(feature.name).trim().isEmpty() ? normalized : feature.name;
                    }
                }
            }
        }
        return normalized;
    }

    private void openActionPicker(ModernActionEditorSchema.Field field) {
        actionPickerOptions.clear();
        keyboardKeyHits.clear();
        if (field == null) return;
        actionPickerParamKey = field.key;
        if (field.kind == ModernActionEditorSchema.Kind.KEYBOARD_PICKER) {
            actionPickerMode = ActionPickerMode.KEYBOARD;
        } else if ("sequenceName".equals(field.key)) {
            actionPickerMode = ActionPickerMode.SEQUENCE;
            for (PathSequence sequence : sequences) {
                if (sequence == null || safe(sequence.getName()).trim().isEmpty()) continue;
                if (Boolean.TRUE.equals(hiddenCategories.get(safe(sequence.getCategory()).trim()))) continue;
                String detail = safe(sequence.getCategory()).trim();
                if (!safe(sequence.getSubCategory()).trim().isEmpty()) {
                    detail += (detail.isEmpty() ? "" : " / ") + safe(sequence.getSubCategory()).trim();
                }
                actionPickerOptions.add(new ActionPickerOption(sequence.getName(), sequence.getName(), detail));
            }
        } else if ("capturedId".equals(field.key)) {
            actionPickerMode = ActionPickerMode.CAPTURED_ID;
            for (CapturedIdRuleManager.RuleCard card : CapturedIdRuleManager.getRuleCards()) {
                if (card == null || card.model == null || safe(card.model.name).trim().isEmpty()) continue;
                String label = safe(card.model.displayName).trim();
                if (label.isEmpty()) label = card.model.name;
                actionPickerOptions.add(new ActionPickerOption(card.model.name, label,
                        safe(card.model.category).trim()));
            }
        } else if ("featureId".equals(field.key)) {
            actionPickerMode = ActionPickerMode.OTHER_FEATURE;
            OtherFeatureGroupManager.reload();
            for (GroupDef group : OtherFeatureGroupManager.getGroups()) {
                if (group == null || group.features == null) continue;
                for (FeatureDef feature : group.features) {
                    if (feature == null || safe(feature.id).trim().isEmpty()) continue;
                    actionPickerOptions.add(new ActionPickerOption(feature.id, feature.name,
                            safe(group.name).trim() + (safe(feature.description).trim().isEmpty()
                                    ? "" : " · " + safe(feature.description).trim())));
                }
            }
        } else if (ActionEditorUxSupport.isLabelKey(selectedAction.type, field.key)) {
            actionPickerMode = ActionPickerMode.LABEL;
            if (selectedStep != null) {
                for (int i = 0; i < selectedStep.getActions().size(); i++) {
                    ActionData action = selectedStep.getActions().get(i);
                    if (action == null || !"label".equalsIgnoreCase(safe(action.type))) continue;
                    String name = action.params == null ? "" : jsonValue(action.params.get("labelName")).trim();
                    if (name.isEmpty()) continue;
                    actionPickerOptions.add(new ActionPickerOption(name, name, tr("gui.modern.path.wb.fmt.action_n", String.valueOf(i))));
                }
            }
        } else if (ActionEditorUxSupport.isActionIndexKey(selectedAction.type, field.key)) {
            actionPickerMode = ActionPickerMode.ACTION_INDEX;
            if (selectedStep != null) {
                List<ActionData> actions = selectedStep.getActions();
                for (int i = 0; i < actions.size(); i++) {
                    ActionData action = actions.get(i);
                    String type = action == null ? "" : safe(action.type);
                    actionPickerOptions.add(new ActionPickerOption(String.valueOf(i),
                            i + " · " + (type.isEmpty() ? "?" : type), safe(action == null ? "" : action.getDescription())));
                }
            }
        } else if (ActionEditorUxSupport.isVariableKey(selectedAction.type, field.key)) {
            actionPickerMode = ActionPickerMode.VARIABLE;
            if (isSourceVariablePickerField(field)) addSourceVariablePickerOptions();
            else addDeclaredVariablePickerOptions();
        } else if (ActionEditorUxSupport.isTemplateKey(selectedAction.type, field.key)) {
            actionPickerMode = ActionPickerMode.TEMPLATE;
            for (LegacyActionTemplateManager.TemplateEditModel model : LegacyActionTemplateManager.getTemplateModels()) {
                if (model == null || safe(model.name).trim().isEmpty()) continue;
                actionPickerOptions.add(new ActionPickerOption(model.name, model.name, safe(model.note)));
            }
        } else {
            return;
        }
        actionPickerOptions.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        actionPickerScroll = 0;
        clearFocus();
        ModernTextField search = fields.get("action.picker.search");
        if (search != null) search.setText("");
    }

    private void addDeclaredVariablePickerOptions() {
        for (ActionVariableRegistry.VariableEntry entry : ActionVariableRegistry.collectVariables(sequences)) {
            if (entry == null || safe(entry.getVariableName()).trim().isEmpty()) continue;
            actionPickerOptions.add(new ActionPickerOption(entry.getVariableName(), entry.getVariableName(),
                    ActionVariableRegistry.scopeKeyToDisplay(entry.getScopeKey())));
        }
    }

    private void addSourceVariablePickerOptions() {
        Map<String, String> candidates = new LinkedHashMap<String, String>();
        for (ActionVariableRegistry.VariableEntry entry : ActionVariableRegistry.collectVariables(sequences)) {
            if (entry == null) continue;
            addSourceVariableCandidate(candidates, entry.getVariableName());
            for (ActionVariableRegistry.VariableSource source : entry.getSources()) {
                for (String variableName : ActionVariableRegistry.collectProducedVariableNames(source)) {
                    addSourceVariableCandidate(candidates, variableName);
                }
            }
        }
        for (Map.Entry<String, String> candidate : candidates.entrySet()) {
            actionPickerOptions.add(new ActionPickerOption(candidate.getKey(), candidate.getKey(), candidate.getValue()));
        }
    }

    private void addSourceVariableCandidate(Map<String, String> candidates, String variableName) {
        String canonicalName = ActionVariableRegistry.buildCanonicalVariableName(
                ActionVariableRegistry.extractScopeKey(variableName), ActionVariableRegistry.extractBaseName(variableName));
        if (canonicalName.isEmpty() || candidates.containsKey(canonicalName)) return;
        candidates.put(canonicalName, ActionVariableRegistry.scopeKeyToDisplay(
                ActionVariableRegistry.extractScopeKey(canonicalName)));
    }

    private void closeActionPicker() {
        actionPickerMode = ActionPickerMode.NONE;
        actionPickerOptions.clear();
        keyboardKeyHits.clear();
        actionPickerScroll = 0;
        actionPickerMaxScroll = 0;
        actionPickerBounds = null;
        actionPickerListBounds = null;
        actionPickerSearchBounds = null;
        actionPickerClearBounds = null;
        actionPickerCancelBounds = null;
        actionPickerScrollTrack = null;
        actionPickerScrollThumb = null;
        draggingActionPickerScroll = false;
        if ("action.picker.search".equals(focusedFieldKey)) clearFocus();
    }

    private List<ActionPickerOption> filteredActionPickerOptions() {
        ModernTextField search = fields.get("action.picker.search");
        String query = PinyinSearchHelper.normalizeQuery(search == null ? "" : search.getText());
        if (query.isEmpty()) return new ArrayList<>(actionPickerOptions);
        List<ActionPickerOption> result = new ArrayList<>();
        for (ActionPickerOption option : actionPickerOptions) {
            if (PinyinSearchHelper.matchesNormalized(option.label, query)
                    || PinyinSearchHelper.matchesNormalized(option.value, query)
                    || PinyinSearchHelper.matchesNormalized(option.detail, query)) {
                result.add(option);
            }
        }
        return result;
    }

    private String actionPickerTitle() {
        if (actionPickerMode == ActionPickerMode.SEQUENCE) return "gui.modern.path.wb.u069";
        if (actionPickerMode == ActionPickerMode.CAPTURED_ID) return "gui.modern.path.wb.u070";
        if (actionPickerMode == ActionPickerMode.KEYBOARD) return "gui.modern.path.wb.u042";
        if (actionPickerMode == ActionPickerMode.LABEL) return "gui.modern.path.wb.u073";
        if (actionPickerMode == ActionPickerMode.ACTION_INDEX) return "gui.modern.path.wb.u074";
        if (actionPickerMode == ActionPickerMode.VARIABLE) {
            return "fromVar".equals(actionPickerParamKey) ? "gui.modern.path.wb.u075" : "gui.modern.path.wb.u076";
        }
        if (actionPickerMode == ActionPickerMode.TEMPLATE) return "gui.modern.path.wb.u077";
        return "gui.modern.path.wb.u071";
    }

    private void drawActionPicker(FontRenderer font, int mouseX, int mouseY) {
        if (actionPickerMode == ActionPickerMode.NONE || auxContentBounds == null) return;
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0xB80A1016);
        if (actionPickerMode == ActionPickerMode.KEYBOARD) {
            drawKeyboardPicker(font, mouseX, mouseY);
            return;
        }
        int width = Math.min(470, Math.max(300, auxContentBounds.width - 80));
        int height = Math.min(390, Math.max(250, auxContentBounds.height - 40));
        actionPickerBounds = new ModernMainLayout.Rect(auxContentBounds.x + (auxContentBounds.width - width) / 2,
                auxContentBounds.y + (auxContentBounds.height - height) / 2, width, height);
        ModernUiRenderer.drawPanel(actionPickerBounds.x, actionPickerBounds.y, width, height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, actionPickerTitle(), actionPickerBounds.x + 14, actionPickerBounds.y + 11,
                ModernUiRenderer.TEXT, width - 28);
        String pickerHint = actionPickerMode == ActionPickerMode.VARIABLE && "fromVar".equals(actionPickerParamKey)
                ? "gui.modern.path.wb.u079"
                : "gui.modern.path.wb.u080";
        ModernUiRenderer.drawText(font, pickerHint,
                actionPickerBounds.x + 14, actionPickerBounds.y + 27, ModernUiRenderer.MUTED_TEXT, width - 28);
        actionPickerSearchBounds = new ModernMainLayout.Rect(actionPickerBounds.x + 12, actionPickerBounds.y + 47,
                width - 24, 21);
        drawField(font, "action.picker.search", actionPickerSearchBounds, true, "gui.modern.path.wb.u081", mouseX, mouseY);
        actionPickerListBounds = new ModernMainLayout.Rect(actionPickerBounds.x + 10, actionPickerBounds.y + 76,
                width - 20, height - 114);
        ModernUiRenderer.drawSubtlePanel(actionPickerListBounds.x, actionPickerListBounds.y,
                actionPickerListBounds.width, actionPickerListBounds.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        List<ActionPickerOption> options = filteredActionPickerOptions();
        int rowHeight = 38;
        int visible = Math.max(1, actionPickerListBounds.height / rowHeight);
        actionPickerMaxScroll = Math.max(0, options.size() - visible);
        actionPickerScroll = clamp(actionPickerScroll, 0, actionPickerMaxScroll);
        ModernUiRenderer.beginClip(actionPickerListBounds);
        int y = actionPickerListBounds.y + 4;
        ActionPickerOption hoveredOption = null;
        ModernMainLayout.Rect hoveredRow = null;
        for (int i = actionPickerScroll; i < options.size() && i < actionPickerScroll + visible; i++) {
            ActionPickerOption option = options.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(actionPickerListBounds.x + 5,
                    y + (i - actionPickerScroll) * rowHeight, actionPickerListBounds.width - 24, rowHeight - 3);
            boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                hoveredOption = option;
                hoveredRow = row;
                continue;
            }
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, option.label, row.x + 8, row.y + 7, ModernUiRenderer.TEXT,
                    row.width - 16);
            ModernUiRenderer.drawText(font, option.detail.isEmpty() ? option.value : option.detail,
                    row.x + 8, row.y + 21, ModernUiRenderer.MUTED_TEXT, row.width - 16);
        }
        if (hoveredOption != null && hoveredRow != null) {
            ModernMainLayout.Rect grown = new ModernMainLayout.Rect(hoveredRow.x - 2, hoveredRow.y - 2,
                    hoveredRow.width + 4, hoveredRow.height + 4);
            ModernUiRenderer.drawSubtlePanel(grown.x, grown.y, grown.width, grown.height, 5,
                    ModernUiRenderer.SURFACE_HOVER, ModernUiRenderer.ACCENT);
            ModernUiRenderer.drawText(font, hoveredOption.label, grown.x + 10, grown.y + 8,
                    ModernUiRenderer.TEXT, grown.width - 20);
            ModernUiRenderer.drawText(font, hoveredOption.detail.isEmpty() ? hoveredOption.value : hoveredOption.detail,
                    grown.x + 10, grown.y + 22, ModernUiRenderer.SUBTLE_TEXT, grown.width - 20);
        }
        ModernUiRenderer.endClip();
        drawActionPickerScrollbar(actionPickerListBounds, options.size(), visible, mouseX, mouseY);
        if (options.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u082", actionPickerListBounds.x + 12,
                    actionPickerListBounds.y + 18, ModernUiRenderer.MUTED_TEXT, actionPickerListBounds.width - 24);
        }
        actionPickerClearBounds = new ModernMainLayout.Rect(actionPickerBounds.x + 12,
                actionPickerBounds.bottom() - 29, 72, 20);
        actionPickerCancelBounds = new ModernMainLayout.Rect(actionPickerBounds.right() - 72,
                actionPickerBounds.bottom() - 29, 60, 20);
        drawButton(font, actionPickerClearBounds, "gui.modern.path.wb.u083", false, false, true, mouseX, mouseY);
        drawButton(font, actionPickerCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
    }

    private void drawKeyboardPicker(FontRenderer font, int mouseX, int mouseY) {
        int width = Math.min(760, Math.max(360, auxContentBounds.width - 32));
        int height = Math.min(330, Math.max(260, auxContentBounds.height - 24));
        actionPickerBounds = new ModernMainLayout.Rect(auxContentBounds.x + (auxContentBounds.width - width) / 2,
                auxContentBounds.y + (auxContentBounds.height - height) / 2, width, height);
        ModernUiRenderer.drawPanel(actionPickerBounds.x, actionPickerBounds.y, width, height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u042", actionPickerBounds.x + 14, actionPickerBounds.y + 11,
                ModernUiRenderer.TEXT, width - 28);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u085", actionPickerBounds.x + 14,
                actionPickerBounds.y + 27, ModernUiRenderer.MUTED_TEXT, width - 28);
        ModernUiRenderer.drawDivider(actionPickerBounds.x + 12, actionPickerBounds.y + 46, width - 24,
                ModernUiRenderer.BORDER_SUBTLE);

        keyboardKeyHits.clear();
        List<List<KeyboardKey>> rows = keyboardRows();
        int keyboardX = actionPickerBounds.x + 14;
        int keyboardY = actionPickerBounds.y + 57;
        int keyboardWidth = width - 28;
        int rowHeight = Math.max(19, Math.min(24, (height - 120) / rows.size()));
        int gap = 4;
        String selected = selectedAction == null || selectedAction.params == null ? ""
                : jsonValue(selectedAction.params.get("key")).trim();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<KeyboardKey> row = rows.get(rowIndex);
            int units = 0;
            for (KeyboardKey key : row) units += key.units;
            int keyGapTotal = Math.max(0, row.size() - 1) * gap;
            int unitWidth = Math.max(9, (keyboardWidth - keyGapTotal) / Math.max(1, units));
            int x = keyboardX;
            for (int keyIndex = 0; keyIndex < row.size(); keyIndex++) {
                KeyboardKey key = row.get(keyIndex);
                int buttonWidth = keyIndex == row.size() - 1 ? keyboardX + keyboardWidth - x
                        : Math.max(10, key.units * unitWidth);
                ModernMainLayout.Rect button = new ModernMainLayout.Rect(x, keyboardY + rowIndex * (rowHeight + gap),
                        buttonWidth, rowHeight);
                keyboardKeyHits.add(new KeyboardKeyHit(key, button));
                boolean hovered = button.contains(mouseX, mouseY);
                boolean selectedKey = key.value.equalsIgnoreCase(selected);
                ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 3,
                        selectedKey ? ModernUiRenderer.SURFACE_PRESSED
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        selectedKey ? ModernUiRenderer.ACCENT
                                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                drawCenteredButtonText(font, key.label, button,
                        selectedKey ? ModernUiRenderer.TEXT
                                : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
                x = button.right() + gap;
            }
        }
        actionPickerClearBounds = new ModernMainLayout.Rect(actionPickerBounds.x + 14,
                actionPickerBounds.bottom() - 29, 72, 20);
        actionPickerCancelBounds = new ModernMainLayout.Rect(actionPickerBounds.right() - 74,
                actionPickerBounds.bottom() - 29, 60, 20);
        drawButton(font, actionPickerClearBounds, "gui.modern.path.wb.u083", false, false, true, mouseX, mouseY);
        drawButton(font, actionPickerCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
    }

    private List<List<KeyboardKey>> keyboardRows() {
        return Arrays.asList(
                keyboardRow("Esc|ESC", "F1|F1", "F2|F2", "F3|F3", "F4|F4", "F5|F5", "F6|F6",
                        "F7|F7", "F8|F8", "F9|F9", "F10|F10", "F11|F11", "F12|F12", "PrtSc|SYSRQ"),
                keyboardRow("`|GRAVE", "1|1", "2|2", "3|3", "4|4", "5|5", "6|6", "7|7", "8|8",
                        "9|9", "0|0", "-|MINUS", "=|EQUALS", "Backspace|BACK@2"),
                keyboardRow("Tab|TAB@2", "Q|Q", "W|W", "E|E", "R|R", "T|T", "Y|Y", "U|U", "I|I",
                        "O|O", "P|P", "[|LBRACKET", "]|RBRACKET", "Enter|RETURN@2"),
                keyboardRow("Caps|CAPITAL@2", "A|A", "S|S", "D|D", "F|F", "G|G", "H|H", "J|J", "K|K",
                        "L|L", ";|SEMICOLON", "'|APOSTROPHE", "Enter|RETURN@2"),
                keyboardRow("LShift|LSHIFT@3", "Z|Z", "X|X", "C|C", "V|V", "B|B", "N|N", "M|M", ",|COMMA",
                        ".|PERIOD", "/|SLASH", "RShift|RSHIFT@3"),
                keyboardRow("LCtrl|LCTRL@2", "LWin|LMETA@2", "LAlt|LALT@2", "Space|SPACE@7", "RAlt|RALT@2",
                        "RWin|RMETA@2", "RCtrl|RCTRL@2", "Ins|INSERT", "Del|DELETE", "Home|HOME", "End|END"),
                keyboardRow("PgUp|PRIOR@2", "PgDn|NEXT@2", "Up|UP@2", "Left|LEFT@2", "Down|DOWN@2", "Right|RIGHT@2"));
    }

    private List<KeyboardKey> keyboardRow(String... specs) {
        List<KeyboardKey> row = new ArrayList<>();
        for (String spec : specs) {
            String[] labelAndValue = safe(spec).split("\\|", 2);
            String label = labelAndValue.length == 0 ? "" : labelAndValue[0];
            String valueAndUnits = labelAndValue.length < 2 ? "" : labelAndValue[1];
            String[] parts = valueAndUnits.split("@", 2);
            int units = 1;
            if (parts.length > 1) {
                try {
                    units = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    units = 1;
                }
            }
            row.add(new KeyboardKey(label, parts.length == 0 ? "" : parts[0], units));
        }
        return row;
    }

    private String keyboardKeyDisplay(String value) {
        String normalized = safe(value).trim();
        if (normalized.startsWith("KEY_")) normalized = normalized.substring(4);
        if ("LCTRL".equalsIgnoreCase(normalized) || "RCTRL".equalsIgnoreCase(normalized)
                || "CTRL".equalsIgnoreCase(normalized)) return "Ctrl";
        if ("LSHIFT".equalsIgnoreCase(normalized) || "RSHIFT".equalsIgnoreCase(normalized)
                || "SHIFT".equalsIgnoreCase(normalized)) return "Shift";
        if ("LALT".equalsIgnoreCase(normalized) || "RALT".equalsIgnoreCase(normalized)
                || "ALT".equalsIgnoreCase(normalized)) return "Alt";
        if ("RETURN".equalsIgnoreCase(normalized)) return "Enter";
        if ("BACK".equalsIgnoreCase(normalized)) return "Backspace";
        if ("CAPITAL".equalsIgnoreCase(normalized)) return "Caps Lock";
        if ("PRIOR".equalsIgnoreCase(normalized)) return "Page Up";
        if ("NEXT".equalsIgnoreCase(normalized)) return "Page Down";
        return normalized;
    }

    private void drawSystemMessageShortcuts(FontRenderer font, ModernMainLayout.Rect row, int mouseX, int mouseY) {
        int x = row.x + 10;
        int width = Math.max(1, row.width - 20);
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.label.color_shortcuts"), x, row.y + 44,
                ModernUiRenderer.SUBTLE_TEXT, width);
        int gap = 4;
        int columns = 8;
        int buttonWidth = Math.max(18, (width - gap * (columns - 1)) / columns);
        int colorY = row.y + 57;
        for (int i = 0; i < SYSTEM_MESSAGE_COLOR_CODES.length; i++) {
            int col = i % columns;
            int line = i / columns;
            ModernMainLayout.Rect button = new ModernMainLayout.Rect(x + col * (buttonWidth + gap),
                    colorY + line * 21, buttonWidth, 17);
            systemMessageColorBounds.add(button);
            boolean hovered = button.contains(mouseX, mouseY);
            int border = hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE;
            ModernUiRenderer.drawSubtlePanel(button.x, button.y, button.width, button.height, 3,
                    SYSTEM_MESSAGE_COLORS[i], border);
        }
        int formatLabelY = colorY + 45;
        ModernUiRenderer.drawText(font, I18n.format("gui.path.action_editor.label.format_shortcuts"), x,
                formatLabelY, ModernUiRenderer.SUBTLE_TEXT, width);
        int formatY = formatLabelY + 13;
        int formatWidth = Math.max(28, (width - gap * (SYSTEM_MESSAGE_FORMAT_CODES.length - 1))
                / SYSTEM_MESSAGE_FORMAT_CODES.length);
        for (int i = 0; i < SYSTEM_MESSAGE_FORMAT_CODES.length; i++) {
            ModernMainLayout.Rect button = new ModernMainLayout.Rect(x + i * (formatWidth + gap), formatY,
                    formatWidth, 18);
            systemMessageFormatBounds.add(button);
            drawButton(font, button, SYSTEM_MESSAGE_FORMAT_LABELS[i], false, false, canEditStep(), mouseX, mouseY);
        }
    }

    private void insertSystemMessageToken(String token) {
        if (!canEditStep() || selectedAction == null || !"system_message".equalsIgnoreCase(safe(selectedAction.type))
                || token == null || token.isEmpty()) {
            return;
        }
        ModernTextField field = fields.get("param.message");
        if (field == null) {
            return;
        }
        String current = safe(field.getText());
        int cursor = clamp(field.getCursorPosition(), 0, current.length());
        int selection = clamp(field.getSelectionEnd(), 0, current.length());
        int start = Math.min(cursor, selection);
        int end = Math.max(cursor, selection);
        String next = current.substring(0, start) + token + current.substring(end);
        pushHistory("format-system-message");
        field.setText(next);
        field.setCursorPosition(start + token.length());
        field.setSelectionPos(start + token.length());
        for (Map.Entry<String, ModernTextField> entry : fields.entrySet()) {
            entry.getValue().setFocused("param.message".equals(entry.getKey()));
        }
        focusedFieldKey = "param.message";
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        selectedAction.params.addProperty("message", next);
        dirty = true;
    }

    private String actionChoiceDisplay(String key, String value) {
        if ("presetName".equals(key) && selectedAction != null
                && "toggle_kill_aura".equalsIgnoreCase(selectedAction.type)) {
            return safe(value).isEmpty() ? tr("gui.modern.path.schema.kill_aura_current") : value;
        }
        String normalized = safe(value).trim();
        String normalizedKey = safe(key).toLowerCase(Locale.ROOT);
        if ("left".equals(normalizedKey)) return localizedMouseButton(normalized);
        if ("true".equalsIgnoreCase(normalized)) {
            return I18n.format("path.common.on");
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return I18n.format("path.common.off");
        }
        if ("state".equals(normalizedKey)) {
            if ("up".equalsIgnoreCase(normalized) || "robotup".equalsIgnoreCase(normalized)) {
                return I18n.format("gui.path.action_editor.option.key_state.up");
            }
            if ("press".equalsIgnoreCase(normalized) || normalized.isEmpty()) {
                return I18n.format("gui.path.action_editor.option.key_state.press");
            }
            return I18n.format("gui.path.action_editor.option.key_state.down");
        }
        if ("matchmode".equals(normalizedKey) || "locatormatchmode".equals(normalizedKey)) {
            return matchModeToDisplay(normalized);
        }
        if ("executemode".equals(normalizedKey)) return runSequenceExecuteModeToDisplay(normalized);
        if ("direction".equals(normalizedKey)) return directionToDisplay(normalized);
        if ("clicktype".equals(normalizedKey)) return localizedClickType(normalized);
        if ("button".equals(normalizedKey) && selectedAction != null
                && ActionEditorUxSupport.isClickButtonKey(selectedAction.type, key)) {
            return ClickSemantics.displayButton(jsonValue(selectedAction.params == null ? null
                    : selectedAction.params.get("clickType")), parseDefaultInt(normalized, 0));
        }
        if ("slotbase".equals(normalizedKey)) {
            return I18n.format("gui.path.action_editor.option." + ("HEX".equalsIgnoreCase(normalized) ? "hex" : "decimal"));
        }
        if ("usemode".equals(normalizedKey)) return useModeToDisplay(normalized);
        if ("valuetype".equals(normalizedKey)) return valueTypeToDisplay(normalized);
        if ("lookupmode".equals(normalizedKey)) return packetFieldLookupModeToDisplay(normalized);
        if ("locatormode".equals(normalizedKey)) return localizedLocatorMode(normalized);
        if ("movedirection".equals(normalizedKey)) return moveChestDirectionToDisplay(normalized);
        if ("sourcescope".equals(normalizedKey)) return localizedSourceScope(normalized);
        if ("targetscope".equals(normalizedKey)) return localizedTargetScope(normalized);
        if ("spreadmode".equals(normalizedKey)) return spreadModeToDisplay(normalized);
        if ("remaindermode".equals(normalizedKey)) return spreadRemainderModeToDisplay(normalized);
        if ("combinedmode".equals(normalizedKey)) return waitCombinedModeToDisplay(normalized);
        if ("slotarea".equals(normalizedKey)) return captureSlotAreaToDisplay(normalized);
        if ("visioncomparemode".equals(normalizedKey)) return visionCompareModeToDisplay(normalized);
        if ("elementtype".equals(normalizedKey)) return guiElementTypeToDisplay(normalized);
        if ("guielementlocatormode".equals(normalizedKey)) return guiElementLocatorModeToDisplay(normalized);
        if ("waitmode".equals(normalizedKey)) {
            return I18n.format("gui.path.action_editor.option.captured_id_wait_mode."
                    + ("recapture".equalsIgnoreCase(normalized) ? "recapture" : "update"));
        }
        if ("operation".equals(normalizedKey)) return sequenceControlOperationToDisplay(normalized);
        if ("entitytype".equals(normalizedKey)) return localizedEntityType(normalized);
        if ("pickupfiltermode".equals(normalizedKey)) {
            return "CUSTOM".equalsIgnoreCase(normalized)
                    ? tr("gui.modern.path.schema.u311") : tr("gui.modern.path.schema.u310");
        }
        if (normalized.isEmpty()) return I18n.format("gui.path.action_editor.option.unselected");
        return normalized;
    }

    private String localizedClickType(String value) {
        String normalized = com.zszl.zszlScriptMod.utils.ModUtils.normalizeClickTypeName(value);
        String key = "gui.path.action_editor.option.click_type." + normalized.toLowerCase(Locale.ROOT);
        String translated = I18n.format(key);
        return translated.equals(key) ? com.zszl.zszlScriptMod.utils.ModUtils.clickTypeToDisplayName(normalized) : translated;
    }

    private String localizedMouseButton(String value) {
        String normalized = safe(value).toLowerCase(Locale.ROOT).replace('-', '_');
        if ("shift_left".equals(normalized)) return I18n.format("gui.path.action_editor.option.mouse_button.shift_left");
        if ("shift_right".equals(normalized)) return I18n.format("gui.path.action_editor.option.mouse_button.shift_right");
        if ("middle".equals(normalized)) return I18n.format("gui.path.action_editor.option.mouse_button.middle");
        if ("right".equals(normalized) || "false".equals(normalized)) {
            return I18n.format("gui.path.action_editor.option.mouse_button.right");
        }
        return I18n.format("gui.path.action_editor.option.mouse_button.left");
    }

    private String localizedLocatorMode(String value) {
        String normalized = safe(value).toUpperCase(Locale.ROOT);
        String type = selectedAction == null ? "" : safe(selectedAction.type).toLowerCase(Locale.ROOT);
        if ("window_click".equals(type) || "conditional_window_click".equals(type) || "autochestclick".equals(type)) {
            if ("ITEM_TEXT".equals(normalized)) return I18n.format("gui.path.action_editor.option.locator.slot.item_text");
            if ("EMPTY_SLOT".equals(normalized)) return I18n.format("gui.path.action_editor.option.locator.slot.empty");
            if ("SLOT_PATH".equals(normalized)) return I18n.format("gui.path.action_editor.option.locator.slot.path");
            return I18n.format("gui.path.action_editor.option.locator.slot.direct");
        }
        if ("rightclickblock".equals(type) || "rightclickentity".equals(type)) {
            return worldLocatorModeToDisplay(normalized);
        }
        return clickLocatorModeToDisplay(normalized);
    }

    private String localizedSourceScope(String value) {
        if ("CONTAINER".equalsIgnoreCase(value)) return I18n.format("gui.path.action_editor.option.spread_source.container");
        if ("MAIN".equalsIgnoreCase(value)) return I18n.format("gui.path.action_editor.option.spread_source.main");
        if ("HOTBAR".equalsIgnoreCase(value)) return I18n.format("gui.path.action_editor.option.spread_source.hotbar");
        return I18n.format("gui.path.action_editor.option.spread_source.inventory");
    }

    private String localizedTargetScope(String value) {
        if ("background".equalsIgnoreCase(value)) {
            return I18n.format("gui.path.action_editor.option.stop_sequence_scope_background");
        }
        if ("foreground".equalsIgnoreCase(value)) {
            return I18n.format("gui.path.action_editor.option.stop_sequence_scope_foreground");
        }
        if ("MAIN".equalsIgnoreCase(value)) return I18n.format("gui.path.action_editor.option.spread_target.main");
        if ("HOTBAR".equalsIgnoreCase(value)) return I18n.format("gui.path.action_editor.option.spread_target.hotbar");
        return I18n.format("gui.path.action_editor.option.spread_target.inventory");
    }

    private String localizedEntityType(String value) {
        if (safe(value).trim().isEmpty()) return I18n.format("gui.path.action_editor.option.entity_type.inherit");
        if ("hostile".equalsIgnoreCase(value) || "monster".equalsIgnoreCase(value)) {
            return I18n.format("gui.path.action_editor.option.entity_type.hostile");
        }
        if ("passive".equalsIgnoreCase(value) || "animal".equalsIgnoreCase(value)) {
            return I18n.format("gui.path.action_editor.option.entity_type.passive");
        }
        if ("all".equalsIgnoreCase(value) || "entity".equalsIgnoreCase(value)) {
            return I18n.format("gui.path.action_editor.option.entity_type.all");
        }
        return I18n.format("gui.path.action_editor.option.entity_type.player");
    }

    private void drawFooter(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawDivider(footerBounds.x + 10, footerBounds.y, Math.max(1, footerBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        String footerStatus = statusUntil > System.currentTimeMillis() ? status
                : selectedSequence == null ? tr("gui.modern.path.wb.u014")
                        : tr("gui.modern.path.wb.fmt.steps_status", String.valueOf(selectedSequence.getSteps().size()),
                                tr(dirty ? "gui.modern.path.wb.u086" : "gui.modern.path.wb.u087"));
        ModernUiRenderer.drawText(font, footerStatus, footerBounds.x + 108, footerBounds.y + 11,
                dirty ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT,
                Math.max(40, footerDiscardBounds.x - footerBounds.x - 116));
        drawButton(font, footerUndoBounds, "gui.modern.path.wb.u088", false, false, !undoHistory.isEmpty(), mouseX, mouseY);
        drawButton(font, footerRedoBounds, "gui.modern.path.wb.u089", false, false, !redoHistory.isEmpty(), mouseX, mouseY);
        drawButton(font, footerDiscardBounds, "gui.modern.path.wb.u090", false, true, isDirty(), mouseX, mouseY);
        drawButton(font, footerSaveBounds, "gui.modern.path.wb.u091", true, false, isDirty(), mouseX, mouseY);
    }

    private void drawNavTools(FontRenderer font, int mouseX, int mouseY) {
        if (navToolsBounds == null) {
            return;
        }
        navToolsSettingsBounds = drawToolStrip(font, navToolsBounds, "gui.modern.path.wb.u092",
                PathWorkbenchToolCatalog.Zone.NAVIGATION,
                PathWorkbenchToolCatalog.get().pinned(PathWorkbenchToolCatalog.Zone.NAVIGATION), mouseX, mouseY);
    }

    private void drawCategoryRow(FontRenderer font, ModernMainLayout.Rect row, String category, boolean collapsed,
            int mouseX, int mouseY) {
        boolean selected = category.equals(selectedCategory) && selectedSubCategory.isEmpty();
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(row.x + 7, row.y + 6, collapsed,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.SUBTLE_TEXT);
        String label = Boolean.TRUE.equals(hiddenCategories.get(category))
                ? tr("gui.modern.path.wb.fmt.hidden", category) : category;
        ModernUiRenderer.drawText(font, label, row.x + 20, row.y + 6,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(30, row.width - 28));
    }

    private void drawSubCategoryRow(FontRenderer font, ModernMainLayout.Rect row, String sub, int count,
            boolean collapsed, int mouseX, int mouseY) {
        boolean selected = safe(sub).equals(selectedSubCategory) && !selectedSubCategory.isEmpty();
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawChevron(row.x + 7, row.y + 6, collapsed,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawText(font, sub, row.x + 20, row.y + 6,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(30, row.width - 52));
        ModernUiRenderer.drawText(font, String.valueOf(count), row.right() - 25, row.y + 6,
                ModernUiRenderer.MUTED_TEXT, 18);
    }

    private void drawSequenceNavRow(FontRenderer font, ModernMainLayout.Rect row, PathSequence sequence,
            int mouseX, int mouseY) {
        boolean selected = navigationSelection.contains(sequence.getName());
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int dot = ModernUiRenderer.SUCCESS;
        ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 8, dot);
        String label = safe(sequence.getName());
        ModernUiRenderer.drawText(font, label, row.x + 19, row.y + 5, selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(30, row.width - 28));
        String detail = tr("gui.modern.path.wb.fmt.steps_kind", String.valueOf(sequence.getSteps().size()),
                tr("gui.modern.path.wb.u093"));
        ModernUiRenderer.drawText(font, detail, row.x + 19, row.y + 16, ModernUiRenderer.MUTED_TEXT,
                Math.max(30, row.width - 28));
    }

    private void drawStepRow(FontRenderer font, ModernMainLayout.Rect row, PathStep step, int index,
            int mouseX, int mouseY) {
        boolean selected = selectedStepIndices.contains(Integer.valueOf(index)) || index == selectedStepIndex;
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(row.x, row.y, 3, row.height, 2,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.ACCENT_DIM);
        String stepNote = safe(step.getNote());
        boolean hasNote = !stepNote.trim().isEmpty();
        ModernMainLayout.Rect note = stepNoteBounds(row);
        drawNoteGlyphButton(note, hasNote ? tr("gui.modern.path.wb.fmt.step_note", stepNote) : "gui.modern.path.wb.u095", hasNote, isEditableSequence(),
                mouseX, mouseY);
        int contentWidth = Math.max(45, note.x - row.x - 17);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.step_n", String.valueOf(index + 1)), row.x + 10, row.y + 5, ModernUiRenderer.TEXT,
                contentWidth);
        ModernUiRenderer.drawText(font, stepDescription(step), row.x + 10, row.y + 17,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, contentWidth);
    }

    private ModernMainLayout.Rect stepNoteBounds(ModernMainLayout.Rect row) {
        int size = Math.max(14, Math.min(18, row.height - 6));
        return new ModernMainLayout.Rect(row.right() - size - 6, row.y + (row.height - size) / 2, size, size);
    }

    private void drawActionRow(FontRenderer font, ModernMainLayout.Rect row, ActionData action, int index,
            int mouseX, int mouseY) {
        boolean selected = selectedActionIndices.contains(Integer.valueOf(index)) || index == selectedActionIndex;
        boolean hovered = row.contains(mouseX, mouseY);
        int accent = actionAccent(action == null ? "" : action.type);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                        : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(row.x, row.y, 3, row.height, 2, accent);
        ModernMainLayout.Rect packetToggle = packetToggleBounds(row);
        drawPacketToggleIcon(packetToggle, action, mouseX, mouseY);
        actionPacketToggleHits.add(new RowHit(index, packetToggle));
        String type = localizedActionName(action == null ? "" : action.type);
        ModernUiRenderer.drawText(font, (index + 1) + ". " + type, row.x + 10, row.y + 5,
                selected ? ModernUiRenderer.TEXT : accent, Math.max(45, packetToggle.x - row.x - 14));
        String detail = action == null ? "" : safe(action.getDescription());
        ModernUiRenderer.drawText(font, detail, row.x + 10, row.y + 17, ModernUiRenderer.MUTED_TEXT,
                Math.max(45, packetToggle.x - row.x - 14));
    }

    private ModernMainLayout.Rect packetToggleBounds(ModernMainLayout.Rect row) {
        int size = Math.max(17, Math.min(20, row.height - 5));
        return new ModernMainLayout.Rect(row.right() - size - 5, row.y + (row.height - size) / 2, size, size);
    }

    private void drawPacketToggleIcon(ModernMainLayout.Rect rect, ActionData action, int mouseX, int mouseY) {
        if (rect == null || action == null || !isPacketToggleAction(action)) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int color = hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT;
        ModernUiRenderer.drawIcon(RecordingPacketSupport.hasOriginalAction(action.params)
                ? ModernUiRenderer.Icon.MOVE_PANEL : ModernUiRenderer.Icon.PACKET,
                rect.x + 2, rect.y + 2, Math.max(12, rect.width - 4), color);
        if (hovered) {
            hoveredTooltip = tr(RecordingPacketSupport.hasOriginalAction(action.params)
                    ? "gui.modern.path.record.packet_restore" : "gui.modern.path.record.packet_open");
        }
    }

    private boolean isPacketToggleAction(ActionData action) {
        return action != null && (RecordingPacketSupport.isInputAction(action.type)
                || RecordingPacketSupport.hasPacketAssociation(action.params)
                || RecordingPacketSupport.hasOriginalAction(action.params));
    }

    private void drawBackGlyph(ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        GuiModernMainScreen menuScreen = getOwningMenuScreen();
        boolean fullscreenControl = menuScreen != null && !DetachedSwingWindowManager.isDetachedScreen(menuScreen);
        int color = hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT;
        if (fullscreenControl) {
            boolean fullscreen = menuScreen.isActiveTabFullscreen();
            ModernUiRenderer.drawExpandIcon(rect.x + Math.max(1, (rect.width - 15) / 2),
                    rect.y + Math.max(1, (rect.height - 15) / 2), fullscreen, color);
            if (hovered) {
                hoveredTooltip = tr(fullscreen ? "gui.modern.path.wb.u352" : "gui.modern.path.wb.u351");
            }
        } else {
            ModernUiRenderer.drawChevron(rect.x + Math.max(1, (rect.width - 4) / 2),
                    rect.y + Math.max(1, (rect.height - 9) / 2), false, color);
        }
    }

    private GuiModernMainScreen getOwningMenuScreen() {
        if (minecraft != null && minecraft.currentScreen instanceof GuiModernMainScreen) {
            return (GuiModernMainScreen) minecraft.currentScreen;
        }
        return DetachedSwingWindowManager.getActiveScreen();
    }

    private void drawToggleButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean selected,
            boolean enabled, int mouseX, int mouseY) {
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                fill,
                selected && enabled ? ModernUiRenderer.ACCENT : hovered && enabled ? ModernUiRenderer.BORDER
                        : ModernUiRenderer.BORDER_SUBTLE);
        ModernMainLayout.Rect labelBounds = new ModernMainLayout.Rect(rect.x, rect.y,
                Math.max(1, rect.width - 34), rect.height);
        drawCenteredButtonText(font, label, labelBounds,
                ModernUiRenderer.readableText(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.DISABLED_TEXT, fill));
        ModernUiRenderer.drawToggle(rect.right() - 29, rect.y + 3, 23, 12, selected && enabled, hovered && enabled);
    }

    /** 开关按钮：文字占满整个按钮居中，右侧仅保留一个悬浮信息图标。 */
    private void drawOptionToggle(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean selected,
            boolean enabled, String tooltip, int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                fill,
                selected && enabled ? ModernUiRenderer.ACCENT : hovered && enabled ? ModernUiRenderer.BORDER
                        : ModernUiRenderer.BORDER_SUBTLE);
        drawCenteredButtonText(font, label, rect,
                ModernUiRenderer.readableText(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.DISABLED_TEXT, fill));
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        int iconSize = 11;
        int iconX = rect.right() - iconSize - 4;
        int iconY = rect.y + (rect.height - iconSize) / 2;
        boolean iconHovered = enabled && new ModernMainLayout.Rect(iconX - 3, iconY - 3, iconSize + 6, iconSize + 6)
                .contains(mouseX, mouseY);
        if (iconHovered) {
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(iconX, iconY,
                ModernUiRenderer.readableText(iconHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, fill));
    }

    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary, boolean danger,
            boolean enabled, int mouseX, int mouseY) {
        drawButton(font, rect, label, primary, danger, enabled, "", mouseX, mouseY);
    }

    /**
     * Draw a compact command button with a centered label and an optional
     * shortcut in a dedicated right-hand column.
     */
    private void drawButton(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean primary, boolean danger,
            boolean enabled, String shortcut, int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        int fill;
        int border;
        int text;
        if (!enabled) {
            fill = ModernUiRenderer.DISABLED_SURFACE;
            border = ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.DISABLED_TEXT;
        } else if (primary) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.ACCENT;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.ACCENT;
            text = ModernUiRenderer.TEXT;
        } else if (danger) {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.DANGER;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.DANGER;
            text = ModernUiRenderer.TEXT;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
            text = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        String resolvedShortcut = shortcut == null ? "" : shortcut.trim();
        int shortcutWidth = resolvedShortcut.isEmpty() ? 0 : font.getStringWidth(resolvedShortcut);
        int shortcutReserve = shortcutWidth <= 0 ? 0 : shortcutWidth + 10;
        ModernMainLayout.Rect labelBounds = new ModernMainLayout.Rect(rect.x + 4, rect.y,
                Math.max(1, rect.width - shortcutReserve - 8), rect.height);
        drawCenteredButtonText(font, label, labelBounds, ModernUiRenderer.readableText(text, fill));
        if (shortcutWidth > 0) {
            com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, resolvedShortcut, rect.right() - shortcutWidth - 6,
                    rect.y + Math.max(1, (rect.height - font.FONT_HEIGHT) / 2),
                    enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT, shortcutWidth);
        }
    }

    private void drawCenteredButtonText(FontRenderer font, String label, ModernMainLayout.Rect rect, int color) {
        if (font == null || rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        if (com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.isSaveLabel(label)) {
            com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, label, rect.x + 4, rect.y + (rect.height - font.FONT_HEIGHT) / 2, color, rect.width - 8);
            return;
        }
        String text = tr(label == null ? "" : label);
        int textWidth = font.getStringWidth(text);
        if (textWidth <= 0) {
            return;
        }
        int availableWidth = Math.max(4, rect.width - 8);
        float scale = textWidth > availableWidth ? (float) availableWidth / (float) textWidth : 1.0F;
        GlStateManager.pushMatrix();
        GlStateManager.translate(rect.x + rect.width / 2.0F, rect.y + rect.height / 2.0F, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        font.drawString(text, -textWidth / 2.0F, -font.FONT_HEIGHT / 2.0F,
                ModernUiRenderer.readableText(color, ModernUiRenderer.SURFACE), false);
        GlStateManager.popMatrix();
    }

    private void drawParameterInfoIcon(ModernActionEditorSchema.Field field, int x, int y, int mouseX, int mouseY) {
        String tooltip = parameterFieldTooltip(field);
        if (tooltip.isEmpty()) {
            return;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x - 2, y - 2, 15, 15);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private String parameterFieldTooltip(ModernActionEditorSchema.Field field) {
        if (field == null) {
            return "";
        }
        Object[] args = null;
        if (isScreenClickCoordinateField(field) && minecraft != null) {
            int size = Math.max(1, "x".equals(field.key) ? minecraft.displayWidth : minecraft.displayHeight);
            args = new Object[] { String.valueOf(size) };
        }
        String help = ActionEditorFieldHelp.tooltip(selectedAction == null ? "" : selectedAction.type,
                field.key, field.hint, args);
        return help.isEmpty() ? safe(field.label) : help;
    }

    private void drawIconButton(ModernMainLayout.Rect rect, String icon, int mouseX, int mouseY, String tooltip) {
        boolean hovered = rect != null && rect.contains(mouseX, mouseY);
        if (rect == null) {
            return;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        if ("...".equals(icon)) {
            ModernUiRenderer.drawMoreIcon(rect.x + Math.max(1, (rect.width - 13) / 2), rect.y + 5,
                    hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        } else if ("settings".equals(icon)) {
            ModernUiRenderer.drawSettingsIcon(rect.x + Math.max(1, (rect.width - 12) / 2),
                    rect.y + Math.max(1, (rect.height - 12) / 2),
                    hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        }
        if (hovered && tooltip != null) {
            hoveredTooltip = tooltip;
        }
    }

    private void drawInlineInfoIcon(int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(x - 2, y - 2, 15, 15);
        boolean hovered = bounds.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(x - 2, y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(x, y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private void drawNoteGlyphButton(ModernMainLayout.Rect note, String tooltip, boolean hasNote, boolean enabled,
            int mouseX, int mouseY) {
        if (note == null) {
            return;
        }
        boolean hovered = enabled && note.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(note.x, note.y, note.width, note.height, 4,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                hovered ? ModernUiRenderer.ACCENT : hasNote ? ModernUiRenderer.WARNING
                        : ModernUiRenderer.BORDER_SUBTLE);
        int icon = 11;
        ModernUiRenderer.drawInfoIcon(note.x + Math.max(0, (note.width - icon) / 2),
                note.y + Math.max(0, (note.height - icon) / 2),
                hovered ? ModernUiRenderer.TEXT : hasNote ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT);
        if (hovered && tooltip != null) {
            hoveredTooltip = tooltip;
        }
    }

    private void drawSequenceNoteButton(FontRenderer font, ModernMainLayout.Rect rect, int mouseX, int mouseY) {
        if (rect == null) {
            return;
        }
        boolean enabled = isEditableSequence();
        String note = selectedSequence == null ? "" : safe(selectedSequence.getNote());
        boolean hasNote = !note.trim().isEmpty();
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                !enabled ? 0xFF151E26 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                hovered ? ModernUiRenderer.ACCENT : hasNote ? ModernUiRenderer.WARNING
                        : ModernUiRenderer.BORDER_SUBTLE);
        int icon = 11;
        int iconX = rect.right() - icon - 5;
        int iconY = rect.y + Math.max(0, (rect.height - icon) / 2);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(font, "gui.modern.path.wb.u096", rect.x + 6, rect.y + 5,
                enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT,
                Math.max(24, iconX - rect.x - 8));
        ModernUiRenderer.drawInfoIcon(iconX, iconY,
                hovered ? ModernUiRenderer.TEXT : hasNote ? ModernUiRenderer.WARNING : ModernUiRenderer.MUTED_TEXT);
        if (hovered) {
            hoveredTooltip = hasNote ? tr("gui.modern.path.wb.fmt.seq_note", note) : "gui.modern.path.wb.u097";
        }
    }

    private void clearFieldBounds() {
        fieldBounds.clear();
    }

    private void hideInactiveFields() {
        for (Map.Entry<String, ModernTextField> entry : fields.entrySet()) {
            ModernTextField field = entry.getValue();
            field.setVisible(false);
            if (!entry.getKey().equals(focusedFieldKey)) {
                field.setFocused(false);
            }
        }
    }

    private ModernTextField ensureField(String key, int maxLength) {
        ModernTextField field = fields.get(key);
        if (field == null) {
            field = new ModernTextField(5000 + fields.size(), fontRenderer, 0, 0, 1, 18);
            field.setMaxStringLength(maxLength);
            field.setEnableBackgroundDrawing(false);
            field.setCanLoseFocus(false);
            fields.put(key, field);
        }
        return field;
    }

    private void showField(String key, ModernMainLayout.Rect rect, boolean enabled) {
        if (rect == null) {
            return;
        }
        ModernTextField field = ensureField(key, 32767);
        field.x = rect.x;
        field.y = rect.y;
        field.width = Math.max(1, rect.width);
        field.height = Math.max(1, rect.height);
        field.setVisible(true);
        field.setEnabled(enabled);
        field.setCanLoseFocus(false);
        field.setFocused(key.equals(focusedFieldKey));
        fieldBounds.put(key, rect);
    }

    private void drawField(FontRenderer font, String key, ModernMainLayout.Rect rect, boolean enabled,
            String hintText, int mouseX, int mouseY) {
        showField(key, rect, enabled);
        ModernTextField field = fields.get(key);
        boolean focused = field != null && field.isFocused();
        ModernUiRenderer.drawSubtlePanel(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 4,
                enabled ? 0xFF101820 : 0xFF141B22,
                focused ? ModernUiRenderer.ACCENT : enabled ? ModernUiRenderer.BORDER_SUBTLE : 0xFF27333D);
        if (field != null) {
            field.setTextColor(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
            field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
            field.setEnableBackgroundDrawing(false);
            ModernUiRenderer.reflowTextField(field);
            ModernUiRenderer.drawTextField(field);
            if (field.getText().isEmpty() && !focused && hintText != null && !hintText.isEmpty()) {
                float scale = Math.min(2.0F, Math.max(1.0F, (rect.height - 6) / (float) Math.max(1, font.FONT_HEIGHT)));
                ModernUiRenderer.drawText(font, hintText, rect.x + 5,
                        rect.y + Math.round((rect.height - font.FONT_HEIGHT * scale) / 2.0F),
                        ModernUiRenderer.MUTED_TEXT, Math.max(12, rect.width - 10));
            }
        }
    }

    private void drawLabeledField(FontRenderer font, String key, ModernMainLayout.Rect rect, String label,
            boolean enabled, int mouseX, int mouseY) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        drawField(font, key, rect, enabled, label, mouseX, mouseY);
    }

    private String fieldAt(int mouseX, int mouseY) {
        for (Map.Entry<String, ModernMainLayout.Rect> entry : fieldBounds.entrySet()) {
            ModernMainLayout.Rect rect = entry.getValue();
            if (rect != null && rect.contains(mouseX, mouseY)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private void focusField(String key, int mouseX, int mouseY, int mouseButton) {
        expressionCodeEditor.setFocused(false);
        for (Map.Entry<String, ModernTextField> entry : fields.entrySet()) {
            boolean focused = entry.getKey().equals(key);
            entry.getValue().setFocused(focused);
        }
        focusedFieldKey = key;
        ModernTextField field = fields.get(key);
        if (field != null) {
            field.setCanLoseFocus(false);
            field.setFocused(true);
            ModernUiRenderer.moveTextFieldCursorTo(field, mouseX);
        }
    }

    private void clearFocus() {
        expressionCodeEditor.setFocused(false);
        for (ModernTextField field : fields.values()) {
            field.setFocused(false);
        }
        focusedFieldKey = null;
    }

    private boolean typeFocusedField(char typedChar, int keyCode) {
        if (focusedFieldKey == null) {
            return false;
        }
        ModernTextField field = fields.get(focusedFieldKey);
        if (field == null || !field.isFocused()) {
            return false;
        }
        boolean handled = isNumericInputFieldKey(focusedFieldKey)
                ? ModernUiRenderer.typeNumericField(field, typedChar, keyCode, isDecimalInputFieldKey(focusedFieldKey))
                : field.textboxKeyTyped(typedChar, keyCode);
        if (handled && ("search".equals(focusedFieldKey) || "action.editor.search".equals(focusedFieldKey)
                || "template.search".equals(focusedFieldKey))) {
            navigationScroll = 0;
            if ("action.editor.search".equals(focusedFieldKey)) {
                actionPageScroll = 0;
            } else if ("template.search".equals(focusedFieldKey)) {
                templateScroll = 0;
            }
        }
        if (handled && modalMode == ModalMode.NONE) {
            syncEditorFields();
            syncAuxiliaryFields();
        }
        return handled;
    }

    private boolean isNumericInputFieldKey(String key) {
        if ("action.editor.recent.limit".equals(key) || "sequence.loop".equals(key)
                || "step.retry".equals(key) || "step.timeout".equals(key) || "step.tolerance".equals(key)
                || "step.settings.retry".equals(key) || "step.settings.timeout".equals(key)
                || "step.settings.tolerance".equals(key) || "move.chest.maxTake".equals(key)
                || "move.chest.maxPut".equals(key)) {
            return true;
        }
        if (key == null || !key.startsWith("param.") || selectedAction == null) {
            return false;
        }
        return ActionEditorJson.isNumericKey(selectedAction.type, key.substring("param.".length()));
    }

    private boolean isDecimalInputFieldKey(String key) {
        if ("step.x".equals(key) || "step.y".equals(key) || "step.z".equals(key)) {
            return true;
        }
        if (key == null || !key.startsWith("param.") || selectedAction == null) {
            return false;
        }
        String param = key.substring("param.".length());
        return "x".equals(param) || "y".equals(param) || "yaw".equals(param) || "pitch".equals(param)
                || "range".equals(param) || "searchRadius".equals(param) || "followDistance".equals(param)
                || "reachDistance".equals(param) || "huntUpRange".equals(param) || "huntDownRange".equals(param)
                || "areaSweepCellSize".equals(param) || "scanRadius".equals(param)
                || "similarityThreshold".equals(param) || "edgeThreshold".equals(param);
    }

    private void bindEditorFields() {
        if (selectedSequence != null) {
            setFieldTextIfUnchanged("sequence.loop", String.valueOf(selectedSequence.getLoopDelayTicks()));
            setFieldTextIfUnchanged("sequence.note", safe(selectedSequence.getNote()));
        }
        if (selectedStep != null) {
            double[] point = selectedStep.getGotoPoint();
            setFieldTextIfUnchanged("step.x", coordinateText(point, 0));
            setFieldTextIfUnchanged("step.y", coordinateText(point, 1));
            setFieldTextIfUnchanged("step.z", coordinateText(point, 2));
            setFieldTextIfUnchanged("step.note", safe(selectedStep.getNote()));
            setFieldTextIfUnchanged("step.retry", String.valueOf(selectedStep.getRetryCount()));
            setFieldTextIfUnchanged("step.timeout", String.valueOf(selectedStep.getPathRetryTimeoutSeconds()));
            setFieldTextIfUnchanged("step.tolerance", String.valueOf(selectedStep.getArrivalToleranceBlocks()));
            setFieldTextIfUnchanged("step.failureTarget", safe(selectedStep.getRetryExhaustedSequenceName()));
        }
        if (selectedAction != null) {
            setFieldTextIfUnchanged("action.type", safe(selectedAction.type));
            for (ModernActionEditorSchema.Field field : ModernActionEditorSchema.fields(selectedAction.type)) {
                if (field.key == null || (field.kind != ModernActionEditorSchema.Kind.TEXT
                        && field.kind != ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST
                        && !isActionIntegerField(field))) {
                    continue;
                }
                JsonElement value = selectedAction.params == null ? null : selectedAction.params.get(field.key);
                ensureField("param." + field.key, 32767);
                String text = value == null ? safe(field.defaultValue) : jsonValue(value);
                if (ActionEditorUxSupport.isVariableKey(selectedAction.type, field.key)) {
                    text = ActionVariableRegistry.extractBaseName(text);
                }
                setFieldTextIfUnchanged("param." + field.key, text);
            }
        }
    }

    private void setFieldTextIfUnchanged(String key, String value) {
        ModernTextField field = fields.get(key);
        if (field != null && !field.isFocused() && !safe(field.getText()).equals(safe(value))) {
            field.setText(value == null ? "" : value);
        }
    }

    private void syncEditorFields() {
        if (restoringHistory) {
            return;
        }
        if (selectedSequence != null && isEditableSequence()) {
            syncIntegerField("sequence.loop", selectedSequence.getLoopDelayTicks(), value -> {
                selectedSequence.setLoopDelayTicks(Math.max(0, value));
            });
        }
        if (selectedStep != null && canEditStep()) {
            syncCoordinateFields();
            syncIntegerField("step.retry", selectedStep.getRetryCount(), selectedStep::setRetryCount);
            syncIntegerField("step.timeout", selectedStep.getPathRetryTimeoutSeconds(),
                    selectedStep::setPathRetryTimeoutSeconds);
            syncIntegerField("step.tolerance", selectedStep.getArrivalToleranceBlocks(),
                    selectedStep::setArrivalToleranceBlocks);
            syncTextField("step.failureTarget", selectedStep.getRetryExhaustedSequenceName(),
                    selectedStep::setRetryExhaustedSequenceName);
        }
        syncActionParameterFields();
    }

    private void syncAuxiliaryFields() {
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            ModernTextField radius = fields.get("recording.radius");
            if (radius != null) {
                try {
                    PathRecordingManager.setInfluenceRadius(Double.parseDouble(radius.getText().trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            ModernTextField packetWindow = fields.get("recording.packetWindowSeconds");
            if (packetWindow != null) {
                try {
                    PathRecordingManager.setPacketRecordWindowSeconds(Integer.parseInt(packetWindow.getText().trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (auxiliaryView == AuxiliaryView.TEMPLATES && selectedTemplateIndex >= 0
                && selectedTemplateIndex < templateModels.size()) {
            LegacyActionTemplateManager.TemplateEditModel model = templateModels.get(selectedTemplateIndex);
            syncTemplateField("template.name", model.name, value -> model.name = value);
            syncTemplateField("template.sequence", model.sequenceName, value -> model.sequenceName = value);
            syncTemplateField("template.defaults", model.defaultsText, value -> model.defaultsText = value);
            syncTemplateField("template.note", model.note, value -> model.note = value);
        }
    }

    private void syncTemplateField(String key, String current, Consumer<String> setter) {
        ModernTextField field = fields.get(key);
        if (field == null || safe(field.getText()).equals(safe(current))) {
            return;
        }
        setter.accept(field.getText());
        templateDirty = true;
    }

    private void syncTextField(String key, String current, Consumer<String> setter) {
        ModernTextField field = fields.get(key);
        if (field == null || safe(field.getText()).equals(safe(current))) {
            return;
        }
        pushHistory("edit-" + key);
        setter.accept(field.getText());
        dirty = true;
    }

    private void syncIntegerField(String key, int current, Consumer<Integer> setter) {
        ModernTextField field = fields.get(key);
        if (field == null || safe(field.getText()).equals(String.valueOf(current))) {
            return;
        }
        try {
            int value = Integer.parseInt(field.getText().trim());
            if (value < 0) {
                return;
            }
            if (value == current) {
                return;
            }
            pushHistory("edit-" + key);
            setter.accept(value);
            dirty = true;
        } catch (NumberFormatException ignored) {
            status("gui.modern.path.wb.u098");
        }
    }

    private void syncCoordinateFields() {
        ModernTextField xField = fields.get("step.x");
        ModernTextField yField = fields.get("step.y");
        ModernTextField zField = fields.get("step.z");
        if (xField == null || yField == null || zField == null) {
            return;
        }
        double[] current = selectedStep.getGotoPoint();
        String[] values = { safe(xField.getText()).trim(), safe(yField.getText()).trim(), safe(zField.getText()).trim() };
        double[] next = new double[3];
        try {
            for (int i = 0; i < 3; i++) {
                next[i] = values[i].isEmpty() || "none".equalsIgnoreCase(values[i])
                        ? Double.NaN : Double.parseDouble(values[i]);
            }
        } catch (NumberFormatException ignored) {
            status("gui.modern.path.wb.u099");
            return;
        }
        boolean changed = false;
        for (int i = 0; i < next.length; i++) {
            boolean bothNaN = Double.isNaN(next[i]) && (current == null || current.length <= i
                    || Double.isNaN(current[i]));
            if (!bothNaN && (current == null || current.length <= i || Double.compare(next[i], current[i]) != 0)) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return;
        }
        pushHistory("edit-coordinates");
        selectedStep.setGotoPoint(next);
        dirty = true;
    }

    private void syncActionParameterFields() {
        if (selectedAction == null || selectedAction.params == null || !canEditStep()) {
            return;
        }
        for (ModernActionEditorSchema.Field fieldSpec : ModernActionEditorSchema.fields(selectedAction.type)) {
            if (fieldSpec.key == null || (fieldSpec.kind != ModernActionEditorSchema.Kind.TEXT
                    && fieldSpec.kind != ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST
                    && !isActionIntegerField(fieldSpec))) {
                continue;
            }
            ModernTextField field = fields.get("param." + fieldSpec.key);
            if (field == null) {
                continue;
            }
            if (ActionEditorUxSupport.isVariableKey(selectedAction.type, fieldSpec.key)) {
                syncVariableNameField(fieldSpec, field);
                continue;
            }
            String fallback = fieldSpec.defaultValue == null ? "" : fieldSpec.defaultValue;
            JsonElement current = selectedAction.params.get(fieldSpec.key);
            String currentText = current == null ? fallback : jsonValue(current);
            if (currentText.equals(field.getText())) {
                continue;
            }
            if (isActionIntegerField(fieldSpec)) {
                syncActionIntegerField(fieldSpec, field);
                continue;
            }
            JsonElement parsed = parseParameterValue(field.getText(), current == null ? parseLiteral(fallback) : current);
            if (parsed == null) {
                status(tr("gui.modern.path.wb.fmt.param_invalid", tr(fieldSpec.label)));
                continue;
            }
            selectedAction.params.add(fieldSpec.key, parsed);
            dirty = true;
        }
    }

    private void syncActionIntegerField(ModernActionEditorSchema.Field fieldSpec, ModernTextField field) {
        String raw = safe(field.getText()).trim();
        if (raw.isEmpty()) {
            return;
        }
        int min = ActionEditorUxSupport.spinnerMin(fieldSpec.key);
        int max = ActionEditorUxSupport.spinnerMax(fieldSpec.key);
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            status(tr("gui.modern.path.wb.fmt.param_int", tr(fieldSpec.label)));
            return;
        }
        if (value < min || value > max) {
            status(tr("gui.modern.path.wb.fmt.param_range", tr(fieldSpec.label), String.valueOf(min), String.valueOf(max)));
            return;
        }
        int current = ActionEditorJson.readInt(selectedAction.params, fieldSpec.key,
                parseDefaultInt(fieldSpec.defaultValue, min), min, max);
        if (value == current) {
            return;
        }
        pushHistory("edit-param-" + fieldSpec.key);
        selectedAction.params.addProperty(fieldSpec.key, value);
        dirty = true;
    }

    private void selectSequenceByName(String name) {
        selectSequenceByName(name, true);
    }

    private void selectSequenceByName(String name, boolean resetNavigationSelection) {
        PathSequence found = findSequence(name);
        if (resetNavigationSelection) navigationSelection.reset(found == null ? null : found.getName());
        selectedSequence = found;
        selectedSequenceName = found == null ? "" : safe(found.getName());
        if (found != null) {
            selectedCategory = safe(found.getCategory()).trim();
            selectedSubCategory = safe(found.getSubCategory()).trim();
            collapsedCategories.remove(selectedCategory);
            if (!selectedSubCategory.isEmpty()) {
                collapsedSubGroups.remove(groupKey(selectedCategory, selectedSubCategory));
            }
            selectedStepIndex = found.getSteps().isEmpty() ? -1 : clamp(selectedStepIndex, 0, found.getSteps().size() - 1);
            if (selectedStepIndex < 0 && !found.getSteps().isEmpty()) {
                selectedStepIndex = 0;
            }
            selectedStep = selectedStepIndex < 0 ? null : found.getSteps().get(selectedStepIndex);
            syncStepSelectionSet();
            selectedActionIndex = -1;
            selectedAction = null;
            selectedActionIndices.clear();
            selectionAnchorActionIndex = -1;
            listFocus = ListFocus.STEP;
            stepScroll = 0;
            actionScroll = 0;
            parameterScroll = 0;
            try {
                MainUiLayoutManager.recordSequenceOpened(found.getName());
            } catch (Exception ignored) {
            }
        } else {
            selectedStep = null;
            selectedAction = null;
            selectedStepIndex = -1;
            selectedActionIndex = -1;
            selectedStepIndices.clear();
            selectedActionIndices.clear();
        }
        bindEditorFields();
    }

    private void selectStep(int index) {
        if (selectedSequence == null || index < 0 || index >= selectedSequence.getSteps().size()) {
            selectedStepIndex = -1;
            selectedStep = null;
            selectedStepIndices.clear();
            selectedActionIndex = -1;
            selectedAction = null;
            selectedActionIndices.clear();
            return;
        }
        selectedStepIndex = index;
        selectedStep = selectedSequence.getSteps().get(index);
        selectedStepIndices.clear();
        selectedStepIndices.add(Integer.valueOf(index));
        selectionAnchorStepIndex = index;
        selectedActionIndex = -1;
        selectedAction = null;
        selectedActionIndices.clear();
        selectionAnchorActionIndex = -1;
        listFocus = ListFocus.STEP;
        actionScroll = 0;
        parameterScroll = 0;
        bindEditorFields();
    }

    private void selectAction(int index) {
        if (selectedStep == null || index < 0 || index >= selectedStep.getActions().size()) {
            selectedActionIndex = -1;
            selectedAction = null;
            selectedActionIndices.clear();
            return;
        }
        selectedActionIndex = index;
        selectedAction = selectedStep.getActions().get(index);
        selectedActionIndices.clear();
        selectedActionIndices.add(Integer.valueOf(index));
        selectionAnchorActionIndex = index;
        listFocus = ListFocus.ACTION;
        parameterScroll = 0;
        resetSlotGridPress();
        bindEditorFields();
    }

    private void refreshSelection() {
        if (auxiliaryView == AuxiliaryView.RECORDING && recordingDraft != null) {
            selectedSequence = recordingDraft;
            selectedSequenceName = recordingDraft.getName();
            selectedStepIndex = recordingDraft.getSteps().isEmpty() ? -1
                    : clamp(selectedStepIndex, 0, recordingDraft.getSteps().size() - 1);
            selectedStep = selectedStepIndex < 0 ? null : recordingDraft.getSteps().get(selectedStepIndex);
            selectedActionIndex = selectedStep == null || selectedStep.getActions().isEmpty() ? -1
                    : clamp(selectedActionIndex, 0, selectedStep.getActions().size() - 1);
            selectedAction = selectedActionIndex < 0 ? null : selectedStep.getActions().get(selectedActionIndex);
            syncStepSelectionSet();
            syncActionSelectionSet();
            bindEditorFields();
            return;
        }
        if (selectedSequenceName.isEmpty()) {
            selectedSequence = null;
            selectedStep = null;
            selectedAction = null;
            return;
        }
        PathSequence sequence = findSequence(selectedSequenceName);
        if (sequence == null) {
            selectedSequence = null;
            selectedStep = null;
            selectedAction = null;
            selectedSequenceName = "";
            return;
        }
        selectedSequence = sequence;
        selectedStep = selectedStepIndex >= 0 && selectedStepIndex < sequence.getSteps().size()
                ? sequence.getSteps().get(selectedStepIndex) : null;
        selectedAction = selectedStep != null && selectedActionIndex >= 0
                && selectedActionIndex < selectedStep.getActions().size()
                        ? selectedStep.getActions().get(selectedActionIndex) : null;
        syncStepSelectionSet();
        syncActionSelectionSet();
        resetSlotGridPress();
        bindEditorFields();
    }

    private boolean handleNavigationClick(int mouseX, int mouseY) {
        if (navigationDividerBounds != null && navigationDividerBounds.contains(mouseX, mouseY)) {
            draggingNavigationDivider = true;
            return true;
        }
        if (navigationContentBounds == null || !navigationContentBounds.contains(mouseX, mouseY)) {
            return true;
        }
        for (NavHit hit : navHits) {
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            listFocus = ListFocus.NAVIGATION;
            if (hit.kind == NavKind.CATEGORY) {
                selectedCategory = hit.category;
                selectedSubCategory = "";
                if (hit.toggle != null && hit.toggle.contains(mouseX, mouseY)) {
                    if (!collapsedCategories.add(hit.category)) {
                        collapsedCategories.remove(hit.category);
                    }
                    return true;
                }
                selectSequence(visibleSequenceForScope(hit.category, ""));
                return true;
            }
            if (hit.kind == NavKind.SUBCATEGORY) {
                selectedCategory = hit.category;
                selectedSubCategory = hit.subCategory;
                if (hit.toggle != null && hit.toggle.contains(mouseX, mouseY)) {
                    String key = groupKey(hit.category, hit.subCategory);
                    if (!collapsedSubGroups.add(key)) {
                        collapsedSubGroups.remove(key);
                    }
                    return true;
                }
                selectSequence(visibleSequenceForScope(hit.category, hit.subCategory));
                return true;
            }
            if (hit.sequence != null) {
                selectedCategory = safe(hit.sequence.getCategory()).trim();
                selectedSubCategory = safe(hit.sequence.getSubCategory()).trim();
                List<String> visible = new ArrayList<>();
                for (NavHit row : navHits) {
                    if (row.sequence != null) visible.add(row.sequence.getName());
                }
                String active = navigationSelection.click(hit.sequence.getName(), visible,
                        isControlDown(), isShiftDown());
                selectSequenceByName(active, false);
                listFocus = ListFocus.NAVIGATION;
                return true;
            }
        }
        return true;
    }

    private boolean openNavigationContextMenu(int mouseX, int mouseY) {
        NavHit target = null;
        for (NavHit hit : navHits) if (hit.bounds.contains(mouseX, mouseY)) { target = hit; break; }
        final NavHit hit = target;
        menuTitle = "gui.modern.path.wb.ctx.navigation";
        menuItems.clear();
        if (hit == null) {
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.new_category", true, false,
                    () -> beginModal(ModalMode.ADD_CATEGORY, "gui.modern.path.wb.u340", "gui.modern.path.wb.u341")));
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.new_sequence", !selectedCategory.isEmpty(), false,
                    () -> beginModal(ModalMode.ADD_SEQUENCE, "gui.modern.path.wb.u344", selectedCategory)));
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.paste_sequence", clipboardPayloadType == ClipboardPayloadType.SEQUENCE,
                    false, this::pasteSequenceFromClipboard));
        } else if (hit.kind == NavKind.SEQUENCE) {
            selectSequenceByName(hit.sequence.getName(), !navigationSelection.contains(hit.sequence.getName()));
            listFocus = ListFocus.NAVIGATION;
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.rename", isEditableSequence(), false,
                    () -> beginModal(ModalMode.RENAME_SEQUENCE, "gui.modern.path.wb.u345", selectedSequence.getName())));
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.copy_sequence", true, false, this::copySequenceToClipboard));
            menuItems.add(new RectAction("gui.modern.path.wb.u347", isEditableSequence(), true,
                    () -> beginModal(ModalMode.DELETE_SEQUENCE, "gui.modern.path.wb.u347", selectedSequence.getName())));
        } else {
            selectedCategory = hit.category;
            selectedSubCategory = hit.kind == NavKind.SUBCATEGORY ? hit.subCategory : "";
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.new_subcategory", true, false,
                    () -> beginModal(ModalMode.ADD_SUBCATEGORY, "gui.modern.path.wb.u343", selectedCategory)));
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.new_sequence", true, false,
                    () -> beginModal(ModalMode.ADD_SEQUENCE, "gui.modern.path.wb.u344", selectedCategory)));
            boolean canRename = hit.kind == NavKind.CATEGORY ? !isProtectedCategory(selectedCategory)
                    : !hit.subCategory.isEmpty();
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.rename", canRename, false,
                    () -> beginModal(hit.kind == NavKind.CATEGORY ? ModalMode.RENAME_CATEGORY : ModalMode.RENAME_SUBCATEGORY,
                            hit.kind == NavKind.CATEGORY ? "gui.modern.path.wb.u135" : "gui.modern.path.wb.u140",
                            hit.kind == NavKind.CATEGORY ? selectedCategory : selectedSubCategory)));
            boolean canDelete = hit.kind == NavKind.CATEGORY ? !isProtectedCategory(selectedCategory)
                    : !hit.subCategory.isEmpty();
            menuItems.add(new RectAction("gui.modern.path.wb.ctx.delete", canDelete, true,
                    () -> beginModal(hit.kind == NavKind.CATEGORY ? ModalMode.DELETE_CATEGORY : ModalMode.DELETE_SUBCATEGORY,
                            hit.kind == NavKind.CATEGORY ? "gui.modern.path.wb.u137" : "gui.modern.path.wb.u142",
                            hit.kind == NavKind.CATEGORY ? selectedCategory : selectedSubCategory)));
        }
        setMenuAnchor(mouseX, mouseY);
        return true;
    }

    private boolean openStepContextMenu(int mouseX, int mouseY) {
        for (RowHit hit : stepHits) if (hit.bounds.contains(mouseX, mouseY)) {
            applyStepSelectionClick(hit.index, false, false); listFocus = ListFocus.STEP; break;
        }
        menuTitle = "gui.modern.path.wb.ctx.steps"; menuItems.clear();
        menuItems.add(new RectAction("gui.modern.path.tool.u008", isEditableSequence(), false, this::addStep));
        menuItems.add(new RectAction("gui.modern.path.wb.u144", canEditStep(), false, this::openStepSettings));
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.copy", selectedSequence != null && !selectedStepIndexList().isEmpty(), false, this::copySelectedStepsToClipboard));
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.paste", isEditableSequence() && clipboardPayloadType == ClipboardPayloadType.STEPS, false, this::pasteClipboardIntoSelection));
        menuItems.add(new RectAction("gui.modern.path.tool.u018", canEditStep(), true, this::deleteStep));
        setMenuAnchor(mouseX, mouseY); return true;
    }

    private boolean openActionContextMenu(int mouseX, int mouseY) {
        for (RowHit hit : actionHits) if (hit.bounds.contains(mouseX, mouseY)) {
            applyActionSelectionClick(hit.index, false, false); listFocus = ListFocus.ACTION; break;
        }
        menuTitle = "gui.modern.path.wb.ctx.actions"; menuItems.clear();
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.edit", canEditStep() && selectedAction != null, false, () -> openActionEditorPage(false)));
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.copy", selectedStep != null && !selectedActionIndexList().isEmpty(), false, this::copySelectedActionsToClipboard));
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.paste", canEditStep() && clipboardPayloadType == ClipboardPayloadType.ACTIONS, false, this::pasteClipboardIntoSelection));
        menuItems.add(new RectAction("gui.modern.path.wb.ctx.delete", canEditStep() && selectedAction != null, true, this::deleteAction));
        setMenuAnchor(mouseX, mouseY); return true;
    }

    private void selectSequence(PathSequence sequence) {
        if (sequence == null) {
            navigationSelection.reset(null);
            selectedSequence = null;
            selectedSequenceName = "";
            selectedStep = null;
            selectedAction = null;
            selectedStepIndex = -1;
            selectedActionIndex = -1;
            bindEditorFields();
            return;
        }
        selectSequenceByName(sequence.getName());
    }

    private PathSequence visibleSequenceForScope(String category, String subCategory) {
        for (PathSequence sequence : sequencesForCategory(category, subCategory)) {
            return sequence;
        }
        return null;
    }

    private boolean handleStepClick(int mouseX, int mouseY) {
        if (editorDividerBounds != null && editorDividerBounds.contains(mouseX, mouseY)) {
            draggingEditorDivider = true;
            return true;
        }
        if (stepOptionsToggleBounds != null && stepOptionsToggleBounds.contains(mouseX, mouseY)) {
            stepOptionsExpanded = !stepOptionsExpanded;
            clearFocus();
            return true;
        }
        if (stepRunBounds != null && stepRunBounds.contains(mouseX, mouseY)) {
            runSelected(true);
            return true;
        }
        for (RowHit hit : stepHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                if (stepNoteBounds(hit.bounds).contains(mouseX, mouseY)) {
                    openStepNoteModal(hit.index);
                    return true;
                }
                boolean ctrl = isControlDown();
                boolean shift = isShiftDown();
                boolean alreadySelected = selectedStepIndices.contains(Integer.valueOf(hit.index));
                List<Integer> existingBlock = consecutiveSelectedSteps();
                if (!ctrl && !shift && alreadySelected && existingBlock != null && existingBlock.size() > 1
                        && existingBlock.contains(Integer.valueOf(hit.index))) {
                    listFocus = ListFocus.STEP;
                } else {
                    applyStepSelectionClick(hit.index, ctrl, shift);
                }
                List<Integer> block = consecutiveSelectedSteps();
                if (!ctrl && !shift && isEditableSequence() && block != null && !block.isEmpty()
                        && block.contains(Integer.valueOf(hit.index))) {
                    draggingStepIndex = block.get(0).intValue();
                    draggingStepCount = block.size();
                    stepDropIndex = draggingStepIndex;
                    dragStartY = mouseY;
                } else {
                    draggingStepIndex = -1;
                    draggingStepCount = 1;
                }
                return true;
            }
        }
        return true;
    }

    private boolean handleActionClick(int mouseX, int mouseY) {
        if (actionMoreBounds != null && actionMoreBounds.contains(mouseX, mouseY)) {
            openOverflowMenu(PathWorkbenchToolCatalog.Zone.ACTION, mouseX, mouseY);
            return true;
        }
        for (RowHit hit : actionPacketToggleHits) {
            if (hit.bounds.contains(mouseX, mouseY) && selectedStep != null
                    && hit.index >= 0 && hit.index < selectedStep.getActions().size()) {
                applyActionSelectionClick(hit.index, false, false);
                toggleRecordingPacketAction(selectedAction);
                return true;
            }
        }
        for (RowHit hit : actionHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                boolean ctrl = isControlDown();
                boolean shift = isShiftDown();
                boolean alreadySelected = selectedActionIndices.contains(Integer.valueOf(hit.index));
                long now = System.currentTimeMillis();
                if (!ctrl && !shift && alreadySelected && hit.index == lastActionClickIndex
                        && now - lastActionClickTime <= DOUBLE_CLICK_MS) {
                    applyActionSelectionClick(hit.index, false, false);
                    openActionEditorPage(false);
                    lastActionClickIndex = -1;
                    draggingActionIndex = -1;
                    return true;
                }
                List<Integer> existingBlock = consecutiveSelectedActions();
                if (!ctrl && !shift && alreadySelected && existingBlock != null && existingBlock.size() > 1
                        && existingBlock.contains(Integer.valueOf(hit.index))) {
                    listFocus = ListFocus.ACTION;
                } else {
                    applyActionSelectionClick(hit.index, ctrl, shift);
                }
                lastActionClickTime = now;
                lastActionClickIndex = hit.index;
                List<Integer> block = consecutiveSelectedActions();
                if (!ctrl && !shift && canEditStep() && block != null && !block.isEmpty()
                        && block.contains(Integer.valueOf(hit.index))) {
                    draggingActionIndex = block.get(0).intValue();
                    draggingActionCount = block.size();
                    actionDropIndex = draggingActionIndex;
                    dragStartY = mouseY;
                } else {
                    draggingActionIndex = -1;
                    draggingActionCount = 1;
                }
                return true;
            }
        }
        return true;
    }

    private void openActionEditorPage(boolean newAction) {
        if (newAction) {
            selectedAction = null;
            selectedActionIndex = -1;
        }
        actionPageScroll = 0;
        auxiliaryView = AuxiliaryView.ACTION_EDITOR;
        clearFocus();
        if (!"action_editor".equals(command)) {
            requestNativeRoute("action_editor");
        }
    }

    private void cycleActionParameter(String key, JsonElement current) {
        if (selectedAction == null) {
            return;
        }
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        ModernActionEditorSchema.Field schemaField = null;
        for (ModernActionEditorSchema.Field field : ModernActionEditorSchema.fields(selectedAction.type)) {
            if (field.key != null && field.key.equals(key)) {
                schemaField = field;
                break;
            }
        }
        if (current == null) {
            current = schemaField == null ? null : parseLiteral(schemaField.defaultValue);
        }
        String currentValue = jsonValue(current);
        String[] choices = schemaField == null ? new String[0] : schemaField.choices.toArray(new String[0]);
        if (choices.length > 0) {
            pushHistory("cycle-action-parameter");
            int index = 0;
            for (int i = 0; i < choices.length; i++) {
                if (choices[i].equalsIgnoreCase(currentValue)) {
                    index = (i + 1) % choices.length;
                    break;
                }
            }
            selectedAction.params.addProperty(key, choices[index]);
            dirty = true;
            return;
        }
        if (current != null && current.isJsonPrimitive() && current.getAsJsonPrimitive().isBoolean()) {
            pushHistory("cycle-action-parameter");
            selectedAction.params.addProperty(key, !current.getAsBoolean());
            dirty = true;
        }
    }

    private boolean handleParameterControlClick(ParameterHit hit, int mouseX, int mouseY) {
        if (hit == null || selectedAction == null || selectedAction.params == null) return false;
        ModernActionEditorSchema.Field field = hit.field;
        if (!isActionParameterEnabled(field)) return true;
        if (field.kind == ModernActionEditorSchema.Kind.TOGGLE) {
            cycleActionParameter(field.key, selectedAction.params.get(field.key));
            return true;
        }
        if (hit.key != null && hit.key.startsWith("spinner-:")) {
            nudgeSpinner(field, -1);
            return true;
        }
        if (hit.key != null && hit.key.startsWith("spinner+:")) {
            nudgeSpinner(field, 1);
            return true;
        }
        if (hit.key != null && hit.key.startsWith("hotbar:")) {
            String[] parts = hit.key.split(":");
            if (parts.length >= 3 && canEditStep()) {
                pushHistory("choose-hotbar-slot");
                int slot = Integer.parseInt(parts[2]);
                selectedAction.params.addProperty(field.key, "tempslot".equals(field.key) ? slot - 1 : slot);
                dirty = true;
            }
            return true;
        }
        if (hit.key != null && hit.key.startsWith("capture:")) {
            captureCurrentValue(field);
            return true;
        }
        if (hit.key != null && hit.key.startsWith("varscope:")) {
            openVariableScopeMenu(field, mouseX, mouseY);
            return true;
        }
        if (isSourceVariablePickerField(field)) {
            openActionPicker(field);
            return true;
        }
        if (ActionEditorUxSupport.isClickButtonKey(selectedAction.type, field.key)
                || "clickType".equals(field.key) && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)) {
            openClickSemanticsMenu(mouseX, mouseY);
            return true;
        }
        if (field.kind == ModernActionEditorSchema.Kind.KEYBOARD_PICKER) {
            openActionPicker(field);
            return true;
        }
        if (isActionPickerField(field)) {
            openActionPicker(field);
            return true;
        }
        if (field.kind == ModernActionEditorSchema.Kind.CHOICE) {
            openParameterChoiceMenu(field, mouseX, mouseY);
            return true;
        }
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION
                || field.kind == ModernActionEditorSchema.Kind.EXPRESSION_LIST
                || field.kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            openExpressionEditor(field, -1);
            return true;
        }
        if (field.kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST) return false;
        return false;
    }

    private void openClickSemanticsMenu(int mouseX, int mouseY) {
        if (selectedAction == null) {
            return;
        }
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        menuTitle = I18n.format("gui.path.action_editor.label.click_semantics");
        menuItems.clear();
        String currentType = jsonValue(selectedAction.params.get("clickType"));
        int currentButton = ActionEditorJson.readInt(selectedAction.params, "button", 0, 0, 8);
        for (final ClickSemantics.Option option : ClickSemantics.options()) {
            String label = option.label();
            boolean selected = option.clickType.equalsIgnoreCase(currentType) && option.button == currentButton;
            if (option.disabled) {
                label = tr("gui.modern.path.wb.fmt.unavailable", tr(label));
            }
            RectAction item = new RectAction(label, !option.disabled, false, new Runnable() {
                @Override
                public void run() {
                    pushHistory("choose-click-semantics");
                    selectedAction.params.addProperty("clickType", option.clickType);
                    selectedAction.params.addProperty("button", option.button);
                    dirty = true;
                    bindEditorFields();
                }
            });
            item.selected = selected;
            menuItems.add(item);
        }
        setMenuAnchor(mouseX, mouseY);
    }

    private void openParameterChoiceMenu(ModernActionEditorSchema.Field field, int mouseX, int mouseY) {
        if (field == null || field.choices.isEmpty()) return;
        List<String> choices = field.choices;
        if ("presetName".equals(field.key) && "toggle_kill_aura".equalsIgnoreCase(selectedAction.type)) {
            choices = new ArrayList<>();
            choices.add("");
            for (KillAuraHandler.KillAuraPreset preset : KillAuraHandler.getPresetSnapshots()) {
                choices.add(preset.name);
            }
        }
        menuTitle = field.label;
        menuItems.clear();
        String current = selectedAction.params.has(field.key)
                ? jsonValue(selectedAction.params.get(field.key)) : safe(field.defaultValue);
        if ("pickupFilterMode".equals(field.key) && !selectedAction.params.has(field.key)
                && !InventoryItemFilterExpressionEngine.readExpressions(selectedAction.params).isEmpty()) {
            current = "CUSTOM";
        }
        for (String choice : choices) {
            final String value = choice;
            String label = actionChoiceDisplay(field.key, value);
            // Avoid a check-mark prefix here: the bundled font has no glyph for
            // it and renders a square before options such as “玩家”.
            RectAction item = new RectAction(label, true, false, () -> {
                pushHistory("choose-action-parameter");
                selectedAction.params.addProperty(field.key, value);
                dirty = true;
                bindEditorFields();
            });
            item.selected = value.equals(current);
            menuItems.add(item);
        }
        setMenuAnchor(mouseX, mouseY);
    }

    private int dropIndex(ModernMainLayout.Rect list, int rowHeight, int count, int mouseY, int scroll) {
        if (list == null || count <= 0) {
            return 0;
        }
        int index = (mouseY - list.y + rowHeight / 2 + scroll) / rowHeight;
        return clamp(index, 0, count);
    }

    private void finishStepDrag() {
        int source = draggingStepIndex;
        int count = Math.max(1, draggingStepCount);
        int target = stepDropIndex;
        draggingStepIndex = -1;
        draggingStepCount = 1;
        stepDropIndex = -1;
        if (!isEditableSequence() || selectedSequence == null || source < 0 || target < 0
                || source >= selectedSequence.getSteps().size()) {
            return;
        }
        int movedTo = moveBlock(selectedSequence.getSteps(), source, count, target, "drag-step");
        if (movedTo < 0) {
            return;
        }
        selectStepRange(movedTo, count);
        refreshSelection();
        dirty = true;
        status(count > 1 ? tr("gui.modern.path.wb.fmt.moved_steps", String.valueOf(count)) : "gui.modern.path.wb.u100");
    }

    private void finishActionDrag() {
        int source = draggingActionIndex;
        int count = Math.max(1, draggingActionCount);
        int target = actionDropIndex;
        draggingActionIndex = -1;
        draggingActionCount = 1;
        actionDropIndex = -1;
        if (!canEditStep() || selectedStep == null || source < 0 || target < 0
                || source >= selectedStep.getActions().size()) {
            return;
        }
        int movedTo = moveBlock(selectedStep.getActions(), source, count, target, "drag-action");
        if (movedTo < 0) {
            return;
        }
        selectActionRange(movedTo, count);
        refreshSelection();
        dirty = true;
        status(count > 1 ? tr("gui.modern.path.wb.fmt.moved_actions", String.valueOf(count)) : "gui.modern.path.wb.u101");
    }

    private void drawActionLibraryScrollbar(ModernMainLayout.Rect list, int mouseX, int mouseY) {
        if (list == null || paletteMaxScroll <= 0 || list.height <= 0) {
            actionLibraryScrollTrack = null;
            actionLibraryScrollThumb = null;
            actionLibraryScrollHover *= 0.7F;
            return;
        }
        int total = Math.max(1, actionPageRows.size());
        int visible = Math.max(1, list.height / 25);
        int thumbHeight = Math.max(18, list.height * visible / Math.max(visible, total));
        thumbHeight = Math.min(list.height, thumbHeight);
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + (paletteMaxScroll <= 0 ? 0 : travel * actionPageScroll / Math.max(1, paletteMaxScroll));
        actionLibraryScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        actionLibraryScrollThumb = new ModernMainLayout.Rect(list.right() - 12, thumbY, 10, thumbHeight);
        boolean hovered = draggingActionLibraryScroll
                || actionLibraryScrollTrack.contains(mouseX, mouseY);
        float target = hovered ? 1.0F : 0.0F;
        actionLibraryScrollHover += (target - actionLibraryScrollHover) * 0.28F;
        if (Math.abs(target - actionLibraryScrollHover) < 0.01F) {
            actionLibraryScrollHover = target;
        }
        int grown = Math.round(4 + 6 * actionLibraryScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * actionLibraryScrollHover));
        int thumbX = list.right() - grown - 2;
        int trackX = list.right() - trackWidth - 3;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                ModernUiRenderer.ACCENT, actionLibraryScrollHover);
        ModernUiRenderer.drawRoundedRect(trackX, list.y, trackWidth, list.height, 2, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2), thumbColor);
        actionLibraryScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
    }

    private void drawActionPickerScrollbar(ModernMainLayout.Rect list, int total, int visible, int mouseX, int mouseY) {
        if (list == null || actionPickerMaxScroll <= 0 || list.height <= 0) {
            actionPickerScrollTrack = null;
            actionPickerScrollThumb = null;
            actionPickerScrollHover *= 0.7F;
            return;
        }
        int thumbHeight = Math.max(18, list.height * Math.max(1, visible) / Math.max(1, total));
        thumbHeight = Math.min(list.height, thumbHeight);
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * actionPickerScroll / Math.max(1, actionPickerMaxScroll);
        actionPickerScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingActionPickerScroll || actionPickerScrollTrack.contains(mouseX, mouseY);
        float target = hovered ? 1.0F : 0.0F;
        actionPickerScrollHover += (target - actionPickerScrollHover) * 0.28F;
        if (Math.abs(target - actionPickerScrollHover) < 0.01F) actionPickerScrollHover = target;
        int grown = Math.round(4 + 6 * actionPickerScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * actionPickerScrollHover));
        int thumbX = list.right() - grown - 2;
        int trackX = list.right() - trackWidth - 3;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                ModernUiRenderer.ACCENT, actionPickerScrollHover);
        ModernUiRenderer.drawRoundedRect(trackX, list.y, trackWidth, list.height, 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2), thumbColor);
        actionPickerScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
    }

    private boolean beginActionPickerScrollDrag(int mouseX, int mouseY) {
        if (actionPickerScrollTrack == null || actionPickerMaxScroll <= 0
                || !actionPickerScrollTrack.contains(mouseX, mouseY)) {
            return false;
        }
        draggingActionPickerScroll = true;
        actionPickerScrollDragOffset = actionPickerScrollThumb != null && actionPickerScrollThumb.contains(mouseX, mouseY)
                ? mouseY - actionPickerScrollThumb.y
                : actionPickerScrollThumb == null ? 9 : actionPickerScrollThumb.height / 2;
        applyActionPickerScrollFromMouse(mouseY);
        return true;
    }

    private void applyActionPickerScrollFromMouse(int mouseY) {
        if (actionPickerScrollTrack == null || actionPickerScrollThumb == null || actionPickerMaxScroll <= 0) return;
        int travel = Math.max(1, actionPickerScrollTrack.height - actionPickerScrollThumb.height);
        int target = clamp(mouseY - actionPickerScrollDragOffset, actionPickerScrollTrack.y,
                actionPickerScrollTrack.y + travel);
        actionPickerScroll = clamp(Math.round((target - actionPickerScrollTrack.y) * actionPickerMaxScroll
                / (float) travel), 0, actionPickerMaxScroll);
    }

    private int mixScrollColor(int from, int to, float amount) {
        float t = amount < 0 ? 0 : amount > 1 ? 1 : amount;
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = (int) (((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private boolean beginActionLibraryScrollDrag(int mouseX, int mouseY) {
        if (actionLibraryScrollTrack == null || paletteMaxScroll <= 0
                || !actionLibraryScrollTrack.contains(mouseX, mouseY)) {
            return false;
        }
        draggingActionLibraryScroll = true;
        if (actionLibraryScrollThumb != null && actionLibraryScrollThumb.contains(mouseX, mouseY)) {
            actionLibraryScrollDragOffset = mouseY - actionLibraryScrollThumb.y;
        } else {
            actionLibraryScrollDragOffset = actionLibraryScrollThumb == null
                    ? 9 : actionLibraryScrollThumb.height / 2;
        }
        applyActionLibraryScrollFromMouse(mouseY);
        return true;
    }

    private void applyActionLibraryScrollFromMouse(int mouseY) {
        if (actionLibraryScrollTrack == null || actionLibraryScrollThumb == null || paletteMaxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, actionLibraryScrollTrack.height - actionLibraryScrollThumb.height);
        int target = clamp(mouseY - actionLibraryScrollDragOffset, actionLibraryScrollTrack.y,
                actionLibraryScrollTrack.y + travel);
        actionPageScroll = clamp(Math.round((target - actionLibraryScrollTrack.y) * paletteMaxScroll / (float) travel),
                0, paletteMaxScroll);
    }

    private void drawExpressionTemplateScrollbar(ModernMainLayout.Rect list, int mouseX, int mouseY) {
        if (list == null || expressionTemplateMaxScroll <= 0 || list.height <= 0) {
            expressionTemplateScrollTrack = null;
            expressionTemplateScrollThumb = null;
            expressionTemplateScrollHover *= 0.7F;
            return;
        }
        int visible = Math.max(1, list.height / 48);
        int total = Math.max(visible, visible + expressionTemplateMaxScroll / 48);
        int thumbHeight = Math.max(18, list.height * visible / Math.max(visible, total));
        thumbHeight = Math.min(list.height, thumbHeight);
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * expressionTemplateScroll / Math.max(1, expressionTemplateMaxScroll);
        expressionTemplateScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingExpressionTemplateScroll
                || expressionTemplateScrollTrack.contains(mouseX, mouseY);
        float target = hovered ? 1.0F : 0.0F;
        expressionTemplateScrollHover += (target - expressionTemplateScrollHover) * 0.28F;
        if (Math.abs(target - expressionTemplateScrollHover) < 0.01F) {
            expressionTemplateScrollHover = target;
        }
        int grown = Math.round(4 + 6 * expressionTemplateScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * expressionTemplateScrollHover));
        int thumbX = list.right() - grown - 2;
        int trackX = list.right() - trackWidth - 3;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                ModernUiRenderer.ACCENT, expressionTemplateScrollHover);
        ModernUiRenderer.drawRoundedRect(trackX, list.y, trackWidth, list.height, 2, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2), thumbColor);
        expressionTemplateScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
    }

    private boolean beginExpressionTemplateScrollDrag(int mouseX, int mouseY) {
        if (expressionTemplateScrollTrack == null || expressionTemplateMaxScroll <= 0
                || !expressionTemplateScrollTrack.contains(mouseX, mouseY)) {
            return false;
        }
        draggingExpressionTemplateScroll = true;
        if (expressionTemplateScrollThumb != null && expressionTemplateScrollThumb.contains(mouseX, mouseY)) {
            expressionTemplateScrollDragOffset = mouseY - expressionTemplateScrollThumb.y;
        } else {
            expressionTemplateScrollDragOffset = expressionTemplateScrollThumb == null
                    ? 9 : expressionTemplateScrollThumb.height / 2;
        }
        applyExpressionTemplateScrollFromMouse(mouseY);
        return true;
    }

    private void applyExpressionTemplateScrollFromMouse(int mouseY) {
        if (expressionTemplateScrollTrack == null || expressionTemplateScrollThumb == null
                || expressionTemplateMaxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, expressionTemplateScrollTrack.height - expressionTemplateScrollThumb.height);
        int target = clamp(mouseY - expressionTemplateScrollDragOffset, expressionTemplateScrollTrack.y,
                expressionTemplateScrollTrack.y + travel);
        expressionTemplateScroll = clamp(Math.round((target - expressionTemplateScrollTrack.y)
                * expressionTemplateMaxScroll / (float) travel), 0, expressionTemplateMaxScroll);
    }

    private void drawExpressionPreviewScrollbar(ModernMainLayout.Rect list, int mouseX, int mouseY) {
        if (list == null || expressionPreviewMaxScroll <= 0 || list.height <= 0) {
            expressionPreviewScrollTrack = null;
            expressionPreviewScrollThumb = null;
            expressionPreviewScrollHover *= 0.7F;
            return;
        }
        int visible = Math.max(1, list.height / 13);
        int total = Math.max(visible, visible + expressionPreviewMaxScroll / 13);
        int thumbHeight = Math.max(18, list.height * visible / Math.max(visible, total));
        thumbHeight = Math.min(list.height, thumbHeight);
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * expressionPreviewScroll / Math.max(1, expressionPreviewMaxScroll);
        expressionPreviewScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingExpressionPreviewScroll
                || expressionPreviewScrollTrack.contains(mouseX, mouseY);
        float target = hovered ? 1.0F : 0.0F;
        expressionPreviewScrollHover += (target - expressionPreviewScrollHover) * 0.28F;
        if (Math.abs(target - expressionPreviewScrollHover) < 0.01F) {
            expressionPreviewScrollHover = target;
        }
        int grown = Math.round(4 + 6 * expressionPreviewScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * expressionPreviewScrollHover));
        int thumbX = list.right() - grown - 2;
        int trackX = list.right() - trackWidth - 3;
        int thumbColor = hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                ModernUiRenderer.ACCENT, expressionPreviewScrollHover);
        ModernUiRenderer.drawRoundedRect(trackX, list.y, trackWidth, list.height, 2, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2), thumbColor);
        expressionPreviewScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
    }

    private boolean beginExpressionPreviewScrollDrag(int mouseX, int mouseY) {
        if (expressionPreviewScrollTrack == null || expressionPreviewMaxScroll <= 0
                || !expressionPreviewScrollTrack.contains(mouseX, mouseY)) {
            return false;
        }
        draggingExpressionPreviewScroll = true;
        if (expressionPreviewScrollThumb != null && expressionPreviewScrollThumb.contains(mouseX, mouseY)) {
            expressionPreviewScrollDragOffset = mouseY - expressionPreviewScrollThumb.y;
        } else {
            expressionPreviewScrollDragOffset = expressionPreviewScrollThumb == null
                    ? 9 : expressionPreviewScrollThumb.height / 2;
        }
        applyExpressionPreviewScrollFromMouse(mouseY);
        return true;
    }

    private void applyExpressionPreviewScrollFromMouse(int mouseY) {
        if (expressionPreviewScrollTrack == null || expressionPreviewScrollThumb == null
                || expressionPreviewMaxScroll <= 0) {
            return;
        }
        int travel = Math.max(1, expressionPreviewScrollTrack.height - expressionPreviewScrollThumb.height);
        int target = clamp(mouseY - expressionPreviewScrollDragOffset, expressionPreviewScrollTrack.y,
                expressionPreviewScrollTrack.y + travel);
        expressionPreviewScroll = clamp(Math.round((target - expressionPreviewScrollTrack.y)
                * expressionPreviewMaxScroll / (float) travel), 0, expressionPreviewMaxScroll);
    }

    private void drawScrollBarFor(ModernHoverScrollbar bar, ModernMainLayout.Rect rect, int scroll, int maxScroll,
            int itemCount, int visibleCount, int mouseX, int mouseY, IntConsumer setter) {
        if (bar == null) {
            return;
        }
        bar.draw(rect, scroll, maxScroll, visibleCount, itemCount, mouseX, mouseY, setter);
    }

    private ModernHoverScrollbar[] hoverScrollBars() {
        return new ModernHoverScrollbar[] {
                navigationScrollBar, stepScrollBar, actionScrollBar, validationScrollBar, logListScrollBar,
                logDetailScrollBar, recordingScrollBar, parameterScrollBar, stepTargetNavScrollBar,
                stepTargetDetailsScrollBar, recordingCategoryScrollBar
        };
    }

    private void idleHoverScrollBars() {
        for (ModernHoverScrollbar bar : hoverScrollBars()) {
            bar.idle();
        }
    }

    private boolean beginHoverScrollDrag(int mouseX, int mouseY) {
        if (recordingCategoryPickerOpen) {
            return recordingCategoryScrollBar.beginDrag(mouseX, mouseY);
        }
        for (ModernHoverScrollbar bar : hoverScrollBars()) {
            if (bar.beginDrag(mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private boolean applyHoverScrollDrag(int mouseX, int mouseY) {
        for (ModernHoverScrollbar bar : hoverScrollBars()) {
            if (bar.isDragging()) {
                return bar.applyDrag(mouseX, mouseY);
            }
        }
        return false;
    }

    private boolean endHoverScrollDrag() {
        boolean dragging = false;
        for (ModernHoverScrollbar bar : hoverScrollBars()) {
            if (bar.isDragging()) {
                dragging = true;
            }
            bar.endDrag();
        }
        return dragging;
    }

    private int countErrors(List<Issue> issues) {
        int errors = 0;
        if (issues != null) {
            for (Issue issue : issues) {
                if (issue != null && issue.getSeverity() == PathConfigValidator.Severity.ERROR) {
                    errors++;
                }
            }
        }
        return errors;
    }

    private int sessionAccent(SessionSnapshot session) {
        if (session == null || !session.isFinished()) {
            return ModernUiRenderer.WARNING;
        }
        return session.isSuccess() ? ModernUiRenderer.SUCCESS : 0xFFB64A4A;
    }

    private void status(String message) {
        status = message == null ? "" : message;
        statusUntil = System.currentTimeMillis() + 5000L;
    }

    private boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
        if (rect == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        int safeAmount = Math.max(0, amount);
        return new ModernMainLayout.Rect(rect.x + safeAmount, rect.y + safeAmount,
                Math.max(1, rect.width - safeAmount * 2), Math.max(1, rect.height - safeAmount * 2));
    }

    private void addStep() {
        if (!isEditableSequence()) {
            status("gui.modern.path.wb.u102");
            return;
        }
        pushHistory("add-step");
        selectedSequence.getSteps().add(new PathStep(captureCurrentCoordinates()));
        selectedStepIndex = selectedSequence.getSteps().size() - 1;
        selectedActionIndex = -1;
        selectedStepIndices.clear();
        selectedStepIndices.add(Integer.valueOf(selectedStepIndex));
        selectionAnchorStepIndex = selectedStepIndex;
        selectedActionIndices.clear();
        listFocus = ListFocus.STEP;
        refreshSelection();
        dirty = true;
        status("gui.modern.path.wb.u103");
    }

    private void insertRecordingStep() {
        if (auxiliaryView != AuxiliaryView.RECORDING || recordingDraft == null) {
            return;
        }
        pushHistory("insert-recording-step");
        int insertIndex = selectedStepIndex < 0 ? recordingDraft.getSteps().size()
                : Math.min(recordingDraft.getSteps().size(), selectedStepIndex + 1);
        recordingDraft.getSteps().add(insertIndex, new PathStep(captureCurrentCoordinates()));
        selectedStepIndex = insertIndex;
        selectedActionIndex = -1;
        selectedStep = recordingDraft.getSteps().get(insertIndex);
        selectedAction = null;
        selectedStepIndices.clear();
        selectedStepIndices.add(Integer.valueOf(insertIndex));
        selectedActionIndices.clear();
        listFocus = ListFocus.STEP;
        recordingDraftStepCount = recordingDraft.getSteps().size();
        dirty = true;
        status("gui.modern.path.wb.u104");
    }

    private void copyStep() {
        copySelectedStepsToClipboard();
    }

    private void deleteStep() {
        if (!isEditableSequence() || selectedSequence == null) {
            return;
        }
        List<Integer> indices = selectedStepIndexList();
        if (indices.isEmpty()) {
            return;
        }
        pushHistory("delete-step");
        int next = indices.get(0).intValue();
        for (int i = indices.size() - 1; i >= 0; i--) {
            int index = indices.get(i).intValue();
            if (index < 0 || index >= selectedSequence.getSteps().size()) {
                continue;
            }
            removePersistentActions(selectedSequence.getSteps().get(index));
            selectedSequence.getSteps().remove(index);
        }
        if (selectedSequence.getSteps().isEmpty()) {
            selectedStepIndex = -1;
            selectedStepIndices.clear();
            selectedActionIndex = -1;
            selectedActionIndices.clear();
        } else {
            selectStep(Math.min(next, selectedSequence.getSteps().size() - 1));
        }
        refreshSelection();
        dirty = true;
        status(indices.size() > 1 ? tr("gui.modern.path.wb.fmt.deleted_steps", String.valueOf(indices.size())) : "gui.modern.path.wb.u105");
    }

    private void moveStep(int direction) {
        if (!isEditableSequence() || selectedSequence == null) {
            return;
        }
        List<Integer> block = consecutiveSelectedSteps();
        if (block == null || block.isEmpty()) {
            status("gui.modern.path.wb.u106");
            return;
        }
        int start = block.get(0).intValue();
        int count = block.size();
        int target = start + (direction < 0 ? 0 : count + 1);
        if (direction < 0) {
            target = start - 1;
            if (target < 0) {
                return;
            }
        } else if (start + count >= selectedSequence.getSteps().size()) {
            return;
        }
        int movedTo = moveBlock(selectedSequence.getSteps(), start, count, target,
                direction < 0 ? "move-step-up" : "move-step-down");
        if (movedTo < 0) {
            return;
        }
        selectStepRange(movedTo, count);
        refreshSelection();
        dirty = true;
    }

    private void captureStepCoordinates() {
        if (!canEditStep() || minecraft == null || minecraft.player == null) {
            status("gui.modern.path.wb.u107");
            return;
        }
        pushHistory("capture-coordinates");
        selectedStep.setGotoPoint(captureCurrentCoordinates());
        bindEditorFields();
        dirty = true;
        status("gui.modern.path.wb.u108");
    }

    private void runSelected(boolean fromStep) {
        runSelectedFromStep(fromStep ? selectedStepIndex : -1);
    }

    private void runSelectedFromStep(int startStepIndex) {
        if (selectedSequence == null) {
            status("gui.modern.path.wb.u109");
            return;
        }
        syncEditorFields();
        List<Issue> issues = PathConfigValidator.validateSequences(sequences);
        if (countErrors(issues) > 0) {
            validationIssues = new ArrayList<>(issues);
            auxiliaryView = AuxiliaryView.VALIDATION;
            clearFocus();
            status("gui.modern.path.wb.u110");
            return;
        }
        if (dirty) {
            commitDraft();
        }
        PathSequence live = PathSequenceManager.getSequence(selectedSequence.getName());
        if (live == null || live.getSteps().isEmpty()) {
            status("gui.modern.path.wb.u111");
            return;
        }
        if (startStepIndex >= 0) {
            if (startStepIndex >= live.getSteps().size()) {
                status("gui.modern.path.wb.u112");
                return;
            }
            PathSequenceManager.runPathSequenceFromStep(live.getName(), startStepIndex);
            status(tr("gui.modern.path.wb.fmt.run_from", String.valueOf(startStepIndex + 1)));
        } else {
            PathSequenceManager.runPathSequence(live.getName());
            status("gui.modern.path.wb.u113");
        }
    }

    private double[] captureCurrentCoordinates() {
        if (minecraft == null || minecraft.player == null) {
            return new double[] { Double.NaN, Double.NaN, Double.NaN };
        }
        EntityPlayerSP player = minecraft.player;
        return new double[] { round(player.posX), round(player.posY), round(player.posZ) };
    }

    private void openStepTargetPicker() {
        stepTargetPickerOpen = true;
        stepSettingsPolicyOpen = false;
        stepTargetNavigationScroll = 0;
        stepTargetDetailsScroll = 0;
        if (findSequence(stepSettingsTargetSequence) == null) {
            PathSequence first = firstVisibleTargetSequence();
            stepSettingsTargetSequence = first == null ? "" : safe(first.getName());
            stepSettingsTargetStepIndex = 0;
            stepSettingsTargetActionIndex = 0;
        }
        clearFocus();
    }

    private PathSequence firstVisibleTargetSequence() {
        for (PathSequence sequence : sequences) {
            if (sequence != null && !safe(sequence.getName()).trim().isEmpty()) {
                return sequence;
            }
        }
        return null;
    }

    private boolean handleStepTargetPickerClick(int mouseX, int mouseY) {
        if (beginHoverScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (stepTargetCancelBounds != null && stepTargetCancelBounds.contains(mouseX, mouseY)) {
            stepTargetPickerOpen = false;
            return true;
        }
        for (StepTargetSequenceHit hit : stepTargetSequenceHits) {
            if (stepTargetNavigationBounds != null && stepTargetNavigationBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                stepSettingsTargetSequence = safe(hit.sequence.getName());
                stepSettingsTargetStepIndex = 0;
                stepSettingsTargetActionIndex = 0;
                stepTargetDetailsScroll = 0;
                return true;
            }
        }
        for (StepTargetActionHit hit : stepTargetActionHits) {
            if (stepTargetDetailsBounds != null && stepTargetDetailsBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                stepSettingsTargetStepIndex = hit.stepIndex;
                stepSettingsTargetActionIndex = hit.actionIndex;
                stepTargetPickerOpen = false;
                return true;
            }
        }
        if (stepTargetPickerBounds == null || !stepTargetPickerBounds.contains(mouseX, mouseY)) {
            stepTargetPickerOpen = false;
        }
        return true;
    }

    private void openPalette(int mouseX, int mouseY) {
        recordingReplacementTarget = null;
        clearFocus();
        paletteBounds = placePopup(Math.min(460, Math.max(1, bounds.width - 16)),
                Math.min(360, Math.max(1, bounds.height - 16)), mouseX, mouseY);
        paletteScroll = 0;
        setFieldTextIfUnchanged("palette.search", "");
    }

    private void closePalette() {
        recordingReplacementTarget = null;
        paletteBounds = null;
        paletteSearchBounds = null;
        if ("palette.search".equals(focusedFieldKey)) {
            clearFocus();
        }
    }

    private void applyActionType() {
        if (!canEditStep() || selectedAction == null) {
            return;
        }
        ModernTextField field = fields.get("action.type");
        String type = field == null ? "" : safe(field.getText()).trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty()) {
            status("gui.modern.path.wb.u114");
            return;
        }
        if (type.equalsIgnoreCase(safe(selectedAction.type))) {
            return;
        }
        pushHistory("change-action-type");
        selectedAction.type = type;
        selectedAction.params = defaultParams(type);
        bindEditorFields();
        dirty = true;
        status("gui.modern.path.wb.u115");
    }

    private void addActionByType(String type) {
        if (!canEditStep()) {
            return;
        }
        rememberRecentActionType(type);
        if (auxiliaryView == AuxiliaryView.RECORDING && recordingReplacementTarget != null) {
            int index = selectedStep.getActions().indexOf(recordingReplacementTarget);
            if (index < 0) return;
            pushHistory("replace-recording-action");
            selectedStep.getActions().set(index, new ActionData(type, defaultParams(type)));
            selectAction(index);
            parameterScroll = 0;
            status("gui.modern.path.record.replaced");
            return;
        }
        pushHistory("add-action");
        ActionData action = new ActionData(type, defaultParams(type));
        int insertionIndex = auxiliaryView == AuxiliaryView.RECORDING && selectedActionIndex >= 0
                ? Math.min(selectedStep.getActions().size(), selectedActionIndex + 1)
                : selectedStep.getActions().size();
        selectedStep.getActions().add(insertionIndex, action);
        selectedActionIndex = insertionIndex;
        selectedAction = action;
        selectedActionIndices.clear();
        selectedActionIndices.add(Integer.valueOf(selectedActionIndex));
        selectionAnchorActionIndex = selectedActionIndex;
        listFocus = ListFocus.ACTION;
        parameterScroll = 0;
        bindEditorFields();
        dirty = true;
        status(tr("gui.modern.path.wb.fmt.added_action", prettyType(type)));
    }

    private void rememberRecentActionType(String type) {
        String normalized = safe(type).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return;
        }
        recentActionTypes.remove(normalized);
        recentActionTypes.add(0, normalized);
        while (recentActionTypes.size() > 50) {
            recentActionTypes.remove(recentActionTypes.size() - 1);
        }
        actionPageRenderedRecentLimit = -1;
    }

    private void locateSelectedAction() {
        if (selectedAction == null) {
            status("gui.modern.path.wb.u116");
            return;
        }
        for (int i = 0; i < actionPageRows.size(); i++) {
            ActionLibraryNode node = actionPageRows.get(i).node;
            if (node != null && node.actionType != null
                    && node.actionType.equalsIgnoreCase(selectedAction.type)) {
                int visible = Math.max(1, (actionPageLibraryBounds.height - 64) / 25);
                actionPageScroll = clamp(i - visible / 2, 0, paletteMaxScroll);
        status(tr("gui.modern.path.wb.u117") + safe(node.label) + "”");
                return;
            }
        }
        status("gui.modern.path.wb.u118");
    }

    private void copyAction() {
        copySelectedActionsToClipboard();
    }

    private void deleteAction() {
        if (!canEditStep() || selectedStep == null) {
            return;
        }
        List<Integer> indices = selectedActionIndexList();
        if (indices.isEmpty()) {
            return;
        }
        pushHistory("delete-action");
        int next = indices.get(0).intValue();
        for (int i = indices.size() - 1; i >= 0; i--) {
            int index = indices.get(i).intValue();
            if (index < 0 || index >= selectedStep.getActions().size()) {
                continue;
            }
            ActionData deleting = selectedStep.getActions().get(index);
            if (deleting != null) {
                removedPersistentActions.add(deleting);
            }
            selectedStep.getActions().remove(index);
        }
        if (selectedStep.getActions().isEmpty()) {
            selectedActionIndex = -1;
            selectedActionIndices.clear();
            selectedAction = null;
        } else {
            selectAction(Math.min(next, selectedStep.getActions().size() - 1));
        }
        refreshSelection();
        dirty = true;
        status(indices.size() > 1 ? tr("gui.modern.path.wb.fmt.deleted_actions", String.valueOf(indices.size())) : "gui.modern.path.wb.u119");
    }

    private void moveAction(int direction) {
        if (!canEditStep() || selectedStep == null) {
            return;
        }
        List<Integer> block = consecutiveSelectedActions();
        if (block == null || block.isEmpty()) {
            status("gui.modern.path.wb.u120");
            return;
        }
        int start = block.get(0).intValue();
        int count = block.size();
        int target = direction < 0 ? start - 1 : start + count + 1;
        if (direction < 0 && start <= 0) {
            return;
        }
        if (direction > 0 && start + count >= selectedStep.getActions().size()) {
            return;
        }
        int movedTo = moveBlock(selectedStep.getActions(), start, count, target,
                direction < 0 ? "move-action-up" : "move-action-down");
        if (movedTo < 0) {
            return;
        }
        selectActionRange(movedTo, count);
        refreshSelection();
        dirty = true;
    }

    private void toggleSequenceSetting(ModernMainLayout.Rect rect) {
        if (!isEditableSequence() || rect == null) {
            return;
        }
        if (rect == sequenceToggleCloseBounds) {
            pushHistory("toggle-close-gui");
            selectedSequence.setCloseGuiAfterStart(!selectedSequence.shouldCloseGuiAfterStart());
        } else if (rect == sequenceToggleSingleBounds) {
            pushHistory("toggle-single-execution");
            selectedSequence.setSingleExecution(!selectedSequence.isSingleExecution());
        } else if (rect == sequenceToggleBackgroundBounds) {
            pushHistory("toggle-background");
            selectedSequence.setNonInterruptingExecution(!selectedSequence.isNonInterruptingExecution());
        } else if (rect == sequenceToggleLockBounds) {
            pushHistory("toggle-lock-policy");
            selectedSequence.setLockConflictPolicy(nextLockPolicy(selectedSequence.getLockConflictPolicy()));
        }
        dirty = true;
    }

    private String nextLockPolicy(String current) {
        if ("WAIT".equalsIgnoreCase(current)) {
            return "FAIL";
        }
        if ("FAIL".equalsIgnoreCase(current)) {
            return "PREEMPT_BACKGROUND";
        }
        return "WAIT";
    }

    private String lockPolicyLabel() {
        if (selectedSequence == null) {
            return "gui.modern.path.wb.u121";
        }
        String policy = selectedSequence.getLockConflictPolicy();
        if ("FAIL".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u122";
        }
        if ("PREEMPT_BACKGROUND".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u123";
        }
        return "gui.modern.path.wb.u124";
    }

    private String lockPolicyTooltip() {
        String policy = selectedSequence == null ? "" : selectedSequence.getLockConflictPolicy();
        if ("FAIL".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u125";
        }
        if ("PREEMPT_BACKGROUND".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u126";
        }
        return "gui.modern.path.wb.u127";
    }

    private String policyLabel(String policy) {
        if ("RESTART_SEQUENCE".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u128";
        }
        if ("RUN_SEQUENCE".equalsIgnoreCase(policy)) {
            return "gui.modern.path.wb.u129";
        }
        return "gui.modern.path.wb.u130";
    }

    private String stepTargetLabel() {
        if (safe(stepSettingsTargetSequence).trim().isEmpty()) {
            return "gui.modern.path.wb.u131";
        }
        return tr("gui.modern.path.wb.fmt.step_action", stepSettingsTargetSequence,
                String.valueOf(stepSettingsTargetStepIndex + 1), String.valueOf(stepSettingsTargetActionIndex + 1));
    }

    private void openMainMenu(int mouseX, int mouseY) {
        menuTitle = "gui.modern.path.wb.u132";
        menuItems.clear();
        for (PathWorkbenchToolCatalog.ToolId tool : PathWorkbenchToolCatalog.get()
                .all(PathWorkbenchToolCatalog.Zone.HEADER)) {
            menuItems.add(new RectAction(tool.label, "", true, false, () -> runTool(tool), tool, tool.description));
        }
        setMenuAnchor(mouseX, mouseY);
    }

    // These tools open as their own modern tabs instead of auxiliary views, so
    // the current workbench keeps its state while the tool stays available.
    private void openExecutionLogRoute() {
        closeMenu();
        clearFocus();
        requestNativeRoute("execution_log");
    }

    private void openActionTemplatesRoute() {
        closeMenu();
        clearFocus();
        requestNativeRoute("action_templates");
    }

    private void openActionVariablesRoute() {
        closeMenu();
        clearFocus();
        requestNativeRoute("action_variables");
    }

    private void openCategoryMenu(int mouseX, int mouseY) {
        menuTitle = "gui.modern.path.wb.u133";
        menuItems.clear();
        boolean categorySelected = !selectedCategory.isEmpty() && !isProtectedCategory(selectedCategory);
        boolean subSelected = !selectedCategory.isEmpty() && !selectedSubCategory.isEmpty();
        menuItems.add(new RectAction("gui.modern.path.wb.u134", categorySelected && selectedSubCategory.isEmpty(), false,
                () -> beginModal(ModalMode.RENAME_CATEGORY, "gui.modern.path.wb.u135", selectedCategory)));
        menuItems.add(new RectAction("gui.modern.path.wb.u136", categorySelected && selectedSubCategory.isEmpty(), true,
                () -> beginModal(ModalMode.DELETE_CATEGORY, "gui.modern.path.wb.u137", selectedCategory)));
        menuItems.add(new RectAction("gui.modern.path.wb.u138", categorySelected && selectedSubCategory.isEmpty(), false,
                this::toggleSelectedCategoryHidden));
        menuItems.add(new RectAction("gui.modern.path.wb.u139", subSelected, false,
                () -> beginModal(ModalMode.RENAME_SUBCATEGORY, "gui.modern.path.wb.u140", selectedSubCategory)));
        menuItems.add(new RectAction("gui.modern.path.wb.u141", subSelected, true,
                () -> beginModal(ModalMode.DELETE_SUBCATEGORY, "gui.modern.path.wb.u142", selectedSubCategory)));
        menuItems.add(new RectAction("gui.modern.path.wb.u143", true, false, () -> {
            pushHistory("sort-categories");
            sortCategories();
            dirty = true;
        }));
        setMenuAnchor(mouseX, mouseY);
    }

    private void openSequenceMenu(int mouseX, int mouseY) {
        openOverflowMenu(PathWorkbenchToolCatalog.Zone.NAVIGATION, mouseX, mouseY);
    }

    private void openActionMenu(int mouseX, int mouseY) {
        openOverflowMenu(PathWorkbenchToolCatalog.Zone.ACTION, mouseX, mouseY);
    }

    private void beginModal(ModalMode mode, String title, String message) {
        closeMenu();
        closePalette();
        clearFocus();
        modalAnchorX = lastMouseX;
        modalAnchorY = lastMouseY;
        modalMode = mode;
        modalTitle = safe(title);
        modalMessage = safe(message);
        setFieldText("modal.main", modalDefaultMain(mode));
        setFieldText("modal.secondary", modalDefaultSecondary(mode));
        if (mode == ModalMode.MOVE_SEQUENCE) {
            moveTargetCategory = selectedCategory;
            moveTargetSubCategory = selectedSubCategory;
            movePickerQuery = "";
            moveCategoriesScroll = moveSubCategoriesScroll = 0;
            movePickerHits.clear();
            modalSecondaryBounds = null;
            setFieldText("modal.main", "");
        }
        ModernTextField main = fields.get("modal.main");
        if (main != null && modalNeedsInput()) {
            main.setCanLoseFocus(false);
            main.setFocused(true);
            focusedFieldKey = "modal.main";
        }
    }

    private void setFieldText(String key, String value) {
        ModernTextField field = fields.get(key);
        if (field != null) {
            field.setText(value == null ? "" : value);
        }
    }

    private boolean isNoteModal() {
        return modalMode == ModalMode.STEP_NOTE || modalMode == ModalMode.SEQUENCE_NOTE;
    }

    private String modalDefaultMain(ModalMode mode) {
        if (mode == ModalMode.SEQUENCE_NOTE && selectedSequence != null) {
            return safe(selectedSequence.getNote());
        }
        if (mode == ModalMode.STEP_NOTE && selectedSequence != null && modalStepIndex >= 0
                && modalStepIndex < selectedSequence.getSteps().size()) {
            return safe(selectedSequence.getSteps().get(modalStepIndex).getNote());
        }
        if (mode == ModalMode.RUN_FROM_STEP) {
            return String.valueOf(selectedStepIndex >= 0 ? selectedStepIndex + 1 : 1);
        }
        if (mode == ModalMode.STEP_SETTINGS && selectedStep != null) {
            return String.valueOf(selectedStep.getRetryCount());
        }
        if (mode == ModalMode.BUILTIN_DELAY_SETTINGS) {
            return PathSequenceEventListener.isBuiltinSequenceDelayEnabled()
                    ? PathSequenceEventListener.getBuiltinSequenceDelayTicksSpec()
                    : "0";
        }
        if (mode == ModalMode.RENAME_SEQUENCE && selectedSequence != null) {
            return safe(selectedSequence.getName());
        }
        if (mode == ModalMode.RENAME_CATEGORY) {
            return selectedCategory;
        }
        if (mode == ModalMode.RENAME_SUBCATEGORY) {
            return selectedSubCategory;
        }
        if (mode == ModalMode.MOVE_SEQUENCE) {
            return selectedCategory;
        }
        return "";
    }

    private String modalDefaultSecondary(ModalMode mode) {
        if (mode == ModalMode.STEP_SETTINGS && selectedStep != null) {
            return String.valueOf(selectedStep.getPathRetryTimeoutSeconds());
        }
        return mode == ModalMode.MOVE_SEQUENCE ? selectedSubCategory : "";
    }

    private void openStepSettings() {
        if (!canEditStep()) {
            return;
        }
        clearFocus();
        modalAnchorX = lastMouseX;
        modalAnchorY = lastMouseY;
        modalMode = ModalMode.STEP_SETTINGS;
        modalTitle = "gui.modern.path.wb.u144";
        modalMessage = "gui.modern.path.wb.u145";
        setFieldTextIfUnchanged("step.settings.retry", String.valueOf(selectedStep.getRetryCount()));
        setFieldTextIfUnchanged("step.settings.timeout", String.valueOf(selectedStep.getPathRetryTimeoutSeconds()));
        setFieldTextIfUnchanged("step.settings.tolerance", String.valueOf(selectedStep.getArrivalToleranceBlocks()));
        stepSettingsPolicy = selectedStep.getRetryExhaustedPolicy();
        stepSettingsTargetSequence = safe(selectedStep.getRetryExhaustedSequenceName());
        stepSettingsTargetStepIndex = selectedStep.getRetryExhaustedStepIndex();
        stepSettingsTargetActionIndex = selectedStep.getRetryExhaustedActionIndex();
        stepSettingsPolicyOpen = false;
        stepTargetPickerOpen = false;
        stepTargetNavigationScroll = 0;
        stepTargetDetailsScroll = 0;
    }

    private void openBuiltinDelaySettings() {
        beginModal(ModalMode.BUILTIN_DELAY_SETTINGS, "gui.modern.path.tool.u047",
                "gui.modern.path.tool.u049");
    }

    private boolean applyBuiltinDelaySettings(String rawSpec) {
        String spec = safe(rawSpec).trim();
        if (spec.isEmpty() || !TickRangeSpec.isValid(spec)) {
            status("gui.modern.path.tool.u050");
            return false;
        }
        String normalized = PathSequenceEventListener.normalizeBuiltinSequenceDelayTicksSpec(spec);
        if (normalized == null || normalized.trim().isEmpty()) {
            status("gui.modern.path.tool.u050");
            return false;
        }
        boolean enabled = !"0".equals(normalized.trim());
        PathSequenceEventListener.updateBuiltinSequenceDelayConfig(enabled, normalized);
        status("gui.modern.path.tool.u051");
        return true;
    }

    private void openStepNoteModal(int stepIndex) {
        if (!isEditableSequence() || selectedSequence == null || stepIndex < 0
                || stepIndex >= selectedSequence.getSteps().size()) {
            return;
        }
        modalStepIndex = stepIndex;
        beginModal(ModalMode.STEP_NOTE, "gui.modern.path.wb.u146", tr("gui.modern.path.wb.fmt.step_note_prompt", String.valueOf(stepIndex + 1)));
    }

    private void openSequenceNoteModal() {
        if (!isEditableSequence() || selectedSequence == null) {
            return;
        }
        modalStepIndex = -1;
        beginModal(ModalMode.SEQUENCE_NOTE, "gui.modern.path.wb.u096", "gui.modern.path.wb.u147");
    }

    private void openRunFromStepModal() {
        if (selectedSequence == null || selectedSequence.getSteps().isEmpty()) {
            status("gui.modern.path.wb.u111");
            return;
        }
        modalStepIndex = -1;
        beginModal(ModalMode.RUN_FROM_STEP, "gui.modern.path.wb.u148",
                tr("gui.modern.path.wb.fmt.step_index_range", String.valueOf(selectedSequence.getSteps().size())));
    }

    private void saveStepNote() {
        if (!isEditableSequence() || selectedSequence == null || modalStepIndex < 0
                || modalStepIndex >= selectedSequence.getSteps().size()) {
            return;
        }
        ModernTextField field = fields.get("modal.main");
        String note = field == null ? "" : safe(field.getText());
        pushHistory("step-note");
        selectedSequence.getSteps().get(modalStepIndex).setNote(note);
        setFieldText("step.note", note);
        dirty = true;
        status("gui.modern.path.wb.u149");
    }

    private void saveSequenceNote() {
        if (!isEditableSequence() || selectedSequence == null) {
            return;
        }
        ModernTextField field = fields.get("modal.main");
        String note = field == null ? "" : safe(field.getText());
        pushHistory("sequence-note");
        selectedSequence.setNote(note);
        setFieldText("sequence.note", note);
        dirty = true;
        status("gui.modern.path.wb.u150");
    }

    private boolean runFromModalStep() {
        ModernTextField field = fields.get("modal.main");
        int displayIndex;
        try {
            displayIndex = Integer.parseInt(field == null ? "" : field.getText().trim());
        } catch (NumberFormatException ignored) {
            status("gui.modern.path.wb.u151");
            return false;
        }
        if (selectedSequence == null || displayIndex < 1 || displayIndex > selectedSequence.getSteps().size()) {
            status(tr("gui.modern.path.wb.fmt.step_index_between", String.valueOf(selectedSequence == null ? 0 : selectedSequence.getSteps().size())));
            return false;
        }
        runSelectedFromStep(displayIndex - 1);
        return true;
    }

    private boolean applyStepSettings() {
        if (!canEditStep() || selectedStep == null) {
            return false;
        }
        int retry = parseNonNegative("step.settings.retry", selectedStep.getRetryCount());
        int timeout = parseNonNegative("step.settings.timeout", selectedStep.getPathRetryTimeoutSeconds());
        int tolerance = parseNonNegative("step.settings.tolerance", selectedStep.getArrivalToleranceBlocks());
        if (retry < 0 || timeout < 0 || tolerance < 0) {
            status("gui.modern.path.wb.u152");
            return false;
        }
        if ("RUN_SEQUENCE".equalsIgnoreCase(stepSettingsPolicy)) {
            PathSequence target = findSequence(stepSettingsTargetSequence);
            if (target == null || target.getSteps().isEmpty()) {
                status("gui.modern.path.wb.u153");
                return false;
            }
            if (stepSettingsTargetStepIndex < 0 || stepSettingsTargetStepIndex >= target.getSteps().size()) {
                status("gui.modern.path.wb.u154");
                return false;
            }
            PathStep targetStep = target.getSteps().get(stepSettingsTargetStepIndex);
            int actionCount = targetStep == null ? 0 : targetStep.getActions().size();
            if (actionCount > 0 && (stepSettingsTargetActionIndex < 0
                    || stepSettingsTargetActionIndex >= actionCount)) {
                status("gui.modern.path.wb.u155");
                return false;
            }
        }
        pushHistory("step-settings");
        selectedStep.setRetryCount(retry);
        selectedStep.setPathRetryTimeoutSeconds(timeout);
        selectedStep.setArrivalToleranceBlocks(tolerance);
        selectedStep.setRetryExhaustedPolicy(stepSettingsPolicy);
        if ("RUN_SEQUENCE".equalsIgnoreCase(stepSettingsPolicy)) {
            selectedStep.setRetryExhaustedSequenceName(stepSettingsTargetSequence);
            selectedStep.setRetryExhaustedStepIndex(stepSettingsTargetStepIndex);
            selectedStep.setRetryExhaustedActionIndex(stepSettingsTargetActionIndex);
        } else {
            selectedStep.setRetryExhaustedSequenceName("");
            selectedStep.setRetryExhaustedStepIndex(0);
            selectedStep.setRetryExhaustedActionIndex(0);
        }
        dirty = true;
        status("gui.modern.path.wb.u156");
        return true;
    }

    private int parseNonNegative(String key, int fallback) {
        ModernTextField field = fields.get(key);
        try {
            int value = Integer.parseInt(field == null ? "" : field.getText().trim());
            return value < 0 ? -1 : value;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void closeModal() {
        modalMode = ModalMode.NONE;
        modalStepIndex = -1;
        modalBounds = null;
        modalMainBounds = null;
        modalSecondaryBounds = null;
        modalConfirmBounds = null;
        modalCancelBounds = null;
        stepSettingsModalBounds = null;
        stepSettingsPolicyBounds = null;
        stepSettingsTargetBounds = null;
        stepSettingsConfirmBounds = null;
        stepSettingsCancelBounds = null;
        stepSettingsPolicyOptions.clear();
        stepTargetSequenceHits.clear();
        stepTargetActionHits.clear();
        stepTargetPickerBounds = null;
        stepTargetPickerOpen = false;
        stepSettingsPolicyOpen = false;
        clearFocus();
    }

    private void applyModal() {
        ModernTextField mainField = fields.get("modal.main");
        ModernTextField secondaryField = fields.get("modal.secondary");
        String main = mainField == null ? "" : safe(mainField.getText()).trim();
        String secondary = secondaryField == null ? "" : safe(secondaryField.getText()).trim();
        ModalMode mode = modalMode;
        if (mode == ModalMode.ADD_SEQUENCE) {
            addSequence(main);
        } else if (mode == ModalMode.RENAME_SEQUENCE) {
            renameSelectedSequence(main);
        } else if (mode == ModalMode.MOVE_SEQUENCE) {
            if (!canMoveToPickedGroup()) {
                return;
            }
            moveSelectedSequence(moveTargetCategory, moveTargetSubCategory);
        } else if (mode == ModalMode.DELETE_SEQUENCE) {
            deleteSelectedSequence();
        } else if (mode == ModalMode.ADD_CATEGORY) {
            addCategory(main);
        } else if (mode == ModalMode.RENAME_CATEGORY) {
            renameCategory(main);
        } else if (mode == ModalMode.DELETE_CATEGORY) {
            deleteCategory();
        } else if (mode == ModalMode.ADD_SUBCATEGORY) {
            addSubCategory(main);
        } else if (mode == ModalMode.RENAME_SUBCATEGORY) {
            renameSubCategory(main);
        } else if (mode == ModalMode.DELETE_SUBCATEGORY) {
            deleteSubCategory();
        } else if (mode == ModalMode.STEP_SETTINGS) {
            if (!applyStepSettings()) {
                return;
            }
        } else if (mode == ModalMode.BUILTIN_DELAY_SETTINGS) {
            if (!applyBuiltinDelaySettings(main)) {
                return;
            }
        } else if (mode == ModalMode.STEP_NOTE) {
            saveStepNote();
        } else if (mode == ModalMode.SEQUENCE_NOTE) {
            saveSequenceNote();
        } else if (mode == ModalMode.RUN_FROM_STEP) {
            if (!runFromModalStep()) {
                return;
            }
        }
        closeModal();
    }

    private boolean handleModalClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (modalMode == ModalMode.MOVE_SEQUENCE) {
            for (NavHit hit : movePickerHits) {
                ModernMainLayout.Rect pane = hit.kind == NavKind.CATEGORY ? moveCategoriesBounds : moveSubCategoriesBounds;
                if (pane != null && pane.contains(mouseX, mouseY) && hit.bounds.contains(mouseX, mouseY)) {
                    if (hit.kind == NavKind.CATEGORY) {
                        if (!moveTargetCategory.equals(hit.category)) {
                            moveTargetCategory = hit.category;
                            moveTargetSubCategory = "";
                            moveSubCategoriesScroll = 0;
                        }
                    } else {
                        moveTargetSubCategory = hit.subCategory;
                    }
                    movePickerHits.clear();
                    return true;
                }
            }
        }
        if (modalMode == ModalMode.STEP_SETTINGS) {
            if (stepTargetPickerOpen) {
                return handleStepTargetPickerClick(mouseX, mouseY);
            }
            if (stepSettingsPolicyOpen) {
                for (RectAction option : stepSettingsPolicyOptions) {
                    if (option.bounds != null && option.bounds.contains(mouseX, mouseY)) {
                        option.action.run();
                        stepSettingsPolicyOpen = false;
                        return true;
                    }
                }
                stepSettingsPolicyOpen = false;
                return true;
            }
            String settingsField = fieldAt(mouseX, mouseY);
            if (settingsField != null && settingsField.startsWith("step.settings.")) {
                focusField(settingsField, mouseX, mouseY, mouseButton);
                return true;
            }
            if (stepSettingsPolicyBounds != null && stepSettingsPolicyBounds.contains(mouseX, mouseY)) {
                stepSettingsPolicyOpen = !stepSettingsPolicyOpen;
                clearFocus();
                return true;
            }
            if (stepSettingsTargetBounds != null && stepSettingsTargetBounds.contains(mouseX, mouseY)
                    && "RUN_SEQUENCE".equalsIgnoreCase(stepSettingsPolicy)) {
                openStepTargetPicker();
                return true;
            }
            if (stepSettingsConfirmBounds != null && stepSettingsConfirmBounds.contains(mouseX, mouseY)) {
                applyModal();
                return true;
            }
            if (stepSettingsCancelBounds != null && stepSettingsCancelBounds.contains(mouseX, mouseY)) {
                closeModal();
                return true;
            }
            if (stepSettingsModalBounds != null && !stepSettingsModalBounds.contains(mouseX, mouseY)) {
                closeModal();
                return true;
            }
        }
        if (isNoteModal() && handleNoteChipClick(modalNoteColorChips, modalNoteFormatChips,
                "modal.main", false, mouseX, mouseY)) {
            return true;
        }
        if (modalMainBounds != null && modalMainBounds.contains(mouseX, mouseY)) {
            focusField("modal.main", mouseX, mouseY, mouseButton);
            return true;
        }
        if (modalSecondaryBounds != null && modalSecondaryBounds.contains(mouseX, mouseY)) {
            focusField("modal.secondary", mouseX, mouseY, mouseButton);
            return true;
        }
        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null && ("modal.main".equals(fieldKey) || "modal.secondary".equals(fieldKey))) {
            focusField(fieldKey, mouseX, mouseY, mouseButton);
            return true;
        }
        if (modalCancelBounds != null && modalCancelBounds.contains(mouseX, mouseY)) {
            closeModal();
            return true;
        }
        if (modalConfirmBounds != null && modalConfirmBounds.contains(mouseX, mouseY)) {
            applyModal();
            return true;
        }
        if (modalBounds != null && !modalBounds.contains(mouseX, mouseY)) {
            closeModal();
            return true;
        }
        return true;
    }

    private boolean handleModalKey(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            closeModal();
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            applyModal();
            return true;
        }
        return typeFocusedField(typedChar, keyCode);
    }

    private void addSequence(String name) {
        if (name.isEmpty()) {
            status("gui.modern.path.wb.u157");
            return;
        }
        if (findSequence(name) != null) {
            status("gui.modern.path.wb.u158");
            return;
        }
        String category = selectedCategory.isEmpty() ? defaultCategory() : selectedCategory;
        pushHistory("add-sequence");
        PathSequence sequence = new PathSequence(name);
        sequence.setCustom(true);
        sequence.setCategory(category);
        sequence.setSubCategory(selectedSubCategory);
        sequences.add(0, sequence);
        addCategoryLocal(category);
        addSubCategoryLocal(category, selectedSubCategory);
        selectedCategory = category;
        selectedSequenceName = name;
        selectedStepIndex = -1;
        selectedActionIndex = -1;
        selectSequenceByName(name);
        dirty = true;
        status(tr("gui.modern.path.wb.fmt.added_seq", name));
    }

    private void copySelectedSequence() {
        if (selectedSequence == null) {
            status("gui.modern.path.wb.u109");
            return;
        }
        String base = tr("gui.modern.path.wb.fmt.copy", safe(selectedSequence.getName()));
        String name = uniqueSequenceName(base);
        pushHistory("copy-sequence");
        PathSequence copy = new PathSequence(selectedSequence);
        copy.setName(name);
        copy.setCustom(true);
        copy.setCategory(safe(selectedSequence.getCategory()).trim().isEmpty() ? defaultCategory()
                : selectedSequence.getCategory());
        copy.setSubCategory(selectedSequence.getSubCategory());
        sequences.add(0, copy);
        addCategoryLocal(copy.getCategory());
        addSubCategoryLocal(copy.getCategory(), copy.getSubCategory());
        selectedSequenceName = name;
        selectedStepIndex = copy.getSteps().isEmpty() ? -1 : 0;
        selectedActionIndex = -1;
        selectSequenceByName(name);
        dirty = true;
        status("gui.modern.path.wb.u159");
    }

    private void renameSelectedSequence(String name) {
        if (!isEditableSequence()) {
            return;
        }
        if (name.isEmpty() || findSequenceExcept(name, selectedSequence) != null) {
            status("gui.modern.path.wb.u160");
            return;
        }
        if (name.equals(selectedSequence.getName())) {
            return;
        }
        pushHistory("rename-sequence");
        selectedSequence.setName(name);
        selectedSequenceName = name;
        dirty = true;
        status("gui.modern.path.wb.u161");
    }

    private void moveSelectedSequence(String category, String subCategory) {
        if (!isEditableSequence() || category.isEmpty()) {
            status("gui.modern.path.wb.u162");
            return;
        }
        pushHistory("move-sequence");
        selectedSequence.setCategory(category);
        selectedSequence.setSubCategory(subCategory);
        addCategoryLocal(category);
        addSubCategoryLocal(category, subCategory);
        selectedCategory = category;
        selectedSubCategory = subCategory;
        dirty = true;
        status(subCategory.isEmpty() ? tr("gui.modern.path.wb.fmt.moved_to", category)
                : tr("gui.modern.path.wb.fmt.moved_to_sub", category, subCategory));
    }

    private void deleteSelectedSequence() {
        if (!isEditableSequence()) {
            return;
        }
        pushHistory("delete-sequence");
        removePersistentActions(selectedSequence);
        sequences.remove(selectedSequence);
        selectedSequence = null;
        selectedSequenceName = "";
        selectedStep = null;
        selectedAction = null;
        selectedStepIndex = -1;
        selectedActionIndex = -1;
        dirty = true;
        PathSequence next = visibleSequenceForScope(selectedCategory, selectedSubCategory);
        if (next == null) {
            next = visibleSequenceForScope(selectedCategory, "");
        }
        selectSequence(next);
        status("gui.modern.path.wb.u163");
    }

    private void moveSequence(int direction) {
        if (!isEditableSequence()) {
            return;
        }
        List<PathSequence> sameScope = sequencesForCategory(selectedSequence.getCategory(), selectedSequence.getSubCategory());
        int index = sameScope.indexOf(selectedSequence);
        int target = index + direction;
        if (index < 0 || target < 0 || target >= sameScope.size()) {
            return;
        }
        PathSequence anchor = sameScope.get(target);
        int globalIndex = sequences.indexOf(selectedSequence);
        int anchorIndex = sequences.indexOf(anchor);
        if (globalIndex < 0 || anchorIndex < 0) {
            return;
        }
        pushHistory(direction < 0 ? "move-sequence-up" : "move-sequence-down");
        Collections.swap(sequences, globalIndex, anchorIndex);
        dirty = true;
        status(direction < 0 ? "gui.modern.path.wb.u164" : "gui.modern.path.wb.u165");
    }

    private void addCategory(String category) {
        if (category.isEmpty()) {
            status("gui.modern.path.wb.u166");
            return;
        }
        if (findCategory(category) != null) {
            status("gui.modern.path.wb.u167");
            return;
        }
        pushHistory("add-category");
        categories.add(category);
        subCategories.put(category, new ArrayList<String>());
        hiddenCategories.put(category, false);
        selectedCategory = category;
        selectedSubCategory = "";
        selectSequence((PathSequence) null);
        dirty = true;
        status("gui.modern.path.wb.u168");
    }

    private void renameCategory(String name) {
        if (selectedCategory.isEmpty() || isProtectedCategory(selectedCategory) || name.isEmpty()
                || findCategoryExcept(name, selectedCategory) != null) {
            status("gui.modern.path.wb.u169");
            return;
        }
        if (name.equals(selectedCategory)) {
            return;
        }
        String old = selectedCategory;
        pushHistory("rename-category");
        int index = categories.indexOf(old);
        if (index >= 0) {
            categories.set(index, name);
        }
        List<String> subs = subCategories.remove(old);
        subCategories.put(name, subs == null ? new ArrayList<String>() : subs);
        Boolean hidden = hiddenCategories.remove(old);
        hiddenCategories.put(name, hidden == null ? false : hidden);
        for (PathSequence sequence : sequences) {
            if (sequence != null && old.equalsIgnoreCase(safe(sequence.getCategory()).trim())) {
                sequence.setCategory(name);
            }
        }
        selectedCategory = name;
        dirty = true;
        status("gui.modern.path.wb.u170");
    }

    private void deleteCategory() {
        if (selectedCategory.isEmpty() || isProtectedCategory(selectedCategory)) {
            status("gui.modern.path.wb.u171");
            return;
        }
        String removed = selectedCategory;
        pushHistory("delete-category");
        for (PathSequence sequence : new ArrayList<>(sequences)) {
            if (sequence != null && removed.equalsIgnoreCase(safe(sequence.getCategory()).trim())) {
                removePersistentActions(sequence);
                sequences.remove(sequence);
            }
        }
        categories.removeIf(category -> removed.equalsIgnoreCase(category));
        subCategories.remove(removed);
        hiddenCategories.remove(removed);
        selectedCategory = categories.isEmpty() ? defaultCategory() : categories.get(0);
        selectedSubCategory = "";
        selectSequence(visibleSequenceForScope(selectedCategory, ""));
        dirty = true;
        status("gui.modern.path.wb.u172");
    }

    private void addSubCategory(String subCategory) {
        if (selectedCategory.isEmpty() || subCategory.isEmpty()) {
            status("gui.modern.path.wb.u173");
            return;
        }
        if (containsIgnoreCase(subCategoriesFor(selectedCategory), subCategory)) {
            status("gui.modern.path.wb.u174");
            return;
        }
        pushHistory("add-subcategory");
        addSubCategoryLocal(selectedCategory, subCategory);
        selectedSubCategory = subCategory;
        dirty = true;
        status("gui.modern.path.wb.u175");
    }

    private void renameSubCategory(String subCategory) {
        if (selectedCategory.isEmpty() || selectedSubCategory.isEmpty() || subCategory.isEmpty()
                || containsIgnoreCase(subCategoriesFor(selectedCategory), subCategory)
                && !subCategory.equalsIgnoreCase(selectedSubCategory)) {
            status("gui.modern.path.wb.u176");
            return;
        }
        String old = selectedSubCategory;
        if (old.equals(subCategory)) {
            return;
        }
        pushHistory("rename-subcategory");
        List<String> list = subCategories.get(selectedCategory);
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                if (old.equalsIgnoreCase(list.get(i))) {
                    list.set(i, subCategory);
                    break;
                }
            }
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null && selectedCategory.equalsIgnoreCase(safe(sequence.getCategory()).trim())
                    && old.equalsIgnoreCase(safe(sequence.getSubCategory()).trim())) {
                sequence.setSubCategory(subCategory);
            }
        }
        selectedSubCategory = subCategory;
        dirty = true;
        status("gui.modern.path.wb.u177");
    }

    private void deleteSubCategory() {
        if (selectedCategory.isEmpty() || selectedSubCategory.isEmpty()) {
            return;
        }
        String removed = selectedSubCategory;
        pushHistory("delete-subcategory");
        List<String> list = subCategories.get(selectedCategory);
        if (list != null) {
            list.removeIf(item -> removed.equalsIgnoreCase(item));
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null && selectedCategory.equalsIgnoreCase(safe(sequence.getCategory()).trim())
                    && removed.equalsIgnoreCase(safe(sequence.getSubCategory()).trim())) {
                sequence.setSubCategory("");
            }
        }
        selectedSubCategory = "";
        dirty = true;
        status("gui.modern.path.wb.u178");
    }

    private void toggleSelectedCategoryHidden() {
        if (selectedCategory.isEmpty() || isProtectedCategory(selectedCategory)) {
            return;
        }
        boolean next = !Boolean.TRUE.equals(hiddenCategories.get(selectedCategory));
        hiddenCategories.put(selectedCategory, next);
        dirty = true;
        status(next ? "gui.modern.path.wb.u179" : "gui.modern.path.wb.u180");
    }

    private void sortCategories() {
        String defaultName = defaultCategory();
        String builtinName = builtinCategory();
        List<String> sorted = new ArrayList<>(categories);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        categories.clear();
        if (sorted.remove(defaultName)) {
            categories.add(defaultName);
        }
        if (sorted.remove(builtinName)) {
            categories.add(builtinName);
        }
        categories.addAll(sorted);
        sortCategoriesOnSave = true;
    }

    public void refreshAfterExternalSequenceChange() {
        if (initialized && !isDirty()) reloadDraft();
    }

    private void reloadDraft() {
        if (dirty || templateDirty) {
            status("gui.modern.path.wb.u181");
            return;
        }
        loadDraft();
        bindEditorFields();
        status("gui.modern.path.wb.u182");
    }

    private void commitDraft() {
        ensureStableActionIds();
        ActionEditorJson.canonicalizeMoveChestActions(sequences);
        commitCategoryChanges();
        PathSequenceManager.saveAllSequences(sequences);
        cleanupRemovedPersistentActions();
        originalSequences = copySequences(sequences);
        originalCategories = new ArrayList<>(categories);
        originalSubCategories = copySubCategories(subCategories);
        originalHiddenCategories = new LinkedHashMap<>(hiddenCategories);
        dirty = false;
        sortCategoriesOnSave = false;
        undoHistory.clear();
        redoHistory.clear();
        status("gui.modern.path.wb.u183");
    }

    private void ensureStableActionIds() {
        for (PathSequence sequence : sequences) {
            if (sequence == null) continue;
            for (PathStep step : sequence.getSteps()) {
                if (step == null) continue;
                for (ActionData action : step.getActions()) {
                    if (action == null || action.params == null) continue;
                    String type = safe(action.type).toLowerCase(Locale.ROOT);
                    if (!"window_click".equals(type) && !"run_sequence".equals(type)) continue;
                    String uuid = action.params.has("uuid") ? safe(action.params.get("uuid").getAsString()).trim() : "";
                    if (uuid.isEmpty()) action.params.addProperty("uuid", java.util.UUID.randomUUID().toString());
                }
            }
        }
    }

    private void commitCategoryChanges() {
        for (String oldCategory : originalCategories) {
            if (!containsIgnoreCase(categories, oldCategory) && !isProtectedCategory(oldCategory)) {
                PathSequenceManager.deleteCategory(oldCategory);
            }
        }
        for (String category : categories) {
            if (!containsIgnoreCase(originalCategories, category) && !isBuiltinCategory(category)) {
                PathSequenceManager.addCategory(category);
            }
        }
        Set<String> allCategoryKeys = new LinkedHashSet<>();
        allCategoryKeys.addAll(originalSubCategories.keySet());
        allCategoryKeys.addAll(subCategories.keySet());
        for (String category : allCategoryKeys) {
            List<String> oldSubs = originalSubCategories.get(category);
            List<String> newSubs = subCategories.get(category);
            if (oldSubs != null) {
                for (String oldSub : oldSubs) {
                    if (newSubs == null || !containsIgnoreCase(newSubs, oldSub)) {
                        MainUiLayoutManager.deleteSubCategory(category, oldSub, false);
                    }
                }
            }
            if (newSubs != null) {
                for (String newSub : newSubs) {
                    if (oldSubs == null || !containsIgnoreCase(oldSubs, newSub)) {
                        MainUiLayoutManager.addSubCategory(category, newSub);
                    }
                }
            }
        }
        for (String category : categories) {
            boolean hidden = Boolean.TRUE.equals(hiddenCategories.get(category));
            boolean originalHidden = Boolean.TRUE.equals(originalHiddenCategories.get(category));
            if (hidden != originalHidden) {
                PathSequenceManager.setCategoryHidden(category, hidden);
            }
        }
        if (sortCategoriesOnSave) {
            PathSequenceManager.sortCustomCategoriesAlphabetically();
        }
    }

    private void cleanupRemovedPersistentActions() {
        if (removedPersistentActions.isEmpty()) {
            return;
        }
        Set<String> active = new LinkedHashSet<>();
        for (PathSequence sequence : sequences) {
            if (sequence == null || sequence.getSteps() == null) {
                continue;
            }
            for (PathStep step : sequence.getSteps()) {
                if (step == null || step.getActions() == null) {
                    continue;
                }
                for (ActionData action : step.getActions()) {
                    String uuid = PathSequenceManager.getPersistentActionUuid(action);
                    if (!uuid.isEmpty()) {
                        active.add(uuid);
                    }
                }
            }
        }
        for (ActionData action : removedPersistentActions) {
            String uuid = PathSequenceManager.getPersistentActionUuid(action);
            if (!uuid.isEmpty() && !active.contains(uuid)) {
                PathSequenceManager.removePersistentActionRecord(action);
            }
        }
        removedPersistentActions.clear();
    }

    private void removePersistentActions(PathSequence sequence) {
        if (sequence == null || sequence.getSteps() == null) {
            return;
        }
        for (PathStep step : sequence.getSteps()) {
            removePersistentActions(step);
        }
    }

    private void removePersistentActions(PathStep step) {
        if (step == null || step.getActions() == null) {
            return;
        }
        for (ActionData action : step.getActions()) {
            if (action != null && !PathSequenceManager.getPersistentActionUuid(action).isEmpty()) {
                removedPersistentActions.add(action);
            }
        }
    }

    private void pushHistory(String reason) {
        if (restoringHistory) {
            return;
        }
        if (auxiliaryView == AuxiliaryView.RECORDING && recordingDraft != null) {
            recordingDraftManuallyEdited = true;
            recordingUndoHistory.add(new PathSequence(recordingDraft, true));
            while (recordingUndoHistory.size() > MAX_HISTORY) recordingUndoHistory.remove(0);
            recordingRedoHistory.clear();
            dirty = true;
            return;
        }
        undoHistory.add(captureHistory());
        while (undoHistory.size() > MAX_HISTORY) {
            undoHistory.remove(0);
        }
        redoHistory.clear();
        dirty = true;
    }

    private HistoryState captureHistory() {
        return new HistoryState(copySequences(sequences), new ArrayList<>(categories), copySubCategories(subCategories),
                new LinkedHashMap<>(hiddenCategories),
                selectedCategory, selectedSubCategory, selectedSequenceName, selectedStepIndex, selectedActionIndex,
                sortCategoriesOnSave);
    }

    private void undo() {
        if (auxiliaryView == AuxiliaryView.RECORDING && recordingDraft != null) {
            if (recordingUndoHistory.isEmpty()) {
                status("gui.modern.path.wb.u184");
                return;
            }
            recordingRedoHistory.add(new PathSequence(recordingDraft, true));
            restoreRecordingHistory(recordingUndoHistory.remove(recordingUndoHistory.size() - 1));
            status("gui.modern.path.wb.u185");
            return;
        }
        if (undoHistory.isEmpty()) {
            status("gui.modern.path.wb.u186");
            return;
        }
        redoHistory.add(captureHistory());
        HistoryState state = undoHistory.remove(undoHistory.size() - 1);
        restoreHistory(state);
        status("gui.modern.path.wb.u187");
    }

    private void redo() {
        if (auxiliaryView == AuxiliaryView.RECORDING && recordingDraft != null) {
            if (recordingRedoHistory.isEmpty()) {
                status("gui.modern.path.wb.u188");
                return;
            }
            recordingUndoHistory.add(new PathSequence(recordingDraft, true));
            restoreRecordingHistory(recordingRedoHistory.remove(recordingRedoHistory.size() - 1));
            status("gui.modern.path.wb.u189");
            return;
        }
        if (redoHistory.isEmpty()) {
            status("gui.modern.path.wb.u190");
            return;
        }
        undoHistory.add(captureHistory());
        HistoryState state = redoHistory.remove(redoHistory.size() - 1);
        restoreHistory(state);
        status("gui.modern.path.wb.u191");
    }

    private void restoreRecordingHistory(PathSequence state) {
        if (state == null) return;
        recordingDraft = new PathSequence(state, true);
        recordingDraftManuallyEdited = true;
        selectedSequence = recordingDraft;
        selectedSequenceName = recordingDraft.getName();
        refreshSelection();
    }

    private void restoreHistory(HistoryState state) {
        if (state == null) {
            return;
        }
        restoringHistory = true;
        try {
            sequences = copySequences(state.sequences);
            categories = new ArrayList<>(state.categories);
            subCategories = copySubCategories(state.subCategories);
            hiddenCategories = new LinkedHashMap<>(state.hiddenCategories);
            selectedCategory = safe(state.category);
            selectedSubCategory = safe(state.subCategory);
            selectedSequenceName = safe(state.sequenceName);
            selectedStepIndex = state.stepIndex;
            selectedActionIndex = state.actionIndex;
            sortCategoriesOnSave = state.sortCategoriesOnSave;
            refreshSelection();
            bindEditorFields();
            dirty = true;
        } finally {
            restoringHistory = false;
        }
    }

    private List<PathSequence> copySequences(List<PathSequence> source) {
        List<PathSequence> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (PathSequence sequence : source) {
            if (sequence == null) {
                continue;
            }
            try {
                copied.add(new PathSequence(sequence, true));
            } catch (Exception ignored) {
                // A malformed entry should not prevent the remaining paths from opening.
            }
        }
        return copied;
    }

    private Map<String, List<String>> copySubCategories(Map<String, List<String>> source) {
        Map<String, List<String>> copied = new LinkedHashMap<>();
        if (source == null) {
            return copied;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copied.put(entry.getKey(), entry.getValue() == null ? new ArrayList<String>()
                    : new ArrayList<>(entry.getValue()));
        }
        return copied;
    }

    private Map<String, List<String>> collectSubCategories(List<String> categoryNames, List<PathSequence> source) {
        Map<String, List<String>> collected = new LinkedHashMap<>();
        if (categoryNames != null) {
            for (String category : categoryNames) {
                if (category == null || category.trim().isEmpty()) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                try {
                    values.addAll(MainUiLayoutManager.getSubCategories(category));
                } catch (Exception ignored) {
                }
                collected.put(category, values);
            }
        }
        if (source != null) {
            for (PathSequence sequence : source) {
                if (sequence == null) {
                    continue;
                }
                String category = safe(sequence.getCategory()).trim();
                String sub = safe(sequence.getSubCategory()).trim();
                if (category.isEmpty()) {
                    category = defaultCategory();
                }
                if (!collected.containsKey(category)) {
                    collected.put(category, new ArrayList<String>());
                }
                if (!sub.isEmpty() && !containsIgnoreCase(collected.get(category), sub)) {
                    collected.get(category).add(sub);
                }
            }
        }
        return collected;
    }

    private void addCategoryLocal(String category) {
        String normalized = safe(category).trim();
        if (normalized.isEmpty()) {
            normalized = defaultCategory();
        }
        if (findCategory(normalized) == null) {
            categories.add(normalized);
        }
        if (!subCategories.containsKey(normalized)) {
            subCategories.put(normalized, new ArrayList<String>());
        }
        if (!hiddenCategories.containsKey(normalized)) {
            hiddenCategories.put(normalized, false);
        }
    }

    private void addSubCategoryLocal(String category, String subCategory) {
        String normalizedCategory = safe(category).trim();
        String normalizedSub = safe(subCategory).trim();
        if (normalizedCategory.isEmpty() || normalizedSub.isEmpty()) {
            return;
        }
        addCategoryLocal(normalizedCategory);
        List<String> list = subCategories.get(normalizedCategory);
        if (!containsIgnoreCase(list, normalizedSub)) {
            list.add(normalizedSub);
        }
    }

    private List<String> subCategoriesFor(String category) {
        List<String> result = new ArrayList<>();
        List<String> stored = subCategories.get(category);
        if (stored != null) {
            result.addAll(stored);
        }
        for (PathSequence sequence : sequences) {
            if (sequence == null || !safe(category).equalsIgnoreCase(safe(sequence.getCategory()).trim())) {
                continue;
            }
            String sub = safe(sequence.getSubCategory()).trim();
            if (!sub.isEmpty() && !containsIgnoreCase(result, sub)) {
                result.add(sub);
            }
        }
        return result;
    }

    private List<PathSequence> sequencesForCategory(String category, String subCategory) {
        List<PathSequence> result = new ArrayList<>();
        String normalizedCategory = safe(category).trim();
        String normalizedSub = safe(subCategory).trim();
        for (PathSequence sequence : sequences) {
            if (sequence == null || !normalizedCategory.equalsIgnoreCase(safe(sequence.getCategory()).trim())) {
                continue;
            }
            if (!normalizedSub.isEmpty() && !normalizedSub.equalsIgnoreCase(safe(sequence.getSubCategory()).trim())) {
                continue;
            }
            if (normalizedSub.isEmpty() && !safe(sequence.getSubCategory()).trim().isEmpty()) {
                continue;
            }
            result.add(sequence);
        }
        return result;
    }

    private List<PathSequence> allSequencesForCategory(String category) {
        List<PathSequence> result = new ArrayList<>();
        String normalizedCategory = safe(category).trim();
        for (PathSequence sequence : sequences) {
            if (sequence != null && normalizedCategory.equalsIgnoreCase(safe(sequence.getCategory()).trim())) {
                result.add(sequence);
            }
        }
        return result;
    }

    private boolean hasMatchingSequence(List<PathSequence> candidates, String query) {
        if (candidates == null) {
            return false;
        }
        for (PathSequence sequence : candidates) {
            if (matches(sequence, query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(PathSequence sequence, String query) {
        String normalizedQuery = PinyinSearchHelper.normalizeQuery(safe(query).trim());
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String text = safe(sequence == null ? "" : sequence.getName()) + " "
                + safe(sequence == null ? "" : sequence.getCategory()) + " "
                + safe(sequence == null ? "" : sequence.getSubCategory()) + " "
                + safe(sequence == null ? "" : sequence.getNote());
        return PinyinSearchHelper.matchesNormalized(text, normalizedQuery);
    }

    private boolean contains(String text, String query) {
        return safe(text).toLowerCase(Locale.ROOT).contains(safe(query).toLowerCase(Locale.ROOT));
    }

    private PathSequence findSequence(String name) {
        String normalized = safe(name).trim();
        if (normalized.isEmpty()) {
            return null;
        }
        for (PathSequence sequence : sequences) {
            if (sequence != null && normalized.equalsIgnoreCase(safe(sequence.getName()).trim())) {
                return sequence;
            }
        }
        return null;
    }

    private PathSequence adoptPersistedSequence(String name) {
        PathSequence existing = findSequence(name);
        if (existing != null) {
            return existing;
        }
        PathSequence persisted = PathSequenceManager.getSequence(name);
        if (persisted == null) {
            return null;
        }
        PathSequence copy;
        try {
            copy = new PathSequence(persisted, true);
        } catch (Exception ignored) {
            return null;
        }
        sequences.add(0, copy);
        addCategoryLocal(copy.getCategory());
        addSubCategoryLocal(copy.getCategory(), copy.getSubCategory());
        try {
            originalSequences.add(0, new PathSequence(copy, true));
        } catch (Exception ignored) {
        }
        return copy;
    }

    private PathSequence findSequenceExcept(String name, PathSequence excluded) {
        String normalized = safe(name).trim();
        for (PathSequence sequence : sequences) {
            if (sequence != null && sequence != excluded && normalized.equalsIgnoreCase(safe(sequence.getName()).trim())) {
                return sequence;
            }
        }
        return null;
    }

    private String uniqueSequenceName(String base) {
        String candidate = safe(base).trim().isEmpty() ? "gui.modern.path.wb.u192" : base.trim();
        int suffix = 2;
        while (findSequence(candidate) != null) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private String findCategory(String name) {
        for (String category : categories) {
            if (safe(category).trim().equalsIgnoreCase(safe(name).trim())) {
                return category;
            }
        }
        return null;
    }

    private String findCategoryExcept(String name, String excluded) {
        for (String category : categories) {
            if (!safe(category).trim().equalsIgnoreCase(safe(excluded).trim())
                    && safe(category).trim().equalsIgnoreCase(safe(name).trim())) {
                return category;
            }
        }
        return null;
    }

    private boolean isEditableSequence() {
        return selectedSequence != null;
    }

    private boolean canEditStep() {
        return isEditableSequence() && selectedStep != null;
    }

    private boolean isProtectedCategory(String category) {
        return defaultCategory().equals(category) || builtinCategory().equals(category);
    }

    private boolean isBuiltinCategory(String category) {
        return builtinCategory().equals(category);
    }

    private String defaultCategory() {
        return I18n.format("path.category.default");
    }

    private String builtinCategory() {
        return I18n.format("path.category.builtin");
    }

    private boolean containsIgnoreCase(List<String> values, String value) {
        if (values == null) {
            return false;
        }
        for (String existing : values) {
            if (safe(existing).trim().equalsIgnoreCase(safe(value).trim())) {
                return true;
            }
        }
        return false;
    }

    private String groupKey(String category, String subCategory) {
        return safe(category).trim().toLowerCase(Locale.ROOT) + "::"
                + (safe(subCategory).trim().isEmpty() ? "__ungrouped__"
                        : safe(subCategory).trim().toLowerCase(Locale.ROOT));
    }

    private String lower(String value) {
        return safe(value).trim().toLowerCase(Locale.ROOT);
    }

    private String query(String key) {
        ModernTextField field = fields.get(key);
        return field == null ? "" : safe(field.getText()).trim().toLowerCase(Locale.ROOT);
    }

    private String coordinateText(double[] point, int index) {
        if (point == null || point.length <= index || Double.isNaN(point[index])) {
            return "";
        }
        return String.format(Locale.ROOT, "%.3f", point[index]).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private double round(double value) {
        return Math.round(value * 1000.0D) / 1000.0D;
    }

    private String stepDescription(PathStep step) {
        if (step == null) {
            return "gui.modern.path.wb.u193";
        }
        double[] point = step.getGotoPoint();
        String target = point == null || point.length < 3 || Double.isNaN(point[0]) ? "gui.modern.path.wb.u194"
                : String.format(Locale.ROOT, "GOTO %.0f, %.0f, %.0f", point[0], point[1], point[2]);
        return tr("gui.modern.path.wb.fmt.target_actions", tr(target), String.valueOf(step.getActions().size()));
    }

    private String prettyType(String type) {
        String value = safe(type).trim();
        if (value.isEmpty()) {
            return "gui.modern.path.wb.u195";
        }
        String[] parts = value.split("[_\\-\\s]+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return builder.length() == 0 ? value : builder.toString();
    }

    private String localizedActionName(String type) {
        String normalized = safe(type).trim().toLowerCase(Locale.ROOT);
        String key = ActionDisplayCatalog.getActionDisplayKeys().get(normalized);
        if (key != null) {
            String label = I18n.format(key);
            if (!safe(label).isEmpty() && !label.equals(key)) {
                return label;
            }
        }
        return prettyType(normalized);
    }

    private int actionAccent(String type) {
        String normalized = safe(type).toLowerCase(Locale.ROOT);
        if (normalized.contains("condition") || normalized.contains("wait") || normalized.contains("if")) {
            return 0xFFC58CFF;
        }
        if (normalized.contains("move") || normalized.contains("goto") || normalized.contains("click")) {
            return 0xFF69D4CC;
        }
        if (normalized.contains("packet") || normalized.contains("send")) {
            return 0xFFF0B55E;
        }
        if (normalized.contains("set") || normalized.contains("capture") || normalized.contains("var")) {
            return 0xFF6AA9FF;
        }
        return ModernUiRenderer.SUBTLE_TEXT;
    }

    private String jsonValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        return element.isJsonPrimitive() ? element.getAsString() : element.toString();
    }

    private JsonElement parseParameterValue(String raw, JsonElement current) {
        String value = safe(raw);
        if (ActionParameterVariableResolver.looksLikeVariableReference(value)) {
            return new com.google.gson.JsonPrimitive(value.trim());
        }
        if (current != null && current.isJsonPrimitive()) {
            if (current.getAsJsonPrimitive().isBoolean()) {
                if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                    return null;
                }
                return new com.google.gson.JsonPrimitive(Boolean.parseBoolean(value.trim()));
            }
            if (current.getAsJsonPrimitive().isNumber()) {
                try {
                    if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                        return new com.google.gson.JsonPrimitive(Double.parseDouble(value.trim()));
                    }
                    return new com.google.gson.JsonPrimitive(Long.parseLong(value.trim()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return new com.google.gson.JsonPrimitive(value);
        }
        if (current != null && (current.isJsonArray() || current.isJsonObject())) {
            try {
                JsonElement parsed = new JsonParser().parse(value.trim());
                return parsed != null && (parsed.isJsonArray() || parsed.isJsonObject()) ? parsed : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        return new com.google.gson.JsonPrimitive(value);
    }

    private JsonElement parseLiteral(String raw) {
        String value = safe(raw).trim();
        if (value.isEmpty()) {
            return new com.google.gson.JsonPrimitive("");
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return new com.google.gson.JsonPrimitive(Boolean.parseBoolean(value));
        }
        try {
            if (value.matches("[-+]?\\d+")) {
                return new com.google.gson.JsonPrimitive(Long.parseLong(value));
            }
            if (value.matches("[-+]?(\\d+\\.\\d*|\\.\\d+)([eE][-+]?\\d+)?")) {
                return new com.google.gson.JsonPrimitive(Double.parseDouble(value));
            }
        } catch (NumberFormatException ignored) {
        }
        if ((value.startsWith("[") && value.endsWith("]")) || (value.startsWith("{") && value.endsWith("}"))) {
            try {
                return new JsonParser().parse(value);
            } catch (Exception ignored) {
            }
        }
        return new com.google.gson.JsonPrimitive(raw == null ? "" : raw);
    }

    private JsonObject defaultParams(String type) {
        String normalized = safe(type).toLowerCase(Locale.ROOT);
        JsonObject params = ModernActionEditorSchema.defaultsFor(normalized);
        if ("click".equals(normalized)) {
            params.addProperty("originalWidth", Math.max(1, minecraft.displayWidth));
            params.addProperty("originalHeight", Math.max(1, minecraft.displayHeight));
        } else if ("window_click".equals(normalized) || "run_sequence".equals(normalized)) {
            params.addProperty("uuid", java.util.UUID.randomUUID().toString());
        }
        return params;
    }

    private ModernMainLayout.Rect auxContentBounds;
    private ModernMainLayout.Rect auxBackBounds;
    private ModernMainLayout.Rect auxPrimaryBounds;
    private ModernMainLayout.Rect auxSecondaryBounds;
    private final List<RowHit> validationHits = new ArrayList<>();
    private final List<RowHit> sessionHits = new ArrayList<>();
    private final List<RowHit> templateHits = new ArrayList<>();
    private ModernMainLayout.Rect templateEditorBounds;
    private ModernMainLayout.Rect templateListBounds;
    private ModernMainLayout.Rect logListBounds;
    private ModernMainLayout.Rect logDetailBounds;

    private void drawAuxiliary(FontRenderer font, int mouseX, int mouseY) {
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            // The workspace tab already identifies this page. Use all remaining space
            // for the recording editor, including the unused auxiliary footer.
            auxBackBounds = null;
            actionPageWritingHelpBounds = null;
            auxContentBounds = inset(bounds, 10);
            drawRecordingView(font, mouseX, mouseY);
            return;
        }
        if (auxiliaryView == AuxiliaryView.LOGS) {
            // The log view owns its compact header and footer. Do not spend a
            // separate auxiliary-header row on a repeated title/subtitle.
            auxBackBounds = null;
            auxPrimaryBounds = null;
            auxSecondaryBounds = null;
            logListBounds = null;
            logDetailBounds = null;
            logExportBounds = null;
            actionPageWritingHelpBounds = null;
            auxContentBounds = inset(bounds, 10);
            drawLogsView(font, mouseX, mouseY);
            return;
        }
        String auxTitle;
        String auxSubtitle;
        switch (auxiliaryView) {
            case VALIDATION:
                auxTitle = "gui.modern.path.wb.u196";
                auxSubtitle = "gui.modern.path.wb.u197";
                break;
            case LOGS:
                auxTitle = "gui.modern.path.wb.u198";
                auxSubtitle = "gui.modern.path.wb.u199";
                break;
            case TEMPLATES:
                auxTitle = "gui.modern.path.wb.u200";
                auxSubtitle = "gui.modern.path.wb.u201";
                break;
            case VARIABLES:
                auxTitle = "gui.modern.path.wb.u202";
                auxSubtitle = "gui.modern.path.wb.u203";
                break;
            case RECORDING:
                auxTitle = "gui.modern.path.wb.u204";
                auxSubtitle = "gui.modern.path.wb.u205";
                break;
            case ACTION_EDITOR:
                auxTitle = "gui.modern.path.wb.u206";
                auxSubtitle = "gui.modern.path.wb.u207";
                break;
            case EXPRESSION_EDITOR:
                auxTitle = expressionEditorTitle;
                auxSubtitle = "gui.modern.path.wb.u208";
                break;
            default:
                auxTitle = title;
                auxSubtitle = subtitle;
                break;
        }
        drawAuxHeader(font, auxTitle, auxSubtitle, mouseX, mouseY);
        // Auxiliary pages do not render the workbench footer.  Using footerBounds
        // here left a large dead area below their own controls, especially on the
        // template page.  Let the page use the full shell and keep one consistent
        // breathing-space inset at the bottom instead.
        auxContentBounds = new ModernMainLayout.Rect(bounds.x + 10, headerBounds.bottom() + 8,
                Math.max(1, bounds.width - 20), Math.max(1, bounds.bottom() - headerBounds.bottom() - 18));
        switch (auxiliaryView) {
            case VALIDATION:
                drawValidationView(font, mouseX, mouseY);
                break;
            case LOGS:
                drawLogsView(font, mouseX, mouseY);
                break;
            case TEMPLATES:
                drawTemplatesView(font, mouseX, mouseY);
                break;
            case VARIABLES:
                drawVariablesView(font, mouseX, mouseY);
                break;
            case RECORDING:
                drawRecordingView(font, mouseX, mouseY);
                break;
            case ACTION_EDITOR:
                drawActionEditorPage(font, mouseX, mouseY);
                if (actionPickerMode != ActionPickerMode.NONE) {
                    drawActionPicker(font, mouseX, mouseY);
                }
                break;
            case EXPRESSION_EDITOR:
                drawExpressionEditorPage(font, mouseX, mouseY);
                break;
            default:
                break;
        }
    }

    private void drawAuxHeader(FontRenderer font, String auxTitle, String auxSubtitle, int mouseX, int mouseY) {
        ModernUiRenderer.drawRoundedRect(headerBounds.x + 1, headerBounds.y + 1,
                Math.max(1, headerBounds.width - 2), Math.max(1, headerBounds.height - 2), 6,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawDivider(headerBounds.x + 10, headerBounds.bottom(), Math.max(1, headerBounds.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        auxBackBounds = new ModernMainLayout.Rect(headerBounds.x + 8, headerBounds.y + 7, 28, 26);
        drawBackGlyph(auxBackBounds, mouseX, mouseY);
        actionPageWritingHelpBounds = null;
        if (auxiliaryView == AuxiliaryView.TEMPLATES) {
            // The template page is self-explanatory from its content.  Keep only
            // the navigation affordance in the compact top bar.
            return;
        }
        ModernUiRenderer.drawText(font, auxTitle, headerBounds.x + 46, headerBounds.y + 7,
                ModernUiRenderer.TEXT, Math.max(80, headerBounds.width - 58));
        ModernUiRenderer.drawText(font, auxSubtitle, headerBounds.x + 46, headerBounds.y + 22,
                ModernUiRenderer.MUTED_TEXT, Math.max(80, headerBounds.width - 58));
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR) {
            // Keep this action-editor affordance fixed in the header instead of
            // hiding it in an overflow strip.  It remains visible at every window
            // width and opens the draggable three-column parameter reference.
            actionPageWritingHelpBounds = new ModernMainLayout.Rect(
                    headerBounds.right() - 126, headerBounds.y + 9, 112, 22);
            drawButton(font, actionPageWritingHelpBounds, "gui.modern.path.wb.u349", true,
                    showActionWritingHelp, true, mouseX, mouseY);
        }
    }

    private void drawValidationView(FontRenderer font, int mouseX, int mouseY) {
        int errors = countErrors(validationIssues);
        int warnings = Math.max(0, validationIssues.size() - errors);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.err_warn", String.valueOf(errors), String.valueOf(warnings)),
                auxContentBounds.x + 4, auxContentBounds.y + 4,
                errors > 0 ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS,
                Math.max(80, auxContentBounds.width - 8));
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(auxContentBounds.x + 2, auxContentBounds.y + 24,
                Math.max(1, auxContentBounds.width - 4), Math.max(1, auxContentBounds.height - 56));
        ModernUiRenderer.drawSubtlePanel(list.x, list.y, list.width, list.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        validationHits.clear();
        int visible = Math.max(1, list.height / 34);
        validationMaxScroll = Math.max(0, validationIssues.size() - visible);
        validationScroll = clamp(validationScroll, 0, validationMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y + 5 - validationScroll * 34;
        for (int i = 0; i < validationIssues.size(); i++) {
            Issue issue = validationIssues.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x + 5, y, ModernHoverScrollbar.contentWidth(list.width - 5), 30);
            boolean error = issue != null && issue.getSeverity() == PathConfigValidator.Severity.ERROR;
            boolean hovered = row.contains(lastMouseX, lastMouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    error ? 0xFFB64A4A : 0xFFE0A100);
            if (issue != null) {
                ModernUiRenderer.drawText(font, tr(error ? "gui.modern.path.wb.u209" : "gui.modern.path.wb.u210") + " · " + issue.getLocationText(),
                        row.x + 7, row.y + 4, error ? 0xFFFFA0A0 : 0xFFFFD27A, Math.max(40, row.width - 14));
                ModernUiRenderer.drawText(font, issue.getSummary() + (issue.getDetail().isEmpty() ? "" : " · " + issue.getDetail()),
                        row.x + 7, row.y + 16, ModernUiRenderer.SUBTLE_TEXT, Math.max(40, row.width - 14));
            }
            if (row.bottom() > list.y && row.y < list.bottom()) {
                validationHits.add(new RowHit(i, row));
            }
            y += 34;
        }
        ModernUiRenderer.endClip();
        if (validationIssues.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u211", list.x + 12, list.y + list.height / 2 - 5,
                    ModernUiRenderer.SUCCESS, Math.max(50, list.width - 24));
        }
        drawScrollBarFor(validationScrollBar, list, validationScroll, validationMaxScroll, validationIssues.size(),
                visible, mouseX, mouseY, value -> validationScroll = value);
        auxPrimaryBounds = new ModernMainLayout.Rect(auxContentBounds.right() - 112, auxContentBounds.bottom() - 23, 52, 20);
        auxSecondaryBounds = new ModernMainLayout.Rect(auxContentBounds.right() - 54, auxContentBounds.bottom() - 23, 52, 20);
        drawButton(font, auxPrimaryBounds, "gui.modern.path.wb.u212", false, false, true, mouseX, mouseY);
        drawButton(font, auxSecondaryBounds, "gui.modern.path.wb.u213", true, false, true, mouseX, mouseY);
    }

    private void drawLogsView(FontRenderer font, int mouseX, int mouseY) {
        executionLogView.draw(font, auxContentBounds, mouseX, mouseY);
        return;
        /*
        int gap = 8;
        int listWidth = Math.max(150, Math.min(250, auxContentBounds.width / 3));
        logListBounds = new ModernMainLayout.Rect(auxContentBounds.x, auxContentBounds.y + 20, listWidth,
                Math.max(1, auxContentBounds.height - 20));
        logDetailBounds = new ModernMainLayout.Rect(logListBounds.right() + gap, logListBounds.y,
                Math.max(1, auxContentBounds.right() - logListBounds.right() - gap), logListBounds.height);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.sessions", String.valueOf(sessions.size())), logListBounds.x, auxContentBounds.y + 4,
                ModernUiRenderer.SUBTLE_TEXT, listWidth);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u214", logDetailBounds.x, auxContentBounds.y + 4,
                ModernUiRenderer.SUBTLE_TEXT, logDetailBounds.width);
        ModernUiRenderer.drawSubtlePanel(logListBounds.x, logListBounds.y, logListBounds.width, logListBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(logDetailBounds.x, logDetailBounds.y, logDetailBounds.width, logDetailBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        sessionHits.clear();
        int visible = Math.max(1, logListBounds.height / 47);
        sessionMaxScroll = Math.max(0, sessions.size() - visible);
        sessionScroll = clamp(sessionScroll, 0, sessionMaxScroll);
        ModernUiRenderer.beginClip(logListBounds);
        int y = logListBounds.y + 5 - sessionScroll * 47;
        for (int i = 0; i < sessions.size(); i++) {
            SessionSnapshot session = sessions.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(logListBounds.x + 5, y,
                    ModernHoverScrollbar.contentWidth(logListBounds.width - 7), 42);
            boolean selected = i == selectedSessionIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            int accent = sessionAccent(session);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawRoundedRect(row.x, row.y, 3, row.height, 2, accent);
            ModernUiRenderer.drawText(font, safe(session.getSequenceName()), row.x + 9, row.y + 5,
                    ModernUiRenderer.TEXT, Math.max(40, row.width - 18));
            ModernUiRenderer.drawText(font, session.isBackground() ? "gui.modern.path.wb.u033" : "gui.modern.path.wb.u215", row.x + 9, row.y + 18,
                    accent, Math.max(30, row.width - 18));
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.events_done", String.valueOf(session.getEvents().size()),
                    tr(session.isFinished() ? (session.isSuccess() ? "gui.modern.path.wb.u216" : "gui.modern.path.wb.u217") : "gui.modern.path.wb.u218")), row.x + 9, row.y + 30,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, row.width - 18));
            if (row.bottom() > logListBounds.y && row.y < logListBounds.bottom()) {
                sessionHits.add(new RowHit(i, row));
            }
            y += 47;
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(logListScrollBar, logListBounds, sessionScroll, sessionMaxScroll, sessions.size(), visible,
                mouseX, mouseY, value -> sessionScroll = value);
        drawLogDetail(font, mouseX, mouseY);
        auxPrimaryBounds = new ModernMainLayout.Rect(auxContentBounds.x, auxContentBounds.bottom() - 20, 52, 18);
        auxSecondaryBounds = new ModernMainLayout.Rect(auxContentBounds.x + 58, auxContentBounds.bottom() - 20, 52, 18);
        ModernMainLayout.Rect export = new ModernMainLayout.Rect(auxContentBounds.x + 116, auxContentBounds.bottom() - 20, 52, 18);
        drawButton(font, auxPrimaryBounds, "gui.modern.path.wb.u212", false, false, true, mouseX, mouseY);
        drawButton(font, auxSecondaryBounds, "gui.modern.path.wb.u219", false, true, !sessions.isEmpty(), mouseX, mouseY);
        drawButton(font, export, "gui.modern.path.wb.u220", true, false, selectedSessionIndex >= 0, mouseX, mouseY);
        logExportBounds = export;
        */
    }

    private ModernMainLayout.Rect logExportBounds;

    private void drawLogDetail(FontRenderer font, int mouseX, int mouseY) {
        SessionSnapshot session = selectedSessionIndex >= 0 && selectedSessionIndex < sessions.size()
                ? sessions.get(selectedSessionIndex) : null;
        if (session == null) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u221", logDetailBounds.x + 10, logDetailBounds.y + 16,
                    ModernUiRenderer.MUTED_TEXT, Math.max(60, logDetailBounds.width - 20));
            logDetailMaxScroll = 0;
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(tr("gui.modern.path.wb.fmt.log_seq", session.getSequenceName(),
                tr(session.isBackground() ? "gui.modern.path.wb.fmt.bg" : "gui.modern.path.wb.fmt.fg")));
        lines.add(tr("gui.modern.path.wb.fmt.log_status", tr(session.isFinished()
                ? (session.isSuccess() ? "gui.modern.path.wb.u216" : "gui.modern.path.wb.u217") : "gui.modern.path.wb.u218")));
        lines.add(tr("gui.modern.path.wb.fmt.log_start", String.valueOf(new Date(session.getStartTime())),
                String.valueOf(session.getDurationMs())));
        lines.add(tr("gui.modern.path.wb.fmt.log_reason", safe(session.getFinishReason())));
        lines.add(tr("gui.modern.path.wb.fmt.log_vars", String.valueOf(session.getInitialVariables().size())));
        lines.add("");
        for (ExecutionEvent event : session.getEvents()) {
            lines.add(tr("gui.modern.path.wb.fmt.log_event", String.valueOf(new Date(event.getTimestamp())),
                    event.getType(), String.valueOf(event.getStepIndex()), String.valueOf(event.getActionIndex())));
            lines.add("  " + event.getMessage());
            if (!safe(event.getStatus()).isEmpty()) {
                lines.add(tr("gui.modern.path.wb.fmt.log_event_status", event.getStatus()));
            }
        }
        int lineHeight = font.FONT_HEIGHT + 2;
        int visible = Math.max(1, (logDetailBounds.height - 10) / lineHeight);
        logDetailMaxScroll = Math.max(0, lines.size() - visible);
        logDetailScroll = clamp(logDetailScroll, 0, logDetailMaxScroll);
        ModernUiRenderer.beginClip(logDetailBounds);
        int y = logDetailBounds.y + 7 - logDetailScroll * lineHeight;
        for (String line : lines) {
            if (y + lineHeight >= logDetailBounds.y && y < logDetailBounds.bottom()) {
                ModernUiRenderer.drawText(font, line, logDetailBounds.x + 8, y, ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(40, logDetailBounds.width - 20));
            }
            y += lineHeight;
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(logDetailScrollBar, logDetailBounds, logDetailScroll, logDetailMaxScroll, lines.size(),
                visible, mouseX, mouseY, value -> logDetailScroll = value);
    }

    private void drawTemplatesView(FontRenderer font, int mouseX, int mouseY) {
        int gap = 8;
        // The page header no longer carries a title or explanatory subtitle, so
        // start the three-column workspace at the content edge and give the
        // controls the reclaimed vertical space.
        int top = auxContentBounds.y;
        int height = Math.max(1, auxContentBounds.height - 28);
        int total = Math.max(2, auxContentBounds.width - gap * 2);
        ModernSplitPane.Split categorySplit = ModernSplitPane.calculate(total, templateCategoryRatio,
                125, 220, 105, 190);
        templateCategoryRatio = categorySplit.ratio;
        int remaining = categorySplit.secondWidth;
        ModernSplitPane.Split listSplit = ModernSplitPane.calculate(remaining, templateListRatio,
                220, 260, 190, 210);
        templateListRatio = listSplit.ratio;
        ModernMainLayout.Rect category = new ModernMainLayout.Rect(auxContentBounds.x, top,
                categorySplit.firstWidth, height);
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(category.right() + gap, top,
                listSplit.firstWidth, height);
        templateEditorBounds = new ModernMainLayout.Rect(list.right() + gap, top,
                Math.max(1, listSplit.secondWidth), height);
        templateCategoryBounds = category;
        templateListBounds = list;
        templateCategoryDividerBounds = ModernSplitPane.verticalDividerBounds(category.x, category.width, gap, top, height);
        templateListDividerBounds = ModernSplitPane.verticalDividerBounds(list.x, list.width, gap, top, height);
        if (templateCategoryDividerBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.wb.u222";
        } else if (templateListDividerBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.wb.u223";
        }
        drawTemplateCategoryPane(font, category, mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(list.x, list.y, list.width, list.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(templateEditorBounds.x, templateEditorBounds.y, templateEditorBounds.width,
                templateEditorBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        templateSearchBounds = new ModernMainLayout.Rect(list.x + 8, list.y + 8, list.width - 16, 20);
        drawField(font, "template.search", templateSearchBounds, true, "gui.modern.path.wb.u225", mouseX, mouseY);
        templateListViewportBounds = new ModernMainLayout.Rect(list.x, list.y + 34, list.width,
                Math.max(1, list.height - 34));
        List<ActionTemplate> visibleTemplates = filteredActionTemplates();
        templateHits.clear();
        int rowHeight = 34;
        int visible = Math.max(1, (templateListViewportBounds.height - 10) / rowHeight);
        templateMaxScroll = Math.max(0,
                visibleTemplates.size() * rowHeight - templateListViewportBounds.height + 10);
        templateScroll = clamp(templateScroll, 0, templateMaxScroll);
        ModernUiRenderer.beginClip(templateListViewportBounds);
        for (int i = 0; i < visibleTemplates.size(); i++) {
            ActionTemplate template = visibleTemplates.get(i);
            int sourceIndex = actionTemplateIndex(template);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x + 6,
                    templateListViewportBounds.y + 6 + i * rowHeight - templateScroll,
                    ModernHoverScrollbar.contentWidth(list.width - 5), 30);
            boolean selected = sourceIndex == selectedActionTemplateIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, safe(template.getName()), row.x + 8, row.y + 4,
                    selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(40, row.width - 16));
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.cat_actions", safe(template.getCategory()), String.valueOf(template.getActions().size())),
                    row.x + 8, row.y + 17, ModernUiRenderer.MUTED_TEXT, Math.max(40, row.width - 16));
            if (row.bottom() > templateListViewportBounds.y && row.y < templateListViewportBounds.bottom()) {
                templateHits.add(new RowHit(sourceIndex, row));
            }
        }
        ModernUiRenderer.endClip();
        drawTemplateScrollbar(templateListViewportBounds, mouseX, mouseY, visibleTemplates.size(), visible);
        if (visibleTemplates.isEmpty()) {
            ModernUiRenderer.drawText(font, actionTemplates.isEmpty() ? "gui.modern.path.wb.u226" : "gui.modern.path.wb.u227",
                    templateListViewportBounds.x + 10, templateListViewportBounds.y + templateListViewportBounds.height / 2 - 5,
                    ModernUiRenderer.MUTED_TEXT, Math.max(40, list.width - 20));
        }
        if (selectedActionTemplateIndex >= 0 && selectedActionTemplateIndex < actionTemplates.size()) {
            ActionTemplate template = actionTemplates.get(selectedActionTemplateIndex);
            int x = templateEditorBounds.x + 10;
            int width = Math.max(1, templateEditorBounds.width - 20);
            int y = templateEditorBounds.y + 12;
            ModernUiRenderer.drawText(font, safe(template.getName()), x, y, ModernUiRenderer.TEXT, width);
            ModernUiRenderer.drawText(font,
                    tr("gui.modern.path.wb.fmt.template_meta", tr(template.isCustom() ? "gui.modern.path.wb.u093" : "gui.modern.path.wb.u094"),
                            safe(template.getCategory()), String.valueOf(template.getActions().size())),
                    x, y + 17, template.isCustom() ? ModernUiRenderer.SUCCESS : ModernUiRenderer.ACCENT, width);
            ModernUiRenderer.drawText(font, safe(template.getSummary()), x, y + 39, ModernUiRenderer.SUBTLE_TEXT, width);
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.usecase", safe(template.getUseCase())), x, y + 58,
                    ModernUiRenderer.MUTED_TEXT, width);
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.hint", safe(template.getNote())), x, y + 77,
                    ModernUiRenderer.MUTED_TEXT, width);

            int actionTop = y + 106;
            int actionHeight = Math.max(36, templateEditorBounds.bottom() - actionTop - 8);
            ModernMainLayout.Rect actionList = new ModernMainLayout.Rect(x, actionTop, width, actionHeight);
            ModernUiRenderer.drawSubtlePanel(actionList.x, actionList.y, actionList.width, actionList.height, 5,
                    ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u228", actionList.x + 9, actionList.y + 7,
                    ModernUiRenderer.TEXT, Math.max(40, actionList.width - 18));
            ModernUiRenderer.beginClip(actionList);
            int actionY = actionList.y + 24;
            int actionVisible = Math.max(1, (actionList.height - 28) / 22);
            for (int i = 0; i < template.getActions().size() && i < actionVisible; i++) {
                ActionData action = template.getActions().get(i);
                ModernUiRenderer.drawText(font, (i + 1) + ". " + safe(action == null ? "" : action.type),
                        actionList.x + 9, actionY, ModernUiRenderer.ACCENT, Math.max(40, actionList.width - 18));
                ModernUiRenderer.drawText(font, safeActionDescription(action), actionList.x + 9, actionY + 11,
                        ModernUiRenderer.MUTED_TEXT, Math.max(40, actionList.width - 18));
                actionY += 22;
            }
            if (template.getActions().size() > actionVisible) {
                ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.more_actions", String.valueOf(template.getActions().size() - actionVisible)),
                        actionList.x + 9, actionList.bottom() - 15, ModernUiRenderer.MUTED_TEXT,
                        Math.max(40, actionList.width - 18));
            }
            ModernUiRenderer.endClip();
        }
        ModernSplitPane.drawVerticalDivider(templateCategoryDividerBounds, mouseX, mouseY,
                draggingTemplateCategoryDivider);
        ModernSplitPane.drawVerticalDivider(templateListDividerBounds, mouseX, mouseY, draggingTemplateListDivider);
        int buttonY = auxContentBounds.bottom() - 20;
        int buttonGap = 5;
        // Use a flex row across the complete footer width.  The old max width
        // left the right half of the page empty on wide screens.
        int buttonWidth = Math.max(48, (auxContentBounds.width - buttonGap * 4) / 5);
        templateAddBounds = new ModernMainLayout.Rect(auxContentBounds.x, buttonY, buttonWidth, 20);
        templateDeleteBounds = new ModernMainLayout.Rect(templateAddBounds.right() + buttonGap, buttonY, buttonWidth, 20);
        templateInsertBounds = new ModernMainLayout.Rect(templateDeleteBounds.right() + buttonGap, buttonY,
                buttonWidth, 20);
        templateInsertStepBounds = new ModernMainLayout.Rect(templateInsertBounds.right() + buttonGap, buttonY,
                buttonWidth, 20);
        templateSaveBounds = new ModernMainLayout.Rect(templateInsertStepBounds.right() + buttonGap, buttonY,
                Math.max(48, auxContentBounds.right() - (templateInsertStepBounds.right() + buttonGap)), 20);
        drawButton(font, templateAddBounds, "gui.modern.path.wb.u229", true, false, selectedStep != null, mouseX, mouseY);
        drawButton(font, templateDeleteBounds, "gui.modern.path.wb.u230", false, true,
                selectedActionTemplateIndex >= 0 && selectedActionTemplateIndex < actionTemplates.size()
                        && actionTemplates.get(selectedActionTemplateIndex).isCustom(),
                mouseX, mouseY);
        drawButton(font, templateInsertBounds, "gui.modern.path.wb.u231", false, false,
                selectedActionTemplateIndex >= 0 && canEditStep(), mouseX, mouseY);
        drawButton(font, templateInsertStepBounds, "gui.modern.path.wb.u232", false, false,
                selectedActionTemplateIndex >= 0 && isEditableSequence(), mouseX, mouseY);
        drawButton(font, templateSaveBounds, "gui.modern.path.wb.u212", false, false, true, mouseX, mouseY);
    }

    private void drawTemplateCategoryPane(FontRenderer font, ModernMainLayout.Rect pane, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(pane.x, pane.y, pane.width, pane.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        templateCategoryHits.clear();
        int rowHeight = 28;
        int visible = Math.max(1, (pane.height - 10) / rowHeight);
        templateCategoryMaxScroll = Math.max(0, actionTemplateCategories.size() * rowHeight - pane.height + 10);
        templateCategoryScroll = clamp(templateCategoryScroll, 0, templateCategoryMaxScroll);
        ModernUiRenderer.beginClip(pane);
        for (int i = 0; i < actionTemplateCategories.size(); i++) {
            String category = actionTemplateCategories.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(pane.x + 6,
                    pane.y + 6 + i * rowHeight - templateCategoryScroll, ModernHoverScrollbar.contentWidth(pane.width - 6), 24);
            boolean selected = safe(category).equals(selectedActionTemplateCategory);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                            : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, safe(category), row.x + 9, row.y + 6,
                    selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(30, row.width - 18));
            if (row.bottom() > pane.y && row.y < pane.bottom()) {
                templateCategoryHits.add(new RowHit(i, row));
            }
        }
        ModernUiRenderer.endClip();
        drawTemplateCategoryScrollbar(pane, mouseX, mouseY, visible);
    }

    private List<ActionTemplate> filteredActionTemplates() {
        String query = PinyinSearchHelper.normalizeQuery(query("template.search"));
        List<ActionTemplate> result = new ArrayList<ActionTemplate>();
        for (ActionTemplate template : actionTemplates) {
            if (template != null
                    && ("gui.modern.path.wb.u008".equals(selectedActionTemplateCategory)
                            || selectedActionTemplateCategory.equals(template.getCategory()))
                    && (query.isEmpty()
                    || PinyinSearchHelper.matchesNormalized(template.getSearchText(), query))) {
                result.add(template);
            }
        }
        return result;
    }

    private void rebuildActionTemplateCategories() {
        actionTemplateCategories.clear();
        actionTemplateCategories.add("gui.modern.path.wb.u008");
        for (ActionTemplate template : actionTemplates) {
            if (template != null && !actionTemplateCategories.contains(template.getCategory())) {
                actionTemplateCategories.add(template.getCategory());
            }
        }
        if (!actionTemplateCategories.contains(selectedActionTemplateCategory)) {
            selectedActionTemplateCategory = "gui.modern.path.wb.u008";
        }
        templateCategoryScroll = clamp(templateCategoryScroll, 0, templateCategoryMaxScroll);
    }

    private void selectFirstVisibleActionTemplate() {
        List<ActionTemplate> visible = filteredActionTemplates();
        selectedActionTemplateIndex = visible.isEmpty() ? -1 : actionTemplateIndex(visible.get(0));
    }

    private void updateTemplatePaneRatios(int mouseX) {
        int total = Math.max(2, auxContentBounds.width - 16);
        if (draggingTemplateCategoryDivider) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(total,
                    mouseX - auxContentBounds.x, 125, total - 125, 105, 190);
            templateCategoryRatio = split.ratio;
            return;
        }
        int categoryWidth = ModernSplitPane.calculate(total, templateCategoryRatio, 125, 220, 105, 190).firstWidth;
        int remainingStart = auxContentBounds.x + categoryWidth + 8;
        int remaining = total - categoryWidth;
        ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(remaining,
                mouseX - remainingStart, 220, 260, 190, 210);
        templateListRatio = split.ratio;
    }

    private void drawTemplateCategoryScrollbar(ModernMainLayout.Rect list, int mouseX, int mouseY, int visible) {
        if (list == null || templateCategoryMaxScroll <= 0 || list.height <= 0) {
            templateCategoryScrollTrack = null;
            templateCategoryScrollThumb = null;
            templateCategoryScrollHover *= 0.7F;
            return;
        }
        int total = Math.max(visible, actionTemplateCategories.size());
        int thumbHeight = Math.max(18, list.height * visible / Math.max(visible, total));
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * templateCategoryScroll / Math.max(1, templateCategoryMaxScroll);
        templateCategoryScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingTemplateCategoryScroll || templateCategoryScrollTrack.contains(mouseX, mouseY);
        templateCategoryScrollHover += ((hovered ? 1.0F : 0.0F) - templateCategoryScrollHover) * 0.28F;
        int grown = Math.round(4 + 6 * templateCategoryScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * templateCategoryScrollHover));
        int thumbX = list.right() - grown - 2;
        templateCategoryScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
        ModernUiRenderer.drawRoundedRect(list.right() - trackWidth - 3, list.y, trackWidth, list.height, 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2),
                hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                        ModernUiRenderer.ACCENT, templateCategoryScrollHover));
    }

    private void drawTemplateScrollbar(ModernMainLayout.Rect list, int mouseX, int mouseY, int total, int visible) {
        if (list == null || templateMaxScroll <= 0 || list.height <= 0) {
            templateScrollTrack = null;
            templateScrollThumb = null;
            templateScrollHover *= 0.7F;
            return;
        }
        int thumbHeight = Math.max(18, list.height * visible / Math.max(visible, total));
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * templateScroll / Math.max(1, templateMaxScroll);
        templateScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingTemplateScroll || templateScrollTrack.contains(mouseX, mouseY);
        templateScrollHover += ((hovered ? 1.0F : 0.0F) - templateScrollHover) * 0.28F;
        int grown = Math.round(4 + 6 * templateScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * templateScrollHover));
        int thumbX = list.right() - grown - 2;
        templateScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
        ModernUiRenderer.drawRoundedRect(list.right() - trackWidth - 3, list.y, trackWidth, list.height, 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2),
                hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                        ModernUiRenderer.ACCENT, templateScrollHover));
    }

    private boolean beginTemplateScrollbarDrag(int mouseX, int mouseY) {
        if (templateScrollTrack != null && templateMaxScroll > 0 && templateScrollTrack.contains(mouseX, mouseY)) {
            draggingTemplateScroll = true;
            templateScrollDragOffset = templateScrollThumb != null && templateScrollThumb.contains(mouseX, mouseY)
                    ? mouseY - templateScrollThumb.y : templateScrollThumb == null ? 9 : templateScrollThumb.height / 2;
            applyTemplateScrollFromMouse(mouseY);
            return true;
        }
        if (templateCategoryScrollTrack != null && templateCategoryMaxScroll > 0
                && templateCategoryScrollTrack.contains(mouseX, mouseY)) {
            draggingTemplateCategoryScroll = true;
            templateCategoryScrollDragOffset = templateCategoryScrollThumb != null
                    && templateCategoryScrollThumb.contains(mouseX, mouseY)
                            ? mouseY - templateCategoryScrollThumb.y
                            : templateCategoryScrollThumb == null ? 9 : templateCategoryScrollThumb.height / 2;
            applyTemplateCategoryScrollFromMouse(mouseY);
            return true;
        }
        return false;
    }

    private void applyTemplateScrollFromMouse(int mouseY) {
        if (templateScrollTrack == null || templateScrollThumb == null || templateMaxScroll <= 0) return;
        int travel = Math.max(1, templateScrollTrack.height - templateScrollThumb.height);
        int target = clamp(mouseY - templateScrollDragOffset, templateScrollTrack.y,
                templateScrollTrack.y + travel);
        templateScroll = clamp(Math.round((target - templateScrollTrack.y) * templateMaxScroll / (float) travel),
                0, templateMaxScroll);
    }

    private void applyTemplateCategoryScrollFromMouse(int mouseY) {
        if (templateCategoryScrollTrack == null || templateCategoryScrollThumb == null
                || templateCategoryMaxScroll <= 0) return;
        int travel = Math.max(1, templateCategoryScrollTrack.height - templateCategoryScrollThumb.height);
        int target = clamp(mouseY - templateCategoryScrollDragOffset, templateCategoryScrollTrack.y,
                templateCategoryScrollTrack.y + travel);
        templateCategoryScroll = clamp(Math.round((target - templateCategoryScrollTrack.y)
                * templateCategoryMaxScroll / (float) travel), 0, templateCategoryMaxScroll);
    }

    private int actionTemplateIndex(ActionTemplate target) {
        if (target == null) {
            return -1;
        }
        for (int i = 0; i < actionTemplates.size(); i++) {
            ActionTemplate template = actionTemplates.get(i);
            if (template != null && safe(template.getId()).equals(safe(target.getId()))) {
                return i;
            }
        }
        return -1;
    }

    private String safeActionDescription(ActionData action) {
        if (action == null) {
            return "";
        }
        try {
            return action.getDescription();
        } catch (Exception ignored) {
            return safe(action.type);
        }
    }

    private ModernMainLayout.Rect templateNameBounds;
    private ModernMainLayout.Rect templateSequenceBounds;
    private ModernMainLayout.Rect templateDefaultsBounds;
    private ModernMainLayout.Rect templateNoteBounds;
    private ModernMainLayout.Rect templateAddBounds;
    private ModernMainLayout.Rect templateDeleteBounds;
    private ModernMainLayout.Rect templateInsertBounds;
    private ModernMainLayout.Rect templateInsertStepBounds;
    private ModernMainLayout.Rect templateSaveBounds;
    private ModernMainLayout.Rect templateSearchBounds;

    private void drawVariablesView(FontRenderer font, int mouseX, int mouseY) {
        bindVariablePanelHost();
        variablePanel.draw(font, auxContentBounds, bounds, mouseX, mouseY);
    }

    private ModernMainLayout.Rect recordingNameBounds;
    private ModernMainLayout.Rect recordingCategoryBounds;
    private ModernMainLayout.Rect recordingRadiusBounds;
    private ModernMainLayout.Rect recordingPacketWindowBounds;
    private ModernMainLayout.Rect recordingCategoryPickerBounds;
    private boolean recordingCategoryPickerOpen;
    private int recordingCategoryPickerScroll;
    private int recordingCategoryPickerMaxScroll;
    private final ModernHoverScrollbar recordingCategoryScrollBar = new ModernHoverScrollbar();
    private String recordingSaveCategory = "";
    private String recordingSaveSubCategory = "";
    private final List<NavHit> recordingCategoryHits = new ArrayList<NavHit>();
    private ModernMainLayout.Rect recordingStepDividerBounds;
    private ModernMainLayout.Rect recordingActionDividerBounds;
    private ActionData recordingReplacementTarget;
    private final Map<PathWorkbenchToolCatalog.Zone, ModernMainLayout.Rect> recordingSettingsHits = new LinkedHashMap<>();
    private final Map<PathWorkbenchToolCatalog.Zone, ModernMainLayout.Rect> recordingOverflowHits = new LinkedHashMap<>();
    private int recordingScroll;
    private int recordingMaxScroll;
    private final List<RowHit> recordingStepHits = new ArrayList<RowHit>();
    private final List<RowHit> recordingActionHits = new ArrayList<RowHit>();
    private final List<RowHit> recordingPacketToggleHits = new ArrayList<RowHit>();
    private PathSequence recordingDraft;
    private String recordingPreviousSequenceName = "";
    private int recordingPreviousStepIndex = -1;
    private int recordingPreviousActionIndex = -1;
    private int recordingDraftStepCount = -1;
    private int recordingDraftActionCount = -1;
    private long recordingDraftRevision = -1L;
    private boolean recordingDraftManuallyEdited;
    private final Set<ActionData> importedRecordingActions = Collections.newSetFromMap(
            new IdentityHashMap<ActionData, Boolean>());
    private PacketWorkbenchTab recordingPacketWorkbench;
    private ModernMainLayout.Rect recordingPacketOverlayBounds;
    private ModernMainLayout.Rect recordingPacketViewerBounds;
    private ModernMainLayout.Rect recordingPacketCloseBounds;
    private ModernMainLayout.Rect recordingPacketSendBounds;
    private ModernMainLayout.Rect recordingPacketReceiveBounds;
    private ModernMainLayout.Rect recordingPacketReplaceBounds;
    private String recordingPacketDirection = RecordingPacketSupport.C2S;
    private ActionData recordingPacketAction;

    private void drawRecordingView(FontRenderer font, int mouseX, int mouseY) {
        toolHits.clear();
        recordingSettingsHits.clear();
        recordingOverflowHits.clear();
        syncRecordingDraft();
        int x = auxContentBounds.x;
        int width = Math.max(1, auxContentBounds.width);
        int labelY = auxContentBounds.y;
        int topY = labelY + font.FONT_HEIGHT + 5;
        int fieldGap = 6;
        int nameWidth = Math.max(90, (width - fieldGap * 3) / 4);
        recordingNameBounds = new ModernMainLayout.Rect(x, topY, nameWidth, 18);
        recordingCategoryBounds = new ModernMainLayout.Rect(recordingNameBounds.right() + fieldGap, topY, nameWidth, 18);
        recordingRadiusBounds = new ModernMainLayout.Rect(recordingCategoryBounds.right() + fieldGap, topY, nameWidth, 18);
        recordingPacketWindowBounds = new ModernMainLayout.Rect(recordingRadiusBounds.right() + fieldGap, topY,
                Math.max(1, x + width - recordingRadiusBounds.right() - fieldGap), 18);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u233", recordingNameBounds.x, labelY,
                ModernUiRenderer.TEXT, recordingNameBounds.width);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u234", recordingCategoryBounds.x, labelY,
                ModernUiRenderer.TEXT, recordingCategoryBounds.width);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u235", recordingRadiusBounds.x, labelY,
                ModernUiRenderer.TEXT, recordingRadiusBounds.width);
        ModernUiRenderer.drawText(font, "gui.modern.path.record.packet_window_seconds", recordingPacketWindowBounds.x, labelY,
                ModernUiRenderer.TEXT, recordingPacketWindowBounds.width);
        drawLabeledField(font, "recording.name", recordingNameBounds, "gui.modern.path.wb.u233", true, mouseX, mouseY);
        drawRecordingCategoryField(font, mouseX, mouseY);
        drawLabeledField(font, "recording.radius", recordingRadiusBounds, "gui.modern.path.wb.u235", true, mouseX, mouseY);
        drawLabeledField(font, "recording.packetWindowSeconds", recordingPacketWindowBounds,
                "gui.modern.path.record.packet_window_seconds", true, mouseX, mouseY);
        boolean recording = PathRecordingManager.isRecording();
        String state = recording ? (PathRecordingManager.isPaused() ? "gui.modern.path.wb.u236" : "gui.modern.path.wb.u237")
                : "gui.modern.path.wb.u238";
        if (PathRecordingPlayback.isRunning()) {
            state = tr(state) + " · " + tr(PathRecordingPlayback.isPaused()
                    ? "gui.modern.path.record.run_paused" : "gui.modern.path.record.running");
        }
        if (statusUntil > System.currentTimeMillis() && !safe(status).isEmpty()) {
            state = tr(state) + " · " + tr(status);
        }
        ModernUiRenderer.drawText(font, state, x, topY + 25,
                recording ? (PathRecordingManager.isPaused() ? ModernUiRenderer.WARNING : ModernUiRenderer.SUCCESS)
                        : ModernUiRenderer.MUTED_TEXT, Math.max(80, width));

        int gap = 7;
        int paneTotal = Math.max(3, width - gap * 2);
        ModernSplitPane.Split stepSplit = ModernSplitPane.calculate(paneTotal, recordingStepRatio,
                170, 250, 160, 160);
        recordingStepRatio = stepSplit.ratio;
        int stepWidth = stepSplit.firstWidth;
        int remainingTotal = Math.max(2, stepSplit.secondWidth - gap);
        ModernSplitPane.Split actionSplit = ModernSplitPane.calculate(remainingTotal, recordingActionRatio,
                220, 340, 160, 160);
        recordingActionRatio = actionSplit.ratio;
        int actionWidth = actionSplit.firstWidth;
        int parameterWidth = Math.max(1, actionSplit.secondWidth);
        int paneY = topY + 43;
        int controlHeight = PathWorkbenchToolCatalog.get().stripHeight(
                PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL, width - 16, false);
        if (!recordingControlToolsExpanded) {
            controlHeight = TOOL_STRIP_HEADER_HEIGHT;
        }
        int paneHeight = Math.max(1, auxContentBounds.bottom() - paneY - controlHeight - 8);
        ModernMainLayout.Rect stepPane = new ModernMainLayout.Rect(x, paneY, stepWidth, paneHeight);
        ModernMainLayout.Rect actionPane = new ModernMainLayout.Rect(stepPane.right() + gap, paneY, actionWidth, paneHeight);
        ModernMainLayout.Rect parameterPane = new ModernMainLayout.Rect(actionPane.right() + gap, paneY,
                parameterWidth, paneHeight);
        recordingStepDividerBounds = ModernSplitPane.verticalDividerBounds(stepPane.x, stepPane.width, gap,
                paneY, paneHeight);
        recordingActionDividerBounds = ModernSplitPane.verticalDividerBounds(actionPane.x, actionPane.width, gap,
                paneY, paneHeight);
        drawRecordingStepPane(font, stepPane, mouseX, mouseY);
        drawRecordingActionPane(font, actionPane, mouseX, mouseY);
        drawRecordingParameterEditor(font, parameterPane, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(recordingStepDividerBounds, mouseX, mouseY, draggingRecordingStepDivider);
        ModernSplitPane.drawVerticalDivider(recordingActionDividerBounds, mouseX, mouseY, draggingRecordingActionDivider);

        ModernMainLayout.Rect controls = new ModernMainLayout.Rect(x, auxContentBounds.bottom() - controlHeight,
                width, controlHeight);
        drawRecordingToolStrip(font, controls, PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL, mouseX, mouseY);
        drawRecordingOverflow(PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL,
                recordingControlOverflowBounds(controls), mouseX, mouseY);
        drawRecordingCategoryPicker(font, mouseX, mouseY);
    }

    private ModernMainLayout.Rect recordingControlOverflowBounds(ModernMainLayout.Rect controls) {
        if (controls == null) {
            return null;
        }
        int right = recordingControlToolsToggleBounds == null
                ? controls.right() - 24 : recordingControlToolsToggleBounds.x - 3;
        return new ModernMainLayout.Rect(Math.max(controls.x + 1, right - 16), controls.y + 3, 16, 16);
    }

    private void drawRecordingCategoryField(FontRenderer font, int mouseX, int mouseY) {
        if (recordingCategoryBounds == null) {
            return;
        }
        ModernMainLayout.Rect rect = recordingCategoryBounds;
        boolean open = recordingCategoryPickerOpen;
        boolean hovered = rect.contains(mouseX, mouseY) || open;
        ModernUiRenderer.drawSubtlePanel(rect.x - 1, rect.y - 1, rect.width + 2, rect.height + 2, 4,
                hovered ? 0xFF15222C : 0xFF101820,
                open ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        String label = recordingCategoryButtonLabel();
        boolean placeholder = safe(recordingSaveCategory).trim().isEmpty();
        int textY = rect.y + Math.max(1, (rect.height - font.FONT_HEIGHT) / 2);
        ModernUiRenderer.drawText(font, label, rect.x + 5, textY,
                placeholder ? ModernUiRenderer.MUTED_TEXT : ModernUiRenderer.TEXT,
                Math.max(12, rect.width - 22));
        ModernUiRenderer.drawDropdownChevron(rect.right() - 14,
                rect.y + Math.max(4, (rect.height - 8) / 2),
                hovered || open ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        if (hovered) {
            hoveredTooltip = "gui.modern.path.record.pick_category";
        }
    }

    private String recordingCategoryButtonLabel() {
        String category = safe(recordingSaveCategory).trim();
        if (category.isEmpty()) {
            return "gui.modern.path.wb.u234";
        }
        String sub = safe(recordingSaveSubCategory).trim();
        return sub.isEmpty() ? category : category + " / " + sub;
    }

    private List<String> recordingCategorySource() {
        List<String> result = new ArrayList<String>();
        if (categories != null) {
            for (String category : categories) {
                if (category != null && !category.trim().isEmpty()) {
                    result.add(category);
                }
            }
        }
        if (result.isEmpty()) {
            result.add(defaultCategory());
        }
        return result;
    }

    private String recordingCategoryRowLabel(String category) {
        String name = safe(category).trim();
        if (Boolean.TRUE.equals(hiddenCategories.get(category))) {
            return tr("gui.modern.path.wb.fmt.hidden", name);
        }
        return name;
    }

    private int recordingCategoryOptionCount() {
        int count = 0;
        for (String category : recordingCategorySource()) {
            count++;
            for (String sub : subCategoriesFor(category)) {
                if (!safe(sub).trim().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    private void drawRecordingCategoryPicker(FontRenderer font, int mouseX, int mouseY) {
        recordingCategoryHits.clear();
        recordingCategoryPickerBounds = null;
        if (!recordingCategoryPickerOpen || recordingCategoryBounds == null || auxContentBounds == null) {
            return;
        }
        int rowHeight = 20;
        int padding = 6;
        int rowCount = Math.max(1, recordingCategoryOptionCount());
        int contentHeight = rowCount * rowHeight + padding;
        int menuWidth = Math.max(recordingCategoryBounds.width, 168);
        menuWidth = Math.min(menuWidth, Math.max(120, auxContentBounds.width));
        int maxHeight = Math.min(Math.max(contentHeight, rowHeight + padding),
                Math.min(240, Math.max(70, auxContentBounds.height - 24)));
        int menuX = clamp(recordingCategoryBounds.x, auxContentBounds.x,
                Math.max(auxContentBounds.x, auxContentBounds.right() - menuWidth));
        int menuY = recordingCategoryBounds.bottom() + 3;
        if (menuY + maxHeight > auxContentBounds.bottom()) {
            menuY = recordingCategoryBounds.y - maxHeight - 3;
        }
        if (menuY < auxContentBounds.y) {
            menuY = auxContentBounds.y;
            maxHeight = Math.min(maxHeight, Math.max(1, auxContentBounds.bottom() - menuY));
        }
        recordingCategoryPickerBounds = new ModernMainLayout.Rect(menuX, menuY, menuWidth, maxHeight);
        recordingCategoryPickerMaxScroll = Math.max(0, contentHeight - maxHeight);
        recordingCategoryPickerScroll = clamp(recordingCategoryPickerScroll, 0, recordingCategoryPickerMaxScroll);

        ModernUiRenderer.drawPanel(menuX, menuY, menuWidth, maxHeight, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        if (recordingCategoryPickerBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.record.pick_category";
        }
        ModernUiRenderer.beginClip(recordingCategoryPickerBounds);
        int y = menuY + 3 - recordingCategoryPickerScroll;
        List<String> source = recordingCategorySource();
        if (source.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.record.category_none", menuX + 8, menuY + 6,
                    ModernUiRenderer.MUTED_TEXT, menuWidth - 16);
        } else {
            for (String category : source) {
                y = drawRecordingCategoryOption(font, category, "", false, menuX, ModernHoverScrollbar.contentWidth(menuWidth), y, rowHeight,
                        mouseX, mouseY);
                for (String sub : subCategoriesFor(category)) {
                    String nested = safe(sub).trim();
                    if (nested.isEmpty()) {
                        continue;
                    }
                    y = drawRecordingCategoryOption(font, category, nested, true, menuX, ModernHoverScrollbar.contentWidth(menuWidth), y, rowHeight,
                            mouseX, mouseY);
                }
            }
        }
        ModernUiRenderer.endClip();
        recordingCategoryScrollBar.draw(recordingCategoryPickerBounds, recordingCategoryPickerScroll,
                recordingCategoryPickerMaxScroll, maxHeight, contentHeight, mouseX, mouseY,
                value -> recordingCategoryPickerScroll = value);
    }

    private int drawRecordingCategoryOption(FontRenderer font, String category, String subCategory, boolean nested,
            int menuX, int menuWidth, int y, int rowHeight, int mouseX, int mouseY) {
        ModernMainLayout.Rect row = new ModernMainLayout.Rect(menuX + 3, y, menuWidth - 6 - (recordingCategoryPickerMaxScroll > 0 ? 14 : 0), rowHeight - 1);
        boolean selected = safe(category).equals(safe(recordingSaveCategory))
                && safe(subCategory).equals(safe(recordingSaveSubCategory));
        boolean hovered = recordingCategoryPickerBounds != null
                && recordingCategoryPickerBounds.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
        if (recordingCategoryPickerBounds != null && row.bottom() > recordingCategoryPickerBounds.y
                && row.y < recordingCategoryPickerBounds.bottom()) {
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                    selected ? ModernUiRenderer.SURFACE_PRESSED
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String label = nested ? subCategory : recordingCategoryRowLabel(category);
            ModernUiRenderer.drawText(font, label, row.x + (nested ? 16 : 7), row.y + 5,
                    selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(12, row.width - (nested ? 22 : 14)));
        }
        recordingCategoryHits.add(new NavHit(nested ? NavKind.SUBCATEGORY : NavKind.CATEGORY, category, subCategory,
                null, row, null));
        return y + rowHeight;
    }

    private boolean handleRecordingCategoryClick(int mouseX, int mouseY, int mouseButton) {
        if (recordingCategoryPickerOpen) {
            if (mouseButton != 0) {
                closeRecordingCategoryPicker();
                return true;
            }
            if (recordingCategoryPickerBounds != null && recordingCategoryPickerBounds.contains(mouseX, mouseY)) {
                for (NavHit hit : recordingCategoryHits) {
                    if (hit.bounds != null && hit.bounds.contains(mouseX, mouseY)) {
                        applyRecordingCategorySelection(hit.category, hit.subCategory);
                        return true;
                    }
                }
                return true;
            }
            if (recordingCategoryBounds != null && recordingCategoryBounds.contains(mouseX, mouseY)) {
                closeRecordingCategoryPicker();
                return true;
            }
            closeRecordingCategoryPicker();
            return true;
        }
        if (mouseButton == 0 && recordingCategoryBounds != null && recordingCategoryBounds.contains(mouseX, mouseY)) {
            recordingCategoryPickerOpen = true;
            recordingCategoryPickerScroll = 0;
            return true;
        }
        return false;
    }

    private void applyRecordingCategorySelection(String category, String subCategory) {
        recordingSaveCategory = safe(category).trim();
        if (recordingSaveCategory.isEmpty()) {
            recordingSaveCategory = defaultCategory();
        }
        recordingSaveSubCategory = safe(subCategory).trim();
        if (recordingDraft != null) {
            recordingDraft.setCategory(recordingSaveCategory);
            recordingDraft.setSubCategory(recordingSaveSubCategory);
        }
        closeRecordingCategoryPicker();
    }

    private void closeRecordingCategoryPicker() {
        recordingCategoryScrollBar.endDrag();
        recordingCategoryScrollBar.idle();
        recordingCategoryPickerOpen = false;
        recordingCategoryPickerBounds = null;
        recordingCategoryHits.clear();
    }

    private String recordingCategoryOrDefault() {
        String category = safe(recordingSaveCategory).trim();
        return category.isEmpty() ? defaultCategory() : category;
    }

    private void toggleRecordingPacketAction(ActionData action) {
        if (action == null || !isPacketToggleAction(action)) {
            return;
        }
        if (RecordingPacketSupport.hasOriginalAction(action.params)) {
            if (!canEditStep()) {
                return;
            }
            pushHistory("restore-recorded-input-action");
            String originalType = action.params.get(RecordingPacketSupport.ORIGINAL_TYPE_KEY).getAsString();
            JsonObject originalParams = action.params.get(RecordingPacketSupport.ORIGINAL_PARAMS_KEY).getAsJsonObject();
            action.type = originalType;
            action.params = RecordingPacketSupport.copyObject(originalParams);
            bindEditorFields();
            dirty = true;
            status("gui.modern.path.record.packet_restored");
            return;
        }
        openRecordingPacketOverlay(action);
    }

    private void openRecordingPacketOverlay(ActionData action) {
        if (action == null) {
            return;
        }
        recordingPacketAction = action;
        recordingPacketDirection = RecordingPacketSupport.C2S;
        recordingPacketWorkbench = new PacketWorkbenchTab(minecraft, null);
        openRecordingPacketViewer();
    }

    private void openRecordingPacketViewer() {
        if (recordingPacketWorkbench == null || recordingPacketAction == null) {
            return;
        }
        List<PacketCaptureHandler.CapturedPacketData> packets = RecordingPacketSupport.packetsForDirection(
                recordingPacketAction.params, recordingPacketDirection);
        long focusTimestamp = 0L;
        if (recordingPacketAction.params != null
                && recordingPacketAction.params.has(RecordingPacketSupport.INPUT_TIMESTAMP_KEY)) {
            try {
                focusTimestamp = recordingPacketAction.params
                        .get(RecordingPacketSupport.INPUT_TIMESTAMP_KEY).getAsLong();
            } catch (Exception ignored) {
                // Older or manually edited actions may not have a valid timestamp.
            }
        }
        String title = RecordingPacketSupport.C2S.equals(recordingPacketDirection)
                ? "gui.modern.path.record.packet_send" : "gui.modern.path.record.packet_receive";
        recordingPacketWorkbench.openViewer(packets, title, true, recordingPacketDirection, true, focusTimestamp);
    }

    private void closeRecordingPacketOverlay() {
        if (recordingPacketWorkbench != null) {
            recordingPacketWorkbench.discardDraft();
        }
        recordingPacketWorkbench = null;
        recordingPacketAction = null;
        recordingPacketOverlayBounds = null;
        recordingPacketViewerBounds = null;
        recordingPacketCloseBounds = null;
        recordingPacketSendBounds = null;
        recordingPacketReceiveBounds = null;
        recordingPacketReplaceBounds = null;
    }

    private void drawRecordingPacketOverlay(FontRenderer font, int mouseX, int mouseY) {
        if (bounds == null || recordingPacketWorkbench == null) {
            return;
        }
        ModernUiRenderer.drawBackdropOverlay(bounds, 0xB0000000);
        int maxWidth = Math.max(1, bounds.width - 18);
        int maxHeight = Math.max(1, bounds.height - 24);
        int width = Math.min(maxWidth, Math.max(520, bounds.width - 64));
        int height = Math.min(maxHeight, Math.max(320, bounds.height - 54));
        int x = bounds.x + (bounds.width - width) / 2;
        int y = bounds.y + (bounds.height - height) / 2;
        recordingPacketOverlayBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.ACCENT);
        ModernUiRenderer.drawText(font, "gui.modern.path.record.packet_browser", x + 12, y + 9,
                ModernUiRenderer.TEXT, Math.max(80, width - 300));
        int buttonY = y + 8;
        int buttonX = x + Math.max(150, width / 3);
        recordingPacketSendBounds = new ModernMainLayout.Rect(buttonX, buttonY, 86, 22);
        recordingPacketReceiveBounds = new ModernMainLayout.Rect(buttonX + 90, buttonY, 86, 22);
        drawButton(font, recordingPacketSendBounds, "gui.modern.path.record.packet_send",
                RecordingPacketSupport.C2S.equals(recordingPacketDirection), false, true, mouseX, mouseY);
        drawButton(font, recordingPacketReceiveBounds, "gui.modern.path.record.packet_receive",
                RecordingPacketSupport.S2C.equals(recordingPacketDirection), false, true, mouseX, mouseY);
        int closeX = x + width - 12 - 22;
        recordingPacketCloseBounds = new ModernMainLayout.Rect(closeX, buttonY, 22, 22);
        boolean closeHover = recordingPacketCloseBounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(closeX, buttonY, 22, 22, 5,
                closeHover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                closeHover ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawCloseIcon(closeX + 6, buttonY + 6,
                closeHover ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        PacketCaptureHandler.CapturedPacketData selected = recordingPacketWorkbench.selectedPacket();
        int replaceWidth = 118;
        recordingPacketReplaceBounds = new ModernMainLayout.Rect(closeX - replaceWidth - 8, buttonY, replaceWidth, 22);
        drawButton(font, recordingPacketReplaceBounds, "gui.modern.path.record.packet_replace", true, false,
                selected != null && recordingPacketAction != null, mouseX, mouseY);
        recordingPacketViewerBounds = new ModernMainLayout.Rect(x + 10, y + 35, width - 20, height - 47);
        recordingPacketWorkbench.draw(font, recordingPacketViewerBounds, mouseX, mouseY);
    }

    private boolean handleRecordingPacketOverlayClick(int mouseX, int mouseY, int mouseButton) {
        if (recordingPacketOverlayBounds == null || !recordingPacketOverlayBounds.contains(mouseX, mouseY)) {
            closeRecordingPacketOverlay();
            return true;
        }
        if (mouseButton == 0 && recordingPacketCloseBounds != null
                && recordingPacketCloseBounds.contains(mouseX, mouseY)) {
            closeRecordingPacketOverlay();
            return true;
        }
        if (mouseButton == 0 && recordingPacketSendBounds != null
                && recordingPacketSendBounds.contains(mouseX, mouseY)) {
            recordingPacketDirection = RecordingPacketSupport.C2S;
            openRecordingPacketViewer();
            return true;
        }
        if (mouseButton == 0 && recordingPacketReceiveBounds != null
                && recordingPacketReceiveBounds.contains(mouseX, mouseY)) {
            recordingPacketDirection = RecordingPacketSupport.S2C;
            openRecordingPacketViewer();
            return true;
        }
        if (mouseButton == 0 && recordingPacketReplaceBounds != null
                && recordingPacketReplaceBounds.contains(mouseX, mouseY)) {
            PacketCaptureHandler.CapturedPacketData packet = recordingPacketWorkbench.selectedPacket();
            if (packet != null && recordingPacketAction != null) {
                replaceRecordingActionWithPacket(recordingPacketAction, packet, recordingPacketDirection);
                closeRecordingPacketOverlay();
            }
            return true;
        }
        if (recordingPacketViewerBounds != null && recordingPacketViewerBounds.contains(mouseX, mouseY)) {
            recordingPacketWorkbench.mouseClicked(mouseX, mouseY, mouseButton);
            if (!recordingPacketWorkbench.isViewerOpen()) {
                closeRecordingPacketOverlay();
            }
            return true;
        }
        return true;
    }

    private void replaceRecordingActionWithPacket(ActionData action,
            PacketCaptureHandler.CapturedPacketData packet, String direction) {
        if (action == null || packet == null || !canEditStep()) {
            return;
        }
        pushHistory("replace-recorded-input-with-packet");
        JsonObject originalParams = RecordingPacketSupport.copyObject(action.params);
        JsonObject packetParams = RecordingPacketSupport.sendPacketParams(packet, direction);
        packetParams.addProperty(RecordingPacketSupport.ORIGINAL_TYPE_KEY, safe(action.type));
        packetParams.add(RecordingPacketSupport.ORIGINAL_PARAMS_KEY, originalParams);
        action.type = "send_packet";
        action.params = packetParams;
        bindEditorFields();
        dirty = true;
        status("gui.modern.path.record.packet_replaced");
    }

    private boolean isRecordingTool(PathWorkbenchToolCatalog.ToolId tool) {
        return tool != null && (tool.zone == PathWorkbenchToolCatalog.Zone.RECORDING_STEP
                || tool.zone == PathWorkbenchToolCatalog.Zone.RECORDING_ACTION
                || tool.zone == PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL);
    }

    private String toolLabel(PathWorkbenchToolCatalog.ToolId tool) {
        if (tool == PathWorkbenchToolCatalog.ToolId.RECORDING_PAUSE && PathRecordingManager.isPaused()) {
            return "gui.modern.path.wb.u240";
        }
        if (tool == PathWorkbenchToolCatalog.ToolId.RECORDING_RUN_PAUSE && PathRecordingPlayback.isPaused()) {
            return "gui.modern.path.record.resume_run";
        }
        return tool.label;
    }

    private void drawRecordingToolStrip(FontRenderer font, ModernMainLayout.Rect area,
            PathWorkbenchToolCatalog.Zone zone, int mouseX, int mouseY) {
        recordingSettingsHits.put(zone, drawToolStrip(font, area, zone.title,
                zone, PathWorkbenchToolCatalog.get().pinned(zone), mouseX, mouseY));
    }

    private void drawRecordingOverflow(PathWorkbenchToolCatalog.Zone zone, ModernMainLayout.Rect rect,
            int mouseX, int mouseY) {
        if (!PathWorkbenchToolCatalog.get().overflow(zone).isEmpty()) {
            recordingOverflowHits.put(zone, rect);
            drawIconButton(rect, "...", mouseX, mouseY, "gui.modern.path.record.more");
        }
    }

    private boolean isRecordingToolEnabled(PathWorkbenchToolCatalog.ToolId tool) {
        if (auxiliaryView != AuxiliaryView.RECORDING) return false;
        boolean hasDraft = recordingDraft != null && !recordingDraft.getSteps().isEmpty();
        boolean running = PathRecordingPlayback.isRunning();
        switch (tool) {
            case RECORDING_START: return !PathRecordingManager.isRecording() && !running;
            case RECORDING_PAUSE: return PathRecordingManager.isRecording() && !running;
            case RECORDING_FINISH: return PathRecordingManager.isRecording();
            case RECORDING_SAVE: return hasDraft;
            case RECORDING_RUN: return hasDraft && !running && minecraft != null && minecraft.player != null;
            case RECORDING_RUN_PAUSE:
            case RECORDING_RUN_STOP: return running;
            case RECORDING_CLEAR: return hasDraft || PathRecordingManager.isRecording();
            case RECORDING_UNDO: return !recordingUndoHistory.isEmpty();
            case RECORDING_REDO: return !recordingRedoHistory.isEmpty();
            case RECORDING_STEP_ADD: return recordingDraft != null;
            case RECORDING_STEP_GET_COORDS: return canEditStep() && minecraft != null && minecraft.player != null;
            case RECORDING_STEP_RUN: return canEditStep() && !running && minecraft != null && minecraft.player != null;
            case RECORDING_STEP_DELETE:
            case RECORDING_STEP_CLEAR_COORDS:
            case RECORDING_ACTION_ADD: return canEditStep();
            case RECORDING_STEP_UP: return canEditStep() && canMoveSelectedSteps(-1);
            case RECORDING_STEP_DOWN: return canEditStep() && canMoveSelectedSteps(1);
            case RECORDING_ACTION_REPLACE:
            case RECORDING_ACTION_DELETE: return canEditStep() && selectedAction != null;
            case RECORDING_ACTION_UP: return canEditStep() && canMoveSelectedActions(-1);
            case RECORDING_ACTION_DOWN: return canEditStep() && canMoveSelectedActions(1);
            default: return false;
        }
    }

    private void runRecordingTool(PathWorkbenchToolCatalog.ToolId tool) {
        syncEditorFields();
        syncAuxiliaryFields();
        syncRecordingDraft();
        if (!isRecordingToolEnabled(tool)) return;
        switch (tool) {
            case RECORDING_STEP_ADD: insertRecordingStep(); break;
            case RECORDING_STEP_DELETE: deleteStep(); break;
            case RECORDING_STEP_GET_COORDS: captureStepCoordinates(); break;
            case RECORDING_STEP_CLEAR_COORDS: runTool(PathWorkbenchToolCatalog.ToolId.STEP_CLEAR_COORDS); break;
            case RECORDING_STEP_UP: moveStep(-1); break;
            case RECORDING_STEP_DOWN: moveStep(1); break;
            case RECORDING_STEP_RUN: runRecordingFromStep(selectedStepIndex); break;
            case RECORDING_ACTION_ADD: openPalette(lastMouseX, lastMouseY); break;
            case RECORDING_ACTION_REPLACE:
                openPalette(lastMouseX, lastMouseY);
                recordingReplacementTarget = selectedAction;
                break;
            case RECORDING_ACTION_DELETE: deleteAction(); break;
            case RECORDING_ACTION_UP: moveAction(-1); break;
            case RECORDING_ACTION_DOWN: moveAction(1); break;
            case RECORDING_START:
                clearRecordingDraft();
                PathRecordingManager.startRecording();
                minecraft.displayGuiScreen(null);
                break;
            case RECORDING_PAUSE: PathRecordingManager.togglePaused(); break;
            case RECORDING_FINISH: PathRecordingManager.finishRecording(); break;
            case RECORDING_SAVE: saveRecordedSequence(); break;
            case RECORDING_RUN: runRecordingFromStep(0); break;
            case RECORDING_RUN_PAUSE:
                PathRecordingPlayback.togglePaused();
                if (!PathRecordingPlayback.isPaused()) minecraft.displayGuiScreen(null);
                break;
            case RECORDING_RUN_STOP: PathRecordingPlayback.stop(); break;
            case RECORDING_UNDO: undo(); break;
            case RECORDING_REDO: redo(); break;
            case RECORDING_CLEAR: clearRecordingDraft(); break;
            default: break;
        }
    }

    private void clearRecordingDraft() {
        PathRecordingPlayback.stop();
        PathRecordingManager.finishRecording();
        PathRecordingManager.getRecordedSteps().clear();
        recordingDraft = null;
        recordingDraftStepCount = -1;
        recordingDraftActionCount = -1;
        recordingDraftRevision = -1L;
        recordingDraftManuallyEdited = false;
        importedRecordingActions.clear();
        recordingUndoHistory.clear();
        recordingRedoHistory.clear();
        selectedStepIndex = -1;
        selectedActionIndex = -1;
        recordingScroll = 0;
        actionScroll = 0;
        syncRecordingDraft();
        status("gui.modern.path.wb.u274");
    }

    private void runRecordingFromStep(int startStepIndex) {
        syncEditorFields();
        syncRecordingDraft();
        if (recordingDraft == null || startStepIndex < 0 || startStepIndex >= recordingDraft.getSteps().size()
                || PathRecordingPlayback.isRunning()) return;
        // Validate against known sequence references without adding the draft to the saved sequence list.
        PathSequence preview = new PathSequence(recordingDraft, false);
        preview.setName(uniqueSequenceName(tr("gui.modern.path.record.preview")));
        List<PathSequence> validationScope = new ArrayList<>(sequences);
        validationScope.add(preview);
        for (Issue issue : PathConfigValidator.validateSequences(validationScope)) {
            if (issue.getSeverity() == PathConfigValidator.Severity.ERROR
                    && preview.getName().equals(issue.getSequenceName())) {
                if (issue.getStepIndex() >= 0) {
                    selectStep(issue.getStepIndex());
                    if (issue.getActionIndex() >= 0) selectAction(issue.getActionIndex());
                }
                status(tr("gui.modern.path.record.invalid", issue.getSummary()));
                return;
            }
        }
        if (PathRecordingPlayback.start(preview, startStepIndex)) {
            status(tr("gui.modern.path.wb.fmt.run_from", String.valueOf(startStepIndex + 1)));
            minecraft.displayGuiScreen(null);
        }
    }

    private boolean handleRecordingToolsClick(int mouseX, int mouseY) {
        if (recordingStepToolsToggleBounds != null
                && recordingStepToolsToggleBounds.contains(mouseX, mouseY)) {
            recordingStepToolsExpanded = !recordingStepToolsExpanded;
            return true;
        }
        if (recordingActionToolsToggleBounds != null
                && recordingActionToolsToggleBounds.contains(mouseX, mouseY)) {
            recordingActionToolsExpanded = !recordingActionToolsExpanded;
            return true;
        }
        if (recordingControlToolsToggleBounds != null
                && recordingControlToolsToggleBounds.contains(mouseX, mouseY)) {
            recordingControlToolsExpanded = !recordingControlToolsExpanded;
            return true;
        }
        for (Map.Entry<PathWorkbenchToolCatalog.Zone, ModernMainLayout.Rect> entry : recordingSettingsHits.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                openCustomizePanel(entry.getKey(), entry.getValue());
                return true;
            }
        }
        for (Map.Entry<PathWorkbenchToolCatalog.Zone, ModernMainLayout.Rect> entry : recordingOverflowHits.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                openOverflowMenu(entry.getKey(), mouseX, mouseY);
                return true;
            }
        }
        for (Map.Entry<String, ModernMainLayout.Rect> entry : toolHits.entrySet()) {
            if (entry.getValue().contains(mouseX, mouseY)) {
                PathWorkbenchToolCatalog.ToolId tool = toolFromKey(entry.getKey());
                if (isRecordingTool(tool)) runRecordingTool(tool);
                return true;
            }
        }
        return false;
    }

    private void openRecordingContextMenu(boolean stepMenu, int mouseX, int mouseY) {
        PathWorkbenchToolCatalog.Zone zone = stepMenu ? PathWorkbenchToolCatalog.Zone.RECORDING_STEP
                : PathWorkbenchToolCatalog.Zone.RECORDING_ACTION;
        menuTitle = zone.title;
        menuItems.clear();
        for (PathWorkbenchToolCatalog.ToolId tool : PathWorkbenchToolCatalog.get().all(zone)) {
            menuItems.add(new RectAction(toolLabel(tool), tool.shortcut, isToolEnabled(tool), tool.danger,
                    () -> runTool(tool)));
        }
        setMenuAnchor(mouseX, mouseY);
    }

    private void syncRecordingDraft() {
        List<PathRecordingManager.RecordedStep> recorded = PathRecordingManager.getRecordedSteps();
        int actionCount = 0;
        for (PathRecordingManager.RecordedStep step : recorded) {
            if (step != null) actionCount += step.getActions().size();
        }
        if (recordingDraft == null || recordingDraftRevision != PathRecordingManager.getRevision()) {
            if (recordingDraft != null && recordingDraftManuallyEdited) {
                mergeRecordingCapture(recorded);
                recordingDraftRevision = PathRecordingManager.getRevision();
                bindEditorFields();
                return;
            }
            int oldStep = selectedStepIndex;
            int oldAction = selectedActionIndex;
            String name = fields.get("recording.name") == null ? tr("gui.modern.path.wb.u204") : fields.get("recording.name").getText();
            String category = recordingCategoryOrDefault();
            recordingDraft = buildRecordedSequence(name, category, recorded);
            recordingDraft.setSubCategory(safe(recordingSaveSubCategory).trim());
            importedRecordingActions.clear();
            for (PathRecordingManager.RecordedStep captured : recorded) {
                if (captured != null) importedRecordingActions.addAll(captured.getActions());
            }
            if (!recordingDraft.getSteps().isEmpty()
                    && Double.isNaN(recordingDraft.getSteps().get(recordingDraft.getSteps().size() - 1).getGotoPoint()[0])) {
                recordingDraft.getSteps().remove(recordingDraft.getSteps().size() - 1);
            }
            recordingDraftStepCount = recorded.size();
            recordingDraftActionCount = actionCount;
            recordingDraftRevision = PathRecordingManager.getRevision();
            selectedSequence = recordingDraft;
            selectedSequenceName = recordingDraft.getName();
            selectedStepIndex = recordingDraft.getSteps().isEmpty() ? -1 : clamp(oldStep < 0 ? 0 : oldStep, 0,
                    recordingDraft.getSteps().size() - 1);
            selectedStep = selectedStepIndex < 0 ? null : recordingDraft.getSteps().get(selectedStepIndex);
            selectedActionIndex = selectedStep == null || selectedStep.getActions().isEmpty() ? -1
                    : clamp(oldAction < 0 ? 0 : oldAction, 0, selectedStep.getActions().size() - 1);
            selectedAction = selectedActionIndex < 0 ? null : selectedStep.getActions().get(selectedActionIndex);
            syncStepSelectionSet();
            syncActionSelectionSet();
            bindEditorFields();
        }
    }

    private void mergeRecordingCapture(List<PathRecordingManager.RecordedStep> recorded) {
        if (recordingDraft == null) return;
        for (PathRecordingManager.RecordedStep captured : recorded) {
            if (captured == null || captured.playerPos == null) continue;
            // Deleted/coordinate-cleared captures must not be recreated by a later recording event.
            boolean hasNewActions = false;
            for (ActionData action : captured.getActions()) {
                mergePacketAssociationIntoDraft(action);
                if (action != null && !importedRecordingActions.contains(action)) {
                    hasNewActions = true;
                    break;
                }
            }
            if (!hasNewActions) continue;
            PathStep target = null;
            for (PathStep candidate : recordingDraft.getSteps()) {
                if (sameRecordingPosition(candidate == null ? null : candidate.getGotoPoint(), captured.playerPos)) {
                    target = candidate;
                    break;
                }
            }
            if (target == null) {
                target = new PathStep(new double[] { round(captured.playerPos.x), round(captured.playerPos.y),
                        round(captured.playerPos.z) });
                recordingDraft.addStep(target);
            }
            for (ActionData action : captured.getActions()) {
                if (action != null && importedRecordingActions.add(action)) {
                    target.addAction(new ActionData(action, true));
                }
            }
        }
        selectedStepIndex = recordingDraft.getSteps().isEmpty() ? -1
                : clamp(selectedStepIndex, 0, recordingDraft.getSteps().size() - 1);
        selectedStep = selectedStepIndex < 0 ? null : recordingDraft.getSteps().get(selectedStepIndex);
        selectedActionIndex = selectedStep == null || selectedStep.getActions().isEmpty() ? -1
                : clamp(selectedActionIndex, 0, selectedStep.getActions().size() - 1);
        selectedAction = selectedActionIndex < 0 ? null : selectedStep.getActions().get(selectedActionIndex);
        syncStepSelectionSet();
        syncActionSelectionSet();
    }

    private void mergePacketAssociationIntoDraft(ActionData source) {
        if (recordingDraft == null || source == null || source.params == null
                || !source.params.has(RecordingPacketSupport.INPUT_TIMESTAMP_KEY)) {
            return;
        }
        long timestamp;
        try {
            timestamp = source.params.get(RecordingPacketSupport.INPUT_TIMESTAMP_KEY).getAsLong();
        } catch (Exception ignored) {
            return;
        }
        for (PathStep step : recordingDraft.getSteps()) {
            if (step == null) continue;
            for (ActionData target : step.getActions()) {
                if (target == null || !safe(target.type).equalsIgnoreCase(safe(source.type))
                        || target.params == null || !target.params.has(RecordingPacketSupport.INPUT_TIMESTAMP_KEY)) {
                    continue;
                }
                try {
                    if (target.params.get(RecordingPacketSupport.INPUT_TIMESTAMP_KEY).getAsLong() != timestamp) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
                JsonObject sourceCopy = RecordingPacketSupport.copyObject(source.params);
                if (source.params.has(RecordingPacketSupport.PACKETS_KEY)) {
                    target.params.add(RecordingPacketSupport.PACKETS_KEY,
                            sourceCopy.get(RecordingPacketSupport.PACKETS_KEY));
                }
                if (source.params.has(RecordingPacketSupport.WINDOW_SECONDS_KEY)) {
                    target.params.add(RecordingPacketSupport.WINDOW_SECONDS_KEY,
                            sourceCopy.get(RecordingPacketSupport.WINDOW_SECONDS_KEY));
                }
                if (source.params.has(RecordingPacketSupport.RANGE_KEY)) {
                    target.params.add(RecordingPacketSupport.RANGE_KEY,
                            sourceCopy.get(RecordingPacketSupport.RANGE_KEY));
                }
                return;
            }
        }
    }

    private boolean sameRecordingPosition(double[] point, net.minecraft.util.math.Vec3d position) {
        return point != null && point.length >= 3 && position != null
                && Math.floor(point[0]) == Math.floor(position.x)
                && Math.floor(point[1]) == Math.floor(position.y)
                && Math.floor(point[2]) == Math.floor(position.z);
    }

    private void drawRecordingStepPane(FontRenderer font, ModernMainLayout.Rect pane, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(pane.x, pane.y, pane.width, pane.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u243", pane.x + 9, pane.y + 8, ModernUiRenderer.TEXT, pane.width - 18);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.n_pos", String.valueOf(recordingDraft == null ? 0 : recordingDraft.getSteps().size())),
                pane.x + 9, pane.y + 22, ModernUiRenderer.MUTED_TEXT, pane.width - 18);
        drawRecordingOverflow(PathWorkbenchToolCatalog.Zone.RECORDING_STEP,
                new ModernMainLayout.Rect(pane.right() - 25, pane.y + 7, 18, 18), mouseX, mouseY);
        int stripHeight = PathWorkbenchToolCatalog.get().stripHeight(
                PathWorkbenchToolCatalog.Zone.RECORDING_STEP, pane.width - 26, false);
        if (!recordingStepToolsExpanded) {
            stripHeight = TOOL_STRIP_HEADER_HEIGHT;
        }
        drawRecordingToolStrip(font, new ModernMainLayout.Rect(pane.x + 5, pane.bottom() - stripHeight - 5,
                pane.width - 10, stripHeight), PathWorkbenchToolCatalog.Zone.RECORDING_STEP, mouseX, mouseY);
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(pane.x + 5, pane.y + 42, pane.width - 10,
                Math.max(1, pane.height - 52 - stripHeight));
        stepListBounds = list;
        recordingStepHits.clear();
        int total = recordingDraft == null ? 0 : recordingDraft.getSteps().size();
        int visible = Math.max(1, list.height / STEP_ROW_HEIGHT);
        recordingMaxScroll = Math.max(0, total - visible);
        recordingScroll = clamp(recordingScroll, 0, recordingMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y - recordingScroll * STEP_ROW_HEIGHT;
        for (int i = 0; i < total; i++) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x + 2, y, ModernHoverScrollbar.contentWidth(list.width - 2), STEP_ROW_HEIGHT - 3);
            PathStep step = recordingDraft.getSteps().get(i);
            boolean selected = i == selectedStepIndex;
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : row.contains(mouseX, mouseY)
                            ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            double[] point = step.getGotoPoint();
            String coordinate = !step.hasGotoTarget() ? "gui.modern.path.wb.u245"
                    : String.format(Locale.ROOT, "%.0f, %.0f, %.0f", point[0], point[1], point[2]);
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.step_n", String.valueOf(i + 1)), row.x + 8, row.y + 5,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 16);
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.coord_actions", tr(coordinate), String.valueOf(step.getActions().size())), row.x + 8,
                    row.y + 17, ModernUiRenderer.MUTED_TEXT, row.width - 16);
            if (row.bottom() > list.y && row.y < list.bottom()) recordingStepHits.add(new RowHit(i, row));
            y += STEP_ROW_HEIGHT;
        }
        ModernUiRenderer.endClip();
        if (total == 0) ModernUiRenderer.drawText(font, "gui.modern.path.wb.u246", list.x + 10, list.y + list.height / 2,
                ModernUiRenderer.MUTED_TEXT, list.width - 20);
        drawScrollBarFor(recordingScrollBar, list, recordingScroll, recordingMaxScroll, total, visible, mouseX, mouseY,
                value -> recordingScroll = value);
    }

    private void drawRecordingActionPane(FontRenderer font, ModernMainLayout.Rect pane, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(pane.x, pane.y, pane.width, pane.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u247", pane.x + 9, pane.y + 8, ModernUiRenderer.TEXT, pane.width - 18);
        ModernUiRenderer.drawText(font, selectedStep == null ? "gui.modern.path.wb.u248" : tr("gui.modern.path.wb.fmt.n_actions", String.valueOf(selectedStep.getActions().size())),
                pane.x + 9, pane.y + 22, ModernUiRenderer.MUTED_TEXT, pane.width - 18);
        drawRecordingOverflow(PathWorkbenchToolCatalog.Zone.RECORDING_ACTION,
                new ModernMainLayout.Rect(pane.right() - 25, pane.y + 7, 18, 18), mouseX, mouseY);
        int stripHeight = PathWorkbenchToolCatalog.get().stripHeight(
                PathWorkbenchToolCatalog.Zone.RECORDING_ACTION, pane.width - 26, false);
        if (!recordingActionToolsExpanded) {
            stripHeight = TOOL_STRIP_HEADER_HEIGHT;
        }
        drawRecordingToolStrip(font, new ModernMainLayout.Rect(pane.x + 5, pane.bottom() - stripHeight - 5,
                pane.width - 10, stripHeight), PathWorkbenchToolCatalog.Zone.RECORDING_ACTION, mouseX, mouseY);
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(pane.x + 5, pane.y + 42, pane.width - 10,
                Math.max(1, pane.height - 52 - stripHeight));
        actionListBounds = list;
        recordingActionHits.clear();
        recordingPacketToggleHits.clear();
        int total = selectedStep == null ? 0 : selectedStep.getActions().size();
        int visible = Math.max(1, list.height / ACTION_ROW_HEIGHT);
        actionMaxScroll = Math.max(0, total - visible);
        actionScroll = clamp(actionScroll, 0, actionMaxScroll);
        ModernUiRenderer.beginClip(list);
        int y = list.y - actionScroll * ACTION_ROW_HEIGHT;
        for (int i = 0; i < total; i++) {
            ActionData action = selectedStep.getActions().get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x + 2, y, ModernHoverScrollbar.contentWidth(list.width - 2), ACTION_ROW_HEIGHT - 3);
            boolean selected = selectedActionIndices.contains(Integer.valueOf(i));
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : row.contains(mouseX, mouseY)
                            ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? actionAccent(action.type) : ModernUiRenderer.BORDER_SUBTLE);
            ModernMainLayout.Rect packetToggle = packetToggleBounds(row);
            drawPacketToggleIcon(packetToggle, action, mouseX, mouseY);
            recordingPacketToggleHits.add(new RowHit(i, packetToggle));
            ModernUiRenderer.drawText(font, (i + 1) + ". " + prettyType(action.type), row.x + 8, row.y + 4,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, packetToggle.x - row.x - 12);
            ModernUiRenderer.drawText(font, safeActionDescription(action), row.x + 8, row.y + 16,
                    ModernUiRenderer.MUTED_TEXT, packetToggle.x - row.x - 12);
            if (row.bottom() > list.y && row.y < list.bottom()) recordingActionHits.add(new RowHit(i, row));
            y += ACTION_ROW_HEIGHT;
        }
        ModernUiRenderer.endClip();
        if (total == 0) ModernUiRenderer.drawText(font, "gui.modern.path.wb.u249", list.x + 10, list.y + list.height / 2,
                ModernUiRenderer.MUTED_TEXT, list.width - 20);
        drawScrollBarFor(actionScrollBar, list, actionScroll, actionMaxScroll, total, visible, mouseX, mouseY,
                value -> actionScroll = value);
    }

    private void drawRecordingParameterEditor(FontRenderer font, ModernMainLayout.Rect pane, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(pane.x, pane.y, pane.width, pane.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        actionPageParamsBounds = pane;
        int x = pane.x + 12;
        int width = Math.max(1, pane.width - 24);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u206", x, pane.y + 9, ModernUiRenderer.TEXT, width);
        if (selectedAction == null) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u250", x, pane.y + 34, ModernUiRenderer.MUTED_TEXT, width);
            parameterHits.clear();
            expressionRowHits.clear();
            structuredListHits.clear();
            structuredListEditorHits.clear();
            return;
        }
        ModernUiRenderer.drawText(font, safe(selectedAction.getDescription()), x, pane.y + 27, ModernUiRenderer.MUTED_TEXT, width);
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(x, pane.y + 46, width, Math.max(1, pane.height - 76));
        actionPageParameterViewportBounds = viewport;
        parameterHits.clear();
        expressionRowHits.clear();
        slotGridHits.clear();
        structuredListHits.clear();
        structuredListEditorHits.clear();
        structuredListAddBounds = null;
        structuredListModeBounds = null;
        huntEntitySuggestionBounds = null;
        huntEntitySuggestionHits.clear();
        huntEntitySuggestionValues.clear();
        List<ModernActionEditorSchema.Field> schema = ModernActionEditorSchema.fields(selectedAction.type);
        int contentHeight = 0;
        String previousSection = "";
        for (ModernActionEditorSchema.Field field : schema) {
            int fieldHeight = schemaFieldHeight(field);
            if (fieldHeight <= 0) continue;
            if (!safe(field.section).equals(previousSection)) { contentHeight += 22; previousSection = safe(field.section); }
            contentHeight += fieldHeight;
        }
        parameterMaxScroll = Math.max(0, contentHeight - viewport.height);
        parameterScroll = clamp(parameterScroll, 0, parameterMaxScroll);
        ModernUiRenderer.beginClip(viewport);
        int y = viewport.y - parameterScroll;
        previousSection = "";
        for (ModernActionEditorSchema.Field field : schema) {
            int fieldHeight = schemaFieldHeight(field);
            if (fieldHeight <= 0) continue;
            if (!safe(field.section).equals(previousSection)) {
                ModernUiRenderer.drawText(font, safe(field.section), viewport.x + 4, y + 4,
                        isActionSectionEnabled(field.section) ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT,
                        Math.max(60, viewport.width - 8));
                previousSection = safe(field.section);
                y += 22;
            }
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(viewport.x, y, ModernHoverScrollbar.contentWidth(viewport.width), fieldHeight - 4);
            if (row.bottom() > viewport.y && row.y < viewport.bottom()) drawSchemaParameterRow(font, row, field, mouseX, mouseY);
            y += fieldHeight;
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(parameterScrollBar, viewport, parameterScroll, parameterMaxScroll,
                Math.max(1, contentHeight / PARAM_ROW_HEIGHT), Math.max(1, viewport.height / PARAM_ROW_HEIGHT),
                mouseX, mouseY, value -> parameterScroll = value);
        drawHuntEntityNameSuggestions(font, mouseX, mouseY);
    }

    /** Full-page native editor. The legacy editor's library/detail split is kept,
     * but all controls are rendered in the modern workbench coordinate space. */
    private void ensureActionPageLibrary() {
        if (!actionPageRoots.isEmpty() && actionPageRenderedRecentLimit == recentActionLimit()) {
            return;
        }
        rebuildActionPageLibrary();
    }

    private void rebuildActionPageLibrary() {
        actionPageRoots.clear();
        final Map<String, String> displayKeys = ActionDisplayCatalog.getActionDisplayKeys();
        ActionLibraryNode recent = new ActionLibraryNode();
        recent.id = "group_modern_recent_actions";
        recent.label = "gui.modern.path.wb.u251";
        LinkedHashSet<String> recentDedup = new LinkedHashSet<String>();
        int recentLimit = recentActionLimit();
        int recentCount = 0;
        for (String type : recentActionTypes) {
            String normalized = safe(type).trim().toLowerCase(Locale.ROOT);
            if (recentCount >= recentLimit) {
                break;
            }
            if (!normalized.isEmpty() && recentDedup.add(normalized) && displayKeys.containsKey(normalized)) {
                String key = displayKeys.get(normalized);
                String label = I18n.format(key);
                recent.children.add(ActionLibraryNode.item("recent_" + normalized,
                        safe(label).equals(key) || safe(label).isEmpty() ? prettyType(normalized) : label,
                        normalized));
                recentCount++;
            }
        }
        actionPageRoots.add(recent);
        actionPageRoots.addAll(ActionLibraryTreeFactory.buildRoots(null, null, null, type -> {
            String key = displayKeys.get(type);
            String label = key == null ? "" : I18n.format(key);
            return safe(label).equals(key) || safe(label).isEmpty() ? prettyType(type) : label;
        }));
        for (ActionLibraryNode root : actionPageRoots) {
            expandActionPageGroup(root);
        }
        actionPageRenderedRecentLimit = recentLimit;
    }

    private void expandActionPageGroup(ActionLibraryNode node) {
        if (node == null || !node.isGroup()) {
            return;
        }
        actionPageExpandedGroups.add(node.id);
        for (ActionLibraryNode child : node.children) {
            expandActionPageGroup(child);
        }
    }

    private String actionPageFilter() {
        ModernTextField field = fields.get("action.editor.search");
        return PinyinSearchHelper.normalizeQuery(field == null ? "" : field.getText());
    }

    private int recentActionLimit() {
        ModernTextField field = fields.get("action.editor.recent.limit");
        try {
            return clamp(Integer.parseInt(field == null ? "5" : field.getText().trim()), 1, 50);
        } catch (NumberFormatException ignored) {
            return 5;
        }
    }

    private void drawActionEditorPage(FontRenderer font, int mouseX, int mouseY) {
        ensureActionPageLibrary();
        systemMessageColorBounds.clear();
        systemMessageFormatBounds.clear();
        int gap = 8;
        int splitTotal = Math.max(2, auxContentBounds.width - gap);
        ModernSplitPane.Split actionPageSplit = ModernSplitPane.calculate(splitTotal, actionPageLibraryRatio,
                205, 240, 160, 160);
        actionPageLibraryRatio = actionPageSplit.ratio;
        int libraryWidth = actionPageSplit.firstWidth;
        actionPageLibraryBounds = new ModernMainLayout.Rect(auxContentBounds.x, auxContentBounds.y,
                libraryWidth, auxContentBounds.height);
        actionPageDividerBounds = ModernSplitPane.verticalDividerBounds(actionPageLibraryBounds.x,
                actionPageLibraryBounds.width, gap, auxContentBounds.y, auxContentBounds.height);
        actionPageParamsBounds = new ModernMainLayout.Rect(actionPageLibraryBounds.right() + gap, auxContentBounds.y,
                Math.max(1, actionPageSplit.secondWidth), auxContentBounds.height);
        ModernUiRenderer.drawSubtlePanel(actionPageLibraryBounds.x, actionPageLibraryBounds.y,
                actionPageLibraryBounds.width, actionPageLibraryBounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(actionPageParamsBounds.x, actionPageParamsBounds.y,
                actionPageParamsBounds.width, actionPageParamsBounds.height, 6, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernSplitPane.drawVerticalDivider(actionPageDividerBounds, mouseX, mouseY, draggingActionPageDivider);
        if (actionPageDividerBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.wb.u252";
        }
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u253", actionPageLibraryBounds.x + 10, actionPageLibraryBounds.y + 9,
                ModernUiRenderer.TEXT, actionPageLibraryBounds.width - 20);
        actionPageLocateActionBounds = new ModernMainLayout.Rect(actionPageLibraryBounds.right() - 58,
                actionPageLibraryBounds.y + 6, 48, 18);
        drawButton(font, actionPageLocateActionBounds, "gui.modern.path.wb.u254", false, false, selectedAction != null, mouseX, mouseY);
        actionPageLibrarySearchBounds = new ModernMainLayout.Rect(actionPageLibraryBounds.x + 8,
                actionPageLibraryBounds.y + 27, actionPageLibraryBounds.width - 16, 20);
        drawField(font, "action.editor.search", actionPageLibrarySearchBounds, true, "gui.modern.path.wb.u255",
                mouseX, mouseY);
        String query = actionPageFilter();
        actionPageRows.clear();
        actionPageRows.addAll(ActionLibraryViewSupport.buildVisibleRows(actionPageRoots,
                actionPageExpandedGroups, query));
        int listY = actionPageLibraryBounds.y + 55;
        int listHeight = Math.max(1, actionPageLibraryBounds.height - 64);
        int rowHeight = 25;
        int visible = Math.max(1, listHeight / rowHeight);
        paletteMaxScroll = Math.max(0, actionPageRows.size() - visible);
        actionPageScroll = clamp(actionPageScroll, 0, paletteMaxScroll);
        ModernUiRenderer.beginClip(new ModernMainLayout.Rect(actionPageLibraryBounds.x + 6, listY,
                actionPageLibraryBounds.width - 12, listHeight));
        for (int i = 0; i < actionPageRows.size(); i++) {
            ActionLibraryVisibleRow visibleRow = actionPageRows.get(i);
            ActionLibraryNode node = visibleRow.node;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(actionPageLibraryBounds.x + 8,
                    listY + i * rowHeight - actionPageScroll * rowHeight, ModernHoverScrollbar.contentWidth(actionPageLibraryBounds.width - 14), 22);
            boolean selected = selectedAction != null && node.actionType != null
                    && node.actionType.equalsIgnoreCase(selectedAction.type);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                            : node.isGroup() ? ModernUiRenderer.SURFACE : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            int indent = Math.min(42, visibleRow.depth * 12);
            if (node.isGroup()) {
                ModernUiRenderer.drawChevron(row.x + 7 + indent, row.y + 6,
                        !actionPageExpandedGroups.contains(node.id), ModernUiRenderer.ACCENT);
            } else {
                ModernUiRenderer.drawRoundedRect(row.x + 8 + indent, row.y + 7, 4, 8, 2,
                        actionAccent(node.actionType));
            }
            int labelRight = row.right() - 8;
            if ("group_modern_recent_actions".equals(node.id)) {
                actionPageRecentLimitBounds = new ModernMainLayout.Rect(row.right() - 54, row.y + 2, 46, 18);
                drawField(font, "action.editor.recent.limit", actionPageRecentLimitBounds, true, "5", mouseX, mouseY);
                labelRight = actionPageRecentLimitBounds.x - 6;
            }
            ModernUiRenderer.drawText(font, safe(node.label), row.x + 20 + indent, row.y + 6,
                    node.isGroup() ? ModernUiRenderer.TEXT : selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(20, labelRight - row.x - 20 - indent));
            if (!node.isGroup() && node.actionType != null) {
                int infoX = row.right() - 18;
                ModernMainLayout.Rect info = new ModernMainLayout.Rect(infoX, row.y + 4, 14, 14);
                boolean infoHover = info.contains(mouseX, mouseY);
                ModernUiRenderer.drawInfoIcon(info.x + 2, info.y + 2,
                        infoHover ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
                if (infoHover) {
                    hoveredTooltip = safe(node.label) + "\n用于编辑该动作的参数并加入当前序列。";
                }
            }
        }
        ModernUiRenderer.endClip();
        drawActionLibraryScrollbar(new ModernMainLayout.Rect(actionPageLibraryBounds.x + 6, listY,
                actionPageLibraryBounds.width - 12, listHeight), mouseX, mouseY);

        int x = actionPageParamsBounds.x + 16;
        int width = Math.max(1, actionPageParamsBounds.width - 32);
        ModernUiRenderer.drawRoundedRect(actionPageParamsBounds.x, actionPageParamsBounds.y, 3,
                actionPageParamsBounds.height, 2, selectedAction == null ? ModernUiRenderer.BORDER_SUBTLE
                        : actionAccent(selectedAction.type));
        ModernUiRenderer.drawText(font, selectedAction == null ? "gui.modern.path.wb.u256" : "gui.modern.path.wb.u256",
                x, actionPageParamsBounds.y + 10, ModernUiRenderer.TEXT, width);
        if (selectedAction == null) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u257", x,
                    actionPageParamsBounds.y + 38, ModernUiRenderer.MUTED_TEXT, width);
            actionPageBackBounds = new ModernMainLayout.Rect(actionPageParamsBounds.right() - 70,
                    actionPageParamsBounds.bottom() - 28, 58, 20);
            drawButton(font, actionPageBackBounds, "gui.modern.path.wb.u258", true, false, true, mouseX, mouseY);
            return;
        }
        if (selectedAction.params == null) {
            selectedAction.params = new JsonObject();
        }
        ModernUiRenderer.drawText(font, safe(selectedAction.getDescription()), x, actionPageParamsBounds.y + 28,
                ModernUiRenderer.MUTED_TEXT, width);
        actionTypeBounds = null;
        actionPageApplyTypeBounds = null;
        int viewportY = actionPageParamsBounds.y + 47;
        int viewportHeight = Math.max(1, actionPageParamsBounds.bottom() - 36 - viewportY);
        ModernMainLayout.Rect viewport = new ModernMainLayout.Rect(x, viewportY, width, viewportHeight);
        actionPageParameterViewportBounds = viewport;
        parameterHits.clear();
        expressionRowHits.clear();
        slotGridHits.clear();
        structuredListHits.clear();
        structuredListEditorHits.clear();
        structuredListAddBounds = null;
        structuredListModeBounds = null;
        huntEntitySuggestionBounds = null;
        huntEntitySuggestionHits.clear();
        huntEntitySuggestionValues.clear();
        List<ModernActionEditorSchema.Field> schema = ModernActionEditorSchema.fields(selectedAction.type);
        int contentHeight = 0;
        String previousSection = "";
        for (ModernActionEditorSchema.Field field : schema) {
            int fieldHeight = schemaFieldHeight(field);
            if (fieldHeight <= 0) continue;
            if (!safe(field.section).equals(previousSection)) {
                contentHeight += 22;
                previousSection = safe(field.section);
            }
            contentHeight += fieldHeight;
        }
        if (contentHeight == 0) {
            contentHeight = 88;
        }
        parameterMaxScroll = Math.max(0, contentHeight - viewport.height);
        parameterScroll = clamp(parameterScroll, 0, parameterMaxScroll);
        ModernUiRenderer.beginClip(viewport);
        int y = viewport.y - parameterScroll;
        previousSection = "";
        for (ModernActionEditorSchema.Field field : schema) {
            int fieldHeight = schemaFieldHeight(field);
            if (fieldHeight <= 0) continue;
            if (!safe(field.section).equals(previousSection)) {
                ModernUiRenderer.drawText(font, safe(field.section), viewport.x + 4, y + 4,
                        isActionSectionEnabled(field.section) ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT,
                        Math.max(60, viewport.width - 8));
                previousSection = safe(field.section);
                y += 22;
            }
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(viewport.x, y, ModernHoverScrollbar.contentWidth(viewport.width), fieldHeight - 4);
            if (row.bottom() > viewport.y && row.y < viewport.bottom()) {
                drawSchemaParameterRow(font, row, field, mouseX, mouseY);
            }
            y += fieldHeight;
        }
        if (schema.isEmpty() || y == viewport.y - parameterScroll) {
            ModernMainLayout.Rect empty = new ModernMainLayout.Rect(viewport.x, viewport.y + 8, viewport.width, 72);
            ModernUiRenderer.drawSubtlePanel(empty.x, empty.y, empty.width, empty.height, 5,
                    ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, ActionEditorUxSupport.emptyParamTitle(selectedAction.type),
                    empty.x + 12, empty.y + 14, ModernUiRenderer.TEXT, empty.width - 24);
            ModernUiRenderer.drawText(font, ActionEditorUxSupport.emptyParamHint(selectedAction.type),
                    empty.x + 12, empty.y + 34, ModernUiRenderer.MUTED_TEXT, empty.width - 24);
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(parameterScrollBar, viewport, parameterScroll, parameterMaxScroll,
                Math.max(1, contentHeight / PARAM_ROW_HEIGHT), Math.max(1, viewport.height / PARAM_ROW_HEIGHT),
                mouseX, mouseY, value -> parameterScroll = value);
        drawHuntEntityNameSuggestions(font, mouseX, mouseY);
        actionPageBackBounds = new ModernMainLayout.Rect(actionPageParamsBounds.right() - 70,
                actionPageParamsBounds.bottom() - 28, 58, 20);
        drawButton(font, actionPageBackBounds, "gui.modern.path.wb.u258", false, false, true, mouseX, mouseY);
        if (showActionWritingHelp) {
            drawActionWritingHelp(font, mouseX, mouseY);
        }
    }

    private void drawActionWritingHelp(FontRenderer font, int mouseX, int mouseY) {
        if (auxContentBounds == null) return;
        if (actionWritingHelpBounds == null) {
            int width = Math.min(700, Math.max(470, auxContentBounds.width - 36));
            int height = Math.min(390, Math.max(240, auxContentBounds.height - 44));
            int x = auxContentBounds.right() - width - 18;
            int y = auxContentBounds.y + 24;
            actionWritingHelpBounds = new ModernMainLayout.Rect(x, y, width, height);
        }
        ModernMainLayout.Rect b = actionWritingHelpBounds;
        ModernUiRenderer.drawRoundedRect(b.x - 2, b.y - 2, b.width + 4, b.height + 4, 7, 0xD9091118);
        ModernUiRenderer.drawSubtlePanel(b.x, b.y, b.width, b.height, 7, ModernUiRenderer.SHELL,
                ModernUiRenderer.ACCENT);
        actionWritingHelpHeaderBounds = new ModernMainLayout.Rect(b.x + 1, b.y + 1, b.width - 2, 30);
        ModernUiRenderer.drawRoundedRect(actionWritingHelpHeaderBounds.x, actionWritingHelpHeaderBounds.y,
                actionWritingHelpHeaderBounds.width, actionWritingHelpHeaderBounds.height, 6,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u349", b.x + 10, b.y + 8,
                ModernUiRenderer.TEXT, b.width - 48);
        actionWritingHelpCloseBounds = new ModernMainLayout.Rect(b.right() - 28, b.y + 6, 20, 18);
        drawButton(font, actionWritingHelpCloseBounds, "×", false, false, true, mouseX, mouseY);

        int contentY = b.y + 38;
        int gap = 6;
        int colWidth = Math.max(90, (b.width - 24 - gap * 2) / 3);
        String actionName = selectedAction == null ? tr("gui.modern.path.wb.u256")
                : safe(selectedAction.type);
        drawWritingHelpColumn(font, new ModernMainLayout.Rect(b.x + 8, contentY, colWidth, b.height - 54),
                "动作", Arrays.asList(actionName, "用途：编辑当前动作并加入序列。", "点击右侧字段可直接修改。"), mouseX, mouseY);
        List<String> parameterLines = new ArrayList<>();
        if (selectedAction != null) {
            for (ModernActionEditorSchema.Field field : ModernActionEditorSchema.fields(selectedAction.type)) {
                if (field == null || field.key == null || field.kind == ModernActionEditorSchema.Kind.SECTION) continue;
                String value = selectedAction.params == null ? "" : jsonValue(selectedAction.params.get(field.key));
                if (value.isEmpty()) value = safe(field.defaultValue);
                parameterLines.add(tr(field.label) + " = " + (value.isEmpty() ? "（示例）" : value));
            }
        }
        drawWritingHelpColumn(font, new ModernMainLayout.Rect(b.x + 8 + colWidth + gap, contentY, colWidth, b.height - 54),
                "参数示例", parameterLines, mouseX, mouseY);
        List<String> meaning = new ArrayList<>();
        meaning.add("分支表：每张卡片一行");
        meaning.add("键值 = 该分支动作数");
        meaning.add("例如 boss=2");
        meaning.add("遍历点：每个点单独添加");
        meaning.add("表达式：可用变量和函数");
        drawWritingHelpColumn(font, new ModernMainLayout.Rect(b.x + 8 + (colWidth + gap) * 2, contentY, colWidth, b.height - 54),
                "填写说明", meaning, mouseX, mouseY);
        ModernUiRenderer.drawRoundedRect(b.right() - 12, b.bottom() - 12, 8, 8, 2,
                resizingActionWritingHelp ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
    }

    private void drawWritingHelpColumn(FontRenderer font, ModernMainLayout.Rect rect, String title,
            List<String> lines, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, title, rect.x + 8, rect.y + 8, ModernUiRenderer.ACCENT,
                rect.width - 16);
        int y = rect.y + 27;
        if (lines != null) {
            for (String line : lines) {
                if (y > rect.bottom() - 12) break;
                ModernUiRenderer.drawText(font, safe(line), rect.x + 8, y, ModernUiRenderer.SUBTLE_TEXT,
                        rect.width - 16);
                y += 18;
            }
        }
    }

    private void drawExpressionEditorPage(FontRenderer font, int mouseX, int mouseY) {
        int gap = 8;
        int splitTotal = Math.max(2, auxContentBounds.width - gap);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, expressionEditorSplitRatio,
                210, 240, 160, 160);
        expressionEditorSplitRatio = split.ratio;
        ModernMainLayout.Rect templates = new ModernMainLayout.Rect(auxContentBounds.x, auxContentBounds.y,
                split.firstWidth, auxContentBounds.height);
        expressionEditorDividerBounds = ModernSplitPane.verticalDividerBounds(templates.x, templates.width, gap,
                auxContentBounds.y, auxContentBounds.height);
        ModernMainLayout.Rect editor = new ModernMainLayout.Rect(templates.right() + gap, auxContentBounds.y,
                Math.max(1, split.secondWidth), auxContentBounds.height);
        ModernUiRenderer.drawSubtlePanel(templates.x, templates.y, templates.width, templates.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(editor.x, editor.y, editor.width, editor.height, 6,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernSplitPane.drawVerticalDivider(expressionEditorDividerBounds, mouseX, mouseY,
                draggingExpressionEditorDivider);
        if (expressionEditorDividerBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.path.wb.u259";
        }

        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u260", templates.x + 12, templates.y + 10,
                ModernUiRenderer.TEXT, templates.width - 24);
        ModernMainLayout.Rect search = new ModernMainLayout.Rect(templates.x + 10, templates.y + 29,
                templates.width - 20, 22);
        drawField(font, "expression.editor.search", search, true, "gui.modern.path.wb.u261", mouseX, mouseY);
        expressionTemplateListBounds = new ModernMainLayout.Rect(templates.x + 8, templates.y + 59,
                templates.width - 16, templates.height - 67);
        List<ExpressionTemplateCard> cards = filteredExpressionTemplates();
        int rowHeight = 48;
        expressionTemplateMaxScroll = Math.max(0, cards.size() * rowHeight - expressionTemplateListBounds.height);
        expressionTemplateScroll = clamp(expressionTemplateScroll, 0, expressionTemplateMaxScroll);
        expressionTemplateHits.clear();
        ModernUiRenderer.beginClip(expressionTemplateListBounds);
        int y = expressionTemplateListBounds.y - expressionTemplateScroll;
        for (ExpressionTemplateCard card : cards) {
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(expressionTemplateListBounds.x + 2, y,
                    ModernHoverScrollbar.contentWidth(expressionTemplateListBounds.width - 3), 43);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean selected = selectedExpressionTemplate != null
                    && selectedExpressionTemplate.name.equals(card.name)
                    && selectedExpressionTemplate.example.equals(card.example);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 5,
                    hovered || selected ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, card.name, row.x + 9, row.y + 7,
                    hovered || selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 18);
            ModernUiRenderer.drawText(font, card.example, row.x + 9, row.y + 23,
                    ModernUiRenderer.ACCENT, row.width - 18);
            if (row.bottom() > expressionTemplateListBounds.y && row.y < expressionTemplateListBounds.bottom()) {
                expressionTemplateHits.add(new ExpressionTemplateHit(card, row));
                if (hovered) {
                    hoveredTooltip = expressionTemplateTooltip(card);
                }
            }
            y += rowHeight;
        }
        ModernUiRenderer.endClip();
        drawExpressionTemplateScrollbar(expressionTemplateListBounds, mouseX, mouseY);

        int x = editor.x + 16;
        int width = editor.width - 32;
        ModernUiRenderer.drawText(font, expressionEditorTitle, x, editor.y + 13,
                ModernUiRenderer.TEXT, width);
        ModernUiRenderer.drawText(font, expressionEditorKind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST
                ? "gui.modern.path.wb.u262"
                : "gui.modern.path.wb.u263",
                x, editor.y + 31, ModernUiRenderer.MUTED_TEXT, width);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u264", x, editor.y + 61,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(40, width - 88));
        expressionEditorExpandBounds = new ModernMainLayout.Rect(editor.right() - 88, editor.y + 54, 72, 20);
        drawButton(font, expressionEditorExpandBounds, expressionEditorExpanded ? "gui.modern.path.wb.u265" : "gui.modern.path.wb.u266",
                false, false, true, mouseX, mouseY);

        int available = Math.max(120, editor.height - 140);
        int codeHeight = expressionEditorExpanded
                ? Math.max(120, available * 62 / 100)
                : clamp(available * 40 / 100, 108, 220);
        int maxCodeHeight = Math.max(80, editor.height - 190);
        codeHeight = Math.min(codeHeight, maxCodeHeight);
        expressionCodeEditorBounds = new ModernMainLayout.Rect(x, editor.y + 78, width, codeHeight);
        long now = System.currentTimeMillis();
        if (now >= expressionCompletionRefreshAt) {
            expressionCodeEditor.setDynamicCompletions(ExpressionEditorPreview.buildCompletions(sequences,
                    selectedSequence, selectedStepIndex, selectedActionIndex));
            expressionCompletionRefreshAt = now + 250L;
        }
        expressionCodeEditor.draw(font, expressionCodeEditorBounds, canEditStep(), mouseX, mouseY);
        String editorTooltip = expressionCodeEditor.getHoveredTooltip();
        if (!safe(editorTooltip).isEmpty()) {
            hoveredTooltip = editorTooltip;
        }
        ModernUiRenderer.drawText(font, expressionCodeEditor.getStatusText(), x,
                expressionCodeEditorBounds.bottom() + 4, ModernUiRenderer.MUTED_TEXT, width);

        int helpTop = expressionCodeEditorBounds.bottom() + 19;
        int helpHeight = Math.max(54, editor.bottom() - 40 - helpTop);
        expressionPreviewBounds = new ModernMainLayout.Rect(x, helpTop, width, helpHeight);
        ModernUiRenderer.drawSubtlePanel(expressionPreviewBounds.x, expressionPreviewBounds.y,
                expressionPreviewBounds.width, expressionPreviewBounds.height, 5,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        drawExpressionPreviewPanel(font, mouseX, mouseY);
        expressionEditorCancelBounds = new ModernMainLayout.Rect(editor.right() - 132, editor.bottom() - 30, 56, 22);
        expressionEditorConfirmBounds = new ModernMainLayout.Rect(editor.right() - 68, editor.bottom() - 30, 56, 22);
        drawButton(font, expressionEditorCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
        drawButton(font, expressionEditorConfirmBounds, "gui.modern.path.wb.u267", true, false, canEditStep(), mouseX, mouseY);
    }

    private void drawExpressionPreviewPanel(FontRenderer font, int mouseX, int mouseY) {
        if (expressionPreviewBounds == null) {
            return;
        }
        String expression = expressionCodeEditor.getText();
        List<ExpressionEditorPreview.Line> report = ExpressionEditorPreview.build(expression,
                expressionPreviewMode(expressionEditorKind), sequences, selectedSequence, selectedStepIndex,
                selectedActionIndex, selectedExpressionTemplate);
        int lineHeight = 13;
        expressionPreviewLineCount = report.size();
        int contentHeight = 12;
        for (ExpressionEditorPreview.Line line : report) {
            contentHeight += line.text.isEmpty() ? 8 : lineHeight;
        }
        expressionPreviewMaxScroll = Math.max(0, contentHeight - expressionPreviewBounds.height);
        expressionPreviewScroll = clamp(expressionPreviewScroll, 0, expressionPreviewMaxScroll);
        ModernUiRenderer.beginClip(expressionPreviewBounds);
        int y = expressionPreviewBounds.y + 8 - expressionPreviewScroll;
        int textWidth = Math.max(20, expressionPreviewBounds.width - 28);
        for (ExpressionEditorPreview.Line line : report) {
            if (y + lineHeight >= expressionPreviewBounds.y && y <= expressionPreviewBounds.bottom()) {
                ModernUiRenderer.drawText(font, line.text,
                        expressionPreviewBounds.x + 10 + line.indent * 10, y, line.color, textWidth - line.indent * 10);
            }
            y += line.text.isEmpty() ? 8 : lineHeight;
        }
        ModernUiRenderer.endClip();
        drawExpressionPreviewScrollbar(expressionPreviewBounds, mouseX, mouseY);
    }

    private ExpressionEditorPreview.Mode expressionPreviewMode(ModernActionEditorSchema.Kind kind) {
        if (kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            return ExpressionEditorPreview.Mode.ITEM_FILTER;
        }
        if (kind == ModernActionEditorSchema.Kind.EXPRESSION_LIST
                || kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST) {
            return ExpressionEditorPreview.Mode.BOOLEAN;
        }
        return ExpressionEditorPreview.Mode.VALUE;
    }

    private void insertExpressionTemplate(String example) {
        String snippet = example == null ? "" : example;
        if (expressionCodeEditor.getText().trim().isEmpty()) {
            expressionCodeEditor.setText(snippet);
        } else {
            expressionCodeEditor.insertText(snippet);
        }
        expressionCodeEditor.setFocused(true);
    }

    private String expressionTemplateTooltip(ExpressionTemplateCard card) {
        if (card == null) {
            return "";
        }
        StringBuilder text = new StringBuilder(card.name);
        if (!safe(card.description).isEmpty()) {
            text.append('\n').append(card.description);
        }
        if (!safe(card.format).isEmpty()) {
            text.append('\n').append(tr("gui.modern.path.wb.fmt.format", card.format));
        }
        if (!safe(card.example).isEmpty()) {
            text.append('\n').append(tr("gui.modern.path.wb.fmt.fill", card.example));
        }
        return text.toString();
    }

    private List<ExpressionTemplateCard> filteredExpressionTemplates() {
        List<ExpressionTemplateCard> source = expressionEditorKind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST
                ? ExpressionTemplateCatalog.buildItemFilterCards()
                : expressionEditorKind == ModernActionEditorSchema.Kind.EXPRESSION_LIST
                        ? ExpressionTemplateCatalog.buildBooleanCards()
                        : ExpressionTemplateCatalog.buildSetVarCards();
        ModernTextField search = fields.get("expression.editor.search");
        String query = PinyinSearchHelper.normalizeQuery(search == null ? "" : search.getText());
        if (query.isEmpty()) return source;
        List<ExpressionTemplateCard> result = new ArrayList<>();
        for (ExpressionTemplateCard card : source) {
            StringBuilder searchable = new StringBuilder(card.name).append(' ').append(card.example).append(' ')
                    .append(card.description).append(' ').append(card.format);
            for (String keyword : card.keywords) searchable.append(' ').append(keyword);
            if (PinyinSearchHelper.matchesNormalized(searchable.toString(), query)) result.add(card);
        }
        return result;
    }

    private void openExpressionEditor(ModernActionEditorSchema.Field field, int index) {
        if (field == null) return;
        clearFocus();
        expressionEditorKind = field.kind;
        expressionEditorParamKey = safe(field.key);
        expressionEditorIndex = index;
        expressionEditorTitle = (index < 0 ? tr("gui.modern.path.wb.u059") : tr("gui.modern.path.wb.u268"))
                + " " + tr(field.label);
        String value = "";
        if (field.kind == ModernActionEditorSchema.Kind.EXPRESSION) {
            value = selectedAction == null || selectedAction.params == null ? ""
                    : jsonValue(selectedAction.params.get(field.key));
            expressionEditorTitle = tr("gui.modern.path.wb.u268") + " " + tr(field.label);
        } else {
            List<String> values = getExpressionValues(field);
            if (index >= 0 && index < values.size()) value = values.get(index);
        }
        setFieldTextIfUnchanged("expression.editor.search", "");
        expressionCodeEditor.setText(value);
        expressionCodeEditor.setFocused(true);
        expressionCompletionRefreshAt = 0L;
        expressionTemplateScroll = 0;
        expressionPreviewScroll = 0;
        selectedExpressionTemplate = null;
        auxiliaryView = AuxiliaryView.EXPRESSION_EDITOR;
        focusedFieldKey = null;
        if (!"expression_editor".equals(command)) {
            requestNativeRoute("expression_editor");
        }
    }

    private void commitExpressionEditor() {
        if (selectedAction == null || expressionEditorKind == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        String value = safe(expressionCodeEditor.getText()).trim();
        if (value.isEmpty()) {
            status("gui.modern.path.wb.u269");
            return;
        }
        pushHistory("edit-expression");
        if (expressionEditorKind == ModernActionEditorSchema.Kind.EXPRESSION) {
            selectedAction.params.addProperty(expressionEditorParamKey, value);
        } else {
            ModernActionEditorSchema.Field field = findSchemaField(expressionEditorParamKey);
            List<String> values = getExpressionValues(field);
            if (expressionEditorIndex >= 0 && expressionEditorIndex < values.size()) {
                values.set(expressionEditorIndex, value);
            } else {
                values.add(value);
            }
            writeExpressionValues(field, values);
        }
        dirty = true;
        auxiliaryView = AuxiliaryView.ACTION_EDITOR;
        clearFocus();
        bindEditorFields();
        if (!"action_editor".equals(command)) {
            requestNativeRoute("action_editor");
        }
    }

    private List<String> getExpressionValues(ModernActionEditorSchema.Field field) {
        List<String> result = new ArrayList<>();
        if (field == null || selectedAction == null || selectedAction.params == null) return result;
        if (field.kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            result.addAll(InventoryItemFilterExpressionEngine.readExpressions(selectedAction.params));
            return result;
        }
        if (field.kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST) {
            String text = jsonValue(selectedAction.params.get(field.key));
            for (String line : text.split("[\\r\\n;；]+")) {
                String expression = line.trim();
                if (!expression.isEmpty()) result.add(expression);
            }
            return result;
        }
        JsonElement array = selectedAction.params.get("expressions");
        if (array != null && array.isJsonArray()) {
            for (JsonElement element : array.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive() && !element.getAsString().trim().isEmpty()) {
                    result.add(element.getAsString().trim());
                }
            }
        }
        if (result.isEmpty()) {
            String legacy = jsonValue(selectedAction.params.get("expression")).trim();
            if (!legacy.isEmpty()) result.add(legacy);
        }
        return result;
    }

    private void writeExpressionValues(ModernActionEditorSchema.Field field, List<String> values) {
        if (selectedAction == null || selectedAction.params == null || field == null) return;
        if (field.kind == ModernActionEditorSchema.Kind.ITEM_FILTER_LIST) {
            InventoryItemFilterExpressionEngine.writeExpressions(selectedAction.params, values);
            return;
        }
        if (field.kind == ModernActionEditorSchema.Kind.TEXT_EXPRESSION_LIST) {
            StringBuilder text = new StringBuilder();
            for (String value : values) {
                String expression = safe(value).trim();
                if (expression.isEmpty()) continue;
                if (text.length() > 0) text.append('\n');
                text.append(expression);
            }
            selectedAction.params.addProperty(field.key, text.toString());
            return;
        }
        JsonArray array = new JsonArray();
        StringBuilder legacy = new StringBuilder();
        for (String value : values) {
            String expression = safe(value).trim();
            if (expression.isEmpty()) continue;
            array.add(expression);
            if (legacy.length() > 0) legacy.append(" && ");
            legacy.append(values.size() == 1 ? expression : "(" + expression + ")");
        }
        if (array.size() == 0) {
            selectedAction.params.remove("expressions");
            selectedAction.params.remove("expression");
        } else {
            selectedAction.params.add("expressions", array);
            selectedAction.params.addProperty("expression", legacy.toString());
        }
    }

    private void deleteExpressionValue(ModernActionEditorSchema.Field field, int index) {
        List<String> values = getExpressionValues(field);
        if (index < 0 || index >= values.size()) return;
        pushHistory("delete-expression");
        values.remove(index);
        writeExpressionValues(field, values);
        dirty = true;
    }

    private ModernActionEditorSchema.Field findSchemaField(String key) {
        if (selectedAction == null) return null;
        for (ModernActionEditorSchema.Field field : ModernActionEditorSchema.fields(selectedAction.type)) {
            if (key.equals(field.key)) return field;
        }
        return null;
    }

    private void openValidation() {
        syncEditorFields();
        validationIssues = new ArrayList<>(PathConfigValidator.validateSequences(sequences));
        validationScroll = 0;
        auxiliaryView = AuxiliaryView.VALIDATION;
        closeMenu();
        closePalette();
        clearFocus();
        status(tr("gui.modern.path.wb.fmt.validate", String.valueOf(countErrors(validationIssues)),
                String.valueOf(Math.max(0, validationIssues.size() - countErrors(validationIssues)))));
    }

    private void openLogs() {
        reloadSessions();
        executionLogView.reload();
        auxiliaryView = AuxiliaryView.LOGS;
        closeMenu();
        closePalette();
        clearFocus();
    }

    private void openTemplates() {
        actionTemplates = ActionTemplateCatalog.getTemplates();
        rebuildActionTemplateCategories();
        selectedActionTemplateIndex = actionTemplates.isEmpty() ? -1
                : clamp(selectedActionTemplateIndex, 0, actionTemplates.size() - 1);
        templateScroll = 0;
        templateCategoryScroll = 0;
        setFieldTextIfUnchanged("template.search", "");
        auxiliaryView = AuxiliaryView.TEMPLATES;
        closeMenu();
        closePalette();
        clearFocus();
    }

    private void openVariables() {
        bindVariablePanelHost();
        closeMenu();
        closePalette();
        clearFocus();
        variablePanel.open();
        auxiliaryView = AuxiliaryView.VARIABLES;
    }

    private void openRecording() {
        setFieldTextIfUnchanged("recording.name", uniqueSequenceName(tr("gui.modern.path.wb.u204")));
        recordingSaveCategory = selectedCategory.isEmpty() ? defaultCategory() : selectedCategory;
        recordingSaveSubCategory = selectedSubCategory;
        closeRecordingCategoryPicker();
        setFieldTextIfUnchanged("recording.radius", String.valueOf(Math.round(PathRecordingManager.getInfluenceRadius())));
        setFieldTextIfUnchanged("recording.packetWindowSeconds",
                String.valueOf(PathRecordingManager.getPacketRecordWindowSeconds()));
        recordingPreviousSequenceName = selectedSequence == null ? "" : selectedSequence.getName();
        recordingPreviousStepIndex = selectedStepIndex;
        recordingPreviousActionIndex = selectedActionIndex;
        recordingDraft = null;
        recordingDraftStepCount = -1;
        recordingDraftActionCount = -1;
        recordingDraftRevision = -1L;
        recordingDraftManuallyEdited = false;
        importedRecordingActions.clear();
        recordingUndoHistory.clear();
        recordingRedoHistory.clear();
        auxiliaryView = AuxiliaryView.RECORDING;
        closeMenu();
        closePalette();
        clearFocus();
    }

    private void openTriggerRules() {
        closeMenu();
        closePalette();
        clearFocus();
        requestNativeRoute("sequence_trigger_rules");
    }

    private void closeAuxiliary() {
        closeRecordingPacketOverlay();
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            closeRecordingCategoryPicker();
            restoreRecordingSelection();
        }
        auxiliaryView = AuxiliaryView.NONE;
        clearFocus();
        bindEditorFields();
    }

    private void restoreRecordingSelection() {
        if (recordingPreviousSequenceName != null && !recordingPreviousSequenceName.isEmpty()) {
            selectedStepIndex = recordingPreviousStepIndex;
            selectedActionIndex = recordingPreviousActionIndex;
            selectSequenceByName(recordingPreviousSequenceName);
        } else {
            selectedSequence = null;
            selectedSequenceName = "";
            selectedStep = null;
            selectedAction = null;
        }
        recordingDraft = null;
    }

    private void returnToPathManager() {
        if (routeRequest != null) {
            requestNativeRoute("path_manager");
        } else {
            auxiliaryView = AuxiliaryView.NONE;
        }
        clearFocus();
    }

    private void returnToActionEditor() {
        if (routeRequest != null) {
            requestNativeRoute("action_editor");
        } else {
            auxiliaryView = AuxiliaryView.ACTION_EDITOR;
        }
        clearFocus();
    }

    private void reloadSessions() {
        sessions = new ArrayList<>(ExecutionLogManager.getSessionsSnapshot());
        if (sessions.isEmpty()) {
            selectedSessionIndex = -1;
        } else {
            selectedSessionIndex = clamp(selectedSessionIndex < 0 ? 0 : selectedSessionIndex, 0, sessions.size() - 1);
        }
        sessionScroll = 0;
        logDetailScroll = 0;
    }

    private void bindTemplateFields() {
        if (selectedTemplateIndex < 0 || selectedTemplateIndex >= templateModels.size()) {
            setFieldTextIfUnchanged("template.name", "");
            setFieldTextIfUnchanged("template.sequence", "");
            setFieldTextIfUnchanged("template.defaults", "");
            setFieldTextIfUnchanged("template.note", "");
            return;
        }
        LegacyActionTemplateManager.TemplateEditModel model = templateModels.get(selectedTemplateIndex);
        setFieldTextIfUnchanged("template.name", model.name);
        setFieldTextIfUnchanged("template.sequence", model.sequenceName);
        setFieldTextIfUnchanged("template.defaults", model.defaultsText);
        setFieldTextIfUnchanged("template.note", model.note);
    }

    private boolean handleAuxiliaryClick(int mouseX, int mouseY, int mouseButton) {
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR) {
            return handleActionEditorClick(mouseX, mouseY, mouseButton);
        }
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            return handleRecordingClick(mouseX, mouseY, mouseButton);
        }
        if (mouseButton != 0) {
            return true;
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR) {
            return handleExpressionEditorClick(mouseX, mouseY);
        }
        if (auxiliaryView == AuxiliaryView.VARIABLES && variablePanel.isPopupOpen()) {
            return variablePanel.mouseClicked(mouseX, mouseY, mouseButton);
        }
        if (auxiliaryView == AuxiliaryView.TEMPLATES) {
            if (templateCategoryDividerBounds != null && templateCategoryDividerBounds.contains(mouseX, mouseY)) {
                long now = System.currentTimeMillis();
                if (now - lastTemplateCategoryDividerClickAt <= 350L
                        && Math.abs(mouseX - lastTemplateCategoryDividerClickX) <= 4) {
                    templateCategoryRatio = 0.20D;
                    lastTemplateCategoryDividerClickAt = 0L;
                    draggingTemplateCategoryDivider = false;
                    return true;
                }
                lastTemplateCategoryDividerClickAt = now;
                lastTemplateCategoryDividerClickX = mouseX;
                templateCategoryDragStartRatio = templateCategoryRatio;
                draggingTemplateCategoryDivider = true;
                return true;
            }
            if (templateListDividerBounds != null && templateListDividerBounds.contains(mouseX, mouseY)) {
                long now = System.currentTimeMillis();
                if (now - lastTemplateListDividerClickAt <= 350L
                        && Math.abs(mouseX - lastTemplateListDividerClickX) <= 4) {
                    templateListRatio = 0.44D;
                    lastTemplateListDividerClickAt = 0L;
                    draggingTemplateListDivider = false;
                    return true;
                }
                lastTemplateListDividerClickAt = now;
                lastTemplateListDividerClickX = mouseX;
                templateListDragStartRatio = templateListRatio;
                draggingTemplateListDivider = true;
                return true;
            }
            if (beginTemplateScrollbarDrag(mouseX, mouseY)) {
                return true;
            }
            for (RowHit hit : templateCategoryHits) {
                if (hit.bounds.contains(mouseX, mouseY) && hit.index >= 0
                        && hit.index < actionTemplateCategories.size()) {
                    selectedActionTemplateCategory = actionTemplateCategories.get(hit.index);
                    templateScroll = 0;
                    selectFirstVisibleActionTemplate();
                    return true;
                }
            }
        }
        syncAuxiliaryFields();
        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null) {
            focusField(fieldKey, mouseX, mouseY, mouseButton);
            return true;
        }
        if (auxBackBounds != null && auxBackBounds.contains(mouseX, mouseY)) {
            closeAuxiliary();
            return true;
        }
        if (recordingStepDividerBounds != null && recordingStepDividerBounds.contains(mouseX, mouseY)) {
            draggingRecordingStepDivider = true;
            return true;
        }
        if (recordingActionDividerBounds != null && recordingActionDividerBounds.contains(mouseX, mouseY)) {
            draggingRecordingActionDivider = true;
            return true;
        }
        if (auxiliaryView == AuxiliaryView.VALIDATION) {
            for (RowHit hit : validationHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    focusValidationIssue(hit.index);
                    return true;
                }
            }
            if (auxPrimaryBounds != null && auxPrimaryBounds.contains(mouseX, mouseY)) {
                openValidation();
                return true;
            }
            if (auxSecondaryBounds != null && auxSecondaryBounds.contains(mouseX, mouseY)) {
                closeAuxiliary();
                return true;
            }
        } else if (auxiliaryView == AuxiliaryView.LOGS) {
            if (executionLogView.click(mouseX, mouseY, mouseButton)) {
                return true;
            }
            for (RowHit hit : sessionHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    selectedSessionIndex = hit.index;
                    logDetailScroll = 0;
                    return true;
                }
            }
            if (auxPrimaryBounds != null && auxPrimaryBounds.contains(mouseX, mouseY)) {
                reloadSessions();
                status("gui.modern.path.wb.u270");
                return true;
            }
            if (auxSecondaryBounds != null && auxSecondaryBounds.contains(mouseX, mouseY)) {
                ExecutionLogManager.clearSessions();
                reloadSessions();
                status("gui.modern.path.wb.u271");
                return true;
            }
            if (logExportBounds != null && logExportBounds.contains(mouseX, mouseY)) {
                exportSelectedSession();
                return true;
            }
        } else if (auxiliaryView == AuxiliaryView.TEMPLATES) {
            for (RowHit hit : templateHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    selectedActionTemplateIndex = hit.index;
                    return true;
                }
            }
            if (templateAddBounds != null && templateAddBounds.contains(mouseX, mouseY)) {
                addActionTemplate();
                return true;
            }
            if (templateDeleteBounds != null && templateDeleteBounds.contains(mouseX, mouseY)) {
                deleteSelectedActionTemplate();
                return true;
            }
            if (templateInsertBounds != null && templateInsertBounds.contains(mouseX, mouseY)) {
                insertSelectedActionTemplate(false);
                return true;
            }
            if (templateInsertStepBounds != null && templateInsertStepBounds.contains(mouseX, mouseY)) {
                insertSelectedActionTemplate(true);
                return true;
            }
            if (templateSaveBounds != null && templateSaveBounds.contains(mouseX, mouseY)) {
                openTemplates();
                return true;
            }
        } else if (auxiliaryView == AuxiliaryView.VARIABLES) {
            return variablePanel.mouseClicked(mouseX, mouseY, mouseButton);
        }
        return true;
    }

    private boolean handleRecordingClick(int mouseX, int mouseY, int mouseButton) {
        syncEditorFields();
        syncRecordingDraft();
        if (handleRecordingCategoryClick(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton == 1) {
            if (stepListBounds != null && stepListBounds.contains(mouseX, mouseY)) {
                for (RowHit hit : recordingStepHits) {
                    if (hit.bounds.contains(mouseX, mouseY)) {
                        applyStepSelectionClick(hit.index, false, false);
                        openRecordingContextMenu(true, mouseX, mouseY);
                        return true;
                    }
                }
            }
            if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)) {
                for (RowHit hit : recordingActionHits) {
                    if (hit.bounds.contains(mouseX, mouseY)) {
                        applyActionSelectionClick(hit.index, false, false);
                        openRecordingContextMenu(false, mouseX, mouseY);
                        return true;
                    }
                }
            }
            return true;
        }
        if (mouseButton != 0) return true;
        if (handleRecordingToolsClick(mouseX, mouseY)) return true;
        if (recordingStepDividerBounds != null && recordingStepDividerBounds.contains(mouseX, mouseY)) {
            draggingRecordingStepDivider = true;
            return true;
        }
        if (recordingActionDividerBounds != null && recordingActionDividerBounds.contains(mouseX, mouseY)) {
            draggingRecordingActionDivider = true;
            return true;
        }
        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null && (fieldKey.startsWith("recording.") || fieldKey.startsWith("param.")
                || fieldKey.startsWith("structured.list."))) {
            if (fieldKey.startsWith("structured.list.name.")) {
                structuredEditingKey = fieldKey.substring("structured.list.name.".length());
            } else if (fieldKey.startsWith("structured.list.value.")) {
                structuredEditingKey = fieldKey.substring("structured.list.value.".length());
            }
            focusField(fieldKey, mouseX, mouseY, 0);
            return true;
        }
        if (auxBackBounds != null && auxBackBounds.contains(mouseX, mouseY)) {
            closeAuxiliary();
            return true;
        }
        for (RowHit hit : recordingStepHits) {
            if (stepListBounds != null && stepListBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                applyStepSelectionClick(hit.index, isControlDown(), isShiftDown());
                draggingStepIndex = !isControlDown() && !isShiftDown() ? hit.index : -1;
                List<Integer> selectedSteps = consecutiveSelectedSteps();
                draggingStepCount = selectedSteps == null || selectedSteps.isEmpty() ? 1 : selectedSteps.size();
                stepDropIndex = hit.index;
                dragStartY = mouseY;
                return true;
            }
        }
        for (RowHit hit : recordingPacketToggleHits) {
            if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY) && selectedStep != null
                    && hit.index >= 0 && hit.index < selectedStep.getActions().size()) {
                applyActionSelectionClick(hit.index, false, false);
                toggleRecordingPacketAction(selectedAction);
                return true;
            }
        }
        for (RowHit hit : recordingActionHits) {
            if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)
                    && hit.bounds.contains(mouseX, mouseY)) {
                applyActionSelectionClick(hit.index, isControlDown(), isShiftDown());
                draggingActionIndex = !isControlDown() && !isShiftDown() ? hit.index : -1;
                List<Integer> selectedActions = consecutiveSelectedActions();
                draggingActionCount = selectedActions == null || selectedActions.isEmpty() ? 1 : selectedActions.size();
                actionDropIndex = hit.index;
                dragStartY = mouseY;
                return true;
            }
        }
        if (actionPageParameterViewportBounds != null && actionPageParameterViewportBounds.contains(mouseX, mouseY)) {
            for (StructuredListEditorHit editor : structuredListEditorHits) {
                if (editor.add != null && editor.add.contains(mouseX, mouseY)) {
                    structuredEditingKey = editor.key;
                    applyStructuredListEntry();
                    return true;
                }
                if (editor.extra != null && editor.extra.contains(mouseX, mouseY) && "entries".equals(editor.key)) {
                    structuredEditingKey = editor.key;
                    structuredPlayerMode = PlayerListTriggerSupport.MODE_CONTAINS.equals(structuredPlayerMode)
                            ? PlayerListTriggerSupport.MODE_EXACT : PlayerListTriggerSupport.MODE_CONTAINS;
                    return true;
                }
            }
            for (StructuredListHit hit : structuredListHits) {
                if (hit.delete.contains(mouseX, mouseY)) { deleteStructuredListEntry(hit); return true; }
                if (hit.row.contains(mouseX, mouseY)) { selectStructuredListEntry(hit); return true; }
            }
            for (SlotGridHit hit : slotGridHits) {
                if (hit.bounds.contains(mouseX, mouseY)) { beginSlotGridPress(hit); return true; }
            }
            for (ParameterHit hit : parameterHits) {
                if (hit.bounds.contains(mouseX, mouseY) && handleParameterControlClick(hit, mouseX, mouseY)) return true;
            }
            for (ExpressionRowHit hit : expressionRowHits) {
                if (hit.delete.contains(mouseX, mouseY)) { deleteExpressionValue(hit.field, hit.index); return true; }
                if (hit.row.contains(mouseX, mouseY)) { openExpressionEditor(hit.field, hit.index); return true; }
            }
        }
        clearFocus();
        return true;
    }

    private boolean handleExpressionEditorClick(int mouseX, int mouseY) {
        if (expressionCodeEditor.mouseClicked(mouseX, mouseY, 0)) {
            for (ModernTextField field : fields.values()) {
                field.setFocused(false);
            }
            focusedFieldKey = null;
            return true;
        }
        if (expressionEditorDividerBounds != null && expressionEditorDividerBounds.contains(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            if (now - lastExpressionDividerClickAt <= 350L
                    && Math.abs(mouseX - lastExpressionDividerClickX) <= 4) {
                expressionEditorSplitRatio = 0.38D;
                lastExpressionDividerClickAt = 0L;
                draggingExpressionEditorDivider = false;
                return true;
            }
            lastExpressionDividerClickAt = now;
            lastExpressionDividerClickX = mouseX;
            expressionEditorDragStartRatio = expressionEditorSplitRatio;
            draggingExpressionEditorDivider = true;
            return true;
        }
        if (beginExpressionTemplateScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (beginExpressionPreviewScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (expressionEditorExpandBounds != null && expressionEditorExpandBounds.contains(mouseX, mouseY)) {
            expressionEditorExpanded = !expressionEditorExpanded;
            return true;
        }
        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null) {
            focusField(fieldKey, mouseX, mouseY, 0);
            if ("expression.editor.search".equals(fieldKey)) expressionTemplateScroll = 0;
            return true;
        }
        if (auxBackBounds != null && auxBackBounds.contains(mouseX, mouseY)
                || expressionEditorCancelBounds != null && expressionEditorCancelBounds.contains(mouseX, mouseY)) {
            returnToActionEditor();
            clearFocus();
            return true;
        }
        if (expressionEditorConfirmBounds != null && expressionEditorConfirmBounds.contains(mouseX, mouseY)) {
            commitExpressionEditor();
            return true;
        }
        for (ExpressionTemplateHit hit : expressionTemplateHits) {
            if (!hit.bounds.contains(mouseX, mouseY)) continue;
            selectedExpressionTemplate = hit.card;
            insertExpressionTemplate(hit.card.example);
            for (ModernTextField field : fields.values()) {
                field.setFocused(false);
            }
            focusedFieldKey = null;
            return true;
        }
        clearFocus();
        return true;
    }

    private boolean handleActionEditorClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0 && actionPageWritingHelpBounds != null
                && actionPageWritingHelpBounds.contains(mouseX, mouseY)) {
            showActionWritingHelp = !showActionWritingHelp;
            if (showActionWritingHelp) {
                actionWritingHelpBounds = null;
            }
            return true;
        }
        if (mouseButton == 0 && showActionWritingHelp && actionWritingHelpBounds != null) {
            if (actionWritingHelpCloseBounds != null && actionWritingHelpCloseBounds.contains(mouseX, mouseY)) {
                showActionWritingHelp = false;
                return true;
            }
            ModernMainLayout.Rect resize = new ModernMainLayout.Rect(actionWritingHelpBounds.right() - 16,
                    actionWritingHelpBounds.bottom() - 16, 16, 16);
            if (resize.contains(mouseX, mouseY)) {
                resizingActionWritingHelp = true;
                actionWritingHelpResizeStartX = mouseX;
                actionWritingHelpResizeStartY = mouseY;
                actionWritingHelpResizeStartWidth = actionWritingHelpBounds.width;
                actionWritingHelpResizeStartHeight = actionWritingHelpBounds.height;
                return true;
            }
            if (actionWritingHelpHeaderBounds != null && actionWritingHelpHeaderBounds.contains(mouseX, mouseY)) {
                draggingActionWritingHelp = true;
                actionWritingHelpDragOffsetX = mouseX - actionWritingHelpBounds.x;
                actionWritingHelpDragOffsetY = mouseY - actionWritingHelpBounds.y;
                return true;
            }
            if (actionWritingHelpBounds.contains(mouseX, mouseY)) {
                return true;
            }
        }
        if (mouseButton == 0 && actionPageDividerBounds != null
                && actionPageDividerBounds.contains(mouseX, mouseY)) {
            long now = System.currentTimeMillis();
            if (now - lastActionPageDividerClickAt <= 350L
                    && Math.abs(mouseX - lastActionPageDividerClickX) <= 4) {
                actionPageLibraryRatio = 0.29D;
                lastActionPageDividerClickAt = 0L;
                draggingActionPageDivider = false;
                return true;
            }
            lastActionPageDividerClickAt = now;
            lastActionPageDividerClickX = mouseX;
            actionPageLibraryDragStartRatio = actionPageLibraryRatio;
            draggingActionPageDivider = true;
            return true;
        }
        if (mouseButton == 0 && beginActionLibraryScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (actionPickerMode != ActionPickerMode.NONE) {
            if (mouseButton != 0) {
                return true;
            }
            return handleActionPickerClick(mouseX, mouseY);
        }
        if (mouseButton == 0) {
            String limitFieldKey = fieldAt(mouseX, mouseY);
            if ("move.chest.maxTake".equals(limitFieldKey) || "move.chest.maxPut".equals(limitFieldKey)) {
                focusField(limitFieldKey, mouseX, mouseY, mouseButton);
                return true;
            }
        }
        if (selectedAction != null && ActionEditorUxSupport.isMoveChestAction(selectedAction.type)
                && moveChestEditor.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) {
            return true;
        }
        if (handleHuntEntitySuggestionClick(mouseX, mouseY)) {
            return true;
        }
        if (actionPageParameterViewportBounds != null && actionPageParameterViewportBounds.contains(mouseX, mouseY)) {
            for (StructuredListEditorHit editor : structuredListEditorHits) {
                if (editor.add != null && editor.add.contains(mouseX, mouseY)) {
                    structuredEditingKey = editor.key;
                    applyStructuredListEntry();
                    return true;
                }
                if (editor.extra != null && editor.extra.contains(mouseX, mouseY)
                        && "entries".equals(editor.key)) {
                    structuredEditingKey = editor.key;
                    structuredPlayerMode = PlayerListTriggerSupport.MODE_CONTAINS.equals(structuredPlayerMode)
                            ? PlayerListTriggerSupport.MODE_EXACT : PlayerListTriggerSupport.MODE_CONTAINS;
                    return true;
                }
            }
            for (StructuredListHit hit : structuredListHits) {
                if (hit.delete.contains(mouseX, mouseY)) {
                    deleteStructuredListEntry(hit);
                    return true;
                }
                if (hit.row.contains(mouseX, mouseY)) {
                    selectStructuredListEntry(hit);
                    return true;
                }
            }
            for (SlotGridHit hit : slotGridHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    beginSlotGridPress(hit);
                    return true;
                }
            }
            for (int i = 0; i < systemMessageColorBounds.size() && i < SYSTEM_MESSAGE_COLOR_CODES.length; i++) {
                if (systemMessageColorBounds.get(i).contains(mouseX, mouseY)) {
                    insertSystemMessageToken(SYSTEM_MESSAGE_COLOR_CODES[i]);
                    return true;
                }
            }
            for (int i = 0; i < systemMessageFormatBounds.size() && i < SYSTEM_MESSAGE_FORMAT_CODES.length; i++) {
                if (systemMessageFormatBounds.get(i).contains(mouseX, mouseY)) {
                    insertSystemMessageToken(SYSTEM_MESSAGE_FORMAT_CODES[i]);
                    return true;
                }
            }
        }
        String fieldKey = fieldAt(mouseX, mouseY);
        if (fieldKey != null) {
            if (fieldKey.startsWith("structured.list.name.")) {
                structuredEditingKey = fieldKey.substring("structured.list.name.".length());
            } else if (fieldKey.startsWith("structured.list.value.")) {
                structuredEditingKey = fieldKey.substring("structured.list.value.".length());
            }
            focusField(fieldKey, mouseX, mouseY, 0);
            return true;
        }
        if (auxBackBounds != null && auxBackBounds.contains(mouseX, mouseY)
                || actionPageBackBounds != null && actionPageBackBounds.contains(mouseX, mouseY)) {
            returnToPathManager();
            return true;
        }
        if (actionPageApplyTypeBounds != null && actionPageApplyTypeBounds.contains(mouseX, mouseY)) {
            applyActionType();
            return true;
        }
        if (actionPageLocateActionBounds != null && actionPageLocateActionBounds.contains(mouseX, mouseY)) {
            locateSelectedAction();
            return true;
        }
        for (ExpressionRowHit hit : expressionRowHits) {
            if (hit.delete.contains(mouseX, mouseY)) {
                deleteExpressionValue(hit.field, hit.index);
                return true;
            }
            if (hit.edit.contains(mouseX, mouseY) || hit.row.contains(mouseX, mouseY)) {
                openExpressionEditor(hit.field, hit.index);
                return true;
            }
        }
        if (selectedAction != null && selectedAction.params == null && canEditStep()) {
            selectedAction.params = new JsonObject();
        }
        for (ParameterHit hit : parameterHits) {
            if (hit.bounds.contains(mouseX, mouseY) && canEditStep() && selectedAction != null
                    && selectedAction.params != null) {
                if (handleParameterControlClick(hit, mouseX, mouseY)) return true;
            }
        }
        if (actionPageLibraryBounds != null && actionPageLibraryBounds.contains(mouseX, mouseY)) {
            int listY = actionPageLibraryBounds.y + 55;
            if (mouseY >= listY && mouseY < actionPageLibraryBounds.bottom()) {
                int index = (mouseY - listY + actionPageScroll * 25) / 25;
                if (index >= 0 && index < actionPageRows.size()) {
                    ActionLibraryNode node = actionPageRows.get(index).node;
                    if (node.isGroup()) {
                        if (!actionPageExpandedGroups.add(node.id)) {
                            actionPageExpandedGroups.remove(node.id);
                        }
                    } else if (node.actionType != null && canEditStep()) {
                        if (selectedAction == null) {
                            addActionByType(node.actionType);
                        } else {
                            rememberRecentActionType(node.actionType);
                            setFieldTextIfUnchanged("action.type", node.actionType);
                            applyActionType();
                        }
                    }
                }
            }
            return true;
        }
        clearFocus();
        return true;
    }

    private boolean handleActionPickerClick(int mouseX, int mouseY) {
        if (beginActionPickerScrollDrag(mouseX, mouseY)) {
            return true;
        }
        if (actionPickerClearBounds != null && actionPickerClearBounds.contains(mouseX, mouseY)) {
            applyActionPickerSelection(null);
            closeActionPicker();
            return true;
        }
        if (actionPickerCancelBounds != null && actionPickerCancelBounds.contains(mouseX, mouseY)) {
            closeActionPicker();
            return true;
        }
        if (actionPickerMode == ActionPickerMode.KEYBOARD) {
            for (KeyboardKeyHit hit : keyboardKeyHits) {
                if (hit.bounds.contains(mouseX, mouseY)) {
                    applyKeyboardPickerSelection(hit.key.value);
                    closeActionPicker();
                    return true;
                }
            }
            if (actionPickerBounds == null || !actionPickerBounds.contains(mouseX, mouseY)) {
                closeActionPicker();
            }
            return true;
        }
        if (actionPickerSearchBounds != null && actionPickerSearchBounds.contains(mouseX, mouseY)) {
            focusField("action.picker.search", mouseX, mouseY, 0);
            return true;
        }
        if (actionPickerListBounds != null && actionPickerListBounds.contains(mouseX, mouseY)) {
            List<ActionPickerOption> options = filteredActionPickerOptions();
            int index = (mouseY - actionPickerListBounds.y - 4) / 38 + actionPickerScroll;
            int visible = Math.max(1, actionPickerListBounds.height / 38);
            if (index >= actionPickerScroll && index < options.size() && index < actionPickerScroll + visible) {
                applyActionPickerSelection(options.get(index));
                closeActionPicker();
            }
            return true;
        }
        if (actionPickerBounds == null || !actionPickerBounds.contains(mouseX, mouseY)) {
            closeActionPicker();
        }
        return true;
    }

    private void applyActionPickerSelection(ActionPickerOption option) {
        if (!canEditStep() || selectedAction == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        pushHistory("choose-fixed-action-parameter");
        String value = option == null ? "" : option.value;
        String key = actionPickerParamKey.isEmpty() ? "key" : actionPickerParamKey;
        if (actionPickerMode == ActionPickerMode.KEYBOARD) {
            selectedAction.params.addProperty("key", value);
        } else if (actionPickerMode == ActionPickerMode.SEQUENCE) {
            selectedAction.params.addProperty("sequenceName", value);
        } else if (actionPickerMode == ActionPickerMode.CAPTURED_ID) {
            selectedAction.params.addProperty("capturedId", value);
        } else if (actionPickerMode == ActionPickerMode.OTHER_FEATURE) {
            selectedAction.params.addProperty("featureId", value);
            if (option == null) {
                selectedAction.params.remove("featureName");
            } else {
                selectedAction.params.addProperty("featureName", option.label);
            }
        } else {
            selectedAction.params.addProperty(key, value);
        }
        bindEditorFields();
        dirty = true;
    }

    private void applyKeyboardPickerSelection(String value) {
        if (!canEditStep() || selectedAction == null) return;
        if (selectedAction.params == null) selectedAction.params = new JsonObject();
        pushHistory("choose-action-keyboard-key");
        selectedAction.params.addProperty("key", safe(value).trim());
        bindEditorFields();
        dirty = true;
    }

    private boolean handleAuxiliaryWheel(int wheel, int mouseX, int mouseY) {
        if (auxiliaryView == AuxiliaryView.VALIDATION && auxContentBounds.contains(mouseX, mouseY)) {
            validationScroll = clamp(validationScroll + (wheel > 0 ? -1 : 1), 0, validationMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.LOGS) {
            if (executionLogView.wheel(wheel, mouseX, mouseY)) return true;
            if (logListBounds != null && logListBounds.contains(mouseX, mouseY)) {
                sessionScroll = clamp(sessionScroll + (wheel > 0 ? -1 : 1), 0, sessionMaxScroll);
                return true;
            }
            if (logDetailBounds != null && logDetailBounds.contains(mouseX, mouseY)) {
                logDetailScroll = clamp(logDetailScroll + (wheel > 0 ? -1 : 1), 0, logDetailMaxScroll);
                return true;
            }
        }
        if (auxiliaryView == AuxiliaryView.TEMPLATES
                && ((templateCategoryBounds != null && templateCategoryBounds.contains(mouseX, mouseY))
                        || (templateListBounds != null && templateListBounds.contains(mouseX, mouseY)))) {
            if (templateCategoryBounds != null && templateCategoryBounds.contains(mouseX, mouseY)) {
                templateCategoryScroll = clamp(templateCategoryScroll + (wheel > 0 ? -28 : 28), 0,
                        templateCategoryMaxScroll);
                return true;
            }
            if (templateListViewportBounds != null && templateListViewportBounds.contains(mouseX, mouseY)) {
                templateScroll = clamp(templateScroll + (wheel > 0 ? -34 : 34), 0, templateMaxScroll);
                return true;
            }
            return true;
        }
        if (auxiliaryView == AuxiliaryView.VARIABLES) {
            return variablePanel.mouseWheel(wheel, mouseX, mouseY) || true;
        }
        if (auxiliaryView == AuxiliaryView.TEMPLATES) {
            return true;
        }
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && actionPageLibraryBounds != null
                && actionPageLibraryBounds.contains(mouseX, mouseY)) {
            actionPageScroll = clamp(actionPageScroll + (wheel > 0 ? -1 : 1), 0, paletteMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && huntEntitySuggestionBounds != null
                && huntEntitySuggestionBounds.contains(mouseX, mouseY)) {
            huntEntitySuggestionScroll = clamp(huntEntitySuggestionScroll + (wheel > 0 ? -1 : 1),
                    0, huntEntitySuggestionMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.ACTION_EDITOR && actionPageParameterViewportBounds != null
                && actionPageParameterViewportBounds.contains(mouseX, mouseY)) {
            parameterScroll = clamp(parameterScroll + (wheel > 0 ? -PARAM_ROW_HEIGHT : PARAM_ROW_HEIGHT),
                    0, parameterMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.RECORDING) {
            if (recordingCategoryPickerOpen) {
                if (recordingCategoryPickerBounds != null && recordingCategoryPickerBounds.contains(mouseX, mouseY)) {
                    recordingCategoryPickerScroll = clamp(recordingCategoryPickerScroll + (wheel > 0 ? -20 : 20),
                            0, recordingCategoryPickerMaxScroll);
                }
                return true;
            }
            if (stepListBounds != null && stepListBounds.contains(mouseX, mouseY)) {
                // Recording lists render offsets in rows, unlike the main workbench.
                recordingScroll = clamp(recordingScroll + (wheel > 0 ? -1 : 1), 0,
                        recordingMaxScroll);
                return true;
            }
            if (actionListBounds != null && actionListBounds.contains(mouseX, mouseY)) {
                actionScroll = clamp(actionScroll + (wheel > 0 ? -1 : 1), 0,
                        actionMaxScroll);
                return true;
            }
            if (actionPageParameterViewportBounds != null && actionPageParameterViewportBounds.contains(mouseX, mouseY)) {
                parameterScroll = clamp(parameterScroll + (wheel > 0 ? -PARAM_ROW_HEIGHT : PARAM_ROW_HEIGHT), 0,
                        parameterMaxScroll);
                return true;
            }
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR
                && expressionCodeEditor.mouseWheel(mouseX, mouseY, wheel)) {
            return true;
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR && expressionTemplateListBounds != null
                && expressionTemplateListBounds.contains(mouseX, mouseY)) {
            expressionTemplateScroll = clamp(expressionTemplateScroll + (wheel > 0 ? -48 : 48),
                    0, expressionTemplateMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.EXPRESSION_EDITOR && expressionPreviewBounds != null
                && expressionPreviewBounds.contains(mouseX, mouseY)) {
            expressionPreviewScroll = clamp(expressionPreviewScroll + (wheel > 0 ? -26 : 26),
                    0, expressionPreviewMaxScroll);
            return true;
        }
        if (auxiliaryView == AuxiliaryView.RECORDING && auxContentBounds != null) {
            recordingScroll = clamp(recordingScroll + (wheel > 0 ? -1 : 1), 0, recordingMaxScroll);
            return true;
        }
        return false;
    }

    private void focusValidationIssue(int index) {
        if (index < 0 || index >= validationIssues.size()) {
            return;
        }
        Issue issue = validationIssues.get(index);
        if (issue == null) {
            return;
        }
        selectSequenceByName(issue.getSequenceName());
        if (selectedSequence != null && issue.getStepIndex() >= 0
                && issue.getStepIndex() < selectedSequence.getSteps().size()) {
            selectStep(issue.getStepIndex());
            if (issue.getActionIndex() >= 0 && selectedStep != null
                    && issue.getActionIndex() < selectedStep.getActions().size()) {
                selectAction(issue.getActionIndex());
            }
        }
        closeAuxiliary();
        status("gui.modern.path.wb.u279");
    }

    private void exportSelectedSession() {
        if (selectedSessionIndex < 0 || selectedSessionIndex >= sessions.size()) {
            status("gui.modern.path.wb.u280");
            return;
        }
        java.nio.file.Path path = ExecutionLogManager.exportSession(sessions.get(selectedSessionIndex).getSessionId());
        status(path == null ? "gui.modern.path.wb.u281" : "gui.modern.path.wb.u282");
    }

    private void addActionTemplate() {
        List<ActionData> sourceActions = templateSourceActions();
        if (sourceActions.isEmpty()) {
            status("gui.modern.path.wb.u283");
            return;
        }
        String category = safe(selectedCategory).trim();
        if (category.isEmpty()) {
            category = "gui.modern.path.wb.u093";
        }
        String name = uniqueActionTemplateName("gui.modern.path.wb.u284");
        ActionTemplate created = ActionTemplateCatalog.addCustomTemplate(category, name,
                "gui.modern.path.wb.u285", "gui.modern.path.wb.u286",
                "gui.modern.path.wb.u287", sourceActions);
        if (created == null) {
            status("gui.modern.path.wb.u288");
            return;
        }
        reloadActionTemplates(created.getId());
        status("gui.modern.path.wb.u289");
    }

    private List<ActionData> templateSourceActions() {
        List<ActionData> source = new ArrayList<ActionData>();
        if (selectedStep == null) {
            return source;
        }
        List<Integer> indices = selectedActionIndexList();
        if (indices.isEmpty()) {
            for (ActionData action : selectedStep.getActions()) {
                if (action != null) {
                    source.add(new ActionData(action));
                }
            }
            return source;
        }
        for (Integer index : indices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < selectedStep.getActions().size()) {
                ActionData action = selectedStep.getActions().get(index.intValue());
                if (action != null) {
                    source.add(new ActionData(action));
                }
            }
        }
        return source;
    }

    private String uniqueActionTemplateName(String base) {
        String candidate = base;
        int suffix = 2;
        while (actionTemplateNameExists(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean actionTemplateNameExists(String name) {
        for (ActionTemplate template : actionTemplates) {
            if (template != null && safe(name).equalsIgnoreCase(safe(template.getName()))) {
                return true;
            }
        }
        return false;
    }

    private void reloadActionTemplates(String focusId) {
        actionTemplates = ActionTemplateCatalog.getTemplates();
        rebuildActionTemplateCategories();
        selectedActionTemplateIndex = -1;
        if (focusId != null && !focusId.trim().isEmpty()) {
            for (int i = 0; i < actionTemplates.size(); i++) {
                if (focusId.equals(actionTemplates.get(i).getId())) {
                    selectedActionTemplateIndex = i;
                    break;
                }
            }
        }
        if (selectedActionTemplateIndex < 0 && !actionTemplates.isEmpty()) {
            selectedActionTemplateIndex = 0;
        }
        templateScroll = 0;
    }

    private void deleteSelectedActionTemplate() {
        if (selectedActionTemplateIndex < 0 || selectedActionTemplateIndex >= actionTemplates.size()) {
            return;
        }
        ActionTemplate template = actionTemplates.get(selectedActionTemplateIndex);
        if (template == null || !template.isCustom()) {
            status("gui.modern.path.wb.u290");
            return;
        }
        if (ActionTemplateCatalog.deleteCustomTemplate(template.getId())) {
            reloadActionTemplates(null);
            status(tr("gui.modern.path.wb.fmt.deleted_template", template.getName()));
        } else {
            status("gui.modern.path.wb.u291");
        }
    }

    private void insertSelectedActionTemplate(boolean asNewStep) {
        if (selectedActionTemplateIndex < 0 || selectedActionTemplateIndex >= actionTemplates.size()) {
            status("gui.modern.path.wb.u292");
            return;
        }
        ActionTemplate template = actionTemplates.get(selectedActionTemplateIndex);
        if (template == null || template.getActions().isEmpty()) {
            status("gui.modern.path.wb.u293");
            return;
        }
        if (asNewStep) {
            if (!isEditableSequence()) {
                status("gui.modern.path.wb.u294");
                return;
            }
            int insertIndex = selectedStepIndex >= 0 && selectedStepIndex < selectedSequence.getSteps().size()
                    ? selectedStepIndex + 1 : selectedSequence.getSteps().size();
            PathStep newStep = new PathStep(captureCurrentCoordinates());
            newStep.setNote(tr("gui.modern.path.wb.fmt.template_note", safe(template.getName())));
            for (ActionData action : template.getActions()) {
                if (action != null) {
                    newStep.addAction(cloneTemplateActionForInsertion(action, 0));
                }
            }
            pushHistory("insert-template-step");
            selectedSequence.getSteps().add(insertIndex, newStep);
            selectStep(insertIndex);
            selectAction(newStep.getActions().size() - 1);
            dirty = true;
            closeAuxiliary();
            status("gui.modern.path.wb.u295");
            return;
        }
        if (!canEditStep()) {
            status("gui.modern.path.wb.u296");
            return;
        }
        int insertionBaseIndex = selectedStep.getActions().size();
        pushHistory("insert-template-actions");
        for (ActionData action : template.getActions()) {
            if (action != null) {
                selectedStep.addAction(cloneTemplateActionForInsertion(action, insertionBaseIndex));
            }
        }
        selectAction(selectedStep.getActions().size() - 1);
        dirty = true;
        closeAuxiliary();
        status("gui.modern.path.wb.u297");
    }

    private ActionData cloneTemplateActionForInsertion(ActionData action, int actionIndexOffset) {
        ActionData copied = new ActionData(action);
        if (actionIndexOffset <= 0 || copied.type == null || copied.params == null
                || !"goto_action".equalsIgnoreCase(copied.type) || !copied.params.has("targetActionIndex")) {
            return copied;
        }
        try {
            int targetIndex = Integer.parseInt(copied.params.get("targetActionIndex").getAsString().trim());
            copied.params.addProperty("targetActionIndex", targetIndex + actionIndexOffset);
        } catch (Exception ignored) {
        }
        return copied;
    }

    private void addTemplateModel() {
        LegacyActionTemplateManager.TemplateEditModel model = new LegacyActionTemplateManager.TemplateEditModel();
        model.name = uniqueTemplateName("gui.modern.path.wb.u298");
        model.sequenceName = selectedSequence == null ? "" : safe(selectedSequence.getName());
        templateModels.add(model);
        selectedTemplateIndex = templateModels.size() - 1;
        templateDirty = true;
        bindTemplateFields();
        status("gui.modern.path.wb.u299");
    }

    private String uniqueTemplateName(String base) {
        String candidate = base;
        int suffix = 2;
        while (templateNameExists(candidate)) {
            candidate = base + " " + suffix++;
        }
        return candidate;
    }

    private boolean templateNameExists(String name) {
        for (LegacyActionTemplateManager.TemplateEditModel model : templateModels) {
            if (model != null && safe(name).equalsIgnoreCase(safe(model.name))) {
                return true;
            }
        }
        return false;
    }

    private void deleteTemplateModel() {
        if (selectedTemplateIndex < 0 || selectedTemplateIndex >= templateModels.size()) {
            return;
        }
        templateModels.remove(selectedTemplateIndex);
        selectedTemplateIndex = templateModels.isEmpty() ? -1
                : Math.min(selectedTemplateIndex, templateModels.size() - 1);
        templateDirty = true;
        bindTemplateFields();
        status("gui.modern.path.wb.u300");
    }

    private void saveTemplates() {
        syncAuxiliaryFields();
        LegacyActionTemplateManager.saveTemplateModels(templateModels);
        templateDirty = false;
        status("gui.modern.path.wb.u013");
    }

    private void insertSelectedTemplate() {
        if (!canEditStep() || selectedTemplateIndex < 0 || selectedTemplateIndex >= templateModels.size()) {
            return;
        }
        syncAuxiliaryFields();
        LegacyActionTemplateManager.TemplateEditModel model = templateModels.get(selectedTemplateIndex);
        if (safe(model.name).trim().isEmpty()) {
            status("gui.modern.path.wb.u301");
            return;
        }
        pushHistory("insert-template-action");
        JsonObject params = new JsonObject();
        params.addProperty("templateName", model.name.trim());
        params.addProperty("paramsText", "");
        ActionData action = new ActionData("run_template", params);
        selectedStep.getActions().add(action);
        selectedActionIndex = selectedStep.getActions().size() - 1;
        selectedAction = action;
        dirty = true;
        closeAuxiliary();
        bindEditorFields();
        status("gui.modern.path.wb.u302");
    }



    private void requestNativeRoute(String target) {
        if (routeRequest != null) {
            try {
                routeRequest.accept(target);
                return;
            } catch (Exception ignored) {
            }
        }
        status(tr("gui.modern.path.wb.fmt.open_entry", target));
    }

    private void saveRecordedSequence() {
        List<PathRecordingManager.RecordedStep> recorded = PathRecordingManager.getRecordedSteps();
        syncRecordingDraft();
        String name = fields.get("recording.name") == null ? "" : safe(fields.get("recording.name").getText()).trim();
        String category = recordingCategoryOrDefault();
        String subCategory = safe(recordingSaveSubCategory).trim();
        if (recordingDraft == null || recordingDraft.getSteps().isEmpty()) {
            status("gui.modern.path.wb.u303");
            return;
        }
        if (name.isEmpty() || findSequence(name) != null) {
            status("gui.modern.path.wb.u304");
            return;
        }
        if (PathRecordingManager.isRecording()) {
            PathRecordingManager.finishRecording();
        }
        pushHistory("save-recording");
        PathSequence sequence = recordingDraft == null ? buildRecordedSequence(name, category, recorded)
                : new PathSequence(recordingDraft, true);
        sequence.setName(name);
        sequence.setCategory(category);
        sequence.setSubCategory(subCategory);
        sequence.setCustom(true);
        sequences.add(0, sequence);
        addCategoryLocal(category);
        addSubCategoryLocal(category, subCategory);
        selectedCategory = category;
        selectedSubCategory = subCategory;
        selectedSequenceName = name;
        selectedStepIndex = 0;
        selectedActionIndex = 0;
        selectSequenceByName(name);
        recorded.clear();
        recordingDraft = null;
        recordingDraftStepCount = -1;
        recordingDraftActionCount = -1;
        recordingDraftRevision = -1L;
        recordingDraftManuallyEdited = false;
        importedRecordingActions.clear();
        List<Issue> issues = PathConfigValidator.validateSequences(sequences);
        if (countErrors(issues) > 0) {
            validationIssues = new ArrayList<>(issues);
            auxiliaryView = AuxiliaryView.VALIDATION;
            status("gui.modern.path.wb.u305");
            return;
        }
        recordingPreviousSequenceName = name;
        recordingPreviousStepIndex = 0;
        recordingPreviousActionIndex = 0;
        commitDraft();
        closeAuxiliary();
        status(tr("gui.modern.path.wb.fmt.saved_seq", name));
    }

    private PathSequence buildRecordedSequence(String name, String category,
            List<PathRecordingManager.RecordedStep> recorded) {
        PathSequence sequence = new PathSequence(name);
        sequence.setCustom(true);
        sequence.setCategory(category);
        for (PathRecordingManager.RecordedStep recordedStep : recorded) {
            if (recordedStep == null || recordedStep.playerPos == null) {
                continue;
            }
            PathStep step = new PathStep(new double[] { round(recordedStep.playerPos.x), round(recordedStep.playerPos.y),
                    round(recordedStep.playerPos.z) });
            for (ActionData action : recordedStep.getActions()) {
                if (action != null) step.addAction(new ActionData(action, true));
            }
            // Keep the legacy chest recorder usable when an old caller supplied only chestPos.
            if (step.getActions().isEmpty() && recordedStep.chestPos != null) {
                JsonObject rightClick = new JsonObject();
                JsonArray position = new JsonArray();
                position.add(recordedStep.chestPos.getX());
                position.add(recordedStep.chestPos.getY());
                position.add(recordedStep.chestPos.getZ());
                rightClick.add("pos", position);
                rightClick.addProperty("locatorMode", "POSITION");
                step.addAction(new ActionData("rightclickblock", rightClick));
            }
            sequence.addStep(step);
        }
        return sequence;
    }

    private int menuAnchorX;
    private int menuAnchorY;

    private void drawMenu(FontRenderer font, int mouseX, int mouseY) {
        if (menuItems.isEmpty()) {
            return;
        }
        int rowHeight = 22;
        int titleHeight = 25;
        int width = 176;
        int rightReserve = 8;
        int maxLabelWidth = 0;
        for (RectAction item : menuItems) {
            maxLabelWidth = Math.max(maxLabelWidth, font.getStringWidth(tr(item.label)));
            int shortcutReserve = item.shortcut.isEmpty() ? 0 : font.getStringWidth(item.shortcut) + 10;
            int iconReserve = item.tool == null ? 0 : 40;
            rightReserve = Math.max(rightReserve, shortcutReserve + iconReserve + 8);
        }
        // Keep the label centered in the content column while the optional
        // shortcut and pin/info icons stay in a stable right-hand column.
        width = Math.max(width, maxLabelWidth + rightReserve + 24);
        width = Math.min(Math.max(160, width), Math.max(170, bounds.width - 16));
        int height = titleHeight + 8 + menuItems.size() * rowHeight;
        int x = clamp(menuAnchorX, bounds.x + 8, Math.max(bounds.x + 8, bounds.right() - width - 8));
        int y = clamp(menuAnchorY, bounds.y + 8, Math.max(bounds.y + 8, bounds.bottom() - height - 8));
        menuBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height), 0x22000000);
        ModernUiRenderer.drawPanel(x, y, width, height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, Math.max(1, width - 2), titleHeight, 5,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, menuTitle, x + 9, y + 7, ModernUiRenderer.TEXT, width - 18);
        ModernUiRenderer.drawDivider(x + 7, y + titleHeight, Math.max(1, width - 14), ModernUiRenderer.BORDER_SUBTLE);
        for (int i = 0; i < menuItems.size(); i++) {
            RectAction item = menuItems.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(x + 4, y + titleHeight + 4 + i * rowHeight,
                    width - 8, rowHeight - 1);
            item.bounds = row;
            item.infoBounds = null;
            item.pinBounds = null;
            boolean hovered = row.contains(mouseX, mouseY);
            if (item.selected) {
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        ModernUiRenderer.SURFACE_PRESSED, ModernUiRenderer.ACCENT);
            }
            if (hovered && item.enabled) {
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        item.danger ? 0x553D252D : ModernUiRenderer.SURFACE_HOVER,
                        item.danger ? 0xFF8F4F55 : ModernUiRenderer.BORDER_SUBTLE);
            }
            int color = !item.enabled ? ModernUiRenderer.MUTED_TEXT
                    : item.danger ? 0xFFE69A9A : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT;
            int iconReserve = item.tool == null ? 0 : 40;
            ModernMainLayout.Rect labelBounds = new ModernMainLayout.Rect(row.x + 8, row.y,
                    Math.max(1, row.width - rightReserve - 8), row.height);
            drawCenteredButtonText(font, item.label, labelBounds, color);
            if (!item.shortcut.isEmpty()) {
                int shortcutWidth = font.getStringWidth(item.shortcut);
                ModernUiRenderer.drawText(font, item.shortcut, row.right() - iconReserve - 8 - shortcutWidth,
                        row.y + 6, hovered && item.enabled ? ModernUiRenderer.SUBTLE_TEXT : ModernUiRenderer.MUTED_TEXT,
                        shortcutWidth);
            }
            if (item.tool != null) {
                drawMenuToolIcons(item, row, mouseX, mouseY);
            }
        }
    }

    private void drawMenuToolIcons(RectAction item, ModernMainLayout.Rect row, int mouseX, int mouseY) {
        int pinSize = 12;
        int infoSize = 11;
        int pinX = row.right() - pinSize - 3;
        int infoX = pinX - infoSize - 5;
        int infoY = row.y + Math.max(0, (row.height - infoSize) / 2);
        int pinY = row.y + Math.max(0, (row.height - pinSize) / 2);
        item.infoBounds = new ModernMainLayout.Rect(infoX - 2, row.y + 1, infoSize + 4, row.height - 2);
        item.pinBounds = new ModernMainLayout.Rect(pinX - 2, row.y + 1, pinSize + 4, row.height - 2);
        drawInlineInfoIcon(infoX, infoY, item.description, mouseX, mouseY);
        boolean pinned = PathWorkbenchToolCatalog.get().isPinned(item.tool.zone, item.tool);
        boolean pinHovered = item.pinBounds.contains(mouseX, mouseY);
        if (pinHovered) {
            ModernUiRenderer.drawRoundedRect(pinX - 2, pinY - 2, pinSize + 4, pinSize + 4, 4,
                    ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = pinned ? "gui.modern.path.wb.u306" : "gui.modern.path.wb.u307";
        }
        int pinColor = pinned
                ? (pinHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.ACCENT)
                : (pinHovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        ModernUiRenderer.drawPinIcon(pinX, pinY, pinned, pinColor);
    }

    private void setMenuAnchor(int mouseX, int mouseY) {
        menuAnchorX = mouseX;
        menuAnchorY = mouseY;
        menuBounds = null;
    }

    private boolean handleMenuClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            closeMenu();
            return true;
        }
        if (menuBounds == null || !menuBounds.contains(mouseX, mouseY)) {
            closeMenu();
            return true;
        }
        for (RectAction item : menuItems) {
            if (item.bounds == null || !item.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (item.pinBounds != null && item.pinBounds.contains(mouseX, mouseY) && item.tool != null) {
                PathWorkbenchToolCatalog.get().togglePinned(item.tool.zone, item.tool);
                return true;
            }
            if (item.infoBounds != null && item.infoBounds.contains(mouseX, mouseY)) {
                return true;
            }
            if (!item.enabled) {
                closeMenu();
                return true;
            }
            Runnable action = item.action;
            closeMenu();
            if (action != null) {
                action.run();
            }
            return true;
        }
        return true;
    }

    private void closeMenu() {
        menuItems.clear();
        menuBounds = null;
        menuTitle = "";
    }

    private void drawActionPalette(FontRenderer font, int mouseX, int mouseY) {
        if (paletteBounds == null) {
            return;
        }
        ensureActionPageLibrary();
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0xB80A1016);
        ModernUiRenderer.drawPanel(paletteBounds.x, paletteBounds.y, paletteBounds.width, paletteBounds.height, 7,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, recordingReplacementTarget == null ? "gui.modern.path.wb.u308"
                : "gui.modern.path.record.replace_action", paletteBounds.x + 12, paletteBounds.y + 10,
                ModernUiRenderer.TEXT, paletteBounds.width - 24);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u309", paletteBounds.x + 12,
                paletteBounds.y + 25, ModernUiRenderer.MUTED_TEXT, paletteBounds.width - 24);
        paletteSearchBounds = new ModernMainLayout.Rect(paletteBounds.x + 10, paletteBounds.y + 43,
                Math.max(1, paletteBounds.width - 20), 20);
        drawField(font, "palette.search", paletteSearchBounds, true, "gui.modern.path.wb.u310", mouseX, mouseY);
        actionPageRows.clear();
        String query = PinyinSearchHelper.normalizeQuery(query("palette.search"));
        actionPageRows.addAll(ActionLibraryViewSupport.buildVisibleRows(actionPageRoots,
                actionPageExpandedGroups, query));
        ModernMainLayout.Rect list = new ModernMainLayout.Rect(paletteBounds.x + 10, paletteBounds.y + 70,
                Math.max(1, paletteBounds.width - 20), Math.max(1, paletteBounds.height - 105));
        int visible = Math.max(1, list.height / 25);
        paletteMaxScroll = Math.max(0, actionPageRows.size() * 25 - list.height);
        paletteScroll = clamp(paletteScroll, 0, paletteMaxScroll);
        ModernUiRenderer.drawSubtlePanel(list.x, list.y, list.width, list.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(list);
        int y = list.y - paletteScroll;
        for (ActionLibraryVisibleRow visibleRow : actionPageRows) {
            ActionLibraryNode node = visibleRow.node;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(list.x + 5, y, ModernHoverScrollbar.contentWidth(list.width - 5), 22);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    node.isGroup() ? (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE)
                            : (hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL),
                    hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            int indent = Math.min(42, visibleRow.depth * 12);
            if (node.isGroup()) {
                ModernUiRenderer.drawChevron(row.x + 8 + indent, row.y + 6,
                        !actionPageExpandedGroups.contains(node.id), ModernUiRenderer.ACCENT);
            } else {
                ModernUiRenderer.drawRoundedRect(row.x + 10 + indent, row.y + 7, 4, 8, 2,
                        actionAccent(node.actionType));
            }
            ModernUiRenderer.drawText(font, safe(node.label), row.x + 22 + indent, row.y + 5,
                    node.isGroup() ? ModernUiRenderer.TEXT : hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(40, row.width - 30 - indent));
            y += 25;
        }
        ModernUiRenderer.endClip();
        drawPaletteScrollbar(list, actionPageRows.size(), visible, mouseX, mouseY);
        if (actionPageRows.isEmpty()) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u311", list.x + 10, list.y + list.height / 2 - 5,
                    ModernUiRenderer.MUTED_TEXT, Math.max(50, list.width - 20));
        }
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u312", paletteBounds.x + 12, paletteBounds.bottom() - 21,
                ModernUiRenderer.MUTED_TEXT, Math.max(50, paletteBounds.width - 24));
    }

    private List<String> filteredActionTypes() {
        String query = query("palette.search");
        if (query.isEmpty()) {
            return ACTION_TYPES;
        }
        List<String> result = new ArrayList<>();
        for (String type : ACTION_TYPES) {
            if (contains(type, query) || contains(prettyType(type), query)) {
                result.add(type);
            }
        }
        return result;
    }

    private void drawPaletteScrollbar(ModernMainLayout.Rect list, int total, int visible, int mouseX, int mouseY) {
        if (list == null || paletteMaxScroll <= 0 || list.height <= 0) {
            paletteScrollTrack = null;
            paletteScrollThumb = null;
            paletteScrollHover *= 0.7F;
            return;
        }
        int thumbHeight = Math.max(18, list.height * Math.max(1, visible) / Math.max(1, total));
        thumbHeight = Math.min(list.height, thumbHeight);
        int travel = Math.max(0, list.height - thumbHeight);
        int thumbY = list.y + travel * paletteScroll / Math.max(1, paletteMaxScroll);
        paletteScrollTrack = new ModernMainLayout.Rect(list.right() - 14, list.y, 14, list.height);
        boolean hovered = draggingPaletteScroll || paletteScrollTrack.contains(mouseX, mouseY);
        paletteScrollHover += ((hovered ? 1.0F : 0.0F) - paletteScrollHover) * 0.28F;
        int grown = Math.round(4 + 6 * paletteScrollHover);
        int trackWidth = Math.max(2, Math.round(2 + 2 * paletteScrollHover));
        int thumbX = list.right() - grown - 2;
        paletteScrollThumb = new ModernMainLayout.Rect(thumbX, thumbY, grown, thumbHeight);
        ModernUiRenderer.drawRoundedRect(list.right() - trackWidth - 3, list.y, trackWidth, list.height, 2,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawRoundedRect(thumbX, thumbY, grown, thumbHeight, Math.max(2, grown / 2),
                hovered ? ModernUiRenderer.ACCENT : mixScrollColor(ModernUiRenderer.SUBTLE_TEXT,
                        ModernUiRenderer.ACCENT, paletteScrollHover));
    }

    private boolean beginPaletteScrollbarDrag(int mouseX, int mouseY) {
        if (paletteScrollTrack == null || paletteMaxScroll <= 0 || !paletteScrollTrack.contains(mouseX, mouseY)) {
            return false;
        }
        draggingPaletteScroll = true;
        paletteScrollDragOffset = paletteScrollThumb != null && paletteScrollThumb.contains(mouseX, mouseY)
                ? mouseY - paletteScrollThumb.y : paletteScrollThumb == null ? 9 : paletteScrollThumb.height / 2;
        applyPaletteScrollFromMouse(mouseY);
        return true;
    }

    private void applyPaletteScrollFromMouse(int mouseY) {
        if (paletteScrollTrack == null || paletteScrollThumb == null || paletteMaxScroll <= 0) return;
        int travel = Math.max(1, paletteScrollTrack.height - paletteScrollThumb.height);
        int target = clamp(mouseY - paletteScrollDragOffset, paletteScrollTrack.y,
                paletteScrollTrack.y + travel);
        paletteScroll = clamp(Math.round((target - paletteScrollTrack.y) * paletteMaxScroll / (float) travel),
                0, paletteMaxScroll);
    }

    private boolean handlePaletteClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            closePalette();
            return true;
        }
        if (paletteSearchBounds != null && paletteSearchBounds.contains(mouseX, mouseY)) {
            focusField("palette.search", mouseX, mouseY, mouseButton);
            return true;
        }
        if (beginPaletteScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        String fieldKey = fieldAt(mouseX, mouseY);
        if ("palette.search".equals(fieldKey)) {
            focusField(fieldKey, mouseX, mouseY, mouseButton);
            return true;
        }
        if (paletteBounds == null || !paletteBounds.contains(mouseX, mouseY)) {
            closePalette();
            return true;
        }
        int listY = paletteBounds.y + 70;
        int listHeight = Math.max(1, paletteBounds.height - 105);
        if (mouseY >= listY && mouseY < listY + listHeight) {
            ensureActionPageLibrary();
            String query = PinyinSearchHelper.normalizeQuery(query("palette.search"));
            actionPageRows.clear();
            actionPageRows.addAll(ActionLibraryViewSupport.buildVisibleRows(actionPageRoots,
                    actionPageExpandedGroups, query));
            int index = (mouseY - listY + paletteScroll) / 25;
            if (index >= 0 && index < actionPageRows.size()) {
                ActionLibraryNode node = actionPageRows.get(index).node;
                if (node.isGroup()) {
                    if (!actionPageExpandedGroups.add(node.id)) {
                        actionPageExpandedGroups.remove(node.id);
                    }
                    paletteScroll = 0;
                } else if (node.actionType != null && !node.actionType.trim().isEmpty()) {
                    addActionByType(node.actionType);
                    closePalette();
                }
            }
        }
        return true;
    }

    private ModernMainLayout.Rect placePopup(int width, int height, int anchorX, int anchorY) {
        width = Math.min(width, Math.max(1, bounds.width - 16));
        height = Math.min(height, Math.max(1, bounds.height - 16));
        int x = anchorX + 10;
        int y = anchorY + 8;
        if (x + width > bounds.right() - 8) x = anchorX - width - 10;
        if (y + height > bounds.bottom() - 8) y = anchorY - height - 8;
        x = clamp(x, bounds.x + 8, Math.max(bounds.x + 8, bounds.right() - width - 8));
        y = clamp(y, bounds.y + 8, Math.max(bounds.y + 8, bounds.bottom() - height - 8));
        return new ModernMainLayout.Rect(x, y, width, height);
    }

    private boolean canMoveToPickedGroup() {
        return selectedSequence != null && isEditableSequence()
                && categories.contains(moveTargetCategory) && !isBuiltinCategory(moveTargetCategory)
                && (moveTargetSubCategory.isEmpty() || subCategoriesFor(moveTargetCategory).contains(moveTargetSubCategory))
                && !(moveTargetCategory.equals(safe(selectedSequence.getCategory()))
                    && moveTargetSubCategory.equals(safe(selectedSequence.getSubCategory())));
    }

    private void drawMoveGroupPicker(FontRenderer font, int mouseX, int mouseY) {
        int width = Math.min(540, Math.max(1, bounds.width - 24));
        int height = Math.min(360, Math.max(1, bounds.height - 20));
        modalBounds = new ModernMainLayout.Rect(bounds.x + (bounds.width - width) / 2,
                bounds.y + (bounds.height - height) / 2, width, height);
        int x = modalBounds.x, y = modalBounds.y;
        width = modalBounds.width;
        height = modalBounds.height;
        ModernUiRenderer.drawBackdropOverlay(bounds, 0xB80A1016);
        ModernUiRenderer.drawPanel(x, y, width, height, 8, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, width - 2, 30, 7, ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, modalTitle, x + 14, y + 10, ModernUiRenderer.TEXT, width - 28);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.move.hint"), x + 14, y + 38,
                ModernUiRenderer.SUBTLE_TEXT, width - 28);
        modalMainBounds = new ModernMainLayout.Rect(x + 14, y + 54, width - 28, 22);
        modalSecondaryBounds = null;
        drawField(font, "modal.main", modalMainBounds, true, "gui.modern.path.move.search", mouseX, mouseY);
        String filter = query("modal.main");
        if (!filter.equals(movePickerQuery)) {
            movePickerQuery = filter;
            moveCategoriesScroll = moveSubCategoriesScroll = 0;
        }
        List<String> matches = new ArrayList<>();
        for (String category : categories) {
            if (safe(category).isEmpty() || isBuiltinCategory(category)) continue;
            boolean match = filter.isEmpty() || contains(category, filter);
            for (String sub : subCategoriesFor(category)) {
                match |= contains(sub, filter);
            }
            if (match) matches.add(category);
        }
        int leftWidth = (width - 38) / 2;
        int listHeight = Math.max(1, height - 166);
        moveCategoriesBounds = new ModernMainLayout.Rect(x + 14, y + 99, leftWidth, listHeight);
        moveSubCategoriesBounds = new ModernMainLayout.Rect(x + 24 + leftWidth, y + 99, leftWidth, listHeight);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.move.categories", matches.size()), x + 14, y + 84,
                ModernUiRenderer.SUBTLE_TEXT, leftWidth);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.move.subcategories"), x + 24 + leftWidth, y + 84,
                ModernUiRenderer.SUBTLE_TEXT, leftWidth);
        List<String> subs = new ArrayList<>();
        if (matches.contains(moveTargetCategory)) {
            subs.add("");
            for (String sub : subCategoriesFor(moveTargetCategory)) {
                if (!safe(sub).isEmpty() && (filter.isEmpty() || contains(moveTargetCategory, filter) || contains(sub, filter))) {
                    subs.add(sub);
                }
            }
        }
        moveCategoriesMaxScroll = Math.max(0, matches.size() * 34 - listHeight);
        moveSubCategoriesMaxScroll = Math.max(0, subs.size() * 34 - listHeight);
        moveCategoriesScroll = clamp(moveCategoriesScroll, 0, moveCategoriesMaxScroll);
        moveSubCategoriesScroll = clamp(moveSubCategoriesScroll, 0, moveSubCategoriesMaxScroll);
        movePickerHits.clear();
        drawMoveGroupColumn(font, moveCategoriesBounds, matches, true, moveCategoriesScroll, mouseX, mouseY);
        drawMoveGroupColumn(font, moveSubCategoriesBounds, subs, false, moveSubCategoriesScroll, mouseX, mouseY);
        String destination = moveTargetCategory + (moveTargetSubCategory.isEmpty() ? "" : " / " + moveTargetSubCategory);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.move.destination", destination), x + 14, y + height - 55,
                ModernUiRenderer.ACCENT, width - 28);
        modalConfirmBounds = new ModernMainLayout.Rect(x + width - 158, y + height - 31, 72, 22);
        modalCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + height - 31, 64, 22);
        drawButton(font, modalConfirmBounds, "gui.modern.path.move.confirm", true, false, canMoveToPickedGroup(), mouseX, mouseY);
        drawButton(font, modalCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
    }

    private void drawMoveGroupColumn(FontRenderer font, ModernMainLayout.Rect pane, List<String> values,
            boolean categoryColumn, int scroll, int mouseX, int mouseY) {
        ModernUiRenderer.drawPanel(pane.x, pane.y, pane.width, pane.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(pane);
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(pane.x + 4, pane.y + i * 34 - scroll + 2, pane.width - 12, 30);
            if (row.bottom() <= pane.y || row.y >= pane.bottom()) continue;
            boolean selected = value.equals(categoryColumn ? moveTargetCategory : moveTargetSubCategory);
            boolean hovered = pane.contains(mouseX, mouseY) && row.contains(mouseX, mouseY);
            ModernUiRenderer.drawPanel(row.x, row.y, row.width, row.height, 4,
                    selected || hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            String label = value.isEmpty() ? tr("gui.modern.path.move.root") : value;
            ModernUiRenderer.drawText(font, label, row.x + 8, row.y + 5,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.TEXT, row.width - 18);
            boolean current = selectedSequence != null && (categoryColumn
                    ? value.equals(safe(selectedSequence.getCategory()))
                    : moveTargetCategory.equals(safe(selectedSequence.getCategory())) && value.equals(safe(selectedSequence.getSubCategory())));
            String detail = current ? tr("gui.modern.path.move.current") : categoryColumn
                    ? tr("gui.modern.path.move.children", subCategoriesFor(value).size())
                    : tr("gui.modern.path.move.select");
            ModernUiRenderer.drawText(font, detail, row.x + 8, row.y + 18, ModernUiRenderer.MUTED_TEXT, row.width - 18);
            movePickerHits.add(new NavHit(categoryColumn ? NavKind.CATEGORY : NavKind.SUBCATEGORY,
                    categoryColumn ? value : moveTargetCategory, categoryColumn ? "" : value, null, row, null));
            if (hovered) hoveredTooltip = categoryColumn ? value : moveTargetCategory + " / " + label;
        }
        if (values.isEmpty()) {
            ModernUiRenderer.drawText(font, tr(categoryColumn ? "gui.modern.path.move.empty" : "gui.modern.path.move.pick_category"),
                    pane.x + 10, pane.y + 12, ModernUiRenderer.MUTED_TEXT, pane.width - 20);
        }
        ModernUiRenderer.endClip();
        int contentHeight = values.size() * 34;
        if (contentHeight > pane.height) {
            int thumb = Math.max(12, pane.height * pane.height / contentHeight);
            int top = pane.y + (pane.height - thumb) * scroll / (contentHeight - pane.height);
            ModernUiRenderer.drawRoundedRect(pane.right() - 5, top, 3, thumb, 1, ModernUiRenderer.ACCENT);
        }
    }

    private void drawModal(FontRenderer font, int mouseX, int mouseY) {
        if (modalMode == ModalMode.MOVE_SEQUENCE) {
            drawMoveGroupPicker(font, mouseX, mouseY);
            return;
        }
        int width = Math.min(380, Math.max(250, bounds.width - 24));
        boolean input = modalNeedsInput();
        int height = input ? 142 : 112;
        modalNoteColorChips = null;
        modalNoteFormatChips = null;
        if (isNoteModal()) {
            height += NOTE_CHIP_ROW_HEIGHT * 2 + 18;
        }
        height = Math.min(height, Math.max(100, bounds.height - 20));
        modalBounds = placePopup(width, height, modalAnchorX, modalAnchorY);
        int x = modalBounds.x;
        int y = modalBounds.y;
        width = modalBounds.width;
        height = modalBounds.height;
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0xB80A1016);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, Math.max(1, width - 2), 30, 6,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, modalTitle, x + 12, y + 9, ModernUiRenderer.TEXT, width - 24);
        ModernUiRenderer.drawText(font, modalMessage, x + 12, y + 38, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(50, width - 24));
        int inputY = y + 61;
        if (input) {
            ModernMainLayout.Rect main = new ModernMainLayout.Rect(x + 12, inputY, width - 24, 20);
            modalMainBounds = main;
            String placeholder = isNoteModal() ? "gui.modern.path.wb.u313"
                    : modalMode == ModalMode.RUN_FROM_STEP ? "gui.modern.path.wb.u314" : "gui.modern.path.wb.u054";
            drawField(font, "modal.main", main, true, placeholder, mouseX, mouseY);
            if (isNoteModal()) {
                int colorChipWidth = Math.max(10, Math.min(20, (width - 24 - (NOTE_COLOR_CODES.length - 1) * 2)
                        / NOTE_COLOR_CODES.length));
                int formatChipWidth = Math.max(18, Math.min(36, (width - 24 - (NOTE_FORMAT_CODES.length - 1) * 2)
                        / NOTE_FORMAT_CODES.length));
                modalNoteColorChips = drawNoteChipRow(font, x + 12, inputY + 26, NOTE_COLOR_CODES.length,
                        colorChipWidth, true, true, mouseX, mouseY);
                modalNoteFormatChips = drawNoteChipRow(font, x + 12, inputY + 26 + NOTE_CHIP_ROW_HEIGHT + 3,
                        NOTE_FORMAT_CODES.length, formatChipWidth, false, true, mouseX, mouseY);
            }
            modalConfirmBounds = new ModernMainLayout.Rect(x + width - 150, y + height - 27, 64, 20);
            modalCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + height - 27, 64, 20);
        } else {
            modalConfirmBounds = new ModernMainLayout.Rect(x + width - 150, y + height - 27, 64, 20);
            modalCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + height - 27, 64, 20);
        }
        drawButton(font, modalConfirmBounds, "gui.modern.path.wb.u316", true, modalMode == ModalMode.DELETE_SEQUENCE
                || modalMode == ModalMode.DELETE_CATEGORY || modalMode == ModalMode.DELETE_SUBCATEGORY, true,
                mouseX, mouseY);
        drawButton(font, modalCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
    }

    private void drawStepSettingsModal(FontRenderer font, int mouseX, int mouseY) {
        int width = Math.min(620, Math.max(420, bounds.width - 24));
        int height = Math.min(330, Math.max(300, bounds.height - 20));
        stepSettingsModalBounds = placePopup(width, height, modalAnchorX, modalAnchorY);
        int x = stepSettingsModalBounds.x;
        int y = stepSettingsModalBounds.y;
        width = stepSettingsModalBounds.width;
        height = stepSettingsModalBounds.height;
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0xB80A1016);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, width - 2, 30, 6, ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u144", x + 12, y + 9, ModernUiRenderer.TEXT, width - 24);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u317", x + 12, y + 39,
                ModernUiRenderer.SUBTLE_TEXT, width - 24);
        int left = x + 12;
        int top = y + 58;
        int controlX = left + 184;
        int controlWidth = Math.max(120, width - 208);
        drawStepSettingField(font, "step.settings.retry", "gui.modern.path.wb.u318", "gui.modern.path.wb.u319",
                "gui.modern.path.wb.u320",
                left, top, controlX, controlWidth, mouseX, mouseY);
        drawStepSettingField(font, "step.settings.timeout", "gui.modern.path.wb.u321", "gui.modern.path.wb.u322",
                "gui.modern.path.wb.u323",
                left, top + 42, controlX, controlWidth, mouseX, mouseY);
        drawStepSettingField(font, "step.settings.tolerance", "gui.modern.path.wb.u324", "gui.modern.path.wb.u325",
                "gui.modern.path.wb.u326",
                left, top + 84, controlX, controlWidth, mouseX, mouseY);

        int policyY = top + 126;
        drawStepSettingLabel(font, "gui.modern.path.wb.u327", "gui.modern.path.wb.u328",
                "gui.modern.path.wb.u329",
                left, policyY, mouseX, mouseY);
        stepSettingsPolicyBounds = new ModernMainLayout.Rect(controlX, policyY + 5, controlWidth, 25);
        drawStepSettingsSelect(font, stepSettingsPolicyBounds, policyLabel(stepSettingsPolicy), true, mouseX, mouseY);

        int targetY = top + 168;
        boolean targetEnabled = "RUN_SEQUENCE".equalsIgnoreCase(stepSettingsPolicy);
        drawStepSettingLabel(font, "gui.modern.path.wb.u330", "gui.modern.path.wb.u131",
                "gui.modern.path.wb.u331",
                left, targetY, mouseX, mouseY);
        stepSettingsTargetBounds = new ModernMainLayout.Rect(controlX, targetY + 5, controlWidth, 25);
        drawStepSettingsSelect(font, stepSettingsTargetBounds, targetEnabled ? stepTargetLabel() : "gui.modern.path.wb.u332",
                targetEnabled, mouseX, mouseY);
        stepSettingsConfirmBounds = new ModernMainLayout.Rect(x + width - 150, y + height - 27, 64, 20);
        stepSettingsCancelBounds = new ModernMainLayout.Rect(x + width - 78, y + height - 27, 64, 20);
        drawButton(font, stepSettingsConfirmBounds, "gui.modern.path.wb.u333", true, false, true, mouseX, mouseY);
        drawButton(font, stepSettingsCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
        if (stepSettingsPolicyOpen) {
            drawStepSettingsPolicyMenu(font, mouseX, mouseY);
        }
        if (stepTargetPickerOpen) {
            drawStepTargetPicker(font, mouseX, mouseY);
        }
    }

    private void drawStepSettingField(FontRenderer font, String key, String label, String summary, String detail,
            int left, int y, int controlX, int controlWidth, int mouseX, int mouseY) {
        drawStepSettingLabel(font, label, summary, detail, left, y, mouseX, mouseY);
        drawField(font, key, new ModernMainLayout.Rect(controlX, y + 5, controlWidth, 25), true, "gui.modern.path.wb.u334",
                mouseX, mouseY);
    }

    private void drawStepSettingLabel(FontRenderer font, String label, String summary, String detail,
            int left, int y, int mouseX, int mouseY) {
        ModernUiRenderer.drawText(font, label, left, y + 3, ModernUiRenderer.TEXT, 142);
        ModernUiRenderer.drawText(font, summary, left, y + 19, ModernUiRenderer.MUTED_TEXT, 142);
        int iconX = left + 156;
        int iconY = y + 11;
        ModernMainLayout.Rect icon = new ModernMainLayout.Rect(iconX - 3, iconY - 3, 16, 16);
        boolean hovered = icon.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(icon.x, icon.y, icon.width, icon.height, 4,
                    ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = detail;
        }
        ModernUiRenderer.drawInfoIcon(iconX, iconY, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
    }

    private void drawStepSettingsSelect(FontRenderer font, ModernMainLayout.Rect rect, String label, boolean enabled,
            int mouseX, int mouseY) {
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill = !enabled ? ModernUiRenderer.DISABLED_SURFACE
                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int text = ModernUiRenderer.readableText(enabled ? ModernUiRenderer.TEXT : ModernUiRenderer.DISABLED_TEXT, fill);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                fill,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(font, label, rect.x + 9, rect.y + 8,
                text, Math.max(20, rect.width - 30));
        ModernUiRenderer.drawChevron(rect.right() - 17, rect.y + 9, false,
                text);
    }

    private void drawStepSettingsPolicyMenu(FontRenderer font, int mouseX, int mouseY) {
        stepSettingsPolicyOptions.clear();
        String[] values = { "END_SEQUENCE", "RESTART_SEQUENCE", "RUN_SEQUENCE" };
        int rowHeight = 24;
        int menuY = stepSettingsPolicyBounds.bottom() + 3;
        ModernMainLayout.Rect menu = new ModernMainLayout.Rect(stepSettingsPolicyBounds.x, menuY,
                stepSettingsPolicyBounds.width, values.length * rowHeight + 6);
        ModernUiRenderer.drawPanel(menu.x, menu.y, menu.width, menu.height, 5,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(menu.x + 3, menu.y + 3 + i * rowHeight,
                    menu.width - 6, rowHeight - 1);
            RectAction option = new RectAction(policyLabel(value), true, false, () -> stepSettingsPolicy = value);
            option.bounds = row;
            stepSettingsPolicyOptions.add(option);
            boolean selected = value.equalsIgnoreCase(stepSettingsPolicy);
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                    selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                            : ModernUiRenderer.SHELL_RAISED,
                    selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, option.label, row.x + 8, row.y + 7,
                    selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 16);
        }
    }

    private void drawStepTargetPicker(FontRenderer font, int mouseX, int mouseY) {
        int width = Math.min(720, Math.max(520, bounds.width - 30));
        int height = Math.min(430, Math.max(320, bounds.height - 24));
        int x = bounds.x + (bounds.width - width) / 2;
        int y = bounds.y + (bounds.height - height) / 2;
        stepTargetPickerBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0xD00A1016);
        ModernUiRenderer.drawPanel(x, y, width, height, 7, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u335", x + 13, y + 10, ModernUiRenderer.TEXT, width - 100);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u336", x + 13, y + 27,
                ModernUiRenderer.MUTED_TEXT, width - 26);
        int paneY = y + 49;
        int paneHeight = height - 86;
        int navWidth = Math.max(190, width * 36 / 100);
        stepTargetNavigationBounds = new ModernMainLayout.Rect(x + 10, paneY, navWidth, paneHeight);
        stepTargetDetailsBounds = new ModernMainLayout.Rect(stepTargetNavigationBounds.right() + 8, paneY,
                width - navWidth - 28, paneHeight);
        ModernUiRenderer.drawSubtlePanel(stepTargetNavigationBounds.x, stepTargetNavigationBounds.y,
                stepTargetNavigationBounds.width, stepTargetNavigationBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(stepTargetDetailsBounds.x, stepTargetDetailsBounds.y,
                stepTargetDetailsBounds.width, stepTargetDetailsBounds.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        drawStepTargetNavigation(font, mouseX, mouseY);
        drawStepTargetDetails(font, mouseX, mouseY);
        stepTargetCancelBounds = new ModernMainLayout.Rect(x + width - 72, y + height - 28, 60, 20);
        drawButton(font, stepTargetCancelBounds, "gui.modern.path.wb.u084", false, false, true, mouseX, mouseY);
    }

    private void drawStepTargetNavigation(FontRenderer font, int mouseX, int mouseY) {
        stepTargetSequenceHits.clear();
        Map<String, List<PathSequence>> grouped = new LinkedHashMap<>();
        for (PathSequence sequence : sequences) {
            if (sequence == null || safe(sequence.getName()).trim().isEmpty()) continue;
            String category = safe(sequence.getCategory()).trim();
            String subCategory = safe(sequence.getSubCategory()).trim();
            String group = category.isEmpty() ? "gui.modern.path.wb.u021" : category;
            if (!subCategory.isEmpty()) group += " / " + subCategory;
            grouped.computeIfAbsent(group, key -> new ArrayList<>()).add(sequence);
        }
        int contentHeight = 4;
        for (List<PathSequence> group : grouped.values()) contentHeight += 22 + group.size() * 29;
        stepTargetNavigationMaxScroll = Math.max(0, contentHeight - stepTargetNavigationBounds.height);
        stepTargetNavigationScroll = clamp(stepTargetNavigationScroll, 0, stepTargetNavigationMaxScroll);
        ModernUiRenderer.beginClip(stepTargetNavigationBounds);
        int y = stepTargetNavigationBounds.y + 4 - stepTargetNavigationScroll;
        for (Map.Entry<String, List<PathSequence>> entry : grouped.entrySet()) {
            ModernUiRenderer.drawText(font, entry.getKey(), stepTargetNavigationBounds.x + 9, y + 5,
                    ModernUiRenderer.ACCENT, stepTargetNavigationBounds.width - 22);
            y += 22;
            for (PathSequence sequence : entry.getValue()) {
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(stepTargetNavigationBounds.x + 5, y,
                        ModernHoverScrollbar.contentWidth(stepTargetNavigationBounds.width - 7), 26);
                stepTargetSequenceHits.add(new StepTargetSequenceHit(sequence, row));
                boolean selected = safe(sequence.getName()).equalsIgnoreCase(stepSettingsTargetSequence);
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                                : ModernUiRenderer.SHELL,
                        selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(font, safe(sequence.getName()), row.x + 8, row.y + 5,
                        selected || hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, row.width - 16);
                y += 29;
            }
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(stepTargetNavScrollBar, stepTargetNavigationBounds, stepTargetNavigationScroll,
                stepTargetNavigationMaxScroll, Math.max(1, contentHeight), stepTargetNavigationBounds.height,
                mouseX, mouseY, value -> stepTargetNavigationScroll = value);
    }

    private void drawStepTargetDetails(FontRenderer font, int mouseX, int mouseY) {
        stepTargetActionHits.clear();
        PathSequence target = findSequence(stepSettingsTargetSequence);
        if (target == null) {
            ModernUiRenderer.drawText(font, "gui.modern.path.wb.u337", stepTargetDetailsBounds.x + 12,
                    stepTargetDetailsBounds.y + 16, ModernUiRenderer.MUTED_TEXT, stepTargetDetailsBounds.width - 24);
            return;
        }
        int contentHeight = 5;
        for (PathStep step : target.getSteps()) {
            contentHeight += 29 + Math.max(1, step.getActions().size()) * 27;
        }
        stepTargetDetailsMaxScroll = Math.max(0, contentHeight - stepTargetDetailsBounds.height);
        stepTargetDetailsScroll = clamp(stepTargetDetailsScroll, 0, stepTargetDetailsMaxScroll);
        ModernUiRenderer.beginClip(stepTargetDetailsBounds);
        int y = stepTargetDetailsBounds.y + 5 - stepTargetDetailsScroll;
        for (int stepIndex = 0; stepIndex < target.getSteps().size(); stepIndex++) {
            PathStep step = target.getSteps().get(stepIndex);
            ModernMainLayout.Rect stepRow = new ModernMainLayout.Rect(stepTargetDetailsBounds.x + 5, y,
                    ModernHoverScrollbar.contentWidth(stepTargetDetailsBounds.width - 7), 25);
            stepTargetActionHits.add(new StepTargetActionHit(stepIndex, 0, stepRow));
            boolean stepHovered = stepRow.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(stepRow.x, stepRow.y, stepRow.width, stepRow.height, 4,
                    stepHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                    stepHovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.run_from_first", String.valueOf(stepIndex + 1)),
                    stepRow.x + 8, stepRow.y + 7, ModernUiRenderer.TEXT, stepRow.width - 16);
            y += 29;
            List<ActionData> actions = step.getActions();
            if (actions.isEmpty()) {
                ModernUiRenderer.drawText(font, "gui.modern.path.wb.u338", stepRow.x + 15, y + 6,
                        ModernUiRenderer.MUTED_TEXT, stepRow.width - 24);
                y += 27;
                continue;
            }
            for (int actionIndex = 0; actionIndex < actions.size(); actionIndex++) {
                ActionData action = actions.get(actionIndex);
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(stepTargetDetailsBounds.x + 14, y,
                        ModernHoverScrollbar.contentWidth(stepTargetDetailsBounds.width - 16), 24);
                stepTargetActionHits.add(new StepTargetActionHit(stepIndex, actionIndex, row));
                boolean selected = stepIndex == stepSettingsTargetStepIndex
                        && actionIndex == stepSettingsTargetActionIndex;
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 3,
                        selected ? ModernUiRenderer.SURFACE_PRESSED : hovered ? ModernUiRenderer.SURFACE_HOVER
                                : ModernUiRenderer.SHELL,
                        selected || hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                String type = action == null ? "?" : safe(action.type);
                ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.fmt.action_type", String.valueOf(actionIndex + 1), prettyType(type)),
                        row.x + 8, row.y + 6, selected || hovered ? ModernUiRenderer.TEXT
                                : ModernUiRenderer.SUBTLE_TEXT, row.width - 16);
                if (hovered && action != null) hoveredTooltip = safe(action.getDescription());
                y += 27;
            }
        }
        ModernUiRenderer.endClip();
        drawScrollBarFor(stepTargetDetailsScrollBar, stepTargetDetailsBounds, stepTargetDetailsScroll,
                stepTargetDetailsMaxScroll, Math.max(1, contentHeight), stepTargetDetailsBounds.height,
                mouseX, mouseY, value -> stepTargetDetailsScroll = value);
    }

    private boolean modalNeedsInput() {
        return modalMode != ModalMode.DELETE_SEQUENCE && modalMode != ModalMode.DELETE_CATEGORY
                && modalMode != ModalMode.DELETE_SUBCATEGORY;
    }

    private String toolKey(PathWorkbenchToolCatalog.ToolId tool) {
        return tool == null ? "" : tool.zone.name() + "." + tool.id;
    }

    private ModernMainLayout.Rect drawToolStrip(FontRenderer font, ModernMainLayout.Rect area, String title,
            PathWorkbenchToolCatalog.Zone zone, List<PathWorkbenchToolCatalog.ToolId> tools, int mouseX, int mouseY) {
        if (area == null) {
            return null;
        }
        boolean collapsible = zone == PathWorkbenchToolCatalog.Zone.NAVIGATION
                || zone == PathWorkbenchToolCatalog.Zone.STEP
                || zone == PathWorkbenchToolCatalog.Zone.ACTION
                || zone == PathWorkbenchToolCatalog.Zone.RECORDING_STEP
                || zone == PathWorkbenchToolCatalog.Zone.RECORDING_ACTION
                || zone == PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL;
        boolean expanded = !collapsible || isToolStripExpanded(zone);
        ModernUiRenderer.drawSubtlePanel(area.x, area.y, area.width, area.height, 5, ModernUiRenderer.SURFACE,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernMainLayout.Rect settings = new ModernMainLayout.Rect(Math.max(area.x + 1, area.right() - 20),
                area.y + 3, 16, 16);
        ModernMainLayout.Rect toggle = null;
        if (collapsible) {
            toggle = new ModernMainLayout.Rect(Math.max(area.x + 1, settings.x - 35), area.y + 2, 30, 20);
            setToolStripToggleBounds(zone, toggle);
        }
        int titleRight = toggle == null ? settings.x : toggle.x;
        ModernUiRenderer.drawText(font, title, area.x + 8, area.y + 5, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(24, titleRight - area.x - 12));
        if (toggle != null) {
            drawToolStripToggle(toggle, expanded, mouseX, mouseY,
                    expanded ? "gui.modern.path.wb.toolbar.collapse" : "gui.modern.path.wb.toolbar.expand");
        }
        drawIconButton(settings, "settings", mouseX, mouseY, tr("gui.modern.path.wb.u339") + " · " + tr(title));
        if (!expanded) {
            return settings;
        }
        int innerWidth = Math.max(1, area.width - 16);
        int columns = Math.max(1, PathWorkbenchToolCatalog.wrapColumns(innerWidth));
        if (!tools.isEmpty() && isRecordingTool(tools.get(0))) {
            columns = Math.min(columns, tools.size());
        }
        int gap = 3;
        int buttonWidth = Math.max(1, (innerWidth - gap * Math.max(0, columns - 1)) / columns);
        int x = area.x + 8;
        int y = area.y + 20;
        int column = 0;
        for (int i = 0; i < tools.size(); i++) {
            PathWorkbenchToolCatalog.ToolId tool = tools.get(i);
            if (column >= columns) {
                column = 0;
                x = area.x + 8;
                y += TOOL_BUTTON_HEIGHT + 4;
            }
            int width;
            if (i == tools.size() - 1 || column == columns - 1) {
                width = Math.max(1, area.right() - 8 - x);
            } else {
                width = buttonWidth;
            }
            ModernMainLayout.Rect rect = new ModernMainLayout.Rect(x, y, width, TOOL_BUTTON_HEIGHT);
            // A tool strip can become shorter than its wrapped rows at narrow
            // aspect ratios. Never paint or register hit targets outside the
            // strip; otherwise navigation buttons leak into the footer below.
            if (rect.bottom() > area.bottom()) {
                continue;
            }
            toolHits.put(toolKey(tool), rect);
            drawButton(font, rect, toolLabel(tool), tool.primary, tool.danger, isToolEnabled(tool), tool.shortcut,
                    mouseX, mouseY);
            x += width + gap;
            column++;
        }
        return settings;
    }

    private boolean isToolStripExpanded(PathWorkbenchToolCatalog.Zone zone) {
        if (zone == PathWorkbenchToolCatalog.Zone.NAVIGATION) {
            return navToolsExpanded;
        }
        if (zone == PathWorkbenchToolCatalog.Zone.STEP) {
            return stepToolsExpanded;
        }
        if (zone == PathWorkbenchToolCatalog.Zone.ACTION) {
            return actionToolsExpanded;
        }
        if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_STEP) {
            return recordingStepToolsExpanded;
        }
        if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_ACTION) {
            return recordingActionToolsExpanded;
        }
        if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL) {
            return recordingControlToolsExpanded;
        }
        return true;
    }

    private void setToolStripToggleBounds(PathWorkbenchToolCatalog.Zone zone, ModernMainLayout.Rect bounds) {
        if (zone == PathWorkbenchToolCatalog.Zone.NAVIGATION) {
            navToolsToggleBounds = bounds;
        } else if (zone == PathWorkbenchToolCatalog.Zone.STEP) {
            stepToolsToggleBounds = bounds;
        } else if (zone == PathWorkbenchToolCatalog.Zone.ACTION) {
            actionToolsToggleBounds = bounds;
        } else if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_STEP) {
            recordingStepToolsToggleBounds = bounds;
        } else if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_ACTION) {
            recordingActionToolsToggleBounds = bounds;
        } else if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL) {
            recordingControlToolsToggleBounds = bounds;
        }
    }

    private void drawToolStripToggle(ModernMainLayout.Rect rect, boolean expanded, int mouseX, int mouseY,
            String tooltip) {
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 5,
                hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                hovered ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawCollapseChevron(rect.x + rect.width / 2, rect.y + rect.height / 2,
                !expanded, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
        if (hovered) {
            hoveredTooltip = tr(tooltip);
        }
    }

    private void drawDropMarker(ModernMainLayout.Rect list, int rowHeight, int dropIndex, int scroll) {
        if (list == null || dropIndex < 0) {
            return;
        }
        int dropY = list.y + (dropIndex * rowHeight) - scroll - 1;
        if (dropY >= list.y - 2 && dropY <= list.bottom() + 2) {
            ModernUiRenderer.drawRoundedRect(list.x + 2, dropY, Math.max(1, list.width - 8), 2, 1,
                    ModernUiRenderer.ACCENT);
        }
    }

    private boolean handleToolStripClick(int mouseX, int mouseY) {
        if (navToolsToggleBounds != null && navToolsToggleBounds.contains(mouseX, mouseY)) {
            navToolsExpanded = !navToolsExpanded;
            return true;
        }
        if (stepToolsToggleBounds != null && stepToolsToggleBounds.contains(mouseX, mouseY)) {
            stepToolsExpanded = !stepToolsExpanded;
            return true;
        }
        if (actionToolsToggleBounds != null && actionToolsToggleBounds.contains(mouseX, mouseY)) {
            actionToolsExpanded = !actionToolsExpanded;
            return true;
        }
        if (recordingStepToolsToggleBounds != null && recordingStepToolsToggleBounds.contains(mouseX, mouseY)) {
            recordingStepToolsExpanded = !recordingStepToolsExpanded;
            return true;
        }
        if (recordingActionToolsToggleBounds != null && recordingActionToolsToggleBounds.contains(mouseX, mouseY)) {
            recordingActionToolsExpanded = !recordingActionToolsExpanded;
            return true;
        }
        if (navToolsSettingsBounds != null && navToolsSettingsBounds.contains(mouseX, mouseY)) {
            openCustomizePanel(PathWorkbenchToolCatalog.Zone.NAVIGATION, navToolsSettingsBounds);
            return true;
        }
        if (stepToolsSettingsBounds != null && stepToolsSettingsBounds.contains(mouseX, mouseY)) {
            openCustomizePanel(PathWorkbenchToolCatalog.Zone.STEP, stepToolsSettingsBounds);
            return true;
        }
        if (actionToolsSettingsBounds != null && actionToolsSettingsBounds.contains(mouseX, mouseY)) {
            openCustomizePanel(PathWorkbenchToolCatalog.Zone.ACTION, actionToolsSettingsBounds);
            return true;
        }
        for (Map.Entry<String, ModernMainLayout.Rect> entry : toolHits.entrySet()) {
            ModernMainLayout.Rect rect = entry.getValue();
            if (rect == null || !rect.contains(mouseX, mouseY)) {
                continue;
            }
            PathWorkbenchToolCatalog.ToolId tool = toolFromKey(entry.getKey());
            if (tool != null && isToolEnabled(tool)) {
                runTool(tool);
            }
            return true;
        }
        return false;
    }

    private PathWorkbenchToolCatalog.ToolId toolFromKey(String key) {
        if (key == null || !key.contains(".")) {
            return null;
        }
        int split = key.indexOf('.');
        try {
            PathWorkbenchToolCatalog.Zone zone = PathWorkbenchToolCatalog.Zone.valueOf(key.substring(0, split));
            return PathWorkbenchToolCatalog.ToolId.byId(zone, key.substring(split + 1));
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isToolEnabled(PathWorkbenchToolCatalog.ToolId tool) {
        if (tool == null) {
            return false;
        }
        if (isRecordingTool(tool)) return isRecordingToolEnabled(tool);
        if (tool.zone == PathWorkbenchToolCatalog.Zone.HEADER) {
            return true;
        }
        switch (tool) {
            case NAV_ADD_CATEGORY:
            case NAV_CATEGORY_MANAGE:
            case NAV_RELOAD:
                return true;
            case NAV_ADD_SUBCATEGORY:
            case NAV_ADD_SEQUENCE:
                return !selectedCategory.isEmpty();
            case NAV_COPY_SEQUENCE:
            case NAV_RUN_FROM_STEP:
                return selectedSequence != null;
            case NAV_RENAME_SEQUENCE:
            case NAV_MOVE_SEQUENCE:
            case NAV_DELETE_SEQUENCE:
            case NAV_SEQUENCE_UP:
            case NAV_SEQUENCE_DOWN:
                return isEditableSequence();
            case STEP_ADD:
                return isEditableSequence();
            case STEP_COPY:
                return selectedSequence != null && !selectedStepIndexList().isEmpty();
            case STEP_PASTE:
                return isEditableSequence() && clipboardPayloadType == ClipboardPayloadType.STEPS
                        && !stepClipboard.isEmpty();
            case STEP_DELETE:
            case STEP_GET_COORDS:
            case STEP_CLEAR_COORDS:
            case STEP_SETTINGS:
                return canEditStep();
            case STEP_UP:
                return canEditStep() && canMoveSelectedSteps(-1);
            case STEP_DOWN:
                return canEditStep() && canMoveSelectedSteps(1);
            case ACTION_ADD:
            case ACTION_INSERT_TEMPLATE:
                return canEditStep();
            case ACTION_EDIT:
            case ACTION_DELETE:
                return canEditStep() && selectedAction != null;
            case ACTION_COPY:
                return selectedStep != null && !selectedActionIndexList().isEmpty();
            case ACTION_PASTE:
                return canEditStep() && clipboardPayloadType == ClipboardPayloadType.ACTIONS
                        && !actionClipboard.isEmpty();
            case ACTION_UP:
                return canEditStep() && canMoveSelectedActions(-1);
            case ACTION_DOWN:
                return canEditStep() && canMoveSelectedActions(1);
            case ACTION_BUILTIN_DELAY:
                return true;
            default:
                return false;
        }
    }

    private void runTool(PathWorkbenchToolCatalog.ToolId tool) {
        if (tool == null) {
            return;
        }
        if (isRecordingTool(tool)) {
            runRecordingTool(tool);
            return;
        }
        switch (tool) {
            case HEADER_VALIDATE:
                openValidation();
                break;
            case HEADER_RECORD:
                if (routeRequest != null) {
                    requestNativeRoute("recording");
                } else {
                    openRecording();
                }
                break;
            case HEADER_LOG:
                openExecutionLogRoute();
                break;
            case HEADER_TEMPLATES:
                openActionTemplatesRoute();
                break;
            case HEADER_VARIABLES:
                openActionVariablesRoute();
                break;
            case HEADER_TRIGGERS:
                openTriggerRules();
                break;
            case HEADER_RELOAD:
                reloadDraft();
                break;
            case NAV_ADD_CATEGORY:
                beginModal(ModalMode.ADD_CATEGORY, "gui.modern.path.wb.u340", "gui.modern.path.wb.u341");
                break;
            case NAV_ADD_SUBCATEGORY:
                if (selectedCategory.isEmpty()) {
                    status("gui.modern.path.wb.u342");
                } else {
                    beginModal(ModalMode.ADD_SUBCATEGORY, "gui.modern.path.wb.u343", selectedCategory);
                }
                break;
            case NAV_CATEGORY_MANAGE:
                openCategoryMenu(lastMouseX, lastMouseY);
                break;
            case NAV_ADD_SEQUENCE:
                beginModal(ModalMode.ADD_SEQUENCE, "gui.modern.path.wb.u344", selectedCategory);
                break;
            case NAV_COPY_SEQUENCE:
                copySelectedSequence();
                break;
            case NAV_RENAME_SEQUENCE:
                if (isEditableSequence()) {
                    beginModal(ModalMode.RENAME_SEQUENCE, "gui.modern.path.wb.u345",
                            selectedSequence == null ? "" : selectedSequence.getName());
                }
                break;
            case NAV_MOVE_SEQUENCE:
                if (isEditableSequence()) {
                    beginModal(ModalMode.MOVE_SEQUENCE, "gui.modern.path.wb.u346", selectedCategory);
                }
                break;
            case NAV_DELETE_SEQUENCE:
                if (isEditableSequence()) {
                    beginModal(ModalMode.DELETE_SEQUENCE, "gui.modern.path.wb.u347",
                            selectedSequence == null ? "" : selectedSequence.getName());
                }
                break;
            case NAV_SEQUENCE_UP:
                moveSequence(-1);
                break;
            case NAV_SEQUENCE_DOWN:
                moveSequence(1);
                break;
            case NAV_RUN_FROM_STEP:
                openRunFromStepModal();
                break;
            case NAV_RELOAD:
                reloadDraft();
                break;
            case STEP_ADD:
                addStep();
                break;
            case STEP_COPY:
                copySelectedStepsToClipboard();
                break;
            case STEP_PASTE:
                pasteClipboardIntoSelection();
                break;
            case STEP_DELETE:
                deleteStep();
                break;
            case STEP_UP:
                moveStep(-1);
                break;
            case STEP_DOWN:
                moveStep(1);
                break;
            case STEP_GET_COORDS:
                captureStepCoordinates();
                break;
            case STEP_CLEAR_COORDS:
                if (canEditStep()) {
                    pushHistory("clear-coordinates");
                    selectedStep.setGotoPoint(new double[] { Double.NaN, Double.NaN, Double.NaN });
                    bindEditorFields();
                    dirty = true;
                }
                break;
            case STEP_SETTINGS:
                openStepSettings();
                break;
            case ACTION_ADD:
                openActionEditorPage(true);
                break;
            case ACTION_EDIT:
                openActionEditorPage(false);
                break;
            case ACTION_COPY:
                copySelectedActionsToClipboard();
                break;
            case ACTION_PASTE:
                pasteClipboardIntoSelection();
                break;
            case ACTION_DELETE:
                deleteAction();
                break;
            case ACTION_UP:
                moveAction(-1);
                break;
            case ACTION_DOWN:
                moveAction(1);
                break;
            case ACTION_INSERT_TEMPLATE:
                openTemplates();
                break;
            case ACTION_BUILTIN_DELAY:
                openBuiltinDelaySettings();
                break;
            default:
                break;
        }
    }

    private void openOverflowMenu(PathWorkbenchToolCatalog.Zone zone, int mouseX, int mouseY) {
        List<PathWorkbenchToolCatalog.ToolId> overflow = PathWorkbenchToolCatalog.get().overflow(zone);
        if (overflow.isEmpty()) {
            return;
        }
        menuTitle = zone == PathWorkbenchToolCatalog.Zone.ACTION ? "gui.modern.path.wb.u039"
                : zone == PathWorkbenchToolCatalog.Zone.STEP ? "gui.modern.path.wb.u023"
                        : zone == PathWorkbenchToolCatalog.Zone.HEADER ? "gui.modern.path.wb.u132" : "gui.modern.path.wb.u019";
        if (zone == PathWorkbenchToolCatalog.Zone.RECORDING_STEP
                || zone == PathWorkbenchToolCatalog.Zone.RECORDING_ACTION
                || zone == PathWorkbenchToolCatalog.Zone.RECORDING_CONTROL) menuTitle = zone.title;
        menuItems.clear();
        for (PathWorkbenchToolCatalog.ToolId tool : overflow) {
            menuItems.add(new RectAction(toolLabel(tool), tool.shortcut, isToolEnabled(tool), tool.danger, () -> runTool(tool)));
        }
        setMenuAnchor(mouseX, mouseY);
    }

    private void openCustomizePanel(PathWorkbenchToolCatalog.Zone zone, ModernMainLayout.Rect anchor) {
        closeMenu();
        customizeZone = zone;
        customizeBounds = null;
        if (anchor != null) {
            menuAnchorX = anchor.right();
            menuAnchorY = anchor.bottom();
        }
    }

    private void closeCustomizePanel() {
        customizeZone = null;
        customizeBounds = null;
        customizeHits.clear();
    }

    private void drawCustomizePanel(FontRenderer font, int mouseX, int mouseY) {
        if (customizeZone == null) {
            return;
        }
        List<PathWorkbenchToolCatalog.ToolId> tools = PathWorkbenchToolCatalog.get().all(customizeZone);
        int rowHeight = 22;
        int titleHeight = 40;
        int width = 230;
        int height = titleHeight + 10 + tools.size() * rowHeight;
        int x = clamp(menuAnchorX, bounds.x + 8, Math.max(bounds.x + 8, bounds.right() - width - 8));
        int y = clamp(menuAnchorY, bounds.y + 8, Math.max(bounds.y + 8, bounds.bottom() - height - 8));
        customizeBounds = new ModernMainLayout.Rect(x, y, width, height);
        customizeHits.clear();
        ModernUiRenderer.drawBackdropOverlay(new ModernMainLayout.Rect(bounds.x, bounds.y, bounds.width, bounds.height),
                0x22000000);
        ModernUiRenderer.drawPanel(x, y, width, height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(x + 1, y + 1, Math.max(1, width - 2), titleHeight, 5,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(font, tr("gui.modern.path.wb.u339") + " · " + tr(customizeZone.title), x + 9, y + 7,
                ModernUiRenderer.TEXT, width - 18);
        ModernUiRenderer.drawText(font, "gui.modern.path.wb.u348", x + 9, y + 22,
                ModernUiRenderer.MUTED_TEXT, width - 18);
        for (int i = 0; i < tools.size(); i++) {
            PathWorkbenchToolCatalog.ToolId tool = tools.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(x + 6, y + titleHeight + 6 + i * rowHeight,
                    width - 12, rowHeight - 2);
            ModernMainLayout.Rect toggleBounds = new ModernMainLayout.Rect(row.right() - 40, row.y, 40, row.height);
            customizeHits.add(new CustomizeHit(tool, row, toggleBounds));
            boolean pinned = PathWorkbenchToolCatalog.get().isPinned(customizeZone, tool);
            boolean hovered = row.contains(mouseX, mouseY);
            boolean toggleHovered = toggleBounds.contains(mouseX, mouseY);
            if (hovered) {
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        ModernUiRenderer.SURFACE_HOVER, ModernUiRenderer.BORDER_SUBTLE);
            }
            ModernUiRenderer.drawText(font, tool.label, row.x + 8, row.y + 6, ModernUiRenderer.TEXT,
                    Math.max(40, row.width - 52));
            ModernUiRenderer.drawToggle(row.right() - 30, row.y + 4, 22, 12, pinned, toggleHovered);
        }
    }

    private boolean handleCustomizeClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            closeCustomizePanel();
            return true;
        }
        if (customizeBounds == null || !customizeBounds.contains(mouseX, mouseY)) {
            closeCustomizePanel();
            return true;
        }
        for (CustomizeHit hit : customizeHits) {
            if (hit.bounds != null && hit.bounds.contains(mouseX, mouseY)) {
                if (hit.toggleBounds != null && hit.toggleBounds.contains(mouseX, mouseY)) {
                    PathWorkbenchToolCatalog.get().togglePinned(customizeZone, hit.tool);
                } else if (isToolEnabled(hit.tool)) {
                    closeCustomizePanel();
                    runTool(hit.tool);
                }
                return true;
            }
        }
        return true;
    }

    private boolean handleWorkbenchShortcut(int keyCode) {
        if (isControlDown() && keyCode == Keyboard.KEY_C) {
            if (listFocus == ListFocus.ACTION || (listFocus != ListFocus.STEP && listFocus != ListFocus.NAVIGATION
                    && !selectedActionIndexList().isEmpty())) {
                copySelectedActionsToClipboard();
            } else if (listFocus == ListFocus.NAVIGATION) {
                copySequenceToClipboard();
            } else {
                copySelectedStepsToClipboard();
            }
            return true;
        }
        if (isControlDown() && keyCode == Keyboard.KEY_V) {
            pasteClipboardIntoSelection();
            return true;
        }
        if (isControlDown() && (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN)) {
            int direction = keyCode == Keyboard.KEY_UP ? -1 : 1;
            if (listFocus == ListFocus.ACTION) {
                moveAction(direction);
            } else if (listFocus == ListFocus.NAVIGATION) {
                moveSequence(direction);
            } else {
                moveStep(direction);
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            if (listFocus == ListFocus.ACTION && selectedAction != null) {
                openActionEditorPage(false);
                return true;
            }
            if (listFocus == ListFocus.STEP && canEditStep()) {
                openStepSettings();
                return true;
            }
            return false;
        }
        if (keyCode == Keyboard.KEY_DELETE) {
            if (listFocus == ListFocus.ACTION) {
                deleteAction();
                return true;
            }
            if (listFocus == ListFocus.STEP) {
                deleteStep();
                return true;
            }
            if (listFocus == ListFocus.NAVIGATION && isEditableSequence()) {
                beginModal(ModalMode.DELETE_SEQUENCE, "gui.modern.path.wb.u347",
                        selectedSequence == null ? "" : selectedSequence.getName());
                return true;
            }
        }
        return false;
    }

    private void copySelectedStepsToClipboard() {
        if (selectedSequence == null) {
            return;
        }
        List<Integer> indices = selectedStepIndexList();
        if (indices.isEmpty()) {
            return;
        }
        stepClipboard.clear();
        actionClipboard.clear();
        for (Integer index : indices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < selectedSequence.getSteps().size()) {
                stepClipboard.add(new PathStep(selectedSequence.getSteps().get(index.intValue())));
            }
        }
        clipboardPayloadType = ClipboardPayloadType.STEPS;
        status(tr("gui.modern.path.wb.fmt.copied_steps", String.valueOf(stepClipboard.size())));
    }

    private void copySequenceToClipboard() {
        if (selectedSequence == null) return;
        sequenceClipboard = new PathSequence(selectedSequence);
        stepClipboard.clear();
        actionClipboard.clear();
        clipboardPayloadType = ClipboardPayloadType.SEQUENCE;
        status(tr("gui.modern.path.wb.ctx.copied_sequence", safe(selectedSequence.getName())));
    }

    private void pasteSequenceFromClipboard() {
        if (clipboardPayloadType != ClipboardPayloadType.SEQUENCE || sequenceClipboard == null) return;
        String base = safe(sequenceClipboard.getName());
        String name = base + " copy";
        int suffix = 2;
        while (findSequence(name) != null) name = base + " copy " + suffix++;
        pushHistory("paste-sequence");
        PathSequence copy = new PathSequence(sequenceClipboard);
        copy.setName(name);
        copy.setCustom(true);
        copy.setCategory(selectedCategory.isEmpty() ? defaultCategory() : selectedCategory);
        copy.setSubCategory(selectedSubCategory);
        sequences.add(copy);
        addCategoryLocal(copy.getCategory());
        addSubCategoryLocal(copy.getCategory(), copy.getSubCategory());
        dirty = true;
        selectSequenceByName(name);
        listFocus = ListFocus.NAVIGATION;
        status(tr("gui.modern.path.wb.ctx.pasted_sequence", name));
    }

    private void copySelectedActionsToClipboard() {
        if (selectedStep == null) {
            return;
        }
        List<Integer> indices = selectedActionIndexList();
        if (indices.isEmpty()) {
            return;
        }
        actionClipboard.clear();
        stepClipboard.clear();
        for (Integer index : indices) {
            if (index != null && index.intValue() >= 0 && index.intValue() < selectedStep.getActions().size()) {
                actionClipboard.add(new ActionData(selectedStep.getActions().get(index.intValue())));
            }
        }
        clipboardPayloadType = ClipboardPayloadType.ACTIONS;
        status(tr("gui.modern.path.wb.fmt.copied_actions", String.valueOf(actionClipboard.size())));
    }

    private void pasteClipboardIntoSelection() {
        if (clipboardPayloadType == ClipboardPayloadType.SEQUENCE) {
            pasteSequenceFromClipboard();
            return;
        }
        if (clipboardPayloadType == ClipboardPayloadType.STEPS && selectedSequence != null && !stepClipboard.isEmpty()) {
            if (!isEditableSequence()) {
                status("gui.modern.path.wb.u102");
                return;
            }
            pushHistory("paste-steps");
            List<Integer> selectedIndices = selectedStepIndexList();
            int insertIndex = selectedIndices.isEmpty()
                    ? selectedSequence.getSteps().size()
                    : Math.min(selectedSequence.getSteps().size(),
                            selectedIndices.get(selectedIndices.size() - 1).intValue() + 1);
            selectedStepIndices.clear();
            for (PathStep copied : stepClipboard) {
                selectedSequence.getSteps().add(insertIndex, new PathStep(copied));
                selectedStepIndices.add(Integer.valueOf(insertIndex));
                insertIndex++;
            }
            int last = selectedStepIndices.isEmpty() ? -1
                    : new ArrayList<Integer>(selectedStepIndices).get(selectedStepIndices.size() - 1).intValue();
            if (last >= 0) {
                selectedStepIndex = last;
                selectionAnchorStepIndex = last;
            }
            selectedActionIndex = -1;
            selectedActionIndices.clear();
            listFocus = ListFocus.STEP;
            refreshSelection();
            dirty = true;
            status(tr("gui.modern.path.wb.fmt.pasted_steps", String.valueOf(stepClipboard.size())));
            return;
        }
        if (clipboardPayloadType == ClipboardPayloadType.ACTIONS && selectedStep != null && !actionClipboard.isEmpty()) {
            if (!canEditStep()) {
                status("gui.modern.path.wb.u102");
                return;
            }
            pushHistory("paste-actions");
            List<Integer> selectedIndices = selectedActionIndexList();
            int insertIndex = selectedIndices.isEmpty()
                    ? selectedStep.getActions().size()
                    : Math.min(selectedStep.getActions().size(),
                            selectedIndices.get(selectedIndices.size() - 1).intValue() + 1);
            selectedActionIndices.clear();
            for (ActionData copied : actionClipboard) {
                selectedStep.getActions().add(insertIndex, new ActionData(copied));
                selectedActionIndices.add(Integer.valueOf(insertIndex));
                insertIndex++;
            }
            int last = selectedActionIndices.isEmpty() ? -1
                    : new ArrayList<Integer>(selectedActionIndices).get(selectedActionIndices.size() - 1).intValue();
            if (last >= 0) {
                selectedActionIndex = last;
                selectionAnchorActionIndex = last;
            }
            listFocus = ListFocus.ACTION;
            refreshSelection();
            dirty = true;
            status(tr("gui.modern.path.wb.fmt.pasted_actions", String.valueOf(actionClipboard.size())));
        }
    }

    private void applyStepSelectionClick(int index, boolean ctrl, boolean shift) {
        if (selectedSequence == null || index < 0 || index >= selectedSequence.getSteps().size()) {
            return;
        }
        listFocus = ListFocus.STEP;
        if (shift && selectionAnchorStepIndex >= 0 && selectionAnchorStepIndex < selectedSequence.getSteps().size()) {
            selectedStepIndices.clear();
            int start = Math.min(selectionAnchorStepIndex, index);
            int end = Math.max(selectionAnchorStepIndex, index);
            for (int i = start; i <= end; i++) {
                selectedStepIndices.add(Integer.valueOf(i));
            }
            setActiveStepPreservingSelection(index);
            return;
        }
        if (ctrl) {
            if (selectedStepIndices.contains(Integer.valueOf(index)) && selectedStepIndices.size() > 1) {
                selectedStepIndices.remove(Integer.valueOf(index));
                int fallback = selectedStepIndices.iterator().next().intValue();
                setActiveStepPreservingSelection(fallback);
            } else {
                selectedStepIndices.add(Integer.valueOf(index));
                selectionAnchorStepIndex = index;
                setActiveStepPreservingSelection(index);
            }
            return;
        }
        selectStep(index);
    }

    private void applyActionSelectionClick(int index, boolean ctrl, boolean shift) {
        if (selectedStep == null || index < 0 || index >= selectedStep.getActions().size()) {
            return;
        }
        listFocus = ListFocus.ACTION;
        if (shift && selectionAnchorActionIndex >= 0 && selectionAnchorActionIndex < selectedStep.getActions().size()) {
            selectedActionIndices.clear();
            int start = Math.min(selectionAnchorActionIndex, index);
            int end = Math.max(selectionAnchorActionIndex, index);
            for (int i = start; i <= end; i++) {
                selectedActionIndices.add(Integer.valueOf(i));
            }
            setActiveActionPreservingSelection(index);
            return;
        }
        if (ctrl) {
            if (selectedActionIndices.contains(Integer.valueOf(index)) && selectedActionIndices.size() > 1) {
                selectedActionIndices.remove(Integer.valueOf(index));
                int fallback = selectedActionIndices.iterator().next().intValue();
                setActiveActionPreservingSelection(fallback);
            } else {
                selectedActionIndices.add(Integer.valueOf(index));
                selectionAnchorActionIndex = index;
                setActiveActionPreservingSelection(index);
            }
            return;
        }
        selectAction(index);
    }

    private void setActiveStepPreservingSelection(int index) {
        if (selectedSequence == null || index < 0 || index >= selectedSequence.getSteps().size()) {
            selectedStepIndex = -1;
            selectedStep = null;
            selectedActionIndex = -1;
            selectedAction = null;
            selectedActionIndices.clear();
            return;
        }
        selectedStepIndex = index;
        selectedStep = selectedSequence.getSteps().get(index);
        selectedActionIndex = -1;
        selectedAction = null;
        selectedActionIndices.clear();
        actionScroll = 0;
        parameterScroll = 0;
        bindEditorFields();
    }

    private void setActiveActionPreservingSelection(int index) {
        if (selectedStep == null || index < 0 || index >= selectedStep.getActions().size()) {
            selectedActionIndex = -1;
            selectedAction = null;
            return;
        }
        selectedActionIndex = index;
        selectedAction = selectedStep.getActions().get(index);
        parameterScroll = 0;
        bindEditorFields();
    }

    private void selectStepRange(int start, int count) {
        selectedStepIndices.clear();
        if (selectedSequence == null) {
            return;
        }
        int safeStart = clamp(start, 0, Math.max(0, selectedSequence.getSteps().size() - 1));
        int safeCount = Math.max(1, count);
        for (int i = 0; i < safeCount && safeStart + i < selectedSequence.getSteps().size(); i++) {
            selectedStepIndices.add(Integer.valueOf(safeStart + i));
        }
        selectedStepIndex = safeStart + Math.min(safeCount, selectedSequence.getSteps().size() - safeStart) - 1;
        selectionAnchorStepIndex = selectedStepIndex;
        listFocus = ListFocus.STEP;
    }

    private void selectActionRange(int start, int count) {
        selectedActionIndices.clear();
        if (selectedStep == null) {
            return;
        }
        int safeStart = clamp(start, 0, Math.max(0, selectedStep.getActions().size() - 1));
        int safeCount = Math.max(1, count);
        for (int i = 0; i < safeCount && safeStart + i < selectedStep.getActions().size(); i++) {
            selectedActionIndices.add(Integer.valueOf(safeStart + i));
        }
        selectedActionIndex = safeStart + Math.min(safeCount, selectedStep.getActions().size() - safeStart) - 1;
        selectionAnchorActionIndex = selectedActionIndex;
        listFocus = ListFocus.ACTION;
    }

    private void syncStepSelectionSet() {
        selectedStepIndices.clear();
        if (selectedStepIndex >= 0) {
            selectedStepIndices.add(Integer.valueOf(selectedStepIndex));
            selectionAnchorStepIndex = selectedStepIndex;
        } else {
            selectionAnchorStepIndex = -1;
        }
    }

    private void syncActionSelectionSet() {
        selectedActionIndices.clear();
        if (selectedActionIndex >= 0) {
            selectedActionIndices.add(Integer.valueOf(selectedActionIndex));
            selectionAnchorActionIndex = selectedActionIndex;
        } else {
            selectionAnchorActionIndex = -1;
        }
    }

    private List<Integer> selectedStepIndexList() {
        List<Integer> indices = new ArrayList<Integer>(selectedStepIndices);
        if (indices.isEmpty() && selectedStepIndex >= 0) {
            indices.add(Integer.valueOf(selectedStepIndex));
        }
        Collections.sort(indices);
        return indices;
    }

    private List<Integer> selectedActionIndexList() {
        List<Integer> indices = new ArrayList<Integer>(selectedActionIndices);
        if (indices.isEmpty() && selectedActionIndex >= 0) {
            indices.add(Integer.valueOf(selectedActionIndex));
        }
        Collections.sort(indices);
        return indices;
    }

    private List<Integer> consecutiveSelectedSteps() {
        return consecutiveBlock(selectedStepIndexList());
    }

    private List<Integer> consecutiveSelectedActions() {
        return consecutiveBlock(selectedActionIndexList());
    }

    private List<Integer> consecutiveBlock(List<Integer> indices) {
        if (indices == null || indices.isEmpty()) {
            return indices;
        }
        for (int i = 1; i < indices.size(); i++) {
            if (indices.get(i).intValue() != indices.get(i - 1).intValue() + 1) {
                return null;
            }
        }
        return indices;
    }

    private boolean canMoveSelectedSteps(int direction) {
        List<Integer> block = consecutiveSelectedSteps();
        if (block == null || block.isEmpty() || selectedSequence == null) {
            return false;
        }
        int start = block.get(0).intValue();
        int count = block.size();
        return direction < 0 ? start > 0 : start + count < selectedSequence.getSteps().size();
    }

    private boolean canMoveSelectedActions(int direction) {
        List<Integer> block = consecutiveSelectedActions();
        if (block == null || block.isEmpty() || selectedStep == null) {
            return false;
        }
        int start = block.get(0).intValue();
        int count = block.size();
        return direction < 0 ? start > 0 : start + count < selectedStep.getActions().size();
    }

    private String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

    private <T> int moveBlock(List<T> list, int source, int count, int target, String history) {
        if (list == null || source < 0 || count <= 0 || source + count > list.size()) {
            return -1;
        }
        int insert = target;
        if (insert > source) {
            insert -= count;
        }
        insert = clamp(insert, 0, list.size() - count);
        if (insert == source) {
            return -1;
        }
        pushHistory(history);
        List<T> moved = new ArrayList<T>();
        for (int i = 0; i < count; i++) {
            moved.add(list.remove(source));
        }
        insert = clamp(insert, 0, list.size());
        list.addAll(insert, moved);
        return insert;
    }

}
