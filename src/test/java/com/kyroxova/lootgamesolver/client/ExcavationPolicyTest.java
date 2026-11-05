package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExcavationPolicyTest {

    @Test
    public void alternateTunnelDirectionsAlwaysApproachTheStructure() {
        for (int[] direction : ExcavationPolicy.approachDirections(10, 20, 5, 17)) {
            assertEquals(1, Math.abs(direction[0]) + Math.abs(direction[1]));
            assertTrue(Math.abs(10 + direction[0] - 5) + Math.abs(20 + direction[1] - 17) < 8);
        }
        assertEquals(0, ExcavationPolicy.approachDirections(5, 17, 5, 17).length);
    }

    @Test
    public void descentTransitionsToLevelTunnelAboveBoard() {
        int y = 70;
        for (int step = 0; step < 30; step++) {
            int next = ExcavationPolicy.destinationY(y, 50);
            assertTrue(next >= 51 && next <= y && y - next <= 1);
            y = next;
        }
        assertEquals(51, y);
    }

    @Test
    public void neverAcceptsVerticalShaftOrDiagonalExcavation() {
        assertTrue(ExcavationPolicy.beside(-2, 7, -1, 7));
        assertFalse(ExcavationPolicy.beside(-2, 7, -2, 7));
        assertFalse(ExcavationPolicy.beside(-2, 7, -1, 8));
    }
}
