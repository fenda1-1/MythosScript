package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.input.Keyboard;

import com.zszl.zszlScriptMod.gui.modern.ModernHoverScrollbar;
import com.zszl.zszlScriptMod.gui.MainUiLayoutManager;
import com.zszl.zszlScriptMod.gui.modern.ModernMainLayout;
import com.zszl.zszlScriptMod.gui.modern.ModernSplitPane;
import com.zszl.zszlScriptMod.gui.modern.ModernUiRenderer;
import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import com.zszl.zszlScriptMod.utils.CapturedIdSmartRuleGenerator;

import net.minecraft.client.gui.FontRenderer;

/** Sample editor and proposal importer for captured-ID smart generation. */
final class PacketSmartGeneratorPanel extends PacketPanelBase {
    private final PacketCapturedIdDraft state = new PacketCapturedIdDraft();
    private final PacketTextField sample = new PacketTextField(7501, 32767), prefix = new PacketTextField(7502, 128);
    private final PacketTextField display = new PacketTextField(7503, 128), channel = new PacketTextField(7504, 128), category = new PacketTextField(7505, 128);
    private final List<String> initial;
    private int selectedProposal = -1;
    private int selectedSample = -1;
    private int sampleScroll;
    private int proposalScroll;
    private int lastMouseX, lastMouseY;
    private String direction = "both";
    private ModernMainLayout.Rect sampleBounds, sampleListBounds, addBounds, removeBounds, clearBounds, proposalBounds, importBounds, importAllBounds, copyBounds, directionBounds;
    private ModernMainLayout.Rect mobileViewport, mobileScrollbarBounds, mobileScrollbarThumbBounds;
    private int mobileScroll, mobileMaxScroll;
    private boolean draggingMobileScrollbar, draggingSplit, draggingSampleBar, draggingProposalBar;
    private final ModernHoverScrollbar mobileScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar sampleScrollbar = new ModernHoverScrollbar();
    private final ModernHoverScrollbar proposalScrollbar = new ModernHoverScrollbar();
    private final PacketDropdown directionDrop = new PacketDropdown("both", "inbound", "outbound");
    private double splitRatio = 0.48D;
    private ModernMainLayout.Rect splitBounds;

    PacketSmartGeneratorPanel(PacketWorkbenchTab owner, List<String> samples) { super(owner, "gui.modern.pktgen.u001"); initial = samples == null ? new ArrayList<String>() : new ArrayList<>(samples); }
    @Override protected void initializePanel() {
        splitRatio = MainUiLayoutManager.getModernSplitRatio("packet.smart_generator.proposals", splitRatio);
        sample.ensure(font); prefix.ensure(font); display.ensure(font); channel.ensure(font); category.ensure(font);
        registerDropdown(directionDrop);
        directionDrop.setValue(direction);
        for (String value : initial) state.addSample(value); prefix.setText("smart_id"); display.setText(tr("gui.modern.pktgen.u002"));
    }

    int addSamples(List<String> values) {
        int added = state.addSamples(values);
        selectedSample = -1;
        if (sample.initialized()) sample.setText("");
        return added;
    }
    @Override public void updateScreen() { sample.update(); prefix.update(); display.update(); channel.update(); category.update(); }

    @Override protected void drawBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my) {
        int x = area.x + 12, y = area.y + 42, footer = area.bottom() - 30;
        lastMouseX = mx;
        lastMouseY = my;
        if (area.width < 600) {
            drawCompactBody(font, area, mx, my, x, y, footer);
            return;
        }
        int available = Math.max(2, area.width - 24);
        ModernSplitPane.Split split = ModernSplitPane.calculate(available, splitRatio, 240, 240, 180, 180);
        splitRatio = split.ratio;
        ModernMainLayout.Rect left = new ModernMainLayout.Rect(x, y, split.firstWidth, Math.max(40, footer - y - 8));
        splitBounds = ModernSplitPane.verticalDividerBounds(left.x, left.width, 10, y, left.height);
        proposalBounds = new ModernMainLayout.Rect(left.right() + 10, y, Math.max(1, split.secondWidth - 10), left.height);
        ModernSplitPane.drawVerticalDivider(splitBounds, mx, my, draggingSplit);
        ModernUiRenderer.drawSubtlePanel(left.x, left.y, left.width, left.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        ModernUiRenderer.drawSubtlePanel(proposalBounds.x, proposalBounds.y, proposalBounds.width, proposalBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        direction = directionDrop.value();
        state.setDirection(direction);
        int fw = Math.max(90, left.width / 2 - 18); int cy = left.y + 34;
        cy = row(font, "gui.modern.pktgen.u003", prefix, left.x + 10, cy, fw, prefix.text()); cy = row(font, "gui.modern.pktgen.u004", display, left.x + 10, cy, fw, display.text()); cy = row(font, "gui.modern.pktgen.u005", channel, left.x + 10, cy, fw, channel.text()); cy = row(font, "gui.modern.pktgen.u006", category, left.x + 10, cy, fw, category.text());
        text(font, "gui.modern.pktgen.u007", left.x + 10, cy + 6, ModernUiRenderer.MUTED_TEXT, 70); sampleBounds = new ModernMainLayout.Rect(left.x + 84, cy, left.width - 94, 20); field(sample, sampleBounds, null); cy += 28;
        addBounds = new ModernMainLayout.Rect(left.x + 10, cy, 76, 22); removeBounds = new ModernMainLayout.Rect(addBounds.right() + 6, cy, 76, 22); clearBounds = new ModernMainLayout.Rect(removeBounds.right() + 6, cy, 58, 22); directionBounds = new ModernMainLayout.Rect(clearBounds.right() + 6, cy, Math.max(1, left.right() - clearBounds.right() - 16), 22); drawButton(font, addBounds, selectedSample >= 0 ? "gui.modern.pktgen.u008" : "gui.modern.pktgen.u009", mx, my, true, false); drawButton(font, removeBounds, "gui.modern.pktgen.u010", mx, my, selectedSample >= 0, false); drawButton(font, clearBounds, "gui.modern.pktgen.u011", mx, my); directionDrop.drawButton(font, directionBounds, mx, my);
        int listY = cy + 30; text(font, tr("gui.modern.pktgen.fmt.samples", String.valueOf(state.samples().size())), left.x + 10, listY, ModernUiRenderer.TEXT, left.width - 20);
        int sy = listY + 20; sampleListBounds = new ModernMainLayout.Rect(left.x + 8, sy, Math.max(1, left.width - 16), Math.max(1, left.bottom() - sy - 6));
        int sampleRows = Math.max(1, (sampleListBounds.height - 2) / 24);
        sampleScroll = clamp(sampleScroll, 0, Math.max(0, state.samples().size() - sampleRows));
        for (int i = sampleScroll; i < state.samples().size() && i < sampleScroll + sampleRows; i++) {
            int rowY = sy + (i - sampleScroll) * 24;
            boolean hover = sampleListBounds.contains(mx, my) && my >= rowY && my < rowY + 20;
            ModernUiRenderer.drawSubtlePanel(left.x + 8, rowY, ModernHoverScrollbar.contentWidth(left.width - 16), 20, 3, i == selectedSample ? 0xFF2C3D49 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, i == selectedSample ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, (i + 1) + ". " + inline(state.samples().get(i), 58), left.x + 14, rowY + 6, ModernUiRenderer.SUBTLE_TEXT, left.width - 28);
        }
        int sampleMax = Math.max(0, state.samples().size() - sampleRows);
        if (sampleMax > 0) sampleScrollbar.draw(sampleListBounds, sampleScroll, sampleMax, sampleRows, state.samples().size(), mx, my, value -> sampleScroll = value);
        else sampleScrollbar.idle();
        CapturedIdSmartRuleGenerator.AnalysisResult analysis = state.analyze(); text(font, analysis.getMessage(), proposalBounds.x + 10, proposalBounds.y + 10, ModernUiRenderer.SUBTLE_TEXT, proposalBounds.width - 20); text(font, "建议 (" + analysis.getProposals().size() + ")", proposalBounds.x + 10, proposalBounds.y + 30, ModernUiRenderer.TEXT, proposalBounds.width - 20);
        int py = proposalBounds.y + 52;
        int proposalRows = Math.max(1, (proposalBounds.height - 58) / 66);
        proposalScroll = clamp(proposalScroll, 0, Math.max(0, analysis.getProposals().size() - proposalRows));
        for (int i = proposalScroll; i < analysis.getProposals().size() && i < proposalScroll + proposalRows; i++) {
            int rowY = py + (i - proposalScroll) * 66;
            CapturedIdSmartRuleGenerator.Proposal p = analysis.getProposals().get(i);
            boolean selected = i == selectedProposal, hover = proposalBounds.contains(mx, my) && my >= rowY && my < rowY + 58;
            ModernUiRenderer.drawSubtlePanel(proposalBounds.x + 8, rowY, ModernHoverScrollbar.contentWidth(proposalBounds.width - 8), 58, 3, selected ? 0xFF2C3D49 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED, selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, "#" + p.getIndex() + "  " + p.getMinBytes() + "-" + p.getMaxBytes() + " bytes", proposalBounds.x + 14, rowY + 6, ModernUiRenderer.TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
            text(font, inline(p.getPattern(), 90), proposalBounds.x + 14, rowY + 22, ModernUiRenderer.SUBTLE_TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
            text(font, inline(p.getSummary(), 90), proposalBounds.x + 14, rowY + 38, ModernUiRenderer.MUTED_TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
        }
        int proposalMax = Math.max(0, analysis.getProposals().size() - proposalRows);
        if (proposalMax > 0) proposalScrollbar.draw(proposalBounds, proposalScroll, proposalMax, proposalRows, analysis.getProposals().size(), mx, my, value -> proposalScroll = value);
        else proposalScrollbar.idle();
        int gap = 5, bw = Math.max(58, (proposalBounds.width - gap * 2) / 3); importBounds = new ModernMainLayout.Rect(proposalBounds.x, footer, bw, 22); importAllBounds = new ModernMainLayout.Rect(importBounds.right() + gap, footer, bw, 22); copyBounds = new ModernMainLayout.Rect(importAllBounds.right() + gap, footer, proposalBounds.right() - importAllBounds.right() - gap, 22); drawButton(font, importBounds, "gui.modern.pktgen.u012", mx, my, selectedProposal >= 0, true); drawButton(font, importAllBounds, "gui.modern.pktgen.u013", mx, my, !analysis.getProposals().isEmpty(), false); drawButton(font, copyBounds, "gui.modern.pktgen.u014", mx, my, !analysis.getProposals().isEmpty(), false);
    }

    private void drawCompactBody(FontRenderer font, ModernMainLayout.Rect area, int mx, int my, int x, int top,
            int footer) {
        splitBounds = null;
        int width = Math.max(1, area.width - 24);
        int leftHeight = 380;
        int proposalHeight = 300;
        int gap = 8;
        int contentHeight = leftHeight + gap + proposalHeight + 26;
        mobileViewport = new ModernMainLayout.Rect(x, top, width, Math.max(1, footer - top - 8));
        width = ModernHoverScrollbar.contentWidth(width);
        mobileMaxScroll = Math.max(0, contentHeight - mobileViewport.height);
        mobileScroll = clamp(mobileScroll, 0, mobileMaxScroll);
        int contentY = top - mobileScroll;
        ModernMainLayout.Rect left = new ModernMainLayout.Rect(x, contentY, width, leftHeight);
        proposalBounds = new ModernMainLayout.Rect(x, left.bottom() + gap, width, proposalHeight);
        ModernUiRenderer.beginClip(mobileViewport);
        ModernUiRenderer.drawSubtlePanel(left.x, left.y, left.width, left.height, 5,
                ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        text(font, "gui.modern.pktgen.u015", left.x + 10, left.y + 10, ModernUiRenderer.TEXT, width - 20);
        int fw = Math.max(1, width - 100);
        int cy = left.y + 34;
        cy = row(font, "gui.modern.pktgen.u003", prefix, left.x + 10, cy, fw, prefix.text());
        cy = row(font, "gui.modern.pktgen.u004", display, left.x + 10, cy, fw, display.text());
        cy = row(font, "gui.modern.pktgen.u005", channel, left.x + 10, cy, fw, channel.text());
        cy = row(font, "gui.modern.pktgen.u006", category, left.x + 10, cy, fw, category.text());
        text(font, "gui.modern.pktgen.u007", left.x + 10, cy + 6, ModernUiRenderer.MUTED_TEXT, 78);
        sampleBounds = new ModernMainLayout.Rect(left.x + 82, cy, Math.max(1, width - 92), 20);
        field(sample, sampleBounds, null);
        cy += 28;
        int actionWidth = Math.max(1, (width - 12) / 3);
        addBounds = new ModernMainLayout.Rect(left.x + 10, cy, actionWidth, 22);
        removeBounds = new ModernMainLayout.Rect(addBounds.right() + 6, cy, actionWidth, 22);
        clearBounds = new ModernMainLayout.Rect(removeBounds.right() + 6, cy,
                Math.max(1, left.x + width - removeBounds.right() - 10), 22);
        drawButton(font, addBounds, selectedSample >= 0 ? "gui.modern.pktgen.u008" : "gui.modern.pktgen.u009", mx, my, true, false);
        drawButton(font, removeBounds, "gui.modern.pktgen.u010", mx, my, selectedSample >= 0, false);
        drawButton(font, clearBounds, "gui.modern.pktgen.u011", mx, my);
        cy += 28;
        directionBounds = new ModernMainLayout.Rect(left.x + 10, cy, Math.max(1, width - 20), 22);
        direction = directionDrop.value();
        state.setDirection(direction);
        directionDrop.drawButton(font, directionBounds, mx, my);
        cy += 30;
        text(font, tr("gui.modern.pktgen.fmt.samples", String.valueOf(state.samples().size())), left.x + 10, cy, ModernUiRenderer.TEXT, width - 20);
        int sy = cy + 20;
        sampleListBounds = new ModernMainLayout.Rect(left.x + 8, sy, Math.max(1, width - 16),
                Math.max(1, left.bottom() - sy - 8));
        int sampleRows = Math.max(1, (sampleListBounds.height - 2) / 24);
        sampleScroll = clamp(sampleScroll, 0, Math.max(0, state.samples().size() - sampleRows));
        for (int i = sampleScroll; i < state.samples().size() && i < sampleScroll + sampleRows; i++) {
            int rowY = sy + (i - sampleScroll) * 24;
            boolean hover = sampleListBounds.contains(mx, my) && my >= rowY && my < rowY + 20;
            ModernUiRenderer.drawSubtlePanel(left.x + 8, rowY, ModernHoverScrollbar.contentWidth(width - 16), 20, 3,
                    i == selectedSample ? 0xFF2C3D49 : hover ? ModernUiRenderer.SURFACE_HOVER : ModernUiRenderer.SHELL_RAISED,
                    i == selectedSample ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, (i + 1) + ". " + inline(state.samples().get(i), 58), left.x + 14, rowY + 6,
                    ModernUiRenderer.SUBTLE_TEXT, width - 28);
        }
        int sampleMax = Math.max(0, state.samples().size() - sampleRows);
        if (sampleMax > 0) sampleScrollbar.draw(sampleListBounds, sampleScroll, sampleMax, sampleRows, state.samples().size(), mx, my, value -> sampleScroll = value);
        else sampleScrollbar.idle();

        ModernUiRenderer.drawSubtlePanel(proposalBounds.x, proposalBounds.y, proposalBounds.width,
                proposalBounds.height, 5, ModernUiRenderer.SURFACE, ModernUiRenderer.BORDER_SUBTLE);
        CapturedIdSmartRuleGenerator.AnalysisResult analysis = state.analyze();
        text(font, analysis.getMessage(), proposalBounds.x + 10, proposalBounds.y + 10,
                ModernUiRenderer.SUBTLE_TEXT, proposalBounds.width - 20);
        text(font, tr("gui.modern.pktgen.fmt.proposals", String.valueOf(analysis.getProposals().size())), proposalBounds.x + 10, proposalBounds.y + 30,
                ModernUiRenderer.TEXT, proposalBounds.width - 20);
        int py = proposalBounds.y + 52;
        int proposalRows = Math.max(1, (proposalBounds.height - 58) / 66);
        proposalScroll = clamp(proposalScroll, 0, Math.max(0, analysis.getProposals().size() - proposalRows));
        for (int i = proposalScroll; i < analysis.getProposals().size() && i < proposalScroll + proposalRows; i++) {
            int rowY = py + (i - proposalScroll) * 66;
            CapturedIdSmartRuleGenerator.Proposal proposal = analysis.getProposals().get(i);
            boolean selected = i == selectedProposal;
            ModernUiRenderer.drawSubtlePanel(proposalBounds.x + 8, rowY, ModernHoverScrollbar.contentWidth(proposalBounds.width - 8), 58, 3,
                    selected ? 0xFF2C3D49 : ModernUiRenderer.SHELL_RAISED,
                    selected ? ModernUiRenderer.ACCENT : ModernUiRenderer.BORDER_SUBTLE);
            text(font, "#" + proposal.getIndex() + "  " + proposal.getMinBytes() + "-"
                    + proposal.getMaxBytes() + " bytes", proposalBounds.x + 14, rowY + 6,
                    ModernUiRenderer.TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
            text(font, inline(proposal.getPattern(), 90), proposalBounds.x + 14, rowY + 22,
                    ModernUiRenderer.SUBTLE_TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
            text(font, inline(proposal.getSummary(), 90), proposalBounds.x + 14, rowY + 38,
                    ModernUiRenderer.MUTED_TEXT, ModernHoverScrollbar.contentWidth(proposalBounds.width - 14));
        }
        int proposalMax = Math.max(0, analysis.getProposals().size() - proposalRows);
        if (proposalMax > 0) proposalScrollbar.draw(proposalBounds, proposalScroll, proposalMax, proposalRows, analysis.getProposals().size(), mx, my, value -> proposalScroll = value);
        else proposalScrollbar.idle();
        int buttonWidth = Math.max(1, (width - 10) / 3);
        importBounds = new ModernMainLayout.Rect(x, proposalBounds.bottom() + 4, buttonWidth, 22);
        importAllBounds = new ModernMainLayout.Rect(importBounds.right() + 5, importBounds.y, buttonWidth, 22);
        copyBounds = new ModernMainLayout.Rect(importAllBounds.right() + 5, importBounds.y,
                Math.max(1, x + width - importAllBounds.right() - 5), 22);
        drawButton(font, importBounds, "gui.modern.pktgen.u012", mx, my, selectedProposal >= 0, true);
        drawButton(font, importAllBounds, "gui.modern.pktgen.u013", mx, my, !analysis.getProposals().isEmpty(), false);
        drawButton(font, copyBounds, "gui.modern.pktgen.u014", mx, my, !analysis.getProposals().isEmpty(), false);
        ModernUiRenderer.endClip();
        drawMobileScrollbar(mx, my);
    }

    private int row(FontRenderer font, String label, PacketTextField field, int x, int y, int width, String value) { text(font, label, x, y + 6, ModernUiRenderer.MUTED_TEXT, 78); this.field(field, new ModernMainLayout.Rect(x + 82, y, width, 20), value); return y + 26; }
    @Override protected boolean handleBodyClick(int x, int y, int button) {
        if (button == 0 && splitBounds != null && splitBounds.contains(x, y)) { draggingSplit = true; return true; }
        if (button == 0 && sampleScrollbar.beginDrag(x, y)) { draggingSampleBar = true; return true; }
        if (button == 0 && proposalScrollbar.beginDrag(x, y)) { draggingProposalBar = true; return true; }
        if (button == 0 && mobileScrollbar.beginDrag(x, y)) {
            draggingMobileScrollbar = true;
            return true;
        }
        if (sampleListBounds != null && sampleListBounds.contains(x, y) && y >= sampleListBounds.y) {
            int index = (y - sampleListBounds.y) / 24 + sampleScroll;
            if (index >= 0 && index < state.samples().size()) {
                if (button == 1) { state.removeSample(index); selectedSample = -1; sample.setText(""); }
                else if (button == 0) { selectedSample = index; sample.setText(state.samples().get(index)); sample.focus(true); }
            }
            return true;
        }
        if (button != 0) return true;
        if (fieldClick(sample, x, y, button) || fieldClick(prefix, x, y, button) || fieldClick(display, x, y, button) || fieldClick(channel, x, y, button) || fieldClick(category, x, y, button)) return true;
        if (hit(addBounds, x, y)) { commitSample(); return true; }
        if (hit(removeBounds, x, y) && selectedSample >= 0) { state.removeSample(selectedSample); selectedSample = -1; sample.setText(""); return true; }
        if (hit(clearBounds, x, y)) { state.clearSamples(); selectedProposal = -1; selectedSample = -1; sample.setText(""); return true; }
        if (proposalBounds != null && proposalBounds.contains(x, y) && y >= proposalBounds.y + 52) { int i = (y - proposalBounds.y - 52) / 66 + proposalScroll; if (i >= 0 && i < state.analyze().getProposals().size()) selectedProposal = i; return true; }
        if (hit(importBounds, x, y) && selectedProposal >= 0) { importOne(selectedProposal); return true; }
        if (hit(importAllBounds, x, y)) { importAll(); return true; }
        if (hit(copyBounds, x, y)) { copyAll(); return true; }
        return true;
    }
    private void importOne(int index) {
        String p = prefix.text().trim(); if (p.isEmpty()) p = "smart_id";
        String d = display.text().trim(); if (d.isEmpty()) d = tr("gui.modern.pktgen.u002");
        String c = category.text().trim(), ch = channel.text().trim();
        state.setCategory(c); state.setChannel(ch); state.setDirection(direction);
        Set<String> existing = existingRuleNames();
        String name = uniqueName(existing, p + "_" + (index + 1));
        CapturedIdRuleManager.RuleEditModel model = state.proposal(index, name, d + " " + (index + 1));
        if (model != null) {
            model.category = c; model.channel = ch; model.direction = direction;
            if (CapturedIdRuleManager.addRule(model)) owner.status(tr("gui.modern.pktgen.fmt.imported_one", name));
            else owner.status("gui.modern.pktgen.u016");
        }
    }
    private void importAll() {
        List<CapturedIdSmartRuleGenerator.Proposal> proposals = state.analyze().getProposals();
        if (proposals.isEmpty()) { owner.status("gui.modern.pktgen.u017"); return; }
        String p = prefix.text().trim(); if (p.isEmpty()) p = "smart_id";
        String d = display.text().trim(); if (d.isEmpty()) d = tr("gui.modern.pktgen.u002");
        String c = category.text().trim(), ch = channel.text().trim(); state.setCategory(c); state.setChannel(ch); state.setDirection(direction);
        Set<String> existing = existingRuleNames(); int imported = 0;
        for (int i = 0; i < proposals.size(); i++) {
            String name = uniqueName(existing, p + "_" + (i + 1));
            CapturedIdRuleManager.RuleEditModel model = state.proposal(i, name, d + " " + (i + 1));
            if (model != null && CapturedIdRuleManager.addRule(model)) { existing.add(name.toLowerCase(java.util.Locale.ROOT)); imported++; }
        }
        owner.status(imported == 0 ? "gui.modern.pktgen.u016" : tr("gui.modern.pktgen.fmt.imported_n", String.valueOf(imported)));
    }
    private Set<String> existingRuleNames() {
        Set<String> names = new HashSet<>();
        for (CapturedIdRuleManager.RuleCard card : CapturedIdRuleManager.getRuleCards()) if (card != null && card.model != null && card.model.name != null) names.add(card.model.name.trim().toLowerCase(java.util.Locale.ROOT));
        return names;
    }
    private static String uniqueName(Set<String> existing, String preferred) {
        String base = preferred == null ? "smart_id" : preferred.trim(); if (base.isEmpty()) base = "smart_id";
        String candidate = base; int suffix = 2;
        while (existing.contains(candidate.toLowerCase(java.util.Locale.ROOT))) candidate = base + "_" + suffix++;
        return candidate;
    }
    private void copyAll() {
        List<CapturedIdSmartRuleGenerator.Proposal> proposals = state.analyze().getProposals();
        if (proposals.isEmpty()) { owner.status("gui.modern.pktgen.u018"); return; }
        String p = prefix.text().trim(); if (p.isEmpty()) p = "smart_id";
        String c = category.text().trim(), ch = channel.text().trim();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < proposals.size(); i++) {
            CapturedIdSmartRuleGenerator.Proposal item = proposals.get(i);
            if (b.length() > 0) b.append("\n\n");
            b.append("# Rule ").append(i + 1).append('\n')
                    .append("name=").append(p).append('_').append(i + 1).append('\n')
                    .append("channel=").append(ch).append('\n').append("direction=").append(direction).append('\n')
                    .append("category=").append(c).append('\n').append("group=1\nvalueType=hex\n")
                    .append("byteLength=").append(Math.max(1, item.getMaxBytes())).append('\n')
                    .append("pattern=").append(item.getPattern()).append('\n')
                    .append("samples=").append(join(item.getSampleValues()));
        }
        PacketClipboard.copyOrExport(owner.minecraft(), b.toString()); owner.status("gui.modern.pktgen.u019");
    }
    private static String join(List<String> values) { if (values == null) return ""; StringBuilder b = new StringBuilder(); for (String value : values) { if (b.length() > 0) b.append(" | "); b.append(value == null ? "" : value); } return b.toString(); }
    @Override public boolean keyTyped(char c, int code) { if (fieldKey(sample, c, code) || fieldKey(prefix, c, code) || fieldKey(display, c, code) || fieldKey(channel, c, code) || fieldKey(category, c, code)) return true; if (code == Keyboard.KEY_RETURN && sample.focused()) { commitSample(); return true; } return code == Keyboard.KEY_RETURN; }

    private void commitSample() {
        String formatted = PacketCapturedIdDraft.normalize(sample.text());
        if (formatted.isEmpty()) { owner.status("gui.modern.pktgen.u020"); return; }
        if (selectedSample >= 0) state.replaceSample(selectedSample, formatted);
        else state.addSample(formatted);
        selectedSample = -1;
        sample.setText("");
    }
    @Override protected List<PacketContextMenu.Item> moreItems() {
        List<PacketContextMenu.Item> items = new ArrayList<PacketContextMenu.Item>();
        items.add(new PacketContextMenu.Item("gui.modern.pktgen.u009", () -> commitSample()));
        items.add(new PacketContextMenu.Item("gui.modern.pktgen.u013", () -> importAll()));
        items.add(new PacketContextMenu.Item("gui.modern.pktgen.u014", () -> copyAll()));
        return items;
    }
    @Override public void discardDraft() { PacketTextField.clearActiveFocus(); draggingMobileScrollbar = false; mobileScroll = 0; }
    void pointer(int x, int y) { lastMouseX = x; lastMouseY = y; }
    @Override public boolean handleMouseWheel(int wheel) {
        if (wheel == 0) return false;
        if (mobileViewport != null && mobileViewport.contains(lastMouseX, lastMouseY)) { int before = mobileScroll; mobileScroll = clamp(mobileScroll + (wheel > 0 ? -30 : 30), 0, mobileMaxScroll); return before != mobileScroll; }
        if (sampleListBounds != null && sampleListBounds.contains(lastMouseX, lastMouseY)) {
            int rows = Math.max(1, (sampleListBounds.height - 2) / 24);
            sampleScroll = clamp(sampleScroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, state.samples().size() - rows)); return true;
        }
        if (proposalBounds != null && proposalBounds.contains(lastMouseX, lastMouseY)) {
            int rows = Math.max(1, (proposalBounds.height - 58) / 66);
            proposalScroll = clamp(proposalScroll + (wheel > 0 ? -1 : 1), 0, Math.max(0, state.analyze().getProposals().size() - rows)); return true;
        }
        return false;
    }
    @Override public boolean mouseClickMove(int x, int y, int button, long timeSinceLastClick) {
        if (draggingSplit && button == 0) {
            ModernSplitPane.Split split = ModernSplitPane.calculateFromPointer(Math.max(2, area.width - 24),
                    x - (area.x + 12), 240, 240, 180, 180);
            splitRatio = split.ratio;
            return true;
        }
        if (draggingSampleBar && button == 0) { sampleScrollbar.applyDrag(x, y); return true; }
        if (draggingProposalBar && button == 0) { proposalScrollbar.applyDrag(x, y); return true; }
        if (draggingMobileScrollbar && button == 0) { mobileScrollbar.applyDrag(x, y); return true; }
        return false;
    }
    @Override public boolean mouseReleased(int x, int y, int button) {
        if (button != 0) return false;
        if (draggingSplit) { MainUiLayoutManager.setModernSplitRatio("packet.smart_generator.proposals", splitRatio); draggingSplit = false; return true; }
        if (draggingSampleBar) { draggingSampleBar = false; sampleScrollbar.endDrag(); return true; }
        if (draggingProposalBar) { draggingProposalBar = false; proposalScrollbar.endDrag(); return true; }
        if (draggingMobileScrollbar) { draggingMobileScrollbar = false; mobileScrollbar.endDrag(); return true; }
        return false;
    }
    private void drawMobileScrollbar(int mouseX, int mouseY) {
        if (mobileViewport == null || mobileMaxScroll <= 0) {
            mobileScrollbar.idle();
            mobileScrollbarBounds = null;
            mobileScrollbarThumbBounds = null;
            return;
        }
        mobileScrollbar.draw(mobileViewport, mobileScroll, mobileMaxScroll, mobileViewport.height,
                mobileViewport.height + mobileMaxScroll, mouseX, mouseY, value -> mobileScroll = value);
        mobileScrollbarBounds = new ModernMainLayout.Rect(mobileViewport.right() - 14, mobileViewport.y, 14,
                mobileViewport.height);
        mobileScrollbarThumbBounds = mobileScrollbarBounds;
    }
    private void updateMobileScroll(int mouseY) { if (mobileScrollbarBounds == null || mobileScrollbarThumbBounds == null || mobileMaxScroll <= 0) return; int travel = Math.max(1, mobileScrollbarBounds.height - mobileScrollbarThumbBounds.height); int target = Math.max(0, Math.min(travel, mouseY - mobileScrollbarBounds.y - mobileScrollbarThumbBounds.height / 2)); mobileScroll = clamp(Math.round(target * (float) mobileMaxScroll / travel), 0, mobileMaxScroll); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static String inline(String s, int max) { String v = s == null ? "" : s.replace('\n', ' ').trim(); return v.length() <= max ? v : v.substring(0, Math.max(0, max - 3)) + "..."; }
}
