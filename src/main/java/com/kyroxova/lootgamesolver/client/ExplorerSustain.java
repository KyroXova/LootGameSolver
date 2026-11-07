package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Manages food and the optional Extra Utilities Healing Axe while exploring. */
final class ExplorerSustain {
    private final InventoryToolTransfer transfer = new InventoryToolTransfer();
    private boolean eating;
    private int eatingTicks, restoreSlot = -1, automaticSlot = -1, cooldown;

    boolean busy() { return eating || transfer.active(); }

    boolean tick(Minecraft mc, boolean walking, boolean moving) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) return false;
        if (!walking) { stop(mc); return false; }
        if (transfer.active()) {
            if (!transfer.tick(mc)) cooldown = 100;
            else if (!transfer.active()) automaticSlot = mc.thePlayer.inventory.currentItem;
            return true;
        }
        if (eating) {
            if (mc.currentScreen != null || mc.thePlayer.getFoodStats().getFoodLevel() > 8
                || ++eatingTicks > 80 || (eatingTicks > 2 && !mc.thePlayer.isUsingItem())) {
                releaseUse(mc);
                eating = false;
                cooldown = 20;
                return false;
            }
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
            return true;
        }
        if (cooldown > 0) cooldown--;
        if (mc.currentScreen != null || mc.thePlayer.isUsingItem()) return false;
        if (mc.thePlayer.getFoodStats().getFoodLevel() <= 8 && cooldown == 0) {
            int food = findFood(mc, 0, 9);
            if (food < 0) {
                int inventoryFood = findFood(mc, 9, 36);
                if (inventoryFood >= 0) {
                    if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
                    if (transfer.start(mc, inventoryFood)) return true;
                }
            }
            if (food >= 0) {
                select(mc, food);
                eating = true;
                eatingTicks = 0;
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem());
                return true;
            }
            cooldown = 100;
        }
        if (moving && mc.thePlayer.getFoodStats().getFoodLevel() < 20) {
            boolean found = false;
            for (int i = 0; i < 9; i++) {
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (isHealingAxe(stack)) {
                    select(mc, i);
                    found = true;
                    break;
                }
            }
            if (!found && cooldown == 0) for (int i = 9; i < 36; i++) {
                if (isHealingAxe(mc.thePlayer.inventory.getStackInSlot(i))) {
                    if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
                    if (transfer.start(mc, i)) return true;
                    break;
                }
            }
        }
        return false;
    }

    private static int findFood(Minecraft mc, int start, int end) {
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0 && stack.getItemUseAction() == EnumAction.eat) return i;
        }
        return -1;
    }

    private static boolean isHealingAxe(ItemStack stack) {
        return stack != null && stack.getItem().getClass().getName().equals("com.rwtema.extrautils.item.ItemHealingAxe");
    }

    private void select(Minecraft mc, int slot) {
        if (slot == mc.thePlayer.inventory.currentItem) return;
        if (restoreSlot < 0) restoreSlot = mc.thePlayer.inventory.currentItem;
        automaticSlot = slot;
        mc.thePlayer.inventory.currentItem = slot;
        mc.playerController.updateController();
    }

    private static void releaseUse(Minecraft mc) {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        boolean physical = key < 0 ? key >= -100 && key + 100 < Mouse.getButtonCount() && Mouse.isButtonDown(key + 100)
            : key > 0 && key < 256 && Keyboard.isKeyDown(key);
        KeyBinding.setKeyBindState(key, physical);
    }

    void stop(Minecraft mc) {
        if (eating && mc.gameSettings != null) releaseUse(mc);
        eating = false;
        transfer.cancel(mc);
        if (mc.thePlayer != null && restoreSlot >= 0 && mc.thePlayer.inventory.currentItem == automaticSlot) {
            mc.thePlayer.inventory.currentItem = restoreSlot;
            if (mc.playerController != null) mc.playerController.updateController();
        }
        restoreSlot = -1;
        automaticSlot = -1;
        cooldown = 0;
    }
}
