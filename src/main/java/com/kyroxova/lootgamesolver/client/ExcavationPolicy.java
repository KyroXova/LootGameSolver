package com.kyroxova.lootgamesolver.client;

/** A staircase descends beside the player; at board height it becomes a level approach. */
final class ExcavationPolicy {

    // Radius is measured from the LootGames center. The dungeon footprint itself
    // occupies much of that radius, so the opening must be farther out to begin
    // mining close to the nearest outer wall rather than deep inside the room.
    static final double MIN_ENTRANCE_RADIUS = 13.0D;
    static final double MAX_ENTRANCE_RADIUS = 36.0D;

    static boolean shouldApproach(double x, double footY, double z, int targetX, int targetY, int targetZ) {
        return targetY < footY - 2 && Math.abs(x - targetX) + Math.abs(z - targetZ) <= 32;
    }

    static int destinationY(int footY, int boardY) {
        return footY > boardY + 1 ? footY - 1 : footY;
    }

    static boolean preferredEntrance(double x, double z, int targetX, int targetZ) {
        double distance = Math.hypot(x - targetX, z - targetZ);
        return distance >= MIN_ENTRANCE_RADIUS && distance <= MAX_ENTRANCE_RADIUS;
    }

    static boolean beside(int x, int z, int nx, int nz) {
        return Math.abs(nx - x) + Math.abs(nz - z) == 1;
    }

    static int[][] approachDirections(int x, int z, int targetX, int targetZ) {
        int dx = Integer.signum(targetX - x), dz = Integer.signum(targetZ - z);
        if (dx == 0) return dz == 0 ? new int[0][] : new int[][] { { 0, dz } };
        if (dz == 0) return new int[][] { { dx, 0 } };
        return Math.abs(targetX - x) >= Math.abs(targetZ - z) ? new int[][] { { dx, 0 }, { 0, dz } }
            : new int[][] { { 0, dz }, { dx, 0 } };
    }
}
