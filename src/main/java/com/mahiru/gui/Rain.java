package com.mahiru.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Falling arts: every drop has its own size, speed, tilt and spin. */
public final class Rain {
    private static final class Drop {
        Arts.Art art;
        float x, y, vy, h, rot, spin, alpha;
    }

    private final List<Drop> drops = new ArrayList<>();
    private final Random rng = new Random();
    private long last = 0;
    private float acc = 0;
    private boolean primed = false;

    public void reset() {
        drops.clear();
        last = 0;
        acc = 0;
        primed = false;
    }

    private Drop spawn(int w, int h, Settings.Title t, boolean anywhere) {
        Arts.Art a = Arts.pick("rain");
        if (a == null) return null;
        Drop d = new Drop();
        d.art = a;
        d.h = (28f + rng.nextFloat() * 72f) * t.rainSize;
        d.vy = (18f + rng.nextFloat() * 110f) * t.rainSpeed * (0.6f + 0.4f * d.h / 60f);
        d.x = (rng.nextFloat() * 1.1f - 0.05f) * w;
        d.y = anywhere ? rng.nextFloat() * h : -d.h;
        d.rot = (rng.nextFloat() - 0.5f) * 30f;
        d.spin = (rng.nextFloat() - 0.5f) * 24f;
        d.alpha = (0.55f + rng.nextFloat() * 0.45f) * t.rainOpacity;
        return d;
    }

    public void update(int w, int h, Settings.Title t) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        // rain rules: freeze while the game window is not focused
        if (!net.minecraft.client.Minecraft.getInstance().isWindowActive()) dt = 0f;
        if (!primed) {
            primed = true;
            int n = Math.min(300, Math.max(1, t.rainMax / 2));
            for (int i = 0; i < n; i++) {
                if (!t.insane() && areaUsed() >= 0.45f * w * h) break;
                Drop d = spawn(w, h, t, true);
                if (d == null) break;   // nothing to rain (empty catalogue)
                drops.add(d);
            }
        }
        acc += dt * t.rainRate;
        while (acc >= 1f) {
            acc -= 1f;
            // rain rules: at most t.rainMax drops AND at most ~45% of the screen area covered by drops
            if (drops.size() < t.rainMax && (t.insane() || areaUsed() < 0.45f * w * h)) { Drop d = spawn(w, h, t, false); if (d != null) drops.add(d); else acc = 0; }
        }
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop d = drops.get(i);
            d.y += d.vy * dt;
            d.rot += d.spin * dt;
            if (d.y - d.h > h) drops.remove(i);
        }
        while (drops.size() > t.rainMax) drops.remove(0);
    }

    private float areaUsed() {
        float a = 0;
        for (Drop d : drops) a += d.h * d.h * d.art.sw / d.art.sh;
        return a;
    }

    public void render(GuiGraphics g) {
        drops.sort((a, b) -> Float.compare(a.h, b.h));
        RenderSystem.enableBlend();
        for (Drop d : drops) {
            Arts.Art a = d.art;
            float w = d.h * a.sw / a.sh;
            g.pose().pushPose();
            g.pose().translate(d.x, d.y, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(d.rot));
            g.setColor(1f, 1f, 1f, d.alpha);
            a.drawSmall(g, Math.round(-w / 2), Math.round(-d.h / 2), Math.round(w), Math.round(d.h), Settings.get().title.flipArt);
            g.pose().popPose();
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
