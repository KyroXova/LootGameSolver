package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.explorer.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.passable;
import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.safe;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerRoutePlanner {

    public GroundPathfinder.Terrain walkingTerrain(Minecraft mc, Explorer explorer, final World terrainWorld) {
        int px = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.posX);
        int py = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = mc.thePlayer == null ? 0 : (int) Math.floor(mc.thePlayer.posZ);
        final boolean surfaceStart = mc.thePlayer != null && terrainWorld.blockExists(px, py, pz)
            && terrainWorld.getHeightValue(px, pz) - py <= 6;
        final boolean protectedJourney = explorer.returning || explorer.digging
            || explorer.movingToPuzzleCenter
            || explorer.roomEntryPlanning
            || explorer.roomEntryCrossing
            || explorer.roomExitPlanning
            || explorer.roomExitCrossing;
        final boolean structureApproach = explorer.knownStructure != null && !explorer.searching;
        final int gx = explorer.target == null ? px : explorer.target.xCoord;
        final int gz = explorer.target == null ? pz : explorer.target.zCoord;
        return new ExplorerWalkingTerrain(
            terrainWorld,
            explorer.blockedWalkNodes,
            surfaceStart && !LootGameSolverConfig.caveMode,
            protectedJourney,
            structureApproach,
            gx,
            gz,
            () -> explorer.movingToPuzzleCenter,
            () -> explorer.target);
    }

    public int existingWalkCost(World w, Explorer explorer, int x, int y, int z, int nx, int ny, int nz) {
        if (Math.abs(nx - x) + Math.abs(nz - z) > 1 || Math.abs(ny - y) > 1) return -1;
        if (explorer.blockedWalkNodes.contains(supportKey(nx, ny, nz))) return -1;
        if (!safe(w, nx, ny, nz) || w.getBlock(nx, ny, nz)
            .getMaterial()
            .isLiquid()) return -1;
        if (ny > y && !passable(w, x, y + 2, z)) return -1;
        if (ny < y && !passable(w, nx, y + 1, nz)) return -1;
        return 10 + 10 * Math.abs(ny - y);
    }

    public void plan(final Minecraft mc, Explorer explorer) {
        explorer.walkingPlanFailed = false;
        explorer.walkingSearchNeedsResume = false;
        explorer.route.clear();
        explorer.extendedFrom = null;
        explorer.continuationSearch = null;
        explorer.segmentX = mc.thePlayer.posX;
        explorer.segmentZ = mc.thePlayer.posZ;
        explorer.walkingSearch = new GroundPathfinder.Search(
            walkingTerrain(mc, explorer, mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            explorer.target.xCoord,
            explorer.target.zCoord,
            LootGameSolverConfig.explorationRange);
        if (explorer.walkingSearch.advance(3_000_000L)) {
            explorer.route.addAll(explorer.walkingSearch.path());
            explorer.walkingPlanFailed = !explorer.walkingSearch.reachedGoal();
            explorer.walkingSearchNeedsResume = explorer.walkingPlanFailed && !explorer.route.isEmpty();
            explorer.walkingSearch = null;
        }
    }

    public void extendRoute(Minecraft mc, Explorer explorer) {
        int[] tail = explorer.route.isEmpty() ? explorer.extendedFrom : explorer.route.get(explorer.route.size() - 1);
        if (tail == null) return;
        if (tail == explorer.extendedFrom && explorer.continuationSearch == null) return;
        int gx = explorer.searching ? explorer.searchGoalX : explorer.target.xCoord;
        int gz = explorer.searching ? explorer.searchGoalZ : explorer.target.zCoord;
        if ((tail != explorer.extendedFrom || explorer.continuationSearch == null)
            && Math.abs(tail[0] - gx) + Math.abs(tail[2] - gz) <= 2) {
            if (!explorer.searching || explorer.search == null) return;
            int[] goal = explorer.search.next();
            if (goal == null) return;
            gx = goal[0];
            gz = goal[1];
        }
        if (tail != explorer.extendedFrom || explorer.continuationSearch == null) {
            explorer.extendedFrom = tail;
            explorer.continuationX = gx;
            explorer.continuationZ = gz;
            explorer.continuationSearch = new GroundPathfinder.Search(
                walkingTerrain(mc, explorer, mc.theWorld),
                tail[0],
                tail[1],
                tail[2],
                gx,
                gz,
                LootGameSolverConfig.explorationRange);
        }
        boolean finished = explorer.continuationSearch.advance(3_000_000L);
        List<int[]> continuation = explorer.continuationSearch.path();
        if (!finished && (explorer.route.size() > 32 || continuation.size() < (explorer.route.isEmpty() ? 1 : 6)))
            return;
        explorer.continuationSearch = null;
        if (continuation.isEmpty()) {
            explorer.extendedFrom = null;
            return;
        }
        boolean joined = shortcutToContinuation(mc, explorer, continuation);
        if (!joined) NavigationSteering.appendWithoutLoops(explorer.route, continuation);
        explorer.walkingSearchNeedsResume = !finished;
        if (explorer.searching) {
            explorer.searchGoalX = explorer.continuationX;
            explorer.searchGoalZ = explorer.continuationZ;
            int[] end = explorer.route.get(explorer.route.size() - 1);
            explorer.target.xCoord = end[0];
            explorer.target.yCoord = end[1];
            explorer.target.zCoord = end[2];
        }
    }

    public boolean shortcutToContinuation(Minecraft mc, Explorer explorer, List<int[]> continuation) {
        if (!mc.thePlayer.onGround || explorer.route.size() < 2) return false;
        for (int[] step : explorer.route) if (step.length > 3 && step[3] != 0) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] current = explorer.route.get(0);
        double forwardX = current[0] + 0.5D - mc.thePlayer.posX;
        double forwardZ = current[2] + 0.5D - mc.thePlayer.posZ;
        for (int i = continuation.size() - 1; i >= 0; i--) {
            int[] step = continuation.get(i);
            int dx = Math.abs(step[0] - x), dz = Math.abs(step[2] - z);
            if (step[1] != y || dx > 1
                || dz > 1
                || dx + dz == 0
                || (step[0] + 0.5D - mc.thePlayer.posX) * forwardX + (step[2] + 0.5D - mc.thePlayer.posZ) * forwardZ
                    <= 0D
                || !safe(mc.theWorld, step[0], y, step[2])
                || dx == 1 && dz == 1 && (!safe(mc.theWorld, step[0], y, z) || !safe(mc.theWorld, x, y, step[2])))
                continue;
            if (explorer.route.size() + i < 3) return false;
            explorer.route.clear();
            explorer.route.addAll(continuation.subList(i, continuation.size()));
            explorer.segmentX = mc.thePlayer.posX;
            explorer.segmentZ = mc.thePlayer.posZ;
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            explorer.status = "Taking shorter route";
            return true;
        }
        return false;
    }

    public void advanceWalkingSearch(Minecraft mc, Explorer explorer) {
        if (explorer.walkingSearch == null) return;
        if (!explorer.walkingSearch.advance(3_000_000L)) {
            List<int[]> partial = explorer.walkingSearch.path();
            if (partial.isEmpty()) {
                explorer.status = "Continuing route search";
                return;
            }
            explorer.route.clear();
            explorer.route.addAll(partial);
            explorer.walkingSearch = null;
            if (explorer.roomEntryPlanning) {
                explorer.roomEntryPlanning = false;
                explorer.roomEntrySearchNeedsResume = true;
            } else if (!explorer.movingToPuzzleCenter) {
                explorer.walkingSearchNeedsResume = true;
            }
            explorer.segmentX = mc.thePlayer.posX;
            explorer.segmentZ = mc.thePlayer.posZ;
            explorer.status = "Following safe checkpoint";
        } else {
            if (explorer.roomEntryPlanning) {
                boolean reachedEntry = explorer.walkingSearch.reachedGoal();
                List<int[]> entryPath = explorer.walkingSearch.path();
                explorer.walkingSearch = null;
                explorer.roomEntryPlanning = false;
                explorer.roomEntrySearchNeedsResume = false;
                if (reachedEntry) {
                    explorer.route.clear();
                    explorer.route.addAll(entryPath);
                    explorer.appendRoomExitWaypoint(explorer.activeRoomEntry, 3);
                    explorer.appendRoomExitWaypoint(explorer.activeRoomEntry, 6);
                    explorer.appendRoomExitWaypoint(explorer.activeRoomEntry, 0);
                    explorer.roomEntryCrossing = true;
                    explorer.segmentX = mc.thePlayer.posX;
                    explorer.segmentZ = mc.thePlayer.posZ;
                    explorer.status = "Walking through room entrance";
                } else {
                    explorer.roomEntryCandidateIndex++;
                    if (explorer.startNextRoomEntrySearch(mc)) return;
                    explorer.roomEntryCandidates = null;
                    explorer.stop(mc, "No walkable room entrance");
                    return;
                }
            } else {
                boolean reachedCenter = explorer.movingToPuzzleCenter && explorer.walkingSearch.reachedGoal();
                boolean reachedGoal = explorer.walkingSearch.reachedGoal();
                explorer.route.addAll(explorer.walkingSearch.path());
                explorer.walkingSearch = null;
                explorer.walkingSearchNeedsResume = !explorer.movingToPuzzleCenter && !reachedGoal && !explorer.route.isEmpty();
                if (explorer.movingToPuzzleCenter && reachedCenter && explorer.target != null) {
                    explorer.puzzleCenter.appendPuzzleCenterWaypoint(
                        explorer,
                        new int[] { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord });
                }
                explorer.walkingPlanFailed = !reachedGoal;
            }
        }
    }
}
