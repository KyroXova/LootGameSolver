package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ShoreArrivalTest {

    @Test
    public void recognizesNearbyGroundWithoutAcceptingAnotherElevationOrDistantShore() {
        int[] goal = { 10, 64, 20 };
        assertTrue(BaritoneTransitCoordinator.nearShoreGoal(10, 64, 20, goal));
        assertTrue(BaritoneTransitCoordinator.nearShoreGoal(11, 64, 21, goal));
        assertFalse(BaritoneTransitCoordinator.nearShoreGoal(12, 64, 20, goal));
        assertFalse(BaritoneTransitCoordinator.nearShoreGoal(10, 62, 20, goal));
    }
}
