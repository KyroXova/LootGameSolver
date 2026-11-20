package com.kyroxova.lootgamesolver.explorer.trail;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.samePosition;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.structure.ExplorerStructureDiscovery;

public class ExplorerTrailCoordinator {

    private World waypointScanWorld;
    private long waypointScanTick = Long.MIN_VALUE;
    private int[] waypointScanResult;

    public void beginAccessTrail(Minecraft mc, Explorer explorer) {
        if (explorer.knownStructure == null || mc.thePlayer == null) return;
        if (explorer.entrySurfacePoint == null && mc.theWorld != null) {
            int sy = mc.theWorld.getHeightValue(explorer.knownStructure[0], explorer.knownStructure[2]);
            explorer.entrySurfacePoint = new int[] { explorer.knownStructure[0], sy + 1, explorer.knownStructure[2] };
        }
        if (explorer.trail.target == null || !samePosition(explorer.trail.target, explorer.knownStructure)) {
            persistAccessTrail(explorer, true);
            explorer.existingAccessChecked = false;
            explorer.blockedWalkNodes.clear();
            explorer.stallRecoveries = 0;
            explorer.trail.points.clear();
            explorer.trail.target = explorer.knownStructure.clone();
            explorer.trail.worldId = Explorer.worldIdentity(mc);
            explorer.trail.dimension = mc.theWorld.provider.dimensionId;
            explorer.trail.dirty = false;
            explorer.trail.excavating = false;
        }
    }

    public void recordAccessPoint(Explorer explorer, int x, int y, int z) {
        explorer.trail.points.clear();
        explorer.trail.dirty = false;
    }

    public void persistAccessTrail(Explorer explorer, boolean force) {}

    public void removePersistedTrail(Explorer explorer) {
        explorer.trail.points.clear();
        explorer.trail.dirty = false;
    }

    public void clearLastCheckpoint(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        boolean onSurfaceOutsideRoom = !explorer.insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(x, y, z);
        if (onSurfaceOutsideRoom) {
            explorer.continueProgressiveSearch(mc, true);
            return;
        }
        boolean removed = explorer.trail.points.removeLast();
        if (removed) {
            explorer.trail.dirty = true;
            if (explorer.trail.points.size() >= 2) persistAccessTrail(explorer, true);
            else removePersistedTrail(explorer);
        }
        if (explorer.returning || explorer.returnAvailable) {
            boolean resumeSearch = explorer.resumeSearchAfterReturn;
            explorer.route.clear();
            explorer.clearChestPitSearch();
            explorer.returnJoinSearch = null;
            explorer.returnTrailRoute = null;
            if (!explorer.startChestPitReturn(mc, resumeSearch) && !explorer.startSavedReturn(mc, resumeSearch, 128)) {
                explorer.retryExitLater(mc);
            }
        } else if (explorer.walking && explorer.target != null) {
            explorer.route.clear();
            explorer.walkingSearch = null;
            explorer.continuationSearch = null;
            explorer.extendedFrom = null;
            explorer.excavationSearch = null;
            explorer.excavationRoute.clear();
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            if (!explorer.digging) explorer.plan(mc);
            explorer.status = "Checkpoint cleared; replanning";
        } else explorer.status = removed ? "Last saved checkpoint cleared" : "No checkpoint to clear";
    }

    public int checkpointDistance(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null) return 0;
        double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        if (explorer.baritoneCoordinator != null && explorer.baritoneCoordinator.active()) {
            if (explorer.baritoneCoordinator.searchMove) {
                return (int) Math.round(
                    Math.hypot(
                        px - (explorer.baritoneCoordinator.searchX + 0.5D),
                        pz - (explorer.baritoneCoordinator.searchZ + 0.5D)));
            }
            if (explorer.target != null) {
                return (int) Math
                    .round(Math.hypot(px - (explorer.target.xCoord + 0.5D), pz - (explorer.target.zCoord + 0.5D)));
            }
            if (explorer.baritoneCoordinator.returnPoint != null) {
                return (int) Math.round(
                    Math.hypot(
                        px - (explorer.baritoneCoordinator.returnPoint[0] + 0.5D),
                        pz - (explorer.baritoneCoordinator.returnPoint[2] + 0.5D)));
            }
        }
        if (explorer.target != null) {
            return (int) Math
                .round(Math.hypot(px - (explorer.target.xCoord + 0.5D), pz - (explorer.target.zCoord + 0.5D)));
        }
        if (explorer.knownStructure != null) {
            return (int) Math
                .round(Math.hypot(px - (explorer.knownStructure[0] + 0.5D), pz - (explorer.knownStructure[2] + 0.5D)));
        }
        if (!explorer.route.isEmpty()) {
            int[] last = explorer.route.get(explorer.route.size() - 1);
            return (int) Math.round(Math.hypot(px - (last[0] + 0.5D), pz - (last[2] + 0.5D)));
        }
        return 0;
    }

    public int[] structureWaypoint(Minecraft mc, Explorer explorer) {
        if (explorer.roomWorkActive) return null;
        if (explorer.searching || explorer.baritoneCoordinator.searchMove) return null;
        if (explorer.knownStructure != null)
            return ExplorerStructureDiscovery.displayedPosition(mc, explorer.knownStructure);
        if (explorer.target != null) return null;
        if (mc != null && mc.theWorld != null && mc.thePlayer != null) {
            long now = mc.theWorld.getTotalWorldTime();
            if (waypointScanWorld == mc.theWorld && now >= waypointScanTick && now - waypointScanTick < 20)
                return waypointScanResult == null ? null : waypointScanResult.clone();
            waypointScanWorld = mc.theWorld;
            waypointScanTick = now;
            TileEntity found = ExplorerStructureDiscovery.find(
                mc,
                explorer.savedFinder,
                explorer.solvedStructures,
                explorer.blockedStructures,
                Explorer.worldIdentity(mc));
            if (found != null) {
                int[] foundPosition = { found.xCoord, found.yCoord, found.zCoord };
                explorer.waypointLabelY = ExplorerStructureDiscovery.labelHeight(mc, found);
                waypointScanResult = ExplorerStructureDiscovery.displayedPosition(mc, foundPosition);
                return waypointScanResult == null ? null : waypointScanResult.clone();
            }
            waypointScanResult = null;
        }
        return null;
    }

    public void prepareRewardCollection(Minecraft mc, Explorer explorer, int[] center) {
        if (explorer.walking || explorer.baritoneCoordinator.active()) explorer.stop(mc, "Reward collection started");
        if (mc == null || mc.theWorld == null || center == null) return;
        explorer.target = null;
        explorer.puzzleMasterTarget = null;
        explorer.movingToPuzzleCenter = false;
        explorer.movingToRewardView = false;
        explorer.knownStructure = center.clone();
        explorer.trail.target = null;
        beginAccessTrail(mc, explorer);
    }
}
