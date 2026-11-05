package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationSteeringTest {

    @Test
    public void halfTurnSettlesQuicklyWithoutOvershooting() {
        for (int fps : new int[] { 30, 60, 144 }) {
            float angle = 0;
            for (int frame = 0; frame < fps; frame++) {
                angle += NavigationSteering.cameraStep(angle, 179F, 1D / fps);
                assertTrue(angle >= 0 && angle <= 179F);
            }
            assertEquals(179F, angle, 0.08F);
        }
    }

    @Test
    public void headingStaysAlongSegmentAfterSmallOvershoot() {
        assertEquals(-45F, NavigationSteering.heading(1.5D, 2.5D, 2.5D, 3.5D, 20F), 0F);
        assertEquals(-90F, NavigationSteering.heading(1.5D, 2.5D, 2.5D, 2.5D, 20F), 0F);
        assertEquals(0F, NavigationSteering.heading(2.5D, 2.5D, 2.5D, 3.5D, 20F), 0F);
        assertEquals(20F, NavigationSteering.heading(2.5D, 2.5D, 2.5D, 2.5D, 20F), 0F);
        assertTrue(NavigationSteering.passed(2.6D, 2.5D, 1.5D, 2.5D, 2.5D, 2.5D));
    }

    @Test
    public void renderStepsConvergeAcrossFrameRatesWithoutRevolutions() {
        for (int fps : new int[] { 30, 60, 144 }) {
            float yaw = 179F;
            for (int i = 0; i < fps; i++) {
                float delta = NavigationSteering.cameraStep(yaw, -179F, 1D / fps);
                assertTrue(delta >= 0 && delta <= 300D / fps + 0.001D);
                yaw += delta;
            }
            assertEquals(181F, yaw, 0.08F);
            yaw = 0F;
            for (int i = 0; i < fps; i++) yaw += NavigationSteering.cameraStep(yaw, 90F, 1D / fps);
            assertEquals(90F, yaw, 0.1F);
        }
        assertEquals(0F, NavigationSteering.cameraStep(0, 90, 0), 0F);
        assertTrue(Math.abs(NavigationSteering.cameraStep(0, 180, 5)) <= 15F);
    }

    @Test
    public void passedWaypointDoesNotRequireTurningBack() {
        assertTrue(NavigationSteering.passed(2.65D, 0.5D, 1.5D, 0.5D, 2.5D, 0.5D));
        org.junit.Assert.assertFalse(NavigationSteering.passed(2.65D, 1.2D, 1.5D, 0.5D, 2.5D, 0.5D));
    }

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
