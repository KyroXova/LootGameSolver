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
    private int beforeBag, beforePlayer, beforeHotbar, destinationSlot, sourceSlot, stage, moveAmount, movedAmount;

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
        if (stage == 1) return tickPickupStage(mc);
        if (stage == 2) return tickPlaceStage(mc);
        if (stage == 3) return tickFullReturnStage(mc);
        return startNextUnload(mc);
    }

    private Result tickPickupStage(Minecraft mc) {
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        if (!ItemStack.areItemStacksEqual(cursor, pending)
            || count(backpack, pending) != beforeBag - pending.stackSize
            || count(mc.thePlayer.inventory, pending) != beforePlayer
            || countHotbar(mc, pending) != beforeHotbar) return Result.FAILED;
        return placeNext(mc);
    }

    private Result tickPlaceStage(Minecraft mc) {
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        Slot destination = container.getSlot(destinationSlot);
        ItemStack placed = destination.getStack();
        int previousDestinationCount = destinationBefore == null ? 0 : destinationBefore.stackSize;
        if (placed == null || !sameType(placed, pending)
            || placed.stackSize != previousDestinationCount + moveAmount
            || count(mc.thePlayer.inventory, pending) != beforePlayer + movedAmount + moveAmount
            || countHotbar(mc, pending) != beforeHotbar
            || (cursor == null ? pending.stackSize != movedAmount + moveAmount
                : !sameType(cursor, pending) || cursor.stackSize != pending.stackSize - movedAmount - moveAmount))
            return Result.FAILED;
        if (cursor != null) {
            movedAmount += moveAmount;
            return placeNext(mc);
        }
        return completeTransfer(mc, pending.stackSize);
    }

    private Result tickFullReturnStage(Minecraft mc) {
        if (mc.thePlayer.inventory.getItemStack() != null || count(backpack, pending) != beforeBag - moveAmount
            || count(mc.thePlayer.inventory, pending) != beforePlayer + moveAmount
            || countHotbar(mc, pending) != beforeHotbar) return Result.FAILED;
        ledger.withdrawn(pending, moveAmount);
        clearPending();
        return Result.PLAYER_FULL;
    }

    private Result startNextUnload(Minecraft mc) {
        boolean remaining = false;
        Slot selected = null;
        for (Object value : container.inventorySlots) {
            if (selected != null) break;
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            ItemStack stack = slot.getStack();
            if (slot.inventory != backpack || index < 0 || index >= backpack.getSizeInventory() || stack == null)
                continue;
            remaining = true;
            if (InventoryProtection.bag(stack) || !slot.canTakeStack(mc.thePlayer)) continue;
            if (destination(container, mc.thePlayer.inventory, stack) == null) return Result.PLAYER_FULL;
            selected = slot;
        }
        if (selected == null) return remaining ? Result.NEEDS_ATTENTION : Result.EMPTY;
        pending = selected.getStack()
            .copy();
        beforeBag = count(backpack, pending);
        beforePlayer = count(mc.thePlayer.inventory, pending);
        beforeHotbar = countHotbar(mc, pending);
        movedAmount = 0;
        sourceSlot = selected.slotNumber;
        click = VerifiedContainerClick.start(mc, selected.slotNumber, false);
        if (click == null) {
            pending = null;
            return Result.FAILED;
        }
        stage = 1;
        return Result.WORKING;
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
            if (current == null && limit > 0) return slot;
            if (current != null && current.isItemEqual(stack)
                && ItemStack.areItemStackTagsEqual(current, stack)
                && current.stackSize < limit) return slot;
        }
        return null;
    }

    private Result placeNext(Minecraft mc) {
        ItemStack cursor = mc.thePlayer.inventory.getItemStack();
        Slot destination = cursor == null ? null : destination(container, mc.thePlayer.inventory, cursor);
        if (destination == null) {
            if (cursor == null) return Result.FAILED;
            Slot source = container.getSlot(sourceSlot);
            if (source.getStack() != null || !source.isItemValid(cursor)) return Result.FAILED;
            moveAmount = pending.stackSize - cursor.stackSize;
            click = VerifiedContainerClick.start(mc, sourceSlot, false);
            if (click == null) return Result.FAILED;
            stage = 3;
            return Result.WORKING;
        }
        destinationSlot = destination.slotNumber;
        ItemStack present = destination.getStack();
        int limit = Math.min(cursor.getMaxStackSize(), destination.getSlotStackLimit());
        moveAmount = Math.min(cursor.stackSize, present == null ? limit : limit - present.stackSize);
        if (moveAmount <= 0) return Result.FAILED;
        destinationBefore = present == null ? null : present.copy();
        click = VerifiedContainerClick.start(mc, destinationSlot, false);
        if (click == null) return Result.FAILED;
        stage = 2;
        return Result.WORKING;
    }

    private Result completeTransfer(Minecraft mc, int amount) {
        int afterBag = count(backpack, pending);
        int afterPlayer = count(mc.thePlayer.inventory, pending);
        if (mc.thePlayer.inventory.getItemStack() != null || countHotbar(mc, pending) != beforeHotbar
            || !StorageTransferPolicy.confirmed(beforeBag, afterBag, beforePlayer, afterPlayer)
            || beforeBag - afterBag != amount
            || afterPlayer - beforePlayer != amount) return Result.FAILED;
        ledger.withdrawn(pending, amount);
        clearPending();
        return Result.WORKING;
    }

    private void clearPending() {
        pending = null;
        destinationBefore = null;
        movedAmount = 0;
        stage = 0;
    }

    private static boolean sameType(ItemStack first, ItemStack second) {
        return first.isItemEqual(second) && ItemStack.areItemStackTagsEqual(first, second);
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
