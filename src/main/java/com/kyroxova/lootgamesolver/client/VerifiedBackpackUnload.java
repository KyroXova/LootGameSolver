package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Withdraws from one identified open backpack, checking every server-side transfer. */
final class VerifiedBackpackUnload {

    enum Result {
        WORKING,
        EMPTY,
        PLAYER_FULL,
        NEEDS_ATTENTION,
        FAILED
    }

    private final Container container;
    private final IInventory backpack;
    private final int window;
    private final boolean[] rejected;
    private ItemStack pending;
    private int beforeBag, beforePlayer, beforeHotbar, pendingSlot = -1, wait;

    VerifiedBackpackUnload(Container container, IInventory backpack) {
        this.container = container;
        this.backpack = backpack;
        this.window = container.windowId;
        this.rejected = new boolean[backpack.getSizeInventory()];
    }

    Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || !(mc.currentScreen instanceof GuiContainer)
            || mc.thePlayer.openContainer != container
            || container.windowId != window
            || mc.thePlayer.inventory.getItemStack() != null
            || backpack == mc.thePlayer.inventory) return Result.FAILED;
        if (wait > 0) {
            wait--;
            return Result.WORKING;
        }
        if (pending != null) {
            int afterBag = count(backpack, pending);
            int afterPlayer = count(mc.thePlayer.inventory, pending);
            if (countHotbar(mc, pending) != beforeHotbar) return Result.FAILED;
            if (!StorageTransferPolicy.confirmed(beforeBag, afterBag, beforePlayer, afterPlayer)) {
                if (afterBag != beforeBag || afterPlayer != beforePlayer) return Result.FAILED;
                rejected[pendingSlot] = true;
            }
            pending = null;
            pendingSlot = -1;
        }
        boolean remaining = false;
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            ItemStack stack = slot.getStack();
            if (slot.inventory != backpack || index < 0 || index >= rejected.length || stack == null) continue;
            remaining = true;
            if (InventoryProtection.bag(stack) || rejected[index] || !slot.canTakeStack(mc.thePlayer)) continue;
            if (!playerHasSpace(mc, stack)) return Result.PLAYER_FULL;
            pending = stack.copy();
            beforeBag = count(backpack, pending);
            beforePlayer = count(mc.thePlayer.inventory, pending);
            beforeHotbar = countHotbar(mc, pending);
            pendingSlot = index;
            if (!BaritoneInventoryBridge.quickMove(mc, slot.slotNumber)) {
                pending = null;
                return Result.FAILED;
            }
            wait = 5;
            return Result.WORKING;
        }
        return remaining ? Result.NEEDS_ATTENTION : Result.EMPTY;
    }

    private static boolean playerHasSpace(Minecraft mc, ItemStack stack) {
        // Marked storage deposits operate on the main inventory. Request a deposit
        // before withdrawing more when only hotbar slots remain available.
        for (int i = 9; i < 36; i++) {
            ItemStack current = mc.thePlayer.inventory.getStackInSlot(i);
            if (current == null || (current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack)
                && current.stackSize < current.getMaxStackSize())) return true;
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

    private static int countHotbar(Minecraft mc, ItemStack stack) {
        int result = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack current = mc.thePlayer.inventory.getStackInSlot(i);
            if (current != null && current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack))
                result += current.stackSize;
        }
        return result;
    }
}
