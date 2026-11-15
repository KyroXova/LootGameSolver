package com.kyroxova.lootgamesolver.minecraft;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

public final class BaritoneBlockBridge {

    private BaritoneBlockBridge() {}

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
