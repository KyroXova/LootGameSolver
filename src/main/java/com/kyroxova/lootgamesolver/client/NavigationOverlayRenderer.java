package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

final class NavigationOverlayRenderer {

    static void render(ExplorationBeta exploration, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        int[] waypoint = exploration.structureWaypoint();
        if (!exploration.navigating()) return;
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
            Tessellator t = Tessellator.instance;
            double[] start = { mc.thePlayer.posX, mc.thePlayer.boundingBox.minY + 0.15D, mc.thePlayer.posZ };
            if (waypoint != null) {
                double ground = Math.max(0D, exploration.waypointLabelY() - 3D);
                double top = Math.max(ground + 1D, mc.theWorld.getHeight() - 1D);
                drawWaypointBeam(
                    t,
                    waypoint[0] + 0.5D,
                    waypoint[2] + 0.5D,
                    ground,
                    top,
                    mc.theWorld.getTotalWorldTime() + partialTicks);
            }
            GL11.glLineWidth(2F);
            t.startDrawing(GL11.GL_LINES);
            if (exploration.navigating()) {
                double[] end = draw(t, exploration.navigationRoute(), start, false);
                draw(t, exploration.excavationPreview(), end, true);
            }
            t.draw();
            if (waypoint != null) {
                int distance = (int) Math.ceil(
                    Math.sqrt(mc.thePlayer.getDistanceSq(waypoint[0] + 0.5D, waypoint[1] + 0.5D, waypoint[2] + 0.5D)));
                WorldOverlayRenderer.drawFloatingText(
                    "LootGame | " + distance + " blocks away",
                    waypoint[0] + 0.5D,
                    exploration.waypointLabelY(),
                    waypoint[2] + 0.5D,
                    0x5599FF,
                    mc,
                    (float) Math.min(0.18D, 0.025D + distance * 0.0005D));
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void drawWaypointBeam(Tessellator t, double x, double z, double ground, double top, double time) {
        double height = top - ground;
        double rotation = time * 0.06D;
        GL11.glLineWidth(4F);
        // Explicit line pairs avoid rendering glitches in legacy 1.7 drivers.
        t.startDrawing(GL11.GL_LINES);
        int segments = Math.max(16, (int) Math.ceil(height / 1.5D) * 16);
        double radius = 0.42D;
        for (int segment = 0; segment < segments; segment++) {
            double progress = segment / (double) segments;
            double nextProgress = (segment + 1D) / segments;
            double angle = progress * height / 1.5D * Math.PI * 2D + rotation;
            double next = nextProgress * height / 1.5D * Math.PI * 2D + rotation;
            float pulse = (float) (0.55D + 0.3D * (0.5D + 0.5D * Math.sin(angle - rotation * 3D)));
            t.setColorRGBA_F(0.28F, 0.72F, 1F, pulse);
            t.addVertex(x + Math.cos(angle) * radius, ground + progress * height, z + Math.sin(angle) * radius);
            t.addVertex(x + Math.cos(next) * radius, ground + nextProgress * height, z + Math.sin(next) * radius);
        }
        t.draw();
        GL11.glLineWidth(2F);
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
