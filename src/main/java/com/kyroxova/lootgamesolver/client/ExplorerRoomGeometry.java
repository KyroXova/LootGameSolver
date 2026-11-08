package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

final class ExplorerRoomGeometry {

    interface ClearableColumn {

        boolean test(World world, int x, int y, int z);
    }

    private int[] cachedRoomBounds;
    private int roomBoundsCacheX, roomBoundsCacheY, roomBoundsCacheZ, roomBoundsCacheTick = -1000;
    private long roomBoundsCacheNanos, roomBoundsChunkSignature;
    private boolean roomBoundsCacheInitialized;

    void reset() {
        cachedRoomBounds = null;
        roomBoundsCacheTick = -1000;
        roomBoundsCacheInitialized = false;
    }

    List<int[]> findRoomExitCandidates(Minecraft mc, int[] bounds, ClearableColumn clearable) {
        List<int[]> found = new ArrayList<int[]>();
        int[][] sides = { { bounds[0], -1, 0 }, { bounds[1], 1, 0 }, { bounds[2], 0, -1 }, { bounds[3], 0, 1 } };
        for (int dy = -3; dy <= 4; dy++) {
            int floorY = (int) Math.floor(mc.thePlayer.boundingBox.minY) + dy;
            for (int[] side : sides) {
                boolean xSide = side[1] != 0;
                int alongMin = xSide ? bounds[2] + 1 : bounds[0] + 1;
                int alongMax = xSide ? bounds[3] - 1 : bounds[1] - 1;
                for (int along = alongMin; along <= alongMax; along++) {
                    int[] opening = RoomOpeningGeometry.positions(side[0], xSide, along, side[1], side[2], floorY);
                    int inX = opening[0], inZ = opening[2], outX = opening[3], outZ = opening[5];
                    int wallX = opening[6], wallZ = opening[8];
                    if (!passable(mc.theWorld, wallX, floorY, wallZ) || !passable(mc.theWorld, wallX, floorY + 1, wallZ)
                        || !(solidCheckpoint(mc.theWorld, outX, floorY, outZ)
                            || clearable.test(mc.theWorld, outX, floorY, outZ)))
                        continue;
                    boolean levelInside = safe(mc.theWorld, inX, floorY, inZ);
                    boolean lowerInside = !levelInside && safe(mc.theWorld, inX, floorY - 1, inZ)
                        && mc.theWorld.getBlock(wallX, floorY - 1, wallZ)
                            .isReplaceable(mc.theWorld, wallX, floorY - 1, wallZ)
                        && mc.theWorld.getBlock(wallX, floorY - 2, wallZ)
                            .isNormalCube();
                    int approachX = inX - side[1], approachZ = inZ - side[2];
                    boolean twoBelow = !levelInside && !lowerInside
                        && safe(mc.theWorld, inX, floorY - 2, inZ)
                        && safe(mc.theWorld, approachX, floorY - 2, approachZ)
                        && passable(mc.theWorld, inX, floorY, inZ)
                        && passable(mc.theWorld, inX, floorY + 1, inZ)
                        && mc.theWorld.getBlock(inX, floorY - 2, inZ)
                            .isReplaceable(mc.theWorld, inX, floorY - 2, inZ);
                    if (!levelInside && !lowerInside && !twoBelow) continue;
                    boolean sideWallA = isDungeonWall(
                        mc.theWorld,
                        xSide ? wallX : along - 1,
                        floorY,
                        xSide ? along - 1 : wallZ)
                        || isDungeonWall(mc.theWorld, xSide ? wallX : along - 1, floorY + 1, xSide ? along - 1 : wallZ);
                    boolean sideWallB = isDungeonWall(
                        mc.theWorld,
                        xSide ? wallX : along + 1,
                        floorY,
                        xSide ? along + 1 : wallZ)
                        || isDungeonWall(mc.theWorld, xSide ? wallX : along + 1, floorY + 1, xSide ? along + 1 : wallZ);
                    if (!sideWallA && !sideWallB) continue;
                    if (twoBelow) found.add(
                        new int[] { approachX, floorY - 2, approachZ, outX, floorY, outZ, wallX, floorY, wallZ, inX,
                            floorY - 1, inZ });
                    else if (lowerInside)
                        found.add(new int[] { inX, floorY - 1, inZ, outX, floorY, outZ, wallX, floorY, wallZ, 1 });
                    else found.add(opening);
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

    int[] dungeonRoomBounds(World world, int x, int y, int z, int ticks) {
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
