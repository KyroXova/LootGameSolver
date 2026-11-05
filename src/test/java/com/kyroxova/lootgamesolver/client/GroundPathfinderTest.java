package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

public class GroundPathfinderTest {

    @Test
    public void expiredTimeSlicePreservesSearchInsteadOfDeclaringFailure() {
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0 && !(x == 2 && Math.abs(z) < 3);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        };
        GroundPathfinder.Search search = new GroundPathfinder.Search(terrain, 0, 0, 0, 6, 0, 12);
        assertFalse(search.advance(0));
        boolean complete = false;
        for (int tick = 0; tick < 10000 && !complete; tick++) complete = search.advance(100_000L);
        assertTrue(complete);
        assertFalse(
            search.path()
                .isEmpty());
        assertTrue(
            search.path()
                .stream()
                .anyMatch(step -> Math.abs(step[2]) >= 3));
    }

    private static List<int[]> completedRoute(GroundPathfinder.Terrain terrain, int x, int y, int z, int gx, int gz,
        int radius) {
        GroundPathfinder.Search search = new GroundPathfinder.Search(terrain, x, y, z, gx, gz, radius);
        for (int tick = 0; tick < 10000; tick++) if (search.advance(1_000_000L)) return search.path();
        throw new AssertionError("Search did not complete within its node bound");
    }

    @Test
    public void diagonalDoesNotCutThroughBlockedCorner() {
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0 && x == z;
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        };
        assertTrue(completedRoute(terrain, 0, 0, 0, 6, 6, 12).isEmpty());
    }

    @Test
    public void openGroundUsesDirectDiagonalSteps() {
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0;
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        };
        List<int[]> route = completedRoute(terrain, 0, 0, 0, 6, 6, 12);
        assertFalse(route.isEmpty());
        for (int[] step : route) assertTrue(step[0] == step[2]);
    }

    @Test
    public void partialRoutesCanContinueFromTheirEndpoint() {
        GroundPathfinder.Terrain terrain = new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0 && z == 0;
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        };
        int x = 0;
        for (int i = 0; i < 5 && x < 49; i++) {
            List<int[]> route = completedRoute(terrain, x, 0, 0, 50, 0, 12);
            assertFalse(route.isEmpty());
            assertTrue(route.get(0)[0] == x + 1);
            x = route.get(route.size() - 1)[0];
        }
        assertTrue(x >= 49);
    }

    @Test
    public void openDiagonalApproachDoesNotZigzagEveryBlock() {
        List<int[]> route = completedRoute(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return y == 0;
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 0, 0, 6, 6, 12);
        assertFalse(route.isEmpty());
        int bends = 0, x = 0, z = 0, lastX = 0, lastZ = 0;
        for (int[] step : route) {
            int dx = step[0] - x, dz = step[2] - z;
            if ((lastX != 0 || lastZ != 0) && (dx != lastX || dz != lastZ)) bends++;
            x = step[0];
            z = step[2];
            lastX = dx;
            lastZ = dz;
        }
        assertTrue(bends <= 1);
        assertTrue(Math.abs(x - 6) + Math.abs(z - 6) <= 1);
    }

    @Test
    public void surfaceWaterRouteCanClimbOntoBank() {
        List<int[]> route = completedRoute(new GroundPathfinder.Terrain() {

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
        List<int[]> route = completedRoute(new GroundPathfinder.Terrain() {

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
        List<int[]> route = completedRoute(new GroundPathfinder.Terrain() {

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
        List<int[]> route = completedRoute(new GroundPathfinder.Terrain() {

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
        assertTrue(completedRoute(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return (x == 0 && y == 0) || (x > 0 && y == -4);
            }

            public boolean clear(int x, int y, int z) {
                return true;
            }
        }, 0, 0, 0, 10, 0, 12).isEmpty());
    }
}
