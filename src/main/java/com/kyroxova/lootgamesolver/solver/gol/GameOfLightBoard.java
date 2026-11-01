package com.kyroxova.lootgamesolver.solver.gol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.GameBoard;

public final class GameOfLightBoard implements GameBoard {

    public static final String STAGE_WAITING_START = "waiting_start";
    public static final String STAGE_SHOW_SEQUENCE = "show_sequence";
    public static final String STAGE_WAITING_FOR_SEQUENCE = "waiting_for_sequence";
    public static final String STAGE_UNDER_EXPANDING = "under_expanding";

    private final String stageId;
    private final List<CellPosition> sequence;
    private final int boardSize;
    private final boolean sequenceCompleted;
    private final int currentSymbol;

    public GameOfLightBoard(String stageId, List<CellPosition> sequence) {
        this(stageId, sequence, 3, false, 0);
    }

    public GameOfLightBoard(String stageId, List<CellPosition> sequence, int boardSize) {
        this(stageId, sequence, boardSize, false, 0);
    }

    public GameOfLightBoard(String stageId, List<CellPosition> sequence, int boardSize, boolean sequenceCompleted) {
        this(stageId, sequence, boardSize, sequenceCompleted, 0);
    }

    public GameOfLightBoard(String stageId, List<CellPosition> sequence, int boardSize, boolean sequenceCompleted,
        int currentSymbol) {
        this.stageId = stageId != null ? stageId : "";
        this.sequence = sequence != null ? new ArrayList<CellPosition>(sequence)
            : Collections.<CellPosition>emptyList();
        this.boardSize = boardSize > 0 ? boardSize : 3;
        this.sequenceCompleted = sequenceCompleted;
        this.currentSymbol = currentSymbol;
    }

    public String getStageId() {
        return stageId;
    }

    public List<CellPosition> getSequence() {
        return Collections.unmodifiableList(sequence);
    }

    public int getBoardSize() {
        return boardSize;
    }

    public boolean isSequenceCompleted() {
        return sequenceCompleted;
    }

    public int getCurrentSymbol() {
        return currentSymbol;
    }

    public int getWidth() {
        return boardSize;
    }

    public int getHeight() {
        return boardSize;
    }
}
