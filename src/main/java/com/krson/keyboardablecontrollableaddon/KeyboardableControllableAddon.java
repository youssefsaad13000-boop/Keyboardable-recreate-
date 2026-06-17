package com.krson.keyboardablecontrollableaddon;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(KeyboardableControllableAddon.MOD_ID)
public class KeyboardableControllableAddon {
    public static final String MOD_ID = "keyboardable_controllable_addon";

    public KeyboardableControllableAddon() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ClientHooks.init(modBus);
    }
}
