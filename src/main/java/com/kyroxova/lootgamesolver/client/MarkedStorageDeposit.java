package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.inventory.Slot;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.minecraft.BaritoneBlockBridge;

/** Opens marked, reachable inventories and verifies each deposit before continuing. */
final class MarkedStorageDeposit {

    enum Result {
        WORKING,
        DONE,
        FULL_OR_UNAVAILABLE,
        FAILED
    }

    private final String[] targets;
    private final World world;
    private final StorageLootLedger ledger;
    private final InventoryBaseline baseline;
    private int next, wait, oldSlot = -1, moved;
    private StorageTarget opening;
    private IInventory inventory;
    private VerifiedStorageTransfer transfer;

    MarkedStorageDeposit(Minecraft mc, String[] targets, StorageLootLedger ledger, InventoryBaseline baseline) {
        world = mc.theWorld;
        this.targets = targets.clone();
        this.ledger = ledger;
        this.baseline = baseline;
    }

    Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld != world
            || mc.playerController == null
            || mc.thePlayer.inventory.getItemStack() != null
            || mc.thePlayer.hurtTime > 0) return Result.FAILED;
        if (transfer != null) {
            VerifiedStorageTransfer.Result result = transfer.tick(mc);
            if (result == VerifiedStorageTransfer.Result.WORKING) return Result.WORKING;
            if (result == VerifiedStorageTransfer.Result.FAILED) return Result.FAILED;
            moved += transfer.moved();
            mc.thePlayer.closeScreen();
            transfer = null;
            inventory = null;
            if (result == VerifiedStorageTransfer.Result.DONE) return Result.DONE;
            return Result.WORKING;
        }
        if (opening != null) {
            if (mc.currentScreen instanceof GuiContainer) {
                IInventory opened = inventory;
                if (mc.thePlayer.openContainer instanceof ContainerChest) {
                    IInventory chest = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
                    if (chest instanceof InventoryLargeChest
                        && ((InventoryLargeChest) chest).isPartOfLargeChest(inventory)) opened = chest;
                }
                boolean matching = false;
                for (Object value : mc.thePlayer.openContainer.inventorySlots)
                    if (((Slot) value).inventory == opened) matching = true;
                if (!matching || opening.loadedInventory(mc) != inventory) return Result.FAILED;
                transfer = new VerifiedStorageTransfer(mc.thePlayer.openContainer, opened, ledger, baseline);
                opening = null;
                restoreSlot(mc);
                return Result.WORKING;
            }
            if (mc.currentScreen != null) return Result.FAILED;
            if (++wait < 40) return Result.WORKING;
            opening = null;
            inventory = null;
            restoreSlot(mc);
        }
        if (mc.currentScreen != null || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
            return Result.FAILED;
        while (next < targets.length) {
            StorageTarget target = StorageTarget.parse(targets[next++]);
            if (target == null) continue;
            IInventory candidate = target.loadedInventory(mc);
            double[] pos = target.loadedPosition(mc);
            if (candidate == null || pos == null || mc.thePlayer.getDistanceSq(pos[0], pos[1], pos[2]) > 16D) continue;
            int empty = -1;
            for (int i = 0; i < 9; i++) if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                empty = i;
                break;
            }
            // A vanilla chest consumes the interaction even when its lid is obstructed.
            // Other storage blocks and entities may instead use or place the held item.
            if (empty < 0 && target.entity == null
                && world.getBlock(target.x, target.y, target.z) instanceof net.minecraft.block.BlockChest)
                empty = mc.thePlayer.inventory.currentItem;
            if (empty < 0) continue;
            Vec3 eye = Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
            Vec3 destination = Vec3.createVectorHelper(pos[0], pos[1], pos[2]);
            MovingObjectPosition hit = world.rayTraceBlocks(eye, destination);
            if (target.entity == null
                && (hit == null || hit.blockX != target.x || hit.blockY != target.y || hit.blockZ != target.z))
                continue;
            if (target.entity != null
                && (!(candidate instanceof Entity) || !mc.thePlayer.canEntityBeSeen((Entity) candidate))) continue;
            oldSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = empty;
            mc.playerController.updateController();
            boolean sent = target.entity == null
                ? BaritoneBlockBridge.click(mc, target.x, target.y, target.z, hit.sideHit, hit.hitVec, false)
                : mc.playerController.interactWithEntitySendPacket(mc.thePlayer, (Entity) candidate);
            if (!sent) {
                restoreSlot(mc);
                continue;
            }
            opening = target;
            inventory = candidate;
            wait = 0;
            return Result.WORKING;
        }
        return Result.FULL_OR_UNAVAILABLE;
    }

    void cancel(Minecraft mc) {
        if (mc != null && mc.thePlayer != null) restoreSlot(mc);
    }

    int moved() {
        return moved;
    }

    private void restoreSlot(Minecraft mc) {
        if (oldSlot >= 0) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            mc.playerController.updateController();
            oldSlot = -1;
        }
    }
}
