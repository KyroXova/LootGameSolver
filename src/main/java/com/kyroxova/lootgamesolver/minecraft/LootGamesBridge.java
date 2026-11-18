package com.kyroxova.lootgamesolver.minecraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
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
    private final FoliageClearer foliageClearer = new FoliageClearer();

    public void cancelFoliageClearing() {
        foliageClearer.reset();
    }

    public boolean isClearingFoliage() {
        return foliageClearer.clearing();
    }

    public boolean foliageClearingFailed() {
        return foliageClearer.failed();
    }

    public boolean clearMinesweeperObstruction(int[] position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (position == null || position.length < 3 || mc.theWorld == null || mc.thePlayer == null) return false;
        String type = mc.theWorld.getBlock(position[0], position[1], position[2])
            .getClass()
            .getName();
        if (!type.equals(LOOTGAMES_PKG + "common.block.MSActivatorBlock")) {
            Object game = nearbyGame(true);
            if (game == null || !game.getClass()
                .getName()
                .equals(MS_GAME)) return false;
        }
        return foliageClearer.clearLine(mc, position[0], position[1], position[2]);
    }

    public int[] findRoomObstruction(int[] center) {
        Minecraft mc = Minecraft.getMinecraft();
        if (center == null || mc.theWorld == null || mc.thePlayer == null) return null;
        int cx = center[0], cy = center[1], cz = center[2];
        int[] closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
                    if (FoliageClearer.isRoomObstruction(block, mc.theWorld, x, y, z)) {
                        double distSq = mc.thePlayer.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            closest = new int[] { x, y, z };
                        }
                    }
                }
            }
        }
        return closest;
    }

    public boolean clearObstructionBlock(int x, int y, int z) {
        return foliageClearer.clearBlock(Minecraft.getMinecraft(), x, y, z);
    }

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
        return preparePlayerPositionAndAim(player, x, y, z, false);
    }

    private static boolean preparePlayerPositionAndAim(EntityPlayer player, int x, int y, int z, boolean top) {
        if (player == null) return false;
        double eyeY = player.posY + player.getEyeHeight();
        double targetY = eyeY < y ? y + 0.05D : (top || eyeY > y + 1.0D ? y + 0.95D : y + 0.5D);
        double dx = x + 0.5D - player.posX;
        double dy = targetY - eyeY;
        double dz = z + 0.5D - player.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));

        player.rotationYaw = updateRotation(player.rotationYaw, yaw, 45.0F);
        player.rotationPitch = updateRotation(player.rotationPitch, pitch, 35.0F);

        if (distSq > 17.64D) return false;
        return Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - player.rotationYaw)) < 15F
            && Math.abs(pitch - player.rotationPitch) < 15F;
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

            if (detected.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER
                && foliageClearer.clearLine(mc, block[0], block[1], block[2])) return false;

            if (!preparePlayerPositionAndAim(mc.thePlayer, block[0], block[1], block[2], true)) {
                return false;
            }
            net.minecraft.util.MovingObjectPosition visible = visibleTarget(mc, block[0], block[1], block[2]);
            if (visible == null || visible.sideHit != 1) return false;
            boolean attack = action.type == SolverAction.Type.REVEAL
                && detected.type != com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT;
            if (!attack && !selectSafeInteractionItem(mc)) return false;
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
                return BaritoneBlockBridge
                    .click(mc, block[0], block[1], block[2], visible.sideHit, visible.hitVec, attack);
            } finally {
                // Keep reverse-cycle sneak active through the click's network tick.
                if (shouldSneak && !wasSneaking) {
                    pendingSneakRelease = true;
                    restoreSneaking = wasSneaking;
                }
            }
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

    static boolean targetHit(net.minecraft.util.MovingObjectPosition hit, int x, int y, int z) {
        return hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK
            && hit.blockX == x
            && hit.blockY == y
            && hit.blockZ == z
            && hit.hitVec != null;
    }

    private static net.minecraft.util.MovingObjectPosition visibleTarget(Minecraft mc, int x, int y, int z) {
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 look = mc.thePlayer.getLook(1.0F);
        double reach = mc.playerController.getBlockReachDistance();
        net.minecraft.util.MovingObjectPosition hit = mc.theWorld
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        if (targetHit(hit, x, y, z)) return hit;

        double targetY = eye.yCoord > y + 1.0D ? y + 0.99D : (eye.yCoord < y ? y + 0.01D : y + 0.5D);
        Vec3 directTarget = Vec3.createVectorHelper(x + 0.5D, targetY, z + 0.5D);
        if (eye.squareDistanceTo(directTarget) <= reach * reach) {
            hit = mc.theWorld.rayTraceBlocks(eye, directTarget);
            if (targetHit(hit, x, y, z)) return hit;
        }
        return null;
    }

    public boolean hasClearView(int[] position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (position == null || position.length < 3 || mc.thePlayer == null || mc.theWorld == null) return false;
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        Vec3 center = Vec3.createVectorHelper(position[0] + 0.5D, position[1] + 0.5D, position[2] + 0.5D);
        return targetHit(mc.theWorld.rayTraceBlocks(eye, center), position[0], position[1], position[2]);
    }

    public static boolean topFaceVisible(Minecraft mc, int[] position) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || position == null) return false;
        return topFaceVisibleFrom(
            mc,
            position,
            mc.thePlayer.posX,
            mc.thePlayer.posY + mc.thePlayer.getEyeHeight(),
            mc.thePlayer.posZ);
    }

    public static boolean topFaceVisibleFrom(Minecraft mc, int[] position, double x, double y, double z) {
        if (mc == null || mc.theWorld == null || position == null) return false;
        Vec3 eye = Vec3.createVectorHelper(x, y, z);
        Vec3 top = Vec3.createVectorHelper(position[0] + 0.5D, position[1] + 0.999D, position[2] + 0.5D);
        net.minecraft.util.MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, top);
        return targetHit(hit, position[0], position[1], position[2]) && hit.sideHit == 1;
    }

    public boolean activateMaster(int x, int y, int z) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
        net.minecraft.block.Block master = mc.theWorld.getBlock(x, y, z);
        boolean valid = isMasterBlock(master);
        if (!valid) {
            net.minecraft.tileentity.TileEntity te = mc.theWorld.getTileEntity(x, y, z);
            if (te != null && isMasterTile(te.getClass().getName())) valid = true;
        }
        if (!valid) return false;
        if (!selectSafeInteractionItem(mc)) return false;

        double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
        double targetY = eyeY < y ? y + 0.1D : (eyeY > y + 1.0D ? y + 0.9D : y + 0.5D);
        double dx = x + 0.5D - mc.thePlayer.posX;
        double dy = targetY - eyeY;
        double dz = z + 0.5D - mc.thePlayer.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double reach = Math.min(4.5D, mc.playerController.getBlockReachDistance());
        if (distSq > reach * reach) return false;

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, Math.hypot(dx, dz)) * 180.0D / Math.PI));
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;

        net.minecraft.util.MovingObjectPosition hit = visibleTarget(mc, x, y, z);
        int side = eyeY < y ? 0 : (eyeY > y + 1.0D ? 1 : 2);
        Vec3 hitVec = null;
        if (hit != null) {
            side = hit.sideHit;
            hitVec = hit.hitVec;
        } else {
            double hitY = side == 0 ? y + 0.0D : (side == 1 ? y + 1.0D : y + 0.5D);
            hitVec = Vec3.createVectorHelper(x + 0.5D, hitY, z + 0.5D);
        }
        if (BaritoneBlockBridge.click(mc, x, y, z, side, hitVec, false)) return true;
        boolean direct = mc.playerController
            .onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, side, hitVec);
        mc.thePlayer.swingItem();
        return direct;
    }

    public int[] nearbyMasterBlock() {
        return nearbyMasterBlock(null);
    }

    public int[] nearbyMasterBlock(int[] center) {
        Minecraft mc = Minecraft.getMinecraft();
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
                        net.minecraft.tileentity.TileEntity te = mc.theWorld.getTileEntity(x, y, z);
                        if (te != null && isMasterTile(te.getClass().getName())) isMaster = true;
                    }
                    if (isMaster) {
                        double distance = dx * dx + dy * dy + dz * dz;
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
            if (!(obj instanceof net.minecraft.tileentity.TileEntity)) continue;
            net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) obj;
            if (te.isInvalid()) continue;
            if (isMasterTile(te.getClass().getName())) {
                double distance = mc.thePlayer.getDistanceSq(te.xCoord + 0.5D, te.yCoord + 0.5D, te.zCoord + 0.5D);
                if (distance <= 256.0D && distance < best) {
                    closest = new int[] { te.xCoord, te.yCoord, te.zCoord };
                    best = distance;
                }
            }
        }
        return closest;
    }

    public static boolean isMasterBlock(net.minecraft.block.Block block) {
        if (block == null) return false;
        String name = block.getClass().getName();
        return name.endsWith(".PuzzleMasterBlock") || name.equals("ru.timeconqueror.lootgames.common.block.PuzzleMasterBlock");
    }

    public static boolean isMasterTile(String className) {
        return className != null && (className.endsWith(".PuzzleMasterTile")
            || className.equals("ru.timeconqueror.lootgames.common.block.tile.PuzzleMasterTile"));
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
        if (mc.theWorld.getBlock(x, y, z)
            .getClass()
            .getName()
            .equals(LOOTGAMES_PKG + "common.block.MSActivatorBlock") && foliageClearer.clearLine(mc, x, y, z))
            return false;
        if (!selectSafeInteractionItem(mc) || !preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
        net.minecraft.util.MovingObjectPosition hit = visibleTarget(mc, x, y, z);
        int side = 1;
        Vec3 hitVec = null;
        if (hit != null) {
            side = hit.sideHit;
            hitVec = hit.hitVec;
        } else {
            double eyeY = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
            side = eyeY < y ? 0 : 1;
            hitVec = Vec3.createVectorHelper(x + 0.5D, y + (side == 0 ? 0.0D : 1.0D), z + 0.5D);
        }
        if (BaritoneBlockBridge.click(mc, x, y, z, side, hitVec, false)) return true;
        boolean direct = mc.playerController
            .onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, side, hitVec);
        mc.thePlayer.swingItem();
        return direct;
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
            if (game.getClass()
                .getName()
                .equals(MS_GAME) && foliageClearer.clearLine(mc, x, y, z)) return false;
            double dx = x + 0.5D - mc.thePlayer.posX;
            double dy = y + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
            double dz = z + 0.5D - mc.thePlayer.posZ;
            double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance());
            if (dx * dx + dy * dy + dz * dz > reach * reach || !selectSafeInteractionItem(mc)
                || !preparePlayerPositionAndAim(mc.thePlayer, x, y, z)) return false;
            net.minecraft.util.MovingObjectPosition hit = visibleTarget(mc, x, y, z);
            if (hit == null) return false;
            return BaritoneBlockBridge.click(mc, x, y, z, hit.sideHit, hit.hitVec, false);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not activate the LootGames board center", e);
            return false;
        }
    }

    private static boolean selectSafeInteractionItem(Minecraft mc) {
        net.minecraft.item.ItemStack held = mc.thePlayer.getHeldItem();
        if (held != null && !(held.getItem() instanceof net.minecraft.item.ItemBlock)) {
            if (held.getItem() instanceof net.minecraft.item.ItemTool
                || held.getItem() instanceof net.minecraft.item.ItemSword) return true;
        } else if (held == null) {
            return true;
        }
        int fallbackSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            net.minecraft.item.ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(slot);
            if (candidate == null) {
                mc.thePlayer.inventory.currentItem = slot;
                if (mc.playerController != null) mc.playerController.updateController();
                return true;
            }
            if (candidate.getItem() instanceof net.minecraft.item.ItemTool
                || candidate.getItem() instanceof net.minecraft.item.ItemSword) {
                mc.thePlayer.inventory.currentItem = slot;
                if (mc.playerController != null) mc.playerController.updateController();
                return true;
            }
            if (fallbackSlot == -1 && !(candidate.getItem() instanceof net.minecraft.item.ItemBlock)) {
                fallbackSlot = slot;
            }
        }
        if (fallbackSlot != -1) {
            mc.thePlayer.inventory.currentItem = fallbackSlot;
            if (mc.playerController != null) mc.playerController.updateController();
            return true;
        }
        return held != null && !(held.getItem() instanceof net.minecraft.item.ItemBlock);
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
