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
    private int toolWaitTicks;
    private String status = "Scanning loaded terrain";
    private ProgressiveSearch search;
    private boolean searching;
    private NavigationInput navigationInput;
    private int stairX;
    private int stairZ;
    private int stairSteps;
    private boolean seekingDryEntrance;

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
        if (walking && mc.thePlayer != null && navigationInput == null) {
            navigationInput = new NavigationInput(mc.thePlayer.movementInput);
            mc.thePlayer.movementInput = navigationInput;
        }
        if (navigationInput != null) {
            navigationInput.forward = 0;
            navigationInput.sideways = 0;
            navigationInput.hop = false;
        }
        if (world != mc.theWorld) {
            if (navigationInput != null) stop(mc, "World changed");
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
        if (walking && searching && ticks % 40 == 0) {
            TileEntity previous = target;
            scan(mc);
            if (target != null) {
                searching = false;
                route.clear();
            } else target = previous;
        }
        if (!walking) return;
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled
            || solverActive
            || mc.currentScreen != null
            || mc.thePlayer.hurtTime > 0
            || (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 100)
            || mc.thePlayer.capabilities.isFlying
            || GameSettings.isKeyDown(mc.gameSettings.keyBindForward)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindBack)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindLeft)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindRight)
            || GameSettings.isKeyDown(mc.gameSettings.keyBindSneak)) {
            stop(mc, "Walk cancelled");
            return;
        }
        if (target == null || target.isInvalid()) {
            stop(mc, "Target no longer loaded");
            return;
        }
        if (route.isEmpty()) {
            if (seekingDryEntrance) {
                seekingDryEntrance = false;
                digging = true;
                savedSlot = mc.thePlayer.inventory.currentItem;
            }
            if ((digging || target.yCoord < mc.thePlayer.boundingBox.minY - 2) && !dryShore(
                mc.theWorld,
                (int) Math.floor(mc.thePlayer.posX),
                (int) Math.floor(mc.thePlayer.boundingBox.minY),
                (int) Math.floor(mc.thePlayer.posZ))) {
                digging = false;
                if (!findDryEntrance(mc)) stop(mc, "No verified dry entrance near the water; digging stopped");
                return;
            }
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
        double dx = next[0] + 0.5D - mc.thePlayer.posX;
        double dz = next[2] + 0.5D - mc.thePlayer.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.30D && (!mc.thePlayer.onGround && !mc.thePlayer.isInWater())) {
            mc.thePlayer.setSprinting(false);
            mc.thePlayer.motionX *= 0.4D;
            mc.thePlayer.motionZ *= 0.4D;
            return;
        }
        if (distance < (route.size() > 1 ? 0.35D : 0.18D)
            && (py == next[1] || (mc.thePlayer.isInWater() && Math.abs(py - next[1]) <= 1))
            && (mc.thePlayer.onGround || mc.thePlayer.isInWater())) {
            route.remove(0);
            lastDistance = Double.MAX_VALUE;
            stalled = 0;
            if (!route.isEmpty() && navigationInput != null) navigationInput.forward = 0.7F;
            return;
        }
        if (distance >= lastDistance - 0.005D) stalled++;
        else stalled = 0;
        lastDistance = distance;
        if (stalled > 40) {
            stop(mc, "Walk stopped: no progress");
            return;
        }
        float desired = digging && (stairX != 0 || stairZ != 0)
            ? (float) (Math.atan2(stairZ, stairX) * 180D / Math.PI) - 90F
            : (float) (Math.atan2(dz, dx) * 180D / Math.PI) - 90F;
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(desired - mc.thePlayer.rotationYaw);
        if (!digging && next[1] == py) mc.thePlayer.rotationYaw = net.minecraft.util.MathHelper
            .wrapAngleTo180_float(mc.thePlayer.rotationYaw + Math.max(-12F, Math.min(12F, turn)));
        boolean straight = route.size() >= 3 && !digging
            && !mc.thePlayer.isInWater()
            && next[1] == py
            && route.get(1)[1] == py
            && route.get(2)[1] == py
            && route.get(2)[0] - next[0] == 2 * (route.get(1)[0] - next[0])
            && route.get(2)[2] - next[2] == 2 * (route.get(1)[2] - next[2])
            && safe(mc.theWorld, route.get(1)[0], py, route.get(1)[2])
            && safe(mc.theWorld, route.get(2)[0], py, route.get(2)[2]);
        mc.thePlayer.setSprinting(
            straight && Math.abs(turn) < 10F
                && mc.thePlayer.getFoodStats()
                    .getFoodLevel() > 6);
        if (navigationInput != null) {
            navigationInput.forward = Math.abs(turn) > 35F ? 0F
                : route.size() == 1 ? (float) Math.min(0.65D, distance * 1.5D) : 1F;
            navigationInput.hop = next[1] > py || mc.thePlayer.isInWater();
            if ((digging || next[1] != py) && distance > 0.02D) {
                double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                double speed = Math.min(0.5D, distance);
                navigationInput.forward = (float) ((-dx * Math.sin(yaw) + dz * Math.cos(yaw)) / distance * speed);
                navigationInput.sideways = (float) ((dx * Math.cos(yaw) + dz * Math.sin(yaw)) / distance * speed);
            }
        }
    }

    private void stop(Minecraft mc, String message) {
        if (navigationInput != null && mc.thePlayer != null) {
            if (mc.thePlayer.movementInput == navigationInput) mc.thePlayer.movementInput = navigationInput.original;
            navigationInput = null;
            mc.thePlayer.setSprinting(false);
        }
        if (digging && mc.playerController != null) mc.playerController.resetBlockRemoving();
        if (savedSlot >= 0 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        digging = false;
        stairX = 0;
        stairZ = 0;
        stairSteps = 0;
        seekingDryEntrance = false;
        digTicks = 0;
        toolWaitTicks = 0;
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
        for (int attempt = 0; attempt < 4; attempt++) {
            if (attemptStairStep(mc)) return true;
            if (digTicks > 200) return false;
            int previousX = stairX;
            stairX = stairZ == 0 && previousX == 0 ? 1 : -stairZ;
            stairZ = previousX;
        }
        return false;
    }

    private static boolean dryShore(World world, int x, int y, int z) {
        if (!passable(world, x, y, z) || !world.getBlock(x, y - 1, z)
            .isNormalCube()) return false;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -2; dy <= 1; dy++) {
            if (!world.blockExists(x + dx, y + dy, z + dz) || world.getBlock(x + dx, y + dy, z + dz)
                .getMaterial()
                .isLiquid()) return false;
        }
        return true;
    }

    private boolean findDryEntrance(final Minecraft mc) {
        final World w = mc.theWorld;
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        int attempts = 0;
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int radius = 3; radius <= 12; radius += 3)
            for (int[] direction : directions) for (int dy = 0; dy <= 1; dy++) {
                int gx = x + direction[0] * radius, gy = y + dy, gz = z + direction[1] * radius;
                if (!dryShore(w, gx, gy, gz) || !safe(w, gx, gy, gz)
                    || !passable(w, gx, gy, gz)
                    || !w.getBlock(gx, gy - 1, gz)
                        .isNormalCube())
                    continue;
                if (++attempts > 4) return false;
                List<int[]> candidate = new GroundPathfinder().find(new GroundPathfinder.Terrain() {

                    public boolean stand(int a, int b, int c) {
                        return safe(w, a, b, c);
                    }

                    public boolean clear(int a, int b, int c) {
                        return passable(w, a, b, c);
                    }
                }, x, y, z, gx, gz, 16);
                if (candidate.isEmpty()) continue;
                int[] last = candidate.get(candidate.size() - 1);
                if (!dryShore(w, last[0], last[1], last[2]) || !passable(w, last[0], last[1], last[2])
                    || !w.getBlock(last[0], last[1] - 1, last[2])
                        .isNormalCube())
                    continue;
                route.clear();
                route.addAll(candidate);
                seekingDryEntrance = true;
                searching = false;
                stairX = 0;
                stairZ = 0;
                status = "Moving to dry staircase entrance";
                return true;
            }
        return false;
    }

    private boolean attemptStairStep(Minecraft mc) {
        int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int z = (int) Math.floor(mc.thePlayer.posZ);
        if (!mc.thePlayer.onGround || !safe(mc.theWorld, x, y, z)) return false;
        if (Math.abs(mc.thePlayer.posX - x - 0.5D) > 0.2D || Math.abs(mc.thePlayer.posZ - z - 0.5D) > 0.2D) {
            route.add(new int[] { x, y, z });
            return true;
        }
        if (stairX == 0 && stairZ == 0) {
            stairX = Integer.signum(target.xCoord - x);
            stairZ = Integer.signum(target.zCoord - z);
            if (Math.abs(target.xCoord - x) >= Math.abs(target.zCoord - z)) stairZ = 0;
            else stairX = 0;
            if (stairX == 0 && stairZ == 0) stairX = 1;
        }
        int nx = x + stairX, nz = z + stairZ;
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
            if (passable(w, nx, by, nz)) continue;
            if (b != Blocks.stone && b != Blocks.dirt && b != Blocks.grass && b != Blocks.cobblestone) return false;
            if (w.getTileEntity(nx, by, nz) != null || b.getBlockHardness(w, nx, by, nz) < 0) return false;
        }
        for (int by = y + 1; by >= y - 1; by--) {
            net.minecraft.block.Block b = w.getBlock(nx, by, nz);
            if (passable(w, nx, by, nz)) continue;
            int slot = DigToolSelector.equip(mc, b, w.getBlockMetadata(nx, by, nz));
            if (slot == -2) {
                if (++toolWaitTicks > 40) {
                    stop(
                        mc,
                        "\u00A7eAuto-dig paused: inventory swap could not finish. Close other inventories and clear the cursor.");
                    return true;
                }
                status = "Preparing digging tool...";
                return true;
            }
            toolWaitTicks = 0;
            if (slot < 0) {
                stop(
                    mc,
                    "\u00A7eAuto-dig paused: \u00A7fKeep a usable \u00A7bpickaxe\u00A7f and \u00A7bshovel\u00A7f in your inventory. Check tool durability and harvest level.");
                return true;
            }
            if (++digTicks > 200) return false;
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.updateController();
            if (mc.thePlayer.getDistanceSq(nx + 0.5D, by + 0.5D, nz + 0.5D) > 16D) return false;
            mc.thePlayer.swingItem();
            mc.playerController.onPlayerDamageBlock(nx, by, nz, 1);
            status = "Digging verified staircase step";
            return true;
        }
        digTicks = 0;
        if (!safe(w, nx, y - 1, nz)) return false;
        route.add(new int[] { nx, y - 1, nz });
        stairSteps++;
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
                if ((block.getMaterial()
                    .isLiquid() && block.getMaterial() != net.minecraft.block.material.Material.water)
                    || block == Blocks.fire
                    || block == Blocks.cactus
                    || block == Blocks.web
                    || block == Blocks.portal) return false;
            }
        }
        if (world.getBlock(x, y, z)
            .getMaterial() == net.minecraft.block.material.Material.water && passable(world, x, y + 1, z)
            && passable(world, x, y + 2, z)) return true;
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
