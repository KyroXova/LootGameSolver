package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class GroundPathfinder {

    public interface Terrain {

        boolean stand(int x, int y, int z);

        boolean clear(int x, int y, int z);

        default boolean checkpoint(int x, int y, int z) {
            return stand(x, y, z);
        }

        default int extraCost(int x, int y, int z) {
            return 0;
        }

        default int transitionCost(int x, int y, int z, int nx, int ny, int nz) {
            return 0;
        }

        default boolean canTransition(int x, int y, int z, int nx, int ny, int nz) {
            return true;
        }
    }

    private static final int[][] DIRECTIONS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 1, 1 }, { 1, -1 },
        { -1, 1 }, { -1, -1 } };
    private static final int ANY_GOAL_Y = Integer.MIN_VALUE;

    private static final class Node implements Comparable<Node> {

        final int x, y, z, cost;
        final int bends;
        final double remaining;
        final Node parent;

        Node(int x, int y, int z, int cost, int gx, int gy, int gz, Node parent) {
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
            int dy = gy == ANY_GOAL_Y ? 0 : Math.abs(y - gy);
            remaining = 10 * Math.max(dx, dz) + 4 * Math.min(dx, dz) + 10 * dy;
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
        private final int x, y, z, gx, gy, gz, radius;
        private final PriorityQueue<Node> open = new PriorityQueue<Node>();
        private final Map<Long, Integer> bestCosts = new HashMap<Long, Integer>();
        private final Set<Long> visited = new HashSet<Long>();
        private final Map<Long, Boolean> standable = new HashMap<Long, Boolean>();
        private final Map<Long, Boolean> clearable = new HashMap<Long, Boolean>();
        private final Map<Long, Integer> terrainCosts = new HashMap<Long, Integer>();
        private final Node start;
        private Node best;
        private boolean done;

        Search(Terrain terrain, int x, int y, int z, int gx, int gz, int radius) {
            this(terrain, x, y, z, gx, ANY_GOAL_Y, gz, radius);
        }

        Search(Terrain terrain, int x, int y, int z, int gx, int gy, int gz, int radius) {
            this.terrain = terrain;
            this.x = x;
            this.y = y;
            this.z = z;
            this.gx = gx;
            this.gz = gz;
            this.gy = gy;
            this.radius = radius;
            start = new Node(x, y, z, 0, gx, gy, gz, null);
            best = terrain.checkpoint(x, y, z) ? start : null;
            done = !terrain.stand(x, y, z);
            if (!done) {
                open.add(start);
                bestCosts.put(key(x, y, z), 0);
            }
        }

        boolean advance(long budget) {
            if (done) return true;
            final long deadline = System.nanoTime() + budget;
            while (!open.isEmpty() && visited.size() < 8192 && System.nanoTime() < deadline) {
                Node node = open.remove();
                long nodeKey = key(node.x, node.y, node.z);
                Integer bestCost = bestCosts.get(nodeKey);
                if (bestCost == null || node.cost != bestCost.intValue() || !visited.add(nodeKey)) continue;
                boolean canStop = terrain.checkpoint(node.x, node.y, node.z);
                if (canStop && (best == null || node.remaining < best.remaining)) best = node;
                if (canStop && reachesGoal(node)) {
                    best = node;
                    done = true;
                    break;
                }
                for (int[] direction : DIRECTIONS) for (int dy = -1; dy <= 1; dy++) {
                    int nx = node.x + direction[0], ny = node.y + dy, nz = node.z + direction[1];
                    boolean diagonal = direction[0] != 0 && direction[1] != 0;
                    if (diagonal && (dy != 0 || !stand(terrain, standable, nx, node.y, node.z)
                        || !stand(terrain, standable, node.x, node.y, nz))) continue;
                    if (Math.abs(nx - x) > radius || Math.abs(nz - z) > radius || Math.abs(ny - y) > 12) continue;
                    long position = key(nx, ny, nz);
                    Boolean canStand = standable.get(position);
                    if (canStand == null) {
                        canStand = terrain.stand(nx, ny, nz);
                        standable.put(position, canStand);
                    }
                    if (!canStand || !terrain.canTransition(node.x, node.y, node.z, nx, ny, nz)) continue;
                    if (dy > 0 && !clear(terrain, clearable, node.x, node.y + 2, node.z)) continue;
                    if (dy < 0 && !clear(terrain, clearable, nx, node.y + 1, nz)) continue;
                    if (diagonal && (!clear(terrain, clearable, nx, node.y + 1, node.z)
                        || !clear(terrain, clearable, node.x, node.y + 1, nz))) continue;
                    int stepCost = (diagonal ? 14 : 10) + 10 * Math.abs(dy)
                        + 10 * Math.max(0, terrainCost(terrain, terrainCosts, nx, ny, nz))
                        + Math.max(0, terrain.transitionCost(node.x, node.y, node.z, nx, ny, nz));
                    if (node.parent != null
                        && (nx - node.x != node.x - node.parent.x || nz - node.z != node.z - node.parent.z))
                        stepCost += 6;
                    int nextCost = node.cost + stepCost;
                    Integer priorCost = bestCosts.get(position);
                    if (priorCost != null && priorCost.intValue() <= nextCost) continue;
                    if (visited.contains(position)) continue;
                    bestCosts.put(position, nextCost);
                    open.add(new Node(nx, ny, nz, nextCost, gx, gy, gz, node));
                }
            }
            done = done || open.isEmpty() || visited.size() >= 8192;
            return done;
        }

        List<int[]> path() {
            List<int[]> result = new ArrayList<int[]>();
            if (best == null || best == start
                || best.remaining >= start.remaining && terrain.checkpoint(start.x, start.y, start.z)) return result;
            for (Node node = best; node.parent != null; node = node.parent)
                result.add(new int[] { node.x, node.y, node.z });
            Collections.reverse(result);
            return result;
        }

        boolean reachedGoal() {
            return best != null && terrain.checkpoint(best.x, best.y, best.z) && reachesGoal(best);
        }

        private boolean reachesGoal(Node node) {
            return gy == ANY_GOAL_Y ? node.remaining <= 10D : node.x == gx && node.y == gy && node.z == gz;
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

    private static boolean clear(Terrain terrain, Map<Long, Boolean> cache, int x, int y, int z) {
        long key = key(x, y, z);
        Boolean value = cache.get(key);
        if (value == null) {
            value = terrain.clear(x, y, z);
            cache.put(key, value);
        }
        return value;
    }

    private static int terrainCost(Terrain terrain, Map<Long, Integer> cache, int x, int y, int z) {
        long key = key(x, y, z);
        Integer value = cache.get(key);
        if (value == null) {
            value = Math.max(0, terrain.extraCost(x, y, z));
            cache.put(key, value);
        }
        return value;
    }
}
