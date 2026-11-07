package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationSurfacePolicyTest {

    @Test
    public void avoidsUnrelatedCavesButAllowsARealApproachAndReturn() {
        assertFalse(NavigationSurfacePolicy.allow(true, false, true, 80, 60, 0, 0, 30, 0));
        assertTrue(NavigationSurfacePolicy.allow(true, false, true, 80, 60, 27, 0, 30, 0));
        assertFalse(NavigationSurfacePolicy.allow(true, false, false, 80, 60, 27, 0, 30, 0));
        assertTrue(NavigationSurfacePolicy.allow(false, false, true, 80, 60, 0, 0, 30, 0));
        assertTrue(NavigationSurfacePolicy.allow(true, true, true, 80, 60, 0, 0, 30, 0));
    }

    @Test
    public void handlesFractionalSnowLayerRises() {
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 70.5D));
        assertTrue(NavigationSurfacePolicy.needsJump(70D, 70.875D));
        assertFalse(NavigationSurfacePolicy.needsJump(70.875D, 71.125D));
        assertTrue(NavigationSurfacePolicy.reachableRise(70.875D, 71.875D));
        assertFalse(NavigationSurfacePolicy.reachableRise(70D, 71.125D));
    }
}
