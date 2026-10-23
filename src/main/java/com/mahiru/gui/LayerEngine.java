package com.mahiru.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Turns the layers of a menu into things to draw: which art each layer shows and where it goes. */
public final class LayerEngine {
    /** One layer as it is drawn: the layer itself plus the art that was picked for it. */
    public static final class Item {
        public final Settings.Layer layer;
        public final Arts.Art art;

        public Item(Settings.Layer layer, Arts.Art art) {
            this.layer = layer;
            this.art = art;
        }
    }

    /** Automatic placements are expensive (image analysis): remember them. */
    public static final class Cache {
        private final Map<String, Placement> auto = new HashMap<>();

        public void clear() { auto.clear(); }
    }

    private static final Random RNG = new Random();

    /** The art a random layer may show: its group (SRC_GROUP) or the rotation pool (SRC_POOL). */
    public static List<Arts.Art> poolOf(Settings.Layer l) {
        List<Arts.Art> out = new ArrayList<>();
        Settings st = Settings.get();
        if (Settings.SRC_GROUP.equals(l.src)) {
            for (Arts.Art a : Arts.all()) {
                Settings.ArtMeta m = st.artView(a.id);
                if (l.group.equals(m.group) && m.rotation) out.add(a);
            }
            if (out.isEmpty()) for (Arts.Art a : Arts.all()) if (l.group.equals(st.artView(a.id).group)) out.add(a);
        } else {
            for (Arts.Art a : Arts.all()) if (st.inRotation(a.id)) out.add(a);
        }
        return out;
    }

    /** The art a layer shows right now (a random one for random layers). Null when there is nothing to show. */
    public static Arts.Art pickArt(Settings.Layer l, String menuKey) {
        if (!l.random()) return Arts.byId(l.art);
        if (Settings.SRC_POOL.equals(l.src)) return Arts.pick(menuKey + "#" + l.id);
        List<Arts.Art> pool = poolOf(l);
        if (pool.isEmpty()) return Arts.byId(l.art);
        return pool.get(RNG.nextInt(pool.size()));
    }

    /** Picks an art for every layer of a menu (bottom to top). */
    public static List<Item> pickAll(String menuKey) {
        List<Item> out = new ArrayList<>();
        for (Settings.Layer l : Settings.get().layersView(menuKey)) {
            Arts.Art a = pickArt(l, menuKey);
            if (a != null) out.add(new Item(l, a));
        }
        return out;
    }

    /** Where an art goes in a layer: the calibration of that art, the fixed transform, or the automatic search. */
    public static Placement placement(Settings.Layer l, Arts.Art a, Masks.Mask mask, Cache cache) {
        if (l.random()) {
            Placement s = l.spot(a.id);
            if (s != null && s.w > 0 && s.h > 0) return s;
            return auto(a, mask, s != null && s.flip, s != null && s.flipY, cache);
        }
        if (l.w > 0 && l.h > 0) {
            Placement p = new Placement(l.x, l.y, l.w, l.h);
            p.rot = l.rot;
            p.flip = l.flip;
            p.flipY = l.flipY;
            return p;
        }
        return auto(a, mask, l.flip, l.flipY, cache);
    }

    /** true when the placement of this art in this layer is the automatic one (nothing saved) */
    public static boolean isAuto(Settings.Layer l, Arts.Art a) {
        if (l.random()) {
            int[] s = l.spots.get(a.id);
            return s == null || s[2] <= 0 || s[3] <= 0;
        }
        return l.w <= 0 || l.h <= 0;
    }

    private static Placement auto(Arts.Art a, Masks.Mask mask, boolean flip, boolean flipY, Cache cache) {
        String k = a.id + "|" + (flip ? 1 : 0) + (flipY ? 1 : 0) + "|" + mask.w + "x" + mask.h;
        Placement p = cache == null ? null : cache.auto.get(k);
        if (p == null) {
            p = Placement.find(a, mask, flip, flipY);
            p.flip = flip;
            p.flipY = flipY;
            if (cache != null) cache.auto.put(k, p);
        }
        Placement c = new Placement(p.x, p.y, p.w, p.h);
        c.rot = p.rot;
        c.flip = p.flip;
        c.flipY = p.flipY;
        return c;
    }

    /** Writes a placement back into the layer (calibration of that art, or the fixed transform). */
    public static void store(Settings.Layer l, Arts.Art a, Placement p) {
        Settings.guardShared(l);
        if (l.random()) {
            l.spots.put(a.id, Settings.toSpot(p));
        } else {
            l.x = p.x; l.y = p.y; l.w = p.w; l.h = p.h; l.rot = p.rot; l.flip = p.flip; l.flipY = p.flipY;
        }
    }

    /** Back to the automatic placement. */
    public static void clear(Settings.Layer l, Arts.Art a) {
        Settings.guardShared(l);
        if (l.random()) {
            // keep a mirror choice: automatic placement with the same flip
            int[] old = l.spots.get(a.id);
            if (old != null && (old[5] == 1 || old[6] == 1)) l.spots.put(a.id, new int[] { 0, 0, 0, 0, 0, old[5], old[6] });
            else l.spots.remove(a.id);
        } else { l.w = 0; l.h = 0; l.x = 0; l.y = 0; l.rot = 0; }
    }

    private LayerEngine() {}
}
