package com.kyroxova.lootgamesolver.client;

final class PuzzleCompletionGate {

    private boolean bound, win, loss, stageCompleted;

    void begin() {
        bound = true;
        win = false;
        loss = false;
        stageCompleted = false;
    }

    void clear() {
        bound = false;
        win = false;
        loss = false;
        stageCompleted = false;
    }

    void message(String translationKey) {
        if (!bound) return;
        if ("msg.lootgames.win".equals(translationKey)) win = true;
        if ("msg.lootgames.lose".equals(translationKey)) loss = true;
        if ("msg.lootgames.stage_complete".equals(translationKey)) stageCompleted = true;
    }

    boolean stageCompleted() {
        return bound && stageCompleted;
    }

    void clearStageCompleted() {
        stageCompleted = false;
    }

    boolean won(boolean loaded) {
        return bound && win && !loss && loaded;
    }

    boolean lost() {
        return bound && loss;
    }
}
