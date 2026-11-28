package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.dryShore;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;
import com.kyroxova.lootgamesolver.explorer.pathfinder.ReturnRoutePolicy;

public class ExplorerGoalCoordinator {

    public boolean checkPendingSearches(Minecraft mc, Explorer explorer) {
        if (!explorer.returning && !explorer.searching
            && !explorer.movingToPuzzleCenter
            && explorer.target != null
            && explorer.reachableBoard(mc)) {
            handleArrivedAtBoard(mc, explorer);
            return true;
        }
        if (!explorer.returning && (explorer.target == null || explorer.target.isInvalid())) {
            explorer.stop(mc, "Target no longer loaded");
            return true;
        }
        if (shouldStartRoomEntrySearch(mc, explorer) && explorer.startRoomEntrySearch(mc)) return true;
        if (explorer.planningExistingAccess && explorer.existingAccessPlan != null) {
            advanceExistingAccessPlan(mc, explorer);
            return true;
        }
        if (explorer.returnJoinSearch != null) {
            advanceReturnJoinSearch(mc, explorer);
            return true;
        }
        return false;
    }

    private static void handleArrivedAtBoard(Minecraft mc, Explorer explorer) {
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround && explorer.trail.target != null) explorer.recordAccessPoint(px, py, pz);
        explorer.returnAvailable = explorer.trail.target != null && explorer.trail.target[0] == explorer.target.xCoord
            && explorer.trail.target[1] == explorer.target.yCoord
            && explorer.trail.target[2] == explorer.target.zCoord
            && (!explorer.trail.points.reverse(px, py, pz)
                .isEmpty() || explorer.trail.points.nearestIndex(px, py, pz, 32) >= 0);
        int[] arrivedAt = { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord };
        explorer.stop(mc, "Arrived at LootGame; opening puzzle");
        explorer.knownStructure = arrivedAt;
        explorer.puzzleArrivalPending = true;
    }

    private static boolean shouldStartRoomEntrySearch(Minecraft mc, Explorer explorer) {
        return !explorer.returning && !explorer.searching
            && !explorer.movingToPuzzleCenter
            && !explorer.roomEntryPlanning
            && !explorer.roomEntryCrossing
            && explorer.roomEntryCandidates == null
            && explorer.digging
            && explorer.trail.points.size() <= 1
            && explorer.target != null
            && explorer.knownStructure != null
            && Math.hypot(mc.thePlayer.posX - explorer.target.xCoord, mc.thePlayer.posZ - explorer.target.zCoord) <= 32;
    }

    private static void advanceExistingAccessPlan(Minecraft mc, Explorer explorer) {
        boolean complete = explorer.existingAccessPlan.advance(
            (x, y, z, nx, ny, nz) -> explorer.routePlanner.existingWalkCost(mc.theWorld, explorer, x, y, z, nx, ny, nz),
            32,
            System.nanoTime() + 2_500_000L);
        explorer.status = "Checking existing route: " + explorer.existingAccessPlan.expandedNodes() + " positions";
        if (!complete) return;
        List<int[]> existing = explorer.existingAccessPlan.path;
        explorer.existingAccessPlan = null;
        explorer.planningExistingAccess = false;
        if (!existing.isEmpty()) {
            explorer.route.clear();
            explorer.route.addAll(existing);
            explorer.digging = false;
            explorer.status = "Following safe existing entrance";
            return;
        }
        explorer.plan(mc);
        if (explorer.walkingSearch == null && explorer.route.isEmpty()) {
            if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
            else explorer.status = explorer.digging ? "No open route; planning a safe dig entrance"
                : "Walking to the selected dig entrance";
        } else {
            explorer.status = "Walking to the nearest safe entrance";
        }
    }

    private static void advanceReturnJoinSearch(Minecraft mc, Explorer explorer) {
        if (!explorer.returnJoinSearch.advance(3_000_000L)) {
            explorer.status = explorer.roomExitDugPathJoin ? "Finding a safe link to dug path"
                : "Finding a safe link to saved access route";
            return;
        }
        List<int[]> join = explorer.returnJoinSearch.path();
        boolean reachedJoin = explorer.returnJoinSearch.reachedGoal();
        explorer.returnJoinSearch = null;
        if (explorer.roomExitDugPathJoin) {
            handleDugPathJoinResult(mc, explorer, join, reachedJoin);
            return;
        }
        if (explorer.roomExitPlanning) {
            if (!handleRoomExitJoinResult(mc, explorer, join, reachedJoin)) return;
        } else if (explorer.returnTrailRoute != null) {
            if (!handleSavedTrailJoinResult(mc, explorer, join, reachedJoin)) return;
        } else {
            explorer.route.addAll(join);
        }
        if (explorer.route.isEmpty()) handleEmptyReturnJoinRoute(mc, explorer);
        else explorer.status = "Following saved access route out";
    }

    private static void handleDugPathJoinResult(Minecraft mc, Explorer explorer, List<int[]> join,
        boolean reachedJoin) {
        if (reachedJoin) {
            explorer.route.clear();
            explorer.route.addAll(join);
            explorer.roomExit.appendDugPath(explorer);
        } else if (!join.isEmpty()) {
            explorer.route.clear();
            explorer.route.addAll(join);
            explorer.status = "Following safe link to dug path";
        } else {
            explorer.roomExitDugPathJoin = false;
            if (!explorer.startSavedReturn(mc, true, 128) && !explorer.digTowardSavedExit(mc))
                explorer.retryExitLater(mc);
        }
    }

    private static boolean handleRoomExitJoinResult(Minecraft mc, Explorer explorer, List<int[]> join,
        boolean reachedJoin) {
        explorer.roomExitPlanning = false;
        if (reachedJoin) {
            explorer.route.clear();
            explorer.route.addAll(join);
            if (explorer.route.isEmpty()) explorer.route.add(
                new int[] { (int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ) });
            explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 0);
            if (explorer.activeRoomExit.length >= 12) explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 9);
            if (explorer.activeRoomExit.length >= 9) explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 6);
            explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 3);
            explorer.roomExitCrossing = true;
            explorer.roomExitJumpRetries = 0;
            explorer.roomExitNeedsResume = false;
            explorer.status = "Walking to room exit";
            return true;
        }
        if (!join.isEmpty()) {
            explorer.route.clear();
            explorer.route.addAll(join);
            explorer.roomExitNeedsResume = true;
            explorer.status = "Following exit route";
            return true;
        }
        explorer.roomExitCandidateIndex++;
        if (!explorer.startNextRoomExitSearch(mc) && !explorer.digTowardSavedExit(mc)) explorer.retryExitLater(mc);
        return false;
    }

    private static boolean handleSavedTrailJoinResult(Minecraft mc, Explorer explorer, List<int[]> join,
        boolean reachedJoin) {
        if (reachedJoin) {
            explorer.route.addAll(join);
            explorer.route.addAll(explorer.returnTrailRoute);
            explorer.returnTrailRoute = null;
            explorer.rejoiningSavedTrail = false;
            return true;
        }
        if (!join.isEmpty()) {
            explorer.route.addAll(join);
            explorer.status = "Following safe checkpoint toward the saved exit";
            return true;
        }
        if (!explorer.digTowardSavedExit(mc)) explorer.retryExitLater(mc);
        return false;
    }

    private static void handleEmptyReturnJoinRoute(Minecraft mc, Explorer explorer) {
        if (explorer.rejoiningSavedTrail && explorer.returnTrailRoute != null) {
            explorer.returnJoinSearch = new GroundPathfinder.Search(
                explorer.walkingTerrain(mc.theWorld),
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                explorer.returnJoinX,
                explorer.returnJoinY,
                explorer.returnJoinZ,
                Math.max(16, LootGameSolverConfig.explorationRange));
            return;
        }
        int exitX = (int) Math.floor(mc.thePlayer.posX);
        int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int exitZ = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) explorer.continueProgressiveSearch(mc, true);
        else explorer.retryExitLater(mc);
    }

    public void handleEmptyRoute(Minecraft mc, Explorer explorer) {
        if (explorer.continuationSearch != null && !explorer.returning
            && !explorer.digging
            && !explorer.movingToPuzzleCenter) {
            explorer.extendRoute(mc);
            if (!explorer.route.isEmpty() || explorer.continuationSearch != null) {
                if (explorer.route.isEmpty()) explorer.status = "Planning next route";
                return;
            }
        }
        if (handleEmptyRoomOrReturnState(mc, explorer)) return;
        if (explorer.returnDigging) {
            handleEmptyReturnDigging(mc, explorer);
            return;
        }
        if (explorer.returning) {
            handleEmptyReturning(mc, explorer);
            return;
        }
        handleEmptyApproachOrSearch(mc, explorer);
    }

    private static boolean handleEmptyRoomOrReturnState(Minecraft mc, Explorer explorer) {
        if (explorer.followingOpenExit) {
            explorer.followingOpenExit = false;
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (mc.theWorld.canBlockSeeTheSky(x, y, z)) explorer.continueProgressiveSearch(mc, true);
            else if (!explorer.startSavedReturn(mc, true, 128)) explorer.retryExitLater(mc);
            return true;
        }
        if (explorer.movingToPuzzleCenter) {
            explorer.reachPuzzleCenter(mc);
            return true;
        }
        if (explorer.roomEntryCrossing) {
            handleEmptyRoomEntryCrossing(mc, explorer);
            return true;
        }
        if (!explorer.returning && explorer.roomEntrySearchNeedsResume && explorer.activeRoomEntry != null) {
            explorer.roomEntrySearchNeedsResume = false;
            explorer.roomEntryPlanning = true;
            explorer.walkingSearch = new GroundPathfinder.Search(
                explorer.walkingTerrain(mc.theWorld),
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                explorer.activeRoomEntry[3],
                explorer.activeRoomEntry[5],
                LootGameSolverConfig.explorationRange);
            explorer.status = "Finding room entrance";
            return true;
        }
        if (!explorer.returning && explorer.walkingSearchNeedsResume) {
            explorer.walkingSearchNeedsResume = false;
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return true;
        }
        return false;
    }

    private static void handleEmptyRoomEntryCrossing(Minecraft mc, Explorer explorer) {
        explorer.roomEntryCrossing = false;
        explorer.activeRoomEntry = null;
        if (!explorer.insideDungeonRoom(mc)) {
            explorer.roomEntryCandidateIndex++;
            if (explorer.startNextRoomEntrySearch(mc)) {
                explorer.status = "Room entrance blocked; checking the next wall opening";
                return;
            }
            explorer.roomEntryCandidates = null;
            explorer.stop(mc, "Room entrance did not cross the wall");
            return;
        }
        explorer.roomEntryCandidates = null;
        explorer.plan(mc);
        if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
            explorer.status = "Following room route";
            return;
        }
        if (explorer.reachableBoard(mc)) explorer.signalPuzzleArrival(mc, "Arrived at LootGame; opening puzzle");
        else explorer.stop(mc, "No safe route to room center");
    }

    private static void handleEmptyReturnDigging(Minecraft mc, Explorer explorer) {
        int horizontal = Math.abs((int) Math.floor(mc.thePlayer.posX) - explorer.target.xCoord)
            + Math.abs((int) Math.floor(mc.thePlayer.posZ) - explorer.target.zCoord);
        int vertical = Math.abs((int) Math.floor(mc.thePlayer.boundingBox.minY) - explorer.target.yCoord);
        if (horizontal <= 3 && vertical <= 1) {
            explorer.resumeSavedReturnAfterDig();
            return;
        }
        if (!explorer.stairStep(mc) && !explorer.tryNextReturnDigAnchor(mc)) explorer.retryExitLater(mc);
    }

    private static void handleEmptyReturning(Minecraft mc, Explorer explorer) {
        if (explorer.roomExitDugPathJoin) {
            explorer.roomExit.startDugPathReturn(mc, explorer);
            return;
        }
        if (explorer.roomExitNeedsResume && explorer.activeRoomExit != null) {
            explorer.roomExitNeedsResume = false;
            explorer.returnJoinSearch = new GroundPathfinder.Search(
                explorer.walkingTerrain(mc.theWorld),
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                explorer.activeRoomExit[0],
                explorer.activeRoomExit[1],
                explorer.activeRoomExit[2],
                LootGameSolverConfig.explorationRange);
            explorer.roomExitPlanning = true;
            explorer.status = "Continuing exit route";
            return;
        }
        if (explorer.roomExitCrossing) {
            handleEmptyRoomExitCrossing(mc, explorer);
            return;
        }
        if (ReturnRoutePolicy.shouldContinueSavedTrailJoin(explorer.rejoiningSavedTrail, explorer.returnTrailRoute)) {
            explorer.returnJoinSearch = new GroundPathfinder.Search(
                explorer.walkingTerrain(mc.theWorld),
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ),
                explorer.returnJoinX,
                explorer.returnJoinY,
                explorer.returnJoinZ,
                Math.max(16, LootGameSolverConfig.explorationRange));
            explorer.status = "Continuing route to saved tunnel";
            return;
        }
        if (explorer.insideDungeonRoom(mc)) {
            if ((explorer.roomExitCandidates == null && explorer.startRoomExitSearch(mc))
                || explorer.digTowardSavedExit(mc)) return;
            explorer.retryExitLater(mc);
            return;
        }
        int exitX = (int) Math.floor(mc.thePlayer.posX);
        int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int exitZ = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) {
            explorer.blockedWalkNodes.clear();
            if (!explorer.startSavedReturn(mc, explorer.resumeSearchAfterReturn, 128)
                && !explorer.digTowardSavedExit(mc)) explorer.retryExitLater(mc);
            return;
        }
        explorer.returning = false;
        explorer.returnAvailable = false;
        explorer.removePersistedTrail();
        explorer.trail.excavating = false;
        if (explorer.resumeSearchAfterReturn) {
            explorer.continueProgressiveSearch(mc, true);
        } else {
            explorer.trail.points.clear();
            explorer.trail.target = null;
            explorer.stop(mc, "Returned to access entrance");
        }
    }

    private static void handleEmptyRoomExitCrossing(Minecraft mc, Explorer explorer) {
        explorer.roomExitCrossing = false;
        if (explorer.insideDungeonRoom(mc)) {
            explorer.roomExitCandidateIndex++;
            if (!explorer.startNextRoomExitSearch(mc) && !explorer.digTowardSavedExit(mc)) explorer.retryExitLater(mc);
            return;
        }
        explorer.roomExitCandidates = null;
        explorer.activeRoomExit = null;
        int ex = (int) Math.floor(mc.thePlayer.posX);
        int ey = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int ez = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.theWorld.canBlockSeeTheSky(ex, ey, ez)) {
            explorer.continueProgressiveSearch(mc, true);
        } else if (!explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer)
            && !explorer.startSavedReturn(mc, true, 128)
            && !explorer.digTowardSavedExit(mc)) {
                explorer.retryExitLater(mc);
            }
    }

    private static void handleEmptyApproachOrSearch(Minecraft mc, Explorer explorer) {
        if (!explorer.searching && !explorer.movingToPuzzleCenter && explorer.insideDungeonRoom(mc)) {
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                explorer.status = "Following room route";
                return;
            }
            if (explorer.reachableBoard(mc)) explorer.signalPuzzleArrival(mc, "Arrived at LootGame; opening puzzle");
            else explorer.stop(mc, "No safe route to room center");
            return;
        }
        if (explorer.seekingDryEntrance) {
            explorer.seekingDryEntrance = false;
            explorer.digging = true;
            explorer.entranceSelected = true;
            explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        }
        if (needsDryShoreRelocation(mc, explorer)) {
            explorer.digging = false;
            if (!explorer.findDryEntrance(mc))
                explorer.stop(mc, "No verified dry entrance near the water; digging stopped");
            return;
        }
        if (!explorer.searching && !explorer.digging && tryPlanOrEnterStructure(mc, explorer)) return;
        if (explorer.searching) {
            advanceSearchingEmptyRoute(mc, explorer);
            return;
        }
        if (explorer.digging && advanceDiggingEmptyRoute(mc, explorer)) return;
        if (!explorer.insideDungeonRoom(mc)) {
            if (explorer.startRoomEntrySearch(mc)
                || (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc))) return;
            explorer.stop(mc, "No safe route into the puzzle room");
            return;
        }
        explorer.signalPuzzleArrival(mc, "Arrived beside LootGame");
    }

    private static boolean needsDryShoreRelocation(Minecraft mc, Explorer explorer) {
        if (explorer.entranceSelected) return false;
        boolean nearLowTarget = !explorer.searching && explorer.target.yCoord < mc.thePlayer.boundingBox.minY - 2
            && Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
                + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord) <= 32;
        if (!explorer.digging && !nearLowTarget) return false;
        return !dryShore(
            mc.theWorld,
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ));
    }

    private static boolean tryPlanOrEnterStructure(Minecraft mc, Explorer explorer) {
        double horizontal = Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
            + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord);
        if (horizontal > 32 || (horizontal > 3 && explorer.target.yCoord >= mc.thePlayer.boundingBox.minY - 2)) {
            if (!explorer.walkingPlanFailed) explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return true;
            if ((horizontal <= 40 && explorer.beginDiggingAtCurrentPosition(mc)) || explorer.startRoomEntrySearch(mc)
                || explorer.beginExistingAccessSearch(mc)) return true;
            if (LootGameSolverConfig.staircaseEnabled && horizontal <= 40 && !mc.thePlayer.isInWater()) {
                if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
            } else explorer.stop(mc, "No verified route around the obstruction");
            return true;
        }
        if (LootGameSolverConfig.staircaseEnabled
            && Math.abs(explorer.target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1) {
            if (explorer.startRoomEntrySearch(mc) || explorer.beginExistingAccessSearch(mc)) return true;
            if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
            return true;
        }
        return false;
    }

    private static void advanceSearchingEmptyRoute(Minecraft mc, Explorer explorer) {
        explorer.scan(mc);
        if (explorer.target != null) {
            explorer.beginAccessTrail(mc);
            explorer.searching = false;
            if (explorer.reachableBoard(mc)) {
                explorer.signalPuzzleArrival(mc, "Found nearby LootGame");
                return;
            }
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return;
            if (LootGameSolverConfig.staircaseEnabled && explorer.target.yCoord < mc.thePlayer.boundingBox.minY - 2
                && mc.theWorld.blockExists(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord)) {
                if (explorer.startRoomEntrySearch(mc)) return;
                if (!explorer.selectDigEntrance(mc) && !explorer.nextSearchLeg(mc))
                    explorer.stop(mc, "No safe dig entrance near the structure");
                return;
            }
        }
        if (!explorer.nextSearchLeg(mc)) {
            explorer.rotateSearch(mc);
            explorer.nextSearchLeg(mc);
        }
    }

    private static boolean advanceDiggingEmptyRoute(Minecraft mc, Explorer explorer) {
        boolean farFromTarget = Math.abs(explorer.target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1
            || Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
                + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord) > 3;
        if (farFromTarget) {
            if (!explorer.stairStep(mc)) {
                if (explorer.stairSteps == 0 && ++explorer.approachAttempts <= 3) {
                    explorer.excavationSearch = null;
                    explorer.excavationRoute.clear();
                    explorer.miningAim.reset();
                    explorer.digging = false;
                    explorer.entranceSelected = false;
                    if (explorer.findDryEntrance(mc)) {
                        explorer.status = "Checking a different supported entrance";
                        return true;
                    }
                }
                explorer.stop(mc, "Access route blocked; keeping the existing entrance for reuse");
            }
            return true;
        }
        explorer.digging = false;
        explorer.plan(mc);
        return !explorer.route.isEmpty() || explorer.walkingSearch != null;
    }
}
