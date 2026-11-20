package com.kyroxova.lootgamesolver.explorer;

import com.kyroxova.lootgamesolver.explorer.excavation.BlockPlacer;
import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationControlPolicy;
import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationInput;
import com.kyroxova.lootgamesolver.explorer.structure.SavedStructureFinder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.tileentity.TileEntity;

import com.kyroxova.lootgamesolver.client.BaritoneNavigationBridge;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

public class ExplorerSessionController {

    public boolean checkPreTick(Minecraft mc, Explorer explorer, boolean solverActive, boolean roomWorkActive) {
        explorer.roomWorkActive = roomWorkActive;
        if (explorer.inventoryMaintenance && mc.thePlayer != null && mc.theWorld != null) {
            explorer.baritoneNavigation.setPaused(true);
            if (explorer.navigationInput != null) {
                explorer.navigationInput.forward = 0;
                explorer.navigationInput.sideways = 0;
                explorer.navigationInput.hop = false;
            }
            return true;
        }
        boolean isMining = (explorer.baritoneCoordinator.active() && explorer.baritoneCoordinator.breaking)
            || explorer.digging
            || explorer.returnDigging
            || (mc.gameSettings != null && GameSettings.isKeyDown(mc.gameSettings.keyBindAttack));
        if (isMining) explorer.inventoryMiningCooldownTicks = 30;
        else if (explorer.inventoryMiningCooldownTicks > 0) explorer.inventoryMiningCooldownTicks--;
        if (isMining) {
            explorer.miningCameraCooldownTicks = 40;
            explorer.sustain.notifyBlockMined();
        } else if (explorer.miningCameraCooldownTicks > 0) {
            explorer.miningCameraCooldownTicks--;
        }
        if (explorer.baritoneCoordinator.active() && mc.thePlayer != null && mc.theWorld != null) {
            if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled || solverActive) {
                explorer.stop(mc, solverActive ? "Puzzle in progress" : "Exploration disabled");
                return true;
            }
            if (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 150) {
                explorer.baritoneNavigation.stop();
                explorer.baritoneCoordinator.reset();
                if (!explorer.swimToNearestShore(mc)) explorer.stop(mc, "Low Air");
                return true;
            }
            boolean manual = NavigationControlPolicy.hasManualMovement(
                GameSettings.isKeyDown(mc.gameSettings.keyBindForward),
                GameSettings.isKeyDown(mc.gameSettings.keyBindBack),
                GameSettings.isKeyDown(mc.gameSettings.keyBindLeft),
                GameSettings.isKeyDown(mc.gameSettings.keyBindRight),
                GameSettings.isKeyDown(mc.gameSettings.keyBindSneak),
                GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
            boolean interrupted = manual || mc.currentScreen != null
                || mc.thePlayer.hurtTime > 0
                || mc.thePlayer.capabilities.isFlying;
            if (explorer.baritoneCoordinator.breaking) explorer.sustain.notifyBlockMined();
            boolean sustaining = explorer.sustain.busy();
            if (!interrupted || sustaining) sustaining = explorer.sustain.tick(mc, explorer.walking, !explorer.baritoneCoordinator.breaking);
            explorer.baritoneNavigation.setPaused(interrupted || sustaining);
            if (interrupted || sustaining) {
                explorer.status = sustaining ? "Eating or preparing food"
                    : manual ? "Paused: release movement keys to resume"
                        : mc.currentScreen != null ? "Paused: close menu to resume"
                            : mc.thePlayer.hurtTime > 0 ? "Paused after damage"
                                : "Paused: land and disable flight to resume";
                return true;
            }
        } else {
            if (explorer.digging || explorer.returnDigging) explorer.sustain.notifyBlockMined();
            explorer.sustain.tick(mc, explorer.walking, explorer.walking && !explorer.digging && !explorer.returnDigging);
        }
        boolean baritoneActive = explorer.baritoneCoordinator.active()
            || explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING;
        if (baritoneActive && explorer.navigationInput != null) {
            if (mc.thePlayer != null && mc.thePlayer.movementInput == explorer.navigationInput) {
                mc.thePlayer.movementInput = explorer.navigationInput.original;
            }
            explorer.navigationInput = null;
        }
        if (explorer.baritoneCoordinator.tick(mc, explorer)) return true;
        if (!explorer.digging && !explorer.returnDigging) explorer.miningDriver.releaseMiningInput(mc, explorer);
        baritoneActive = explorer.baritoneCoordinator.active()
            || explorer.baritoneNavigation.state() == BaritoneNavigationBridge.State.RUNNING;
        if (explorer.walking && !baritoneActive && mc.thePlayer != null && explorer.navigationInput == null) {
            explorer.navigationInput = new NavigationInput(mc.thePlayer.movementInput);
            mc.thePlayer.movementInput = explorer.navigationInput;
        } else if (baritoneActive && explorer.navigationInput != null) {
            if (mc.thePlayer != null && mc.thePlayer.movementInput == explorer.navigationInput) {
                mc.thePlayer.movementInput = explorer.navigationInput.original;
            }
            explorer.navigationInput = null;
        }
        if (explorer.navigationInput != null) {
            explorer.navigationInput.forward = 0;
            explorer.navigationInput.sideways = 0;
            explorer.navigationInput.hop = false;
        }
        if (explorer.world != mc.theWorld) {
            explorer.sustain.stop(mc);
            explorer.persistAccessTrail(true);
            if (explorer.navigationInput != null) explorer.stop(mc, "World changed");
            if (explorer.savedFinder != null) explorer.savedFinder.cancel();
            explorer.savedFinder = null;
            explorer.world = mc.theWorld;
            explorer.target = null;
            explorer.knownStructure = null;
            explorer.trail.points.clear();
            explorer.trail.target = null;
            explorer.puzzleArrivalPending = false;
            explorer.puzzleCenterArrivalPending = false;
            explorer.movingToPuzzleCenter = false;
            explorer.puzzleMasterTarget = null;
            explorer.resumeSearchAfterReturn = false;
            explorer.planningExistingAccess = false;
            explorer.existingAccessPlan = null;
            explorer.returning = false;
            explorer.returnAvailable = false;
            explorer.excavationTerrain.clearSupports();
            explorer.roomGeometry.reset();
            explorer.roomEntryCandidates = null;
            explorer.activeRoomEntry = null;
            explorer.roomEntryPlanning = false;
            explorer.roomEntryCrossing = false;
            explorer.roomEntrySearchNeedsResume = false;
            explorer.walkingSearchNeedsResume = false;
            explorer.chestPitNav.reset();
            explorer.routeRetryTicks = 0;
            explorer.excavationTerrain.clearToolCache();
            explorer.walking = false;
            explorer.route.clear();
            explorer.search = null;
            explorer.searching = false;
            explorer.explorationOriginX = Integer.MIN_VALUE;
            explorer.explorationOriginZ = Integer.MIN_VALUE;
            explorer.sessionVisitedChunks.clear();
            if (explorer.world != null && mc.isSingleplayer() && mc.getIntegratedServer() != null) {
                java.io.File save = new java.io.File(
                    new java.io.File(mc.mcDataDir, "saves"),
                    mc.getIntegratedServer()
                        .getFolderName());
                String dimension = explorer.world.provider.getSaveFolder();
                if (dimension != null) save = new java.io.File(save, dimension);
                explorer.savedFinder = new SavedStructureFinder(new java.io.File(save, "region"));
            }
        }
        if (mc.thePlayer == null || mc.theWorld == null) return true;
        int pcx = ((int) Math.floor(mc.thePlayer.posX)) >> 4;
        int pcz = ((int) Math.floor(mc.thePlayer.posZ)) >> 4;
        explorer.markChunkVisited(pcx, pcz, 2);
        if (explorer.sustain.busy() && explorer.sustain.tick(mc, explorer.walking, false)) {
            explorer.status = "Eating or preparing food";
            return true;
        }
        if (explorer.toolTransfer.active()) {
            explorer.status = "Moving tool using inventory screen";
            if (!explorer.toolTransfer.tick(mc)) explorer.stop(mc, "Inventory transfer interrupted; inspect the tool slots");
            return true;
        }
        BlockPlacer.Outcome placement = explorer.blockPlacer.tick(mc, explorer.returning, explorer.route);
        if (placement == BlockPlacer.Outcome.WAITING) {
            explorer.status = "Waiting for return step placement";
            return true;
        }
        if (placement == BlockPlacer.Outcome.FAILED) {
            explorer.stop(mc, "Could not place a safe return step; add cobblestone or dirt");
            return true;
        }
        if (explorer.ticks % 20 == 0) explorer.excavationTerrain.clearToolCache();
        explorer.updateRoutePreview(mc);
        if (++explorer.ticks % 100 == 0 && !explorer.walking && !roomWorkActive) explorer.scan(mc);
        if (explorer.walking && explorer.searching && !roomWorkActive && explorer.ticks % 40 == 0) {
            TileEntity previous = explorer.target;
            explorer.scan(mc);
            if (explorer.target != null) {
                explorer.searching = false;
                explorer.route.clear();
            } else explorer.target = previous;
        }
        if (!explorer.walking) return true;
        if (solverActive) {
            explorer.pausedForControl = true;
            mc.thePlayer.setSprinting(false);
            if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSprint.getKeyCode(),
                    net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
            }
            explorer.status = "Puzzle in progress";
            return true;
        }
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.explorationEnabled) {
            explorer.stop(mc, "Exploration disabled");
            return true;
        }
        if (mc.thePlayer.isInWater() && mc.thePlayer.getAir() < 150) {
            if (explorer.swimToNearestShore(mc)) return true;
            explorer.stop(mc, "Low Air");
            return true;
        }
        boolean manual = NavigationControlPolicy.hasManualMovement(
            GameSettings.isKeyDown(mc.gameSettings.keyBindForward),
            GameSettings.isKeyDown(mc.gameSettings.keyBindBack),
            GameSettings.isKeyDown(mc.gameSettings.keyBindLeft),
            GameSettings.isKeyDown(mc.gameSettings.keyBindRight),
            GameSettings.isKeyDown(mc.gameSettings.keyBindSneak),
            GameSettings.isKeyDown(mc.gameSettings.keyBindJump));
        if (manual || mc.currentScreen != null || mc.thePlayer.hurtTime > 0 || mc.thePlayer.capabilities.isFlying) {
            explorer.pausedForControl = true;
            mc.thePlayer.setSprinting(false);
            if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(
                    mc.gameSettings.keyBindSprint.getKeyCode(),
                    net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
            }
            mc.playerController.resetBlockRemoving();
            explorer.miningAim.reset();
            explorer.status = manual ? "Paused: release movement keys to resume"
                : mc.currentScreen != null ? "Paused: close menu to resume"
                    : mc.thePlayer.hurtTime > 0 ? "Paused after damage" : "Paused: land and disable flight to resume";
            return true;
        }
        if (explorer.sustain.tick(mc, true, !explorer.route.isEmpty() && !explorer.digging && !explorer.returnDigging && !explorer.blockPlacer.selectingBlock())) {
            explorer.status = "Eating or preparing food";
            return true;
        }
        return false;
    }
}
