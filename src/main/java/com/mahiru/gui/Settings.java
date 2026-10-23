package com.mahiru.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything the user configures; stored in config/zaecguimod/layout.json (format 2: every menu is a list of layers).
 * Files of the first format (placements / flips / pinned / extras) are converted on load.
 */
public final class Settings {
    public static final int MODE_BEHIND = 0;   // behind slot cells: slots stay crisp, art shows only in the gaps
    public static final int MODE_THROUGH = 1;  // art shows inside slot squares and gaps; slot outlines stay crisp
    public static final int MODE_PANEL = 2;    // above the panel and slot backgrounds, below items
    public static final int MODE_OVER = 3;     // above everything in the menu
    public static final String[] MODE_NAMES = { "Background (behind slots)", "Visible in slots", "On the window", "On top of all" };

    public static final String SRC_POOL = "pool";     // random art of the rotation pool
    public static final String SRC_GROUP = "group";   // random art of one group
    public static final String SRC_ART = "art";       // one fixed art
    public static final int MAX_LAYERS = 16;
    public static final String MAIN_ID = "main";
    /** gear position "not moved yet": the button sits under the menu panel, away from other mods' overlays */
    public static final int GEAR_DEFAULT = -1;
    /** written into the file; the first format has none */
    public int format = 2;

    /** One art in a menu. Random layers calibrate every art separately (spots); a fixed layer has one transform. */
    public static final class Layer {
        public String id = "";
        public boolean visible = true;
        public boolean locked = false;
        /** 0..1, negative = follow the menu */
        public float opacity = -1f;
        /** MODE_*, -1 = follow the menu */
        public int mode = -1;
        public String src = SRC_POOL;
        /** for SRC_GROUP */
        public String group = "";
        /** SRC_ART: the art; random layers: the sample shown in the studio */
        public String art = "";
        /** transform of a fixed layer; w <= 0 means "automatic placement" */
        public int x, y, w, h, rot;
        public boolean flip, flipY;
        /** "inv" = shared inventory-style layer (kept in step across menus by the studio), "" = ordinary */
        public String link = "";
        /** random layers: art id -> {x, y, w, h, rot, flip} saved for that art; no entry = automatic; w = 0 = automatic but flipped */
        public Map<String, int[]> spots = new LinkedHashMap<>();

        public boolean random() { return !SRC_ART.equals(src); }

        public Layer copy() {
            Layer c = new Layer();
            c.id = id; c.visible = visible; c.locked = locked; c.opacity = opacity; c.mode = mode;
            c.src = src; c.group = group; c.art = art;
            c.x = x; c.y = y; c.w = w; c.h = h; c.rot = rot; c.flip = flip; c.flipY = flipY; c.link = link == null ? "" : link;
            spots.forEach((k, v) -> c.spots.put(k, v.clone()));
            return c;
        }

        /** the saved spot of an art in a random layer, or null (automatic) */
        public Placement spot(String artId) {
            int[] s = spots.get(artId);
            return s == null ? null : fromSpot(s);
        }

        /** the layer's real calibration of one art (w > 0), else null */
        public boolean hasSavedSpot(String artId) {
            int[] s = spots.get(artId);
            return s != null && s[2] > 0 && s[3] > 0;
        }

        /** true when nothing about this layer differs from a fresh default layer */
        boolean isDefault() {
            return SRC_POOL.equals(src) && visible && !locked && opacity < 0 && mode < 0 && spots.isEmpty() && (link == null || link.isEmpty());
        }
    }

    public static Placement fromSpot(int[] s) {
        Placement p = new Placement(s[0], s[1], s[2], s[3]);
        p.rot = s[4];
        p.flip = s[5] == 1;
        p.flipY = s.length > 6 && s[6] == 1;
        return p;
    }

    public static int[] toSpot(Placement p) {
        return new int[] { p.x, p.y, p.w, p.h, p.rot, p.flip ? 1 : 0, p.flipY ? 1 : 0 };
    }

    public static final class LegacyExtra {
        public int x, y, w, h, rot;
        public String art = "";
        public String group = "";
        public boolean flip, visible = true;
        public float opacity = -1f;
        public Integer layer;
    }

    public static final class MenuCfg {
        public boolean disabled = false;
        /** 0..1, negative = follow the global value */
        public float opacity = -1f;
        /** MODE_*, -1 = follow the global value */
        public int mode = -1;
        /** bottom to top; empty = one default random layer */
        public List<Layer> layers = new ArrayList<>();

        // ---- format 1, read once and then dropped
        public Integer layer;
        public String pinned;
        public Map<String, Boolean> flips;
        public List<LegacyExtra> extras;
    }

    public static final class ArtMeta {
        public String group = "main";
        public boolean rotation = true;
        /** format 1 only */
        public Boolean flipX;
    }

    public static final int TITLE_STATIC = 0, TITLE_RAIN = 1, TITLE_BOTH = 2, TITLE_PLAIN = 3;
    public static final int BG_GRADIENT = 0, BG_SOLID = 1, BG_PANORAMA = 2;
    public static final String[] BG_TYPES = { "Gradient", "One colour", "Game panorama" };
    public static final String[] BG_DIRS = { "Top to bottom", "Bottom to top", "Left to right", "Right to left" };
    public static final int BG_A_DEFAULT = 0xFFF3DE, BG_B_DEFAULT = 0xF2C7A0;
    public static final String[] TITLE_MODES = { "Static art", "Art rain", "Static art + rain", "Plain background" };

    public static final class Title {
        /** new installs: imported pictures fall like rain over the game's own panorama */
        public int mode = TITLE_RAIN;
        /** beyond the normal slider ranges (Shift+click): the 45% area safety cap is lifted */
        public boolean insane() { return rainMax > 80 || rainRate > 12f || rainSpeed > 4f || rainSize > 3f; }
        /** most drops on screen at once */
        public int rainMax = 24;
        /** new drops per second */
        public float rainRate = 2f;
        /** speed multiplier */
        public float rainSpeed = 1f;
        /** size multiplier */
        public float rainSize = 1f;
        public float rainOpacity = 0.9f;
        /** how far the logo and buttons are pushed down (gui px); shrinks automatically on small screens */
        public int logoShift = 87;
        public boolean mascot = true;
        public int mascotDx = 0, mascotDy = 0;
        /** mirror the static art */
        public boolean flipArt = false;
        /** what is painted behind the art: gradient (colours A to B), one colour (A), or nothing (the game's panorama) */
        public int bgType = BG_PANORAMA;
        /** 0xRRGGBB */
        public int bgA = BG_A_DEFAULT, bgB = BG_B_DEFAULT;
        /** gradient direction: 0 top to bottom, 1 bottom to top, 2 left to right, 3 right to left */
        public int bgDir = 0;
        /** first release only: true meant "left to right"; read once and folded into bgDir */
        public boolean bgHoriz = false;
    }

    public Title title = new Title();

    /** master switch: false = the mod draws and moves nothing */
    public boolean enabled = true;
    /** small gear button in the corner of every supported container menu */
    public boolean showGear = true;
    /** the first-run welcome of the studio was shown */
    public boolean coached = false;
    /** gear button position as permille of the free screen width / height (0..1000) */
    public int gearFx = GEAR_DEFAULT, gearFy = GEAR_DEFAULT;
    public float opacity = 0.5f;
    /** default MODE_* of every layer that does not choose its own */
    public int mode = MODE_BEHIND;
    public Map<String, MenuCfg> menus = new LinkedHashMap<>();
    /** group name -> rotation enabled */
    public Map<String, Boolean> groups = new LinkedHashMap<>();
    public Map<String, ArtMeta> arts = new LinkedHashMap<>();

    // ---- format 1, read once and then dropped
    public Integer layer;
    public Map<String, Map<String, int[]>> placements;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings inst;
    /** set when layout.json was unreadable and a backup was used; the Studio shows it once */
    public static String restoredNote;
    private static long lastBackupAt = 0;

    private static final MenuCfg DEFAULT_MENU = new MenuCfg();
    private static final ArtMeta DEFAULT_ART = new ArtMeta();
    private static final List<Layer> DEFAULT_LAYERS = List.of(defaultLayer());

    private static Layer defaultLayer() {
        Layer l = new Layer();
        l.id = MAIN_ID;
        return l;
    }

    /** The shared default layer must never be edited: the studio works on its own copy (layersEdit). */
    public static void guardShared(Layer l) {
        if (l == DEFAULT_LAYERS.get(0)) throw new IllegalStateException("the shared default layer must not be modified");
    }
    private static final Random IDS = new Random();

    private static boolean migrated;

    /** config/zaecguimod; on first use the settings of the old name (config/mahirugui) are copied over, never moved. */
    public static synchronized Path configDir() {
        Path nu = FMLPaths.CONFIGDIR.get().resolve("zaecguimod");
        if (!migrated) {
            migrated = true;
            Path old = FMLPaths.CONFIGDIR.get().resolve("mahirugui");
            try {
                if (!Files.exists(nu) && Files.isDirectory(old)) {
                    try (var walk = Files.walk(old)) {
                        for (Path src : (Iterable<Path>) walk::iterator) {
                            Path dst = nu.resolve(old.relativize(src).toString());
                            if (Files.isDirectory(src)) Files.createDirectories(dst);
                            else { Files.createDirectories(dst.getParent()); Files.copy(src, dst); }
                        }
                    }
                    Guard.LOG.info("[ZaecGuiMod] copied the old settings from config/mahirugui");
                }
            } catch (Throwable t) {
                Guard.LOG.error("[ZaecGuiMod] could not migrate the old settings", t);
            }
        }
        return nu;
    }

    private static Path file() {
        return configDir().resolve("layout.json");
    }

    private static Path bak(int i) { return file().resolveSibling("layout.json.bak." + i); }

    private static final int BACKUPS = 5;

    private static Settings read(Path f) {
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, Settings.class);
        } catch (Throwable e) {
            return null;
        }
    }

    public static synchronized Settings get() {
        if (inst == null) {
            Path f = file();
            Settings s = read(f);
            if (s == null && Files.exists(f)) {
                // unreadable file: keep it aside and fall back to the newest readable backup, never lose it silently
                try { Files.move(f, f.resolveSibling("layout.json.broken-" + System.currentTimeMillis())); } catch (Exception ignored) { }
                List<Path> cands = new ArrayList<>();
                for (int i = 1; i <= BACKUPS; i++) cands.add(bak(i));
                cands.add(f.resolveSibling("layout.json.bak"));
                for (Path b : cands) {
                    s = read(b);
                    if (s != null) { restoredNote = "Settings file was damaged: restored from a backup (the broken file is kept next to it)"; break; }
                }
                if (s == null) restoredNote = "Settings file was damaged and no backup was found: starting fresh (the broken file is kept next to it)";
                Guard.LOG.warn("[ZaecGuiMod] layout.json was unreadable: {}", restoredNote);
            }
            if (s == null) s = new Settings();
            if (s.menus != null && s.looksLegacy() && Files.exists(f)) {
                // the conversion is one way: keep the original file once
                try {
                    Path v1 = f.resolveSibling("layout.json.v1");
                    if (!Files.exists(v1)) Files.copy(f, v1);
                } catch (Exception e) { Guard.LOG.warn("[ZaecGuiMod] could not keep a copy of the old layout.json", e); }
            }
            s.sanitize();
            inst = s;
        }
        return inst;
    }

    private static int ci(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private static float cf(float v, float lo, float hi, float def) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return def;
        return Math.max(lo, Math.min(hi, v));
    }

    public static String newId() {
        return String.format("%06x", IDS.nextInt(0xFFFFFF));
    }

    // ------------------------------------------------------------------ load: format 1 -> 2, then make everything safe

    /** true when the file still has data of the first format (placements / flips / pinned / extras) */
    private boolean looksLegacy() {
        if (placements != null || layer != null) return true;
        for (MenuCfg m : menus.values()) {
            if (m != null && (m.pinned != null || m.flips != null || m.extras != null || m.layer != null)) return true;
        }
        return false;
    }

    private void convertV1() {
        if (layer != null) { mode = layer; layer = null; }
        Map<String, Map<String, int[]>> pl = placements;
        placements = null;
        for (Map.Entry<String, MenuCfg> en : menus.entrySet()) {
            MenuCfg m = en.getValue();
            if (m == null) continue;
            if (m.layer != null) { m.mode = m.layer; m.layer = null; }
            Map<String, int[]> mine = pl == null ? null : pl.get(en.getKey());
            boolean legacy = (mine != null && !mine.isEmpty()) || (m.pinned != null && !m.pinned.isEmpty())
                    || (m.flips != null && !m.flips.isEmpty()) || (m.extras != null && !m.extras.isEmpty());
            if (legacy && m.layers.isEmpty()) {
                Layer base = new Layer();
                base.id = newId();
                if (mine != null) {
                    for (Map.Entry<String, int[]> p : mine.entrySet()) {
                        int[] a = p.getValue();
                        if (a == null || a.length < 4) continue;
                        boolean fl = m.flips != null && Boolean.TRUE.equals(m.flips.get(p.getKey()));
                        base.spots.put(p.getKey(), new int[] { a[0], a[1], a[2], a[3], a.length > 4 ? a[4] : 0, fl ? 1 : 0 });
                    }
                }
                if (m.flips != null) {
                    // mirrored arts without a saved position: automatic placement, mirrored
                    for (Map.Entry<String, Boolean> f : m.flips.entrySet()) {
                        if (Boolean.TRUE.equals(f.getValue()) && !base.spots.containsKey(f.getKey()))
                            base.spots.put(f.getKey(), new int[] { 0, 0, 0, 0, 0, 1 });
                    }
                }
                if (m.pinned != null && !m.pinned.isEmpty()) {
                    base.src = SRC_ART;
                    base.art = m.pinned;
                    int[] s = base.spots.get(m.pinned);
                    if (s != null) { base.x = s[0]; base.y = s[1]; base.w = s[2]; base.h = s[3]; base.rot = s[4]; base.flip = s[5] == 1; }
                    base.spots.clear();
                }
                m.layers.add(base);
                if (m.extras != null) {
                    for (LegacyExtra e : m.extras) {
                        if (e == null) continue;
                        boolean grp = e.group != null && !e.group.isEmpty();
                        if ((e.art == null || e.art.isEmpty()) && !grp) continue;
                        Layer l = new Layer();
                        l.id = newId();
                        l.art = e.art == null ? "" : e.art;
                        if (grp) {
                            l.src = SRC_GROUP;
                            l.group = e.group;
                            // the placement belonged to the sample art
                            if (e.w > 0 && e.h > 0 && !l.art.isEmpty()) l.spots.put(l.art, new int[] { e.x, e.y, e.w, e.h, e.rot, e.flip ? 1 : 0 });
                        } else {
                            l.src = SRC_ART;
                            l.x = e.x; l.y = e.y; l.w = e.w; l.h = e.h; l.rot = e.rot; l.flip = e.flip;
                        }
                        l.visible = e.visible;
                        l.opacity = e.opacity;
                        l.mode = e.layer == null ? -1 : e.layer;
                        m.layers.add(l);
                    }
                }
            }
            if ("title".equals(en.getKey()) && m.flips != null && m.flips.containsValue(Boolean.TRUE)) title.flipArt = true;
            m.pinned = null;
            m.flips = null;
            m.extras = null;
        }
        if (pl != null) {
            // placements of menus that had no other settings
            for (Map.Entry<String, Map<String, int[]>> en : pl.entrySet()) {
                if (menus.containsKey(en.getKey()) || en.getValue() == null || en.getValue().isEmpty()) continue;
                MenuCfg m = new MenuCfg();
                Layer base = new Layer();
                base.id = newId();
                for (Map.Entry<String, int[]> p : en.getValue().entrySet()) {
                    int[] a = p.getValue();
                    if (a == null || a.length < 4) continue;
                    base.spots.put(p.getKey(), new int[] { a[0], a[1], a[2], a[3], a.length > 4 ? a[4] : 0, 0 });
                }
                m.layers.add(base);
                menus.put(en.getKey(), m);
            }
        }
        // an art-wide mirror has no place in format 2 (mirroring is per layer and per art)
        for (ArtMeta a : arts.values()) if (a != null) a.flipX = null;
    }

    /** Makes any file content safe to use: out-of-range numbers, nulls, broken arrays, unknown groups. */
    private void sanitize() {
        if (menus == null) menus = new LinkedHashMap<>();
        if (groups == null) groups = new LinkedHashMap<>();
        if (arts == null) arts = new LinkedHashMap<>();
        if (title == null) title = new Title();
        menus.values().removeIf(m -> m == null);
        arts.values().removeIf(a -> a == null);
        for (MenuCfg m : menus.values()) if (m.layers == null) m.layers = new ArrayList<>();
        convertV1();

        mode = ci(mode, 0, MODE_NAMES.length - 1);
        // the old default (top right corner, where JEI has its page buttons) counts as "not moved"
        if (gearFx == 985 && gearFy == 15) { gearFx = GEAR_DEFAULT; gearFy = GEAR_DEFAULT; }
        gearFx = gearFx == GEAR_DEFAULT ? GEAR_DEFAULT : ci(gearFx, 0, 1000);
        gearFy = gearFy == GEAR_DEFAULT ? GEAR_DEFAULT : ci(gearFy, 0, 1000);
        opacity = cf(opacity, 0f, 1f, 0.5f);
        title.mode = ci(title.mode, 0, TITLE_MODES.length - 1);
        title.rainMax = ci(title.rainMax, 1, 1000);
        title.rainRate = cf(title.rainRate, 0.2f, 200f, 2f);
        title.rainSpeed = cf(title.rainSpeed, 0.2f, 20f, 1f);
        title.rainSize = cf(title.rainSize, 0.4f, 10f, 1f);
        title.rainOpacity = cf(title.rainOpacity, 0.1f, 1f, 0.9f);
        title.bgType = ci(title.bgType, 0, BG_TYPES.length - 1);
        if (title.bgHoriz) { title.bgDir = 2; title.bgHoriz = false; }
        title.bgDir = ci(title.bgDir, 0, BG_DIRS.length - 1);
        title.bgA &= 0xFFFFFF;
        title.bgB &= 0xFFFFFF;
        title.logoShift = ci(title.logoShift, 0, 140);
        title.mascotDx = ci(title.mascotDx, -999, 999);
        title.mascotDy = ci(title.mascotDy, -999, 999);

        groups.replaceAll((k, v) -> v == null ? Boolean.TRUE : v);
        if (!groups.containsKey("main")) groups.put("main", true);
        if (!groups.containsKey("unsorted")) groups.put("unsorted", false);
        for (ArtMeta a : arts.values()) {
            if (a.group == null || a.group.isEmpty()) a.group = "main";
            if (!groups.containsKey(a.group)) a.group = "unsorted";
        }
        for (MenuCfg m : menus.values()) fixMenu(m);
    }

    private static void fixMenu(MenuCfg m) {
        if (m.layers == null) m.layers = new ArrayList<>();
        m.mode = m.mode < -1 || m.mode >= MODE_NAMES.length ? -1 : m.mode;
        m.opacity = Float.isNaN(m.opacity) || Float.isInfinite(m.opacity) ? -1f : Math.min(1f, m.opacity);
        m.layers.removeIf(l -> l == null);
        while (m.layers.size() > MAX_LAYERS) m.layers.remove(m.layers.size() - 1);
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Layer l : m.layers) {
            fixLayer(l);
            if (!ids.add(l.id)) { l.id = newId(); ids.add(l.id); }
        }
    }

    // ---- snapshots for the studio's undo

    public String menuJson(String key) { return GSON.toJson(menu(key)); }

    public void restoreMenu(String key, String json) {
        MenuCfg m = GSON.fromJson(json, MenuCfg.class);
        if (m == null) return;
        fixMenu(m);
        menus.put(key, m);
    }

    private static final class LibState {
        Map<String, Boolean> groups;
        Map<String, ArtMeta> arts;
    }

    /** the library (groups, pool switches, art-to-group assignment) as one undo snapshot */
    public String libJson() {
        LibState l = new LibState();
        l.groups = groups;
        l.arts = arts;
        return GSON.toJson(l);
    }

    public void restoreLib(String json) {
        LibState l = GSON.fromJson(json, LibState.class);
        if (l == null || l.groups == null || l.arts == null) return;
        groups = l.groups;
        arts = l.arts;
        sanitize();
    }

    public String titleJson() { return GSON.toJson(title); }

    public void restoreTitle(String json) {
        Title t = GSON.fromJson(json, Title.class);
        if (t == null) return;
        title = t;
        sanitize();
    }

    private static void fixLayer(Layer l) {
        if (l.id == null || l.id.isEmpty()) l.id = newId();
        if (!SRC_POOL.equals(l.src) && !SRC_GROUP.equals(l.src) && !SRC_ART.equals(l.src)) l.src = SRC_POOL;
        if (l.group == null) l.group = "";
        if (l.link == null) l.link = "";
        if (l.art == null) l.art = "";
        if (SRC_ART.equals(l.src) && l.art.isEmpty()) l.src = SRC_POOL;
        l.opacity = Float.isNaN(l.opacity) || Float.isInfinite(l.opacity) ? -1f : Math.min(1f, l.opacity);
        if (l.mode < -1 || l.mode >= MODE_NAMES.length) l.mode = -1;
        l.rot = ci(l.rot, -180, 180);
        if (Math.abs((long) l.x) > 4000 || Math.abs((long) l.y) > 4000 || l.w > 4000 || l.h > 4000) { l.w = 0; l.h = 0; l.x = 0; l.y = 0; }
        if (l.w <= 0 || l.h <= 0) { l.w = 0; l.h = 0; }
        if (l.spots == null) l.spots = new LinkedHashMap<>();
        l.spots.values().removeIf(s -> s == null || s.length < 4 || s[2] < 0 || s[3] < 0
                || Math.abs((long) s[0]) > 4000 || Math.abs((long) s[1]) > 4000 || s[2] > 4000 || s[3] > 4000);
        for (Map.Entry<String, int[]> e : l.spots.entrySet()) {
            int[] s = e.getValue();
            if (s.length != 7) {
                int[] n = new int[7];
                System.arraycopy(s, 0, n, 0, Math.min(7, s.length));
                e.setValue(n);
                s = n;
            }
            s[4] = ci(s[4], -180, 180);
            s[5] = s[5] == 0 ? 0 : 1;
            s[6] = s[6] == 0 ? 0 : 1;
        }
    }

    /** NaN / Infinity would make the writer fail: fix them in place before every save. */
    private void sanitizeFloats() {
        opacity = cf(opacity, 0f, 1f, 0.5f);
        title.rainRate = cf(title.rainRate, 0.2f, 200f, 2f);
        title.rainSpeed = cf(title.rainSpeed, 0.2f, 20f, 1f);
        title.rainSize = cf(title.rainSize, 0.4f, 10f, 1f);
        title.rainOpacity = cf(title.rainOpacity, 0.1f, 1f, 0.9f);
        for (MenuCfg m : menus.values()) {
            m.opacity = Float.isNaN(m.opacity) || Float.isInfinite(m.opacity) ? -1f : Math.min(1f, m.opacity);
            for (Layer l : m.layers) l.opacity = Float.isNaN(l.opacity) || Float.isInfinite(l.opacity) ? -1f : Math.min(1f, l.opacity);
        }
    }

    /** Removes everything that refers to an art that no longer exists. */
    public void forgetArt(String artId) {
        arts.remove(artId);
        for (MenuCfg m : menus.values()) {
            m.layers.removeIf(l -> !l.random() && artId.equals(l.art));
            for (Layer l : m.layers) {
                l.spots.remove(artId);
                if (artId.equals(l.art) && l.random()) l.art = "";
            }
        }
    }

    /** the whole setup as text (a snapshot for "discard changes") */
    public String toJson() { return GSON.toJson(this); }

    /** the layers stored in a {@link #menuJson} text, ready to be put into another menu; empty when unreadable */
    public static List<Layer> layersOf(String menuJson) {
        try {
            MenuCfg m = GSON.fromJson(menuJson, MenuCfg.class);
            if (m == null) return new ArrayList<>();
            fixMenu(m);
            return new ArrayList<>(m.layers);
        } catch (Throwable t) {
            return new ArrayList<>();
        }
    }

    /** Parses and sanitizes a layout.json text; null when unreadable. */
    public static Settings parse(String json) {
        try {
            Settings s = GSON.fromJson(json, Settings.class);
            if (s == null) return null;
            s.sanitize();
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Merge: only adds what is missing here, never overrides what the user already has. */
    public void mergeFrom(Settings o) {
        o.groups.forEach(groups::putIfAbsent);
        o.arts.forEach(arts::putIfAbsent);
        o.menus.forEach((k, m) -> {
            MenuCfg mine = menus.get(k);
            if (mine == null || mine.layers.isEmpty()) {
                if (mine == null) menus.put(k, m);
                else mine.layers = m.layers;
            } else if (!m.layers.isEmpty()) {
                // calibrations of the first random layer; other layers stay as they are
                Layer a = null, b = null;
                for (Layer l : mine.layers) if (l.random()) { a = l; break; }
                for (Layer l : m.layers) if (l.random()) { b = l; break; }
                if (a != null && b != null) b.spots.forEach(a.spots::putIfAbsent);
            }
        });
        sanitize();
    }

    /** Replace: take everything from the other settings. */
    public void replaceWith(Settings o) {
        enabled = o.enabled;
        showGear = o.showGear;
        gearFx = o.gearFx;
        gearFy = o.gearFy;
        opacity = o.opacity;
        mode = o.mode;
        menus = o.menus;
        groups = o.groups;
        arts = o.arts;
        title = o.title;
        sanitize();
    }

    /** Creating accessor: use only where the value is about to be changed. */
    public MenuCfg menu(String name) {
        return menus.computeIfAbsent(name, k -> new MenuCfg());
    }

    /** Creating accessor: use only where the value is about to be changed. */
    public ArtMeta art(String id) {
        return arts.computeIfAbsent(id, k -> new ArtMeta());
    }

    /** Read-only views for render paths: never create entries (the returned default must not be modified). */
    public MenuCfg menuView(String name) {
        MenuCfg m = menus.get(name);
        return m != null ? m : DEFAULT_MENU;
    }

    public ArtMeta artView(String id) {
        ArtMeta m = arts.get(id);
        return m != null ? m : DEFAULT_ART;
    }

    /** The layers a menu really has: its own list, or the single default random layer. Never modify the result. */
    public List<Layer> layersView(String menu) {
        MenuCfg m = menus.get(menu);
        return m == null || m.layers.isEmpty() ? DEFAULT_LAYERS : m.layers;
    }

    /** Studio: the layers of a menu as an editable list (a default layer is created when there is none). */
    public List<Layer> layersEdit(String menu) {
        MenuCfg m = menu(menu);
        if (m.layers.isEmpty()) {
            m.layers.add(defaultLayer());
        }
        return m.layers;
    }

    public float opacityFor(MenuCfg c) {
        return c.opacity >= 0 ? c.opacity : opacity;
    }

    public int modeFor(MenuCfg c) {
        return c.mode >= 0 ? c.mode : mode;
    }

    public float opacityOf(MenuCfg c, Layer l) {
        return l.opacity >= 0 ? l.opacity : opacityFor(c);
    }

    public int modeOf(MenuCfg c, Layer l) {
        return l.mode >= 0 ? l.mode : modeFor(c);
    }

    /** True when the art may be picked by random rotation. */
    public boolean inRotation(String artId) {
        ArtMeta m = artView(artId);
        return m.rotation && groups.getOrDefault(m.group, true);
    }

    /** Entries that equal the defaults are not written (keeps the file small and readable). */
    private void prune() {
        arts.values().removeIf(a -> "main".equals(a.group) && a.rotation);
        for (MenuCfg m : menus.values()) {
            if (m.layers.size() == 1 && m.layers.get(0).isDefault()) m.layers.clear();
        }
        menus.values().removeIf(m -> m.opacity < 0 && m.mode < 0 && !m.disabled && m.layers.isEmpty());
    }

    /** Keeps up to 5 older versions (at most one new one per 30 s so rapid saves cannot wash out the good ones). */
    private static void rotateBackups(Path f) throws java.io.IOException {
        if (!Files.exists(f) || read(f) == null) return;
        long now = System.currentTimeMillis();
        if (Files.exists(bak(1)) && now - lastBackupAt < 30_000) return;
        lastBackupAt = now;
        Files.deleteIfExists(bak(BACKUPS));
        for (int i = BACKUPS - 1; i >= 1; i--) {
            if (Files.exists(bak(i))) Files.move(bak(i), bak(i + 1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.copy(f, bak(1), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Atomic write: temp file, verified by reading it back, rotating backups, then moved into place. */
    public static synchronized boolean save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            // prune a copy: the studio keeps editing the live objects
            Settings live = get();
            live.sanitizeFloats();
            Settings s = GSON.fromJson(GSON.toJson(live), Settings.class);
            s.prune();
            Path tmp = f.resolveSibling("layout.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(s, w);
            }
            if (read(tmp) == null) throw new java.io.IOException("written file does not parse back");
            rotateBackups(f);
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Throwable e) {
            Guard.LOG.error("[ZaecGuiMod] could not save layout.json", e);
            return false;
        }
    }

    private Settings() {}
}
