package com.kyroxova.lootgamesolver.client.storage;

import java.util.Locale;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

public final class InventoryProtection {

    private InventoryProtection() {}

    public static boolean bag(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String unlocalized = "";
        try {
            unlocalized = stack.getUnlocalizedName();
        } catch (Throwable ignored) {}
        return bagIdentity(
            String.valueOf(Item.itemRegistry.getNameForObject(stack.getItem())),
            stack.getItem()
                .getClass()
                .getName(),
            unlocalized);
    }

    public static boolean bagIdentity(String registryName, String className) {
        return bagIdentity(registryName, className, "");
    }

    public static boolean bagIdentity(String registryName, String className, String unlocalizedName) {
        String id = registryName == null ? "" : registryName.toLowerCase(Locale.ROOT);
        String type = className == null ? "" : className.toLowerCase(Locale.ROOT);
        String unloc = unlocalizedName == null ? "" : unlocalizedName.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        int dot = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        if (dot >= 0) type = type.substring(dot + 1);

        if (id.contains("lootbag") || id.contains("loot_bag") || type.contains("lootbag") || type.contains("loot_bag")
            || id.contains("jetpack") || type.contains("jetpack") || unloc.contains("jetpack")
            || id.contains("copter") || type.contains("copter") || unloc.contains("copter")
            || id.contains("batpack") || type.contains("batpack") || unloc.contains("batpack")
            || id.contains("lappack") || type.contains("lappack") || unloc.contains("lappack")
            || id.contains("energypack") || type.contains("energypack") || unloc.contains("energypack")
            || id.contains("fluxpack") || type.contains("fluxpack") || unloc.contains("fluxpack")
            || id.contains("capacitor") || type.contains("capacitor") || unloc.contains("capacitor")
            || id.contains("battery") || type.contains("battery") || unloc.contains("battery")
            || id.contains("charger") || type.contains("charger") || unloc.contains("charger")
            || id.contains("charging") || type.contains("charging") || unloc.contains("charging")
            || id.contains("sleeping") || type.contains("sleeping") || unloc.contains("sleeping")
            || id.contains("feedbag") || type.contains("feedbag") || unloc.contains("feedbag")
            || id.contains("lunch") || type.contains("lunch") || unloc.contains("lunch")
            || id.contains("trash") || type.contains("trash") || unloc.contains("trash")
            || id.contains("quiver") || type.contains("quiver") || unloc.contains("quiver")
            || id.contains("focus") || type.contains("focus") || unloc.contains("focus")
            || id.contains("wand") || type.contains("wand") || unloc.contains("wand")
            || id.contains("seedbag") || type.contains("seedbag") || unloc.contains("seedbag")) {
            return false;
        }

        return id.contains("backpack") || type.contains("backpack")
            || id.contains("knapsack")
            || id.contains("satchel")
            || id.contains("bag")
            || type.contains("bag")
            || id.contains("pouch")
            || type.contains("pouch");
    }

    public static boolean backpackContainer(String name) {
        if (name == null) return false;
        String type = name.toLowerCase(Locale.ROOT);
        if (type.startsWith("de.eydamos.guiadvanced.") || type.startsWith("de.eydamos.backpack.inventory.container."))
            return true;
        int dot = type.lastIndexOf('.');
        if (dot >= 0) type = type.substring(dot + 1);
        if (type.contains("jetpack") || type.contains("copter")
            || type.contains("batpack") || type.contains("lappack")
            || type.contains("energypack") || type.contains("fluxpack")
            || type.contains("capacitor") || type.contains("battery")
            || type.contains("charger") || type.contains("charging")
            || type.contains("quiver") || type.contains("focus")
            || type.contains("wand") || type.contains("seedbag")
            || type.contains("lunch") || type.contains("trash")
            || type.contains("sleeping") || type.contains("feedbag")) {
            return false;
        }
        return (type.contains("backpack") || type.contains("knapsack")
            || type.contains("satchel")
            || type.contains("bag")
            || type.contains("pouch")
            || type.contains("holding")
            || type.contains("iteminventory"));
    }

    public static boolean essential(ItemStack stack) {
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

    public static boolean essentialForPacking(ItemStack stack, InventoryBaseline baseline, int slot, boolean healingAxe) {
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

    public static boolean isBeeFrame(Class<?> type) {
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
