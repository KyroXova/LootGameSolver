package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.junit.Test;

public class VisibleBlockInteractionTest {

    @Test
    public void requiresCrosshairAndActualRayToMatchAnInReachFace() {
        Vec3 eye = Vec3.createVectorHelper(0, 1, 0);
        MovingObjectPosition chest = new MovingObjectPosition(1, 0, 0, 1, Vec3.createVectorHelper(1.5, 1, 0.5));
        MovingObjectPosition wall = new MovingObjectPosition(2, 0, 0, 1, Vec3.createVectorHelper(2, 1, 0));
        MovingObjectPosition otherSide = new MovingObjectPosition(1, 0, 0, 2, Vec3.createVectorHelper(1.5, 1, 0.5));
        assertTrue(VisibleBlockInteraction.matches(chest, chest, eye, 1, 0, 0, 3));
        assertFalse(VisibleBlockInteraction.matches(wall, chest, eye, 1, 0, 0, 3));
        assertFalse(VisibleBlockInteraction.matches(chest, wall, eye, 1, 0, 0, 3));
        assertFalse(VisibleBlockInteraction.matches(chest, otherSide, eye, 1, 0, 0, 3));
        assertFalse(VisibleBlockInteraction.matches(chest, chest, eye, 1, 0, 0, 1));
    }
}
