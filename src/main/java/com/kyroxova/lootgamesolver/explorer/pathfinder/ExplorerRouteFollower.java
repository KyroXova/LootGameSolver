package com.kyroxova.lootgamesolver.explorer.pathfinder;

import static com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry.isRewardChestPit;
import static com.kyroxova.lootgamesolver.client.loot.RewardChestGeometry.rewardChestAhead;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.safe;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.walkSurfaceY;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.AxisAlignedBB;

public final class ExplorerRouteFollower {

    private int chestPitJumpBoostTicks;

    public void boostChestPitJump() {
        chestPitJumpBoostTicks = 12;
    }

    public void follow(Minecraft mc, NavigationInput input, List<int[]> route, int[] next, int footY, float turn,
        double distance, double dx, double dz, boolean centering, boolean movingToPuzzleCenter, boolean digging,
        boolean returning) {
        if (input == null) return;
        boolean inWater = mc.thePlayer.isInWater();
        boolean straight = isStraightRun(mc, route, next, footY, turn, distance, movingToPuzzleCenter, digging);
        input.forward = Math.abs(turn) > 35F ? 0F
            : route.size() == 1 && !movingToPuzzleCenter ? (float) Math.min(1D, distance * 1.5D) : 1F;
        input.hop = shouldHop(mc, next, footY, turn, distance, dx, dz, returning, inWater);
        if (distance > 0.02D) {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            double speed = Math.min(
                centering ? (movingToPuzzleCenter ? 0.75D : 0.4D) : straight ? 1D : 0.95D,
                !centering && route.size() > 1 ? 1D : distance * 1.5D);
            input.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
            input.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
        }
        applySprintAndWaterControls(mc, input, turn, centering, digging, inWater);
    }

    private static boolean isStraightRun(Minecraft mc, List<int[]> route, int[] next, int footY, float turn,
        double distance, boolean movingToPuzzleCenter, boolean digging) {
        if (digging || mc.thePlayer.isInWater() || next[1] != footY) return false;
        if (movingToPuzzleCenter || (route.size() == 1 && distance > 0.35D)) return true;
        boolean shortClearRun = route.size() >= 2 && route.get(1)[1] == footY
            && distance > 0.85D
            && Math.abs(turn) < 16F;
        if (shortClearRun) return true;
        return route.size() >= 3 && route.get(1)[1] == footY
            && route.get(2)[1] == footY
            && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
            && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
            && safe(mc.theWorld, route.get(1)[0], footY, route.get(1)[2])
            && safe(mc.theWorld, route.get(2)[0], footY, route.get(2)[2]);
    }

    private boolean shouldHop(Minecraft mc, int[] next, int footY, float turn, double distance, double dx, double dz,
        boolean returning, boolean inWater) {
        double targetSurface = walkSurfaceY(mc.theWorld, next[0], next[1], next[2]);
        boolean needsJump = needsStepOrSurfaceJump(mc, next, footY, targetSurface);
        boolean jumpDistance = distance <= 1.65D || (mc.thePlayer.isCollidedHorizontally && distance <= 2.2D);
        boolean standardJump = needsJump && (Math.abs(turn) < 45F || mc.thePlayer.isCollidedHorizontally)
            && jumpDistance
            && (mc.thePlayer.onGround || inWater);
        return standardJump || shouldPerformPitOrEscapeJump(mc, turn, distance, dx, dz, returning) || inWater;
    }

    private static boolean needsStepOrSurfaceJump(Minecraft mc, int[] next, int footY, double targetSurface) {
        Block nextBlock = mc.theWorld.getBlock(next[0], next[1], next[2]);
        AxisAlignedBB nextBox = nextBlock.getCollisionBoundingBoxFromPool(mc.theWorld, next[0], next[1], next[2]);
        double rise = targetSurface >= 0D ? targetSurface - mc.thePlayer.boundingBox.minY : (double) (next[1] - footY);
        if (targetSurface >= 0D && rise > 0D && rise <= 0.55D) return false;
        boolean climbRewardChest = nextBlock instanceof BlockChest && nextBox != null
            && nextBox.maxY - mc.thePlayer.boundingBox.minY > 0.45D
            && nextBox.maxY - mc.thePlayer.boundingBox.minY <= 1D;
        boolean climbSurface = NavigationSurfacePolicy.needsJump(mc.thePlayer.boundingBox.minY, targetSurface);
        return climbSurface || climbRewardChest
            || (targetSurface < 0D && next[1] > footY)
            || (mc.thePlayer.isCollidedHorizontally && (targetSurface < 0D || (rise > 0.55D && rise <= 1.001D)));
    }

    private boolean shouldPerformPitOrEscapeJump(Minecraft mc, float turn, double distance, double dx, double dz,
        boolean returning) {
        boolean chestPitJump = returning && chestPitJumpBoostTicks > 0
            && isRewardChestPit(mc)
            && mc.thePlayer.onGround
            && Math.abs(turn) < 45F
            && distance < 4D;
        if (chestPitJumpBoostTicks > 0) chestPitJumpBoostTicks--;
        boolean escapeRewardPit = returning && rewardChestAhead(mc, dx, dz);
        return (escapeRewardPit && Math.abs(turn) < 35F && distance < 2.5D && mc.thePlayer.onGround) || chestPitJump;
    }

    private static void applySprintAndWaterControls(Minecraft mc, NavigationInput input, float turn, boolean centering,
        boolean digging, boolean inWater) {
        boolean wantSprint = !centering && !digging
            && !inWater
            && Math.abs(turn) < 70F
            && input.forward > 0.05F
            && mc.thePlayer.getFoodStats()
                .getFoodLevel() > 6;
        if (wantSprint) {
            input.forward = 1.0F;
            if (Math.abs(turn) < 25F) input.sideways = 0.0F;
        } else if (inWater && Math.abs(turn) < 60F) {
            input.forward = 1.0F;
        }
        mc.thePlayer.setSprinting(wantSprint);
        if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
            KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSprint.getKeyCode(),
                wantSprint || GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
        }
    }
}
