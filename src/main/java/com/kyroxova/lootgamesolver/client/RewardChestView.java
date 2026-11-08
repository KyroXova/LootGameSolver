package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;

final class RewardChestView {

    private RewardChestView() {}

    static net.minecraft.util.MovingObjectPosition visibleChest(Minecraft mc, Vec3 eye, int[] pos) {
        double[] offsets = { 0.2D, 0.5D, 0.8D };
        for (double y : offsets) for (double x : offsets) for (double z : offsets) {
            Vec3 point = Vec3.createVectorHelper(pos[0] + x, pos[1] + y, pos[2] + z);
            if (eye.squareDistanceTo(point) > 16D) continue;
            net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
            if (hit != null && hit.blockX == pos[0] && hit.blockY == pos[1] && hit.blockZ == pos[2]) return hit;
        }
        return null;
    }

    static int[] findAndMove(Minecraft mc, Explorer exploration, int[] chest, int[] center,
        List<int[]> failedChestViews) {
        int playerFloorY = (int) Math.floor(mc.thePlayer.boundingBox.minY) - 1;
        java.util.List<int[]> positions = new java.util.ArrayList<>();
        for (int floorY = Math.min(playerFloorY, chest[1] - 1); floorY <= Math.max(playerFloorY, chest[1]); floorY++)
            for (int radius = 1; radius <= 4; radius++)
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = chest[0] + dx, z = chest[2] + dz;
                    if (dx * dx + dz * dz > 4) continue;
                    if (center != null && Math.abs(x - center[0]) <= 1 && Math.abs(z - center[2]) <= 1) continue;
                    boolean failed = false;
                    for (int[] view : failedChestViews) if (view[0] == x && view[1] == floorY && view[2] == z) {
                        failed = true;
                        break;
                    }
                    if (failed || !mc.theWorld.blockExists(x, floorY, z)
                        || !mc.theWorld.getBlock(x, floorY, z)
                            .getMaterial()
                            .isSolid()
                        || !mc.theWorld.isAirBlock(x, floorY + 1, z)
                        || !mc.theWorld.isAirBlock(x, floorY + 2, z)) continue;
                    Vec3 eye = Vec3.createVectorHelper(x + 0.5D, floorY + 1D + mc.thePlayer.getEyeHeight(), z + 0.5D);
                    if (visibleChest(mc, eye, chest) != null) positions.add(new int[] { x, floorY, z });
                }
        positions.sort((a, b) -> {
            int da = Math.abs(a[0] - chest[0]) + Math.abs(a[2] - chest[2]);
            int db = Math.abs(b[0] - chest[0]) + Math.abs(b[2] - chest[2]);
            return da != db ? Integer.compare(da, db)
                : Double.compare(
                    mc.thePlayer.getDistanceSq(a[0] + 0.5D, a[1] + 1D, a[2] + 0.5D),
                    mc.thePlayer.getDistanceSq(b[0] + 0.5D, b[1] + 1D, b[2] + 0.5D));
        });
        for (int[] position : positions) if (exploration.moveToRewardView(mc, position)) {
            return position;
        }
        failedChestViews.clear();
        return null;
    }
}
