package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.isFoliage;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

final class ExplorerMiningTarget {

    private ExplorerMiningTarget() {}

    static net.minecraft.util.Vec3 visibleMiningPoint(Minecraft mc, int x, int y, int z) {
        net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        double height = 1D;
        net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
        if (block == Blocks.snow_layer) {
            block.setBlockBoundsBasedOnState(mc.theWorld, x, y, z);
            net.minecraft.util.AxisAlignedBB bounds = block.getSelectedBoundingBoxFromPool(mc.theWorld, x, y, z);
            if (bounds != null) height = Math.max(0.01D, Math.min(1D, bounds.maxY - y));
        }
        double[] offsets = { 0.5D, 0.3D, 0.7D };
        int[] faces = { 2, 3, 4, 5, 1, 0 };
        for (double a : offsets) for (double b : offsets) for (int face : faces) {
            double dx = face == 4 ? 0.02D : face == 5 ? 0.98D : a;
            double dy = face == 0 ? 0.02D : face == 1 ? 0.98D : face >= 4 ? a : b;
            double dz = face == 2 ? 0.02D : face == 3 ? 0.98D : face >= 4 ? b : face <= 1 ? b : a;
            net.minecraft.util.Vec3 point = net.minecraft.util.Vec3.createVectorHelper(x + dx, y + dy * height, z + dz);
            net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
            if (hit != null && hit.blockX == x
                && hit.blockY == y
                && hit.blockZ == z
                && (hit.sideHit == face || isFoliage(block))
                && eye.squareDistanceTo(hit.hitVec)
                    <= Math.pow(Math.min(4.2D, mc.playerController.getBlockReachDistance()), 2))
                return point;
        }
        return null;
    }
}
