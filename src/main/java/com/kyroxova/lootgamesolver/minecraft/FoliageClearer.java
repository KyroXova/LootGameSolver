package com.kyroxova.lootgamesolver.minecraft;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/** Clears only a plant directly obstructing a requested puzzle interaction. */
final class FoliageClearer {

    private World world;
    private int x, y, z, ticks;
    private boolean mining, timedOut;

    boolean clearLine(Minecraft mc, int targetX, int targetY, int targetZ) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || mc.currentScreen != null) {
            reset();
            return false;
        }
        Vec3 eyes = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 target = Vec3.createVectorHelper(targetX + 0.5D, targetY + 1D, targetZ + 0.5D);
        MovingObjectPosition obstruction = mc.theWorld.rayTraceBlocks(eyes, target);
        if (obstruction == null || obstruction.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
            || obstruction.blockX == targetX && obstruction.blockY == targetY && obstruction.blockZ == targetZ) {
            reset();
            return false;
        }
        Block block = mc.theWorld.getBlock(obstruction.blockX, obstruction.blockY, obstruction.blockZ);
        float hardness = block
            .getBlockHardness(mc.theWorld, obstruction.blockX, obstruction.blockY, obstruction.blockZ);
        if (Math.abs(obstruction.blockX - targetX) > 4 || Math.abs(obstruction.blockZ - targetZ) > 4
            || obstruction.blockY < targetY
            || obstruction.blockY > targetY + 3
            || !eligible(
                block instanceof BlockBush,
                block.getMaterial() == Material.plants || block.getMaterial() == Material.vine
                    || block.getMaterial() == Material.leaves,
                mc.theWorld.getTileEntity(obstruction.blockX, obstruction.blockY, obstruction.blockZ) != null,
                hardness)
            || eyes.squareDistanceTo(obstruction.hitVec) > Math.pow(mc.playerController.getBlockReachDistance(), 2)) {
            reset();
            return false;
        }
        if (world != mc.theWorld || x != obstruction.blockX || y != obstruction.blockY || z != obstruction.blockZ) {
            reset();
            world = mc.theWorld;
            x = obstruction.blockX;
            y = obstruction.blockY;
            z = obstruction.blockZ;
        }
        if (timedOut) return true;
        if (++ticks > 200) {
            BaritoneBlockBridge.stopBreaking();
            mining = false;
            timedOut = true;
            return true;
        }
        double dx = obstruction.hitVec.xCoord - eyes.xCoord, dy = obstruction.hitVec.yCoord - eyes.yCoord;
        double dz = obstruction.hitVec.zCoord - eyes.zCoord;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
        mc.thePlayer.rotationYaw += MathHelper
            .clamp_float(MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw), -35F, 35F);
        mc.thePlayer.rotationPitch += MathHelper.clamp_float(pitch - mc.thePlayer.rotationPitch, -25F, 25F);
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == x
            && hit.blockY == y
            && hit.blockZ == z) {
            mining = BaritoneBlockBridge.startBreaking(hit);
        }
        return true;
    }

    static boolean eligible(boolean bush, boolean plantMaterial, boolean tile, float hardness) {
        return (bush || plantMaterial) && !tile && hardness >= 0F && hardness <= 1F;
    }

    boolean clearing() {
        return world != null && !timedOut;
    }

    boolean failed() {
        return timedOut;
    }

    void reset() {
        if (mining) BaritoneBlockBridge.stopBreaking();
        world = null;
        mining = false;
        timedOut = false;
        ticks = 0;
    }
}
