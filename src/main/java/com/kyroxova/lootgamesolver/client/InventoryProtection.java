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
        return bagIdentity(
            String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())),
            stack.getItem()
                .getClass()
                .getName());
    }

    static boolean bagIdentity(String registryName, String className) {
        String id = registryName.toLowerCase(Locale.ROOT);
        String type = className.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        int dot = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        if (dot >= 0) type = type.substring(dot + 1);
        // Consumable reward bags are not portable storage and must never be opened here.
        if (id.contains("lootbag") || id.contains("loot_bag") || type.contains("lootbag") || type.contains("loot_bag"))
            return false;
        return id.contains("backpack") || type.contains("backpack")
            || id.contains("knapsack")
            || id.contains("satchel")
            || id.contains("bag")
            || type.contains("bag");
    }

    static boolean backpackContainer(String name) {
        if (name == null) return false;
        String type = name.toLowerCase(Locale.ROOT);
        if (type.startsWith("de.eydamos.guiadvanced.") || type.startsWith("de.eydamos.backpack.inventory.container."))
            return true;
        int dot = type.lastIndexOf('.');
        if (dot >= 0) type = type.substring(dot + 1);
        return (type.contains("backpack") || type.contains("knapsack")
            || type.contains("satchel")
            || type.contains("bag")) && !type.contains("jetpack") && !type.contains("copter");
    }

    static boolean essential(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return bag(stack) || stack.isItemStackDamageable()
            || item instanceof ItemTool
            || item instanceof ItemSword
            || item instanceof ItemArmor
            || item instanceof ItemFood
            || stack.getItemUseAction() == net.minecraft.item.EnumAction.eat
            || item.getClass()
                .getName()
                .contains("ItemHealingAxe");
    }

    static boolean essentialForPacking(ItemStack stack, InventoryBaseline baseline, int slot, boolean healingAxe) {
        if (stack == null) return false;
        if (isBeeFrame(
            stack.getItem()
                .getClass()))
            return false;
        if (!essential(stack)) return false;
        Item item = stack.getItem();
        if (item instanceof ItemFood || stack.getItemUseAction() == net.minecraft.item.EnumAction.eat)
            return !healingAxe;
        if (bag(stack) || item instanceof ItemTool
            || item instanceof ItemSword
            || item instanceof ItemArmor
            || item.getClass()
                .getName()
                .contains("ItemHealingAxe"))
            return true;
        return slot < 9 || baseline == null || baseline.protects(slot, stack);
    }

    static boolean isBeeFrame(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if ("forestry.apiculture.items.ItemHiveFrame".equals(current.getName())) return true;
            for (Class<?> implemented : current.getInterfaces()) if (frameInterface(implemented)) return true;
        }
        return false;
    }

    private static boolean frameInterface(Class<?> type) {
        if ("forestry.api.apiculture.IHiveFrame".equals(type.getName())) return true;
        for (Class<?> parent : type.getInterfaces()) if (frameInterface(parent)) return true;
        return false;
    }
}
