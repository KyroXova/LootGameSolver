package com.kyroxova.lootgamesolver.explorer.structure;

import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

public final class ExplorerRoomGeometry {

    public interface ClearableColumn {

        boolean test(World world, int x, int y, int z);
    }

    private int[] cachedRoomBounds;
    private int roomBoundsCacheX, roomBoundsCacheY, roomBoundsCacheZ, roomBoundsCacheTick = -1000;
    private long roomBoundsCacheNanos, roomBoundsChunkSignature;
    private boolean roomBoundsCacheInitialized;

    public void reset() {
        cachedRoomBounds = null;
        roomBoundsCacheTick = -1000;
        roomBoundsCacheInitialized = false;
    }

    public List<int[]> findRoomExitCandidates(Minecraft mc, int[] bounds, ClearableColumn clearable) {
        List<int[]> found = new ArrayList<int[]>();
        int[][] sides = { { bounds[0], -1, 0 }, { bounds[1], 1, 0 }, { bounds[2], 0, -1 }, { bounds[3], 0, 1 } };
        for (int dy = -3; dy <= 4; dy++) {
            int floorY = (int) Math.floor(mc.thePlayer.boundingBox.minY) + dy;
            for (int[] side : sides) {
                boolean xSide = side[1] != 0;
                int alongMin = xSide ? bounds[2] + 1 : bounds[0] + 1;
                int alongMax = xSide ? bounds[3] - 1 : bounds[1] - 1;
                for (int along = alongMin; along <= alongMax; along++) {
                    int[] candidate = buildExitCandidate(mc.theWorld, side, xSide, along, floorY, clearable);
                    if (candidate != null) found.add(candidate);
                }
            }
        }
        final double px = mc.thePlayer.posX, pz = mc.thePlayer.posZ;
        found.sort(
            (first, second) -> Double.compare(
                Math.hypot(first[0] + 0.5D - px, first[2] + 0.5D - pz),
                Math.hypot(second[0] + 0.5D - px, second[2] + 0.5D - pz)));
        return found;
    }

    private static int[] buildExitCandidate(World world, int[] side, boolean xSide, int along, int floorY,
        ClearableColumn clearable) {
        int[] opening = RoomOpeningGeometry.positions(side[0], xSide, along, side[1], side[2], floorY);
        int inX = opening[0], inZ = opening[2], outX = opening[3], outZ = opening[5];
        int wallX = opening[6], wallZ = opening[8];
        if (!passable(world, wallX, floorY, wallZ) || !passable(world, wallX, floorY + 1, wallZ)
            || !(solidCheckpoint(world, outX, floorY, outZ) || clearable.test(world, outX, floorY, outZ))) return null;
        if (!hasSideWallFrame(world, xSide, wallX, wallZ, along, floorY)) return null;

        boolean levelInside = safe(world, inX, floorY, inZ);
        if (levelInside) return opening;
        boolean lowerInside = safe(world, inX, floorY - 1, inZ) && world.getBlock(wallX, floorY - 1, wallZ)
            .isReplaceable(world, wallX, floorY - 1, wallZ)
            && world.getBlock(wallX, floorY - 2, wallZ)
                .isNormalCube();
        if (lowerInside) return new int[] { inX, floorY - 1, inZ, outX, floorY, outZ, wallX, floorY, wallZ, 1 };
        int approachX = inX - side[1], approachZ = inZ - side[2];
        boolean twoBelow = safe(world, inX, floorY - 2, inZ) && safe(world, approachX, floorY - 2, approachZ)
            && passable(world, inX, floorY, inZ)
            && passable(world, inX, floorY + 1, inZ)
            && world.getBlock(inX, floorY - 2, inZ)
                .isReplaceable(world, inX, floorY - 2, inZ);
        return twoBelow
            ? new int[] { approachX, floorY - 2, approachZ, outX, floorY, outZ, wallX, floorY, wallZ, inX, floorY - 1,
                inZ }
            : null;
    }

    private static boolean hasSideWallFrame(World world, boolean xSide, int wallX, int wallZ, int along, int floorY) {
        int aX = xSide ? wallX : along - 1, aZ = xSide ? along - 1 : wallZ;
        int bX = xSide ? wallX : along + 1, bZ = xSide ? along + 1 : wallZ;
        return isDungeonWall(world, aX, floorY, aZ) || isDungeonWall(world, aX, floorY + 1, aZ)
            || isDungeonWall(world, bX, floorY, bZ)
            || isDungeonWall(world, bX, floorY + 1, bZ);
    }

    public int[] dungeonRoomBounds(World world, int x, int y, int z, int ticks) {
        boolean nearby = roomBoundsCacheInitialized && Math.abs(x - roomBoundsCacheX) <= 32
            && Math.abs(z - roomBoundsCacheZ) <= 32;
        if (nearby && cachedRoomBounds != null && ticks - roomBoundsCacheTick < 200) return cachedRoomBounds;
        if (nearby && cachedRoomBounds == null
            && y == roomBoundsCacheY
            && System.nanoTime() - roomBoundsCacheNanos < 250_000_000L
            && loadedChunkSignature(world, roomBoundsCacheX, roomBoundsCacheY, roomBoundsCacheZ)
                == roomBoundsChunkSignature)
            return null;
        int[] xWallCounts = new int[65], zWallCounts = new int[65];
        for (int dx = -32; dx <= 32; dx++) for (int dz = -32; dz <= 32; dz++) {
            for (int dy = -3; dy <= 4; dy++) {
                int bx = x + dx, by = y + dy, bz = z + dz;
                if (!world.blockExists(bx, by, bz) || !isDungeonWall(world, bx, by, bz)) continue;
                xWallCounts[dx + 32]++;
                zWallCounts[dz + 32]++;
            }
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        // Use dense wall cross-sections as room edges; tunnel walls are sparse.
        for (int i = 0; i < 65; i++) {
            if (xWallCounts[i] >= 24) {
                minX = Math.min(minX, x + i - 32);
                maxX = Math.max(maxX, x + i - 32);
            }
            if (zWallCounts[i] >= 24) {
                minZ = Math.min(minZ, z + i - 32);
                maxZ = Math.max(maxZ, z + i - 32);
            }
        }
        roomBoundsCacheX = x;
        roomBoundsCacheY = y;
        roomBoundsCacheZ = z;
        roomBoundsCacheTick = ticks;
        roomBoundsCacheNanos = System.nanoTime();
        roomBoundsChunkSignature = loadedChunkSignature(world, x, y, z);
        roomBoundsCacheInitialized = true;
        cachedRoomBounds = minX == Integer.MAX_VALUE || maxX - minX < 8
            || maxZ - minZ < 8
            || minX <= x - 31
            || maxX >= x + 31
            || minZ <= z - 31
            || maxZ >= z + 31 ? null : new int[] { minX, maxX, minZ, maxZ };
        return cachedRoomBounds;
    }

    private long loadedChunkSignature(World world, int x, int y, int z) {
        long signature = 0L;
        int minChunkX = (x - 32) >> 4, maxChunkX = (x + 32) >> 4;
        int minChunkZ = (z - 32) >> 4, maxChunkZ = (z + 32) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++)
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++)
                signature = (signature << 1) | (world.blockExists(chunkX << 4, y, chunkZ << 4) ? 1L : 0L);
        return signature;
    }
}
