package com.kyroxova.lootgamesolver.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

final class ExplorerCamera {

    private boolean active;
    private float targetYaw, targetPitch;
    private long lastFrame;

    void render(Minecraft mc, World world, boolean walking) {
        long now = System.nanoTime();
        double seconds = lastFrame == 0 ? 1D / 60D : (now - lastFrame) / 1_000_000_000D;
        lastFrame = now;
        if (!walking || !active
            || mc.thePlayer == null
            || mc.theWorld != world
            || mc.currentScreen != null
            || mc.isGamePaused()) return;
        float yaw = NavigationSteering.cameraStep(mc.thePlayer.rotationYaw, targetYaw, seconds);
        float pitch = NavigationSteering.cameraStep(mc.thePlayer.rotationPitch, targetPitch, seconds);
        mc.thePlayer.setAngles(yaw / 0.15F, -pitch / 0.15F);
    }

    void look(float yaw, float pitch) {
        targetYaw = yaw;
        targetPitch = pitch;
        active = true;
    }

    void clear() {
        active = false;
    }

    void reset() {
        active = false;
        lastFrame = 0;
    }
}
