package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class Keybinds {

    public static KeyBinding toggleOverlay;
    public static KeyBinding toggleAutoSolve;
    public static KeyBinding explore;

    private Keybinds() {}

    public static void register() {
        toggleOverlay = register("key.lootgamesolver.toggle_overlay", LootGameSolverConfig.overlayKey);
        toggleAutoSolve = register("key.lootgamesolver.toggle_auto_solve", LootGameSolverConfig.solveKey);
        if (Explorer.ENABLED) {
            explore = register("key.lootgamesolver.explore", LootGameSolverConfig.exploreKey);
        }
    }

    private static KeyBinding register(String name, int configuredKey) {
        KeyBinding binding = new KeyBinding(
            name,
            configuredKey == 0 ? Keyboard.KEY_NONE : configuredKey,
            "key.categories.lootgamesolver");
        ClientRegistry.registerKeyBinding(binding);
        return binding;
    }
}
