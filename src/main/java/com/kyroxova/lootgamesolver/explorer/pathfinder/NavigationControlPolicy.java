package com.kyroxova.lootgamesolver.explorer.pathfinder;

public final class NavigationControlPolicy {

    private NavigationControlPolicy() {}

    public static boolean hasManualMovement(boolean forward, boolean back, boolean left, boolean right, boolean sneak,
        boolean jump) {
        return forward || back || left || right || sneak || jump;
    }
}
