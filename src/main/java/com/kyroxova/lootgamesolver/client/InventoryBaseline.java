package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

final class InventoryBaseline {

    private final ItemStack[] original = new ItemStack[36];

    InventoryBaseline(ItemStack[] contents) {
        for (int slot = 0; slot < Math.min(original.length, contents.length); slot++)
            if (contents[slot] != null) original[slot] = contents[slot].copy();
    }

    static InventoryBaseline capture(Minecraft mc) {
        ItemStack[] contents = new ItemStack[36];
        if (mc != null && mc.thePlayer != null) for (int slot = 0; slot < contents.length; slot++)
            contents[slot] = mc.thePlayer.inventory.getStackInSlot(slot);
        return new InventoryBaseline(contents);
    }

    boolean protects(int slot, ItemStack stack) {
        if (slot < 0 || slot >= original.length || stack == null) return false;
        for (ItemStack item : original) {
            if (item != null && item.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(item, stack)) return true;
        }
        return false;
    }

    boolean protectsQuantity(ItemStack[] current, int slot) {
        if (current == null || slot < 0 || slot >= current.length || current[slot] == null) return false;
        ItemStack stack = current[slot];
        int originalCount = 0;
        for (ItemStack item : original) {
            if (matches(item, stack)) originalCount += item.stackSize;
        }
        if (originalCount == 0) return false;
        int earlierCount = 0;
        for (int i = 0; i < slot; i++) {
            if (matches(current[i], stack)) earlierCount += current[i].stackSize;
        }
        return earlierCount < originalCount;
    }

    private static boolean matches(ItemStack first, ItemStack second) {
        return first != null && first.isItemEqual(second) && ItemStack.areItemStackTagsEqual(first, second);
    }
}
