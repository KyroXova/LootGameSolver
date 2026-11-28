package com.kyroxova.lootgamesolver.client.storage;

import java.util.Locale;

import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;

import com.kyroxova.lootgamesolver.LootGameSolver;

public final class InventoryProtection {

    private InventoryProtection() {}

    public static boolean bag(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        String unlocalized = "";
        try {
            unlocalized = stack.getUnlocalizedName();
        } catch (Throwable ignored) {
            LootGameSolver.LOG.debug("Could not read item unlocalized name", ignored);
        }
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

    private static final String[] EXCLUDED_BAG_TERMS = { "jetpack", "copter", "batpack", "lappack", "energypack",
        "fluxpack", "capacitor", "battery", "charger", "charging", "sleeping", "feedbag", "lunch", "trash", "quiver",
        "focus", "wand", "seedbag" };
    private static final String[] BAG_ID_OR_TYPE_TERMS = { "backpack", "bag", "pouch" };
    private static final String[] BAG_ID_ONLY_TERMS = { "knapsack", "satchel" };

    public static boolean bagIdentity(String registryName, String className, String unlocalizedName) {
        String id = registryName == null ? "" : registryName.toLowerCase(Locale.ROOT);
        String type = className == null ? "" : className.toLowerCase(Locale.ROOT);
        String unloc = unlocalizedName == null ? "" : unlocalizedName.toLowerCase(Locale.ROOT);
        int colon = id.indexOf(':');
        if (colon >= 0) id = id.substring(colon + 1);
        int dot = Math.max(type.lastIndexOf('.'), type.lastIndexOf('$'));
        if (dot >= 0) type = type.substring(dot + 1);

        if (containsAny(id, "lootbag", "loot_bag") || containsAny(type, "lootbag", "loot_bag")) return false;
        for (String excluded : EXCLUDED_BAG_TERMS) {
            if (id.contains(excluded) || type.contains(excluded) || unloc.contains(excluded)) return false;
        }
        for (String term : BAG_ID_OR_TYPE_TERMS) {
            if (id.contains(term) || type.contains(term)) return true;
        }
        return containsAny(id, BAG_ID_ONLY_TERMS);
    }

    private static boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) return true;
        }
        return false;
    }

    public static boolean backpackContainer(String name) {
        if (name == null) return false;
        String type = name.toLowerCase(Locale.ROOT);
        if (type.startsWith("de.eydamos.guiadvanced.") || type.startsWith("de.eydamos.backpack.inventory.container."))
            return true;
        int dot = type.lastIndexOf('.');
        if (dot >= 0) type = type.substring(dot + 1);
        if (type.contains("jetpack") || type.contains("copter")
            || type.contains("batpack")
            || type.contains("lappack")
            || type.contains("energypack")
            || type.contains("fluxpack")
            || type.contains("capacitor")
            || type.contains("battery")
            || type.contains("charger")
            || type.contains("charging")
            || type.contains("quiver")
            || type.contains("focus")
            || type.contains("wand")
            || type.contains("seedbag")
            || type.contains("lunch")
            || type.contains("trash")
            || type.contains("sleeping")
            || type.contains("feedbag")) {
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

    public static boolean essentialForPacking(ItemStack stack, InventoryBaseline baseline, int slot,
        boolean healingAxe) {
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
