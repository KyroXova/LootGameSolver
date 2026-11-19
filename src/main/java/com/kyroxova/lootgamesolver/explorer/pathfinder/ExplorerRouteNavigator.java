package com.kyroxova.lootgamesolver.explorer.pathfinder;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import com.kyroxova.lootgamesolver.explorer.trail.FoliagePolicy;
import static com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry.isRewardChestPit;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.staircaseDirection;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.isFoliage;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.safe;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerRouteNavigator {

    public boolean handleReturnAndExitRecovery(Minecraft mc, Explorer explorer) {
        if (explorer.returning && explorer.resumeSearchAfterReturn && !explorer.insideDungeonRoom(mc)) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            int[] entrance = explorer.trail.points.pointAt(0);
            boolean reachedMouth = entrance != null && Math.abs(x - entrance[0]) + Math.abs(z - entrance[2]) <= 1
                && y >= entrance[1]
                && Math.abs(y - entrance[1]) <= 2
                && ExplorerTerrain.solidCheckpoint(mc.theWorld, x, y, z);
            if (reachedMouth || mc.theWorld.canBlockSeeTheSky(x, y, z)) {
                explorer.continueProgressiveSearch(mc, true);
                return true;
            }
        }
        if (explorer.returning && explorer.escapingChestPit && mc.thePlayer.onGround && !isRewardChestPit(mc)) {
            explorer.escapingChestPit = false;
            explorer.clearChestPitSearch();
            explorer.route.clear();
            explorer.returnJoinSearch = null;
            explorer.returnTrailRoute = null;
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            if (explorer.insideDungeonRoom(mc) && explorer.startRoomExitSearch(mc)) return true;
            if (explorer.startSavedReturn(mc, explorer.resumeSearchAfterReturn, 128)) return true;
            explorer.retryExitLater(mc);
            return true;
        }
        if (explorer.returning && isRewardChestPit(mc) && jumpOutOfRewardPit(mc, explorer)) return true;
        if (explorer.exitRecoveryTicks > 0) {
            if (--explorer.exitRecoveryTicks == 0) {
                if (explorer.startChestPitReturn(mc, explorer.resumeSearchAfterReturn)) return true;
                explorer.blockedWalkNodes.clear();
                final net.minecraft.world.World exitWorld = mc.theWorld;
                final GroundPathfinder.Terrain ground = explorer.walkingTerrain(exitWorld);
                int sx = (int) Math.floor(mc.thePlayer.posX), sy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
                int sz = (int) Math.floor(mc.thePlayer.posZ);
                explorer.openExitSearch = new OpenExitSearch(new OpenExitSearch.Terrain() {

                    public boolean stand(int x, int y, int z) {
                        return ground.stand(x, y, z);
                    }

                    public boolean clear(int x, int y, int z) {
                        return ground.clear(x, y, z);
                    }

                    public boolean exit(int x, int y, int z) {
                        if (!ExplorerTerrain.solidCheckpoint(exitWorld, x, y, z)) return false;
                        int index = explorer.trail.points.nearestIndex(x, y, z, 0);
                        if (explorer.trail.points.size() >= 2) return index >= 0 && index < explorer.trail.points.size() - 1;
                        return exitWorld.canBlockSeeTheSky(x, y, z);
                    }
                }, sx, sy, sz);
            }
            return true;
        }
        if (explorer.openExitSearch != null) {
            explorer.status = "Searching connected open blocks for an exit";
            if (!explorer.openExitSearch.advance(96, System.nanoTime() + 3_000_000L)) return true;
            java.util.List<int[]> openPath = explorer.openExitSearch.path();
            explorer.openExitSearch = null;
            if (!openPath.isEmpty()) {
                explorer.route.clear();
                explorer.route.addAll(openPath);
                explorer.followingOpenExit = true;
                explorer.segmentX = mc.thePlayer.posX;
                explorer.segmentZ = mc.thePlayer.posZ;
                explorer.status = "Following connected open route";
                return true;
            }
            boolean started = explorer.startChestPitReturn(mc, true);
            if (!started) started = ++explorer.exitRecoveryAttempts % 2 == 1 && explorer.digTowardSavedExit(mc);
            if (!started) started = explorer.startSavedReturn(mc, true, 128);
            if (!started) started = explorer.startRoomExitSearch(mc);
            if (!started) explorer.retryExitLater(mc);
            return true;
        }
        if (explorer.chestPitNav.active()) {
            explorer.advanceChestPitReturn(mc);
            return true;
        }
        return false;
    }

    public boolean advanceRoute(Minecraft mc, Explorer explorer) {
        if (!explorer.digging && !explorer.returning
            && !explorer.roomExitCrossing
            && !explorer.roomEntryCrossing
            && explorer.route.size() >= 3
            && mc.thePlayer.onGround) {
            int cx = (int) Math.floor(mc.thePlayer.posX), cy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int cz = (int) Math.floor(mc.thePlayer.posZ);
            for (int i = Math.min(explorer.route.size() - 1, 8); i >= 2; i--) {
                int[] step = explorer.route.get(i);
                int dx = Math.abs(step[0] - cx), dz = Math.abs(step[2] - cz);
                if (step.length > 3 && step[3] != 0 || step[1] != cy
                    || dx > 1
                    || dz > 1
                    || dx + dz == 0
                    || !safe(mc.theWorld, step[0], cy, step[2])
                    || dx == 1 && dz == 1
                        && (!safe(mc.theWorld, step[0], cy, cz) || !safe(mc.theWorld, cx, cy, step[2])))
                    continue;
                boolean special = false;
                for (int j = 0; j < i; j++) {
                    if (explorer.route.get(j).length > 3 && explorer.route.get(j)[3] != 0) special = true;
                }
                if (special) continue;
                explorer.route.subList(0, i)
                    .clear();
                break;
            }
        }
        if (!explorer.digging && !explorer.roomExitCrossing && !explorer.roomEntryCrossing && explorer.route.size() >= 2) {
            int[] first = explorer.route.get(0), second = explorer.route.get(1);
            int cx = (int) Math.floor(mc.thePlayer.posX), cy = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int cz = (int) Math.floor(mc.thePlayer.posZ);
            if ((first.length < 4 || first[3] == 0) && (second.length < 4 || second[3] == 0)
                && first[1] == cy
                && second[1] == cy
                && Math.abs(second[0] - cx) == 1
                && Math.abs(second[2] - cz) == 1
                && safe(mc.theWorld, second[0], cy, cz)
                && safe(mc.theWorld, cx, cy, second[2])
                && safe(mc.theWorld, second[0], cy, second[2])) {
                explorer.route.remove(0);
            }
        }
        int[] next = explorer.route.get(0);
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (explorer.returning && explorer.roomExitCrossing
            && mc.thePlayer.onGround
            && Math.abs(next[0] - px) + Math.abs(next[2] - pz) <= 1
            && explorer.clearRoomExitObstruction(mc, next, px, py, pz)) return true;
        if (!explorer.returning && explorer.trail.target != null && mc.thePlayer.onGround) {
            explorer.recordAccessPoint(px, py, pz);
        }
        if (explorer.digging && mc.thePlayer.onGround && Math.abs(next[0] - px) + Math.abs(next[2] - pz) <= 1) {
            for (int by = py; by <= py + 1; by++) {
                net.minecraft.block.Block plant = mc.theWorld.getBlock(next[0], by, next[2]);
                if (plant == net.minecraft.init.Blocks.snow_layer
                    && mc.theWorld.getBlockMetadata(next[0], by, next[2]) <= 1) continue;
                if (FoliagePolicy.canClear(
                    isFoliage(plant),
                    mc.theWorld.getTileEntity(next[0], by, next[2]) != null,
                    plant.getBlockHardness(mc.theWorld, next[0], by, next[2]),
                    next[0],
                    by,
                    next[2],
                    px,
                    py,
                    pz,
                    next[0],
                    next[2],
                    plant == Blocks.snow_layer)) {
                    explorer.minePlannedBlock(mc, next[0], by, next[2], next[1]);
                    return true;
                }
            }
        }
        boolean diagonal = next[0] != px && next[2] != pz && next[1] == py;
        if (diagonal && (!safe(mc.theWorld, next[0], py, pz) || !safe(mc.theWorld, px, py, next[2]))) {
            if (explorer.returning) {
                if (explorer.rerouteSavedReturn(mc, 1)) return true;
                if (explorer.digTowardSavedExit(mc)) return true;
                explorer.retryExitLater(mc);
                return true;
            }
            if (!explorer.digging) explorer.plan(mc);
            else explorer.stop(mc, "Diagonal clearance changed");
            return true;
        }
        if (!safe(mc.theWorld, next[0], next[1], next[2]) || Math.abs(py - next[1]) > (mc.thePlayer.isInWater() ? 2 : 1)) {
            if (explorer.returning) {
                if (next[1] > py && explorer.tryPlaceReturnStep(mc, next)) return true;
                if (explorer.rerouteSavedReturn(mc, 1)) return true;
                if (explorer.digTowardSavedExit(mc)) return true;
                explorer.retryExitLater(mc);
                return true;
            }
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (!explorer.digging && mc.thePlayer.onGround && safe(mc.theWorld, px, py, pz)) {
                explorer.plan(mc);
                if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                    explorer.lastDistance = Double.MAX_VALUE;
                    explorer.stalled = 0;
                    explorer.status = "Rerouted around obstruction";
                    return true;
                }
            }
            explorer.stop(mc, "Route obstructed or unsafe");
            return true;
        }
        double dx = next[0] + 0.5D - mc.thePlayer.posX;
        double dz = next[2] + 0.5D - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        boolean centering = next.length > 3 && next[3] == 2;
        boolean passed = !centering && py == next[1]
            && NavigationSteering
                .passed(mc.thePlayer.posX, mc.thePlayer.posZ, explorer.segmentX, explorer.segmentZ, next[0] + 0.5D, next[2] + 0.5D);
        if ((!explorer.digging || mc.thePlayer.onGround) && (passed
            || NavigationSteering.reached(distance, py, next[1], mc.thePlayer.isInWater(), explorer.route.size() > 1))) {
            explorer.segmentX = next[0] + 0.5D;
            explorer.segmentZ = next[2] + 0.5D;
            if (!explorer.returning && explorer.trail.target != null) explorer.recordAccessPoint(px, py, pz);
            explorer.route.remove(0);
            if (explorer.roomExitCrossing) explorer.roomExitJumpRetries = 0;
            if (explorer.returning && !explorer.insideDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(px, py, pz)) {
                explorer.continueProgressiveSearch(mc, true);
                return true;
            }
            if (!explorer.returning && !explorer.digging
                && explorer.approachReady
                && explorer.approachPlan != null
                && explorer.approachAnchor == next
                && !explorer.approachPlan.path.isEmpty()) {
                explorer.route.clear();
                explorer.excavationRoute.clear();
                explorer.excavationRoute.addAll(explorer.approachPlan.path);
                explorer.approachPlan = null;
                explorer.approachAnchor = null;
                explorer.approachReady = false;
                explorer.digging = true;
                explorer.entranceSelected = true;
                explorer.savedSlot = mc.thePlayer.inventory.currentItem;
                explorer.status = "Following planned excavation from this checkpoint";
                return true;
            }
            explorer.lastDistance = Double.MAX_VALUE;
            explorer.stalled = 0;
            explorer.stallRecoveries = 0;
            if (explorer.route.isEmpty()) {
                if (mc.thePlayer.onGround && !explorer.digging) {
                    mc.thePlayer.motionX *= 0.3D;
                    mc.thePlayer.motionZ *= 0.3D;
                }
                return true;
            }
            next = explorer.route.get(0);
            centering = next.length > 3 && next[3] == 2;
            if (!safe(mc.theWorld, next[0], next[1], next[2])
                || Math.abs(py - next[1]) > (mc.thePlayer.isInWater() ? 2 : 1)) {
                if (explorer.returning && next[1] > py && explorer.tryPlaceReturnStep(mc, next)) return true;
                if (explorer.returning && explorer.rerouteSavedReturn(mc, 1)) return true;
                if (explorer.returning && explorer.digTowardSavedExit(mc)) return true;
                explorer.stop(mc, "Next route segment is no longer safe");
                return true;
            }
            dx = next[0] + 0.5D - mc.thePlayer.posX;
            dz = next[2] + 0.5D - mc.thePlayer.posZ;
            distance = Math.sqrt(dx * dx + dz * dz);
        }
        if (explorer.digging && distance < 0.2D && !mc.thePlayer.onGround) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (++explorer.settlingTicks > 40) explorer.stop(mc, "Stair landing did not complete");
            return true;
        }
        explorer.settlingTicks = 0;
        float desired = centering ? mc.thePlayer.rotationYaw
            : NavigationSteering.heading(
                mc.thePlayer.posX,
                mc.thePlayer.posZ,
                next[0] + 0.5D,
                next[2] + 0.5D,
                mc.thePlayer.rotationYaw);
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(desired - mc.thePlayer.rotationYaw);
        int upcomingPitch = staircaseDirection(explorer.route, py);
        if (upcomingPitch != 0) {
            int[] firstStep = null;
            for (int[] step : explorer.route) {
                if (step[1] != py) {
                    firstStep = step;
                    break;
                }
            }
            if (firstStep != null
                && Math.hypot(firstStep[0] + 0.5D - mc.thePlayer.posX, firstStep[2] + 0.5D - mc.thePlayer.posZ)
                    <= 1.8D) {
                if (explorer.cameraPitchDirection == 0) {
                    explorer.pitchLastY = py;
                    explorer.pitchLandingX = mc.thePlayer.posX;
                    explorer.pitchLandingZ = mc.thePlayer.posZ;
                }
                explorer.cameraPitchDirection = upcomingPitch;
            }
        }
        if (explorer.cameraPitchDirection != 0 && py != explorer.pitchLastY) {
            explorer.pitchLastY = py;
            explorer.pitchLandingX = mc.thePlayer.posX;
            explorer.pitchLandingZ = mc.thePlayer.posZ;
        }
        if (explorer.cameraPitchDirection != 0 && upcomingPitch == 0
            && mc.thePlayer.onGround
            && Math.hypot(mc.thePlayer.posX - explorer.pitchLandingX, mc.thePlayer.posZ - explorer.pitchLandingZ) >= 1D) {
            explorer.cameraPitchDirection = 0;
        }
        boolean swimming = mc.thePlayer.isInWater() || mc.theWorld.getBlock(next[0], next[1], next[2])
            .getMaterial() == net.minecraft.block.material.Material.water;
        float desiredPitch = swimming ? 0F : explorer.cameraPitchDirection > 0 ? -10F : explorer.cameraPitchDirection < 0 ? 30F : 0F;
        Float pathHeading = NavigationSteering.pathLookHeading(explorer.route, mc.thePlayer.posX, mc.thePlayer.posZ);
        if (!centering && pathHeading != null) explorer.look(pathHeading, desiredPitch);
        explorer.status = centering ? "Centering on supported ground"
            : explorer.returning ? "Returning along saved access path" : "Following checked route";
        if (!centering && Math.abs(turn) > 55F) {
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            mc.thePlayer.setSprinting(false);
            explorer.status = "Aligning with route";
            return true;
        }
        if (distance >= explorer.lastDistance - 0.005D) explorer.stalled++;
        else explorer.stalled = 0;
        explorer.lastDistance = distance;
        if (explorer.stalled >= 8 && explorer.stalled <= 24 && mc.thePlayer.onGround && explorer.navigationInput != null) {
            explorer.navigationInput.hop = true;
            explorer.navigationInput.forward = 1.0F;
        }
        if (explorer.stalled > 40) {
            explorer.stalled = 0;
            explorer.lastDistance = Double.MAX_VALUE;
            if (explorer.returning && explorer.roomExitCrossing
                && mc.thePlayer.onGround
                && next[1] == py + 1
                && distance < 1.8D
                && distance > 0.02D
                && safe(mc.theWorld, next[0], next[1], next[2])
                && explorer.roomExitJumpRetries++ < 3
                && explorer.navigationInput != null) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                explorer.navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance);
                explorer.navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance);
                explorer.navigationInput.hop = true;
                explorer.status = "Climbing exit step";
                return true;
            }
            if (!explorer.route.isEmpty()) {
                int[] failed = explorer.route.get(0);
                explorer.markBlockedWalkNode(failed[0], failed[1], failed[2]);
            }
            if (explorer.returning) {
                if (isRewardChestPit(mc)) {
                    explorer.routeFollower.boostChestPitJump();
                    explorer.status = "Jumping clear of the reward chests";
                    return true;
                }
                if (explorer.rerouteSavedReturn(mc, 1) || explorer.digTowardSavedExit(mc)) {
                    explorer.status = "Recovering along the saved exit route";
                    return true;
                }
                explorer.retryExitLater(mc);
                return true;
            }
            if (!explorer.route.isEmpty()) {
                int[] failed = explorer.route.get(0);
                explorer.markBlockedWalkNode(failed[0], failed[1], failed[2]);
            }
            if (++explorer.stallRecoveries <= 5) {
                explorer.route.clear();
                explorer.walkingSearch = null;
                explorer.continuationSearch = null;
                explorer.extendedFrom = null;
                if (explorer.searching) {
                    if (explorer.nextSearchLeg(mc)) {
                        explorer.status = "Stalled checkpoint skipped; trying another exploration leg";
                        return true;
                    }
                } else {
                    if (explorer.digging) {
                        explorer.excavationRoute.clear();
                        explorer.excavationSearch = null;
                        explorer.status = "Planning another route from the tunnel";
                        return true;
                    }
                    if (explorer.beginExistingAccessSearch(mc)) return true;
                    explorer.plan(mc);
                    if (!explorer.route.isEmpty() || explorer.walkingSearch != null) {
                        explorer.status = "Stalled checkpoint avoided; following alternate route";
                        return true;
                    }
                    if (LootGameSolverConfig.staircaseEnabled && !mc.thePlayer.isInWater() && explorer.selectDigEntrance(mc)) {
                        explorer.status = "Walking route blocked; checking a safe dig entrance";
                        return true;
                    }
                }
            }
            explorer.rotateSearch(mc);
            explorer.nextSearchLeg(mc);
            return true;
        }
        explorer.routeFollower.follow(
            mc,
            explorer.navigationInput,
            explorer.route,
            next,
            py,
            turn,
            distance,
            dx,
            dz,
            centering,
            explorer.movingToPuzzleCenter,
            explorer.digging,
            explorer.returning);
        return true;
    }

    public boolean jumpOutOfRewardPit(Minecraft mc, Explorer explorer) {
        if (explorer.navigationInput == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int nearest = explorer.trail.points.nearestIndex(x, y, z, 128);
        int[] goal = nearest < 0 ? null : explorer.trail.points.pointAt(nearest);
        int[] best = RewardChestGeometry.exitDirection(mc.theWorld, x, y, z, goal);
        if (best == null) return false;
        float yaw = NavigationSteering.heading(
            mc.thePlayer.posX,
            mc.thePlayer.posZ,
            x + best[0] + 0.5D,
            z + best[1] + 0.5D,
            mc.thePlayer.rotationYaw);
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        explorer.look(yaw, -10F);
        if (Math.abs(turn) < 35F) {
            explorer.navigationInput.forward = 1F;
            explorer.navigationInput.hop = mc.thePlayer.onGround;
            explorer.escapingChestPit = true;
        }
        explorer.stalled = 0;
        explorer.lastDistance = Double.MAX_VALUE;
        explorer.status = "Jumping clear of reward chests";
        return true;
    }

    public boolean swimToNearestShore(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isInWater()) return false;
        int px = (int) Math.floor(mc.thePlayer.posX);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int[] best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int r = 1; r <= 24; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    int x = px + dx, z = pz + dz;
                    int y = mc.theWorld.getHeightValue(x, z);
                    if (y <= 0) continue;
                    net.minecraft.block.Block b = mc.theWorld.getBlock(x, y - 1, z);
                    net.minecraft.block.Block above = mc.theWorld.getBlock(x, y, z);
                    net.minecraft.block.Block above2 = mc.theWorld.getBlock(x, y + 1, z);
                    if (b.isNormalCube() && !b.getMaterial()
                        .isLiquid()
                        && !above.getMaterial()
                            .isLiquid()
                        && !above2.getMaterial()
                            .isLiquid()) {
                        double dsq = (x - px) * (x - px) + (z - pz) * (z - pz);
                        if (dsq < bestDistSq) {
                            bestDistSq = dsq;
                            best = new int[] { x, y, z };
                        }
                    }
                }
            }
            if (best != null) break;
        }
        if (best != null) {
            explorer.route.clear();
            explorer.route.add(best);
            explorer.walking = true;
            explorer.status = "Swimming: Shore";
            if (explorer.navigationInput != null) {
                explorer.navigationInput.forward = 1F;
                explorer.navigationInput.hop = true;
            }
            return true;
        }
        return false;
    }
}
