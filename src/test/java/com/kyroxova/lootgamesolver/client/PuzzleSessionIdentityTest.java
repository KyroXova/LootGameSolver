package com.kyroxova.lootgamesolver.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.kyroxova.lootgamesolver.core.MiniGame;

public class PuzzleSessionIdentityTest {

    @Test
    public void firstObservationEstablishesIdentity() {
        PuzzleSessionIdentity identity = new PuzzleSessionIdentity();

        assertEquals(PuzzleSessionIdentity.Change.NONE, identity.update(MiniGame.MINESWEEPER, new Object(), 1));
    }

    @Test
    public void newMinesweeperRoundRequestsActionReset() {
        PuzzleSessionIdentity identity = new PuzzleSessionIdentity();
        Object game = new Object();
        identity.update(MiniGame.MINESWEEPER, game, 1);

        assertEquals(PuzzleSessionIdentity.Change.ROUND, identity.update(MiniGame.MINESWEEPER, game, 2));
        assertEquals(PuzzleSessionIdentity.Change.NONE, identity.update(MiniGame.MINESWEEPER, game, 2));
    }

    @Test
    public void replacedGameRequestsSafeStop() {
        PuzzleSessionIdentity identity = new PuzzleSessionIdentity();
        identity.update(MiniGame.SUDOKU, new Object(), -1);

        assertEquals(PuzzleSessionIdentity.Change.GAME, identity.update(MiniGame.MINESWEEPER, new Object(), 1));
    }

    @Test
    public void unknownRoundDoesNotEraseKnownRound() {
        PuzzleSessionIdentity identity = new PuzzleSessionIdentity();
        Object game = new Object();
        identity.update(MiniGame.MINESWEEPER, game, 2);

        assertEquals(PuzzleSessionIdentity.Change.NONE, identity.update(MiniGame.MINESWEEPER, game, -1));
        assertEquals(PuzzleSessionIdentity.Change.ROUND, identity.update(MiniGame.MINESWEEPER, game, 3));
    }
}
