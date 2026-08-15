package com.kyroxova.lootgamesolver.core;

import java.util.ArrayList;
import java.util.List;

/** Keeps all policy choices (mark-only, one step and optional guessing) out of solvers. */
public final class ActionPlanner {

    public enum Mode {
        SOLVE,
        MARK_MINES,
        SINGLE_STEP
    }

    public List<SolverAction> plan(SolveResult result, Mode mode, boolean allowProbabilityMoves, double threshold) {
        List<SolverAction> planned = new ArrayList<SolverAction>();
        if (result.getStatus() == SolveResult.Status.PROBABILISTIC) {
            boolean allow = (mode == Mode.SOLVE) || allowProbabilityMoves;
            if (!allow) return planned;
        }
        for (SolverAction action : result.getActions()) {
            if (mode != Mode.MARK_MINES || action.type == SolverAction.Type.FLAG) planned.add(action);
            if (mode == Mode.SINGLE_STEP && !planned.isEmpty()) break;
        }
        return planned;
    }
}
