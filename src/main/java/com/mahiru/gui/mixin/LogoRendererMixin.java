package com.mahiru.gui.mixin;

import com.mahiru.gui.TitleHooks;
import net.minecraft.client.gui.components.LogoRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    @ModifyArg(
            method = "renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IFI)V"),
            index = 3)
    private int zaecguimod$shiftLogo(int height) {
        return height + TitleHooks.logoShift();
    }
}
