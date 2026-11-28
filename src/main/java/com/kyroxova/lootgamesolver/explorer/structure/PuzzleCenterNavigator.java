package com.kyroxova.lootgamesolver.explorer.structure;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.samePosition;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;

public final class PuzzleCenterNavigator {

    public boolean puzzleCenterArrivalPending;

    public void reset() {
        puzzleCenterArrivalPending = false;
    }

    public boolean consumeArrival() {
        if (!puzzleCenterArrivalPending) return false;
        puzzleCenterArrivalPending = false;
        return true;
    }

    public boolean resumePuzzleInCurrentRoom(Minecraft mc, Explorer explorer, int[] center) {
        if (explorer.walking || !isInSameRoom(mc, explorer, center)) return false;
        explorer.knownStructure = center.clone();
        TileEntity current = new TileEntity();
        current.xCoord = explorer.knownStructure[0];
        current.yCoord = explorer.knownStructure[1];
        current.zCoord = explorer.knownStructure[2];
        explorer.target = current;
        explorer.beginAccessTrail(mc);
        explorer.returnAvailable = false;
        explorer.puzzleArrivalPending = true;
        explorer.status = "Resuming unfinished puzzle in current room";
        return true;
    }

    public boolean isInSameRoom(Minecraft mc, Explorer explorer, int[] center) {
        if (mc == null || center == null || center.length < 3 || mc.thePlayer == null || mc.theWorld == null)
            return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds != null) {
            if (x <= bounds[0] || x >= bounds[1]
                || z <= bounds[2]
                || z >= bounds[3]
                || center[0] <= bounds[0]
                || center[0] >= bounds[1]
                || center[2] <= bounds[2]
                || center[2] >= bounds[3]
                || Math.abs(y - center[1] - 1) > 3) {
                return false;
            }
        } else {
            if (Math.hypot(x - center[0], z - center[2]) > 16.0D || Math.abs(y - center[1] - 1) > 5) {
                return false;
            }
        }
        return true;
    }

    public boolean moveToPuzzleCenter(Minecraft mc, Explorer explorer, int[] center) {
        return moveToPosition(mc, explorer, center, true);
    }

    public boolean moveToRewardView(Minecraft mc, Explorer explorer, int[] center) {
        return moveToPosition(mc, explorer, center, false);
    }

    public boolean moveToPosition(Minecraft mc, Explorer explorer, int[] center, boolean allowDigging) {
        if (mc.thePlayer == null || center == null || center.length < 3 || explorer.movingToPuzzleCenter) return false;
        double initDx = mc.thePlayer.posX - (center[0] + 0.5D);
        double initDz = mc.thePlayer.posZ - (center[2] + 0.5D);
        double initDy = (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()) - (center[1] + 0.5D);
        boolean arrived = allowDigging
            ? initDx * initDx + initDy * initDy + initDz * initDz <= 14.0D && Math.hypot(initDx, initDz) <= 2.8D
            : rewardViewArrived(initDx, initDz, mc.thePlayer.boundingBox.minY, center[1]);
        if (arrived) {
            markPuzzleCenterArrived(explorer);
            return true;
        }
        if (!explorer.walking && explorer.navigationInput == null) {
            Boolean baritoneResult = tryStartBaritoneCenterMove(explorer, center, allowDigging);
            if (baritoneResult != null) return baritoneResult.booleanValue();
        }
        startGroundCenterSearch(mc, explorer, center, allowDigging);
        if (explorer.walkingSearch == null && explorer.route.isEmpty())
            return handleEmptyCenterRoute(mc, explorer, center, allowDigging);
        explorer.walking = explorer.walkingSearch != null || !explorer.route.isEmpty();
        if (!explorer.walking) return handleEmptyCenterRoute(mc, explorer, center, allowDigging);
        explorer.status = "Moving to board center";
        return true;
    }

    private void markPuzzleCenterArrived(Explorer explorer) {
        explorer.movingToPuzzleCenter = false;
        explorer.target = explorer.puzzleMasterTarget;
        explorer.puzzleMasterTarget = null;
        puzzleCenterArrivalPending = true;
    }

    private Boolean tryStartBaritoneCenterMove(Explorer explorer, int[] center, boolean allowDigging) {
        BaritoneNavigationBridge.Start started = allowDigging
            ? explorer.baritoneNavigation.startAdjacentWithoutBreaking(center[0], center[1] + 1, center[2])
            : explorer.baritoneNavigation.startPuzzleTileApproach(center[0], center[1] + 1, center[2]);
        if (started == BaritoneNavigationBridge.Start.STARTED) {
            explorer.puzzleMasterTarget = explorer.target;
            TileEntity point = new TileEntity();
            point.xCoord = center[0];
            point.yCoord = center[1];
            point.zCoord = center[2];
            explorer.target = point;
            explorer.baritoneCoordinator.centerMove = true;
            explorer.movingToPuzzleCenter = true;
            explorer.movingToRewardView = !allowDigging;
            puzzleCenterArrivalPending = false;
            explorer.walking = true;
            explorer.status = "Moving to board center";
            return Boolean.TRUE;
        }
        if (started != BaritoneNavigationBridge.Start.ABSENT) {
            explorer.status = "Baritone navigation unavailable or busy";
            return Boolean.FALSE;
        }
        return null;
    }

    private void startGroundCenterSearch(Minecraft mc, Explorer explorer, int[] center, boolean allowDigging) {
        explorer.puzzleMasterTarget = explorer.target;
        TileEntity point = new TileEntity();
        point.xCoord = center[0];
        point.yCoord = center[1];
        point.zCoord = center[2];
        explorer.target = point;
        explorer.movingToPuzzleCenter = true;
        explorer.movingToRewardView = !allowDigging;
        puzzleCenterArrivalPending = false;
        explorer.cameraPitchDirection = 0;
        explorer.route.clear();
        explorer.entranceCandidates.clear();
        explorer.entranceCandidate = 0;
        explorer.digging = false;
        explorer.searching = false;
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.segmentX = mc.thePlayer.posX;
        explorer.segmentZ = mc.thePlayer.posZ;
        explorer.walkingSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            center[0],
            center[1] + 1,
            center[2],
            LootGameSolverConfig.explorationRange);
        if (explorer.walkingSearch.advance(3_000_000L)) {
            boolean reachedCenter = explorer.walkingSearch.reachedGoal();
            explorer.route.addAll(explorer.walkingSearch.path());
            explorer.walkingSearch = null;
            if (reachedCenter) appendPuzzleCenterWaypoint(explorer, center);
        }
    }

    private boolean handleEmptyCenterRoute(Minecraft mc, Explorer explorer, int[] center, boolean allowDigging) {
        double distance = Math.hypot(mc.thePlayer.posX - (center[0] + 0.5D), mc.thePlayer.posZ - (center[2] + 0.5D));
        if (distance <= 2.5D && Math.abs(Math.floor(mc.thePlayer.boundingBox.minY) - center[1] - 1) <= 3) {
            markPuzzleCenterArrived(explorer);
            return true;
        }
        if (allowDigging && !explorer.inDungeonRoom(mc)
            && LootGameSolverConfig.staircaseEnabled
            && explorer.selectDigEntrance(mc)) {
            explorer.walking = true;
            explorer.status = explorer.digging ? "Planning an entry to the board center"
                : "Moving to a safe board entrance";
            return true;
        }
        if (!allowDigging) {
            explorer.target = explorer.puzzleMasterTarget;
            explorer.puzzleMasterTarget = null;
            explorer.movingToPuzzleCenter = false;
        } else abandonPuzzleCenterMove(mc, explorer);
        return false;
    }

    public static boolean rewardViewArrived(double dx, double dz, double feetY, int floorY) {
        return Math.hypot(dx, dz) <= 0.75D && Math.floor(feetY) == floorY + 1;
    }

    public void appendPuzzleCenterWaypoint(Explorer explorer, int[] center) {
        int[] stand = new int[] { center[0], center[1] + 1, center[2], explorer.movingToRewardView ? 0 : 2 };
        if (explorer.route.isEmpty() || !samePosition(explorer.route.get(explorer.route.size() - 1), stand))
            explorer.route.add(stand);
        else explorer.route.set(explorer.route.size() - 1, stand);
    }

    public boolean reachPuzzleCenter(Minecraft mc, Explorer explorer) {
        int[] center = explorer.target == null ? null
            : new int[] { explorer.target.xCoord, explorer.target.yCoord, explorer.target.zCoord };
        if (center == null) {
            abandonPuzzleCenterMove(mc, explorer);
            return true;
        }
        double dx = mc.thePlayer.posX - (center[0] + 0.5D);
        double dz = mc.thePlayer.posZ - (center[2] + 0.5D);
        double dy = (mc.thePlayer.posY + mc.thePlayer.getEyeHeight()) - (center[1] + 0.5D);
        double distSq = dx * dx + dy * dy + dz * dz;
        double hDist = Math.hypot(dx, dz);
        double reach = mc.playerController != null ? mc.playerController.getBlockReachDistance() : 4.0D;
        boolean withinInteraction = (distSq <= reach * reach || hDist <= 2.5D);
        if (withinInteraction && mc.thePlayer.onGround) {
            explorer.target = explorer.puzzleMasterTarget;
            explorer.puzzleMasterTarget = null;
            explorer.movingToPuzzleCenter = false;
            explorer.stop(mc, "At LootGames board center");
            puzzleCenterArrivalPending = true;
            return true;
        }
        explorer.walkingSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            center[0],
            center[2],
            LootGameSolverConfig.explorationRange);
        if (explorer.walkingSearch.advance(3_000_000L)) {
            boolean reachedCenter = explorer.walkingSearch.reachedGoal();
            explorer.route.clear();
            explorer.route.addAll(explorer.walkingSearch.path());
            explorer.walkingSearch = null;
            if (reachedCenter) appendPuzzleCenterWaypoint(explorer, center);
            if (!explorer.route.isEmpty()) {
                explorer.walking = true;
                explorer.segmentX = mc.thePlayer.posX;
                explorer.segmentZ = mc.thePlayer.posZ;
                explorer.status = "Moving to board center";
                return true;
            }
        } else {
            explorer.route.clear();
            explorer.walking = true;
            explorer.status = "Checking route to board center";
            return true;
        }
        if (explorer.movingToRewardView) {
            explorer.stop(mc, "Chest view unreachable");
            return true;
        }
        if (explorer.seekingDryEntrance) {
            explorer.seekingDryEntrance = false;
            explorer.digging = true;
            explorer.entranceSelected = true;
            explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        }
        if (explorer.digging) {
            if (explorer.stairStep(mc)) return true;
            explorer.digging = false;
            explorer.entranceSelected = false;
            explorer.excavationRoute.clear();
            explorer.excavationSearch = null;
        }
        if (LootGameSolverConfig.staircaseEnabled && !explorer.inDungeonRoom(mc) && explorer.selectDigEntrance(mc)) {
            explorer.walking = true;
            explorer.status = explorer.digging ? "Planning an entry to the board center"
                : "Moving to a safe board entrance";
            return true;
        }
        abandonPuzzleCenterMove(mc, explorer);
        return true;
    }

    public void abandonPuzzleCenterMove(Minecraft mc, Explorer explorer) {
        if (explorer.puzzleMasterTarget != null) explorer.target = explorer.puzzleMasterTarget;
        explorer.puzzleMasterTarget = null;
        explorer.movingToPuzzleCenter = false;
        explorer.stop(mc, "Board center has no verified safe route; explorer held");
    }
}
