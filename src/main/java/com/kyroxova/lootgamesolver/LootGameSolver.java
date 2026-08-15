package com.kyroxova.lootgamesolver;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kyroxova.lootgamesolver.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** Entry point. All puzzle logic is deliberately independent of Forge and Minecraft. */
@Mod(
    modid = LootGameSolver.MOD_ID,
    name = "LootGameSolver",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    dependencies = "after:lootgames")
public final class LootGameSolver {

    public static final String MOD_ID = "lootgamesolver";
    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @SidedProxy(
        clientSide = "com.kyroxova.lootgamesolver.proxy.ClientProxy",
        serverSide = "com.kyroxova.lootgamesolver.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
