package com.kyroxova.lootgamesolver.client.storage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Withdraws from one identified open backpack, checking every server-side transfer. */
public final class VerifiedBackpackUnload {

    public enum Result {
        WORKING,
        EMPTY,
        PLAYER_FULL,
        NEEDS_ATTENTION,
        FAILED
    }

    private final Container container;
    private final IInventory backpack;
    private final StorageLootLedger ledger;
    private final int window;
    private ItemStack pending;
    private ItemStack destinationBefore;
    private VerifiedContainerClick click;
    private int beforeBag, beforePlayer, beforeHotbar, destinationSlot, stage;

    public VerifiedBackpackUnload(Container container, IInventory backpack, StorageLootLedger ledger) {
        this.container = container;
        this.backpack = backpack;
        this.ledger = ledger;
        this.window = container.windowId;
    }

    public Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || !(mc.currentScreen instanceof GuiContainer)
            || mc.thePlayer.openContainer != container
            || container.windowId != window
            || (stage == 0 && mc.thePlayer.inventory.getItemStack() != null)
            || backpack == mc.thePlayer.inventory) return Result.FAILED;
        if (stage != 0) {
            VerifiedContainerClick.Result reply = click.tick();
            if (reply == VerifiedContainerClick.Result.WAITING) return Result.WORKING;
            if (reply == VerifiedContainerClick.Result.FAILED) return Result.FAILED;
            click = null;
        }
        if (stage == 1) {
            ItemStack cursor = mc.thePlayer.inventory.getItemStack();
            Slot destination = container.getSlot(destinationSlot);
            if (!ItemStack.areItemStacksEqual(cursor, pending)
                || !ItemStack.areItemStacksEqual(destinationBefore, destination.getStack())) return Result.FAILED;
            click = VerifiedContainerClick.start(mc, destinationSlot, false);
            if (click == null) return Result.FAILED;
            stage = 2;
            return Result.WORKING;
        }
        if (stage == 2) {
            int afterBag = count(backpack, pending);
            int afterPlayer = count(mc.thePlayer.inventory, pending);
            if (mc.thePlayer.inventory.getItemStack() != null || countHotbar(mc, pending) != beforeHotbar
                || !StorageTransferPolicy.confirmed(beforeBag, afterBag, beforePlayer, afterPlayer))
                return Result.FAILED;
            ledger.withdrawn(pending, beforeBag - afterBag);
            pending = null;
            destinationBefore = null;
            stage = 0;
        }
        boolean remaining = false;
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            ItemStack stack = slot.getStack();
            if (slot.inventory != backpack || index < 0 || index >= backpack.getSizeInventory() || stack == null)
                continue;
            remaining = true;
            if (InventoryProtection.bag(stack) || !slot.canTakeStack(mc.thePlayer)) continue;
            Slot destination = destination(container, mc.thePlayer.inventory, stack);
            if (destination == null) return Result.PLAYER_FULL;
            pending = stack.copy();
            beforeBag = count(backpack, pending);
            beforePlayer = count(mc.thePlayer.inventory, pending);
            beforeHotbar = countHotbar(mc, pending);
            destinationSlot = destination.slotNumber;
            destinationBefore = destination.getStack() == null ? null
                : destination.getStack()
                    .copy();
            click = VerifiedContainerClick.start(mc, slot.slotNumber, false);
            if (click == null) {
                pending = null;
                return Result.FAILED;
            }
            stage = 1;
            return Result.WORKING;
        }
        return remaining ? Result.NEEDS_ATTENTION : Result.EMPTY;
    }

    public void cancel() {
        if (click != null) click.close();
    }

    public static Slot destination(Container container, IInventory playerInventory, ItemStack stack) {
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            if (slot.inventory != playerInventory || index < 9 || index >= 36 || !slot.isItemValid(stack)) continue;
            ItemStack current = slot.getStack();
            int limit = Math.min(stack.getMaxStackSize(), slot.getSlotStackLimit());
            if (current == null && stack.stackSize <= limit) return slot;
            if (current != null && current.isItemEqual(stack)
                && ItemStack.areItemStackTagsEqual(current, stack)
                && current.stackSize + stack.stackSize <= limit) return slot;
        }
        return null;
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
