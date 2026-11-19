package com.kyroxova.lootgamesolver.client.ui;

import com.kyroxova.lootgamesolver.client.ClientHooks;
import com.kyroxova.lootgamesolver.client.Keybinds;
import com.kyroxova.lootgamesolver.client.puzzle.AutoPuzzleCoordinator;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;

public final class ClientHudOverlay {

    public static final ResourceLocation TEXTURE_UI_9SLICE = new ResourceLocation(
        "lootgamesolver",
        "textures/gui/ui_9slice.png");

    private ClientHudOverlay() {}

    public static void render(ClientHooks hooks, RenderGameOverlayEvent.Text event) {
        if (!LootGameSolverConfig.enabled) return;
        if (!LootGameSolverConfig.showOverlay && !hooks.session.isActive()
            && !hooks.rewardReturnPending
            && (hooks.exploration == null || !hooks.exploration.navigating())
            && !hooks.wasInsideRoom
            && !hooks.autoPuzzle.isActive()
            && !hooks.keyHandler.exploreKeyHeld
            && hooks.checkpointClearDoneTicks == 0) return;
        DetectedGame cur = hooks.session.getCurrentDetectedGame();
        if (cur == null && (hooks.exploration == null || !LootGameSolverConfig.explorationEnabled)) return;

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
            if (hooks.automationPaused) {
                statusStr = "Paused";
            } else if (hooks.session.isActive()) {
                statusStr = StatusText.solver(hooks.session.getStatus());
            } else if (hooks.exploration != null) {
                statusStr = (hooks.exploration.navigating() || hooks.rewardReturnPending
                    || hooks.autoPuzzle.isActive()
                    || hooks.autoPuzzle.getState() == AutoPuzzleCoordinator.State.FAILED) ? hooks.exploration.navigationStatus()
                        : "Ready";
            } else {
                statusStr = "Ready";
            }

            boolean autoActive = !hooks.automationPaused && hooks.session.isActive();
            boolean holoActive = LootGameSolverConfig.showOverlay;
            boolean explorerActive = hooks.exploration != null && ClientHooks.explorerHudActive(
                hooks.exploration.navigating(),
                hooks.rewardReturnPending,
                hooks.explorationDriven,
                hooks.session.isActive(),
                hooks.autoPuzzle.isActive(),
                hooks.storageRecovery != null);

            String autoColor = hooks.automationPaused ? "\u00A7f" : autoActive ? "\u00A7c" : "\u00A77";
            String holoColor = holoActive ? "\u00A7e" : "\u00A77";

            long totalSeconds = hooks.totalActiveTicks / 20L;
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            long seconds = totalSeconds % 60L;
            String timerStr = String.format("%02d:%02d:%02d", hours, minutes, seconds);
            String line1 = "\u00A7b\u00A7lLoot Game Solver \u00A77[ \u00A7d" + timerStr
                + " \u00A77] [ \u00A76"
                + hooks.solvedPuzzles
                + " \u00A77]";
            int dist = hooks.rewardReturnPending ? hooks.rewardLooter.chestDistance(mc)
                : hooks.exploration != null ? hooks.exploration.checkpointDistance(mc) : 0;
            String distBadge = (explorerActive && dist > 0) ? " \u00A77[ \u00A7f" + dist + "m \u00A77]" : "";
            String line2 = (explorerActive ? "\u00A7a\u00A7l" : "\u00A7f") + gameTitle
                + " \u00A78|\u00A7"
                + (hooks.session.isActive() || explorerActive ? "a" : "e")
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
            if (hooks.exploration != null && LootGameSolverConfig.explorationEnabled) {
                String label = " [" + GameSettings.getKeyDisplayString(Keybinds.explore.getKeyCode())
                    + "] "
                    + hooks.exploration.explorationAction();
                int held = hooks.checkpointClearDoneTicks > 0 ? 20 : hooks.keyHandler.exploreKeyHeld ? hooks.keyHandler.exploreHoldTicks : 0;
                line3 += " \u00A78|"
                    + ClientHooks.explorerKeyLabel(label, !hooks.automationPaused && explorerActive, held, hooks.automationPaused);
            }
            int width1 = mc.fontRenderer.getStringWidth(line1);
            int width2 = mc.fontRenderer.getStringWidth(line2);
            int width3 = mc.fontRenderer.getStringWidth(line3);
            int maxWidth = Math.max(width1, Math.max(width2, width3));

            int panelWidth = Math.max(170, maxWidth + 40);
            int panelHeight = 44;
            int x = resolution.getScaledWidth() - panelWidth - 15;
            int y = resolution.getScaledHeight() - panelHeight - 55;

            drawNineSlice(TEXTURE_UI_9SLICE, x, y, panelWidth, panelHeight, mc);

            mc.fontRenderer.drawStringWithShadow(line1, x + (panelWidth - width1) / 2, y + 7, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line2, x + (panelWidth - width2) / 2, y + 17, 0xFFFFFF);
            mc.fontRenderer.drawStringWithShadow(line3, x + (panelWidth - width3) / 2, y + 27, 0xFFFFFF);
        } finally {
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    public static void drawNineSlice(ResourceLocation texture, int x, int y, int width, int height, Minecraft mc) {
        if (texture == null || mc == null || mc.getTextureManager() == null) return;
        mc.getTextureManager().bindTexture(texture);

        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        float t = 32.0F;

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
        tessellator.addVertexWithUV((double) x, (double) (y + height), 0.0D, (double) (u * f), (double) ((v + vHeight) * f1));
        tessellator.addVertexWithUV((double) (x + width), (double) (y + height), 0.0D, (double) ((u + uWidth) * f), (double) ((v + vHeight) * f1));
        tessellator.addVertexWithUV((double) (x + width), (double) y, 0.0D, (double) ((u + uWidth) * f), (double) (v * f1));
        tessellator.addVertexWithUV((double) x, (double) y, 0.0D, (double) (u * f), (double) (v * f1));
        tessellator.draw();
    }
}
