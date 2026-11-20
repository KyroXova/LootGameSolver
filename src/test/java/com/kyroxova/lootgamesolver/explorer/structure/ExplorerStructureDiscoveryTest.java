package com.kyroxova.lootgamesolver.explorer.structure;

import static org.junit.Assert.*;

import org.junit.Test;

public class ExplorerStructureDiscoveryTest {

    public static final class Position {

        public int getX() {
            return 18;
        }

        public int getY() {
            return 42;
        }

        public int getZ() {
            return -7;
        }
    }

    public static final class Game {

        public int getCurrentBoardSize() {
            return 9;
        }

        public Position getGameCenter() {
            return new Position();
        }
    }

    @Test
    public void discoversOnlyKnownMasterTiles() {
        assertTrue(
            ExplorerStructureDiscovery.isLootGamesTile("ru.timeconqueror.lootgames.common.block.tile.MSMasterTile"));
        assertTrue(
            ExplorerStructureDiscovery.isLootGamesTile("ru.timeconqueror.lootgames.common.block.tile.SudokuTile"));
        assertFalse(
            ExplorerStructureDiscovery.isLootGamesTile("ru.timeconqueror.lootgames.common.block.tile.SubordinateTile"));
        assertFalse(ExplorerStructureDiscovery.isLootGamesTile(null));
    }

    @Test
    public void usesTheActualBoardCenterRatherThanTheMasterCorner() {
        assertArrayEquals(new int[] { 18, 42, -7 }, ExplorerStructureDiscovery.gameCenter(new Game()));
        assertNull(ExplorerStructureDiscovery.gameCenter(null));
    }
}
