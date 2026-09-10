package com.zszl.zszlScriptMod.gui.modern.core;

/** Small, UI-agnostic guard for destructive actions in embedded workbenches. */
public final class ModernConfirmationState {
    private static final long TIMEOUT_MILLIS = 4200L;

    private String pendingAction = "";
    private long expiresAt;

    /** Returns true only for the second request of the same action. */
    public boolean request(String action) {
        String normalized = normalize(action);
        long now = System.currentTimeMillis();
        if (!normalized.isEmpty() && normalized.equals(pendingAction) && now <= expiresAt) {
            clear();
            return true;
        }
        if (normalized.isEmpty()) {
            clear();
            return false;
        }
        pendingAction = normalized;
        expiresAt = now + TIMEOUT_MILLIS;
        return false;
    }

    public boolean isPending(String action) {
        if (System.currentTimeMillis() > expiresAt) {
            clear();
        }
        return normalize(action).equals(pendingAction);
    }

    public void clear() {
        pendingAction = "";
        expiresAt = 0L;
    }

    private static String normalize(String action) {
        return action == null ? "" : action.trim();
    }
}
