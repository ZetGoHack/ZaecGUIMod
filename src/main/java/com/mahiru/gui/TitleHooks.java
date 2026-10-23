package com.mahiru.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Random;

/** Main menu: logo/buttons pushed down, random big art on the side, SPECIAL sitting on the T of the logo. */
public class TitleHooks {
    private static final ResourceLocation SPECIAL = ZaecGuiMod.id("textures/gui/special.png");
    private static final int SPECIAL_TEX_W = 607, SPECIAL_TEX_H = 1200;
    private static final Random RNG = new Random();

    /** true while the studio draws its own TitleScreen instance (mc.screen is the studio then) */
    public static boolean previewing = false;
    /** the art the studio wants to see in its title preview (null = none) */
    public static Arts.Art previewArt;
    private static final Rain PRV_RAIN = new Rain();
    private static java.lang.ref.WeakReference<Screen> prvOwner = new java.lang.ref.WeakReference<>(null);
    private static int shift = 0;
    private static java.lang.ref.WeakReference<Screen> owner = new java.lang.ref.WeakReference<>(null);
    private static Arts.Art art;
    private static boolean artLeft = true;

    /** Extra downward offset for the logo, only while the title screen is on screen. */
    public static int logoShift() {
        return Settings.get().enabled && (previewing || isTitleLike(Minecraft.getInstance().screen)) ? shift : 0;
    }

    /**
     * The vanilla title screen, or a replacement from another mod (they all keep the "narrator.screen.title"
     * screen title, e.g. NoteBuns Menu extends plain Screen and ignores TitleScreen mixins).
     */
    public static boolean isTitleLike(Screen s) {
        if (s == null || s instanceof Studio) return false;
        if (s instanceof TitleScreen) return true;
        return s.getTitle().getContents() instanceof TranslatableContents tc && "narrator.screen.title".equals(tc.getKey());
    }

    /** Called from ScreenMixin for replacement title screens, right after their own background. */
    public static void drawForeignBackground(GuiGraphics g, Screen s) {
        if (s instanceof TitleScreen || !isTitleLike(s)) return;
        Guard.run("title-background-foreign", () -> drawBackground0(g, s));
    }

    @SubscribeEvent
    public void onInit(ScreenEvent.Init.Post e) {
        Guard.run("title-init", () -> init(e));
    }

    private static void init(ScreenEvent.Init.Post e) {
        if (!isTitleLike(e.getScreen())) return;
        Screen s = e.getScreen();
        shift = 0;
        if (!Settings.get().enabled) return;
        int bottom = 0;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof AbstractWidget w && w.getY() < s.height - 25) bottom = Math.max(bottom, w.getY() + w.getHeight());
        }
        shift = Math.max(0, Math.min(Settings.get().title.logoShift, s.height - 14 - bottom));
        if (shift == 0) return;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof AbstractWidget w && w.getY() < s.height - 25) w.setY(w.getY() + shift);
        }
    }

    private static final Rain RAIN = new Rain();

    /** Called from TitleScreenMixin right after the panorama, before the widgets. */
    public static void drawBackground(GuiGraphics g, Screen s) {
        Guard.run("title-background", () -> drawBackground0(g, s));
    }

    private static void drawBackground0(GuiGraphics g, Screen s) {
        if (!Settings.get().enabled) return;
        Settings.Title t = Settings.get().title;
        Arts.Art use;
        boolean left;
        Rain rain;
        if (previewing) {
            if (prvOwner.get() != s) { prvOwner = new java.lang.ref.WeakReference<>(s); PRV_RAIN.reset(); }
            use = previewArt;
            left = use == null || !"right".equals(use.anchor);
            rain = PRV_RAIN;
        } else {
            use = null; left = true; rain = RAIN;
        }
        if (!previewing && owner.get() != s) {
            owner = new java.lang.ref.WeakReference<>(s);
            art = Arts.pick("title");
            RAIN.reset();
            artLeft = art == null || switch (art.anchor) {
                case "right" -> false;
                case "left" -> true;
                default -> RNG.nextBoolean();
            };
        }
        if (!previewing) { use = art; left = artLeft; }
        paintBackground(g, s.width, s.height, t);
        if (t.mode == Settings.TITLE_PLAIN) return;
        if ((t.mode == Settings.TITLE_STATIC || t.mode == Settings.TITLE_BOTH) && use != null) {
            // full height; only shrink when it would cover more than ~62% of the width (no padding otherwise)
            int h = s.height;
            int w = Math.round(h * (float) use.w / use.h);
            int maxW = Math.round(s.width * 0.62f);
            if (w > maxW) { w = maxW; h = Math.round(w * (float) use.h / use.w); }
            int x = left ? 0 : s.width - w;
            RenderSystem.enableBlend();
            use.drawBig(g, x, s.height - h, w, h, Settings.get().title.flipArt);
            RenderSystem.disableBlend();
        }
        if (t.mode == Settings.TITLE_RAIN || t.mode == Settings.TITLE_BOTH) {
            rain.update(s.width, s.height, t);
            rain.render(g);
        }
    }

    /** the backdrop behind the art: a gradient, one colour, or nothing so the game's panorama stays */
    private static void paintBackground(GuiGraphics g, int w, int h, Settings.Title t) {
        int a = 0xFF000000 | t.bgA, b = 0xFF000000 | t.bgB;
        switch (t.bgType) {
            case Settings.BG_PANORAMA -> { }
            case Settings.BG_SOLID -> g.fill(0, 0, w, h, a);
            default -> {
                boolean reverse = t.bgDir == 1 || t.bgDir == 3;
                int from = reverse ? b : a, to = reverse ? a : b;
                if (t.bgDir < 2) { g.fillGradient(0, 0, w, h, from, to); break; }
                int strips = Math.max(1, Math.min(w, 96));
                for (int i = 0; i < strips; i++) {
                    int x0 = i * w / strips, x1 = (i + 1) * w / strips;
                    g.fill(x0, 0, x1, h, mix(from, to, strips == 1 ? 0f : i / (float) (strips - 1)));
                }
            }
        }
    }

    private static int mix(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int gg = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    @SubscribeEvent
    public void onRenderPost(ScreenEvent.Render.Post e) {
        Guard.run("title-mascot", () -> {
            if (isTitleLike(e.getScreen())) drawMascot(e.getGuiGraphics(), e.getScreen().width);
        });
    }

    /** where the mascot is drawn: {x, y, w, h} in gui pixels of a screen that is {@code sw} wide */
    public static int[] mascotRect(int sw) {
        int logoY = 30 + shift;
        // sits on the T: feet 27px below the top of the logo; shrink if the screen is too short for full height
        int bottom = logoY + 27;
        int h = Math.max(24, Math.min(117, bottom - 2));
        int w = Math.round(61f * h / 117f);
        int x = sw / 2 + 80 + (61 - w) / 2 + Settings.get().title.mascotDx;
        int y = bottom - h + Settings.get().title.mascotDy;
        return new int[] { x, y, w, h };
    }

    /** where the logo is: {x, y, w, h} (vanilla logo is 256 x 44 at y = 30, pushed down by the shift) */
    public static int[] logoRect(int sw) {
        return new int[] { sw / 2 - 128, 30 + shift, 256, 44 };
    }

    public static void drawMascot(GuiGraphics g, int sw) {
        if (!Settings.get().enabled || !Settings.get().title.mascot) return;
        int[] r = mascotRect(sw);
        int x = r[0], y = r[1], w = r[2], h = r[3];
        RenderSystem.enableBlend();
        g.blit(SPECIAL, x, y, w, h, 0, 0, SPECIAL_TEX_W, SPECIAL_TEX_H, SPECIAL_TEX_W, SPECIAL_TEX_H);
        RenderSystem.disableBlend();
    }
}
