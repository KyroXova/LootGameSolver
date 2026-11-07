package com.kyroxova.lootgamesolver.client;

import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.oredict.OreDictionary;

final class RewardWeighting {
    private RewardWeighting() {}

    static double probability(ItemStack stack, WeightedRandomChestContent[] entries) {
        if (stack == null || entries == null) return Double.NaN;
        long total = 0, matching = 0;
        for (WeightedRandomChestContent entry : entries) {
            if (entry == null || entry.itemWeight <= 0) continue;
            total += entry.itemWeight;
            ItemStack template = entry.theItemId;
            if (template != null && template.getItem() == stack.getItem()
                && (template.getItemDamage() == OreDictionary.WILDCARD_VALUE
                    || template.getItemDamage() == stack.getItemDamage())) matching += entry.itemWeight;
        }
        return total == 0 || matching == 0 ? Double.NaN : (double) matching / total;
    }
}
