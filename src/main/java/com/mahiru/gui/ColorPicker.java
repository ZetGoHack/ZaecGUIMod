package com.mahiru.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.function.IntConsumer;

/**
 * A colour picker window: a hue/saturation wheel, a brightness bar, a hex field and a few ready colours.
 * Drawn by {@link Studio} above everything except dialogs; a click outside closes it.
 */
public final class ColorPicker {
    private static final int R = 44, SIZE = R * 2;
    private static final ResourceLocation WHEEL = ZaecGuiMod.id("generated/color_wheel");
    private static boolean wheelReady;

    private static final int[] PRESETS = {
            0xFFF3DE, 0xF2C7A0, 0xFFD6E0, 0xCDE7F0, 0xCFE8C8, 0xD9CCF0, 0x2B2D42, 0x14141C, 0xFFFFFF, 0x000000 };

    private final Kit k;
    private boolean open;
    private float hue, sat = 1f, val = 1f;
    private int ax, ay;
    private IntConsumer set;
    private Runnable begin;
    private String hexShown = "";

    ColorPicker(Kit k) { this.k = k; }

    public boolean isOpen() { return open; }

    /** @param rgb the colour now (0xRRGGBB); {@code onChange} gets every new colour, {@code begin} runs before each change (undo) */
    public void open(int ax, int ay, int rgb, Runnable begin, IntConsumer onChange) {
        this.ax = ax;
        this.ay = ay;
        this.set = onChange;
        this.begin = begin;
        float[] hsv = rgbToHsv(rgb);
        hue = hsv[0]; sat = hsv[1]; val = hsv[2];
        open = true;
        k.modalZ = 250;
        hexShown = hex(rgb);
        k.setValue("cp-hex", hexShown);
    }

    public void close() {
        if (!open) return;
        open = false;
        k.modalZ = 0;
        if ("cp-hex".equals(k.focusedInput())) k.setFocus("");
    }

    private int current() { return hsvToRgb(hue, sat, val); }

    private void apply() {
        if (begin != null) begin.run();
        int c = current();
        hexShown = hex(c);
        k.setValue("cp-hex", hexShown);
        if (set != null) set.accept(c);
    }

    private static String hex(int rgb) { return String.format("%06X", rgb & 0xFFFFFF); }

    public void draw(int sw, int sh) {
        if (!open) return;
        GuiGraphics g = k.g;
        ensureWheel();
        int w = SIZE + 24, h = SIZE + 8 + 10 + 6 + 14 + 6 + 28 + 14;
        int x = Math.max(4, Math.min(sw - w - 4, ax)), y = Math.max(4, Math.min(sh - h - 4, ay));
        int saveZ = 300;
        k.zLevel(saveZ);
        Kit.Hit back = new Kit.Hit();
        back.x = 0; back.y = 0; back.w = sw; back.h = sh; back.id = "cp-back";
        back.press = this::close;
        k.add(back);
        k.zLevel(saveZ + 1);
        Kit.Hit box = new Kit.Hit();
        box.x = x; box.y = y; box.w = w; box.h = h; box.id = "cp-box";
        k.add(box);
        g.pose().pushPose();
        g.pose().translate(0, 0, 600);
        k.rect(x - 1, y - 1, w + 2, h + 2, Kit.EDGE_HI);
        k.rect(x, y, w, h, 0xFF16161E);
        int wx = x + 12, wy = y + 6;
        // the wheel
        g.blit(WHEEL, wx, wy, 0, 0, SIZE, SIZE, SIZE, SIZE);
        double ang = hue * Math.PI * 2;
        int mxp = wx + R + (int) Math.round(Math.cos(ang) * sat * (R - 1)), myp = wy + R + (int) Math.round(Math.sin(ang) * sat * (R - 1));
        marker(g, mxp, myp);
        Kit.Hit wheel = new Kit.Hit();
        wheel.x = wx; wheel.y = wy; wheel.w = SIZE; wheel.h = SIZE; wheel.id = "cp-wheel";
        wheel.tip = T.t("Hue and saturation");
        wheel.drag = (mx0, my0) -> {
            pickWheel(wx + R, wy + R, mx0, my0);
            return (mx1, my1) -> pickWheel(wx + R, wy + R, mx1, my1);
        };
        k.add(wheel);
        // brightness
        int by = wy + SIZE + 6;
        for (int i = 0; i < SIZE; i++) k.rect(wx + i, by, 1, 8, 0xFF000000 | hsvToRgb(hue, sat, i / (float) (SIZE - 1)));
        int vx = wx + Math.round(val * (SIZE - 1));
        k.rect(vx - 1, by - 2, 3, 12, 0xFFFFFFFF);
        k.rect(vx, by - 1, 1, 10, 0xFF101018);
        Kit.Hit bar = new Kit.Hit();
        bar.x = wx; bar.y = by - 2; bar.w = SIZE; bar.h = 12; bar.id = "cp-bright";
        bar.tip = T.t("Brightness");
        bar.drag = (mx0, my0) -> {
            pickBright(wx, mx0);
            return (mx1, my1) -> pickBright(wx, mx1);
        };
        k.add(bar);
        // hex + swatch
        int hy = by + 16;
        k.rect(wx, hy, 16, 12, 0xFF000000 | current());
        k.border(wx, hy, 16, 12, 0xFF8A8AB0);
        k.text("#", wx + 20, hy + 2, Kit.DIM);
        k.input("cp-hex", wx + 28, hy, SIZE - 28, 12, "RRGGBB", v -> {
            String t = v.trim().replace("#", "");
            if (t.length() == 6 && t.matches("[0-9a-fA-F]{6}") && !t.equalsIgnoreCase(hexShown)) {
                int c = Integer.parseInt(t, 16);
                float[] hsv = rgbToHsv(c);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                hexShown = hex(c);
                if (begin != null) begin.run();
                if (set != null) set.accept(c);
            }
        });
        // ready colours
        int py = hy + 18;
        for (int i = 0; i < PRESETS.length; i++) {
            final int c = PRESETS[i];
            int px = wx + (i % 5) * 17, pyy = py + (i / 5) * 14;
            Kit.Hit ph = new Kit.Hit();
            ph.x = px; ph.y = pyy; ph.w = 15; ph.h = 12; ph.id = "cp-p" + i; ph.tip = "#" + hex(c);
            ph.press = () -> {
                float[] hsv = rgbToHsv(c);
                hue = hsv[0]; sat = hsv[1]; val = hsv[2];
                apply();
            };
            k.add(ph);
            k.rect(px, pyy, 15, 12, 0xFF000000 | c);
            k.border(px, pyy, 15, 12, 0xFF55556A);
        }
        k.b(wx + 5 * 17 + 2, py, SIZE - 5 * 17 - 2, 12).label(T.t("OK")).id("cp-ok").press(this::close).show();
        g.pose().popPose();
        k.zLevel(0);
    }

    private void marker(GuiGraphics g, int x, int y) {
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xFFFFFFFF);
        g.fill(x - 2, y - 2, x + 3, y + 3, 0xFF000000 | current());
    }

    private void pickWheel(int cx, int cy, double mx, double my) {
        double dx = mx - cx, dy = my - cy;
        double r = Math.sqrt(dx * dx + dy * dy);
        sat = (float) Math.min(1.0, r / (R - 1));
        hue = (float) ((Math.atan2(dy, dx) / (Math.PI * 2) + 1.0) % 1.0);
        apply();
    }

    private void pickBright(int wx, double mx) {
        val = (float) Math.max(0.0, Math.min(1.0, (mx - wx) / (SIZE - 1)));
        apply();
    }

    /** the wheel is a small generated texture: full brightness, hue around, saturation outwards */
    private static void ensureWheel() {
        if (wheelReady) return;
        NativeImage img = new NativeImage(SIZE, SIZE, true);
        for (int yy = 0; yy < SIZE; yy++) {
            for (int xx = 0; xx < SIZE; xx++) {
                double dx = xx - R + 0.5, dy = yy - R + 0.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                int abgr = 0;
                if (r <= R - 0.5) {
                    float hh = (float) ((Math.atan2(dy, dx) / (Math.PI * 2) + 1.0) % 1.0);
                    int rgb = hsvToRgb(hh, (float) Math.min(1.0, r / (R - 1)), 1f);
                    abgr = 0xFF000000 | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
                }
                img.setPixelRGBA(xx, yy, abgr);
            }
        }
        Minecraft.getInstance().getTextureManager().register(WHEEL, new DynamicTexture(img));
        wheelReady = true;
    }

    // ---- colour maths

    public static int hsvToRgb(float h, float s, float v) {
        h = ((h % 1f) + 1f) % 1f;
        float c = v * s, hp = h * 6f, x = c * (1f - Math.abs(hp % 2f - 1f)), m = v - c;
        float r, gg, b;
        switch ((int) hp) {
            case 0 -> { r = c; gg = x; b = 0; }
            case 1 -> { r = x; gg = c; b = 0; }
            case 2 -> { r = 0; gg = c; b = x; }
            case 3 -> { r = 0; gg = x; b = c; }
            case 4 -> { r = x; gg = 0; b = c; }
            default -> { r = c; gg = 0; b = x; }
        }
        int ri = Math.round((r + m) * 255), gi = Math.round((gg + m) * 255), bi = Math.round((b + m) * 255);
        return (ri << 16) | (gi << 8) | bi;
    }

    public static float[] rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = ((b - r) / d + 2f) / 6f;
        else h = ((r - g) / d + 4f) / 6f;
        if (h < 0) h += 1f;
        return new float[] { h, max == 0 ? 0 : d / max, max };
    }
}
