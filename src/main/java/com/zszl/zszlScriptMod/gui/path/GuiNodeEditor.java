package com.zszl.zszlScriptMod.gui.path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zszl.zszlScriptMod.gui.components.GuiTextInput;
import com.zszl.zszlScriptMod.gui.components.ThemedButton;
import com.zszl.zszlScriptMod.gui.components.ThemedGuiScreen;
import com.zszl.zszlScriptMod.gui.modern.ModernRuleEditorUi;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.path.node.NodeCanvasNote;
import com.zszl.zszlScriptMod.path.node.NodeEdge;
import com.zszl.zszlScriptMod.path.node.NodeEditorDraftManager;
import com.zszl.zszlScriptMod.path.node.NodeExecutionContext;
import com.zszl.zszlScriptMod.path.node.NodeGraph;
import com.zszl.zszlScriptMod.path.node.NodeGraphValidator;
import com.zszl.zszlScriptMod.path.node.NodeGroupBox;
import com.zszl.zszlScriptMod.path.node.NodeNode;
import com.zszl.zszlScriptMod.path.node.NodeSequenceRunner;
import com.zszl.zszlScriptMod.path.node.NodeSequenceStorage;
import com.zszl.zszlScriptMod.shadowbaritone.utils.GuiPathingPolicy;
import com.zszl.zszlScriptMod.system.ProfileManager;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;

public class GuiNodeEditor extends ThemedGuiScreen {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final int BTN_BACK = 1;
    private static final int BTN_SAVE = 2;
    private static final int BTN_VALIDATE = 3;
    private static final int BTN_RUN = 4;
    private static final int BTN_STEP = 5;
    private static final int BTN_CONTINUE = 6;
    private static final int BTN_AUTO_LAYOUT = 7;
    private static final int BTN_RESET_VIEW = 8;
    private static final int BTN_FOCUS_START = 9;
    private static final int BTN_ADD_START = 10;
    private static final int BTN_ADD_ACTION = 11;
    private static final int BTN_ADD_IF = 12;
    private static final int BTN_ADD_SETVAR = 13;
    private static final int BTN_ADD_WAIT = 14;
    private static final int BTN_ADD_END = 15;
    private static final int BTN_ADD_TRIGGER = 16;
    private static final int BTN_ADD_RUN_SEQUENCE = 17;
    private static final int BTN_ADD_SUBGRAPH = 18;
    private static final int BTN_ADD_PARALLEL = 19;
    private static final int BTN_HOTKEYS = 20;
    private static final int BTN_ADD_JOIN = 22;
    private static final int BTN_ADD_DELAY_TASK = 23;
    private static final int BTN_ADD_RESOURCE_LOCK = 24;
    private static final int BTN_ADD_RESULT_CACHE = 25;
    private static final int BTN_EDIT_PARAMS = 126;
    private static final int BTN_EDIT_DATA = 120;
    private static final int BTN_DELETE_NODE = 121;
    private static final int BTN_RESTORE_DRAFT = 127;
    private static final int BTN_DISCARD_DRAFT = 128;
    private static final int BTN_TOGGLE_BREAKPOINT = 129;
    private static final int BTN_CLEAR_BREAKPOINTS = 130;
    private static final int BTN_TOGGLE_IGNORE = 131;
    private static final int BTN_TOGGLE_CONSOLE = 132;
    private static final int BTN_GROUP_FLOW = 100;
    private static final int BTN_GROUP_TRIGGER = 101;
    private static final int BTN_GROUP_VAR = 102;
    private static final int BTN_GROUP_ADVANCED = 103;
    private static final int BTN_PANEL_LIBRARY = 104;
    private static final int BTN_PANEL_WORKFLOW = 105;
    private static final int BTN_WORKFLOW_NEW = 106;
    private static final int BTN_WORKFLOW_RENAME = 107;
    private static final int BTN_WORKFLOW_PASTE = 108;
    private static final int BTN_WORKFLOW_COPY = 109;

    private static final int TOOLBAR_WIDTH = 110;
    private static final int PROPERTY_WIDTH = 220;
    private static final int TOP_BAR_HEIGHT = 28;
    private static final int CONSOLE_HEIGHT = 120;
    private static final int NODE_WIDTH = 108;
    private static final int NODE_HEIGHT = 108;
    private static final int PORT_SIZE = 8;
    private static final int GRID_SIZE = 20;

    private final GuiScreen parent;
    private final List<NodeNode> clipboardNodes = new ArrayList<>();
    private final List<NodeEdge> clipboardEdges = new ArrayList<>();
    private final List<String> undoStack = new ArrayList<>();
    private final List<String> redoStack = new ArrayList<>();

    private final List<NodeGraph> graphs = new ArrayList<>();
    private NodeGraph currentGraph;
    private int currentGraphIndex = -1;

    private NodeNode selectedNode;
    private final List<String> selectedNodeIds = new ArrayList<>();
    private boolean draggingNode = false;
    private boolean selectingBox = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private int selectionStartX = 0;
    private int selectionStartY = 0;
    private int selectionEndX = 0;
    private int selectionEndY = 0;
    private String lastClickedNodeId = null;
    private long lastClickedNodeTimeMs = 0L;

    private String linkingFromNodeId = null;
    private String linkingFromPort = null;
    private String hoveredEdgeId = null;
    private String selectedEdgeId = null;
    private String hoveredPortNodeId = null;
    private String hoveredPortName = null;
    private boolean hoveredPortOutput = false;
    private String hoveredPortTooltip = "";
    private String linkPreviewTargetNodeId = null;
    private String linkPreviewTargetPort = null;
    private boolean linkPreviewCompatible = false;
    private int previewLinkMouseX = -1;
    private int previewLinkMouseY = -1;
    private int alignGuideX = Integer.MIN_VALUE;
    private int alignGuideY = Integer.MIN_VALUE;

    private GuiTextField nodeIdField;
    private GuiTextField nodeSearchField;
    private GuiTextField quickAddSearchField;

    private String statusMessage = "";
    private int statusColor = 0xFFFFFFFF;
    private boolean quickAddVisible = false;
    private int quickAddX = 0;
    private int quickAddY = 0;
    private int quickAddWidth = 240;
    private int quickAddHeight = 176;
    private int quickAddTargetCanvasX = 0;
    private int quickAddTargetCanvasY = 0;
    private int quickAddHoverIndex = -1;
    private int quickAddScrollOffset = 0;
    private long lastBlankCanvasClickTimeMs = 0L;
    private String runtimeHighlightedNodeId = null;
    private String runtimeErrorNodeId = null;
    private NodeSequenceRunner activeRunner = null;
    private NodeExecutionContext activeContext = null;
    private boolean breakpointPauseNotified = false;
    private boolean popupVisible = false;
    private String popupTitle = "";
    private String popupMessage = "";
    private final List<String> liveValidationErrors = new ArrayList<>();
    private int validationRefreshTicker = 0;
    private final List<NodeParameterSchemaRegistry.FieldSchema> activePropertySchemas = new ArrayList<>();
    private final List<GuiTextField> propertyFormTextFields = new ArrayList<>();
    private final List<GuiButton> propertyFormButtons = new ArrayList<>();
    private String propertyTooltipText = "";

    private int canvasX;
    private int canvasY;
    private int canvasWidth;
    private int canvasHeight;
    private int propertyX;
    private int propertyY;
    private int propertyHeight;
    private int consoleX;
    private int consoleY;
    private int consoleWidth;
    private int consoleHeight;
    private int consoleScrollOffset = 0;
    private boolean consoleVisible = false;
    private boolean propertyPanelVisible = false;
    private int canvasOffsetX = 0;
    private int canvasOffsetY = 0;
    private float canvasZoom = 1.0f;
    private boolean panningCanvas = false;
    private int panStartMouseX = 0;
    private int panStartMouseY = 0;
    private int panStartOffsetX = 0;
    private int panStartOffsetY = 0;
    private int toolbarTreeX = 10;
    private int toolbarTreeY = TOP_BAR_HEIGHT + 54;
    private int toolbarTreeWidth = TOOLBAR_WIDTH - 20;
    private int toolbarButtonWidth = TOOLBAR_WIDTH - 26;
    private int toolbarTreeHeight = 200;
    private int toolbarScrollOffset = 0;
    private int toolbarContentHeight = 0;
    private int propertyScrollOffset = 0;
    private int propertyContentHeight = 0;
    private int miniMapX = 0;
    private int miniMapY = 0;
    private int miniMapW = 110;
    private int miniMapH = 80;
    private boolean groupFlowExpanded = false;
    private boolean groupTriggerExpanded = false;
    private boolean groupVarExpanded = false;
    private boolean groupAdvancedExpanded = false;
    private final List<String> favoriteNodeTypes = new ArrayList<>();
    private final List<String> recentNodeTypes = new ArrayList<>();
    private final List<LibraryVisibleRow> visibleLibraryRows = new ArrayList<>();
    private final List<WorkflowVisibleRow> visibleWorkflowRows = new ArrayList<>();
    private final List<LibraryNode> libraryRoots = new ArrayList<>();
    private final LinkedHashSet<String> expandedLibraryGroupIds = new LinkedHashSet<>();
    private LibraryNode draggingLibraryNode = null;
    private String draggingLibraryNodeType = null;
    private String draggingLibraryLabel = null;
    private boolean draggingLibraryItem = false;
    private boolean draggingLibraryMoved = false;
    private int draggingLibraryStartX = 0;
    private int draggingLibraryStartY = 0;
    private NodeGraph workflowClipboardGraph = null;
    private String workflowClipboardName = "";
    private boolean workflowListFocused = false;
    private boolean draggingWorkflowRow = false;
    private boolean draggingWorkflowMoved = false;
    private int workflowDragStartY = 0;
    private int workflowDragSourceIndex = -1;
    private int workflowDragTargetIndex = -1;
    private boolean workflowContextMenuVisible = false;
    private int workflowContextMenuX = 0;
    private int workflowContextMenuY = 0;
    private WorkflowVisibleRow workflowContextTargetRow = null;
    private final List<WorkflowContextAction> workflowContextActions = new ArrayList<>();

    private static final int LIBRARY_ROW_HEIGHT = 18;
    private static final int WORKFLOW_ROW_HEIGHT = 20;
    private static final int LIBRARY_INDENT = 12;
    private static final int LIBRARY_NAME_WIDTH = TOOLBAR_WIDTH - 34;
    private static final int LEFT_PANEL_MODE_BUTTON_Y = TOP_BAR_HEIGHT + 30;
    private static final int LEFT_PANEL_SEARCH_LABEL_Y = TOP_BAR_HEIGHT + 52;
    private static final int LEFT_PANEL_SEARCH_FIELD_Y = TOP_BAR_HEIGHT + 62;
    private static final int LEFT_PANEL_WORKFLOW_ACTION_Y = TOP_BAR_HEIGHT + 68;
    private static final int LEFT_PANEL_TREE_Y_LIBRARY = TOP_BAR_HEIGHT + 82;
    private static final int LEFT_PANEL_TREE_Y_WORKFLOW = TOP_BAR_HEIGHT + 52;
    private static final String LEFT_PANEL_MODE_LIBRARY = "library";
    private static final String LEFT_PANEL_MODE_WORKFLOW = "workflow";

    private static final class LibraryNode {
        String id;
        String label;
        String nodeType;
        JsonObject presetData;
        List<LibraryNode> children = new ArrayList<>();

        static LibraryNode group(String id, String label, LibraryNode... children) {
            LibraryNode node = new LibraryNode();
            node.id = id;
            node.label = label;
            node.children = children == null ? new ArrayList<LibraryNode>()
                    : new ArrayList<LibraryNode>(Arrays.asList(children));
            return node;
        }

        static LibraryNode item(String id, String label, String nodeType) {
            LibraryNode node = new LibraryNode();
            node.id = id;
            node.label = label;
            node.nodeType = nodeType;
            return node;
        }

        static LibraryNode item(String id, String label, String nodeType, JsonObject presetData) {
            LibraryNode node = item(id, label, nodeType);
            node.presetData = presetData;
            return node;
        }

        boolean isGroup() {
            return children != null && !children.isEmpty();
        }
    }

    private static final class LibraryVisibleRow {
        LibraryNode node;
        int depth;
        int contentY;
    }

    private static final class WorkflowVisibleRow {
        NodeGraph graph;
        int contentY;
    }

    private static final class QuickAddEntry {
        LibraryNode node;
        String searchText;
    }

    private static final class WorkflowContextAction {
        String id;
        String label;
        boolean enabled;

        WorkflowContextAction(String id, String label, boolean enabled) {
            this.id = id;
            this.label = label;
            this.enabled = enabled;
        }
    }

    private static final class LibraryPanelState {
        List<String> expandedGroups = new ArrayList<>();
        List<String> favoriteTypes = new ArrayList<>();
        List<String> recentTypes = new ArrayList<>();
    }

    private static final class PortHit {
        NodeNode node;
        String port;
        boolean output;

        PortHit(NodeNode node, String port, boolean output) {
            this.node = node;
            this.port = port;
            this.output = output;
        }
    }

    public GuiNodeEditor() {
        this(null);
    }

    private String leftPanelMode = LEFT_PANEL_MODE_LIBRARY;

    public GuiNodeEditor(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        NodeEditorHotkeyManager.load();
        loadCurrentGraph();

        updateLayoutMetrics();
        this.propertyScrollOffset = 0;
        this.propertyContentHeight = 0;
        this.propertyPanelVisible = selectedNode != null;

        this.nodeIdField = null;
        this.nodeSearchField = new GuiTextField(1002, this.fontRenderer, 12, LEFT_PANEL_SEARCH_FIELD_Y,
                TOOLBAR_WIDTH - 16,
                16);
        this.nodeSearchField.setMaxStringLength(32);
        this.quickAddSearchField = new GuiTextField(1003, this.fontRenderer, 0, 0, 160, 16);
        this.quickAddSearchField.setMaxStringLength(48);
        this.quickAddSearchField.setVisible(false);
        updateSelectedNodeFields();
        initializeLibraryTreeIfNeeded();
        loadLibraryPanelState();

        int topButtonW = 54;
        int topY = 5;
        this.buttonList.add(new ThemedButton(BTN_BACK, 6, topY, 46, 18, I18n.format("gui.node_editor.back")));
        this.buttonList.add(new ThemedButton(BTN_SAVE, 56, topY, topButtonW, 18,
                "§a" + I18n.format("gui.node_editor.save")));
        this.buttonList.add(new ThemedButton(BTN_VALIDATE, 56 + topButtonW + 4, topY, topButtonW, 18,
                "§b" + I18n.format("gui.node_editor.validate")));
        this.buttonList.add(new ThemedButton(BTN_RUN, 56 + (topButtonW + 4) * 2, topY, topButtonW, 18,
                "§e" + I18n.format("gui.node_editor.run")));
        this.buttonList.add(new ThemedButton(BTN_STEP, 56 + (topButtonW + 4) * 3, topY, topButtonW, 18,
                "§d" + I18n.format("gui.node_editor.step")));
        this.buttonList.add(new ThemedButton(BTN_CONTINUE, 56 + (topButtonW + 4) * 4, topY, topButtonW, 18,
                "§b" + I18n.format("gui.node_editor.continue")));
        this.buttonList.add(new ThemedButton(BTN_AUTO_LAYOUT, 56 + (topButtonW + 4) * 5, topY, topButtonW, 18,
                "§a" + I18n.format("gui.node_editor.layout")));
        this.buttonList.add(new ThemedButton(BTN_RESET_VIEW, 56 + (topButtonW + 4) * 6, topY, topButtonW, 18,
                "§e重置视图"));
        this.buttonList.add(new ThemedButton(BTN_FOCUS_START, 56 + (topButtonW + 4) * 7, topY, topButtonW, 18,
                "§b定位开始"));
        this.buttonList.add(new ThemedButton(BTN_HOTKEYS, 56 + (topButtonW + 4) * 8, topY, topButtonW + 10, 18,
                "§d快捷键"));
        this.buttonList
                .add(new ThemedButton(BTN_TOGGLE_CONSOLE, 56 + (topButtonW + 4) * 9 + 10, topY, topButtonW + 4, 18,
                        "§7控制台"));

        int statusTop = this.height - 22;
        toolbarTreeX = 10;
        toolbarTreeY = LEFT_PANEL_TREE_Y_LIBRARY;
        toolbarTreeWidth = TOOLBAR_WIDTH - 20;
        toolbarButtonWidth = toolbarTreeWidth - 16;
        toolbarTreeHeight = Math.max(64, statusTop - toolbarTreeY - 6);
        int toolbarX = toolbarTreeX;
        int toolbarY = toolbarTreeY;
        int btnW = TOOLBAR_WIDTH - 16;
        int btnH = 18;
        int gap = 3;
        this.buttonList.add(new ThemedButton(BTN_PANEL_LIBRARY, toolbarX, LEFT_PANEL_MODE_BUTTON_Y, 44, 18, "节点库"));
        this.buttonList
                .add(new ThemedButton(BTN_PANEL_WORKFLOW, toolbarX + 48, LEFT_PANEL_MODE_BUTTON_Y, 44, 18, "工作流"));
        this.buttonList
                .add(new ThemedButton(BTN_WORKFLOW_NEW, toolbarX, LEFT_PANEL_WORKFLOW_ACTION_Y, 40, btnH, "§a新建"));
        this.buttonList.add(
                new ThemedButton(BTN_WORKFLOW_RENAME, toolbarX + 44, LEFT_PANEL_WORKFLOW_ACTION_Y, 46, btnH, "§b重命名"));
        this.buttonList.add(
                new ThemedButton(BTN_WORKFLOW_COPY, toolbarX, LEFT_PANEL_WORKFLOW_ACTION_Y + btnH + 4, 40, btnH,
                        "§d复制"));
        this.buttonList
                .add(new ThemedButton(BTN_WORKFLOW_PASTE, toolbarX + 44, LEFT_PANEL_WORKFLOW_ACTION_Y + btnH + 4, 46,
                        btnH, "§e粘贴"));
        this.buttonList.add(new ThemedButton(BTN_GROUP_FLOW, toolbarX, toolbarY, btnW, btnH, "§b流程 / 条件"));
        this.buttonList.add(new ThemedButton(BTN_ADD_START, toolbarX, toolbarY + (btnH + gap), btnW, btnH,
                I18n.format("gui.node_editor.add.start")));
        this.buttonList.add(new ThemedButton(BTN_ADD_ACTION, toolbarX, toolbarY + (btnH + gap) * 2, btnW, btnH,
                I18n.format("gui.node_editor.add.action")));
        this.buttonList.add(new ThemedButton(BTN_ADD_IF, toolbarX, toolbarY + (btnH + gap) * 3, btnW, btnH,
                I18n.format("gui.node_editor.add.if")));
        this.buttonList.add(new ThemedButton(BTN_ADD_WAIT, toolbarX, toolbarY + (btnH + gap) * 4, btnW, btnH,
                I18n.format("gui.node_editor.add.waituntil")));
        this.buttonList.add(new ThemedButton(BTN_ADD_END, toolbarX, toolbarY + (btnH + gap) * 5, btnW, btnH,
                I18n.format("gui.node_editor.add.end")));
        this.buttonList.add(new ThemedButton(BTN_ADD_RUN_SEQUENCE, toolbarX, toolbarY + (btnH + gap) * 6, btnW, btnH,
                I18n.format("gui.node_editor.add.runsequence")));
        this.buttonList.add(new ThemedButton(BTN_ADD_SUBGRAPH, toolbarX, toolbarY + (btnH + gap) * 7, btnW, btnH,
                I18n.format("gui.node_editor.add.subgraph")));
        this.buttonList.add(new ThemedButton(BTN_ADD_PARALLEL, toolbarX, toolbarY + (btnH + gap) * 8, btnW, btnH,
                I18n.format("gui.node_editor.add.parallel")));
        this.buttonList.add(new ThemedButton(BTN_ADD_JOIN, toolbarX, toolbarY + (btnH + gap) * 9, btnW, btnH,
                I18n.format("gui.node_editor.add.join")));
        this.buttonList.add(new ThemedButton(BTN_ADD_DELAY_TASK, toolbarX, toolbarY + (btnH + gap) * 10, btnW, btnH,
                I18n.format("gui.node_editor.add.delaytask")));

        int triggerBaseY = toolbarY + (btnH + gap) * 11;
        this.buttonList.add(new ThemedButton(BTN_GROUP_TRIGGER, toolbarX, triggerBaseY, btnW, btnH, "§d触发器"));
        this.buttonList.add(new ThemedButton(BTN_ADD_TRIGGER, toolbarX, triggerBaseY + (btnH + gap), btnW, btnH,
                I18n.format("gui.node_editor.add.trigger")));

        int varBaseY = triggerBaseY + (btnH + gap) * 2;
        this.buttonList.add(new ThemedButton(BTN_GROUP_VAR, toolbarX, varBaseY, btnW, btnH, "§6变量 / 缓存"));
        this.buttonList.add(new ThemedButton(BTN_ADD_SETVAR, toolbarX, varBaseY + (btnH + gap), btnW, btnH,
                I18n.format("gui.node_editor.add.setvar")));
        this.buttonList.add(new ThemedButton(BTN_ADD_RESULT_CACHE, toolbarX, varBaseY + (btnH + gap) * 2, btnW, btnH,
                I18n.format("gui.node_editor.add.resultcache")));

        int advancedBaseY = varBaseY + (btnH + gap) * 3;
        this.buttonList.add(new ThemedButton(BTN_GROUP_ADVANCED, toolbarX, advancedBaseY, btnW, btnH, "§7高级控制"));
        this.buttonList.add(new ThemedButton(BTN_ADD_RESOURCE_LOCK, toolbarX, advancedBaseY + (btnH + gap), btnW,
                btnH, I18n.format("gui.node_editor.add.resourcelock")));

        this.buttonList.add(new ThemedButton(BTN_EDIT_PARAMS, propertyX + 10, propertyY + 82, PROPERTY_WIDTH - 20, 20,
                "§b参数编辑"));
        this.buttonList.add(new ThemedButton(BTN_EDIT_DATA, propertyX + 10, propertyY + 106, PROPERTY_WIDTH - 20, 20,
                "§7高级 JSON 编辑"));
        this.buttonList.add(new ThemedButton(BTN_DELETE_NODE, propertyX + 10, propertyY + 130, PROPERTY_WIDTH - 20,
                20, "§c" + I18n.format("gui.node_editor.delete_node")));
        this.buttonList.add(new ThemedButton(BTN_RESTORE_DRAFT, propertyX + 10, propertyY + 154,
                PROPERTY_WIDTH - 20, 20, "§e恢复草稿"));
        this.buttonList.add(new ThemedButton(BTN_DISCARD_DRAFT, propertyX + 10, propertyY + 178,
                PROPERTY_WIDTH - 20, 20, "§7放弃草稿"));
        this.buttonList.add(new ThemedButton(BTN_TOGGLE_BREAKPOINT, propertyX + 10, propertyY + 202,
                PROPERTY_WIDTH - 20, 20, "§e切换当前节点断点"));
        this.buttonList.add(new ThemedButton(BTN_CLEAR_BREAKPOINTS, propertyX + 10, propertyY + 226,
                PROPERTY_WIDTH - 20, 20, "§c清空全部断点"));
        this.buttonList.add(new ThemedButton(BTN_TOGGLE_IGNORE, propertyX + 10, propertyY + 250,
                PROPERTY_WIDTH - 20, 20, "§c忽略当前节点"));

        updateToolbarButtonVisibility();
    }

    private void loadCurrentGraph() {
        graphs.clear();
        NodeSequenceStorage.LoadResult result = NodeSequenceStorage.loadAll();
        if (result.isCompatible()) {
            graphs.addAll(result.getSequences());
        }

        if (graphs.isEmpty()) {
            currentGraph = new NodeGraph();
            currentGraph.setName("node_graph_1");
            currentGraph.setNodes(new ArrayList<NodeNode>());
            currentGraph.setEdges(new ArrayList<NodeEdge>());
            graphs.add(currentGraph);
            currentGraphIndex = 0;
        } else {
            currentGraphIndex = 0;
            currentGraph = graphs.get(0);
            if (currentGraph.getNodes() == null) {
                currentGraph.setNodes(new ArrayList<NodeNode>());
            }
            if (currentGraph.getEdges() == null) {
                currentGraph.setEdges(new ArrayList<NodeEdge>());
            }
        }

        NodeGraph draft = NodeEditorDraftManager.loadDraft(currentGraph == null ? "" : currentGraph.getName());
        if (draft != null) {
            currentGraph = draft;
            if (currentGraphIndex >= 0 && currentGraphIndex < graphs.size()) {
                graphs.set(currentGraphIndex, currentGraph);
            }
            setStatus("已恢复未保存草稿", 0xFFFFFF88);
        }

        if (selectedNode != null) {
            selectedNode = findNodeById(selectedNode.getId());
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        applyFieldChanges();

        switch (button.id) {
            case BTN_BACK:
                this.mc.displayGuiScreen(parent == null ? com.zszl.zszlScriptMod.gui.GuiModernMainScreen.getPersistentScreen() : parent);
                return;
            case BTN_SAVE:
                saveGraph();
                return;
            case BTN_VALIDATE:
                validateGraph();
                return;
            case BTN_RUN:
                runGraph();
                return;
            case BTN_STEP:
                stepGraph();
                return;
            case BTN_CONTINUE:
                continueGraph();
                return;
            case BTN_AUTO_LAYOUT:
                autoLayoutNodes();
                return;
            case BTN_RESET_VIEW:
                resetCanvasView();
                return;
            case BTN_FOCUS_START:
                focusStartNode();
                return;
            case BTN_HOTKEYS:
                this.mc.displayGuiScreen(new GuiNodeEditorHotkeyManager(this));
                return;
            case BTN_PANEL_LIBRARY:
                leftPanelMode = LEFT_PANEL_MODE_LIBRARY;
                workflowListFocused = false;
                workflowContextMenuVisible = false;
                updateToolbarButtonVisibility();
                return;
            case BTN_PANEL_WORKFLOW:
                leftPanelMode = LEFT_PANEL_MODE_WORKFLOW;
                workflowListFocused = false;
                workflowContextMenuVisible = false;
                if (nodeSearchField != null) {
                    nodeSearchField.setFocused(false);
                }
                updateToolbarButtonVisibility();
                return;
            case BTN_WORKFLOW_NEW:
                createNewWorkflow();
                return;
            case BTN_WORKFLOW_RENAME:
                renameCurrentWorkflow();
                return;
            case BTN_WORKFLOW_COPY:
                duplicateCurrentWorkflow();
                return;
            case BTN_WORKFLOW_PASTE:
                pasteWorkflowFromClipboard();
                return;
            case BTN_GROUP_FLOW:
                groupFlowExpanded = !groupFlowExpanded;
                updateToolbarButtonVisibility();
                return;
            case BTN_GROUP_TRIGGER:
                groupTriggerExpanded = !groupTriggerExpanded;
                updateToolbarButtonVisibility();
                return;
            case BTN_GROUP_VAR:
                groupVarExpanded = !groupVarExpanded;
                updateToolbarButtonVisibility();
                return;
            case BTN_GROUP_ADVANCED:
                groupAdvancedExpanded = !groupAdvancedExpanded;
                updateToolbarButtonVisibility();
                return;
            case BTN_ADD_START:
                addNode(NodeNode.TYPE_START);
                return;
            case BTN_ADD_ACTION:
                addNode(NodeNode.TYPE_ACTION);
                return;
            case BTN_ADD_IF:
                addNode(NodeNode.TYPE_IF);
                return;
            case BTN_ADD_SETVAR:
                addNode(NodeNode.TYPE_SET_VAR);
                return;
            case BTN_ADD_WAIT:
                addNode(NodeNode.TYPE_WAIT_UNTIL);
                return;
            case BTN_ADD_END:
                addNode(NodeNode.TYPE_END);
                return;
            case BTN_ADD_TRIGGER:
                addNode(NodeNode.TYPE_TRIGGER);
                return;
            case BTN_ADD_RUN_SEQUENCE:
                addNode(NodeNode.TYPE_RUN_SEQUENCE);
                return;
            case BTN_ADD_SUBGRAPH:
                addNode(NodeNode.TYPE_SUBGRAPH);
                return;
            case BTN_ADD_PARALLEL:
                addNode(NodeNode.TYPE_PARALLEL);
                return;
            case BTN_ADD_JOIN:
                addNode(NodeNode.TYPE_JOIN);
                return;
            case BTN_ADD_DELAY_TASK:
                addNode(NodeNode.TYPE_DELAY_TASK);
                return;
            case BTN_ADD_RESOURCE_LOCK:
                addNode(NodeNode.TYPE_RESOURCE_LOCK);
                return;
            case BTN_ADD_RESULT_CACHE:
                addNode(NodeNode.TYPE_RESULT_CACHE);
                return;
            case BTN_EDIT_PARAMS:
                editSelectedNodeParams();
                return;
            case BTN_EDIT_DATA:
                editSelectedNodeData();
                return;
            case BTN_DELETE_NODE:
                deleteSelectedNode();
                return;
            case BTN_RESTORE_DRAFT:
                restoreDraft();
                return;
            case BTN_DISCARD_DRAFT:
                discardDraft();
                return;
            case BTN_TOGGLE_BREAKPOINT:
                toggleBreakpoint(selectedNode);
                return;
            case BTN_CLEAR_BREAKPOINTS:
                ensureDebugContext();
                activeContext.clearBreakpoints();
                setStatus("已清空全部断点", 0xFFCCCCCC);
                return;
            case BTN_TOGGLE_IGNORE:
                toggleIgnoreSelectedNode();
                return;
            case BTN_TOGGLE_CONSOLE:
                consoleVisible = !consoleVisible;
                updateLayoutMetrics();
                updateConsoleToggleButtonLabel();
                setStatus(consoleVisible ? "已显示控制台" : "已隐藏控制台", 0xFF88FF88);
                return;
            default:
                break;
        }
    }

    private void addNode(String type) {
        addNodeWithData(type, null, null, null);
    }

    private void addNodeWithData(String type, JsonObject overrideData, Integer canvasPosX, Integer canvasPosY) {
        if (currentGraph == null) {
            return;
        }

        NodeNode node = new NodeNode();
        node.setId(createUniqueNodeId(type));
        node.setType(type);
        int centerX = canvasPosX == null ? screenToCanvasX(canvasX + canvasWidth / 2) : canvasPosX.intValue();
        int centerY = canvasPosY == null ? screenToCanvasY(canvasY + canvasHeight / 2) : canvasPosY.intValue();
        int spreadX = (currentGraph.getNodes().size() % 3 - 1) * 36;
        int spreadY = (currentGraph.getNodes().size() / 3 % 3 - 1) * 24;
        node.setX(centerX - NODE_WIDTH / 2 + spreadX);
        node.setY(centerY - NODE_HEIGHT / 2 + spreadY);
        node.setData(overrideData == null ? createDefaultData(type) : overrideData);
        pushUndoState();
        currentGraph.getNodes().add(node);
        selectedNode = node;
        propertyPanelVisible = true;
        propertyScrollOffset = 0;
        recordRecentNodeType(type);
        updateSelectedNodeFields();
        saveDraftSilently();
        setStatus("已添加节点: " + node.getId(), 0xFF88FF88);
    }

    private JsonObject createDefaultData(String type) {
        return NodeParameterSchemaRegistry.createDefaultData(type);
    }

    private void saveGraph() {
        if (currentGraph == null) {
            return;
        }

        applyFieldChanges();
        if (!validateGraphInternal(true)) {
            showPopup("保存失败", statusMessage);
            return;
        }

        try {
            if (currentGraphIndex >= 0 && currentGraphIndex < graphs.size()) {
                graphs.set(currentGraphIndex, currentGraph);
            } else if (!graphs.contains(currentGraph)) {
                graphs.add(currentGraph);
            }
            NodeSequenceStorage.saveAll(graphs);
            NodeEditorDraftManager.removeDraft(currentGraph.getName());
            setStatus("节点图已保存: " + safe(currentGraph.getName()), 0xFF88FF88);
            sendChat("§a[节点编辑器] 已保存节点图: " + safe(currentGraph.getName()));
        } catch (Exception e) {
            setStatus("保存失败: " + e.getMessage(), 0xFFFF8888);
            showPopup("保存失败", "原因: " + e.getMessage());
            sendChat("§c[节点编辑器] 保存失败: " + e.getMessage());
        }
    }

    private void validateGraph() {
        validateGraphInternal(false);
    }

    private boolean validateGraphInternal(boolean silentSuccess) {
        if (currentGraph == null) {
            setStatus("当前没有可校验的节点图", 0xFFFF8888);
            return false;
        }

        NodeGraphValidator.ValidationResult result = NodeGraphValidator.validate(currentGraph);
        if (!result.isValid()) {
            String message = result.getErrors().isEmpty() ? "校验失败" : result.getErrors().get(0);
            setStatus("校验失败: " + message, 0xFFFF8888);
            sendChat("§c[节点编辑器] 校验失败: " + message);
            return false;
        }

        if (!silentSuccess) {
            setStatus("校验通过", 0xFF88FF88);
            sendChat("§a[节点编辑器] 校验通过");
        }
        return true;
    }

    private void runGraph() {
        if (!validateGraphInternal(true)) {
            showPopup("运行前校验失败", statusMessage);
            return;
        }
        if (Minecraft.getMinecraft().player == null) {
            setStatus("当前无玩家实例，无法运行", 0xFFFF8888);
            showPopup("运行失败", "当前无玩家实例，无法运行");
            return;
        }

        activeContext = new NodeExecutionContext();
        activeContext.setVariable("triggerSource", "manual:node_editor");
        activeContext.setVariable("triggerType", "manual");
        activeContext.setRunMode(NodeExecutionContext.RunMode.CONTINUE);
        activeContext.addConsoleLine("[RUN] 手动启动图: " + safe(currentGraph == null ? null : currentGraph.getName()));
        activeRunner = new NodeSequenceRunner(currentGraph, activeContext);
        runtimeHighlightedNodeId = activeRunner.getCurrentNodeId();
        runtimeErrorNodeId = null;
        breakpointPauseNotified = false;
        setStatus("节点图开始运行", 0xFFFFFF88);
    }

    private void stepGraph() {
        if (Minecraft.getMinecraft().player == null) {
            setStatus("当前无玩家实例，无法单步执行", 0xFFFF8888);
            return;
        }
        if (activeRunner == null || activeContext == null || activeContext.isCompleted() || activeContext.hasError()) {
            if (!validateGraphInternal(true)) {
                showPopup("单步前校验失败", statusMessage);
                return;
            }
            activeContext = new NodeExecutionContext();
            activeContext.setVariable("triggerSource", "manual:node_editor");
            activeContext.setVariable("triggerType", "manual");
            activeContext.addConsoleLine("[RUN] 进入单步模式: " + safe(currentGraph == null ? null : currentGraph.getName()));
            activeRunner = new NodeSequenceRunner(currentGraph, activeContext);
            runtimeErrorNodeId = null;
        }
        breakpointPauseNotified = false;
        activeContext.requestStep();
        activeContext.setRunMode(NodeExecutionContext.RunMode.STEP);
        activeContext.resume();
        runtimeHighlightedNodeId = activeRunner.getCurrentNodeId();
        setStatus("已请求单步执行", 0xFFFFFF88);
    }

    private void continueGraph() {
        if (activeContext == null || activeRunner == null) {
            runGraph();
            return;
        }
        breakpointPauseNotified = false;
        activeContext.setRunMode(NodeExecutionContext.RunMode.CONTINUE);
        activeContext.resume();
        setStatus("继续执行", 0xFFFFFF88);
    }

    private void editSelectedNodeParams() {
        if (selectedNode == null) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }

        List<String> graphNames = new ArrayList<>();
        for (NodeGraph graph : graphs) {
            if (graph != null && graph.getName() != null && !graph.getName().trim().isEmpty()) {
                graphNames.add(graph.getName().trim());
            }
        }

        JsonObject currentData = selectedNode.getData() == null ? new JsonObject()
                : new JsonParser().parse(selectedNode.getData().toString()).getAsJsonObject();

        this.mc.displayGuiScreen(
                new GuiNodeParameterEditor(this, selectedNode.getNormalizedType(), currentData, graphNames,
                        updatedData -> {
                            if (selectedNode != null) {
                                selectedNode.setData(updatedData == null ? new JsonObject() : updatedData);
                                saveDraftSilently();
                                refreshLiveValidation();
                                setStatus("节点参数表单已更新", 0xFF88FF88);
                            }
                        }));
    }

    private void editSelectedNodeData() {
        if (selectedNode == null) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }

        String initial = GSON.toJson(selectedNode.getData() == null ? new JsonObject() : selectedNode.getData());
        this.mc.displayGuiScreen(new GuiTextInput(this, "高级 JSON 编辑", initial, text -> {
            try {
                JsonObject data = new JsonParser().parse(text == null || text.trim().isEmpty() ? "{}" : text)
                        .getAsJsonObject();
                selectedNode.setData(data);
                saveDraftSilently();
                setStatus("节点数据已更新", 0xFF88FF88);
            } catch (Exception e) {
                setStatus("JSON 格式错误: " + e.getMessage(), 0xFFFF8888);
            }
            mc.displayGuiScreen(this);
        }));
    }

    private void openSingleFieldEditor(String title, String initial, java.util.function.Consumer<String> callback) {
        this.mc.displayGuiScreen(new GuiTextInput(this, title, initial == null ? "" : initial, value -> {
            callback.accept(value);
            mc.displayGuiScreen(this);
        }));
    }

    private String readDataValue(JsonObject data, String... keys) {
        if (data == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (key != null && data.has(key) && !data.get(key).isJsonNull()) {
                return data.get(key).isJsonPrimitive() ? data.get(key).getAsString() : data.get(key).toString();
            }
        }
        return "";
    }

    private void deleteSelectedNode() {
        if (selectedNode == null || currentGraph == null) {
            return;
        }

        String selectedId = selectedNode.getId();
        pushUndoState();
        currentGraph.getNodes().remove(selectedNode);
        List<NodeEdge> toRemove = new ArrayList<NodeEdge>();
        for (NodeEdge edge : currentGraph.getEdges()) {
            if (edge == null) {
                continue;
            }
            if (selectedId.equals(edge.getFromNodeId()) || selectedId.equals(edge.getToNodeId())) {
                toRemove.add(edge);
            }
        }
        currentGraph.getEdges().removeAll(toRemove);
        selectedNode = null;
        propertyPanelVisible = false;
        linkingFromNodeId = null;
        linkingFromPort = null;
        updateSelectedNodeFields();
        saveDraftSilently();
        setStatus("已删除节点: " + selectedId, 0xFFFFFF88);
    }

    private void restoreDraft() {
        if (currentGraph == null) {
            setStatus("当前没有可恢复的图", 0xFFFF8888);
            return;
        }
        NodeGraph draft = NodeEditorDraftManager.loadDraft(currentGraph.getName());
        if (draft == null) {
            setStatus("当前图没有草稿可恢复", 0xFFFFFF88);
            return;
        }
        currentGraph = draft;
        if (currentGraphIndex >= 0 && currentGraphIndex < graphs.size()) {
            graphs.set(currentGraphIndex, currentGraph);
        }
        selectedNode = null;
        propertyPanelVisible = false;
        selectedNodeIds.clear();
        linkingFromNodeId = null;
        linkingFromPort = null;
        updateSelectedNodeFields();
        setStatus("已恢复草稿: " + safe(currentGraph.getName()), 0xFF88FF88);
    }

    private void discardDraft() {
        if (currentGraph == null) {
            setStatus("当前没有可放弃的草稿", 0xFFFF8888);
            return;
        }
        String graphName = safe(currentGraph.getName());
        NodeEditorDraftManager.removeDraft(graphName);
        selectedNode = null;
        propertyPanelVisible = false;
        selectedNodeIds.clear();
        linkingFromNodeId = null;
        linkingFromPort = null;
        loadCurrentGraph();
        updateSelectedNodeFields();
        setStatus("已放弃草稿并恢复正式内容", 0xFF88FF88);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateLayoutMetrics();
        updateConsoleToggleButtonLabel();
        this.drawDefaultBackground();
        ModernUiRenderer.drawPanel(4, 4, this.width - 8, this.height - 8, 8,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(5, 5, Math.max(1, this.width - 10), TOP_BAR_HEIGHT, 7,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(this.fontRenderer, I18n.format("gui.node_editor.title"), this.width - 170, 12,
                ModernUiRenderer.TEXT, 140);
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, this.width - 28, 12,
                "节点编辑器用于编排复杂流程。拖拽节点和连线，右侧编辑属性，底部可查看运行日志。", mouseX, mouseY);
        ModernUiRenderer.drawDivider(10, TOP_BAR_HEIGHT + 1, Math.max(1, this.width - 20),
                ModernUiRenderer.BORDER_SUBTLE);

        drawToolbarPanel();
        drawCanvasPanel(mouseX, mouseY);
        drawPropertyPanel();
        drawConsolePanel();

        drawStatusBar();

        if (workflowContextMenuVisible) {
            drawWorkflowContextMenu(mouseX, mouseY);
        }

        if (quickAddVisible) {
            drawQuickAddOverlay(mouseX, mouseY);
        }

        if (popupVisible) {
            drawPopup(mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawToolbarPanel() {
        int statusTop = this.height - 22;
        ModernUiRenderer.drawSubtlePanel(8, TOP_BAR_HEIGHT + 4, TOOLBAR_WIDTH - 8,
                Math.max(40, statusTop - TOP_BAR_HEIGHT - 6), 6, ModernUiRenderer.SHELL_RAISED,
                ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, I18n.format("gui.node_editor.toolbar"), 16, TOP_BAR_HEIGHT + 10,
                ModernUiRenderer.TEXT, Math.max(70, TOOLBAR_WIDTH - 24));
        if (LEFT_PANEL_MODE_LIBRARY.equals(leftPanelMode)) {
            this.drawString(this.fontRenderer, "搜索", 12, LEFT_PANEL_SEARCH_LABEL_Y, 0xFFFFFFFF);
            ModernRuleEditorUi.drawTextField(this, nodeSearchField);
        }
        int treeBorderColor = 0x33223344;
        if (LEFT_PANEL_MODE_WORKFLOW.equals(leftPanelMode) && workflowListFocused) {
            treeBorderColor = 0xAA6FB8FF;
        }
        Gui.drawRect(toolbarTreeX - 1, toolbarTreeY - 2, toolbarTreeX + toolbarTreeWidth + 1,
                toolbarTreeY + toolbarTreeHeight + 2, treeBorderColor);
        if (LEFT_PANEL_MODE_WORKFLOW.equals(leftPanelMode) && workflowListFocused) {
            Gui.drawRect(toolbarTreeX - 2, toolbarTreeY - 3, toolbarTreeX + toolbarTreeWidth + 2, toolbarTreeY - 2,
                    0xCC8FD3FF);
            Gui.drawRect(toolbarTreeX - 2, toolbarTreeY + toolbarTreeHeight + 2, toolbarTreeX + toolbarTreeWidth + 2,
                    toolbarTreeY + toolbarTreeHeight + 3, 0xCC8FD3FF);
            Gui.drawRect(toolbarTreeX - 2, toolbarTreeY - 2, toolbarTreeX - 1, toolbarTreeY + toolbarTreeHeight + 2,
                    0xCC8FD3FF);
            Gui.drawRect(toolbarTreeX + toolbarTreeWidth + 1, toolbarTreeY - 2, toolbarTreeX + toolbarTreeWidth + 2,
                    toolbarTreeY + toolbarTreeHeight + 2, 0xCC8FD3FF);
        }
        if (LEFT_PANEL_MODE_LIBRARY.equals(leftPanelMode)) {
            drawLibraryTreeRows();
        } else {
            drawWorkflowRows();
        }
        if (linkingFromNodeId != null) {
            int hintY = Math.max(TOP_BAR_HEIGHT + 40, toolbarTreeY - 30);
            this.fontRenderer.drawSplitString("连线中:\n" + linkingFromNodeId + "."
                    + NodeNode.getPortDisplayName(linkingFromPort), 12,
                    hintY, TOOLBAR_WIDTH - 16, 0xFFFFFF88);
        }
        if (draggingLibraryItem && draggingLibraryLabel != null) {
            int hintY = Math.max(TOP_BAR_HEIGHT + 56, toolbarTreeY - 14);
            this.fontRenderer.drawSplitString("拖拽中: " + draggingLibraryLabel, 12, hintY, TOOLBAR_WIDTH - 16,
                    0xFF88DDFF);
        }
        int maxScroll = getToolbarMaxScroll();
        if (maxScroll > 0) {
            int trackX = toolbarTreeX + toolbarTreeWidth - 4;
            int trackY = toolbarTreeY;
            int trackH = toolbarTreeHeight;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (float) Math.max(trackH, toolbarContentHeight))));
            int thumbY = trackY + (int) ((trackH - thumbH) * (toolbarScrollOffset / (float) maxScroll));
            ModernRuleEditorUi.drawScrollbar(trackX, trackY, trackH, thumbY, thumbH);
        }
    }

    private void drawCanvasPanel(int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(canvasX, canvasY, canvasWidth, canvasHeight, 5,
                0xFF101820, ModernUiRenderer.BORDER_SUBTLE);
        drawCanvasGrid();
        hoveredEdgeId = findHoveredEdgeId(mouseX, mouseY);
        PortHit hoveredPort = findHoveredPort(mouseX, mouseY);
        updatePortHoverState(hoveredPort);
        updateLinkPreviewState(hoveredPort);

        for (NodeGroupBox group : safeGroups()) {
            drawGroupBox(group);
        }
        for (NodeCanvasNote note : safeNotes()) {
            drawCanvasNote(note);
        }

        for (NodeEdge edge : safeEdges()) {
            if (isEdgeVisible(edge)) {
                drawEdge(edge);
            }
        }

        if (linkingFromNodeId != null && linkingFromPort != null
                && isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
            NodeNode from = findNodeById(linkingFromNodeId);
            if (from != null) {
                int x1 = getPortCenterX(from, true, linkingFromPort);
                int y1 = getPortCenterY(from, true, linkingFromPort);
                int previewColor = getPortColor(linkingFromPort, true);
                if (linkPreviewTargetNodeId != null && !linkPreviewCompatible) {
                    previewColor = 0xFFFF6666;
                }
                drawBezierConnection(x1, y1, mouseX, mouseY, previewColor, true, false, false);
            }
        }

        for (NodeNode node : safeNodes()) {
            if (isNodeVisible(node)) {
                drawNodeCard(node, mouseX, mouseY);
            }
        }

        if (selectingBox) {
            int left = Math.min(selectionStartX, selectionEndX);
            int right = Math.max(selectionStartX, selectionEndX);
            int top = Math.min(selectionStartY, selectionEndY);
            int bottom = Math.max(selectionStartY, selectionEndY);
            Gui.drawRect(left, top, right, bottom, 0x2239C5FF);
            Gui.drawRect(left, top, right, top + 1, 0xFF66CCFF);
            Gui.drawRect(left, bottom - 1, right, bottom, 0xFF66CCFF);
            Gui.drawRect(left, top, left + 1, bottom, 0xFF66CCFF);
            Gui.drawRect(right - 1, top, right, bottom, 0xFF66CCFF);
        }

        if (alignGuideX != Integer.MIN_VALUE) {
            Gui.drawRect(alignGuideX, canvasY, alignGuideX + 1, canvasY + canvasHeight, 0x88FFD166);
        }
        if (alignGuideY != Integer.MIN_VALUE) {
            Gui.drawRect(canvasX, alignGuideY, canvasX + canvasWidth, alignGuideY + 1, 0x88FFD166);
        }

        this.drawString(this.fontRenderer, "缩放: " + Math.round(canvasZoom * 100) + "%", canvasX + 8, canvasY + 8,
                0xFFCCCCCC);
        drawMiniMap();

        int infoX = canvasX + canvasWidth - 22;
        int infoY = canvasY + 8;
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, infoX, infoY,
                hoveredPortTooltip.isEmpty()
                        ? "端口说明：输入端口接收上游节点结果，输出端口可连接到后续节点。"
                        : hoveredPortTooltip,
                mouseX, mouseY);
    }

    private void drawPropertyPanel() {
        if (!propertyPanelVisible || selectedNode == null) {
            hidePropertyButtons();
            return;
        }

        ModernUiRenderer.drawSubtlePanel(propertyX, propertyY, PROPERTY_WIDTH, propertyHeight, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(this.fontRenderer, I18n.format("gui.node_editor.properties"), propertyX + 10,
                propertyY + 8, ModernUiRenderer.TEXT, Math.max(80, PROPERTY_WIDTH - 20));
        int viewportTop = propertyY + 24;
        int viewportBottom = propertyY + propertyHeight - 8;
        int viewportHeight = Math.max(1, viewportBottom - viewportTop);
        int contentX = propertyX + 10;
        int contentWidth = PROPERTY_WIDTH - 20;
        int contentY = viewportTop + 6 - propertyScrollOffset;
        int cursor = 0;

        Gui.drawRect(propertyX + 6, viewportTop, propertyX + PROPERTY_WIDTH - 6, viewportBottom, 0x3318202E);

        int baseInfoHeight = 62;
        drawPropertySectionBox(contentX, contentY + cursor, contentWidth, baseInfoHeight, viewportTop, viewportBottom);
        drawPropertyLine(contentX + 4, contentY + cursor + 4,
                I18n.format("gui.node_editor.node_id") + ": " + safe(selectedNode.getId()),
                0xFFFFFFFF, viewportTop, viewportBottom);
        drawPropertyLine(contentX + 4, contentY + cursor + 18,
                "类型: " + getDisplayNodeName(selectedNode.getNormalizedType()),
                getNodeColor(selectedNode.getNormalizedType()), viewportTop, viewportBottom);
        drawPropertyLine(contentX + 4, contentY + cursor + 32,
                I18n.format("gui.node_editor.position") + ": " + selectedNode.getX() + ", " + selectedNode.getY(),
                0xFFCCCCCC, viewportTop, viewportBottom);
        drawPropertyLine(contentX + 4, contentY + cursor + 46,
                I18n.format("gui.node_editor.breakpoint") + ": "
                        + (activeContext != null && activeContext.hasBreakpoint(selectedNode.getId())
                                ? I18n.format("gui.node_editor.breakpoint.on")
                                : I18n.format("gui.node_editor.breakpoint.off")),
                0xFFFFFFFF, viewportTop, viewportBottom);
        cursor += baseInfoHeight + 8;

        cursor = layoutPropertyButton(BTN_EDIT_PARAMS, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_EDIT_DATA, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_DELETE_NODE, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_RESTORE_DRAFT, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_DISCARD_DRAFT, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_TOGGLE_BREAKPOINT, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_CLEAR_BREAKPOINTS, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor = layoutPropertyButton(BTN_TOGGLE_IGNORE, contentX, contentY, cursor, contentWidth, viewportTop,
                viewportBottom);
        cursor += 6;

        String helpText = "帮助说明：\n" + NodeNode.getHelpText(selectedNode.getNormalizedType());
        int helpHeight = 8 + this.fontRenderer.listFormattedStringToWidth(helpText, contentWidth - 8).size()
                * this.fontRenderer.FONT_HEIGHT;
        drawPropertySectionBox(contentX, contentY + cursor, contentWidth, helpHeight, viewportTop, viewportBottom);
        drawPropertySplit(contentX + 4, contentY + cursor + 4, helpText, contentWidth - 8, 0xFFDDDDDD, viewportTop,
                viewportBottom);
        cursor += helpHeight + 8;

        String jsonText = "参数预览（只读）:\n"
                + GSON.toJson(selectedNode.getData() == null ? new JsonObject() : selectedNode.getData());
        int jsonHeight = 8 + this.fontRenderer.listFormattedStringToWidth(jsonText, contentWidth - 8).size()
                * this.fontRenderer.FONT_HEIGHT;
        drawPropertySectionBox(contentX, contentY + cursor, contentWidth, jsonHeight, viewportTop, viewportBottom);
        drawPropertySplit(contentX + 4, contentY + cursor + 4, jsonText, contentWidth - 8, 0xFFBBBBBB, viewportTop,
                viewportBottom);
        cursor += jsonHeight + 8;

        StringBuilder validationText = new StringBuilder();
        if (liveValidationErrors.isEmpty()) {
            validationText.append(I18n.format("gui.node_editor.validation.ok"));
        } else {
            int count = Math.min(6, liveValidationErrors.size());
            for (int i = 0; i < count; i++) {
                validationText.append("- ").append(liveValidationErrors.get(i)).append("\n");
            }
        }
        String validationTitle = I18n.format("gui.node_editor.validation");
        int validationBodyHeight = this.fontRenderer
                .listFormattedStringToWidth(validationText.toString(), contentWidth - 8)
                .size() * this.fontRenderer.FONT_HEIGHT;
        int validationHeight = 12 + 6 + validationBodyHeight;
        drawPropertySectionBox(contentX, contentY + cursor, contentWidth, validationHeight, viewportTop,
                viewportBottom);
        drawPropertyLine(contentX + 4, contentY + cursor + 4, validationTitle, 0xFFFFFFFF, viewportTop, viewportBottom);
        drawPropertySplit(contentX + 4, contentY + cursor + 16, validationText.toString(), contentWidth - 8,
                liveValidationErrors.isEmpty() ? 0xFF88FF88 : 0xFFFF8888, viewportTop, viewportBottom);
        cursor += validationHeight + 8;

        if (activeContext != null) {
            String runtimeText = buildRuntimePanelText(activeContext);
            int runtimeBodyHeight = this.fontRenderer.listFormattedStringToWidth(runtimeText, contentWidth - 8)
                    .size() * this.fontRenderer.FONT_HEIGHT;
            int runtimeHeight = 12 + 6 + runtimeBodyHeight;
            drawPropertySectionBox(contentX, contentY + cursor, contentWidth, runtimeHeight, viewportTop,
                    viewportBottom);
            drawPropertyLine(contentX + 4, contentY + cursor + 4, I18n.format("gui.node_editor.runtime"), 0xFFFFFFFF,
                    viewportTop, viewportBottom);
            drawPropertySplit(contentX + 4, contentY + cursor + 16, runtimeText, contentWidth - 8, 0xFFDDDDDD,
                    viewportTop, viewportBottom);
            cursor += runtimeHeight + 8;

            String breakpointPanelText = buildBreakpointPanelText(activeContext);
            int breakpointBodyHeight = this.fontRenderer
                    .listFormattedStringToWidth(breakpointPanelText, contentWidth - 8)
                    .size() * this.fontRenderer.FONT_HEIGHT;
            int breakpointHeight = 12 + 6 + breakpointBodyHeight;
            drawPropertySectionBox(contentX, contentY + cursor, contentWidth, breakpointHeight, viewportTop,
                    viewportBottom);
            drawPropertyLine(contentX + 4, contentY + cursor + 4, "Breakpoint 列表", 0xFFFFFFFF, viewportTop,
                    viewportBottom);
            drawPropertySplit(contentX + 4, contentY + cursor + 16, breakpointPanelText, contentWidth - 8, 0xFFE9D6D6,
                    viewportTop, viewportBottom);
            cursor += breakpointHeight + 8;
        }

        propertyContentHeight = Math.max(viewportHeight, cursor + 8);
        int maxScroll = getPropertyMaxScroll();
        propertyScrollOffset = MathHelper.clamp(propertyScrollOffset, 0, maxScroll);

        if (maxScroll > 0) {
            int trackX = propertyX + PROPERTY_WIDTH - 8;
            int trackY = viewportTop;
            int trackH = viewportHeight;
            int thumbH = Math.max(16, (int) (trackH * (trackH / (float) Math.max(trackH, propertyContentHeight))));
            int thumbY = trackY + (int) ((trackH - thumbH) * (propertyScrollOffset / (float) maxScroll));
            ModernRuleEditorUi.drawScrollbar(trackX, trackY, trackH, thumbY, thumbH);
        }
    }

    private void drawStatusBar() {
        Gui.drawRect(8, this.height - 22, this.width - 8, this.height - 8, 0x77222222);
        this.drawString(this.fontRenderer, statusMessage == null ? "" : statusMessage, 12, this.height - 18,
                statusColor);

        StringBuilder clipboardText = new StringBuilder();
        if (!clipboardNodes.isEmpty()) {
            clipboardText.append("节点剪贴板: ").append(clipboardNodes.size()).append(" 项");
        }
        if (workflowClipboardGraph != null) {
            if (clipboardText.length() > 0) {
                clipboardText.append("  |  ");
            }
            clipboardText.append("工作流剪贴板: ").append(safe(workflowClipboardName).isEmpty()
                    ? "(未命名)"
                    : safe(workflowClipboardName));
        }
        if (clipboardText.length() > 0) {
            int textWidth = this.fontRenderer.getStringWidth(clipboardText.toString());
            this.drawString(this.fontRenderer, clipboardText.toString(), this.width - 14 - textWidth, this.height - 18,
                    0xFFBFD5E8);
        }
    }

    private void hidePropertyButtons() {
        setPropertyButtonVisible(BTN_EDIT_PARAMS, false);
        setPropertyButtonVisible(BTN_EDIT_DATA, false);
        setPropertyButtonVisible(BTN_DELETE_NODE, false);
        setPropertyButtonVisible(BTN_RESTORE_DRAFT, false);
        setPropertyButtonVisible(BTN_DISCARD_DRAFT, false);
        setPropertyButtonVisible(BTN_TOGGLE_BREAKPOINT, false);
        setPropertyButtonVisible(BTN_CLEAR_BREAKPOINTS, false);
        setPropertyButtonVisible(BTN_TOGGLE_IGNORE, false);
    }

    private void setPropertyButtonVisible(int buttonId, boolean visible) {
        GuiButton button = findButtonById(buttonId);
        if (button == null) {
            return;
        }
        button.visible = visible;
        button.enabled = visible;
    }

    private int layoutPropertyButton(int buttonId, int x, int baseY, int cursor, int width, int viewportTop,
            int viewportBottom) {
        GuiButton button = findButtonById(buttonId);
        int buttonHeight = 20;
        if (button == null) {
            return cursor + buttonHeight + 4;
        }
        int y = baseY + cursor;
        button.x = x;
        button.y = y;
        button.width = width;
        button.height = buttonHeight;
        boolean visible = isVisibleInViewport(y, buttonHeight, viewportTop, viewportBottom);
        button.visible = visible;
        button.enabled = visible;
        return cursor + buttonHeight + 4;
    }

    private void drawPropertySectionBox(int x, int y, int width, int height, int viewportTop, int viewportBottom) {
        if (!isVisibleInViewport(y, height, viewportTop, viewportBottom)) {
            return;
        }
        Gui.drawRect(x, y, x + width, y + height, 0x332A3444);
        Gui.drawRect(x, y, x + width, y + 1, 0x553A5A78);
        Gui.drawRect(x, y + height - 1, x + width, y + height, 0x553A5A78);
    }

    private void drawPropertyLine(int x, int y, String text, int color, int viewportTop, int viewportBottom) {
        if (!isVisibleInViewport(y, this.fontRenderer.FONT_HEIGHT + 2, viewportTop, viewportBottom)) {
            return;
        }
        this.drawString(this.fontRenderer, text, x, y, color);
    }

    private void drawPropertySplit(int x, int y, String text, int width, int color, int viewportTop,
            int viewportBottom) {
        int lineCount = this.fontRenderer.listFormattedStringToWidth(text, width).size();
        int height = Math.max(this.fontRenderer.FONT_HEIGHT, lineCount * this.fontRenderer.FONT_HEIGHT);
        if (!isVisibleInViewport(y, height + 2, viewportTop, viewportBottom)) {
            return;
        }
        this.fontRenderer.drawSplitString(text, x, y, width, color);
    }

    private boolean isVisibleInViewport(int y, int height, int viewportTop, int viewportBottom) {
        return y + height > viewportTop && y < viewportBottom;
    }

    private int getPropertyMaxScroll() {
        int viewportHeight = Math.max(1, propertyHeight - 32);
        return Math.max(0, propertyContentHeight - viewportHeight);
    }

    private boolean isInsidePropertyPanel(int mouseX, int mouseY) {
        return isInside(mouseX, mouseY, propertyX, propertyY, PROPERTY_WIDTH, propertyHeight);
    }

    private void drawEdge(NodeEdge edge) {
        NodeNode from = findNodeById(edge.getFromNodeId());
        NodeNode to = findNodeById(edge.getToNodeId());
        if (from == null || to == null) {
            return;
        }

        int x1 = getPortCenterX(from, true, edge.getFromPort());
        int y1 = getPortCenterY(from, true, edge.getFromPort());
        int x2 = getPortCenterX(to, false, edge.getToPort());
        int y2 = getPortCenterY(to, false, edge.getToPort());

        boolean hovered = safe(edge.getId()).equals(safe(hoveredEdgeId));
        boolean selected = safe(edge.getId()).equals(safe(selectedEdgeId));
        boolean runtime = safe(from.getId()).equals(safe(runtimeHighlightedNodeId))
                || safe(to.getId()).equals(safe(runtimeHighlightedNodeId));
        int color = getEdgeColor(edge);
        drawBezierConnection(x1, y1, x2, y2, color, runtime, hovered, selected);
    }

    private int getCanvasScaledTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.round(this.fontRenderer.getStringWidth(text) * canvasZoom));
    }

    private int getCanvasScaledWrapWidth(int pixelWidth) {
        float scale = Math.max(0.01f, canvasZoom);
        return Math.max(1, Math.round(pixelWidth / scale));
    }

    private void drawCanvasScaledString(String text, int screenX, int screenY, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) screenX, (float) screenY, 0.0F);
        GlStateManager.scale(canvasZoom, canvasZoom, 1.0F);
        this.fontRenderer.drawString(text, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private void drawCanvasScaledSplitString(String text, int screenX, int screenY, int pixelWidth, int color) {
        if (text == null || text.isEmpty() || pixelWidth <= 0) {
            return;
        }
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) screenX, (float) screenY, 0.0F);
        GlStateManager.scale(canvasZoom, canvasZoom, 1.0F);
        this.fontRenderer.drawSplitString(text, 0, 0, getCanvasScaledWrapWidth(pixelWidth), color);
        GlStateManager.popMatrix();
    }

    private int getNodePortRowCount(NodeNode node) {
        if (node == null) {
            return 1;
        }
        return Math.max(1, Math.max(getInputPorts(node.getNormalizedType()).size(),
                getOutputPorts(node.getNormalizedType()).size()));
    }

    private int getNodeContentTopOffset(NodeNode node) {
        int portRows = getNodePortRowCount(node);
        return 32 + Math.max(0, (portRows - 1) * 14);
    }

    private String limitWrappedText(String text, int wrapWidth, int maxLines) {
        if (text == null || text.isEmpty() || maxLines <= 0 || wrapWidth <= 0 || this.fontRenderer == null) {
            return text == null ? "" : text;
        }
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, wrapWidth);
        if (lines.size() <= maxLines) {
            return String.join("\n", lines);
        }

        List<String> limited = new ArrayList<String>(lines.subList(0, maxLines));
        int lastIndex = limited.size() - 1;
        String lastLine = limited.get(lastIndex);
        while (!lastLine.isEmpty() && this.fontRenderer.getStringWidth(lastLine + "…") > wrapWidth) {
            lastLine = lastLine.substring(0, lastLine.length() - 1);
        }
        limited.set(lastIndex, lastLine + "…");
        return String.join("\n", limited);
    }

    private void drawNodeCard(NodeNode node, int mouseX, int mouseY) {
        int x = toScreenX(node.getX());
        int y = toScreenY(node.getY());
        int width = scaled(NODE_WIDTH);
        int height = scaled(getNodeRenderHeight(node));
        boolean selected = (selectedNode != null && safe(selectedNode.getId()).equals(safe(node.getId())))
                || selectedNodeIds.contains(node.getId());
        boolean hovered = isInside(mouseX, mouseY, x, y, width, height);
        boolean runningHighlight = safe(node.getId()).equals(safe(runtimeHighlightedNodeId));
        boolean errorHighlight = safe(node.getId()).equals(safe(runtimeErrorNodeId));
        boolean hasBreakpoint = activeContext != null && activeContext.hasBreakpoint(node.getId());

        int nodeFill = selected ? 0xFF2C3B46 : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
        int nodeBorder = selected ? ModernUiRenderer.ACCENT_DIM
                : hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE;
        ModernUiRenderer.drawSubtlePanel(x, y, width, height, 5, nodeFill, nodeBorder);

        if (isNodeIgnored(node)) {
            Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0x66FFB3B3);
            Gui.drawRect(x, y, x + width, y + 1, 0x99FF8A8A);
            Gui.drawRect(x, y + height - 1, x + width, y + height, 0x99FF8A8A);
            Gui.drawRect(x, y, x + 1, y + height, 0x99FF8A8A);
            Gui.drawRect(x + width - 1, y, x + width, y + height, 0x99FF8A8A);
        }

        if (hovered && !selected) {
            Gui.drawRect(x - 1, y - 1, x + width + 1, y, 0xAA88CCFF);
            Gui.drawRect(x - 1, y + height, x + width + 1, y + height + 1, 0xAA88CCFF);
            Gui.drawRect(x - 1, y, x, y + height, 0xAA88CCFF);
            Gui.drawRect(x + width, y, x + width + 1, y + height, 0xAA88CCFF);
        }
        if (errorHighlight) {
            Gui.drawRect(x - 2, y - 2, x + width + 2, y, 0xFFFF5555);
            Gui.drawRect(x - 2, y + height, x + width + 2, y + height + 2, 0xFFFF5555);
            Gui.drawRect(x - 2, y, x, y + height, 0xFFFF5555);
            Gui.drawRect(x + width, y, x + width + 2, y + height, 0xFFFF5555);
        }
        if (runningHighlight) {
            int pulse = ((int) (System.currentTimeMillis() / 160L) % 2 == 0) ? 0xCCFFE45E : 0x88FFE45E;
            Gui.drawRect(x - 2, y - 2, x + width + 2, y, pulse);
            Gui.drawRect(x - 2, y + height, x + width + 2, y + height + 2, pulse);
            Gui.drawRect(x - 2, y, x, y + height, pulse);
            Gui.drawRect(x + width, y, x + width + 2, y + height, pulse);
        }

        int headerHeight = scaled(16);
        Gui.drawRect(x, y, x + width, y + headerHeight, getNodeColor(node.getNormalizedType()));
        drawNodeColorTag(node, x, y, width);

        String icon = getNodeIcon(node.getNormalizedType());
        drawCanvasScaledString(icon, x + scaled(4), y + scaled(4), 0xFFFFFFFF);
        drawCanvasScaledString(getDisplayNodeName(node.getNormalizedType()), x + scaled(18), y + scaled(3), 0xFFFFFFFF);

        int contentTopY = y + scaled(getNodeContentTopOffset(node));
        String subtitle = getNodeSubtitle(node);
        if (!subtitle.isEmpty() && !node.isMinimized()) {
            drawCanvasScaledString(subtitle, x + scaled(6), contentTopY, 0xFFBFD5E8);
        }

        int badgeY = y + scaled(3);
        int minimizeX = x + width - scaled(24);
        int collapseX = x + width - scaled(14);
        drawCanvasScaledString(node.isMinimized() ? "□" : "_", minimizeX, badgeY, 0xFFFFFFFF);
        drawCanvasScaledString(node.isCollapsed() ? "+" : "-", collapseX, badgeY, 0xFFFFFFFF);

        if (hasBreakpoint) {
            Gui.drawRect(x + width - 10, y + 2, x + width - 4, y + 8, 0xFFFF4444);
        }
        if (isNodeIgnored(node)) {
            drawCanvasScaledString("IGN", x + width - scaled(36), y + scaled(3), 0xFFFFE0E0);
        }

        if (!node.isMinimized()) {
            String summary = limitWrappedText(getNodeSummary(node), Math.max(20, NODE_WIDTH - 12), 2);
            if (!summary.isEmpty()) {
                int summaryY = contentTopY + (subtitle.isEmpty() ? 0 : scaled(12));
                drawCanvasScaledSplitString(summary, x + scaled(6), summaryY, Math.max(scaled(20), width - scaled(12)),
                        0xFFE9EEF4);
            }
        }

        if (!node.isCollapsed() && !node.isMinimized()) {
            String keyParams = getNodeKeyParamPreview(node);
            if (!keyParams.isEmpty()) {
                int previewY = y + height - scaled(30);
                Gui.drawRect(x + 4, previewY - 2, x + width - 4, previewY + scaled(11), 0x223F566F);
                drawCanvasScaledString(keyParams, x + scaled(6), previewY + scaled(1), 0xFFB8F0C8);
            }
        }

        if (!node.isMinimized()) {
            drawCanvasScaledString(safe(node.getId()), x + scaled(6), y + height - scaled(13), 0xFFCCCCCC);
        }

        List<String> inputs = getInputPorts(node.getNormalizedType());
        List<String> outputs = getOutputPorts(node.getNormalizedType());

        for (String input : inputs) {
            if (!node.isMinimized()) {
                drawPortLabel(node, input, false, x, y, width, height);
            }
            drawPort(node, input, false);
        }
        for (String output : outputs) {
            if (!node.isMinimized()) {
                drawPortLabel(node, output, true, x, y, width, height);
            }
            drawPort(node, output, true);
        }
    }

    private void drawPortLabel(NodeNode node, String port, boolean output, int nodeX, int nodeY, int nodeWidth,
            int nodeHeight) {
        if (node == null || node.isMinimized()) {
            return;
        }
        String label = safe(NodeNode.getPortDisplayName(port));
        if (label.isEmpty()) {
            return;
        }

        int portCenterY = getPortCenterY(node, output, port);
        int boxHeight = scaled(11);
        int maxBoxWidth = Math.max(scaled(18), Math.min(scaled(34), nodeWidth / 3));
        String fitted = fitCanvasLabel(label, maxBoxWidth - scaled(6));
        int textWidth = getCanvasScaledTextWidth(fitted);
        int boxWidth = Math.min(maxBoxWidth, Math.max(textWidth + scaled(6), scaled(16)));
        int boxY = MathHelper.clamp(portCenterY - boxHeight / 2, nodeY + scaled(18),
                nodeY + nodeHeight - boxHeight - scaled(4));

        int boxX;
        int bg;
        int fg;
        if (output) {
            boxX = nodeX + nodeWidth - boxWidth - scaled(10);
            bg = 0x66304D30;
            fg = 0xFFB8E6B8;
        } else {
            boxX = nodeX + scaled(10);
            bg = 0x664D3030;
            fg = 0xFFE6B3B3;
        }

        Gui.drawRect(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bg);
        Gui.drawRect(boxX, boxY, boxX + boxWidth, boxY + 1, 0x553A5A78);
        Gui.drawRect(boxX, boxY + boxHeight - 1, boxX + boxWidth, boxY + boxHeight, 0x553A5A78);
        drawCanvasScaledString(fitted, boxX + scaled(3), boxY + scaled(2), fg);
    }

    private String fitCanvasLabel(String text, int maxPixelWidth) {
        String value = safe(text);
        if (value.isEmpty() || maxPixelWidth <= 0) {
            return "";
        }
        if (getCanvasScaledTextWidth(value) <= maxPixelWidth) {
            return value;
        }
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String next = sb.toString() + value.charAt(i) + ellipsis;
            if (getCanvasScaledTextWidth(next) > maxPixelWidth) {
                break;
            }
            sb.append(value.charAt(i));
        }
        if (sb.length() <= 0) {
            return ellipsis;
        }
        return sb.toString() + ellipsis;
    }

    private void drawPort(NodeNode node, String port, boolean output) {
        int x = getPortCenterX(node, output, port) - scaled(PORT_SIZE) / 2;
        int y = getPortCenterY(node, output, port) - scaled(PORT_SIZE) / 2;
        int size = scaled(PORT_SIZE);
        int color = getPortColor(port, output);
        int borderColor = 0xAA000000;

        if (output && safe(node.getId()).equals(safe(linkingFromNodeId)) && safe(port).equals(safe(linkingFromPort))) {
            color = 0xFFFFFF00;
            borderColor = 0xFFFFFFAA;
        }

        boolean hovered = isHoveredPort(node, port, output);
        boolean suggested = !output && isSuggestedTargetPort(node, port);
        boolean previewTarget = !output && isLinkPreviewTarget(node, port);

        if (suggested) {
            Gui.drawRect(x - 2, y - 2, x + size + 2, y + size + 2, 0x3344FF88);
            borderColor = 0xFF66FF99;
        }
        if (previewTarget && !linkPreviewCompatible) {
            color = 0xFF8F4444;
            borderColor = 0xFFFF6666;
        } else if (previewTarget) {
            borderColor = 0xFFFFFF88;
        }
        if (hovered) {
            borderColor = 0xFFFFFFFF;
        }

        Gui.drawRect(x, y, x + size, y + size, color);
        Gui.drawRect(x - 1, y - 1, x + size + 1, y, borderColor);
        Gui.drawRect(x - 1, y + size, x + size + 1, y + size + 1, borderColor);
        Gui.drawRect(x - 1, y, x, y + size, borderColor);
        Gui.drawRect(x + size, y, x + size + 1, y + size, borderColor);
    }

    private int getPortCenterX(NodeNode node, boolean output, String port) {
        int nodeX = toScreenX(node.getX());
        int nodeWidth = scaled(NODE_WIDTH);
        return output ? nodeX + nodeWidth - 2 : nodeX + 2;
    }

    private int getPortCenterY(NodeNode node, boolean output, String port) {
        List<String> ports = output ? getOutputPorts(node.getNormalizedType())
                : getInputPorts(node.getNormalizedType());
        int index = Math.max(0, ports.indexOf(normalize(port)));
        int top = toScreenY(node.getY());
        int height = scaled(getNodeRenderHeight(node));
        if (node != null && node.isMinimized()) {
            return top + height / 2;
        }
        if (node != null && node.isCollapsed()) {
            return top + Math.max(scaled(24), height / 2 + index * scaled(10));
        }
        return top + scaled(22 + index * 14);
    }

    private List<String> getInputPorts(String type) {
        return NodeNode.getInputPorts(type);
    }

    private List<String> getOutputPorts(String type) {
        return NodeNode.getOutputPorts(type);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (popupVisible) {
            popupVisible = false;
            return;
        }

        if (quickAddVisible) {
            if (handleQuickAddMouseClick(mouseX, mouseY, mouseButton)) {
                return;
            }
            if (mouseButton == 0) {
                closeQuickAddOverlay();
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (LEFT_PANEL_MODE_LIBRARY.equals(leftPanelMode)) {
            nodeSearchField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (nodeSearchField != null) {
            nodeSearchField.setFocused(false);
        }
        if (quickAddVisible && quickAddSearchField != null) {
            quickAddSearchField.mouseClicked(mouseX, mouseY, mouseButton);
        }

        if (LEFT_PANEL_MODE_LIBRARY.equals(leftPanelMode)) {
            LibraryVisibleRow libraryRow = hitTestLibraryRow(mouseX, mouseY);
            if (libraryRow != null) {
                if (mouseButton == 0) {
                    if (libraryRow.node != null && !libraryRow.node.isGroup()) {
                        draggingLibraryItem = true;
                        draggingLibraryNode = libraryRow.node;
                        draggingLibraryNodeType = libraryRow.node.nodeType;
                        draggingLibraryLabel = libraryRow.node.label;
                        draggingLibraryMoved = false;
                        draggingLibraryStartX = mouseX;
                        draggingLibraryStartY = mouseY;
                    } else {
                        draggingLibraryItem = false;
                        draggingLibraryNode = null;
                    }
                    if (libraryRow.node != null && libraryRow.node.isGroup()) {
                        onLibraryRowLeftClick(libraryRow);
                    }
                    return;
                }
                if (mouseButton == 1) {
                    onLibraryRowRightClick(libraryRow);
                    return;
                }
            }
        } else {
            if (workflowContextMenuVisible) {
                if (mouseButton == 0) {
                    if (handleWorkflowContextMenuClick(mouseX, mouseY)) {
                        return;
                    }
                    workflowContextMenuVisible = false;
                } else if (mouseButton == 1) {
                    workflowContextMenuVisible = false;
                }
            }

            WorkflowVisibleRow workflowRow = hitTestWorkflowRow(mouseX, mouseY);
            if (workflowRow != null && mouseButton == 0) {
                workflowListFocused = true;
                draggingWorkflowRow = true;
                draggingWorkflowMoved = false;
                workflowDragStartY = mouseY;
                workflowDragSourceIndex = graphs.indexOf(workflowRow.graph);
                workflowDragTargetIndex = workflowDragSourceIndex;
                switchToWorkflow(workflowRow.graph);
                return;
            }
            if (mouseButton == 1
                    && isInside(mouseX, mouseY, toolbarTreeX, toolbarTreeY, toolbarTreeWidth, toolbarTreeHeight)) {
                workflowListFocused = true;
                if (workflowRow != null && workflowRow.graph != null && workflowRow.graph != currentGraph) {
                    switchToWorkflow(workflowRow.graph);
                }
                openWorkflowContextMenu(mouseX, mouseY, workflowRow);
                return;
            }
        }

        workflowListFocused = false;

        if (propertyPanelVisible && isInsidePropertyPanel(mouseX, mouseY)) {
            return;
        }

        if (workflowContextMenuVisible && mouseButton == 0) {
            workflowContextMenuVisible = false;
        }

        if (!isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
            return;
        }

        if (mouseButton == 1) {
            NodeEdge hoveredEdge = findEdgeById(hoveredEdgeId);
            if (hoveredEdge != null) {
                pushUndoState();
                currentGraph.getEdges().remove(hoveredEdge);
                if (safe(hoveredEdge.getId()).equals(safe(selectedEdgeId))) {
                    selectedEdgeId = null;
                }
                saveDraftSilently();
                setStatus("已删除连线: " + hoveredEdge.getFromNodeId() + "."
                        + NodeNode.getPortDisplayName(hoveredEdge.getFromPort())
                        + " -> " + hoveredEdge.getToNodeId() + "."
                        + NodeNode.getPortDisplayName(hoveredEdge.getToPort()), 0xFFFF8888);
                return;
            }
        }

        if (!isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
            return;
        }

        if (mouseButton == 0 && isInsideMiniMap(mouseX, mouseY)) {
            centerCanvasToMiniMapPoint(mouseX, mouseY);
            return;
        }

        if (mouseButton == 2
                || (mouseButton == 0 && org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_SPACE))) {
            panningCanvas = true;
            panStartMouseX = mouseX;
            panStartMouseY = mouseY;
            panStartOffsetX = canvasOffsetX;
            panStartOffsetY = canvasOffsetY;
            return;
        }

        for (NodeNode node : reverseNodes()) {
            int nodeScreenX = toScreenX(node.getX());
            int nodeScreenY = toScreenY(node.getY());
            int nodeScreenW = scaled(NODE_WIDTH);
            int nodeScreenH = scaled(getNodeRenderHeight(node));
            if (mouseButton == 1
                    && isInside(mouseX, mouseY, nodeScreenX, nodeScreenY, nodeScreenW, nodeScreenH)) {
                toggleBreakpoint(node);
                return;
            }

            if (mouseButton != 0) {
                continue;
            }

            String inputPort = hitTestPort(node, mouseX, mouseY, false);
            if (inputPort != null) {
                if (linkingFromNodeId != null) {
                    createConnection(linkingFromNodeId, linkingFromPort, node.getId(), inputPort);
                    linkingFromNodeId = null;
                    linkingFromPort = null;
                    return;
                }
                NodeEdge incoming = findIncomingEdge(node.getId(), inputPort);
                if (incoming != null) {
                    linkingFromNodeId = incoming.getFromNodeId();
                    linkingFromPort = incoming.getFromPort();
                    selectedEdgeId = incoming.getId();
                    pushUndoState();
                    currentGraph.getEdges().remove(incoming);
                    saveDraftSilently();
                    setStatus("已拆下原连线，选择新的输入端口以完成重连", 0xFFFFFF88);
                    return;
                }
            }

            String outputPort = hitTestPort(node, mouseX, mouseY, true);
            if (outputPort != null) {
                if (!isOutputPortMultiConnect(node, outputPort) && countOutgoingEdges(node.getId(), outputPort) > 0) {
                    setStatus("该输出端口已存在连线；当前版本请先删除旧连线或从目标输入端口拆线后重连", 0xFFFF8888);
                    return;
                }
                selectedNode = node;
                propertyPanelVisible = true;
                propertyScrollOffset = 0;
                updateSelectedNodeFields();
                linkingFromNodeId = node.getId();
                linkingFromPort = outputPort;
                selectedEdgeId = null;
                setStatus("已选择输出端口: " + linkingFromNodeId + "."
                        + NodeNode.getPortDisplayName(linkingFromPort), 0xFFFFFF88);
                return;
            }
        }

        if (mouseButton != 0) {
            return;
        }

        for (NodeNode node : reverseNodes()) {
            int nodeScreenX = toScreenX(node.getX());
            int nodeScreenY = toScreenY(node.getY());
            int nodeScreenW = scaled(NODE_WIDTH);
            int nodeScreenH = scaled(getNodeRenderHeight(node));
            if (isInside(mouseX, mouseY, nodeScreenX, nodeScreenY, nodeScreenW, nodeScreenH)) {
                if (isInside(mouseX, mouseY, nodeScreenX + nodeScreenW - 28, nodeScreenY + 1, 12, 12)) {
                    node.setMinimized(!node.isMinimized());
                    saveDraftSilently();
                    setStatus(node.isMinimized() ? "已最小化节点: " + node.getId() : "已恢复节点: " + node.getId(), 0xFF88FF88);
                    return;
                }
                if (isInside(mouseX, mouseY, nodeScreenX + nodeScreenW - 16, nodeScreenY + 1, 12, 12)) {
                    node.setCollapsed(!node.isCollapsed());
                    saveDraftSilently();
                    setStatus(node.isCollapsed() ? "已折叠节点: " + node.getId() : "已展开节点: " + node.getId(), 0xFF88FF88);
                    return;
                }

                selectedNode = node;
                boolean shiftDown = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                        || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
                if (shiftDown) {
                    if (selectedNodeIds.contains(node.getId())) {
                        selectedNodeIds.remove(node.getId());
                    } else {
                        selectedNodeIds.add(node.getId());
                    }
                    if (selectedNodeIds.isEmpty()) {
                        selectedNodeIds.add(node.getId());
                    }
                } else if (!selectedNodeIds.contains(node.getId())) {
                    selectedNodeIds.clear();
                    selectedNodeIds.add(node.getId());
                }

                long nowMs = System.currentTimeMillis();
                boolean doubleClick = safe(node.getId()).equals(safe(lastClickedNodeId))
                        && nowMs - lastClickedNodeTimeMs <= 300L;
                lastClickedNodeId = node.getId();
                lastClickedNodeTimeMs = nowMs;

                propertyPanelVisible = doubleClick;
                if (doubleClick) {
                    propertyScrollOffset = 0;
                    setStatus("已打开节点属性: " + node.getId(), 0xFF88FF88);
                } else {
                    setStatus("已选中节点: " + node.getId(), 0xFFCCCCCC);
                }

                updateSelectedNodeFields();
                draggingNode = true;
                dragOffsetX = mouseX - nodeScreenX;
                dragOffsetY = mouseY - nodeScreenY;
                return;
            }
        }

        NodeEdge hoveredEdge = findEdgeById(hoveredEdgeId);
        if (hoveredEdge != null) {
            selectedEdgeId = hoveredEdge.getId();
            selectedNode = null;
            propertyPanelVisible = false;
            selectedNodeIds.clear();
            updateSelectedNodeFields();
            setStatus("已选中连线，右键可删除；点击已连接输入端口可重连", 0xFFFFFF88);
            return;
        }

        selectedEdgeId = null;
        selectedNode = null;
        propertyPanelVisible = false;
        selectedNodeIds.clear();

        long nowMs = System.currentTimeMillis();
        boolean blankDoubleClick = nowMs - lastBlankCanvasClickTimeMs <= 300L;
        lastBlankCanvasClickTimeMs = nowMs;
        if (blankDoubleClick) {
            openQuickAddOverlay(mouseX, mouseY);
            updateSelectedNodeFields();
            return;
        }

        selectingBox = true;
        selectionStartX = mouseX;
        selectionStartY = mouseY;
        selectionEndX = mouseX;
        selectionEndY = mouseY;
        updateSelectedNodeFields();
    }

    private void createConnection(String fromNodeId, String fromPort, String toNodeId, String toPort) {
        if (currentGraph == null) {
            return;
        }

        String error = getConnectionCompatibilityError(fromNodeId, fromPort, toNodeId, toPort);
        if (error != null) {
            setStatus("无法连接: " + error, 0xFFFF8888);
            return;
        }

        List<NodeEdge> edges = currentGraph.getEdges();
        NodeNode toNode = findNodeById(toNodeId);
        int replacedCount = 0;
        pushUndoState();

        if (toNode != null && getMaxIncomingConnections(toNode, toPort) == 1) {
            List<NodeEdge> replaced = new ArrayList<NodeEdge>();
            for (NodeEdge existing : edges) {
                if (existing == null) {
                    continue;
                }
                if (safe(toNodeId).equals(safe(existing.getToNodeId()))
                        && normalize(toPort).equals(normalize(existing.getToPort()))) {
                    replaced.add(existing);
                }
            }
            replacedCount = replaced.size();
            if (!replaced.isEmpty()) {
                edges.removeAll(replaced);
            }
        }

        NodeEdge edge = new NodeEdge();
        edge.setId("edge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        edge.setFromNodeId(fromNodeId);
        edge.setFromPort(normalize(fromPort));
        edge.setToNodeId(toNodeId);
        edge.setToPort(normalize(toPort));
        edges.add(edge);
        selectedEdgeId = edge.getId();
        saveDraftSilently();

        String message = "已连接: " + fromNodeId + "." + NodeNode.getPortDisplayName(fromPort)
                + " -> " + toNodeId + "." + NodeNode.getPortDisplayName(toPort);
        if (replacedCount > 0) {
            message += "（已替换旧连线 " + replacedCount + " 条）";
        }
        setStatus(message, 0xFF88FF88);
    }

    private String hitTestPort(NodeNode node, int mouseX, int mouseY, boolean output) {
        List<String> ports = output ? getOutputPorts(node.getNormalizedType())
                : getInputPorts(node.getNormalizedType());
        int size = scaled(PORT_SIZE);
        for (String port : ports) {
            int px = getPortCenterX(node, output, port) - size / 2;
            int py = getPortCenterY(node, output, port) - size / 2;
            if (isInside(mouseX, mouseY, px, py, size, size)) {
                return port;
            }
        }
        return null;
    }

    private PortHit findHoveredPort(int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
            return null;
        }
        for (NodeNode node : reverseNodes()) {
            if (node == null || !isNodeVisible(node)) {
                continue;
            }
            String outputPort = hitTestPort(node, mouseX, mouseY, true);
            if (outputPort != null) {
                return new PortHit(node, outputPort, true);
            }
            String inputPort = hitTestPort(node, mouseX, mouseY, false);
            if (inputPort != null) {
                return new PortHit(node, inputPort, false);
            }
        }
        return null;
    }

    private void updatePortHoverState(PortHit hit) {
        if (hit == null || hit.node == null) {
            hoveredPortNodeId = null;
            hoveredPortName = null;
            hoveredPortOutput = false;
            hoveredPortTooltip = "";
            return;
        }
        hoveredPortNodeId = hit.node.getId();
        hoveredPortName = normalize(hit.port);
        hoveredPortOutput = hit.output;
        hoveredPortTooltip = buildPortTooltip(hit.node, hit.port, hit.output);
    }

    private void updateLinkPreviewState(PortHit hit) {
        linkPreviewTargetNodeId = null;
        linkPreviewTargetPort = null;
        linkPreviewCompatible = false;
        previewLinkMouseX = -1;
        previewLinkMouseY = -1;

        if (linkingFromNodeId == null || linkingFromPort == null || hit == null || hit.output || hit.node == null) {
            return;
        }

        linkPreviewTargetNodeId = hit.node.getId();
        linkPreviewTargetPort = normalize(hit.port);
        linkPreviewCompatible = getConnectionCompatibilityError(linkingFromNodeId, linkingFromPort,
                linkPreviewTargetNodeId, linkPreviewTargetPort) == null;
    }

    private String buildPortTooltip(NodeNode node, String port, boolean output) {
        if (node == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(output ? "输出端口" : "输入端口").append(": ")
                .append(NodeNode.getPortDisplayName(port)).append("\n");
        sb.append("节点: ").append(safe(node.getId())).append(" / ").append(getDisplayNodeName(node.getNormalizedType()))
                .append("\n");
        sb.append("语义: ").append(getPortSemanticLabel(port)).append("\n");

        int count = output ? countOutgoingEdges(node.getId(), port) : countIncomingEdges(node.getId(), port);
        sb.append(output ? "已连接输出数: " : "已连接输入数: ").append(count);

        if (!output) {
            int limit = getMaxIncomingConnections(node, port);
            if (limit == 0) {
                sb.append("（无限制）");
            } else {
                sb.append(" / 上限 ").append(limit);
                if (limit == 1 && count > 0) {
                    sb.append("（新连线会替换旧连线）");
                } else if (limit > 1) {
                    sb.append("（允许多路汇入）");
                }
            }
        }

        if (linkingFromNodeId != null && linkingFromPort != null && !output) {
            String error = getConnectionCompatibilityError(linkingFromNodeId, linkingFromPort, node.getId(), port);
            sb.append("\n连接校验: ").append(error == null ? "可连接" : error);
        }

        return sb.toString();
    }

    private String getPortSemanticLabel(String port) {
        String normalizedPort = normalize(port);
        if (NodeNode.PORT_NEXT.equals(normalizedPort)) {
            return "主流程 / 成功继续";
        }
        if (NodeNode.PORT_TRUE.equals(normalizedPort)) {
            return "条件成立 / 命中";
        }
        if (NodeNode.PORT_FALSE.equals(normalizedPort)) {
            return "条件失败 / 备用分支";
        }
        if (NodeNode.PORT_TIMEOUT.equals(normalizedPort)) {
            return "超时分支";
        }
        if (NodeNode.PORT_IN.equals(normalizedPort)) {
            return "流程输入";
        }
        return normalizedPort;
    }

    private boolean isHoveredPort(NodeNode node, String port, boolean output) {
        return node != null
                && safe(node.getId()).equals(safe(hoveredPortNodeId))
                && normalize(port).equals(normalize(hoveredPortName))
                && hoveredPortOutput == output;
    }

    private boolean isSuggestedTargetPort(NodeNode node, String port) {
        return node != null
                && linkingFromNodeId != null
                && linkingFromPort != null
                && getConnectionCompatibilityError(linkingFromNodeId, linkingFromPort, node.getId(), port) == null;
    }

    private boolean isLinkPreviewTarget(NodeNode node, String port) {
        return node != null
                && safe(node.getId()).equals(safe(linkPreviewTargetNodeId))
                && normalize(port).equals(normalize(linkPreviewTargetPort));
    }

    private int getMaxIncomingConnections(NodeNode node, String port) {
        if (node == null) {
            return 1;
        }
        JsonObject data = node.getData() == null ? new JsonObject() : node.getData();
        if (data.has("maxIncomingConnections") && data.get("maxIncomingConnections").isJsonPrimitive()
                && data.get("maxIncomingConnections").getAsJsonPrimitive().isNumber()) {
            return Math.max(0, data.get("maxIncomingConnections").getAsInt());
        }
        String normalizedPort = normalize(port);
        if (NodeNode.PORT_IN.equals(normalizedPort)) {
            String nodeType = node.getNormalizedType();
            if (NodeNode.TYPE_END.equals(nodeType) || NodeNode.TYPE_JOIN.equals(nodeType)) {
                return 0;
            }
        }
        return 1;
    }

    private boolean isInputPortMultiConnect(NodeNode node, String port) {
        return getMaxIncomingConnections(node, port) == 0 || getMaxIncomingConnections(node, port) > 1;
    }

    private boolean isOutputPortMultiConnect(NodeNode node, String port) {
        return false;
    }

    private int countIncomingEdges(String toNodeId, String toPort) {
        int count = 0;
        for (NodeEdge edge : safeEdges()) {
            if (edge == null) {
                continue;
            }
            if (safe(toNodeId).equals(safe(edge.getToNodeId()))
                    && normalize(toPort).equals(normalize(edge.getToPort()))) {
                count++;
            }
        }
        return count;
    }

    private int countOutgoingEdges(String fromNodeId, String fromPort) {
        int count = 0;
        for (NodeEdge edge : safeEdges()) {
            if (edge == null) {
                continue;
            }
            if (safe(fromNodeId).equals(safe(edge.getFromNodeId()))
                    && normalize(fromPort).equals(normalize(edge.getFromPort()))) {
                count++;
            }
        }
        return count;
    }

    private boolean hasExactEdge(String fromNodeId, String fromPort, String toNodeId, String toPort) {
        for (NodeEdge edge : safeEdges()) {
            if (edge == null) {
                continue;
            }
            if (safe(fromNodeId).equals(safe(edge.getFromNodeId()))
                    && normalize(fromPort).equals(normalize(edge.getFromPort()))
                    && safe(toNodeId).equals(safe(edge.getToNodeId()))
                    && normalize(toPort).equals(normalize(edge.getToPort()))) {
                return true;
            }
        }
        return false;
    }

    private String getConnectionCompatibilityError(String fromNodeId, String fromPort, String toNodeId, String toPort) {
        NodeNode fromNode = findNodeById(fromNodeId);
        NodeNode toNode = findNodeById(toNodeId);
        if (fromNode == null || toNode == null) {
            return "源节点或目标节点不存在";
        }
        if (safe(fromNodeId).equals(safe(toNodeId))) {
            return "不能连接到自身";
        }
        if (!NodeGraphValidator.isValidOutputPort(fromNode.getNormalizedType(), normalize(fromPort))) {
            return "源输出端口无效";
        }
        if (!NodeGraphValidator.isValidInputPort(toNode.getNormalizedType(), normalize(toPort))) {
            return "目标输入端口无效";
        }
        if (hasExactEdge(fromNodeId, fromPort, toNodeId, toPort)) {
            return "该连线已存在";
        }
        if (!isOutputPortMultiConnect(fromNode, fromPort) && countOutgoingEdges(fromNodeId, fromPort) > 0) {
            return "该输出端口已存在连线";
        }

        int incomingCount = countIncomingEdges(toNodeId, toPort);
        int incomingLimit = getMaxIncomingConnections(toNode, toPort);
        if (incomingLimit == 1 && incomingCount > 0) {
            return null;
        }
        if (incomingLimit > 1 && incomingCount >= incomingLimit) {
            return "该输入端口已达到连接上限(" + incomingLimit + ")";
        }
        return null;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (draggingLibraryItem) {
            if (Math.abs(mouseX - draggingLibraryStartX) > 4 || Math.abs(mouseY - draggingLibraryStartY) > 4) {
                draggingLibraryMoved = true;
            }
            return;
        }
        if (draggingWorkflowRow) {
            if (Math.abs(mouseY - workflowDragStartY) > 4) {
                draggingWorkflowMoved = true;
            }
            WorkflowVisibleRow hoverRow = hitTestWorkflowRow(mouseX, mouseY);
            if (hoverRow != null && hoverRow.graph != null) {
                workflowDragTargetIndex = graphs.indexOf(hoverRow.graph);
            }
            return;
        }
        if (panningCanvas) {
            canvasOffsetX = panStartOffsetX + (mouseX - panStartMouseX);
            canvasOffsetY = panStartOffsetY + (mouseY - panStartMouseY);
        } else if (draggingNode && selectedNode != null) {
            int targetX = snapToGrid(screenToCanvasX(mouseX - dragOffsetX));
            int targetY = snapToGrid(screenToCanvasY(mouseY - dragOffsetY));
            alignGuideX = Integer.MIN_VALUE;
            alignGuideY = Integer.MIN_VALUE;

            for (NodeNode other : safeNodes()) {
                if (other == null || selectedNodeIds.contains(other.getId())) {
                    continue;
                }
                if (Math.abs(other.getX() - targetX) <= 6) {
                    targetX = other.getX();
                    alignGuideX = toScreenX(other.getX());
                }
                if (Math.abs(other.getY() - targetY) <= 6) {
                    targetY = other.getY();
                    alignGuideY = toScreenY(other.getY());
                }
            }

            int deltaX = targetX - selectedNode.getX();
            int deltaY = targetY - selectedNode.getY();

            for (NodeNode node : safeNodes()) {
                if (selectedNodeIds.contains(node.getId())) {
                    node.setX(node.getX() + deltaX);
                    node.setY(node.getY() + deltaY);
                }
            }
            updateSelectedNodeFields();
        } else if (selectingBox) {
            selectionEndX = mouseX;
            selectionEndY = mouseY;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (draggingLibraryItem && state == 0 && draggingLibraryNode != null) {
            if (isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
                int cx = screenToCanvasX(mouseX);
                int cy = screenToCanvasY(mouseY);
                addFromLibraryNode(draggingLibraryNode, cx, cy);
                setStatus("已拖拽创建节点: " + safe(draggingLibraryLabel), 0xFF88FF88);
            } else if (!draggingLibraryMoved) {
                addFromLibraryNode(draggingLibraryNode, null, null);
            }
        }
        draggingLibraryItem = false;
        draggingLibraryNode = null;
        draggingLibraryNodeType = null;
        draggingLibraryLabel = null;
        draggingLibraryMoved = false;

        if (draggingWorkflowRow) {
            if (draggingWorkflowMoved
                    && workflowDragSourceIndex >= 0
                    && workflowDragTargetIndex >= 0
                    && workflowDragSourceIndex != workflowDragTargetIndex
                    && workflowDragSourceIndex < graphs.size()
                    && workflowDragTargetIndex < graphs.size()) {
                NodeGraph moved = graphs.remove(workflowDragSourceIndex);
                graphs.add(workflowDragTargetIndex, moved);
                currentGraphIndex = graphs.indexOf(currentGraph);
                rebuildVisibleWorkflowRows();
                ensureWorkflowRowVisible(currentGraphIndex);
                setStatus("已调整工作流顺序: " + safe(moved == null ? null : moved.getName()), 0xFF88FF88);
            }
            draggingWorkflowRow = false;
            draggingWorkflowMoved = false;
            workflowDragSourceIndex = -1;
            workflowDragTargetIndex = -1;
        }

        if (selectingBox) {
            selectNodesInBox();
        }
        draggingNode = false;
        selectingBox = false;
        panningCanvas = false;
        alignGuideX = Integer.MIN_VALUE;
        alignGuideY = Integer.MIN_VALUE;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = org.lwjgl.input.Mouse.getDWheel();
        if (dWheel == 0) {
            return;
        }

        int mouseX = org.lwjgl.input.Mouse.getX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - org.lwjgl.input.Mouse.getY() * this.height / this.mc.displayHeight - 1;

        if (consoleVisible && isInside(mouseX, mouseY, consoleX, consoleY, consoleWidth, consoleHeight)) {
            int step = 3;
            if (dWheel > 0) {
                consoleScrollOffset = Math.min(200, consoleScrollOffset + step);
            } else {
                consoleScrollOffset = Math.max(0, consoleScrollOffset - step);
            }
            return;
        }

        if (propertyPanelVisible
                && isInside(mouseX, mouseY, propertyX, propertyY + 24, PROPERTY_WIDTH,
                        Math.max(1, propertyHeight - 32))) {
            int step = 20;
            if (dWheel > 0) {
                propertyScrollOffset -= step;
            } else {
                propertyScrollOffset += step;
            }
            propertyScrollOffset = MathHelper.clamp(propertyScrollOffset, 0, getPropertyMaxScroll());
            return;
        }

        if (isInside(mouseX, mouseY, toolbarTreeX, toolbarTreeY, toolbarTreeWidth, toolbarTreeHeight)) {
            int step = 20;
            if (dWheel > 0) {
                toolbarScrollOffset -= step;
            } else {
                toolbarScrollOffset += step;
            }
            int maxScroll = getToolbarMaxScroll();
            toolbarScrollOffset = MathHelper.clamp(toolbarScrollOffset, 0, maxScroll);
            updateToolbarButtonVisibility();
            return;
        }

        if (!isInside(mouseX, mouseY, canvasX, canvasY, canvasWidth, canvasHeight)) {
            return;
        }

        float oldZoom = canvasZoom;
        if (dWheel > 0) {
            canvasZoom = Math.min(2.5f, canvasZoom + 0.1f);
        } else {
            canvasZoom = Math.max(0.5f, canvasZoom - 0.1f);
        }

        if (oldZoom != canvasZoom) {
            float canvasLocalX = mouseX - canvasX - canvasOffsetX;
            float canvasLocalY = mouseY - canvasY - canvasOffsetY;
            float logicalX = canvasLocalX / oldZoom;
            float logicalY = canvasLocalY / oldZoom;

            canvasOffsetX = Math.round(mouseX - canvasX - logicalX * canvasZoom);
            canvasOffsetY = Math.round(mouseY - canvasY - logicalY * canvasZoom);
        }
    }

    private int scaled(int value) {
        return Math.max(1, Math.round(value * canvasZoom));
    }

    private int toScreenX(int canvasNodeX) {
        return canvasX + canvasOffsetX + Math.round(canvasNodeX * canvasZoom);
    }

    private int toScreenY(int canvasNodeY) {
        return canvasY + canvasOffsetY + Math.round(canvasNodeY * canvasZoom);
    }

    private int screenToCanvasX(int screenX) {
        return Math.round((screenX - canvasX - canvasOffsetX) / canvasZoom);
    }

    private int screenToCanvasY(int screenY) {
        return Math.round((screenY - canvasY - canvasOffsetY) / canvasZoom);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (popupVisible) {
            popupVisible = false;
            return;
        }
        if (quickAddVisible && quickAddSearchField != null) {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
                closeQuickAddOverlay();
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_UP) {
                moveQuickAddSelection(-1);
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_DOWN) {
                moveQuickAddSelection(1);
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
                createQuickAddHoveredNode();
                return;
            }
            if (quickAddSearchField.isFocused()) {
                quickAddSearchField.textboxKeyTyped(typedChar, keyCode);
                quickAddScrollOffset = 0;
                quickAddHoverIndex = getQuickAddEntries().isEmpty() ? -1 : 0;
                return;
            }
        }

        if (LEFT_PANEL_MODE_LIBRARY.equals(leftPanelMode) && nodeSearchField != null && nodeSearchField.isFocused()) {
            nodeSearchField.textboxKeyTyped(typedChar, keyCode);
            updateToolbarButtonVisibility();
            return;
        }

        if (workflowListFocused && LEFT_PANEL_MODE_WORKFLOW.equals(leftPanelMode)) {
            if (keyCode == org.lwjgl.input.Keyboard.KEY_UP) {
                focusAdjacentWorkflow(-1);
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_DOWN) {
                focusAdjacentWorkflow(1);
                return;
            }
            if (keyCode == org.lwjgl.input.Keyboard.KEY_RETURN || keyCode == org.lwjgl.input.Keyboard.KEY_NUMPADENTER) {
                activateCurrentWorkflowFromList();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.NEW_WORKFLOW, keyCode)) {
                createNewWorkflow();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.RENAME_WORKFLOW, keyCode)) {
                renameCurrentWorkflow();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.CUT, keyCode)) {
                cutCurrentWorkflow();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.COPY, keyCode)) {
                duplicateCurrentWorkflow();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.PASTE, keyCode)) {
                pasteWorkflowFromClipboard();
                return;
            }
            if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.DELETE, keyCode)) {
                deleteCurrentWorkflow();
                return;
            }
        }

        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.DELETE, keyCode)) {
            deleteSelectionOrEdge();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.SELECT_ALL, keyCode)) {
            selectAllNodes();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.CUT, keyCode)) {
            cutSelectionToClipboard();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.COPY, keyCode)) {
            copySelectionToClipboard();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.PASTE, keyCode)) {
            pasteClipboard();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.DUPLICATE, keyCode)) {
            duplicateSelection();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.UNDO, keyCode)) {
            undoAction();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.REDO, keyCode)) {
            redoAction();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.FOCUS_SELECTED, keyCode)) {
            focusSelectedNodes();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.FOCUS_START, keyCode)) {
            focusStartNode();
            return;
        }
        if (NodeEditorHotkeyManager.matches(NodeEditorHotkeyManager.Action.SAVE, keyCode)) {
            saveGraph();
            return;
        }

        if (keyCode == org.lwjgl.input.Keyboard.KEY_N) {
            createCanvasNote();
            return;
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_G) {
            createGroupFromSelection();
            return;
        }
        if (keyCode == org.lwjgl.input.Keyboard.KEY_T) {
            cycleSelectedNodeColorTag();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        validationRefreshTicker++;
        if (validationRefreshTicker >= 10) {
            validationRefreshTicker = 0;
            refreshLiveValidation();
        }
        tickActiveRunner();
    }

    private void applyFieldChanges() {
        saveDraftSilently();
    }

    private void initializeLibraryTreeIfNeeded() {
        if (!libraryRoots.isEmpty()) {
            return;
        }
        libraryRoots.clear();

        libraryRoots.add(LibraryNode.group("group_interact", "交互",
                LibraryNode.group("group_interact_user", "用户交互",
                        LibraryNode.item("item_action_command", "执行命令", NodeNode.TYPE_ACTION,
                                presetAction("command", "command", "/help")),
                        LibraryNode.item("item_action_message", "系统提示", NodeNode.TYPE_ACTION,
                                presetAction("system_message", "message", "提示消息"))),
                LibraryNode.group("group_interact_gui", "GUI 交互",
                        LibraryNode.item("item_action_click", "屏幕点击", NodeNode.TYPE_ACTION,
                                presetAction("click", "x", 960, "y", 540)),
                        LibraryNode.item("item_action_window_click", "容器内槽位点击", NodeNode.TYPE_ACTION,
                                presetAction("window_click", "windowId", "-1", "slot", "0", "contains", "")),
                        LibraryNode.item("item_action_takeallitems_safe", "安全取出全部物品", NodeNode.TYPE_ACTION,
                                presetAction("take_all_items_safe")),
                        LibraryNode.item("item_action_move_inventory_items_to_chest_slots", "容器物品批量移动",
                                NodeNode.TYPE_ACTION,
                                presetAction("move_inventory_items_to_chest_slots", "delayTicks", 2, "chestRows", 6,
                                        "chestCols", 9, "inventoryRows", 4, "inventoryCols", 9, "chestSlotsText", "",
                                        "inventorySlotsText", "", "moveDirection", "INVENTORY_TO_CHEST", "button", 0,
                                        "clickType", "PICKUP",
                                        "itemName", "",
                                        "requiredNbtTagsText", "")),
                        LibraryNode.item("item_action_warehouse_auto_deposit", "仓库管理-自动存入", NodeNode.TYPE_ACTION,
                                presetAction("warehouse_auto_deposit")),
                        LibraryNode.item("item_action_block_gui", "屏蔽GUI", NodeNode.TYPE_ACTION,
                                presetAction("blocknextgui", "count", 1, "blockCurrentGui", false)),
                        LibraryNode.item("item_action_hide_current_gui", "隐藏当前GUI", NodeNode.TYPE_ACTION,
                                presetAction("hidecurrentgui")),
                        LibraryNode.item("item_action_show_hidden_gui", "显示已隐藏GUI", NodeNode.TYPE_ACTION,
                                presetAction("showhiddengui")),
                        LibraryNode.item("item_action_close_container_window", "关闭当前容器窗口", NodeNode.TYPE_ACTION,
                                presetAction("close_container_window")),
                        LibraryNode.item("item_action_hud_text_check", "GUI/HUD 文本识别", NodeNode.TYPE_ACTION,
                                presetAction("hud_text_check", "contains", "", "matchBlock", false, "separator",
                                        " | ")),
                        LibraryNode.item("item_action_autopickup", "自动拾取", NodeNode.TYPE_ACTION,
                                presetAction("autopickup", "enabled", true))),
                LibraryNode.group("group_interact_chat", "聊天交互",
                        LibraryNode.item("item_trigger_chat", "聊天触发", NodeNode.TYPE_TRIGGER,
                                presetTrigger("onchat")))));

        libraryRoots.add(LibraryNode.group("group_move", "移动",
                LibraryNode.item("item_move_forward", "前进 N 格", NodeNode.TYPE_ACTION,
                        presetAction("command", "command", "!goto forward")),
                LibraryNode.item("item_move_back", "后退", NodeNode.TYPE_ACTION,
                        presetAction("command", "command", "!back")),
                LibraryNode.item("item_move_jump", "跳跃", NodeNode.TYPE_ACTION, presetAction("jump", "count", 1)),
                LibraryNode.item("item_move_turn", "转向", NodeNode.TYPE_ACTION,
                        presetAction("setview", "yaw", 0, "pitch", 0)),
                LibraryNode.item("item_move_goto", "到达某坐标", NodeNode.TYPE_ACTION,
                        presetAction("command", "command", "!goto 0 64 0")),
                LibraryNode.item("item_move_rightclick_block", "右键方块", NodeNode.TYPE_ACTION,
                        presetAction("rightclickblock", "pos", "0,64,0")),
                LibraryNode.item("item_move_rightclick_entity", "右键最近实体", NodeNode.TYPE_ACTION,
                        presetAction("rightclickentity", "pos", "0,64,0", "range", 3))));

        libraryRoots.add(LibraryNode.group("group_condition", "条件",
                LibraryNode.item("item_if", "If", NodeNode.TYPE_IF),
                LibraryNode.item("item_compare", "比较", NodeNode.TYPE_IF, presetIf("x", "==", 1)),
                LibraryNode.item("item_range", "范围判断", NodeNode.TYPE_IF, presetIf("distance", "<=", 5)),
                LibraryNode.item("item_text", "文本匹配", NodeNode.TYPE_IF, presetIf("msg", "==", "hello"))));

        libraryRoots.add(LibraryNode.group("group_var", "变量",
                LibraryNode.item("item_setvar", "设置变量", NodeNode.TYPE_SET_VAR),
                LibraryNode.item("item_readvar", "读取变量", NodeNode.TYPE_IF, presetIf("someVar", "!=", "")),
                LibraryNode.item("item_cache", "缓存", NodeNode.TYPE_RESULT_CACHE)));

        libraryRoots.add(LibraryNode.group("group_flow", "流程",
                LibraryNode.item("item_start", "开始", NodeNode.TYPE_START),
                LibraryNode.item("item_end", "结束", NodeNode.TYPE_END),
                LibraryNode.item("item_subgraph", "子图", NodeNode.TYPE_SUBGRAPH),
                LibraryNode.item("item_runseq", "运行序列", NodeNode.TYPE_RUN_SEQUENCE),
                LibraryNode.item("item_parallel", "并行", NodeNode.TYPE_PARALLEL),
                LibraryNode.item("item_join", "汇聚", NodeNode.TYPE_JOIN),
                LibraryNode.item("item_delay", "延迟", NodeNode.TYPE_DELAY_TASK)));

        libraryRoots.add(LibraryNode.group("group_trigger", "触发器",
                LibraryNode.item("item_trigger_chat2", "聊天触发", NodeNode.TYPE_TRIGGER, presetTrigger("onchat")),
                LibraryNode.item("item_trigger_packet", "包触发", NodeNode.TYPE_TRIGGER, presetTrigger("onpacket")),
                LibraryNode.item("item_trigger_gui", "GUI 打开触发", NodeNode.TYPE_TRIGGER, presetTrigger("onguiopen")),
                LibraryNode.item("item_trigger_hp", "低血量触发", NodeNode.TYPE_TRIGGER, presetTrigger("onhplow")),
                LibraryNode.item("item_trigger_timer", "定时触发", NodeNode.TYPE_TRIGGER, presetTrigger("ontimer")),
                LibraryNode.item("item_trigger_inventory_full", "背包已满触发", NodeNode.TYPE_TRIGGER,
                        presetTrigger("oninventoryfull"))));

        libraryRoots.add(LibraryNode.group("group_resource", "资源控制",
                LibraryNode.item("item_lock", "锁", NodeNode.TYPE_RESOURCE_LOCK),
                LibraryNode.item("item_retry", "重试", NodeNode.TYPE_WAIT_UNTIL),
                LibraryNode.item("item_timeout", "超时", NodeNode.TYPE_WAIT_UNTIL, presetTimeout(200)),
                LibraryNode.item("item_silent_use", "静默使用物品", NodeNode.TYPE_ACTION,
                        presetAction("silentuse", "item", "minecraft:stone", "tempslot", 0)),
                LibraryNode.item("item_auto_eat", "自动吃食物", NodeNode.TYPE_ACTION,
                        presetAction("autoeat", "enabled", true, "foodLevelThreshold", 12, "autoMoveFoodEnabled", true,
                                "eatWithLookDown", false, "targetHotbarSlot", 9)),
                LibraryNode.item("item_run_last_sequence", "运行上次序列", NodeNode.TYPE_ACTION,
                        presetAction("runlastsequence"))));

        libraryRoots.add(LibraryNode.group("group_debug", "调试",
                LibraryNode.item("item_debug_log", "日志", NodeNode.TYPE_ACTION,
                        presetAction("system_message", "message", "debug")),
                LibraryNode.item("item_debug_break", "断点", NodeNode.TYPE_WAIT_UNTIL),
                LibraryNode.item("item_debug_step", "单步", NodeNode.TYPE_DELAY_TASK, presetDelay(1))));

        libraryRoots.add(LibraryNode.group("group_template", "模板",
                LibraryNode.item("item_tpl_branch", "常用模板：分支流程", "template:branch"),
                LibraryNode.item("item_tpl_action", "常用模板：动作链", "template:action_chain"),
                LibraryNode.item("item_tpl_wait", "常用模板：等待后执行", "template:wait_action"),
                LibraryNode.item("item_tpl_trigger_action", "触发模板：触发后执行", "template:trigger_action"),
                LibraryNode.item("item_tpl_trigger_branch", "触发模板：触发后分支", "template:trigger_branch"),
                LibraryNode.item("item_tpl_gui_click", "GUI 模板：元素点击", "template:gui_click_element"),
                LibraryNode.item("item_tpl_gui_flow", "GUI 模板：常见界面流程", "template:gui_common_flow")));
    }

    private JsonObject presetAction(String actionType, Object... kv) {
        JsonObject data = createDefaultData(NodeNode.TYPE_ACTION);
        data.addProperty("actionType", actionType);
        JsonObject params = data.has("params") && data.get("params").isJsonObject() ? data.getAsJsonObject("params")
                : new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = String.valueOf(kv[i]);
            Object value = kv[i + 1];
            if (value instanceof Number) {
                params.addProperty(key, (Number) value);
            } else if (value instanceof Boolean) {
                params.addProperty(key, (Boolean) value);
            } else {
                params.addProperty(key, String.valueOf(value));
            }
        }
        data.add("params", params);
        return data;
    }

    private JsonObject presetIf(String leftVar, String operator, Object right) {
        JsonObject data = createDefaultData(NodeNode.TYPE_IF);
        data.addProperty("leftVar", leftVar);
        data.addProperty("operator", operator);
        if (right instanceof Number) {
            data.addProperty("right", (Number) right);
            data.addProperty("rightType", "number");
        } else if (right instanceof Boolean) {
            data.addProperty("right", (Boolean) right);
            data.addProperty("rightType", "boolean");
        } else {
            data.addProperty("right", String.valueOf(right));
            data.addProperty("rightType", "string");
        }
        return data;
    }

    private JsonObject presetTrigger(String triggerType) {
        JsonObject data = createDefaultData(NodeNode.TYPE_TRIGGER);
        data.addProperty("triggerType", triggerType);
        return data;
    }

    private JsonObject presetDelay(int ticks) {
        JsonObject data = createDefaultData(NodeNode.TYPE_DELAY_TASK);
        data.addProperty("delayTicks", ticks);
        return data;
    }

    private JsonObject presetTimeout(int timeoutTicks) {
        JsonObject data = createDefaultData(NodeNode.TYPE_WAIT_UNTIL);
        data.addProperty("timeoutTicks", timeoutTicks);
        return data;
    }

    private void updateToolbarButtonVisibility() {
        int statusTop = this.height - 22;
        toolbarTreeX = 10;
        toolbarTreeWidth = TOOLBAR_WIDTH - 20;
        toolbarButtonWidth = toolbarTreeWidth - 16;

        for (GuiButton button : this.buttonList) {
            if (button == null) {
                continue;
            }
            if (isToolbarNodeButton(button.id)
                    || button.id == BTN_GROUP_FLOW
                    || button.id == BTN_GROUP_TRIGGER
                    || button.id == BTN_GROUP_VAR
                    || button.id == BTN_GROUP_ADVANCED
                    || button.id == BTN_WORKFLOW_NEW
                    || button.id == BTN_WORKFLOW_RENAME
                    || button.id == BTN_WORKFLOW_COPY
                    || button.id == BTN_WORKFLOW_PASTE
                    || button.id == BTN_PANEL_LIBRARY
                    || button.id == BTN_PANEL_WORKFLOW) {
                button.visible = false;
                button.enabled = false;
            }
        }

        layoutFixedToolbarButton(BTN_PANEL_LIBRARY, toolbarTreeX, LEFT_PANEL_MODE_BUTTON_Y, 44, 18, true);
        layoutFixedToolbarButton(BTN_PANEL_WORKFLOW, toolbarTreeX + 48, LEFT_PANEL_MODE_BUTTON_Y, 44, 18, true);

        if (nodeSearchField != null) {
            nodeSearchField.x = 12;
            nodeSearchField.y = LEFT_PANEL_SEARCH_FIELD_Y;
            nodeSearchField.width = TOOLBAR_WIDTH - 16;
            nodeSearchField.height = 16;
        }

        boolean workflowMode = LEFT_PANEL_MODE_WORKFLOW.equals(leftPanelMode);
        layoutFixedToolbarButton(BTN_WORKFLOW_NEW, toolbarTreeX, LEFT_PANEL_WORKFLOW_ACTION_Y, 40, 18, false);
        layoutFixedToolbarButton(BTN_WORKFLOW_RENAME, toolbarTreeX + 44, LEFT_PANEL_WORKFLOW_ACTION_Y, 46, 18, false);
        layoutFixedToolbarButton(BTN_WORKFLOW_COPY, toolbarTreeX, LEFT_PANEL_WORKFLOW_ACTION_Y + 22, 40, 18, false);
        layoutFixedToolbarButton(BTN_WORKFLOW_PASTE, toolbarTreeX + 44, LEFT_PANEL_WORKFLOW_ACTION_Y + 22, 46, 18,
                false);

        toolbarTreeY = workflowMode ? LEFT_PANEL_TREE_Y_WORKFLOW : LEFT_PANEL_TREE_Y_LIBRARY;
        toolbarTreeHeight = Math.max(64, statusTop - toolbarTreeY - 6);
        if (!workflowMode) {
            rebuildVisibleLibraryRows();
        } else {
            rebuildVisibleWorkflowRows();
        }
    }

    private void layoutFixedToolbarButton(int buttonId, int x, int y, int width, int height, boolean visible) {
        GuiButton button = findButtonById(buttonId);
        if (button == null) {
            return;
        }
        button.x = x;
        button.y = y;
        button.width = width;
        button.height = height;
        button.visible = visible;
        button.enabled = visible;
    }

    private void rebuildVisibleLibraryRows() {
        visibleLibraryRows.clear();
        String keyword = nodeSearchField == null ? "" : normalize(nodeSearchField.getText());
        int y = 0;

        LibraryNode favorites = buildFavoritesGroup();
        if (favorites != null) {
            y = appendVisibleRows(favorites, 0, y, keyword);
        }
        LibraryNode recent = buildRecentGroup();
        if (recent != null) {
            y = appendVisibleRows(recent, 0, y, keyword);
        }

        List<LibraryNode> sortedRoots = new ArrayList<>(libraryRoots);
        sortedRoots.sort(Comparator.comparing(a -> safe(a.label)));
        for (LibraryNode root : sortedRoots) {
            y = appendVisibleRows(root, 0, y, keyword);
        }
        toolbarContentHeight = Math.max(0, y);
        toolbarScrollOffset = MathHelper.clamp(toolbarScrollOffset, 0, getToolbarMaxScroll());
    }

    private int appendVisibleRows(LibraryNode node, int depth, int y, String keyword) {
        if (node == null) {
            return y;
        }
        if (!node.isGroup()) {
            if (!keyword.isEmpty() && !matchesLibrarySearch(node, keyword)) {
                return y;
            }
            LibraryVisibleRow row = new LibraryVisibleRow();
            row.node = node;
            row.depth = depth;
            row.contentY = y;
            visibleLibraryRows.add(row);
            return y + LIBRARY_ROW_HEIGHT;
        }

        boolean showGroup = keyword.isEmpty() || groupHasMatchingChild(node, keyword)
                || matchesLibrarySearch(node, keyword);
        if (!showGroup) {
            return y;
        }
        LibraryVisibleRow groupRow = new LibraryVisibleRow();
        groupRow.node = node;
        groupRow.depth = depth;
        groupRow.contentY = y;
        visibleLibraryRows.add(groupRow);
        y += LIBRARY_ROW_HEIGHT;

        if (!isLibraryGroupExpanded(node.id) && keyword.isEmpty()) {
            return y;
        }
        for (LibraryNode child : node.children) {
            y = appendVisibleRows(child, depth + 1, y, keyword);
        }
        return y;
    }

    private boolean matchesLibrarySearch(LibraryNode node, String keyword) {
        if (node == null) {
            return false;
        }
        String n = normalize(node.label) + " " + normalize(node.id) + " " + normalize(getNodeAliases(node.nodeType));
        return n.contains(normalize(keyword));
    }

    private boolean groupHasMatchingChild(LibraryNode group, String keyword) {
        if (group == null || group.children == null) {
            return false;
        }
        for (LibraryNode child : group.children) {
            if (child.isGroup()) {
                if (groupHasMatchingChild(child, keyword) || matchesLibrarySearch(child, keyword)) {
                    return true;
                }
            } else if (matchesLibrarySearch(child, keyword)) {
                return true;
            }
        }
        return false;
    }

    private void drawLibraryTreeRows() {
        int mouseX = org.lwjgl.input.Mouse.getX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - org.lwjgl.input.Mouse.getY() * this.height / this.mc.displayHeight - 1;
        for (LibraryVisibleRow row : visibleLibraryRows) {
            int y = toolbarTreeY + row.contentY - toolbarScrollOffset;
            if (y + LIBRARY_ROW_HEIGHT < toolbarTreeY || y > toolbarTreeY + toolbarTreeHeight) {
                continue;
            }
            int x = toolbarTreeX + 2 + row.depth * LIBRARY_INDENT;
            int width = Math.max(10, LIBRARY_NAME_WIDTH - row.depth * LIBRARY_INDENT);
            boolean hovered = isInside(mouseX, mouseY, x, y, width, LIBRARY_ROW_HEIGHT);
            int bg = hovered ? 0x335A86AA : 0x22223344;
            Gui.drawRect(x, y, x + width, y + LIBRARY_ROW_HEIGHT - 1, bg);

            String prefix = row.node.isGroup() ? (isLibraryGroupExpanded(row.node.id) ? "▼ " : "▶ ") : "• ";
            String label = prefix + safe(row.node.label);
            if (!row.node.isGroup() && favoriteNodeTypes.contains(row.node.nodeType)) {
                label = "★ " + label;
            }
            this.drawString(this.fontRenderer, label, x + 2, y + 5, row.node.isGroup() ? 0xFFE6EEF8 : 0xFFCCDDFF);
        }
    }

    private LibraryVisibleRow hitTestLibraryRow(int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, toolbarTreeX, toolbarTreeY, toolbarTreeWidth, toolbarTreeHeight)) {
            return null;
        }
        int contentY = mouseY - toolbarTreeY + toolbarScrollOffset;
        for (LibraryVisibleRow row : visibleLibraryRows) {
            if (contentY >= row.contentY && contentY < row.contentY + LIBRARY_ROW_HEIGHT) {
                return row;
            }
        }
        return null;
    }

    private void rebuildVisibleWorkflowRows() {
        visibleWorkflowRows.clear();
        int y = 0;
        for (NodeGraph graph : graphs) {
            if (graph == null) {
                continue;
            }
            WorkflowVisibleRow row = new WorkflowVisibleRow();
            row.graph = graph;
            row.contentY = y;
            visibleWorkflowRows.add(row);
            y += WORKFLOW_ROW_HEIGHT;
        }
        toolbarContentHeight = Math.max(0, y);
        toolbarScrollOffset = MathHelper.clamp(toolbarScrollOffset, 0, getToolbarMaxScroll());
    }

    private void drawWorkflowRows() {
        int mouseX = org.lwjgl.input.Mouse.getX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - org.lwjgl.input.Mouse.getY() * this.height / this.mc.displayHeight - 1;
        for (WorkflowVisibleRow row : visibleWorkflowRows) {
            int y = toolbarTreeY + row.contentY - toolbarScrollOffset;
            if (y + WORKFLOW_ROW_HEIGHT < toolbarTreeY || y > toolbarTreeY + toolbarTreeHeight) {
                continue;
            }
            boolean hovered = isInside(mouseX, mouseY, toolbarTreeX + 2, y, toolbarTreeWidth - 6, WORKFLOW_ROW_HEIGHT);
            boolean selected = currentGraph != null && row.graph == currentGraph;
            boolean contextTarget = workflowContextMenuVisible && workflowContextTargetRow == row;
            boolean dragSource = draggingWorkflowRow && graphs.indexOf(row.graph) == workflowDragSourceIndex;
            boolean dragTarget = draggingWorkflowRow && graphs.indexOf(row.graph) == workflowDragTargetIndex;
            int bg = selected ? 0x554E8BC9 : (hovered ? 0x335A86AA : 0x22223344);
            if (contextTarget) {
                bg = 0x556A5ACD;
            }
            if (dragSource) {
                bg = 0x668C7BFF;
            } else if (dragTarget) {
                bg = 0x6657D38C;
            }
            Gui.drawRect(toolbarTreeX + 2, y, toolbarTreeX + toolbarTreeWidth - 2, y + WORKFLOW_ROW_HEIGHT - 1, bg);
            Gui.drawRect(toolbarTreeX + 2, y, toolbarTreeX + toolbarTreeWidth - 2, y + WORKFLOW_ROW_HEIGHT - 1, bg);

            String name = safe(row.graph.getName());
            int nodeCount = row.graph.getNodes() == null ? 0 : row.graph.getNodes().size();
            String text = name + " §7(" + nodeCount + "节点)";
            this.drawString(this.fontRenderer, text, toolbarTreeX + 6, y + 6, selected ? 0xFFFFFFFF : 0xFFCCDDFF);
        }
    }

    private WorkflowVisibleRow hitTestWorkflowRow(int mouseX, int mouseY) {
        if (!isInside(mouseX, mouseY, toolbarTreeX, toolbarTreeY, toolbarTreeWidth, toolbarTreeHeight)) {
            return null;
        }
        int contentY = mouseY - toolbarTreeY + toolbarScrollOffset;
        for (WorkflowVisibleRow row : visibleWorkflowRows) {
            if (contentY >= row.contentY && contentY < row.contentY + WORKFLOW_ROW_HEIGHT) {
                return row;
            }
        }
        return null;
    }

    private void focusAdjacentWorkflow(int direction) {
        if (graphs.isEmpty()) {
            setStatus("当前没有可切换的工作流", 0xFFFFFF88);
            return;
        }
        int currentIndex = currentGraphIndex;
        if (currentIndex < 0 || currentIndex >= graphs.size()) {
            currentIndex = Math.max(0, graphs.indexOf(currentGraph));
        }
        if (currentIndex < 0) {
            currentIndex = 0;
        }
        int nextIndex = MathHelper.clamp(currentIndex + direction, 0, graphs.size() - 1);
        workflowListFocused = true;
        switchToWorkflow(graphs.get(nextIndex));
        ensureWorkflowRowVisible(nextIndex);
    }

    private void ensureWorkflowRowVisible(int index) {
        if (index < 0 || index >= visibleWorkflowRows.size()) {
            return;
        }
        WorkflowVisibleRow row = visibleWorkflowRows.get(index);
        int rowTop = row.contentY;
        int rowBottom = row.contentY + WORKFLOW_ROW_HEIGHT;
        if (rowTop < toolbarScrollOffset) {
            toolbarScrollOffset = rowTop;
        } else if (rowBottom > toolbarScrollOffset + toolbarTreeHeight) {
            toolbarScrollOffset = Math.max(0, rowBottom - toolbarTreeHeight);
        }
        toolbarScrollOffset = MathHelper.clamp(toolbarScrollOffset, 0, getToolbarMaxScroll());
    }

    private void activateCurrentWorkflowFromList() {
        workflowListFocused = true;
        if (currentGraph == null) {
            setStatus("当前没有工作流", 0xFFFFFF88);
            return;
        }
        ensureWorkflowRowVisible(currentGraphIndex);
        setStatus("当前工作流: " + safe(currentGraph.getName()), 0xFF88FF88);
    }

    private void onLibraryRowLeftClick(LibraryVisibleRow row) {
        if (row == null || row.node == null) {
            return;
        }
        if (row.node.isGroup()) {
            toggleLibraryGroup(row.node.id);
            return;
        }
        addFromLibraryNode(row.node, null, null);
    }

    private void onLibraryRowRightClick(LibraryVisibleRow row) {
        if (row == null || row.node == null || row.node.isGroup()) {
            return;
        }
        if (row.node.nodeType == null || row.node.nodeType.startsWith("template:")) {
            return;
        }
        toggleFavoriteNodeType(row.node.nodeType);
        saveLibraryPanelState();
    }

    private void toggleLibraryGroup(String groupId) {
        String id = safe(groupId);
        if (id.isEmpty()) {
            return;
        }
        if (expandedLibraryGroupIds.contains(id)) {
            expandedLibraryGroupIds.remove(id);
        } else {
            expandedLibraryGroupIds.add(id);
        }
        saveLibraryPanelState();
        updateToolbarButtonVisibility();
    }

    private boolean isLibraryGroupExpanded(String groupId) {
        return groupId != null && expandedLibraryGroupIds.contains(groupId);
    }

    private void addFromLibraryNode(LibraryNode node, Integer canvasPosX, Integer canvasPosY) {
        if (node == null || node.nodeType == null || node.nodeType.trim().isEmpty()) {
            return;
        }
        if (node.nodeType.startsWith("template:")) {
            createFromTemplate(node.nodeType, canvasPosX, canvasPosY);
            return;
        }
        JsonObject data = null;
        if (node.presetData != null) {
            data = new JsonParser().parse(node.presetData.toString()).getAsJsonObject();
        }
        addNodeWithData(node.nodeType, data, canvasPosX, canvasPosY);
    }

    private void createFromTemplate(String templateType, Integer canvasPosX, Integer canvasPosY) {
        int cx = canvasPosX == null ? screenToCanvasX(canvasX + canvasWidth / 2) : canvasPosX.intValue();
        int cy = canvasPosY == null ? screenToCanvasY(canvasY + canvasHeight / 2) : canvasPosY.intValue();
        if ("template:branch".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_START, null, cx - 180, cy - 80);
            NodeNode start = selectedNode;
            addNodeWithData(NodeNode.TYPE_IF, presetIf("x", ">", 0), cx - 40, cy - 80);
            NodeNode ifNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say true"), cx + 130, cy - 140);
            NodeNode trueNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say false"), cx + 130, cy - 20);
            NodeNode falseNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 300, cy - 80);
            NodeNode end = selectedNode;
            createConnection(start.getId(), "next", ifNode.getId(), "in");
            createConnection(ifNode.getId(), "true", trueNode.getId(), "in");
            createConnection(ifNode.getId(), "false", falseNode.getId(), "in");
            createConnection(trueNode.getId(), "next", end.getId(), "in");
            createConnection(falseNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：分支流程", 0xFF88FF88);
            return;
        }
        if ("template:wait_action".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_START, null, cx - 140, cy);
            NodeNode start = selectedNode;
            addNodeWithData(NodeNode.TYPE_WAIT_UNTIL, presetTimeout(100), cx + 20, cy);
            NodeNode waitNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say ready"), cx + 180, cy);
            NodeNode actionNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 340, cy);
            NodeNode end = selectedNode;
            createConnection(start.getId(), "next", waitNode.getId(), "in");
            createConnection(waitNode.getId(), "next", actionNode.getId(), "in");
            createConnection(actionNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：等待后执行", 0xFF88FF88);
            return;
        }
        if ("template:trigger_action".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_TRIGGER, presetTrigger("onchat"), cx - 160, cy);
            NodeNode trigger = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say triggered"), cx + 10, cy);
            NodeNode actionNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 190, cy);
            NodeNode end = selectedNode;
            createConnection(trigger.getId(), "next", actionNode.getId(), "in");
            createConnection(actionNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：触发后执行", 0xFF88FF88);
            return;
        }
        if ("template:trigger_branch".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_TRIGGER, presetTrigger("onchat"), cx - 200, cy - 70);
            NodeNode trigger = selectedNode;
            addNodeWithData(NodeNode.TYPE_IF, presetIf("message", "==", "hello"), cx - 30, cy - 70);
            NodeNode ifNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say hit"), cx + 160, cy - 130);
            NodeNode trueNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say miss"), cx + 160, cy - 10);
            NodeNode falseNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 340, cy - 70);
            NodeNode end = selectedNode;
            createConnection(trigger.getId(), "next", ifNode.getId(), "in");
            createConnection(ifNode.getId(), "true", trueNode.getId(), "in");
            createConnection(ifNode.getId(), "false", falseNode.getId(), "in");
            createConnection(trueNode.getId(), "next", end.getId(), "in");
            createConnection(falseNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：触发后分支", 0xFF88FF88);
            return;
        }
        if ("template:gui_click_element".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_START, null, cx - 220, cy);
            NodeNode start = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION,
                    presetAction("hud_text_check", "contains", "", "matchBlock", false, "separator", " | "),
                    cx - 40, cy);
            NodeNode scanNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION,
                    presetAction("window_click", "windowId", "-1", "slot", "0", "contains", "确认",
                            "button", 0, "clickType", "PICKUP"),
                    cx + 150, cy);
            NodeNode clickNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 330, cy);
            NodeNode end = selectedNode;
            createConnection(start.getId(), "next", scanNode.getId(), "in");
            createConnection(scanNode.getId(), "next", clickNode.getId(), "in");
            createConnection(clickNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：GUI 元素点击", 0xFF88FF88);
            return;
        }
        if ("template:gui_common_flow".equals(templateType)) {
            addNodeWithData(NodeNode.TYPE_TRIGGER, presetTrigger("onguiopen"), cx - 260, cy);
            NodeNode trigger = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION,
                    presetAction("hud_text_check", "contains", "", "matchBlock", true, "separator", " | "),
                    cx - 80, cy);
            NodeNode scanNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION,
                    presetAction("window_click", "windowId", "-1", "slot", "0", "contains", "",
                            "button", 0, "clickType", "QUICK_MOVE"),
                    cx + 110, cy);
            NodeNode clickNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_ACTION, presetAction("take_all_items_safe"), cx + 300, cy);
            NodeNode takeNode = selectedNode;
            addNodeWithData(NodeNode.TYPE_END, null, cx + 470, cy);
            NodeNode end = selectedNode;
            createConnection(trigger.getId(), "next", scanNode.getId(), "in");
            createConnection(scanNode.getId(), "next", clickNode.getId(), "in");
            createConnection(clickNode.getId(), "next", takeNode.getId(), "in");
            createConnection(takeNode.getId(), "next", end.getId(), "in");
            setStatus("已从模板创建：常见界面流程", 0xFF88FF88);
            return;
        }
        addNodeWithData(NodeNode.TYPE_START, null, cx - 180, cy);
        NodeNode start = selectedNode;
        addNodeWithData(NodeNode.TYPE_ACTION, presetAction("command", "command", "/say action"), cx, cy);
        NodeNode action = selectedNode;
        addNodeWithData(NodeNode.TYPE_END, null, cx + 180, cy);
        NodeNode end = selectedNode;
        createConnection(start.getId(), "next", action.getId(), "in");
        createConnection(action.getId(), "next", end.getId(), "in");
        setStatus("已从模板创建：动作链", 0xFF88FF88);
    }

    private LibraryNode buildFavoritesGroup() {
        if (favoriteNodeTypes.isEmpty()) {
            return null;
        }
        List<LibraryNode> children = new ArrayList<>();
        for (String nodeType : favoriteNodeTypes) {
            children.add(LibraryNode.item("fav_" + nodeType, getDisplayNodeName(nodeType), nodeType));
        }
        return LibraryNode.group("group_favorites", "收藏节点", children.toArray(new LibraryNode[0]));
    }

    private LibraryNode buildRecentGroup() {
        if (recentNodeTypes.isEmpty()) {
            return null;
        }
        List<LibraryNode> children = new ArrayList<>();
        for (String nodeType : recentNodeTypes) {
            children.add(LibraryNode.item("recent_" + nodeType, getDisplayNodeName(nodeType), nodeType));
        }
        return LibraryNode.group("group_recent", "最近使用", children.toArray(new LibraryNode[0]));
    }

    private int computeToolbarContentHeight(String keyword) {
        int gap = 3;
        int btnH = 18;
        int cursor = 0;
        cursor += btnH + gap; // flow group
        if (groupFlowExpanded) {
            cursor = addVisibleNodeButtonHeights(
                    new int[] { BTN_ADD_START, BTN_ADD_ACTION, BTN_ADD_IF, BTN_ADD_WAIT, BTN_ADD_END,
                            BTN_ADD_RUN_SEQUENCE,
                            BTN_ADD_SUBGRAPH, BTN_ADD_PARALLEL, BTN_ADD_JOIN, BTN_ADD_DELAY_TASK },
                    keyword, cursor, btnH, gap);
        }
        cursor += btnH + gap; // trigger group
        if (groupTriggerExpanded) {
            cursor = addVisibleNodeButtonHeights(new int[] { BTN_ADD_TRIGGER }, keyword, cursor, btnH, gap);
        }
        cursor += btnH + gap; // var group
        if (groupVarExpanded) {
            cursor = addVisibleNodeButtonHeights(new int[] { BTN_ADD_SETVAR, BTN_ADD_RESULT_CACHE }, keyword, cursor,
                    btnH,
                    gap);
        }
        cursor += btnH + gap; // advanced group
        if (groupAdvancedExpanded) {
            cursor = addVisibleNodeButtonHeights(new int[] { BTN_ADD_RESOURCE_LOCK }, keyword, cursor, btnH, gap);
        }
        return Math.max(0, cursor - gap);
    }

    private int addVisibleNodeButtonHeights(int[] buttonIds, String keyword, int cursor, int btnH, int gap) {
        for (int buttonId : buttonIds) {
            if (!shouldShowNodeButton(buttonId, keyword)) {
                continue;
            }
            cursor += btnH + gap;
        }
        return cursor;
    }

    private int layoutToolbarGroup(int groupButtonId, int[] childButtonIds, boolean expanded, String keyword,
            int yCursor) {
        int gap = 3;
        int btnH = 18;

        GuiButton groupButton = findButtonById(groupButtonId);
        if (groupButton != null) {
            groupButton.displayString = getGroupButtonLabel(groupButtonId);
            layoutToolbarButton(groupButton, yCursor);
        }
        yCursor += btnH + gap;

        for (int buttonId : childButtonIds) {
            GuiButton button = findButtonById(buttonId);
            if (button == null) {
                continue;
            }
            String nodeType = getNodeTypeForButton(buttonId);
            button.displayString = getNodeButtonLabel(nodeType);
            boolean show = expanded && shouldShowNodeButton(buttonId, keyword);
            if (!show) {
                button.visible = false;
                button.enabled = false;
                continue;
            }
            layoutToolbarButton(button, yCursor);
            yCursor += btnH + gap;
        }
        return yCursor;
    }

    private void layoutToolbarButton(GuiButton button, int contentOffsetY) {
        if (button == null) {
            return;
        }
        int y = toolbarTreeY + contentOffsetY - toolbarScrollOffset;
        button.x = toolbarTreeX + 1;
        button.y = y;
        button.width = toolbarButtonWidth;
        button.height = 18;

        boolean inViewport = y + button.height > toolbarTreeY && y < toolbarTreeY + toolbarTreeHeight;
        button.visible = inViewport;
        button.enabled = inViewport;
    }

    private boolean shouldShowNodeButton(int buttonId, String keyword) {
        String nodeType = getNodeTypeForButton(buttonId);
        if (nodeType == null || nodeType.isEmpty()) {
            return false;
        }
        return keyword == null || keyword.isEmpty() || matchesNodeSearch(nodeType, keyword);
    }

    private GuiButton findButtonById(int buttonId) {
        for (GuiButton button : this.buttonList) {
            if (button != null && button.id == buttonId) {
                return button;
            }
        }
        return null;
    }

    private int getToolbarMaxScroll() {
        return Math.max(0, toolbarContentHeight - toolbarTreeHeight);
    }

    private boolean isNodeTypeGroupExpanded(String nodeType) {
        if (NodeNode.TYPE_TRIGGER.equals(nodeType)) {
            return groupTriggerExpanded;
        }
        if (NodeNode.TYPE_SET_VAR.equals(nodeType) || NodeNode.TYPE_RESULT_CACHE.equals(nodeType)) {
            return groupVarExpanded;
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(nodeType)) {
            return groupAdvancedExpanded;
        }
        return groupFlowExpanded;
    }

    private String getGroupButtonLabel(int buttonId) {
        switch (buttonId) {
            case BTN_GROUP_FLOW:
                return (groupFlowExpanded ? "▼ " : "▶ ") + "流程 / 条件";
            case BTN_GROUP_TRIGGER:
                return (groupTriggerExpanded ? "▼ " : "▶ ") + "触发器";
            case BTN_GROUP_VAR:
                return (groupVarExpanded ? "▼ " : "▶ ") + "变量 / 缓存";
            case BTN_GROUP_ADVANCED:
                return (groupAdvancedExpanded ? "▼ " : "▶ ") + "高级控制";
            default:
                return "";
        }
    }

    private String getNodeTypeForButton(int buttonId) {
        switch (buttonId) {
            case BTN_ADD_START:
                return NodeNode.TYPE_START;
            case BTN_ADD_ACTION:
                return NodeNode.TYPE_ACTION;
            case BTN_ADD_IF:
                return NodeNode.TYPE_IF;
            case BTN_ADD_SETVAR:
                return NodeNode.TYPE_SET_VAR;
            case BTN_ADD_WAIT:
                return NodeNode.TYPE_WAIT_UNTIL;
            case BTN_ADD_END:
                return NodeNode.TYPE_END;
            case BTN_ADD_TRIGGER:
                return NodeNode.TYPE_TRIGGER;
            case BTN_ADD_RUN_SEQUENCE:
                return NodeNode.TYPE_RUN_SEQUENCE;
            case BTN_ADD_SUBGRAPH:
                return NodeNode.TYPE_SUBGRAPH;
            case BTN_ADD_PARALLEL:
                return NodeNode.TYPE_PARALLEL;
            case BTN_ADD_JOIN:
                return NodeNode.TYPE_JOIN;
            case BTN_ADD_DELAY_TASK:
                return NodeNode.TYPE_DELAY_TASK;
            case BTN_ADD_RESOURCE_LOCK:
                return NodeNode.TYPE_RESOURCE_LOCK;
            case BTN_ADD_RESULT_CACHE:
                return NodeNode.TYPE_RESULT_CACHE;
            default:
                return "";
        }
    }

    private boolean isToolbarNodeButton(int buttonId) {
        return buttonId == BTN_ADD_START
                || buttonId == BTN_ADD_ACTION
                || buttonId == BTN_ADD_IF
                || buttonId == BTN_ADD_SETVAR
                || buttonId == BTN_ADD_WAIT
                || buttonId == BTN_ADD_END
                || buttonId == BTN_ADD_TRIGGER
                || buttonId == BTN_ADD_RUN_SEQUENCE
                || buttonId == BTN_ADD_SUBGRAPH
                || buttonId == BTN_ADD_PARALLEL
                || buttonId == BTN_ADD_JOIN
                || buttonId == BTN_ADD_DELAY_TASK
                || buttonId == BTN_ADD_RESOURCE_LOCK
                || buttonId == BTN_ADD_RESULT_CACHE;
    }

    private String getNodeButtonLabel(String nodeType) {
        String label = getDisplayNodeName(nodeType);
        return isFavoriteNodeType(nodeType) ? "★ " + label : label;
    }

    private boolean isFavoriteNodeType(String nodeType) {
        return nodeType != null && favoriteNodeTypes.contains(nodeType);
    }

    private void toggleFavoriteNodeType(String nodeType) {
        if (nodeType == null || nodeType.trim().isEmpty()) {
            return;
        }
        if (favoriteNodeTypes.contains(nodeType)) {
            favoriteNodeTypes.remove(nodeType);
            setStatus("已取消收藏节点: " + getDisplayNodeName(nodeType), 0xFFCCCCCC);
        } else {
            favoriteNodeTypes.add(nodeType);
            setStatus("已收藏节点: " + getDisplayNodeName(nodeType), 0xFF88FF88);
        }
        saveLibraryPanelState();
        updateToolbarButtonVisibility();
    }

    private void recordRecentNodeType(String nodeType) {
        String normalizedType = normalize(nodeType);
        if (normalizedType.isEmpty() || normalizedType.startsWith("template:")) {
            return;
        }
        recentNodeTypes.remove(normalizedType);
        recentNodeTypes.add(0, normalizedType);
        while (recentNodeTypes.size() > 12) {
            recentNodeTypes.remove(recentNodeTypes.size() - 1);
        }
        saveLibraryPanelState();
        updateToolbarButtonVisibility();
    }

    private Path getLibraryPanelStateFile() {
        return ProfileManager.getCurrentProfileDir().resolve("node_library_panel_state.json");
    }

    private void loadLibraryPanelState() {
        Path file = getLibraryPanelStateFile();
        if (!Files.exists(file)) {
            expandedLibraryGroupIds.clear();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LibraryPanelState state = GSON.fromJson(reader, LibraryPanelState.class);
            expandedLibraryGroupIds.clear();
            favoriteNodeTypes.clear();
            recentNodeTypes.clear();
            if (state != null) {
                if (state.expandedGroups != null) {
                    expandedLibraryGroupIds.addAll(state.expandedGroups);
                }
                if (state.favoriteTypes != null) {
                    favoriteNodeTypes.addAll(state.favoriteTypes);
                }
                if (state.recentTypes != null) {
                    recentNodeTypes.addAll(state.recentTypes);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void saveLibraryPanelState() {
        try {
            Path file = getLibraryPanelStateFile();
            Files.createDirectories(file.getParent());
            LibraryPanelState state = new LibraryPanelState();
            state.expandedGroups = new ArrayList<>(expandedLibraryGroupIds);
            state.favoriteTypes = new ArrayList<>(favoriteNodeTypes);
            state.recentTypes = new ArrayList<>(recentNodeTypes);
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
        } catch (Exception ignored) {
        }
    }

    private boolean matchesNodeSearch(String nodeType, String keyword) {
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isEmpty()) {
            return true;
        }
        String displayName = normalize(NodeNode.getDisplayName(nodeType));
        String typeName = normalize(nodeType);
        String aliases = normalize(getNodeAliases(nodeType));
        return displayName.contains(normalizedKeyword)
                || typeName.contains(normalizedKeyword)
                || aliases.contains(normalizedKeyword);
    }

    private String getNodeAliases(String nodeType) {
        if (NodeNode.TYPE_START.equals(nodeType)) {
            return "start begin entry 开始 入口";
        }
        if (NodeNode.TYPE_ACTION.equals(nodeType)) {
            return "action command 动作 命令";
        }
        if (NodeNode.TYPE_IF.equals(nodeType)) {
            return "if condition 条件 判断 分支";
        }
        if (NodeNode.TYPE_SET_VAR.equals(nodeType)) {
            return "setvar variable var 变量 赋值";
        }
        if (NodeNode.TYPE_WAIT_UNTIL.equals(nodeType)) {
            return "waituntil wait timeout 等待 超时";
        }
        if (NodeNode.TYPE_END.equals(nodeType)) {
            return "end finish stop 结束 完成";
        }
        if (NodeNode.TYPE_TRIGGER.equals(nodeType)) {
            return "trigger event 触发器 事件";
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(nodeType)) {
            return "runsequence sequence rungraph 运行序列 运行图";
        }
        if (NodeNode.TYPE_SUBGRAPH.equals(nodeType)) {
            return "subgraph childgraph 子图";
        }
        if (NodeNode.TYPE_PARALLEL.equals(nodeType)) {
            return "parallel 并行";
        }
        if (NodeNode.TYPE_JOIN.equals(nodeType)) {
            return "join 汇聚 合流";
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(nodeType)) {
            return "delaytask delay 延迟任务 延迟";
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(nodeType)) {
            return "resourcelock lock 资源锁 锁";
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(nodeType)) {
            return "resultcache cache 缓存 结果缓存";
        }
        return "";
    }

    private void updateSelectedNodeFields() {
    }

    private void openQuickAddOverlay(int mouseX, int mouseY) {
        initializeLibraryTreeIfNeeded();
        quickAddVisible = true;
        quickAddWidth = 240;
        quickAddHeight = 176;
        quickAddX = Math.max(canvasX + 8,
                Math.min(mouseX - quickAddWidth / 2, canvasX + canvasWidth - quickAddWidth - 8));
        quickAddY = Math.max(canvasY + 8, Math.min(mouseY - 24, canvasY + canvasHeight - quickAddHeight - 8));
        quickAddTargetCanvasX = screenToCanvasX(mouseX);
        quickAddTargetCanvasY = screenToCanvasY(mouseY);
        quickAddScrollOffset = 0;
        quickAddHoverIndex = 0;
        if (quickAddSearchField != null) {
            quickAddSearchField.setVisible(true);
            quickAddSearchField.x = quickAddX + 8;
            quickAddSearchField.y = quickAddY + 24;
            quickAddSearchField.width = quickAddWidth - 16;
            quickAddSearchField.height = 16;
            quickAddSearchField.setText("");
            quickAddSearchField.setFocused(true);
        }
        setStatus("已打开快捷添加面板", 0xFF88FF88);
    }

    private void closeQuickAddOverlay() {
        quickAddVisible = false;
        quickAddHoverIndex = -1;
        quickAddScrollOffset = 0;
        if (quickAddSearchField != null) {
            quickAddSearchField.setFocused(false);
            quickAddSearchField.setVisible(false);
            quickAddSearchField.setText("");
        }
    }

    private List<QuickAddEntry> getQuickAddEntries() {
        initializeLibraryTreeIfNeeded();
        List<QuickAddEntry> entries = new ArrayList<>();
        String keyword = quickAddSearchField == null ? "" : normalize(quickAddSearchField.getText());
        appendQuickAddEntries(entries, libraryRoots, keyword);
        return entries;
    }

    private void appendQuickAddEntries(List<QuickAddEntry> entries, List<LibraryNode> nodes, String keyword) {
        if (nodes == null) {
            return;
        }
        for (LibraryNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.isGroup()) {
                appendQuickAddEntries(entries, node.children, keyword);
                continue;
            }
            QuickAddEntry entry = new QuickAddEntry();
            entry.node = node;
            entry.searchText = normalize(safe(node.label) + " " + safe(node.id) + " " + safe(node.nodeType)
                    + " " + getNodeAliases(node.nodeType));
            if (keyword.isEmpty() || entry.searchText.contains(keyword)) {
                entries.add(entry);
            }
        }
    }

    private void drawQuickAddOverlay(int mouseX, int mouseY) {
        Gui.drawRect(quickAddX, quickAddY, quickAddX + quickAddWidth, quickAddY + quickAddHeight, 0xEE1D2633);
        Gui.drawRect(quickAddX, quickAddY, quickAddX + quickAddWidth, quickAddY + 1, 0xFF6FAFE3);
        Gui.drawRect(quickAddX, quickAddY + quickAddHeight - 1, quickAddX + quickAddWidth, quickAddY + quickAddHeight,
                0xFF6FAFE3);
        this.drawString(this.fontRenderer, "快捷添加节点", quickAddX + 8, quickAddY + 8, 0xFFFFFFFF);
        this.drawString(this.fontRenderer, "双击空白处创建", quickAddX + quickAddWidth - 82, quickAddY + 8, 0xFFB8D7F0);

        if (quickAddSearchField != null) {
            quickAddSearchField.x = quickAddX + 8;
            quickAddSearchField.y = quickAddY + 24;
            quickAddSearchField.width = quickAddWidth - 16;
            quickAddSearchField.height = 16;
            drawThemedTextField(quickAddSearchField);
        }

        List<QuickAddEntry> entries = getQuickAddEntries();
        int listX = quickAddX + 8;
        int listY = quickAddY + 46;
        int listW = quickAddWidth - 16;
        int listH = quickAddHeight - 54;
        int rowH = 18;
        int maxVisible = Math.max(1, listH / rowH);

        if (entries.isEmpty()) {
            Gui.drawRect(listX, listY, listX + listW, listY + listH, 0x22111111);
            this.drawString(this.fontRenderer, "没有匹配节点", listX + 6, listY + 6, 0xFFAAAAAA);
            return;
        }

        quickAddHoverIndex = MathHelper.clamp(quickAddHoverIndex, 0, entries.size() - 1);
        quickAddScrollOffset = MathHelper.clamp(quickAddScrollOffset, 0, Math.max(0, entries.size() - maxVisible));

        for (int i = 0; i < maxVisible; i++) {
            int index = i + quickAddScrollOffset;
            if (index >= entries.size()) {
                break;
            }
            QuickAddEntry entry = entries.get(index);
            int rowY = listY + i * rowH;
            boolean hovered = isInside(mouseX, mouseY, listX, rowY, listW, rowH);
            if (hovered) {
                quickAddHoverIndex = index;
            }
            boolean selected = index == quickAddHoverIndex;
            int bg = selected ? 0xAA2D6A9F : (hovered ? 0x88405A78 : 0x22273344);
            Gui.drawRect(listX, rowY, listX + listW, rowY + rowH - 1, bg);
            this.drawString(this.fontRenderer, safe(entry.node.label), listX + 6, rowY + 5, 0xFFFFFFFF);
        }

        drawQuickAddGhostPreview(entries);
    }

    private void drawQuickAddGhostPreview(List<QuickAddEntry> entries) {
        if (!quickAddVisible || entries == null || entries.isEmpty()
                || quickAddHoverIndex < 0 || quickAddHoverIndex >= entries.size()) {
            return;
        }
        QuickAddEntry entry = entries.get(quickAddHoverIndex);
        if (entry == null || entry.node == null || entry.node.nodeType == null
                || entry.node.nodeType.trim().isEmpty()) {
            return;
        }

        int previewX = Math.min(canvasX + canvasWidth - scaled(NODE_WIDTH) - 24, quickAddX + quickAddWidth + 14);
        int previewY = Math.max(canvasY + 12, Math.min(quickAddY, canvasY + canvasHeight - scaled(NODE_HEIGHT) - 24));

        GlStateManager.pushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 0.72F);

        NodeNode previewNode = new NodeNode();
        previewNode.setId("preview_node");
        previewNode.setType(entry.node.nodeType);
        previewNode.setX(screenToCanvasX(previewX));
        previewNode.setY(screenToCanvasY(previewY));
        previewNode.setData(entry.node.presetData == null
                ? createDefaultData(entry.node.nodeType)
                : new JsonParser().parse(entry.node.presetData.toString()).getAsJsonObject());
        previewNode.setCollapsed(false);
        previewNode.setMinimized(false);

        drawNodeCard(previewNode, -9999, -9999);

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        this.drawString(this.fontRenderer, "预览: " + safe(entry.node.label), previewX, previewY - 12, 0xFFDDEEFF);
    }

    private boolean handleQuickAddMouseClick(int mouseX, int mouseY, int mouseButton) {
        if (!quickAddVisible) {
            return false;
        }
        int listX = quickAddX + 8;
        int listY = quickAddY + 46;
        int listW = quickAddWidth - 16;
        int listH = quickAddHeight - 54;
        int rowH = 18;

        if (quickAddSearchField != null && isInside(mouseX, mouseY, quickAddSearchField.x, quickAddSearchField.y,
                quickAddSearchField.width, quickAddSearchField.height)) {
            if (mouseButton == 0) {
                quickAddSearchField.mouseClicked(mouseX, mouseY, mouseButton);
            }
            return true;
        }

        if (isInside(mouseX, mouseY, listX, listY, listW, listH)) {
            if (mouseButton != 0) {
                return true;
            }
            List<QuickAddEntry> entries = getQuickAddEntries();
            int maxVisible = Math.max(1, listH / rowH);
            int index = (mouseY - listY) / rowH + quickAddScrollOffset;
            if (index >= 0 && index < entries.size() && index < quickAddScrollOffset + maxVisible) {
                quickAddHoverIndex = index;
                createQuickAddHoveredNode();
            }
            return true;
        }

        return isInside(mouseX, mouseY, quickAddX, quickAddY, quickAddWidth, quickAddHeight);
    }

    private void moveQuickAddSelection(int delta) {
        List<QuickAddEntry> entries = getQuickAddEntries();
        if (entries.isEmpty()) {
            quickAddHoverIndex = -1;
            return;
        }
        quickAddHoverIndex = MathHelper.clamp(quickAddHoverIndex + delta, 0, entries.size() - 1);

        int listH = quickAddHeight - 54;
        int rowH = 18;
        int maxVisible = Math.max(1, listH / rowH);
        if (quickAddHoverIndex < quickAddScrollOffset) {
            quickAddScrollOffset = quickAddHoverIndex;
        } else if (quickAddHoverIndex >= quickAddScrollOffset + maxVisible) {
            quickAddScrollOffset = quickAddHoverIndex - maxVisible + 1;
        }
    }

    private void createQuickAddHoveredNode() {
        List<QuickAddEntry> entries = getQuickAddEntries();
        if (entries.isEmpty() || quickAddHoverIndex < 0 || quickAddHoverIndex >= entries.size()) {
            setStatus("没有可创建的节点", 0xFFFFFF88);
            return;
        }
        QuickAddEntry entry = entries.get(quickAddHoverIndex);
        addFromLibraryNode(entry.node, quickAddTargetCanvasX, quickAddTargetCanvasY);
        closeQuickAddOverlay();
        setStatus("已快捷创建节点: " + safe(entry.node.label), 0xFF88FF88);
    }

    private void switchToWorkflow(NodeGraph targetGraph) {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        if (targetGraph == null || targetGraph == currentGraph) {
            return;
        }
        if (currentGraph != null) {
            applyFieldChanges();
            saveDraftSilently();
        }
        currentGraph = targetGraph;
        currentGraphIndex = graphs.indexOf(targetGraph);
        if (currentGraphIndex < 0) {
            graphs.add(targetGraph);
            currentGraphIndex = graphs.size() - 1;
        }
        selectedNode = null;
        selectedEdgeId = null;
        selectedNodeIds.clear();
        propertyPanelVisible = false;
        linkingFromNodeId = null;
        linkingFromPort = null;
        propertyScrollOffset = 0;
        updateSelectedNodeFields();
        refreshLiveValidation();
        updateToolbarButtonVisibility();
        ensureWorkflowRowVisible(currentGraphIndex);
        setStatus("已切换工作流: " + safe(currentGraph.getName()), 0xFF88FF88);
    }

    private void createNewWorkflow() {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        NodeGraph graph = createWorkflowSkeleton(findUniqueGraphName("node_graph"));
        graphs.add(graph);
        currentGraphIndex = graphs.size() - 1;
        currentGraph = graph;
        selectedNode = null;
        selectedEdgeId = null;
        selectedNodeIds.clear();
        propertyPanelVisible = false;
        linkingFromNodeId = null;
        linkingFromPort = null;
        updateSelectedNodeFields();
        saveDraftSilently();
        updateToolbarButtonVisibility();
        setStatus("已新建工作流: " + safe(graph.getName()), 0xFF88FF88);
    }

    private void renameCurrentWorkflow() {
        renameWorkflow(currentGraph);
    }

    private void renameWorkflow(NodeGraph targetGraph) {
        workflowContextMenuVisible = false;
        if (targetGraph == null) {
            setStatus("当前没有可重命名的工作流", 0xFFFF8888);
            return;
        }
        if (targetGraph != currentGraph) {
            switchToWorkflow(targetGraph);
        }
        String oldName = safe(targetGraph.getName());
        this.mc.displayGuiScreen(new GuiTextInput(this, "重命名工作流", oldName, text -> {
            String newName = safe(text).trim();
            if (newName.isEmpty()) {
                setStatus("工作流名称不能为空", 0xFFFF8888);
                mc.displayGuiScreen(this);
                return;
            }
            for (NodeGraph graph : graphs) {
                if (graph != null && graph != targetGraph && newName.equalsIgnoreCase(safe(graph.getName()))) {
                    setStatus("工作流名称已存在: " + newName, 0xFFFF8888);
                    mc.displayGuiScreen(this);
                    return;
                }
            }
            targetGraph.setName(newName);
            if (targetGraph == currentGraph) {
                saveDraftSilently();
            }
            updateToolbarButtonVisibility();
            setStatus("已重命名工作流: " + oldName + " -> " + newName, 0xFF88FF88);
            mc.displayGuiScreen(this);
        }));
    }

    private void duplicateCurrentWorkflow() {
        duplicateWorkflow(currentGraph);
    }

    private void duplicateWorkflow(NodeGraph targetGraph) {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        if (targetGraph == null) {
            setStatus("当前没有可复制的工作流", 0xFFFF8888);
            return;
        }
        workflowClipboardGraph = GSON.fromJson(GSON.toJson(targetGraph), NodeGraph.class);
        if (workflowClipboardGraph == null) {
            setStatus("复制工作流失败", 0xFFFF8888);
            return;
        }
        workflowClipboardName = safe(targetGraph.getName());
        setStatus("已复制工作流: " + workflowClipboardName, 0xFF88FF88);
    }

    private void cutCurrentWorkflow() {
        cutWorkflow(currentGraph);
    }

    private void cutWorkflow(NodeGraph targetGraph) {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        if (targetGraph == null) {
            setStatus("当前没有可剪切的工作流", 0xFFFF8888);
            return;
        }
        String workflowName = safe(targetGraph.getName());
        workflowClipboardGraph = GSON.fromJson(GSON.toJson(targetGraph), NodeGraph.class);
        if (workflowClipboardGraph == null) {
            setStatus("剪切工作流失败", 0xFFFF8888);
            return;
        }
        workflowClipboardName = workflowName;
        deleteWorkflow(targetGraph);
        setStatus("已剪切工作流: " + workflowName, 0xFF88FF88);
    }

    private void pasteWorkflowFromClipboard() {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        if (workflowClipboardGraph == null) {
            setStatus("工作流剪贴板为空", 0xFFFFFF88);
            return;
        }
        NodeGraph pasted = GSON.fromJson(GSON.toJson(workflowClipboardGraph), NodeGraph.class);
        if (pasted == null) {
            setStatus("粘贴工作流失败", 0xFFFF8888);
            return;
        }
        String baseName = workflowClipboardName.trim().isEmpty() ? "node_graph" : workflowClipboardName.trim();
        pasted.setName(findUniqueGraphName(baseName + "_copy"));
        graphs.add(pasted);
        switchToWorkflow(pasted);
        saveDraftSilently();
        setStatus("已粘贴工作流: " + safe(pasted.getName()), 0xFF88FF88);
    }

    private void deleteCurrentWorkflow() {
        deleteWorkflow(currentGraph);
    }

    private void deleteWorkflow(NodeGraph targetGraph) {
        workflowContextMenuVisible = false;
        workflowListFocused = true;
        if (targetGraph == null) {
            setStatus("当前没有可删除的工作流", 0xFFFF8888);
            return;
        }
        if (targetGraph != currentGraph) {
            switchToWorkflow(targetGraph);
        }
        String deleteName = safe(currentGraph.getName());
        int index = currentGraphIndex;
        graphs.remove(currentGraph);
        if (graphs.isEmpty()) {
            NodeGraph graph = createWorkflowSkeleton(findUniqueGraphName("node_graph"));
            graphs.add(graph);
        }
        currentGraphIndex = MathHelper.clamp(index, 0, graphs.size() - 1);
        currentGraph = graphs.get(currentGraphIndex);
        selectedNode = null;
        selectedEdgeId = null;
        selectedNodeIds.clear();
        propertyPanelVisible = false;
        linkingFromNodeId = null;
        linkingFromPort = null;
        updateSelectedNodeFields();
        saveDraftSilently();
        updateToolbarButtonVisibility();
        setStatus("已删除工作流: " + deleteName, 0xFFFF8888);
    }

    private NodeGraph createWorkflowSkeleton(String name) {
        NodeGraph graph = new NodeGraph();
        graph.setName(name);
        graph.setSchemaVersion(NodeGraph.CURRENT_SCHEMA_VERSION);

        NodeNode start = new NodeNode();
        start.setId("start_1");
        start.setType(NodeNode.TYPE_START);
        start.setX(80);
        start.setY(80);
        start.setData(createDefaultData(NodeNode.TYPE_START));

        NodeNode end = new NodeNode();
        end.setId("end_1");
        end.setType(NodeNode.TYPE_END);
        end.setX(260);
        end.setY(80);
        end.setData(createDefaultData(NodeNode.TYPE_END));

        NodeEdge edge = new NodeEdge();
        edge.setId("edge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        edge.setFromNodeId(start.getId());
        edge.setFromPort(NodeNode.PORT_NEXT);
        edge.setToNodeId(end.getId());
        edge.setToPort(NodeNode.PORT_IN);

        graph.getNodes().add(start);
        graph.getNodes().add(end);
        graph.getEdges().add(edge);
        return graph;
    }

    private String findUniqueGraphName(String base) {
        String prefix = safe(base).trim().isEmpty() ? "node_graph" : safe(base).trim();
        String candidate = prefix;
        int index = 1;
        boolean exists = true;
        while (exists) {
            exists = false;
            for (NodeGraph graph : graphs) {
                if (graph != null && candidate.equalsIgnoreCase(safe(graph.getName()))) {
                    exists = true;
                    candidate = prefix + "_" + index++;
                    break;
                }
            }
        }
        return candidate;
    }

    private String createUniqueNodeId(String type) {
        String base = normalize(type);
        if (base.isEmpty()) {
            base = "node";
        }
        int index = 1;
        String candidate = base + "_" + index;
        while (findNodeById(candidate) != null) {
            index++;
            candidate = base + "_" + index;
        }
        return candidate;
    }

    private List<NodeNode> safeNodes() {
        return currentGraph == null || currentGraph.getNodes() == null ? Collections.<NodeNode>emptyList()
                : currentGraph.getNodes();
    }

    private List<NodeNode> reverseNodes() {
        List<NodeNode> list = new ArrayList<NodeNode>(safeNodes());
        Collections.reverse(list);
        return list;
    }

    private List<NodeEdge> safeEdges() {
        return currentGraph == null || currentGraph.getEdges() == null ? Collections.<NodeEdge>emptyList()
                : currentGraph.getEdges();
    }

    private List<NodeCanvasNote> safeNotes() {
        return currentGraph == null || currentGraph.getNotes() == null ? Collections.<NodeCanvasNote>emptyList()
                : currentGraph.getNotes();
    }

    private List<NodeGroupBox> safeGroups() {
        return currentGraph == null || currentGraph.getGroups() == null ? Collections.<NodeGroupBox>emptyList()
                : currentGraph.getGroups();
    }

    private NodeNode findNodeById(String id) {
        if (id == null) {
            return null;
        }
        for (NodeNode node : safeNodes()) {
            if (node != null && id.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private int getNodeColor(String type) {
        if (NodeNode.TYPE_START.equals(type)) {
            return 0xFF2E8B57;
        }
        if (NodeNode.TYPE_ACTION.equals(type)) {
            return 0xFF4682B4;
        }
        if (NodeNode.TYPE_IF.equals(type)) {
            return 0xFF8A2BE2;
        }
        if (NodeNode.TYPE_SET_VAR.equals(type)) {
            return 0xFFB8860B;
        }
        if (NodeNode.TYPE_WAIT_UNTIL.equals(type)) {
            return 0xFFCD5C5C;
        }
        if (NodeNode.TYPE_END.equals(type)) {
            return 0xFF696969;
        }
        return 0xFF555555;
    }

    private int getColorTagColor(String tag) {
        String normalized = normalize(tag);
        if ("red".equals(normalized)) {
            return 0xFFE74C3C;
        }
        if ("yellow".equals(normalized)) {
            return 0xFFF1C40F;
        }
        if ("green".equals(normalized)) {
            return 0xFF2ECC71;
        }
        if ("blue".equals(normalized)) {
            return 0xFF3498DB;
        }
        if ("purple".equals(normalized)) {
            return 0xFF9B59B6;
        }
        return 0x00000000;
    }

    private void drawNodeColorTag(NodeNode node, int x, int y, int width) {
        int tagColor = getColorTagColor(node.getColorTag());
        if ((tagColor >>> 24) == 0) {
            return;
        }
        Gui.drawRect(x + width - 12, y + 3, x + width - 4, y + 11, tagColor);
        Gui.drawRect(x + width - 13, y + 2, x + width - 3, y + 3, 0xAA000000);
        Gui.drawRect(x + width - 13, y + 11, x + width - 3, y + 12, 0xAA000000);
        Gui.drawRect(x + width - 13, y + 3, x + width - 12, y + 11, 0xAA000000);
        Gui.drawRect(x + width - 4, y + 3, x + width - 3, y + 11, 0xAA000000);
    }

    private void drawCanvasGrid() {
        int grid = scaled(GRID_SIZE);
        if (grid < 8) {
            return;
        }
        int startX = canvasX + Math.floorMod(canvasOffsetX, grid);
        int startY = canvasY + Math.floorMod(canvasOffsetY, grid);
        int minor = 0x222F3E55;
        int major = 0x334A6A88;

        for (int x = startX; x < canvasX + canvasWidth; x += grid) {
            int logical = Math.round((x - canvasX - canvasOffsetX) / canvasZoom);
            boolean isMajor = logical % (GRID_SIZE * 5) == 0;
            Gui.drawRect(x, canvasY, x + 1, canvasY + canvasHeight, isMajor ? major : minor);
        }
        for (int y = startY; y < canvasY + canvasHeight; y += grid) {
            int logical = Math.round((y - canvasY - canvasOffsetY) / canvasZoom);
            boolean isMajor = logical % (GRID_SIZE * 5) == 0;
            Gui.drawRect(canvasX, y, canvasX + canvasWidth, y + 1, isMajor ? major : minor);
        }
    }

    private int snapToGrid(int value) {
        return Math.round(value / (float) GRID_SIZE) * GRID_SIZE;
    }

    private int parseColor(String color, int fallback) {
        try {
            String text = safe(color).trim();
            if (text.startsWith("!")) {
                text = text.substring(1);
            }
            if (text.length() == 6) {
                return (int) (0xFF000000L | Long.parseLong(text, 16));
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private void drawCanvasNote(NodeCanvasNote note) {
        if (note == null) {
            return;
        }
        int x = toScreenX(note.getX());
        int y = toScreenY(note.getY());
        int w = scaled(note.getWidth());
        int h = scaled(note.getHeight());
        int color = parseColor(note.getColor(), 0xFFF6E58D);
        Gui.drawRect(x, y, x + w, y + h, (color & 0x00FFFFFF) | 0x55000000);
        Gui.drawRect(x, y, x + w, y + 14, color);
        Gui.drawRect(x, y, x + w, y + 1, 0xAA000000);
        Gui.drawRect(x, y + h - 1, x + w, y + h, 0xAA000000);
        Gui.drawRect(x, y, x + 1, y + h, 0xAA000000);
        Gui.drawRect(x + w - 1, y, x + w, y + h, 0xAA000000);
        this.drawString(this.fontRenderer, safe(note.getTitle()), x + 4, y + 3, 0xFF1E1E1E);
        this.fontRenderer.drawSplitString(safe(note.getContent()), x + 4, y + 18, Math.max(20, w - 8), 0xFFEFEFEF);
    }

    private void drawGroupBox(NodeGroupBox group) {
        if (group == null) {
            return;
        }
        int x = toScreenX(group.getX());
        int y = toScreenY(group.getY());
        int w = scaled(group.getWidth());
        int h = scaled(group.getHeight());
        int color = parseColor(group.getColor(), 0xFF7ED6DF);
        Gui.drawRect(x, y, x + w, y + h, 0x1122FFFF);
        Gui.drawRect(x, y, x + w, y + 1, color);
        Gui.drawRect(x, y + h - 1, x + w, y + h, color);
        Gui.drawRect(x, y, x + 1, y + h, color);
        Gui.drawRect(x + w - 1, y, x + w, y + h, color);
        this.drawString(this.fontRenderer, safe(group.getTitle()), x + 4, y + 4, color);
        if (!safe(group.getTag()).isEmpty()) {
            this.drawString(this.fontRenderer, "!" + safe(group.getTag()), x + w - 40, y + 4, 0xFFEFEFEF);
        }
    }

    private void drawLine(int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            Gui.drawRect(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            Gui.drawRect(px, py, px + 2, py + 2, color);
        }
    }

    private int getPortColor(String port, boolean output) {
        String p = normalize(port);
        if ("true".equals(p) || "yes".equals(p) || "success".equals(p) || "next".equals(p)) {
            return output ? 0xFF6FD98C : 0xFF58C777;
        }
        if ("false".equals(p) || "no".equals(p) || "fail".equals(p) || "timeout".equals(p)) {
            return output ? 0xFFE07A7A : 0xFFD46868;
        }
        if ("in".equals(p) || "input".equals(p)) {
            return 0xFFCC6666;
        }
        return output ? 0xFF66CCFF : 0xFFCC9966;
    }

    private int getEdgeColor(NodeEdge edge) {
        if (edge == null) {
            return 0xFF66CCFF;
        }
        return getPortColor(edge.getFromPort(), true);
    }

    private void drawBezierConnection(int x1, int y1, int x2, int y2, int baseColor, boolean runtime, boolean hovered,
            boolean selected) {
        int color = baseColor;
        int thickness = 2;
        if (runtime) {
            color = 0xFFFFFF55;
            thickness = 3;
        }
        if (hovered) {
            color = 0xFFFFFFFF;
            thickness = Math.max(thickness, 3);
        }
        if (selected) {
            color = 0xFFFFCC55;
            thickness = Math.max(thickness, 4);
        }

        int dx = Math.abs(x2 - x1);
        int offset = Math.max(scaled(26), Math.min(scaled(90), dx / 2));
        float cx1 = x1 + offset;
        float cy1 = y1;
        float cx2 = x2 - offset;
        float cy2 = y2;

        int steps = Math.max(24, Math.min(72, dx / 6 + Math.abs(y2 - y1) / 8 + 16));
        float prevX = x1;
        float prevY = y1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float omt = 1.0f - t;
            float px = omt * omt * omt * x1
                    + 3 * omt * omt * t * cx1
                    + 3 * omt * t * t * cx2
                    + t * t * t * x2;
            float py = omt * omt * omt * y1
                    + 3 * omt * omt * t * cy1
                    + 3 * omt * t * t * cy2
                    + t * t * t * y2;
            drawThickSegment(Math.round(prevX), Math.round(prevY), Math.round(px), Math.round(py), color, thickness);
            prevX = px;
            prevY = py;
        }

        drawArrowHead(Math.round(prevX), Math.round(prevY), x2, y2, color, Math.max(6, thickness + 4));
    }

    private void drawThickSegment(int x1, int y1, int x2, int y2, int color, int thickness) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        int half = Math.max(0, thickness / 2);
        for (int i = 0; i <= steps; i++) {
            int px = x1 + dx * i / steps;
            int py = y1 + dy * i / steps;
            Gui.drawRect(px - half, py - half, px + half + 1, py + half + 1, color);
        }
    }

    private void drawArrowHead(int fromX, int fromY, int tipX, int tipY, int color, int size) {
        float angle = (float) Math.atan2(tipY - fromY, tipX - fromX);
        float left = angle + (float) Math.PI * 0.8f;
        float right = angle - (float) Math.PI * 0.8f;
        int lx = tipX + Math.round((float) Math.cos(left) * size);
        int ly = tipY + Math.round((float) Math.sin(left) * size);
        int rx = tipX + Math.round((float) Math.cos(right) * size);
        int ry = tipY + Math.round((float) Math.sin(right) * size);
        drawThickSegment(tipX, tipY, lx, ly, color, 2);
        drawThickSegment(tipX, tipY, rx, ry, color, 2);
    }

    private String findHoveredEdgeId(int mouseX, int mouseY) {
        for (NodeEdge edge : safeEdges()) {
            if (edge != null && isPointNearEdge(edge, mouseX, mouseY, 6.0f)) {
                return edge.getId();
            }
        }
        return null;
    }

    private boolean isPointNearEdge(NodeEdge edge, int mouseX, int mouseY, float threshold) {
        NodeNode from = findNodeById(edge.getFromNodeId());
        NodeNode to = findNodeById(edge.getToNodeId());
        if (from == null || to == null) {
            return false;
        }
        int x1 = getPortCenterX(from, true, edge.getFromPort());
        int y1 = getPortCenterY(from, true, edge.getFromPort());
        int x2 = getPortCenterX(to, false, edge.getToPort());
        int y2 = getPortCenterY(to, false, edge.getToPort());
        int dx = Math.abs(x2 - x1);
        int offset = Math.max(scaled(26), Math.min(scaled(90), dx / 2));
        float cx1 = x1 + offset;
        float cy1 = y1;
        float cx2 = x2 - offset;
        float cy2 = y2;

        int steps = Math.max(24, Math.min(72, dx / 6 + Math.abs(y2 - y1) / 8 + 16));
        float prevX = x1;
        float prevY = y1;
        for (int i = 1; i <= steps; i++) {
            float t = i / (float) steps;
            float omt = 1.0f - t;
            float px = omt * omt * omt * x1
                    + 3 * omt * omt * t * cx1
                    + 3 * omt * t * t * cx2
                    + t * t * t * x2;
            float py = omt * omt * omt * y1
                    + 3 * omt * omt * t * cy1
                    + 3 * omt * t * t * cy2
                    + t * t * t * y2;
            if (distanceToSegment(mouseX, mouseY, prevX, prevY, px, py) <= threshold) {
                return true;
            }
            prevX = px;
            prevY = py;
        }
        return false;
    }

    private float distanceToSegment(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            float ddx = px - x1;
            float ddy = py - y1;
            return (float) Math.sqrt(ddx * ddx + ddy * ddy);
        }
        float t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0f, Math.min(1.0f, t));
        float sx = x1 + t * dx;
        float sy = y1 + t * dy;
        float ddx = px - sx;
        float ddy = py - sy;
        return (float) Math.sqrt(ddx * ddx + ddy * ddy);
    }

    private NodeEdge findEdgeById(String id) {
        if (id == null) {
            return null;
        }
        for (NodeEdge edge : safeEdges()) {
            if (edge != null && id.equals(edge.getId())) {
                return edge;
            }
        }
        return null;
    }

    private NodeEdge findIncomingEdge(String toNodeId, String toPort) {
        for (NodeEdge edge : safeEdges()) {
            if (edge == null) {
                continue;
            }
            if (safe(toNodeId).equals(safe(edge.getToNodeId()))
                    && normalize(toPort).equals(normalize(edge.getToPort()))) {
                return edge;
            }
        }
        return null;
    }

    private void setStatus(String message, int color) {
        this.statusMessage = message == null ? "" : message;
        this.statusColor = color;
        if (activeContext != null && message != null && !message.trim().isEmpty()) {
            activeContext.addConsoleLine("[UI] " + message.trim());
        }
    }

    private void refreshLiveValidation() {
        liveValidationErrors.clear();
        if (currentGraph == null) {
            return;
        }
        applyFieldChanges();
        NodeGraphValidator.ValidationResult result = NodeGraphValidator.validate(currentGraph);
        liveValidationErrors.addAll(result.getErrors());
    }

    private boolean isNodeVisible(NodeNode node) {
        if (node == null) {
            return false;
        }
        int x = toScreenX(node.getX());
        int y = toScreenY(node.getY());
        int w = scaled(NODE_WIDTH);
        int h = scaled(getNodeRenderHeight(node));
        return x + w >= canvasX
                && x <= canvasX + canvasWidth
                && y + h >= canvasY
                && y <= canvasY + canvasHeight;
    }

    private boolean isEdgeVisible(NodeEdge edge) {
        if (edge == null) {
            return false;
        }
        NodeNode from = findNodeById(edge.getFromNodeId());
        NodeNode to = findNodeById(edge.getToNodeId());
        if (from == null || to == null) {
            return false;
        }
        int fromX = toScreenX(from.getX());
        int fromY = toScreenY(from.getY());
        int toX = toScreenX(to.getX());
        int toY = toScreenY(to.getY());
        int nodeW = scaled(NODE_WIDTH);
        int nodeH = scaled(NODE_HEIGHT);
        int padding = scaled(8);
        int minX = Math.min(fromX, toX) - padding;
        int maxX = Math.max(fromX + nodeW, toX + nodeW) + padding;
        int minY = Math.min(fromY, toY) - padding;
        int maxY = Math.max(fromY + nodeH, toY + nodeH) + padding;
        return maxX >= canvasX
                && minX <= canvasX + canvasWidth
                && maxY >= canvasY
                && minY <= canvasY + canvasHeight;
    }

    private void tickActiveRunner() {
        if (activeRunner == null || activeContext == null || Minecraft.getMinecraft().player == null) {
            return;
        }

        try {
            if (activeContext.isCompleted() || activeContext.hasError()) {
                finishActiveRunner();
                return;
            }

            activeRunner.tick(Minecraft.getMinecraft().player);
            runtimeHighlightedNodeId = activeContext.getCurrentNodeId();

            if (activeContext.isPaused()) {
                if (activeContext.isPausedAtBreakpoint() && !breakpointPauseNotified) {
                    breakpointPauseNotified = true;
                    String nodeId = safe(activeContext.getCurrentNodeId());
                    setStatus("命中断点，已暂停: " + nodeId, 0xFFFFCC55);
                    showPopup("命中断点", "节点 ID: " + nodeId + "\n已暂停运行，可使用 Step 或 Continue 继续。");
                    sendChat("§e[节点编辑器] 命中断点，已暂停: " + nodeId);
                } else if (!activeContext.isPausedAtBreakpoint() && !breakpointPauseNotified) {
                    breakpointPauseNotified = true;
                    setStatus("单步执行完成，已暂停在节点: " + safe(activeContext.getCurrentNodeId()), 0xFFFFFF88);
                }
                return;
            }

            if (activeContext.hasError() || activeContext.isCompleted() || activeContext.isWaiting()) {
                finishActiveRunner();
            }
        } catch (Throwable throwable) {
            String message = "运行器异常: " + throwable.getClass().getSimpleName() + ": " + safe(throwable.getMessage());
            if (activeContext != null) {
                activeContext.fail(message);
            }
            runtimeHighlightedNodeId = activeContext == null ? null : activeContext.getCurrentNodeId();
            runtimeErrorNodeId = runtimeHighlightedNodeId;
            setStatus(message, 0xFFFF8888);
            showPopup("节点运行异常", message);
            sendChat("§c[节点编辑器] " + message);
            activeRunner = null;
        }
    }

    private void finishActiveRunner() {
        if (activeContext == null) {
            activeRunner = null;
            return;
        }

        runtimeHighlightedNodeId = activeContext.getCurrentNodeId();

        if (activeContext.isCompleted()) {
            setStatus("运行完成，当前节点: " + safe(activeContext.getCurrentNodeId()), 0xFF88FF88);
            sendChat("§a[节点编辑器] 运行完成");
        } else if (activeContext.isWaiting()) {
            setStatus("运行进入等待节点: " + safe(activeContext.getCurrentNodeId()), 0xFFFFFF88);
            sendChat("§e[节点编辑器] 运行进入等待节点: " + safe(activeContext.getCurrentNodeId()));
        } else if (activeContext.hasError()) {
            String nodeId = safe(activeContext.getCurrentNodeId());
            if (activeContext.getExecutionError() != null
                    && !safe(activeContext.getExecutionError().getNodeId()).isEmpty()) {
                nodeId = safe(activeContext.getExecutionError().getNodeId());
            }
            runtimeErrorNodeId = nodeId;
            String reason = safe(activeContext.getErrorMessage());
            setStatus("运行失败 @ " + nodeId + ": " + reason, 0xFFFF8888);
            showPopup("节点运行失败", "节点 ID: " + nodeId + "\n原因: " + reason);
            sendChat("§c[节点编辑器] 运行失败 @ " + nodeId + ": " + reason);
        }

        activeRunner = null;
        breakpointPauseNotified = false;
    }

    private void showPopup(String title, String message) {
        this.popupTitle = title == null ? "提示" : title;
        this.popupMessage = message == null ? "" : message;
        this.popupVisible = true;
    }

    private void toggleBreakpoint(NodeNode node) {
        if (node == null) {
            return;
        }
        ensureDebugContext();
        if (activeContext.hasBreakpoint(node.getId())) {
            activeContext.removeBreakpoint(node.getId());
            setStatus("已移除断点: " + node.getId(), 0xFFCCCCCC);
        } else {
            activeContext.addBreakpoint(node.getId());
            setStatus("已添加断点: " + node.getId(), 0xFFFFFF88);
        }
    }

    private void ensureDebugContext() {
        if (activeContext == null) {
            activeContext = new NodeExecutionContext();
        }
    }

    private boolean isNodeIgnored(NodeNode node) {
        if (node == null || node.getData() == null) {
            return false;
        }
        JsonObject data = node.getData();
        return data.has("ignored")
                && data.get("ignored").isJsonPrimitive()
                && data.get("ignored").getAsJsonPrimitive().isBoolean()
                && data.get("ignored").getAsBoolean();
    }

    private void toggleIgnoreSelectedNode() {
        if (selectedNode == null) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }
        JsonObject data = selectedNode.getData() == null ? new JsonObject() : selectedNode.getData();
        boolean ignored = isNodeIgnored(selectedNode);
        data.addProperty("ignored", !ignored);
        selectedNode.setData(data);
        saveDraftSilently();
        setStatus(!ignored ? "已忽略节点: " + selectedNode.getId() : "已启用节点: " + selectedNode.getId(), 0xFF88FF88);
    }

    private void updateLayoutMetrics() {
        this.canvasX = TOOLBAR_WIDTH + 8;
        this.canvasY = TOP_BAR_HEIGHT + 8;
        this.propertyX = this.width - PROPERTY_WIDTH - 12;
        this.propertyY = TOP_BAR_HEIGHT + 8;
        this.consoleX = canvasX;
        this.consoleWidth = Math.max(120, this.width - canvasX - 8);
        this.consoleHeight = consoleVisible ? CONSOLE_HEIGHT : 0;
        int bottomReserved = consoleVisible ? (consoleHeight + 8) : 0;
        this.canvasWidth = Math.max(120, this.width - canvasX - 8);
        this.canvasHeight = this.height - canvasY - 8 - bottomReserved;
        this.consoleY = canvasY + canvasHeight + 8;
        this.propertyHeight = this.height - propertyY - 8 - bottomReserved;
    }

    private void updateConsoleToggleButtonLabel() {
        GuiButton button = findButtonById(BTN_TOGGLE_CONSOLE);
        if (button != null) {
            button.displayString = consoleVisible ? "§a控制台" : "§7控制台";
        }
    }

    private void drawConsolePanel() {
        if (!consoleVisible) {
            return;
        }
        Gui.drawRect(consoleX, consoleY, consoleX + consoleWidth, consoleY + consoleHeight, 0x77202A36);
        Gui.drawRect(consoleX, consoleY, consoleX + consoleWidth, consoleY + 1, 0xFF4E8BC9);
        Gui.drawRect(consoleX, consoleY + consoleHeight - 1, consoleX + consoleWidth, consoleY + consoleHeight,
                0xFF4E8BC9);
        this.drawString(this.fontRenderer, "运行控制台", consoleX + 6, consoleY + 6, 0xFFFFFFFF);

        List<String> lines = activeContext == null ? Collections.<String>emptyList() : activeContext.getConsoleLines();
        int lineHeight = this.fontRenderer.FONT_HEIGHT + 1;
        int textTop = consoleY + 20;
        int visibleHeight = consoleHeight - 26;
        int maxVisibleLines = Math.max(1, visibleHeight / lineHeight);
        int totalLines = lines.size();
        int start = Math.max(0, totalLines - maxVisibleLines - consoleScrollOffset);
        int end = Math.min(totalLines, start + maxVisibleLines);

        if (lines.isEmpty()) {
            this.drawString(this.fontRenderer, "暂无运行日志", consoleX + 8, textTop, 0xFFAAAAAA);
            return;
        }

        int y = textTop;
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            this.fontRenderer.drawSplitString(line, consoleX + 8, y, consoleWidth - 16, 0xFFD8E6F3);
            y += lineHeight;
        }
    }

    private void resetCanvasView() {
        canvasZoom = 1.0f;
        List<NodeNode> nodes = safeNodes();
        if (nodes.isEmpty()) {
            canvasOffsetX = 0;
            canvasOffsetY = 0;
            setStatus("已重置画布视图", 0xFF88FF88);
            return;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeNode node : nodes) {
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + NODE_WIDTH);
            maxY = Math.max(maxY, node.getY() + NODE_HEIGHT);
        }

        int contentCenterX = (minX + maxX) / 2;
        int contentCenterY = (minY + maxY) / 2;
        canvasOffsetX = canvasWidth / 2 - contentCenterX;
        canvasOffsetY = canvasHeight / 2 - contentCenterY;
        setStatus("已重置画布视图", 0xFF88FF88);
    }

    private void autoLayoutNodes() {
        if (currentGraph == null || safeNodes().isEmpty()) {
            setStatus("当前没有可自动排版的节点", 0xFFFFFF88);
            return;
        }

        pushUndoState();

        List<NodeNode> targetNodes = new ArrayList<>();
        if (!selectedNodeIds.isEmpty()) {
            for (NodeNode node : safeNodes()) {
                if (node != null && selectedNodeIds.contains(node.getId())) {
                    targetNodes.add(node);
                }
            }
        }
        if (targetNodes.isEmpty()) {
            targetNodes.addAll(safeNodes());
        }

        java.util.Set<String> targetIds = new java.util.LinkedHashSet<>();
        for (NodeNode node : targetNodes) {
            if (node != null) {
                targetIds.add(node.getId());
            }
        }

        java.util.Map<String, List<NodeEdge>> outgoing = new java.util.LinkedHashMap<>();
        java.util.Map<String, List<NodeEdge>> incoming = new java.util.LinkedHashMap<>();
        for (String id : targetIds) {
            outgoing.put(id, new ArrayList<NodeEdge>());
            incoming.put(id, new ArrayList<NodeEdge>());
        }
        for (NodeEdge edge : safeEdges()) {
            if (edge == null) {
                continue;
            }
            if (targetIds.contains(edge.getFromNodeId()) && targetIds.contains(edge.getToNodeId())) {
                outgoing.get(edge.getFromNodeId()).add(edge);
                incoming.get(edge.getToNodeId()).add(edge);
            }
        }

        List<NodeNode> startCandidates = new ArrayList<>();
        for (NodeNode node : targetNodes) {
            if (node == null) {
                continue;
            }
            if (NodeNode.TYPE_START.equals(node.getNormalizedType())) {
                startCandidates.add(node);
            }
        }
        if (startCandidates.isEmpty()) {
            for (NodeNode node : targetNodes) {
                if (node != null && incoming.get(node.getId()).isEmpty()) {
                    startCandidates.add(node);
                }
            }
        }
        if (startCandidates.isEmpty()) {
            startCandidates.add(targetNodes.get(0));
        }

        java.util.Map<String, Integer> layerMap = new java.util.LinkedHashMap<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        for (NodeNode start : startCandidates) {
            if (start == null) {
                continue;
            }
            layerMap.put(start.getId(), 0);
            queue.add(start.getId());
        }

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            int baseLayer = layerMap.getOrDefault(currentId, 0);
            List<NodeEdge> edges = outgoing.get(currentId);
            if (edges == null) {
                continue;
            }
            edges.sort(Comparator.comparingInt(this::getEdgeOrderWeight));
            for (NodeEdge edge : edges) {
                String nextId = edge.getToNodeId();
                NodeNode nextNode = findNodeById(nextId);
                if (nextNode == null || !targetIds.contains(nextId)) {
                    continue;
                }
                int nextLayer = baseLayer + 1;
                if (NodeNode.TYPE_JOIN.equals(nextNode.getNormalizedType())) {
                    int maxParentLayer = 0;
                    List<NodeEdge> parents = incoming.get(nextId);
                    if (parents != null) {
                        for (NodeEdge parent : parents) {
                            maxParentLayer = Math.max(maxParentLayer, layerMap.getOrDefault(parent.getFromNodeId(), 0));
                        }
                    }
                    nextLayer = Math.max(nextLayer, maxParentLayer + 1);
                }
                if (!layerMap.containsKey(nextId) || nextLayer > layerMap.get(nextId)) {
                    layerMap.put(nextId, nextLayer);
                    queue.add(nextId);
                }
            }
        }

        int fallbackLayer = 0;
        for (NodeNode node : targetNodes) {
            if (node == null) {
                continue;
            }
            if (!layerMap.containsKey(node.getId())) {
                layerMap.put(node.getId(), ++fallbackLayer);
            }
        }

        java.util.Map<Integer, List<NodeNode>> layers = new java.util.TreeMap<>();
        for (NodeNode node : targetNodes) {
            if (node == null) {
                continue;
            }
            int layer = layerMap.getOrDefault(node.getId(), 0);
            layers.computeIfAbsent(layer, k -> new ArrayList<NodeNode>()).add(node);
        }

        for (java.util.Map.Entry<Integer, List<NodeNode>> entry : layers.entrySet()) {
            List<NodeNode> nodesInLayer = entry.getValue();
            nodesInLayer.sort((a, b) -> {
                double ay = computeNodePriorityY(a, incoming, targetIds);
                double by = computeNodePriorityY(b, incoming, targetIds);
                int cmp = Double.compare(ay, by);
                if (cmp != 0) {
                    return cmp;
                }
                return safe(a.getId()).compareToIgnoreCase(safe(b.getId()));
            });
        }

        final int layerGapX = NODE_WIDTH + 72;
        final int layerGapY = NODE_HEIGHT + 26;
        final int baseX = 80;
        final int baseY = 80;

        for (java.util.Map.Entry<Integer, List<NodeNode>> entry : layers.entrySet()) {
            int layer = entry.getKey();
            List<NodeNode> nodesInLayer = entry.getValue();
            int layerX = baseX + layer * layerGapX;

            int totalHeight = Math.max(1, nodesInLayer.size()) * layerGapY;
            int startY = baseY - totalHeight / 2;

            for (int i = 0; i < nodesInLayer.size(); i++) {
                NodeNode node = nodesInLayer.get(i);
                int y = startY + i * layerGapY;

                if (NodeNode.TYPE_JOIN.equals(node.getNormalizedType())) {
                    List<NodeEdge> parents = incoming.get(node.getId());
                    if (parents != null && !parents.isEmpty()) {
                        int sumY = 0;
                        int count = 0;
                        for (NodeEdge parent : parents) {
                            NodeNode parentNode = findNodeById(parent.getFromNodeId());
                            if (parentNode != null && targetIds.contains(parentNode.getId())) {
                                sumY += parentNode.getY();
                                count++;
                            }
                        }
                        if (count > 0) {
                            y = sumY / count;
                        }
                    }
                }

                node.setX(layerX);
                node.setY(snapToGrid(y));
            }
        }

        if (!selectedNodeIds.isEmpty()) {
            setStatus("已重新整理选中节点", 0xFF88FF88);
        } else {
            setStatus("已执行自动布局（Start 分层 / 分支纵向 / Join 汇聚）", 0xFF88FF88);
        }
        saveDraftSilently();
    }

    private int getEdgeOrderWeight(NodeEdge edge) {
        if (edge == null) {
            return 99;
        }
        String port = normalize(edge.getFromPort());
        if (NodeNode.PORT_TRUE.equals(port)) {
            return 0;
        }
        if (NodeNode.PORT_NEXT.equals(port)) {
            return 1;
        }
        if (NodeNode.PORT_FALSE.equals(port)) {
            return 2;
        }
        if (NodeNode.PORT_TIMEOUT.equals(port)) {
            return 3;
        }
        return 10;
    }

    private double computeNodePriorityY(NodeNode node, java.util.Map<String, List<NodeEdge>> incoming,
            java.util.Set<String> targetIds) {
        if (node == null) {
            return 0;
        }
        List<NodeEdge> parents = incoming.get(node.getId());
        if (parents == null || parents.isEmpty()) {
            return node.getY();
        }
        double sum = 0;
        int count = 0;
        for (NodeEdge edge : parents) {
            NodeNode parent = findNodeById(edge.getFromNodeId());
            if (parent != null && targetIds.contains(parent.getId())) {
                sum += parent.getY() + getEdgeOrderWeight(edge) * 18;
                count++;
            }
        }
        return count <= 0 ? node.getY() : sum / count;
    }

    private void focusStartNode() {
        for (NodeNode node : safeNodes()) {
            if (node != null && NodeNode.TYPE_START.equals(node.getNormalizedType())) {
                canvasZoom = 1.0f;
                canvasOffsetX = canvasWidth / 2 - (node.getX() + NODE_WIDTH / 2);
                canvasOffsetY = canvasHeight / 2 - (node.getY() + NODE_HEIGHT / 2);
                selectedNode = node;
                propertyPanelVisible = true;
                selectedNodeIds.clear();
                selectedNodeIds.add(node.getId());
                updateSelectedNodeFields();
                setStatus("已定位到开始节点: " + node.getId(), 0xFF88FF88);
                return;
            }
        }
        setStatus("未找到开始节点", 0xFFFF8888);
    }

    private void selectNodesInBox() {
        boolean shiftDown = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LSHIFT)
                || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RSHIFT);
        if (!shiftDown) {
            selectedNodeIds.clear();
        }
        int left = screenToCanvasX(Math.min(selectionStartX, selectionEndX));
        int right = screenToCanvasX(Math.max(selectionStartX, selectionEndX));
        int top = screenToCanvasY(Math.min(selectionStartY, selectionEndY));
        int bottom = screenToCanvasY(Math.max(selectionStartY, selectionEndY));

        for (NodeNode node : safeNodes()) {
            if (node.getX() >= left && node.getX() + NODE_WIDTH <= right
                    && node.getY() >= top && node.getY() + NODE_HEIGHT <= bottom) {
                selectedNodeIds.add(node.getId());
                selectedNode = node;
            }
        }
        updateSelectedNodeFields();
    }

    private void drawMiniMap() {
        miniMapW = 110;
        miniMapH = 80;
        miniMapX = canvasX + canvasWidth - miniMapW - 8;
        if (propertyPanelVisible && selectedNode != null) {
            miniMapX = Math.max(canvasX + 8, propertyX - miniMapW - 10);
        }
        miniMapY = canvasY + 8;

        Gui.drawRect(miniMapX, miniMapY, miniMapX + miniMapW, miniMapY + miniMapH, 0x66111111);
        Gui.drawRect(miniMapX, miniMapY, miniMapX + miniMapW, miniMapY + 1, 0x884A6A88);
        Gui.drawRect(miniMapX, miniMapY + miniMapH - 1, miniMapX + miniMapW, miniMapY + miniMapH, 0x884A6A88);

        if (safeNodes().isEmpty()) {
            return;
        }

        int[] bounds = getGraphBounds();
        int minX = bounds[0];
        int minY = bounds[1];
        int maxX = bounds[2];
        int maxY = bounds[3];
        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);

        int innerX = miniMapX + 2;
        int innerY = miniMapY + 2;
        int innerW = miniMapW - 4;
        int innerH = miniMapH - 4;

        int viewLeft = screenToCanvasX(canvasX);
        int viewTop = screenToCanvasY(canvasY);
        int viewRight = screenToCanvasX(canvasX + canvasWidth);
        int viewBottom = screenToCanvasY(canvasY + canvasHeight);

        int rectX1 = innerX + Math.round((viewLeft - minX) * (innerW - 1) / (float) spanX);
        int rectY1 = innerY + Math.round((viewTop - minY) * (innerH - 1) / (float) spanY);
        int rectX2 = innerX + Math.round((viewRight - minX) * (innerW - 1) / (float) spanX);
        int rectY2 = innerY + Math.round((viewBottom - minY) * (innerH - 1) / (float) spanY);
        rectX1 = MathHelper.clamp(rectX1, innerX, innerX + innerW - 1);
        rectY1 = MathHelper.clamp(rectY1, innerY, innerY + innerH - 1);
        rectX2 = MathHelper.clamp(rectX2, innerX, innerX + innerW - 1);
        rectY2 = MathHelper.clamp(rectY2, innerY, innerY + innerH - 1);

        Gui.drawRect(Math.min(rectX1, rectX2), Math.min(rectY1, rectY2),
                Math.max(rectX1, rectX2) + 1, Math.max(rectY1, rectY2) + 1, 0x3339C5FF);
        Gui.drawRect(Math.min(rectX1, rectX2), Math.min(rectY1, rectY2),
                Math.max(rectX1, rectX2) + 1, Math.min(rectY1, rectY2) + 1, 0xFF66CCFF);

        for (NodeNode node : safeNodes()) {
            float u = (node.getX() - minX) / (float) spanX;
            float v = (node.getY() - minY) / (float) spanY;
            int x = innerX + Math.round(u * (innerW - 1));
            int y = innerY + Math.round(v * (innerH - 1));
            x = MathHelper.clamp(x, innerX, innerX + innerW - 4);
            y = MathHelper.clamp(y, innerY, innerY + innerH - 3);
            int color = selectedNodeIds.contains(node.getId()) ? 0xFFFFFF55 : getNodeColor(node.getNormalizedType());
            Gui.drawRect(x, y, x + 4, y + 3, color);
        }
    }

    private int[] getGraphBounds() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeNode node : safeNodes()) {
            if (node == null) {
                continue;
            }
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + NODE_WIDTH);
            maxY = Math.max(maxY, node.getY() + NODE_HEIGHT);
        }
        if (minX == Integer.MAX_VALUE) {
            return new int[] { 0, 0, 1, 1 };
        }
        return new int[] { minX, minY, maxX, maxY };
    }

    private boolean isInsideMiniMap(int mouseX, int mouseY) {
        return isInside(mouseX, mouseY, miniMapX, miniMapY, miniMapW, miniMapH);
    }

    private void centerCanvasToMiniMapPoint(int mouseX, int mouseY) {
        if (safeNodes().isEmpty()) {
            return;
        }
        int[] bounds = getGraphBounds();
        int minX = bounds[0];
        int minY = bounds[1];
        int maxX = bounds[2];
        int maxY = bounds[3];
        int spanX = Math.max(1, maxX - minX);
        int spanY = Math.max(1, maxY - minY);

        int innerX = miniMapX + 2;
        int innerY = miniMapY + 2;
        int innerW = Math.max(1, miniMapW - 4);
        int innerH = Math.max(1, miniMapH - 4);

        float u = (mouseX - innerX) / (float) (innerW - 1);
        float v = (mouseY - innerY) / (float) (innerH - 1);
        u = MathHelper.clamp(u, 0.0f, 1.0f);
        v = MathHelper.clamp(v, 0.0f, 1.0f);

        float targetCanvasX = minX + u * spanX;
        float targetCanvasY = minY + v * spanY;
        canvasOffsetX = Math.round(canvasWidth / 2.0f - targetCanvasX * canvasZoom);
        canvasOffsetY = Math.round(canvasHeight / 2.0f - targetCanvasY * canvasZoom);
        setStatus("已定位画布到小地图区域", 0xFF88FF88);
    }

    private void drawPopup(int mouseX, int mouseY) {
        int boxW = Math.min(320, this.width - 40);
        int boxH = 110;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;

        Gui.drawRect(0, 0, this.width, this.height, 0x88000000);
        ModernUiRenderer.drawPanel(boxX, boxY, boxW, boxH, 8,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawRoundedRect(boxX + 1, boxY + 1, Math.max(1, boxW - 2), 32, 7,
                ModernUiRenderer.SHELL_RAISED);
        ModernUiRenderer.drawText(this.fontRenderer, popupTitle, boxX + 12, boxY + 9,
                ModernUiRenderer.TEXT, Math.max(100, boxW - 52));
        ModernRuleEditorUi.drawInfoIcon(this.fontRenderer, this.width, this.height, boxX + boxW - 28, boxY + 10,
                "此提示用于说明节点编辑器中的当前操作。点击任意位置关闭。", mouseX, mouseY);
        ModernUiRenderer.drawDivider(boxX + 10, boxY + 32, Math.max(1, boxW - 20),
                ModernUiRenderer.BORDER_SUBTLE);
        this.fontRenderer.drawSplitString(popupMessage, boxX + 12, boxY + 44, boxW - 24, ModernUiRenderer.TEXT);
        ModernUiRenderer.drawText(this.fontRenderer, "点击任意位置关闭", boxX + 12, boxY + boxH - 18,
                ModernUiRenderer.MUTED_TEXT, Math.max(100, boxW - 24));
    }

    private void sendChat(String message) {
        if (Minecraft.getMinecraft().player != null && message != null && !message.isEmpty()) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString(message));
        }
    }

    private void saveDraftSilently() {
        if (currentGraph == null) {
            return;
        }
        NodeEditorDraftManager.saveDraft(currentGraph);
    }

    private String snapshotCurrentGraph() {
        return currentGraph == null ? "" : GSON.toJson(currentGraph);
    }

    private void pushUndoState() {
        if (currentGraph == null) {
            return;
        }
        undoStack.add(snapshotCurrentGraph());
        if (undoStack.size() > 50) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    private void restoreGraphSnapshot(String snapshot, boolean keepForRedo) {
        if (snapshot == null || snapshot.trim().isEmpty()) {
            return;
        }
        NodeGraph restored = GSON.fromJson(snapshot, NodeGraph.class);
        if (restored == null) {
            return;
        }
        if (keepForRedo && currentGraph != null) {
            redoStack.add(snapshotCurrentGraph());
            if (redoStack.size() > 50) {
                redoStack.remove(0);
            }
        }
        currentGraph = restored;
        if (currentGraphIndex >= 0 && currentGraphIndex < graphs.size()) {
            graphs.set(currentGraphIndex, currentGraph);
        }
        selectedNode = null;
        selectedNodeIds.clear();
        selectedEdgeId = null;
        propertyPanelVisible = false;
        updateSelectedNodeFields();
        saveDraftSilently();
    }

    private void undoAction() {
        if (undoStack.isEmpty()) {
            setStatus("没有可撤销的操作", 0xFFFFFF88);
            return;
        }
        String snapshot = undoStack.remove(undoStack.size() - 1);
        restoreGraphSnapshot(snapshot, true);
        setStatus("已撤销", 0xFF88FF88);
    }

    private void redoAction() {
        if (redoStack.isEmpty()) {
            setStatus("没有可重做的操作", 0xFFFFFF88);
            return;
        }
        if (currentGraph != null) {
            undoStack.add(snapshotCurrentGraph());
        }
        String snapshot = redoStack.remove(redoStack.size() - 1);
        NodeGraph restored = GSON.fromJson(snapshot, NodeGraph.class);
        if (restored != null) {
            currentGraph = restored;
            if (currentGraphIndex >= 0 && currentGraphIndex < graphs.size()) {
                graphs.set(currentGraphIndex, currentGraph);
            }
            selectedNode = null;
            selectedNodeIds.clear();
            selectedEdgeId = null;
            propertyPanelVisible = false;
            updateSelectedNodeFields();
            saveDraftSilently();
        }
        setStatus("已重做", 0xFF88FF88);
    }

    private void deleteSelectionOrEdge() {
        if (selectedEdgeId != null) {
            NodeEdge edge = findEdgeById(selectedEdgeId);
            if (edge != null && currentGraph != null) {
                pushUndoState();
                currentGraph.getEdges().remove(edge);
                selectedEdgeId = null;
                saveDraftSilently();
                setStatus("已删除连线", 0xFFFF8888);
                return;
            }
        }
        if (selectedNodeIds.isEmpty() && selectedNode != null) {
            selectedNodeIds.add(selectedNode.getId());
        }
        if (selectedNodeIds.isEmpty() || currentGraph == null) {
            setStatus("请先选择节点或连线", 0xFFFFFF88);
            return;
        }
        pushUndoState();
        List<String> ids = new ArrayList<>(selectedNodeIds);
        currentGraph.getNodes().removeIf(node -> node != null && ids.contains(node.getId()));
        currentGraph.getEdges().removeIf(edge -> edge != null
                && (ids.contains(edge.getFromNodeId()) || ids.contains(edge.getToNodeId())));
        selectedNode = null;
        selectedNodeIds.clear();
        propertyPanelVisible = false;
        updateSelectedNodeFields();
        saveDraftSilently();
        setStatus("已批量删除选中节点", 0xFFFF8888);
    }

    private void cutSelectionToClipboard() {
        int count = selectedNodeIds.isEmpty() && selectedNode != null ? 1 : selectedNodeIds.size();
        if (count <= 0) {
            if (selectedEdgeId != null) {
                deleteSelectionOrEdge();
                setStatus("已删除当前连线（暂不支持连线剪切板）", 0xFFFF8888);
                return;
            }
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }
        copySelectionToClipboard();
        deleteSelectionOrEdge();
        setStatus("已剪切 " + count + " 个节点", 0xFF88FF88);
    }

    private void selectAllNodes() {
        selectedNodeIds.clear();
        selectedEdgeId = null;
        for (NodeNode node : safeNodes()) {
            if (node == null || node.getId() == null) {
                continue;
            }
            selectedNodeIds.add(node.getId());
            if (selectedNode == null) {
                selectedNode = node;
            }
        }
        if (selectedNodeIds.isEmpty()) {
            selectedNode = null;
            propertyPanelVisible = false;
            setStatus("当前没有可全选的节点", 0xFFFFFF88);
            return;
        }
        propertyPanelVisible = selectedNode != null;
        setStatus("已全选 " + selectedNodeIds.size() + " 个节点", 0xFF88FF88);
    }

    private void copySelectionToClipboard() {
        clipboardNodes.clear();
        clipboardEdges.clear();
        if (selectedNodeIds.isEmpty() && selectedNode != null) {
            selectedNodeIds.add(selectedNode.getId());
        }
        if (selectedNodeIds.isEmpty()) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }
        for (NodeNode node : safeNodes()) {
            if (node != null && selectedNodeIds.contains(node.getId())) {
                clipboardNodes.add(GSON.fromJson(GSON.toJson(node), NodeNode.class));
            }
        }
        for (NodeEdge edge : safeEdges()) {
            if (edge != null && selectedNodeIds.contains(edge.getFromNodeId())
                    && selectedNodeIds.contains(edge.getToNodeId())) {
                clipboardEdges.add(GSON.fromJson(GSON.toJson(edge), NodeEdge.class));
            }
        }
        setStatus("已复制 " + clipboardNodes.size() + " 个节点", 0xFF88FF88);
    }

    private void pasteClipboard() {
        if (currentGraph == null || clipboardNodes.isEmpty()) {
            setStatus("剪贴板为空", 0xFFFFFF88);
            return;
        }
        pushUndoState();
        java.util.Map<String, String> idMap = new java.util.LinkedHashMap<>();
        selectedNodeIds.clear();
        for (NodeNode source : clipboardNodes) {
            NodeNode copy = GSON.fromJson(GSON.toJson(source), NodeNode.class);
            String oldId = copy.getId();
            String newId = createUniqueNodeId(copy.getNormalizedType());
            idMap.put(oldId, newId);
            copy.setId(newId);
            copy.setX(copy.getX() + 20);
            copy.setY(copy.getY() + 20);
            currentGraph.getNodes().add(copy);
            selectedNodeIds.add(newId);
            selectedNode = copy;
        }
        for (NodeEdge edge : clipboardEdges) {
            if (!idMap.containsKey(edge.getFromNodeId()) || !idMap.containsKey(edge.getToNodeId())) {
                continue;
            }
            NodeEdge copyEdge = GSON.fromJson(GSON.toJson(edge), NodeEdge.class);
            copyEdge.setId("edge_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            copyEdge.setFromNodeId(idMap.get(edge.getFromNodeId()));
            copyEdge.setToNodeId(idMap.get(edge.getToNodeId()));
            currentGraph.getEdges().add(copyEdge);
        }
        propertyPanelVisible = selectedNode != null;
        updateSelectedNodeFields();
        saveDraftSilently();
        setStatus("已粘贴 " + clipboardNodes.size() + " 个节点", 0xFF88FF88);
    }

    private void duplicateSelection() {
        copySelectionToClipboard();
        if (!clipboardNodes.isEmpty()) {
            pasteClipboard();
        }
    }

    private void focusSelectedNodes() {
        if (selectedNodeIds.isEmpty() && selectedNode != null) {
            selectedNodeIds.add(selectedNode.getId());
        }
        if (selectedNodeIds.isEmpty()) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeNode node : safeNodes()) {
            if (node != null && selectedNodeIds.contains(node.getId())) {
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
                maxX = Math.max(maxX, node.getX() + NODE_WIDTH);
                maxY = Math.max(maxY, node.getY() + NODE_HEIGHT);
            }
        }
        if (minX == Integer.MAX_VALUE) {
            setStatus("未找到选中节点", 0xFFFFFF88);
            return;
        }
        int centerX = (minX + maxX) / 2;
        int centerY = (minY + maxY) / 2;
        canvasOffsetX = Math.round(canvasWidth / 2.0f - centerX * canvasZoom);
        canvasOffsetY = Math.round(canvasHeight / 2.0f - centerY * canvasZoom);
        setStatus("已聚焦选中节点", 0xFF88FF88);
    }

    private void createCanvasNote() {
        if (currentGraph == null) {
            return;
        }
        NodeCanvasNote note = new NodeCanvasNote();
        note.setId("note_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        note.setX(screenToCanvasX(canvasX + canvasWidth / 2) - 90);
        note.setY(screenToCanvasY(canvasY + canvasHeight / 2) - 45);
        note.setTitle("注释");
        note.setContent("双击可编辑（后续可扩展）");
        currentGraph.getNotes().add(note);
        saveDraftSilently();
        setStatus("已创建注释框", 0xFF88FF88);
    }

    private void createGroupFromSelection() {
        if (currentGraph == null || selectedNodeIds.isEmpty()) {
            setStatus("请先选中节点后再创建分组框", 0xFFFFFF88);
            return;
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (NodeNode node : safeNodes()) {
            if (node != null && selectedNodeIds.contains(node.getId())) {
                minX = Math.min(minX, node.getX());
                minY = Math.min(minY, node.getY());
                maxX = Math.max(maxX, node.getX() + NODE_WIDTH);
                maxY = Math.max(maxY, node.getY() + NODE_HEIGHT);
            }
        }
        if (minX == Integer.MAX_VALUE) {
            setStatus("未找到可分组节点", 0xFFFF8888);
            return;
        }
        NodeGroupBox group = new NodeGroupBox();
        group.setId("group_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        group.setX(minX - 20);
        group.setY(minY - 24);
        group.setWidth((maxX - minX) + 40);
        group.setHeight((maxY - minY) + 48);
        group.setTitle("节点分组");
        group.setTag("group");
        currentGraph.getGroups().add(group);
        saveDraftSilently();
        setStatus("已创建节点分组框", 0xFF88FF88);
    }

    private void cycleSelectedNodeColorTag() {
        if (selectedNodeIds.isEmpty() && selectedNode != null) {
            selectedNodeIds.add(selectedNode.getId());
        }
        if (selectedNodeIds.isEmpty()) {
            setStatus("请先选择节点", 0xFFFFFF88);
            return;
        }
        String[] tags = new String[] { "", "red", "yellow", "green", "blue", "purple" };
        for (NodeNode node : safeNodes()) {
            if (node == null || !selectedNodeIds.contains(node.getId())) {
                continue;
            }
            String current = node.getColorTag();
            int idx = 0;
            for (int i = 0; i < tags.length; i++) {
                if (tags[i].equals(current)) {
                    idx = i;
                    break;
                }
            }
            node.setColorTag(tags[(idx + 1) % tags.length]);
        }
        saveDraftSilently();
        setStatus("已切换节点颜色标签", 0xFF88FF88);
    }

    private int getNodeRenderHeight(NodeNode node) {
        if (node == null) {
            return NODE_HEIGHT;
        }
        if (node.isMinimized()) {
            return 20;
        }
        if (node.isCollapsed()) {
            return 42;
        }

        int height = getNodeContentTopOffset(node) + 16;
        String subtitle = getNodeSubtitle(node);
        if (!subtitle.isEmpty()) {
            height += 10;
        }

        String summary = getNodeSummary(node);
        if (!summary.isEmpty()) {
            int summaryLines = 2;
            if (this.fontRenderer != null) {
                summaryLines = Math.max(1, Math.min(2,
                        this.fontRenderer.listFormattedStringToWidth(summary, Math.max(20, NODE_WIDTH - 12)).size()));
            }
            height += summaryLines * this.fontRenderer.FONT_HEIGHT + 4;
        }

        if (!getNodeKeyParamPreview(node).isEmpty()) {
            height += 18;
        }

        height += 16;
        return Math.max(NODE_HEIGHT, height);
    }

    private String getNodeIcon(String type) {
        String normalized = normalize(type);
        if (NodeNode.TYPE_START.equals(normalized)) {
            return "▶";
        }
        if (NodeNode.TYPE_ACTION.equals(normalized)) {
            return "⚙";
        }
        if (NodeNode.TYPE_IF.equals(normalized)) {
            return "?";
        }
        if (NodeNode.TYPE_SET_VAR.equals(normalized)) {
            return "V";
        }
        if (NodeNode.TYPE_WAIT_UNTIL.equals(normalized)) {
            return "⌛";
        }
        if (NodeNode.TYPE_END.equals(normalized)) {
            return "■";
        }
        if (NodeNode.TYPE_TRIGGER.equals(normalized)) {
            return "⚡";
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalized) || NodeNode.TYPE_SUBGRAPH.equals(normalized)) {
            return "◎";
        }
        if (NodeNode.TYPE_PARALLEL.equals(normalized)) {
            return "∥";
        }
        if (NodeNode.TYPE_JOIN.equals(normalized)) {
            return "↘";
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(normalized)) {
            return "⏱";
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "🔒";
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(normalized)) {
            return "▣";
        }
        return "•";
    }

    private String getNodeSubtitle(NodeNode node) {
        if (node == null) {
            return "";
        }
        String normalized = node.getNormalizedType();
        if (NodeNode.TYPE_ACTION.equals(normalized)) {
            return "执行动作 / 调用行为";
        }
        if (NodeNode.TYPE_IF.equals(normalized)) {
            return "条件判断 / 分支路由";
        }
        if (NodeNode.TYPE_SET_VAR.equals(normalized)) {
            return "变量写入 / 值更新";
        }
        if (NodeNode.TYPE_WAIT_UNTIL.equals(normalized)) {
            return "等待满足条件继续";
        }
        if (NodeNode.TYPE_TRIGGER.equals(normalized)) {
            return "事件进入点";
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalized) || NodeNode.TYPE_SUBGRAPH.equals(normalized)) {
            return "调用其他图";
        }
        if (NodeNode.TYPE_PARALLEL.equals(normalized)) {
            return "并行启动多个子图";
        }
        if (NodeNode.TYPE_JOIN.equals(normalized)) {
            return "汇聚等待多个分支";
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(normalized)) {
            return "延迟后继续执行";
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "尝试获取资源锁";
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(normalized)) {
            return "结果缓存读写";
        }
        if (NodeNode.TYPE_START.equals(normalized)) {
            return "流程入口";
        }
        if (NodeNode.TYPE_END.equals(normalized)) {
            return "流程终点";
        }
        return "";
    }

    private String getNodeSummary(NodeNode node) {
        if (node == null) {
            return "";
        }
        JsonObject data = node.getData() == null ? new JsonObject() : node.getData();
        String normalized = node.getNormalizedType();
        if (NodeNode.TYPE_TRIGGER.equals(normalized)) {
            return "触发: " + safe(readPreviewValue(data, "triggerType")) + " / 过滤: "
                    + safe(readPreviewValue(data, "contains"));
        }
        if (NodeNode.TYPE_ACTION.equals(normalized)) {
            JsonObject params = data.has("params") && data.get("params").isJsonObject() ? data.getAsJsonObject("params")
                    : new JsonObject();
            return "动作类型: " + safe(readPreviewValue(data, "actionType")) + "\n参数数: " + params.entrySet().size();
        }
        if (NodeNode.TYPE_IF.equals(normalized) || NodeNode.TYPE_WAIT_UNTIL.equals(normalized)) {
            String expression = safe(readPreviewValue(data, "expression"));
            if (!expression.isEmpty()) {
                return "高级表达式模式";
            }
            return safe(readPreviewValue(data, "leftVar")) + " "
                    + safe(readPreviewValue(data, "operator")) + " "
                    + safe(readPreviewValue(data, "right"));
        }
        if (NodeNode.TYPE_SET_VAR.equals(normalized)) {
            return "变量: " + safe(readPreviewValue(data, "name")) + " / 类型: "
                    + safe(readPreviewValue(data, "valueType"));
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalized) || NodeNode.TYPE_SUBGRAPH.equals(normalized)) {
            return "目标图: " + safe(readPreviewValue(data, "graphName"));
        }
        if (NodeNode.TYPE_PARALLEL.equals(normalized)) {
            JsonObject branches = data.has("branches") && data.get("branches").isJsonObject()
                    ? data.getAsJsonObject("branches")
                    : new JsonObject();
            return "并行分支数: " + branches.entrySet().size();
        }
        if (NodeNode.TYPE_JOIN.equals(normalized)) {
            return "joinKey=" + safe(readPreviewValue(data, "joinKey")) + " / required="
                    + safe(readPreviewValue(data, "required"));
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(normalized)) {
            return "delayTicks=" + safe(readPreviewValue(data, "delayTicks")) + " / delayMs="
                    + safe(readPreviewValue(data, "delayMs"));
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "资源: " + safe(readPreviewValue(data, "resourceKey"));
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(normalized)) {
            return "缓存: " + safe(readPreviewValue(data, "cacheKey"));
        }
        return NodeNode.getHelpText(normalized);
    }

    private String getNodeKeyParamPreview(NodeNode node) {
        if (node == null) {
            return "";
        }
        JsonObject data = node.getData() == null ? new JsonObject() : node.getData();
        String normalized = node.getNormalizedType();
        if (NodeNode.TYPE_ACTION.equals(normalized)) {
            JsonObject params = data.has("params") && data.get("params").isJsonObject() ? data.getAsJsonObject("params")
                    : new JsonObject();
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : params.entrySet()) {
                return entry.getKey() + "=" + previewJsonValue(entry.getValue());
            }
            return "点击编辑动作参数";
        }
        if (NodeNode.TYPE_TRIGGER.equals(normalized)) {
            return "throttleMs=" + safe(readPreviewValue(data, "throttleMs"));
        }
        if (NodeNode.TYPE_IF.equals(normalized) || NodeNode.TYPE_WAIT_UNTIL.equals(normalized)) {
            String expression = safe(readPreviewValue(data, "expression"));
            return expression.isEmpty() ? "mode=simple" : "mode=advanced";
        }
        if (NodeNode.TYPE_SET_VAR.equals(normalized)) {
            return "scope=" + safe(readPreviewValue(data, "scope"));
        }
        if (NodeNode.TYPE_RUN_SEQUENCE.equals(normalized) || NodeNode.TYPE_SUBGRAPH.equals(normalized)) {
            return "graph=" + safe(readPreviewValue(data, "graphName"));
        }
        if (NodeNode.TYPE_JOIN.equals(normalized)) {
            return "required=" + safe(readPreviewValue(data, "required"));
        }
        if (NodeNode.TYPE_DELAY_TASK.equals(normalized)) {
            String ticks = safe(readPreviewValue(data, "delayTicks"));
            return ticks.isEmpty() ? "delayMs=" + safe(readPreviewValue(data, "delayMs")) : "delayTicks=" + ticks;
        }
        if (NodeNode.TYPE_RESOURCE_LOCK.equals(normalized)) {
            return "lock=" + safe(readPreviewValue(data, "resourceKey"));
        }
        if (NodeNode.TYPE_RESULT_CACHE.equals(normalized)) {
            return "value=" + safe(readPreviewValue(data, "value"));
        }
        return "";
    }

    private String readPreviewValue(JsonObject data, String key) {
        if (data == null || key == null || !data.has(key) || data.get(key).isJsonNull()) {
            return "";
        }
        return previewJsonValue(data.get(key));
    }

    private String previewJsonValue(com.google.gson.JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return "";
        }
        String text = value.isJsonPrimitive() ? value.getAsString() : value.toString();
        return text.length() > 28 ? text.substring(0, 28) + "..." : text;
    }

    private String buildRuntimePanelText(NodeExecutionContext context) {
        StringBuilder runtimeText = new StringBuilder();
        runtimeText.append("当前节点: ").append(safe(context.getCurrentNodeId())).append("\n");
        runtimeText.append("上一个节点: ").append(safe(context.getPreviousNodeId())).append("\n");
        runtimeText.append("下一跳分支: ").append(safe(context.getLastBranch())).append("\n");
        runtimeText.append("当前运行图名称: ").append(safeValue(context.getVariable("graphName"))).append("\n");
        runtimeText.append("当前触发来源: ").append(safeValue(context.getVariable("triggerSource"))).append("\n");
        runtimeText.append("运行状态: ").append(resolveRuntimeStatus(context)).append("\n");
        runtimeText.append("运行耗时: ").append(formatDuration(context.getStartTimeMs() <= 0L
                ? 0L
                : Math.max(0L, System.currentTimeMillis() - context.getStartTimeMs()))).append("\n");
        runtimeText.append("步数统计: ").append(context.getExecutedSteps()).append(" / ")
                .append(context.getMaxSteps()).append("\n");
        runtimeText.append("当前变量快照: ").append(buildVariableSnapshot(context)).append("\n");

        String parallelStatus = buildParallelStatus(context);
        if (!parallelStatus.isEmpty()) {
            runtimeText.append("并行分支状态:\n").append(parallelStatus).append("\n");
        }

        String joinStatus = buildJoinStatus(context);
        if (!joinStatus.isEmpty()) {
            runtimeText.append("Join 汇聚状态:\n").append(joinStatus).append("\n");
        }

        List<NodeExecutionContext.ExecutionRecord> history = context.getHistory();
        int start = Math.max(0, history.size() - 4);
        runtimeText.append("历史:");
        for (int i = start; i < history.size(); i++) {
            NodeExecutionContext.ExecutionRecord record = history.get(i);
            runtimeText.append("\n- ").append(record.getNodeId());
            if (!record.getBranch().isEmpty()) {
                runtimeText.append(" [").append(record.getBranch()).append("]");
            }
            if (!record.getMessage().isEmpty()) {
                runtimeText.append(" ").append(record.getMessage());
            }
        }
        return runtimeText.toString();
    }

    private String buildVariableSnapshot(NodeExecutionContext context) {
        if (context == null || context.getVariables().isEmpty()) {
            return "(空)";
        }
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (java.util.Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
            if (count >= 6) {
                sb.append(" ...");
                break;
            }
            if (count > 0) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(safeValue(entry.getValue()));
            count++;
        }
        return sb.length() <= 0 ? "(空)" : sb.toString();
    }

    private String buildParallelStatus(NodeExecutionContext context) {
        if (context == null || context.getScopedVariables().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, java.util.Map<String, Object>> scopeEntry : context.getScopedVariables()
                .entrySet()) {
            if (!scopeEntry.getKey().startsWith("parallel:")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(scopeEntry.getKey()).append(":");
            for (java.util.Map.Entry<String, Object> entry : scopeEntry.getValue().entrySet()) {
                sb.append("\n  - ").append(entry.getKey()).append(": ").append(safeValue(entry.getValue()));
            }
        }
        return sb.toString();
    }

    private String buildJoinStatus(NodeExecutionContext context) {
        if (context == null || context.getScopedVariables().isEmpty()) {
            return "";
        }
        java.util.Map<String, Object> joinScope = context.getScopedVariables().get("join");
        if (joinScope == null || joinScope.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Object> entry : joinScope.entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("- ").append(entry.getKey()).append(": ").append(safeValue(entry.getValue()));
        }
        return sb.toString();
    }

    private String buildBreakpointPanelText(NodeExecutionContext context) {
        if (context == null || context.getBreakpoints().isEmpty()) {
            return "当前无断点\n可右键节点或使用“切换当前节点断点”按钮添加。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("共 ").append(context.getBreakpoints().size()).append(" 个断点");
        for (String nodeId : context.getBreakpoints()) {
            sb.append("\n- ").append(safe(nodeId));
            if (selectedNode != null && safe(selectedNode.getId()).equals(safe(nodeId))) {
                sb.append("  (当前选中)");
            }
        }
        sb.append("\n\n可右键节点切换断点，或使用“清空全部断点”统一移除。");
        return sb.toString();
    }

    private void openWorkflowContextMenu(int mouseX, int mouseY, WorkflowVisibleRow row) {
        workflowContextTargetRow = row;
        workflowContextMenuX = mouseX;
        workflowContextMenuY = mouseY;
        workflowContextActions.clear();

        boolean hasTarget = row != null && row.graph != null;
        workflowContextActions.add(new WorkflowContextAction("new", "新建工作流 (Ctrl+N)", true));
        workflowContextActions.add(new WorkflowContextAction("rename", "重命名 (F2)", hasTarget));
        workflowContextActions.add(new WorkflowContextAction("copy", "复制 (Ctrl+C)", hasTarget));
        workflowContextActions.add(new WorkflowContextAction("paste", "粘贴 (Ctrl+V)", workflowClipboardGraph != null));
        workflowContextActions.add(new WorkflowContextAction("cut", "剪切 (Ctrl+X)", hasTarget));
        workflowContextActions.add(new WorkflowContextAction("delete", "删除 (Del)", hasTarget));

        int menuWidth = getWorkflowContextMenuWidth();
        int menuHeight = getWorkflowContextMenuHeight();
        workflowContextMenuX = Math.max(toolbarTreeX, Math.min(mouseX, toolbarTreeX + toolbarTreeWidth - menuWidth));
        workflowContextMenuY = Math.max(toolbarTreeY, Math.min(mouseY, toolbarTreeY + toolbarTreeHeight - menuHeight));
        workflowContextMenuVisible = true;
    }

    private int getWorkflowContextMenuWidth() {
        return 152;
    }

    private int getWorkflowContextMenuHeight() {
        return workflowContextActions.size() * 18 + 4;
    }

    private void drawWorkflowContextMenu(int mouseX, int mouseY) {
        if (!workflowContextMenuVisible || workflowContextActions.isEmpty()) {
            return;
        }
        int width = getWorkflowContextMenuWidth();
        int itemHeight = 18;
        int height = getWorkflowContextMenuHeight();
        Gui.drawRect(workflowContextMenuX, workflowContextMenuY, workflowContextMenuX + width,
                workflowContextMenuY + height,
                0xEE1C2330);
        Gui.drawRect(workflowContextMenuX, workflowContextMenuY, workflowContextMenuX + width, workflowContextMenuY + 1,
                0xFF5E88B8);
        Gui.drawRect(workflowContextMenuX, workflowContextMenuY + height - 1, workflowContextMenuX + width,
                workflowContextMenuY + height, 0xFF5E88B8);

        for (int i = 0; i < workflowContextActions.size(); i++) {
            WorkflowContextAction action = workflowContextActions.get(i);
            int y = workflowContextMenuY + 2 + i * itemHeight;
            boolean hovered = isInside(mouseX, mouseY, workflowContextMenuX + 2, y, width - 4, itemHeight - 1);
            int bg = hovered ? 0x445A86AA : 0x22223344;
            Gui.drawRect(workflowContextMenuX + 2, y, workflowContextMenuX + width - 2, y + itemHeight - 1, bg);
            this.drawString(this.fontRenderer, action.label, workflowContextMenuX + 6, y + 5,
                    action.enabled ? 0xFFFFFFFF : 0xFF777777);
        }
    }

    private boolean handleWorkflowContextMenuClick(int mouseX, int mouseY) {
        if (!workflowContextMenuVisible || workflowContextActions.isEmpty()) {
            return false;
        }
        int width = getWorkflowContextMenuWidth();
        int itemHeight = 18;
        int height = getWorkflowContextMenuHeight();
        if (!isInside(mouseX, mouseY, workflowContextMenuX, workflowContextMenuY, width, height)) {
            return false;
        }

        int index = (mouseY - workflowContextMenuY - 2) / itemHeight;
        if (index < 0 || index >= workflowContextActions.size()) {
            return true;
        }

        WorkflowContextAction action = workflowContextActions.get(index);
        if (!action.enabled) {
            workflowContextMenuVisible = false;
            return true;
        }

        NodeGraph targetGraph = workflowContextTargetRow == null ? null : workflowContextTargetRow.graph;
        workflowContextMenuVisible = false;

        if ("new".equals(action.id)) {
            createNewWorkflow();
            return true;
        }
        if ("rename".equals(action.id)) {
            renameWorkflow(targetGraph);
            return true;
        }
        if ("copy".equals(action.id)) {
            duplicateWorkflow(targetGraph);
            return true;
        }
        if ("paste".equals(action.id)) {
            pasteWorkflowFromClipboard();
            return true;
        }
        if ("cut".equals(action.id)) {
            cutWorkflow(targetGraph);
            return true;
        }
        if ("delete".equals(action.id)) {
            deleteWorkflow(targetGraph);
            return true;
        }
        return true;
    }

    private String resolveRuntimeStatus(NodeExecutionContext context) {
        if (context == null) {
            return "未运行";
        }
        if (context.hasError()) {
            return "失败";
        }
        if (context.isCompleted()) {
            return "完成";
        }
        if (context.isPausedAtBreakpoint()) {
            return "命中断点暂停";
        }
        if (context.isPaused()) {
            return "暂停";
        }
        if (context.isWaiting()) {
            return "等待";
        }
        return "运行中";
    }

    private String formatDuration(long durationMs) {
        if (durationMs <= 0L) {
            return "0ms";
        }
        if (durationMs < 1000L) {
            return durationMs + "ms";
        }
        long seconds = durationMs / 1000L;
        long millis = durationMs % 1000L;
        if (seconds < 60L) {
            return seconds + "." + String.format(Locale.ROOT, "%03d", millis) + "s";
        }
        long minutes = seconds / 60L;
        long remainSeconds = seconds % 60L;
        return minutes + "m " + remainSeconds + "s";
    }

    private String safeValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return text.length() > 72 ? text.substring(0, 72) + "..." : text;
    }

    private String getDisplayNodeName(String type) {
        String normalized = normalize(type);
        String displayName = NodeNode.getDisplayName(normalized);
        return displayName == null || displayName.trim().isEmpty() ? normalized : displayName;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void onGuiClosed() {
        saveLibraryPanelState();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return !GuiPathingPolicy.shouldKeepPathingDuringGui(this.mc);
    }
}

