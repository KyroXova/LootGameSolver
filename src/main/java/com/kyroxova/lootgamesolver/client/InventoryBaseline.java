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
        if (slot < 0 || slot >= original.length || stack == null || original[slot] == null) return false;
        return original[slot].isItemEqual(stack) && ItemStack.areItemStackTagsEqual(original[slot], stack);
    }
}
