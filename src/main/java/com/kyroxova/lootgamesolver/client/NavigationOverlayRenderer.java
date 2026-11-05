package com.kyroxova.lootgamesolver.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;

import org.lwjgl.opengl.GL11;

/** Draws cached decisions only; rendering never runs world scans or route searches. */
final class NavigationOverlayRenderer {

    static void render(ExplorationBeta exploration, float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        int[] waypoint = exploration.structureWaypoint();
        if (!exploration.navigating() && waypoint == null) return;
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
                for (int[] blocked : exploration.rejectedPreview()) {
                    t.setColorRGBA_F(1F, 0.15F, 0.1F, 0.5F);
                    t.addVertex(blocked[3] + 0.5D, blocked[4] + 0.15D, blocked[5] + 0.5D);
                    t.addVertex(blocked[0] + 0.5D, blocked[1] + 0.15D, blocked[2] + 0.5D);
                }
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
                    mc);
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private static void drawWaypointBeam(Tessellator t, double x, double z, double ground, double top, double time) {
        double height = top - ground;
        double phase = time * 0.035D;
        // Translucent cylindrical shell makes the marker readable at a distance and from every angle.
        t.startDrawing(GL11.GL_QUADS);
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2D * i / 16D, a1 = Math.PI * 2D * (i + 1) / 16D;
            t.setColorRGBA_F(0.12F, 0.5F, 1F, 0.12F);
            t.addVertex(x + Math.cos(a0) * 0.24D, ground, z + Math.sin(a0) * 0.24D);
            t.addVertex(x + Math.cos(a1) * 0.24D, ground, z + Math.sin(a1) * 0.24D);
            t.addVertex(x + Math.cos(a1) * 0.24D, top, z + Math.sin(a1) * 0.24D);
            t.addVertex(x + Math.cos(a0) * 0.24D, top, z + Math.sin(a0) * 0.24D);
        }
        t.draw();

        // Horizontal rings and sparkles rise continuously through the beam.
        t.startDrawing(GL11.GL_LINE_LOOP);
        for (int ring = 0; ring < 12; ring++) {
            double progress = (ring / 12D + time * 0.008D) % 1D;
            double y = ground + progress * height;
            float alpha = (float) (0.25D + 0.45D * Math.sin(progress * Math.PI));
            t.setColorRGBA_F(0.34F, 0.78F, 1F, alpha);
            for (int i = 0; i < 20; i++) {
                double angle = Math.PI * 2D * i / 20D + phase;
                t.addVertex(x + Math.cos(angle) * 0.25D, y, z + Math.sin(angle) * 0.25D);
            }
            // Tessellator line loops must be drawn separately; close this ring with its first point.
            t.addVertex(x + Math.cos(phase) * 0.25D, y, z + Math.sin(phase) * 0.25D);
            t.draw();
            if (ring != 11) t.startDrawing(GL11.GL_LINE_LOOP);
        }
        GL11.glPointSize(5F);
        t.startDrawing(GL11.GL_POINTS);
        for (int i = 0; i < 40; i++) {
            double progress = (i / 40D + time * 0.014D) % 1D;
            double angle = i * 2.399963D + phase * 1.8D;
            float alpha = (float) (0.55D + 0.4D * (0.5D + 0.5D * Math.sin(time * 0.12D + i)));
            t.setColorRGBA_F(0.38F, 0.82F, 1F, alpha);
            t.addVertex(x + Math.cos(angle) * 0.2D, ground + progress * height, z + Math.sin(angle) * 0.2D);
        }
        t.draw();
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
