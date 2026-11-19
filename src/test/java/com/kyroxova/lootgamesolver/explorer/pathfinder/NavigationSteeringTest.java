package com.kyroxova.lootgamesolver.explorer.pathfinder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NavigationSteeringTest {

    @Test
    public void cameraOnlyTracksStraightRunsOfThreeOrMoreBlocks() {
        assertNull(
            NavigationSteering.straightHeading(
                java.util.Arrays.asList(new int[] { 0, 64, 0 }, new int[] { 1, 64, 1 }, new int[] { 2, 64, 2 }),
                0,
                64,
                0));
        assertNull(
            NavigationSteering.straightHeading(
                java.util.Arrays.asList(new int[] { 1, 64, 0 }, new int[] { 1, 64, 1 }, new int[] { 2, 64, 1 }),
                0,
                64,
                0));
        assertEquals(
            -45F,
            NavigationSteering.straightHeading(
                java.util.Arrays.asList(
                    new int[] { -2, 64, -2 },
                    new int[] { 0, 64, 0 },
                    new int[] { 1, 64, 1 },
                    new int[] { 2, 64, 2 },
                    new int[] { 3, 64, 3 }),
                0,
                64,
                0),
            0F);
    }

    @Test
    public void yieldsTheWalkingCameraDuringBaritoneInteractions() {
        assertTrue(NavigationSteering.yieldCameraToBaritone(true, true, false, 0));
        assertTrue(NavigationSteering.yieldCameraToBaritone(true, false, false, 3));
        assertTrue(NavigationSteering.yieldCameraToBaritone(true, false, true, 0));
        assertFalse(NavigationSteering.yieldCameraToBaritone(true, false, false, 0));
        assertFalse(NavigationSteering.yieldCameraToBaritone(false, true, true, 3));
    }

    @Test
    public void stoppedPlayerWithoutRouteHasNoCameraHeading() {
        assertNull(NavigationSteering.walkingHeading(0, 0, 0, java.util.Collections.emptyList()));
        assertEquals(-90F, NavigationSteering.walkingHeading(0.2, 0, 0, java.util.Collections.emptyList()), 0F);
    }

    @Test
    public void pathHeadingFollowsSegmentInsteadOfPlayerPosition() {
        assertEquals(
            -90F,
            NavigationSteering.pathHeading(java.util.Arrays.asList(new int[] { 10, 64, 10 }, new int[] { 11, 64, 10 })),
            0F);
        assertEquals(
            -45F,
            NavigationSteering.pathHeading(java.util.Arrays.asList(new int[] { 10, 64, 10 }, new int[] { 11, 64, 11 })),
            0F);
        assertNull(NavigationSteering.pathHeading(java.util.Collections.singletonList(new int[] { 10, 64, 10 })));
    }

    @Test
    public void continuationRemovesDetourWhenItRejoinsEarlierWaypoint() {
        java.util.List<int[]> route = new java.util.ArrayList<int[]>();
        route.add(new int[] { 1, 64, 0 });
        route.add(new int[] { 2, 64, 0 });
        route.add(new int[] { 3, 64, 0 });
        java.util.List<int[]> extension = java.util.Arrays
            .asList(new int[] { 2, 64, 0 }, new int[] { 2, 64, 1 }, new int[] { 2, 64, 2 });
        NavigationSteering.appendWithoutLoops(route, extension);
        assertEquals(4, route.size());
        assertEquals(2, route.get(1)[0]);
        assertEquals(1, route.get(2)[2]);
        assertEquals(2, route.get(3)[2]);
    }

    @Test
    public void halfTurnSettlesQuicklyWithoutOvershooting() {
        for (int fps : new int[] { 30, 60, 144 }) {
            float angle = 0;
            for (int frame = 0; frame < 2 * fps; frame++) {
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
            for (int i = 0; i < 2 * fps; i++) yaw += NavigationSteering.cameraStep(yaw, 90F, 1D / fps);
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
