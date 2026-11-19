package com.kyroxova.lootgamesolver.explorer.pathfinder;

import net.minecraft.util.MovementInput;

public final class NavigationInput extends MovementInput {

    public final MovementInput original;
    public float forward;
    public float sideways;
    public boolean hop;

    public NavigationInput(MovementInput original) {
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
