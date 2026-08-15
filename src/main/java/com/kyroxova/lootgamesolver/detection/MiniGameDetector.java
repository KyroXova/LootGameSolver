package com.kyroxova.lootgamesolver.detection;

import com.kyroxova.lootgamesolver.minecraft.DetectedGame;

/** Minecraft-facing detector. It must return null rather than guess. */
public interface MiniGameDetector {

    DetectedGame detect();
}
