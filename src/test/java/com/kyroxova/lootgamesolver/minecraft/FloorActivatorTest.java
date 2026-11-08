package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FloorActivatorTest {

    @Test
    public void identifiesFloorActivatorsButNotTheOverheadMasterOrOrdinaryBlocks() {
        assertTrue(LootGamesBridge.isFloorActivatorName("ru.timeconqueror.lootgames.common.block.GOLActivatorBlock"));
        assertTrue(LootGamesBridge.isFloorActivatorName("ru.timeconqueror.lootgames.common.block.MSActivatorBlock"));
        assertTrue(
            LootGamesBridge.isFloorActivatorName("ru.timeconqueror.lootgames.common.block.SudokuActivatorBlock"));
        assertFalse(LootGamesBridge.isFloorActivatorName("ru.timeconqueror.lootgames.common.block.PuzzleMasterBlock"));
        assertFalse(LootGamesBridge.isFloorActivatorName("net.minecraft.block.BlockStone"));
    }
}
