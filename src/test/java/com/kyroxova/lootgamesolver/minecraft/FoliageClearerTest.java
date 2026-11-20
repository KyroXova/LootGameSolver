package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoliageClearerTest {

    @Test
    public void onlyAllowsBreakablePlantsWithoutTileEntities() {
        assertTrue(FoliageClearer.eligible(true, false, false, 0.2F));
        assertTrue(FoliageClearer.eligible(false, true, false, 0.2F));
        assertFalse(FoliageClearer.eligible(true, true, true, 0.2F));
        assertFalse(FoliageClearer.eligible(true, true, false, -1F));
        assertFalse(FoliageClearer.eligible(false, false, false, 0.2F));
        assertFalse(FoliageClearer.eligible(true, false, false, 2F));
    }

    @Test
    public void roomObstructionRejectsUnbreakableOrHighHardnessBlocks() {
        assertFalse(FoliageClearer.isRoomObstruction(null, null, 0, 0, 0));
    }
}
