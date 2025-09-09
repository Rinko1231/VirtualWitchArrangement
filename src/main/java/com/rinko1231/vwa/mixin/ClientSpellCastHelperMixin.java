package com.rinko1231.vwa.mixin;

import com.rinko1231.vwa.NewEldritchResearchScreen;
import io.redspace.ironsspellbooks.player.ClientSpellCastHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientSpellCastHelper.class, remap = false)
public abstract class ClientSpellCastHelperMixin {

    @Inject(method = "openEldritchResearchScreen", at = @At("HEAD"), cancellable = true)
    private static void redirectToSpiral(InteractionHand hand, CallbackInfo ci) {
        Minecraft.getInstance().setScreen(new NewEldritchResearchScreen(Component.empty(), hand));
        ci.cancel(); // 阻止原来的 EldritchResearchScreen 打开
    }
}
