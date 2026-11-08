package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ProgressiveSearchTest {

    @Test
    public void expandsAfterEachRingAndStopsAtLimit() {
        ProgressiveSearch search = new ProgressiveSearch(-100, 200, 32);
        assertArrayEquals(new int[] { -84, 200 }, search.next());
        for (int i = 1; i < 8; i++) search.next();
        assertArrayEquals(new int[] { -68, 200 }, search.next());
        for (int i = 9; i < 16; i++) search.next();
        assertNull(search.next());
        assertNull(search.next());
    }

    @Test
    public void goalsAreUniqueAndStayWithinConfiguredBoundary() {
        ProgressiveSearch search = new ProgressiveSearch(-19, -53, 256);
        Set<String> visited = new HashSet<String>();
        int[] goal;
        while ((goal = search.next()) != null) {
            assertTrue(Math.abs(goal[0] + 19) <= 256);
            assertTrue(Math.abs(goal[1] + 53) <= 256);
            assertTrue(visited.add(goal[0] + ":" + goal[1]));
        }
        assertEquals(128, visited.size());
    }
}
