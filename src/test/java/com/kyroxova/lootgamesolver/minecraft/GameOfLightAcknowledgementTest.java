package com.kyroxova.lootgamesolver.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;

public class GameOfLightAcknowledgementTest {

    private static final class FakeGame {

        private FakeStage stage = new FakeStage();
        private Object specialMarkAppearance;

        public FakeStage getStage() {
            return stage;
        }
    }

    private static final class FakeStage {}

    private static final class FakeAppearance {

        private final String state;

        private FakeAppearance(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    private static DetectedGame snapshot(FakeGame game, String stage, int currentSymbol) {
        GameOfLightBoard board = new GameOfLightBoard(
            stage,
            Collections.singletonList(new CellPosition(1, 1)),
            3,
            false,
            currentSymbol);
        return DetectedGame.gameOfLight(game, board, stage, null);
    }

    @Test
    public void waitsForAcceptedMarkAndDoesNotAdvanceOnRetry() {
        LootGamesBridge bridge = new LootGamesBridge();
        SolverAction action = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(1, 1), 0, 1D);
        FakeGame game = new FakeGame();
        DetectedGame beforeClick = snapshot(game, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, 0);

        bridge.recordSubmittedAction(beforeClick);
        assertFalse(bridge.actionComplete(beforeClick, action));
        bridge.recordSubmittedAction(beforeClick);
        assertFalse(bridge.actionComplete(beforeClick, action));

        game.specialMarkAppearance = new FakeAppearance("SHOWING");
        assertFalse(bridge.actionComplete(beforeClick, action));
        game.specialMarkAppearance = new FakeAppearance("ACCEPTED");
        assertFalse(bridge.actionComplete(beforeClick, action));
        assertTrue(bridge.actionComplete(snapshot(game, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, 1), action));
    }

    @Test
    public void rejectedMarkDoesNotCompleteOrResendPendingAction() {
        LootGamesBridge bridge = new LootGamesBridge();
        SolverAction action = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(1, 1), 0, 1D);
        FakeGame game = new FakeGame();
        DetectedGame waiting = snapshot(game, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, 0);

        bridge.recordSubmittedAction(waiting);
        game.specialMarkAppearance = new FakeAppearance("DENIED");
        assertFalse(bridge.actionComplete(waiting, action));
        bridge.recordSubmittedAction(waiting);
        game.specialMarkAppearance = new FakeAppearance("ACCEPTED");
        assertFalse(bridge.actionComplete(waiting, action));

        game.stage = new FakeStage();
        assertTrue(bridge.actionComplete(snapshot(game, GameOfLightBoard.STAGE_SHOW_SEQUENCE, 0), action));
    }

    @Test
    public void stageTransitionCompletesActionAndClearsRejectedState() {
        LootGamesBridge bridge = new LootGamesBridge();
        SolverAction action = new SolverAction(SolverAction.Type.REVEAL, new CellPosition(1, 1), 0, 1D);
        FakeGame game = new FakeGame();
        DetectedGame waiting = snapshot(game, GameOfLightBoard.STAGE_WAITING_FOR_SEQUENCE, 0);

        bridge.recordSubmittedAction(waiting);
        game.stage = new FakeStage();
        assertTrue(bridge.actionComplete(snapshot(game, GameOfLightBoard.STAGE_SHOW_SEQUENCE, 0), action));
    }
}
