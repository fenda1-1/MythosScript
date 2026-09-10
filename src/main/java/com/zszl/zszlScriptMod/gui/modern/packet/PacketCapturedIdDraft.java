package com.zszl.zszlScriptMod.gui.modern.packet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.zszl.zszlScriptMod.utils.CapturedIdRuleManager;
import com.zszl.zszlScriptMod.utils.CapturedIdSmartRuleGenerator;

/** Samples and proposal selection for the embedded captured-ID generator. */
public final class PacketCapturedIdDraft {
    private final List<String> samples = new ArrayList<>();
    private String category = "";
    private String channel = "";
    private String direction = "both";

    public List<String> samples() { return Collections.unmodifiableList(samples); }
    public int addSamples(List<String> values) {
        if (values == null || values.isEmpty()) return 0;
        int before = samples.size();
        for (int i = 0; i < values.size(); i++) addSample(values.get(i));
        return samples.size() - before;
    }
    public void addSample(String hex) { String value = normalize(hex); if (!value.isEmpty() && !samples.contains(value)) samples.add(value); }
    public void removeSample(int index) { if (index >= 0 && index < samples.size()) samples.remove(index); }
    public void replaceSample(int index, String hex) {
        if (index < 0 || index >= samples.size()) return;
        String value = normalize(hex);
        if (value.isEmpty()) return;
        for (int i = 0; i < samples.size(); i++) if (i != index && value.equals(samples.get(i))) return;
        samples.set(index, value);
    }
    public void clearSamples() { samples.clear(); }
    public void setCategory(String value) { category = value == null ? "" : value.trim(); }
    public void setChannel(String value) { channel = value == null ? "" : value.trim(); }
    public void setDirection(String value) { direction = "inbound".equals(value) || "outbound".equals(value) ? value : "both"; }
    public String category() { return category; }
    public CapturedIdSmartRuleGenerator.AnalysisResult analyze() { return CapturedIdSmartRuleGenerator.analyze(new ArrayList<>(samples)); }
    public CapturedIdRuleManager.RuleEditModel proposal(int index, String name, String displayName) {
        List<CapturedIdSmartRuleGenerator.Proposal> proposals = analyze().getProposals();
        if (index < 0 || index >= proposals.size()) return null;
        return CapturedIdSmartRuleGenerator.buildRuleModel(proposals.get(index), name, displayName, category, channel, direction);
    }
    public boolean importProposal(int index, String name, String displayName) {
        CapturedIdRuleManager.RuleEditModel model = proposal(index, name, displayName);
        return model != null && CapturedIdRuleManager.addRule(model);
    }
    static String normalize(String value) {
        String compact = value == null ? "" : value.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        if (compact.isEmpty()) return "";
        if ((compact.length() & 1) != 0) compact = "0" + compact;
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i + 1 < compact.length(); i += 2) {
            if (formatted.length() > 0) formatted.append(' ');
            formatted.append(compact.substring(i, i + 2));
        }
        return formatted.toString();
    }
}
