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
            || explorer.inDungeonRoom(mc)) return false;
        explorer.roomEntryCandidates = explorer.roomGeometry.findRoomExitCandidates(
            mc,
            bounds,
            (w, bx, by, bz) -> RoomExit.clearableExitColumn(w, explorer, bx, by, bz));
        explorer.roomEntryCandidates.removeIf(candidate -> candidate.length >= 10);
        explorer.roomEntryCandidateIndex = 0;
        final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        final int tx = explorer.target.xCoord, tz = explorer.target.zCoord;
        explorer.roomEntryCandidates.sort(
            (a, b) -> Double.compare(
                Math.hypot(a[3] + 0.5D - px, a[5] + 0.5D - pz) + Math.hypot(a[0] - tx, a[2] - tz),
                Math.hypot(b[3] + 0.5D - px, b[5] + 0.5D - pz) + Math.hypot(b[0] - tx, b[2] - tz)));
        if (startNextSearch(mc, explorer)) return true;
        explorer.roomEntryCandidates = null;
        return false;
    }

    public boolean startNextSearch(Minecraft mc, Explorer explorer) {
        if (explorer.roomEntryCandidates == null
            || explorer.roomEntryCandidateIndex >= explorer.roomEntryCandidates.size()) return false;
        explorer.activeRoomEntry = explorer.roomEntryCandidates.get(explorer.roomEntryCandidateIndex);
        explorer.walkingSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            explorer.activeRoomEntry[3],
            explorer.activeRoomEntry[4],
            explorer.activeRoomEntry[5],
            LootGameSolverConfig.explorationRange);
        explorer.roomEntryPlanning = true;
        explorer.roomEntrySearchNeedsResume = false;
        explorer.walking = true;
        explorer.searching = false;
        explorer.digging = false;
        explorer.route.clear();
        explorer.status = "Finding room entrance";
        return true;
    }
}
