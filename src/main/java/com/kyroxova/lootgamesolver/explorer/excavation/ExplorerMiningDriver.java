package com.kyroxova.lootgamesolver.explorer.excavation;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry;
import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import com.kyroxova.lootgamesolver.explorer.trail.FoliagePolicy;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerGeometry.supportKey;
import static com.kyroxova.lootgamesolver.explorer.excavation.ExplorerMiningTarget.visibleMiningPoint;
import static com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain.isFoliage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.client.DigToolSelector;

public class ExplorerMiningDriver {

    public void releaseMiningInput(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.gameSettings == null || explorer == null) return;
        explorer.miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
    }

    public void releaseMiningWhileSandFalls(Minecraft mc, Explorer explorer) {
        if (mc == null || mc.gameSettings == null || explorer == null) return;
        explorer.miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        if (mc.playerController != null) mc.playerController.resetBlockRemoving();
        explorer.miningAim.reset();
        explorer.digTicks = 0;
    }

    public void reset(Minecraft mc, Explorer explorer) {
        if (explorer == null) return;
        releaseMiningInput(mc, explorer);
        if (mc != null) explorer.toolTransfer.cancel(mc);
        explorer.miningAim.reset();
        explorer.digTicks = 0;
        explorer.aimTicks = 0;
        explorer.toolWaitTicks = 0;
        explorer.waitingForTool = false;
        explorer.miningTarget = Long.MIN_VALUE;
        explorer.miningSlot = -1;
        if (explorer.digging && mc != null && mc.playerController != null) {
            mc.playerController.resetBlockRemoving();
        }
        if (explorer.savedSlot >= 0 && mc != null && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = explorer.savedSlot;
        }
        explorer.savedSlot = -1;
    }

    public boolean minePlannedBlock(Minecraft mc, Explorer explorer, int nx, int by, int nz, int destinationY) {
        World w = mc.theWorld;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        net.minecraft.block.Block b = w.getBlock(nx, by, nz);
        int mineX = nx, mineY = by, mineZ = nz;
        net.minecraft.util.Vec3 aimPoint = visibleMiningPoint(mc, mineX, mineY, mineZ);
        net.minecraft.util.MovingObjectPosition obstruction = w.rayTraceBlocks(
            net.minecraft.util.Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ),
            net.minecraft.util.Vec3.createVectorHelper(nx + 0.5D, by + 0.5D, nz + 0.5D));
        if (obstruction != null
            && obstruction.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            net.minecraft.block.Block visible = w.getBlock(obstruction.blockX, obstruction.blockY, obstruction.blockZ);
            boolean plant = isFoliage(visible);
            boolean fallingObstruction = b instanceof net.minecraft.block.BlockFalling && obstruction.blockX == nx
                && obstruction.blockZ == nz
                && obstruction.blockY > by
                && obstruction.blockY <= y + 4
                && visible instanceof net.minecraft.block.BlockFalling
                && explorer.excavationTerrain.excavatable(w, obstruction.blockX, obstruction.blockY, obstruction.blockZ);
            if (aimPoint == null && (fallingObstruction || FoliagePolicy.canClear(
                plant,
                w.getTileEntity(obstruction.blockX, obstruction.blockY, obstruction.blockZ) != null,
                visible.getBlockHardness(w, obstruction.blockX, obstruction.blockY, obstruction.blockZ),
                obstruction.blockX,
                obstruction.blockY,
                obstruction.blockZ,
                x,
                y,
                z,
                nx,
                nz,
                visible == Blocks.snow_layer))) {
                mineX = obstruction.blockX;
                mineY = obstruction.blockY;
                mineZ = obstruction.blockZ;
                b = visible;
                aimPoint = visibleMiningPoint(mc, mineX, mineY, mineZ);
            }
        }
        int slot = DigToolSelector.equip(mc, b, w.getBlockMetadata(mineX, mineY, mineZ));
        if (slot >= 9) {
            if (explorer.toolTransfer.start(mc, slot)) {
                explorer.status = "Opening inventory to prepare tool";
                return true;
            }
            if (++explorer.toolWaitTicks > 40) {
                explorer.stop(
                    mc,
                    "\u00A7eAuto-dig paused: inventory swap could not finish. Close other inventories and clear the cursor.");
                return true;
            }
            explorer.status = "Preparing digging tool...";
            return true;
        }
        explorer.toolWaitTicks = 0;
        if (slot < 0) {
            explorer.waitingForTool = true;
            explorer.miningAttack.release(mc.gameSettings.keyBindAttack, GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
            mc.playerController.resetBlockRemoving();
            explorer.digTicks = 0;
            explorer.miningAim.reset();
            explorer.status = "Mining paused: no usable tool for " + b.getLocalizedName();
            return true;
        }
        explorer.waitingForTool = false;
        long targetKey = supportKey(mineX, mineY, mineZ);
        if (targetKey != explorer.miningTarget || slot != explorer.miningSlot) {
            explorer.miningTarget = targetKey;
            explorer.miningSlot = slot;
            explorer.digTicks = 0;
            explorer.aimTicks = 0;
        }
        mc.thePlayer.motionX = 0;
        mc.thePlayer.motionZ = 0;
        mc.thePlayer.inventory.currentItem = slot;
        mc.playerController.updateController();
        if (aimPoint == null) {
            explorer.miningAim.reset();
            mc.playerController.resetBlockRemoving();
            explorer.status = "Finding visible block face";
            return false;
        }
        double aimX = aimPoint.xCoord - mc.thePlayer.posX;
        double aimY = aimPoint.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double aimZ = aimPoint.zCoord - mc.thePlayer.posZ;
        float yaw = (float) (Math.atan2(aimZ, aimX) * 180D / Math.PI) - 90F;
        float pitch = (float) (-Math.atan2(aimY, Math.sqrt(aimX * aimX + aimZ * aimZ)) * 180D / Math.PI);
        explorer.look(yaw, pitch);
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        boolean matches = hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == mineX
            && hit.blockY == mineY
            && hit.blockZ == mineZ;
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        if (!explorer.miningAim.ready(
            mineX,
            mineY,
            mineZ,
            matches,
            Math.sqrt(aimX * aimX + aimY * aimY + aimZ * aimZ),
            Math.min(4.2D, mc.playerController.getBlockReachDistance()),
            yawError,
            pitch - mc.thePlayer.rotationPitch)) {
            mc.playerController.resetBlockRemoving();
            if (++explorer.aimTicks > 80) {
                explorer.status = "Mining target has no reachable visible face";
                return false;
            }
            explorer.status = "Aiming at visible excavation block";
            return true;
        }
        explorer.aimTicks = 0;
        float damage = b.getPlayerRelativeBlockHardness(mc.thePlayer, w, mineX, mineY, mineZ);
        int allowance = MiningAttackInput.allowance(damage, mc.thePlayer.capabilities.isCreativeMode);
        if (allowance == 0 || ++explorer.digTicks > allowance) {
            explorer.stop(mc, "Mining stalled; check tool strength or block protection");
            return true;
        }
        explorer.miningAttack.hold(mc.gameSettings.keyBindAttack);
        if (b instanceof net.minecraft.block.BlockFalling
            || w.getBlock(mineX, mineY + 1, mineZ) instanceof net.minecraft.block.BlockFalling) {
            explorer.fallingBlocks.record(b, mineX, mineY, mineZ);
        }
        explorer.status = "Mining a safe path";
        return true;
    }
}
