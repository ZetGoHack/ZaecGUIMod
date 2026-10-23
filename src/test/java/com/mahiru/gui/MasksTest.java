package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MasksTest {
    /** Builds a mask out of 16x16 slots and optional blocked UI rectangles. */
    private static final class B {
        final List<int[]> rects = new ArrayList<>();
        final List<Character> kinds = new ArrayList<>();

        B slot(int x, int y) { rects.add(new int[] { x, y, 16, 16 }); kinds.add('s'); return this; }
        B label(int x, int y, int w, int h) { rects.add(new int[] { x, y, w, h }); kinds.add('l'); return this; }
        B row(int x, int y, int n) { for (int i = 0; i < n; i++) slot(x + 18 * i, y); return this; }
        B grid(int x, int y, int cols, int rows) { for (int r = 0; r < rows; r++) row(x, y + 18 * r, cols); return this; }
        /** player inventory: 3 rows of 9, then the hotbar 58 px under the first row */
        B player(int x, int y) { return grid(x, y, 9, 3).row(x, y + 58, 9); }

        Masks.Mask build(int w, int h) {
            char[] k = new char[kinds.size()];
            for (int i = 0; i < k.length; i++) k[i] = kinds.get(i);
            return new Masks.Mask(w, h, rects.toArray(new int[0][]), k);
        }
    }

    @Test
    void nullMaskHasNoOrigin() {
        assertNull(Masks.inventoryOrigin(null));
    }

    @Test
    void chestLikeLayout() {
        Masks.Mask m = new B().grid(8, 18, 9, 3).player(8, 85).build(176, 168);
        assertArrayEquals(new int[] { 8, 85 }, Masks.inventoryOrigin(m));
    }

    @Test
    void hopperLikeLayout() {
        Masks.Mask m = new B().row(44, 20, 5).label(8, 6, 40, 9).player(8, 51).build(176, 133);
        assertArrayEquals(new int[] { 8, 51 }, Masks.inventoryOrigin(m));
    }

    @Test
    void villagerLikeLayoutWithXOffset() {
        Masks.Mask m = new B().slot(136, 37).slot(162, 37).slot(220, 37).player(108, 84).build(276, 166);
        assertArrayEquals(new int[] { 108, 84 }, Masks.inventoryOrigin(m));
    }

    @Test
    void maskWithoutInventoryBlock() {
        Masks.Mask m = new B().grid(8, 18, 9, 3).label(0, 0, 10, 10).build(176, 100);
        assertNull(Masks.inventoryOrigin(m));
    }

    @Test
    void creativeLikeWithOnlyAPartialGridAndHotbar() {
        Masks.Mask m = new B().grid(9, 18, 9, 4).row(9, 112, 9).build(195, 136);
        assertNull(Masks.inventoryOrigin(m));
    }

    @Test
    void incompleteInventoryIsRejected() {
        B b = new B().grid(8, 84, 9, 3).row(8, 142, 8);   // the ninth hotbar slot is missing
        assertNull(Masks.inventoryOrigin(b.build(176, 166)));
    }

    @Test
    void nonSlotRectsAreIgnored() {
        B b = new B().player(8, 84);
        b.kinds.replaceAll(c -> 'w');
        assertNull(Masks.inventoryOrigin(b.build(176, 166)));
    }

    @Test
    void containerRowsAreNotMistakenForTheInventory() {
        Masks.Mask m = new B().grid(8, 18, 9, 6).player(8, 140).build(176, 222);
        assertArrayEquals(new int[] { 8, 140 }, Masks.inventoryOrigin(m));
    }

    // ---- the real masks.json, parsed the same way Masks.get does

    private static Map<String, Masks.Mask> realMasks() throws IOException {
        Path p = TestPaths.project("src/main/resources/assets/zaecguimod/masks.json");
        JsonObject root = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, Masks.Mask> out = new LinkedHashMap<>();
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
            out.put(e.getKey(), new Masks.Mask(o.get("w").getAsInt(), o.get("h").getAsInt(), rects, kinds));
        }
        return out;
    }

    @Test
    void everyMenuInfoEntryHasARealMask() throws IOException {
        Map<String, Masks.Mask> all = realMasks();
        for (MenuInfo.Entry e : MenuInfo.ENTRIES) assertTrue(all.containsKey(e.key()), "no mask for " + e.key());
    }

    @Test
    void realMasksReportTheStandardInventoryOrigin() throws IOException {
        Map<String, Masks.Mask> all = realMasks();
        Map<String, int[]> expected = new LinkedHashMap<>();
        for (String k : new String[] { "anvil", "blast_furnace", "brewing_stand", "cartography_table", "crafter", "crafting_table",
                "dispenser", "enchanting_table", "furnace", "grindstone", "horse", "inventory", "loom", "shulker_box", "smithing",
                "smoker", "stonecutter" })
            expected.put(k, new int[] { 8, 84 });
        expected.put("beacon", new int[] { 36, 137 });
        expected.put("hopper", new int[] { 8, 51 });
        expected.put("villager", new int[] { 108, 84 });
        for (int rows = 1; rows <= 6; rows++) expected.put("generic_54_" + rows, new int[] { 8, 49 + 18 * (rows - 1) });
        for (var e : expected.entrySet()) {
            Masks.Mask m = all.get(e.getKey());
            assertNotNull(m, e.getKey());
            assertArrayEquals(e.getValue(), Masks.inventoryOrigin(m), e.getKey());
        }
    }

    @Test
    void realCreativeItemListHasNoInventory() throws IOException {
        assertNull(Masks.inventoryOrigin(realMasks().get("creative")));
    }

    @Test
    void realCreativeInventoryTabHasItsInventoryAt9x54() throws IOException {
        assertArrayEquals(new int[] { 9, 54 }, Masks.inventoryOrigin(realMasks().get("creative_inventory")));
    }

    @Test
    void realMasksAreWellFormed() throws IOException {
        for (var e : realMasks().entrySet()) {
            Masks.Mask m = e.getValue();
            assertTrue(m.w > 0 && m.h > 0, e.getKey());
            assertEquals(m.rects.length, m.kinds.length, e.getKey());
            for (int[] r : m.rects) assertTrue(r[2] > 0 && r[3] > 0, e.getKey() + " has an empty rect");
        }
    }
}
