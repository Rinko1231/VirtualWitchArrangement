package com.rinko1231.vwa;


import com.rinko1231.vwa.config.EldritchNodePositionsConfig;

import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;


import static com.rinko1231.vwa.VirtualWitchArrangement.MODID;
@SuppressWarnings("removal")//shut up
@EventBusSubscriber(
        modid = MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = {Dist.CLIENT}
)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        EldritchNodePositionsConfig.load();
    }
    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().is(ItemRegistry.ELDRITCH_PAGE.get())) {
            event.getToolTip().add(
                    Component.translatable("tooltip.irons_spellbooks.eldritch_manuscript.drag_nodes").withStyle(ChatFormatting.GRAY));
        }
    }
}
