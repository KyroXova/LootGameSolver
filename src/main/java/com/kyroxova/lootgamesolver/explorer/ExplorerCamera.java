package com.kyroxova.lootgamesolver.explorer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

final class ExplorerCamera {

    private boolean active;
    private float targetYaw, targetPitch;
    private long lastFrame;
    private long lastUserMouseTime;
    private boolean force;

    void render(Minecraft mc, World world, boolean walking) {
        long nowNano = System.nanoTime();
        double seconds = lastFrame == 0 ? 1D / 60D : (nowNano - lastFrame) / 1_000_000_000D;
        lastFrame = nowNano;
        if (!active || mc.thePlayer == null || mc.theWorld != world || mc.currentScreen != null || mc.isGamePaused())
            return;

        long nowMs = System.currentTimeMillis();
        if (mc.mouseHelper != null && (mc.mouseHelper.deltaX != 0 || mc.mouseHelper.deltaY != 0)) {
            lastUserMouseTime = nowMs;
        }

        // Keep mouse aim unlocked to player use if mouse was moved within the last 10 seconds (only for walking
        // steering)
        if (!force && nowMs - lastUserMouseTime < 10_000L) {
            return;
        }

        float yaw = NavigationSteering.cameraStep(mc.thePlayer.rotationYaw, targetYaw, seconds);
        float pitch = NavigationSteering.cameraStep(mc.thePlayer.rotationPitch, targetPitch, seconds);
        mc.thePlayer.setAngles(yaw / 0.15F, -pitch / 0.15F);
    }

    void look(float yaw, float pitch) {
        look(yaw, pitch, false);
    }

    void look(float yaw, float pitch, boolean force) {
        targetYaw = yaw;
        targetPitch = pitch;
        this.force = force;
        active = true;
    }

    void clear() {
        active = false;
        force = false;
    }

    void reset() {
        active = false;
        force = false;
        lastFrame = 0;
        lastUserMouseTime = 0;
    }
}
