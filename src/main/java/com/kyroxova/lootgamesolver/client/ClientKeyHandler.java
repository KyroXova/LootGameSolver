package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.kyroxova.lootgamesolver.client.puzzle.AutoPuzzleCoordinator;
import com.kyroxova.lootgamesolver.client.storage.InventoryBaseline;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;

public final class ClientKeyHandler {

    public boolean exploreKeyHeld;
    public int exploreHoldTicks;
    public boolean exploreHoldActionDone;
    public boolean pausedOnThisPress;
    public long explorePausedAt;
    private boolean lastAutoSolveKey;
    private boolean lastOverlayKey;

    public boolean isKeyJustPressed(KeyBinding binding, int fallbackCode) {
        if (Minecraft.getMinecraft().currentScreen != null) return false;
        if (binding != null) return binding.isPressed();
        int code = binding != null ? binding.getKeyCode() : fallbackCode;
        if (code > 0 && code < 256) {
            boolean down = Keyboard.isKeyDown(code);
            if (binding == Keybinds.toggleAutoSolve) {
                if (down && !lastAutoSolveKey) {
                    lastAutoSolveKey = true;
                    return true;
                }
                if (!down) lastAutoSolveKey = false;
            } else if (binding == Keybinds.toggleOverlay) {
                if (down && !lastOverlayKey) {
                    lastOverlayKey = true;
                    return true;
                }
                if (!down) lastOverlayKey = false;
            }
        }
        return false;
    }

    public void checkKeyToggles(Minecraft mc, ClientHooks hooks) {
        boolean runExploreAction = updateExploreKeyState(mc, hooks);
        if (hooks.automationPaused) return;
        if (hooks.exploration != null && runExploreAction
            && mc.currentScreen == null
            && mc.thePlayer != null
            && mc.theWorld != null) {
            triggerExploreAction(mc, hooks);
        }
        boolean autoSolvePressed = isKeyJustPressed(Keybinds.toggleAutoSolve, LootGameSolverConfig.solveKey);
        boolean overlayPressed = isKeyJustPressed(Keybinds.toggleOverlay, LootGameSolverConfig.overlayKey);
        if (autoSolvePressed) handleAutoSolveToggle(mc, hooks);
        if (overlayPressed) {
            hooks.session.invalidateDormantCache();
            LootGameSolverConfig.setOverlayVisible(!LootGameSolverConfig.showOverlay);
        }
    }

    private boolean updateExploreKeyState(Minecraft mc, ClientHooks hooks) {
        boolean explorePressed = Keybinds.explore.isPressed();
        boolean exploreDown = GameSettings.isKeyDown(Keybinds.explore);
        boolean active = hooks.session.isActive() || hooks.autoPuzzle.isActive()
            || hooks.rewardReturnPending
            || hooks.storageRecovery != null
            || (hooks.exploration != null && hooks.exploration.navigating());
        boolean acceptsExplore = mc.currentScreen == null || mc.currentScreen instanceof GuiChest
            || mc.currentScreen instanceof GuiInventory;
        if (!acceptsExplore) {
            resetExploreKeyState();
            return false;
        }
        if (exploreDown) {
            handleExploreKeyDown(mc, hooks, active);
            return false;
        }
        return handleExploreKeyRelease(mc, hooks, explorePressed, active);
    }

    private void resetExploreKeyState() {
        exploreKeyHeld = false;
        exploreHoldActionDone = false;
        exploreHoldTicks = 0;
    }

    private void handleExploreKeyDown(Minecraft mc, ClientHooks hooks, boolean active) {
        if (!exploreKeyHeld) {
            exploreHoldTicks = 0;
            pausedOnThisPress = false;
            if (ClientHooks.pauseOnExplorePress(hooks.automationPaused, active)) {
                hooks.setAutomationPaused(mc, true);
                explorePausedAt = System.currentTimeMillis();
                pausedOnThisPress = true;
            }
        }
        exploreKeyHeld = true;
        exploreHoldTicks++;
        if (exploreHoldTicks >= 20 && !exploreHoldActionDone) {
            clearHeldAutomation(mc, hooks);
        }
    }

    private void clearHeldAutomation(Minecraft mc, ClientHooks hooks) {
        exploreHoldActionDone = true;
        hooks.checkpointClearDoneTicks = 12;
        boolean clearedPaused = hooks.automationPaused;
        if (hooks.automationPaused) {
            if (hooks.rewardReturnPending) hooks.rewardLooter.saveCheckpoint(mc);
            cancelRewardAutomation(mc, hooks);
            if (hooks.storageRecovery != null) hooks.storageRecovery.cancel();
            hooks.storageRecovery = null;
            hooks.session.cancel("Automation cleared");
            hooks.tileNavigator.stop();
            hooks.autoPuzzle.reset();
            if (hooks.exploration != null && hooks.exploration.navigating())
                hooks.exploration.stop(mc, "Automation cleared");
            hooks.setAutomationPaused(mc, false);
        } else if (hooks.rewardReturnPending) {
            cancelRewardAutomation(mc, hooks);
        }
        if (hooks.exploration != null && mc.thePlayer != null && mc.theWorld != null)
            hooks.exploration.clearLastCheckpoint(mc);
        if (clearedPaused && hooks.exploration != null && hooks.exploration.navigating())
            hooks.exploration.stop(mc, "Automation cleared");
    }

    private static void cancelRewardAutomation(Minecraft mc, ClientHooks hooks) {
        if (hooks.preLootPacker.active()) hooks.preLootPacker.cancel(mc);
        if (hooks.rewardLooter.active()) hooks.rewardLooter.cancel(mc);
        hooks.rewardReturnPending = false;
        hooks.rewardPreCleanupPending = false;
        hooks.rewardCenter = null;
    }

    private boolean handleExploreKeyRelease(Minecraft mc, ClientHooks hooks, boolean explorePressed, boolean active) {
        if (!exploreKeyHeld && explorePressed && ClientHooks.pauseOnExplorePress(hooks.automationPaused, active)) {
            hooks.setAutomationPaused(mc, true);
            explorePausedAt = System.currentTimeMillis();
            pausedOnThisPress = true;
        }
        boolean runExploreAction = false;
        if (ClientHooks.resumeOnExploreRelease(
            exploreKeyHeld || explorePressed,
            exploreHoldActionDone,
            hooks.automationPaused,
            pausedOnThisPress)) {
            hooks.setAutomationPaused(mc, false);
            if (ClientHooks.quickExploreRecovery(explorePausedAt, System.currentTimeMillis())) {
                if (hooks.session.isActive()) hooks.tileNavigator.stop();
                else if (!hooks.rewardReturnPending && hooks.storageRecovery == null && hooks.exploration != null)
                    hooks.exploration.recoverActiveRoute(mc);
            }
            explorePausedAt = 0;
        } else {
            runExploreAction = !hooks.automationPaused && !pausedOnThisPress
                && (exploreKeyHeld ? !exploreHoldActionDone : explorePressed);
        }
        resetExploreKeyState();
        pausedOnThisPress = false;
        return runExploreAction;
    }

    private void triggerExploreAction(Minecraft mc, ClientHooks hooks) {
        if (!hooks.exploration.navigating() && !hooks.rewardReturnPending) {
            hooks.inventoryBaseline = InventoryBaseline.capture(mc);
            hooks.trashCleanup.setBaseline(hooks.inventoryBaseline);
            hooks.rewardLooter.setBaseline(hooks.inventoryBaseline);
        }
        if (hooks.rewardReturnPending) {
            if (hooks.preLootPacker.active()) hooks.preLootPacker.cancel(mc);
            hooks.rewardLooter.cancel(mc);
            hooks.rewardReturnPending = false;
            if (hooks.exploration.navigating()) hooks.exploration.toggle(mc);
            hooks.exploration.setAutomationStatus("Reward collection stopped");
            return;
        }
        hooks.session.cancel("Exploration selected");
        hooks.releaseMovementKeys(mc);
        hooks.puzzleCompletion.clear();
        hooks.autoPuzzle.reset();
        if (!hooks.exploration.navigating()
            && (hooks.exploration.inDungeonRoom(mc) || hooks.puzzleBridge.nearbyFloorActivator() != null
                || hooks.puzzleBridge.nearbyGame(true) != null)) {
            triggerExploreInRoom(mc, hooks);
            return;
        }
        if (!hooks.exploration.navigating() && hooks.exploration.hasPendingReturn()) {
            hooks.exploration.toggle(mc);
            return;
        }
        int[] center = hooks.puzzleBridge.nearbyFloorActivator();
        if (center == null) {
            Object nearby = hooks.puzzleBridge.nearbyGame(true);
            if (nearby != null) center = hooks.puzzleBridge.gameCenter(nearby);
        }
        if (!hooks.exploration.resumePuzzleInCurrentRoom(mc, center)) hooks.exploration.toggle(mc);
    }

    private void triggerExploreInRoom(Minecraft mc, ClientHooks hooks) {
        hooks.explorationDriven = true;
        int[] floor = hooks.puzzleBridge.nearbyFloorActivator();
        Object nearbyGame = hooks.puzzleBridge.nearbyGame(true);
        DetectedGame board = hooks.puzzleBridge.detect(true);
        int[] boardCenter = floor != null ? floor
            : nearbyGame == null ? null : hooks.puzzleBridge.gameCenter(nearbyGame);
        if (board != null) {
            if (boardCenter != null) hooks.autoPuzzle.setRewardCenter(boardCenter);
            hooks.startPuzzleSolver();
            hooks.exploration.setAutomationStatus("Solving active puzzle in room");
            return;
        }
        if (boardCenter != null) {
            hooks.autoPuzzle.startManual(boardCenter, floor, mc.theWorld, hooks.puzzleCompletion);
            hooks.exploration.setAutomationStatus("Opening puzzle in current room");
            return;
        }
        int[] rewards = ClientHooks.nearbyRewardCenter(mc);
        if (rewards != null) {
            hooks.exploration.prepareRewardCollection(mc, rewards);
            hooks.rewardCenter = rewards;
            hooks.ensureInventoryBaseline(mc);
            hooks.rewardLooter.start(mc, rewards, MiniGame.UNKNOWN);
            hooks.rewardPreCleanupPending = true;
            hooks.rewardPrePackingAttempted = false;
            hooks.rewardReturnPending = true;
            hooks.exploration.setAutomationStatus("Collecting reward chests");
            return;
        }
        hooks.exploration.toggle(mc);
    }

    private void handleAutoSolveToggle(Minecraft mc, ClientHooks hooks) {
        hooks.explorationDriven = false;
        hooks.session.invalidateDormantCache();
        if (hooks.session.isActive()) {
            hooks.session.cancel("Auto-Solver Stopped");
            hooks.releaseMovementKeys(mc);
            if (hooks.autoPuzzle.isSolving()) hooks.autoPuzzle.setState(AutoPuzzleCoordinator.State.FAILED);
            return;
        }
        hooks.captureInventoryBaseline(mc);
        hooks.session.start(ActionPlanner.Mode.SOLVE);
        Object game = hooks.puzzleBridge.nearbyGame(true);
        int level = hooks.puzzleBridge.currentMinesweeperLevel(game);
        if (hooks.session.isActive()) {
            int[] currentCenter = game == null ? hooks.puzzleBridge.nearbyFloorActivator()
                : hooks.puzzleBridge.gameCenter(game);
            if (currentCenter != null) hooks.autoPuzzle.setRewardCenter(currentCenter);
            hooks.puzzleCompletion.begin();
            hooks.autoPuzzle.setState(AutoPuzzleCoordinator.State.SOLVING);
        }
        if (level >= 1 && level <= 4) {
            int[] currentCenter = hooks.puzzleBridge.gameCenter(game);
            if (currentCenter != null) hooks.autoPuzzle.setRewardCenter(currentCenter);
            hooks.puzzleCompletion.begin();
            if (hooks.session.isActive()) {
                hooks.autoPuzzle.setState(AutoPuzzleCoordinator.State.SOLVING);
            } else if (hooks.puzzleBridge.canOpenMinesweeperBoard(game)) {
                hooks.autoPuzzle.setState(AutoPuzzleCoordinator.State.REVEALING_NEXT_STAGE);
            }
        }
    }
}
