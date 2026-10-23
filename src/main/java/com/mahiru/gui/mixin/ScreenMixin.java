package com.mahiru.gui.mixin;

import com.mahiru.gui.TitleHooks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets replacement title screens (which extend plain Screen) get the same background as the vanilla one. */
@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
                    shift = At.Shift.AFTER))
    private void zaecguimod$foreignTitleBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleHooks.drawForeignBackground(g, (Screen) (Object) this);
    }
}
