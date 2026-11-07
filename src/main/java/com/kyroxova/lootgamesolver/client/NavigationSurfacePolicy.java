package com.kyroxova.lootgamesolver.client;

final class NavigationSurfacePolicy {
    private NavigationSurfacePolicy() {}

    static boolean allow(boolean surfaceStart, boolean protectedJourney, boolean structureApproach, int surfaceY, int y, int x, int z,
        int goalX, int goalZ) {
        if (!surfaceStart || protectedJourney || surfaceY - y <= 6) return true;
        return structureApproach && Math.abs(x - goalX) + Math.abs(z - goalZ) <= 6;
    }
}
