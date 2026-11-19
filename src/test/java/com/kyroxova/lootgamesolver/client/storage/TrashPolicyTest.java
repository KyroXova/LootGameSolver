package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TrashPolicyTest {

    @Test
    public void inventoryPressureBeginsAtSeventyFivePercent() {
        assertFalse(TrashCleanup.inventoryPressureFromEmptySlots(10));
        assertTrue(TrashCleanup.inventoryPressureFromEmptySlots(9));
        assertTrue(TrashCleanup.inventoryPressureFromEmptySlots(0));
    }

    @Test
    public void oreDisposalRequiresAnExplicitSelection() {
        assertFalse(TrashPolicy.discardOre("mod:rare_ore", true, false, new String[0]));
        assertTrue(TrashPolicy.discardOre("mod:common_ore", true, false, new String[] { "mod:common_ore" }));
        assertTrue(TrashPolicy.discardOre("mod:rare_ore", true, true, new String[0]));
        assertFalse(TrashPolicy.discardOre("mod:rare_ore", false, true, new String[0]));
    }

    @Test
    public void filtersSpecifiedJunkAndKeepsUnrelatedLoot() {
        assertTrue(TrashPolicy.buildingReserve("minecraft:cobblestone"));
        assertTrue(TrashPolicy.buildingReserve("minecraft:dirt"));
        assertTrue(TrashPolicy.discard("mod:ore", 0, true, false));
        assertTrue(TrashPolicy.discard("minecraft:cake", 0, false, true));
        assertTrue(TrashPolicy.discard("openmodularturrets:hardWallTierOne", 0, false, true));
        assertTrue(TrashPolicy.discard("lootgames:LootGamesDungeonLight", 1, false, true));
        assertFalse(TrashPolicy.discard("minecraft:diamond", 0, false, false));
        assertFalse(TrashPolicy.discard("minecraft:redstone", 0, false, false));
        assertFalse(TrashPolicy.discard("minecraft:glowstone", 0, false, true));
        assertFalse(TrashPolicy.isOreOrDust(null, "gregtech:gt.metaitem.01"));
        assertFalse(TrashPolicy.discard("mod:valuable_block", 0, false, true));
        assertFalse(TrashPolicy.discard("mod:backpack", 0, false, false));
        assertTrue(TrashPolicy.isOreDictionaryTrash("crushedGypsum"));
        assertTrue(TrashPolicy.isOreDictionaryTrash("crushedPurifiedIron"));
        assertTrue(TrashPolicy.isOreDictionaryTrash("dustImpureGold"));
        assertTrue(TrashPolicy.isOreDictionaryTrash("dustGold"));
        assertTrue(TrashPolicy.isOreDictionaryTrash("dustSmallIron"));
        assertFalse(TrashPolicy.isOreDictionaryTrash("ingotIron"));
        assertTrue(TrashPolicy.discard("gregtech:gt.blockgranites", 9, false, true));
        assertTrue(TrashPolicy.discard("gregtech:gt.blockgranites", 0, false, true));
        assertTrue(TrashPolicy.discard("gregtech:gt.metaitem.01", 3086, false, false));
        assertFalse(TrashPolicy.discard("gregtech:gt.metaitem.01", 2999, false, false));
        assertFalse(TrashPolicy.discard("gregtech:gt.metaitem.01", 4000, false, false));
        assertTrue(TrashPolicy.discard("minecraft:clay", 0, false, true));
        assertTrue(TrashPolicy.discard("minecraft:clay_ball", 0, false, false));
    }

    @Test
    public void brokenBlocksAndPreviousRunJunkRecognizedAsTrash() {
        assertTrue(TrashPolicy.isBrokenBlockOrTrash(null, "lootgames:dungeon_brick", 0));
        assertTrue(TrashPolicy.isBrokenBlockOrTrash(null, "lootgames:dungeon_wall", 0));
        assertTrue(TrashPolicy.isBrokenBlockOrTrash(null, "minecraft:cobblestone", 0));
        assertTrue(TrashPolicy.isBrokenBlockOrTrash(null, "minecraft:tallgrass", 0));
        assertTrue(TrashPolicy.isBrokenBlockOrTrash(null, "minecraft:dirt", 0));
        assertFalse(TrashPolicy.isBrokenBlockOrTrash(null, "minecraft:iron_sword", 0));
    }
}
