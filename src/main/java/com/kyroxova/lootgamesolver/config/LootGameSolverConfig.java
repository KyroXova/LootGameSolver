package com.kyroxova.lootgamesolver.config;

import com.kyroxova.lootgamesolver.explorer.Explorer;
import java.io.File;

import net.minecraftforge.common.config.Configuration;

import org.lwjgl.input.Keyboard;

public final class LootGameSolverConfig {

    public static boolean enabled = true;
    public static int overlayKey = Keyboard.KEY_H; // H key (35)
    public static int solveKey = Keyboard.KEY_R; // R key (19)
    public static int clickDelayMs = 120;
    public static int boardUpdateTimeoutMs = 1500;
    public static boolean allowProbabilityMoves = true;
    public static double probabilityThreshold = 0.90D;
    public static boolean showOverlay = true;
    private static Configuration configuration;
    public static boolean showDebugInfo = false;
    public static int renderStyle = 0; // 0 = Minimal Outline & Text, 1 = Text Only
    public static int exploreKey = Keyboard.KEY_G;
    public static int explorationRange = 256;
    public static int explorationSearchLimit = 400;
    public static boolean explorationEnabled = true;
    public static boolean staircaseEnabled = true;
    public static boolean caveMode = false;
    public static int spiralWidth = 3;
    public static boolean discardAllMinedOres = true;
    public static String[] discardOreItems = new String[0];
    public static String storageDestination = "";
    public static String storageWorld = "";
    public static String[] storageTargets = new String[0];
    public static boolean storageEnabled = false;

    private LootGameSolverConfig() {}

    public static void load(File file) {
        Configuration cfg = new Configuration(file);
        configuration = cfg;
        storageDestination = cfg.getString("destination", "storageRecovery", "", "Configured home or warp command.");
        storageWorld = cfg.getString("world", "storageRecovery", "", "World associated with storage configuration.");
        storageTargets = cfg.getStringList("targets", "storageRecovery", new String[0], "Marked storage locations.");
        storageEnabled = cfg
            .getBoolean("enabled", "storageRecovery", false, "Enable configured storage recovery when available.");
        enabled = cfg.getBoolean("enabled", Configuration.CATEGORY_GENERAL, enabled, "Master switch.");
        explorationEnabled = cfg
            .getBoolean("enabled", "explorationBeta", true, "Enable experimental nearby finder and walking.");
        caveMode = cfg.getBoolean(
            "caveMode",
            "explorationBeta",
            false,
            "Allow Explorer to travel through caves between structures.");
        staircaseEnabled = cfg.getBoolean(
            "staircaseEnabled",
            "explorationBeta",
            true,
            "Allow checked tunnel and stair access while preserving return-path floors.");
        spiralWidth = cfg.getInt(
            "spiralWidth",
            "explorationBeta",
            3,
            3,
            4,
            "Outer spiral footprint: 3 gives two steps per turn; 4 gives three steps per turn.");
        explorationRange = cfg
            .getInt("routeRange", "explorationBeta", 256, 16, 512, "Maximum horizontal route search radius.");
        explorationSearchLimit = cfg.getInt(
            "searchLimit",
            "explorationBeta",
            400,
            16,
            1024,
            "Progressive search radius limit for discovering unopened structures.");
        discardAllMinedOres = cfg.getBoolean(
            "discardAllMinedOres",
            "inventory",
            discardAllMinedOres,
            "Discard newly acquired ore-dictionary ores during automatic cleanup.");
        discardOreItems = cfg.getStringList(
            "discardOreItems",
            "inventory",
            discardOreItems,
            "Registry names of newly acquired ores allowed to be discarded when discardAllMinedOres is false.");
        exploreKey = cfg
            .getInt("exploreKey", "keybinds", exploreKey, 0, 255, "Default walk toggle; also rebind in Controls.");
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
            "Allow probabilistic moves when their safety meets the configured threshold.");
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

    public static void setOverlayVisible(boolean visible) {
        if (showOverlay == visible) return;
        showOverlay = visible;
        if (configuration != null) {
            configuration.get("display", "showOverlay", true)
                .set(visible);
            configuration.save();
        }
    }

    public static void setCaveMode(boolean enabled) {
        caveMode = enabled;
        if (configuration != null) {
            configuration.get("explorationBeta", "caveMode", false)
                .set(enabled);
            configuration.save();
        }
    }

    public static void setStorageDestination(String world, String command) {
        if (!world.equals(storageWorld)) storageTargets = new String[0];
        storageWorld = world;
        storageDestination = command;
        saveStorage();
    }

    public static void setStorageTargets(String[] targets) {
        storageTargets = targets.clone();
        saveStorage();
    }

    public static void setStorageEnabled(boolean enabled) {
        storageEnabled = enabled;
        saveStorage();
    }

    private static void saveStorage() {
        if (configuration == null) return;
        configuration.get("storageRecovery", "world", "")
            .set(storageWorld);
        configuration.get("storageRecovery", "destination", "")
            .set(storageDestination);
        configuration.get("storageRecovery", "targets", new String[0])
            .set(storageTargets);
        configuration.get("storageRecovery", "enabled", false)
            .set(storageEnabled);
        configuration.save();
    }
}
