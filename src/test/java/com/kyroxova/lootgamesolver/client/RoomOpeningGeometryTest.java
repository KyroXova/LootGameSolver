package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public class RoomOpeningGeometryTest {

    @Test
    public void usesTheScannedPositionOnBothWallAxes() {
        assertArrayEquals(
            new int[] { 99, 64, -37, 101, 64, -37, 100, 64, -37 },
            RoomOpeningGeometry.positions(100, true, -37, 1, 0, 64));
        assertArrayEquals(
            new int[] { 42, 52, -199, 42, 52, -201, 42, 52, -200 },
            RoomOpeningGeometry.positions(-200, false, 42, 0, -1, 52));
    }
}
