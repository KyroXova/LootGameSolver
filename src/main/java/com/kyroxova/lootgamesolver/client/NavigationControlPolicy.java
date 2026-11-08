package com.kyroxova.lootgamesolver.client;

final class NavigationControlPolicy {

    private NavigationControlPolicy() {}

    static boolean hasManualMovement(boolean forward, boolean back, boolean left, boolean right, boolean sneak,
        boolean jump) {
        return forward || back || left || right || sneak || jump;
    }
}
