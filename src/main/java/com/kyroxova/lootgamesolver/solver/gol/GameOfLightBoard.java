package com.kyroxova.lootgamesolver.solver.gol;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.GameBoard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record GameOfLightBoard(String stageId, List<CellPosition> sequence, int boardSize, boolean sequenceCompleted,
                               int currentSymbol) implements GameBoard {

    public static final String STAGE_WAITING_START = "waiting_start";
    public static final String STAGE_SHOW_SEQUENCE = "show_sequence";
    public static final String STAGE_WAITING_FOR_SEQUENCE = "waiting_for_sequence";
    public static final String STAGE_UNDER_EXPANDING = "under_expanding";

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
            : Collections.emptyList();
        this.boardSize = boardSize > 0 ? boardSize : 3;
        this.sequenceCompleted = sequenceCompleted;
        this.currentSymbol = currentSymbol;
    }

    @Override
    public List<CellPosition> sequence() {
        return Collections.unmodifiableList(sequence);
    }

    public int getWidth() {
        return boardSize;
    }

    public int getHeight() {
        return boardSize;
    }
}
