package com.mahiru.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Zaec Studio: an editor for the arts in game menus. Left: library of arts. Centre: the real menu with its layers (drag,
 * scale, turn). Right: layers of the menu and the properties of the selected one. Everything saves by itself.
 * The screen only holds the state and the actions; drawing is in {@link StudioView} and {@link StudioCanvas}.
 */
public class Studio extends Screen {
    public static final String TITLE_KEY = "title";
    static final int TOP = 18, BOT = 12;

    private static final String[][] FAMILIES = {
            { "furnace", "blast_furnace", "smoker" },
            { "generic_54_3", "shulker_box" } };

    final Screen previous;
    private final String startArt;
    String menuKey;
    Kit kit;
    final StudioCanvas canvas = new StudioCanvas(this);

    // ---- library
    Arts.Art libArt;
    String group = "main";
    int filter;                       // 0 all, 1 in rotation, 2 off
    final Set<String> multi = new LinkedHashSet<>();
    String anchorId;
    final Kit.Scroll gridScroll = new Kit.Scroll();
    String search = "";
    /** library panel rectangle {x0, y0, x1, y1} of this frame (null = not drawn) and the columns of its grid */
    int[] libRect;
    int gridCols = 1;

    // ---- layers
    String selId = "";
    final Kit.Scroll layersScroll = new Kit.Scroll(), inspScroll = new Kit.Scroll(), pickerScroll = new Kit.Scroll();

    // ---- view state
    boolean playerView, libOverlay, pickerOpen, snapOn = true, ghostsOn = true;
    /** menu picker keyboard highlight (a menu key or an "@" action), the last search text and mouse position it saw */
    String pickerSel = "", pickerQuery = "";
    int pickerMx = -1, pickerMy = -1;
    boolean pickerReveal;
    boolean mouseDown;
    private boolean initialised;

    // ---- history (snapshots of one menu or of the title settings)
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

    private static final class Hist {
        String key, json, sel;
        /** when the step was made: undo takes the newest of the menu history and the library history */
        long at = SEQ.incrementAndGet();
        boolean title;
        /** library state (groups, pool switches) when the step changed it */
        String libJson;
        /** other menus changed by the same step: {key, json} */
        List<String[]> others = new ArrayList<>();
    }

    // every menu (and the main menu settings) has its own history
    private final Map<String, Deque<Hist>> undoMap = new HashMap<>(), redoMap = new HashMap<>();

    private Deque<Hist> undoStack() { return undoMap.computeIfAbsent(menuKey, k -> new ArrayDeque<>()); }

    private Deque<Hist> redoStack() { return redoMap.computeIfAbsent(menuKey, k -> new ArrayDeque<>()); }

    // library changes (groups, pool switch) belong to no menu: one shared history, interleaved by time with the menu's own
    private final Deque<Hist> libUndo = new ArrayDeque<>(), libRedo = new ArrayDeque<>();

    private static Deque<Hist> newer(Deque<Hist> a, Deque<Hist> b) {
        if (a.isEmpty()) return b.isEmpty() ? null : b;
        if (b.isEmpty()) return a;
        return a.peekFirst().at >= b.peekFirst().at ? a : b;
    }
    private long lastHistAt;
    private String lastHistKey = "";

    // ---- autosave
    boolean pendingSave;
    private long lastChange;
    long savedAt;

    public Studio(Screen previous, String startMenu) {
        this(previous, startMenu, null);
    }

    public Studio(Screen previous, String startMenu, String startArt) {
        super(Component.literal("Zaec Studio"));
        this.previous = previous;
        this.startArt = startArt;
        this.menuKey = startMenu != null ? startMenu : "inventory";
    }

    // ------------------------------------------------------------------ basic access

    Settings st() { return Settings.get(); }
    boolean isTitle() { return TITLE_KEY.equals(menuKey); }
    Masks.Mask mask() { return isTitle() ? null : Masks.get(menuKey); }
    static long now() { return System.currentTimeMillis(); }

    /** the editable layers of the current menu (never empty: a default random layer is created on demand) */
    List<Settings.Layer> layers() {
        return isTitle() ? List.of() : st().layersEdit(menuKey);
    }

    Settings.MenuCfg cfg() { return st().menu(menuKey); }

    Settings.Layer sel() {
        List<Settings.Layer> ls = layers();
        if (NO_SEL.equals(selId)) return null;
        for (Settings.Layer l : ls) if (l.id.equals(selId)) return l;
        if (!ls.isEmpty()) { selId = ls.get(0).id; return ls.get(0); }
        return null;
    }

    /** nothing selected (a click on empty canvas); the layers stay as they are */
    static final String NO_SEL = "-";

    void deselect() { selId = NO_SEL; }

    int selIndex() {
        List<Settings.Layer> ls = layers();
        for (int i = 0; i < ls.size(); i++) if (ls.get(i).id.equals(selId)) return i;
        return -1;
    }

    /** the art a layer shows in the studio: the fixed art, or the sample of a random layer */
    Arts.Art sample(Settings.Layer l) {
        Arts.Art a = Arts.byId(l.art);
        if (a != null || !l.random()) return a;
        if (libArt != null) return libArt;
        List<Arts.Art> pool = LayerEngine.poolOf(l);
        return pool.isEmpty() ? null : pool.get(0);
    }

    /** where the art of a layer sits right now (saved, fixed or automatic) */
    Placement placementOf(Settings.Layer l) {
        Arts.Art a = sample(l);
        Masks.Mask m = mask();
        if (a == null || m == null) return null;
        return LayerEngine.placement(l, a, m, canvas.cache);
    }

    void touch() {
        pendingSave = true;
        lastChange = now();
        // an import that finishes after the studio was closed has nobody left to flush it
        if (Minecraft.getInstance().screen != this) flush();
    }

    void flush() {
        if (!pendingSave) return;
        pendingSave = false;
        if (Settings.save()) savedAt = now();
        else {
            // try again in a couple of seconds (a locked file is usually free by then)
            pendingSave = true;
            lastChange = now() + 2000;
            Toasts.error(T.t("Could not write config/zaecguimod/layout.json"));
        }
    }

    @Override
    public void tick() {
        if (pendingSave && now() - lastChange > 400) flush();
    }

    // ------------------------------------------------------------------ history

    /** Remember the state before a change. {@code coalesce}: fold quick repeated changes (nudges, wheel) into one step. */
    void hist(boolean coalesce) {
        String key = menuKey;
        if (coalesce && key.equals(lastHistKey) && now() - lastHistAt < 600) { lastHistAt = now(); return; }
        lastHistAt = now();
        lastHistKey = key;
        undoStack().push(snapshot(key));
        redoStack().clear();
        libRedo.clear();
        while (undoStack().size() > 80) undoStack().removeLast();
    }

    /** one undo step that also covers other menus (apply to similar menus) */
    private void histWithOthers(List<String> otherKeys) {
        Hist h = snapshot(menuKey);
        for (String k : otherKeys) h.others.add(new String[] { k, st().menuJson(k) });
        lastHistAt = now();
        lastHistKey = menuKey;
        undoStack().push(h);
        redoStack().clear();
        libRedo.clear();
        while (undoStack().size() > 80) undoStack().removeLast();
    }

    /** one undo step for a library change (groups, pool switch); lives in the history of the open menu */
    void histLib() {
        Hist h = snapshot(menuKey);
        h.libJson = st().libJson();
        lastHistAt = now();
        lastHistKey = menuKey;
        libUndo.push(h);
        redoStack().clear();
        libRedo.clear();
        while (libUndo.size() > 80) libUndo.removeLast();
    }

    private Hist snapshot(String key) {
        Hist h = new Hist();
        h.key = key;
        h.title = TITLE_KEY.equals(key);
        h.json = h.title ? st().titleJson() : st().menuJson(key);
        h.sel = key.equals(menuKey) ? selId : "";
        return h;
    }

    /** the current state of everything a step covers (what redo / undo of the opposite direction needs) */
    private Hist snapshotLike(Hist like) {
        Hist h = snapshot(like.key);
        if (like.libJson != null) h.libJson = st().libJson();
        for (String[] o : like.others) h.others.add(new String[] { o[0], st().menuJson(o[0]) });
        return h;
    }

    private void restore(Hist h) {
        lastHistKey = "";
        if (h.libJson != null) st().restoreLib(h.libJson);
        else if (h.title) st().restoreTitle(h.json);
        else st().restoreMenu(h.key, h.json);
        for (String[] o : h.others) st().restoreMenu(o[0], o[1]);
        if (!h.key.equals(menuKey)) selectMenu(h.key);
        selId = h.sel == null ? "" : h.sel;
        canvas.cache.clear();
        touch();
    }

    void undo() {
        Deque<Hist> from = newer(undoStack(), libUndo);
        if (from == null) { Toasts.warn(T.t("Nothing to undo")); return; }
        Hist h = from.pop();
        (from == libUndo ? libRedo : redoStack()).push(snapshotLike(h));
        restore(h);
        Toasts.ok(T.t("Undone"));
    }

    void redo() {
        Deque<Hist> from = newer(redoStack(), libRedo);
        if (from == null) { Toasts.warn(T.t("Nothing to redo")); return; }
        Hist h = from.pop();
        (from == libRedo ? libUndo : undoStack()).push(snapshotLike(h));
        restore(h);
        Toasts.ok(T.t("Redone"));
    }

    int undoCount() { return undoStack().size() + libUndo.size(); }
    int redoCount() { return redoStack().size() + libRedo.size(); }

    boolean canUndo() { return undoCount() > 0; }
    boolean canRedo() { return redoCount() > 0; }

    // ------------------------------------------------------------------ menus

    static List<String> menuKeys() {
        List<String> l = new ArrayList<>();
        l.add(TITLE_KEY);
        for (MenuInfo.Entry e : MenuInfo.ENTRIES) l.add(e.key());
        return l;
    }

    static String menuTitle(String key) {
        return TITLE_KEY.equals(key) ? T.t("Main menu") : MenuInfo.title(key);
    }

    void selectMenu(String key) {
        menuKey = key;
        selId = "";
        canvas.menuChanged();
        if (!isTitle()) sel();
    }

    void stepMenu(int dir) {
        List<String> k = menuKeys();
        int i = k.indexOf(menuKey);
        selectMenu(k.get(Math.floorMod(i + dir, k.size())));
    }

    /** how many arts of the first random layer of a menu have a saved position: {saved, total} */
    int[] calibration(String key) {
        for (Settings.Layer l : st().layersView(key)) {
            if (!l.random()) continue;
            List<Arts.Art> pool = LayerEngine.poolOf(l);
            int saved = 0;
            for (Arts.Art a : pool) if (l.hasSavedSpot(a.id)) saved++;
            return new int[] { saved, pool.size() };
        }
        return new int[] { 0, 0 };
    }

    // ------------------------------------------------------------------ layer actions

    void selectLayer(String id) {
        selId = id;
        Settings.Layer l = sel();
        if (l != null && l.random()) {
            Arts.Art a = Arts.byId(l.art);
            if (a != null) libArt = a;
        }
    }

    private Placement defaultFixed(Arts.Art a) {
        Masks.Mask m = mask();
        int mw = m == null ? 176 : m.w, mh = m == null ? 166 : m.h;
        int w = Math.round(mw * 0.4f);
        int h = Math.max(8, Math.round(w * (float) a.sh / a.sw));
        return new Placement((mw - w) / 2, (mh - h) / 2, w, h);
    }

    void addLayer(boolean random) {
        if (isTitle()) return;
        List<Settings.Layer> ls = layers();
        if (ls.size() >= Settings.MAX_LAYERS) { Toasts.warn(T.t("Too many layers in this menu")); return; }
        if (!random && libArt == null) { Toasts.warn(T.t("Pick an art in the library first")); return; }
        hist(false);
        Settings.Layer l = new Settings.Layer();
        l.id = Settings.newId();
        if (random) {
            l.src = Settings.SRC_POOL;
            l.art = libArt == null ? "" : libArt.id;
        } else {
            l.src = Settings.SRC_ART;
            l.art = libArt.id;
            LayerEngine.store(l, libArt, defaultFixed(libArt));
        }
        ls.add(l);
        selectLayer(l.id);
        touch();
        Toasts.ok(random ? T.t("Random layer added") : T.t("Layer added: drag it in the preview"));
    }

    /** a library art dropped on the canvas: a fixed layer centred on the drop point */
    void addDroppedLayer(Arts.Art a, double panelX, double panelY) {
        if (isTitle() || mask() == null) return;
        List<Settings.Layer> ls = layers();
        if (ls.size() >= Settings.MAX_LAYERS) { Toasts.warn(T.t("Too many layers in this menu")); return; }
        hist(false);
        Settings.Layer l = new Settings.Layer();
        l.id = Settings.newId();
        l.src = Settings.SRC_ART;
        l.art = a.id;
        Placement p = defaultFixed(a);
        p.x = (int) Math.round(panelX - p.w / 2.0);
        p.y = (int) Math.round(panelY - p.h / 2.0);
        canvas.clamp(p);
        LayerEngine.store(l, a, p);
        ls.add(l);
        selectLayer(l.id);
        touch();
    }

    void duplicateLayer() {
        Settings.Layer l = sel();
        List<Settings.Layer> ls = layers();
        if (l == null || ls.size() >= Settings.MAX_LAYERS) return;
        hist(false);
        Settings.Layer c = l.copy();
        c.id = Settings.newId();
        c.link = "";
        if (!c.random() && c.w > 0) { c.x += 6; c.y += 6; }
        ls.add(selIndex() + 1, c);
        selectLayer(c.id);
        touch();
    }

    void deleteLayer() {
        Settings.Layer l = sel();
        List<Settings.Layer> ls = layers();
        if (l == null) return;
        hist(false);
        int i = selIndex();
        ls.remove(l);
        selId = "";
        if (!ls.isEmpty()) selectLayer(ls.get(Math.min(i, ls.size() - 1)).id);
        touch();
        Toasts.ok(T.t("Layer deleted (Ctrl+Z brings it back)"));
    }

    void moveLayer(int dir) {
        List<Settings.Layer> ls = layers();
        int i = selIndex(), j = i + dir;
        if (i < 0 || j < 0 || j >= ls.size()) return;
        hist(false);
        Settings.Layer l = ls.get(i);
        ls.set(i, ls.get(j));
        ls.set(j, l);
        touch();
    }

    void moveLayerTo(boolean front) {
        List<Settings.Layer> ls = layers();
        Settings.Layer l = sel();
        if (l == null || ls.size() < 2) return;
        hist(false);
        ls.remove(l);
        if (front) ls.add(l); else ls.add(0, l);
        touch();
    }

    void toggleVisible(Settings.Layer l) {
        hist(false);
        l.visible = !l.visible;
        touch();
    }

    /** change what a layer shows; what it looks like (position of the sample art) is kept */
    void setSource(Settings.Layer l, String src, String grp) {
        Arts.Art a = sample(l);
        if (Settings.SRC_ART.equals(src) && a == null) { Toasts.warn(T.t("Pick an art in the library first")); return; }
        Placement cur = placementOf(l);
        hist(false);
        l.src = src;
        l.group = grp == null ? "" : grp;
        if (a != null) {
            boolean inPool = !l.random() || LayerEngine.poolOf(l).contains(a);
            if (inPool) {
                l.art = a.id;
                if (cur != null) LayerEngine.store(l, a, cur);
            } else {
                List<Arts.Art> pool = LayerEngine.poolOf(l);
                if (!pool.isEmpty()) l.art = pool.get(0).id;
            }
        }
        touch();
    }

    void setFixedArt(Settings.Layer l, Arts.Art a) {
        hist(false);
        if (l.w > 0 && l.h > 0) l.h = Math.max(8, Math.round(l.w * (float) a.sh / a.sw));
        l.art = a.id;
        touch();
    }

    void useLibraryArt() {
        Settings.Layer l = sel();
        if (l == null || libArt == null) return;
        if (l.random()) { l.art = libArt.id; touch(); } else setFixedArt(l, libArt);
    }

    // ---- transform of the selected layer (every change makes the position of that art "saved")

    private final Set<String> savedHint = new java.util.HashSet<>();

    /** writes a placement into a layer; the first time an automatic position becomes a saved one the user is told */
    void store(Settings.Layer l, Arts.Art a, Placement p) {
        boolean wasAuto = LayerEngine.isAuto(l, a);
        LayerEngine.store(l, a, p);
        if (wasAuto && l.random() && savedHint.add(l.id + "|" + a.id))
            Toasts.ok(T.f("Position saved for %s only. The other arts stay automatic.", a.id));
    }

    interface PlaceEdit { void apply(Placement p, Arts.Art a); }

    void edit(boolean coalesce, PlaceEdit e) {
        Settings.Layer l = sel();
        Arts.Art a = l == null ? null : sample(l);
        Placement p = l == null ? null : placementOf(l);
        if (p == null || a == null) return;
        hist(coalesce);
        e.apply(p, a);
        canvas.clamp(p);
        store(l, a, p);
        touch();
    }

    void nudge(int dx, int dy) { edit(true, (p, a) -> { p.x += dx; p.y += dy; }); }

    void rotate(int deg) {
        edit(true, (p, a) -> {
            int r = p.rot + deg;
            while (r > 180) r -= 360;
            while (r < -180) r += 360;
            p.rot = r;
        });
    }

    void setRotation(int deg) { edit(true, (p, a) -> p.rot = Math.max(-180, Math.min(180, deg))); }

    void scaleBy(float f) {
        edit(true, (p, a) -> {
            int nw = Math.round(p.w * f);
            if (nw == p.w) nw += f > 1f ? 1 : -1;
            Masks.Mask m = mask();
            nw = Math.max(8, Math.min(nw, (m != null ? m.w : 400) * 3));
            int nh = Math.max(8, Math.round(nw * (float) a.sh / a.sw));
            p.x += (p.w - nw) / 2;
            p.y += (p.h - nh) / 2;
            p.w = nw;
            p.h = nh;
        });
    }

    /** set the width (byWidth) or the height; the other follows the art's proportions, the centre stays */
    void setSize(int value, boolean byWidth) {
        edit(true, (p, a) -> {
            int nw = byWidth ? Math.max(8, Math.min(value, 1200)) : Math.max(8, Math.round(Math.max(8, Math.min(value, 1200)) * (float) a.sw / a.sh));
            int nh = Math.max(8, Math.round(nw * (float) a.sh / a.sw));
            p.x += (p.w - nw) / 2;
            p.y += (p.h - nh) / 2;
            p.w = nw;
            p.h = nh;
        });
    }

    void setPos(Integer x, Integer y) {
        edit(true, (p, a) -> { if (x != null) p.x = x; if (y != null) p.y = y; });
    }

    void center(boolean horizontal) {
        Masks.Mask m = mask();
        if (m == null) return;
        edit(false, (p, a) -> { if (horizontal) p.x = (m.w - p.w) / 2; else p.y = (m.h - p.h) / 2; });
    }

    void flip() { flip(false); }

    /** mirror the art left-right, or top-bottom when {@code vertical} */
    void flip(boolean vertical) {
        Settings.Layer l = sel();
        Arts.Art a = l == null ? null : sample(l);
        Placement p = l == null ? null : placementOf(l);
        if (a == null || p == null) return;
        hist(false);
        // the art stays exactly where it is: an automatic position becomes a saved one with the mirror applied
        if (vertical) p.flipY = !p.flipY; else p.flip = !p.flip;
        store(l, a, p);
        touch();
    }

    void resetAuto() {
        Settings.Layer l = sel();
        Arts.Art a = l == null ? null : sample(l);
        if (a == null) return;
        hist(false);
        LayerEngine.clear(l, a);
        touch();
        Toasts.ok(T.t("Back to the automatic position (Ctrl+Z undoes)"));
    }

    void setOpacity(Settings.Layer l, float v) {
        hist(true);
        l.opacity = Math.max(0f, Math.min(1f, v));
        touch();
    }

    void followMenuOpacity(Settings.Layer l) {
        hist(false);
        l.opacity = -1f;
        touch();
    }

    void setMode(Settings.Layer l, int mode) {
        hist(false);
        l.mode = mode;
        touch();
    }

    // ---- sample art of a random layer

    void stepSample(int dir) {
        Settings.Layer l = sel();
        if (l == null || !l.random()) return;
        List<Arts.Art> pool = LayerEngine.poolOf(l);
        if (pool.isEmpty()) return;
        Arts.Art cur = sample(l);
        int i = Math.max(0, pool.indexOf(cur));
        Arts.Art next = pool.get(Math.floorMod(i + dir, pool.size()));
        l.art = next.id;
        libArt = next;
        touch();
    }

    /** another random art for the selected random layer */
    void roll() {
        Settings.Layer l = isTitle() ? null : sel();
        if (l == null || !l.random()) { Toasts.warn(T.t("Select a random layer first")); return; }
        List<Arts.Art> pool = LayerEngine.poolOf(l);
        if (pool.isEmpty()) return;
        l.art = pool.get(new java.util.Random().nextInt(pool.size())).id;
        libArt = Arts.byId(l.art);
        touch();
    }

    /** jump the sample to the next art of the pool that has no saved position in this layer */
    void nextUnsaved() {
        Settings.Layer l = sel();
        if (l == null || !l.random()) return;
        List<Arts.Art> pool = LayerEngine.poolOf(l);
        if (pool.isEmpty()) return;
        Arts.Art cur = sample(l);
        int i = Math.max(0, pool.indexOf(cur));
        for (int k = 1; k <= pool.size(); k++) {
            Arts.Art a = pool.get((i + k) % pool.size());
            if (!l.hasSavedSpot(a.id)) {
                l.art = a.id;
                libArt = a;
                touch();
                return;
            }
        }
        Toasts.ok(T.t("Every art of this layer has a saved position"));
    }

    /** how many menus have a saved position for this art in their first random layer: {positioned, menus} */
    int[] positionedMenus(Arts.Art a) {
        int done = 0, total = 0;
        for (MenuInfo.Entry e : MenuInfo.ENTRIES) {
            total++;
            for (Settings.Layer l : st().layersView(e.key())) if (l.random() && l.hasSavedSpot(a.id)) { done++; break; }
        }
        return new int[] { done, total };
    }

    boolean hasFamily() {
        for (String[] f : FAMILIES) for (String k : f) if (k.equals(menuKey)) return true;
        return false;
    }

    /** next menu (after this one) where the sample art of the selected layer has no saved position */
    void nextMenuToCalibrate() {
        Settings.Layer l = isTitle() ? null : sel();
        Arts.Art a = l == null ? libArt : sample(l);
        if (a == null) return;
        List<String> keys = menuKeys();
        int start = keys.indexOf(menuKey);
        for (int k = 1; k < keys.size(); k++) {
            String key = keys.get((start + k) % keys.size());
            if (TITLE_KEY.equals(key)) continue;
            boolean saved = false;
            for (Settings.Layer ml : st().layersView(key)) if (ml.random() && ml.hasSavedSpot(a.id)) saved = true;
            if (!saved && !st().menuView(key).disabled) {
                selectMenu(key);
                for (Settings.Layer ml : layers()) if (ml.random()) { selectLayer(ml.id); break; }
                setLib(a);
                return;
            }
        }
        Toasts.ok(T.t("This art has a saved position in every menu"));
    }

    void applyToSimilar() {
        Settings.Layer l = isTitle() ? null : sel();
        Arts.Art a = l == null ? null : sample(l);
        Placement cur = l == null ? null : placementOf(l);
        if (a == null || cur == null || !l.random()) { Toasts.warn(T.t("Select a random layer first")); return; }
        String[] fam = null;
        for (String[] f : FAMILIES) for (String k : f) if (k.equals(menuKey)) fam = f;
        if (fam == null) { Toasts.warn(T.t("No similar menus for this one")); return; }
        List<String> targets = new ArrayList<>();
        for (String k : fam) {
            if (k.equals(menuKey)) continue;
            Settings.Layer target = null;
            for (Settings.Layer o : st().layersEdit(k)) if (o.random()) { target = o; break; }
            if (target != null && !target.hasSavedSpot(a.id)) targets.add(k);
        }
        if (targets.isEmpty() && l.hasSavedSpot(a.id)) {
            Toasts.warn(T.t("Similar menus already have a saved position for this art"));
            return;
        }
        histWithOthers(targets);
        if (!l.hasSavedSpot(a.id)) LayerEngine.store(l, a, cur);
        int n = 0;
        for (String k : targets) {
            for (Settings.Layer o : st().layersEdit(k)) if (o.random()) { o.spots.put(a.id, Settings.toSpot(cur)); break; }
            n++;
        }
        touch();
        if (n > 0) Toasts.ok(T.f("Applied to %s similar menu(s) (saved ones were kept)", n));
        else Toasts.warn(T.t("Similar menus already have a saved position for this art"));
    }

    // ---- one style for the player-inventory block of many menus

    /** menus (other than this one) that have an inventory block and room for another layer */
    private List<String> inventoryTargets() {
        List<String> out = new ArrayList<>();
        for (MenuInfo.Entry e : MenuInfo.ENTRIES) {
            String k = e.key();
            if (k.equals(menuKey) || st().menuView(k).disabled) continue;
            if (Masks.inventoryOrigin(Masks.get(k)) == null) continue;
            out.add(k);
        }
        return out;
    }

    boolean canShareInventory() {
        return !isTitle() && sel() != null && Masks.inventoryOrigin(mask()) != null;
    }

    void shareInventoryDialog() {
        Settings.Layer l = isTitle() ? null : sel();
        int[] from = Masks.inventoryOrigin(mask());
        if (l == null || from == null) { Toasts.warn(T.t("This menu has no inventory block")); return; }
        List<String> targets = inventoryTargets();
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Use this layer for the inventory everywhere?");
        d.text = T.f("A copy of this layer is placed over the inventory block of %s other menu(s), moved to match it. Earlier shared layers are replaced; all other layers stay untouched.", targets.size());
        d.yes = T.t("Apply");
        d.no = T.t("Cancel");
        d.onYes = () -> shareInventory(l, from, targets);
        kit.openDialog(d);
    }

    private static Settings.Layer shifted(Settings.Layer src, int dx, int dy) {
        Settings.Layer c = src.copy();
        c.id = Settings.newId();
        c.link = "inv";
        c.visible = true;
        if (c.w > 0 && c.h > 0) { c.x += dx; c.y += dy; }
        for (int[] s : c.spots.values()) if (s[2] > 0 && s[3] > 0) { s[0] += dx; s[1] += dy; }
        return c;
    }

    private void shareInventory(Settings.Layer l, int[] from, List<String> targets) {
        histWithOthers(targets);
        int n = 0, full = 0;
        for (String k : targets) {
            int[] to = Masks.inventoryOrigin(Masks.get(k));
            if (to == null) continue;
            List<Settings.Layer> ls = st().layersEdit(k);
            int old = 0;
            for (Settings.Layer o : ls) if ("inv".equals(o.link)) old++;
            if (ls.size() - old >= Settings.MAX_LAYERS) { full++; continue; }
            ls.removeIf(o -> "inv".equals(o.link));
            ls.add(0, shifted(l, to[0] - from[0], to[1] - from[1]));
            n++;
        }
        // the source stays the "master" copy; other shared layers of this menu are replaced by it
        layers().removeIf(o -> o != l && "inv".equals(o.link));
        l.link = "inv";
        touch();
        Toasts.ok(T.f("Inventory style applied to %s menu(s) (Ctrl+Z undoes)", n));
        if (full > 0) Toasts.warn(T.f("%s menu(s) skipped: too many layers", full));
    }

    boolean hasSharedInventory() {
        for (MenuInfo.Entry e : MenuInfo.ENTRIES)
            for (Settings.Layer o : st().layersView(e.key())) if ("inv".equals(o.link)) return true;
        return false;
    }

    void unshareInventoryDialog() {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Remove shared inventory layers?");
        d.text = T.t("Every layer that was spread over the inventory blocks is deleted from all menus. Other layers stay.");
        d.yes = T.t("Remove");
        d.no = T.t("Cancel");
        d.onYes = () -> {
            List<String> keys = new ArrayList<>();
            for (MenuInfo.Entry e : MenuInfo.ENTRIES) {
                if (e.key().equals(menuKey)) continue;
                for (Settings.Layer o : st().layersView(e.key())) if ("inv".equals(o.link)) { keys.add(e.key()); break; }
            }
            histWithOthers(keys);
            for (String k : keys) st().layersEdit(k).removeIf(o -> "inv".equals(o.link));
            if (!isTitle()) layers().removeIf(o -> "inv".equals(o.link));
            selId = "";
            sel();
            touch();
            Toasts.ok(T.t("Shared inventory layers removed (Ctrl+Z undoes)"));
        };
        kit.openDialog(d);
    }

    void resetMenuDialog() {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Reset this menu?");
        d.text = T.f("All layers and saved positions of '%s' will be removed. Ctrl+Z brings them back.", menuTitle(menuKey));
        d.yes = T.t("Reset");
        d.no = T.t("Cancel");
        d.onYes = this::resetMenu;
        kit.openDialog(d);
    }

    private void resetMenu() {
        hist(false);
        Settings.MenuCfg m = cfg();
        m.layers.clear();
        m.opacity = -1f;
        m.mode = -1;
        m.disabled = false;
        selId = "";
        sel();
        touch();
        Toasts.ok(T.t("Menu reset to one random layer (Ctrl+Z undoes)"));
    }

    // ---- discard the changes of this session / copy layers between menus / first-run welcome

    private String sessionJson = "";
    private String clipJson;
    private String clipFrom = "";

    boolean changedThisSession() { return !sessionJson.isEmpty() && !sessionJson.equals(st().toJson()); }

    void discardDialog() {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Discard the changes of this session?");
        d.text = T.t("Everything is put back as it was when you opened the studio: layers, positions, groups, the main menu. Imported pictures stay in the library.");
        d.yes = T.t("Discard");
        d.no = T.t("Cancel");
        d.onYes = () -> {
            Settings old = Settings.parse(sessionJson);
            if (old == null) { Toasts.error(T.t("The saved state could not be read")); return; }
            st().replaceWith(old);
            undoMap.clear();
            redoMap.clear();
            libUndo.clear();
            libRedo.clear();
            multi.clear();
            canvas.cache.clear();
            selectMenu(menuKey);
            touch();
            flush();
            Toasts.ok(T.t("Changes of this session discarded"));
        };
        kit.openDialog(d);
    }

    void copyLayers() {
        if (isTitle()) return;
        clipJson = st().menuJson(menuKey);
        clipFrom = menuKey;
        Toasts.ok(T.f("Layers of '%s' copied", menuTitle(menuKey)));
    }

    boolean canPaste() { return !isTitle() && clipJson != null; }

    /** put the copied layers into this menu: appended, or replacing everything here; one undo step */
    void pasteLayers(boolean replace) {
        if (!canPaste()) return;
        List<Settings.Layer> src = Settings.layersOf(clipJson);
        if (src.isEmpty()) { Toasts.warn(T.t("The copied menu has no layers")); return; }
        hist(false);
        List<Settings.Layer> ls = layers();
        if (replace) ls.clear();
        int added = 0;
        for (Settings.Layer l : src) {
            if (ls.size() >= Settings.MAX_LAYERS) break;
            Settings.Layer c = l.copy();
            c.id = Settings.newId();
            c.link = "";
            ls.add(c);
            added++;
        }
        selId = ls.isEmpty() ? "" : ls.get(ls.size() - 1).id;
        canvas.cache.clear();
        touch();
        Toasts.ok(T.f("%s layer(s) pasted from '%s' (Ctrl+Z undoes)", added, menuTitle(clipFrom)));
    }

    private void welcome() {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Hi! This is Zaec Studio");
        d.info = true;
        d.yes = T.t("Let's go");
        boolean empty = Arts.all().isEmpty();
        d.text = T.t("Here you dress up your game menus with pictures, the way you like it.")
                + "\n" + (empty ? T.t("First, bring some pictures: drop PNG, JPG or GIF files onto this window and they show up on the left.")
                        : T.t("Pick a picture on the left and drop it onto the menu in the middle. Then drag it to where it looks best."))
                + "\n" + T.t("No need to save: everything is kept for you, and Ctrl+Z takes a step back.")
                + "\n" + T.t("Lost? F1 shows a cheat sheet, F9 takes you back to the game.");
        kit.openDialog(d);
    }

    // ------------------------------------------------------------------ library

    List<Arts.Art> artsInView() {
        List<Arts.Art> out = new ArrayList<>();
        String q = search.trim().toLowerCase(java.util.Locale.ROOT);
        for (Arts.Art a : Arts.all()) {
            if (!st().artView(a.id).group.equals(group)) continue;
            boolean in = st().inRotation(a.id);
            if (filter == 1 && !in) continue;
            if (filter == 2 && in) continue;
            if (!q.isEmpty() && !a.id.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
            out.add(a);
        }
        return out;
    }

    List<Arts.Art> targets() {
        List<Arts.Art> out = new ArrayList<>();
        if (multi.size() > 1) for (Arts.Art a : Arts.all()) if (multi.contains(a.id)) out.add(a);
        if (out.isEmpty() && libArt != null) out.add(libArt);
        return out;
    }

    boolean isPicked(Arts.Art a) { return a == libArt || multi.contains(a.id); }

    void pickArt(Arts.Art hit, boolean shift, boolean ctrl) {
        List<Arts.Art> list = artsInView();
        int i = list.indexOf(hit);
        if (shift && anchorId != null && i >= 0) {
            int a0 = -1;
            for (int k = 0; k < list.size(); k++) if (list.get(k).id.equals(anchorId)) a0 = k;
            if (a0 < 0) a0 = i;
            multi.clear();
            for (int k = Math.min(a0, i); k <= Math.max(a0, i); k++) multi.add(list.get(k).id);
            setLib(hit, false);
            Toasts.ok(T.f("%s arts selected", multi.size()));
        } else if (ctrl) {
            if (multi.isEmpty() && libArt != null) multi.add(libArt.id);
            anchorId = hit.id;
            if (multi.remove(hit.id)) {
                Arts.Art rest = null;
                for (String id : multi) rest = Arts.byId(id);
                if (multi.size() <= 1) multi.clear();
                if (rest != null) setLib(rest, false);
            } else {
                multi.add(hit.id);
                setLib(hit, false);
            }
        } else {
            multi.clear();
            anchorId = hit.id;
            setLib(hit);
        }
    }

    /** the library selection changed: a random layer follows it (it shows that art as its sample) */
    void setLib(Arts.Art a) { setLib(a, true); }

    /** @param follow a plain click: the selected random layer starts editing this art; Ctrl / Shift clicks only pick */
    void setLib(Arts.Art a, boolean follow) {
        libArt = a;
        if (!follow) return;
        Settings.Layer l = isTitle() ? null : sel();
        if (l != null && l.random() && !a.id.equals(l.art)) { l.art = a.id; touch(); }
    }

    void selectAllInView() {
        List<Arts.Art> l = artsInView();
        multi.clear();
        for (Arts.Art a : l) multi.add(a.id);
        if (!l.isEmpty() && (libArt == null || !multi.contains(libArt.id))) setLib(l.get(0));
        Toasts.ok(T.f("%s arts selected", multi.size()));
    }

    void toggleRotation() {
        if (libArt == null) return;
        boolean v = !st().art(libArt.id).rotation;
        List<Arts.Art> t = targets();
        histLib();
        for (Arts.Art a : t) st().art(a.id).rotation = v;
        touch();
        if (t.size() > 1) Toasts.ok(T.f("Rotation %s for %s art(s)", v ? T.t("ON") : T.t("OFF"), t.size()));
        else Toasts.ok(T.f("%s: random pool %s", libArt.id, v ? T.t("ON") : T.t("OFF")));
    }

    void moveTargetsTo(String to) {
        List<Arts.Art> t = targets();
        if (t.isEmpty()) return;
        histLib();
        for (Arts.Art a : t) st().art(a.id).group = to;
        touch();
        Toasts.ok(t.size() > 1 ? T.f("%s arts moved to group '%s'", t.size(), to) : T.f("%s moved to group '%s'", t.get(0).id, to));
    }

    void newGroupDialog() {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("New group");
        d.text = T.t("Name of the new group of arts:");
        d.inputId = "dlg-group";
        d.inputHint = T.t("group name");
        d.yes = T.t("Create");
        d.no = T.t("Cancel");
        d.onYes = () -> {
            String n = d.input.trim();
            if (n.isEmpty()) { Toasts.warn(T.t("Type a group name first")); return; }
            if (st().groups.containsKey(n)) { Toasts.warn(T.f("Group '%s' already exists", n)); return; }
            histLib();
            st().groups.put(n, true);
            group = n;
            multi.clear();
            touch();
            Toasts.ok(T.f("Group '%s' created", n));
        };
        kit.openDialog(d);
    }

    void deleteImportedDialog(Arts.Art a) {
        if (a == null || !a.imported) return;
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Delete imported art?");
        d.text = T.f("%s will be removed together with its saved positions. This cannot be undone.", a.id);
        d.yes = T.t("Delete");
        d.no = T.t("Cancel");
        d.onYes = () -> {
            String id = a.id;
            if (!Imports.delete(id)) { Toasts.error(T.t("Could not delete the imported art")); return; }
            st().forgetArt(id);
            multi.remove(id);
            List<Arts.Art> all = Arts.all();
            libArt = all.isEmpty() ? null : all.get(0);
            if (libArt != null) group = st().artView(libArt.id).group;
            canvas.cache.clear();
            // earlier steps would bring back layers of the deleted art
            undoMap.clear();
            redoMap.clear();
                libUndo.clear();
                libRedo.clear();
            touch();
            Toasts.ok(T.t("Imported art deleted"));
        };
        kit.openDialog(d);
    }

    // ------------------------------------------------------------------ files: images, setup zip, export

    void doExport() {
        try {
            Path f = SetupZip.export();
            Toasts.ok(T.f("Exported: %s", f.getFileName().toString()));
            SetupZip.openFolder(f.getParent());
        } catch (Throwable t) {
            Guard.LOG.error("[ZaecGuiMod] export failed", t);
            Toasts.error(T.t("Could not write the export zip"));
        }
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        List<Path> zips = new ArrayList<>(), images = new ArrayList<>();
        for (Path p : paths) {
            String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (n.endsWith(".zip")) zips.add(p); else images.add(p);
        }
        if (zips.isEmpty() && images.isEmpty()) return;
        // say what was caught and what happens with it
        if (!zips.isEmpty() && !images.isEmpty())
            Toasts.ok(T.f("Caught %s picture(s) and a setup zip: the pictures are imported, the zip opens an import window", images.size()));
        else if (!zips.isEmpty())
            Toasts.ok(T.f("Caught a setup zip (%s): checking it, then you choose what to import", zips.get(0).getFileName().toString()));
        if (!zips.isEmpty()) openSetupZip(zips.get(0));
        if (images.isEmpty()) return;
        if (zips.isEmpty()) Toasts.ok(T.f("Caught %s file(s): importing them as arts into the group 'imported'", images.size()));
        Imports.importAsync(images, res -> Guard.run("import-finish", () -> {
            for (String e : res.errors) Toasts.error(e);
            if (res.duplicates > 0) Toasts.warn(T.f("%s file(s) were already imported", res.duplicates));
            if (res.imported.isEmpty()) return;
            st().groups.putIfAbsent("imported", true);
            Arts.Art last = null;
            for (String id : res.imported) {
                st().art(id).group = "imported";
                Arts.Art a = Arts.byId(id);
                if (a != null) last = a;
            }
            touch();
            Toasts.ok(T.f("Imported %s art(s) into the group 'imported'", res.imported.size()));
            if (last != null) { group = "imported"; filter = 0; search = ""; multi.clear(); setLib(last); }
        }));
    }

    private void openSetupZip(Path zip) {
        Toasts.ok(T.t("Checking the setup zip..."));
        Thread th = new Thread(() -> {
            SetupZip.Preview pv = null;
            String err = null;
            try { pv = SetupZip.inspect(zip); } catch (Throwable t) { err = SetupZip.errorKey(t); }
            SetupZip.Preview fp = pv;
            String fe = err;
            Minecraft.getInstance().execute(() -> {
                if (fe != null) { Toasts.error(T.f("%s: %s", zip.getFileName().toString(), T.t(fe))); return; }
                showImportDialog(fp);
            });
        }, "Zaec-ZipCheck");
        th.setDaemon(true);
        th.start();
    }

    private void showImportDialog(SetupZip.Preview pv) {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Import setup");
        d.text = T.f("Setup from %s: %s imported art(s), %s of them new here. Merge only adds what you do not have yet.",
                pv.created.isEmpty() ? "?" : pv.created.replace('T', ' ').substring(0, Math.min(16, pv.created.length())),
                pv.artIds.size(), pv.newArts);
        List<String> labels = new ArrayList<>();
        List<Boolean> vals = new ArrayList<>();
        int iSet = -1, iArts = -1;
        if (pv.hasSettings) { iSet = labels.size(); labels.add(T.t("Settings (layers, groups, menus)")); vals.add(true); }
        if (!pv.artIds.isEmpty()) { iArts = labels.size(); labels.add(T.t("Imported arts")); vals.add(true); }
        final int iRep = labels.size();
        labels.add(T.t("REPLACE everything (a backup is made first)"));
        vals.add(false);
        d.checks = labels.toArray(new String[0]);
        d.vals = new boolean[vals.size()];
        for (int i = 0; i < d.vals.length; i++) d.vals[i] = vals.get(i);
        d.yes = T.t("Import");
        d.no = T.t("Cancel");
        final int fSet = iSet, fArts = iArts;
        d.onYes = () -> {
            boolean wantSet = fSet >= 0 && d.vals[fSet], wantArts = fArts >= 0 && d.vals[fArts], rep = d.vals[iRep];
            if (!wantSet && !wantArts) { Toasts.warn(T.t("Nothing selected to import")); return; }
            SetupZip.apply(pv, wantSet, wantArts, rep, e -> {
                if (e != null) { Toasts.error(T.t(e)); return; }
                canvas.cache.clear();
                List<Arts.Art> all = Arts.all();
                if (libArt == null || Arts.byId(libArt.id) == null) libArt = all.isEmpty() ? null : all.get(0);
                multi.clear();
                selId = "";
                undoMap.clear();
                redoMap.clear();
                libUndo.clear();
                libRedo.clear();
                Toasts.ok(rep ? T.t("Setup replaced (the old one is in config/zaecguimod/backups)") : T.t("Setup merged"));
            });
        };
        kit.openDialog(d);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init() {
        // a resize calls init() again: keep the kit, or an open dialog / popup / typed text would vanish
        if (kit == null) kit = new Kit(font);
        if (!initialised) {
            initialised = true;
            List<Arts.Art> all = Arts.all();
            Arts.Art started = startArt == null ? null : Arts.byId(startArt);
            libArt = started != null ? started : all.isEmpty() ? null : all.get(0);
            if (libArt != null) group = st().artView(libArt.id).group;
            selectMenu(menuKey);
            if (started != null) setLib(started);
            sessionJson = st().toJson();
            if (!st().coached) { st().coached = true; touch(); welcome(); }
            else if (all.isEmpty()) Toasts.warn(T.t("Add your first pictures: drop PNG, JPG or GIF files onto this window"));
        }
        if (Settings.restoredNote != null) {
            Toasts.warn(T.t(Settings.restoredNote));
            Settings.restoredNote = null;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // the studio paints its own background
    }

    private static long lastFailAt;

    /** a failure inside the studio is logged and shown as a toast; it never reaches the game's crash handler */
    private void fail(String where, Throwable t) {
        Guard.LOG.error("[ZaecGuiMod] studio error in {}", where, t);
        if (now() - lastFailAt > 1500) { lastFailAt = now(); Toasts.error(T.t("Something went wrong here (details are in the log)")); }
        if (kit != null) { try { kit.resetClips(); } catch (Throwable ignored) { } }
    }

    private void safe(String where, Runnable r) {
        try { r.run(); } catch (Throwable t) { fail(where, t); }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (kit == null) kit = new Kit(font);
        safe("frame", () -> { kit.frame(g, mx, my, mouseDown); kit.tickRepeat(); });
        libRect = null;
        safe("draw", () -> StudioView.draw(this, g, mx, my, pt));
        kit.resetClips();
        safe("toasts", () -> {
            g.pose().pushPose();
            g.pose().translate(0, 0, 300);
            try { Toasts.render(g, width); } finally { g.pose().popPose(); }
        });
        safe("popup", () -> kit.drawPopup(width, height));
        safe("colors", () -> kit.colors.draw(width, height));
        safe("dialog", () -> kit.drawDialog(width, height));
        safe("tooltip", () -> kit.drawTooltip(width, height));
        kit.endFrame();
    }

    private static boolean ctrl(int mods) { return (mods & GLFW.GLFW_MOD_CONTROL) != 0; }
    private static boolean shift(int mods) { return (mods & GLFW.GLFW_MOD_SHIFT) != 0; }

    /** the mouse rests over the library panel (and nothing modal is open): then the arrow keys walk through the arts */
    private boolean overLibrary() {
        return libRect != null && kit.dialog == null && !kit.popupOpen() && !pickerOpen
                && kit.mx >= libRect[0] && kit.mx < libRect[2] && kit.my >= libRect[1] && kit.my < libRect[3];
    }

    /** move the library selection by whole cells (Shift extends it); keeps the art inside the visible grid */
    private void libMove(int dx, int dy, boolean extend) {
        List<Arts.Art> list = artsInView();
        if (list.isEmpty()) return;
        int cols = Math.max(1, gridCols), i = list.indexOf(libArt), t;
        if (i < 0) t = 0; else t = Math.max(0, Math.min(list.size() - 1, i + dx + dy * cols));
        pickArt(list.get(t), extend, false);
        int top = StudioView.GRID_GAP + (t / cols) * (StudioView.GRID_CH + StudioView.GRID_GAP);
        int bottom = top + StudioView.GRID_CH + StudioView.GRID_GAP;
        if (top - StudioView.GRID_GAP < gridScroll.offset) gridScroll.offset = Math.max(0, top - StudioView.GRID_GAP);
        else if (bottom > gridScroll.offset + gridScroll.view) gridScroll.offset = bottom - gridScroll.view;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        try {
            return keyPressed0(key, scan, mods);
        } catch (Throwable t) {
            fail("keyPressed", t);
            return true;
        }
    }

    private boolean keyPressed0(int key, int scan, int mods) {
        boolean ctrl = ctrl(mods), shift = shift(mods);
        if (kit != null && kit.dialog == null && KeyHooks.STUDIO.matches(key, scan)) {
            // a rebound printable key must still be typeable in a text field
            if (key < GLFW.GLFW_KEY_ESCAPE && kit.typing()) { kit.key(key, scan, mods); return true; }
            if (!KeyHooks.studioHeld) onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F1 && kit != null) {
            // F1 opens the sheet from anywhere (also while typing) and closes it again; other dialogs ignore it
            if (kit.dialog == null) StudioView.helpDialog(this); else if (kit.dialog.help != null) kit.closeDialog();
            return true;
        }
        if (kit != null && kit.colors.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) { kit.colors.close(); return true; }
        if (kit != null && kit.dialog == null && !kit.popupOpen() && pickerOpen && StudioView.pickerKey(this, key, mods)) return true;
        if (kit != null && kit.key(key, scan, mods)) return true;
        if (pickerOpen && key == GLFW.GLFW_KEY_ESCAPE) { pickerOpen = false; return true; }
        if (kit != null && kit.colors.isOpen() && key == GLFW.GLFW_KEY_ESCAPE) { kit.colors.close(); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (libOverlay) { libOverlay = false; return true; }
            if (!multi.isEmpty()) { multi.clear(); return true; }
            onClose();
            return true;
        }
        if (!ctrl && kit != null && overLibrary()) {
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> { libMove(-1, 0, shift); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { libMove(1, 0, shift); return true; }
                case GLFW.GLFW_KEY_UP -> { libMove(0, -1, shift); return true; }
                case GLFW.GLFW_KEY_DOWN -> { libMove(0, 1, shift); return true; }
                case GLFW.GLFW_KEY_SPACE -> { toggleRotation(); return true; }
                default -> { }
            }
        }
        if (ctrl && key == GLFW.GLFW_KEY_Z) { if (shift) redo(); else undo(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_Y) { redo(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_A) { selectAllInView(); return true; }
        if (ctrl && key == GLFW.GLFW_KEY_D) { duplicateLayer(); return true; }
        if (key == GLFW.GLFW_KEY_TAB) { playerView = !playerView; return true; }
        if (ctrl && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) { stepMenu(key == GLFW.GLFW_KEY_LEFT ? -1 : 1); return true; }
        if (ctrl && !isTitle() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            List<Settings.Layer> ls = layers();
            int i = selIndex() + (key == GLFW.GLFW_KEY_UP ? 1 : -1);
            if (i >= 0 && i < ls.size()) selectLayer(ls.get(i).id);
            return true;
        }
        if (key == GLFW.GLFW_KEY_N && !ctrl) { nextMenuToCalibrate(); return true; }
        if (!isTitle() && sel() != null) {
            int step = shift ? 5 : 1;
            switch (key) {
                case GLFW.GLFW_KEY_LEFT -> { nudge(-step, 0); return true; }
                case GLFW.GLFW_KEY_RIGHT -> { nudge(step, 0); return true; }
                case GLFW.GLFW_KEY_UP -> { nudge(0, -step); return true; }
                case GLFW.GLFW_KEY_DOWN -> { nudge(0, step); return true; }
                case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> { scaleBy(1.04f); return true; }
                case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> { scaleBy(1f / 1.04f); return true; }
                case GLFW.GLFW_KEY_LEFT_BRACKET -> { rotate(shift ? -15 : -5); return true; }
                case GLFW.GLFW_KEY_RIGHT_BRACKET -> { rotate(shift ? 15 : 5); return true; }
                case GLFW.GLFW_KEY_H -> { flip(false); return true; }
                case GLFW.GLFW_KEY_V -> { flip(true); return true; }
                case GLFW.GLFW_KEY_DELETE -> { deleteLayer(); return true; }
                case GLFW.GLFW_KEY_PAGE_UP -> { moveLayer(1); return true; }
                case GLFW.GLFW_KEY_PAGE_DOWN -> { moveLayer(-1); return true; }
                case GLFW.GLFW_KEY_COMMA -> { stepSample(-1); return true; }
                case GLFW.GLFW_KEY_PERIOD -> { stepSample(1); return true; }
                case GLFW.GLFW_KEY_R -> { if (!ctrl) { roll(); return true; } }
                case GLFW.GLFW_KEY_0 -> { canvas.fit(); return true; }
                default -> { }
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        try {
            return charTyped0(c, mods);
        } catch (Throwable t) {
            fail("charTyped", t);
            return true;
        }
    }

    private boolean charTyped0(char c, int mods) {
        if (kit != null && kit.chr(c, mods)) return true;
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        try {
            return mouseClicked0(mx, my, button);
        } catch (Throwable t) {
            fail("mouseClicked", t);
            return true;
        }
    }

    private boolean mouseClicked0(double mx, double my, int button) {
        if (button == 0) mouseDown = true;
        if (kit != null && kit.press(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        try {
            return mouseDragged0(mx, my, button, dx, dy);
        } catch (Throwable t) {
            fail("mouseDragged", t);
            return true;
        }
    }

    private boolean mouseDragged0(double mx, double my, int button, double dx, double dy) {
        if (kit != null && kit.dragMove(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        try {
            return mouseReleased0(mx, my, button);
        } catch (Throwable t) {
            fail("mouseReleased", t);
            return true;
        }
    }

    private boolean mouseReleased0(double mx, double my, int button) {
        if (button == 0) mouseDown = false;
        if (kit != null) kit.release(mx, my, button);
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        try {
            return mouseScrolled0(mx, my, sx, sy);
        } catch (Throwable t) {
            fail("mouseScrolled", t);
            return true;
        }
    }

    private boolean mouseScrolled0(double mx, double my, double sx, double sy) {
        if (kit != null && kit.wheel(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        flush();
        Minecraft.getInstance().setScreen(previous);
    }

    @Override
    public void removed() {
        flush();
        StudioView.clearGhost();
        Guard.run("studio-dispose-preview", canvas::dispose);
        if (previous != null) {
            Map<String, String> samples = new HashMap<>();
            boolean sameMenu = previous instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> acs
                    && menuKey.equals(MenuInfo.keyFor(acs));
            if (!isTitle() && sameMenu) for (Settings.Layer l : layers()) if (l.random() && Arts.byId(l.art) != null) samples.put(l.id, l.art);
            Guard.run("studio-refresh", () -> ContainerHooks.refresh(previous, samples));
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
