package com.kyroxova.lootgamesolver.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.kyroxova.lootgamesolver.LootGameSolver;
import com.kyroxova.lootgamesolver.client.AimHelper;

public final class LootGamesInteractionHelper {

    private LootGamesInteractionHelper() {}

    public static boolean targetHit(MovingObjectPosition hit, int x, int y, int z) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == x
            && hit.blockY == y
            && hit.blockZ == z
            && hit.hitVec != null;
    }

    public static MovingObjectPosition visibleTarget(Minecraft mc, int x, int y, int z) {
        Vec3 eye = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        double reach = mc.playerController.getBlockReachDistance();
        MovingObjectPosition hit = mc.theWorld
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        if (targetHit(hit, x, y, z)) return hit;

        double targetY = eye.yCoord > y + 1.0D ? y + 0.99D : (eye.yCoord < y ? y + 0.01D : y + 0.5D);
        Vec3 directTarget = Vec3.createVectorHelper(x + 0.5D, targetY, z + 0.5D);
        if (eye.squareDistanceTo(directTarget) <= reach * reach) {
            hit = mc.theWorld.rayTraceBlocks(eye, directTarget);
            if (targetHit(hit, x, y, z)) return hit;
        }
        return null;
    }

    public static boolean hasClearView(Minecraft mc, int[] position) {
        if (position == null || position.length < 3 || mc.thePlayer == null || mc.theWorld == null) return false;
        Vec3 eye = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 center = Vec3.createVectorHelper(position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
        return targetHit(mc.theWorld.rayTraceBlocks(eye, center), position[0], position[1], position[2]);
    }

    public static boolean topFaceVisible(Minecraft mc, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null) return false;
        return topFaceVisibleFrom(mc, position, mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
    }

    public static boolean topFaceVisibleFrom(Minecraft mc, int[] position, double x, double y, double z) {
        if (mc == null || mc.theWorld == null || position == null) return false;
        Vec3 eye = Vec3.createVectorHelper(x, y, z);
        Vec3 top = Vec3.createVectorHelper(position[0] + 0.5D, position[1] + 0.999D, position[2] + 0.5D);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, top);
        return targetHit(hit, position[0], position[1], position[2]) && hit.sideHit == 1;
    }

    public static MovingObjectPosition visibleMasterFace(Minecraft mc, int x, int y, int z) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return null;
        Vec3 eye = Vec3.createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        double reach = Math.min(4.2D, mc.playerController.getBlockReachDistance());
        double[] offsets = { 0.5D, 0.15D, 0.85D };
        for (double ox : offsets) {
            for (double oy : offsets) {
                for (double oz : offsets) {
                    Vec3 point = Vec3.createVectorHelper(x + ox, y + oy, z + oz);
                    if (eye.squareDistanceTo(point) > reach * reach) continue;
                    MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, point);
                    if (targetHit(hit, x, y, z)) return hit;
                }
            }
        }
        return null;
    }

    public static boolean selectSafeInteractionItem(Minecraft mc) {
        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null) return true;
        int fallbackSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(slot);
            if (candidate == null) {
                mc.thePlayer.inventory.currentItem = slot;
                if (mc.playerController != null) mc.playerController.updateController();
                return true;
            }
            if (fallbackSlot == -1 && !(candidate.getItem() instanceof ItemBlock)) {
                fallbackSlot = slot;
            }
        }
        if (fallbackSlot != -1) {
            mc.thePlayer.inventory.currentItem = fallbackSlot;
            if (mc.playerController != null) mc.playerController.updateController();
            return true;
        }
        return true;
    }

    public static boolean preparePlayerPositionAndAim(EntityPlayer player, int x, int y, int z) {
        return preparePlayerPositionAndAim(player, x, y, z, false);
    }

    public static boolean preparePlayerPositionAndAim(EntityPlayer player, int x, int y, int z, boolean top) {
        if (player == null) return false;
        double eyeY = player.posY + player.getEyeHeight();
        double targetY = eyeY < y ? y + 0.05D : (top || eyeY > y + 1.0D ? y + 0.95D : y + 0.5D);
        double dx = x + 0.5D - player.posX;
        double dy = targetY - eyeY;
        double dz = z + 0.5D - player.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq > 17.64D) return false;

        float yaw = AimHelper.calculateYaw(dx, dz);
        float pitch = AimHelper.calculatePitch(dx, dy, dz);
        return AimHelper.stepTurn(player, yaw, pitch, 45.0F, 35.0F, 15.0F, 15.0F);
    }

    public static boolean prepareExactCenterAndAim(EntityPlayer player, int x, int y, int z) {
        if (player == null) return false;
        double dx = x + 0.5D - player.posX, dz = z + 0.5D - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int footY = (int) Math.floor(player.boundingBox.minY);
        if (!player.onGround || footY != y + 1 || horizontal > 0.18D) {
            if (horizontal > 0.18D) {
                double step = Math.min(0.045D, (horizontal - 0.12D) * 0.12D);
                player.motionX += dx / horizontal * step;
                player.motionZ += dz / horizontal * step;
            } else if (footY < y + 1 && player.onGround) {
                player.jump();
            }
            return false;
        }
        float yaw = AimHelper.calculateYaw(dx, dz);
        double dy = y + 0.5D - player.posY - player.getEyeHeight();
        float pitch = (float) (-Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        return AimHelper.stepTurn(player, yaw, pitch, 35.0F, 25.0F, 2.0F, 2.0F);
    }

    public static boolean activateMaster(Minecraft mc, int x, int y, int z) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
        net.minecraft.block.Block master = mc.theWorld.getBlock(x, y, z);
        boolean valid = LootGamesTileAccess.isMasterBlock(master);
        if (!valid) {
            TileEntity te = mc.theWorld.getTileEntity(x, y, z);
            if (te != null && LootGamesTileAccess.isMasterTile(te.getClass().getName())) valid = true;
        }
        if (!valid) return false;
        if (!selectSafeInteractionItem(mc)) return false;

        MovingObjectPosition hit = visibleMasterFace(mc, x, y, z);
        if (hit == null) return false;
        double dx = hit.hitVec.xCoord - mc.thePlayer.posX;
        double dy = hit.hitVec.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double dz = hit.hitVec.zCoord - mc.thePlayer.posZ;
        mc.thePlayer.rotationYaw = AimHelper.calculateYaw(dx, dz);
        mc.thePlayer.rotationPitch = AimHelper.calculatePitch(dx, dy, dz);
        int side = hit.sideHit;
        Vec3 hitVec = hit.hitVec;
        if (BaritoneBlockBridge.click(mc, x, y, z, side, hitVec, false)) return true;
        boolean direct = mc.playerController
            .onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, side, hitVec);
        mc.thePlayer.swingItem();
        return direct;
    }

    public static boolean activateFloorActivator(Minecraft mc, int[] position, FoliageClearer foliageClearer) {
        if (position == null || mc.theWorld == null || mc.thePlayer == null || mc.playerController == null)
            return false;
        int x = position[0], y = position[1], z = position[2];
        if (!LootGamesTileAccess.isFloorActivator(mc.theWorld.getBlock(x, y, z))) return false;
        if (mc.theWorld.getBlock(x, y, z).getClass().getName().equals(LootGamesTileAccess.LOOTGAMES_PKG + "common.block.MSActivatorBlock")
            && foliageClearer != null && foliageClearer.clearLine(mc, x, y, z)) {
            return false;
        }
        if (!selectSafeInteractionItem(mc) || !preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
        MovingObjectPosition hit = visibleTarget(mc, x, y, z);
        int side = 1;
        Vec3 hitVec = null;
        if (hit != null) {
            side = hit.sideHit;
            hitVec = hit.hitVec;
        } else {
            double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
            side = eyeY < y ? 0 : 1;
            hitVec = Vec3.createVectorHelper(x + 0.5D, y + (side == 0 ? 0.0D : 1.0D), z + 0.5D);
        }
        if (BaritoneBlockBridge.click(mc, x, y, z, side, hitVec, false)) return true;
        boolean direct = mc.playerController
            .onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, side, hitVec);
        mc.thePlayer.swingItem();
        return direct;
    }

    public static boolean activateBoardCenter(Minecraft mc, Object game, int[] position, FoliageClearer foliageClearer) {
        try {
            if (game == null || mc.theWorld == null || mc.thePlayer == null || mc.playerController == null)
                return false;
            if (position == null) return false;
            int x = position[0], y = position[1], z = position[2];
            if (game.getClass().getName().equals(LootGamesTileAccess.MS_GAME)
                && foliageClearer != null && foliageClearer.clearLine(mc, x, y, z)) return false;
            double dx = x + 0.5D - mc.thePlayer.posX;
            double dy = y + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double dz = z + 0.5D - mc.thePlayer.posZ;
            double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance());
            if (dx * dx + dy * dy + dz * dz > reach * reach || !selectSafeInteractionItem(mc)
                || !preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
            MovingObjectPosition hit = visibleTarget(mc, x, y, z);
            if (hit == null) return false;
            return BaritoneBlockBridge.click(mc, x, y, z, hit.sideHit, hit.hitVec, false);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not activate the LootGames board center", e);
            return false;
        }
    }
}
