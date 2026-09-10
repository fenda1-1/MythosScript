package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSettingsTab;
import com.zszl.zszlScriptMod.gui.modern.core.ModernEmbeddedPanel;
import com.zszl.zszlScriptMod.gui.modern.core.ModernPanelStack;
import com.zszl.zszlScriptMod.gui.modern.core.ModernScreenContext;
import com.zszl.zszlScriptMod.gui.packet.PacketFilterConfig;
import com.zszl.zszlScriptMod.gui.packet.PacketIdRecordManager;
import com.zszl.zszlScriptMod.gui.packet.PacketSequenceManager;
import com.zszl.zszlScriptMod.gui.packet.PacketSnapshotManager;
import com.zszl.zszlScriptMod.utils.PacketCaptureHandler;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import com.zszl.zszlScriptMod.gui.modern.form.ModernFormI18n;

/** Native packet workbench. Child workflows bind as sibling tabs of this instance. */
public final class PacketWorkbenchTab implements ModernSettingsTab {
    static final String COMMAND = "packet_handler";
    static final String VIEWER = "packet_viewer";
    static final String FILTER = "packet_filter";
    static final String FIELD_RULES = "packet_field_rules";
    static final String INTERCEPT = "packet_intercept";
    static final String CAPTURED_IDS = "packet_captured_ids";
    static final String ID_GENERATOR = "packet_id_generator";
    static final String SNAPSHOTS = "packet_snapshots";
    static final String ID_RECORDS = "packet_id_records";
    static final String SEQUENCES = "packet_sequences";
    static final String SEQUENCE_EDITOR = "packet_sequence_editor";

    private final Minecraft minecraft;
    private final Consumer<String> routeRequest;
    private final PacketOverviewPanel overview;
    private final Map<String, ModernEmbeddedPanel> children = new HashMap<String, ModernEmbeddedPanel>();
    private final ModernPanelStack overlays = new ModernPanelStack();
    private ModernMainLayout.Rect bounds = new ModernMainLayout.Rect(0, 0, 1, 1);
    private FontRenderer initializedFont;
    private String status = "";
    private String focusedCommand = COMMAND;
    private boolean returnRequested;
    private PacketDrafts.FilterDraft filterDraft;
    private PacketCaptureHandler.CapturedPacketData lastSelectedPacket;

    public PacketWorkbenchTab(Minecraft minecraft, ModernScreenContext context) {
        this.minecraft = minecraft;
        this.routeRequest = context == null ? null : context.getRouteRequest();
        this.overview = new PacketOverviewPanel(this);
        String initial = context == null ? COMMAND : context.getCommand();
        if (initial != null && !initial.isEmpty() && !COMMAND.equals(initial)) {
            focusCommand(initial);
        }
    }

    public static PacketWorkbenchTab create(Minecraft minecraft, ModernScreenContext context) {
        return new PacketWorkbenchTab(minecraft, context);
    }

    public Minecraft minecraft() { return minecraft; }
    public ModernPanelStack panels() { return overlays; }
    public String status() { return status; }
    void status(String value) { status = value == null ? "" : value; }

    public List<PacketCaptureHandler.CapturedPacketData> capturedPackets() {
        synchronized (PacketCaptureHandler.capturedPackets) { return new ArrayList<>(PacketCaptureHandler.capturedPackets); }
    }
    public List<PacketCaptureHandler.CapturedPacketData> receivedPackets() {
        synchronized (PacketCaptureHandler.capturedReceivedPackets) { return new ArrayList<>(PacketCaptureHandler.capturedReceivedPackets); }
    }
    public List<PacketSnapshotManager.SnapshotMeta> snapshots() { return PacketSnapshotManager.listSnapshots(); }
    public List<PacketIdRecordManager.PacketIdRecord> capturedIds() { return PacketIdRecordManager.listRecords(); }
    public List<String> sequences() { return PacketSequenceManager.getAllSequenceNames(); }

    public void toggleCapture() { PacketCaptureHandler.isCapturing = !PacketCaptureHandler.isCapturing; status(tr("gui.modern.pktwb.fmt.capture", tr(PacketCaptureHandler.isCapturing ? "gui.modern.pktwb.u001" : "gui.modern.pktwb.u002"))); }
    public void clearCapture() { PacketCaptureHandler.clearAllPackets(); status("gui.modern.pktwb.u003"); }
    public void toggleBusinessProcessing() {
        PacketFilterConfig.INSTANCE.enableBusinessPacketProcessing = !PacketFilterConfig.INSTANCE.enableBusinessPacketProcessing;
        PacketFilterConfig.save(); status(tr("gui.modern.pktwb.fmt.business", tr(PacketFilterConfig.INSTANCE.enableBusinessPacketProcessing ? "gui.modern.pktwb.u004" : "gui.modern.pktwb.u005")));
    }
    public void cycleCaptureMode() {
        if (PacketFilterConfig.INSTANCE.captureMode == null) PacketFilterConfig.INSTANCE.captureMode = PacketCaptureHandler.CaptureMode.BLACKLIST;
        PacketFilterConfig.INSTANCE.captureMode = PacketFilterConfig.INSTANCE.captureMode.next();
        PacketFilterConfig.save(); status(tr("gui.modern.pktwb.fmt.mode", PacketFilterConfig.INSTANCE.captureMode.name()));
    }

    public void openViewer() { lastSelectedPacket = null; showChild(VIEWER, new PacketViewerPanel(this)); }
    public void openViewer(List<PacketCaptureHandler.CapturedPacketData> packets, String title, boolean readOnly) {
        lastSelectedPacket = null;
        showChild(VIEWER, new PacketViewerPanel(this, packets, title, readOnly));
    }
    public void openViewer(List<PacketCaptureHandler.CapturedPacketData> packets, String title, boolean readOnly, String direction) {
        openViewer(packets, title, readOnly, direction, false);
    }
    public void openViewer(List<PacketCaptureHandler.CapturedPacketData> packets, String title, boolean readOnly,
            String direction, boolean minimalView) {
        lastSelectedPacket = null;
        showChild(VIEWER, new PacketViewerPanel(this, packets, title, readOnly, direction, minimalView));
    }
    public boolean isViewerOpen() { return VIEWER.equals(focusedCommand); }
    public PacketCaptureHandler.CapturedPacketData selectedPacket() {
        if (current() instanceof PacketViewerPanel) {
            PacketCaptureHandler.CapturedPacketData selected = ((PacketViewerPanel) current()).selectedPacket();
            if (selected != null) {
                lastSelectedPacket = selected;
            }
        }
        return lastSelectedPacket;
    }
    void rememberSelectedPacket(PacketCaptureHandler.CapturedPacketData packet) {
        if (packet != null) {
            lastSelectedPacket = packet;
        }
    }
    public void openDetail(int index) { overlays.push(new PacketDetailPanel(this, capturedPackets(), index)); }
    public void openDetail(PacketCaptureHandler.CapturedPacketData packet, int index, String direction) {
        overlays.push(new PacketDetailPanel(this, packet, index, direction));
    }
    public void openFilter() { showChild(FILTER, new PacketFilterPanel(this)); }
    public void openFieldRules() { showChild(FIELD_RULES, new PacketFieldRulesPanel(this)); }
    public void openInterceptRules() { showChild(INTERCEPT, new PacketInterceptRulesPanel(this)); }
    public void openCapturedIds() { showChild(CAPTURED_IDS, new PacketCapturedIdPanel(this)); }
    public void openCapturedIdGenerator() { openCapturedIdGenerator(null); }
    public void openCapturedIdGenerator(List<String> samples) {
        ModernEmbeddedPanel existing = children.get(ID_GENERATOR);
        if (existing instanceof PacketSmartGeneratorPanel) {
            PacketSmartGeneratorPanel panel = (PacketSmartGeneratorPanel) existing;
            int added = panel.addSamples(samples);
            focusedCommand = ID_GENERATOR;
            if (routeRequest != null) routeRequest.accept(ID_GENERATOR);
            if (added > 0) status(tr("gui.modern.pktgen.fmt.appended", String.valueOf(added)));
            return;
        }
        showChild(ID_GENERATOR, new PacketSmartGeneratorPanel(this, samples == null ? new ArrayList<String>() : samples));
    }
    public void openSnapshots() { showChild(SNAPSHOTS, new PacketSnapshotPanel(this)); }
    public void openIdRecords() { showChild(ID_RECORDS, new PacketIdRecordPanel(this)); }
    public void openSequenceManager() { showChild(SEQUENCES, new PacketSequenceManagerPanel(this)); }
    public void openSequenceEditor(String name) { showChild(SEQUENCE_EDITOR, new PacketSequenceEditorPanel(this, name, null)); }
    public void openSequenceEditor(String name, List<PacketCaptureHandler.CapturedPacketData> initial) {
        showChild(SEQUENCE_EDITOR, new PacketSequenceEditorPanel(this, name, initial));
    }
    public void openSequenceEditor(String name, List<PacketCaptureHandler.CapturedPacketData> initial, String direction) {
        showChild(SEQUENCE_EDITOR, new PacketSequenceEditorPanel(this, name, initial, direction));
    }
    public void openSequenceSelector(PacketSequenceSelectorPanel.Selection selection) { overlays.push(new PacketSequenceSelectorPanel(this, selection)); }
    public void openSequenceSelector() { openSequenceSelector(null); }
    public void openPathSequenceSelector(PacketPathSequenceSelectorPanel.Selection selection) {
        overlays.push(new PacketPathSequenceSelectorPanel(this, selection));
    }
    public void openSequenceEditorFromManager(String name) { openSequenceEditor(name); }
    public PacketDrafts.FilterDraft beginFilterEdit() { filterDraft = PacketDrafts.FilterDraft.capture(PacketFilterConfig.INSTANCE); return filterDraft; }
    public void saveFilter() { if (filterDraft != null) { filterDraft.save(); filterDraft = null; status("gui.modern.pktwb.u006"); } }
    public void cancelFilter() { if (filterDraft != null) { filterDraft.cancel(); filterDraft = null; status("gui.modern.pktwb.u007"); } }
    public PacketDrafts.SequenceDraft newSequence(String name) { return new PacketDrafts.SequenceDraft(name); }
    public boolean saveSequence(PacketDrafts.SequenceDraft draft) { boolean ok = draft != null && draft.save(); status(ok ? "gui.modern.pktwb.u008" : "gui.modern.pktwb.u009"); return ok; }

    public void back() {
        if (overlays.depth() > 0) {
            overlays.pop();
            return;
        }
        if (!COMMAND.equals(focusedCommand)) {
            focusedCommand = COMMAND;
            if (routeRequest != null) returnRequested = true;
        }
    }
    public void requestBack() {
        ModernEmbeddedPanel panel = current();
        if (overlays.depth() <= 0 && COMMAND.equals(focusedCommand)) return;
        if (panel == null || !panel.isDirty()) {
            back();
            return;
        }
        overlays.push(new PacketModalPanel(this, "gui.modern.pktwb.u010", "gui.modern.pktwb.u011", "", false,
                ignored -> back()));
    }

    @Override public void focusCommand(String command) {
        String cmd = command == null || command.isEmpty() ? COMMAND : command;
        focusedCommand = cmd;
        if (COMMAND.equals(cmd)) return;
        if (!children.containsKey(cmd)) children.put(cmd, createChild(cmd));
    }

    @Override public boolean consumeReturnRequest() {
        boolean value = returnRequested;
        returnRequested = false;
        return value;
    }

    @Override public void ensureInitialized(FontRenderer fontRenderer) {
        if (fontRenderer != null) initializedFont = fontRenderer;
        initializeCurrent();
    }
    @Override public void updateScreen() { initializeCurrent(); current().updateScreen(); }
    @Override public void draw(FontRenderer fontRenderer, ModernMainLayout.Rect contentBounds, int mouseX, int mouseY) {
        bounds = contentBounds == null ? bounds : contentBounds;
        if (fontRenderer != null) initializedFont = fontRenderer;
        initializeCurrent();
        ModernEmbeddedPanel panel = current(); panel.setBounds(bounds); panel.draw(fontRenderer, bounds, mouseX, mouseY);
    }
    @Override public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) { return current().mouseClicked(mouseX, mouseY, mouseButton); }
    @Override public boolean mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        return current().mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }
    @Override public boolean mouseReleased(int mouseX, int mouseY, int state) { return current().mouseReleased(mouseX, mouseY, state); }
    @Override public boolean keyTyped(char typedChar, int keyCode) { return current().keyTyped(typedChar, keyCode); }
    @Override public boolean handleMouseWheel(int wheel) { return current().handleMouseWheel(wheel); }
    @Override public boolean handleMouseWheel(int wheel, int mouseX, int mouseY) {
        ModernEmbeddedPanel panel = current();
        if (panel instanceof PacketFilterPanel) ((PacketFilterPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketFieldRulesPanel) ((PacketFieldRulesPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketInterceptRulesPanel) ((PacketInterceptRulesPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketViewerPanel) ((PacketViewerPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketSequenceEditorPanel) ((PacketSequenceEditorPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketCapturedIdPanel) ((PacketCapturedIdPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketIdRecordPanel) ((PacketIdRecordPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketSequenceManagerPanel) ((PacketSequenceManagerPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketSnapshotPanel) ((PacketSnapshotPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketSmartGeneratorPanel) ((PacketSmartGeneratorPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketSequenceSelectorPanel) ((PacketSequenceSelectorPanel) panel).pointer(mouseX, mouseY);
        if (panel instanceof PacketPathSequenceSelectorPanel) ((PacketPathSequenceSelectorPanel) panel).pointer(mouseX, mouseY);
        return panel.handleMouseWheel(wheel);
    }
    @Override public boolean handleEscape() {
        if (overlays.depth() <= 0 && COMMAND.equals(focusedCommand)) return false;
        requestBack();
        return true;
    }
    @Override public boolean isTextInputFocused() {
        return PacketTextField.hasActiveFocus()
                || current() instanceof PacketPanelBase && ((PacketPanelBase) current()).navigationOverlayOpen();
    }
    @Override public void clearTextInputFocusOutside(int mouseX, int mouseY) {
        if (!PacketTextField.activeContains(mouseX, mouseY)) PacketTextField.clearActiveFocus();
    }
    @Override public boolean containsContent(int mouseX, int mouseY) { return bounds.contains(mouseX, mouseY); }
    @Override public String getHoveredTooltip(int mouseX, int mouseY) { return current().getHoveredTooltip(mouseX, mouseY); }
    @Override public void discardDraft() {
        if (filterDraft != null) cancelFilter();
        overlays.clearAndDiscard();
        children.clear();
        focusedCommand = COMMAND;
        status = "";
        returnRequested = false;
    }
    @Override public void save() {
        if (filterDraft != null) {
            filterDraft.save();
            filterDraft = null;
        }
        current().save();
    }
    @Override public boolean isDirty() {
        if (filterDraft != null || overlays.isDirty()) return true;
        for (ModernEmbeddedPanel panel : children.values()) {
            if (panel != null && panel.isDirty()) return true;
        }
        return false;
    }

    private void showChild(String command, ModernEmbeddedPanel panel) {
        if (command == null || panel == null) return;
        children.put(command, panel);
        focusedCommand = command;
        if (routeRequest != null) routeRequest.accept(command);
    }

    private ModernEmbeddedPanel createChild(String command) {
        if (VIEWER.equals(command)) return new PacketViewerPanel(this);
        if (FILTER.equals(command)) return new PacketFilterPanel(this);
        if (FIELD_RULES.equals(command)) return new PacketFieldRulesPanel(this);
        if (INTERCEPT.equals(command)) return new PacketInterceptRulesPanel(this);
        if (CAPTURED_IDS.equals(command)) return new PacketCapturedIdPanel(this);
        if (ID_GENERATOR.equals(command)) return new PacketSmartGeneratorPanel(this, new ArrayList<String>());
        if (SNAPSHOTS.equals(command)) return new PacketSnapshotPanel(this);
        if (ID_RECORDS.equals(command)) return new PacketIdRecordPanel(this);
        if (SEQUENCES.equals(command)) return new PacketSequenceManagerPanel(this);
        if (SEQUENCE_EDITOR.equals(command)) return new PacketSequenceEditorPanel(this, null, null);
        return overview;
    }

    private void initializeCurrent() {
        if (initializedFont == null) return;
        overview.ensureInitialized(initializedFont);
        current().ensureInitialized(initializedFont);
    }

    private ModernEmbeddedPanel current() {
        if (overlays.depth() > 0) {
            ModernEmbeddedPanel overlay = (ModernEmbeddedPanel) overlays.current();
            if (overlay != null) return overlay;
        }
        if (!COMMAND.equals(focusedCommand)) {
            ModernEmbeddedPanel child = children.get(focusedCommand);
            if (child != null) return child;
        }
        return overview;
    }
    private String tr(String key) {
        return ModernFormI18n.tr(key);
    }

    private String tr(String key, Object... args) {
        return ModernFormI18n.tr(key, args);
    }

}
