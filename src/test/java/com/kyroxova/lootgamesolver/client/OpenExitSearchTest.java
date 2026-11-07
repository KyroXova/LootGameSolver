package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OpenExitSearchTest {

    @Test
    public void followsAnOpenDoorInsteadOfWalkingThroughTheNearestWall() {
        OpenExitSearch search = new OpenExitSearch(new OpenExitSearch.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0 && Math.abs(x) <= 5 && Math.abs(z) <= 5 && !(x == 2 && z != 3);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }

            public boolean exit(int x, int y, int z) {
                return x == 4 && z == 0;
            }
        }, 0, 0, 0);
        for (int i = 0; i < 1000 && !search.advance(10, Long.MAX_VALUE); i++) {}
        assertFalse(
            search.path()
                .isEmpty());
        boolean door = false;
        for (int[] point : search.path()) {
            if (point[0] == 2) {
                assertTrue(point[2] == 3);
                door = true;
            }
        }
        assertTrue(door);
    }
}
