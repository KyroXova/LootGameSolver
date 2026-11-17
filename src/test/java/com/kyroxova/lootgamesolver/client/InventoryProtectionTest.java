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
    }
}
