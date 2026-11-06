package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

public class ReturnRoutePolicyTest {

    @Test
    public void pendingSavedTunnelJoinContinuesBeforeSearchingRoomCorners() {
        assertTrue(ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, Collections.singletonList(new int[] { 1, 2, 3 })));
    }

    @Test
    public void roomSearchIsAllowedWhenNoSavedTunnelJoinRemains() {
        assertFalse(ReturnRoutePolicy.shouldContinueSavedTrailJoin(false, Collections.singletonList(new int[] { 1, 2, 3 })));
        assertFalse(ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, Collections.<int[]>emptyList()));
        assertFalse(ReturnRoutePolicy.shouldContinueSavedTrailJoin(true, null));
    }
}
