package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.safe;
import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.walkSurfaceY;
import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.isRewardChestPit;
import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.rewardChestAhead;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;

final class ExplorerRouteFollower {

    private int chestPitJumpBoostTicks;

    void boostChestPitJump() {
        chestPitJumpBoostTicks = 12;
    }

    void follow(Minecraft mc, NavigationInput input, List<int[]> route, int[] next, int footY, float turn,
        double distance, double dx, double dz, boolean centering, boolean movingToPuzzleCenter, boolean digging,
        boolean returning) {
        boolean shortClearRun = route.size() >= 2 && next[1] == footY
            && route.get(1)[1] == footY
            && distance > 0.85D
            && Math.abs(turn) < 16F;
        boolean straight = !digging && !mc.thePlayer.isInWater()
            && next[1] == footY
            && (movingToPuzzleCenter || shortClearRun
                || route.size() == 1 && distance > 0.35D
                || route.size() >= 3 && !mc.thePlayer.isInWater()
                    && route.get(1)[1] == footY
                    && route.get(2)[1] == footY
                    && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
                    && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
                    && safe(mc.theWorld, route.get(1)[0], footY, route.get(1)[2])
                    && safe(mc.theWorld, route.get(2)[0], footY, route.get(2)[2]));
        if (input == null) return;
        input.forward = Math.abs(turn) > 35F ? 0F
            : route.size() == 1 && !movingToPuzzleCenter ? (float) Math.min(1D, distance * 1.5D) : 1F;
        Block nextBlock = mc.theWorld.getBlock(next[0], next[1], next[2]);
        AxisAlignedBB nextBox = nextBlock.getCollisionBoundingBoxFromPool(mc.theWorld, next[0], next[1], next[2]);
        boolean climbRewardChest = nextBlock instanceof BlockChest && nextBox != null
            && nextBox.maxY - mc.thePlayer.boundingBox.minY > 0.45D
            && nextBox.maxY - mc.thePlayer.boundingBox.minY <= 1D;
        boolean escapeRewardPit = returning && rewardChestAhead(mc, dx, dz);
        boolean climbSurface = NavigationSurfacePolicy
            .needsJump(mc.thePlayer.boundingBox.minY, walkSurfaceY(mc.theWorld, next[0], next[1], next[2]));
        boolean chestPitJump = returning && chestPitJumpBoostTicks > 0
            && isRewardChestPit(mc)
            && mc.thePlayer.onGround
            && Math.abs(turn) < 45F
            && distance < 4D;
        if (chestPitJumpBoostTicks > 0) chestPitJumpBoostTicks--;
        input.hop = ((next[1] > footY || climbRewardChest || climbSurface) && Math.abs(turn) < 35F && distance < 1.4D)
            || escapeRewardPit && Math.abs(turn) < 35F && distance < 2.5D
            || chestPitJump
            || mc.thePlayer.isInWater();
        if (distance > 0.02D) {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            double speed = Math.min(
                centering ? (movingToPuzzleCenter ? 0.75D : 0.4D) : straight ? 1D : 0.95D,
                !centering && route.size() > 1 ? 1D : distance * 1.5D);
            input.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
            input.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
        }
        mc.thePlayer.setSprinting(
            !centering && !digging && !mc.thePlayer.isInWater() && Math.abs(turn) < 35F && input.forward > 0.8F);
    }
}
