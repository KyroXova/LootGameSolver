package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.tileentity.TileEntityChest;

import org.junit.Test;

public class MarkedStorageDepositTest {

    @Test
    public void matchesTheClientPlaceholderForSingleAndAdjacentDoubleChests() {
        TileEntityChest marked = new TileEntityChest();
        InventoryBasic single = new InventoryBasic("chest", false, 27);
        InventoryBasic doubled = new InventoryBasic("chest", false, 54);
        assertTrue(MarkedStorageDeposit.vanillaChestSizes(marked, single, false));
        assertFalse(MarkedStorageDeposit.vanillaChestSizes(marked, doubled, false));
        assertTrue(MarkedStorageDeposit.vanillaChestSizes(marked, doubled, true));
        assertFalse(MarkedStorageDeposit.vanillaChestSizes(marked, new InventoryBasic("other", false, 9), true));
    }
}
