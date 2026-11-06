package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** Bounded three-dimensional ground search, allowing one-block steps and detours. */
public final class GroundPathfinder {

    public interface Terrain {

        boolean stand(int x, int y, int z);

        boolean clear(int x, int y, int z);

        default int extraCost(int x, int y, int z) {
            return 0;
        }
    }

    private static final class Node implements Comparable<Node> {

        final int x, y, z, cost;
        final int bends;
        final double remaining;
        final Node parent;

        Node(int x, int y, int z, int cost, int gx, int gz, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
            this.parent = parent;
            bends = parent == null ? 0
                : parent.bends + (parent.parent != null
                    && (x - parent.x != parent.x - parent.parent.x || z - parent.z != parent.z - parent.parent.z) ? 1
                        : 0);
            int dx = Math.abs(x - gx), dz = Math.abs(z - gz);
            remaining = 10 * Math.max(dx, dz) + 4 * Math.min(dx, dz);
        }

        public int compareTo(Node other) {
            int order = Double.compare(cost + remaining, other.cost + other.remaining);
            if (order == 0) order = Integer.compare(bends, other.bends);
            return order != 0 ? order : Double.compare(remaining, other.remaining);
        }
    }

    public List<int[]> find(Terrain terrain, int x, int y, int z, int gx, int gz, int radius) {
        return find(terrain, x, y, z, gx, gz, radius, 8_000_000L);
    }

    List<int[]> find(Terrain terrain, int x, int y, int z, int gx, int gz, int radius, long budget) {
        Search search = new Search(terrain, x, y, z, gx, gz, radius);
        search.advance(budget);
        return search.path();
    }

    static final class Search {

        private final Terrain terrain;
        private final int x, y, z, gx, gz, radius;
        private final PriorityQueue<Node> open = new PriorityQueue<Node>();
        private final Set<Long> visited = new HashSet<Long>();
        private final Map<Long, Boolean> standable = new HashMap<Long, Boolean>();
        private final Node start;
        private Node best;
        private boolean done;

        Search(Terrain terrain, int x, int y, int z, int gx, int gz, int radius) {
            this.terrain = terrain;
            this.x = x;
            this.y = y;
            this.z = z;
            this.gx = gx;
            this.gz = gz;
            this.radius = radius;
            start = new Node(x, y, z, 0, gx, gz, null);
            best = start;
            done = !terrain.stand(x, y, z);
            if (!done) open.add(start);
        }

        boolean advance(long budget) {
            if (done) return true;
            final long deadline = System.nanoTime() + budget;
            int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 }, { -1, 1 },
                { -1, -1 } };
            while (!open.isEmpty() && visited.size() < 2048 && System.nanoTime() < deadline) {
                Node node = open.remove();
                if (!visited.add(key(node.x, node.y, node.z))) continue;
                if (node.remaining < best.remaining) best = node;
                if (node.remaining <= 10) {
                    best = node;
                    done = true;
                    break;
                }
                for (int[] direction : directions) for (int dy = -1; dy <= 1; dy++) {
                    int nx = node.x + direction[0], ny = node.y + dy, nz = node.z + direction[1];
                    boolean diagonal = direction[0] != 0 && direction[1] != 0;
                    if (diagonal && (dy != 0 || !stand(terrain, standable, nx, node.y, node.z)
                        || !stand(terrain, standable, node.x, node.y, nz))) continue;
                    if (Math.abs(nx - x) > radius || Math.abs(nz - z) > radius || Math.abs(ny - y) > 12) continue;
                    long position = key(nx, ny, nz);
                    if (visited.contains(position)) continue;
                    Boolean canStand = standable.get(position);
                    if (canStand == null) {
                        canStand = terrain.stand(nx, ny, nz);
                        standable.put(position, canStand);
                    }
                    if (!canStand) continue;
                    if (dy > 0 && !terrain.clear(node.x, node.y + 2, node.z)) continue;
                    if (dy < 0 && !terrain.clear(nx, node.y + 1, nz)) continue;
                    open.add(
                        new Node(
                            nx,
                            ny,
                            nz,
                            node.cost + (diagonal ? 14 : 10) + 10 * Math.abs(dy) + 10 * terrain.extraCost(nx, ny, nz),
                            gx,
                            gz,
                            node));
                }
            }
            done = done || open.isEmpty() || visited.size() >= 2048;
            return done;
        }

        List<int[]> path() {
            List<int[]> result = new ArrayList<int[]>();
            if (best.remaining >= start.remaining) return result;
            for (Node node = best; node.parent != null; node = node.parent)
                result.add(new int[] { node.x, node.y, node.z });
            Collections.reverse(result);
            return result;
        }

        boolean reachedGoal() {
            return best.remaining <= 10D;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3ffffffL) << 38 | ((long) z & 0x3ffffffL) << 12 | (y & 0xfffL);
    }

    private static boolean stand(Terrain terrain, Map<Long, Boolean> cache, int x, int y, int z) {
        long key = key(x, y, z);
        Boolean value = cache.get(key);
        if (value == null) {
            value = terrain.stand(x, y, z);
            cache.put(key, value);
        }
        return value;
    }
}
