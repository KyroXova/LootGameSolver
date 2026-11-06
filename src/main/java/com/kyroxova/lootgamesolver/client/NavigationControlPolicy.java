package com.kyroxova.lootgamesolver.client;

/** Manual controls that immediately hand movement back to the player. */
final class NavigationControlPolicy {

    private NavigationControlPolicy() {}

    static boolean hasManualMovement(boolean forward, boolean back, boolean left, boolean right, boolean sneak,
        boolean jump) {
        return forward || back || left || right || sneak || jump;
    }
}
