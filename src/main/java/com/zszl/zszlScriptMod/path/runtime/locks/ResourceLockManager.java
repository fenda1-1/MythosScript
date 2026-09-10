package com.zszl.zszlScriptMod.path.runtime.locks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
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

    private static final Map<Resource, LockOwner> LOCKS = new EnumMap<>(Resource.class);

    private ResourceLockManager() {
    }

    public static synchronized String acquireOrSync(String ownerId, String sequenceName, boolean background,
            EnumSet<Resource> desiredResources, String detail) {
        String normalizedOwner = safe(ownerId);
        EnumSet<Resource> desired = desiredResources == null
                ? EnumSet.noneOf(Resource.class)
                : EnumSet.copyOf(desiredResources);

        releaseNotDesired(normalizedOwner, desired);

        for (Resource resource : desired) {
            LockOwner existing = LOCKS.get(resource);
            if (existing != null && !normalizedOwner.equals(existing.ownerId)) {
                return resource.name().toLowerCase(Locale.ROOT);
            }
        }

        for (Resource resource : desired) {
            LOCKS.put(resource, new LockOwner(normalizedOwner, safe(sequenceName), background, safe(detail)));
        }
        return "";
    }

    public static synchronized void releaseAll(String ownerId) {
        String normalizedOwner = safe(ownerId);
        List<Resource> remove = new ArrayList<>();
        for (Map.Entry<Resource, LockOwner> entry : LOCKS.entrySet()) {
            if (entry.getValue() != null && normalizedOwner.equals(entry.getValue().ownerId)) {
                remove.add(entry.getKey());
            }
        }
        for (Resource resource : remove) {
            LOCKS.remove(resource);
        }
    }

    public static synchronized List<LockSnapshot> getSnapshots() {
        if (LOCKS.isEmpty()) {
            return Collections.emptyList();
        }
        List<LockSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<Resource, LockOwner> entry : LOCKS.entrySet()) {
            LockOwner owner = entry.getValue();
            if (owner == null) {
                continue;
            }
            snapshots.add(new LockSnapshot(entry.getKey(), owner.ownerId, owner.sequenceName, owner.background,
                    owner.detail));
        }
        return snapshots;
    }

    public static synchronized boolean isHeldByBackground(Resource resource) {
        if (resource == null) {
            return false;
        }
        LockOwner owner = LOCKS.get(resource);
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

    private static void releaseNotDesired(String ownerId, EnumSet<Resource> desired) {
        List<Resource> remove = new ArrayList<>();
        for (Map.Entry<Resource, LockOwner> entry : LOCKS.entrySet()) {
            if (entry.getValue() != null
                    && ownerId.equals(entry.getValue().ownerId)
                    && !desired.contains(entry.getKey())) {
                remove.add(entry.getKey());
            }
        }
        for (Resource resource : remove) {
            LOCKS.remove(resource);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
