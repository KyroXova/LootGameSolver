package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.minecraft.LootGamesBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;

public final class ClientHooks {

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
    private final RewardChestLooter rewardLooter = new RewardChestLooter();
    private final TrashCleanup trashCleanup = new TrashCleanup();
    private boolean rewardReturnPending;
    private boolean rewardPreCleanupPending;
    private InventoryBaseline inventoryBaseline;
    private int[] rewardCenter;
    private boolean exploreKeyHeld;
    private boolean exploreHoldActionDone;
    private int exploreHoldTicks;
    private int checkpointClearDoneTicks;
    private boolean wasInsideRoom;

    private enum AutoPuzzleState {
        IDLE,
        OPENING,
        POSITIONING,
        POSITIONING_NEXT_STAGE,
        REVEALING,
        REVEALING_NEXT_STAGE,
        SOLVING,
        FAILED
    }

    private AutoPuzzleState autoPuzzleState = AutoPuzzleState.IDLE;
    private int[] autoPuzzlePosition;
    private int[] floorActivatorPosition;
    private boolean floorActivatorClicked;
    private int puzzleWaitTicks;
    private int masterClickAttempts;
    private int centerClickAttempts;
    private boolean minesweeperOpeningClickSent;
    private int activeMinesweeperLevel = -1;
    private int lastMasterClickTick = -40;
    private int lastCenterClickTick = -40;
    private net.minecraft.world.World puzzleWorld;

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
        if (event == null || event.message == null || autoPuzzleState != AutoPuzzleState.SOLVING) return;
        capturePuzzleMessage(event.message);
    }

    private void capturePuzzleMessage(IChatComponent message) {
        if (message instanceof ChatComponentTranslation)
            puzzleCompletion.message(((ChatComponentTranslation) message).getKey());
        else if (net.minecraft.util.StatCollector.translateToLocal("msg.lootgames.win")
            .equals(message.getUnformattedText())) puzzleCompletion.message("msg.lootgames.win");
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
        checkKeyToggles();
        if (checkpointClearDoneTicks > 0) checkpointClearDoneTicks--;
        if (puzzleWorld != mc.theWorld) {
            wasInsideRoom = false;
            trashCleanup.reset();
            rewardLooter.cancel(mc);
            rewardReturnPending = false;
            rewardPreCleanupPending = false;
            inventoryBaseline = null;
            trashCleanup.setBaseline(null);
            rewardLooter.setBaseline(null);
            rewardCenter = null;
            puzzleWorld = mc.theWorld;
            if (autoPuzzleState != AutoPuzzleState.IDLE) {
                session.cancel("World changed during puzzle automation");
                puzzleCompletion.clear();
                autoPuzzleState = AutoPuzzleState.IDLE;
                autoPuzzlePosition = null;
            }
        }
        trashCleanup.advanceCooldown();
        if (exploration != null && mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.ticksExisted % 20 == 0) {
            boolean inside = exploration.inDungeonRoom(mc);
            if (inside && !wasInsideRoom) LootGameSolverConfig.setOverlayVisible(true);
            wasInsideRoom = inside;
        }
        if (exploration != null) exploration.tick(mc, session.isActive());
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (rewardReturnPending) {
            int x = (int) Math.floor(mc.thePlayer.posX), y = (int) Math.floor(mc.thePlayer.boundingBox.minY);
            int z = (int) Math.floor(mc.thePlayer.posZ);
            if (exploration != null && !exploration.inDungeonRoom(mc) && mc.theWorld.canBlockSeeTheSky(x, y, z)) {
                rewardLooter.cancel(mc);
                rewardReturnPending = false;
                exploration.finishPuzzle(mc, true);
                return;
            }
            if (rewardPreCleanupPending) {
                if (trashCleanup.active() || trashCleanup.shouldClean(mc)) {
                    if (trashCleanup.tick(mc, true)) {
                        if (exploration != null) exploration.setAutomationStatus(trashCleanup.status());
                        return;
                    }
                }
                rewardPreCleanupPending = false;
            }
            rewardLooter.tick(mc, exploration);
            if (exploration != null) exploration.setAutomationStatus(rewardLooter.status());
            if (!rewardLooter.active()) {
                if (rewardLooter.completed()) {
                    if (trashCleanup.active() || trashCleanup.shouldClean(mc)) {
                        if (trashCleanup.tick(mc, true)) {
                            if (exploration != null) exploration.setAutomationStatus(trashCleanup.status());
                            return;
                        }
                    }
                    rewardReturnPending = false;
                    if (exploration != null) exploration.finishPuzzle(mc, true);
                }
            }
            return;
        }
        if (!session.isActive() && (trashCleanup.active()
            || exploration != null && trashCleanup.inventoryPressure(mc) && exploration.inventoryCleanupSafe(mc))) {
            if (trashCleanup.tick(mc, true)) {
                if (exploration != null) exploration.setAutomationStatus(trashCleanup.status());
                return;
            }
        }
        if (mc.currentScreen != null) {
            if (session.inventoryTransferActive()) session.tick();
            else session.pauseForScreen();
            return;
        }
        if (exploration != null && exploration.consumePuzzleArrival() && autoPuzzleState == AutoPuzzleState.IDLE) {
            autoPuzzlePosition = exploration.puzzlePosition();
            floorActivatorPosition = null;
            floorActivatorClicked = false;
            autoPuzzleState = AutoPuzzleState.OPENING;
            puzzleWaitTicks = 0;
            masterClickAttempts = 0;
            centerClickAttempts = 0;
            minesweeperOpeningClickSent = false;
            lastMasterClickTick = -40;
            lastCenterClickTick = -40;
            puzzleCompletion.begin();
        }

        if (autoPuzzleState == AutoPuzzleState.SOLVING && !puzzleCompletion.won(mc.theWorld != null)) {
            Object game = puzzleBridge.nearbyGame(true);
            int level = puzzleBridge.currentMinesweeperLevel(game);
            boolean nextLevel = activeMinesweeperLevel >= 1 && level > activeMinesweeperLevel && level <= 4;
            boolean retryReady = level == activeMinesweeperLevel && puzzleBridge.canOpenMinesweeperBoard(game);
            if (nextLevel || retryReady) {
                session.cancel(nextLevel ? "Minesweeper stage advanced" : "Minesweeper attempt reset");
                if (nextLevel) activeMinesweeperLevel = level;
                int[] center = puzzleBridge.gameCenter(game);
                autoPuzzleState = exploration != null && center != null && exploration.moveToPuzzleCenter(mc, center)
                    ? AutoPuzzleState.POSITIONING_NEXT_STAGE
                    : AutoPuzzleState.REVEALING_NEXT_STAGE;
                puzzleWaitTicks = 0;
                centerClickAttempts = 0;
                minesweeperOpeningClickSent = false;
                lastCenterClickTick = -40;
                if (exploration != null) exploration.setAutomationStatus(
                    nextLevel ? "Opening Minesweeper stage " + level : "Reopening Minesweeper attempt");
            }
        }

        // Poll dormant board detection less often; poll every tick while solving.
        detectCounter++;
        if (session.isActive() || detectCounter % 20 == 0) {
            session.tick();
        }

        tickAutoPuzzle(mc);

        if (session.isActive()) {
            handleAutoMovement(mc);
        }
    }

    private void tickAutoPuzzle(Minecraft mc) {
        if (autoPuzzleState == AutoPuzzleState.OPENING) {
            puzzleWaitTicks++;
            int[] activator = puzzleBridge.nearbyFloorActivator();
            if (activator != null && exploration != null) {
                rewardCenter = activator.clone();
                floorActivatorPosition = activator;
                floorActivatorClicked = false;
                if (exploration.moveToPuzzleCenter(mc, activator)) {
                    autoPuzzleState = AutoPuzzleState.POSITIONING;
                    puzzleWaitTicks = 0;
                    return;
                }
            }
            Object game = puzzleBridge.gameAtMaster(autoPuzzlePosition);
            if (game == null) game = puzzleBridge.nearbyGame(true);
            boolean masterInteractionSettled = masterClickAttempts > 0 && puzzleWaitTicks - lastMasterClickTick >= 20;
            if (game != null) {
                int[] detectedCenter = puzzleBridge.gameCenter(game);
                if (detectedCenter != null) rewardCenter = detectedCenter;
                if (puzzleBridge.detect(true) != null) {
                    startPuzzleSolver();
                    return;
                }
                int[] center = detectedCenter;
                if (center != null && exploration != null && exploration.moveToPuzzleCenter(mc, center)) {
                    autoPuzzleState = AutoPuzzleState.POSITIONING;
                    puzzleWaitTicks = 0;
                    return;
                }
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null)
                    exploration.setAutomationStatus("Could not reach the board center; explorer held");
                return;
            }
            if (autoPuzzlePosition != null && masterClickAttempts < 4
                && puzzleWaitTicks - lastMasterClickTick >= 40
                && puzzleBridge.activateMaster(autoPuzzlePosition[0], autoPuzzlePosition[1], autoPuzzlePosition[2])) {
                masterClickAttempts++;
                lastMasterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Opening LootGames master");
            }
            if (game == null && masterInteractionSettled && puzzleWaitTicks <= 200 && exploration != null)
                exploration.setAutomationStatus("Waiting for LootGames to create the puzzle");
            if (puzzleWaitTicks > 200) {
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null) exploration.setAutomationStatus(
                    game == null ? "Opened puzzle could not be located; explorer held"
                        : "Puzzle did not open; explorer held");
            }
            return;
        }
        if (autoPuzzleState == AutoPuzzleState.POSITIONING) {
            if (exploration != null && exploration.consumePuzzleCenterArrival()) {
                autoPuzzleState = AutoPuzzleState.REVEALING;
                puzzleWaitTicks = 0;
                centerClickAttempts = 0;
                lastCenterClickTick = -40;
            } else if (exploration == null || !exploration.navigating()) {
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null)
                    exploration.setAutomationStatus("Could not reach the board center; explorer held");
            }
            return;
        }
        if (autoPuzzleState == AutoPuzzleState.POSITIONING_NEXT_STAGE) {
            if (exploration != null && exploration.consumePuzzleCenterArrival()) {
                autoPuzzleState = AutoPuzzleState.REVEALING_NEXT_STAGE;
                puzzleWaitTicks = 0;
                lastCenterClickTick = -40;
            } else if (exploration == null || !exploration.navigating()) {
                // Wait until grounded and centered before clicking.
                autoPuzzleState = AutoPuzzleState.REVEALING_NEXT_STAGE;
                puzzleWaitTicks = 0;
                lastCenterClickTick = -40;
            }
            return;
        }
        if (autoPuzzleState == AutoPuzzleState.REVEALING) {
            puzzleWaitTicks++;
            DetectedGame revealed = puzzleBridge.detect(true);
            Object game = puzzleBridge.nearbyGame(true);
            Object activeGame = game != null ? game : revealed == null ? null : revealed.game;
            if (floorActivatorPosition != null) {
                if (!floorActivatorClicked) {
                    if (puzzleBridge.activateFloorActivator(floorActivatorPosition)) {
                        floorActivatorClicked = true;
                        lastCenterClickTick = puzzleWaitTicks;
                        if (exploration != null) exploration.setAutomationStatus("Opening floor activator");
                    }
                    if (puzzleWaitTicks > 200 && !floorActivatorClicked) {
                        autoPuzzleState = AutoPuzzleState.FAILED;
                        if (exploration != null) exploration.setAutomationStatus("Could not click floor activator");
                    }
                    return;
                }
                if (activeGame == null) {
                    if (puzzleWaitTicks > lastCenterClickTick + 200) {
                        autoPuzzleState = AutoPuzzleState.FAILED;
                        if (exploration != null) exploration.setAutomationStatus("Floor activator did not open puzzle");
                    }
                    return;
                }
                floorActivatorPosition = null;
                centerClickAttempts = 0;
                lastCenterClickTick = puzzleWaitTicks - 20;
            }
            if (revealed != null && centerClickAttempts > 0) {
                if (puzzleWaitTicks - lastCenterClickTick < 20) return;
                startPuzzleSolver();
                return;
            }
            if (centerClickAttempts < 4 && activeGame != null
                && puzzleWaitTicks - lastCenterClickTick >= (centerClickAttempts == 0 ? 1 : 20)
                && puzzleBridge.activateBoardCenter(activeGame)) {
                centerClickAttempts++;
                lastCenterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Revealing board");
            }
            if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Board reveal failed");
            }
            return;
        }
        if (autoPuzzleState == AutoPuzzleState.REVEALING_NEXT_STAGE) {
            puzzleWaitTicks++;
            Object game = puzzleBridge.nearbyGame(true);
            DetectedGame board = puzzleBridge.detect(true);
            Object activeGame = game != null ? game : board == null ? null : board.game;
            if (centerClickAttempts > 0 && board != null) {
                if (puzzleWaitTicks - lastCenterClickTick < 20) return;
                startPuzzleSolver();
                return;
            }
            if (centerClickAttempts == 0 && puzzleBridge.canOpenMinesweeperBoard(activeGame)
                && puzzleWaitTicks - lastCenterClickTick >= 1
                && puzzleBridge.activateBoardCenter(activeGame)) {
                centerClickAttempts++;
                lastCenterClickTick = puzzleWaitTicks;
                if (exploration != null) exploration.setAutomationStatus("Revealing Minesweeper board");
            }
            if (puzzleWaitTicks > (centerClickAttempts > 0 ? lastCenterClickTick + 100 : 600)) {
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Stage reveal failed");
            }
            return;
        }
        if (autoPuzzleState == AutoPuzzleState.SOLVING) {
            if (puzzleCompletion.lost()) {
                session.cancel("LootGames reported a loss; explorer held");
                autoPuzzleState = AutoPuzzleState.FAILED;
                if (exploration != null) exploration.setAutomationStatus("Puzzle lost; explorer held");
                return;
            }
            if (puzzleCompletion.won(mc.theWorld != null)) {
                com.kyroxova.lootgamesolver.core.MiniGame completedGame = session.getGame();
                session.cancel("Puzzle solved");
                puzzleCompletion.clear();
                autoPuzzleState = AutoPuzzleState.IDLE;
                if (exploration != null) {
                    exploration.prepareRewardCollection(mc, rewardCenter != null ? rewardCenter : autoPuzzlePosition);
                    ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewardCenter != null ? rewardCenter : autoPuzzlePosition, completedGame);
                    rewardPreCleanupPending = true;
                    rewardReturnPending = true;
                }
                autoPuzzlePosition = null;
            }
        }
    }

    private void startPuzzleSolver() {
        if (autoPuzzleState != AutoPuzzleState.REVEALING_NEXT_STAGE) {
            Object game = puzzleBridge.nearbyGame(true);
            int level = puzzleBridge.currentMinesweeperLevel(game);
            if (level >= 1) activeMinesweeperLevel = level;
        }
        if (autoPuzzleState != AutoPuzzleState.REVEALING_NEXT_STAGE || inventoryBaseline == null)
            captureInventoryBaseline(Minecraft.getMinecraft());
        session.start(ActionPlanner.Mode.SOLVE);
        if (session.isActive()) autoPuzzleState = AutoPuzzleState.SOLVING;
        else autoPuzzleState = AutoPuzzleState.FAILED;
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
            LootGameSolverConfig.setOverlayVisible(!exploration.navigating() && !rewardReturnPending);
            if (!exploration.navigating() && !rewardReturnPending) {
                inventoryBaseline = InventoryBaseline.capture(mc);
                trashCleanup.setBaseline(inventoryBaseline);
                rewardLooter.setBaseline(inventoryBaseline);
            }
            if (rewardReturnPending) {
                rewardLooter.cancel(mc);
                rewardReturnPending = false;
                if (exploration.navigating()) exploration.toggle(mc);
                exploration.setAutomationStatus("Reward collection stopped");
                return;
            }
            session.cancel("Exploration selected");
            puzzleCompletion.clear();
            autoPuzzleState = AutoPuzzleState.IDLE;
            if (!exploration.navigating() && exploration.inDungeonRoom(mc)) {
                int[] floor = puzzleBridge.nearbyFloorActivator();
                Object nearbyGame = puzzleBridge.nearbyGame(true);
                DetectedGame board = puzzleBridge.detect(true);
                int[] boardCenter = floor != null ? floor
                    : nearbyGame == null ? null : puzzleBridge.gameCenter(nearbyGame);
                if ((floor != null || board != null || puzzleBridge.canOpenMinesweeperBoard(nearbyGame))
                    && boardCenter != null
                    && exploration.resumePuzzleInCurrentRoom(mc, boardCenter)) return;
                int[] rewards = nearbyRewardCenter(mc);
                if (rewards != null) {
                    exploration.prepareRewardCollection(mc, rewards);
                    rewardCenter = rewards;
                    ensureInventoryBaseline(mc);
                    rewardLooter.start(mc, rewards, com.kyroxova.lootgamesolver.core.MiniGame.UNKNOWN);
                    rewardPreCleanupPending = true;
                    rewardReturnPending = true;
                    exploration.setAutomationStatus("Collecting reward chests");
                    return;
                }
                exploration.setAutomationStatus("Checking room board");
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
            session.invalidateDormantCache();
            if (session.isActive()) {
                session.cancel("Auto-Solver Stopped");
                if (autoPuzzleState == AutoPuzzleState.SOLVING) autoPuzzleState = AutoPuzzleState.FAILED;
            } else {
                LootGameSolverConfig.setOverlayVisible(true);
                captureInventoryBaseline(mc);
                session.start(ActionPlanner.Mode.SOLVE);
                Object game = puzzleBridge.nearbyGame(true);
                int level = puzzleBridge.currentMinesweeperLevel(game);
                if (session.isActive()) {
                    int[] currentCenter = game == null ? puzzleBridge.nearbyFloorActivator()
                        : puzzleBridge.gameCenter(game);
                    if (currentCenter != null) rewardCenter = currentCenter;
                    puzzleCompletion.begin();
                    autoPuzzleState = AutoPuzzleState.SOLVING;
                }
                if (level >= 1 && level <= 4) {
                    int[] currentCenter = puzzleBridge.gameCenter(game);
                    if (currentCenter != null) rewardCenter = currentCenter;
                    activeMinesweeperLevel = level;
                    puzzleCompletion.begin();
                    if (session.isActive()) {
                        autoPuzzleState = AutoPuzzleState.SOLVING;
                    } else if (puzzleBridge.canOpenMinesweeperBoard(game)) {
                        autoPuzzleState = AutoPuzzleState.REVEALING_NEXT_STAGE;
                        puzzleWaitTicks = 0;
                        centerClickAttempts = 0;
                        lastCenterClickTick = -40;
                    }
                }
            }
        }

        if (overlayPressed) {
            session.invalidateDormantCache();
            LootGameSolverConfig.setOverlayVisible(!LootGameSolverConfig.showOverlay);
        }
    }

    private void captureInventoryBaseline(Minecraft mc) {
        inventoryBaseline = InventoryBaseline.capture(mc);
        trashCleanup.setBaseline(inventoryBaseline);
        rewardLooter.setBaseline(inventoryBaseline);
    }

    private void ensureInventoryBaseline(Minecraft mc) {
        if (inventoryBaseline == null) captureInventoryBaseline(mc);
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
        List<SolverAction> actions = session.getActiveActions();
        if (actions == null || actions.isEmpty()) {
            releaseMovementKeys(mc);
            return;
        }

        SolverAction targetAction = actions.get(0);
        int[] block = session.getBlockPos(targetAction.position);
        if (block == null) {
            releaseMovementKeys(mc);
            return;
        }

        double dx = block[0] + 0.5D - mc.thePlayer.posX;
        double dy = block[1] + 0.5D - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        double dz = block[2] + 0.5D - mc.thePlayer.posZ;
        double distSq = dx * dx + dy * dy + dz * dz;
        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));
        mc.thePlayer.rotationYaw = updateRotation(mc.thePlayer.rotationYaw, targetYaw, 40.0F);
        mc.thePlayer.rotationPitch = updateRotation(mc.thePlayer.rotationPitch, targetPitch, 30.0F);

        if (distSq > 7.84D) {
            double speed = 0.22D;
            if (distXZ > 0.1D) {
                mc.thePlayer.motionX += (dx / distXZ) * speed;
                mc.thePlayer.motionZ += (dz / distXZ) * speed;
            }
            if (mc.thePlayer.capabilities.allowFlying) {
                mc.thePlayer.capabilities.isFlying = true;
                if (Math.abs(dy) > 0.8D) {
                    mc.thePlayer.motionY = dy > 0 ? 0.15D : -0.15D;
                }
            } else if (mc.thePlayer.isCollidedHorizontally && mc.thePlayer.onGround) {
                mc.thePlayer.jump();
            }
        }
    }

    private float updateRotation(float current, float target, float maxStep) {
        float diff = current - target;
        while (diff >= 180.0F) diff -= 360.0F;
        while (diff < -180.0F) diff += 360.0F;
        if (diff > maxStep) diff = maxStep;
        if (diff < -maxStep) diff = -maxStep;
        return current - diff;
    }

    private void releaseMovementKeys(Minecraft mc) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @SubscribeEvent
    public void overlay(RenderGameOverlayEvent.Text event) {
        if (!LootGameSolverConfig.enabled) return;
        if (!LootGameSolverConfig.showOverlay && !session.isActive()
            && !rewardReturnPending
            && (exploration == null || !exploration.navigating())
            && !wasInsideRoom
            && (autoPuzzleState == AutoPuzzleState.IDLE || autoPuzzleState == AutoPuzzleState.FAILED)) return;
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

            String gameTitle = cur == null ? "Explore Beta"
                : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER ? "Minesweeper"
                    : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU ? "Sudoku"
                        : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT ? "G O L" : "LootGames";

            String statusStr = cur == null ? exploration.hud(mc)
                : session.isActive() ? compactStatus(session.getStatus()) : "Ready";

            boolean autoActive = session.isActive();
            boolean holoActive = LootGameSolverConfig.showOverlay;
            boolean explorerActive = exploration != null && (exploration.navigating() || rewardReturnPending);

            String autoColor = autoActive ? "\u00A7c" : "\u00A77";
            String holoColor = holoActive ? "\u00A7e" : "\u00A77";

            String line1 = "\u00A7b\u00A7lLoot Game Solver";
            String line2 = (explorerActive ? "\u00A7a\u00A7l" : "\u00A7f") + gameTitle
                + " \u00A78|\u00A7"
                + (session.isActive() || explorerActive ? "a" : "e")
                + " "
                + statusStr;
            String line3 = autoColor + "["
                + autoKeyStr
                + "] Auto \u00A78|\u00A77 "
                + holoColor
                + "["
                + holoKeyStr
                + "] Holograms";
            String navigationLine = exploration != null && (cur == null || explorerActive)
                ? "\u00A77" + exploration.navigationStatus()
                : null;
            if (exploration != null && LootGameSolverConfig.explorationEnabled) {
                line3 += " \u00A78|" + (explorerActive ? "\u00A7a" : "\u00A77")
                    + " ["
                    + GameSettings.getKeyDisplayString(Keybinds.explore.getKeyCode())
                    + "] "
                    + exploration.explorationAction();
            }
            if (explorerActive && navigationLine != null) navigationLine = "\u00A7a" + exploration.navigationStatus();
            int width1 = mc.fontRenderer.getStringWidth("Loot Game Solver");
            int width2 = mc.fontRenderer.getStringWidth(gameTitle + " | " + statusStr);
            int width3 = mc.fontRenderer.getStringWidth(line3);
            int maxWidth = Math.max(width1, Math.max(width2, width3));
            int navigationWidth = navigationLine == null ? 0 : mc.fontRenderer.getStringWidth(navigationLine);
            maxWidth = Math.max(maxWidth, navigationWidth);

            int panelWidth = maxWidth + 32;
            boolean showCheckpointProgress = exploreKeyHeld || checkpointClearDoneTicks > 0;
            int panelHeight = (navigationLine == null ? 44 : 54) + (showCheckpointProgress ? 10 : 0);
            int x = resolution.getScaledWidth() - panelWidth - 15;
            int y = resolution.getScaledHeight() - panelHeight - 55;

            drawNineSlice(TEXTURE_UI_9SLICE, x, y, panelWidth, panelHeight, mc);

            mc.fontRenderer.drawStringWithShadow(line1, x + (panelWidth - width1) / 2, y + 7, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line2, x + (panelWidth - width2) / 2, y + 17, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line3, x + (panelWidth - width3) / 2, y + 27, 0xFFFFFF);
            if (navigationLine != null) mc.fontRenderer
                .drawStringWithShadow(navigationLine, x + (panelWidth - navigationWidth) / 2, y + 37, 0xFFFFFF);
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
