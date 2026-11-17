package com.kyroxova.lootgamesolver.client;

import java.util.Locale;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

final class TrashPolicy {

    private TrashPolicy() {}

    static boolean buildingReserve(String id) {
        if (id == null) return false;
        String name = id.toLowerCase(Locale.ROOT);
        return name.equals("minecraft:cobblestone") || name.equals("minecraft:dirt") || name.equals("minecraft:stone");
    }

    static boolean isOreOrDust(ItemStack stack, String id) {
        if (stack != null) {
            for (int oreId : OreDictionary.getOreIDs(stack)) {
                String name = OreDictionary.getOreName(oreId);
                if (name.startsWith("ore") || name.startsWith("rawOre")) {
                    return true;
                }
            }
        }
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (stack != null && stack.getItem() instanceof ItemBlock
                && (lower.contains("blockores") || lower.contains(":ore") || lower.endsWith("_ore"))) {
                return true;
            }
        }
        return false;
    }

    static boolean isBrokenBlockOrTrash(ItemStack stack, String id, int metadata) {
        if (stack == null && id == null) return false;
        if (isOreOrDust(stack, id)) return true;
        if (id != null && (stack == null || stack.getItem() instanceof ItemBlock)) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.equals("minecraft:log") || lower.equals("minecraft:log2")
                || lower.equals("minecraft:leaves")
                || lower.equals("minecraft:leaves2")
                || lower.equals("minecraft:sapling")
                || lower.equals("minecraft:tallgrass")
                || lower.equals("minecraft:yellow_flower")
                || lower.equals("minecraft:red_flower")
                || lower.equals("minecraft:netherrack")
                || (lower.startsWith("lootgames:") && lower.contains("dungeon"))) {
                return true;
            }
        }
        return discard(id, metadata, false, stack != null && stack.getItem() instanceof ItemBlock);
    }

    static boolean discardOre(String id, boolean isOre, boolean all, String[] configured) {
        if (!isOre || id == null) return false;
        if (all) return true;
        if (configured != null) for (String candidate : configured) {
            if (id.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    static boolean discard(String id, int metadata, boolean ore, boolean blockItem) {
        if (id == null) return false;
        String name = id.toLowerCase(Locale.ROOT);
        return ore || buildingReserve(name)
            || name.equals("minecraft:stone")
            || name.equals("minecraft:gravel")
            || name.equals("minecraft:sand")
            || name.equals("minecraft:sandstone")
            || name.equals("minecraft:snow")
            || name.equals("minecraft:snow_layer")
            || name.equals("minecraft:snowball")
            || name.equals("minecraft:cake")
            || name.equals("minecraft:cake_block")
            || (name.equals("lootgames:lootgamesdungeonlight") && metadata == 1)
            || name.startsWith("openmodularturrets:hardwall");
    }
}
