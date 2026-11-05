package com.kyroxova.lootgamesolver.client;

/** Only a server win signal for the current loaded world can confirm a solved structure. */
final class PuzzleCompletionGate {

    private boolean bound, win, loss;

    void begin() {
        bound = true;
        win = false;
        loss = false;
    }

    void clear() {
        bound = false;
        win = false;
        loss = false;
    }

    void message(String translationKey) {
        if (!bound) return;
        if ("msg.lootgames.win".equals(translationKey)) win = true;
        if ("msg.lootgames.lose".equals(translationKey)) loss = true;
    }

    boolean won(boolean loaded) {
        return bound && win && !loss && loaded;
    }

    boolean lost() {
        return bound && loss;
    }
}
