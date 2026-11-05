package com.kyroxova.lootgamesolver.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

/** Loaded-terrain exploration. Routes deliberately require level, open ground. */
public final class ExplorationBeta {

    // Local testing only. Set false before pushing or publishing.
    public static final boolean ENABLED = true;
    private final List<int[]> route = new ArrayList<int[]>();
    private World world;
    private TileEntity target;
    private int ticks;
    private int stalled;
    private double lastDistance = Double.MAX_VALUE;
    private boolean walking;
    private String status = "Scanning loaded terrain";

    public void toggle(Minecraft mc) {
        if (!LootGameSolverConfig.enabled) return;
        if (walking) {
            stop(mc, "Walk stopped");
            return;
        }
        scan(mc);
        if (target == null) {
            stop(mc, "No loaded LootGames board nearby");
            return;
        }
        plan(mc);
        walking = !route.isEmpty();
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        if (!walking) stop(mc, "No safe level route within 32 blocks");
        else status = "Walking to LootGame — press G to stop";
    }

    public void tick(Minecraft mc, boolean solverActive) {
        if (world != mc.theWorld) {
            world = mc.theWorld;
            target = null;
            walking = false;
            route.clear();
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (++ticks % 100 == 0 && !walking) scan(mc);
        if (!walking) return;
        if (!LootGameSolverConfig.enabled || solverActive
            || mc.currentScreen != null
            || mc.thePlayer.hurtTime > 0
            || mc.thePlayer.isInWater()
            || mc.thePlayer.capabilities.isFlying
            || GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindRight)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindJump)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            stop(mc, "Walk cancelled");
            return;
        }
        if (target == null || target.isInvalid()
            || !mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
            stop(mc, "Target no longer loaded");
            return;
        }
        if (route.isEmpty()) {
            stop(mc, "Arrived beside LootGame");
            return;
        }
        int[] next = route.get(0);
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (!safe(mc.theWorld, px, py, pz) || !safe(mc.theWorld, next[0], next[1], next[2])
            || py != next[1]
            || !mc.thePlayer.onGround
            || mc.thePlayer.isCollidedHorizontally) {
            stop(mc, "Route obstructed or unsafe");
            return;
        }
        double dx = next[0] + 0.5D - mc.thePlayer.posX;
        double dz = next[2] + 0.5D - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.15D) {
            route.remove(0);
            lastDistance = Double.MAX_VALUE;
            stalled = 0;
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            return;
        }
        if (distance >= lastDistance - 0.005D) stalled++;
        else stalled = 0;
        lastDistance = distance;
        if (stalled > 40) {
            stop(mc, "Walk stopped: no progress");
            return;
        }
        mc.thePlayer.rotationYaw = (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
        double speed = Math.min(0.10D, distance);
        mc.thePlayer.motionX = dx / distance * speed;
        mc.thePlayer.motionZ = dz / distance * speed;
    }

    private void stop(Minecraft mc, String message) {
        if (walking && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
        }
        walking = false;
        route.clear();
        status = message;
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[LootGames Beta] " + message));
    }

    private void scan(Minecraft mc) {
        target = null;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        double nearest = 128D * 128D;
        for (Object entry : mc.theWorld.loadedTileEntityList) {
            if (!(entry instanceof TileEntity)) continue;
            TileEntity tile = (TileEntity) entry;
            if (tile.isInvalid() || !tile.getClass()
                .getName()
                .startsWith("ru.timeconqueror.lootgames.")) continue;
            try {
                Object game = tile.getClass()
                    .getMethod("getGame")
                    .invoke(tile);
                if (game == null) continue;
                double distance = mc.thePlayer
                    .getDistanceSq(tile.xCoord + 0.5D, tile.yCoord + 0.5D, tile.zCoord + 0.5D);
                if (distance < nearest) {
                    nearest = distance;
                    target = tile;
                }
            } catch (ReflectiveOperationException ignored) {}
        }
    }

    private void plan(Minecraft mc) {
        route.clear();
        int x = (int) Math.floor(mc.thePlayer.posX);
        int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!safe(mc.theWorld, x, y, z)) return;
        ArrayDeque<int[]> queue = new ArrayDeque<int[]>();
        Map<Long, int[]> parents = new HashMap<Long, int[]>();
        queue.add(new int[] { x, y, z });
        parents.put(key(x, z), null);
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!queue.isEmpty() && parents.size() <= 4096) {
            int[] node = queue.remove();
            double dx = node[0] + 0.5D - target.xCoord;
            double dz = node[2] + 0.5D - target.zCoord;
            if (dx * dx + dz * dz <= 9D && Math.abs(y - target.yCoord) <= 3) {
                while (parents.get(key(node[0], node[2])) != null) {
                    route.add(node);
                    node = parents.get(key(node[0], node[2]));
                }
                Collections.reverse(route);
                return;
            }
            for (int[] direction : directions) {
                int nx = node[0] + direction[0], nz = node[2] + direction[1];
                long key = key(nx, nz);
                if (Math.abs(nx - x) > 32 || Math.abs(nz - z) > 32 || parents.containsKey(key)) continue;
                if (!safe(mc.theWorld, nx, y, nz)) continue;
                parents.put(key, node);
                queue.add(new int[] { nx, y, nz });
            }
        }
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static boolean safe(World world, int x, int y, int z) {
        // Open headroom and a full floor; reject nearby liquids, fire, cactus and unknown terrain.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 2; dy++) {
                if (!world.blockExists(x + dx, y + dy, z + dz)) return false;
                net.minecraft.block.Block block = world.getBlock(x + dx, y + dy, z + dz);
                if (block.getMaterial()
                    .isLiquid() || block == Blocks.fire
                    || block == Blocks.cactus
                    || block == Blocks.web
                    || block == Blocks.portal) return false;
            }
        }
        return world.getBlock(x, y - 1, z)
            .isNormalCube() && world.isAirBlock(x, y, z)
            && world.isAirBlock(x, y + 1, z)
            && world.isAirBlock(x, y + 2, z);
    }

    public String hud(Minecraft mc) {
        if (target == null) return "LootGames Beta: no loaded board | G: safe walk";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return "LootGames Beta: " + target.xCoord
            + ", "
            + target.yCoord
            + ", "
            + target.zCoord
            + " ("
            + distance
            + "m) | G: safe walk | "
            + status;
    }
}
