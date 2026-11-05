package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class GroundPathfinderTest {

    @Test
    public void surfaceWaterRouteCanClimbOntoBank() {
        List<int[]> route = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return z == 0 && y == (x < 3 ? 63 : 64);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 63, 0, 6, 0, 12);
        assertFalse(route.isEmpty());
        int[] last = route.get(route.size() - 1);
        assertTrue(last[0] >= 5 && last[1] == 64);
    }

    @Test
    public void prefersDryDetourOverShortWaterCrossing() {
        List<int[]> route = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0;
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }

            public int extraCost(int x, int y, int z) {
                return x == 2 && z == 0 ? 12 : 0;
            }
        }, 0, 0, 0, 6, 0, 12);
        assertFalse(route.isEmpty());
        for (int[] point : route) assertFalse(point[0] == 2 && point[2] == 0);
    }

    @Test
    public void detoursAroundWall() {
        List<int[]> route = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0 && !(x == 2 && Math.abs(z) < 3);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 0, 0, 6, 0, 12);
        assertFalse(route.isEmpty());
        boolean detour = false;
        for (int[] point : route) {
            assertFalse(point[0] == 2 && Math.abs(point[2]) < 3);
            if (Math.abs(point[2]) >= 3) detour = true;
        }
        assertTrue(detour);
    }

    @Test
    public void climbsAndApproachesDistantGoal() {
        List<int[]> route = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return z == 0 && y == (x < 2 ? 0 : 1);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 0, 0, 240, 0, 12);
        assertFalse(route.isEmpty());
        int[] last = route.get(route.size() - 1);
        assertTrue(last[0] == 12 && last[1] == 1);
    }

    @Test
    public void rejectsLargeDrop() {
        assertTrue(new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return (x == 0 && y == 0) || (x > 0 && y == -4);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 0, 0, 10, 0, 12)
            .isEmpty());
    }
}
