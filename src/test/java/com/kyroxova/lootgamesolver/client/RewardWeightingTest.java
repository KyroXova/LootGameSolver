package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.Test;

public class RewardWeightingTest {
    @Test
    public void combinesDuplicateAndWildcardEntriesAndLeavesUnknownItemsUnranked() {
        Item rare = new Item(), common = new Item(), unknown = new Item();
        WeightedRandomChestContent[] table = {
            new WeightedRandomChestContent(new ItemStack(rare, 1, OreDictionary.WILDCARD_VALUE), 1, 1, 1),
            new WeightedRandomChestContent(new ItemStack(rare, 1, 3), 1, 1, 2),
            new WeightedRandomChestContent(new ItemStack(common), 1, 1, 7)
        };
        assertEquals(0.3D, RewardWeighting.probability(new ItemStack(rare, 1, 3), table), 0.00001D);
        assertEquals(0.7D, RewardWeighting.probability(new ItemStack(common), table), 0.00001D);
        assertTrue(Double.isNaN(RewardWeighting.probability(new ItemStack(unknown), table)));
    }
}
