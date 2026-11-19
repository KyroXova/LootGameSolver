package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.explorer.ExplorerGeometry.supportKey;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerSearchCoordinator {

    public void rotateSearch(Minecraft mc, Explorer explorer) {
        explorer.searchYaw = (explorer.searchYaw + 45.0F) % 360.0F;
        int px = mc != null && mc.thePlayer != null ? (int) Math.floor(mc.thePlayer.posX) : 0;
        int pz = mc != null && mc.thePlayer != null ? (int) Math.floor(mc.thePlayer.posZ) : 0;
        explorer.search = new ProgressiveSearch(px, pz, explorer.searchYaw, LootGameSolverConfig.explorationSearchLimit, 128);
    }

    public boolean nextSearchGoal(Minecraft mc, Explorer explorer) {
        if (explorer.search == null) rotateSearch(mc, explorer);
        int[] next = explorer.search.next();
        if (next == null) {
            rotateSearch(mc, explorer);
            next = explorer.search.next();
        }
        if (next != null) {
            BaritoneNavigationBridge.Start started = explorer.baritoneNavigation.startHorizontal(next[0], next[1]);
            if (started == BaritoneNavigationBridge.Start.STARTED) {
                explorer.baritoneCoordinator.searchX = next[0];
                explorer.baritoneCoordinator.searchZ = next[1];
                explorer.baritoneCoordinator.searchTicks = 0;
                explorer.baritoneCoordinator.searchHandoff = false;
                explorer.baritoneCoordinator.searchMove = true;
                explorer.searching = true;
                explorer.walking = true;
                explorer.status = "Exploring for structures";
                return true;
            }
        }
        return nextSearchLeg(mc, explorer);
    }

    public void scan(Minecraft mc, Explorer explorer) {
        explorer.target = null;
        explorer.knownStructure = null;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        explorer.target = ExplorerStructureDiscovery
            .find(mc, explorer.savedFinder, explorer.solvedStructures, explorer.blockedStructures, Explorer.worldIdentity(mc));
        if (explorer.target != null) {
            explorer.knownStructure = new int[] { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord };
            explorer.waypointLabelY = ExplorerStructureDiscovery.labelHeight(mc, explorer.target);
        }
    }

    public boolean nextSearchLeg(Minecraft mc, Explorer explorer) {
        for (int attempt = 0; attempt < 16; attempt++) {
            if (explorer.search == null) rotateSearch(mc, explorer);
            int[] goal = explorer.search.next();
            if (goal == null) {
                rotateSearch(mc, explorer);
                goal = explorer.search.next();
            }
            if (goal == null) continue;
            int x = (int) Math.floor(mc.thePlayer.posX), z = (int) Math.floor(mc.thePlayer.posZ);
            int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            double dx = goal[0] - x, dz = goal[1] - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 4) continue;
            explorer.searchGoalX = goal[0];
            explorer.searchGoalZ = goal[1];
            double step = Math.min(64D, length);
            explorer.target = new TileEntity();
            explorer.target.xCoord = x + (int) Math.round(dx / length * step);
            explorer.target.yCoord = y;
            explorer.target.zCoord = z + (int) Math.round(dz / length * step);
            if (!mc.theWorld.blockExists(explorer.target.xCoord, y, explorer.target.zCoord)) continue;
            explorer.plan(mc);
            explorer.searching = true;
            explorer.digging = false;
            explorer.walking = true;
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            explorer.status = explorer.route.isEmpty() && explorer.walkingSearch == null ? "Checking safe direction"
                : "Exploring outward";
            return true;
        }
        explorer.searching = true;
        explorer.walking = true;
        explorer.status = "Waiting for loaded terrain";
        return true;
    }

    public void continueProgressiveSearch(Minecraft mc, Explorer explorer, boolean solved) {
        explorer.snowRecoveryTarget = null;
        explorer.removePersistedTrail();
        explorer.route.clear();
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.extendedFrom = null;
        explorer.excavationSearch = null;
        explorer.excavationRoute.clear();
        explorer.approachPlan = null;
        explorer.approachAnchor = null;
        explorer.approachReady = false;
        explorer.clearChestPitSearch();
        explorer.roomExitCandidates = null;
        explorer.activeRoomExit = null;
        explorer.roomExitPlanning = false;
        explorer.roomExitNeedsResume = false;
        explorer.roomExitCrossing = false;
        explorer.exitRecoveryTicks = 0;
        explorer.openExitSearch = null;
        explorer.followingOpenExit = false;
        explorer.blockedWalkNodes.clear();
        explorer.digging = false;
        explorer.returnDigging = false;
        explorer.trail.excavating = false;
        explorer.entranceSelected = false;
        explorer.seekingDryEntrance = false;
        explorer.returnAvailable = false;
        explorer.stalled = 0;
        explorer.stallRecoveries = 0;
        explorer.lastDistance = Double.MAX_VALUE;
        explorer.target = null;
        explorer.knownStructure = null;
        explorer.trail.points.clear();
        explorer.trail.target = null;
        explorer.returning = false;
        explorer.resumeSearchAfterReturn = false;
        explorer.returnJoinSearch = null;
        explorer.returnTrailRoute = null;
        explorer.roomEntryCandidates = null;
        explorer.activeRoomEntry = null;
        explorer.roomEntryPlanning = false;
        explorer.roomEntryCrossing = false;
        explorer.roomEntrySearchNeedsResume = false;
        explorer.walkingSearchNeedsResume = false;
        explorer.rejoiningSavedTrail = false;
        explorer.search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            mc.thePlayer.rotationYaw,
            LootGameSolverConfig.explorationSearchLimit,
            128);
        int[] first = explorer.search.next();
        if (first != null) {
            BaritoneNavigationBridge.Start started = explorer.baritoneNavigation.startHorizontal(first[0], first[1]);
            if (started == BaritoneNavigationBridge.Start.STARTED) {
                explorer.baritoneCoordinator.searchX = first[0];
                explorer.baritoneCoordinator.searchZ = first[1];
                explorer.baritoneCoordinator.searchTicks = 0;
                explorer.baritoneCoordinator.searchHandoff = false;
                explorer.baritoneCoordinator.searchMove = true;
                explorer.searching = true;
                explorer.walking = true;
                explorer.status = solved ? "Puzzle solved; searching for the next structure" : "Searching for structures";
                return;
            }
            if (started != BaritoneNavigationBridge.Start.ABSENT) {
                explorer.walking = false;
                explorer.status = "Baritone navigation unavailable or busy";
                return;
            }
        }
        explorer.walking = nextSearchLeg(mc, explorer);
        explorer.status = solved ? "Puzzle solved; searching for the next structure" : "Searching for structures";
    }

    public void retryExitLater(Minecraft mc, Explorer explorer) {
        if (explorer.returnDigging && explorer.returnOriginalTarget != null) {
            explorer.target = explorer.returnOriginalTarget;
        }
        explorer.returnOriginalTarget = null;
        explorer.returnDigging = false;
        explorer.digging = false;
        explorer.returning = true;
        explorer.walking = true;
        explorer.searching = false;
        explorer.route.clear();
        explorer.returnJoinSearch = null;
        explorer.returnTrailRoute = null;
        explorer.roomExitCandidates = null;
        explorer.roomExitPlanning = false;
        explorer.roomExitCrossing = false;
        explorer.excavationSearch = null;
        explorer.excavationRoute.clear();
        explorer.exitRecoveryTicks = 40;
        explorer.openExitSearch = null;
        explorer.followingOpenExit = false;
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        if (LootGameSolverConfig.staircaseEnabled && explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer)) {
            explorer.returning = false;
            return;
        }
        explorer.status = "Exit blocked; retrying saved tunnel and safe alternatives";
    }

    public void retryCurrentRoute(Minecraft mc, Explorer explorer, String reason) {
        explorer.routeRetryTicks = 0;
        explorer.route.clear();
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.extendedFrom = null;
        if (explorer.returning) {
            if (explorer.startChestPitReturn(mc, true)) return;
            if (explorer.startSavedReturn(mc, true, 128)) return;
            if (explorer.startRoomExitSearch(mc)) return;
            retryExitLater(mc, explorer);
            return;
        }
        if (explorer.movingToPuzzleCenter) {
            if (++explorer.approachAttempts <= 3) {
                explorer.reachPuzzleCenter(mc);
            } else {
                explorer.approachAttempts = 0;
                int[] room = explorer.knownStructure == null ? null : explorer.knownStructure.clone();
                explorer.stop(mc, "Puzzle approach failed; room work held");
                explorer.knownStructure = room;
            }
            return;
        }
        if (explorer.digging) {
            explorer.excavationRoute.clear();
            explorer.excavationSearch = null;
            explorer.miningAim.reset();
            if (LootGameSolverConfig.staircaseEnabled && explorer.stairStep(mc)) {
                explorer.status = "Replanning tunnel from current position";
                return;
            }
            explorer.digging = false;
            explorer.entranceSelected = false;
        }
        if (explorer.searching && nextSearchLeg(mc, explorer)) {
            explorer.status = "Trying another exploration direction";
            return;
        }
        if (explorer.target != null && !explorer.target.isInvalid()) {
            boolean nearEntrance = Math.hypot(
                mc.thePlayer.posX - explorer.target.xCoord,
                mc.thePlayer.posZ - explorer.target.zCoord) <= 6D;
            if (nearEntrance && !explorer.insideDungeonRoom(mc)) {
                if (explorer.startRoomEntrySearch(mc)) {
                    explorer.status = "Finding a clear structure entrance";
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled && explorer.beginDiggingAtCurrentPosition(mc)) return;
            }
            if (explorer.beginExistingAccessSearch(mc)) {
                explorer.status = "Checking a different existing path";
                return;
            }
            explorer.plan(mc);
            if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                explorer.status = "Following a newly checked route";
                return;
            }
            if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) {
                explorer.status = "Checking another safe approach";
                return;
            }
        }
        if (explorer.target == null || explorer.target.isInvalid()) {
            scan(mc, explorer);
            if (explorer.target != null) {
                explorer.beginAccessTrail(mc);
                if (explorer.reuseSavedAccessPath(mc) || explorer.beginExistingAccessSearch(mc)) {
                    explorer.status = "Resuming access to the nearest structure";
                    return;
                }
                explorer.plan(mc);
                if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                    explorer.status = "Following route to the nearest structure";
                    return;
                }
            }
        }
        if (nextSearchLeg(mc, explorer)) {
            explorer.status = "Trying another exploration direction";
            return;
        }
        explorer.routeRetryStatus = reason;
        explorer.routeRetryTicks = 8;
        explorer.walking = true;
        explorer.status = "Waiting for a safe route, then retrying";
    }

    public void queueRouteRetry(Minecraft mc, Explorer explorer, String message) {
        if (mc.thePlayer == null) return;
        if (!explorer.route.isEmpty()) {
            int[] failed = explorer.route.get(0);
            markBlockedWalkNode(explorer, failed[0], failed[1], failed[2]);
        }
        if (explorer.navigationInput != null) {
            explorer.navigationInput.forward = 0;
            explorer.navigationInput.sideways = 0;
            explorer.navigationInput.hop = false;
        }
        mc.thePlayer.setSprinting(false);
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        explorer.miningAim.reset();
        explorer.stalled = 0;
        explorer.lastDistance = Double.MAX_VALUE;
        boolean nearEntrance = !explorer.returning && explorer.target != null
            && !explorer.target.isInvalid()
            && Math.hypot(mc.thePlayer.posX - explorer.target.xCoord, mc.thePlayer.posZ - explorer.target.zCoord) <= 6D;
        if (nearEntrance && !explorer.route.isEmpty()) {
            int[] failed = explorer.route.get(0);
            long failedKey = supportKey(failed[0], failed[1], failed[2]);
            explorer.blockedWalkNodes.remove(failedKey);
        }
        explorer.routeRetryTicks = Math.max(explorer.routeRetryTicks, nearEntrance ? 2 : 6);
        explorer.routeRetryStatus = message;
        explorer.walking = true;
        explorer.status = "Checking another safe route";
    }

    public boolean recoverableRouteFailure(String message) {
        String text = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        return text.contains("no safe") || text.contains("no verified")
            || text.contains("blocked")
            || text.contains("obstructed")
            || text.contains("stalled")
            || text.contains("route")
            || text.contains("search limit")
            || text.contains("landing did not")
            || text.contains("no walkable")
            || text.contains("entrance did not cross")
            || text.contains("target no longer loaded")
            || text.contains("could not place a safe return step")
            || text.contains("could not reach")
            || text.contains("center")
            || text.contains("no safe surface")
            || text.contains("replanning");
    }

    public void markBlockedWalkNode(Explorer explorer, int x, int y, int z) {
        long key = supportKey(x, y, z);
        explorer.blockedWalkNodes.remove(key);
        explorer.blockedWalkNodes.add(key);
        while (explorer.blockedWalkNodes.size() > 128) {
            java.util.Iterator<Long> oldest = explorer.blockedWalkNodes.iterator();
            if (!oldest.hasNext()) break;
            oldest.next();
            oldest.remove();
        }
    }
}
