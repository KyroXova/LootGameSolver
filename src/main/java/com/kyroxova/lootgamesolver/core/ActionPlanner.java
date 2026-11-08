package com.kyroxova.lootgamesolver.core;

import java.util.ArrayList;
import java.util.List;

public final class ActionPlanner {

    public List<SolverAction> plan(SolveResult result, Mode mode, boolean allowProbabilityMoves, double threshold) {
        List<SolverAction> planned = new ArrayList<SolverAction>();
        if (result.getStatus() == SolveResult.Status.PROBABILISTIC) {
            double safety = result.getBestSafetyProbability();
            if (!allowProbabilityMoves || Double.isNaN(threshold)
                || Double.isInfinite(threshold)
                || Double.isNaN(safety)
                || safety < 0D
                || safety > 1D
                || safety < threshold) return planned;
        }
        for (SolverAction action : result.getActions()) {
            if (mode != Mode.MARK_MINES || action.type == SolverAction.Type.FLAG) planned.add(action);
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
