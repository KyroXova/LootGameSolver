package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import org.junit.Test;

public class InteractionVisibilityTest {

    @Test
    public void rejectsTheWallInFrontOfTheTarget() {
        MovingObjectPosition wall = new MovingObjectPosition(1, 2, 3, 1, Vec3.createVectorHelper(1.5, 3, 3.5));
        assertTrue(LootGamesBridge.targetHit(wall, 1, 2, 3));
        assertFalse(LootGamesBridge.targetHit(wall, 1, 2, 4));
        assertFalse(LootGamesBridge.targetHit(null, 1, 2, 3));
    }
}
