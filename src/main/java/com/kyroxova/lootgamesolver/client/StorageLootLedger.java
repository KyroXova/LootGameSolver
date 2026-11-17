package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

/** Tracks quantities withdrawn from bags until they have been deposited. */
final class StorageLootLedger {

    private final List<ItemStack> pending = new ArrayList<ItemStack>();

    void withdrawn(ItemStack stack, int amount) {
        if (stack == null || amount <= 0) return;
        ItemStack existing = find(stack);
        if (existing != null) existing.stackSize += amount;
        else {
            ItemStack entry = stack.copy();
            entry.stackSize = amount;
            pending.add(entry);
        }
    }

    boolean contains(ItemStack stack) {
        return find(stack) != null;
    }

    void deposited(ItemStack stack, int amount) {
        ItemStack entry = find(stack);
        if (entry == null || amount <= 0) return;
        entry.stackSize -= Math.min(entry.stackSize, amount);
        if (entry.stackSize == 0) pending.remove(entry);
    }

    boolean empty() {
        return pending.isEmpty();
    }

    private ItemStack find(ItemStack stack) {
        if (stack == null) return null;
        for (ItemStack item : pending)
            if (item.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(item, stack)) return item;
        return null;
    }
}
