package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrashPolicyTest {

    @Test
    public void filtersSpecifiedJunkAndKeepsUnrelatedLoot() {
        assertTrue(TrashPolicy.buildingReserve("minecraft:cobblestone"));
        assertTrue(TrashPolicy.buildingReserve("minecraft:dirt"));
        assertTrue(TrashPolicy.discard("mod:ore", 0, true, false));
        assertTrue(TrashPolicy.discard("minecraft:cake", 0, false, true));
        assertTrue(TrashPolicy.discard("openmodularturrets:hardWallTierOne", 0, false, true));
        assertTrue(TrashPolicy.discard("lootgames:LootGamesDungeonLight", 1, false, true));
        assertFalse(TrashPolicy.discard("minecraft:diamond", 0, false, false));
        assertFalse(TrashPolicy.discard("mod:valuable_block", 0, false, true));
        assertFalse(TrashPolicy.discard("mod:backpack", 0, false, false));
    }
}
