package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PuzzleTileNavigatorTest {

    @Test
    public void considersVerticalDistanceWhenApproachingTile() {
        assertTrue(PuzzleTileNavigator.withinReach(3, 1, 0, 4));
        assertFalse(PuzzleTileNavigator.withinReach(3.8, 1.6, 0, 4));
        assertFalse(PuzzleTileNavigator.withinReach(0, 0, 0, 0));
    }
}
