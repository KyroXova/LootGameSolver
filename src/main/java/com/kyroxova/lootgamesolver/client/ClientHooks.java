package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.input.Keyboard;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.ActionPlanner;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;

public final class ClientHooks {

    private final SolverSession session = new SolverSession();
    private final WorldOverlayRenderer worldRenderer = new WorldOverlayRenderer(session);

    private boolean lastAutoSolveKey = false;
    private boolean lastOverlayKey = false;
    private int detectCounter = 0;

    public WorldOverlayRenderer getWorldRenderer() {
        return worldRenderer;
    }

    @SubscribeEvent
    public void onKeyInput(cpw.mods.fml.common.gameevent.InputEvent.KeyInputEvent event) {
        checkKeyToggles();
    }

    @SubscribeEvent
    public void clientTick(ClientTickEvent event) {
        if (event.phase != cpw.mods.fml.common.gameevent.TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        // Poll board detection every 10 ticks when dormant so HUD is ALWAYS shown in structure
        detectCounter++;
        if (session.isActive() || detectCounter % 10 == 0) {
            session.tick();
        }

        if (session.isActive()) {
            handleAutoMovement(mc);
        }
    }

    private void checkKeyToggles() {
        boolean autoSolvePressed = isKeyJustPressed(Keybinds.toggleAutoSolve, LootGameSolverConfig.solveKey);
        boolean overlayPressed = isKeyJustPressed(Keybinds.toggleOverlay, LootGameSolverConfig.overlayKey);

        if (autoSolvePressed) {
            if (session.isActive()) {
                session.cancel("Auto-Solver Stopped");
            } else {
                session.start(ActionPlanner.Mode.SOLVE);
            }
        }

        if (overlayPressed) {
            LootGameSolverConfig.showOverlay = !LootGameSolverConfig.showOverlay;
        }
    }

    private boolean isKeyJustPressed(KeyBinding binding, int fallbackCode) {
        if (binding != null && binding.isPressed()) return true;
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

        // Smooth humanized camera rotation to face target block
        float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float targetPitch = (float) (-(Math.atan2(dy, distXZ) * 180.0D / Math.PI));
        mc.thePlayer.rotationYaw = updateRotation(mc.thePlayer.rotationYaw, targetYaw, 35.0F);
        mc.thePlayer.rotationPitch = updateRotation(mc.thePlayer.rotationPitch, targetPitch, 25.0F);

        // Step towards target block if > 2.8 blocks away
        if (distSq > 8.0D) {
            double speed = 0.20D;
            if (distXZ > 0.1D) {
                mc.thePlayer.motionX += (dx / distXZ) * speed;
                mc.thePlayer.motionZ += (dz / distXZ) * speed;
            }
            if (dy > 1.0D) {
                if (mc.thePlayer.capabilities.allowFlying) {
                    mc.thePlayer.capabilities.isFlying = true;
                    mc.thePlayer.motionY = 0.15D;
                } else if (mc.thePlayer.onGround) {
                    mc.thePlayer.jump();
                }
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
        DetectedGame cur = session.getCurrentDetectedGame();
        if (cur == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        String autoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleAutoSolve.getKeyCode());
        String holoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleOverlay.getKeyCode());

        String gameTitle = cur.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER ? "Minesweeper"
            : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU ? "Sudoku"
                : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT ? "Game of Light" : "LootGames";

        String statusStr = session.isActive() ? session.getStatus() : "Ready";

        String line1 = "\u00A7b\u00A7lLoot Game Solver";
        String line2 = "\u00A7f" + gameTitle + " \u00A78|\u00A7" + (session.isActive() ? "a" : "e") + " " + statusStr;
        String line3 = "\u00A77[" + autoKeyStr + "] Auto-Solve \u00A78|\u00A77 [" + holoKeyStr + "] Holograms";

        int width1 = mc.fontRenderer.getStringWidth("Loot Game Solver");
        int width2 = mc.fontRenderer.getStringWidth(gameTitle + " | " + statusStr);
        int width3 = mc.fontRenderer.getStringWidth("[" + autoKeyStr + "] Auto-Solve | [" + holoKeyStr + "] Holograms");
        int maxWidth = Math.max(width1, Math.max(width2, width3));

        int panelWidth = maxWidth + 20;
        int panelHeight = 36;
        int x = resolution.getScaledWidth() - panelWidth - 15;
        int y = resolution.getScaledHeight() - panelHeight - 55;

        net.minecraft.client.gui.Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0xDD0F141C);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + panelWidth, y + 1, 0xFF3B4252);
        net.minecraft.client.gui.Gui.drawRect(x, y, x + 1, y + panelHeight, 0xFF3B4252);
        net.minecraft.client.gui.Gui.drawRect(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, 0xFF3B4252);
        net.minecraft.client.gui.Gui.drawRect(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF3B4252);

        mc.fontRenderer.drawStringWithShadow(line1, x + (panelWidth - width1) / 2, y + 4, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line2, x + (panelWidth - width2) / 2, y + 14, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line3, x + (panelWidth - width3) / 2, y + 24, 0xAAAAAA);
    }
}
