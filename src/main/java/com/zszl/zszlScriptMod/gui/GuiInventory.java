package com.zszl.zszlScriptMod.gui;

import com.zszl.zszlScriptMod.otherfeatures.handler.block.BlockFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.item.ItemFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.misc.MiscFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.MovementFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.movement.SpeedHandler;
import com.zszl.zszlScriptMod.otherfeatures.handler.render.RenderFeatureManager;
import com.zszl.zszlScriptMod.otherfeatures.handler.world.WorldFeatureManager;

/** Shared dashboard state and feature actions used by the modern main screen. */
public class GuiInventory extends GuiInventoryCustomSupport {
    protected static int getTopButtonWidth(OverlayMetrics m, int buttonCount) {
        int gap = m.padding;
        int availableWidth = Math.max(scaleUi(160, m.scale), m.totalWidth - m.padding * 2);
        int fittedWidth = (availableWidth - gap * Math.max(0, buttonCount - 1)) / Math.max(1, buttonCount);
        return Math.max(scaleUi(40, m.scale), Math.min(m.pathManagerButtonWidth, fittedWidth));
    }

    static boolean isOtherFeatureEnabled(String featureId) {
        if ("speed".equalsIgnoreCase(featureId)) {
            return SpeedHandler.enabled;
        }
        if (MovementFeatureManager.isManagedFeature(featureId)) {
            return MovementFeatureManager.isEnabled(featureId);
        }
        if (BlockFeatureManager.isManagedFeature(featureId)) {
            return BlockFeatureManager.isEnabled(featureId);
        }
        if (RenderFeatureManager.isManagedFeature(featureId)) {
            return RenderFeatureManager.isEnabled(featureId);
        }
        if (WorldFeatureManager.isManagedFeature(featureId)) {
            return WorldFeatureManager.isEnabled(featureId);
        }
        if (ItemFeatureManager.isManagedFeature(featureId)) {
            return ItemFeatureManager.isEnabled(featureId);
        }
        if (MiscFeatureManager.isManagedFeature(featureId)) {
            return MiscFeatureManager.isEnabled(featureId);
        }
        return false;
    }

    static boolean toggleOtherFeature(String featureId) {
        if (featureId == null || featureId.trim().isEmpty()) {
            return false;
        }
        if ("speed".equalsIgnoreCase(featureId)) {
            SpeedHandler.INSTANCE.toggleEnabled();
            return true;
        }
        if (MovementFeatureManager.isManagedFeature(featureId)) {
            MovementFeatureManager.toggleFeature(featureId);
            return true;
        }
        if (BlockFeatureManager.isManagedFeature(featureId)) {
            BlockFeatureManager.toggleFeature(featureId);
            return true;
        }
        if (RenderFeatureManager.isManagedFeature(featureId)) {
            RenderFeatureManager.toggleFeature(featureId);
            return true;
        }
        if (WorldFeatureManager.isManagedFeature(featureId)) {
            WorldFeatureManager.toggleFeature(featureId);
            return true;
        }
        if (ItemFeatureManager.isManagedFeature(featureId)) {
            ItemFeatureManager.toggleFeature(featureId);
            return true;
        }
        if (MiscFeatureManager.isManagedFeature(featureId)) {
            MiscFeatureManager.toggleFeature(featureId);
            return true;
        }
        return false;
    }
}
