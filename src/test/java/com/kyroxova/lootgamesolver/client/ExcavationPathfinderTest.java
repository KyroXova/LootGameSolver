package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExcavationPathfinderTest {

    @Test
    public void descentNeverExcavatesAnEarlierStairFloor() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 12, 0, 0, 1, 0, true);
        finish(search, (x, y, z, nx, ny, nz) -> 1);
        assertFalse(search.path.isEmpty());
        java.util.List<int[]> previous = new java.util.ArrayList<int[]>();
        previous.add(new int[] { 0, 12, 0 });
        int y = 12;
        for (int[] step : search.path) {
            for (int[] earlier : previous) {
                boolean cutsFloor = earlier[0] == step[0] && earlier[2] == step[2]
                    && earlier[1] - 1 >= step[1]
                    && earlier[1] - 1 <= Math.max(y, step[1]) + 1;
                assertFalse("A return stair floor would be removed", cutsFloor);
            }
            previous.add(step);
            y = step[1];
        }
    }

    @Test
    public void beginsSupportedDescentWithoutWaitingForCompleteDeepRoute() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 30, 0, 15, 0, 0, true);
        finish(search, (x, y, z, nx, ny, nz) -> ny == 30 ? 1 : 8);
        assertFalse(search.path.isEmpty());
        int[] last = search.path.get(search.path.size() - 1);
        assertTrue(last[1] <= 28);
        assertTrue(search.expandedNodes() < 4096);
    }

    private static void finish(ExcavationPathfinder search, ExcavationPathfinder.Terrain terrain) {
        for (int i = 0; i < 5000; i++) {
            if (search.advance(terrain, 24, Long.MAX_VALUE)) return;
        }
        throw new AssertionError("Search did not finish within its node bound");
    }

    @Test
    public void routesAroundRoofBeforeDescendingBesideIt() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 5, 0, 0, 1, 0);
        finish(search, (x, y, z, nx, ny, nz) -> ny == 4 && Math.abs(nx) <= 2 && Math.abs(nz) <= 2 ? -1 : 1);
        assertFalse(search.path.isEmpty());
        boolean besideRoof = false;
        int x = 0, y = 5, z = 0;
        for (int[] step : search.path) {
            assertTrue(Math.abs(step[0] - x) + Math.abs(step[2] - z) == 1);
            assertTrue(Math.abs(step[1] - y) <= 1);
            if (Math.abs(step[0]) > 2 || Math.abs(step[2]) > 2) besideRoof = true;
            x = step[0];
            y = step[1];
            z = step[2];
        }
        assertTrue(besideRoof);
        assertTrue(y == 1 && Math.abs(x) + Math.abs(z) <= 2);
    }

    @Test
    public void detoursSidewaysInsteadOfOnlyApproachingTarget() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 1, 0, 6, 1, 0);
        finish(search, (x, y, z, nx, ny, nz) -> ny != 1 || (nx == 2 && Math.abs(nz) < 2) ? -1 : 1);
        assertFalse(search.path.isEmpty());
        assertTrue(
            search.path.stream()
                .anyMatch(step -> Math.abs(step[2]) >= 2));
    }

    @Test
    public void rejectedHazardsNeverBecomeExecutablePaths() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 5, 0, 6, 1, 0);
        finish(search, (x, y, z, nx, ny, nz) -> -1);
        assertTrue(search.path.isEmpty());
        assertFalse(search.rejected.isEmpty());
    }

    @Test
    public void horizonReturnsSafeProgressWhenFullGoalIsBlocked() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 5, 0, 10, 5, 0, true);
        finish(search, (x, y, z, nx, ny, nz) -> ny == 5 && nz == 0 && nx >= 0 && nx <= 4 ? 1 : -1);
        assertFalse(search.path.isEmpty());
        int[] end = search.path.get(search.path.size() - 1);
        assertTrue(end[0] == 4 && end[1] == 5 && end[2] == 0);
    }

    @Test
    public void searchYieldsAndContinuesAcrossTicks() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 1, 0, 10, 1, 0);
        ExcavationPathfinder.Terrain terrain = (x, y, z, nx, ny, nz) -> ny == 1 ? 1 : -1;
        assertFalse(search.advance(terrain, 1, Long.MAX_VALUE));
        finish(search, terrain);
        assertFalse(search.path.isEmpty());
    }
}
