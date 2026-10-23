package com.mahiru.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Drag&drop import of PNG / JPG / GIF (first frame) files as extra arts.
 * Decoding and scaling run on a worker thread; results are stored under config/zaecguimod/imports.
 */
public final class Imports {
    public static final class Entry {
        public String id;
        public int w, h, sw, sh;
        public String name = "";
    }

    public static final class Result {
        public final List<String> imported = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public int duplicates;
    }

    public static final long MAX_FILE = 25L * 1024 * 1024;
    public static final int MAX_DIM = 8192, BIG_H = 1080, SMALL_H = 380, MIN_SIDE = 16, MAX_ARTS = 300;
    private static final float MAX_ASPECT = 6f;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Fail extends Exception {
        Fail(String m) { super(m); }
    }

    public static Path dir() { return Settings.configDir().resolve("imports"); }
    private static Path lib() { return dir().resolve("library.json"); }
    public static Path bigFile(String id) { return dir().resolve(id + "_b.png"); }
    public static Path smallFile(String id) { return dir().resolve(id + "_s.png"); }

    public static synchronized List<Entry> load() {
        List<Entry> out = new ArrayList<>();
        Path f = lib();
        if (!Files.exists(f)) return out;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            List<Entry> l = GSON.fromJson(r, new TypeToken<List<Entry>>() {}.getType());
            if (l != null) for (Entry e : l) {
                if (e == null || e.id == null || !e.id.matches("u_[0-9a-f]{12}") || e.w <= 0 || e.h <= 0 || e.sw <= 0 || e.sh <= 0) continue;
                if (!Files.exists(bigFile(e.id)) || !Files.exists(smallFile(e.id))) continue;
                out.add(e);
            }
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] could not read the import library", t);
        }
        return out;
    }

    public static void storeAll(List<Entry> list) throws Exception { store(list); }

    private static synchronized void store(List<Entry> list) throws Exception {
        Files.createDirectories(dir());
        Path tmp = dir().resolve("library.json.tmp");
        Files.writeString(tmp, GSON.toJson(list), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, lib(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, lib(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Imports the dropped files in the background; {@code done} is called on the game thread. */
    public static void importAsync(List<Path> paths, Consumer<Result> done) {
        Minecraft mc = Minecraft.getInstance();
        Thread t = new Thread(() -> {
            Result res = new Result();
            for (Path p : paths) {
                String name = p.getFileName() == null ? "?" : p.getFileName().toString();
                try {
                    Entry e = process(p, name, res);
                    if (e != null) res.imported.add(e.id);
                } catch (Fail f) {
                    res.errors.add(T.f("%s: %s", name, T.t(f.getMessage())));
                } catch (Throwable x) {
                    Guard.LOG.error("[ZaecGuiMod] import of {} failed", p, x);
                    res.errors.add(T.f("%s: %s", name, T.t("could not be read as an image")));
                }
            }
            mc.execute(() -> {
                if (!res.imported.isEmpty()) Arts.invalidate();
                done.accept(res);
            });
        }, "Zaec-Import");
        t.setDaemon(true);
        t.start();
    }

    private static Entry process(Path p, String name, Result res) throws Exception {
        String low = name.toLowerCase(Locale.ROOT);
        if (!(low.endsWith(".png") || low.endsWith(".jpg") || low.endsWith(".jpeg") || low.endsWith(".gif")))
            throw new Fail("unsupported file type (use PNG, JPG or GIF)");
        if (!Files.isRegularFile(p)) throw new Fail("not a file");
        long size = Files.size(p);
        if (size > MAX_FILE) throw new Fail("file is larger than 25 MB");
        byte[] data = Files.readAllBytes(p);
        String id = "u_" + hash(data);
        List<Entry> lib = load();
        for (Entry e : lib) if (e.id.equals(id)) { res.duplicates++; return null; }
        if (lib.size() >= MAX_ARTS) throw new Fail("too many imported arts");

        ByteBuffer buf = MemoryUtil.memAlloc(data.length);
        try {
            buf.put(data).flip();
            try (MemoryStack st = MemoryStack.stackPush()) {
                IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
                if (!STBImage.stbi_info_from_memory(buf, w, h, c)) throw new Fail("could not be read as an image");
                if (w.get(0) > MAX_DIM || h.get(0) > MAX_DIM || (long) w.get(0) * h.get(0) > 24_000_000L) throw new Fail("image is larger than 8192 px");
            }
        } finally {
            MemoryUtil.memFree(buf);
        }

        int[] px;
        int w, h;
        try (NativeImage img = NativeImage.read(NativeImage.Format.RGBA, new ByteArrayInputStream(data))) {
            w = img.getWidth();
            h = img.getHeight();
            px = img.getPixelsRGBA();
        }
        // trim fully transparent margins
        int x0 = w, y0 = h, x1 = -1, y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 8) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        if (x1 < 0) throw new Fail("image is fully transparent");
        int cw = x1 - x0 + 1, chh = y1 - y0 + 1;
        if (cw < MIN_SIDE || chh < MIN_SIDE) throw new Fail("image is too small (min 16 px)");
        float ratio = (float) chh / cw;
        if (ratio > MAX_ASPECT || ratio < 1f / MAX_ASPECT) throw new Fail("image proportions are too extreme");
        int[] crop = new int[cw * chh];
        for (int y = 0; y < chh; y++) System.arraycopy(px, (y0 + y) * w + x0, crop, y * cw, cw);

        int bh = Math.min(BIG_H, chh), bw = Math.max(1, Math.round(cw * (float) bh / chh));
        int sh = Math.min(SMALL_H, chh), sw = Math.max(1, Math.round(cw * (float) sh / chh));
        Files.createDirectories(dir());
        write(bigFile(id), scale(crop, cw, chh, bw, bh), bw, bh);
        write(smallFile(id), scale(crop, cw, chh, sw, sh), sw, sh);

        Entry e = new Entry();
        e.id = id; e.w = bw; e.h = bh; e.sw = sw; e.sh = sh; e.name = name;
        synchronized (Imports.class) {
            List<Entry> now = load();
            now.add(e);
            store(now);
        }
        return e;
    }

    private static String hash(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
        return sb.toString();
    }

    /** Box filter on premultiplied alpha (packed ABGR as NativeImage stores it). */
    private static int[] scale(int[] src, int sw, int sh, int dw, int dh) {
        if (sw == dw && sh == dh) return src;
        int[] out = new int[dw * dh];
        for (int y = 0; y < dh; y++) {
            int ya = y * sh / dh, yb = Math.max(ya + 1, (y + 1) * sh / dh);
            for (int x = 0; x < dw; x++) {
                int xa = x * sw / dw, xb = Math.max(xa + 1, (x + 1) * sw / dw);
                long a = 0, r = 0, g = 0, b = 0;
                int n = 0;
                for (int yy = ya; yy < yb; yy++) {
                    for (int xx = xa; xx < xb; xx++) {
                        int p = src[yy * sw + xx];
                        int pa = p >>> 24;
                        a += pa;
                        r += (long) (p & 0xFF) * pa;
                        g += (long) ((p >> 8) & 0xFF) * pa;
                        b += (long) ((p >> 16) & 0xFF) * pa;
                        n++;
                    }
                }
                int oa = (int) (a / n);
                int or = a == 0 ? 0 : (int) (r / a), og = a == 0 ? 0 : (int) (g / a), ob = a == 0 ? 0 : (int) (b / a);
                out[y * dw + x] = (oa << 24) | (ob << 16) | (og << 8) | or;
            }
        }
        return out;
    }

    private static void write(Path f, int[] px, int w, int h) throws Exception {
        try (NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false)) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setPixelRGBA(x, y, px[y * w + x]);
            out.writeToFile(f);
        }
    }

    /** Removes an imported art and its files. Returns false when it is not an import. */
    public static boolean delete(String id) {
        if (id == null || !id.startsWith("u_")) return false;
        try {
            synchronized (Imports.class) {
                List<Entry> now = load();
                now.removeIf(e -> e.id.equals(id));
                store(now);
            }
            Files.deleteIfExists(bigFile(id));
            Files.deleteIfExists(smallFile(id));
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] could not delete imported art {}", id, t);
            return false;
        }
        Arts.releaseTextures(id);
        Arts.invalidate();
        return true;
    }

    private Imports() {}
}
