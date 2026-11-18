package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;

public final class ClientHooks {

    private static boolean resumeLootRequested;

    static void requestResumeLoot() {
        resumeLootRequested = true;
    }

    private static final ResourceLocation TEXTURE_UI_9SLICE = new ResourceLocation(
        "lootgamesolver",
        "textures/gui/ui_9slice.png");
    private final SolverSession session = new SolverSession();
    private final Explorer exploration = Explorer.ENABLED ? new Explorer() : null;
    private final WorldOverlayRenderer worldRenderer = new WorldOverlayRenderer(session, exploration);
    private boolean lastAutoSolveKey = false;
    private boolean lastOverlayKey = false;
    private int detectCounter = 0;
    private final LootGamesBridge puzzleBridge = new LootGamesBridge();
    private final PuzzleCompletionGate puzzleCompletion = new PuzzleCompletionGate();
    private final PuzzleTileNavigator tileNavigator = new PuzzleTileNavigator();
    private final RoomOpenWatchdog roomOpenWatchdog = new RoomOpenWatchdog();
    private final RewardChestLooter rewardLooter = new RewardChestLooter();
    private final RewardChestLooter preLootPacker = new RewardChestLooter();
    private final TrashCleanup trashCleanup = new TrashCleanup();
    private StorageRecoveryController storageRecovery;
    private boolean storageResumeChecked;
    private int storageResumeWait;
    private boolean rewardReturnPending;
    private boolean rewardPreCleanupPending;
    private boolean rewardPrePackingAttempted;
    private int inventoryMaintenanceStage;
    private int inventoryMaintenanceWait;
    private int inventoryMaintenanceCooldown;
    private boolean explorationDriven;
    private InventoryBaseline inventoryBaseline;
    private int[] rewardCenter;
    private boolean exploreKeyHeld;
    private boolean exploreHoldActionDone;
    private int exploreHoldTicks;
    private int checkpointClearDoneTicks;
    private boolean wasInsideRoom;
    private World puzzleWorld;
    private long totalActiveTicks = 0L;

    private final AutoPuzzleCoordinator autoPuzzle = new AutoPuzzleCoordinator();

    public static void drawNineSlice(ResourceLocation texture, int x, int y, int width, int height, Minecraft mc) {
        if (texture == null || mc == null || mc.getTextureManager() == null) return;
        mc.getTextureManager()
            .bindTexture(texture);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        float t = 32.0F; // 32x32 texture sheet

        int fillStartX = x + 12;
        int fillEndX = x + width - 12;
        int fillStartY = y + 12;
        int fillEndY = y + height - 12;

        for (int curY = fillStartY; curY < fillEndY; curY += 6) {
            int drawH = Math.min(6, fillEndY - curY);
            for (int curX = fillStartX; curX < fillEndX; curX += 6) {
                int drawW = Math.min(6, fillEndX - curX);
                drawSubTexture(curX, curY, 13, 13, drawW, drawH, drawW, drawH, t, t);
            }
        }

        int topStartX = x + 12;
        int topEndX = x + width - 12;
        for (int curX = topStartX; curX < topEndX; curX += 6) {
            int drawW = Math.min(6, topEndX - curX);
            drawSubTexture(curX, y, 13, 0, drawW, 12, drawW, 12, t, t);
        }

        int botStartX = x + 12;
        int botEndX = x + width - 12;
        for (int curX = botStartX; curX < botEndX; curX += 6) {
            int drawW = Math.min(6, botEndX - curX);
            drawSubTexture(curX, y + height - 12, 13, 20, drawW, 12, drawW, 12, t, t);
        }

        int leftStartY = y + 12;
        int leftEndY = y + height - 12;
        for (int curY = leftStartY; curY < leftEndY; curY += 6) {
            int drawH = Math.min(6, leftEndY - curY);
            drawSubTexture(x, curY, 0, 13, 12, drawH, 12, drawH, t, t);
        }

        int rightStartY = y + 12;
        int rightEndY = y + height - 12;
        for (int curY = rightStartY; curY < rightEndY; curY += 6) {
            int drawH = Math.min(6, rightEndY - curY);
            drawSubTexture(x + width - 12, curY, 20, 13, 12, drawH, 12, drawH, t, t);
        }

        drawSubTexture(x, y, 0, 0, 12, 12, 12, 12, t, t);

        drawSubTexture(x + width - 12, y, 20, 0, 12, 12, 12, 12, t, t);

        drawSubTexture(x, y + height - 12, 0, 20, 12, 12, 12, 12, t, t);

        drawSubTexture(x + width - 12, y + height - 12, 20, 20, 12, 12, 12, 12, t, t);
    }

    private static void drawSubTexture(int x, int y, float u, float v, float uWidth, float vHeight, int width,
        int height, float texWidth, float texHeight) {
        float f = 1.0F / texWidth;
        float f1 = 1.0F / texHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator
            .addVertexWithUV((double) x, (double) (y + height), 0.0D, (double) (u * f), (double) ((v + vHeight) * f1));
        tessellator.addVertexWithUV(
            (double) (x + width),
            (double) (y + height),
            0.0D,
            (double) ((u + uWidth) * f),
            (double) ((v + vHeight) * f1));
        tessellator
            .addVertexWithUV((double) (x + width), (double) y, 0.0D, (double) ((u + uWidth) * f), (double) (v * f1));
        tessellator.addVertexWithUV((double) x, (double) y, 0.0D, (double) (u * f), (double) (v * f1));
        tessellator.draw();
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

        Minecraft mc = Minecraft.getMinecraft();
        if (storageRecovery != null) {
            if (mc.currentScreen == null && Keybinds.explore != null && Keybinds.explore.isPressed())
                storageRecovery.cancel();
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
        checkKeyToggles();
        if (checkpointClearDoneTicks > 0) checkpointClearDoneTicks--;
        if (puzzleWorld != mc.theWorld) {
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
        if (exploration != null && mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.ticksExisted % 20 == 0) {
            boolean inside = exploration.inDungeonRoom(mc);
            wasInsideRoom = inside;
        }
        if (exploration != null) {
            if (autoPuzzle.isActive() || session.isActive() || rewardReturnPending || storageRecovery != null)
                exploration.suspendStructureSearchForRoomWork(mc);
            exploration.tick(mc, session.isActive());
        }
        if (mc.thePlayer == null || mc.theWorld == null) return;
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
                    if ("No available backpack; items kept".equals(preLootPacker.status())
                        && startStorageRecovery(mc, rewardLooter.saveCheckpoint(mc))) return;
                }
                rewardPreCleanupPending = false;
                rewardPrePackingAttempted = false;
            }
            rewardLooter.tick(mc, exploration);
            if (exploration != null) exploration.setAutomationStatus(rewardLooter.status());
            if (!rewardLooter.active()) {
                if (("Inventory and backpacks full; rewards remain in chest".equals(rewardLooter.status())
                    || "No available backpack; items kept".equals(rewardLooter.status())) && mc.currentScreen == null
                    && (exploration == null || !exploration.navigating())) {
                    if (startStorageRecovery(mc, rewardLooter.pendingCheckpoint())) return;
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
        if (exploration != null && exploration.navigating()
            && autoPuzzle.isIdle()
            && !session.isActive()
            && mc.thePlayer.ticksExisted % 20 == 0
            && exploration.inDungeonRoom(mc)) {
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
                int[] rewards = nearbyRewardCenter(mc);
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

    private void startPuzzleSolver() {
        autoPuzzle.startPuzzleSolver(Minecraft.getMinecraft(), puzzleBridge, session, this);
    }

    boolean isExplorationDriven() {
        return explorationDriven;
    }

    void setRewardPreCleanupPending(boolean pending) {
        this.rewardPreCleanupPending = pending;
        this.rewardPrePackingAttempted = false;
    }

    void setRewardReturnPending(boolean pending) {
        this.rewardReturnPending = pending;
    }

    void setRewardCenter(int[] center) {
        rewardCenter = center == null ? null : center.clone();
    }

    private boolean startStorageRecovery(Minecraft mc, PendingLootStore.Checkpoint checkpoint) {
        if (checkpoint == null || !java.util.Arrays.equals(rewardLooter.rewardCenter(), checkpoint.center))
            return false;
        StorageRecoveryController recovery = new StorageRecoveryController();
        if (!recovery.start(mc, checkpoint, inventoryBaseline)) return false;
        storageRecovery = recovery;
        if (exploration != null) exploration.setAutomationStatus("Preparing storage trip");
        return true;
    }

    InventoryBaseline getInventoryBaseline() {
        return inventoryBaseline;
    }

    void captureInventoryBaseline(Minecraft mc) {
        inventoryBaseline = InventoryBaseline.capture(mc);
        trashCleanup.setBaseline(inventoryBaseline);
        rewardLooter.setBaseline(inventoryBaseline);
        preLootPacker.setBaseline(inventoryBaseline);
    }

    void ensureInventoryBaseline(Minecraft mc) {
        if (inventoryBaseline == null) captureInventoryBaseline(mc);
    }

    private void checkKeyToggles() {
        Minecraft mc = Minecraft.getMinecraft();
        boolean explorePressed = Keybinds.explore.isPressed();
        int exploreCode = Keybinds.explore.getKeyCode();
        boolean exploreDown = exploreCode > 0 && exploreCode < 256 && Keyboard.isKeyDown(exploreCode);
        boolean runExploreAction = false;
        if (mc.currentScreen != null) {
            exploreKeyHeld = false;
            exploreHoldActionDone = false;
            exploreHoldTicks = 0;
        } else if (exploreDown) {
            if (!exploreKeyHeld) exploreHoldTicks = 0;
            exploreKeyHeld = true;
            exploreHoldTicks++;
            if (exploreHoldTicks >= 20 && !exploreHoldActionDone) {
                exploreHoldActionDone = true;
                checkpointClearDoneTicks = 12;
                if (rewardReturnPending) {
                    if (preLootPacker.active()) preLootPacker.cancel(mc);
                    rewardLooter.cancel(mc);
                    rewardReturnPending = false;
                }
                if (exploration != null && mc.thePlayer != null && mc.theWorld != null)
                    exploration.clearLastCheckpoint(mc);
            }
        } else {
            runExploreAction = exploreKeyHeld ? !exploreHoldActionDone : explorePressed;
            exploreKeyHeld = false;
            exploreHoldActionDone = false;
            exploreHoldTicks = 0;
        }
        if (exploration != null && runExploreAction
            && mc.currentScreen == null
            && mc.thePlayer != null
            && mc.theWorld != null) {
            if (!exploration.navigating() && !rewardReturnPending) {
                inventoryBaseline = InventoryBaseline.capture(mc);
                trashCleanup.setBaseline(inventoryBaseline);
                rewardLooter.setBaseline(inventoryBaseline);
            }
            if (rewardReturnPending) {
                if (preLootPacker.active()) preLootPacker.cancel(mc);
                rewardLooter.cancel(mc);
                rewardReturnPending = false;
                if (exploration.navigating()) exploration.toggle(mc);
                exploration.setAutomationStatus("Reward collection stopped");
                return;
            }
            session.cancel("Exploration selected");
            releaseMovementKeys(mc);
            puzzleCompletion.clear();
            autoPuzzle.reset();
            if (!exploration.navigating()
                && (exploration.inDungeonRoom(mc) || puzzleBridge.nearbyFloorActivator() != null
                    || puzzleBridge.nearbyGame(true) != null)) {
                explorationDriven = true;
                int[] floor = puzzleBridge.nearbyFloorActivator();
                Object nearbyGame = puzzleBridge.nearbyGame(true);
                DetectedGame board = puzzleBridge.detect(true);
                int[] boardCenter = floor != null ? floor
                    : nearbyGame == null ? null : puzzleBridge.gameCenter(nearbyGame);
                if (board != null) {
                    if (boardCenter != null) autoPuzzle.setRewardCenter(boardCenter);
                    startPuzzleSolver();
                    exploration.setAutomationStatus("Solving active puzzle in room");
                    return;
                }
                if (boardCenter != null) {
                    autoPuzzle.startManual(boardCenter, floor, mc.theWorld, puzzleCompletion);
                    exploration.setAutomationStatus("Opening puzzle in current room");
                    return;
                }
                int[] rewards = nearbyRewardCenter(mc);
                if (rewards != null) {
                    exploration.prepareRewardCollection(mc, rewards);
                    rewardCenter = rewards;
                    ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewards, com.kyroxova.lootgamesolver.core.MiniGame.UNKNOWN);
                    rewardPreCleanupPending = true;
                    rewardPrePackingAttempted = false;
                    rewardReturnPending = true;
                    exploration.setAutomationStatus("Collecting reward chests");
                    return;
                }
                exploration.toggle(mc);
                return;
            }
            if (!exploration.navigating() && exploration.hasPendingReturn()) {
                exploration.toggle(mc);
                return;
            }
            int[] center = puzzleBridge.nearbyFloorActivator();
            if (center == null) {
                Object nearby = puzzleBridge.nearbyGame(true);
                if (nearby != null) center = puzzleBridge.gameCenter(nearby);
            }
            if (!exploration.resumePuzzleInCurrentRoom(mc, center)) exploration.toggle(mc);
        }
        boolean autoSolvePressed = isKeyJustPressed(Keybinds.toggleAutoSolve, LootGameSolverConfig.solveKey);
        boolean overlayPressed = isKeyJustPressed(Keybinds.toggleOverlay, LootGameSolverConfig.overlayKey);

        if (autoSolvePressed) {
            explorationDriven = false;
            session.invalidateDormantCache();
            if (session.isActive()) {
                session.cancel("Auto-Solver Stopped");
                releaseMovementKeys(mc);
                if (autoPuzzle.isSolving()) autoPuzzle.setState(AutoPuzzleCoordinator.State.FAILED);
            } else {
                captureInventoryBaseline(mc);
                session.start(ActionPlanner.Mode.SOLVE);
                Object game = puzzleBridge.nearbyGame(true);
                int level = puzzleBridge.currentMinesweeperLevel(game);
                if (session.isActive()) {
                    int[] currentCenter = game == null ? puzzleBridge.nearbyFloorActivator()
                        : puzzleBridge.gameCenter(game);
                    if (currentCenter != null) autoPuzzle.setRewardCenter(currentCenter);
                    puzzleCompletion.begin();
                    autoPuzzle.setState(AutoPuzzleCoordinator.State.SOLVING);
                }
                if (level >= 1 && level <= 4) {
                    int[] currentCenter = puzzleBridge.gameCenter(game);
                    if (currentCenter != null) autoPuzzle.setRewardCenter(currentCenter);
                    puzzleCompletion.begin();
                    if (session.isActive()) {
                        autoPuzzle.setState(AutoPuzzleCoordinator.State.SOLVING);
                    } else if (puzzleBridge.canOpenMinesweeperBoard(game)) {
                        autoPuzzle.setState(AutoPuzzleCoordinator.State.REVEALING_NEXT_STAGE);
                    }
                }
            }
        }

        if (overlayPressed) {
            session.invalidateDormantCache();
            LootGameSolverConfig.setOverlayVisible(!LootGameSolverConfig.showOverlay);
        }
    }

    static int[] nearbyRewardCenter(Minecraft mc) {
        java.util.List<net.minecraft.tileentity.TileEntityChest> nearby = new java.util.ArrayList<>();
        for (Object value : mc.theWorld.loadedTileEntityList) {
            if (!(value instanceof net.minecraft.tileentity.TileEntityChest)) continue;
            net.minecraft.tileentity.TileEntityChest chest = (net.minecraft.tileentity.TileEntityChest) value;
            if (mc.thePlayer.getDistanceSq(chest.xCoord + 0.5D, chest.yCoord + 0.5D, chest.zCoord + 0.5D) <= 256D)
                nearby.add(chest);
        }
        for (net.minecraft.tileentity.TileEntityChest chest : nearby) {
            int x = chest.xCoord, y = chest.yCoord, z = chest.zCoord;
            int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
            for (int[] offset : offsets) {
                int cx = x + offset[0], cz = z + offset[1], count = 0;
                for (net.minecraft.tileentity.TileEntityChest other : nearby)
                    if (other.yCoord == y && Math.abs(other.xCoord - cx) + Math.abs(other.zCoord - cz) == 1) count++;
                if (count >= 3) return new int[] { cx, y, cz };
            }
        }
        return null;
    }

    private boolean isKeyJustPressed(KeyBinding binding, int fallbackCode) {
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

    private void handleAutoMovement(Minecraft mc) {
        if (mc.thePlayer == null || mc.thePlayer.movementInput == null) return;
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

    void releaseMovementKeys(Minecraft mc) {
        tileNavigator.stop();
        if (mc.thePlayer != null) {
            mc.thePlayer.setSprinting(false);
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
        if (!LootGameSolverConfig.enabled) return;
        if (!LootGameSolverConfig.showOverlay && !session.isActive()
            && !rewardReturnPending
            && (exploration == null || !exploration.navigating())
            && !wasInsideRoom
            && !autoPuzzle.isActive()) return;
        DetectedGame cur = session.getCurrentDetectedGame();
        if (cur == null && (exploration == null || !LootGameSolverConfig.explorationEnabled)) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

            String autoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleAutoSolve.getKeyCode());
            String holoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleOverlay.getKeyCode());

            String gameTitle = cur == null ? "Explorer"
                : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER ? "Minesweeper"
                    : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU ? "Sudoku"
                        : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT ? "G O L" : "LootGames";

            String statusStr;
            if (session.isActive()) {
                statusStr = compactStatus(session.getStatus());
            } else if (exploration != null) {
                statusStr = (exploration.navigating() || rewardReturnPending
                    || autoPuzzle.isActive()
                    || autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED) ? exploration.navigationStatus()
                        : "Ready";
            } else {
                statusStr = "Ready";
            }

            boolean autoActive = session.isActive();
            boolean holoActive = LootGameSolverConfig.showOverlay;
            boolean explorerActive = exploration != null && (exploration.navigating() || rewardReturnPending);

            String autoColor = autoActive ? "\u00A7c" : "\u00A77";
            String holoColor = holoActive ? "\u00A7e" : "\u00A77";

            long totalSeconds = totalActiveTicks / 20L;
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            String timerStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            String line1 = "\u00A7b\u00A7lLoot Game Solver \u00A77[ \u00A7f" + timerStr + " \u00A77]";
            int dist = exploration != null ? exploration.checkpointDistance(mc) : 0;
            String distBadge = (explorerActive && dist > 0) ? " \u00A77[ \u00A7f" + dist + "m \u00A77]" : "";
            String line2 = (explorerActive ? "\u00A7a\u00A7l" : "\u00A7f") + gameTitle
                + " \u00A78|\u00A7"
                + (session.isActive() || explorerActive ? "a" : "e")
                + " "
                + statusStr
                + distBadge;
            String line3 = autoColor + "["
                + autoKeyStr
                + "] Auto \u00A78|\u00A77 "
                + holoColor
                + "["
                + holoKeyStr
                + "] Holograms";
            if (exploration != null && LootGameSolverConfig.explorationEnabled) {
                line3 += " \u00A78|" + (explorerActive ? "\u00A7a" : "\u00A77")
                    + " ["
                    + GameSettings.getKeyDisplayString(Keybinds.explore.getKeyCode())
                    + "] "
                    + exploration.explorationAction();
            }
            int width1 = mc.fontRenderer.getStringWidth(line1);
            int width2 = mc.fontRenderer.getStringWidth(line2);
            int width3 = mc.fontRenderer.getStringWidth(line3);
            int maxWidth = Math.max(width1, Math.max(width2, width3));

            int panelWidth = Math.max(170, maxWidth + 40);
            boolean showCheckpointProgress = exploreKeyHeld || checkpointClearDoneTicks > 0;
            int panelHeight = 44 + (showCheckpointProgress ? 10 : 0);
            int x = resolution.getScaledWidth() - panelWidth - 15;
            int y = resolution.getScaledHeight() - panelHeight - 55;

            drawNineSlice(TEXTURE_UI_9SLICE, x, y, panelWidth, panelHeight, mc);

            mc.fontRenderer.drawStringWithShadow(line1, x + (panelWidth - width1) / 2, y + 7, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line2, x + (panelWidth - width2) / 2, y + 17, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line3, x + (panelWidth - width3) / 2, y + 27, 0xFFFFFF);
            if (showCheckpointProgress) {
                float progress = checkpointClearDoneTicks > 0 ? 1F : Math.min(1F, exploreHoldTicks / 20F);
                int barX = x + 14, barY = y + panelHeight - 8, barWidth = panelWidth - 28;
                drawCheckpointProgressBar(barX, barY, barWidth, 3, progress);
            }
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static String compactStatus(String text) {
        return StatusText.solver(text);
    }

    private static void drawCheckpointProgressBar(int x, int y, int width, int height, float progress) {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0.12F, 0.12F, 0.12F, 0.9F);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        int fill = Math.max(1, Math.round(width * Math.max(0F, Math.min(1F, progress))));
        float leftGreen = Math.min(1F, progress * 0.75F);
        float rightGreen = Math.min(1F, progress + 0.25F);
        GL11.glBegin(GL11.GL_QUADS);
        setProgressColor(1F - leftGreen, leftGreen, 0.05F, 1F);
        GL11.glVertex2f(x, y);
        setProgressColor(1F - rightGreen, rightGreen, 0.05F, 1F);
        GL11.glVertex2f(x + fill, y);
        setProgressColor(1F - rightGreen, rightGreen, 0.05F, 1F);
        GL11.glVertex2f(x + fill, y + height);
        setProgressColor(1F - leftGreen, leftGreen, 0.05F, 1F);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    private static void setProgressColor(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }
}
