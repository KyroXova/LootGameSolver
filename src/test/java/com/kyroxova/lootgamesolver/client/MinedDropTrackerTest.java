package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class MinedDropTrackerTest {

    @Test
    public void tracksMiningIncreasesWithoutDiscardingOriginalItems() {
        Item item = new Item();
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(item, 10);
        MinedDropTracker tracker = new MinedDropTracker();
        tracker.observe(inventory, true, stack -> true);
        assertFalse(tracker.fullyTracked(inventory, 0));
        assertFalse(tracker.containsTracked(inventory, 0));
        inventory[1] = new ItemStack(item, 64);
        tracker.observe(inventory, true, stack -> true);
        assertTrue(tracker.fullyTracked(inventory, 1));
        assertFalse(tracker.fullyTracked(inventory, 0));
        inventory[1] = null;
        tracker.observe(inventory, false, stack -> true);
        assertFalse(tracker.fullyTracked(inventory, 0));
    }

    @Test
    public void leavesMixedStacksAndNonMiningPickupsAlone() {
        Item item = new Item();
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(item, 10);
        MinedDropTracker tracker = new MinedDropTracker();
        tracker.observe(inventory, false, stack -> true);
        inventory[0].stackSize = 20;
        tracker.observe(inventory, true, stack -> true);
        assertFalse(tracker.fullyTracked(inventory, 0));
        assertTrue(tracker.containsTracked(inventory, 0));
        inventory[1] = new ItemStack(new Item(), 3);
        tracker.observe(inventory, false, stack -> true);
        assertFalse(tracker.fullyTracked(inventory, 1));
    }
}
