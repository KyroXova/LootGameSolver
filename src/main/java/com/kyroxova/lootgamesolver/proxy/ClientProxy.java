package com.kyroxova.lootgamesolver.proxy;

import com.kyroxova.lootgamesolver.client.ClientHooks;
import com.kyroxova.lootgamesolver.client.Keybinds;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.common.MinecraftForge;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        Keybinds.register();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientHooks hooks = new ClientHooks();
        cpw.mods.fml.common.FMLCommonHandler.instance()
            .bus()
            .register(hooks);
        MinecraftForge.EVENT_BUS.register(hooks);
        MinecraftForge.EVENT_BUS.register(hooks.getWorldRenderer());
    }
}
