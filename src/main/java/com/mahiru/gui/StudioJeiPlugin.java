package com.mahiru.gui;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;

/**
 * Optional JEI integration (only loaded when JEI is installed): while the studio is open, the whole screen counts as
 * occupied, so JEI draws no ingredient list over the menu previews.
 */
@JeiPlugin
public class StudioJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ZaecGuiMod.id("jei");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                return Minecraft.getInstance().screen instanceof Studio ? List.of(new Rect2i(0, 0, 20000, 20000)) : List.of();
            }
        });
    }
}
