package com.mahiru.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Draws a random art in container menus (a new one each time a menu opens); F9 opens the studio. */
public class ContainerHooks {
    public static final class Choice {
        public String key;
        public Masks.Mask mask;
        /** what to draw, bottom to top; for the studio preview the studio refills it every frame */
        public java.util.List<LayerEngine.Item> items = new java.util.ArrayList<>();
        public final LayerEngine.Cache cache = new LayerEngine.Cache();
        /** true for studio preview screens (drawn under a custom pose) */
        public boolean preview;
        /** absolute origin and scale of a preview screen, updated by the studio every frame */
        public float absX, absY, scale = 1f;

        /** the art of the first random layer (what the player mostly sees), or null */
        public Arts.Art mainArt() {
            for (LayerEngine.Item i : items) if (i.layer.random()) return i.art;
            return items.isEmpty() ? null : items.get(0).art;
        }
    }

    private static final Map<Screen, Choice> CHOICES = new WeakHashMap<>();

    public static void registerPreview(Screen s, Choice c) {
        c.preview = true;
        CHOICES.put(s, c);
    }

    private static Choice choice(AbstractContainerScreen<?> s) {
        Choice c = CHOICES.get(s);
        if (c != null && (c.preview || java.util.Objects.equals(c.key, MenuInfo.keyFor(s)))) return c;
        c = new Choice();
        try {
            c.key = MenuInfo.keyFor(s);
            c.mask = c.key == null ? null : Masks.get(c.key);
            if (c.mask != null && !Settings.get().menuView(c.key).disabled) c.items = LayerEngine.pickAll(c.key);
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] could not choose arts for a menu", t);
        }
        CHOICES.put(s, c);
        return c;
    }

    /**
     * The studio changed settings while a menu screen stayed open underneath: build the layers again, keeping the
     * art each layer was showing (or the one just edited in the studio for that layer).
     */
    public static void refresh(Screen s, Map<String, String> studioSamples) {
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;
        Choice old = CHOICES.get(s);
        if (old == null || old.preview) return;
        CHOICES.remove(s);
        Choice n = choice(acs);
        if (n.key == null || n.mask == null || !n.key.equals(old.key)) return;
        java.util.List<LayerEngine.Item> merged = new java.util.ArrayList<>();
        for (LayerEngine.Item it : n.items) {
            Arts.Art keep = null;
            String sample = studioSamples == null ? null : studioSamples.get(it.layer.id);
            if (sample != null) keep = Arts.byId(sample);
            if (keep == null) {
                for (LayerEngine.Item o : old.items) {
                    if (o.layer.id.equals(it.layer.id) && o.layer.src.equals(it.layer.src) && o.layer.group.equals(it.layer.group)
                            && (it.layer.random() || o.layer.art.equals(it.layer.art))) { keep = o.art; break; }
                }
            }
            merged.add(keep == null ? it : new LayerEngine.Item(it.layer, keep));
        }
        n.items = merged;
    }

    private static void draw(AbstractContainerScreen<?> s, net.minecraft.client.gui.GuiGraphics g, int pass, boolean relative) {
        if (!Settings.get().enabled) return;
        Choice c = choice(s);
        if (c.mask == null || c.key == null) return;
        Settings st = Settings.get();
        Settings.MenuCfg cfg = st.menuView(c.key);
        if (cfg.disabled && !c.preview) return;
        int left = s.getGuiLeft(), top = s.getGuiTop();
        float ax = c.preview ? c.absX : left, ay = c.preview ? c.absY : top, sc = c.preview ? c.scale : 1f;
        int[] scissor = c.preview ? new int[] {
                Math.round(ax + sc * Placement.INSET), Math.round(ay + sc * Placement.INSET),
                Math.round(ax + sc * (s.getXSize() - Placement.INSET)), Math.round(ay + sc * (s.getYSize() - Placement.INSET)) } : new int[] {
                left + Placement.INSET, top + Placement.INSET,
                left + s.getXSize() - Placement.INSET, top + s.getYSize() - Placement.INSET };
        int ox = relative ? 0 : left, oy = relative ? 0 : top;
        drawItems(g, c.items, c.cache, c.mask, cfg, pass, ox, oy, ax, ay, sc, scissor);
    }

    /** Draws layers bottom to top. {@code pass}: 0 = background pass, 1 = foreground pass, -1 = everything (schematic). */
    public static void drawItems(net.minecraft.client.gui.GuiGraphics g, java.util.List<LayerEngine.Item> items, LayerEngine.Cache cache,
                                 Masks.Mask mask, Settings.MenuCfg cfg, int pass, int ox, int oy, float ax, float ay, float sc, int[] scissor) {
        Settings st = Settings.get();
        for (LayerEngine.Item it : items) {
            Settings.Layer l = it.layer;
            if (!l.visible) continue;
            int mode = st.modeOf(cfg, l);
            boolean over = mode == Settings.MODE_OVER;
            if (pass >= 0 && over != (pass == 1)) continue;
            Placement p = LayerEngine.placement(l, it.art, mask, cache);
            ArtRenderer.draw(g, mask, it.art, p, st.opacityOf(cfg, l), mode, ox, oy, ax, ay, sc, scissor, over ? 200f : 0f, p.flip);
        }
    }

    @SubscribeEvent
    public void onBackground(ContainerScreenEvent.Render.Background e) {
        Guard.run("container-background", () -> draw(e.getContainerScreen(), e.getGuiGraphics(), 0, false));
    }

    @SubscribeEvent
    public void onForeground(ContainerScreenEvent.Render.Foreground e) {
        Guard.run("container-foreground", () -> draw(e.getContainerScreen(), e.getGuiGraphics(), 1, true));
    }

    private static final int GEAR = 14;

    private static boolean gearShown(net.minecraft.client.gui.screens.Screen s) {
        if (!(s instanceof AbstractContainerScreen<?> acs) || CHOICES.containsKey(s) && CHOICES.get(s).preview) return false;
        if (!Settings.get().showGear) return false;
        return MenuInfo.keyFor(acs) != null;
    }

    /** a gap below the menu panel that keeps the default button clear of overlays that hug the panel (JEI, recipe viewers) */
    private static final int GEAR_GAP = 12;

    private static int gearX(net.minecraft.client.gui.screens.Screen s) {
        int fx = Settings.get().gearFx;
        if (fx == Settings.GEAR_DEFAULT && s instanceof AbstractContainerScreen<?> acs)
            return Math.max(0, Math.min(s.width - GEAR, acs.getGuiLeft()));
        return Math.max(0, Math.round((s.width - GEAR) * Math.max(0, fx) / 1000f));
    }

    private static int gearY(net.minecraft.client.gui.screens.Screen s) {
        int fy = Settings.get().gearFy;
        if (fy == Settings.GEAR_DEFAULT && s instanceof AbstractContainerScreen<?> acs)
            return Math.max(0, Math.min(s.height - GEAR - 2, acs.getGuiTop() + acs.getYSize() + GEAR_GAP));
        return Math.max(0, Math.round((s.height - GEAR) * Math.max(0, fy) / 1000f));
    }

    private static boolean gearPress, gearDragging;
    private static double gearDownX, gearDownY;
    @SubscribeEvent
    public void gearRender(ScreenEvent.Render.Post e) {
        Guard.run("gear-render", () -> {
            var s = e.getScreen();
            if (!gearShown(s)) return;
            var g = e.getGuiGraphics();
            int x = gearX(s), y = gearY(s), mx = e.getMouseX(), my = e.getMouseY();
            boolean hov = mx >= x && mx < x + GEAR && my >= y && my < y + GEAR;
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            g.fill(x, y, x + GEAR, y + GEAR, hov ? 0xE03A3A58 : 0xB0202028);
            g.fill(x, y, x + GEAR, y + 1, 0xFF6A6A90);
            g.fill(x, y + GEAR - 1, x + GEAR, y + GEAR, 0xFF6A6A90);
            g.fill(x, y, x + 1, y + GEAR, 0xFF6A6A90);
            g.fill(x + GEAR - 1, y, x + GEAR, y + GEAR, 0xFF6A6A90);
            int cx = x + GEAR / 2, cy = y + GEAR / 2, col = hov ? 0xFFFFFFFF : 0xFFC8C8E0;
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int r2 = dx * dx + dy * dy;
                    boolean ring = r2 <= 12 && r2 >= 3;
                    boolean tooth = r2 >= 9 && r2 <= 20 && (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy));
                    if (ring || tooth) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, col);
                }
            }
            if (hov) g.renderTooltip(net.minecraft.client.Minecraft.getInstance().font,
                    net.minecraft.network.chat.Component.literal(T.t("Open the studio on this menu") + " (F9). " + T.t("Drag to move, right-click to put it back")), mx, my);
            g.pose().popPose();
        });
    }

    @SubscribeEvent
    public void gearClick(ScreenEvent.MouseButtonPressed.Pre e) {
        Guard.run("gear-click", () -> {
            var s = e.getScreen();
            if (!gearShown(s)) return;
            int x = gearX(s), y = gearY(s);
            if (e.getMouseX() >= x && e.getMouseX() < x + GEAR && e.getMouseY() >= y && e.getMouseY() < y + GEAR) {
                e.setCanceled(true);
                if (e.getButton() == 1) {
                    Settings.get().gearFx = Settings.GEAR_DEFAULT;
                    Settings.get().gearFy = Settings.GEAR_DEFAULT;
                    Settings.save();
                } else if (e.getButton() == 0) {
                    gearPress = true;
                    gearDragging = false;
                    gearDownX = e.getMouseX();
                    gearDownY = e.getMouseY();
                }
            }
        });
    }

    @SubscribeEvent
    public void gearDrag(ScreenEvent.MouseDragged.Pre e) {
        Guard.run("gear-drag", () -> {
            var s = e.getScreen();
            if (!gearPress || !gearShown(s)) return;
            if (!gearDragging && Math.abs(e.getMouseX() - gearDownX) + Math.abs(e.getMouseY() - gearDownY) > 3) gearDragging = true;
            if (!gearDragging) return;
            e.setCanceled(true);
            Settings st = Settings.get();
            st.gearFx = Math.max(0, Math.min(1000, (int) Math.round((e.getMouseX() - GEAR / 2.0) / Math.max(1, s.width - GEAR) * 1000)));
            st.gearFy = Math.max(0, Math.min(1000, (int) Math.round((e.getMouseY() - GEAR / 2.0) / Math.max(1, s.height - GEAR) * 1000)));
        });
    }

    @SubscribeEvent
    public void gearRelease(ScreenEvent.MouseButtonReleased.Pre e) {
        Guard.run("gear-release", () -> {
            if (!gearPress || e.getButton() != 0) return;
            gearPress = false;
            if (!gearShown(e.getScreen())) { gearDragging = false; return; }
            e.setCanceled(true);
            if (gearDragging) { gearDragging = false; Settings.save(); }
            else openStudio(e.getScreen());
        });
    }

    // Other mods (JEI) draw their overlays from the same container events; for a studio preview they are clipped to the panel.
    private static boolean clipOn;

    private static void clipBegin(ContainerScreenEvent e) {
        if (clipOn) return;
        Choice c = CHOICES.get(e.getContainerScreen());
        if (c == null || !c.preview) return;
        var s = e.getContainerScreen();
        var g = e instanceof ContainerScreenEvent.Render.Background b ? b.getGuiGraphics() : ((ContainerScreenEvent.Render.Foreground) e).getGuiGraphics();
        g.enableScissor(Math.round(c.absX), Math.round(c.absY), Math.round(c.absX + c.scale * s.getXSize()), Math.round(c.absY + c.scale * s.getYSize()));
        clipOn = true;
    }

    private static void clipEnd(net.minecraft.client.gui.GuiGraphics g) {
        if (!clipOn) return;
        clipOn = false;
        g.disableScissor();
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void bgClipBegin(ContainerScreenEvent.Render.Background e) { Guard.run("preview-clip", () -> clipBegin(e)); }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void bgClipEnd(ContainerScreenEvent.Render.Background e) { Guard.run("preview-clip", () -> clipEnd(e.getGuiGraphics())); }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void fgClipBegin(ContainerScreenEvent.Render.Foreground e) { Guard.run("preview-clip", () -> clipBegin(e)); }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public void fgClipEnd(ContainerScreenEvent.Render.Foreground e) { Guard.run("preview-clip", () -> clipEnd(e.getGuiGraphics())); }

    /** Opens the studio (on a container menu it jumps straight to that menu and the art on it). */
    public static void openStudio(Screen current) {
        Minecraft mc = Minecraft.getInstance();
        if (current instanceof AbstractContainerScreen<?> s && mc.player != null) {
            // the container stays open underneath: closing the studio brings the very same screen back
            String key = null;
            try { key = MenuInfo.keyFor(s); } catch (Throwable t) { Guard.LOG.error("[ZaecGuiMod] unknown container screen", t); }
            Choice cur = CHOICES.get(s);
            Arts.Art shown = cur != null ? cur.mainArt() : null;
            String artId = shown != null ? shown.id : null;
            mc.setScreen(new Studio(s, key, artId));
        } else if (TitleHooks.isTitleLike(current)) {
            mc.setScreen(new Studio(current, Studio.TITLE_KEY));
        } else {
            mc.setScreen(new Studio(current, null));
        }
    }

    /** Panic switch: every Zaec effect off/on without opening anything. */
    public static void togglePanic() {
        Settings st = Settings.get();
        st.enabled = !st.enabled;
        Settings.save();
        Minecraft m = Minecraft.getInstance();
        if (m.player != null) m.player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                T.f("ZaecGuiMod: %s (F10)", T.t(st.enabled ? "ON" : "OFF"))), true);
        // the title screen shifts its widgets in init: rebuild it so buttons and logo stay in step
        if (TitleHooks.isTitleLike(m.screen)) m.screen.resize(m, m.screen.width, m.screen.height);
    }
}
