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

        // Strategy 2: Check loaded tile entities list (restricted to 7x7 area around player)
        List<TileEntity> tiles = world.loadedTileEntityList;
        for (int i = 0; i < tiles.size(); i++) {
            TileEntity tile = tiles.get(i);
            if (tile == null) continue;
            double dx = tile.xCoord + 0.5D - player.posX;
            double dy = tile.yCoord + 0.5D - player.posY;
            double dz = tile.zCoord + 0.5D - player.posZ;
            if (Math.abs(dx) > 16.0D || Math.abs(dz) > 16.0D || Math.abs(dy) > 8.0D) continue;

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
        if (!bool(game, "isBoardGenerated")) return null;
        Object rawBoard = invoke(game, "getBoard");
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
        List<CellPosition> sequence = new ArrayList<CellPosition>();
        int currentSymbol = 0;
        if ("show_sequence".equals(stageId) || "waiting_for_sequence".equals(stageId)) {
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
                if ("waiting_for_sequence".equals(stageId)) {
                    Field curField = getFieldRecursive(stage.getClass(), "currentSymbol");
                    if (curField != null) {
                        currentSymbol = curField.getInt(stage);
                    }
                }
            } catch (Exception e) {
                LootGameSolver.LOG.debug("Error reading Game of Light sequence data", e);
            }
        }
        if ("waiting_for_sequence".equals(stageId) && currentSymbol > 0 && currentSymbol <= sequence.size()) {
            sequence = new ArrayList<CellPosition>(sequence.subList(currentSymbol, sequence.size()));
        }
        GameOfLightBoard board = new GameOfLightBoard(stageId, sequence);
        String signature = stageId + ":" + currentSymbol + ":" + sequence.size() + ":" + sequence.toString();
        java.util.Map<CellPosition, int[]> posMap = precomputePositions(game, 3, 3);
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
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
            int[] block = boardBlock(detected.game, action.position);

            // Realistic player interaction: auto-walk/fly into range, aim camera at block, and swing arm
            if (!preparePlayerPositionAndAim(mc.thePlayer, block[0], block[1], block[2])) {
                return false;
            }
            mc.thePlayer.swingItem();

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
            } else if (action.type == SolverAction.Type.SET_VALUE) {
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
                if (detected.sudokuPlayerValues != null) {
                    int currentVal = detected.sudokuPlayerValues[action.position.y][action.position.x];
                    int nextVal = (currentVal % 9) + 1;
                    detected.sudokuPlayerValues[action.position.y][action.position.x] = nextVal;
                    try {
                        Object rawBoard = invoke(detected.game, "getBoard");
                        if (rawBoard != null) {
                            Class<?> posClass = Class.forName("ru.timeconqueror.lootgames.api.util.Pos2i");
                            java.lang.reflect.Constructor<?> posCons = posClass.getConstructor(int.class, int.class);
                            Object posObj = posCons.newInstance(action.position.x, action.position.y);
                            invoke(rawBoard, "cSetPlayerValue", posObj, Integer.valueOf(nextVal));
                        }
                    } catch (Exception ignored) {}
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
            return true;
        } catch (Exception e) {
            LootGameSolver.LOG.warn("LootGames interaction failed; automation stopped", e);
            return false;
        }
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

        player.rotationYaw = yaw;
        player.rotationPitch = pitch;

        // Auto-walk / auto-fly towards target block if out of reach range (> 4.2 blocks)
        if (distSq > 17.64D) {
            double speed = 0.18D;
            if (distXZ > 0.1D) {
                player.motionX += (dx / distXZ) * speed;
                player.motionZ += (dz / distXZ) * speed;
            }
            if (player.capabilities.allowFlying) {
                player.capabilities.isFlying = true;
                if (Math.abs(dy) > 1.2D) {
                    player.motionY = dy > 0 ? 0.15D : -0.15D;
                }
            }
            return false;
        }
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
