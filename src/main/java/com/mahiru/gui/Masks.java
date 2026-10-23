package com.mahiru.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** UI layout masks (slots / labels / widgets per menu) exported from the game's own menu code. */
public final class Masks {
    public static final class Mask {
        public final int w, h;
        /** x, y, w, h per rect */
        public final int[][] rects;
        /** 's' = item slot, anything else = blocked UI */
        public final char[] kinds;

        Mask(int w, int h, int[][] rects, char[] kinds) {
            this.w = w; this.h = h; this.rects = rects; this.kinds = kinds;
        }
    }

    private static volatile Map<String, Mask> all;
    private static long failedAt;

    public static synchronized void invalidate() { all = null; }

    public static synchronized Mask get(String name) {
        Map<String, Mask> cur = all;
        if (cur == null && System.currentTimeMillis() - failedAt < 5000) return null;
        if (cur == null) {
            cur = new HashMap<>();
            try (InputStream in = Minecraft.getInstance().getResourceManager().open(ZaecGuiMod.id("masks.json"))) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    JsonArray rs = o.getAsJsonArray("rects");
                    int[][] rects = new int[rs.size()][4];
                    char[] kinds = new char[rs.size()];
                    for (int i = 0; i < rs.size(); i++) {
                        JsonArray r = rs.get(i).getAsJsonArray();
                        kinds[i] = r.get(0).getAsString().charAt(0);
                        for (int j = 0; j < 4; j++) rects[i][j] = r.get(j + 1).getAsInt();
                    }
                    cur.put(e.getKey(), new Mask(o.get("w").getAsInt(), o.get("h").getAsInt(), rects, kinds));
                }
                if (!cur.isEmpty()) all = cur;
            } catch (Exception ex) {
                Guard.LOG.error("[ZaecGuiMod] could not load masks.json", ex);
            }
            if (all == null) failedAt = System.currentTimeMillis();
        }
        return cur.get(name);
    }

    /**
     * Top-left slot of the player's inventory block (3 rows of 9 above the hotbar row), or null when the menu has none.
     * The block is found from the geometry: the lowest hotbar row whose 27 main slots sit 58 px higher.
     */
    public static int[] inventoryOrigin(Mask m) {
        if (m == null) return null;
        java.util.Set<Long> slots = new java.util.HashSet<>();
        for (int i = 0; i < m.rects.length; i++) if (m.kinds[i] == 's') slots.add(((long) m.rects[i][0] << 32) | (m.rects[i][1] & 0xFFFFFFFFL));
        int[] best = null;
        for (int i = 0; i < m.rects.length; i++) {
            if (m.kinds[i] != 's') continue;
            int x0 = m.rects[i][0], hy = m.rects[i][1];
            if (best != null && hy - 58 <= best[1]) continue;
            // a grid that continues right above the block (the creative item list) is not an inventory block
            if (slots.contains(((long) x0 << 32) | ((hy - 58 - 18) & 0xFFFFFFFFL))) continue;
            boolean ok = true;
            for (int c = 0; c < 9 && ok; c++) {
                if (!slots.contains(((long) (x0 + 18 * c) << 32) | (hy & 0xFFFFFFFFL))) ok = false;
                for (int r = 0; r < 3 && ok; r++)
                    if (!slots.contains(((long) (x0 + 18 * c) << 32) | ((hy - 58 + 18 * r) & 0xFFFFFFFFL))) ok = false;
            }
            if (ok) best = new int[] { x0, hy - 58 };
        }
        return best;
    }

    private Masks() {}
}
