package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightSolver;
import com.kyroxova.lootgamesolver.solver.minesweeper.MinesweeperSolver;
import com.kyroxova.lootgamesolver.solver.sudoku.SudokuSolver;

/** Event-driven, single-action automation state machine. It never clicks in a busy loop. */
public final class SolverSession {

    private final LootGamesBridge bridge = new LootGamesBridge();
    private final MinesweeperSolver minesweeperSolver = new MinesweeperSolver();
    private final SudokuSolver sudokuSolver = new SudokuSolver();
    private final GameOfLightSolver golSolver = new GameOfLightSolver();
    private final ActionPlanner planner = new ActionPlanner();

    private ActionPlanner.Mode mode;
    private SolverAction activeAction;
    private List<SolverAction> activeActions;
    private DetectedGame currentSnapshot;
    private String beforeActionSignature;
    private long lastActionAt;
    private String status = "Idle";
    private MiniGame game = MiniGame.UNKNOWN;
    private int queued;
    private int remainingClicksForActiveAction = 0;
    private final int[][] optimisticSudokuValues = new int[9][9];

    public void start(ActionPlanner.Mode requestedMode) {
        if (!LootGameSolverConfig.enabled) {
            status = "Disabled in config";
            return;
        }
        cancel(
            "Preparing " + requestedMode.name()
                .toLowerCase());
        mode = requestedMode;
        advance();
    }

    public void tick() {
        currentSnapshot = bridge.detect();
        if (mode == null) return;

        long now = System.currentTimeMillis();
        if (now - lastActionAt < LootGameSolverConfig.clickDelayMs) return;

        DetectedGame snapshot = currentSnapshot;
        if (snapshot == null) {
            cancel("No supported LootGames board nearby");
            return;
        }
        game = snapshot.type;

        if (activeAction == null || isActionComplete(snapshot)) {
            advanceAuto(snapshot);
        } else {
            send(snapshot);
        }
    }

    private void advance() {
        advanceAuto(bridge.detect());
    }

    private int calculateClicksNeeded(int curVal, int targetVal) {
        if (curVal == targetVal) return 0;
        if (curVal == 0) return targetVal;
        int diff = targetVal - curVal;
        if (diff < 0) diff += 9;
        return diff;
    }

    private void advanceAuto(DetectedGame snapshot) {
        currentSnapshot = snapshot;
        if (snapshot == null) {
            cancel("No supported LootGames board nearby");
            return;
        }
        game = snapshot.type;

        if (snapshot.type == MiniGame.SUDOKU && snapshot.sudokuPlayerValues != null) {
            for (int r = 0; r < 9; r++) {
                for (int c = 0; c < 9; c++) {
                    if (snapshot.sudokuPlayerValues[r][c] == optimisticSudokuValues[r][c]) {
                        optimisticSudokuValues[r][c] = 0;
                    } else if (optimisticSudokuValues[r][c] != 0) {
                        snapshot.sudokuPlayerValues[r][c] = optimisticSudokuValues[r][c];
                    }
                    if (snapshot.sudoku != null && snapshot.sudokuPlayerValues[r][c] != 0) {
                        snapshot.sudoku.set(r, c, snapshot.sudokuPlayerValues[r][c]);
                    }
                }
            }
        }

        SolveResult result = snapshot.type == MiniGame.MINESWEEPER ? minesweeperSolver.solve(snapshot.minesweeper)
            : snapshot.type == MiniGame.SUDOKU ? sudokuSolver.solve(snapshot.sudoku) : golSolver.solve(snapshot.gol);

        List<SolverAction> actions = planner
            .plan(result, mode, LootGameSolverConfig.allowProbabilityMoves, LootGameSolverConfig.probabilityThreshold);

        // Sort actions by distance to player so nearest target block is always executed first
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null && actions.size() > 1) {
            final double px = mc.thePlayer.posX;
            final double py = mc.thePlayer.posY + mc.thePlayer.getEyeHeight();
            final double pz = mc.thePlayer.posZ;
            java.util.Collections.sort(actions, new java.util.Comparator<SolverAction>() {

                @Override
                public int compare(SolverAction a1, SolverAction a2) {
                    int[] b1 = getBlockPos(a1.position);
                    int[] b2 = getBlockPos(a2.position);
                    if (b1 == null || b2 == null) return 0;
                    double d1 = (b1[0] + 0.5D - px) * (b1[0] + 0.5D - px) + (b1[1] + 0.5D - py) * (b1[1] + 0.5D - py)
                        + (b1[2] + 0.5D - pz) * (b1[2] + 0.5D - pz);
                    double d2 = (b2[0] + 0.5D - px) * (b2[0] + 0.5D - px) + (b2[1] + 0.5D - py) * (b2[1] + 0.5D - py)
                        + (b2[2] + 0.5D - pz) * (b2[2] + 0.5D - pz);
                    return Double.compare(d1, d2);
                }
            });
        }

        activeActions = actions;
        queued = actions.size();

        if (actions.isEmpty()) {
            if (snapshot.type == MiniGame.SUDOKU) {
                bridge.submitSudokuCheck(snapshot);
            }
            if (snapshot.type == MiniGame.GAME_OF_LIGHT && ("show_sequence".equals(snapshot.gol.getStageId())
                || "under_expanding".equals(snapshot.gol.getStageId()))) {
                activeAction = null;
                status = result.getMessage();
                return;
            }
            if (mode == ActionPlanner.Mode.SINGLE_STEP) {
                mode = null;
            }
            activeAction = null;
            status = result.getMessage()
                + (result.getStatus() == SolveResult.Status.PROBABILISTIC ? " (guess disabled)" : "");
            return;
        }

        SolverAction nextAction = actions.get(0);
        if (activeAction == null || !nextAction.position.equals(activeAction.position)
            || remainingClicksForActiveAction <= 0) {
            activeAction = nextAction;
            if (activeAction.type == SolverAction.Type.SET_VALUE) {
                int curVal = (snapshot.sudokuPlayerValues != null)
                    ? snapshot.sudokuPlayerValues[activeAction.position.y][activeAction.position.x]
                    : 0;
                remainingClicksForActiveAction = calculateClicksNeeded(curVal, activeAction.value);
            } else {
                remainingClicksForActiveAction = 1;
            }
        } else {
            activeAction = nextAction;
        }

        String actionName = activeAction.type == SolverAction.Type.REVEAL ? "Revealing"
            : activeAction.type == SolverAction.Type.FLAG ? "Flagging"
                : activeAction.type == SolverAction.Type.SET_VALUE ? "Setting " + activeAction.value : "Interacting";
        status = actionName + " (" + actions.size() + " remaining)";
        send(snapshot);
    }

    private void send(DetectedGame snapshot) {
        if (activeAction.type == SolverAction.Type.SET_VALUE && remainingClicksForActiveAction <= 0) {
            return;
        }
        if (!bridge.interact(snapshot, activeAction)) {
            status = "Navigating to " + activeAction.position;
            return;
        }
        if (activeAction.type == SolverAction.Type.SET_VALUE) {
            remainingClicksForActiveAction--;
            if (remainingClicksForActiveAction <= 0 && snapshot.type == MiniGame.SUDOKU) {
                optimisticSudokuValues[activeAction.position.y][activeAction.position.x] = activeAction.value;
            }
        }
        beforeActionSignature = snapshot.signature;
        lastActionAt = System.currentTimeMillis();
    }

    public boolean isActionComplete(DetectedGame snapshot) {
        if (activeAction == null) return true;
        if (activeAction.type == SolverAction.Type.SET_VALUE) {
            return remainingClicksForActiveAction <= 0;
        }
        return bridge.actionComplete(snapshot, activeAction);
    }

    public void cancel(String reason) {
        mode = null;
        activeAction = null;
        activeActions = null;
        beforeActionSignature = null;
        queued = 0;
        remainingClicksForActiveAction = 0;
        for (int r = 0; r < 9; r++) {
            java.util.Arrays.fill(optimisticSudokuValues[r], 0);
        }
        status = reason;
    }

    public boolean isActive() {
        return mode != null;
    }

    public String getStatus() {
        return status;
    }

    public MiniGame getGame() {
        return game;
    }

    public int getQueued() {
        return queued;
    }

    public DetectedGame getCurrentDetectedGame() {
        return currentSnapshot != null ? currentSnapshot : bridge.detect();
    }

    public List<SolverAction> computeActionsForCurrentGame() {
        DetectedGame snapshot = getCurrentDetectedGame();
        if (snapshot == null) return java.util.Collections.emptyList();
        SolveResult result = snapshot.type == MiniGame.MINESWEEPER ? minesweeperSolver.solve(snapshot.minesweeper)
            : snapshot.type == MiniGame.SUDOKU ? sudokuSolver.solve(snapshot.sudoku) : golSolver.solve(snapshot.gol);
        return planner.plan(
            result,
            ActionPlanner.Mode.SOLVE,
            LootGameSolverConfig.allowProbabilityMoves,
            LootGameSolverConfig.probabilityThreshold);
    }

    public List<SolverAction> getActiveActions() {
        if (activeActions != null && !activeActions.isEmpty()) return activeActions;
        DetectedGame cur = getCurrentDetectedGame();
        if (cur != null) {
            SolveResult res = cur.type == MiniGame.MINESWEEPER ? minesweeperSolver.solve(cur.minesweeper)
                : cur.type == MiniGame.SUDOKU ? sudokuSolver.solve(cur.sudoku) : golSolver.solve(cur.gol);
            return res.getActions();
        }
        return null;
    }

    public int[] getBlockPos(CellPosition position) {
        DetectedGame cur = getCurrentDetectedGame();
        if (cur == null || cur.blockPositions == null) return null;
        return cur.blockPositions.get(position);
    }
}
