package com.mahiru.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/** The centre of the studio: the real menu with its layers, handles, snapping, zoom and pan. */
final class StudioCanvas {
    private final Studio s;
    final LayerEngine.Cache cache = new LayerEngine.Cache();

    // ---- the real menu screen used as the backdrop
    private Screen preview;
    private ContainerHooks.Choice choice;
    private boolean previewTried, previewInited;
    private static final int PAD = 40;

    // ---- main menu preview
    private net.minecraft.client.gui.screens.TitleScreen titleScr;
    private int tpW = -1, tpH = -1, tpShift = -1;
    private boolean tpEnabled, titleFailed;

    // ---- view
    private float zoom = 1f;
    private boolean fit = true;
    private float panX, panY;

    // ---- geometry of this frame
    private int vx, vy, vw, vh;
    private float sc = 1f, px0, py0;

    // ---- snapping guides (panel coordinates; drawn while dragging)
    private final List<float[]> guides = new ArrayList<>();

    StudioCanvas(Studio s) {
        this.s = s;
    }

    void menuChanged() {
        broken = false;
        previewTried = false;
        previewInited = false;
        dispose();
        choice = null;
        cache.clear();
        fit = true;
        panX = 0;
        panY = 0;
    }

    /** forget the preview screen and give back what it holds */
    void dispose() {
        if (preview != null) MenuInfo.disposePreview(preview);
        preview = null;
    }

    void fit() {
        fit = true;
        panX = 0;
        panY = 0;
    }

    /** keeps at least 16 px of the art inside the panel */
    void clamp(Placement p) {
        Masks.Mask m = s.mask();
        if (m == null) return;
        Placement b = p.bounds();
        int keep = Math.min(16, Math.min(b.w, b.h));
        int dx = 0, dy = 0;
        if (b.x + b.w < keep) dx = keep - (b.x + b.w);
        if (b.x > m.w - keep) dx = (m.w - keep) - b.x;
        if (b.y + b.h < keep) dy = keep - (b.y + b.h);
        if (b.y > m.h - keep) dy = (m.h - keep) - b.y;
        p.x += dx;
        p.y += dy;
    }

    // ------------------------------------------------------------------ coordinates

    /** true when the point is inside the viewport of the menu (not the toolbar) */
    boolean contains(double mx, double my) {
        return !s.isTitle() && mx >= vx && mx < vx + vw && my >= vy && my < vy + vh;
    }

    float panelX(double mx) { return toPanelX(mx); }

    float panelY(double my) { return toPanelY(my); }

    private float toPanelX(double mx) { return (float) ((mx - px0) / sc); }
    private float toPanelY(double my) { return (float) ((my - py0) / sc); }
    private float scrX(double px) { return (float) (px0 + sc * px); }
    private float scrY(double py) { return (float) (py0 + sc * py); }

    /** screen position of a point given in the layer's own frame (offset from its centre, before rotation) */
    private float[] corner(Placement p, double ox, double oy) {
        double r = Math.toRadians(p.rot), c = Math.cos(r), sn = Math.sin(r);
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0;
        return new float[] { scrX(cx + ox * c - oy * sn), scrY(cy + ox * sn + oy * c) };
    }

    private boolean inPlace(Placement p, double mx, double my) {
        double lx = toPanelX(mx), ly = toPanelY(my);
        double cx = p.x + p.w / 2.0, cy = p.y + p.h / 2.0;
        if (p.rot != 0) {
            double r = Math.toRadians(-p.rot);
            double dx = lx - cx, dy = ly - cy;
            lx = cx + dx * Math.cos(r) - dy * Math.sin(r);
            ly = cy + dx * Math.sin(r) + dy * Math.cos(r);
        }
        return lx >= p.x && lx < p.x + p.w && ly >= p.y && ly < p.y + p.h;
    }

    /** the topmost visible layer under the mouse, or -1 */
    private int pickLayer(double mx, double my) {
        Masks.Mask m = s.mask();
        if (m == null) return -1;
        List<Settings.Layer> ls = s.layers();
        for (int i = ls.size() - 1; i >= 0; i--) {
            Settings.Layer l = ls.get(i);
            if (!l.visible) continue;
            Arts.Art a = s.sample(l);
            if (a == null) continue;
            if (inPlace(LayerEngine.placement(l, a, m, cache), mx, my)) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the toolbar and the viewport inside the given rectangle. */
    void draw(GuiGraphics g, Kit kit, int x, int y, int w, int h, float pt) {
        if (s.isTitle()) {
            vx = x; vy = y; vw = w; vh = h;
            drawTitle(g, kit, pt);
            return;
        }
        int th = 16;
        drawToolbar(kit, x, y, w, th);
        vx = x; vy = y + th; vw = w; vh = h - th;
        drawViewport(g, kit, pt);
    }

    private void drawToolbar(Kit kit, int x, int y, int w, int h) {
        kit.rect(x, y, w, h, Kit.PANEL);
        Settings.Layer l = s.sel();
        boolean has = l != null && s.sample(l) != null;
        Arts.Art a = l == null ? null : s.sample(l);
        boolean canReset = has && !LayerEngine.isAuto(l, a);
        boolean anyRandom = false;
        for (Settings.Layer q : s.layers()) if (q.random()) anyRandom = true;
        String none = T.t("Select a layer first");
        int bx = x + 2, bs = 13, gap = 2, by = y + 1;
        bx = tb(kit, bx, by, bs, Kit.FLIP, T.t("Flip left-right (H)"), false, has, none, s::flip) + gap;
        bx = tb(kit, bx, by, bs, Kit.FLIP_V, T.t("Flip top-bottom (V)"), false, has, none, () -> s.flip(true)) + gap;
        bx = tb(kit, bx, by, bs, Kit.ROT_L, T.t("Turn left 5 degrees ([ ], Shift = 15)"), false, has, none, () -> s.rotate(-(Screen.hasShiftDown() ? 15 : 5))) + gap;
        bx = tb(kit, bx, by, bs, Kit.ROT_R, T.t("Turn right 5 degrees (Shift = 15)"), false, has, none, () -> s.rotate(Screen.hasShiftDown() ? 15 : 5)) + gap;
        bx = tb(kit, bx, by, bs, Kit.CENTER_H, T.t("Centre horizontally"), false, has, none, () -> s.center(true)) + gap;
        bx = tb(kit, bx, by, bs, Kit.CENTER_V, T.t("Centre vertically"), false, has, none, () -> s.center(false)) + gap;
        bx = tb(kit, bx, by, bs, Kit.RESET, T.t("Back to the automatic position"), false, canReset, has ? T.t("Already automatic") : none, s::resetAuto) + gap + 4;
        bx = tb(kit, bx, by, bs, Kit.EYE, T.t("Player view: hide handles (Tab)"), s.playerView, true, "", () -> s.playerView = !s.playerView) + gap;
        bx = tb(kit, bx, by, bs, Kit.DICE, T.t("Another random art for the selected layer (R)"), false, anyRandom, T.t("No random layer in this menu"), s::roll) + gap;
        bx = tb(kit, bx, by, bs, Kit.MAGNET, T.t("Snap to edges and centres (hold Alt to ignore)"), s.snapOn, true, "", () -> s.snapOn = !s.snapOn) + gap;
        bx = tb(kit, bx, by, bs, Kit.LAYERS, T.t("Faint outlines of the other arts of the pool, where they are saved"), s.ghostsOn, anyRandom, T.t("No random layer in this menu"), () -> s.ghostsOn = !s.ghostsOn) + gap + 4;
        if (bx + 3 * (bs + gap) < x + w) {
            bx = tb(kit, bx, by, bs, Kit.ZOOM_OUT, T.t("Zoom out"), false, true, "", () -> zoomBy(1f / 1.25f)) + gap;
            bx = tb(kit, bx, by, bs, Kit.ZOOM_IN, T.t("Zoom in (mouse wheel)"), false, true, "", () -> zoomBy(1.25f)) + gap;
            tb(kit, bx, by, bs, Kit.FIT, T.t("Fit the menu in the view (0)"), fit, true, "", this::fit);
        }
    }

    private int tb(Kit kit, int x, int y, int size, int icon, String tip, boolean on, boolean enabled, String why, Runnable r) {
        kit.b(x, y, size, size).icon(icon).tip(tip).on(on).enabled(enabled, why).id("tb" + icon).press(r).show();
        return x + size;
    }

    private void zoomBy(float f) { zoomAt(vx + vw / 2.0, vy + vh / 2.0, f); }

    private void zoomAt(double mx, double my, float f) {
        Masks.Mask m = s.mask();
        if (m == null) return;
        float ns = Math.max(0.25f, Math.min(8f, sc * f));
        float u = (float) ((mx - px0) / sc), v = (float) ((my - py0) / sc);
        float npx = (float) (mx - ns * u), npy = (float) (my - ns * v);
        panX = npx - vx - vw / 2f + ns * m.w / 2f;
        panY = npy - vy - vh / 2f + ns * m.h / 2f;
        zoom = ns;
        fit = false;
    }

    private void ensurePreview(Masks.Mask m) {
        if (!previewTried) {
            previewTried = true;
            preview = MenuInfo.createPreview(s.menuKey);
            previewInited = false;
        }
        if (preview != null && !previewInited) {
            previewInited = true;
            try {
                MenuInfo.initPreview(preview, m.w + 2 * PAD, m.h + 2 * PAD);
            } catch (Throwable t) {
                Guard.LOG.error("[ZaecGuiMod] the menu preview could not start, using the schematic one", t);
                dispose();
                return;
            }
            try {
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(
                        s, new ArrayList<>(s.children()), w -> { }, w -> { }));
            } catch (Throwable t) {
                Guard.LOG.warn("[ZaecGuiMod] could not re-announce the studio screen", t);
            }
            choice = new ContainerHooks.Choice();
            choice.key = s.menuKey;
            choice.mask = m;
            ContainerHooks.registerPreview(preview, choice);
        }
    }

    private void drawViewport(GuiGraphics g, Kit kit, float pt) {
        Masks.Mask m = s.mask();
        kit.rect(vx, vy, vw, vh, 0xFF0D0D12);
        if (m == null) return;
        float fitZ = Math.max(0.25f, Math.min(6f, FIT_FILL * Math.min((vw - 16f) / m.w, (vh - 16f) / m.h)));
        if (fit) { zoom = fitZ; panX = 0; panY = 0; }
        sc = zoom;
        px0 = vx + vw / 2f + panX - sc * m.w / 2f;
        py0 = vy + vh / 2f + panY - sc * m.h / 2f;

        // the viewport itself is a hit: pick / move layers, pan, zoom, context menu
        Kit.Hit vp = new Kit.Hit();
        vp.x = vx; vp.y = vy; vp.w = vw; vp.h = vh; vp.id = "canvas";
        vp.drag = this::beginPick;
        vp.dragMid = (mx0, my0) -> new PanDrag(mx0, my0);
        vp.right = () -> {
            int i = pickLayer(kit.mx, kit.my);
            if (i >= 0) { s.selectLayer(s.layers().get(i).id); StudioView.openLayerMenu(s, kit.mx, kit.my); }
            else openCanvasMenu();
        };
        vp.wheel = sy -> {
            if (Screen.hasControlDown()) s.scaleBy(sy > 0 ? 1.06f : 1f / 1.06f);
            else if (Screen.hasShiftDown()) s.rotate(sy > 0 ? 5 : -5);
            else zoomAt(kit.mx, kit.my, sy > 0 ? 1.15f : 1f / 1.15f);
        };
        kit.add(vp);

        // the layers, in the order and with the art the studio shows
        List<LayerEngine.Item> items = new ArrayList<>();
        for (Settings.Layer l : s.layers()) {
            Arts.Art a = s.sample(l);
            if (a != null) items.add(new LayerEngine.Item(l, a));
        }
        Settings.MenuCfg cfg = s.st().menuView(s.menuKey);
        ensurePreview(m);

        kit.pushClip(vx, vy, vx + vw, vy + vh);
        try {
            if (preview != null && choice != null && !previewBroken()) {
                AbstractContainerScreen<?> acs = (AbstractContainerScreen<?>) preview;
                choice.items = items;
                choice.absX = px0;
                choice.absY = py0;
                choice.scale = sc;
                g.pose().pushPose();
                g.pose().translate(px0 - sc * acs.getGuiLeft(), py0 - sc * acs.getGuiTop(), 0);
                g.pose().scale(sc, sc, 1f);
                try {
                    MenuInfo.withCreativeTab(preview, () -> preview.render(g, -9999, -9999, pt));
                } catch (Throwable t) {
                    broken = true;
                    Guard.LOG.error("[ZaecGuiMod] the menu preview failed, using the schematic one", t);
                } finally {
                    g.pose().popPose();
                }
                // other mods (JEI...) draw overlays during the preview's render events: wipe everything around the panel
                g.pose().pushPose();
                g.pose().translate(0, 0, 250);
                // the creative screen has its tabs above and below the panel: leave them visible
                boolean tabs = acs instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
                int pl = Math.round(px0), pt2 = Math.round(py0 - (tabs ? sc * 32 : 0)), pr = Math.round(px0 + sc * acs.getXSize()), pb = Math.round(py0 + sc * acs.getYSize() + (tabs ? sc * 34 : 0));
                g.fill(vx, vy, vx + vw, pt2, 0xFF0D0D12);
                g.fill(vx, pb, vx + vw, vy + vh, 0xFF0D0D12);
                g.fill(vx, pt2, pl, pb, 0xFF0D0D12);
                g.fill(pr, pt2, vx + vw, pb, 0xFF0D0D12);
                g.pose().popPose();
            } else {
                drawSchematic(g, m, items, cfg);
            }
            if (!s.playerView) drawOverlay(g, kit, m);
        } finally {
            kit.popClip();
            RenderSystem.disableBlend();
            g.setColor(1f, 1f, 1f, 1f);
        }
        if (!s.playerView) drawHandleHits(kit);
        drawBadge(g, kit);
    }

    /** the "fit" view fills this share of the viewport, so the menu does not start huge */
    private static final float FIT_FILL = 0.7f;

    private boolean broken;

    private boolean previewBroken() { return broken; }

    private void drawSchematic(GuiGraphics g, Masks.Mask m, List<LayerEngine.Item> items, Settings.MenuCfg cfg) {
        g.pose().pushPose();
        g.pose().translate(px0, py0, 0);
        g.pose().scale(sc, sc, 1f);
        g.fill(0, 0, m.w, m.h, 0xFFC6C6C6);
        for (int i = 0; i < m.rects.length; i++) {
            int[] r = m.rects[i];
            if (m.kinds[i] == 's') g.fill(r[0] - 1, r[1] - 1, r[0] + 17, r[1] + 17, 0xFF8B8B8B);
            else g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF9A9AAA);
        }
        int[] clip = { Math.round(px0 + sc * Placement.INSET), Math.round(py0 + sc * Placement.INSET),
                Math.round(px0 + sc * (m.w - Placement.INSET)), Math.round(py0 + sc * (m.h - Placement.INSET)) };
        ContainerHooks.drawItems(g, items, cache, m, cfg, -1, 0, 0, px0, py0, sc, clip);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ overlay: selection frame, guides, handles

    private void drawOverlay(GuiGraphics g, Kit kit, Masks.Mask m) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (float[] gl : guides) {
            if (gl[0] == 0) g.fill(Math.round(scrX(gl[1])), vy, Math.round(scrX(gl[1])) + 1, vy + vh, 0xFFFF5AE0);
            else g.fill(vx, Math.round(scrY(gl[1])), vx + vw, Math.round(scrY(gl[1])) + 1, 0xFFFF5AE0);
        }
        Settings.Layer l = s.sel();
        Placement p = l == null ? null : s.placementOf(l);
        if (s.ghostsOn && l != null && l.random()) drawGhosts(g, l);
        if (p != null) {
            g.pose().pushPose();
            g.pose().translate(scrX(p.x + p.w / 2.0), scrY(p.y + p.h / 2.0), 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.rot));
            int hw = Math.round(sc * p.w / 2f), hh = Math.round(sc * p.h / 2f);
            int col = l.visible ? Kit.ACCENT : 0xFF888888;
            g.fill(-hw, -hh, hw, -hh + 1, col);
            g.fill(-hw, hh - 1, hw, hh, col);
            g.fill(-hw, -hh, -hw + 1, hh, col);
            g.fill(hw - 1, -hh, hw, hh, col);
            g.pose().popPose();
            // handles
            double hx = p.w / 2.0, hy = p.h / 2.0;
            for (int cI = 0; cI < 4; cI++) {
                float[] c = corner(p, (cI % 2 == 0 ? -1 : 1) * hx, (cI < 2 ? -1 : 1) * hy);
                g.fill(Math.round(c[0]) - 2, Math.round(c[1]) - 2, Math.round(c[0]) + 3, Math.round(c[1]) + 3, 0xFFFFFFFF);
                g.fill(Math.round(c[0]) - 1, Math.round(c[1]) - 1, Math.round(c[0]) + 2, Math.round(c[1]) + 2, 0xFF30306A);
            }
            float[] top = corner(p, 0, -hy), rot = rotHandle(p);
            g.pose().pushPose();
            g.pose().translate(top[0], top[1], 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.rot));
            g.fill(0, -14, 1, 0, 0x99FFFFFF);
            g.pose().popPose();
            g.fill(Math.round(rot[0]) - 3, Math.round(rot[1]) - 3, Math.round(rot[0]) + 4, Math.round(rot[1]) + 4, 0xFFFFFFFF);
            g.fill(Math.round(rot[0]) - 2, Math.round(rot[1]) - 2, Math.round(rot[0]) + 3, Math.round(rot[1]) + 3, 0xFF6A3A9A);
        }
        g.pose().popPose();
    }

    /** thin outlines of the saved places of the other pool arts of the selected random layer (not the sample itself) */
    private void drawGhosts(GuiGraphics g, Settings.Layer l) {
        Arts.Art cur = s.sample(l);
        for (Arts.Art a : LayerEngine.poolOf(l)) {
            if (a == cur || !l.hasSavedSpot(a.id)) continue;
            Placement q = l.spot(a.id);
            if (q == null) continue;
            g.pose().pushPose();
            g.pose().translate(scrX(q.x + q.w / 2.0), scrY(q.y + q.h / 2.0), 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(q.rot));
            int hw = Math.round(sc * q.w / 2f), hh = Math.round(sc * q.h / 2f);
            int col = 0x88B0B0D0;
            // dotted look: short segments along every side
            for (int i = -hw; i < hw; i += 6) {
                int e = Math.min(hw, i + 3);
                g.fill(i, -hh, e, -hh + 1, col);
                g.fill(i, hh - 1, e, hh, col);
            }
            for (int i = -hh; i < hh; i += 6) {
                int e = Math.min(hh, i + 3);
                g.fill(-hw, i, -hw + 1, e, col);
                g.fill(hw - 1, i, hw, e, col);
            }
            g.pose().popPose();
            Font f = Minecraft.getInstance().font;
            String label = a.id;
            int lx = Math.round(scrX(q.x)), ly = Math.round(scrY(q.y));
            g.fill(lx, ly, lx + f.width(label) + 3, ly + 10, 0xAA101018);
            g.drawString(f, label, lx + 2, ly + 1, 0xFFC8C8E0, false);
        }
    }

    private float[] rotHandle(Placement p) {
        return corner(p, 0, -p.h / 2.0 - 14.0 / sc);
    }

    private void drawHandleHits(Kit kit) {
        Settings.Layer l = s.sel();
        Arts.Art a = l == null ? null : s.sample(l);
        Placement p = l == null ? null : s.placementOf(l);
        if (p == null || a == null) return;
        double hx = p.w / 2.0, hy = p.h / 2.0;
        for (int cI = 0; cI < 4; cI++) {
            final int sx = cI % 2 == 0 ? -1 : 1, sy = cI < 2 ? -1 : 1;
            float[] c = corner(p, sx * hx, sy * hy);
            Kit.Hit h = new Kit.Hit();
            h.x = Math.round(c[0]) - 4; h.y = Math.round(c[1]) - 4; h.w = 9; h.h = 9; h.id = "handle" + cI;
            h.tip = T.t("Drag to resize (keeps the proportions)");
            h.drag = (mx0, my0) -> new ScaleDrag(l, a, sx, sy, mx0, my0);
            kit.add(h);
        }
        float[] r = rotHandle(p);
        Kit.Hit h = new Kit.Hit();
        h.x = Math.round(r[0]) - 5; h.y = Math.round(r[1]) - 5; h.w = 11; h.h = 11; h.id = "handle-rot";
        h.tip = T.t("Drag to turn (Shift = steps of 15 degrees)");
        h.drag = (mx0, my0) -> new RotDrag(l, a, mx0, my0);
        kit.add(h);
    }

    private void drawBadge(GuiGraphics g, Kit kit) {
        Settings.Layer l = s.sel();
        String t;
        if (s.playerView) t = T.t("Player view (Tab to go back)");
        else if (l != null && l.random()) {
            Arts.Art a = s.sample(l);
            if (a == null) return;
            t = T.f("Editing %s - %s", a.id, LayerEngine.isAuto(l, a) ? T.t("automatic position, drag to save") : T.t("position saved"));
        } else return;
        int w = Math.min(vw - 8, kit.font.width(t) + 8);
        g.pose().pushPose();
        g.pose().translate(0, 0, 320);
        g.fill(vx + 3, vy + 3, vx + 3 + w, vy + 15, 0xB0101018);
        Ui.drawFit(g, kit.font, t, vx + 7, vy + 3, w - 8, 12, false, 0xFFDDDDEE);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ main menu preview

    private void drawTitle(GuiGraphics g, Kit kit, float pt) {
        kit.rect(vx, vy, vw, vh, 0xFF0A0A10);
        if (!drawRealTitle(g, kit, pt)) {
            kit.text(T.t("The main menu preview is not available"), vx + 8, vy + 8, Kit.DIM);
        } else {
            titleHandles(g, kit);
        }
        kit.text(T.t("Main menu: drag the logo or the mascot; the rest is on the right"), vx + 4, vy + vh - 11, 0xFF9A9AB8);
    }

    // preview geometry of the last frame: screen px = origin + k * gui px
    private float tOx, tOy, tK = 1f;

    /** drag handles on the main menu preview: the mascot moves freely, the logo moves up / down together with the buttons */
    private void titleHandles(GuiGraphics g, Kit kit) {
        if (s.playerView || !s.st().enabled) return;
        Settings.Title t = s.st().title;
        int sw = s.width;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        int[] lr = TitleHooks.logoRect(sw);
        titleFrame(g, kit, lr, "title-logo", T.t("Drag up / down: the logo and the buttons move together"), (mx0, my0) -> {
            s.hist(false);
            final int start = t.logoShift;
            return new Kit.Drag() {
                @Override
                public void move(double mx, double my) {
                    t.logoShift = Math.max(0, Math.min(140, start + (int) Math.round((my - my0) / tK)));
                    s.touch();
                }
            };
        }, 0);
        if (t.mascot) {
            int[] mr = TitleHooks.mascotRect(sw);
            titleFrame(g, kit, mr, "title-mascot", T.t("Drag to move the mascot"), (mx0, my0) -> {
                s.hist(false);
                final int sx = t.mascotDx, sy = t.mascotDy;
                return new Kit.Drag() {
                    @Override
                    public void move(double mx, double my) {
                        t.mascotDx = Math.max(-999, Math.min(999, sx + (int) Math.round((mx - mx0) / tK)));
                        t.mascotDy = Math.max(-999, Math.min(999, sy + (int) Math.round((my - my0) / tK)));
                        s.touch();
                    }
                };
            }, 1);
        }
        g.pose().popPose();
    }

    private void titleFrame(GuiGraphics g, Kit kit, int[] r, String id, String tip, Kit.DragStart drag, int z) {
        int x = Math.round(tOx + tK * r[0]), y = Math.round(tOy + tK * r[1]);
        int w = Math.max(6, Math.round(tK * r[2])), h = Math.max(6, Math.round(tK * r[3]));
        Kit.Hit hit = new Kit.Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.id = id; hit.z = z;
        hit.tip = tip;
        hit.drag = drag;
        kit.add(hit);
        Kit.Hit top = kit.hitAt(kit.mx, kit.my);
        boolean hot = top != null && top.id.equals(id) || kit.dragging() && kit.pressedId().equals(id);
        int col = hot ? Kit.ACCENT : 0x66FFFFFF;
        kit.border(x, y, w, h, col);
        if (hot) {
            for (int c = 0; c < 4; c++) {
                int cx = x + (c % 2) * (w - 1), cy = y + (c / 2) * (h - 1);
                g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
            }
        }
    }

    private boolean drawRealTitle(GuiGraphics g, Kit kit, float pt) {
        if (titleFailed) return false;
        Minecraft mc = Minecraft.getInstance();
        int vw2 = s.width, vh2 = s.height;
        float k = Math.min((float) vw / vw2, (float) (vh - 14) / vh2);
        float ox = vx + (vw - k * vw2) / 2f, oy = vy + (vh - 14 - k * vh2) / 2f;
        tOx = ox; tOy = oy; tK = k;
        boolean pushed = false, scissored = false;
        try {
            TitleHooks.previewing = true;
            TitleHooks.previewArt = s.libArt;
            if (titleScr == null) titleScr = new net.minecraft.client.gui.screens.TitleScreen(false);
            if (vw2 != tpW || vh2 != tpH || s.st().title.logoShift != tpShift || s.st().enabled != tpEnabled) {
                titleScr.init(mc, vw2, vh2);
                try {
                    NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(
                            s, new ArrayList<>(s.children()), w -> { }, w -> { }));
                } catch (Throwable ignored) { }
                tpW = vw2; tpH = vh2; tpShift = s.st().title.logoShift; tpEnabled = s.st().enabled;
            }
            g.enableScissor(Math.round(ox), Math.round(oy), Math.round(ox + k * vw2), Math.round(oy + k * vh2));
            scissored = true;
            g.pose().pushPose();
            pushed = true;
            g.pose().translate(ox, oy, 0);
            g.pose().scale(k, k, 1f);
            titleScr.render(g, -9999, -9999, pt);
            TitleHooks.drawMascot(g, vw2);
            return true;
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] could not render the main menu preview", t);
            titleFailed = true;
            return false;
        } finally {
            TitleHooks.previewing = false;
            if (pushed) g.pose().popPose();
            if (scissored) g.disableScissor();
            RenderSystem.disableBlend();
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    // ------------------------------------------------------------------ menus

    private void openCanvasMenu() {
        List<Kit.Item> l = new ArrayList<>();
        l.add(Kit.Item.of(T.t("Add the selected art as a layer"), () -> s.addLayer(false)).icon(Kit.PLUS));
        l.add(Kit.Item.of(T.t("Add a random layer"), () -> s.addLayer(true)).icon(Kit.DICE));
        l.add(Kit.Item.sep());
        l.add(Kit.Item.of(T.t("Copy the layers of this menu"), s::copyLayers).icon(Kit.COPY));
        Kit.Item pa = Kit.Item.of(T.t("Paste the copied layers (add)"), () -> s.pasteLayers(false));
        Kit.Item pr = Kit.Item.of(T.t("Paste the copied layers (replace)"), () -> s.pasteLayers(true));
        if (!s.canPaste()) { pa.off(T.t("Copy the layers of a menu first")); pr.off(T.t("Copy the layers of a menu first")); }
        l.add(pa);
        l.add(pr);
        l.add(Kit.Item.sep());
        l.add(Kit.Item.of(T.t("Fit the menu in the view"), this::fit).icon(Kit.FIT));
        l.add(Kit.Item.of(T.t("Reset this menu to one random layer"), s::resetMenuDialog).icon(Kit.RESET));
        s.kit.openPopup(s.kit.mx, s.kit.my, l);
    }

    // ------------------------------------------------------------------ drags

    private Kit.Drag beginPick(double mx0, double my0) {
        int i = pickLayer(mx0, my0);
        Settings.Layer l;
        if (i >= 0) {
            l = s.layers().get(i);
        } else {
            // a hidden layer that is still selected can be grabbed by its frame
            Settings.Layer cur = s.sel();
            Arts.Art ca = cur == null ? null : s.sample(cur);
            Placement cp = cur == null || cur.visible || ca == null ? null : s.placementOf(cur);
            if (cp == null || !inPlace(cp, mx0, my0)) { s.deselect(); return null; }
            l = cur;
        }
        s.selectLayer(l.id);
        Arts.Art a = s.sample(l);
        Placement start = s.placementOf(l);
        if (a == null || start == null) return null;
        return new MoveDrag(l, a, start, mx0, my0);
    }

    private final class MoveDrag implements Kit.Drag {
        final Settings.Layer l;
        final Arts.Art a;
        final Placement start;
        final double mx0, my0;
        boolean moved;

        MoveDrag(Settings.Layer l, Arts.Art a, Placement start, double mx0, double my0) {
            this.l = l; this.a = a; this.start = start; this.mx0 = mx0; this.my0 = my0;
        }

        @Override
        public void move(double mx, double my) {
            if (!moved && Math.abs(mx - mx0) + Math.abs(my - my0) < 3) return;
            if (!moved) { moved = true; s.hist(false); }
            Placement p = new Placement(start.x, start.y, start.w, start.h);
            p.rot = start.rot;
            p.flip = start.flip;
            p.flipY = start.flipY;
            p.x = start.x + (int) Math.round((mx - mx0) / sc);
            p.y = start.y + (int) Math.round((my - my0) / sc);
            guides.clear();
            if (s.snapOn && !Screen.hasAltDown()) snap(p, l);
            clamp(p);
            s.store(l, a, p);
            s.touch();
        }

        @Override
        public void end(double mx, double my) { guides.clear(); }
    }

    private void snap(Placement p, Settings.Layer self) {
        Masks.Mask m = s.mask();
        if (m == null) return;
        float thr = 6f / sc;
        List<Float> tx = new ArrayList<>(), ty = new ArrayList<>();
        tx.add((float) Placement.INSET); tx.add(m.w / 2f); tx.add((float) (m.w - Placement.INSET));
        ty.add((float) Placement.INSET); ty.add(m.h / 2f); ty.add((float) (m.h - Placement.INSET));
        for (Settings.Layer o : s.layers()) {
            if (o == self || !o.visible) continue;
            Placement op = s.placementOf(o);
            if (op == null) continue;
            Placement b = op.bounds();
            tx.add((float) b.x); tx.add(b.x + b.w / 2f); tx.add((float) (b.x + b.w));
            ty.add((float) b.y); ty.add(b.y + b.h / 2f); ty.add((float) (b.y + b.h));
        }
        Placement b = p.bounds();
        float[] ex = { b.x, b.x + b.w / 2f, b.x + b.w }, ey = { b.y, b.y + b.h / 2f, b.y + b.h };
        float bestX = thr, bestY = thr, dxs = 0, dys = 0, gx = 0, gy = 0;
        boolean hx = false, hy = false;
        for (float e : ex) for (float t : tx) if (Math.abs(t - e) < bestX) { bestX = Math.abs(t - e); dxs = t - e; gx = t; hx = true; }
        for (float e : ey) for (float t : ty) if (Math.abs(t - e) < bestY) { bestY = Math.abs(t - e); dys = t - e; gy = t; hy = true; }
        if (hx) { p.x += Math.round(dxs); guides.add(new float[] { 0, gx }); }
        if (hy) { p.y += Math.round(dys); guides.add(new float[] { 1, gy }); }
    }

    private final class ScaleDrag implements Kit.Drag {
        final Settings.Layer l;
        final Arts.Art a;
        final int sx, sy;
        final Placement start;
        final double mx0, my0;
        boolean moved;

        ScaleDrag(Settings.Layer l, Arts.Art a, int sx, int sy, double mx0, double my0) {
            this.l = l; this.a = a; this.sx = sx; this.sy = sy; this.mx0 = mx0; this.my0 = my0;
            Placement p = s.placementOf(l);
            this.start = p == null ? new Placement(0, 0, 10, 10) : p;
        }

        @Override
        public void move(double mx, double my) {
            if (!moved && Math.abs(mx - mx0) + Math.abs(my - my0) < 3) return;
            if (!moved) { moved = true; s.hist(false); }
            double r = Math.toRadians(start.rot), c = Math.cos(r), sn = Math.sin(r);
            double cx = start.x + start.w / 2.0, cy = start.y + start.h / 2.0;
            // the opposite corner stays where it is
            double ax = cx + (-sx * start.w / 2.0) * c - (-sy * start.h / 2.0) * sn;
            double ay = cy + (-sx * start.w / 2.0) * sn + (-sy * start.h / 2.0) * c;
            double dx = toPanelX(mx) - ax, dy = toPanelY(my) - ay;
            double du = dx * c + dy * sn, dv = -dx * sn + dy * c;
            double k = Math.max(du * sx / start.w, dv * sy / start.h);
            k = Math.max(8.0 / Math.min(start.w, start.h), Math.min(k, 1200.0 / Math.max(start.w, start.h)));
            double nw = k * start.w, nh = k * start.h;
            double ncx = ax + (sx * nw / 2.0) * c - (sy * nh / 2.0) * sn;
            double ncy = ay + (sx * nw / 2.0) * sn + (sy * nh / 2.0) * c;
            Placement p = new Placement((int) Math.round(ncx - nw / 2.0), (int) Math.round(ncy - nh / 2.0), (int) Math.round(nw), (int) Math.round(nh));
            p.rot = start.rot;
            p.flip = start.flip;
            p.flipY = start.flipY;
            clamp(p);
            s.store(l, a, p);
            s.touch();
        }
    }

    private final class RotDrag implements Kit.Drag {
        final Settings.Layer l;
        final Arts.Art a;
        final Placement start;
        final double mx0, my0;
        boolean moved;

        RotDrag(Settings.Layer l, Arts.Art a, double mx0, double my0) {
            this.l = l; this.a = a; this.mx0 = mx0; this.my0 = my0;
            Placement p = s.placementOf(l);
            this.start = p == null ? new Placement(0, 0, 10, 10) : p;
        }

        @Override
        public void move(double mx, double my) {
            if (!moved && Math.abs(mx - mx0) + Math.abs(my - my0) < 3) return;
            if (!moved) { moved = true; s.hist(false); }
            double cx = scrX(start.x + start.w / 2.0), cy = scrY(start.y + start.h / 2.0);
            double deg = Math.toDegrees(Math.atan2(my - cy, mx - cx)) + 90.0;
            if (Screen.hasShiftDown()) deg = Math.round(deg / 15.0) * 15.0;
            int rot = (int) Math.round(deg);
            while (rot > 180) rot -= 360;
            while (rot < -180) rot += 360;
            Placement p = new Placement(start.x, start.y, start.w, start.h);
            p.rot = rot;
            p.flip = start.flip;
            p.flipY = start.flipY;
            s.store(l, a, p);
            s.touch();
        }
    }

    private final class PanDrag implements Kit.Drag {
        double lastX, lastY;

        PanDrag(double mx, double my) { lastX = mx; lastY = my; }

        @Override
        public void move(double mx, double my) {
            panX += (float) (mx - lastX);
            panY += (float) (my - lastY);
            lastX = mx;
            lastY = my;
            fit = false;
        }
    }
}
