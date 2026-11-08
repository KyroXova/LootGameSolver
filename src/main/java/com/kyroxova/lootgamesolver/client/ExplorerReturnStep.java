package com.kyroxova.lootgamesolver.client;

import static com.kyroxova.lootgamesolver.client.ExplorerGeometry.samePosition;
import static com.kyroxova.lootgamesolver.client.ExplorerTerrain.safe;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

final class ExplorerReturnStep {

    enum Outcome {
        NONE,
        COMPLETE,
        WAITING,
        FAILED
    }

    private final InventoryToolTransfer toolTransfer;
    private final ExplorerCamera camera;
    private final MiningAimGate placementAim = new MiningAimGate();
    private int[] pendingReturnPlacement, pendingReturnWaypoint;
    private int[] activeReturnWaypoint;
    private int returnPlacementTicks, returnPlacementAimTicks;
    private int returnPlacementSlot = -1;
    private String status;

    ExplorerReturnStep(InventoryToolTransfer toolTransfer, ExplorerCamera camera) {
        this.toolTransfer = toolTransfer;
        this.camera = camera;
    }

    Outcome tick(Minecraft mc, boolean returning, List<int[]> route) {
        if (pendingReturnPlacement == null) {
            if (returnPlacementSlot >= 0 && (!returning || route.isEmpty()
                || activeReturnWaypoint == null
                || !samePosition(route.get(0), activeReturnWaypoint))) abandonAttempt(mc);
            return Outcome.NONE;
        }
        int px = pendingReturnPlacement[0], py = pendingReturnPlacement[1], pz = pendingReturnPlacement[2];
        if (mc.theWorld.getBlock(px, py, pz) != Blocks.air && mc.theWorld.getBlock(px, py, pz)
            .getMaterial()
            .isSolid()) {
            if (pendingReturnWaypoint != null && returning) {
                if (!route.isEmpty() && route.get(0)[0] == pendingReturnWaypoint[0]
                    && route.get(0)[2] == pendingReturnWaypoint[2]
                    && route.get(0)[1] == pendingReturnWaypoint[1] + 1) route.remove(0);
                if (route.isEmpty() || !samePosition(route.get(0), pendingReturnWaypoint))
                    route.add(0, pendingReturnWaypoint);
                pendingReturnWaypoint = null;
            }
            pendingReturnPlacement = null;
            restoreReturnPlacementSlot(mc);
            return Outcome.COMPLETE;
        }
        if (++returnPlacementTicks > 30) {
            pendingReturnPlacement = null;
            pendingReturnWaypoint = null;
            restoreReturnPlacementSlot(mc);
            return Outcome.FAILED;
        }
        return Outcome.WAITING;
    }

    boolean selectingBlock() {
        return returnPlacementSlot >= 0;
    }

    String status() {
        return status;
    }

    void reset(Minecraft mc) {
        abandonAttempt(mc);
    }

    boolean tryPlaceReturnStep(Minecraft mc, int[] next, boolean returning) {
        status = null;
        if (pendingReturnPlacement != null) return true;
        if (returnPlacementSlot >= 0
            && (next == null || activeReturnWaypoint == null || !samePosition(next, activeReturnWaypoint)))
            abandonAttempt(mc);
        if (!returning || mc.thePlayer == null || !mc.thePlayer.onGround || next == null) {
            abandonAttempt(mc);
            return false;
        }
        int playerX = (int) Math.floor(mc.thePlayer.posX);
        int playerY = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int playerZ = (int) Math.floor(mc.thePlayer.posZ);
        if (next[1] <= playerY || next[1] > playerY + 2
            || Math.abs(next[0] - playerX) > 1
            || Math.abs(next[2] - playerZ) > 1
            || Math.abs(next[0] - playerX) + Math.abs(next[2] - playerZ) == 0
            || Math.hypot(mc.thePlayer.posX - (next[0] + 0.5D), mc.thePlayer.posZ - (next[2] + 0.5D)) > 1.8D) {
            abandonAttempt(mc);
            return false;
        }
        if (next[0] != playerX && next[2] != playerZ
            && (!safe(mc.theWorld, next[0], playerY, playerZ) || !safe(mc.theWorld, playerX, playerY, next[2]))) {
            abandonAttempt(mc);
            return false;
        }
        boolean needsIntermediateStep = next[1] == playerY + 2;
        int x = next[0], y = needsIntermediateStep ? playerY : next[1] - 1, z = next[2];
        net.minecraft.block.Block targetBlock = mc.theWorld.getBlock(x, y, z);
        if (!targetBlock.isReplaceable(mc.theWorld, x, y, z)) {
            abandonAttempt(mc);
            return false;
        }
        if (activeReturnWaypoint == null) activeReturnWaypoint = next.clone();
        int slot = findPlaceableBlock(mc, true);
        if (slot < 0) {
            int inventorySlot = findPlaceableBlock(mc, false);
            if (inventorySlot >= 9 && toolTransfer.start(mc, inventorySlot)) {
                if (returnPlacementSlot < 0) returnPlacementSlot = mc.thePlayer.inventory.currentItem;
                status = "Opening inventory to prepare a return step block";
                return true;
            }
            status = "Return step needs cobblestone or dirt";
            abandonAttempt(mc);
            return false;
        }
        if (returnPlacementSlot < 0) returnPlacementSlot = mc.thePlayer.inventory.currentItem;
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.updateController();
            placementAim.reset();
            status = "Preparing return step";
            return true;
        }
        if (mc.theWorld.getBlock(x, y, z) != Blocks.air && !targetBlock.isReplaceable(mc.theWorld, x, y, z)) {
            abandonAttempt(mc);
            return false;
        }
        // Prefer a support block directly below the placement target.
        int[][] adjacent = { { x, y - 1, z, 1 }, { x - 1, y, z, 5 }, { x + 1, y, z, 4 }, { x, y, z - 1, 3 },
            { x, y, z + 1, 2 } };
        if (++returnPlacementAimTicks > 100) {
            returnPlacementAimTicks = 0;
            restoreReturnPlacementSlot(mc);
            return false;
        }
        for (int sideIndex = 0; sideIndex < adjacent.length; sideIndex++) {
            int[] side = adjacent[(sideIndex + (returnPlacementAimTicks - 1) / 20) % adjacent.length];
            net.minecraft.block.Block support = mc.theWorld.getBlock(side[0], side[1], side[2]);
            if (support == Blocks.air || !support.getMaterial()
                .isSolid() || mc.theWorld.getTileEntity(side[0], side[1], side[2]) != null) continue;
            double hx = side[0] + 0.5D + (x - side[0]) * 0.49D;
            double hy = side[1] + 0.5D + (y - side[1]) * 0.49D;
            double hz = side[2] + 0.5D + (z - side[2]) * 0.49D;
            net.minecraft.util.Vec3 eye = net.minecraft.util.Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
            net.minecraft.util.MovingObjectPosition visible = mc.theWorld
                .rayTraceBlocks(eye, net.minecraft.util.Vec3.createVectorHelper(hx, hy, hz));
            if (visible == null || visible.blockX != side[0]
                || visible.blockY != side[1]
                || visible.blockZ != side[2]
                || visible.sideHit != side[3]
                || eye.squareDistanceTo(visible.hitVec) > 16D) continue;
            net.minecraft.util.Vec3 hit = visible.hitVec;
            hx = hit.xCoord;
            hy = hit.yCoord;
            hz = hit.zCoord;
            double dx = hx - mc.thePlayer.posX, dz = hz - mc.thePlayer.posZ;
            double dy = hy - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            float yaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
            float pitch = (float) (-Math.atan2(dy, Math.hypot(dx, dz)) * 180D / Math.PI);
            camera.look(yaw, pitch);
            net.minecraft.util.MovingObjectPosition aimed = mc.objectMouseOver;
            boolean matches = aimed != null
                && aimed.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
                && aimed.blockX == side[0]
                && aimed.blockY == side[1]
                && aimed.blockZ == side[2]
                && aimed.sideHit == side[3];
            if (!placementAim.ready(
                side[0],
                side[1],
                side[2],
                matches,
                Math.sqrt(eye.squareDistanceTo(hit)),
                Math.min(4D, mc.playerController.getBlockReachDistance()),
                net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw),
                pitch - mc.thePlayer.rotationPitch)) {
                status = "Aiming at return step support";
                return true;
            }
            if (mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                side[0],
                side[1],
                side[2],
                side[3],
                aimed.hitVec)) {
                pendingReturnPlacement = new int[] { x, y, z };
                returnPlacementAimTicks = 0;
                placementAim.reset();
                pendingReturnWaypoint = needsIntermediateStep ? new int[] { next[0], playerY + 1, next[2] } : null;
                returnPlacementTicks = 0;
                status = "Placing a checked block for the return step";
                return true;
            }
        }
        abandonAttempt(mc);
        return false;
    }

    private void restoreReturnPlacementSlot(Minecraft mc) {
        placementAim.reset();
        if (returnPlacementSlot >= 0) {
            mc.thePlayer.inventory.currentItem = returnPlacementSlot;
            mc.playerController.updateController();
        }
        returnPlacementSlot = -1;
        activeReturnWaypoint = null;
        returnPlacementAimTicks = 0;
    }

    private void abandonAttempt(Minecraft mc) {
        pendingReturnPlacement = null;
        pendingReturnWaypoint = null;
        returnPlacementTicks = 0;
        restoreReturnPlacementSlot(mc);
    }

    private int findPlaceableBlock(Minecraft mc, boolean hotbarOnly) {
        int limit = hotbarOnly ? 9 : mc.thePlayer.inventory.mainInventory.length;
        int fallback = -1;
        for (int i = hotbarOnly ? 0 : 9; i < limit; i++) {
            net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof net.minecraft.item.ItemBlock)) continue;
            net.minecraft.block.Block block = ((net.minecraft.item.ItemBlock) stack.getItem()).field_150939_a;
            if (block == null || block instanceof net.minecraft.block.BlockFalling
                || block.getMaterial()
                    .isLiquid()
                || block.hasTileEntity(0)
                || !block.getMaterial()
                    .isSolid()
                || !block.isNormalCube()) continue;
            if (block == Blocks.cobblestone) return i;
            if (block == Blocks.dirt) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }
}
