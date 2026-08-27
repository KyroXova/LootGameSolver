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

/**
 * Optional, reflection-only adapter for LootGames 2.x. No LootGames classes
 * appear in this mod's bytecode, so a missing or newer jar fails closed.
 */
public final class LootGamesBridge implements MiniGameDetector {

    private static final String MS_GAME = "ru.timeconqueror.lootgames.minigame.minesweeper.GameMineSweeper";
    private static final String SDK_GAME = "ru.timeconqueror.lootgames.minigame.sudoku.GameSudoku";
    private static final String GOL_GAME = "ru.timeconqueror.lootgames.minigame.gol.GameOfLight";
    private static final String POS = "ru.timeconqueror.lootgames.api.util.Pos2i";

    public DetectedGame detect() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return null;
        Object game = findNearbyGame(mc.theWorld, mc.thePlayer);
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
    private Object findNearbyGame(World world, EntityPlayer player) {
        Object selected = null;
        double selectedDistance = Double.MAX_VALUE;

        // Strategy 1: Check crosshair targeted block
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.objectMouseOver != null
            && mc.objectMouseOver.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            int bx = mc.objectMouseOver.blockX;
            int by = mc.objectMouseOver.blockY;
            int bz = mc.objectMouseOver.blockZ;
            TileEntity hitTile = world.getTileEntity(bx, by, bz);
            Object game = extractGameFromTile(world, hitTile, bx, by, bz);
            if (game != null) return game;
        }

        // Strategy 2: Check loaded tile entities list (expanded to 32 block horizontal radius)
        List<TileEntity> tiles = world.loadedTileEntityList;
        for (int i = 0; i < tiles.size(); i++) {
            TileEntity tile = tiles.get(i);
            if (tile == null) continue;
            double dx = tile.xCoord + 0.5D - player.posX;
            double dy = tile.yCoord + 0.5D - player.posY;
            double dz = tile.zCoord + 0.5D - player.posZ;
            if (Math.abs(dx) > 32.0D || Math.abs(dz) > 32.0D || Math.abs(dy) > 16.0D) continue;

            Object game = extractGameFromTile(world, tile, tile.xCoord, tile.yCoord, tile.zCoord);
            if (game == null) continue;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < selectedDistance) {
                selected = game;
                selectedDistance = distance;
            }
        }
        return selected;
    }

    private Object extractGameFromTile(World world, TileEntity tile, int x, int y, int z) {
        if (tile != null) {
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

        // Check if block at position is SmartSubordinateBlock
        try {
            Class<?> subClass = Class.forName("ru.timeconqueror.lootgames.api.block.SmartSubordinateBlock");
            net.minecraft.block.Block b = world.getBlock(x, y, z);
            if (subClass.isInstance(b)) {
                Class<?> blockPosClass = Class.forName("ru.timeconqueror.lootgames.utils.future.BlockPos");
                Method ofMethod = blockPosClass.getMethod("of", int.class, int.class, int.class);
                Object blockPos = ofMethod.invoke(null, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));

                Method getMasterPos = subClass.getMethod("getMasterPos", World.class, blockPosClass);
                Object masterPos = getMasterPos.invoke(null, world, blockPos);
                if (masterPos != null) {
                    int mx = number(invoke(masterPos, "getX"));
                    int my = number(invoke(masterPos, "getY"));
                    int mz = number(invoke(masterPos, "getZ"));
                    TileEntity masterTile = world.getTileEntity(mx, my, mz);
                    if (masterTile != null) {
                        try {
                            Method getGame = masterTile.getClass()
                                .getMethod("getGame");
                            Object game = getGame.invoke(masterTile);
                            if (game != null) {
                                String gName = game.getClass()
                                    .getName();
                                if (MS_GAME.equals(gName) || SDK_GAME.equals(gName) || GOL_GAME.equals(gName))
                                    return game;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private DetectedGame readMinesweeper(Object game) throws Exception {
        boolean generated = bool(game, "isBoardGenerated");
        Object rawBoard = null;
        try {
            rawBoard = invoke(game, "getBoard");
        } catch (Exception ignored) {}

        int size = 9;
        int mines = 10;
        if (rawBoard != null) {
            try {
                size = number(invoke(rawBoard, "size"));
            } catch (Exception ignored) {}
            try {
                mines = number(invoke(rawBoard, "getBombCount"));
            } catch (Exception ignored) {}
        } else {
            try {
                size = number(invoke(game, "getBoardSize"));
            } catch (Exception ignored) {}
        }

        if (!generated || rawBoard == null) {
            // Stage initial / ungenerated state: construct all-unknown board so solver can click to begin stage
            MinesweeperBoard board = new MinesweeperBoard(size, size, mines);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    board.set(x, y, MinesweeperCell.unknown());
                }
            }
            java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, size, size);
            return DetectedGame.minesweeper(game, board, "UNGENERATED:" + size, posMap);
        }

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
                int puzzleVal = readSudokuValue(rawBoard, "getPuzzleValue", x, y);
                int playerVal = readSudokuValue(rawBoard, "getPlayerValue", x, y);
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

    private int readSudokuValue(Object rawBoard, String methodName, int x, int y) {
        try {
            return number(invoke(rawBoard, methodName, Integer.valueOf(x), Integer.valueOf(y)));
        } catch (Exception ignored) {}
        try {
            return number(invoke(rawBoard, methodName, pos(x, y)));
        } catch (Exception ignored) {}
        try {
            Field f = getFieldRecursive(rawBoard.getClass(), methodName);
            if (f != null) {
                Object val = f.get(rawBoard);
                if (val instanceof int[][]) {
                    return ((int[][]) val)[y][x];
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private final List<CellPosition> accumulatedGolSequence = new ArrayList<CellPosition>();
    private final java.util.Set<Object> processedDisplayedSymbols = new java.util.HashSet<Object>();
    private String lastGolStageId = "";

    private DetectedGame readGameOfLight(Object game) throws Exception {
        Object stage = invoke(game, "getStage");
        if (stage == null) return null;
        String stageId = String.valueOf(invoke(stage, "getID"));
        List<CellPosition> sequence = new ArrayList<CellPosition>();
        int currentSymbol = 0;
        int boardSize = 3;
        try {
            boardSize = number(invoke(game, "getCurrentBoardSize"));
        } catch (Exception ignored) {}
        if (boardSize <= 0) boardSize = 3;

        if (!stageId.equals(lastGolStageId)) {
            if ("waiting_start".equals(stageId) || "show_sequence".equals(stageId)) {
                accumulatedGolSequence.clear();
                processedDisplayedSymbols.clear();
            }
            lastGolStageId = stageId;
        }

        if ("show_sequence".equals(stageId) || "waiting_for_sequence".equals(stageId)) {
            try {
                // Strategy 1: Read from stage.sequence
                Field seqField = getFieldRecursive(stage.getClass(), "sequence");
                if (seqField != null) {
                    Object rawSeq = seqField.get(stage);
                    if (rawSeq instanceof List) {
                        List<?> list = (List<?>) rawSeq;
                        for (Object item : list) {
                            CellPosition pos = extractCellPositionFromSymbolOrPos(item);
                            if (pos != null) {
                                sequence.add(pos);
                            }
                        }
                    } else if (rawSeq instanceof int[]) {
                        int[] arr = (int[]) rawSeq;
                        for (int idx : arr) {
                            CellPosition pos = cellPositionFromSymbolIndex(idx);
                            if (pos != null) {
                                sequence.add(pos);
                            }
                        }
                    }
                }

                // Strategy 2: Accumulate live displayed symbols from game.getDisplayedSymbols()
                try {
                    List<?> dispList = (List<?>) invoke(game, "getDisplayedSymbols");
                    if (dispList != null && !dispList.isEmpty()) {
                        for (Object item : dispList) {
                            if (item == null) continue;
                            if (processedDisplayedSymbols.add(item)) {
                                Object sym = null;
                                try {
                                    sym = invoke(item, "getSymbol");
                                } catch (Exception ignored) {
                                    Field sf = getFieldRecursive(item.getClass(), "symbol");
                                    if (sf != null) sym = sf.get(item);
                                }
                                CellPosition pos = extractCellPositionFromSymbolOrPos(sym);
                                if (pos != null) {
                                    accumulatedGolSequence.add(pos);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                if (!sequence.isEmpty()) {
                    accumulatedGolSequence.clear();
                    accumulatedGolSequence.addAll(sequence);
                } else if (!accumulatedGolSequence.isEmpty()) {
                    sequence = new ArrayList<CellPosition>(accumulatedGolSequence);
                }

                if ("waiting_for_sequence".equals(stageId)) {
                    int readSymbol = extractIntFromStage(
                        stage,
                        "currentSymbol",
                        "currentIndex",
                        "index",
                        "step",
                        "currentStep",
                        "symbolIndex",
                        "placedSymbols");
                    if (readSymbol >= 0) {
                        currentSymbol = readSymbol;
                    }
                }
            } catch (Exception e) {
                LootGameSolver.LOG.debug("Error reading Game of Light sequence data", e);
            }
        }
        boolean sequenceCompleted = false;
        if ("waiting_for_sequence".equals(stageId)) {
            if (currentSymbol > 0 && currentSymbol < sequence.size()) {
                sequence = new ArrayList<CellPosition>(sequence.subList(currentSymbol, sequence.size()));
            } else if (currentSymbol >= sequence.size() && !sequence.isEmpty()) {
                sequenceCompleted = true;
                sequence = java.util.Collections.emptyList();
            }
        }
        GameOfLightBoard board = new GameOfLightBoard(stageId, sequence, boardSize, sequenceCompleted, currentSymbol);
        String signature = stageId + ":"
            + currentSymbol
            + ":"
            + sequence.size()
            + ":"
            + sequenceCompleted
            + ":"
            + sequence.toString();
        java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, boardSize, boardSize);
        return DetectedGame.gameOfLight(game, board, signature, posMap);
    }

    private int extractIntFromStage(Object stage, String... fieldNames) {
        if (stage == null) return -1;
        for (String name : fieldNames) {
            Field f = getFieldRecursive(stage.getClass(), name);
            if (f != null) {
                try {
                    Object val = f.get(stage);
                    if (val instanceof Number) {
                        return ((Number) val).intValue();
                    } else if (val != null && val.getClass()
                        .isEnum()) {
                            return ((Enum<?>) val).ordinal();
                        }
                } catch (Exception ignored) {}
            }
            String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            try {
                Method m = stage.getClass()
                    .getMethod(getter);
                Object val = m.invoke(stage);
                if (val instanceof Number) {
                    return ((Number) val).intValue();
                } else if (val != null && val.getClass()
                    .isEnum()) {
                        return ((Enum<?>) val).ordinal();
                    }
            } catch (Exception ignored) {}
        }
        return -1;
    }

    private CellPosition extractCellPositionFromSymbolOrPos(Object item) {
        if (item == null) return null;

        if (item instanceof Number) {
            return cellPositionFromSymbolIndex(((Number) item).intValue());
        }

        if (item.getClass()
            .isEnum()) {
            return cellPositionFromSymbolIndex(((Enum<?>) item).ordinal());
        }

        // Try getting position via Symbol enum index (0..7)
        try {
            Method getIndexMethod = item.getClass()
                .getMethod("getIndex");
            int idx = number(getIndexMethod.invoke(item));
            return cellPositionFromSymbolIndex(idx);
        } catch (Exception ignored) {}

        Object pos2i = item;
        if (!item.getClass()
            .getName()
            .contains("Pos2i")) {
            try {
                pos2i = invoke(item, "getPos");
            } catch (Exception ignored) {
                Field pf = getFieldRecursive(item.getClass(), "pos");
                if (pf != null) {
                    try {
                        pos2i = pf.get(item);
                    } catch (Exception ignored2) {}
                }
            }
        }
        if (pos2i != null) {
            try {
                int px = number(invoke(pos2i, "getX"));
                int py = number(invoke(pos2i, "getY"));
                return new CellPosition(px, py);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static CellPosition cellPositionFromSymbolIndex(int idx) {
        switch (idx) {
            case 0:
                return new CellPosition(0, 0); // NORTH_WEST
            case 1:
                return new CellPosition(1, 0); // NORTH
            case 2:
                return new CellPosition(2, 0); // NORTH_EAST
            case 3:
                return new CellPosition(0, 1); // WEST
            case 4:
                return new CellPosition(2, 1); // EAST
            case 5:
                return new CellPosition(0, 2); // SOUTH_WEST
            case 6:
                return new CellPosition(1, 2); // SOUTH
            case 7:
                return new CellPosition(2, 2); // SOUTH_EAST
            default:
                return null;
        }
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

    /** Executes exactly one normal Minecraft interaction. Caller waits for a packet-driven board update. */
    public boolean interact(DetectedGame detected, SolverAction action) {
        return interact(detected, action, false, 1);
    }

    /** Executes an interaction with optional sneak (shift) state and remaining click count. */
    public boolean interact(DetectedGame detected, SolverAction action, boolean sneak) {
        return interact(detected, action, sneak, 1);
    }

    public boolean interact(DetectedGame detected, SolverAction action, boolean sneak, int remainingClicks) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
            int[] block = boardBlock(detected.game, action.position);

            // Realistic player interaction: auto-walk/fly into range, aim camera at block, and swing arm
            if (!preparePlayerPositionAndAim(mc.thePlayer, block[0], block[1], block[2])) {
                return false;
            }
            mc.thePlayer.swingItem();

            ensureEmptyHand(mc.thePlayer);
            setSneakingState(mc, sneak);
            try {
                if (action.type == SolverAction.Type.REVEAL) {
                    if (detected.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER) {
                        boolean hasRevealed = false;
                        if (detected.minesweeper != null) {
                            for (CellPosition p : detected.minesweeper.positions()) {
                                if (detected.minesweeper.get(p)
                                    .getState() == com.kyroxova.lootgamesolver.solver.minesweeper.CellState.REVEALED) {
                                    hasRevealed = true;
                                    break;
                                }
                            }
                        }
                        if (!hasRevealed
                            || (detected.signature != null && detected.signature.startsWith("UNGENERATED"))) {
                            // Starting an ungenerated stage or opening new board requires right-click on center block
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
                            // Safe cell reveal on active open board: LEFT-CLICK (clickBlock)
                            mc.playerController.clickBlock(block[0], block[1], block[2], 1);
                        }
                    } else if (detected.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT) {
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
                        // Safe cell reveal for Minesweeper / Sudoku: LEFT-CLICK (clickBlock)
                        mc.playerController.clickBlock(block[0], block[1], block[2], 1);
                    }
                } else {
                    // Mine flagging / set value: RIGHT-CLICK (onPlayerRightClick)
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
                if (sneak && remainingClicks <= 1) {
                    setSneakingState(mc, false);
                }
            }
            return true;
        } catch (Exception e) {
            LootGameSolver.LOG.warn("LootGames interaction failed; automation stopped", e);
            return false;
        }
    }

    private static float updateRotation(float current, float target, float maxStep) {
        float diff = current - target;
        while (diff >= 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current - diff;
    }

    private static void setSneakingState(Minecraft mc, boolean sneak) {
        if (mc == null || mc.thePlayer == null) return;
        mc.thePlayer.setSneaking(sneak);
        if (mc.gameSettings != null && mc.gameSettings.keyBindSneak != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), sneak);
        }
        if (mc.thePlayer.sendQueue != null) {
            try {
                int actionId = sneak ? 1 : 2;
                mc.thePlayer.sendQueue.addToSendQueue(
                    new net.minecraft.network.play.client.C0BPacketEntityAction(mc.thePlayer, actionId));
            } catch (Exception ignored) {}
        }
    }

    private static void ensureEmptyHand(EntityPlayer player) {
        if (player == null || player.inventory == null) return;
        if (player.getHeldItem() == null) return;

        for (int i = 0; i < 9; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.currentItem = i;
                return;
            }
        }

        for (int i = 9; i < 36; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = player.inventory.mainInventory[8];
                player.inventory.mainInventory[8] = null;
                player.inventory.currentItem = 8;
                return;
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.inventory.mainInventory[i];
            if (stack != null && stack.getItem() != null) {
                String name = stack.getItem()
                    .getUnlocalizedName();
                if (name != null) {
                    name = name.toLowerCase();
                    if (!name.contains("hammer") && !name.contains("wrench")
                        && !name.contains("pickaxe")
                        && !name.contains("sword")
                        && !name.contains("axe")
                        && !name.contains("wand")
                        && !name.contains("tool")
                        && !name.contains("chisel")) {
                        player.inventory.currentItem = i;
                        return;
                    }
                }
            }
        }
    }

    private static boolean preparePlayerPositionAndAim(EntityPlayer player, int x, int y, int z) {
        if (player == null) return false;
        double dx = x + 0.5D - player.posX;
        double dy = y + 0.5D - (player.posY + player.getEyeHeight());
        double dz = z + 0.5D - player.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > 1024.0D) {
            return false;
        }

        // Reach assistance: nudge player motion if further than 4.0 blocks
        if (distSq > 16.0D) {
            double distXZ = Math.sqrt(dx * dx + dz * dz);
            if (distXZ > 0.1D) {
                player.motionX += (dx / distXZ) * 0.10D;
                player.motionZ += (dz / distXZ) * 0.10D;
            }
        }

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));

        player.rotationYaw = updateRotation(player.rotationYaw, targetYaw, 60.0F);
        player.rotationPitch = updateRotation(player.rotationPitch, targetPitch, 45.0F);

        return true;
    }

    public boolean actionComplete(DetectedGame current, SolverAction action) {
        if (current.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER) {
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
        if (current.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT) return true;
        return false;
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
        Class<?> type = Class.forName(POS);
        Constructor<?> constructor = type.getConstructor(int.class, int.class);
        return constructor.newInstance(Integer.valueOf(x), Integer.valueOf(y));
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
}
