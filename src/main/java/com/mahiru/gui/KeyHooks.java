package com.mahiru.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** Rebindable keys (Options > Controls > ZaecGuiMod). They work with any screen open and in the bare world. */
public final class KeyHooks {
    public static final String CATEGORY = "key.categories.zaecguimod";
    public static final KeyMapping STUDIO = new KeyMapping("key.zaecguimod.studio", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, CATEGORY);
    public static final KeyMapping TOGGLE = new KeyMapping("key.zaecguimod.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F10, CATEGORY);

    /** true from the press until the release: key repeat must not fire the action again */
    public static boolean studioHeld, toggleHeld;
    private boolean prevStudio, prevToggle;

    @SubscribeEvent
    public void onKey(ScreenEvent.KeyPressed.Pre e) {
        boolean studio = STUDIO.matches(e.getKeyCode(), e.getScanCode());
        boolean toggle = TOGGLE.matches(e.getKeyCode(), e.getScanCode());
        if (!studio && !toggle) return;
        var screen = e.getScreen();
        // never while the player is choosing a key, typing, or on a loading / error screen
        if (screen instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsScreen) return;
        if (e.getKeyCode() < GLFW.GLFW_KEY_ESCAPE && screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox) return;
        if (screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen || screen instanceof net.minecraft.client.gui.screens.ProgressScreen
                || screen instanceof net.minecraft.client.gui.screens.ConnectScreen || screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen) return;
        if (toggle && !(e.getScreen() instanceof Studio)) {
            e.setCanceled(true);
            if (!toggleHeld) {
                toggleHeld = true;
                Guard.run("toggle", ContainerHooks::togglePanic);
            }
        } else if (studio && !(e.getScreen() instanceof Studio)) {
            e.setCanceled(true);
            if (!studioHeld) {
                studioHeld = true;
                Guard.run("open-studio", () -> ContainerHooks.openStudio(e.getScreen()));
            }
        }
    }

    @SubscribeEvent
    public void onKeyUp(ScreenEvent.KeyReleased.Pre e) {
        if (STUDIO.matches(e.getKeyCode(), e.getScanCode())) studioHeld = false;
        if (TOGGLE.matches(e.getKeyCode(), e.getScanCode())) toggleHeld = false;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        boolean s = STUDIO.isDown(), t = TOGGLE.isDown();
        if (mc.screen == null) {
            if (s && !prevStudio && !studioHeld) {
                studioHeld = true;
                Guard.run("open-studio", () -> ContainerHooks.openStudio(null));
            }
            if (t && !prevToggle && !toggleHeld) {
                toggleHeld = true;
                Guard.run("toggle", ContainerHooks::togglePanic);
            }
            if (!s) studioHeld = false;
            if (!t) toggleHeld = false;
        }
        prevStudio = s;
        prevToggle = t;
    }
}
