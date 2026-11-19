package com.kyroxova.lootgamesolver.client.puzzle;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.explorer.trail.ExplorerTerrain;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

/** Routes to a visible tile face without breaking or placing puzzle blocks. */
public final class PuzzleTileNavigator {

    private final BaritoneNavigationBridge navigation = new BaritoneNavigationBridge();
    private final List<int[]> tried = new ArrayList<int[]>();
    private int[] tile, goal;
    private int stalled, busyTicks;
    private double lastX, lastZ;
    private boolean directMoving;

    public boolean moving() {
        return directMoving || goal != null;
    }

    public void pause(boolean paused) {
        if (moving()) navigation.setPaused(paused);
    }

    public boolean tick(Minecraft mc, int[] target) {
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
        double distXZ = Math.hypot(dx, dz);
        double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance() - 0.2D);
        double closeReach = 2.2D;
        boolean inCloseReach = (distXZ <= 1.6D || withinReach(dx, dy, dz, closeReach))
            && LootGamesBridge.topFaceVisible(mc, target);
        boolean inMaxReach = withinReach(dx, dy, dz, reach) && LootGamesBridge.topFaceVisible(mc, target);

        if (inCloseReach) {
            stop();
            return true;
        }

        double floorY = target[1] + 1;
        boolean inRoom = Math.abs(mc.thePlayer.posY - floorY) < 1.4D
            && distXZ < 8.0D
            && LootGamesBridge.topFaceVisible(mc, target);

        if (inRoom) {
            stopRoute();
            double speed = 0.30D;
            mc.thePlayer.motionX = (dx / distXZ) * speed;
            mc.thePlayer.motionZ = (dz / distXZ) * speed;
            mc.thePlayer.setSprinting(true);
            if (mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
            directMoving = true;
            return true;
        }

        directMoving = false;
        boolean arrivedAtGoal = goal == null || navigation.state() != BaritoneNavigationBridge.State.RUNNING
            || atApproachGoal(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY, mc.thePlayer.posZ, goal)
            || navigation.completed();
        boolean reachable = inCloseReach || (inMaxReach && arrivedAtGoal);
        if (!java.util.Arrays.equals(tile, target)) {
            if (!keepApproachForNewTile(
                mc,
                goal,
                target,
                reach,
                reachable,
                navigation.state() == BaritoneNavigationBridge.State.RUNNING)) stop();
            tile = target.clone();
            tried.clear();
        }
        if (reachable) {
            stopRoute();
            return true;
        }
        if (goal != null) {
            double goalDx = goal[0] + 0.5D - mc.thePlayer.posX;
            double goalDz = goal[2] + 0.5D - mc.thePlayer.posZ;
            boolean sprint = goalDx * goalDx + goalDz * goalDz > 0.49D && mc.thePlayer.onGround
                && !mc.thePlayer.isSneaking()
                && mc.thePlayer.getFoodStats()
                    .getFoodLevel() > 6;
            if (navigation.state() == BaritoneNavigationBridge.State.RUNNING) {
                navigation.setSprinting(sprint);
                if (sprint) {
                    mc.thePlayer.setSprinting(true);
                    if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                        net.minecraft.client.settings.KeyBinding
                            .setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true);
                    }
                }
            }
            double mx = mc.thePlayer.posX - lastX, mz = mc.thePlayer.posZ - lastZ;
            if (mx * mx + mz * mz >= 0.04D) {
                lastX = mc.thePlayer.posX;
                lastZ = mc.thePlayer.posZ;
                stalled = 0;
            } else stalled++;
            if (stalled >= 6 && stalled <= 18 && mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
            if (navigation.state() == BaritoneNavigationBridge.State.RUNNING && stalled < 30) return true;
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
            double distToTile = Math.hypot(cx, cz);
            if (distToTile < 0.8D || !withinReach(cx, mc.thePlayer.getEyeHeight() + 0.001D, cz, reach)
                || !LootGamesBridge.topFaceVisibleFrom(mc, target, x + 0.5D, y + mc.thePlayer.getEyeHeight(), z + 0.5D))
                continue;
            candidates.add(candidate);
        }
        candidates.sort((a, b) -> {
            double targetDistA = Math.hypot(a[0] + 0.5D - target[0] - 0.5D, a[2] + 0.5D - target[2] - 0.5D);
            double targetDistB = Math.hypot(b[0] + 0.5D - target[0] - 0.5D, b[2] + 0.5D - target[2] - 0.5D);
            double playerDistA = mc.thePlayer.getDistanceSq(a[0] + 0.5D, a[1], a[2] + 0.5D);
            double playerDistB = mc.thePlayer.getDistanceSq(b[0] + 0.5D, b[1], b[2] + 0.5D);
            return Double.compare(playerDistA + targetDistA * targetDistA * 2.0D, playerDistB + targetDistB * targetDistB * 2.0D);
        });
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
            if (result == BaritoneNavigationBridge.Start.BUSY) return ++busyTicks < 20;
            tried.add(candidate);
        }
        return false;
    }

    private void stopRoute() {
        if (goal != null) navigation.stop();
        goal = null;
        stalled = 0;
    }

    public static boolean withinReach(double dx, double dy, double dz, double reach) {
        return reach > 0 && dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    public static boolean keepApproachForNewTile(boolean reachable, boolean hasGoal, boolean running) {
        return reachable && hasGoal && running;
    }

    public static boolean keepApproachForNewTile(Minecraft mc, int[] goal, int[] newTarget, double reach, boolean reachable,
        boolean running) {
        if (!running || goal == null || newTarget == null) return false;
        if (reachable) return true;
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return false;
        double eyeHeight = mc.thePlayer.getEyeHeight();
        double cx = goal[0] + 0.5D - newTarget[0] - 0.5D;
        double cz = goal[2] + 0.5D - newTarget[2] - 0.5D;
        double cy = eyeHeight + 0.001D;
        if (!withinReach(cx, cy, cz, reach)) return false;
        return LootGamesBridge.topFaceVisibleFrom(mc, newTarget, goal[0] + 0.5D, goal[1] + eyeHeight, goal[2] + 0.5D);
    }

    public static boolean sameFeetBlock(double x, double y, double z, int[] pos) {
        return (int) Math.floor(x) == pos[0] && (int) Math.floor(y) == pos[1] && (int) Math.floor(z) == pos[2];
    }

    public static boolean atApproachGoal(double x, double y, double z, int[] pos) {
        double dx = x - pos[0] - 0.5D, dz = z - pos[2] - 0.5D;
        return Math.floor(y) == pos[1] && dx * dx + dz * dz <= 0.55D * 0.55D;
    }

    public void stop() {
        stopRoute();
        directMoving = false;
        tile = null;
        tried.clear();
        busyTicks = 0;
    }
}
