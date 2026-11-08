package com.kyroxova.lootgamesolver.client;

import java.io.File;
import java.io.IOException;

final class ExplorerTrailSession {

    final AccessTrail points = new AccessTrail();
    final AccessTrailStore store;
    int[] target;
    String worldId;
    int dimension;
    int lastSaveTick;
    boolean dirty;
    boolean excavating;

    ExplorerTrailSession(File file) {
        store = new AccessTrailStore(file);
    }

    boolean persist(boolean force, int ticks) {
        if (!dirty || target == null || worldId == null || points.size() < 2) return true;
        if (!force && ticks - lastSaveTick < 10) return true;
        try {
            store.save(worldId, dimension, target[0], target[1], target[2], points.snapshot());
            dirty = false;
            lastSaveTick = ticks;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    boolean removePersisted() {
        if (target == null || worldId == null) return true;
        try {
            store.remove(worldId, dimension, target[0], target[1], target[2]);
            dirty = false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
