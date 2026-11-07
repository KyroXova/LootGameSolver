package com.kyroxova.lootgamesolver.client;

import java.util.List;
import java.util.function.Predicate;

final class ReturnRoutePolicy {

    private ReturnRoutePolicy() {}

    static boolean shouldContinueSavedTrailJoin(boolean rejoiningSavedTrail, List<int[]> savedRoute) {
        return rejoiningSavedTrail && savedRoute != null && !savedRoute.isEmpty();
    }

    static int nextSafeCheckpoint(List<int[]> route, int firstIndex, Predicate<int[]> isSafe) {
        if (route == null || isSafe == null) return -1;
        for (int index = Math.max(1, firstIndex); index < route.size(); index++) {
            if (isSafe.test(route.get(index))) return index;
        }
        return -1;
    }
}
