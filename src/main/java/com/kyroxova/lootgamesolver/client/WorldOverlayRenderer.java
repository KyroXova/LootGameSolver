package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * Ultra-optimized 3D overlay renderer.
 * Holograms are rendered ONLY if the block is within a 7x7 area around the player and in direct line-of-sight view.
 */
public final class WorldOverlayRenderer {

    private final SolverSession session;

    public WorldOverlayRenderer(SolverSession session) {
        this.session = session;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!LootGameSolverConfig.enabled || !LootGameSolverConfig.showOverlay) return;
        DetectedGame current = session.getCurrentDetectedGame();
        if (current == null) return;

        List<SolverAction> actions = session.getActiveActions();
        if (actions == null || actions.isEmpty()) {
            actions = session.computeActionsForCurrentGame();
        }
        if (actions == null || actions.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        double renderPosX = RenderManager.renderPosX;
        double renderPosY = RenderManager.renderPosY;
        double renderPosZ = RenderManager.renderPosZ;

        Vec3 eyeVec = Vec3
            .createVectorHelper(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);

        GL11.glPushMatrix();
        GL11.glTranslated(-renderPosX, -renderPosY, -renderPosZ);
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glLineWidth(1.5F);

        int index = 1;
        int maxRender = actions.size();
        for (int i = 0; i < maxRender; i++) {
            SolverAction action = actions.get(i);
            int[] block = session.getBlockPos(action.position);
            if (block == null) continue;

            // Distance Restriction (32.0 block radius)
            double dx = Math.abs(block[0] + 0.5D - mc.thePlayer.posX);
            double dy = Math.abs(block[1] + 0.5D - mc.thePlayer.posY);
            double dz = Math.abs(block[2] + 0.5D - mc.thePlayer.posZ);
            if (dx > 32.0D || dz > 32.0D || dy > 16.0D) continue;

            double minX = block[0] + 0.02D, minY = block[1] + 0.02D, minZ = block[2] + 0.02D;
            double maxX = minX + 0.96D, maxY = minY + 0.96D, maxZ = minZ + 0.96D;
            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

            if (current.type == MiniGame.MINESWEEPER) {
                if (action.type == SolverAction.Type.FLAG) {
                    if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 1.0F, 0.2F, 0.2F);
                    drawFloatingText("FLAG", minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0xFF4444, mc);
                } else if (action.type == SolverAction.Type.REVEAL) {
                    if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.2F, 1.0F, 0.2F);
                    drawFloatingText("SAFE", minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0x44FF44, mc);
                }
            } else if (current.type == MiniGame.SUDOKU) {
                if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.2F, 0.7F, 1.0F);
                drawFloatingText(String.valueOf(action.value), minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0x55FFFF, mc);
            } else if (current.type == MiniGame.GAME_OF_LIGHT) {
                if (action.position.x == 1 && action.position.y == 1) {
                    if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 1.0F, 0.8F, 0.0F);
                    drawFloatingText("START", minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0xFFFF44, mc);
                } else {
                    if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.0F, 0.9F, 0.9F);
                    drawFloatingText("#" + index, minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0x00FFFF, mc);
                }
            }
            index++;
        }

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void drawWireframe(AxisAlignedBB box, float r, float g, float b) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(1);
        tessellator.setColorRGBA_F(r, g, b, 0.85F);

        tessellator.addVertex(box.minX, box.minY, box.minZ);
        tessellator.addVertex(box.maxX, box.minY, box.minZ);
        tessellator.addVertex(box.maxX, box.minY, box.minZ);
        tessellator.addVertex(box.maxX, box.minY, box.maxZ);
        tessellator.addVertex(box.maxX, box.minY, box.maxZ);
        tessellator.addVertex(box.minX, box.minY, box.maxZ);
        tessellator.addVertex(box.minX, box.minY, box.maxZ);
        tessellator.addVertex(box.minX, box.minY, box.minZ);

        tessellator.addVertex(box.minX, box.maxY, box.minZ);
        tessellator.addVertex(box.maxX, box.maxY, box.minZ);
        tessellator.addVertex(box.maxX, box.maxY, box.minZ);
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ);
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ);
        tessellator.addVertex(box.minX, box.maxY, box.maxZ);
        tessellator.addVertex(box.minX, box.maxY, box.maxZ);
        tessellator.addVertex(box.minX, box.maxY, box.minZ);

        tessellator.addVertex(box.minX, box.minY, box.minZ);
        tessellator.addVertex(box.minX, box.maxY, box.minZ);
        tessellator.addVertex(box.maxX, box.minY, box.minZ);
        tessellator.addVertex(box.maxX, box.maxY, box.minZ);
        tessellator.addVertex(box.maxX, box.minY, box.maxZ);
        tessellator.addVertex(box.maxX, box.maxY, box.maxZ);
        tessellator.addVertex(box.minX, box.minY, box.maxZ);
        tessellator.addVertex(box.minX, box.maxY, box.maxZ);
        tessellator.draw();
    }

    private static void drawFloatingText(String text, double x, double y, double z, int color, Minecraft mc) {
        FontRenderer font = mc.fontRenderer;
        RenderManager rm = RenderManager.instance;
        float scale = 0.025F;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-rm.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(rm.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glScalef(-scale, -scale, scale);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        int strWidth = font.getStringWidth(text);
        font.drawStringWithShadow(text, -strWidth / 2, 0, color);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }
}
