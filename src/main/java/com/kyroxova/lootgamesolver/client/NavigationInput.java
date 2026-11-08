package com.kyroxova.lootgamesolver.client;

import net.minecraft.util.MovementInput;

final class NavigationInput extends MovementInput {

    final MovementInput original;
    float forward;
    float sideways;
    boolean hop;

    NavigationInput(MovementInput original) {
        this.original = original;
    }

    public void updatePlayerMoveState() {
        original.updatePlayerMoveState();
        boolean manual = original.moveForward != 0 || original.moveStrafe != 0 || original.sneak;
        moveForward = manual ? original.moveForward : forward;
        moveStrafe = manual ? original.moveStrafe : sideways;
        jump = original.jump || (!manual && hop);
        sneak = original.sneak;
    }
}
