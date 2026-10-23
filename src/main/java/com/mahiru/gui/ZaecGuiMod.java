package com.mahiru.gui;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@Mod(value = ZaecGuiMod.MODID, dist = Dist.CLIENT)
public class ZaecGuiMod {
    public static final String MODID = "zaecguimod";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public ZaecGuiMod(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new Studio(parent, null));
        modBus.addListener((RegisterClientReloadListenersEvent e) ->
                e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) rm -> {
                    Arts.invalidate();
                    Masks.invalidate();
                }));
        NeoForge.EVENT_BUS.register(new TitleHooks());
        NeoForge.EVENT_BUS.register(new ContainerHooks());
        NeoForge.EVENT_BUS.register(new KeyHooks());
        modBus.addListener((RegisterKeyMappingsEvent e) -> {
            e.register(KeyHooks.STUDIO);
            e.register(KeyHooks.TOGGLE);
        });
    }
}
