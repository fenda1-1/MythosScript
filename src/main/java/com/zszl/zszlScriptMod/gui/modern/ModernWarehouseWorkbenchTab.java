package com.zszl.zszlScriptMod.gui.modern;

import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.lwjgl.input.Keyboard;

import com.google.gson.Gson;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernTreeGuide;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;
import com.zszl.zszlScriptMod.gui.modern.form.ModernPathSequencePicker;
import com.zszl.zszlScriptMod.gui.modern.rules.AutoFollowAreaPicker;
import com.zszl.zszlScriptMod.handlers.GoToAndOpenHandler;
import com.zszl.zszlScriptMod.handlers.SortingManager;
import com.zszl.zszlScriptMod.handlers.WarehouseEventHandler;
import com.zszl.zszlScriptMod.handlers.WarehouseManager;
import com.zszl.zszlScriptMod.system.dungeon.ChestData;
import com.zszl.zszlScriptMod.system.dungeon.SortingRule;
import com.zszl.zszlScriptMod.system.dungeon.Warehouse;
import com.zszl.zszlScriptMod.utils.ModUtils;
import com.zszl.zszlScriptMod.utils.PinyinSearchHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.components.ModernTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.ItemShulkerBox;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;

/**
 * Native list-detail workbench for the smart warehouse manager.
 *
 * <p>The workbench keeps a deep copy of the manager data until the user saves.
 * Runtime actions still go through the existing handlers, while no legacy GUI
 * is needed for editing regions, chest records, policies, or sorting rules.</p>
 */
public final class ModernWarehouseWorkbenchTab implements ModernSettingsTab {
    private final ModernNavigationActions navigationActions = new ModernNavigationActions("warehouse");

    private static final Gson GSON = new Gson();

    private static final int TOP_INSET = 8;
    private static final int FOOTER_HEIGHT = 32;
    private static final int NAV_FOOTER_HEIGHT = 50;
    private static final int NAV_ROW_HEIGHT = 25;
    private static final int CHEST_ROW_HEIGHT = 25;
    private static final int RULE_ROW_HEIGHT = 25;
    private static final int MAX_TEXT_LENGTH = 4096;

    private static final String CATEGORY_ALL = "__all__";
    private static final String CATEGORY_DEFAULT = "默认";

    private enum View {
        REGION("gui.modern.warehouse.u002"),
        CHESTS("gui.modern.warehouse.u003"),
        INVENTORY("gui.modern.warehouse.u004"),
        POLICY("gui.modern.warehouse.u005"),
        SORTING("gui.modern.warehouse.u006");

        private final String label;

        View(String label) {
            this.label = label;
        }
    }

    private enum DialogMode {
        NONE,
        ADD_CATEGORY,
        RENAME_CATEGORY
    }

    private enum ConfirmationMode {
        NONE,
        DELETE_WAREHOUSE,
        DELETE_CATEGORY,
        DELETE_CHEST,
        DELETE_RULE,
        CLEAR_DESIGNATED,
        RELOAD
    }

    private static final class LeftHit {
        private final int type;
        private final String category;
        private final Warehouse warehouse;
        private final ModernMainLayout.Rect bounds;

        private LeftHit(int type, String category, Warehouse warehouse, ModernMainLayout.Rect bounds) {
            this.type = type;
            this.category = category == null ? "" : category;
            this.warehouse = warehouse;
            this.bounds = bounds;
        }
    }

    private static final class ChestHit {
        private final int index;
        private final ChestData chest;
        private final ModernMainLayout.Rect bounds;

        private ChestHit(int index, ChestData chest, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.chest = chest;
            this.bounds = bounds;
        }
    }

    private static final class RuleHit {
        private final int index;
        private final ModernMainLayout.Rect bounds;

        private RuleHit(int index, ModernMainLayout.Rect bounds) {
            this.index = index;
            this.bounds = bounds;
        }
    }

    private static final class FieldHit {
        private final ModernTextField field;
        private final ModernMainLayout.Rect bounds;

        private FieldHit(ModernTextField field, ModernMainLayout.Rect bounds) {
            this.field = field;
            this.bounds = bounds;
        }
    }

    private static final class TooltipHit {
        private final ModernMainLayout.Rect bounds;
        private final String text;

        private TooltipHit(ModernMainLayout.Rect bounds, String text) {
            this.bounds = bounds;
            this.text = text == null ? "" : text;
        }
    }

    private static final class MenuEntry {
        private final String key;
        private final String label;

        private MenuEntry(String key, String label) {
            this.key = key;
            this.label = label;
        }
    }

    private static final class InventoryEntry {
        private final String key;
        private final String name;
        private final ItemStack sample;
        private int count;
        private final List<Integer> slots = new ArrayList<>();

        private InventoryEntry(String key, String name, ItemStack sample) {
            this.key = key;
            this.name = name;
            this.sample = sample;
        }
    }

    private final List<Warehouse> warehouses = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private final List<Warehouse> committedWarehouses = new ArrayList<>();
    private final List<String> committedCategories = new ArrayList<>();
    private final IdentityHashMap<Warehouse, Warehouse> committedSourceByDraft = new IdentityHashMap<>();
    private final List<Warehouse> originalSourceWarehouses = new ArrayList<>();
    private final IdentityHashMap<Warehouse, Warehouse> sourceByDraft = new IdentityHashMap<>();
    private final Set<String> collapsedCategories = new HashSet<>();

    private final List<LeftHit> leftHits = new ArrayList<>();
    private final List<ChestHit> chestHits = new ArrayList<>();
    private final Map<Integer, ModernMainLayout.Rect> chestDeleteBounds = new LinkedHashMap<>();
    private final List<RuleHit> ruleHits = new ArrayList<>();
    private final List<FieldHit> fieldHits = new ArrayList<>();
    private final List<TooltipHit> tooltipHits = new ArrayList<>();
    private final List<MenuEntry> contextMenu = new ArrayList<>();
    private final Map<String, ModernMainLayout.Rect> actionBounds = new LinkedHashMap<>();
    private final Map<String, Boolean> actionEnabled = new LinkedHashMap<>();
    private final Map<String, String> actionTooltips = new LinkedHashMap<>();

    private Warehouse selectedWarehouse;
    private Warehouse loadedRegionOwner;
    private ChestData loadedPolicyOwner;
    private ModernTextField spreadNamesField;
    private ModernTextField postDepositSequenceField;
    private ModernPathSequencePicker postDepositSequencePicker;
    private boolean loadingPolicyFields;
    private boolean policyItemsEdited;
    private boolean policyItemsClearRequested;
    private boolean depositSlotsExpanded;
    private SortingRule loadedRuleOwner;
    private String selectedCategory = CATEGORY_ALL;
    private int selectedChestIndex = -1;
    private int selectedRuleIndex = -1;
    private View view = View.REGION;
    private final RuleSectionNavigation sectionNavigation = new RuleSectionNavigation(new String[] {
        "gui.modern.warehouse.u002", "gui.modern.warehouse.u003", "gui.modern.warehouse.u004",
        "gui.modern.warehouse.u005", "gui.modern.warehouse.u006"}, new ModernUiRenderer.Icon[] {
        ModernUiRenderer.Icon.ROUTE, ModernUiRenderer.Icon.STORAGE, ModernUiRenderer.Icon.PICKUP,
        ModernUiRenderer.Icon.SETTINGS, ModernUiRenderer.Icon.CONDITIONS});
    private final Map<Warehouse, int[]> viewPositions = new java.util.WeakHashMap<>();

    private ModernTextField searchField;
    private ModernTextField nameField;
    private ModernTextField categoryField;
    private ModernTextField x1Field;
    private ModernTextField z1Field;
    private ModernTextField x2Field;
    private ModernTextField z2Field;
    private ModernTextField chestSearchField;
    private ModernTextField inventorySearchField;
    private ModernTextField designatedField;
    private ModernTextField ruleNameField;
    private ModernTextField ruleKeywordsField;
    private ModernTextField dialogField;

    private ModernMainLayout.Rect bounds;
    private ModernMainLayout.Rect navigationBounds;
    private ModernMainLayout.Rect editorBounds;
    private ModernMainLayout.Rect navigationSearchBounds;
    private ModernMainLayout.Rect navigationViewport;
    private ModernMainLayout.Rect rightViewport;
    private ModernMainLayout.Rect chestListViewport;
    private ModernMainLayout.Rect inventoryViewport;
    private ModernMainLayout.Rect ruleListViewport;
    private ModernMainLayout.Rect splitDividerBounds;
    private final ModernHoverScrollbar navigationScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar rightScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar chestInnerScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar inventoryInnerScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar ruleInnerScrollbar = new ModernHoverScrollbar();
    private ModernMainLayout.Rect slotGridBounds;
    private ModernMainLayout.Rect depositSlotGridBounds;
    private ModernMainLayout.Rect dialogBounds;
    private ModernMainLayout.Rect dialogConfirmBounds;
    private ModernMainLayout.Rect dialogCancelBounds;
    private ModernMainLayout.Rect confirmationBounds;
    private ModernMainLayout.Rect confirmationYesBounds;
    private ModernMainLayout.Rect confirmationNoBounds;

    private ModernMainLayout.Rect navigationNewBounds;
    private ModernMainLayout.Rect navigationDuplicateBounds;
    private ModernMainLayout.Rect navigationCategoryBounds;
    private ModernMainLayout.Rect categoryChoiceBounds;
    private ModernMainLayout.Rect navigationDeleteBounds;
    private ModernMainLayout.Rect saveBounds;
    private ModernMainLayout.Rect revertBounds;
    private ModernMainLayout.Rect reloadBounds;

    private double navigationRatio = 0.28D;
    private int navigationScroll;
    private int navigationMaxScroll;
    private int rightScroll;
    private int rightMaxScroll;
    private int chestListScroll;
    private int chestListMaxScroll;
    private int inventoryScroll;
    private int inventoryMaxScroll;
    private int ruleListScroll;
    private int ruleListMaxScroll;
    private int slotCellSize;
    private int slotGap;
    private int slotDragStart = -1;
    private int depositSlotCellSize;
    private int depositSlotGap;
    private int depositSlotDragStart = -1;

    private boolean initialized;
    private boolean draggingDivider;
    private boolean draggingSlots;
    private boolean draggingDepositSlots;
    private boolean depositSlotDragAddMode;
    private boolean withdrawShiftQuickMove = true;
    private DialogMode dialogMode = DialogMode.NONE;
    private ConfirmationMode confirmationMode = ConfirmationMode.NONE;
    private Warehouse confirmationWarehouse;
    private ChestData confirmationChest;
    private SortingRule confirmationRule;
    private String confirmationCategory = "";
    private String contextCategory = "";
    private Warehouse contextWarehouse;
    private ModernMainLayout.Rect contextBounds;
    private String statusMessage = "";
    private int statusColor = ModernUiRenderer.SUBTLE_TEXT;
    private String hoveredTooltip = "";
    private String committedFingerprint = "";
    private boolean dirtyForFrame;
    private int lastMouseX;
    private int lastMouseY;
    /** The field focused before the render-only hide/show pass. */
    private ModernTextField focusedFieldBeforeLayoutReset;

    public ModernWarehouseWorkbenchTab() {
    }

    @Override
    public void ensureInitialized(FontRenderer fontRenderer) {
        if (initialized) {
            return;
        }
        navigationRatio = MainUiLayoutManager.getModernSplitRatio("warehouse.navigation", navigationRatio);
        searchField = createField(fontRenderer, 120);
        nameField = createField(fontRenderer, 256);
        categoryField = createField(fontRenderer, 128);
        x1Field = createField(fontRenderer, 32);
        z1Field = createField(fontRenderer, 32);
        x2Field = createField(fontRenderer, 32);
        z2Field = createField(fontRenderer, 32);
        chestSearchField = createField(fontRenderer, 128);
        inventorySearchField = createField(fontRenderer, 128);
        designatedField = createField(fontRenderer, MAX_TEXT_LENGTH);
        designatedField.setChangeListener(value -> {
            if (!loadingPolicyFields) {
                policyItemsEdited = true;
            }
        });
        spreadNamesField = createField(fontRenderer, MAX_TEXT_LENGTH);
        postDepositSequenceField = createField(fontRenderer, MAX_TEXT_LENGTH);
        postDepositSequencePicker = new ModernPathSequencePicker(name -> {
            ChestData chest = selectedChest();
            if (chest != null) {
                chest.postDepositSequence = name == null ? "" : name.trim();
            }
        });
        postDepositSequencePicker.ensureInitialized(fontRenderer);
        ruleNameField = createField(fontRenderer, 256);
        ruleKeywordsField = createField(fontRenderer, MAX_TEXT_LENGTH);
        dialogField = createField(fontRenderer, 128);
        loadDraftFromSource(null, null);
        initialized = true;
    }

    @Override
    public void updateScreen() {
        updateCursor(searchField);
        updateCursor(nameField);
        updateCursor(categoryField);
        updateCursor(x1Field);
        updateCursor(z1Field);
        updateCursor(x2Field);
        updateCursor(z2Field);
        updateCursor(chestSearchField);
        updateCursor(inventorySearchField);
        updateCursor(designatedField);
        updateCursor(ruleNameField);
        updateCursor(ruleKeywordsField);
        updateCursor(dialogField);
        if (postDepositSequencePicker != null) {
            postDepositSequencePicker.updateScreen();
        }
    }

    @Override
    public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect requestedBounds, int mouseX, int mouseY) {
        ensureInitialized(fontRenderer);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        hoveredTooltip = "";
        leftHits.clear();
        chestHits.clear();
        chestDeleteBounds.clear();
        ruleHits.clear();
        fieldHits.clear();
        tooltipHits.clear();
        actionBounds.clear();
        actionEnabled.clear();
        actionTooltips.clear();
        // These bounds are page-local. Clear them before drawing the current page
        // so an old region category control cannot intercept clicks on another page.
        categoryChoiceBounds = null;
        slotGridBounds = null;
        depositSlotGridBounds = null;
        focusedFieldBeforeLayoutReset = findFocusedField();
        hideFields();
        dirtyForFrame = isDirty();

        bounds = inset(requestedBounds, 6);
        ModernUiRenderer.drawPanel(bounds.x, bounds.y, bounds.width, bounds.height, 7,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.beginClip(bounds);
        try {
        int bodyHeight = Math.max(1, bounds.height - TOP_INSET - FOOTER_HEIGHT - 6);
        int splitTotal = Math.max(2, bounds.width - 20);
        ModernSplitPane.Split split = ModernSplitPane.calculate(splitTotal, navigationRatio, 150, 280, 82, 156);
        navigationRatio = split.ratio;
        int bodyY = bounds.y + TOP_INSET;
        navigationBounds = new ModernMainLayout.Rect(bounds.x + 8, bodyY, split.firstWidth, bodyHeight);
        splitDividerBounds = ModernSplitPane.verticalDividerBounds(navigationBounds.x, navigationBounds.width, 0,
                bodyY, bodyHeight);
        editorBounds = new ModernMainLayout.Rect(splitDividerBounds.right(), bodyY,
                Math.max(1, bounds.right() - splitDividerBounds.right() - 8), bodyHeight);

        drawNavigation(fontRenderer, mouseX, mouseY);
        drawEditor(fontRenderer, mouseX, mouseY);
        ModernSplitPane.drawVerticalDivider(splitDividerBounds, mouseX, mouseY, draggingDivider);
        drawFooter(fontRenderer, mouseX, mouseY);

        navigationActions.drawOverlay(mouseX, mouseY);
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            postDepositSequencePicker.draw(fontRenderer, bounds,
                    "gui.modern.warehouse.post_sequence", mouseX, mouseY);
        }
        if (contextBounds != null) {
            drawContextMenu(fontRenderer, mouseX, mouseY);
        }
        if (confirmationMode != ConfirmationMode.NONE) {
            drawConfirmation(fontRenderer, mouseX, mouseY);
        }
        if (dialogMode != DialogMode.NONE) {
            drawInputDialog(fontRenderer, mouseX, mouseY);
        }
        } finally {
        ModernUiRenderer.endClip();
        }
        focusedFieldBeforeLayoutReset = null;
    }


    private void configureNavigationActions() {
        navigationActions.action("move", "gui.modern.nav.move", false, false, () -> selectedWarehouse != null,
                () -> { if (applyCurrentViewFields() && selectedWarehouse != null) navigationActions.choose(
                        "移动到分类", categories, selectedWarehouse.category, value -> {
                            selectedWarehouse.category = value;
                            selectedCategory = value;
                            resetSelectionFields();
                        }); });
        navigationActions.action("category_add", "gui.modern.nav.category_add", true, false, () -> true, () -> openCategoryInput(DialogMode.ADD_CATEGORY, ""));
        navigationActions.action("category_rename", "gui.modern.nav.category_rename", false, false, () -> isConcreteCategory(selectedCategory), () -> openCategoryInput(DialogMode.RENAME_CATEGORY, selectedCategory));
        navigationActions.action("category_delete", "gui.modern.nav.category_delete", false, true, () -> isConcreteCategory(selectedCategory), () -> requestDeleteCategory(selectedCategory));
        navigationActions.action("add", "gui.modern.nav.add", true, false, () -> true, this::addWarehouse);
        navigationActions.action("copy", "gui.modern.nav.copy", true, false, () -> selectedWarehouse != null, this::duplicateWarehouse);
        navigationActions.action("delete", "gui.modern.nav.delete", true, true, () -> selectedWarehouse != null || isConcreteCategory(selectedCategory), this::requestDeleteCurrent);
        navigationActions.action("expand", "gui.modern.nav.expand", false, false, () -> true, () -> collapsedCategories.clear());
        navigationActions.action("collapse", "gui.modern.nav.collapse", false, false, () -> true, () -> collapsedCategories.addAll(categories));
        navigationActions.action("rename", "gui.modern.nav.rename", true, false, () -> selectedWarehouse != null, () -> { if (applyCurrentViewFields() && selectedWarehouse != null) navigationActions.prompt("gui.modern.nav.rename", selectedWarehouse.name, value -> { selectedWarehouse.name = value; resetSelectionFields(); }); });
        navigationActions.action("category_manage", "gui.modern.nav.category_manage", true, false, () -> true, navigationActions::manageCategories);
    }

    private boolean navigationContext(int mouseX, int mouseY) {
        if (!navigationActions.inTree(mouseX, mouseY)) return false;
        if (!applyCurrentViewFields()) return true;
        searchField.setFocused(false);
        for (LeftHit hit : leftHits) if (hit.bounds.contains(mouseX, mouseY)) {
            if (hit.type == 2) {
                if (selectedWarehouse != hit.warehouse) selectWarehouse(hit.warehouse);
                navigationActions.context(mouseX, mouseY, "add", "copy", "rename", "move", "delete");
            } else if (hit.type == 1) {
                selectedCategory = hit.category;
                navigationActions.action("fold", "gui.modern.nav.fold", false, false, () -> true,
                        () -> { if (!collapsedCategories.add(hit.category)) collapsedCategories.remove(hit.category); });
                navigationActions.context(mouseX, mouseY, "add", "category_add", "category_rename", "fold", "category_delete");
            } else navigationActions.context(mouseX, mouseY, "add", "category_add", "expand", "collapse");
            return true;
        }
        navigationActions.context(mouseX, mouseY, "add", "category_add", "expand", "collapse");
        return true;
    }

    private void drawNavigation(FontRenderer fontRenderer, int mouseX, int mouseY) {
        configureNavigationActions();
        navigationActions.begin(fontRenderer, navigationBounds);
        ModernUiRenderer.drawSubtlePanel(navigationBounds.x, navigationBounds.y, navigationBounds.width,
                navigationBounds.height, 6, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.beginClip(navigationBounds);
        ModernMainLayout.Rect searchBounds = new ModernMainLayout.Rect(navigationBounds.x + 8,
                navigationBounds.y + 30, Math.max(1, navigationBounds.width - 16), 20);
        navigationSearchBounds = searchBounds;
        drawSearchField(fontRenderer, searchField, searchBounds, "gui.modern.warehouse.u012", mouseX, mouseY, false);

        int buttonReserve = navigationBounds.bottom() - navigationActions.contentBottom();
        navigationViewport = new ModernMainLayout.Rect(navigationBounds.x + 5, searchBounds.bottom() + 6,
                Math.max(1, navigationBounds.width - 10),
                Math.max(1, navigationBounds.height - (searchBounds.bottom() - navigationBounds.y) - buttonReserve - 7));
        String query = normalizedSearch(searchField);
        ModernUiRenderer.beginClip(navigationViewport);
        int contentY = navigationViewport.y - navigationScroll;
        int contentBottom = contentY;

        ModernMainLayout.Rect allBounds = ModernTreeGuide.groupRow(navigationViewport, contentY);
        drawNavigationRow(fontRenderer, allBounds, "gui.modern.warehouse.u013", CATEGORY_ALL.equals(selectedCategory), false, 0,
                mouseX, mouseY);
        if (intersects(allBounds, navigationViewport)) {
            leftHits.add(new LeftHit(0, CATEGORY_ALL, null, allBounds));
        }
        contentY = ModernTreeGuide.nextY(contentY, NAV_ROW_HEIGHT);

        for (String category : categories) {
            boolean categoryMatches = matches(category, query);
            List<Warehouse> visible = matchingWarehouses(category, categoryMatches ? "" : query);
            if (!query.isEmpty() && !categoryMatches && visible.isEmpty()) {
                continue;
            }
            ModernMainLayout.Rect categoryBounds = ModernTreeGuide.groupRow(navigationViewport, contentY);
            boolean categorySelected = category.equalsIgnoreCase(selectedCategory);
            boolean collapsed = collapsedCategories.contains(category) && query.isEmpty();
            drawNavigationRow(fontRenderer, categoryBounds, category, categorySelected, true, visible.size(),
                    mouseX, mouseY);
            if (intersects(categoryBounds, navigationViewport)) {
                leftHits.add(new LeftHit(1, category, null, categoryBounds));
            }
            contentY = ModernTreeGuide.nextY(contentY, NAV_ROW_HEIGHT);
            if (!collapsed) {
                for (Warehouse warehouse : visible) {
                    ModernMainLayout.Rect warehouseBounds = ModernTreeGuide.itemRow(navigationViewport, contentY);
                    ModernTreeGuide.drawChild(navigationViewport.x, 0, categoryBounds, warehouseBounds);
                    boolean selected = warehouse == selectedWarehouse;
                    drawWarehouseRow(fontRenderer, warehouseBounds, warehouse, selected, mouseX, mouseY);
                    if (intersects(warehouseBounds, navigationViewport)) {
                        leftHits.add(new LeftHit(2, category, warehouse, warehouseBounds));
                    }
                    contentY = ModernTreeGuide.nextY(contentY, ModernTreeGuide.ITEM_HEIGHT);
                }
            }
        }
        contentBottom = contentY;
        int contentHeight = Math.max(1, contentBottom + navigationScroll - navigationViewport.y);
        navigationMaxScroll = Math.max(0, contentHeight - navigationViewport.height);
        navigationScroll = clamp(navigationScroll, 0, navigationMaxScroll);
        ModernUiRenderer.endClip();
        drawScrollbar(navigationScrollbar, navigationViewport, navigationMaxScroll, contentHeight, navigationScroll,
                mouseX, mouseY, value -> navigationScroll = value);

        navigationActions.draw(mouseX, mouseY);
        ModernUiRenderer.endClip();
    }

    private void drawNavigationRow(FontRenderer fontRenderer, ModernMainLayout.Rect row, String label,
            boolean selected, boolean category, int count, int mouseX, int mouseY) {
        if (row == null) {
            return;
        }
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        int textX = row.x + 9;
        if (category) {
            boolean collapsed = collapsedCategories.contains(label) && normalizedSearch(searchField).isEmpty();
            ModernUiRenderer.drawChevron(row.x + 7, row.y + 6, collapsed,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT);
            textX = row.x + 19;
        } else if (!CATEGORY_ALL.equals(label)) {
            ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 7,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.MUTED_TEXT);
            textX = row.x + 19;
        }
        int countReserve = category ? 26 : 6;
        ModernUiRenderer.drawText(fontRenderer, label, textX, row.y + 6,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, row.right() - textX - countReserve));
        if (category) {
            ModernUiRenderer.drawText(fontRenderer, String.valueOf(count), row.right() - 20, row.y + 6,
                    ModernUiRenderer.MUTED_TEXT, 16);
        }
    }

    private void drawWarehouseRow(FontRenderer fontRenderer, ModernMainLayout.Rect row, Warehouse warehouse,
            boolean selected, int mouseX, int mouseY) {
        boolean hovered = row.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                selected ? ModernUiRenderer.SURFACE_PRESSED
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 8,
                warehouse != null && warehouse.isActive ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
        String name = warehouse == null ? "" : safe(warehouse.name);
        ModernUiRenderer.drawText(fontRenderer, name, row.x + 19, row.y + 4,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, row.width - 26));
        String range = warehouse == null ? "" : formatRange(warehouse);
        ModernUiRenderer.drawText(fontRenderer, range, row.x + 19, row.y + 14,
                ModernUiRenderer.MUTED_TEXT, Math.max(1, row.width - 26));
    }

    private void drawEditor(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawSubtlePanel(editorBounds.x, editorBounds.y, editorBounds.width, editorBounds.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.beginClip(editorBounds);
        String title = selectedWarehouse == null ? "gui.modern.warehouse.u022" : safe(selectedWarehouse.name);
        ModernUiRenderer.drawText(fontRenderer, title, editorBounds.x + 9, editorBounds.y + 7,
                ModernUiRenderer.TEXT, Math.max(1, editorBounds.width - 18));
        if (selectedWarehouse != null) {
            String category = normalizeCategory(selectedWarehouse.category);
            ModernUiRenderer.drawText(fontRenderer, category, editorBounds.x + 9, editorBounds.y + 20,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, editorBounds.width - 18));
        }

        rightViewport = sectionNavigation.layout(fontRenderer, new ModernMainLayout.Rect(editorBounds.x,
                editorBounds.y + 33, editorBounds.width, Math.max(1, editorBounds.height - 33)));
        sectionNavigation.draw(fontRenderer, view.ordinal(), mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rightViewport.x, rightViewport.y, rightViewport.width, rightViewport.height,
                5, ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ensureLoadedEditorOwner();
        ModernUiRenderer.beginClip(rightViewport);
        int contentBottom = drawEditorContent(fontRenderer, mouseX, mouseY);
        ModernUiRenderer.endClip();
        int contentHeight = Math.max(1, contentBottom + rightScroll - (rightViewport.y + 2));
        rightMaxScroll = Math.max(0, contentHeight - rightViewport.height);
        rightScroll = clamp(rightScroll, 0, rightMaxScroll);
        drawScrollbar(rightScrollbar, rightViewport, rightMaxScroll, contentHeight, rightScroll, mouseX, mouseY,
                value -> rightScroll = value);
        ModernUiRenderer.endClip();
    }

    private int drawEditorContent(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (view != View.CHESTS) {
            chestInnerScrollbar.idle();
        }
        if (view != View.INVENTORY) {
            inventoryInnerScrollbar.idle();
        }
        if (view != View.SORTING) {
            ruleInnerScrollbar.idle();
        }
        int y = rightViewport.y + 7 - rightScroll;
        if (selectedWarehouse == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u023", rightViewport.x + 10, y + 14,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER));
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u024", rightViewport.x + 10, y + 31,
                    ModernUiRenderer.SUBTLE_TEXT, Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER));
            return y + 55;
        }
        switch (view) {
        case CHESTS:
            return drawChestsView(fontRenderer, y, mouseX, mouseY);
        case INVENTORY:
            return drawInventoryView(fontRenderer, y, mouseX, mouseY);
        case POLICY:
            return drawPolicyView(fontRenderer, y, mouseX, mouseY);
        case SORTING:
            return drawSortingView(fontRenderer, y, mouseX, mouseY);
        case REGION:
        default:
            return drawRegionView(fontRenderer, y, mouseX, mouseY);
        }
    }

    private void drawFooter(FontRenderer fontRenderer, int mouseX, int mouseY) {
        int y = bounds.bottom() - 26;
        int buttonWidth = Math.max(1, Math.min(82, (bounds.width - 42) / 3));
        int saveWidth = Math.max(buttonWidth, SaveShortcutHint.preferredWidth(fontRenderer, "保存", 16));
        saveBounds = new ModernMainLayout.Rect(bounds.right() - saveWidth - 8, y, saveWidth, 20);
        revertBounds = new ModernMainLayout.Rect(saveBounds.x - buttonWidth - 4, y, buttonWidth, 20);
        reloadBounds = new ModernMainLayout.Rect(revertBounds.x - buttonWidth - 4, y, buttonWidth, 20);
        drawAction("reload", reloadBounds, "gui.modern.warehouse.u025", ActionTone.DEFAULT, true,
                "gui.modern.warehouse.u026", fontRenderer, mouseX, mouseY);
        drawAction("revert", revertBounds, "gui.modern.warehouse.u027", ActionTone.DEFAULT, dirtyForFrame,
                "gui.modern.warehouse.u028", fontRenderer, mouseX, mouseY);
        drawAction("save", saveBounds, dirtyForFrame ? "gui.modern.warehouse.u029" : "gui.modern.warehouse.u030", ActionTone.PRIMARY, dirtyForFrame,
                "gui.modern.warehouse.u031", fontRenderer, mouseX, mouseY);
        int statusWidth = Math.max(1, reloadBounds.x - bounds.x - 22);
        String status = statusMessage.isEmpty() ? (dirtyForFrame ? "gui.modern.warehouse.u032" : "gui.modern.warehouse.u011") : statusMessage;
        ModernUiRenderer.drawText(fontRenderer, status, bounds.x + 10, y + 6,
                statusMessage.isEmpty() ? (dirtyForFrame ? ModernUiRenderer.WARNING : ModernUiRenderer.SUBTLE_TEXT)
                        : statusColor,
                statusWidth);
    }

    private void drawScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect viewport, int maxScroll, int contentHeight,
            int scroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (viewport == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(viewport, scroll, maxScroll, viewport.height, Math.max(viewport.height, contentHeight), mouseX, mouseY,
                setter);
    }

    private int drawRegionView(FontRenderer fontRenderer, int y, int mouseX, int mouseY) {
        ensureRegionFields();
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u033", "gui.modern.warehouse.u034", x, y, width, mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u035", nameField, "gui.modern.warehouse.u036", mouseX, mouseY);
        y = drawCategoryChoiceLine(fontRenderer, y, "gui.modern.warehouse.u037", mouseX, mouseY);
        y = drawPairFieldLine(fontRenderer, y, "gui.modern.warehouse.u038", x1Field, "gui.modern.warehouse.u039", z1Field, mouseX, mouseY);
        y = drawPairFieldLine(fontRenderer, y, "gui.modern.warehouse.u042", x2Field, "gui.modern.warehouse.u043", z2Field, mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "area_picker", "开始可视化取范围", ActionTone.PRIMARY,
                selectedWarehouse != null, "灵魂出窍：左键点 1，右键点 2，中键确认，Esc 取消。", mouseX, mouseY);
        y = drawToggleLine(fontRenderer, y, "region_active", "gui.modern.warehouse.u045", selectedWarehouse != null
                && selectedWarehouse.isActive, "gui.modern.warehouse.u046", mouseX, mouseY);

        y += 4;
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u047", "gui.modern.warehouse.u048", x, y, width, mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "scan", "gui.modern.warehouse.u049", ActionTone.PRIMARY,
                sourceByDraft.get(selectedWarehouse) != null,
                "gui.modern.warehouse.u050", mouseX, mouseY);
        y = drawSummaryPanel(fontRenderer, y, width, mouseX, mouseY);
        return y + 8;
    }

    private int drawChestsView(FontRenderer fontRenderer, int y, int mouseX, int mouseY) {
        syncScannedChestStates();
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u051", "gui.modern.warehouse.u052", x, y, width, mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u053", chestSearchField, "gui.modern.warehouse.u054", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "scan", "gui.modern.warehouse.u055", ActionTone.PRIMARY,
                sourceByDraft.get(selectedWarehouse) != null,
                "gui.modern.warehouse.u056", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "scan_unscanned", "gui.modern.warehouse.scan_unscanned",
                ActionTone.DEFAULT, sourceByDraft.get(selectedWarehouse) != null,
                "gui.modern.warehouse.scan_unscanned_help", mouseX, mouseY);

        int listHeight = Math.max(46, Math.min(154, rightViewport.height / 2));
        ModernMainLayout.Rect listPanel = new ModernMainLayout.Rect(x, y, width, listHeight);
        ModernUiRenderer.drawSubtlePanel(listPanel.x, listPanel.y, listPanel.width, listPanel.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "已记录箱子 " + chestCount(selectedWarehouse), listPanel.x + 8,
                listPanel.y + 7, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, listPanel.width - 16));
        chestListViewport = new ModernMainLayout.Rect(listPanel.x + 7, listPanel.y + 25,
                Math.max(1, listPanel.width - 14), Math.max(1, listPanel.height - 31));
        List<Integer> visibleIndexes = matchingChestIndexes(selectedWarehouse, normalizedSearch(chestSearchField));
        int contentHeight = Math.max(1, visibleIndexes.size() * CHEST_ROW_HEIGHT);
        chestListMaxScroll = Math.max(0, contentHeight - chestListViewport.height);
        chestListScroll = clamp(chestListScroll, 0, chestListMaxScroll);
        ModernUiRenderer.beginClip(chestListViewport);
        if (visibleIndexes.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u057", chestListViewport.x + 6,
                    chestListViewport.y + 12, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, chestListViewport.width - 12));
        } else {
            int rowWidth = ModernHoverScrollbar.contentWidth(chestListViewport.width);
            for (int rowIndex = 0; rowIndex < visibleIndexes.size(); rowIndex++) {
                int actualIndex = visibleIndexes.get(rowIndex);
                ChestData chest = chestAt(selectedWarehouse, actualIndex);
                int rowY = chestListViewport.y + rowIndex * CHEST_ROW_HEIGHT - chestListScroll;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(chestListViewport.x, rowY, rowWidth,
                        CHEST_ROW_HEIGHT - 2);
                if (!intersects(row, chestListViewport)) {
                    continue;
                }
                boolean selected = actualIndex == selectedChestIndex;
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        selected ? ModernUiRenderer.SURFACE_PRESSED
                                : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                        selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 8,
                        chest != null && chest.hasBeenScanned ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING);
                String label = chest == null ? "gui.modern.warehouse.u058" : formatPos(chest.pos);
                String details = chest == null ? "" : ModernFormI18n.tr(chest.hasBeenScanned ? "gui.modern.warehouse.u059" : "gui.modern.warehouse.u060")
                        + " · 规则 " + sortingRuleCount(chest);
                ModernMainLayout.Rect delete = new ModernMainLayout.Rect(row.right() - 23, row.y + 2, 20, 20);
                chestDeleteBounds.put(actualIndex, delete);
                boolean deleteHovered = delete.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(delete.x, delete.y, delete.width, delete.height, 4,
                        deleteHovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                        deleteHovered ? ModernUiRenderer.DANGER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawIcon(ModernUiRenderer.Icon.CLOSE, delete.x + 3, delete.y + 3, 14,
                        deleteHovered ? ModernUiRenderer.DANGER : ModernUiRenderer.MUTED_TEXT);
                ModernUiRenderer.drawText(fontRenderer, label, row.x + 19, row.y + 3,
                        selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT,
                        Math.max(1, row.width - 50));
                ModernUiRenderer.drawText(fontRenderer, details, row.x + 19, row.y + 13,
                        ModernUiRenderer.MUTED_TEXT, Math.max(1, row.width - 50));
                chestHits.add(new ChestHit(actualIndex, chest, row));
            }
        }
        ModernUiRenderer.endClip();
        if (chestListMaxScroll > 0) {
            drawInnerScrollbar(chestInnerScrollbar, chestListViewport, chestListMaxScroll, contentHeight,
                    chestListScroll, mouseX, mouseY, value -> chestListScroll = value);
        } else {
            chestInnerScrollbar.idle();
        }

        y = listPanel.bottom() + 8;
        ChestData chest = selectedChest();
        ModernMainLayout.Rect detail = new ModernMainLayout.Rect(x, y, width, 114);
        ModernUiRenderer.drawSubtlePanel(detail.x, detail.y, detail.width, detail.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        if (chest == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u061", detail.x + 8, detail.y + 13,
                    ModernUiRenderer.MUTED_TEXT, Math.max(1, detail.width - 16));
            return detail.bottom() + 8;
        }
        ModernUiRenderer.drawText(fontRenderer, formatPos(chest.pos), detail.x + 8, detail.y + 8,
                ModernUiRenderer.TEXT, Math.max(1, detail.width - 16));
        ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr(chest.hasBeenScanned ? "gui.modern.warehouse.u059" : "gui.modern.warehouse.u060")
                + " · 库存记录 " + inventoryEntryCount(chest) + " · 指定物品 " + designatedCount(chest),
                detail.x + 8, detail.y + 22, ModernUiRenderer.MUTED_TEXT, Math.max(1, detail.width - 16));
        int buttonY = detail.y + 42;
        int gap = 4;
        int buttonWidth = Math.max(1, (detail.width - 16 - gap * 2) / 3);
        drawAction("view_inventory", new ModernMainLayout.Rect(detail.x + 8, buttonY, buttonWidth, 21), "gui.modern.warehouse.u062",
                ActionTone.DEFAULT, true, "gui.modern.warehouse.u063", fontRenderer, mouseX, mouseY);
        drawAction("view_policy", new ModernMainLayout.Rect(detail.x + 8 + buttonWidth + gap, buttonY,
                buttonWidth, 21), "gui.modern.warehouse.u064", ActionTone.DEFAULT, true,
                "gui.modern.warehouse.u065", fontRenderer, mouseX, mouseY);
        drawAction("view_sorting", new ModernMainLayout.Rect(detail.x + 8 + (buttonWidth + gap) * 2, buttonY,
                Math.max(1, detail.right() - 8 - (detail.x + 8 + (buttonWidth + gap) * 2)), 21), "gui.modern.warehouse.u066",
                ActionTone.DEFAULT, true, "gui.modern.warehouse.u067", fontRenderer, mouseX, mouseY);
        drawAction("open_chest", new ModernMainLayout.Rect(detail.x + 8, buttonY + 26,
                Math.max(1, detail.width - 16), 21), "gui.modern.warehouse.u068", ActionTone.DEFAULT, chest.pos != null,
                "gui.modern.warehouse.u069", fontRenderer, mouseX, mouseY);
        return detail.bottom() + 8;
    }

    private int drawInventoryView(FontRenderer fontRenderer, int y, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ChestData chest = selectedChest();
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u070", "gui.modern.warehouse.u071", x, y, width, mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u072", inventorySearchField, "gui.modern.warehouse.u073", mouseX, mouseY);
        if (chest == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u074", x, y + 12,
                    ModernUiRenderer.MUTED_TEXT, width);
            return y + 42;
        }
        if (!chest.hasBeenScanned) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u075", x, y + 12,
                    ModernUiRenderer.MUTED_TEXT, width);
            return y + 42;
        }
        List<InventoryEntry> entries = inventoryEntries(chest, normalizedSearch(inventorySearchField));
        ModernMainLayout.Rect listPanel = new ModernMainLayout.Rect(x, y, width,
                Math.max(60, Math.min(280, rightViewport.height - 42)));
        ModernUiRenderer.drawSubtlePanel(listPanel.x, listPanel.y, listPanel.width, listPanel.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "快照物品 " + entries.size() + " 类 · " + chest.pos,
                listPanel.x + 8, listPanel.y + 7, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, listPanel.width - 16));
        inventoryViewport = new ModernMainLayout.Rect(listPanel.x + 7, listPanel.y + 25,
                Math.max(1, listPanel.width - 14), Math.max(1, listPanel.height - 31));
        int contentHeight = Math.max(1, entries.size() * 24);
        inventoryMaxScroll = Math.max(0, contentHeight - inventoryViewport.height);
        inventoryScroll = clamp(inventoryScroll, 0, inventoryMaxScroll);
        ModernUiRenderer.beginClip(inventoryViewport);
        if (entries.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u076", inventoryViewport.x + 6,
                    inventoryViewport.y + 12, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, inventoryViewport.width - 12));
        } else {
            int rowWidth = ModernHoverScrollbar.contentWidth(inventoryViewport.width);
            for (int i = 0; i < entries.size(); i++) {
                InventoryEntry entry = entries.get(i);
                int rowY = inventoryViewport.y + i * 24 - inventoryScroll;
                ModernMainLayout.Rect row = new ModernMainLayout.Rect(inventoryViewport.x, rowY, rowWidth, 22);
                if (!intersects(row, inventoryViewport)) {
                    continue;
                }
                boolean hovered = row.contains(mouseX, mouseY);
                ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                        hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                        hovered ? ModernUiRenderer.BORDER : ModernUiRenderer.BORDER_SUBTLE);
                ModernUiRenderer.drawText(fontRenderer, entry.name + "  x" + entry.count, row.x + 7, row.y + 5,
                        ModernUiRenderer.TEXT, Math.max(1, row.width - 14));
                if (hovered) {
                    String slots = joinIntegers(entry.slots);
                    hoveredTooltip = buildItemTooltip(entry, slots);
                }
            }
        }
        ModernUiRenderer.endClip();
        if (inventoryMaxScroll > 0) {
            drawInnerScrollbar(inventoryInnerScrollbar, inventoryViewport, inventoryMaxScroll, contentHeight,
                    inventoryScroll, mouseX, mouseY, value -> inventoryScroll = value);
        } else {
            inventoryInnerScrollbar.idle();
        }
        return listPanel.bottom() + 8;
    }

    private int drawPolicyView(FontRenderer fontRenderer, int y, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ChestData chest = selectedChest();
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u064", "gui.modern.warehouse.u077", x, y, width, mouseX, mouseY);
        if (chest == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u074", x, y + 12,
                    ModernUiRenderer.MUTED_TEXT, width);
            return y + 42;
        }
        ensurePolicyFields();
        y = drawReadOnlyLine(fontRenderer, y, "gui.modern.warehouse.deposit_slots",
                chest.depositInventorySlots == null || chest.depositInventorySlots.isEmpty()
                        ? ModernFormI18n.tr("gui.modern.warehouse.all_inventory")
                        : joinIntegers(chest.depositInventorySlots),
                "gui.modern.warehouse.deposit_slots_help", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "deposit_slots", "gui.modern.warehouse.select_deposit_slots",
                ActionTone.DEFAULT, true, "gui.modern.warehouse.deposit_slots_help", mouseX, mouseY);
        if (depositSlotsExpanded) {
            y = drawDepositSlots(fontRenderer, y, chest, mouseX, mouseY);
        }
        y = drawToggleLine(fontRenderer, y, "spread_after_deposit", "gui.modern.warehouse.spread_after_deposit",
                chest.spreadAfterDeposit, "gui.modern.warehouse.spread_help", mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.spread_names", spreadNamesField,
                "gui.modern.warehouse.spread_help", mouseX, mouseY);
        y = drawPostDepositSequencePicker(fontRenderer, y, chest, width, mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u078", designatedField,
                "gui.modern.warehouse.u079", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "generate_designated", "gui.modern.warehouse.u080",
                ActionTone.DEFAULT, chest.hasBeenScanned,
                "gui.modern.warehouse.u081", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "clear_designated", "gui.modern.warehouse.u082", ActionTone.DANGER,
                designatedCount(chest) > 0, "gui.modern.warehouse.u083", mouseX, mouseY);
        y = drawToggleLine(fontRenderer, y, "chest_auto_deposit", "gui.modern.warehouse.u084",
                chest.autoDepositEnabled, "gui.modern.warehouse.u085", mouseX, mouseY);
        y = drawReadOnlyLine(fontRenderer, y, "gui.modern.warehouse.u086", "gui.modern.warehouse.u087",
                "gui.modern.warehouse.u088", mouseX, mouseY);
        y = drawToggleLine(fontRenderer, y, "global_deposit", "gui.modern.warehouse.u089",
                WarehouseEventHandler.oneClickDepositMode,
                "gui.modern.warehouse.u090", mouseX, mouseY);
        y += 4;
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u091", "gui.modern.warehouse.u092", x, y, width, mouseX,
                mouseY);
        y = drawActionLine(fontRenderer, y, "auto_route", "gui.modern.warehouse.u095", ActionTone.PRIMARY, true,
                "gui.modern.warehouse.u096", mouseX, mouseY);
        y = drawChoiceLine(fontRenderer, y, "gui.modern.warehouse.u097", "withdraw_shift", "gui.modern.warehouse.u098", withdrawShiftQuickMove,
                "withdraw_normal", "gui.modern.warehouse.u099", !withdrawShiftQuickMove, mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "withdraw", "gui.modern.warehouse.u100", ActionTone.DEFAULT, true,
                "gui.modern.warehouse.u101", mouseX, mouseY);
        return y + 8;
    }

    private int drawPostDepositSequencePicker(FontRenderer fontRenderer, int y, ChestData chest, int width,
            int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.post_sequence", x, y + 6,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(1, Math.min(92, width / 3) - 7));
        int labelWidth = Math.min(92, Math.max(50, width / 3));
        ModernMainLayout.Rect button = new ModernMainLayout.Rect(x + labelWidth, y,
                Math.max(1, width - labelWidth), 21);
        String value = chest == null || chest.postDepositSequence == null || chest.postDepositSequence.trim().isEmpty()
                ? "gui.modern.warehouse.choose_sequence" : chest.postDepositSequence;
        drawChoiceButton(fontRenderer, button, value, chest != null && !safe(chest.postDepositSequence).trim().isEmpty(),
                mouseX, mouseY);
        actionBounds.put("post_sequence_picker", button);
        actionEnabled.put("post_sequence_picker", Boolean.TRUE);
        actionTooltips.put("post_sequence_picker", "gui.modern.warehouse.post_sequence_help");
        return y + 27;
    }

    private int drawDepositSlots(FontRenderer font, int y, ChestData chest, int mouseX, int mouseY) {
        int width = Math.max(9, rightViewport.width - 16 - ModernHoverScrollbar.GUTTER);
        int cell = Math.max(1, Math.min(28, width / 9));
        int x = rightViewport.x + 8 + Math.max(0, (width - cell * 9) / 2);
        depositSlotCellSize = cell;
        depositSlotGap = 0;
        depositSlotGridBounds = new ModernMainLayout.Rect(x, y, cell * 9, cell * 4);
        for (int visible = 0; visible < 36; visible++) {
            int raw = visible < 27 ? visible + 9 : visible - 27;
            boolean selected = chest.depositInventorySlots != null && chest.depositInventorySlots.contains(raw);
            ModernMainLayout.Rect slot = new ModernMainLayout.Rect(x + visible % 9 * cell,
                    y + visible / 9 * cell, cell - 1, cell - 1);
            drawChoiceButton(font, slot, String.valueOf(visible + 1), selected, mouseX, mouseY);
        }
        return drawActionLine(font, y + cell * 4 + 4, "deposit_slots_clear", "gui.modern.warehouse.all_inventory",
                ActionTone.DEFAULT, true, "gui.modern.warehouse.deposit_slots_help", mouseX, mouseY);
    }

    private int drawSortingView(FontRenderer fontRenderer, int y, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ChestData chest = selectedChest();
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u066", "gui.modern.warehouse.u102", x, y, width, mouseX, mouseY);
        if (chest == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u074", x, y + 12,
                    ModernUiRenderer.MUTED_TEXT, width);
            return y + 42;
        }
        ensureSortingRules(chest);
        y = drawToggleLine(fontRenderer, y, "sorting_enabled", "gui.modern.warehouse.u103",
                chest.sortEnabled, "gui.modern.warehouse.u104", mouseX, mouseY);
        int actionY = y;
        int gap = 4;
        int buttonWidth = Math.max(1, (width - gap * 2) / 3);
        drawAction("rule_add", new ModernMainLayout.Rect(x, actionY, buttonWidth, 21), "gui.modern.warehouse.u105",
                ActionTone.PRIMARY, true, "gui.modern.warehouse.u106", fontRenderer, mouseX, mouseY);
        drawAction("rule_duplicate", new ModernMainLayout.Rect(x + buttonWidth + gap, actionY, buttonWidth, 21), "gui.modern.warehouse.u107",
                ActionTone.DEFAULT, selectedRule() != null, "gui.modern.warehouse.u108", fontRenderer, mouseX, mouseY);
        drawAction("rule_delete", new ModernMainLayout.Rect(x + (buttonWidth + gap) * 2, actionY,
                Math.max(1, width - (buttonWidth + gap) * 2), 21), "gui.modern.warehouse.u109", ActionTone.DANGER,
                selectedRule() != null, "gui.modern.warehouse.u110", fontRenderer, mouseX, mouseY);
        y += 29;

        ModernMainLayout.Rect listPanel = new ModernMainLayout.Rect(x, y, width,
                Math.max(48, Math.min(128, rightViewport.height / 3)));
        ModernUiRenderer.drawSubtlePanel(listPanel.x, listPanel.y, listPanel.width, listPanel.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, "规则列表 " + chest.sortingRules.size(), listPanel.x + 8,
                listPanel.y + 7, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, listPanel.width - 16));
        ruleListViewport = new ModernMainLayout.Rect(listPanel.x + 7, listPanel.y + 25,
                Math.max(1, listPanel.width - 14), Math.max(1, listPanel.height - 31));
        int listContentHeight = Math.max(1, chest.sortingRules.size() * RULE_ROW_HEIGHT);
        ruleListMaxScroll = Math.max(0, listContentHeight - ruleListViewport.height);
        ruleListScroll = clamp(ruleListScroll, 0, ruleListMaxScroll);
        ModernUiRenderer.beginClip(ruleListViewport);
        for (int i = 0; i < chest.sortingRules.size(); i++) {
            SortingRule rule = chest.sortingRules.get(i);
            int rowY = ruleListViewport.y + i * RULE_ROW_HEIGHT - ruleListScroll;
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(ruleListViewport.x, rowY,
                    ModernHoverScrollbar.contentWidth(ruleListViewport.width), RULE_ROW_HEIGHT - 2);
            if (!intersects(row, ruleListViewport)) {
                continue;
            }
            boolean selected = i == selectedRuleIndex;
            boolean hovered = row.contains(mouseX, mouseY);
            ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                    selected ? ModernUiRenderer.SURFACE_PRESSED
                            : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            ModernUiRenderer.drawStatusDot(row.x + 7, row.y + 8,
                    rule != null && rule.enabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT);
            ModernUiRenderer.drawText(fontRenderer, rule == null ? "gui.modern.warehouse.u111" : safe(rule.name), row.x + 19, row.y + 6,
                    selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, row.width - 25));
            ruleHits.add(new RuleHit(i, row));
        }
        ModernUiRenderer.endClip();
        if (ruleListMaxScroll > 0) {
            drawInnerScrollbar(ruleInnerScrollbar, ruleListViewport, ruleListMaxScroll, listContentHeight,
                    ruleListScroll, mouseX, mouseY, value -> ruleListScroll = value);
        } else {
            ruleInnerScrollbar.idle();
        }
        y = listPanel.bottom() + 8;
        SortingRule rule = selectedRule();
        if (rule == null) {
            ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u112", x, y + 12,
                    ModernUiRenderer.MUTED_TEXT, width);
            return y + 38;
        }
        ensureRuleFields(rule);
        y = drawSectionTitle(fontRenderer, "gui.modern.warehouse.u113", "gui.modern.warehouse.u114", x, y, width, mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u115", ruleNameField, "gui.modern.warehouse.u066", mouseX, mouseY);
        y = drawFieldLine(fontRenderer, y, "gui.modern.warehouse.u116", ruleKeywordsField, "gui.modern.warehouse.u117", mouseX, mouseY);
        y = drawToggleLine(fontRenderer, y, "rule_enabled", "gui.modern.warehouse.u118", rule.enabled,
                "gui.modern.warehouse.u119", mouseX, mouseY);
        y = drawChoiceLine(fontRenderer, y, "gui.modern.warehouse.u120", "match_any", "gui.modern.warehouse.u121",
                rule.matchMode == SortingRule.MatchMode.ANY, "match_all", "gui.modern.warehouse.u122",
                rule.matchMode == SortingRule.MatchMode.ALL, mouseX, mouseY);
        y = drawChoiceLine(fontRenderer, y, "gui.modern.warehouse.u123", "type_any", "gui.modern.warehouse.u124",
                rule.itemType == SortingRule.ItemType.ANY, "type_shulker", "gui.modern.warehouse.u125",
                rule.itemType == SortingRule.ItemType.SHULKER_ONLY, mouseX, mouseY);
        y = drawChoiceLine(fontRenderer, y, "gui.modern.warehouse.u123", "type_non_shulker", "gui.modern.warehouse.u126",
                rule.itemType == SortingRule.ItemType.NON_SHULKER_ONLY, "type_any_second", "gui.modern.warehouse.u127",
                rule.itemType == SortingRule.ItemType.ANY, mouseX, mouseY);
        y = drawSlotGrid(fontRenderer, y, rule, mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "sort_rules", "gui.modern.warehouse.u128", ActionTone.PRIMARY,
                !chest.sortingRules.isEmpty(), "gui.modern.warehouse.u129", mouseX, mouseY);
        y = drawActionLine(fontRenderer, y, "sort_smart", "gui.modern.warehouse.u130", ActionTone.DEFAULT,
                true, "gui.modern.warehouse.u131", mouseX, mouseY);
        return y + 8;
    }

    private int drawSectionTitle(FontRenderer fontRenderer, String title, String tooltip, int x, int y, int width,
            int mouseX, int mouseY) {
        ModernUiRenderer.drawText(fontRenderer, title, x, y, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, width - 16));
        drawInfo(fontRenderer, x + Math.min(Math.max(0, width - 11), fontRenderer.getStringWidth(title) + 5), y - 1,
                tooltip, mouseX, mouseY);
        return y + 18;
    }

    private int drawFieldLine(FontRenderer fontRenderer, int y, String label, ModernTextField field, String placeholder,
            int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        boolean stacked = width < 230;
        if (stacked) {
            ModernUiRenderer.drawText(fontRenderer, label, x, y, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, width - 2));
            drawTextField(fontRenderer, field, new ModernMainLayout.Rect(x, y + 14, width, 21), placeholder,
                    mouseX, mouseY);
            return y + 41;
        }
        int labelWidth = Math.min(92, Math.max(50, width / 3));
        ModernUiRenderer.drawText(fontRenderer, label, x, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, labelWidth - 7));
        drawTextField(fontRenderer, field, new ModernMainLayout.Rect(x + labelWidth, y, width - labelWidth, 21),
                placeholder, mouseX, mouseY);
        return y + 27;
    }

    private int drawCategoryChoiceLine(FontRenderer fontRenderer, int y, String label, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        boolean stacked = width < 230;
        int labelWidth = stacked ? width : Math.min(92, Math.max(50, width / 3));
        ModernMainLayout.Rect rect = stacked
                ? new ModernMainLayout.Rect(x, y + 14, width, 21)
                : new ModernMainLayout.Rect(x + labelWidth, y, width - labelWidth, 21);
        categoryChoiceBounds = rect;
        categoryField.setVisible(false);
        categoryField.setFocused(false);
        if (stacked) {
            ModernUiRenderer.drawText(fontRenderer, label, x, y, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, width - 2));
        } else {
            ModernUiRenderer.drawText(fontRenderer, label, x, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                    Math.max(1, labelWidth - 7));
        }
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, 0xFF101820,
                hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, safe(categoryField.getText()), rect.x + 6,
                rect.y + Math.max(1, (rect.height - fontRenderer.FONT_HEIGHT) / 2), ModernUiRenderer.TEXT,
                Math.max(1, rect.width - 12));
        return y + (stacked ? 41 : 27);
    }

    private int drawPairFieldLine(FontRenderer fontRenderer, int y, String firstLabel, ModernTextField first,
            String secondLabel, ModernTextField second, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        if (width < 260) {
            y = drawFieldLine(fontRenderer, y, firstLabel, first, "0", mouseX, mouseY);
            return drawFieldLine(fontRenderer, y, secondLabel, second, "0", mouseX, mouseY);
        }
        int gap = 6;
        int groupWidth = Math.max(1, (width - gap) / 2);
        int labelWidth = Math.min(48, Math.max(24, groupWidth / 3));
        ModernUiRenderer.drawText(fontRenderer, firstLabel, x, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, labelWidth));
        drawTextField(fontRenderer, first, new ModernMainLayout.Rect(x + labelWidth, y,
                Math.max(1, groupWidth - labelWidth), 21), "0", mouseX, mouseY);
        int secondX = x + groupWidth + gap;
        ModernUiRenderer.drawText(fontRenderer, secondLabel, secondX, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, labelWidth));
        drawTextField(fontRenderer, second, new ModernMainLayout.Rect(secondX + labelWidth, y,
                Math.max(1, groupWidth - labelWidth), 21), "0", mouseX, mouseY);
        return y + 27;
    }

    private int drawActionLine(FontRenderer fontRenderer, int y, String key, String label, ActionTone tone,
            boolean enabled, String tooltip, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ModernMainLayout.Rect button = new ModernMainLayout.Rect(x, y, width, 21);
        drawAction(key, button, label, tone, enabled, tooltip, fontRenderer, mouseX, mouseY);
        return y + 27;
    }

    private int drawToggleLine(FontRenderer fontRenderer, int y, String key, String label, boolean enabled,
            String tooltip, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, y, width, 24);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, row.x + 8, row.y + 6,
                ModernUiRenderer.TEXT, Math.max(1, row.width - 54));
        int toggleX = row.x + Math.max(1, row.width - 38);
        ModernUiRenderer.drawToggle(toggleX, row.y + 4, 28, 14, enabled, row.contains(mouseX, mouseY));
        actionBounds.put(key, row);
        actionEnabled.put(key, Boolean.TRUE);
        actionTooltips.put(key, tooltip);
        if (row.contains(mouseX, mouseY)) {
            hoveredTooltip = tooltip;
        }
        return y + 30;
    }

    private int drawReadOnlyLine(FontRenderer fontRenderer, int y, String label, String value, String tooltip,
            int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ModernMainLayout.Rect row = new ModernMainLayout.Rect(x, y, width, 24);
        ModernUiRenderer.drawSubtlePanel(row.x, row.y, row.width, row.height, 4,
                ModernUiRenderer.SHELL, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawText(fontRenderer, label, row.x + 8, row.y + 6, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, row.width / 2));
        ModernUiRenderer.drawText(fontRenderer, value, row.x + Math.max(1, row.width / 2), row.y + 6,
                ModernUiRenderer.TEXT, Math.max(1, row.width / 2 - 8));
        if (row.contains(mouseX, mouseY)) {
            hoveredTooltip = tooltip;
        }
        return y + 30;
    }

    private int drawChoiceLine(FontRenderer fontRenderer, int y, String label, String firstKey, String firstLabel,
            boolean firstSelected, String secondKey, String secondLabel, boolean secondSelected, int mouseX,
            int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        boolean stacked = width < 260;
        ModernUiRenderer.drawText(fontRenderer, label, x, y + 6, ModernUiRenderer.SUBTLE_TEXT,
                stacked ? Math.max(1, width) : Math.max(1, Math.min(68, width / 3)));
        int startX = stacked ? x : x + Math.min(68, Math.max(28, width / 3));
        int available = Math.max(1, width - (startX - x));
        int gap = 4;
        if (stacked) {
            startX = x;
            y += 17;
            available = width;
        }
        int firstWidth = Math.max(1, (available - gap) / 2);
        ModernMainLayout.Rect first = new ModernMainLayout.Rect(startX, y, firstWidth, 21);
        ModernMainLayout.Rect second = new ModernMainLayout.Rect(startX + firstWidth + gap, y,
                Math.max(1, available - firstWidth - gap), 21);
        drawChoiceButton(fontRenderer, first, firstLabel, firstSelected, mouseX, mouseY);
        drawChoiceButton(fontRenderer, second, secondLabel, secondSelected, mouseX, mouseY);
        actionBounds.put(firstKey, first);
        actionBounds.put(secondKey, second);
        actionEnabled.put(firstKey, Boolean.TRUE);
        actionEnabled.put(secondKey, Boolean.TRUE);
        actionTooltips.put(firstKey, ModernFormI18n.tr(label) + ModernFormI18n.tr("gui.modern.warehouse.u132")
                + ModernFormI18n.tr(firstLabel) + "。");
        actionTooltips.put(secondKey, ModernFormI18n.tr(label) + ModernFormI18n.tr("gui.modern.warehouse.u132")
                + ModernFormI18n.tr(secondLabel) + "。");
        if (first.contains(mouseX, mouseY)) {
            hoveredTooltip = actionTooltips.get(firstKey);
        } else if (second.contains(mouseX, mouseY)) {
            hoveredTooltip = actionTooltips.get(secondKey);
        }
        return y + 27;
    }

    private int drawSlotGrid(FontRenderer fontRenderer, int y, SortingRule rule, int mouseX, int mouseY) {
        int x = rightViewport.x + 8;
        int width = Math.max(1, rightViewport.width - 8 - ModernHoverScrollbar.GUTTER);
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u133", x, y, ModernUiRenderer.SUBTLE_TEXT, width);
        y += 17;
        int available = Math.max(1, width);
        slotCellSize = Math.max(1, Math.min(18, available / 9));
        slotGap = available >= slotCellSize * 9 + 8 ? 1 : 0;
        int gridWidth = slotCellSize * 9 + slotGap * 8;
        int gridX = x + Math.max(0, (width - gridWidth) / 2);
        slotGridBounds = new ModernMainLayout.Rect(gridX, y, gridWidth, slotCellSize * 6 + slotGap * 5);
        Set<Integer> selected = new HashSet<>();
        if (rule.targetSlots != null) {
            selected.addAll(rule.targetSlots);
        }
        for (int slot = 0; slot < 54; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            int slotX = gridX + col * (slotCellSize + slotGap);
            int slotY = y + row * (slotCellSize + slotGap);
            boolean active = selected.contains(slot);
            ModernUiRenderer.drawSubtlePanel(slotX, slotY, slotCellSize, slotCellSize, 1,
                    active ? ModernUiRenderer.ACCENT_DIM : ModernUiRenderer.SURFACE,
                    active ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            if (slotCellSize >= 8) {
                ModernUiRenderer.drawText(fontRenderer, String.valueOf(slot), slotX + 2, slotY + 2,
                        active ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT, Math.max(1, slotCellSize - 3));
            }
        }
        if (slotGridBounds.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.warehouse.u134";
        }
        return slotGridBounds.bottom() + 12;
    }

    private void drawChoiceButton(FontRenderer fontRenderer, ModernMainLayout.Rect bounds, String label,
            boolean selected, int mouseX, int mouseY) {
        boolean hovered = bounds.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(bounds.x, bounds.y, bounds.width, bounds.height, 4,
                selected ? ModernUiRenderer.ACCENT_DIM : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, bounds.x + 5, bounds.y + 6,
                selected ? ModernUiRenderer.TEXT : ModernUiRenderer.SUBTLE_TEXT, Math.max(1, bounds.width - 10));
    }

    private int drawSummaryPanel(FontRenderer fontRenderer, int y, int width, int mouseX, int mouseY) {
        if (selectedWarehouse == null) {
            return y;
        }
        ModernMainLayout.Rect panel = new ModernMainLayout.Rect(rightViewport.x + 8, y, width, 66);
        ModernUiRenderer.drawSubtlePanel(panel.x, panel.y, panel.width, panel.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        String range = formatRange(selectedWarehouse);
        ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr("gui.modern.warehouse.fmt.range", range), panel.x + 8, panel.y + 9,
                ModernUiRenderer.TEXT, Math.max(1, panel.width - 16));
        ModernUiRenderer.drawText(fontRenderer, ModernFormI18n.tr("gui.modern.warehouse.fmt.size",
                formatDouble(Math.abs(selectedWarehouse.x2 - selectedWarehouse.x1)),
                formatDouble(Math.abs(selectedWarehouse.z2 - selectedWarehouse.z1)),
                String.valueOf(chestCount(selectedWarehouse))), panel.x + 8,
                panel.y + 23, ModernUiRenderer.SUBTLE_TEXT, Math.max(1, panel.width - 16));
        ModernUiRenderer.drawText(fontRenderer, selectedWarehouse.isActive ? "gui.modern.warehouse.u135"
                : "gui.modern.warehouse.u136", panel.x + 8, panel.y + 38,
                selectedWarehouse.isActive ? ModernUiRenderer.SUCCESS : ModernUiRenderer.MUTED_TEXT,
                Math.max(1, panel.width - 16));
        if (panel.contains(mouseX, mouseY)) {
            hoveredTooltip = "gui.modern.warehouse.u137";
        }
        return panel.bottom();
    }

    private void drawInnerScrollbar(ModernHoverScrollbar bar, ModernMainLayout.Rect viewport, int maxScroll,
            int contentHeight, int scroll, int mouseX, int mouseY, java.util.function.IntConsumer setter) {
        if (bar == null) {
            return;
        }
        if (viewport == null || maxScroll <= 0) {
            bar.idle();
            return;
        }
        bar.draw(viewport, scroll, maxScroll, viewport.height, Math.max(viewport.height, contentHeight), mouseX, mouseY,
                setter);
    }

    private enum ActionTone {
        DEFAULT,
        PRIMARY,
        DANGER
    }

    private void drawAction(String key, ModernMainLayout.Rect rect, String label, ActionTone tone, boolean enabled,
            String tooltip, FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            return;
        }
        actionBounds.put(key, rect);
        actionEnabled.put(key, Boolean.valueOf(enabled));
        actionTooltips.put(key, tooltip == null ? "" : tooltip);
        boolean hovered = enabled && rect.contains(mouseX, mouseY);
        int fill;
        int border;
        int color;
        if (!enabled) {
            fill = 0xFF151E26;
            border = ModernUiRenderer.BORDER_SUBTLE;
            color = ModernUiRenderer.MUTED_TEXT;
        } else if (tone == ActionTone.PRIMARY) {
            fill = hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT;
            border = hovered ? 0xFFFFB0C4 : ModernUiRenderer.ACCENT;
            color = ModernUiRenderer.SHELL;
        } else if (tone == ActionTone.DANGER) {
            fill = hovered ? 0xFFF29A78 : 0xFFD96A52;
            border = hovered ? 0xFFFFC0A7 : 0xFFD96A52;
            color = ModernUiRenderer.SHELL;
        } else {
            fill = hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE;
            border = hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE;
            color = ModernUiRenderer.TEXT;
        }
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, fill, border);
        ModernUiRenderer.drawText(fontRenderer, label, rect.x + 5,
                rect.y + Math.max(3, (rect.height - fontRenderer.FONT_HEIGHT) / 2), color,
                Math.max(1, rect.width - 10));
        if (hovered && tooltip != null && !tooltip.isEmpty()) {
            hoveredTooltip = tooltip;
        }
    }

    private void drawTextField(FontRenderer fontRenderer, ModernTextField field, ModernMainLayout.Rect rect,
            String placeholder, int mouseX, int mouseY) {
        if (field == null || rect == null || rightViewport == null || !intersects(rect, rightViewport)) {
            if (field != null) {
                field.setVisible(false);
            }
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        if (field == focusedFieldBeforeLayoutReset) {
            field.setFocused(true);
        }
        boolean focused = field.isFocused();
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : hovered ? 0xFF617581 : ModernUiRenderer.BORDER_SUBTLE);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.x = rect.x + 6;
        field.y = rect.y + Math.max(1, (rect.height - fontRenderer.FONT_HEIGHT) / 2);
        field.width = Math.max(1, rect.width - 12);
        field.height = Math.max(2, fontRenderer.FONT_HEIGHT + 2);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && safe(field.getText()).trim().isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, rect.x + 6,
                    rect.y + Math.max(1, (rect.height - fontRenderer.FONT_HEIGHT) / 2), ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, rect.width - 12));
        }
        fieldHits.add(new FieldHit(field, rect));
        if (hovered) {
            tooltipHits.add(new TooltipHit(rect, "gui.modern.warehouse.u138"));
        }
    }

    private void drawSearchField(FontRenderer fontRenderer, ModernTextField field, ModernMainLayout.Rect rect,
            String placeholder, int mouseX, int mouseY, boolean rightField) {
        if (field == null || rect == null) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        if (field == focusedFieldBeforeLayoutReset) {
            field.setFocused(true);
        }
        boolean focused = field.isFocused();
        boolean hovered = rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                ModernUiRenderer.SURFACE, focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        int iconOffset = Math.min(18, Math.max(0, rect.width - 1));
        ModernUiRenderer.drawSearchIcon(rect.x + 5, rect.y + 4, ModernUiRenderer.MUTED_TEXT);
        field.x = rect.x + iconOffset;
        field.y = rect.y + Math.max(1, (rect.height - fontRenderer.FONT_HEIGHT) / 2);
        field.width = Math.max(1, rect.right() - field.x - 5);
        field.height = Math.max(2, fontRenderer.FONT_HEIGHT + 2);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
        if (!focused && safe(field.getText()).isEmpty()) {
            ModernUiRenderer.drawText(fontRenderer, placeholder, field.x, field.y, ModernUiRenderer.MUTED_TEXT,
                    Math.max(1, field.width));
        }
        if (hovered) {
            tooltipHits.add(new TooltipHit(rect, rightField ? "gui.modern.warehouse.u139" : "gui.modern.warehouse.u140"));
        }
    }

    private void drawInfo(FontRenderer fontRenderer, int x, int y, String tooltip, int mouseX, int mouseY) {
        if (tooltip == null || tooltip.trim().isEmpty() || bounds == null) {
            return;
        }
        ModernMainLayout.Rect icon = new ModernMainLayout.Rect(x, y, 11, 11);
        if (icon.x < bounds.x || icon.y < bounds.y || icon.x >= bounds.right() || icon.y >= bounds.bottom()) {
            return;
        }
        boolean hovered = icon.contains(mouseX, mouseY);
        if (hovered) {
            ModernUiRenderer.drawRoundedRect(icon.x - 2, icon.y - 2, 15, 15, 4, ModernUiRenderer.SURFACE_HOVER);
            hoveredTooltip = tooltip;
        }
        ModernUiRenderer.drawInfoIcon(icon.x, icon.y, hovered ? ModernUiRenderer.TEXT : ModernUiRenderer.MUTED_TEXT);
        tooltipHits.add(new TooltipHit(icon, tooltip));
    }

    private void drawInputDialog(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawBackdropOverlay(bounds, 0x99000000);
        int width = Math.min(330, Math.max(1, bounds.width - 18));
        int height = 92;
        int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
        dialogBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawPanel(x, y, width, height, 6, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        String title = dialogMode == DialogMode.ADD_CATEGORY ? "gui.modern.warehouse.u141" : "gui.modern.warehouse.u142";
        ModernUiRenderer.drawText(fontRenderer, title, x + 10, y + 9, ModernUiRenderer.TEXT,
                Math.max(1, width - 20));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u143", x + 10, y + 27, ModernUiRenderer.SUBTLE_TEXT,
                Math.max(1, width - 20));
        ModernMainLayout.Rect fieldRect = new ModernMainLayout.Rect(x + 10, y + 42, Math.max(1, width - 20), 21);
        drawDialogField(fontRenderer, dialogField, fieldRect, mouseX, mouseY);
        int buttonWidth = Math.max(1, Math.min(84, (width - 30) / 2));
        dialogCancelBounds = new ModernMainLayout.Rect(x + width - buttonWidth * 2 - 14, y + 68, buttonWidth, 20);
        dialogConfirmBounds = new ModernMainLayout.Rect(x + width - buttonWidth - 7, y + 68, buttonWidth, 20);
        drawDialogButton(fontRenderer, dialogCancelBounds, "gui.modern.warehouse.u144", false, mouseX, mouseY);
        drawDialogButton(fontRenderer, dialogConfirmBounds, "gui.modern.warehouse.u145", true, mouseX, mouseY);
    }

    private void drawDialogField(FontRenderer fontRenderer, ModernTextField field, ModernMainLayout.Rect rect, int mouseX,
            int mouseY) {
        if (field == null) {
            return;
        }
        field.setVisible(true);
        field.setEnabled(true);
        if (field == focusedFieldBeforeLayoutReset) {
            field.setFocused(true);
        }
        boolean focused = field.isFocused();
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4, 0xFF101820,
                focused ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        field.x = rect.x + 6;
        field.y = rect.y + 3;
        field.width = Math.max(1, rect.width - 12);
        field.height = 16;
        field.setEnableBackgroundDrawing(false);
        field.setTextColor(ModernUiRenderer.TEXT);
        ModernUiRenderer.reflowTextField(field);
        ModernUiRenderer.drawTextField(field);
    }

    private void drawDialogButton(FontRenderer fontRenderer, ModernMainLayout.Rect rect, String label, boolean primary,
            int mouseX, int mouseY) {
        boolean hovered = rect != null && rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                primary ? (hovered ? 0xFFFF86A7 : ModernUiRenderer.ACCENT)
                        : hovered ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SURFACE,
                primary ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, rect.x + 5,
                rect.y + Math.max(3, (rect.height - fontRenderer.FONT_HEIGHT) / 2),
                primary ? ModernUiRenderer.SHELL : ModernUiRenderer.TEXT, Math.max(1, rect.width - 10));
    }

    private void drawConfirmation(FontRenderer fontRenderer, int mouseX, int mouseY) {
        ModernUiRenderer.drawBackdropOverlay(bounds, 0x99000000);
        int width = Math.min(370, Math.max(1, bounds.width - 18));
        int height = 104;
        int x = bounds.x + Math.max(0, (bounds.width - width) / 2);
        int y = bounds.y + Math.max(0, (bounds.height - height) / 2);
        confirmationBounds = new ModernMainLayout.Rect(x, y, width, height);
        ModernUiRenderer.drawPanel(x, y, width, height, 6, ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        ModernUiRenderer.drawText(fontRenderer, confirmationTitle(), x + 10, y + 9, ModernUiRenderer.TEXT,
                Math.max(1, width - 20));
        ModernUiRenderer.drawText(fontRenderer, confirmationMessage(), x + 10, y + 28,
                ModernUiRenderer.SUBTLE_TEXT, Math.max(1, width - 20));
        ModernUiRenderer.drawText(fontRenderer, "gui.modern.warehouse.u146", x + 10, y + 43,
                ModernUiRenderer.WARNING, Math.max(1, width - 20));
        int buttonWidth = Math.max(1, Math.min(92, (width - 30) / 2));
        confirmationNoBounds = new ModernMainLayout.Rect(x + width - buttonWidth * 2 - 14, y + 76, buttonWidth, 20);
        confirmationYesBounds = new ModernMainLayout.Rect(x + width - buttonWidth - 7, y + 76, buttonWidth, 20);
        drawDialogButton(fontRenderer, confirmationNoBounds, "gui.modern.warehouse.u144", false, mouseX, mouseY);
        String confirmationLabel = confirmationMode == ConfirmationMode.CLEAR_DESIGNATED ? "gui.modern.warehouse.u147"
                : confirmationMode == ConfirmationMode.RELOAD ? "gui.modern.warehouse.u148" : "gui.modern.warehouse.u149";
        drawDangerButton(fontRenderer, confirmationYesBounds, confirmationLabel, mouseX, mouseY);
    }

    private void drawDangerButton(FontRenderer fontRenderer, ModernMainLayout.Rect rect, String label, int mouseX,
            int mouseY) {
        boolean hovered = rect != null && rect.contains(mouseX, mouseY);
        ModernUiRenderer.drawSubtlePanel(rect.x, rect.y, rect.width, rect.height, 4,
                hovered ? 0xFFF29A78 : 0xFFD96A52, hovered ? 0xFFFFC0A7 : 0xFFD96A52);
        com.zszl.zszlScriptMod.gui.modern.SaveShortcutHint.drawText(fontRenderer, label, rect.x + 5,
                rect.y + Math.max(3, (rect.height - fontRenderer.FONT_HEIGHT) / 2), ModernUiRenderer.SHELL,
                Math.max(1, rect.width - 10));
    }

    private void drawContextMenu(FontRenderer fontRenderer, int mouseX, int mouseY) {
        if (contextMenu.isEmpty()) {
            contextBounds = null;
            return;
        }
        int width = Math.min(190, Math.max(1, bounds.width - 12));
        int height = contextMenu.size() * 21 + 4;
        int x = clamp(contextBounds == null ? bounds.x : contextBounds.x, bounds.x + 4,
                Math.max(bounds.x + 4, bounds.right() - width - 4));
        int y = clamp(contextBounds == null ? bounds.y : contextBounds.y, bounds.y + 4,
                Math.max(bounds.y + 4, bounds.bottom() - height - 4));
        ModernMainLayout.Rect panel = new ModernMainLayout.Rect(x, y, width, height);
        contextBounds = panel;
        ModernUiRenderer.drawPanel(panel.x, panel.y, panel.width, panel.height, 6,
                ModernUiRenderer.SHELL_RAISED, ModernUiRenderer.BORDER);
        for (int i = 0; i < contextMenu.size(); i++) {
            MenuEntry entry = contextMenu.get(i);
            ModernMainLayout.Rect row = new ModernMainLayout.Rect(panel.x + 2, panel.y + 2 + i * 21,
                    Math.max(1, panel.width - 4), 20);
            boolean hovered = row.contains(mouseX, mouseY);
            if (hovered) {
                ModernUiRenderer.drawRoundedRect(row.x, row.y, row.width, row.height, 3,
                        ModernUiRenderer.SURFACE_HOVER);
            }
            ModernUiRenderer.drawText(fontRenderer, entry.label, row.x + 7, row.y + 6,
                    ModernUiRenderer.TEXT, Math.max(1, row.width - 14));
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (!initialized || bounds == null || !bounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.mouseClicked(mouseX, mouseY);
        }
        if (dialogMode != DialogMode.NONE) {
            return handleDialogClick(mouseX, mouseY, mouseButton);
        }
        if (confirmationMode != ConfirmationMode.NONE) {
            return handleConfirmationClick(mouseX, mouseY, mouseButton);
        }
        // A navigation chooser may still be open from the previous region page.
        // Let a click on the editor close that chooser and continue to the real
        // field hit test instead of consuming the click as a group selection.
        if (navigationActions.isOpen() && editorBounds != null && editorBounds.contains(mouseX, mouseY)) {
            navigationActions.close();
        } else if (navigationActions.mouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton == 1 && navigationContext(mouseX, mouseY)) return true;
        if (contextBounds != null) {
            if (handleContextClick(mouseX, mouseY, mouseButton)) {
                return true;
            }
            contextBounds = null;
            contextMenu.clear();
            return true;
        }
        if (mouseButton != 0) {

            return true;
        }
        if (splitDividerBounds != null && splitDividerBounds.contains(mouseX, mouseY)) {
            draggingDivider = true;
            return true;
        }
        if (navigationScrollbar.beginDrag(mouseX, mouseY) || beginInnerScrollbarDrag(mouseX, mouseY)
                || rightScrollbar.beginDrag(mouseX, mouseY)) {
            return true;
        }
        if (saveBounds != null && saveBounds.contains(mouseX, mouseY)) {
            save();
            return true;
        }
        if (revertBounds != null && revertBounds.contains(mouseX, mouseY)) {
            discardDraft();
            return true;
        }
        if (reloadBounds != null && reloadBounds.contains(mouseX, mouseY)) {
            if (isDirty()) {
                requestReload();
            } else {
                reloadFromSource();
            }
            return true;
        }
        if (searchField != null && searchField.getVisible() && navigationSearchBounds != null
                && navigationSearchBounds.contains(mouseX, mouseY)) {
            clearFieldFocusExcept(searchField);
            searchField.setFocused(true);
            searchField.mouseClicked(mouseX, mouseY, mouseButton);
            return true;
        }
        if (navigationNewBounds != null && navigationNewBounds.contains(mouseX, mouseY)) {
            addWarehouse();
            return true;
        }
        if (navigationDuplicateBounds != null && navigationDuplicateBounds.contains(mouseX, mouseY)) {
            duplicateWarehouse();
            return true;
        }
        if (navigationCategoryBounds != null && navigationCategoryBounds.contains(mouseX, mouseY)) {
            openCategoryInput(DialogMode.ADD_CATEGORY, "");
            return true;
        }
        if (navigationDeleteBounds != null && navigationDeleteBounds.contains(mouseX, mouseY)) {
            requestDeleteCurrent();
            return true;
        }
        for (int i = leftHits.size() - 1; i >= 0; i--) {
            LeftHit hit = leftHits.get(i);
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            if (hit.type == 0) {
                selectAllCategory();
            } else if (hit.type == 1) {
                selectCategory(hit.category);
                if (!normalizedSearch(searchField).isEmpty()) {
                    collapsedCategories.remove(hit.category);
                } else if (!collapsedCategories.add(hit.category)) {
                    collapsedCategories.remove(hit.category);
                }
            } else {
                selectWarehouse(hit.warehouse);
            }
            return true;
        }
        if (editorBounds != null && editorBounds.contains(mouseX, mouseY)) {
            if (handleEditorClick(mouseX, mouseY)) {
                return true;
            }
        }
        clearFieldFocusExcept(null);
        return true;
    }

    private boolean handleEditorClick(int mouseX, int mouseY) {
        if (rightViewport == null) {
            return true;
        }
        if (sectionNavigation.contains(mouseX, mouseY)) {
            int index = sectionNavigation.click(mouseX, mouseY);
            if (index >= 0) switchView(View.values()[index]);
            return true;
        }
        if (!rightViewport.contains(mouseX, mouseY)) {
            return true;
        }
        if (view == View.REGION && categoryChoiceBounds != null && categoryChoiceBounds.contains(mouseX, mouseY)) {
            clearFieldFocusExcept(null);
            navigationActions.choose("选择分组", categories, categoryField.getText(), value -> {
                categoryField.setText(value);
                applyCurrentViewFields();
            });
            return true;
        }
        for (FieldHit hit : fieldHits) {
            if (hit.bounds.contains(mouseX, mouseY)) {
                clearFieldFocusExcept(hit.field);
                hit.field.mouseClicked(mouseX, mouseY, 0);
                return true;
            }
        }
        if (view == View.POLICY && depositSlotsExpanded && depositSlotGridBounds != null
                && depositSlotGridBounds.contains(mouseX, mouseY)) {
            int visibleSlot = depositSlotAt(mouseX, mouseY);
            ChestData chest = selectedChest();
            if (visibleSlot >= 0 && chest != null) {
                int rawSlot = visibleToRawDepositSlot(visibleSlot);
                LinkedHashSet<Integer> selected = selectedDepositSlotSet(chest);
                depositSlotDragStart = visibleSlot;
                depositSlotDragAddMode = !selected.contains(rawSlot);
                applyDepositSlotRectangle(chest, visibleSlot, visibleSlot);
                draggingDepositSlots = true;
                return true;
            }
        }
        for (Map.Entry<String, ModernMainLayout.Rect> entry : actionBounds.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(mouseX, mouseY)) {
                if (Boolean.TRUE.equals(actionEnabled.get(entry.getKey()))) {
                    performAction(entry.getKey());
                }
                return true;
            }
        }
        if (view == View.CHESTS && chestListViewport != null && chestListViewport.contains(mouseX, mouseY)) {
            for (Map.Entry<Integer, ModernMainLayout.Rect> entry : chestDeleteBounds.entrySet()) {
                if (entry.getValue().contains(mouseX, mouseY)) {
                    ChestData chest = chestAt(selectedWarehouse, entry.getKey());
                    if (chest != null) {
                        requestDeleteChest(chest);
                    }
                    return true;
                }
            }
            for (int i = chestHits.size() - 1; i >= 0; i--) {
                ChestHit hit = chestHits.get(i);
                if (hit.bounds.contains(mouseX, mouseY)) {
                    selectChest(hit.index);
                    return true;
                }
            }
        }
        if (view == View.SORTING && ruleListViewport != null && ruleListViewport.contains(mouseX, mouseY)) {
            for (int i = ruleHits.size() - 1; i >= 0; i--) {
                RuleHit hit = ruleHits.get(i);
                if (hit.bounds.contains(mouseX, mouseY)) {
                    selectRule(hit.index);
                    return true;
                }
            }
        }
        if (view == View.SORTING && slotGridBounds != null && slotGridBounds.contains(mouseX, mouseY)) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                SortingRule rule = selectedRule();
                if (rule != null) {
                    ensureSortingRules(selectedChest());
                    LinkedHashSet<Integer> selected = selectedSlotSet(rule);
                    draggingSlots = true;
                    slotDragStart = slot;
                    if (selected.contains(slot)) {
                        selected.remove(slot);
                    } else {
                        selected.add(slot);
                    }
                    rule.targetSlots = new ArrayList<>(selected);
                }
                return true;
            }
        }
        return true;
    }

    private boolean handleDialogClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (dialogCancelBounds != null && dialogCancelBounds.contains(mouseX, mouseY)) {
            closeDialog();
            return true;
        }
        if (dialogConfirmBounds != null && dialogConfirmBounds.contains(mouseX, mouseY)) {
            commitDialog();
            return true;
        }
        if (dialogField != null && dialogBounds != null && dialogField.getVisible()
                && fieldContains(dialogField, mouseX, mouseY)) {
            dialogField.setFocused(true);
            dialogField.mouseClicked(mouseX, mouseY, 0);
            return true;
        }
        if (dialogBounds != null && dialogBounds.contains(mouseX, mouseY)) {
            dialogField.setFocused(false);
        }
        return true;
    }

    private boolean handleConfirmationClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        if (confirmationNoBounds != null && confirmationNoBounds.contains(mouseX, mouseY)) {
            clearConfirmation();
            return true;
        }
        if (confirmationYesBounds != null && confirmationYesBounds.contains(mouseX, mouseY)) {
            confirmDestructiveAction();
            return true;
        }
        return true;
    }

    private boolean handleContextClick(int mouseX, int mouseY, int mouseButton) {
        if (contextBounds == null || !contextBounds.contains(mouseX, mouseY)) {
            return false;
        }
        if (mouseButton != 0) {
            return true;
        }
        int index = (mouseY - contextBounds.y - 2) / 21;
        if (index < 0 || index >= contextMenu.size()) {
            return true;
        }
        String key = contextMenu.get(index).key;
        contextBounds = null;
        contextMenu.clear();
        if ("category_new".equals(key)) {
            openCategoryInput(DialogMode.ADD_CATEGORY, "");
        } else if ("category_rename".equals(key)) {
            openCategoryInput(DialogMode.RENAME_CATEGORY, contextCategory);
        } else if ("category_delete".equals(key)) {
            requestDeleteCategory(contextCategory);
        } else if ("warehouse_new".equals(key)) {
            addWarehouse();
        } else if ("warehouse_duplicate".equals(key)) {
            duplicateWarehouse();
        } else if ("warehouse_delete".equals(key)) {
            requestDeleteWarehouse(contextWarehouse);
        }
        return true;
    }

    private void openContextMenu(int mouseX, int mouseY) {
        if (!applyCurrentViewFields()) {
            return;
        }
        for (int i = leftHits.size() - 1; i >= 0; i--) {
            LeftHit hit = leftHits.get(i);
            if (!hit.bounds.contains(mouseX, mouseY)) {
                continue;
            }
            contextMenu.clear();
            contextWarehouse = hit.warehouse;
            contextCategory = hit.category;
            if (hit.type == 1) {
                selectedCategory = hit.category;
                contextMenu.add(new MenuEntry("warehouse_new", "gui.modern.warehouse.u150"));
                contextMenu.add(new MenuEntry("category_rename", "gui.modern.warehouse.u151"));
                contextMenu.add(new MenuEntry("category_delete", "gui.modern.warehouse.u152"));
            } else if (hit.type == 2) {
                if (hit.warehouse != selectedWarehouse && applyCurrentViewFields()) {
                    selectedWarehouse = hit.warehouse;
                    selectedCategory = normalizeCategory(hit.warehouse.category);
                    resetSelectionFields();
                }
                contextMenu.add(new MenuEntry("warehouse_duplicate", "gui.modern.warehouse.u153"));
                contextMenu.add(new MenuEntry("warehouse_delete", "gui.modern.warehouse.u154"));
            } else {
                contextMenu.add(new MenuEntry("warehouse_new", "gui.modern.warehouse.u155"));
                contextMenu.add(new MenuEntry("category_new", "gui.modern.warehouse.u156"));
            }
            contextBounds = new ModernMainLayout.Rect(mouseX, mouseY, 1, 1);
            return;
        }
    }

    @Override
    public boolean keyTyped(char typedChar, int keyCode) {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.keyTyped(typedChar, keyCode);
        }
        if (navigationActions.keyTyped(typedChar, keyCode)) return true;
        if (!initialized) {
            return false;
        }
        if (dialogMode != DialogMode.NONE) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeDialog();
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                commitDialog();
                return true;
            }
            return dialogField != null && dialogField.textboxKeyTyped(typedChar, keyCode);
        }
        if (confirmationMode != ConfirmationMode.NONE) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                clearConfirmation();
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                confirmDestructiveAction();
                return true;
            }
            return true;
        }
        if (contextBounds != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                contextBounds = null;
                contextMenu.clear();
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            if (draggingDivider || isAnyScrollbarDragging() || draggingSlots) {
                draggingDivider = false;
                endAllScrollbarDrags();
                draggingSlots = false;
                return true;
            }
            return false;
        }
        if (keyCode == Keyboard.KEY_F && isControlDown()) {
            clearFieldFocusExcept(searchField);
            if (searchField != null) {
                searchField.setFocused(true);
            }
            return true;
        }
        if (searchField != null && searchField.isFocused()) {
            if (searchField.textboxKeyTyped(typedChar, keyCode)) {
                navigationScroll = 0;
                return true;
            }
        }
        for (FieldHit hit : fieldHits) {
            if (hit.field != null && hit.field.isFocused() && hit.field.textboxKeyTyped(typedChar, keyCode)) {
                return true;
            }
        }
        if (!isAnyFieldFocused()) {
            int page = rightViewport == null ? 32 : Math.max(24, rightViewport.height - 20);
            if (keyCode == Keyboard.KEY_HOME) {
                rightScroll = 0;
                return true;
            }
            if (keyCode == Keyboard.KEY_END) {
                rightScroll = rightMaxScroll;
                return true;
            }
            if (keyCode == Keyboard.KEY_PRIOR) {
                rightScroll = clamp(rightScroll - page, 0, rightMaxScroll);
                return true;
            }
            if (keyCode == Keyboard.KEY_NEXT) {
                rightScroll = clamp(rightScroll + page, 0, rightMaxScroll);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        }
        if (clickedMouseButton == 0 && sectionNavigation.drag(mouseX, mouseY)) return true;
        if (clickedMouseButton != 0) {
            return false;
        }
        if (draggingDivider && bounds != null) {
            int splitTotal = Math.max(2, bounds.width - 20);
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(splitTotal,
                    mouseX - bounds.x - 8, 150, 280, 82, 156);
            navigationRatio = split.ratio;
            return true;
        }
        if (applyAnyScrollbarDrag(mouseX, mouseY)) {
            return true;
        }
        if (draggingSlots && slotGridBounds != null) {
            int slot = slotAt(mouseX, mouseY);
            if (slot >= 0) {
                selectSlotRectangle(slotDragStart, slot);
            }
            return true;
        }
        if (draggingDepositSlots && depositSlotGridBounds != null) {
            int slot = depositSlotAt(mouseX, mouseY);
            if (slot >= 0) {
                applyDepositSlotRectangle(selectedChest(), depositSlotDragStart, slot);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int state) {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.mouseReleased(mouseX, mouseY, state);
        }
        sectionNavigation.release();
        if (state == 0 && (draggingDivider || isAnyScrollbarDragging() || draggingSlots || draggingDepositSlots)) {
            if (draggingDivider) {
                MainUiLayoutManager.setModernSplitRatio("warehouse.navigation", navigationRatio);
            }
            draggingDivider = false;
            endAllScrollbarDrags();
            draggingSlots = false;
            slotDragStart = -1;
            draggingDepositSlots = false;
            depositSlotDragStart = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseWheel(int wheel) {
        return handleMouseWheel(wheel, lastMouseX, lastMouseY);
    }

    @Override
    public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.handleMouseWheel(wheel, mouseX, mouseY);
        }
        if (navigationActions.wheel(wheel)) return true;
        if (wheel == 0 || bounds == null || !bounds.contains(mouseX, mouseY)
                || dialogMode != DialogMode.NONE || confirmationMode != ConfirmationMode.NONE) {
            return false;
        }
        if (navigationViewport != null && navigationViewport.contains(mouseX, mouseY)) {
            int before = navigationScroll;
            navigationScroll = clamp(navigationScroll + (wheel > 0 ? -32 : 32), 0, navigationMaxScroll);
            return before != navigationScroll;
        }
        if (sectionNavigation.wheel(wheel, mouseX, mouseY)) return true;
        if (view == View.CHESTS && chestListViewport != null && chestListViewport.contains(mouseX, mouseY)
                && chestListMaxScroll > 0) {
            int before = chestListScroll;
            chestListScroll = clamp(chestListScroll + (wheel > 0 ? -25 : 25), 0, chestListMaxScroll);
            return before != chestListScroll;
        }
        if (view == View.INVENTORY && inventoryViewport != null && inventoryViewport.contains(mouseX, mouseY)
                && inventoryMaxScroll > 0) {
            int before = inventoryScroll;
            inventoryScroll = clamp(inventoryScroll + (wheel > 0 ? -25 : 25), 0, inventoryMaxScroll);
            return before != inventoryScroll;
        }
        if (view == View.SORTING && ruleListViewport != null && ruleListViewport.contains(mouseX, mouseY)
                && ruleListMaxScroll > 0) {
            int before = ruleListScroll;
            ruleListScroll = clamp(ruleListScroll + (wheel > 0 ? -25 : 25), 0, ruleListMaxScroll);
            return before != ruleListScroll;
        }
        if (rightViewport != null && rightViewport.contains(mouseX, mouseY)) {
            int before = rightScroll;
            rightScroll = clamp(rightScroll + (wheel > 0 ? -32 : 32), 0, rightMaxScroll);
            return before != rightScroll;
        }
        return false;
    }

    @Override
    public boolean handleEscape() {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isOpen()) {
            return postDepositSequencePicker.handleEscape();
        }
        if (navigationActions.isOpen()) { navigationActions.close(); return true; }
        if (dialogMode != DialogMode.NONE) {
            closeDialog();
            return true;
        }
        if (confirmationMode != ConfirmationMode.NONE) {
            clearConfirmation();
            return true;
        }
        if (contextBounds != null) {
            contextBounds = null;
            contextMenu.clear();
            return true;
        }
            if (draggingDivider || isAnyScrollbarDragging() || draggingSlots || draggingDepositSlots) {
                draggingDivider = false;
                endAllScrollbarDrags();
                draggingSlots = false;
                slotDragStart = -1;
                draggingDepositSlots = false;
                depositSlotDragStart = -1;
                return true;
        }
        return false;
    }

    @Override
    public boolean isTextInputFocused() {
        if (postDepositSequencePicker != null && postDepositSequencePicker.isTextInputFocused()) return true;
        if (navigationActions.isOpen()) return true;
        return isAnyFieldFocused();
    }

    @Override
    public boolean containsContent(int mouseX, int mouseY) {
        return bounds != null && bounds.contains(mouseX, mouseY);
    }

    @Override
    public String getHoveredTooltip(int mouseX, int mouseY) {
        String sectionTip = sectionNavigation.tooltip(mouseX, mouseY);
        if (!sectionTip.isEmpty()) return sectionTip;
        for (int i = tooltipHits.size() - 1; i >= 0; i--) {
            TooltipHit hit = tooltipHits.get(i);
            if (hit.bounds != null && hit.bounds.contains(mouseX, mouseY)) {
                return hit.text;
            }
        }
        return hoveredTooltip == null ? "" : hoveredTooltip;
    }

    @Override
    public void save() {
        if (!initialized) {
            return;
        }
        View savedView = view;
        BlockPos savedChestPos = selectedChest() == null ? null : selectedChest().pos;
        if (!applyCurrentViewFields()) {
            return;
        }
        String savedRuleName = selectedRule() == null ? "" : safe(selectedRule().name);
        if (selectedWarehouse != null && isBlank(selectedWarehouse.name)) {
            setStatus("gui.modern.warehouse.u157", ModernUiRenderer.WARNING);
            return;
        }
        try {
            List<Warehouse> source = WarehouseManager.warehouses;
            if (source == null) {
                source = new CopyOnWriteArrayList<>();
                WarehouseManager.warehouses = source;
            }
            Set<Warehouse> keptSources = Collections.newSetFromMap(new IdentityHashMap<Warehouse, Boolean>());
            Warehouse selectedSource = null;
            for (Warehouse draft : warehouses) {
                if (draft == null) {
                    continue;
                }
                Warehouse target = sourceByDraft.get(draft);
                if (target == null || !source.contains(target)) {
                    target = copyWarehouse(draft);
                    source.add(target);
                    sourceByDraft.put(draft, target);
                }
                applyWarehouseValues(target, draft);
                keptSources.add(target);
                if (draft == selectedWarehouse) {
                    selectedSource = target;
                }
            }
            for (Warehouse originalSource : originalSourceWarehouses) {
                if (!keptSources.contains(originalSource)) {
                    source.remove(originalSource);
                }
            }
            if (selectedWarehouse != null && selectedWarehouse.isActive && selectedSource != null) {
                for (Warehouse warehouse : source) {
                    if (warehouse != null && warehouse != selectedSource) {
                        warehouse.isActive = false;
                    }
                }
            }
            ensureSourceCategories();
            WarehouseManager.saveWarehouses();
            String selectedName = selectedWarehouse == null ? "" : selectedWarehouse.name;
            loadDraftFromSource(selectedName, selectedSource);
            view = savedView;
            restoreChestSelection(savedChestPos, savedRuleName);
            setStatus("gui.modern.warehouse.u158", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.save_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    @Override
    public void discardDraft() {
        navigationActions.close();
        if (!initialized) {
            return;
        }
        String selectedName = selectedWarehouse == null ? "" : safe(selectedWarehouse.name);
        loadDraftFromSource(selectedName, null);
        view = View.REGION;
        clearTransientInteractionState();
        setStatus("gui.modern.warehouse.u159", ModernUiRenderer.SUBTLE_TEXT);
    }

    @Override
    public boolean isDirty() {
        if (!initialized) {
            return false;
        }
        boolean rawFieldsDirty = activeFieldTextDiffers();
        return rawFieldsDirty || !committedFingerprint.equals(snapshotFingerprint());
    }

    private void performAction(String key) {
        if (key == null) {
            return;
        }
        if ("deposit_slots".equals(key)) {
            depositSlotsExpanded = !depositSlotsExpanded;
        } else if ("deposit_slots_clear".equals(key)) {
            if (selectedChest() != null) selectedChest().depositInventorySlots = new ArrayList<>();
        } else if (key.startsWith("deposit_slot_")) {
            ChestData chest = selectedChest();
            if (chest != null) {
                if (chest.depositInventorySlots == null) chest.depositInventorySlots = new ArrayList<>();
                Integer slot = Integer.valueOf(key.substring("deposit_slot_".length()));
                if (!chest.depositInventorySlots.remove(slot)) chest.depositInventorySlots.add(slot);
            }
        } else if ("spread_after_deposit".equals(key)) {
            if (selectedChest() != null) selectedChest().spreadAfterDeposit = !selectedChest().spreadAfterDeposit;
        } else if ("post_sequence_picker".equals(key)) {
            ChestData chest = selectedChest();
            if (chest != null && postDepositSequencePicker != null) {
                postDepositSequencePicker.open(chest.postDepositSequence);
            }
        } else if ("area_picker".equals(key)) {
            startAreaPicker();
        } else if ("scan".equals(key)) {
            scanSelectedWarehouse();
        } else if ("scan_unscanned".equals(key)) {
            scanUnscannedChests();
        } else if ("region_active".equals(key)) {
            if (selectedWarehouse != null) {
                selectedWarehouse.isActive = !selectedWarehouse.isActive;
                setStatus(selectedWarehouse.isActive ? "gui.modern.warehouse.u160" : "gui.modern.warehouse.u161",
                        selectedWarehouse.isActive ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT);
            }
        } else if ("chest_auto_deposit".equals(key)) {
            ChestData chest = selectedChest();
            if (chest != null) {
                chest.autoDepositEnabled = !chest.autoDepositEnabled;
                setStatus(chest.autoDepositEnabled ? "gui.modern.warehouse.u162" : "gui.modern.warehouse.u163",
                        chest.autoDepositEnabled ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT);
            }
        } else if ("global_deposit".equals(key)) {
            WarehouseEventHandler.oneClickDepositMode = !WarehouseEventHandler.oneClickDepositMode;
            setStatus(WarehouseEventHandler.oneClickDepositMode ? "gui.modern.warehouse.u164" : "gui.modern.warehouse.u165",
                    WarehouseEventHandler.oneClickDepositMode ? ModernUiRenderer.SUCCESS : ModernUiRenderer.SUBTLE_TEXT);
        } else if ("generate_designated".equals(key)) {
            generateDesignatedItems();
        } else if ("clear_designated".equals(key)) {
            requestClearDesignated();
        } else if ("auto_route".equals(key)) {
            runAutoDepositRoute();
        } else if ("withdraw_shift".equals(key)) {
            withdrawShiftQuickMove = true;
        } else if ("withdraw_normal".equals(key)) {
            withdrawShiftQuickMove = false;
        } else if ("withdraw".equals(key)) {
            runWithdraw();
        } else if ("sorting_enabled".equals(key)) {
            ChestData chest = selectedChest();
            if (chest != null) {
                chest.sortEnabled = !chest.sortEnabled;
            }
        } else if ("rule_add".equals(key)) {
            addSortingRule();
        } else if ("rule_duplicate".equals(key)) {
            duplicateSortingRule();
        } else if ("rule_delete".equals(key)) {
            requestDeleteRule();
        } else if ("rule_enabled".equals(key)) {
            SortingRule rule = selectedRule();
            if (rule != null) {
                rule.enabled = !rule.enabled;
            }
        } else if ("match_any".equals(key)) {
            setRuleMatchMode(SortingRule.MatchMode.ANY);
        } else if ("match_all".equals(key)) {
            setRuleMatchMode(SortingRule.MatchMode.ALL);
        } else if ("type_any".equals(key) || "type_any_second".equals(key)) {
            setRuleItemType(SortingRule.ItemType.ANY);
        } else if ("type_shulker".equals(key)) {
            setRuleItemType(SortingRule.ItemType.SHULKER_ONLY);
        } else if ("type_non_shulker".equals(key)) {
            setRuleItemType(SortingRule.ItemType.NON_SHULKER_ONLY);
        } else if ("sort_rules".equals(key)) {
            runRuleSorting();
        } else if ("sort_smart".equals(key)) {
            runSmartSorting();
        } else if ("view_inventory".equals(key)) {
            switchView(View.INVENTORY);
        } else if ("view_policy".equals(key)) {
            switchView(View.POLICY);
        } else if ("view_sorting".equals(key)) {
            switchView(View.SORTING);
        } else if ("open_chest".equals(key)) {
            ChestData chest = selectedChest();
            if (chest == null || chest.pos == null || mc().player == null || mc().world == null) {
                setStatus("gui.modern.warehouse.u166", ModernUiRenderer.WARNING);
            } else {
                boolean started = GoToAndOpenHandler.start(chest.pos);
                setStatus(started ? "gui.modern.warehouse.u167" : "gui.modern.warehouse.u168",
                        started ? ModernUiRenderer.SUCCESS : ModernUiRenderer.WARNING);
            }
        }
    }

    private void setStatus(String message, int color) {
        statusMessage = safe(message);
        statusColor = color;
    }

    private void startAreaPicker() {
        if (selectedWarehouse == null || x1Field == null || z1Field == null || x2Field == null || z2Field == null) {
            setStatus("请先选择一个仓库。", ModernUiRenderer.WARNING);
            return;
        }
        final Warehouse selected = selectedWarehouse;
        AutoFollowAreaPicker.start((first, second) -> {
            if (selectedWarehouse != selected) {
                return;
            }
            x1Field.setText(formatDouble(first.getX() + 0.5D));
            z1Field.setText(formatDouble(first.getZ() + 0.5D));
            x2Field.setText(formatDouble(second.getX() + 0.5D));
            z2Field.setText(formatDouble(second.getZ() + 0.5D));
            setStatus("范围点已回填。", ModernUiRenderer.SUCCESS);
        });
    }

    private void scanSelectedWarehouse() {
        if (selectedWarehouse == null) {
            setStatus("gui.modern.warehouse.u171", ModernUiRenderer.WARNING);
            return;
        }
        if (!applyRegionFields()) {
            return;
        }
        Warehouse source = sourceByDraft.get(selectedWarehouse);
        if (source == null) {
            setStatus("gui.modern.warehouse.u172", ModernUiRenderer.WARNING);
            return;
        }
        source = sourceByDraft.get(selectedWarehouse);
        BlockPos selectedChestPos = selectedChest() == null ? null : selectedChest().pos;
        try {
            applyWarehouseValues(source, selectedWarehouse);
            WarehouseManager.scanForChestsInWarehouse(source);
            applyWarehouseValues(selectedWarehouse, source);
            loadedRegionOwner = null;
            loadRegionFields();
            rebaseCommittedWarehouse(source, selectedWarehouse);
            rebaseCommittedCategoryAdd(selectedWarehouse.category);
            view = View.CHESTS;
            restoreChestSelection(selectedChestPos, "");
            setStatus("gui.modern.warehouse.u173", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.scan_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private void scanUnscannedChests() {
        if (selectedWarehouse == null) {
            setStatus("gui.modern.warehouse.u171", ModernUiRenderer.WARNING);
            return;
        }
        Warehouse source = sourceByDraft.get(selectedWarehouse);
        if (source == null) {
            setStatus("gui.modern.warehouse.u172", ModernUiRenderer.WARNING);
            return;
        }
        int before = 0;
        if (source.chests != null) {
            for (ChestData chest : source.chests) {
                if (chest != null && !chest.hasBeenScanned) {
                    before++;
                }
            }
        }
        boolean started = WarehouseEventHandler.startScanUnscannedChests(source);
        syncScannedChestStates();
        int remaining = 0;
        if (source.chests != null) {
            for (ChestData chest : source.chests) {
                if (chest != null && !chest.hasBeenScanned) {
                    remaining++;
                }
            }
        }
        if (started) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.scan_unscanned", String.valueOf(before)),
                    ModernUiRenderer.SUCCESS);
        } else if (before > remaining) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.scan_unscanned", String.valueOf(before - remaining)),
                    ModernUiRenderer.SUCCESS);
        } else {
            setStatus("gui.modern.warehouse.scan_unscanned_none", ModernUiRenderer.SUBTLE_TEXT);
        }
    }

    private void runAutoDepositRoute() {
        try {
            WarehouseEventHandler.startAutoDepositByHighlights();
            setStatus(WarehouseEventHandler.isAutoDepositRouteRunning() ? "gui.modern.warehouse.u175" : "gui.modern.warehouse.u176",
                    ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.auto_deposit_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private void runWithdraw() {
        if (mc().player == null || mc().currentScreen == null) {
            setStatus("gui.modern.warehouse.u177", ModernUiRenderer.WARNING);
            return;
        }
        try {
            ModUtils.takeAllItemsFromChest(withdrawShiftQuickMove);
            setStatus(withdrawShiftQuickMove ? "gui.modern.warehouse.u178" : "gui.modern.warehouse.u179", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.withdraw_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private void runRuleSorting() {
        ChestData chest = selectedChest();
        if (chest == null) {
            setStatus("gui.modern.warehouse.u180", ModernUiRenderer.WARNING);
            return;
        }
        ContainerChest container = currentChestContainer();
        if (container == null) {
            setStatus("gui.modern.warehouse.u177", ModernUiRenderer.WARNING);
            return;
        }
        try {
            if (!applySortingFields()) {
                return;
            }
            SortingManager.sort(container, copyRules(chest.sortingRules));
            setStatus("gui.modern.warehouse.u181", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.sort_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private void runSmartSorting() {
        ChestData chest = selectedChest();
        ContainerChest container = currentChestContainer();
        if (chest == null || container == null) {
            setStatus("gui.modern.warehouse.u182", ModernUiRenderer.WARNING);
            return;
        }
        try {
            applyPolicyFields();
            SortingManager.sortSmart(container, new HashSet<>(safeSet(chest.designatedItems)));
            setStatus("gui.modern.warehouse.u183", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.smart_sort_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private ContainerChest currentChestContainer() {
        Minecraft minecraft = mc();
        if (minecraft == null || minecraft.player == null || !(minecraft.player.openContainer instanceof ContainerChest)) {
            return null;
        }
        return (ContainerChest) minecraft.player.openContainer;
    }

    private void switchView(View next) {
        if (next == null || next == view) {
            return;
        }
        if (!applyCurrentViewFields()) {
            return;
        }
        clearFieldFocusExcept(null);
        int[] positions = viewPositions.computeIfAbsent(selectedWarehouse, key -> new int[View.values().length]);
        positions[view.ordinal()] = rightScroll;
        view = next;
        rightScroll = positions[view.ordinal()];
        draggingSlots = false;
        draggingDepositSlots = false;
        depositSlotDragStart = -1;

    }

    private void selectAllCategory() {
        if (!applyCurrentViewFields()) {
            return;
        }
        selectedCategory = CATEGORY_ALL;
        if (selectedWarehouse == null && !warehouses.isEmpty()) {
            selectedWarehouse = warehouses.get(0);
        }
        resetSelectionFields();
    }

    private void selectCategory(String category) {
        if (!applyCurrentViewFields()) {
            return;
        }
        selectedCategory = normalizeCategory(category);
        if (selectedWarehouse == null || !selectedCategory.equalsIgnoreCase(normalizeCategory(selectedWarehouse.category))) {
            Warehouse first = firstWarehouseInCategory(selectedCategory);
            selectedWarehouse = first;
        }
        resetSelectionFields();
    }

    private void selectWarehouse(Warehouse warehouse) {
        if (warehouse == null || !applyCurrentViewFields()) {
            return;
        }
        selectedWarehouse = warehouse;
        selectedCategory = normalizeCategory(warehouse.category);
        selectedChestIndex = -1;
        selectedRuleIndex = -1;
        view = View.REGION;
        rightScroll = 0;
        chestListScroll = 0;
        inventoryScroll = 0;
        ruleListScroll = 0;
        resetSelectionFields();
        setStatus(ModernFormI18n.tr("gui.modern.wb.fmt.selected", safe(warehouse.name)), ModernUiRenderer.SUBTLE_TEXT);
    }

    private void selectChest(int index) {
        if (!applyCurrentViewFields()) {
            return;
        }
        ChestData chest = chestAt(selectedWarehouse, index);
        if (chest == null) {
            return;
        }
        selectedChestIndex = index;
        selectedRuleIndex = -1;
        loadedPolicyOwner = null;
        loadedRuleOwner = null;
        inventoryScroll = 0;
        ruleListScroll = 0;
        setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.selected_chest", formatPos(chest.pos)), ModernUiRenderer.SUBTLE_TEXT);
    }

    private void restoreChestSelection(BlockPos position, String ruleName) {
        if (position == null || selectedWarehouse == null || selectedWarehouse.chests == null) {
            selectedChestIndex = -1;
            selectedRuleIndex = -1;
            return;
        }
        selectedChestIndex = -1;
        for (int i = 0; i < selectedWarehouse.chests.size(); i++) {
            ChestData chest = selectedWarehouse.chests.get(i);
            if (chest != null && position.equals(chest.pos)) {
                selectedChestIndex = i;
                break;
            }
        }
        selectedRuleIndex = -1;
        if (selectedChestIndex >= 0 && view == View.SORTING) {
            ChestData chest = selectedChest();
            if (chest != null && chest.sortingRules != null) {
                for (int i = 0; i < chest.sortingRules.size(); i++) {
                    SortingRule rule = chest.sortingRules.get(i);
                    if (rule != null && safe(ruleName).equals(safe(rule.name))) {
                        selectedRuleIndex = i;
                        break;
                    }
                }
            }
        }
        loadedPolicyOwner = null;
        loadedRuleOwner = null;
    }

    private void selectRule(int index) {
        ChestData chest = selectedChest();
        if (chest == null || chest.sortingRules == null || index < 0 || index >= chest.sortingRules.size()
                || !applySortingFields()) {
            return;
        }
        selectedRuleIndex = index;
        loadedRuleOwner = null;
        setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.selected_rule",
                safe(chest.sortingRules.get(index).name)), ModernUiRenderer.SUBTLE_TEXT);
    }

    private void resetSelectionFields() {
        clearFieldFocusExcept(null);
        loadedRegionOwner = null;
        loadedPolicyOwner = null;
        loadedRuleOwner = null;
        selectedChestIndex = -1;
        selectedRuleIndex = -1;
        chestSearchField.setText("");
        inventorySearchField.setText("");
        designatedField.setText("");
        ruleNameField.setText("");
        ruleKeywordsField.setText("");
        loadRegionFields();
    }

    private void ensureLoadedEditorOwner() {
        if (selectedWarehouse != null && view == View.REGION) {
            ensureRegionFields();
        }
        if (view == View.POLICY) {
            ensurePolicyFields();
        }
        if (view == View.SORTING) {
            SortingRule rule = selectedRule();
            if (rule != null) {
                ensureRuleFields(rule);
            }
        }
    }

    private void ensureRegionFields() {
        if (selectedWarehouse != null && loadedRegionOwner != selectedWarehouse) {
            loadRegionFields();
        }
    }

    private void loadRegionFields() {
        loadedRegionOwner = selectedWarehouse;
        if (selectedWarehouse == null) {
            return;
        }
        nameField.setText(safe(selectedWarehouse.name));
        categoryField.setText(normalizeCategory(selectedWarehouse.category));
        x1Field.setText(formatCoordinateInput(selectedWarehouse.x1));
        z1Field.setText(formatCoordinateInput(selectedWarehouse.z1));
        x2Field.setText(formatCoordinateInput(selectedWarehouse.x2));
        z2Field.setText(formatCoordinateInput(selectedWarehouse.z2));
    }

    private void ensurePolicyFields() {
        ChestData chest = selectedChest();
        if (chest == null) {
            return;
        }
        if (loadedPolicyOwner != chest) {
            loadingPolicyFields = true;
            designatedField.setText(String.join(", ", WarehouseEventHandler.orderedDepositNames(chest)));
            spreadNamesField.setText(safe(chest.spreadItemNames));
            postDepositSequenceField.setText(safe(chest.postDepositSequence));
            loadingPolicyFields = false;
            policyItemsEdited = false;
            policyItemsClearRequested = false;
            loadedPolicyOwner = chest;
        }
    }

    private void ensureSortingRules(ChestData chest) {
        if (chest == null) {
            return;
        }
        if (chest.sortingRules == null) {
            chest.sortingRules = new ArrayList<>();
        }
        if (selectedRuleIndex >= chest.sortingRules.size()) {
            selectedRuleIndex = chest.sortingRules.isEmpty() ? -1 : chest.sortingRules.size() - 1;
        }
    }

    private void ensureRuleFields(SortingRule rule) {
        if (rule == null) {
            return;
        }
        if (loadedRuleOwner != rule) {
            loadedRuleOwner = rule;
            ruleNameField.setText(safe(rule.name));
            ruleKeywordsField.setText(join(safeList(rule.itemKeywords), ", "));
        }
    }

    private boolean applyCurrentViewFields() {
        if (view == View.REGION) {
            return applyRegionFields();
        }
        if (view == View.POLICY) {
            return applyPolicyFields();
        }
        if (view == View.SORTING) {
            return applySortingFields();
        }
        return true;
    }

    private boolean applyRegionFields() {
        if (selectedWarehouse == null || loadedRegionOwner != selectedWarehouse) {
            return true;
        }
        Double x1 = parseCoordinate(x1Field.getText());
        Double z1 = parseCoordinate(z1Field.getText());
        Double x2 = parseCoordinate(x2Field.getText());
        Double z2 = parseCoordinate(z2Field.getText());
        if (x1 == null || z1 == null || x2 == null || z2 == null) {
            setStatus("gui.modern.warehouse.u184", ModernUiRenderer.WARNING);
            return false;
        }
        selectedWarehouse.name = safe(nameField.getText()).trim();
        selectedWarehouse.category = normalizeCategory(categoryField.getText());
        selectedWarehouse.x1 = x1.doubleValue();
        selectedWarehouse.z1 = z1.doubleValue();
        selectedWarehouse.x2 = x2.doubleValue();
        selectedWarehouse.z2 = z2.doubleValue();
        selectedWarehouse.updateBounds();
        addCategoryLocally(selectedWarehouse.category);
        return true;
    }

    private boolean applyPolicyFields() {
        ChestData chest = selectedChest();
        if (chest == null || designatedField == null || loadedPolicyOwner != chest) {
            return true;
        }
        if (policyItemsEdited || policyItemsClearRequested) {
            chest.designatedItems = new HashSet<>(splitStrings(designatedField.getText()));
            chest.depositItemOrder = new ArrayList<>(splitStrings(designatedField.getText()));
            chest.depositItemsConfigured = true;
        } else if ((designatedField.getText() == null || designatedField.getText().trim().isEmpty())
                && chest.designatedItems != null && !chest.designatedItems.isEmpty()) {
            // A render-only field reset must never erase a saved policy.
            loadingPolicyFields = true;
            designatedField.setText(String.join(", ", WarehouseEventHandler.orderedDepositNames(chest)));
            loadingPolicyFields = false;
        }
        policyItemsEdited = false;
        policyItemsClearRequested = false;
        chest.spreadItemNames = spreadNamesField.getText().trim();
        return true;
    }

    private boolean applySortingFields() {
        SortingRule rule = selectedRule();
        if (rule == null || loadedRuleOwner != rule) {
            return true;
        }
        rule.name = safe(ruleNameField.getText()).trim();
        rule.itemKeywords = splitStrings(ruleKeywordsField.getText());
        if (isBlank(rule.name)) {
            setStatus("gui.modern.warehouse.u185", ModernUiRenderer.WARNING);
            return false;
        }
        if (rule.targetSlots == null) {
            rule.targetSlots = new ArrayList<>();
        }
        return true;
    }

    private boolean activeFieldTextDiffers() {
        if (view == View.REGION && selectedWarehouse != null && loadedRegionOwner == selectedWarehouse) {
            return !safe(nameField.getText()).trim().equals(safe(selectedWarehouse.name))
                    || !normalizeCategory(categoryField.getText()).equals(normalizeCategory(selectedWarehouse.category))
                    || !coordinateTextMatches(x1Field, selectedWarehouse.x1)
                    || !coordinateTextMatches(z1Field, selectedWarehouse.z1)
                    || !coordinateTextMatches(x2Field, selectedWarehouse.x2)
                    || !coordinateTextMatches(z2Field, selectedWarehouse.z2);
        }
        if (view == View.POLICY && selectedChest() != null && loadedPolicyOwner == selectedChest()) {
            return !String.join(", ", WarehouseEventHandler.orderedDepositNames(selectedChest())).equals(designatedField.getText())
                    || !safe(selectedChest().spreadItemNames).equals(spreadNamesField.getText());
        }
        if (view == View.SORTING && selectedRule() != null && loadedRuleOwner == selectedRule()) {
            SortingRule rule = selectedRule();
            return !safe(rule.name).equals(ruleNameField.getText())
                    || !join(safeList(rule.itemKeywords), ", ").equals(ruleKeywordsField.getText());
        }
        return false;
    }

    private void addWarehouse() {
        if (!applyCurrentViewFields()) {
            return;
        }
        Warehouse warehouse = new Warehouse();
        warehouse.name = uniqueWarehouseName(I18n.format("gui.warehouse.edit.new_name"));
        warehouse.category = isConcreteCategory(selectedCategory) ? selectedCategory : CATEGORY_DEFAULT;
        warehouse.isActive = false;
        warehouse.chests = new CopyOnWriteArrayList<>();
        warehouse.updateBounds();
        warehouses.add(warehouse);
        addCategoryLocally(warehouse.category);
        selectedWarehouse = warehouse;
        selectedCategory = warehouse.category;
        view = View.REGION;
        selectedChestIndex = -1;
        selectedRuleIndex = -1;
        rightScroll = 0;
        resetSelectionFields();
        setStatus("gui.modern.warehouse.u186", ModernUiRenderer.SUCCESS);
    }

    private void duplicateWarehouse() {
        if (selectedWarehouse == null || !applyCurrentViewFields()) {
            if (selectedWarehouse == null) {
                setStatus("gui.modern.warehouse.u171", ModernUiRenderer.WARNING);
            }
            return;
        }
        Warehouse copy = copyWarehouse(selectedWarehouse);
        copy.name = uniqueWarehouseName(ModernFormI18n.tr("gui.modern.wb.fmt.copy", safe(selectedWarehouse.name)));
        warehouses.add(Math.min(warehouses.size(), warehouses.indexOf(selectedWarehouse) + 1), copy);
        selectedWarehouse = copy;
        selectedCategory = normalizeCategory(copy.category);
        selectedChestIndex = -1;
        selectedRuleIndex = -1;
        sourceByDraft.remove(copy);
        view = View.REGION;
        resetSelectionFields();
        setStatus("gui.modern.warehouse.u187", ModernUiRenderer.SUCCESS);
    }

    private void requestDeleteCurrent() {
        if (selectedWarehouse != null) {
            requestDeleteWarehouse(selectedWarehouse);
        } else if (isConcreteCategory(selectedCategory)) {
            requestDeleteCategory(selectedCategory);
        } else {
            setStatus("gui.modern.warehouse.u188", ModernUiRenderer.WARNING);
        }
    }

    private void requestDeleteWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            setStatus("gui.modern.warehouse.u171", ModernUiRenderer.WARNING);
            return;
        }
        confirmationMode = ConfirmationMode.DELETE_WAREHOUSE;
        confirmationWarehouse = warehouse;
        confirmationCategory = "";
        confirmationRule = null;
        confirmationChest = null;
    }

    private void requestDeleteCategory(String category) {
        if (!isConcreteCategory(category)) {
            setStatus("gui.modern.warehouse.u189", ModernUiRenderer.WARNING);
            return;
        }
        confirmationMode = ConfirmationMode.DELETE_CATEGORY;
        confirmationCategory = normalizeCategory(category);
        confirmationWarehouse = null;
        confirmationRule = null;
        confirmationChest = null;
    }

    private void requestDeleteRule() {
        SortingRule rule = selectedRule();
        if (rule == null) {
            setStatus("gui.modern.warehouse.u190", ModernUiRenderer.WARNING);
            return;
        }
        confirmationMode = ConfirmationMode.DELETE_RULE;
        confirmationRule = rule;
        confirmationWarehouse = null;
        confirmationChest = selectedChest();
        confirmationCategory = "";
    }

    private void requestDeleteChest(ChestData chest) {
        if (chest == null || selectedWarehouse == null) {
            setStatus("gui.modern.warehouse.u171", ModernUiRenderer.WARNING);
            return;
        }
        confirmationMode = ConfirmationMode.DELETE_CHEST;
        confirmationChest = chest;
        confirmationWarehouse = selectedWarehouse;
        confirmationRule = null;
        confirmationCategory = "";
    }

    private void requestClearDesignated() {
        ChestData chest = selectedChest();
        if (chest == null || designatedCount(chest) == 0) {
            setStatus("gui.modern.warehouse.u191", ModernUiRenderer.SUBTLE_TEXT);
            return;
        }
        confirmationMode = ConfirmationMode.CLEAR_DESIGNATED;
        confirmationChest = chest;
        confirmationWarehouse = null;
        confirmationRule = null;
        confirmationCategory = "";
    }

    private void requestReload() {
        confirmationMode = ConfirmationMode.RELOAD;
        confirmationWarehouse = null;
        confirmationChest = null;
        confirmationRule = null;
        confirmationCategory = "";
    }

    private String confirmationTitle() {
        switch (confirmationMode) {
        case DELETE_WAREHOUSE:
            return "gui.modern.warehouse.u192";
        case DELETE_CATEGORY:
            return "gui.modern.warehouse.u193";
        case DELETE_CHEST:
            return "gui.modern.warehouse.delete_chest_title";
        case DELETE_RULE:
            return "gui.modern.warehouse.u194";
        case CLEAR_DESIGNATED:
            return "gui.modern.warehouse.u195";
        case RELOAD:
            return "gui.modern.warehouse.u196";
        case NONE:
        default:
            return "gui.modern.warehouse.u197";
        }
    }

    private String confirmationMessage() {
        switch (confirmationMode) {
        case DELETE_WAREHOUSE:
            return ModernFormI18n.tr("gui.modern.warehouse.fmt.delete_area",
                    confirmationWarehouse == null ? "" : safe(confirmationWarehouse.name));
        case DELETE_CATEGORY:
            return ModernFormI18n.tr("gui.modern.warehouse.fmt.delete_group", confirmationCategory);
        case DELETE_CHEST:
            return ModernFormI18n.tr("gui.modern.warehouse.fmt.delete_chest", confirmationChest == null ? "" : formatPos(confirmationChest.pos));
        case DELETE_RULE:
            return ModernFormI18n.tr("gui.modern.warehouse.fmt.delete_rule",
                    confirmationRule == null ? "" : safe(confirmationRule.name));
        case CLEAR_DESIGNATED:
            return "gui.modern.warehouse.u203";
        case RELOAD:
            return "gui.modern.warehouse.u204";
        case NONE:
        default:
            return "";
        }
    }

    private void confirmDestructiveAction() {
        ConfirmationMode pending = confirmationMode;
        Warehouse warehouse = confirmationWarehouse;
        SortingRule rule = confirmationRule;
        String category = confirmationCategory;
        ChestData chest = confirmationChest;
        clearConfirmation();
        if (pending == ConfirmationMode.DELETE_WAREHOUSE) {
            deleteWarehouseNow(warehouse);
        } else if (pending == ConfirmationMode.DELETE_CATEGORY) {
            deleteCategoryNow(category);
        } else if (pending == ConfirmationMode.DELETE_CHEST) {
            deleteChestNow(warehouse, chest);
        } else if (pending == ConfirmationMode.DELETE_RULE) {
            deleteRuleNow(chest, rule);
        } else if (pending == ConfirmationMode.CLEAR_DESIGNATED) {
            if (chest != null) {
                policyItemsClearRequested = true;
                chest.designatedItems = new HashSet<>();
                chest.depositItemOrder = new ArrayList<>();
                loadingPolicyFields = true;
                designatedField.setText("");
                loadingPolicyFields = false;
                loadedPolicyOwner = null;
                ensurePolicyFields();
                setStatus("gui.modern.warehouse.u205", ModernUiRenderer.SUCCESS);
            }
        } else if (pending == ConfirmationMode.RELOAD) {
            reloadFromSource();
        }
    }

    private void clearConfirmation() {
        confirmationMode = ConfirmationMode.NONE;
        confirmationWarehouse = null;
        confirmationChest = null;
        confirmationRule = null;
        confirmationCategory = "";
        confirmationBounds = null;
        confirmationYesBounds = null;
        confirmationNoBounds = null;
    }

    private void deleteWarehouseNow(Warehouse warehouse) {
        int removedIndex = warehouse == null ? -1 : warehouses.indexOf(warehouse);
        if (warehouse == null || removedIndex < 0 || !warehouses.remove(warehouse)) {
            setStatus("gui.modern.warehouse.u206", ModernUiRenderer.WARNING);
            return;
        }
        sourceByDraft.remove(warehouse);
        if (warehouse == selectedWarehouse) {
            int index = Math.min(warehouses.size() - 1, Math.max(0, removedIndex));
            selectedWarehouse = warehouses.isEmpty() ? null : warehouses.get(index);
            selectedCategory = selectedWarehouse == null ? CATEGORY_ALL : normalizeCategory(selectedWarehouse.category);
            selectedChestIndex = -1;
            selectedRuleIndex = -1;
            loadedRegionOwner = null;
            loadedPolicyOwner = null;
            loadedRuleOwner = null;
            if (selectedWarehouse != null) {
                loadRegionFields();
            }
        }
        setStatus("gui.modern.warehouse.u207", ModernUiRenderer.SUCCESS);
    }

    private void deleteCategoryNow(String category) {
        if (!isConcreteCategory(category)) {
            return;
        }
        if (!WarehouseManager.deleteCategory(category)) {
            setStatus("gui.modern.warehouse.u208", ModernUiRenderer.WARNING);
            return;
        }
        removeCategoryLocally(category);
        for (Warehouse warehouse : warehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(category)) {
                warehouse.category = CATEGORY_DEFAULT;
            }
        }
        rebaseCommittedCategoryDelete(category);
        selectedCategory = CATEGORY_ALL;
        if (selectedWarehouse != null) {
            loadRegionFields();
        }
        setStatus("gui.modern.warehouse.u209", ModernUiRenderer.SUCCESS);
    }

    private void deleteRuleNow(ChestData chest, SortingRule rule) {
        if (chest == null || rule == null || chest.sortingRules == null || !chest.sortingRules.remove(rule)) {
            setStatus("gui.modern.warehouse.u210", ModernUiRenderer.WARNING);
            return;
        }
        selectedRuleIndex = Math.min(selectedRuleIndex, chest.sortingRules.size() - 1);
        loadedRuleOwner = null;
        setStatus("gui.modern.warehouse.u211", ModernUiRenderer.SUCCESS);
    }

    private void deleteChestNow(Warehouse warehouse, ChestData chest) {
        int removedIndex = warehouse == null || warehouse.chests == null || chest == null
                ? -1 : warehouse.chests.indexOf(chest);
        if (removedIndex < 0 || !warehouse.chests.remove(chest)) {
            setStatus("gui.modern.warehouse.delete_chest_failed", ModernUiRenderer.WARNING);
            return;
        }
        if (warehouse == selectedWarehouse) {
            if (selectedChestIndex == removedIndex) {
                selectedChestIndex = Math.min(selectedChestIndex, warehouse.chests.size() - 1);
            } else if (selectedChestIndex > removedIndex) {
                selectedChestIndex--;
            }
            selectedRuleIndex = -1;
            loadedPolicyOwner = null;
            loadedRuleOwner = null;
        }
        setStatus("gui.modern.warehouse.delete_chest_done", ModernUiRenderer.SUCCESS);
    }

    private void openCategoryInput(DialogMode mode, String initial) {
        dialogMode = mode == null ? DialogMode.NONE : mode;
        if (dialogMode == DialogMode.NONE) {
            return;
        }
        dialogField.setText(initial == null ? "" : initial);
        dialogField.setFocused(true);
        dialogBounds = null;
        dialogConfirmBounds = null;
        dialogCancelBounds = null;
        clearFieldFocusExcept(dialogField);
    }

    private void closeDialog() {
        dialogMode = DialogMode.NONE;
        dialogField.setFocused(false);
        dialogBounds = null;
        dialogConfirmBounds = null;
        dialogCancelBounds = null;
    }

    private void commitDialog() {
        String value = safe(dialogField.getText()).trim();
        if (value.isEmpty()) {
            setStatus("gui.modern.warehouse.u212", ModernUiRenderer.WARNING);
            return;
        }
        if (dialogMode == DialogMode.ADD_CATEGORY) {
            if (categoryExists(value)) {
                setStatus("gui.modern.warehouse.u213", ModernUiRenderer.WARNING);
                return;
            }
            if (!WarehouseManager.addCategory(value)) {
                setStatus("gui.modern.warehouse.u214", ModernUiRenderer.WARNING);
                return;
            }
            addCategoryLocally(value);
            rebaseCommittedCategoryAdd(value);
            selectedCategory = value;
            collapsedCategories.remove(value);
            setStatus("gui.modern.warehouse.u215", ModernUiRenderer.SUCCESS);
        } else if (dialogMode == DialogMode.RENAME_CATEGORY) {
            String old = contextCategory;
            if (!isConcreteCategory(old)) {
                closeDialog();
                return;
            }
            if (!value.equalsIgnoreCase(old) && categoryExists(value)) {
                setStatus("gui.modern.warehouse.u213", ModernUiRenderer.WARNING);
                return;
            }
            if (!WarehouseManager.renameCategory(old, value)) {
                setStatus("gui.modern.warehouse.u216", ModernUiRenderer.WARNING);
                return;
            }
            replaceCategoryLocally(old, value);
            for (Warehouse warehouse : warehouses) {
                if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(old)) {
                    warehouse.category = value;
                }
            }
            rebaseCommittedCategoryRename(old, value);
            selectedCategory = value;
            if (selectedWarehouse != null) {
                loadedRegionOwner = null;
                loadRegionFields();
            }
            setStatus("gui.modern.warehouse.u217", ModernUiRenderer.SUCCESS);
        }
        closeDialog();
    }

    private void addSortingRule() {
        ChestData chest = selectedChest();
        if (chest == null) {
            setStatus("gui.modern.warehouse.u180", ModernUiRenderer.WARNING);
            return;
        }
        if (!applySortingFields()) {
            return;
        }
        ensureSortingRules(chest);
        SortingRule rule = new SortingRule();
        rule.name = uniqueRuleName(chest, ModernFormI18n.tr("gui.modern.warehouse.u218"));
        rule.enabled = true;
        rule.matchMode = SortingRule.MatchMode.ANY;
        rule.itemType = SortingRule.ItemType.ANY;
        rule.targetSlots = new ArrayList<>();
        rule.itemKeywords = new ArrayList<>();
        chest.sortingRules.add(rule);
        selectedRuleIndex = chest.sortingRules.size() - 1;
        loadedRuleOwner = null;
        setStatus("gui.modern.warehouse.u219", ModernUiRenderer.SUCCESS);
    }

    private void duplicateSortingRule() {
        ChestData chest = selectedChest();
        SortingRule source = selectedRule();
        if (chest == null || source == null) {
            setStatus("gui.modern.warehouse.u190", ModernUiRenderer.WARNING);
            return;
        }
        if (!applySortingFields()) {
            return;
        }
        SortingRule copy = copySortingRule(source);
        copy.name = uniqueRuleName(chest, ModernFormI18n.tr("gui.modern.wb.fmt.copy", safe(source.name)));
        int index = Math.max(0, Math.min(chest.sortingRules.size(), selectedRuleIndex + 1));
        chest.sortingRules.add(index, copy);
        selectedRuleIndex = index;
        loadedRuleOwner = null;
        setStatus("gui.modern.warehouse.u220", ModernUiRenderer.SUCCESS);
    }

    private void setRuleMatchMode(SortingRule.MatchMode mode) {
        SortingRule rule = selectedRule();
        if (rule != null && mode != null) {
            rule.matchMode = mode;
        }
    }

    private void setRuleItemType(SortingRule.ItemType type) {
        SortingRule rule = selectedRule();
        if (rule != null && type != null) {
            rule.itemType = type;
        }
    }

    private void generateDesignatedItems() {
        ChestData chest = selectedChest();
        if (chest == null || !chest.hasBeenScanned) {
            setStatus("gui.modern.warehouse.u221", ModernUiRenderer.WARNING);
            return;
        }
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (ItemStack stack : chest.getSnapshotContents(54)) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof ItemShulkerBox) {
                NBTTagCompound nbt = stack.getSubCompound("BlockEntityTag");
                if (nbt != null && nbt.hasKey("Items", 9)) {
                    NonNullList<ItemStack> inside = NonNullList.withSize(27, ItemStack.EMPTY);
                    ItemStackHelper.loadAllItems(nbt, inside);
                    for (ItemStack nested : inside) {
                        if (nested != null && !nested.isEmpty()) {
                            found.add(nested.getDisplayName());
                        }
                    }
                }
            } else {
                found.add(stack.getDisplayName());
            }
        }
        chest.designatedItems = new HashSet<>(found);
        loadedPolicyOwner = null;
        ensurePolicyFields();
        setStatus("gui.modern.warehouse.u222", ModernUiRenderer.SUCCESS);
    }

    private void selectSlotRectangle(int first, int second) {
        SortingRule rule = selectedRule();
        if (rule == null || first < 0 || second < 0) {
            return;
        }
        int firstColumn = first % 9;
        int firstRow = first / 9;
        int secondColumn = second % 9;
        int secondRow = second / 9;
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        for (int row = Math.min(firstRow, secondRow); row <= Math.max(firstRow, secondRow); row++) {
            for (int column = Math.min(firstColumn, secondColumn); column <= Math.max(firstColumn, secondColumn); column++) {
                selected.add(row * 9 + column);
            }
        }
        rule.targetSlots = new ArrayList<>(selected);
    }

    private int depositSlotAt(int mouseX, int mouseY) {
        if (depositSlotGridBounds == null || depositSlotCellSize <= 0
                || !depositSlotGridBounds.contains(mouseX, mouseY)) {
            return -1;
        }
        int step = Math.max(1, depositSlotCellSize + depositSlotGap);
        int column = (mouseX - depositSlotGridBounds.x) / step;
        int row = (mouseY - depositSlotGridBounds.y) / step;
        if (column < 0 || column >= 9 || row < 0 || row >= 4) {
            return -1;
        }
        int localX = (mouseX - depositSlotGridBounds.x) % step;
        int localY = (mouseY - depositSlotGridBounds.y) % step;
        if (localX >= depositSlotCellSize || localY >= depositSlotCellSize) {
            return -1;
        }
        return row * 9 + column;
    }

    private int visibleToRawDepositSlot(int visibleSlot) {
        // The display follows the Minecraft inventory layout: main inventory
        // slots first, followed by the nine hotbar slots.
        return visibleSlot < 27 ? visibleSlot + 9 : visibleSlot - 27;
    }

    private LinkedHashSet<Integer> selectedDepositSlotSet(ChestData chest) {
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        if (chest != null && chest.depositInventorySlots != null) {
            for (Integer slot : chest.depositInventorySlots) {
                if (slot != null && slot.intValue() >= 0 && slot.intValue() < 36) {
                    selected.add(slot);
                }
            }
        }
        return selected;
    }

    private void applyDepositSlotRectangle(ChestData chest, int firstVisible, int secondVisible) {
        if (chest == null || firstVisible < 0 || secondVisible < 0) {
            return;
        }
        int firstColumn = firstVisible % 9;
        int firstRow = firstVisible / 9;
        int secondColumn = secondVisible % 9;
        int secondRow = secondVisible / 9;
        LinkedHashSet<Integer> selected = selectedDepositSlotSet(chest);
        for (int row = Math.min(firstRow, secondRow); row <= Math.max(firstRow, secondRow); row++) {
            for (int column = Math.min(firstColumn, secondColumn); column <= Math.max(firstColumn, secondColumn); column++) {
                int rawSlot = visibleToRawDepositSlot(row * 9 + column);
                if (depositSlotDragAddMode) {
                    selected.add(rawSlot);
                } else {
                    selected.remove(rawSlot);
                }
            }
        }
        chest.depositInventorySlots = new ArrayList<>(selected);
    }

    private int slotAt(int mouseX, int mouseY) {
        if (slotGridBounds == null || slotCellSize <= 0 || !slotGridBounds.contains(mouseX, mouseY)) {
            return -1;
        }
        int column = (mouseX - slotGridBounds.x) / Math.max(1, slotCellSize + slotGap);
        int row = (mouseY - slotGridBounds.y) / Math.max(1, slotCellSize + slotGap);
        if (column < 0 || column >= 9 || row < 0 || row >= 6) {
            return -1;
        }
        int localX = (mouseX - slotGridBounds.x) % Math.max(1, slotCellSize + slotGap);
        int localY = (mouseY - slotGridBounds.y) % Math.max(1, slotCellSize + slotGap);
        if (localX >= slotCellSize || localY >= slotCellSize) {
            return -1;
        }
        return row * 9 + column;
    }

    private LinkedHashSet<Integer> selectedSlotSet(SortingRule rule) {
        LinkedHashSet<Integer> selected = new LinkedHashSet<>();
        if (rule != null && rule.targetSlots != null) {
            for (Integer slot : rule.targetSlots) {
                if (slot != null && slot.intValue() >= 0 && slot.intValue() < 54) {
                    selected.add(slot);
                }
            }
        }
        return selected;
    }

    private boolean beginInnerScrollbarDrag(int mouseX, int mouseY) {
        return chestInnerScrollbar.beginDrag(mouseX, mouseY) || inventoryInnerScrollbar.beginDrag(mouseX, mouseY)
                || ruleInnerScrollbar.beginDrag(mouseX, mouseY);
    }

    private boolean applyAnyScrollbarDrag(int mouseX, int mouseY) {
        ModernHoverScrollbar[] bars = hoverScrollBars();
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].isDragging()) {
                bars[i].applyDrag(mouseX, mouseY);
                return true;
            }
        }
        return false;
    }

    private boolean isAnyScrollbarDragging() {
        ModernHoverScrollbar[] bars = hoverScrollBars();
        for (int i = 0; i < bars.length; i++) {
            if (bars[i].isDragging()) {
                return true;
            }
        }
        return false;
    }

    private void endAllScrollbarDrags() {
        ModernHoverScrollbar[] bars = hoverScrollBars();
        for (int i = 0; i < bars.length; i++) {
            bars[i].endDrag();
        }
    }

    private ModernHoverScrollbar[] hoverScrollBars() {
        return new ModernHoverScrollbar[] {
                navigationScrollbar, rightScrollbar, chestInnerScrollbar, inventoryInnerScrollbar, ruleInnerScrollbar
        };
    }

    private void loadDraftFromSource(String selectedName, Warehouse preferredSource) {
        warehouses.clear();
        categories.clear();
        committedWarehouses.clear();
        committedCategories.clear();
        committedSourceByDraft.clear();
        originalSourceWarehouses.clear();
        sourceByDraft.clear();

        List<Warehouse> source = WarehouseManager.warehouses;
        if (source != null) {
            for (Warehouse originalSource : source) {
                if (originalSource == null) {
                    continue;
                }
                originalSource.updateBounds();
                originalSourceWarehouses.add(originalSource);
                Warehouse draft = copyWarehouse(originalSource);
                warehouses.add(draft);
                sourceByDraft.put(draft, originalSource);
                Warehouse committed = copyWarehouse(originalSource);
                committedWarehouses.add(committed);
                committedSourceByDraft.put(committed, originalSource);
            }
        }
        List<String> sourceCategories = WarehouseManager.getCategoriesSnapshot();
        if (sourceCategories != null) {
            for (String category : sourceCategories) {
                addCategoryLocally(category);
            }
        }
        for (Warehouse warehouse : warehouses) {
            if (warehouse != null) {
                addCategoryLocally(warehouse.category);
            }
        }
        if (categories.isEmpty()) {
            categories.add(CATEGORY_DEFAULT);
        }

        selectedWarehouse = null;
        if (preferredSource != null) {
            for (Warehouse draft : warehouses) {
                if (sourceByDraft.get(draft) == preferredSource) {
                    selectedWarehouse = draft;
                    break;
                }
            }
        }
        if (selectedWarehouse == null && !isBlank(selectedName)) {
            for (Warehouse draft : warehouses) {
                if (safe(selectedName).equalsIgnoreCase(safe(draft.name))) {
                    selectedWarehouse = draft;
                    break;
                }
            }
        }
        if (selectedWarehouse == null && !warehouses.isEmpty()) {
            selectedWarehouse = warehouses.get(0);
        }
        selectedCategory = selectedWarehouse == null ? CATEGORY_ALL : normalizeCategory(selectedWarehouse.category);
        selectedChestIndex = -1;
        selectedRuleIndex = -1;
        loadedRegionOwner = null;
        loadedPolicyOwner = null;
        loadedRuleOwner = null;
        if (selectedWarehouse != null && nameField != null) {
            loadRegionFields();
        }
        committedCategories.addAll(categories);
        committedFingerprint = committedSnapshotFingerprint();
    }

    private void reloadFromSource() {
        try {
            WarehouseManager.loadWarehouses();
            loadDraftFromSource(null, null);
            view = View.REGION;
            clearTransientInteractionState();
            setStatus("gui.modern.warehouse.u223", ModernUiRenderer.SUCCESS);
        } catch (RuntimeException exception) {
            setStatus(ModernFormI18n.tr("gui.modern.warehouse.fmt.reload_fail", safe(exception.getMessage())), ModernUiRenderer.WARNING);
        }
    }

    private void clearTransientInteractionState() {
        contextBounds = null;
        contextMenu.clear();
        clearConfirmation();
        closeDialog();
        draggingDivider = false;
        endAllScrollbarDrags();
        draggingSlots = false;
        slotDragStart = -1;
        draggingDepositSlots = false;
        depositSlotDragStart = -1;
        depositSlotGridBounds = null;
        navigationScroll = 0;
        rightScroll = 0;
        chestListScroll = 0;
        inventoryScroll = 0;
        ruleListScroll = 0;
        clearFieldFocusExcept(null);
    }

    private void ensureSourceCategories() {
        List<String> existing = WarehouseManager.getCategoriesSnapshot();
        for (String category : categories) {
            if (!containsCategory(existing, category)) {
                WarehouseManager.addCategory(category);
                existing = WarehouseManager.getCategoriesSnapshot();
            }
        }
    }

    private void addCategoryLocally(String category) {
        String normalized = normalizeCategory(category);
        for (String existing : categories) {
            if (existing.equalsIgnoreCase(normalized)) {
                return;
            }
        }
        categories.add(normalized);
    }

    private void removeCategoryLocally(String category) {
        for (int i = categories.size() - 1; i >= 0; i--) {
            if (categories.get(i).equalsIgnoreCase(normalizeCategory(category))) {
                categories.remove(i);
            }
        }
        if (categories.isEmpty()) {
            categories.add(CATEGORY_DEFAULT);
        }
    }

    private void replaceCategoryLocally(String oldCategory, String newCategory) {
        String normalizedNew = normalizeCategory(newCategory);
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).equalsIgnoreCase(normalizeCategory(oldCategory))) {
                categories.set(i, normalizedNew);
                return;
            }
        }
        addCategoryLocally(normalizedNew);
    }

    private void rebaseCommittedCategoryAdd(String category) {
        if (!containsCategory(committedCategories, category)) {
            committedCategories.add(normalizeCategory(category));
        }
        committedFingerprint = committedSnapshotFingerprint();
    }

    private void rebaseCommittedCategoryRename(String oldCategory, String newCategory) {
        String old = normalizeCategory(oldCategory);
        String replacement = normalizeCategory(newCategory);
        for (int i = 0; i < committedCategories.size(); i++) {
            if (normalizeCategory(committedCategories.get(i)).equalsIgnoreCase(old)) {
                committedCategories.set(i, replacement);
            }
        }
        for (Warehouse warehouse : committedWarehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(old)) {
                warehouse.category = replacement;
            }
        }
        committedFingerprint = committedSnapshotFingerprint();
    }

    private void rebaseCommittedCategoryDelete(String category) {
        String normalized = normalizeCategory(category);
        for (int i = committedCategories.size() - 1; i >= 0; i--) {
            if (normalizeCategory(committedCategories.get(i)).equalsIgnoreCase(normalized)) {
                committedCategories.remove(i);
            }
        }
        addCommittedCategory(CATEGORY_DEFAULT);
        for (Warehouse warehouse : committedWarehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(normalized)) {
                warehouse.category = CATEGORY_DEFAULT;
            }
        }
        committedFingerprint = committedSnapshotFingerprint();
    }

    private void addCommittedCategory(String category) {
        if (!containsCategory(committedCategories, category)) {
            committedCategories.add(normalizeCategory(category));
        }
    }

    private boolean categoryExists(String category) {
        return containsCategory(categories, category);
    }

    private boolean containsCategory(List<String> values, String category) {
        if (values == null) {
            return false;
        }
        String normalized = normalizeCategory(category);
        for (String value : values) {
            if (normalizeCategory(value).equalsIgnoreCase(normalized)) {
                return true;
            }
        }
        return false;
    }

    private Warehouse firstWarehouseInCategory(String category) {
        for (Warehouse warehouse : warehouses) {
            if (warehouse != null && normalizeCategory(warehouse.category).equalsIgnoreCase(normalizeCategory(category))) {
                return warehouse;
            }
        }
        return null;
    }

    private String uniqueWarehouseName(String base) {
        String candidate = isBlank(base) ? I18n.format("gui.warehouse.edit.new_name") : base.trim();
        int suffix = 2;
        while (warehouseNameExists(candidate)) {
            candidate = base.trim() + " " + suffix++;
        }
        return candidate;
    }

    private boolean warehouseNameExists(String name) {
        for (Warehouse warehouse : warehouses) {
            if (warehouse != null && safe(warehouse.name).equalsIgnoreCase(safe(name))) {
                return true;
            }
        }
        return false;
    }

    private String uniqueRuleName(ChestData chest, String base) {
        String candidate = isBlank(base) ? "gui.modern.warehouse.u066" : base.trim();
        int suffix = 2;
        while (ruleNameExists(chest, candidate)) {
            candidate = base.trim() + " " + suffix++;
        }
        return candidate;
    }

    private boolean ruleNameExists(ChestData chest, String name) {
        if (chest == null || chest.sortingRules == null) {
            return false;
        }
        for (SortingRule rule : chest.sortingRules) {
            if (rule != null && safe(rule.name).equalsIgnoreCase(safe(name))) {
                return true;
            }
        }
        return false;
    }

    private List<SortingRule> copyRules(List<SortingRule> source) {
        List<SortingRule> result = new ArrayList<>();
        if (source != null) {
            for (SortingRule rule : source) {
                if (rule != null) {
                    result.add(copySortingRule(rule));
                }
            }
        }
        return result;
    }

    private boolean coordinateTextMatches(ModernTextField field, double expected) {
        if (field == null) {
            return true;
        }
        String text = safe(field.getText()).trim().replace(',', '.');
        if (text.isEmpty()) {
            return expected == 0.0D;
        }
        try {
            double parsed = Double.parseDouble(text);
            return !Double.isNaN(parsed) && !Double.isInfinite(parsed) && parsed == expected;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private Double parseCoordinate(String text) {
        String value = safe(text).trim();
        if (value.isEmpty()) {
            return Double.valueOf(0.0D);
        }
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return null;
            }
            return Double.valueOf(parsed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        String result = String.format(Locale.ROOT, "%.2f", value);
        while (result.indexOf('.') >= 0 && result.endsWith("0")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.endsWith(".")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String formatCoordinateInput(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "0";
        }
        return Double.toString(value);
    }

    private String normalizeCategory(String category) {
        String normalized = safe(category).trim();
        return normalized.isEmpty() ? CATEGORY_DEFAULT : normalized;
    }

    private boolean isConcreteCategory(String category) {
        return !isBlank(category) && !CATEGORY_ALL.equals(category);
    }

    private boolean isControlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }

    private Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ModernMainLayout.Rect inset(ModernMainLayout.Rect rect, int amount) {
        if (rect == null) {
            return new ModernMainLayout.Rect(0, 0, 1, 1);
        }
        int safeAmount = Math.max(0, amount);
        return new ModernMainLayout.Rect(rect.x + safeAmount, rect.y + safeAmount,
                Math.max(1, rect.width - safeAmount * 2), Math.max(1, rect.height - safeAmount * 2));
    }

    private static boolean intersects(ModernMainLayout.Rect first, ModernMainLayout.Rect second) {
        return first != null && second != null && first.x < second.right() && first.right() > second.x
                && first.y < second.bottom() && first.bottom() > second.y;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private List<Warehouse> matchingWarehouses(String category, String query) {
        List<Warehouse> result = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            if (warehouse == null || !normalizeCategory(warehouse.category).equalsIgnoreCase(normalizeCategory(category))) {
                continue;
            }
            if (query == null || query.isEmpty() || warehouseMatches(warehouse, query)) {
                result.add(warehouse);
            }
        }
        return result;
    }

    private boolean warehouseMatches(Warehouse warehouse, String query) {
        if (warehouse == null) {
            return false;
        }
        String text = safe(warehouse.name) + " " + normalizeCategory(warehouse.category) + " " + formatRange(warehouse);
        return matches(text, query);
    }

    private boolean matches(String value, String query) {
        if (query == null || query.isEmpty()) {
            return true;
        }
        try {
            return PinyinSearchHelper.matchesNormalized(value, query);
        } catch (RuntimeException ignored) {
            return safe(value).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
        }
    }

    private String normalizedSearch(ModernTextField field) {
        String value = field == null ? "" : safe(field.getText()).trim();
        if (value.isEmpty()) {
            return "";
        }
        try {
            return PinyinSearchHelper.normalizeQuery(value);
        } catch (RuntimeException ignored) {
            return value.toLowerCase(Locale.ROOT);
        }
    }

    private List<Integer> matchingChestIndexes(Warehouse warehouse, String query) {
        List<Integer> result = new ArrayList<>();
        if (warehouse == null || warehouse.chests == null) {
            return result;
        }
        for (int i = 0; i < warehouse.chests.size(); i++) {
            ChestData chest = warehouse.chests.get(i);
            if (chest == null) {
                continue;
            }
            String text = formatPos(chest.pos) + " " + ModernFormI18n.tr(chest.hasBeenScanned ? "gui.modern.warehouse.u059" : "gui.modern.warehouse.u060")
                    + " " + joinStrings(safeSet(chest.designatedItems));
            boolean matchesRecord = query == null || query.isEmpty() || matches(text, query);
            if (!matchesRecord && chest.hasBeenScanned) {
                for (ItemStack stack : chest.getSnapshotContents(54)) {
                    if (stack != null && !stack.isEmpty() && matches(stack.getDisplayName(), query)) {
                        matchesRecord = true;
                        break;
                    }
                }
            }
            if (matchesRecord) {
                result.add(i);
            }
        }
        return result;
    }

    /** Copy only runtime scan data back from the committed warehouse. */
    private void syncScannedChestStates() {
        if (selectedWarehouse == null) {
            return;
        }
        Warehouse source = sourceByDraft.get(selectedWarehouse);
        if (source == null || source.chests == null || selectedWarehouse.chests == null) {
            return;
        }
        for (ChestData draft : selectedWarehouse.chests) {
            if (draft == null || draft.pos == null) {
                continue;
            }
            ChestData committed = findChestByPosition(source.chests, draft.pos);
            if (committed != null && committed.hasBeenScanned
                    && (!draft.hasBeenScanned || !safe(draft.itemsNBTString).equals(safe(committed.itemsNBTString)))) {
                draft.hasBeenScanned = true;
                draft.itemsNBTString = committed.itemsNBTString;
            }
        }
    }

    private ChestData selectedChest() {
        return chestAt(selectedWarehouse, selectedChestIndex);
    }

    private ChestData chestAt(Warehouse warehouse, int index) {
        if (warehouse == null || warehouse.chests == null || index < 0 || index >= warehouse.chests.size()) {
            return null;
        }
        return warehouse.chests.get(index);
    }

    private SortingRule selectedRule() {
        ChestData chest = selectedChest();
        if (chest == null || chest.sortingRules == null || selectedRuleIndex < 0
                || selectedRuleIndex >= chest.sortingRules.size()) {
            return null;
        }
        return chest.sortingRules.get(selectedRuleIndex);
    }

    private int chestCount(Warehouse warehouse) {
        return warehouse == null || warehouse.chests == null ? 0 : warehouse.chests.size();
    }

    private int sortingRuleCount(ChestData chest) {
        return chest == null || chest.sortingRules == null ? 0 : chest.sortingRules.size();
    }

    private int designatedCount(ChestData chest) {
        return chest == null || chest.designatedItems == null ? 0 : chest.designatedItems.size();
    }

    private int inventoryEntryCount(ChestData chest) {
        return chest == null || !chest.hasBeenScanned ? 0 : inventoryEntries(chest, "").size();
    }

    private List<InventoryEntry> inventoryEntries(ChestData chest, String query) {
        if (chest == null || !chest.hasBeenScanned) {
            return Collections.emptyList();
        }
        Map<String, InventoryEntry> byKey = new LinkedHashMap<>();
        NonNullList<ItemStack> contents = chest.getSnapshotContents(54);
        for (int i = 0; i < contents.size(); i++) {
            ItemStack stack = contents.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String name = safe(stack.getDisplayName());
            if (query != null && !query.isEmpty() && !matches(name, query)) {
                continue;
            }
            String key = uniqueItemKey(stack);
            InventoryEntry entry = byKey.get(key);
            if (entry == null) {
                entry = new InventoryEntry(key, name, stack.copy());
                byKey.put(key, entry);
            }
            entry.count += stack.getCount();
            entry.slots.add(Integer.valueOf(i));
        }
        List<InventoryEntry> result = new ArrayList<>(byKey.values());
        Collections.sort(result, new Comparator<InventoryEntry>() {
            @Override
            public int compare(InventoryEntry first, InventoryEntry second) {
                return safe(first.name).toLowerCase(Locale.ROOT)
                        .compareTo(safe(second.name).toLowerCase(Locale.ROOT));
            }
        });
        return result;
    }

    private String uniqueItemKey(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return "";
        }
        String registryName = String.valueOf(stack.getItem().getRegistryName());
        String key = registryName + "@" + stack.getMetadata();
        if (stack.hasTagCompound()) {
            key += ":" + stack.getTagCompound().toString();
        }
        return key;
    }

    private String buildItemTooltip(InventoryEntry entry, String slots) {
        List<String> lines = new ArrayList<>();
        if (entry != null && entry.sample != null) {
            try {
                List<String> tooltip = entry.sample.getTooltip(mc().player, ITooltipFlag.TooltipFlags.NORMAL);
                if (tooltip != null) {
                    lines.addAll(tooltip);
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (lines.isEmpty()) {
            lines.add(entry == null ? "" : entry.name);
        }
        lines.add(ModernFormI18n.tr("gui.modern.warehouse.fmt.total", String.valueOf(entry == null ? 0 : entry.count)));
        lines.add(ModernFormI18n.tr("gui.modern.warehouse.fmt.slots", slots));
        return join(lines, "\n");
    }

    private String formatPos(BlockPos pos) {
        if (pos == null) {
            return "gui.modern.warehouse.u224";
        }
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private String formatRange(Warehouse warehouse) {
        if (warehouse == null) {
            return "gui.modern.warehouse.u225";
        }
        return "(" + formatDouble(warehouse.x1) + ", " + formatDouble(warehouse.z1) + ") ~ ("
                + formatDouble(warehouse.x2) + ", " + formatDouble(warehouse.z2) + ")";
    }

    private String joinIntegers(List<Integer> values) {
        if (values == null || values.isEmpty()) {
            return "gui.modern.warehouse.u226";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                result.append(", ");
            }
            result.append(values.get(i));
        }
        return result.toString();
    }

    private String joinStrings(Iterable<String> values) {
        if (values == null) {
            return "";
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                result.add(value.trim());
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return join(result, ", ");
    }

    private String join(List<String> values, String separator) {
        StringBuilder result = new StringBuilder();
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(value.trim());
        }
        return result.toString();
    }

    private List<String> splitStrings(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        String[] parts = value.split("[,;\\n]");
        for (String part : parts) {
            String normalized = safe(part).trim();
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private Set<String> safeSet(Set<String> values) {
        return values == null ? Collections.<String>emptySet() : values;
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.<String>emptyList() : values;
    }

    private String viewTooltip(View candidate) {
        switch (candidate) {
        case REGION:
            return "gui.modern.warehouse.u227";
        case CHESTS:
            return "gui.modern.warehouse.u052";
        case INVENTORY:
            return "gui.modern.warehouse.u228";
        case POLICY:
            return "gui.modern.warehouse.u229";
        case SORTING:
        default:
            return "gui.modern.warehouse.u230";
        }
    }

    private String snapshotFingerprint() {
        try {
            return GSON.toJson(categories) + "\n" + GSON.toJson(warehouses);
        } catch (RuntimeException exception) {
            StringBuilder result = new StringBuilder();
            result.append(categories.toString());
            for (Warehouse warehouse : warehouses) {
                result.append('|').append(warehouse == null ? "" : warehouse.name);
            }
            return result.toString();
        }
    }

    private String committedSnapshotFingerprint() {
        try {
            return GSON.toJson(committedCategories) + "\n" + GSON.toJson(committedWarehouses);
        } catch (RuntimeException exception) {
            return committedCategories.toString() + "\n" + committedWarehouses.toString();
        }
    }

    private void rebaseCommittedWarehouse(Warehouse source, Warehouse draft) {
        if (source == null || draft == null) {
            return;
        }
        for (Warehouse committed : committedWarehouses) {
            if (committedSourceByDraft.get(committed) == source) {
                applyWarehouseValues(committed, draft);
                committedFingerprint = committedSnapshotFingerprint();
                return;
            }
        }
    }

    private static Warehouse copyWarehouse(Warehouse source) {
        Warehouse copy = new Warehouse();
        if (source == null) {
            copy.name = "";
            copy.category = CATEGORY_DEFAULT;
            copy.chests = new CopyOnWriteArrayList<>();
            copy.updateBounds();
            return copy;
        }
        copy.name = source.name;
        copy.category = source.category == null || source.category.trim().isEmpty() ? CATEGORY_DEFAULT
                : source.category;
        copy.isActive = source.isActive;
        copy.x1 = source.x1;
        copy.z1 = source.z1;
        copy.x2 = source.x2;
        copy.z2 = source.z2;
        copy.chests = new CopyOnWriteArrayList<>();
        if (source.chests != null) {
            for (ChestData chest : source.chests) {
                if (chest != null) {
                    copy.chests.add(copyChest(chest));
                }
            }
        }
        copy.updateBounds();
        return copy;
    }

    private static ChestData copyChest(ChestData source) {
        ChestData copy = new ChestData();
        if (source == null) {
            return copy;
        }
        copy.pos = source.pos == null ? null : new BlockPos(source.pos.getX(), source.pos.getY(), source.pos.getZ());
        copy.hasBeenScanned = source.hasBeenScanned;
        copy.itemsNBTString = source.itemsNBTString;
        copy.sortEnabled = source.sortEnabled;
        copy.autoDepositEnabled = source.autoDepositEnabled;
        copy.depositFrequency = source.depositFrequency;
        copyDepositPolicy(copy, source);
        copy.designatedItems = new HashSet<>();
        if (source.designatedItems != null) {
            copy.designatedItems.addAll(source.designatedItems);
        }
        copy.sortingRules = new ArrayList<>();
        if (source.sortingRules != null) {
            for (SortingRule rule : source.sortingRules) {
                if (rule != null) {
                    copy.sortingRules.add(copySortingRule(rule));
                }
            }
        }
        return copy;
    }

    private static void copyDepositPolicy(ChestData target, ChestData source) {
        target.depositItemOrder = new ArrayList<>(WarehouseEventHandler.orderedDepositNames(source));
        target.depositItemsConfigured = source.depositItemsConfigured;
        target.depositInventorySlots = source.depositInventorySlots == null ? new ArrayList<Integer>()
                : new ArrayList<>(source.depositInventorySlots);
        target.spreadAfterDeposit = source.spreadAfterDeposit;
        target.spreadItemNames = source.spreadItemNames;
        target.postDepositSequence = source.postDepositSequence;
    }

    private static SortingRule copySortingRule(SortingRule source) {
        SortingRule copy = new SortingRule();
        if (source == null) {
            return copy;
        }
        copy.name = "gui.modern.warehouse.u218".equals(source.name)
                ? ModernFormI18n.tr("gui.modern.warehouse.u218") : source.name;
        copy.enabled = source.enabled;
        copy.matchMode = source.matchMode == null ? SortingRule.MatchMode.ANY : source.matchMode;
        copy.itemType = source.itemType == null ? SortingRule.ItemType.ANY : source.itemType;
        copy.targetSlots = source.targetSlots == null ? new ArrayList<Integer>() : new ArrayList<>(source.targetSlots);
        copy.itemKeywords = source.itemKeywords == null ? new ArrayList<String>() : new ArrayList<>(source.itemKeywords);
        return copy;
    }

    private static void applyWarehouseValues(Warehouse target, Warehouse source) {
        if (target == null || source == null) {
            return;
        }
        target.name = source.name;
        target.category = source.category == null || source.category.trim().isEmpty() ? CATEGORY_DEFAULT
                : source.category.trim();
        target.isActive = source.isActive;
        target.x1 = source.x1;
        target.z1 = source.z1;
        target.x2 = source.x2;
        target.z2 = source.z2;
        List<ChestData> existingChests = target.chests == null ? Collections.<ChestData>emptyList() : target.chests;
        List<ChestData> appliedChests = new ArrayList<>();
        if (source.chests != null) {
            for (ChestData chest : source.chests) {
                if (chest != null) {
                    ChestData applied = findChestByPosition(existingChests, chest.pos);
                    if (applied == null) {
                        applied = copyChest(chest);
                    } else {
                        applyChestValues(applied, chest);
                    }
                    appliedChests.add(applied);
                }
            }
        }
        target.chests = new CopyOnWriteArrayList<>(appliedChests);
        target.updateBounds();
    }

    private static ChestData findChestByPosition(List<ChestData> chests, BlockPos position) {
        if (chests == null || position == null) {
            return null;
        }
        for (ChestData chest : chests) {
            if (chest != null && position.equals(chest.pos)) {
                return chest;
            }
        }
        return null;
    }

    private static void applyChestValues(ChestData target, ChestData source) {
        if (target == null || source == null) {
            return;
        }
        target.pos = source.pos == null ? null : new BlockPos(source.pos.getX(), source.pos.getY(), source.pos.getZ());
        target.hasBeenScanned = source.hasBeenScanned;
        target.itemsNBTString = source.itemsNBTString;
        target.sortEnabled = source.sortEnabled;
        target.autoDepositEnabled = source.autoDepositEnabled;
        target.depositFrequency = source.depositFrequency;
        target.designatedItems = source.designatedItems == null ? new HashSet<String>()
                : new HashSet<>(source.designatedItems);
        copyDepositPolicy(target, source);
        target.sortingRules = source.sortingRules == null ? new ArrayList<SortingRule>() : copyRulesStatic(source.sortingRules);
    }

    private static List<SortingRule> copyRulesStatic(List<SortingRule> source) {
        List<SortingRule> result = new ArrayList<>();
        if (source != null) {
            for (SortingRule rule : source) {
                if (rule != null) {
                    result.add(copySortingRule(rule));
                }
            }
        }
        return result;
    }

    private void updateCursor(ModernTextField field) {
        if (field != null) {
            field.updateCursorCounter();
        }
    }

    private ModernTextField createField(FontRenderer fontRenderer, int maxLength) {
        ModernTextField field = new ModernTextField(0, fontRenderer, 0, 0, 1, 18);
        field.setEnableBackgroundDrawing(false);
        field.setMaxStringLength(Math.max(1, maxLength));
        field.setTextColor(ModernUiRenderer.TEXT);
        field.setDisabledTextColour(ModernUiRenderer.MUTED_TEXT);
        field.setVisible(false);
        field.setCanLoseFocus(true);
        return field;
    }

    private void hideFields() {
        setHidden(searchField);
        setHidden(nameField);
        setHidden(categoryField);
        setHidden(x1Field);
        setHidden(z1Field);
        setHidden(x2Field);
        setHidden(z2Field);
        setHidden(chestSearchField);
        setHidden(inventorySearchField);
        setHidden(designatedField);
        setHidden(spreadNamesField);
        setHidden(postDepositSequenceField);
        setHidden(ruleNameField);
        setHidden(ruleKeywordsField);
        if (dialogMode == DialogMode.NONE) {
            setHidden(dialogField);
        }
    }

    private void setHidden(ModernTextField field) {
        if (field != null) {
            field.setVisible(false);
            field.x = -2000;
            field.y = -2000;
        }
    }

    private void clearFieldFocusExcept(ModernTextField keep) {
        ModernTextField[] fields = allFields();
        for (ModernTextField field : fields) {
            if (field != null && field != keep) {
                field.setFocused(false);
            }
        }
    }

    private boolean isAnyFieldFocused() {
        for (ModernTextField field : allFields()) {
            if (field != null && field.isFocused()) {
                return true;
            }
        }
        return false;
    }

    private ModernTextField[] allFields() {
        return new ModernTextField[] { searchField, nameField, categoryField, x1Field, z1Field, x2Field, z2Field,
                chestSearchField, inventorySearchField, designatedField, spreadNamesField, postDepositSequenceField,
                ruleNameField, ruleKeywordsField, dialogField };
    }

    private ModernTextField findFocusedField() {
        for (ModernTextField field : allFields()) {
            if (field != null && field.isVisible() && field.isFocused()) {
                return field;
            }
        }
        return null;
    }

    private boolean fieldContains(ModernTextField field, int mouseX, int mouseY) {
        return field != null && field.getVisible() && mouseX >= field.x && mouseX < field.x + field.width
                && mouseY >= field.y && mouseY < field.y + field.height;
    }
}
