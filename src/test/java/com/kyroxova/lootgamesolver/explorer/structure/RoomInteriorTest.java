package com.kyroxova.lootgamesolver.explorer.structure;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoomInteriorTest {

    @Test
    public void acceptsInteriorButRejectsRoofBelowAndUnknownWalls() {
        assertTrue(RoomExit.insideVerticalBounds(52, 51, 57));
        assertTrue(RoomExit.insideVerticalBounds(51, 51, 57));
        assertFalse(RoomExit.insideVerticalBounds(57, 51, 57));
        assertFalse(RoomExit.insideVerticalBounds(50, 51, 57));
        assertFalse(RoomExit.insideVerticalBounds(52, Integer.MAX_VALUE, Integer.MIN_VALUE));
    }
}
