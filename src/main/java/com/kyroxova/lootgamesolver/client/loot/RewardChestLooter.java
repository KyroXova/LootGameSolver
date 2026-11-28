package com.kyroxova.lootgamesolver.client.loot;

import static com.kyroxova.lootgamesolver.client.loot.RewardChestView.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.Vec3;
import net.minecraft.util.WeightedRandomChestContent;
import net.minecraftforge.common.ChestGenHooks;

import com.kyroxova.lootgamesolver.client.AimHelper;
import com.kyroxova.lootgamesolver.client.BaritoneInventoryBridge;
import com.kyroxova.lootgamesolver.client.ClientHooks;
import com.kyroxova.lootgamesolver.client.VisibleBlockInteraction;
import com.kyroxova.lootgamesolver.client.storage.InventoryBaseline;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.explorer.excavation.MiningAimGate;

public final class RewardChestLooter {

    public final List<int[]> chests = new ArrayList<int[]>();
    public final List<RewardLootPolicy.Entry> discovered = new ArrayList<RewardLootPolicy.Entry>();
    public List<RewardLootPolicy.Entry> queue = new ArrayList<RewardLootPolicy.Entry>();
    public final Map<Long, ItemStack> scanned = new HashMap<Long, ItemStack>();
    public InventoryBaseline baseline;
    public MinedDropTracker minedDrops;

    public void setMinedDrops(MinedDropTracker value) {
        minedDrops = value;
    }

    public final RewardBackpackPacker backpackPacker = new RewardBackpackPacker();
    private final MiningAimGate chestAim = new MiningAimGate();
    private final LootedChestStore lootedChests = new LootedChestStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "looted-chests.json"));
    private final PendingLootStore pendingLoot = new PendingLootStore(
        new java.io.File(
            new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/lootgamesolver"),
            "pending-loot.json"));
    public int[] center;
    private int[] checkpointCenter;
    private int[] chestView;
    private final List<int[]> failedChestViews = new ArrayList<int[]>();
    private int failedViewChest = -1;
    private int failedViewRounds;
    private int chestDiscoveryQuietTicks;
    private MiniGame gameType;
    public int ticks, index, waiting, openWait, pendingSlot = -1, pendingCount;
    public int queueIndex, oldSlot = -1;
    public boolean active, opened, collecting, packing;
    private boolean existingInventoryPackRequested;
    public boolean packingOnly;
    public String status = "";

    public void setBaseline(InventoryBaseline value) {
        baseline = value;
    }

    public void start(Minecraft mc, int[] position, MiniGame type) {
        if (baseline == null) baseline = InventoryBaseline.capture(mc);
        center = position == null ? null : position.clone();
        checkpointCenter = center == null ? null : center.clone();
        chestView = null;
        failedChestViews.clear();
        failedViewChest = -1;
        failedViewRounds = 0;
        chestDiscoveryQuietTicks = 0;
        gameType = type;
        ticks = 0;
        index = 0;
        waiting = 0;
        pendingSlot = -1;
        openWait = 0;
        opened = false;
        collecting = false;
        packing = false;
        existingInventoryPackRequested = false;
        packingOnly = false;
        queueIndex = 0;
        oldSlot = -1;
        chestAim.reset();
        discovered.clear();
        queue.clear();
        scanned.clear();
        backpackPacker.reset();
        active = true;
        chests.clear();
        status = "Waiting for reward chests";
    }

    public boolean active() {
        return active;
    }

    public int[] expectedView() {
        return chestView == null ? null : chestView.clone();
    }

    public boolean hasUncollectedCluster(Minecraft mc, int[] cluster) {
        if (mc == null || mc.theWorld == null || cluster == null) return false;
        String world = Explorer.worldIdentity(mc);
        int dimension = mc.theWorld.provider.dimensionId;
        for (Object obj : mc.theWorld.loadedTileEntityList) {
            if (!(obj instanceof TileEntityChest)) continue;
            TileEntity tile = (TileEntity) obj;
            if (tile.yCoord == cluster[1]
                && Math.abs(tile.xCoord - cluster[0]) + Math.abs(tile.zCoord - cluster[2]) == 1
                && !lootedChests.contains(world, dimension, new int[] { tile.xCoord, tile.yCoord, tile.zCoord }))
                return true;
        }
        return false;
    }

    public int chestDistance(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return 0;
        int[] pos = index < chests.size() ? chests.get(index) : center;
        if (pos == null) return 0;
        return (int) Math.round(Math.hypot(mc.thePlayer.posX - pos[0] - 0.5D, mc.thePlayer.posZ - pos[2] - 0.5D));
    }

    public boolean startInventoryPacking(Minecraft mc) {
        if (baseline == null || !hasPackableItems(mc)) return false;
        start(mc, null, MiniGame.UNKNOWN);
        packingOnly = true;
        packing = true;
        status = "Packing inventory";
        return true;
    }

    public boolean completed() {
        return "Reward chests collected".equals(status);
    }

    public String status() {
        return status;
    }

    public PendingLootStore.Checkpoint pendingCheckpoint() {
        return pendingLoot.load();
    }

    public PendingLootStore.Checkpoint saveCheckpoint(Minecraft mc) {
        if (mc == null || mc.theWorld == null || checkpointCenter == null) return null;
        if (!pendingLoot
            .save(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, checkpointCenter, gameType, true))
            return null;
        return pendingLoot.load();
    }

    public boolean clearCheckpoint() {
        return pendingLoot.clear();
    }

    public int[] rewardCenter() {
        return checkpointCenter == null ? null : checkpointCenter.clone();
    }

    public void cancel(Minecraft mc) {
        backpackPacker.cancel(mc, oldSlot);
        if ((opened || packing) && mc.thePlayer != null && mc.currentScreen instanceof GuiContainer)
            mc.thePlayer.closeScreen();
        active = false;
        opened = false;
    }

    public void tick(Minecraft mc, Explorer exploration) {
        if (!active || mc.thePlayer == null || mc.theWorld == null) return;
        if (exploration != null) exploration.suspendUnrelatedRouteForRewards(mc, chestView);
        updateChestViewNavigation(mc, exploration);
        if (exploration != null && exploration.navigating()) {
            status = "Moving toward reward chests";
            return;
        }
        checkChestViewArrival(mc);
        if (exploration != null) exploration.consumePuzzleCenterArrival();
        if (backpackPacker.handleTransfer(mc, this) || backpackPacker.handleHotbarRestore(mc, this)) return;
        if (packingOnly) {
            if (packing) backpackPacker.tick(mc, exploration, this);
            else {
                active = false;
                status = "Inventory packing finished";
            }
            return;
        }
        if (center == null) {
            status = "Reward center unavailable";
            active = false;
            return;
        }
        if (++ticks < 30) return;
        if (chests.isEmpty() && index == 0) {
            int[] cluster = ClientHooks.nearbyRewardCenter(mc, center);
            if (cluster != null) center = cluster;
        }
        if (discoverChests(mc)) chestDiscoveryQuietTicks = 0;
        else if (chestDiscoveryQuietTicks < 60) chestDiscoveryQuietTicks++;
        if (chests.isEmpty()) {
            status = "Waiting for reward chests";
            return;
        }
        if (packing) {
            backpackPacker.tick(mc, exploration, this);
            return;
        }
        while (!collecting && index < chests.size()
            && lootedChests.contains(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(index)))
            index++;
        if (!collecting && index >= chests.size()) {
            handleAllChestsDone(mc);
            return;
        }
        if (collecting && tickCollectingQueue(mc)) return;
        if (waiting > 0) {
            waiting--;
            return;
        }
        int[] pos = chests.get(index);
        if (failedViewChest != index) {
            failedViewChest = index;
            failedChestViews.clear();
            failedViewRounds = 0;
        }
        if (mc.currentScreen instanceof GuiChest && opened && mc.thePlayer.openContainer instanceof ContainerChest) {
            tickOpenChestScreen(mc, pos);
            return;
        }
        tickApproachAndOpenChest(mc, exploration, pos);
    }

    private void updateChestViewNavigation(Minecraft mc, Explorer exploration) {
        if (exploration == null || !exploration.navigating() || chestView == null || index >= chests.size()) return;
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        net.minecraft.util.MovingObjectPosition visible = visibleChest(mc, eye, chests.get(index));
        double reach = Math.min(4D, mc.playerController.getBlockReachDistance());
        if ((visible == null || eye.squareDistanceTo(visible.hitVec) > reach * reach) && !collecting) {
            for (int i = index + 1; i < chests.size(); i++) {
                if (lootedChests.contains(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(i)))
                    continue;
                net.minecraft.util.MovingObjectPosition other = visibleChest(mc, eye, chests.get(i));
                if (other != null && eye.squareDistanceTo(other.hitVec) <= reach * reach) {
                    java.util.Collections.swap(chests, index, i);
                    visible = other;
                    break;
                }
            }
        }
        if (visible != null && eye.squareDistanceTo(visible.hitVec) <= reach * reach) {
            exploration.stopRewardView(mc);
            chestView = null;
        }
    }

    private void checkChestViewArrival(Minecraft mc) {
        if (chestView == null) return;
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        boolean arrived = mc.thePlayer.getDistanceSq(chestView[0] + 0.5D, chestView[1] + 1D, chestView[2] + 0.5D)
            <= 2.25D;
        if (!arrived || (index < chests.size() && visibleChest(mc, eye, chests.get(index)) == null)) {
            failedChestViews.add(chestView);
        }
        chestView = null;
    }

    private void handleAllChestsDone(Minecraft mc) {
        if (chestDiscoveryQuietTicks < 60) {
            status = "Checking for remaining reward chests";
            return;
        }
        if (opened && mc.currentScreen instanceof GuiChest) mc.thePlayer.closeScreen();
        opened = false;
        if (!existingInventoryPackRequested && hasPackableItems(mc)) {
            existingInventoryPackRequested = true;
            packing = true;
            backpackPacker.packOpening = false;
            backpackPacker.packMoved = false;
            backpackPacker.backpackSlot = -1;
            java.util.Arrays.fill(backpackPacker.backpackTried, false);
            java.util.Arrays.fill(backpackPacker.slotRejectedForCurrentBag, false);
            status = "Packing leftover inventory items";
            return;
        }
        status = "Reward chests collected";
        PendingLootStore.Checkpoint checkpoint = pendingLoot.load();
        if (checkpoint != null && checkpoint.world.equals(Explorer.worldIdentity(mc))
            && checkpoint.dimension == mc.theWorld.provider.dimensionId
            && java.util.Arrays.equals(checkpoint.center, checkpointCenter)) pendingLoot.clear();
        active = false;
    }

    private boolean tickCollectingQueue(Minecraft mc) {
        if (queueIndex >= queue.size()) {
            if (!opened || !(mc.currentScreen instanceof GuiChest)
                || !(mc.thePlayer.openContainer instanceof ContainerChest)) {
                status = "Rechecking reward chest";
                collecting = false;
                return true;
            }
            IInventory checked = ((ContainerChest) mc.thePlayer.openContainer).getLowerChestInventory();
            for (int slot = 0; slot < checked.getSizeInventory(); slot++) {
                if (checked.getStackInSlot(slot) != null) {
                    collecting = false;
                    status = "Checking remaining items";
                    return true;
                }
            }
            if (!lootedChests.mark(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(index))) {
                status = "Could not save chest progress";
                return true;
            }
            mc.thePlayer.closeScreen();
            opened = false;
            collecting = false;
            index++;
            openWait = 0;
            waiting = 2;
            status = "Chest collected";
            return true;
        }
        int nextChest = queue.get(queueIndex).chest;
        if (opened && index != nextChest && mc.currentScreen instanceof GuiChest) {
            mc.thePlayer.closeScreen();
            opened = false;
            openWait = 0;
            waiting = 2;
        }
        index = nextChest;
        return false;
    }

    private void tickOpenChestScreen(Minecraft mc, int[] pos) {
        ContainerChest container = (ContainerChest) mc.thePlayer.openContainer;
        IInventory chest = container.getLowerChestInventory();
        if (chest.getSizeInventory() != 27
            || !(mc.theWorld.getTileEntity(pos[0], pos[1], pos[2]) instanceof TileEntityChest)) {
            mc.thePlayer.closeScreen();
            opened = false;
            openWait = 0;
            waiting = 5;
            status = "Unexpected chest screen; looting paused";
            return;
        }
        if (!collecting) {
            discovered.clear();
            for (int slot = 0; slot < chest.getSizeInventory(); slot++) {
                ItemStack stack = chest.getStackInSlot(slot);
                if (stack == null) continue;
                discovered.add(new RewardLootPolicy.Entry(index, slot, probability(mc, stack, pos)));
                scanned.put(slotKey(index, slot), stack.copy());
            }
            queue = RewardLootPolicy.ordered(discovered);
            queueIndex = 0;
            collecting = true;
            return;
        }
        if (pendingSlot >= 0 && handlePendingChestTransfer(mc, chest)) return;
        if (queueIndex >= queue.size() || queue.get(queueIndex).chest != index) {
            mc.thePlayer.closeScreen();
            opened = false;
            openWait = 0;
            waiting = 4;
            return;
        }
        int best = queue.get(queueIndex).slot;
        if (best < 0 || best >= chest.getSizeInventory() || chest.getStackInSlot(best) == null) {
            queueIndex++;
            return;
        }
        ItemStack stack = chest.getStackInSlot(best);
        ItemStack expected = scanned.get(slotKey(index, best));
        if (expected == null || !expected.isItemEqual(stack) || !ItemStack.areItemStackTagsEqual(expected, stack)) {
            status = "Reward chest changed; looting stopped";
            cancel(mc);
            return;
        }
        int beforeCount = stack.stackSize;
        if (!BaritoneInventoryBridge.quickMove(mc, best)) {
            status = "Reward transfer unavailable";
            return;
        }
        pendingSlot = best;
        pendingCount = beforeCount;
        waiting = 2;
        status = "Collecting reward items";
    }

    private boolean handlePendingChestTransfer(Minecraft mc, IInventory chest) {
        ItemStack after = chest.getStackInSlot(pendingSlot);
        if (after != null && after.stackSize >= pendingCount) {
            boolean inventoryHasSpace = mc.thePlayer.inventory.getFirstEmptyStack() >= 0;
            if (!inventoryHasSpace && hasPackableItems(mc)) {
                mc.thePlayer.closeScreen();
                opened = false;
                pendingSlot = -1;
                packing = true;
                backpackPacker.packOpening = false;
                backpackPacker.packMoved = false;
                backpackPacker.backpackSlot = -1;
                status = "Moving collected rewards into a backpack";
                return true;
            }
            if (!inventoryHasSpace) {
                mc.thePlayer.closeScreen();
                opened = false;
                pendingSlot = -1;
                active = false;
                status = "Inventory and backpacks full; rewards remain in chest";
                saveCheckpoint(mc);
                return true;
            }
            queueIndex++;
            pendingSlot = -1;
            return true;
        }
        ItemStack transferredReward = scanned.get(slotKey(index, pendingSlot));
        if (minedDrops != null && transferredReward != null && (after == null || after.stackSize < pendingCount))
            minedDrops.protectReward(transferredReward);
        pendingSlot = -1;
        java.util.Arrays.fill(backpackPacker.backpackTried, false);
        if (after == null) {
            queueIndex++;
            return false;
        }
        return true;
    }

    private void tickApproachAndOpenChest(Minecraft mc, Explorer exploration, int[] pos) {
        if (mc.currentScreen != null) {
            status = "Close the open screen to collect rewards";
            return;
        }
        if (opened) {
            if (++openWait > 40) {
                status = "Retrying reward chest";
                opened = false;
                openWait = 0;
            }
            return;
        }
        double reach = Math.min(4.0D, mc.playerController.getBlockReachDistance());
        Vec3 eye = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        net.minecraft.util.MovingObjectPosition hit = visibleChest(mc, eye, pos);
        if (hit == null || eye.squareDistanceTo(hit.hitVec) > reach * reach) {
            if (!collecting && swapToVisibleUnlootedChest(mc, eye, reach)) return;
            if (exploration != null && moveToChestView(mc, exploration, pos, center, failedChestViews))
                status = "Moving toward reward chests";
            else if (active) status = "Finding a clear view of the reward chest";
            waiting = 6;
            return;
        }
        double hitX = hit.hitVec.xCoord - eye.xCoord, hitY = hit.hitVec.yCoord - eye.yCoord;
        double hitZ = hit.hitVec.zCoord - eye.zCoord;
        float targetYaw = Math.hypot(hitX, hitZ) < 0.05D ? mc.thePlayer.rotationYaw
            : AimHelper.calculateYaw(hitX, hitZ);
        float targetPitch = AimHelper.calculatePitch(hitX, hitY, hitZ);
        if (!turnToward(mc, exploration, targetYaw, targetPitch)) {
            chestAim.reset();
            status = "Facing reward chest";
            return;
        }
        net.minecraft.util.MovingObjectPosition clickTarget = resolveChestClickTarget(mc, eye, pos, reach, hit);
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(targetYaw - mc.thePlayer.rotationYaw);
        float pitchError = targetPitch - mc.thePlayer.rotationPitch;
        if (clickTarget == null || eye.squareDistanceTo(clickTarget.hitVec) > reach * reach
            || Math.abs(yawError) > 4.5F
            || Math.abs(pitchError) > 4.5F) {
            status = "Facing reward chest";
            return;
        }
        if (mc.thePlayer.isSneaking()) {
            status = "Release sneak to open reward chest";
            return;
        }
        boolean direct = mc.playerController.onPlayerRightClick(
            mc.thePlayer,
            mc.theWorld,
            mc.thePlayer.getHeldItem(),
            pos[0],
            pos[1],
            pos[2],
            clickTarget.sideHit,
            clickTarget.hitVec);
        mc.thePlayer.swingItem();
        if (!direct) {
            status = "Chest interaction unavailable";
            return;
        }
        chestAim.reset();
        if (exploration != null) exploration.clearAim();
        opened = true;
        waiting = 4;
        status = "Opening reward chest";
    }

    private boolean swapToVisibleUnlootedChest(Minecraft mc, Vec3 eye, double reach) {
        for (int i = index + 1; i < chests.size(); i++) {
            net.minecraft.util.MovingObjectPosition other = visibleChest(mc, eye, chests.get(i));
            if (other != null && eye.squareDistanceTo(other.hitVec) <= reach * reach
                && !lootedChests.contains(Explorer.worldIdentity(mc), mc.theWorld.provider.dimensionId, chests.get(i))) {
                java.util.Collections.swap(chests, index, i);
                status = "Opening a visible reward chest";
                return true;
            }
        }
        return false;
    }

    private static net.minecraft.util.MovingObjectPosition resolveChestClickTarget(Minecraft mc, Vec3 eye, int[] pos,
        double reach, net.minecraft.util.MovingObjectPosition fallback) {
        Vec3 look = mc.thePlayer.getLook(1.0F);
        net.minecraft.util.MovingObjectPosition aimed = mc.theWorld
            .rayTraceBlocks(eye, eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach));
        net.minecraft.util.MovingObjectPosition crosshair = mc.objectMouseOver;
        boolean matches = mc.renderViewEntity == mc.thePlayer
            && (VisibleBlockInteraction.matches(crosshair, aimed, eye, pos[0], pos[1], pos[2], reach)
                || (aimed != null && aimed.blockX == pos[0]
                    && aimed.blockY == pos[1]
                    && aimed.blockZ == pos[2]
                    && eye.squareDistanceTo(aimed.hitVec) <= reach * reach));
        if (!matches) return null;
        if (crosshair != null && crosshair.blockX == pos[0]
            && crosshair.blockY == pos[1]
            && crosshair.blockZ == pos[2]) return crosshair;
        if (aimed != null && aimed.blockX == pos[0] && aimed.blockY == pos[1] && aimed.blockZ == pos[2]) return aimed;
        return fallback;
    }

    private boolean discoverChests(Minecraft mc) {
        boolean added = false;
        for (Object obj : mc.theWorld.loadedTileEntityList) {
            if (!(obj instanceof TileEntityChest) || chests.size() >= 4) continue;
            TileEntity tile = (TileEntity) obj;
            if (Math.abs(tile.xCoord - center[0]) + Math.abs(tile.zCoord - center[2]) > 3
                || Math.abs(tile.yCoord - center[1]) > 5) continue;
            boolean known = false;
            for (int[] pos : chests) if (pos[0] == tile.xCoord && pos[1] == tile.yCoord && pos[2] == tile.zCoord) {
                known = true;
                break;
            }
            if (!known) {
                chests.add(new int[] { tile.xCoord, tile.yCoord, tile.zCoord });
                added = true;
            }
        }
        return added;
    }

    private boolean moveToChestView(Minecraft mc, Explorer exploration, int[] chest, int[] roomCenter,
        List<int[]> failedViews) {
        chestView = RewardChestView.findAndMove(mc, exploration, chest, roomCenter, failedViews);
        if (chestView == null && ++failedViewRounds >= 6) {
            boolean saved = saveCheckpoint(mc) != null;
            active = false;
            status = "Reward chest unreachable; collection paused";
            mc.thePlayer.addChatMessage(
                new net.minecraft.util.ChatComponentText(
                    "\u00A7e[LootGameSolver] Could not reach a reward chest."
                        + (saved ? " Rewards saved; use /lgs resumeLoot after clearing the route."
                            : " Could not save the reward checkpoint.")));
        }
        return chestView != null;
    }

    public static boolean turnToward(Minecraft mc, Explorer exploration, float yaw, float pitch) {
        if (exploration != null) {
            exploration.look(yaw, pitch, true);
        } else {
            float yawDelta = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
            float pitchDelta = pitch - mc.thePlayer.rotationPitch;
            float yawStep = Math.max(-15F, Math.min(15F, yawDelta));
            float pitchStep = Math.max(-15F, Math.min(15F, pitchDelta));
            mc.thePlayer.setAngles(yawStep / 0.15F, -pitchStep / 0.15F);
        }
        float yawError = net.minecraft.util.MathHelper.wrapAngleTo180_float(yaw - mc.thePlayer.rotationYaw);
        float pitchError = pitch - mc.thePlayer.rotationPitch;
        return Math.abs(yawError) < 4.0F && Math.abs(pitchError) < 4.0F;
    }

    public boolean hasPackableItems(Minecraft mc) {
        return backpackPacker.hasPackableItems(mc, baseline, minedDrops);
    }

    public boolean isPackable(Minecraft mc, int slotIndex, ItemStack stack) {
        return backpackPacker.isPackable(mc, slotIndex, stack, baseline, minedDrops);
    }

    private static long slotKey(int chest, int slot) {
        return ((long) chest << 32) | (slot & 0xffffffffL);
    }

    private double probability(Minecraft mc, ItemStack stack, int[] chest) {
        if (!mc.isSingleplayer()) return Double.NaN;
        int dx = chest[0] - center[0], dz = chest[2] - center[2];
        int stage = stageAt(dx, dz);
        if (stage == 0 || gameType == null) return Double.NaN;
        String field = gameType == MiniGame.GAME_OF_LIGHT ? "rewardsGol"
            : gameType == MiniGame.MINESWEEPER ? "rewardsMinesweeper"
                : gameType == MiniGame.SUDOKU ? "rewardsSudoku" : null;
        if (field == null) return Double.NaN;
        try {
            Class<?> configs = Class.forName("ru.timeconqueror.lootgames.common.config.LGConfigs");
            Object rewards = configs.getField("REWARDS")
                .get(null);
            Object stages = rewards.getClass()
                .getField(field)
                .get(rewards);
            Object reward = stages.getClass()
                .getMethod("getStage" + stage)
                .invoke(stages);
            String table = (String) reward.getClass()
                .getMethod("getLootTable", net.minecraft.world.World.class)
                .invoke(reward, mc.theWorld);
            WeightedRandomChestContent[] entries = ChestGenHooks.getItems(table, new Random(0L));
            return RewardWeighting.probability(stack, entries);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Double.NaN;
        }
    }

    public static int stageAt(int dx, int dz) {
        if (dx == 0 && dz == -1) return 1;
        if (dx == 1 && dz == 0) return 2;
        if (dx == 0 && dz == 1) return 3;
        if (dx == -1 && dz == 0) return 4;
        return 0;
    }

    private boolean isStandingOnChest(Minecraft mc, int[] chest) {
        return false;
    }
}
