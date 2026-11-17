package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class VerifiedBackpackUnloadTest {

    @Test
    public void choosesMainInventoryEvenWhenHotbarIsFirstAndHasSpace() {
        InventoryBasic player = new InventoryBasic("player", false, 36);
        Container container = new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        };
        Slot hotbar = new Slot(player, 0, 0, 0);
        Slot main = new Slot(player, 9, 0, 0);
        container.inventorySlots.add(hotbar);
        container.inventorySlots.add(main);
        assertSame(main, VerifiedBackpackUnload.destination(container, player, new ItemStack(new Item(), 16)));
    }

    @Test
    public void requiresRoomForTheWholeStack() {
        InventoryBasic player = new InventoryBasic("player", false, 36);
        Container container = new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        };
        Item item = new Item();
        player.setInventorySlotContents(9, new ItemStack(item, 60));
        container.inventorySlots.add(new Slot(player, 9, 0, 0));
        assertNull(VerifiedBackpackUnload.destination(container, player, new ItemStack(item, 8)));
        assertNotNull(VerifiedBackpackUnload.destination(container, player, new ItemStack(item, 4)));
    }
}
