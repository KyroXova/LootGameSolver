package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;

/** Finds and centers on a supported, clear three-by-three area before setting a return warp. */
final class ReturnWarpPositioner {

    interface Standable {

        boolean test(int x, int y, int z);
    }

    enum Result {
        WORKING,
        READY,
        FAILED
    }

    private final BaritoneNavigationBridge navigation = new BaritoneNavigationBridge();

    void pause(boolean paused) {
        navigation.setPaused(paused);
    }

    private final java.util.List<int[]> candidates = new java.util.ArrayList<int[]>();
    private int[] goal;
    private int ticks, candidateIndex;
    private boolean routed;

    boolean start(Minecraft mc, int[] checkpoint) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || checkpoint == null || checkpoint.length != 3)
            return false;
        int px = (int) Math.floor(mc.thePlayer.posX), py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        for (int r = 0; r <= 8; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            for (int dy = 0; dy <= 2; dy++) for (int sign : new int[] { 1, -1 }) {
                int y = py + sign * dy, x = px + dx, z = pz + dz;
                double cx = x + 0.5D - checkpoint[0] - 0.5D;
                double cy = y - checkpoint[1] - 0.5D;
                double cz = z + 0.5D - checkpoint[2] - 0.5D;
                if (cx * cx + cy * cy + cz * cz > 225D) continue;
                if (!clear(mc, x, y, z)) continue;
                int[] candidate = new int[] { x, y, z };
                boolean duplicate = false;
                for (int[] existing : candidates) if (java.util.Arrays.equals(existing, candidate)) duplicate = true;
                if (!duplicate) candidates.add(candidate);
                if (candidates.size() >= 20) {
                    goal = candidates.get(0);
                    return true;
                }
            }
        }
        if (candidates.isEmpty()) return false;
        goal = candidates.get(0);
        return true;
    }

    static boolean clear(Minecraft mc, int x, int y, int z) {
        if (!ExplorerTerrain.safe(mc.theWorld, x, y, z)) return false;
        return footprint(x, y, z, (a, b, c) -> ExplorerTerrain.puzzleCenterStandable(mc.theWorld, a, b, c));
    }

    static boolean footprint(int x, int y, int z, Standable standable) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!standable.test(x + dx, y, z + dz)) return false;
        }
        return true;
    }

    Result tick(Minecraft mc) {
        if (goal == null || mc == null
            || mc.thePlayer == null
            || mc.theWorld == null
            || mc.currentScreen != null
            || mc.thePlayer.hurtTime > 0
            || ++ticks > 600) return Result.FAILED;
        if (!clear(mc, goal[0], goal[1], goal[2])) return nextCandidate();
        double dx = goal[0] + 0.5D - mc.thePlayer.posX, dz = goal[2] + 0.5D - mc.thePlayer.posZ;
        boolean sameFloor = Math.abs(mc.thePlayer.boundingBox.minY - goal[1]) < 0.2D;
        if (centered(dx, dz, sameFloor, mc.thePlayer.onGround)) {
            stop();
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            return Result.READY;
        }
        if (sameFloor && Math.hypot(dx, dz) < 0.8D) {
            if (routed) {
                navigation.stop();
                routed = false;
            }
            double distance = Math.hypot(dx, dz);
            if (distance > 0.01D) {
                mc.thePlayer.motionX = dx / distance * 0.07D;
                mc.thePlayer.motionZ = dz / distance * 0.07D;
            }
            return Result.WORKING;
        }
        if (!routed) {
            if (navigation.startPuzzleTileApproach(goal[0], goal[1], goal[2]) != BaritoneNavigationBridge.Start.STARTED)
                return Result.FAILED;
            routed = true;
        } else if (navigation.state() != BaritoneNavigationBridge.State.RUNNING) return nextCandidate();
        return Result.WORKING;
    }

    private Result nextCandidate() {
        stop();
        if (++candidateIndex >= candidates.size()) return Result.FAILED;
        goal = candidates.get(candidateIndex);
        return Result.WORKING;
    }

    void stop() {
        if (routed) navigation.stop();
        routed = false;
    }

    static boolean centered(double dx, double dz, boolean sameFloor, boolean grounded) {
        return sameFloor && grounded && Math.hypot(dx, dz) < 0.18D;
    }
}
