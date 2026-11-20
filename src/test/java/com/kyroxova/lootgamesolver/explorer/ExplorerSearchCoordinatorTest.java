package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ExplorerSearchCoordinatorTest {

    private static void markVisited(Set<Long> set, int chunkX, int chunkZ, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                set.add(Explorer.chunkKey(chunkX + dx, chunkZ + dz));
            }
        }
    }

    @Test
    public void chunkKeyAndMarkVisitedTracksChunks() {
        Set<Long> visited = new HashSet<Long>();
        markVisited(visited, 10, -20, 1);

        assertTrue(ExplorerSearchCoordinator.isExplored(visited, null, 10 << 4, -20 << 4));
        assertTrue(ExplorerSearchCoordinator.isExplored(visited, null, 11 << 4, -20 << 4));
        assertTrue(ExplorerSearchCoordinator.isExplored(visited, null, 9 << 4, -19 << 4));
        assertFalse(ExplorerSearchCoordinator.isExplored(visited, null, 20 << 4, 30 << 4));
    }

    @Test
    public void findOutwardYawRejectsHeadingBackwardsTowardsOrigin() {
        ExplorerSearchCoordinator coordinator = new ExplorerSearchCoordinator();
        Set<Long> visited = new HashSet<Long>();
        // Origin at (0, 0), player has moved out to (0, 500) [South]
        int originX = 0;
        int originZ = 0;

        // Player is at (0, 500). Outward vector points South (+Z, yaw = 0).
        // Candidate baseYaw facing North (180, towards origin) should not be chosen
        // findOutwardYaw with baseYaw = 0 (South) should stay South
        float chosen = coordinator.findOutwardYaw(originX, originZ, visited, null, 0, 500, 0.0F, 0.0F);
        assertEquals(0.0F, chosen, 0.01F);
    }

    @Test
    public void findOutwardYawDeflectsAwayFromVisitedTerrain() {
        ExplorerSearchCoordinator coordinator = new ExplorerSearchCoordinator();
        Set<Long> visited = new HashSet<Long>();
        int originX = 0;
        int originZ = 0;

        // Mark the chunk straight ahead (0, 600..800) as visited
        int px = 0;
        int pz = 500;
        // Yaw 0 goes straight to (0, 628) and (0, 756)
        markVisited(visited, 0, 628 >> 4, 3);
        markVisited(visited, 0, 756 >> 4, 3);

        // findOutwardYaw should deflect away from 0.0F to an unvisited side angle (+/-25 or +/-45)
        float chosen = coordinator.findOutwardYaw(originX, originZ, visited, null, px, pz, 0.0F, 0.0F);
        assertTrue("Expected deflection away from visited forward path, got: " + chosen, Math.abs(chosen) >= 20.0F);
    }
}
