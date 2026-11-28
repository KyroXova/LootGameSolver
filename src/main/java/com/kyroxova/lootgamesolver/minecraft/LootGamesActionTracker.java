package com.kyroxova.lootgamesolver.minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;

import com.kyroxova.lootgamesolver.LootGameSolver;
import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.CellState;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperBoard;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperCell;
import com.kyroxova.lootgamesolver.solver.sudoku.SudokuBoard;

public final class LootGamesActionTracker {

    Object golGame;
    Object golStage;
    Object golPendingStage;
    Object golPendingAppearance;
    String golPendingAppearanceState;
    int golPendingSymbol;
    boolean golActionPending;
    boolean golActionRejected;
    boolean golAcceptedAwaitingRefresh;
    String golRejectedStageId;
    final List<CellPosition> golSequence = new ArrayList<CellPosition>();
    int golSubmitted;

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

    public boolean requiresMinesweeperOpeningClick(DetectedGame detected) {
        return detected != null && detected.type == MiniGame.MINESWEEPER;
    }

    public int currentMinesweeperLevel(Object game) {
        if (game == null || !LootGamesTileAccess.MS_GAME.equals(
            game.getClass()
                .getName())) {
            return -1;
        }
        try {
            Object level = LootGamesTileAccess.getFieldValue(game, "currentLevel");
            return level instanceof Number ? ((Number) level).intValue() : -1;
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the Minesweeper stage", e);
            return -1;
        }
    }

    public static boolean minesweeperGenerated(Object game) throws Exception {
        try {
            return game.getClass()
                .getField("cIsGenerated")
                .getBoolean(game);
        } catch (NoSuchFieldException absent) {
            return LootGamesTileAccess.bool(game, "isBoardGenerated");
        }
    }

    public boolean canOpenMinesweeperBoard(Object game) {
        if (game == null || !LootGamesTileAccess.MS_GAME.equals(
            game.getClass()
                .getName())) {
            return false;
        }
        try {
            if (minesweeperGenerated(game)) return false;
            Object stage = LootGamesTileAccess.invoke(game, "getStage");
            return "waiting".equals(String.valueOf(LootGamesTileAccess.invoke(stage, "getID")));
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the Minesweeper attempt state", e);
            return false;
        }
    }

    public int[] gameCenter(Object game) {
        try {
            if (game != null) {
                try {
                    int size = LootGamesTileAccess.number(LootGamesTileAccess.invoke(game, "getCurrentBoardSize"));
                    if (size > 0) return boardBlock(game, new CellPosition(size / 2, size / 2));
                } catch (Exception ignored) {
                    LootGameSolver.LOG.debug("Could not read current board size", ignored);
                }
            }
            if (game != null && LootGamesTileAccess.MS_GAME.equals(
                game.getClass()
                    .getName())) {
                Object board = LootGamesTileAccess.invoke(game, "getBoard");
                int size = LootGamesTileAccess.number(LootGamesTileAccess.invoke(board, "size"));
                if (size > 0) {
                    return boardBlock(game, new CellPosition(size / 2, size / 2));
                }
            }
            Object center = LootGamesTileAccess.invoke(game, "getGameCenter");
            int x = LootGamesTileAccess.number(LootGamesTileAccess.invoke(center, "getX"));
            int y = LootGamesTileAccess.number(LootGamesTileAccess.invoke(center, "getY"));
            int z = LootGamesTileAccess.number(LootGamesTileAccess.invoke(center, "getZ"));
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld != null) {
                for (int floorY = y; floorY >= y - 5; floorY--) {
                    if (!mc.theWorld.blockExists(x, floorY, z)) break;
                    net.minecraft.block.Block block = mc.theWorld.getBlock(x, floorY, z);
                    if (block != null && block != Blocks.air
                        && block.getMaterial()
                            .isSolid()) {
                        return new int[] { x, floorY, z };
                    }
                }
            }
            return new int[] { x, y, z };
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read the LootGames board center", e);
            return null;
        }
    }

    public void recordSubmittedAction(DetectedGame snapshot) {
        if (snapshot.type == MiniGame.GAME_OF_LIGHT && !golActionPending
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
            if (snapshot.gol != null && GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(snapshot.gol.getStageId())) {
                try {
                    Object sequence = LootGamesTileAccess.getFieldValue(golPendingStage, "sequence");
                    if (sequence instanceof List<?> && ((List<?>) sequence).isEmpty()) {
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
        if (current.type == MiniGame.MINESWEEPER) {
            if (!current.minesweeper.contains(action.position)) return true;
            MinesweeperCell cell = current.minesweeper.get(action.position);
            return action.type == SolverAction.Type.REVEAL ? cell.getState() == CellState.REVEALED
                : action.type == SolverAction.Type.FLAG ? cell.getState() == CellState.FLAGGED : false;
        }
        if (current.type == MiniGame.SUDOKU && action.type == SolverAction.Type.SET_VALUE) {
            if (current.sudokuPlayerValues != null) {
                return current.sudokuPlayerValues[action.position.y][action.position.x] == action.value;
            }
            return true;
        }
        if (current.type == MiniGame.GAME_OF_LIGHT) {
            if (current.gol == null) return false;
            Object liveStage = readGameStage(current.game);
            if (liveStage != null && golPendingStage != null && !java.util.Objects.equals(liveStage, golPendingStage)) {
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
            boolean appearanceChanged = !java.util.Objects.equals(appearance, golPendingAppearance)
                || !java.util.Objects.equals(stateName, golPendingAppearanceState);
            if (appearanceChanged && "ACCEPTED".equalsIgnoreCase(stateName)) {
                golSubmitted = Math.max(golSubmitted + 1, golPendingSymbol + 1);
                clearGameOfLightAction();
                golSubmitted = Math.max(golSubmitted, currentSymbol);
                return false;
            }
            if (appearanceChanged && "DENIED".equalsIgnoreCase(stateName)) {
                golActionPending = false;
                golActionRejected = true;
                golRejectedStageId = current.gol.getStageId();
                return false;
            }
            return false;
        }
        return false;
    }

    public static Object readGameStage(Object game) {
        if (game == null) return null;
        try {
            return LootGamesTileAccess.invoke(game, "getStage");
        } catch (Exception e) {
            return null;
        }
    }

    public static Object readMarkAppearance(Object game) {
        if (game == null) return null;
        try {
            return LootGamesTileAccess.getFieldValue(game, "specialMarkAppearance");
        } catch (Exception e) {
            return null;
        }
    }

    public static Object readMarkState(Object appearance) {
        if (appearance == null) return null;
        try {
            return LootGamesTileAccess.invoke(appearance, "getState");
        } catch (Exception e) {
            return null;
        }
    }

    public static String readMarkStateName(Object appearance) {
        Object state = readMarkState(appearance);
        return state != null ? String.valueOf(state) : null;
    }

    public void clearGameOfLightAction() {
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
            Object pos = LootGamesTileAccess.createPos(0, 0);
            java.lang.reflect.Constructor<?> pkgCons = pkgClass.getConstructor(pos.getClass());
            Object packet = pkgCons.newInstance(pos);

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

    public int[] boardBlock(Object game, CellPosition position) throws Exception {
        Object result = LootGamesTileAccess
            .invoke(game, "convertToBlockPos", LootGamesTileAccess.createPos(position.x, position.y));
        return new int[] { LootGamesTileAccess.number(LootGamesTileAccess.invoke(result, "getX")),
            LootGamesTileAccess.number(LootGamesTileAccess.invoke(result, "getY")),
            LootGamesTileAccess.number(LootGamesTileAccess.invoke(result, "getZ")) };
    }

    public Map<CellPosition, int[]> precomputePositions(Object game, int width, int height) {
        Map<CellPosition, int[]> map = new HashMap<CellPosition, int[]>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                CellPosition pos = new CellPosition(x, y);
                try {
                    int[] block = boardBlock(game, pos);
                    map.put(pos, block);
                } catch (Exception ignored) {
                    LootGameSolver.LOG.debug("Could not precompute board block for {}", pos, ignored);
                }
            }
        }
        return map;
    }

    public DetectedGame readMinesweeper(Object game) throws Exception {
        if (!minesweeperGenerated(game)) return null;
        Object rawBoard = LootGamesTileAccess.invoke(game, "getBoard");
        if (rawBoard == null) return null;
        int size = LootGamesTileAccess.number(LootGamesTileAccess.invoke(rawBoard, "size"));
        int mines = LootGamesTileAccess.number(LootGamesTileAccess.invoke(rawBoard, "getBombCount"));
        MinesweeperBoard board = new MinesweeperBoard(size, size, mines);
        StringBuilder sig = new StringBuilder(size * size);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                Object field = LootGamesTileAccess.invoke(rawBoard, "getField", LootGamesTileAccess.createPos(x, y));
                boolean hidden = LootGamesTileAccess.bool(field, "isHidden");
                String mark = String.valueOf(LootGamesTileAccess.invoke(field, "getMark"));
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
                    int clue = LootGamesTileAccess
                        .number(LootGamesTileAccess.invoke(LootGamesTileAccess.invoke(field, "getType"), "getId"));
                    board.set(x, y, MinesweeperCell.revealed(clue));
                    sig.append((char) ('0' + clue));
                }
            }
        }
        Map<CellPosition, int[]> posMap = precomputePositions(game, size, size);
        return DetectedGame.minesweeper(game, board, sig.toString(), posMap);
    }

    public DetectedGame readSudoku(Object game) throws Exception {
        Object rawBoard = LootGamesTileAccess.invoke(game, "getBoard");
        if (rawBoard == null || !LootGamesTileAccess.bool(rawBoard, "isGenerated")) return null;
        SudokuBoard board = new SudokuBoard();
        int[][] playerValues = new int[9][9];
        StringBuilder sig = new StringBuilder(500);

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                int puzzleVal = LootGamesTileAccess.number(
                    LootGamesTileAccess.invoke(rawBoard, "getPuzzleValue", Integer.valueOf(x), Integer.valueOf(y)));
                int playerVal = LootGamesTileAccess.number(
                    LootGamesTileAccess.invoke(rawBoard, "getPlayerValue", Integer.valueOf(x), Integer.valueOf(y)));
                board.set(y, x, puzzleVal);
                board.setPlayerValue(y, x, playerVal);
                playerValues[y][x] = playerVal;
                sig.append((char) ('0' + puzzleVal))
                    .append((char) ('0' + playerVal));
            }
        }
        Map<CellPosition, int[]> posMap = precomputePositions(game, 9, 9);
        return DetectedGame.sudoku(game, board, playerValues, null, sig.toString(), posMap);
    }

    public DetectedGame readGameOfLight(Object game) throws Exception {
        Object stage = LootGamesTileAccess.invoke(game, "getStage");
        if (stage == null) return null;
        String stageId = String.valueOf(LootGamesTileAccess.invoke(stage, "getID"));
        if (!GameOfLightBoard.STAGE_WAITING_START.equals(stageId)
            && !GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)
            && !GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)
            && !GameOfLightBoard.STAGE_UNDER_EXPANDING.equals(stageId)) {
            return null;
        }

        if (!java.util.Objects.equals(golGame, game)
            || (!java.util.Objects.equals(golStage, stage)
                && !GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId))) {
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
        int boardSize = readGameOfLightBoardSize(game, stage);
        List<CellPosition> sequence = new ArrayList<CellPosition>();
        int currentSymbol = readGameOfLightSequence(stage, stageId, sequence);
        if (GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)) {
            golSequence.clear();
            golSequence.addAll(sequence);
            golSubmitted = 0;
        } else if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
            if (sequence.isEmpty()) {
                sequence = new ArrayList<CellPosition>(golSequence);
                currentSymbol = golSubmitted;
            }
            currentSymbol = Math.max(currentSymbol, golSubmitted);
            if (currentSymbol > 0 && currentSymbol <= sequence.size()) {
                sequence = new ArrayList<CellPosition>(sequence.subList(currentSymbol, sequence.size()));
            }
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
        Map<CellPosition, int[]> posMap = precomputePositions(game, boardSize, boardSize);
        return DetectedGame.gameOfLight(game, board, signature, posMap);
    }

    private static int readGameOfLightBoardSize(Object game, Object stage) {
        int boardSize = 3;
        try {
            boardSize = LootGamesTileAccess.number(LootGamesTileAccess.invoke(game, "getSize"));
        } catch (Exception e1) {
            try {
                boardSize = LootGamesTileAccess.number(LootGamesTileAccess.invoke(game, "getBoardSize"));
            } catch (Exception e2) {
                try {
                    boardSize = LootGamesTileAccess.number(LootGamesTileAccess.invoke(stage, "getSize"));
                } catch (Exception e3) {
                    Object f = LootGamesTileAccess.getFieldValue(game, "size");
                    if (f instanceof Number) boardSize = ((Number) f).intValue();
                }
            }
        }
        return boardSize <= 0 ? 3 : boardSize;
    }

    private static int readGameOfLightSequence(Object stage, String stageId, List<CellPosition> sequence) {
        int currentSymbol = 0;
        if (!GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stageId)
            && !GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
            return currentSymbol;
        }
        try {
            Object rawSeq = LootGamesTileAccess.getFieldValue(stage, "sequence");
            if (rawSeq instanceof List<?>) {
                for (Object sym : (List<?>) rawSeq) {
                    Object pos2i = LootGamesTileAccess.invoke(sym, "getPos");
                    int px = LootGamesTileAccess.number(LootGamesTileAccess.invoke(pos2i, "getX"));
                    int py = LootGamesTileAccess.number(LootGamesTileAccess.invoke(pos2i, "getY"));
                    sequence.add(new CellPosition(px, py));
                }
            }
            if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stageId)) {
                Object cur = LootGamesTileAccess.getFieldValue(stage, "currentSymbol");
                if (cur instanceof Number) currentSymbol = ((Number) cur).intValue();
            }
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Error reading Game of Light sequence data", e);
        }
        return currentSymbol;
    }
}
