package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.kyroxova.lootgamesolver.client.loot.MinedDropStore;
import com.kyroxova.lootgamesolver.client.loot.MinedDropTracker;
import com.kyroxova.lootgamesolver.client.loot.PendingLootStore;
import com.kyroxova.lootgamesolver.client.loot.RewardChestLooter;
import com.kyroxova.lootgamesolver.client.puzzle.AutoPuzzleCoordinator;
import com.kyroxova.lootgamesolver.client.puzzle.PuzzleCompletionGate;
import com.kyroxova.lootgamesolver.client.puzzle.PuzzleTileNavigator;
import com.kyroxova.lootgamesolver.client.puzzle.RoomOpenWatchdog;
import com.kyroxova.lootgamesolver.client.puzzle.SolverSession;
import com.kyroxova.lootgamesolver.client.storage.InventoryBaseline;
import com.kyroxova.lootgamesolver.client.storage.StorageRecoveryController;
import com.kyroxova.lootgamesolver.client.storage.TrashCleanup;
import com.kyroxova.lootgamesolver.client.ui.ClientHudOverlay;
import com.kyroxova.lootgamesolver.client.ui.StatusText;
import com.kyroxova.lootgamesolver.client.ui.WorldOverlayRenderer;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;

public final class ClientHooks {

    private static boolean resumeLootRequested;
    private static boolean travelModeRefreshRequested;
    private static boolean clearCheckpointRequested;

    public static void requestClearCheckpoint() {
        clearCheckpointRequested = true;
    }

    public static void requestTravelModeRefresh() {
        travelModeRefreshRequested = true;
    }

    public static void requestResumeLoot() {
        resumeLootRequested = true;
    }

    private static final ResourceLocation TEXTURE_UI_9SLICE = new ResourceLocation(
        "lootgamesolver",
        "textures/gui/ui_9slice.png");
    public final SolverSession session = new SolverSession();
    public final Explorer exploration = Explorer.ENABLED ? new Explorer() : null;
    private final WorldOverlayRenderer worldRenderer = new WorldOverlayRenderer(session, exploration);
    public final ClientKeyHandler keyHandler = new ClientKeyHandler();
    private int detectCounter = 0;
    public final LootGamesBridge puzzleBridge = new LootGamesBridge();
    public final PuzzleCompletionGate puzzleCompletion = new PuzzleCompletionGate();
    public final PuzzleTileNavigator tileNavigator = new PuzzleTileNavigator();
    private final RoomOpenWatchdog roomOpenWatchdog = new RoomOpenWatchdog();
    public final RewardChestLooter rewardLooter = new RewardChestLooter();
    public final RewardChestLooter preLootPacker = new RewardChestLooter();
    public final TrashCleanup trashCleanup = new TrashCleanup();
    private final MinedDropTracker minedDrops = new MinedDropTracker();
    private MinedDropStore minedDropStore;
    public StorageRecoveryController storageRecovery;
    private boolean storageResumeChecked;
    private int storageResumeWait;
    public boolean rewardReturnPending;
    public boolean rewardPreCleanupPending;
    public boolean rewardPrePackingAttempted;
    private int inventoryMaintenanceStage;
    private int inventoryMaintenanceWait;
    private int inventoryMaintenanceCooldown;
    private int rewardLooterCooldown;
    public boolean explorationDriven;
    public InventoryBaseline inventoryBaseline;
    public int[] rewardCenter;
    public boolean automationPaused;
    public int checkpointClearDoneTicks;
    public boolean wasInsideRoom;
    private World puzzleWorld;
    public long totalActiveTicks = 0L;
    public int solvedPuzzles;

    public final AutoPuzzleCoordinator autoPuzzle = new AutoPuzzleCoordinator();

    public void recordPuzzleSolved() {
        solvedPuzzles++;
    }

    public static boolean explorerHudActive(boolean navigating, boolean looting, boolean explorationDriven,
        boolean solving, boolean opening, boolean storage) {
        return navigating || looting || (explorationDriven && (solving || opening || storage));
    }

    public static boolean pauseOnExplorePress(boolean paused, boolean active) {
        return !paused && active;
    }

    public static boolean resumeOnExploreRelease(boolean wasHeld, boolean holdAction, boolean paused,
        boolean pausedOnPress) {
        return wasHeld && !holdAction && paused && !pausedOnPress;
    }

    public static boolean quickExploreRecovery(long pausedAt, long now) {
        return pausedAt > 0 && now >= pausedAt && now - pausedAt <= 2000;
    }

    public static void drawNineSlice(ResourceLocation texture, int x, int y, int width, int height, Minecraft mc) {
        ClientHudOverlay.drawNineSlice(texture, x, y, width, height, mc);
    }

    public WorldOverlayRenderer getWorldRenderer() {
        return worldRenderer;
    }

    public Explorer getExploration() {
        return exploration;
    }

    @SubscribeEvent
    public void onKeyInput(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {}

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.world != null && event.world.isRemote) {
            session.invalidateDormantCache();
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        if (event == null || event.message == null) return;
        if (storageRecovery != null) storageRecovery.onChat(event.message);
        if (autoPuzzle.isSolving()) capturePuzzleMessage(event.message);
    }

    private void capturePuzzleMessage(IChatComponent message) {
        if (message instanceof ChatComponentTranslation)
            puzzleCompletion.message(((ChatComponentTranslation) message).getKey());
        else if (net.minecraft.util.StatCollector.translateToLocal("msg.lootgames.win")
            .equals(message.getUnformattedText())) puzzleCompletion.message("msg.lootgames.win");
        else if (net.minecraft.util.StatCollector.translateToLocal("msg.lootgames.stage_complete")
            .equals(message.getUnformattedText())) puzzleCompletion.message("msg.lootgames.stage_complete");
        for (IChatComponent sibling : message.getSiblings()) capturePuzzleMessage(sibling);
    }

    @SubscribeEvent
    public void renderTick(cpw.mods.fml.common.gameevent.TickEvent.RenderTickEvent event) {
        if (event.phase == cpw.mods.fml.common.gameevent.TickEvent.Phase.START && exploration != null)
            exploration.renderCamera(Minecraft.getMinecraft());
    }

    @SubscribeEvent
    public void clientTick(ClientTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;
        if (travelModeRefreshRequested && exploration != null && !automationPaused) {
            travelModeRefreshRequested = false;
            exploration.refreshTravelMode(Minecraft.getMinecraft());
        }

        Minecraft mc = Minecraft.getMinecraft();
        keyHandler.checkKeyToggles(mc, this);
        if (automationPaused && mc.theWorld != null && mc.thePlayer != null) return;
        if (mc.theWorld == null || mc.thePlayer == null) automationPaused = false;
        if (storageRecovery != null) {
            if (exploration != null) exploration.setAutomationStatus(storageRecovery.status());
            StorageRecoveryController.Result result = storageRecovery.tick(mc);
            if (result == StorageRecoveryController.Result.WORKING) return;
            PendingLootStore.Checkpoint checkpoint = storageRecovery.checkpoint();
            String storageFailure = storageRecovery.failureReason();
            storageRecovery = null;
            puzzleWorld = mc.theWorld;
            if (result == StorageRecoveryController.Result.RETURNED && mc.theWorld != null && mc.thePlayer != null) {
                rewardCenter = checkpoint.center.clone();
                rewardLooter.start(mc, rewardCenter, checkpoint.game);
                rewardPreCleanupPending = false;
                rewardReturnPending = true;
                explorationDriven = exploration != null;
                mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7a[LootGameSolver] Storage trip complete; resuming saved rewards."));
            } else if (result == StorageRecoveryController.Result.RETURNED_UNSTORED) {
                rewardReturnPending = false;
                rewardPreCleanupPending = false;
                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] Returned to rewards, but storage was unavailable or full. Empty bags or storage, then use /lgs resumeLoot."));
            } else {
                rewardReturnPending = false;
                rewardPreCleanupPending = false;
                if (storageFailure != null && mc.thePlayer != null) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText("\u00a7e[LootGameSolver] " + storageFailure));
                if (mc.thePlayer != null) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] Storage recovery stopped. Your unfinished rewards remain saved; check storage and use /lgs resumeLoot when back nearby."));
                if (mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() != null) mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] An item is on your cursor. Place it in an inventory slot before closing the screen."));
            }
            return;
        }
        if (checkpointClearDoneTicks > 0) checkpointClearDoneTicks--;
        if (puzzleWorld != mc.theWorld) {
            if (minedDropStore != null && minedDrops.dirty()) minedDropStore.save(minedDrops);
            minedDropStore = null;
            minedDrops.clear();
            trashCleanup.setMinedDrops(minedDrops);
            rewardLooter.setMinedDrops(minedDrops);
            preLootPacker.setMinedDrops(minedDrops);
            if (exploration != null) exploration.setInventoryMaintenance(false);
            inventoryMaintenanceStage = 0;
            inventoryMaintenanceCooldown = 0;
            storageResumeChecked = false;
            storageResumeWait = 0;
            wasInsideRoom = false;
            trashCleanup.reset();
            if (preLootPacker.active()) preLootPacker.cancel(mc);
            rewardLooter.cancel(mc);
            rewardReturnPending = false;
            rewardPreCleanupPending = false;
            explorationDriven = false;
            inventoryBaseline = null;
            trashCleanup.setBaseline(null);
            rewardLooter.setBaseline(null);
            preLootPacker.setBaseline(null);
            rewardPrePackingAttempted = false;
            rewardCenter = null;
            puzzleWorld = mc.theWorld;
            if (!autoPuzzle.isIdle()) {
                session.cancel("World changed during puzzle automation");
                releaseMovementKeys(mc);
                puzzleCompletion.clear();
                autoPuzzle.reset();
            }
        }
        if (!storageResumeChecked && mc.thePlayer != null
            && mc.theWorld != null
            && mc.currentScreen == null
            && !session.isActive()
            && !rewardReturnPending
            && ++storageResumeWait >= 60) {
            storageResumeChecked = true;
            PendingLootStore.Checkpoint saved = rewardLooter.pendingCheckpoint();
            if (saved != null) {
                StorageRecoveryController recovery = new StorageRecoveryController();
                ensureInventoryBaseline(mc);
                if (recovery.resumeAtStorage(mc, saved, inventoryBaseline)) {
                    storageRecovery = recovery;
                    explorationDriven = true;
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7a[LootGameSolver] Resuming the interrupted storage trip."));
                    return;
                }
            }
        }
        trashCleanup.advanceCooldown();
        if (inventoryMaintenanceCooldown > 0) inventoryMaintenanceCooldown--;
        if (rewardLooterCooldown > 0) rewardLooterCooldown--;
        if (exploration != null && mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.ticksExisted % 20 == 0) {
            boolean inside = exploration.inDungeonRoom(mc);
            wasInsideRoom = inside;
        }
        if (exploration != null) {
            boolean roomWork = autoPuzzle.isActive() || session.isActive()
                || rewardReturnPending
                || storageRecovery != null
                || rewardLooter.active()
                || (exploration.inDungeonRoom(mc)
                    && rewardLooter.hasUncollectedCluster(mc, exploration.verifiedRoomCenter(mc)));
            if (roomWork) exploration.suspendStructureSearchForRoomWork(mc);
            if (rewardReturnPending) exploration.suspendUnrelatedRouteForRewards(mc, rewardLooter.expectedView());
            exploration.tick(mc, session.isActive(), roomWork);
        }
        if (mc.thePlayer != null && mc.theWorld != null) {
            if (minedDropStore == null && mc.thePlayer.ticksExisted >= 40 && hasInventoryContents(mc)) {
                minedDropStore = new MinedDropStore(
                    new java.io.File(mc.mcDataDir, "config/lootgamesolver"),
                    Explorer.worldIdentity(mc),
                    mc.thePlayer.getUniqueID()
                        .toString());
                minedDropStore.load(minedDrops);
                minedDrops.observe(MinedDropTracker.inventory(mc), false, MinedDropTracker::eligible);
            }
            boolean mining = exploration != null && exploration.observingMinedDrops()
                && !session.isActive()
                && !rewardReturnPending
                && mc.currentScreen == null;
            if (minedDropStore != null) {
                minedDrops.observe(MinedDropTracker.inventory(mc), mining, MinedDropTracker::eligible);
                if (minedDrops.dirty() && mc.thePlayer.ticksExisted % 20 == 0) minedDropStore.save(minedDrops);
            }
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (clearCheckpointRequested) {
            clearCheckpointRequested = false;
            rewardLooter.clearCheckpoint();
            if (rewardLooter.active()) rewardLooter.cancel(mc);
            if (preLootPacker.active()) preLootPacker.cancel(mc);
            rewardReturnPending = false;
            rewardPreCleanupPending = false;
            rewardCenter = null;
            if (exploration != null) exploration.clearLastCheckpoint(mc);
            rewardLooterCooldown = 0;
            mc.thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentText("\u00a7a[LootGameSolver] Checkpoint cleared."));
        }
        if (resumeLootRequested) {
            resumeLootRequested = false;
            PendingLootStore.Checkpoint checkpoint = rewardLooter.pendingCheckpoint();
            if (checkpoint == null || !checkpoint.world.equals(Explorer.worldIdentity(mc))
                || checkpoint.dimension != mc.theWorld.provider.dimensionId
                || mc.thePlayer.getDistanceSq(
                    checkpoint.center[0] + 0.5D,
                    checkpoint.center[1] + 0.5D,
                    checkpoint.center[2] + 0.5D) > 256D) {
                mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00a7e[LootGameSolver] No pending loot nearby in this world and dimension."));
            } else if (session.isActive() || rewardReturnPending
                || mc.currentScreen != null
                || (exploration != null && exploration.navigating())) {
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7e[LootGameSolver] Stop other automation and close screens before resuming loot."));
                } else {
                    rewardCenter = checkpoint.center.clone();
                    ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewardCenter, checkpoint.game);
                    explorationDriven = exploration != null;
                    rewardPreCleanupPending = false;
                    rewardReturnPending = true;
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7a[LootGameSolver] Resuming the saved reward collection."));
                }
        }
        if (inventoryMaintenanceStage != 0) {
            if (exploration == null || !exploration.navigating()
                || session.isActive()
                || rewardReturnPending
                || !LootGameSolverConfig.enabled
                || !LootGameSolverConfig.explorationEnabled) {
                if (exploration != null) {
                    exploration.setInventoryMaintenance(false);
                    exploration.setAutomationStatus(null);
                }
                if (rewardLooter.active()) rewardLooter.cancel(mc);
                inventoryMaintenanceStage = 0;
                inventoryMaintenanceCooldown = 200;
                return;
            }
            if (++inventoryMaintenanceWait < 4 || !exploration.inventoryMaintenanceReady(mc)) return;
            if (inventoryMaintenanceStage == 1) {
                if (!trashCleanup.active() && mc.currentScreen != null) return;
                if (trashCleanup.active() || trashCleanup.shouldClean(mc)) {
                    if (trashCleanup.tick(mc, true)) {
                        exploration.setAutomationStatus(trashCleanup.status());
                        return;
                    }
                }
                exploration.setInventoryMaintenance(false);
                exploration.setAutomationStatus(null);
                inventoryMaintenanceStage = 0;
                inventoryMaintenanceCooldown = 100;
            }
            return;
        }
        if (rewardReturnPending) {
            if (rewardCenter != null
                && rewardCollectionTooFar(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, rewardCenter)) {
                boolean saved = rewardLooter.saveCheckpoint(mc) != null;
                if (preLootPacker.active()) preLootPacker.cancel(mc);
                rewardLooter.cancel(mc);
                rewardReturnPending = false;
                rewardPreCleanupPending = false;
                rewardLooterCooldown = 100;
                if (exploration != null && exploration.navigating()) exploration.stop(mc, "Reward collection paused");
                mc.thePlayer.addChatMessage(
                    new net.minecraft.util.ChatComponentText(
                        "\u00A7e[LootGameSolver] Reward collection paused: too far from the room."
                            + (saved ? " Return nearby and use /lgs resumeLoot."
                                : " Could not save the reward checkpoint.")));
                return;
            }
            if (rewardPreCleanupPending) {
                if (exploration != null && !exploration.inventoryCleanupDelayPassed()) return;
                if (!rewardPrePackingAttempted) {
                    trashCleanup.clearCooldown();
                    if (trashCleanup.active() || trashCleanup.shouldClean(mc)) {
                        if (trashCleanup.tick(mc, true)) {
                            if (exploration != null) exploration.setAutomationStatus(trashCleanup.status());
                            return;
                        }
                    }
                    rewardPrePackingAttempted = true;
                    preLootPacker.setBaseline(inventoryBaseline);
                    if (preLootPacker.startInventoryPacking(mc)) {
                        if (exploration != null) exploration.setAutomationStatus("Packing inventory before looting");
                        return;
                    }
                }
                if (preLootPacker.active()) {
                    preLootPacker.tick(mc, null);
                    if (exploration != null) exploration.setAutomationStatus(preLootPacker.status());
                    if (preLootPacker.active()) return;
                }
                rewardPreCleanupPending = false;
                rewardPrePackingAttempted = false;
            }
            rewardLooter.tick(mc, exploration);
            if (exploration != null) exploration.setAutomationStatus(rewardLooter.status());
            if (!rewardLooter.active()) {
                if ("Reward chest unreachable; collection paused".equals(rewardLooter.status())) {
                    rewardReturnPending = false;
                    rewardPreCleanupPending = false;
                    rewardLooterCooldown = 100;
                    if (exploration != null) exploration.setAutomationStatus(rewardLooter.status());
                    return;
                }
                if ("Inventory and backpacks full; rewards remain in chest".equals(rewardLooter.status())
                    && mc.thePlayer != null
                    && mc.thePlayer.inventory.getFirstEmptyStack() < 0
                    && mc.currentScreen == null
                    && (exploration == null || !exploration.navigating())) {
                    if (startStorageRecovery(mc, rewardLooter.pendingCheckpoint())) return;
                    rewardReturnPending = false;
                    rewardPreCleanupPending = false;
                    rewardLooterCooldown = 100;
                    if (exploration != null) exploration.stop(mc, "Reward collection paused");
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7e[LootGameSolver] Reward collection paused: storage is unavailable. "
                                + (rewardLooter.pendingCheckpoint() != null ? "Use /lgs resumeLoot after making space."
                                    : "Could not save the reward checkpoint.")));
                    return;
                }
                if (rewardLooter.completed()) {
                    if (trashCleanup.active() || trashCleanup.shouldClean(mc)) {
                        if (trashCleanup.tick(mc, true)) {
                            if (exploration != null) exploration.setAutomationStatus(trashCleanup.status());
                            return;
                        }
                    }
                    rewardReturnPending = false;
                    rewardPreCleanupPending = false;
                    if (explorationDriven && exploration != null) {
                        if (rewardCenter != null) exploration.markStructureSolved(mc, rewardCenter);
                        exploration.finishPuzzle(mc, true);
                    }
                } else if (!"Inventory and backpacks full; rewards remain in chest".equals(rewardLooter.status())
                    && !"No available backpack; items kept".equals(rewardLooter.status())) {
                        boolean saved = rewardLooter.saveCheckpoint(mc) != null;
                        rewardReturnPending = false;
                        rewardPreCleanupPending = false;
                        rewardLooterCooldown = 100;
                        mc.thePlayer.addChatMessage(
                            new net.minecraft.util.ChatComponentText(
                                "\u00A7e[LootGameSolver] " + rewardLooter.status()
                                    + (saved ? " Rewards saved; use /lgs resumeLoot nearby." : "")));
                    }
            }
            return;
        }
        if (!session.isActive() && inventoryMaintenanceCooldown == 0
            && exploration != null
            && exploration.navigating()
            && (trashCleanup.inventoryPressure(mc) || trashCleanup.shouldClean(mc))
            && exploration.inventoryCleanupSafe(mc)
            && mc.currentScreen == null
            && mc.thePlayer.inventory.getItemStack() == null) {
            ensureInventoryBaseline(mc);
            inventoryMaintenanceStage = 1;
            inventoryMaintenanceWait = 0;
            exploration.setInventoryMaintenance(true);
            exploration.setAutomationStatus("Preparing inventory cleanup");
            return;
        }
        if (mc.currentScreen != null) {
            tileNavigator.pause(true);
            if (session.inventoryTransferActive()) session.tick();
            else session.pauseForScreen();
            return;
        }
        if (exploration != null
            && (exploration.navigating()
                || (explorationDriven && autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED))
            && (autoPuzzle.isIdle() || autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED)
            && !session.isActive()
            && rewardLooterCooldown == 0
            && mc.thePlayer.ticksExisted % 20 == 0
            && (exploration.inDungeonRoom(mc)
                || (explorationDriven && autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED
                    && autoPuzzle.getRewardCenter() != null))) {
            int[] anchor = exploration.verifiedRoomCenter(mc);
            if (anchor == null && autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED)
                anchor = autoPuzzle.getRewardCenter();
            int[] cluster = nearbyRewardCenter(mc, anchor);
            if (cluster != null && rewardLooter.hasUncollectedCluster(mc, cluster)) {
                autoPuzzle.reset();
                exploration.prepareRewardCollection(mc, cluster);
                rewardCenter = cluster;
                explorationDriven = true;
                ensureInventoryBaseline(mc);
                rewardLooter.start(mc, cluster, com.kyroxova.lootgamesolver.core.MiniGame.UNKNOWN);
                rewardPreCleanupPending = true;
                rewardPrePackingAttempted = false;
                rewardReturnPending = true;
                exploration.setAutomationStatus("Collecting reward chests");
                return;
            }
            if (!exploration.navigating()) return;
            int[] center = puzzleBridge.nearbyFloorActivator();
            if (center == null) {
                center = puzzleBridge.nearbyMasterBlock(exploration.verifiedRoomCenter(mc));
            }
            if (center == null) {
                Object game = puzzleBridge.nearbyGame(true);
                if (game != null) center = puzzleBridge.gameCenter(game);
            }
            if (center != null) exploration.interruptSearchForRoomPuzzle(mc, center);
        }
        if (exploration != null && exploration.consumePuzzleArrival() && autoPuzzle.isIdle()) {
            explorationDriven = true;
            autoPuzzle.startFromExploration(exploration.puzzlePosition(), mc.theWorld, puzzleCompletion);
        }

        autoPuzzle.checkSolvingStage(mc, puzzleBridge, exploration, session, puzzleCompletion, tileNavigator);

        // Poll dormant board detection less often; poll every tick while solving.
        detectCounter++;
        if (session.isActive() || detectCounter % 20 == 0) {
            session.tick(!tileNavigator.moving());
        }

        autoPuzzle.tick(mc, puzzleBridge, exploration, session, puzzleCompletion, rewardLooter, this);

        if (exploration != null) {
            boolean watchingRoom = !session.isActive() && !rewardReturnPending
                && (exploration.navigating() || (explorationDriven && !autoPuzzle.isIdle()));
            int[] room = watchingRoom ? exploration.verifiedRoomCenter(mc) : null;
            RoomOpenWatchdog.Action recovery = roomOpenWatchdog.tick(room, watchingRoom);
            if (room != null && (recovery == RoomOpenWatchdog.Action.RETRY || autoPuzzle.isIdle()))
                exploration.setAutomationStatus(roomOpenWatchdog.status());
            if (recovery != RoomOpenWatchdog.Action.WAIT) {
                int[] rewards = nearbyRewardCenter(mc, room);
                if (rewards != null) {
                    roomOpenWatchdog.reset();
                    autoPuzzle.reset();
                    exploration.prepareRewardCollection(mc, rewards);
                    rewardCenter = rewards;
                    explorationDriven = true;
                    ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewards, com.kyroxova.lootgamesolver.core.MiniGame.UNKNOWN);
                    rewardPreCleanupPending = true;
                    rewardPrePackingAttempted = false;
                    rewardReturnPending = true;
                } else if (recovery == RoomOpenWatchdog.Action.SKIP) {
                    roomOpenWatchdog.reset();
                    autoPuzzle.reset();
                    session.cancel("Skipping unopenable room");
                    releaseMovementKeys(mc);
                    exploration.skipUnopenableRoom(mc, room);
                    mc.thePlayer.addChatMessage(
                        new net.minecraft.util.ChatComponentText(
                            "\u00a7e[LootGameSolver] Could not open this room after three retries; moving on."));
                } else {
                    int[] master = puzzleBridge.nearbyMasterBlock(room);
                    int[] floor = puzzleBridge.nearbyFloorActivator();
                    Object game = puzzleBridge.nearbyGame(true);
                    int[] center = master != null ? master
                        : (floor != null ? floor : (game == null ? room : puzzleBridge.gameCenter(game)));
                    exploration.stop(mc, "Retrying room puzzle");
                    explorationDriven = true;
                    autoPuzzle.startManual(center != null ? center : room, floor, mc.theWorld, puzzleCompletion);
                }
            }
        }

        if (session.isActive()) {
            if (exploration != null && exploration.navigating()) {
                exploration.stop(mc, "Solving puzzle in progress");
            }
            tileNavigator.pause(false);
            handleAutoMovement(mc);
        } else tileNavigator.stop();

        if (session.isActive() || autoPuzzle.isActive()
            || (exploration != null && (exploration.navigating() || rewardReturnPending))) {
            totalActiveTicks++;
        }
    }

    public void startPuzzleSolver() {
        autoPuzzle.startPuzzleSolver(Minecraft.getMinecraft(), puzzleBridge, session, this);
    }

    public boolean isExplorationDriven() {
        return explorationDriven;
    }

    public void setRewardPreCleanupPending(boolean pending) {
        this.rewardPreCleanupPending = pending;
        this.rewardPrePackingAttempted = false;
    }

    public void setRewardReturnPending(boolean pending) {
        this.rewardReturnPending = pending;
    }

    public void setRewardCenter(int[] center) {
        rewardCenter = center == null ? null : center.clone();
    }

    private boolean startStorageRecovery(Minecraft mc, PendingLootStore.Checkpoint checkpoint) {
        if (checkpoint == null) return false;
        int[] expectedCenter = rewardLooter.rewardCenter();
        if (expectedCenter == null) expectedCenter = rewardCenter;
        if (expectedCenter != null && !java.util.Arrays.equals(expectedCenter, checkpoint.center)) return false;
        StorageRecoveryController recovery = new StorageRecoveryController();
        if (!recovery.start(mc, checkpoint, inventoryBaseline)) return false;
        storageRecovery = recovery;
        if (exploration != null) exploration.setAutomationStatus("Preparing storage trip");
        return true;
    }

    public InventoryBaseline getInventoryBaseline() {
        return inventoryBaseline;
    }

    private static boolean hasInventoryContents(Minecraft mc) {
        for (net.minecraft.item.ItemStack stack : MinedDropTracker.inventory(mc)) if (stack != null) return true;
        return false;
    }

    public void setAutomationPaused(Minecraft mc, boolean paused) {
        automationPaused = paused;
        tileNavigator.pause(paused);
        if (exploration != null) exploration.pauseAutomation(paused);
        if (storageRecovery != null) storageRecovery.pause(paused);
        if (paused && mc.thePlayer != null) {
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            mc.thePlayer.setSprinting(false);
        }
    }

    public void captureInventoryBaseline(Minecraft mc) {
        inventoryBaseline = InventoryBaseline.capture(mc);
        trashCleanup.setBaseline(inventoryBaseline);
        rewardLooter.setBaseline(inventoryBaseline);
        preLootPacker.setBaseline(inventoryBaseline);
    }

    public void ensureInventoryBaseline(Minecraft mc) {
        if (inventoryBaseline == null) captureInventoryBaseline(mc);
    }

    public static int[] nearbyRewardCenter(Minecraft mc) {
        return nearbyRewardCenter(mc, null);
    }

    public static boolean rewardClusterMatches(int[] anchor, int x, int y, int z) {
        return anchor == null
            || (Math.abs(anchor[0] - x) <= 5 && Math.abs(anchor[2] - z) <= 5 && Math.abs(anchor[1] - y) <= 5);
    }

    public static boolean rewardCollectionTooFar(double x, double y, double z, int[] center) {
        double dx = x - (center[0] + 0.5D), dz = z - (center[2] + 0.5D);
        return dx * dx + dz * dz > 32D * 32D || Math.abs(y - center[1]) > 24D;
    }

    public static int[] nearbyRewardCenter(Minecraft mc, int[] anchor) {
        java.util.List<net.minecraft.tileentity.TileEntityChest> nearby = new java.util.ArrayList<>();
        for (Object value : mc.theWorld.loadedTileEntityList) {
            if (!(value instanceof net.minecraft.tileentity.TileEntityChest)) continue;
            net.minecraft.tileentity.TileEntityChest chest = (net.minecraft.tileentity.TileEntityChest) value;
            if (mc.thePlayer.getDistanceSq(chest.xCoord + 0.5D, chest.yCoord + 0.5D, chest.zCoord + 0.5D) <= 256D)
                nearby.add(chest);
        }
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.tileentity.TileEntityChest chest : nearby) {
            int x = chest.xCoord, y = chest.yCoord, z = chest.zCoord;
            int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] offset : offsets) {
                int cx = x + offset[0], cz = z + offset[1], count = 0;
                for (net.minecraft.tileentity.TileEntityChest other : nearby)
                    if (other.yCoord == y && Math.abs(other.xCoord - cx) + Math.abs(other.zCoord - cz) == 1) count++;
                if (count >= 3 && rewardClusterMatches(anchor, cx, y, cz)) {
                    double distance = anchor == null ? mc.thePlayer.getDistanceSq(cx + 0.5D, y, cz + 0.5D)
                        : Math.pow(anchor[0] - cx, 2) + Math.pow(anchor[1] - y, 2) + Math.pow(anchor[2] - cz, 2);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = new int[] { cx, y, cz };
                    }
                }
            }
        }
        return best;
    }

    private void handleAutoMovement(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null || mc.currentScreen != null) return;
        SolverAction targetAction = session.getActiveAction();
        if (targetAction == null) {
            releaseMovementKeys(mc);
            return;
        }
        int[] block = session.getBlockPos(targetAction.position);
        if (block == null) {
            releaseMovementKeys(mc);
            return;
        }

        if (!tileNavigator.tick(mc, block)) {
            tileNavigator.stop();
            session.cancel("No safe route to a visible puzzle tile");
            if (exploration != null) exploration.setAutomationStatus("Puzzle tile unreachable; explorer held");
        }
    }

    public void releaseMovementKeys(Minecraft mc) {
        tileNavigator.stop();
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
            mc.thePlayer.motionX = 0;
            mc.thePlayer.motionZ = 0;
            if (mc.thePlayer.movementInput != null) {
                mc.thePlayer.movementInput.moveForward = 0.0F;
                mc.thePlayer.movementInput.jump = false;
            }
        }
        if (mc.gameSettings != null && mc.gameSettings.keyBindSprint != null) {
            net.minecraft.client.settings.KeyBinding.setKeyBindState(
                mc.gameSettings.keyBindSprint.getKeyCode(),
                net.minecraft.client.settings.GameSettings.isKeyDown(mc.gameSettings.keyBindSprint));
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Text event) {
        ClientHudOverlay.render(this, event);
    }

    private static String compactStatus(String text) {
        return StatusText.solver(text);
    }

    public static String explorerKeyLabel(String label, boolean active, int heldTicks) {
        return explorerKeyLabel(label, active, heldTicks, false);
    }

    public static String explorerKeyLabel(String label, boolean active, int heldTicks, boolean paused) {
        String base = paused ? "\u00A7f" : active ? "\u00A7a" : "\u00A77";
        if (heldTicks < 5) return base + label;
        int filled = Math.min(label.length(), Math.max(0, (heldTicks - 4) * label.length() / 16));
        return "\u00A7e" + label.substring(0, filled) + base + label.substring(filled);
    }
}
