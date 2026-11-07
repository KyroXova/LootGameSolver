package com.kyroxova.lootgamesolver.client;

import com.kyroxova.lootgamesolver.core.MiniGame;

final class PuzzleSessionIdentity {

    enum Change {
        NONE,
        GAME,
        ROUND
    }

    private Object game;
    private MiniGame type = MiniGame.UNKNOWN;
    private int round = -1;

    Change update(MiniGame nextType, Object nextGame, int nextRound) {
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

    void clear() {
        game = null;
        type = MiniGame.UNKNOWN;
        round = -1;
    }
}
