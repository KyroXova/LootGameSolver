package com.kyroxova.lootgamesolver.client;

import net.minecraft.util.MovementInput;

/** Feeds normal movement physics while preserving the player's original input handler. */
final class NavigationInput extends MovementInput {

    final MovementInput original;
    float forward;
    boolean hop;

    NavigationInput(MovementInput original) {
        this.original = original;
    }

    public void updatePlayerMoveState() {
        original.updatePlayerMoveState();
        boolean manual = original.moveForward != 0 || original.moveStrafe != 0 || original.sneak;
        moveForward = manual ? original.moveForward : forward;
        moveStrafe = original.moveStrafe;
        jump = original.jump || (!manual && hop);
        sneak = original.sneak;
    }
}
