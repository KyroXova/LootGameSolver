package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.List;

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
    private SavedStructureFinder savedFinder;
    private TileEntity target;
    private int ticks;
    private int stalled;
    private double lastDistance = Double.MAX_VALUE;
    private boolean walking;
    private boolean digging;
    private int savedSlot = -1;
    private int digTicks;
    private String status = "Scanning loaded terrain";
    private ProgressiveSearch search;
    private boolean searching;

    public void toggle(Minecraft mc) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) return;
        if (walking) {
            stop(mc, "Walk stopped");
            return;
        }
        search = new ProgressiveSearch(
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.posZ),
            LootGameSolverConfig.explorationSearchLimit);
        scan(mc);
        if (target == null) {
            walking = nextSearchLeg(mc);
            if (!walking) stop(mc, "No safe exploration route available");
            return;
        }
        plan(mc);
        digging = route.isEmpty() && LootGameSolverConfig.staircaseEnabled
            && target.yCoord < mc.thePlayer.boundingBox.minY - 2
            && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord);
        walking = !route.isEmpty() || digging;
        if (digging) savedSlot = mc.thePlayer.inventory.currentItem;
        stalled = 0;
        lastDistance = Double.MAX_VALUE;
        if (!walking) {
            walking = nextSearchLeg(mc);
            if (!walking) stop(mc, "No safe exploration route available");
        } else status = "Walking to LootGame";
    }

    public void tick(Minecraft mc, boolean solverActive) {
        if (world != mc.theWorld) {
            if (savedFinder != null) savedFinder.cancel();
            savedFinder = null;
            world = mc.theWorld;
            target = null;
            walking = false;
            route.clear();
            search = null;
            searching = false;
            if (world != null && mc.isSingleplayer() && mc.getIntegratedServer() != null) {
                java.io.File save = new java.io.File(
                    new java.io.File(mc.mcDataDir, "saves"),
                    mc.getIntegratedServer()
                        .getFolderName());
                String dimension = world.provider.getSaveFolder();
                if (dimension != null) save = new java.io.File(save, dimension);
                savedFinder = new SavedStructureFinder(new java.io.File(save, "region"));
            }
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
        if (target == null || target.isInvalid()) {
            stop(mc, "Target no longer loaded");
            return;
        }
        if (route.isEmpty()) {
            if (!searching && !digging) {
                double horizontal = Math.abs(mc.thePlayer.posX - target.xCoord)
                    + Math.abs(mc.thePlayer.posZ - target.zCoord);
                if (horizontal > 3) {
                    plan(mc);
                    if (!route.isEmpty()) return;
                    stop(mc, "No verified route around the obstruction");
                    return;
                }
                if (LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2) {
                    digging = true;
                    savedSlot = mc.thePlayer.inventory.currentItem;
                }
            }
            if (searching) {
                scan(mc);
                if (target != null) {
                    searching = false;
                    if (mc.thePlayer.getDistanceSq(target.xCoord + 0.5D, target.yCoord + 0.5D, target.zCoord + 0.5D)
                        <= 16D) {
                        stop(mc, "Found nearby LootGame");
                        return;
                    }
                    plan(mc);
                    if (!route.isEmpty()) return;
                    if (LootGameSolverConfig.staircaseEnabled && target.yCoord < mc.thePlayer.boundingBox.minY - 2
                        && mc.theWorld.blockExists(target.xCoord, target.yCoord, target.zCoord)) {
                        digging = true;
                        savedSlot = mc.thePlayer.inventory.currentItem;
                        return;
                    }
                }
                if (!nextSearchLeg(mc)) stop(mc, "Search limit reached or no safe route remains");
                return;
            }
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
        if (!safe(mc.theWorld, next[0], next[1], next[2]) || Math.abs(py - next[1]) > 1

        ) {
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
        if (next[1] > py && mc.thePlayer.onGround) mc.thePlayer.jump();
        double dx = next[0] + 0.5D - mc.thePlayer.posX;
        double dz = next[2] + 0.5D - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.15D && py == next[1] && mc.thePlayer.onGround) {
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
        if (distance < 0.02D) return;
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
        searching = false;
        route.clear();
        status = message;
        if (mc.thePlayer != null) mc.thePlayer.addChatMessage(new ChatComponentText("[LootGames Beta] " + message));
    }

    private void scan(Minecraft mc) {
        target = null;
        if (mc.theWorld == null || mc.thePlayer == null) return;
        double nearest = Double.MAX_VALUE;
        if (savedFinder != null) {
            for (int[] position : savedFinder.positions()) {
                double distance = mc.thePlayer
                    .getDistanceSq(position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
                if (distance < nearest) {
                    if (mc.theWorld.blockExists(position[0], position[1], position[2])
                        && !(mc.theWorld.getTileEntity(position[0], position[1], position[2]) instanceof TileEntity))
                        continue;
                    nearest = distance;
                    target = new TileEntity();
                    target.xCoord = position[0];
                    target.yCoord = position[1];
                    target.zCoord = position[2];
                }
            }
        }
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

    private boolean nextSearchLeg(Minecraft mc) {
        if (search == null) return false;
        // Bound pathfinding work per decision; stop rather than spin on unreachable goals.
        for (int attempt = 0; attempt < 4; attempt++) {
            int[] goal = search.next();
            if (goal == null) return false;
            int x = (int) Math.floor(mc.thePlayer.posX), z = (int) Math.floor(mc.thePlayer.posZ);
            int y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            double dx = goal[0] - x, dz = goal[1] - z;
            double length = Math.sqrt(dx * dx + dz * dz);
            if (length < 4) continue;
            double step = Math.min(16D, length);
            target = new TileEntity();
            target.xCoord = x + (int) Math.round(dx / length * step);
            target.yCoord = y;
            target.zCoord = z + (int) Math.round(dz / length * step);
            if (!mc.theWorld.blockExists(target.xCoord, y, target.zCoord)) continue;
            plan(mc);
            if (route.isEmpty()) continue;
            searching = true;
            digging = false;
            stalled = 0;
            lastDistance = Double.MAX_VALUE;
            status = "Exploring outward";
            return true;
        }
        return false;
    }

    private void plan(final Minecraft mc) {
        route.clear();
        route.addAll(new GroundPathfinder().find(new GroundPathfinder.Terrain() {

            public boolean stand(int x, int y, int z) {
                return safe(mc.theWorld, x, y, z);
            }

            public boolean clear(int x, int y, int z) {
                return passable(mc.theWorld, x, y, z);
            }
        },
            (int) Math.floor(mc.thePlayer.posX),
            (int) Math.floor(mc.thePlayer.boundingBox.minY),
            (int) Math.floor(mc.thePlayer.posZ),
            target.xCoord,
            target.zCoord,
            LootGameSolverConfig.explorationRange));
    }

    private static boolean passable(World world, int x, int y, int z) {
        return world.blockExists(x, y, z) && !world.getBlock(x, y, z)
            .getMaterial()
            .isLiquid()
            && world.getBlock(x, y, z)
                .getCollisionBoundingBoxFromPool(world, x, y, z) == null;
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
            .isNormalCube() && passable(world, x, y, z)
            && passable(world, x, y + 1, z)
            && passable(world, x, y + 2, z);
    }

    public String hud(Minecraft mc) {
        if (target == null) return "Searching nearby";
        int distance = (int) Math.sqrt(mc.thePlayer.getDistanceSq(target.xCoord, target.yCoord, target.zCoord));
        return target.xCoord + ", " + target.yCoord + ", " + target.zCoord + " (" + distance + "m)";
    }
}
