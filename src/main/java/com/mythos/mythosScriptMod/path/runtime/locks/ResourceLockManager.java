package com.mythos.mythosScriptMod.path.runtime.locks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ResourceLockManager {

    public enum Resource {
        MOVE,
        LOOK,
        INTERACT,
        INVENTORY,
        PACKET,
        WAIT,
        COMBAT
    }

    public static final class LockSnapshot {
        private final Resource resource;
        private final String ownerId;
        private final String sequenceName;
        private final boolean background;
        private final String detail;

        private LockSnapshot(Resource resource, String ownerId, String sequenceName, boolean background, String detail) {
            this.resource = resource;
            this.ownerId = ownerId;
            this.sequenceName = sequenceName;
            this.background = background;
            this.detail = detail;
        }

        public Resource getResource() {
            return resource;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public String getSequenceName() {
            return sequenceName;
        }

        public boolean isBackground() {
            return background;
        }

        public String getDetail() {
            return detail;
        }
    }

    private static final class LockOwner {
        private final String ownerId;
        private final String sequenceName;
        private final boolean background;
        private final String detail;

        private LockOwner(String ownerId, String sequenceName, boolean background, String detail) {
            this.ownerId = ownerId;
            this.sequenceName = sequenceName;
            this.background = background;
            this.detail = detail;
        }
    }

    // Locks are scoped per owner (bot username, "" for the human/standalone).
    // Two sequences on the SAME bot must serialise on MOVE/LOOK/etc, but a
    // lock held by one bot must never block another bot — that was the B1
    // defect (one global map let bot1's MOVE lock stall every other bot).
    private static final Map<String, Map<Resource, LockOwner>> LOCKS_BY_SCOPE = new HashMap<>();

    private ResourceLockManager() {
    }

    private static Map<Resource, LockOwner> scopeMap(String scope) {
        return LOCKS_BY_SCOPE.computeIfAbsent(safe(scope), k -> new EnumMap<>(Resource.class));
    }

    public static synchronized String acquireOrSync(String scope, String ownerId, String sequenceName,
            boolean background, EnumSet<Resource> desiredResources, String detail) {
        Map<Resource, LockOwner> locks = scopeMap(scope);
        String normalizedOwner = safe(ownerId);
        EnumSet<Resource> desired = desiredResources == null
                ? EnumSet.noneOf(Resource.class)
                : EnumSet.copyOf(desiredResources);

        releaseNotDesired(locks, normalizedOwner, desired);

        for (Resource resource : desired) {
            LockOwner existing = locks.get(resource);
            if (existing != null && !normalizedOwner.equals(existing.ownerId)) {
                return resource.name().toLowerCase(Locale.ROOT);
            }
        }

        for (Resource resource : desired) {
            locks.put(resource, new LockOwner(normalizedOwner, safe(sequenceName), background, safe(detail)));
        }
        return "";
    }

    public static synchronized void releaseAll(String scope, String ownerId) {
        Map<Resource, LockOwner> locks = LOCKS_BY_SCOPE.get(safe(scope));
        if (locks == null) {
            return;
        }
        String normalizedOwner = safe(ownerId);
        List<Resource> remove = new ArrayList<>();
        for (Map.Entry<Resource, LockOwner> entry : locks.entrySet()) {
            if (entry.getValue() != null && normalizedOwner.equals(entry.getValue().ownerId)) {
                remove.add(entry.getKey());
            }
        }
        for (Resource resource : remove) {
            locks.remove(resource);
        }
        if (locks.isEmpty()) {
            LOCKS_BY_SCOPE.remove(safe(scope));
        }
    }

    public static synchronized List<LockSnapshot> getSnapshots() {
        if (LOCKS_BY_SCOPE.isEmpty()) {
            return Collections.emptyList();
        }
        List<LockSnapshot> snapshots = new ArrayList<>();
        for (Map<Resource, LockOwner> locks : LOCKS_BY_SCOPE.values()) {
            for (Map.Entry<Resource, LockOwner> entry : locks.entrySet()) {
                LockOwner owner = entry.getValue();
                if (owner == null) {
                    continue;
                }
                snapshots.add(new LockSnapshot(entry.getKey(), owner.ownerId, owner.sequenceName, owner.background,
                        owner.detail));
            }
        }
        return snapshots;
    }

    public static synchronized boolean isHeldByBackground(String scope, Resource resource) {
        if (resource == null) {
            return false;
        }
        Map<Resource, LockOwner> locks = LOCKS_BY_SCOPE.get(safe(scope));
        LockOwner owner = locks == null ? null : locks.get(resource);
        return owner != null && owner.background;
    }

    public static synchronized Resource parseResource(String name) {
        String normalized = safe(name).trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return Resource.valueOf(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void releaseNotDesired(Map<Resource, LockOwner> locks, String ownerId, EnumSet<Resource> desired) {
        List<Resource> remove = new ArrayList<>();
        for (Map.Entry<Resource, LockOwner> entry : locks.entrySet()) {
            if (entry.getValue() != null
                    && ownerId.equals(entry.getValue().ownerId)
                    && !desired.contains(entry.getKey())) {
                remove.add(entry.getKey());
            }
        }
        for (Resource resource : remove) {
            locks.remove(resource);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
