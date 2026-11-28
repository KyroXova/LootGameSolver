package com.kyroxova.lootgamesolver.minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.LootGameSolver;

import cpw.mods.fml.relauncher.ReflectionHelper;

public final class LootGamesTileAccess {

    public static final String LOOTGAMES_PKG = "ru.timeconqueror.lootgames.";
    public static final String MS_GAME = "ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper";
    public static final String SDK_GAME = "ru.timeconqueror.lootgames.minigame.sudoku.GameSudoku";
    public static final String GOL_GAME = "ru.timeconqueror.lootgames.minigame.gol.GameOfLight";
    public static final String POS = "ru.timeconqueror.lootgames.api.util.Pos2i";

    private static boolean subordinateReflectionInitialized = false;
    private static Class<?> cachedSubordinateClass = null;
    private static Method cachedGetMasterPosMethod = null;
    private static Method cachedBlockPosOfMethod = null;

    private static boolean posReflectionInitialized = false;
    private static Constructor<?> cachedPosConstructor = null;

    private LootGamesTileAccess() {}

    public static void initSubordinateReflection() {
        if (subordinateReflectionInitialized) return;
        subordinateReflectionInitialized = true;
        try {
            cachedSubordinateClass = Class.forName("ru.timeconqueror.lootgames.api.block.SmartSubordinateBlock");
            Class<?> blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
            cachedBlockPosOfMethod = blockPosClass.getMethod("of", int.class, int.class, int.class);
            cachedGetMasterPosMethod = cachedSubordinateClass.getMethod("getMasterPos", World.class, blockPosClass);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not initialize SmartSubordinateBlock reflection cache", e);
        }
    }

    public static void initPosReflection() {
        if (posReflectionInitialized) return;
        posReflectionInitialized = true;
        try {
            Class<?> posClass = Class.forName(POS);
            cachedPosConstructor = posClass.getConstructor(int.class, int.class);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not initialize Pos2i reflection constructor", e);
        }
    }

    public static Object createPos(int x, int y) throws Exception {
        initPosReflection();
        if (cachedPosConstructor != null) {
            return cachedPosConstructor.newInstance(Integer.valueOf(x), Integer.valueOf(y));
        }
        return Class.forName(POS)
            .getConstructor(int.class, int.class)
            .newInstance(Integer.valueOf(x), Integer.valueOf(y));
    }

    @SuppressWarnings("unchecked")
    public static Object getFieldValue(Object target, String fieldName) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                clazz.getDeclaredField(fieldName);
                return ReflectionHelper.getPrivateValue((Class<Object>) clazz, target, fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    public static Object invoke(Object target, String name, Object... args) throws Exception {
        Method[] methods = target.getClass()
            .getMethods();
        for (Method method : methods) {
            if (method.getName()
                .equals(name) && method.getParameterTypes().length == args.length) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(
            target.getClass()
                .getName() + "."
                + name);
    }

    public static boolean bool(Object target, String name) throws Exception {
        return ((Boolean) invoke(target, name)).booleanValue();
    }

    public static int number(Object value) {
        return ((Number) value).intValue();
    }

    public static boolean isMasterBlock(net.minecraft.block.Block block) {
        if (block == null) return false;
        String name = block.getClass()
            .getName();
        return name.endsWith(".PuzzleMasterBlock")
            || name.equals("ru.timeconqueror.lootgames.common.block.PuzzleMasterBlock");
    }

    public static boolean isMasterTile(String className) {
        return className != null && (className.endsWith(".PuzzleMasterTile")
            || className.equals("ru.timeconqueror.lootgames.common.block.tile.PuzzleMasterTile"));
    }

    public static boolean isFloorActivator(net.minecraft.block.Block block) {
        if (block == null) return false;
        return isFloorActivatorName(
            block.getClass()
                .getName());
    }

    public static boolean isFloorActivatorName(String name) {
        return name.equals(LOOTGAMES_PKG + "common.block.GOLActivatorBlock")
            || name.equals(LOOTGAMES_PKG + "common.block.MSActivatorBlock")
            || name.equals(LOOTGAMES_PKG + "common.block.SudokuActivatorBlock");
    }

    public static boolean isSmartSubordinate(net.minecraft.block.Block block) {
        if (block == null) return false;
        initSubordinateReflection();
        return cachedSubordinateClass != null && cachedSubordinateClass.isInstance(block);
    }

    public static int[] getMasterPos(World world, int x, int y, int z) {
        initSubordinateReflection();
        if (cachedSubordinateClass == null || cachedBlockPosOfMethod == null || cachedGetMasterPosMethod == null) {
            return null;
        }
        try {
            Object blockPos = cachedBlockPosOfMethod
                .invoke(null, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
            Object masterPos = cachedGetMasterPosMethod.invoke(null, world, blockPos);
            if (masterPos != null) {
                return new int[] { number(invoke(masterPos, "getX")), number(invoke(masterPos, "getY")),
                    number(invoke(masterPos, "getZ")) };
            }
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not resolve LootGames master position", e);
        }
        return null;
    }

    public static Object extractGameFromTile(World world, TileEntity tile, int x, int y, int z) {
        if (tile != null && !tile.isInvalid()
            && tile.getClass()
                .getName()
                .startsWith(LOOTGAMES_PKG)) {
            try {
                Method getGame = tile.getClass()
                    .getMethod("getGame");
                Object game = getGame.invoke(tile);
                if (game != null) {
                    String gName = game.getClass()
                        .getName();
                    if (MS_GAME.equals(gName) || SDK_GAME.equals(gName) || GOL_GAME.equals(gName)) return game;
                }
            } catch (Exception e) {
                LootGameSolver.LOG.debug("Could not extract LootGames instance from tile", e);
            }
        }

        net.minecraft.block.Block b = world.getBlock(x, y, z);
        if (b != null && b.getClass()
            .getName()
            .startsWith(LOOTGAMES_PKG)) {
            int[] masterPos = getMasterPos(world, x, y, z);
            if (masterPos != null) {
                TileEntity masterTile = world.getTileEntity(masterPos[0], masterPos[1], masterPos[2]);
                if (masterTile != null && !masterTile.isInvalid()
                    && masterTile.getClass()
                        .getName()
                        .startsWith(LOOTGAMES_PKG)) {
                    try {
                        Method getGame = masterTile.getClass()
                            .getMethod("getGame");
                        Object game = getGame.invoke(masterTile);
                        if (game != null) {
                            String gName = game.getClass()
                                .getName();
                            if (MS_GAME.equals(gName) || SDK_GAME.equals(gName) || GOL_GAME.equals(gName)) return game;
                        }
                    } catch (Exception e) {
                        LootGameSolver.LOG.debug("Could not extract LootGames instance from master tile", e);
                    }
                }
            }
        }
        return null;
    }

    public static boolean isPlayerInsideOrNearStructure(World world, EntityPlayer player) {
        int px = (int) Math.floor(player.posX);
        int py = (int) Math.floor(player.posY);
        int pz = (int) Math.floor(player.posZ);
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    net.minecraft.block.Block b = world.getBlock(px + dx, py + dy, pz + dz);
                    if (b != null && b.getClass()
                        .getName()
                        .startsWith(LOOTGAMES_PKG)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Object findNearbyGame(World world, EntityPlayer player, boolean forceScan) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            int bx = mc.objectMouseOver.blockX;
            int by = mc.objectMouseOver.blockY;
            int bz = mc.objectMouseOver.blockZ;
            double dx = bx + 0.5D - player.posX;
            double dy = by + 0.5D - player.posY;
            double dz = bz + 0.5D - player.posZ;
            if (Math.abs(dx) <= 32.0D && Math.abs(dz) <= 32.0D && Math.abs(dy) <= 8.0D) {
                net.minecraft.block.Block hitBlock = world.getBlock(bx, by, bz);
                if (hitBlock != null && hitBlock.getClass()
                    .getName()
                    .startsWith(LOOTGAMES_PKG)) {
                    TileEntity hitTile = world.getTileEntity(bx, by, bz);
                    Object game = extractGameFromTile(world, hitTile, bx, by, bz);
                    if (game != null) return game;
                }
            }
        }

        if (!forceScan && !isPlayerInsideOrNearStructure(world, player)) {
            return null;
        }

        Object selected = null;
        double selectedDistance = Double.MAX_VALUE;
        List<TileEntity> tiles = world.loadedTileEntityList;
        for (int i = 0; i < tiles.size(); i++) {
            TileEntity tile = tiles.get(i);
            if (tile == null || tile.isInvalid()) continue;

            if (!tile.getClass()
                .getName()
                .startsWith(LOOTGAMES_PKG)) continue;

            double tdx = tile.xCoord + 0.5D - player.posX;
            double tdy = tile.yCoord + 0.5D - player.posY;
            double tdz = tile.zCoord + 0.5D - player.posZ;
            if (Math.abs(tdx) > 32.0D || Math.abs(tdz) > 32.0D || Math.abs(tdy) > 8.0D) continue;

            Object game = extractGameFromTile(world, tile, tile.xCoord, tile.yCoord, tile.zCoord);
            if (game == null) continue;
            double distance = tdx * tdx + tdy * tdy + tdz * tdz;
            if (distance < selectedDistance) {
                selected = game;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    public static int[] nearbyMasterBlock(Minecraft mc, int[] center) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int px = center != null ? center[0] : (int) Math.floor(mc.thePlayer.posX);
        int py = center != null ? center[1] : (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = center != null ? center[2] : (int) Math.floor(mc.thePlayer.posZ);
        int[] closest = null;
        double best = Double.MAX_VALUE;
        int minDy = center != null ? 0 : -2;
        int maxDy = center != null ? 4 : 5;
        int r = center != null ? 3 : 16;
        for (int dy = minDy; dy <= maxDy; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (!mc.theWorld.blockExists(x, y, z)) continue;
                    net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
                    boolean isMaster = isMasterBlock(block);
                    if (!isMaster) {
                        TileEntity te = mc.theWorld.getTileEntity(x, y, z);
                        if (te != null && isMasterTile(
                            te.getClass()
                                .getName()))
                            isMaster = true;
                    }
                    if (isMaster) {
                        double distance = mc.thePlayer.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (distance < best) {
                            closest = new int[] { x, y, z };
                            best = distance;
                        }
                    }
                }
            }
        }
        if (closest != null) return closest;
        for (Object obj : mc.theWorld.loadedTileEntityList) {
            if (!(obj instanceof TileEntity)) continue;
            TileEntity te = (TileEntity) obj;
            if (te.isInvalid()) continue;
            if (isMasterTile(
                te.getClass()
                    .getName())) {
                double distance = mc.thePlayer.getDistanceSq(te.xCoord + 0.5D, te.yCoord + 0.5D, te.zCoord + 0.5D);
                if (distance <= 256.0D && distance < best) {
                    closest = new int[] { te.xCoord, te.yCoord, te.zCoord };
                    best = distance;
                }
            }
        }
        return closest;
    }

    public static int[] nearbyFloorActivator(Minecraft mc) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int px = (int) Math.floor(mc.thePlayer.posX), py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int[] closest = null;
        double best = Double.MAX_VALUE;
        for (int dy = -4; dy <= 2; dy++) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    int x = px + dx, y = py + dy, z = pz + dz;
                    if (!mc.theWorld.blockExists(x, y, z) || !isFloorActivator(mc.theWorld.getBlock(x, y, z))) continue;
                    double distance = dx * dx + dy * dy + dz * dz;
                    if (distance < best) {
                        closest = new int[] { x, y, z };
                        best = distance;
                    }
                }
            }
        }
        return closest;
    }

    public static Object gameAtMaster(Minecraft mc, int[] position) {
        if (position == null || position.length < 3
            || mc.theWorld == null
            || !mc.theWorld.blockExists(position[0], position[1], position[2])) return null;
        TileEntity tile = mc.theWorld.getTileEntity(position[0], position[1], position[2]);
        return tile == null || tile.isInvalid() ? null
            : extractGameFromTile(mc.theWorld, tile, position[0], position[1], position[2]);
    }
}
