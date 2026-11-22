package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class StorageLootLedgerTest {

    @Test
    public void keepsWithdrawalsPendingUntilTheirFullQuantityIsDeposited() {
        Item item = new Item();
        ItemStack stack = new ItemStack(item, 7, 2);
        ItemStack different = new ItemStack(item, 1, 3);
        StorageLootLedger ledger = new StorageLootLedger();
        ledger.withdrawn(stack, 3);
        ledger.withdrawn(stack, 4);
        ledger.deposited(different, 99);
        ledger.deposited(stack, 6);
        assertTrue(ledger.contains(stack));
        assertFalse(ledger.empty());
        ledger.deposited(stack, 1);
        assertTrue(ledger.empty());
    }

}
