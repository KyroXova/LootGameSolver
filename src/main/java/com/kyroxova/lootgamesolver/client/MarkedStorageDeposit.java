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
    private StorageTarget approach;
    private final BaritoneNavigationBridge navigation = new BaritoneNavigationBridge();
    private int approachTicks, approachRetries, lastApproachIndex = -1;
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
        if (approach != null) {
            if (mc.currentScreen != null || ++approachTicks > 400
                || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
                || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
                || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
                || net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindRight))
                return Result.FAILED;
            if (navigation.state() == BaritoneNavigationBridge.State.RUNNING) return Result.WORKING;
            boolean reached = navigation.completed();
            navigation.stop();
            approach = null;
            if (reached && ++approachRetries <= 2) next--;
        }
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
                    else if (vanillaChestView(mc, opening, inventory, chest)) opened = chest;
                }
                boolean matching = false;
                for (Object value : mc.thePlayer.openContainer.inventorySlots) {
                    IInventory slotInventory = ((Slot) value).inventory;
                    if (slotInventory == opened) matching = true;
                    else if (slotInventory != mc.thePlayer.inventory) return Result.FAILED;
                }
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
            if (candidate == null || pos == null) continue;
            double distance = mc.thePlayer.getDistanceSq(pos[0], pos[1], pos[2]);
            if (distance > 16D) {
                if (distance > 64D) continue;
                if (approach(target, pos)) return Result.WORKING;
                continue;
            }
            int empty = -1;
            for (int i = 0; i < 9; i++) if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                empty = i;
                break;
            }
            // Forge calls onItemUseFirst before a chest receives the click. Only
            // ordinary vanilla items are safe if there is no empty hotbar slot.
            if (empty < 0 && target.entity == null
                && !mc.thePlayer.isSneaking()
                && world.getBlock(target.x, target.y, target.z) instanceof net.minecraft.block.BlockChest) {
                for (int i = 0; i < 9; i++) {
                    net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                    if (stack != null && (stack.getItem()
                        .getClass() == net.minecraft.item.Item.class
                        || stack.getItem()
                            .getClass() == net.minecraft.item.ItemBlock.class)) {
                        empty = i;
                        break;
                    }
                }
            }
            if (empty < 0) continue;
            Vec3 eye = Vec3.createVectorHelper(
                mc.thePlayer.posX,
                mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
                mc.thePlayer.posZ);
            Vec3 destination = Vec3.createVectorHelper(pos[0], pos[1], pos[2]);
            MovingObjectPosition hit = world.rayTraceBlocks(eye, destination);
            if (target.entity == null
                && (hit == null || hit.blockX != target.x || hit.blockY != target.y || hit.blockZ != target.z)) {
                if (approach(target, pos)) return Result.WORKING;
                continue;
            }
            if (target.entity != null
                && (!(candidate instanceof Entity) || !mc.thePlayer.canEntityBeSeen((Entity) candidate))) {
                if (approach(target, pos)) return Result.WORKING;
                continue;
            }
            oldSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = empty;
            mc.playerController.updateController();
            boolean sent;
            if (target.entity == null) {
                sent = BaritoneBlockBridge.click(mc, target.x, target.y, target.z, hit.sideHit, hit.hitVec, false);
            } else {
                // This method sends the packet even if the client-side interaction returns false.
                mc.playerController.interactWithEntitySendPacket(mc.thePlayer, (Entity) candidate);
                sent = true;
            }
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
        if (transfer != null) transfer.cancel();
        navigation.stop();
        if (mc != null && mc.thePlayer != null) restoreSlot(mc);
    }

    int moved() {
        return moved;
    }

    static boolean vanillaChestView(Minecraft mc, StorageTarget target, IInventory marked, IInventory opened) {
        if (!(opened instanceof net.minecraft.inventory.InventoryBasic) || target == null
            || mc == null
            || mc.theWorld == null) return false;
        if (marked instanceof net.minecraft.entity.item.EntityMinecartChest)
            return target.entity != null && opened.getSizeInventory() == marked.getSizeInventory()
                && java.util.Objects.equals(opened.getInventoryName(), marked.getInventoryName());
        if (!(marked instanceof net.minecraft.tileentity.TileEntityChest) || target.entity != null) return false;
        if (vanillaChestSizes(marked, opened, false))
            return java.util.Objects.equals(opened.getInventoryName(), marked.getInventoryName());
        if (!vanillaChestSizes(marked, opened, true)) return false;
        int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int[] offset : offsets) {
            int x = target.x + offset[0], z = target.z + offset[1];
            if (mc.theWorld.blockExists(x, target.y, z)
                && mc.theWorld.getBlock(x, target.y, z) == mc.theWorld.getBlock(target.x, target.y, target.z)
                && mc.theWorld.getTileEntity(x, target.y, z) instanceof net.minecraft.tileentity.TileEntityChest) {
                IInventory neighbor = (IInventory) mc.theWorld.getTileEntity(x, target.y, z);
                String name = opened.getInventoryName();
                if ((marked.hasCustomInventoryName() && java.util.Objects.equals(name, marked.getInventoryName()))
                    || (neighbor.hasCustomInventoryName()
                        && java.util.Objects.equals(name, neighbor.getInventoryName()))
                    || (!marked.hasCustomInventoryName() && !neighbor.hasCustomInventoryName()
                        && "container.chestDouble".equals(name)))
                    return true;
            }
        }
        return false;
    }

    static boolean vanillaChestSizes(IInventory marked, IInventory opened, boolean adjacent) {
        return marked instanceof net.minecraft.tileentity.TileEntityChest
            && opened instanceof net.minecraft.inventory.InventoryBasic
            && (opened.getSizeInventory() == marked.getSizeInventory()
                || (adjacent && opened.getSizeInventory() == marked.getSizeInventory() * 2));
    }

    private boolean approach(StorageTarget target, double[] pos) {
        if (lastApproachIndex != next - 1) {
            lastApproachIndex = next - 1;
            approachRetries = 0;
        }
        if (approachRetries > 2) return false;
        BaritoneNavigationBridge.Start started = navigation
            .startAdjacentWithoutBreaking((int) Math.floor(pos[0]), (int) Math.floor(pos[1]), (int) Math.floor(pos[2]));
        if (started != BaritoneNavigationBridge.Start.STARTED) return false;
        approach = target;
        approachTicks = 0;
        return true;
    }

    private void restoreSlot(Minecraft mc) {
        if (oldSlot >= 0) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            if (mc.playerController != null) mc.playerController.updateController();
            oldSlot = -1;
        }
    }
}
