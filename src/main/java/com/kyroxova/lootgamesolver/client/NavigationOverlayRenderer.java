package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

/** Draws cached decisions only; rendering never runs world scans or route searches. */
final class NavigationOverlayRenderer {

    static void render(ExplorationBeta exploration) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!exploration.navigating() || mc.theWorld == null || mc.thePlayer == null) return;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(-RenderManager.renderPosX, -RenderManager.renderPosY, -RenderManager.renderPosZ);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glLineWidth(2F);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINES);
            double[] start = { mc.thePlayer.posX, mc.thePlayer.boundingBox.minY + 0.15D, mc.thePlayer.posZ };
            double[] end = draw(t, exploration.navigationRoute(), start, false);
            draw(t, exploration.excavationPreview(), end, true);
            for (int[] blocked : exploration.rejectedPreview()) {
                t.setColorRGBA_F(1F, 0.15F, 0.1F, 0.5F);
                t.addVertex(blocked[3] + 0.5D, blocked[4] + 0.15D, blocked[5] + 0.5D);
                t.addVertex(blocked[0] + 0.5D, blocked[1] + 0.15D, blocked[2] + 0.5D);
            }
            t.draw();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static double[] draw(Tessellator t, List<int[]> route, double[] start, boolean excavation) {
        double x = start[0], y = start[1], z = start[2];
        for (int i = 0; i < Math.min(96, route.size()); i++) {
            int[] step = route.get(i);
            boolean caution = excavation || (step.length > 3 && step[3] != 0) || Math.abs(step[1] + 0.15D - y) > 0.2D;
            t.setColorRGBA_F(caution ? 1F : 0.15F, 0.9F, 0.15F, 0.8F);
            t.addVertex(x, y, z);
            x = step[0] + 0.5D;
            y = step[1] + 0.15D;
            z = step[2] + 0.5D;
            t.addVertex(x, y, z);
        }
        return new double[] { x, y, z };
    }
}
