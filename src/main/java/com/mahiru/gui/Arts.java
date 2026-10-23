package com.mahiru.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Art catalogue: big textures for the title screen, small ones (+ alpha maps) for container menus. */
public final class Arts {
    public static final class Art {
        public final String id;
        public final int w, h, sw, sh;
        public final String anchor;
        public final ResourceLocation big, small;
        public final boolean imported;
        private boolean bigReg, smallReg;
        private volatile float[][] alpha;   // [ALPHA_H][alphaW] coverage 0..1, built lazily

        Art(String id, int w, int h, int sw, int sh, String anchor) {
            this.id = id; this.w = w; this.h = h; this.sw = sw; this.sh = sh; this.anchor = anchor;
            this.big = ZaecGuiMod.id("textures/art/" + id + ".png");
            this.small = ZaecGuiMod.id("textures/art/s" + id.substring(1) + ".png");
            this.imported = false;
        }

        /** An imported art: textures are created from config/zaecguimod/imports on first use. */
        Art(Imports.Entry e) {
            this.id = e.id; this.w = e.w; this.h = e.h; this.sw = e.sw; this.sh = e.sh; this.anchor = "center";
            this.big = ZaecGuiMod.id("imported/" + e.id + "_b");
            this.small = ZaecGuiMod.id("imported/" + e.id + "_s");
            this.imported = true;
        }

        private static void register(ResourceLocation loc, java.nio.file.Path file) {
            try (InputStream in = java.nio.file.Files.newInputStream(file)) {
                NativeImage img = NativeImage.read(NativeImage.Format.RGBA, in);
                net.minecraft.client.renderer.texture.DynamicTexture t = new net.minecraft.client.renderer.texture.DynamicTexture(img);
                t.setFilter(true, false);
                Minecraft.getInstance().getTextureManager().register(loc, t);
            } catch (Throwable ex) {
                Guard.LOG.error("[ZaecGuiMod] could not load imported texture {}", file, ex);
            }
        }

        public boolean flipped() { return false; }

        public synchronized ResourceLocation small() {
            if (imported && !smallReg) { smallReg = true; register(small, Imports.smallFile(id)); }
            return small;
        }

        public synchronized ResourceLocation big() {
            if (imported && !bigReg) { bigReg = true; register(big, Imports.bigFile(id)); }
            return big;
        }

        /** Draws the whole art (small texture) into the box, mirrored when the art is flipped. */
        public void drawSmall(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h) {
            drawSmall(g, x, y, w, h, flipped());
        }

        public void drawSmall(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, boolean flip) {
            drawSmall(g, x, y, w, h, flip, false);
        }

        /** A negative texture size mirrors that axis. */
        public void drawSmall(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, boolean flip, boolean flipY) {
            g.blit(small(), x, y, w, h, flip ? sw : 0, flipY ? sh : 0, flip ? -sw : sw, flipY ? -sh : sh, sw, sh);
        }

        public void drawBig(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h) {
            drawBig(g, x, y, w, h, flipped());
        }

        public void drawBig(net.minecraft.client.gui.GuiGraphics g, int x, int y, int w, int h, boolean flip) {
            if (flip) g.blit(big(), x, y, w, h, this.w, 0, -this.w, this.h, this.w, this.h);
            else g.blit(big(), x, y, w, h, 0, 0, this.w, this.h, this.w, this.h);
        }

        public static final int ALPHA_H = 48;

        /** Low-res alpha map of the small texture; width follows aspect ratio. */
        public float[][] alpha() {
            float[][] cached = alpha;
            if (cached != null) return cached;
            boolean ok = true;
            int aw = Math.max(1, Math.round(ALPHA_H * (float) sw / sh));
            float[][] a = new float[ALPHA_H][aw];
            try (var res = imported ? java.nio.file.Files.newInputStream(Imports.smallFile(id))
                         : Minecraft.getInstance().getResourceManager().open(small);
                 NativeImage img = NativeImage.read(res)) {
                for (int y = 0; y < ALPHA_H; y++) {
                    for (int x = 0; x < aw; x++) {
                        int x0 = x * img.getWidth() / aw, x1 = Math.max(x0 + 1, (x + 1) * img.getWidth() / aw);
                        int y0 = y * img.getHeight() / ALPHA_H, y1 = Math.max(y0 + 1, (y + 1) * img.getHeight() / ALPHA_H);
                        long sum = 0; int n = 0;
                        for (int yy = y0; yy < y1; yy += 3) for (int xx = x0; xx < x1; xx += 3) {
                            sum += (img.getPixelRGBA(xx, yy) >>> 24) & 0xFF; n++;
                        }
                        a[y][x] = n == 0 ? 0 : sum / (255f * n);
                    }
                }
            } catch (Exception e) {
                ok = false;
                for (float[] row : a) java.util.Arrays.fill(row, 0.5f);
            }
            if (ok) alpha = a;
            return a;
        }
    }

    private static volatile List<Art> all;
    private static final Random RNG = new Random();
    private static final Map<String, String> last = new HashMap<>();

    /** Forget the catalogue (resource reload); it is rebuilt on next use. */
    public static synchronized void invalidate() { all = null; retryAt = 0; }

    /** when the catalogue could not be read: not before this time (ms) is it tried again, so a broken pack costs no I/O per frame */
    private static long retryAt;

    public static synchronized List<Art> all() {
        List<Art> cur = all;
        if (cur != null && (retryAt == 0 || System.currentTimeMillis() < retryAt)) return cur;
        List<Art> list = new ArrayList<>();
        boolean parsed = false;
        try (InputStream in = Minecraft.getInstance().getResourceManager()
                .open(ZaecGuiMod.id("textures/art/arts.json"))) {
            JsonArray arr = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            for (JsonElement el : arr) {
                JsonObject o = el.getAsJsonObject();
                String anchor = o.has("anchor") && !o.get("anchor").isJsonNull() ? o.get("anchor").getAsString() : "center";
                list.add(new Art(o.get("id").getAsString(), o.get("w").getAsInt(), o.get("h").getAsInt(),
                        o.get("sw").getAsInt(), o.get("sh").getAsInt(), anchor));
            }
            parsed = true;
        } catch (java.io.FileNotFoundException e) {
            parsed = true;   // the mod ships without pictures: the library is whatever the player imported
        } catch (Exception e) {
            Guard.LOG.error("[ZaecGuiMod] could not load the art catalogue", e);
        }
        for (Imports.Entry e : Imports.load()) list.add(new Art(e));
        // an unreadable catalogue with nothing imported is remembered for a few seconds, then tried again
        // (a lite build ships an empty catalogue on purpose: that parses fine and is cached for good)
        retryAt = !list.isEmpty() || parsed ? 0 : System.currentTimeMillis() + 5000;
        all = list;
        return list;
    }

    /** Frees the GPU textures of a deleted import. */
    public static void releaseTextures(String id) {
        var tm = Minecraft.getInstance().getTextureManager();
        tm.release(ZaecGuiMod.id("imported/" + id + "_b"));
        tm.release(ZaecGuiMod.id("imported/" + id + "_s"));
    }

    public static Art byId(String id) {
        for (Art a : all()) if (a.id.equals(id)) return a;
        return null;
    }

    /** Random art from the rotation pool, never the same one twice in a row for that key. */
    public static synchronized Art pick(String key) {
        List<Art> pool = new ArrayList<>();
        Settings st = Settings.get();
        for (Art a : all()) if (st.inRotation(a.id)) pool.add(a);
        if (pool.isEmpty()) return null;
        Art a;
        int tries = 0;
        do { a = pool.get(RNG.nextInt(pool.size())); tries++; }
        while (pool.size() > 1 && a.id.equals(last.get(key)) && tries < 8);
        last.put(key, a.id);
        return a;
    }

    private Arts() {}
}
