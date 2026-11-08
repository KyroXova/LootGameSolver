package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.oredict.OreDictionary;

final class TrashCleanup {

    private boolean opening, open;
    private int wait, timeout, retryDelay;
    private int pendingIndex = -1, pendingSize;
    private String status = "";
    private InventoryBaseline baseline;

    void setBaseline(InventoryBaseline value) {
        baseline = value;
    }

    String status() {
        return status;
    }

    boolean active() {
        return opening || open;
    }

    void advanceCooldown() {
        if (retryDelay > 0) retryDelay--;
    }

    boolean inventoryPressure(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return false;
        int empty = 0;
        for (int i = 0; i < 36; i++) if (mc.thePlayer.inventory.getStackInSlot(i) == null) empty++;
        return empty <= 8 && nextTrash(mc) >= 0;
    }

    void reset() {
        opening = false;
        open = false;
        wait = 0;
        timeout = 0;
        retryDelay = 0;
        pendingIndex = -1;
    }

    boolean shouldClean(Minecraft mc) {
        return retryDelay == 0 && mc.currentScreen == null && nextTrash(mc) >= 0;
    }

    boolean tick(Minecraft mc, boolean enabled) {
        if (mc.thePlayer == null || mc.theWorld == null) {
            reset();
            return false;
        }
        if (!opening && !open) {
            if (!enabled || retryDelay > 0
                || mc.currentScreen != null
                || mc.thePlayer.inventory.getItemStack() != null
                || nextTrash(mc) < 0) return false;
            mc.thePlayer.sendChatMessage("/trash_can");
            opening = true;
            timeout = 0;
            status = "Opening trash can";
            return true;
        }
        if (opening) {
            if (mc.currentScreen instanceof GuiChest && mc.thePlayer.openContainer instanceof ContainerChest) {
                IInventory inventory = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
                String name = inventory.getInventoryName();
                if (inventory.getSizeInventory() == 36 && ("key.serverutilities.trash".equals(name)
                    || StatCollector.translateToLocal("key.serverutilities.trash")
                        .equals(name))) {
                    opening = false;
                    open = true;
                    wait = 10;
                    return true;
                }
            }
            if (++timeout > 80 || mc.currentScreen != null) {
                opening = false;
                retryDelay = 1200;
                status = "Trash can unavailable; items kept";
                return false;
            }
            return true;
        }
        if (!(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
            open = false;
            retryDelay = 100;
            return false;
        }
        if (wait-- > 0) return true;
        if (pendingIndex >= 0) {
            ItemStack remaining = mc.thePlayer.inventory.getStackInSlot(pendingIndex);
            if (remaining != null && remaining.stackSize >= pendingSize) {
                mc.thePlayer.closeScreen();
                open = false;
                pendingIndex = -1;
                retryDelay = 1200;
                status = "Trash transfer failed; items kept";
                return true;
            }
            pendingIndex = -1;
        }
        int playerIndex = nextTrash(mc);
        if (playerIndex < 0) {
            mc.thePlayer.closeScreen();
            open = false;
            status = "Trash cleared";
            return true;
        }
        ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        for (Object obj : container.inventorySlots) {
            Slot slot = (Slot) obj;
            if (slot.inventory == mc.thePlayer.inventory && slot.getSlotIndex() == playerIndex
                && slot.canTakeStack(mc.thePlayer)) {
                pendingIndex = playerIndex;
                pendingSize = slot.getStack().stackSize;
                mc.playerController.windowClick(container.windowId, slot.slotNumber, 0, 1, mc.thePlayer);
                wait = 5;
                status = "Clearing unwanted items";
                return true;
            }
        }
        mc.thePlayer.closeScreen();
        open = false;
        retryDelay = 1200;
        status = "Trash transfer unavailable; items kept";
        return true;
    }

    private int nextTrash(Minecraft mc) {
        int reserve = -1, reserveSize = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || stack.hasDisplayName() || stack.hasTagCompound()) continue;
            String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
            if (TrashPolicy.buildingReserve(id) && stack.stackSize > reserveSize) {
                reserve = i;
                reserveSize = stack.stackSize;
            }
        }
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (i == reserve || stack == null
                || baseline != null && baseline.protects(i, stack)
                || InventoryProtection.essential(stack)
                || stack.hasDisplayName()
                || stack.hasTagCompound()) continue;
            String id = (String) Item.itemRegistry.getNameForObject(stack.getItem());
            boolean ore = false;
            for (int oreId : OreDictionary.getOreIDs(stack)) {
                if (OreDictionary.getOreName(oreId)
                    .startsWith("ore")) {
                    ore = true;
                    break;
                }
            }
            if (TrashPolicy.discard(id, stack.getItemDamage(), ore, stack.getItem() instanceof ItemBlock)) return i;
        }
        return -1;
    }
}
