package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Moves ordinary player stacks only into a specifically identified open inventory. */
final class VerifiedStorageTransfer {

    enum Result {
        WORKING,
        DONE,
        FULL_OR_REJECTED,
        FAILED
    }

    private final Container container;
    private final IInventory destination;
    private final StorageLootLedger ledger;
    private final InventoryBaseline baseline;
    private final int window;
    private final boolean[] rejected = new boolean[36];
    private ItemStack pending;
    private int beforePlayer, beforeDestination, pendingPlayerSlot = -1, wait, moved;

    int moved() {
        return moved;
    }

    VerifiedStorageTransfer(Container container, IInventory destination, StorageLootLedger ledger,
        InventoryBaseline baseline) {
        this.container = container;
        this.destination = destination;
        this.ledger = ledger;
        this.baseline = baseline;
        this.window = container.windowId;
    }

    Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || !(mc.currentScreen instanceof GuiContainer)
            || mc.thePlayer.openContainer != container
            || container.windowId != window
            || mc.thePlayer.inventory.getItemStack() != null
            || destination == mc.thePlayer.inventory) return Result.FAILED;
        if (wait > 0) {
            wait--;
            return Result.WORKING;
        }
        if (pending != null) {
            int afterPlayer = count(mc.thePlayer.inventory, pending);
            int afterDestination = count(destination, pending);
            if (!StorageTransferPolicy.confirmed(beforePlayer, afterPlayer, beforeDestination, afterDestination)) {
                if (afterPlayer != beforePlayer || afterDestination != beforeDestination) return Result.FAILED;
                rejected[pendingPlayerSlot] = true;
            } else {
                int amount = beforePlayer - afterPlayer;
                moved += amount;
                ledger.deposited(pending, amount);
            }
            pending = null;
            pendingPlayerSlot = -1;
        }
        boolean remaining = false;
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            ItemStack stack = slot.getStack();
            if (slot.inventory != mc.thePlayer.inventory || index < 9
                || index >= 36
                || stack == null
                || InventoryProtection.bag(stack)
                || (InventoryProtection.essential(stack)
                    && (!ledger.contains(stack) || baseline == null || baseline.protects(index, stack)))
                || index == reserve) continue;
            remaining = true;
            if (rejected[index] || !slot.canTakeStack(mc.thePlayer) || !accepts(stack)) continue;
            pending = stack.copy();
            beforePlayer = count(mc.thePlayer.inventory, pending);
            beforeDestination = count(destination, pending);
            pendingPlayerSlot = index;
            if (!BaritoneInventoryBridge.quickMove(mc, slot.slotNumber)) {
                pending = null;
                return Result.FAILED;
            }
            wait = 5;
            return Result.WORKING;
        }
        return remaining ? Result.FULL_OR_REJECTED : Result.DONE;
    }

    private boolean accepts(ItemStack stack) {
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.inventory != destination || !slot.isItemValid(stack)) continue;
            ItemStack current = slot.getStack();
            if (current == null || (current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack)
                && current.stackSize < Math.min(current.getMaxStackSize(), slot.getSlotStackLimit()))) return true;
        }
        return false;
    }

    private static int count(IInventory inventory, ItemStack stack) {
        int result = 0;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack current = inventory.getStackInSlot(i);
            if (current != null && current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack))
                result += current.stackSize;
        }
        return result;
    }
}
