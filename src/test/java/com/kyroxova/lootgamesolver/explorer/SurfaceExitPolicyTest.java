package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SurfaceExitPolicyTest {

    @Test
    public void idleExitReplansWithoutInterruptingActiveMining() {
        org.junit.Assert.assertEquals(0, BaritoneTransitCoordinator.surfaceIdleTicks(159, true));
        org.junit.Assert.assertEquals(160, BaritoneTransitCoordinator.surfaceIdleTicks(159, false));
    }

    @Test
    public void onlySearchesAfterLeavingTheRoomAndReachingOpenSky() {
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(true, false));
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(true, true));
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(false, false));
        assertFalse(BaritoneTransitCoordinator.needsSurfaceExit(false, true));
    }
}
