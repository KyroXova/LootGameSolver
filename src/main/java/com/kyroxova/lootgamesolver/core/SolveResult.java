package com.kyroxova.lootgamesolver.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record SolveResult(Status status, List<SolverAction> actions, double bestSafetyProbability, String message) {

    public SolveResult(Status status, List<SolverAction> actions, double bestSafetyProbability, String message) {
        this.status = status;
        this.actions = Collections.unmodifiableList(new ArrayList<SolverAction>(actions));
        this.bestSafetyProbability = bestSafetyProbability;
        this.message = message;
    }

    public static SolveResult none(Status status, String message) {
        return new SolveResult(status, Collections.emptyList(), 0D, message);
    }

    public enum Status {
        GUARANTEED_SAFE,
        GUARANTEED_MINE,
        PROBABILISTIC,
        NO_MOVE,
        SOLVED,
        INVALID
    }
}
