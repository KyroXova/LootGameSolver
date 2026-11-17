package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C16PacketClientStatus;

public final class InventoryToolTransfer {

    private static final class ToolScreen extends GuiInventory {

        ToolScreen(Minecraft mc) {
            super(mc.thePlayer);
        }

        boolean swap(Minecraft mc, int source, int hotbar) {
            Slot slot = inventorySlots.getSlotFromInventory(mc.thePlayer.inventory, source);
            return slot != null && slot.canTakeStack(mc.thePlayer)
                && BaritoneInventoryBridge.swapOpenContainer(mc, slot.slotNumber, hotbar);
        }
    }

    private ToolScreen screen;
    private ItemStack expected;
    private int source, hotbar, ticks;
    private boolean swapped;
    private boolean clearing;

    public boolean active() {
        return screen != null;
    }

    public boolean start(Minecraft mc, int inventorySlot) {
        return start(mc, inventorySlot, null);
    }

    public boolean start(Minecraft mc, int inventorySlot, InventoryBaseline baseline) {
        if (active() || mc.currentScreen != null
            || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null) return false;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(inventorySlot);
        if (stack == null) return false;
        expected = stack.copy();
        clearing = false;
        source = inventorySlot;
        hotbar = -1;
        for (int i = 0; i < 9; i++) if (mc.thePlayer.inventory.getStackInSlot(i) == null) {
            hotbar = i;
            break;
        }
        if (hotbar < 0) for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (!InventoryProtection.essential(s) && (baseline == null || !baseline.protects(i, s))) {
                hotbar = i;
                break;
            }
        }
        if (hotbar < 0) for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (!InventoryProtection.essential(s)) {
                hotbar = i;
                break;
            }
        }
        if (hotbar < 0) for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.getStackInSlot(i);
            if (!InventoryProtection.bag(s) && (s == null || s.getItem() == null
                || !s.getItem()
                    .getClass()
                    .getName()
                    .contains("ItemHealingAxe"))) {
                hotbar = i;
                break;
            }
        }
        if (hotbar < 0) return false;
        ticks = 0;
        swapped = false;
        screen = new ToolScreen(mc);
        mc.getNetHandler()
            .addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(screen);
        return true;
    }

    public int hotbarSlot() {
        return hotbar;
    }

    public boolean restore(Minecraft mc, int inventorySlot, int hotbarSlot, ItemStack displaced) {
        if (active() || mc.currentScreen != null
            || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null
            || inventorySlot < 9
            || inventorySlot >= 36
            || hotbarSlot < 0
            || hotbarSlot >= 9
            || !InventoryProtection.bag(mc.thePlayer.inventory.getStackInSlot(hotbarSlot))
            || !ItemStack.areItemStacksEqual(displaced, mc.thePlayer.inventory.getStackInSlot(inventorySlot)))
            return false;
        if (displaced == null) return clearHotbar(mc, hotbarSlot, inventorySlot);
        source = inventorySlot;
        hotbar = hotbarSlot;
        expected = displaced.copy();
        clearing = false;
        ticks = 0;
        swapped = false;
        screen = new ToolScreen(mc);
        mc.getNetHandler()
            .addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(screen);
        return true;
    }

    public boolean clearHotbar(Minecraft mc, int hotbarSlot, int emptySlot) {
        if (active() || mc.currentScreen != null
            || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null
            || hotbarSlot < 0
            || hotbarSlot >= 9
            || emptySlot < 9
            || emptySlot >= 36
            || mc.thePlayer.inventory.getStackInSlot(emptySlot) != null) return false;
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
        if (stack == null) return false;
        expected = stack.copy();
        source = emptySlot;
        hotbar = hotbarSlot;
        ticks = 0;
        swapped = false;
        clearing = true;
        screen = new ToolScreen(mc);
        mc.getNetHandler()
            .addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT));
        mc.displayGuiScreen(screen);
        return true;
    }

    public boolean tick(Minecraft mc) {
        if (mc.currentScreen != screen || mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer
            || mc.thePlayer.inventory.getItemStack() != null) {
            cancel(mc);
            return false;
        }
        if (++ticks == 6) {
            if (clearing
                ? mc.thePlayer.inventory.getStackInSlot(source) != null
                    || !ItemStack.areItemStacksEqual(expected, mc.thePlayer.inventory.getStackInSlot(hotbar))
                : !ItemStack.areItemStacksEqual(expected, mc.thePlayer.inventory.getStackInSlot(source))) {
                cancel(mc);
                return false;
            }
            swapped = screen.swap(mc, source, hotbar);
            if (!swapped) {
                cancel(mc);
                return false;
            }
        }
        if (ticks >= 12) {
            boolean valid = swapped && (clearing
                ? mc.thePlayer.inventory.getStackInSlot(hotbar) == null
                    && ItemStack.areItemStacksEqual(expected, mc.thePlayer.inventory.getStackInSlot(source))
                : ItemStack.areItemStacksEqual(expected, mc.thePlayer.inventory.getStackInSlot(hotbar)));
            if (valid) {
                mc.thePlayer.inventory.currentItem = hotbar;
                mc.playerController.updateController();
            }
            cancel(mc);
            return valid;
        }
        return true;
    }

    public void cancel(Minecraft mc) {
        if (screen != null && mc.currentScreen == screen
            && mc.thePlayer != null
            && mc.thePlayer.inventory.getItemStack() == null) mc.thePlayer.closeScreen();
        screen = null;
        expected = null;
    }
}
