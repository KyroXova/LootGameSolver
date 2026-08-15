package com.kyroxova.lootgamesolver.core;

public interface MiniGameSolver<T extends GameBoard> {

    boolean canSolve(T board);

    SolveResult solve(T board);
}
