package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

final class AccessTrail {

    private final List<int[]> points = new ArrayList<int[]>();

    void clear() {
        points.clear();
    }

    boolean record(int x, int y, int z) {
        if (!points.isEmpty()) {
            int[] last = points.get(points.size() - 1);
            if (last[0] == x && last[1] == y && last[2] == z) return false;
            // Do not infer a bridge between checkpoints; it could cross walls.
            if (!adjacent(last, x, y, z)) return false;
        }
        points.add(new int[] { x, y, z });
        return true;
    }

    void replace(List<int[]> saved) {
        points.clear();
        if (saved == null) return;
        for (int[] p : saved) {
            if (!validPoint(p) || !points.isEmpty() && (same(points.get(points.size() - 1), p)
                || !adjacent(points.get(points.size() - 1), p[0], p[1], p[2]))) break;
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

    boolean removeLast() {
        if (points.size() <= 1) return false;
        points.remove(points.size() - 1);
        return true;
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

    int nearestForwardIndex(int x, int y, int z, int maxDistance) {
        int index = -1, closest = Integer.MAX_VALUE;
        for (int i = 0; i < points.size(); i++) {
            int[] p = points.get(i);
            int distance = Math.abs(p[0] - x) + Math.abs(p[1] - y) + Math.abs(p[2] - z);
            if (distance < closest || (distance == closest && i < index)) {
                index = i;
                closest = distance;
            }
        }
        return closest <= maxDistance ? index : -1;
    }

    List<int[]> forwardFrom(int index) {
        List<int[]> route = new ArrayList<int[]>();
        if (index < 0 || index >= points.size()) return route;
        for (int i = index; i < points.size(); i++) route.add(
            points.get(i)
                .clone());
        return route;
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

    static boolean validPoint(int[] point) {
        return point != null && point.length >= 3
            && point[0] >= -30_000_000
            && point[0] <= 30_000_000
            && point[2] >= -30_000_000
            && point[2] <= 30_000_000
            && point[1] >= -64
            && point[1] <= 512;
    }

    static boolean adjacent(int[] point, int x, int y, int z) {
        int dx = Math.abs(point[0] - x), dy = Math.abs(point[1] - y), dz = Math.abs(point[2] - z);
        return dx <= 1 && dy <= 1 && dz <= 1 && (dy == 0 || dx + dz <= 1);
    }

    static boolean same(int[] first, int[] second) {
        return first[0] == second[0] && first[1] == second[1] && first[2] == second[2];
    }

}
