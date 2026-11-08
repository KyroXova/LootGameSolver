package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SudokuInteractionPolicyTest {

    @Test
    public void avoidsSneakClickWhenHeldItemBlocksInteraction() {
        assertFalse(SolverSession.shouldUseReverseSudokuClicks(0, 9, false));
        assertTrue(SolverSession.shouldUseReverseSudokuClicks(0, 9, true));
        assertFalse(SolverSession.shouldUseReverseSudokuClicks(0, 1, true));
    }
}
