package com.kyroxova.lootgamesolver.explorer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ProgressiveSearchTest {

    @Test
    public void expandsStraightAlongFacingDirectionAndStopsAtLimit() {
        // yaw 0 = South (+Z)
        ProgressiveSearch search = new ProgressiveSearch(0, 0, 0.0F, 128);
        assertArrayEquals(new int[] { 0, 64 }, search.next());
        assertArrayEquals(new int[] { 0, 128 }, search.next());
        assertNull(search.next());
        assertNull(search.next());
    }

    @Test
    public void goalsAreUniqueAndStayWithinConfiguredBoundary() {
        ProgressiveSearch search = new ProgressiveSearch(-19, -53, 90.0F, 256);
        Set<String> visited = new HashSet<String>();
        int[] goal;
        while ((goal = search.next()) != null) {
            assertTrue(Math.abs(goal[0] + 19) <= 256);
            assertTrue(Math.abs(goal[1] + 53) <= 256);
            assertTrue(visited.add(goal[0] + ":" + goal[1]));
        }
        assertEquals(4, visited.size());
    }
}
