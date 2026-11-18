package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.explorer.ExplorerTerrain;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

/** Routes to a visible tile face without breaking or placing puzzle blocks. */
final class PuzzleTileNavigator {

    private final BaritoneNavigationBridge navigation = new BaritoneNavigationBridge();
    private final List<int[]> tried = new ArrayList<int[]>();
    private int[] tile, goal;
    private int stalled, busyTicks;
    private double lastX, lastY, lastZ;

    boolean moving() {
        return goal != null;
    }

    void pause(boolean paused) {
        if (moving()) navigation.setPaused(paused);
    }

    boolean tick(Minecraft mc, int[] target) {
        if (mc == null || mc.thePlayer == null
            || mc.theWorld == null
            || mc.playerController == null
            || target == null) {
            stop();
            return false;
        }
        if (!java.util.Arrays.equals(tile, target)) {
            stop();
            tile = target.clone();
            tried.clear();
        }
        double dx = target[0] + 0.5D - mc.thePlayer.posX;
        double dy = target[1] + 0.999D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double dz = target[2] + 0.5D - mc.thePlayer.posZ;
        double reach = Math.min(4D, mc.playerController.getBlockReachDistance());
        if (withinReach(dx, dy, dz, reach) && LootGamesBridge.topFaceVisible(mc, target)) {
            stopRoute();
            return true;
        }
        if (goal != null) {
            double mx = mc.thePlayer.posX - lastX, my = mc.thePlayer.posY - lastY, mz = mc.thePlayer.posZ - lastZ;
            if (mx * mx + my * my + mz * mz >= 0.09D) {
                lastX = mc.thePlayer.posX;
                lastY = mc.thePlayer.posY;
                lastZ = mc.thePlayer.posZ;
                stalled = 0;
            } else stalled++;
            if (navigation.state() == BaritoneNavigationBridge.State.RUNNING && stalled < 160) return true;
            tried.add(goal);
            stopRoute();
        }
        List<int[]> candidates = new ArrayList<int[]>();
        int y = target[1] + 1;
        for (int x = target[0] - 3; x <= target[0] + 3; x++) for (int z = target[2] - 3; z <= target[2] + 3; z++) {
            int[] candidate = { x, y, z };
            boolean used = false;
            for (int[] old : tried) if (java.util.Arrays.equals(old, candidate)) used = true;
            if (used || !ExplorerTerrain.puzzleCenterStandable(mc.theWorld, x, y, z)
                || !ExplorerTerrain.safe(mc.theWorld, x, y, z)) continue;
            double cx = x + 0.5D - target[0] - 0.5D, cz = z + 0.5D - target[2] - 0.5D;
            if (!withinReach(cx, mc.thePlayer.getEyeHeight() + 0.001D, cz, reach)
                || !LootGamesBridge.topFaceVisibleFrom(mc, target, x + 0.5D, y + mc.thePlayer.getEyeHeight(), z + 0.5D))
                continue;
            candidates.add(candidate);
        }
        candidates.sort(
            (a, b) -> Double.compare(
                mc.thePlayer.getDistanceSq(a[0] + 0.5D, a[1], a[2] + 0.5D),
                mc.thePlayer.getDistanceSq(b[0] + 0.5D, b[1], b[2] + 0.5D)));
        for (int[] candidate : candidates) {
            BaritoneNavigationBridge.Start result = navigation
                .startPuzzleTileApproach(candidate[0], candidate[1], candidate[2]);
            if (result == BaritoneNavigationBridge.Start.STARTED) {
                goal = candidate;
                stalled = 0;
                busyTicks = 0;
                lastX = mc.thePlayer.posX;
                lastY = mc.thePlayer.posY;
                lastZ = mc.thePlayer.posZ;
                return true;
            }
            if (result == BaritoneNavigationBridge.Start.BUSY) return ++busyTicks < 100;
            tried.add(candidate);
        }
        return false;
    }

    private void stopRoute() {
        if (goal != null) navigation.stop();
        goal = null;
        stalled = 0;
    }

    static boolean withinReach(double dx, double dy, double dz, double reach) {
        return reach > 0 && dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    void stop() {
        stopRoute();
        tile = null;
        tried.clear();
        busyTicks = 0;
    }
}
