package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.solidCheckpoint;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public final class WalkPathfinder {

    public GroundPathfinder.Search walkingSearch;
    public GroundPathfinder.Search continuationSearch;
    public int continuationX, continuationZ;
    public boolean needsResume;

    public void reset() {
        walkingSearch = null;
        continuationSearch = null;
        continuationX = 0;
        continuationZ = 0;
        needsResume = false;
    }

    public void start(GroundPathfinder.Terrain terrain, int sx, int sy, int sz, int tx, int ty, int tz, int range) {
        walkingSearch = new GroundPathfinder.Search(terrain, sx, sy, sz, tx, ty, tz, range);
        needsResume = false;
    }

    public void startContinuation(GroundPathfinder.Terrain terrain, int sx, int sy, int sz, int tx, int ty, int tz,
        int range) {
        continuationX = tx;
        continuationZ = tz;
        continuationSearch = new GroundPathfinder.Search(terrain, sx, sy, sz, tx, ty, tz, range);
    }

    public boolean active() {
        return walkingSearch != null;
    }

    public boolean continuationActive() {
        return continuationSearch != null;
    }

    public boolean routeWalkable(Minecraft mc, List<int[]> points) {
        if (mc == null || mc.theWorld == null || points == null || points.isEmpty()) return false;
        int[] endpoint = points.get(points.size() - 1);
        return solidCheckpoint(mc.theWorld, endpoint[0], endpoint[1], endpoint[2]);
    }
}
