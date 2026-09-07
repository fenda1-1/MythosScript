package com.zszl.zszlScriptMod.shadowbaritone.api.utils;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

import java.util.Locale;

public final class BlockDisplayLookup {

    private BlockDisplayLookup() {
    }

    public static Block findBlockByUserInput(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        Block byId = BlockUtils.stringToBlockNullable(normalized);
        if (byId != null) {
            return byId;
        }

        String normalizedDisplay = normalizeDisplayLookup(raw);
        if (normalizedDisplay.isEmpty()) {
            return null;
        }

        Block fuzzyMatch = null;
        boolean fuzzyAmbiguous = false;
        for (Block candidate : Block.REGISTRY) {
            if (candidate == null) {
                continue;
            }
            String displayName = normalizeDisplayLookup(getDisplayName(candidate));
            if (displayName.isEmpty()) {
                continue;
            }
            if (displayName.equals(normalizedDisplay)) {
                return candidate;
            }
            if (displayName.contains(normalizedDisplay) || normalizedDisplay.contains(displayName)) {
                if (fuzzyMatch == null) {
                    fuzzyMatch = candidate;
                } else if (fuzzyMatch != candidate) {
                    fuzzyAmbiguous = true;
                }
            }
        }
        return fuzzyAmbiguous ? null : fuzzyMatch;
    }

    private static String getDisplayName(Block block) {
        ItemStack stack = new ItemStack(block);
        return stack.isEmpty() ? "" : stack.getDisplayName();
    }

    private static String normalizeDisplayLookup(String raw) {
        return (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT))
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace(":", "");
    }
}
