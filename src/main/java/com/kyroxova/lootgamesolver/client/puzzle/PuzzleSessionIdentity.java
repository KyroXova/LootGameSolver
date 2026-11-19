package com.kyroxova.lootgamesolver.client.puzzle;

import com.kyroxova.lootgamesolver.core.MiniGame;

public final class PuzzleSessionIdentity {

    public enum Change {
        NONE,
        GAME,
        ROUND
    }

    private Object game;
    private MiniGame type = MiniGame.UNKNOWN;
    private int round = -1;

    public Change update(MiniGame nextType, Object nextGame, int nextRound) {
        if (game == null) {
            game = nextGame;
            type = nextType;
            round = nextRound;
            return Change.NONE;
        }
        if (type != nextType || game != nextGame) {
            game = nextGame;
            type = nextType;
            round = nextRound;
            return Change.GAME;
        }
        boolean roundChanged = type == MiniGame.MINESWEEPER && round > 0 && nextRound > 0 && round != nextRound;
        if (nextRound > 0) round = nextRound;
        return roundChanged ? Change.ROUND : Change.NONE;
    }

    public void clear() {
        game = null;
        type = MiniGame.UNKNOWN;
        round = -1;
    }
}
