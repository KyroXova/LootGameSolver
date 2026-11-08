package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class ExcavationPatternsTest {

    @Test
    public void angledStraightStairsTurnTowardGoalInsteadOfOvershootingIt() {
        List<int[]> p = ExcavationPatterns.straight(0, 80, 0, 17, 50, 17);
        assertEquals(30, p.size());
        for (int[] step : p) assertTrue(step[0] >= 0 && step[0] <= 17 && step[2] >= 0 && step[2] <= 17);
        assertEquals(50, p.get(29)[1]);
    }

    @Test
    public void spiralFitsThreeByThreeAndPreservesStairFloors() {
        List<int[]> proposal = ExcavationPatterns.spiral(10, 80, 20, 40, 3, 0);
        List<int[]> checked = ExcavationPatterns.checked(proposal, (x, y, z, nx, ny, nz) -> 1, 10, 80, 20);
        assertEquals(40, checked.size());
        for (int i = 0; i < checked.size(); i++) {
            int[] p = checked.get(i);
            assertTrue(p[0] >= 10 && p[0] <= 12 && p[2] >= 20 && p[2] <= 22);
            assertEquals(79 - i, p[1]);
        }
        assertEquals(12, checked.get(1)[0]);
        assertEquals(20, checked.get(1)[2]);
        assertEquals(12, checked.get(3)[0]);
        assertEquals(22, checked.get(3)[2]);
    }

    @Test
    public void threeStepsBetweenTurnsUsesFourByFourFootprint() {
        List<int[]> p = ExcavationPatterns.spiral(0, 64, 0, 40, 4, 0);
        assertEquals(3, p.get(2)[0]);
        assertEquals(0, p.get(2)[2]);
        assertEquals(3, p.get(5)[0]);
        assertEquals(3, p.get(5)[2]);
    }

    @Test
    public void ascendingSpiralAndStraightStairsRiseOneBlockAtATime() {
        List<int[]> up = ExcavationPatterns
            .checked(ExcavationPatterns.spiral(0, 20, 0, 40, 3, 1), (x, y, z, nx, ny, nz) -> 1, 0, 20, 0);
        assertEquals(20, up.size());
        for (int i = 0; i < up.size(); i++) assertEquals(21 + i, up.get(i)[1]);
        List<int[]> straight = ExcavationPatterns.straight(0, 20, 0, 30, 30, 0);
        assertEquals(10, straight.size());
        assertEquals(30, straight.get(9)[1]);
    }

    @Test
    public void tunnelStaysLevelAndStopsNearGoal() {
        List<int[]> p = ExcavationPatterns.straight(0, 50, 0, 8, 50, 3);
        assertEquals(9, p.size());
        for (int[] step : p) assertEquals(50, step[1]);
        int[] last = p.get(p.size() - 1);
        assertEquals(2, Math.abs(last[0] - 8) + Math.abs(last[2] - 3));
    }

    @Test
    public void hazardCannotBecomePartOfAConfirmedPattern() {
        List<int[]> p = ExcavationPatterns.spiral(0, 64, 0, 40, 3, 0);
        List<int[]> checked = ExcavationPatterns.checked(p, (x, y, z, nx, ny, nz) -> ny < 62 ? -1 : 1, 0, 64, 0);
        assertEquals(2, checked.size());
    }
}
