package com.kyroxova.lootgamesolver.explorer.trail;

import java.util.Locale;

public final class OilAvoidance {

    private static final java.util.Map<net.minecraft.block.Block, Boolean> OIL_BLOCKS = new java.util.IdentityHashMap<net.minecraft.block.Block, Boolean>();

    private OilAvoidance() {}

    public static boolean isOil(String name) {
        if (name == null) return false;
        String normalized = name.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        return normalized.startsWith("oil") || (normalized.endsWith("oil") && !normalized.endsWith("soil"))
            || normalized.contains("crudeoil");
    }

    public static boolean isOilBlock(net.minecraft.block.Block block) {
        Boolean oil = OIL_BLOCKS.get(block);
        if (oil == null) {
            net.minecraftforge.fluids.Fluid fluid = net.minecraftforge.fluids.FluidRegistry.lookupFluidForBlock(block);
            oil = fluid != null && isOil(fluid.getName());
            OIL_BLOCKS.put(block, oil);
        }
        return oil;
    }
}
