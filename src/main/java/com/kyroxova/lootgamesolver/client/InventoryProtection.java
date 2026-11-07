package com.kyroxova.lootgamesolver.client;

import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

final class InventoryProtection {
    private InventoryProtection() {}

    static boolean bag(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String id = String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())).toLowerCase(Locale.ROOT);
        String type = stack.getItem().getClass().getName().toLowerCase(Locale.ROOT);
        return id.contains("backpack") || type.contains("backpack")
            || id.contains(":knapsack") || id.contains(":satchel");
    }

    static boolean essential(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return bag(stack) || stack.isItemStackDamageable() || item instanceof ItemTool
            || item instanceof ItemSword || item instanceof ItemArmor || item instanceof ItemFood
            || stack.getItemUseAction() == net.minecraft.item.EnumAction.eat
            || item.getClass().getName().contains("ItemHealingAxe");
    }
}
