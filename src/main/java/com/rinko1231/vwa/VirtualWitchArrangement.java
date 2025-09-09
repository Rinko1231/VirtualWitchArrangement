package com.rinko1231.vwa;


import com.rinko1231.vwa.config.ResearchLayoutClientConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;


@Mod(VirtualWitchArrangement.MOD_ID)
public class VirtualWitchArrangement {
    public static final String MOD_ID = "virtualwitcharrangement";
    public VirtualWitchArrangement() {
        @SuppressWarnings("removal")
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        if (FMLEnvironment.dist.isClient()) {
            ResearchLayoutClientConfig.register();
        }
    }

}
