package com.kyroxova.lootgamesolver.client;

import java.util.List;

/** Return-route decisions that must remain independent of room geometry. */
final class ReturnRoutePolicy {

    private ReturnRoutePolicy() {}

    static boolean shouldContinueSavedTrailJoin(boolean rejoiningSavedTrail, List<int[]> savedRoute) {
        return rejoiningSavedTrail && savedRoute != null && !savedRoute.isEmpty();
    }
}
