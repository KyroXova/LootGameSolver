package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.passable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

final class RewardChestGeometry {

    private RewardChestGeometry() {}

    static int[] exitDirection(World world, int x, int y, int z, int[] goal) {
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int[] direction : directions) {
            int nx = x + direction[0], nz = z + direction[1];
            if (!isRewardChest(world, nx, y, nz) || !passable(world, nx, y + 1, nz) || !passable(world, x, y + 2, z))
                continue;
            double score = goal == null ? 0D : Math.hypot(nx - goal[0], nz - goal[2]);
            if (score < bestScore) {
                best = direction;
                bestScore = score;
            }
        }
        return best;
    }

    static boolean isRewardChest(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && world.getBlock(x, y, z) instanceof net.minecraft.block.BlockChest;
    }

    static boolean rewardChestAhead(Minecraft mc, double dx, double dz) {
        if (!mc.thePlayer.onGround || dx * dx + dz * dz < 0.001D) return false;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) {
            net.minecraft.block.Block block = mc.theWorld.getBlock(x + ox, y, z + oz);
            if (!(block instanceof net.minecraft.block.BlockChest)) continue;
            double cx = x + ox + 0.5D - mc.thePlayer.posX, cz = z + oz + 0.5D - mc.thePlayer.posZ;
            if (cx * dx + cz * dz <= 0 || cx * cx + cz * cz > 2.25D) continue;
            net.minecraft.util.AxisAlignedBB box = block
                .getCollisionBoundingBoxFromPool(mc.theWorld, x + ox, y, z + oz);
            if (box != null && box.maxY - mc.thePlayer.boundingBox.minY > 0.45D
                && box.maxY - mc.thePlayer.boundingBox.minY <= 1D) return true;
        }
        return false;
    }

    static boolean isRewardChestPit(Minecraft mc) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null) return false;
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int sides = 0;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 } };
        for (int[] direction : directions) {
            boolean chest = false;
            for (int dy = 0; dy <= 1 && !chest; dy++) {
                int cx = x + direction[0], cy = y + dy, cz = z + direction[1];
                if (!isRewardChest(mc.theWorld, cx, cy, cz)) continue;
                net.minecraft.util.AxisAlignedBB box = mc.theWorld.getBlock(cx, cy, cz)
                    .getCollisionBoundingBoxFromPool(mc.theWorld, cx, cy, cz);
                chest = box != null && box.maxY > mc.thePlayer.boundingBox.minY + 0.3D;
            }
            if (chest) sides++;
        }
        return sides >= 3;
    }
}
