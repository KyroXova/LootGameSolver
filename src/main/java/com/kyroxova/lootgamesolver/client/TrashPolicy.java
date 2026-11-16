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
        return name.contains("cobblestone") || name.contains("dirt") || name.contains("stone");
    }

    static boolean isOreOrDust(ItemStack stack, String id) {
        if (stack != null) {
            for (int oreId : OreDictionary.getOreIDs(stack)) {
                String name = OreDictionary.getOreName(oreId);
                if (name.startsWith("ore") || name.startsWith("dust")
                    || name.startsWith("crushed") || name.startsWith("rawOre")) {
                    return true;
                }
            }
            if (stack.getItem() != null) {
                String unloc = stack.getItem().getUnlocalizedName(stack);
                if (unloc != null) {
                    String ulower = unloc.toLowerCase(Locale.ROOT);
                    if (ulower.contains("ore") || ulower.contains("dust") || ulower.contains("crushed")) {
                        return true;
                    }
                }
            }
        }
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.contains("blockores") || lower.contains(":ore") || lower.endsWith("ore")
                || (lower.contains("gregtech") && (lower.contains("metaitem") || lower.contains("ore") || lower.contains("dust")))) {
                return true;
            }
        }
        return false;
    }

    static boolean isBrokenBlockOrTrash(ItemStack stack, String id, int metadata) {
        if (stack == null && id == null) return false;
        if (isOreOrDust(stack, id)) return true;
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.contains("log") || lower.contains("leaves")
                || lower.contains("sapling") || lower.contains("seed")
                || lower.contains("tallgrass") || lower.contains("flower")
                || lower.contains("gravel") || lower.contains("sand")
                || lower.contains("netherrack") || lower.contains("snow")
                || lower.contains("cobblestone") || lower.contains("dirt")
                || lower.contains("stone") || lower.contains("granite")
                || lower.contains("diorite") || lower.contains("andesite")
                || lower.contains("marble") || lower.contains("basalt")
                || lower.contains("limestone")
                || lower.contains("dungeon") || lower.contains("lootgames")) {
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
