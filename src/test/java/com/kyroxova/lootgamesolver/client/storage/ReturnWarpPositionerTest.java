package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReturnWarpPositionerTest {

    @Test
    public void requiresAllNineCellsAndAStationaryCenter() {
        assertTrue(ReturnWarpPositioner.footprint(10, 20, 30, (x, y, z) -> true));
        assertFalse(ReturnWarpPositioner.footprint(10, 20, 30, (x, y, z) -> x != 9 || z != 29));
        assertTrue(ReturnWarpPositioner.centered(0.1, 0.1, true, true));
        assertFalse(ReturnWarpPositioner.centered(0.2, 0, true, true));
        assertFalse(ReturnWarpPositioner.centered(0, 0, true, false));
    }
}
