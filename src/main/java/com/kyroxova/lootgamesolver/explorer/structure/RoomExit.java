package com.kyroxova.lootgamesolver.explorer.structure;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.samePosition;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.isDungeonWall;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.passable;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.pathfinder.GroundPathfinder;
import com.kyroxova.lootgamesolver.explorer.trail.ContactHazards;

public final class RoomExit {

    public List<int[]> candidates;
    public int candidateIndex;
    public int[] active;
    public boolean planning;
    public boolean needsResume;
    public boolean crossing;
    public int jumpRetries;

    public void reset() {
        candidates = null;
        candidateIndex = 0;
        active = null;
        planning = false;
        needsResume = false;
        crossing = false;
        jumpRetries = 0;
    }

    public boolean isInsideDungeonRoom(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        int roomY = explorer.knownStructure != null ? explorer.knownStructure[1]
            : explorer.target == null ? Integer.MIN_VALUE : explorer.target.yCoord;
        if (roomY == Integer.MIN_VALUE) return false;
        return isInsideRoom(mc, explorer, roomY);
    }

    public boolean isInsideRoom(Minecraft mc, Explorer explorer, int roomY) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        int midX = (bounds[0] + bounds[1]) >> 1, midZ = (bounds[2] + bounds[3]) >> 1;
        int[][] walls = { { bounds[0], midZ }, { bounds[1], midZ }, { midX, bounds[2] }, { midX, bounds[3] } };
        int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
        for (int by = Math.max(0, roomY - 12); by <= Math.min(255, roomY + 12); by++) {
            int count = 0;
            for (int[] wall : walls) if (isDungeonWall(mc.theWorld, wall[0], by, wall[1])) count++;
            if (count >= 2) {
                bottom = Math.min(bottom, by);
                top = Math.max(top, by);
            }
        }
        return insideVerticalBounds(y, bottom, top);
    }

    public static boolean insideVerticalBounds(int feetY, int bottom, int top) {
        return bottom != Integer.MAX_VALUE && top > bottom && feetY >= bottom && feetY < top;
    }

    public boolean startSearch(Minecraft mc, Explorer explorer) {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (explorer.staircaseDugPath != null && explorer.staircaseDugPath.size() >= 2) {
            explorer.returning = true;
            explorer.returnAvailable = false;
            explorer.resumeSearchAfterReturn = true;
            explorer.walking = true;
            explorer.searching = false;
            explorer.digging = false;
            return startDugPathReturn(mc, explorer);
        }
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        candidates = explorer.roomGeometry
            .findRoomExitCandidates(mc, bounds, (w, bx, by, bz) -> clearableExitColumn(w, explorer, bx, by, bz));
        if (explorer.trail.points.size() >= 2 && candidates.size() > 1) {
            int[] mouth = savedRoomMouth(explorer, bounds);
            candidates.sort((a, b) -> Double.compare(exitMouthDistance(a, mouth), exitMouthDistance(b, mouth)));
        }
        candidateIndex = 0;
        planning = false;
        needsResume = false;
        crossing = false;
        return startNextSearch(mc, explorer);
    }

    public boolean startDugPathReturn(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || explorer.staircaseDugPath.size() < 2)
            return false;
        int[] end = explorer.staircaseDugPath.get(explorer.staircaseDugPath.size() - 1);
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (samePosition(end, new int[] { x, y, z })) {
            explorer.route.clear();
            appendDugPath(explorer);
            explorer.roomExitDugPathJoin = false;
            explorer.status = "Ascending dug path to surface";
            return true;
        }
        explorer.route.clear();
        explorer.returnJoinSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            x,
            y,
            z,
            end[0],
            end[1],
            end[2],
            LootGameSolverConfig.explorationRange);
        explorer.roomExitDugPathJoin = true;
        explorer.status = "Finding a safe link to dug path";
        return true;
    }

    public void appendDugPath(Explorer explorer) {
        int last = explorer.staircaseDugPath.size() - 1;
        if (last < 1) return;
        int[] end = explorer.staircaseDugPath.get(last);
        if (explorer.route.isEmpty() || !samePosition(explorer.route.get(explorer.route.size() - 1), end))
            explorer.route.add(new int[] { end[0], end[1], end[2], 0 });
        for (int i = last - 1; i >= 0; i--) {
            int[] p = explorer.staircaseDugPath.get(i);
            explorer.route.add(new int[] { p[0], p[1], p[2], 0 });
        }
        explorer.roomExitDugPathJoin = false;
        explorer.status = "Ascending dug path to surface";
    }

    public boolean startNextSearch(Minecraft mc, Explorer explorer) {
        if (candidates == null || candidateIndex >= candidates.size()) return false;
        active = candidates.get(candidateIndex);
        explorer.returnJoinSearch = new GroundPathfinder.Search(
            explorer.walkingTerrain(mc.theWorld),
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            active[0],
            active[1],
            active[2],
            LootGameSolverConfig.explorationRange);
        planning = true;
        needsResume = false;
        explorer.returning = true;
        explorer.returnAvailable = false;
        explorer.resumeSearchAfterReturn = true;
        explorer.walking = true;
        explorer.searching = false;
        explorer.digging = false;
        explorer.route.clear();
        explorer.status = "Finding room exit";
        return true;
    }

    public void appendWaypoint(Explorer explorer, int[] exit, int offset) {
        int[] point = new int[] { exit[offset], exit[offset + 1], exit[offset + 2] };
        if (explorer.route.isEmpty() || !samePosition(explorer.route.get(explorer.route.size() - 1), point))
            explorer.route.add(point);
    }

    public static boolean clearableExitColumn(World world, Explorer explorer, int x, int y, int z) {
        if (!world.blockExists(x, y - 1, z) || !world.getBlock(x, y - 1, z)
            .isNormalCube()) return false;
        for (int by = y; by <= y + 1; by++) {
            if (!world.blockExists(x, by, z)) return false;
            net.minecraft.block.Block block = world.getBlock(x, by, z);
            if (ContactHazards.dangerous(block, world.getBlockMetadata(x, by, z)) || block.getMaterial()
                .isLiquid() || !passable(world, x, by, z) && !explorer.excavationTerrain.excavatable(world, x, by, z))
                return false;
        }
        return true;
    }

    public boolean clearObstruction(Minecraft mc, Explorer explorer, int[] next, int x, int y, int z) {
        World world = mc.theWorld;
        if (next[1] > y && !passable(world, x, y + 2, z)) {
            net.minecraft.block.Block overhead = world.getBlock(x, y + 2, z);
            if (!(overhead instanceof net.minecraft.block.BlockFalling)
                && explorer.excavationTerrain.excavatable(world, x, y + 2, z))
                return explorer.minePlannedBlock(mc, x, y + 2, z, y + 2);
        }
        if (next[0] == x && next[2] == z) return false;
        for (int by = next[1] + 1; by >= next[1]; by--) {
            if (passable(world, next[0], by, next[2])) continue;
            net.minecraft.block.Block block = world.getBlock(next[0], by, next[2]);
            if (ContactHazards.dangerous(block, world.getBlockMetadata(next[0], by, next[2]))
                || !explorer.excavationTerrain.excavatable(world, next[0], by, next[2])) return false;
            if (!passable(world, next[0], by, next[2]))
                return explorer.minePlannedBlock(mc, next[0], by, next[2], next[1]);
        }
        return false;
    }

    public boolean planRoofEscape(Minecraft mc, Explorer explorer) {
        if (explorer.target == null || explorer.knownStructure == null || explorer.returnDigging || explorer.returning)
            return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (y <= explorer.target.yCoord + 3 || !isDungeonWall(mc.theWorld, x, y - 1, z)) return false;
        int[] bounds = explorer.roomGeometry.dungeonRoomBounds(mc.theWorld, x, y, z, explorer.ticks);
        if (bounds == null || x <= bounds[0] || x >= bounds[1] || z <= bounds[2] || z >= bounds[3]) return false;
        int[][] exits = { { bounds[0] - 3, y, z }, { bounds[1] + 3, y, z }, { x, y, bounds[2] - 3 },
            { x, y, bounds[3] + 3 } };
        int[] best = exits[0];
        for (int[] exit : exits) {
            if (Math.abs(exit[0] - x) + Math.abs(exit[2] - z) < Math.abs(best[0] - x) + Math.abs(best[2] - z))
                best = exit;
        }
        explorer.roofEscapeGoal = best;
        explorer.excavationRoute.clear();
        explorer.excavationSearch = null;
        explorer.excavationReplans = 0;
        explorer.digging = true;
        explorer.entranceSelected = true;
        explorer.status = "Planning a safe route off the roof to a side wall";
        return true;
    }

    private static double exitMouthDistance(int[] candidate, int[] mouth) {
        if (mouth == null || candidate == null) return Double.MAX_VALUE;
        int cx = candidate.length > 3 ? candidate[3] : candidate[0];
        int cz = candidate.length > 5 ? candidate[5] : candidate[2];
        return Math.hypot(cx - mouth[0], cz - mouth[2]);
    }

    private int[] savedRoomMouth(Explorer explorer, int[] bounds) {
        for (int i = explorer.trail.points.size() - 1; i >= 0; i--) {
            int[] point = explorer.trail.points.pointAt(i);
            boolean besideX = (Math.abs(point[0] - bounds[0]) <= 1 || Math.abs(point[0] - bounds[1]) <= 1)
                && point[2] >= bounds[2]
                && point[2] <= bounds[3];
            boolean besideZ = (Math.abs(point[2] - bounds[2]) <= 1 || Math.abs(point[2] - bounds[3]) <= 1)
                && point[0] >= bounds[0]
                && point[0] <= bounds[1];
            if (besideX || besideZ) return point;
        }
        return explorer.trail.points.pointAt(explorer.trail.points.size() - 1);
    }
}
