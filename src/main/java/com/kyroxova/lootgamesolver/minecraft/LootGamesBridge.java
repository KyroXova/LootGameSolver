package com.kyroxova.lootgamesolver.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovingObjectPosition;

import com.kyroxova.lootgamesolver.LootGameSolver;
import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.detection.MiniGameDetector;

public final class LootGamesBridge implements MiniGameDetector {

    private boolean pendingSneakRelease;
    private boolean restoreSneaking;
    private final FoliageClearer foliageClearer = new FoliageClearer();
    private final LootGamesActionTracker actionTracker = new LootGamesActionTracker();

    public void cancelFoliageClearing() {
        foliageClearer.reset();
    }

    public boolean isClearingFoliage() {
        return foliageClearer.clearing();
    }

    public boolean foliageClearingFailed() {
        return foliageClearer.failed();
    }

    public boolean clearMinesweeperObstruction(int[] position) {
        Minecraft mc = Minecraft.getMinecraft();
        if (position == null || position.length < 3 || mc.theWorld == null || mc.thePlayer == null) return false;
        String type = mc.theWorld.getBlock(position[0], position[1], position[2])
            .getClass()
            .getName();
        if (!type.equals(LootGamesTileAccess.LOOTGAMES_PKG + "common.block.MSActivatorBlock")) {
            Object game = nearbyGame(true);
            if (game == null || !game.getClass()
                .getName()
                .equals(LootGamesTileAccess.MS_GAME)) return false;
        }
        return foliageClearer.clearLine(mc, position[0], position[1], position[2]);
    }

    public int[] findRoomObstruction(int[] center) {
        Minecraft mc = Minecraft.getMinecraft();
        if (center == null || mc.theWorld == null || mc.thePlayer == null) return null;
        int cx = center[0], cy = center[1], cz = center[2];
        int[] closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                for (int dy = 1; dy <= 3; dy++) {
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    net.minecraft.block.Block block = mc.theWorld.getBlock(x, y, z);
                    if (FoliageClearer.isRoomObstruction(block, mc.theWorld, x, y, z)) {
                        double distSq = mc.thePlayer.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D);
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            closest = new int[] { x, y, z };
                        }
                    }
                }
            }
        }
        return closest;
    }

    public boolean clearObstructionBlock(int x, int y, int z) {
        return foliageClearer.clearBlock(Minecraft.getMinecraft(), x, y, z);
    }

    public void invalidateDormantCache() {}

    public void resetGameOfLightPendingAction() {
        actionTracker.resetGameOfLightPendingAction();
    }

    public boolean isGameOfLightActionPending() {
        return actionTracker.isGameOfLightActionPending();
    }

    public DetectedGame detect() {
        return detect(false);
    }

    public DetectedGame detect(boolean forceScan) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return null;
        Object game = LootGamesTileAccess.findNearbyGame(mc.theWorld, mc.thePlayer, forceScan);
        if (game == null) return null;
        try {
            String name = game.getClass()
                .getName();
            if (LootGamesTileAccess.MS_GAME.equals(name)) return actionTracker.readMinesweeper(game);
            if (LootGamesTileAccess.SDK_GAME.equals(name)) return actionTracker.readSudoku(game);
            if (LootGamesTileAccess.GOL_GAME.equals(name)) return actionTracker.readGameOfLight(game);
        } catch (Exception e) {
            LootGameSolver.LOG.debug("Could not read LootGames state", e);
        }
        return null;
    }

    public boolean interact(DetectedGame detected, SolverAction action) {
        return interact(detected, action, false);
    }

    public boolean interact(DetectedGame detected, SolverAction action, boolean sudokuReverseClick) {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.theWorld == null || mc.thePlayer == null || mc.playerController == null) return false;
            releasePendingSneak(mc);
            int[] block = actionTracker.boardBlock(detected.game, action.position);

            if (detected.type == MiniGame.MINESWEEPER && foliageClearer.clearLine(mc, block[0], block[1], block[2]))
                return false;

            if (!LootGamesInteractionHelper
                .preparePlayerPositionAndAim(mc.thePlayer, block[0], block[1], block[2], true)) {
                return false;
            }
            MovingObjectPosition visible = LootGamesInteractionHelper.visibleTarget(mc, block[0], block[1], block[2]);
            if (visible == null || visible.sideHit != 1) return false;
            boolean attack = action.type == SolverAction.Type.REVEAL && detected.type != MiniGame.GAME_OF_LIGHT;
            if (!attack && !LootGamesInteractionHelper.selectSafeInteractionItem(mc)) return false;
            boolean shouldSneak = detected.type == MiniGame.SUDOKU && action.type == SolverAction.Type.SET_VALUE
                && sudokuReverseClick;
            boolean wasSneaking = mc.thePlayer.isSneaking();
            if (shouldSneak && !wasSneaking) {
                mc.getNetHandler()
                    .addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, shouldSneak ? 1 : 2));
                mc.thePlayer.setSneaking(shouldSneak);
            }
            try {
                return BaritoneBlockBridge
                    .click(mc, block[0], block[1], block[2], visible.sideHit, visible.hitVec, attack);
            } finally {
                if (shouldSneak && !wasSneaking) {
                    pendingSneakRelease = true;
                    restoreSneaking = wasSneaking;
                }
            }
        } catch (Exception e) {
            LootGameSolver.LOG.warn("LootGames interaction failed; automation stopped", e);
            return false;
        }
    }

    public void tick() {
        releasePendingSneak(Minecraft.getMinecraft());
    }

    public void releaseSneak() {
        releasePendingSneak(Minecraft.getMinecraft());
    }

    private void releasePendingSneak(Minecraft mc) {
        if (!pendingSneakRelease) return;
        pendingSneakRelease = false;
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;
        mc.getNetHandler()
            .addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, restoreSneaking ? 1 : 2));
        mc.thePlayer.setSneaking(restoreSneaking);
    }

    public boolean hasClearView(int[] position) {
        return LootGamesInteractionHelper.hasClearView(Minecraft.getMinecraft(), position);
    }

    public static boolean topFaceVisible(Minecraft mc, int[] position) {
        return LootGamesInteractionHelper.topFaceVisible(mc, position);
    }

    public static boolean topFaceVisibleFrom(Minecraft mc, int[] position, double x, double y, double z) {
        return LootGamesInteractionHelper.topFaceVisibleFrom(mc, position, x, y, z);
    }

    public boolean activateMaster(int x, int y, int z) {
        return LootGamesInteractionHelper.activateMaster(Minecraft.getMinecraft(), x, y, z);
    }

    public static MovingObjectPosition visibleMasterFace(Minecraft mc, int x, int y, int z) {
        return LootGamesInteractionHelper.visibleMasterFace(mc, x, y, z);
    }

    public int[] nearbyMasterBlock() {
        return nearbyMasterBlock(null);
    }

    public int[] nearbyMasterBlock(int[] center) {
        return LootGamesTileAccess.nearbyMasterBlock(Minecraft.getMinecraft(), center);
    }

    public static boolean isMasterBlock(net.minecraft.block.Block block) {
        return LootGamesTileAccess.isMasterBlock(block);
    }

    public static boolean isMasterTile(String className) {
        return LootGamesTileAccess.isMasterTile(className);
    }

    static boolean isFloorActivatorName(String name) {
        return LootGamesTileAccess.isFloorActivatorName(name);
    }

    static boolean targetHit(MovingObjectPosition hit, int x, int y, int z) {
        return LootGamesInteractionHelper.targetHit(hit, x, y, z);
    }

    static boolean minesweeperGenerated(Object game) throws Exception {
        return LootGamesActionTracker.minesweeperGenerated(game);
    }

    public Object nearbyGame(boolean forceScan) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return null;
        return LootGamesTileAccess.findNearbyGame(mc.theWorld, mc.thePlayer, forceScan);
    }

    public int[] nearbyFloorActivator() {
        return LootGamesTileAccess.nearbyFloorActivator(Minecraft.getMinecraft());
    }

    public boolean activateFloorActivator(int[] position) {
        return LootGamesInteractionHelper.activateFloorActivator(Minecraft.getMinecraft(), position, foliageClearer);
    }

    public Object gameAtMaster(int[] position) {
        return LootGamesTileAccess.gameAtMaster(Minecraft.getMinecraft(), position);
    }

    public boolean activateBoardCenter(Object game) {
        return LootGamesInteractionHelper
            .activateBoardCenter(Minecraft.getMinecraft(), game, gameCenter(game), foliageClearer);
    }

    public boolean requiresMinesweeperOpeningClick(DetectedGame detected) {
        return actionTracker.requiresMinesweeperOpeningClick(detected);
    }

    public int currentMinesweeperLevel(Object game) {
        return actionTracker.currentMinesweeperLevel(game);
    }

    public boolean canOpenMinesweeperBoard(Object game) {
        return actionTracker.canOpenMinesweeperBoard(game);
    }

    public int[] gameCenter(Object game) {
        return actionTracker.gameCenter(game);
    }

    public void recordSubmittedAction(DetectedGame snapshot) {
        actionTracker.recordSubmittedAction(snapshot);
    }

    public boolean actionComplete(DetectedGame current, SolverAction action) {
        return actionTracker.actionComplete(current, action);
    }

    public void submitSudokuCheck(DetectedGame snapshot) {
        actionTracker.submitSudokuCheck(snapshot);
    }

    public int[] getBlockPos(Object game, CellPosition position) {
        return actionTracker.getBlockPos(game, position);
    }
}
