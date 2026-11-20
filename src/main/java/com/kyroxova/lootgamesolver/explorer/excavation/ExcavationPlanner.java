package com.kyroxova.lootgamesolver.explorer.excavation;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.entranceScore;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.samePosition;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.dryShore;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.isFoliage;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.passable;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.safe;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.solidCheckpoint;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;
import com.kyroxova.lootgamesolver.explorer.trail.FoliagePolicy;

public final class ExcavationPlanner {

    public int stairX;
    public int stairZ;
    public int stairSteps;
    public boolean seekingDryEntrance;
    public boolean entranceSelected;
    public final List<int[]> entranceCandidates = new ArrayList<int[]>();
    public int entranceCandidate;
    public int entranceSurfaceY = 64;
    public int[] entrySurfacePoint;
    public String excavationMode = "Access route";
    public final List<int[]> excavationRoute = new ArrayList<int[]>();
    public ExcavationPathfinder excavationSearch;
    public int excavationReplans;
    public int[] roofEscapeGoal;
    public int[] snowRecoveryTarget;
    public ExcavationPathfinder approachPlan;
    public int[] approachAnchor;
    public boolean approachReady;
    public int approachAttempts;
    public int settlingTicks;

    public void reset() {
        stairX = 0;
        stairZ = 0;
        stairSteps = 0;
        seekingDryEntrance = false;
        entranceSelected = false;
        entranceCandidates.clear();
        entranceCandidate = 0;
        entranceSurfaceY = 64;
        entrySurfacePoint = null;
        excavationMode = "Access route";
        excavationRoute.clear();
        excavationSearch = null;
        excavationReplans = 0;
        roofEscapeGoal = null;
        snowRecoveryTarget = null;
        approachPlan = null;
        approachAnchor = null;
        approachReady = false;
        approachAttempts = 0;
        settlingTicks = 0;
    }

    public boolean beginDiggingAtCurrentPosition(Minecraft mc, Explorer explorer) {
        if (!LootGameSolverConfig.staircaseEnabled || mc.thePlayer.isInWater() || !mc.thePlayer.onGround) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!safe(mc.theWorld, x, y, z) || !dryShore(mc.theWorld, x, y, z)) return false;
        explorer.entranceSurfaceY = y;
        explorer.entrySurfacePoint = new int[] { x, y, z };
        explorer.staircaseDugPath.clear();
        explorer.staircaseDugPath.add(new int[] { x, y, z });
        explorer.route.clear();
        explorer.entranceCandidates.clear();
        explorer.extendedFrom = null;
        explorer.approachPlan = null;
        explorer.approachAnchor = null;
        explorer.approachReady = false;
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.excavationSearch = null;
        explorer.excavationRoute.clear();
        explorer.digging = true;
        explorer.entranceSelected = true;
        explorer.seekingDryEntrance = false;
        explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        explorer.status = "Planning a safe tunnel from the current checkpoint";
        return true;
    }

    public boolean beginDiggingToSurface(Minecraft mc, Explorer explorer) {
        if (!LootGameSolverConfig.staircaseEnabled || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int surfaceY = mc.theWorld.getHeightValue(x, z);
        if (surfaceY <= y) surfaceY = y + 25;
        net.minecraft.tileentity.TileEntity surfaceTarget = new net.minecraft.tileentity.TileEntity();
        surfaceTarget.xCoord = x;
        surfaceTarget.yCoord = surfaceY;
        surfaceTarget.zCoord = z;
        explorer.target = surfaceTarget;
        explorer.route.clear();
        explorer.walkingSearch = null;
        explorer.continuationSearch = null;
        explorer.excavationSearch = null;
        explorer.excavationRoute.clear();
        explorer.digging = true;
        explorer.entranceSelected = true;
        explorer.seekingDryEntrance = false;
        explorer.savedSlot = mc.thePlayer.inventory.currentItem;
        explorer.status = "Digging upward staircase to surface";
        return true;
    }

    public List<int[]> patternedAccess(Minecraft mc, Explorer explorer, int x, int y, int z) {
        if (explorer.target == null) return new ArrayList<int[]>();
        int goalY = explorer.target.yCoord + 1;
        ExcavationPathfinder.Terrain terrain = (a, b, c, nx, ny, nz) -> explorer.excavationTerrain
            .excavationCost(mc.theWorld, a, b, c, nx, ny, nz);
        if (goalY == y) {
            List<int[]> proposal = ExcavationPatterns
                .straight(x, y, z, explorer.target.xCoord, goalY, explorer.target.zCoord);
            List<int[]> checked = ExcavationPatterns
                .checked(proposal.subList(0, Math.min(16, proposal.size())), terrain, x, y, z);
            if (!checked.isEmpty() && checked.size() >= Math.min(4, proposal.size())) {
                excavationMode = goalY == y ? "Straight tunnel" : goalY > y ? "Upward staircase" : "Downward staircase";
                return checked;
            }
        }
        if (goalY != y) {
            int orientation = Math.abs(explorer.target.xCoord - x) >= Math.abs(explorer.target.zCoord - z)
                ? explorer.target.xCoord >= x ? 0 : 2
                : explorer.target.zCoord >= z ? 1 : 3;
            for (int turn = 0; turn < 4; turn++) {
                List<int[]> proposal = ExcavationPatterns
                    .spiral(x, y, z, goalY, LootGameSolverConfig.spiralWidth, (orientation + turn) % 4);
                int section = 8 * (LootGameSolverConfig.spiralWidth - 1);
                List<int[]> checked = ExcavationPatterns
                    .checked(proposal.subList(0, Math.min(section, proposal.size())), terrain, x, y, z);
                if (checked.size() < proposal.size()) {
                    int lap = 4 * (LootGameSolverConfig.spiralWidth - 1);
                    checked = new ArrayList<int[]>(checked.subList(0, checked.size() / lap * lap));
                }
                if (!checked.isEmpty() && checked.size() >= Math.min(4, proposal.size())) {
                    excavationMode = "Spiral staircase";
                    return checked;
                }
            }
        }
        return new ArrayList<int[]>();
    }

    public boolean stairStep(Minecraft mc, Explorer explorer) {
        if (explorer.fallingBlocks.waiting(mc, () -> explorer.releaseMiningWhileSandFalls(mc))) {
            explorer.status = "Waiting for sand";
            return true;
        }
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround) return ++settlingTicks <= 40;
        settlingTicks = 0;
        if (roofEscapeGoal != null && Math.abs(x - roofEscapeGoal[0]) + Math.abs(z - roofEscapeGoal[2]) <= 2) {
            roofEscapeGoal = null;
            excavationRoute.clear();
            excavationSearch = null;
            explorer.status = "Reached roof edge; planning side access";
        }
        if (roofEscapeGoal == null) explorer.roomExit.planRoofEscape(mc, explorer);
        explorer.excavationTerrain.protectSupport(supportKey(x, y - 1, z));
        if (!explorer.returnDigging) {
            if (explorer.staircaseDugPath.isEmpty() || !samePosition(
                explorer.staircaseDugPath.get(explorer.staircaseDugPath.size() - 1),
                new int[] { x, y, z })) {
                explorer.staircaseDugPath.add(new int[] { x, y, z });
            }
            if (explorer.trail.target == null || explorer.trail.target[0] != explorer.target.xCoord
                || explorer.trail.target[1] != explorer.target.yCoord
                || explorer.trail.target[2] != explorer.target.zCoord) {
                explorer.persistAccessTrail(true);
                explorer.trail.points.clear();
                explorer.trail.target = new int[] { explorer.target.xCoord, explorer.target.yCoord,
                    explorer.target.zCoord };
                explorer.trail.worldId = explorer.worldIdentity(mc);
                explorer.trail.dimension = mc.theWorld.provider.dimensionId;
                explorer.trail.dirty = false;
                explorer.trail.excavating = false;
            }
            if (!explorer.trail.excavating) {
                explorer.trail.points.clear();
                explorer.trail.dirty = false;
                explorer.trail.excavating = true;
            }
            explorer.recordAccessPoint(x, y, z);
        }
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            explorer.singleStep(mc, x, y, z, true);
            return true;
        }
        if (snowRecoveryTarget != null) {
            int[] snow = snowRecoveryTarget;
            if (Math.abs(snow[0] - x) + Math.abs(snow[2] - z) == 1
                && mc.theWorld.getBlock(snow[0], snow[1], snow[2]) == Blocks.snow_layer
                && explorer.minePlannedBlock(mc, snow[0], snow[1], snow[2], snow[1])) return true;
            snowRecoveryTarget = null;
        }
        if (excavationRoute.isEmpty() && excavationSearch == null
            && explorer.trail.points.size() <= 1
            && roofEscapeGoal == null
            && excavationReplans == 0) {
            excavationRoute.addAll(patternedAccess(mc, explorer, x, y, z));
            if (!excavationRoute.isEmpty()) {
                approachPlan = null;
                approachAnchor = null;
            }
        }
        if (approachPlan != null) {
            if (approachAnchor[0] == x && approachAnchor[1] == y && approachAnchor[2] == z) {
                if (approachReady) excavationRoute.addAll(approachPlan.path);
                else excavationSearch = approachPlan;
            }
            approachPlan = null;
            approachAnchor = null;
        }
        if (excavationRoute.isEmpty()) {
            if (excavationSearch == null) {
                excavationSearch = new ExcavationPathfinder(
                    x,
                    y,
                    z,
                    roofEscapeGoal == null ? explorer.target.xCoord : roofEscapeGoal[0],
                    roofEscapeGoal == null ? explorer.target.yCoord + 1 : roofEscapeGoal[1],
                    roofEscapeGoal == null ? explorer.target.zCoord : roofEscapeGoal[2],
                    true);
                explorer.rejectedSteps.clear();
            }
            boolean complete = excavationSearch.advance(
                (a, b, c, nx, ny,
                    nz) -> a == x && b == y && c == z && explorer.blockedWalkNodes.contains(supportKey(nx, ny, nz)) ? -1
                        : explorer.excavationTerrain.excavationCost(mc.theWorld, a, b, c, nx, ny, nz),
                24,
                System.nanoTime() + 3_000_000L);
            explorer.rejectedSteps.clear();
            explorer.rejectedSteps.addAll(excavationSearch.rejected);
            explorer.status = "Planning a safe tunnel";
            if (!complete) return true;
            explorer.rejectedSteps.clear();
            excavationRoute.addAll(excavationSearch.path);
            excavationSearch = null;
        }
        if (!excavationRoute.isEmpty()) {
            int[] next = excavationRoute.get(0);
            stairX = next[0] - x;
            stairZ = next[2] - z;
            if (attemptStairStep(mc, explorer, next[1])) return true;
        }
        return ++excavationReplans <= 12;
    }

    public boolean clearSnowForExcavation(Minecraft mc, Explorer explorer, int x, int y, int z) {
        return false;
    }

    public boolean selectDigEntrance(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null || explorer.target == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (explorer.entrySurfacePoint == null) {
            explorer.entrySurfacePoint = new int[] { x, y, z };
        }
        if (mc.thePlayer.onGround
            && ExcavationPolicy.closeEntrance(x, z, explorer.target.xCoord, explorer.target.zCoord)
            && dryShore(mc.theWorld, x, y, z)
            && safe(mc.theWorld, x, y, z)) {
            explorer.entranceSurfaceY = y;
            explorer.entrySurfacePoint = new int[] { x, y, z };
            explorer.staircaseDugPath.clear();
            explorer.staircaseDugPath.add(new int[] { x, y, z });
            explorer.route.clear();
            explorer.extendedFrom = null;
            explorer.approachPlan = null;
            explorer.approachAnchor = null;
            explorer.walkingSearch = null;
            explorer.continuationSearch = null;
            explorer.excavationSearch = null;
            explorer.excavationRoute.clear();
            explorer.digging = true;
            explorer.seekingDryEntrance = false;
            explorer.entranceSelected = true;
            explorer.savedSlot = mc.thePlayer.inventory.currentItem;
            explorer.status = "Planning access from the nearest safe structure wall";
            return true;
        }
        explorer.digging = false;
        if (findDryEntrance(mc, explorer)) {
            explorer.walking = true;
            return true;
        }
        return false;
    }

    public boolean findDryEntrance(final Minecraft mc, Explorer explorer) {
        final World w = mc.theWorld;
        final GroundPathfinder.Terrain approachTerrain = explorer.walkingTerrain(w);
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (explorer.entranceCandidates.isEmpty()) {
            explorer.entranceCandidate = 0;
            int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 },
                { 2, 1 }, { 2, -1 }, { -2, 1 }, { -2, -1 }, { 1, 2 }, { 1, -2 }, { -1, 2 }, { -1, -2 } };
            for (int radius = (int) ExcavationPolicy.MIN_ENTRANCE_RADIUS; radius
                <= ExcavationPolicy.MAX_ENTRANCE_RADIUS; radius += 2) for (int[] direction : directions) {
                    double norm = Math.hypot(direction[0], direction[1]);
                    int gx = explorer.target.xCoord + (int) Math.round(direction[0] * radius / norm);
                    int gz = explorer.target.zCoord + (int) Math.round(direction[1] * radius / norm);
                    if (!ExcavationPolicy.preferredEntrance(gx, gz, explorer.target.xCoord, explorer.target.zCoord))
                        continue;
                    if (!w.blockExists(gx, y, gz)) continue;
                    int surface = w.getHeightValue(gx, gz);
                    int[] heights = { surface - 2, surface - 1, surface, surface + 1, y - 1, y, y + 1 };
                    for (int i = 0; i < heights.length; i++) {
                        int gy = heights[i];
                        boolean duplicate = false;
                        for (int j = 0; j < i; j++) if (heights[j] == gy) duplicate = true;
                        if (!duplicate && gy > 0 && gy < w.getHeight() - 2 && dryShore(w, gx, gy, gz))
                            explorer.entranceCandidates.add(new int[] { gx, gy, gz });
                    }
                }
            final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
            final int tx = explorer.target.xCoord, tz = explorer.target.zCoord;
            explorer.entranceCandidates
                .sort((a, b) -> Double.compare(entranceScore(a, px, pz, tx, tz), entranceScore(b, px, pz, tx, tz)));
        }
        for (int attempts = 0; attempts < 1
            && explorer.entranceCandidate < explorer.entranceCandidates.size(); attempts++) {
            int[] goal = explorer.entranceCandidates.get(explorer.entranceCandidate++);
            if (Math.abs(goal[0] - x) + Math.abs(goal[2] - z) <= 1 && Math.abs(goal[1] - y) <= 1) {
                if (mc.thePlayer.onGround && dryShore(w, x, y, z) && safe(w, x, y, z)) {
                    explorer.digging = true;
                    explorer.seekingDryEntrance = false;
                    explorer.entranceSelected = true;
                    explorer.savedSlot = mc.thePlayer.inventory.currentItem;
                    explorer.entranceCandidates.clear();
                    explorer.status = "Planning access from the nearest safe structure wall";
                    return true;
                }
            }
            if (mc.thePlayer.isInWater()) {
                com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge.Start started = explorer.baritoneNavigation
                    .startPuzzleTileApproach(goal[0], goal[1], goal[2]);
                if (started == com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge.Start.STARTED) {
                    explorer.baritoneCoordinator.beginShoreMove(goal);
                    explorer.walking = true;
                    explorer.searching = false;
                    explorer.status = "Swimming to a dry entrance";
                    return true;
                }
            }
            List<int[]> candidate = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

                public boolean stand(int a, int b, int c) {
                    return approachTerrain.stand(a, b, c);
                }

                public boolean clear(int a, int b, int c) {
                    return passable(w, a, b, c);
                }

                public boolean checkpoint(int a, int b, int c) {
                    return solidCheckpoint(w, a, b, c);
                }
            }, x, y, z, goal[0], goal[2], 48);
            if (candidate.isEmpty()) continue;
            int[] last = candidate.get(candidate.size() - 1);
            if (last[1] != goal[1] || Math.abs(last[0] - goal[0]) + Math.abs(last[2] - goal[2]) > 1
                || !dryShore(w, last[0], last[1], last[2])) continue;
            if (candidate.size() > Math.max(12D, 2D * Math.hypot(goal[0] - x, goal[2] - z))) continue;
            explorer.route.clear();
            explorer.route.addAll(candidate);
            explorer.walkingSearch = null;
            explorer.walkingSearchNeedsResume = false;
            explorer.continuationSearch = null;
            explorer.extendedFrom = null;
            explorer.approachPlan = null;
            explorer.approachAnchor = null;
            explorer.approachReady = false;
            explorer.segmentX = mc.thePlayer.posX;
            explorer.segmentZ = mc.thePlayer.posZ;
            explorer.seekingDryEntrance = true;
            explorer.searching = false;
            explorer.entranceCandidates.clear();
            explorer.stairX = 0;
            explorer.stairZ = 0;
            explorer.status = "Moving to selected dry entrance";
            return true;
        }
        if (explorer.entranceCandidate < explorer.entranceCandidates.size()) {
            explorer.status = "Checking alternate dry entrances";
            return true;
        }
        explorer.entranceCandidates.clear();
        return false;
    }

    public boolean attemptStairStep(Minecraft mc, Explorer explorer, int destinationY) {
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround) {
            explorer.status = "Waiting to land on the next stair";
            return ++settlingTicks <= 40;
        }
        settlingTicks = 0;
        if (!safe(mc.theWorld, x, y, z)) return false;
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            explorer.singleStep(mc, x, y, z, true);
            return true;
        }
        if (stairX == 0 && stairZ == 0) {
            stairX = Integer.signum(explorer.target.xCoord - x);
            stairZ = Integer.signum(explorer.target.zCoord - z);
            if (Math.abs(explorer.target.xCoord - x) >= Math.abs(explorer.target.zCoord - z)) stairZ = 0;
            else stairX = 0;
            if (stairX == 0 && stairZ == 0) stairX = 1;
        }
        int nx = x + stairX, nz = z + stairZ;
        if (!ExcavationPolicy.beside(x, z, nx, nz)) return false;
        World w = mc.theWorld;
        for (int by = y; by <= y + 1; by++) {
            net.minecraft.block.Block plant = w.getBlock(x, by, z);
            if (plant == net.minecraft.init.Blocks.snow_layer) continue;
            if (FoliagePolicy.canClear(
                isFoliage(plant),
                w.getTileEntity(x, by, z) != null,
                plant.getBlockHardness(w, x, by, z),
                x,
                by,
                z,
                x,
                y,
                z,
                nx,
                nz,
                plant == Blocks.snow_layer)) return explorer.minePlannedBlock(mc, x, by, z, destinationY);
        }
        if (explorer.excavationTerrain.excavationCost(w, x, y, z, nx, destinationY, nz) < 0) return false;
        if (destinationY > y && !passable(w, x, y + 2, z)) return explorer.minePlannedBlock(mc, x, y + 2, z, y + 2);
        int clearTop = Math.max(y, destinationY) + 1;
        if (!w.blockExists(nx, clearTop + 1, nz)) return false;
        if (clearTop > y + 4) return false;
        for (Object entity : w.loadedEntityList) {
            if (entity instanceof net.minecraft.entity.item.EntityFallingBlock) {
                net.minecraft.entity.Entity falling = (net.minecraft.entity.Entity) entity;
                if ((int) Math.floor(falling.posX) == nx && (int) Math.floor(falling.posZ) == nz
                    && falling.posY >= destinationY
                    && falling.posY <= clearTop + 12) {
                    explorer.releaseMiningWhileSandFalls(mc);
                    explorer.status = "Waiting for sand";
                    return true;
                }
            }
        }
        for (int by = destinationY; by <= clearTop; by++) {
            if (w.getBlock(nx, by, nz) instanceof net.minecraft.block.BlockFalling)
                return explorer.minePlannedBlock(mc, nx, by, nz, destinationY);
        }
        for (int by = clearTop; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (passable(w, nx, by, nz)) continue;
            if (FoliagePolicy.canClear(
                isFoliage(b),
                w.getTileEntity(nx, by, nz) != null,
                b.getBlockHardness(w, nx, by, nz),
                nx,
                by,
                nz,
                x,
                y,
                z,
                nx,
                nz,
                b == Blocks.snow_layer)) continue;
            if (!explorer.excavationTerrain.excavatable(w, nx, by, nz)) return false;
            if (w.getTileEntity(nx, by, nz) != null || b.getBlockHardness(w, nx, by, nz) < 0) return false;
        }
        for (int by = clearTop; by >= destinationY; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (isFoliage(b) && FoliagePolicy.canClear(
                true,
                w.getTileEntity(nx, by, nz) != null,
                b.getBlockHardness(w, nx, by, nz),
                nx,
                by,
                nz,
                x,
                y,
                z,
                nx,
                nz,
                b == Blocks.snow_layer)) return explorer.minePlannedBlock(mc, nx, by, nz, destinationY);
            if (b == Blocks.snow_layer || passable(w, nx, by, nz)) continue;
            return explorer.minePlannedBlock(mc, nx, by, nz, destinationY);
        }
        explorer.digTicks = 0;
        explorer.miningAim.reset();
        if (!safe(w, nx, destinationY, nz)) return false;
        explorer.singleStep(mc, nx, destinationY, nz, false);
        explorer.excavationTerrain.protectSupport(supportKey(nx, destinationY - 1, nz));
        if (!excavationRoute.isEmpty()) excavationRoute.remove(0);
        int lastX = nx, lastY = destinationY, lastZ = nz;
        while (!excavationRoute.isEmpty() && explorer.route.size() < 6) {
            int[] following = excavationRoute.get(0);
            if (!ExcavationPolicy.beside(lastX, lastZ, following[0], following[2]) || Math.abs(following[1] - lastY) > 1
                || !safe(w, following[0], following[1], following[2])
                || explorer.excavationTerrain
                    .excavationCost(w, lastX, lastY, lastZ, following[0], following[1], following[2]) < 0)
                break;
            boolean clear = true;
            for (int by = following[1]; by <= Math.max(lastY, following[1]) + 1; by++) {
                if (isFoliage(w.getBlock(following[0], by, following[2]))
                    || !passable(w, following[0], by, following[2])) clear = false;
            }
            if (!clear) break;
            explorer.route.add(new int[] { following[0], following[1], following[2], 0 });
            explorer.excavationTerrain.protectSupport(supportKey(following[0], following[1] - 1, following[2]));
            lastX = following[0];
            lastY = following[1];
            lastZ = following[2];
            excavationRoute.remove(0);
        }
        excavationReplans = 0;
        stairSteps++;
        explorer.lastDistance = Double.MAX_VALUE;
        explorer.stalled = 0;
        return true;
    }

    public int foliageSupportY(World w, Explorer explorer, int x, int foliageY, int z, int playerX, int footY,
        int playerZ, int destinationY) {
        net.minecraft.block.Block foliage = w.getBlock(x, foliageY, z);
        if (!FoliagePolicy.canClear(
            isFoliage(foliage),
            w.getTileEntity(x, foliageY, z) != null,
            foliage.getBlockHardness(w, x, foliageY, z),
            x,
            foliageY,
            z,
            playerX,
            footY,
            playerZ,
            x,
            z,
            foliage == Blocks.snow_layer)) return -1;
        int supportY = foliageY;
        for (int scan = 0; scan < 8 && supportY > destinationY - 1; scan++) {
            if (!isFoliage(w.getBlock(x, supportY, z))) break;
            supportY--;
        }
        if (supportY < destinationY || supportY > Math.max(footY, destinationY) + 1
            || !explorer.excavationTerrain.excavatable(w, x, supportY, z)) return -1;
        return supportY;
    }
}
