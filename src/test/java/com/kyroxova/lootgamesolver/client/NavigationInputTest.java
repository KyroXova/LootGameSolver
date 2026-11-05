package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.minecraft.util.MovementInput;

import org.junit.Test;

public class NavigationInputTest {

    @Test
    public void jumpForSwimmingDoesNotSuppressForwardMovement() {
        MovementInput manual = new MovementInput();
        manual.jump = true;
        NavigationInput input = new NavigationInput(manual);
        input.forward = 1F;
        input.updatePlayerMoveState();
        assertEquals(1F, input.moveForward, 0F);
        assertTrue(input.jump);
    }

    @Test
    public void feedsForwardMovementAndJumpThroughNormalInput() {
        NavigationInput input = new NavigationInput(new MovementInput());
        input.forward = 1F;
        input.hop = true;
        input.updatePlayerMoveState();
        assertEquals(1F, input.moveForward, 0F);
        assertTrue(input.jump);
    }

    @Test
    public void manualInputTakesPriority() {
        MovementInput manual = new MovementInput();
        manual.moveForward = -1F;
        manual.sneak = true;
        NavigationInput input = new NavigationInput(manual);
        input.forward = 1F;
        input.updatePlayerMoveState();
        assertEquals(-1F, input.moveForward, 0F);
        assertTrue(input.sneak);
    }
}
