package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DigToolSelectorTest {

    @Test
    public void choosesFastestUsableToolAcrossMainInventory() {
        float[] speeds = new float[36];
        boolean[] usable = new boolean[36];
        speeds[2] = 3F;
        usable[2] = true;
        speeds[25] = 9F;
        usable[25] = true;
        speeds[30] = 20F;
        assertEquals(25, DigToolSelector.bestSlot(speeds, usable));
    }

    @Test
    public void prefersHotbarOnEqualSpeedAndRejectsMissingTools() {
        assertEquals(0, DigToolSelector.bestSlot(new float[] { 4F, 4F }, new boolean[] { true, true }));
        assertEquals(-1, DigToolSelector.bestSlot(new float[] { 1F, 20F }, new boolean[] { true, false }));
    }
}
