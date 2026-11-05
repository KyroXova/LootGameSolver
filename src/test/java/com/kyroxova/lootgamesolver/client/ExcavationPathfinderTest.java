package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ExcavationPathfinderTest {

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
    public void searchYieldsAndContinuesAcrossTicks() {
        ExcavationPathfinder search = new ExcavationPathfinder(0, 1, 0, 10, 1, 0);
        ExcavationPathfinder.Terrain terrain = (x, y, z, nx, ny, nz) -> ny == 1 ? 1 : -1;
        assertFalse(search.advance(terrain, 1, Long.MAX_VALUE));
        finish(search, terrain);
        assertFalse(search.path.isEmpty());
    }
}
