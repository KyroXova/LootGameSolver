package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExplorerSustainTest {

    @Test
    public void avoidsValuableAndKnownHarmfulFoods() {
        assertFalse(ExplorerSustain.safeFood("minecraft:golden_apple", 0));
        assertFalse(ExplorerSustain.safeFood("minecraft:rotten_flesh", 0));
        assertFalse(ExplorerSustain.safeFood("minecraft:spider_eye", 0));
        assertFalse(ExplorerSustain.safeFood("minecraft:fish", 3));
        assertTrue(ExplorerSustain.safeFood("minecraft:fish", 0));
        assertTrue(ExplorerSustain.safeFood("mod:pizza", 0));
    }
}
