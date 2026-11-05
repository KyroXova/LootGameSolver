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
    private boolean digging;
    private int savedSlot = -1;
    private int digTicks;
    private String status = "Scanning loaded terrain";

    public void toggle(Minecraft mc) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
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
        digging = route.isEmpty() && LootGameSolverConfig.staircaseEnabled
            && target.yCoord < mc.thePlayer.boundingBox.minY - 2;
        walking = !route.isEmpty() || digging;
        if (digging) savedSlot = mc.thePlayer.inventory.currentItem;
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        if (!walking) stop(mc, "No safe route to target");
        else status = "Walking to LootGame";
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
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled
            || solverActive
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
            if (digging && target.yCoord < mc.thePlayer.boundingBox.minY - 2) {
                if (!stairStep(mc)) stop(mc, "No safe staircase step; stopped");
                return;
            }
            if (digging) {
                digging = false;
                plan(mc);
                if (!route.isEmpty()) return;
            }
            stop(mc, "Arrived beside LootGame");
            return;
        }
        int[] next = route.get(0);
        int px = (int) Math.floor(mc.thePlayer.posX);
        int py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        if (!safe(mc.theWorld, px, py, pz) || !safe(mc.theWorld, next[0], next[1], next[2])
            || (py != next[1] && !(digging && py == next[1] + 1))
            || (!mc.thePlayer.onGround && !digging)
            || mc.thePlayer.isCollidedHorizontally) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (!digging && mc.thePlayer.onGround && safe(mc.theWorld, px, py, pz)) {
                plan(mc);
                if (!route.isEmpty()) {
                    lastDistance = Double.MAX_VALUE;
                    stalled = 0;
                    status = "Rerouted around obstruction";
                    return;
                }
            }
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
        if (digging && mc.playerController != null) mc.playerController.resetBlockRemoving();
        if (savedSlot >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        digging = false;
        digTicks = 0;
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
                boolean unopened = tile.getClass()
                    .getName()
                    .equals("ru.timeconqueror.lootgames.common.block.tile.PuzzleMasterTile");
                Object game = unopened ? tile
                    : tile.getClass()
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
                if (Math.abs(nx - x) > LootGameSolverConfig.explorationRange
                    || Math.abs(nz - z) > LootGameSolverConfig.explorationRange
                    || parents.containsKey(key)) continue;
                if (!safe(mc.theWorld, nx, y, nz)) continue;
                parents.put(key, node);
                queue.add(new int[] { nx, y, nz });
            }
        }
    }

    private boolean stairStep(Minecraft mc) {
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround || !safe(mc.theWorld, x, y, z)) return false;
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            route.add(new int[] { x, y, z });
            return true;
        }
        int dx = Integer.signum(target.xCoord - x), dz = Integer.signum(target.zCoord - z);
        if (Math.abs(target.xCoord - x) >= Math.abs(target.zCoord - z)) dz = 0;
        else dx = 0;
        if (dx == 0 && dz == 0) return false;
        int nx = x + dx, nz = z + dz;
        World w = mc.theWorld;
        // Only excavate beside the player, with a verified full floor below the future step.
        if (!w.blockExists(nx, y - 2, nz) || !w.getBlock(nx, y - 2, nz)
            .isNormalCube()) return false;
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) for (int oy = -2; oy <= 2; oy++) {
            if (!w.blockExists(nx + ox, y + oy, nz + oz)) return false;
            net.minecraft.block.Block b = w.getBlock(nx + ox, y + oy, nz + oz);
            if (b.getMaterial()
                .isLiquid() || b == Blocks.fire
                || b == Blocks.cactus
                || b == Blocks.sand
                || b == Blocks.gravel
                || b == Blocks.portal) return false;
        }
        for (int by = y + 1; by >= y - 1; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (w.isAirBlock(nx, by, nz)) continue;
            if (b != Blocks.stone && b != Blocks.dirt && b != Blocks.grass && b != Blocks.cobblestone) return false;
            if (w.getTileEntity(nx, by, nz) != null || b.getBlockHardness(w, nx, by, nz) < 0) return false;
        }
        for (int by = y + 1; by >= y - 1; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (w.isAirBlock(nx, by, nz)) continue;
            int slot = -1;
            float speed = 1F;
            for (int i = 0; i < 9; i++) {
                net.minecraft.item.ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack == null || !stack.getItem()
                    .canHarvestBlock(b, stack)
                    || (stack.isItemStackDamageable() && stack.getMaxDamage() - stack.getItemDamage() < 10)) continue;
                float candidate = stack.getItem()
                    .getDigSpeed(stack, b, w.getBlockMetadata(nx, by, nz));
                if (candidate > speed) {
                    speed = candidate;
                    slot = i;
                }
            }
            if (slot < 0 || ++digTicks > 200) return false;
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            mc.thePlayer.inventory.currentItem = slot;
            if (mc.thePlayer.getDistanceSq(nx + 0.5D, by + 0.5D, nz + 0.5D) > 16D) return false;
            mc.thePlayer.swingItem();
            mc.playerController.onPlayerDamageBlock(nx, by, nz, 1);
            status = "Digging verified staircase step";
            return true;
        }
        digTicks = 0;
        if (!safe(w, nx, y - 1, nz)) return false;
        route.add(new int[] { nx, y - 1, nz });
        lastDistance = Double.MAX_VALUE;
        stalled = 0;
        return true;
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
        if (target == null) return "No structure in loaded terrain";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
