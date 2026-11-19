package com.kyroxova.lootgamesolver.explorer.pathfinder;

import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.isRewardChestPit;
import static com.kyroxova.lootgamesolver.client.RewardChestGeometry.rewardChestAhead;
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
        boolean inWater = mc.thePlayer.isInWater();
        boolean stepUp = next[1] > footY || climbRewardChest || climbSurface || mc.thePlayer.isCollidedHorizontally;
        boolean jumpDistance = distance <= 1.65D || (mc.thePlayer.isCollidedHorizontally && distance <= 2.2D);
        input.hop = (stepUp && Math.abs(turn) < 45F && jumpDistance && (mc.thePlayer.onGround || inWater))
            || (escapeRewardPit && Math.abs(turn) < 35F && distance < 2.5D && mc.thePlayer.onGround)
            || chestPitJump
            || inWater;
        if (distance > 0.02D) {
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            double speed = Math.min(
                centering ? (movingToPuzzleCenter ? 0.75D : 0.4D) : straight ? 1D : 0.95D,
                !centering && route.size() > 1 ? 1D : distance * 1.5D);
            input.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
            input.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
        }
        boolean wantSprint = !centering && !digging
            && !inWater
            && Math.abs(turn) < 70F
            && input.forward > 0.05F
            && mc.thePlayer.getFoodStats()
                .getFoodLevel() > 6;
        if (wantSprint) {
            input.forward = 1.0F;
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
