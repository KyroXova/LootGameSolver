package com.kyroxova.lootgamesolver.explorer.pathfinder;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RewardRouteOwnershipTest {

    @Test
    public void onlyCurrentChestViewIsAllowedToKeepMoving() {
        int[] view = { -3560, 65, -150 };
        assertTrue(Explorer.rewardRouteMatches(true, true, view, view.clone()));
        assertFalse(Explorer.rewardRouteMatches(true, true, new int[] { -3220, 65, -150 }, view));
        assertFalse(Explorer.rewardRouteMatches(true, false, view, view));
        assertFalse(Explorer.rewardRouteMatches(true, true, view, null));
        assertFalse(Explorer.rewardRouteMatches(false, true, view, view));
    }
}
