package com.kyroxova.lootgamesolver.client.ui;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class NavigationPathGeometryTest {

    @Test
    public void dropsThePreviousCenterWhenMovingForward() {
        assertEquals(
            1,
            NavigationPathGeometry
                .firstForwardIndex(Arrays.asList(new int[] { 0, 64, 0 }, new int[] { 1, 64, 0 }), 1.0, 0.5));
        assertEquals(
            1,
            NavigationPathGeometry
                .firstForwardIndex(Arrays.asList(new int[] { 0, 64, 0 }, new int[] { 1, 64, 1 }), 0.75, 0.75));
        assertEquals(
            0,
            NavigationPathGeometry
                .firstForwardIndex(Arrays.asList(new int[] { 0, 64, 0 }, new int[] { 1, 64, 0 }), 0.25, 0.5));
    }
}
