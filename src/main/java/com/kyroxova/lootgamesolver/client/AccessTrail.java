package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

/** Reached access waypoints, retained for a checked return walk. */
final class AccessTrail {

    private final List<int[]> points = new ArrayList<int[]>();

    void clear() {
        points.clear();
    }

    boolean record(int x, int y, int z) {
        if (!points.isEmpty()) {
            int[] last = points.get(points.size() - 1);
            if (last[0] == x && last[1] == y && last[2] == z) return false;
            int distance = Math.abs(last[0] - x) + Math.abs(last[1] - y) + Math.abs(last[2] - z);
            if (!adjacent(last, x, y, z)) {
                if (distance > 8) points.clear();
                else appendCardinalBridge(last, x, y, z);
            }
        }
        points.add(new int[] { x, y, z });
        return true;
    }

    void replace(List<int[]> saved) {
        points.clear();
        if (saved == null) return;
        for (int[] p : saved) {
            if (p == null || p.length < 3
                || Math.abs(p[0]) > 30_000_000
                || Math.abs(p[2]) > 30_000_000
                || p[1] < -64
                || p[1] > 512) continue;
            if (points.size() >= 16_000) break;
            points.add(new int[] { p[0], p[1], p[2] });
        }
    }

    List<int[]> snapshot() {
        List<int[]> copy = new ArrayList<int[]>(points.size());
        for (int[] p : points) copy.add(p.clone());
        return copy;
    }

    int size() {
        return points.size();
    }

    boolean nearTerminal(int x, int y, int z, int maxDistance) {
        int nearest = nearestIndex(x, y, z, maxDistance);
        return points.size() >= 2 && nearest >= Math.max(1, (int) (points.size() * 0.6D));
    }

    List<int[]> reverse(int x, int y, int z) {
        List<int[]> route = new ArrayList<int[]>();
        int start = -1, closest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            int distance = Math.abs(p[0] - x) + Math.abs(p[1] - y) + Math.abs(p[2] - z);
            if (adjacent(p, x, y, z) && (distance < closest || (distance == closest && i > start))) {
                start = i;
                closest = distance;
            }
        }
        if (start < 1) return route;
        return reverseFrom(start, x, y, z);
    }

    int nearestIndex(int x, int y, int z, int maxDistance) {
        int index = -1, closest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            int distance = Math.abs(p[0] - x) + Math.abs(p[1] - y) + Math.abs(p[2] - z);
            if (distance < closest || (distance == closest && i > index)) {
                index = i;
                closest = distance;
            }
        }
        return closest <= maxDistance ? index : -1;
    }

    int[] pointAt(int index) {
        return index < 0 || index >= points.size() ? null
            : points.get(index)
                .clone();
    }

    List<int[]> reverseFrom(int index) {
        return reverseFrom(index, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    private List<int[]> reverseFrom(int index, int x, int y, int z) {
        List<int[]> route = new ArrayList<int[]>();
        if (index < 0 || index >= points.size()) return route;
        for (int i = index; i >= 0; i--) {
            int[] p = points.get(i);
            if (p[0] != x || p[1] != y || p[2] != z) route.add(p.clone());
        }
        return route;
    }

    private static boolean adjacent(int[] point, int x, int y, int z) {
        int dx = Math.abs(point[0] - x), dy = Math.abs(point[1] - y), dz = Math.abs(point[2] - z);
        return dx <= 1 && dy <= 1 && dz <= 1 && (dy == 0 || dx + dz <= 1);
    }

    private void appendCardinalBridge(int[] start, int x, int y, int z) {
        int cx = start[0], cy = start[1], cz = start[2];
        while (cx != x) {
            cx += Integer.signum(x - cx);
            if (cx != x || cy != y || cz != z) points.add(new int[] { cx, cy, cz });
        }
        while (cz != z) {
            cz += Integer.signum(z - cz);
            if (cx != x || cy != y || cz != z) points.add(new int[] { cx, cy, cz });
        }
        while (cy != y) {
            cy += Integer.signum(y - cy);
            if (cx != x || cy != y || cz != z) points.add(new int[] { cx, cy, cz });
        }
    }
}
