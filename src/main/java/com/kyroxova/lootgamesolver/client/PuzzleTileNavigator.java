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
    private double lastX, lastZ;

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
        double dx = target[0] + 0.5D - mc.thePlayer.posX;
        double dy = target[1] + 0.999D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double dz = target[2] + 0.5D - mc.thePlayer.posZ;
        double reach = Math.min(3.5D, mc.playerController.getBlockReachDistance() - 0.25D);
        boolean reachable = withinReach(dx, dy, dz, reach) && LootGamesBridge.topFaceVisible(mc, target);
        if (!java.util.Arrays.equals(tile, target)) {
            if (!keepApproachForNewTile(
                reachable,
                goal != null,
                navigation.state() == BaritoneNavigationBridge.State.RUNNING)) stop();
            tile = target.clone();
            tried.clear();
        }
        if (reachable && (goal == null || navigation.state() != BaritoneNavigationBridge.State.RUNNING
            || atApproachGoal(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ, goal)
            || navigation.completed())) {
            if (goal != null && navigation.state() == BaritoneNavigationBridge.State.RUNNING
                && !net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint))
                mc.thePlayer.setSprinting(false);
            stopRoute();
            return true;
        }
        if (goal != null) {
            double goalDx = goal[0] + 0.5D - mc.thePlayer.posX;
            double goalDz = goal[2] + 0.5D - mc.thePlayer.posZ;
            boolean sprint = goalDx * goalDx + goalDz * goalDz > 4D && mc.thePlayer.onGround
                && !mc.thePlayer.isSneaking()
                && mc.thePlayer.getFoodStats()
                    .getFoodLevel() > 6;
            if (navigation.state() == BaritoneNavigationBridge.State.RUNNING) {
                navigation.setSprinting(sprint);
                if (sprint) mc.thePlayer.setSprinting(true);
            }
            double mx = mc.thePlayer.posX - lastX, mz = mc.thePlayer.posZ - lastZ;
            if (mx * mx + mz * mz >= 0.09D) {
                lastX = mc.thePlayer.posX;
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
            if (sameFeetBlock(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ, candidate)) continue;
            boolean used = false;
            for (int[] old : tried) if (java.util.Arrays.equals(old, candidate)) used = true;
            if (used || !ExplorerTerrain.puzzleCenterStandable(mc.theWorld, x, y, z)
                || !ExplorerTerrain.safe(mc.theWorld, x, y, z)) continue;
            double cx = x + 0.5D - target[0] - 0.5D, cz = z + 0.5D - target[2] - 0.5D;
            if (cx * cx + cz * cz > 4D || !withinReach(cx, mc.thePlayer.getEyeHeight() + 0.001D, cz, reach)
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

    static boolean keepApproachForNewTile(boolean reachable, boolean hasGoal, boolean running) {
        return reachable && hasGoal && running;
    }

    static boolean sameFeetBlock(double x, double y, double z, int[] pos) {
        return (int) Math.floor(x) == pos[0] && (int) Math.floor(y) == pos[1] && (int) Math.floor(z) == pos[2];
    }

    static boolean atApproachGoal(double x, double y, double z, int[] pos) {
        double dx = x - pos[0] - 0.5D, dz = z - pos[2] - 0.5D;
        return Math.floor(y) == pos[1] && dx * dx + dz * dz <= 0.55D * 0.55D;
    }

    void stop() {
        stopRoute();
        tile = null;
        tried.clear();
        busyTicks = 0;
    }
}
