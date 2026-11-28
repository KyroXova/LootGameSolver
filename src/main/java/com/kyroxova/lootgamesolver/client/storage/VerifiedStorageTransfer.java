package com.kyroxova.lootgamesolver.client.storage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/** Moves ordinary player stacks only into a specifically identified open inventory. */
public final class VerifiedStorageTransfer {

    public enum Result {
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
    private final BinnieCompartmentAdapter binnie;
    private final boolean[] rejected = new boolean[36];
    private final List<ItemStack> rejectedTypes = new ArrayList<ItemStack>();
    private ItemStack pending;
    private VerifiedContainerClick click;
    private int beforePlayer, beforeDestination, pendingPlayerSlot = -1, moved;

    public int moved() {
        return moved;
    }

    public BinnieCompartmentAdapter binnieAdapter() {
        return binnie;
    }

    public List<ItemStack> rejectedTypes() {
        List<ItemStack> result = new ArrayList<ItemStack>();
        for (ItemStack stack : rejectedTypes) result.add(stack.copy());
        return result;
    }

    public VerifiedStorageTransfer(Container container, IInventory destination, StorageLootLedger ledger,
        InventoryBaseline baseline) {
        this.container = container;
        this.destination = destination;
        this.ledger = ledger;
        this.baseline = baseline;
        this.window = container.windowId;
        this.binnie = BinnieCompartmentAdapter.detect(container);
    }

    public Result tick(Minecraft mc) {
        if (mc == null || mc.thePlayer == null
            || !(mc.currentScreen instanceof GuiContainer)
            || mc.thePlayer.openContainer != container
            || container.windowId != window
            || mc.thePlayer.inventory.getItemStack() != null
            || destination == mc.thePlayer.inventory) return Result.FAILED;
        if (binnie != null && binnie.isSyncing()) return Result.WORKING;
        if (pending != null) {
            Result pendingResult = handlePendingClick(mc);
            if (pendingResult != null) return pendingResult;
        }
        Slot selected = findNextTransferSlot(mc);
        if (selected != null) {
            pending = selected.getStack()
                .copy();
            beforePlayer = count(mc.thePlayer.inventory, pending);
            beforeDestination = count(destination, pending);
            pendingPlayerSlot = selected.getSlotIndex();
            click = VerifiedContainerClick.start(mc, selected.slotNumber, true);
            if (click == null) {
                pending = null;
                return Result.FAILED;
            }
            return Result.WORKING;
        }
        if (hasAnyMovableRemaining(mc)) {
            if (binnie != null && binnie.canSwitchToNextTab() && binnie.switchToNextTab(mc)) {
                java.util.Arrays.fill(rejected, false);
                rejectedTypes.clear();
                return Result.WORKING;
            }
            return Result.FULL_OR_REJECTED;
        }
        return Result.DONE;
    }

    private Result handlePendingClick(Minecraft mc) {
        VerifiedContainerClick.Result reply = click.tick();
        if (reply == VerifiedContainerClick.Result.WAITING) return Result.WORKING;
        if (reply == VerifiedContainerClick.Result.FAILED) return Result.FAILED;
        click = null;
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
        return null;
    }

    private Slot findNextTransferSlot(Minecraft mc) {
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        Slot selected = null;
        for (Object value : container.inventorySlots) {
            if (selected != null) break;
            Slot slot = (Slot) value;
            int index = slot.getSlotIndex();
            ItemStack stack = slot.getStack();
            if (slot.inventory != mc.thePlayer.inventory || !movable(index, stack, reserve, ledger, baseline)) continue;
            if (rejected[index] || !slot.canTakeStack(mc.thePlayer) || !accepts(stack)) {
                addRejectedType(stack);
                continue;
            }
            selected = slot;
        }
        return selected;
    }

    private boolean hasAnyMovableRemaining(Minecraft mc) {
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.inventory == mc.thePlayer.inventory
                && movable(slot.getSlotIndex(), slot.getStack(), reserve, ledger, baseline)) return true;
        }
        return false;
    }

    public void cancel() {
        if (click != null) click.close();
    }

    public static boolean hasMovable(Minecraft mc, StorageLootLedger ledger, InventoryBaseline baseline) {
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        for (int index = 9; index < 36; index++)
            if (movable(index, mc.thePlayer.inventory.getStackInSlot(index), reserve, ledger, baseline)) return true;
        return false;
    }

    public static boolean movable(int index, ItemStack stack, int reserve, StorageLootLedger ledger,
        InventoryBaseline baseline) {
        if (index < 9 || index >= 36 || stack == null || index == reserve || InventoryProtection.bag(stack))
            return false;
        return !InventoryProtection.essential(stack)
            || (ledger.contains(stack) && baseline != null && !baseline.protects(index, stack));
    }

    private boolean accepts(ItemStack stack) {
        for (Object value : container.inventorySlots) {
            Slot slot = (Slot) value;
            if (slot.inventory != destination || !slot.isItemValid(stack)) continue;
            if (binnie != null && !binnie.isSlotOnCurrentTab(slot.getSlotIndex())) continue;
            ItemStack current = slot.getStack();
            if (current == null || (current.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(current, stack)
                && current.stackSize < Math.min(current.getMaxStackSize(), slot.getSlotStackLimit()))) return true;
        }
        return false;
    }

    private void addRejectedType(ItemStack stack) {
        if (containsItemType(rejectedTypes, stack)) return;
        ItemStack type = stack.copy();
        type.stackSize = 1;
        rejectedTypes.add(type);
    }

    static boolean containsItemType(List<ItemStack> types, ItemStack stack) {
        if (stack == null) return false;
        for (ItemStack type : types)
            if (type.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(type, stack)) return true;
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
