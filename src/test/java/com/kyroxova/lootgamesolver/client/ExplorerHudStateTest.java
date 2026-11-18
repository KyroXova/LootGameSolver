package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExplorerHudStateTest {

    @Test
    public void explorerRemainsHighlightedDuringItsPuzzleAndRewardWork() {
        assertTrue(ClientHooks.explorerHudActive(true, false, true, false, false, false));
        assertTrue(ClientHooks.explorerHudActive(false, true, true, false, false, false));
        assertTrue(ClientHooks.explorerHudActive(false, false, true, true, false, false));
        assertTrue(ClientHooks.explorerHudActive(false, false, true, false, true, false));
        assertTrue(ClientHooks.explorerHudActive(false, false, true, false, false, true));
        assertFalse(ClientHooks.explorerHudActive(false, false, false, true, false, false));
        assertFalse(ClientHooks.explorerHudActive(false, false, true, false, false, false));
    }
}
