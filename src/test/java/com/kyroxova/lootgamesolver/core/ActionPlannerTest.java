package com.kyroxova.lootgamesolver.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class ActionPlannerTest {

    private final ActionPlanner planner = new ActionPlanner();
    private final SolverAction reveal = new SolverAction(SolverAction.Type.FLAG, new CellPosition(1, 2), 0, 0.8D);

    @Test
    public void probabilitySettingDisablesGuessesInEveryMode() {
        SolveResult guess = probabilistic(0.99D);
        for (ActionPlanner.Mode mode : ActionPlanner.Mode.values()) {
            assertTrue(
                planner.plan(guess, mode, false, 0.5D)
                    .isEmpty());
        }
    }

    @Test
    public void probabilityThresholdIsInclusiveAndAppliedInEveryMode() {
        SolveResult safeEnough = probabilistic(0.9D);
        SolveResult tooRisky = probabilistic(0.899D);
        for (ActionPlanner.Mode mode : ActionPlanner.Mode.values()) {
            assertEquals(
                1,
                planner.plan(safeEnough, mode, true, 0.9D)
                    .size());
            assertTrue(
                planner.plan(tooRisky, mode, true, 0.9D)
                    .isEmpty());
        }
    }

    @Test
    public void singleStepStillLimitsAcceptedGuessToOneAction() {
        SolveResult guess = new SolveResult(
            SolveResult.Status.PROBABILISTIC,
            Arrays.asList(reveal, new SolverAction(SolverAction.Type.REVEAL, new CellPosition(3, 4), 0, 0.8D)),
            0.95D,
            "Guess");

        assertEquals(
            1,
            planner.plan(guess, ActionPlanner.Mode.SINGLE_STEP, true, 0.9D)
                .size());
    }

    @Test
    public void invalidProbabilityInputsAreRejected() {
        assertTrue(
            planner.plan(probabilistic(Double.NaN), ActionPlanner.Mode.SOLVE, true, 0D)
                .isEmpty());
        assertTrue(
            planner.plan(probabilistic(1.1D), ActionPlanner.Mode.SOLVE, true, 0D)
                .isEmpty());
        assertTrue(
            planner.plan(probabilistic(0.9D), ActionPlanner.Mode.SOLVE, true, Double.NaN)
                .isEmpty());
    }

    private SolveResult probabilistic(double safety) {
        return new SolveResult(SolveResult.Status.PROBABILISTIC, Collections.singletonList(reveal), safety, "Guess");
    }
}
