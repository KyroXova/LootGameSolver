package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class RewardLootPolicyTest {

    @Test
    public void rareItemsComeFirstAndUnknownItemsAreNotDiscarded() {
        List<RewardLootPolicy.Entry> ordered = RewardLootPolicy.ordered(
            Arrays.asList(
                new RewardLootPolicy.Entry(0, 0, Double.NaN),
                new RewardLootPolicy.Entry(1, 1, 0.4D),
                new RewardLootPolicy.Entry(2, 2, 0.01D)));
        assertEquals(2, ordered.get(0).chest);
        assertEquals(1, ordered.get(1).chest);
        assertEquals(0, ordered.get(2).chest);
    }
}
