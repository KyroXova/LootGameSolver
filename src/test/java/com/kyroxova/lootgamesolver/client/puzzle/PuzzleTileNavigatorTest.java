package com.kyroxova.lootgamesolver.client.puzzle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PuzzleTileNavigatorTest {

    @org.junit.Test
    public void retainsMovementOnlyWhenNewTileIsReachableAndRouteIsActive() {
        org.junit.Assert.assertTrue(PuzzleTileNavigator.keepApproachForNewTile(true, true, true));
        org.junit.Assert.assertFalse(PuzzleTileNavigator.keepApproachForNewTile(false, true, true));
        org.junit.Assert.assertFalse(PuzzleTileNavigator.keepApproachForNewTile(true, false, true));
        org.junit.Assert.assertFalse(PuzzleTileNavigator.keepApproachForNewTile(true, true, false));
        int[] goal = { 10, 64, -3 };
        int[] target = { 11, 64, -3 };
        assertTrue(PuzzleTileNavigator.keepApproachForNewTile(null, goal, target, 3.5D, true, true));
        assertFalse(PuzzleTileNavigator.keepApproachForNewTile(null, goal, target, 3.5D, false, false));
        assertFalse(PuzzleTileNavigator.keepApproachForNewTile(null, null, target, 3.5D, true, true));
    }

    @Test
    public void considersVerticalDistanceWhenApproachingTile() {
        assertTrue(PuzzleTileNavigator.withinReach(3, 1, 0, 4));
        assertFalse(PuzzleTileNavigator.withinReach(3.8, 1.6, 0, 4));
        assertFalse(PuzzleTileNavigator.withinReach(0, 0, 0, 0));
    }

    @Test
    public void rejectsTheCurrentBlockAndRecognizesAnActualApproachPosition() {
        int[] goal = { 10, 64, -3 };
        assertTrue(PuzzleTileNavigator.sameFeetBlock(10.1, 64, -2.9, goal));
        assertFalse(PuzzleTileNavigator.sameFeetBlock(11.0, 64, -2.9, goal));
        assertFalse(PuzzleTileNavigator.atApproachGoal(10.01, 64, -2.01, goal));
        assertTrue(PuzzleTileNavigator.atApproachGoal(10.5, 64, -2.5, goal));
        assertFalse(PuzzleTileNavigator.atApproachGoal(10.5, 65, -2.5, goal));
    }
}
