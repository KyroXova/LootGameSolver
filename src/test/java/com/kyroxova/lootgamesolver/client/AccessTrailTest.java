package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

public class AccessTrailTest {

    @Test
    public void returnWalkCanResumeFromMiddleOfRecordedStairs() {
        AccessTrail trail = new AccessTrail();
        trail.record(0, 64, 0);
        trail.record(1, 63, 0);
        trail.record(2, 62, 0);
        trail.record(3, 61, 0);
        List<int[]> back = trail.reverse(2, 62, 0);
        assertEquals(2, back.size());
        assertArrayEquals(new int[] { 1, 63, 0 }, back.get(0));
        assertArrayEquals(new int[] { 0, 64, 0 }, back.get(1));
    }

    @Test
    public void reachedSpiralCanBeReversedIntoAnAscendingReturnWalk() {
        AccessTrail trail = new AccessTrail();
        trail.record(0, 64, 0);
        List<int[]> down = ExcavationPatterns.spiral(0, 64, 0, 40, 3, 0);
        for (int[] point : down) trail.record(point[0], point[1], point[2]);
        int[] last = down.get(down.size() - 1);
        List<int[]> back = trail.reverse(last[0], last[1], last[2]);
        assertEquals(24, back.size());
        assertArrayEquals(new int[] { 0, 64, 0 }, back.get(back.size() - 1));
        for (int i = 0; i < back.size(); i++) assertEquals(41 + i, back.get(i)[1]);
    }

    @Test
    public void manualTeleportCannotCreateAnUnsafeJumpInReturnPath() {
        AccessTrail trail = new AccessTrail();
        trail.record(0, 64, 0);
        trail.record(1, 63, 0);
        assertTrue(
            trail.reverse(20, 20, 20)
                .isEmpty());
        trail.record(20, 20, 20);
        assertTrue(
            trail.reverse(20, 20, 20)
                .isEmpty());
    }

    @Test
    public void returnCanReconnectToSavedTrailAfterPuzzleInteractionMovedPlayer() {
        AccessTrail trail = new AccessTrail();
        trail.record(0, 64, 0);
        trail.record(1, 63, 0);
        trail.record(2, 62, 0);
        assertEquals(2, trail.nearestIndex(3, 62, 0, 8));
        List<int[]> back = trail.reverseFrom(2);
        assertEquals(3, back.size());
        assertArrayEquals(new int[] { 2, 62, 0 }, back.get(0));
        assertArrayEquals(new int[] { 0, 64, 0 }, back.get(2));
        assertEquals(-1, trail.nearestIndex(20, 20, 20, 8));
    }

    @Test
    public void smallCheckpointGapsDoNotEraseTheSavedEntrance() {
        AccessTrail trail = new AccessTrail();
        trail.record(0, 64, 0);
        trail.record(1, 64, 0);
        trail.record(4, 64, 0);
        assertEquals(5, trail.size());
        assertArrayEquals(
            new int[] { 0, 64, 0 },
            trail.reverse(4, 64, 0)
                .get(3));
        assertTrue(trail.nearTerminal(4, 64, 0, 1));
    }
}
