package com.kyroxova.lootgamesolver.core;

public record SolverAction(Type type, CellPosition position, int value, double confidence) {

    public SolverAction(Type type, CellPosition position) {
        this(type, position, 0, 1.0D);
    }

    public enum Type {
        REVEAL,
        FLAG,
        UNFLAG,
        SET_VALUE
    }
}
