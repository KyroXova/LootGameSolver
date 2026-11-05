package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FoliagePolicyTest {

    @Test
    public void permitsSoftPlantOccludingCurrentOrNextColumn() {
        assertTrue(FoliagePolicy.canClear(true, false, 0, 3, 65, 5, 3, 64, 5, 4, 5));
        assertTrue(FoliagePolicy.canClear(true, false, 0, 4, 64, 5, 3, 64, 5, 4, 5));
    }

    @Test
    public void refusesOtherColumnsProtectedBlocksAndUnbreakablePlants() {
        assertFalse(FoliagePolicy.canClear(true, false, 0, 4, 65, 6, 3, 64, 5, 4, 5));
        assertFalse(FoliagePolicy.canClear(true, true, 0, 4, 64, 5, 3, 64, 5, 4, 5));
        assertFalse(FoliagePolicy.canClear(true, false, -1, 4, 64, 5, 3, 64, 5, 4, 5));
        assertFalse(FoliagePolicy.canClear(false, false, 0, 4, 64, 5, 3, 64, 5, 4, 5));
        assertFalse(FoliagePolicy.canClear(true, false, 0, 4, 60, 5, 3, 64, 5, 4, 5));
    }
}
