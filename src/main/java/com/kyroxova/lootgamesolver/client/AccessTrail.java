package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

/** Reached access waypoints, retained for a checked return walk. */
final class AccessTrail {

    private final List<int[]> points = new ArrayList<int[]>();

    void clear() {
        points.clear();
    }

    void record(int x, int y, int z) {
        if (!points.isEmpty()) {
            int[] last = points.get(points.size() - 1);
            if (last[0] == x && last[1] == y && last[2] == z) return;
            if (!adjacent(last, x, y, z)) points.clear();
        }
        points.add(new int[] { x, y, z });
    }

    List<int[]> reverse(int x, int y, int z) {
        List<int[]> route = new ArrayList<int[]>();
        int start = -1, closest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            int distance = Math.abs(p[0] - x) + Math.abs(p[1] - y) + Math.abs(p[2] - z);
            if (adjacent(p, x, y, z) && distance < closest) {
                start = i;
                closest = distance;
            }
        }
        if (start < 1) return route;
        for (int i = start; i >= 0; i--) {
            int[] p = points.get(i);
            if (p[0] != x || p[1] != y || p[2] != z) route.add(p.clone());
        }
        return route;
    }

    private static boolean adjacent(int[] point, int x, int y, int z) {
        int dx = Math.abs(point[0] - x), dy = Math.abs(point[1] - y), dz = Math.abs(point[2] - z);
        return dx <= 1 && dy <= 1 && dz <= 1 && (dy == 0 || dx + dz <= 1);
    }
}
