package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import org.junit.Test;

public class StorageTripPolicyTest {

    @Test
    public void recoveryRequiresEnabledMatchingWorldDestinationAndTarget() {
        String[] target = { "block:0:1:2:3" };
        assertTrue(StorageTripPolicy.destinationConfigured(true, "world", "world", "/home", target));
        assertTrue(StorageTripPolicy.destinationConfigured(true, "world", "world", "/warp storage", target));
        assertFalse(StorageTripPolicy.destinationConfigured(false, "world", "world", "/home", target));
        assertFalse(StorageTripPolicy.destinationConfigured(true, "world", "other", "/home", target));
        assertFalse(StorageTripPolicy.destinationConfigured(true, "world", "world", "/warp x /home", target));
        assertFalse(StorageTripPolicy.destinationConfigured(true, "world", "world", "/home", new String[0]));
        assertFalse(StorageTripPolicy.destinationConfigured(true, "world", "world", "/home", new String[] { "bad" }));
        assertFalse(StorageTripPolicy.validTarget("block:0:1:256:3"));
        assertTrue(StorageTripPolicy.validTarget("entity:0:00000000-0000-0000-0000-000000000001"));
    }

    @Test
    public void teleportMustReachCorrectDimensionAndPosition() {
        assertTrue(StorageTripPolicy.near(2, 1, 2, 3, 2, 1, 2, 3, 4));
        assertFalse(StorageTripPolicy.near(0, 1, 2, 3, 2, 1, 2, 3, 4));
        assertFalse(StorageTripPolicy.near(2, 10, 2, 3, 2, 1, 2, 3, 4));
        assertFalse(StorageTripPolicy.near(2, Double.NaN, 2, 3, 2, 1, 2, 3, 4));
    }

}
