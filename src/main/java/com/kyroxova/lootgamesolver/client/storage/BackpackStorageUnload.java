package com.kyroxova.lootgamesolver.client.storage;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.kyroxova.lootgamesolver.client.BaritoneInventoryBridge;

/** Opens each carried backpack and withdraws its contents in verified batches. */
public final class BackpackStorageUnload {

    public enum Result {
        WORKING,
        NEED_DEPOSIT,
        DONE,
        FAILED
    }

    private enum Phase {
        FIND,
        SWAP,
        OPEN,
        WITHDRAW,
        RESTORE
    }

    private final boolean[] finished = new boolean[36];
    private final InventoryToolTransfer swap = new InventoryToolTransfer();
    private final InventoryBaseline baseline;
    private final StorageLootLedger ledger;
    private Phase phase = Phase.FIND;
    private VerifiedBackpackUnload withdraw;
    private int source = -1, hotbar = -1, oldSlot = -1, wait;
    private ItemStack displaced;
    private Result afterRestore;

    public BackpackStorageUnload(InventoryBaseline baseline, StorageLootLedger ledger) {
        this.baseline = baseline;
        this.ledger = ledger;
    }

    public Result tick(Minecraft mc) {
        if (mc == null || mc.theWorld == null
            || mc.thePlayer == null
            || mc.playerController == null
            || (phase != Phase.WITHDRAW && mc.thePlayer.inventory.getItemStack() != null)
            || mc.thePlayer.hurtTime > 0) return Result.FAILED;
        if (phase == Phase.SWAP || phase == Phase.RESTORE) {
            Result swapResult = tickSwapOrRestore(mc);
            if (swapResult != null) return swapResult;
        }
        if (phase == Phase.WITHDRAW) return tickWithdraw(mc);
        if (phase == Phase.OPEN) return tickOpen(mc);
        return startNextBackpack(mc);
    }

    private Result tickSwapOrRestore(Minecraft mc) {
        if (swap.active()) {
            if (!swap.tick(mc)) return Result.FAILED;
            if (swap.active()) return Result.WORKING;
        }
        if (phase == Phase.RESTORE) {
            restoreSelection(mc);
            phase = Phase.FIND;
            return afterRestore;
        }
        if (!InventoryProtection.bag(mc.thePlayer.inventory.getStackInSlot(hotbar))) return Result.FAILED;
        phase = Phase.OPEN;
        wait = 0;
        return null;
    }

    private Result tickWithdraw(Minecraft mc) {
        VerifiedBackpackUnload.Result result = withdraw.tick(mc);
        if (result == VerifiedBackpackUnload.Result.WORKING) return Result.WORKING;
        if (result == VerifiedBackpackUnload.Result.FAILED || result == VerifiedBackpackUnload.Result.NEEDS_ATTENTION)
            return Result.FAILED;
        mc.thePlayer.closeScreen();
        withdraw = null;
        if (result == VerifiedBackpackUnload.Result.EMPTY) finished[source] = true;
        return restore(mc, result == VerifiedBackpackUnload.Result.PLAYER_FULL ? Result.NEED_DEPOSIT : Result.WORKING);
    }

    private Result tickOpen(Minecraft mc) {
        if (mc.currentScreen instanceof GuiContainer) return openBackpackContainer(mc);
        if (mc.currentScreen != null || ++wait > 40) return Result.FAILED;
        if (wait > 1) return Result.WORKING;
        if (!InventoryProtection.bag(mc.thePlayer.inventory.getStackInSlot(hotbar))) return Result.FAILED;
        mc.thePlayer.inventory.currentItem = hotbar;
        mc.playerController.updateController();
        return BaritoneInventoryBridge.useHotbarItem(mc, hotbar) ? Result.WORKING : Result.FAILED;
    }

    private Result openBackpackContainer(Minecraft mc) {
        if (mc.currentScreen instanceof GuiChest || mc.thePlayer.openContainer instanceof ContainerChest)
            return Result.FAILED;
        String name = mc.thePlayer.openContainer.getClass()
            .getName()
            .toLowerCase(Locale.ROOT);
        if (!InventoryProtection.backpackContainer(name)) {
            mc.thePlayer.closeScreen();
            finished[source] = true;
            return restore(mc, Result.WORKING);
        }
        IInventory contents = resolveBackpackInventory(mc);
        if (contents == null) {
            mc.thePlayer.closeScreen();
            finished[source] = true;
            return restore(mc, Result.WORKING);
        }
        withdraw = new VerifiedBackpackUnload(mc.thePlayer.openContainer, contents, ledger);
        phase = Phase.WITHDRAW;
        return Result.WORKING;
    }

    private IInventory resolveBackpackInventory(Minecraft mc) {
        IInventory contents = null;
        int containerSlots = 0;
        for (Object value : mc.thePlayer.openContainer.inventorySlots) {
            IInventory candidate = ((Slot) value).inventory;
            if (candidate == mc.thePlayer.inventory) continue;
            containerSlots++;
            if (contents != null && contents != candidate) return null;
            contents = candidate;
        }
        return containerSlots >= 4 ? contents : null;
    }

    private Result startNextBackpack(Minecraft mc) {
        if (mc.currentScreen != null || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer)
            return Result.FAILED;
        source = -1;
        for (int i = 0; i < 36; i++) {
            if (!finished[i] && InventoryProtection.bag(mc.thePlayer.inventory.getStackInSlot(i))) {
                source = i;
                break;
            }
        }
        if (source < 0) return Result.DONE;
        oldSlot = mc.thePlayer.inventory.currentItem;
        if (source < 9) {
            hotbar = source;
            phase = Phase.OPEN;
            wait = 0;
            return Result.WORKING;
        }
        if (!swap.start(mc, source, baseline)) return Result.FAILED;
        hotbar = swap.hotbarSlot();
        ItemStack previous = mc.thePlayer.inventory.getStackInSlot(hotbar);
        displaced = previous == null ? null : previous.copy();
        phase = Phase.SWAP;
        return Result.WORKING;
    }

    private Result restore(Minecraft mc, Result result) {
        if (source < 9) {
            restoreSelection(mc);
            phase = Phase.FIND;
            return result;
        }
        if (!swap.restore(mc, source, hotbar, displaced)) return Result.FAILED;
        afterRestore = result;
        phase = Phase.RESTORE;
        return Result.WORKING;
    }

    public void cancel(Minecraft mc) {
        if (withdraw != null) withdraw.cancel();
        if (mc == null) return;
        swap.cancel(mc);
        if (mc != null && mc.thePlayer != null) restoreSelection(mc);
    }

    private void restoreSelection(Minecraft mc) {
        if (oldSlot >= 0) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            if (mc.playerController != null) mc.playerController.updateController();
            oldSlot = -1;
        }
    }
}
