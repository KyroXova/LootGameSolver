package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationControlPolicyTest {

    @Test
    public void anyManualMovementControlPausesNavigation() {
        assertTrue(NavigationControlPolicy.hasManualMovement(false, false, false, false, false, true));
        assertTrue(NavigationControlPolicy.hasManualMovement(false, false, true, false, false, false));
        assertFalse(NavigationControlPolicy.hasManualMovement(false, false, false, false, false, false));
    }
}
