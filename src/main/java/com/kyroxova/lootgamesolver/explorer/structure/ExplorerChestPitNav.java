package com.kyroxova.lootgamesolver.explorer.structure;

import com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import static com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry.isRewardChest;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.solidCheckpoint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public final class ExplorerChestPitNav {

    private List<int[]> chestPitExits;
    private int chestPitExitIndex;
    private int chestPitTrailIndex = -1;
    private int[] chestPitSource;
    private List<int[]> chestPitApproach;
    private GroundPathfinder.Search chestPitApproachSearch;
    private GroundPathfinder.Search chestPitSearch;

    public boolean active() {
        return chestPitExits != null;
    }

    public void reset() {
        chestPitSearch = null;
        chestPitApproachSearch = null;
        chestPitApproach = null;
        chestPitSource = null;
        chestPitExits = null;
        chestPitTrailIndex = -1;
    }

    public boolean start(Minecraft mc, Explorer explorer, boolean resumeSearch) {
        if (mc.thePlayer == null || mc.theWorld == null || explorer.trail.points.size() < 2) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int trailIndex = explorer.trail.points.nearestIndex(x, y, z, 128);
        if (trailIndex < 0) return false;

        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        List<int[]> exits = new ArrayList<>();
        int chestSides = 0;
        for (int[] direction : directions) {
            boolean chest = false;
            for (int dy = -2; dy <= 1 && !chest; dy++)
                chest = isRewardChest(mc.theWorld, x + direction[0], y + dy, z + direction[1]);
            if (!chest) continue;
            chestSides++;
            for (int distance = 1; distance <= 5; distance++) for (int dy = -1; dy <= 2; dy++) {
                int ex = x + direction[0] * distance, ey = y + dy, ez = z + direction[1] * distance;
                if (solidCheckpoint(mc.theWorld, ex, ey, ez)) exits.add(new int[] { ex, ey, ez });
            }
        }
        if (chestSides < 3 || exits.isEmpty()) return false;

        int[] trailTarget = explorer.trail.points.pointAt(trailIndex);
        exits.sort(
            (a, b) -> Double.compare(
                Math.abs(a[0] - trailTarget[0]) + Math.abs(a[2] - trailTarget[2]),
                Math.abs(b[0] - trailTarget[0]) + Math.abs(b[2] - trailTarget[2])));
        chestPitExits = exits;
        chestPitExitIndex = 0;
        chestPitTrailIndex = trailIndex;
        chestPitSource = new int[] { x, y, z };
        chestPitApproach = null;
        chestPitApproachSearch = null;
        chestPitSearch = null;
        explorer.clearReturnTransits();
        explorer.returning = true;
        explorer.returnAvailable = false;
        explorer.resumeSearchAfterReturn = resumeSearch;
        explorer.walking = true;
        explorer.searching = false;
        explorer.digging = false;
        explorer.route.clear();
        explorer.segmentX = mc.thePlayer.posX;
        explorer.segmentZ = mc.thePlayer.posZ;
        beginNextSearch(mc, explorer);
        explorer.status = "Planning a jump out of the reward chest pit";
        return true;
    }

    private void beginNextSearch(Minecraft mc, Explorer explorer) {
        if (chestPitExits == null || chestPitExitIndex >= chestPitExits.size()) {
            chestPitSearch = null;
            chestPitApproachSearch = null;
            return;
        }
        int[] exit = chestPitExits.get(chestPitExitIndex);
        int[] join = explorer.trail.points.pointAt(chestPitTrailIndex);
        if (join == null || chestPitSource == null) {
            chestPitSearch = null;
            chestPitApproachSearch = null;
            return;
        }
        chestPitApproachSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            chestPitSource[0],
            chestPitSource[1],
            chestPitSource[2],
            exit[0],
            exit[1],
            exit[2],
            8);
        chestPitApproach = null;
        chestPitSearch = null;
    }

    private void beginJoinSearch(Minecraft mc, Explorer explorer, int[] exit) {
        int[] join = explorer.trail.points.pointAt(chestPitTrailIndex);
        if (join == null) {
            chestPitSearch = null;
            return;
        }
        chestPitSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            exit[0],
            exit[1],
            exit[2],
            join[0],
            join[1],
            join[2],
            Math.max(128, LootGameSolverConfig.explorationRange));
    }

    public boolean advance(Minecraft mc, Explorer explorer) {
        if (chestPitExits == null) return false;
        if (chestPitApproachSearch != null) {
            explorer.status = "Finding a reachable jump out of the chest pit";
            if (!chestPitApproachSearch.advance(3_000_000L)) return true;
            if (!chestPitApproachSearch.reachedGoal()) {
                chestPitApproachSearch = null;
                chestPitExitIndex++;
                beginNextSearch(mc, explorer);
                return true;
            }
            chestPitApproach = chestPitApproachSearch.path();
            chestPitApproachSearch = null;
            beginJoinSearch(mc, explorer, chestPitExits.get(chestPitExitIndex));
            return true;
        }
        if (chestPitSearch == null) {
            reset();
            if (explorer.startSavedReturn(mc, true, 128)) return true;
            if (explorer.startRoomExitSearch(mc)) return true;
            explorer.retryExitLater(mc);
            return true;
        }
        explorer.status = "Finding a clear route from the chest pit to the saved tunnel";
        if (!chestPitSearch.advance(3_000_000L)) return true;
        List<int[]> path = chestPitSearch.path();
        if (chestPitSearch.reachedGoal()) {
            explorer.route.clear();
            if (chestPitApproach != null) {
                for (int[] step : chestPitApproach) explorer.appendRoutePoint(explorer.route, step);
            } else {
                explorer.appendRoutePoint(explorer.route, chestPitExits.get(chestPitExitIndex));
            }
            for (int[] step : path) explorer.appendRoutePoint(explorer.route, step);
            List<int[]> saved = explorer.trail.points.reverseFrom(chestPitTrailIndex);
            if (explorer.savedRouteWalkable(mc, saved)) {
                for (int[] step : saved) explorer.appendRoutePoint(explorer.route, step);
            } else {
                reset();
                if (explorer.startSavedReturn(mc, true, 128)) return true;
                if (explorer.startRoomExitSearch(mc)) return true;
                explorer.retryExitLater(mc);
                return true;
            }
            reset();
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            explorer.segmentX = mc.thePlayer.posX;
            explorer.segmentZ = mc.thePlayer.posZ;
            explorer.status = "Jumping clear of the reward chests; returning through the saved tunnel";
            return true;
        }
        chestPitExitIndex++;
        if (chestPitExitIndex < chestPitExits.size()) {
            beginNextSearch(mc, explorer);
            return true;
        }
        reset();
        if (explorer.startSavedReturn(mc, true, 128)) return true;
        if (explorer.startRoomExitSearch(mc)) return true;
        explorer.retryExitLater(mc);
        return true;
    }
}
