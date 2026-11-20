package com.kyroxova.lootgamesolver.explorer.trail;

import java.io.File;
import java.io.IOException;

public final class ExplorerTrailSession {

    public final AccessTrail points = new AccessTrail();
    public final AccessTrailStore store;
    public int[] target;
    public String worldId;
    public int dimension;
    public int lastSaveTick;
    public boolean dirty;
    public boolean excavating;

    public ExplorerTrailSession(File file) {
        store = new AccessTrailStore(file);
    }

    public boolean persist(boolean force, int ticks) {
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

    public boolean removePersisted() {
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
