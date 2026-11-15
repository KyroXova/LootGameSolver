package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class InventoryBaselineTest {

    @Test
    public void protectsOriginalItemsEvenAfterTheyMove() {
        Item original = new Item();
        Item other = new Item();
        ItemStack[] slots = new ItemStack[36];
        slots[4] = new ItemStack(original, 8);
        InventoryBaseline baseline = new InventoryBaseline(slots);
        slots[4].stackSize = 12;
        assertTrue(baseline.protects(4, slots[4]));
        assertTrue(baseline.protects(5, new ItemStack(original)));
        assertFalse(baseline.protects(4, new ItemStack(other)));
    }

    @Test
    public void protectsOriginalQuantityButAllowsExcessStacks() {
        Item item = new Item();
        ItemStack[] original = new ItemStack[36];
        original[4] = new ItemStack(item, 10);
        InventoryBaseline baseline = new InventoryBaseline(original);
        ItemStack[] current = new ItemStack[36];
        current[2] = new ItemStack(item, 64);
        current[8] = new ItemStack(item, 64);
        assertTrue(baseline.protectsQuantity(current, 2));
        assertFalse(baseline.protectsQuantity(current, 8));
    }
}
