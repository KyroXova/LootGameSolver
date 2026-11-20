package com.kyroxova.lootgamesolver.client.loot;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.kyroxova.lootgamesolver.core.MiniGame;

public class PendingLootStoreTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void persistsAndDoesNotOverwriteAnotherUnfinishedStructure() throws Exception {
        PendingLootStore store = new PendingLootStore(new java.io.File(folder.getRoot(), "pending.json"));
        assertTrue(store.save("world", 2, new int[] { 1, 2, 3 }, MiniGame.MINESWEEPER));
        PendingLootStore.Checkpoint loaded = store.load();
        assertEquals("world", loaded.world);
        assertEquals(2, loaded.dimension);
        assertArrayEquals(new int[] { 1, 2, 3 }, loaded.center);
        assertEquals(MiniGame.MINESWEEPER, loaded.game);
        assertFalse(store.save("world", 2, new int[] { 4, 5, 6 }, MiniGame.SUDOKU));
        assertTrue(store.clear());
        assertNull(store.load());
    }
}
