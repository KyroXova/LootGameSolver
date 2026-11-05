package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiningAimGateTest {

    @Test
    public void requiresTwoConsecutiveVisibleAlignedTicks() {
        MiningAimGate gate = new MiningAimGate();
        assertFalse(gate.ready(1, 2, 3, false, 2, 4, 0, 0));
        assertFalse(gate.ready(1, 2, 3, true, 2, 4, 0, 0));
        assertTrue(gate.ready(1, 2, 3, true, 2, 4, 0, 0));
        assertFalse(gate.ready(1, 2, 3, true, 2, 4, 10, 0));
        assertFalse(gate.ready(1, 2, 3, true, 2, 4, 0, 0));
        assertTrue(gate.ready(1, 2, 3, true, 2, 4, 0, 0));
    }

    @Test
    public void changedTargetAndStopRequireFreshAim() {
        MiningAimGate gate = new MiningAimGate();
        assertFalse(gate.ready(1, 2, 3, true, 2, 4, 0, 0));
        assertFalse(gate.ready(1, 1, 3, true, 2, 4, 0, 0));
        assertTrue(gate.ready(1, 1, 3, true, 2, 4, 0, 0));
        gate.reset();
        assertFalse(gate.ready(1, 1, 3, true, 2, 4, 0, 0));
    }

    @Test
    public void neverPermitsOutOfReachOrMisalignedMining() {
        MiningAimGate gate = new MiningAimGate();
        for (int i = 0; i < 10; i++) {
            assertFalse(gate.ready(1, 2, 3, true, 5, 4, 0, 0));
            assertFalse(gate.ready(1, 2, 3, true, 2, 4, 0, 3));
            assertFalse(gate.ready(1, 2, 3, false, 2, 4, 0, 0));
        }
    }
}
