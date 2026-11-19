package com.kyroxova.lootgamesolver.explorer;

import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.dryShore;
import static com.kyroxova.lootgamesolver.explorer.ExplorerTerrain.safe;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerTransitCoordinator {

    private boolean undergroundNearby(Minecraft mc, Explorer explorer) {
        return !explorer.returning && !explorer.searching
            && LootGameSolverConfig.staircaseEnabled
            && explorer.target != null
            && ExcavationPolicy.shouldApproach(
                mc.thePlayer.posX,
                mc.thePlayer.boundingBox.minY,
                mc.thePlayer.posZ,
                explorer.target.xCoord,
                explorer.target.yCoord,
                explorer.target.zCoord);
    }

    public void chooseApproachDuringWalking(Minecraft mc, Explorer explorer) {
        if (explorer.digging || explorer.seekingDryEntrance
            || explorer.movingToPuzzleCenter
            || explorer.roomEntryPlanning
            || explorer.roomEntrySearchNeedsResume
            || explorer.roomEntryCrossing
            || explorer.route.isEmpty()
            || !undergroundNearby(mc, explorer)) return;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (mc.thePlayer.onGround && ExcavationPolicy.closeEntrance(x, z, explorer.target.xCoord, explorer.target.zCoord)
            && dryShore(mc.theWorld, x, y, z)
            && safe(mc.theWorld, x, y, z)) {
            explorer.entranceSurfaceY = y;
            explorer.entrySurfacePoint = new int[] { x, y, z };
            explorer.staircaseDugPath.clear();
            explorer.staircaseDugPath.add(new int[] { x, y, z });
            explorer.route.clear();
            explorer.extendedFrom = null;
            explorer.approachPlan = null;
            explorer.walkingSearch = null;
            explorer.continuationSearch = null;
            explorer.excavationSearch = null;
            explorer.excavationRoute.clear();
            explorer.digging = true;
            explorer.entranceSelected = true;
            explorer.savedSlot = mc.thePlayer.inventory.currentItem;
            explorer.status = "Planning underground access from current dry ground";
        } else if (explorer.ticks % 10 == 0) {
            explorer.findDryEntrance(mc);
        }
    }

    public void inspectUpcomingRoute(Minecraft mc, Explorer explorer) {
        if (explorer.digging || explorer.seekingDryEntrance
            || explorer.roomEntryPlanning
            || explorer.roomEntrySearchNeedsResume
            || explorer.roomEntryCrossing
            || explorer.roomExitCrossing
            || explorer.route.size() < 2
            || explorer.ticks % 10 != 0) return;
        int[] previous = { (int) Math.floor(mc.thePlayer.posX), (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ) };
        for (int i = 0; i < Math.min(12, explorer.route.size()); i++) {
            int[] step = explorer.route.get(i);
            boolean diagonal = step[0] != previous[0] && step[2] != previous[2] && step[1] == previous[1];
            boolean returnStep = explorer.returning && step[1] > previous[1]
                && step[1] <= previous[1] + 2
                && Math.abs(step[0] - previous[0]) + Math.abs(step[2] - previous[2]) == 1
                && mc.theWorld.blockExists(step[0], previous[1], step[2])
                && mc.theWorld.getBlock(step[0], previous[1], step[2])
                    .isReplaceable(mc.theWorld, step[0], previous[1], step[2]);
            if ((!safe(mc.theWorld, step[0], step[1], step[2]) && !returnStep)
                || (diagonal && (!safe(mc.theWorld, step[0], step[1], previous[2])
                    || !safe(mc.theWorld, previous[0], step[1], step[2])))) {
                if (explorer.returning) {
                    if (explorer.rerouteSavedReturn(mc, i + 1) || explorer.digTowardSavedExit(mc)) {
                        explorer.status = "Rerouting saved exit";
                        return;
                    }
                    explorer.retryExitLater(mc);
                    return;
                }
                explorer.plan(mc);
                explorer.status = "Replanning before blocked checkpoint";
                return;
            }
            previous = step;
        }
    }

    public void prepareApproach(Minecraft mc, Explorer explorer) {
        if (explorer.returning || explorer.digging
            || explorer.seekingDryEntrance
            || explorer.roomEntryPlanning
            || explorer.roomEntrySearchNeedsResume
            || explorer.roomEntryCrossing
            || explorer.searching
            || explorer.route.isEmpty()
            || explorer.route.size() > 8
            || !LootGameSolverConfig.staircaseEnabled) return;
        int[] end = explorer.route.get(explorer.route.size() - 1);
        if (explorer.target.yCoord >= end[1] - 2 || Math.abs(end[0] - explorer.target.xCoord) + Math.abs(end[2] - explorer.target.zCoord) > 48)
            return;
        if (explorer.approachAnchor != end) {
            explorer.approachAnchor = end;
            explorer.approachPlan = null;
            explorer.approachReady = false;
            if (!dryShore(mc.theWorld, end[0], end[1], end[2])) return;
            explorer.approachPlan = new ExcavationPathfinder(
                end[0],
                end[1],
                end[2],
                explorer.target.xCoord,
                explorer.target.yCoord + 1,
                explorer.target.zCoord,
                true);
        }
        if (explorer.approachPlan != null && !explorer.approachReady) {
            explorer.approachReady = explorer.approachPlan.advance(
                (x, y, z, nx, ny, nz) -> explorer.excavationTerrain.excavationCost(mc.theWorld, x, y, z, nx, ny, nz),
                12,
                System.nanoTime() + 1_500_000L);
        }
    }

    public void recoverActiveRoute(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (explorer.baritoneCoordinator.surfaceMove || explorer.returning) {
            explorer.baritoneNavigation.stop();
            explorer.baritoneCoordinator.reset();
            explorer.route.clear();
            explorer.returning = false;
            if (!explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer)) explorer.retryExitLater(mc);
        } else if (explorer.baritoneCoordinator.structureMove && explorer.target != null) {
            explorer.baritoneNavigation.stop();
            explorer.baritoneCoordinator.reset();
            routeToStructure(mc, explorer);
        } else {
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
        }
    }

    public void refreshTravelMode(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        if (!LootGameSolverConfig.caveMode && (explorer.baritoneCoordinator.searchMove || explorer.baritoneCoordinator.preRouting)) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (mc.theWorld.getHeightValue(x, z) - y > 6) {
                explorer.baritoneNavigation.stop();
                explorer.baritoneCoordinator.reset();
                if (!explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer, false)) {
                    explorer.stop(mc, "No safe surface destination found");
                }
                return;
            }
        }
        if (explorer.baritoneCoordinator.searchMove) {
            int x = explorer.baritoneCoordinator.searchX, z = explorer.baritoneCoordinator.searchZ;
            explorer.baritoneNavigation.stop();
            explorer.baritoneCoordinator.reset();
            explorer.baritoneCoordinator.searchX = x;
            explorer.baritoneCoordinator.searchZ = z;
            explorer.baritoneCoordinator.searchMove = true;
            explorer.baritoneCoordinator.searchNext = true;
            explorer.baritoneCoordinator.searchHandoff = true;
        } else if (explorer.baritoneCoordinator.structureMove && explorer.baritoneCoordinator.preRouting && explorer.target != null) {
            explorer.baritoneNavigation.stop();
            explorer.baritoneCoordinator.reset();
            routeToStructure(mc, explorer);
        }
    }

    public void routeToStructure(Minecraft mc, Explorer explorer) {
        if (explorer.target == null || mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        explorer.rememberBaritoneReturnPoint(mc);
        double playerY = mc.thePlayer.boundingBox.minY;
        double horizontal = Math
            .hypot(mc.thePlayer.posX - explorer.target.xCoord - 0.5D, mc.thePlayer.posZ - explorer.target.zCoord - 0.5D);
        if (playerY > explorer.target.yCoord + 5 || (!LootGameSolverConfig.caveMode && horizontal > 6D)) {
            BaritoneNavigationBridge.Start start = explorer.baritoneNavigation.startHorizontal(explorer.target.xCoord, explorer.target.zCoord);
            if (start == BaritoneNavigationBridge.Start.STARTED) {
                explorer.baritoneCoordinator.structureMove = true;
                explorer.baritoneCoordinator.preRouting = true;
                explorer.walking = true;
                explorer.searching = false;
                explorer.status = "Pre-routing to Puzzle";
                return;
            }
        }
        BaritoneNavigationBridge.Start baritoneStart = explorer.baritoneNavigation
            .startStructureApproach(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord);
        if (baritoneStart == BaritoneNavigationBridge.Start.STARTED) {
            explorer.baritoneCoordinator.structureMove = true;
            explorer.baritoneCoordinator.preRouting = false;
            explorer.walking = true;
            explorer.searching = false;
            explorer.status = "Walking to LootGame";
            return;
        }
        if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) {
            explorer.walking = true;
            explorer.searching = false;
            return;
        }
        explorer.plan(mc);
        explorer.walking = explorer.walkingSearch != null || !explorer.route.isEmpty();
        if (explorer.walking) {
            explorer.status = "Walking to LootGame";
        }
    }

    public void toggle(Minecraft mc, Explorer explorer) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
        if (explorer.walking) {
            explorer.stop(mc, "Walk stopped");
            return;
        }
        explorer.returnAvailable = false;
        explorer.returning = false;
        explorer.puzzleArrivalPending = false;
        explorer.planningExistingAccess = false;
        explorer.existingAccessPlan = null;
        explorer.existingAccessChecked = false;
        explorer.stallRecoveries = 0;
        explorer.blockedWalkNodes.clear();
        explorer.searchYaw = mc.thePlayer.rotationYaw;
        explorer.search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            explorer.searchYaw,
            LootGameSolverConfig.explorationSearchLimit,
            128);
        explorer.entranceSelected = false;
        explorer.excavationSearch = null;
        explorer.excavationReplans = 0;
        explorer.approachAttempts = 0;
        explorer.extendedFrom = null;
        explorer.approachPlan = null;
        explorer.approachAnchor = null;
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.navigationPreview.clear();
        explorer.baritoneCoordinator.routePreview.clear();
        explorer.route.clear();
        explorer.returnTrailRoute = null;
        explorer.excavationRoute.clear();
        explorer.rejectedSteps.clear();
        explorer.entranceCandidates.clear();
        explorer.scan(mc);
        if (explorer.target == null) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (!LootGameSolverConfig.caveMode && mc.theWorld.getHeightValue(x, z) - y > 6) {
                if (!explorer.baritoneCoordinator.beginSurfaceExit(mc, explorer, false)) {
                    explorer.stop(mc, "No safe surface destination found");
                }
                return;
            }
            int[] next = explorer.search.next();
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
                    return;
                }
                if (started != BaritoneNavigationBridge.Start.ABSENT) {
                    explorer.status = "Baritone navigation unavailable or busy";
                    return;
                }
            }
            explorer.walking = explorer.nextSearchLeg(mc);
            return;
        }
        routeToStructure(mc, explorer);
        if (explorer.walking) return;
        explorer.beginAccessTrail(mc);
        int playerX = (int) Math.floor(mc.thePlayer.posX);
        int playerY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int playerZ = (int) Math.floor(mc.thePlayer.posZ);
        int nearbyTrailIndex = explorer.trail.points.nearestIndex(playerX, playerY, playerZ, 24);
        if (nearbyTrailIndex < 0 && explorer.trail.target != null && explorer.trail.points.size() > 0) {
            explorer.trail.points.clear();
            explorer.trail.target = explorer.knownStructure == null ? null : explorer.knownStructure.clone();
            explorer.trail.excavating = false;
            explorer.trail.dirty = false;
            explorer.removePersistedTrail();
        }
        if (explorer.reuseSavedAccessPath(mc)) {} else if (explorer.beginExistingAccessSearch(mc)) {} else {
            explorer.plan(mc);
            explorer.walking = explorer.walkingSearch != null || !explorer.route.isEmpty();
            if (!explorer.walking && LootGameSolverConfig.staircaseEnabled
                && mc.theWorld.blockExists(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord)) {
                explorer.walking = explorer.selectDigEntrance(mc);
            }
        }
        if (explorer.digging) explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        explorer.stalled = 0;
        explorer.lastDistance = Double.MAX_VALUE;
        if (!explorer.walking) {
            explorer.walking = explorer.nextSearchLeg(mc);
            if (!explorer.walking) {
                explorer.rotateSearch(mc);
                explorer.walking = explorer.nextSearchLeg(mc);
                if (!explorer.walking) explorer.queueRouteRetry(mc, "Planning next search leg");
            }
        } else explorer.status = "Walking to LootGame";
    }

    public void resumeCurrentTarget(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null || explorer.target == null) return;
        if (!explorer.searching && explorer.navigationInput == null) {
            BaritoneNavigationBridge.Start started = explorer.baritoneNavigation
                .startStructureApproach(explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord);
            if (started == BaritoneNavigationBridge.Start.STARTED) {
                explorer.rememberBaritoneReturnPoint(mc);
                explorer.baritoneCoordinator.structureMove = true;
                explorer.walking = true;
                explorer.status = "Walking to LootGame";
                return;
            }
            if (started != BaritoneNavigationBridge.Start.ABSENT) {
                explorer.status = "Baritone navigation unavailable or busy";
                return;
            }
        }
        explorer.routeRetryTicks = 0;
        explorer.routeRetryStatus = "";
        explorer.stalled = 0;
        explorer.stallRecoveries = 0;
        explorer.lastDistance = Double.MAX_VALUE;
        explorer.walking = true;
        if (explorer.searching) {
            explorer.route.clear();
            explorer.walkingSearch = null;
            explorer.continuationSearch = null;
            explorer.plan(mc);
            explorer.status = "Resuming current exploration leg";
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
        explorer.beginAccessTrail(mc);
        explorer.route.clear();
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.extendedFrom = null;
        if (explorer.reuseSavedAccessPath(mc)) {
            explorer.status = "Resuming saved tunnel route";
            return;
        }
        if (explorer.beginExistingAccessSearch(mc)) {
            explorer.status = "Checking the existing route again";
            return;
        }
        explorer.plan(mc);
        if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
            explorer.status = "Resuming route to current structure";
            return;
        }
        if (LootGameSolverConfig.staircaseEnabled && explorer.selectDigEntrance(mc)) {
            explorer.status = explorer.digging ? "Planning another tunnel route" : "Moving to another safe entrance";
            return;
        }
        explorer.routeRetryStatus = "No safe route to current target yet";
        explorer.routeRetryTicks = 1;
        explorer.status = "Retrying route to current target";
    }
}
