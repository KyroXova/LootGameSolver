package com.kyroxova.lootgamesolver.core;

import java.util.ArrayList;
import java.util.List;
public final class ActionPlanner {

    public List<SolverAction> plan(SolveResult result, Mode mode, boolean allowProbabilityMoves, double threshold) {
        List<SolverAction> planned = new ArrayList<SolverAction>();
        if (result.status() == SolveResult.Status.PROBABILISTIC) {
            boolean allow = (mode == Mode.SOLVE) || allowProbabilityMoves;
            if (!allow) return planned;
        }
        for (SolverAction action : result.actions()) {
            if (mode != Mode.MARK_MINES || action.type() == SolverAction.Type.FLAG) planned.add(action);
            if (mode == Mode.SINGLE_STEP && !planned.isEmpty()) break;
        }
        return planned;
    }

    public enum Mode {
        SOLVE,
        MARK_MINES,
        SINGLE_STEP
    }
}
