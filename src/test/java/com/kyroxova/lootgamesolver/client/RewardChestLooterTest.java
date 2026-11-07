package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class RewardChestLooterTest {
    @Test
    public void mapsRewardChestDirectionsToLootGamesStageOrder() {
        assertEquals(1, RewardChestLooter.stageAt(0, -1));
        assertEquals(2, RewardChestLooter.stageAt(1, 0));
        assertEquals(3, RewardChestLooter.stageAt(0, 1));
        assertEquals(4, RewardChestLooter.stageAt(-1, 0));
        assertEquals(0, RewardChestLooter.stageAt(1, 1));
    }
}
