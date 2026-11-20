package com.kyroxova.lootgamesolver.explorer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import com.kyroxova.lootgamesolver.explorer.pathfinder.NavigationSteering;

public final class ExplorerCamera {

    private boolean active;
    private float targetYaw, targetPitch;
    private long lastFrame;
    private long lastUserMouseTime;
    private boolean force;
    private boolean smoothForced;

    public void render(Minecraft mc, World world, boolean walking) {
        long nowNano = System.nanoTime();
        double seconds = lastFrame == 0 ? 1D / 60D : (nowNano - lastFrame) / 1_000_000_000D;
        lastFrame = nowNano;
        if (!active || mc.thePlayer == null || mc.theWorld != world || mc.currentScreen != null || mc.isGamePaused())
            return;

        long nowMs = System.currentTimeMillis();
        if (mc.mouseHelper != null && (mc.mouseHelper.deltaX != 0 || mc.mouseHelper.deltaY != 0)) {
            lastUserMouseTime = nowMs;
        }

        // Keep mouse aim unlocked to player use if mouse was moved within the last second (only for walking
        // steering)
        if (!force && nowMs - lastUserMouseTime < 1_000L) {
            return;
        }

        float yaw = (walking && !force) || smoothForced
            ? NavigationSteering.cameraStep(mc.thePlayer.rotationYaw, targetYaw, seconds)
            : NavigationSteering.interactionCameraStep(mc.thePlayer.rotationYaw, targetYaw, seconds);
        float pitch = (walking && !force) || smoothForced
            ? NavigationSteering.cameraStep(mc.thePlayer.rotationPitch, targetPitch, seconds)
            : NavigationSteering.interactionCameraStep(mc.thePlayer.rotationPitch, targetPitch, seconds);
        mc.thePlayer.setAngles(yaw / 0.15F, -pitch / 0.15F);
    }

    public void look(float yaw, float pitch) {
        look(yaw, pitch, false);
    }

    public void look(float yaw, float pitch, boolean force) {
        targetYaw = yaw;
        targetPitch = pitch;
        this.force = force;
        this.smoothForced = false;
        active = true;
    }

    public void lookSmooth(float yaw, float pitch) {
        look(yaw, pitch, true);
        smoothForced = true;
    }

    public void clear() {
        active = false;
        force = false;
        smoothForced = false;
    }

    public void reset() {
        active = false;
        force = false;
        smoothForced = false;
        lastFrame = 0;
        lastUserMouseTime = 0;
    }
}
