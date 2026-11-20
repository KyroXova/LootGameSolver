package com.kyroxova.lootgamesolver.client.puzzle;

public final class PuzzleCompletionGate {

    private boolean bound, win, loss, stageCompleted;

    public void begin() {
        bound = true;
        win = false;
        loss = false;
        stageCompleted = false;
    }

    public void clear() {
        bound = false;
        win = false;
        loss = false;
        stageCompleted = false;
    }

    public void message(String translationKey) {
        if (!bound) return;
        if ("msg.lootgames.win".equals(translationKey)) win = true;
        if ("msg.lootgames.lose".equals(translationKey)) loss = true;
        if ("msg.lootgames.stage_complete".equals(translationKey)) stageCompleted = true;
    }

    public boolean stageCompleted() {
        return bound && stageCompleted;
    }

    public void clearStageCompleted() {
        stageCompleted = false;
    }

    public boolean won(boolean loaded) {
        return bound && win && !loss && loaded;
    }

    public boolean lost() {
        return bound && loss;
    }
}
