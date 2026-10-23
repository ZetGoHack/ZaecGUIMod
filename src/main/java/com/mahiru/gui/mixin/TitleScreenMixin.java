package com.mahiru.gui.mixin;

import com.mahiru.gui.TitleHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void zaecguimod$background(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleHooks.drawBackground(g, (net.minecraft.client.gui.screens.Screen) (Object) this);
    }
}
