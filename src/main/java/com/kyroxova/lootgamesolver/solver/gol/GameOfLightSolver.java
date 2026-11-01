package com.kyroxova.lootgamesolver.solver.gol;

import java.util.ArrayList;
import java.util.List;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGameSolver;
import com.kyroxova.lootgamesolver.core.SolveResult;
import com.kyroxova.lootgamesolver.core.SolverAction;

public final class GameOfLightSolver implements MiniGameSolver<GameOfLightBoard> {

    public boolean canSolve(GameOfLightBoard board) {
        return board != null;
    }

    public SolveResult solve(GameOfLightBoard board) {
        if (board == null) return SolveResult.none(SolveResult.Status.INVALID, "No board state");

        String stage = board.getStageId();
        if (GameOfLightBoard.STAGE_WAITING_START.equals(stage)) {
            List<SolverAction> actions = new ArrayList<SolverAction>();
            int cx = board.getWidth() / 2;
            int cy = board.getHeight() / 2;
            actions.add(new SolverAction(SolverAction.Type.REVEAL, new CellPosition(cx, cy), 0, 1.0D));
            return new SolveResult(
                SolveResult.Status.GUARANTEED_SAFE,
                actions,
                1.0D,
                "Click center block to start Game of Light");
        }

        if (GameOfLightBoard.STAGE_SHOW_SEQUENCE.equals(stage)) {
            return SolveResult.none(SolveResult.Status.NO_MOVE, "Watching sequence presentation...");
        }

        if (GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE.equals(stage)) {
            if (board.isSequenceCompleted()) {
                return SolveResult.none(SolveResult.Status.NO_MOVE, "Sequence complete! Waiting for next round...");
            }
            List<CellPosition> seq = board.getSequence();
            if (seq.isEmpty()) {
                return SolveResult.none(SolveResult.Status.NO_MOVE, "Watching sequence presentation...");
            }

            List<SolverAction> actions = new ArrayList<SolverAction>();
            for (CellPosition pos : seq) {
                actions.add(new SolverAction(SolverAction.Type.REVEAL, pos, 0, 1D));
            }
            return new SolveResult(
                SolveResult.Status.GUARANTEED_SAFE,
                actions,
                1D,
                "Game of Light sequence ready (" + seq.size() + " steps)");
        }

        if (GameOfLightBoard.STAGE_UNDER_EXPANDING.equals(stage)) {
            return SolveResult.none(SolveResult.Status.NO_MOVE, "Board expanding...");
        }

        return SolveResult.none(SolveResult.Status.NO_MOVE, "Waiting for game stage: " + stage);
    }
}
