package com.kyroxova.lootgamesolver.explorer.pathfinder;

import java.util.List;
import java.util.function.Predicate;

public final class ReturnRoutePolicy {

    private ReturnRoutePolicy() {}

    public static boolean shouldContinueSavedTrailJoin(boolean rejoiningSavedTrail, List<int[]> savedRoute) {
        return rejoiningSavedTrail && savedRoute != null && !savedRoute.isEmpty();
    }

    public static int nextSafeCheckpoint(List<int[]> route, int firstIndex, Predicate<int[]> isSafe) {
        if (route == null || isSafe == null) return -1;
        for (int index = Math.max(1, firstIndex); index < route.size(); index++) {
            if (isSafe.test(route.get(index))) return index;
        }
        return -1;
    }
}
