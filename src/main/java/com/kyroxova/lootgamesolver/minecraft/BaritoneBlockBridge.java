package com.kyroxova.lootgamesolver.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

public final class BaritoneBlockBridge {

    private BaritoneBlockBridge() {}

    static boolean startBreaking(net.minecraft.util.MovingObjectPosition hit) {
        try {
            Object interaction = interaction();
            Class<?> posType = Class.forName("baritone.api.utils.BetterBlockPos");
            Object pos = posType.getConstructor(int.class, int.class, int.class)
                .newInstance(hit.blockX, hit.blockY, hit.blockZ);
            Object result = Class.forName("baritone.api.utils.IBlockInteraction")
                .getMethod("startBreaking", posType, ForgeDirection.class, Vec3.class)
                .invoke(interaction, pos, ForgeDirection.getOrientation(hit.sideHit), hit.hitVec);
            return "ATTEMPTED".equals(String.valueOf(result));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }

    static void stopBreaking() {
        try {
            Class.forName("baritone.api.utils.IBlockInteraction")
                .getMethod("stopBreaking")
                .invoke(interaction());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            com.kyroxova.lootgamesolver.LootGameSolver.LOG.debug("Could not stop Baritone block breaking", ignored);
        }
    }

    private static Object interaction() throws ReflectiveOperationException {
        Object provider = Class.forName("baritone.api.BaritoneAPI")
            .getMethod("getProvider")
            .invoke(null);
        Object baritone = Class.forName("baritone.api.IBaritoneProvider")
            .getMethod("getPrimaryBaritone")
            .invoke(provider);
        return Class.forName("baritone.api.IBaritone")
            .getMethod("getBlockInteraction")
            .invoke(baritone);
    }

    public static boolean click(Minecraft mc, int x, int y, int z, int side, Vec3 hit, boolean attack) {
        Class<?> api;
        try {
            api = Class.forName("baritone.api.BaritoneAPI");
        } catch (ClassNotFoundException absent) {
            if (attack) mc.playerController.clickBlock(x, y, z, side);
            else mc.playerController
                .onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), x, y, z, side, hit);
            mc.thePlayer.swingItem();
            return true;
        } catch (LinkageError failure) {
            return false;
        }
        try {
            Object provider = api.getMethod("getProvider")
                .invoke(null);
            Object baritone = Class.forName("baritone.api.IBaritoneProvider")
                .getMethod("getPrimaryBaritone")
                .invoke(provider);
            Object interaction = Class.forName("baritone.api.IBaritone")
                .getMethod("getBlockInteraction")
                .invoke(baritone);
            Class<?> positionType = Class.forName("baritone.api.utils.BetterBlockPos");
            Object position = positionType.getConstructor(int.class, int.class, int.class)
                .newInstance(x, y, z);
            Class<?> interactionType = Class.forName("baritone.api.utils.IBlockInteraction");
            ForgeDirection face = ForgeDirection.getOrientation(side);
            Object result = attack
                ? interactionType
                    .getMethod("attackBlock", positionType, ForgeDirection.class, Vec3.class, boolean.class)
                    .invoke(interaction, position, face, hit, false)
                : interactionType
                    .getMethod("useBlock", positionType, ForgeDirection.class, Vec3.class, boolean.class, boolean.class)
                    .invoke(interaction, position, face, hit, false, false);
            return "ATTEMPTED".equals(String.valueOf(result));
        } catch (ReflectiveOperationException | LinkageError failure) {
            return false;
        }
    }
}
