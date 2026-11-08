package com.kyroxova.lootgamesolver.client;

import java.util.List;

final class ExplorerGeometry {

    private ExplorerGeometry() {}

    static boolean samePosition(int[] first, int[] second) {
        return first[0] == second[0] && first[1] == second[1] && first[2] == second[2];
    }

    static void appendRoutePoint(List<int[]> points, int[] point) {
        if (point == null) return;
        if (points.isEmpty() || !samePosition(points.get(points.size() - 1), point)) points.add(point.clone());
    }

    static double exitMouthDistance(int[] candidate, int[] mouth) {
        double dx = candidate[3] - mouth[0];
        double dy = candidate[4] - mouth[1];
        double dz = candidate[5] - mouth[2];
        return dx * dx + 2D * dy * dy + dz * dz;
    }

    static int staircaseDirection(List<int[]> steps, int startY) {
        int direction = 0, transitions = 0, previousY = startY;
        for (int i = 0; i < Math.min(8, steps.size()); i++) {
            int y = steps.get(i)[1];
            int nextDirection = Integer.compare(y, previousY);
            previousY = y;
            if (nextDirection == 0) continue;
            if (direction == 0 && i > 1) return 0;
            if (direction != 0 && nextDirection != direction) return 0;
            direction = nextDirection;
            transitions++;
        }
        return transitions >= 3 ? direction : 0;
    }

    static double entranceScore(int[] candidate, double x, double z, int tx, int tz) {
        double travel = Math.hypot(candidate[0] + 0.5D - x, candidate[2] + 0.5D - z);
        double approach = Math.hypot(candidate[0] - tx, candidate[2] - tz);
        return 0.5D * travel + 2D * Math.max(0D, approach - ExcavationPolicy.MIN_ENTRANCE_RADIUS);
    }

    static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    static long supportKey(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }
}
