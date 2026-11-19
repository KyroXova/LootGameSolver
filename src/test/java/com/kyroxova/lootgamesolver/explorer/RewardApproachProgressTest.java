package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewardApproachProgressTest {

    @Test
    public void onlyHorizontalMovementResetsTheApproachWatchdog() {
        assertFalse(BaritoneTransitCoordinator.rewardApproachProgress(0, 0));
        assertFalse(BaritoneTransitCoordinator.rewardApproachProgress(0.1, 0.1));
        assertTrue(BaritoneTransitCoordinator.rewardApproachProgress(0.31, 0));
        assertTrue(BaritoneTransitCoordinator.rewardApproachProgress(0.22, 0.22));
    }
}
