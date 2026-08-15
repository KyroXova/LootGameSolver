package com.kyroxova.lootgamesolver.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SolveResult {

    public enum Status {
        GUARANTEED_SAFE,
        GUARANTEED_MINE,
        PROBABILISTIC,
        NO_MOVE,
        SOLVED,
        INVALID
    }

    private final Status status;
    private final List<SolverAction> actions;
    private final double bestSafetyProbability;
    private final String message;

    public SolveResult(Status status, List<SolverAction> actions, double safety, String message) {
        this.status = status;
        this.actions = Collections.unmodifiableList(new ArrayList<SolverAction>(actions));
        this.bestSafetyProbability = safety;
        this.message = message;
    }

    public static SolveResult none(Status status, String message) {
        return new SolveResult(status, Collections.<SolverAction>emptyList(), 0D, message);
    }

    public Status getStatus() {
        return status;
    }

    public List<SolverAction> getActions() {
        return actions;
    }

    public double getBestSafetyProbability() {
        return bestSafetyProbability;
    }

    public String getMessage() {
        return message;
    }
}
