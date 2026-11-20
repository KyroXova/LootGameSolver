package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.assertNull;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;

import org.junit.Test;

public class BinnieCompartmentAdapterTest {

    @Test
    public void returnsNullForNullOrVanillaContainers() {
        assertNull(BinnieCompartmentAdapter.detect(null));
        Container chest = new ContainerChest(new InventoryBasic("a", false, 9), new InventoryBasic("b", false, 9));
        assertNull(BinnieCompartmentAdapter.detect(chest));
    }
}
