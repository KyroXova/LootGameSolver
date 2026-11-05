package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PuzzleCompletionGateTest {

    @Test
    public void onlyCurrentServerWinCompletesThePuzzle() {
        PuzzleCompletionGate gate = new PuzzleCompletionGate();
        assertFalse(gate.won(true));
        gate.begin();
        assertFalse(gate.won(true));
        gate.message("msg.lootgames.win");
        assertTrue(gate.won(true));
        assertFalse(gate.won(false));
    }

    @Test
    public void lossCannotBeOverriddenByLaterWinMessage() {
        PuzzleCompletionGate gate = new PuzzleCompletionGate();
        gate.begin();
        gate.message("msg.lootgames.lose");
        gate.message("msg.lootgames.win");
        assertFalse(gate.won(true));
    }
}
