package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SurfaceExitPolicyTest {

    @Test
    public void onlySearchesAfterLeavingTheRoomAndReachingOpenSky() {
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(true, false));
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(true, true));
        assertTrue(BaritoneTransitCoordinator.needsSurfaceExit(false, false));
        assertFalse(BaritoneTransitCoordinator.needsSurfaceExit(false, true));
    }
}
