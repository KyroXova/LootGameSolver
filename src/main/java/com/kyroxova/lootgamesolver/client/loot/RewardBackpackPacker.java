package com.kyroxova.lootgamesolver.client.loot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.kyroxova.lootgamesolver.client.BaritoneInventoryBridge;
import com.kyroxova.lootgamesolver.client.storage.InventoryBaseline;
import com.kyroxova.lootgamesolver.client.storage.InventoryProtection;
import com.kyroxova.lootgamesolver.client.storage.InventoryToolTransfer;
import com.kyroxova.lootgamesolver.client.storage.TrashCleanup;
import com.kyroxova.lootgamesolver.client.storage.TrashPolicy;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.ExplorerSustain;

public final class RewardBackpackPacker {

    public final boolean[] backpackTried = new boolean[36];
    public final boolean[] slotRejectedForCurrentBag = new boolean[36];
    public final List<ItemStack> itemRejectedForCurrentBag = new ArrayList<ItemStack>();
    public final InventoryToolTransfer backpackTransfer = new InventoryToolTransfer();

    public int backpackSlot = -1;
    public int preferredBagSlot = -1;
    public int packPendingSlot = -1;
    public int packPendingCount;
    public int packPendingBagCount;
    public ItemStack packPendingStack;
    public int backpackTransferAttempts;
    public int backpackAimTicks;
    public float backpackOldYaw;
    public float backpackOldPitch;
    public boolean backpackAimSaved;
    public boolean backpackAiming;
    public boolean packOpening;
    public boolean packMoved;
    public boolean temporaryBag;
    public boolean temporaryBagUsed;
    public boolean restoringBag;
    public int bagSource;
    public int bagHotbar;
    public ItemStack bagDisplaced;
    public int openWait;

    public void reset() {
        backpackSlot = -1;
        preferredBagSlot = -1;
        packPendingSlot = -1;
        packPendingStack = null;
        backpackTransferAttempts = 0;
        backpackAimTicks = 0;
        backpackAimSaved = false;
        backpackAiming = false;
        packOpening = false;
        packMoved = false;
        temporaryBag = false;
        temporaryBagUsed = false;
        restoringBag = false;
        bagDisplaced = null;
        openWait = 0;
        Arrays.fill(backpackTried, false);
        Arrays.fill(slotRejectedForCurrentBag, false);
        itemRejectedForCurrentBag.clear();
    }

    public void cancel(Minecraft mc, int oldSlot) {
        backpackTransfer.cancel(mc);
        if (temporaryBag && temporaryBagUsed && mc.thePlayer != null && !restoringBag) {
            backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced);
        }
        if (mc.thePlayer != null && oldSlot >= 0) mc.thePlayer.inventory.currentItem = oldSlot;
        restoreBackpackAim(mc, null);
    }

    public boolean handleTransfer(Minecraft mc, RewardChestLooter looter) {
        if (!backpackTransfer.active()) return false;
        if (!backpackTransfer.tick(mc)) {
            if (bagSource >= 0 && bagSource < backpackTried.length) backpackTried[bagSource] = true;
            if (bagHotbar >= 0 && bagHotbar < backpackTried.length) backpackTried[bagHotbar] = true;
            temporaryBag = false;
            temporaryBagUsed = false;
            restoringBag = false;
            backpackSlot = -1;
            looter.waiting = 10;
            looter.status = "Trying another backpack";
        } else if (!backpackTransfer.active() && restoringBag) {
            temporaryBag = false;
            temporaryBagUsed = false;
            restoringBag = false;
            backpackSlot = -1;
        }
        return true;
    }

    public boolean handleHotbarRestore(Minecraft mc, RewardChestLooter looter) {
        boolean structureFinished = (!looter.packing && !looter.collecting && looter.index >= looter.chests.size())
            || (looter.packingOnly && !looter.packing);
        if (temporaryBag && temporaryBagUsed && !packOpening && mc.currentScreen == null && structureFinished) {
            if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                restoringBag = true;
                looter.status = "Restoring hotbar";
            } else {
                looter.status = "Hotbar changed; bag kept";
                temporaryBag = false;
                temporaryBagUsed = false;
                restoringBag = false;
                backpackSlot = -1;
            }
            return true;
        }
        return false;
    }

    public void tick(Minecraft mc, Explorer exploration, RewardChestLooter looter) {
        if (looter.waiting > 0) {
            looter.waiting--;
            return;
        }
        if (backpackAiming) {
            if (mc.currentScreen != null || backpackSlot < 0
                || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
                backpackAiming = false;
                restoreBackpackAim(mc, exploration);
                return;
            }
            if (!RewardChestLooter.turnToward(mc, exploration, backpackOldYaw, -90F)) {
                backpackAimTicks = 0;
                looter.status = "Facing clear space";
                return;
            }
            if (++backpackAimTicks < 2) return;
            if (!BaritoneInventoryBridge.useHotbarItem(mc, backpackSlot)) {
                backpackAiming = false;
                restoreBackpackAim(mc, exploration);
                looter.status = "Backpack interaction unavailable";
                return;
            }
            if (temporaryBag && backpackSlot == bagHotbar) temporaryBagUsed = true;
            backpackAiming = false;
            restoreBackpackAim(mc, exploration);
            packOpening = true;
            Arrays.fill(slotRejectedForCurrentBag, false);
            itemRejectedForCurrentBag.clear();
            openWait = 0;
            looter.waiting = 5;
            return;
        }
        Container container = mc.thePlayer.openContainer;
        String name = container.getClass()
            .getName();
        int nonPlayerSlots = 0;
        for (Object obj : container.inventorySlots) {
            Slot s = (Slot) obj;
            if (s.inventory != mc.thePlayer.inventory) {
                nonPlayerSlots++;
            }
        }
        boolean isBackpack = InventoryProtection.backpackContainer(name) && nonPlayerSlots >= 4
            && backpackSlot >= 0
            && isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot));
        if (packOpening && mc.currentScreen instanceof GuiContainer && !isBackpack) {
            mc.thePlayer.closeScreen();
            restoreBackpackAim(mc, null);
            packOpening = false;
            if (looter.oldSlot >= 0) mc.thePlayer.inventory.currentItem = looter.oldSlot;
            looter.oldSlot = -1;
            int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
            if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
            if (temporaryBag && bagHotbar >= 0 && bagHotbar < backpackTried.length) backpackTried[bagHotbar] = true;
            if (temporaryBag && mc.thePlayer != null && !restoringBag) {
                backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced);
                restoringBag = true;
            }
            temporaryBag = false;
            temporaryBagUsed = false;
            backpackSlot = -1;
            openWait = 0;
            looter.waiting = 5;
            looter.status = "Unexpected container; trying another backpack";
            return;
        }
        if (packOpening && mc.currentScreen instanceof GuiContainer && isBackpack) {
            if (packPendingSlot >= 0) {
                Slot pending = packPendingSlot < container.inventorySlots.size() ? container.getSlot(packPendingSlot)
                    : null;
                ItemStack remaining = pending == null ? null : pending.getStack();
                int bagCount = backpackItemCount(container, mc, packPendingStack);
                boolean sourceChanged = remaining == null || !remaining.isItemEqual(packPendingStack)
                    || !ItemStack.areItemStackTagsEqual(remaining, packPendingStack)
                    || remaining.stackSize < packPendingCount;
                if (sourceChanged && bagCount > packPendingBagCount) {
                    packMoved = true;
                } else if (sourceChanged) {
                    itemRejectedForCurrentBag.add(packPendingStack.copy());
                } else {
                    if (pending != null && pending.inventory == mc.thePlayer.inventory) {
                        int playerSlot = pending.getSlotIndex();
                        if (playerSlot >= 0 && playerSlot < slotRejectedForCurrentBag.length) {
                            slotRejectedForCurrentBag[playerSlot] = true;
                        }
                    }
                }
                packPendingSlot = -1;
                packPendingStack = null;
            }
            for (Object obj : container.inventorySlots) {
                Slot slot = (Slot) obj;
                int playerSlot = slot.getSlotIndex();
                ItemStack stack = slot.getStack();
                if (slot.inventory != mc.thePlayer.inventory || playerSlot < 0
                    || playerSlot >= 36
                    || slotRejectedForCurrentBag[playerSlot]
                    || rejectedForBag(stack)
                    || !isPackable(mc, playerSlot, stack, looter.baseline, looter.minedDrops)) continue;
                if (!slot.canTakeStack(mc.thePlayer)) continue;
                if (!backpackHasSpace(container, mc, stack)) {
                    slotRejectedForCurrentBag[playerSlot] = true;
                    continue;
                }
                int beforeCount = stack.stackSize;
                packPendingStack = stack.copy();
                packPendingBagCount = backpackItemCount(container, mc, stack);
                if (!BaritoneInventoryBridge.quickMove(mc, slot.slotNumber)) {
                    looter.status = "Backpack transfer unavailable";
                    return;
                }
                packPendingSlot = slot.slotNumber;
                packPendingCount = beforeCount;
                looter.waiting = 5;
                return;
            }
            mc.thePlayer.closeScreen();
            restoreBackpackAim(mc, null);
            packOpening = false;
            if (looter.oldSlot >= 0) mc.thePlayer.inventory.currentItem = looter.oldSlot;
            looter.oldSlot = -1;
            int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
            if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
            if (temporaryBag && bagHotbar >= 0 && bagHotbar < backpackTried.length) backpackTried[bagHotbar] = true;
            if (packMoved) {
                preferredBagSlot = temporaryBag ? bagHotbar : backpackSlot;
            } else if (preferredBagSlot == attemptedSlot || (temporaryBag && preferredBagSlot == bagHotbar)) {
                preferredBagSlot = -1;
            }
            backpackSlot = -1;
            looter.waiting = 5;
            openWait = 0;
            if (looter.collecting && looter.queueIndex < looter.queue.size() && packMoved) {
                looter.packing = false;
                packMoved = false;
                looter.status = "Resuming reward collection";
            } else if (!hasPackableItems(mc, looter.baseline, looter.minedDrops)) {
                looter.packing = false;
                packMoved = false;
            }
            return;
        }
        if (packOpening) {
            if (++openWait > 40) {
                restoreBackpackAim(mc, null);
                if (looter.oldSlot >= 0) mc.thePlayer.inventory.currentItem = looter.oldSlot;
                looter.oldSlot = -1;
                int attemptedSlot = temporaryBag ? bagSource : backpackSlot;
                if (attemptedSlot >= 0 && attemptedSlot < backpackTried.length) backpackTried[attemptedSlot] = true;
                if (temporaryBag && bagHotbar >= 0 && bagHotbar < backpackTried.length) backpackTried[bagHotbar] = true;
                if (temporaryBag && mc.thePlayer != null && !restoringBag) {
                    backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced);
                    restoringBag = true;
                }
                temporaryBag = false;
                temporaryBagUsed = false;
                backpackSlot = -1;
                packOpening = false;
                openWait = 0;
                looter.waiting = 5;
            }
            return;
        }
        restoreBackpackAim(mc, null);
        if (mc.currentScreen != null) {
            looter.status = "Close the open screen to use a backpack";
            return;
        }
        if (backpackSlot < 0 || !isBackpack(mc.thePlayer.inventory.getStackInSlot(backpackSlot))) {
            backpackSlot = -1;
            if (temporaryBag && !backpackTried[bagSource]
                && isBackpack(mc.thePlayer.inventory.getStackInSlot(bagHotbar))) backpackSlot = bagHotbar;
            if (backpackSlot < 0 && preferredBagSlot >= 0
                && preferredBagSlot < 36
                && !backpackTried[preferredBagSlot]
                && isBackpack(mc.thePlayer.inventory.getStackInSlot(preferredBagSlot))) backpackSlot = preferredBagSlot;
            for (int i = 0; backpackSlot < 0 && i < 36; i++) {
                if (!backpackTried[i] && isBackpack(mc.thePlayer.inventory.getStackInSlot(i))) {
                    backpackSlot = i;
                    break;
                }
            }
        }
        if (backpackSlot < 0) {
            if (temporaryBag && temporaryBagUsed && !restoringBag) {
                if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                    restoringBag = true;
                    looter.status = "Restoring hotbar";
                    return;
                }
                looter.status = "Hotbar changed; bag kept";
                temporaryBag = false;
                temporaryBagUsed = false;
            }
            if (looter.packingOnly && !hasPackableItems(mc, looter.baseline, looter.minedDrops))
                looter.status = "Inventory packing finished";
            else if (looter.packingOnly) looter.status = "No available backpack; items kept";
            else if (!looter.collecting && looter.index >= looter.chests.size()
                && !hasPackableItems(mc, looter.baseline, looter.minedDrops)) looter.status = "Reward chests collected";
            else if (mc.thePlayer.inventory.getFirstEmptyStack() >= 0) {
                looter.packing = false;
                looter.status = "Resuming reward collection";
                return;
            } else {
                looter.status = "Inventory and backpacks full; rewards remain in chest";
            }
            if ((hasPackableItems(mc, looter.baseline, looter.minedDrops) || looter.collecting)
                && mc.thePlayer.inventory.getFirstEmptyStack() < 0) {
                boolean saved = looter.center == null || looter.saveCheckpoint(mc) != null;
                mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] Inventory or backpacks are full or unavailable. Please make space."
                            + (looter.collecting ? " Uncollected rewards remain in the chest." : "")
                            + (saved ? "" : " Could not save the loot checkpoint.")));
            }
            looter.packing = false;
            looter.active = false;
            return;
        }
        if (backpackSlot >= 9) {
            int selected = backpackSlot;
            boolean hotbarFull = true;
            for (int i = 0; i < 9; i++) {
                if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
                    hotbarFull = false;
                    break;
                }
            }
            if (hotbarFull && temporaryBag && temporaryBagUsed && bagHotbar >= 0 && !restoringBag) {
                if (backpackTransfer.restore(mc, bagSource, bagHotbar, bagDisplaced)) {
                    restoringBag = true;
                    looter.status = "Making space in hotbar";
                    return;
                }
            }
            backpackSlot = -1;
            if (backpackTransfer.start(mc, selected, looter.baseline)) {
                backpackTransferAttempts = 0;
                bagSource = selected;
                bagHotbar = backpackTransfer.hotbarSlot();
                ItemStack displaced = mc.thePlayer.inventory.getStackInSlot(bagHotbar);
                bagDisplaced = displaced == null ? null : displaced.copy();
                temporaryBag = true;
                temporaryBagUsed = false;
                return;
            }
            if (++backpackTransferAttempts >= 20) {
                backpackTried[selected] = true;
                backpackTransferAttempts = 0;
                looter.status = "Backpack unavailable";
            }
            looter.waiting = 2;
            return;
        }
        if (looter.oldSlot < 0) looter.oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = backpackSlot;
        mc.playerController.updateController();
        packMoved = false;
        if (!BaritoneInventoryBridge.useHotbarItem(mc, backpackSlot)) {
            looter.status = "Backpack interaction unavailable";
            looter.waiting = 5;
            return;
        }
        if (temporaryBag && backpackSlot == bagHotbar) temporaryBagUsed = true;
        packOpening = true;
        Arrays.fill(slotRejectedForCurrentBag, false);
        itemRejectedForCurrentBag.clear();
        openWait = 0;
        looter.waiting = 5;
    }

    public void restoreBackpackAim(Minecraft mc, Explorer exploration) {
        if (!backpackAimSaved || mc.thePlayer == null) return;
        backpackAimSaved = false;
        if (exploration != null) exploration.clearAim();
    }

    public static boolean isBackpack(ItemStack stack) {
        return InventoryProtection.bag(stack);
    }

    public static boolean backpackHasSpace(Container container, Minecraft mc, ItemStack stack) {
        for (Object obj : container.inventorySlots) {
            Slot slot = (Slot) obj;
            if (slot.inventory == mc.thePlayer.inventory || !slot.isItemValid(stack)) continue;
            ItemStack existing = slot.getStack();
            if (existing == null) return true;
            if (existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack)
                && existing.stackSize < Math.min(existing.getMaxStackSize(), slot.getSlotStackLimit())) return true;
        }
        return false;
    }

    public boolean rejectedForBag(ItemStack stack) {
        if (stack == null) return false;
        for (ItemStack rejected : itemRejectedForCurrentBag) {
            if (rejected.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(rejected, stack)) return true;
        }
        return false;
    }

    public static int backpackItemCount(Container container, Minecraft mc, ItemStack stack) {
        if (stack == null) return 0;
        int count = 0;
        for (Object obj : container.inventorySlots) {
            Slot slot = (Slot) obj;
            ItemStack existing = slot.getStack();
            if (slot.inventory != mc.thePlayer.inventory && existing != null
                && existing.isItemEqual(stack)
                && ItemStack.areItemStackTagsEqual(existing, stack)) count += existing.stackSize;
        }
        return count;
    }

    public boolean isPackable(Minecraft mc, int slotIndex, ItemStack stack, InventoryBaseline baseline,
        MinedDropTracker minedDrops) {
        if (stack == null || stack.stackSize <= 0) return false;
        if (temporaryBag && slotIndex == bagSource) return false;
        if (InventoryProtection.bag(stack)) return false;
        if (ExplorerSustain.isHealingAxe(stack)) return false;
        boolean healingAxe = false;
        for (int i = 0; i < 36; i++) {
            if (ExplorerSustain.isHealingAxe(mc.thePlayer.inventory.getStackInSlot(i))) {
                healingAxe = true;
                break;
            }
        }
        if (InventoryProtection.essentialForPacking(stack, baseline, slotIndex, healingAxe)) return false;
        if (isReservedBuildingStack(mc, slotIndex, stack)) return false;
        String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
        if (TrashPolicy.isBrokenBlockOrTrash(stack, id, stack.getItemDamage())
            || (minedDrops != null && minedDrops.containsTracked(MinedDropTracker.inventory(mc), slotIndex)))
            return false;
        if (id != null && id.toLowerCase(java.util.Locale.ROOT)
            .contains("torch")) return false;
        return true;
    }

    public boolean hasPackableItems(Minecraft mc, InventoryBaseline baseline, MinedDropTracker minedDrops) {
        if (mc == null || mc.thePlayer == null) return false;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isPackable(mc, i, stack, baseline, minedDrops)) return true;
        }
        return false;
    }

    public static boolean isReservedBuildingStack(Minecraft mc, int slot, ItemStack stack) {
        if (mc == null || stack == null) return false;
        return slot == TrashCleanup.findBuildingReserveSlot(mc);
    }
}
