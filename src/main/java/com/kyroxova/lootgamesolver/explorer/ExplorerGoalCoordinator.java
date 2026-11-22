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
            if (mc.thePlayer.onGround && explorer.trail.target != null) {
                explorer.recordAccessPoint(
                    (int) Math.floor(mc.thePlayer.posX),
                    (int) Math.floor(mc.thePlayer.boundingBox.minY),
                    (int) Math.floor(mc.thePlayer.posZ));
            }
            explorer.returnAvailable = explorer.trail.target != null
                && explorer.trail.target[0] == explorer.target.xCoord
                && explorer.trail.target[1] == explorer.target.yCoord
                && explorer.trail.target[2] == explorer.target.zCoord
                && (!explorer.trail.points
                    .reverse(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ))
                    .isEmpty()
                    || explorer.trail.points.nearestIndex(
                        (int) Math.floor(mc.thePlayer.posX),
                        (int) Math.floor(mc.thePlayer.boundingBox.minY),
                        (int) Math.floor(mc.thePlayer.posZ),
                        32) >= 0);
            int[] arrivedAt = { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord };
            explorer.stop(mc, "Arrived at LootGame; opening puzzle");
            explorer.knownStructure = arrivedAt;
            explorer.puzzleArrivalPending = true;
            return true;
        }
        if (!explorer.returning && (explorer.target == null || explorer.target.isInvalid())) {
            explorer.stop(mc, "Target no longer loaded");
            return true;
        }
        if (!explorer.returning && !explorer.searching
            && !explorer.movingToPuzzleCenter
            && !explorer.roomEntryPlanning
            && !explorer.roomEntryCrossing
            && explorer.roomEntryCandidates == null
            && explorer.digging
            && explorer.trail.points.size() <= 1
            && explorer.target != null
            && explorer.knownStructure != null
            && Math.hypot(mc.thePlayer.posX - explorer.target.xCoord, mc.thePlayer.posZ - explorer.target.zCoord) <= 32
            && explorer.startRoomEntrySearch(mc)) return true;
        if (explorer.planningExistingAccess && explorer.existingAccessPlan != null) {
            boolean complete = explorer.existingAccessPlan.advance(
                (x, y, z, nx, ny, nz) -> explorer.routePlanner
                    .existingWalkCost(mc.theWorld, explorer, x, y, z, nx, ny, nz),
                32,
                System.nanoTime() + 2_500_000L);
            explorer.status = "Checking existing route: " + explorer.existingAccessPlan.expandedNodes() + " positions";
            if (complete) {
                List<int[]> existing = explorer.existingAccessPlan.path;
                explorer.existingAccessPlan = null;
                explorer.planningExistingAccess = false;
                if (!existing.isEmpty()) {
                    explorer.route.clear();
                    explorer.route.addAll(existing);
                    explorer.digging = false;
                    explorer.status = "Following safe existing entrance";
                } else {
                    explorer.plan(mc);
                    if (explorer.walkingSearch == null && explorer.route.isEmpty()) {
                        if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
                        else explorer.status = explorer.digging ? "No open route; planning a safe dig entrance"
                            : "Walking to the selected dig entrance";
                    } else explorer.status = "Walking to the nearest safe entrance";
                }
            }
            return true;
        }
        if (explorer.returnJoinSearch != null) {
            if (!explorer.returnJoinSearch.advance(3_000_000L)) {
                explorer.status = explorer.roomExitDugPathJoin ? "Finding a safe link to dug path"
                    : "Finding a safe link to saved access route";
                return true;
            }
            List<int[]> join = explorer.returnJoinSearch.path();
            boolean reachedJoin = explorer.returnJoinSearch.reachedGoal();
            explorer.returnJoinSearch = null;
            if (explorer.roomExitDugPathJoin) {
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
                    if (explorer.startSavedReturn(mc, true, 128) || explorer.digTowardSavedExit(mc)) return true;
                    explorer.retryExitLater(mc);
                }
                return true;
            }
            if (explorer.roomExitPlanning) {
                explorer.roomExitPlanning = false;
                if (reachedJoin) {
                    explorer.route.clear();
                    explorer.route.addAll(join);
                    if (explorer.route.isEmpty()) explorer.route.add(
                        new int[] { (int) Math.floor(mc.thePlayer.posX),
                            (int) Math.floor(mc.thePlayer.boundingBox.minY), (int) Math.floor(mc.thePlayer.posZ) });
                    explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 0);
                    if (explorer.activeRoomExit.length >= 12)
                        explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 9);
                    if (explorer.activeRoomExit.length >= 9)
                        explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 6);
                    explorer.appendRoomExitWaypoint(explorer.activeRoomExit, 3);
                    explorer.roomExitCrossing = true;
                    explorer.roomExitJumpRetries = 0;
                    explorer.roomExitNeedsResume = false;
                    explorer.status = "Walking to room exit";
                } else if (!join.isEmpty()) {
                    explorer.route.clear();
                    explorer.route.addAll(join);
                    explorer.roomExitNeedsResume = true;
                    explorer.status = "Following exit route";
                } else {
                    explorer.roomExitCandidateIndex++;
                    if (explorer.startNextRoomExitSearch(mc)) return true;
                    if (explorer.digTowardSavedExit(mc)) return true;
                    explorer.retryExitLater(mc);
                    return true;
                }
            } else if (explorer.returnTrailRoute != null && reachedJoin) {
                explorer.route.addAll(join);
                explorer.route.addAll(explorer.returnTrailRoute);
                explorer.returnTrailRoute = null;
                explorer.rejoiningSavedTrail = false;
            } else if (explorer.returnTrailRoute != null && !join.isEmpty()) {
                explorer.route.addAll(join);
                explorer.status = "Following safe checkpoint toward the saved exit";
            } else if (explorer.returnTrailRoute != null) {
                if (explorer.digTowardSavedExit(mc)) return true;
                explorer.retryExitLater(mc);
                return true;
            } else {
                explorer.route.addAll(join);
            }
            if (explorer.route.isEmpty()) {
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
                    return true;
                }
                int exitX = (int) Math.floor(mc.thePlayer.posX);
                int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int exitZ = (int) Math.floor(mc.thePlayer.posZ);
                if (mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) explorer.continueProgressiveSearch(mc, true);
                else explorer.retryExitLater(mc);
            } else explorer.status = "Following saved access route out";
            return true;
        }
        return false;
    }

    public void handleEmptyRoute(Minecraft mc, Explorer explorer) {
        if (explorer.continuationSearch != null && !explorer.returning
            && !explorer.digging
            && !explorer.movingToPuzzleCenter) {
            explorer.extendRoute(mc);
            if (!explorer.route.isEmpty()) return;
            if (explorer.continuationSearch != null) {
                explorer.status = "Planning next route";
                return;
            }
        }
        if (explorer.followingOpenExit) {
            explorer.followingOpenExit = false;
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (mc.theWorld.canBlockSeeTheSky(x, y, z)) explorer.continueProgressiveSearch(mc, true);
            else if (!explorer.startSavedReturn(mc, true, 128)) explorer.retryExitLater(mc);
            return;
        }
        if (explorer.movingToPuzzleCenter) {
            explorer.reachPuzzleCenter(mc);
            return;
        }
        if (explorer.roomEntryCrossing) {
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
            if (explorer.reachableBoard(mc)) {
                explorer.signalPuzzleArrival(mc, "Arrived at LootGame; opening puzzle");
                return;
            }
            explorer.stop(mc, "No safe route to room center");
            return;
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
            return;
        }
        if (!explorer.returning && explorer.walkingSearchNeedsResume) {
            explorer.walkingSearchNeedsResume = false;
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return;
        }
        if (explorer.returnDigging) {
            int horizontal = Math.abs((int) Math.floor(mc.thePlayer.posX) - explorer.target.xCoord)
                + Math.abs((int) Math.floor(mc.thePlayer.posZ) - explorer.target.zCoord);
            int vertical = Math.abs((int) Math.floor(mc.thePlayer.boundingBox.minY) - explorer.target.yCoord);
            if (horizontal <= 3 && vertical <= 1) {
                explorer.resumeSavedReturnAfterDig();
                return;
            }
            if (!explorer.stairStep(mc)) {
                if (explorer.tryNextReturnDigAnchor(mc)) return;
                explorer.retryExitLater(mc);
            }
            return;
        }
        if (explorer.returning) {
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
                explorer.roomExitCrossing = false;
                if (explorer.insideDungeonRoom(mc)) {
                    explorer.roomExitCandidateIndex++;
                    if (explorer.startNextRoomExitSearch(mc)) return;
                    if (explorer.digTowardSavedExit(mc)) return;
                    explorer.retryExitLater(mc);
                    return;
                }
                explorer.roomExitCandidates = null;
                explorer.activeRoomExit = null;
                int ex = (int) Math.floor(mc.thePlayer.posX);
                int ey = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int ez = (int) Math.floor(mc.thePlayer.posZ);
                if (mc.theWorld.canBlockSeeTheSky(ex, ey, ez)) {
                    explorer.continueProgressiveSearch(mc, true);
                } else if (explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer)) {
                    return;
                } else if (!explorer.startSavedReturn(mc, true, 128) && !explorer.digTowardSavedExit(mc)) {
                    explorer.retryExitLater(mc);
                }
                return;
            }
            if (ReturnRoutePolicy
                .shouldContinueSavedTrailJoin(explorer.rejoiningSavedTrail, explorer.returnTrailRoute)) {
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
                if (explorer.roomExitCandidates == null && explorer.startRoomExitSearch(mc)) return;
                if (explorer.digTowardSavedExit(mc)) return;
                explorer.retryExitLater(mc);
                return;
            }
            int exitX = (int) Math.floor(mc.thePlayer.posX);
            int exitY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int exitZ = (int) Math.floor(mc.thePlayer.posZ);
            if (!mc.theWorld.canBlockSeeTheSky(exitX, exitY, exitZ)) {
                explorer.blockedWalkNodes.clear();
                if (explorer.startSavedReturn(mc, explorer.resumeSearchAfterReturn, 128)
                    || explorer.digTowardSavedExit(mc)) return;
                explorer.retryExitLater(mc);
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
            return;
        }
        if (!explorer.searching && !explorer.movingToPuzzleCenter && explorer.insideDungeonRoom(mc)) {
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                explorer.status = "Following room route";
                return;
            }
            if (explorer.reachableBoard(mc)) {
                explorer.signalPuzzleArrival(mc, "Arrived at LootGame; opening puzzle");
            } else explorer.stop(mc, "No safe route to room center");
            return;
        }
        if (explorer.seekingDryEntrance) {
            explorer.seekingDryEntrance = false;
            explorer.digging = true;
            explorer.entranceSelected = true;
            explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        }
        if (!explorer.entranceSelected
            && (explorer.digging || (!explorer.searching && explorer.target.yCoord < mc.thePlayer.boundingBox.minY - 2
                && Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
                    + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord) <= 32))
            && !dryShore(
                mc.theWorld,
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ))) {
            explorer.digging = false;
            if (!explorer.findDryEntrance(mc))
                explorer.stop(mc, "No verified dry entrance near the water; digging stopped");
            return;
        }
        if (!explorer.searching && !explorer.digging) {
            double horizontal = Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
                + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord);
            if (horizontal > 32 || (horizontal > 3 && explorer.target.yCoord >= mc.thePlayer.boundingBox.minY - 2)) {
                if (!explorer.walkingPlanFailed) explorer.plan(mc);
                if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return;
                if (horizontal <= 40 && explorer.beginDiggingAtCurrentPosition(mc)) return;
                if (explorer.startRoomEntrySearch(mc)) return;
                if (explorer.beginExistingAccessSearch(mc)) return;
                if (LootGameSolverConfig.staircaseEnabled && horizontal <= 40 && !mc.thePlayer.isInWater()) {
                    if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
                } else explorer.stop(mc, "No verified route around the obstruction");
                return;
            }
            if (LootGameSolverConfig.staircaseEnabled
                && Math.abs(explorer.target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1) {
                if (explorer.startRoomEntrySearch(mc)) return;
                if (explorer.beginExistingAccessSearch(mc)) return;
                if (!explorer.selectDigEntrance(mc)) explorer.stop(mc, "No safe surface dig entrance in range");
                return;
            }
        }
        if (explorer.searching) {
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
                    && mc.theWorld
                        .blockExists(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord)) {
                    if (explorer.startRoomEntrySearch(mc)) return;
                    if (!explorer.selectDigEntrance(mc) && !explorer.nextSearchLeg(mc)) {
                        explorer.stop(mc, "No safe dig entrance near the structure");
                    }
                    return;
                }
            }
            if (!explorer.nextSearchLeg(mc)) {
                explorer.rotateSearch(mc);
                explorer.nextSearchLeg(mc);
            }
            return;
        }
        if (explorer.digging && (Math.abs(explorer.target.yCoord + 1 - mc.thePlayer.boundingBox.minY) > 1
            || Math.abs(mc.thePlayer.posX - explorer.target.xCoord)
                + Math.abs(mc.thePlayer.posZ - explorer.target.zCoord) > 3)) {
            if (!explorer.stairStep(mc)) {
                if (explorer.stairSteps == 0 && ++explorer.approachAttempts <= 3) {
                    explorer.excavationSearch = null;
                    explorer.excavationRoute.clear();
                    explorer.miningAim.reset();
                    explorer.digging = false;
                    explorer.entranceSelected = false;
                    if (explorer.findDryEntrance(mc)) {
                        explorer.status = "Checking a different supported entrance";
                        return;
                    }
                }
                explorer.stop(mc, "Access route blocked; keeping the existing entrance for reuse");
            }
            return;
        }
        if (explorer.digging) {
            explorer.digging = false;
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) return;
        }
        if (!explorer.insideDungeonRoom(mc)) {
            if (explorer.startRoomEntrySearch(mc)) return;
            if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) return;
            explorer.stop(mc, "No safe route into the puzzle room");
            return;
        }
        explorer.signalPuzzleArrival(mc, "Arrived beside LootGame");
    }
}
