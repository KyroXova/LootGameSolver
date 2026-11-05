package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationSteeringTest {

    @Test
    public void crossesYawBoundaryWithoutFullRevolution() {
        assertEquals(181F, NavigationSteering.turn(179F, -179F, 8F), 0.001F);
        assertEquals(-181F, NavigationSteering.turn(-179F, 179F, 8F), 0.001F);
        assertEquals(541F, NavigationSteering.turn(539F, -179F, 8F), 0.001F);
    }

    @Test
    public void capsTurnsAndAcceptsAirborneAndBobbingWaypoints() {
        assertEquals(8F, NavigationSteering.turn(0F, 90F, 8F), 0.001F);
        assertTrue(NavigationSteering.reached(0.3D, 64, 64, false, true));
        assertTrue(NavigationSteering.reached(0.1D, 64, 63, true, false));
    }
}
