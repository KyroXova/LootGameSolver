package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class VerifiedStorageTransferTest {

    @Test
    public void protectsOriginalGearButDepositsGearWithdrawnFromBags() {
        ItemStack original = new ItemStack(new Item().setMaxDamage(100));
        ItemStack loot = new ItemStack(new Item().setMaxDamage(100));
        ItemStack[] originalInventory = new ItemStack[36];
        originalInventory[0] = original;
        InventoryBaseline baseline = new InventoryBaseline(originalInventory);
        StorageLootLedger ledger = new StorageLootLedger();
        ledger.withdrawn(original, 1);
        ledger.withdrawn(loot, 1);
        assertFalse(VerifiedStorageTransfer.movable(9, original, -1, ledger, baseline));
        assertTrue(VerifiedStorageTransfer.movable(10, loot, -1, ledger, baseline));
        assertFalse(VerifiedStorageTransfer.movable(0, loot, -1, ledger, baseline));
        assertFalse(VerifiedStorageTransfer.movable(10, loot, 10, ledger, baseline));
    }
}
