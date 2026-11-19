package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class InventoryProtectionTest {

    private static class LootBagItem extends Item {
    }

    private static class BackpackItem extends Item {
    }

    @Test
    public void distinguishesConsumableLootBagsFromBackpacks() {
        assertFalse(InventoryProtection.bag(new ItemStack(new LootBagItem())));
        assertTrue(InventoryProtection.bag(new ItemStack(new BackpackItem())));
        assertFalse(
            InventoryProtection
                .bagIdentity("adventurebackpack:coalJetpack", "com.darkona.adventurebackpack.item.ItemCoalJetpack"));
        assertTrue(
            InventoryProtection
                .bagIdentity("adventurebackpack:backpack", "com.darkona.adventurebackpack.item.ItemAdventureBackpack"));
        assertFalse(InventoryProtection.backpackContainer("com.darkona.adventurebackpack.inventory.ContainerJetpack"));
        assertTrue(InventoryProtection.backpackContainer("com.darkona.adventurebackpack.inventory.ContainerBackpack"));
        assertTrue(InventoryProtection.backpackContainer("de.eydamos.guiadvanced.ContainerWindow"));
    }

    @Test
    public void packingKeepsCarriedDurableGearButAcceptsFramesAndNewDurableLoot() {
        ItemStack frame = new ItemStack(new forestry.apiculture.items.ItemHiveFrame());
        ItemStack gear = new ItemStack(new Item().setMaxDamage(100));
        ItemStack loot = new ItemStack(new Item().setMaxDamage(100));
        ItemStack[] contents = new ItemStack[36];
        contents[10] = frame;
        contents[11] = gear;
        InventoryBaseline baseline = new InventoryBaseline(contents);
        assertFalse(InventoryProtection.essentialForPacking(frame, baseline, 10, false));
        assertTrue(InventoryProtection.essentialForPacking(gear, baseline, 11, false));
        assertFalse(InventoryProtection.essentialForPacking(loot, baseline, 12, false));
        assertTrue(InventoryProtection.essentialForPacking(loot, baseline, 2, false));
        ItemStack food = new ItemStack(new net.minecraft.item.ItemFood(1, false));
        assertTrue(InventoryProtection.essentialForPacking(food, baseline, 12, false));
        assertFalse(InventoryProtection.essentialForPacking(food, baseline, 12, true));
    }
}
