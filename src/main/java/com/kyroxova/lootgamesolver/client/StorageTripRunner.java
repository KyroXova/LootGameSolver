package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;

/** Coordinates command dispatch and verifies the player reaches the expected area. */
final class StorageTripRunner {

    private static final int COMMAND_TIMEOUT = 600;
    private final StorageTripState state = new StorageTripState();
    private String world;
    private String destination;
    private int originDimension, ticks;
    private double originX, originY, originZ;
    private boolean warpConfirmed;

    StorageTripState.Phase phase() {
        return state.phase();
    }

    boolean start(Minecraft mc, boolean checkpointSaved) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return false;
        String currentWorld = Explorer.worldIdentity(mc);
        boolean configured = StorageTripPolicy.destinationConfigured(
            LootGameSolverConfig.storageEnabled,
            LootGameSolverConfig.storageWorld,
            currentWorld,
            LootGameSolverConfig.storageDestination,
            LootGameSolverConfig.storageTargets);
        if (!state.begin(configured, checkpointSaved)) return false;
        world = currentWorld;
        destination = LootGameSolverConfig.storageDestination;
        originDimension = mc.theWorld.provider.dimensionId;
        originX = mc.thePlayer.posX;
        originY = mc.thePlayer.posY;
        originZ = mc.thePlayer.posZ;
        ticks = 0;
        warpConfirmed = false;
        mc.thePlayer.sendChatMessage("/setwarp " + StorageWarpFeedback.RETURN_WARP);
        return true;
    }

    void onChat(String key, String text, String expected) {
        if (state.phase() == StorageTripState.Phase.WAIT_RETURN_WARP
            && StorageWarpFeedback.isReturnWarpConfirmation(key, text, expected)) warpConfirmed = true;
    }

    void tick(Minecraft mc) {
        StorageTripState.Phase phase = state.phase();
        if (phase == StorageTripState.Phase.IDLE || phase == StorageTripState.Phase.FAILED
            || phase == StorageTripState.Phase.READY_TO_RESUME) return;
        if (++ticks > COMMAND_TIMEOUT) {
            state.fail();
            return;
        }
        if (mc == null || mc.theWorld == null || mc.thePlayer == null) return;
        if (!world.equals(Explorer.worldIdentity(mc)) || !LootGameSolverConfig.storageEnabled) {
            state.fail();
            return;
        }
        if (phase == StorageTripState.Phase.WAIT_RETURN_WARP && warpConfirmed) {
            state.returnWarpResult(true);
            ticks = 0;
            mc.thePlayer.sendChatMessage(destination);
        } else if (phase == StorageTripState.Phase.WAIT_STORAGE && nearMarkedTarget(mc)) {
            state.storageArrival(true);
            ticks = 0;
        } else if (phase == StorageTripState.Phase.WAIT_RETURN && StorageTripPolicy.near(
            mc.theWorld.provider.dimensionId,
            mc.thePlayer.posX,
            mc.thePlayer.posY,
            mc.thePlayer.posZ,
            originDimension,
            originX,
            originY,
            originZ,
            5D)) {
                state.returnArrival(true);
            }
    }

    private boolean nearMarkedTarget(Minecraft mc) {
        for (String encoded : LootGameSolverConfig.storageTargets) {
            StorageTarget target = StorageTarget.parse(encoded);
            if (target == null) continue;
            double[] position = target.loadedPosition(mc);
            if (position != null && StorageTripPolicy.near(
                mc.theWorld.provider.dimensionId,
                mc.thePlayer.posX,
                mc.thePlayer.posY,
                mc.thePlayer.posZ,
                target.dimension,
                position[0],
                position[1],
                position[2],
                8D)) return true;
        }
        return false;
    }

    void depositProgress() {
        if (state.phase() == StorageTripState.Phase.DEPOSITING) ticks = 0;
    }

    void depositFinished(Minecraft mc, boolean confirmed, boolean bagsEmpty, boolean itemsStored) {
        if (state.phase() != StorageTripState.Phase.DEPOSITING || mc == null || mc.thePlayer == null) return;
        state.depositResult(confirmed, bagsEmpty, itemsStored);
        if (state.phase() == StorageTripState.Phase.WAIT_RETURN) {
            ticks = 0;
            mc.thePlayer.sendChatMessage("/warp " + StorageWarpFeedback.RETURN_WARP);
        }
    }
}
