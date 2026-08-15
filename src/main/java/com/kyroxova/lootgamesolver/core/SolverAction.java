package com.kyroxova.lootgamesolver.core;

public final class SolverAction {

    public enum Type {
        REVEAL,
        FLAG,
        UNFLAG,
        SET_VALUE
    }

    public final Type type;
    public final CellPosition position;
    public final int value;
    public final double confidence;

    public SolverAction(Type type, CellPosition position) {
        this(type, position, 0, 1.0D);
    }

    public SolverAction(Type type, CellPosition position, int value, double confidence) {
        this.type = type;
        this.position = position;
        this.value = value;
        this.confidence = confidence;
    }
}
