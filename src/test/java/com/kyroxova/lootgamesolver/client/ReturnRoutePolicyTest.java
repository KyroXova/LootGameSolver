package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ReturnRoutePolicyTest {

    @Test
    public void pendingSavedTunnelJoinContinuesBeforeSearchingRoomCorners() {
        assertTrue(
            ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, Collections.singletonList(new int[] { 1, 2, 3 })));
    }

    @Test
    public void roomSearchIsAllowedWhenNoSavedTunnelJoinRemains() {
        assertFalse(
            ReturnRoutePolicy.shouldContinueSavedTrailJoin(false, Collections.singletonList(new int[] { 1, 2, 3 })));
        assertFalse(ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, Collections.<int[]>emptyList()));
        assertFalse(ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, null));
    }

    @Test
    public void rerouteCanSkipBlockedStepsToFindLaterSafeCheckpoint() {
        java.util.List<int[]> route = Arrays.asList(
            new int[] { 1, 2, 3 },
            new int[] { 2, 2, 3 },
            new int[] { 3, 2, 3 },
            new int[] { 4, 2, 3 },
            new int[] { 5, 2, 3 },
            new int[] { 6, 2, 3 },
            new int[] { 7, 2, 3 });

        int checkpoint = ReturnRoutePolicy.nextSafeCheckpoint(route, 3, point -> point[0] >= 6);

        assertTrue(checkpoint == 5);
    }

    @Test
    public void rerouteDoesNotSelectCheckpointBeforeBlockedSegment() {
        java.util.List<int[]> route = Arrays
            .asList(new int[] { 1, 2, 3 }, new int[] { 2, 2, 3 }, new int[] { 3, 2, 3 }, new int[] { 4, 2, 3 });

        assertTrue(ReturnRoutePolicy.nextSafeCheckpoint(route, 3, point -> true) == 3);
        assertTrue(ReturnRoutePolicy.nextSafeCheckpoint(route, 4, point -> true) == -1);
    }
}
