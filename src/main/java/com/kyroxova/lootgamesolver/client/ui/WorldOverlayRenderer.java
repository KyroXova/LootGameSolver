package com.kyroxova.lootgamesolver.client.ui;

import com.kyroxova.lootgamesolver.client.ExplorationBeta;
import com.kyroxova.lootgamesolver.client.puzzle.SolverSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.core.CellPosition;
import com.kyroxova.lootgamesolver.core.MiniGame;
import com.kyroxova.lootgamesolver.core.SolverAction;
import com.kyroxova.lootgamesolver.explorer.Explorer;
import com.kyroxova.lootgamesolver.minecraft.DetectedGame;
import com.kyroxova.lootgamesolver.solver.gol.GameOfLightBoard;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class WorldOverlayRenderer {

    private static final ResourceLocation RED_FLAG = new ResourceLocation(
        "lootgamesolver",
        "textures/minesweeper/red_flag.png");
    private static final ResourceLocation SAFE_FLAG = new ResourceLocation(
        "lootgamesolver",
        "textures/minesweeper/safe_flag.png");

    private final SolverSession session;
    private final Explorer exploration;

    public static Map<CellPosition, String> sequenceLabels(List<SolverAction> actions, int completed) {
        Map<CellPosition, String> labels = new LinkedHashMap<CellPosition, String>();
        for (int i = 0; i < actions.size(); i++) {
            CellPosition position = actions.get(i).position;
            String label = labels.get(position);
            labels.put(position, (label == null ? "" : label + ", ") + "#" + (completed + i + 1));
        }
        return labels;
    }

    public WorldOverlayRenderer(SolverSession session, Explorer exploration) {
        this.session = session;
        this.exploration = exploration;
    }

    @Deprecated
    public WorldOverlayRenderer(SolverSession session, ExplorationBeta exploration) {
        this(session, (Explorer) exploration);
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

    public static void drawFloatingText(String text, double x, double y, double z, int color, Minecraft mc) {
        drawFloatingText(text, x, y, z, color, mc, 0.025F);
    }

    public static void drawFloatingText(String text, double x, double y, double z, int color, Minecraft mc, float scale) {
        FontRenderer font = mc.fontRenderer;
        RenderManager rm = RenderManager.instance;

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

    private static void drawFlag(ResourceLocation texture, double x, double y, double z, Minecraft mc) {
        RenderManager manager = RenderManager.instance;
        mc.getTextureManager()
            .bindTexture(texture);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glRotatef(-manager.playerViewY, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(manager.playerViewX, 1.0F, 0.0F, 0.0F);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(-0.36D, -0.36D, 0D, 0D, 1D);
        tessellator.addVertexWithUV(0.36D, -0.36D, 0D, 1D, 1D);
        tessellator.addVertexWithUV(0.36D, 0.36D, 0D, 1D, 0D);
        tessellator.addVertexWithUV(-0.36D, 0.36D, 0D, 0D, 0D);
        tessellator.draw();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glPopMatrix();
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!LootGameSolverConfig.enabled) return;
        if (LootGameSolverConfig.showOverlay || (exploration != null && exploration.navigating()))
            renderStorageTargets();
        if (!LootGameSolverConfig.showOverlay) return;
        boolean solving = session != null && session.isActive();
        if (exploration != null && !solving) NavigationOverlayRenderer.render(exploration, event.partialTicks);
        DetectedGame current = session.getCurrentDetectedGame();
        if (current == null) return;

        List<SolverAction> actions = session.getActiveActions();
        if (actions == null || actions.isEmpty()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.theWorld == null) return;

        double renderPosX = RenderManager.renderPosX;
        double renderPosY = RenderManager.renderPosY;
        double renderPosZ = RenderManager.renderPosZ;

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-renderPosX, -renderPosY, -renderPosZ);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glLineWidth(1.5F);

            SolverAction firstAction = actions.get(0);
            int[] firstBlock = session.getBlockPos(firstAction.position);
            if (!solving && firstBlock != null) {
                double fdx = firstBlock[0] + 0.5D - mc.thePlayer.posX;
                double fdy = firstBlock[1] + 0.5D - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
                double fdz = firstBlock[2] + 0.5D - mc.thePlayer.posZ;
                if (fdx * fdx + fdy * fdy + fdz * fdz > 7.84D) {
                    GL11.glLineWidth(2.5F);
                    Tessellator t = Tessellator.instance;
                    t.startDrawing(GL11.GL_LINES);
                    t.setColorRGBA_F(0.2F, 0.9F, 1.0F, 0.85F);
                    t.addVertex(mc.thePlayer.posX, mc.thePlayer.boundingBox.minY + 0.15D, mc.thePlayer.posZ);
                    t.addVertex(firstBlock[0] + 0.5D, firstBlock[1] + 0.15D, firstBlock[2] + 0.5D);
                    t.draw();
                }
            }

            int maxRender = actions.size();
            Map<CellPosition, String> lightLabels = current.type == MiniGame.GAME_OF_LIGHT
                ? sequenceLabels(actions, current.gol == null ? 0 : current.gol.getCurrentSymbol())
                : null;
            for (int i = 0; i < maxRender; i++) {
                SolverAction action = actions.get(i);
                if (lightLabels != null && !lightLabels.containsKey(action.position)) continue;
                int[] block = session.getBlockPos(action.position);
                if (block == null) continue;

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
                        drawFlag(RED_FLAG, minX + 0.5D, minY + 1.1D, minZ + 0.5D, mc);
                    } else if (action.type == SolverAction.Type.REVEAL) {
                        if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.2F, 1.0F, 0.2F);
                        drawFlag(SAFE_FLAG, minX + 0.5D, minY + 1.1D, minZ + 0.5D, mc);
                    }
                } else if (current.type == MiniGame.SUDOKU) {
                    if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.2F, 0.7F, 1.0F);
                    drawFloatingText(
                        String.valueOf(action.value),
                        minX + 0.5D,
                        minY + 1.25D,
                        minZ + 0.5D,
                        0x55FFFF,
                        mc);
                } else if (current.type == MiniGame.GAME_OF_LIGHT) {
                    if (current.gol != null && GameOfLightBoard.STAGE_WAITING_START.equals(current.gol.getStageId())) {
                        if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 1.0F, 0.8F, 0.0F);
                        drawFloatingText("START", minX + 0.5D, minY + 1.25D, minZ + 0.5D, 0xFFFF44, mc);
                    } else {
                        if (LootGameSolverConfig.renderStyle == 0) drawWireframe(box, 0.0F, 0.9F, 0.9F);
                        drawFloatingText(
                            lightLabels.get(action.position),
                            minX + 0.5D,
                            minY + 1.25D,
                            minZ + 0.5D,
                            0x00FFFF,
                            mc);
                    }
                    lightLabels.remove(action.position);
                }
            }
        } finally {
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void renderStorageTargets() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null
            || !Explorer.worldIdentity(mc)
                .equals(LootGameSolverConfig.storageWorld))
            return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDepthMask(false);
            GL11.glLineWidth(2F);
            for (String target : LootGameSolverConfig.storageTargets) {
                String[] fields = target.split(":");
                try {
                    if (fields.length < 3 || Integer.parseInt(fields[1]) != mc.theWorld.provider.dimensionId) continue;
                    AxisAlignedBB box = null;
                    if ("block".equals(fields[0]) && fields.length == 5) {
                        int x = Integer.parseInt(fields[2]), y = Integer.parseInt(fields[3]),
                            z = Integer.parseInt(fields[4]);
                        if (!mc.theWorld.blockExists(x, y, z)
                            || !(mc.theWorld.getTileEntity(x, y, z) instanceof net.minecraft.inventory.IInventory))
                            continue;
                        if (mc.thePlayer.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) > 4096D) continue;
                        box = AxisAlignedBB.getBoundingBox(x, y, z, x + 1, y + 1, z + 1);
                    } else if ("entity".equals(fields[0]) && fields.length == 3) {
                        java.util.UUID id = java.util.UUID.fromString(fields[2]);
                        for (Object value : mc.theWorld.loadedEntityList) {
                            if (value instanceof net.minecraft.entity.Entity) {
                                net.minecraft.entity.Entity entity = (net.minecraft.entity.Entity) value;
                                if (entity instanceof net.minecraft.inventory.IInventory
                                    && id.equals(entity.getUniqueID())
                                    && mc.thePlayer.getDistanceSqToEntity(entity) <= 4096D) {
                                    box = entity.boundingBox.copy();
                                    break;
                                }
                            }
                        }
                    }
                    if (box != null) drawWireframe(box.expand(0.01D, 0.01D, 0.01D), 0.2F, 1F, 0.2F);
                } catch (IllegalArgumentException ignored) {}
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }
}
