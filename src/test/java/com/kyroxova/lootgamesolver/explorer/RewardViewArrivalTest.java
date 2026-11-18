package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewardViewArrivalTest {

    @Test
    public void requiresTheChosenPositionAndFloor() {
        assertTrue(PuzzleCenterNavigator.rewardViewArrived(0.4D, 0.4D, 52D, 51));
        assertFalse(PuzzleCenterNavigator.rewardViewArrived(2D, 0D, 52D, 51));
        assertFalse(PuzzleCenterNavigator.rewardViewArrived(0D, 0D, 53D, 51));
    }
}
