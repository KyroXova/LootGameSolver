package com.kyroxova.lootgamesolver.proxy;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        LootGameSolverConfig.load(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {}
}
