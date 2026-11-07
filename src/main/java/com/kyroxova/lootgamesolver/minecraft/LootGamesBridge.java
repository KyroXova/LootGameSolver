package com.kyroxova.lootgamesolver.minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.LootGameSolver;
import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.detection.MiniGameDetector;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperCell;
import com.kyroxova.lootgamesolver.solver.sudoku.SudokuBoard;

public final class LootGamesBridge implements MiniGameDetector {

    private boolean pendingSneakRelease;
    private boolean restoreSneaking;

    private static final String LOOTGAMES_PKG = "ru.timeconqueror.lootgames.";
    private static final String MS_GAME = "ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper";
    private static final String SDK_GAME = "ru.timeconqueror.lootgames.minigame.sudoku.GameSudoku";
    private static final String GOL_GAME = "ru.timeconqueror.lootgames.minigame.gol.GameOfLight";
    private static final String POS = "ru.timeconqueror.lootgames.api.util.Pos2i";

    private Object golGame;
    private Object golStage;
    private Object golPendingStage;
    private Object golPendingAppearance;
    private String golPendingAppearanceState;
    private int golPendingSymbol;
    private boolean golActionPending;
    private boolean golActionRejected;
    private boolean golAcceptedAwaitingRefresh;
    private String golRejectedStageId;
    private List<CellPosition> golSequence = new ArrayList<CellPosition>();
    private int golSubmitted;

    private static boolean subordinateReflectionInitialized = false;
    private static Class<?> cachedSubordinateClass = null;
    private static Method cachedGetMasterPosMethod = null;
    private static Method cachedBlockPosOfMethod = null;

    private static boolean posReflectionInitialized = false;
    private static Constructor<?> cachedPosConstructor = null;

    private static void initSubordinateReflection() {
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

    private static Field getFieldRecursive(Class<?> clazz, String fieldName) {
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static float updateRotation(float current, float target, float maxStep) {
        float diff = current - target;
        while (diff >= 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current - diff;
    }

    private static boolean preparePlayerPositionAndAim(EntityPlayer player, int x, int y, int z) {
        if (player == null) return false;
        double dx = x + 0.5D - player.posX;
        double dy = y + 0.5D - (player.posY + player.getEyeHeight());
        double dz = z + 0.5D - player.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));

        player.rotationYaw = updateRotation(player.rotationYaw, yaw, 35.0F);
        player.rotationPitch = updateRotation(player.rotationPitch, pitch, 25.0F);

        if (distSq > 17.64D) {
            double speed = 0.15D;
            if (distXZ > 0.1D) {
                player.motionX += (dx / distXZ) * speed;
                player.motionZ += (dz / distXZ) * speed;
            }
            if (player.capabilities.allowFlying) {
                player.capabilities.isFlying = true;
                if (Math.abs(dy) > 1.2D) {
                    player.motionY = dy > 0 ? 0.12D : -0.12D;
                }
            }
            return false;
        }
        return Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw)) < 2F
            && Math.abs(pitch - player.rotationPitch) < 2F;
    }

    private static boolean prepareExactCenterAndAim(EntityPlayer player, int x, int y, int z) {
        if (player == null) return false;
        double dx = x + 0.5D - player.posX, dz = z + 0.5D - player.posZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int footY = (int) Math.floor(player.boundingBox.minY);
        if (!player.onGround || footY != y + 1 || horizontal > 0.18D) {
            if (horizontal > 0.18D) {
                double step = Math.min(0.045D, (horizontal - 0.12D) * 0.12D);
                player.motionX += dx / horizontal * step;
                player.motionZ += dz / horizontal * step;
            } else if (footY < y + 1 && player.onGround) {
                player.jump();
            }
            return false;
        }
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        double dy = y + 0.5D - player.posY - player.getEyeHeight();
        float pitch = (float) (-Math.atan2(dy, horizontal) * 180.0D / Math.PI);
        player.rotationYaw = updateRotation(player.rotationYaw, yaw, 35.0F);
        player.rotationPitch = updateRotation(player.rotationPitch, pitch, 25.0F);
        return Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw)) < 2F
            && Math.abs(pitch - player.rotationPitch) < 2F;
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        Method[] methods = target.getClass()
            .getMethods();
        for (Method method : methods) if (method.getName()
            .equals(name) && method.getParameterTypes().length == args.length) return method.invoke(target, args);
        throw new NoSuchMethodException(
            target.getClass()
                .getName() + "."
                + name);
    }

    private static boolean bool(Object target, String name) throws Exception {
        return ((Boolean) invoke(target, name)).booleanValue();
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    static boolean minesweeperGenerated(Object game) throws Exception {
        try {
            return game.getClass()
                .getField("cIsGenerated")
                .getBoolean(game);
        } catch (NoSuchFieldException absent) {
            return bool(game, "isBoardGenerated");
        }
    }

    public void invalidateDormantCache() {}

    public void resetGameOfLightPendingAction() {
        golActionPending = false;
        golActionRejected = false;
        golAcceptedAwaitingRefresh = false;
        golPendingStage = null;
        golPendingAppearance = null;
        golPendingAppearanceState = null;
        golPendingSymbol = 0;
        golRejectedStageId = null;
    }

    public boolean isGameOfLightActionPending() {
        return golActionPending || golActionRejected || golAcceptedAwaitingRefresh;
    }

    public DetectedGame detect() {
        return detect(false);
    }

    public DetectedGame detect(boolean forceScan) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return null;
        Object game = findNearbyGame(mc.theWorld, mc.thePlayer, forceScan);
        if (game == null) return null;
        try {
            String name = game.getClass()
                .getName();
            if (MS_GAME.equals(name)) return readMinesweeper(game);
            if (SDK_GAME.equals(name)) return readSudoku(game);
            if (GOL_GAME.equals(name)) return readGameOfLight(game);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read LootGames state", e);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object findNearbyGame(World world, EntityPlayer player, boolean forceScan) {
        // Check the targeted block first for immediate detection.
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

        // Search loaded tile entities only after confirming proximity to LootGames.
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

    private boolean isPlayerInsideOrNearStructure(World world, EntityPlayer player) {
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

    private Object extractGameFromTile(World world, TileEntity tile, int x, int y, int z) {
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
            } catch (Exception ignored) {}
        }

        net.minecraft.block.Block b = world.getBlock(x, y, z);
        if (b != null && b.getClass()
            .getName()
            .startsWith(LOOTGAMES_PKG)) {
            initSubordinateReflection();
            if (cachedSubordinateClass != null && cachedSubordinateClass.isInstance(b)) {
                try {
                    Object blockPos = cachedBlockPosOfMethod
                        .invoke(null, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
                    Object masterPos = cachedGetMasterPosMethod.invoke(null, world, blockPos);
                    if (masterPos != null) {
                        int mx = number(invoke(masterPos, "getX"));
                        int my = number(invoke(masterPos, "getY"));
                        int mz = number(invoke(masterPos, "getZ"));
                        TileEntity masterTile = world.getTileEntity(mx, my, mz);
                        if (masterTile != null && !masterTile.isInvalid()
                            && masterTile.getClass()
                                .getName()
                                .startsWith(LOOTGAMES_PKG)) {
                            Method getGame = masterTile.getClass()
                                .getMethod("getGame");
                            Object game = getGame.invoke(masterTile);
                            if (game != null) {
                                String gName = game.getClass()
                                    .getName();
                                if (MS_GAME.equals(gName) || SDK_GAME.equals(gName) || GOL_GAME.equals(gName))
                                    return game;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private DetectedGame readMinesweeper(Object game) throws Exception {
        if (!minesweeperGenerated(game)) return null;
        Object rawBoard = invoke(game, "getBoard");
        if (rawBoard == null) return null;
        int size = number(invoke(rawBoard, "size"));
        int mines = number(invoke(rawBoard, "getBombCount"));
        MinesweeperBoard board = new MinesweeperBoard(size, size, mines);
        StringBuilder sig = new StringBuilder(size * size);
        for (int x = 0; x < size; x++) for (int y = 0; y < size; y++) {
            Object field = invoke(rawBoard, "getField", pos(x, y));
            boolean hidden = bool(field, "isHidden");
            String mark = String.valueOf(invoke(field, "getMark"));
            if (hidden) {
                if ("FLAG".equals(mark)) {
                    board.set(x, y, MinesweeperCell.flagged());
                    sig.append('F');
                } else if ("QUESTION".equals(mark) || "QUESTION_MARK".equals(mark)) {
                    board.set(x, y, MinesweeperCell.question());
                    sig.append('?');
                } else {
                    board.set(x, y, MinesweeperCell.unknown());
                    sig.append('#');
                }
            } else {
                int clue = number(invoke(invoke(field, "getType"), "getId"));
                board.set(x, y, MinesweeperCell.revealed(clue));
                sig.append((char) ('0' + clue));
            }
        }
        java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, size, size);
        return DetectedGame.minesweeper(game, board, sig.toString(), posMap);
    }

    private DetectedGame readSudoku(Object game) throws Exception {
        Object rawBoard = invoke(game, "getBoard");
        if (rawBoard == null || !bool(rawBoard, "isGenerated")) return null;
        SudokuBoard board = new SudokuBoard();
        int[][] playerValues = new int[9][9];
        StringBuilder sig = new StringBuilder(500);

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int puzzleVal = number(invoke(rawBoard, "getPuzzleValue", Integer.valueOf(x), Integer.valueOf(y)));
                int playerVal = number(invoke(rawBoard, "getPlayerValue", Integer.valueOf(x), Integer.valueOf(y)));
                board.set(y, x, puzzleVal);
                board.setPlayerValue(y, x, playerVal);
                playerValues[y][x] = playerVal;
                sig.append((char) ('0' + puzzleVal))
                    .append((char) ('0' + playerVal));
            }
        }
        java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, 9, 9);
        return DetectedGame.sudoku(game, board, playerValues, null, sig.toString(), posMap);
    }

    private DetectedGame readGameOfLight(Object game) throws Exception {
        Object stage = invoke(game, "getStage");
        if (stage == null) return null;
        String stageId = String.valueOf(invoke(stage, "getID"));
        if (!GameOfLightBoard.STAGE_WAITING_START.equals(stageId)
            && !GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)
            && !GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)
            && !GameOfLightBoard.STAGE_UNDER_EXPANDING.equals(stageId)) {
            return null;
        }

        if (golGame != game || (golStage != stage && !GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId))) {
            golSequence.clear();
            golSubmitted = 0;
            golActionPending = false;
            golActionRejected = false;
            golAcceptedAwaitingRefresh = false;
            golRejectedStageId = null;
            golPendingStage = null;
            golPendingAppearance = null;
            golPendingAppearanceState = null;
            golPendingSymbol = 0;
        }
        golGame = game;
        golStage = stage;
        int boardSize = 3;
        try {
            boardSize = number(invoke(game, "getSize"));
        } catch (Exception e1) {
            try {
                boardSize = number(invoke(game, "getBoardSize"));
            } catch (Exception e2) {
                try {
                    boardSize = number(invoke(stage, "getSize"));
                } catch (Exception e3) {
                    try {
                        Field f = getFieldRecursive(game.getClass(), "size");
                        if (f != null) boardSize = f.getInt(game);
                    } catch (Exception ignored) {}
                }
            }
        }
        if (boardSize <= 0) boardSize = 3;

        List<CellPosition> sequence = new ArrayList<CellPosition>();
        int currentSymbol = 0;
        if (GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)
            || GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
            try {
                Field seqField = getFieldRecursive(stage.getClass(), "sequence");
                if (seqField != null) {
                    List<?> list = (List<?>) seqField.get(stage);
                    if (list != null) {
                        for (Object sym : list) {
                            Object pos2i = invoke(sym, "getPos");
                            int px = number(invoke(pos2i, "getX"));
                            int py = number(invoke(pos2i, "getY"));
                            sequence.add(new CellPosition(px, py));
                        }
                    }
                }
                if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
                    Field curField = getFieldRecursive(stage.getClass(), "currentSymbol");
                    if (curField != null) {
                        currentSymbol = curField.getInt(stage);
                    }
                }
            } catch (Exception e) {
                LootGameSolver.LOG.debug("Error reading Game of Light sequence data", e);
            }
        }
        if (GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)) {
            golSequence = new ArrayList<CellPosition>(sequence);
            golSubmitted = 0;
        } else if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId) && sequence.isEmpty()) {
            sequence = new ArrayList<CellPosition>(golSequence);
            currentSymbol = golSubmitted;
        }
        if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
            // The mark animation may be the only client-visible acknowledgement.
            currentSymbol = Math.max(currentSymbol, golSubmitted);
        }
        if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId) && currentSymbol > 0
            && currentSymbol <= sequence.size()) {
            sequence = new ArrayList<CellPosition>(sequence.subList(currentSymbol, sequence.size()));
        }
        GameOfLightBoard board = new GameOfLightBoard(
            stageId,
            sequence,
            boardSize,
            !golSequence.isEmpty() && currentSymbol == golSequence.size(),
            currentSymbol);
        String signature = stageId + ":"
            + boardSize
            + ":"
            + currentSymbol
            + ":"
            + sequence.size()
            + ":"
            + sequence.toString();
        java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, boardSize, boardSize);
        return DetectedGame.gameOfLight(game, board, signature, posMap);
    }

    private java.util.Map<CellPosition, int[]> precomputePositions(Object game, int width, int height) {
        java.util.Map<CellPosition, int[]> map = new java.util.HashMap<CellPosition, int[]>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                CellPosition pos = new CellPosition(x, y);
                try {
                    int[] block = boardBlock(game, pos);
                    map.put(pos, block);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    public boolean interact(DetectedGame detected, SolverAction action) {
        return interact(detected, action, false);
    }

    public boolean interact(DetectedGame detected, SolverAction action, boolean sudokuReverseClick) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
            releasePendingSneak(mc);
            int[] block = boardBlock(detected.game, action.position);

            if (!preparePlayerPositionAndAim(mc.thePlayer, block[0], block[1], block[2])) {
                return false;
            }
            mc.thePlayer.swingItem();

            boolean shouldSneak = detected.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU
                && action.type == SolverAction.Type.SET_VALUE
                && sudokuReverseClick;
            boolean wasSneaking = mc.thePlayer.isSneaking();
            if (shouldSneak && !wasSneaking) {
                mc.getNetHandler()
                    .addToSendQueue(
                        new net.minecraft.network.play.client.C0BPacketEntityAction(mc.thePlayer, shouldSneak ? 1 : 2));
                mc.thePlayer.setSneaking(shouldSneak);
            }
            try {
                if (action.type == SolverAction.Type.REVEAL) {
                    if (detected.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT) {
                        ItemStack held = mc.thePlayer.getHeldItem();
                        mc.playerController.onPlayerRightClick(
                            mc.thePlayer,
                            mc.theWorld,
                            held,
                            block[0],
                            block[1],
                            block[2],
                            1,
                            Vec3.createVectorHelper(block[0] + 0.5D, block[1] + 1D, block[2] + 0.5D));
                    } else {
                        mc.playerController.clickBlock(block[0], block[1], block[2], 1);
                    }
                } else {
                    ItemStack held = mc.thePlayer.getHeldItem();
                    mc.playerController.onPlayerRightClick(
                        mc.thePlayer,
                        mc.theWorld,
                        held,
                        block[0],
                        block[1],
                        block[2],
                        1,
                        Vec3.createVectorHelper(block[0] + 0.5D, block[1] + 1D, block[2] + 0.5D));
                }
            } finally {
                // Keep reverse-cycle sneak active through the click's network tick.
                if (shouldSneak && !wasSneaking) {
                    pendingSneakRelease = true;
                    restoreSneaking = wasSneaking;
                }
            }
            return true;
        } catch (Exception e) {
            LootGameSolver.LOG.warn("LootGames interaction failed; automation stopped", e);
            return false;
        }
    }

    public void tick() {
        releasePendingSneak(Minecraft.getMinecraft());
    }

    public void releaseSneak() {
        releasePendingSneak(Minecraft.getMinecraft());
    }

    private void releasePendingSneak(Minecraft mc) {
        if (!pendingSneakRelease) return;
        pendingSneakRelease = false;
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;
        mc.getNetHandler()
            .addToSendQueue(
                new net.minecraft.network.play.client.C0BPacketEntityAction(mc.thePlayer, restoreSneaking ? 1 : 2));
        mc.thePlayer.setSneaking(restoreSneaking);
    }

    public boolean activateMaster(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
        net.minecraft.block.Block master = mc.theWorld.getBlock(x, y, z);
        if (master == null || !master.getClass()
            .getName()
            .endsWith(".PuzzleMasterBlock")) return false;
        if (!preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            || hit.blockX != x
            || hit.blockY != y
            || hit.blockZ != z) return false;
        mc.thePlayer.swingItem();
        return mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            x,
            y,
            z,
            hit.sideHit,
            hit.hitVec);
    }

    public Object nearbyGame(boolean forceScan) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return null;
        return findNearbyGame(mc.theWorld, mc.thePlayer, forceScan);
    }

    public int[] nearbyFloorActivator() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        int px = (int) Math.floor(mc.thePlayer.posX), py = (int) Math.floor(mc.thePlayer.boundingBox.minY);
        int pz = (int) Math.floor(mc.thePlayer.posZ);
        int[] closest = null;
        double best = Double.MAX_VALUE;
        for (int dy = -4; dy <= 2; dy++) for (int dx = -16; dx <= 16; dx++) for (int dz = -16; dz <= 16; dz++) {
            int x = px + dx, y = py + dy, z = pz + dz;
            if (!mc.theWorld.blockExists(x, y, z) || !isFloorActivator(mc.theWorld.getBlock(x, y, z))) continue;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < best) {
                closest = new int[] { x, y, z };
                best = distance;
            }
        }
        return closest;
    }

    private static boolean isFloorActivator(net.minecraft.block.Block block) {
        if (block == null) return false;
        return isFloorActivatorName(
            block.getClass()
                .getName());
    }

    static boolean isFloorActivatorName(String name) {
        return name.equals(LOOTGAMES_PKG + "common.block.GOLActivatorBlock")
            || name.equals(LOOTGAMES_PKG + "common.block.MSActivatorBlock")
            || name.equals(LOOTGAMES_PKG + "common.block.SudokuActivatorBlock");
    }

    public boolean activateFloorActivator(int[] position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (position == null || mc.theWorld == null || mc.thePlayer == null || mc.playerController == null)
            return false;
        int x = position[0], y = position[1], z = position[2];
        if (!isFloorActivator(mc.theWorld.getBlock(x, y, z))) return false;
        if (!preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
        net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            || hit.blockX != x
            || hit.blockY != y
            || hit.blockZ != z) return false;
        mc.thePlayer.swingItem();
        return mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            x,
            y,
            z,
            hit.sideHit,
            hit.hitVec);
    }

    public Object gameAtMaster(int[] position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (position == null || position.length < 3
            || mc.theWorld == null
            || !mc.theWorld.blockExists(position[0], position[1], position[2])) return null;
        TileEntity tile = mc.theWorld.getTileEntity(position[0], position[1], position[2]);
        return tile == null || tile.isInvalid() ? null
            : extractGameFromTile(mc.theWorld, tile, position[0], position[1], position[2]);
    }

    public boolean activateBoardCenter(Object game) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (game == null || mc.theWorld == null || mc.thePlayer == null || mc.playerController == null)
                return false;
            int[] position = gameCenter(game);
            if (position == null) return false;
            int x = position[0], y = position[1], z = position[2];
            if (!prepareExactCenterAndAim(mc.thePlayer, x, y, z)) return false;
            net.minecraft.util.MovingObjectPosition hit = mc.objectMouseOver;
            if (hit == null || hit.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
                || hit.blockX != x
                || hit.blockY != y
                || hit.blockZ != z) return false;
            mc.thePlayer.swingItem();
            return mc.playerController.onPlayerRightClick(
                mc.thePlayer,
                mc.theWorld,
                mc.thePlayer.getHeldItem(),
                x,
                y,
                z,
                hit.sideHit,
                hit.hitVec);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not activate the LootGames board center", e);
            return false;
        }
    }

    public boolean requiresMinesweeperOpeningClick(DetectedGame detected) {
        return detected != null && detected.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER;
    }

    public int currentMinesweeperLevel(Object game) {
        if (game == null || !MS_GAME.equals(
            game.getClass()
                .getName()))
            return -1;
        try {
            Field level = getFieldRecursive(game.getClass(), "currentLevel");
            return level == null ? -1 : level.getInt(game);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the Minesweeper stage", e);
            return -1;
        }
    }

    public boolean canOpenMinesweeperBoard(Object game) {
        if (game == null || !MS_GAME.equals(
            game.getClass()
                .getName()))
            return false;
        try {
            if (minesweeperGenerated(game)) return false;
            Object stage = invoke(game, "getStage");
            return "waiting".equals(String.valueOf(invoke(stage, "getID")));
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the Minesweeper attempt state", e);
            return false;
        }
    }

    public int[] gameCenter(Object game) {
        try {
            if (game != null) {
                try {
                    int size = number(invoke(game, "getCurrentBoardSize"));
                    if (size > 0) return boardBlock(game, new CellPosition(size / 2, size / 2));
                } catch (Exception ignored) {}
            }
            if (game != null && MS_GAME.equals(
                game.getClass()
                    .getName())) {
                Object board = invoke(game, "getBoard");
                int size = number(invoke(board, "size"));
                if (size > 0) {
                    return boardBlock(game, new CellPosition(size / 2, size / 2));
                }
            }
            Object center = invoke(game, "getGameCenter");
            int x = number(invoke(center, "getX"));
            int y = number(invoke(center, "getY"));
            int z = number(invoke(center, "getZ"));
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                // Resolve the clickable floor block below LootGames' logical center.
                for (int floorY = y; floorY >= y - 5; floorY--) {
                    if (!mc.theWorld.blockExists(x, floorY, z)) break;
                    net.minecraft.block.Block block = mc.theWorld.getBlock(x, floorY, z);
                    if (block != null && block != net.minecraft.init.Blocks.air
                        && block.getMaterial()
                            .isSolid())
                        return new int[] { x, floorY, z };
                }
            }
            return new int[] { x, y, z };
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the LootGames board center", e);
            return null;
        }
    }

    public void recordSubmittedAction(DetectedGame snapshot) {
        if (snapshot.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT && !golActionPending
            && (!golActionRejected || snapshot.gol == null || !golRejectedStageId.equals(snapshot.gol.getStageId()))) {
            if (golActionRejected && snapshot.gol != null && !golRejectedStageId.equals(snapshot.gol.getStageId())) {
                golActionRejected = false;
                golRejectedStageId = null;
            }
            golPendingStage = readGameStage(snapshot.game);
            golPendingAppearance = readMarkAppearance(snapshot.game);
            golPendingAppearanceState = readMarkStateName(golPendingAppearance);
            golPendingSymbol = snapshot.gol != null ? snapshot.gol.getCurrentSymbol() : golSubmitted;
            golActionPending = true;
            // Keep the playback index locally when LootGames provides no intermediate acknowledgement.
            if (snapshot.gol != null && GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(snapshot.gol.getStageId())) {
                try {
                    Field sequence = getFieldRecursive(golPendingStage.getClass(), "sequence");
                    if (sequence != null && ((List<?>) sequence.get(golPendingStage)).isEmpty()) {
                        golSubmitted = golPendingSymbol + 1;
                        clearGameOfLightAction();
                    }
                } catch (Exception e) {
                    LootGameSolver.LOG.debug("Could not read client sequence progress", e);
                }
            }
        }
    }

    public boolean actionComplete(DetectedGame current, SolverAction action) {
        if (current.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER) {
            if (!current.minesweeper.contains(action.position)) return true;
            MinesweeperCell cell = current.minesweeper.get(action.position);
            return action.type == SolverAction.Type.REVEAL
                ? cell.getState() == com.kyroxova.lootgamesolver.solver.minesweeper.CellState.REVEALED
                : action.type == SolverAction.Type.FLAG
                    ? cell.getState() == com.kyroxova.lootgamesolver.solver.minesweeper.CellState.FLAGGED
                    : false;
        }
        if (current.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU
            && action.type == SolverAction.Type.SET_VALUE) {
            if (current.sudokuPlayerValues != null) {
                return current.sudokuPlayerValues[action.position.y][action.position.x] == action.value;
            }
            return true;
        }
        if (current.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT) {
            if (current.gol == null) return false;
            Object liveStage = readGameStage(current.game);
            if (liveStage != null && golPendingStage != null && liveStage != golPendingStage) {
                clearGameOfLightAction();
                return true;
            }
            int currentSymbol = current.gol.getCurrentSymbol();
            if (currentSymbol > golPendingSymbol) {
                golSubmitted = Math.max(golSubmitted, currentSymbol);
                clearGameOfLightAction();
                return true;
            }
            if (golActionRejected && golRejectedStageId != null && golRejectedStageId.equals(current.gol.getStageId()))
                return false;
            if (!golActionPending) return true;
            if (golActionRejected) return false;
            if (!GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(current.gol.getStageId())) return false;

            Object appearance = readMarkAppearance(current.game);
            String stateName = readMarkStateName(appearance);
            boolean appearanceChanged = appearance != golPendingAppearance
                || !java.util.Objects.equals(stateName, golPendingAppearanceState);
            if (appearanceChanged && "ACCEPTED".equalsIgnoreCase(stateName)) {
                golSubmitted = Math.max(golSubmitted + 1, golPendingSymbol + 1);
                clearGameOfLightAction();
                golSubmitted = Math.max(golSubmitted, currentSymbol);
                return false;
            }
            if (appearanceChanged && "DENIED".equalsIgnoreCase(stateName)) {
                // Keep the sequence index unchanged after a rejected symbol.
                golActionPending = false;
                golActionRejected = true;
                golRejectedStageId = current.gol.getStageId();
                return false;
            }
            return false;
        }
        return false;
    }

    private static Object readGameStage(Object game) {
        if (game == null) return null;
        try {
            return invoke(game, "getStage");
        } catch (Exception e) {
            return null;
        }
    }

    private static Object readMarkAppearance(Object game) {
        if (game == null) return null;
        try {
            Field field = getFieldRecursive(game.getClass(), "specialMarkAppearance");
            return field != null ? field.get(game) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Object readMarkState(Object appearance) {
        if (appearance == null) return null;
        try {
            return invoke(appearance, "getState");
        } catch (Exception e) {
            return null;
        }
    }

    private static String readMarkStateName(Object appearance) {
        Object state = readMarkState(appearance);
        return state != null ? String.valueOf(state) : null;
    }

    private void clearGameOfLightAction() {
        golActionPending = false;
        golActionRejected = false;
        golAcceptedAwaitingRefresh = false;
        golPendingStage = null;
        golPendingAppearance = null;
        golPendingAppearanceState = null;
        golPendingSymbol = 0;
        golRejectedStageId = null;
    }

    public void submitSudokuCheck(DetectedGame snapshot) {
        if (snapshot == null || snapshot.game == null) return;
        try {
            Class<?> pkgClass = Class
                .forName("ru.timeconqueror.lootgames.common.packet.game.sudoku.CPSudokuEndGameCheck");
            Class<?> posClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
            java.lang.reflect.Constructor<?> posCons = posClass.getConstructor(int.class, int.class);
            java.lang.reflect.Constructor<?> pkgCons = pkgClass.getConstructor(posClass);
            Object packet = pkgCons.newInstance(posCons.newInstance(0, 0));

            for (Method m : snapshot.game.getClass()
                .getMethods()) {
                if ("sendFeedbackPacket".equals(m.getName()) && m.getParameterTypes().length == 1) {
                    m.invoke(snapshot.game, packet);
                    break;
                }
            }
        } catch (Exception e) {
            LootGameSolver.LOG.warn("Failed to submit Sudoku end game check packet", e);
        }
    }

    public int[] getBlockPos(Object game, CellPosition position) {
        try {
            return boardBlock(game, position);
        } catch (Exception e) {
            return null;
        }
    }

    private int[] boardBlock(Object game, CellPosition position) throws Exception {
        Object result = invoke(game, "convertToBlockPos", pos(position.x, position.y));
        return new int[] { number(invoke(result, "getX")), number(invoke(result, "getY")),
            number(invoke(result, "getZ")) };
    }

    private Object pos(int x, int y) throws Exception {
        if (!posReflectionInitialized) {
            posReflectionInitialized = true;
            try {
                Class<?> type = Class.forName(POS);
                cachedPosConstructor = type.getConstructor(int.class, int.class);
            } catch (Exception e) {
                LootGameSolver.LOG.debug("Could not initialize Pos2i constructor reflection cache", e);
            }
        }
        if (cachedPosConstructor != null) {
            return cachedPosConstructor.newInstance(Integer.valueOf(x), Integer.valueOf(y));
        }
        Class<?> type = Class.forName(POS);
        Constructor<?> constructor = type.getConstructor(int.class, int.class);
        return constructor.newInstance(Integer.valueOf(x), Integer.valueOf(y));
    }
}
