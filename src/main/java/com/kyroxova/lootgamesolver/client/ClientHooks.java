package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

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
        mc.thePlayer.rotationYaw = updateRotation(mc.thePlayer.rotationYaw, targetYaw, 40.0F);
        mc.thePlayer.rotationPitch = updateRotation(mc.thePlayer.rotationPitch, targetPitch, 30.0F);

        // Step towards target block if > 2.8 blocks away
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
        DetectedGame cur = session.getCurrentDetectedGame();
        if (cur == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        String autoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleAutoSolve.getKeyCode());
        String holoKeyStr = GameSettings.getKeyDisplayString(Keybinds.toggleOverlay.getKeyCode());

        String gameTitle = cur.type == com.kyroxova.lootgamesolver.core.MiniGame.MINESWEEPER ? "Minesweeper"
            : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.SUDOKU ? "Sudoku"
                : cur.type == com.kyroxova.lootgamesolver.core.MiniGame.GAME_OF_LIGHT ? "G O L" : "LootGames";

        String statusStr = session.isActive() ? session.getStatus() : "Ready";

        boolean autoActive = session.isActive();
        boolean holoActive = LootGameSolverConfig.showOverlay;

        String autoColor = autoActive ? "\u00A7c" : "\u00A77";
        String holoColor = holoActive ? "\u00A7e" : "\u00A77";

        String line1 = "\u00A7b\u00A7lLoot Game Solver";
        String line2 = "\u00A7f" + gameTitle + " \u00A78|\u00A7" + (session.isActive() ? "a" : "e") + " " + statusStr;
        String line3 = autoColor + "["
            + autoKeyStr
            + "] Auto \u00A78|\u00A77 "
            + holoColor
            + "["
            + holoKeyStr
            + "] Holograms";

        int width1 = mc.fontRenderer.getStringWidth("Loot Game Solver");
        int width2 = mc.fontRenderer.getStringWidth(gameTitle + " | " + statusStr);
        int width3 = mc.fontRenderer.getStringWidth("[" + autoKeyStr + "] Auto | [" + holoKeyStr + "] Holograms");
        int maxWidth = Math.max(width1, Math.max(width2, width3));

        int panelWidth = maxWidth + 32;
        int panelHeight = 44;
        int x = resolution.getScaledWidth() - panelWidth - 15;
        int y = resolution.getScaledHeight() - panelHeight - 55;

        drawNineSlice(TEXTURE_UI_9SLICE, x, y, panelWidth, panelHeight, mc);

        mc.fontRenderer.drawStringWithShadow(line1, x + (panelWidth - width1) / 2, y + 7, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line2, x + (panelWidth - width2) / 2, y + 17, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line3, x + (panelWidth - width3) / 2, y + 27, 0xFFFFFF);
    }

    private static final ResourceLocation TEXTURE_UI_9SLICE = new ResourceLocation(
        "lootgamesolver",
        "textures/gui/ui_9slice.png");

    public static void drawNineSlice(ResourceLocation texture, int x, int y, int width, int height, Minecraft mc) {
        if (texture == null || mc == null || mc.getTextureManager() == null) return;
        mc.getTextureManager()
            .bindTexture(texture);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        float t = 32.0F; // 32x32 texture sheet

        // 1. Center Background (Tiled 6x6 from UV 13,13)
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

        // 2. Top Edge (Tiled 6x12 from UV 13,0 between 12x12 corners)
        int topStartX = x + 12;
        int topEndX = x + width - 12;
        for (int curX = topStartX; curX < topEndX; curX += 6) {
            int drawW = Math.min(6, topEndX - curX);
            drawSubTexture(curX, y, 13, 0, drawW, 12, drawW, 12, t, t);
        }

        // 3. Bottom Edge (Tiled 6x12 from UV 13,20 between 12x12 corners)
        int botStartX = x + 12;
        int botEndX = x + width - 12;
        for (int curX = botStartX; curX < botEndX; curX += 6) {
            int drawW = Math.min(6, botEndX - curX);
            drawSubTexture(curX, y + height - 12, 13, 20, drawW, 12, drawW, 12, t, t);
        }

        // 4. Left Edge (Tiled 12x6 from UV 0,13 between 12x12 corners)
        int leftStartY = y + 12;
        int leftEndY = y + height - 12;
        for (int curY = leftStartY; curY < leftEndY; curY += 6) {
            int drawH = Math.min(6, leftEndY - curY);
            drawSubTexture(x, curY, 0, 13, 12, drawH, 12, drawH, t, t);
        }

        // 5. Right Edge (Tiled 12x6 from UV 20,13 between 12x12 corners)
        int rightStartY = y + 12;
        int rightEndY = y + height - 12;
        for (int curY = rightStartY; curY < rightEndY; curY += 6) {
            int drawH = Math.min(6, rightEndY - curY);
            drawSubTexture(x + width - 12, curY, 20, 13, 12, drawH, 12, drawH, t, t);
        }

        // 6. 4 Corners (12x12 un-stretched at 1:1 pixel scale)
        // Top-Left Corner (UV 0,0 12x12)
        drawSubTexture(x, y, 0, 0, 12, 12, 12, 12, t, t);

        // Top-Right Corner (UV 20,0 12x12)
        drawSubTexture(x + width - 12, y, 20, 0, 12, 12, 12, 12, t, t);

        // Bottom-Left Corner (UV 0,20 12x12)
        drawSubTexture(x, y + height - 12, 0, 20, 12, 12, 12, 12, t, t);

        // Bottom-Right Corner (UV 20,20 12x12)
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
}
