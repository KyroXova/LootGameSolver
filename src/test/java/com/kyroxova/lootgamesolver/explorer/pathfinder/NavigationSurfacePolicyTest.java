package com.kyroxova.lootgamesolver.explorer.pathfinder;

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
        // Steps up to slab height (<= 4 layers / <= 0.55D) do not need jump
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 70.125D)); // 1 layer
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 70.25D)); // 2 layers
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 70.375D)); // 3 layers
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 70.5D)); // 4 layers (slab)

        // Steps above slab height (> 4 layers / > 0.55D) require jump
        assertTrue(NavigationSurfacePolicy.needsJump(70D, 70.625D)); // 5 layers
        assertTrue(NavigationSurfacePolicy.needsJump(70D, 70.875D)); // 7 layers
        assertTrue(NavigationSurfacePolicy.needsJump(70D, 71.0D)); // full block

        // Connected transitions with small rise (e.g. from 2 layers to 5 layers = 3 layer diff) do not need jump
        assertFalse(NavigationSurfacePolicy.needsJump(70.25D, 70.625D));
        assertFalse(NavigationSurfacePolicy.needsJump(70.875D, 71.125D));

        // Reachable transitions
        assertTrue(NavigationSurfacePolicy.reachableRise(70.875D, 71.875D));
        // More than 1 block layered snow pile (> 1.001D) is not reachable via a single jump
        assertFalse(NavigationSurfacePolicy.reachableRise(70D, 71.125D));
        assertFalse(NavigationSurfacePolicy.reachableRise(70D, 71.25D));
        assertFalse(NavigationSurfacePolicy.needsJump(70D, 71.25D));
    }
}
