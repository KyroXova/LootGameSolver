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

    public GameOfLightBoard(String stageId, List<CellPosition> sequence) {
        this.stageId = stageId != null ? stageId : "";
        this.sequence = sequence != null ? new ArrayList<CellPosition>(sequence)
            : Collections.<CellPosition>emptyList();
    }

    public String getStageId() {
        return stageId;
    }

    public List<CellPosition> getSequence() {
        return Collections.unmodifiableList(sequence);
    }

    public int getWidth() {
        return 3;
    }

    public int getHeight() {
        return 3;
    }
}
