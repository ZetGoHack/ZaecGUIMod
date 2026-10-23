package com.mahiru.gui.mixin;

import com.mahiru.gui.TitleHooks;
import net.minecraft.client.gui.components.SplashRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SplashRenderer.class)
public class SplashRendererMixin {
    @ModifyArg(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0),
            index = 1)
    private float zaecguimod$shiftSplash(float y) {
        return y + TitleHooks.logoShift();
    }
}
