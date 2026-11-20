package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import org.junit.Test;

public class StorageWarpFeedbackTest {

    @Test
    public void acceptsOnlyTheExpectedWarpConfirmation() {
        assertTrue(
            StorageWarpFeedback
                .isReturnWarpConfirmation("serverutilities.lang.warps.set", "Warp 'returnLooting' set!", null));
        assertFalse(
            StorageWarpFeedback.isReturnWarpConfirmation("serverutilities.lang.warps.set", "Warp 'other' set!", null));
        assertFalse(
            StorageWarpFeedback.isReturnWarpConfirmation(
                "serverutilities.lang.warps.not_set",
                "Unknown Warp: 'returnLooting'!",
                "Warp 'returnLooting' set!"));
        assertTrue(
            StorageWarpFeedback
                .isReturnWarpConfirmation(null, "Warp 'returnLooting' set!", "Warp 'returnLooting' set!"));
    }
}
