package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void holdColorsTheKeyLabelOnlyAfterTheHoldBegins() {
        String label = " [G] Explore";
        assertEquals("\u00A7a" + label, ClientHooks.explorerKeyLabel(label, true, 4));
        assertEquals("\u00A7e" + label + "\u00A7a", ClientHooks.explorerKeyLabel(label, true, 20));
    }

    @Test
    public void rewardTargetsStayInTheCurrentRoom() {
        int[] room = { 100, 50, 100 };
        assertTrue(ClientHooks.rewardClusterMatches(room, 102, 51, 99));
        assertFalse(ClientHooks.rewardClusterMatches(room, 120, 51, 99));
        assertFalse(ClientHooks.rewardCollectionTooFar(102, 51, 101, room));
        assertTrue(ClientHooks.rewardCollectionTooFar(400, 51, 101, room));
    }
}
