package com.kyroxova.lootgamesolver.client.loot;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.kyroxova.lootgamesolver.client.puzzle.AutoPuzzleCoordinator;
import com.kyroxova.lootgamesolver.client.puzzle.PuzzleCompletionGate;

public class RewardCenterResetTest {

    @Test
    public void startingAnotherRoomDiscardsThePreviousRewardLocation() {
        AutoPuzzleCoordinator coordinator = new AutoPuzzleCoordinator();
        coordinator.setRewardCenter(new int[] { 100, 60, 100 });
        int[] next = { 440, 65, 100 };
        coordinator.startFromExploration(next, null, new PuzzleCompletionGate());
        next[0] = 1;
        assertArrayEquals(new int[] { 440, 65, 100 }, coordinator.getRewardCenter());
        coordinator.startFromExploration(null, null, new PuzzleCompletionGate());
        assertNull(coordinator.getRewardCenter());
    }
}
