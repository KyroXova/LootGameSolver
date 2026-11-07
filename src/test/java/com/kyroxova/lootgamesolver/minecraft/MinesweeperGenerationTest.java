package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinesweeperGenerationTest {
    public static class SyncedGame {
        public boolean cIsGenerated;
        public boolean isBoardGenerated() { return true; }
    }

    public static class OlderGame {
        public boolean isBoardGenerated() { return true; }
    }

    @Test
    public void prefersSyncedClientFlagAndSupportsOlderGames() throws Exception {
        SyncedGame game = new SyncedGame();
        assertFalse(LootGamesBridge.minesweeperGenerated(game));
        game.cIsGenerated = true;
        assertTrue(LootGamesBridge.minesweeperGenerated(game));
        assertTrue(LootGamesBridge.minesweeperGenerated(new OlderGame()));
    }
}
