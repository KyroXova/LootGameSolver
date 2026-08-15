package com.kyroxova.lootgamesolver.config;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import org.lwjgl.input.Keyboard;

/** Forge 1.7.10 configuration. Key values are LWJGL key codes. */
public final class LootGameSolverConfig {

    public static boolean enabled = true;
    public static int overlayKey = Keyboard.KEY_H; // H key (35)
    public static int solveKey = Keyboard.KEY_R; // R key (19)
    public static int clickDelayMs = 120;
    public static int boardUpdateTimeoutMs = 1500;
    public static boolean allowProbabilityMoves = true;
    public static double probabilityThreshold = 0.90D;
    public static boolean showOverlay = true;
    public static boolean showDebugInfo = false;
    public static int renderStyle = 0; // 0 = Minimal Outline & Text, 1 = Text Only

    private LootGameSolverConfig() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        enabled = cfg.getBoolean("enabled", Configuration.CATEGORY_GENERAL, enabled, "Master switch.");
        overlayKey = cfg.getInt("overlayKey", "keybinds", overlayKey, 0, 255, "LWJGL key code for toggling holograms.");
        solveKey = cfg.getInt("solveKey", "keybinds", solveKey, 0, 255, "LWJGL key code for toggling auto-solver.");
        clickDelayMs = cfg
            .getInt("clickDelay", "safety", clickDelayMs, 0, 5000, "Minimum delay between interactions (ms).");
        boardUpdateTimeoutMs = cfg.getInt(
            "boardUpdateTimeout",
            "safety",
            boardUpdateTimeoutMs,
            250,
            30000,
            "Stop if an interaction is not reflected by the client board in this time.");
        allowProbabilityMoves = cfg.getBoolean(
            "allowProbabilityMoves",
            "solver",
            allowProbabilityMoves,
            "Allow guesses. Disabled by default.");
        probabilityThreshold = cfg.getFloat(
            "probabilityThreshold",
            "solver",
            (float) probabilityThreshold,
            0.0F,
            1.0F,
            "Minimum safety probability for a guess.");
        showOverlay = cfg.getBoolean("showOverlay", "display", showOverlay, "Show compact solver status.");
        showDebugInfo = cfg.getBoolean("showDebugInfo", "display", showDebugInfo, "Show debug information.");
        if (cfg.hasChanged()) cfg.save();
    }
}
