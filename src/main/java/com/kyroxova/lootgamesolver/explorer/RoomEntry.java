package com.kyroxova.lootgamesolver.explorer;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public final class RoomEntry {

    public List<int[]> candidates;
    public int candidateIndex;
    public int[] active;
    public boolean planning;
    public boolean crossing;
    public boolean searchNeedsResume;

    public void reset() {
        candidates = null;
        candidateIndex = 0;
        active = null;
        planning = false;
        crossing = false;
        searchNeedsResume = false;
    }

    public boolean startSearch(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null || explorer.target == null || explorer.knownStructure == null)
            return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds == null || explorer.target.xCoord <= bounds[0]
            || explorer.target.xCoord >= bounds[1]
            || explorer.target.zCoord <= bounds[2]
            || explorer.target.zCoord >= bounds[3]
            || (x > bounds[0] && x < bounds[1] && z > bounds[2] && z < bounds[3] && y <= explorer.target.yCoord + 3))
            return false;
        candidates = explorer.roomGeometry.findRoomExitCandidates(
            mc,
            bounds,
            (w, bx, by, bz) -> RoomExit.clearableExitColumn(w, explorer, bx, by, bz));
        candidates.removeIf(candidate -> candidate.length >= 10);
        candidateIndex = 0;
        final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        final int tx = explorer.target.xCoord, tz = explorer.target.zCoord;
        candidates.sort(
            (a, b) -> Double.compare(
                Math.hypot(a[3] + 0.5D - px, a[5] + 0.5D - pz) + Math.hypot(a[0] - tx, a[2] - tz),
                Math.hypot(b[3] + 0.5D - px, b[5] + 0.5D - pz) + Math.hypot(b[0] - tx, b[2] - tz)));
        if (startNextSearch(mc, explorer)) return true;
        candidates = null;
        return false;
    }

    public boolean startNextSearch(Minecraft mc, Explorer explorer) {
        if (candidates == null || candidateIndex >= candidates.size()) return false;
        active = candidates.get(candidateIndex);
        explorer.walkingSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            active[3],
            active[4],
            active[5],
            LootGameSolverConfig.explorationRange);
        planning = true;
        searchNeedsResume = false;
        explorer.walking = true;
        explorer.searching = false;
        explorer.digging = false;
        explorer.route.clear();
        explorer.status = "Finding room entrance";
        return true;
    }
}
