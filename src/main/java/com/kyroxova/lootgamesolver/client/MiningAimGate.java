package com.kyroxova.lootgamesolver.client;

final class MiningAimGate {

    private int x, y, z;
    private int stable;
    private boolean initialized;

    boolean ready(int x, int y, int z, boolean crosshairMatches, double distance, double reach, float yawError,
        float pitchError) {
        if (!initialized || this.x != x || this.y != y || this.z != z) {
            this.x = x;
            this.y = y;
            this.z = z;
            stable = 0;
            initialized = true;
        }
        if (!crosshairMatches || distance > reach || Math.abs(yawError) > 2F || Math.abs(pitchError) > 2F) {
            stable = 0;
            return false;
        }
        return ++stable >= 2;
    }

    void reset() {
        initialized = false;
        stable = 0;
    }
}
